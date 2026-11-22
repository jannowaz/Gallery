package org.fossify.gallery.helpers

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import androidx.paging.PagingSource
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.Mutex
import org.fossify.commons.extensions.isAStorageRootFolder
import org.fossify.gallery.compose.screens.SortField
import org.fossify.gallery.databases.GalleryDatabase
import org.fossify.gallery.interfaces.DirectoryAggregate
import org.fossify.gallery.extensions.collectionDB
import org.fossify.gallery.extensions.directoryDB
import org.fossify.gallery.extensions.favoritesDB
import org.fossify.gallery.extensions.getFavoriteFromPath
import org.fossify.gallery.extensions.mediaDB
import org.fossify.gallery.extensions.mediaCacheDB
import org.fossify.gallery.extensions.mediaTagDB
import org.fossify.gallery.extensions.dateTakensDB
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.resolveRecycleBinFile
import org.fossify.gallery.R
import org.fossify.gallery.models.Directory
import org.fossify.gallery.models.MediaCollection
import org.fossify.gallery.models.Medium
import org.fossify.gallery.models.MediaCache
import org.fossify.gallery.models.MediaTag
import org.fossify.gallery.models.MediaReviewState
import org.fossify.gallery.models.MediaReviewStatus
import org.fossify.gallery.models.PersonPresence
import org.fossify.gallery.models.TagCount
import org.fossify.gallery.models.TagPathRow
import org.fossify.gallery.viewmodels.MediaFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap

// SQLite's default host-parameter limit is 999 (older bundled versions; some newer builds raise it,
// but Android's bundled version cannot be relied on to). Room expands a "full_path IN (:paths)" query
// into one bound parameter per element, so a single unchunked call with e.g. a "select all" batch of a
// few thousand paths can exceed that limit and throw at runtime. Chunk comfortably under it instead.
internal const val SQLITE_BATCH_CHUNK_SIZE = 900

/** Negative IDs designate built-in virtual collections and can never collide with Room IDs. */
private const val SYSTEM_COLLECTION_FAVORITES = -1L
private const val SYSTEM_COLLECTION_RATING_BASE = -100L

internal const val CANDIDATE_BATCH_SIZE = 10000

internal const val MEDIA_COLUMNS =
    "filename, full_path, parent_path, last_modified, date_taken, size, type, video_duration, is_favorite, deleted_ts, media_store_id, rating, date_sort_key, date_added"

fun resolveContentUriToPath(uriString: String): String? {
    if (uriString.startsWith("/")) return uriString
    val uri = Uri.parse(uriString)
    val docId = try { DocumentsContract.getTreeDocumentId(uri) } catch (_: Exception) { return null }
    val parts = docId.split(":")
    if (parts.size == 2 && parts[0] == "primary") {
        return "${Environment.getExternalStorageDirectory().absolutePath}/${parts[1]}"
    }
    return null
}

class MediaRepository(internal val context: Context) : MediaRepositoryInterface {

    companion object {
        /** Process-wide throttle for [syncDirectoriesFromMedia] - this class is instantiated per
         * call site, so an instance field would defeat the throttle entirely. */
        @Volatile
        private var lastDirectorySyncMs = 0L
        // Reuse the aggregate just read while rebuilding the directory cache. Albums often opens
        // immediately after that rebuild; retaining this process-local immutable snapshot avoids a
        // second full media-table GROUP BY for the image/video badges.
        @Volatile
        private var directoryAggregatesCache: List<DirectoryAggregate>? = null

        // MediaViewModel and MediaSyncWorker can both notice the same MediaStore change. Serialize
        // their incremental scans so two large cursors never compete for the same provider/SQLite
        // resources while the user is pulling to refresh.
        private val mediaStoreSyncMutex = kotlinx.coroutines.sync.Mutex()
        // A mutex alone only queues duplicate callers: the worker would still run the same scan a
        // second time after the manual refresh released the lock. Share the in-flight incremental
        // Deferred so both callers await one provider query and one DB transaction.
        private val incrementalSyncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val incrementalSyncLock = Any()
        @Volatile
        private var activeIncrementalSync: Deferred<List<Medium>>? = null

    }
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    // A quick sequence of tag edits for the same file only needs one cache write: that job reads the
    // latest XMP snapshot when it starts. Tracking the in-flight jobs also lets the UI wait until the
    // normalized media_tags row is committed before it refreshes a filtered Paging source.
    private val metadataCacheJobs = ConcurrentHashMap<String, Job>()

    /** Completes the fields that can be recovered from the actual file when MediaStore briefly
     * exposed a row with zero size/timestamps (this can happen while another app replaces a JPEG's
     * bytes or while a scanner is still publishing the replacement). */
    private data class RecoveredFileMetadata(
        val modified: Long,
        val taken: Long,
        val size: Long,
        val added: Long,
    )

    private fun recoverFileMetadata(path: String, modified: Long, taken: Long, size: Long, added: Long): RecoveredFileMetadata? {
        val file = File(path)
        if (!file.isFile) return null
        val diskSize = file.length()
        val diskModified = file.lastModified().takeIf { it > 0L } ?: 0L
        val recoveredModified = modified.takeIf { it > 0L } ?: diskModified
        val recoveredTaken = taken.takeIf { it > 0L } ?: readExifDate(path) ?: recoveredModified
        val recoveredSize = size.takeIf { it > 0L } ?: diskSize
        val recoveredAdded = added.takeIf { it > 0L } ?: maxOf(recoveredModified, recoveredTaken)
        if (recoveredModified <= 0L && recoveredTaken <= 0L && recoveredSize <= 0L && recoveredAdded <= 0L) return null
        return RecoveredFileMetadata(recoveredModified, recoveredTaken, recoveredSize, recoveredAdded)
    }

    /** EXIF capture time is a better fallback than 1970 for old camera files whose MediaStore row
     * has no DATE_TAKEN. Parsing is intentionally conservative; an invalid EXIF value falls back
     * to the real filesystem mtime. */
    private fun readExifDate(path: String): Long? = runCatching {
        if (File(path).extension.lowercase(Locale.ROOT) !in setOf("jpg", "jpeg", "tif", "tiff", "png", "webp", "heic", "heif")) return@runCatching null
        val exif = ExifInterface(path)
        val raw = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
            ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
            ?: return@runCatching null
        SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).apply {
            isLenient = false
            timeZone = TimeZone.getDefault()
        }.parse(raw)?.time
    }.getOrNull()

    override fun getMediaFromPath(path: String): List<Medium> {
        return try {
            context.mediaDB.getMediaFromPath(path)
        } catch (e: Exception) {
            android.util.Log.e("MediaRepository", "getMediaFromPath failed for $path", e)
            emptyList()
        }
    }

    /** What MediaStore holds vs what this app's library table holds. See [libraryHealth]. */
    data class LibraryHealth(val inMediaStore: Int, val inLibrary: Int) {
        val missing: Int get() = (inMediaStore - inLibrary).coerceAtLeast(0)

        /**
         * Whether the gap is big enough to be worth reporting. A handful of rows is normal: the two
         * counts are taken moments apart and MediaStore keeps growing while the app runs, so a
         * camera shot or a chat download lands between them. A real sync failure is orders of
         * magnitude larger - the one this check exists for was 3,761.
         */
        val isSignificant: Boolean get() = missing > 100
    }

    /**
     * Compares MediaStore's image+video count against the library table's live rows.
     *
     * Exists because a sync bug once left 3,761 files permanently invisible and there was no way to
     * notice: nobody counts album contents by hand, and the app looked perfectly healthy. Two counts
     * are enough to catch the entire class of "media silently never appears" problems, whatever
     * causes the next one.
     */
    fun libraryHealth(): LibraryHealth {
        val store = countInMediaStore(MediaStore.Images.Media.EXTERNAL_CONTENT_URI) +
            countInMediaStore(MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
        val library = try {
            context.mediaDB.getLiveMediaCount()
        } catch (e: Exception) {
            android.util.Log.e("MediaRepository", "getLiveMediaCount failed", e)
            0
        }
        return LibraryHealth(inMediaStore = store, inLibrary = library)
    }

    /** Projects a single column so the cursor window stays small on a six-figure library. */
    private fun countInMediaStore(uri: Uri): Int = try {
        context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns._ID), null, null, null)
            ?.use { it.count } ?: 0
    } catch (e: Exception) {
        android.util.Log.e("MediaRepository", "countInMediaStore failed for $uri", e)
        0
    }

    /** Capped, paths-only variant of [getMediaFromPath] for preview strips. See MediumDao. */
    fun getPreviewPathsFromPath(path: String, limit: Int): List<String> {
        return try {
            context.mediaDB.getPreviewPathsFromPath(path, limit)
        } catch (e: Exception) {
            android.util.Log.e("MediaRepository", "getPreviewPathsFromPath failed for $path", e)
            emptyList()
        }
    }

    override fun isFavorite(path: String): Boolean {
        return try {
            context.favoritesDB.isFavorite(path)
        } catch (e: Exception) {
            android.util.Log.e("MediaRepository", "isFavorite failed for $path", e)
            false
        }
    }

    override fun toggleFavorite(path: String, isFav: Boolean): Boolean {
        val success = try {
            if (isFav) {
                val name = File(path).name
                val parentPath = File(path).parent ?: ""
                context.favoritesDB.insert(org.fossify.gallery.models.Favorite(id = null, fullPath = path, filename = name, parentPath = parentPath))
            } else {
                context.favoritesDB.deleteFavoritePath(path)
            }
            context.mediaDB.updateFavorite(path, isFav)
            true
        } catch (e: Exception) {
            android.util.Log.e("MediaRepository", "toggleFavorite failed for $path", e)
            false
        }
        // Favorites also feed the built-in Favorites collection, so invalidate its cover/count as
        // part of the same lightweight metadata refresh.
        if (success) RefreshBus.trigger(pathsChanged = false, collectionsChanged = true)
        return success
    }

    override fun getRating(path: String): Int {
        // A single-row, indexed-by-full_path DB lookup instead of a live XMP file read (which was
        // also unused everywhere until now) - the Viewer used to call getMediaFromPath(currentPath)
        // for this instead, which filters on parent_path and so silently matched nothing for a file
        // path (always falling back to 0), on top of loading every file in the whole folder.
        return try {
            (context.mediaDB.getRatingForPath(path) ?: 0).coerceIn(0, 5)
        } catch (e: Exception) {
            android.util.Log.e("MediaRepository", "getRating failed for $path", e)
            0
        }
    }

    override fun updateRating(path: String, rating: Int): Boolean {
        val normalizedRating = rating.coerceIn(0, 5)
        return withMetadataWriteLock(path) {
            // readOrNull (not read): a failed XMP read must abort the whole edit, not fall through
            // as an empty result - writing that back would wipe the file's existing metadata.
            val current = XmpWriter.readOrNull(path) ?: return@withMetadataWriteLock false
            if (!XmpWriter.write(path, current.tags, normalizedRating)) return@withMetadataWriteLock false
            try {
                context.mediaDB.updateRating(path, normalizedRating)
            } catch (e: Exception) {
                // The file is already changed at this point. Best-effort rollback keeps the DB and
                // XMP aligned when the Room write fails, and the false result lets optimistic UI
                // callers revert their visual state.
                XmpWriter.write(path, current.tags, current.rating)
                android.util.Log.e("MediaRepository", "updateRating failed for $path", e)
                return@withMetadataWriteLock false
            }
            refreshExternalMediaStoreTimestamp(path)
            enqueueMetadataCacheSync(path)
            true
        }
    }

    override fun getTags(path: String): Set<String> {
        return withMetadataWriteLock(path) { XmpWriter.read(path).tags.toSet() }
    }

    override fun addTag(path: String, tag: String): Boolean {
        val cleanTag = XmpWriter.sanitizeTag(tag)
        if (cleanTag.isBlank()) return false
        return withMetadataWriteLock(path) {
            val current = XmpWriter.readOrNull(path) ?: return@withMetadataWriteLock false
            // Tags are user-facing labels, so matching is case-insensitive. Keep the existing
            // spelling when it already exists instead of creating both "Berlin" and "berlin".
            if (current.tags.any { it.equals(cleanTag, ignoreCase = true) }) return@withMetadataWriteLock true
            val tags = current.tags + cleanTag
            if (!XmpWriter.write(path, tags, current.rating, context.config.tagHierarchy)) return@withMetadataWriteLock false
            enqueueMetadataCacheSync(path)
            true
        }
    }

    override fun removeTag(path: String, tag: String): Boolean {
        val cleanTag = XmpWriter.sanitizeTag(tag)
        if (cleanTag.isBlank()) return false
        return withMetadataWriteLock(path) {
            val current = XmpWriter.readOrNull(path) ?: return@withMetadataWriteLock false
            val tags = current.tags.filterNot { it.equals(cleanTag, ignoreCase = true) }
            if (tags.size == current.tags.size) return@withMetadataWriteLock true
            if (!XmpWriter.write(path, tags, current.rating, context.config.tagHierarchy)) return@withMetadataWriteLock false
            enqueueMetadataCacheSync(path)
            true
        }
    }

    /** Toggles one tag while holding the per-file metadata lock for the complete read-modify-write. */
    fun toggleTag(path: String, tag: String): Boolean {
        val cleanTag = XmpWriter.sanitizeTag(tag)
        if (cleanTag.isBlank()) return false
        return withMetadataWriteLock(path) {
            val current = XmpWriter.readOrNull(path) ?: return@withMetadataWriteLock false
            val hasTag = current.tags.any { it.equals(cleanTag, ignoreCase = true) }
            val tags = if (hasTag) current.tags.filterNot { it.equals(cleanTag, ignoreCase = true) } else current.tags + cleanTag
            if (!XmpWriter.write(path, tags, current.rating, context.config.tagHierarchy)) return@withMetadataWriteLock false
            enqueueMetadataCacheSync(path)
            true
        }
    }

    /** Removes several tags in one locked XMP write, used by the tag browser's bulk delete. */
    fun removeTags(path: String, tagsToRemove: Collection<String>): Boolean {
        val normalized = tagsToRemove.map(XmpWriter::sanitizeTag).filter(String::isNotBlank).toSet()
        if (normalized.isEmpty()) return true
        return withMetadataWriteLock(path) {
            val current = XmpWriter.readOrNull(path) ?: return@withMetadataWriteLock false
            val tags = current.tags.filterNot { currentTag -> normalized.any { it.equals(currentTag, ignoreCase = true) } }
            if (tags.size == current.tags.size) return@withMetadataWriteLock true
            if (!XmpWriter.write(path, tags, current.rating, context.config.tagHierarchy)) return@withMetadataWriteLock false
            enqueueMetadataCacheSync(path)
            true
        }
    }

    /** Restores an exact tag snapshot for Undo. */
    fun replaceTags(path: String, tags: Collection<String>): Boolean {
        return withMetadataWriteLock(path) {
            val current = XmpWriter.readOrNull(path) ?: return@withMetadataWriteLock false
            val normalized = tags.map(XmpWriter::sanitizeTag).filter(String::isNotBlank)
                .distinctBy { it.lowercase() }
            if (!XmpWriter.write(path, normalized, current.rating, context.config.tagHierarchy)) return@withMetadataWriteLock false
            enqueueMetadataCacheSync(path)
            true
        }
    }

    /** Reads many normalized tag rows with SQLite-sized chunks, avoiding one XMP parse per item. */
    suspend fun getTagsForPaths(paths: Collection<String>): Map<String, Set<String>> {
        val distinct = paths.filter(String::isNotBlank).distinct()
        if (distinct.isEmpty()) return emptyMap()
        return distinct.chunked(900).flatMap { context.mediaTagDB.getTagsForPaths(it) }
            .groupBy({ it.path }, { it.tag })
            .mapValues { (_, tags) -> tags.toSet() }
    }

    suspend fun getRatingsForPaths(paths: Collection<String>): Map<String, Int> {
        val distinct = paths.filter(String::isNotBlank).distinct()
        if (distinct.isEmpty()) return emptyMap()
        return distinct.chunked(900).flatMap { context.mediaDB.getMediaByPaths(it) }
            .associate { it.path to it.rating.coerceIn(0, 5) }
    }

    /** Replaces a tag in one atomic read-modify-write. This avoids the partial state caused by
     * implementing rename/merge as addTag followed by removeTag. */
    fun replaceTag(path: String, oldTag: String, newTag: String): Boolean {
        val source = XmpWriter.sanitizeTag(oldTag)
        val target = XmpWriter.sanitizeTag(newTag)
        if (source.isBlank() || target.isBlank()) return false
        return withMetadataWriteLock(path) {
            val current = XmpWriter.readOrNull(path) ?: return@withMetadataWriteLock false
            if (source.equals(target, ignoreCase = true)) return@withMetadataWriteLock true
            if (current.tags.none { it.equals(source, ignoreCase = true) }) return@withMetadataWriteLock true
            val tags = current.tags.map { if (it.equals(source, ignoreCase = true)) target else it }
            if (!XmpWriter.write(path, tags, current.rating, context.config.tagHierarchy)) return@withMetadataWriteLock false
            enqueueMetadataCacheSync(path)
            true
        }
    }

    /** Re-emits the current hierarchy into XMP after a parent assignment. This makes the persisted
     * file metadata agree with Config, so a later metadata scan cannot resurrect an old parent. */
    fun rewriteTagHierarchy(paths: Collection<String>): Int {
        var failed = 0
        paths.distinct().forEach { path ->
            val ok = withMetadataWriteLock(path) {
                val current = XmpWriter.readOrNull(path)
                current != null && XmpWriter.write(path, current.tags, current.rating, context.config.tagHierarchy).also {
                    if (it) enqueueMetadataCacheSync(path)
                }
            }
            if (!ok) failed++
        }
        return failed
    }

    private fun <T> withMetadataWriteLock(path: String, block: () -> T): T =
        MetadataWriteCoordinator.withLock(path, block)

    private fun enqueueMetadataCacheSync(path: String) {
        synchronized(metadataCacheJobs) {
            if (metadataCacheJobs[path]?.isActive == true) return
            val job = repositoryScope.launch {
                try {
                    MetadataWriteCoordinator.withLockSuspend(path) {
                        // Always read the latest file snapshot when the async job starts. If two
                        // edits were queued close together, an older job can then never overwrite
                        // the cache with an older in-memory snapshot after the newer write reached
                        // disk.
                        val current = XmpWriter.readOrNull(path) ?: return@withLockSuspend
                        syncCache(path, current.tags, current.rating)
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    android.util.Log.e("MediaRepository", "metadata cache sync failed for $path", e)
                }
            }
            metadataCacheJobs[path] = job
            job.invokeOnCompletion { metadataCacheJobs.remove(path, job) }
        }
    }

    /** Waits for cache writes already scheduled by a tag/rating batch to become visible to Room. */
    suspend fun awaitMetadataCacheSync(paths: Collection<String>) {
        paths.distinct().mapNotNull { metadataCacheJobs[it] }.joinAll()
    }

    /** Mirrors a file's current XMP tags/rating into the queryable cache: `media_cache` for rating
     * (still read by the rest of the app) and the normalized `media_tags` table (one row per tag)
     * for tag browsing/counting/search, which is cheaper and exact-match instead of substring-on-CSV. */
    private suspend fun syncCache(path: String, tags: List<String>, rating: Int) {
        try {
            context.mediaCacheDB.upsertAll(listOf(MediaCache(fullPath = path, tags = tags.joinToString(","), rating = rating, lastScanned = System.currentTimeMillis())))
        } catch (e: Exception) {
            android.util.Log.e("MediaRepository", "syncCache failed for $path", e)
        }
        try {
            context.mediaTagDB.replaceTagsForPath(path, tags)
            // Tag browsing/search screens keep a process-local summary cache. Invalidate it only
            // after the normalized table is complete, then notify once the write is genuinely
            // visible to readers; otherwise a batch could report success while the UI still served
            // the previous tag set for an indeterminate amount of time.
            invalidateTagsWithPathsCache()
            // Ratings back the built-in 1–5 star collections; publish only after the normalized
            // metadata cache is committed so their counts and covers update together.
            RefreshBus.trigger(pathsChanged = false, collectionsChanged = true)
        } catch (e: Exception) {
            android.util.Log.e("MediaRepository", "syncTags failed for $path", e)
        }
    }

    override fun deleteMediaBatch(paths: List<String>, onProgress: (done: Int, total: Int) -> Unit): Int {
        // Two phases: files first, then ALL database cleanup in chunked bulk statements. The
        // previous per-file version issued four single-row transactions per path (~19,000 for a
        // 4,800-item bin) that fought the observer-triggered background reloads for SQLite's
        // write lock - deletion started fast and then crawled (user report 2026-07-17, stuck at
        // 79/4800). One RefreshBus tick at the end, not one per file.
        var failed = 0
        val deleted = ArrayList<String>(paths.size)
        paths.forEachIndexed { i, p ->
            val realFile = context.resolveRecycleBinFile(p)
            val fileGone = try { !realFile.exists() || realFile.delete() } catch (e: Exception) {
                android.util.Log.e("MediaRepository", "deleteMediaBatch file delete failed", e)
                false
            }
            if (fileGone) {
                XmpWriter.deleteSidecars(realFile.path)
                deleted.add(p)
            } else {
                failed++
            }
            onProgress(i + 1, paths.size)
        }
        deleted.chunked(SQLITE_BATCH_CHUNK_SIZE).forEach { chunk ->
            try { context.mediaDB.deleteByPathsBatch(chunk) } catch (e: Exception) { android.util.Log.e("MediaRepository", "deleteMediaBatch media rows failed", e) }
            try { GalleryDatabase.getInstance(context.applicationContext).MediaReviewStateDao().clearAll(chunk) } catch (e: Exception) { android.util.Log.e("MediaRepository", "deleteMediaBatch review rows failed", e) }
            try { context.favoritesDB.deleteFavoritePathsBatch(chunk) } catch (e: Exception) { android.util.Log.e("MediaRepository", "deleteMediaBatch favorites failed", e) }
            try { context.mediaCacheDB.deleteByPathsBatch(chunk) } catch (e: Exception) { android.util.Log.e("MediaRepository", "deleteMediaBatch cache failed", e) }
            try { context.mediaTagDB.deleteAllForPathsBatch(chunk) } catch (e: Exception) { android.util.Log.e("MediaRepository", "deleteMediaBatch tags failed", e) }
        }
        if (deleted.isNotEmpty()) {
            syncDirectoriesFromMedia(force = true)
            RefreshBus.trigger(affectedPaths = deleted)
        }
        return failed
    }

    override fun deleteMedium(path: String): Boolean {
        val ok = deleteMediumInternal(path)
        if (ok) RefreshBus.trigger(affectedPaths = listOf(path))
        return ok
    }

    private fun deleteMediumInternal(path: String): Boolean {
        // File removal is verified FIRST and gates everything else - the old order deleted the DB/
        // favorites/cache/tag rows unconditionally and only then attempted File(path).delete(),
        // discarding its boolean result. Under scoped storage that call can return false (app
        // doesn't own the file, no MANAGE_EXTERNAL_STORAGE) without throwing, which used to make the
        // item silently vanish from the Recycle Bin UI - "permanently deleted" - while its bytes
        // stayed on disk, still taking up storage, untracked by this app. Now a failed file removal
        // leaves every row alone, so the item stays visible in the bin and the caller can tell the
        // user it didn't work instead of lying about it.
        // resolveRecycleBinFile: a legacy (widget/camera-review-only) recycle-bin row's stored path
        // is a "recycle_bin/..." placeholder, not a real one - File(path) on that string is never
        // found, which used to make this whole check silently pass ("already gone") without ever
        // touching the actual bytes still sitting in the app's internal recycle-bin folder. DB/
        // favorites/cache/tag rows below still key on the original `path` (the DB's real column
        // value) - only the on-disk file location is resolved differently.
        val realFile = context.resolveRecycleBinFile(path)
        val fileGone = try { !realFile.exists() || realFile.delete() } catch (e: Exception) {
            android.util.Log.e("MediaRepository", "deleteMedium File delete failed", e)
            false
        }
        if (!fileGone) return false
        XmpWriter.deleteSidecars(realFile.path)
        try { context.mediaDB.deleteMediumPath(path) } catch (e: Exception) { android.util.Log.e("MediaRepository", "deleteMedium DB failed", e) }
        try { GalleryDatabase.getInstance(context.applicationContext).MediaReviewStateDao().clear(path) } catch (e: Exception) { android.util.Log.e("MediaRepository", "deleteMedium review failed", e) }
        try { context.favoritesDB.deleteFavoritePath(path) } catch (e: Exception) { android.util.Log.e("MediaRepository", "deleteMedium Fav failed", e) }
        try { context.mediaCacheDB.deleteByPathSync(path) } catch (e: Exception) { android.util.Log.e("MediaRepository", "deleteMedium Cache failed", e) }
        try { repositoryScope.launch { context.mediaTagDB.deleteAllForPath(path) } } catch (e: Exception) { android.util.Log.e("MediaRepository", "deleteMedium Tags failed", e) }
        return true
    }

    fun getByMinRating(minRating: Int): List<Medium> =
        try { context.mediaDB.getByMinRating(minRating) } catch (_: Exception) { emptyList() }

    fun getMediaByPaths(paths: List<String>): List<Medium> =
        try { paths.chunked(SQLITE_BATCH_CHUNK_SIZE).flatMap { context.mediaDB.getMediaByPaths(it) } } catch (_: Exception) { emptyList() }

    fun getNewestMedia(limit: Int): List<Medium> =
        try { context.mediaDB.getNewestMedia(limit) } catch (_: Exception) { emptyList() }

    fun getNewMediaCountSince(since: Long): Int =
        if (since <= 0L) 0 else try { context.mediaDB.getNewMediaCountSince(since) } catch (_: Exception) { 0 }

    // COUNT (file count) is a folder-only sort option, never reachable for a media query - falls
    // back to name here purely so this `when` stays exhaustive.
    fun getMediaPaged(
        field: SortField,
        desc: Boolean,
        groupBy: GroupBy = GroupBy.NONE,
        excludedGroupValues: Set<String> = emptySet(),
    ): PagingSource<Int, Medium> {
        if (groupBy != GroupBy.NONE && groupBy != GroupBy.TAG) {
            return FilteredMediaPagingSource(
                buildFilteredMediaQuery(MediaFilter(), field, desc, pathsOnly = false, groupBy = groupBy, excludedGroupValues = excludedGroupValues),
                GalleryDatabase.getInstance(context.applicationContext),
            )
        }
        return when (field) {
        SortField.NAME, SortField.COUNT, SortField.RECENT -> if (desc) context.mediaDB.getMediaPagedByNameDesc() else context.mediaDB.getMediaPagedByNameAsc()
        SortField.DATE -> context.mediaDB.getMediaPagedByDate(desc)
        SortField.SIZE -> context.mediaDB.getMediaPagedBySize(desc)
        SortField.RATING -> context.mediaDB.getMediaPagedByRating(desc)
        }
    }

    suspend fun getActivePaths(): List<String> =
        try { context.mediaDB.getActivePaths() } catch (_: Exception) { emptyList() }

    /**
     * Searches the live library without touching MediaStore or the filesystem.
     *
     * The search panel used to open a MediaStore cursor over every file on the device for each
     * pause while typing, then stat every match and intersect the result with Room's active-path
     * set. That made a large library compete with the grid/viewer for the same I/O budget and could
     * briefly return rows the Media tab could not open. Room is the already-synchronised source of
     * truth here, so push all predicates into SQLite and return paths only.
     */
    data class TextSearchPreview(val paths: List<String>, val totalCount: Int)

    /** A compact, bounded preview for the search panel. Applying the query itself is done through
     * [MediaFilter], so a broad search never needs to materialize every matching path in memory. */
    suspend fun searchActiveMediaPreview(
        terms: List<String>,
        typeFilter: Int = 0,
        dateFilter: Int = 0,
        limit: Int = 20,
    ): TextSearchPreview = withContext(Dispatchers.IO) {
        val normalizedTerms = terms.map(String::trim).filter(String::isNotBlank).distinct()
        if (normalizedTerms.isEmpty()) return@withContext TextSearchPreview(emptyList(), 0)

        val where = StringBuilder("deleted_ts = 0")
        val args = mutableListOf<Any?>()
        normalizedTerms.forEach { term ->
            val escaped = term.lowercase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_")
            // A search term can match a filename, its path or one of the exact tag rows. Keeping
            // the EXISTS correlated to the active media query means tag searches inherit the same
            // deleted/type/date constraints without materializing the entire tag index in Kotlin.
            where.append(" AND (filename LIKE ? COLLATE NOCASE ESCAPE '\\' OR full_path LIKE ? COLLATE NOCASE ESCAPE '\\' OR EXISTS (SELECT 1 FROM media_tags mt WHERE mt.media_path = full_path AND mt.tag LIKE ? COLLATE NOCASE ESCAPE '\\'))")
            args += "%$escaped%"
            args += "%$escaped%"
            args += "%$escaped%"
        }
        if (typeFilter > 0) {
            where.append(" AND type = ?")
            args += typeFilter
        }
        val cutoff = when (dateFilter) {
            1 -> System.currentTimeMillis() - 86400000L
            2 -> System.currentTimeMillis() - 7 * 86400000L
            3 -> System.currentTimeMillis() - 30 * 86400000L
            4 -> System.currentTimeMillis() - 365 * 86400000L
            else -> 0L
        }
        if (cutoff > 0L) {
            where.append(" AND last_modified >= ?")
            args += cutoff
        }

        val db = GalleryDatabase.getInstance(context.applicationContext)
        val total = db.query(SimpleSQLiteQuery("SELECT COUNT(*) FROM media WHERE $where", args.toTypedArray())).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
        if (total == 0) return@withContext TextSearchPreview(emptyList(), 0)
        val queryArgs = args.toMutableList().apply { add(limit.coerceIn(1, 60)) }
        val query = SimpleSQLiteQuery(
            "SELECT full_path FROM media WHERE $where ORDER BY date_sort_key DESC, full_path COLLATE NOCASE ASC LIMIT ?",
            queryArgs.toTypedArray(),
        )
        val paths = db.query(query).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }
        TextSearchPreview(paths, total)
    }

    /** Full sorted path list (no LIMIT/OFFSET) - used to give the Viewer a complete swipe-through
     * list regardless of how much of the grid has been paged in. Cheap: full_path only, no thumbnails. */
    suspend fun getActivePathsSorted(field: SortField, desc: Boolean, groupBy: GroupBy = GroupBy.NONE): List<String> {
        return try {
            if (groupBy != GroupBy.NONE && groupBy != GroupBy.TAG) {
                GalleryDatabase.getInstance(context.applicationContext).query(
                    buildFilteredMediaQuery(MediaFilter(), field, desc, pathsOnly = true, groupBy = groupBy),
                ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }
            } else {
                when (field) {
                    SortField.NAME, SortField.COUNT, SortField.RECENT -> if (desc) context.mediaDB.getActivePathsByNameDesc() else context.mediaDB.getActivePathsByNameAsc()
                    SortField.DATE -> context.mediaDB.getActivePathsByDate(desc)
                    SortField.SIZE -> context.mediaDB.getActivePathsBySize(desc)
                    SortField.RATING -> context.mediaDB.getActivePathsByRating(desc)
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
        // MediaViewModel's prefetchSortedPathsAsync() deliberately cancels a still-running previous
        // call here whenever a newer one supersedes it (sort change, refresh, ...) - that's routine,
        // not a failure. Must rethrow (never fold into the catch-all below): a broad `catch (Exception)`
        // also matches CancellationException (and Compose's ForgottenCoroutineScopeException, thrown
        // when rememberCoroutineScope's scope leaves composition mid-call) since both are Exception
        // subtypes in Kotlin - swallowing it here previously turned a routine cancellation into a
        // cached emptyList() result (see MediaViewModel.activePathsSorted's cache), permanently
        // breaking the Media tab's Viewer entry (opened with zero pages - confirmed live via
        // `ViewerScreen ENTER paths.size=0`) until something happened to change the cache key.
            throw e
        } catch (_: Exception) { emptyList() }
    }

    /** Filtered counterpart of [getMediaPaged] - builds one dynamic SQL query covering every active
     * [MediaFilter] dimension (rating/tag/path-include/path-exclude/size/date) instead of the old
     * fetch-then-filter-in-Kotlin pipeline, so filtered browsing pages and invalidates exactly like
     * the unfiltered grid instead of being capped at a fixed in-memory fetch size. */
    fun getMediaPagedFiltered(
        filter: MediaFilter,
        field: SortField,
        desc: Boolean,
        groupBy: GroupBy = GroupBy.NONE,
        excludedGroupValues: Set<String> = emptySet(),
    ): PagingSource<Int, Medium> =
        FilteredMediaPagingSource(
            buildFilteredMediaQuery(filter, field, desc, pathsOnly = false, groupBy = groupBy, excludedGroupValues = excludedGroupValues),
            GalleryDatabase.getInstance(context.applicationContext),
        )

    /** Filtered counterpart of [getActivePathsSorted] - full sorted path list (no LIMIT/OFFSET)
     * matching the same filter, used so Viewer swipe-through and select-all/invert cover the
     * complete filtered set, not just what the grid has paged in so far. */
    suspend fun getActivePathsSortedFiltered(filter: MediaFilter, field: SortField, desc: Boolean, groupBy: GroupBy = GroupBy.NONE): List<String> = try {
        val query = buildFilteredMediaQuery(filter, field, desc, pathsOnly = true, groupBy = groupBy)
        GalleryDatabase.getInstance(context.applicationContext).query(query).use { cursor ->
            val paths = ArrayList<String>(cursor.count)
            while (cursor.moveToNext()) paths.add(cursor.getString(0))
            paths
        }
    } catch (_: Exception) { emptyList() }

    suspend fun getViewerPathWindow(
        filter: MediaFilter,
        field: SortField,
        desc: Boolean,
        groupBy: GroupBy,
        anchorPath: String,
        radius: Int,
    ): ViewerPathWindow = getViewerPathWindowImpl(filter, field, desc, groupBy, anchorPath, radius)

    /** Loads one bounded continuation page before or after [anchorPath]. */
    suspend fun getViewerPathChunk(
        filter: MediaFilter,
        field: SortField,
        desc: Boolean,
        groupBy: GroupBy,
        anchorPath: String,
        direction: ViewerLoadDirection,
        limit: Int,
    ): ViewerPathChunk = getViewerPathChunkImpl(filter, field, desc, groupBy, anchorPath, direction, limit)

    internal fun buildFilterWhereClause(filter: MediaFilter): Pair<String, List<Any?>> =
        buildFilterWhereClauseImpl(filter)

    /** Replaces the previous review outcome atomically. Passing null resets the item to undecided. */
    fun setReviewStatus(path: String, status: MediaReviewStatus?): Boolean = setReviewStatusImpl(path, status)

    fun setReviewStatus(paths: Collection<String>, status: MediaReviewStatus?): Boolean = setReviewStatusBatchImpl(paths, status)

    fun getReviewStatus(path: String): MediaReviewStatus? = getReviewStatusImpl(path)

    /** Keeps a review decision attached when MediaStore/MediaBatchWorker changes a file path. */
    fun updateReviewPath(oldPath: String, newPath: String) = updateReviewPathImpl(oldPath, newPath)

    fun getPersonScanMedia(folderPath: String, includeSubfolders: Boolean, result: PersonPresence): List<Medium> =
        getPersonScanMediaImpl(folderPath, includeSubfolders, result)

    fun getPersonScanCounts(folderPath: String, includeSubfolders: Boolean): Map<PersonPresence, Int> =
        getPersonScanCountsImpl(folderPath, includeSubfolders)

    /** Permanently excludes reviewed media from the no-person cleanup inbox until it is deleted. */
    fun keepPersonScanMedia(paths: Set<String>) = keepPersonScanMediaImpl(paths)

    /** Keeps every path-keyed app record attached when a file changes location. */
    fun migratePathMetadata(oldPath: String, newPath: String) = migratePathMetadataImpl(oldPath, newPath)

    private fun buildFilteredMediaQuery(
        filter: MediaFilter,
        sortField: SortField,
        desc: Boolean,
        pathsOnly: Boolean,
        groupBy: GroupBy = GroupBy.NONE,
        excludedGroupValues: Set<String> = emptySet(),
    ): SupportSQLiteQuery =
        buildFilteredMediaQueryImpl(filter, sortField, desc, pathsOnly, groupBy, excludedGroupValues)

    /** True total row count for a MediaFilter matching exactly what [getMediaPagedFiltered] loads. */
    suspend fun getFilteredMediaCount(filter: MediaFilter): Int = getFilteredMediaCountImpl(filter)

    /** Exact SQL-backed group summaries (labels + totals) for grouped Paging views. */
    suspend fun getGroupedMediaSummaries(
        filter: MediaFilter,
        groupBy: GroupBy,
        desc: Boolean,
        unratedLabel: String,
    ): List<GroupSummary> = getGroupedMediaSummariesImpl(filter, groupBy, desc, unratedLabel)

    suspend fun getTaggedPaths(): Set<String> =
        try { context.mediaTagDB.getTaggedPaths().toSet() } catch (_: Exception) { emptySet() }

    @Volatile private var tagCountsCache: Map<String, Int>? = null
    @Volatile private var tagCountsCacheGeneration: Long = -1L
    private val tagCountsCacheMutex = Mutex()

    /** Shared generation-aware tag counts cache for Filter, Explorer and Tag Browser surfaces. */
    suspend fun getTagCountsCached(): Map<String, Int> = tagCountsCacheMutex.withLock {
        val generation = RefreshBus.generation
        tagCountsCache?.takeIf { tagCountsCacheGeneration == generation }?.let { return@withLock it }
        val counts = try { context.mediaTagDB.getTagCounts().associate { it.tag to it.cnt } } catch (_: Exception) { emptyMap() }
        tagCountsCache = counts
        tagCountsCacheGeneration = generation
        counts
    }

    suspend fun getTagCounts(): Map<String, Int> = getTagCountsCached()

    suspend fun getAllTags(): List<String> =
        getTagCountsCached().keys.sorted()

    /** Tag -> the file paths carrying it. Backed by the normalized `media_tags` table (exact-match
     * per tag) instead of splitting/scanning the old comma-joined `media_cache.tags` column, which
     * could false-match a tag as a substring of another (e.g. "Auto" inside a file tagged "Autobahn").
     *
     * A GROUP_CONCAT-based variant (one row per tag instead of one row per (tag, path) pair) was
     * tried here, on the theory that fewer Room rows would be cheaper to materialize. Measured it
     * was ~3x SLOWER on-device (2.4s vs 0.8s) and confirmed independently via a local sqlite3 EXPLAIN
     * (GROUP_CONCAT's incremental buffer growth over hundreds of tag groups, several with 600-1000+
     * paths, costs more than the plain per-pair scan saves) - so it was reverted. */
    suspend fun getAllTagsWithPaths(): Map<String, List<String>> =
        try { context.mediaTagDB.getAllTagPathPairs().groupBy({ it.tag }, { it.path }) } catch (_: Exception) { emptyMap() }

    /** Tag suggestions ranked by overall usage frequency (previously approximated recency by
     * sampling the 1000 most-recently-scanned cache rows and recounting client-side; global
     * frequency via the normalized table is simpler and at least as good a signal). */
    suspend fun getTagSuggestions(limit: Int): List<Pair<String, Int>> =
        getTagCountsCached().entries.sortedByDescending { it.value }.take(limit).map { it.key to it.value }

    suspend fun getPathsForTag(tag: String): Set<String> =
        try { context.mediaTagDB.getPathsForTag(tag).toSet() } catch (_: Exception) { emptySet() }

    /** Path -> all tags carried by that one file, batched for a bounded set of [paths] (a single
     * folder's contents, never the full library) - used by tag-hierarchy grouping. */
    suspend fun getTagsForPaths(paths: List<String>): Map<String, List<String>> =
        try {
            paths.distinct().chunked(SQLITE_BATCH_CHUNK_SIZE)
                .flatMap { context.mediaTagDB.getTagsForPaths(it) }
                .groupBy({ it.path }, { it.tag })
        } catch (_: Exception) { emptyMap() }

    fun getDeletedPaths(): Set<String> =
        try { context.mediaDB.getDeletedMedia().map { it.path }.toSet() } catch (_: Exception) { emptySet() }

    fun getFavorites(): List<Medium> =
        try { context.mediaDB.getFavorites() } catch (_: Exception) { emptyList() }

    fun getAllDirectories(): List<Directory> = try {
        val directories = context.directoryDB.getAll()
        if (directories.isEmpty()) directories else {
            // Directory rows predate the separate image/video totals. Enrich the whole result with
            // one grouped SQL query instead of querying each folder (the old N+1 shape made opening
            // the drawer increasingly expensive on larger libraries).
            val counts = getDirectoryAggregatesForUi().associateBy { it.parentPath }
            directories.map { directory ->
                counts[directory.path]?.let { aggregate ->
                    directory.copy(imageCount = aggregate.imageCount, videoCount = aggregate.videoCount)
                } ?: directory
            }
        }
    } catch (_: Exception) { emptyList() }

    /** One grouped query at most per process/refresh cycle; used to enrich legacy Directory rows. */
    fun getDirectoryAggregatesForUi(): List<DirectoryAggregate> = try {
        directoryAggregatesCache ?: context.mediaDB.getDirectoryAggregates().also { directoryAggregatesCache = it }
    } catch (_: Exception) {
        emptyList()
    }

    fun getCollections(): List<MediaCollection> =
        try { context.collectionDB.getAll() } catch (_: Exception) { emptyList() }

    fun insertCollection(collection: MediaCollection): Long = try {
        context.collectionDB.insert(collection).also { id ->
            if (id > 0L) RefreshBus.trigger(pathsChanged = false, collectionsChanged = true)
        }
    } catch (_: Exception) { -1L }

    fun deleteCollection(collection: MediaCollection) {
        try {
            context.collectionDB.delete(collection)
            RefreshBus.trigger(pathsChanged = false, collectionsChanged = true)
        } catch (_: Exception) { }
    }

    fun getDeletedMedia(): List<Medium> =
        try { context.mediaDB.getDeletedMedia() } catch (_: Exception) { emptyList() }

    /** Pulls the *shared* MediaStore trash (Android 11+) into the app's recycle-bin view, so files
     * trashed by other apps - or by us but not yet in the DB - show up here too. Additive: every
     * currently-trashed image/video is ensured to have a soft-deleted row. Returns how many rows were
     * newly added, so the screen can tell the user what a refresh pulled in. */
    fun syncTrashFromMediaStore(): Int {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) return 0
        return try {
            val uri = android.provider.MediaStore.Files.getContentUri("external")
            val projection = arrayOf(
                android.provider.MediaStore.MediaColumns.DATA,
                android.provider.MediaStore.MediaColumns.DISPLAY_NAME,
                android.provider.MediaStore.MediaColumns.SIZE,
                android.provider.MediaStore.MediaColumns.DATE_MODIFIED,
                android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE,
            )
            val args = android.os.Bundle().apply {
                putInt(android.provider.MediaStore.QUERY_ARG_MATCH_TRASHED, android.provider.MediaStore.MATCH_ONLY)
                putString(
                    android.content.ContentResolver.QUERY_ARG_SQL_SELECTION,
                    "${android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE} = ? OR ${android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE} = ?",
                )
                putStringArray(
                    android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                    arrayOf(
                        android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
                        android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
                    ),
                )
            }
            val existing = try { context.mediaDB.getDeletedMedia().map { it.path }.toSet() } catch (_: Exception) { emptySet() }
            val found = ArrayList<Medium>()
            context.contentResolver.query(uri, projection, args, null)?.use { c ->
                val dataIdx = c.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.DATA)
                val nameIdx = c.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeIdx = c.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.SIZE)
                val dateIdx = c.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.DATE_MODIFIED)
                val typeIdx = c.getColumnIndexOrThrow(android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE)
                val now = System.currentTimeMillis()
                while (c.moveToNext()) {
                    val path = c.getString(dataIdx) ?: continue
                    if (path in existing) continue
                    val rawName = c.getString(nameIdx) ?: File(path).name
                    // Show the original filename, not the ".trashed-<expiry>-name" the OS renamed it to.
                    val displayName = rawName.replaceFirst(Regex("^\\.trashed-\\d+-"), "")
                    val size = c.getLong(sizeIdx)
                    val modified = c.getLong(dateIdx) * 1000L
                    val isVideo = c.getInt(typeIdx) == android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                    found.add(Medium(null, displayName, path, File(path).parent ?: "", modified, modified, size, if (isVideo) 2 else 1, 0, false, now, 0L, 0))
                }
            }
            if (found.isNotEmpty()) {
                context.mediaDB.insertAllKeepingExisting(found)
                found.forEach { runCatching { context.mediaDB.softDelete(it.path, it.deletedTS) } }
            }
            found.size
        } catch (e: Exception) {
            android.util.Log.e("MediaRepository", "syncTrashFromMediaStore failed", e); 0
        }
    }

    suspend fun getTagsInFolder(folder: String): Map<String, Int> =
        try {
            val pattern = if (folder.endsWith("/")) "$folder%" else "$folder/%"
            context.mediaTagDB.getTagCountsInFolder(pattern).associate { it.tag to it.cnt }
        } catch (_: Exception) { emptyMap() }

    fun getValidFavoritePaths(): List<String> =
        try { context.favoritesDB.getValidFavoritePaths() } catch (_: Exception) { emptyList() }

    fun addFavoriteByPath(path: String) {
        try { context.favoritesDB.insert(context.getFavoriteFromPath(path)) } catch (_: Exception) { }
    }

    /** DB-only rating write (no XMP). Used when importing ratings that already exist on disk. */
    fun setDbRating(path: String, rating: Int) {
        try { context.mediaDB.updateRating(path, rating.coerceIn(0, 5)) } catch (e: Exception) { android.util.Log.e("MediaRepository", "setDbRating failed", e) }
    }

    fun setDbRatingBatch(paths: Collection<String>, rating: Int) {
        val normalizedRating = rating.coerceIn(0, 5)
        try { paths.distinct().chunked(SQLITE_BATCH_CHUNK_SIZE).forEach { context.mediaDB.updateRatingBatch(it, normalizedRating) } } catch (e: Exception) { android.util.Log.e("MediaRepository", "setDbRatingBatch failed", e) }
    }

    /** Writes the rating into a file's XMP sidecar only - no DB or cache write. Batch rating changes
     * update the DB only for paths whose XMP write succeeded via [setDbRatingBatch]. */
    fun writeRatingXmp(path: String, rating: Int): Boolean {
        val normalizedRating = rating.coerceIn(0, 5)
        return withMetadataWriteLock(path) {
            val current = XmpWriter.readOrNull(path) ?: return@withMetadataWriteLock false
            val success = XmpWriter.write(path, current.tags, normalizedRating)
            if (success) refreshExternalMediaStoreTimestamp(path)
            success
        }
    }

    /** Refreshes cache rows after a successful XMP-only batch write, in one place. */
    fun refreshMetadataCache(paths: Collection<String>) {
        paths.distinct().forEach(::enqueueMetadataCacheSync)
    }

    /** After writing a rating into a file, make the change visible to *other* apps' MediaStore-backed
     * pickers (Instagram et al., which sort by DATE_MODIFIED): bump the media file's on-disk mtime and
     * hand the path to the MediaScanner so MediaStore refreshes its DATE_MODIFIED for it.
     *
     * Deliberately does NOT touch our own DB row. The Media tab sorts on date_sort_key (date_added-
     * preferring) and the incremental sync IGNOREs already-known paths (insertAllKeepingExisting), so
     * this can never reorder our own date-sorted grid - a rating edit must surface as "recently changed"
     * to external pickers without looking like newly-added media inside this app. JPEG embedded-XMP
     * writes already move the mtime; the explicit setLastModified is what covers sidecar formats (PNG,
     * ...) whose media file is otherwise left untouched by the rating write. */
    private fun refreshExternalMediaStoreTimestamp(path: String) {
        try {
            val f = File(path)
            if (f.exists()) f.setLastModified(System.currentTimeMillis())
        } catch (e: Exception) {
            android.util.Log.e("MediaRepository", "setLastModified failed for $path", e)
        }
        runCatching {
            android.media.MediaScannerConnection.scanFile(context, arrayOf(path), null, null)
        }.onFailure { android.util.Log.e("MediaRepository", "scanFile failed for $path", it) }
    }

    /** Decodes only the bounds of an image to derive its aspect ratio. Lives here so no Composable
     * ever touches [android.graphics.BitmapFactory] / the file system directly. */
    fun decodeImageAspect(path: String): Float = try {
        val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(path, opts)
        if (opts.outWidth > 0 && opts.outHeight > 0) opts.outWidth.toFloat() / opts.outHeight.toFloat() else 1f
    } catch (_: Exception) { 1f }

    // ---- Media loading: the repository owns all MediaStore / disk enumeration ----

    internal val videoExts = setOf("mp4", "mkv", "mov", "3gp", "wmv", "flv", "avi")
    internal val imageExts = setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "avif", "bmp", "svg", "apng", "jxl")

    /**
     * Pulls newly added images/videos from MediaStore (plus recent disk folders) into the local DB.
     * Returns the media that was actually newly inserted, so callers (e.g. [org.fossify.gallery.workers.MediaSyncWorker])
     * can limit any follow-up work (like rebuilding directory rows) to just the affected folders.
     */
    /**
     * Rebuilds the `directories` cache from the media table (count, newest thumbnail, types, size
     * per folder) and drops rows whose folder has no live media left. Before this existed nothing
     * in the Compose flow ever added a directory row for a NEW folder (only the fresh-DB bootstrap
     * did), so a folder created after first launch showed its files in the Media tab but never
     * appeared in Albums - and deleted folders lingered as ghost albums with stale counts.
     */
    /**
     * Removes live rows whose on-disk file vanished without the app's involvement (deleted via
     * PC/MTP, another app, a shell) - the store sync only ever ADDS rows, so such ghosts stayed
     * visible forever. Streams paths rowid-keyed in chunks (never materialises the whole library),
     * then bulk-deletes the missing ones from media/favorites/cache/tags. Returns the pruned count.
     * Recycle-bin rows are deliberately skipped: their placeholder paths resolve elsewhere and the
     * 30-day bin expiry owns their cleanup.
     */
    fun pruneMissingMedia(): Int {
        val missing = mutableListOf<String>()
        try {
            var afterRowId = 0L
            while (true) {
                val chunk = context.mediaDB.getLivePathsAfter(afterRowId, 800)
                if (chunk.isEmpty()) break
                chunk.filterTo(missing) { !File(it).exists() }
                afterRowId = context.mediaDB.getRowId(chunk.last()) ?: break
            }
            if (missing.isEmpty()) return 0
            missing.chunked(SQLITE_BATCH_CHUNK_SIZE).forEach { batch ->
                context.mediaDB.deleteByPathsBatch(batch)
                context.favoritesDB.deleteFavoritePathsBatch(batch)
                context.mediaCacheDB.deleteByPathsBatch(batch)
                context.mediaTagDB.deleteAllForPathsBatch(batch)
            }
            android.util.Log.i("MediaRepository", "pruneMissingMedia removed ${missing.size} ghost rows")
            syncDirectoriesFromMedia(force = true)
            RefreshBus.trigger(affectedPaths = missing)
        } catch (e: Exception) {
            android.util.Log.e("MediaRepository", "pruneMissingMedia failed", e)
        }
        return missing.size
    }

    /** Throttled wrapper for the periodic callers - a full-library exists sweep costs real I/O. */
    fun pruneMissingMediaIfDue(minIntervalMs: Long = 6 * 60 * 60 * 1000L) {
        val now = System.currentTimeMillis()
        if (now - context.config.lastMissingMediaSweep < minIntervalMs) return
        context.config.lastMissingMediaSweep = now
        pruneMissingMedia()
    }

    /** Repairs rows that were inserted while MediaStore still reported a zero-sized/1970 file.
     * The media bytes are not rewritten: the files are valid, and the local library metadata is
     * rebuilt from their real length, mtime and (where available) EXIF capture time. */
    fun repairInvalidMediaMetadata(): Int {
        val candidates = try {
            context.mediaDB.getMetadataRepairRows()
        } catch (e: Exception) {
            android.util.Log.e("MediaRepository", "metadata repair query failed", e)
            return 0
        }
        if (candidates.isEmpty()) return 0

        val repairedPaths = ArrayList<String>()
        candidates.forEach { row ->
            val recovered = recoverFileMetadata(row.path, row.modified, row.taken, row.size, row.added) ?: return@forEach
            if (recovered.modified == row.modified && recovered.taken == row.taken &&
                recovered.size == row.size && recovered.added == row.added) return@forEach
            try {
                context.mediaDB.repairMetadata(
                    path = row.path,
                    modified = recovered.modified,
                    taken = recovered.taken,
                    size = recovered.size,
                    added = recovered.added,
                )
                repairedPaths += row.path
            } catch (e: Exception) {
                android.util.Log.e("MediaRepository", "metadata repair failed for ${row.path}", e)
            }
        }

        if (repairedPaths.isNotEmpty()) {
            syncDirectoriesFromMedia(force = true)
            // Let Android's provider catch up as well, so external pickers no longer see the stale
            // zero-sized row. The local Room repair above is immediate and does not wait for this
            // asynchronous scanner callback.
            runCatching {
                MediaScannerConnection.scanFile(context, repairedPaths.toTypedArray(), null, null)
            }.onFailure { android.util.Log.w("MediaRepository", "metadata repair scan failed", it) }
            android.util.Log.i("MediaRepository", "repaired ${repairedPaths.size} media metadata rows")
        }
        return repairedPaths.size
    }

    fun syncDirectoriesFromMedia(force: Boolean = false) {
        try {
            // Rebuilding WRITES the whole directories table (REPLACE per row) - during a mass
            // deletion the MediaStore observer fires RefreshBus every ~1.5s, and an un-throttled
            // rebuild per Albums reload kept grabbing SQLite's write lock away from the deletion
            // loop. 15s of directory-count staleness is invisible; callers that just changed the
            // media table (store sync, batch delete) pass force=true so their final state lands.
            val now = System.currentTimeMillis()
            if (!force && now - lastDirectorySyncMs < 15_000) return
            lastDirectorySyncMs = now
            val aggregates = context.mediaDB.getDirectoryAggregates().also { directoryAggregatesCache = it }
            // Empty media table means fresh DB or mid-bootstrap - don't wipe rows based on nothing.
            if (aggregates.isEmpty()) return
            val dirs = aggregates.map { agg ->
                org.fossify.gallery.models.Directory(
                    id = null,
                    path = agg.parentPath,
                    tmb = agg.thumbnail ?: "",
                    name = File(agg.parentPath).name,
                    mediaCnt = agg.count,
                    modified = agg.maxModified,
                    taken = agg.maxTaken,
                    size = agg.totalSize,
                    location = if (agg.parentPath.startsWith("/storage/") && !agg.parentPath.startsWith("/storage/emulated")) LOCATION_SD else LOCATION_INTERNAL,
                    types = (if (agg.hasImages > 0) 1 else 0) or (if (agg.hasVideos > 0) 2 else 0),
                    sortValue = "",
                    imageCount = agg.imageCount,
                    videoCount = agg.videoCount,
                )
            }
            context.directoryDB.insertAll(dirs)
            context.directoryDB.deleteDirectoriesWithoutMedia()
        } catch (e: Exception) {
            android.util.Log.e("MediaRepository", "syncDirectoriesFromMedia failed", e)
        }
    }

    /**
     * @param fullRescan ignores the stored watermark for this one run and re-examines every
     * image/video MediaStore knows about. Used by the one-shot gap repair (see
     * MediaSyncWorker.scheduleGapRepair) to recover rows an older, truncating version of this sync
     * dropped below the watermark. Deliberately does NOT go down the isFirstSync path: that one adds
     * a full `scanMediaFromDisk()` and merges both lists in memory, which is far heavier than simply
     * letting the (now batched) cursor loop below walk everything and insert only what is missing.
     */
    /** One-time pass that fills in video_duration for legacy rows stored as 0 (every video before the
     * incremental sync learned to read MediaStore's DURATION column). One MediaStore query over all
     * videos, then a single transactional batch update, guarded by a config flag so it runs once.
     * Returns true when it actually did the backfill (so the caller can refresh derived caches). */
    internal fun backfillVideoDurations(): Boolean {
        if (context.config.videoDurationsBackfilled) return false
        try {
            val uri = MediaStore.Files.getContentUri("external")
            val proj = arrayOf(
                MediaStore.MediaColumns.DATA,
                MediaStore.MediaColumns.RELATIVE_PATH,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.DURATION,
            )
            val sel = "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?"
            val args = arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString())
            val storageRoot = Environment.getExternalStorageDirectory().absolutePath
            val durations = HashMap<String, Int>()
            context.contentResolver.query(uri, proj, sel, args, null)?.use { c ->
                val dataIdx = c.getColumnIndex(MediaStore.MediaColumns.DATA)
                val relIdx = c.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                val nameIdx = c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                val durIdx = c.getColumnIndex(MediaStore.Files.FileColumns.DURATION)
                while (c.moveToNext()) {
                    val path = SyncFields.resolvePath(
                        data = if (dataIdx >= 0) c.getString(dataIdx) else null,
                        storageRoot = storageRoot,
                        relativePath = if (relIdx >= 0) c.getString(relIdx) else null,
                        displayName = if (nameIdx >= 0) c.getString(nameIdx) else null,
                    ) ?: continue
                    val dur = ((if (durIdx >= 0) c.getLong(durIdx) else 0L) / 1000L).toInt()
                    if (dur > 0) durations[path] = dur
                }
            }
            if (durations.isNotEmpty()) context.mediaDB.updateVideoDurations(durations)
            context.config.videoDurationsBackfilled = true
            return true
        } catch (e: Exception) {
            android.util.Log.e("MediaRepository", "backfillVideoDurations failed", e)
            return false
        }
    }

    fun syncNewMediaFromStore(fullRescan: Boolean = false): List<Medium> =
        syncNewMediaFromStoreImpl(fullRescan)

    /**
     * Runs a sync off the caller's thread and serializes process-local callers. Incremental calls
     * share the same in-flight Deferred; a manual pull and the observer worker therefore never do
     * two scans back-to-back. Full repair scans remain exclusive and are not allowed to accidentally
     * join a cheaper incremental request.
     */
    suspend fun syncNewMediaFromStoreCoalesced(
        fullRescan: Boolean = false,
        source: String = "background",
    ): List<Medium> {
        if (fullRescan) {
            return mediaStoreSyncMutex.withLock {
                withContext(Dispatchers.IO) { runTrackedSync(fullRescan = true, source = source) }
            }
        }

        var shouldStart = false
        val deferred = synchronized(incrementalSyncLock) {
            activeIncrementalSync?.takeIf { it.isActive } ?: incrementalSyncScope.async(start = CoroutineStart.LAZY) {
                mediaStoreSyncMutex.withLock {
                    withContext(Dispatchers.IO) { runTrackedSync(fullRescan = false, source = source) }
                }
            }.also { created ->
                shouldStart = true
                activeIncrementalSync = created
                created.invokeOnCompletion {
                    synchronized(incrementalSyncLock) {
                        if (activeIncrementalSync === created) activeIncrementalSync = null
                    }
                }
            }
        }
        if (shouldStart) deferred.start()
        return deferred.await()
    }

    private fun runTrackedSync(fullRescan: Boolean, source: String): List<Medium> {
        val startedAt = android.os.SystemClock.elapsedRealtime()
        LibrarySyncStatus.started(fullRescan)
        android.util.Log.d("MediaRepository", "library sync started source=$source full=$fullRescan")
        return try {
            val added = syncNewMediaFromStore(fullRescan)
            LibrarySyncStatus.completed(added.size, android.os.SystemClock.elapsedRealtime() - startedAt)
            android.util.Log.d(
                "MediaRepository",
                "library sync finished source=$source added=${added.size} durationMs=${android.os.SystemClock.elapsedRealtime() - startedAt}",
            )
            added
        } catch (e: Throwable) {
            LibrarySyncStatus.failed(e, android.os.SystemClock.elapsedRealtime() - startedAt)
            throw e
        }
    }

    /** Full MediaStore enumeration with a recursive disk fallback — used when the local DB is empty. */
    fun scanMediaFromDisk(): List<Medium> = scanMediaFromDiskImpl()

    fun moveToRecycleBin(path: String) = moveToRecycleBinImpl(path)

    fun mediaPathsUnderFolders(folderPaths: Collection<String>): List<String> = mediaPathsUnderFoldersImpl(folderPaths)

    fun moveToRecycleBinBatch(paths: Collection<String>) = moveToRecycleBinBatchImpl(paths)

    fun restoreFromRecycleBin(path: String, notify: Boolean = true): Boolean = restoreFromRecycleBinImpl(path, notify)

    /** Restores all paths and returns the number of items that could not be restored. */
    fun restoreFromRecycleBinBatch(paths: Collection<String>): Int = restoreFromRecycleBinBatchImpl(paths)

    // ---- Small in-memory caches for screens whose whole composable tree is disposed and recreated
    // whenever Navigation-Compose pushes another destination (Viewer, Settings, ...) on top of Home
    // and the user navigates back. A plain remember{} in those screens gets wiped on that round trip,
    // forcing an expensive DB/query cascade to rerun every time (this is what happened with
    // ExplorerScreen's full-device MediaStore scan; see MediaStoreOps for the analogous fix there).
    // This repo instance itself is remember{}-scoped above the NavHost, so it survives that disposal.

    @Volatile private var favoritesCache: Pair<List<Medium>, List<Directory>>? = null
    @Volatile private var favoritesCacheGeneration: Long = -1L

    fun getFavoritesCached(): Pair<List<Medium>, List<Directory>>? = favoritesCache

    /** True when Favorites may have been changed since its process-level cache was built. */
    fun isFavoritesCacheStale(): Boolean =
        favoritesCache == null || favoritesCacheGeneration != RefreshBus.generation

    suspend fun refreshFavoritesCache(): Pair<List<Medium>, List<Directory>> = coroutineScope {
        val generation = RefreshBus.generation
        val mediaDeferred = async(Dispatchers.IO) { getFavorites() }
        val dirsDeferred = async(Dispatchers.IO) {
            val paths = context.config.favoriteFolders
            if (paths.isEmpty()) emptyList() else getAllDirectories().filter { it.path in paths }
        }
        (mediaDeferred.await() to dirsDeferred.await()).also {
            favoritesCache = it
            // If a write lands while the two queries are running, retaining the starting generation
            // deliberately leaves the cache stale. The next entry/refresh then retries against the
            // newer committed snapshot instead of silently accepting a mixed result.
            favoritesCacheGeneration = generation
        }
    }

    @Volatile private var collectionsCache: List<MediaCollection>? = null
    @Volatile private var collectionMediaCache: Map<Long, List<String>> = emptyMap()
    // RefreshBus.collectionGeneration the caches above were last built at. Lets CollectionsScreen
    // tell, on re-entry, whether a change that can affect a collection's result set happened since
    // (a delete inside a Collection bumps the generation) - if so the cached per-collection
    // counts/covers are stale and must be rebuilt, otherwise the cache is served as-is so navigation
    // round trips stay cheap. -1 = never built.
    @Volatile private var collectionsCacheGeneration: Long = -1L

    fun getCollectionsCached(): List<MediaCollection>? = collectionsCache

    fun getCollectionMediaCached(collectionId: Long): List<String> = collectionMediaCache[collectionId] ?: emptyList()

    /** True when the collections cache is missing or a media mutation happened since it was built -
     * i.e. its per-collection counts/covers can no longer be trusted and it should be reloaded. */
    fun isCollectionsCacheStale(): Boolean =
        collectionsCache == null || collectionsCacheGeneration != RefreshBus.collectionGeneration

    suspend fun refreshCollectionsCache(): List<MediaCollection> {
        // Snapshot the generation up front: any mutation that lands mid-rebuild bumps it past this
        // value, correctly leaving the cache marked stale so the next entry rebuilds again.
        val gen = RefreshBus.collectionGeneration
        val userCollections = getCollections()
        // These are virtual smart collections, not rows the user has to create or maintain. Their
        // negative IDs keep them distinct from Room's auto-generated collection IDs and make them
        // read-only in the Collections tab.
        val systemCollections = withContext(Dispatchers.IO) { buildSystemCollections() }
        val colls = systemCollections.map { it.first } + userCollections
        // A collection's smart predicate is independent of every other collection. Resolve those
        // bounded result sets concurrently so ten small collections do not turn a refresh into a
        // visibly serial N+1 waterfall. The cache still publishes as one immutable snapshot, so a
        // screen can never observe half of the collections from the old generation and half new.
        val resolved = coroutineScope {
            userCollections.map { collection ->
                async(Dispatchers.IO) { collection.id to getCollectionPaths(collection) }
            }.awaitAll()
        }
        collectionMediaCache = systemCollections.associate { it.first.id to it.second } + resolved.toMap()
        collectionsCache = colls
        collectionsCacheGeneration = gen
        return colls
    }

    private fun buildSystemCollections(): List<Pair<MediaCollection, List<String>>> {
        val favorites = context.mediaDB.getFavorites().map { it.path }
        val ratingCollections = (1..5).map { rating ->
            val paths = context.mediaDB.getPathsWithExactRating(rating)
            MediaCollection(
                id = SYSTEM_COLLECTION_RATING_BASE - rating,
                name = context.resources.getQuantityString(R.plurals.system_collection_rating, rating, rating),
                mediaPaths = MediaCollection.createPathsJson(paths),
            ) to paths
        }
        return listOf(
            MediaCollection(
                id = SYSTEM_COLLECTION_FAVORITES,
                name = context.getString(R.string.nav_favorites),
                mediaPaths = MediaCollection.createPathsJson(favorites),
            ) to favorites,
        ) + ratingCollections
    }

    /** Resolves a collection's matching paths via the exact same MediaFilter this collection's
     * content view builds in ComposeExplorerActivity.applyCollection() - previously this queried
     * unresolved content:// URIs through a non-recursive exact-match DAO call that could never
     * match anything, and ignored tagFilter/ratingFilter/excludedPaths entirely, so any collection
     * using included folders (or tag/rating filters) showed "0 Medien" here despite having real
     * content when opened. */
    private suspend fun getCollectionPaths(coll: MediaCollection): List<String> {
        val tagNames = if (coll.tagFilter.isNotBlank()) {
            val names = coll.tagFilter.split(",").map { it.trim() }.filter { it.isNotBlank() }
            expandTagsWithDescendants(names, context.config.tagHierarchy).ifEmpty { null }
        } else {
            null
        }
        val incPaths = coll.getIncludedPaths().mapNotNull { resolveContentUriToPath(it) }.filter { it.isNotEmpty() }.toSet()
        val excPaths = coll.getExcludedPaths().mapNotNull { resolveContentUriToPath(it) }.filter { it.isNotEmpty() }.toSet()
        val filter = MediaFilter(
            rating = coll.ratingFilter,
            tagNames = tagNames,
            pathFilter = incPaths.ifEmpty { null },
            excludePaths = excPaths.ifEmpty { null },
            minSize = coll.minSize,
            dateRange = coll.dateRange,
            typeFilter = coll.typeFilter,
        )
        // Manual members are an additive part of a collection, not another filter dimension. This
        // is what makes a Stack stable: changing its group or smart filters cannot silently make a
        // manually added image disappear. getMediaByPaths is chunked under SQLite's bind limit and
        // also drops deleted/missing rows, so the cache stays cheap and self-healing.
        val manualPaths = coll.getMediaPaths()
            .mapNotNull(::resolveContentUriToPath)
            .filter(String::isNotBlank)
        val manualMediaByPath = if (manualPaths.isEmpty()) {
            emptyMap()
        } else {
            getMediaByPaths(manualPaths).associateBy { it.path }
        }
        val manualResult = manualPaths.mapNotNull { manualMediaByPath[it] }
            .sortedByDescending { it.dateSortKey }
            .map { it.path }

        val filteredResult = if (filter.isActive) {
            getActivePathsSortedFiltered(filter, SortField.DATE, true)
        } else {
            emptyList()
        }
        return (manualResult + filteredResult).distinct()
    }

    @Volatile private var tagsWithPathsCache: Map<String, List<String>>? = null
    private var tagsCacheGeneration = -1L
    private val tagsCacheMutex = kotlinx.coroutines.sync.Mutex()

    fun getTagsWithPathsCached(): Map<String, List<String>>? = tagsWithPathsCache

    fun isTagsWithPathsCacheStale(): Boolean =
        tagsWithPathsCache == null || tagsCacheGeneration != RefreshBus.generation

    private fun invalidateTagsWithPathsCache() {
        tagsWithPathsCache = null
        tagsCacheGeneration = -1L
    }

    // TagBrowserScreen and ComposeExplorerActivity both hold their own RefreshBus subscription and
    // both call this on every event - since they share this one MediaRepository instance (via
    // LocalMediaRepository), that's the same full media_tags scan run twice per event. The mutex
    // serializes concurrent callers, and the freshness check lets the second one reuse what the
    // first just computed instead of repeating the query.
    suspend fun refreshTagsWithPathsCache(): Map<String, List<String>> = tagsCacheMutex.withLock {
        val cached = tagsWithPathsCache
        if (cached != null && tagsCacheGeneration == RefreshBus.generation) {
            return@withLock cached
        }
        getAllTagsWithPaths().also {
            tagsWithPathsCache = it
            tagsCacheGeneration = RefreshBus.generation
        }
    }

    /** Shared tag snapshot for surfaces that need both tag counts and the matching media paths. */
    suspend fun getTagCatalogCached(): TagCatalog =
        TagCatalog(refreshTagsWithPathsCache())
}

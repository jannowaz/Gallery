package org.fossify.gallery.helpers

import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import androidx.paging.PagingSource
import org.fossify.gallery.compose.screens.SortField
import org.fossify.gallery.extensions.collectionDB
import org.fossify.gallery.extensions.directoryDB
import org.fossify.gallery.extensions.favoritesDB
import org.fossify.gallery.extensions.getFavoriteFromPath
import org.fossify.gallery.extensions.mediaDB
import org.fossify.gallery.extensions.mediaCacheDB
import org.fossify.gallery.extensions.mediaTagDB
import org.fossify.gallery.extensions.config
import org.fossify.gallery.models.Directory
import org.fossify.gallery.models.MediaCollection
import org.fossify.gallery.models.Medium
import org.fossify.gallery.models.MediaCache
import org.fossify.gallery.models.MediaTag
import org.fossify.gallery.models.TagCount
import org.fossify.gallery.models.TagPathRow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.io.File

// SQLite's default host-parameter limit is 999 (older bundled versions; some newer builds raise it,
// but Android's bundled version cannot be relied on to). Room expands a "full_path IN (:paths)" query
// into one bound parameter per element, so a single unchunked call with e.g. a "select all" batch of a
// few thousand paths can exceed that limit and throw at runtime. Chunk comfortably under it instead.
private const val SQLITE_BATCH_CHUNK_SIZE = 900

class MediaRepository(private val context: Context) : MediaRepositoryInterface {
    private val repositoryScope = CoroutineScope(Dispatchers.IO)

    override fun getMediaFromPath(path: String): List<Medium> {
        return try {
            context.mediaDB.getMediaFromPath(path)
        } catch (e: Exception) {
            android.util.Log.e("MediaRepository", "getMediaFromPath failed for $path", e)
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

    override fun toggleFavorite(path: String, isFav: Boolean) {
        try {
            if (isFav) {
                val name = File(path).name
                val parentPath = File(path).parent ?: ""
                context.favoritesDB.insert(org.fossify.gallery.models.Favorite(id = null, fullPath = path, filename = name, parentPath = parentPath))
            } else {
                context.favoritesDB.deleteFavoritePath(path)
            }
            context.mediaDB.updateFavorite(path, isFav)
        } catch (e: Exception) {
            android.util.Log.e("MediaRepository", "toggleFavorite failed for $path", e)
        }
        RefreshBus.trigger()
    }

    override fun getRating(path: String): Int {
        // A single-row, indexed-by-full_path DB lookup instead of a live XMP file read (which was
        // also unused everywhere until now) - the Viewer used to call getMediaFromPath(currentPath)
        // for this instead, which filters on parent_path and so silently matched nothing for a file
        // path (always falling back to 0), on top of loading every file in the whole folder.
        return try {
            context.mediaDB.getRatingForPath(path) ?: 0
        } catch (e: Exception) {
            android.util.Log.e("MediaRepository", "getRating failed for $path", e)
            0
        }
    }

    override fun updateRating(path: String, rating: Int) {
        val current = XmpWriter.read(path)
        XmpWriter.write(path, current.tags, rating)
        try {
            context.mediaDB.updateRating(path, rating)
        } catch (e: Exception) {
            android.util.Log.e("MediaRepository", "updateRating failed for $path", e)
        }
        // Background sync to avoid blocking the caller
        repositoryScope.launch {
            syncCache(path, current.tags, rating)
        }
    }

    override fun getTags(path: String): Set<String> {
        return XmpWriter.read(path).tags.toSet()
    }

    override fun addTag(path: String, tag: String) {
        val current = XmpWriter.read(path)
        val tags = if (tag in current.tags) current.tags else current.tags + tag
        XmpWriter.write(path, tags, current.rating)
        // Background sync
        repositoryScope.launch {
            syncCache(path, tags, current.rating)
        }
    }

    override fun removeTag(path: String, tag: String) {
        val current = XmpWriter.read(path)
        val tags = current.tags.filter { it != tag }
        XmpWriter.write(path, tags, current.rating)
        // Background sync
        repositoryScope.launch {
            syncCache(path, tags, current.rating)
        }
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
            context.mediaTagDB.deleteAllForPath(path)
            tags.filter { it.isNotBlank() }.distinct().forEach { context.mediaTagDB.insert(MediaTag(mediaPath = path, tag = it)) }
        } catch (e: Exception) {
            android.util.Log.e("MediaRepository", "syncTags failed for $path", e)
        }
    }

    override fun deleteMedium(path: String) {
        try { context.mediaDB.deleteMediumPath(path) } catch (e: Exception) { android.util.Log.e("MediaRepository", "deleteMedium DB failed", e) }
        try { context.favoritesDB.deleteFavoritePath(path) } catch (e: Exception) { android.util.Log.e("MediaRepository", "deleteMedium Fav failed", e) }
        try { context.mediaCacheDB.deleteByPathSync(path) } catch (e: Exception) { android.util.Log.e("MediaRepository", "deleteMedium Cache failed", e) }
        try { repositoryScope.launch { context.mediaTagDB.deleteAllForPath(path) } } catch (e: Exception) { android.util.Log.e("MediaRepository", "deleteMedium Tags failed", e) }
        try { File("$path.xmp").delete() } catch (e: Exception) { android.util.Log.e("MediaRepository", "deleteMedium XMP delete failed", e) }
        try { File(path).delete() } catch (e: Exception) { android.util.Log.e("MediaRepository", "deleteMedium File delete failed", e) }
    }

    fun getByMinRating(minRating: Int): List<Medium> =
        try { context.mediaDB.getByMinRating(minRating) } catch (_: Exception) { emptyList() }

    fun getMediaByPaths(paths: List<String>): List<Medium> =
        try { paths.chunked(SQLITE_BATCH_CHUNK_SIZE).flatMap { context.mediaDB.getMediaByPaths(it) } } catch (_: Exception) { emptyList() }

    fun getNewestMedia(limit: Int): List<Medium> =
        try { context.mediaDB.getNewestMedia(limit) } catch (_: Exception) { emptyList() }

    fun getMediaPaged(field: SortField, desc: Boolean): PagingSource<Int, Medium> = when (field) {
        SortField.NAME -> if (desc) context.mediaDB.getMediaPagedByNameDesc() else context.mediaDB.getMediaPagedByNameAsc()
        SortField.DATE -> context.mediaDB.getMediaPagedByDate(desc)
        SortField.SIZE -> context.mediaDB.getMediaPagedBySize(desc)
        SortField.RATING -> context.mediaDB.getMediaPagedByRating(desc)
    }

    suspend fun getActivePaths(): List<String> =
        try { context.mediaDB.getActivePaths() } catch (_: Exception) { emptyList() }

    /** Full sorted path list (no LIMIT/OFFSET) - used to give the Viewer a complete swipe-through
     * list regardless of how much of the grid has been paged in. Cheap: full_path only, no thumbnails. */
    suspend fun getActivePathsSorted(field: SortField, desc: Boolean): List<String> = try {
        when (field) {
            SortField.NAME -> if (desc) context.mediaDB.getActivePathsByNameDesc() else context.mediaDB.getActivePathsByNameAsc()
            SortField.DATE -> context.mediaDB.getActivePathsByDate(desc)
            SortField.SIZE -> context.mediaDB.getActivePathsBySize(desc)
            SortField.RATING -> context.mediaDB.getActivePathsByRating(desc)
        }
    } catch (_: Exception) { emptyList() }

    suspend fun getTaggedPaths(): Set<String> =
        try { context.mediaTagDB.getTaggedPaths().toSet() } catch (_: Exception) { emptySet() }

    suspend fun getTagCounts(): Map<String, Int> =
        try { context.mediaTagDB.getTagCounts().associate { it.tag to it.cnt } } catch (_: Exception) { emptyMap() }

    suspend fun getAllTags(): List<String> =
        try { context.mediaTagDB.getTagCounts().map { it.tag }.sorted() } catch (_: Exception) { emptyList() }

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
        try { context.mediaTagDB.getTagCounts().take(limit).map { it.tag to it.cnt } } catch (_: Exception) { emptyList() }

    suspend fun getPathsForTag(tag: String): Set<String> =
        try { context.mediaTagDB.getPathsForTag(tag).toSet() } catch (_: Exception) { emptySet() }

    fun getDeletedPaths(): Set<String> =
        try { context.mediaDB.getDeletedMedia().map { it.path }.toSet() } catch (_: Exception) { emptySet() }

    fun getFavorites(): List<Medium> =
        try { context.mediaDB.getFavorites() } catch (_: Exception) { emptyList() }

    fun getAllDirectories(): List<Directory> =
        try { context.directoryDB.getAll() } catch (_: Exception) { emptyList() }

    fun getCollections(): List<MediaCollection> =
        try { context.collectionDB.getAll() } catch (_: Exception) { emptyList() }

    fun insertCollection(collection: MediaCollection): Long =
        try { context.collectionDB.insert(collection) } catch (_: Exception) { -1L }

    fun deleteCollection(collection: MediaCollection) {
        try { context.collectionDB.delete(collection) } catch (_: Exception) { }
    }

    fun getDeletedMedia(): List<Medium> =
        try { context.mediaDB.getDeletedMedia() } catch (_: Exception) { emptyList() }

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
        try { context.mediaDB.updateRating(path, rating) } catch (e: Exception) { android.util.Log.e("MediaRepository", "setDbRating failed", e) }
    }

    fun setDbRatingBatch(paths: Collection<String>, rating: Int) {
        try { paths.chunked(SQLITE_BATCH_CHUNK_SIZE).forEach { context.mediaDB.updateRatingBatch(it, rating) } } catch (e: Exception) { android.util.Log.e("MediaRepository", "setDbRatingBatch failed", e) }
    }

    /** Writes the rating into a file's XMP sidecar only - no DB write. Batch rating changes already
     * update the DB in one statement via [setDbRatingBatch]; writing the DB again per-path here would
     * fire Room's InvalidationTracker (and reload any active PagingSource) once per file instead of once
     * per batch. */
    fun writeRatingXmp(path: String, rating: Int) {
        val current = XmpWriter.read(path)
        XmpWriter.write(path, current.tags, rating)
        repositoryScope.launch { syncCache(path, current.tags, rating) }
    }

    /** Decodes only the bounds of an image to derive its aspect ratio. Lives here so no Composable
     * ever touches [android.graphics.BitmapFactory] / the file system directly. */
    fun decodeImageAspect(path: String): Float = try {
        val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(path, opts)
        if (opts.outWidth > 0 && opts.outHeight > 0) opts.outWidth.toFloat() / opts.outHeight.toFloat() else 1f
    } catch (_: Exception) { 1f }

    // ---- Media loading: the repository owns all MediaStore / disk enumeration ----

    private val videoExts = setOf("mp4", "mkv", "mov", "3gp", "wmv", "flv", "avi")
    private val imageExts = setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "avif", "bmp", "svg", "apng", "jxl")

    /**
     * Pulls newly added images/videos from MediaStore (plus recent disk folders) into the local DB.
     * Returns the media that was actually newly inserted, so callers (e.g. [org.fossify.gallery.workers.MediaSyncWorker])
     * can limit any follow-up work (like rebuilding directory rows) to just the affected folders.
     */
    fun syncNewMediaFromStore(): List<Medium> {
        try {
            val lastSync = context.config.lastSyncTimestamp
            val existingPaths = context.mediaDB.getAllPaths().toSet()
            val newMedia = mutableListOf<Medium>()
            val uri = MediaStore.Files.getContentUri("external")
            val proj = arrayOf(
                MediaStore.MediaColumns.DATA,
                MediaStore.MediaColumns.RELATIVE_PATH,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.DATE_MODIFIED,
                MediaStore.MediaColumns.SIZE,
                MediaStore.Files.FileColumns.MEDIA_TYPE,
            )
            
            // Incremental sync: only query items modified since last sync
            val lastSyncSec = lastSync / 1000L
            val sel = "(${MediaStore.Files.FileColumns.MEDIA_TYPE} = ? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?) AND ${MediaStore.MediaColumns.DATE_MODIFIED} > ?"
            val args = arrayOf(
                MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
                MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
                lastSyncSec.toString()
            )
            
            val storageRoot = Environment.getExternalStorageDirectory().absolutePath
            var latestTimestamp = lastSync
            
            context.contentResolver.query(uri, proj, sel, args, "${MediaStore.MediaColumns.DATE_MODIFIED} DESC")?.use { c ->
                val dataIdx = c.getColumnIndex(MediaStore.MediaColumns.DATA)
                val relPathIdx = c.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                val nameIdx = c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                val dateIdx = c.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                val sizeIdx = c.getColumnIndex(MediaStore.MediaColumns.SIZE)
                val typeIdx = c.getColumnIndex(MediaStore.Files.FileColumns.MEDIA_TYPE)
                var scanned = 0
                while (c.moveToNext()) {
                    if (scanned++ >= 10000) break // Lower limit for incremental sync
                    var path = if (dataIdx >= 0) c.getString(dataIdx) else null
                    if (path.isNullOrBlank()) {
                        val relPath = if (relPathIdx >= 0) c.getString(relPathIdx) ?: "" else ""
                        val name = if (nameIdx >= 0) c.getString(nameIdx) ?: "" else ""
                        path = "$storageRoot/$relPath$name"
                    }
                    if (path.isNullOrBlank() || path in existingPaths) continue
                    val name = File(path).name
                    val modifiedSec = if (dateIdx >= 0) c.getLong(dateIdx) else 0L
                    val modified = modifiedSec * 1000L
                    latestTimestamp = maxOf(latestTimestamp, modified)
                    
                    val size = if (sizeIdx >= 0) c.getLong(sizeIdx) else 0L
                    val mediaType = if (typeIdx >= 0) c.getInt(typeIdx) else 1
                    val type = if (mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO) 2 else 1
                    newMedia.add(Medium(null, name, path, File(path).parent ?: "", modified, modified, size, type, 0, false, 0L, 0L, 0))
                }
            }
            val deletedPaths = try {
                context.mediaDB.getDeletedMedia().map { it.path }.toSet()
            } catch (e: Exception) {
                android.util.Log.e("MediaRepository", "getDeletedMedia failed", e)
                emptySet()
            }
            
            // Only scan recent disk folders if we found something new or if it's the first sync
            if (newMedia.isNotEmpty() || lastSync == 0L) {
                newMedia.addAll(recentDiskMedia(existingPaths + newMedia.map { it.path } + deletedPaths))
            }

            val isFirstSync = lastSync == 0L || existingPaths.isEmpty()
            if (isFirstSync) {
                // A first/baseline sync must be exhaustive regardless of whether the incremental
                // DATE_MODIFIED filter above already matched something: that filter only looks at
                // files newer than lastSync, so on a genuine first sync any media with an older
                // DATE_MODIFIED (restored backups, files copied with preserved timestamps, etc.)
                // would otherwise be silently skipped forever once one newer file made `newMedia`
                // non-empty. Merge in a full unbounded scan instead of returning early.
                val diskMedia = scanMediaFromDisk()
                val merged = (newMedia + diskMedia).distinctBy { it.path }
                if (merged.isNotEmpty()) {
                    try {
                        context.mediaDB.insertAllKeepingExisting(merged)
                    } catch (e: Exception) {
                        android.util.Log.e("MediaRepository", "insertAllKeepingExisting failed", e)
                    }
                }
                context.config.lastSyncTimestamp = System.currentTimeMillis()
                return merged
            } else if (newMedia.isNotEmpty()) {
                try {
                    context.mediaDB.insertAllKeepingExisting(newMedia)
                    context.config.lastSyncTimestamp = latestTimestamp
                } catch (e: Exception) {
                    android.util.Log.e("MediaRepository", "insertAllKeepingExisting failed", e)
                }
                return newMedia
            } else {
                // Update timestamp anyway to skip these items next time
                context.config.lastSyncTimestamp = System.currentTimeMillis()
            }
        } catch (e: Exception) {
            android.util.Log.e("MediaRepository", "syncNewMediaFromStore failed", e)
        }
        return emptyList()
    }

    /** Full MediaStore enumeration with a recursive disk fallback — used when the local DB is empty. */
    fun scanMediaFromDisk(): List<Medium> {
        val allMedia = mutableListOf<Medium>()
        val seen = mutableSetOf<String>()
        val exts = videoExts + imageExts

        try {
            val uri = MediaStore.Files.getContentUri("external")
            val proj = arrayOf(
                MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.DATE_MODIFIED, MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.MIME_TYPE, MediaStore.Files.FileColumns.MEDIA_TYPE,
                MediaStore.Files.FileColumns.DURATION,
            )
            val sel = "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?"
            val args = arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(), MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString())
            context.contentResolver.query(uri, proj, sel, args, "${MediaStore.MediaColumns.DATE_MODIFIED} DESC")?.use { c ->
                val dataCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                val nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val dateCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                val sizeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val typeCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
                val durCol = try { c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DURATION) } catch (_: Exception) { -1 }
                val maxItems = 20000
                while (c.moveToNext() && allMedia.size < maxItems) {
                    val path = c.getString(dataCol) ?: continue
                    if (path in seen) continue
                    seen.add(path)
                    val name = c.getString(nameCol) ?: ""
                    val modified = c.getLong(dateCol) * 1000L
                    val size = c.getLong(sizeCol)
                    val mediaType = c.getInt(typeCol)
                    val type = if (mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO) 2 else 1
                    val duration = if (durCol >= 0) (c.getInt(durCol) / 1000) else 0
                    allMedia.add(Medium(null, name, path, File(path).parent ?: "", modified, modified, size, type, duration, false, 0L, 0L, 0))
                }
            }
        } catch (_: Exception) { }

        if (allMedia.isEmpty()) {
            val root = Environment.getExternalStorageDirectory()
            val dirs = listOf(root, File(root, "DCIM"), File(root, "Pictures"), File(root, "Download"), File(root, "Movies")).filter { it.isDirectory }
            for (dir in dirs) scanFile(dir, allMedia, seen, 0, exts)
        }

        return allMedia.sortedByDescending { it.modified }
    }

    private fun recentDiskMedia(knownPaths: Set<String>): List<Medium> {
        val result = mutableListOf<Medium>()
        val exts = videoExts + imageExts
        try {
            val root = Environment.getExternalStorageDirectory()
            val dirs = listOf(
                File(root, "DCIM/Camera"),
                File(root, "DCIM"),
                File(root, "Pictures"),
                File(root, "Pictures/Screenshots"),
                File(root, "Pictures/Screenshot"),
                File(root, "Movies"),
                File(root, "Download"),
            ).filter { it.isDirectory }
            for (dir in dirs) {
                val files = dir.listFiles() ?: continue
                for (f in files) {
                    if (!f.isFile || f.name.startsWith(".")) continue
                    val p = f.absolutePath
                    if (p in knownPaths) continue
                    val ext = f.extension.lowercase()
                    if (ext !in exts) continue
                    val modified = f.lastModified()
                    result.add(Medium(null, f.name, p, f.parent ?: "", modified, modified, f.length(), if (ext in videoExts) 2 else 1, 0, false, 0L, 0L, 0))
                }
            }
        } catch (_: Exception) { }
        return result
    }

    private fun scanFile(dir: File, result: MutableList<Medium>, seen: MutableSet<String>, depth: Int, exts: Set<String>) {
        if (depth > 4 || !dir.isDirectory) return
        val files = dir.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory && !file.name.startsWith(".")) {
                scanFile(file, result, seen, depth + 1, exts)
            } else if (file.isFile) {
                val ext = file.extension.lowercase()
                if (ext in exts && file.path !in seen) {
                    seen.add(file.path)
                    result.add(Medium(null, file.name, file.absolutePath, file.parent ?: "", file.lastModified(), file.lastModified(), file.length(), if (ext in videoExts) 2 else 1, 0, false, 0L, 0L, 0))
                }
            }
        }
    }

    fun moveToRecycleBin(path: String) {
        try {
            val now = System.currentTimeMillis()
            context.mediaDB.softDelete(path, now)
        } catch (e: Exception) {
            android.util.Log.e("MediaRepository", "moveToRecycleBin failed for $path", e)
        }
    }

    fun moveToRecycleBinBatch(paths: Collection<String>) {
        try {
            val now = System.currentTimeMillis()
            paths.chunked(SQLITE_BATCH_CHUNK_SIZE).forEach { context.mediaDB.softDeleteBatch(it, now) }
        } catch (e: Exception) {
            android.util.Log.e("MediaRepository", "moveToRecycleBinBatch failed", e)
        }
    }

    fun restoreFromRecycleBin(path: String) {
        try {
            context.mediaDB.restoreDeleted(path)
        } catch (e: Exception) {
            android.util.Log.e("MediaRepository", "restoreFromRecycleBin failed for $path", e)
        }
    }

    fun restoreFromRecycleBinBatch(paths: Collection<String>) {
        try {
            paths.chunked(SQLITE_BATCH_CHUNK_SIZE).forEach { context.mediaDB.restoreDeletedBatch(it) }
        } catch (e: Exception) {
            android.util.Log.e("MediaRepository", "restoreFromRecycleBinBatch failed", e)
        }
    }

    // ---- Small in-memory caches for screens whose whole composable tree is disposed and recreated
    // whenever Navigation-Compose pushes another destination (Viewer, Settings, ...) on top of Home
    // and the user navigates back. A plain remember{} in those screens gets wiped on that round trip,
    // forcing an expensive DB/query cascade to rerun every time (this is what happened with
    // ExplorerScreen's full-device MediaStore scan; see MediaStoreOps for the analogous fix there).
    // This repo instance itself is remember{}-scoped above the NavHost, so it survives that disposal.

    @Volatile private var favoritesCache: Pair<List<Medium>, List<Directory>>? = null

    fun getFavoritesCached(): Pair<List<Medium>, List<Directory>>? = favoritesCache

    suspend fun refreshFavoritesCache(): Pair<List<Medium>, List<Directory>> = coroutineScope {
        val mediaDeferred = async(Dispatchers.IO) { getFavorites() }
        val dirsDeferred = async(Dispatchers.IO) {
            val paths = context.config.favoriteFolders
            if (paths.isEmpty()) emptyList() else getAllDirectories().filter { it.path in paths }
        }
        (mediaDeferred.await() to dirsDeferred.await()).also { favoritesCache = it }
    }

    @Volatile private var collectionsCache: List<MediaCollection>? = null
    @Volatile private var collectionMediaCache: Map<Long, List<Medium>> = emptyMap()

    fun getCollectionsCached(): List<MediaCollection>? = collectionsCache

    fun getCollectionMediaCached(collectionId: Long): List<Medium> = collectionMediaCache[collectionId] ?: emptyList()

    fun refreshCollectionsCache(): List<MediaCollection> {
        val colls = getCollections()
        collectionMediaCache = colls.associate { c -> c.id to c.getIncludedPaths().flatMap { getMediaFromPath(it) } }
        collectionsCache = colls
        return colls
    }

    @Volatile private var tagsWithPathsCache: Map<String, List<String>>? = null

    fun getTagsWithPathsCached(): Map<String, List<String>>? = tagsWithPathsCache

    suspend fun refreshTagsWithPathsCache(): Map<String, List<String>> =
        getAllTagsWithPaths().also { tagsWithPathsCache = it }
}

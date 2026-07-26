package org.fossify.gallery.helpers

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.paging.PagingSource
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.sync.withLock
import org.fossify.commons.extensions.isAStorageRootFolder
import org.fossify.gallery.compose.screens.SortField
import org.fossify.gallery.databases.GalleryDatabase
import org.fossify.gallery.extensions.collectionDB
import org.fossify.gallery.extensions.directoryDB
import org.fossify.gallery.extensions.favoritesDB
import org.fossify.gallery.extensions.getFavoriteFromPath
import org.fossify.gallery.extensions.mediaDB
import org.fossify.gallery.extensions.mediaCacheDB
import org.fossify.gallery.extensions.mediaTagDB
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.resolveRecycleBinFile
import org.fossify.gallery.models.Directory
import org.fossify.gallery.models.MediaCollection
import org.fossify.gallery.models.Medium
import org.fossify.gallery.models.MediaCache
import org.fossify.gallery.models.MediaTag
import org.fossify.gallery.models.TagCount
import org.fossify.gallery.models.TagPathRow
import org.fossify.gallery.viewmodels.MediaFilter
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

// How many MediaStore rows syncNewMediaFromStore() holds in memory before deduping them against the
// DB and starting a fresh batch. This used to be a hard `break` that discarded everything past the
// limit - see the comment at the cursor loop for what that cost.
private const val CANDIDATE_BATCH_SIZE = 10000

// Column order shared by every hand-written filtered query below - must match MediumDao's @Query
// column lists exactly, since Medium's constructor (via FilteredMediaPagingSource.convertRows) reads
// the cursor positionally.
private const val MEDIA_COLUMNS =
    "filename, full_path, parent_path, last_modified, date_taken, size, type, video_duration, is_favorite, deleted_ts, media_store_id, rating, date_sort_key, date_added"

/** Resolves a SAF tree-picker `content://` URI (as stored by a folder-picking dialog) to the plain
 * filesystem path it points at, or passes an already-plain path through unchanged. Shared by
 * MediaRepository's collection-count query and ComposeExplorerActivity's applyCollection() - both
 * need to resolve the same MediaCollection.includedPaths/excludedPaths the same way, and having two
 * separate copies is exactly how the "collection shows 0 media but has content" bug happened: the
 * count path never resolved the URI at all and queried an exact non-recursive match against it. */
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

class MediaRepository(private val context: Context) : MediaRepositoryInterface {

    companion object {
        /** Process-wide throttle for [syncDirectoriesFromMedia] - this class is instantiated per
         * call site, so an instance field would defeat the throttle entirely. */
        @Volatile
        private var lastDirectorySyncMs = 0L

        private const val TAGS_CACHE_COALESCE_MS = 2_000L
    }
    private val repositoryScope = CoroutineScope(Dispatchers.IO)

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
        RefreshBus.trigger()
        return success
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

    override fun updateRating(path: String, rating: Int): Boolean {
        val current = XmpWriter.read(path)
        // The XMP file is the source of truth for tags/rating - if the write to disk fails, the DB
        // must not move ahead of it, or the two disagree forever with no way for the UI to notice
        // (a rating shown as applied that a re-read of the file would silently undo).
        if (!XmpWriter.write(path, current.tags, rating)) return false
        try {
            context.mediaDB.updateRating(path, rating)
        } catch (e: Exception) {
            android.util.Log.e("MediaRepository", "updateRating failed for $path", e)
        }
        // Background sync to avoid blocking the caller
        repositoryScope.launch {
            syncCache(path, current.tags, rating)
        }
        return true
    }

    override fun getTags(path: String): Set<String> {
        return XmpWriter.read(path).tags.toSet()
    }

    override fun addTag(path: String, tag: String): Boolean {
        val current = XmpWriter.read(path)
        val tags = if (tag in current.tags) current.tags else current.tags + tag
        if (!XmpWriter.write(path, tags, current.rating, context.config.tagHierarchy)) return false
        // Background sync
        repositoryScope.launch {
            syncCache(path, tags, current.rating)
        }
        return true
    }

    override fun removeTag(path: String, tag: String): Boolean {
        val current = XmpWriter.read(path)
        val tags = current.tags.filter { it != tag }
        if (!XmpWriter.write(path, tags, current.rating, context.config.tagHierarchy)) return false
        // Background sync
        repositoryScope.launch {
            syncCache(path, tags, current.rating)
        }
        return true
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
                try { File("${realFile.path}.xmp").delete() } catch (_: Exception) { }
                deleted.add(p)
            } else {
                failed++
            }
            onProgress(i + 1, paths.size)
        }
        deleted.chunked(SQLITE_BATCH_CHUNK_SIZE).forEach { chunk ->
            try { context.mediaDB.deleteByPathsBatch(chunk) } catch (e: Exception) { android.util.Log.e("MediaRepository", "deleteMediaBatch media rows failed", e) }
            try { context.favoritesDB.deleteFavoritePathsBatch(chunk) } catch (e: Exception) { android.util.Log.e("MediaRepository", "deleteMediaBatch favorites failed", e) }
            try { context.mediaCacheDB.deleteByPathsBatch(chunk) } catch (e: Exception) { android.util.Log.e("MediaRepository", "deleteMediaBatch cache failed", e) }
            try { context.mediaTagDB.deleteAllForPathsBatch(chunk) } catch (e: Exception) { android.util.Log.e("MediaRepository", "deleteMediaBatch tags failed", e) }
        }
        if (deleted.isNotEmpty()) {
            syncDirectoriesFromMedia(force = true)
            RefreshBus.trigger()
        }
        return failed
    }

    override fun deleteMedium(path: String): Boolean {
        val ok = deleteMediumInternal(path)
        if (ok) RefreshBus.trigger()
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
        try { File("${realFile.path}.xmp").delete() } catch (e: Exception) { android.util.Log.e("MediaRepository", "deleteMedium XMP delete failed", e) }
        try { context.mediaDB.deleteMediumPath(path) } catch (e: Exception) { android.util.Log.e("MediaRepository", "deleteMedium DB failed", e) }
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

    // COUNT (file count) is a folder-only sort option, never reachable for a media query - falls
    // back to name here purely so this `when` stays exhaustive.
    fun getMediaPaged(field: SortField, desc: Boolean): PagingSource<Int, Medium> = when (field) {
        SortField.NAME, SortField.COUNT -> if (desc) context.mediaDB.getMediaPagedByNameDesc() else context.mediaDB.getMediaPagedByNameAsc()
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
            SortField.NAME, SortField.COUNT -> if (desc) context.mediaDB.getActivePathsByNameDesc() else context.mediaDB.getActivePathsByNameAsc()
            SortField.DATE -> context.mediaDB.getActivePathsByDate(desc)
            SortField.SIZE -> context.mediaDB.getActivePathsBySize(desc)
            SortField.RATING -> context.mediaDB.getActivePathsByRating(desc)
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
    } catch (e: Exception) { emptyList() }

    /** Filtered counterpart of [getMediaPaged] - builds one dynamic SQL query covering every active
     * [MediaFilter] dimension (rating/tag/path-include/path-exclude/size/date) instead of the old
     * fetch-then-filter-in-Kotlin pipeline, so filtered browsing pages and invalidates exactly like
     * the unfiltered grid instead of being capped at a fixed in-memory fetch size. */
    fun getMediaPagedFiltered(filter: MediaFilter, field: SortField, desc: Boolean): PagingSource<Int, Medium> =
        FilteredMediaPagingSource(buildFilteredMediaQuery(filter, field, desc, pathsOnly = false), GalleryDatabase.getInstance(context.applicationContext))

    /** Filtered counterpart of [getActivePathsSorted] - full sorted path list (no LIMIT/OFFSET)
     * matching the same filter, used so Viewer swipe-through and select-all/invert cover the
     * complete filtered set, not just what the grid has paged in so far. */
    suspend fun getActivePathsSortedFiltered(filter: MediaFilter, field: SortField, desc: Boolean): List<String> = try {
        val query = buildFilteredMediaQuery(filter, field, desc, pathsOnly = true)
        GalleryDatabase.getInstance(context.applicationContext).query(query).use { cursor ->
            val paths = ArrayList<String>(cursor.count)
            while (cursor.moveToNext()) paths.add(cursor.getString(0))
            paths
        }
    } catch (_: Exception) { emptyList() }

    private fun escapeLikePrefix(dirPath: String): String =
        dirPath.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "/%"

    /** Appends an `IN (...)`/`LIKE` predicate for a path set (literal files chunked under the same
     * [SQLITE_BATCH_CHUNK_SIZE] bound-parameter cap used elsewhere, directories as escaped LIKE
     * prefixes) - mirrors the include/exclude split `applyCollection`/the old `computePathFallback`
     * already did in Kotlin, just pushed into SQL so it isn't capped by an in-memory fetch size. */
    private fun appendPathPredicate(where: StringBuilder, args: MutableList<Any?>, paths: Set<String>, negate: Boolean) {
        if (paths.isEmpty()) return
        val dirs = paths.filter { File(it).isDirectory }
        val files = (paths - dirs.toSet()).toList()
        val clauses = mutableListOf<String>()
        files.chunked(SQLITE_BATCH_CHUNK_SIZE).forEach { chunk ->
            clauses.add("full_path IN (${chunk.joinToString(",") { "?" }})")
            args.addAll(chunk)
        }
        dirs.forEach { d ->
            clauses.add("full_path LIKE ? ESCAPE '\\'")
            args.add(escapeLikePrefix(d))
        }
        if (clauses.isEmpty()) return
        val combined = clauses.joinToString(" OR ")
        where.append(if (negate) " AND NOT ($combined)" else " AND ($combined)")
    }

    private fun dateRangeCutoff(dateRange: Int): Long = when (dateRange) {
        1 -> System.currentTimeMillis() - 86400000L
        2 -> System.currentTimeMillis() - 7 * 86400000L
        3 -> System.currentTimeMillis() - 30 * 86400000L
        4 -> System.currentTimeMillis() - 365 * 86400000L
        else -> 0L
    }

    /** Builds the WHERE clause + bound args shared by every filtered media query - the true row
     * count for a MediaFilter must always come from this same predicate (via [getFilteredMediaCount])
     * rather than a second, separately-maintained implementation. That exact kind of divergence (a
     * count computed one way, content loaded another) is what silently showed "0 Medien" for
     * Collections using included folders while the collection's actual content loaded fine. */
    private fun buildFilterWhereClause(filter: MediaFilter): Pair<String, List<Any?>> {
        val where = StringBuilder("deleted_ts = 0")
        val args = mutableListOf<Any?>()

        if (filter.rating > 0) {
            where.append(" AND rating >= ?")
            args.add(filter.rating)
        }
        filter.tagNames?.let { names ->
            if (names.isEmpty()) {
                where.append(" AND 0")
            } else {
                where.append(" AND full_path IN (SELECT DISTINCT media_path FROM media_tags WHERE tag IN (${names.joinToString(",") { "?" }}))")
                args.addAll(names)
            }
        }
        filter.pathFilter?.let { appendPathPredicate(where, args, it, negate = false) }
        filter.excludePaths?.let { appendPathPredicate(where, args, it, negate = true) }
        if (filter.minSize > 0) {
            where.append(" AND size >= ?")
            args.add(filter.minSize)
        }
        if (filter.dateRange > 0) {
            where.append(" AND (CASE WHEN date_taken > 0 THEN date_taken ELSE last_modified END) >= ?")
            args.add(dateRangeCutoff(filter.dateRange))
        }
        if (filter.typeFilter > 0) {
            where.append(" AND type = ?")
            args.add(filter.typeFilter)
        }
        return where.toString() to args
    }

    private fun buildFilteredMediaQuery(filter: MediaFilter, sortField: SortField, desc: Boolean, pathsOnly: Boolean): SupportSQLiteQuery {
        val (where, args) = buildFilterWhereClause(filter)

        val mult = if (desc) -1 else 1
        val orderBy = when (sortField) {
            // COUNT (file count) is a folder-only sort option, never reachable for a media query -
            // falls back to name here purely so this `when` stays exhaustive.
            SortField.NAME, SortField.COUNT -> "ORDER BY filename COLLATE NOCASE ${if (desc) "DESC" else "ASC"}"
            // date_sort_key (date_added-preferring, trigger-maintained) - same key the unfiltered
            // paged/sorted queries use, so a filtered view (e.g. a Collection) orders newly added
            // media to the top too, consistent with the main grid.
            SortField.DATE -> "ORDER BY $mult * date_sort_key"
            SortField.SIZE -> "ORDER BY $mult * size"
            SortField.RATING -> "ORDER BY $mult * rating, $mult * last_modified"
        }

        val columns = if (pathsOnly) "full_path" else MEDIA_COLUMNS
        return SimpleSQLiteQuery("SELECT $columns FROM media WHERE $where $orderBy", args.toTypedArray())
    }

    /** True total row count for a MediaFilter - unlike `LazyPagingItems.itemCount`, which only
     * reflects how many rows Paging3 has loaded into memory so far and climbs as the user scrolls,
     * this is a cheap indexed `COUNT(*)` matching exactly what [getMediaPagedFiltered] loads. */
    suspend fun getFilteredMediaCount(filter: MediaFilter): Int = try {
        val (where, args) = buildFilterWhereClause(filter)
        val query = SimpleSQLiteQuery("SELECT COUNT(*) FROM media WHERE $where", args.toTypedArray())
        GalleryDatabase.getInstance(context.applicationContext).query(query).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    } catch (_: Exception) {
        0
    }

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

    /** Path -> all tags carried by that one file, batched for a bounded set of [paths] (a single
     * folder's contents, never the full library) - used by tag-hierarchy grouping. */
    suspend fun getTagsForPaths(paths: List<String>): Map<String, List<String>> =
        try { context.mediaTagDB.getTagsForPaths(paths).groupBy({ it.path }, { it.tag }) } catch (_: Exception) { emptyMap() }

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
    fun writeRatingXmp(path: String, rating: Int): Boolean {
        val current = XmpWriter.read(path)
        val success = XmpWriter.write(path, current.tags, rating)
        repositoryScope.launch { syncCache(path, current.tags, rating) }
        return success
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
            RefreshBus.trigger()
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
            val aggregates = context.mediaDB.getDirectoryAggregates()
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
    fun syncNewMediaFromStore(fullRescan: Boolean = false): List<Medium> {
        try {
            val storedSync = context.config.lastSyncTimestamp
            val lastSync = if (fullRescan) 0L else storedSync
            // Was `context.mediaDB.getAllPaths().toSet()` - materialized every path in the entire
            // library (200,000+ rows on a large library) into memory on *every single call* just to
            // do an in-memory `in` check against a handful of recently-modified rows below. This
            // ContentObserver-triggered sync fires often (see ComposeExplorerActivity's mediaObserver),
            // so that cost was paid repeatedly, not just once. Replaced with `getExistingPaths()`
            // batch-checked below, scoped to just the bounded candidate set this function actually
            // needs to dedup (the cursor loop flushes in CANDIDATE_BATCH_SIZE chunks).
            // Keyed off the STORED watermark, not the possibly-overridden `lastSync`: a repair
            // rescan on a populated library is not a first sync and must not trigger the heavy
            // disk-scan-and-merge branch below.
            val isFirstSync = storedSync == 0L || !context.mediaDB.hasAnyMedia()
            val newMedia = mutableListOf<Medium>()
            val uri = MediaStore.Files.getContentUri("external")
            val proj = arrayOf(
                MediaStore.MediaColumns.DATA,
                MediaStore.MediaColumns.RELATIVE_PATH,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.DATE_MODIFIED,
                MediaStore.MediaColumns.DATE_TAKEN,
                MediaStore.MediaColumns.DATE_ADDED,
                MediaStore.MediaColumns.SIZE,
                MediaStore.Files.FileColumns.MEDIA_TYPE,
            )

            // Incremental sync: only query items modified since last sync. `>=`, not `>`: DATE_MODIFIED
            // has second granularity and lastSyncSec is stored truncated to a second, so a strict `>`
            // permanently skipped any file whose DATE_MODIFIED landed in the exact same second as the
            // newest file the previous sync saw - the "everything shows up except the very newest
            // media" symptom. Re-scanning that one boundary second is free (getExistingPaths dedups it
            // below), and guarantees nothing on the second boundary is lost.
            val lastSyncSec = lastSync / 1000L
            // DATE_ADDED (when MediaStore indexed the row) is checked alongside DATE_MODIFIED:
            // MediaStore can index files long after their mtime - a slow volume scan of a large
            // pushed folder, MTP/PC copies with preserved timestamps - and a sync that ran between
            // index batches used to fast-forward lastSyncTimestamp past those stragglers' mtimes,
            // hiding them from the library forever (repro: 500 pushed files, only 89 ever synced).
            val sel = "(${MediaStore.Files.FileColumns.MEDIA_TYPE} = ? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?) AND (${MediaStore.MediaColumns.DATE_MODIFIED} >= ? OR ${MediaStore.MediaColumns.DATE_ADDED} >= ?)"
            val args = arrayOf(
                MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
                MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
                lastSyncSec.toString(),
                lastSyncSec.toString()
            )

            val storageRoot = Environment.getExternalStorageDirectory().absolutePath

            // Peak memory is one batch, not the whole (potentially six-figure) result set: each full
            // batch is deduped against the DB here and only the genuinely new rows are retained.
            val batcher = SyncBatcher(CANDIDATE_BATCH_SIZE, lastSync) { batch ->
                // Dedup against LIVE paths only (not getExistingPaths): a freshly re-created file on a
                // path that still carries a soft-deleted recycle-bin row must be treated as new so it
                // reaches newMedia and gets revived below. Safe because this loop is timestamp-filtered
                // (fresh DATE_ADDED/DATE_MODIFIED), so an untouched recycle-bin item never gets here.
                val existing = batch.map { it.path }
                    .chunked(SQLITE_BATCH_CHUNK_SIZE)
                    .flatMap { context.mediaDB.getLivePaths(it) }
                    .toSet()
                batch.filterTo(newMedia) { it.path !in existing }
            }

            context.contentResolver.query(uri, proj, sel, args, "${MediaStore.MediaColumns.DATE_MODIFIED} DESC")?.use { c ->
                val dataIdx = c.getColumnIndex(MediaStore.MediaColumns.DATA)
                val relPathIdx = c.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                val nameIdx = c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                val dateIdx = c.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                val takenIdx = c.getColumnIndex(MediaStore.MediaColumns.DATE_TAKEN)
                val addedIdx = c.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)
                val sizeIdx = c.getColumnIndex(MediaStore.MediaColumns.SIZE)
                val typeIdx = c.getColumnIndex(MediaStore.Files.FileColumns.MEDIA_TYPE)
                while (c.moveToNext()) {
                    // Deliberately NOT capped with a `break` any more. It used to stop after 10,000
                    // rows, and because this query is ordered by DATE_MODIFIED DESC while the
                    // selection also matches on DATE_ADDED, the rows it dropped were exactly the
                    // ones with an old mtime but a fresh date_added - i.e. a bulk copy of files that
                    // kept their original timestamps. latestTimestamp below then still advanced to
                    // the maximum over the rows that *were* processed, moving the watermark past the
                    // dropped rows' date_added, so they matched neither half of the selection ever
                    // again and stayed invisible permanently.
                    //
                    // Measured on a real device before this change: 206,432 items in MediaStore vs
                    // 202,671 in the media table - 3,761 files permanently missing, all sharing one
                    // date_added (a single bulk copy) with mtimes spread over months.
                    //
                    // Memory stays bounded the way the cap intended: `candidates` is flushed to the
                    // DB-dedup every CANDIDATE_BATCH_SIZE rows (see flushCandidates below) instead
                    // of the whole result set being held at once.
                    val path = SyncFields.resolvePath(
                        data = if (dataIdx >= 0) c.getString(dataIdx) else null,
                        storageRoot = storageRoot,
                        relativePath = if (relPathIdx >= 0) c.getString(relPathIdx) else null,
                        displayName = if (nameIdx >= 0) c.getString(nameIdx) else null,
                    ) ?: continue
                    val name = File(path).name
                    val modifiedSec = if (dateIdx >= 0) c.getLong(dateIdx) else 0L
                    val modified = modifiedSec * 1000L
                    // date_taken (already millis, unlike date_modified/date_added which are seconds)
                    // is preserved for grouping/display; the actual sort now keys on date_added (see
                    // below). Falling back to modified when MediaStore has no date_taken (non-EXIF
                    // file) keeps taken from being an inconsistent 0.
                    val taken = SyncFields.takenMs(if (takenIdx >= 0) c.getLong(takenIdx) else 0L, modified)
                    val added = SyncFields.addedMs(if (addedIdx >= 0) c.getLong(addedIdx) else 0L, modified, taken)

                    val size = if (sizeIdx >= 0) c.getLong(sizeIdx) else 0L
                    val mediaType = if (typeIdx >= 0) c.getInt(typeIdx) else 1
                    val type = if (mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO) 2 else 1
                    // Watermark and batching both go through SyncBatcher.add, which cannot advance
                    // the one without emitting the other - see IncrementalSyncCore.
                    batcher.add(
                        Medium(null, name, path, File(path).parent ?: "", modified, taken, size, type, 0, false, 0L, 0L, 0, dateAdded = added),
                        modifiedMs = modified,
                        addedMs = added,
                    )
                }
            }
            // Whatever is left in the last, partial batch.
            batcher.finish()
            val latestTimestamp = batcher.watermark

            val deletedPaths = try {
                context.mediaDB.getDeletedMedia().map { it.path }.toSet()
            } catch (e: Exception) {
                android.util.Log.e("MediaRepository", "getDeletedMedia failed", e)
                emptySet()
            }

            // Only scan recent disk folders if we found something new or if it's the first sync
            if (newMedia.isNotEmpty() || lastSync == 0L) {
                newMedia.addAll(recentDiskMedia(newMedia.map { it.path }.toSet() + deletedPaths))
            }

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
                        syncDirectoriesFromMedia(force = true)
                    } catch (e: Exception) {
                        android.util.Log.e("MediaRepository", "insertAllKeepingExisting failed", e)
                    }
                }
                context.config.lastSyncTimestamp = System.currentTimeMillis()
                return merged
            } else if (newMedia.isNotEmpty()) {
                try {
                    // Revive BEFORE the insert: any newMedia path that still carries a soft-deleted
                    // row would otherwise be IGNOREd by insertAllKeepingExisting (unique full_path)
                    // and stay invisible. reviveSoftDeleted un-deletes and refreshes those in place,
                    // keeping their id/favorite/rating/tag associations (which REPLACE would lose).
                    reviveSoftDeleted(newMedia)
                    context.mediaDB.insertAllKeepingExisting(newMedia)
                    context.config.lastSyncTimestamp = latestTimestamp
                    // Both sync callers (MediaViewModel and MediaSyncWorker) get the directory
                    // rebuild here - previously only the worker updated directories, and the
                    // ViewModel path consumed lastSyncTimestamp first, so the worker usually saw
                    // nothing new and new folders never reached the Albums list.
                    syncDirectoriesFromMedia(force = true)
                } catch (e: Exception) {
                    android.util.Log.e("MediaRepository", "insertAllKeepingExisting failed", e)
                }
                return newMedia
            } else {
                // Advance only to the newest timestamp the query actually SAW - fast-forwarding to
                // "now" here is what permanently skipped files MediaStore indexed moments later
                // with an older DATE_MODIFIED.
                context.config.lastSyncTimestamp = latestTimestamp
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
                MediaStore.MediaColumns.DATE_MODIFIED, MediaStore.MediaColumns.DATE_ADDED, MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.MIME_TYPE, MediaStore.Files.FileColumns.MEDIA_TYPE,
                MediaStore.Files.FileColumns.DURATION,
            )
            val sel = "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?"
            val args = arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(), MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString())
            context.contentResolver.query(uri, proj, sel, args, "${MediaStore.MediaColumns.DATE_MODIFIED} DESC")?.use { c ->
                val dataCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                val nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val dateCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                val addedCol = try { c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED) } catch (_: Exception) { -1 }
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
                    val added = (if (addedCol >= 0) c.getLong(addedCol) * 1000L else 0L).takeIf { it > 0 } ?: modified
                    val size = c.getLong(sizeCol)
                    val mediaType = c.getInt(typeCol)
                    val type = if (mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO) 2 else 1
                    val duration = if (durCol >= 0) (c.getInt(durCol) / 1000) else 0
                    allMedia.add(Medium(null, name, path, File(path).parent ?: "", modified, modified, size, type, duration, false, 0L, 0L, 0, dateAdded = added))
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

    // [knownPaths] only needs to cover paths this same sync pass already decided to insert (or that
    // are in the recycle bin) - not the whole library. Whether a found file already exists in the DB
    // is checked below via a batch query scoped to just this function's own (small, non-recursive,
    // ~7-folder) candidate set, the same pattern syncNewMediaFromStore() uses for its own candidates.
    /**
     * A freshly re-created file can land on a path that still carries a soft-deleted (recycle-bin)
     * row - the recycle bin only sets `deleted_ts`, it does not move the file off disk, so nothing
     * stops a new file taking the same path later. `insertAllKeepingExisting` IGNOREs it (unique
     * `full_path`), leaving the new file permanently invisible - the reproduced "not all new media
     * show up, even after refresh" bug. Un-delete and refresh those rows to the new file's metadata.
     *
     * Correctness rests on the caller: [candidates] here come only from the timestamp-filtered
     * incremental MediaStore loop (fresh DATE_ADDED/DATE_MODIFIED), so an untouched recycle-bin item
     * - whose on-disk file keeps its old timestamps - never reaches this and is never resurrected.
     */
    private fun reviveSoftDeleted(candidates: List<Medium>) {
        if (candidates.isEmpty()) return
        try {
            val byPath = candidates.associateBy { it.path }
            val softDeleted = byPath.keys.chunked(SQLITE_BATCH_CHUNK_SIZE)
                .flatMap { context.mediaDB.getSoftDeletedPaths(it) }
            if (softDeleted.isEmpty()) return
            softDeleted.forEach { dbPath ->
                // getSoftDeletedPaths returns the DB's stored casing; map back case-insensitively.
                val m = byPath[dbPath] ?: byPath.entries.firstOrNull { it.key.equals(dbPath, ignoreCase = true) }?.value
                if (m != null) context.mediaDB.reviveMedium(dbPath, m.modified, m.taken, m.size, m.type, m.dateAdded)
            }
            android.util.Log.i("MediaRepository", "Revived ${softDeleted.size} re-created files from soft-deleted rows")
        } catch (e: Exception) {
            android.util.Log.e("MediaRepository", "reviveSoftDeleted failed", e)
        }
    }

    private fun recentDiskMedia(knownPaths: Set<String>): List<Medium> {
        val candidates = mutableListOf<Medium>()
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
                    candidates.add(Medium(null, f.name, p, f.parent ?: "", modified, modified, f.length(), if (ext in videoExts) 2 else 1, 0, false, 0L, 0L, 0))
                }
            }
        } catch (_: Exception) { }
        if (candidates.isEmpty()) return candidates
        val existing = candidates.map { it.path }
            .chunked(SQLITE_BATCH_CHUNK_SIZE)
            .flatMap { context.mediaDB.getExistingPaths(it) }
            .toSet()
        return candidates.filterNot { it.path in existing }
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

    /** Every active (non-deleted) media path found anywhere under any of [folderPaths] (recursive) -
     * there's no dedicated "folder" entity to soft-delete, so deleting a folder means recursively
     * soft-deleting every file inside it, same as picking every one of those files individually.
     * Storage roots (internal storage / an SD card's or OTG's top level) are silently excluded - one
     * can show up as a folder tile when it directly holds media, but "deleting" it would recursively
     * soft-delete the user's entire library, not just a folder's contents. Matches the legacy Views
     * folder screen's own `!isAStorageRootFolder(it)` filter on its batch delete (DirectoryAdapter.
     * deleteFolders) - silent there too, since it's a multi-select batch op, not a single explicit
     * target (unlike rename, which explicitly refuses+toasts for exactly this reason). */
    fun mediaPathsUnderFolders(folderPaths: Collection<String>): List<String> =
        folderPaths.filterNot { context.isAStorageRootFolder(it) }
            .flatMap { MediaStoreOps.mediaEntriesUnder(context, it) }.map { it.path }.distinct()

    fun moveToRecycleBinBatch(paths: Collection<String>) {
        try {
            val now = System.currentTimeMillis()
            paths.chunked(SQLITE_BATCH_CHUNK_SIZE).forEach { context.mediaDB.softDeleteBatch(it, now) }
        } catch (e: Exception) {
            android.util.Log.e("MediaRepository", "moveToRecycleBinBatch failed", e)
        }
        RefreshBus.trigger()
    }

    fun restoreFromRecycleBin(path: String): Boolean {
        val ok = try {
            context.mediaDB.restoreDeleted(path)
            true
        } catch (e: Exception) {
            android.util.Log.e("MediaRepository", "restoreFromRecycleBin failed for $path", e)
            false
        }
        RefreshBus.trigger()
        return ok
    }

    fun restoreFromRecycleBinBatch(paths: Collection<String>) {
        try {
            paths.chunked(SQLITE_BATCH_CHUNK_SIZE).forEach { context.mediaDB.restoreDeletedBatch(it) }
        } catch (e: Exception) {
            android.util.Log.e("MediaRepository", "restoreFromRecycleBinBatch failed", e)
        }
        RefreshBus.trigger()
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
    @Volatile private var collectionMediaCache: Map<Long, List<String>> = emptyMap()

    fun getCollectionsCached(): List<MediaCollection>? = collectionsCache

    fun getCollectionMediaCached(collectionId: Long): List<String> = collectionMediaCache[collectionId] ?: emptyList()

    suspend fun refreshCollectionsCache(): List<MediaCollection> {
        val colls = getCollections()
        collectionMediaCache = colls.associate { c -> c.id to getCollectionPaths(c) }
        collectionsCache = colls
        return colls
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
        )
        if (!filter.isActive) return emptyList()
        return getActivePathsSortedFiltered(filter, SortField.DATE, true)
    }

    @Volatile private var tagsWithPathsCache: Map<String, List<String>>? = null
    private var tagsCacheRefreshedAt = 0L
    private val tagsCacheMutex = kotlinx.coroutines.sync.Mutex()

    fun getTagsWithPathsCached(): Map<String, List<String>>? = tagsWithPathsCache

    // TagBrowserScreen and ComposeExplorerActivity both hold their own RefreshBus subscription and
    // both call this on every event - since they share this one MediaRepository instance (via
    // LocalMediaRepository), that's the same full media_tags scan run twice per event. The mutex
    // serializes concurrent callers, and the freshness check lets the second one reuse what the
    // first just computed instead of repeating the query.
    suspend fun refreshTagsWithPathsCache(): Map<String, List<String>> = tagsCacheMutex.withLock {
        val cached = tagsWithPathsCache
        if (cached != null && System.currentTimeMillis() - tagsCacheRefreshedAt < TAGS_CACHE_COALESCE_MS) {
            return@withLock cached
        }
        getAllTagsWithPaths().also {
            tagsWithPathsCache = it
            tagsCacheRefreshedAt = System.currentTimeMillis()
        }
    }
}

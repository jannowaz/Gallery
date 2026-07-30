package org.fossify.gallery.helpers

import android.app.PendingIntent
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import org.fossify.gallery.extensions.mediaDB
import java.io.File

/**
 * Scoped-storage-safe media operations via MediaStore. On Android 11+/13+ the app cannot modify or
 * delete media it does not own using raw java.io.File; it must resolve content URIs and (for items
 * it doesn't own) obtain user consent through the system dialogs produced by
 * createTrashRequest / createDeleteRequest / createWriteRequest. The actual IntentSender is launched
 * by the Compose layer (see compose/util/MediaStoreConsent.kt).
 */
object MediaStoreOps {

    /**
     * True when the app holds MANAGE_EXTERNAL_STORAGE ("All files access") - which, per Android's
     * own scoped-storage docs, exempts it from the ownership/ [createWriteRequest] consent dance
     * entirely (it can already read/write/delete any file on shared storage directly). Callers
     * asking for a write/delete consent dialog should skip that step when this is true - requesting
     * it anyway is not just redundant, it can outright fail (e.g. [MoverWidgetProvider]'s "move now"
     * already skips consent unconditionally and works, since this permission covers it).
     */
    fun hasAllFilesAccess(context: Context): Boolean =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) android.os.Environment.isExternalStorageManager()
        else true

    /** Resolves the MediaStore content URI for a given absolute file path, or null if unknown. */
    fun uriForPath(context: Context, path: String): Uri? {
        val collection = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.Files.FileColumns.MEDIA_TYPE)
        try {
            context.contentResolver.query(
                collection, projection,
                "${MediaStore.MediaColumns.DATA} = ?", arrayOf(path), null,
            )?.use { c ->
                if (c.moveToFirst()) {
                    val id = c.getLong(c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                    val type = c.getInt(c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE))
                    val base = when (type) {
                        MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                        MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                        else -> collection
                    }
                    return ContentUris.withAppendedId(base, id)
                }
            }
        } catch (_: Exception) { }
        return null
    }

    fun urisForPaths(context: Context, paths: Collection<String>): List<Pair<String, Uri>> =
        paths.mapNotNull { p -> uriForPath(context, p)?.let { p to it } }

    fun deleteRequest(context: Context, uris: List<Uri>): PendingIntent =
        MediaStore.createDeleteRequest(context.contentResolver, uris)

    fun writeRequest(context: Context, uris: List<Uri>): PendingIntent =
        MediaStore.createWriteRequest(context.contentResolver, uris)

    /** Renames the display name (and the on-disk file) of [uri]. Requires prior write consent. */
    fun rename(context: Context, uri: Uri, newName: String): Boolean = try {
        val values = ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, newName) }
        context.contentResolver.update(uri, values, null, null) > 0
    } catch (_: Exception) { false }

    /**
     * Moves [uri] into [targetRelativePath] (e.g. "Pictures/Foo") by updating RELATIVE_PATH.
     * Requires prior write consent. Returns true on success.
     */
    fun move(context: Context, uri: Uri, targetRelativePath: String, newName: String? = null): Boolean = try {
        val rel = targetRelativePath.trim('/') + "/"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.RELATIVE_PATH, rel)
            // Also rename in the same write when the target name differs from the source (the
            // "keep both" collision resolution retargets to "name (1).ext") - without this the file
            // would move under its original name and re-collide with the existing file it was meant
            // to sit beside. A no-op update when the name is unchanged.
            if (newName != null) put(MediaStore.MediaColumns.DISPLAY_NAME, newName)
        }
        context.contentResolver.update(uri, values, null, null) > 0
    } catch (_: Exception) { false }

    /** The top-level storage volume segment of an absolute shared-storage path, e.g.
     * "emulated/0" for internal storage or the hex volume id for an SD card - "" if unrecognized. */
    private fun volumeOf(path: String): String = Regex("^/storage/([^/]+(?:/[^/]+)?)/").find(path)?.groupValues?.get(1) ?: ""

    /** True when [pathA] and [pathB] live on the same physical storage volume - a plain
     * RELATIVE_PATH update ([move]) is a near-instant filesystem rename in that case, vs. the
     * full byte copy [copy] needs for a genuine cross-volume move. */
    fun sameVolume(pathA: String, pathB: String): Boolean {
        val a = volumeOf(pathA)
        return a.isNotEmpty() && a == volumeOf(pathB)
    }

    /**
     * Copies the content of [sourceUri] into a new MediaStore entry under [targetRelativePath].
     * No consent needed (the new item is app-owned). Returns the new URI or null on failure.
     */
    fun copy(context: Context, sourceUri: Uri, displayName: String, targetRelativePath: String, isVideo: Boolean): Uri? {
        return try {
            val rel = targetRelativePath.trim('/') + "/"
            val collection = if (isVideo) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            else MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            // Carry the original capture/modified dates over from the source row - a plain insert()
            // with neither set defaults both to "now", which is exactly what made a moved file (the
            // Mover feature and any cross-volume move both go through this copy-then-delete) jump
            // back to the top of any date-sorted view as if it were brand new. date_taken is what
            // MediumDao's sort actually keys on (falling back to last_modified only when it's <= 0,
            // see getMediaPagedByDate) - date_modified is set best-effort too, but MediaProvider may
            // still override it with the real write timestamp regardless of what's requested here.
            val dateProjection = arrayOf(MediaStore.MediaColumns.DATE_TAKEN, MediaStore.MediaColumns.DATE_MODIFIED, MediaStore.MediaColumns.SIZE)
            var origDateTaken = 0L
            var origDateModified = 0L
            var sourceSize = -1L
            context.contentResolver.query(sourceUri, dateProjection, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val tIdx = c.getColumnIndex(MediaStore.MediaColumns.DATE_TAKEN)
                    val mIdx = c.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                    val sIdx = c.getColumnIndex(MediaStore.MediaColumns.SIZE)
                    if (tIdx >= 0) origDateTaken = c.getLong(tIdx)
                    if (mIdx >= 0) origDateModified = c.getLong(mIdx)
                    if (sIdx >= 0) sourceSize = c.getLong(sIdx)
                }
            }
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.RELATIVE_PATH, rel)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
                if (origDateTaken > 0) put(MediaStore.MediaColumns.DATE_TAKEN, origDateTaken)
                if (origDateModified > 0) put(MediaStore.MediaColumns.DATE_MODIFIED, origDateModified)
            }
            val newUri = context.contentResolver.insert(collection, values) ?: return null
            val copiedBytes = context.contentResolver.openInputStream(sourceUri)?.use { input ->
                context.contentResolver.openOutputStream(newUri)?.use { output -> input.copyTo(output) }
                    ?: return null.also { context.contentResolver.delete(newUri, null, null) }
            } ?: return null.also { context.contentResolver.delete(newUri, null, null) }
            // Verified BEFORE the caller ever gets a URI back to delete the source against - a
            // truncated copy (source modified/removed mid-read by another app, IO error that
            // didn't throw) must never look like a successful move. Every caller of copy() only
            // deletes the original after receiving a non-null result here.
            if (sourceSize > 0 && copiedBytes != sourceSize) {
                context.contentResolver.delete(newUri, null, null)
                return null
            }
            val done = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
                // Re-applied: clearing IS_PENDING is itself a write, which can bump DATE_MODIFIED
                // back to now again on some providers.
                if (origDateTaken > 0) put(MediaStore.MediaColumns.DATE_TAKEN, origDateTaken)
                if (origDateModified > 0) put(MediaStore.MediaColumns.DATE_MODIFIED, origDateModified)
            }
            context.contentResolver.update(newUri, done, null, null)
            newUri
        } catch (_: Exception) { null }
    }

    /** Derives the MediaStore RELATIVE_PATH for an absolute shared-storage folder path. */
    fun relativePathFor(folderAbsolutePath: String): String {
        val markers = listOf("/storage/emulated/0/", "/sdcard/")
        var rel = folderAbsolutePath
        for (m in markers) if (rel.startsWith(m)) { rel = rel.removePrefix(m); break }
        // Fallback: strip any leading /storage/<vol>/
        if (rel.startsWith("/storage/")) rel = rel.substringAfter("/storage/").substringAfter("/")
        return rel.trim('/')
    }

    fun isVideoPath(path: String): Boolean =
        path.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS

    fun fileName(path: String): String = File(path).name

    data class MediaEntry(val path: String, val name: String, val modified: Long, val size: Long, val dateAdded: Long = 0L)

    /**
     * Returns all image/video entries located anywhere under [rootPath] (recursive) via MediaStore.
     * Used to reconstruct the folder tree without raw directory listing (blocked on scoped storage).
     */
    fun mediaEntriesUnder(context: Context, rootPath: String): List<MediaEntry> {
        val root = rootPath.trimEnd('/')
        if (root.isEmpty()) return emptyList()
        val uri = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.SIZE,
        )
        val typeSel = "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?"
        val escaped = root.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        val selection = "($typeSel) AND ${MediaStore.MediaColumns.DATA} LIKE ? ESCAPE '\\'"
        val args = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
            "$escaped/%",
        )
        val out = ArrayList<MediaEntry>()
        try {
            context.contentResolver.query(uri, projection, selection, args, null)?.use { c ->
                val dIdx = c.getColumnIndex(MediaStore.MediaColumns.DATA)
                val nIdx = c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                val mIdx = c.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                val aIdx = c.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)
                val sIdx = c.getColumnIndex(MediaStore.MediaColumns.SIZE)
                if (dIdx < 0) return@use
                while (c.moveToNext()) {
                    val p = c.getString(dIdx) ?: continue
                    if (!p.startsWith("$root/")) continue
                    val name = if (nIdx >= 0) c.getString(nIdx) ?: File(p).name else File(p).name
                    val modified = if (mIdx >= 0) c.getLong(mIdx) * 1000L else 0L
                    val size = if (sIdx >= 0) c.getLong(sIdx) else 0L
                    val added = (if (aIdx >= 0) c.getLong(aIdx) * 1000L else 0L).takeIf { it > 0 } ?: modified
                    out.add(MediaEntry(p, name, modified, size, added))
                }
            }
        } catch (_: Exception) { }
        return out
    }

    // In-memory cache of the last mediaEntriesUnder() result, kept alive at the process level (not
    // tied to any composable's remember{} scope). ExplorerScreen's whole composition is disposed and
    // recreated whenever the user opens the Viewer (a separate NavHost destination) and navigates
    // back, which previously reset a composable-scoped cache to null and forced a full-device
    // MediaStore re-query every single time - the reported "Explorer reloads every time" slowness.
    @Volatile private var cachedRoot: String? = null
    @Volatile private var cachedEntries: List<MediaEntry>? = null

    fun cachedEntriesUnder(rootPath: String): List<MediaEntry>? {
        val root = rootPath.trimEnd('/')
        return cachedEntries?.takeIf { cachedRoot == root }
    }

    /**
     * Returns all cached entries whose path lies under [rootPath], via binary range search instead
     * of a full linear scan. Requires [entries] to be sorted ascending by `path` (natural/UTF-16
     * order) - guaranteed because both cache producers ([refreshEntriesFromDb], [refreshEntriesUnder])
     * store their list through [sortedByPath].
     *
     * On a ~206k-item library the old `entries.filter { it.path.startsWith("root/") }` was O(N) per
     * folder open (~340ms); this is O(log N + k) where k is the subtree size - a few ms even deep in
     * the tree. The one-time sort cost is paid once at cache-load time, not per navigation.
     *
     * All strings prefixed by `"root/"` occupy the contiguous range `["root/", "root0")`: the char
     * after `/` (0x2F) in any child path is always < `0` (0x30), so `root + "0"` is a tight exclusive
     * upper bound. Both the sort and this search use Kotlin's natural String order, matching
     * `startsWith`, so the range is exact.
     */
    fun entriesUnder(entries: List<MediaEntry>, rootPath: String): List<MediaEntry> {
        val prefix = rootPath.trimEnd('/') + "/"
        val upper = prefix.dropLast(1) + (prefix.last() + 1) // "root/" -> "root0"
        val lo = lowerBound(entries, prefix)
        val hi = lowerBound(entries, upper)
        return if (lo >= hi) emptyList() else entries.subList(lo, hi).toList()
    }

    /** First index whose `path >= key`, over a list sorted ascending by `path`. */
    private fun lowerBound(entries: List<MediaEntry>, key: String): Int {
        var lo = 0
        var hi = entries.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (entries[mid].path < key) lo = mid + 1 else hi = mid
        }
        return lo
    }

    /** Sorted ascending by `path` in natural String order so [entriesUnder] can binary-search it. */
    private fun sortedByPath(entries: List<MediaEntry>): List<MediaEntry> = entries.sortedBy { it.path }

    /** Re-queries MediaStore and refreshes the cache used by [cachedEntriesUnder]. Kept for the
     * off-[storageRoot] case (SD cards etc.); the storage-root path now goes through
     * [refreshEntriesFromDb], which is much faster - see its doc. */
    fun refreshEntriesUnder(context: Context, rootPath: String): List<MediaEntry> {
        val root = rootPath.trimEnd('/')
        val fresh = sortedByPath(mediaEntriesUnder(context, root))
        cachedRoot = root
        cachedEntries = fresh
        return fresh
    }

    /**
     * Same cache, but sourced from the Room `media` table instead of a full-device MediaStore
     * cursor. Measured on-device on a ~206k-item library: **1.8s vs 8.1s** for the MediaStore scan.
     *
     * An earlier DB attempt was reverted as slower (see git history), but it used a
     * `full_path LIKE 'root/%'` predicate - which, because `full_path` is COLLATE NOCASE, cannot use
     * the index and degrades to a full scan plus per-row collation work (the same index-defeating
     * trap fixed for parent_path in 2026-07-21). This query does the opposite: no SQL filtering at
     * all, just `SELECT ... WHERE deleted_ts = 0`, and the root-prefix filtering stays in Kotlin
     * where the caller already does it. Do NOT reintroduce a LIKE here.
     *
     * Only sound now that the media table actually matches MediaStore (the sync-truncation fix of
     * 2026-07-21); before that this would have shown folders with silently missing files.
     */
    fun refreshEntriesFromDb(context: Context, rootPath: String): List<MediaEntry> {
        val root = rootPath.trimEnd('/')
        val fresh = try {
            context.mediaDB.getAllLiveEntriesForExplorer()
        } catch (e: Exception) {
            android.util.Log.e("MediaStoreOps", "DB entry load failed, falling back to MediaStore", e)
            return refreshEntriesUnder(context, root)
        }
        val sorted = sortedByPath(fresh)
        cachedRoot = root
        cachedEntries = sorted
        return sorted
    }
}

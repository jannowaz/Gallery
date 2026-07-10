package org.fossify.gallery.helpers

import android.app.PendingIntent
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
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
    fun move(context: Context, uri: Uri, targetRelativePath: String): Boolean = try {
        val rel = targetRelativePath.trim('/') + "/"
        val values = ContentValues().apply { put(MediaStore.MediaColumns.RELATIVE_PATH, rel) }
        context.contentResolver.update(uri, values, null, null) > 0
    } catch (_: Exception) { false }

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
            val dateProjection = arrayOf(MediaStore.MediaColumns.DATE_TAKEN, MediaStore.MediaColumns.DATE_MODIFIED)
            var origDateTaken = 0L
            var origDateModified = 0L
            context.contentResolver.query(sourceUri, dateProjection, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val tIdx = c.getColumnIndex(MediaStore.MediaColumns.DATE_TAKEN)
                    val mIdx = c.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                    if (tIdx >= 0) origDateTaken = c.getLong(tIdx)
                    if (mIdx >= 0) origDateModified = c.getLong(mIdx)
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
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                context.contentResolver.openOutputStream(newUri)?.use { output -> input.copyTo(output) }
                    ?: return null.also { context.contentResolver.delete(newUri, null, null) }
            } ?: return null.also { context.contentResolver.delete(newUri, null, null) }
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

    data class MediaEntry(val path: String, val name: String, val modified: Long, val size: Long)

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
                val sIdx = c.getColumnIndex(MediaStore.MediaColumns.SIZE)
                if (dIdx < 0) return@use
                while (c.moveToNext()) {
                    val p = c.getString(dIdx) ?: continue
                    if (!p.startsWith("$root/")) continue
                    val name = if (nIdx >= 0) c.getString(nIdx) ?: File(p).name else File(p).name
                    val modified = if (mIdx >= 0) c.getLong(mIdx) * 1000L else 0L
                    val size = if (sIdx >= 0) c.getLong(sIdx) else 0L
                    out.add(MediaEntry(p, name, modified, size))
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

    /** Re-queries MediaStore and refreshes the cache used by [cachedEntriesUnder].
     *
     * A local-Room-DB-backed variant (via a `full_path LIKE` query against the already-synced
     * `media` table) was tried here instead of this raw ContentResolver scan, on the theory that
     * `media.full_path`'s index (see Medium.kt) would make it a range scan instead of a linear scan.
     * Measured on-device on a ~200k-item library it was consistently SLOWER (7.5-10.3s vs ~6s for
     * this version, even after forcing the index with `INDEXED BY` once EXPLAIN QUERY PLAN showed
     * SQLite's planner was picking a different index by default) - so it was reverted. Left as a
     * cautionary note: a query that looks like a clear win on paper (and even checks out via
     * EXPLAIN QUERY PLAN / a desktop sqlite3 sanity check) still needs an actual on-device
     * measurement before assuming it's faster.
     */
    fun refreshEntriesUnder(context: Context, rootPath: String): List<MediaEntry> {
        val root = rootPath.trimEnd('/')
        val fresh = mediaEntriesUnder(context, root)
        cachedRoot = root
        cachedEntries = fresh
        return fresh
    }
}

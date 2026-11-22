package org.fossify.gallery.helpers

import android.app.PendingIntent
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import org.fossify.gallery.extensions.mediaDB
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Scoped-storage-safe media operations via MediaStore. On Android 11+/13+ the app cannot modify or
 * delete media it does not own using raw java.io.File; it must resolve content URIs and (for items
 * it doesn't own) obtain user consent through the system dialogs produced by
 * createTrashRequest / createDeleteRequest / createWriteRequest. The actual IntentSender is launched
 * by the Compose layer (see compose/util/MediaStoreConsent.kt).
 */
object MediaStoreOps {

    private const val TAG = "MediaStoreOps"
    private val IMAGE_ALLOWED_ROOTS = setOf("DCIM", "Pictures")
    private val VIDEO_ALLOWED_ROOTS = setOf("DCIM", "Pictures", "Movies")

    data class TrashResult(val updated: Boolean, val path: String?)

    private fun imageCollection(): Uri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    } else {
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }

    private fun videoCollection(): Uri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    } else {
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    }

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

    /**
     * (Un)trashes [uri] via MediaStore's IS_TRASHED so a "deleted" file is actually hidden from
     * every other app (and the store), not just flagged in this app's DB. With All-Files-Access this
     * runs silently (like [move]); otherwise it throws and we fall back to a DB-only soft-delete.
     * Trashing renames the file (to ".trashed-<expiry>-<name>"), so this returns the file's NEW DATA
     * path afterwards - the recycle-bin row must be re-pointed to it. [updated] distinguishes a
     * successful provider update from a query failure, because treating the latter as a normal
     * fallback leaves the Room row pointing at a path that MediaStore has already renamed.
     */
    fun setTrashed(context: Context, uri: Uri, trashed: Boolean): TrashResult {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) return TrashResult(false, null)
        return try {
            val values = ContentValues().apply { put(MediaStore.MediaColumns.IS_TRASHED, if (trashed) 1 else 0) }
            if (context.contentResolver.update(uri, values, null, null) <= 0) return TrashResult(false, null)
            val args = android.os.Bundle().apply { putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE) }
            var path: String? = null
            repeat(4) { attempt ->
                if (path == null) {
                    context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), args, null)?.use { c ->
                        if (c.moveToFirst()) path = c.getString(0)
                    }
                    if (path == null && attempt < 3) Thread.sleep(40L)
                }
            }
            TrashResult(true, path)
        } catch (_: Exception) { TrashResult(false, null) }
    }

    /** Fallback URI for a MediaStore row whose DATA path changed during trash/restore. */
    fun uriForMediaStoreId(context: Context, mediaStoreId: Long): Uri? {
        if (mediaStoreId <= 0L) return null
        val uri = ContentUris.withAppendedId(MediaStore.Files.getContentUri("external"), mediaStoreId)
        return try {
            context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns._ID), null, null, null)?.use {
                if (it.moveToFirst()) uri else null
            }
        } catch (_: Exception) { null }
    }

    /** Like [uriForPath] but also matches trashed items - needed to resolve a file in the recycle bin
     * (default queries hide trashed rows), so restore can flip IS_TRASHED back off. */
    fun uriForPathIncludingTrashed(context: Context, path: String): Uri? {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) return uriForPath(context, path)
        val collection = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.Files.FileColumns.MEDIA_TYPE)
        return try {
            val args = android.os.Bundle().apply {
                putString(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION, "${MediaStore.MediaColumns.DATA} = ?")
                putStringArray(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, arrayOf(path))
                putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE)
            }
            context.contentResolver.query(collection, projection, args, null)?.use { c ->
                if (c.moveToFirst()) {
                    val id = c.getLong(c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                    val type = c.getInt(c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE))
                    val base = when (type) {
                        MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE -> imageCollection()
                        MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO -> videoCollection()
                        else -> collection
                    }
                    ContentUris.withAppendedId(base, id)
                } else null
            }
        } catch (_: Exception) { null }
    }

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
                        MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE -> imageCollection()
                        MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO -> videoCollection()
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

    fun deleteRequest(context: Context, uris: List<Uri>): PendingIntent {
        // The consent request APIs were added in Android 11. Keep the API check at this boundary
        // so legacy callers cannot accidentally load the method on older devices.
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            MediaStore.createDeleteRequest(context.contentResolver, uris)
        } else {
            error("MediaStore delete consent is only available on Android 11+")
        }
    }

    fun writeRequest(context: Context, uris: List<Uri>): PendingIntent {
        // The consent request APIs were added in Android 11. Keep the API check at this boundary
        // so legacy callers cannot accidentally load the method on older devices.
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            MediaStore.createWriteRequest(context.contentResolver, uris)
        } else {
            error("MediaStore write consent is only available on Android 11+")
        }
    }

    /**
     * True when Android's MediaStore Images/Video collection accepts [targetRelativePath] as a
     * RELATIVE_PATH target on Android 11+ (`DCIM/...`, `Pictures/...`, or `Movies/...` for video).
     * Custom top-level folders (e.g. `Nsfw/...`, `Download/...`, `Documents/...`) are rejected by
     * MediaProvider on typed image/video URIs with `IllegalArgumentException`, so moving or copying
     * into them requires All Files Access (`MANAGE_EXTERNAL_STORAGE`) and direct filesystem I/O.
     */
    fun isMediaStoreCollectionAllowedPath(targetRelativePath: String, isVideo: Boolean): Boolean {
        val topDir = targetRelativePath.trim('/').substringBefore('/')
        if (topDir.isEmpty()) return false
        val allowed = if (isVideo) VIDEO_ALLOWED_ROOTS else IMAGE_ALLOWED_ROOTS
        return allowed.any { it.equals(topDir, ignoreCase = true) }
    }

    /** True when moving/copying into [targetFolderPath] requires All Files Access on Android 11+. */
    fun requiresAllFilesAccessForTarget(targetFolderPath: String, isVideo: Boolean = false): Boolean {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) return false
        return !isMediaStoreCollectionAllowedPath(relativePathFor(targetFolderPath), isVideo)
    }

    /** Renames the display name (and the on-disk file) of [uri]. Requires prior write consent. */
    fun rename(context: Context, uri: Uri, newName: String): Boolean = try {
        val values = ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, newName) }
        context.contentResolver.update(uri, values, null, null) > 0
    } catch (e: Exception) {
        Log.w(TAG, "rename($uri -> $newName) failed", e)
        false
    }

    /**
     * Moves [uri] into [targetRelativePath] (e.g. "Pictures/Foo") by updating RELATIVE_PATH.
     * Requires prior write consent. Returns true on success.
     */
    fun move(context: Context, uri: Uri, targetRelativePath: String, newName: String? = null): Boolean {
        val rel = targetRelativePath.trim('/') + "/"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.RELATIVE_PATH, rel)
            // Also rename in the same write when the target name differs from the source (the
            // "keep both" collision resolution retargets to "name (1).ext") - without this the file
            // would move under its original name and re-collide with the existing file it was meant
            // to sit beside. A no-op update when the name is unchanged.
            if (newName != null) put(MediaStore.MediaColumns.DISPLAY_NAME, newName)
        }
        return try {
            context.contentResolver.update(uri, values, null, null) > 0
        } catch (e: IllegalArgumentException) {
            // Typed Images/Video URIs reject RELATIVE_PATH outside DCIM/Pictures(/Movies). Retry
            // once against MediaStore.Files with the same _ID for directories like Download/Documents
            // where the Files collection may accept it.
            val fileId = runCatching { ContentUris.parseId(uri) }.getOrDefault(-1L)
            if (fileId > 0L) {
                val filesUri = ContentUris.withAppendedId(MediaStore.Files.getContentUri("external"), fileId)
                if (filesUri != uri) {
                    val retried = try {
                        context.contentResolver.update(filesUri, values, null, null) > 0
                    } catch (retryEx: Exception) {
                        Log.w(TAG, "move($filesUri -> $rel) fallback failed", retryEx)
                        false
                    }
                    if (retried) return true
                }
            }
            Log.w(TAG, "move($uri -> $rel) rejected by MediaStore", e)
            false
        } catch (e: Exception) {
            Log.w(TAG, "move($uri -> $rel) failed", e)
            false
        }
    }

    /**
     * Moves [sourcePath] to [targetPath] using the safest path available:
     * - Standard MediaStore directories (`DCIM`/`Pictures`/`Movies`) on the same volume try
     *   MediaStore `RELATIVE_PATH` first (atomic + keeps the MediaStore row in sync immediately).
     * - Custom top-level directories (e.g. `Nsfw/...`, `Download/...`), unindexed files (`.nomedia`),
     *   or MediaStore rejections fall back to verified direct filesystem move when All Files Access
     *   (`MANAGE_EXTERNAL_STORAGE`) is granted, followed by `MediaScannerConnection.scanFile`.
     * - Cross-volume moves use verified copy-then-delete (never losing the source on copy failure
     *   and rolling back the copy if deleting the source fails).
     */
    fun moveToPath(context: Context, sourcePath: String, targetPath: String): Boolean {
        if (sourcePath == targetPath) return true
        val targetFile = File(targetPath)
        val targetParent = targetFile.parent ?: return false
        val targetName = targetFile.name
        val targetRel = relativePathFor(targetParent)
        val isVideo = isVideoPath(sourcePath)
        val isStandardDir = isMediaStoreCollectionAllowedPath(targetRel, isVideo)

        if (sameVolume(sourcePath, targetPath)) {
            if (isStandardDir) {
                val uri = uriForPath(context, sourcePath)
                if (uri != null && move(context, uri, targetRel, targetName)) return true
            }
            if (hasAllFilesAccess(context)) {
                return moveFileDirectly(context, sourcePath, targetPath)
            }
            val uri = uriForPath(context, sourcePath)
            if (uri != null && move(context, uri, targetRel, targetName)) return true
            Log.w(TAG, "moveToPath failed (allFiles=false, standardDir=$isStandardDir): $sourcePath -> $targetPath")
            return false
        }

        // Cross-volume move
        if (isStandardDir) {
            val uri = uriForPath(context, sourcePath)
            if (uri != null) {
                val newUri = copy(context, uri, targetName, targetRel, isVideo)
                if (newUri != null) {
                    if (context.contentResolver.delete(uri, null, null) > 0) return true
                    context.contentResolver.delete(newUri, null, null)
                    Log.w(TAG, "moveToPath cross-volume source delete returned 0 for $sourcePath")
                }
            }
        }
        if (hasAllFilesAccess(context)) {
            return moveFileDirectly(context, sourcePath, targetPath)
        }
        Log.w(TAG, "moveToPath cross-volume failed (allFiles=false): $sourcePath -> $targetPath")
        return false
    }

    /** Copies [sourcePath] to [targetPath], falling back to direct file copy when All Files Access is granted. */
    fun copyToPath(context: Context, sourcePath: String, targetPath: String): Boolean {
        val targetFile = File(targetPath)
        val targetParent = targetFile.parent ?: return false
        val targetName = targetFile.name
        val targetRel = relativePathFor(targetParent)
        val isVideo = isVideoPath(sourcePath)
        if (isMediaStoreCollectionAllowedPath(targetRel, isVideo)) {
            val uri = uriForPath(context, sourcePath)
            if (uri != null && copy(context, uri, targetName, targetRel, isVideo) != null) return true
        }
        if (hasAllFilesAccess(context)) {
            return copyFileDirectly(context, sourcePath, targetPath)
        }
        Log.w(TAG, "copyToPath failed (allFiles=false): $sourcePath -> $targetPath")
        return false
    }

    /** Renames [sourcePath] to [targetPath] in the same folder, with direct-file fallback under All Files Access. */
    fun renameToPath(context: Context, sourcePath: String, targetPath: String): Boolean {
        if (sourcePath == targetPath) return true
        val newName = File(targetPath).name
        val uri = uriForPath(context, sourcePath)
        if (uri != null && rename(context, uri, newName)) return true
        if (hasAllFilesAccess(context)) {
            return moveFileDirectly(context, sourcePath, targetPath)
        }
        return false
    }

    private fun logWarn(message: String, t: Throwable? = null) {
        runCatching {
            if (t != null) Log.w(TAG, message, t) else Log.w(TAG, message)
        }
    }

    /**
     * Moves a file directly on the filesystem (requires `MANAGE_EXTERNAL_STORAGE` on Android 11+).
     * Never overwrites an existing target file, verifies byte counts on cross-device copies,
     * preserves `lastModified`, and notifies `MediaScannerConnection` for both paths.
     */
    internal fun moveFileDirectly(context: Context?, sourcePath: String, targetPath: String): Boolean {
        val src = File(sourcePath)
        val dst = File(targetPath)
        if (!src.isFile) {
            logWarn("moveFileDirectly: source missing: $sourcePath")
            return false
        }
        if (dst.exists()) {
            logWarn("moveFileDirectly: refusing to overwrite existing target: $targetPath")
            return false
        }
        val parent = dst.parentFile ?: return false
        if (!parent.exists() && !parent.mkdirs() && !parent.isDirectory) {
            logWarn("moveFileDirectly: could not create target directory: ${parent.absolutePath}")
            return false
        }
        val origModified = src.lastModified()
        val origSize = src.length()

        var moved = false
        if (sameVolume(sourcePath, targetPath)) {
            moved = src.renameTo(dst)
            if (!moved && !dst.exists()) {
                moved = try {
                    Files.move(src.toPath(), dst.toPath())
                    true
                } catch (e: Exception) {
                    logWarn("moveFileDirectly Files.move failed, will try copy+delete: $sourcePath -> $targetPath", e)
                    false
                }
            }
            if (moved && (!dst.isFile || src.exists())) {
                moved = false
            }
        }

        if (!moved) {
            // Cross-volume or filesystem-rename fallback: stream-copy to a temp sibling, verify
            // exact size, atomically publish into targetPath, then delete source (rolling back on
            // delete failure so neither half is ever silently lost).
            if (!copyFileToTarget(src, dst, origSize, origModified)) return false
            if (!src.delete() && src.exists()) {
                dst.delete()
                logWarn("moveFileDirectly: source delete failed after copy; rolled back $targetPath")
                return false
            }
        } else if (origModified > 0L && dst.lastModified() != origModified) {
            runCatching { dst.setLastModified(origModified) }
        }

        if (context != null) {
            runCatching { MediaScannerConnection.scanFile(context, arrayOf(sourcePath, targetPath), null, null) }
        }
        return true
    }

    /** Copies a file directly on the filesystem with temp-file staging and exact byte verification. */
    internal fun copyFileDirectly(context: Context?, sourcePath: String, targetPath: String): Boolean {
        val src = File(sourcePath)
        val dst = File(targetPath)
        if (!src.isFile || dst.exists()) return false
        val parent = dst.parentFile ?: return false
        if (!parent.exists() && !parent.mkdirs() && !parent.isDirectory) return false
        if (!copyFileToTarget(src, dst, src.length(), src.lastModified())) return false
        if (context != null) {
            runCatching { MediaScannerConnection.scanFile(context, arrayOf(targetPath), null, null) }
        }
        return true
    }

    private fun copyFileToTarget(src: File, dst: File, origSize: Long, origModified: Long): Boolean {
        val parent = dst.parentFile ?: return false
        val tmp = File(parent, ".fossify_move_${System.nanoTime()}_${dst.name}.tmp")
        return try {
            val copiedBytes = src.inputStream().use { input ->
                tmp.outputStream().use { output -> input.copyTo(output) }
            }
            if (origSize >= 0L && copiedBytes != origSize) {
                tmp.delete()
                logWarn("copyFileToTarget truncated ($copiedBytes != $origSize): ${src.absolutePath}")
                return false
            }
            if (origModified > 0L) runCatching { tmp.setLastModified(origModified) }
            if (dst.exists()) {
                tmp.delete()
                return false
            }
            val published = tmp.renameTo(dst) || try {
                Files.move(tmp.toPath(), dst.toPath(), StandardCopyOption.ATOMIC_MOVE)
                true
            } catch (_: Exception) {
                try {
                    Files.move(tmp.toPath(), dst.toPath())
                    true
                } catch (_: Exception) {
                    false
                }
            }
            if (!published || !dst.isFile || (origSize >= 0L && dst.length() != origSize)) {
                tmp.delete()
                if (dst.exists() && dst.length() != origSize) dst.delete()
                return false
            }
            true
        } catch (e: Exception) {
            tmp.delete()
            logWarn("copyFileToTarget failed: ${src.absolutePath} -> ${dst.absolutePath}", e)
            false
        }
    }

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
            val collection = if (isVideo) videoCollection() else imageCollection()
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
            // Publishing the item is the commit point of a cross-volume move. If this update fails,
            // leaving IS_PENDING set would hide the new file while the worker still deletes the
            // source and repoints Room. Roll the destination back and report failure instead.
            if (context.contentResolver.update(newUri, done, null, null) <= 0) {
                context.contentResolver.delete(newUri, null, null)
                return null
            }
            newUri
        } catch (e: Exception) {
            Log.w(TAG, "copy($sourceUri -> $targetRelativePath/$displayName) failed", e)
            null
        }
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

    data class MediaEntry(
        val path: String,
        val name: String,
        val modified: Long,
        val size: Long,
        val dateAdded: Long = 0L,
        // Matches Medium.type: 1=image, 2=video. The default keeps old cache/test constructors
        // source-compatible while every provider/DB producer now supplies the exact type.
        val type: Int = 1,
    )

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
            MediaStore.Files.FileColumns.MEDIA_TYPE,
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
                val tIdx = c.getColumnIndex(MediaStore.Files.FileColumns.MEDIA_TYPE)
                if (dIdx < 0) return@use
                while (c.moveToNext()) {
                    val p = c.getString(dIdx) ?: continue
                    if (!p.startsWith("$root/")) continue
                    val name = if (nIdx >= 0) c.getString(nIdx) ?: File(p).name else File(p).name
                    val modified = if (mIdx >= 0) c.getLong(mIdx) * 1000L else 0L
                    val size = if (sIdx >= 0) c.getLong(sIdx) else 0L
                    val added = (if (aIdx >= 0) c.getLong(aIdx) * 1000L else 0L).takeIf { it > 0 } ?: modified
                    val type = if (tIdx >= 0 && c.getInt(tIdx) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO) 2 else 1
                    out.add(MediaEntry(p, name, modified, size, added, type))
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
    @Volatile private var cachedEntriesLower: List<Pair<String, MediaEntry>>? = null
    @Volatile private var cachedPathGeneration: Long = -1L

    fun cachedEntriesUnder(rootPath: String): List<MediaEntry>? {
        val root = rootPath.trimEnd('/')
        return cachedEntries?.takeIf { cachedRoot == root && cachedPathGeneration == RefreshBus.pathGeneration }
    }

    /**
     * Returns all cached entries whose path lies under [rootPath], via binary range search instead
     * of a full linear scan. Requires [entries] to be sorted ascending by `path` (natural/UTF-16
     * order) - guaranteed because both cache producers ([refreshEntriesFromDb], [refreshEntriesUnder])
     * store their list through [sortedByPath].
     */
    fun entriesUnder(entries: List<MediaEntry>, rootPath: String): List<MediaEntry> {
        val prefix = rootPath.trimEnd('/') + "/"
        val upper = prefix.dropLast(1) + (prefix.last() + 1) // "root/" -> "root0"
        val lo = lowerBound(entries, prefix)
        val hi = lowerBound(entries, upper)
        return if (lo >= hi) emptyList() else entries.subList(lo, hi).toList()
    }

    /**
     * Case-insensitive counterpart to [entriesUnder] for Android's case-insensitive shared storage
     * (e.g. merging `Pictures` and `pictures` into one logical folder without dropping files).
     * Uses an O(log N + k) pre-sorted lowercase index when [entries] is the active process cache.
     */
    fun entriesUnderIgnoreCase(entries: List<MediaEntry>, rootPath: String): List<MediaEntry> {
        val prefix = rootPath.trimEnd('/').lowercase(java.util.Locale.ROOT) + "/"
        val indexed = cachedEntriesLower?.takeIf { cachedEntries === entries }
        if (indexed != null) {
            val upper = prefix.dropLast(1) + (prefix.last() + 1)
            val lo = lowerBoundIndexed(indexed, prefix)
            val hi = lowerBoundIndexed(indexed, upper)
            return if (lo >= hi) emptyList() else indexed.subList(lo, hi).map { it.second }
        }
        return entries.filter { it.path.startsWith(prefix, ignoreCase = true) }
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

    private fun lowerBoundIndexed(entries: List<Pair<String, MediaEntry>>, key: String): Int {
        var lo = 0
        var hi = entries.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (entries[mid].first < key) lo = mid + 1 else hi = mid
        }
        return lo
    }

    /** Sorted ascending by `path` in natural String order so [entriesUnder] can binary-search it. */
    private fun sortedByPath(entries: List<MediaEntry>): List<MediaEntry> = entries.sortedBy { it.path }

    private fun buildLowerIndex(entries: List<MediaEntry>): List<Pair<String, MediaEntry>> =
        entries.map { it.path.lowercase(java.util.Locale.ROOT) to it }.sortedBy { it.first }

    /** Re-queries MediaStore and refreshes the cache used by [cachedEntriesUnder]. Kept for the
     * off-[storageRoot] case (SD cards etc.); the storage-root path now goes through
     * [refreshEntriesFromDb], which is much faster - see its doc. */
    fun refreshEntriesUnder(context: Context, rootPath: String): List<MediaEntry> {
        val root = rootPath.trimEnd('/')
        val fresh = sortedByPath(mediaEntriesUnder(context, root))
        cachedRoot = root
        cachedEntries = fresh
        cachedEntriesLower = buildLowerIndex(fresh)
        cachedPathGeneration = RefreshBus.pathGeneration
        return fresh
    }

    /**
     * Same cache, but sourced from the Room `media` table instead of a full-device MediaStore
     * cursor.
     */
    fun refreshEntriesFromDb(context: Context, rootPath: String): List<MediaEntry> {
        val root = rootPath.trimEnd('/')
        val startedAt = android.os.SystemClock.elapsedRealtime()
        val fresh = try {
            context.mediaDB.getAllLiveEntriesForExplorer()
        } catch (e: Exception) {
            Log.e("MediaStoreOps", "DB entry load failed, falling back to MediaStore", e)
            return refreshEntriesUnder(context, root)
        }
        val sorted = sortedByPath(fresh)
        cachedRoot = root
        cachedEntries = sorted
        cachedEntriesLower = buildLowerIndex(sorted)
        cachedPathGeneration = RefreshBus.pathGeneration
        Log.d(
            "GalleryPerf",
            "explorer cache bootstrap rows=${sorted.size} durationMs=${android.os.SystemClock.elapsedRealtime() - startedAt}",
        )
        return sorted
    }

    /**
     * Patches the process cache for a known set of changed files.
     */
    fun refreshEntriesForPathsFromDb(
        context: Context,
        rootPath: String,
        affectedPaths: Collection<String>,
    ): List<MediaEntry>? {
        val root = rootPath.trimEnd('/')
        val current = cachedEntries?.takeIf { cachedRoot == root && cachedPathGeneration == RefreshBus.pathGeneration } ?: return null
        val paths = affectedPaths.map { it.trimEnd('/') }.filter { it.isNotBlank() }.toSet()
        if (paths.isEmpty()) return current
        val startedAt = android.os.SystemClock.elapsedRealtime()

        val kept = current.filterNot { entry -> paths.any { affects(it, entry.path) } }
        val fresh = try {
            paths.toList()
                .chunked(900)
                .flatMap { context.mediaDB.getLiveEntriesForExplorerPaths(it) }
        } catch (e: Exception) {
            android.util.Log.e("MediaStoreOps", "Targeted DB entry load failed", e)
            return null
        }
        val merged = sortedByPath((kept + fresh).distinctBy { it.path })
        cachedEntries = merged
        cachedEntriesLower = buildLowerIndex(merged)
        cachedPathGeneration = RefreshBus.pathGeneration
        android.util.Log.d(
            "GalleryPerf",
            "explorer cache patch changed=${paths.size} rows=${fresh.size} durationMs=${android.os.SystemClock.elapsedRealtime() - startedAt}",
        )
        return merged
    }

    /** True when [changed] is a file/folder inside [cachedPath], or vice versa. */
    private fun affects(cachedPath: String, changed: String): Boolean =
        cachedPath.equals(changed, ignoreCase = true) ||
            cachedPath.startsWith("$changed/", ignoreCase = true) ||
            changed.startsWith("$cachedPath/", ignoreCase = true)
}

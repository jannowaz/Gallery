package org.fossify.gallery.compose.screens.analysis

import android.content.Context
import android.provider.MediaStore

/**
 * Enumerates media files under a folder via MediaStore instead of raw filesystem directory
 * listing. On Android 11+/13+ with only READ_MEDIA_* permissions, directory listing of shared
 * storage (File.listFiles / Files.newDirectoryStream) is blocked and returns nothing, which made
 * the analysis/duplicate scans silently report "no files". Reading a known media file's bytes by
 * path is still permitted, so we resolve the file paths through MediaStore and let the callers
 * read each file directly.
 */
object MediaStoreEnumerator {

    /** Returns absolute file paths of all images/videos located under [rootPath] (recursive). */
    fun mediaPathsUnder(context: Context, rootPath: String): List<String> {
        val root = rootPath.trimEnd('/')
        if (root.isEmpty()) return emptyList()

        val uri = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(MediaStore.MediaColumns.DATA, MediaStore.Files.FileColumns.MEDIA_TYPE)
        val mediaTypeSel = "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?"
        // Escape LIKE wildcards in the path so folders containing '%' or '_' match literally.
        val escaped = root.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        val selection = "($mediaTypeSel) AND (${MediaStore.MediaColumns.DATA} LIKE ? ESCAPE '\\' OR ${MediaStore.MediaColumns.DATA} = ?)"
        val args = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
            "$escaped/%",
            root,
        )

        val paths = ArrayList<String>()
        try {
            context.contentResolver.query(uri, projection, selection, args, null)?.use { c ->
                val dataIdx = c.getColumnIndex(MediaStore.MediaColumns.DATA)
                if (dataIdx < 0) return@use
                while (c.moveToNext()) {
                    val p = c.getString(dataIdx) ?: continue
                    if (p.startsWith("$root/") || p == root) paths.add(p)
                }
            }
        } catch (_: Exception) { }
        return paths
    }
}

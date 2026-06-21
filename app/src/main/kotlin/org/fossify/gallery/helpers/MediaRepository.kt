package org.fossify.gallery.helpers

import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.runBlocking
import org.fossify.gallery.extensions.collectionDB
import org.fossify.gallery.extensions.directoryDB
import org.fossify.gallery.extensions.favoritesDB
import org.fossify.gallery.extensions.getFavoriteFromPath
import org.fossify.gallery.extensions.mediaDB
import org.fossify.gallery.extensions.mediaCacheDB
import org.fossify.gallery.models.Directory
import org.fossify.gallery.models.MediaCollection
import org.fossify.gallery.models.Medium
import org.fossify.gallery.models.MediaCache
import java.io.File

class MediaRepository(private val context: Context) : MediaRepositoryInterface {

    override fun getMediaFromPath(path: String): List<Medium> {
        return try { context.mediaDB.getMediaFromPath(path) } catch (_: Exception) { emptyList() }
    }

    override fun isFavorite(path: String): Boolean {
        return try { context.favoritesDB.isFavorite(path) } catch (_: Exception) { false }
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
        } catch (_: Exception) { }
        RefreshBus.trigger()
    }

    override fun getRating(path: String): Int {
        return XmpWriter.read(path).rating
    }

    override fun updateRating(path: String, rating: Int) {
        val current = XmpWriter.read(path)
        XmpWriter.write(path, current.tags, rating)
        try { context.mediaDB.updateRating(path, rating) } catch (_: Exception) { }
        syncCache(path, current.tags.joinToString(","), rating)
    }

    override fun getTags(path: String): Set<String> {
        return XmpWriter.read(path).tags.toSet()
    }

    override fun addTag(path: String, tag: String) {
        val current = XmpWriter.read(path)
        val tags = if (tag in current.tags) current.tags else current.tags + tag
        XmpWriter.write(path, tags, current.rating)
        syncCache(path, tags.joinToString(","), current.rating)
    }

    override fun removeTag(path: String, tag: String) {
        val current = XmpWriter.read(path)
        val tags = current.tags.filter { it != tag }
        XmpWriter.write(path, tags, current.rating)
        syncCache(path, tags.joinToString(","), current.rating)
    }

    private fun syncCache(path: String, tags: String, rating: Int) {
        try {
            runBlocking {
                context.mediaCacheDB.upsertAll(listOf(MediaCache(fullPath = path, tags = tags, rating = rating, lastScanned = System.currentTimeMillis())))
            }
        } catch (_: Exception) { }
    }

    override fun deleteMedium(path: String) {
        try { context.mediaDB.deleteMediumPath(path) } catch (_: Exception) { }
        try { context.favoritesDB.deleteFavoritePath(path) } catch (_: Exception) { }
        try { context.mediaCacheDB.deleteByPathSync(path) } catch (_: Exception) { }
        try { File("$path.xmp").delete() } catch (_: Exception) { }
        try { File(path).delete() } catch (_: Exception) { }
    }

    fun getByMinRating(minRating: Int): List<Medium> =
        try { context.mediaDB.getByMinRating(minRating) } catch (_: Exception) { emptyList() }

    fun getMediaByPaths(paths: List<String>): List<Medium> =
        try { context.mediaDB.getMediaByPaths(paths) } catch (_: Exception) { emptyList() }

    fun getNewestMedia(limit: Int): List<Medium> =
        try { context.mediaDB.getNewestMedia(limit) } catch (_: Exception) { emptyList() }

    suspend fun getTaggedPaths(): Set<String> =
        try { context.mediaCacheDB.getAllTagged().map { it.fullPath }.toSet() } catch (_: Exception) { emptySet() }

    suspend fun getTagCounts(): Map<String, Int> =
        try {
            context.mediaCacheDB.getAllTagged()
                .flatMap { it.tags.split(",").filter(String::isNotBlank) }
                .groupingBy { it }
                .eachCount()
        } catch (_: Exception) { emptyMap() }

    suspend fun getAllTags(): List<String> =
        try {
            context.mediaCacheDB.getAllTagged()
                .flatMap { it.tags.split(",").filter(String::isNotBlank) }
                .distinct()
                .sorted()
        } catch (_: Exception) { emptyList() }

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

    suspend fun getAllTagged(): List<MediaCache> =
        try { context.mediaCacheDB.getAllTagged() } catch (_: Exception) { emptyList() }

    suspend fun getRecentTagged(limit: Int): List<MediaCache> =
        try { context.mediaCacheDB.getRecentTagged(limit) } catch (_: Exception) { emptyList() }

    suspend fun upsertCache(items: List<MediaCache>) {
        try { context.mediaCacheDB.upsertAll(items) } catch (_: Exception) { }
    }

    fun getValidFavoritePaths(): List<String> =
        try { context.favoritesDB.getValidFavoritePaths() } catch (_: Exception) { emptyList() }

    fun addFavoriteByPath(path: String) {
        try { context.favoritesDB.insert(context.getFavoriteFromPath(path)) } catch (_: Exception) { }
    }

    /** DB-only rating write (no XMP). Used when importing ratings that already exist on disk. */
    fun setDbRating(path: String, rating: Int) {
        try { context.mediaDB.updateRating(path, rating) } catch (_: Exception) { }
    }

    fun updateMediumPath(oldPath: String, parentPath: String, newName: String, newPath: String) {
        try { context.mediaDB.updateMedium(oldPath, parentPath, newName, newPath) } catch (_: Exception) { }
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

    /** Pulls newly added images/videos from MediaStore (plus recent disk folders) into the local DB. */
    fun syncNewMediaFromStore() {
        try {
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
            val sel = "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?"
            val args = arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(), MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString())
            val storageRoot = Environment.getExternalStorageDirectory().absolutePath
            context.contentResolver.query(uri, proj, sel, args, "${MediaStore.MediaColumns.DATE_MODIFIED} DESC")?.use { c ->
                val dataIdx = c.getColumnIndex(MediaStore.MediaColumns.DATA)
                val relPathIdx = c.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                val nameIdx = c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                val dateIdx = c.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                val sizeIdx = c.getColumnIndex(MediaStore.MediaColumns.SIZE)
                val typeIdx = c.getColumnIndex(MediaStore.Files.FileColumns.MEDIA_TYPE)
                var scanned = 0
                while (c.moveToNext()) {
                    if (scanned++ >= 20000) break
                    var path = if (dataIdx >= 0) c.getString(dataIdx) else null
                    if (path.isNullOrBlank()) {
                        val relPath = if (relPathIdx >= 0) c.getString(relPathIdx) ?: "" else ""
                        val name = if (nameIdx >= 0) c.getString(nameIdx) ?: "" else ""
                        path = "$storageRoot/$relPath$name"
                    }
                    if (path.isNullOrBlank() || path in existingPaths) continue
                    val name = File(path).name
                    val modified = if (dateIdx >= 0) c.getLong(dateIdx) * 1000L else System.currentTimeMillis()
                    val size = if (sizeIdx >= 0) c.getLong(sizeIdx) else 0L
                    val mediaType = if (typeIdx >= 0) c.getInt(typeIdx) else 1
                    val type = if (mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO) 2 else 1
                    newMedia.add(Medium(null, name, path, File(path).parent ?: "", modified, modified, size, type, 0, false, 0L, 0L, 0))
                }
            }
            val deletedPaths = try { context.mediaDB.getDeletedMedia().map { it.path }.toSet() } catch (_: Exception) { emptySet() }
            newMedia.addAll(recentDiskMedia(existingPaths + newMedia.map { it.path } + deletedPaths))
            if (newMedia.isNotEmpty()) context.mediaDB.insertAllKeepingExisting(newMedia)
        } catch (_: Exception) { }
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
        } catch (_: Exception) { }
    }

    fun restoreFromRecycleBin(path: String) {
        try {
            context.mediaDB.restoreDeleted(path)
        } catch (_: Exception) { }
    }
}

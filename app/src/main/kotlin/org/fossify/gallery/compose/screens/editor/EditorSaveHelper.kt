package org.fossify.gallery.compose.screens.editor

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.graphics.scale
import androidx.core.net.toUri
import androidx.exifinterface.media.ExifInterface
import coil3.BitmapImage
import coil3.SingletonImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.memory.MemoryCache
import coil3.request.CachePolicy
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.request.bitmapConfig
import coil3.size.Scale
import coil3.size.Size
import coil3.toBitmap
import com.zomato.photofilters.FilterPack
import com.zomato.photofilters.imageprocessors.Filter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fossify.commons.extensions.getCompressionFormat
import org.fossify.commons.extensions.getFilenameFromContentUri
import org.fossify.commons.extensions.getFilenameFromPath
import org.fossify.commons.extensions.getParentPath
import org.fossify.commons.extensions.getPicturesDirectoryPath
import org.fossify.commons.extensions.getRealPathFromURI
import org.fossify.commons.extensions.isInDownloadDir
import org.fossify.commons.extensions.isRestrictedWithSAFSdk30
import org.fossify.commons.extensions.rescanPaths
import org.fossify.gallery.R
import org.fossify.gallery.compose.components.GalleryThumbnailKeyRegistry
import org.fossify.gallery.extensions.mediaDB
import org.fossify.gallery.extensions.mediaTagDB
import org.fossify.gallery.extensions.readExif
import org.fossify.gallery.extensions.writeExif
import org.fossify.gallery.helpers.FilterThumbnailsManager
import org.fossify.gallery.helpers.IntentSecurity
import org.fossify.gallery.helpers.MediaRepository
import org.fossify.gallery.helpers.MoveConflicts
import org.fossify.gallery.helpers.RefreshBus
import org.fossify.gallery.helpers.TEMP_FOLDER_NAME
import org.fossify.gallery.helpers.XmpWriter
import org.fossify.gallery.models.FilterItem
import org.fossify.gallery.models.Medium
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

private const val SAVE_NEW_QUALITY = 90
private const val OVERWRITE_QUALITY = 95

internal data class SaveAsDefaults(
    val folderPath: String,
    val filename: String,
    val extension: String,
)

internal sealed interface SaveBitmapOutcome {
    data class Saved(val path: String, val backedUpOriginal: Boolean) : SaveBitmapOutcome
    data class Failed(val messageRes: Int) : SaveBitmapOutcome
}

internal object EditorSaveHelper {

    fun prepareSaveAsDefaults(
        context: Context,
        path: String,
        appendSuffix: Boolean,
    ): SaveAsDefaults {
        val parent = path.getParentPath()
        val folderPath = if (context.isRestrictedWithSAFSdk30(parent) && !context.isInDownloadDir(parent)) {
            context.getPicturesDirectoryPath(parent)
        } else {
            parent
        }
        val fullName = path.getFilenameFromPath()
        val dotAt = fullName.lastIndexOf('.')
        var baseName = if (dotAt > 0) fullName.substring(0, dotAt) else fullName
        val extension = if (dotAt > 0) fullName.substring(dotAt + 1) else "jpg"
        if (appendSuffix) {
            baseName += "_1"
        }
        return SaveAsDefaults(folderPath = folderPath, filename = baseName, extension = extension)
    }

    suspend fun loadBitmapWithCoil(
        context: Context,
        uri: Uri,
        size: Size = Size.ORIGINAL,
        scale: Scale = Scale.FIT,
        mutable: Boolean = false,
    ): Bitmap? = withContext(Dispatchers.IO) {
        val request = ImageRequest.Builder(context.applicationContext)
            .data(uri)
            .size(size)
            .scale(scale)
            .allowHardware(false)
            .bitmapConfig(Bitmap.Config.ARGB_8888)
            .memoryCachePolicy(CachePolicy.DISABLED)
            .diskCachePolicy(CachePolicy.DISABLED)
            .build()
        when (val result = SingletonImageLoader.get(context.applicationContext).execute(request)) {
            is SuccessResult -> {
                val raw = (result.image as? BitmapImage)?.bitmap ?: result.image.toBitmap()
                if (raw.config == Bitmap.Config.ARGB_8888 && (!mutable || raw.isMutable)) {
                    raw
                } else {
                    raw.copy(Bitmap.Config.ARGB_8888, mutable)
                }
            }
            is ErrorResult -> null
        }
    }

    suspend fun buildFilterItems(
        context: Context,
        uri: Uri,
        thumbnailSizePx: Int,
    ): List<FilterItem> = withContext(Dispatchers.Default) {
        val baseThumb = loadBitmapWithCoil(
            context = context,
            uri = uri,
            size = Size(thumbnailSizePx, thumbnailSizePx),
            scale = Scale.FILL,
            mutable = true,
        ) ?: return@withContext emptyList()
        val manager = FilterThumbnailsManager()
        manager.clearThumbs()
        val noFilter = Filter(context.getString(org.fossify.commons.R.string.none))
        manager.addThumb(FilterItem(baseThumb, noFilter))
        FilterPack.getFilterPack(context).forEach { filter ->
            manager.addThumb(FilterItem(baseThumb, filter))
        }
        manager.processThumbs()
    }

    suspend fun saveBitmapToPath(
        context: Context,
        sourceUri: Uri?,
        bitmap: Bitmap,
        targetPath: String,
        resizeWidth: Int,
        resizeHeight: Int,
        cachedExif: ExifInterface?,
    ): SaveBitmapOutcome = withContext(Dispatchers.IO) {
        val exif = cachedExif ?: sourceUri?.let { runCatching { context.readExif(it) }.getOrNull() }
        val originalFilePath = sourceUri?.let { src ->
            if (src.scheme == "file") src.path else runCatching { context.getRealPathFromURI(src) }.getOrNull()
        }
        val isOverwrite = originalFilePath != null &&
            File(targetPath).absolutePath == File(originalFilePath).absolutePath
        if (isOverwrite) {
            saveBitmapOverwriteSafely(context, bitmap, targetPath, resizeWidth, resizeHeight, exif)
        } else {
            saveBitmapAsNewFile(context, bitmap, targetPath, resizeWidth, resizeHeight, exif)
        }
    }

    private fun saveBitmapAsNewFile(
        context: Context,
        bitmap: Bitmap,
        targetPath: String,
        resizeWidth: Int,
        resizeHeight: Int,
        exif: ExifInterface?,
    ): SaveBitmapOutcome {
        return try {
            val file = File(targetPath)
            file.parentFile?.mkdirs()
            val encodedBitmap = scaleIfNeeded(bitmap, resizeWidth, resizeHeight)
            FileOutputStream(file).use { out ->
                if (!encodedBitmap.compress(file.absolutePath.getCompressionFormat(), SAVE_NEW_QUALITY, out)) {
                    throw IOException("Bitmap encoder rejected edit")
                }
            }
            if (encodedBitmap !== bitmap) encodedBitmap.recycle()
            context.writeExif(exif, file.toUri())
            SaveBitmapOutcome.Saved(file.absolutePath, backedUpOriginal = false)
        } catch (_: OutOfMemoryError) {
            SaveBitmapOutcome.Failed(org.fossify.commons.R.string.out_of_memory_error)
        } catch (_: Exception) {
            SaveBitmapOutcome.Failed(R.string.image_editing_failed)
        }
    }

    private fun saveBitmapOverwriteSafely(
        context: Context,
        bitmap: Bitmap,
        path: String,
        resizeWidth: Int,
        resizeHeight: Int,
        exif: ExifInterface?,
    ): SaveBitmapOutcome {
        val file = File(path)
        val tmp = File(context.cacheDir, "edit_${System.nanoTime()}.${path.substringAfterLast('.', "jpg")}")
        val encodeOutcome = encodeTempFile(tmp, bitmap, path, resizeWidth, resizeHeight)
        if (encodeOutcome != null) return encodeOutcome

        val origMedium = runCatching { context.mediaDB.getMediaByPaths(listOf(path)).firstOrNull() }.getOrNull()
        val origModified = file.lastModified()
        val originalXmp = runCatching { XmpWriter.readOrNull(path) }.getOrNull()
        val backupPath = runCatching { MoveConflicts.backupToRecycleBin(context, path) }.getOrNull()
        if (backupPath == null) {
            runCatching { tmp.delete() }
            return SaveBitmapOutcome.Failed(R.string.image_editing_failed)
        }

        val placed = tmp.renameTo(file) ||
            runCatching { tmp.copyTo(file, overwrite = false); tmp.delete() }.isSuccess
        if (!placed) {
            runCatching { tmp.delete() }
            runCatching { file.delete() }
            runCatching { MediaRepository(context).restoreFromRecycleBin(backupPath) }
            return SaveBitmapOutcome.Failed(R.string.image_editing_failed)
        }

        restoreMetadataAfterOverwrite(context, file, path, backupPath, origMedium, origModified, exif, originalXmp)
        return SaveBitmapOutcome.Saved(path, backedUpOriginal = true)
    }

    private fun encodeTempFile(
        tmp: File,
        bitmap: Bitmap,
        path: String,
        resizeWidth: Int,
        resizeHeight: Int,
    ): SaveBitmapOutcome.Failed? {
        try {
            FileOutputStream(tmp).use { out ->
                val bmp = scaleIfNeeded(bitmap, resizeWidth, resizeHeight)
                val ok = bmp.compress(path.getCompressionFormat(), OVERWRITE_QUALITY, out)
                if (bmp !== bitmap) bmp.recycle()
                if (!ok) throw IOException("Bitmap encoder rejected edit")
            }
        } catch (_: OutOfMemoryError) {
            runCatching { tmp.delete() }
            return SaveBitmapOutcome.Failed(org.fossify.commons.R.string.out_of_memory_error)
        } catch (_: Exception) {
            runCatching { tmp.delete() }
            return SaveBitmapOutcome.Failed(R.string.image_editing_failed)
        }
        if (!tmp.exists() || tmp.length() == 0L) {
            runCatching { tmp.delete() }
            return SaveBitmapOutcome.Failed(R.string.image_editing_failed)
        }
        return null
    }

    private fun restoreMetadataAfterOverwrite(
        context: Context,
        file: File,
        path: String,
        backupPath: String,
        origMedium: Medium?,
        origModified: Long,
        exif: ExifInterface?,
        originalXmp: XmpWriter.XmpData?,
    ) {
        context.writeExif(exif, file.toUri())
        originalXmp?.let { xmp ->
            if (xmp.tags.isNotEmpty() || xmp.rating > 0) {
                runCatching { XmpWriter.write(path, xmp.tags, xmp.rating, xmp.hierarchy) }
            }
        }
        if (origModified > 0) runCatching { file.setLastModified(origModified) }
        origMedium?.let { m ->
            runCatching {
                context.mediaDB.insertAllKeepingExisting(
                    listOf(
                        Medium(
                            id = null,
                            name = file.name,
                            path = path,
                            parentPath = file.parent ?: "",
                            modified = m.modified,
                            taken = m.taken,
                            size = file.length(),
                            type = m.type,
                            videoDuration = m.videoDuration,
                            isFavorite = m.isFavorite,
                            deletedTS = 0L,
                            mediaStoreId = 0L,
                            rating = m.rating,
                            dateAdded = m.dateAdded,
                        ),
                    ),
                )
            }
        }
        if (backupPath == path) {
            runCatching { context.mediaDB.restoreDeleted(path) }
        } else {
            runCatching { context.mediaTagDB.copyPath(backupPath, path) }
        }
    }

    suspend fun writeBitmapToCacheForShare(
        context: Context,
        sourceUri: Uri,
        bitmap: Bitmap,
    ): String? = withContext(Dispatchers.IO) {
        runCatching {
            val folder = File(context.cacheDir, TEMP_FOLDER_NAME)
            if (!folder.exists() && !folder.mkdirs()) return@withContext null
            val rawFilename = context.applicationContext.getFilenameFromContentUri(sourceUri)
            val filename = IntentSecurity.sanitizeSingleFilename(
                rawName = rawFilename,
                fallback = "tmp-${System.currentTimeMillis()}.jpg",
            )
            val target = File(folder, filename)
            FileOutputStream(target).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 0, out)
            }
            target.absolutePath
        }.getOrNull()
    }

    @OptIn(ExperimentalCoilApi::class)
    fun finalizeSavedPath(activity: Activity, path: String, onFinished: () -> Unit) {
        runCatching {
            val loader = SingletonImageLoader.get(activity.applicationContext)
            GalleryThumbnailKeyRegistry.get(path)?.let { key ->
                loader.memoryCache?.remove(key)
            }
            loader.memoryCache?.remove(MemoryCache.Key(path))
            loader.memoryCache?.remove(MemoryCache.Key("viewer:$path:0"))
            loader.diskCache?.remove(path)
        }
        RefreshBus.trigger(affectedPaths = listOf(path))
        val paths = arrayListOf(path)
        activity.rescanPaths(paths) {
            onFinished()
        }
    }
}

private fun scaleIfNeeded(bitmap: Bitmap, resizeWidth: Int, resizeHeight: Int): Bitmap =
    if (resizeWidth > 0 && resizeHeight > 0) {
        bitmap.scale(resizeWidth, resizeHeight, false)
    } else {
        bitmap
    }

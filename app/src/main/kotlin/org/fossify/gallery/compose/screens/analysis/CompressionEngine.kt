package org.fossify.gallery.compose.screens.analysis

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.exifinterface.media.ExifInterface
import androidx.media3.common.MediaItem
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.fossify.gallery.extensions.copyNonDimensionAttributesTo
import java.io.File

/**
 * Produces a probe-compressed copy of an image/video for the storage-analysis review flow.
 * Never touches the original - output always goes to a fresh temp file under [outputDir], and the
 * caller (see CompressionWorker/CompressionReviewScreen) decides afterwards whether to keep it.
 */
class CompressionEngine(private val context: Context) {

    companion object {
        /** Single source of truth for the temp-file location, shared with the stale-file sweep in
         * RecycleBinCleanupWorker. */
        fun cacheDir(context: Context) = File(context.cacheDir, "compression_review")
    }

    private val outputDir: File by lazy {
        cacheDir(context).apply { mkdirs() }
    }

    /** Downscales to [maxDimension] on the longest edge (no-op if already smaller) and re-encodes
     * as JPEG at [quality]. Non-dimension EXIF (rotation, GPS, capture date, etc.) is best-effort
     * carried over via the same [copyNonDimensionAttributesTo] used by the image editor. */
    suspend fun compressImage(path: String, maxDimension: Int, quality: Int): File = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        check(bounds.outWidth > 0 && bounds.outHeight > 0) { "Decode failed: $path" }

        var sampleSize = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= maxDimension) sampleSize *= 2
        val decoded = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sampleSize })
            ?: error("Decode failed: $path")

        val scale = maxDimension.toFloat() / maxOf(decoded.width, decoded.height)
        val scaled = if (scale < 1f) {
            val w = (decoded.width * scale).toInt().coerceAtLeast(1)
            val h = (decoded.height * scale).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(decoded, w, h, true).also { if (it !== decoded) decoded.recycle() }
        } else decoded

        val outFile = File(outputDir, "img_${System.nanoTime()}.jpg")
        outFile.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, quality, it) }
        scaled.recycle()

        try {
            ExifInterface(path).copyNonDimensionAttributesTo(ExifInterface(outFile.absolutePath))
        } catch (_: Exception) { }

        outFile
    }

    /** Re-encodes the video at [path] to fit within [targetWidth]x[targetHeight] (aspect-preserving,
     * no upscale) at roughly [targetBitrateKbps]. Runs the hardware-accelerated Media3 Transformer,
     * which must be driven from a Looper thread - the whole call hops to the main dispatcher for
     * that, then back off it once the transform is done. */
    suspend fun compressVideo(path: String, targetWidth: Int, targetHeight: Int, targetBitrateKbps: Long): File =
        withContext(Dispatchers.Main) {
            val outFile = File(outputDir, "vid_${System.nanoTime()}.mp4")
            val presentation = Presentation.createForWidthAndHeight(targetWidth, targetHeight, Presentation.LAYOUT_SCALE_TO_FIT)
            val editedMediaItem = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(File(path))))
                .setEffects(Effects(emptyList(), listOf(presentation)))
                .build()
            val encoderFactory = DefaultEncoderFactory.Builder(context)
                .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate((targetBitrateKbps * 1000).toInt()).build())
                .build()
            val transformer = Transformer.Builder(context)
                .setEncoderFactory(encoderFactory)
                .build()

            suspendCancellableCoroutine { cont ->
                transformer.addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        if (cont.isActive) cont.resumeWith(Result.success(outFile))
                    }

                    override fun onError(composition: Composition, exportResult: ExportResult, exception: ExportException) {
                        if (cont.isActive) cont.resumeWith(Result.failure(exception))
                    }
                })
                cont.invokeOnCancellation { Handler(Looper.getMainLooper()).post { transformer.cancel() } }
                transformer.start(editedMediaItem, outFile.absolutePath)
            }
        }
}

package org.fossify.gallery.compose.screens.analysis
import org.fossify.gallery.R

import android.content.Context
import android.net.Uri

@kotlinx.serialization.Serializable
data class AnalysisResult(
    val path: String,
    val name: String,
    val fileSize: Long,
    val mediaType: Int,
    val width: Int,
    val height: Int,
    val durationMs: Long = 0,
    val bitrateKbps: Long = 0,
    val codec: String? = null,
    val imageFormat: String? = null,
    val bpp: Float = 0f,
    val score: Int = 0,
    val wastedBytes: Long = 0,
    val reasons: List<String> = emptyList(),
)

object AnalysisCriteria {

    // Configurable thresholds
    data class Thresholds(
        val videoBitrate4K: Long = 100_000,     // Kbps – YouTube: 68 Mbps, Blu-ray: 128 Mbps
        val videoBitrate1440p: Long = 50_000,    // YouTube: 24 Mbps
        val videoBitrate1080p: Long = 30_000,    // YouTube: 12 Mbps, Blu-ray: 25 Mbps → 30 = 2× YouTube
        val videoBitrate720p: Long = 15_000,     // YouTube: 7.5 Mbps → 15 = 2× YouTube
        val videoBitrateSD: Long = 8_000,        // YouTube: 2.5 Mbps → 8 = 3× YouTube
        val videoMbPerMin: Long = 200,           // MB/Minute – entspricht ~27 Mbps
        val imageBmpBpp: Float = 0.1f,           // BMP hat typisch 24 BPP → alles flagged
        val imagePngBpp: Float = 1.5f,           // PNG-Foto > 1.5 BPP → JPEG wäre 0.2–0.3 BPP
        val imageJpegBpp: Float = 0.5f,          // JPEG Q=100 ~0.5 BPP, Q=85 ~0.2 BPP
        val maxMegapixels: Int = 12,             // 12 MP = 4000×3000, mehr ist auf Handy unsichtbar
        val maxFileSize: Long = 500_000_000,     // 500 MB Einzeldatei
    )
    val thresholds = Thresholds()

    fun analyze(path: String, context: Context? = null): AnalysisResult? {
        val isContentUri = path.startsWith("content://")
        val name: String
        val size: Long

        if (isContentUri) {
            val uri = Uri.parse(path)
            name = uri.lastPathSegment ?: return null
            size = context?.contentResolver?.let { cr ->
                cr.query(uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getLong(0) else 0L
                }
            } ?: 0L
        } else {
            val file = java.io.File(path)
            if (!file.exists()) return null
            name = file.name
            size = file.length()
        }
        val ext = name.substringAfterLast('.', "").lowercase()
        val isVideo = ext in VIDEO_EXTS
        val isImage = ext in IMAGE_EXTS
        if (!isVideo && !isImage) return null

        val width: Int
        val height: Int
        val durationMs: Long
        val codec: String?
        val bitrateKbps: Long

        if (isVideo) {
            val meta = readVideoMeta(path, context)
            width = meta.width
            height = meta.height
            durationMs = meta.durationMs
            codec = meta.codec
            bitrateKbps = if (durationMs > 0) (size * 8 * 1000 / durationMs) / 1000 else 0
        } else {
            val dims = readImageDimensions(path, context)
            width = dims.first
            height = dims.second
            durationMs = 0
            codec = null
            bitrateKbps = 0
        }
        if (width == 0 || height == 0) return null

        val bpp = if (isImage && width > 0 && height > 0) size.toFloat() / (width * height) else 0f

        val reasons = mutableListOf<String>()
        var wastedBytes = 0L

        if (isVideo) {
            reasons.addAll(analyzeVideo(context, size, width, height, durationMs, bitrateKbps, codec))
            wastedBytes = estimateVideoWaste(size, width, height, durationMs, bitrateKbps)
        } else {
            reasons.addAll(analyzeImage(context, size, ext, width, height, bpp))
            wastedBytes = estimateImageWaste(size, ext, width, height, bpp)
        }

        // <5MB estimated savings = not worth it - gates score along with wastedBytes/reasons so a
        // file whose only fired reason nets negligible savings doesn't still slip into the results
        // list (via score>0) as a ghost entry with no visible reason or savings estimate.
        val passesSavingsThreshold = wastedBytes > 5_000_000
        return AnalysisResult(
            path = path, name = name, fileSize = size,
            mediaType = if (isVideo) 2 else 1,
            width = width, height = height, durationMs = durationMs,
            bitrateKbps = bitrateKbps, codec = codec,
            imageFormat = if (isImage) ext else null,
            bpp = bpp, score = if (passesSavingsThreshold) reasons.size.coerceAtMost(3) else 0,
            wastedBytes = if (passesSavingsThreshold) wastedBytes else 0,
            reasons = if (passesSavingsThreshold) reasons else emptyList(),
        )
    }

    private fun analyzeVideo(context: Context?, size: Long, width: Int, height: Int, durationMs: Long, bitrateKbps: Long, codec: String?): List<String> {
        val context = context ?: return emptyList()
        val reasons = mutableListOf<String>()
        val pixels = width * height
        val t = thresholds
        val (limitKbps, label) = when {
            pixels >= 7680 * 4320 -> t.videoBitrate4K to "4K"
            pixels >= 3840 * 2160 -> t.videoBitrate4K to "4K"
            pixels >= 2560 * 1440 -> t.videoBitrate1440p to "1440p"
            pixels >= 1920 * 1080 -> t.videoBitrate1080p to "1080p"
            pixels >= 1280 * 720 -> t.videoBitrate720p to "720p"
            else -> t.videoBitrateSD to "SD"
        }
        val ytRef = when (label) { "4K" -> 68_000L; "1440p" -> 24_000L; "1080p" -> 12_000L; "720p" -> 7_500L; else -> 2_500L }
        if (bitrateKbps > limitKbps) {
            reasons.add(context.getString(R.string.reason_video_bitrate, formatKbps(bitrateKbps), label, formatKbps(ytRef), "%.0f".format(bitrateKbps.toDouble() / ytRef)))
        }
        if (durationMs > 0) {
            val mbPerMin = (size.toDouble() / (durationMs / 60000.0)) / 1_000_000
            if (mbPerMin > t.videoMbPerMin) reasons.add(context.getString(R.string.reason_video_mbmin, "%.0f".format(mbPerMin), formatKbps((mbPerMin * 8 * 1_000_000 / 60).toLong()), t.videoMbPerMin))
        }
        if (codec != null && codec in listOf("video/mp4v-es", "video/3gpp", "video/x-ms-wmv")) {
            reasons.add(context.getString(R.string.reason_video_codec, codec))
        }
        if (size > t.maxFileSize) reasons.add(context.getString(R.string.reason_single_file, t.maxFileSize / 1_000_000))
        if (width > 3840 || height > 2160) reasons.add(context.getString(R.string.reason_video_resolution, width, height))
        return reasons
    }

    private fun analyzeImage(context: Context?, size: Long, format: String, width: Int, height: Int, bpp: Float): List<String> {
        val context = context ?: return emptyList()
        val reasons = mutableListOf<String>()
        val mp = (width * height) / 1_000_000
        val t = thresholds
        when (format) {
            "bmp", "dib" -> reasons.add(context.getString(R.string.reason_img_bmp, "%.1f".format(bpp)))
            "png" -> {
                if (bpp > t.imagePngBpp) reasons.add(context.getString(R.string.reason_img_png_photo, "%.1f".format(bpp)))
                else if (bpp > 0.8f) reasons.add(context.getString(R.string.reason_img_png_webp, "%.1f".format(bpp)))
            }
            "tiff", "tif" -> reasons.add(context.getString(R.string.reason_img_tiff))
            "jpeg", "jpg" -> {
                if (bpp > t.imageJpegBpp) reasons.add(context.getString(R.string.reason_img_jpeg_high, "%.2f".format(bpp), "%.2f".format(0.2f)))
                else if (bpp > 0.3f) reasons.add(context.getString(R.string.reason_img_jpeg_webp, "%.2f".format(bpp)))
            }
        }
        if (mp > t.maxMegapixels) reasons.add(context.getString(R.string.reason_img_megapixels, mp, width, height))
        if (width > 7680) reasons.add(context.getString(R.string.reason_img_width, width))
        if (height > 7680) reasons.add(context.getString(R.string.reason_img_height, height))
        if (size > t.maxFileSize) reasons.add(context.getString(R.string.reason_single_file, t.maxFileSize / 1_000_000))
        return reasons
    }

    /** The estimate shown next to the convert decision is derived from the exact target the
     * compression will actually use - previously this had its own ladder (4K → 50 Mbps) while the
     * real transform downscaled 4K to 1080p@12Mbps, so the UI promised ~10 MB where the result
     * saved ~39 MB. */
    private fun estimateVideoWaste(size: Long, width: Int, height: Int, durationMs: Long, bitrateKbps: Long): Long {
        if (durationMs <= 0) return 0
        val (_, _, targetKbps) = suggestedVideoTarget(width, height, bitrateKbps) ?: return 0
        val optimalBytes = (targetKbps * durationMs) / 8
        return (size - optimalBytes).coerceAtLeast(0)
    }

    /** Target (width, height, bitrateKbps) for re-encoding [result] - the single ladder shared
     * with [estimateVideoWaste], so the estimate shown to the user and the actual compression
     * always agree. Null if the source is already at/under target. */
    fun suggestedVideoTarget(result: AnalysisResult): Triple<Int, Int, Long>? {
        if (result.mediaType != 2) return null
        return suggestedVideoTarget(result.width, result.height, result.bitrateKbps)
    }

    private fun suggestedVideoTarget(width: Int, height: Int, bitrateKbps: Long): Triple<Int, Int, Long>? {
        if (width <= 0 || height <= 0) return null
        val pixels = width.toLong() * height
        val (targetW, targetKbps) = when {
            pixels >= 3840L * 2160 -> 1920 to 12_000L
            pixels >= 1920L * 1080 -> maxOf(width, height) to 12_000L
            pixels >= 1280L * 720 -> maxOf(width, height) to 6_000L
            else -> maxOf(width, height) to 3_000L
        }
        if (bitrateKbps <= targetKbps && targetW >= maxOf(width, height)) return null
        // Preserve aspect ratio when downscaling from >4K - targetW bounds the longest edge, so a
        // portrait source gets the swap.
        val longEdge = minOf(targetW, maxOf(width, height))
        return if (width >= height) Triple(longEdge, (longEdge.toLong() * height / width).toInt(), targetKbps)
        else Triple((longEdge.toLong() * width / height).toInt(), longEdge, targetKbps)
    }

    /** Target (longest edge in px, JPEG quality) for re-encoding [result] - mirrors
     * [estimateImageWaste]'s targets. Null if already at/under target. */
    fun suggestedImageTarget(result: AnalysisResult): Pair<Int, Int>? {
        if (result.mediaType != 1 || result.width <= 0 || result.height <= 0) return null
        val mp = (result.width.toLong() * result.height) / 1_000_000
        val longestEdge = maxOf(result.width, result.height)
        val needsDownscale = mp > thresholds.maxMegapixels
        val needsRequant = result.bpp > 0.3f
        if (!needsDownscale && !needsRequant) return null
        val targetEdge = if (needsDownscale) {
            // Scale the longest edge down until megapixels fit the threshold.
            val scale = kotlin.math.sqrt(thresholds.maxMegapixels.toDouble() / mp.coerceAtLeast(1))
            (longestEdge * scale).toInt().coerceAtLeast(1)
        } else longestEdge
        return targetEdge to 85
    }

    private fun estimateImageWaste(size: Long, format: String, width: Int, height: Int, bpp: Float): Long {
        if (bpp <= 0f || width * height <= 0) return 0
        val pixels = width.toLong() * height
        // Same downscale target as suggestedImageTarget: >12MP sources get scaled to the megapixel
        // threshold before re-encoding, so the estimate must price the target pixel count, not the
        // source's - otherwise oversized images understate their real savings.
        val targetPixels = minOf(pixels, thresholds.maxMegapixels * 1_000_000L)
        val targetBpp = when (format) {
            "bmp", "dib" -> 0.25f
            "png" -> if (bpp > 1.0f) 0.2f else (bpp * 0.7f)
            "tiff", "tif" -> 0.25f
            "jpeg", "jpg" -> if (bpp > 0.4f) 0.2f else bpp
            else -> bpp
        }
        if (targetBpp >= bpp && targetPixels >= pixels) return 0
        return (size - (targetPixels * targetBpp).toLong()).coerceAtLeast(0)
    }

    private fun readVideoMeta(path: String, context: Context? = null): VideoMeta {
        val r = android.media.MediaMetadataRetriever()
        return try {
            if (path.startsWith("content://")) {
                r.setDataSource(context, Uri.parse(path))
            } else {
                r.setDataSource(path)
            }
            VideoMeta(
                width = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0,
                height = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0,
                durationMs = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0,
                codec = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_MIMETYPE),
            )
        } catch (_: Exception) { VideoMeta() } finally { r.release() }
    }

    private fun readImageDimensions(path: String, context: Context? = null): Pair<Int, Int> {
        val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        if (path.startsWith("content://")) {
            context?.contentResolver?.openInputStream(Uri.parse(path))?.use { stream ->
                android.graphics.BitmapFactory.decodeStream(stream, null, opts)
            }
        } else {
            android.graphics.BitmapFactory.decodeFile(path, opts)
        }
        return opts.outWidth to opts.outHeight
    }

    private data class VideoMeta(val width: Int = 0, val height: Int = 0, val durationMs: Long = 0, val codec: String? = null)

    private fun formatKbps(kbps: Long) = when { kbps >= 1000 -> "${"%.1f".format(kbps / 1000.0)} Mbps"; else -> "$kbps Kbps" }

    val VIDEO_EXTS = setOf("mp4", "mkv", "mov", "3gp", "avi", "wmv", "flv", "webm", "m4v", "mpg", "mpeg")
    val IMAGE_EXTS = setOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "tiff", "tif", "dib")
}

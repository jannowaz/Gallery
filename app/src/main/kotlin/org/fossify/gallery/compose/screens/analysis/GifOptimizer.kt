package org.fossify.gallery.compose.screens.analysis

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Movie
import android.graphics.drawable.AnimatedImageDrawable
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaMetadataRetriever
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import java.io.File
import java.nio.ByteBuffer

/**
 * Re-encodes a GIF into something far smaller without much visible loss:
 *  - a **static** (single-frame) GIF → WebP (lossless where the platform supports it), staying an image;
 *  - an **animated** GIF → H.264 MP4, since GIF has no inter-frame compression and stores dozens of
 *    near-full 256-colour frames (a few seconds is often 5-20 MB where the MP4 is a few hundred KB).
 *
 * The animated path decodes frames with [Movie] (works from API 26, purpose-built for GIF) and feeds
 * them to a [MediaCodec] AVC encoder via a queried YUV colour format - self-contained, no native
 * dependency. On any decode/encode failure it throws rather than silently flattening the animation to
 * a single frame; the caller then leaves the original untouched.
 */
object GifOptimizer {

    /** True if [path] is an animated GIF (more than one frame). Parses the GIF block structure for a
     * definitive frame count first (the reliable signal - an animated source must never be mistaken
     * for static and flattened); only if that can't be determined does it fall back to the platform
     * decoders. A false negative here would silently drop the animation, so anything uncertain that a
     * decoder flags as animated is treated as animated. */
    fun isAnimated(context: Context, path: String): Boolean {
        when (GifInspect.frameCount(path)) {
            in 2..Int.MAX_VALUE -> return true   // definitively animated
            1 -> return false                    // definitively static
            // else (-1): couldn't parse - fall through to the decoders
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching {
                val src = ImageDecoder.createSource(File(path))
                return ImageDecoder.decodeDrawable(src) is AnimatedImageDrawable
            }
        }
        return runCatching { Movie.decodeFile(path)?.let { it.duration() > 0 } ?: false }.getOrDefault(false)
    }

    /** Produces the optimized temp file (`.webp` for static, `.mp4` for animated) in [outputDir].
     * Throws on failure so the caller can leave the original in place. */
    fun optimize(context: Context, path: String, outputDir: File): File {
        outputDir.mkdirs()
        return if (isAnimated(context, path)) toMp4(path, outputDir) else toWebp(path, outputDir)
    }

    private fun toWebp(path: String, outputDir: File): File {
        val bmp = BitmapFactory.decodeFile(path) ?: error("GIF decode failed")
        val out = File(outputDir, "gif_${System.nanoTime()}.webp")
        try {
            @Suppress("DEPRECATION")
            val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSLESS else Bitmap.CompressFormat.WEBP
            out.outputStream().use { bmp.compress(format, 100, it) }
        } finally { bmp.recycle() }
        return out
    }

    private fun toMp4(path: String, outputDir: File): File {
        @Suppress("DEPRECATION")
        val movie = Movie.decodeFile(path) ?: error("GIF decode failed")
        val srcW = movie.width().coerceAtLeast(2)
        val srcH = movie.height().coerceAtLeast(2)
        val durationMs = movie.duration().coerceAtLeast(1)
        // Encoders require even dimensions; also cap to a sane bound.
        val w = (srcW and 1.inv()).coerceAtMost(1920)
        val h = (srcH and 1.inv()).coerceAtMost(1920)
        val fps = 15
        val frameCount = ((durationMs * fps) / 1000).toInt().coerceIn(1, fps * 60) // cap at 60s
        // ~0.15 bpp·frame is plenty for GIF-sourced content and still tiny next to the source.
        val bitRate = (w * h * fps * 0.15f).toInt().coerceIn(300_000, 8_000_000)

        val mime = MediaFormat.MIMETYPE_VIDEO_AVC
        val codec = MediaCodec.createEncoderByType(mime)
        val colorFormat = pickColorFormat(codec, mime)

        val format = MediaFormat.createVideoFormat(mime, w, h).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat)
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
        }

        val out = File(outputDir, "gif_${System.nanoTime()}.mp4")
        val muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var trackIndex = -1
        var muxerStarted = false

        val frameBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(frameBmp)
        val scaleX = w.toFloat() / srcW
        val scaleY = h.toFloat() / srcH
        val argb = IntArray(w * h)
        val yuv = ByteArray(w * h * 3 / 2)
        val semiPlanar = colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar

        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            val info = MediaCodec.BufferInfo()

            fun drain(endOfStream: Boolean) {
                while (true) {
                    val outIndex = codec.dequeueOutputBuffer(info, 10_000)
                    when {
                        outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            trackIndex = muxer.addTrack(codec.outputFormat); muxer.start(); muxerStarted = true
                        }
                        outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> { if (!endOfStream) return }
                        outIndex >= 0 -> {
                            val encoded = codec.getOutputBuffer(outIndex)!!
                            if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                            if (info.size > 0 && muxerStarted) {
                                encoded.position(info.offset); encoded.limit(info.offset + info.size)
                                muxer.writeSampleData(trackIndex, encoded, info)
                            }
                            codec.releaseOutputBuffer(outIndex, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                        }
                    }
                    if (outIndex == MediaCodec.INFO_TRY_AGAIN_LATER && endOfStream) { /* keep waiting */ }
                }
            }

            for (i in 0 until frameCount) {
                val timeMs = (i.toLong() * durationMs / frameCount).toInt()
                frameBmp.eraseColor(Color.BLACK)
                canvas.save(); canvas.scale(scaleX, scaleY); movie.setTime(timeMs); movie.draw(canvas, 0f, 0f); canvas.restore()
                frameBmp.getPixels(argb, 0, w, 0, 0, w, h)
                argbToYuv420(argb, w, h, yuv, semiPlanar)

                // Drain output whenever no input buffer is free rather than blocking forever - the
                // encoder can stall on input until its output is consumed.
                var queued = false
                while (!queued) {
                    val inIndex = codec.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val inBuf = codec.getInputBuffer(inIndex)!!
                        inBuf.clear(); inBuf.put(yuv)
                        codec.queueInputBuffer(inIndex, 0, yuv.size, i.toLong() * 1_000_000L / fps, 0)
                        queued = true
                    } else drain(false)
                }
                drain(false)
            }

            var eosQueued = false
            while (!eosQueued) {
                val inIndex = codec.dequeueInputBuffer(10_000)
                if (inIndex >= 0) {
                    codec.queueInputBuffer(inIndex, 0, 0, frameCount.toLong() * 1_000_000L / fps, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    eosQueued = true
                } else drain(false)
            }
            drain(true)
        } finally {
            runCatching { codec.stop() }
            runCatching { codec.release() }
            runCatching { if (muxerStarted) muxer.stop() }
            runCatching { muxer.release() }
            frameBmp.recycle()
        }
        if (!out.exists() || out.length() == 0L) { out.delete(); error("GIF→MP4 produced no output") }
        // Guard against silently flattening an animation: the source was detected as animated, so the
        // result must be a real, playable video. If it has no duration/track, fail instead of keeping
        // a broken or single-frame output - the caller then leaves the original GIF untouched.
        val durationOk = runCatching {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(out.absolutePath)
                (r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) > 0L
            } finally { r.release() }
        }.getOrDefault(false)
        if (!durationOk) { out.delete(); error("GIF animation could not be preserved") }
        return out
    }

    // Prefer Planar (I420): its U-then-V plane layout is unambiguous. SemiPlanar is NV12-vs-NV21
    // ambiguous across encoders (would swap chroma → purple/green tint), so only fall back to it when
    // Planar isn't offered.
    private fun pickColorFormat(codec: MediaCodec, mime: String): Int {
        val supported = codec.codecInfo.getCapabilitiesForType(mime).colorFormats.toSet()
        return when {
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar in supported -> MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible in supported -> MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar in supported -> MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
            else -> MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar
        }
    }

    /** BT.601 ARGB → YUV420. [semiPlanar] true = NV12 (Y plane then interleaved VU... actually UV);
     * false = I420 (Y, then U, then V). */
    private fun argbToYuv420(argb: IntArray, w: Int, h: Int, out: ByteArray, semiPlanar: Boolean) {
        val frameSize = w * h
        var yIndex = 0
        var uvIndex = frameSize
        for (j in 0 until h) {
            for (i in 0 until w) {
                val c = argb[j * w + i]
                val r = (c shr 16) and 0xff
                val g = (c shr 8) and 0xff
                val b = c and 0xff
                val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                out[yIndex++] = y.coerceIn(0, 255).toByte()
                // Subsample chroma at 2x2.
                if (j % 2 == 0 && i % 2 == 0) {
                    val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                    val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                    if (semiPlanar) {
                        out[uvIndex++] = u.coerceIn(0, 255).toByte()
                        out[uvIndex++] = v.coerceIn(0, 255).toByte()
                    } else {
                        // I420: all U then all V.
                        val uPlane = frameSize + (j / 2) * (w / 2) + (i / 2)
                        val vPlane = frameSize + frameSize / 4 + (j / 2) * (w / 2) + (i / 2)
                        out[uPlane] = u.coerceIn(0, 255).toByte()
                        out[vPlane] = v.coerceIn(0, 255).toByte()
                    }
                }
            }
        }
    }
}

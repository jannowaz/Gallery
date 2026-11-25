package org.fossify.gallery.compose.screens.viewer

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.media.MediaScannerConnection
import android.os.Build
import android.util.Log
import android.util.Rational
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.fossify.gallery.helpers.RefreshBus
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer

private const val SCRUB_FRAME_COUNT = 24
private const val SCRUB_FRAME_WIDTH = 256
private const val SCRUB_FRAME_HEIGHT = 144
private const val SCRUB_FRAME_BUDGET_BYTES = 8 * 1024 * 1024
private const val ROTATION_90 = 90
private const val ROTATION_270 = 270
private const val PIP_MIN_ASPECT = 0.42f
private const val PIP_MAX_ASPECT = 2.39f
private const val PIP_ASPECT_SCALE = 1000
private const val MICROS_PER_MILLI = 1000L
private const val DEFAULT_TRIM_BUFFER_BYTES = 1 shl 20
private const val MAX_TRIM_BUFFER_BYTES = 16 shl 20
private const val TRIM_SUFFIX_START = 2
private const val DEFAULT_PIP_W = 16
private const val DEFAULT_PIP_H = 9

internal data class VideoDimensionsInfo(
    val aspect: Float,
    val pipAspect: Rational,
)

internal fun readVideoDimensionsInfo(
    retriever: MediaMetadataRetriever,
    path: String,
): VideoDimensionsInfo? {
    retriever.setDataSource(path)
    val w = retriever
        .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
        ?.toFloatOrNull() ?: 0f
    val h = retriever
        .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
        ?.toFloatOrNull() ?: 0f
    if (w <= 0f || h <= 0f) return null
    val rotation = retriever
        .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
        ?.toIntOrNull() ?: 0
    val aspect = if (rotation == ROTATION_90 || rotation == ROTATION_270) h / w else w / h
    val clamped = aspect.coerceIn(PIP_MIN_ASPECT, PIP_MAX_ASPECT)
    val pipAspect = Rational((clamped * PIP_ASPECT_SCALE).toInt(), PIP_ASPECT_SCALE)
    return VideoDimensionsInfo(aspect = aspect, pipAspect = pipAspect)
}

/** API 26 has no scaled retriever API. Decode one frame, immediately scale it, and release the
 * decoder-sized bitmap before retaining anything in the scrub cache. */
internal fun decodeScrubFrame(retriever: MediaMetadataRetriever, timeUs: Long): Bitmap? {
    val decoded = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            retriever.getScaledFrameAtTime(
                timeUs,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                SCRUB_FRAME_WIDTH,
                SCRUB_FRAME_HEIGHT,
            )
        } else {
            retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        }
    }.getOrNull() ?: return null

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) return decoded
    val scale = minOf(
        SCRUB_FRAME_WIDTH.toFloat() / decoded.width.coerceAtLeast(1),
        SCRUB_FRAME_HEIGHT.toFloat() / decoded.height.coerceAtLeast(1),
        1f,
    )
    val width = (decoded.width * scale).toInt().coerceAtLeast(1)
    val height = (decoded.height * scale).toInt().coerceAtLeast(1)
    if (width == decoded.width && height == decoded.height) return decoded
    return try {
        Bitmap.createScaledBitmap(decoded, width, height, true)
    } finally {
        if (!decoded.isRecycled) decoded.recycle()
    }
}

internal suspend fun extractVideoScrubFrames(
    retriever: MediaMetadataRetriever,
    frameMutex: Mutex,
): List<Bitmap> = withContext(Dispatchers.IO) {
    frameMutex.withLock {
        val durationMs = runCatching {
            retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
        }.getOrDefault(0L)
        if (durationMs <= 0L) return@withLock emptyList()
        val out = ArrayList<Bitmap>(SCRUB_FRAME_COUNT)
        var retainedBytes = 0
        try {
            for (i in 0 until SCRUB_FRAME_COUNT) {
                coroutineContext.ensureActive()
                val timeMs = durationMs * i / (SCRUB_FRAME_COUNT - 1)
                val bmp = decodeScrubFrame(retriever, timeMs * MICROS_PER_MILLI)
                coroutineContext.ensureActive()
                if (bmp == null) continue
                val bytes = bmp.allocationByteCount
                if (retainedBytes + bytes <= SCRUB_FRAME_BUDGET_BYTES) {
                    out.add(bmp)
                    retainedBytes += bytes
                } else if (!bmp.isRecycled) {
                    bmp.recycle()
                }
            }
            out
        } catch (e: CancellationException) {
            releaseScrubFrames(out)
            throw e
        }
    }
}

internal fun releaseScrubFrames(frames: Iterable<Bitmap>) {
    frames.forEach { bitmap -> if (!bitmap.isRecycled) bitmap.recycle() }
}

internal suspend fun exportTrimmedMp4(
    ctx: Context,
    path: String,
    startUs: Long,
    endUs: Long,
): Result<File> = withContext(Dispatchers.IO) {
    var extractor: MediaExtractor? = null
    var muxer: MediaMuxer? = null
    var muxerStarted = false
    var outFile: File? = null
    var succeeded = false
    try {
        check(endUs > startUs) { "Invalid trim range" }
        val candidate = nextUniqueTrimmedFile(File(path))
        outFile = candidate
        val ext = MediaExtractor().also { extractor = it; it.setDataSource(path) }
        val mux = MediaMuxer(candidate.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            .also { muxer = it }
        val trackPlan = selectMuxableTracks(ext, mux)
        check(trackPlan.addedTracks > 0) { "Unsupported video/audio tracks for MP4 trimming" }
        mux.start()
        muxerStarted = true
        val samplesWritten = copyTrimSamples(ext, mux, trackPlan, startUs, endUs)
        check(samplesWritten > 0) { "No media samples found in selected range" }
        mux.stop()
        muxerStarted = false
        succeeded = true
        runCatching {
            MediaScannerConnection.scanFile(ctx, arrayOf(candidate.absolutePath), null, null)
        }
        RefreshBus.trigger()
        Result.success(candidate)
    } catch (e: IOException) {
        Result.failure(e)
    } catch (e: IllegalStateException) {
        Result.failure(e)
    } catch (e: IllegalArgumentException) {
        Result.failure(e)
    } catch (e: SecurityException) {
        Result.failure(e)
    } finally {
        if (muxerStarted) runCatching { muxer?.stop() }
        runCatching { muxer?.release() }
        runCatching { extractor?.release() }
        if (!succeeded) runCatching { outFile?.delete() }
    }
}

private class MuxTrackPlan(
    val indexMap: IntArray,
    val bufferCapacity: Int,
    val addedTracks: Int,
)

private fun selectMuxableTracks(extractor: MediaExtractor, muxer: MediaMuxer): MuxTrackPlan {
    val trackCount = extractor.trackCount
    val indexMap = IntArray(trackCount) { -1 }
    var bufCap = DEFAULT_TRIM_BUFFER_BYTES
    var addedTracks = 0
    for (i in 0 until trackCount) {
        val fmt = extractor.getTrackFormat(i)
        val mime = fmt.getString(MediaFormat.KEY_MIME).orEmpty()
        val isSupportedTrack = mime.startsWith("video/") || mime.startsWith("audio/")
        val addedIndex = if (isSupportedTrack) runCatching { muxer.addTrack(fmt) }.getOrNull() else null
        if (addedIndex != null) {
            indexMap[i] = addedIndex
            extractor.selectTrack(i)
            addedTracks++
            if (fmt.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                bufCap = maxOf(bufCap, fmt.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE))
            }
        }
    }
    return MuxTrackPlan(
        indexMap = indexMap,
        bufferCapacity = bufCap.coerceAtMost(MAX_TRIM_BUFFER_BYTES),
        addedTracks = addedTracks,
    )
}

private fun copyTrimSamples(
    extractor: MediaExtractor,
    muxer: MediaMuxer,
    trackPlan: MuxTrackPlan,
    startUs: Long,
    endUs: Long,
): Int {
    extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
    val buffer = ByteBuffer.allocate(trackPlan.bufferCapacity)
    val info = MediaCodec.BufferInfo()
    var baseTime = -1L
    var samplesWritten = 0
    var sampleSize = extractor.readSampleData(buffer, 0)
    while (sampleSize >= 0 && extractor.sampleTime <= endUs) {
        val sampleTime = extractor.sampleTime
        val trackIdx = extractor.sampleTrackIndex
        val dstTrack = trackPlan.indexMap.getOrElse(trackIdx) { -1 }
        if (dstTrack >= 0) {
            if (baseTime < 0L) baseTime = sampleTime
            info.offset = 0
            info.size = sampleSize
            info.presentationTimeUs = (sampleTime - baseTime).coerceAtLeast(0L)
            val isSync = (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0
            info.flags = if (isSync) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
            muxer.writeSampleData(dstTrack, buffer, info)
            samplesWritten++
        }
        extractor.advance()
        sampleSize = extractor.readSampleData(buffer, 0)
    }
    return samplesWritten
}

internal fun nextUniqueTrimmedFile(sourceFile: File): File {
    val parentDir = sourceFile.parentFile
    val baseName = sourceFile.nameWithoutExtension
    var candidate = File(parentDir, "${baseName}_trimmed.mp4")
    var suffix = TRIM_SUFFIX_START
    while (candidate.exists()) {
        candidate = File(parentDir, "${baseName}_trimmed_${suffix++}.mp4")
    }
    return candidate
}

internal fun updateAutoPipParams(activity: Activity?, activeAspect: Rational?) {
    if (activity == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    try {
        val params = PictureInPictureParams.Builder()
            .setAutoEnterEnabled(activeAspect != null)
            .apply { activeAspect?.let { setAspectRatio(it) } }
            .build()
        activity.setPictureInPictureParams(params)
    } catch (e: IllegalStateException) {
        Log.e("VideoPage", "setPictureInPictureParams failed", e)
    } catch (e: IllegalArgumentException) {
        Log.e("VideoPage", "setPictureInPictureParams failed", e)
    }
}

internal fun enterVideoPip(
    activity: Activity?,
    pipAspect: Rational?,
    player: ExoPlayer,
    isPlaying: Boolean,
) {
    if (activity == null) return
    val ratio = pipAspect ?: Rational(DEFAULT_PIP_W, DEFAULT_PIP_H)
    try {
        activity.enterPictureInPictureMode(
            PictureInPictureParams.Builder().setAspectRatio(ratio).build(),
        )
    } catch (e: IllegalStateException) {
        Log.e("VideoPage", "enterPictureInPictureMode failed", e)
    } catch (e: IllegalArgumentException) {
        Log.e("VideoPage", "enterPictureInPictureMode failed", e)
    }
    if (!isPlaying) {
        if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
        player.play()
    }
}

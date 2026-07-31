package org.fossify.gallery.helpers

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Extracts still frames from a video for the "grab frames" screen: samples candidate frames across
 * the clip, scores each for sharpness (so blurry ones can be de-emphasised and the crisp ones
 * pre-selected), drops near-duplicates, and saves the chosen ones at full resolution.
 *
 * Sampling/scoring runs on downscaled previews (cheap); only the frames the user actually keeps are
 * re-decoded at native resolution. A single MediaMetadataRetriever is reused per pass and always
 * released in a finally.
 */
object VideoFrameExtractor {

    data class FrameCandidate(val timeMs: Long, val preview: Bitmap, val sharpness: Double)

    data class SaveResult(val savedPaths: List<String>, val failed: Int)

    private const val PREVIEW_W = 320
    private const val PREVIEW_H = 180

    /**
     * Samples up to [count] frames evenly across the video, each scored by sharpness. Adjacent
     * near-identical frames (static scene) are skipped so the picker isn't flooded with duplicates.
     * Returns previews in timeline order; the caller ranks by [FrameCandidate.sharpness].
     */
    /**
     * How many frames to sample for a clip of [durationMs]. Roughly one every ~400 ms, floored at 12
     * (so even a very short clip gives a real choice) and capped at [max] (so a long clip doesn't
     * decode hundreds of frames for a picker). Frame decode is the whole cost here, so fewer samples
     * = proportionally faster; a fixed 36 was hugely over-sampled for short videos.
     *
     * Measured (emulator, 4K 6 s clip): parallelising the decode across 4 workers only shaved ~16 %
     * (1 worker 7.8 s → 4 workers 6.6 s - the decoder serialises, and it costs 4x the decode memory),
     * so the win comes from sampling fewer frames, not from more threads.
     */
    private fun sampleCount(durationMs: Long, max: Int): Int =
        (durationMs / 400L).toInt().coerceIn(12, max)

    suspend fun sampleCandidates(path: String, maxCount: Int = 36, onProgress: (Int, Int) -> Unit): List<FrameCandidate> =
        withContext(Dispatchers.IO) {
            val started = System.currentTimeMillis()
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(path)
                val dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: return@withContext emptyList()
                if (dur <= 0L) return@withContext emptyList()
                val steps = sampleCount(dur, maxCount)
                val out = ArrayList<FrameCandidate>(steps)
                var lastHash = 0L
                var haveLast = false
                for (i in 0 until steps) {
                    val t = dur * i / (steps - 1)
                    val bmp = try {
                        if (Build.VERSION.SDK_INT >= 27) r.getScaledFrameAtTime(t * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, PREVIEW_W, PREVIEW_H)
                        else r.getFrameAtTime(t * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    } catch (_: Exception) { null }
                    onProgress(i + 1, steps)
                    if (bmp == null) continue
                    val hash = aHash(bmp)
                    // Skip a frame that's almost identical to the previous kept one (static footage).
                    if (haveLast && hamming(hash, lastHash) < 5) { bmp.recycle(); continue }
                    lastHash = hash; haveLast = true
                    out.add(FrameCandidate(t, bmp, sharpness(bmp)))
                }
                android.util.Log.d("FrameExtractor", "sampled ${out.size}/$steps frames in ${System.currentTimeMillis() - started}ms")
                out
            } catch (_: Exception) {
                emptyList()
            } finally {
                runCatching { r.release() }
            }
        }

    /**
     * Re-decodes each of [times] (ms) at native resolution and writes it as a JPEG next to the video.
     * Scans the results into MediaStore so they appear in the gallery immediately.
     */
    suspend fun saveFrames(context: Context, videoPath: String, times: List<Long>, onProgress: (Int, Int) -> Unit): SaveResult =
        withContext(Dispatchers.IO) {
            if (times.isEmpty()) return@withContext SaveResult(emptyList(), 0)
            val r = MediaMetadataRetriever()
            val parent = File(videoPath).parentFile ?: context.cacheDir
            val base = File(videoPath).nameWithoutExtension
            val saved = ArrayList<String>(times.size)
            var failed = 0
            try {
                r.setDataSource(videoPath)
                times.forEachIndexed { i, t ->
                    onProgress(i + 1, times.size)
                    val bmp = try { r.getFrameAtTime(t * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) } catch (_: Exception) { null }
                    if (bmp == null) { failed++; return@forEachIndexed }
                    val secs = t / 1000
                    val out = File(parent, "${base}_frame_%02dm%02ds_%02d.jpg".format(secs / 60, secs % 60, i))
                    try {
                        out.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
                        saved.add(out.absolutePath)
                    } catch (_: Exception) { failed++ } finally { bmp.recycle() }
                }
            } catch (_: Exception) {
                // setDataSource failed - every requested frame counts as failed.
                failed += times.size - saved.size
            } finally {
                runCatching { r.release() }
            }
            if (saved.isNotEmpty()) runCatching { MediaScannerConnection.scanFile(context, saved.toTypedArray(), null, null) }
            SaveResult(saved, failed)
        }

    /** Variance of the Laplacian on a small grayscale copy - the standard blur measure (higher = sharper). */
    private fun sharpness(bmp: Bitmap): Double {
        val w = 160
        val h = (bmp.height * w / bmp.width.coerceAtLeast(1)).coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(bmp, w, h, true)
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        small.recycle()
        val gray = DoubleArray(w * h) { val c = px[it]; 0.299 * (c shr 16 and 0xff) + 0.587 * (c shr 8 and 0xff) + 0.114 * (c and 0xff) }
        var sum = 0.0; var sumSq = 0.0; var n = 0
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val idx = y * w + x
            val lap = gray[idx - 1] + gray[idx + 1] + gray[idx - w] + gray[idx + w] - 4 * gray[idx]
            sum += lap; sumSq += lap * lap; n++
        }
        if (n == 0) return 0.0
        val mean = sum / n
        return (sumSq / n - mean * mean).coerceAtLeast(0.0)
    }

    /** 8x8 average hash for cheap near-duplicate detection between consecutive samples. */
    private fun aHash(bmp: Bitmap): Long {
        val small = Bitmap.createScaledBitmap(bmp, 8, 8, true)
        val px = IntArray(64)
        small.getPixels(px, 0, 8, 0, 0, 8, 8)
        small.recycle()
        val g = IntArray(64) { val c = px[it]; (c shr 16 and 0xff) + (c shr 8 and 0xff) + (c and 0xff) }
        val avg = g.average()
        var hash = 0L
        for (i in 0 until 64) if (g[i] >= avg) hash = hash or (1L shl i)
        return hash
    }

    private fun hamming(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)
}

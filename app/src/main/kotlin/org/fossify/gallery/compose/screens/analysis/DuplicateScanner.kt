package org.fossify.gallery.compose.screens.analysis

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.fossify.gallery.helpers.XmpWriter
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.security.MessageDigest

data class DuplicateFile(
    val path: String,
    val name: String,
    val size: Long,
    val modified: Long,
    val mediaType: Int,
    val width: Int = 0,
    val height: Int = 0,
    val durationMs: Long = 0,
    val rating: Int = 0,
    val tags: List<String> = emptyList(),
)

data class DuplicateGroup(
    val hash: String,
    val files: List<DuplicateFile>,
) {
    val size: Long get() = files.maxOfOrNull { it.size } ?: 0L
    val wastedBytes: Long get() = (files.sumOf { it.size } - size).coerceAtLeast(0)
}

sealed class DuplicateProgress {
    data class Collecting(val found: Int) : DuplicateProgress()
    data class Hashing(val percent: Int, val hashed: Int, val total: Int) : DuplicateProgress()
    data class Done(val groups: List<DuplicateGroup>, val totalScanned: Int) : DuplicateProgress()
}

class DuplicateScanner(private val context: Context) {

    fun scanFolder(rootPath: String): Flow<DuplicateProgress> = flow {
        val files = mutableListOf<File>()
        collectMediaFiles(rootPath, files)
        emit(DuplicateProgress.Collecting(files.size))
        val totalScanned = files.size
        if (totalScanned == 0) {
            emit(DuplicateProgress.Done(emptyList(), 0))
            return@flow
        }

        val bySize = files.groupBy { it.length() }.filter { it.value.size > 1 }
        val totalCandidates = bySize.values.sumOf { it.size }

        val byHash = HashMap<String, MutableList<File>>()
        var hashed = 0
        for ((size, group) in bySize) {
            // Pre-filter with a cheap partial hash (first 64 KB) so we only fully hash real collisions
            val byPartial = HashMap<String, MutableList<File>>()
            for (file in group) {
                val ph = partialHash(file)
                if (ph != null) byPartial.getOrPut(ph) { mutableListOf() }.add(file)
                hashed++
                val percent = if (totalCandidates > 0) (hashed * 100) / totalCandidates else 100
                emit(DuplicateProgress.Hashing(percent, hashed, totalCandidates))
            }
            for ((_, sub) in byPartial) {
                if (sub.size < 2) continue
                for (file in sub) {
                    val full = hashFile(file)
                    if (full != null) byHash.getOrPut("${size}_$full") { mutableListOf() }.add(file)
                }
            }
        }

        val groups = byHash.values
            .filter { it.size > 1 }
            .map { dupFiles ->
                DuplicateGroup(
                    hash = "${dupFiles.first().length()}",
                    files = dupFiles.map { readMetadata(it) }.sortedByDescending { it.modified },
                )
            }
            .sortedByDescending { it.wastedBytes }

        emit(DuplicateProgress.Done(groups, totalScanned))
    }.flowOn(Dispatchers.IO)

    fun scanFolderSimilar(rootPath: String, threshold: Int = 10): Flow<DuplicateProgress> = flow {
        val files = mutableListOf<File>()
        collectMediaFiles(rootPath, files, imagesOnly = true)
        emit(DuplicateProgress.Collecting(files.size))
        val total = files.size
        if (total == 0) {
            emit(DuplicateProgress.Done(emptyList(), 0))
            return@flow
        }

        val hashes = LongArray(total)
        val valid = BooleanArray(total)
        for (i in 0 until total) {
            val h = perceptualHash(files[i])
            if (h != null) { hashes[i] = h; valid[i] = true }
            val percent = ((i + 1) * 100) / total
            emit(DuplicateProgress.Hashing(percent, i + 1, total))
        }

        val parent = IntArray(total) { it }
        fun find(x: Int): Int {
            var r = x
            while (parent[r] != r) { parent[r] = parent[parent[r]]; r = parent[r] }
            return r
        }
        for (i in 0 until total) {
            if (!valid[i]) continue
            for (j in i + 1 until total) {
                if (!valid[j]) continue
                if (java.lang.Long.bitCount(hashes[i] xor hashes[j]) <= threshold) {
                    val ri = find(i); val rj = find(j)
                    if (ri != rj) parent[ri] = rj
                }
            }
        }

        val clusters = HashMap<Int, MutableList<Int>>()
        for (i in 0 until total) {
            if (!valid[i]) continue
            clusters.getOrPut(find(i)) { mutableListOf() }.add(i)
        }

        val groups = clusters.values
            .filter { it.size > 1 }
            .map { indices ->
                DuplicateGroup(
                    hash = "sim_${indices.first()}",
                    files = indices.map { readMetadata(files[it]) }.sortedByDescending { it.modified },
                )
            }
            .sortedByDescending { it.wastedBytes }

        emit(DuplicateProgress.Done(groups, total))
    }.flowOn(Dispatchers.IO)

    private fun perceptualHash(file: File): Long? {
        return try {
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val target = 32
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= target && bounds.outHeight / (sample * 2) >= target) sample *= 2
            val decodeOpts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = android.graphics.BitmapFactory.decodeFile(file.absolutePath, decodeOpts) ?: return null
            val scaled = android.graphics.Bitmap.createScaledBitmap(bmp, 9, 8, true)
            if (scaled != bmp) bmp.recycle()
            var hash = 0L
            var bit = 0
            for (y in 0 until 8) {
                for (x in 0 until 8) {
                    val left = grayValue(scaled.getPixel(x, y))
                    val right = grayValue(scaled.getPixel(x + 1, y))
                    if (left > right) hash = hash or (1L shl bit)
                    bit++
                }
            }
            scaled.recycle()
            hash
        } catch (_: Exception) { null }
    }

    private fun grayValue(pixel: Int): Int {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        return (r * 299 + g * 587 + b * 114) / 1000
    }

    private fun collectMediaFiles(rootPath: String, result: MutableList<File>, imagesOnly: Boolean = false) {
        try {
            Files.newDirectoryStream(Paths.get(rootPath)).use { stream ->
                for (entry in stream) {
                    val name = entry.fileName.toString()
                    if (name.startsWith(".")) continue
                    if (Files.isDirectory(entry)) {
                        collectMediaFiles(entry.toString(), result, imagesOnly)
                    } else {
                        val ext = name.substringAfterLast('.', "").lowercase()
                        val match = if (imagesOnly) ext in AnalysisCriteria.IMAGE_EXTS
                            else ext in AnalysisCriteria.VIDEO_EXTS || ext in AnalysisCriteria.IMAGE_EXTS
                        if (match) result.add(entry.toFile())
                    }
                }
            }
        } catch (_: Exception) { }
    }

    private fun partialHash(file: File): String? {
        return try {
            val digest = MessageDigest.getInstance("MD5")
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                val read = input.read(buffer)
                if (read > 0) digest.update(buffer, 0, read)
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) { null }
    }

    private fun hashFile(file: File): String? {
        return try {
            val digest = MessageDigest.getInstance("MD5")
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                var read = input.read(buffer)
                while (read > 0) {
                    digest.update(buffer, 0, read)
                    read = input.read(buffer)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) { null }
    }

    private fun readMetadata(file: File): DuplicateFile {
        val path = file.absolutePath
        val name = file.name
        val ext = name.substringAfterLast('.', "").lowercase()
        val isVideo = ext in AnalysisCriteria.VIDEO_EXTS
        var width = 0
        var height = 0
        var durationMs = 0L
        try {
            if (isVideo) {
                val r = android.media.MediaMetadataRetriever()
                try {
                    r.setDataSource(path)
                    width = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                    height = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                    durationMs = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                } finally { r.release() }
            } else {
                val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFile(path, opts)
                width = opts.outWidth
                height = opts.outHeight
            }
        } catch (_: Exception) { }

        var rating = 0
        var tags = emptyList<String>()
        try {
            val xmp = XmpWriter.read(path)
            rating = xmp.rating
            tags = xmp.tags
        } catch (_: Exception) { }

        return DuplicateFile(
            path = path,
            name = name,
            size = file.length(),
            modified = file.lastModified(),
            mediaType = if (isVideo) 2 else 1,
            width = width,
            height = height,
            durationMs = durationMs,
            rating = rating,
            tags = tags,
        )
    }
}

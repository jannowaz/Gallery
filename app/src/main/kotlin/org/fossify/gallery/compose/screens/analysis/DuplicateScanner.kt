package org.fossify.gallery.compose.screens.analysis

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.fossify.gallery.helpers.XmpWriter
import java.io.File
import java.security.MessageDigest
import org.fossify.gallery.extensions.favoritesDB
import org.fossify.gallery.extensions.fileHashDB
import org.fossify.gallery.extensions.mediaDB

@kotlinx.serialization.Serializable
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
    val isFavorite: Boolean = false,
)

@kotlinx.serialization.Serializable
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

    /** All favorited paths, loaded once per scanner instance - used to flag [DuplicateFile.isFavorite]
     * so an auto-select can keep the starred copy and the delete confirm can warn about favorites. */
    private val favoritePaths: Set<String> by lazy {
        runCatching { context.favoritesDB.getValidFavoritePaths().toHashSet() }.getOrDefault(hashSetOf())
    }

    /**
     * Persistent per-file hash memo backed by the `file_hashes` table: valid while size+mtime
     * still match on disk. This is what makes repeat scans (and the recent-vs-library mode)
     * battery-cheap - anything hashed once is never hashed again until the file changes.
     */
    private inner class HashCache(files: List<File>) {
        private val cached = HashMap<String, org.fossify.gallery.models.FileHash>()
        private val dirty = HashMap<String, org.fossify.gallery.models.FileHash>()

        init {
            files.map { it.absolutePath }.chunked(900).forEach { chunk ->
                try {
                    context.fileHashDB.getByPaths(chunk).forEach { cached[it.path] = it }
                } catch (e: Exception) { android.util.Log.e("DuplicateScanner", "hash cache load failed", e) }
            }
        }

        private fun validFor(file: File, entry: org.fossify.gallery.models.FileHash?) =
            entry != null && entry.size == file.length() && entry.modified == file.lastModified()

        private fun entryFor(file: File): org.fossify.gallery.models.FileHash? {
            val p = file.absolutePath
            return (dirty[p] ?: cached[p])?.takeIf { validFor(file, it) }
        }

        private fun update(file: File, mutate: (org.fossify.gallery.models.FileHash) -> org.fossify.gallery.models.FileHash) {
            val base = entryFor(file) ?: org.fossify.gallery.models.FileHash(file.absolutePath, file.length(), file.lastModified())
            dirty[file.absolutePath] = mutate(base)
        }

        fun partial(file: File): String? {
            entryFor(file)?.partialHash?.let { return it }
            val h = partialHash(file) ?: return null
            update(file) { it.copy(partialHash = h) }
            return h
        }

        fun full(file: File): String? {
            entryFor(file)?.fullHash?.let { return it }
            val h = hashFile(file) ?: return null
            update(file) { it.copy(fullHash = h) }
            return h
        }

        fun phash(file: File): Long? {
            entryFor(file)?.phash?.let { return it }
            val h = perceptualHash(file) ?: return null
            update(file) { it.copy(phash = h) }
            return h
        }

        fun flush() {
            dirty.values.chunked(900).forEach { chunk ->
                try {
                    context.fileHashDB.upsertAll(chunk)
                } catch (e: Exception) { android.util.Log.e("DuplicateScanner", "hash cache flush failed", e) }
            }
        }
    }

    /** The size-group -> partial-hash -> full-hash pipeline shared by both exact scan modes. */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<DuplicateProgress>.buildExactGroups(files: List<File>, cache: HashCache): List<DuplicateGroup> {
        val bySize = files.groupBy { it.length() }.filter { it.value.size > 1 }
        val totalCandidates = bySize.values.sumOf { it.size }
        val byHash = HashMap<String, MutableList<File>>()
        var hashed = 0
        var lastPercent = -1
        for ((size, group) in bySize) {
            // Pre-filter with a cheap partial hash (first 64 KB) so we only fully hash real collisions
            val byPartial = HashMap<String, MutableList<File>>()
            for (file in group) {
                val ph = cache.partial(file)
                if (ph != null) byPartial.getOrPut(ph) { mutableListOf() }.add(file)
                hashed++
                val percent = if (totalCandidates > 0) (hashed * 100) / totalCandidates else 100
                if (percent != lastPercent) { lastPercent = percent; emit(DuplicateProgress.Hashing(percent, hashed, totalCandidates)) }
            }
            for ((_, sub) in byPartial) {
                if (sub.size < 2) continue
                for (file in sub) {
                    val full = cache.full(file)
                    if (full != null) byHash.getOrPut("${size}_$full") { mutableListOf() }.add(file)
                }
            }
        }
        return byHash.values
            .filter { it.size > 1 }
            .map { dupFiles ->
                DuplicateGroup(
                    hash = "${dupFiles.first().length()}_${dupFiles.first().absolutePath.hashCode()}",
                    files = dupFiles.map { readMetadata(it) }.sortedByDescending { it.modified },
                )
            }
            .sortedByDescending { it.wastedBytes }
    }

    fun scanFolder(rootPath: String): Flow<DuplicateProgress> = flow {
        val files = mutableListOf<File>()
        collectMediaFiles(rootPath, files)
        emit(DuplicateProgress.Collecting(files.size))
        val totalScanned = files.size
        if (totalScanned == 0) {
            emit(DuplicateProgress.Done(emptyList(), 0))
            return@flow
        }
        val cache = HashCache(files)
        val groups = buildExactGroups(files, cache)
        cache.flush()
        emit(DuplicateProgress.Done(groups, totalScanned))
    }.flowOn(Dispatchers.IO)

    /**
     * "New media vs. whole library": duplicates of anything added/modified since [sinceMs],
     * searched across the entire indexed library. Deliberately DB-first for battery: probes and
     * candidates come from the media table (exact duplicates must match in size, so only files
     * sharing a probe's size are ever touched on disk), and the hash cache skips everything
     * already hashed by an earlier scan. Only groups containing at least one recent file are
     * reported - pre-existing duplicate pairs elsewhere are not this mode's question.
     */
    fun scanRecentAgainstLibrary(sinceMs: Long): Flow<DuplicateProgress> = flow {
        val probes = try { context.mediaDB.getRecentLivePathSizes(sinceMs) } catch (e: Exception) {
            android.util.Log.e("DuplicateScanner", "recent probes query failed", e); emptyList()
        }
        emit(DuplicateProgress.Collecting(probes.size))
        if (probes.isEmpty()) {
            emit(DuplicateProgress.Done(emptyList(), 0))
            return@flow
        }
        val probePaths = probes.map { it.path }.toHashSet()
        val candidates = probes.map { it.size }.distinct().chunked(900).flatMap { sizes ->
            try { context.mediaDB.getLivePathSizesBySizes(sizes) } catch (e: Exception) {
                android.util.Log.e("DuplicateScanner", "size candidates query failed", e); emptyList()
            }
        }
        val files = candidates.asSequence().map { File(it.path) }.filter { it.exists() }.toList()
        val cache = HashCache(files)
        val groups = buildExactGroups(files, cache).filter { g -> g.files.any { it.path in probePaths } }
        cache.flush()
        emit(DuplicateProgress.Done(groups, probes.size))
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

        val cache = HashCache(files)
        val hashes = LongArray(total)
        val valid = BooleanArray(total)
        for (i in 0 until total) {
            val h = cache.phash(files[i])
            if (h != null) { hashes[i] = h; valid[i] = true }
            val percent = ((i + 1) * 100) / total
            emit(DuplicateProgress.Hashing(percent, i + 1, total))
        }

        // Anchor-based (not transitive-chain) clustering: each cluster is anchored to one seed
        // image, and only images within [threshold] of that *specific* seed join it - membership
        // never chains through an intermediate image. A prior transitive union-find version (any
        // A~B and B~C merges A+B+C even when A and C aren't themselves similar) let one large,
        // thematically-similar-but-not-actually-duplicate folder collapse into a single cluster
        // spanning most of the folder (1087 files observed on a real device) - which then hung the
        // UI trying to render one card with 1087 rows. Same O(n^2) comparison cost as before.
        val used = BooleanArray(total)
        val clusters = mutableListOf<MutableList<Int>>()
        for (i in 0 until total) {
            if (!valid[i] || used[i]) continue
            val cluster = mutableListOf(i)
            used[i] = true
            for (j in i + 1 until total) {
                if (!valid[j] || used[j]) continue
                if (java.lang.Long.bitCount(hashes[i] xor hashes[j]) <= threshold) {
                    cluster.add(j)
                    used[j] = true
                }
            }
            clusters.add(cluster)
        }

        val groups = clusters
            .filter { it.size > 1 }
            .map { indices ->
                DuplicateGroup(
                    hash = "sim_${indices.first()}",
                    files = indices.map { readMetadata(files[it]) }.sortedByDescending { it.modified },
                )
            }
            .sortedByDescending { it.wastedBytes }

        cache.flush()
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
        // Resolve via MediaStore (filesystem directory listing is blocked under scoped storage).
        for (p in MediaStoreEnumerator.mediaPathsUnder(context, rootPath)) {
            val name = p.substringAfterLast('/')
            if (name.startsWith(".")) continue
            val ext = name.substringAfterLast('.', "").lowercase()
            val match = if (imagesOnly) ext in AnalysisCriteria.IMAGE_EXTS
                else ext in AnalysisCriteria.VIDEO_EXTS || ext in AnalysisCriteria.IMAGE_EXTS
            if (match) result.add(File(p))
        }
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
            isFavorite = path in favoritePaths,
        )
    }
}

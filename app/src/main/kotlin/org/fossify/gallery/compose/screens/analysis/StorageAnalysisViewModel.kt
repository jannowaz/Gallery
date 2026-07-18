package org.fossify.gallery.compose.screens.analysis
import org.fossify.gallery.R

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.gallery.extensions.mediaCacheDB
import org.fossify.gallery.extensions.mediaDB
import org.fossify.gallery.helpers.RefreshBus
import org.fossify.gallery.helpers.XmpWriter
import org.fossify.gallery.models.Medium
import java.io.File

enum class FilterMode { ALL, IMAGES, VIDEOS }
enum class AnalysisSortMode { WASTED, SIZE, NAME }

data class AnalysisState(
    val isScanning: Boolean = false,
    val progress: Int = 0,
    val scannedCount: Int = 0,
    val totalFiles: Int = 0,
    val folderPath: String = "",
    val results: List<AnalysisResult> = emptyList(),
    val filterMode: FilterMode = FilterMode.ALL,
    val sortMode: AnalysisSortMode = AnalysisSortMode.WASTED,
    val selectedPaths: Set<String> = emptySet(),
    val transformResults: List<TransformResult> = emptyList(),
    val isTransforming: Boolean = false,
    /** Set when [results] were restored from the last persisted scan instead of a fresh run. */
    val restoredAt: Long? = null,
)

class StorageAnalysisViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(AnalysisState())
    val state: StateFlow<AnalysisState> = _state.asStateFlow()
    private val analyzer = MediaAnalyzer(app)
    val engine = TransformationEngine(app)
    private var scanJob: kotlinx.coroutines.Job? = null

    init {
        // Restore the last persisted scan so leaving the screen (or the app dying) doesn't cost
        // the user the minutes the scan took - a fresh scan simply replaces it.
        viewModelScope.launch(Dispatchers.IO) {
            val saved = ScanResultStore.loadStorageScan(getApplication()) ?: return@launch
            _state.update {
                if (it.isScanning || it.results.isNotEmpty()) it
                else it.copy(results = saved.results, folderPath = saved.folder, restoredAt = saved.timestamp)
            }
        }
    }

    fun startAnalysis(folderPath: String) {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            _state.update { it.copy(isScanning = true, progress = 0, folderPath = folderPath, results = emptyList(), scannedCount = 0, totalFiles = 0, restoredAt = null) }
            analyzer.analyzeFolder(folderPath).collect { progress ->
                when (progress) {
                    is AnalysisProgress.Scanning -> _state.update { it.copy(progress = progress.percent, scannedCount = progress.scanned, totalFiles = progress.total) }
                    is AnalysisProgress.Found -> _state.update { it.copy(results = progress.allResults) }
                    is AnalysisProgress.Done -> {
                        _state.update { it.copy(isScanning = false, progress = 100, results = progress.results, totalFiles = progress.totalScanned) }
                        withContext(Dispatchers.IO) { ScanResultStore.saveStorageScan(getApplication(), folderPath, progress.results) }
                    }
                }
            }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        _state.update { it.copy(isScanning = false) }
    }

    fun setFilterMode(mode: FilterMode) { _state.update { it.copy(filterMode = mode) } }
    fun setSortMode(mode: AnalysisSortMode) { _state.update { it.copy(sortMode = mode) } }
    fun toggleSelection(path: String) {
        _state.update { s -> s.copy(selectedPaths = if (path in s.selectedPaths) s.selectedPaths - path else s.selectedPaths + path) }
    }
    fun selectAll() { _state.update { s -> s.copy(selectedPaths = s.results.map { it.path }.toSet()) } }
    fun clearSelection() { _state.update { it.copy(selectedPaths = emptySet()) } }

    fun executeTransforms(losslessOnly: Boolean = true) {
        val selected = _state.value.selectedPaths
        if (selected.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(isTransforming = true, transformResults = emptyList()) }
            // Force lossless-only. Lossy re-compression (e.g. JPEG Q85) would irreversibly
            // degrade quality while overwriting the original, so it is never performed.
            val suggestions = engine.suggestTransformations(_state.value.results.filter { it.path in selected }, losslessOnly = true)
            if (suggestions.isEmpty()) { _state.update { it.copy(isTransforming = false) }; return@launch }
            val results = engine.executeBatch(suggestions) { _, _ -> }
            _state.update { it.copy(isTransforming = false, transformResults = results, selectedPaths = emptySet()) }
            startAnalysis(_state.value.folderPath)
        }
    }

    fun clearTransformResults() { _state.update { it.copy(transformResults = emptyList()) } }

    /** Enqueues [org.fossify.gallery.workers.CompressionWorker] for the current selection - unlike
     * [executeTransforms] this never decides anything itself, it only produces temp files for the
     * user to compare/accept in [CompressionReviewScreen]. */
    fun startCompression() {
        val selected = _state.value.selectedPaths
        if (selected.isEmpty()) return
        val toCompress = _state.value.results.filter { it.path in selected }.map { it.path to it.mediaType }
        viewModelScope.launch {
            org.fossify.gallery.workers.CompressionWorker.enqueue(getApplication(), toCompress)
            _state.update { it.copy(selectedPaths = emptySet()) }
        }
    }

    /** Every current result [executeTransforms] could apply losslessly - drives the "Optimize all"
     * CTA, which only makes sense to show/enable when this is non-empty. */
    fun losslessEligiblePaths(): Set<String> =
        engine.suggestTransformations(_state.value.results, losslessOnly = true).map { it.originalPath }.toSet()

    /** One-tap entry point for the recommended "optimize everything first" workflow: lossless
     * conversions can't lose quality (executeTransforms always forces losslessOnly regardless of
     * the flag passed to it), so unlike the manual multi-select path this skips the confirm dialog
     * and just runs on every currently eligible result directly. */
    fun optimizeAll() {
        val eligible = losslessEligiblePaths()
        if (eligible.isEmpty()) return
        _state.update { it.copy(selectedPaths = eligible) }
        executeTransforms()
    }

    /** One-tap entry point for "then compress the rest": selects every current result (same
     * "ignore the filter chip" scope as [selectAll]) and hands it to the existing compression
     * pipeline. Meant to be tapped after [optimizeAll] - by then, the lossless-eligible files have
     * already dropped out of [AnalysisState.results] (executeTransforms re-scans when it finishes),
     * so this naturally only ever compresses what's left. */
    fun compressAll() {
        if (_state.value.results.isEmpty()) return
        _state.update { it.copy(selectedPaths = _state.value.results.map { r -> r.path }.toSet()) }
        startCompression()
    }
}

data class TransformSuggestion(
    val originalPath: String,
    val originalSize: Long,
    val targetFormat: String,
    val estimatedNewSize: Long,
    val savedBytes: Long,
    val isLossless: Boolean,
    val reason: String,
)

data class TransformResult(
    val success: Boolean,
    val originalPath: String,
    val newPath: String,
    val savedBytes: Long,
    val error: String? = null,
)

class TransformationEngine(private val context: android.content.Context) {

    fun suggestTransformations(results: List<AnalysisResult>, losslessOnly: Boolean = true): List<TransformSuggestion> {
        return results.mapNotNull { r ->
            when (r.imageFormat) {
                "bmp", "dib" -> TransformSuggestion(r.path, r.fileSize, "png", r.fileSize / 20, r.fileSize * 19 / 20, true, "BMP → PNG (lossless)")
                "png" -> {
                    if (r.bpp > 1.5f && !losslessOnly) TransformSuggestion(r.path, r.fileSize, "jpeg", r.fileSize / 6, r.fileSize * 5 / 6, false, "PNG → JPEG Q85")
                    else if (r.bpp > 0.8f) TransformSuggestion(r.path, r.fileSize, "webp", r.fileSize * 7 / 10, r.fileSize * 3 / 10, true, "PNG → WebP-lossless")
                    else null
                }
                "tiff", "tif" -> TransformSuggestion(r.path, r.fileSize, "png", r.fileSize / 15, r.fileSize * 14 / 15, true, "TIFF → PNG (lossless)")
                "jpeg", "jpg" -> {
                    if (r.bpp > 0.4f && !losslessOnly) TransformSuggestion(r.path, r.fileSize, "jpeg", r.fileSize * 4 / 10, r.fileSize * 6 / 10, false, "JPEG Rekompression Q85")
                    else null
                }
                else -> null
            }
        }
    }

    suspend fun executeBatch(suggestions: List<TransformSuggestion>, onProgress: (Int, Int) -> Unit): List<TransformResult> = withContext(Dispatchers.IO) {
        suggestions.mapIndexed { i, s -> onProgress(i + 1, suggestions.size); execute(s) }
    }

    suspend fun execute(s: TransformSuggestion): TransformResult = withContext(Dispatchers.IO) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(s.originalPath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext TransformResult(false, s.originalPath, "", 0, "Decode failed")
            // Never silently downsample (that would break the lossless promise); skip images too large to decode safely.
            if (bounds.outWidth.toLong() * bounds.outHeight > 24_000_000L) return@withContext TransformResult(false, s.originalPath, "", 0, context.getString(R.string.opt_err_image_too_large))

            val srcData = try { XmpWriter.read(s.originalPath) } catch (_: Exception) { null }
            val tmpFile = File(context.cacheDir, "transform_${System.nanoTime()}.tmp")
            val bitmap = BitmapFactory.decodeFile(s.originalPath) ?: return@withContext TransformResult(false, s.originalPath, "", 0, "Decode failed")
            val (format, quality) = when (s.targetFormat) {
                "png" -> Bitmap.CompressFormat.PNG to 100
                "jpeg" -> Bitmap.CompressFormat.JPEG to 85
                "webp" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSLESS to 100 else Bitmap.CompressFormat.PNG to 100
                else -> Bitmap.CompressFormat.JPEG to 85
            }
            tmpFile.outputStream().use { bitmap.compress(format, quality, it) }
            bitmap.recycle()
            if (!tmpFile.exists() || tmpFile.length() == 0L) { tmpFile.delete(); return@withContext TransformResult(false, s.originalPath, "", 0, "Encode failed") }
            if (tmpFile.length() >= s.originalSize) { tmpFile.delete(); return@withContext TransformResult(false, s.originalPath, "", 0, context.getString(R.string.opt_err_no_savings)) }

            // Preserve EXIF metadata for formats that support it (best-effort)
            if (s.targetFormat == "jpeg" || s.targetFormat == "webp") {
                try { copyExif(s.originalPath, tmpFile.absolutePath) } catch (e: Exception) { android.util.Log.e("StorageAnalysis", "EXIF copy failed for ${s.originalPath}", e) }
            }

            val original = File(s.originalPath)
            val actualExt = when (s.targetFormat) {
                "jpeg" -> "jpg"
                "webp" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) "webp" else "png"
                else -> s.targetFormat
            }
            val newPath = s.originalPath.replaceAfterLast('.', actualExt)
            val finalFile = File(newPath)
            val sameFile = finalFile.absolutePath == original.absolutePath

            val saved: Long
            if (sameFile) {
                // Refuse to overwrite an original in place - that has no undo and risks data loss.
                // Lossless transforms always change the extension, so this only guards the lossy path.
                tmpFile.delete()
                return@withContext TransformResult(false, s.originalPath, "", 0, context.getString(R.string.opt_err_inplace_disabled))
            } else {
                if (finalFile.exists()) { tmpFile.delete(); return@withContext TransformResult(false, s.originalPath, "", 0, context.getString(R.string.opt_err_target_exists)) }
                val moved = tmpFile.renameTo(finalFile) || runCatching { tmpFile.copyTo(finalFile, overwrite = false); tmpFile.delete() }.isSuccess
                if (moved && finalFile.exists() && finalFile.length() > 0) {
                    saved = (s.originalSize - finalFile.length()).coerceAtLeast(0)
                    // Move the original to the recycle bin instead of hard-deleting it (recoverable).
                    softDeleteOriginal(original)
                }
                else { tmpFile.delete(); return@withContext TransformResult(false, s.originalPath, "", 0, "Rename failed") }
            }

            // Carry app tags/rating onto the new file and keep DB/MediaStore consistent
            if (srcData != null && (srcData.tags.isNotEmpty() || srcData.rating > 0)) {
                try { XmpWriter.write(newPath, srcData.tags, srcData.rating) } catch (e: Exception) { android.util.Log.e("StorageAnalysis", "Tag/rating carry-over failed for $newPath", e) }
            }
            if (!sameFile) {
                try { context.mediaCacheDB.deleteByPathSync(s.originalPath) } catch (e: Exception) { android.util.Log.e("StorageAnalysis", "Cache row cleanup failed for ${s.originalPath}", e) }
            }
            try { android.media.MediaScannerConnection.scanFile(context, arrayOf(s.originalPath, newPath), null, null) } catch (_: Exception) { }
            RefreshBus.trigger()
            TransformResult(true, s.originalPath, newPath, saved)
        } catch (e: OutOfMemoryError) {
            TransformResult(false, s.originalPath, "", 0, context.getString(R.string.opt_err_image_too_large))
        } catch (e: Exception) { TransformResult(false, s.originalPath, "", 0, e.message) }
    }

    fun softDeleteOriginal(original: File) {
        try {
            val path = original.absolutePath
            val medium = Medium(
                null, original.name, path, original.parent ?: "", original.lastModified(), original.lastModified(),
                original.length(), if (path.substringAfterLast('.', "").lowercase() in org.fossify.gallery.helpers.VIDEO_EXTENSIONS) 2 else 1,
                0, false, 0L, 0L, 0,
            )
            context.mediaDB.insertAllKeepingExisting(listOf(medium))
            context.mediaDB.softDelete(path, System.currentTimeMillis())
        } catch (e: Exception) { android.util.Log.e("StorageAnalysis", "Recycle-bin registration failed for ${original.absolutePath}", e) }
    }

    private fun copyExif(src: String, dst: String) {
        val tags = listOf(
            ExifInterface.TAG_DATETIME, ExifInterface.TAG_DATETIME_ORIGINAL, ExifInterface.TAG_DATETIME_DIGITIZED,
            ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL, ExifInterface.TAG_ORIENTATION,
            ExifInterface.TAG_F_NUMBER, ExifInterface.TAG_EXPOSURE_TIME, ExifInterface.TAG_FOCAL_LENGTH,
            ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE, ExifInterface.TAG_GPS_LONGITUDE_REF, ExifInterface.TAG_GPS_ALTITUDE, ExifInterface.TAG_GPS_ALTITUDE_REF,
            ExifInterface.TAG_WHITE_BALANCE,
        )
        val from = ExifInterface(src)
        val to = ExifInterface(dst)
        tags.forEach { t -> from.getAttribute(t)?.let { to.setAttribute(t, it) } }
        to.saveAttributes()
    }
}

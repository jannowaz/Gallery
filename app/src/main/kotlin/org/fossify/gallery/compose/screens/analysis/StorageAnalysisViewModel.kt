package org.fossify.gallery.compose.screens.analysis
import org.fossify.gallery.R

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorSpace
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.gallery.extensions.copyNonDimensionAttributesTo
import org.fossify.gallery.extensions.mediaCacheDB
import org.fossify.gallery.extensions.mediaDB
import org.fossify.gallery.helpers.RefreshBus
import org.fossify.gallery.helpers.UndoAction
import org.fossify.gallery.helpers.UndoManager
import org.fossify.gallery.helpers.UndoType
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
    /** (done, total) while a batch optimize is running - null when not transforming. */
    val optimizeProgress: Pair<Int, Int>? = null,
    /** True while a "compress all"/"compress selection" enqueue is in flight - the DB insert
     * itself is fast, but without this the button gives zero feedback for the tap-to-navigate gap. */
    val isEnqueuingCompression: Boolean = false,
    /** Set when [results] were restored from the last persisted scan instead of a fresh run. */
    val restoredAt: Long? = null,
    /** Active results filter: only files in this folder are shown. null = all folders. Lets a
     * whole-storage scan be worked through folder by folder instead of one mixed list. */
    val folderFilter: String? = null,
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
            _state.update { it.copy(isScanning = true, progress = 0, folderPath = folderPath, results = emptyList(), scannedCount = 0, totalFiles = 0, restoredAt = null, folderFilter = null, selectedPaths = emptySet()) }
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

    /** Narrows the visible results to files in [folder]; null shows all. Selection is left
     * untouched so a filter can be flipped through without losing marks. */
    fun setFolderFilter(folder: String?) { _state.update { it.copy(folderFilter = folder) } }

    fun toggleSelection(path: String) {
        _state.update { s -> s.copy(selectedPaths = if (path in s.selectedPaths) s.selectedPaths - path else s.selectedPaths + path) }
    }

    /** Marks [paths] - replacing the current selection ([additive] = false) or adding to it. The
     * caller passes the currently *visible* (filtered + sorted) set, so "mark all shown" is truly
     * what-you-see-is-what-you-get and never selects a file the active filter is hiding. */
    fun selectPaths(paths: Collection<String>, additive: Boolean = false) {
        _state.update { s -> s.copy(selectedPaths = if (additive) s.selectedPaths + paths else paths.toSet()) }
    }

    /** Adds every result living directly in [folder] to the selection. */
    fun selectFolder(folder: String) {
        _state.update { s -> s.copy(selectedPaths = s.selectedPaths + s.results.filter { File(it.path).parent == folder }.map { it.path }) }
    }

    fun clearSelection() { _state.update { it.copy(selectedPaths = emptySet()) } }

    fun executeTransforms(losslessOnly: Boolean = true) {
        val selected = _state.value.selectedPaths
        if (selected.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(isTransforming = true, transformResults = emptyList(), optimizeProgress = null) }
            // Force lossless-only. Lossy re-compression (e.g. JPEG Q85) would irreversibly
            // degrade quality while overwriting the original, so it is never performed.
            val suggestions = engine.suggestTransformations(_state.value.results.filter { it.path in selected }, losslessOnly = true)
            if (suggestions.isEmpty()) { _state.update { it.copy(isTransforming = false) }; return@launch }
            val results = engine.executeBatch(suggestions) { done, total -> _state.update { it.copy(optimizeProgress = done to total) } }
            // One-tap undo for the whole batch: restore every original from the recycle bin and
            // delete the generated copies. The originals are only soft-deleted (recoverable), so this
            // is always safe - it just makes the safety net visible instead of buried in the bin.
            val replaced = results.filter { it.success && it.newPath.isNotEmpty() }
            if (replaced.isNotEmpty()) {
                UndoManager.push(
                    UndoAction(
                        paths = replaced.map { it.originalPath }.toSet(),
                        type = UndoType.OPTIMIZE_REPLACE,
                        extra = replaced.associate { it.originalPath to it.newPath },
                    )
                )
            }
            _state.update { it.copy(isTransforming = false, optimizeProgress = null, transformResults = results, selectedPaths = emptySet()) }
            startAnalysis(_state.value.folderPath)
        }
    }

    fun clearTransformResults() { _state.update { it.copy(transformResults = emptyList()) } }

    /** Enqueues [org.fossify.gallery.workers.CompressionWorker] for the current selection - unlike
     * [executeTransforms] this never decides anything itself, it only produces temp files for the
     * user to compare/accept in [CompressionReviewScreen]. Suspend (not its own viewModelScope.launch)
     * so callers can await the DB insert finishing before navigating to the review screen - without
     * that, navigation could win the race and briefly show "nothing to review" before the pending
     * rows appeared, which read as the tap having done nothing. */
    suspend fun startCompressionAwait() {
        val selected = _state.value.selectedPaths
        if (selected.isEmpty()) return
        _state.update { it.copy(isEnqueuingCompression = true) }
        val toCompress = _state.value.results.filter { it.path in selected }.map { it.path to it.mediaType }
        try {
            org.fossify.gallery.workers.CompressionWorker.enqueue(getApplication(), toCompress)
            _state.update { it.copy(selectedPaths = emptySet()) }
        } finally {
            _state.update { it.copy(isEnqueuingCompression = false) }
        }
    }

    fun startCompression() { viewModelScope.launch { startCompressionAwait() } }

    /** Every current result [executeTransforms] could apply losslessly - drives the "Optimize all"
     * CTA, which only makes sense to show/enable when this is non-empty. */
    fun losslessEligiblePaths(): Set<String> =
        engine.suggestTransformations(_state.value.results, losslessOnly = true).map { it.originalPath }.toSet()

    /** One-tap entry point for the recommended "optimize everything first" workflow: lossless
     * conversions can't lose quality (executeTransforms always forces losslessOnly regardless of
     * the flag passed to it), so unlike the manual multi-select path this skips the confirm dialog
     * and just runs on every currently eligible result directly. */
    fun optimizeAll(scopePaths: Set<String>? = null) {
        val eligible = losslessEligiblePaths().let { if (scopePaths != null) it intersect scopePaths else it }
        if (eligible.isEmpty()) return
        _state.update { it.copy(selectedPaths = eligible) }
        executeTransforms()
    }

    /** One-tap entry point for "then compress the rest": selects every current result (same
     * "ignore the filter chip" scope as [selectAll]) and hands it to the existing compression
     * pipeline. Meant to be tapped after [optimizeAll] - by then, the lossless-eligible files have
     * already dropped out of [AnalysisState.results] (executeTransforms re-scans when it finishes),
     * so this naturally only ever compresses what's left. */
    suspend fun compressAllAwait(scopePaths: Set<String>? = null) {
        val paths = scopePaths ?: _state.value.results.map { r -> r.path }.toSet()
        if (paths.isEmpty()) return
        _state.update { it.copy(selectedPaths = paths) }
        startCompressionAwait()
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
            // Truly-lossless guard: BitmapFactory decodes to 8-bit sRGB, so a >8-bit-per-channel
            // source (RGBA_F16, e.g. 16-bit TIFF/PNG) would be truncated and a wide-gamut/ICC source
            // (Display-P3, Adobe RGB) flattened to sRGB - both silent quality loss. Skip those rather
            // than pretend the conversion was lossless; normal 8-bit sRGB photos are unaffected.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val cs = bounds.outColorSpace
                val notLossless = bounds.outConfig == Bitmap.Config.RGBA_F16 ||
                    (cs != null && cs != ColorSpace.get(ColorSpace.Named.SRGB))
                if (notLossless) return@withContext TransformResult(false, s.originalPath, "", 0, context.getString(R.string.opt_err_color_precision))
            }

            val srcData = try { XmpWriter.read(s.originalPath) } catch (_: Exception) { null }
            // Captured while the original is still live in the DB - used below to give the optimized
            // copy the original's timeline position (date_added/date_taken/mtime) so it isn't shown
            // as brand new.
            val origMedium = runCatching { context.mediaDB.getMediaByPaths(listOf(s.originalPath)).firstOrNull() }.getOrNull()
            val origModified = File(s.originalPath).lastModified()
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

            // Preserve EXIF (capture date, GPS, camera, orientation) onto the new file - best-effort,
            // and now for PNG too (androidx ExifInterface writes PNG/WebP/JPEG). Keeps the photo's own
            // history intact, not just its pixels.
            try {
                ExifInterface(s.originalPath).copyNonDimensionAttributesTo(ExifInterface(tmpFile.absolutePath))
            } catch (e: Exception) { android.util.Log.e("StorageAnalysis", "EXIF copy failed for ${s.originalPath}", e) }

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
                    // Give the new file the original's timeline slot BEFORE the MediaStore scan below
                    // triggers a sync: the sync inserts with insertAllKeepingExisting (IGNORE), so this
                    // pre-registered row (carrying the original's date_added/date_taken) is left intact
                    // and the optimized copy sorts in place instead of jumping to the top as brand new.
                    inheritTimeline(origMedium, finalFile)
                    // Move the original to the recycle bin instead of hard-deleting it (recoverable).
                    softDeleteOriginal(original)
                }
                else { tmpFile.delete(); return@withContext TransformResult(false, s.originalPath, "", 0, "Rename failed") }
            }

            // Carry app tags/rating onto the new file and keep DB/MediaStore consistent
            if (srcData != null && (srcData.tags.isNotEmpty() || srcData.rating > 0)) {
                try { XmpWriter.write(newPath, srcData.tags, srcData.rating) } catch (e: Exception) { android.util.Log.e("StorageAnalysis", "Tag/rating carry-over failed for $newPath", e) }
            }
            // Last, after the XMP write above (which touches the file's mtime): stamp the original's
            // modified time so a modified-date sort keeps the copy in place too.
            if (origModified > 0) runCatching { finalFile.setLastModified(origModified) }
            if (!sameFile) {
                try { context.mediaCacheDB.deleteByPathSync(s.originalPath) } catch (e: Exception) { android.util.Log.e("StorageAnalysis", "Cache row cleanup failed for ${s.originalPath}", e) }
            }
            // Only scan the NEW file. The original was just soft-deleted (DB flag; the file itself
            // stays on disk until the recycle bin is emptied) - re-scanning its still-present path
            // re-registers it in MediaStore, which the incremental sync's reviveSoftDeleted then
            // un-deletes, resurrecting the "removed" original next to its optimized copy.
            try { android.media.MediaScannerConnection.scanFile(context, arrayOf(newPath), null, null) } catch (_: Exception) { }
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

    /**
     * Pre-registers a DB row for [newFile] carrying the original medium's timeline fields
     * (date_added, date_taken, last_modified, rating), so a freshly optimized/compressed copy keeps
     * its place in the date-sorted grid instead of showing up as brand new. Relies on the sync using
     * `insertAllKeepingExisting` (IGNORE): as long as this runs before the MediaStore scan, the sync
     * leaves the row alone. No-op if the original wasn't in the DB. Also usable by CompressionKeeper.
     */
    fun inheritTimeline(origMedium: Medium?, newFile: File) {
        origMedium ?: return
        try {
            val inherited = Medium(
                null, newFile.name, newFile.absolutePath, newFile.parent ?: "",
                origMedium.modified, origMedium.taken, newFile.length(), origMedium.type,
                origMedium.videoDuration, false, 0L, 0L, origMedium.rating, dateAdded = origMedium.dateAdded,
            )
            context.mediaDB.insertAllKeepingExisting(listOf(inherited))
        } catch (e: Exception) { android.util.Log.e("StorageAnalysis", "Timeline inherit failed for ${newFile.absolutePath}", e) }
    }
}

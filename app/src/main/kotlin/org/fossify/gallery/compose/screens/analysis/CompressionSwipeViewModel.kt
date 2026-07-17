package org.fossify.gallery.compose.screens.analysis

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.gallery.R
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.UndoAction
import org.fossify.gallery.helpers.UndoManager
import org.fossify.gallery.helpers.UndoType
import java.io.File

/**
 * Transient handoff for the swipe flow's candidate list - same pattern (and reason) as
 * [org.fossify.gallery.navigation.ViewerArgs]: serialising hundreds of paths into a typed route
 * URL overflows and fails to match.
 */
object CompressionSwipeArgs {
    var results: List<AnalysisResult> = emptyList()
}

sealed interface SwipePhase {
    /** Deciding whether to convert this candidate at all. */
    data class Triage(val item: AnalysisResult) : SwipePhase

    /** Probe compression running - purely temp-file work, nothing touched yet. [progress] is the
     * transcode percentage for videos (null for images, whose re-encode is near-instant). */
    data class Converting(val item: AnalysisResult, val progress: Int? = null) : SwipePhase

    /** Probe result ready: original untouched on disk, compressed copy at [tempPath]. */
    data class Compare(val item: AnalysisResult, val tempPath: String, val newSize: Long) : SwipePhase

    data object Finished : SwipePhase
}

data class SwipeStats(
    val converted: Int = 0,
    val skipped: Int = 0,
    val failed: Int = 0,
    val savedBytes: Long = 0,
)

/**
 * Drives the "swipe review" flow over [CompressionSwipeArgs.results]: triage-swipe decides whether
 * to probe-compress an item (inline via [CompressionEngine], sequential - the user is watching),
 * compare-swipe then decides between original and compressed via the same [CompressionKeeper] the
 * list-based review uses. The original is never touched before the compare decision, and accepting
 * only ever soft-deletes it into the recycle bin.
 */
class CompressionSwipeViewModel(app: Application) : AndroidViewModel(app) {

    private val engine = CompressionEngine(app)
    private val queue: List<AnalysisResult> = CompressionSwipeArgs.results
    val total = queue.size
    private var index = 0

    private val _phase = MutableStateFlow<SwipePhase>(queue.firstOrNull()?.let { SwipePhase.Triage(it) } ?: SwipePhase.Finished)
    val phase: StateFlow<SwipePhase> = _phase.asStateFlow()

    private val _position = MutableStateFlow(1)
    val position: StateFlow<Int> = _position.asStateFlow()

    private val _stats = MutableStateFlow(SwipeStats())
    val stats: StateFlow<SwipeStats> = _stats.asStateFlow()

    /** One-shot error message for the screen to toast; consumed via [clearError]. */
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    fun clearError() { _error.value = null }

    fun skip() {
        if (_phase.value !is SwipePhase.Triage) return
        _stats.update { it.copy(skipped = it.skipped + 1) }
        advance()
    }

    fun convert() {
        val item = (_phase.value as? SwipePhase.Triage)?.item ?: return
        _phase.value = SwipePhase.Converting(item)
        viewModelScope.launch {
            val outFile = try {
                withContext(Dispatchers.IO) {
                    if (item.mediaType == 2) {
                        val (w, h, kbps) = AnalysisCriteria.suggestedVideoTarget(item)
                            ?: error(getApplication<Application>().getString(R.string.swipe_already_optimal))
                        engine.compressVideo(item.path, w, h, kbps) { percent ->
                            val current = _phase.value
                            if (current is SwipePhase.Converting) _phase.value = current.copy(progress = percent)
                        }
                    } else {
                        val (edge, quality) = AnalysisCriteria.suggestedImageTarget(item)
                            ?: error(getApplication<Application>().getString(R.string.swipe_already_optimal))
                        engine.compressImage(item.path, edge, quality)
                    }
                }
            } catch (e: Exception) {
                fail(e.message)
                return@launch
            }
            if (outFile.length() >= item.fileSize) {
                runCatching { outFile.delete() }
                fail(getApplication<Application>().getString(R.string.swipe_no_savings))
            } else {
                _phase.value = SwipePhase.Compare(item, outFile.absolutePath, outFile.length())
            }
        }
    }

    fun keepNew() {
        val compare = _phase.value as? SwipePhase.Compare ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val newPath = CompressionKeeper.keepNew(getApplication(), compare.item.path, compare.tempPath)
            if (newPath != null) {
                UndoManager.push(UndoAction(paths = setOf(compare.item.path), type = UndoType.COMPRESS_REPLACE, extra = mapOf("newPath" to newPath)))
                val saved = (compare.item.fileSize - compare.newSize).coerceAtLeast(0)
                _stats.update { it.copy(converted = it.converted + 1, savedBytes = it.savedBytes + saved) }
                getApplication<Application>().config.totalCompressionSavedBytes += saved
                advance()
            } else {
                runCatching { File(compare.tempPath).delete() }
                fail(getApplication<Application>().getString(R.string.swipe_keep_failed))
            }
        }
    }

    fun keepOriginal() {
        val compare = _phase.value as? SwipePhase.Compare ?: return
        viewModelScope.launch(Dispatchers.IO) { runCatching { File(compare.tempPath).delete() } }
        _stats.update { it.copy(skipped = it.skipped + 1) }
        advance()
    }

    private fun fail(message: String?) {
        _stats.update { it.copy(failed = it.failed + 1) }
        _error.value = message
        advance()
    }

    private fun advance() {
        index++
        _position.value = (index + 1).coerceAtMost(total.coerceAtLeast(1))
        _phase.value = if (index >= queue.size) SwipePhase.Finished else SwipePhase.Triage(queue[index])
    }

    override fun onCleared() {
        // Leaving mid-compare abandons the temp file - delete it now instead of waiting for the
        // 24h orphan sweep in RecycleBinCleanupWorker.
        (_phase.value as? SwipePhase.Compare)?.let { runCatching { File(it.tempPath).delete() } }
    }
}

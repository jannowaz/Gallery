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
import org.fossify.gallery.helpers.MediaRepository
import org.fossify.gallery.helpers.UndoAction
import org.fossify.gallery.helpers.UndoManager
import org.fossify.gallery.helpers.UndoType

data class DuplicateState(
    val isScanning: Boolean = false,
    val phase: String = "",
    val progress: Int = 0,
    val hashedCount: Int = 0,
    val totalCandidates: Int = 0,
    val totalScanned: Int = 0,
    val folderPath: String = "",
    val groups: List<DuplicateGroup> = emptyList(),
    val selectedForDeletion: Set<String> = emptySet(),
    val scanDone: Boolean = false,
    val mode: DuplicateMode = DuplicateMode.EXACT,
    val similarThreshold: Int = 10,
)

enum class DuplicateMode { EXACT, SIMILAR }

class DuplicateFinderViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(DuplicateState())
    val state: StateFlow<DuplicateState> = _state.asStateFlow()
    private val scanner = DuplicateScanner(app)
    private val repo = MediaRepository(app)

    fun setMode(mode: DuplicateMode) {
        _state.update { it.copy(mode = mode, groups = emptyList(), selectedForDeletion = emptySet(), scanDone = false) }
    }

    fun setThreshold(threshold: Int) {
        _state.update { it.copy(similarThreshold = threshold.coerceIn(0, 20)) }
    }

    fun startScan(folderPath: String) {
        val mode = _state.value.mode
        val threshold = _state.value.similarThreshold
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isScanning = true, progress = 0, phase = "Sammeln…", folderPath = folderPath,
                    groups = emptyList(), selectedForDeletion = emptySet(),
                    hashedCount = 0, totalCandidates = 0, totalScanned = 0, scanDone = false,
                )
            }
            val flow = if (mode == DuplicateMode.SIMILAR) scanner.scanFolderSimilar(folderPath, threshold) else scanner.scanFolder(folderPath)
            flow.collect { progress ->
                when (progress) {
                    is DuplicateProgress.Collecting -> _state.update { it.copy(phase = "${progress.found} Dateien gefunden…") }
                    is DuplicateProgress.Hashing -> _state.update {
                        it.copy(phase = "Vergleichen…", progress = progress.percent, hashedCount = progress.hashed, totalCandidates = progress.total)
                    }
                    is DuplicateProgress.Done -> _state.update {
                        it.copy(isScanning = false, progress = 100, groups = progress.groups, totalScanned = progress.totalScanned, scanDone = true)
                    }
                }
            }
        }
    }

    fun toggleSelection(path: String) {
        _state.update { s -> s.copy(selectedForDeletion = if (path in s.selectedForDeletion) s.selectedForDeletion - path else s.selectedForDeletion + path) }
    }

    fun selectAllButNewest() {
        _state.update { s ->
            val toDelete = s.groups.flatMap { g -> g.files.sortedByDescending { it.modified }.drop(1) }.map { it.path }.toSet()
            s.copy(selectedForDeletion = toDelete)
        }
    }

    fun clearSelection() { _state.update { it.copy(selectedForDeletion = emptySet()) } }

    fun deleteSelected() {
        val selected = _state.value.selectedForDeletion
        if (selected.isEmpty()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { selected.forEach { repo.moveToRecycleBin(it) } }
            UndoManager.push(UndoAction(paths = selected, type = UndoType.DELETE))
            _state.update { s ->
                val remaining = s.groups
                    .map { g -> g.copy(files = g.files.filter { it.path !in selected }) }
                    .filter { it.files.size > 1 }
                s.copy(groups = remaining, selectedForDeletion = emptySet())
            }
        }
    }
}

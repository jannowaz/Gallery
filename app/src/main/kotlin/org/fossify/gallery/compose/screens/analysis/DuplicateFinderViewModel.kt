package org.fossify.gallery.compose.screens.analysis
import org.fossify.gallery.R

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
import org.fossify.gallery.extensions.mediaDB
import org.fossify.gallery.helpers.MediaRepository
import org.fossify.gallery.helpers.RefreshBus
import org.fossify.gallery.helpers.UndoAction
import org.fossify.gallery.helpers.UndoManager
import org.fossify.gallery.helpers.UndoType
import org.fossify.gallery.models.Medium
import java.io.File

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
    private var scanJob: kotlinx.coroutines.Job? = null

    fun cancelScan() {
        scanJob?.cancel()
        _state.update { it.copy(isScanning = false) }
    }

    fun setMode(mode: DuplicateMode) {
        _state.update { it.copy(mode = mode, groups = emptyList(), selectedForDeletion = emptySet(), scanDone = false) }
    }

    fun setThreshold(threshold: Int) {
        _state.update { it.copy(similarThreshold = threshold.coerceIn(0, 20)) }
    }

    fun startScan(folderPath: String) {
        val mode = _state.value.mode
        val threshold = _state.value.similarThreshold
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    isScanning = true, progress = 0, phase = getApplication<Application>().getString(R.string.dup_phase_collecting), folderPath = folderPath,
                    groups = emptyList(), selectedForDeletion = emptySet(),
                    hashedCount = 0, totalCandidates = 0, totalScanned = 0, scanDone = false,
                )
            }
            val flow = if (mode == DuplicateMode.SIMILAR) scanner.scanFolderSimilar(folderPath, threshold) else scanner.scanFolder(folderPath)
            flow.collect { progress ->
                when (progress) {
                    is DuplicateProgress.Collecting -> _state.update { it.copy(phase = getApplication<Application>().getString(R.string.dup_phase_found, progress.found)) }
                    is DuplicateProgress.Hashing -> _state.update {
                        it.copy(phase = getApplication<Application>().getString(R.string.dup_phase_comparing), progress = progress.percent, hashedCount = progress.hashed, totalCandidates = progress.total)
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
            // Auto-selecting is only safe for EXACT duplicates. In SIMILAR mode the files are
            // merely perceptually similar (not identical), so bulk-selecting would risk deleting
            // wanted photos - require manual selection there.
            if (s.mode != DuplicateMode.EXACT) return@update s
            val toDelete = s.groups.flatMap { g ->
                g.files.sortedWith(compareByDescending<DuplicateFile> { it.size }.thenByDescending { it.modified }).drop(1)
            }.map { it.path }.toSet()
            s.copy(selectedForDeletion = toDelete)
        }
    }

    /** Adds every other duplicate-group file that lives in the same folder as [path] to the
     * selection (on top of whatever's already selected, and marking [path] itself too if it
     * wasn't already) - a folder holding one file the user wants gone usually holds the whole
     * batch of "junk" copies they're trying to clear out, not just that single one. Scoped to
     * files the scan actually flagged as duplicates (any group), not the folder's contents at
     * large - "other found entries", not "everything in this folder". */
    fun selectAllInFolder(path: String) {
        val folder = File(path).parent ?: return
        _state.update { s ->
            // Same EXACT-only guard as selectAllButNewest, enforced here too (not just in the
            // Screen's icon visibility) so this can never mass-select SIMILAR-mode files - those
            // are only perceptually similar, not verified identical, regardless of which UI ends
            // up calling this.
            if (s.mode != DuplicateMode.EXACT) return@update s
            val sameFolderPaths = s.groups.asSequence().flatMap { it.files }.filter { File(it.path).parent == folder }.map { it.path }
            s.copy(selectedForDeletion = s.selectedForDeletion + sameFolderPaths)
        }
    }

    fun clearSelection() { _state.update { it.copy(selectedForDeletion = emptySet()) } }

    fun deleteSelected() {
        val selected = _state.value.selectedForDeletion
        if (selected.isEmpty()) return
        val filesByPath = _state.value.groups.flatMap { it.files }.associateBy { it.path }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                // Move to the in-app recycle bin instead of permanently deleting. This is
                // recoverable and never destroys the file, even for SIMILAR matches.
                val media = selected.mapNotNull { path ->
                    val f = filesByPath[path] ?: return@mapNotNull null
                    Medium(null, f.name, path, File(path).parent ?: "", f.modified, f.modified, f.size, f.mediaType, 0, false, 0L, 0L, f.rating)
                }
                try { if (media.isNotEmpty()) getApplication<Application>().mediaDB.insertAllKeepingExisting(media) } catch (_: Exception) { }
                selected.forEach { repo.moveToRecycleBin(it) }
            }
            UndoManager.push(UndoAction(paths = selected, type = UndoType.DELETE))
            RefreshBus.trigger()
            _state.update { s ->
                val remaining = s.groups
                    .map { g -> g.copy(files = g.files.filter { it.path !in selected }) }
                    .filter { it.files.size > 1 }
                s.copy(groups = remaining, selectedForDeletion = emptySet())
            }
        }
    }
}

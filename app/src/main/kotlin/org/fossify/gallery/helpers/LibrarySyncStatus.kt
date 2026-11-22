package org.fossify.gallery.helpers

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The small, process-wide status model shared by all library surfaces. */
enum class LibrarySyncPhase {
    IDLE,
    SCANNING,
    COMPLETE,
    ERROR,
}

data class LibrarySyncState(
    val phase: LibrarySyncPhase = LibrarySyncPhase.IDLE,
    val fullRescan: Boolean = false,
    val addedCount: Int = 0,
    val durationMs: Long = 0L,
    val errorMessage: String? = null,
    val revision: Long = 0L,
) {
    val isRunning: Boolean get() = phase == LibrarySyncPhase.SCANNING
}

/**
 * Publishes one calm refresh state for the whole foreground UI instead of making every tab invent
 * its own spinner/toast. The state is process-wide because the actual sync is process-wide too:
 * MediaViewModel, WorkManager and the boot catch-up can all be alive at the same time.
 */
object LibrarySyncStatus {
    private val _state = MutableStateFlow(LibrarySyncState())
    val state: StateFlow<LibrarySyncState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var clearJob: Job? = null
    private var revision = 0L

    fun started(fullRescan: Boolean) {
        clearJob?.cancel()
        revision++
        _state.value = LibrarySyncState(
            phase = LibrarySyncPhase.SCANNING,
            fullRescan = fullRescan,
            revision = revision,
        )
    }

    fun completed(addedCount: Int, durationMs: Long) {
        val currentRevision = revision
        _state.update {
            it.copy(
                phase = LibrarySyncPhase.COMPLETE,
                addedCount = addedCount,
                durationMs = durationMs,
                errorMessage = null,
            )
        }
        clearJob = scope.launch {
            delay(if (addedCount > 0) 3_000L else 1_200L)
            if (revision == currentRevision) _state.value = LibrarySyncState(revision = currentRevision)
        }
    }

    fun failed(error: Throwable, durationMs: Long) {
        val currentRevision = revision
        _state.update {
            it.copy(
                phase = LibrarySyncPhase.ERROR,
                durationMs = durationMs,
                errorMessage = error.message?.takeIf { message -> message.isNotBlank() },
            )
        }
        clearJob = scope.launch {
            delay(5_000L)
            if (revision == currentRevision) _state.value = LibrarySyncState(revision = currentRevision)
        }
    }
}

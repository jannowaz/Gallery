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

/**
 * One foreground-facing status feed for long-running library work. WorkManager remains the source
 * of truth for execution; this small model only makes active, completed and failed work coherent
 * across Explorer, Albums and the Media tab without each surface inventing its own spinner.
 */
enum class LibraryActivityKind { METADATA_SCAN, PERSON_SCAN }
enum class LibraryActivityPhase { RUNNING, COMPLETE, ERROR, CANCELLED }

data class LibraryActivityState(
    val key: String,
    val kind: LibraryActivityKind,
    val phase: LibraryActivityPhase,
    val sourcePath: String? = null,
    val includeSubfolders: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    val matches: Int = 0,
    val errorMessage: String? = null,
) {
    val progress: Float? get() = total.takeIf { it > 0 }?.let { done.coerceIn(0, it).toFloat() / it }
}

object LibraryActivityStatus {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val clearJobs = mutableMapOf<String, Job>()
    private val _states = MutableStateFlow<List<LibraryActivityState>>(emptyList())
    val states: StateFlow<List<LibraryActivityState>> = _states.asStateFlow()

    fun started(key: String, kind: LibraryActivityKind, sourcePath: String? = null, includeSubfolders: Boolean = false) {
        clearJobs.remove(key)?.cancel()
        val fresh = LibraryActivityState(
                key = key,
                kind = kind,
                phase = LibraryActivityPhase.RUNNING,
                sourcePath = sourcePath,
                includeSubfolders = includeSubfolders,
            )
        _states.update { states -> states.filterNot { it.key == key } + fresh }
    }

    fun progress(key: String, done: Int, total: Int, matches: Int = 0) {
        update(key) { previous ->
            previous.copy(done = done.coerceAtLeast(0), total = total.coerceAtLeast(0), matches = matches.coerceAtLeast(0))
        }
    }

    fun completed(key: String, done: Int, total: Int, matches: Int = 0) {
        finish(key, LibraryActivityPhase.COMPLETE, done, total, matches, null, 4_000L)
    }

    fun cancelled(key: String, done: Int = 0, total: Int = 0, matches: Int = 0) {
        finish(key, LibraryActivityPhase.CANCELLED, done, total, matches, null, 4_000L)
    }

    fun failed(key: String, error: Throwable?, done: Int = 0, total: Int = 0, matches: Int = 0) {
        finish(key, LibraryActivityPhase.ERROR, done, total, matches, error?.message?.takeIf(String::isNotBlank), 12_000L)
    }

    private fun finish(key: String, phase: LibraryActivityPhase, done: Int, total: Int, matches: Int, error: String?, clearAfterMs: Long) {
        update(key) { previous ->
            previous.copy(phase = phase, done = done.coerceAtLeast(0), total = total.coerceAtLeast(0), matches = matches.coerceAtLeast(0), errorMessage = error)
        }
        clearJobs.remove(key)?.cancel()
        clearJobs[key] = scope.launch {
            delay(clearAfterMs)
            _states.update { states -> states.filterNot { it.key == key } }
            clearJobs.remove(key)
        }
    }

    private fun update(key: String, transform: (LibraryActivityState) -> LibraryActivityState) {
        _states.update { states ->
            val old = states.firstOrNull { it.key == key }
            if (old == null) states else states.map { if (it.key == key) transform(it) else it }
        }
    }
}

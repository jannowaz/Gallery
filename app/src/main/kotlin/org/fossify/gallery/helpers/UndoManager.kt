package org.fossify.gallery.helpers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

enum class UndoType {
    DELETE,
    MOVE,
    TAG_ADD,
    TAG_REMOVE,
    /** Restores the exact per-file tag snapshots captured by a bulk tag operation. */
    TAG_RESTORE_SNAPSHOT,
    /** Restores the hierarchy configuration captured before a parent assignment. */
    TAG_HIERARCHY,
    RATING_CHANGE,

    /** A compression review/swipe replaced the original (now in the recycle bin) with the
     * compressed copy at extra["newPath"] - undo restores the original and deletes the copy. */
    COMPRESS_REPLACE,

    /** A lossless "Optimize" batch replaced each original (now in the recycle bin) with a converted
     * copy - extra maps originalPath -> newPath for every file. Undo restores all originals and
     * deletes the generated copies. Batch-shaped (a whole "Optimize all" run) unlike COMPRESS_REPLACE. */
    OPTIMIZE_REPLACE,
}

data class UndoAction(
    val paths: Set<String>,
    val type: UndoType,
    val extra: Map<String, String> = emptyMap(),
    val tagSnapshots: Map<String, Set<String>> = emptyMap(),
    val hierarchyBefore: Map<String, String>? = null,
    val ratingSnapshots: Map<String, Int> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis(),
    val id: Long = nextUndoActionId.incrementAndGet(),
)

private val nextUndoActionId = AtomicLong(0L)

object UndoManager {
    private const val MAX_ACTIONS = 5
    private const val EXPIRE_MS = 30_000L

    private val _actions = MutableStateFlow<List<UndoAction>>(emptyList())
    val actions: StateFlow<List<UndoAction>> = _actions.asStateFlow()

    private val _inFlightActionId = MutableStateFlow<Long?>(null)
    val inFlightActionId: StateFlow<Long?> = _inFlightActionId.asStateFlow()

    private val stateLock = Any()
    private val undoHandlers = mutableMapOf<UndoType, suspend (UndoAction) -> Unit>()

    fun registerHandler(type: UndoType, handler: suspend (UndoAction) -> Unit) {
        synchronized(stateLock) { undoHandlers[type] = handler }
    }

    fun unregisterHandler(type: UndoType) {
        synchronized(stateLock) { undoHandlers.remove(type) }
    }

    fun push(action: UndoAction) {
        // The shared UndoBar decides which actions deserve visible chrome. Keep DELETE actions in
        // the manager so its stack remains consistent and concurrent undo operations stay testable;
        // delete recovery itself is handled by the recycle bin.
        synchronized(stateLock) {
            val expired = _actions.value.filter { System.currentTimeMillis() - it.timestamp < EXPIRE_MS }
            _actions.value = (expired + action).takeLast(MAX_ACTIONS)
        }
    }

    fun peek(): UndoAction? = _actions.value.lastOrNull()

    /** Returns false when the handler threw - the undo bar shows an error toast then instead of
     * silently disappearing as if the undo had worked. */
    suspend fun undoLast(): Boolean {
        val reservation = synchronized(stateLock) {
            when {
                _inFlightActionId.value != null -> UndoReservation(busy = true)
                _actions.value.isEmpty() -> UndoReservation()
                else -> {
                    val action = _actions.value.last()
                    _inFlightActionId.value = action.id
                    UndoReservation(action = action)
                }
            }
        }
        if (reservation.busy) return false
        val action = reservation.action ?: return true

        val handler = synchronized(stateLock) { undoHandlers[action.type] }
        var ok = false
        try {
            ok = if (handler == null) {
                false
            } else {
                // Handlers do Room writes and file ops (restoreFromRecycleBin, delete, worker
                // enqueue). Keep that work off the UI thread even when the bar was tapped from Main.
                withContext(Dispatchers.IO) { handler.invoke(action) }
                true
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("UndoManager", "Undo handler failed for ${action.type}", e)
            false
        } finally {
            synchronized(stateLock) {
                // Publish the action removal before clearing the in-flight flag. That way the UI
                // can never observe "not busy" while the successfully handled action is still the
                // visible last item and start the same undo a second time.
                if (ok) _actions.value = _actions.value.filterNot { it.id == action.id }
                if (_inFlightActionId.value == action.id) _inFlightActionId.value = null
            }
        }

        // Failed or missing handlers intentionally keep the action visible so the user can retry
        // after the underlying transient error is gone; no false success is reported.
        return ok
    }

    /** Removes a specific action (used by the auto-dismiss timer of the undo bar). */
    fun remove(actionId: Long) {
        synchronized(stateLock) {
            if (_inFlightActionId.value != actionId) {
                _actions.value = _actions.value.filterNot { it.id == actionId }
            }
        }
    }

    fun clear() {
        synchronized(stateLock) {
            _actions.value = _actions.value.filter { it.id == _inFlightActionId.value }
        }
    }

    private data class UndoReservation(
        val action: UndoAction? = null,
        val busy: Boolean = false,
    )
}

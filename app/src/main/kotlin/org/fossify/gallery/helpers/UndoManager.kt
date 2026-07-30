package org.fossify.gallery.helpers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

enum class UndoType {
    DELETE,
    MOVE,
    TAG_ADD,
    TAG_REMOVE,
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
    val timestamp: Long = System.currentTimeMillis(),
)

object UndoManager {
    private const val MAX_ACTIONS = 5
    private const val EXPIRE_MS = 30_000L

    private val _actions = MutableStateFlow<List<UndoAction>>(emptyList())
    val actions: StateFlow<List<UndoAction>> = _actions.asStateFlow()

    private val undoHandlers = mutableMapOf<UndoType, suspend (UndoAction) -> Unit>()

    fun registerHandler(type: UndoType, handler: suspend (UndoAction) -> Unit) {
        undoHandlers[type] = handler
    }

    fun push(action: UndoAction) {
        val expired = _actions.value.filter { System.currentTimeMillis() - it.timestamp < EXPIRE_MS }
        val updated = (expired + action).takeLast(MAX_ACTIONS)
        _actions.value = updated
    }

    fun peek(): UndoAction? = _actions.value.lastOrNull()

    /** Returns false when the handler threw - the undo bar shows an error toast then instead of
     * silently disappearing as if the undo had worked. */
    suspend fun undoLast(): Boolean {
        val action = _actions.value.lastOrNull() ?: return true
        // Always drop the action afterwards, even if no handler is registered or it failed, so
        // the undo bar never gets stuck.
        val ok = try {
            // Handlers do Room writes and file ops (restoreFromRecycleBin, delete, worker enqueue) -
            // the undo bar fires them from a Main-dispatcher scope, so without hopping to IO Room
            // throws "Cannot access database on the main thread" and every undo silently fails.
            withContext(Dispatchers.IO) { undoHandlers[action.type]?.invoke(action) }
            true
        } catch (e: Exception) {
            android.util.Log.e("UndoManager", "Undo handler failed for ${action.type}", e)
            false
        }
        _actions.value = _actions.value.dropLast(1)
        return ok
    }

    /** Removes a specific action (used by the auto-dismiss timer of the undo bar). */
    fun remove(timestamp: Long) {
        _actions.value = _actions.value.filterNot { it.timestamp == timestamp }
    }

    fun clear() {
        _actions.value = emptyList()
    }
}

package org.fossify.gallery.helpers

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class UndoType {
    DELETE,
    MOVE,
    TAG_ADD,
    TAG_REMOVE,
    RATING_CHANGE,
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

    suspend fun undoLast() {
        val action = _actions.value.lastOrNull() ?: return
        val handler = undoHandlers[action.type] ?: return
        handler(action)
        _actions.value = _actions.value.dropLast(1)
    }

    fun clear() {
        _actions.value = emptyList()
    }

    fun clearExpired() {
        _actions.value = _actions.value.filter { System.currentTimeMillis() - it.timestamp < EXPIRE_MS }
    }
}

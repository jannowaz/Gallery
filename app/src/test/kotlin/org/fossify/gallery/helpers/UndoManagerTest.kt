package org.fossify.gallery.helpers

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UndoManagerTest {

    @Before
    fun reset() {
        UndoManager.clear()
        UndoType.entries.forEach(UndoManager::unregisterHandler)
    }

    @Test
    fun `completing undo A does not remove action B pushed while A is suspended`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var calls = 0
        UndoManager.registerHandler(UndoType.DELETE) {
            calls++
            started.complete(Unit)
            release.await()
        }
        val actionA = UndoAction(paths = setOf("a"), type = UndoType.DELETE)
        val actionB = UndoAction(paths = setOf("b"), type = UndoType.DELETE)
        UndoManager.push(actionA)

        val undo = async { UndoManager.undoLast() }
        withTimeout(1_000) { started.await() }
        UndoManager.push(actionB)
        release.complete(Unit)

        assertTrue(undo.await())
        assertEquals(listOf(actionB), UndoManager.actions.value)
        assertEquals(1, calls)
    }

    @Test
    fun `second tap while undo is running is rejected and handler runs once`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var calls = 0
        UndoManager.registerHandler(UndoType.MOVE) {
            calls++
            started.complete(Unit)
            release.await()
        }
        UndoManager.push(UndoAction(paths = setOf("a"), type = UndoType.MOVE))

        val first = async { UndoManager.undoLast() }
        withTimeout(1_000) { started.await() }
        assertFalse(UndoManager.undoLast())
        release.complete(Unit)

        assertTrue(first.await())
        assertEquals(1, calls)
    }

    @Test
    fun `missing handler is not reported as success and action remains retryable`() = runBlocking {
        UndoManager.push(UndoAction(paths = setOf("a"), type = UndoType.RATING_CHANGE))

        assertFalse(UndoManager.undoLast())
        assertEquals(1, UndoManager.actions.value.size)
    }

    @Test
    fun `remove deletes only the targeted action and preserves other queued actions`() {
        val deleteAction = UndoAction(paths = setOf("a"), type = UndoType.DELETE)
        val tagAction = UndoAction(paths = setOf("b"), type = UndoType.TAG_ADD, extra = mapOf("tag" to "sun"))
        UndoManager.push(deleteAction)
        UndoManager.push(tagAction)

        UndoManager.remove(tagAction.id)

        assertEquals(listOf(deleteAction), UndoManager.actions.value)
    }
}

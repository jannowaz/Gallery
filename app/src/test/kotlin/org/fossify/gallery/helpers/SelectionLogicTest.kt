package org.fossify.gallery.helpers

import org.junit.Assert.assertEquals
import org.junit.Test

class SelectionLogicTest {

    @Test
    fun `select all and invert only operate on visible items`() {
        val available = listOf("a", "b", "c")
        assertEquals(setOf("a", "b", "c"), selectionForAll(setOf("x"), available, invert = false))
        assertEquals(setOf("b", "c"), selectionForAll(setOf("a", "x"), available, invert = true))
    }
}

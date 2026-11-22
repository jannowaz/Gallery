package org.fossify.gallery.compose.components

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryBadgesAndTilesTest {

    @Test
    fun `clampSelectionInset prevents negative spring overshoot padding`() {
        assertEquals(0.dp, clampSelectionInset((-1.5f).dp))
        assertEquals(0.dp, clampSelectionInset((-0.01f).dp))
        assertEquals(0.dp, clampSelectionInset(0.dp))
        assertEquals(4.dp, clampSelectionInset(4.dp))
        assertEquals(6.dp, clampSelectionInset(6.dp))
    }

    @Test
    fun `formatCompactCount abbreviates thousands and millions cleanly`() {
        assertEquals("0", formatCompactCount(0))
        assertEquals("42", formatCompactCount(42))
        assertEquals("999", formatCompactCount(999))
        assertEquals("1k", formatCompactCount(1_000))
        assertEquals("1M", formatCompactCount(1_000_000))
        assertTrue(formatCompactCount(1_500).endsWith("k"))
        assertTrue(formatCompactCount(2_500_000).endsWith("M"))
    }
}

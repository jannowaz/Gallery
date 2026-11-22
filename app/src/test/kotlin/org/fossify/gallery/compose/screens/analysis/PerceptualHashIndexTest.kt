package org.fossify.gallery.compose.screens.analysis

import org.junit.Assert.assertEquals
import org.junit.Test

class PerceptualHashIndexTest {
    @Test
    fun `finds exactly the hashes within the requested hamming radius`() {
        val hashes = longArrayOf(
            0b0000,
            0b0001,
            0b0011,
            0b1111,
            0b1000,
        )
        val index = PerceptualHashIndex(hashes, BooleanArray(hashes.size) { true })

        assertEquals(setOf(0, 1, 2, 4), index.within(anchorIndex = 0, maxDistance = 2).toSet())
        assertEquals(setOf(3), index.within(anchorIndex = 3, maxDistance = 0).toSet())
    }
}

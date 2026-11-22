package org.fossify.gallery.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewerWindowMathTest {
    @Test
    fun appendIsBoundedForVeryLargeLibraries() {
        val current = (100_000 until 100_032).map { "p$it" }
        val incoming = (100_032 until 400_000).map { "p$it" }

        val result = ViewerWindowMath.merge(current, incoming, ViewerLoadDirection.AFTER, maxSize = 65)

        assertEquals(65, result.size)
        assertEquals("p399935", result.first())
        assertEquals("p399999", result.last())
    }

    @Test
    fun prependKeepsNewestBeforeWindowAndRemovesDuplicates() {
        val result = ViewerWindowMath.merge(
            current = listOf("p10", "p11", "p12"),
            incoming = listOf("p7", "p8", "p9", "p10"),
            direction = ViewerLoadDirection.BEFORE,
            maxSize = 5,
        )

        assertEquals(listOf("p7", "p8", "p9", "p10", "p11"), result)
        assertTrue(result.distinct().size == result.size)
    }
}

package org.fossify.gallery.compose.util

import org.junit.Assert.assertEquals
import org.junit.Test

class PinchToResizeColumnsTest {

    @Test
    fun `pinch outward decreases column count to enlarge tiles`() {
        assertEquals(3, computePinchedColumnCount(currentColumns = 4, zoomFactor = 1.25f))
        assertEquals(2, computePinchedColumnCount(currentColumns = 3, zoomFactor = 1.6f))
    }

    @Test
    fun `pinch inward increases column count to fit more tiles`() {
        assertEquals(5, computePinchedColumnCount(currentColumns = 4, zoomFactor = 0.80f))
        assertEquals(6, computePinchedColumnCount(currentColumns = 5, zoomFactor = 0.5f))
    }

    @Test
    fun `zoom inside dead zone preserves current column count`() {
        assertEquals(4, computePinchedColumnCount(currentColumns = 4, zoomFactor = 1.0f))
        assertEquals(4, computePinchedColumnCount(currentColumns = 4, zoomFactor = 1.24f))
        assertEquals(4, computePinchedColumnCount(currentColumns = 4, zoomFactor = 0.81f))
    }

    @Test
    fun `column count is clamped within min and max bounds`() {
        assertEquals(
            2,
            computePinchedColumnCount(currentColumns = 2, zoomFactor = 1.5f, minColumns = 2, maxColumns = 6),
        )
        assertEquals(
            6,
            computePinchedColumnCount(currentColumns = 6, zoomFactor = 0.6f, minColumns = 2, maxColumns = 6),
        )
        assertEquals(
            5,
            computePinchedColumnCount(currentColumns = 5, zoomFactor = 0.5f, minColumns = 2, maxColumns = 5),
        )
    }
}

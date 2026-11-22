package org.fossify.gallery.compose.screens.editor

import org.junit.Assert.assertEquals
import org.junit.Test

class EditorModelsTest {

    @Test
    fun `computeProportionalHeight preserves aspect ratio and clamps`() {
        assertEquals(1080, computeProportionalHeight(1920, 1920, 1080))
        assertEquals(540, computeProportionalHeight(960, 1920, 1080))
        assertEquals(0, computeProportionalHeight(0, 1920, 1080))
        assertEquals(1080, computeProportionalHeight(4000, 1920, 1080))
    }

    @Test
    fun `computeProportionalWidth preserves aspect ratio and clamps`() {
        assertEquals(1920, computeProportionalWidth(1080, 1920, 1080))
        assertEquals(960, computeProportionalWidth(540, 1920, 1080))
        assertEquals(0, computeProportionalWidth(0, 1920, 1080))
        assertEquals(1920, computeProportionalWidth(4000, 1920, 1080))
    }
}

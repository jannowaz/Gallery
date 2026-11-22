package org.fossify.gallery.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageColorAdjustmentsTest {

    @Test
    fun defaultParams_areIdentity() {
        val params = ColorAdjustmentParams()
        assertTrue(params.isIdentity)
        assertEquals(0f, params.brightness, 0.0001f)
        assertEquals(1f, params.contrast, 0.0001f)
        assertEquals(1f, params.saturation, 0.0001f)
    }

    @Test
    fun nonIdentityOffsets_areDetected() {
        assertFalse(ColorAdjustmentParams(brightness = 15f).isIdentity)
        assertFalse(ColorAdjustmentParams(contrast = 1.25f).isIdentity)
        assertFalse(ColorAdjustmentParams(saturation = 0.4f).isIdentity)
    }

    @Test
    fun subEpsilonOffsets_remainIdentity() {
        val nearIdentity = ColorAdjustmentParams(
            brightness = 0.005f,
            contrast = 1.004f,
            saturation = 0.996f,
        )
        assertTrue(nearIdentity.isIdentity)
    }

    @Test
    fun clamped_enforcesValidMinAndMaxBounds() {
        val tooLow = ColorAdjustmentParams(brightness = -500f, contrast = -1f, saturation = -2f).clamped()
        assertEquals(-100f, tooLow.brightness, 0.0001f)
        assertEquals(0.5f, tooLow.contrast, 0.0001f)
        assertEquals(0f, tooLow.saturation, 0.0001f)

        val tooHigh = ColorAdjustmentParams(brightness = 500f, contrast = 9f, saturation = 5f).clamped()
        assertEquals(100f, tooHigh.brightness, 0.0001f)
        assertEquals(2.0f, tooHigh.contrast, 0.0001f)
        assertEquals(2.0f, tooHigh.saturation, 0.0001f)
    }

    @Test
    fun clamped_preservesInRangeValues() {
        val valid = ColorAdjustmentParams(brightness = -25f, contrast = 1.35f, saturation = 0.75f)
        assertEquals(valid, valid.clamped())
    }
}

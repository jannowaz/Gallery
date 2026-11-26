package org.fossify.gallery.compose.screens.viewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoomableBoxTest {

    @Test
    fun `computeFillScale fills 16x9 landscape video on 9x20 portrait viewport`() {
        val viewWidth = 1080f
        val viewHeight = 2400f
        val contentAspect = 16f / 9f

        val fill = computeFillScale(viewWidth, viewHeight, contentAspect)
        val expected = (16f / 9f) / (1080f / 2400f)

        assertEquals(expected, fill, 0.001f)
        // At this scale, the letterboxed display height matches viewport height exactly.
        val (_, dispH) = computeDisplaySize(viewWidth, viewHeight, contentAspect)
        assertEquals(viewHeight, dispH * fill, 0.5f)
    }

    @Test
    fun `computeFillScale fills 9x16 portrait video on 20x9 landscape viewport`() {
        val viewWidth = 2400f
        val viewHeight = 1080f
        val contentAspect = 9f / 16f

        val fill = computeFillScale(viewWidth, viewHeight, contentAspect)
        val expected = (2400f / 1080f) / (9f / 16f)

        assertEquals(expected, fill, 0.001f)
        val (dispW, _) = computeDisplaySize(viewWidth, viewHeight, contentAspect)
        assertEquals(viewWidth, dispW * fill, 0.5f)
    }

    @Test
    fun `computeFillScale falls back to default zoom when video matches viewport aspect`() {
        val fill = computeFillScale(1080f, 1920f, 9f / 16f)
        assertEquals(DEFAULT_DOUBLE_TAP_ZOOM, fill, 0.001f)
    }

    @Test
    fun `computeFillScale falls back to default zoom when dimensions or aspect are unknown`() {
        assertEquals(DEFAULT_DOUBLE_TAP_ZOOM, computeFillScale(0f, 1920f, 16f / 9f), 0.001f)
        assertEquals(DEFAULT_DOUBLE_TAP_ZOOM, computeFillScale(1080f, 0f, 16f / 9f), 0.001f)
        assertEquals(DEFAULT_DOUBLE_TAP_ZOOM, computeFillScale(1080f, 1920f, 0f), 0.001f)
    }

    @Test
    fun `computeMaxZoomScale expands above ZOOM_MAX for ultra-wide landscape video`() {
        val ultraWideFill = computeFillScale(1080f, 2400f, 21f / 9f)
        val maxScale = computeMaxZoomScale(ultraWideFill)

        assertTrue("Ultra-wide fill scale should exceed 5x", ultraWideFill > 5f)
        assertTrue("Max zoom should allow zooming past fill", maxScale > ultraWideFill * 1.5f)
        assertTrue("Max zoom should exceed default ZOOM_MAX", maxScale > ZOOM_MAX)
    }

    @Test
    fun `computeMaxZoomScale preserves ZOOM_MAX floor for moderate fill scales`() {
        assertEquals(ZOOM_MAX, computeMaxZoomScale(2f), 0.001f)
    }

    @Test
    fun `scaling zoom constant matches Media3 RESIZE_MODE_ZOOM`() {
        assertEquals(4, SCALING_ZOOM)
    }
}

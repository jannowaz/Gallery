package org.fossify.gallery.viewmodels

import org.fossify.gallery.compose.screens.analysis.GifInspect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class GifInspectTest {

    // Minimal, structurally valid GIF pieces.
    private val header = intBytes(0x47, 0x49, 0x46, 0x38, 0x39, 0x61) // "GIF89a"
    private fun lsd(packed: Int) = intBytes(0x01, 0x00, 0x01, 0x00, packed, 0x00, 0x00) // 1x1
    private val gce = intBytes(0x21, 0xF9, 0x04, 0x00, 0x00, 0x00, 0x00, 0x00) // graphic control ext
    // One image: descriptor (1x1, no local table) + LZW data sub-block + terminator.
    private val imageBlock = intBytes(0x2C, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00, 0x02, 0x02, 0x44, 0x01, 0x00)
    private val trailer = intBytes(0x3B)

    private fun count(vararg parts: ByteArray) = GifInspect.frameCount(ByteArrayInputStream(parts.reduce { a, b -> a + b }))

    @Test fun `single-frame GIF counts one`() {
        assertEquals(1, count(header, lsd(0x00), imageBlock, trailer))
    }

    @Test fun `two-frame GIF counts two`() {
        assertEquals(2, count(header, lsd(0x00), gce, imageBlock, gce, imageBlock, trailer))
    }

    @Test fun `three-frame GIF stops early reporting two (animated is enough)`() {
        assertEquals(2, count(header, lsd(0x00), imageBlock, imageBlock, imageBlock, trailer))
    }

    @Test fun `global colour table is skipped correctly`() {
        // packed 0x80 => GCT present, size bits 0 => 2 colours => 6 bytes of table.
        val gct = intBytes(0, 0, 0, 255, 255, 255)
        assertEquals(2, count(header, lsd(0x80), gct, imageBlock, imageBlock, trailer))
    }

    @Test fun `local colour table and extensions are skipped without miscounting`() {
        // Image descriptor with a local colour table (packed 0x80, 2 colours => 6 bytes) between frames.
        val imgWithLct = intBytes(0x2C, 0, 0, 0, 0, 0x01, 0, 0x01, 0, 0x80, /*LCT*/ 0, 0, 0, 1, 1, 1, /*data*/ 0x02, 0x02, 0x44, 0x01, 0x00)
        assertEquals(2, count(header, lsd(0x00), gce, imgWithLct, gce, imageBlock, trailer))
    }

    @Test fun `non-GIF data returns minus one`() {
        assertEquals(-1, GifInspect.frameCount(ByteArrayInputStream(intBytes(0x89, 0x50, 0x4E, 0x47, 1, 2, 3, 4, 5, 6, 7, 8))))
        assertEquals(-1, GifInspect.frameCount(ByteArrayInputStream(ByteArray(0))))
    }

    @Test fun `animated classification never treats multi-frame as static`() {
        // The safety-critical property: >1 frame must be reported as animated (>=2), never 1.
        val animated = count(header, lsd(0x00), imageBlock, imageBlock, trailer)
        assertTrue("multi-frame GIF must read as animated", animated >= 2)
    }

    private fun intBytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }
}

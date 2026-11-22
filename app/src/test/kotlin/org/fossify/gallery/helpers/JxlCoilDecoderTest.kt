package org.fossify.gallery.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JxlCoilDecoderTest {

    @Test
    fun isJxlMagicBytes_detectsNakedCodestreamHeader() {
        val naked = byteArrayOf(0xFF.toByte(), 0x0A.toByte(), 0x00, 0x10)
        assertTrue(JxlCoilDecoder.isJxlMagicBytes(naked))
        assertTrue(JxlCoilDecoder.isJxlMagicBytes(byteArrayOf(0xFF.toByte(), 0x0A.toByte())))
    }

    @Test
    fun isJxlMagicBytes_detectsIsobmffContainerHeader() {
        val container = byteArrayOf(
            0x00, 0x00, 0x00, 0x0C,
            0x4A, 0x58, 0x4C, 0x20,
            0x0D, 0x0A, 0x87.toByte(), 0x0A,
            0x00, 0x00, 0x00, 0x14,
        )
        assertTrue(JxlCoilDecoder.isJxlMagicBytes(container))
    }

    @Test
    fun isJxlMagicBytes_rejectsNonJxlFormatsAndShortBuffers() {
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        val webp = "RIFF\u0000\u0000\u0000\u0000WEBP".toByteArray()

        assertFalse(JxlCoilDecoder.isJxlMagicBytes(jpeg))
        assertFalse(JxlCoilDecoder.isJxlMagicBytes(png))
        assertFalse(JxlCoilDecoder.isJxlMagicBytes(webp))
        assertFalse(JxlCoilDecoder.isJxlMagicBytes(byteArrayOf()))
        assertFalse(JxlCoilDecoder.isJxlMagicBytes(byteArrayOf(0xFF.toByte())))
    }

    @Test
    fun isJxlMagicBytes_rejectsTruncatedOrMutatedIsobmffHeader() {
        val truncated = byteArrayOf(
            0x00, 0x00, 0x00, 0x0C,
            0x4A, 0x58, 0x4C, 0x20,
        )
        val mutated = byteArrayOf(
            0x00, 0x00, 0x00, 0x0C,
            0x4A, 0x58, 0x4C, 0x20,
            0x0D, 0x0A, 0x87.toByte(), 0x0B,
        )
        assertFalse(JxlCoilDecoder.isJxlMagicBytes(truncated))
        assertFalse(JxlCoilDecoder.isJxlMagicBytes(mutated))
    }

    @Test
    fun factory_equalsAndHashCode_consistentForCoilRegistryDeduplication() {
        val a = JxlCoilDecoder.Factory()
        val b = JxlCoilDecoder.Factory()
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }
}

package org.fossify.gallery.receivers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class WidgetActionReceiverTest {

    @Test
    fun `sanitizeFilePrefix preserves safe prefix`() {
        assertEquals("Vacation_2026", WidgetActionReceiver.sanitizeFilePrefix("Vacation_2026"))
    }

    @Test
    fun `sanitizeFilePrefix blocks path traversal and path separators`() {
        val result = WidgetActionReceiver.sanitizeFilePrefix("../../etc/passwd")
        assertFalse(result.contains(".."))
        assertFalse(result.contains('/'))
        assertFalse(result.contains('\\'))
        assertEquals("etc_passwd", result)
    }

    @Test
    fun `sanitizeFilePrefix replaces invalid filename characters`() {
        assertEquals("a_b_c_d_e_f_g", WidgetActionReceiver.sanitizeFilePrefix("a:b*c?d\"e<f>g"))
    }

    @Test
    fun `sanitizeFilePrefix falls back to IMG when blank, separators, or dots only`() {
        assertEquals("IMG", WidgetActionReceiver.sanitizeFilePrefix(""))
        assertEquals("IMG", WidgetActionReceiver.sanitizeFilePrefix("   "))
        assertEquals("IMG", WidgetActionReceiver.sanitizeFilePrefix("..."))
        assertEquals("IMG", WidgetActionReceiver.sanitizeFilePrefix(".."))
        assertEquals("IMG", WidgetActionReceiver.sanitizeFilePrefix("///"))
    }

    @Test
    fun `sanitizeFilePrefix trims surrounding dots and caps length to 64`() {
        assertEquals("hidden", WidgetActionReceiver.sanitizeFilePrefix("...hidden..."))
        val longInput = "A".repeat(100)
        assertEquals(64, WidgetActionReceiver.sanitizeFilePrefix(longInput).length)
    }
}

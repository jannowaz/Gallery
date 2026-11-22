package org.fossify.gallery.compose.screens.viewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.text.DateFormat
import java.util.Calendar
import java.util.Locale

class ViewerOverlaysTest {

    @Test
    fun `formatExifDate formats standard EXIF colon timestamps into localized dates`() {
        val formatted = formatExifDate("2026:11:22 14:35:09", Locale.GERMANY)
        val expected = DateFormat.getDateInstance(DateFormat.SHORT, Locale.GERMANY).format(
            Calendar.getInstance().apply {
                clear()
                set(2026, Calendar.NOVEMBER, 22)
            }.time,
        )
        assertEquals(expected, formatted)
        assertNotNull(formatted)
        assertFalse(formatted!!.contains(':'))
    }

    @Test
    fun `formatExifDate supports ISO hyphen dates and preserves unknown text safely`() {
        val expectedUs = DateFormat.getDateInstance(DateFormat.SHORT, Locale.US).format(
            Calendar.getInstance().apply {
                clear()
                set(2025, Calendar.MARCH, 5)
            }.time,
        )
        assertEquals(expectedUs, formatExifDate("2025-03-05T08:15:00Z", Locale.US))
        assertEquals("unknown", formatExifDate("  unknown  ", Locale.US))
    }

    @Test
    fun `formatExifDate returns null for blank or null input`() {
        assertNull(formatExifDate(null, Locale.US))
        assertNull(formatExifDate("", Locale.US))
        assertNull(formatExifDate("   ", Locale.US))
    }
}

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

    @Test
    fun `sanitizeVideoPlaybackSpeed keeps supported speeds and falls back to 1x`() {
        VIDEO_PLAYBACK_SPEEDS.forEach { speed ->
            assertEquals(speed, sanitizeVideoPlaybackSpeed(speed), 0.0001f)
        }
        assertEquals(1f, sanitizeVideoPlaybackSpeed(0f), 0.0001f)
        assertEquals(1f, sanitizeVideoPlaybackSpeed(4f), 0.0001f)
        assertEquals(1f, sanitizeVideoPlaybackSpeed(Float.NaN), 0.0001f)
    }

    @Test
    fun `formatVideoSeconds formats zero, short, and long durations as mm ss`() {
        assertEquals("00:00", formatVideoSeconds(-5))
        assertEquals("00:09", formatVideoSeconds(9))
        assertEquals("01:05", formatVideoSeconds(65))
        assertEquals("62:03", formatVideoSeconds(3723))
    }

    @Test
    fun `nextUniqueTrimmedFile avoids overwriting existing files`() {
        val tempDir = kotlin.io.path.createTempDirectory("trim-test").toFile()
        try {
            val source = java.io.File(tempDir, "clip.mp4").apply { createNewFile() }
            val first = nextUniqueTrimmedFile(source)
            assertEquals("clip_trimmed.mp4", first.name)
            first.createNewFile()

            val second = nextUniqueTrimmedFile(source)
            assertEquals("clip_trimmed_2.mp4", second.name)
            second.createNewFile()

            val third = nextUniqueTrimmedFile(source)
            assertEquals("clip_trimmed_3.mp4", third.name)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}

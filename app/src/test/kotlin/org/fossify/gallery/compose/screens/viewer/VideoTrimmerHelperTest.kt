package org.fossify.gallery.compose.screens.viewer

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class VideoTrimmerHelperTest {

    @Test
    fun clampTrimRange_handlesZeroAndNegativeDuration() {
        assertEquals(0L to 0L, clampTrimRange(100L, 500L, 0L))
        assertEquals(0L to 0L, clampTrimRange(0L, 1000L, -50L))
    }

    @Test
    fun clampTrimRange_enforcesMinimumSpanWhenInvertedOrTooNarrow() {
        val (start, end) = clampTrimRange(5_000L, 5_100L, 10_000L)
        assertEquals(5_000L, start)
        assertEquals(5_000L + MIN_TRIM_DURATION_MS, end)

        val (invStart, invEnd) = clampTrimRange(8_000L, 2_000L, 10_000L)
        assertEquals(8_000L, invStart)
        assertEquals(8_000L + MIN_TRIM_DURATION_MS, invEnd)
    }

    @Test
    fun clampTrimRange_clampsOutOfBoundsStartAndEnd() {
        val (s1, e1) = clampTrimRange(-500L, 99_000L, 10_000L)
        assertEquals(0L, s1)
        assertEquals(10_000L, e1)

        val (s2, e2) = clampTrimRange(9_900L, 10_000L, 10_000L)
        assertEquals(10_000L - MIN_TRIM_DURATION_MS, s2)
        assertEquals(10_000L, e2)
    }

    @Test
    fun clampTrimRange_allowsShortVideoBelowMinSpan() {
        val (start, end) = clampTrimRange(0L, 300L, 300L)
        assertEquals(0L, start)
        assertEquals(300L, end)
    }

    @Test
    fun buildTrimmedOutputFile_createsTrimmedMp4Sibling() {
        val out = buildTrimmedOutputFile("/storage/emulated/0/Movies/clip.mov", exists = { false })
        assertEquals(File("/storage/emulated/0/Movies/clip_trimmed.mp4"), out)
    }

    @Test
    fun buildTrimmedOutputFile_avoidsCollisionsWithIncrementalSuffix() {
        val existing = setOf(
            "/storage/emulated/0/Movies/clip_trimmed.mp4",
            "/storage/emulated/0/Movies/clip_trimmed_2.mp4",
        )
        val out = buildTrimmedOutputFile(
            originalPath = "/storage/emulated/0/Movies/clip.mp4",
            exists = { it.path in existing },
        )
        assertEquals(File("/storage/emulated/0/Movies/clip_trimmed_3.mp4"), out)
    }

    @Test
    fun formatTrimTimestamp_formatsSecondsMinutesAndHours() {
        assertEquals("00:00", formatTrimTimestamp(-1_000L))
        assertEquals("00:00", formatTrimTimestamp(0L))
        assertEquals("00:09", formatTrimTimestamp(9_400L))
        assertEquals("02:05", formatTrimTimestamp(125_000L))
        assertEquals("1:01:05", formatTrimTimestamp(3_665_000L))
    }
}

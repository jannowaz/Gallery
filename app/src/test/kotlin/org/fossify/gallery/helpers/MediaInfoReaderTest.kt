package org.fossify.gallery.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

class MediaInfoReaderTest {
    @Test
    fun xmpArraysIgnoreLanguageQualifierAndReturnFullValue() {
        val xmp = linkedMapOf(
            "dc:description[1]/?xml:lang" to "x-default",
            "dc:description[1]" to "A deliberately long generation prompt\nwith a second line",
        )

        assertEquals(
            "A deliberately long generation prompt\nwith a second line",
            findXmpValue(xmp, "dc:description"),
        )
    }

    @Test
    fun standardMetadataWinsAndPngPromptIsUsedAsDescriptionFallback() {
        val standard = resolveDescriptiveMetadata(
            DescriptiveMetadataSources(
                xmp = mapOf("dc:title[1]" to "Visible title"),
                exifDescription = "Embedded description",
                pngText = mapOf("parameters" to "Generation prompt"),
            ),
        )
        val promptFallback = resolveDescriptiveMetadata(
            DescriptiveMetadataSources(pngText = mapOf("Prompt" to "Generation prompt")),
        )

        assertEquals("Visible title", standard.title)
        assertEquals("Embedded description", standard.description)
        assertEquals("Generation prompt", promptFallback.description)
    }

    @Test
    fun duplicateCommentIsNotShownTwice() {
        val result = resolveDescriptiveMetadata(
            DescriptiveMetadataSources(
                exifDescription = "same   prompt",
                jpegComment = "Same prompt",
            ),
        )

        assertEquals("same   prompt", result.description)
        assertNull(result.comment)
    }

    @Test
    fun exifCommentEncodingPrefixAndNullPaddingAreRemoved() {
        assertEquals(
            "Keep all of this text",
            cleanMetadataValue("ASCII\u0000\u0000\u0000Keep all of this text\u0000"),
        )
        assertNull(cleanMetadataValue(" \u0000 "))
    }

    @Test
    fun publicExifDateTimeFallbackParsesStandardValueAndRejectsInvalidInput() {
        val expected = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
            .parse("2026:11:20 22:30:00")?.time

        assertEquals(expected, parseExifDateTimeMillis("2026:11:20 22:30:00"))
        assertNull(parseExifDateTimeMillis("not a date"))
        assertNull(parseExifDateTimeMillis(null))
    }
}

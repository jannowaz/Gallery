package org.fossify.gallery.activities

import org.junit.Assert.assertEquals
import org.junit.Test

class ComposeExternalViewerHelperTest {

    @Test
    fun resolveExtension_prefersCleanDisplayNameExtension() {
        assertEquals("jxl", resolveExternalViewerExtension("IMG_2026.JXL", "application/octet-stream"))
        assertEquals("webp", resolveExternalViewerExtension("sticker.webp", "image/jpeg"))
        assertEquals("mp4", resolveExternalViewerExtension("clip.MP4", null))
    }

    @Test
    fun resolveExtension_fallsBackToMimeTypeWhenNameHasNoExtension() {
        assertEquals("png", resolveExternalViewerExtension("attachment_123", "image/png"))
        assertEquals("heic", resolveExternalViewerExtension(null, "image/heic"))
        assertEquals("jxl", resolveExternalViewerExtension(null, "image/jxl"))
        assertEquals("mkv", resolveExternalViewerExtension("video", "video/x-matroska"))
        assertEquals("mp4", resolveExternalViewerExtension(null, "video/unknown"))
        assertEquals("jpg", resolveExternalViewerExtension(null, null))
    }

    @Test
    fun sanitizeFilename_stripsTraversalAndSpecialChars() {
        assertEquals("photo_1.jpg", sanitizeExternalViewerFilename("../../photo 1.png", "jpg"))
        assertEquals("external_media.mp4", sanitizeExternalViewerFilename("///...", "mp4"))
        assertEquals("IMG_001.jxl", sanitizeExternalViewerFilename("IMG_001.jxl", ".JXL"))
    }
}

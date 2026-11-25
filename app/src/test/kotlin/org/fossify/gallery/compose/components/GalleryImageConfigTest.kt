package org.fossify.gallery.compose.components

import org.fossify.gallery.compose.screens.viewer.viewerImageCacheKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GalleryImageConfigTest {

    @Test
    fun shouldUseRgb565Thumbnail_trueForOpaqueCameraThumbnails() {
        assertTrue(shouldUseRgb565Thumbnail("/storage/DCIM/IMG_001.jpg", 384))
        assertTrue(shouldUseRgb565Thumbnail("/storage/DCIM/IMG_002.JPEG", 256))
        assertTrue(shouldUseRgb565Thumbnail("/storage/DCIM/IMG_003.heic", 384))
        assertTrue(shouldUseRgb565Thumbnail("/storage/DCIM/IMG_004.HEIF", 384))
    }

    @Test
    fun shouldUseRgb565Thumbnail_falseForAlphaFormatsOrFullSizeLoads() {
        assertFalse(shouldUseRgb565Thumbnail("/storage/Pictures/sticker.png", 384))
        assertFalse(shouldUseRgb565Thumbnail("/storage/Pictures/image.webp", 384))
        assertFalse(shouldUseRgb565Thumbnail("/storage/Pictures/anim.gif", 384))
        assertFalse(shouldUseRgb565Thumbnail("/storage/Pictures/vector.svg", 384))
        assertFalse(shouldUseRgb565Thumbnail("/storage/Pictures/photo.jxl", 384))
        assertFalse(shouldUseRgb565Thumbnail("/storage/DCIM/IMG_001.jpg", null))
    }

    @Test
    fun viewerImageCacheKey_matchesBetweenImagePageAndPrefetch() {
        assertEquals(
            "viewer:/storage/DCIM/a.jpg:0",
            viewerImageCacheKey("/storage/DCIM/a.jpg"),
        )
        assertEquals(
            "viewer:/storage/DCIM/a.jpg:2",
            viewerImageCacheKey("/storage/DCIM/a.jpg", retryNonce = 2),
        )
    }
}

package org.fossify.gallery.compose.screens

import org.fossify.gallery.helpers.TYPE_GIFS
import org.fossify.gallery.helpers.TYPE_IMAGES
import org.fossify.gallery.helpers.TYPE_RAWS
import org.fossify.gallery.helpers.TYPE_SVGS
import org.fossify.gallery.helpers.TYPE_VIDEOS
import org.fossify.gallery.models.Medium
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecycleBinFilterTest {

    private fun medium(type: Int) = Medium(
        id = 1L,
        name = "sample",
        path = "/storage/emulated/0/DCIM/sample",
        parentPath = "/storage/emulated/0/DCIM",
        modified = 0L,
        taken = 0L,
        size = 100L,
        type = type,
        videoDuration = 0,
        isFavorite = false,
        deletedTS = 1000L,
        mediaStoreId = 1L,
    )

    @Test
    fun `all filter accepts every media type`() {
        listOf(TYPE_IMAGES, TYPE_VIDEOS, TYPE_GIFS, TYPE_RAWS, TYPE_SVGS).forEach { type ->
            assertTrue(matchesRecycleBinMediaType(medium(type), mediaTypeFilter = 0))
        }
    }

    @Test
    fun `image filter includes photos gifs raws and svgs but excludes videos`() {
        assertTrue(matchesRecycleBinMediaType(medium(TYPE_IMAGES), mediaTypeFilter = 1))
        assertTrue(matchesRecycleBinMediaType(medium(TYPE_GIFS), mediaTypeFilter = 1))
        assertTrue(matchesRecycleBinMediaType(medium(TYPE_RAWS), mediaTypeFilter = 1))
        assertTrue(matchesRecycleBinMediaType(medium(TYPE_SVGS), mediaTypeFilter = 1))
        assertFalse(matchesRecycleBinMediaType(medium(TYPE_VIDEOS), mediaTypeFilter = 1))
    }

    @Test
    fun `video filter includes only videos`() {
        assertTrue(matchesRecycleBinMediaType(medium(TYPE_VIDEOS), mediaTypeFilter = 2))
        assertFalse(matchesRecycleBinMediaType(medium(TYPE_IMAGES), mediaTypeFilter = 2))
        assertFalse(matchesRecycleBinMediaType(medium(TYPE_GIFS), mediaTypeFilter = 2))
        assertFalse(matchesRecycleBinMediaType(medium(TYPE_RAWS), mediaTypeFilter = 2))
        assertFalse(matchesRecycleBinMediaType(medium(TYPE_SVGS), mediaTypeFilter = 2))
    }
}

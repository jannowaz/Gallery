package org.fossify.gallery.compose.screens

import org.fossify.gallery.helpers.TYPE_GIFS
import org.fossify.gallery.helpers.TYPE_IMAGES
import org.fossify.gallery.helpers.TYPE_RAWS
import org.fossify.gallery.helpers.TYPE_SVGS
import org.fossify.gallery.helpers.TYPE_VIDEOS
import org.fossify.gallery.viewmodels.LibraryFilterState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExplorerNameFilterTest {
    @Test
    fun `blank filter keeps every name visible`() {
        assertTrue(explorerNameMatchesFilter("Camera", "   "))
    }

    @Test
    fun `matches partial names without case sensitivity`() {
        assertTrue(explorerNameMatchesFilter("Summer Holiday.JPG", "holiday"))
        assertTrue(explorerNameMatchesFilter("Screenshots", "  SCREEN  "))
    }

    @Test
    fun `rejects names without the requested text`() {
        assertFalse(explorerNameMatchesFilter("Camera", "download"))
    }

    @Test
    fun `empty collection filter excludes all items instead of showing full library`() {
        val item = ExplorerItem(name = "photo.jpg", path = "/sdcard/DCIM/photo.jpg", isDirectory = false, type = TYPE_IMAGES)
        val emptyCollectionFilter = LibraryFilterState(
            activeCollectionName = "1 Stern",
            additionalMediaPaths = emptySet(),
        )
        assertFalse(item.matchesLibraryFilter(emptyCollectionFilter, emptySet(), null))
    }

    @Test
    fun `typeFilter matches images videos webm gif raw and svg accurately`() {
        val jpg = ExplorerItem(name = "a.jpg", path = "/a.jpg", isDirectory = false, type = TYPE_IMAGES)
        val webm = ExplorerItem(name = "clip.webm", path = "/clip.webm", isDirectory = false, type = 0)
        val gif = ExplorerItem(name = "anim.gif", path = "/anim.gif", isDirectory = false, type = TYPE_GIFS)
        val raw = ExplorerItem(name = "shot.dng", path = "/shot.dng", isDirectory = false, type = TYPE_RAWS)
        val svg = ExplorerItem(name = "icon.svg", path = "/icon.svg", isDirectory = false, type = TYPE_SVGS)

        assertTrue(jpg.matchesLibraryFilter(LibraryFilterState(typeFilter = TYPE_IMAGES), emptySet(), null))
        assertFalse(webm.matchesLibraryFilter(LibraryFilterState(typeFilter = TYPE_IMAGES), emptySet(), null))

        assertTrue(webm.matchesLibraryFilter(LibraryFilterState(typeFilter = TYPE_VIDEOS), emptySet(), null))
        assertFalse(jpg.matchesLibraryFilter(LibraryFilterState(typeFilter = TYPE_VIDEOS), emptySet(), null))

        assertTrue(gif.matchesLibraryFilter(LibraryFilterState(typeFilter = TYPE_GIFS), emptySet(), null))
        assertFalse(jpg.matchesLibraryFilter(LibraryFilterState(typeFilter = TYPE_GIFS), emptySet(), null))

        assertTrue(raw.matchesLibraryFilter(LibraryFilterState(typeFilter = TYPE_RAWS), emptySet(), null))
        assertFalse(jpg.matchesLibraryFilter(LibraryFilterState(typeFilter = TYPE_RAWS), emptySet(), null))

        assertTrue(svg.matchesLibraryFilter(LibraryFilterState(typeFilter = TYPE_SVGS), emptySet(), null))
        assertFalse(jpg.matchesLibraryFilter(LibraryFilterState(typeFilter = TYPE_SVGS), emptySet(), null))
    }
}

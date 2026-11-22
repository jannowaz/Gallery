package org.fossify.gallery.viewmodels

import org.fossify.gallery.compose.screens.analysis.DuplicateFile
import org.fossify.gallery.compose.screens.analysis.DuplicateGroup
import org.fossify.gallery.compose.screens.analysis.DuplicateSelection
import org.fossify.gallery.compose.screens.analysis.KeepStrategy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateSelectionTest {

    private fun file(path: String, size: Long = 1000, modified: Long = 1000, rating: Int = 0, fav: Boolean = false) =
        DuplicateFile(path = path, name = path.substringAfterLast('/'), size = size, modified = modified, mediaType = 1, rating = rating, isFavorite = fav)

    // Mirrors the on-device scenario: three groups, each with one copy in .../Camera.
    private val g1 = DuplicateGroup("h1", listOf(file("/DCIM/CompressTest/big.jpg"), file("/DCIM/DupTest/WhatsApp/w1.jpg"), file("/DCIM/DupTest/Camera/c1.jpg")))
    private val g2 = DuplicateGroup("h2", listOf(file("/DCIM/CompressTest/opt.jpg"), file("/DCIM/DupTest/WhatsApp/w2.jpg"), file("/DCIM/DupTest/Backup/b2.jpg"), file("/DCIM/DupTest/Camera/c2.jpg")))
    private val g3 = DuplicateGroup("h3", listOf(file("/DCIM/DupTest/Camera/c3.jpg"), file("/DCIM/DupTest/Backup/b3.jpg")))
    private val groups = listOf(g1, g2, g3)

    @Test
    fun `invert flips every group and never leaves a Camera copy marked`() {
        val cameraCopies = setOf("/DCIM/DupTest/Camera/c1.jpg", "/DCIM/DupTest/Camera/c2.jpg", "/DCIM/DupTest/Camera/c3.jpg")
        val result = DuplicateSelection.invert(groups, cameraCopies, folderFilter = null)

        val expected = setOf(
            "/DCIM/CompressTest/big.jpg", "/DCIM/DupTest/WhatsApp/w1.jpg",           // g1
            "/DCIM/CompressTest/opt.jpg", "/DCIM/DupTest/WhatsApp/w2.jpg", "/DCIM/DupTest/Backup/b2.jpg", // g2
            "/DCIM/DupTest/Backup/b3.jpg",                                            // g3
        )
        assertEquals(expected, result)
        cameraCopies.forEach { assertFalse("$it should be unmarked after inverting a Camera selection", it in result) }
    }

    @Test
    fun `invert keeps a survivor when a whole group would flip to marked`() {
        // Nothing marked in g3 -> inverting would mark both copies; the keeper (newest/largest) is spared.
        val g = DuplicateGroup("h", listOf(file("/a/x.jpg", size = 100, modified = 1), file("/a/y.jpg", size = 200, modified = 2)))
        val result = DuplicateSelection.invert(listOf(g), emptySet(), folderFilter = null)
        assertEquals(1, result.size)
        assertTrue("keeper (larger/newer) must survive", "/a/y.jpg" !in result)
        assertTrue("/a/x.jpg" in result)
    }

    @Test
    fun `substring then invert leaves no Camera copy marked (full on-device sequence)`() {
        // Reproduces the exact device flow: mark everything under .../Camera, then invert.
        val afterMark = DuplicateSelection.bySubstring(groups, emptySet(), folderFilter = null, query = "Camera", contains = true)
        assertEquals(setOf("/DCIM/DupTest/Camera/c1.jpg", "/DCIM/DupTest/Camera/c2.jpg", "/DCIM/DupTest/Camera/c3.jpg"), afterMark)

        val afterInvert = DuplicateSelection.invert(groups, afterMark, folderFilter = null)
        listOf("c1", "c2", "c3").forEach { c ->
            assertFalse("Camera/$c must be unmarked after invert", "/DCIM/DupTest/Camera/$c.jpg" in afterInvert)
        }
        assertEquals(6, afterInvert.size)
    }

    @Test
    fun `substring marks the sole matching copy even in a two-file group`() {
        val marked = DuplicateSelection.bySubstring(groups, emptySet(), folderFilter = null, query = "Camera", contains = true)
        assertTrue("c3 (the only Camera copy in the 2-file group) must be marked", "/DCIM/DupTest/Camera/c3.jpg" in marked)
    }

    @Test
    fun `invert is scoped to the folder filter and leaves hidden groups untouched`() {
        // Filter to WhatsApp: only g1 and g2 are visible; g3 (no WhatsApp copy) is hidden.
        val selected = setOf("/DCIM/DupTest/Camera/c3.jpg") // a mark living in the hidden group
        val result = DuplicateSelection.invert(groups, selected, folderFilter = "/DCIM/DupTest/WhatsApp")

        assertTrue("hidden group's mark must be preserved", "/DCIM/DupTest/Camera/c3.jpg" in result)
        // g1/g2 had nothing marked -> each flips fully except its spared keeper.
        assertTrue("/DCIM/DupTest/Camera/c1.jpg" in result || "/DCIM/DupTest/WhatsApp/w1.jpg" in result)
        assertFalse("g3 must not gain new marks (b3 stays untouched)", "/DCIM/DupTest/Backup/b3.jpg" in result)
    }

    @Test
    fun `auto-select only changes explicitly selected groups`() {
        val first = DuplicateGroup(
            "one",
            listOf(file("/one/old.jpg", modified = 1), file("/one/new.jpg", modified = 2)),
        )
        val second = DuplicateGroup(
            "two",
            listOf(file("/two/old.jpg", modified = 1), file("/two/new.jpg", modified = 2)),
        )
        val existingMarkOutsideTarget = setOf("/two/new.jpg")

        val result = DuplicateSelection.applyKeepStrategy(
            groups = listOf(first, second),
            selected = existingMarkOutsideTarget,
            strategy = KeepStrategy.NEWEST,
            targetGroupKeys = setOf(first.targetKey),
            additive = false,
        )

        assertEquals(setOf("/one/old.jpg", "/two/new.jpg"), result)
        assertFalse("unselected group must not be recalculated", "/two/old.jpg" in result)
    }

    @Test
    fun `group targeting does not rely on a possibly shared hash`() {
        val first = DuplicateGroup(
            "same-hash",
            listOf(file("/first/old.jpg", modified = 1), file("/first/new.jpg", modified = 2)),
        )
        val second = DuplicateGroup(
            "same-hash",
            listOf(file("/second/old.jpg", modified = 1), file("/second/new.jpg", modified = 2)),
        )

        val result = DuplicateSelection.applyKeepStrategy(
            groups = listOf(first, second),
            selected = emptySet(),
            strategy = KeepStrategy.NEWEST,
            targetGroupKeys = setOf(first.targetKey),
            additive = false,
        )

        assertEquals(setOf("/first/old.jpg"), result)
    }
}

package org.fossify.gallery.helpers

import org.fossify.gallery.models.Medium
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** MediaGrouping is pure list-shaping logic shared between MediaScreen's paged accumulator and
 * the full-list builders - these pin down bucket boundaries, header ordering, hierarchy nesting,
 * and the cycle-safety guarantees the grouped grid relies on. */
class MediaGroupingTest {

    private fun medium(name: String, size: Long = 0, rating: Int = 0) = Medium(
        id = null, name = name, path = "/x/$name", parentPath = "/x", modified = 0, taken = 0,
        size = size, type = 1, videoDuration = 0, isFavorite = false, deletedTS = 0,
        mediaStoreId = 0, rating = rating,
    )

    private fun headers(rows: List<GroupRow>) = rows.filterIsInstance<GroupRow.SectionHeader>()

    // --- Labels ---

    @Test
    fun `ratingLabelFor renders stars and the unrated label`() {
        assertEquals("unrated", ratingLabelFor(0, "unrated"))
        assertEquals("★★★☆☆", ratingLabelFor(3, "unrated"))
        assertEquals("★★★★★", ratingLabelFor(5, "unrated"))
    }

    @Test
    fun `sizeLabelFor bucket boundaries`() {
        assertEquals("≥ 100 MB", sizeLabelFor(100L * 1024 * 1024))
        assertEquals("10–100 MB", sizeLabelFor(10L * 1024 * 1024))
        assertEquals("1–10 MB", sizeLabelFor(1L * 1024 * 1024))
        assertEquals("100 KB–1 MB", sizeLabelFor(100L * 1024))
        assertEquals("< 100 KB", sizeLabelFor(100L * 1024 - 1))
    }

    @Test
    fun `alphabetLabelFor letters, digits and blanks`() {
        assertEquals("B", alphabetLabelFor("berlin.jpg"))
        assertEquals("#", alphabetLabelFor("2020.png"))
        assertEquals("#", alphabetLabelFor(""))
        assertEquals("A", alphabetLabelFor("  ansel.jpg"))
    }

    // --- Rating groups ---

    @Test
    fun `rating groups are ordered highest first`() {
        val rows = buildRatingGroupRows(
            listOf(medium("a.jpg", rating = 0), medium("b.jpg", rating = 5), medium("c.jpg", rating = 3)),
            GroupOrder.ALPHABETICAL, "unrated",
        )
        assertEquals(listOf("★★★★★", "★★★☆☆", "unrated"), headers(rows).map { it.label })
        assertEquals(listOf(5, 3, null), headers(rows).map { it.ratingValue })
    }

    @Test
    fun `rating groups by count put the biggest bucket first`() {
        val rows = buildRatingGroupRows(
            listOf(medium("a.jpg", rating = 5), medium("b.jpg", rating = 3), medium("c.jpg", rating = 3)),
            GroupOrder.COUNT, "unrated",
        )
        assertEquals("★★★☆☆", headers(rows).first().label)
    }

    // --- Size groups ---

    @Test
    fun `size groups descend from largest bucket and keep item order within a bucket`() {
        val big = medium("big.jpg", size = 200L * 1024 * 1024)
        val small1 = medium("s1.jpg", size = 10)
        val small2 = medium("s2.jpg", size = 20)
        val rows = buildSizeGroupRows(listOf(small1, big, small2), desc = true)

        assertEquals(listOf("≥ 100 MB", "< 100 KB"), headers(rows).map { it.label })
        val smallItems = rows.filterIsInstance<GroupRow.Items>().last().media
        assertEquals(listOf("s1.jpg", "s2.jpg"), smallItems.map { it.name })
    }

    @Test
    fun `ascending size groups reverse the bucket order`() {
        val rows = buildSizeGroupRows(
            listOf(medium("big.jpg", size = 200L * 1024 * 1024), medium("s.jpg", size = 10)),
            desc = false,
        )
        assertEquals(listOf("< 100 KB", "≥ 100 MB"), headers(rows).map { it.label })
    }

    // --- Alphabet groups ---

    @Test
    fun `alphabet groups sort letters first and the hash bucket last`() {
        val rows = buildAlphabetGroupRows(
            listOf(medium("2020.png"), medium("berlin.jpg"), medium("ansel.jpg")),
            desc = false,
        )
        assertEquals(listOf("A", "B", "#"), headers(rows).map { it.label })
    }

    // --- Breadcrumb ---

    @Test
    fun `currentBreadcrumb walks up to the nearest header of each shallower depth`() {
        val rows = listOf(
            GroupRow.SectionHeader("tag:Urlaub", "Urlaub", 0, 0, 2, true, true),
            GroupRow.SectionHeader("tag:Kroatien", "Kroatien", 1, 2, 2, false, true),
            GroupRow.Items("tag:Kroatien", listOf(medium("a.jpg"))),
        )
        assertEquals("Urlaub ▸ Kroatien", currentBreadcrumb(rows, firstVisibleIndex = 2))
        assertEquals("Urlaub", currentBreadcrumb(rows, firstVisibleIndex = 0))
        assertNull(currentBreadcrumb(emptyList(), 0))
    }

    // --- Tag groups ---

    private val tagged = listOf(medium("child.jpg"), medium("parent.jpg"), medium("loose.jpg"))
    private val tagsByPath = mapOf(
        "/x/child.jpg" to listOf("Kroatien"),
        "/x/parent.jpg" to listOf("Urlaub"),
    )
    private val hierarchy = mapOf("Kroatien" to "Urlaub")

    @Test
    fun `tag groups nest children under parents and count them in totalCount`() {
        val rows = buildTagGroupRows(tagged, tagsByPath, hierarchy, GroupOrder.ALPHABETICAL, onlyTopLevelTags = false, collapsedKeys = emptySet(), untaggedLabel = "untagged")

        val urlaub = headers(rows).first { it.label == "Urlaub" }
        val kroatien = headers(rows).first { it.label == "Kroatien" }
        assertEquals(0, urlaub.depth)
        assertEquals(1, kroatien.depth)
        assertEquals(1, urlaub.exactCount)
        assertEquals(2, urlaub.totalCount)

        val untagged = headers(rows).first { it.key == "untagged" }
        assertEquals(1, untagged.exactCount)
    }

    @Test
    fun `collapsed section emits its header but no items or children`() {
        val rows = buildTagGroupRows(tagged, tagsByPath, hierarchy, GroupOrder.ALPHABETICAL, onlyTopLevelTags = false, collapsedKeys = setOf("tag:Urlaub"), untaggedLabel = "untagged")

        assertTrue(headers(rows).any { it.label == "Urlaub" && !it.isExpanded })
        assertTrue(headers(rows).none { it.label == "Kroatien" })
        assertTrue(rows.filterIsInstance<GroupRow.Items>().none { it.sectionKey == "tag:Urlaub" })
    }

    @Test
    fun `onlyTopLevelTags pools children into their root without duplicating media`() {
        val both = medium("both.jpg")
        val rows = buildTagGroupRows(
            listOf(both),
            mapOf("/x/both.jpg" to listOf("Urlaub", "Kroatien")),
            hierarchy, GroupOrder.ALPHABETICAL, onlyTopLevelTags = true, collapsedKeys = emptySet(), untaggedLabel = "untagged",
        )

        assertEquals(listOf("Urlaub"), headers(rows).map { it.label })
        assertEquals(1, rows.filterIsInstance<GroupRow.Items>().single().media.size)
    }

    @Test
    fun `cyclic tag hierarchy still shows the media instead of looping forever`() {
        val rows = buildTagGroupRows(
            listOf(medium("a.jpg")),
            mapOf("/x/a.jpg" to listOf("x")),
            mapOf("x" to "y", "y" to "x"),
            GroupOrder.ALPHABETICAL, onlyTopLevelTags = false, collapsedKeys = emptySet(), untaggedLabel = "untagged",
        )
        assertTrue(rows.filterIsInstance<GroupRow.Items>().any { it.media.single().name == "a.jpg" })
    }
}

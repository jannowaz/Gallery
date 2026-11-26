package org.fossify.gallery.compose.screens.analysis

import org.fossify.gallery.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DuplicateFinderHelpersTest {

    private fun dupFile(
        path: String,
        size: Long = 1_000L,
        rating: Int = 0,
        isFavorite: Boolean = false,
    ): DuplicateFile = DuplicateFile(
        path = path,
        name = path.substringAfterLast('/'),
        size = size,
        modified = 1_000L,
        mediaType = 1,
        rating = rating,
        isFavorite = isFavorite,
    )

    @Test
    fun `thresholdLabelRes maps every perceptual distance bucket accurately`() {
        assertEquals(R.string.dup_threshold_almost_identical, thresholdLabelRes(0))
        assertEquals(R.string.dup_threshold_almost_identical, thresholdLabelRes(2))
        assertEquals(R.string.dup_threshold_strict, thresholdLabelRes(6))
        assertEquals(R.string.dup_threshold_medium, thresholdLabelRes(10))
        assertEquals(R.string.dup_threshold_loose, thresholdLabelRes(14))
        assertEquals(R.string.dup_threshold_very_loose, thresholdLabelRes(20))
    }

    @Test
    fun `resolveTreeDocumentPath decodes primary and external volume document ids`() {
        assertEquals("/storage/emulated/0/DCIM/Camera", resolveTreeDocumentPath("primary:DCIM/Camera"))
        assertEquals("/storage/emulated/0", resolveTreeDocumentPath("primary:"))
        assertEquals("/storage/1234-5678/Pictures", resolveTreeDocumentPath("1234-5678:Pictures/"))
        assertNull(resolveTreeDocumentPath("malformed-id"))
    }

    @Test
    fun `sortAndFilterDuplicateGroups filters by folder and sorts by savings size and count`() {
        val groupSmallCountMany = DuplicateGroup(
            hash = "h1",
            files = listOf(
                dupFile("/a/1.jpg", size = 100L),
                dupFile("/a/2.jpg", size = 100L),
                dupFile("/a/3.jpg", size = 100L),
            ),
        )
        val groupLargePair = DuplicateGroup(
            hash = "h2",
            files = listOf(
                dupFile("/b/1.jpg", size = 5_000L),
                dupFile("/a/copy.jpg", size = 5_000L),
            ),
        )
        val groups = listOf(groupSmallCountMany, groupLargePair)

        assertEquals(
            listOf("h2", "h1"),
            sortAndFilterDuplicateGroups(groups, null, DuplicateSort.SAVINGS).map { it.hash },
        )
        assertEquals(
            listOf("h2", "h1"),
            sortAndFilterDuplicateGroups(groups, null, DuplicateSort.SIZE).map { it.hash },
        )
        assertEquals(
            listOf("h1", "h2"),
            sortAndFilterDuplicateGroups(groups, null, DuplicateSort.COUNT).map { it.hash },
        )
        assertEquals(
            listOf("h2"),
            sortAndFilterDuplicateGroups(groups, "/b", DuplicateSort.SAVINGS).map { it.hash },
        )
    }

    @Test
    fun `computeDuplicateDeletionSummary tracks survivors fully marked groups and rated files`() {
        val groupA = DuplicateGroup(
            hash = "a",
            files = listOf(
                dupFile("/a/1.jpg", size = 1_000L, rating = 5),
                dupFile("/a/2.jpg", size = 1_000L),
            ),
        )
        val groupB = DuplicateGroup(
            hash = "b",
            files = listOf(
                dupFile("/b/1.jpg", size = 2_000L, isFavorite = true),
                dupFile("/b/2.jpg", size = 2_000L),
            ),
        )

        val summary = computeDuplicateDeletionSummary(
            groups = listOf(groupA, groupB),
            selected = setOf("/a/1.jpg", "/b/1.jpg", "/b/2.jpg"),
        )
        assertEquals(3, summary.selectedCount)
        assertEquals(5_000L, summary.selectedBytes)
        assertEquals(1, summary.keepingCopyGroups)
        assertEquals(1, summary.fullyMarkedGroups)
        assertEquals(2, summary.ratedOrFavoriteCount)
    }
}

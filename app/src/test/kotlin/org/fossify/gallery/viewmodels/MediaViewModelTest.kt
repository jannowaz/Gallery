package org.fossify.gallery.viewmodels

import org.fossify.gallery.compose.screens.SortField
import org.fossify.gallery.helpers.TYPE_IMAGES
import org.fossify.gallery.helpers.TYPE_VIDEOS
import org.fossify.gallery.models.MediaReviewFilter
import org.fossify.gallery.models.MediaReviewStatus
import org.fossify.gallery.models.Medium
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaViewModelTest {

    private fun medium(
        path: String,
        name: String = path.substringAfterLast('/'),
        modified: Long = 1_000L,
        taken: Long = 0L,
        dateAdded: Long = 0L,
        dateSortKey: Long = 0L,
        size: Long = 100L,
        type: Int = TYPE_IMAGES,
        rating: Int = 0,
    ): Medium = Medium(
        id = null,
        name = name,
        path = path,
        parentPath = path.substringBeforeLast('/'),
        modified = modified,
        taken = taken,
        size = size,
        type = type,
        videoDuration = 0,
        isFavorite = false,
        deletedTS = 0L,
        mediaStoreId = 0L,
        rating = rating,
        dateAdded = dateAdded,
        dateSortKey = dateSortKey,
    )

    @Test
    fun `dateRangeCutoffMs computes expected cutoffs for each range bucket`() {
        val now = 1_000_000_000_000L
        val day = 86_400_000L
        assertEquals(0L, dateRangeCutoffMs(0, now))
        assertEquals(now - day, dateRangeCutoffMs(1, now))
        assertEquals(now - 7 * day, dateRangeCutoffMs(2, now))
        assertEquals(now - 30 * day, dateRangeCutoffMs(3, now))
        assertEquals(now - 365 * day, dateRangeCutoffMs(4, now))
        assertEquals(0L, dateRangeCutoffMs(99, now))
    }

    @Test
    fun `isPathUnderRoots matches exact and nested paths without matching sibling prefixes`() {
        val roots = setOf("/storage/emulated/0/DCIM/")
        assertTrue(isPathUnderRoots("/storage/emulated/0/DCIM/photo.jpg", roots))
        assertTrue(isPathUnderRoots("/storage/emulated/0/dcim/Camera/a.jpg", roots))
        assertFalse(isPathUnderRoots("/storage/emulated/0/DCIM2/photo.jpg", roots))
        assertTrue(isPathUnderRoots("/storage/emulated/0/DCIM/photo.jpg", setOf("/storage/emulated/0/DCIM/photo.jpg")))
    }

    @Test
    fun `filterOverrideMedia applies rating size type and tag match-all semantics`() {
        val photoA = medium("/dcim/a.jpg", rating = 5, size = 2_000L, type = TYPE_IMAGES)
        val photoB = medium("/dcim/b.jpg", rating = 4, size = 500L, type = TYPE_IMAGES)
        val videoC = medium("/dcim/c.mp4", rating = 5, size = 5_000L, type = TYPE_VIDEOS)
        val tags = mapOf(
            photoA.path to listOf("travel", "beach"),
            photoB.path to listOf("travel"),
            videoC.path to listOf("travel", "beach"),
        )

        val anyTagResult = filterOverrideMedia(
            list = listOf(photoA, photoB, videoC),
            filter = MediaFilter(
                rating = 4,
                minSize = 1_000L,
                typeFilter = TYPE_IMAGES,
                tagNames = setOf("travel", "snow"),
            ),
            tagsByPath = tags,
        )
        assertEquals(listOf(photoA.path), anyTagResult.map { it.path })

        val allTagsResult = filterOverrideMedia(
            list = listOf(photoA, photoB, videoC),
            filter = MediaFilter(tagNames = setOf("travel", "beach"), tagMatchAll = true),
            tagsByPath = tags,
        )
        assertEquals(listOf(photoA.path, videoC.path), allTagsResult.map { it.path })
    }

    @Test
    fun `filterOverrideMedia supports pathFilter excludePaths reviewFilter and additive additionalPaths`() {
        val inCamera = medium("/dcim/Camera/1.jpg", rating = 5)
        val excluded = medium("/dcim/Camera/Hidden/2.jpg", rating = 5)
        val manualExtra = medium("/downloads/3.jpg", rating = 0)
        val reviewedExtra = medium("/downloads/4.jpg", rating = 0)
        val reviews = mapOf(
            reviewedExtra.path to MediaReviewStatus.DECIDED,
        )

        val result = filterOverrideMedia(
            list = listOf(inCamera, excluded, manualExtra, reviewedExtra),
            filter = MediaFilter(
                rating = 4,
                pathFilter = setOf("/dcim/Camera"),
                excludePaths = setOf("/dcim/Camera/Hidden"),
                additionalPaths = setOf(manualExtra.path, reviewedExtra.path, inCamera.path),
                reviewFilter = MediaReviewFilter.UNDECIDED,
            ),
            reviewByPath = reviews,
        )
        assertEquals(listOf(inCamera.path, manualExtra.path), result.map { it.path })
    }

    @Test
    fun `sortMediaList orders by rating with modified descending secondary tiebreaker`() {
        val oldFive = medium("/a.jpg", rating = 5, modified = 100L)
        val newFive = medium("/b.jpg", rating = 5, modified = 300L)
        val lowRated = medium("/c.jpg", rating = 2, modified = 999L)

        val desc = sortMediaList(listOf(oldFive, lowRated, newFive), SortField.RATING, desc = true)
        assertEquals(listOf(newFive.path, oldFive.path, lowRated.path), desc.map { it.path })

        val asc = sortMediaList(listOf(oldFive, lowRated, newFive), SortField.RATING, desc = false)
        assertEquals(listOf(lowRated.path, newFive.path, oldFive.path), asc.map { it.path })
    }

    @Test
    fun `sortMediaList orders by date using dateSortKey taken modified fallback and by size`() {
        val bySortKey = medium("/a.jpg", dateSortKey = 500L, taken = 100L, modified = 50L, size = 10L)
        val byTaken = medium("/b.jpg", dateSortKey = 0L, taken = 400L, modified = 900L, size = 300L)
        val byModified = medium("/c.jpg", dateSortKey = 0L, taken = 0L, modified = 200L, size = 50L)

        val byDateDesc = sortMediaList(listOf(byModified, byTaken, bySortKey), SortField.DATE, desc = true)
        assertEquals(listOf(bySortKey.path, byTaken.path, byModified.path), byDateDesc.map { it.path })

        val bySizeAsc = sortMediaList(listOf(byTaken, byModified, bySortKey), SortField.SIZE, desc = false)
        assertEquals(listOf(bySortKey.path, byModified.path, byTaken.path), bySizeAsc.map { it.path })
    }

    @Test
    fun `groupMediaByMonth preserves insertion order and returns empty for empty input`() {
        assertTrue(groupMediaByMonth(emptyList()).isEmpty())
        val jan = medium("/jan.jpg", dateSortKey = 1_705_000_000_000L)
        val jan2 = medium("/jan2.jpg", dateSortKey = 1_705_100_000_000L)
        val mar = medium("/mar.jpg", dateSortKey = 1_710_000_000_000L)

        val groups = groupMediaByMonth(listOf(mar, jan, jan2))
        assertEquals(2, groups.size)
        assertEquals(listOf(mar.path), groups[0].items.map { it.path })
        assertEquals(listOf(jan.path, jan2.path), groups[1].items.map { it.path })
    }
}

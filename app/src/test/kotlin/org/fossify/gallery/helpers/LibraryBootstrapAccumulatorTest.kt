package org.fossify.gallery.helpers

import org.fossify.gallery.models.Medium
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryBootstrapAccumulatorTest {
    private fun medium(path: String, type: Int, size: Long, added: Long) = Medium(
        id = null,
        name = path.substringAfterLast('/'),
        path = path,
        parentPath = path.substringBeforeLast('/'),
        modified = added - 1,
        taken = added - 2,
        size = size,
        type = type,
        videoDuration = 0,
        isFavorite = false,
        deletedTS = 0,
        mediaStoreId = 0,
        dateAdded = added,
    )

    @Test
    fun `aggregates a directory without retaining media rows`() {
        val accumulator = LibraryBootstrapAccumulator()
        accumulator.add(medium("/storage/a/older.jpg", type = 1, size = 11, added = 10))
        accumulator.add(medium("/storage/a/newer.mp4", type = 2, size = 13, added = 20))

        val directory = accumulator.directories().single()

        assertEquals("/storage/a", directory.path)
        assertEquals("newer.mp4", directory.tmb.substringAfterLast('/'))
        assertEquals(2, directory.mediaCnt)
        assertEquals(24, directory.size)
        assertEquals(3, directory.types)
        assertEquals(20, accumulator.latestSeenTimestamp)
        assertTrue(accumulator.directories().all { it.mediaCnt > 0 })
    }
}

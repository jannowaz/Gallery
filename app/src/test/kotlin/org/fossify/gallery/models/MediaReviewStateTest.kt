package org.fossify.gallery.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

class MediaReviewStateTest {
    @Test
    fun `missing state is undecided`() {
        assertNull(null as MediaReviewStatus?)
        assertTrue((null as MediaReviewStatus?).matches(MediaReviewFilter.UNDECIDED))
    }

    @Test
    fun `a later decision replaces skipped state`() {
        val skipped = MediaReviewState("/storage/photo.jpg", MediaReviewStatus.SKIPPED.databaseValue)
        val laterDecision = skipped.copy(status = MediaReviewStatus.DECIDED.databaseValue)

        assertEquals(MediaReviewStatus.DECIDED.databaseValue, laterDecision.status)
        assertFalse(MediaReviewStatus.SKIPPED.matches(MediaReviewFilter.UNDECIDED))
        assertFalse(MediaReviewStatus.DECIDED.matches(MediaReviewFilter.UNDECIDED))
    }

    @Test
    fun `reset removes the persisted state conceptually`() {
        val previous: MediaReviewStatus? = MediaReviewStatus.SKIPPED
        val reset: MediaReviewStatus? = null

        assertEquals(MediaReviewStatus.SKIPPED, previous)
        assertNull(reset)
        assertTrue(reset.matches(MediaReviewFilter.UNDECIDED))
        assertTrue(MediaReviewStatus.DECIDED.matches(MediaReviewFilter.NONE))
    }
}

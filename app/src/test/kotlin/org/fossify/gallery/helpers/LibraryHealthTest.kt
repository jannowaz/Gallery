package org.fossify.gallery.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The threshold logic behind the "check library" action in Settings. Tested rather than eyeballed
 * because both failure directions are bad: too eager and it cries wolf on every launch over the
 * handful of files MediaStore gained mid-count, too lax and it stays silent through the exact
 * situation it was built for (3,761 files permanently missing, nothing in the UI hinting at it).
 */
class LibraryHealthTest {

    private fun health(store: Int, library: Int) = MediaRepository.LibraryHealth(store, library)

    @Test
    fun `missing is the plain difference`() {
        assertEquals(3_761, health(store = 206_432, library = 202_671).missing)
    }

    @Test
    fun `a library ahead of MediaStore reports zero rather than a negative count`() {
        // Happens routinely: the recycle bin and pending deletions can leave rows the store no
        // longer lists. "-12 media missing" would be nonsense to show a user.
        assertEquals(0, health(store = 100, library = 112).missing)
        assertFalse(health(store = 100, library = 112).isSignificant)
    }

    @Test
    fun `everyday drift between the two counts is not reported`() {
        // The two counts are taken moments apart while the device keeps taking photos and receiving
        // downloads. On the real device this sat at 8-10 across several checks.
        assertFalse(health(store = 206_444, library = 206_434).isSignificant)
        assertFalse(health(store = 206_444, library = 206_344).isSignificant)
    }

    @Test
    fun `a real sync failure is reported`() {
        assertTrue(health(store = 206_432, library = 202_671).isSignificant)
    }

    @Test
    fun `the threshold sits just above the noise band`() {
        assertFalse("100 missing is still within noise", health(store = 1_000, library = 900).isSignificant)
        assertTrue("101 missing is not", health(store = 1_000, library = 899).isSignificant)
    }

    @Test
    fun `an empty library on an empty device is healthy, not broken`() {
        assertEquals(0, health(store = 0, library = 0).missing)
        assertFalse(health(store = 0, library = 0).isSignificant)
    }
}

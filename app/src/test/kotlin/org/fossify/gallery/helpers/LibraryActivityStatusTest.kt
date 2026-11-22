package org.fossify.gallery.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class LibraryActivityStatusTest {
    @Test
    fun `running activity keeps source progress and matches`() {
        val key = "test-person-scan"
        LibraryActivityStatus.started(key, LibraryActivityKind.PERSON_SCAN, "/Pictures/Test", includeSubfolders = true)
        LibraryActivityStatus.progress(key, done = 24, total = 80, matches = 9)

        val state = LibraryActivityStatus.states.value.firstOrNull { it.key == key }
        assertNotNull(state)
        assertEquals(LibraryActivityPhase.RUNNING, state?.phase)
        assertEquals(24, state?.done)
        assertEquals(80, state?.total)
        assertEquals(9, state?.matches)
        assertEquals(true, state?.includeSubfolders)
    }
}

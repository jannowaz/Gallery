package org.fossify.gallery.helpers

import org.junit.Assert.assertEquals
import org.junit.Test

class ExplorerNavigationTest {

    @Test
    fun `builds every parent for a saved primary-storage path`() {
        assertEquals(
            listOf(
                "/storage/emulated/0",
                "/storage/emulated/0/DCIM",
                "/storage/emulated/0/DCIM/Camera",
            ),
            explorerAncestorChain(
                "/storage/emulated/0",
                "/storage/emulated/0/DCIM/Camera",
            ),
        )
    }

    @Test
    fun `keeps external paths as a single entry`() {
        assertEquals(
            listOf("/storage/1234-5678/Pictures"),
            explorerAncestorChain(
                "/storage/emulated/0",
                "/storage/1234-5678/Pictures",
            ),
        )
    }

    @Test
    fun `does not create a duplicate root entry`() {
        assertEquals(
            listOf("/storage/emulated/0"),
            explorerAncestorChain(
                "/storage/emulated/0",
                "/storage/emulated/0",
            ),
        )
    }
}

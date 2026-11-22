package org.fossify.gallery.compose.components

import org.junit.Assert.assertEquals
import org.junit.Test

class AlbumListRowTest {
    @Test
    fun `normalizes primary storage path variants`() {
        val variants = listOf(
            "/storage/emulated/0/DCIM/Camera",
            "storage/emulated/0/DCIM/Camera",
            "/emulated/0/DCIM/Camera",
            "0/DCIM/Camera",
            "/sdcard/DCIM/Camera",
        )

        variants.forEach { path ->
            assertEquals("Internal storage/DCIM", folderPathHint(path, "Internal storage"))
        }
    }

    @Test
    fun `normalizes a folder whose parent is the emulated storage root`() {
        assertEquals(
            "Internal storage",
            folderPathHint("/storage/emulated/0", "Internal storage"),
        )
    }

    @Test
    fun `keeps a useful tail for non-primary storage`() {
        assertEquals(
            "1234-5678/Pictures/Trips",
            folderPathHint("/storage/1234-5678/Pictures/Trips/October", "Internal storage"),
        )
    }
}

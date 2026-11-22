package org.fossify.gallery.compose.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FolderDialogsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `readFolderInfo counts direct files, subfolders, and byte size`() {
        val dir = tempFolder.newFolder("Holiday")
        File(dir, "a.jpg").writeBytes(ByteArray(128))
        File(dir, "b.mp4").writeBytes(ByteArray(256))
        File(dir, "Sub").mkdir()

        val info = FolderInfoReader.read(dir.absolutePath)

        assertEquals("Holiday", info.name)
        assertEquals(dir.absolutePath, info.path)
        assertEquals(2, info.directFileCount)
        assertEquals(1, info.directSubfolderCount)
        assertEquals(384L, info.totalSizeBytes)
        assertTrue(info.modifiedMillis > 0L)
    }
}

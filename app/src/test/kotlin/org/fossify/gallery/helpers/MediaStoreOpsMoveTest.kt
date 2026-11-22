package org.fossify.gallery.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class MediaStoreOpsMoveTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `isMediaStoreCollectionAllowedPath allows DCIM and Pictures for images`() {
        assertTrue(MediaStoreOps.isMediaStoreCollectionAllowedPath("DCIM/Camera", isVideo = false))
        assertTrue(MediaStoreOps.isMediaStoreCollectionAllowedPath("Pictures/Vacation/2026", isVideo = false))
        assertTrue(MediaStoreOps.isMediaStoreCollectionAllowedPath("pictures/Screenshots", isVideo = false))
        assertFalse(MediaStoreOps.isMediaStoreCollectionAllowedPath("Movies/Clips", isVideo = false))
        assertFalse(MediaStoreOps.isMediaStoreCollectionAllowedPath("Download/Instagram", isVideo = false))
        assertFalse(MediaStoreOps.isMediaStoreCollectionAllowedPath("Nsfw/Girls/Laura", isVideo = false))
        assertFalse(MediaStoreOps.isMediaStoreCollectionAllowedPath("", isVideo = false))
    }

    @Test
    fun `isMediaStoreCollectionAllowedPath allows Movies for videos but not custom roots`() {
        assertTrue(MediaStoreOps.isMediaStoreCollectionAllowedPath("Movies/Clips", isVideo = true))
        assertTrue(MediaStoreOps.isMediaStoreCollectionAllowedPath("DCIM/Camera", isVideo = true))
        assertTrue(MediaStoreOps.isMediaStoreCollectionAllowedPath("Pictures/Videos", isVideo = true))
        assertFalse(MediaStoreOps.isMediaStoreCollectionAllowedPath("Download/Videos", isVideo = true))
        assertFalse(MediaStoreOps.isMediaStoreCollectionAllowedPath("Nsfw/Clips", isVideo = true))
    }

    @Test
    fun `moveFileDirectly moves file into nested folder and preserves content and timestamp`() {
        val srcDir = tempFolder.newFolder("Download", "Piko")
        val src = File(srcDir, "photo.jpg")
        val payload = ByteArray(4096) { (it % 251).toByte() }
        src.writeBytes(payload)
        val originalTs = 1_700_000_000_000L
        src.setLastModified(originalTs)

        val dst = File(tempFolder.root, "Nsfw/Girls/Laura/photo.jpg")
        val moved = MediaStoreOps.moveFileDirectly(context = null, sourcePath = src.absolutePath, targetPath = dst.absolutePath)

        assertTrue(moved)
        assertFalse(src.exists())
        assertTrue(dst.isFile)
        assertEquals(payload.toList(), dst.readBytes().toList())
        assertEquals(originalTs, dst.lastModified())
    }

    @Test
    fun `moveFileDirectly never overwrites an existing target file`() {
        val src = File(tempFolder.root, "source.jpg").apply { writeText("new-content") }
        val dst = File(tempFolder.root, "target.jpg").apply { writeText("existing-content") }

        val moved = MediaStoreOps.moveFileDirectly(context = null, sourcePath = src.absolutePath, targetPath = dst.absolutePath)

        assertFalse(moved)
        assertTrue(src.isFile)
        assertEquals("new-content", src.readText())
        assertEquals("existing-content", dst.readText())
    }

    @Test
    fun `copyFileDirectly copies bytes and keeps source intact`() {
        val src = File(tempFolder.root, "clip.mp4").apply { writeText("video-bytes") }
        val dst = File(tempFolder.root, "CustomRoot/Sub/clip.mp4")

        val copied = MediaStoreOps.copyFileDirectly(context = null, sourcePath = src.absolutePath, targetPath = dst.absolutePath)

        assertTrue(copied)
        assertTrue(src.isFile)
        assertTrue(dst.isFile)
        assertEquals("video-bytes", dst.readText())
    }
}

package org.fossify.gallery.compose.screens.viewer

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class VideoTrimHelperTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `nextUniqueTrimmedFile creates base trimmed name when unused`() {
        val dir = tempFolder.newFolder("videos")
        val src = File(dir, "clip.mp4").apply { createNewFile() }

        val out = nextUniqueTrimmedFile(src)

        assertEquals(File(dir, "clip_trimmed.mp4"), out)
    }

    @Test
    fun `nextUniqueTrimmedFile avoids overwriting existing trimmed copies`() {
        val dir = tempFolder.newFolder("videos2")
        val src = File(dir, "clip.mp4").apply { createNewFile() }
        File(dir, "clip_trimmed.mp4").createNewFile()
        File(dir, "clip_trimmed_2.mp4").createNewFile()

        val out = nextUniqueTrimmedFile(src)

        assertEquals(File(dir, "clip_trimmed_3.mp4"), out)
    }
}

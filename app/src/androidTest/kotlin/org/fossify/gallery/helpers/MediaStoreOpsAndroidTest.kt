package org.fossify.gallery.helpers

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * On-device Android 11+ verification for [MediaStoreOps] move/copy/rename paths and All Files
 * Access detection, cleaning up all temporary MediaStore entries and files after each test.
 */
@RunWith(AndroidJUnit4::class)
class MediaStoreOpsAndroidTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val createdPaths = mutableListOf<String>()
    private val createdDirs = mutableListOf<File>()

    @After
    fun tearDown() {
        createdPaths.forEach { path ->
            runCatching {
                context.contentResolver.delete(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    "${MediaStore.MediaColumns.DATA} = ?",
                    arrayOf(path),
                )
            }
            runCatching { File(path).delete() }
        }
        createdPaths.clear()
        createdDirs.asReversed().forEach { dir ->
            runCatching { dir.deleteRecursively() }
        }
        createdDirs.clear()
    }

    private fun insertTestJpegIntoPictures(folderName: String, fileName: String, payload: ByteArray): String {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/$folderName/")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        assertNotNull("Failed to insert test MediaStore image", uri)
        context.contentResolver.openOutputStream(uri!!)?.use { it.write(payload) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
            context.contentResolver.update(uri, done, null, null)
        }
        val path = queryDataPath(uri)
        assertNotNull("Inserted MediaStore row should expose DATA path", path)
        createdPaths += path!!
        createdDirs += File(path).parentFile!!
        return path
    }

    private fun queryDataPath(uri: android.net.Uri): String? {
        return context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }

    @Test
    fun mediaStore_moveToPath_movesOwnedImageBetweenPicturesSubfolders() {
        val token = System.nanoTime()
        val payload = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte(), 1, 2, 3, 4)
        val srcPath = insertTestJpegIntoPictures("GalleryMoveSrc_$token", "move_sample.jpg", payload)
        val dstDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "GalleryMoveDst_$token")
        val dstPath = File(dstDir, "move_sample.jpg").absolutePath
        createdPaths += dstPath
        createdDirs += dstDir

        val moved = MediaStoreOps.moveToPath(context, srcPath, dstPath)

        assertTrue("Expected MediaStoreOps.moveToPath to succeed for Pictures -> Pictures", moved)
        assertFalse("Source file should no longer exist after move", File(srcPath).exists())
        assertTrue("Destination file should exist after move", File(dstPath).isFile)
        assertArrayEquals(payload, File(dstPath).readBytes())
    }

    @Test
    fun mediaStore_copyAndRenameToPath_workEndToEnd() {
        val token = System.nanoTime()
        val payload = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte(), 9, 8, 7)
        val srcPath = insertTestJpegIntoPictures("GalleryCopySrc_$token", "orig.jpg", payload)
        val dstDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "GalleryCopyDst_$token")
        val copyPath = File(dstDir, "copied.jpg").absolutePath
        val renamedPath = File(dstDir, "renamed.jpg").absolutePath
        createdPaths += copyPath
        createdPaths += renamedPath
        createdDirs += dstDir

        val copied = MediaStoreOps.copyToPath(context, srcPath, copyPath)
        assertTrue("Expected MediaStoreOps.copyToPath to succeed", copied)
        assertTrue("Source should still exist after copy", File(srcPath).isFile)
        assertTrue("Copy should exist", File(copyPath).isFile)
        assertArrayEquals(payload, File(copyPath).readBytes())

        val renamed = MediaStoreOps.renameToPath(context, copyPath, "renamed.jpg")
        assertTrue("Expected MediaStoreOps.renameToPath to succeed", renamed)
        assertFalse("Old name should be gone after rename", File(copyPath).exists())
        assertTrue("New name should exist after rename", File(renamedPath).isFile)
    }

    @Test
    fun requiresAllFilesAccessForTarget_matchesAndroidStoragePolicy() {
        val hasAllFiles = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
        assertFalse(
            MediaStoreOps.requiresAllFilesAccessForTarget(
                "/storage/emulated/0/Pictures/Album",
                isVideo = false,
            ),
        )
        assertEquals(
            !hasAllFiles,
            MediaStoreOps.requiresAllFilesAccessForTarget(
                "/storage/emulated/0/CustomRoot/Album",
                isVideo = false,
            ),
        )
    }

    @Test
    fun directFilesystemMove_preservesContentAndTimestampInAppDir() {
        val base = File(context.cacheDir, "move_direct_${System.nanoTime()}").apply { mkdirs() }
        createdDirs += base
        val src = File(base, "src/photo.jpg").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(11, 22, 33, 44, 55))
            setLastModified(1_700_000_000_000L)
        }
        val dst = File(base, "dst/nested/photo.jpg")

        assertTrue(MediaStoreOps.moveFileDirectly(context, src.absolutePath, dst.absolutePath))
        assertFalse(src.exists())
        assertTrue(dst.isFile)
        assertArrayEquals(byteArrayOf(11, 22, 33, 44, 55), dst.readBytes())
        assertEquals(1_700_000_000_000L, dst.lastModified())
    }
}

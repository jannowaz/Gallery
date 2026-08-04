package org.fossify.gallery.viewmodels

import org.fossify.gallery.compose.screens.analysis.CompressionSafety
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Locks the data-safety invariant that prevents "original deleted, no valid new file": a replacement
 * is only valid if it exists and is non-empty. These guard the precondition that both the compress
 * and optimize replace paths gate on before ever moving an original to the recycle bin.
 */
class CompressionSafetyTest {

    @get:Rule val tmp = TemporaryFolder()

    @Test fun `missing file is never a valid replacement`() {
        val missing = File(tmp.root, "nope.jpg")
        assertFalse(CompressionSafety.hasContent(missing))
        assertFalse(CompressionSafety.isValidReplacement(missing))
    }

    @Test fun `zero-byte file is never a valid replacement`() {
        val empty = File(tmp.root, "empty.jpg").apply { createNewFile() }
        assertFalse("0-byte file must never let the original be trashed", CompressionSafety.hasContent(empty))
        assertFalse(CompressionSafety.isValidReplacement(empty))
    }

    @Test fun `zero-byte file of any type is rejected`() {
        for (name in listOf("x.mp4", "x.webp", "x.png", "x.gif", "x.bin")) {
            val f = File(tmp.root, name).apply { createNewFile() }
            assertFalse("$name (0 bytes) must be invalid", CompressionSafety.isValidReplacement(f))
        }
    }

    @Test fun `non-empty file with unknown extension passes the content gate`() {
        val f = File(tmp.root, "result.bin").apply { writeBytes(ByteArray(128) { 1 }) }
        assertTrue(CompressionSafety.hasContent(f))
        // Unknown type can't be decode-checked, so a non-empty file is accepted (best assertion).
        assertTrue(CompressionSafety.isValidReplacement(f))
    }

    @Test fun `hasContent tracks existence and size exactly`() {
        val f = File(tmp.root, "grow.dat")
        assertFalse(CompressionSafety.hasContent(f))          // missing
        f.createNewFile()
        assertFalse(CompressionSafety.hasContent(f))          // 0 bytes
        f.writeBytes(byteArrayOf(1, 2, 3))
        assertTrue(CompressionSafety.hasContent(f))           // has bytes
    }
}

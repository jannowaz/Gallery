package org.fossify.gallery.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntentSecurityTest {

    @Test
    fun acceptsNormalExternalStoragePaths() {
        assertTrue(IntentSecurity.isSyntacticallySafeExternalPath("/storage/emulated/0/DCIM/Camera/IMG_001.jpg"))
        assertTrue(IntentSecurity.isSyntacticallySafeExternalPath("/storage/emulated/0/Pictures"))
        assertTrue(IntentSecurity.isSyntacticallySafeExternalPath("/storage/1234-5678/Vacation/clip.mp4"))
    }

    @Test
    fun rejectsPrivateAndSystemPaths() {
        assertFalse(
            IntentSecurity.isSyntacticallySafeExternalPath(
                "/data/data/org.fossify.gallery/shared_prefs/Prefs.xml"
            )
        )
        assertFalse(
            IntentSecurity.isSyntacticallySafeExternalPath(
                "/data/user/0/org.fossify.gallery/databases/gallery.db"
            )
        )
        assertFalse(IntentSecurity.isSyntacticallySafeExternalPath("/proc/self/environ"))
        assertFalse(IntentSecurity.isSyntacticallySafeExternalPath("/sys/class/power_supply"))
        assertFalse(IntentSecurity.isSyntacticallySafeExternalPath("/system/build.prop"))
        assertFalse(IntentSecurity.isSyntacticallySafeExternalPath("/vendor/etc/fstab"))
    }

    @Test
    fun rejectsPathTraversalNullBytesAndRelativePaths() {
        assertFalse(IntentSecurity.isSyntacticallySafeExternalPath(null))
        assertFalse(IntentSecurity.isSyntacticallySafeExternalPath(""))
        assertFalse(IntentSecurity.isSyntacticallySafeExternalPath("   "))
        assertFalse(IntentSecurity.isSyntacticallySafeExternalPath("relative/path.jpg"))
        assertFalse(IntentSecurity.isSyntacticallySafeExternalPath("/storage/emulated/0/DCIM/../../data/secret.jpg"))
        assertFalse(IntentSecurity.isSyntacticallySafeExternalPath("/storage/emulated/0/DCIM/photo.jpg\u0000.png"))
        assertFalse(IntentSecurity.isSyntacticallySafeExternalPath("/storage/emulated/0\\..\\data\\file"))
    }

    @Test
    fun sanitizesUntrustedContentDisplayNames() {
        assertEquals("photo.jpg", IntentSecurity.sanitizeSingleFilename("photo.jpg", "fallback.jpg"))
        assertEquals("Prefs.xml", IntentSecurity.sanitizeSingleFilename("../../shared_prefs/Prefs.xml", "fallback.jpg"))
        assertEquals("evil.png", IntentSecurity.sanitizeSingleFilename("..\\..\\evil.png", "fallback.jpg"))
        assertEquals("fallback.jpg", IntentSecurity.sanitizeSingleFilename("..", "fallback.jpg"))
        assertEquals("fallback.jpg", IntentSecurity.sanitizeSingleFilename(".", "fallback.jpg"))
        assertEquals("fallback.jpg", IntentSecurity.sanitizeSingleFilename("   ", "fallback.jpg"))
        assertEquals("fallback.jpg", IntentSecurity.sanitizeSingleFilename(null, "fallback.jpg"))
    }

    @Test
    fun sanitizeFilePrefixPreservesSafePrefix() {
        assertEquals("Vacation_2026", IntentSecurity.sanitizeFilePrefix("Vacation_2026"))
    }

    @Test
    fun sanitizeFilePrefixBlocksPathTraversalAndSeparators() {
        val result = IntentSecurity.sanitizeFilePrefix("../../etc/passwd")
        assertFalse(result.contains(".."))
        assertFalse(result.contains('/'))
        assertFalse(result.contains('\\'))
        assertEquals("etc_passwd", result)
    }

    @Test
    fun sanitizeFilePrefixReplacesInvalidFilenameCharacters() {
        assertEquals("a_b_c_d_e_f_g", IntentSecurity.sanitizeFilePrefix("a:b*c?d\"e<f>g"))
    }

    @Test
    fun sanitizeFilePrefixFallsBackToImgWhenBlankOrDotsOnly() {
        assertEquals("IMG", IntentSecurity.sanitizeFilePrefix(""))
        assertEquals("IMG", IntentSecurity.sanitizeFilePrefix("   "))
        assertEquals("IMG", IntentSecurity.sanitizeFilePrefix("..."))
        assertEquals("IMG", IntentSecurity.sanitizeFilePrefix(".."))
        assertEquals("IMG", IntentSecurity.sanitizeFilePrefix("///"))
    }

    @Test
    fun sanitizeFilePrefixTrimsDotsAndCapsLength() {
        assertEquals("hidden", IntentSecurity.sanitizeFilePrefix("...hidden..."))
        assertEquals(64, IntentSecurity.sanitizeFilePrefix("A".repeat(100)).length)
    }
}

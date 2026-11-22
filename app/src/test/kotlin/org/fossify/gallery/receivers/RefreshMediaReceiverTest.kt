package org.fossify.gallery.receivers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RefreshMediaReceiverTest {

    @Test
    fun `accepts standard shared-storage media paths`() {
        assertTrue(RefreshMediaReceiver.isSafeRefreshMediaPath("/storage/emulated/0/DCIM/Camera/IMG_001.jpg"))
        assertTrue(RefreshMediaReceiver.isSafeRefreshMediaPath("/storage/emulated/0/Movies/clip.MP4"))
        assertTrue(RefreshMediaReceiver.isSafeRefreshMediaPath("/sdcard/Pictures/photo.jxl"))
    }

    @Test
    fun `rejects traversal private data non-media and malformed paths`() {
        assertFalse(RefreshMediaReceiver.isSafeRefreshMediaPath(null))
        assertFalse(RefreshMediaReceiver.isSafeRefreshMediaPath(""))
        assertFalse(RefreshMediaReceiver.isSafeRefreshMediaPath("relative/photo.jpg"))
        assertFalse(RefreshMediaReceiver.isSafeRefreshMediaPath("/storage/emulated/0/DCIM/../secret.jpg"))
        assertFalse(RefreshMediaReceiver.isSafeRefreshMediaPath("/data/data/org.fossify.gallery/cache/a.jpg"))
        assertFalse(RefreshMediaReceiver.isSafeRefreshMediaPath("/storage/emulated/0/Download/readme.txt"))
        assertFalse(RefreshMediaReceiver.isSafeRefreshMediaPath("/storage/emulated/0/DCIM/photo\u0000.jpg"))
    }
}

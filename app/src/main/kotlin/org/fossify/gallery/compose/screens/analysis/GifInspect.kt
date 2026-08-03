package org.fossify.gallery.compose.screens.analysis

import java.io.BufferedInputStream
import java.io.FileInputStream
import java.io.InputStream

/**
 * Pure GIF structural inspection (no Android APIs), split out so it can be unit tested: an animated
 * GIF must never be mistaken for a static one and flattened, so the frame count is the authoritative
 * animated-or-not signal.
 */
object GifInspect {

    fun frameCount(path: String): Int = try {
        BufferedInputStream(FileInputStream(path)).use { frameCount(it) }
    } catch (_: Exception) { -1 }

    /** Counts image frames by walking the GIF block structure (each 0x2C image descriptor = one
     * frame), correctly skipping sub-block payloads so data bytes are never misread as block markers.
     * Stops as soon as a second frame is found (callers only need "animated or not"). Returns -1 if
     * the stream isn't a parseable GIF. */
    fun frameCount(s: InputStream): Int {
        val header = ByteArray(6)
        if (s.read(header) < 6 || !String(header, Charsets.US_ASCII).startsWith("GIF")) return -1
        val lsd = ByteArray(7)
        if (s.read(lsd) < 7) return -1
        val packed = lsd[4].toInt() and 0xff
        if (packed and 0x80 != 0) skipFully(s, (3 * (1 shl ((packed and 0x07) + 1))).toLong())
        var frames = 0
        var done = false
        while (!done) {
            when (s.read()) {
                -1, 0x3B -> done = true                       // EOF / trailer
                0x2C -> {                                     // image descriptor = one frame
                    frames++
                    if (frames > 1) {
                        done = true
                    } else {
                        val desc = ByteArray(9)
                        if (s.read(desc) < 9) {
                            done = true
                        } else {
                            val ip = desc[8].toInt() and 0xff
                            if (ip and 0x80 != 0) skipFully(s, (3 * (1 shl ((ip and 0x07) + 1))).toLong())
                            s.read()                          // LZW minimum code size
                            skipSubBlocks(s)
                        }
                    }
                }
                0x21 -> { s.read(); skipSubBlocks(s) }        // extension: label + sub-blocks
                else -> done = true                           // unexpected byte - stop safely
            }
        }
        return frames
    }

    private fun skipSubBlocks(s: InputStream) {
        while (true) {
            val size = s.read()
            if (size <= 0) return                             // 0x00 block terminator or EOF
            skipFully(s, size.toLong())
        }
    }

    private fun skipFully(s: InputStream, n: Long) {
        var remaining = n
        while (remaining > 0) {
            val skipped = s.skip(remaining)
            if (skipped > 0) remaining -= skipped else { if (s.read() < 0) return; remaining-- }
        }
    }
}

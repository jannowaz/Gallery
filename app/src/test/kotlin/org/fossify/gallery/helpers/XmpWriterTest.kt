package org.fossify.gallery.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** [XmpWriter] is pure JVM file I/O, so the full write→read roundtrip (sidecar and embedded JPEG)
 * is testable without a device. These pin down the data-loss-critical behaviors: roundtrip
 * fidelity, preserving foreign XMP fields on rewrite, single-segment JPEG rewrites, legacy sidecar
 * migration, and cycle safety in the tag hierarchy. */
class XmpWriterTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun mediaFile(name: String, content: ByteArray = byteArrayOf(1, 2, 3)): File =
        File(tmp.root, name).apply { writeBytes(content) }

    /** Minimal structurally valid JPEG: SOI + APP0(JFIF) + payload + EOI. */
    private fun minimalJpeg(name: String): File {
        val app0Data = "JFIF".toByteArray() + byteArrayOf(0, 1, 2, 0, 0, 1, 0, 1, 0, 0)
        val app0 = byteArrayOf(0xFF.toByte(), 0xE0.toByte(), 0, (app0Data.size + 2).toByte()) + app0Data
        val payload = byteArrayOf(0x11, 0x22, 0x33, 0x44)
        val bytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte()) + app0 + payload +
            byteArrayOf(0xFF.toByte(), 0xD9.toByte())
        return mediaFile(name, bytes)
    }

    private fun countOccurrences(data: ByteArray, pattern: ByteArray): Int {
        var count = 0
        outer@ for (i in 0..data.size - pattern.size) {
            for (j in pattern.indices) if (data[i + j] != pattern[j]) continue@outer
            count++
        }
        return count
    }

    // --- Sidecar roundtrip ---

    @Test
    fun `sidecar write and read roundtrip tags and rating`() {
        val file = mediaFile("photo.png")
        assertTrue(XmpWriter.write(file.absolutePath, listOf("beach", "sunset"), 4))

        val data = XmpWriter.read(file.absolutePath)
        assertEquals(listOf("beach", "sunset"), data.tags)
        assertEquals(4, data.rating)
    }

    @Test
    fun `sidecar uses replace-style name when no sibling collides`() {
        val file = mediaFile("photo.png")
        XmpWriter.write(file.absolutePath, listOf("beach"), 0)
        assertTrue(File(tmp.root, "photo.xmp").exists())
    }

    @Test
    fun `sidecar uses append-style name when a sibling shares the base name`() {
        mediaFile("img.dng")
        val file = mediaFile("img.png")
        XmpWriter.write(file.absolutePath, listOf("beach"), 0)
        assertTrue(File(tmp.root, "img.png.xmp").exists())
        assertFalse(File(tmp.root, "img.xmp").exists())
    }

    @Test
    fun `write returns false for a missing file`() {
        assertFalse(XmpWriter.write(File(tmp.root, "nope.png").absolutePath, listOf("x"), 1))
    }

    @Test
    fun `special characters in tags survive the xml roundtrip`() {
        val file = mediaFile("photo.png")
        val tricky = listOf("Tom & Jerry", "a<b", "quo\"te")
        assertTrue(XmpWriter.write(file.absolutePath, tricky, 0))
        assertEquals(tricky, XmpWriter.read(file.absolutePath).tags)
    }

    // --- Foreign-field preservation ---

    @Test
    fun `foreign xmp fields survive a rewrite, owned fields are replaced`() {
        val file = mediaFile("photo.png")
        File(tmp.root, "photo.xmp").writeText(
            """<?xpacket begin="" id="W5M0MpCehiHzreSzNTczkc9d"?>
<x:xmpmeta xmlns:x="adobe:ns:meta/">
  <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
    <rdf:Description rdf:about="" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:xmp="http://ns.adobe.com/xap/1.0/">
      <dc:creator><rdf:Seq><rdf:li>Jane</rdf:li></rdf:Seq></dc:creator>
      <xmp:Rating>2</xmp:Rating>
    </rdf:Description>
  </rdf:RDF>
</x:xmpmeta>
<?xpacket end="w"?>"""
        )

        assertTrue(XmpWriter.write(file.absolutePath, listOf("beach"), 5))

        val sidecarText = File(tmp.root, "photo.xmp").readText()
        assertTrue("foreign dc:creator must survive", sidecarText.contains("Jane"))
        assertFalse("old owned rating must be stripped", sidecarText.contains("<xmp:Rating>2</xmp:Rating>"))

        val data = XmpWriter.read(file.absolutePath)
        assertEquals(listOf("beach"), data.tags)
        assertEquals(5, data.rating)
    }

    @Test
    fun `write without hierarchy leaves an existing hierarchicalSubject block alone`() {
        val file = mediaFile("photo.png")
        File(tmp.root, "photo.xmp").writeText(
            """<?xpacket begin="" id="W5M0MpCehiHzreSzNTczkc9d"?>
<x:xmpmeta xmlns:x="adobe:ns:meta/">
  <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
    <rdf:Description rdf:about="" xmlns:lr="http://ns.adobe.com/lightroom/1.0/">
      <lr:hierarchicalSubject><rdf:Bag><rdf:li>Places|Berlin</rdf:li></rdf:Bag></lr:hierarchicalSubject>
    </rdf:Description>
  </rdf:RDF>
</x:xmpmeta>
<?xpacket end="w"?>"""
        )

        assertTrue(XmpWriter.write(file.absolutePath, listOf("Berlin"), 3))

        assertTrue(File(tmp.root, "photo.xmp").readText().contains("Places|Berlin"))
        assertEquals("Places", XmpWriter.read(file.absolutePath).hierarchy["Berlin"])
    }

    // --- Hierarchy ---

    @Test
    fun `hierarchy chains roundtrip through hierarchicalSubject`() {
        val file = mediaFile("photo.png")
        val hierarchy = mapOf("Berlin" to "Germany", "Germany" to "Places")
        assertTrue(XmpWriter.write(file.absolutePath, listOf("Berlin"), 0, hierarchy))

        val data = XmpWriter.read(file.absolutePath)
        assertTrue(data.tags.contains("Berlin"))
        assertEquals("Germany", data.hierarchy["Berlin"])
        assertEquals("Places", data.hierarchy["Germany"])
    }

    @Test
    fun `cyclic hierarchy does not hang the writer`() {
        val file = mediaFile("photo.png")
        assertTrue(XmpWriter.write(file.absolutePath, listOf("a"), 0, mapOf("a" to "b", "b" to "a")))
        assertTrue(XmpWriter.read(file.absolutePath).tags.contains("a"))
    }

    // --- JPEG embedding ---

    @Test
    fun `jpeg write embeds xmp and preserves the image bytes`() {
        val file = minimalJpeg("photo.jpg")
        assertTrue(XmpWriter.write(file.absolutePath, listOf("beach"), 4))

        val bytes = file.readBytes()
        assertEquals(0xFF.toByte(), bytes[0])
        assertEquals(0xD8.toByte(), bytes[1])
        assertEquals("payload must survive", 1, countOccurrences(bytes, byteArrayOf(0x11, 0x22, 0x33, 0x44)))
        assertEquals(0xD9.toByte(), bytes[bytes.size - 1])

        val data = XmpWriter.read(file.absolutePath)
        assertEquals(listOf("beach"), data.tags)
        assertEquals(4, data.rating)
    }

    @Test
    fun `second jpeg write replaces the xmp segment instead of adding another`() {
        val file = minimalJpeg("photo.jpg")
        assertTrue(XmpWriter.write(file.absolutePath, listOf("old"), 1))
        assertTrue(XmpWriter.write(file.absolutePath, listOf("new"), 2))

        val header = "http://ns.adobe.com/xap/1.0/".toByteArray() + byteArrayOf(0)
        assertEquals(1, countOccurrences(file.readBytes(), header))

        val data = XmpWriter.read(file.absolutePath)
        assertEquals(listOf("new"), data.tags)
        assertEquals(2, data.rating)
    }

    @Test
    fun `jpeg write leaves no temp file behind`() {
        val file = minimalJpeg("photo.jpg")
        assertTrue(XmpWriter.write(file.absolutePath, listOf("beach"), 0))
        assertFalse(File("${file.absolutePath}.tmp").exists())
    }

    // --- Legacy sidecar migration ---

    @Test
    fun `legacy rating-only sidecar is still readable`() {
        // Regression: a non-blank legacy sidecar used to be fed to the XML parser, which silently
        // returned an empty result - old ratings were lost even though the migration code existed.
        val file = mediaFile("img.png")
        File(tmp.root, "img.png.xmp").writeText("4")
        assertEquals(4, XmpWriter.read(file.absolutePath).rating)
    }

    @Test
    fun `legacy comma-separated tag sidecar is still readable`() {
        val file = mediaFile("img.png")
        File(tmp.root, "img.png.xmp").writeText("beach, sunset")
        assertEquals(listOf("beach", "sunset"), XmpWriter.read(file.absolutePath).tags)
    }

    // --- sanitizeTag ---

    @Test
    fun `sanitizeTag passes plain tags through`() {
        assertEquals("beach", XmpWriter.sanitizeTag(" beach "))
    }

    @Test
    fun `sanitizeTag decodes decimal utf-16le byte lists`() {
        assertEquals("das", XmpWriter.sanitizeTag("100 0 97 0 115 0"))
        // Odd byte count: trailing null was dropped by whoever wrote it - must be padded, not crash.
        assertEquals("da", XmpWriter.sanitizeTag("100 0 97"))
    }

    @Test
    fun `sanitizeTag strips embedded nulls`() {
        assertEquals("abcdef", XmpWriter.sanitizeTag("abc" + 0.toChar() + "def"))
    }
}

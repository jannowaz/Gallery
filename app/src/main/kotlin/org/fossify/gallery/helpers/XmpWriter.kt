package org.fossify.gallery.helpers

import java.io.File
import java.io.RandomAccessFile

object XmpWriter {
    private const val XMP_NS = "http://ns.adobe.com/xap/1.0/"
    private const val DC_NS = "http://purl.org/dc/elements/1.1/"
    private const val MAX_SCAN_BYTES = 2L * 1024 * 1024 // 2MB — XMP is always at the JPEG header

    data class XmpData(
        val tags: List<String> = emptyList(),
        val rating: Int = 0,
    )

    fun read(path: String): XmpData {
        if (path.isBlank()) return XmpData()
        val file = File(path)
        if (!file.exists()) return XmpData()
        val isJpeg = file.extension.lowercase() in setOf("jpg", "jpeg")
        val raw: String = if (isJpeg) readXmpFromJpeg(file) else readXmpFromSidecar(file)
        if (raw.isBlank()) return tryMigrateOldFormat(file)
        return parseXmp(raw)
    }

    fun write(path: String, tags: List<String>, rating: Int) {
        val file = File(path)
        if (!file.exists()) return
        val xmpBytes = buildXmpPacket(tags, rating)
        val isJpeg = file.extension.lowercase() in setOf("jpg", "jpeg")
        if (isJpeg) writeXmpToJpeg(file, xmpBytes) else writeXmpSidecar(file, xmpBytes)
    }

    private fun parseXmp(raw: String): XmpData {
        val tags = mutableListOf<String>()
        val subjectMatch = Regex("<dc:subject>\\s*<rdf:Bag>\\s*(.*?)\\s*</rdf:Bag>\\s*</dc:subject>", RegexOption.DOT_MATCHES_ALL)
            .find(raw)
        if (subjectMatch != null) {
            val bagContent = subjectMatch.groupValues[1]
            val tagRegex = Regex("<rdf:li>([^<]+)</rdf:li>")
            tagRegex.findAll(bagContent).forEach { tags.add(it.groupValues[1]) }
        }
        val rating = Regex("<xmp:Rating>(\\d+)</xmp:Rating>").find(raw)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        return XmpData(tags = tags, rating = rating)
    }

    private fun tryMigrateOldFormat(file: File): XmpData {
        val sidecar = File("${file.absolutePath}.xmp")
        if (!sidecar.exists()) return XmpData()
        val text = sidecar.readText().trim()
        if (text.isBlank()) return XmpData()
        val rating = text.toIntOrNull()
        if (rating != null) return XmpData(rating = rating)
        val tags = text.split(",").map { it.trim() }.filter { it.isNotBlank() }
        if (tags.isNotEmpty()) return XmpData(tags = tags)
        return XmpData()
    }

    private fun buildXmpPacket(tags: List<String>, rating: Int): ByteArray {
        val tagXml = if (tags.isNotEmpty()) {
            tags.joinToString("\n") { "          <rdf:li>${xmlEscape(it)}</rdf:li>" }
        } else ""
        val xmp = """<?xpacket begin="" id="W5M0MpCehiHzreSzNTczkc9d"?>
<x:xmpmeta xmlns:x="adobe:ns:meta/">
  <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
    <rdf:Description rdf:about=""
        xmlns:xmp="$XMP_NS"
        xmlns:dc="$DC_NS">
      ${
            if (rating > 0) "      <xmp:Rating>$rating</xmp:Rating>" else ""
        }
      ${
            if (tags.isNotEmpty()) """      <dc:subject>
        <rdf:Bag>
$tagXml
        </rdf:Bag>
      </dc:subject>""" else ""
        }
    </rdf:Description>
  </rdf:RDF>
</x:xmpmeta>
<?xpacket end="w"?>"""
        return xmp.toByteArray(Charsets.UTF_8)
    }

    private fun xmlEscape(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    // --- JPEG embedding with streaming (no full-file read) ---

    private fun readXmpFromJpeg(file: File): String {
        try {
            RandomAccessFile(file, "r").use { raf ->
                val readLen = minOf(raf.length(), MAX_SCAN_BYTES).toInt()
                val buffer = ByteArray(readLen)
                raf.readFully(buffer)
                val xmpHeader = "http://ns.adobe.com/xap/1.0/\u0000".toByteArray()
                val idx = findSequence(buffer, xmpHeader)
                if (idx >= 0) {
                    val start = idx + xmpHeader.size
                    val end = findSequence(buffer, "<?xpacket end=".toByteArray())
                    if (end > start) return String(buffer, start, end - start, Charsets.UTF_8)
                }
            }
        } catch (_: Exception) { }
        return ""
    }

    private fun readXmpFromSidecar(file: File): String {
        val sidecar = File("${file.absolutePath}.xmp")
        if (!sidecar.exists()) return ""
        return try { sidecar.readText() } catch (_: Exception) { "" }
    }

    private fun writeXmpSidecar(file: File, xmpData: ByteArray) {
        try { File("${file.absolutePath}.xmp").writeBytes(xmpData) } catch (_: Exception) { }
    }

    /**
     * Streaming write: scans the first [MAX_SCAN_BYTES] of the JPEG for an existing XMP APP1 segment.
     * If found, replaces it. Otherwise inserts after all APP marker segments.
     * Uses a temp file to avoid loading the entire image into memory.
     */
    private fun writeXmpToJpeg(file: File, xmpData: ByteArray) {
        try {
            val xmpHeader = "http://ns.adobe.com/xap/1.0/\u0000".toByteArray()
            val newApp1 = buildApp1Segment(xmpData)
            val scanLen = minOf(file.length(), MAX_SCAN_BYTES).toInt()
            val scanBuf = ByteArray(scanLen)

            RandomAccessFile(file, "r").use { raf ->
                raf.readFully(scanBuf)
            }

            // Locate existing XMP position, or compute insertion point after APP markers
            data class SegmentPos(val start: Int, val length: Int)

            val existingXmp: SegmentPos? = run {
                val idx = findSequence(scanBuf, xmpHeader)
                if (idx >= 0) {
                    val start = idx - 29
                    if (start >= 0 && scanBuf[start] == 0xFF.toByte() && scanBuf[start + 1] == 0xE1.toByte()) {
                        val segLen = ((scanBuf[start + 2].toInt() and 0xFF) shl 8) or (scanBuf[start + 3].toInt() and 0xFF)
                        SegmentPos(start, 2 + segLen)
                    } else null
                } else null
            }

            val insertPos: Int = if (existingXmp != null) {
                existingXmp.start
            } else {
                // Walk past all APP marker segments (0xFFE0 – 0xFFEF)
                var pos = 2
                while (pos + 3 < scanBuf.size) {
                    if (scanBuf[pos] == 0xFF.toByte() && (scanBuf[pos + 1].toInt() and 0xFF) in 0xE0..0xEF) {
                        val segLen = ((scanBuf[pos + 2].toInt() and 0xFF) shl 8) or (scanBuf[pos + 3].toInt() and 0xFF)
                        pos += 2 + segLen
                    } else break
                }
                pos
            }

            val tempFile = File("${file.absolutePath}.tmp")

            RandomAccessFile(file, "r").use { input ->
                tempFile.outputStream().buffered().use { output ->
                    val buf = ByteArray(64 * 1024)

                    // Copy bytes before the target position
                    var remaining = insertPos.toLong()
                    while (remaining > 0) {
                        val chunk = input.read(buf, 0, minOf(buf.size, remaining.toInt()))
                        if (chunk <= 0) break
                        output.write(buf, 0, chunk)
                        remaining -= chunk
                    }

                    // Write new XMP segment
                    output.write(newApp1)

                    // Skip old XMP segment if replacing
                    if (existingXmp != null) {
                        input.seek((existingXmp.start + existingXmp.length).toLong())
                    }

                    // Copy the rest of the file
                    var bytesRead: Int
                    while (input.read(buf).also { bytesRead = it } > 0) {
                        output.write(buf, 0, bytesRead)
                    }
                }
            }

            // Atomic rename
            if (tempFile.exists() && tempFile.length() > 0) {
                file.delete()
                tempFile.renameTo(file)
            }
        } catch (_: Exception) { }
    }

    private fun buildApp1Segment(xmpData: ByteArray): ByteArray {
        val header = "http://ns.adobe.com/xap/1.0/\u0000".toByteArray()
        val app1Data = header + xmpData
        val len = app1Data.size + 2
        val segLen = byteArrayOf(((len shr 8) and 0xFF).toByte(), (len and 0xFF).toByte())
        return byteArrayOf(0xFF.toByte(), 0xE1.toByte()) + segLen + app1Data
    }

    private fun findSequence(data: ByteArray, pattern: ByteArray): Int {
        outer@ for (i in 0..data.size - pattern.size) {
            for (j in pattern.indices) { if (data[i + j] != pattern[j]) continue@outer }
            return i
        }
        return -1
    }
}

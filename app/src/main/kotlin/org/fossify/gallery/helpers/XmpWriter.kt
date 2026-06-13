package org.fossify.gallery.helpers

import java.io.File
import java.io.RandomAccessFile

object XmpWriter {
    private const val XMP_NS = "http://ns.adobe.com/xap/1.0/"
    private const val DC_NS = "http://purl.org/dc/elements/1.1/"

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
        // Only extract <rdf:li> values that are inside <dc:subject> (actual tags, not other XMP list data)
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

    // --- JPEG embedding (reused from RatingWriter) ---

    private fun writeXmpToJpeg(file: File, xmpData: ByteArray) {
        try {
            RandomAccessFile(file, "rw").use { raf ->
                val buffer = ByteArray(raf.length().toInt())
                raf.readFully(buffer)
                raf.seek(0)
                val xmpHeader = "http://ns.adobe.com/xap/1.0/\u0000".toByteArray()
                val existingIndex = findSequence(buffer, xmpHeader)
                val newData = if (existingIndex >= 0) {
                    val start = existingIndex - 29
                    if (start >= 0 && buffer[start] == 0xFF.toByte() && buffer[start + 1] == 0xE1.toByte()) {
                        val oldLen = ((buffer[start + 2].toInt() and 0xFF) shl 8) or (buffer[start + 3].toInt() and 0xFF)
                        val newApp1 = buildApp1Segment(xmpData)
                        buffer.copyOf(start) + newApp1 + buffer.copyOfRange(start + 2 + oldLen, buffer.size)
                    } else {
                        insertAfterHeader(buffer, xmpData)
                    }
                } else {
                    insertAfterHeader(buffer, xmpData)
                }
                raf.setLength(newData.size.toLong())
                raf.write(newData)
            }
        } catch (_: Exception) { }
    }

    private fun insertAfterHeader(buffer: ByteArray, xmpData: ByteArray): ByteArray {
        var pos = 2
        while (pos < buffer.size - 1) {
            if (buffer[pos] == 0xFF.toByte() && buffer[pos + 1].toInt() and 0xFF >= 0xE0) {
                val segLen = ((buffer[pos + 2].toInt() and 0xFF) shl 8) or (buffer[pos + 3].toInt() and 0xFF)
                pos += 2 + segLen
            } else break
        }
        return buffer.copyOf(pos) + buildApp1Segment(xmpData) + buffer.copyOfRange(pos, buffer.size)
    }

    private fun buildApp1Segment(xmpData: ByteArray): ByteArray {
        val header = "http://ns.adobe.com/xap/1.0/\u0000".toByteArray()
        val app1Data = header + xmpData
        val len = app1Data.size + 2
        val segLen = byteArrayOf(((len shr 8) and 0xFF).toByte(), (len and 0xFF).toByte())
        return byteArrayOf(0xFF.toByte(), 0xE1.toByte()) + segLen + app1Data
    }

    private fun readXmpFromJpeg(file: File): String {
        try {
            val data = file.readBytes()
            val xmpHeader = "http://ns.adobe.com/xap/1.0/\u0000".toByteArray()
            val idx = findSequence(data, xmpHeader)
            if (idx >= 0) {
                val start = idx + xmpHeader.size
                val end = findSequence(data, "<?xpacket end=".toByteArray())
                if (end > start) return String(data, start, end - start, Charsets.UTF_8)
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

    private fun findSequence(data: ByteArray, pattern: ByteArray): Int {
        outer@ for (i in 0..data.size - pattern.size) {
            for (j in pattern.indices) { if (data[i + j] != pattern[j]) continue@outer }
            return i
        }
        return -1
    }
}

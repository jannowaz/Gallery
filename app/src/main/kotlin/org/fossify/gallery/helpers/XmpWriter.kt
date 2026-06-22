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
        val hierarchy: Map<String, String> = emptyMap(),
    )

    fun read(path: String): XmpData {
        if (path.isBlank()) return XmpData()
        val file = File(path)
        if (!file.exists()) return XmpData()
        val isJpeg = file.extension.lowercase() in setOf("jpg", "jpeg")
        val raw: String = if (isJpeg) readXmpFromJpeg(file) else readXmpFromSidecar(file)
        var data = if (raw.isBlank()) tryMigrateOldFormat(file) else parseXmp(raw)
        // Fall back to IPTC / Windows (XPKeywords) keywords written by other apps (image formats only)
        if (data.tags.isEmpty() && file.extension.lowercase() in IMAGE_META_EXTS) {
            val extra = readExternalKeywords(file)
            if (extra.isNotEmpty()) data = data.copy(tags = extra)
        }
        return data.copy(tags = data.tags.map(::sanitizeTag).filter { it.isNotBlank() }.distinct(), hierarchy = data.hierarchy)
    }

    /**
     * Some files store keywords as a UTF-16LE byte array that other readers render as a
     * space-separated list of decimal byte values (e.g. "100 0 97 0 115 0 103" → "das…"). Decode
     * those back into the real text and strip any stray UTF-16 null padding.
     */
    private fun sanitizeTag(raw: String): String {
        var t = raw.trim()
        val tokens = t.split(Regex(" +"))
        if (tokens.size >= 2 && tokens.contains("0") && tokens.all { tok -> tok.toIntOrNull()?.let { it in 0..255 } == true }) {
            runCatching {
                val bytes = tokens.map { it.toInt().toByte() }.toByteArray()
                val decoded = String(bytes, Charsets.UTF_16LE).trim('\u0000', ' ')
                if (decoded.isNotBlank()) t = decoded
            }
        }
        if (t.contains('\u0000')) t = t.replace("\u0000", "")
        return t.trim()
    }

    private val IMAGE_META_EXTS = setOf("jpg", "jpeg", "tiff", "tif", "png", "webp", "heic", "heif")

    private fun readExternalKeywords(file: File): List<String> {
        return try {
            val md = com.drew.imaging.ImageMetadataReader.readMetadata(file)
            val tags = LinkedHashSet<String>()
            md.getFirstDirectoryOfType(com.drew.metadata.iptc.IptcDirectory::class.java)?.keywords
                ?.forEach { it.trim().takeIf(String::isNotBlank)?.let(tags::add) }
            md.getFirstDirectoryOfType(com.drew.metadata.exif.ExifIFD0Directory::class.java)
                ?.let { decodeXpKeywords(it) }
                ?.split(';', ',')?.forEach { it.trim().takeIf(String::isNotBlank)?.let(tags::add) }
            tags.toList()
        } catch (_: Throwable) { emptyList() }
    }

    /**
     * Windows XPKeywords (EXIF 0x9C9E) is stored as a UTF-16LE byte array. metadata-extractor's
     * getString() renders it as a space-separated list of raw byte values (e.g. "65 0 110 0 ..."),
     * so decode the raw bytes instead. Falls back to decoding the numeric string if that's all we get.
     */
    private fun decodeXpKeywords(ifd0: com.drew.metadata.exif.ExifIFD0Directory): String? {
        val tag = com.drew.metadata.exif.ExifIFD0Directory.TAG_WIN_KEYWORDS
        ifd0.getByteArray(tag)?.let { raw ->
            return try { String(raw, Charsets.UTF_16LE).trim('\u0000', ' ') } catch (_: Exception) { null }
        }
        val s = ifd0.getString(tag) ?: return null
        if (Regex("^\\s*\\d+(\\s+\\d+)+\\s*$").matches(s)) {
            return try {
                val bytes = s.trim().split(Regex("\\s+")).map { it.toInt().toByte() }.toByteArray()
                String(bytes, Charsets.UTF_16LE).trim('\u0000', ' ')
            } catch (_: Exception) { null }
        }
        return s
    }

    fun write(path: String, tags: List<String>, rating: Int) {
        val file = File(path)
        if (!file.exists()) return
        val xmpBytes = buildXmpPacket(tags, rating)
        val isJpeg = file.extension.lowercase() in setOf("jpg", "jpeg")
        if (isJpeg) writeXmpToJpeg(file, xmpBytes) else writeXmpSidecar(file, xmpBytes)
    }

    private fun parseXmp(raw: String): XmpData {
        val tags = LinkedHashSet<String>()
        val liRegex = Regex("<rdf:li[^>]*>([^<]+)</rdf:li>")
        // Standard keywords: dc:subject (rdf:Bag or rdf:Seq, with or without attributes)
        Regex("<dc:subject[^>]*>(.*?)</dc:subject>", RegexOption.DOT_MATCHES_ALL).findAll(raw).forEach { m ->
            liRegex.findAll(m.groupValues[1]).forEach { tags.add(it.groupValues[1].trim()) }
        }
        // Lightroom hierarchical keywords — keep leaf tags and build the parent→child hierarchy.
        val hierarchy = mutableMapOf<String, String>()
        Regex("hierarchicalSubject[^>]*>(.*?)</[A-Za-z0-9]+:hierarchicalSubject>", RegexOption.DOT_MATCHES_ALL).findAll(raw).forEach { m ->
            liRegex.findAll(m.groupValues[1]).forEach {
                val full = it.groupValues[1].trim()
                val parts = full.split("|").map { it.trim() }.filter { it.isNotBlank() }
                if (parts.isNotEmpty()) tags.add(parts.last())
                for (i in 1 until parts.size) {
                    hierarchy[parts[i]] = parts[i - 1]
                }
            }
        }
        // Microsoft Photo keyword lists
        Regex("LastKeyword(?:XMP|IPTC)[^>]*>(.*?)</[A-Za-z0-9]+:LastKeyword(?:XMP|IPTC)>", RegexOption.DOT_MATCHES_ALL).findAll(raw).forEach { m ->
            liRegex.findAll(m.groupValues[1]).forEach { tags.add(it.groupValues[1].substringAfterLast('|').trim()) }
        }
        val rating = Regex("<xmp:Rating>(\\d+)</xmp:Rating>").find(raw)?.groupValues?.get(1)?.toIntOrNull()
            ?: Regex("xmp:Rating=\"(\\d+)\"").find(raw)?.groupValues?.get(1)?.toIntOrNull()
            ?: 0
        return XmpData(tags = tags.filter { it.isNotBlank() }, rating = rating, hierarchy = hierarchy)
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
                    val start = idx - 4
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

            // Safe replace: keep the original as a backup until the new file is in place
            if (tempFile.exists() && tempFile.length() > 0) {
                val backup = File("${file.absolutePath}.bak")
                if (file.renameTo(backup)) {
                    if (tempFile.renameTo(file)) {
                        backup.delete()
                    } else {
                        backup.renameTo(file)
                        tempFile.delete()
                    }
                } else if (!tempFile.renameTo(file)) {
                    tempFile.inputStream().use { ins -> file.outputStream().use { outs -> ins.copyTo(outs) } }
                    tempFile.delete()
                }
            } else {
                tempFile.delete()
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

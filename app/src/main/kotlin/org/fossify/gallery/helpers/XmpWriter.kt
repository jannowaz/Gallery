package org.fossify.gallery.helpers

import java.io.File
import java.io.RandomAccessFile

object XmpWriter {
    private const val XMP_NS = "http://ns.adobe.com/xap/1.0/"
    private const val DC_NS = "http://purl.org/dc/elements/1.1/"
    private const val LR_NS = "http://ns.adobe.com/lightroom/1.0/"
    private const val MAX_SCAN_BYTES = 2L * 1024 * 1024 // 2MB — XMP is always at the JPEG header

    // Built via Char(0)/byteArrayOf(0) instead of a unicode-null-escaped string literal, so the
    // source file itself stays plain ASCII/UTF-8 rather than containing an embedded control byte.
    private val NUL_CHAR = 0.toChar()
    private val XMP_APP1_HEADER = XMP_NS.toByteArray() + byteArrayOf(0)

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
    fun sanitizeTag(raw: String): String {
        var t = raw.trim()
        val tokens = t.split(Regex("[\\s,;]+"))
        if (tokens.size >= 2 && tokens.contains("0") && tokens.all { tok -> tok.toIntOrNull()?.let { it in 0..255 } == true }) {
            runCatching {
                val bytes = tokens.map { it.toInt().toByte() }.toByteArray()
                // UTF-16LE needs an even byte count; if the last null was dropped, pad with 0.
                val fullBytes = if (bytes.size % 2 == 1) bytes + byteArrayOf(0) else bytes
                val decoded = String(fullBytes, Charsets.UTF_16LE).trim(NUL_CHAR, ' ')
                if (decoded.isNotBlank()) t = decoded
            }
        }
        if (t.contains(NUL_CHAR)) t = t.replace(NUL_CHAR.toString(), "")
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
            return try { String(raw, Charsets.UTF_16LE).trim(NUL_CHAR, ' ') } catch (_: Exception) { null }
        }
        val s = ifd0.getString(tag) ?: return null
        if (Regex("^\\s*\\d+(\\s+\\d+)+\\s*$").matches(s)) {
            return try {
                val bytes = s.trim().split(Regex("\\s+")).map { it.toInt().toByte() }.toByteArray()
                String(bytes, Charsets.UTF_16LE).trim(NUL_CHAR, ' ')
            } catch (_: Exception) { null }
        }
        return s
    }

    /**
     * @param hierarchy child→parent tag map (e.g. from `Config.tagHierarchy`) used to also emit
     * `lr:hierarchicalSubject` (the Lightroom/digiKam-compatible hierarchy field) for tags that have
     * a known parent chain. Pass `emptyMap()` (the default) for rating-only writes so any
     * hierarchicalSubject block already present in the file (written by another app) is left alone
     * instead of being stripped.
     */
    /** Returns whether the write actually reached disk - callers must not update the DB/cache as
     * if a tag/rating change took effect when this is false, or the two silently disagree forever
     * (see MediaRepository.updateRating/addTag/removeTag/writeRatingXmp). */
    fun write(path: String, tags: List<String>, rating: Int, hierarchy: Map<String, String> = emptyMap()): Boolean {
        val file = File(path)
        if (!file.exists()) return false
        val isJpeg = file.extension.lowercase() in setOf("jpg", "jpeg")
        // Read-modify-write instead of regenerating from scratch: an existing XMP packet can carry
        // fields this app knows nothing about (GPS, captions, copyright, another app's own
        // hierarchicalSubject/TagsList) - only the fields we actually own below are touched, so
        // everything else round-trips untouched instead of being silently dropped.
        val existingRaw = if (isJpeg) readXmpFromJpeg(file) else readXmpFromSidecar(file)
        val xmpBytes = buildXmpPacket(existingRaw, tags, rating, hierarchy)
        return if (isJpeg) writeXmpToJpeg(file, xmpBytes) else writeXmpSidecar(file, xmpBytes)
    }

    private fun parseXmp(raw: String): XmpData {
        val tags = LinkedHashSet<String>()
        val liRegex = Regex("<rdf:li[^>]*>([^<]+)</rdf:li>")
        // Standard keywords: dc:subject (rdf:Bag or rdf:Seq, with or without attributes)
        Regex("<dc:subject[^>]*>(.*?)</dc:subject>", RegexOption.DOT_MATCHES_ALL).findAll(raw).forEach { m ->
            liRegex.findAll(m.groupValues[1]).forEach { tags.add(it.groupValues[1].trim()) }
        }
        val hierarchy = mutableMapOf<String, String>()
        // Lightroom hierarchical keywords ("Places|Germany|Berlin") — keep the leaf as the file's tag
        // and record each parent→child step in the hierarchy map.
        Regex("hierarchicalSubject[^>]*>(.*?)</[A-Za-z0-9]+:hierarchicalSubject>", RegexOption.DOT_MATCHES_ALL).findAll(raw).forEach { m ->
            liRegex.findAll(m.groupValues[1]).forEach {
                addHierarchicalChain(it.groupValues[1].trim(), "|", tags, hierarchy)
            }
        }
        // Microsoft Photo / digiKam full-path keyword lists ("People/Sarah McLeod", "Places/London") —
        // both use "/" as the hierarchy separator, unlike Lightroom's "|".
        Regex("LastKeyword(?:XMP|IPTC)[^>]*>(.*?)</[A-Za-z0-9]+:LastKeyword(?:XMP|IPTC)>", RegexOption.DOT_MATCHES_ALL).findAll(raw).forEach { m ->
            liRegex.findAll(m.groupValues[1]).forEach {
                addHierarchicalChain(it.groupValues[1].trim(), "/", tags, hierarchy)
            }
        }
        Regex("TagsList[^>]*>(.*?)</[A-Za-z0-9]+:TagsList>", RegexOption.DOT_MATCHES_ALL).findAll(raw).forEach { m ->
            liRegex.findAll(m.groupValues[1]).forEach {
                addHierarchicalChain(it.groupValues[1].trim(), "/", tags, hierarchy)
            }
        }
        val rating = Regex("<xmp:Rating>(\\d+)</xmp:Rating>").find(raw)?.groupValues?.get(1)?.toIntOrNull()
            ?: Regex("xmp:Rating=\"(\\d+)\"").find(raw)?.groupValues?.get(1)?.toIntOrNull()
            ?: 0
        return XmpData(tags = tags.filter { it.isNotBlank() }, rating = rating, hierarchy = hierarchy)
    }

    private fun addHierarchicalChain(full: String, separator: String, tags: MutableSet<String>, hierarchy: MutableMap<String, String>) {
        val parts = full.split(separator).map { it.trim() }.filter { it.isNotBlank() }
        if (parts.isEmpty()) return
        tags.add(parts.last())
        for (i in 1 until parts.size) hierarchy[parts[i]] = parts[i - 1]
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

    /** Builds the parent→leaf chain (root first) for each tag that has an ancestor in [hierarchy],
     * e.g. "Berlin" with hierarchy[Berlin]=Germany, hierarchy[Germany]=Places → "Places|Germany|Berlin".
     * Tags with no known parent are omitted - they stay flat dc:subject-only entries, same as an
     * unfiled keyword in Lightroom. */
    private fun buildHierarchyChains(tags: List<String>, hierarchy: Map<String, String>): List<String> {
        val chains = mutableListOf<String>()
        for (tag in tags) {
            if (hierarchy[tag] == null) continue
            val chain = mutableListOf(tag)
            val seen = mutableSetOf(tag)
            var cur = hierarchy[tag]
            while (cur != null && seen.add(cur)) {
                chain.add(0, cur)
                cur = hierarchy[cur]
            }
            chains.add(chain.joinToString("|"))
        }
        return chains
    }

    /**
     * Extracts the `<x:xmpmeta>...</x:xmpmeta>` block from an existing packet and strips only the
     * fields this app owns (dc:subject, xmp:Rating, and - if [touchHierarchy] - hierarchicalSubject),
     * leaving every other property (GPS, captions, copyright, other apps' own Description blocks)
     * untouched. Returns null if there's nothing usable to preserve (falls back to a fresh packet).
     */
    private fun extractCleanedXmpmeta(raw: String, touchHierarchy: Boolean): String? {
        if (raw.isBlank()) return null
        val startIdx = raw.indexOf("<x:xmpmeta")
        val endTag = "</x:xmpmeta>"
        val endIdx = raw.indexOf(endTag)
        if (startIdx < 0 || endIdx < 0 || endIdx <= startIdx) return null
        var xmpmeta = raw.substring(startIdx, endIdx + endTag.length)
        if (!xmpmeta.contains("<rdf:RDF") || !xmpmeta.contains("</rdf:RDF>")) return null
        xmpmeta = xmpmeta.replace(Regex("<dc:subject[^>]*>.*?</dc:subject>", RegexOption.DOT_MATCHES_ALL), "")
        xmpmeta = xmpmeta.replace(Regex("<xmp:Rating>\\d+</xmp:Rating>"), "")
        xmpmeta = xmpmeta.replace(Regex("\\s+xmp:Rating=\"\\d+\""), "")
        if (touchHierarchy) {
            xmpmeta = xmpmeta.replace(Regex("<[A-Za-z0-9]+:hierarchicalSubject[^>]*>.*?</[A-Za-z0-9]+:hierarchicalSubject>", RegexOption.DOT_MATCHES_ALL), "")
        }
        return xmpmeta
    }

    private fun buildXmpPacket(existingRaw: String, tags: List<String>, rating: Int, hierarchy: Map<String, String>): ByteArray {
        val hierarchyChains = if (hierarchy.isNotEmpty()) buildHierarchyChains(tags, hierarchy) else emptyList()

        val ownedFields = buildString {
            if (rating > 0) append("      <xmp:Rating>$rating</xmp:Rating>\n")
            if (tags.isNotEmpty()) {
                val tagXml = tags.joinToString("\n") { "          <rdf:li>${xmlEscape(it)}</rdf:li>" }
                append(
                    """      <dc:subject>
        <rdf:Bag>
$tagXml
        </rdf:Bag>
      </dc:subject>
"""
                )
            }
            if (hierarchyChains.isNotEmpty()) {
                val hierarchyXml = hierarchyChains.joinToString("\n") { "          <rdf:li>${xmlEscape(it)}</rdf:li>" }
                append(
                    """      <lr:hierarchicalSubject>
        <rdf:Bag>
$hierarchyXml
        </rdf:Bag>
      </lr:hierarchicalSubject>
"""
                )
            }
        }
        val ownedDescription = """    <rdf:Description rdf:about=""
        xmlns:xmp="$XMP_NS"
        xmlns:dc="$DC_NS"
        xmlns:lr="$LR_NS">
$ownedFields    </rdf:Description>"""

        val cleanedXmpmeta = extractCleanedXmpmeta(existingRaw, touchHierarchy = hierarchy.isNotEmpty())
        val body = if (cleanedXmpmeta != null) {
            cleanedXmpmeta.replaceFirst("</rdf:RDF>", "$ownedDescription\n  </rdf:RDF>")
        } else {
            """<x:xmpmeta xmlns:x="adobe:ns:meta/">
  <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
$ownedDescription
  </rdf:RDF>
</x:xmpmeta>"""
        }

        val xmp = "<?xpacket begin=\"\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?>\n$body\n<?xpacket end=\"w\"?>"
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
                val idx = findSequence(buffer, XMP_APP1_HEADER)
                if (idx >= 0) {
                    val start = idx + XMP_APP1_HEADER.size
                    val end = findSequence(buffer, "<?xpacket end=".toByteArray())
                    if (end > start) return String(buffer, start, end - start, Charsets.UTF_8)
                }
            }
        } catch (_: Exception) { }
        return ""
    }

    /**
     * Prefers the "commercial" sidecar naming convention (original extension replaced, e.g.
     * "photo.xmp") used by Lightroom/Capture One/F-Stop Gallery, but falls back to the
     * extension-appended convention ("photo.png.xmp", the digiKam/darktable default) whenever a
     * sibling media file shares the same base name - otherwise two different files (e.g.
     * IMG_0001.RAW + IMG_0001.JPG from the same shot) would collide on a single sidecar name.
     */
    private fun sidecarPath(file: File): File {
        val base = file.nameWithoutExtension
        val hasSiblingCollision = file.parentFile?.listFiles()?.any { f ->
            f.name != file.name && f.nameWithoutExtension.equals(base, ignoreCase = true) && f.extension.lowercase() !in setOf("xmp", "bak", "tmp")
        } == true
        return if (hasSiblingCollision) File("${file.absolutePath}.xmp") else File(file.parentFile, "$base.xmp")
    }

    private fun readXmpFromSidecar(file: File): String {
        val preferred = sidecarPath(file)
        val replaceStyle = File(file.parentFile, "${file.nameWithoutExtension}.xmp")
        val appendStyle = File("${file.absolutePath}.xmp")
        val fallback = if (preferred.absolutePath == replaceStyle.absolutePath) appendStyle else replaceStyle
        val target = when {
            preferred.exists() -> preferred
            fallback.exists() -> fallback
            else -> return ""
        }
        return try { target.readText() } catch (_: Exception) { "" }
    }

    private fun writeXmpSidecar(file: File, xmpData: ByteArray): Boolean {
        return try { sidecarPath(file).writeBytes(xmpData); true } catch (_: Exception) { false }
    }

    /**
     * Streaming write: scans the first [MAX_SCAN_BYTES] of the JPEG for an existing XMP APP1 segment.
     * If found, replaces it. Otherwise inserts after all APP marker segments.
     * Uses a temp file to avoid loading the entire image into memory.
     */
    private fun writeXmpToJpeg(file: File, xmpData: ByteArray): Boolean {
        return try {
            val newApp1 = buildApp1Segment(xmpData)
            val scanLen = minOf(file.length(), MAX_SCAN_BYTES).toInt()
            val scanBuf = ByteArray(scanLen)

            RandomAccessFile(file, "r").use { raf ->
                raf.readFully(scanBuf)
            }

            // Locate existing XMP position, or compute insertion point after APP markers
            data class SegmentPos(val start: Int, val length: Int)

            val existingXmp: SegmentPos? = run {
                val idx = findSequence(scanBuf, XMP_APP1_HEADER)
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

            if (tempFile.exists() && tempFile.length() > 0) {
                // POSIX rename() atomically replaces an existing destination in a single filesystem
                // op - no window where neither the old nor new file exists. The previous version
                // did this as two sequential renameTo() calls via a ".bak" file, which left exactly
                // that window open if the process died between them.
                if (tempFile.renameTo(file)) {
                    true
                } else {
                    val copied = try {
                        tempFile.inputStream().use { ins -> file.outputStream().use { outs -> ins.copyTo(outs) } }
                        true
                    } catch (_: Exception) { false }
                    tempFile.delete()
                    copied
                }
            } else {
                tempFile.delete()
                false
            }
        } catch (_: Exception) { false }
    }

    private fun buildApp1Segment(xmpData: ByteArray): ByteArray {
        val app1Data = XMP_APP1_HEADER + xmpData
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

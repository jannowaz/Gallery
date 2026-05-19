package org.fossify.gallery.helpers

import java.io.File
import java.io.RandomAccessFile

object RatingWriter {
    private const val XMP_NAMESPACE = "http://ns.adobe.com/xap/1.0/"
    private const val RATING_TAG = "xmp:Rating"

    fun writeRating(filePath: String, rating: Int) {
        val file = File(filePath)
        if (!file.exists() || !file.canRead()) return

        val isJpeg = file.extension.lowercase() in listOf("jpg", "jpeg")
        val xmpData = buildXmpPacket(rating)

        if (isJpeg) {
            writeXmpToJpeg(file, xmpData)
        } else {
            writeXmpSidecar(file, xmpData)
        }
    }

    fun readRating(filePath: String): Int {
        val file = File(filePath)
        if (!file.exists()) return 0

        if (file.extension.lowercase() in listOf("jpg", "jpeg")) {
            return readXmpFromJpeg(file)
        }
        val sidecar = File("${file.absolutePath}.xmp")
        return if (sidecar.exists()) readXmpFromSidecar(sidecar) else 0
    }

    private fun buildXmpPacket(rating: Int): ByteArray {
        val xmp = """<?xpacket begin="" id="W5M0MpCehiHzreSzNTczkc9d"?>
            |<x:xmpmeta xmlns:x="adobe:ns:meta/">
            |  <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
            |    <rdf:Description rdf:about=""
            |        xmlns:xmp="$XMP_NAMESPACE">
            |      <xmp:Rating>$rating</xmp:Rating>
            |    </rdf:Description>
            |  </rdf:RDF>
            |</x:xmpmeta>
            |<?xpacket end="w"?>
        """.trimMargin().toByteArray(Charsets.UTF_8)
        return xmp
    }

    private fun writeXmpToJpeg(file: File, xmpData: ByteArray) {
        try {
            val raf = RandomAccessFile(file, "rw")
            val buffer = ByteArray(raf.length().toInt())
            raf.readFully(buffer)
            raf.seek(0)

            val xmpHeader = "http://ns.adobe.com/xap/1.0/\u0000".toByteArray()
            var newData: ByteArray
            val existingIndex = findSequence(buffer, xmpHeader)

            if (existingIndex >= 0) {
                val start = existingIndex - 29
                if (start >= 0 && buffer[start] == 0xFF.toByte() && buffer[start + 1] == 0xE1.toByte()) {
                    val oldLen = ((buffer[start + 2].toInt() and 0xFF) shl 8) or (buffer[start + 3].toInt() and 0xFF)
                    val newApp1 = buildApp1Segment(xmpData)
                    val before = buffer.copyOf(start)
                    val after = if (start + 2 + oldLen < buffer.size) buffer.copyOfRange(start + 2 + oldLen, buffer.size) else ByteArray(0)
                    newData = before + newApp1 + after
                } else {
                    newData = insertAfterHeader(buffer, xmpData)
                }
            } else {
                newData = insertAfterHeader(buffer, xmpData)
            }

            raf.setLength(newData.size.toLong())
            raf.write(newData)
            raf.close()
        } catch (_: Exception) { }
    }

    private fun insertAfterHeader(buffer: ByteArray, xmpData: ByteArray): ByteArray {
        var pos = 2
        while (pos < buffer.size - 1) {
            if (buffer[pos] == 0xFF.toByte() && buffer[pos + 1].toInt() and 0xFF >= 0xE0) {
                val segLen = ((buffer[pos + 2].toInt() and 0xFF) shl 8) or (buffer[pos + 3].toInt() and 0xFF)
                pos += 2 + segLen
            } else {
                break
            }
        }
        val before = buffer.copyOf(pos)
        val after = if (pos < buffer.size) buffer.copyOfRange(pos, buffer.size) else ByteArray(0)
        return before + buildApp1Segment(xmpData) + after
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
            for (j in pattern.indices) {
                if (data[i + j] != pattern[j]) continue@outer
            }
            return i
        }
        return -1
    }

    private fun readXmpFromJpeg(file: File): Int {
        try {
            val data = file.readBytes()
            val xmpHeader = "http://ns.adobe.com/xap/1.0/\u0000".toByteArray()
            val idx = findSequence(data, xmpHeader)
            if (idx >= 0) {
                val xmpStart = idx + xmpHeader.size
                val xmpEnd = findSequence(data, "<?xpacket end=".toByteArray())
                if (xmpEnd > xmpStart) {
                    val xmp = String(data, xmpStart, xmpEnd - xmpStart, Charsets.UTF_8)
                    val regex = Regex("<xmp:Rating>(\\d)</xmp:Rating>")
                    regex.find(xmp)?.let { return it.groupValues[1].toInt() }
                }
            }
        } catch (_: Exception) { }
        return 0
    }

    private fun writeXmpSidecar(file: File, xmpData: ByteArray) {
        try {
            val sidecar = File("${file.absolutePath}.xmp")
            sidecar.writeBytes(xmpData)
        } catch (_: Exception) { }
    }

    private fun readXmpFromSidecar(sidecar: File): Int {
        try {
            val xml = sidecar.readText()
            val regex = Regex("<xmp:Rating>(\\d)</xmp:Rating>")
            regex.find(xml)?.let { return it.groupValues[1].toInt() }
        } catch (_: Exception) { }
        return 0
    }
}

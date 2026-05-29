package org.fossify.gallery.helpers

import java.io.File

object TagWriter {
    private const val XMP_SUFFIX = ".xmp"

    fun readTags(path: String): List<String> {
        val xmpFile = File("$path$XMP_SUFFIX")
        if (!xmpFile.exists()) return emptyList()
        return try {
            xmpFile.readText().split(",").map { it.trim() }.filter { it.isNotBlank() }
        } catch (_: Exception) { emptyList() }
    }

    fun addTag(path: String, tag: String) {
        val existing = readTags(path).toMutableList()
        if (tag !in existing) existing.add(tag)
        writeTags(path, existing)
    }

    fun writeTags(path: String, tags: List<String>) {
        val xmpFile = File("$path$XMP_SUFFIX")
        try { xmpFile.writeText(tags.joinToString(",")) } catch (_: Exception) { }
    }

    fun readRatingFromXmp(path: String): Int {
        val xmpFile = File("$path$XMP_SUFFIX")
        if (!xmpFile.exists()) return 0
        return try {
            xmpFile.readText().trim().toIntOrNull() ?: 0
        } catch (_: Exception) { 0 }
    }
}

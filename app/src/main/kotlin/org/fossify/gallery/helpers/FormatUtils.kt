package org.fossify.gallery.helpers

/** The one byte formatter for the analysis/compression/duplicate surfaces - replaces four
 * per-file private copies that could silently drift apart. */
fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> "${"%.1f".format(bytes / 1_000_000_000.0)} GB"
    bytes >= 1_000_000 -> "${"%.1f".format(bytes / 1_000_000.0)} MB"
    bytes >= 1_000 -> "${bytes / 1_000} KB"
    else -> "$bytes B"
}

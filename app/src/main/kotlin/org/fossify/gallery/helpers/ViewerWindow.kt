package org.fossify.gallery.helpers

/** Direction in which the Viewer needs more paths around its current anchor. */
enum class ViewerLoadDirection {
    BEFORE,
    AFTER,
}

/** The small initial path window shown immediately after opening a media item. */
data class ViewerPathWindow(
    val paths: List<String>,
    val hasPrevious: Boolean,
    val hasNext: Boolean,
)

/** A bounded continuation page for a Viewer path window. */
data class ViewerPathChunk(
    val paths: List<String>,
    val hasMore: Boolean,
)

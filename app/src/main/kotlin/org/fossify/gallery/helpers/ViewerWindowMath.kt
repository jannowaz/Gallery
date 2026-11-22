package org.fossify.gallery.helpers

/** Pure bounded-window operations used by the process-local Viewer session store. */
object ViewerWindowMath {
    fun merge(
        current: List<String>,
        incoming: List<String>,
        direction: ViewerLoadDirection,
        maxSize: Int,
    ): List<String> {
        val limit = maxSize.coerceAtLeast(1)
        val merged = (if (direction == ViewerLoadDirection.BEFORE) incoming + current else current + incoming).distinct()
        val bounded = if (merged.size <= limit) merged else if (direction == ViewerLoadDirection.BEFORE) {
            merged.take(limit)
        } else {
            merged.takeLast(limit)
        }
        return bounded
    }
}

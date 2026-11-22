package org.fossify.gallery.helpers

/**
 * Identifies the currently authoritative refresh request.
 *
 * A cancelled coroutine can still return from a non-cooperative blocking API.  Callers must check
 * [isCurrent] before publishing either data or terminal loading/error state, rather than relying on
 * coroutine cancellation alone.
 */
internal class RefreshGenerationGate {
    private var generation = 0L

    @Synchronized
    fun begin(): Long = ++generation

    @Synchronized
    fun isCurrent(candidate: Long): Boolean = candidate == generation
}

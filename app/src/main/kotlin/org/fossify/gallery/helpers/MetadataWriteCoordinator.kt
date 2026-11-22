package org.fossify.gallery.helpers

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Process-wide coordination for metadata read-modify-write and cache replacement operations.
 * Repositories and WorkManager workers are instantiated separately, so an instance-local mutex
 * cannot protect the same file from a UI edit and a background metadata scan at the same time.
 *
 * The lock must be coroutine-safe: XMP reads and Room writes can suspend and resume on another
 * IO worker. A Java ReentrantLock is thread-owned, so holding it across a suspend point can crash
 * when the continuation reaches a different worker and calls unlock().
 */
object MetadataWriteCoordinator {
    private val locks = Array(32) { Mutex() }

    private fun lockFor(path: String): Mutex =
        locks[(path.hashCode() and Int.MAX_VALUE) % locks.size]

    /** Bridges legacy synchronous XMP callers into the same coroutine-safe lock table. */
    fun <T> withLock(path: String, block: () -> T): T =
        runBlocking { lockFor(path).withLock { block() } }

    /** Runs a suspending DB/XMP operation on an IO thread while holding the same stripe lock. */
    suspend fun <T> withLockSuspend(path: String, block: suspend () -> T): T =
        // The caller already owns the appropriate IO scope. Keeping the lock operation itself in
        // the caller's context avoids an unnecessary dispatcher hop around a suspending critical
        // section and, more importantly, keeps cancellation/release in one coroutine state machine.
        lockFor(path).withLock { block() }
}

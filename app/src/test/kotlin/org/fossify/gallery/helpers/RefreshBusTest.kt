package org.fossify.gallery.helpers

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Pins down RefreshBus's foreground gate. The bus fans one trigger out into a reload in every
 * mounted screen/ViewModel (Explorer, Media, Albums, Favorites, TagBrowser, FolderMedia), and any
 * MediaStore write by any app on the device triggers it via the ContentObserver - so "does a
 * background trigger stay withheld, and is it replayed exactly once on return" is the property
 * that keeps the screen-off case from costing a full multi-screen rescan per write.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RefreshBusTest {

    private class TestOwner : LifecycleOwner {
        // createUnsafe skips the main-thread assertion a plain LifecycleRegistry makes, which a
        // JVM unit test (no Robolectric, no ArchTaskExecutor main looper) can never satisfy.
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }

    private lateinit var owner: TestOwner

    @Before
    fun setUp() {
        // RefreshBus is an object, so its gate state outlives each test. Driving a fresh owner to
        // STARTED normalises both flags: inForeground becomes true and any missed trigger left
        // pending by an earlier test is consumed here, before a collector exists to observe it.
        owner = TestOwner()
        RefreshBus.startForegroundTracking(owner)
        owner.registry.currentState = Lifecycle.State.STARTED
    }

    private fun background() {
        owner.registry.currentState = Lifecycle.State.CREATED
    }

    private fun foreground() {
        owner.registry.currentState = Lifecycle.State.STARTED
    }

    /** Collects the bus for the duration of the test, settling the 300ms debounce as it goes. */
    private fun TestScope.collectEvents(): List<Unit> {
        val seen = mutableListOf<Unit>()
        backgroundScope.launch { RefreshBus.events.collect { seen += it } }
        runCurrent()
        return seen
    }

    @Test
    fun `trigger in the foreground is delivered`() = runTest {
        val seen = collectEvents()

        RefreshBus.trigger()
        advanceTimeBy(1_000)
        runCurrent()

        assertEquals(1, seen.size)
    }

    @Test
    fun `triggers while backgrounded are withheld`() = runTest {
        val seen = collectEvents()

        background()
        repeat(5) { RefreshBus.trigger() }
        advanceTimeBy(10_000)
        runCurrent()

        assertEquals(0, seen.size)
    }

    @Test
    fun `a burst of background triggers is replayed as exactly one refresh on return`() = runTest {
        val seen = collectEvents()

        background()
        repeat(20) { RefreshBus.trigger() }
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(0, seen.size)

        foreground()
        advanceTimeBy(1_000)
        runCurrent()

        // One, not 20: coming back from a long stretch in the background costs a single reload.
        assertEquals(1, seen.size)
    }

    @Test
    fun `returning without any background trigger does not refresh`() = runTest {
        val seen = collectEvents()

        background()
        advanceTimeBy(10_000)
        runCurrent()
        foreground()
        advanceTimeBy(1_000)
        runCurrent()

        // Guards against every app switch turning into a spurious full rescan.
        assertEquals(0, seen.size)
    }

    @Test
    fun `the catch-up fires only once, not on every later return`() = runTest {
        val seen = collectEvents()

        background()
        RefreshBus.trigger()
        foreground()
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(1, seen.size)

        // Second round trip, nothing triggered in between - the flag must have been cleared.
        background()
        advanceTimeBy(1_000)
        foreground()
        advanceTimeBy(1_000)
        runCurrent()

        assertEquals(1, seen.size)
    }
}

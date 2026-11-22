package org.fossify.gallery.helpers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RefreshGenerationGateTest {

    @Test
    fun `replacement refresh owns terminal state after delayed first load`() {
        val gate = RefreshGenerationGate()
        val firstLoad = gate.begin()
        val silentReplacement = gate.begin()

        // The delayed first request may return after cancellation from a blocking API. It must not
        // clear the loading state of the replacement request.
        assertFalse(gate.isCurrent(firstLoad))
        assertTrue(gate.isCurrent(silentReplacement))
    }

    @Test
    fun `only newest request is authoritative`() {
        val gate = RefreshGenerationGate()
        val first = gate.begin()
        val second = gate.begin()
        val third = gate.begin()

        assertFalse(gate.isCurrent(first))
        assertFalse(gate.isCurrent(second))
        assertTrue(gate.isCurrent(third))
    }
}

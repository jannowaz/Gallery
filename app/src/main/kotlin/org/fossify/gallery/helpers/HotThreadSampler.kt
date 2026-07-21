package org.fossify.gallery.helpers

import android.util.Log

/**
 * Debug-only sampling profiler for the cold-start CPU burn measured on 2026-07-21: a single
 * Dispatchers.Default coroutine pegs a core for ~3 minutes after a cold start on a large library
 * (356s of process CPU on a 163k-item device), then the app drops to exactly 0.
 *
 * Exists because the usual tools are unavailable on that device: simpleperf rejects both
 * `cpu-cycles` and `cpu-clock` ("not supported on the device", PMU locked down despite
 * perf_event_paranoid=-1), and `debuggerd -j` requires root. Sampling from inside the process
 * needs neither.
 *
 * Every [INTERVAL_MS] it walks all threads, keeps the ones that are actually on-CPU (RUNNABLE)
 * rather than parked in the worker pool, and tallies their topmost app frame. Whatever is holding
 * a core for minutes will dominate the tally by construction, so this identifies the culprit
 * without having to guess candidates and instrument them one at a time.
 */
object HotThreadSampler {

    private const val TAG = "HotSampler"
    private const val INTERVAL_MS = 500L
    private const val REPORT_EVERY_MS = 15_000L
    private const val MAX_RUNTIME_MS = 8 * 60 * 1000L
    private const val APP_PACKAGE = "org.fossify"

    @Volatile
    private var started = false

    fun start() {
        if (started) return
        started = true
        Thread({ run() }, "HotThreadSampler").apply { isDaemon = true }.start()
    }

    private fun run() {
        // Keyed by "topmost app frame", valued by how many samples caught it on-CPU. A second map
        // keeps one full stack per key so the report shows the call path, not just the leaf.
        val counts = LinkedHashMap<String, Int>()
        val exemplars = HashMap<String, String>()
        var samples = 0
        var busySamples = 0
        val startedAt = System.currentTimeMillis()
        var lastReport = startedAt

        while (System.currentTimeMillis() - startedAt < MAX_RUNTIME_MS) {
            try {
                Thread.sleep(INTERVAL_MS)
            } catch (e: InterruptedException) {
                return
            }
            samples++

            val traces = try {
                Thread.getAllStackTraces()
            } catch (e: Throwable) {
                continue
            }

            for ((thread, stack) in traces) {
                // Only threads actually burning CPU. The Default pool keeps ~20 parked workers on
                // this device; without this filter they drown the tally in WAITING noise.
                if (thread.state != Thread.State.RUNNABLE || stack.isEmpty()) continue
                if (thread.name == "HotThreadSampler") continue

                val appFrame = stack.firstOrNull { it.className.startsWith(APP_PACKAGE) } ?: continue
                busySamples++

                val key = "${thread.name.take(20)} | ${appFrame.className.substringAfterLast('.')}.${appFrame.methodName}:${appFrame.lineNumber}"
                counts[key] = (counts[key] ?: 0) + 1
                if (key !in exemplars) {
                    exemplars[key] = stack.take(12).joinToString("\n      ") {
                        "${it.className}.${it.methodName}:${it.lineNumber}"
                    }
                }
            }

            val now = System.currentTimeMillis()
            if (now - lastReport >= REPORT_EVERY_MS) {
                lastReport = now
                report(counts, exemplars, samples, busySamples, now - startedAt)
                if (busySamples == 0) {
                    // Burn is over - the app measured at exactly 0 ticks once settled, so there is
                    // nothing left to attribute and the sampler should stop perturbing things.
                    Log.i(TAG, "no on-CPU app frames in this window - stopping sampler")
                    return
                }
                counts.clear()
                exemplars.clear()
                samples = 0
                busySamples = 0
            }
        }
    }

    private fun report(
        counts: Map<String, Int>,
        exemplars: Map<String, String>,
        samples: Int,
        busySamples: Int,
        elapsedMs: Long,
    ) {
        Log.i(TAG, "=== t+${elapsedMs / 1000}s | $samples samples, $busySamples on-CPU app hits ===")
        counts.entries.sortedByDescending { it.value }.take(5).forEachIndexed { i, (key, count) ->
            val pct = if (samples > 0) count * 100 / samples else 0
            Log.i(TAG, "  #${i + 1}  $count hits (${pct}% of samples)  $key")
            if (i == 0) Log.i(TAG, "      ${exemplars[key]}")
        }
    }
}

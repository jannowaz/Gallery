package org.fossify.gallery.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// A/B measurement for whether the committed baseline-prof.txt actually helps cold start, rather
// than assuming it does because generation succeeded. Run against the auto-generated "benchmark"
// build type (non-debuggable release, unlike the "nonMinifiedRelease" variant BaselineProfileGenerator
// uses to collect the profile) so JIT/AOT behavior matches what a real install would see.
@RunWith(AndroidJUnit4::class)
class StartupBenchmarks {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun startupNoCompilation() = benchmark(CompilationMode.None())

    @Test
    fun startupWithBaselineProfile() = benchmark(CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Require))

    private fun benchmark(compilationMode: CompilationMode) {
        benchmarkRule.measureRepeated(
            packageName = targetPackageName,
            metrics = listOf(StartupTimingMetric()),
            iterations = 8,
            startupMode = StartupMode.COLD,
            compilationMode = compilationMode,
        ) {
            pressHome()
            startActivityAndWait()
        }
    }

    private val targetPackageName: String
        get() = InstrumentationRegistry.getArguments().getString("androidx.benchmark.targetPackageName")
            ?: error("androidx.benchmark.targetPackageName missing from instrumentation arguments")
}

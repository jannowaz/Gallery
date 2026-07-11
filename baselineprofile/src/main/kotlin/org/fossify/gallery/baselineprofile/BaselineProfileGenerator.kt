package org.fossify.gallery.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    // Cold start only - covers Application/Activity init, DI setup, first composition. Kept as its
    // own journey (rather than folded into mediaGridScroll) since profgen weighs what actually ran
    // on the cold path higher when it's isolated from later warm interactions.
    @Test
    fun startup() = rule.collect(
        packageName = targetPackageName,
    ) {
        startActivityAndWait()
    }

    // Cold start + open the Medien grid + scroll it - the actual hot path this session's profiling
    // found spending real CPU/GC time (MediaFetcher directory scan, paged grid composition).
    @Test
    fun mediaGridScroll() = rule.collect(
        packageName = targetPackageName,
    ) {
        startActivityAndWait()
        device.wait(Until.findObject(By.text("Medien")), 5_000)?.click()
        device.wait(Until.hasObject(By.scrollable(true)), 5_000)
        repeat(3) {
            device.findObject(By.scrollable(true))?.fling(Direction.DOWN)
            device.waitForIdle()
        }
    }

    // This module is a standalone :baselineprofile com.android.test module (not an androidTest
    // source set inside :app), so InstrumentationRegistry's targetContext is NOT wired to the app
    // under test the way it would be for a self-instrumenting module - it resolves to this test
    // module's own package. Confirmed on-device: using targetContext.packageName here made
    // androidx.benchmark's pre-run "force-stop" call kill its own instrumentation process instead
    // of the target app, crashing every run before startActivityAndWait() ever executed. The
    // baseline profile Gradle plugin already injects the real target package as an instrumentation
    // arg, which is what to read instead.
    private val targetPackageName: String
        get() = InstrumentationRegistry.getArguments().getString("androidx.benchmark.targetPackageName")
            ?: error("androidx.benchmark.targetPackageName missing from instrumentation arguments")
}

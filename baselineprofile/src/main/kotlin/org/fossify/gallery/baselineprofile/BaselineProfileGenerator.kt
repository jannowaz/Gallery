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

    // Cold start + open the Media grid + scroll it - locale-independent via testTag resource-id
    // with text fallbacks for older builds.
    @Test
    fun mediaGridScroll() = rule.collect(
        packageName = targetPackageName,
    ) {
        startActivityAndWait()
        val mediaTab = device.wait(Until.findObject(By.res("nav_tab_0")), 5_000)
            ?: device.wait(Until.findObject(By.text("Media")), 2_000)
            ?: device.wait(Until.findObject(By.text("Medien")), 2_000)
        mediaTab?.click()
        device.wait(Until.hasObject(By.scrollable(true)), 5_000)
        repeat(3) {
            device.findObject(By.scrollable(true))?.fling(Direction.DOWN)
            device.waitForIdle()
        }
    }

    // Cold start + scroll Explorer & Albums tabs so folder-tree and album-cover composition paths
    // are also AOT-compiled into the shipped baseline profile.
    @Test
    fun explorerAndAlbumsScroll() = rule.collect(
        packageName = targetPackageName,
    ) {
        startActivityAndWait()
        listOf("nav_tab_1", "nav_tab_2").forEach { tabResId ->
            device.wait(Until.findObject(By.res(tabResId)), 5_000)?.click()
            device.wait(Until.hasObject(By.scrollable(true)), 5_000)
            repeat(2) {
                device.findObject(By.scrollable(true))?.fling(Direction.DOWN)
                device.waitForIdle()
            }
        }
    }

    private val targetPackageName: String
        get() = InstrumentationRegistry.getArguments().getString("androidx.benchmark.targetPackageName")
            ?: error("androidx.benchmark.targetPackageName missing from instrumentation arguments")
}

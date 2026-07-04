package org.fossify.gallery.compose.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.Dp

/**
 * Central motion tokens so animation timing/feel is a handful of shared choices instead of each
 * screen picking its own [tween]/[spring] durations and easings ad hoc. Loosely mirrors M3's
 * "motion scheme" idea (short/medium/long durations, one springy feel for interactive gestures)
 * without depending on the (still-experimental at this Compose BOM version) M3 MotionScheme API.
 */
object AppMotion {
    /** Small state toggles: press feedback, icon swaps, chip selection. */
    val short: TweenSpec<Float> = tween(150, easing = FastOutSlowInEasing)

    /** Default cross-screen / content transitions. */
    val medium: TweenSpec<Float> = tween(300, easing = FastOutSlowInEasing)

    /** Larger surface transitions (sheets, full-screen morphs). */
    val long: TweenSpec<Float> = tween(400, easing = FastOutSlowInEasing)

    /** Gesture-driven snaps (zoom-to-level, drag release) - one consistent springy feel everywhere. */
    val gestureSpring: SpringSpec<Float> = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium)

    /**
     * Selection-bar top-inset reveal (content sliding down/up as a selection toolbar appears).
     * Bouncy, so it can transiently overshoot past its target - callers must coerce the animated
     * Dp to a valid range (e.g. `.coerceAtLeast(0.dp)`) before feeding it to `Modifier.padding()`.
     */
    val insetSpring: SpringSpec<Dp> = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
}

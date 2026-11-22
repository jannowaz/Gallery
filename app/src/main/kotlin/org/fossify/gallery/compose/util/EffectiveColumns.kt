package org.fossify.gallery.compose.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import kotlin.math.roundToInt

/**
 * The user's column-count setting is chosen on a phone-sized portrait surface. Scale it from that
 * reference width instead of only handling landscape: a tablet in portrait should also get more
 * columns, while the user's preference remains the lower bound. The cap keeps extreme multi-window
 * widths from creating unusably narrow tiles.
 */
@Composable
fun rememberEffectiveColumnCount(base: Int): Int {
    val density = LocalDensity.current
    val container = LocalWindowInfo.current.containerSize
    val widthDp = with(density) { container.width.toDp().value }
    if (widthDp <= 0f) return base

    // 360dp is a stable phone-width reference and avoids making the result depend on the current
    // orientation's height. The actual grid still owns the final spacing, so this is only a
    // conservative count hint rather than a promise about exact tile width.
    val referenceWidthDp = 360f
    return (base * widthDp / referenceWidthDp).roundToInt().coerceIn(base, 12)
}

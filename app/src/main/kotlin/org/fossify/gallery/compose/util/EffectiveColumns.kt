package org.fossify.gallery.compose.util

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import kotlin.math.roundToInt

/**
 * The user's column-count setting is chosen while holding the phone upright; applying it verbatim
 * in landscape blows every tile up to ~1 visible row (UX finding 2026-07-10). Scale the count by
 * the current aspect ratio instead, so the on-screen tile size stays roughly what the user picked
 * regardless of orientation. Portrait returns [base] untouched.
 */
@Composable
fun rememberEffectiveColumnCount(base: Int): Int {
    val config = LocalConfiguration.current
    if (config.orientation != Configuration.ORIENTATION_LANDSCAPE || config.screenHeightDp <= 0) return base
    return (base * config.screenWidthDp.toFloat() / config.screenHeightDp).roundToInt().coerceIn(base, 12)
}

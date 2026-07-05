package org.fossify.gallery.compose.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Single choke point for haptic feedback across the app. Callers get a plain function instead of
 * pulling [LocalHapticFeedback] themselves, so a future "disable haptics" setting only needs to be
 * gated here instead of at every long-press/drag/confirm call site.
 */
@Composable
fun rememberGalleryHaptics(): (HapticFeedbackType) -> Unit {
    val haptic = LocalHapticFeedback.current
    return remember(haptic) { { type: HapticFeedbackType -> haptic.performHapticFeedback(type) } }
}

package org.fossify.gallery.compose.util

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Carries a deep-link navigation request (currently only the Quick Mover widget's "set up folder
 * pairs" button, see [org.fossify.gallery.helpers.MoverWidgetProvider]) from `onNewIntent()` into
 * the already-composed [org.fossify.gallery.navigation.GalleryNavHost].
 *
 * A plain "read the Activity's intent once in a LaunchedEffect(Unit)" only covers a cold start -
 * `FLAG_ACTIVITY_CLEAR_TOP` on an already-running task delivers the new Intent via `onNewIntent()`
 * instead of recreating the Activity, and Compose has no reason to recompose or re-run that
 * one-shot effect just because the Activity's own `intent` field was replaced. Mirrors
 * [ScrollToTopBus]'s no-replay shape for the same reason: a screen that (re)composes after the
 * event already fired must not replay it.
 */
object NavigateBus {
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    fun trigger(target: String) {
        _events.tryEmit(target)
    }
}

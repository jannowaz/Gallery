package org.fossify.gallery.compose.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Fired when the user taps a bottom-nav tab that's already selected, so that tab's content can
 * jump back to the top. Mirrors [org.fossify.gallery.helpers.RefreshBus]'s no-replay SharedFlow
 * shape deliberately: a screen that (re)mounts after the event fired must NOT replay it, otherwise
 * switching view type (grid/list/mosaic) or navigating away and back would spuriously re-trigger
 * a scroll-to-top the next time its LaunchedEffect starts collecting.
 */
object ScrollToTopBus {
    private val _events = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    fun trigger(tabIndex: Int) {
        _events.tryEmit(tabIndex)
    }
}

/**
 * Scrolls to top when the bottom-nav tab [tabIndex] is reselected. No-op while [tabIndex] is null,
 * for screens reached outside the bottom-nav (e.g. a folder opened from Explorer) that aren't tied
 * to a tab and shouldn't react to the bus at all.
 */
@Composable
fun ScrollToTopEffect(tabIndex: Int?, onTrigger: suspend () -> Unit) {
    val latestOnTrigger by rememberUpdatedState(onTrigger)
    if (tabIndex != null) {
        LaunchedEffect(tabIndex) {
            ScrollToTopBus.events.collect { tab -> if (tab == tabIndex) latestOnTrigger() }
        }
    }
}

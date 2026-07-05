package org.fossify.gallery.helpers

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.debounce

object RefreshBus {
    private val _events = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    // Debounced so a burst of near-simultaneous triggers (e.g. a batch worker's RefreshBus.trigger()
    // landing close to the ContentObserver's, or several tag/rating writes in quick succession)
    // collapses into a single downstream refresh per collector instead of every one of the several
    // independent caches/screens that collect this (Explorer, Favorites, Collections, Tags,
    // FolderMediaScreen, MediaViewModel's silentRefresh, ...) redoing its reload once per trigger.
    @OptIn(FlowPreview::class)
    val events = _events.asSharedFlow().debounce(300)

    fun trigger() {
        _events.tryEmit(Unit)
    }
}

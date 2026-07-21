package org.fossify.gallery.helpers

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
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

    // Starts false, not true: the process can also be started headless by a Worker (BootScanWorker
    // at boot, MediaSyncWorker off the ContentObserver) with no Activity ever coming up. Defaulting
    // to true there would emit into a void - nothing is collecting yet, and the SharedFlow has no
    // replay - so the event would be silently lost and the user would open the app to stale data.
    // With false, that same trigger sets missedWhileBackgrounded instead and gets replayed below.
    @Volatile
    private var inForeground = false

    @Volatile
    private var missedWhileBackgrounded = false

    fun trigger() {
        // Every collector on this bus does UI-refresh work only (full folder rescans, filtered SQL
        // per collection, tag-cache rebuilds, MediaStore scans) - none of it is needed while no UI
        // is on screen. Before this gate, one MediaStore write by any app on the device (a chat app
        // saving a photo) fanned out through the ContentObserver into a simultaneous reload in
        // Explorer + Media + Albums + Favorites + TagBrowser + FolderMedia, with the screen off,
        // because none of the 10 collect sites were lifecycle-aware: the three in ViewModels run on
        // viewModelScope (alive as long as the Activity is) and the seven in LaunchedEffects run on
        // a composition that a backgrounded Activity keeps alive too. Coalescing to a single flag
        // also means a long stretch in the background costs exactly one refresh on return, not one
        // per write that happened while away.
        if (!inForeground) {
            missedWhileBackgrounded = true
            return
        }
        _events.tryEmit(Unit)
    }

    /**
     * Hooks the gate above to the process lifecycle. Called once from [org.fossify.gallery.App].
     *
     * Uses ProcessLifecycleOwner rather than counting started Activities by hand: it already
     * debounces the stop/start pair that a rotation or an Activity-to-Activity transition produces
     * (Home -> Viewer -> back), which a naive counter would report as a real background trip and
     * turn into a spurious full refresh on every rotation.
     */
    fun startForegroundTracking(owner: LifecycleOwner = ProcessLifecycleOwner.get()) {
        owner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                inForeground = true
                // Catch-up: deliver the collapsed background triggers now that something can act on
                // them, so returning to the app never shows data that a worker/observer already knew
                // was stale.
                if (missedWhileBackgrounded) {
                    missedWhileBackgrounded = false
                    _events.tryEmit(Unit)
                }
            }

            override fun onStop(owner: LifecycleOwner) {
                inForeground = false
            }
        })
    }
}

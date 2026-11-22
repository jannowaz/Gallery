package org.fossify.gallery.helpers

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.channels.BufferOverflow
import java.util.LinkedHashMap
import java.util.concurrent.atomic.AtomicLong

@OptIn(FlowPreview::class)
object RefreshBus {
    // Refreshes are coalesced downstream, but a producer must not lose its signal while a screen is
    // still rendering the previous one. The payload stays Unit so existing collectors remain
    // source-compatible; cache generations cover screens that re-enter after an event.
    private val _events = MutableSharedFlow<Unit>(
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val _pathEvents = MutableSharedFlow<Long>(
        replay = 1,
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val _metadataEvents = MutableSharedFlow<Unit>(
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    // Keep the payload once per path generation. Each collector coalesces independently but reads
    // the same immutable snapshots, so one subscriber can never drain the data needed by another.
    // The bounded history also lets a screen that starts after a background catch-up reconstruct all
    // changes since its last observation; falling behind the bound safely degrades to a full refresh.
    private val pathPayloads = LinkedHashMap<Long, Set<String>?>()

    // Debounced so a burst of near-simultaneous triggers (e.g. a batch worker's RefreshBus.trigger()
    // landing close to the ContentObserver's, or several tag/rating writes in quick succession)
    // collapses into a single downstream refresh per collector instead of every one of the several
    // independent caches/screens that collect this (Favorites, Collections, Tags, FolderMediaScreen,
    // ...) redoing its reload once per trigger.
    @OptIn(FlowPreview::class)
    val events = _events.asSharedFlow().debounce(300)

    // A separate lane for changes that can alter the media path/order set (new, moved, deleted or
    // restored files). Metadata-only changes such as a favorite toggle still need to update the
    // visible screens, but must not make the Viewer and Explorer rebuild their six-figure path
    // caches. Keeping this lane separate removes the most expensive needless work from the common
    // interaction path without changing the general refresh contract used by the other screens.
    @OptIn(FlowPreview::class)
    val pathEvents: Flow<Set<String>?> = flow {
        // A newly created collector performs its own generation-based cache check. Starting at the
        // current generation prevents it from replaying an unrelated historical path event (or an
        // unknown full-refresh marker) that was already handled by a previous screen instance.
        // Collectors that stay mounted across a background/foreground transition retain their
        // previous local generation and still receive the collapsed catch-up event below.
        var deliveredGeneration = pathGenerationCounter.get()
        _pathEvents.asSharedFlow().debounce(300).collect { latestGeneration ->
            if (latestGeneration <= deliveredGeneration) return@collect
            val snapshot = synchronized(pathPayloads) {
                var unknown = false
                val merged = linkedSetOf<String>()
                var generation = deliveredGeneration + 1L
                while (generation <= latestGeneration) {
                    val payload = pathPayloads[generation]
                    if (payload == null) unknown = true else merged.addAll(payload)
                    generation++
                }
                deliveredGeneration = latestGeneration
                val pruneBefore = latestGeneration - 256L
                pathPayloads.keys.removeIf { it < pruneBefore }
                if (unknown) null else merged
            }
            emit(snapshot)
        }
    }

    @OptIn(FlowPreview::class)
    val metadataEvents = _metadataEvents.asSharedFlow().debounce(300)

    // Monotonic "data changed" counter, bumped on every trigger() (i.e. every delete/restore/move/
    // rename/sync/rating write). Screens that cache an expensive derived view keyed off the whole
    // library - CollectionsScreen's per-collection path query being the motivating case - can record
    // the generation their cache was built at and cheaply detect on re-entry whether anything changed
    // meanwhile. This closes the gap the debounced [events] collector alone can't: a change that
    // fired *before* the screen re-entered composition (delete inside a Collection, then tab back to
    // the Collections grid) emits into a collector that isn't listening yet, so the tile count/cover
    // stayed frozen until the next unrelated write or an app restart. Bumped unconditionally (even
    // while backgrounded, unlike the event emit) so a change made while away is still observed on return.
    private val generationCounter = AtomicLong(0L)
    var generation: Long
        get() = generationCounter.get()
        private set(value) { generationCounter.set(value) }

    /** Generation for changes that alter the set or order of media paths. */
    private val pathGenerationCounter = AtomicLong(0L)
    var pathGeneration: Long
        get() = pathGenerationCounter.get()
        private set(value) { pathGenerationCounter.set(value) }

    /** Generation for changes that can alter a collection's filtered result set. */
    private val collectionGenerationCounter = AtomicLong(0L)
    var collectionGeneration: Long
        get() = collectionGenerationCounter.get()
        private set(value) { collectionGenerationCounter.set(value) }

    // Starts false, not true: the process can also be started headless by a Worker (BootScanWorker
    // at boot, MediaSyncWorker off the ContentObserver) with no Activity ever coming up. Defaulting
    // to true there would emit into a void - nothing is collecting yet, and the SharedFlow has no
    // replay - so the event would be silently lost and the user would open the app to stale data.
    // With false, that same trigger sets missedWhileBackgrounded instead and gets replayed below.
    @Volatile
    private var inForeground = false

    /** True while a gallery screen is visible in the process. */
    val isInForeground: Boolean
        get() = inForeground

    @Volatile
    private var missedWhileBackgrounded = false

    @Volatile
    private var missedPathChangeWhileBackgrounded = false

    @Volatile
    private var missedMetadataChangeWhileBackgrounded = false

    fun trigger(
        pathsChanged: Boolean = true,
        collectionsChanged: Boolean = true,
        affectedPaths: Collection<String>? = null,
    ) {
        // A real data change happened - advance the generation regardless of foreground state, so a
        // change made while backgrounded is still detectable by a cache-staleness check on return.
        generationCounter.incrementAndGet()
        val pathGeneration = if (pathsChanged) pathGenerationCounter.incrementAndGet() else null
        if (collectionsChanged) collectionGenerationCounter.incrementAndGet()
        if (pathGeneration != null) {
            synchronized(pathPayloads) {
                pathPayloads[pathGeneration] = affectedPaths
                    ?.map { it.trimEnd('/') }
                    ?.filter { it.isNotBlank() }
                    ?.toSet()
                    ?.takeIf { it.isNotEmpty() }
            }
        }
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
            if (pathsChanged) {
                missedPathChangeWhileBackgrounded = true
            } else {
                missedMetadataChangeWhileBackgrounded = true
            }
            return
        }
        _events.tryEmit(Unit)
        if (pathGeneration != null) {
            _pathEvents.tryEmit(pathGeneration)
        } else {
            _metadataEvents.tryEmit(Unit)
        }
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
                if (missedPathChangeWhileBackgrounded) {
                    missedPathChangeWhileBackgrounded = false
                    // An unknown background change only needs one full fallback refresh on this
                    // return. Clear that marker before emitting so a collector that starts a moment
                    // later cannot accidentally turn a future known-path event into a full refresh.
                    _pathEvents.tryEmit(pathGenerationCounter.get())
                }
                if (missedMetadataChangeWhileBackgrounded) {
                    missedMetadataChangeWhileBackgrounded = false
                    _metadataEvents.tryEmit(Unit)
                }
            }

            override fun onStop(owner: LifecycleOwner) {
                inForeground = false
            }
        })
    }
}

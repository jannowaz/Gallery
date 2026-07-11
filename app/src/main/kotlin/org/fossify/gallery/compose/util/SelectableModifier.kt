package org.fossify.gallery.compose.util

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow

@Stable
class SelectionDragState {
    var isDragging by mutableStateOf(false)
    var dragBounds by mutableStateOf<Rect?>(null)
    var anchorPath by mutableStateOf<String?>(null)
    // The finger-down position the drag rectangle is measured from - see dragSelectionGesture's
    // onDrag for why this has to be kept separate from dragBounds itself.
    var anchorOffset by mutableStateOf<androidx.compose.ui.geometry.Offset?>(null)
    var autoScrollSpeed by mutableStateOf(0f)

    private val itemBounds = mutableMapOf<String, Rect>()

    fun registerItemBounds(path: String, bounds: Rect) {
        itemBounds[path] = bounds
    }

    fun unregisterItemBounds(path: String) {
        itemBounds.remove(path)
    }

    fun findItemAt(position: androidx.compose.ui.geometry.Offset): String? {
        return itemBounds.entries.firstOrNull { (_, bounds) -> bounds.contains(position) }?.key
    }

    fun getItemsInDragArea(): Set<String> {
        val area = dragBounds ?: return emptySet()
        return itemBounds.filter { (_, bounds) ->
            bounds.overlaps(area) || area.overlaps(bounds)
        }.keys
    }

    fun reset() {
        isDragging = false
        dragBounds = null
        anchorPath = null
        anchorOffset = null
        autoScrollSpeed = 0f
    }
}

@Composable
fun rememberSelectionDragState(): SelectionDragState {
    return androidx.compose.runtime.remember { SelectionDragState() }
}

/** Backs the selection-mode "preview" affordance (a tile's eye icon, see MediaTile) - lets the user
 * check a large inline preview of one item without it counting as a tap-to-toggle or long-press
 * range-select, and without navigating away (which would cost scroll position, since the grid's
 * LazyGridState is seeded from a plain saved index/offset, not carried across a screen dispose). */
@Stable
class PeekState {
    var path by mutableStateOf<String?>(null)
        private set
    var isVideo by mutableStateOf(false)
        private set

    fun show(path: String, isVideo: Boolean) {
        this.path = path
        this.isVideo = isVideo
    }

    fun hide() {
        path = null
    }
}

@Composable
fun rememberPeekState(): PeekState = remember { PeekState() }

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.selectableItem(
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onSwipeToSelect: () -> Unit = {},
    interactionSource: MutableInteractionSource? = null,
): Modifier = composed {
    val haptic = rememberGalleryHaptics()
    this.combinedClickable(
        onClick = onClick,
        onLongClick = { haptic(HapticFeedbackType.LongPress); onLongClick() },
        interactionSource = interactionSource,
        indication = androidx.compose.foundation.LocalIndication.current
    )
}

fun Modifier.dragSelectionGesture(
    state: SelectionDragState,
    // Only attached once a selection is already active (started by a tile's own long-press, see
    // MediaTile/selectableItem's onLongClick) - not while just browsing. This pointerInput and each
    // tile's own combinedClickable both race to recognize the same long-press gesture on this Box's
    // full-grid hit area; Compose has no single-owner touch capture between sibling/ancestor
    // pointerInput blocks, so with this always attached, a plain tap OR the very first long-press
    // could unpredictably get bogged down in that arbitration (observed live: taps on the Media grid
    // sometimes silently doing nothing, or a single long-press ending up marking two items) instead
    // of reaching the tile's own click/long-click handling cleanly. Gating this off entirely until
    // a selection already exists removes the conflict for those two majority-of-the-time
    // interactions; drag-to-extend-selection (this modifier's whole purpose) is only ever useful
    // once a selection has already been started anyway.
    enabled: Boolean,
    gridState: LazyGridState? = null,
    staggeredGridState: LazyStaggeredGridState? = null,
    onSelectPath: (String) -> Unit,
): Modifier = composed {
    if (!enabled) return@composed this
    val haptic = rememberGalleryHaptics()
    this.pointerInput(gridState, staggeredGridState) {
        val edgeThreshold = 80f
        // Tracks which paths this drag session has already fired a tick for, so extending the drag
        // over an already-selected item (or jittering back and forth near the boundary) doesn't
        // re-trigger the tick - only a path newly entering the selection gets one.
        val tickedPaths = mutableSetOf<String>()

        detectDragGesturesAfterLongPress(
            onDragStart = { offset ->
                haptic(HapticFeedbackType.LongPress)
                tickedPaths.clear()
                state.isDragging = true
                state.anchorOffset = offset
                state.dragBounds = Rect(offset, offset)
                val item = state.findItemAt(offset)
                state.anchorPath = item
                item?.let { tickedPaths.add(it); onSelectPath(it) }
            },
            onDrag = { change, _ ->
                change.consume()
                // Measured from the fixed anchor point to the current finger position - NOT a
                // running union of every point touched so far (what this used to do, via
                // minOf/maxOf against the *previous* dragBounds). That union only ever grew and
                // never shrank, so the tiny involuntary jitter present in any real long-press
                // (a human finger is never perfectly still) would permanently widen the rect by a
                // few pixels and then never let go of that extra sliver - on a tightly-packed grid
                // that sliver is enough to overlap the next item (typically the one above, since
                // grid rows are tight vertically), silently multi-selecting it on what the user
                // experiences as a single plain long-press.
                val anchor = state.anchorOffset ?: change.position
                state.dragBounds = Rect(
                    left = minOf(anchor.x, change.position.x),
                    top = minOf(anchor.y, change.position.y),
                    right = maxOf(anchor.x, change.position.x),
                    bottom = maxOf(anchor.y, change.position.y),
                )
                state.getItemsInDragArea().forEach { path ->
                    if (tickedPaths.add(path)) haptic(HapticFeedbackType.SegmentTick)
                    onSelectPath(path)
                }

                val containerHeight = size.height.toFloat()
                val posY = change.position.y
                state.autoScrollSpeed = when {
                    posY < edgeThreshold -> ((posY - edgeThreshold) / edgeThreshold * 4f).coerceIn(-4f, 0f)
                    posY > containerHeight - edgeThreshold -> ((posY - (containerHeight - edgeThreshold)) / edgeThreshold * 4f).coerceIn(0f, 4f)
                    else -> 0f
                }
            },
            onDragEnd = { state.reset() },
            onDragCancel = { state.reset() },
        )
    }
}

/**
 * Reports this item's on-screen bounds to [onBoundsChanged] (for drag-select hit-testing), throttled
 * to at most once per [throttleMs] - grid/list layout passes reposition items far more often than
 * drag-select needs fresh bounds for. Shared by MediaTile (grid) and MediaListRow (list) so the two
 * view modes can't drift out of sync on this value.
 *
 * [enabled] gates the whole thing: the reported bounds are only ever consumed while a drag-selection
 * is active, so with no selection in progress this returns the modifier untouched - no
 * onGloballyPositioned callback is attached at all, sparing every visible tile a bounds computation on
 * every layout pass during normal scrolling (the common case, and the hot path on a large grid).
 */
fun Modifier.throttledBoundsReporting(enabled: Boolean, throttleMs: Long = 300L, onBoundsChanged: (Rect) -> Unit): Modifier =
    if (!enabled) this
    else composed {
        var lastUpdate by remember { mutableLongStateOf(0L) }
        this.onGloballyPositioned { coords ->
            val now = System.currentTimeMillis()
            if (now - lastUpdate > throttleMs) {
                lastUpdate = now
                val p = coords.positionInWindow()
                val s = coords.size
                onBoundsChanged(Rect(p, Size(s.width.toFloat(), s.height.toFloat())))
            }
        }
    }

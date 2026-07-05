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
        autoScrollSpeed = 0f
    }
}

@Composable
fun rememberSelectionDragState(): SelectionDragState {
    return androidx.compose.runtime.remember { SelectionDragState() }
}

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
    gridState: LazyGridState? = null,
    staggeredGridState: LazyStaggeredGridState? = null,
    onSelectPath: (String) -> Unit,
): Modifier = composed {
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
                state.dragBounds = Rect(offset, offset)
                val item = state.findItemAt(offset)
                state.anchorPath = item
                item?.let { tickedPaths.add(it); onSelectPath(it) }
            },
            onDrag = { change, _ ->
                change.consume()
                val prev = state.dragBounds ?: return@detectDragGesturesAfterLongPress
                state.dragBounds = Rect(
                    left = minOf(prev.left, change.position.x),
                    top = minOf(prev.top, change.position.y),
                    right = maxOf(prev.right, change.position.x),
                    bottom = maxOf(prev.bottom, change.position.y),
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
 */
fun Modifier.throttledBoundsReporting(throttleMs: Long = 300L, onBoundsChanged: (Rect) -> Unit): Modifier = composed {
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

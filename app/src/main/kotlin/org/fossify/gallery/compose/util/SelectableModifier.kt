package org.fossify.gallery.compose.util

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput

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
): Modifier = composed {
    this.combinedClickable(
        onClick = onClick,
        onLongClick = onLongClick,
    )
}

fun Modifier.dragSelectionGesture(
    state: SelectionDragState,
    gridState: LazyGridState? = null,
    staggeredGridState: LazyStaggeredGridState? = null,
    onSelectPath: (String) -> Unit,
): Modifier = pointerInput(gridState, staggeredGridState) {
    val edgeThreshold = 80f

    detectDragGesturesAfterLongPress(
        onDragStart = { offset ->
            state.isDragging = true
            state.dragBounds = Rect(offset, offset)
            val item = state.findItemAt(offset)
            state.anchorPath = item
            item?.let { onSelectPath(it) }
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
            state.getItemsInDragArea().forEach { onSelectPath(it) }

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

package org.fossify.gallery.compose.util

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.layout.boundsInWindow

@Stable
class SelectionDragState {
    var isDragging by mutableStateOf(false)
    var dragBounds by mutableStateOf<Rect?>(null)
    var anchorPath by mutableStateOf<String?>(null)

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
    onLongClick: () -> Unit,
    onSwipeToSelect: () -> Unit = {},
): Modifier = composed {
    val view = LocalView.current

    this.combinedClickable(
        onClick = {
            if (isSelectionMode) onClick()
            else onClick()
        },
        onLongClick = {
            onLongClick()
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        },
    )
}

fun Modifier.dragSelectionGesture(
    state: SelectionDragState,
    onSelectPath: (String) -> Unit,
): Modifier = pointerInput(Unit) {
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
        },
        onDragEnd = { state.reset() },
        onDragCancel = { state.reset() },
    )
}

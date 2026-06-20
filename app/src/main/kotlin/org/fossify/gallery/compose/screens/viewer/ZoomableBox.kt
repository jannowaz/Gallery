package org.fossify.gallery.compose.screens.viewer

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

const val ZOOM_MIN = 1f
const val ZOOM_MAX = 6f
const val ZOOM_DOUBLE_TAP = 2.5f

class ZoomState(private val scope: CoroutineScope) {
    var scale by mutableFloatStateOf(1f)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set
    var viewSize by mutableStateOf(IntSize.Zero)
        private set

    val isZoomed: Boolean get() = scale > 1.01f

    private val zoomLevels = listOf(1f, 2f, 4f)

    private fun clampOffset(candidate: Offset, s: Float, size: IntSize): Offset {
        val maxX = (s - 1f) * size.width / 2f
        val maxY = (s - 1f) * size.height / 2f
        return Offset(candidate.x.coerceIn(-maxX, maxX), candidate.y.coerceIn(-maxY, maxY))
    }

    fun onTransform(centroid: Offset, pan: Offset, zoom: Float, size: IntSize) {
        viewSize = size
        val newScale = (scale * zoom).coerceIn(ZOOM_MIN, ZOOM_MAX)
        val center = Offset(size.width / 2f, size.height / 2f)
        val d = centroid - center
        val k = if (scale == 0f) 1f else newScale / scale
        val focal = d * (1f - k) + offset * k
        scale = newScale
        offset = if (newScale <= ZOOM_MIN) Offset.Zero else clampOffset(focal + pan, newScale, size)
    }

    fun cycleZoom(tap: Offset, size: IntSize) {
        viewSize = size
        val target = zoomLevels.firstOrNull { it > scale + 0.01f } ?: 1f
        val center = Offset(size.width / 2f, size.height / 2f)
        val d = tap - center
        val k = if (scale == 0f) 1f else target / scale
        val focal = d * (1f - k) + offset * k
        val targetOffset = if (target <= ZOOM_MIN) Offset.Zero else clampOffset(focal, target, size)
        animateTo(target, targetOffset, size)
    }

    fun reset() {
        scale = 1f
        offset = Offset.Zero
    }

    private fun animateTo(targetScale: Float, targetOffset: Offset, size: IntSize) {
        val startScale = scale
        val startOffset = offset
        scope.launch {
            animate(0f, 1f, animationSpec = androidx.compose.animation.core.tween(220, easing = FastOutSlowInEasing)) { t, _ ->
                scale = startScale + (targetScale - startScale) * t
                offset = Offset(
                    startOffset.x + (targetOffset.x - startOffset.x) * t,
                    startOffset.y + (targetOffset.y - startOffset.y) * t,
                )
            }
            scale = targetScale
            offset = if (targetScale <= ZOOM_MIN) Offset.Zero else targetOffset
        }
    }
}

@Composable
fun rememberZoomState(): ZoomState {
    val scope = rememberCoroutineScope()
    return remember { ZoomState(scope) }
}

fun Modifier.zoomable(state: ZoomState, onSingleTap: () -> Unit): Modifier = this
    .pointerInput(Unit) {
        detectTapGestures(
            onTap = { onSingleTap() },
            onDoubleTap = { pos -> state.cycleZoom(pos, size) },
        )
    }
    .pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            do {
                val event = awaitPointerEvent()
                val pressedCount = event.changes.count { it.pressed }
                // At scale 1 a single finger must pass through to the pager; only handle
                // transforms when already zoomed or when at least two fingers are down (pinch).
                if (state.isZoomed || pressedCount >= 2) {
                    val zoom = event.calculateZoom()
                    val pan = event.calculatePan()
                    if (zoom != 1f || pan != androidx.compose.ui.geometry.Offset.Zero) {
                        state.onTransform(event.calculateCentroid(), pan, zoom, size)
                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                    }
                }
            } while (event.changes.any { it.pressed })
        }
    }

@Composable
fun ZoomMinimap(state: ZoomState, modifier: Modifier = Modifier, boxWidth: Dp = 84.dp) {
    val size = state.viewSize
    if (!state.isZoomed || size.width == 0 || size.height == 0) return
    val aspect = size.width.toFloat() / size.height.toFloat()
    val boxH = boxWidth / aspect
    val scale = state.scale
    val fracW = (1f / scale).coerceIn(0f, 1f)
    val fracH = (1f / scale).coerceIn(0f, 1f)
    val centerFracX = (0.5f - state.offset.x / (scale * size.width)).coerceIn(fracW / 2f, 1f - fracW / 2f)
    val centerFracY = (0.5f - state.offset.y / (scale * size.height)).coerceIn(fracH / 2f, 1f - fracH / 2f)

    Box(
        modifier
            .size(boxWidth, boxH)
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.45f))
    ) {
        androidx.compose.foundation.Canvas(Modifier.size(boxWidth, boxH)) {
            val w = this.size.width
            val h = this.size.height
            val rw = w * fracW
            val rh = h * fracH
            val left = w * centerFracX - rw / 2f
            val top = h * centerFracY - rh / 2f
            drawRect(
                color = Color.White,
                topLeft = Offset(left, top),
                size = androidx.compose.ui.geometry.Size(rw, rh),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()),
            )
        }
    }
}

package org.fossify.gallery.compose.screens.viewer
import org.fossify.gallery.compose.theme.AppMotion
import org.fossify.gallery.compose.theme.Radius
import org.fossify.gallery.compose.theme.Scrim

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

const val ZOOM_MIN = 1f
const val ZOOM_MAX = 6f

class ZoomState(private val scope: CoroutineScope) {
    var scale by mutableFloatStateOf(1f)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set
    var viewSize by mutableStateOf(IntSize.Zero)
        private set
    var contentAspect by mutableFloatStateOf(0f)
        private set

    fun updateContentAspect(a: Float) { if (a > 0f) contentAspect = a }

    val isZoomed: Boolean get() = scale > 1.01f

    var interacting by mutableStateOf(false)
        private set
    private var hideJob: Job? = null
    private fun markInteracting() {
        interacting = true
        hideJob?.cancel()
        hideJob = scope.launch { delay(900); interacting = false }
    }

    private val zoomLevels = listOf(1f, 2f, 4f)

    /** What [cycleZoom] would zoom to next, without actually triggering it - lets a caller decide
     * whether an imminent double-tap is zooming in (e.g. to switch to a full-resolution tiled
     * renderer) or back out to 1x, before the animation actually starts. */
    fun peekNextZoomLevel(): Float = zoomLevels.firstOrNull { it > scale + 0.01f } ?: 1f

    private fun clampOffset(candidate: Offset, s: Float, size: IntSize): Offset {
        val w = size.width.toFloat(); val h = size.height.toFloat()
        var dispW = w; var dispH = h
        if (contentAspect > 0f && w > 0f && h > 0f) {
            val viewAspect = w / h
            if (contentAspect > viewAspect) { dispW = w; dispH = w / contentAspect } else { dispH = h; dispW = h * contentAspect }
        }
        val maxX = ((s * dispW - w) / 2f).coerceAtLeast(0f)
        val maxY = ((s * dispH - h) / 2f).coerceAtLeast(0f)
        return Offset(candidate.x.coerceIn(-maxX, maxX), candidate.y.coerceIn(-maxY, maxY))
    }

    fun onTransform(centroid: Offset, pan: Offset, zoom: Float, size: IntSize) {
        viewSize = size
        markInteracting()
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
        markInteracting()
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

/**
 * @param onDoubleTap overrides the default zoom-cycle behavior on double-tap (e.g. VideoPage uses
 * this to seek when the tap lands on the left/right third of the screen, only zooming from the
 * center third) - receives the tap position and the element's current [IntSize] (the caller has no
 * other way to reach the enclosing PointerInputScope's `size`, since this lambda is written at the
 * call site, outside any `pointerInput{}` block, so it can't read an ambient `size` itself).
 * Passing null keeps the default zoom-cycle - this must be the *only* double-tap detector on the
 * element: an earlier version of VideoPage additionally registered its own separate
 * `detectTapGestures` alongside this modifier, so a double-tap fired both this zoom-cycle and the
 * caller's own seek handler at once, and its "allow video gestures" setting only gated the
 * caller's handler, not this one - a real, reproducible double-fire bug, not just untidy code.
 *
 * @param onZoomGestureStart fires once, the moment a second finger joins while at rest (scale 1) -
 * i.e. the start of a pinch-to-zoom. Used by ImagePage to hand off to a full-resolution tiled
 * renderer for the rest of the gesture; the initial pinch delta on the frame it fires is not
 * itself applied to [state] (that renderer takes over from here), so callers that pass this must
 * actually take over the gesture, or the first pinch will silently do nothing.
 */
fun Modifier.zoomable(
    state: ZoomState,
    onSingleTap: () -> Unit,
    onDoubleTap: ((Offset, IntSize) -> Unit)? = null,
    onZoomGestureStart: (() -> Unit)? = null,
): Modifier = this
    .pointerInput(onDoubleTap) {
        detectTapGestures(
            onTap = { onSingleTap() },
            onDoubleTap = { pos -> onDoubleTap?.invoke(pos, size) ?: state.cycleZoom(pos, size) },
        )
    }
    .pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            var zoomStartNotified = false
            do {
                val event = awaitPointerEvent()
                val pressedCount = event.changes.count { it.pressed }
                // At scale 1 a single finger must pass through to the pager; only handle
                // transforms when already zoomed or when at least two fingers are down (pinch).
                if (state.isZoomed || pressedCount >= 2) {
                    if (!state.isZoomed && !zoomStartNotified) { zoomStartNotified = true; onZoomGestureStart?.invoke() }
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
fun ZoomMinimap(state: ZoomState, modifier: Modifier = Modifier, boxWidth: Dp = 56.dp) {
    // viewSize/isZoomed/interacting only change at gesture start/end or on layout, so reading them
    // here is fine. scale/offset change on every pinch/pan frame though - those are read inside the
    // Canvas draw lambda below instead of as composable-body vals, so a pinch/pan only triggers a
    // redraw of this minimap, not a full recomposition of it on every touch-move event.
    val size = state.viewSize
    val visible = state.isZoomed && state.interacting && size.width > 0 && size.height > 0
    AnimatedVisibility(visible = visible, modifier = modifier, enter = fadeIn(AppMotion.medium), exit = fadeOut(AppMotion.medium)) {
        val aspect = (size.width / size.height.toFloat()).coerceAtLeast(0.01f)
        val boxH = boxWidth / aspect
        Box(Modifier.size(boxWidth, boxH).clip(RoundedCornerShape(Radius.sm)).background(Scrim.a22)) {
            androidx.compose.foundation.Canvas(Modifier.size(boxWidth, boxH)) {
                val scale = state.scale
                val fracW = (1f / scale).coerceIn(0f, 1f)
                val fracH = (1f / scale).coerceIn(0f, 1f)
                val centerFracX = (0.5f - state.offset.x / (scale * size.width)).coerceIn(fracW / 2f, 1f - fracW / 2f)
                val centerFracY = (0.5f - state.offset.y / (scale * size.height)).coerceIn(fracH / 2f, 1f - fracH / 2f)
                val w = this.size.width
                val h = this.size.height
                drawRect(
                    color = Color.White.copy(alpha = 0.85f),
                    topLeft = Offset(w * centerFracX - w * fracW / 2f, h * centerFracY - h * fracH / 2f),
                    size = androidx.compose.ui.geometry.Size(w * fracW, h * fracH),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx()),
                )
            }
        }
    }
}

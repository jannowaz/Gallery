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
internal const val DEFAULT_DOUBLE_TAP_ZOOM = 2f
internal const val MIN_MEANINGFUL_FILL_SCALE = 1.15f
private const val FILL_ZOOM_HEADROOM_MULTIPLIER = 2f
private const val ZOOM_ACTIVE_EPSILON = 0.01f
private const val SNAP_TO_FIT_MAX_SCALE = 1.06f
private const val SNAP_TO_FILL_TOLERANCE = 0.10f
private const val INTERACTING_HIDE_DELAY_MS = 900L
private const val ZOOM_ANIM_DURATION_MS = 220
private const val MIN_ASPECT_RATIO = 0.01f
private const val MINIMAP_RECT_ALPHA = 0.85f
private const val MINIMAP_STROKE_DP = 1.5f
private const val HALF = 0.5f

internal fun computeRawFillScale(viewWidth: Float, viewHeight: Float, contentAspect: Float): Float {
    if (viewWidth <= 0f || viewHeight <= 0f || contentAspect <= 0f) return ZOOM_MIN
    val viewAspect = viewWidth / viewHeight
    return maxOf(contentAspect / viewAspect, viewAspect / contentAspect)
}

internal fun computeFillScale(viewWidth: Float, viewHeight: Float, contentAspect: Float): Float {
    val rawFill = computeRawFillScale(viewWidth, viewHeight, contentAspect)
    return if (rawFill < MIN_MEANINGFUL_FILL_SCALE) DEFAULT_DOUBLE_TAP_ZOOM else rawFill
}

internal fun computeMaxZoomScale(fillScale: Float): Float =
    maxOf(ZOOM_MAX, fillScale * FILL_ZOOM_HEADROOM_MULTIPLIER)

internal fun computeDisplaySize(viewWidth: Float, viewHeight: Float, contentAspect: Float): Pair<Float, Float> {
    if (contentAspect <= 0f || viewWidth <= 0f || viewHeight <= 0f) return viewWidth to viewHeight
    val viewAspect = viewWidth / viewHeight
    return if (contentAspect > viewAspect) {
        viewWidth to (viewWidth / contentAspect)
    } else {
        (viewHeight * contentAspect) to viewHeight
    }
}

class ZoomState(private val scope: CoroutineScope) {
    var scale by mutableFloatStateOf(ZOOM_MIN)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set
    var viewSize by mutableStateOf(IntSize.Zero)
        private set
    var contentAspect by mutableFloatStateOf(0f)
        private set

    fun updateContentAspect(a: Float) {
        if (a > 0f) {
            contentAspect = a
            if (isZoomed && viewSize.width > 0 && viewSize.height > 0) {
                offset = clampOffset(offset, scale, viewSize)
            }
        }
    }

    fun updateViewSize(size: IntSize) {
        if (size == viewSize) return
        viewSize = size
        if (isZoomed && size.width > 0 && size.height > 0) {
            offset = clampOffset(offset, scale, size)
        }
    }

    val isZoomed: Boolean get() = scale > ZOOM_MIN + ZOOM_ACTIVE_EPSILON

    val fillScale: Float
        get() = computeFillScale(viewSize.width.toFloat(), viewSize.height.toFloat(), contentAspect)

    var interacting by mutableStateOf(false)
        private set
    private var hideJob: Job? = null
    private fun markInteracting() {
        interacting = true
        hideJob?.cancel()
        hideJob = scope.launch { delay(INTERACTING_HIDE_DELAY_MS); interacting = false }
    }

    private fun clampOffset(candidate: Offset, s: Float, size: IntSize): Offset {
        val w = size.width.toFloat()
        val h = size.height.toFloat()
        val (dispW, dispH) = computeDisplaySize(w, h, contentAspect)
        val maxX = ((s * dispW - w) / 2f).coerceAtLeast(0f)
        val maxY = ((s * dispH - h) / 2f).coerceAtLeast(0f)
        return Offset(candidate.x.coerceIn(-maxX, maxX), candidate.y.coerceIn(-maxY, maxY))
    }

    fun onTransform(centroid: Offset, pan: Offset, zoom: Float, size: IntSize) {
        viewSize = size
        markInteracting()
        // A live pinch must win over a still-running double-tap zoom animation, or the two fight over
        // scale/offset frame-by-frame.
        animJob?.cancel()
        val maxScale = computeMaxZoomScale(fillScale)
        val newScale = (scale * zoom).coerceIn(ZOOM_MIN, maxScale)
        val center = Offset(size.width / 2f, size.height / 2f)
        val d = centroid - center
        val k = if (scale == 0f) 1f else newScale / scale
        val focal = d * (1f - k) + offset * k
        scale = newScale
        offset = if (newScale <= ZOOM_MIN) Offset.Zero else clampOffset(focal + pan, newScale, size)
    }

    fun cycleZoom(
        tap: Offset? = null,
        size: IntSize = viewSize,
        preferFill: Boolean = false,
    ): Boolean {
        if (size.width > 0 && size.height > 0) viewSize = size
        markInteracting()
        val target = if (isZoomed) {
            ZOOM_MIN
        } else if (preferFill) {
            fillScale
        } else {
            DEFAULT_DOUBLE_TAP_ZOOM
        }
        val activeSize = if (size.width > 0 && size.height > 0) size else viewSize
        val center = Offset(activeSize.width / 2f, activeSize.height / 2f)
        val focalPoint = tap ?: center
        val d = focalPoint - center
        val k = if (scale == 0f) 1f else target / scale
        val focal = d * (1f - k) + offset * k
        val targetOffset = if (target <= ZOOM_MIN) {
            Offset.Zero
        } else {
            clampOffset(focal, target, activeSize)
        }
        animateTo(target, targetOffset)
        return target > ZOOM_MIN + ZOOM_ACTIVE_EPSILON
    }

    fun snapToFillIfClose(): Boolean {
        if (viewSize.width <= 0 || viewSize.height <= 0) return false
        if (scale > ZOOM_MIN && scale <= SNAP_TO_FIT_MAX_SCALE) {
            animateTo(ZOOM_MIN, Offset.Zero)
            return false
        }
        val rawFill = computeRawFillScale(
            viewSize.width.toFloat(),
            viewSize.height.toFloat(),
            contentAspect,
        )
        if (rawFill >= MIN_MEANINGFUL_FILL_SCALE &&
            abs(scale - rawFill) / rawFill <= SNAP_TO_FILL_TOLERANCE
        ) {
            val k = if (scale == 0f) 1f else rawFill / scale
            val targetOffset = clampOffset(offset * k, rawFill, viewSize)
            animateTo(rawFill, targetOffset)
            return true
        }
        return false
    }

    fun reset(animated: Boolean = false) {
        if (animated) {
            markInteracting()
            animateTo(ZOOM_MIN, Offset.Zero)
        } else {
            animJob?.cancel()
            scale = ZOOM_MIN
            offset = Offset.Zero
        }
    }

    private var animJob: Job? = null
    private fun animateTo(targetScale: Float, targetOffset: Offset) {
        val startScale = scale
        val startOffset = offset
        // Cancel any in-flight zoom animation first: a second quick double-tap otherwise launches a
        // parallel interpolation that reads a mid-animation scale as its start and then fights the
        // first one, landing at the wrong zoom level with visible jitter.
        animJob?.cancel()
        animJob = scope.launch {
            val spec = androidx.compose.animation.core.tween<Float>(
                ZOOM_ANIM_DURATION_MS,
                easing = FastOutSlowInEasing,
            )
            animate(0f, 1f, animationSpec = spec) { t, _ ->
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
    preferFillOnDoubleTap: Boolean = false,
    snapToFill: Boolean = false,
    onSnapToFill: (() -> Unit)? = null,
): Modifier = this
    .pointerInput(onDoubleTap, preferFillOnDoubleTap) {
        detectTapGestures(
            onTap = { onSingleTap() },
            onDoubleTap = { pos ->
                onDoubleTap?.invoke(pos, size)
                    ?: state.cycleZoom(pos, size, preferFill = preferFillOnDoubleTap)
            },
        )
    }
    .pointerInput(snapToFill, onSnapToFill) {
        handleZoomTransformGestures(
            state = state,
            onZoomGestureStart = onZoomGestureStart,
            snapToFill = snapToFill,
            onSnapToFill = onSnapToFill,
        )
    }

private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.handleZoomTransformGestures(
    state: ZoomState,
    onZoomGestureStart: (() -> Unit)?,
    snapToFill: Boolean,
    onSnapToFill: (() -> Unit)?,
) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var zoomStartNotified = false
        var didPinch = false
        do {
            val event = awaitPointerEvent()
            val pressedCount = event.changes.count { it.pressed }
            if (pressedCount >= 2) didPinch = true
            // At scale 1 a single finger must pass through to the pager; only handle
            // transforms when already zoomed or when at least two fingers are down (pinch).
            if (state.isZoomed || pressedCount >= 2) {
                if (!state.isZoomed && !zoomStartNotified) {
                    zoomStartNotified = true
                    onZoomGestureStart?.invoke()
                }
                val zoom = event.calculateZoom()
                val pan = event.calculatePan()
                if (zoom != 1f || pan != Offset.Zero) {
                    state.onTransform(event.calculateCentroid(), pan, zoom, size)
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                }
            }
        } while (event.changes.any { it.pressed })
        if (snapToFill && didPinch && state.snapToFillIfClose()) {
            onSnapToFill?.invoke()
        }
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
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(AppMotion.medium),
        exit = fadeOut(AppMotion.medium),
    ) {
        val frameAspect = if (state.contentAspect > 0f) {
            state.contentAspect
        } else {
            size.width / size.height.toFloat()
        }.coerceAtLeast(MIN_ASPECT_RATIO)
        val boxH = boxWidth / frameAspect
        Box(Modifier.size(boxWidth, boxH).clip(RoundedCornerShape(Radius.sm)).background(Scrim.a22)) {
            androidx.compose.foundation.Canvas(Modifier.size(boxWidth, boxH)) {
                val scale = state.scale
                val viewW = size.width.toFloat()
                val viewH = size.height.toFloat()
                val (dispW, dispH) = computeDisplaySize(viewW, viewH, state.contentAspect)
                val scaledW = (scale * dispW).coerceAtLeast(1f)
                val scaledH = (scale * dispH).coerceAtLeast(1f)
                val fracW = (viewW / scaledW).coerceIn(0f, 1f)
                val fracH = (viewH / scaledH).coerceIn(0f, 1f)
                val centerFracX = (HALF - state.offset.x / scaledW)
                    .coerceIn(fracW / 2f, 1f - fracW / 2f)
                val centerFracY = (HALF - state.offset.y / scaledH)
                    .coerceIn(fracH / 2f, 1f - fracH / 2f)
                val w = this.size.width
                val h = this.size.height
                drawRect(
                    color = Color.White.copy(alpha = MINIMAP_RECT_ALPHA),
                    topLeft = Offset(
                        w * centerFracX - w * fracW / 2f,
                        h * centerFracY - h * fracH / 2f,
                    ),
                    size = androidx.compose.ui.geometry.Size(w * fracW, h * fracH),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = MINIMAP_STROKE_DP.dp.toPx(),
                    ),
                )
            }
        }
    }
}

package org.fossify.gallery.compose.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.fossify.gallery.R
import kotlin.math.roundToInt

private const val AUTO_HIDE_DELAY_MS = 1500L
private const val IDLE_RAIL_ALPHA = 0.72f
private const val MIN_THUMB_FRACTION = 0.08f
private const val MAX_THUMB_FRACTION = 0.28f
private const val PERCENT_SCALE = 100

/**
 * A narrow, always-available fast-scroll affordance for long lazy lists.
 *
 * The visible thumb is intentionally smaller than the 48dp interaction rail. This keeps it
 * visually quiet while still giving the user a forgiving touch target. Scrolling uses the lazy
 * list's item index directly, so no media objects are materialized and large Explorer folders stay
 * cheap even while the thumb is dragged.
 */
@Composable
fun FastScrollBar(
    state: LazyListState,
    modifier: Modifier = Modifier,
    positionLabel: (Int) -> String? = { null },
) {
    val metrics by remember(state) {
        derivedStateOf {
            fastScrollMetrics(
                total = state.layoutInfo.totalItemsCount,
                visible = state.layoutInfo.visibleItemsInfo.size,
                first = state.firstVisibleItemIndex,
            )
        }
    }
    FastScrollTrack(
        metrics = metrics,
        isScrolling = state.isScrollInProgress,
        positionLabel = positionLabel,
        modifier = modifier,
    ) { index -> state.scrollToItem(index) }
}

/** Fast-scroll variant for album/container grids. The index is an item index, so seeking remains
 * cheap even when a collection contains thousands of tiles; LazyVerticalGrid handles row layout. */
@Composable
fun FastScrollBar(
    state: LazyGridState,
    modifier: Modifier = Modifier,
    positionLabel: (Int) -> String? = { null },
) {
    val metrics by remember(state) {
        derivedStateOf {
            fastScrollMetrics(
                total = state.layoutInfo.totalItemsCount,
                visible = state.layoutInfo.visibleItemsInfo.size,
                first = state.firstVisibleItemIndex,
            )
        }
    }
    FastScrollTrack(
        metrics = metrics,
        isScrolling = state.isScrollInProgress,
        positionLabel = positionLabel,
        modifier = modifier,
    ) { index -> state.scrollToItem(index) }
}

/** Fast-scroll variant for the variable-height media mosaic. */
@Composable
fun FastScrollBar(
    state: LazyStaggeredGridState,
    modifier: Modifier = Modifier,
    positionLabel: (Int) -> String? = { null },
) {
    val metrics by remember(state) {
        derivedStateOf {
            fastScrollMetrics(
                total = state.layoutInfo.totalItemsCount,
                visible = state.layoutInfo.visibleItemsInfo.size,
                first = state.firstVisibleItemIndex,
            )
        }
    }
    FastScrollTrack(
        metrics = metrics,
        isScrolling = state.isScrollInProgress,
        positionLabel = positionLabel,
        modifier = modifier,
    ) { index -> state.scrollToItem(index) }
}

@Composable
private fun FastScrollTrack(
    metrics: Metrics,
    isScrolling: Boolean,
    positionLabel: (Int) -> String?,
    modifier: Modifier = Modifier,
    scrollTo: suspend (Int) -> Unit,
) {
    if (metrics.visible <= 0 || metrics.total <= metrics.visible) return

    val scrollScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val latestPositionLabel by rememberUpdatedState(positionLabel)
    val fastScrollLabel = stringResource(R.string.fast_scroll)
    val startLabel = stringResource(R.string.fast_scroll_to_start)
    val endLabel = stringResource(R.string.fast_scroll_to_end)

    var isDragging by remember { mutableStateOf(false) }
    var railVisible by remember { mutableStateOf(false) }
    var dragTargetIndex by remember { mutableIntStateOf(metrics.first) }
    var dragFraction by remember { mutableFloatStateOf(metrics.scrollFraction) }
    var trackHeightPx by remember { mutableIntStateOf(0) }
    var lastHapticLabel by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(isScrolling, isDragging) {
        if (isScrolling || isDragging) {
            railVisible = true
        } else {
            delay(AUTO_HIDE_DELAY_MS)
            railVisible = false
        }
    }

    val currentPercent = (metrics.scrollFraction * PERCENT_SCALE).roundToInt()
    val semanticPosition = positionLabel(metrics.first)?.takeIf { it.isNotBlank() }
    val railAlpha by animateFloatAsState(
        targetValue = when {
            isDragging -> 1f
            railVisible -> IDLE_RAIL_ALPHA
            else -> 0f
        },
        label = "fastScrollRailAlpha",
    )
    val thumbWidth by animateDpAsState(
        targetValue = if (isDragging) 8.dp else 6.dp,
        label = "fastScrollThumbWidth",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .onSizeChanged { trackHeightPx = it.height },
    ) {
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                // Keep the idle edge rail slim (20dp) and auto-hidden at rest so it never
                // obscures right-column video badges on dense grids, while widening during drag.
                .width(if (isDragging) 48.dp else 20.dp)
                .fillMaxHeight()
                .graphicsLayer { alpha = railAlpha }
                .semantics {
                    progressBarRangeInfo = ProgressBarRangeInfo(metrics.scrollFraction, 0f..1f)
                    contentDescription = fastScrollLabel
                    stateDescription = semanticPosition?.let { "$it, $currentPercent%" } ?: "$currentPercent%"
                    customActions = listOf(
                        CustomAccessibilityAction(startLabel) {
                            scrollScope.launch { scrollTo(0) }
                            true
                        },
                        CustomAccessibilityAction(endLabel) {
                            scrollScope.launch { scrollTo(metrics.maxFirst) }
                            true
                        },
                    )
                }
                .pointerInput(metrics.total, metrics.visible, metrics.maxFirst) {
                    var scrollJob: Job? = null
                    fun updateTarget(y: Float) {
                        val fraction = (y / size.height.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                        dragFraction = fraction
                        val idx = (fraction * metrics.maxFirst).roundToInt().coerceIn(0, metrics.maxFirst)
                        dragTargetIndex = idx
                        val label = latestPositionLabel(idx)?.takeIf { it.isNotBlank() }
                        if (label != null && label != lastHapticLabel) {
                            lastHapticLabel = label
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        scrollJob?.cancel()
                        scrollJob = scrollScope.launch { scrollTo(idx) }
                    }

                    detectDragGestures(
                        onDragStart = { start ->
                            isDragging = true
                            lastHapticLabel = null
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            updateTarget(start.y)
                        },
                        onDragEnd = {
                            isDragging = false
                            lastHapticLabel = null
                        },
                        onDragCancel = {
                            isDragging = false
                            lastHapticLabel = null
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            updateTarget(change.position.y)
                        },
                    )
                },
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(4.dp)
                    .padding(vertical = 2.dp)
                    .background(
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                        RoundedCornerShape(2.dp),
                    ),
            )
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset {
                        val activeFraction = if (isDragging) dragFraction else metrics.scrollFraction
                        val travelPx = trackHeightPx * (1f - metrics.thumbFraction)
                        IntOffset(0, (travelPx * activeFraction).roundToInt())
                    }
                    .width(thumbWidth)
                    .fillMaxHeight(metrics.thumbFraction)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.90f),
                        RoundedCornerShape(4.dp),
                    ),
            )
        }
        if (isDragging) {
            FastScrollSectionBubble(
                label = resolveFastScrollLabel(positionLabel, dragTargetIndex, metrics.maxFirst),
                dragFraction = dragFraction,
                trackHeightPx = trackHeightPx,
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
    }
}

private fun resolveFastScrollLabel(
    positionLabel: (Int) -> String?,
    dragTargetIndex: Int,
    maxFirst: Int,
): String {
    val targetPercent = if (maxFirst > 0) {
        ((dragTargetIndex.toFloat() / maxFirst) * PERCENT_SCALE).roundToInt()
    } else {
        0
    }
    return positionLabel(dragTargetIndex)?.takeIf { it.isNotBlank() } ?: "$targetPercent%"
}

@Composable
private fun FastScrollSectionBubble(
    label: String,
    dragFraction: Float,
    trackHeightPx: Int,
    modifier: Modifier = Modifier,
) {
    val bubbleHeightPx = with(LocalDensity.current) { 44.dp.roundToPx() }
    Surface(
        modifier = modifier
            .offset {
                val maxOffsetY = (trackHeightPx - bubbleHeightPx).coerceAtLeast(0)
                val y = (dragFraction * trackHeightPx - bubbleHeightPx / 2f)
                    .roundToInt()
                    .coerceIn(0, maxOffsetY)
                IntOffset(0, y)
            }
            .padding(start = 16.dp, end = 56.dp)
            .widthIn(min = 84.dp, max = 220.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shadowElevation = 6.dp,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

private fun fastScrollMetrics(total: Int, visible: Int, first: Int): Metrics {
    if (visible <= 0 || total <= visible) {
        return Metrics(total, visible.coerceAtLeast(0), 1, 0, 0f, 1f)
    }
    val maxFirst = (total - visible).coerceAtLeast(1)
    val safeFirst = first.coerceIn(0, maxFirst)
    val scrollFraction = safeFirst.toFloat() / maxFirst
    val thumbFraction = (visible.toFloat() / total).coerceIn(MIN_THUMB_FRACTION, MAX_THUMB_FRACTION)
    return Metrics(total, visible, maxFirst, safeFirst, scrollFraction, thumbFraction)
}

private data class Metrics(
    val total: Int,
    val visible: Int,
    val maxFirst: Int,
    val first: Int,
    val scrollFraction: Float,
    val thumbFraction: Float,
)

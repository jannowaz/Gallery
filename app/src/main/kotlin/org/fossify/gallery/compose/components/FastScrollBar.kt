package org.fossify.gallery.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import org.fossify.gallery.R
import androidx.compose.ui.res.stringResource

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
    FastScrollTrack(metrics, state.isScrollInProgress, modifier, positionLabel) { index -> state.scrollToItem(index) }
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
    FastScrollTrack(metrics, state.isScrollInProgress, modifier, positionLabel) { index -> state.scrollToItem(index) }
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
    FastScrollTrack(metrics, state.isScrollInProgress, modifier, positionLabel) { index -> state.scrollToItem(index) }
}

@Composable
private fun FastScrollTrack(
    metrics: Metrics,
    isScrolling: Boolean,
    modifier: Modifier,
    positionLabel: (Int) -> String?,
    scrollTo: suspend (Int) -> Unit,
) {
    if (metrics.visible <= 0 || metrics.total <= metrics.visible) return

    val scrollScope = rememberCoroutineScope()
    val fastScrollLabel = stringResource(R.string.fast_scroll)
    val startLabel = stringResource(R.string.fast_scroll_to_start)
    val endLabel = stringResource(R.string.fast_scroll_to_end)
    var isDragging by remember { mutableStateOf(false) }
    var railVisible by remember { mutableStateOf(false) }
    LaunchedEffect(isScrolling, isDragging) {
        if (isScrolling || isDragging) {
            railVisible = true
        } else {
            kotlinx.coroutines.delay(1500L)
            railVisible = false
        }
    }
    var dragTargetIndex by remember { mutableStateOf(metrics.first) }
    val currentPercent = (metrics.scrollFraction * 100).roundToInt()
    val semanticPosition = positionLabel(metrics.first)?.takeIf { it.isNotBlank() }
    val railAlpha by animateFloatAsState(
        targetValue = when {
            isDragging -> 1f
            railVisible -> 0.72f
            else -> 0f
        },
        label = "fastScrollRailAlpha",
    )
    var trackHeightPx by remember { mutableIntStateOf(0) }
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
                    var scrollJob: kotlinx.coroutines.Job? = null
                    fun targetAt(y: Float): Int {
                        val fraction = (y / size.height.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                        return (fraction * metrics.maxFirst).roundToInt().coerceIn(0, metrics.maxFirst)
                    }

                    detectDragGestures(
                        onDragStart = { start ->
                            isDragging = true
                            dragTargetIndex = targetAt(start.y)
                            scrollJob?.cancel()
                            scrollJob = scrollScope.launch { scrollTo(dragTargetIndex) }
                        },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false },
                        onDrag = { change, _ ->
                            change.consume()
                            dragTargetIndex = targetAt(change.position.y)
                            scrollJob?.cancel()
                            scrollJob = scrollScope.launch { scrollTo(dragTargetIndex) }
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
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f), RoundedCornerShape(2.dp)),
            )
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset {
                        val travelPx = trackHeightPx * (1f - metrics.thumbFraction)
                        IntOffset(0, (travelPx * metrics.scrollFraction).roundToInt())
                    }
                    .width(6.dp)
                    .fillMaxHeight(metrics.thumbFraction)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.88f), RoundedCornerShape(3.dp)),
            )
        }
        if (isDragging) {
            val targetPercent = if (metrics.maxFirst > 0) {
                ((dragTargetIndex.toFloat() / metrics.maxFirst) * 100).roundToInt()
            } else {
                0
            }
            val label = positionLabel(dragTargetIndex)?.takeIf { it.isNotBlank() } ?: "$targetPercent%"
            Surface(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(start = 16.dp, end = 56.dp)
                    .widthIn(min = 96.dp, max = 220.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                shadowElevation = 6.dp,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}

private fun fastScrollMetrics(total: Int, visible: Int, first: Int): Metrics {
    if (visible <= 0 || total <= visible) {
        return Metrics(total, visible.coerceAtLeast(0), 1, 0, 0f, 1f)
    }
    val maxFirst = (total - visible).coerceAtLeast(1)
    val safeFirst = first.coerceIn(0, maxFirst)
    val scrollFraction = safeFirst.toFloat() / maxFirst
    val thumbFraction = (visible.toFloat() / total).coerceIn(0.08f, 0.28f)
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

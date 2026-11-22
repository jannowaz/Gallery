package org.fossify.gallery.compose.util

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.fossify.gallery.R
import org.fossify.gallery.compose.theme.AppMotion
import kotlin.math.abs

private const val ZOOM_OUT_THRESHOLD = 1.25f
private const val ZOOM_IN_THRESHOLD = 0.80f
private const val ZOOM_SLOP = 0.04f
private const val INDICATOR_DURATION_MS = 900L

/**
 * Maps an accumulated two-finger pinch zoom factor to the next column count.
 * Pinching outward ([zoomFactor] >= [ZOOM_OUT_THRESHOLD]) enlarges tiles (fewer columns);
 * pinching inward ([zoomFactor] <= [ZOOM_IN_THRESHOLD]) shrinks tiles (more columns).
 */
internal fun computePinchedColumnCount(
    currentColumns: Int,
    zoomFactor: Float,
    minColumns: Int = 2,
    maxColumns: Int = 6,
): Int {
    val next = when {
        zoomFactor >= ZOOM_OUT_THRESHOLD -> currentColumns - 1
        zoomFactor <= ZOOM_IN_THRESHOLD -> currentColumns + 1
        else -> currentColumns
    }
    return next.coerceIn(minColumns, maxColumns)
}

/**
 * Lets the user pinch with two fingers on a media or album grid to step the column count up/down.
 * Single-finger scrolling, taps, long-presses, and drag-selection remain completely untouched.
 */
fun Modifier.pinchToChangeColumns(
    enabled: Boolean,
    currentColumns: Int,
    minColumns: Int = 2,
    maxColumns: Int = 6,
    onColumnsChange: (Int) -> Unit,
): Modifier = if (!enabled) {
    this
} else {
    composed {
        val latestColumns by rememberUpdatedState(currentColumns)
        val latestOnChange by rememberUpdatedState(onColumnsChange)
        val haptic = LocalHapticFeedback.current

        pointerInput(minColumns, maxColumns) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                var accumulatedZoom = 1f
                var pinchActive = false

                do {
                    val event = awaitPointerEvent()
                    val pressedCount = event.changes.count { it.pressed }
                    if (pressedCount >= 2) {
                        val zoom = event.calculateZoom()
                        if (zoom != 1f) {
                            accumulatedZoom *= zoom
                            if (abs(accumulatedZoom - 1f) > ZOOM_SLOP) {
                                pinchActive = true
                            }
                            if (pinchActive) {
                                event.changes.forEach { change ->
                                    if (change.positionChanged()) change.consume()
                                }
                                val next = computePinchedColumnCount(
                                    currentColumns = latestColumns,
                                    zoomFactor = accumulatedZoom,
                                    minColumns = minColumns,
                                    maxColumns = maxColumns,
                                )
                                if (next != latestColumns) {
                                    latestOnChange(next)
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    accumulatedZoom = 1f
                                }
                            }
                        }
                    } else if (pinchActive) {
                        accumulatedZoom = 1f
                    }
                } while (event.changes.any { it.pressed })
            }
        }
    }
}

/** Brief, non-intrusive pill indicator shown when the user changes column density via pinch. */
@Composable
fun ColumnCountIndicator(
    columnCount: Int,
    visible: Boolean,
    onAutoHide: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val latestOnAutoHide by rememberUpdatedState(onAutoHide)
    LaunchedEffect(visible, columnCount) {
        if (visible) {
            delay(INDICATOR_DURATION_MS)
            latestOnAutoHide()
        }
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(AppMotion.short),
        exit = fadeOut(AppMotion.medium),
        modifier = modifier,
    ) {
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            shadowElevation = 4.dp,
        ) {
            Text(
                text = "${stringResource(R.string.columns)}: $columnCount",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

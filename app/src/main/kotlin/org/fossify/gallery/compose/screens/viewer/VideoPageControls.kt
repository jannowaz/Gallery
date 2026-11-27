package org.fossify.gallery.compose.screens.viewer

import android.graphics.Bitmap
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.fossify.gallery.R
import org.fossify.gallery.compose.theme.AppMotion
import org.fossify.gallery.compose.theme.BlurRadius
import org.fossify.gallery.compose.theme.Radius
import org.fossify.gallery.compose.theme.Scrim
import org.fossify.gallery.compose.util.BlurState
import org.fossify.gallery.compose.util.privacyBlur
import java.util.Locale

private const val MILLIS_PER_SECOND = 1000
private const val SECONDS_PER_MINUTE = 60
private const val INACTIVE_TRACK_ALPHA = 0.3f
private const val CENTER_ICON_ALPHA = 0.92f
private const val VIDEO_SEEKBAR_BOTTOM_OFFSET_DP = 58
private const val SCRUB_PREVIEW_BOTTOM_DP = 116
private const val SCRUB_PREVIEW_WIDTH_DP = 160
private const val SCRUB_PREVIEW_HEIGHT_DP = 90
private const val ZOOM_HUD_HIDE_MS = 1100L

internal class VideoZoomHudState {
    var labelResId by mutableStateOf<Int?>(null)
        private set
    var seq by mutableIntStateOf(0)
        private set

    fun show(filled: Boolean) {
        labelResId = if (filled) R.string.video_scale_zoom else R.string.video_scale_fit
        seq++
    }

    fun clear() {
        labelResId = null
    }
}

@Composable
internal fun rememberVideoZoomHudState(key: Any): VideoZoomHudState {
    val state = remember(key) { VideoZoomHudState() }
    LaunchedEffect(state.seq) {
        if (state.seq > 0) {
            delay(ZOOM_HUD_HIDE_MS)
            state.clear()
        }
    }
    return state
}

internal fun toggleVideoFillZoom(
    zoom: ZoomState,
    scalingMode: Int,
    onScalingModeChange: (Int) -> Unit,
): Boolean = if (scalingMode != SCALING_FIT) {
    onScalingModeChange(SCALING_FIT)
    zoom.reset(animated = true)
    false
} else {
    zoom.cycleZoom(preferFill = true)
}

internal fun nextVideoPlaybackSpeed(current: Float): Float {
    val idx = VIDEO_PLAYBACK_SPEEDS.indexOf(current).coerceAtLeast(0)
    return VIDEO_PLAYBACK_SPEEDS[(idx + 1) % VIDEO_PLAYBACK_SPEEDS.size]
}

internal fun formatPlaybackSpeed(speed: Float): String =
    if (speed % 1f == 0f) "${speed.toInt()}x" else "${speed}x"

@Composable
internal fun VideoPageOverlayControls(
    isPlaying: Boolean,
    isFilledZoom: Boolean,
    durationMs: Long,
    positionMs: Long,
    scrubFraction: Float,
    frameCache: List<Bitmap>,
    onTogglePlayPause: () -> Unit,
    onToggleFillZoom: () -> Unit,
    onSeekDrag: (Float) -> Unit,
    onSeekFinish: (Long) -> Unit,
) {
    val isScrubbing = scrubFraction >= 0f
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = !isScrubbing,
            enter = fadeIn(AppMotion.short),
            exit = fadeOut(AppMotion.short),
            modifier = Modifier.align(Alignment.Center),
        ) {
            IconButton(
                onClick = onTogglePlayPause,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Scrim.a35),
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = stringResource(R.string.cd_play_pause),
                    tint = Color.White.copy(alpha = CENTER_ICON_ALPHA),
                    modifier = Modifier.size(24.dp),
                )
            }
        }

        if (durationMs > 0L) {
            VideoSeekBar(
                durationMs = durationMs,
                positionMs = positionMs,
                isFilledZoom = isFilledZoom,
                onToggleFillZoom = onToggleFillZoom,
                onSeekDrag = onSeekDrag,
                onSeekFinish = onSeekFinish,
            )
            if (isScrubbing && frameCache.isNotEmpty()) {
                VideoScrubPreview(
                    scrubFraction = scrubFraction,
                    durationMs = durationMs,
                    frameCache = frameCache,
                )
            }
        }
    }
}

@Composable
internal fun VideoZoomHudPill(
    @StringRes labelResId: Int?,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = labelResId != null,
        modifier = modifier,
        enter = fadeIn(AppMotion.short),
        exit = fadeOut(AppMotion.medium),
    ) {
        if (labelResId != null) {
            Surface(shape = RoundedCornerShape(Radius.xl), color = Scrim.a85) {
                Text(
                    text = stringResource(labelResId),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun BoxScope.VideoSeekBar(
    durationMs: Long,
    positionMs: Long,
    isFilledZoom: Boolean,
    onToggleFillZoom: () -> Unit,
    onSeekDrag: (Float) -> Unit,
    onSeekFinish: (Long) -> Unit,
) {
    var seekPos by remember { mutableFloatStateOf(-1f) }
    Surface(
        shape = RoundedCornerShape(Radius.xl),
        color = Scrim.a50,
        modifier = Modifier
            .fillMaxWidth()
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(start = 12.dp, end = 12.dp, bottom = VIDEO_SEEKBAR_BOTTOM_OFFSET_DP.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val displayedPosMs = if (seekPos >= 0f) seekPos else positionMs.toFloat()
            val tabularLabel = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum")
            Text(
                formatVideoSeconds((displayedPosMs / MILLIS_PER_SECOND).toInt()),
                style = tabularLabel,
                color = Color.White,
            )
            Slider(
                value = (displayedPosMs / durationMs.toFloat()).coerceIn(0f, 1f),
                onValueChange = { fraction ->
                    seekPos = fraction * durationMs
                    onSeekDrag(fraction)
                },
                onValueChangeFinished = {
                    val finalPos = if (seekPos >= 0f) seekPos.toLong() else positionMs
                    seekPos = -1f
                    onSeekFinish(finalPos)
                },
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = INACTIVE_TRACK_ALPHA),
                ),
            )
            Text(
                formatVideoSeconds((durationMs / MILLIS_PER_SECOND).toInt()),
                style = tabularLabel,
                color = Color.White,
            )
            IconButton(
                onClick = onToggleFillZoom,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(40.dp)
                    .then(
                        if (isFilledZoom) {
                            Modifier.clip(CircleShape).background(Scrim.a40)
                        } else {
                            Modifier
                        },
                    ),
            ) {
                Icon(
                    imageVector = if (isFilledZoom) {
                        Icons.Default.FullscreenExit
                    } else {
                        Icons.Default.Fullscreen
                    },
                    contentDescription = stringResource(
                        if (isFilledZoom) R.string.video_scale_fit else R.string.video_scale_zoom,
                    ),
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun BoxScope.VideoScrubPreview(
    scrubFraction: Float,
    durationMs: Long,
    frameCache: List<Bitmap>,
) {
    val frameIndex = (scrubFraction * (frameCache.size - 1))
        .toInt()
        .coerceIn(0, frameCache.lastIndex)
    val previewBmp = frameCache[frameIndex]
    Box(
        Modifier
            .fillMaxWidth()
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(bottom = SCRUB_PREVIEW_BOTTOM_DP.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(shape = RoundedCornerShape(Radius.sm), color = Scrim.a85) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    bitmap = previewBmp.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(width = SCRUB_PREVIEW_WIDTH_DP.dp, height = SCRUB_PREVIEW_HEIGHT_DP.dp)
                        .privacyBlur(BlurRadius.scrubPreview, BlurState.enabled),
                    contentScale = ContentScale.Crop,
                )
                val scrubSec = ((scrubFraction * durationMs) / MILLIS_PER_SECOND).toInt()
                Text(
                    formatVideoSeconds(scrubSec),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                    modifier = Modifier.padding(4.dp),
                )
            }
        }
    }
}

internal fun formatVideoSeconds(totalSeconds: Int): String {
    val safeSeconds = totalSeconds.coerceAtLeast(0)
    val mins = safeSeconds / SECONDS_PER_MINUTE
    val secs = safeSeconds % SECONDS_PER_MINUTE
    return String.format(Locale.ROOT, "%02d:%02d", mins, secs)
}

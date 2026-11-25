package org.fossify.gallery.compose.screens.viewer

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.fossify.gallery.R
import org.fossify.gallery.compose.theme.BlurRadius
import org.fossify.gallery.compose.theme.Radius
import org.fossify.gallery.compose.theme.Scrim
import org.fossify.gallery.compose.theme.TrimEndColor
import org.fossify.gallery.compose.theme.TrimStartColor
import org.fossify.gallery.compose.util.BlurState
import org.fossify.gallery.compose.util.privacyBlur
import java.util.Locale
import kotlin.math.abs

private const val MILLIS_PER_SECOND = 1000
private const val SECONDS_PER_MINUTE = 60
private const val INACTIVE_TRACK_ALPHA = 0.3f
private const val CANCEL_BUTTON_ALPHA = 0.85f
private const val DISABLED_SAVE_ALPHA = 0.4f
private const val SCRUB_PREVIEW_BOTTOM_DP = 124
private const val SCRUB_PREVIEW_WIDTH_DP = 160
private const val SCRUB_PREVIEW_HEIGHT_DP = 90
private const val TRIM_BAR_BOTTOM_OFFSET_DP = 64

@Composable
internal fun VideoPageOverlayControls(
    isPlaying: Boolean,
    isMuted: Boolean,
    playbackSpeed: Float,
    moreControlsOpen: Boolean,
    trimMode: Boolean,
    isSavingTrim: Boolean,
    durationMs: Long,
    positionMs: Long,
    scrubFraction: Float,
    frameCache: List<Bitmap>,
    trimStartMs: Float,
    trimEndMs: Float,
    onTogglePlayPause: () -> Unit,
    onToggleMute: () -> Unit,
    onOpenMoreControls: () -> Unit,
    onDismissMoreControls: () -> Unit,
    onCycleSpeed: (Float) -> Unit,
    onEnterPip: () -> Unit,
    onToggleTrimMode: () -> Unit,
    onSeekDrag: (Float) -> Unit,
    onSeekFinish: (Long) -> Unit,
    onUpdateTrimRange: (startMs: Float, endMs: Float, previewSeekMs: Long) -> Unit,
    onCancelTrim: () -> Unit,
    onSaveTrim: (startMs: Float, endMs: Float) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        IconButton(
            onClick = onTogglePlayPause,
            modifier = Modifier
                .align(Alignment.Center)
                .size(56.dp)
                .clip(CircleShape)
                .background(Scrim.a50),
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = stringResource(R.string.cd_play_pause),
                tint = Color.White,
                modifier = Modifier.size(28.dp),
            )
        }

        VideoRightActionColumn(
            isMuted = isMuted,
            playbackSpeed = playbackSpeed,
            moreControlsOpen = moreControlsOpen,
            onToggleMute = onToggleMute,
            onOpenMoreControls = onOpenMoreControls,
            onDismissMoreControls = onDismissMoreControls,
            onCycleSpeed = onCycleSpeed,
            onEnterPip = onEnterPip,
            onToggleTrimMode = onToggleTrimMode,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp),
        )

        if (durationMs > 0L) {
            if (!trimMode) {
                VideoSeekBar(
                    durationMs = durationMs,
                    positionMs = positionMs,
                    onSeekDrag = onSeekDrag,
                    onSeekFinish = onSeekFinish,
                )
            }
            if (scrubFraction >= 0f && frameCache.isNotEmpty()) {
                VideoScrubPreview(
                    scrubFraction = scrubFraction,
                    durationMs = durationMs,
                    frameCache = frameCache,
                )
            }
            if (trimMode) {
                VideoTrimBar(
                    durationMs = durationMs,
                    trimStartMs = trimStartMs,
                    trimEndMs = trimEndMs,
                    isSavingTrim = isSavingTrim,
                    onUpdateTrimRange = onUpdateTrimRange,
                    onCancelTrim = onCancelTrim,
                    onSaveTrim = onSaveTrim,
                )
            }
        }
    }
}

@Composable
private fun VideoRightActionColumn(
    isMuted: Boolean,
    playbackSpeed: Float,
    moreControlsOpen: Boolean,
    onToggleMute: () -> Unit,
    onOpenMoreControls: () -> Unit,
    onDismissMoreControls: () -> Unit,
    onCycleSpeed: (Float) -> Unit,
    onEnterPip: () -> Unit,
    onToggleTrimMode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val speedIdx = VIDEO_PLAYBACK_SPEEDS.indexOf(playbackSpeed).coerceAtLeast(0)
    val nextSpeed = VIDEO_PLAYBACK_SPEEDS[(speedIdx + 1) % VIDEO_PLAYBACK_SPEEDS.size]
    val speedLabel = if (playbackSpeed % 1f == 0f) {
        "${playbackSpeed.toInt()}x"
    } else {
        "${playbackSpeed}x"
    }
    Column(modifier) {
        IconButton(onClick = onToggleMute, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = if (isMuted) {
                    Icons.AutoMirrored.Filled.VolumeOff
                } else {
                    Icons.AutoMirrored.Filled.VolumeUp
                },
                contentDescription = stringResource(R.string.set_mute_videos),
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
        Box {
            IconButton(onClick = onOpenMoreControls, modifier = Modifier.size(48.dp)) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.more_actions),
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
            DropdownMenu(
                expanded = moreControlsOpen,
                onDismissRequest = onDismissMoreControls,
            ) {
                DropdownMenuItem(
                    text = { Text("${stringResource(R.string.playback_speed)}: $speedLabel") },
                    onClick = { onCycleSpeed(nextSpeed) },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.cd_pip)) },
                    onClick = onEnterPip,
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.trim_save)) },
                    leadingIcon = { Icon(Icons.Default.ContentCut, contentDescription = null) },
                    onClick = onToggleTrimMode,
                )
            }
        }
    }
}

@Composable
private fun BoxScope.VideoSeekBar(
    durationMs: Long,
    positionMs: Long,
    onSeekDrag: (Float) -> Unit,
    onSeekFinish: (Long) -> Unit,
) {
    var seekPos by remember { mutableFloatStateOf(-1f) }
    Row(
        Modifier
            .fillMaxWidth()
            .align(Alignment.BottomCenter)
            .background(Scrim.a60)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .navigationBarsPadding(),
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

@Composable
private fun BoxScope.VideoTrimBar(
    durationMs: Long,
    trimStartMs: Float,
    trimEndMs: Float,
    isSavingTrim: Boolean,
    onUpdateTrimRange: (startMs: Float, endMs: Float, previewSeekMs: Long) -> Unit,
    onCancelTrim: () -> Unit,
    onSaveTrim: (startMs: Float, endMs: Float) -> Unit,
) {
    val durF = durationMs.coerceAtLeast(1L).toFloat()
    val endMs = if (trimEndMs < 0f) durF else trimEndMs
    val startF = (trimStartMs / durF).coerceIn(0f, 1f)
    val endF = (endMs / durF).coerceIn(startF, 1f)
    val validRange = endMs > trimStartMs
    Column(
        Modifier
            .fillMaxWidth()
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(bottom = TRIM_BAR_BOTTOM_OFFSET_DP.dp)
            .background(Scrim.a85)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        RangeSlider(
            value = startF..endF,
            enabled = !isSavingTrim,
            onValueChange = { range ->
                val newStart = range.start * durF
                val newEnd = range.endInclusive * durF
                val movedStart = abs(newStart - trimStartMs) >= abs(newEnd - endMs)
                val seekMs = (if (movedStart) newStart else newEnd).toLong()
                onUpdateTrimRange(newStart, newEnd, seekMs)
            },
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = Color.White.copy(alpha = INACTIVE_TRACK_ALPHA),
            ),
        )
        val tabularTrim = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum")
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                formatVideoSeconds((trimStartMs / MILLIS_PER_SECOND).toInt()),
                color = TrimStartColor,
                style = tabularTrim,
            )
            Text(" – ", color = Color.White, style = tabularTrim)
            Text(
                formatVideoSeconds((endMs / MILLIS_PER_SECOND).toInt()),
                color = TrimEndColor,
                style = tabularTrim,
            )
            Spacer(Modifier.weight(1f))
            TextButton(enabled = !isSavingTrim, onClick = onCancelTrim) {
                Text(
                    stringResource(org.fossify.commons.R.string.cancel),
                    color = Color.White.copy(alpha = CANCEL_BUTTON_ALPHA),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            TextButton(
                enabled = validRange && !isSavingTrim,
                onClick = { onSaveTrim(trimStartMs, endMs) },
            ) {
                if (isSavingTrim) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Text(
                        stringResource(R.string.trim_save),
                        color = if (validRange) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Color.White.copy(alpha = DISABLED_SAVE_ALPHA)
                        },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
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

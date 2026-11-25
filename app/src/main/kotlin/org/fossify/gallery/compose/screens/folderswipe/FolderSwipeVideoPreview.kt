package org.fossify.gallery.compose.screens.folderswipe

import android.annotation.SuppressLint
import android.net.Uri
import android.view.LayoutInflater
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import org.fossify.gallery.R
import org.fossify.gallery.compose.components.GalleryImage
import org.fossify.gallery.compose.theme.BlurRadius
import org.fossify.gallery.compose.theme.Scrim
import org.fossify.gallery.compose.util.BlurState
import org.fossify.gallery.compose.util.privacyBlur
import org.fossify.gallery.extensions.config
import java.io.File
import java.util.Locale

private const val SECONDS_PER_MINUTE = 60

@Composable
internal fun FolderSwipeVideoPreview(
    path: String,
    name: String,
    durationSec: Int,
    muted: Boolean,
    onToggleMute: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val autoplay = remember(ctx) { ctx.config.autoplayVideos }
    var firstFrameRendered by remember(path) { mutableStateOf(false) }

    Box(modifier) {
        if (autoplay) {
            SwipeLoopingPlayer(
                path = path,
                muted = muted,
                onFirstFrame = { firstFrameRendered = true },
                modifier = Modifier.fillMaxSize().privacyBlur(BlurRadius.viewer, BlurState.enabled),
            )
        }
        if (!autoplay || !firstFrameRendered) {
            GalleryImage(
                path = path,
                contentDescription = name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                thumbnailSize = 1536,
                backgroundColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            )
        }
        if (!autoplay) {
            SwipeVideoDurationBadge(
                durationSec = durationSec,
                showPlayIcon = true,
                modifier = Modifier.align(Alignment.Center),
            )
        } else {
            Surface(
                modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
                shape = CircleShape,
                color = Scrim.a60,
            ) {
                IconButton(onClick = onToggleMute, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = if (muted) {
                            Icons.AutoMirrored.Filled.VolumeOff
                        } else {
                            Icons.AutoMirrored.Filled.VolumeUp
                        },
                        contentDescription = stringResource(R.string.set_mute_videos),
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            if (durationSec > 0) {
                SwipeVideoDurationBadge(
                    durationSec = durationSec,
                    showPlayIcon = false,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp),
                )
            }
        }
    }
}

@SuppressLint("InflateParams")
@Composable
private fun SwipeLoopingPlayer(
    path: String,
    muted: Boolean,
    onFirstFrame: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val currentOnFirstFrame by rememberUpdatedState(onFirstFrame)
    val player = remember(path) {
        ExoPlayer.Builder(ctx)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(Uri.fromFile(File(path))))
                repeatMode = Player.REPEAT_MODE_ONE
                volume = if (muted) 0f else 1f
                prepare()
                playWhenReady = true
            }
    }
    LaunchedEffect(player, muted) {
        player.volume = if (muted) 0f else 1f
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                currentOnFirstFrame()
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.playWhenReady = false
            player.release()
        }
    }
    val lifecycleOwner = ctx as? LifecycleOwner
    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                player.pause()
            } else if (event == Lifecycle.Event.ON_START) {
                player.playWhenReady = true
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer) }
    }
    val playerView = remember {
        (LayoutInflater.from(ctx).inflate(R.layout.compose_video_player_view, null) as PlayerView)
            .apply { useController = false }
    }
    LaunchedEffect(player) { playerView.player = player }
    AndroidView(factory = { playerView }, modifier = modifier)
}

@Composable
private fun SwipeVideoDurationBadge(
    durationSec: Int,
    showPlayIcon: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = Scrim.a60,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (showPlayIcon) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
            if (durationSec > 0) {
                val mins = durationSec / SECONDS_PER_MINUTE
                val secs = durationSec % SECONDS_PER_MINUTE
                Text(
                    String.format(Locale.ROOT, "%d:%02d", mins, secs),
                    style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

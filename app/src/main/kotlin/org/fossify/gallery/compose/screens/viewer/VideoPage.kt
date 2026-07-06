package org.fossify.gallery.compose.screens.viewer
import androidx.compose.ui.res.stringResource
import org.fossify.gallery.R
import org.fossify.gallery.compose.theme.AppMotion
import org.fossify.gallery.compose.theme.Radius
import org.fossify.gallery.compose.theme.Scrim
import org.fossify.gallery.compose.theme.TrimEndColor
import org.fossify.gallery.compose.theme.TrimStartColor

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.fossify.commons.extensions.toast
import org.fossify.gallery.extensions.config
import java.io.File

val SCALING_FIT = 0
val SCALING_ZOOM = 2
val SCALING_FILL_WIDTH = 3

@Composable
fun VideoPage(
    path: String,
    scalingMode: Int,
    onScalingModeChange: (Int) -> Unit,
    onBackgroundAudioChange: (Boolean) -> Unit = {},
    onToggleUi: () -> Unit = {},
    onZoomChange: (Boolean) -> Unit = {},
    showUi: Boolean = true,
    onInteract: () -> Unit = {},
    modifier: Modifier = Modifier,
    isCurrentPage: Boolean = true,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val zoom = rememberZoomState()
    // All keyed on path (not just frameCache/positionMs) - the Viewer's pager can reuse the same
    // slot/index for a different video after one is deleted (no key= on the pager itself, see
    // ViewerScreen.kt), and without this these UI states (mute/speed/trim/...) used to carry over
    // from the deleted video's session onto whatever now occupies that slot.
    var isPlaying by remember(path) { mutableStateOf(ctx.config.autoplayVideos) }
    var playbackSpeed by remember(path) { mutableFloatStateOf(1f) }
    val speeds = listOf(0.5f, 1f, 1.5f, 2f, 3f)
    var backgroundAudio by remember(path) { mutableStateOf(false) }
    var isMuted by remember(path) { mutableStateOf(ctx.config.muteVideos) }
    var trimMode by remember(path) { mutableStateOf(false) }
    var trimStartMs by remember(path) { mutableFloatStateOf(0f) }
    var trimEndMs by remember(path) { mutableFloatStateOf(-1f) }
    var playerError by remember(path) { mutableStateOf<androidx.media3.common.PlaybackException?>(null) }
    var isBuffering by remember(path) { mutableStateOf(false) }

    var scrubFraction by remember { mutableFloatStateOf(-1f) }
    var frameCache by remember(path) { mutableStateOf<List<Bitmap>>(emptyList()) }
    var positionMs by remember(path) { mutableLongStateOf(0L) }

    LaunchedEffect(isCurrentPage) { if (!isCurrentPage) zoom.reset() }
    LaunchedEffect(zoom.isZoomed, isCurrentPage) { if (isCurrentPage) onZoomChange(zoom.isZoomed) }

    val retriever = remember(path) { MediaMetadataRetriever() }
    val frameMutex = remember { kotlinx.coroutines.sync.Mutex() }
    var retrieverReady by remember(path) { mutableStateOf(false) }
    LaunchedEffect(path) {
        withContext(Dispatchers.IO) {
            frameMutex.withLock {
                try {
                    retriever.setDataSource(path); retrieverReady = true
                    val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toFloatOrNull() ?: 0f
                    val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toFloatOrNull() ?: 0f
                    if (w > 0f && h > 0f) zoom.updateContentAspect(w / h)
                } catch (_: Exception) { }
            }
        }
        // Pre-extract a sparse set of scaled scrub thumbnails once (after the player has settled), so
        // the seek preview never has to decode in real time — real-time decoding contends with
        // ExoPlayer's decoder and frequently returns nothing. While seeking, the nearest cached frame
        // is shown.
        if (retrieverReady) {
            delay(400)
            val frames = withContext(Dispatchers.IO) {
                frameMutex.withLock {
                    val dur = try { retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L } catch (_: Exception) { 0L }
                    if (dur <= 0L) return@withLock emptyList<Bitmap>()
                    val n = 24
                    val out = ArrayList<Bitmap>(n)
                    for (i in 0 until n) {
                        val t = dur * i / (n - 1)
                        val bmp = try {
                            if (android.os.Build.VERSION.SDK_INT >= 27) retriever.getScaledFrameAtTime(t * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 256, 144)
                            else retriever.getFrameAtTime(t * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        } catch (_: Exception) { null }
                        if (bmp != null) out.add(bmp)
                    }
                    out
                }
            }
            frameCache = frames
        }
    }
    DisposableEffect(path) {
        onDispose { try { retriever.release() } catch (_: Exception) { } }
    }

    val player = remember(path) {
        ExoPlayer.Builder(ctx).build().apply {
            // `true` here is what actually enables Media3's built-in audio-focus handling (duck on
            // transient loss, pause on an incoming call/another player taking focus, resume after) -
            // without it the player never requests focus at all, so it plays right through calls.
            setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder()
                    .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                    .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true,
            )
            setMediaItem(MediaItem.fromUri(Uri.fromFile(File(path))))
            repeatMode = if (ctx.config.loopVideos) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            volume = if (ctx.config.muteVideos) 0f else 1f
            if (ctx.config.rememberLastVideoPosition) {
                val savedPos = ctx.config.getLastVideoPosition(path)
                if (savedPos > 0) seekTo(savedPos.toLong())
            }
        }
    }
    LaunchedEffect(isCurrentPage) {
        if (isCurrentPage) {
            // Only allocate the decoder/codec once this page is actually the visible one.
            if (player.playbackState == Player.STATE_IDLE) player.prepare()
            player.playWhenReady = ctx.config.autoplayVideos
        } else {
            player.playWhenReady = false
        }
    }
    DisposableEffect(player) {
        onDispose {
            player.playWhenReady = false
            player.pause()
            // Don't save a position that's at (or within half a second of) the very end - otherwise
            // reopening a video that was watched to completion immediately re-seeks to the last
            // frame and looks stuck/ended instead of starting over.
            val nearEnd = player.duration > 0 && player.currentPosition >= player.duration - 500
            if (ctx.config.rememberLastVideoPosition && !nearEnd) ctx.config.saveLastVideoPosition(path, player.currentPosition.toInt())
            player.release()
        }
    }

    val lifecycleOwner = ctx as? androidx.lifecycle.LifecycleOwner
    DisposableEffect(lifecycleOwner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) player.pause()
        }
        lifecycleOwner?.lifecycle?.addObserver(obs)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(obs) }
    }

    val listener = remember {
        object : Player.Listener {
            override fun onIsPlayingChanged(p: Boolean) { isPlaying = p }
            override fun onPlaybackStateChanged(state: Int) { isBuffering = state == Player.STATE_BUFFERING }
            // Previously unhandled entirely - a corrupt file or unsupported codec left the player
            // silently stuck (no error toast, no icon, controls that no longer did anything).
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) { playerError = error }
        }
    }
    DisposableEffect(player) {
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    val spv = remember { androidx.media3.ui.PlayerView(ctx).apply { useController = false } }
    LaunchedEffect(player) { spv.player = player }
    LaunchedEffect(scalingMode) { spv.resizeMode = scalingMode }
    LaunchedEffect(isCurrentPage, isPlaying) {
        while (isCurrentPage) {
            positionMs = player.currentPosition
            delay(500)
        }
    }

    Box(Modifier.fillMaxSize().clipToBounds().background(Color.Black).then(modifier)) {
        AndroidView(factory = { spv }, modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = zoom.scale; scaleY = zoom.scale
                translationX = zoom.offset.x; translationY = zoom.offset.y
            }
            // Single onDoubleTap detector (see zoomable()'s doc) - seeks on the left/right third
            // when video gestures are allowed, otherwise every double-tap just zooms.
            .zoomable(zoom, onSingleTap = { onToggleUi() }, onDoubleTap = if (ctx.config.allowVideoGestures) { pos, sz ->
                val w = sz.width
                if (pos.x < w / 3f) { player.seekTo((player.currentPosition - 10000).coerceAtLeast(0)); onInteract() }
                else if (pos.x > w * 2 / 3f) { player.seekTo((player.currentPosition + 10000).coerceAtMost(player.duration)); onInteract() }
                else zoom.cycleZoom(pos, sz)
            } else null)
        )

        ZoomMinimap(zoom, modifier = Modifier.align(Alignment.TopEnd).padding(top = 72.dp, end = 12.dp))

        if (isBuffering && playerError == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                androidx.compose.material3.CircularProgressIndicator(color = Color.White)
            }
        }

        playerError?.let {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.ErrorOutline, null, tint = Color.White, modifier = Modifier.size(48.dp))
                    Text(stringResource(R.string.error_loading_media), color = Color.White, modifier = Modifier.padding(top = 12.dp))
                }
            }
        }

        AnimatedVisibility(visible = showUi, enter = fadeIn(AppMotion.medium), exit = fadeOut(AppMotion.medium)) {
            Box(Modifier.fillMaxSize()) {
                IconButton(
                    onClick = { if (isPlaying) player.pause() else player.play(); onInteract() },
                    modifier = Modifier.align(Alignment.Center).size(56.dp).clip(CircleShape).background(Scrim.a50)
                ) { Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, stringResource(R.string.cd_play_pause), tint = Color.White, modifier = Modifier.size(28.dp)) }

                val speedIdx = speeds.indexOf(playbackSpeed)
                val nextSpeed = speeds[(speedIdx + 1) % speeds.size]
                Column(Modifier.align(Alignment.CenterEnd).padding(end = 8.dp)) {
                    TextButton(onClick = { playbackSpeed = nextSpeed; player.setPlaybackSpeed(nextSpeed); onInteract() }, modifier = Modifier.size(48.dp)) {
                        Text("${playbackSpeed}x", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                    }
                    IconButton(onClick = { isMuted = !isMuted; player.volume = if (isMuted) 0f else 1f; onInteract() }, modifier = Modifier.size(48.dp)) { Icon(if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp, stringResource(R.string.set_mute_videos), tint = Color.White, modifier = Modifier.size(22.dp)) }
                    IconButton(onClick = { trimMode = !trimMode; if (trimMode && trimEndMs < 0f) trimEndMs = player.duration.toFloat(); onInteract() }, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.ContentCut, stringResource(R.string.trim_save), tint = if (trimMode) MaterialTheme.colorScheme.primary else Color.White, modifier = Modifier.size(22.dp)) }
                }

                if (player.duration > 0) {
                    var seekPos by remember { mutableFloatStateOf(-1f) }
                    Row(
                        Modifier.fillMaxWidth().align(Alignment.BottomCenter).background(Scrim.a60).padding(horizontal = 12.dp, vertical = 8.dp).navigationBarsPadding(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val posMs = if (seekPos >= 0) seekPos else positionMs.toFloat()
                        Text("%02d:%02d".format((posMs / 1000).toInt() / 60, (posMs / 1000).toInt() % 60), style = MaterialTheme.typography.labelSmall, color = Color.White)
                        Slider(
                            value = if (player.duration > 0) posMs / player.duration else 0f,
                            onValueChange = { fraction ->
                                seekPos = fraction * player.duration
                                scrubFraction = fraction
                                player.seekTo((fraction * player.duration).toLong())
                                onInteract()
                            },
                            onValueChangeFinished = { seekPos = -1f; scrubFraction = -1f },
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(alpha = 0.3f)),
                        )
                        Text("%02d:%02d".format((player.duration / 1000) / 60, (player.duration / 1000) % 60), style = MaterialTheme.typography.labelSmall, color = Color.White)
                    }
                    // Frame preview — show the nearest pre-extracted thumbnail.
                    if (scrubFraction >= 0f && frameCache.isNotEmpty()) {
                        val previewBmp = frameCache[(scrubFraction * (frameCache.size - 1)).toInt().coerceIn(0, frameCache.size - 1)]
                        Box(Modifier.fillMaxWidth().align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 84.dp), contentAlignment = Alignment.Center) {
                            Surface(shape = RoundedCornerShape(Radius.sm), color = Scrim.a85) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Image(bitmap = previewBmp.asImageBitmap(), contentDescription = null, modifier = Modifier.size(width = 160.dp, height = 90.dp), contentScale = ContentScale.Crop)
                                    Text("%02d:%02d".format(((scrubFraction * player.duration) / 1000).toInt() / 60, ((scrubFraction * player.duration) / 1000).toInt() % 60), color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(4.dp))
                                }
                            }
                        }
                    }
                    // Trim bar
                    if (trimMode) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { trimStartMs = player.currentPosition.toFloat(); onInteract() }) {
                                Text(stringResource(R.string.trim_start).format((trimStartMs / 1000).toInt() / 60, (trimStartMs / 1000).toInt() % 60), color = TrimStartColor, style = MaterialTheme.typography.labelSmall)
                            }
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { trimEndMs = player.currentPosition.toFloat(); onInteract() }) {
                                Text(stringResource(R.string.trim_end).format((trimEndMs / 1000).toInt() / 60, (trimEndMs / 1000).toInt() % 60), color = TrimEndColor, style = MaterialTheme.typography.labelSmall)
                            }
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = {
                                onInteract()
                                val start = trimStartMs.toLong() * 1000; val end = trimEndMs.toLong() * 1000
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        val outFile = File(path.replaceBeforeLast('.', path.substringBeforeLast('.') + "_trimmed"))
                                        val extractor = android.media.MediaExtractor(); extractor.setDataSource(path)
                                        val muxer = android.media.MediaMuxer(outFile.absolutePath, android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                                        val trackCount = extractor.trackCount
                                        val indexMap = IntArray(trackCount) { -1 }
                                        for (i in 0 until trackCount) { extractor.selectTrack(i); indexMap[i] = muxer.addTrack(extractor.getTrackFormat(i)) }
                                        muxer.start()
                                        extractor.seekTo(start, android.media.MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                                        val buf = java.nio.ByteBuffer.allocate(1 shl 20); val info = android.media.MediaCodec.BufferInfo()
                                        while (true) {
                                            info.offset = 0; info.size = extractor.readSampleData(buf, 0)
                                            if (info.size < 0) break
                                            val t = extractor.sampleTime
                                            if (t > end) break
                                            info.presentationTimeUs = t - start; info.flags = extractor.sampleFlags
                                            muxer.writeSampleData(indexMap[extractor.sampleTrackIndex], buf, info); extractor.advance()
                                        }
                                        muxer.stop(); muxer.release(); extractor.release()
                                        try { android.media.MediaScannerConnection.scanFile(ctx, arrayOf(outFile.absolutePath), null, null) } catch (_: Exception) { }
                                        org.fossify.gallery.helpers.RefreshBus.trigger()
                                        withContext(Dispatchers.Main) { ctx.toast(ctx.getString(R.string.saved_as, outFile.name), android.widget.Toast.LENGTH_SHORT) }
                                    } catch (e: Exception) { withContext(Dispatchers.Main) { ctx.toast(ctx.getString(R.string.error_generic, e.message), android.widget.Toast.LENGTH_SHORT) } }
                                }
                            }) { Text(stringResource(R.string.trim_save), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        }
    }
}

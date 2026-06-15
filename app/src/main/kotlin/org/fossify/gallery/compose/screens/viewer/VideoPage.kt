package org.fossify.gallery.compose.screens.viewer

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var showControls by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(true) }
    var playbackSpeed by remember { mutableFloatStateOf(1f) }
    val speeds = listOf(0.5f, 1f, 1.5f, 2f, 3f)
    val autoHideMs = ctx.config.viewerAutoHideMs
    var autoHideJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var backgroundAudio by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    var trimMode by remember { mutableStateOf(false) }
    var trimStartMs by remember { mutableFloatStateOf(0f) }
    var trimEndMs by remember { mutableFloatStateOf(-1f) }

    var scrubFraction by remember { mutableFloatStateOf(-1f) }
    var scrubPreviewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var lastFrameRequestMs by remember { mutableLongStateOf(0L) }

    val retriever = remember { MediaMetadataRetriever() }
    DisposableEffect(path) {
        retriever.setDataSource(path)
        onDispose { retriever.release() }
    }

    val player = remember(path) {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(File(path))))
            repeatMode = Player.REPEAT_MODE_ONE
            prepare()
            playWhenReady = true
        }
    }
    val bgAudio by rememberUpdatedState(backgroundAudio)
    DisposableEffect(player) {
        onDispose {
            player.playWhenReady = false
            player.pause()
            if (!bgAudio) player.release()
        }
    }

    val listener = remember { object : Player.Listener { override fun onIsPlayingChanged(p: Boolean) { isPlaying = p } } }
    DisposableEffect(player) {
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    val spv = remember { androidx.media3.ui.PlayerView(ctx).apply { useController = false } }
    LaunchedEffect(player) { spv.player = player }
    LaunchedEffect(scalingMode) { spv.resizeMode = scalingMode }

    LaunchedEffect(showControls) {
        if (showControls) {
            delay(autoHideMs.toLong())
            showControls = false
        }
    }

    fun resetAutoHide() {
        autoHideJob?.cancel()
        autoHideJob = scope.launch {
            delay(autoHideMs.toLong())
            showControls = false
        }
    }

    Box(Modifier.fillMaxSize().clipToBounds().background(Color.Black).then(modifier)) {
        AndroidView(factory = { spv }, modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = scale; scaleY = scale
                translationX = offsetX; translationY = offsetY
            }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(0.5f, 5f)
                    scale = newScale
                    if (newScale > 1f) {
                        val maxX = (newScale - 1f) * size.width / 2f
                        val maxY = (newScale - 1f) * size.height / 2f
                        offsetX = (offsetX + pan.x).coerceIn(-maxX, maxX)
                        offsetY = (offsetY + pan.y).coerceIn(-maxY, maxY)
                    } else {
                        offsetX = 0f; offsetY = 0f
                    }
                }
            }
        )

        Box(Modifier.fillMaxSize().pointerInput(Unit) {
            detectTapGestures(
                onTap = { showControls = !showControls },
                onDoubleTap = { tapPos ->
                    if (scale > 1.1f) {
                        scale = 1f; offsetX = 0f; offsetY = 0f
                    } else {
                        val next = when (scalingMode) { SCALING_FIT -> SCALING_ZOOM; SCALING_ZOOM -> SCALING_FIT; else -> SCALING_FIT }
                        onScalingModeChange(next)
                    }
                }
            )
        })

        AnimatedVisibility(visible = showControls, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize()) {
                IconButton(
                    onClick = { if (isPlaying) player.pause() else player.play() },
                    modifier = Modifier.align(Alignment.Center).size(56.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f))
                ) { Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Play/Pause", tint = Color.White, modifier = Modifier.size(28.dp)) }

                val speedIdx = speeds.indexOf(playbackSpeed)
                val nextSpeed = speeds[(speedIdx + 1) % speeds.size]
                Column(Modifier.align(Alignment.CenterEnd).padding(end = 8.dp)) {
                    TextButton(onClick = { playbackSpeed = nextSpeed; player.setPlaybackSpeed(nextSpeed) }, modifier = Modifier.size(48.dp)) {
                        Text("${playbackSpeed}x", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(onClick = { backgroundAudio = !backgroundAudio; onBackgroundAudioChange(backgroundAudio) }, modifier = Modifier.size(48.dp)) { Text(if (backgroundAudio) "🎧" else "📢", style = MaterialTheme.typography.labelSmall) }
                    TextButton(onClick = { isMuted = !isMuted; player.volume = if (isMuted) 0f else 1f }, modifier = Modifier.size(48.dp)) { Text(if (isMuted) "🔇" else "🔊", style = MaterialTheme.typography.labelSmall) }
                    TextButton(onClick = { trimMode = !trimMode; if (trimMode && trimEndMs < 0f) trimEndMs = player.duration.toFloat() }, modifier = Modifier.size(48.dp)) { Text(if (trimMode) "✂" else "⚡", style = MaterialTheme.typography.labelSmall, color = Color.White) }
                }

                if (player.duration > 0) {
                    var seekPos by remember { mutableFloatStateOf(-1f) }
                    Row(
                        Modifier.fillMaxWidth().align(Alignment.BottomCenter).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 12.dp, vertical = 8.dp).navigationBarsPadding(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val posMs = if (seekPos >= 0) seekPos else player.currentPosition.toFloat()
                        Text("%02d:%02d".format((posMs / 1000).toInt() / 60, (posMs / 1000).toInt() % 60), style = MaterialTheme.typography.labelSmall, color = Color.White)
                        Slider(
                            value = if (player.duration > 0) posMs / player.duration else 0f,
                            onValueChange = { fraction ->
                                seekPos = fraction * player.duration
                                scrubFraction = fraction
                                player.seekTo((fraction * player.duration).toLong())
                                resetAutoHide()
                                val now = System.currentTimeMillis()
                                if (now - lastFrameRequestMs > 90) {
                                    lastFrameRequestMs = now
                                    scope.launch(Dispatchers.IO) {
                                        try {
                                            val ms = (fraction * player.duration).toLong()
                                            val bmp = retriever.getFrameAtTime(ms * 1000, MediaMetadataRetriever.OPTION_CLOSEST)
                                            withContext(Dispatchers.Main) { scrubPreviewBitmap = bmp }
                                        } catch (_: Exception) { }
                                    }
                                }
                            },
                            onValueChangeFinished = { seekPos = -1f; scrubFraction = -1f },
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(alpha = 0.3f)),
                        )
                        Text("%02d:%02d".format((player.duration / 1000) / 60, (player.duration / 1000) % 60), style = MaterialTheme.typography.labelSmall, color = Color.White)
                    }
                    // Frame preview
                    if (scrubFraction >= 0f && scrubPreviewBitmap != null) {
                        Box(Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(bottom = 60.dp), contentAlignment = Alignment.Center) {
                            Surface(shape = RoundedCornerShape(8.dp), color = Color.Black.copy(alpha = 0.85f)) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Image(bitmap = scrubPreviewBitmap!!.asImageBitmap(), contentDescription = null, modifier = Modifier.size(width = 160.dp, height = 90.dp), contentScale = ContentScale.Crop)
                                    Text("%02d:%02d".format(((scrubFraction * player.duration) / 1000).toInt() / 60, ((scrubFraction * player.duration) / 1000).toInt() % 60), color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(4.dp))
                                }
                            }
                        }
                    }
                    // Trim bar
                    if (trimMode) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { trimStartMs = player.currentPosition.toFloat() }) {
                                Text("Start: %02d:%02d".format((trimStartMs / 1000).toInt() / 60, (trimStartMs / 1000).toInt() % 60), color = Color(0xFF64B5F6), style = MaterialTheme.typography.labelSmall)
                            }
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { trimEndMs = player.currentPosition.toFloat() }) {
                                Text("Ende: %02d:%02d".format((trimEndMs / 1000).toInt() / 60, (trimEndMs / 1000).toInt() % 60), color = Color(0xFFEF5350), style = MaterialTheme.typography.labelSmall)
                            }
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = {
                                val start = trimStartMs.toLong() * 1000; val end = trimEndMs.toLong() * 1000
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        val outFile = File(path.replaceBeforeLast('.', path.substringBeforeLast('.') + "_trimmed"))
                                        val extractor = android.media.MediaExtractor(); extractor.setDataSource(path)
                                        val muxer = android.media.MediaMuxer(outFile.absolutePath, android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                                        extractor.selectTrack(0); val trackIndex = muxer.addTrack(extractor.getTrackFormat(0)); muxer.start()
                                        extractor.seekTo(start, android.media.MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                                        val buf = java.nio.ByteBuffer.allocate(256 * 1024); val info = android.media.MediaCodec.BufferInfo()
                                        while (true) { info.offset = 0; info.size = extractor.readSampleData(buf, 0); if (info.size < 0 || extractor.sampleTime > end) break; info.presentationTimeUs = extractor.sampleTime - start; info.flags = extractor.sampleFlags; muxer.writeSampleData(trackIndex, buf, info); extractor.advance() }
                                        muxer.stop(); muxer.release(); extractor.release()
                                        withContext(Dispatchers.Main) { ctx.toast("Gespeichert: ${outFile.name}", android.widget.Toast.LENGTH_SHORT) }
                                    } catch (e: Exception) { withContext(Dispatchers.Main) { ctx.toast("Fehler: ${e.message}", android.widget.Toast.LENGTH_SHORT) } }
                                }
                            }) { Text("✂ Speichern", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        }
    }
}

package org.fossify.gallery.compose.screens.viewer

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.Settings
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var showControls by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(true) }
    var playbackSpeed by remember { mutableFloatStateOf(1f) }
    val speeds = listOf(0.5f, 1f, 1.5f, 2f, 3f)
    val autoHideMs = ctx.config.viewerAutoHideMs
    var seekTimeMs by remember { mutableFloatStateOf(0f) }
    var isSeeking by remember { mutableStateOf(false) }
    var seekBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var backgroundAudio by remember { mutableStateOf(false) }
    var brightness by remember { mutableFloatStateOf(-1f) }
    var volume by remember { mutableFloatStateOf(-1f) }
    var trimMode by remember { mutableStateOf(false) }
    var trimStartMs by remember { mutableFloatStateOf(0f) }
    var trimEndMs by remember { mutableFloatStateOf(-1f) }
    val window = (ctx as? android.app.Activity)?.window
    val trimScope = rememberCoroutineScope()

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
        if (showControls) { delay(autoHideMs.toLong()); showControls = false }
    }

    Box(Modifier.fillMaxSize().clipToBounds().background(Color.Black).then(modifier)) {
        AndroidView(factory = { spv }, modifier = Modifier.fillMaxSize().graphicsLayer {
            scaleX = scale; scaleY = scale
            translationX = offsetX; translationY = offsetY
        }.pointerInput(Unit) {
            val sz = this.size
            awaitPointerEventScope {
                while (true) {
                    val e = awaitPointerEvent()
                    val c = e.changes.filter { it.pressed }
                    if (c.size > 1) {
                        val pts = c.map { it.position }
                        val pp = c.map { it.previousPosition }
                        val cent = Offset(pts.sumOf { it.x.toDouble() }.toFloat() / pts.size, pts.sumOf { it.y.toDouble() }.toFloat() / pts.size)
                        val pcent = Offset(pp.sumOf { it.x.toDouble() }.toFloat() / pp.size, pp.sumOf { it.y.toDouble() }.toFloat() / pp.size)
                        val d = pts.sumOf { (it - cent).getDistance().toDouble() }.toFloat()
                        val pd = pp.sumOf { (it - pcent).getDistance().toDouble() }.toFloat()
                        val z = if (pd > 0f) (d / pd).coerceIn(0.5f, 3f) else 1f
                        scale = (scale * z).coerceIn(1f, 5f)
                        val pan = cent - pcent
                        val mx = (scale - 1f) * sz.width / 2f
                        val my = (scale - 1f) * sz.height / 2f
                        offsetX = (offsetX + pan.x).coerceIn(-mx, mx)
                        offsetY = (offsetY + pan.y).coerceIn(-my, my)
                        c.forEach { it.consume() }
                    } else if (c.size == 1 && scale > 1f) {
                        val ch = c.first()
                        val pan = ch.position - ch.previousPosition
                        val mx = (scale - 1f) * sz.width / 2f
                        val my = (scale - 1f) * sz.height / 2f
                        offsetX = (offsetX + pan.x).coerceIn(-mx, mx)
                        offsetY = (offsetY + pan.y).coerceIn(-my, my)
                        c.forEach { it.consume() }
                    }
                }
            }
        })

        Box(Modifier.fillMaxSize().pointerInput(Unit) {
            detectTapGestures(
                onTap = { showControls = !showControls },
                onDoubleTap = {
                    val next = when (scalingMode) {
                        SCALING_FIT -> SCALING_ZOOM
                        SCALING_ZOOM -> SCALING_FIT
                        else -> SCALING_FIT
                    }
                    onScalingModeChange(next)
                    scale = 1f; offsetX = 0f; offsetY = 0f
                }
            )
        }.pointerInput(player.duration) {
            if (player.duration <= 0) return@pointerInput
            detectHorizontalDragGestures(
                onDragStart = {
                    isSeeking = true; showControls = true
                    seekBitmap = try { retriever.getFrameAtTime(player.currentPosition * 1000, MediaMetadataRetriever.OPTION_CLOSEST) } catch (_: Exception) { null }
                },
                onHorizontalDrag = { _, dragAmount ->
                    val fraction = dragAmount / (size.width * 0.5f)
                    val deltaMs = (player.duration * fraction).toLong()
                    seekTimeMs = (player.currentPosition + deltaMs).toFloat().coerceIn(0f, player.duration.toFloat())
                    seekBitmap = try { retriever.getFrameAtTime(seekTimeMs.toLong() * 1000, MediaMetadataRetriever.OPTION_CLOSEST) } catch (_: Exception) { null }
                },
                onDragEnd = {
                    isSeeking = false
                    player.seekTo(seekTimeMs.toLong())
                },
                onDragCancel = { isSeeking = false }
            )
        }.pointerInput(Unit) {
            detectVerticalDragGestures(
                onVerticalDrag = { change, dragAmount ->
                    if (scale > 1f) return@detectVerticalDragGestures
                    val isLeftHalf = change.position.x < size.width / 2f
                    val fraction = -dragAmount / size.height
                    if (isLeftHalf) {
                        brightness = ((brightness.coerceAtLeast(0f) + fraction).coerceIn(0f, 1f))
                        window?.let {
                            val lp = it.attributes
                            lp.screenBrightness = brightness
                            it.attributes = lp
                        }
                    } else {
                        val audioManager = ctx.getSystemService(android.content.Context.AUDIO_SERVICE) as? android.media.AudioManager
                        val maxVol = audioManager?.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC) ?: 15
                        val curVol = audioManager?.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) ?: 0
                        val newVol = (curVol + (fraction * maxVol).toInt()).coerceIn(0, maxVol)
                        audioManager?.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, newVol, 0)
                        volume = newVol.toFloat() / maxVol.toFloat()
                    }
                    showControls = false
                },
                onDragEnd = {
                    println("Vertical drag ended")
                    brightness = -1f; volume = -1f
                }
            )
        })

        AnimatedVisibility(visible = showControls, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize()) {
                IconButton(
                    onClick = { if (isPlaying) player.pause() else player.play() },
                    modifier = Modifier.align(Alignment.Center).size(56.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f))
                ) {
                    Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Play/Pause", tint = Color.White, modifier = Modifier.size(28.dp))
                }
                val speedIdx = speeds.indexOf(playbackSpeed)
                val nextSpeed = speeds[(speedIdx + 1) % speeds.size]
                Column(Modifier.align(Alignment.CenterEnd).padding(end = 8.dp)) {
                    TextButton(
                        onClick = { playbackSpeed = nextSpeed; player.setPlaybackSpeed(nextSpeed) },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Text("${playbackSpeed}x", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(
                        onClick = {
                            backgroundAudio = !backgroundAudio
                            onBackgroundAudioChange(backgroundAudio)
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Text(if (backgroundAudio) "🎧" else "📢", style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (player.duration > 0) {
                    var seekPos by remember { mutableFloatStateOf(-1f) }
                    Row(
                        Modifier.fillMaxWidth().align(Alignment.BottomCenter).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 12.dp, vertical = 8.dp).navigationBarsPadding(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val pos = if (seekPos >= 0) seekPos else player.currentPosition.toFloat()
                        Text("%02d:%02d".format((pos / 1000).toInt() / 60, (pos / 1000).toInt() % 60), style = MaterialTheme.typography.labelSmall, color = Color.White)
                        Slider(
                            value = if (player.duration > 0) pos / player.duration else 0f,
                            onValueChange = { seekPos = it * player.duration; player.seekTo((it * player.duration).toLong()) },
                            onValueChangeFinished = { seekPos = -1f },
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(alpha = 0.3f))
                        )
                        Text("%02d:%02d".format((player.duration / 1000) / 60, (player.duration / 1000) % 60), style = MaterialTheme.typography.labelSmall, color = Color.White)
                    }
                    if (trimMode) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(onClick = {
                                trimStartMs = player.currentPosition.toFloat()
                            }) { Text("Start: %02d:%02d".format((trimStartMs / 1000).toInt() / 60, (trimStartMs / 1000).toInt() % 60), color = Color(0xFF64B5F6), style = MaterialTheme.typography.labelSmall) }
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = {
                                trimEndMs = player.currentPosition.toFloat()
                            }) { Text("Ende: %02d:%02d".format((trimEndMs / 1000).toInt() / 60, (trimEndMs / 1000).toInt() % 60), color = Color(0xFFEF5350), style = MaterialTheme.typography.labelSmall) }
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = {
                                val start = trimStartMs.toLong() * 1000
                                val end = trimEndMs.toLong() * 1000
                                trimScope.launch(Dispatchers.IO) {
                                    try {
                                        val outFile = java.io.File(path.replaceBeforeLast('.', path.substringBeforeLast('.') + "_trimmed"))
                                        val extractor = android.media.MediaExtractor()
                                        extractor.setDataSource(path)
                                        val muxer = android.media.MediaMuxer(outFile.absolutePath, android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                                        extractor.selectTrack(0)
                                        val format = extractor.getTrackFormat(0)
                                        val trackIndex = muxer.addTrack(format)
                                        muxer.start()
                                        extractor.seekTo(start, android.media.MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                                        val buf = java.nio.ByteBuffer.allocate(256 * 1024)
                                        val bufferInfo = android.media.MediaCodec.BufferInfo()
                                        while (true) {
                                            bufferInfo.offset = 0; bufferInfo.size = extractor.readSampleData(buf, 0)
                                            if (bufferInfo.size < 0 || extractor.sampleTime > end) break
                                            bufferInfo.presentationTimeUs = extractor.sampleTime - start
                                            bufferInfo.flags = extractor.sampleFlags
                                            muxer.writeSampleData(trackIndex, buf, bufferInfo)
                                            extractor.advance()
                                        }
                                        muxer.stop(); muxer.release(); extractor.release()
                                        withContext(Dispatchers.Main) { ctx.toast("Gespeichert: ${outFile.name}", android.widget.Toast.LENGTH_SHORT) }
                                    } catch (e: Exception) { withContext(Dispatchers.Main) { ctx.toast("Fehler: ${e.message}", android.widget.Toast.LENGTH_SHORT) } }
                                }
                            }) {
                                Text("✂ Speichern", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Seek preview with frame thumbnail
        AnimatedVisibility(visible = isSeeking, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp), enter = fadeIn(), exit = fadeOut()) {
            Surface(
                modifier = Modifier.size(width = 180.dp, height = 120.dp),
                shape = RoundedCornerShape(8.dp),
                color = Color.Black.copy(alpha = 0.85f),
            ) {
                Box {
                    seekBitmap?.let { bmp ->
                        Image(bitmap = bmp.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)), contentAlignment = Alignment.Center) {
                        Text(
                            "%02d:%02d".format((seekTimeMs / 1000).toInt() / 60, (seekTimeMs / 1000).toInt() % 60),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        // Brightness / Volume feedback overlay
        val showBrightnessVol = brightness >= 0f || volume >= 0f
        AnimatedVisibility(visible = showBrightnessVol, modifier = Modifier.align(Alignment.Center), enter = fadeIn(), exit = fadeOut()) {
            Surface(shape = RoundedCornerShape(12.dp), color = Color.Black.copy(alpha = 0.7f)) {
                if (brightness >= 0f) {
                    Text("☀ ${(brightness * 100).toInt()}%", modifier = Modifier.padding(16.dp), color = Color.White, style = MaterialTheme.typography.bodyLarge)
                } else if (volume >= 0f) {
                    Text("🔊 ${(volume * 100).toInt()}%", modifier = Modifier.padding(16.dp), color = Color.White, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
                    }
                    TextButton(
                        onClick = {
                            trimMode = !trimMode
                            if (trimMode && trimEndMs < 0f) trimEndMs = player.duration.toFloat()
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Text(if (trimMode) "✂" else "⚡", style = MaterialTheme.typography.labelSmall, color = Color.White)
                    }
                }

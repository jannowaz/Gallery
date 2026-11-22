@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package org.fossify.gallery.compose.screens.viewer

import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.fossify.gallery.R
import org.fossify.gallery.compose.components.LibrarySheetHeader
import org.fossify.gallery.compose.theme.Radius
import org.fossify.gallery.helpers.RefreshBus
import java.io.File
import java.util.Locale

internal const val MIN_TRIM_DURATION_MS = 500L

/** Clamps a user-selected trim interval to valid bounds within [durationMs]. */
internal fun clampTrimRange(startMs: Long, endMs: Long, durationMs: Long): Pair<Long, Long> {
    if (durationMs <= 0L) return 0L to 0L
    val minSpan = minOf(MIN_TRIM_DURATION_MS, durationMs)
    val clampedStart = startMs.coerceIn(0L, (durationMs - minSpan).coerceAtLeast(0L))
    val clampedEnd = endMs.coerceIn(clampedStart + minSpan, durationMs)
    return clampedStart to clampedEnd
}

/** Builds a non-colliding sibling output file (`<name>_trimmed.mp4`, `_trimmed_2.mp4`, ...). */
internal fun buildTrimmedOutputFile(originalPath: String, exists: (File) -> Boolean = { it.exists() }): File {
    val src = File(originalPath)
    val parent = src.parentFile ?: File(".")
    val baseName = src.nameWithoutExtension.ifBlank { "video" }
    var candidate = File(parent, "${baseName}_trimmed.mp4")
    var suffix = 2
    while (exists(candidate)) {
        candidate = File(parent, "${baseName}_trimmed_$suffix.mp4")
        suffix++
    }
    return candidate
}

internal fun formatTrimTimestamp(ms: Long): String {
    val totalSec = (ms.coerceAtLeast(0L) / 1000L)
    val hours = totalSec / 3600L
    val mins = (totalSec % 3600L) / 60L
    val secs = totalSec % 60L
    return if (hours > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", hours, mins, secs)
    } else {
        String.format(Locale.US, "%02d:%02d", mins, secs)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoTrimmerSheet(
    videoPath: String,
    onDismiss: () -> Unit,
    onTrimSaved: (String) -> Unit,
) {
    val ctx = LocalContext.current
    val res = androidx.compose.ui.platform.LocalResources.current
    val scope = rememberCoroutineScope()

    var durationMs by remember(videoPath) { mutableLongStateOf(0L) }
    var trimStartMs by remember(videoPath) { mutableLongStateOf(0L) }
    var trimEndMs by remember(videoPath) { mutableLongStateOf(0L) }
    var removeAudio by remember(videoPath) { mutableStateOf(false) }
    var isPlaying by remember(videoPath) { mutableStateOf(true) }
    var exporting by remember(videoPath) { mutableStateOf(false) }
    var exportProgress by remember(videoPath) { mutableIntStateOf(0) }
    var exportJob by remember(videoPath) { mutableStateOf<Job?>(null) }

    LaunchedEffect(videoPath) {
        val probedMs = withContext(Dispatchers.IO) { probeVideoDurationMs(videoPath) }
        if (probedMs > 0L) {
            durationMs = probedMs
            trimStartMs = 0L
            trimEndMs = probedMs
        }
    }

    val player = remember(videoPath) {
        ExoPlayer.Builder(ctx)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(Uri.fromFile(File(videoPath))))
                repeatMode = Player.REPEAT_MODE_ONE
                prepare()
                playWhenReady = true
            }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY && durationMs <= 0L && player.duration > 0L) {
                    durationMs = player.duration
                    trimStartMs = 0L
                    trimEndMs = player.duration
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        player.addListener(listener)
        onDispose {
            exportJob?.cancel()
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(removeAudio) {
        player.volume = if (removeAudio) 0f else 1f
    }

    // Loop preview inside [trimStartMs, trimEndMs]
    LaunchedEffect(player, trimStartMs, trimEndMs, durationMs) {
        if (durationMs <= 0L) return@LaunchedEffect
        while (isActive) {
            val pos = player.currentPosition
            if (pos < trimStartMs || pos >= trimEndMs) {
                player.seekTo(trimStartMs)
            }
            delay(150)
        }
    }

    val canSave = !exporting && durationMs > 0L && (trimStartMs > 0L || trimEndMs < durationMs || removeAudio)

    ModalBottomSheet(
        onDismissRequest = { if (!exporting) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LibrarySheetHeader(
                title = stringResource(R.string.trim_video),
                onDismiss = {
                    exportJob?.cancel()
                    onDismiss()
                },
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(Radius.md))
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                AndroidView(
                    factory = { context ->
                        (LayoutInflater.from(context).inflate(R.layout.compose_video_player_view, null) as PlayerView).apply {
                            useController = false
                            this.player = player
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                IconButton(
                    onClick = {
                        if (player.isPlaying) player.pause() else player.play()
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f)),
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = stringResource(if (isPlaying) R.string.cd_pause else R.string.cd_play),
                        tint = Color.White,
                    )
                }
            }

            if (durationMs > 0L) {
                val range = trimStartMs.toFloat()..trimEndMs.toFloat()
                Text(
                    text = stringResource(
                        R.string.trim_video_range,
                        formatTrimTimestamp(trimStartMs),
                        formatTrimTimestamp(trimEndMs),
                        formatTrimTimestamp((trimEndMs - trimStartMs).coerceAtLeast(0L)),
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )

                RangeSlider(
                    value = range,
                    onValueChange = { newRange ->
                        val (s, e) = clampTrimRange(newRange.start.toLong(), newRange.endInclusive.toLong(), durationMs)
                        val startMoved = s != trimStartMs
                        trimStartMs = s
                        trimEndMs = e
                        player.seekTo(if (startMoved) s else (e - 500L).coerceAtLeast(s))
                    },
                    valueRange = 0f..durationMs.toFloat(),
                    enabled = !exporting,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = removeAudio,
                        enabled = !exporting,
                        role = Role.Switch,
                        onValueChange = { removeAudio = it },
                    )
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.trim_video_mute),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = removeAudio,
                    onCheckedChange = null,
                    enabled = !exporting,
                )
            }

            if (exporting) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "${stringResource(R.string.trim_video_exporting)} ${exportProgress}%",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    LinearProgressIndicator(
                        progress = { (exportProgress / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        exportJob?.cancel()
                        exporting = false
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.cancel))
                }
                Button(
                    onClick = {
                        player.pause()
                        exporting = true
                        exportProgress = 0
                        exportJob = scope.launch {
                            val result = runCatching {
                                exportTrimmedVideo(
                                    context = ctx.applicationContext,
                                    sourcePath = videoPath,
                                    startMs = trimStartMs,
                                    endMs = trimEndMs,
                                    removeAudio = removeAudio,
                                    onProgress = { pct -> exportProgress = pct },
                                )
                            }
                            exporting = false
                            result.onSuccess { outFile ->
                                MediaScannerConnection.scanFile(ctx.applicationContext, arrayOf(outFile.absolutePath), arrayOf("video/mp4"), null)
                                RefreshBus.trigger(pathsChanged = true, affectedPaths = listOf(outFile.absolutePath))
                                Toast.makeText(ctx, res.getString(R.string.trim_video_saved), Toast.LENGTH_SHORT).show()
                                onTrimSaved(outFile.absolutePath)
                            }.onFailure { err ->
                                if (err !is kotlinx.coroutines.CancellationException) {
                                    android.util.Log.e("VideoTrimmer", "Trim failed for $videoPath", err)
                                    Toast.makeText(ctx, res.getString(R.string.trim_video_failed), Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    enabled = canSave,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.trim_video_save_copy))
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

private fun probeVideoDurationMs(path: String): Long {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(path)
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
    } catch (_: Exception) {
        0L
    } finally {
        runCatching { retriever.release() }
    }
}

private suspend fun exportTrimmedVideo(
    context: Context,
    sourcePath: String,
    startMs: Long,
    endMs: Long,
    removeAudio: Boolean,
    onProgress: (Int) -> Unit,
): File {
    val tempDir = File(context.cacheDir, "video_trim").apply { mkdirs() }
    val tempFile = File(tempDir, "trim_${System.nanoTime()}.mp4")
    try {
        withContext(Dispatchers.Main) {
            val clipping = MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(startMs)
                .setEndPositionMs(endMs)
                .build()
            val mediaItem = MediaItem.Builder()
                .setUri(Uri.fromFile(File(sourcePath)))
                .setClippingConfiguration(clipping)
                .build()
            val edited = EditedMediaItem.Builder(mediaItem)
                .setRemoveAudio(removeAudio)
                .build()
            val transformer = Transformer.Builder(context).build()

            suspendCancellableCoroutine { cont ->
                transformer.addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        if (cont.isActive) cont.resumeWith(Result.success(Unit))
                    }

                    override fun onError(composition: Composition, exportResult: ExportResult, exception: ExportException) {
                        if (cont.isActive) cont.resumeWith(Result.failure(exception))
                    }
                })
                cont.invokeOnCancellation { Handler(Looper.getMainLooper()).post { transformer.cancel() } }
                transformer.start(edited, tempFile.absolutePath)

                val handler = Handler(Looper.getMainLooper())
                val holder = ProgressHolder()
                handler.postDelayed(object : Runnable {
                    override fun run() {
                        if (!cont.isActive) return
                        if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                            onProgress(holder.progress)
                        }
                        handler.postDelayed(this, 250)
                    }
                }, 250)
            }
        }

        return withContext(Dispatchers.IO) {
            check(tempFile.exists() && tempFile.length() > 0L) { "Trimmed file is empty" }
            val destFile = buildTrimmedOutputFile(sourcePath)
            tempFile.inputStream().use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            }
            check(destFile.exists() && destFile.length() > 0L) { "Failed to copy trimmed output" }
            destFile
        }
    } finally {
        runCatching { tempFile.delete() }
    }
}

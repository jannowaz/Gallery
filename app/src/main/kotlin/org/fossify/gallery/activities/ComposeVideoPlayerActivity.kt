@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package org.fossify.gallery.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.SurfaceView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.documentfile.provider.DocumentFile
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.commons.dialogs.PropertiesDialog
import org.fossify.commons.extensions.toast
import org.fossify.gallery.compose.components.SelectionRow
import org.fossify.gallery.compose.components.StarRatingDialog
import org.fossify.gallery.compose.components.TagInputDialog
import org.fossify.gallery.compose.theme.AppProviders
import org.fossify.gallery.compose.theme.GalleryTheme
import org.fossify.gallery.compose.theme.LocalMediaRepository
import org.fossify.gallery.extensions.deleteMediumWithPath
import org.fossify.gallery.helpers.MediaRepository
import java.io.File

class ComposeVideoPlayerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        val videoPath = intent.getStringExtra("VIDEO_PATH") ?: run { finish(); return }
        setContent {
            val repo = remember { MediaRepository(this@ComposeVideoPlayerActivity) }
            GalleryTheme(darkTheme = true) {
                AppProviders(repo) {
                    VideoPlayerScreen(videoPath = videoPath, onClose = { finish(); overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out) })
                }
            }
        }
    }
    override fun finish() {
        super.finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}

@Composable
private fun VideoPlayerScreen(videoPath: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = LocalMediaRepository.current
    var isPlaying by remember { mutableStateOf(true) }
    var showControls by remember { mutableStateOf(true) }
    var progress by remember { mutableFloatStateOf(0f) }
    var showActionSheet by remember { mutableStateOf(false) }
    var isFavorite by remember { mutableStateOf(false) }
    var showRatingDialog by remember { mutableStateOf(false) }
    var showTagsDialog by remember { mutableStateOf(false) }
    var currentRating by remember { mutableIntStateOf(0) }
    var pendingCopyPath by remember { mutableStateOf<String?>(null) }
    var pendingIsMove by remember { mutableStateOf(false) }

    val safLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val srcPath = pendingCopyPath ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val src = File(srcPath)
                    val mime = when (src.extension.lowercase()) { "mp4" -> "video/mp4"; "mkv" -> "video/x-matroska"; "mov" -> "video/quicktime"; else -> "video/*" }
                    val destDir = DocumentFile.fromTreeUri(context, uri)
                    val destFile = destDir?.createFile(mime, src.nameWithoutExtension) ?: return@launch
                    context.contentResolver.openOutputStream(destFile.uri)?.use { out -> src.inputStream().use { it.copyTo(out) } }
                    if (pendingIsMove) { src.delete(); context.deleteMediumWithPath(srcPath) }
                    withContext(Dispatchers.Main) { context.toast(if (pendingIsMove) "Verschoben" else "Kopiert", Toast.LENGTH_SHORT) }
                } catch (e: Exception) { withContext(Dispatchers.Main) { context.toast("Fehler: ${e.message}", Toast.LENGTH_SHORT) } }
            }
        }
        pendingCopyPath = null
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            isFavorite = repo.isFavorite(videoPath)
            currentRating = try { repo.getMediaFromPath(videoPath).firstOrNull()?.rating ?: 0 } catch (_: Exception) { 0 }
        }
    }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(File(videoPath))))
            prepare()
            playWhenReady = true
        }
    }

    val playerListener = remember {
        object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY && exoPlayer.duration > 0) {
                    progress = (exoPlayer.currentPosition.toFloat() / exoPlayer.duration)
                }
            }
            override fun onIsPlayingChanged(isNowPlaying: Boolean) { isPlaying = isNowPlaying }
        }
    }

    DisposableEffect(exoPlayer) {
        exoPlayer.addListener(playerListener)
        onDispose { exoPlayer.release() }
    }

    LaunchedEffect(isPlaying) {
        if (!isPlaying) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(250)
            if (exoPlayer.duration > 0) progress = (exoPlayer.currentPosition.toFloat() / exoPlayer.duration)
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx -> SurfaceView(ctx).also {
                exoPlayer.setVideoSurfaceView(it)
                exoPlayer.setVideoScalingMode(androidx.media3.common.C.VIDEO_SCALING_MODE_SCALE_TO_FIT)
            } },
            modifier = Modifier.fillMaxSize()
        )

        Box(Modifier.fillMaxSize()
            .pointerInput(Unit) {
                detectVerticalDragGestures(onDragEnd = {}, onVerticalDrag = { _, drag -> if (drag < -20) showActionSheet = true })
            }
            .clickable { showControls = !showControls }
        )

        AnimatedVisibility(visible = showControls, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize()) {
                IconButton(
                    onClick = { if (isPlaying) exoPlayer.pause() else exoPlayer.play() },
                    modifier = Modifier.align(Alignment.Center).size(64.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f))
                ) {
                    Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Play/Pause", tint = Color.White, modifier = Modifier.size(32.dp))
                }
            }
        }

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(4.dp).align(Alignment.BottomCenter).padding(bottom = 4.dp),
            color = Color.White,
            trackColor = Color.White.copy(alpha = 0.3f)
        )
    }

    if (showActionSheet) {
        ModalBottomSheet(onDismissRequest = { showActionSheet = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                SelectionRow(Icons.Default.Share, "Teilen") {
                    val uri = Uri.fromFile(File(videoPath))
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "video/*"; putExtra(Intent.EXTRA_STREAM, uri) }, "Teilen").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    showActionSheet = false
                }
                SelectionRow(Icons.Default.ContentCopy, "Kopieren") { pendingCopyPath = videoPath; pendingIsMove = false; safLauncher.launch(null); showActionSheet = false }
                SelectionRow(Icons.Default.Delete, "Löschen", tint = MaterialTheme.colorScheme.error) {
                    File(videoPath).delete(); context.deleteMediumWithPath(videoPath); showActionSheet = false; onClose()
                }
                HorizontalDivider()
                SelectionRow(Icons.Default.Info, "Info") { (context as? android.app.Activity)?.let { PropertiesDialog(it, videoPath, false) }; showActionSheet = false }
                SelectionRow(Icons.Default.Star, "Bewerten") { showRatingDialog = true; showActionSheet = false }
                SelectionRow(Icons.Default.Edit, "Tags") { showTagsDialog = true; showActionSheet = false }
                SelectionRow(if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, if (isFavorite) "Von Favoriten entfernen" else "Favorisieren") {
                    scope.launch { isFavorite = !isFavorite; repo.toggleFavorite(videoPath, isFavorite) }; showActionSheet = false
                }
            }
        }
    }

    if (showRatingDialog) {
        StarRatingDialog(currentRating = currentRating, onRate = { i ->
            currentRating = i; scope.launch(Dispatchers.IO) { repo.updateRating(videoPath, i) }; showRatingDialog = false
        }, onDismiss = { showRatingDialog = false })
    }
    if (showTagsDialog) {
        TagInputDialog(initialTags = repo.getTags(videoPath), onAddTag = { repo.addTag(videoPath, it) }, onRemoveTag = { repo.removeTag(videoPath, it) }, onDismiss = { showTagsDialog = false })
    }
}

package org.fossify.gallery.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.TextureView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
import org.fossify.gallery.extensions.openEditor
import org.fossify.gallery.helpers.MediaRepository
import java.io.File

private val videoExts = setOf("mp4", "mkv", "mov", "3gp", "wmv", "flv", "avi")

class ComposeViewerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        val paths = intent.getStringArrayListExtra("PATHS") ?: arrayListOf()
        val startIdx = intent.getIntExtra("START_INDEX", 0)
        setContent {
            val repo = remember { MediaRepository(this@ComposeViewerActivity) }
            GalleryTheme(darkTheme = true) { AppProviders(repo) { ViewerScreen(paths = paths, startIndex = startIdx, onClose = { finish() }) } }
        }
    }
}

private fun isVideo(path: String) = path.substringAfterLast('.', "").lowercase() in videoExts

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViewerScreen(paths: List<String>, startIndex: Int = 0, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = startIndex, pageCount = { paths.size })
    var showUI by remember { mutableStateOf(true) }
    var showActionSheet by remember { mutableStateOf(false) }
    val currentPath = paths.getOrNull(pagerState.currentPage) ?: ""
    val currentIsVideo = isVideo(currentPath)
    val repo = LocalMediaRepository.current
    var isFavorite by remember { mutableStateOf(false) }
    var showRatingDialog by remember { mutableStateOf(false) }
    var showTagsDialog by remember { mutableStateOf(false) }
    var showVideoSettings by remember { mutableStateOf(false) }
    var currentRating by remember { mutableIntStateOf(0) }
    var pendingCopyPath by remember { mutableStateOf<String?>(null) }
    var pendingIsMove by remember { mutableStateOf(false) }
    var videoScalingMode by remember { mutableIntStateOf(0) } // 0=Passend(FIT), 2=Fullscreen(ZOOM), 3=Breite(FIXED_WIDTH)

    val safLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val srcPath = pendingCopyPath ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val src = File(srcPath)
                    val ext = src.extension.ifEmpty { "jpg" }
                    val mime = when (ext) { "jpg","jpeg" -> "image/jpeg"; "png" -> "image/png"; "gif" -> "image/gif"; "mp4" -> "video/mp4"; else -> "*/*" }
                    val destDir = androidx.documentfile.provider.DocumentFile.fromTreeUri(ctx, uri)
                    val destFile = destDir?.createFile(mime, src.nameWithoutExtension) ?: return@launch
                    ctx.contentResolver.openOutputStream(destFile.uri)?.use { out -> src.inputStream().use { inp -> inp.copyTo(out) } }
                    if (pendingIsMove) { src.delete(); ctx.deleteMediumWithPath(srcPath) }
                    withContext(Dispatchers.Main) { ctx.toast(if (pendingIsMove) "Verschoben" else "Kopiert", Toast.LENGTH_SHORT) }
                } catch (e: Exception) { withContext(Dispatchers.Main) { ctx.toast("Fehler: ${e.message}", Toast.LENGTH_SHORT) } }
            }
        }
        pendingCopyPath = null
    }
    var offsetY by remember { mutableFloatStateOf(0f) }
    val heroAnim = remember { Animatable(1f) }

    LaunchedEffect(pagerState.currentPage) {
        withContext(Dispatchers.IO) {
            isFavorite = repo.isFavorite(currentPath)
            currentRating = try { repo.getMediaFromPath(currentPath).firstOrNull()?.rating ?: 0 } catch (_: Exception) { 0 }
        }
    }

    LaunchedEffect(showUI) {
        if (showUI) { delay(3000); showUI = false }
    }

    Box(Modifier.fillMaxSize().background(Color.Black).graphicsLayer {
        val h = size.height.toFloat()
        val dragAlpha = if (h > 0f) ((h - offsetY) / h).coerceIn(0f, 1f) else 1f
        alpha = heroAnim.value * dragAlpha
    }) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize().offset(y = offsetY.dp)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = { if (kotlin.math.abs(offsetY) > size.height / 4f) onClose() else offsetY = 0f },
                    onVerticalDrag = { _, drag ->
                        if (drag < -20) { showActionSheet = true; offsetY = 0f }
                        else offsetY = (offsetY + drag).coerceAtLeast(0f)
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(onTap = { showUI = !showUI })
            }
        ) { page ->
            val path = paths.getOrNull(page) ?: ""
            val file = File(path)

            if (isVideo(path)) {
                VideoPage(path = path, scalingMode = videoScalingMode)
            } else if (file.exists()) {
                ImagePage(path = path, file = file)
            }
        }

        // Top bar
        AnimatedVisibility(visible = showUI, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize()) {
                IconButton(onClick = { onClose() }, modifier = Modifier.align(Alignment.TopStart).padding(16.dp).background(Color.Black.copy(alpha = 0.4f), CircleShape).size(44.dp)) {
                    Icon(Icons.Default.Close, "Schließen", tint = Color.White)
                }
                if (paths.size > 1) {
                    Text("${pagerState.currentPage + 1} / ${paths.size}", color = Color.White, modifier = Modifier.align(Alignment.TopCenter).padding(top = 24.dp).background(Color.Black.copy(alpha = 0.4f), CircleShape).padding(horizontal = 16.dp, vertical = 8.dp))
                }
            }
        }
    }

    if (showActionSheet) {
        ModalBottomSheet(onDismissRequest = { showActionSheet = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                SelectionRow(Icons.Default.Share, "Teilen") {
                    val uri = android.net.Uri.fromFile(File(currentPath))
                    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "*/*"; putExtra(Intent.EXTRA_STREAM, uri) }, "Teilen").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    showActionSheet = false
                }
                SelectionRow(Icons.Default.ContentCopy, "Kopieren") { pendingCopyPath = currentPath; pendingIsMove = false; safLauncher.launch(null); showActionSheet = false }
                SelectionRow(Icons.Default.Delete, "Löschen", tint = MaterialTheme.colorScheme.error) {
                    File(currentPath).delete(); ctx.deleteMediumWithPath(currentPath); showActionSheet = false; onClose()
                }
                HorizontalDivider()
                if (currentIsVideo) {
                    SelectionRow(Icons.Default.Star, "Anzeigemodus") { showVideoSettings = true; showActionSheet = false }
                }
                SelectionRow(Icons.Default.Info, "Info") { (ctx as? android.app.Activity)?.let { PropertiesDialog(it, currentPath, false) }; showActionSheet = false }
                SelectionRow(Icons.Default.Star, "Bewerten") { showRatingDialog = true; showActionSheet = false }
                SelectionRow(Icons.Default.Edit, "Tags") { showTagsDialog = true; showActionSheet = false }
                SelectionRow(if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, if (isFavorite) "Von Favoriten entfernen" else "Favorisieren") {
                    scope.launch { isFavorite = !isFavorite; repo.toggleFavorite(currentPath, isFavorite) }; showActionSheet = false
                }
                SelectionRow(Icons.Default.Edit, "Bearbeiten") { (ctx as? android.app.Activity)?.openEditor(currentPath); showActionSheet = false }
            }
        }
    }

    if (showVideoSettings) {
        AlertDialog(
            onDismissRequest = { showVideoSettings = false },
            title = { Text("Anzeigemodus") },
            text = {
                Column {
                    listOf(
                        "Passend" to 0,
                        "Fullscreen" to 2,
                        "Breite füllen" to 3,
                    ).forEach { (label, mode) ->
                        TextButton(
                            onClick = { videoScalingMode = mode; showVideoSettings = false },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(label, color = if (videoScalingMode == mode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showVideoSettings = false }) { Text("Schließen") } }
        )
    }

    if (showRatingDialog) {
        StarRatingDialog(currentRating = currentRating, onRate = { i ->
            currentRating = i; scope.launch(Dispatchers.IO) { repo.updateRating(currentPath, i) }; showRatingDialog = false
        }, onDismiss = { showRatingDialog = false })
    }
    if (showTagsDialog) {
        TagInputDialog(initialTags = repo.getTags(currentPath), onSave = { repo.addTag(currentPath, it); ctx.toast("Tag gespeichert", Toast.LENGTH_SHORT) }, onDismiss = { showTagsDialog = false })
    }
}

@Composable
private fun ImagePage(path: String, file: File) {
    val ctx = LocalContext.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var contentScale by remember { mutableStateOf(ContentScale.Fit) }

    Box(Modifier.fillMaxSize().clipToBounds()) {
        AsyncImage(
            model = ImageRequest.Builder(ctx).data(android.net.Uri.fromFile(file)).crossfade(true).build(),
            contentDescription = file.name,
            contentScale = contentScale,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale; scaleY = scale
                translationX = offsetX; translationY = offsetY
            }.pointerInput(Unit) {
                val viewSize = this.size
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val changes = event.changes.filter { it.pressed }
                        if (changes.size > 1) {
                            val pts = changes.map { it.position }
                            val prevPts = changes.map { it.previousPosition }
                            val centroid = androidx.compose.ui.geometry.Offset(pts.sumOf { it.x.toDouble() }.toFloat() / pts.size, pts.sumOf { it.y.toDouble() }.toFloat() / pts.size)
                            val prevCentroid = androidx.compose.ui.geometry.Offset(prevPts.sumOf { it.x.toDouble() }.toFloat() / prevPts.size, prevPts.sumOf { it.y.toDouble() }.toFloat() / prevPts.size)
                            val curDist = pts.sumOf { (it - centroid).getDistance().toDouble() }.toFloat()
                            val prevDist = prevPts.sumOf { (it - prevCentroid).getDistance().toDouble() }.toFloat()
                            val zoom = if (prevDist > 0f) (curDist / prevDist).coerceIn(0.5f, 3f) else 1f
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            val pan = centroid - prevCentroid
                            val maxX = (scale - 1f) * viewSize.width / 2f
                            val maxY = (scale - 1f) * viewSize.height / 2f
                            offsetX = (offsetX + pan.x).coerceIn(-maxX, maxX)
                            offsetY = (offsetY + pan.y).coerceIn(-maxY, maxY)
                            changes.forEach { it.consume() }
                        } else if (changes.size == 1 && scale > 1f) {
                            val c = changes.first()
                            val pan = c.position - c.previousPosition
                            val maxX = (scale - 1f) * viewSize.width / 2f
                            val maxY = (scale - 1f) * viewSize.height / 2f
                            offsetX = (offsetX + pan.x).coerceIn(-maxX, maxX)
                            offsetY = (offsetY + pan.y).coerceIn(-maxY, maxY)
                            changes.forEach { it.consume() }
                        }
                    }
                }
            }
        )
        Box(Modifier.fillMaxSize().pointerInput(Unit) {
            detectTapGestures(onDoubleTap = {
                contentScale = if (contentScale == ContentScale.Fit) ContentScale.Crop else ContentScale.Fit
                scale = 1f; offsetX = 0f; offsetY = 0f
            })
        })
    }
}

@Composable
private fun VideoPage(path: String, scalingMode: Int) {
    val ctx = LocalContext.current
    val player = remember(path) {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(File(path))))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }

    val spv = remember { androidx.media3.ui.PlayerView(ctx) }
    LaunchedEffect(player) { spv.player = player }
    LaunchedEffect(scalingMode) { spv.resizeMode = scalingMode }

    AndroidView(factory = { spv }, modifier = Modifier.fillMaxSize().background(Color.Black))
}

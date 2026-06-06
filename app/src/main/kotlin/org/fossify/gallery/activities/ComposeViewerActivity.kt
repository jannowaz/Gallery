package org.fossify.gallery.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.TextureView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
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
import org.fossify.gallery.compose.components.TagInputDialog
import org.fossify.gallery.compose.screens.FolderPickerSheet
import org.fossify.gallery.compose.theme.AppProviders
import org.fossify.gallery.compose.theme.GalleryTheme
import org.fossify.gallery.compose.theme.LocalMediaRepository
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.deleteMediumWithPath
import org.fossify.gallery.extensions.mediaCacheDB
import org.fossify.gallery.extensions.openEditor
import org.fossify.gallery.helpers.MediaRepository
import org.fossify.gallery.helpers.VIDEO_EXTENSIONS
import java.io.File

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

private fun isVideo(path: String) = path.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS

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
    var showTagsDialog by remember { mutableStateOf(false) }
    var showVideoSettings by remember { mutableStateOf(false) }
    var showRatingOverlay by remember { mutableStateOf(false) }
    var showQuickTags by remember { mutableStateOf(false) }
    var showPersistentTags by remember { mutableStateOf(true) }
    var tagRefreshTrigger by remember { mutableIntStateOf(0) }
    val quickTags = remember { (ctx.config.quickTags).toList() }
    var currentRating by remember { mutableIntStateOf(0) }
    var showFolderPicker by remember { mutableStateOf(false) }
    var pendingFolderPickerIsMove by remember { mutableStateOf(false) }
    var videoScalingMode by remember { mutableIntStateOf(0) } // 0=Passend(FIT), 2=Fullscreen(ZOOM), 3=Breite(FIXED_WIDTH)
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
                detectTapGestures(onTap = {
                    val p = paths.getOrNull(pagerState.currentPage) ?: ""
                    if (isVideo(p)) showUI = !showUI
                    else { showActionSheet = true; offsetY = 0f }
                })
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

        // Bottom overlays: draw order = quick tags (bottom), rating (middle), persistent tags (top)
        // Quick tag bar (bottom-most, drawn first)
        AnimatedVisibility(visible = showQuickTags && quickTags.isNotEmpty(), modifier = Modifier.align(Alignment.BottomCenter), enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 8.dp, vertical = 10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    quickTags.forEach { tag ->
                        val hasTag = repo.getTags(currentPath).contains(tag)
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (hasTag) MaterialTheme.colorScheme.primary.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.15f),
                            tonalElevation = if (hasTag) 2.dp else 0.dp,
                            modifier = Modifier.clickable {
                                scope.launch(Dispatchers.IO) {
                                    if (hasTag) repo.removeTag(currentPath, tag) else repo.addTag(currentPath, tag)
                                    tagRefreshTrigger++
                                }
                            }
                        ) {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                val shortTag = if (tag.length > 30) tag.take(30) + "…" else tag
                                Text(shortTag, style = MaterialTheme.typography.labelSmall, color = if (hasTag) MaterialTheme.colorScheme.onPrimary else Color.White.copy(alpha = 0.9f))
                            }
                        }
                    }
                }
            }
        }

        // Rating overlay (middle)
        AnimatedVisibility(visible = showRatingOverlay, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = if (showQuickTags && quickTags.isNotEmpty()) 48.dp else 0.dp), enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.6f)).padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                    for (i in 1..5) {
                        IconButton(onClick = {
                            val newRating = if (currentRating == i) 0 else i
                            currentRating = newRating
                            scope.launch(Dispatchers.IO) { repo.updateRating(currentPath, newRating) }
                        }, modifier = Modifier.size(44.dp)) {
                            Icon(if (i <= currentRating) Icons.Default.Star else Icons.Default.StarBorder, "Bewertung $i", tint = if (i <= currentRating) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), modifier = Modifier.size(28.dp))
                        }
                    }
                }
            }
        }

        // Persistent tags (topmost, drawn last)
        var currentTags by remember { mutableStateOf<Set<String>>(emptySet()) }
        LaunchedEffect(currentPath, showPersistentTags, tagRefreshTrigger) {
            if (showPersistentTags) {
                currentTags = withContext(Dispatchers.IO) { repo.getTags(currentPath) }
            }
        }
        val tagsBottomPad = (if (showRatingOverlay) 56 else 0) + (if (showQuickTags && quickTags.isNotEmpty()) 48 else 0)
        AnimatedVisibility(visible = showPersistentTags && currentTags.isNotEmpty(), modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = tagsBottomPad.dp), enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 8.dp, vertical = 6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    currentTags.forEach { tag ->
                        val shortTag = if (tag.length > 30) tag.take(30) + "…" else tag
                        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)) {
                            Text(shortTag, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                        }
                    }
                }
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
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    SelectionRow(Icons.Default.Share, "Teilen", modifier = Modifier.weight(1f)) {
                        val uri = androidx.core.content.FileProvider.getUriForFile(ctx, "${ctx.packageName}.provider", File(currentPath))
                        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "*/*"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, "Teilen").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        showActionSheet = false
                    }
                    Spacer(Modifier.width(8.dp))
                    SelectionRow(Icons.Default.ContentCopy, "Kopieren", modifier = Modifier.weight(1f)) { pendingFolderPickerIsMove = false; showFolderPicker = true; showActionSheet = false }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    SelectionRow(Icons.AutoMirrored.Filled.DriveFileMove, "Verschieben", modifier = Modifier.weight(1f)) { pendingFolderPickerIsMove = true; showFolderPicker = true; showActionSheet = false }
                    Spacer(Modifier.width(8.dp))
                    SelectionRow(Icons.Default.Delete, "Löschen", tint = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f)) {
                        File(currentPath).delete(); ctx.deleteMediumWithPath(currentPath); showActionSheet = false; onClose()
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    SelectionRow(Icons.Default.Info, "Info", modifier = Modifier.weight(1f)) { (ctx as? android.app.Activity)?.let { PropertiesDialog(it, currentPath, false) }; showActionSheet = false }
                    Spacer(Modifier.width(8.dp))
                    SelectionRow(if (showRatingOverlay) Icons.Default.Star else Icons.Default.StarBorder, "Bewerten", modifier = Modifier.weight(1f)) { showRatingOverlay = !showRatingOverlay; showActionSheet = false }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    SelectionRow(Icons.Default.Edit, "Tags", modifier = Modifier.weight(1f)) { showTagsDialog = true; showActionSheet = false }
                    Spacer(Modifier.width(8.dp))
                    SelectionRow(if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, if (isFavorite) "Favorit" else "Favorisieren", modifier = Modifier.weight(1f)) {
                        scope.launch(Dispatchers.IO) { isFavorite = !isFavorite; repo.toggleFavorite(currentPath, isFavorite) }; showActionSheet = false
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    SelectionRow(Icons.Default.Edit, "Bearbeiten", modifier = Modifier.weight(1f)) { (ctx as? android.app.Activity)?.openEditor(currentPath); showActionSheet = false }
                    if (quickTags.isNotEmpty()) {
                        Spacer(Modifier.width(8.dp))
                        SelectionRow(if (showQuickTags) Icons.Default.Star else Icons.Default.StarBorder, "Schnell-Tags", modifier = Modifier.weight(1f)) { showQuickTags = !showQuickTags; showActionSheet = false }
                    } else {
                        Spacer(Modifier.width(8.dp))
                        Spacer(Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    SelectionRow(if (showPersistentTags) Icons.Default.Visibility else Icons.Default.VisibilityOff, "Tags anzeigen", modifier = Modifier.weight(1f)) { showPersistentTags = !showPersistentTags; showActionSheet = false }
                }
                if (currentIsVideo) {
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    SelectionRow(Icons.Default.Star, "Anzeigemodus") { showVideoSettings = true; showActionSheet = false }
                }
                Spacer(Modifier.height(16.dp))
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

    if (showTagsDialog) {
        var allTags by remember { mutableStateOf<List<String>>(emptyList()) }
        LaunchedEffect(Unit) { withContext(Dispatchers.IO) {
            try { allTags = ctx.mediaCacheDB.getAllTagged().flatMap { it.tags.split(",").filter(String::isNotBlank) }.distinct() } catch (_: Exception) { }
        } }
        TagInputDialog(initialTags = repo.getTags(currentPath), suggestedTags = allTags, onAddTag = { repo.addTag(currentPath, it) }, onRemoveTag = { repo.removeTag(currentPath, it) }, onDismiss = { showTagsDialog = false })
    }

    if (showFolderPicker) {
        FolderPickerSheet(
            isMoveOperation = pendingFolderPickerIsMove,
            sourcePaths = listOf(currentPath),
            onDismiss = { showFolderPicker = false }
        )
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
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

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

    Box(Modifier.fillMaxSize().clipToBounds().background(Color.Black)) {
        AndroidView(
            factory = { spv },
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
                            val cent = androidx.compose.ui.geometry.Offset(pts.sumOf { it.x.toDouble() }.toFloat() / pts.size, pts.sumOf { it.y.toDouble() }.toFloat() / pts.size)
                            val prevCent = androidx.compose.ui.geometry.Offset(prevPts.sumOf { it.x.toDouble() }.toFloat() / prevPts.size, prevPts.sumOf { it.y.toDouble() }.toFloat() / prevPts.size)
                            val curDist = pts.sumOf { (it - cent).getDistance().toDouble() }.toFloat()
                            val prevDist = prevPts.sumOf { (it - prevCent).getDistance().toDouble() }.toFloat()
                            val zoom = if (prevDist > 0f) (curDist / prevDist).coerceIn(0.5f, 3f) else 1f
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            val pan = cent - prevCent
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
                scale = if (scale > 1f) 1f else 2.5f
                if (scale == 1f) { offsetX = 0f; offsetY = 0f }
            })
        })
    }
}

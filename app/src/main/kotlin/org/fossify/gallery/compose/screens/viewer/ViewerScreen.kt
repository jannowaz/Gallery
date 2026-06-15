package org.fossify.gallery.compose.screens.viewer

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.commons.extensions.toast
import org.fossify.commons.dialogs.PropertiesDialog
import org.fossify.gallery.compose.components.SelectionRow
import org.fossify.gallery.compose.components.TagInputDialog
import org.fossify.gallery.compose.screens.FolderPickerSheet
import org.fossify.gallery.compose.theme.LocalMediaRepository
import org.fossify.gallery.compose.theme.GalleryTheme
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.mediaCacheDB
import org.fossify.gallery.extensions.openEditor
import org.fossify.gallery.helpers.VIDEO_EXTENSIONS
import java.io.File

private fun isVideo(path: String) = path.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(
    paths: List<String>,
    startIndex: Int = 0,
    onClose: () -> Unit,
) {
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
    var showFolderPicker by remember { mutableStateOf(false) }
    var pendingFolderPickerIsMove by remember { mutableStateOf(false) }
    var showRatingOverlay by remember { mutableStateOf(ctx.config.viewerShowRatingBar) }
    var showQuickTags by remember { mutableStateOf(false) }
    var showPersistentTags by remember { mutableStateOf(true) }
    var tagRefreshTrigger by remember { mutableIntStateOf(0) }
    val quickTags = remember { ctx.config.quickTags.toList() }
    var currentRating by remember { mutableIntStateOf(0) }
    var videoScalingMode by remember { mutableIntStateOf(0) }
    var backgroundAudio by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showVideoSettings by remember { mutableStateOf(false) }
    LaunchedEffect(pagerState.currentPage) {
        withContext(Dispatchers.IO) {
            isFavorite = repo.isFavorite(currentPath)
            currentRating = try { repo.getMediaFromPath(currentPath).firstOrNull()?.rating ?: 0 } catch (_: Exception) { 0 }
        }
    }

    val autoHideMs = ctx.config.viewerAutoHideMs
    LaunchedEffect(showUI) { if (showUI) { delay(autoHideMs.toLong()); showUI = false } }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
                .pointerInput(Unit) { detectTapGestures(onTap = { showUI = !showUI }) }
        ) { page ->
            val path = paths.getOrNull(page) ?: ""
            val file = File(path)
            if (isVideo(path)) VideoPage(
                path = path, scalingMode = videoScalingMode,
                onScalingModeChange = { videoScalingMode = it },
                onBackgroundAudioChange = { backgroundAudio = it },
            )
            else if (file.exists()) ImagePage(path = path, file = file, onClose = onClose)
        }

        // Top bar
        AnimatedVisibility(visible = showUI, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize()) {
                IconButton(
                    onClick = { ctx.config.lastViewedPath = currentPath; onClose() },
                    modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
                        .background(Color.Black.copy(alpha = 0.4f), CircleShape).size(44.dp)
                ) { Icon(Icons.Default.Close, "Schließen", tint = Color.White) }
                if (paths.size > 1) Text(
                    "${pagerState.currentPage + 1} / ${paths.size}",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 24.dp)
                        .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        // Rating overlay (inside main Box)
        AnimatedVisibility(
            visible = showRatingOverlay,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = if (currentIsVideo) 56.dp else 0.dp),
            enter = fadeIn(), exit = fadeOut(),
        ) {
            Box(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.65f)).padding(8.dp), contentAlignment = Alignment.Center) {
                Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                    for (i in 1..5) {
                        IconButton(onClick = { val r = if (currentRating == i) 0 else i; currentRating = r; scope.launch(Dispatchers.IO) { repo.updateRating(currentPath, r) } }, modifier = Modifier.size(40.dp)) {
                            Icon(if (i <= currentRating) Icons.Default.Star else Icons.Default.StarBorder, "Bewertung $i", tint = if (i <= currentRating) MaterialTheme.colorScheme.tertiary else Color.White.copy(alpha = 0.4f), modifier = Modifier.size(24.dp))
                        }
                    }
                    IconButton(onClick = { showRatingOverlay = false; ctx.config.viewerShowRatingBar = false }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Close, "Ausblenden", tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(16.dp)) }
                }
            }
        }
    }

    if (showActionSheet) {
        ModalBottomSheet(onDismissRequest = { showActionSheet = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).verticalScroll(rememberScrollState())) {
                Row(Modifier.fillMaxWidth()) {
                    SelectionRow(Icons.Default.Share, "◎ Teilen", modifier = Modifier.weight(1f)) { val u = androidx.core.content.FileProvider.getUriForFile(ctx, "${ctx.packageName}.provider", File(currentPath)); ctx.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).apply { type = if (currentIsVideo) "video/*" else "image/*"; putExtra(android.content.Intent.EXTRA_STREAM, u); addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }, "Teilen").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)); showActionSheet = false }
                    Spacer(Modifier.width(8.dp))
                    SelectionRow(Icons.Default.Edit, "Bearbeiten", modifier = Modifier.weight(1f)) { (ctx as? android.app.Activity)?.let { it.openEditor(currentPath) }; showActionSheet = false }
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth()) {
                    SelectionRow(Icons.Default.ContentCopy, "Kopieren", modifier = Modifier.weight(1f)) { pendingFolderPickerIsMove = false; showFolderPicker = true; showActionSheet = false }
                    Spacer(Modifier.width(8.dp))
                    SelectionRow(Icons.AutoMirrored.Filled.DriveFileMove, "Verschieben", modifier = Modifier.weight(1f)) { pendingFolderPickerIsMove = true; showFolderPicker = true; showActionSheet = false }
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth()) {
                    SelectionRow(Icons.Default.Info, "Info", modifier = Modifier.weight(1f)) { try { (ctx as? android.app.Activity)?.let { org.fossify.commons.dialogs.PropertiesDialog(it, currentPath, false) } } catch (e: Exception) { ctx.toast("Info-Fehler: ${e.message}", android.widget.Toast.LENGTH_SHORT) }; showActionSheet = false }
                    Spacer(Modifier.width(8.dp))
                    SelectionRow(Icons.Default.Delete, "Löschen", tint = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f)) { showDeleteConfirm = true; showActionSheet = false }
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth()) {
                    SelectionRow(if (showRatingOverlay) Icons.Default.Star else Icons.Default.StarBorder, "Bewerten", modifier = Modifier.weight(1f)) { val v = !showRatingOverlay; showRatingOverlay = v; ctx.config.viewerShowRatingBar = v; showActionSheet = false }
                    Spacer(Modifier.width(8.dp))
                    SelectionRow(if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, if (isFavorite) "Favorit" else "Favorisieren", modifier = Modifier.weight(1f)) { val f = !isFavorite; isFavorite = f; scope.launch(Dispatchers.IO) { repo.toggleFavorite(currentPath, f) }; showActionSheet = false }
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth()) {
                    SelectionRow(Icons.Default.Edit, "Tags", modifier = Modifier.weight(1f)) { showTagsDialog = true; showActionSheet = false }
                    if (quickTags.isNotEmpty()) { Spacer(Modifier.width(8.dp)); SelectionRow(if (showQuickTags) Icons.AutoMirrored.Filled.Label else Icons.AutoMirrored.Filled.Label, "Quick-Tags", modifier = Modifier.weight(1f)) { showQuickTags = !showQuickTags; showActionSheet = false } } else Spacer(Modifier.weight(1f))
                }
                if (currentIsVideo) {
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth()) {
                        SelectionRow(Icons.Default.Star, "Anzeigemodus", modifier = Modifier.weight(1f)) { showVideoSettings = true; showActionSheet = false }
                        Spacer(Modifier.width(8.dp))
                        SelectionRow(Icons.Default.Close, "Frame speichern", modifier = Modifier.weight(1f)) {
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val r = android.media.MediaMetadataRetriever(); r.setDataSource(currentPath)
                                    val bmp = r.frameAtTime ?: return@launch; r.release()
                                    val parentDir = File(currentPath).parentFile ?: ctx.cacheDir
                                    val outFile = File(parentDir, "frame_${System.currentTimeMillis()}.jpg")
                                    outFile.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, it) }; bmp.recycle()
                                    withContext(Dispatchers.Main) { ctx.toast("Frame gespeichert: ${outFile.name}", android.widget.Toast.LENGTH_SHORT) }
                                } catch (e: Exception) { withContext(Dispatchers.Main) { ctx.toast("Fehler: ${e.message}", android.widget.Toast.LENGTH_SHORT) } }
                            }
                            showActionSheet = false
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 6.dp)) { SelectionRow(if (showPersistentTags) Icons.AutoMirrored.Filled.Label else Icons.AutoMirrored.Filled.Label, "Tags anzeigen", modifier = Modifier.weight(1f)) { showPersistentTags = !showPersistentTags; showActionSheet = false } }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Löschen") },
            text = { Text("\"${File(currentPath).name}\" in den Papierkorb verschieben?") },
            confirmButton = { TextButton(onClick = { showDeleteConfirm = false; ctx.config.lastViewedPath = currentPath; scope.launch(Dispatchers.IO) { repo.moveToRecycleBin(currentPath) }; onClose() }) { Text("Löschen", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Abbrechen") } },
        )
    }

    if (showVideoSettings) {
        AlertDialog(onDismissRequest = { showVideoSettings = false }, title = { Text("Anzeigemodus") }, text = { Column { listOf("Passend" to 0, "Fullscreen" to 2, "Breite füllen" to 3).forEach { (l, m) -> TextButton(onClick = { videoScalingMode = m; showVideoSettings = false }, modifier = Modifier.fillMaxWidth()) { Text(l, color = if (videoScalingMode == m) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) } } } }, confirmButton = { TextButton(onClick = { showVideoSettings = false }) { Text("Schließen") } })
    }

    if (showTagsDialog) {
        var allTags by remember { mutableStateOf<List<String>>(emptyList()) }
        LaunchedEffect(Unit) {
            allTags = withContext(Dispatchers.IO) {
                try { ctx.mediaCacheDB.getAllTagged().flatMap { it.tags.split(",").filter(String::isNotBlank) }.distinct() } catch (_: Exception) { emptyList() }
            }
        }
        TagInputDialog(
            initialTags = repo.getTags(currentPath), suggestedTags = allTags,
            onAddTag = { scope.launch(Dispatchers.IO) { repo.addTag(currentPath, it) } },
            onRemoveTag = { scope.launch(Dispatchers.IO) { repo.removeTag(currentPath, it) } },
            onDismiss = { showTagsDialog = false },
        )
    }

    if (showFolderPicker) {
        FolderPickerSheet(
            isMoveOperation = pendingFolderPickerIsMove,
            sourcePaths = listOf(currentPath),
            onDismiss = { showFolderPicker = false },
        )
    }
}

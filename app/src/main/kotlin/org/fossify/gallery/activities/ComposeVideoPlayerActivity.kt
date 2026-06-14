@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package org.fossify.gallery.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.commons.dialogs.PropertiesDialog
import org.fossify.commons.extensions.toast
import org.fossify.gallery.compose.components.SelectionRow
import org.fossify.gallery.compose.components.TagInputDialog
import org.fossify.gallery.compose.screens.FolderPickerSheet
import org.fossify.gallery.compose.screens.viewer.SCALING_FIT
import org.fossify.gallery.compose.screens.viewer.VideoPage
import org.fossify.gallery.compose.theme.AppProviders
import org.fossify.gallery.compose.theme.GalleryTheme
import org.fossify.gallery.compose.theme.LocalMediaRepository
import org.fossify.gallery.extensions.deleteMediumWithPath
import org.fossify.gallery.extensions.mediaCacheDB
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
    override fun finish() { super.finish(); overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out) }
}

@Composable
private fun VideoPlayerScreen(videoPath: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = LocalMediaRepository.current
    var showActionSheet by remember { mutableStateOf(false) }
    var isFavorite by remember { mutableStateOf(false) }
    var showTagsDialog by remember { mutableStateOf(false) }
    var currentRating by remember { mutableIntStateOf(0) }
    var showFolderPicker by remember { mutableStateOf(false) }
    var pendingFolderPickerIsMove by remember { mutableStateOf(false) }
    var scalingMode by remember { mutableIntStateOf(SCALING_FIT) }
    var showInlineRating by remember { mutableStateOf(false) }

    BackHandler(enabled = showActionSheet || showTagsDialog || showFolderPicker || showInlineRating) {
        when { showFolderPicker -> showFolderPicker = false; showTagsDialog -> showTagsDialog = false; showInlineRating -> showInlineRating = false; showActionSheet -> showActionSheet = false }
    }

    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { isFavorite = repo.isFavorite(videoPath); currentRating = try { repo.getMediaFromPath(videoPath).firstOrNull()?.rating ?: 0 } catch (_: Exception) { 0 } } }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        VideoPage(path = videoPath, scalingMode = scalingMode, onScalingModeChange = { scalingMode = it })

        AnimatedVisibility(visible = showInlineRating, modifier = Modifier.align(Alignment.BottomCenter), enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.65f)).padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                    for (i in 1..5) { IconButton(onClick = { val r = if (currentRating == i) 0 else i; currentRating = r; scope.launch(Dispatchers.IO) { repo.updateRating(videoPath, r) } }, modifier = Modifier.size(40.dp)) { Icon(if (i <= currentRating) Icons.Default.Star else Icons.Default.StarBorder, "Bewertung $i", tint = if (i <= currentRating) MaterialTheme.colorScheme.tertiary else Color.White.copy(alpha = 0.4f), modifier = Modifier.size(24.dp)) } }
                }
            }
        }

        Box(Modifier.fillMaxSize().pointerInput(Unit) { kotlinx.coroutines.coroutineScope { launch { detectTapGestures(onDoubleTap = { }) }; launch { detectVerticalDragGestures(onDragEnd = { }, onVerticalDrag = { _, drag -> if (drag < -20) showActionSheet = true }) } } })
    }

    if (showActionSheet) {
        ModalBottomSheet(onDismissRequest = { showActionSheet = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                SelectionRow(Icons.Default.Share, "Teilen") { val u = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", File(videoPath)); context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "video/*"; putExtra(Intent.EXTRA_STREAM, u); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, "Teilen").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); showActionSheet = false }
                SelectionRow(Icons.Default.ContentCopy, "Kopieren") { pendingFolderPickerIsMove = false; showFolderPicker = true; showActionSheet = false }
                SelectionRow(Icons.AutoMirrored.Filled.DriveFileMove, "Verschieben") { pendingFolderPickerIsMove = true; showFolderPicker = true; showActionSheet = false }
                SelectionRow(Icons.Default.Delete, "Löschen", tint = MaterialTheme.colorScheme.error) { scope.launch(Dispatchers.IO) { repo.moveToRecycleBin(videoPath) }; showActionSheet = false; onClose() }
                HorizontalDivider()
                SelectionRow(Icons.Default.Info, "Info") { try { (context as? android.app.Activity)?.let { PropertiesDialog(it, videoPath, false) } } catch (e: Exception) { context.toast("Info-Fehler: ${e.message}", Toast.LENGTH_SHORT) }; showActionSheet = false }
                SelectionRow(Icons.Default.Star, "Bewerten") { showInlineRating = !showInlineRating; showActionSheet = false }
                SelectionRow(Icons.Default.Edit, "Tags") { showTagsDialog = true; showActionSheet = false }
                SelectionRow(if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, if (isFavorite) "Von Favoriten entfernen" else "Favorisieren") { val f = !isFavorite; isFavorite = f; scope.launch(Dispatchers.IO) { repo.toggleFavorite(videoPath, f) }; showActionSheet = false }
            }
        }
    }

    if (showTagsDialog) {
        var allTags by remember { mutableStateOf<List<String>>(emptyList()) }
        LaunchedEffect(Unit) { allTags = withContext(Dispatchers.IO) { try { context.mediaCacheDB.getAllTagged().flatMap { it.tags.split(",").filter(String::isNotBlank) }.distinct() } catch (_: Exception) { emptyList() } } }
        TagInputDialog(initialTags = repo.getTags(videoPath), suggestedTags = allTags, onAddTag = { scope.launch(Dispatchers.IO) { repo.addTag(videoPath, it) } }, onRemoveTag = { scope.launch(Dispatchers.IO) { repo.removeTag(videoPath, it) } }, onDismiss = { showTagsDialog = false })
    }

    if (showFolderPicker) { FolderPickerSheet(isMoveOperation = pendingFolderPickerIsMove, sourcePaths = listOf(videoPath), onDismiss = { showFolderPicker = false }) }
}

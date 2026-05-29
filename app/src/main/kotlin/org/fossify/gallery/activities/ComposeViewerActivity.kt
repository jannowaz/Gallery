package org.fossify.gallery.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViewerScreen(paths: List<String>, startIndex: Int = 0, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = startIndex, pageCount = { paths.size })
    var showUI by remember { mutableStateOf(true) }
    var showActionSheet by remember { mutableStateOf(false) }
    val currentPath = paths.getOrNull(pagerState.currentPage) ?: ""
    val repo = LocalMediaRepository.current
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
    val density = LocalDensity.current
    val heroAnim = remember { Animatable(1f) }

    LaunchedEffect(pagerState.currentPage) {
        withContext(Dispatchers.IO) {
            isFavorite = repo.isFavorite(currentPath)
            currentRating = try { repo.getMediaFromPath(currentPath).firstOrNull()?.rating ?: 0 } catch (_: Exception) { 0 }
        }
    }

    // Auto-hide UI after 3s
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
                    onVerticalDrag = { _, drag -> offsetY = (offsetY + drag).coerceAtLeast(0f) }
                )
            }
        ) { page ->
            val path = paths.getOrNull(page) ?: ""
            val file = File(path)
            Box(Modifier.fillMaxSize()) {
                if (file.exists()) {
                    AsyncImage(model = ImageRequest.Builder(ctx).data(android.net.Uri.fromFile(file)).crossfade(true).build(), contentDescription = file.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
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

        // Swipe up to open action sheet, tap to toggle UI
        Box(Modifier.fillMaxSize().pointerInput(Unit) {
            detectVerticalDragGestures(onDragEnd = { }, onVerticalDrag = { _, drag -> if (drag < -20) showActionSheet = true })
        }.clickable { showUI = !showUI })
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

    if (showRatingDialog) {
        StarRatingDialog(currentRating = currentRating, onRate = { i ->
            currentRating = i; repo.updateRating(currentPath, i); showRatingDialog = false
        }, onDismiss = { showRatingDialog = false })
    }
    if (showTagsDialog) {
        TagInputDialog(initialTags = repo.getTags(currentPath), onSave = { repo.addTag(currentPath, it); ctx.toast("Tag gespeichert", Toast.LENGTH_SHORT) }, onDismiss = { showTagsDialog = false })
    }
}

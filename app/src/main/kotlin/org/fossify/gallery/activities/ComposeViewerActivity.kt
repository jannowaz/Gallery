package org.fossify.gallery.activities

import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
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
import org.fossify.commons.dialogs.PropertiesDialog
import org.fossify.commons.extensions.toast
import org.fossify.gallery.compose.components.SelectionRow
import org.fossify.gallery.compose.components.TagInputDialog
import org.fossify.gallery.compose.screens.FolderPickerSheet
import org.fossify.gallery.compose.screens.viewer.ImagePage
import org.fossify.gallery.compose.screens.viewer.VideoPage
import org.fossify.gallery.compose.theme.AppProviders
import org.fossify.gallery.compose.theme.GalleryTheme
import org.fossify.gallery.compose.theme.LocalMediaRepository
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.deleteMediumWithPath
import org.fossify.gallery.extensions.mediaCacheDB
import org.fossify.gallery.extensions.openEditor
import org.fossify.gallery.helpers.MediaRepository
import org.fossify.gallery.helpers.UndoAction
import org.fossify.gallery.helpers.UndoManager
import org.fossify.gallery.helpers.UndoType
import org.fossify.gallery.helpers.VIDEO_EXTENSIONS
import java.io.File

class ComposeViewerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val hasHero = intent.hasExtra("HERO_LEFT")
        if (hasHero) overridePendingTransition(0, 0) else overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        val paths = intent.getStringArrayListExtra("PATHS") ?: arrayListOf()
        val startIdx = intent.getIntExtra("START_INDEX", 0)
        val conf = config
        setContent {
            val repo = remember { MediaRepository(this@ComposeViewerActivity) }
            GalleryTheme(darkTheme = conf.forceDarkMode || isSystemInDarkTheme()) { AppProviders(repo) { ViewerScreen(paths = paths, startIndex = startIdx, onClose = { finish() }) } }
        }
    }
}

private fun isVideo(path: String) = path.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViewerScreen(paths: List<String>, startIndex: Int = 0, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val items = remember { paths.toMutableStateList() }
    val pagerState = rememberPagerState(initialPage = startIndex.coerceIn(0, (paths.size - 1).coerceAtLeast(0)), pageCount = { items.size })
    var showUI by remember { mutableStateOf(true) }
    var showActionSheet by remember { mutableStateOf(false) }
    val currentPath = items.getOrNull(pagerState.currentPage) ?: ""
    val currentIsVideo = isVideo(currentPath)
    val repo = LocalMediaRepository.current
    var isFavorite by remember { mutableStateOf(false) }
    var showTagsDialog by remember { mutableStateOf(false) }
    var showVideoSettings by remember { mutableStateOf(false) }
    var showRatingOverlay by remember { mutableStateOf(ctx.config.viewerShowRatingBar) }
    var showQuickTags by remember { mutableStateOf(false) }
    var showPersistentTags by remember { mutableStateOf(true) }
    var tagRefreshTrigger by remember { mutableIntStateOf(0) }
    val quickTags = remember { (ctx.config.quickTags).toList() }
    var currentRating by remember { mutableIntStateOf(0) }
    var showFolderPicker by remember { mutableStateOf(false) }
    var pendingFolderPickerIsMove by remember { mutableStateOf(false) }
    var videoScalingMode by remember { mutableIntStateOf(0) }
    var isClosing by remember { mutableStateOf(false) }
    var immersiveMode by remember { mutableStateOf(false) }
    var backgroundAudio by remember { mutableStateOf(false) }
    var showExif by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var isCurrentZoomed by remember { mutableStateOf(false) }

    BackHandler(enabled = showActionSheet || showExif || showDeleteConfirm || showVideoSettings || showTagsDialog || showFolderPicker) {
        when {
            showFolderPicker -> showFolderPicker = false
            showTagsDialog -> showTagsDialog = false
            showVideoSettings -> showVideoSettings = false
            showDeleteConfirm -> showDeleteConfirm = false
            showExif -> showExif = false
            showActionSheet -> showActionSheet = false
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        isCurrentZoomed = false
        withContext(Dispatchers.IO) {
            isFavorite = repo.isFavorite(currentPath)
            currentRating = try { repo.getMediaFromPath(currentPath).firstOrNull()?.rating ?: 0 } catch (_: Exception) { 0 }
        }
    }

    val autoHideMs = ctx.config.viewerAutoHideMs
    LaunchedEffect(showUI) { if (showUI) { delay(autoHideMs.toLong()); showUI = false } }

    LaunchedEffect(immersiveMode) {
        val window = (ctx as? android.app.Activity)?.window ?: return@LaunchedEffect
        val controller = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        if (immersiveMode) {
            controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else { controller.show(androidx.core.view.WindowInsetsCompat.Type.systemBars()) }
    }

    val heroRect = remember {
        val act = ctx as? ComponentActivity
        if (act != null) {
            val l = act.intent.getFloatExtra("HERO_LEFT", -1f)
            if (l >= 0) android.graphics.RectF(l, act.intent.getFloatExtra("HERO_TOP", 0f), l + act.intent.getFloatExtra("HERO_WIDTH", 0f), act.intent.getFloatExtra("HERO_TOP", 0f) + act.intent.getFloatExtra("HERO_HEIGHT", 0f)) else null
        } else null
    }
    val heroProgress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { if (heroRect != null) { heroProgress.snapTo(0f); heroProgress.animateTo(1f, animationSpec = tween(350, easing = FastOutSlowInEasing)) } else heroProgress.snapTo(1f) }

    val closeWithAnimation: () -> Unit = {
        (ctx as? android.app.Activity)?.window?.attributes?.screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        ctx.config.lastViewedPath = currentPath
        if (backgroundAudio) { (ctx as? android.app.Activity)?.moveTaskToBack(true) }
        else if (heroRect != null && !isClosing) { isClosing = true; scope.launch { heroProgress.animateTo(0f, animationSpec = tween(250, easing = FastOutSlowInEasing)); onClose() } }
        else onClose()
    }

    Box(Modifier.fillMaxSize().background(Color.Black).graphicsLayer {
        val progress = heroProgress.value
        alpha = progress
        heroRect?.let { r ->
            if (progress < 1f) {
                val targetW = size.width.toFloat(); val targetH = size.height.toFloat()
                val w = r.width() + (targetW - r.width()) * progress; val h = r.height() + (targetH - r.height()) * progress
                val cx = r.centerX() + (targetW / 2f - r.centerX()) * progress; val cy = r.centerY() + (targetH / 2f - r.centerY()) * progress
                scaleX = w / targetW; scaleY = h / targetH; translationX = cx - targetW / 2f * scaleX; translationY = cy - targetH / 2f * scaleY
            }
        }
    }) {
        HorizontalPager(state = pagerState, userScrollEnabled = !isCurrentZoomed, modifier = Modifier.fillMaxSize()) { page ->
            val path = items.getOrNull(page) ?: ""
            val file = File(path)
            if (isVideo(path)) VideoPage(
                path = path, scalingMode = videoScalingMode, onScalingModeChange = { videoScalingMode = it }, onBackgroundAudioChange = { backgroundAudio = it },
                onToggleUi = { showUI = !showUI },
                onZoomChange = { if (page == pagerState.currentPage) isCurrentZoomed = it },
                isCurrentPage = page == pagerState.currentPage,
            )
            else if (file.exists()) ImagePage(
                path = path, file = file, onClose = closeWithAnimation,
                onToggleUi = { showUI = !showUI },
                onZoomChange = { if (page == pagerState.currentPage) isCurrentZoomed = it },
                isCurrentPage = page == pagerState.currentPage,
            )
        }

        // Unified contextual overlay
        var currentTags by remember { mutableStateOf<Set<String>>(emptySet()) }
        LaunchedEffect(currentPath, tagRefreshTrigger) { currentTags = withContext(Dispatchers.IO) { repo.getTags(currentPath) } }
        val showOverlay = showRatingOverlay || (showQuickTags && quickTags.isNotEmpty()) || showPersistentTags
        AnimatedVisibility(visible = showOverlay, modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = if (currentIsVideo) 56.dp else 0.dp), enter = fadeIn(), exit = fadeOut()) {
            Column(Modifier.fillMaxWidth()) {
                if (showRatingOverlay) {
                    Box(Modifier.fillMaxWidth().padding(top = 6.dp), contentAlignment = Alignment.Center) {
                        Surface(shape = RoundedCornerShape(24.dp), color = Color.Black.copy(alpha = 0.32f)) {
                            Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                for (i in 1..5) { IconButton(onClick = { val r = if (currentRating == i) 0 else i; currentRating = r; scope.launch(Dispatchers.IO) { repo.updateRating(currentPath, r) } }, modifier = Modifier.size(40.dp)) { Icon(if (i <= currentRating) Icons.Default.Star else Icons.Default.StarBorder, "Bewertung $i", tint = if (i <= currentRating) MaterialTheme.colorScheme.tertiary else Color.White.copy(alpha = 0.4f), modifier = Modifier.size(24.dp)) } }
                                IconButton(onClick = { showRatingOverlay = false; ctx.config.viewerShowRatingBar = false }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Close, "Ausblenden", tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(16.dp)) }
                            }
                        }
                    }
                }
                val hasQuick = showQuickTags && quickTags.isNotEmpty(); val hasPersistent = showPersistentTags && currentTags.isNotEmpty()
                if (hasQuick || hasPersistent) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (hasPersistent) currentTags.forEach { tag -> val st = if (tag.length > 30) tag.take(30) + "…" else tag; Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)) { Text(st, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)) } }
                        if (hasQuick) quickTags.forEach { tag -> val h = repo.getTags(currentPath).contains(tag); Surface(shape = RoundedCornerShape(20.dp), color = if (h) MaterialTheme.colorScheme.primary.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.15f), modifier = Modifier.clickable { scope.launch(Dispatchers.IO) { if (h) repo.removeTag(currentPath, tag) else repo.addTag(currentPath, tag); tagRefreshTrigger++ } }) { val st = if (tag.length > 30) tag.take(30) + "…" else tag; Text(st, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = if (h) MaterialTheme.colorScheme.onPrimary else Color.White.copy(alpha = 0.9f)) } }
                    }
                }
            }
        }

        // Top bar
        AnimatedVisibility(visible = showUI, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize()) {
                IconButton(onClick = closeWithAnimation, modifier = Modifier.align(Alignment.TopStart).padding(16.dp).background(Color.Black.copy(alpha = 0.4f), CircleShape).size(44.dp)) { Icon(Icons.Default.Close, "Schließen", tint = Color.White) }
                Row(Modifier.align(Alignment.TopEnd).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (!currentIsVideo) {
                        IconButton(onClick = { showExif = !showExif }, modifier = Modifier.background(Color.Black.copy(alpha = 0.4f), CircleShape).size(44.dp)) { Icon(Icons.Default.Info, "EXIF", tint = Color.White) }
                        Spacer(Modifier.width(8.dp))
                    }
                    IconButton(onClick = { showActionSheet = true }, modifier = Modifier.background(Color.Black.copy(alpha = 0.4f), CircleShape).size(44.dp)) { Icon(Icons.Default.MoreVert, "Aktionen", tint = Color.White) }
                }
                if (items.size > 1) Text("${pagerState.currentPage + 1} / ${items.size}", color = Color.White, modifier = Modifier.align(Alignment.TopCenter).padding(top = 24.dp).background(Color.Black.copy(alpha = 0.4f), CircleShape).padding(horizontal = 16.dp, vertical = 8.dp))
            }
        }

        // EXIF sheet
        if (showExif && !currentIsVideo) ExifSheet(path = currentPath, onDismiss = { showExif = false })
    }

    if (showActionSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { showActionSheet = false }, sheetState = sheetState) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).verticalScroll(rememberScrollState())) {
                Row(Modifier.fillMaxWidth()) {
                    SelectionRow(Icons.Default.Share, "◎ Teilen", modifier = Modifier.weight(1f)) { val u = androidx.core.content.FileProvider.getUriForFile(ctx, "${ctx.packageName}.provider", File(currentPath)); ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = if (currentIsVideo) "video/*" else "image/*"; putExtra(Intent.EXTRA_STREAM, u); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, "Teilen").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); showActionSheet = false }
                    Spacer(Modifier.width(8.dp))
                    SelectionRow(Icons.Default.Edit, "Bearbeiten", modifier = Modifier.weight(1f)) { (ctx as? android.app.Activity)?.openEditor(currentPath); showActionSheet = false }
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth()) {
                    SelectionRow(Icons.Default.ContentCopy, "Kopieren", modifier = Modifier.weight(1f)) { pendingFolderPickerIsMove = false; showFolderPicker = true; showActionSheet = false }
                    Spacer(Modifier.width(8.dp))
                    SelectionRow(Icons.AutoMirrored.Filled.DriveFileMove, "Verschieben", modifier = Modifier.weight(1f)) { pendingFolderPickerIsMove = true; showFolderPicker = true; showActionSheet = false }
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth()) {
                    SelectionRow(Icons.Default.Info, "Info", modifier = Modifier.weight(1f)) { try { (ctx as? android.app.Activity)?.let { PropertiesDialog(it, currentPath, false) } } catch (e: Exception) { ctx.toast("Info-Fehler: ${e.message}", Toast.LENGTH_SHORT) }; showActionSheet = false }
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
                    if (quickTags.isNotEmpty()) { Spacer(Modifier.width(8.dp)); SelectionRow(if (showQuickTags) Icons.AutoMirrored.Filled.Label else Icons.AutoMirrored.Filled.Label, "Schnell-Tags", modifier = Modifier.weight(1f)) { showQuickTags = !showQuickTags; showActionSheet = false } } else Spacer(Modifier.weight(1f))
                }
                if (currentIsVideo) {
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth()) {
                        SelectionRow(Icons.Default.Star, "Anzeigemodus", modifier = Modifier.weight(1f)) { showVideoSettings = true; showActionSheet = false }
                        Spacer(Modifier.width(8.dp))
                        SelectionRow(Icons.Default.Close, "Frame speichern", modifier = Modifier.weight(1f)) {
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val r = MediaMetadataRetriever(); r.setDataSource(currentPath)
                                    val bmp = r.frameAtTime ?: return@launch; r.release()
                                    val parentDir = File(currentPath).parentFile ?: ctx.cacheDir
                                    val outFile = File(parentDir, "frame_${System.currentTimeMillis()}.jpg")
                                    outFile.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }; bmp.recycle()
                                    try { android.media.MediaScannerConnection.scanFile(ctx, arrayOf(outFile.absolutePath), null, null) } catch (_: Exception) { }
                                    org.fossify.gallery.helpers.RefreshBus.trigger()
                                    withContext(Dispatchers.Main) { ctx.toast("Frame gespeichert: ${outFile.name}", Toast.LENGTH_SHORT) }
                                } catch (e: Exception) { withContext(Dispatchers.Main) { ctx.toast("Fehler: ${e.message}", Toast.LENGTH_SHORT) } }
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

    if (showVideoSettings) {
        AlertDialog(onDismissRequest = { showVideoSettings = false }, title = { Text("Anzeigemodus") }, text = { Column { listOf("Passend" to 0, "Vollbild (Zoom)" to 4, "Strecken" to 3).forEach { (l, m) -> TextButton(onClick = { videoScalingMode = m; showVideoSettings = false }, modifier = Modifier.fillMaxWidth()) { Text(l, color = if (videoScalingMode == m) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) } } } }, confirmButton = { TextButton(onClick = { showVideoSettings = false }) { Text("Schließen") } })
    }

    if (showTagsDialog) {
        var allTags by remember { mutableStateOf<List<String>>(emptyList()) }
        var tagCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
        LaunchedEffect(Unit) { withContext(Dispatchers.IO) { try { val tagged = ctx.mediaCacheDB.getRecentTagged(1000); val counts = tagged.flatMap { it.tags.split(",").filter(String::isNotBlank) }.groupingBy { it }.eachCount(); allTags = counts.entries.sortedByDescending { it.value }.map { it.key }; tagCounts = counts } catch (_: Exception) { } } }
        TagInputDialog(initialTags = repo.getTags(currentPath), suggestedTags = allTags, suggestedTagCounts = tagCounts, onAddTag = { scope.launch(Dispatchers.IO) { repo.addTag(currentPath, it) } }, onRemoveTag = { scope.launch(Dispatchers.IO) { repo.removeTag(currentPath, it) } }, onDismiss = { showTagsDialog = false })
    }

    if (showFolderPicker) {
        FolderPickerSheet(isMoveOperation = pendingFolderPickerIsMove, sourcePaths = listOf(currentPath), onDismiss = { showFolderPicker = false })
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Löschen") },
            text = { Text("\"${File(currentPath).name}\" in den Papierkorb verschieben?") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    val idx = pagerState.currentPage
                    val p = items.getOrNull(idx)
                    if (p != null) {
                        scope.launch(Dispatchers.IO) { repo.moveToRecycleBin(p) }
                        UndoManager.push(UndoAction(paths = setOf(p), type = UndoType.DELETE))
                        if (items.size <= 1) closeWithAnimation() else {
                            items.removeAt(idx)
                            if (idx >= items.size) scope.launch { pagerState.scrollToPage(items.size - 1) }
                        }
                    }
                }) { Text("Löschen", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Abbrechen") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExifSheet(path: String, onDismiss: () -> Unit) {
    var exifData by remember { mutableStateOf<Map<String, String>?>(null) }
    var rotationDegrees by remember { mutableIntStateOf(0) }
    var showDatePicker by remember { mutableStateOf(false) }
    var pendingDate by remember { mutableStateOf(android.icu.util.Calendar.getInstance().timeInMillis) }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    LaunchedEffect(path) {
        exifData = withContext(Dispatchers.IO) {
            val data = mutableMapOf<String, String>()
            try {
                val exif = android.media.ExifInterface(path)
                data["Aufnahmedatum"] = exif.getAttribute(android.media.ExifInterface.TAG_DATETIME) ?: ""
                data["Hersteller"] = exif.getAttribute(android.media.ExifInterface.TAG_MAKE) ?: ""
                data["Kamera"] = exif.getAttribute(android.media.ExifInterface.TAG_MODEL) ?: ""
                data["Breite"] = exif.getAttribute(android.media.ExifInterface.TAG_IMAGE_WIDTH) ?: ""
                data["Höhe"] = exif.getAttribute(android.media.ExifInterface.TAG_IMAGE_LENGTH) ?: ""
                data["Brennweite"] = exif.getAttribute(android.media.ExifInterface.TAG_FOCAL_LENGTH) ?: ""
                data["Blende"] = exif.getAttribute(android.media.ExifInterface.TAG_F_NUMBER) ?: ""
                data["Belichtungszeit"] = exif.getAttribute(android.media.ExifInterface.TAG_EXPOSURE_TIME) ?: ""
                data["ISO"] = exif.getAttribute(android.media.ExifInterface.TAG_ISO) ?: ""
                val lat = exif.getAttribute(android.media.ExifInterface.TAG_GPS_LATITUDE); val lon = exif.getAttribute(android.media.ExifInterface.TAG_GPS_LONGITUDE)
                if (!lat.isNullOrBlank() && !lon.isNullOrBlank()) data["GPS"] = "$lat, $lon"
                data["Dateigröße"] = if (File(path).length() > 1_000_000) "${File(path).length() / 1_000_000} MB" else "${File(path).length() / 1_000} KB"
                data["Dateiname"] = File(path).name
                data.entries.removeAll { it.value.isBlank() }
            } catch (_: Exception) { }
            data
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("EXIF-Details", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Schließen") }
            }
            Spacer(Modifier.height(8.dp))
            val data = exifData
            if (data == null) Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else if (data.isEmpty()) Text("Keine EXIF-Daten verfügbar", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else data.forEach { (label, value) -> Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) { Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(120.dp)); Text(value, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f)) } }

            Spacer(Modifier.height(16.dp)); HorizontalDivider(); Spacer(Modifier.height(12.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable {
                    scope.launch(Dispatchers.IO) {
                        try {
                            val exif = android.media.ExifInterface(path)
                            val cur = exif.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)
                            val next = when (cur) { 1 -> 8; 8 -> 3; 3 -> 6; 6 -> 1; else -> 8 }
                            exif.setAttribute(android.media.ExifInterface.TAG_ORIENTATION, next.toString())
                            exif.saveAttributes()
                            rotationDegrees = (rotationDegrees - 90) % 360
                        } catch (_: Exception) { }
                    }
                }) {
                    Icon(Icons.Default.Close, "90° CCW", modifier = Modifier.size(32.dp))
                    Text("90° CCW", style = MaterialTheme.typography.labelSmall)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable {
                    scope.launch(Dispatchers.IO) {
                        try {
                            val exif = android.media.ExifInterface(path)
                            val cur = exif.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)
                            val next = when (cur) { 1 -> 6; 6 -> 3; 3 -> 8; 8 -> 1; else -> 6 }
                            exif.setAttribute(android.media.ExifInterface.TAG_ORIENTATION, next.toString())
                            exif.saveAttributes()
                            rotationDegrees = (rotationDegrees + 90) % 360
                        } catch (_: Exception) { }
                    }
                }) {
                    Icon(Icons.Default.Close, "90° CW", modifier = Modifier.size(32.dp))
                    Text("90° CW", style = MaterialTheme.typography.labelSmall)
                }
            }

            Spacer(Modifier.height(12.dp))
            Surface(
                onClick = {
                    try {
                        val exif = android.media.ExifInterface(path)
                        val dt = exif.getAttribute(android.media.ExifInterface.TAG_DATETIME)
                        if (dt != null) {
                            val sdf = java.text.SimpleDateFormat("yyyy:MM:dd HH:mm:ss", java.util.Locale.US)
                            pendingDate = sdf.parse(dt)?.time ?: System.currentTimeMillis()
                        }
                    } catch (_: Exception) { }
                    showDatePicker = true
                },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Aufnahmedatum ändern", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = pendingDate)
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    showDatePicker = false
                    datePickerState.selectedDateMillis?.let { millis ->
                        scope.launch(Dispatchers.IO) {
                            try {
                                val exif = android.media.ExifInterface(path)
                                val sdf = java.text.SimpleDateFormat("yyyy:MM:dd HH:mm:ss", java.util.Locale.US)
                                exif.setAttribute(android.media.ExifInterface.TAG_DATETIME, sdf.format(java.util.Date(millis)))
                                exif.saveAttributes()
                                withContext(Dispatchers.Main) { ctx.toast("Datum gespeichert", android.widget.Toast.LENGTH_SHORT) }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) { ctx.toast("Fehler: ${e.message}", android.widget.Toast.LENGTH_SHORT) }
                            }
                        }
                    }
                }) { Text("Speichern") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Abbrechen") } },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

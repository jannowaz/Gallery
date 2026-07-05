package org.fossify.gallery.compose.screens.viewer
import androidx.compose.ui.res.stringResource
import org.fossify.gallery.R
import org.fossify.gallery.compose.theme.Radius
import org.fossify.gallery.compose.theme.Scrim

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.updateBrightness
import org.fossify.commons.dialogs.PropertiesDialog
import org.fossify.gallery.compose.components.ConfirmDestructive
import org.fossify.gallery.compose.components.SelectionRow
import org.fossify.gallery.compose.util.rememberGalleryHaptics
import org.fossify.gallery.compose.theme.AppMotion
import org.fossify.gallery.compose.theme.FavoriteColor
import org.fossify.gallery.compose.theme.RatingStarColor
import org.fossify.gallery.compose.components.TagInputDialog
import org.fossify.gallery.compose.components.UndoBar
import org.fossify.gallery.compose.screens.FolderPickerSheet
import org.fossify.gallery.compose.theme.LocalMediaRepository
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.openEditor
import org.fossify.gallery.helpers.VIDEO_EXTENSIONS
import org.fossify.gallery.helpers.UndoAction
import org.fossify.gallery.helpers.UndoManager
import org.fossify.gallery.helpers.UndoType
import java.io.File
import kotlin.math.abs

private fun isVideo(path: String) = path.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS

/** Small uppercase caption grouping a block of controls in the action sheet - Bewertung/Aktionen/Tags
 * were previously separated only by dividers, which reads as one long wall of controls. */
@Composable
private fun SheetSectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 0.6.sp,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

@Composable
private fun ActionChip(icon: ImageVector, label: String, tint: Color = MaterialTheme.colorScheme.onSurface, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(4.dp).clip(RoundedCornerShape(Radius.md)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp)) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(
    paths: List<String>,
    startIndex: Int = 0,
    onClose: () -> Unit,
) {
    val ctx = LocalContext.current
    val haptic = rememberGalleryHaptics()
    val scope = rememberCoroutineScope()
    val items = remember { paths.toMutableStateList() }
    val pagerState = rememberPagerState(initialPage = startIndex.coerceIn(0, (paths.size - 1).coerceAtLeast(0)), pageCount = { items.size })
    var showUI by remember { mutableStateOf(paths.getOrNull(startIndex.coerceIn(0, (paths.size - 1).coerceAtLeast(0)))?.let { !isVideo(it) } ?: true) }
    var showActionSheet by remember { mutableStateOf(false) }
    val currentPath = items.getOrNull(pagerState.currentPage) ?: ""
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
    var showVideoSettings by remember { mutableStateOf(false) }
    var isCurrentZoomed by remember { mutableStateOf(false) }
    var uiInteractionTick by remember { mutableIntStateOf(0) }
    var showGestureHint by remember { mutableStateOf(!ctx.config.hasSeenViewerGestureHint) }
    fun dismissGestureHint() {
        if (showGestureHint) {
            showGestureHint = false
            ctx.config.hasSeenViewerGestureHint = true
        }
    }
    LaunchedEffect(showGestureHint) { if (showGestureHint) { delay(3000); dismissGestureHint() } }
    LaunchedEffect(pagerState.currentPage) {
        isCurrentZoomed = false
        withContext(Dispatchers.IO) {
            isFavorite = repo.isFavorite(currentPath)
            currentRating = try { repo.getRating(currentPath) } catch (_: Exception) { 0 }
        }
    }

    val autoHideMs = ctx.config.viewerAutoHideMs
    LaunchedEffect(showUI, uiInteractionTick) { if (showUI) { delay(autoHideMs.toLong()); showUI = false } }

    DisposableEffect(Unit) {
        val window = (ctx as? android.app.Activity)?.window
        var originalBrightness: Float? = null
        if (window != null) {
            if (ctx.config.keepScreenOn) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (ctx.config.maxBrightness) originalBrightness = window.updateBrightness(true, null)
        }
        onDispose {
            if (window != null) {
                if (ctx.config.keepScreenOn) window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                if (ctx.config.maxBrightness) window.updateBrightness(false, originalBrightness)
            }
        }
    }

    var showDeleteConfirm by remember { mutableStateOf(false) }
    fun performDelete() {
        val idx = pagerState.currentPage
        val p = items.getOrNull(idx) ?: return
        ctx.config.lastViewedPath = p
        scope.launch(Dispatchers.IO) { repo.moveToRecycleBin(p) }
        UndoManager.push(UndoAction(paths = setOf(p), type = UndoType.DELETE))
        if (items.size <= 1) onClose() else {
            items.removeAt(idx)
            if (idx >= items.size) scope.launch { pagerState.scrollToPage(items.size - 1) }
        }
    }
    fun deleteCurrent() {
        if (ctx.config.skipDeleteConfirmation) performDelete() else showDeleteConfirm = true
    }

    BackHandler(enabled = showActionSheet || showVideoSettings || showTagsDialog || showFolderPicker) {
        when {
            showFolderPicker -> showFolderPicker = false
            showTagsDialog -> showTagsDialog = false
            showVideoSettings -> showVideoSettings = false
            showActionSheet -> showActionSheet = false
        }
    }

    var dragOffset by remember { mutableFloatStateOf(0f) }
    // Tracks whether the drag is currently past either dismiss threshold, so the haptic tick fires
    // once on crossing into the zone rather than continuously every frame the drag stays past it.
    var dismissThresholdCrossed by remember { mutableStateOf(false) }
    val dragScale = (1f - (abs(dragOffset) / 1000f)).coerceIn(0.85f, 1f)
    val dragAlpha = (1f - (abs(dragOffset) / 600f)).coerceIn(0.5f, 1f)

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dragAlpha))) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !isCurrentZoomed,
            // Preload one neighbour on each side so the adjacent image/video frame is already
            // decoded by the time a swipe lands on it, instead of a blank frame flashing in.
            // Video playback itself still only starts on the current page (VideoPage gates it on
            // isCurrentPage), so this doesn't spin up extra players.
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize()
                .graphicsLayer {
                    translationY = dragOffset
                    scaleX = dragScale
                    scaleY = dragScale
                }
                .pointerInput(isCurrentZoomed) {
                    if (!isCurrentZoomed) {
                        detectVerticalDragGestures(
                            onDragStart = { dragOffset = 0f; dismissThresholdCrossed = false; dismissGestureHint() },
                            onDragEnd = {
                                if (dragOffset < -180f) showActionSheet = true
                                else if (dragOffset > 240f) { ctx.config.lastViewedPath = currentPath; onClose() }
                                else { scope.launch { animate(dragOffset, 0f, animationSpec = org.fossify.gallery.compose.theme.AppMotion.gestureSpring) { v, _ -> dragOffset = v } } }
                            },
                            onVerticalDrag = { _, drag ->
                                dragOffset += drag
                                val crossed = dragOffset < -180f || dragOffset > 240f
                                if (crossed && !dismissThresholdCrossed) haptic(HapticFeedbackType.GestureThresholdActivate)
                                dismissThresholdCrossed = crossed
                            },
                        )
                    }
                }
        ) { page ->
            val path = items.getOrNull(page) ?: ""
            val file = File(path)
            if (isVideo(path)) VideoPage(
                path = path, scalingMode = videoScalingMode,
                onScalingModeChange = { videoScalingMode = it },
                onBackgroundAudioChange = { backgroundAudio = it },
                onToggleUi = { showUI = !showUI },
                showUi = showUI,
                onInteract = { uiInteractionTick++ },
                onZoomChange = { if (page == pagerState.currentPage) isCurrentZoomed = it },
                isCurrentPage = page == pagerState.currentPage,
            )
            else if (file.exists()) ImagePage(
                path = path, file = file, onClose = onClose,
                onToggleUi = { showUI = !showUI },
                onZoomChange = { if (page == pagerState.currentPage) isCurrentZoomed = it },
                isCurrentPage = page == pagerState.currentPage,
            )
        }

        // Top bar
        AnimatedVisibility(visible = showUI, enter = fadeIn(AppMotion.medium), exit = fadeOut(AppMotion.medium)) {
            Box(Modifier.fillMaxSize()) {
                IconButton(
                    onClick = { ctx.config.lastViewedPath = currentPath; onClose() },
                    modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
                        .background(Scrim.a40, CircleShape).size(44.dp)
                ) { Icon(Icons.Default.Close, stringResource(R.string.cd_close), tint = Color.White) }
                if (items.size > 1) Text(
                    "${pagerState.currentPage + 1} / ${items.size}",
                    color = Color.White,
                    style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 24.dp)
                        .background(Scrim.a40, CircleShape)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        // One-time hint for the vertical drag gestures (swipe up = actions sheet, down = close) -
        // neither has any visual affordance otherwise. Shown once ever, dismissed by any drag,
        // tap, or after a few seconds on its own.
        AnimatedVisibility(
            visible = showGestureHint,
            enter = fadeIn(AppMotion.medium),
            exit = fadeOut(AppMotion.medium),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Surface(shape = RoundedCornerShape(Radius.lg), color = Scrim.a60) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.KeyboardArrowUp, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.viewer_gesture_hint_up), color = Color.White, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.KeyboardArrowDown, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.viewer_gesture_hint_down), color = Color.White, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        // Soft bottom scrim for control legibility, anchored to the screen bottom so it never floats
        // with a hard edge even when the controls are lifted above the video seek bar.
        AnimatedVisibility(
            visible = showUI,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(AppMotion.medium), exit = fadeOut(AppMotion.medium),
        ) {
            Box(Modifier.fillMaxWidth().height(200.dp).background(Brush.verticalGradient(0f to Color.Transparent, 1f to Scrim.a60)))
        }

        // Bottom overlays in one column: rating bar (persistent) above the action bar (with the UI),
        // so they never overlap and the rating bar slides smoothly when the UI is toggled. For videos
        // the group sits permanently above the seek bar (constant offset) so they never overlap even
        // mid-animation.
        val bottomGroupOffset = if (currentIsVideo) 64.dp else 0.dp
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(bottom = bottomGroupOffset),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedVisibility(visible = showRatingOverlay, enter = fadeIn(AppMotion.medium), exit = fadeOut(AppMotion.medium)) {
                Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                    Surface(shape = RoundedCornerShape(Radius.xl), color = Scrim.a32) {
                        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 6.dp)) {
                            for (i in 1..5) {
                                IconButton(onClick = { haptic(HapticFeedbackType.Confirm); uiInteractionTick++; val r = if (currentRating == i) 0 else i; currentRating = r; scope.launch(Dispatchers.IO) { repo.updateRating(currentPath, r) } }, modifier = Modifier.size(40.dp)) {
                                    Icon(if (i <= currentRating) Icons.Default.Star else Icons.Default.StarBorder, stringResource(R.string.cd_rating_star, i), tint = if (i <= currentRating) RatingStarColor else Color.White.copy(alpha = 0.4f), modifier = Modifier.size(24.dp))
                                }
                            }
                            IconButton(onClick = { showRatingOverlay = false; ctx.config.viewerShowRatingBar = false }, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.Close, stringResource(R.string.action_hide), tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(16.dp)) }
                        }
                    }
                }
            }
            AnimatedVisibility(visible = showUI, enter = fadeIn(AppMotion.medium), exit = fadeOut(AppMotion.medium)) {
                Box(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = {
                            uiInteractionTick++
                            val u = androidx.core.content.FileProvider.getUriForFile(ctx, "${ctx.packageName}.provider", File(currentPath))
                            ctx.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).apply { type = if (currentIsVideo) "video/*" else "image/*"; putExtra(android.content.Intent.EXTRA_STREAM, u); addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }, ctx.getString(R.string.action_share)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                        }) { Icon(Icons.Default.Share, stringResource(R.string.action_share), tint = Color.White) }
                        IconButton(onClick = {
                            uiInteractionTick++
                            val f = !isFavorite; isFavorite = f; scope.launch(Dispatchers.IO) { repo.toggleFavorite(currentPath, f) }
                        }) { Icon(if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, stringResource(R.string.favorite), tint = if (isFavorite) FavoriteColor else Color.White) }
                        if (!currentIsVideo) {
                            IconButton(onClick = { uiInteractionTick++; (ctx as? android.app.Activity)?.openEditor(currentPath) }) { Icon(Icons.Default.Edit, stringResource(R.string.edit), tint = Color.White) }
                        }
                        IconButton(onClick = { uiInteractionTick++; deleteCurrent() }) { Icon(Icons.Default.Delete, stringResource(org.fossify.commons.R.string.delete), tint = Color.White) }
                        IconButton(onClick = { uiInteractionTick++; showActionSheet = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.more_actions), tint = Color.White) }
                    }
                }
            }
        }

        UndoBar(modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }

    if (showActionSheet) {
        var exifLines by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
        var tagSuggestions by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
        var currentTags by remember { mutableStateOf<Set<String>>(emptySet()) }
        var tagInput by remember { mutableStateOf("") }
        LaunchedEffect(showActionSheet) {
            if (showActionSheet) {
                currentTags = withContext(Dispatchers.IO) { repo.getTags(currentPath) }
                tagSuggestions = withContext(Dispatchers.IO) { repo.getTagSuggestions(24) }
            }
            if (showActionSheet && !currentIsVideo) {
                exifLines = withContext(Dispatchers.IO) {
                    val lines = mutableListOf<Pair<String, String>>()
                    try {
                        val exif = android.media.ExifInterface(currentPath)
                        exif.getAttribute(android.media.ExifInterface.TAG_DATETIME)?.let { lines.add(ctx.getString(R.string.sort_date) to it.take(10)) }
                        exif.getAttribute(android.media.ExifInterface.TAG_IMAGE_WIDTH)?.let { w -> exif.getAttribute(android.media.ExifInterface.TAG_IMAGE_LENGTH)?.let { h -> lines.add(ctx.getString(R.string.exif_resolution) to "$w × $h") } }
                        val make = exif.getAttribute(android.media.ExifInterface.TAG_MAKE) ?: ""
                        val model = exif.getAttribute(android.media.ExifInterface.TAG_MODEL) ?: ""
                        if (make.isNotBlank() || model.isNotBlank()) lines.add(ctx.getString(R.string.exif_camera) to "$make $model".trim())
                        exif.getAttribute(android.media.ExifInterface.TAG_FOCAL_LENGTH)?.let { lines.add(ctx.getString(R.string.exif_focal_length) to it) }
                        exif.getAttribute(android.media.ExifInterface.TAG_F_NUMBER)?.let { lines.add(ctx.getString(R.string.exif_aperture) to "f/$it") }
                        exif.getAttribute(android.media.ExifInterface.TAG_EXPOSURE_TIME)?.let { lines.add(ctx.getString(R.string.exif_exposure) to "${it}s") }
                        exif.getAttribute(android.media.ExifInterface.TAG_ISO)?.let { lines.add("ISO" to it) }
                        lines.add(ctx.getString(R.string.sort_size) to if (File(currentPath).length() > 1_000_000) "${File(currentPath).length() / 1_000_000} MB" else "${File(currentPath).length() / 1_000} KB")
                    } catch (_: Exception) { }
                    lines
                }
            }
        }
        val filteredSuggestions = if (tagInput.isBlank()) tagSuggestions.filter { it.first !in currentTags }.take(8) else tagSuggestions.filter { it.first.contains(tagInput, ignoreCase = true) && it.first !in currentTags }.take(12)
        fun addTag(tag: String) { val t = tag.trim().replace(",", "").replace(";", ""); if (t.isNotBlank() && t !in currentTags) { currentTags = currentTags + t; scope.launch(Dispatchers.IO) { repo.addTag(currentPath, t) }; tagInput = "" } }
        fun removeTag(tag: String) { currentTags = currentTags - tag; scope.launch(Dispatchers.IO) { repo.removeTag(currentPath, tag) } }
        ModalBottomSheet(onDismissRequest = { showActionSheet = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
                Text(File(currentPath).name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (exifLines.isNotEmpty()) { Spacer(Modifier.height(2.dp)); Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) { exifLines.take(8).forEach { (label, value) -> Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(value, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface); Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } } }
                Spacer(Modifier.height(12.dp))
                SheetSectionLabel(stringResource(R.string.rating_title))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { for (i in 1..5) { IconButton(onClick = { haptic(HapticFeedbackType.Confirm); val r = if (currentRating == i) 0 else i; currentRating = r; scope.launch(Dispatchers.IO) { repo.updateRating(currentPath, r) } }, modifier = Modifier.size(40.dp)) { Icon(if (i <= currentRating) Icons.Default.Star else Icons.Default.StarBorder, stringResource(R.string.cd_rating_star, i), tint = if (i <= currentRating) RatingStarColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f), modifier = Modifier.size(28.dp)) } } }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                SheetSectionLabel(stringResource(R.string.video_actions))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) { ActionChip(Icons.Default.Share, stringResource(R.string.action_share)) { val u = androidx.core.content.FileProvider.getUriForFile(ctx, "${ctx.packageName}.provider", File(currentPath)); ctx.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).apply { type = if (currentIsVideo) "video/*" else "image/*"; putExtra(android.content.Intent.EXTRA_STREAM, u); addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }, ctx.getString(R.string.action_share)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)); showActionSheet = false }; ActionChip(Icons.Default.Edit, stringResource(R.string.edit)) { (ctx as? android.app.Activity)?.openEditor(currentPath); showActionSheet = false }; ActionChip(Icons.Default.ContentCopy, stringResource(R.string.action_copy)) { pendingFolderPickerIsMove = false; showFolderPicker = true; showActionSheet = false }; ActionChip(Icons.AutoMirrored.Filled.DriveFileMove, stringResource(R.string.action_move)) { pendingFolderPickerIsMove = true; showFolderPicker = true; showActionSheet = false } }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) { ActionChip(Icons.Default.Info, stringResource(R.string.action_info)) { try { (ctx as? android.app.Activity)?.let { org.fossify.commons.dialogs.PropertiesDialog(it, currentPath, false) } } catch (e: Exception) { ctx.toast(ctx.getString(R.string.info_error, e.message), android.widget.Toast.LENGTH_SHORT) }; showActionSheet = false }; ActionChip(Icons.Default.Delete, stringResource(org.fossify.commons.R.string.delete), MaterialTheme.colorScheme.error) { showActionSheet = false; deleteCurrent() }; ActionChip(if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, stringResource(R.string.favorite), if (isFavorite) FavoriteColor else MaterialTheme.colorScheme.onSurfaceVariant) { val f = !isFavorite; isFavorite = f; scope.launch(Dispatchers.IO) { repo.toggleFavorite(currentPath, f) }; showActionSheet = false }; ActionChip(Icons.Default.Star, stringResource(R.string.action_rate), if (showRatingOverlay) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) { showRatingOverlay = !showRatingOverlay; ctx.config.viewerShowRatingBar = showRatingOverlay; showActionSheet = false } }
                if (currentIsVideo) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) { ActionChip(Icons.Default.AspectRatio, stringResource(R.string.video_display)) { showVideoSettings = true; showActionSheet = false }; ActionChip(Icons.Default.PhotoCamera, stringResource(R.string.action_save_frame)) { scope.launch(Dispatchers.IO) { try { val r = android.media.MediaMetadataRetriever(); r.setDataSource(currentPath); val bmp = r.frameAtTime ?: return@launch; r.release(); val parentDir = File(currentPath).parentFile ?: ctx.cacheDir; val outFile = File(parentDir, "frame_${System.currentTimeMillis()}.jpg"); outFile.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, it) }; bmp.recycle(); withContext(Dispatchers.Main) { ctx.toast(ctx.getString(R.string.frame_saved, outFile.name), android.widget.Toast.LENGTH_SHORT) } } catch (e: Exception) { withContext(Dispatchers.Main) { ctx.toast(ctx.getString(R.string.error_generic, e.message), android.widget.Toast.LENGTH_SHORT) } } }; showActionSheet = false } } }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                SheetSectionLabel(stringResource(R.string.action_tags))
                if (currentTags.isNotEmpty()) { Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { currentTags.forEach { tag -> InputChip(selected = true, onClick = {}, label = { Text(tag, style = MaterialTheme.typography.labelSmall) }, trailingIcon = { Icon(Icons.Default.Close, stringResource(R.string.action_remove), Modifier.size(14.dp).clickable { removeTag(tag) }) }, shape = RoundedCornerShape(Radius.sm), colors = InputChipDefaults.inputChipColors(containerColor = MaterialTheme.colorScheme.primaryContainer, labelColor = MaterialTheme.colorScheme.onPrimaryContainer)) } }; Spacer(Modifier.height(8.dp)) }
                OutlinedTextField(value = tagInput, onValueChange = { tagInput = it }, placeholder = { Text(stringResource(R.string.add_tag)) }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(Radius.md), colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { addTag(tagInput) }))
                if (filteredSuggestions.isNotEmpty()) { Spacer(Modifier.height(6.dp)); Text(stringResource(R.string.suggestions), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(4.dp)); Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { filteredSuggestions.forEach { (tag, count) -> Surface(onClick = { addTag(tag) }, shape = RoundedCornerShape(Radius.lg), color = MaterialTheme.colorScheme.surfaceVariant) { Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) { Text(tag, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.width(4.dp)); Text("$count", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) } } } } }
                if (tagInput.isNotBlank() && filteredSuggestions.none { it.first.equals(tagInput, ignoreCase = true) }) { Spacer(Modifier.height(6.dp)); TextButton(onClick = { addTag(tagInput) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.add_new_tag, tagInput.trim())) } }
                if (quickTags.isNotEmpty()) { Spacer(Modifier.height(6.dp)); Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { quickTags.forEach { tag -> val hasTag = currentTags.contains(tag); Surface(onClick = { scope.launch(Dispatchers.IO) { if (hasTag) repo.removeTag(currentPath, tag) else repo.addTag(currentPath, tag) }; currentTags = if (hasTag) currentTags - tag else currentTags + tag }, shape = RoundedCornerShape(Radius.lg), color = if (hasTag) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant) { Text(tag, Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall, color = if (hasTag) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant) } } } }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (showVideoSettings) {
        AlertDialog(onDismissRequest = { showVideoSettings = false }, title = { Text(stringResource(R.string.display_mode)) }, text = { Column { listOf(stringResource(R.string.video_scale_fit) to 0, stringResource(R.string.video_scale_zoom) to 4, stringResource(R.string.video_scale_stretch) to 3).forEach { (l, m) -> TextButton(onClick = { videoScalingMode = m; showVideoSettings = false }, modifier = Modifier.fillMaxWidth()) { Text(l, color = if (videoScalingMode == m) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) } } } }, confirmButton = { TextButton(onClick = { showVideoSettings = false }) { Text(stringResource(R.string.cd_close)) } })
    }

    if (showTagsDialog) {
        var allTags by remember { mutableStateOf<List<String>>(emptyList()) }
        LaunchedEffect(Unit) {
            allTags = withContext(Dispatchers.IO) { repo.getAllTags() }
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

    if (showDeleteConfirm) {
        val question = stringResource(if (ctx.config.useRecycleBin) org.fossify.commons.R.string.are_you_sure_recycle_bin else org.fossify.commons.R.string.are_you_sure_delete)
        ConfirmDestructive(
            title = stringResource(org.fossify.commons.R.string.delete),
            text = question,
            confirmLabel = stringResource(org.fossify.commons.R.string.delete),
            onConfirm = { showDeleteConfirm = false; performDelete() },
            onDismiss = { showDeleteConfirm = false },
        )
    }
}

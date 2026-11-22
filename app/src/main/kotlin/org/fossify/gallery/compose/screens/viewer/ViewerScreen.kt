package org.fossify.gallery.compose.screens.viewer
import androidx.compose.ui.res.stringResource
import org.fossify.gallery.R
import org.fossify.gallery.compose.theme.Radius
import org.fossify.gallery.compose.theme.Scrim

import android.widget.Toast
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.ui.draw.drawBehind
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.BurstMode
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
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
import org.fossify.gallery.helpers.EXT_NAME
import org.fossify.gallery.helpers.EXT_PATH
import org.fossify.gallery.helpers.EXT_SIZE
import org.fossify.gallery.helpers.EXT_RESOLUTION
import org.fossify.gallery.helpers.EXT_LAST_MODIFIED
import org.fossify.gallery.helpers.EXT_DATE_TAKEN
import org.fossify.gallery.helpers.EXT_CAMERA_MODEL
import org.fossify.gallery.helpers.EXT_EXIF_PROPERTIES
import org.fossify.gallery.helpers.EXT_GPS
import org.fossify.gallery.compose.components.ConfirmDestructive
import org.fossify.gallery.compose.components.MediaInfoDialog
import org.fossify.gallery.compose.components.SelectionRow
import org.fossify.gallery.compose.components.LibrarySheetHeader
import org.fossify.gallery.compose.util.rememberGalleryHaptics
import org.fossify.gallery.compose.theme.AppMotion
import org.fossify.gallery.compose.theme.FavoriteColor
import org.fossify.gallery.compose.theme.RatingStarColor
import org.fossify.gallery.compose.components.RenameDialog
import org.fossify.gallery.compose.components.RateAndTagSheet
import org.fossify.gallery.compose.components.TagInputDialog
import org.fossify.gallery.compose.components.TagHierarchyPickerDialog
import org.fossify.gallery.compose.components.UndoBar
import org.fossify.gallery.compose.screens.FolderPickerSheet
import org.fossify.gallery.compose.theme.LocalMediaRepository
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.getDecodedImageDimensions
import org.fossify.gallery.extensions.openEditor
import org.fossify.gallery.helpers.VIDEO_EXTENSIONS
import org.fossify.gallery.helpers.MAX_RECENT_TAGS
import org.fossify.gallery.helpers.UndoAction
import org.fossify.gallery.helpers.UndoManager
import org.fossify.gallery.helpers.withRecycleBinDeletePasswordCheck
import org.fossify.gallery.helpers.UndoType
import org.fossify.gallery.helpers.QuickTagsBus
import org.fossify.gallery.helpers.ViewerLoadDirection
import java.io.File
import kotlin.math.abs

private fun isVideo(path: String) = path.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(
    paths: List<String>,
    startIndex: Int = 0,
    startPath: String? = null,
    onLoadMore: (suspend (ViewerLoadDirection, String) -> Unit)? = null,
    onClose: () -> Unit,
) {
    val ctx = LocalContext.current
    val res = androidx.compose.ui.platform.LocalResources.current
    val haptic = rememberGalleryHaptics()
    val scope = rememberCoroutineScope()
    // The video brightness-drag writes screenBrightness onto the Activity window - and this is a
    // single-Activity app, so that window backs the whole UI (grid, albums, settings, ...). Without
    // restoring it on the way out, dimming one video left the ENTIRE app stuck at that brightness
    // until the process was recreated. Reset to the system default when the Viewer leaves composition,
    // making the override viewer-scoped.
    DisposableEffect(Unit) {
        onDispose {
            (ctx as? android.app.Activity)?.let { act ->
                val attrs = act.window.attributes
                attrs.screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                act.window.attributes = attrs
                org.fossify.gallery.helpers.ColorModeHelper.resetColorMode(act)
            }
        }
    }
    val items = remember { paths.toMutableStateList() }
    val initialPage = startPath?.let { path -> paths.indexOf(path).takeIf { it >= 0 } }
        ?: startIndex.coerceIn(0, (paths.size - 1).coerceAtLeast(0))
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { items.size })
    LaunchedEffect(paths) {
        val visiblePath = items.getOrNull(pagerState.currentPage)
        if (items != paths) {
            items.clear()
            items.addAll(paths)
        }
        // Keep the currently visible path anchored when a continuation page is prepended or
        // appended. Re-selecting startPath here would snap every later update back to the original
        // image after the user had already swiped away from it.
        val targetPage = visiblePath?.let { items.indexOf(it).takeIf { index -> index >= 0 } }
        if (targetPage != null && targetPage != pagerState.currentPage) {
            pagerState.scrollToPage(targetPage)
        }
    }
    LaunchedEffect(pagerState, onLoadMore) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            val anchor = items.getOrNull(page) ?: return@collect
            if (page <= 2) onLoadMore?.invoke(ViewerLoadDirection.BEFORE, anchor)
            if (items.lastIndex - page <= 2) onLoadMore?.invoke(ViewerLoadDirection.AFTER, anchor)
        }
    }
    // Prefetch 2-ahead neighbor images into Coil's memory cache (beyondViewportPageCount = 1 already
    // composes +/-1) using ImagePage's exact viewer cache key so rapid swiping hits RAM instead of disk.
    LaunchedEffect(pagerState, items.size) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            val loader = coil.Coil.imageLoader(ctx)
            val animateGifs = org.fossify.gallery.compose.util.ThumbnailOverlayState.animateGifs
            listOf(page + 2, page - 2).forEach { targetIndex ->
                val targetPath = items.getOrNull(targetIndex) ?: return@forEach
                if (isVideo(targetPath)) return@forEach
                val file = File(targetPath)
                val ext = targetPath.substringAfterLast('.', "").lowercase()
                val forceStatic = (ext == "gif" || ext == "webp") && !animateGifs
                val req = coil.request.ImageRequest.Builder(ctx)
                    .data(android.net.Uri.fromFile(file))
                    .size(2560)
                    .memoryCacheKey("viewer:${file.absolutePath}:0")
                    .apply { if (forceStatic) decoderFactory(coil.decode.BitmapFactoryDecoder.Factory()) }
                    .build()
                loader.enqueue(req)
            }
        }
    }
    // Always starts visible, videos included - a video used to open with no chrome at all (no
    // visible exit, no context) since this used to special-case `!isVideo(...)`. The existing
    // auto-hide effect below (keyed on showUI/uiInteractionTick) already hides it again after a few
    // seconds regardless of media type, so dropping the video branch here doesn't leave the chrome
    // stuck on-screen - it just gives a video the same brief, visible-on-open moment a photo gets.
    var showUI by remember { mutableStateOf(true) }
    var showActionSheet by remember { mutableStateOf(false) }
    var showRateAndTagSheet by remember { mutableStateOf(false) }
    var slideshowActive by remember { mutableStateOf(false) }
    var showSlideshowDialog by remember { mutableStateOf(false) }
    val currentPath = items.getOrNull(pagerState.currentPage) ?: ""
    val currentIsVideo = isVideo(currentPath)
    LaunchedEffect(currentIsVideo) {
        if (currentIsVideo) {
            org.fossify.gallery.helpers.ColorModeHelper.resetColorMode(ctx as? android.app.Activity)
        }
    }
    DisposableEffect(slideshowActive) {
        val window = (ctx as? android.app.Activity)?.window
        if (slideshowActive) {
            showUI = false
            window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            if (slideshowActive && !ctx.config.keepScreenOn) {
                window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }
    LaunchedEffect(slideshowActive, pagerState.currentPage, items.size) {
        if (!slideshowActive || items.size <= 1) return@LaunchedEffect
        val intervalMs = ctx.config.slideshowInterval.coerceIn(1, 60) * 1000L
        delay(intervalMs)
        if (!slideshowActive || items.size <= 1) return@LaunchedEffect
        val nextPage = if (ctx.config.slideshowRandomOrder) {
            val candidates = items.indices.filter { it != pagerState.currentPage }
            candidates.randomOrNull()
        } else {
            val sequential = pagerState.currentPage + 1
            when {
                sequential < items.size -> sequential
                ctx.config.loopSlideshow -> 0
                else -> null
            }
        }
        if (nextPage != null) {
            pagerState.animateScrollToPage(nextPage)
        } else {
            slideshowActive = false
            showUI = true
            ctx.toast(R.string.slideshow_ended)
        }
    }
    val repo = LocalMediaRepository.current
    var isFavorite by remember { mutableStateOf(false) }
    var showFolderPicker by remember { mutableStateOf(false) }
    var pendingFolderPickerIsMove by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var infoDialogPath by remember { mutableStateOf<String?>(null) }
    var showQuickTags by remember { mutableStateOf(false) }
    var showPersistentTags by remember { mutableStateOf(true) }
    var tagRefreshTrigger by remember { mutableIntStateOf(0) }
    val quickTagsVersion by QuickTagsBus.version.collectAsStateWithLifecycle()
    val quickTags = remember(quickTagsVersion) { ctx.config.quickTags.toList() }
    val recentTags = remember(quickTagsVersion) { ctx.config.recentTags }
    var currentRating by remember { mutableIntStateOf(0) }
    var editorTags by remember(currentPath) { mutableStateOf<Set<String>>(emptySet()) }
    var editorTagSuggestions by remember(currentPath) { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
    LaunchedEffect(showRateAndTagSheet, currentPath) {
        if (showRateAndTagSheet) {
            val (tags, suggestions) = withContext(Dispatchers.IO) {
                repo.getTags(currentPath) to repo.getTagSuggestions(48)
            }
            editorTags = tags
            editorTagSuggestions = suggestions
        }
    }
    // Keep rating one tap away in the viewer. The preference only controls whether the compact
    // rail is shown; the rating itself remains available in the action sheet as a fallback.
    var showRatingOverlay by remember { mutableStateOf(ctx.config.viewerShowRatingBar) }
    var videoScalingMode by remember { mutableIntStateOf(0) }
    var showVideoSettings by remember { mutableStateOf(false) }
    var showVideoTrimmer by remember { mutableStateOf(false) }
    var showFrameGrabber by remember { mutableStateOf(false) }
    var captureFrameRequest by remember { mutableIntStateOf(0) }
    var captureFramePath by remember { mutableStateOf<String?>(null) }
    var volumePreview by remember { mutableStateOf<Int?>(null) }
    val viewerBgColor = if (ctx.config.blackBackground) Color.Black else MaterialTheme.colorScheme.background
    // Inside the tiny PiP window every overlay is noise - chrome is forced hidden and the
    // persistent overlays below gate on !inPip.
    val inPip = org.fossify.gallery.compose.util.PipState.inPip
    LaunchedEffect(inPip) { if (inPip) showUI = false }
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
    // The video-only left/right brightness/volume drag zones have no visual affordance either -
    // shown once, the first time a video is opened with gestures enabled (independent of the
    // generic up/down hint above, which may already have been dismissed on a photo).
    var showVideoGestureHint by remember { mutableStateOf(!ctx.config.hasSeenVideoGestureZonesHint) }
    fun dismissVideoGestureHint() {
        if (showVideoGestureHint) {
            showVideoGestureHint = false
            ctx.config.hasSeenVideoGestureZonesHint = true
        }
    }
    val showVideoGestureHintNow = showVideoGestureHint && currentIsVideo && ctx.config.allowVideoGestures
    LaunchedEffect(showVideoGestureHintNow) { if (showVideoGestureHintNow) { delay(3000); dismissVideoGestureHint() } }
    LaunchedEffect(pagerState.currentPage) {
        isCurrentZoomed = false
        withContext(Dispatchers.IO) {
            isFavorite = repo.isFavorite(currentPath)
            currentRating = try { repo.getRating(currentPath) } catch (_: Exception) { 0 }
        }
    }
    var extendedDetailsText by remember { mutableStateOf("") }
    LaunchedEffect(currentPath, currentIsVideo) {
        extendedDetailsText = if (ctx.config.showExtendedDetails) withContext(Dispatchers.IO) { buildExtendedDetailsText(ctx, currentPath, currentIsVideo) } else ""
    }

    val autoHideMs = ctx.config.viewerAutoHideMs
    LaunchedEffect(showUI, uiInteractionTick) { if (showUI) { delay(autoHideMs.toLong()); showUI = false } }

    // "Hide system UI" ties the status/nav bars to the same auto-hide chrome state as the in-app
    // overlay (mirrors the legacy ViewPagerActivity's hideSystemUI()/showSystemUI() calls, which
    // fire from the identical auto-hide-controls callback) instead of being a separate always-on
    // immersive mode - so it can reuse showUI's existing timer/tap-to-toggle logic below.
    LaunchedEffect(showUI) {
        val window = (ctx as? android.app.Activity)?.window ?: return@LaunchedEffect
        if (!ctx.config.hideSystemUI) return@LaunchedEffect
        val controller = androidx.core.view.WindowInsetsControllerCompat(window, window.decorView)
        if (showUI) {
            controller.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        } else {
            controller.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
    }

    DisposableEffect(Unit) {
        val window = (ctx as? android.app.Activity)?.window
        var originalBrightness: Float? = null
        var originalCutoutMode: Int? = null
        if (window != null) {
            if (ctx.config.keepScreenOn) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (ctx.config.maxBrightness) originalBrightness = window.updateBrightness(true, null)
            // showNotch=false lets fullscreen photo/video content extend behind the display cutout
            // instead of the system reserving a permanent letterboxed strip for it (mirrors
            // BaseViewerActivity's identical cutout-mode toggle for the legacy Views viewer).
            if (!ctx.config.showNotch && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                originalCutoutMode = window.attributes.layoutInDisplayCutoutMode
                window.attributes = window.attributes.apply { layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES }
            }
        }
        onDispose {
            if (window != null) {
                if (ctx.config.keepScreenOn) window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                if (ctx.config.maxBrightness) window.updateBrightness(false, originalBrightness)
                if (ctx.config.hideSystemUI) androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P && originalCutoutMode != null) {
                    window.attributes = window.attributes.apply { layoutInDisplayCutoutMode = originalCutoutMode }
                }
            }
        }
    }

    var showDeleteConfirm by remember { mutableStateOf(false) }
    fun performDelete() {
        val idx = pagerState.currentPage
        val p = items.getOrNull(idx) ?: return
        ctx.withRecycleBinDeletePasswordCheck {
            ctx.config.lastViewedPath = p
            scope.launch(Dispatchers.IO) { repo.moveToRecycleBin(p) }
            UndoManager.push(UndoAction(paths = setOf(p), type = UndoType.DELETE))
            if (items.size <= 1) onClose() else {
                items.removeAt(idx)
                if (idx >= items.size) scope.launch { pagerState.scrollToPage(items.size - 1) }
            }
        }
    }
    fun deleteCurrent() {
        if (ctx.config.skipDeleteConfirmation) performDelete() else showDeleteConfirm = true
    }

    fun toggleFavorite() {
        val path = currentPath
        val next = !isFavorite
        isFavorite = next
        scope.launch(Dispatchers.IO) {
            val ok = repo.toggleFavorite(path, next)
            if (!ok) withContext(Dispatchers.Main) {
                if (currentPath == path) isFavorite = !next
            }
        }
    }

    // The viewer rail and the action sheet share the same immediate rating operation. A failed
    // write rolls the optimistic UI back instead of showing a rating that was never stored.
    fun rateCurrent(rating: Int) {
        haptic(HapticFeedbackType.Confirm)
        uiInteractionTick++
        val path = currentPath
        val previous = currentRating
        currentRating = if (currentRating == rating) 0 else rating
        val next = currentRating
        scope.launch(Dispatchers.IO) {
            val ratingSaved = repo.updateRating(path, next)
            if (!ratingSaved) withContext(Dispatchers.Main) {
                if (currentPath == path) {
                    currentRating = previous
                }
                ctx.toast(R.string.rating_write_failed, Toast.LENGTH_SHORT)
            }
        }
    }

    fun saveVideoFrame(sourcePath: String, bitmap: android.graphics.Bitmap?) {
        if (bitmap == null) {
            ctx.toast(res.getString(R.string.error_generic, res.getString(R.string.frame_capture_failed)), Toast.LENGTH_SHORT)
            return
        }
        scope.launch(Dispatchers.IO) {
            try {
                val sourceFile = File(sourcePath)
                val parent = sourceFile.parentFile ?: error("Video has no parent directory")
                val screensDir = File(parent, "screens")
                if (!screensDir.isDirectory && !screensDir.mkdirs() && !screensDir.isDirectory) {
                    error("Could not create ${screensDir.absolutePath}")
                }
                val stem = sourceFile.nameWithoutExtension.ifBlank { "video" }
                var suffix = 0
                var output: File
                do {
                    val suffixText = if (suffix == 0) "" else "_$suffix"
                    output = File(screensDir, "${stem}_screen_${System.currentTimeMillis()}$suffixText.jpg")
                    suffix++
                } while (output.exists())
                output.outputStream().use { stream ->
                    check(bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, stream)) { "Could not encode screenshot" }
                }
                bitmap.recycle()
                runCatching {
                    android.media.MediaScannerConnection.scanFile(ctx, arrayOf(output.absolutePath), arrayOf("image/jpeg"), null)
                }
                org.fossify.gallery.helpers.RefreshBus.trigger(affectedPaths = listOf(output.absolutePath))
                withContext(Dispatchers.Main) {
                    ctx.toast(res.getString(R.string.frame_saved, "screens/${output.name}"), Toast.LENGTH_SHORT)
                }
            } catch (e: Exception) {
                runCatching { bitmap.recycle() }
                withContext(Dispatchers.Main) {
                    ctx.toast(res.getString(R.string.error_generic, e.message ?: ""), Toast.LENGTH_SHORT)
                }
            }
        }
    }

    val viewerBackState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
    NavigationBackHandler(
        state = viewerBackState,
        isBackEnabled = slideshowActive || showSlideshowDialog || infoDialogPath != null || showActionSheet || showRateAndTagSheet || showVideoSettings || showFolderPicker || showRenameDialog,
        onBackCompleted = {
            when {
                slideshowActive -> { slideshowActive = false; showUI = true }
                showSlideshowDialog -> showSlideshowDialog = false
                infoDialogPath != null -> infoDialogPath = null
                showRenameDialog -> showRenameDialog = false
                showFolderPicker -> showFolderPicker = false
                showRateAndTagSheet -> showRateAndTagSheet = false
                showVideoSettings -> showVideoSettings = false
                showActionSheet -> showActionSheet = false
            }
        },
    )

    var dragOffset by remember { mutableFloatStateOf(0f) }
    // Tracks whether the drag is currently past either dismiss threshold, so the haptic tick fires
    // once on crossing into the zone rather than continuously every frame the drag stays past it.
    var dismissThresholdCrossed by remember { mutableStateOf(false) }
    // 0 = dismiss/action-sheet (center third, or anywhere on a non-video page), 1 = brightness
    // (left third of a video), 2 = volume (right third) - decided once at drag start. This has to
    // be branches of the *same* detectVerticalDragGestures call, not a second competing detector
    // layered on top of (or inside) VideoPage: Compose's pointer input consumption is cooperative,
    // so a child's own vertical-drag detector consuming events would silently stop this one from
    // ever firing (or vice versa) - the same class of bug already fixed once for double-tap
    // (see zoomable()'s doc in ZoomableBox.kt), just for drag instead of tap.
    var dragMode by remember { mutableIntStateOf(0) }
    var volumeAccum by remember { mutableFloatStateOf(0f) }
    var brightnessPreview by remember { mutableStateOf<Float?>(null) }
    val audioManager = remember { ctx.getSystemService(android.content.Context.AUDIO_SERVICE) as? android.media.AudioManager }

    val viewerFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { viewerFocusRequester.requestFocus() }
    }
    val hasModalOpen = showActionSheet || showRateAndTagSheet || showRenameDialog || showFolderPicker || infoDialogPath != null || showDeleteConfirm

    // dragScale/dragAlpha used to be composable-body vals derived from dragOffset - reading
    // dragOffset there recomposed the whole ViewerScreen (top bar, bottom controls, everything) on
    // every touch-move frame of the swipe-up/-down gesture, even though dragScale was only ever
    // consumed inside graphicsLayer{} and dragAlpha only inside a draw call. Both are now computed
    // directly inside their respective draw-phase lambdas below, so a drag only triggers a redraw.
    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(viewerFocusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (hasModalOpen || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> {
                        val prev = pagerState.currentPage - 1
                        if (prev >= 0) scope.launch { pagerState.animateScrollToPage(prev) }
                        true
                    }
                    Key.DirectionRight -> {
                        val next = pagerState.currentPage + 1
                        if (next < items.size) scope.launch { pagerState.animateScrollToPage(next) }
                        true
                    }
                    Key.Escape -> {
                        ctx.config.lastViewedPath = currentPath
                        onClose()
                        true
                    }
                    Key.F -> {
                        uiInteractionTick++
                        toggleFavorite()
                        true
                    }
                    Key.One -> { rateCurrent(1); true }
                    Key.Two -> { rateCurrent(2); true }
                    Key.Three -> { rateCurrent(3); true }
                    Key.Four -> { rateCurrent(4); true }
                    Key.Five -> { rateCurrent(5); true }
                    Key.Zero -> {
                        if (currentRating > 0) rateCurrent(currentRating)
                        true
                    }
                    Key.Delete, Key.Backspace -> {
                        deleteCurrent()
                        true
                    }
                    else -> false
                }
            }
            .drawBehind {
                val dragAlpha = (1f - (abs(dragOffset) / 520f)).coerceIn(0.15f, 1f)
                drawRect(viewerBgColor.copy(alpha = dragAlpha))
            }
    ) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !isCurrentZoomed,
            // Preload one neighbour on each side so the adjacent image/video frame is already
            // decoded by the time a swipe lands on it, instead of a blank frame flashing in.
            // Video playback itself still only starts on the current page (VideoPage gates it on
            // isCurrentPage), so this doesn't spin up extra players.
            beyondViewportPageCount = 1,
            key = { items.getOrNull(it) ?: it },
            modifier = Modifier.fillMaxSize()
                .graphicsLayer {
                    translationY = dragOffset
                    val dragScale = (1f - (abs(dragOffset) / 1000f)).coerceIn(0.85f, 1f)
                    scaleX = dragScale
                    scaleY = dragScale
                }
                .pointerInput(isCurrentZoomed, currentIsVideo) {
                    if (!isCurrentZoomed) {
                        detectVerticalDragGestures(
                            onDragStart = { offset ->
                                dismissGestureHint()
                                dismissVideoGestureHint()
                                dragMode = if (currentIsVideo && ctx.config.allowVideoGestures) {
                                    when {
                                        offset.x < size.width / 3f -> 1
                                        offset.x > size.width * 2 / 3f -> 2
                                        else -> 0
                                    }
                                } else 0
                                if (dragMode == 0) { dragOffset = 0f; dismissThresholdCrossed = false }
                                volumeAccum = 0f
                            },
                            onDragEnd = {
                                when (dragMode) {
                                    0 -> {
                                        val openSheetAfterSettle = dragOffset < -180f
                                        if (ctx.config.allowDownGesture && dragOffset > 240f) {
                                            ctx.config.lastViewedPath = currentPath
                                            onClose()
                                            return@detectVerticalDragGestures
                                        }
                                        scope.launch {
                                            animate(dragOffset, 0f, animationSpec = AppMotion.gestureSpring) { v, _ -> dragOffset = v }
                                            if (openSheetAfterSettle) showActionSheet = true
                                        }
                                    }
                                    1 -> brightnessPreview = null
                                    2 -> volumePreview = null
                                }
                            },
                            onVerticalDrag = { _, drag ->
                                when (dragMode) {
                                    0 -> {
                                        val nextOffset = dragOffset + drag
                                        dragOffset = if (ctx.config.allowDownGesture) nextOffset else nextOffset.coerceAtMost(0f)
                                        val crossed = dragOffset < -180f || (ctx.config.allowDownGesture && dragOffset > 240f)
                                        if (crossed && !dismissThresholdCrossed) haptic(HapticFeedbackType.GestureThresholdActivate)
                                        dismissThresholdCrossed = crossed
                                    }
                                    1 -> {
                                        val window = (ctx as? android.app.Activity)?.window
                                        if (window != null) {
                                            val attrs = window.attributes
                                            val current = if (attrs.screenBrightness in 0f..1f) attrs.screenBrightness else 0.5f
                                            val updated = (current - drag / size.height).coerceIn(0.01f, 1f)
                                            attrs.screenBrightness = updated
                                            window.attributes = attrs
                                            brightnessPreview = updated
                                        }
                                    }
                                    2 -> audioManager?.let { am ->
                                        val maxVol = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                                        volumeAccum += -drag / size.height * maxVol
                                        if (abs(volumeAccum) >= 1f) {
                                            val step = volumeAccum.toInt()
                                            val curVol = am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
                                            val newVol = (curVol + step).coerceIn(0, maxVol)
                                            am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, newVol, 0)
                                            volumePreview = ((newVol * 100) / maxVol).coerceIn(0, 100)
                                            volumeAccum -= step
                                        } else if (volumePreview == null) {
                                            val curVol = am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
                                            volumePreview = ((curVol * 100) / maxVol).coerceIn(0, 100)
                                        }
                                    }
                                }
                            },
                        )
                    }
                }
        ) { page ->
            val path = items.getOrNull(page) ?: ""
            val file = remember(path) { File(path) }
            var exists by remember(path) { mutableStateOf<Boolean?>(null) }
            var availabilityRetryNonce by remember(path) { mutableIntStateOf(0) }
            LaunchedEffect(path, availabilityRetryNonce) {
                exists = null
                exists = withContext(Dispatchers.IO) { file.exists() }
            }
            when {
                exists == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                exists == false -> ViewerMediaErrorState(
                    file = file,
                    isAvailable = false,
                    onRetry = { availabilityRetryNonce++ },
                )
                isVideo(path) -> VideoPage(
                    path = path, scalingMode = videoScalingMode,
                    onScalingModeChange = { videoScalingMode = it },
                    onToggleUi = {
                        if (slideshowActive) { slideshowActive = false; showUI = true }
                        else showUI = !showUI
                    },
                    showUi = showUI,
                    onInteract = { uiInteractionTick++ },
                    onZoomChange = { if (page == pagerState.currentPage) isCurrentZoomed = it },
                    isCurrentPage = page == pagerState.currentPage,
                    captureFrameRequest = if (page == pagerState.currentPage && path == captureFramePath) captureFrameRequest else 0,
                    onCaptureFrame = { bitmap ->
                        if (captureFramePath == path) {
                            captureFramePath = null
                            saveVideoFrame(path, bitmap)
                        } else {
                            // The pager may have moved on while the fallback decoder was working.
                            bitmap?.recycle()
                        }
                    },
                )
                else -> ImagePage(
                    path = path, file = file, onClose = onClose,
                    onToggleUi = {
                        if (slideshowActive) { slideshowActive = false; showUI = true }
                        else showUI = !showUI
                    },
                    onZoomChange = { if (page == pagerState.currentPage) isCurrentZoomed = it },
                    isCurrentPage = page == pagerState.currentPage,
                )
            }
        }

        brightnessPreview?.let { level ->
            Surface(modifier = Modifier.align(Alignment.Center), shape = RoundedCornerShape(Radius.md), color = Scrim.a60) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.BrightnessMedium, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("${(level * 100).toInt()}%", color = Color.White, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        volumePreview?.let { pct ->
            Surface(modifier = Modifier.align(Alignment.Center), shape = RoundedCornerShape(Radius.md), color = Scrim.a60) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.AutoMirrored.Filled.VolumeUp,
                        null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("$pct%", color = Color.White, style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        // Top bar
        // The main viewer chrome is one stable state. It appears/disappears as a single layer so
        // the top bar, bottom actions and scrim cannot drift through separate fade timelines.
        AnimatedVisibility(
            visible = showUI && !inPip,
            enter = fadeIn(AppMotion.medium),
            exit = fadeOut(AppMotion.medium),
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
        ) {
            // The viewer is edge-to-edge. Keep the interactive chrome below the system status
            // icons instead of letting the close/title/favorite controls sit underneath them.
            Box(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                IconButton(
                    onClick = { ctx.config.lastViewedPath = currentPath; onClose() },
                    modifier = Modifier.align(Alignment.CenterStart)
                        .background(Scrim.a40, CircleShape).size(48.dp)
                ) { Icon(Icons.Default.Close, stringResource(R.string.cd_close), tint = Color.White) }
                if (currentPath.isNotBlank()) {
                    Surface(
                        modifier = Modifier.align(Alignment.Center).padding(horizontal = 52.dp).widthIn(max = 280.dp),
                        shape = RoundedCornerShape(Radius.lg),
                        color = Scrim.a40,
                    ) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                File(currentPath).name,
                                color = Color.White,
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (items.size > 1) Text(
                                "${pagerState.currentPage + 1} / ${items.size}",
                                color = Color.White.copy(alpha = 0.8f),
                                style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
                            )
                        }
                    }
                }
            }
        }

        // "Hide extended details" (default on) ties this overlay's visibility to the same chrome
        // state as the top/bottom bars; turning it off keeps the details on screen even in
        // fullscreen (mirrors the legacy Views viewer's identical `!hideExtendedDetails ||
        // !isFullscreen` alpha logic in PhotoFragment/VideoFragment).
        if (extendedDetailsText.isNotEmpty() && !inPip && (showUI || !ctx.config.hideExtendedDetails)) {
            Surface(
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 72.dp, start = 16.dp, end = 16.dp),
                shape = RoundedCornerShape(Radius.md),
                color = Scrim.a40,
            ) {
                Text(
                    extendedDetailsText,
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 300.dp).padding(horizontal = 10.dp, vertical = 6.dp),
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
            Surface(
                shape = RoundedCornerShape(Radius.lg),
                color = Scrim.a60,
                modifier = Modifier.clickable { dismissGestureHint() },
            ) {
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
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.viewer_gesture_hint_tap), color = Color.White, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // One-time hint for the invisible video-only left/right drag zones (brightness/volume) -
        // otherwise a tap near either edge can silently dim the screen or change volume with no
        // explanation. Independent of the swipe up/down hint above.
        AnimatedVisibility(
            visible = showVideoGestureHintNow,
            enter = fadeIn(AppMotion.medium),
            exit = fadeOut(AppMotion.medium),
            // Offset below center so it doesn't overlap the swipe up/down hint above on the rare
            // first-ever-video-open case where both are shown at once.
            modifier = Modifier.align(Alignment.Center).offset(y = 100.dp).padding(horizontal = 24.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = RoundedCornerShape(Radius.lg), color = Scrim.a60) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Icon(Icons.Default.BrightnessMedium, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.viewer_gesture_hint_brightness), color = Color.White, style = MaterialTheme.typography.labelSmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
                Surface(shape = RoundedCornerShape(Radius.lg), color = Scrim.a60) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Icon(
                            Icons.AutoMirrored.Filled.VolumeUp,
                            null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.viewer_gesture_hint_volume), color = Color.White, style = MaterialTheme.typography.labelSmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }
        }

        // Soft bottom scrim for control legibility, anchored to the screen bottom so it never floats
        // with a hard edge even when the controls are lifted above the video seek bar.
        AnimatedVisibility(
            visible = showUI && !inPip,
            enter = fadeIn(AppMotion.medium),
            exit = fadeOut(AppMotion.medium),
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(Brush.verticalGradient(0f to Color.Transparent, 1f to Scrim.a60)),
            )
        }

        // Bottom overlays in one column: direct rating rail above the action bar (with the UI), so
        // rating and actions never overlap and the rail slides smoothly with the viewer chrome. For
        // videos the group sits permanently above the seek bar (constant offset).
        val bottomGroupOffset = if (currentIsVideo) 64.dp else 0.dp
        val showFilmstrip = showUI && !inPip && items.size > 1
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = (1f - (abs(dragOffset) / 160f)).coerceIn(0f, 1f)
                }
                .navigationBarsPadding()
                .padding(bottom = bottomGroupOffset),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (showUI && !inPip) {
                // A quiet, persistent handle makes the swipe-up action sheet discoverable without
                // adding another text label to the already dense viewer chrome.
                Box(
                    Modifier.padding(bottom = 2.dp).width(32.dp).height(4.dp).clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.58f))
                )
            }
            AnimatedVisibility(
                visible = showFilmstrip,
                enter = fadeIn(AppMotion.medium),
                exit = fadeOut(AppMotion.medium),
            ) {
                ViewerFilmstrip(
                    items = items,
                    currentPage = pagerState.currentPage,
                    onSelectPage = { targetIdx ->
                        uiInteractionTick++
                        scope.launch { pagerState.animateScrollToPage(targetIdx) }
                    },
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
            AnimatedVisibility(
                visible = showRatingOverlay && showUI && !inPip,
                enter = fadeIn(AppMotion.medium),
                exit = fadeOut(AppMotion.medium),
            ) {
                Surface(
                    shape = RoundedCornerShape(Radius.xl),
                    color = Scrim.a40,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    ) {
                        for (i in 1..5) {
                            IconButton(
                                onClick = { rateCurrent(i) },
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(
                                    imageVector = if (i <= currentRating) Icons.Default.Star else Icons.Default.StarBorder,
                                    contentDescription = stringResource(R.string.cd_rating_star, i),
                                    tint = if (i <= currentRating) RatingStarColor else Color.White.copy(alpha = 0.72f),
                                    modifier = Modifier.size(28.dp),
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                uiInteractionTick++
                                toggleFavorite()
                            },
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                stringResource(R.string.favorite),
                                tint = if (isFavorite) FavoriteColor else Color.White.copy(alpha = 0.72f),
                                modifier = Modifier.size(26.dp),
                            )
                        }
                    }
                }
            }
            AnimatedVisibility(
                visible = showUI && !inPip,
                enter = fadeIn(AppMotion.medium),
                exit = fadeOut(AppMotion.medium),
            ) {
                Box(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = {
                            uiInteractionTick++
                            try {
                                val targetFile = File(currentPath)
                                if (!targetFile.exists()) {
                                    ctx.toast(R.string.no_files_found)
                                } else {
                                    val u = androidx.core.content.FileProvider.getUriForFile(ctx, "${ctx.packageName}.provider", targetFile)
                                    ctx.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).apply { type = if (currentIsVideo) "video/*" else "image/*"; putExtra(android.content.Intent.EXTRA_STREAM, u); addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }, res.getString(R.string.action_share)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("Viewer", "Share failed for $currentPath", e)
                                ctx.toast(R.string.viewer_share_failed)
                            }
                        }) { Icon(Icons.Default.Share, stringResource(R.string.action_share), tint = Color.White) }
                        IconButton(onClick = {
                            uiInteractionTick++
                            showRateAndTagSheet = true
                        }) {
                            Icon(Icons.AutoMirrored.Filled.Label, stringResource(R.string.action_tags), tint = Color.White)
                        }
                        if (!showRatingOverlay) {
                            IconButton(onClick = {
                                uiInteractionTick++
                                toggleFavorite()
                            }) {
                                Icon(if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, stringResource(R.string.favorite), tint = if (isFavorite) FavoriteColor else Color.White)
                            }
                        }
                        // Rating and favorite already have their dedicated rail above. Keep this
                        // row for the frequent actions and move edit/frame capture into More;
                        // six equally weighted icons made it hard to find the next action.
                        IconButton(onClick = { uiInteractionTick++; deleteCurrent() }) {
                            Icon(Icons.Default.Delete, stringResource(org.fossify.commons.R.string.delete), tint = Color(0xFFFFB4AB))
                        }
                        IconButton(onClick = { uiInteractionTick++; showActionSheet = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.more_actions), tint = Color.White) }
                    }
                }
            }
        }

        // Keep the undo affordance above the viewer action row AND rating rail so it never
        // covers either the delete button or the rating stars.
        if (!inPip) {
            val filmstripClearance = if (showFilmstrip) 56.dp else 0.dp
            val undoClearance = if (showUI) {
                (if (showRatingOverlay) 128.dp else 72.dp) + filmstripClearance
            } else 0.dp
            UndoBar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = bottomGroupOffset + undoClearance),
            )
        }
    }

    if (showActionSheet) {
        ViewerActionSheet(
            currentPath = currentPath,
            currentIsVideo = currentIsVideo,
            currentRating = currentRating,
            showRatingOverlay = showRatingOverlay,
            slideshowActive = slideshowActive,
            canRunSlideshow = items.size > 1,
            onRate = ::rateCurrent,
            onToggleRatingOverlay = {
                showRatingOverlay = !showRatingOverlay
                ctx.config.viewerShowRatingBar = showRatingOverlay
                showActionSheet = false
            },
            onRename = { showRenameDialog = true; showActionSheet = false },
            onCopy = { pendingFolderPickerIsMove = false; showFolderPicker = true; showActionSheet = false },
            onMove = { pendingFolderPickerIsMove = true; showFolderPicker = true; showActionSheet = false },
            onInfo = { infoDialogPath = currentPath; showActionSheet = false },
            onTrimVideo = { showVideoTrimmer = true; showActionSheet = false },
            onVideoDisplaySettings = { showVideoSettings = true; showActionSheet = false },
            onSaveFrame = { captureFramePath = currentPath; captureFrameRequest++; showActionSheet = false },
            onOpenFrameGrabber = { showFrameGrabber = true; showActionSheet = false },
            onEditImage = {
                (ctx as? android.app.Activity)?.openEditor(currentPath)
                showActionSheet = false
            },
            onToggleSlideshow = {
                showActionSheet = false
                if (slideshowActive) slideshowActive = false else showSlideshowDialog = true
            },
            onOpenTags = {
                showActionSheet = false
                showRateAndTagSheet = true
            },
            onDismiss = { showActionSheet = false },
        )
    }

    if (showSlideshowDialog) {
        ViewerSlideshowDialog(
            onStart = { intervalSec, loopEnabled, randomEnabled ->
                ctx.config.slideshowInterval = intervalSec
                ctx.config.loopSlideshow = loopEnabled
                ctx.config.slideshowRandomOrder = randomEnabled
                showSlideshowDialog = false
                slideshowActive = true
            },
            onDismiss = { showSlideshowDialog = false },
        )
    }

    if (showRateAndTagSheet) {
        val editorPath = currentPath
        val isJpeg = editorPath.substringAfterLast('.', "").lowercase() in setOf("jpg", "jpeg")
        RateAndTagSheet(
            batchCount = 1,
            currentRating = currentRating,
            onRate = ::rateCurrent,
            initialTags = editorTags,
            onAddTag = { tag ->
                scope.launch(Dispatchers.IO) {
                    val saved = repo.addTag(editorPath, tag)
                    withContext(Dispatchers.Main) {
                        if (saved) ctx.config.rememberRecentTag(tag)
                        else ctx.toast(R.string.tag_write_failed, Toast.LENGTH_SHORT)
                    }
                }
            },
            onRemoveTag = { tag ->
                scope.launch(Dispatchers.IO) {
                    if (!repo.removeTag(editorPath, tag)) {
                        withContext(Dispatchers.Main) { ctx.toast(R.string.tag_write_failed, Toast.LENGTH_SHORT) }
                    }
                }
            },
            suggestedTags = (quickTags + editorTagSuggestions.map { it.first }).distinct(),
            suggestedTagCounts = editorTagSuggestions.toMap(),
            recentTags = recentTags,
            tagHierarchy = ctx.config.tagHierarchy,
            metadataStorageHint = if (isJpeg) null else stringResource(R.string.metadata_sidecar_hint),
            onSetTagParent = { child, parent ->
                val hierarchy = ctx.config.tagHierarchy.toMutableMap()
                if (parent.isNullOrBlank()) hierarchy.remove(child) else hierarchy[child] = parent
                ctx.config.tagHierarchy = hierarchy
            },
            onDismiss = { showRateAndTagSheet = false },
        )
    }

    if (showFrameGrabber) {
        org.fossify.gallery.compose.screens.framegrabber.FrameGrabberScreen(currentPath) { deletedOriginal ->
            showFrameGrabber = false
            if (deletedOriginal) {
                // The source video was moved to the recycle bin - drop it from the pager exactly like
                // an in-viewer delete so we don't keep showing a now-binned item.
                val idx = pagerState.currentPage
                if (items.size <= 1) onClose() else {
                    items.removeAt(idx)
                    if (idx >= items.size) scope.launch { pagerState.scrollToPage(items.size - 1) }
                }
            }
        }
    }

    if (showVideoTrimmer) {
        VideoTrimmerSheet(
            videoPath = currentPath,
            onDismiss = { showVideoTrimmer = false },
            onTrimSaved = { savedPath ->
                showVideoTrimmer = false
                val insertAt = (pagerState.currentPage + 1).coerceAtMost(items.size)
                items.add(insertAt, savedPath)
                scope.launch { pagerState.animateScrollToPage(insertAt) }
            },
        )
    }

    if (showVideoSettings) {
        ViewerVideoScalingDialog(
            currentMode = videoScalingMode,
            onSelectMode = { videoScalingMode = it },
            onDismiss = { showVideoSettings = false },
        )
    }

    if (showFolderPicker) {
        FolderPickerSheet(
            isMoveOperation = pendingFolderPickerIsMove,
            sourcePaths = listOf(currentPath),
            onDismiss = { showFolderPicker = false },
        )
    }

    if (showRenameDialog) {
        val renamePath = currentPath
        RenameDialog(
            paths = listOf(renamePath),
            onDismiss = { showRenameDialog = false },
            onRenamed = { mapping ->
                val newPath = mapping[renamePath] ?: return@RenameDialog
                val idx = items.indexOf(renamePath)
                if (idx >= 0) items[idx] = newPath
                ctx.config.lastViewedPath = newPath
            },
        )
    }

    infoDialogPath?.let { path ->
        MediaInfoDialog(
            path = path,
            onDismiss = { infoDialogPath = null },
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

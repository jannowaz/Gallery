package org.fossify.gallery.compose.screens.analysis
import androidx.compose.ui.res.stringResource
import org.fossify.gallery.R
import org.fossify.gallery.helpers.formatBytes
import org.fossify.gallery.compose.theme.Radius

import android.net.Uri
import android.view.LayoutInflater
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import org.fossify.gallery.compose.screens.viewer.rememberZoomState
import org.fossify.gallery.compose.screens.viewer.zoomable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.Image
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilledIconButton
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.asImageBitmap
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.launch
import org.fossify.gallery.compose.components.ConfirmDestructive
import org.fossify.gallery.compose.components.EmptyState
import org.fossify.gallery.compose.components.GalleryImage
import org.fossify.gallery.extensions.config
import org.fossify.gallery.models.CompressionReviewItem
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompressionReviewScreen(onBack: () -> Unit) {
    val vm: CompressionReviewViewModel = viewModel()
    val items by vm.items.collectAsStateWithLifecycle()
    var openedIndex by remember { mutableStateOf<Int?>(null) }

    val done = items.filter { it.status == CompressionReviewItem.STATUS_DONE }
    val pending = items.filter { it.status == CompressionReviewItem.STATUS_PENDING }
    val failed = items.filter { it.status == CompressionReviewItem.STATUS_FAILED }

    // Per-folder review: compress everything in one batch, but check the results folder by folder.
    // The chips show how many results are still open per folder ("Ordner · 3"); picking one narrows
    // the list, the compare pager and the keep-all actions to that folder so nothing spills over.
    var folderFilter by remember { mutableStateOf<String?>(null) }
    val doneByFolder = done.groupBy { File(it.originalPath).parent ?: "" }
    val folderKeys = doneByFolder.keys.sorted()
    LaunchedEffect(folderKeys) { if (folderFilter != null && folderFilter !in doneByFolder) folderFilter = null }
    val visibleDone = folderFilter?.let { f -> done.filter { (File(it.originalPath).parent ?: "") == f } } ?: done

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_compression_review), fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
        if (items.isEmpty()) {
            EmptyState(
                icon = Icons.Default.Compress,
                title = stringResource(R.string.compression_review_empty),
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Column(Modifier.fillMaxSize()) {
                if (doneByFolder.size > 1) {
                    LazyRow(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item {
                            FilterChip(
                                selected = folderFilter == null,
                                onClick = { folderFilter = null },
                                label = { Text("${stringResource(R.string.review_all)} · ${done.size}") },
                            )
                        }
                        items(folderKeys, key = { it }) { folder ->
                            FilterChip(
                                selected = folderFilter == folder,
                                onClick = { folderFilter = folder },
                                label = { Text("${File(folder).name} · ${doneByFolder[folder]?.size ?: 0}") },
                            )
                        }
                    }
                }
                var pendingBulkAction by remember { mutableStateOf<String?>(null) }
                if (pendingBulkAction != null) {
                    val isKeepNew = pendingBulkAction == "keep_new"
                    ConfirmDestructive(
                        title = stringResource(if (isKeepNew) R.string.action_keep_all_new else R.string.action_keep_all_original),
                        text = "${visibleDone.size} ${stringResource(if (isKeepNew) R.string.action_keep_all_new else R.string.action_keep_all_original)}?",
                        confirmLabel = stringResource(if (isKeepNew) R.string.action_keep_all_new else R.string.action_keep_all_original),
                        onConfirm = {
                            pendingBulkAction = null
                            if (isKeepNew) vm.keepAllNew(folderFilter) else vm.keepAllOriginal(folderFilter)
                        },
                        onDismiss = { pendingBulkAction = null },
                    )
                }
                if (visibleDone.size > 1) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = { pendingBulkAction = "keep_new" },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        ) {
                            Text(stringResource(R.string.action_keep_all_new))
                        }
                        OutlinedButton(
                            onClick = { pendingBulkAction = "keep_original" },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        ) {
                            Text(stringResource(R.string.action_keep_all_original))
                        }
                    }
                }
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(8.dp)) {
                    items(pending, key = { "p${it.id}" }) { item -> PendingCard(item) }
                    items(failed, key = { "f${it.id}" }) { item -> FailedCard(item, onDismiss = { vm.dismissFailed(item) }) }
                    itemsIndexed(visibleDone, key = { _, it -> "d${it.id}" }) { index, item -> DoneCard(item, onClick = { openedIndex = index }) }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
        org.fossify.gallery.compose.components.UndoBar(Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
        }
    }

    openedIndex?.let { idx ->
        CompareViewerPager(
            doneItems = visibleDone,
            initialIndex = idx,
            onKeepOriginal = { vm.keepOriginal(it) },
            onKeepNew = { vm.keepNew(it) },
            onDismiss = { openedIndex = null },
        )
    }
}

@Composable
private fun PendingCard(item: CompressionReviewItem) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), shape = RoundedCornerShape(Radius.md)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(File(item.originalPath).name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.compression_pending), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun FailedCard(item: CompressionReviewItem, onDismiss: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), shape = RoundedCornerShape(Radius.md), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(File(item.originalPath).name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.errorMessage ?: stringResource(R.string.compression_failed), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_dismiss)) }
        }
    }
}

@Composable
private fun DoneCard(item: CompressionReviewItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(Radius.md),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(Radius.sm))) {
                GalleryImage(path = item.originalPath, contentDescription = null, modifier = Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(File(item.originalPath).name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val percent = if (item.originalSize > 0) (100 - (item.resultSize * 100 / item.originalSize)).toInt() else 0
                Text(
                    stringResource(R.string.compression_saved_percent, formatBytes(item.originalSize), formatBytes(item.resultSize), percent),
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}

/** Swipeable review across every compressed-and-done item, starting at [initialIndex] - so a
 * batch-compress-all run (see StorageAnalysisViewModel.compressAll) can be judged one after another
 * without closing and re-opening the list for each file. [doneItems] is the live (reactive) list:
 * keepOriginal/keepNew delete the decided item from the DB, which removes it from this list on the
 * next recomposition - the item that used to sit at the next index slides into the current page
 * automatically, which reads as "swipe (or decide) to advance" without any extra bookkeeping.
 * The Keep Original/Keep New actions are bound to whichever item [pagerState] currently shows,
 * not the item this pager opened on. */
@Composable
private fun CompareViewerPager(
    doneItems: List<CompressionReviewItem>,
    initialIndex: Int,
    onKeepOriginal: (CompressionReviewItem) -> Unit,
    onKeepNew: (CompressionReviewItem) -> Unit,
    onDismiss: () -> Unit,
) {
    if (doneItems.isEmpty()) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    // System back closes only this compare overlay (back to the still-pending review list) instead of
    // propagating out of the whole review screen and stranding the results.
    val compareBackState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
    NavigationBackHandler(
        state = compareBackState,
        isBackEnabled = true,
        onBackCompleted = onDismiss,
    )
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(
        initialPage = initialIndex.coerceIn(0, doneItems.lastIndex),
    ) { doneItems.size }
    val pagerScope = rememberCoroutineScope()

    // A decision removes its item from doneItems - if that was the last page, the now-out-of-range
    // page index needs to be pulled back onto the new last item instead of leaving the pager stuck
    // past the end (or crashing on an index that no longer exists).
    LaunchedEffect(doneItems.size) {
        if (doneItems.isEmpty()) { onDismiss(); return@LaunchedEffect }
        if (pagerState.currentPage > doneItems.lastIndex) pagerState.scrollToPage(doneItems.lastIndex)
    }

    val current = doneItems.getOrNull(pagerState.currentPage.coerceIn(0, doneItems.lastIndex))

    // This overlay is a plain full-screen Box, not a Scaffold, so unlike every other screen in the
    // app it doesn't get automatic system-bar inset padding - without statusBarsPadding() here, the
    // top row draws directly under the status bar's own touch-interception zone (confirmed live: on
    // a real device/emulator, taps on Close/Previous/Next in that strip never reached the button at
    // all, even though they were visually in the right place - the status bar window swallows the
    // touch first since it sits above the app in z-order).
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, stringResource(R.string.cd_close)) }
                Spacer(Modifier.weight(1f))
                // WipeCompareImage's own pinch/pan and wipe-divider drag detectors win the
                // horizontal-drag arbitration against HorizontalPager's swipe-to-change-page inside
                // the image area (confirmed live: swiping anywhere over the compare view never
                // changed pages) - these buttons are the reliable way to move between items
                // regardless of that gesture conflict; swipe still works wherever the image's own
                // gestures happen to leave it free (e.g. a video page before playback starts).
                IconButton(
                    onClick = { pagerScope.launch { pagerState.animateScrollToPage((pagerState.currentPage - 1).coerceAtLeast(0)) } },
                    enabled = pagerState.currentPage > 0,
                ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.compare_previous)) }
                Text(
                    stringResource(R.string.compare_position, pagerState.currentPage + 1, doneItems.size),
                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconButton(
                    onClick = { pagerScope.launch { pagerState.animateScrollToPage((pagerState.currentPage + 1).coerceAtMost(doneItems.lastIndex)) } },
                    enabled = pagerState.currentPage < doneItems.lastIndex,
                ) { Icon(Icons.AutoMirrored.Filled.ArrowForward, stringResource(R.string.compare_next)) }
                Spacer(Modifier.weight(1f))
            }
            androidx.compose.foundation.pager.HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
                doneItems.getOrNull(page)?.let { CompareItemContent(it) }
            }
            if (current != null) {
                val percent = if (current.originalSize > 0) (100 - (current.resultSize * 100 / current.originalSize)).toInt() else 0
                Text(
                    stringResource(R.string.compression_saved_percent, formatBytes(current.originalSize), formatBytes(current.resultSize), percent),
                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { onKeepOriginal(current) },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) {
                        Text(stringResource(R.string.action_keep_original))
                    }
                    Button(
                        onClick = { onKeepNew(current) },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) {
                        Text(stringResource(R.string.action_keep_new))
                    }
                }
            }
        }
    }
}

/** One page's compare view - wipe+zoom for images (see WipeCompareImage), before/after toggle for
 * videos. Owns its own showAfter state so paging to a different item doesn't carry over which side
 * the previous item was showing. */
@Composable
private fun CompareItemContent(item: CompressionReviewItem) {
    var showAfter by remember(item.id) { mutableStateOf(false) }
    val isVideo = item.mediaType == 2
    // A GIF (mediaType image) can be optimized into a *video* (animated GIF → MP4). The wipe-compare
    // renders both sides as still images, so the animated "after" MP4 looked frozen even though the
    // file is a real video - use a before/after toggle that plays it instead, so the preserved motion
    // is actually visible.
    val afterIsVideo = item.tempResultPath.substringAfterLast('.', "").lowercase() in org.fossify.gallery.helpers.VIDEO_EXTENSIONS
    val gifToVideo = !isVideo && afterIsVideo
    // For GIF→video, default to the side-by-side split (both animations at once); the before/after
    // toggle stays available.
    var splitMode by remember(item.id) { mutableStateOf(true) }
    Column(Modifier.fillMaxSize()) {
        if (isVideo || gifToVideo) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!(gifToVideo && splitMode)) {
                    FilterChip(selected = !showAfter, onClick = { showAfter = false }, label = { Text(stringResource(R.string.compare_before)) })
                    FilterChip(selected = showAfter, onClick = { showAfter = true }, label = { Text(stringResource(R.string.compare_after)) })
                }
                if (gifToVideo) {
                    Spacer(Modifier.weight(1f))
                    FilterChip(selected = splitMode, onClick = { splitMode = !splitMode }, label = { Text(stringResource(R.string.compare_split)) })
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                isVideo -> ComparePlayer(pathA = item.originalPath, pathB = item.tempResultPath, showB = showAfter, modifier = Modifier.fillMaxSize())
                gifToVideo && splitMode -> GifToVideoSplit(beforeGifPath = item.originalPath, afterVideoPath = item.tempResultPath, modifier = Modifier.fillMaxSize())
                gifToVideo -> GifToVideoCompare(beforeGifPath = item.originalPath, afterVideoPath = item.tempResultPath, showAfter = showAfter, modifier = Modifier.fillMaxSize())
                else -> WipeCompareImage(beforePath = item.originalPath, afterPath = item.tempResultPath, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

/** Side-by-side split for a GIF optimized to a video: the animated GIF and the looping MP4 play at
 * once, so both animations are visible together (a wipe overlay can't clip the video's Surface the
 * way it clips two Compose-drawn images, so the two versions sit next to each other instead). */
@Composable
internal fun GifToVideoSplit(beforeGifPath: String, afterVideoPath: String, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    // One shared zoom state drives both halves, so pinch/pan/double-tap zooms the GIF and the video
    // into the exact same region at once - the point of the split is comparing detail side by side.
    val zoom = rememberZoomState()
    val zoomLayer = Modifier.graphicsLayer {
        scaleX = zoom.scale; scaleY = zoom.scale
        translationX = zoom.offset.x; translationY = zoom.offset.y
    }
    // Play/pause both at once, frozen on the same moment: pausing captures the video's position and
    // renders the GIF's frame at that exact time, so both sides show the same instant for a fair
    // still-frame quality comparison (they otherwise animate independently and never line up).
    var playing by remember { mutableStateOf(true) }
    var freezeMs by remember { mutableStateOf(0L) }

    // One player whose media item is swapped in a LaunchedEffect keyed on the path (not a path-less
    // remember): the review pager reuses the same composition slot for the next item after a keep
    // decision, so a remember-without-key would leave this "after" stuck on the previous item's video.
    val player = remember { ExoPlayer.Builder(ctx).build().apply { repeatMode = Player.REPEAT_MODE_ONE; volume = 0f } }
    val playerView = remember {
        (LayoutInflater.from(ctx).inflate(R.layout.compose_video_player_view, null) as androidx.media3.ui.PlayerView).apply { useController = false }
    }
    LaunchedEffect(player) { playerView.player = player }
    LaunchedEffect(afterVideoPath) { player.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(afterVideoPath)))); player.prepare() }
    LaunchedEffect(playing) { player.playWhenReady = playing }
    DisposableEffect(Unit) { onDispose { player.release() } }

    Box(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxHeight().clipToBounds()) {
                GifSide(beforeGifPath, playing, freezeMs, Modifier.fillMaxSize().then(zoomLayer))
                SplitLabel(stringResource(R.string.compare_before), Modifier.align(Alignment.TopStart))
            }
            Box(Modifier.width(1.dp).fillMaxHeight().background(Color.White.copy(alpha = 0.5f)))
            Box(Modifier.weight(1f).fillMaxHeight().clipToBounds()) {
                AndroidView(factory = { playerView }, modifier = Modifier.fillMaxSize().then(zoomLayer))
                SplitLabel(stringResource(R.string.compare_after), Modifier.align(Alignment.TopEnd))
            }
        }
        // Full-area gesture layer feeding the shared zoom - one pinch/pan drives both halves at once.
        Box(Modifier.fillMaxSize().zoomable(zoom, onSingleTap = {}))
        PlayPauseButton(
            playing = playing,
            onToggle = { if (playing) { freezeMs = player.currentPosition.coerceAtLeast(0L); playing = false } else playing = true },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
        )
    }
}

/** The "before" GIF side: animates live while [playing]; when paused, shows the GIF's frame at
 * [freezeMs] (the moment the "after" video was paused at) so both sides freeze on the same instant. */
@Composable
private fun GifSide(path: String, playing: Boolean, freezeMs: Long, modifier: Modifier) {
    val ctx = LocalContext.current
    if (playing) {
        AsyncImage(model = ImageRequest.Builder(ctx).data(File(path)).build(), contentDescription = stringResource(R.string.compare_before), contentScale = ContentScale.Fit, modifier = modifier)
    } else {
        val frame by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, path, freezeMs) {
            value = withContext(Dispatchers.IO) { extractGifFrame(path, freezeMs)?.asImageBitmap() }
        }
        val f = frame
        if (f != null) Image(bitmap = f, contentDescription = stringResource(R.string.compare_before), contentScale = ContentScale.Fit, modifier = modifier)
        else AsyncImage(model = ImageRequest.Builder(ctx).data(File(path)).build(), contentDescription = stringResource(R.string.compare_before), contentScale = ContentScale.Fit, modifier = modifier)
    }
}

@Suppress("DEPRECATION")
private fun extractGifFrame(path: String, timeMs: Long): android.graphics.Bitmap? {
    val movie = android.graphics.Movie.decodeFile(path) ?: return null
    val w = movie.width().coerceAtLeast(1); val h = movie.height().coerceAtLeast(1)
    val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    canvas.drawColor(android.graphics.Color.BLACK)
    val dur = movie.duration()
    movie.setTime(if (dur > 0) (timeMs % dur).toInt() else 0)
    movie.draw(canvas, 0f, 0f)
    return bmp
}

@Composable
private fun PlayPauseButton(playing: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    FilledIconButton(onClick = onToggle, modifier = modifier) {
        Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, stringResource(R.string.cd_play_pause))
    }
}

@Composable
private fun SplitLabel(text: String, modifier: Modifier = Modifier) {
    androidx.compose.material3.Surface(modifier = modifier.padding(6.dp), color = Color.Black.copy(alpha = 0.55f), shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)) {
        Text(text, color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

/** Compare for an animated GIF that was optimized to a video: the "before" is the animated GIF
 * (Coil animates it), the "after" is the resulting MP4 played on a loop (muted, auto-playing) - so
 * the user can confirm the motion survived the conversion rather than seeing a frozen still. */
@Composable
internal fun GifToVideoCompare(beforeGifPath: String, afterVideoPath: String, showAfter: Boolean, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    // Player kept alive across the before/after toggle (not created inside the branch) so pausing on
    // "after" captures a position that the "before" GIF can then be frozen to - flip to before and it
    // shows the same instant. Media item swapped via LaunchedEffect(path) so a reused pager slot
    // follows the current item instead of replaying the previous one (see GifToVideoSplit).
    var playing by remember { mutableStateOf(true) }
    var freezeMs by remember { mutableStateOf(0L) }
    val player = remember { ExoPlayer.Builder(ctx).build().apply { repeatMode = Player.REPEAT_MODE_ONE; volume = 0f } }
    val playerView = remember {
        (LayoutInflater.from(ctx).inflate(R.layout.compose_video_player_view, null) as androidx.media3.ui.PlayerView).apply { useController = false }
    }
    LaunchedEffect(player) { playerView.player = player }
    LaunchedEffect(afterVideoPath) { player.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(afterVideoPath)))); player.prepare() }
    LaunchedEffect(playing) { player.playWhenReady = playing }
    DisposableEffect(Unit) { onDispose { player.release() } }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (showAfter) {
            AndroidView(factory = { playerView }, modifier = Modifier.fillMaxSize())
        } else {
            GifSide(beforeGifPath, playing, freezeMs, Modifier.fillMaxSize())
        }
        PlayPauseButton(
            playing = playing,
            onToggle = { if (playing) { freezeMs = player.currentPosition.coerceAtLeast(0L); playing = false } else playing = true },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
        )
    }
}

/** Minimal before/after video player: swaps the media item on a single ExoPlayer instance and
 * re-seeks to the same position, so scrubbing to a spot and toggling "Nachher" compares that exact
 * moment instead of resetting to the start. PlayerView must be XML-inflated (compose_video_player_view.xml),
 * not constructed via PlayerView(ctx) directly - see VideoPage.kt's identical comment for why a
 * plain construction silently shows no video. */
@Composable
internal fun ComparePlayer(pathA: String, pathB: String, showB: Boolean, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val player = remember(pathA, pathB) {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(File(if (showB) pathB else pathA))))
            prepare()
            playWhenReady = ctx.config.autoplayVideos
        }
    }
    val playerView = remember {
        (LayoutInflater.from(ctx).inflate(R.layout.compose_video_player_view, null) as androidx.media3.ui.PlayerView)
            .apply { useController = true }
    }
    LaunchedEffect(player) { playerView.player = player }
    LaunchedEffect(player, showB) {
        val targetUri = Uri.fromFile(File(if (showB) pathB else pathA))
        if (player.currentMediaItem?.localConfiguration?.uri != targetUri) {
            val pos = player.currentPosition
            val wasPlaying = player.playWhenReady
            player.setMediaItem(MediaItem.fromUri(targetUri))
            player.prepare()
            player.seekTo(pos)
            player.playWhenReady = wasPlaying
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    AndroidView(factory = { playerView }, modifier = modifier)
}

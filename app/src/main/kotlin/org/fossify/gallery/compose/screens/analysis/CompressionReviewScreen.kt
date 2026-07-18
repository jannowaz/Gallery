package org.fossify.gallery.compose.screens.analysis
import androidx.compose.ui.res.stringResource
import org.fossify.gallery.R
import org.fossify.gallery.helpers.formatBytes
import org.fossify.gallery.compose.theme.Radius

import android.net.Uri
import android.view.LayoutInflater
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.launch
import org.fossify.gallery.compose.components.GalleryImage
import org.fossify.gallery.models.CompressionReviewItem
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompressionReviewScreen(onBack: () -> Unit) {
    val vm: CompressionReviewViewModel = viewModel()
    val items by vm.items.collectAsState()
    var openedIndex by remember { mutableStateOf<Int?>(null) }

    val done = items.filter { it.status == CompressionReviewItem.STATUS_DONE }
    val pending = items.filter { it.status == CompressionReviewItem.STATUS_PENDING }
    val failed = items.filter { it.status == CompressionReviewItem.STATUS_FAILED }

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
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.compression_review_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                if (done.size > 1) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { vm.keepAllNew() }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_keep_all_new)) }
                        OutlinedButton(onClick = { vm.keepAllOriginal() }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_keep_all_original)) }
                    }
                }
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(8.dp)) {
                    items(pending, key = { "p${it.id}" }) { item -> PendingCard(item) }
                    items(failed, key = { "f${it.id}" }) { item -> FailedCard(item, onDismiss = { vm.dismissFailed(item) }) }
                    itemsIndexed(done, key = { _, it -> "d${it.id}" }) { index, item -> DoneCard(item, onClick = { openedIndex = index }) }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
        org.fossify.gallery.compose.components.UndoBar(Modifier.align(Alignment.BottomCenter))
        }
    }

    openedIndex?.let { idx ->
        CompareViewerPager(
            doneItems = done,
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
                    style = MaterialTheme.typography.labelSmall,
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
    if (doneItems.isEmpty()) { onDismiss(); return }
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
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
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
                    style = MaterialTheme.typography.labelMedium,
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
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onKeepOriginal(current) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_keep_original)) }
                    Button(onClick = { onKeepNew(current) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_keep_new)) }
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
    Column(Modifier.fillMaxSize()) {
        if (isVideo) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !showAfter, onClick = { showAfter = false }, label = { Text(stringResource(R.string.compare_before)) })
                FilterChip(selected = showAfter, onClick = { showAfter = true }, label = { Text(stringResource(R.string.compare_after)) })
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (isVideo) {
                ComparePlayer(pathA = item.originalPath, pathB = item.tempResultPath, showB = showAfter, modifier = Modifier.fillMaxSize())
            } else {
                WipeCompareImage(beforePath = item.originalPath, afterPath = item.tempResultPath, modifier = Modifier.fillMaxSize())
            }
        }
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
    val player = remember {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(File(pathA))))
            prepare()
            playWhenReady = false
        }
    }
    val playerView = remember {
        (LayoutInflater.from(ctx).inflate(R.layout.compose_video_player_view, null) as androidx.media3.ui.PlayerView).apply { useController = true }
    }
    LaunchedEffect(player) { playerView.player = player }
    LaunchedEffect(showB) {
        val pos = player.currentPosition
        val wasPlaying = player.isPlaying
        player.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(if (showB) pathB else pathA))))
        player.prepare()
        player.seekTo(pos)
        player.playWhenReady = wasPlaying
    }
    DisposableEffect(Unit) { onDispose { player.release() } }
    AndroidView(factory = { playerView }, modifier = modifier)
}


package org.fossify.gallery.compose.screens.analysis
import androidx.compose.ui.res.stringResource
import org.fossify.gallery.R
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import org.fossify.gallery.compose.components.GalleryImage
import org.fossify.gallery.models.CompressionReviewItem
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompressionReviewScreen(onBack: () -> Unit) {
    val vm: CompressionReviewViewModel = viewModel()
    val items by vm.items.collectAsState()
    var openedItem by remember { mutableStateOf<CompressionReviewItem?>(null) }

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
                    items(done, key = { "d${it.id}" }) { item -> DoneCard(item, onClick = { openedItem = item }) }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
        org.fossify.gallery.compose.components.UndoBar(Modifier.align(Alignment.BottomCenter))
        }
    }

    openedItem?.let { item ->
        CompareDialog(
            item = item,
            onKeepOriginal = { vm.keepOriginal(item); openedItem = null },
            onKeepNew = { vm.keepNew(item); openedItem = null },
            onDismiss = { openedItem = null },
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

@Composable
private fun CompareDialog(item: CompressionReviewItem, onKeepOriginal: () -> Unit, onKeepNew: () -> Unit, onDismiss: () -> Unit) {
    var showAfter by remember(item.id) { mutableStateOf(false) }
    val isVideo = item.mediaType == 2

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, stringResource(R.string.cd_close)) }
                Spacer(Modifier.width(4.dp))
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !showAfter, onClick = { showAfter = false }, label = { Text(stringResource(R.string.compare_before)) })
                    FilterChip(selected = showAfter, onClick = { showAfter = true }, label = { Text(stringResource(R.string.compare_after)) })
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (isVideo) {
                    ComparePlayer(pathA = item.originalPath, pathB = item.tempResultPath, showB = showAfter, modifier = Modifier.fillMaxSize())
                } else {
                    GalleryImage(
                        path = if (showAfter) item.tempResultPath else item.originalPath,
                        contentDescription = stringResource(if (showAfter) R.string.compare_after else R.string.compare_before),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                        thumbnailSize = 1536,
                        backgroundColor = MaterialTheme.colorScheme.background,
                    )
                }
            }
            val percent = if (item.originalSize > 0) (100 - (item.resultSize * 100 / item.originalSize)).toInt() else 0
            Text(
                stringResource(R.string.compression_saved_percent, formatBytes(item.originalSize), formatBytes(item.resultSize), percent),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onKeepOriginal, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_keep_original)) }
                Button(onClick = onKeepNew, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_keep_new)) }
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

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> "${"%.1f".format(bytes / 1_000_000_000.0)} GB"
    bytes >= 1_000_000 -> "${"%.1f".format(bytes / 1_000_000.0)} MB"
    bytes >= 1_000 -> "${bytes / 1_000} KB"
    else -> "$bytes B"
}

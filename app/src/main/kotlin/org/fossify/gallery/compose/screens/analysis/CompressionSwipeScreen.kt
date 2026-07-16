package org.fossify.gallery.compose.screens.analysis

import android.net.Uri
import android.view.LayoutInflater
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.launch
import org.fossify.gallery.R
import org.fossify.gallery.compose.components.GalleryImage
import org.fossify.gallery.compose.screens.viewer.rememberZoomState
import org.fossify.gallery.compose.screens.viewer.zoomable
import org.fossify.gallery.compose.theme.Radius
import java.io.File

/**
 * "Tinder mode" for the storage analysis: one flagged medium at a time, full-screen with its
 * size/bitrate/savings facts. Swipe right (or button) probe-compresses it, swipe left skips.
 * After compression the same gesture decides the compare: right replaces the original (recycle
 * bin), left keeps it and discards the compressed copy.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompressionSwipeScreen(onBack: () -> Unit) {
    val vm: CompressionSwipeViewModel = viewModel()
    val phase by vm.phase.collectAsState()
    val stats by vm.stats.collectAsState()
    val position by vm.position.collectAsState()
    val error by vm.error.collectAsState()
    val ctx = LocalContext.current

    LaunchedEffect(error) {
        error?.let { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show(); vm.clearError() }
    }

    // Mid-session exits throw away the remaining queue position (and a pending compare) - require
    // a deliberate double back while the session is still running.
    val guardedBack = org.fossify.gallery.compose.util.rememberDoubleBackGuard(
        enabled = phase !is SwipePhase.Finished && vm.total > 0,
        onExit = onBack,
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val counter = if (phase is SwipePhase.Finished) "" else "  $position/${vm.total}"
                    Text(stringResource(R.string.swipe_review_title) + counter, fontWeight = FontWeight.Bold)
                },
                navigationIcon = { IconButton(onClick = guardedBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val p = phase) {
                is SwipePhase.Triage -> key(p.item.path) {
                    SwipeDecisionBox(
                        leftLabel = stringResource(R.string.swipe_skip),
                        rightLabel = stringResource(R.string.swipe_convert),
                        onLeft = vm::skip,
                        onRight = vm::convert,
                    ) {
                        TriageContent(p.item, onSkip = vm::skip, onConvert = vm::convert)
                    }
                }
                is SwipePhase.Converting -> key(p.item.path) { ConvertingContent(p.item) }
                is SwipePhase.Compare -> key(p.tempPath) {
                    SwipeDecisionBox(
                        leftLabel = stringResource(R.string.swipe_keep_original),
                        rightLabel = stringResource(R.string.swipe_keep_new),
                        onLeft = vm::keepOriginal,
                        onRight = vm::keepNew,
                    ) {
                        CompareContent(p, onKeepOriginal = vm::keepOriginal, onKeepNew = vm::keepNew)
                    }
                }
                SwipePhase.Finished -> FinishedContent(stats, onBack)
            }
            org.fossify.gallery.compose.components.UndoBar(Modifier.align(Alignment.BottomCenter))
        }
    }
}

/** Tinder-style horizontal swipe wrapper: drags [content] with slight rotation, fades in the
 * corner badge for whichever decision the drag is approaching, and fires it once past the
 * threshold. A single-finger drag reaches this even over the zoomable image (which only consumes
 * when zoomed/pinching) - buttons inside [content] stay the accessible/precise alternative. */
@Composable
private fun SwipeDecisionBox(
    leftLabel: String,
    rightLabel: String,
    onLeft: () -> Unit,
    onRight: () -> Unit,
    content: @Composable () -> Unit,
) {
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val thresholdPx = with(LocalDensity.current) { 110.dp.toPx() }
    var decided by remember { mutableStateOf(false) }

    Box(
        Modifier.fillMaxSize().pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragEnd = {
                    if (decided) return@detectHorizontalDragGestures
                    val v = offsetX.value
                    scope.launch {
                        when {
                            v > thresholdPx -> { decided = true; offsetX.animateTo(size.width * 1.2f, tween(180)); onRight() }
                            v < -thresholdPx -> { decided = true; offsetX.animateTo(-size.width * 1.2f, tween(180)); onLeft() }
                            else -> offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMedium))
                        }
                    }
                },
                onDragCancel = { scope.launch { offsetX.animateTo(0f) } },
                onHorizontalDrag = { change, delta ->
                    if (!decided) {
                        change.consume()
                        scope.launch { offsetX.snapTo(offsetX.value + delta) }
                    }
                },
            )
        }
    ) {
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                translationX = offsetX.value
                rotationZ = (offsetX.value / 60f).coerceIn(-5f, 5f)
            }
        ) { content() }

        SwipeBadge(
            text = rightLabel,
            container = MaterialTheme.colorScheme.primary,
            modifier = Modifier.align(Alignment.TopStart).padding(24.dp)
                .graphicsLayer { alpha = (offsetX.value / thresholdPx).coerceIn(0f, 1f); rotationZ = -8f },
        )
        SwipeBadge(
            text = leftLabel,
            container = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.align(Alignment.TopEnd).padding(24.dp)
                .graphicsLayer { alpha = (-offsetX.value / thresholdPx).coerceIn(0f, 1f); rotationZ = 8f },
        )
    }
}

@Composable
private fun SwipeBadge(text: String, container: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(Radius.md), color = container) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = androidx.compose.material3.contentColorFor(container),
        )
    }
}

@Composable
private fun TriageContent(item: AnalysisResult, onSkip: () -> Unit, onConvert: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (item.mediaType == 2) {
                SinglePlayer(item.path, Modifier.fillMaxSize())
            } else {
                GalleryImage(
                    path = item.path,
                    contentDescription = item.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    thumbnailSize = 1536,
                    backgroundColor = MaterialTheme.colorScheme.background,
                )
            }
        }
        InfoPanel(item)
        ActionRow(leftText = stringResource(R.string.swipe_skip), rightText = stringResource(R.string.swipe_convert), onLeft = onSkip, onRight = onConvert)
    }
}

@Composable
private fun InfoPanel(item: AnalysisResult) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(Radius.md),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(item.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val facts = buildList {
                add(formatBytes(item.fileSize))
                add("${item.width}×${item.height}")
                if (item.mediaType == 2) {
                    if (item.bitrateKbps > 0) add("%.1f Mbps".format(item.bitrateKbps / 1000.0))
                    if (item.durationMs > 0) add("%d:%02d min".format(item.durationMs / 60000, (item.durationMs / 1000) % 60))
                } else {
                    item.imageFormat?.let { add(it.uppercase()) }
                    if (item.bpp > 0f) add("%.2f BPP".format(item.bpp))
                }
            }
            Text(facts.joinToString("  ·  "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
            if (item.wastedBytes > 0) {
                Text(
                    stringResource(R.string.swipe_savings_estimate, formatBytes(item.wastedBytes)),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            item.reasons.take(2).forEach {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ConvertingContent(item: AnalysisResult) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            GalleryImage(
                path = item.path,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.35f },
                contentScale = ContentScale.Fit,
                thumbnailSize = 1024,
                backgroundColor = MaterialTheme.colorScheme.background,
            )
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.swipe_converting), style = MaterialTheme.typography.titleSmall)
                if (item.mediaType == 2) {
                    Text(stringResource(R.string.swipe_converting_video_hint), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun CompareContent(compare: SwipePhase.Compare, onKeepOriginal: () -> Unit, onKeepNew: () -> Unit) {
    // Start on "after": the question being answered is whether the compressed quality is
    // acceptable. A single tap on the image toggles sides with zoom/pan preserved, so the exact
    // same crop can be pixel-compared back and forth.
    var showAfter by remember { mutableStateOf(true) }
    val zoom = rememberZoomState()
    val isVideo = compare.item.mediaType == 2

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(selected = !showAfter, onClick = { showAfter = false }, label = { Text(stringResource(R.string.compare_before) + "  " + formatBytes(compare.item.fileSize)) })
            FilterChip(selected = showAfter, onClick = { showAfter = true }, label = { Text(stringResource(R.string.compare_after) + "  " + formatBytes(compare.newSize)) })
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (isVideo) {
                ComparePlayer(pathA = compare.item.path, pathB = compare.tempPath, showB = showAfter, modifier = Modifier.fillMaxSize())
            } else {
                Box(Modifier.fillMaxSize().zoomable(zoom, onSingleTap = { showAfter = !showAfter })) {
                    GalleryImage(
                        path = if (showAfter) compare.tempPath else compare.item.path,
                        contentDescription = stringResource(if (showAfter) R.string.compare_after else R.string.compare_before),
                        modifier = Modifier.fillMaxSize().graphicsLayer {
                            scaleX = zoom.scale; scaleY = zoom.scale
                            translationX = zoom.offset.x; translationY = zoom.offset.y
                        },
                        contentScale = ContentScale.Fit,
                        thumbnailSize = 2560,
                        backgroundColor = MaterialTheme.colorScheme.background,
                    )
                }
                if (!zoom.isZoomed) {
                    Text(
                        stringResource(R.string.swipe_tap_to_compare),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(4.dp),
                    )
                }
            }
        }
        val percent = if (compare.item.fileSize > 0) (100 - (compare.newSize * 100 / compare.item.fileSize)).toInt() else 0
        Text(
            stringResource(R.string.compression_saved_percent, formatBytes(compare.item.fileSize), formatBytes(compare.newSize), percent) +
                "  ·  " + stringResource(R.string.swipe_original_recoverable),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        ActionRow(leftText = stringResource(R.string.swipe_keep_original), rightText = stringResource(R.string.swipe_keep_new), onLeft = onKeepOriginal, onRight = onKeepNew)
    }
}

@Composable
private fun FinishedContent(stats: SwipeStats, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.swipe_done_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.swipe_done_summary, stats.converted, stats.skipped, stats.failed),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (stats.savedBytes > 0) {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.swipe_done_saved, formatBytes(stats.savedBytes)),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.tertiary,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = onBack) { Text(stringResource(org.fossify.commons.R.string.ok)) }
    }
}

@Composable
private fun ActionRow(leftText: String, rightText: String, onLeft: () -> Unit, onRight: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(onClick = onLeft, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(leftText) }
        Button(onClick = onRight, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(rightText) }
    }
}

/** Single-source triage player, paused at the first frame with the standard controller - the same
 * XML-inflated PlayerView pattern as [ComparePlayer] (plain PlayerView(ctx) shows no video). */
@Composable
private fun SinglePlayer(path: String, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val player = remember {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(File(path))))
            prepare()
            playWhenReady = false
        }
    }
    val playerView = remember {
        (LayoutInflater.from(ctx).inflate(R.layout.compose_video_player_view, null) as androidx.media3.ui.PlayerView).apply { useController = true }
    }
    LaunchedEffect(player) { playerView.player = player }
    DisposableEffect(Unit) { onDispose { player.release() } }
    AndroidView(factory = { playerView }, modifier = modifier)
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> "${"%.1f".format(bytes / 1_000_000_000.0)} GB"
    bytes >= 1_000_000 -> "${"%.1f".format(bytes / 1_000_000.0)} MB"
    bytes >= 1_000 -> "${bytes / 1_000} KB"
    else -> "$bytes B"
}

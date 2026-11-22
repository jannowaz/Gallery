package org.fossify.gallery.compose.screens.framegrabber

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.DisposableEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.commons.extensions.toast
import org.fossify.gallery.R
import org.fossify.gallery.compose.theme.BlurRadius
import org.fossify.gallery.compose.theme.LocalMediaRepository
import org.fossify.gallery.compose.theme.RatingStarColor
import org.fossify.gallery.compose.util.BlurState
import org.fossify.gallery.compose.util.privacyBlur
import org.fossify.gallery.helpers.RefreshBus
import org.fossify.gallery.helpers.UndoAction
import org.fossify.gallery.helpers.UndoManager
import org.fossify.gallery.helpers.UndoType
import org.fossify.gallery.helpers.VideoFrameExtractor
import org.fossify.gallery.helpers.withRecycleBinDeletePasswordCheck

/**
 * "Grab frames" from a video: samples candidate frames, marks/ pre-selects the sharpest, lets the
 * user pick which to keep, and saves them as full-resolution JPEGs. Optionally moves the source
 * video into the recycle bin afterwards. Rendered as a full-screen overlay from the Viewer.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun FrameGrabberScreen(videoPath: String, onDismiss: (deletedOriginal: Boolean) -> Unit) {
    val ctx = LocalContext.current
    val repo = LocalMediaRepository.current
    val scope = rememberCoroutineScope()

    var candidates by remember(videoPath) { mutableStateOf<List<VideoFrameExtractor.FrameCandidate>>(emptyList()) }
    var recommended by remember(videoPath) { mutableStateOf<Set<Long>>(emptySet()) }
    var selected by remember(videoPath) { mutableStateOf<Set<Long>>(emptySet()) }
    var extractProgress by remember(videoPath) { mutableStateOf<Pair<Int, Int>?>(0 to 1) }
    var saveProgress by remember(videoPath) { mutableStateOf<Pair<Int, Int>?>(null) }
    var onlySharp by remember(videoPath) { mutableStateOf(false) }
    var sortBySharp by remember(videoPath) { mutableStateOf(false) }
    var peekFrame by remember(videoPath) { mutableStateOf<VideoFrameExtractor.FrameCandidate?>(null) }
    var deleteOriginal by remember(videoPath) { mutableStateOf(false) }

    LaunchedEffect(videoPath) {
        val list = VideoFrameExtractor.sampleCandidates(videoPath) { done, total -> extractProgress = done to total }
        val maxSharp = list.maxOfOrNull { it.sharpness } ?: 0.0
        // "Sharp" = within ~55% of the crispest frame found. Blurry clips still get at least the best one.
        val rec = list.filter { it.sharpness >= maxSharp * 0.55 }.map { it.timeMs }.toSet()
            .ifEmpty { list.maxByOrNull { it.sharpness }?.let { setOf(it.timeMs) } ?: emptySet() }
        candidates = list
        recommended = rec
        selected = rec
        extractProgress = null
    }

    // Free the preview bitmaps when the screen leaves.
    DisposableEffect(videoPath) { onDispose { candidates.forEach { runCatching { it.preview.recycle() } } } }

    BackHandler {
        when {
            peekFrame != null -> peekFrame = null
            saveProgress == null -> onDismiss(false)
        }
    }

    val shown = remember(candidates, onlySharp, recommended, sortBySharp) {
        val filtered = if (onlySharp) candidates.filter { it.timeMs in recommended } else candidates
        if (sortBySharp) filtered.sortedByDescending { it.sharpness } else filtered
    }
    val saving = saveProgress != null

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.grab_frames)) },
                    navigationIcon = { IconButton(onClick = { if (!saving) onDismiss(false) }) { Icon(Icons.Default.Close, stringResource(org.fossify.commons.R.string.cancel)) } },
                )
            },
            bottomBar = {
                if (candidates.isNotEmpty()) {
                    Surface(tonalElevation = 3.dp) {
                        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                FilterChip(selected = onlySharp, onClick = { onlySharp = !onlySharp }, label = { Text(stringResource(R.string.grab_frames_only_sharp)) })
                                FilterChip(selected = sortBySharp, onClick = { sortBySharp = !sortBySharp }, label = { Text(stringResource(R.string.grab_frames_sort_sharp)) })
                            }
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { deleteOriginal = !deleteOriginal }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = deleteOriginal, onCheckedChange = { deleteOriginal = it })
                                Text(stringResource(R.string.grab_frames_delete_video), style = MaterialTheme.typography.bodySmall)
                            }
                            Button(
                                onClick = {
                                    val times = selected.toList().sorted()
                                    if (times.isEmpty()) return@Button
                                    scope.launch {
                                        saveProgress = 0 to times.size
                                        val res = VideoFrameExtractor.saveFrames(ctx, videoPath, times) { d, t -> saveProgress = d to t }
                                        saveProgress = null
                                        RefreshBus.trigger()
                                        ctx.toast(
                                            if (res.failed > 0) ctx.getString(R.string.grab_frames_saved_with_failures, res.savedPaths.size, res.failed)
                                            else ctx.resources.getQuantityString(R.plurals.grab_frames_saved, res.savedPaths.size, res.savedPaths.size)
                                        )
                                        if (deleteOriginal && res.savedPaths.isNotEmpty()) {
                                            ctx.withRecycleBinDeletePasswordCheck {
                                                scope.launch {
                                                    withContext(Dispatchers.IO) { repo.moveToRecycleBin(videoPath) }
                                                    UndoManager.push(UndoAction(paths = setOf(videoPath), type = UndoType.DELETE))
                                                    onDismiss(true)
                                                }
                                            }
                                        } else {
                                            onDismiss(false)
                                        }
                                    }
                                },
                                enabled = selected.isNotEmpty() && !saving,
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                            ) { Text(stringResource(R.string.grab_frames_save_count, selected.size)) }
                        }
                    }
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when {
                    extractProgress != null -> {
                        val (d, t) = extractProgress!!
                        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Text(stringResource(R.string.grab_frames_analyzing, d, t), Modifier.padding(top = 12.dp), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    candidates.isEmpty() -> Text(stringResource(R.string.grab_frames_none), Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> LazyVerticalGrid(columns = GridCells.Adaptive(140.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp)) {
                        items(shown, key = { it.timeMs }) { c ->
                            val isSel = c.timeMs in selected
                            val isRec = c.timeMs in recommended
                            Box(
                                Modifier.padding(4.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp))
                                    .combinedClickable(
                                        onClick = { selected = if (isSel) selected - c.timeMs else selected + c.timeMs },
                                        onLongClick = { peekFrame = c },
                                    ),
                            ) {
                                Image(c.preview.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize().privacyBlur(BlurRadius.thumbnail, BlurState.enabled), contentScale = ContentScale.Crop)
                                // Selection tick
                                Icon(
                                    if (isSel) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = if (isSel) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(22.dp),
                                )
                                // Sharp badge
                                if (isRec) Row(Modifier.align(Alignment.TopStart).padding(4.dp).clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(alpha = 0.45f)).padding(horizontal = 4.dp, vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Star, null, tint = RatingStarColor, modifier = Modifier.size(12.dp))
                                }
                                val secs = c.timeMs / 1000
                                Text(
                                    "%d:%02d".format(secs / 60, secs % 60),
                                    color = Color.White, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.align(Alignment.BottomStart).padding(4.dp).clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(alpha = 0.45f)).padding(horizontal = 4.dp, vertical = 1.dp),
                                )
                            }
                        }
                    }
                }
                saveProgress?.let { (d, t) ->
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) {
                        Surface(shape = RoundedCornerShape(12.dp), tonalElevation = 4.dp) {
                            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                LinearProgressIndicator(progress = { if (t > 0) d / t.toFloat() else 0f }, modifier = Modifier.fillMaxWidth())
                                Text(stringResource(R.string.grab_frames_saving, d, t), Modifier.padding(top = 10.dp), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
                // Long-press a frame to peek it large; tap anywhere to dismiss, top-right toggles select.
                peekFrame?.let { pf ->
                    val isSel = pf.timeMs in selected
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.92f))
                            .statusBarsPadding()
                            .navigationBarsPadding()
                            .clickable { peekFrame = null },
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            pf.preview.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).privacyBlur(BlurRadius.viewer, BlurState.enabled),
                            contentScale = ContentScale.Fit,
                        )
                        val secs = pf.timeMs / 1000
                        Text("%d:%02d".format(secs / 60, secs % 60), color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp))
                        IconButton(onClick = { selected = if (isSel) selected - pf.timeMs else selected + pf.timeMs }, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)) {
                            Icon(if (isSel) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, contentDescription = null, tint = if (isSel) MaterialTheme.colorScheme.primary else Color.White, modifier = Modifier.size(32.dp))
                        }
                    }
                }
            }
        }
    }
}

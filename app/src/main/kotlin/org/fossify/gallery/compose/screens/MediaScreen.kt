package org.fossify.gallery.compose.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.fossify.commons.dialogs.PropertiesDialog
import org.fossify.gallery.activities.ComposeVideoPlayerActivity
import org.fossify.gallery.activities.ComposeViewerActivity
import org.fossify.gallery.compose.components.SelectionRow
import org.fossify.gallery.compose.components.StarRatingDialog
import org.fossify.gallery.compose.components.TagInputDialog
import org.fossify.gallery.compose.theme.LocalMediaRepository
import org.fossify.gallery.extensions.mediaDB
import org.fossify.gallery.models.Medium
import org.fossify.gallery.viewmodels.MediaViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MediaScreen(
    modifier: Modifier = Modifier,
    viewSettings: ViewSettings = ViewSettings(),
    ratingFilter: Int = 0,
    tagFilterPaths: Set<String>? = null,
    activeTagName: String? = null,
    onClearFilter: () -> Unit = {},
    mediaOverride: List<Medium>? = null,
) {
    val ctx = LocalContext.current
    val viewModel: MediaViewModel = viewModel()
    val state by viewModel.state.collectAsState()
    val repo = LocalMediaRepository.current
    var selectedPaths by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showSelectionSheet by remember { mutableStateOf(false) }
    var showRatingDialog by remember { mutableStateOf(false) }
    var showTagsDialog by remember { mutableStateOf(false) }
    var ratingPath by remember { mutableStateOf("") }
    var currentRating by remember { mutableIntStateOf(0) }
    val columnCount = viewSettings.columnCount
    val videoExts = setOf("mp4", "mkv", "mov", "3gp", "wmv", "flv", "avi")
    val isGrid = viewSettings.viewType == ViewType.GRID

    var ratedMedia by remember { mutableStateOf<List<Medium>?>(null) }
    LaunchedEffect(ratingFilter) {
        if (ratingFilter > 0) {
            ratedMedia = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                ctx.mediaDB.getByMinRating(ratingFilter)
            }
        } else {
            ratedMedia = null
        }
    }

    val baseMedia = mediaOverride ?: state.allMedia
    val unsortedMedia = when {
        ratingFilter > 0 && ratedMedia != null -> ratedMedia!!
        tagFilterPaths != null -> baseMedia.filter { it.path in tagFilterPaths }
        else -> baseMedia
    }
    val displayMedia = remember(unsortedMedia, viewSettings.sortBy, viewSettings.sortDesc) {
        val sorted = when (viewSettings.sortBy) {
            SortField.NAME -> unsortedMedia.sortedBy { it.name.lowercase() }
            SortField.DATE -> unsortedMedia.sortedBy { it.modified }
            SortField.SIZE -> unsortedMedia.sortedBy { it.size }
            SortField.RATING -> unsortedMedia.sortedBy { it.rating }
        }
        if (viewSettings.sortDesc) sorted.reversed() else sorted
    }
    val hasFilter = ratingFilter > 0 || tagFilterPaths != null
    val cornerShape = if (viewSettings.roundedCorners) RoundedCornerShape(8.dp) else RoundedCornerShape(0.dp)
    val itemSpacing = viewSettings.spacing.dp

    fun openViewer(index: Int) {
        val paths = displayMedia.map { it.path }
        val isVideo = displayMedia.getOrNull(index)?.path?.substringAfterLast('.', "")?.lowercase() in videoExts
        if (isVideo) {
            ctx.startActivity(Intent(ctx, ComposeVideoPlayerActivity::class.java).apply {
                putExtra("VIDEO_PATH", displayMedia[index].path)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } else {
            ctx.startActivity(Intent(ctx, ComposeViewerActivity::class.java).apply {
                putStringArrayListExtra("PATHS", ArrayList(paths))
                putExtra("START_INDEX", index)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }

    val hasSelection = selectedPaths.isNotEmpty()

    Box(modifier = modifier.fillMaxSize()) {
        when {
            state.isLoading && !hasFilter && mediaOverride == null -> {
                LazyVerticalGrid(columns = GridCells.Adaptive(120.dp), contentPadding = PaddingValues(8.dp)) {
                    items(12) { ShimmerBox(Modifier.aspectRatio(1f).clip(RoundedCornerShape(8.dp))) }
                }
            }
            displayMedia.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Search, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                        Spacer(Modifier.height(16.dp))
                        Text(if (hasFilter) "Keine Ergebnisse" else "Keine Medien gefunden", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                        if (hasFilter) {
                            Spacer(Modifier.height(8.dp))
                            Surface(Modifier.clickable { onClearFilter() }, color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(16.dp)) {
                                Text("Filter aufheben", Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                    }
                }
            }
            isGrid -> {
                Column {
                    if (hasFilter) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            val label = if (ratingFilter > 0) "★ $ratingFilter+" else "Tag: $activeTagName"
                            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(16.dp)) {
                                Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                    IconButton(onClick = onClearFilter, modifier = Modifier.size(24.dp)) {
                                        Icon(Icons.Default.Close, "Filter entfernen", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                    }
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            Text("${displayMedia.size} Ergebnisse", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    LazyVerticalGrid(columns = GridCells.Fixed(columnCount), contentPadding = PaddingValues(itemSpacing / 2)) {
                    items(displayMedia.size) { idx ->
                        val m = displayMedia[idx]
                        val file = File(m.path)
                        val isVideo = m.path.substringAfterLast('.', "").lowercase() in videoExts
                        Column(Modifier.padding(itemSpacing / 2)) {
                            Box(Modifier.aspectRatio(1f).combinedClickable(
                                onClick = {
                                    if (hasSelection) selectedPaths = if (m.path in selectedPaths) selectedPaths - m.path else selectedPaths + m.path
                                    else openViewer(idx)
                                },
                                onLongClick = { selectedPaths = selectedPaths + m.path }
                            )) {
                                if (file.exists()) {
                                    if (isVideo) VideoThumbnail(videoPath = m.path, modifier = Modifier.fillMaxSize().clip(cornerShape), contentScale = ContentScale.Crop)
                                    else coil.compose.AsyncImage(model = coil.request.ImageRequest.Builder(ctx).data(android.net.Uri.fromFile(file)).crossfade(true).build(), contentDescription = m.name, modifier = Modifier.fillMaxSize().clip(cornerShape), contentScale = ContentScale.Crop)
                                } else {
                                    Box(Modifier.fillMaxSize().clip(cornerShape).background(MaterialTheme.colorScheme.surfaceVariant))
                                }
                                if (m.path in selectedPaths) {
                                    Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)))
                                    Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(24.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    }
                                }
                                if (hasSelection) {
                                    Box(Modifier.align(Alignment.BottomEnd).padding(4.dp).size(28.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape).clickable { openViewer(idx) }, contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Visibility, "Vorschau", tint = Color.White, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                            if (viewSettings.showFileNames) {
                                Text(m.name, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                            }
                        }
                    }
                    }
                }
            }
            else -> {
                LazyColumn(contentPadding = PaddingValues(4.dp)) {
                    items(displayMedia.size) { idx ->
                        val m = displayMedia[idx]
                        val file = File(m.path)
                        val isVideo = m.path.substringAfterLast('.', "").lowercase() in videoExts
                        Surface(modifier = Modifier.fillMaxWidth().combinedClickable(
                            onClick = {
                                if (hasSelection) selectedPaths = if (m.path in selectedPaths) selectedPaths - m.path else selectedPaths + m.path
                                else openViewer(idx)
                            },
                            onLongClick = { selectedPaths = selectedPaths + m.path }
                        ), color = Color.Transparent) {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (file.exists()) {
                                    Box(Modifier.size(56.dp).clip(RoundedCornerShape(8.dp))) {
                                        if (isVideo) VideoThumbnail(videoPath = m.path, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                        else coil.compose.AsyncImage(model = coil.request.ImageRequest.Builder(ctx).data(android.net.Uri.fromFile(file)).crossfade(true).build(), contentDescription = m.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                    }
                                } else {
                                    Box(Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(m.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(formatFileSize(m.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (hasSelection) {
                                    IconButton(onClick = { openViewer(idx) }, modifier = Modifier.size(36.dp)) {
                                        Icon(Icons.Default.Visibility, "Vorschau", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                    }
                                }
                                if (m.path in selectedPaths) Icon(Icons.Default.Close, "Ausgewählt", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        HorizontalDivider(Modifier.padding(start = 76.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    }
                }
            }
        }

        if (hasSelection) {
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().clickable { showSelectionSheet = true },
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                shadowElevation = 8.dp,
            ) {
                Row(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${selectedPaths.size} ausgewählt", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Icon(Icons.Default.Close, "Auswahl aufheben", Modifier.size(20.dp).clickable { selectedPaths = emptySet() }, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    if (showSelectionSheet) {
        ModalBottomSheet(onDismissRequest = { showSelectionSheet = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false), containerColor = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().heightIn(max = 340.dp).padding(horizontal = 16.dp, vertical = 8.dp).verticalScroll(rememberScrollState())) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text("${selectedPaths.size} ausgewählt", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    IconButton(onClick = { selectedPaths = emptySet(); showSelectionSheet = false }) { Icon(Icons.Default.Close, "Auswahl schließen", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                Spacer(Modifier.height(12.dp))
                SelectionRow(Icons.Default.Share, "Teilen") {
                    val uris = ArrayList(selectedPaths.map { android.net.Uri.fromFile(File(it)) })
                    val shareIntent = if (uris.size == 1) {
                        Intent(Intent.ACTION_SEND).apply { type = "*/*"; putExtra(Intent.EXTRA_STREAM, uris.first()) }
                    } else {
                        Intent(Intent.ACTION_SEND_MULTIPLE).apply { type = "*/*"; putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris) }
                    }
                    ctx.startActivity(Intent.createChooser(shareIntent, "Teilen").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    showSelectionSheet = false; selectedPaths = emptySet()
                }
                SelectionRow(Icons.Default.Delete, "Löschen", tint = MaterialTheme.colorScheme.error) {
                    viewModel.deletePaths(selectedPaths); selectedPaths = emptySet(); showSelectionSheet = false
                }
                SelectionRow(Icons.Default.Info, "Info") { selectedPaths.firstOrNull()?.let { (ctx as? android.app.Activity)?.let { a -> PropertiesDialog(a, it, false) } }; showSelectionSheet = false }
                if (selectedPaths.size == 1) {
                    SelectionRow(Icons.Default.Star, "Bewerten") { ratingPath = selectedPaths.first(); showRatingDialog = true; showSelectionSheet = false }
                    SelectionRow(Icons.Default.Edit, "Tags") { ratingPath = selectedPaths.first(); showTagsDialog = true; showSelectionSheet = false }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showRatingDialog) {
        StarRatingDialog(currentRating = currentRating, onRate = { i ->
            currentRating = i; repo.updateRating(ratingPath, i); showRatingDialog = false
        }, onDismiss = { showRatingDialog = false })
    }
    if (showTagsDialog) {
        TagInputDialog(initialTags = repo.getTags(ratingPath), onSave = { tag ->
            repo.addTag(ratingPath, tag)
            Toast.makeText(ctx, "Tag gespeichert", Toast.LENGTH_SHORT).show()
        }, onDismiss = { showTagsDialog = false })
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024; if (kb < 1024) return "${kb} KB"
    val mb = kb / 1024; if (mb < 1024) return "${mb} MB"
    return "%.1f GB".format(mb / 1024.0)
}

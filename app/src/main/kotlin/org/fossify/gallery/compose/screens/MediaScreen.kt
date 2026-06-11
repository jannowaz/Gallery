package org.fossify.gallery.compose.screens

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
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
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.fossify.commons.dialogs.PropertiesDialog
import org.fossify.commons.extensions.toast
import org.fossify.gallery.activities.ComposeVideoPlayerActivity
import org.fossify.gallery.activities.ComposeViewerActivity
import org.fossify.gallery.compose.components.SelectionRow
import org.fossify.gallery.compose.components.StarRatingDialog
import org.fossify.gallery.compose.components.TagInputDialog
import org.fossify.gallery.compose.screens.FolderPickerSheet
import org.fossify.gallery.compose.theme.LocalMediaRepository
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.mediaCacheDB
import org.fossify.gallery.extensions.mediaDB
import org.fossify.gallery.helpers.VIDEO_EXTENSIONS
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
    pathFilter: Set<String>? = null,
    activeTagName: String? = null,
    onClearFilter: () -> Unit = {},
    mediaOverride: List<Medium>? = null,
    refreshTrigger: Int = 0,
) {
    val ctx = LocalContext.current
    val viewModel: MediaViewModel = viewModel()

    LaunchedEffect(refreshTrigger) {
        if (refreshTrigger > 0) viewModel.refresh()
    }

    LaunchedEffect(refreshTrigger) {
        if (refreshTrigger > 0) viewModel.refresh()
    }

    LaunchedEffect(refreshTrigger) {
        if (refreshTrigger > 0) viewModel.refresh()
    }
    val state by viewModel.state.collectAsState()
    val repo = LocalMediaRepository.current
    var selectedPaths by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showSelectionSheet by remember { mutableStateOf(false) }
    var showRatingDialog by remember { mutableStateOf(false) }
    var showTagsDialog by remember { mutableStateOf(false) }
    var showFolderPicker by remember { mutableStateOf(false) }
    var folderPickerIsMove by remember { mutableStateOf(false) }
    var currentRating by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val columnCount = viewSettings.columnCount
    val isGrid = viewSettings.viewType == ViewType.GRID

    val baseMedia = mediaOverride ?: state.allMedia
    var ratedMedia by remember { mutableStateOf<List<Medium>?>(null) }
    var tagMedia by remember { mutableStateOf<List<Medium>?>(null) }
    var pathFallbackMedia by remember { mutableStateOf<List<Medium>?>(null) }

    LaunchedEffect(ratingFilter) {
        if (ratingFilter > 0) {
            val fromDb = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                ctx.mediaDB.getByMinRating(ratingFilter)
            }
            ratedMedia = fromDb
        } else {
            ratedMedia = null
        }
    }

    LaunchedEffect(tagFilterPaths) {
        if (tagFilterPaths != null) {
            val fromDb = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                ctx.mediaDB.getMediaByPaths(tagFilterPaths.toList())
            }
            tagMedia = fromDb
        } else {
            tagMedia = null
        }
    }

    // Load from Room DB when pathFilter yields no results (e.g. protected dirs like Download)
    LaunchedEffect(pathFilter) {
        if (pathFilter != null) {
            val dirs = pathFilter.filter { java.io.File(it).isDirectory }.toSet()
            val allPaths = (pathFilter + dirs).toList()
            pathFallbackMedia = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val db = ctx.mediaDB.getNewestMedia(5000)
                    db.filter { p -> allPaths.any { p.path.startsWith("$it/") || p.path == it } }.take(2000)
                } catch (_: Exception) { null }
            }
        } else { pathFallbackMedia = null }
    }

    val unsortedMedia = run {
        var m = baseMedia
        if (ratingFilter > 0) {
            val db = ratedMedia
            m = if (db != null && db.isNotEmpty()) db else m.filter { it.rating >= ratingFilter }
        }
        if (tagFilterPaths != null) {
            val tagged = tagMedia
            if (tagged != null && tagged.isNotEmpty()) {
                m = m.filter { it.path in tagged.map { it.path }.toSet() }
                if (m.isEmpty()) m = tagged // fallback: use DB results directly
            } else {
                m = m.filter { it.path in tagFilterPaths }
                if (m.isEmpty()) {
                    // Construct Medium objects from paths directly
                    m = tagFilterPaths.mapNotNull { path ->
                        val f = File(path)
                        if (f.exists()) Medium(
                            id = null, name = f.name, path = f.absolutePath,
                            parentPath = f.parent ?: "", modified = f.lastModified(),
                            taken = f.lastModified(), size = f.length(),
                            type = if (VIDEO_EXTENSIONS.any { path.endsWith(it, ignoreCase = true) }) 2 else 1,
                            videoDuration = 0, isFavorite = false, deletedTS = 0L, mediaStoreId = 0, rating = 0,
                        ) else null
                    }
                }
            }
        }
        if (pathFilter != null) {
            val dirs = pathFilter.filter { File(it).isDirectory }.toSet()
            m = m.filter { p -> p.path in pathFilter || dirs.any { p.path.startsWith("$it/") } }
            if (m.isEmpty() && pathFallbackMedia != null) m = pathFallbackMedia!!
        }
        m
    }
    val hasFilter = ratingFilter > 0 || tagFilterPaths != null || pathFilter != null
    val displayMedia = remember(unsortedMedia, viewSettings.sortBy, viewSettings.sortDesc) {
        val sorted = when (viewSettings.sortBy) {
            SortField.NAME -> unsortedMedia.sortedBy { it.name.lowercase() }
            SortField.DATE -> unsortedMedia.sortedBy { it.modified }
            SortField.SIZE -> unsortedMedia.sortedBy { it.size }
            SortField.RATING -> unsortedMedia.sortedBy { it.rating }
        }
        if (viewSettings.sortDesc) sorted.reversed() else sorted
    }
    val cornerShape = if (viewSettings.roundedCorners) RoundedCornerShape(8.dp) else RoundedCornerShape(0.dp)
    val itemSpacing = viewSettings.spacing.dp
    val mediaCardColor = when (viewSettings.displayMode) {
        DisplayMode.COMPACT, DisplayMode.NORMAL -> MaterialTheme.colorScheme.surface
        DisplayMode.DARK -> MaterialTheme.colorScheme.surfaceVariant
    }

    fun openViewer(index: Int) {
        val paths = displayMedia.map { it.path }
        ctx.startActivity(Intent(ctx, ComposeViewerActivity::class.java).apply {
            putStringArrayListExtra("PATHS", ArrayList(paths))
            putExtra("START_INDEX", index)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    val hasSelection = selectedPaths.isNotEmpty()
    BackHandler(enabled = hasSelection) { selectedPaths = emptySet() }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            state.isLoading && !hasFilter && mediaOverride == null -> {
                LoadingIndicator()
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
                            val label = when {
                                activeTagName != null && ratingFilter > 0 -> "Tag: $activeTagName · ★ $ratingFilter+"
                                activeTagName != null -> "Tag: $activeTagName"
                                ratingFilter > 0 -> "★ $ratingFilter+"
                                pathFilter != null -> "Suche: ${activeTagName ?: "aktiv"}"
                                else -> "Gefiltert"
                            }
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
                    LazyVerticalGrid(columns = GridCells.Fixed(columnCount), reverseLayout = viewSettings.anchorBottom, contentPadding = PaddingValues(itemSpacing / 2)) {
                    items(displayMedia.size) { idx ->
                        val m = displayMedia[idx]
                        val file = File(m.path)
                        val isVideo = m.path.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS
                        Column(Modifier.padding(itemSpacing / 2).background(mediaCardColor, cornerShape)) {
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
                                // Thumbnail overlays
                                if (ctx.config.showRatingOnThumbnails && m.rating > 0) {
                                    val stars = "★★★★★".take(m.rating)
                                    Box(Modifier.align(Alignment.TopStart).padding(4.dp).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 1.dp)) {
                                        Text(stars, style = MaterialTheme.typography.labelSmall, color = Color(0xFFFFD700), fontSize = 8.sp)
                                    }
                                }
                                if (isVideo && ctx.config.showVideoDurationOnThumbnails && m.videoDuration > 0) {
                                    val durStr = "%02d:%02d".format(m.videoDuration / 60, m.videoDuration % 60)
                                    Box(Modifier.align(Alignment.BottomEnd).padding(4.dp).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 1.dp)) {
                                        Text(durStr, style = MaterialTheme.typography.labelSmall, color = Color.White, fontSize = 10.sp)
                                    }
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
                LazyColumn(reverseLayout = viewSettings.anchorBottom, contentPadding = PaddingValues(4.dp)) {
                    items(displayMedia.size) { idx ->
                        val m = displayMedia[idx]
                        val file = File(m.path)
                        val isVideo = m.path.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS
                        Surface(modifier = Modifier.fillMaxWidth().background(mediaCardColor, RoundedCornerShape(8.dp)).combinedClickable(
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
                    Text("Alle", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 8.dp).clickable {
                        selectedPaths = displayMedia.map { it.path }.toSet()
                    })
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
                    val uris = ArrayList(selectedPaths.map { p -> androidx.core.content.FileProvider.getUriForFile(ctx, "${ctx.packageName}.provider", File(p)) })
                    val shareIntent = if (uris.size == 1) {
                        Intent(Intent.ACTION_SEND).apply { type = "*/*"; putExtra(Intent.EXTRA_STREAM, uris.first()); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                    } else {
                        Intent(Intent.ACTION_SEND_MULTIPLE).apply { type = "*/*"; putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                    }
                    ctx.startActivity(Intent.createChooser(shareIntent, "Teilen").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    showSelectionSheet = false; selectedPaths = emptySet()
                }
                SelectionRow(Icons.Default.Delete, "Löschen", tint = MaterialTheme.colorScheme.error) {
                    viewModel.deletePaths(selectedPaths); selectedPaths = emptySet(); showSelectionSheet = false
                }
                SelectionRow(Icons.Default.Info, "Info") { try { selectedPaths.firstOrNull()?.let { (ctx as? android.app.Activity)?.let { a -> PropertiesDialog(a, it, false) } } } catch (e: Exception) { ctx.toast("Info-Fehler: ${e.message}", Toast.LENGTH_LONG) }; showSelectionSheet = false }
                SelectionRow(Icons.Default.ContentCopy, "Kopieren") { folderPickerIsMove = false; showFolderPicker = true; showSelectionSheet = false }
                SelectionRow(Icons.AutoMirrored.Filled.DriveFileMove, "Verschieben") { folderPickerIsMove = true; showFolderPicker = true; showSelectionSheet = false }
                SelectionRow(Icons.Default.Star, "Bewerten") { showRatingDialog = true; showSelectionSheet = false }
                SelectionRow(Icons.Default.Edit, "Tags") { showTagsDialog = true; showSelectionSheet = false }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showRatingDialog) {
        val batch = selectedPaths.toList()
        StarRatingDialog(currentRating = currentRating, onRate = { i ->
            currentRating = i
            scope.launch(kotlinx.coroutines.Dispatchers.IO) { batch.forEach { p -> repo.updateRating(p, i) } }
            showRatingDialog = false
        }, onDismiss = { showRatingDialog = false })
    }
    if (showTagsDialog) {
        val batch = selectedPaths.toList()
        var allTags by remember { mutableStateOf<List<String>>(emptyList()) }
        LaunchedEffect(Unit) { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try { allTags = ctx.mediaCacheDB.getAllTagged().flatMap { it.tags.split(",").filter(String::isNotBlank) }.distinct() } catch (_: Exception) { }
        } }
        TagInputDialog(
            initialTags = repo.getTags(batch.first()),
            suggestedTags = allTags,
            onAddTag = { batch.forEach { p -> repo.addTag(p, it) } },
            onRemoveTag = { batch.forEach { p -> repo.removeTag(p, it) } },
            onDismiss = { showTagsDialog = false },
            batchCount = batch.size,
        )
    }

    if (showFolderPicker) {
        val batch = selectedPaths.toList()
        FolderPickerSheet(
            isMoveOperation = folderPickerIsMove,
            sourcePaths = batch,
            onDismiss = { showFolderPicker = false; selectedPaths = emptySet() }
        )
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024; if (kb < 1024) return "${kb} KB"
    val mb = kb / 1024; if (mb < 1024) return "${mb} MB"
    return "%.1f GB".format(mb / 1024.0)
}

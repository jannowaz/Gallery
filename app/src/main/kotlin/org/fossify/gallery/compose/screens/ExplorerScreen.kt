package org.fossify.gallery.compose.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.gallery.activities.ComposeViewerActivity
import org.fossify.gallery.compose.components.FolderTile
import org.fossify.gallery.compose.components.EmptyState
import org.fossify.gallery.compose.components.SelectionBar
import org.fossify.gallery.compose.components.GalleryImage
import org.fossify.gallery.compose.components.SelectionRow
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.mediaDB
import org.fossify.gallery.helpers.MEDIA_EXTENSIONS
import org.fossify.gallery.helpers.VIDEO_EXTENSIONS
import java.io.File

private data class ExplorerItem(
    val name: String, val path: String, val isDirectory: Boolean,
    val lastModified: Long = 0L, val size: Long = 0L,
    val thumbnailPath: String = "",
    val mediaCount: Int = 0,
    val previewPaths: List<String> = emptyList(),
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ExplorerScreen(
    internalStoragePath: String,
    modifier: Modifier = Modifier,
    folderSettings: ViewSettings = ViewSettings(),
    mediaSettings: ViewSettings = ViewSettings(),
    onPathChange: (String) -> Unit = {},
    onSelectionActiveChanged: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val navStack = remember { mutableStateListOf(internalStoragePath) }
    var currentPath by remember { mutableStateOf(internalStoragePath) }
    var folderItems by remember { mutableStateOf<List<ExplorerItem>>(emptyList()) }
    var fileItems by remember { mutableStateOf<List<ExplorerItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedFolderPaths by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showFolderSheet by remember { mutableStateOf(false) }
    val hasFolderSelection = selectedFolderPaths.isNotEmpty()
    LaunchedEffect(hasFolderSelection) { onSelectionActiveChanged(hasFolderSelection) }

    BackHandler(enabled = navStack.size > 1) {
        navStack.removeLastOrNull()
        currentPath = navStack.lastOrNull() ?: internalStoragePath
    }
    BackHandler(enabled = hasFolderSelection) { selectedFolderPaths = emptySet() }

    LaunchedEffect(internalStoragePath) {
        if (internalStoragePath != currentPath) {
            navStack.clear()
            navStack.add(internalStoragePath)
            currentPath = internalStoragePath
        }
    }

    val folderCardColor = when (folderSettings.displayMode) {
        DisplayMode.COMPACT, DisplayMode.NORMAL -> MaterialTheme.colorScheme.surface
        DisplayMode.DARK -> MaterialTheme.colorScheme.surfaceVariant
    }

    suspend fun loadFolderContents(path: String) {
        val (sortedFolders, sortedFiles) = withContext(Dispatchers.IO) {
            val root = path.trimEnd('/')
            val deletedPaths = try { context.mediaDB.getDeletedMedia().map { it.path }.toSet() } catch (_: Exception) { emptySet() }
            val hidden = context.config.explorer2HiddenFolders
            // Reconstruct the folder tree from MediaStore (raw directory listing is blocked under
            // scoped storage). Subfolders are derived from the media paths beneath the current path.
            val entries = org.fossify.gallery.helpers.MediaStoreOps.mediaEntriesUnder(context, root)
            val files = mutableListOf<ExplorerItem>()
            class Agg { var thumb: String = ""; var lastModified: Long = 0L; var count: Int = 0; val previews: MutableList<String> = mutableListOf() }
            val folderMap = LinkedHashMap<String, Agg>()
            for (e in entries) {
                if (e.path in deletedPaths) continue
                val rel = e.path.removePrefix("$root/")
                val slash = rel.indexOf('/')
                if (slash < 0) {
                    val ext = e.name.substringAfterLast('.', "").lowercase()
                    if (ext in MEDIA_EXTENSIONS) files.add(ExplorerItem(name = e.name, path = e.path, isDirectory = false, lastModified = e.modified, size = e.size))
                } else {
                    val seg = rel.substring(0, slash)
                    val folderPath = "$root/$seg"
                    if (folderPath in hidden) continue
                    val agg = folderMap.getOrPut(folderPath) { Agg() }
                    if (agg.thumb.isEmpty()) agg.thumb = e.path
                    if (agg.previews.size < 4) agg.previews.add(e.path)
                    agg.count++
                    if (e.modified > agg.lastModified) agg.lastModified = e.modified
                }
            }
            val folders = folderMap.map { (fp, agg) ->
                ExplorerItem(name = fp.substringAfterLast('/'), path = fp, isDirectory = true, lastModified = agg.lastModified, thumbnailPath = if (folderSettings.showFolderThumbnails) agg.thumb else "", mediaCount = agg.count, previewPaths = agg.previews.toList())
            }
            val sf = when (folderSettings.sortBy) {
                SortField.NAME -> folders.sortedBy { it.name.lowercase() }
                SortField.DATE -> folders.sortedBy { it.lastModified }
                SortField.SIZE -> folders.sortedBy { it.size }
                SortField.RATING -> folders.sortedBy { it.name.lowercase() }
            }.let { if (folderSettings.sortDesc) it.reversed() else it }
            val sfi = when (mediaSettings.sortBy) {
                SortField.NAME -> files.sortedBy { it.name.lowercase() }
                SortField.DATE -> files.sortedBy { it.lastModified }
                SortField.SIZE -> files.sortedBy { it.size }
                SortField.RATING -> files.sortedBy { it.name.lowercase() }
            }.let { if (mediaSettings.sortDesc) it.reversed() else it }
            Pair(sf, sfi)
        }
        folderItems = sortedFolders
        fileItems = sortedFiles
    }

    LaunchedEffect(currentPath, folderSettings.sortBy, folderSettings.sortDesc, mediaSettings.sortBy, mediaSettings.sortDesc) {
        isLoading = true
        loadFolderContents(currentPath)
        isLoading = false
    }

    LaunchedEffect(currentPath) { onPathChange(currentPath); selectedFolderPaths = emptySet() }

    Column(modifier = modifier.fillMaxSize()) {
        // Breadcrumb navigation bar
        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)).padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            if (navStack.size > 1) {
                IconButton(onClick = { navStack.removeLastOrNull(); currentPath = navStack.lastOrNull() ?: internalStoragePath }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zuruck", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
            } else {
                Spacer(Modifier.width(8.dp))
            }
            Text(currentPath, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(end = 8.dp))
        }

        if (isLoading) {
            MediaSkeleton(columns = 3)
        } else if (folderItems.isEmpty() && fileItems.isEmpty()) {
            EmptyState(Icons.Default.Folder, "Keine Elemente", subtitle = "Dieser Ordner enthält keine Medien")
        } else {
            LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) {
                if (folderItems.isNotEmpty()) {
                    item {
                        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Alben", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.weight(1f))
                            Text("${folderItems.size}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                        }
                    }
                    if (folderSettings.viewType == ViewType.GRID) {
                        folderItems.chunked(folderSettings.columnCount).forEach { chunk ->
                            item(key = chunk.joinToString { it.path }) {
                                Row(Modifier.fillMaxWidth().padding(folderSettings.spacing.dp / 2)) {
                                    chunk.forEach { item ->
                                        Box(Modifier.weight(1f).padding(folderSettings.spacing.dp / 2).combinedClickable(
                                            onClick = { if (hasFolderSelection) selectedFolderPaths = if (item.path in selectedFolderPaths) selectedFolderPaths - item.path else selectedFolderPaths + item.path else { navStack.add(item.path); currentPath = item.path } },
                                            onLongClick = { selectedFolderPaths = selectedFolderPaths + item.path }
                                        )) {
                                            FolderTile(
                                                name = item.name,
                                                thumbnailPath = item.thumbnailPath,
                                                showThumbnail = folderSettings.showFolderThumbnails,
                                                roundedCorners = folderSettings.roundedCorners,
                                                containerColor = folderCardColor
                                            )
                                            if (item.path in selectedFolderPaths) {
                                                Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)))
                                                Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(24.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                                                    Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        }
                                    }
                                    repeat(folderSettings.columnCount - chunk.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    } else {
                        items(folderItems, key = { it.path }) { item ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).combinedClickable(
                                    onClick = { if (hasFolderSelection) selectedFolderPaths = if (item.path in selectedFolderPaths) selectedFolderPaths - item.path else selectedFolderPaths + item.path else { navStack.add(item.path); currentPath = item.path } },
                                    onLongClick = { selectedFolderPaths = selectedFolderPaths + item.path }
                                ),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = if (item.path in selectedFolderPaths) MaterialTheme.colorScheme.primaryContainer else folderCardColor)
                            ) {
                                Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(item.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (folderSettings.displayMode == DisplayMode.DARK) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                                        Text("${item.mediaCount} Medien", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        if (item.previewPaths.isEmpty()) {
                                            Box(Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
                                        } else {
                                            item.previewPaths.take(3).forEach { p ->
                                                Box(Modifier.size(44.dp).clip(RoundedCornerShape(6.dp))) {
                                                    GalleryImage(path = p, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop, placeholderIconSize = 12.dp, thumbnailSize = 128)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (fileItems.isNotEmpty()) {
                    item {
                        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Medien", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.weight(1f))
                            Text("${fileItems.size}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                        }
                    }
                    val cornerShape = if (mediaSettings.roundedCorners) RoundedCornerShape(8.dp) else RoundedCornerShape(0.dp)
                    when (mediaSettings.viewType) {
                    ViewType.GRID -> {
                        fileItems.chunked(mediaSettings.columnCount).forEach { chunk ->
                            item(key = chunk.joinToString { it.path }) {
                                Row(Modifier.fillMaxWidth().padding(mediaSettings.spacing.dp / 2)) {
                                    chunk.forEach { item ->
                                        val file = File(item.path)
                                        val isVideo = item.path.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS
                                        val mediaBg = when (mediaSettings.displayMode) { DisplayMode.DARK -> MaterialTheme.colorScheme.surfaceVariant else -> MaterialTheme.colorScheme.surface }
                                        Box(Modifier.weight(1f).padding(mediaSettings.spacing.dp / 2).background(mediaBg, cornerShape).clickable {
                                            context.startActivity(Intent(context, ComposeViewerActivity::class.java).apply { putStringArrayListExtra("PATHS", ArrayList(fileItems.map { it.path })); putExtra("START_INDEX", fileItems.indexOfFirst { it.path == item.path }.coerceAtLeast(0)); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
                                        }) {
                                            Column {
                                                Box(Modifier.aspectRatio(1f).clip(cornerShape)) {
                                                    if (isVideo) VideoThumbnail(videoPath = item.path, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                                    else GalleryImage(path = item.path, contentDescription = item.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop, placeholderIconSize = 20.dp)
                                                }
                                                if (mediaSettings.showFileNames) {
                                                    Text(item.name, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                                                }
                                            }
                                        }
                                    }
                                    repeat(mediaSettings.columnCount - chunk.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                    ViewType.MOSAIC -> {
                        item(key = "explorer_media_mosaic") {
                            val aspectCache = remember { mutableStateMapOf<String, Float>() }
                            val n = mediaSettings.columnCount.coerceAtLeast(1)
                            val columns = remember(fileItems, n, aspectCache.size) {
                                val heights = FloatArray(n)
                                val buckets = Array(n) { mutableListOf<ExplorerItem>() }
                                fileItems.forEach { fi ->
                                    val isVid = fi.path.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS
                                    val ar = if (isVid) 1f else (aspectCache[fi.path] ?: 1f)
                                    val ci = (0 until n).minByOrNull { heights[it] } ?: 0
                                    buckets[ci].add(fi); heights[ci] += 1f / ar.coerceIn(0.3f, 3f)
                                }
                                buckets.map { it.toList() }
                            }
                            Row(Modifier.fillMaxWidth().padding(mediaSettings.spacing.dp / 2)) {
                                columns.forEach { col ->
                                    Column(Modifier.weight(1f)) {
                                        col.forEach { item ->
                                            val isVideo = item.path.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS
                                            if (!isVideo) LaunchedEffect(item.path) {
                                                if (aspectCache[item.path] == null) aspectCache[item.path] = withContext(Dispatchers.IO) { decodeImageAspect(item.path) }
                                            }
                                            val ar = (if (isVideo) 1f else (aspectCache[item.path] ?: 1f)).coerceIn(0.3f, 3f)
                                            Box(Modifier.padding(mediaSettings.spacing.dp / 2).aspectRatio(ar).clip(cornerShape).background(MaterialTheme.colorScheme.surfaceVariant, cornerShape).clickable {
                                                context.startActivity(Intent(context, ComposeViewerActivity::class.java).apply { putStringArrayListExtra("PATHS", ArrayList(fileItems.map { it.path })); putExtra("START_INDEX", fileItems.indexOfFirst { it.path == item.path }.coerceAtLeast(0)); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
                                            }) {
                                                if (isVideo) VideoThumbnail(videoPath = item.path, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                                else GalleryImage(path = item.path, contentDescription = item.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop, placeholderIconSize = 20.dp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        items(fileItems, key = { it.path }) { item ->
                            val file = File(item.path)
                                val isVideo = item.path.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS
                            Surface(Modifier.fillMaxWidth().clickable {
                                context.startActivity(Intent(context, ComposeViewerActivity::class.java).apply { putStringArrayListExtra("PATHS", ArrayList(fileItems.map { it.path })); putExtra("START_INDEX", fileItems.indexOfFirst { it.path == item.path }.coerceAtLeast(0)); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
                            }, color = Color.Transparent) {
                                Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(8.dp))) {
                                        if (isVideo) VideoThumbnail(videoPath = item.path, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                        else GalleryImage(path = item.path, contentDescription = item.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop, placeholderIconSize = 16.dp)
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(item.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(formatFileSize(item.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                            HorizontalDivider(Modifier.padding(start = 68.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        }
                    }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = hasFolderSelection,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(animationSpec = spring(dampingRatio = 0.7f)),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        ) {
            SelectionBar(
                count = selectedFolderPaths.size,
                onClear = { selectedFolderPaths = emptySet() },
                onMoreActions = { showFolderSheet = true },
            )
        }
    }

    if (showFolderSheet) {
        ModalBottomSheet(onDismissRequest = { showFolderSheet = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${selectedFolderPaths.size} ausgewählt", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    IconButton(onClick = { selectedFolderPaths = emptySet(); showFolderSheet = false }) { Icon(Icons.Default.Close, "Auswahl schließen", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                Spacer(Modifier.height(12.dp))
                SelectionRow(Icons.Default.Folder, "Öffnen") { selectedFolderPaths.firstOrNull()?.let { p -> navStack.add(p); currentPath = p }; showFolderSheet = false; selectedFolderPaths = emptySet() }
                SelectionRow(Icons.Default.VisibilityOff, "Ausblenden") {
                    selectedFolderPaths.forEach { p -> context.config.hideExplorer2Folder(p) }
                    scope.launch { loadFolderContents(currentPath) }
                    showFolderSheet = false; selectedFolderPaths = emptySet()
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1_000_000_000.0)
    bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000 -> "%.0f KB".format(bytes / 1_000.0)
    else -> "$bytes B"
}

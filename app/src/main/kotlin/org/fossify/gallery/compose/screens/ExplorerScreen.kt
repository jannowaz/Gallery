package org.fossify.gallery.compose.screens

import android.content.Intent
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
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fossify.gallery.activities.ComposeViewerActivity
import org.fossify.gallery.compose.components.FolderTile
import org.fossify.gallery.compose.components.SelectionRow
import org.fossify.gallery.helpers.MEDIA_EXTENSIONS
import org.fossify.gallery.helpers.VIDEO_EXTENSIONS
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

private data class ExplorerItem(
    val name: String, val path: String, val isDirectory: Boolean,
    val lastModified: Long = 0L, val size: Long = 0L,
    val thumbnailPath: String = "",
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ExplorerScreen(
    internalStoragePath: String,
    modifier: Modifier = Modifier,
    folderSettings: ViewSettings = ViewSettings(),
    mediaSettings: ViewSettings = ViewSettings(),
) {
    val context = LocalContext.current
    val navStack = remember { mutableStateListOf(internalStoragePath) }
    var currentPath by remember { mutableStateOf(internalStoragePath) }
    var folderItems by remember { mutableStateOf<List<ExplorerItem>>(emptyList()) }
    var fileItems by remember { mutableStateOf<List<ExplorerItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedFolderPaths by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showFolderSheet by remember { mutableStateOf(false) }
    val hasFolderSelection = selectedFolderPaths.isNotEmpty()

    suspend fun findThumbnailInFolder(folderPath: String): String = withContext(Dispatchers.IO) {
        try {
            Files.newDirectoryStream(Paths.get(folderPath)).use { stream ->
                for (entry in stream) {
                    val ext = entry.fileName.toString().substringAfterLast('.', "").lowercase()
                    if (ext in MEDIA_EXTENSIONS) return@withContext entry.toString()
                }
            }
        } catch (_: Exception) { }
        ""
    }

    val folderCardColor = when (folderSettings.displayMode) {
        DisplayMode.COMPACT, DisplayMode.NORMAL -> MaterialTheme.colorScheme.surface
        DisplayMode.DARK -> MaterialTheme.colorScheme.surfaceVariant
    }

    suspend fun loadFolderContents(path: String) = withContext(Dispatchers.IO) {
        val dir = Paths.get(path)
        if (!Files.isDirectory(dir)) return@withContext
        val folders = mutableListOf<ExplorerItem>()
        val files = mutableListOf<ExplorerItem>()
        try {
            Files.newDirectoryStream(dir).use { stream ->
                for (entry in stream) {
                    val name = entry.fileName.toString()
                    if (name.startsWith(".")) continue
                    val fPath = entry.toString()
                    if (Files.isDirectory(entry)) {
                        val tmb = findThumbnailInFolder(fPath)
                        folders.add(ExplorerItem(name = name, path = fPath, isDirectory = true, lastModified = Files.getLastModifiedTime(entry).toMillis(), thumbnailPath = tmb))
                    } else {
                        val ext = name.substringAfterLast('.', "").lowercase()
                        if (ext in MEDIA_EXTENSIONS) {
                            files.add(ExplorerItem(name = name, path = fPath, isDirectory = false, lastModified = Files.getLastModifiedTime(entry).toMillis(), size = Files.size(entry)))
                        }
                    }
                }
            }
        } catch (_: Exception) { }
        val sortedFolders = when (folderSettings.sortBy) {
            SortField.NAME -> folders.sortedBy { it.name.lowercase() }
            SortField.DATE -> folders.sortedBy { it.lastModified }
            SortField.SIZE -> folders.sortedBy { it.size }
            SortField.RATING -> folders.sortedBy { it.name.lowercase() }
        }.let { if (folderSettings.sortDesc) it.reversed() else it }
        val sortedFiles = when (mediaSettings.sortBy) {
            SortField.NAME -> files.sortedBy { it.name.lowercase() }
            SortField.DATE -> files.sortedBy { it.lastModified }
            SortField.SIZE -> files.sortedBy { it.size }
            SortField.RATING -> files.sortedBy { it.name.lowercase() }
        }.let { if (mediaSettings.sortDesc) it.reversed() else it }
        folderItems = sortedFolders
        fileItems = sortedFiles
    }

    LaunchedEffect(currentPath, folderSettings.sortBy, folderSettings.sortDesc, mediaSettings.sortBy, mediaSettings.sortDesc) {
        isLoading = true
        loadFolderContents(currentPath)
        isLoading = false
    }

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
            LoadingIndicator()
        } else if (folderItems.isEmpty() && fileItems.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Keine Elemente", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
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
                            item {
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
                            Surface(Modifier.fillMaxWidth().combinedClickable(
                                onClick = { if (hasFolderSelection) selectedFolderPaths = if (item.path in selectedFolderPaths) selectedFolderPaths - item.path else selectedFolderPaths + item.path else { navStack.add(item.path); currentPath = item.path } },
                                onLongClick = { selectedFolderPaths = selectedFolderPaths + item.path }
                            ), color = if (item.path in selectedFolderPaths) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else folderCardColor.copy(alpha = 0.3f)) {
                                Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Text(item.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Icon(Icons.Default.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                                }
                            }
                            HorizontalDivider(Modifier.padding(start = 68.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
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
                    if (mediaSettings.viewType == ViewType.GRID) {
                        fileItems.chunked(mediaSettings.columnCount).forEach { chunk ->
                            item {
                                Row(Modifier.fillMaxWidth().padding(mediaSettings.spacing.dp / 2)) {
                                    chunk.forEach { item ->
                                        val file = File(item.path)
                                        val isVideo = item.path.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS
                                        val mediaBg = when (mediaSettings.displayMode) { DisplayMode.DARK -> MaterialTheme.colorScheme.surfaceVariant else -> MaterialTheme.colorScheme.surface }
                                        Box(Modifier.weight(1f).padding(mediaSettings.spacing.dp / 2).background(mediaBg, cornerShape).clickable {
                                            context.startActivity(Intent(context, ComposeViewerActivity::class.java).apply { putStringArrayListExtra("PATHS", arrayListOf(item.path)); putExtra("START_INDEX", 0); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
                                        }) {
                                            Column {
                                                Box(Modifier.aspectRatio(1f).clip(cornerShape)) {
                                                    if (file.exists()) {
                                                        if (isVideo) VideoThumbnail(videoPath = item.path, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                                        else AsyncImage(model = ImageRequest.Builder(context).data(android.net.Uri.fromFile(file)).crossfade(true).build(), contentDescription = item.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                                    } else {
                                                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
                                                    }
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
                    } else {
                        items(fileItems, key = { it.path }) { item ->
                            val file = File(item.path)
                                val isVideo = item.path.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS
                            Surface(Modifier.fillMaxWidth().clickable {
                                context.startActivity(Intent(context, ComposeViewerActivity::class.java).apply { putStringArrayListExtra("PATHS", arrayListOf(item.path)); putExtra("START_INDEX", 0); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
                            }, color = Color.Transparent) {
                                Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(8.dp))) {
                                        if (file.exists()) {
                                            if (isVideo) VideoThumbnail(videoPath = item.path, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                            else AsyncImage(model = ImageRequest.Builder(context).data(android.net.Uri.fromFile(file)).crossfade(true).build(), contentDescription = item.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                        } else {
                                            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                                                Icon(Icons.Default.Image, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                                            }
                                        }
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

        if (hasFolderSelection) {
            Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)).clickable { showFolderSheet = true }.padding(horizontal = 20.dp, vertical = 14.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${selectedFolderPaths.size} ausgewählt", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Icon(Icons.Default.Close, "Auswahl aufheben", Modifier.size(20.dp).clickable { selectedFolderPaths = emptySet() }, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
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
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024
    if (kb < 1024) return "${kb} KB"
    val mb = kb / 1024
    if (mb < 1024) return "${mb} MB"
    return "%.1f GB".format(mb / 1024.0)
}

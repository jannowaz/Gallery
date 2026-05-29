package org.fossify.gallery.compose.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import org.fossify.gallery.compose.components.FolderTile
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

private val mediaExts = setOf("jpg", "jpeg", "png", "gif", "mp4", "mkv", "webp", "heic", "avif", "bmp", "svg", "apng", "jxl", "mov", "3gp", "wmv", "flv", "avi")
private val videoExts = setOf("mp4", "mkv", "mov", "3gp", "wmv", "flv", "avi")

private data class ExplorerItem(
    val name: String, val path: String, val isDirectory: Boolean,
    val lastModified: Long = 0L, val size: Long = 0L,
    val thumbnailPath: String = "",
)

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

    suspend fun findThumbnailInFolder(folderPath: String): String = withContext(Dispatchers.IO) {
        try {
            Files.newDirectoryStream(Paths.get(folderPath)).use { stream ->
                for (entry in stream) {
                    val ext = entry.fileName.toString().substringAfterLast('.', "").lowercase()
                    if (ext in mediaExts) return@withContext entry.toString()
                }
            }
        } catch (_: Exception) { }
        ""
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
                        if (ext in mediaExts) {
                            files.add(ExplorerItem(name = name, path = fPath, isDirectory = false, lastModified = Files.getLastModifiedTime(entry).toMillis(), size = Files.size(entry)))
                        }
                    }
                }
            }
        } catch (_: Exception) { }
        val sortedFolders = when (folderSettings.sortBy) {
            SortField.NAME -> folders.sortedBy { it.name.lowercase() }
            SortField.DATE -> folders.sortedBy { it.lastModified }
            SortField.SIZE, SortField.RATING -> folders.sortedBy { it.name.lowercase() }
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
        Text(File(currentPath).name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)

        if (isLoading) {
            LazyColumn(contentPadding = PaddingValues(8.dp)) {
                items(6) { ShimmerBox(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 12.dp, vertical = 4.dp)) }
            }
        } else if (folderItems.isEmpty() && fileItems.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Keine Elemente", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                if (folderItems.isNotEmpty()) {
                    Text("Alben", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    if (folderSettings.viewType == ViewType.GRID) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(folderSettings.columnCount),
                            contentPadding = PaddingValues(folderSettings.spacing.dp / 2),
                            modifier = Modifier.weight(if (fileItems.isEmpty()) 1f else 0.4f)
                        ) {
                            items(folderItems, key = { it.path }) { item ->
                                Box(Modifier.padding(folderSettings.spacing.dp / 2).clickable { navStack.add(item.path); currentPath = item.path }) {
                                    FolderTile(
                                        name = item.name,
                                        thumbnailPath = item.thumbnailPath,
                                        showThumbnail = folderSettings.showFolderThumbnails,
                                        roundedCorners = folderSettings.roundedCorners
                                    )
                                }
                            }
                        }
                    } else {
                        LazyColumn(modifier = Modifier.weight(if (fileItems.isEmpty()) 1f else 0.4f)) {
                            items(folderItems, key = { it.path }) { item ->
                                Surface(Modifier.fillMaxWidth().clickable { navStack.add(item.path); currentPath = item.path }, color = Color.Transparent) {
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
                }

                if (fileItems.isNotEmpty()) {
                    Text("Medien", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    if (mediaSettings.viewType == ViewType.GRID) {
                        val cornerShape = if (mediaSettings.roundedCorners) RoundedCornerShape(8.dp) else RoundedCornerShape(0.dp)
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(mediaSettings.columnCount),
                            contentPadding = PaddingValues(mediaSettings.spacing.dp / 2),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(fileItems, key = { it.path }) { item ->
                                val file = File(item.path)
                                val isVideo = item.path.substringAfterLast('.', "").lowercase() in videoExts
                                Column(Modifier.padding(mediaSettings.spacing.dp / 2)) {
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
                    } else {
                        LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) {
                            items(fileItems, key = { it.path }) { item ->
                                val file = File(item.path)
                                val isVideo = item.path.substringAfterLast('.', "").lowercase() in videoExts
                                Surface(Modifier.fillMaxWidth(), color = Color.Transparent) {
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

package org.fossify.gallery.compose.screens
import androidx.compose.ui.res.stringResource
import org.fossify.gallery.R
import org.fossify.gallery.compose.theme.Radius

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.commons.extensions.toast

import org.fossify.gallery.compose.util.rememberMediaStoreConsent
import org.fossify.gallery.compose.theme.LocalMediaRepository
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.deleteMediumWithPath
import org.fossify.gallery.helpers.MEDIA_EXTENSIONS
import org.fossify.gallery.helpers.MediaStoreOps
import org.fossify.gallery.helpers.RefreshBus
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

private data class FolderItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val mediaCount: Int = 0,
    val matchScore: Int = 0,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderPickerSheet(
    isMoveOperation: Boolean,
    sourcePaths: List<String>,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val repo = LocalMediaRepository.current
    val conf = ctx.config
    val rootPath = conf.internalStoragePath.ifBlank { android.os.Environment.getExternalStorageDirectory().absolutePath }
    val startPath = conf.lastCopyMoveDestination.takeIf { it.isNotBlank() && Files.isDirectory(Paths.get(it)) } ?: rootPath
    val navStack = remember { mutableStateListOf(startPath) }
    var currentPath by remember { mutableStateOf(navStack.last()) }
    var folders by remember { mutableStateOf<List<FolderItem>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<FolderItem>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var confirmTarget by remember { mutableStateOf<String?>(null) }
    var pendingCreateFolder by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val consent = rememberMediaStoreConsent()

    suspend fun loadFolders(path: String): List<FolderItem> = withContext(Dispatchers.IO) {
        val dir = Paths.get(path)
        if (!Files.isDirectory(dir)) return@withContext emptyList()
        val result = mutableListOf<FolderItem>()
        try {
            Files.newDirectoryStream(dir).use { stream ->
                for (entry in stream) {
                    val name = entry.fileName.toString()
                    if (name.startsWith(".")) continue
                    if (Files.isDirectory(entry)) {
                        val fPath = entry.toString()
                        val mc = try { Files.newDirectoryStream(entry).use { s -> s.count { !it.fileName.toString().startsWith(".") } } } catch (_: Exception) { 0 }
                        result.add(FolderItem(name = name, path = fPath, isDirectory = true, mediaCount = mc))
                    }
                }
            }
        } catch (_: Exception) { }
        result.sortedBy { it.name.lowercase() }
    }

    suspend fun searchFolders(query: String): List<FolderItem> = withContext(Dispatchers.IO) {
        if (query.length < 2) return@withContext emptyList()
        val qParts = query.lowercase().split(" ").filter { it.isNotBlank() }
        if (qParts.isEmpty()) return@withContext emptyList()
        // Search the already-indexed folder DB instead of walking the filesystem (instant)
        val dirs = repo.getAllDirectories()
        val results = mutableListOf<Pair<Int, FolderItem>>()
        for (d in dirs) {
            val lowerPath = d.path.lowercase()
            var score = 0
            for (part in qParts) {
                val idx = lowerPath.indexOf(part)
                if (idx < 0) { score = -1; break }
                score += maxOf(0, 100 - idx)
            }
            if (score > 0) results.add(score to FolderItem(name = d.name, path = d.path, isDirectory = true, mediaCount = d.mediaCnt, matchScore = score))
        }
        results.sortedByDescending { it.first }.take(80).map { it.second }
    }

    LaunchedEffect(currentPath) {
        folders = loadFolders(currentPath)
    }

    LaunchedEffect(searchQuery) {
        if (searchQuery.length < 2) { searchResults = emptyList(); return@LaunchedEffect }
        isSearching = true
        kotlinx.coroutines.delay(250)
        searchResults = searchFolders(searchQuery)
        isSearching = false
    }

    val isSearchingMode = searchQuery.length >= 2

    fun performCopyMove(destPath: String) {
        conf.lastCopyMoveDestination = destPath
        val targetRel = MediaStoreOps.relativePathFor(destPath)
        scope.launch {
            val srcUris = withContext(Dispatchers.IO) { MediaStoreOps.urisForPaths(ctx, sourcePaths) }
            if (srcUris.isEmpty()) {
                ctx.toast(ctx.getString(R.string.no_files_found), Toast.LENGTH_LONG); onDismiss(); return@launch
            }
            // Moving someone else's media requires user consent via the system dialog. Copying
            // creates a new app-owned file, which needs no consent.
            if (isMoveOperation) {
                val granted = try {
                    consent.request(MediaStoreOps.writeRequest(ctx, srcUris.map { it.second }))
                } catch (_: Exception) { false }
                if (!granted) { ctx.toast(ctx.getString(R.string.cancelled), Toast.LENGTH_SHORT); onDismiss(); return@launch }
            }
            val (done, failed) = withContext(Dispatchers.IO) {
                var ok = 0; var fail = 0
                for ((path, uri) in srcUris) {
                    val success = if (isMoveOperation) {
                        MediaStoreOps.move(ctx, uri, targetRel).also {
                            if (it) try { ctx.deleteMediumWithPath(path) } catch (_: Exception) { }
                        }
                    } else {
                        MediaStoreOps.copy(ctx, uri, File(path).name, targetRel, MediaStoreOps.isVideoPath(path)) != null
                    }
                    if (success) ok++ else fail++
                }
                ok to fail
            }
            RefreshBus.trigger()
            val total = sourcePaths.size
            val msg = when {
                done == total -> if (isMoveOperation) "Verschoben" else "Kopiert"
                done > 0 -> "$done/$total ${if (isMoveOperation) "verschoben" else "kopiert"}, $failed fehlgeschlagen"
                else -> "Keine Dateien ${if (isMoveOperation) "verschoben" else "kopiert"} ($failed fehlgeschlagen)"
            }
            ctx.toast(msg, Toast.LENGTH_LONG)
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(480.dp)) {
            // Header with title and action type
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (isMoveOperation) Icons.AutoMirrored.Filled.DriveFileMove else Icons.Default.ContentCopy,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (isMoveOperation) "Verschieben nach..." else "Kopieren nach...",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(stringResource(R.string.files_count, sourcePaths.size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))

            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(stringResource(R.string.search_folders_hint)) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, stringResource(R.string.cd_search), modifier = Modifier.size(20.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, stringResource(R.string.action_empty), modifier = Modifier.size(16.dp))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyMedium,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                ),
                shape = RoundedCornerShape(Radius.md),
            )
            Spacer(Modifier.height(4.dp))

            if (isSearchingMode) {
                // Search results
                if (isSearching && searchResults.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.searching), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else if (searchResults.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.no_folders_found), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    Text(stringResource(R.string.folders_found, searchResults.size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 4.dp))
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(searchResults, key = { it.path }) { item ->
                            FolderResultRow(
                                item = item,
                                query = searchQuery,
                                onClick = { confirmTarget = item.path }
                            )
                        }
                    }
                }
            } else {
                // Breadcrumb + directory listing
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (navStack.size > 1) {
                        IconButton(onClick = { navStack.removeAt(navStack.lastIndex); currentPath = navStack.last() }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        }
                    } else {
                        Spacer(Modifier.width(36.dp))
                    }
                    Text(currentPath, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    IconButton(onClick = { pendingCreateFolder = true }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.CreateNewFolder, stringResource(R.string.new_folder), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.height(4.dp))
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(folders, key = { it.path }) { folder ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable {
                                navStack.add(folder.path); currentPath = folder.path
                            },
                            color = Color.Transparent,
                            shape = RoundedCornerShape(Radius.sm),
                        ) {
                            Row(Modifier.padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(folder.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (folder.mediaCount > 0) {
                                        Text(stringResource(R.string.media_count, folder.mediaCount), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                        HorizontalDivider(Modifier.padding(start = 44.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    }
                }
            }

            // Action buttons at bottom
            if (!isSearchingMode) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(Radius.md),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f).clickable { confirmTarget = currentPath }
                    ) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
                            Icon(
                                if (isMoveOperation) Icons.AutoMirrored.Filled.DriveFileMove else Icons.Default.ContentCopy,
                                null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (isMoveOperation) "Hierher verschieben" else "Hierher kopieren",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            } else {
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (pendingCreateFolder) {
        AlertDialog(
            onDismissRequest = { pendingCreateFolder = false; newFolderName = "" },
            title = { Text(stringResource(R.string.create_folder_title)) },
            text = {
                OutlinedTextField(value = newFolderName, onValueChange = { newFolderName = it }, label = { Text(stringResource(R.string.folder_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newFolderName.isNotBlank()) {
                        val newDir = File(currentPath, newFolderName)
                        try { newDir.mkdirs(); navStack.add(newDir.path); currentPath = newDir.path } catch (_: Exception) { ctx.toast(ctx.getString(R.string.create_folder_error)) }
                        pendingCreateFolder = false; newFolderName = ""
                    }
                }) { Text(stringResource(R.string.action_create)) }
            },
            dismissButton = { TextButton(onClick = { pendingCreateFolder = false; newFolderName = "" }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    if (confirmTarget != null) {
        val dest = confirmTarget!!
        val parentPath = File(dest).parent ?: rootPath
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmTarget = null },
            title = { Text(if (isMoveOperation) "Verschieben?" else "Kopieren?") },
            text = {
                Column {
                    Text(stringResource(if (isMoveOperation) R.string.move_files_to else R.string.copy_files_to, sourcePaths.size))
                    Spacer(Modifier.height(4.dp))
                    Text(dest, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    performCopyMove(dest)
                    confirmTarget = null
                }) { Text(if (isMoveOperation) "Verschieben" else "Kopieren") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmTarget = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun FolderResultRow(item: FolderItem, query: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = Color.Transparent,
        shape = RoundedCornerShape(Radius.sm),
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.FolderOpen, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.path, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (item.mediaCount > 0) {
                Text("${item.mediaCount}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    HorizontalDivider(Modifier.padding(start = 44.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
}

package org.fossify.gallery.compose.screens

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.fossify.gallery.compose.components.FolderTile
import org.fossify.gallery.extensions.config
import org.fossify.gallery.models.Directory
import org.fossify.gallery.viewmodels.AlbumsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumsScreen(
    viewModel: AlbumsViewModel,
    onFolderClick: (Directory) -> Unit,
    modifier: Modifier = Modifier,
    viewSettings: ViewSettings = ViewSettings(),
) {
    val ctx = LocalContext.current
    val state by viewModel.state.collectAsState()
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var favorites by remember { mutableStateOf(ctx.config.favoriteFolders) }

    val filteredDirs = run {
        val base = if (searchQuery.isBlank()) state.directories
        else state.directories.filter { it.name.contains(searchQuery, ignoreCase = true) }
        val sorted = when (viewSettings.sortBy) {
            SortField.NAME -> base.sortedBy { it.name.lowercase() }
            SortField.DATE -> base.sortedBy { it.modified }
            SortField.SIZE -> base.sortedBy { it.size }
            SortField.RATING -> base.sortedBy { it.mediaCnt }
        }
        if (viewSettings.sortDesc) sorted.reversed() else sorted
    }

    val isGrid = viewSettings.viewType == ViewType.GRID
    val itemSpacing = viewSettings.spacing.dp

    Column(modifier = modifier.fillMaxSize()) {
        if (isSearchActive) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = searchQuery, onValueChange = { searchQuery = it },
                    placeholder = { Text("Suchen...") }, singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)),
                    leadingIcon = { Icon(Icons.Default.Search, "Suchen") },
                    trailingIcon = { IconButton(onClick = { isSearchActive = false; searchQuery = "" }) { Icon(Icons.Default.Close, "Schließen") } }
                )
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = { isSearchActive = true }) { Icon(Icons.Default.Search, "Suchen") }
            }
        }

        if (state.isLoading) {
            Box(Modifier.weight(1f)) {
                LazyColumn { items(6) { ShimmerBox(Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 12.dp, vertical = 4.dp)) } }
            }
        } else if (state.directories.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Folder, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    Spacer(Modifier.height(16.dp))
                    Text("Keine Alben", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
        } else if (isGrid) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(viewSettings.columnCount),
                contentPadding = PaddingValues(itemSpacing / 2),
                modifier = Modifier.weight(1f)
            ) {
                items(filteredDirs, key = { it.path }) { dir ->
                    Box(Modifier.padding(itemSpacing / 2).clickable { onFolderClick(dir) }) {
                        FolderTile(
                            name = dir.name,
                            thumbnailPath = dir.tmb,
                            showThumbnail = viewSettings.showFolderThumbnails,
                            roundedCorners = viewSettings.roundedCorners
                        )
                    }
                }
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(filteredDirs, key = { it.path }) { dir ->
                    val isFav = dir.path in favorites
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clickable { onFolderClick(dir) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(dir.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${dir.mediaCnt} Medien", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = {
                                if (isFav) ctx.config.removeFavoriteFolder(dir.path) else ctx.config.addFavoriteFolder(dir.path)
                                favorites = ctx.config.favoriteFolders
                            }) {
                                Icon(
                                    if (isFav) Icons.Default.Star else Icons.Default.StarBorder,
                                    if (isFav) "Aus Favoriten entfernen" else "Zu Favoriten hinzufügen",
                                    tint = if (isFav) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

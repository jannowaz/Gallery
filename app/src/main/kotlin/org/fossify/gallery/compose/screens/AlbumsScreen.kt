package org.fossify.gallery.compose.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.fossify.commons.dialogs.PropertiesDialog
import org.fossify.gallery.compose.components.FolderTile
import org.fossify.gallery.compose.components.SelectionRow
import org.fossify.gallery.extensions.config
import org.fossify.gallery.models.Directory
import org.fossify.gallery.viewmodels.AlbumsViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AlbumsScreen(
    viewModel: AlbumsViewModel,
    onFolderClick: (Directory) -> Unit,
    modifier: Modifier = Modifier,
    viewSettings: ViewSettings = ViewSettings(),
) {
    val ctx = LocalContext.current
    val state by viewModel.state.collectAsState()
    var favorites by remember { mutableStateOf(ctx.config.favoriteFolders) }
    var selectedPaths by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showSelectionSheet by remember { mutableStateOf(false) }
    val hasSelection = selectedPaths.isNotEmpty()

    val sortedDirs = remember(state.directories, viewSettings.sortBy, viewSettings.sortDesc) {
        val sorted = when (viewSettings.sortBy) {
            SortField.NAME -> state.directories.sortedBy { it.name.lowercase() }
            SortField.DATE -> state.directories.sortedBy { it.modified }
            SortField.SIZE -> state.directories.sortedBy { it.size }
            SortField.RATING -> state.directories.sortedBy { it.name.lowercase() }
        }
        if (viewSettings.sortDesc) sorted.reversed() else sorted
    }

    val isGrid = viewSettings.viewType == ViewType.GRID
    val itemSpacing = viewSettings.spacing.dp
    val containerColor = when (viewSettings.displayMode) {
        DisplayMode.COMPACT, DisplayMode.NORMAL -> MaterialTheme.colorScheme.surface
        DisplayMode.DARK -> MaterialTheme.colorScheme.surfaceVariant
    }

    Box(Modifier.fillMaxSize()) {
        Column(modifier = modifier.fillMaxSize()) {
            if (state.isLoading) {
                LoadingIndicator()
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
                    reverseLayout = viewSettings.anchorBottom,
                    contentPadding = PaddingValues(itemSpacing / 2),
                    modifier = Modifier.weight(1f)
                ) {
                    items(sortedDirs, key = { it.path }) { dir ->
                        Box(Modifier.padding(itemSpacing / 2).combinedClickable(
                            onClick = { if (hasSelection) selectedPaths = if (dir.path in selectedPaths) selectedPaths - dir.path else selectedPaths + dir.path else onFolderClick(dir) },
                            onLongClick = { selectedPaths = selectedPaths + dir.path }
                        )) {
                            FolderTile(
                                name = dir.name,
                                thumbnailPath = dir.tmb,
                                showThumbnail = viewSettings.showFolderThumbnails,
                                roundedCorners = viewSettings.roundedCorners,
                                containerColor = containerColor
                            )
                            if (dir.path in selectedPaths) {
                                Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)))
                                Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(24.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(reverseLayout = viewSettings.anchorBottom, modifier = Modifier.weight(1f)) {
                    items(sortedDirs, key = { it.path }) { dir ->
                        val isFav = dir.path in favorites
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).combinedClickable(
                                onClick = { if (hasSelection) selectedPaths = if (dir.path in selectedPaths) selectedPaths - dir.path else selectedPaths + dir.path else onFolderClick(dir) },
                                onLongClick = { selectedPaths = selectedPaths + dir.path }
                            ),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = if (dir.path in selectedPaths) MaterialTheme.colorScheme.primaryContainer else containerColor)
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(dir.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (viewSettings.displayMode == DisplayMode.DARK) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
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
        ModalBottomSheet(onDismissRequest = { showSelectionSheet = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${selectedPaths.size} ausgewählt", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    IconButton(onClick = { selectedPaths = emptySet(); showSelectionSheet = false }) { Icon(Icons.Default.Close, "Auswahl schließen", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                Spacer(Modifier.height(12.dp))
                SelectionRow(Icons.Default.Folder, "Öffnen") { selectedPaths.firstOrNull()?.let { p -> sortedDirs.find { it.path == p }?.let { onFolderClick(it) } }; showSelectionSheet = false; selectedPaths = emptySet() }
                SelectionRow(Icons.Default.Info, "Info") { selectedPaths.firstOrNull()?.let { (ctx as? android.app.Activity)?.let { a -> PropertiesDialog(a, it, true) } }; showSelectionSheet = false }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                val selPath = selectedPaths.firstOrNull() ?: ""
                val isFav = selPath in favorites
                SelectionRow(if (isFav) Icons.Default.Star else Icons.Default.StarBorder, if (isFav) "Aus Favoriten entfernen" else "Favorisieren") {
                    if (isFav) ctx.config.removeFavoriteFolder(selPath) else ctx.config.addFavoriteFolder(selPath)
                    favorites = ctx.config.favoriteFolders; showSelectionSheet = false; selectedPaths = emptySet()
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

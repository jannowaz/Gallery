package org.fossify.gallery.compose.components
import androidx.compose.ui.res.stringResource
import org.fossify.gallery.R

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.fossify.gallery.compose.screens.DisplayMode
import org.fossify.gallery.compose.screens.ViewSettings
import org.fossify.gallery.compose.screens.ViewType
import org.fossify.gallery.compose.theme.LocalSpacing
import org.fossify.gallery.compose.theme.Radius

/** Generic "album-like" item used by the shared album/library grid (Albums, Favorites, Tags, Collections). */
data class AlbumGridItem(
    val key: String,
    val name: String,
    val thumbnailPath: String = "",
    val count: Int = 0,
    val previewPaths: List<String> = emptyList(),
)

/**
 * Shared album-style item list, matching the Alben tab: Kacheln (FolderTile grid) or Liste
 * (card row with name, "$count Medien" and up to 3 preview thumbnails). Driven by [viewSettings].
 */
@Composable
fun LibraryAlbumGrid(
    items: List<AlbumGridItem>,
    viewSettings: ViewSettings,
    onClick: (AlbumGridItem) -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: ((AlbumGridItem) -> Unit)? = null,
    countLabel: (Int) -> String = { "$it Medien" },
    selectedKeys: Set<String> = emptySet(),
    subtitle: ((AlbumGridItem) -> String)? = null,
) {
    val s = LocalSpacing.current
    val containerColor = when (viewSettings.displayMode) {
        DisplayMode.COMPACT, DisplayMode.NORMAL -> MaterialTheme.colorScheme.surface
        DisplayMode.DARK -> MaterialTheme.colorScheme.surfaceVariant
    }
    val itemSpacing = viewSettings.spacing.dp

    if (viewSettings.viewType == ViewType.LIST) {
        LazyColumn(modifier.fillMaxSize()) {
            items(items, key = { it.key }) { item ->
                val selected = item.key in selectedKeys
                AppCard(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = s.md, vertical = s.xs).clickableItem(item, onClick, onLongClick),
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else containerColor,
                ) {
                    Row(Modifier.padding(s.md).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(item.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (viewSettings.displayMode == DisplayMode.DARK) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                            Text(subtitle?.invoke(item) ?: countLabel(item.count), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (selected) {
                            Icon(Icons.Default.Check, stringResource(R.string.cd_selected), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = s.sm).size(20.dp))
                        } else {
                            val previews = item.previewPaths.ifEmpty { if (item.thumbnailPath.isNotEmpty()) listOf(item.thumbnailPath) else emptyList() }
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                if (previews.isEmpty()) {
                                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(Radius.xs)).background(MaterialTheme.colorScheme.surfaceVariant))
                                } else {
                                    previews.take(3).forEach { p ->
                                        Box(Modifier.size(44.dp).clip(RoundedCornerShape(Radius.xs))) {
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
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(viewSettings.columnCount),
            contentPadding = PaddingValues(itemSpacing / 2),
            modifier = modifier.fillMaxSize(),
        ) {
            items(items, key = { it.key }) { item ->
                Box(Modifier.padding(itemSpacing / 2).clickableItem(item, onClick, onLongClick)) {
                    FolderTile(
                        name = item.name,
                        thumbnailPath = item.thumbnailPath,
                        showThumbnail = viewSettings.showFolderThumbnails,
                        roundedCorners = viewSettings.roundedCorners,
                        containerColor = containerColor,
                        subtitle = if (item.count > 0) countLabel(item.count) else null,
                    )
                    if (item.key in selectedKeys) {
                        Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)))
                        Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(24.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.clickableItem(
    item: AlbumGridItem,
    onClick: (AlbumGridItem) -> Unit,
    onLongClick: ((AlbumGridItem) -> Unit)?,
): Modifier = if (onLongClick != null) {
    this.combinedClickable(onClick = { onClick(item) }, onLongClick = { onLongClick(item) })
} else {
    this.clickable { onClick(item) }
}

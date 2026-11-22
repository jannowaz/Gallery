package org.fossify.gallery.compose.components
import androidx.compose.ui.res.pluralStringResource
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.fossify.gallery.compose.screens.DisplayMode
import org.fossify.gallery.compose.screens.ViewSettings
import org.fossify.gallery.compose.screens.ViewType
import org.fossify.gallery.compose.theme.LocalSpacing
import org.fossify.gallery.compose.theme.Radius
import org.fossify.gallery.compose.util.ScrollToTopEffect

/** Generic "album-like" item used by the shared album/library grid (Albums, Favorites, Tags, Collections). */
data class AlbumGridItem(
    val key: String,
    val name: String,
    val thumbnailPath: String = "",
    val count: Int = 0,
    val imageCount: Int = 0,
    val videoCount: Int = 0,
    val previewPaths: List<String> = emptyList(),
)

/**
 * Shared album-style item list, matching the Albums tab: tiles (FolderTile grid) or list
 * (card row with name, localized item count and up to 3 preview thumbnails). Driven by [viewSettings].
 */
@Composable
fun LibraryAlbumGrid(
    items: List<AlbumGridItem>,
    viewSettings: ViewSettings,
    onClick: (AlbumGridItem) -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: ((AlbumGridItem) -> Unit)? = null,
    countLabel: ((Int) -> String)? = null,
    selectedKeys: Set<String> = emptySet(),
    subtitle: ((AlbumGridItem) -> String)? = null,
    showCollectionStacks: Boolean = false,
    // Faint containing-path hint for folder-backed lists (Albums, Favorites) to disambiguate
    // same-named folders. Left null by container lists (Collections/Tags), which have no path.
    pathHint: ((AlbumGridItem) -> String?)? = null,
    tabIndex: Int? = null,
    fastScrollLabel: ((Int) -> String?)? = null,
) {
    val s = LocalSpacing.current
    val containerColor = when (viewSettings.displayMode) {
        // Not colorScheme.surface - this app's custom ColorScheme sets background == surface, so an
        // item tile with no thumbnail (empty folder/tag/collection) would be visually indistinguishable
        // from the screen behind it, leaving no indication a tappable card is even there.
        DisplayMode.COMPACT, DisplayMode.NORMAL -> MaterialTheme.colorScheme.surfaceContainerHigh
        DisplayMode.DARK -> MaterialTheme.colorScheme.surfaceVariant
    }
    val itemSpacing = viewSettings.spacing.dp

    if (viewSettings.viewType == ViewType.LIST) {
        val listState = rememberLazyListState()
        ScrollToTopEffect(tabIndex) { listState.animateScrollToItem(0) }
        // Lists are intentionally one column. The separate density setting changes rhythm without
        // sacrificing the title, count badge or trailing navigation affordance.
        Box(modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                verticalArrangement = if (viewSettings.anchorBottom) Arrangement.Bottom else Arrangement.Top,
                modifier = Modifier.fillMaxSize(),
            ) {
                items(items, key = { it.key }, contentType = { "album_row" }) { item ->
                    val defaultCountLabel = countLabel?.invoke(item.count)
                        ?: pluralStringResource(R.plurals.media_count_plural, item.count, item.count)
                    val description = buildString {
                        append(item.name)
                        if (item.count > 0) append(", ").append(defaultCountLabel)
                    }
                    AlbumListRow(
                        name = item.name,
                        subtitle = subtitle?.invoke(item) ?: defaultCountLabel,
                        coverPath = item.thumbnailPath.ifEmpty { item.previewPaths.firstOrNull() ?: "" },
                        selected = item.key in selectedKeys,
                        showChevron = true, // every album/collection/favorite/folder row opens on tap
                        showThumbnail = viewSettings.showFolderThumbnails,
                        compact = viewSettings.listDensity == org.fossify.gallery.compose.screens.ListDensity.COMPACT,
                        badgeSubtitle = subtitle == null,
                        pathHint = pathHint?.invoke(item),
                        imageCount = item.imageCount,
                        videoCount = item.videoCount,
                        modifier = Modifier.padding(horizontal = s.md, vertical = s.xs)
                            .clickableItem(item, onClick, onLongClick)
                            .semantics(mergeDescendants = true) {
                                contentDescription = description
                                role = Role.Button
                                selected = item.key in selectedKeys
                            },
                    )
                }
            }
            FastScrollBar(
                state = listState,
                modifier = Modifier.align(Alignment.CenterEnd).padding(vertical = 8.dp),
                positionLabel = { index -> fastScrollLabel?.invoke(index) },
            )
        }
    } else {
        val gridState = rememberLazyGridState()
        ScrollToTopEffect(tabIndex) { gridState.animateScrollToItem(0) }
        val effectiveColumns = org.fossify.gallery.compose.util.rememberEffectiveColumnCount(viewSettings.columnCount)
        val containerWidthDp = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
        val tileWidthDp = containerWidthDp / effectiveColumns.coerceAtLeast(1)
        val isNarrowTile = tileWidthDp < 88.dp
        val isUltraNarrowTile = tileWidthDp < 84.dp
        Box(modifier.fillMaxSize()) {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(effectiveColumns),
                contentPadding = PaddingValues(itemSpacing / 2),
                // Bottom-anchored, same order (see LIST branch above).
                verticalArrangement = if (viewSettings.anchorBottom) Arrangement.Bottom else Arrangement.Top,
                modifier = Modifier.fillMaxSize(),
            ) {
                items(items, key = { it.key }, contentType = { "album_tile" }) { item ->
                    val defaultCountLabel = countLabel?.invoke(item.count)
                        ?: pluralStringResource(R.plurals.media_count_plural, item.count, item.count)
                    val resolvedSubtitle = subtitle?.invoke(item) ?: defaultCountLabel
                    val description = "${item.name}, $resolvedSubtitle"
                    Box(Modifier.padding(itemSpacing / 2).clickableItem(item, onClick, onLongClick).semantics(mergeDescendants = true) {
                        contentDescription = description
                        role = Role.Button
                        selected = item.key in selectedKeys
                    }) {
                        FolderTile(
                            name = item.name,
                            thumbnailPath = item.thumbnailPath,
                            previewPaths = item.previewPaths,
                            isCollection = showCollectionStacks,
                            showThumbnail = viewSettings.showFolderThumbnails,
                            roundedCorners = viewSettings.roundedCorners,
                            containerColor = containerColor,
                            subtitle = resolvedSubtitle,
                            imageCount = item.imageCount,
                            videoCount = item.videoCount,
                            isNarrow = isNarrowTile,
                            isUltraNarrow = isUltraNarrowTile,
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
            FastScrollBar(
                state = gridState,
                modifier = Modifier.align(Alignment.CenterEnd).padding(vertical = 8.dp),
                positionLabel = { index -> fastScrollLabel?.invoke(index) },
            )
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

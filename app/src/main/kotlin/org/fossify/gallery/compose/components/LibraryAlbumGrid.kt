package org.fossify.gallery.compose.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.fossify.gallery.R
import org.fossify.gallery.compose.screens.DisplayMode
import org.fossify.gallery.compose.screens.ListDensity
import org.fossify.gallery.compose.screens.ViewSettings
import org.fossify.gallery.compose.screens.ViewType
import org.fossify.gallery.compose.theme.LocalSpacing
import org.fossify.gallery.compose.util.ScrollToTopEffect
import org.fossify.gallery.compose.util.rememberEffectiveColumnCount

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
 * (card row with name, localized item count and cover thumbnail). Driven by [viewSettings].
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
    val containerColor = when (viewSettings.displayMode) {
        // Not colorScheme.surface - this app's custom ColorScheme sets background == surface, so an
        // item tile with no thumbnail (empty folder/tag/collection) would be visually indistinguishable
        // from the screen behind it, leaving no indication a tappable card is even there.
        DisplayMode.COMPACT, DisplayMode.NORMAL -> MaterialTheme.colorScheme.surfaceContainerHigh
        DisplayMode.DARK -> MaterialTheme.colorScheme.surfaceVariant
    }

    if (viewSettings.viewType == ViewType.LIST) {
        LibraryAlbumList(
            items = items,
            viewSettings = viewSettings,
            containerColor = containerColor,
            onClick = onClick,
            onLongClick = onLongClick,
            countLabel = countLabel,
            selectedKeys = selectedKeys,
            subtitle = subtitle,
            pathHint = pathHint,
            tabIndex = tabIndex,
            fastScrollLabel = fastScrollLabel,
            modifier = modifier,
        )
    } else {
        LibraryAlbumTiles(
            items = items,
            viewSettings = viewSettings,
            containerColor = containerColor,
            onClick = onClick,
            onLongClick = onLongClick,
            countLabel = countLabel,
            selectedKeys = selectedKeys,
            subtitle = subtitle,
            showCollectionStacks = showCollectionStacks,
            pathHint = pathHint,
            tabIndex = tabIndex,
            fastScrollLabel = fastScrollLabel,
            modifier = modifier,
        )
    }
}

@Composable
private fun LibraryAlbumList(
    items: List<AlbumGridItem>,
    viewSettings: ViewSettings,
    containerColor: Color,
    onClick: (AlbumGridItem) -> Unit,
    onLongClick: ((AlbumGridItem) -> Unit)?,
    countLabel: ((Int) -> String)?,
    selectedKeys: Set<String>,
    subtitle: ((AlbumGridItem) -> String)?,
    pathHint: ((AlbumGridItem) -> String?)?,
    tabIndex: Int?,
    fastScrollLabel: ((Int) -> String?)?,
    modifier: Modifier = Modifier,
) {
    val s = LocalSpacing.current
    val listState = rememberLazyListState()
    ScrollToTopEffect(tabIndex) { listState.animateScrollToItem(0) }
    val listGap = (viewSettings.spacing / 2).coerceAtLeast(2).dp
    val arrangement = if (viewSettings.anchorBottom) {
        Arrangement.spacedBy(listGap, Alignment.Bottom)
    } else {
        Arrangement.spacedBy(listGap, Alignment.Top)
    }
    Box(modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = s.md, vertical = s.xs),
            verticalArrangement = arrangement,
            modifier = Modifier.fillMaxSize(),
        ) {
            items(items, key = { it.key }, contentType = { "album_row" }) { item ->
                val defaultCountLabel = countLabel?.invoke(item.count)
                    ?: pluralStringResource(R.plurals.media_count_plural, item.count, item.count)
                AlbumListRow(
                    name = item.name,
                    subtitle = subtitle?.invoke(item) ?: defaultCountLabel,
                    coverPath = item.thumbnailPath.ifEmpty { item.previewPaths.firstOrNull() ?: "" },
                    selected = item.key in selectedKeys,
                    showChevron = true,
                    showThumbnail = viewSettings.showFolderThumbnails,
                    roundedCorners = viewSettings.roundedCorners,
                    containerColor = containerColor,
                    compact = viewSettings.listDensity == ListDensity.COMPACT,
                    badgeSubtitle = subtitle == null,
                    pathHint = pathHint?.invoke(item),
                    imageCount = item.imageCount,
                    videoCount = item.videoCount,
                    modifier = Modifier.clickableItem(item, onClick, onLongClick),
                )
            }
        }
        FastScrollBar(
            state = listState,
            modifier = Modifier.align(Alignment.CenterEnd).padding(vertical = 8.dp),
            positionLabel = { index -> fastScrollLabel?.invoke(index) },
        )
    }
}

@Composable
private fun LibraryAlbumTiles(
    items: List<AlbumGridItem>,
    viewSettings: ViewSettings,
    containerColor: Color,
    onClick: (AlbumGridItem) -> Unit,
    onLongClick: ((AlbumGridItem) -> Unit)?,
    countLabel: ((Int) -> String)?,
    selectedKeys: Set<String>,
    subtitle: ((AlbumGridItem) -> String)?,
    showCollectionStacks: Boolean,
    pathHint: ((AlbumGridItem) -> String?)?,
    tabIndex: Int?,
    fastScrollLabel: ((Int) -> String?)?,
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyGridState()
    ScrollToTopEffect(tabIndex) { gridState.animateScrollToItem(0) }
    val itemSpacing = viewSettings.spacing.dp
    val effectiveColumns = rememberEffectiveColumnCount(viewSettings.columnCount)
    val containerWidthDp = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
    val tileWidthDp = containerWidthDp / effectiveColumns.coerceAtLeast(1)
    val isNarrowTile = tileWidthDp < 88.dp
    val isUltraNarrowTile = tileWidthDp < 84.dp
    // Surface the path hint on grid tiles only when two visible folders share a name (e.g.
    // two "Instagram" or "Screenshots" folders) so single-instance tiles stay uncluttered.
    val duplicateNames = remember(items) {
        items.groupingBy { it.name.lowercase() }.eachCount().filterValues { it > 1 }.keys
    }
    Box(modifier.fillMaxSize()) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(effectiveColumns),
            contentPadding = PaddingValues(itemSpacing / 2),
            verticalArrangement = if (viewSettings.anchorBottom) Arrangement.Bottom else Arrangement.Top,
            modifier = Modifier.fillMaxSize(),
        ) {
            items(items, key = { it.key }, contentType = { "album_tile" }) { item ->
                val defaultCountLabel = countLabel?.invoke(item.count)
                    ?: pluralStringResource(R.plurals.media_count_plural, item.count, item.count)
                val resolvedSubtitle = subtitle?.invoke(item) ?: defaultCountLabel
                val disambiguationHint = if (item.name.lowercase() in duplicateNames) {
                    pathHint?.invoke(item)
                } else {
                    null
                }
                val description = buildString {
                    append(item.name).append(", ").append(resolvedSubtitle)
                    if (!disambiguationHint.isNullOrEmpty()) append(", ").append(disambiguationHint)
                }
                Box(
                    Modifier
                        .padding(itemSpacing / 2)
                        .clickableItem(item, onClick, onLongClick)
                        .semantics(mergeDescendants = true) {
                            contentDescription = description
                            role = Role.Button
                            selected = item.key in selectedKeys
                        },
                ) {
                    FolderTile(
                        name = item.name,
                        thumbnailPath = item.thumbnailPath,
                        previewPaths = item.previewPaths,
                        isCollection = showCollectionStacks,
                        showThumbnail = viewSettings.showFolderThumbnails,
                        roundedCorners = viewSettings.roundedCorners,
                        containerColor = containerColor,
                        subtitle = resolvedSubtitle,
                        pathHint = disambiguationHint,
                        imageCount = item.imageCount,
                        videoCount = item.videoCount,
                        isNarrow = isNarrowTile,
                        isUltraNarrow = isUltraNarrowTile,
                    )
                    if (item.key in selectedKeys) {
                        FolderSelectionOverlay(
                            roundedCorners = viewSettings.roundedCorners,
                            modifier = Modifier.matchParentSize(),
                            isNarrow = isNarrowTile,
                        )
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

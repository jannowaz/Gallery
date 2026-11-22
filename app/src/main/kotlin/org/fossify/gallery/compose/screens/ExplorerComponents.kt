package org.fossify.gallery.compose.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fossify.gallery.R
import org.fossify.gallery.compose.components.GroupToggleButton
import org.fossify.gallery.compose.components.ItemCountBadge
import org.fossify.gallery.compose.components.MediaTile
import org.fossify.gallery.compose.theme.Radius
import org.fossify.gallery.compose.util.ThumbnailOverlayState
import org.fossify.gallery.compose.util.decodeImageAspect
import org.fossify.gallery.helpers.TYPE_VIDEOS
import org.fossify.gallery.helpers.VIDEO_EXTENSIONS
import java.text.NumberFormat

private const val MIN_MOSAIC_ASPECT = 0.3f
private const val MAX_MOSAIC_ASPECT = 3f

private fun ExplorerItem.isVideoItem(): Boolean =
    type == TYPE_VIDEOS || path.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS

private fun distributeMosaicColumns(
    items: List<ExplorerItem>,
    columnCount: Int,
    aspectCache: Map<String, Float>,
): List<List<ExplorerItem>> {
    val heights = FloatArray(columnCount)
    val buckets = Array(columnCount) { mutableListOf<ExplorerItem>() }
    items.forEach { item ->
        val aspectRatio = if (item.isVideoItem()) 1f else (aspectCache[item.path] ?: 1f)
        val column = (0 until columnCount).minByOrNull { heights[it] } ?: 0
        buckets[column].add(item)
        heights[column] += 1f / aspectRatio.coerceIn(MIN_MOSAIC_ASPECT, MAX_MOSAIC_ASPECT)
    }
    return buckets.map { it.toList() }
}

@Composable
internal fun ExplorerMosaicChunk(
    items: List<ExplorerItem>,
    tagsByPath: Map<String, List<String>>,
    columnCount: Int,
    spacing: Dp,
    cornerShape: Shape,
    displayMode: DisplayMode,
    showFileNames: Boolean,
    hasFileSelection: Boolean,
    selectedFilePaths: Set<String>,
    onClick: (ExplorerItem) -> Unit,
    onLongClick: (ExplorerItem) -> Unit,
    onTagClick: (String) -> Unit,
    onPreviewClick: (ExplorerItem) -> Unit,
) {
    val aspectCache = remember { mutableStateMapOf<String, Float>() }
    val imagePaths = remember(items) {
        items.asSequence().filterNot(ExplorerItem::isVideoItem).map { it.path }.toList()
    }
    LaunchedEffect(imagePaths) {
        val missing = imagePaths.filterNot(aspectCache::containsKey)
        if (missing.isNotEmpty()) {
            aspectCache.putAll(withContext(Dispatchers.IO) { missing.associateWith(::decodeImageAspect) })
        }
    }
    if (imagePaths.any { it !in aspectCache }) {
        ExplorerMosaicChunkPlaceholder(columnCount = columnCount, spacing = spacing)
        return
    }
    val columns = remember(items, columnCount, aspectCache.size) {
        distributeMosaicColumns(items, columnCount, aspectCache)
    }
    val mediaBackground = when (displayMode) {
        DisplayMode.DARK -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.surface
    }
    val containerWidthDp = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
    val isNarrowTile = (containerWidthDp / columnCount.coerceAtLeast(1)) < 88.dp

    Row(Modifier.fillMaxWidth().padding(spacing / 2)) {
        columns.forEach { column ->
            Column(Modifier.weight(1f)) {
                column.forEach { item ->
                    key(item.path) {
                        ExplorerMosaicTile(
                            item = item,
                            aspectRatio = (if (item.isVideoItem()) 1f else (aspectCache[item.path] ?: 1f))
                                .coerceIn(MIN_MOSAIC_ASPECT, MAX_MOSAIC_ASPECT),
                            tags = tagsByPath[item.path].orEmpty(),
                            spacing = spacing,
                            cornerShape = cornerShape,
                            mediaBackground = mediaBackground,
                            showFileNames = showFileNames,
                            hasFileSelection = hasFileSelection,
                            isSelected = item.path in selectedFilePaths,
                            isNarrowTile = isNarrowTile,
                            onClick = { onClick(item) },
                            onLongClick = { onLongClick(item) },
                            onTagClick = onTagClick,
                            onPreviewClick = { onPreviewClick(item) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExplorerMosaicTile(
    item: ExplorerItem,
    aspectRatio: Float,
    tags: List<String>,
    spacing: Dp,
    cornerShape: Shape,
    mediaBackground: Color,
    showFileNames: Boolean,
    hasFileSelection: Boolean,
    isSelected: Boolean,
    isNarrowTile: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onTagClick: (String) -> Unit,
    onPreviewClick: () -> Unit,
) {
    val isVideo = item.isVideoItem()
    val medium = remember(item) { item.toMediumForGrouping() }
    Box(Modifier.fillMaxWidth()) {
        MediaTile(
            medium = medium,
            isVideo = isVideo,
            isSelected = isSelected,
            isSelectionMode = hasFileSelection,
            hasTag = tags.isNotEmpty(),
            tags = tags,
            onTagClick = if (!hasFileSelection) onTagClick else null,
            showOverlays = true,
            aspectRatio = aspectRatio,
            cornerShape = cornerShape,
            cardColor = mediaBackground,
            itemSpacing = spacing,
            showFileName = showFileNames,
            showVideoDuration = ThumbnailOverlayState.showVideoDuration,
            showRating = ThumbnailOverlayState.showRating,
            markFavorite = true,
            isNarrowTile = isNarrowTile,
            onClick = onClick,
            onLongClick = onLongClick,
            onSwipeToSelect = {},
            onBoundsChanged = {},
        )
        if (hasFileSelection) {
            IconButton(
                onClick = onPreviewClick,
                modifier = Modifier.align(Alignment.TopEnd).size(48.dp),
            ) {
                Icon(
                    Icons.Default.ZoomIn,
                    stringResource(R.string.cd_preview),
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/** A bounded Explorer mosaic chunk is laid out only after its image bounds are known. */
@Composable
internal fun ExplorerMosaicChunkPlaceholder(
    columnCount: Int,
    spacing: Dp,
) {
    Row(Modifier.fillMaxWidth()) {
        val ratios = listOf(0.76f, 1.36f, 0.91f, 1.48f)
        repeat(columnCount.coerceAtLeast(1)) { index ->
            Column(Modifier.weight(1f).padding(spacing / 2)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(ratios[index % ratios.size])
                        .clip(RoundedCornerShape(Radius.sm))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                )
            }
        }
    }
}

@Composable
internal fun ExplorerSectionHeader(
    label: String,
    count: Int,
    showGroupToggle: Boolean = false,
    groupsExpanded: Boolean = true,
    onToggleGroups: () -> Unit = {},
) {
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
        shape = RoundedCornerShape(Radius.md),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.weight(1f))
            ItemCountBadge(NumberFormat.getIntegerInstance().format(count))
            if (showGroupToggle) {
                GroupToggleButton(expanded = groupsExpanded, onToggle = onToggleGroups, modifier = Modifier.size(48.dp))
            }
        }
    }
}

package org.fossify.gallery.compose.screens
import org.fossify.gallery.compose.theme.Radius
import org.fossify.gallery.compose.util.rememberEffectiveColumnCount

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun MediaSkeleton(
    columns: Int = 4,
    itemCount: Int = 12,
    modifier: Modifier = Modifier,
    viewType: ViewType = ViewType.GRID,
    itemSpacing: Dp = 8.dp,
    showFileName: Boolean = true,
    reserveGridCaptionSpace: Boolean = false,
    listDensity: ListDensity = ListDensity.COMFORTABLE,
) {
    val effectiveColumns = rememberEffectiveColumnCount(columns)
    val transition = rememberInfiniteTransition(label = "skeleton")
    val shimmerOffset by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer",
    )
    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f),
            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.72f),
            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f),
        ),
        start = Offset(shimmerOffset * 1000f, 0f),
        end = Offset((shimmerOffset + 1f) * 1000f, 0f),
    )

    when (viewType) {
        ViewType.LIST -> {
            LazyColumn(modifier = modifier) {
                items(itemCount) {
                    MediaListSkeletonRow(
                        shimmerBrush = shimmerBrush,
                        compact = listDensity == ListDensity.COMPACT,
                    )
                }
            }
        }
        ViewType.MOSAIC -> {
            // Use varied neutral tile heights from the outset. A square shimmer followed by a
            // staggered result is a layout jump just as noticeable as a real image first being
            // drawn square. These ratios are deliberately stable placeholders, not guesses about
            // individual images.
            val ratios = listOf(0.72f, 1.35f, 0.92f, 1.55f, 0.84f, 1.18f, 0.66f, 1.42f)
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(effectiveColumns),
                modifier = modifier,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(itemSpacing / 2),
            ) {
                items(itemCount) { index ->
                    MosaicSkeletonTile(
                        aspectRatio = ratios[index % ratios.size],
                        shimmerBrush = shimmerBrush,
                        itemSpacing = itemSpacing,
                        showFileName = showFileName,
                    )
                }
            }
        }
        ViewType.GRID -> {
            LazyVerticalGrid(
                columns = GridCells.Fixed(effectiveColumns),
                modifier = modifier,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(itemSpacing / 2),
            ) {
                items(itemCount) {
                    GridSkeletonTile(
                        shimmerBrush = shimmerBrush,
                        itemSpacing = itemSpacing,
                        showFileName = showFileName,
                        reserveGridCaptionSpace = reserveGridCaptionSpace,
                    )
                }
            }
        }
    }
}

/** Loading shape for Albums, Collections and tag containers. It follows the folder-preview switch
 * and the selected list/grid layout instead of briefly pretending that every container is media. */
@Composable
fun FolderSkeleton(
    viewSettings: ViewSettings,
    itemCount: Int = 12,
    modifier: Modifier = Modifier,
) {
    val effectiveColumns = rememberEffectiveColumnCount(viewSettings.columnCount)
    val itemSpacing = viewSettings.spacing.dp
    val transition = rememberInfiniteTransition(label = "folderSkeleton")
    val shimmerOffset by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "folderShimmer",
    )
    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f),
            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.72f),
            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f),
        ),
        start = Offset(shimmerOffset * 1000f, 0f),
        end = Offset((shimmerOffset + 1f) * 1000f, 0f),
    )

    if (viewSettings.viewType == ViewType.LIST) {
        LazyColumn(modifier = modifier) {
            items(itemCount) {
                FolderListSkeletonRow(
                    shimmerBrush = shimmerBrush,
                    showThumbnail = viewSettings.showFolderThumbnails,
                    compact = viewSettings.listDensity == ListDensity.COMPACT,
                )
            }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(effectiveColumns),
            modifier = modifier,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(itemSpacing / 2),
        ) {
            items(itemCount) {
                FolderGridSkeletonTile(
                    shimmerBrush = shimmerBrush,
                    itemSpacing = itemSpacing,
                    showThumbnail = viewSettings.showFolderThumbnails,
                )
            }
        }
    }
}

@Composable
private fun GridSkeletonTile(
    shimmerBrush: Brush,
    itemSpacing: Dp,
    showFileName: Boolean,
    reserveGridCaptionSpace: Boolean,
) {
    Column(Modifier.padding(itemSpacing / 2)) {
        Box(
            Modifier
                .aspectRatio(1f)
                .clip(RoundedCornerShape(Radius.sm))
                .background(shimmerBrush),
        )
        if (showFileName) {
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier
                    .fillMaxWidth(0.7f)
                    .height(10.dp)
                    .clip(RoundedCornerShape(Radius.xs))
                    .background(shimmerBrush),
            )
            if (reserveGridCaptionSpace) {
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .fillMaxWidth(0.48f)
                        .height(22.dp)
                        .clip(RoundedCornerShape(Radius.xs))
                        .background(shimmerBrush),
                )
            }
        }
    }
}

@Composable
private fun MosaicSkeletonTile(
    aspectRatio: Float,
    shimmerBrush: Brush,
    itemSpacing: Dp,
    showFileName: Boolean,
) {
    Column(Modifier.padding(itemSpacing / 2)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio)
                .clip(RoundedCornerShape(Radius.sm))
                .background(shimmerBrush),
        )
        if (showFileName) {
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier
                    .fillMaxWidth(0.7f)
                    .height(10.dp)
                    .clip(RoundedCornerShape(Radius.xs))
                    .background(shimmerBrush),
            )
        }
    }
}

@Composable
private fun MediaListSkeletonRow(
    shimmerBrush: Brush,
    compact: Boolean,
) {
    val thumbnailSize = if (compact) 48.dp else 56.dp
    val verticalPadding = if (compact) 5.dp else 8.dp
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = verticalPadding),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(thumbnailSize)
                .clip(RoundedCornerShape(Radius.sm))
                .background(shimmerBrush),
        )
        androidx.compose.foundation.layout.Spacer(Modifier.size(if (compact) 10.dp else 12.dp, 1.dp))
        Column(Modifier.weight(1f)) {
            Box(
                Modifier
                    .fillMaxWidth(0.72f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(Radius.xs))
                    .background(shimmerBrush),
            )
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth(0.42f)
                    .height(10.dp)
                    .clip(RoundedCornerShape(Radius.xs))
                    .background(shimmerBrush),
            )
        }
    }
}

@Composable
private fun FolderGridSkeletonTile(
    shimmerBrush: Brush,
    itemSpacing: Dp,
    showThumbnail: Boolean,
) {
    Column(Modifier.padding(itemSpacing / 2)) {
        if (showThumbnail) {
            Box(
                Modifier
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(Radius.sm))
                    .background(shimmerBrush),
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .height(if (showThumbnail) 64.dp else 72.dp)
                .padding(horizontal = 8.dp, vertical = 10.dp),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(0.72f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(Radius.xs))
                    .background(shimmerBrush),
            )
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .fillMaxWidth(0.46f)
                    .height(10.dp)
                    .clip(RoundedCornerShape(Radius.xs))
                    .background(shimmerBrush),
            )
        }
    }
}

@Composable
private fun FolderListSkeletonRow(
    shimmerBrush: Brush,
    showThumbnail: Boolean,
    compact: Boolean,
) {
    val thumbnailSize = if (compact) 44.dp else 56.dp
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = if (compact) 12.dp else 16.dp, vertical = if (compact) 5.dp else 8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        if (showThumbnail) {
            Box(
                Modifier
                    .size(thumbnailSize)
                    .clip(RoundedCornerShape(Radius.sm))
                    .background(shimmerBrush),
            )
            androidx.compose.foundation.layout.Spacer(Modifier.size(if (compact) 10.dp else 12.dp, 1.dp))
        }
        Column(Modifier.weight(1f)) {
            Box(
                Modifier
                    .fillMaxWidth(if (showThumbnail) 0.7f else 0.48f)
                    .height(13.dp)
                    .clip(RoundedCornerShape(Radius.xs))
                    .background(shimmerBrush),
            )
            Spacer(Modifier.height(9.dp))
            Box(
                Modifier
                    .fillMaxWidth(if (showThumbnail) 0.42f else 0.3f)
                    .height(10.dp)
                    .clip(RoundedCornerShape(Radius.xs))
                    .background(shimmerBrush),
            )
        }
    }
}

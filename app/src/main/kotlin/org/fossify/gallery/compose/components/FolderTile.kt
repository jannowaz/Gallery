package org.fossify.gallery.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.fossify.gallery.compose.screens.VideoThumbnail
import org.fossify.gallery.compose.theme.Radius
import org.fossify.gallery.compose.theme.Scrim
import org.fossify.gallery.compose.theme.SelectionVisual
import org.fossify.gallery.helpers.VIDEO_EXTENSIONS

@Composable
fun FolderTile(
    name: String,
    thumbnailPath: String,
    previewPaths: List<String> = emptyList(),
    isCollection: Boolean = false,
    showThumbnail: Boolean,
    modifier: Modifier = Modifier,
    roundedCorners: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    subtitle: String? = null,
    pathHint: String? = null,
    imageCount: Int = 0,
    videoCount: Int = 0,
    isNarrow: Boolean = false,
    isUltraNarrow: Boolean = false,
) {
    val shape = if (roundedCorners) RoundedCornerShape(Radius.sm) else RoundedCornerShape(0.dp)
    val thumbnails = remember(thumbnailPath, previewPaths, showThumbnail) {
        if (!showThumbnail) emptyList()
        else (listOf(thumbnailPath) + previewPaths).filter { it.isNotEmpty() }.distinct().take(4)
    }

    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(containerColor),
    ) {
        if (showThumbnail) {
            FolderTileCover(
                thumbnails = thumbnails,
                isCollection = isCollection,
                containerColor = containerColor,
                subtitle = subtitle,
                imageCount = imageCount,
                videoCount = videoCount,
                isNarrow = isNarrow,
                isUltraNarrow = isUltraNarrow,
            )
        }
        FolderTileFooter(
            name = name,
            pathHint = pathHint,
            showThumbnail = showThumbnail,
            subtitle = subtitle,
            imageCount = imageCount,
            videoCount = videoCount,
            isNarrow = isNarrow,
            isUltraNarrow = isUltraNarrow,
        )
    }
}

@Composable
private fun FolderTileCover(
    thumbnails: List<String>,
    isCollection: Boolean,
    containerColor: Color,
    subtitle: String?,
    imageCount: Int,
    videoCount: Int,
    isNarrow: Boolean,
    isUltraNarrow: Boolean,
) {
    Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
        when {
            isCollection && thumbnails.size >= 2 -> {
                CollectionCollage(
                    paths = thumbnails,
                    dividerColor = containerColor,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            thumbnails.isNotEmpty() -> {
                FolderMediaCover(path = thumbnails.first(), modifier = Modifier.fillMaxSize())
            }
            else -> {
                Box(
                    Modifier.fillMaxSize().background(containerColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(if (isNarrow) 24.dp else 32.dp),
                    )
                }
            }
        }

        // Place media counts cleanly on the bottom-end corner of the cover so the footer
        // stays slim and photography remains the visual hero across 2–6 columns.
        if (imageCount > 0 || videoCount > 0 || !subtitle.isNullOrEmpty()) {
            FolderCountScrimPill(
                imageCount = imageCount,
                videoCount = videoCount,
                subtitle = subtitle,
                isNarrow = isNarrow,
                isUltraNarrow = isUltraNarrow,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(if (isNarrow) 4.dp else 6.dp)
                    .clearAndSetSemantics {},
            )
        }
    }
}

@Composable
private fun FolderTileFooter(
    name: String,
    pathHint: String?,
    showThumbnail: Boolean,
    subtitle: String?,
    imageCount: Int,
    videoCount: Int,
    isNarrow: Boolean,
    isUltraNarrow: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = if (isNarrow) 6.dp else 10.dp,
                vertical = if (isNarrow) 5.dp else 7.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = name,
            color = MaterialTheme.colorScheme.onSurface,
            style = if (isNarrow) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!pathHint.isNullOrEmpty() && !isNarrow) {
            Text(
                text = pathHint,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!showThumbnail) {
            if (imageCount > 0 || videoCount > 0) {
                MediaTypeCountSummary(imageCount, videoCount, compact = true, ultraNarrow = isUltraNarrow)
            } else if (subtitle != null) {
                ItemCountBadge(subtitle)
            }
        }
    }
}

/** Shared selection overlay for folder/album grid tiles — matches MediaTile's top-start check. */
@Composable
fun FolderSelectionOverlay(
    roundedCorners: Boolean,
    modifier: Modifier = Modifier,
    isNarrow: Boolean = false,
) {
    val shape = if (roundedCorners) RoundedCornerShape(Radius.sm) else RoundedCornerShape(0.dp)
    val checkSize = if (isNarrow) 18.dp else 24.dp
    Box(
        modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = SelectionVisual.overlayAlpha)),
    ) {
        Box(
            Modifier
                .align(Alignment.TopStart)
                .padding(if (isNarrow) 3.dp else 6.dp)
                .size(checkSize),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(checkSize - 6.dp).background(Color.White, CircleShape))
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(checkSize),
            )
        }
    }
}

@Composable
private fun FolderMediaCover(path: String, modifier: Modifier = Modifier) {
    val isVideo = path.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS
    if (isVideo) {
        VideoThumbnail(videoPath = path, modifier = modifier, contentScale = ContentScale.Crop)
    } else {
        GalleryImage(
            path = path,
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop,
            placeholderIconSize = 20.dp,
        )
    }
}

/** Crisp 2-, 3-, or 4-cell collage inside a square collection cover (replaces the clipped offset stack). */
@Composable
private fun CollectionCollage(
    paths: List<String>,
    dividerColor: Color,
    modifier: Modifier = Modifier,
) {
    val gap = 1.5.dp
    Box(modifier.background(dividerColor)) {
        when (paths.size) {
            2 -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                FolderMediaCover(paths[0], Modifier.weight(1f).fillMaxHeight())
                FolderMediaCover(paths[1], Modifier.weight(1f).fillMaxHeight())
            }
            3 -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                FolderMediaCover(paths[0], Modifier.weight(1.25f).fillMaxHeight())
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
                    FolderMediaCover(paths[1], Modifier.weight(1f).fillMaxWidth())
                    FolderMediaCover(paths[2], Modifier.weight(1f).fillMaxWidth())
                }
            }
            else -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    FolderMediaCover(paths[0], Modifier.weight(1f).fillMaxHeight())
                    FolderMediaCover(paths[1], Modifier.weight(1f).fillMaxHeight())
                }
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    FolderMediaCover(paths[2], Modifier.weight(1f).fillMaxHeight())
                    FolderMediaCover(paths[3], Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
}

@Composable
private fun FolderCountScrimPill(
    imageCount: Int,
    videoCount: Int,
    subtitle: String?,
    isNarrow: Boolean,
    isUltraNarrow: Boolean,
    modifier: Modifier = Modifier,
) {
    val fontSize = if (isNarrow) 9.sp else 11.sp
    val iconSize = if (isNarrow) 10.dp else 12.dp
    val showCombined = isUltraNarrow && imageCount > 0 && videoCount > 0
    Surface(
        modifier = modifier,
        color = Scrim.a60,
        contentColor = Color.White,
        shape = RoundedCornerShape(Radius.xs),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (isNarrow) 4.dp else 6.dp,
                vertical = if (isNarrow) 1.5.dp else 2.5.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(if (isNarrow) 3.dp else 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                showCombined -> ScrimCountText(formatCompactCount(imageCount + videoCount), fontSize)
                imageCount > 0 || videoCount > 0 -> {
                    if (imageCount > 0) {
                        ScrimCountItem(Icons.Default.PhotoLibrary, imageCount, isNarrow, iconSize, fontSize)
                    }
                    if (videoCount > 0) {
                        ScrimCountItem(Icons.Default.Videocam, videoCount, isNarrow, iconSize, fontSize)
                    }
                }
                !subtitle.isNullOrEmpty() -> ScrimCountText(subtitle, fontSize)
            }
        }
    }
}

@Composable
private fun ScrimCountItem(
    icon: ImageVector,
    count: Int,
    isNarrow: Boolean,
    iconSize: Dp,
    fontSize: TextUnit,
) {
    val label = if (isNarrow) formatCompactCount(count) else formatIntegerCount(count)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(iconSize))
        ScrimCountText(label, fontSize)
    }
}

@Composable
private fun ScrimCountText(text: String, fontSize: TextUnit) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontSize = fontSize,
        color = Color.White,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

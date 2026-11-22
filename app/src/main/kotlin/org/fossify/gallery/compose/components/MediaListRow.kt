package org.fossify.gallery.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.fossify.gallery.R
import org.fossify.gallery.compose.screens.VideoThumbnail
import org.fossify.gallery.compose.theme.BlurRadius
import org.fossify.gallery.compose.theme.FavoriteColor
import org.fossify.gallery.compose.theme.Radius
import org.fossify.gallery.compose.theme.RatingStarColor
import org.fossify.gallery.compose.util.BlurState
import org.fossify.gallery.compose.util.privacyBlur
import org.fossify.gallery.compose.util.selectableItem
import org.fossify.gallery.compose.util.throttledBoundsReporting
import org.fossify.gallery.models.Medium

/**
 * Single row used by the list view (paged browse, filtered/override media, and Explorer).
 */
@Composable
fun MediaListRow(
    medium: Medium,
    isVideo: Boolean,
    isSelected: Boolean,
    hasSelection: Boolean,
    cardColor: Color,
    fileSizeLabel: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onSwipeToSelect: () -> Unit,
    onBoundsChanged: (Rect) -> Unit,
    onBoundsRemoved: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    showRating: Boolean = true,
    showVideoDuration: Boolean = true,
    compact: Boolean = false,
    roundedCorners: Boolean = true,
    hasTag: Boolean = false,
    tags: List<String> = emptyList(),
    onTagClick: ((String) -> Unit)? = null,
    markFavorite: Boolean = true,
) {
    DisposableEffect(medium.path, hasSelection) {
        onDispose { onBoundsRemoved?.invoke() }
    }
    val rowShape = if (roundedCorners) RoundedCornerShape(Radius.sm) else RoundedCornerShape(0.dp)
    val durationText = remember(medium.videoDuration, isVideo, showVideoDuration) {
        if (showVideoDuration && isVideo && medium.videoDuration > 0) {
            "%02d:%02d".format(medium.videoDuration / 60, medium.videoDuration % 60)
        } else {
            ""
        }
    }
    val spokenMetadata = mediaListSpokenMetadata(
        medium = medium,
        isVideo = isVideo,
        fileSizeLabel = fileSizeLabel,
        markFavorite = markFavorite,
        hasTag = hasTag,
        tags = tags,
    )
    Surface(
        modifier = modifier.fillMaxWidth()
            .background(cardColor, rowShape)
            .throttledBoundsReporting(enabled = hasSelection, onBoundsChanged = onBoundsChanged)
            .selectableItem(
                isSelectionMode = hasSelection,
                onClick = onClick,
                onLongClick = onLongClick,
                onSwipeToSelect = onSwipeToSelect,
            )
            .semantics(mergeDescendants = true) {
                contentDescription = "${medium.name}, $spokenMetadata"
                role = Role.Button
                if (hasSelection) selected = isSelected
            },
        color = Color.Transparent,
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = if (compact) 5.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MediaListRowThumbnail(path = medium.path, isVideo = isVideo, compact = compact, shape = rowShape)
            Spacer(Modifier.width(if (compact) 10.dp else 12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    medium.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.privacyBlur(BlurRadius.thumbnail, BlurState.enabled),
                )
                MediaListRowMetadata(
                    fileSizeLabel = fileSizeLabel,
                    durationText = durationText,
                    rating = if (showRating) medium.rating else 0,
                    isFavorite = markFavorite && medium.isFavorite,
                    hasTag = hasTag,
                )
                if (tags.isNotEmpty()) {
                    TagChipRow(
                        tags = tags,
                        maxVisible = 3,
                        onTagClick = onTagClick,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            if (isSelected) {
                Icon(
                    Icons.Default.CheckCircle,
                    stringResource(R.string.cd_selected),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
    HorizontalDivider(
        Modifier.padding(start = if (compact) 70.dp else 76.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
    )
}

@Composable
private fun mediaListSpokenMetadata(
    medium: Medium,
    isVideo: Boolean,
    fileSizeLabel: String,
    markFavorite: Boolean,
    hasTag: Boolean,
    tags: List<String>,
): String = buildList {
    add(if (isVideo) stringResource(R.string.media_type_video) else stringResource(R.string.media_type_image))
    add(fileSizeLabel)
    if (medium.rating > 0) add(stringResource(R.string.filter_rating, medium.rating))
    if (markFavorite && medium.isFavorite) add(stringResource(R.string.favorite))
    if (hasTag) add(stringResource(R.string.action_tags))
    tags.take(3).forEach { add(it) }
}.joinToString(", ")

@Composable
private fun MediaListRowThumbnail(
    path: String,
    isVideo: Boolean,
    compact: Boolean,
    shape: Shape,
) {
    Box(Modifier.size(if (compact) 48.dp else 56.dp).clip(shape)) {
        val listThumbPx = 256
        if (isVideo) {
            VideoThumbnail(
                videoPath = path,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                thumbnailSize = listThumbPx,
            )
        } else {
            GalleryImage(
                path = path,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                placeholderIconSize = 18.dp,
                thumbnailSize = listThumbPx,
            )
        }
        if (isVideo) {
            VideoPlayOverlay(Modifier.align(Alignment.Center), size = if (compact) 20.dp else 24.dp)
        }
    }
}

@Composable
private fun MediaListRowMetadata(
    fileSizeLabel: String,
    durationText: String,
    rating: Int,
    isFavorite: Boolean,
    hasTag: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val summary = if (durationText.isNotEmpty()) "$fileSizeLabel · $durationText" else fileSizeLabel
        Text(
            summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (rating > 0) {
            Spacer(Modifier.width(6.dp))
            repeat(rating) {
                Icon(Icons.Default.Star, contentDescription = null, tint = RatingStarColor, modifier = Modifier.size(12.dp))
            }
        }
        if (isFavorite) {
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Default.Favorite, contentDescription = null, tint = FavoriteColor, modifier = Modifier.size(14.dp))
        }
        if (hasTag) {
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.AutoMirrored.Filled.Label,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

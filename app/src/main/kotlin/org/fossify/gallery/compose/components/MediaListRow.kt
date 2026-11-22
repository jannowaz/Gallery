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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.fossify.gallery.R
import org.fossify.gallery.compose.screens.VideoThumbnail
import org.fossify.gallery.compose.theme.BlurRadius
import org.fossify.gallery.compose.theme.Radius
import org.fossify.gallery.compose.theme.RatingStarColor
import org.fossify.gallery.compose.theme.FavoriteColor
import org.fossify.gallery.compose.theme.Scrim
import org.fossify.gallery.compose.util.BlurState
import org.fossify.gallery.compose.util.privacyBlur
import org.fossify.gallery.compose.util.selectableItem
import org.fossify.gallery.compose.util.throttledBoundsReporting
import org.fossify.gallery.models.Medium

/**
 * Single row used by the list view (both the paged, unfiltered browse and the filtered/override
 * path) - thumbnail, name/size and a selection checkmark. Kept in one place so the two call sites
 * in MediaScreen can't drift from each other.
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
    hasTag: Boolean = false,
    tags: List<String> = emptyList(),
    onTagClick: ((String) -> Unit)? = null,
    markFavorite: Boolean = true,
) {
    DisposableEffect(medium.path, hasSelection) {
        onDispose { onBoundsRemoved?.invoke() }
    }
    val durationText = remember(medium.videoDuration, isVideo, showVideoDuration) {
        if (showVideoDuration && isVideo && medium.videoDuration > 0)
            "%02d:%02d".format(medium.videoDuration / 60, medium.videoDuration % 60) else ""
    }
    val spokenMetadata = buildList {
        add(if (isVideo) stringResource(R.string.media_type_video) else stringResource(R.string.media_type_image))
        add(fileSizeLabel)
        if (medium.rating > 0) add(stringResource(R.string.filter_rating, medium.rating))
        if (markFavorite && medium.isFavorite) add(stringResource(R.string.favorite))
        if (hasTag) add(stringResource(R.string.action_tags))
        tags.take(3).forEach { add(it) }
    }.joinToString(", ")
    Surface(
        modifier = modifier.fillMaxWidth()
            .background(cardColor, RoundedCornerShape(Radius.sm))
            .throttledBoundsReporting(enabled = hasSelection, onBoundsChanged = onBoundsChanged)
            .selectableItem(isSelectionMode = hasSelection, onClick = onClick, onLongClick = onLongClick, onSwipeToSelect = onSwipeToSelect)
            .semantics(mergeDescendants = true) {
                contentDescription = "${medium.name}, $spokenMetadata"
                role = Role.Button
                if (hasSelection) selected = isSelected
            },
        color = Color.Transparent,
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = if (compact) 5.dp else 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(if (compact) 48.dp else 56.dp).clip(RoundedCornerShape(Radius.sm))) {
                // Decode to ~the 56dp slot's pixel size, not the 384px grid default: this row's
                // thumbnail is far smaller than a grid tile, so requesting 384px decoded ~2-3x more
                // pixels than are ever shown, wasting CPU (decode) and heap (each ARGB_8888 bitmap is
                // 56dp²-worth of pixels but was allocated at 384² = ~9x the bytes). 256 stays crisp on
                // the densest screens (56dp ≈ 196px @ 3.5x) while roughly halving the decode/memory cost
                // per row - a direct scroll-CPU/GC/thermal win in the list view.
                val listThumbPx = 256
                if (isVideo) VideoThumbnail(videoPath = medium.path, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop, thumbnailSize = listThumbPx)
                else GalleryImage(path = medium.path, contentDescription = medium.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop, placeholderIconSize = 18.dp, thumbnailSize = listThumbPx)
                // A play badge so videos are distinguishable from images at a glance - the grid
                // (MediaTile) always showed one, but the list row previously rendered a bare video
                // frame with no marker, so a still-looking frame was indistinguishable from a photo.
                if (isVideo) {
                    VideoPlayOverlay(Modifier.align(Alignment.Center), size = if (compact) 20.dp else 24.dp)
                }
            }
            Spacer(Modifier.width(if (compact) 10.dp else 12.dp))
            Column(Modifier.weight(1f)) {
                // See MediaTile's identical fix - privacy blur previously only covered the thumbnail
                // pixels, leaving the filename right next to it fully legible.
                Text(medium.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.privacyBlur(BlurRadius.thumbnail, BlurState.enabled))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val metadataSummary = if (durationText.isNotEmpty()) "$fileSizeLabel · $durationText" else fileSizeLabel
                    Text(metadataSummary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    // Rating inline with the size line (the list row has no thumbnail-overlay space for
                    // it the way the grid tile does), gated on the same "show rating" thumbnail-overlay
                    // setting so both views honour one toggle.
                    if (showRating && medium.rating > 0) {
                        Spacer(Modifier.width(6.dp))
                        repeat(medium.rating) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = RatingStarColor, modifier = Modifier.size(12.dp))
                        }
                    }
                    if (markFavorite && medium.isFavorite) {
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Default.Favorite, contentDescription = null, tint = FavoriteColor, modifier = Modifier.size(14.dp))
                    }
                    if (hasTag) {
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                    }
                }
                if (tags.isNotEmpty()) TagChipRow(
                    tags = tags,
                    maxVisible = 3,
                    onTagClick = onTagClick,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (isSelected) Icon(Icons.Default.CheckCircle, stringResource(R.string.cd_selected), tint = MaterialTheme.colorScheme.primary)
        }
    }
    HorizontalDivider(Modifier.padding(start = if (compact) 70.dp else 76.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
}

package org.fossify.gallery.compose.components
import org.fossify.gallery.compose.theme.Radius

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import org.fossify.gallery.compose.screens.VideoThumbnail
import org.fossify.gallery.compose.theme.AppMotion
import org.fossify.gallery.compose.theme.BlurRadius
import org.fossify.gallery.compose.theme.FavoriteColor
import org.fossify.gallery.compose.theme.RatingStarColor
import org.fossify.gallery.compose.theme.Scrim
import org.fossify.gallery.compose.theme.SelectionVisual
import org.fossify.gallery.compose.util.BlurState
import org.fossify.gallery.compose.util.pressScale
import org.fossify.gallery.compose.util.privacyBlur
import org.fossify.gallery.compose.util.selectableItem
import org.fossify.gallery.compose.util.sharedElementKey
import org.fossify.gallery.compose.util.throttledBoundsReporting
import org.fossify.gallery.helpers.TYPE_GIFS
import org.fossify.gallery.helpers.TYPE_RAWS
import org.fossify.gallery.helpers.TYPE_SVGS
import org.fossify.gallery.models.Medium
import org.fossify.gallery.R

/** Guards against negative padding crashes when a spring animation briefly overshoots below 0.dp. */
internal fun clampSelectionInset(inset: Dp): Dp = inset.coerceAtLeast(0.dp)

/**
 * Single media thumbnail used by both the grid and the mosaic layout.
 * Stateless apart from a small bounds-update throttle; all behaviour comes in via callbacks so the
 * tile is a focused, skippable recomposition scope.
 */
@Composable
fun MediaTile(
    medium: Medium,
    isVideo: Boolean,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    hasTag: Boolean,
    tags: List<String> = emptyList(),
    onTagClick: ((String) -> Unit)? = null,
    showOverlays: Boolean,
    aspectRatio: Float,
    cornerShape: Shape,
    cardColor: Color,
    itemSpacing: Dp,
    showFileName: Boolean,
    // A regular grid must keep each row on a common baseline. Masonry deliberately does not use
    // this, because its varying item heights are the point of that layout.
    reserveGridCaptionSpace: Boolean = false,
    showVideoDuration: Boolean,
    cropThumbnails: Boolean = true,
    showRating: Boolean = true,
    showFileType: Boolean = true,
    markFavorite: Boolean = true,
    isNarrowTile: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onSwipeToSelect: () -> Unit,
    onBoundsChanged: (Rect) -> Unit,
    onBoundsRemoved: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (isSelectionMode) {
        DisposableEffect(medium.path) {
            onDispose { onBoundsRemoved?.invoke() }
        }
    }
    // Keep normal scrolling free of per-tile Animatable/coroutine allocations; only animate the
    // selection inset while selection mode is actually active.
    val selectionInset = if (isSelectionMode) {
        val rawSelectionInset by animateDpAsState(
            targetValue = if (isSelected) (if (isNarrowTile) 4.dp else 6.dp) else 0.dp,
            animationSpec = AppMotion.selectionInsetSpring,
            label = "selectionInset",
        )
        // Low-bouncy spring can briefly overshoot below 0.dp when deselecting; padding() requires >= 0.dp.
        clampSelectionInset(rawSelectionInset)
    } else {
        0.dp
    }
    val thumbShape = if (isSelected || selectionInset > 0.dp) RoundedCornerShape(Radius.md) else cornerShape
    // Scroll state can change for every visible tile at once. Animating this alpha per tile creates
    // dozens of independent animation clocks and a coordinated fade when a fling starts/stops.
    // Keep the state change synchronous; the press scale uses a lazy Modifier.Node that allocates
    // zero animation state until a tile is actually touched.
    val overlayAlpha = if (showOverlays) 1f else 0f
    // The image is the primary content. Rating, tag and file-type badges are secondary metadata;
    // keep the default grid quiet and reveal them when the user has entered selection mode or
    // explicitly enabled file names/detail-oriented tiles.
    val showSecondaryMetadata = isSelectionMode || showFileName

    val durationText = remember(medium.videoDuration, isVideo, showVideoDuration) {
        if (showVideoDuration && isVideo && medium.videoDuration > 0)
            "%02d:%02d".format(medium.videoDuration / 60, medium.videoDuration % 60) else ""
    }
    val mediaTypeDescription = if (isVideo) stringResource(R.string.media_type_video) else stringResource(R.string.media_type_image)
    val ratingDescription = if (medium.rating > 0) stringResource(R.string.filter_rating, medium.rating) else null
    val favoriteDescription = if (markFavorite && medium.isFavorite) stringResource(R.string.favorite) else null
    val tagDescription = if (hasTag) stringResource(R.string.action_tags) else null
    val tileDescription = buildString {
        append(medium.name)
        append(", ").append(mediaTypeDescription)
        if (ratingDescription != null) append(", ").append(ratingDescription)
        if (favoriteDescription != null) append(", ").append(favoriteDescription)
        if (tagDescription != null) append(", ").append(tagDescription)
    }

    Column(
        modifier
            .padding(itemSpacing / 2)
            .pressScale()
            .background(cardColor, cornerShape)
            // Only report bounds while a selection is active - drag-select hit-testing is the sole
            // consumer, so this stays off during normal scrolling (see throttledBoundsReporting).
            .throttledBoundsReporting(enabled = isSelectionMode, onBoundsChanged = onBoundsChanged)
    ) {
        Box(
            Modifier.aspectRatio(aspectRatio)
                .clip(cornerShape)
                // Fit mode (cropThumbnails off) leaves empty space on two sides of the image inside
                // this still-square box - a solid black letterbox/pillarbox behind it, regardless of
                // the current theme, rather than whatever cardColor (surfaceVariant etc.) would
                // otherwise show through. While selected, reveal a tinted primaryContainer frame
                // around the inset thumbnail for unmistakable, tactile selection feedback.
                .then(
                    when {
                        isSelected || selectionInset > 0.dp ->
                            Modifier.background(MaterialTheme.colorScheme.primaryContainer)
                        !cropThumbnails -> Modifier.background(Color.Black)
                        else -> Modifier
                    },
                )
                .selectableItem(
                    isSelectionMode = isSelectionMode,
                    onClick = onClick,
                    onLongClick = onLongClick,
                    onSwipeToSelect = onSwipeToSelect,
                )
                .semantics {
                    // The image itself is intentionally descriptive, but the clickable tile also
                    // needs a single, stable announcement for TalkBack when the thumbnail is
                    // hidden behind a loading/error placeholder.
                    contentDescription = tileDescription
                    role = Role.Button
                    if (isSelectionMode) selected = isSelected
                }
        ) {
            val thumbScale = if (cropThumbnails) ContentScale.Crop else ContentScale.Fit
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(selectionInset)
                    .clip(thumbShape)
                    .then(if (!cropThumbnails) Modifier.background(Color.Black) else Modifier),
            ) {
                if (isVideo) {
                    VideoThumbnail(
                        videoPath = medium.path,
                        modifier = Modifier.fillMaxSize().sharedElementKey("media_${medium.path}"),
                        contentScale = thumbScale,
                    )
                } else {
                    GalleryImage(
                        path = medium.path,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().sharedElementKey("media_${medium.path}"),
                        contentScale = thumbScale,
                        placeholderIconSize = 16.dp,
                        backgroundColor = if (cropThumbnails) MaterialTheme.colorScheme.surfaceVariant else Color.Black,
                    )
                }
                if (isSelected) {
                    Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.primary.copy(alpha = SelectionVisual.overlayAlpha)))
                }
            }

            if (isVideo && durationText.isEmpty()) {
                VideoPlayOverlay(
                    Modifier.align(Alignment.Center),
                    size = if (isNarrowTile) 20.dp else 32.dp,
                )
            }

            // Keep the identity cues that matter while a fast scroll is in progress. Decorative
            // metadata can disappear temporarily, but a video duration and favorite marker should
            // not blink away and make the grid feel visually unstable.
            if (overlayAlpha > 0f || (isVideo && durationText.isNotEmpty()) || (markFavorite && medium.isFavorite)) {
                // Decorative metadata fades out while scrolling instead of disappearing in one
                // frame. Duration and favorite stay visible as stable identity cues.
                if (overlayAlpha > 0f) Box(Modifier.fillMaxSize().graphicsLayer { alpha = overlayAlpha }) {
                    if (showSecondaryMetadata && showRating && medium.rating > 0) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                            Surface(
                                modifier = Modifier.padding(bottom = 3.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                shape = RoundedCornerShape(Radius.xs),
                            ) {
                                Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)) {
                                // A compact numeric badge stays legible on 5–6 column grids where five
                                // individual stars would overflow into the duration badge.
                                    Text("★${medium.rating}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onTertiaryContainer, fontSize = if (isNarrowTile) 9.sp else 11.sp)
                                }
                            }
                        }
                    }
                    if (showSecondaryMetadata && hasTag) {
                        Surface(
                            modifier = Modifier.align(Alignment.TopEnd).padding(if (isNarrowTile) 2.dp else 4.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            shape = RoundedCornerShape(Radius.xs),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Label, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(horizontal = if (isNarrowTile) 2.dp else 4.dp, vertical = 1.dp).size(if (isNarrowTile) 9.dp else 10.dp))
                        }
                    }
                    if (showSecondaryMetadata && showFileType && (medium.type == TYPE_GIFS || medium.type == TYPE_RAWS || medium.type == TYPE_SVGS)) {
                        val typeLabel = stringResource(
                            when (medium.type) {
                                TYPE_GIFS -> R.string.gif
                                TYPE_RAWS -> R.string.raw
                                else -> R.string.svg
                            }
                        )
                        Surface(
                            modifier = Modifier.align(Alignment.TopStart).padding(if (isNarrowTile) 2.dp else 4.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            shape = RoundedCornerShape(Radius.xs),
                        ) {
                            Text(typeLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer, fontSize = if (isNarrowTile) 8.sp else 10.sp, modifier = Modifier.padding(horizontal = if (isNarrowTile) 3.dp else 4.dp, vertical = 1.dp))
                        }
                    }
                }
                if (isVideo && durationText.isNotEmpty()) {
                    Surface(
                        modifier = Modifier.align(Alignment.BottomEnd).padding(if (isNarrowTile) 2.dp else 4.dp),
                        color = MaterialTheme.colorScheme.inverseSurface,
                        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                        shape = RoundedCornerShape(Radius.xs),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier.padding(horizontal = if (isNarrowTile) 3.dp else 4.dp, vertical = 1.dp),
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.inverseOnSurface,
                                modifier = Modifier.size(if (isNarrowTile) 10.dp else 12.dp),
                            )
                            Text(
                                durationText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.inverseOnSurface,
                                fontSize = if (isNarrowTile) 9.sp else 11.sp,
                                lineHeight = if (isNarrowTile) 11.sp else 14.sp,
                            )
                        }
                    }
                }
                if (markFavorite && medium.isFavorite) {
                    Surface(
                        modifier = Modifier.align(Alignment.BottomStart).padding(if (isNarrowTile) 2.dp else 4.dp),
                        color = MaterialTheme.colorScheme.inverseSurface,
                        contentColor = FavoriteColor,
                        shape = RoundedCornerShape(Radius.xs),
                    ) {
                        Icon(Icons.Default.Favorite, null, tint = FavoriteColor, modifier = Modifier.padding(if (isNarrowTile) 2.dp else 3.dp).size(if (isNarrowTile) 9.dp else 11.dp))
                    }
                }
            }

            // Selection checkbox in the top-start corner — shown for every tile while selecting.
            if (isSelectionMode) {
                val checkSize = if (isNarrowTile) 18.dp else 24.dp
                Box(Modifier.align(Alignment.TopStart).padding(if (isNarrowTile) 2.dp else 4.dp).size(checkSize), contentAlignment = Alignment.Center) {
                    if (isSelected) {
                        Box(Modifier.size(checkSize - 6.dp).background(Color.White, CircleShape))
                        Icon(Icons.Default.CheckCircle, stringResource(R.string.cd_selected), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(checkSize))
                    } else {
                        Box(Modifier.matchParentSize().background(Scrim.a35, CircleShape))
                        Icon(Icons.Default.RadioButtonUnchecked, stringResource(R.string.cd_not_selected), tint = Color.White, modifier = Modifier.size(checkSize - 2.dp))
                    }
                }
            }
        }
        if (showFileName) {
            // Privacy blur only ever covered the thumbnail pixels (a real RenderEffect baked into
            // the frame - see PrivacyBlur.kt) - the filename sitting right next to it stayed fully
            // legible, defeating a fair amount of the point when "blur all media" is on to hide what
            // a file actually shows.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (reserveGridCaptionSpace) Modifier.heightIn(min = 20.dp) else Modifier),
            ) {
                Text(
                    medium.name,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp).privacyBlur(BlurRadius.thumbnail, BlurState.enabled),
                )
                if (tags.isNotEmpty()) {
                    TagChipRow(
                        tags = tags,
                        maxVisible = 2,
                        onTagClick = onTagClick,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

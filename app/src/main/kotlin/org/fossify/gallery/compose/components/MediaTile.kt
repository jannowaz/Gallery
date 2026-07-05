package org.fossify.gallery.compose.components
import org.fossify.gallery.compose.theme.Radius

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import org.fossify.gallery.compose.screens.VideoThumbnail
import org.fossify.gallery.compose.theme.AppMotion
import org.fossify.gallery.compose.theme.FavoriteColor
import org.fossify.gallery.compose.theme.RatingStarColor
import org.fossify.gallery.compose.theme.Scrim
import org.fossify.gallery.compose.util.selectableItem
import org.fossify.gallery.compose.util.sharedElementKey
import org.fossify.gallery.helpers.TYPE_GIFS
import org.fossify.gallery.helpers.TYPE_RAWS
import org.fossify.gallery.helpers.TYPE_SVGS
import org.fossify.gallery.models.Medium
import org.fossify.gallery.R

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
    showOverlays: Boolean,
    aspectRatio: Float,
    cornerShape: Shape,
    cardColor: Color,
    itemSpacing: Dp,
    showFileName: Boolean,
    showVideoDuration: Boolean,
    cropThumbnails: Boolean = true,
    showRating: Boolean = true,
    showFileType: Boolean = true,
    markFavorite: Boolean = true,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onSwipeToSelect: () -> Unit,
    onBoundsChanged: (Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.96f else 1f, animationSpec = AppMotion.short, label = "pressScale")

    var lastBoundsUpdate by remember { mutableLongStateOf(0L) }
    val durationText = remember(medium.videoDuration, isVideo, showVideoDuration) {
        if (showVideoDuration && isVideo && medium.videoDuration > 0)
            "%02d:%02d".format(medium.videoDuration / 60, medium.videoDuration % 60) else ""
    }

    Column(
        modifier
            .padding(itemSpacing / 2)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .background(cardColor, cornerShape)
            .onGloballyPositioned { coords ->
                val now = System.currentTimeMillis()
                if (now - lastBoundsUpdate > 300) {
                    lastBoundsUpdate = now
                    val p = coords.positionInWindow()
                    val s = coords.size
                    onBoundsChanged(Rect(p, Size(s.width.toFloat(), s.height.toFloat())))
                }
            }
    ) {
        Box(
            Modifier.aspectRatio(aspectRatio)
                .selectableItem(
                    isSelectionMode = isSelectionMode,
                    onClick = onClick,
                    onLongClick = onLongClick,
                    onSwipeToSelect = onSwipeToSelect,
                    interactionSource = interactionSource,
                )
                .semantics { if (isSelectionMode) selected = isSelected }
        ) {
            val thumbScale = if (cropThumbnails) ContentScale.Crop else ContentScale.Fit
            if (isVideo) {
                VideoThumbnail(videoPath = medium.path, modifier = Modifier.fillMaxSize().clip(cornerShape).sharedElementKey("media_${medium.path}"), contentScale = thumbScale)
            } else {
                GalleryImage(path = medium.path, contentDescription = medium.name, modifier = Modifier.fillMaxSize().clip(cornerShape).sharedElementKey("media_${medium.path}"), contentScale = thumbScale, placeholderIconSize = 16.dp)
            }

            if (isVideo) {
                Box(Modifier.align(Alignment.Center).size(32.dp).background(Scrim.a35, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.PlayArrow, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(22.dp))
                }
            }

            if (showOverlays) {
                if (showRating && medium.rating > 0) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                        Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.padding(bottom = 3.dp).background(Scrim.a40, RoundedCornerShape(Radius.xs)).padding(horizontal = 4.dp, vertical = 1.dp)) {
                            repeat(medium.rating) {
                                Icon(Icons.Default.Star, contentDescription = null, tint = RatingStarColor, modifier = Modifier.size(11.dp))
                            }
                        }
                    }
                }
                if (hasTag) {
                    Box(Modifier.align(Alignment.TopEnd).padding(4.dp).background(Scrim.a50, RoundedCornerShape(Radius.xs)).padding(horizontal = 4.dp, vertical = 1.dp)) {
                        Icon(Icons.Default.Label, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(10.dp))
                    }
                }
                if (isVideo && durationText.isNotEmpty()) {
                    Box(Modifier.align(Alignment.BottomEnd).padding(4.dp).background(Scrim.a60, RoundedCornerShape(Radius.xs)).padding(horizontal = 4.dp, vertical = 1.dp)) {
                        Text(durationText, style = MaterialTheme.typography.labelSmall, color = Color.White, fontSize = 11.sp)
                    }
                }
                if (markFavorite && medium.isFavorite) {
                    Box(Modifier.align(Alignment.BottomStart).padding(4.dp).background(Scrim.a50, RoundedCornerShape(Radius.xs)).padding(3.dp)) {
                        Icon(Icons.Default.Favorite, null, tint = FavoriteColor, modifier = Modifier.size(11.dp))
                    }
                }
                if (showFileType && !isSelectionMode && medium.type in intArrayOf(TYPE_GIFS, TYPE_RAWS, TYPE_SVGS)) {
                    val typeLabel = stringResource(
                        when (medium.type) {
                            TYPE_GIFS -> R.string.gif
                            TYPE_RAWS -> R.string.raw
                            else -> R.string.svg
                        }
                    )
                    Box(Modifier.align(Alignment.TopStart).padding(4.dp).background(Scrim.a50, RoundedCornerShape(Radius.xs)).padding(horizontal = 4.dp, vertical = 1.dp)) {
                        Text(typeLabel, style = MaterialTheme.typography.labelSmall, color = Color.White, fontSize = 10.sp)
                    }
                }
            }

            if (isSelected) {
                Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)))
            }
            // Selection checkbox in the top-start corner — shown for every tile while selecting.
            if (isSelectionMode) {
                Box(Modifier.align(Alignment.TopStart).padding(4.dp).size(24.dp), contentAlignment = Alignment.Center) {
                    if (isSelected) {
                        Box(Modifier.size(18.dp).background(Color.White, CircleShape))
                        Icon(Icons.Default.CheckCircle, stringResource(R.string.cd_selected), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    } else {
                        // a20 read as near-invisible behind bright photos (sky, snow, ...) - the
                        // white ring had almost nothing to contrast against. a35 keeps the badge
                        // legible on any thumbnail without turning it into a solid dark disc.
                        Box(Modifier.matchParentSize().background(Scrim.a35, CircleShape))
                        Icon(Icons.Default.RadioButtonUnchecked, stringResource(R.string.cd_not_selected), tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
        if (showFileName) {
            Text(medium.name, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

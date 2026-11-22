package org.fossify.gallery.compose.screens.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import org.fossify.gallery.compose.components.GalleryImage
import org.fossify.gallery.compose.theme.Scrim
import org.fossify.gallery.helpers.VIDEO_EXTENSIONS

private const val FILMSTRIP_THUMB_DECODE_PX = 96
private const val FILMSTRIP_UNSELECTED_ALPHA = 0.62f
private const val FILMSTRIP_CENTER_OFFSET = 3

/**
 * Compact thumbnail scrubber shown above the viewer action row when browsing multi-item sets.
 * Keeps thumbnail decode tiny ([FILMSTRIP_THUMB_DECODE_PX] px) so scrubbing stays smooth even
 * across large folders.
 */
@Composable
internal fun ViewerFilmstrip(
    items: List<String>,
    currentPage: Int,
    onSelectPage: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.size <= 1) return

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (currentPage - FILMSTRIP_CENTER_OFFSET).coerceAtLeast(0),
    )
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(currentPage) {
        val visible = listState.layoutInfo.visibleItemsInfo
        val isCentered = visible.any { it.index == currentPage }
        val target = (currentPage - FILMSTRIP_CENTER_OFFSET).coerceAtLeast(0)
        if (!isCentered || visible.size > 2) {
            listState.animateScrollToItem(target)
        }
    }

    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
    ) {
        items(
            count = items.size,
            key = { index -> "${index}_${items[index]}" },
            contentType = { "filmstrip_thumb" },
        ) { index ->
            val path = items[index]
            val selected = index == currentPage
            val isVideo = remember(path) {
                path.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS
            }
            val thumbSize = if (selected) 44.dp else 34.dp
            val shape = RoundedCornerShape(if (selected) 8.dp else 6.dp)

            Box(
                modifier = Modifier
                    .size(thumbSize)
                    .graphicsLayer { alpha = if (selected) 1f else FILMSTRIP_UNSELECTED_ALPHA }
                    .clip(shape)
                    .background(Scrim.a40)
                    .then(
                        if (selected) {
                            Modifier.border(2.dp, Color.White, shape)
                        } else {
                            Modifier
                        },
                    )
                    .clickable {
                        if (!selected) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelectPage(index)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                GalleryImage(
                    path = path,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    thumbnailSize = FILMSTRIP_THUMB_DECODE_PX,
                    placeholderIconSize = 14.dp,
                    backgroundColor = Scrim.a40,
                    modifier = Modifier.fillMaxSize(),
                )
                if (isVideo) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(Scrim.a60)
                            .padding(2.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

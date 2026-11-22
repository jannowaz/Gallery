package org.fossify.gallery.compose.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fossify.gallery.R
import org.fossify.gallery.compose.components.GalleryImage
import org.fossify.gallery.compose.components.GroupToggleButton
import org.fossify.gallery.compose.components.ItemCountBadge
import org.fossify.gallery.compose.components.TagChipRow
import org.fossify.gallery.compose.components.VideoPlayOverlay
import org.fossify.gallery.compose.theme.BlurRadius
import org.fossify.gallery.compose.theme.Radius
import org.fossify.gallery.compose.theme.Scrim
import org.fossify.gallery.compose.theme.SelectionVisual
import org.fossify.gallery.compose.util.BlurState
import org.fossify.gallery.compose.util.decodeImageAspect
import org.fossify.gallery.compose.util.privacyBlur
import org.fossify.gallery.compose.util.sharedElementKey
import org.fossify.gallery.helpers.VIDEO_EXTENSIONS
import java.text.NumberFormat

@OptIn(ExperimentalFoundationApi::class)
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
        items.asSequence()
            .filterNot { it.path.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS }
            .map { it.path }
            .toList()
    }
    LaunchedEffect(imagePaths) {
        val missing = imagePaths.filterNot(aspectCache::containsKey)
        if (missing.isNotEmpty()) {
            aspectCache.putAll(
                withContext(Dispatchers.IO) {
                    missing.associateWith(::decodeImageAspect)
                },
            )
        }
    }
    if (imagePaths.any { it !in aspectCache }) {
        ExplorerMosaicChunkPlaceholder(columnCount = columnCount, spacing = spacing)
        return
    }
    val columns = remember(items, columnCount, aspectCache.size) {
        val heights = FloatArray(columnCount)
        val buckets = Array(columnCount) { mutableListOf<ExplorerItem>() }
        items.forEach { item ->
            val isVideo = item.path.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS
            val aspectRatio = if (isVideo) 1f else (aspectCache[item.path] ?: 1f)
            val column = (0 until columnCount).minByOrNull { heights[it] } ?: 0
            buckets[column].add(item)
            heights[column] += 1f / aspectRatio.coerceIn(0.3f, 3f)
        }
        buckets.map { it.toList() }
    }
    val mediaBackground = when (displayMode) {
        DisplayMode.DARK -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.surface
    }

    Row(Modifier.fillMaxWidth().padding(spacing / 2)) {
        columns.forEach { column ->
            Column(Modifier.weight(1f)) {
                column.forEach { item ->
                    key(item.path) {
                        val isVideo = item.path.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS
                        if (!isVideo) {
                            LaunchedEffect(item.path) {
                                if (aspectCache[item.path] == null) {
                                    aspectCache[item.path] = withContext(Dispatchers.IO) { decodeImageAspect(item.path) }
                                }
                            }
                        }
                        val aspectRatio = (if (isVideo) 1f else (aspectCache[item.path] ?: 1f)).coerceIn(0.3f, 3f)
                        Column(Modifier.padding(spacing / 2)) {
                            Box(
                                Modifier.fillMaxWidth()
                                    .aspectRatio(aspectRatio)
                                    .clip(cornerShape)
                                    .background(mediaBackground, cornerShape)
                                    .combinedClickable(onClick = { onClick(item) }, onLongClick = { onLongClick(item) }),
                            ) {
                                if (isVideo) {
                                    VideoThumbnail(
                                        videoPath = item.path,
                                        modifier = Modifier.fillMaxSize().sharedElementKey("media_${item.path}"),
                                        contentScale = ContentScale.Crop,
                                    )
                                } else {
                                    GalleryImage(
                                        path = item.path,
                                        contentDescription = item.name,
                                        modifier = Modifier.fillMaxSize().sharedElementKey("media_${item.path}"),
                                        contentScale = ContentScale.Crop,
                                        placeholderIconSize = 20.dp,
                                    )
                                }
                                if (isVideo) {
                                    VideoPlayOverlay(Modifier.align(Alignment.Center))
                                }
                                if (hasFileSelection) {
                                    if (item.path in selectedFilePaths) {
                                        Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.primary.copy(alpha = SelectionVisual.overlayAlpha)))
                                    }
                                    Box(Modifier.align(Alignment.TopStart).padding(4.dp).size(24.dp), contentAlignment = Alignment.Center) {
                                        if (item.path in selectedFilePaths) {
                                            Box(Modifier.size(18.dp).background(Color.White, CircleShape))
                                            Icon(Icons.Default.CheckCircle, stringResource(R.string.cd_selected), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                        } else {
                                            Box(Modifier.matchParentSize().background(Scrim.a20, CircleShape))
                                            Icon(Icons.Default.RadioButtonUnchecked, stringResource(R.string.cd_not_selected), tint = Color.White, modifier = Modifier.size(22.dp))
                                        }
                                    }
                                    IconButton(
                                        onClick = { onPreviewClick(item) },
                                        modifier = Modifier.align(Alignment.TopEnd).size(48.dp),
                                    ) {
                                        Icon(Icons.Default.ZoomIn, stringResource(R.string.cd_preview), tint = Color.White, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                            if (showFileNames) {
                                Text(item.name, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp).privacyBlur(BlurRadius.thumbnail, BlurState.enabled))
                                TagChipRow(
                                    tags = tagsByPath[item.path].orEmpty(),
                                    maxVisible = 2,
                                    onTagClick = if (!hasFileSelection) onTagClick else null,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                        }
                    }
                }
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
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.weight(1f))
            ItemCountBadge(NumberFormat.getIntegerInstance().format(count))
            if (showGroupToggle) {
                GroupToggleButton(expanded = groupsExpanded, onToggle = onToggleGroups, modifier = Modifier.size(48.dp))
            }
        }
    }
}

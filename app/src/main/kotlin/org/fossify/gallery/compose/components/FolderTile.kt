package org.fossify.gallery.compose.components
import org.fossify.gallery.compose.theme.Radius

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import org.fossify.gallery.compose.screens.VideoThumbnail
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
    imageCount: Int = 0,
    videoCount: Int = 0,
    isNarrow: Boolean = false,
    isUltraNarrow: Boolean = false,
) {
    val shape = if (roundedCorners) RoundedCornerShape(Radius.sm) else RoundedCornerShape(0.dp)
    val thumbnails = remember(thumbnailPath, previewPaths, showThumbnail) {
        if (!showThumbnail) emptyList()
        else (listOf(thumbnailPath) + previewPaths).filter { it.isNotEmpty() }.distinct().take(3)
    }

    Column(modifier.fillMaxWidth()) {
            if (showThumbnail) {
                Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(shape)) {
                    if (thumbnails.isNotEmpty()) {
                        if (isCollection && thumbnails.size > 1) {
                            thumbnails.drop(1).asReversed().forEachIndexed { index, path ->
                                Box(
                                    Modifier.fillMaxSize()
                                        .offset(x = ((index + 1) * 5).dp, y = ((index + 1) * 4).dp)
                                        .clip(shape)
                                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                ) {
                                    val video = path.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS
                                    if (video) VideoThumbnail(path, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                    else GalleryImage(path, name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, placeholderIconSize = 20.dp)
                                }
                            }
                        }
                        val mainPath = thumbnails.first()
                        val isVideo = mainPath.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS
                        if (isVideo) {
                            VideoThumbnail(videoPath = mainPath, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        } else {
                            GalleryImage(
                                path = mainPath,
                                contentDescription = name,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                                placeholderIconSize = 20.dp,
                            )
                        }
                    } else {
                        Box(Modifier.fillMaxSize().background(containerColor))
                    }
                }
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = containerColor,
                shape = if (showThumbnail) {
                    RoundedCornerShape(
                        bottomStart = if (roundedCorners) Radius.sm else 0.dp,
                        bottomEnd = if (roundedCorners) Radius.sm else 0.dp,
                    )
                } else {
                    shape
                },
            ) {
                Column(
                    Modifier.padding(
                        horizontal = if (isNarrow) 5.dp else 8.dp,
                        vertical = if (isNarrow) 4.dp else 6.dp,
                    ),
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth().heightIn(min = if (isNarrow) 18.dp else 22.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            text = name,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = if (isNarrow) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Box(
                        modifier = Modifier.fillMaxWidth().heightIn(min = if (isNarrow) 20.dp else 24.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (imageCount > 0 || videoCount > 0) {
                            MediaTypeCountSummary(imageCount, videoCount, compact = true, ultraNarrow = isUltraNarrow)
                        } else if (subtitle != null) {
                            ItemCountBadge(subtitle)
                        }
                    }
                }
            }
        }
}

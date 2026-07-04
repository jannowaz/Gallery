package org.fossify.gallery.compose.components
import org.fossify.gallery.compose.theme.Radius
import org.fossify.gallery.compose.theme.Scrim

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.fossify.gallery.compose.screens.VideoThumbnail
import org.fossify.gallery.helpers.VIDEO_EXTENSIONS
import java.io.File

@Composable
fun FolderTile(
    name: String,
    thumbnailPath: String,
    showThumbnail: Boolean,
    modifier: Modifier = Modifier,
    roundedCorners: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    subtitle: String? = null,
) {
    val shape = if (roundedCorners) RoundedCornerShape(Radius.sm) else RoundedCornerShape(0.dp)

    Box(modifier.aspectRatio(1f).clip(shape)) {
        if (showThumbnail && thumbnailPath.isNotEmpty() && File(thumbnailPath).exists()) {
            val isVideo = thumbnailPath.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS
            if (isVideo) {
                VideoThumbnail(videoPath = thumbnailPath, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                GalleryImage(
                    path = thumbnailPath,
                    contentDescription = name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    placeholderIconSize = 20.dp,
                )
            }
            Box(Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0.55f to Color.Transparent,
                    1f to Scrim.a60,
                )
            ))
        } else {
            Box(Modifier.fillMaxSize().background(containerColor))
        }
        Column(Modifier.align(Alignment.BottomStart).padding(8.dp)) {
            val onThumb = showThumbnail && thumbnailPath.isNotEmpty()
            Text(
                text = name,
                color = if (onThumb) Color.White else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = if (onThumb) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

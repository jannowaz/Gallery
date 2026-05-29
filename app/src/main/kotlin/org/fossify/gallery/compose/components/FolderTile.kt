package org.fossify.gallery.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import org.fossify.gallery.compose.screens.VideoThumbnail
import java.io.File

private val videoExts = setOf("mp4", "mkv", "mov", "3gp", "wmv", "flv", "avi")

@Composable
fun FolderTile(
    name: String,
    thumbnailPath: String,
    showThumbnail: Boolean,
    modifier: Modifier = Modifier,
    roundedCorners: Boolean = true,
) {
    val ctx = LocalContext.current
    val shape = if (roundedCorners) RoundedCornerShape(8.dp) else RoundedCornerShape(0.dp)

    Box(modifier.aspectRatio(1f).clip(shape)) {
        if (showThumbnail && thumbnailPath.isNotEmpty() && File(thumbnailPath).exists()) {
            val isVideo = thumbnailPath.substringAfterLast('.', "").lowercase() in videoExts
            if (isVideo) {
                VideoThumbnail(videoPath = thumbnailPath, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                AsyncImage(
                    model = ImageRequest.Builder(ctx).data(android.net.Uri.fromFile(File(thumbnailPath))).crossfade(true).build(),
                    contentDescription = name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)))
        } else {
            Box(Modifier.fillMaxSize().background(
                Brush.linearGradient(listOf(
                    MaterialTheme.colorScheme.surfaceVariant,
                    MaterialTheme.colorScheme.surface
                ))
            ))
        }
        Text(
            text = name,
            modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
            color = if (showThumbnail && thumbnailPath.isNotEmpty()) Color.White else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

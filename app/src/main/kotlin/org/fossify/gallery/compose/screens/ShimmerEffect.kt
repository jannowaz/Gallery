package org.fossify.gallery.compose.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ShimmerBox(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.3f, targetValue = 0.7f,
        animationSpec = infiniteRepeatable(animation = tween(800, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "alpha"
    )
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)))
}

@Composable
fun VideoThumbnail(videoPath: String, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    val bitmap = remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(videoPath) {
        bitmap.value = withContext(Dispatchers.IO) {
            try { val r = android.media.MediaMetadataRetriever(); r.setDataSource(videoPath); val b = r.frameAtTime; r.release(); b } catch (e: Exception) { null }
        }
    }
    val bmp = bitmap.value
    if (bmp != null) {
        androidx.compose.foundation.Image(bitmap = bmp.asImageBitmap(), contentDescription = "Video", modifier = modifier, contentScale = contentScale)
    } else {
        Box(modifier.background(Color.Black.copy(alpha = 0.3f)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Videocam, "Video", tint = Color.White, modifier = Modifier.size(24.dp)) }
    }
}

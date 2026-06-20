package org.fossify.gallery.compose.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val videoThumbnailCache = object : LinkedHashMap<String, android.graphics.Bitmap?>(16, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, android.graphics.Bitmap?>?): Boolean {
        return size > 50
    }
}

@Composable
fun LoadingIndicator(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            modifier = Modifier.size(36.dp),
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 3.dp,
        )
    }
}

@Composable
fun VideoThumbnail(videoPath: String, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    var bitmap by remember(videoPath) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(videoPath) {
        val cached = synchronized(videoThumbnailCache) { videoThumbnailCache[videoPath] }
        if (cached != null) { bitmap = cached; return@LaunchedEffect }
        bitmap = withContext(Dispatchers.IO) {
            val r = android.media.MediaMetadataRetriever()
            try { r.setDataSource(videoPath); val bmp = r.frameAtTime; synchronized(videoThumbnailCache) { videoThumbnailCache[videoPath] = bmp }; bmp } catch (_: Exception) { null } finally { r.release() }
        }
    }
    val bmp = bitmap
    if (bmp != null) {
        androidx.compose.foundation.Image(bitmap = bmp.asImageBitmap(), contentDescription = "Video", modifier = modifier, contentScale = contentScale)
    } else {
        Box(modifier.background(Color.Black.copy(alpha = 0.3f)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Videocam, "Video", tint = Color.White, modifier = Modifier.size(24.dp)) }
    }
}

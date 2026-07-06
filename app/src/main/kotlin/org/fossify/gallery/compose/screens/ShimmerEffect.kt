package org.fossify.gallery.compose.screens

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import org.fossify.gallery.compose.theme.Scrim
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fossify.gallery.extensions.config

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
    val blurred = LocalContext.current.config.blurAllMedia
    var bitmap by remember(videoPath) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(videoPath) {
        val cached = synchronized(videoThumbnailCache) { videoThumbnailCache[videoPath] }
        if (cached != null) { bitmap = cached; return@LaunchedEffect }
        bitmap = withContext(Dispatchers.IO) {
            val r = android.media.MediaMetadataRetriever()
            try { r.setDataSource(videoPath); val bmp = r.frameAtTime; synchronized(videoThumbnailCache) { videoThumbnailCache[videoPath] = bmp }; bmp } catch (_: Exception) { null } finally { r.release() }
        }
    }
    // Crossfade instead of an instant swap - GalleryImage already crossfades via Coil for photos, so
    // this keeps videos from popping in abruptly and looking inconsistent in a mixed photo/video grid.
    Crossfade(targetState = bitmap, modifier = modifier, label = "videoThumbnail") { bmp ->
        if (bmp != null) {
            androidx.compose.foundation.Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Video",
                modifier = Modifier.fillMaxSize().let { if (blurred) it.blur(24.dp) else it },
                contentScale = contentScale,
            )
        } else {
            Box(Modifier.fillMaxSize().background(Scrim.a30), contentAlignment = Alignment.Center) { Icon(Icons.Default.Videocam, "Video", tint = Color.White, modifier = Modifier.size(24.dp)) }
        }
    }
}

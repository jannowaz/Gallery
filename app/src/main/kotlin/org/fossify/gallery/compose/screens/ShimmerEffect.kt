package org.fossify.gallery.compose.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import org.fossify.gallery.compose.theme.BlurRadius
import org.fossify.gallery.compose.theme.Scrim
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import org.fossify.gallery.compose.util.BlurState
import org.fossify.gallery.compose.util.privacyBlur
import java.io.File

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

// Routed through Coil's registered VideoFrameDecoder (see App.kt's ImageLoader) instead of a
// per-call MediaMetadataRetriever.frameAtTime() plus a hand-rolled 50-entry in-memory bitmap LRU.
// That old path decoded a full-resolution frame (tens of MB for a 4K clip) for every tile with no
// disk cache backing it, so scrolling a video-heavy folder repeatedly re-decoded the same frames
// from scratch - a major source of both jank and battery drain. Coil's decoder downsamples to the
// requested size and persists to its disk cache like any other image, matching GalleryImage's
// photo path.
@Composable
fun VideoThumbnail(videoPath: String, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop, thumbnailSize: Int? = 384) {
    val ctx = LocalContext.current
    var imageState by remember(videoPath) { mutableStateOf<AsyncImagePainter.State?>(null) }
    Box(modifier) {
        AsyncImage(
            model = ImageRequest.Builder(ctx)
                .data(Uri.fromFile(File(videoPath)))
                .crossfade(true)
                .apply { if (thumbnailSize != null) size(thumbnailSize, thumbnailSize) }
                .build(),
            contentDescription = "Video",
            modifier = Modifier.fillMaxSize().privacyBlur(BlurRadius.thumbnail, BlurState.enabled),
            contentScale = contentScale,
            onSuccess = { imageState = it },
            onError = { imageState = it },
            onLoading = { imageState = it },
        )
        if (imageState !is AsyncImagePainter.State.Success) {
            Box(Modifier.fillMaxSize().background(Scrim.a30), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Videocam, "Video", tint = Color.White, modifier = Modifier.size(24.dp))
            }
        }
    }
}

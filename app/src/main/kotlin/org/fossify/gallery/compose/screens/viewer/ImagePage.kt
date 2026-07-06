package org.fossify.gallery.compose.screens.viewer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fossify.gallery.R
import org.fossify.gallery.compose.util.sharedElementKey
import java.io.File

@Composable
fun ImagePage(
    path: String,
    file: File,
    onClose: () -> Unit = {},
    onToggleUi: () -> Unit = {},
    onZoomChange: (Boolean) -> Unit = {},
    isCurrentPage: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val zoom = rememberZoomState()
    // Unlike GalleryImage's grid thumbnails, a full-screen decode failure (corrupt file, HEIC on
    // an API <28 device without a HEIF codec, an unsupported RAW variant) used to leave the Viewer
    // showing nothing but a blank/black screen with no feedback at all.
    var imageState by remember(path) { mutableStateOf<AsyncImagePainter.State?>(null) }

    LaunchedEffect(isCurrentPage) { if (!isCurrentPage) zoom.reset() }
    LaunchedEffect(zoom.isZoomed, isCurrentPage) { if (isCurrentPage) onZoomChange(zoom.isZoomed) }
    LaunchedEffect(path) {
        val a = withContext(Dispatchers.IO) {
            try {
                val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFile(file.absolutePath, o)
                if (o.outWidth > 0 && o.outHeight > 0) o.outWidth.toFloat() / o.outHeight else 0f
            } catch (_: Exception) { 0f }
        }
        if (a > 0f) zoom.updateContentAspect(a)
    }

    Box(Modifier.fillMaxSize().clipToBounds().then(modifier)) {
        AsyncImage(
            model = ImageRequest.Builder(ctx).data(android.net.Uri.fromFile(file)).size(2560).crossfade(true).build(),
            contentDescription = file.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { }
                .sharedElementKey("media_$path")
                .graphicsLayer {
                    scaleX = zoom.scale; scaleY = zoom.scale
                    translationX = zoom.offset.x; translationY = zoom.offset.y
                }
                .zoomable(zoom, onSingleTap = onToggleUi),
            onSuccess = { imageState = it },
            onError = { imageState = it },
            onLoading = { imageState = it },
        )

        when (imageState) {
            is AsyncImagePainter.State.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.BrokenImage, file.name, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
                    Text(stringResource(R.string.error_loading_media), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
                }
            }
            is AsyncImagePainter.State.Success -> { }
            else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }

        ZoomMinimap(zoom, modifier = Modifier.align(Alignment.TopEnd).padding(top = 72.dp, end = 12.dp))
    }
}

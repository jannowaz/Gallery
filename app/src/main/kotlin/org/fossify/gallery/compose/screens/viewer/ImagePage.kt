package org.fossify.gallery.compose.screens.viewer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
            model = ImageRequest.Builder(ctx).data(android.net.Uri.fromFile(file)).crossfade(true).build(),
            contentDescription = file.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { }
                .graphicsLayer {
                    scaleX = zoom.scale; scaleY = zoom.scale
                    translationX = zoom.offset.x; translationY = zoom.offset.y
                }
                .zoomable(zoom, onSingleTap = onToggleUi),
        )

        ZoomMinimap(zoom, modifier = Modifier.align(Alignment.TopEnd).padding(top = 72.dp, end = 12.dp))
    }
}

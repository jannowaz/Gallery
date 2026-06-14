package org.fossify.gallery.compose.screens.viewer

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun ImagePage(
    path: String,
    file: File,
    onClose: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var contentScale by remember { mutableStateOf(ContentScale.Fit) }
    var willClose by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().clipToBounds().graphicsLayer {
        alpha = if (willClose) scale.coerceIn(0f, 0.65f).let { 1f - (0.65f - it) / 0.65f } else 1f
    }.then(modifier)) {
        AsyncImage(
            model = ImageRequest.Builder(ctx).data(android.net.Uri.fromFile(file)).crossfade(true).build(),
            contentDescription = file.name,
            contentScale = contentScale,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale; scaleY = scale
                translationX = offsetX; translationY = offsetY
            }.pointerInput(Unit) {
                val viewSize = this.size
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val changes = event.changes.filter { it.pressed }
                        if (changes.size > 1) {
                            val pts = changes.map { it.position }
                            val prevPts = changes.map { it.previousPosition }
                            val centroid = Offset(pts.sumOf { it.x.toDouble() }.toFloat() / pts.size, pts.sumOf { it.y.toDouble() }.toFloat() / pts.size)
                            val prevCentroid = Offset(prevPts.sumOf { it.x.toDouble() }.toFloat() / prevPts.size, prevPts.sumOf { it.y.toDouble() }.toFloat() / prevPts.size)
                            val curDist = pts.sumOf { (it - centroid).getDistance().toDouble() }.toFloat()
                            val prevDist = prevPts.sumOf { (it - prevCentroid).getDistance().toDouble() }.toFloat()
                            val zoom = if (prevDist > 0f) (curDist / prevDist) else 1f
                            val newScale = scale * zoom
                            val clamped = newScale.coerceIn(0.3f, 5f)
                            scale = clamped
                            willClose = clamped < 0.65f
                            if (clamped >= 1f) {
                                val pan = centroid - prevCentroid
                                val maxX = (scale - 1f) * viewSize.width / 2f
                                val maxY = (scale - 1f) * viewSize.height / 2f
                                offsetX = (offsetX + pan.x).coerceIn(-maxX, maxX)
                                offsetY = (offsetY + pan.y).coerceIn(-maxY, maxY)
                            }
                            changes.forEach { it.consume() }
                        } else if (changes.size == 1 && scale > 1f) {
                            val c = changes.first()
                            val pan = c.position - c.previousPosition
                            val maxX = (scale - 1f) * viewSize.width / 2f
                            val maxY = (scale - 1f) * viewSize.height / 2f
                            offsetX = (offsetX + pan.x).coerceIn(-maxX, maxX)
                            offsetY = (offsetY + pan.y).coerceIn(-maxY, maxY)
                            changes.forEach { it.consume() }
                        }
                    }
                }
            }
        )
        Box(Modifier.fillMaxSize().pointerInput(Unit) {
            detectTapGestures(onDoubleTap = {
                contentScale = if (contentScale == ContentScale.Fit) ContentScale.Crop else ContentScale.Fit
                scale = 1f; offsetX = 0f; offsetY = 0f; willClose = false
            })
        })
    }

    if (willClose) {
        LaunchedEffect(willClose) {
            delay(200)
            if (willClose) onClose()
        }
    }
}

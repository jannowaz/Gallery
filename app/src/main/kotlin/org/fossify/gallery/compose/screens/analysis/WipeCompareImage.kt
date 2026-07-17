package org.fossify.gallery.compose.screens.analysis

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import org.fossify.gallery.R
import org.fossify.gallery.compose.components.GalleryImage
import org.fossify.gallery.compose.screens.viewer.rememberZoomState
import org.fossify.gallery.compose.screens.viewer.zoomable
import org.fossify.gallery.compose.theme.Radius
import org.fossify.gallery.compose.theme.Scrim

/**
 * Side-by-side wipe comparison: both versions rendered on top of each other in the SAME zoom/pan
 * transform, with the before-image clipped in screen space to the left of a draggable divider - so
 * any crop can be pixel-compared by sliding the split across it, instead of flipping between two
 * full-screen states and trusting visual memory. The divider strip consumes its own drags, so it
 * coexists with both the pinch-zoom here and the decision swipe of the enclosing SwipeDecisionBox
 * (which only sees drags that start outside the strip).
 */
@Composable
internal fun WipeCompareImage(beforePath: String, afterPath: String, modifier: Modifier = Modifier) {
    val zoom = rememberZoomState()
    var fraction by remember { mutableFloatStateOf(0.5f) }
    var widthPx by remember { mutableIntStateOf(0) }

    Box(modifier.fillMaxSize().clipToBounds().onSizeChanged { widthPx = it.width }) {
        val imageModifier = Modifier.fillMaxSize().graphicsLayer {
            scaleX = zoom.scale; scaleY = zoom.scale
            translationX = zoom.offset.x; translationY = zoom.offset.y
        }
        GalleryImage(
            path = afterPath,
            contentDescription = stringResource(R.string.compare_after),
            modifier = imageModifier,
            contentScale = ContentScale.Fit,
            thumbnailSize = 2560,
            backgroundColor = MaterialTheme.colorScheme.background,
        )
        // Screen-space clip (not a layout clip on the transformed image) so the split line stays
        // exactly under the divider regardless of zoom/pan.
        Box(
            Modifier.fillMaxSize().drawWithContent {
                clipRect(right = size.width * fraction) { this@drawWithContent.drawContent() }
            }
        ) {
            GalleryImage(
                path = beforePath,
                contentDescription = stringResource(R.string.compare_before),
                modifier = imageModifier,
                contentScale = ContentScale.Fit,
                thumbnailSize = 2560,
                backgroundColor = MaterialTheme.colorScheme.background,
            )
        }

        // Pinch/pan for both layers at once; single taps have no job here.
        Box(Modifier.fillMaxSize().zoomable(zoom, onSingleTap = {}))

        // Corner labels naming the two sides.
        Surface(shape = RoundedCornerShape(Radius.sm), color = Scrim.a60, modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
            Text(stringResource(R.string.compare_before), color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
        }
        Surface(shape = RoundedCornerShape(Radius.sm), color = Scrim.a60, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
            Text(stringResource(R.string.compare_after), color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
        }

        // Divider: a 44dp-wide drag strip centered on the split, drawn as a thin line + handle.
        val stripWidth = 44.dp
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset { IntOffset((widthPx * fraction - stripWidth.toPx() / 2f).roundToInt(), 0) }
                .fillMaxHeight()
                .width(stripWidth)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, delta ->
                        change.consume()
                        if (widthPx > 0) fraction = (fraction + delta / widthPx).coerceIn(0.05f, 0.95f)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.fillMaxHeight().width(2.dp).background(Color.White.copy(alpha = 0.85f)))
            Surface(shape = CircleShape, color = Color.White, shadowElevation = 4.dp) {
                Row(Modifier.padding(horizontal = 2.dp, vertical = 6.dp)) {
                    Icon(Icons.Default.ChevronLeft, null, tint = Color.Black, modifier = Modifier.width(14.dp))
                    Icon(Icons.Default.ChevronRight, null, tint = Color.Black, modifier = Modifier.width(14.dp))
                }
            }
        }
    }
}

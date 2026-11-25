package org.fossify.gallery.compose.components

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.decode.BitmapFactoryDecoder
import coil3.request.ImageRequest
import coil3.request.bitmapConfig
import coil3.request.crossfade
import org.fossify.gallery.R
import org.fossify.gallery.compose.theme.BlurRadius
import org.fossify.gallery.compose.util.BlurState
import org.fossify.gallery.compose.util.ThumbnailOverlayState
import org.fossify.gallery.compose.util.privacyBlur
import java.io.File

private val ANIMATED_THUMB_EXTENSIONS = setOf("gif", "webp")
private val OPAQUE_THUMB_EXTENSIONS = setOf("jpg", "jpeg", "heic", "heif")

/**
 * Guaranteed opaque camera formats can be decoded as [Bitmap.Config.RGB_565] for grid/list
 * thumbnails, halving per-bitmap RAM and effectively doubling Coil's memory cache capacity while
 * keeping full alpha transparency (`ARGB_8888`) for PNG/WebP/SVG/GIF/JXL and full-size loads.
 */
internal fun shouldUseRgb565Thumbnail(path: String, thumbnailSize: Int?): Boolean {
    if (thumbnailSize == null) return false
    val ext = path.substringAfterLast('.', "").lowercase()
    return ext in OPAQUE_THUMB_EXTENSIONS
}

@Composable
fun GalleryImage(
    path: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    placeholderIconSize: Dp = 24.dp,
    thumbnailSize: Int? = 384,
    // Fit-scaled images (see MediaTile's cropThumbnails=false mode) don't cover the full box, so
    // this shows through as the letterbox/pillarbox color - callers that want solid black bars
    // instead of the default theme surface pass Color.Black here.
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
) {
    val ctx = LocalContext.current
    var isError by remember(path) { mutableStateOf(false) }
    // Honor "Play animated GIFs" for grid thumbnails via Compose-observable state rather than
    // hitting SharedPreferences on every tile recomposition.
    val isAnimatedExt = remember(path) { path.substringAfterLast('.', "").lowercase() in ANIMATED_THUMB_EXTENSIONS }
    val forceStaticFrame = isAnimatedExt && !ThumbnailOverlayState.animateGifs
    // Keep content URIs intact for SAF-picked folders. For regular library entries Coil can report
    // a missing file through its normal error state, avoiding one extra File.exists() IO call for
    // every thumbnail entering the viewport.
    val imageUri = remember(path) {
        if (path.startsWith("content://")) Uri.parse(path) else Uri.fromFile(File(path))
    }
    val useRgb565 = remember(path, thumbnailSize) { shouldUseRgb565Thumbnail(path, thumbnailSize) }
    val request = remember(ctx, imageUri, thumbnailSize, forceStaticFrame, useRgb565) {
        ImageRequest.Builder(ctx)
            .data(imageUri)
            // Crossfades are useful for a full-size image but add animation work to every tile
            // while scrolling a large library.
            .crossfade(thumbnailSize == null)
            .apply { if (useRgb565) bitmapConfig(Bitmap.Config.RGB_565) }
            .apply { if (thumbnailSize != null) size(thumbnailSize, thumbnailSize) }
            .apply { if (forceStaticFrame) decoderFactory(BitmapFactoryDecoder.Factory()) }
            .build()
    }

    // Use the container's tonal surface as the loading placeholder instead of composing and then
    // removing a placeholder Icon per tile. That avoids a second recomposition and ContentCapture
    // semantics churn on every thumbnail entering the viewport during 120 Hz scrolling.
    Box(modifier.background(backgroundColor)) {
        AsyncImage(
            model = request,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize().privacyBlur(BlurRadius.thumbnail, BlurState.enabled),
            contentScale = contentScale,
            onSuccess = { result ->
                GalleryThumbnailKeyRegistry.record(path, result.result.memoryCacheKey)
                if (isError) isError = false
            },
            onError = {
                if (!isError) isError = true
            },
        )

        if (isError) {
            Column(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.22f)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            ) {
                Icon(
                    Icons.Default.BrokenImage,
                    contentDescription,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(placeholderIconSize),
                )
                Text(
                    stringResource(R.string.media_unreadable_short),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

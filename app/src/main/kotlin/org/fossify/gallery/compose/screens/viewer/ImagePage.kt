package org.fossify.gallery.compose.screens.viewer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.content.Context
import android.net.Uri
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.memory.MemoryCache
import coil.request.ImageRequest
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fossify.gallery.compose.components.GalleryThumbnailKeyRegistry
import org.fossify.gallery.compose.theme.BlurRadius
import org.fossify.gallery.compose.util.BlurState
import org.fossify.gallery.compose.util.ThumbnailOverlayState
import org.fossify.gallery.compose.util.privacyBlur
import org.fossify.gallery.compose.util.sharedElementKey
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.ColorModeHelper
import java.io.File

internal const val VIEWER_IMAGE_MAX_SIZE_PX = 2560

internal fun shouldForceStaticViewerFrame(path: String): Boolean {
    val ext = path.substringAfterLast('.', "").lowercase()
    return (ext == "gif" || ext == "webp") && !ThumbnailOverlayState.animateGifs
}

internal fun viewerImageCacheKey(absolutePath: String, retryNonce: Int = 0): String =
    "viewer:$absolutePath:$retryNonce"

internal fun buildViewerImageRequest(
    ctx: Context,
    file: File,
    retryNonce: Int = 0,
    forceStaticFrame: Boolean = shouldForceStaticViewerFrame(file.absolutePath),
    thumbPlaceholderKey: MemoryCache.Key? = null,
): ImageRequest = ImageRequest.Builder(ctx)
    .data(Uri.fromFile(file))
    .size(VIEWER_IMAGE_MAX_SIZE_PX)
    // When the grid thumbnail is already in Coil's memory cache, show it immediately on
    // frame 0 and upgrade seamlessly without crossfading through a blank frame.
    .placeholderMemoryCacheKey(thumbPlaceholderKey)
    .crossfade(thumbPlaceholderKey == null)
    .memoryCacheKey(viewerImageCacheKey(file.absolutePath, retryNonce))
    .apply { if (forceStaticFrame) decoderFactory(coil.decode.BitmapFactoryDecoder.Factory()) }
    .build()

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
    var retryNonce by remember(path) { mutableIntStateOf(0) }
    var loadedBitmap by remember(path) { mutableStateOf<android.graphics.Bitmap?>(null) }

    // Enable Ultra HDR / wide-gamut window rendering on Android 14+ when the visible page's
    // decoded bitmap carries a gainmap or wide color space.
    LaunchedEffect(isCurrentPage, loadedBitmap) {
        if (isCurrentPage && loadedBitmap != null) {
            val activity = ctx as? android.app.Activity ?: return@LaunchedEffect
            ColorModeHelper.setColorModeForImage(activity, loadedBitmap, ctx.config.ultraHdrRendering)
        }
    }

    // Coil's AsyncImage is capped at 2560px (memory-bounded, fine at rest/fit-to-screen), so zooming
    // in past ~1x on anything shot at real camera resolution just magnifies an already-soft bitmap.
    // Once the user actually starts zooming, hand the *rendering* over to SubsamplingScaleImageView
    // (already a dependency, used by the legacy Views-based PhotoFragment) - it region-decodes tiles
    // straight from the file at the current zoom level, so detail stays sharp at any scale. It owns
    // its own pinch/pan/double-tap gesture handling internally (no public hook to drive it from
    // Compose's ZoomState instead), so this is a full handoff, not a hybrid: Coil+ZoomableBox only
    // drive the gesture up to the moment zooming starts, then step aside entirely until the native
    // view reports it's back at 1x. This does mean the shared-element grid->viewer transition (which
    // only applies to the Coil AsyncImage) isn't reproduced when re-entering the un-zoomed state from
    // a zoom-out - acceptable since that transition only ever plays once, on initial entry anyway.
    var useNativeZoom by remember(path) { mutableStateOf(false) }

    // The app's Coil ImageLoader (App.kt) registers the GIF/animated decoders globally, so animated
    // GIFs/WebPs played in the Viewer regardless of the "Play animated GIFs" setting - the toggle was
    // dead here. When it's off, force a plain BitmapFactory decode of these formats, which yields the
    // first frame as a static image (matches the legacy PhotoFragment's "decode as bitmap" path).
    val forceStaticFrame = remember(path, ThumbnailOverlayState.animateGifs) {
        shouldForceStaticViewerFrame(path)
    }
    val thumbPlaceholderKey = remember(path) { GalleryThumbnailKeyRegistry.get(path) }
    val viewerImageRequest = remember(ctx, file.absolutePath, retryNonce, forceStaticFrame, thumbPlaceholderKey) {
        buildViewerImageRequest(
            ctx = ctx,
            file = file,
            retryNonce = retryNonce,
            forceStaticFrame = forceStaticFrame,
            thumbPlaceholderKey = thumbPlaceholderKey,
        )
    }

    var exifOrientation by remember(path) { mutableIntStateOf(0) }
    LaunchedEffect(isCurrentPage) { if (!isCurrentPage) { zoom.reset(); useNativeZoom = false } }
    LaunchedEffect(zoom.isZoomed, useNativeZoom, isCurrentPage) { if (isCurrentPage) onZoomChange(zoom.isZoomed || useNativeZoom) }
    LaunchedEffect(path) {
        val (a, orient) = withContext(Dispatchers.IO) {
            val aspect = try {
                val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFile(file.absolutePath, o)
                if (o.outWidth > 0 && o.outHeight > 0) o.outWidth.toFloat() / o.outHeight else 0f
            } catch (_: Exception) { 0f }
            val deg = try {
                when (android.media.ExifInterface(file.absolutePath).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)) {
                    android.media.ExifInterface.ORIENTATION_ROTATE_90,
                    android.media.ExifInterface.ORIENTATION_TRANSPOSE -> 90
                    android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    android.media.ExifInterface.ORIENTATION_ROTATE_270,
                    android.media.ExifInterface.ORIENTATION_TRANSVERSE -> 270
                    else -> 0
                }
            } catch (_: Exception) { 0 }
            aspect to deg
        }
        exifOrientation = orient
        if (a > 0f) zoom.updateContentAspect(a)
    }

    Box(Modifier.fillMaxSize().clipToBounds().then(modifier)) {
        if (useNativeZoom) {
            val view = remember(path, exifOrientation) {
                SubsamplingScaleImageView(ctx).apply {
                    setMaxTileSize(4096)
                    setMinimumTileDpi(160)
                    maxScale = ZOOM_MAX
                    orientation = exifOrientation
                    setOnClickListener { onToggleUi() }
                    setImage("file://${file.absolutePath}")
                }
            }
            DisposableEffect(view) { onDispose { view.recycle() } }
            AndroidView(
                factory = {
                    view.apply {
                        onImageEventListener = object : SubsamplingScaleImageView.OnImageEventListener {
                            override fun onReady() {}
                            // Region decoding can fail even when Coil's own decode succeeded (e.g. a
                            // format this library's decoder doesn't support) - fall back to the
                            // already-working Coil view instead of leaving a blank AndroidView.
                            override fun onImageLoadError(e: Exception) { useNativeZoom = false }
                            override fun onImageRotation(degrees: Int) {}
                            // No scale/pan-changed callback exists on this fork - checking on touch-up
                            // is the only available hook to notice "the user zoomed back out" and hand
                            // the gesture back to Coil+ZoomableBox.
                            override fun onUpEvent() { if (isZoomedOut()) { useNativeZoom = false; zoom.reset() } }
                        }
                    }
                },
                modifier = Modifier.fillMaxSize().privacyBlur(BlurRadius.viewer, BlurState.enabled),
            )
        } else {
            AsyncImage(
                model = viewerImageRequest,
                contentDescription = file.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .privacyBlur(BlurRadius.viewer, BlurState.enabled)
                    .onSizeChanged { }
                    .then(if (isCurrentPage) Modifier.sharedElementKey("media_$path") else Modifier)
                    .graphicsLayer {
                        scaleX = zoom.scale; scaleY = zoom.scale
                        translationX = zoom.offset.x; translationY = zoom.offset.y
                    }
                    .zoomable(
                        zoom,
                        onSingleTap = onToggleUi,
                        // Deliberately NOT handing off to the native tiled view here (only pinch-start
                        // below does that): this fires and replaces the whole AsyncImage with the
                        // native view on the very same recomposition, before zoom.cycleZoom()'s 220ms
                        // Compose animation ever gets a visible frame - the native view then mounts at
                        // its own default 1x (this library fork has no external scale/center control),
                        // so the double-tap silently produced no visible zoom at all, and ZoomMinimap
                        // (only rendered in this Coil branch) disappeared with it. A pinch gesture
                        // doesn't have this problem since the native view picks up the continuing
                        // gesture right where the fingers already are.
                        onDoubleTap = { pos, sz -> zoom.cycleZoom(pos, sz) },
                        onZoomGestureStart = { useNativeZoom = true },
                    ),
                onSuccess = {
                    imageState = it
                    loadedBitmap = (it.result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                },
                onError = { imageState = it },
                onLoading = { imageState = it },
            )

            when (imageState) {
                is AsyncImagePainter.State.Error -> ViewerMediaErrorState(
                    file = file,
                    isAvailable = file.exists(),
                    onRetry = {
                        imageState = null
                        retryNonce++
                    },
                )
                is AsyncImagePainter.State.Success -> { }
                else -> if (thumbPlaceholderKey == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
            }

            ZoomMinimap(zoom, modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 72.dp, end = 12.dp))
        }
    }
}

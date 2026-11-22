package org.fossify.gallery.compose.screens.editor

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.size.Scale
import coil.size.Size
import com.canhub.cropper.CropImageOptions
import com.canhub.cropper.CropImageView
import org.fossify.gallery.R
import org.fossify.gallery.helpers.ASPECT_RATIO_FOUR_THREE
import org.fossify.gallery.helpers.ASPECT_RATIO_FREE
import org.fossify.gallery.helpers.ASPECT_RATIO_ONE_ONE
import org.fossify.gallery.helpers.ASPECT_RATIO_SIXTEEN_NINE
import org.fossify.gallery.views.EditorDrawCanvas

private const val RATIO_FOUR = 4
private const val RATIO_THREE = 3
private const val RATIO_SIXTEEN = 16
private const val RATIO_NINE = 9

@Composable
internal fun EditorCanvasContent(
    state: EditorUiState,
    imageUri: Uri?,
    previewBitmap: Bitmap?,
    drawBackgroundBitmap: Bitmap?,
    onDrawBackgroundReady: (Bitmap) -> Unit,
    onCropViewBind: (CropImageView) -> Unit,
    onDrawViewBind: (EditorDrawCanvas) -> Unit,
    onDrawHistoryChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentUri = imageUri ?: return
    when (state.primaryMode) {
        EditorPrimaryMode.CROP_ROTATE -> CropCanvasView(
            currentUri = currentUri,
            showGuidelines = state.showAspectRatioStrip || state.lockSquareCrop,
            aspectRatio = state.aspectRatio,
            otherAspectRatio = state.lastOtherAspectRatio,
            onCropViewBind = onCropViewBind,
            modifier = modifier,
        )
        EditorPrimaryMode.FILTER -> FilterPreviewCanvas(
            bitmap = previewBitmap,
            adjustments = state.colorAdjustments,
            modifier = modifier,
        )
        EditorPrimaryMode.DRAW -> DrawCanvasContainer(
            currentUri = currentUri,
            drawBackgroundBitmap = drawBackgroundBitmap,
            drawColor = state.drawColor,
            brushSizePercent = state.brushSizePercent,
            onDrawBackgroundReady = onDrawBackgroundReady,
            onDrawViewBind = onDrawViewBind,
            onDrawHistoryChange = onDrawHistoryChange,
            modifier = modifier,
        )
    }
}

@Composable
private fun CropCanvasView(
    currentUri: Uri,
    showGuidelines: Boolean,
    aspectRatio: Int,
    otherAspectRatio: Pair<Float, Float>?,
    onCropViewBind: (CropImageView) -> Unit,
    modifier: Modifier = Modifier,
) {
    val loadedUriHolder = remember { arrayOfNulls<Uri>(1) }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            CropImageView(ctx).apply {
                setImageCropOptions(
                    CropImageOptions(
                        backgroundColor = ContextCompat.getColor(ctx, R.color.crop_image_view_background),
                        guidelines = CropImageView.Guidelines.ON,
                        initialCropWindowPaddingRatio = 0f,
                    ),
                )
                onCropViewBind(this)
            }
        },
        update = { view ->
            onCropViewBind(view)
            if (currentUri != loadedUriHolder[0]) {
                loadedUriHolder[0] = currentUri
                view.setImageUriAsync(currentUri)
            }
            view.guidelines = if (showGuidelines) {
                CropImageView.Guidelines.ON
            } else {
                CropImageView.Guidelines.OFF
            }
            applyCropAspectRatio(view, aspectRatio, otherAspectRatio)
        },
    )
}

@Composable
private fun FilterPreviewCanvas(
    bitmap: Bitmap?,
    adjustments: org.fossify.gallery.helpers.ColorAdjustmentParams,
    modifier: Modifier = Modifier,
) {
    if (bitmap == null) return
    val colorFilter = remember(adjustments) {
        if (adjustments.isIdentity) null
        else ColorFilter.colorMatrix(ColorMatrix(adjustments.toColorMatrix().array))
    }
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        colorFilter = colorFilter,
        modifier = modifier,
    )
}

@Composable
private fun DrawCanvasContainer(
    currentUri: Uri,
    drawBackgroundBitmap: Bitmap?,
    drawColor: Int,
    brushSizePercent: Int,
    onDrawBackgroundReady: (Bitmap) -> Unit,
    onDrawViewBind: (EditorDrawCanvas) -> Unit,
    onDrawHistoryChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val currentOnReady by rememberUpdatedState(onDrawBackgroundReady)
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        val density = LocalDensity.current
        val maxWidthPx = constraints.maxWidth.coerceAtLeast(1)
        val maxHeightPx = constraints.maxHeight.coerceAtLeast(1)

        LaunchedEffect(currentUri, maxWidthPx, maxHeightPx) {
            if (drawBackgroundBitmap == null) {
                val loaded = EditorSaveHelper.loadBitmapWithCoil(
                    context = context,
                    uri = currentUri,
                    size = Size(maxWidthPx, maxHeightPx),
                    scale = Scale.FIT,
                    mutable = true,
                )
                if (loaded != null) currentOnReady(loaded)
            }
        }

        if (drawBackgroundBitmap != null) {
            val widthDp = with(density) { drawBackgroundBitmap.width.toDp() }
            val heightDp = with(density) { drawBackgroundBitmap.height.toDp() }
            Box(modifier = Modifier.size(widthDp, heightDp)) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        EditorDrawCanvas(ctx).apply {
                            updateBackgroundBitmap(drawBackgroundBitmap)
                            updateColor(drawColor)
                            updateBrushSize(brushSizePercent)
                            onHistoryChange = onDrawHistoryChange
                            onDrawViewBind(this)
                        }
                    },
                    update = { view ->
                        onDrawViewBind(view)
                        view.updateColor(drawColor)
                        view.updateBrushSize(brushSizePercent)
                    },
                )
            }
        }
    }
}

private fun applyCropAspectRatio(
    view: CropImageView,
    aspectRatio: Int,
    otherPair: Pair<Float, Float>?,
) {
    if (aspectRatio == ASPECT_RATIO_FREE) {
        view.setFixedAspectRatio(false)
        return
    }
    val (x, y) = when (aspectRatio) {
        ASPECT_RATIO_ONE_ONE -> 1 to 1
        ASPECT_RATIO_FOUR_THREE -> RATIO_FOUR to RATIO_THREE
        ASPECT_RATIO_SIXTEEN_NINE -> RATIO_SIXTEEN to RATIO_NINE
        else -> (otherPair?.first?.toInt() ?: 1) to (otherPair?.second?.toInt() ?: 1)
    }
    view.setAspectRatio(x.coerceAtLeast(1), y.coerceAtLeast(1))
}

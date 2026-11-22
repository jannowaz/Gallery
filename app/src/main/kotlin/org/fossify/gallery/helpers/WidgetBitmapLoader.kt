package org.fossify.gallery.helpers

import android.content.Context
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Precision
import coil.size.Scale
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * Loads a software [Bitmap] for home-screen `RemoteViews` widgets via the app's shared Coil
 * [coil.ImageLoader] (reusing its disk/memory cache and video/SVG/GIF decoders instead of keeping
 * a separate Glide stack alive for widgets). Must be called off the main thread.
 */
fun loadWidgetBitmap(
    context: Context,
    path: String,
    sizePx: Int,
    centerCrop: Boolean,
    blur: Boolean,
): Bitmap? {
    val safeSize = sizePx.coerceIn(64, 1024)
    val request = ImageRequest.Builder(context)
        .data(File(path))
        .size(safeSize, safeSize)
        .scale(if (centerCrop) Scale.FILL else Scale.FIT)
        .precision(Precision.EXACT)
        .allowHardware(false)
        .build()

    val result = runCatching {
        runBlocking { Coil.imageLoader(context).execute(request) }
    }.getOrNull() as? SuccessResult ?: return null

    var bitmap = runCatching {
        result.drawable.toBitmap(config = Bitmap.Config.ARGB_8888)
    }.getOrNull() ?: return null

    if (centerCrop && bitmap.width != bitmap.height) {
        val side = minOf(bitmap.width, bitmap.height)
        val x = (bitmap.width - side) / 2
        val y = (bitmap.height - side) / 2
        bitmap = Bitmap.createBitmap(bitmap, x, y, side, side)
    }

    if (blur) {
        bitmap = bitmap.withPrivacyScrim()
    }
    return bitmap
}

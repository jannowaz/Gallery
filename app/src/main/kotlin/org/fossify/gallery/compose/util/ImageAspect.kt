package org.fossify.gallery.compose.util

import android.graphics.BitmapFactory

/**
 * Decodes only the bounds of an image file to derive its aspect ratio (width / height).
 * Plain helper, kept out of composables. Always call from a background dispatcher.
 */
internal fun decodeImageAspect(path: String): Float = try {
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, opts)
    if (opts.outWidth > 0 && opts.outHeight > 0) opts.outWidth.toFloat() / opts.outHeight.toFloat() else 1f
} catch (_: Exception) { 1f }

package org.fossify.gallery.helpers

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color

/**
 * Bakes a solid privacy scrim directly into a copy of this bitmap - the only way to honor "blur
 * all media" for surfaces that can't apply a live view-level blur/RenderEffect. RemoteViews
 * widgets in particular only ever receive a plain static Bitmap from the host process, with no
 * hook to post-process how it's drawn, so the scrim has to already be part of the pixel data.
 */
fun Bitmap.withPrivacyScrim(): Bitmap {
    val scrimmed = copy(config ?: Bitmap.Config.ARGB_8888, true)
    Canvas(scrimmed).drawColor(Color.argb(235, 0, 0, 0))
    return scrimmed
}

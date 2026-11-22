package org.fossify.gallery.helpers

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import kotlin.math.abs

/**
 * Pure color-adjustment parameters (brightness, contrast, saturation) and software-bitmap
 * application helper used by [org.fossify.gallery.activities.EditActivity].
 */
data class ColorAdjustmentParams(
    val brightness: Float = 0f,   // -100f .. +100f
    val contrast: Float = 1f,     // 0.5f .. 2.0f
    val saturation: Float = 1f,   // 0.0f .. 2.0f
) {
    val isIdentity: Boolean
        get() = abs(brightness) < 0.01f && abs(contrast - 1f) < 0.01f && abs(saturation - 1f) < 0.01f

    fun clamped(): ColorAdjustmentParams = ColorAdjustmentParams(
        brightness = brightness.coerceIn(-100f, 100f),
        contrast = contrast.coerceIn(0.5f, 2.0f),
        saturation = saturation.coerceIn(0.0f, 2.0f),
    )

    fun toColorMatrix(): ColorMatrix {
        val c = clamped()
        val satMatrix = ColorMatrix().apply { setSaturation(c.saturation) }
        val translate = (1f - c.contrast) * 128f + c.brightness
        val contrastBrightnessMatrix = ColorMatrix(
            floatArrayOf(
                c.contrast, 0f, 0f, 0f, translate,
                0f, c.contrast, 0f, 0f, translate,
                0f, 0f, c.contrast, 0f, translate,
                0f, 0f, 0f, 1f, 0f,
            )
        )
        satMatrix.postConcat(contrastBrightnessMatrix)
        return satMatrix
    }

    fun applyToBitmap(source: Bitmap): Bitmap {
        if (isIdentity) return source
        val out = createBitmap(source.width, source.height)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(toColorMatrix())
        }
        canvas.drawBitmap(source, 0f, 0f, paint)
        return out
    }
}

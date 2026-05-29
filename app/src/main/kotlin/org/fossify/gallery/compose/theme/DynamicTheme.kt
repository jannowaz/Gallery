package org.fossify.gallery.compose.theme

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object DynamicTheme {
    suspend fun extractColors(path: String): Pair<Color, Color> = withContext(Dispatchers.IO) {
        try {
            val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = 8 }
            val bitmap = android.graphics.BitmapFactory.decodeFile(path, opts)
            if (bitmap != null) {
                val dominant = averageColor(bitmap)
                bitmap.recycle()
                dominant to dominant.copy(alpha = 0.8f)
            } else {
                Color.Gray to Color.DarkGray
            }
        } catch (_: Exception) { Color.Gray to Color.DarkGray }
    }

    private fun averageColor(bitmap: Bitmap): Color {
        var r = 0L; var g = 0L; var b = 0L; var count = 0
        for (x in 0 until bitmap.width step 4) {
            for (y in 0 until bitmap.height step 4) {
                val pixel = bitmap.getPixel(x, y)
                r += android.graphics.Color.red(pixel)
                g += android.graphics.Color.green(pixel)
                b += android.graphics.Color.blue(pixel)
                count++
            }
        }
        return if (count > 0) Color(r.toInt() / count, g.toInt() / count, b.toInt() / count) else Color.Gray
    }
}

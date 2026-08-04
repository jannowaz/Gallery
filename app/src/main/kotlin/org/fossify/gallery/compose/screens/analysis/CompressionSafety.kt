package org.fossify.gallery.compose.screens.analysis

import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import java.io.File

/**
 * The single, named data-safety invariant for compression/optimization: an original may be moved to
 * the recycle bin ONLY after [isValidReplacement] confirms the produced file is real. This exists so
 * no replace path can ever trash the original without a genuine, usable substitute - the cause of a
 * data-loss bug where a silently-failed encode left a 0-byte file that was accepted, then the
 * original was deleted. Both replace paths (CompressionKeeper.keepNew and TransformationEngine.execute)
 * gate on this; any new one must too.
 */
object CompressionSafety {

    /** Cheap, Android-free precondition: the file exists and has bytes. This alone already prevents
     * the "original gone, empty new file" loss; split out so it is unit-testable on the JVM. */
    fun hasContent(file: File): Boolean = file.exists() && file.length() > 0L

    /** The full guard: has content AND, for the media types we can check, actually decodes - so a
     * non-empty-but-corrupt output can't cause the original to be trashed either. Unknown extensions
     * fall back to the content check (best available assertion). */
    fun isValidReplacement(file: File): Boolean {
        if (!hasContent(file)) return false
        val ext = file.extension.lowercase()
        return when {
            ext in AnalysisCriteria.VIDEO_EXTS -> runCatching {
                val r = MediaMetadataRetriever()
                try {
                    r.setDataSource(file.absolutePath)
                    (r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) > 0L
                } finally { r.release() }
            }.getOrDefault(false)
            ext in AnalysisCriteria.IMAGE_EXTS -> runCatching {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, opts)
                opts.outWidth > 0 && opts.outHeight > 0
            }.getOrDefault(false)
            else -> true
        }
    }
}

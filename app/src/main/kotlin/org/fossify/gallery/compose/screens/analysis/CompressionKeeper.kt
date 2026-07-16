package org.fossify.gallery.compose.screens.analysis

import android.content.Context
import org.fossify.gallery.helpers.RefreshBus
import org.fossify.gallery.helpers.XmpWriter
import java.io.File

/**
 * The single "accept a probe-compressed file" implementation, shared by the list-based
 * [CompressionReviewViewModel] and the swipe flow ([CompressionSwipeViewModel]): moves the temp
 * result next to the original under a "_compressed" name, soft-deletes the original into the
 * recycle bin (recoverable), and carries the app's tags/rating over. Call from a background thread.
 */
object CompressionKeeper {

    /** Returns the accepted copy's final path, or null if nothing was changed on disk
     * (missing/unmovable temp file). */
    fun keepNew(context: Context, originalPath: String, tempPath: String): String? {
        val original = File(originalPath)
        val temp = File(tempPath)
        if (!temp.exists()) return null
        val target = uniqueTargetFor(original, temp.extension)
        val srcXmp = runCatching { XmpWriter.read(originalPath) }.getOrNull()

        val moved = temp.renameTo(target) || runCatching { temp.copyTo(target, overwrite = false); temp.delete() }.isSuccess
        if (!moved || !target.exists()) return null

        TransformationEngine(context).softDeleteOriginal(original)
        if (srcXmp != null && (srcXmp.tags.isNotEmpty() || srcXmp.rating > 0)) {
            runCatching { XmpWriter.write(target.absolutePath, srcXmp.tags, srcXmp.rating) }
        }
        runCatching { android.media.MediaScannerConnection.scanFile(context, arrayOf(originalPath, target.absolutePath), null, null) }
        RefreshBus.trigger()
        return target.absolutePath
    }

    private fun uniqueTargetFor(original: File, newExt: String): File {
        val dir = original.parentFile
        val base = original.nameWithoutExtension
        var candidate = File(dir, "${base}_compressed.$newExt")
        var i = 2
        while (candidate.exists()) {
            candidate = File(dir, "${base}_compressed_$i.$newExt")
            i++
        }
        return candidate
    }
}

package org.fossify.gallery.compose.screens.analysis

import android.content.Context
import org.fossify.gallery.extensions.mediaDB
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
        // Captured while the original is still live in the DB, used to keep the compressed copy in its
        // original timeline slot instead of showing up as brand new (see TransformationEngine.inheritTimeline).
        val origModified = original.lastModified()
        val origMedium = runCatching { context.mediaDB.getMediaByPaths(listOf(originalPath)).firstOrNull() }.getOrNull()

        val moved = temp.renameTo(target) || runCatching { temp.copyTo(target, overwrite = false); temp.delete() }.isSuccess
        if (!moved || !target.exists()) return null

        val engine = TransformationEngine(context)
        // Pre-register the timeline row before the scan below (sync IGNOREs existing paths), then
        // soft-delete the original into the recycle bin.
        engine.inheritTimeline(origMedium, target)
        engine.softDeleteOriginal(original)
        if (srcXmp != null && (srcXmp.tags.isNotEmpty() || srcXmp.rating > 0)) {
            runCatching { XmpWriter.write(target.absolutePath, srcXmp.tags, srcXmp.rating) }
        }
        // After the XMP write (which bumps mtime): restore the original's modified time.
        if (origModified > 0) runCatching { target.setLastModified(origModified) }
        // Only scan the NEW file - re-scanning the just-soft-deleted original's still-present path
        // re-registers it in MediaStore and the sync's reviveSoftDeleted resurrects it (see the same
        // fix in TransformationEngine.execute).
        runCatching { android.media.MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), null, null) }
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

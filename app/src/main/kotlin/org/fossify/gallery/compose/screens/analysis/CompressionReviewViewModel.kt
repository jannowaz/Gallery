package org.fossify.gallery.compose.screens.analysis

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.gallery.extensions.compressionReviewDB
import org.fossify.gallery.helpers.RefreshBus
import org.fossify.gallery.helpers.XmpWriter
import org.fossify.gallery.models.CompressionReviewItem
import java.io.File

class CompressionReviewViewModel(app: Application) : AndroidViewModel(app) {
    private val engine = TransformationEngine(app)

    val items: StateFlow<List<CompressionReviewItem>> = getApplication<Application>().compressionReviewDB
        .getAllLive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Discards the probe-compressed temp file - the original is untouched, so this is a pure no-op
     * on real media, just cache cleanup. */
    fun keepOriginal(item: CompressionReviewItem) {
        viewModelScope.launch(Dispatchers.IO) {
            if (item.tempResultPath.isNotBlank()) runCatching { File(item.tempResultPath).delete() }
            item.id?.let { getApplication<Application>().compressionReviewDB.deleteItem(it) }
        }
    }

    /** Moves the compressed temp file into the original's folder under a "_compressed" name, soft-
     * deletes the original into the recycle bin (same mechanism [TransformationEngine] already uses
     * for its own optimize flow), and carries tags/rating over if the original had any. */
    fun keepNew(item: CompressionReviewItem) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val original = File(item.originalPath)
                val temp = File(item.tempResultPath)
                if (!temp.exists()) return@launch
                val target = uniqueTargetFor(original, temp.extension)
                val srcXmp = runCatching { XmpWriter.read(item.originalPath) }.getOrNull()

                val moved = temp.renameTo(target) || runCatching { temp.copyTo(target, overwrite = false); temp.delete() }.isSuccess
                if (!moved || !target.exists()) return@launch

                engine.softDeleteOriginal(original)
                if (srcXmp != null && (srcXmp.tags.isNotEmpty() || srcXmp.rating > 0)) {
                    runCatching { XmpWriter.write(target.absolutePath, srcXmp.tags, srcXmp.rating) }
                }
                runCatching { android.media.MediaScannerConnection.scanFile(getApplication(), arrayOf(item.originalPath, target.absolutePath), null, null) }
                RefreshBus.trigger()
            } finally {
                item.id?.let { getApplication<Application>().compressionReviewDB.deleteItem(it) }
            }
        }
    }

    fun keepAllNew() {
        val done = items.value.filter { it.status == CompressionReviewItem.STATUS_DONE }
        done.forEach { keepNew(it) }
    }

    fun keepAllOriginal() {
        val done = items.value.filter { it.status == CompressionReviewItem.STATUS_DONE }
        done.forEach { keepOriginal(it) }
    }

    fun dismissFailed(item: CompressionReviewItem) {
        viewModelScope.launch(Dispatchers.IO) { item.id?.let { getApplication<Application>().compressionReviewDB.deleteItem(it) } }
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

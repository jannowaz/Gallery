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
import org.fossify.gallery.models.CompressionReviewItem
import java.io.File

class CompressionReviewViewModel(app: Application) : AndroidViewModel(app) {

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

    /** Accepts the compressed version via [CompressionKeeper] (move next to original, soft-delete
     * original into the recycle bin, carry tags/rating over). */
    fun keepNew(item: CompressionReviewItem) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val newPath = CompressionKeeper.keepNew(getApplication(), item.originalPath, item.tempResultPath)
                if (newPath != null) {
                    org.fossify.gallery.helpers.UndoManager.push(
                        org.fossify.gallery.helpers.UndoAction(
                            paths = setOf(item.originalPath),
                            type = org.fossify.gallery.helpers.UndoType.COMPRESS_REPLACE,
                            extra = mapOf("newPath" to newPath),
                        )
                    )
                }
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

}

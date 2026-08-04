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
import org.fossify.gallery.R
import org.fossify.gallery.extensions.compressionReviewDB
import org.fossify.gallery.extensions.config
import org.fossify.gallery.models.CompressionReviewItem
import java.io.File

class CompressionReviewViewModel(app: Application) : AndroidViewModel(app) {

    val items: StateFlow<List<CompressionReviewItem>> = getApplication<Application>().compressionReviewDB
        .getAllLive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Housekeeping: drop any temp file in the compression cache that no review item references
        // anymore (leftovers from crashes, old sessions, or already-decided items), so nothing but
        // pending, referenced results ever lingers. Age-gated so a temp a worker just wrote - but
        // whose DB row isn't in yet - is never swept out from under an in-flight compression.
        viewModelScope.launch(Dispatchers.IO) { sweepOrphanTemps() }
    }

    private suspend fun sweepOrphanTemps() {
        val referenced = runCatching { getApplication<Application>().compressionReviewDB.getAllTempPaths().toSet() }.getOrDefault(emptySet())
        val minAge = System.currentTimeMillis() - 2 * 60 * 1000L
        CompressionEngine.cacheDir(getApplication()).listFiles()?.forEach { f ->
            if (f.absolutePath !in referenced && f.lastModified() < minAge) runCatching { f.delete() }
        }
    }

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
        viewModelScope.launch(Dispatchers.IO) { keepNewInternal(item) }
    }

    private suspend fun keepNewInternal(item: CompressionReviewItem) {
        val newPath = runCatching { CompressionKeeper.keepNew(getApplication(), item.originalPath, item.tempResultPath) }.getOrNull()
        if (newPath != null) {
            getApplication<Application>().config.totalCompressionSavedBytes += (item.originalSize - item.resultSize).coerceAtLeast(0)
            org.fossify.gallery.helpers.UndoManager.push(
                org.fossify.gallery.helpers.UndoAction(
                    paths = setOf(item.originalPath),
                    type = org.fossify.gallery.helpers.UndoType.COMPRESS_REPLACE,
                    extra = mapOf("newPath" to newPath),
                )
            )
            item.id?.let { getApplication<Application>().compressionReviewDB.deleteItem(it) }
        } else {
            // keepNew did nothing (missing/empty temp, or the move failed) - crucially the original
            // was NOT trashed. Don't silently drop the item: mark it failed so it stays visible in
            // the review (dismissable) instead of vanishing with neither a kept new file nor a note.
            getApplication<Application>().compressionReviewDB.update(
                item.copy(status = CompressionReviewItem.STATUS_FAILED, errorMessage = getApplication<Application>().getString(R.string.compression_keep_failed))
            )
        }
    }

    // Sequential, not one launch per item: keeping runs one-at-a-time so a unique target name picked
    // for one file is already on disk before the next is computed - no concurrent race could ever pick
    // the same "_compressed" name for two files, and MediaStore isn't hit with N trashes at once.
    fun keepAllNew() {
        viewModelScope.launch(Dispatchers.IO) {
            items.value.filter { it.status == CompressionReviewItem.STATUS_DONE }.forEach { keepNewInternal(it) }
        }
    }

    fun keepAllOriginal() {
        val done = items.value.filter { it.status == CompressionReviewItem.STATUS_DONE }
        done.forEach { keepOriginal(it) }
    }

    fun dismissFailed(item: CompressionReviewItem) {
        viewModelScope.launch(Dispatchers.IO) { item.id?.let { getApplication<Application>().compressionReviewDB.deleteItem(it) } }
    }

}

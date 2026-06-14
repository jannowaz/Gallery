package org.fossify.gallery.viewmodels

import android.app.Application
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.gallery.extensions.directoryDB
import org.fossify.gallery.extensions.mediaDB
import org.fossify.gallery.helpers.RefreshBus
import org.fossify.gallery.models.Directory
import org.fossify.gallery.models.Medium
import java.io.File

data class ExplorerUiState(
    val selectedTab: Int = 1,
    val explorerPath: String = "",
    val activeRatingFilter: Int = 0,
    val activeTagFilter: Set<String>? = null,
    val activeTagName: String? = null,
    val activePathFilter: Set<String>? = null,
    val mediaRefreshTrigger: Int = 0,
    val preFilterTab: Int = -1,
    val dbInitialized: Boolean = false,
    val dbInitError: String? = null,
    val gridScrollIndex: Int = 0,
    val gridScrollOffset: Int = 0,
)

class ExplorerViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(ExplorerUiState())
    val state: StateFlow<ExplorerUiState> = _state.asStateFlow()

    init {
        _state.update { it.copy(explorerPath = android.os.Environment.getExternalStorageDirectory().absolutePath) }
        viewModelScope.launch {
            RefreshBus.events.collect {
                triggerMediaRefresh()
            }
        }
    }

    fun setSelectedTab(tab: Int) { _state.update { it.copy(selectedTab = tab) } }
    fun setExplorerPath(path: String) { _state.update { it.copy(explorerPath = path) } }
    fun setRatingFilter(rating: Int) { _state.update { it.copy(activeRatingFilter = rating) } }
    fun setTagFilter(tagPaths: Set<String>?, tagName: String?) { _state.update { it.copy(activeTagFilter = tagPaths, activeTagName = tagName) } }
    fun setPathFilter(paths: Set<String>?) { _state.update { it.copy(activePathFilter = paths) } }
    fun setPreFilterTab(tab: Int) { _state.update { it.copy(preFilterTab = tab) } }

    fun clearFilters() {
        _state.update { it.copy(activeRatingFilter = 0, activeTagFilter = null, activeTagName = null, activePathFilter = null, preFilterTab = -1) }
    }

    fun triggerMediaRefresh() {
        _state.update { it.copy(mediaRefreshTrigger = it.mediaRefreshTrigger + 1) }
    }

    fun saveScrollPosition(index: Int, offset: Int) {
        _state.update { it.copy(gridScrollIndex = index, gridScrollOffset = offset) }
    }

    fun initializeDatabase(onComplete: (() -> Unit)? = null) {
        val s = _state.value
        if (s.dbInitialized) { onComplete?.invoke(); return }
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            withContext(Dispatchers.IO) {
                try {
                    val existing = ctx.mediaDB.getNewestMedia(1)
                    if (existing.isEmpty()) {
                        val mediums = mutableListOf<Medium>()
                        val uri = MediaStore.Files.getContentUri("external")
                        val projection = arrayOf(
                            MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.DATA,
                            MediaStore.Files.FileColumns.DATE_MODIFIED, MediaStore.Files.FileColumns.DATE_TAKEN,
                            MediaStore.Files.FileColumns.SIZE, MediaStore.Files.FileColumns.MIME_TYPE,
                            MediaStore.Files.FileColumns.MEDIA_TYPE, MediaStore.Files.FileColumns.DURATION,
                        )
                        val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?"
                        val selectionArgs = arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(), MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString())
                        ctx.contentResolver.query(uri, projection, selection, selectionArgs, "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC")
                            ?.use { cursor ->
                                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
                                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
                                val takenCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_TAKEN)
                                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                                val typeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
                                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DURATION)
                                while (cursor.moveToNext()) {
                                    val path = cursor.getString(dataCol) ?: continue
                                    val modified = cursor.getLong(dateCol) * 1000L
                                    val taken = if (!cursor.isNull(takenCol)) cursor.getLong(takenCol) else modified
                                    val size = cursor.getLong(sizeCol)
                                    val mediaType = cursor.getInt(typeCol)
                                    val type = if (mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO) 2 else 1
                                    val duration = if (!cursor.isNull(durCol)) (cursor.getInt(durCol) / 1000) else 0
                                    mediums.add(Medium(
                                        id = null, name = File(path).name, path = path, parentPath = File(path).parent ?: "",
                                        modified = modified, taken = taken, size = size, type = type,
                                        videoDuration = duration, isFavorite = false, deletedTS = 0L, mediaStoreId = 0, rating = 0,
                                    ))
                                }
                            }
                        if (mediums.isNotEmpty()) {
                            ctx.mediaDB.insertAll(mediums)
                            val dirs = mediums.map { it.parentPath }.distinct()
                            dirs.forEach { dirPath ->
                                val dirMedia = mediums.filter { it.parentPath == dirPath }
                                val dirName = File(dirPath).name
                                val hasImage = dirMedia.any { it.type == 1 }
                                val hasVideo = dirMedia.any { it.type == 2 }
                                val types = if (hasImage && hasVideo) 3 else if (hasVideo) 2 else 1
                                ctx.directoryDB.insertAll(listOf(Directory(
                                    id = null, path = dirPath, tmb = dirMedia.maxByOrNull { it.modified }?.path ?: "",
                                    name = dirName, mediaCnt = dirMedia.size, modified = dirMedia.maxOf { it.modified },
                                    taken = dirMedia.maxOf { it.taken }, size = dirMedia.size.toLong(),
                                    location = org.fossify.gallery.helpers.LOCATION_INTERNAL, types = types, sortValue = "",
                                )))
                            }
                        }
                    }
                    _state.update { it.copy(dbInitialized = true) }
                } catch (e: Exception) {
                    _state.update { it.copy(dbInitError = e.message) }
                }
            }
            onComplete?.invoke()
        }
    }
}

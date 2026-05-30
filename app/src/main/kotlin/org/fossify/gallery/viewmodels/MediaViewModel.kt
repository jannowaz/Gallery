package org.fossify.gallery.viewmodels

import android.app.Application
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.gallery.extensions.mediaDB
import org.fossify.gallery.helpers.MediaRepository
import org.fossify.gallery.models.Medium
import java.io.File

data class MediaUiState(
    val allMedia: List<Medium> = emptyList(),
    val isLoading: Boolean = false,
    val hasMore: Boolean = true,
    val showImages: Boolean = true,
    val showVideos: Boolean = true,
    val error: String? = null,
)

class MediaViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MediaRepository(application)
    private val _state = MutableStateFlow(MediaUiState())
    val state: StateFlow<MediaUiState> = _state.asStateFlow()

    private val videoExts = setOf("mp4", "mkv", "mov", "3gp", "wmv", "flv", "avi")
    private val imageExts = setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "avif", "bmp", "svg", "apng", "jxl")
    private val mediaExts = videoExts + imageExts

    private var loaded = false

    fun load() {
        if (loaded) return
        loaded = true
        doLoad()
    }

    fun refresh() {
        loaded = false
        doLoad()
    }

    private fun doLoad() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val media = withContext(Dispatchers.IO) {
                try {
                    // Use the Room DB (populated by MediaStore scan) instead of limited directory scan
                    val ctx = getApplication<Application>()
                    val fromDb = ctx.mediaDB.getNewestMedia(500)
                    if (fromDb.isNotEmpty()) {
                        fromDb.sortedByDescending { it.modified }
                    } else {
                        // Fallback: scan directories if DB is empty
                        scanDirectories()
                    }
                } catch (_: Exception) { emptyList() }
            }
            _state.update { it.copy(allMedia = media, isLoading = false, hasMore = false) }
        }
    }

    private fun scanDirectories(): List<Medium> {
        val ctx = getApplication<Application>()
        val dirs = listOfNotNull(
            Environment.getExternalStorageDirectory(),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        ).filter { it.isDirectory }
        val allMedia = mutableListOf<Medium>()
        val seen = mutableSetOf<String>()
        val mediaExts = videoExts + imageExts
        for (dir in dirs) {
            scanFile(dir, allMedia, seen, 0, mediaExts)
        }
        return allMedia.sortedByDescending { it.modified }.take(500)
    }

    private fun scanFile(dir: File, result: MutableList<Medium>, seen: MutableSet<String>, depth: Int, mediaExts: Set<String>) {
        if (depth > 3 || !dir.isDirectory) return
        val files = dir.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory && !file.name.startsWith(".")) {
                scanFile(file, result, seen, depth + 1, mediaExts)
            } else if (file.isFile) {
                val ext = file.extension.lowercase()
                if (ext in mediaExts && file.path !in seen) {
                    seen.add(file.path)
                    val type = if (ext in videoExts) 2 else 1
                    result.add(Medium(
                        id = null, name = file.name, path = file.absolutePath,
                        parentPath = file.parent ?: "", modified = file.lastModified(),
                        taken = file.lastModified(), size = file.length(), type = type,
                        videoDuration = 0, isFavorite = false, deletedTS = 0L, mediaStoreId = 0, rating = 0,
                    ))
                }
            }
        }
    }

    fun deletePaths(paths: Set<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            paths.forEach { p -> repository.deleteMedium(p) }
            _state.update { s -> s.copy(allMedia = s.allMedia.filter { it.path !in paths }) }
        }
    }
}

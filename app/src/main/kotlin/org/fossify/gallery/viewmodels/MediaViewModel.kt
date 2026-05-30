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
            val media = withContext(Dispatchers.IO) { scanAllMedia() }
            _state.update { it.copy(allMedia = media, isLoading = false, hasMore = false) }
        }
    }

    private fun scanAllMedia(): List<Medium> {
        val ctx = getApplication<Application>()
        // Use Room DB if populated (MediaStore scan), otherwise fallback to broad directory scan
        try {
            val fromDb = ctx.mediaDB.getNewestMedia(1)
            if (fromDb.isNotEmpty()) return ctx.mediaDB.getNewestMedia(2000).sortedByDescending { it.modified }
        } catch (_: Exception) { }
        // Fallback: scan common media directories broadly
        val root = Environment.getExternalStorageDirectory()
        val dirs = listOfNotNull(
            root, File(root, "DCIM"), File(root, "Pictures"), File(root, "Download"),
            File(root, "Movies"), File(root, "Documents"), File(root, "WhatsApp"),
            File(root, "Android/media/com.whatsapp/WhatsApp/Media"),
        ).filter { it.isDirectory }
        val allMedia = mutableListOf<Medium>()
        val seen = mutableSetOf<String>()
        val exts = videoExts + imageExts
        for (dir in dirs) scanFile(dir, allMedia, seen, 0, exts)
        return allMedia.sortedByDescending { it.modified }.take(2000)
    }

    private fun scanFile(dir: File, result: MutableList<Medium>, seen: MutableSet<String>, depth: Int, exts: Set<String>) {
        if (depth > 4 || !dir.isDirectory) return
        val files = dir.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory && !file.name.startsWith(".")) {
                scanFile(file, result, seen, depth + 1, exts)
            } else if (file.isFile) {
                val ext = file.extension.lowercase()
                if (ext in exts && file.path !in seen) {
                    seen.add(file.path)
                    result.add(Medium(null, file.name, file.absolutePath, file.parent ?: "", file.lastModified(),
                        file.lastModified(), file.length(), if (ext in videoExts) 2 else 1, 0, false, 0L, 0L, 0))
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

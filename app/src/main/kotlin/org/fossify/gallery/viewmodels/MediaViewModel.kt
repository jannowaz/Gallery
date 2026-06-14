package org.fossify.gallery.viewmodels

import android.app.Application
import android.os.Environment
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
import org.fossify.gallery.helpers.MediaRepository
import org.fossify.gallery.models.Medium
import java.io.File

data class MediaUiState(
    val allMedia: List<Medium> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val error: String? = null,
)

class MediaViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MediaRepository(application)
    private val _state = MutableStateFlow(MediaUiState())
    val state: StateFlow<MediaUiState> = _state.asStateFlow()

    private val videoExts = setOf("mp4", "mkv", "mov", "3gp", "wmv", "flv", "avi")
    private val imageExts = setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "avif", "bmp", "svg", "apng", "jxl")

    private var loaded = false
    private var currentPage = 0
    private val pageSize = 500
    private var cachedAllMedia: List<Medium> = emptyList()

    init { load() }

    fun load() {
        if (loaded) return
        loaded = true
        currentPage = 0
        doLoad()
    }

    fun refresh() {
        loaded = false
        currentPage = 0
        cachedAllMedia = emptyList()
        doLoad()
    }

    fun loadMore() {
        if (_state.value.isLoadingMore || !_state.value.hasMore) return
        currentPage++
        viewModelScope.launch {
            _state.update { it.copy(isLoadingMore = true) }
            val nextPage = getPage(cachedAllMedia, currentPage)
            if (nextPage.isNotEmpty()) {
                _state.update { it.copy(allMedia = it.allMedia + nextPage, isLoadingMore = false) }
            } else {
                _state.update { it.copy(isLoadingMore = false, hasMore = false) }
            }
        }
    }

    private fun doLoad() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val ctx = getApplication<Application>()
            val media = withContext(Dispatchers.IO) { scanDirectories(ctx) }
            cachedAllMedia = media
            val firstPage = getPage(media, 0)
            _state.update { it.copy(allMedia = firstPage, isLoading = false, hasMore = media.size > pageSize) }
        }
    }

    private fun getPage(media: List<Medium>, page: Int): List<Medium> {
        val start = page * pageSize
        val end = minOf(start + pageSize, media.size)
        if (start >= media.size) return emptyList()
        return media.subList(start, end)
    }

    private fun scanDirectories(ctx: android.content.Context): List<Medium> {
        val allMedia = mutableListOf<Medium>()
        val seen = mutableSetOf<String>()
        val exts = videoExts + imageExts

        try {
            val uri = MediaStore.Files.getContentUri("external")
            val proj = arrayOf(
                MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.DATE_MODIFIED, MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.MIME_TYPE, MediaStore.Files.FileColumns.MEDIA_TYPE,
                MediaStore.Files.FileColumns.DURATION,
            )
            val sel = "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?"
            val args = arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(), MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString())
            ctx.contentResolver.query(uri, proj, sel, args, "${MediaStore.MediaColumns.DATE_MODIFIED} DESC")?.use { c ->
                val dataCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                val nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val dateCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                val sizeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val typeCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
                val durCol = try { c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DURATION) } catch (_: Exception) { -1 }
                val maxItems = 5000
                while (c.moveToNext() && allMedia.size < maxItems) {
                    val path = c.getString(dataCol) ?: continue
                    if (path in seen) continue
                    seen.add(path)
                    val name = c.getString(nameCol) ?: ""
                    val modified = c.getLong(dateCol) * 1000L
                    val size = c.getLong(sizeCol)
                    val mediaType = c.getInt(typeCol)
                    val type = if (mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO) 2 else 1
                    val duration = if (durCol >= 0) (c.getInt(durCol) / 1000) else 0
                    allMedia.add(Medium(null, name, path, File(path).parent ?: "", modified, modified, size, type, duration, false, 0L, 0L, 0))
                }
            }
        } catch (_: Exception) { }

        if (allMedia.isEmpty()) {
            val root = Environment.getExternalStorageDirectory()
            val dirs = listOf(root, File(root, "DCIM"), File(root, "Pictures"), File(root, "Download"), File(root, "Movies")).filter { it.isDirectory }
            for (dir in dirs) scanFile(dir, allMedia, seen, 0, exts)
        }

        return allMedia.sortedByDescending { it.modified }
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
                    result.add(Medium(null, file.name, file.absolutePath, file.parent ?: "", file.lastModified(), file.lastModified(), file.length(), if (ext in videoExts) 2 else 1, 0, false, 0L, 0L, 0))
                }
            }
        }
    }

    fun deletePaths(paths: Set<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            paths.forEach { p -> repository.deleteMedium(p) }
            val removed = paths; _state.update { s -> s.copy(allMedia = s.allMedia.filter { it.path !in removed }) }
        }
    }

    fun softDeletePaths(paths: Set<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            paths.forEach { p -> repository.moveToRecycleBin(p) }
            val removed = paths; _state.update { s -> s.copy(allMedia = s.allMedia.filter { it.path !in removed }) }
        }
    }

    fun undoDeletePaths(paths: Set<String>) {
        viewModelScope.launch(Dispatchers.IO) { paths.forEach { p -> repository.restoreFromRecycleBin(p) } }
    }
}

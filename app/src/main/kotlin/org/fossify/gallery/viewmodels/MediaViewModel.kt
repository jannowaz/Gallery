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
import org.fossify.gallery.extensions.mediaDB
import org.fossify.gallery.helpers.MediaRepository
import org.fossify.gallery.models.Medium
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MonthGroup(val label: String, val items: List<Medium>)

data class MediaUiState(
    val allMedia: List<Medium> = emptyList(),
    val monthGroups: List<MonthGroup> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val error: String? = null,
    val scrollIndex: Int = 0,
    val scrollOffset: Int = 0,
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
    private var sortField = org.fossify.gallery.compose.screens.SortField.DATE
    private var sortDesc = true

    private fun applySort(list: List<Medium>): List<Medium> {
        val sorted = when (sortField) {
            org.fossify.gallery.compose.screens.SortField.NAME -> list.sortedBy { it.name.lowercase() }
            org.fossify.gallery.compose.screens.SortField.DATE -> list.sortedBy { it.modified }
            org.fossify.gallery.compose.screens.SortField.SIZE -> list.sortedBy { it.size }
            org.fossify.gallery.compose.screens.SortField.RATING -> list.sortedBy { it.rating }
        }
        return if (sortDesc) sorted.reversed() else sorted
    }

    fun setSort(field: org.fossify.gallery.compose.screens.SortField, desc: Boolean) {
        if (field == sortField && desc == sortDesc) return
        sortField = field
        sortDesc = desc
        if (cachedAllMedia.isNotEmpty()) {
            cachedAllMedia = applySort(cachedAllMedia)
            currentPage = 0
            _state.update { it.copy(allMedia = getPage(cachedAllMedia, 0), hasMore = cachedAllMedia.size > pageSize) }
            updateGroups()
        }
    }

    init { load() }

    fun load() {
        if (loaded) return
        loaded = true
        currentPage = 0
        rescanAndLoad()
    }

    fun refresh() {
        loaded = false
        currentPage = 0
        cachedAllMedia = emptyList()
        rescanAndLoad()
    }

    fun silentRefresh() {
        val app = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                quickSyncNewMedia(app)
                val media = try { app.mediaDB.getNewestMedia(4000).sortedByDescending { it.modified } } catch (_: Exception) { scanDirectories(app) }
                cachedAllMedia = applySort(media)
                currentPage = 0
                val firstPage = getPage(media, 0)
                _state.update { it.copy(allMedia = firstPage, hasMore = media.size > pageSize) }
                updateGroups()
            } catch (_: Exception) { }
        }
    }

    private fun quickSyncNewMedia(ctx: android.content.Context) {
        try {
            val existingPaths = ctx.mediaDB.getAllPaths().toSet()
            val newMedia = mutableListOf<Medium>()
            val uri = android.provider.MediaStore.Files.getContentUri("external")
            val proj = arrayOf(
                android.provider.MediaStore.MediaColumns.DATA,
                android.provider.MediaStore.MediaColumns.RELATIVE_PATH,
                android.provider.MediaStore.MediaColumns.DISPLAY_NAME,
                android.provider.MediaStore.MediaColumns.DATE_MODIFIED,
                android.provider.MediaStore.MediaColumns.SIZE,
                android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE,
            )
            val sel = "${android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE} = ? OR ${android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE} = ?"
            val args = arrayOf(android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(), android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString())
            val storageRoot = android.os.Environment.getExternalStorageDirectory().absolutePath
            ctx.contentResolver.query(uri, proj, sel, args, "${android.provider.MediaStore.MediaColumns.DATE_MODIFIED} DESC")?.use { c ->
                val dataIdx = c.getColumnIndex(android.provider.MediaStore.MediaColumns.DATA)
                val relPathIdx = c.getColumnIndex(android.provider.MediaStore.MediaColumns.RELATIVE_PATH)
                val nameIdx = c.getColumnIndex(android.provider.MediaStore.MediaColumns.DISPLAY_NAME)
                val dateIdx = c.getColumnIndex(android.provider.MediaStore.MediaColumns.DATE_MODIFIED)
                val sizeIdx = c.getColumnIndex(android.provider.MediaStore.MediaColumns.SIZE)
                val typeIdx = c.getColumnIndex(android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE)
                var scanned = 0
                while (c.moveToNext()) {
                    if (scanned++ >= 4000) break
                    var path = if (dataIdx >= 0) c.getString(dataIdx) else null
                    if (path.isNullOrBlank()) {
                        val relPath = if (relPathIdx >= 0) c.getString(relPathIdx) ?: "" else ""
                        val name = if (nameIdx >= 0) c.getString(nameIdx) ?: "" else ""
                        path = "$storageRoot/$relPath$name"
                    }
                    if (path.isNullOrBlank() || path in existingPaths) continue
                    val name = File(path).name
                    val modified = if (dateIdx >= 0) c.getLong(dateIdx) * 1000L else System.currentTimeMillis()
                    val size = if (sizeIdx >= 0) c.getLong(sizeIdx) else 0L
                    val mediaType = if (typeIdx >= 0) c.getInt(typeIdx) else 1
                    val type = if (mediaType == android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO) 2 else 1
                    newMedia.add(Medium(null, name, path, File(path).parent ?: "", modified, modified, size, type, 0, false, 0L, 0L, 0))
                }
            }
            val deletedPaths = try { ctx.mediaDB.getDeletedMedia().map { it.path }.toSet() } catch (_: Exception) { emptySet() }
            newMedia.addAll(recentDiskMedia(existingPaths + newMedia.map { it.path } + deletedPaths))
            if (newMedia.isNotEmpty()) ctx.mediaDB.insertAllKeepingExisting(newMedia)
        } catch (_: Exception) { }
    }

    private fun rescanAndLoad() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val app = getApplication<Application>()
            val media = withContext(Dispatchers.IO) {
                try { rescanNewMedia(app) } catch (_: Throwable) { }
                val db = try { app.mediaDB.getNewestMedia(4000) } catch (_: Throwable) { emptyList() }
                if (db.isNotEmpty()) db.sortedByDescending { it.modified }
                else try { scanDirectories(app) } catch (_: Throwable) { emptyList() }
            }
            cachedAllMedia = applySort(media)
            val firstPage = getPage(media, 0)
            _state.update { it.copy(allMedia = firstPage, isLoading = false, hasMore = media.size > pageSize) }
            updateGroups()
        }
    }

    private fun rescanNewMedia(ctx: android.content.Context) {
        try {
            val existingPaths = ctx.mediaDB.getAllPaths().toSet()
            val newMedia = mutableListOf<Medium>()
            val uri = android.provider.MediaStore.Files.getContentUri("external")
            val proj = arrayOf(
                android.provider.MediaStore.MediaColumns._ID,
                android.provider.MediaStore.MediaColumns.DATA,
                android.provider.MediaStore.MediaColumns.RELATIVE_PATH,
                android.provider.MediaStore.MediaColumns.DISPLAY_NAME,
                android.provider.MediaStore.MediaColumns.DATE_MODIFIED,
                android.provider.MediaStore.MediaColumns.SIZE,
                android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE,
            )
            val sel = "${android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE} = ? OR ${android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE} = ?"
            val args = arrayOf(android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(), android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString())
            val storageRoot = android.os.Environment.getExternalStorageDirectory().absolutePath
            ctx.contentResolver.query(uri, proj, sel, args, "${android.provider.MediaStore.MediaColumns.DATE_MODIFIED} DESC")?.use { c ->
                val dataIdx = c.getColumnIndex(android.provider.MediaStore.MediaColumns.DATA)
                val relPathIdx = c.getColumnIndex(android.provider.MediaStore.MediaColumns.RELATIVE_PATH)
                val nameIdx = c.getColumnIndex(android.provider.MediaStore.MediaColumns.DISPLAY_NAME)
                val dateIdx = c.getColumnIndex(android.provider.MediaStore.MediaColumns.DATE_MODIFIED)
                val sizeIdx = c.getColumnIndex(android.provider.MediaStore.MediaColumns.SIZE)
                val typeIdx = c.getColumnIndex(android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE)
                var scanned = 0
                while (c.moveToNext()) {
                    if (scanned++ >= 4000) break
                    var path = if (dataIdx >= 0) c.getString(dataIdx) else null
                    if (path.isNullOrBlank()) {
                        val relPath = if (relPathIdx >= 0) c.getString(relPathIdx) ?: "" else ""
                        val name = if (nameIdx >= 0) c.getString(nameIdx) ?: "" else ""
                        path = "$storageRoot/$relPath$name"
                    }
                    if (path.isNullOrBlank() || path in existingPaths) continue
                    val name = File(path).name
                    val modified = if (dateIdx >= 0) c.getLong(dateIdx) * 1000L else System.currentTimeMillis()
                    val size = if (sizeIdx >= 0) c.getLong(sizeIdx) else 0L
                    val mediaType = if (typeIdx >= 0) c.getInt(typeIdx) else 1
                    val type = if (mediaType == android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO) 2 else 1
                    newMedia.add(Medium(null, name, path, File(path).parent ?: "", modified, modified, size, type, 0, false, 0L, 0L, 0))
                }
            }
            val deletedPaths = try { ctx.mediaDB.getDeletedMedia().map { it.path }.toSet() } catch (_: Exception) { emptySet() }
            newMedia.addAll(recentDiskMedia(existingPaths + newMedia.map { it.path } + deletedPaths))
            if (newMedia.isNotEmpty()) ctx.mediaDB.insertAllKeepingExisting(newMedia)
        } catch (_: Exception) { }
    }

    fun loadMore() {
        if (_state.value.isLoadingMore || !_state.value.hasMore) return
        currentPage++
        viewModelScope.launch {
            _state.update { it.copy(isLoadingMore = true) }
            val nextPage = getPage(cachedAllMedia, currentPage)
            if (nextPage.isNotEmpty()) {
                _state.update { it.copy(allMedia = it.allMedia + nextPage, isLoadingMore = false) }
                updateGroups()
            } else {
                _state.update { it.copy(isLoadingMore = false, hasMore = false) }
            }
        }
    }

    private fun getPage(media: List<Medium>, page: Int): List<Medium> {
        val start = page * pageSize
        val end = minOf(start + pageSize, media.size)
        if (start >= media.size) return emptyList()
        return media.subList(start, end)
    }

    private fun recentDiskMedia(knownPaths: Set<String>): List<Medium> {
        val result = mutableListOf<Medium>()
        val exts = videoExts + imageExts
        try {
            val root = Environment.getExternalStorageDirectory()
            val dirs = listOf(
                File(root, "DCIM/Camera"),
                File(root, "DCIM"),
                File(root, "Pictures"),
                File(root, "Pictures/Screenshots"),
                File(root, "Pictures/Screenshot"),
                File(root, "Movies"),
                File(root, "Download"),
            ).filter { it.isDirectory }
            for (dir in dirs) {
                val files = dir.listFiles() ?: continue
                for (f in files) {
                    if (!f.isFile || f.name.startsWith(".")) continue
                    val p = f.absolutePath
                    if (p in knownPaths) continue
                    val ext = f.extension.lowercase()
                    if (ext !in exts) continue
                    val modified = f.lastModified()
                    result.add(Medium(null, f.name, p, f.parent ?: "", modified, modified, f.length(), if (ext in videoExts) 2 else 1, 0, false, 0L, 0L, 0))
                }
            }
        } catch (_: Exception) { }
        return result
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
                val maxItems = 4000
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

    fun saveScrollPosition(index: Int, offset: Int) {
        _state.update { it.copy(scrollIndex = index, scrollOffset = offset) }
    }

    fun allMediaPaths(): List<String> = cachedAllMedia.map { it.path }

    private fun updateGroups() {
        val media = _state.value.allMedia
        _state.update { it.copy(monthGroups = groupByMonth(media)) }
    }

    private fun groupByMonth(media: List<Medium>): List<MonthGroup> {
        if (media.isEmpty()) return emptyList()
        val f = SimpleDateFormat("MMMM yyyy", Locale.GERMANY)
        val g = LinkedHashMap<String, MutableList<Medium>>()
        media.forEach { m ->
            val d = if (m.taken > 0) Date(m.taken) else Date(m.modified)
            val k = f.format(d).replaceFirstChar { it.uppercase() }
            g.getOrPut(k) { mutableListOf() }.add(m)
        }
        return g.map { MonthGroup(it.key, it.value) }
    }
}

package org.fossify.gallery.viewmodels

import android.app.Application
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.gallery.extensions.*
import org.fossify.commons.helpers.*
import org.fossify.gallery.extensions.*
import org.fossify.gallery.helpers.*
import org.fossify.gallery.models.Directory
import org.fossify.gallery.models.Medium
import java.io.File

data class AlbumsUiState(
    val directories: List<Directory> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val searchQuery: String = "",
    val viewType: Int = VIEW_TYPE_GRID,
    val columnCount: Int = 3,
    val sortOrder: Int = SORT_BY_DATE_MODIFIED or SORT_DESCENDING,
    val error: String? = null,
)

@OptIn(FlowPreview::class)
class AlbumsViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(AlbumsUiState())
    val state: StateFlow<AlbumsUiState> = _state.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    private var fetcher: MediaFetcher? = null
    private val dirListLock = Any()
    private var fullDirList = ArrayList<Directory>()

    init {
        _searchQuery
            .debounce(300L)
            .distinctUntilChanged()
            .onEach { query ->
                _state.update { it.copy(searchQuery = query) }
            }
            .launchIn(viewModelScope)
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            withContext(Dispatchers.IO) {
                val ctx = getApplication<Application>().applicationContext
                ctx.getCachedDirectories(false, false) { dirs ->
                    val processed = ctx.addTempFolderIfNeeded(ArrayList(dirs))
                    val sorted = ctx.getSortedDirectories(processed)
                    synchronized(dirListLock) { fullDirList = ArrayList(sorted) }
                    updateDirectories(sorted)
                    recheckDirectories(ArrayList(sorted))
                }
            }
            _state.update { it.copy(isLoading = false) }
        }
    }

    private fun recheckDirectories(dirs: ArrayList<Directory>) {
        viewModelScope.launch(Dispatchers.IO) {
            val ctx = getApplication<Application>().applicationContext
            val config = ctx.config
            val getImagesOnly = false
            val getVideosOnly = false
            val favoritePaths = ctx.getFavoritePaths()
            val albumCovers = config.parseAlbumCovers()
            val includedFolders = config.includedFolders
            val noMediaFolders = ctx.getNoMediaFoldersSync()
            val getProperFileSize = config.directorySorting and SORT_BY_SIZE != 0

            val fetcher = MediaFetcher(ctx)
            this@AlbumsViewModel.fetcher = fetcher

            val lastModifieds = fetcher.getLastModifieds()
            val dateTakens = fetcher.getDateTakens()
            var changed = false

            for (directory in dirs) {
                if (!isActive) return@launch
                val sorting = config.getFolderSorting(directory.path)
                val grouping = config.getFolderGrouping(directory.path)
                val getProperDateTaken = config.directorySorting and SORT_BY_DATE_TAKEN != 0
                        || sorting and SORT_BY_DATE_TAKEN != 0
                        || grouping and GROUP_BY_DATE_TAKEN_DAILY != 0
                        || grouping and GROUP_BY_DATE_TAKEN_MONTHLY != 0
                val getProperLastModified = config.directorySorting and SORT_BY_DATE_MODIFIED != 0
                            || sorting and SORT_BY_DATE_MODIFIED != 0
                            || grouping and GROUP_BY_LAST_MODIFIED_DAILY != 0
                            || grouping and GROUP_BY_LAST_MODIFIED_MONTHLY != 0

                val curMedia = fetcher.getFilesFrom(
                    curPath = directory.path,
                    isPickImage = getImagesOnly,
                    isPickVideo = getVideosOnly,
                    getProperDateTaken = getProperDateTaken,
                    getProperLastModified = getProperLastModified,
                    getProperFileSize = getProperFileSize,
                    favoritePaths = favoritePaths,
                    getVideoDurations = false,
                    lastModifieds = lastModifieds,
                    dateTakens = dateTakens,
                    android11Files = null
                )

                if (curMedia.isNotEmpty()) {
                    val newDir = ctx.createDirectoryFromMedia(
                        path = directory.path, curMedia = curMedia,
                        albumCovers = albumCovers, hiddenString = "Hidden",
                        includedFolders = includedFolders, getProperFileSize = getProperFileSize,
                        noMediaFolders = noMediaFolders
                    )
                    if (directory.copy(subfoldersCount = 0, subfoldersMediaCount = 0) != newDir) {
                        directory.apply {
                            tmb = newDir.tmb; name = newDir.name; mediaCnt = newDir.mediaCnt
                            modified = newDir.modified; taken = newDir.taken; this@apply.size = newDir.size
                            types = newDir.types
                        }
                        changed = true
                        ctx.updateDBDirectory(directory)
                    }
                }
            }

            if (changed) {
                val sorted = ctx.getSortedDirectories(dirs)
                synchronized(dirListLock) { fullDirList = ArrayList(sorted) }
                updateDirectories(sorted)
            }
        }
    }

    private fun updateDirectories(dirs: List<Directory>) {
        val listCopy = synchronized(dirListLock) { ArrayList(fullDirList) }
        _state.update { it.copy(directories = listCopy) }
    }

    fun setSearchQuery(query: String) { _searchQuery.value = query }

    fun setViewType(vt: Int) {
        getApplication<Application>().applicationContext.config.viewTypeFolders = vt
        _state.update { it.copy(viewType = vt) }
    }

    fun setColumnCount(cc: Int) {
        getApplication<Application>().applicationContext.config.dirColumnCnt = cc
        _state.update { it.copy(columnCount = cc) }
    }

    fun setSortOrder(order: Int) {
        val ctx = getApplication<Application>().applicationContext
        ctx.config.directorySorting = order
        val listCopy = synchronized(dirListLock) { ArrayList(fullDirList) }
        ctx.getSortedDirectories(listCopy).let { sorted ->
            synchronized(dirListLock) { fullDirList = ArrayList(sorted) }
            updateDirectories(sorted)
        }
        _state.update { it.copy(sortOrder = order) }
    }
}

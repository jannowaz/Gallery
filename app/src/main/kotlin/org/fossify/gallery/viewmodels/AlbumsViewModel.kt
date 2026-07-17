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
import org.fossify.gallery.helpers.RefreshBus
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
        // Previously this ViewModel never listened for data changes at all - it only ever loaded
        // once in init{}, so newly downloaded/moved/deleted media only showed up here after the
        // whole ViewModel got recreated (e.g. a Viewer round-trip disposes/recomposes Home). Every
        // other data-driven screen in the app (Collections/Favorites/TagBrowser/Explorer/filtered
        // Media) already subscribes to RefreshBus for exactly this reason.
        viewModelScope.launch {
            RefreshBus.events.collect { silentReload() }
        }
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            fetchAndApplyDirectories()
            _state.update { it.copy(isLoading = false) }
        }
    }

    // Same fetch as load(), but without toggling isLoading - a RefreshBus-triggered background
    // refresh should update the grid in place, not flash the skeleton loader over content the user
    // is already looking at (same pattern as MediaScreen's silentRefresh()/TagBrowserScreen's fix).
    private fun silentReload() {
        viewModelScope.launch { fetchAndApplyDirectories() }
    }

    private suspend fun fetchAndApplyDirectories() {
        withContext(Dispatchers.IO) {
            val ctx = getApplication<Application>().applicationContext
            // Self-heal the directory cache from the media table before reading it: deletes/moves/
            // bin-empties change media rows without running a store sync, and without this rebuild
            // their folders lingered here as ghost albums with stale counts (and brand-new folders
            // were missing entirely).
            org.fossify.gallery.helpers.MediaRepository(ctx).syncDirectoriesFromMedia()
            ctx.getCachedDirectories(false, false) { dirs ->
                val processed = ctx.addTempFolderIfNeeded(ArrayList(dirs))
                val sorted = ctx.getSortedDirectories(processed)
                synchronized(dirListLock) { fullDirList = ArrayList(sorted) }
                updateDirectories(sorted)
                recheckDirectories(ArrayList(sorted))
            }
        }
    }

    // recheckDirectories re-scans every directory from disk (via MediaFetcher.getFilesFrom), which on
    // a folder with a six-figure item count materializes a Medium list that size - expensive enough
    // in time and memory that two runs must never overlap. Without this cancel-previous-first guard,
    // a RefreshBus event landing while the previous recheck (of the same huge folder) was still
    // running started a second full rescan concurrently, doubling peak memory at exactly the moment
    // the heap was already under pressure from that same list - a real OutOfMemoryError observed on
    // a ~200k-media real-device library. Same cancel-previous-job pattern already used by
    // MediaViewModel.prefetchSortedPathsAsync/prefetchFilteredPathsAsync for the equivalent reason.
    private var recheckJob: kotlinx.coroutines.Job? = null
    private fun recheckDirectories(dirs: ArrayList<Directory>) {
        recheckJob?.cancel()
        recheckJob = viewModelScope.launch(Dispatchers.IO) {
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

            for ((index, directory) in dirs.withIndex()) {
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
                        // Replace with a new instance rather than mutating `directory` in place: this
                        // same object is also referenced (via the shallow ArrayList copies threaded
                        // through fetchAndApplyDirectories) by fullDirList and the already-emitted
                        // _state.value.directories - mutating it retroactively changed the "old" state
                        // snapshot too, so the equals()-based dedup in MutableStateFlow.update{} below
                        // could see old/new directories as identical and silently drop the emission,
                        // leaving the grid showing a stale thumbnail/name/count until something else
                        // happened to trigger a recomposition.
                        val updatedDirectory = directory.copy(
                            tmb = newDir.tmb, name = newDir.name, mediaCnt = newDir.mediaCnt,
                            modified = newDir.modified, taken = newDir.taken, size = newDir.size,
                            types = newDir.types
                        )
                        dirs[index] = updatedDirectory
                        changed = true
                        ctx.updateDBDirectory(updatedDirectory)
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

package org.fossify.gallery.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.fossify.gallery.models.Directory
import org.fossify.gallery.extensions.getCachedDirectories
import org.fossify.gallery.extensions.addTempFolderIfNeeded
import org.fossify.gallery.extensions.getSortedDirectories
import org.fossify.commons.helpers.ensureBackgroundThread

import org.fossify.gallery.helpers.MediaFetcher
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.getFavoritePaths
import org.fossify.gallery.extensions.getNoMediaFoldersSync
import org.fossify.gallery.extensions.mediaDB
import org.fossify.commons.helpers.SORT_BY_SIZE
import org.fossify.commons.helpers.SORT_BY_DATE_TAKEN
import org.fossify.commons.helpers.SORT_BY_DATE_MODIFIED
import org.fossify.commons.helpers.FAVORITES
import org.fossify.gallery.helpers.GROUP_BY_DATE_TAKEN_DAILY
import org.fossify.gallery.helpers.GROUP_BY_DATE_TAKEN_MONTHLY
import org.fossify.gallery.helpers.GROUP_BY_LAST_MODIFIED_DAILY
import org.fossify.gallery.helpers.GROUP_BY_LAST_MODIFIED_MONTHLY
import org.fossify.gallery.helpers.RECYCLE_BIN
import org.fossify.gallery.helpers.LOCATION_INTERNAL
import org.fossify.gallery.helpers.SHOW_ALL
import org.fossify.gallery.helpers.GROUP_DESCENDING
import org.fossify.gallery.extensions.createDirectoryFromMedia
import org.fossify.gallery.extensions.getDirectorySortingValue
import org.fossify.gallery.extensions.updateDBDirectory
import org.fossify.gallery.extensions.getCachedMedia
import org.fossify.gallery.interfaces.DirectoryDao
import org.fossify.gallery.interfaces.MediumDao
import org.fossify.gallery.extensions.directoryDB
import org.fossify.gallery.models.Medium
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fossify.commons.extensions.getFilenameFromPath
import org.fossify.commons.helpers.SORT_BY_NAME
import org.fossify.commons.helpers.SORT_BY_PATH
import org.fossify.gallery.extensions.getPathLocation
import org.fossify.gallery.helpers.TYPE_IMAGES
import org.fossify.gallery.helpers.TYPE_VIDEOS
import org.fossify.gallery.helpers.TYPE_GIFS
import org.fossify.gallery.helpers.TYPE_RAWS
import org.fossify.gallery.helpers.TYPE_SVGS
import org.fossify.gallery.helpers.TYPE_PORTRAITS
import org.fossify.gallery.extensions.getUpdatedDeletedMedia
import java.io.File
import org.fossify.commons.helpers.SORT_BY_COUNT
import org.fossify.commons.extensions.internalStoragePath
import org.fossify.commons.extensions.getDoesFilePathExist

data class MainUiState(
    val directories: List<Directory> = emptyList(),
    val isLoading: Boolean = false,
    val currentTab: Int = 1, // 0: Media, 1: Folders, 2: Explorer, 3: Collections, 4: Favorites
    val error: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState
    private var lastMediaFetcher: MediaFetcher? = null

    fun setTab(tab: Int) {
        _uiState.value = _uiState.value.copy(currentTab = tab)
    }

    fun updateDirectories(dirs: List<Directory>) {
        _uiState.value = _uiState.value.copy(directories = dirs)
    }

    fun loadDirectories(getVideos: Boolean, getImages: Boolean) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            val context = getApplication<Application>().applicationContext
            context.getCachedDirectories(getVideos, getImages) { dirs ->
                val processedDirs = context.addTempFolderIfNeeded(dirs)
                val config = context.config
                if (config.showRecycleBinAtFolders && !config.showRecycleBinLast && !processedDirs.any { it.isRecycleBin() }) {
                    if (context.mediaDB.getDeletedMediaCount() > 0) {
                        val recycleBin = Directory(
                            id = null,
                            path = RECYCLE_BIN,
                            tmb = "",
                            name = context.getString(org.fossify.commons.R.string.recycle_bin),
                            mediaCnt = 0,
                            modified = 0,
                            taken = 0,
                            size = 0,
                            location = LOCATION_INTERNAL,
                            types = 0,
                            sortValue = ""
                        )
                        processedDirs.add(0, recycleBin)
                    }
                }

                if (!processedDirs.any { it.path == FAVORITES }) {
                    if (context.mediaDB.getFavoritesCount() > 0) {
                        val favorites = Directory(
                            id = null,
                            path = FAVORITES,
                            tmb = "",
                            name = context.getString(org.fossify.commons.R.string.favorites),
                            mediaCnt = 0,
                            modified = 0,
                            taken = 0,
                            size = 0,
                            location = LOCATION_INTERNAL,
                            types = 0,
                            sortValue = ""
                        )
                        processedDirs.add(0, favorites)
                    }
                }

                val sortedDirs = context.getSortedDirectories(processedDirs)
                _uiState.value = _uiState.value.copy(
                    directories = sortedDirs,
                    isLoading = false
                )
                
                // Start background recheck
                recheckDirectories(ArrayList(sortedDirs), getVideos, getImages)
            }
        }
    }

    private fun recheckDirectories(dirs: ArrayList<Directory>, getVideos: Boolean, getImages: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>().applicationContext
            val config = context.config
            val getImagesOnly = getImages && !getVideos
            val getVideosOnly = getVideos && !getImages
            val favoritePaths = context.getFavoritePaths()
            val albumCovers = config.parseAlbumCovers()
            val includedFolders = config.includedFolders
            val noMediaFolders = context.getNoMediaFoldersSync()
            val getProperFileSize = config.directorySorting and SORT_BY_SIZE != 0
            
            val fetcher = MediaFetcher(context)
            lastMediaFetcher = fetcher
            
            val lastModifieds = fetcher.getLastModifieds()
            val dateTakens = fetcher.getDateTakens()
            
            val dirPathsToRemove = ArrayList<String>()
            val dirsCopy = ArrayList(dirs)
            
            for (directory in dirsCopy) {
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

                val curMedia = when (directory.path) {
                    FAVORITES -> context.mediaDB.getFavorites() as ArrayList<Medium>
                    RECYCLE_BIN -> context.getUpdatedDeletedMedia()
                    else -> fetcher.getFilesFrom(
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
                }

                if (curMedia.isEmpty()) {
                    if (directory.path != config.tempFolderPath) {
                        dirPathsToRemove.add(directory.path)
                    }
                    continue
                }

                val newDir = if (directory.path == FAVORITES || directory.path == RECYCLE_BIN) {
                    val isSortingAscending = config.directorySorting and org.fossify.commons.helpers.SORT_DESCENDING == 0
                    val sortedMedia = if (isSortingAscending) {
                        curMedia.sortedBy { it.modified }
                    } else {
                        curMedia.sortedByDescending { it.modified }
                    }
                    
                    Directory(
                        id = null,
                        path = directory.path,
                        tmb = sortedMedia.firstOrNull()?.path ?: "",
                        name = directory.name,
                        mediaCnt = curMedia.size,
                        modified = sortedMedia.firstOrNull()?.modified ?: 0L,
                        taken = sortedMedia.firstOrNull()?.taken ?: 0L,
                        size = curMedia.sumOf { it.size },
                        location = LOCATION_INTERNAL,
                        types = curMedia.getMediaTypes(),
                        sortValue = context.getDirectorySortingValue(curMedia, directory.path, directory.name, curMedia.sumOf { it.size }, curMedia.size)
                    )
                } else {
                    context.createDirectoryFromMedia(
                        path = directory.path,
                        curMedia = curMedia,
                        albumCovers = albumCovers,
                        hiddenString = "Hidden",
                        includedFolders = includedFolders,
                        getProperFileSize = getProperFileSize,
                        noMediaFolders = noMediaFolders
                    )
                }
                
                if (directory.copy(subfoldersCount = 0, subfoldersMediaCount = 0) != newDir) {
                    directory.apply {
                        tmb = newDir.tmb
                        name = newDir.name
                        mediaCnt = newDir.mediaCnt
                        modified = newDir.modified
                        taken = newDir.taken
                        size = newDir.size
                        types = newDir.types
                        sortValue = context.getDirectorySortingValue(curMedia, path, name, size, mediaCnt)
                    }
                    
                    _uiState.value = if (_uiState.value.currentTab == 1) {
                        _uiState.value.copy(directories = ArrayList(dirs))
                    } else {
                        _uiState.value
                    }
                    context.updateDBDirectory(directory)
                }
            }
            
            if (dirPathsToRemove.isNotEmpty()) {
                val toRemove = dirs.filter { dirPathsToRemove.contains(it.path) }
                dirs.removeAll(toRemove)
                toRemove.forEach { context.directoryDB.deleteDirPath(it.path) }
                _uiState.value = _uiState.value.copy(directories = ArrayList(dirs))
            }
            
            if (dirs.size > 50) {
                excludeSpamFolders(dirs)
            }
        }
    }

    private fun excludeSpamFolders(dirs: List<Directory>) {
        val context = getApplication<Application>().applicationContext
        val config = context.config
        val internalPath = context.internalStoragePath
        val checkedPaths = ArrayList<String>()
        val oftenRepeatedPaths = ArrayList<String>()
        val paths = dirs.map { it.path.removePrefix(internalPath) }
        
        paths.forEach {
            val parts = it.split("/")
            var currentString = ""
            for (i in 0 until parts.size) {
                currentString += "${parts[i]}/"
                if (!checkedPaths.contains(currentString)) {
                    val cnt = paths.count { it.startsWith(currentString) }
                    if (cnt > 50 && currentString.startsWith("/Android/data", true)) {
                        oftenRepeatedPaths.add(currentString)
                    }
                }
                checkedPaths.add(currentString)
            }
        }

        val substringToRemove = oftenRepeatedPaths.filter {
            val path = it
            it == "/" || oftenRepeatedPaths.any { it != path && it.startsWith(path) }
        }

        oftenRepeatedPaths.removeAll(substringToRemove)
        oftenRepeatedPaths.forEach {
            val fullPath = "$internalPath/$it"
            if (context.getDoesFilePathExist(fullPath)) {
                config.addExcludedFolder(fullPath)
            }
        }
    }

    private fun List<Medium>.getMediaTypes(): Int {
        var types = 0
        if (any { it.type == TYPE_IMAGES }) types = types or TYPE_IMAGES
        if (any { it.type == TYPE_VIDEOS }) types = types or TYPE_VIDEOS
        if (any { it.type == TYPE_GIFS }) types = types or TYPE_GIFS
        if (any { it.type == TYPE_RAWS }) types = types or TYPE_RAWS
        if (any { it.type == TYPE_SVGS }) types = types or TYPE_SVGS
        if (any { it.type == TYPE_PORTRAITS }) types = types or TYPE_PORTRAITS
        return types
    }
}

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

    fun loadDirectories(getVideos: Boolean, getImages: Boolean) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            val context = getApplication<Application>().applicationContext
            context.getCachedDirectories(getVideos, getImages) { dirs ->
                val processedDirs = context.addTempFolderIfNeeded(dirs)
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
            
            // Recheck existing directories
            for (directory in dirs) {
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
                    val newDir = context.createDirectoryFromMedia(
                        path = directory.path,
                        curMedia = curMedia,
                        albumCovers = albumCovers,
                        hiddenString = "Hidden",
                        includedFolders = includedFolders,
                        getProperFileSize = getProperFileSize,
                        noMediaFolders = noMediaFolders
                    )
                    
                    if (directory.copy(subfoldersCount = 0, subfoldersMediaCount = 0) != newDir) {
                        directory.apply {
                            tmb = newDir.tmb
                            name = newDir.name
                            mediaCnt = newDir.mediaCnt
                            modified = newDir.modified
                            taken = newDir.taken
                            this@apply.size = newDir.size
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
            }
        }
    }
}

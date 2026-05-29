package org.fossify.gallery.compose.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.fossify.gallery.extensions.config

data class TabViewSettings(
    val media: ViewSettings = ViewSettings(),
    val albums: ViewSettings = ViewSettings(columnCount = 3, displayMode = DisplayMode.NORMAL),
    val explorerAlbums: ViewSettings = ViewSettings(columnCount = 3, displayMode = DisplayMode.NORMAL),
    val explorerMedia: ViewSettings = ViewSettings(),
    val favorites: ViewSettings = ViewSettings(columnCount = 3, displayMode = DisplayMode.NORMAL),
)

class ViewSettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val _settings = MutableStateFlow(TabViewSettings())
    val settings: StateFlow<TabViewSettings> = _settings.asStateFlow()

    private val _settingsMode = MutableStateFlow(SettingsMode.ALBUMS)
    val settingsMode: StateFlow<SettingsMode> = _settingsMode.asStateFlow()

    init { loadFromConfig() }

    fun setSettingsMode(mode: SettingsMode) { _settingsMode.value = mode }

    fun updateMedia(s: ViewSettings) {
        _settings.value = _settings.value.copy(media = s)
        persistMedia(s)
    }

    fun updateAlbums(s: ViewSettings) {
        _settings.value = _settings.value.copy(albums = s)
        persistAlbums(s)
    }

    fun updateExplorerAlbums(s: ViewSettings) {
        _settings.value = _settings.value.copy(explorerAlbums = s)
        persistExplorerAlbums(s)
    }

    fun updateExplorerMedia(s: ViewSettings) {
        _settings.value = _settings.value.copy(explorerMedia = s)
        persistExplorerMedia(s)
    }

    fun updateFavorites(s: ViewSettings) {
        _settings.value = _settings.value.copy(favorites = s)
        persistFavorites(s)
    }

    private fun loadFromConfig() {
        val ctx = getApplication<Application>().applicationContext
        val c = ctx.config
        _settings.value = TabViewSettings(
            media = ViewSettings(
                viewType = ViewType.from(c.viewTypeFiles),
                columnCount = c.mediaColumnCnt.coerceIn(2, 6),
                showFileNames = c.mediaShowFileNames,
                roundedCorners = c.fileRoundedCorners,
                sortBy = SortField.from(c.mediaSortBy),
                sortDesc = c.mediaSortDesc,
                spacing = c.thumbnailSpacing,
            ),
            albums = ViewSettings(
                viewType = ViewType.from(c.viewTypeFolders),
                columnCount = c.dirColumnCnt.coerceIn(2, 6),
                displayMode = DisplayMode.from(c.folderDisplayMode),
                showFileNames = c.displayFileNames,
                roundedCorners = c.fileRoundedCorners,
                sortBy = SortField.from(c.folderSortBy),
                sortDesc = c.folderSortDesc,
                spacing = c.thumbnailSpacing,
                showFolderThumbnails = c.showFolderThumbnails,
            ),
            explorerAlbums = ViewSettings(
                viewType = ViewType.from(c.viewTypeFolders),
                columnCount = c.dirColumnCnt.coerceIn(2, 6),
                displayMode = DisplayMode.from(c.folderDisplayMode),
                showFileNames = c.displayFileNames,
                roundedCorners = c.fileRoundedCorners,
                sortBy = SortField.from(c.folderSortBy),
                sortDesc = c.folderSortDesc,
                spacing = c.thumbnailSpacing,
                showFolderThumbnails = c.showFolderThumbnails,
            ),
            explorerMedia = ViewSettings(
                viewType = ViewType.from(c.viewTypeFiles),
                columnCount = c.mediaColumnCnt.coerceIn(2, 6),
                showFileNames = c.mediaShowFileNames,
                roundedCorners = c.fileRoundedCorners,
                sortBy = SortField.from(c.mediaSortBy),
                sortDesc = c.mediaSortDesc,
                spacing = c.thumbnailSpacing,
            ),
            favorites = ViewSettings(
                viewType = ViewType.from(c.viewTypeFolders),
                columnCount = c.dirColumnCnt.coerceIn(2, 6),
                displayMode = DisplayMode.from(c.folderDisplayMode),
                showFileNames = c.displayFileNames,
                roundedCorners = c.fileRoundedCorners,
                sortBy = SortField.from(c.folderSortBy),
                sortDesc = c.folderSortDesc,
                spacing = c.thumbnailSpacing,
                showFolderThumbnails = c.showFolderThumbnails,
            ),
        )
    }

    private fun persistMedia(s: ViewSettings) {
        val ctx = getApplication<Application>().applicationContext
        ctx.config.viewTypeFiles = s.viewType.value
        ctx.config.mediaColumnCnt = s.columnCount
        ctx.config.mediaShowFileNames = s.showFileNames
        ctx.config.fileRoundedCorners = s.roundedCorners
        ctx.config.mediaSortBy = s.sortBy.value
        ctx.config.mediaSortDesc = s.sortDesc
        ctx.config.thumbnailSpacing = s.spacing
    }

    private fun persistAlbums(s: ViewSettings) {
        val ctx = getApplication<Application>().applicationContext
        ctx.config.viewTypeFolders = s.viewType.value
        ctx.config.dirColumnCnt = s.columnCount
        ctx.config.folderDisplayMode = s.displayMode.value
        ctx.config.displayFileNames = s.showFileNames
        ctx.config.fileRoundedCorners = s.roundedCorners
        ctx.config.folderSortBy = s.sortBy.value
        ctx.config.folderSortDesc = s.sortDesc
        ctx.config.thumbnailSpacing = s.spacing
        ctx.config.showFolderThumbnails = s.showFolderThumbnails
    }

    private fun persistExplorerAlbums(s: ViewSettings) {
        val ctx = getApplication<Application>().applicationContext
        ctx.config.viewTypeFolders = s.viewType.value
        ctx.config.dirColumnCnt = s.columnCount
        ctx.config.folderDisplayMode = s.displayMode.value
        ctx.config.displayFileNames = s.showFileNames
        ctx.config.fileRoundedCorners = s.roundedCorners
        ctx.config.folderSortBy = s.sortBy.value
        ctx.config.folderSortDesc = s.sortDesc
        ctx.config.thumbnailSpacing = s.spacing
        ctx.config.showFolderThumbnails = s.showFolderThumbnails
    }

    private fun persistExplorerMedia(s: ViewSettings) {
        val ctx = getApplication<Application>().applicationContext
        ctx.config.viewTypeFiles = s.viewType.value
        ctx.config.mediaColumnCnt = s.columnCount
        ctx.config.mediaShowFileNames = s.showFileNames
        ctx.config.fileRoundedCorners = s.roundedCorners
        ctx.config.mediaSortBy = s.sortBy.value
        ctx.config.mediaSortDesc = s.sortDesc
        ctx.config.thumbnailSpacing = s.spacing
    }

    private fun persistFavorites(s: ViewSettings) {
        val ctx = getApplication<Application>().applicationContext
        ctx.config.viewTypeFolders = s.viewType.value
        ctx.config.dirColumnCnt = s.columnCount
        ctx.config.folderDisplayMode = s.displayMode.value
        ctx.config.displayFileNames = s.showFileNames
        ctx.config.fileRoundedCorners = s.roundedCorners
        ctx.config.folderSortBy = s.sortBy.value
        ctx.config.folderSortDesc = s.sortDesc
        ctx.config.thumbnailSpacing = s.spacing
        ctx.config.showFolderThumbnails = s.showFolderThumbnails
    }
}

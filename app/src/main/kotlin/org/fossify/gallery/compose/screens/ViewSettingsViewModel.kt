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
    val folderMedia: ViewSettings = ViewSettings(),
    val favorites: ViewSettings = ViewSettings(columnCount = 3, displayMode = DisplayMode.NORMAL),
    val collections: ViewSettings = ViewSettings(columnCount = 3, displayMode = DisplayMode.NORMAL),
    val tags: ViewSettings = ViewSettings(columnCount = 3, displayMode = DisplayMode.NORMAL),
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

    fun updateCollections(s: ViewSettings) {
        _settings.value = _settings.value.copy(collections = s)
        val ctx = getApplication<Application>().applicationContext
        ctx.config.collectionsViewType = s.viewType.value
        ctx.config.collectionsColumnCnt = s.columnCount
        ctx.config.fileRoundedCorners = s.roundedCorners
        ctx.config.thumbnailSpacing = s.spacing
    }

    fun updateTags(s: ViewSettings) {
        _settings.value = _settings.value.copy(tags = s)
        val ctx = getApplication<Application>().applicationContext
        ctx.config.tagsViewType = s.viewType.value
        ctx.config.tagsColumnCnt = s.columnCount
        ctx.config.fileRoundedCorners = s.roundedCorners
        ctx.config.thumbnailSpacing = s.spacing
    }

    fun updateFolderMedia(s: ViewSettings) {
        _settings.value = _settings.value.copy(folderMedia = s)
        persistFolderMedia(s)
    }

    private fun loadFromConfig() {
        val ctx = getApplication<Application>().applicationContext
        val c = ctx.config
        val prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(ctx)
        // Each tab loads its own sort from Config, except the Explorer tab which has its own keys
        // stored in SharedPreferences so it never interferes with Albums / Media.
        fun loadExplorerSort(key: String, fallbackSort: Int, fallbackDesc: Boolean): Pair<Int, Boolean> {
            val s = prefs.getString(key, null)
            if (s != null) {
                val parts = s.split(",")
                val sort = parts.getOrNull(0)?.toIntOrNull()
                if (parts.size == 2 && sort != null) return sort to (parts[1] == "true")
            }
            return fallbackSort to fallbackDesc
        }
        val (exAlbSort, exAlbDesc) = loadExplorerSort("explorer_albums_sort", c.folderSortBy, c.folderSortDesc)
        val (exMedSort, exMedDesc) = loadExplorerSort("explorer_media_sort", c.mediaSortBy, c.mediaSortDesc)
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
                sortBy = SortField.from(exAlbSort),
                sortDesc = exAlbDesc,
                spacing = c.thumbnailSpacing,
                showFolderThumbnails = c.showFolderThumbnails,
            ),
            explorerMedia = ViewSettings(
                viewType = ViewType.from(c.viewTypeFiles),
                columnCount = c.mediaColumnCnt.coerceIn(2, 6),
                showFileNames = c.mediaShowFileNames,
                roundedCorners = c.fileRoundedCorners,
                sortBy = SortField.from(exMedSort),
                sortDesc = exMedDesc,
                spacing = c.thumbnailSpacing,
            ),
            folderMedia = ViewSettings(
                viewType = ViewType.from(c.folderMediaViewType),
                columnCount = c.folderMediaColumnCnt.coerceIn(2, 6),
                showFileNames = c.folderMediaShowFileNames,
                roundedCorners = c.fileRoundedCorners,
                sortBy = SortField.from(c.folderMediaSortBy),
                sortDesc = c.folderMediaSortDesc,
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
            collections = ViewSettings(
                viewType = ViewType.from(c.collectionsViewType),
                columnCount = c.collectionsColumnCnt.coerceIn(2, 6),
                roundedCorners = c.fileRoundedCorners,
                spacing = c.thumbnailSpacing,
                showFolderThumbnails = c.showFolderThumbnails,
            ),
            tags = ViewSettings(
                viewType = ViewType.from(c.tagsViewType),
                columnCount = c.tagsColumnCnt.coerceIn(2, 6),
                roundedCorners = c.fileRoundedCorners,
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
        ctx.config.thumbnailSpacing = s.spacing
        ctx.config.showFolderThumbnails = s.showFolderThumbnails
        // Save explorer Album sort independently so it never interferes with the Albums tab.
        val prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(ctx)
        prefs.edit().putString("explorer_albums_sort", "${s.sortBy.value},${s.sortDesc}").apply()
    }

    private fun persistExplorerMedia(s: ViewSettings) {
        val ctx = getApplication<Application>().applicationContext
        ctx.config.viewTypeFiles = s.viewType.value
        ctx.config.mediaColumnCnt = s.columnCount
        ctx.config.mediaShowFileNames = s.showFileNames
        ctx.config.fileRoundedCorners = s.roundedCorners
        ctx.config.thumbnailSpacing = s.spacing
        // Save explorer File sort independently so it never interferes with the Media tab.
        val prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(ctx)
        prefs.edit().putString("explorer_media_sort", "${s.sortBy.value},${s.sortDesc}").apply()
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

    private fun persistFolderMedia(s: ViewSettings) {
        val ctx = getApplication<Application>().applicationContext
        ctx.config.folderMediaViewType = s.viewType.value
        ctx.config.folderMediaColumnCnt = s.columnCount
        ctx.config.folderMediaShowFileNames = s.showFileNames
        ctx.config.fileRoundedCorners = s.roundedCorners
        ctx.config.folderMediaSortBy = s.sortBy.value
        ctx.config.folderMediaSortDesc = s.sortDesc
        ctx.config.thumbnailSpacing = s.spacing
    }
}

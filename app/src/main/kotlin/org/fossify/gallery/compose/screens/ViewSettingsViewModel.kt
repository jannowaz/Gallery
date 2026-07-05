package org.fossify.gallery.compose.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
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

    // Off the main thread - loadFromConfig() reads a couple dozen SharedPreferences values, which
    // on a cold app start is the first access to that prefs file and was a confirmed
    // StrictMode DiskReadViolation (~440ms, causing dropped frames right after first launch) when
    // run synchronously here as part of ViewModel construction during the first composition.
    // Tabs render with the ViewSettings() defaults for the brief moment until this completes.
    init { viewModelScope.launch(Dispatchers.IO) { loadFromConfig() } }

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
        // The Explorer tab (folder + media listing) and Favorites keep their own isolated
        // SharedPreferences state so browsing/sorting there never interferes with the dedicated
        // Albums / Media tabs, which read straight from Config.
        fun loadFavoritesSort(fallbackSort: Int, fallbackDesc: Boolean): Pair<Int, Boolean> {
            val s = prefs.getString("favorites_sort", null)
            if (s != null) {
                val parts = s.split(",")
                val sort = parts.getOrNull(0)?.toIntOrNull()
                if (parts.size == 2 && sort != null) return sort to (parts[1] == "true")
            }
            return fallbackSort to fallbackDesc
        }
        val (favSort, favDesc) = loadFavoritesSort(c.folderSortBy, c.folderSortDesc)
        val explorerAlbumsDefault = ViewSettings(
            viewType = ViewType.from(c.viewTypeFolders),
            columnCount = c.dirColumnCnt.coerceIn(2, 6),
            displayMode = DisplayMode.from(c.folderDisplayMode),
            showFileNames = c.displayFileNames,
            roundedCorners = c.fileRoundedCorners,
            sortBy = SortField.from(c.folderSortBy),
            sortDesc = c.folderSortDesc,
            spacing = c.thumbnailSpacing,
            showFolderThumbnails = c.showFolderThumbnails,
        )
        val explorerMediaDefault = ViewSettings(
            viewType = ViewType.from(c.viewTypeFiles),
            columnCount = c.mediaColumnCnt.coerceIn(2, 6),
            showFileNames = c.mediaShowFileNames,
            roundedCorners = c.fileRoundedCorners,
            sortBy = SortField.from(c.mediaSortBy),
            sortDesc = c.mediaSortDesc,
            spacing = c.thumbnailSpacing,
        )
        val explorerAlbumsSettings = deserializeViewSettings(prefs.getString("explorer_albums_settings", null), explorerAlbumsDefault)
        val explorerMediaSettings = deserializeViewSettings(prefs.getString("explorer_media_settings", null), explorerMediaDefault)
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
            explorerAlbums = explorerAlbumsSettings,
            explorerMedia = explorerMediaSettings,
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
                sortBy = SortField.from(favSort),
                sortDesc = favDesc,
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
        // Explorer's folder view is stored entirely under its own key (not just sort) so that
        // changing view type/columns/sort/etc. while browsing the Explorer never bleeds into the
        // dedicated Albums tab, and vice versa.
        val prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(ctx)
        prefs.edit().putString("explorer_albums_settings", serializeViewSettings(s)).apply()
    }

    private fun persistExplorerMedia(s: ViewSettings) {
        val ctx = getApplication<Application>().applicationContext
        // Same isolation as persistExplorerAlbums, for the Explorer's file listing vs the Media tab.
        val prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(ctx)
        prefs.edit().putString("explorer_media_settings", serializeViewSettings(s)).apply()
    }

    private fun persistFavorites(s: ViewSettings) {
        val ctx = getApplication<Application>().applicationContext
        ctx.config.viewTypeFolders = s.viewType.value
        ctx.config.dirColumnCnt = s.columnCount
        ctx.config.folderDisplayMode = s.displayMode.value
        ctx.config.displayFileNames = s.showFileNames
        ctx.config.fileRoundedCorners = s.roundedCorners
        ctx.config.thumbnailSpacing = s.spacing
        ctx.config.showFolderThumbnails = s.showFolderThumbnails
        // Save Favorites' sort independently so it never interferes with the Albums tab (both used
        // to share config.folderSortBy/folderSortDesc).
        val prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(ctx)
        prefs.edit().putString("favorites_sort", "${s.sortBy.value},${s.sortDesc}").apply()
    }

    private fun serializeViewSettings(s: ViewSettings): String = listOf(
        s.viewType.value, s.columnCount, s.displayMode.value, s.showFileNames,
        s.roundedCorners, s.sortBy.value, s.sortDesc, s.spacing, s.showFolderThumbnails, s.anchorBottom,
    ).joinToString(",")

    private fun deserializeViewSettings(raw: String?, fallback: ViewSettings): ViewSettings {
        if (raw == null) return fallback
        val p = raw.split(",")
        if (p.size < 10) return fallback
        return try {
            ViewSettings(
                viewType = ViewType.from(p[0].toInt()),
                columnCount = p[1].toInt(),
                displayMode = DisplayMode.from(p[2].toInt()),
                showFileNames = p[3].toBoolean(),
                roundedCorners = p[4].toBoolean(),
                sortBy = SortField.from(p[5].toInt()),
                sortDesc = p[6].toBoolean(),
                spacing = p[7].toInt(),
                showFolderThumbnails = p[8].toBoolean(),
                anchorBottom = p[9].toBoolean(),
            )
        } catch (_: Exception) {
            fallback
        }
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

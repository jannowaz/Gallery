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
import org.fossify.gallery.helpers.GroupBy
import org.fossify.gallery.helpers.GroupOrder

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

    // Path-scoped overrides for FolderMediaScreen (a specific opened folder) and Explorer's media
    // listing (the currently browsed directory) - the "Einstellung global übernehmen" toggle in
    // ViewSettingsSheet. Deliberately separate from TabViewSettings/StateFlow: unlike the tab-level
    // settings, these are looked up per path on demand by the one screen instance that's currently
    // showing that path, not broadcast to every observer - a plain SharedPreferences key keyed by
    // scope+path (mirroring the legacy Views-based Config.saveFolderGrouping/getFolderViewType
    // pattern, which the Compose UI never wired up to) is simpler than adding a path->settings map
    // to the reactive state for something only one screen ever reads at a time.
    private fun pathSettingsKey(scope: String, path: String) = "${scope}_path_settings_${path.lowercase(java.util.Locale.getDefault())}"

    private fun getCustomForPath(scope: String, path: String, fallback: ViewSettings): ViewSettings {
        val prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(getApplication<Application>().applicationContext)
        val raw = prefs.getString(pathSettingsKey(scope, path), null) ?: return fallback
        return deserializeViewSettings(raw, fallback)
    }

    private fun hasCustomForPath(scope: String, path: String): Boolean {
        val prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(getApplication<Application>().applicationContext)
        return prefs.contains(pathSettingsKey(scope, path))
    }

    private fun saveCustomForPath(scope: String, path: String, s: ViewSettings) {
        val prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(getApplication<Application>().applicationContext)
        prefs.edit().putString(pathSettingsKey(scope, path), serializeViewSettings(s)).apply()
    }

    private fun removeCustomForPath(scope: String, path: String) {
        val prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(getApplication<Application>().applicationContext)
        prefs.edit().remove(pathSettingsKey(scope, path)).apply()
    }

    fun getFolderMediaSettingsForPath(path: String): ViewSettings = getCustomForPath("folder_media", path, _settings.value.folderMedia)
    fun hasCustomFolderMediaSettings(path: String): Boolean = hasCustomForPath("folder_media", path)
    fun updateFolderMediaForPath(path: String, s: ViewSettings, applyGlobally: Boolean) {
        if (applyGlobally) {
            removeCustomForPath("folder_media", path)
            updateFolderMedia(s)
        } else {
            saveCustomForPath("folder_media", path, s)
        }
    }

    fun getExplorerMediaSettingsForPath(path: String): ViewSettings = getCustomForPath("explorer_media", path, _settings.value.explorerMedia)
    fun hasCustomExplorerMediaSettings(path: String): Boolean = hasCustomForPath("explorer_media", path)
    fun updateExplorerMediaForPath(path: String, s: ViewSettings, applyGlobally: Boolean) {
        if (applyGlobally) {
            removeCustomForPath("explorer_media", path)
            updateExplorerMedia(s)
        } else {
            saveCustomForPath("explorer_media", path, s)
        }
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
            // Explorer's file list had no section headers at all before grouping existed - default
            // to NONE (unlike ViewSettings()'s own MONTH default) so upgrading users don't suddenly
            // see new headers appear unprompted.
            groupBy = GroupBy.NONE,
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
                groupBy = GroupBy.from(c.mediaGroupBy),
            ).sanitizeGrouping(),
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
                groupBy = GroupBy.from(c.folderMediaGroupBy),
                groupOrder = GroupOrder.from(c.folderMediaGroupOrder),
                onlyTopLevelTags = c.folderMediaOnlyTopLevelTags,
            ).sanitizeGrouping(),
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
                groupBy = GroupBy.from(c.favoritesGroupBy),
            ).sanitizeGrouping(),
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
        ctx.config.mediaGroupBy = s.groupBy.value
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
        ctx.config.favoritesGroupBy = s.groupBy.value
        // Save Favorites' sort independently so it never interferes with the Albums tab (both used
        // to share config.folderSortBy/folderSortDesc).
        val prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(ctx)
        prefs.edit().putString("favorites_sort", "${s.sortBy.value},${s.sortDesc}").apply()
    }

    private fun serializeViewSettings(s: ViewSettings): String = listOf(
        s.viewType.value, s.columnCount, s.displayMode.value, s.showFileNames,
        s.roundedCorners, s.sortBy.value, s.sortDesc, s.spacing, s.showFolderThumbnails, s.anchorBottom,
        s.groupBy.value, s.groupOrder.value, s.onlyTopLevelTags,
    ).joinToString(",")

    private fun deserializeViewSettings(raw: String?, fallback: ViewSettings): ViewSettings {
        if (raw == null) return fallback
        val p = raw.split(",")
        // Only the original 10 fields are required - the 3 grouping fields were added later, so a
        // string saved before they existed (exactly 10 elements) must still parse instead of being
        // discarded wholesale; missing indices 10-12 just fall back to the caller's default.
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
                groupBy = p.getOrNull(10)?.toIntOrNull()?.let { GroupBy.from(it) } ?: fallback.groupBy,
                groupOrder = p.getOrNull(11)?.toIntOrNull()?.let { GroupOrder.from(it) } ?: fallback.groupOrder,
                onlyTopLevelTags = p.getOrNull(12)?.toBooleanStrictOrNull() ?: fallback.onlyTopLevelTags,
            ).sanitizeGrouping()
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
        ctx.config.folderMediaGroupBy = s.groupBy.value
        ctx.config.folderMediaGroupOrder = s.groupOrder.value
        ctx.config.folderMediaOnlyTopLevelTags = s.onlyTopLevelTags
    }
}

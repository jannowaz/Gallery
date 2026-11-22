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
import org.fossify.gallery.extensions.defaultSharedPrefs
import org.fossify.gallery.helpers.GroupBy
import org.fossify.gallery.helpers.GroupOrder

data class TabViewSettings(
    val media: ViewSettings = ViewSettings(),
    // Collections are rendered through the Media screen, but their media set is bounded and can
    // therefore support full-list groupings (including tags) without changing the unbounded Media
    // tab's paging contract. Keep these settings separate so choosing "Tags" inside a collection
    // never leaves the global Media tab with an unsupported GroupBy.TAG value.
    val collectionMedia: ViewSettings = ViewSettings(),
    val albums: ViewSettings = ViewSettings(columnCount = 3, displayMode = DisplayMode.NORMAL),
    val explorerAlbums: ViewSettings = ViewSettings(columnCount = 3, displayMode = DisplayMode.NORMAL),
    val explorerMedia: ViewSettings = ViewSettings(),
    val folderMedia: ViewSettings = ViewSettings(),
    val favorites: ViewSettings = ViewSettings(columnCount = 3, displayMode = DisplayMode.NORMAL),
    val collections: ViewSettings = ViewSettings(columnCount = 3, displayMode = DisplayMode.NORMAL, sortBy = SortField.COUNT),
    val tags: ViewSettings = ViewSettings(columnCount = 3, displayMode = DisplayMode.NORMAL, sortBy = SortField.COUNT),
)

class ViewSettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val _settings = MutableStateFlow(TabViewSettings())
    val settings: StateFlow<TabViewSettings> = _settings.asStateFlow()

    private val _settingsMode = MutableStateFlow(SettingsMode.ALBUMS)
    val settingsMode: StateFlow<SettingsMode> = _settingsMode.asStateFlow()

    private fun surfaceSettingsKey(surface: String) = "compose_view_settings_${surface}_v2"

    private fun loadSurfaceSettings(surface: String, fallback: ViewSettings): ViewSettings {
        val ctx = getApplication<Application>().applicationContext
        val prefs = ctx.defaultSharedPrefs()
        return deserializeViewSettings(prefs.getString(surfaceSettingsKey(surface), null), fallback)
    }

    private fun saveSurfaceSettings(surface: String, settings: ViewSettings) {
        val ctx = getApplication<Application>().applicationContext
        val prefs = ctx.defaultSharedPrefs()
        prefs.edit().putString(surfaceSettingsKey(surface), serializeViewSettings(settings)).apply()
    }

    // Off the main thread - loadFromConfig() reads a couple dozen SharedPreferences values, which
    // on a cold app start is the first access to that prefs file and was a confirmed
    // StrictMode DiskReadViolation (~440ms, causing dropped frames right after first launch) when
    // run synchronously here as part of ViewModel construction during the first composition.
    // Tabs render with the ViewSettings() defaults for the brief moment until this completes.
    init { viewModelScope.launch(Dispatchers.IO) { loadFromConfig() } }

    fun setSettingsMode(mode: SettingsMode) { _settingsMode.value = mode }

    fun updateMedia(s: ViewSettings) {
        val safe = s.sanitizeFor(ViewSettingsSurface.MEDIA)
        _settings.value = _settings.value.copy(media = safe)
        persistMedia(safe)
    }

    fun updateCollectionMedia(s: ViewSettings) {
        val safe = s.sanitizeFor(ViewSettingsSurface.COLLECTION_MEDIA)
        _settings.value = _settings.value.copy(collectionMedia = safe)
        saveSurfaceSettings("collection_media", safe)
    }

    fun updateAlbums(s: ViewSettings) {
        val albums = s.sanitizeFor(ViewSettingsSurface.ALBUMS)
        // Folder appearance can remain tailored to the tab, but the order is a library-wide
        // choice: Albums and Explorer must agree on the selected folder sort.
        val explorerAlbums = _settings.value.explorerAlbums.copy(
            sortBy = albums.sortBy,
            sortDesc = albums.sortDesc,
        ).sanitizeFor(ViewSettingsSurface.EXPLORER_ALBUMS)
        _settings.value = _settings.value.copy(albums = albums, explorerAlbums = explorerAlbums)
        persistAlbums(albums)
        persistExplorerAlbums(explorerAlbums)
    }

    fun updateExplorerAlbums(s: ViewSettings) {
        val explorerAlbums = s.sanitizeFor(ViewSettingsSurface.EXPLORER_ALBUMS)
        val albums = _settings.value.albums.copy(
            sortBy = explorerAlbums.sortBy,
            sortDesc = explorerAlbums.sortDesc,
        ).sanitizeFor(ViewSettingsSurface.ALBUMS)
        _settings.value = _settings.value.copy(albums = albums, explorerAlbums = explorerAlbums)
        persistAlbums(albums)
        persistExplorerAlbums(explorerAlbums)
    }

    fun updateExplorerMedia(s: ViewSettings) {
        val safe = s.sanitizeFor(ViewSettingsSurface.EXPLORER_MEDIA)
        _settings.value = _settings.value.copy(explorerMedia = safe)
        persistExplorerMedia(safe)
    }

    fun updateFavorites(s: ViewSettings) {
        val safe = s.sanitizeFor(ViewSettingsSurface.FAVORITES)
        _settings.value = _settings.value.copy(favorites = safe)
        persistFavorites(safe)
    }

    fun updateCollections(s: ViewSettings) {
        val safe = s.sanitizeFor(ViewSettingsSurface.COLLECTIONS)
        _settings.value = _settings.value.copy(collections = safe)
        saveSurfaceSettings("collections", safe)
    }

    fun updateTags(s: ViewSettings) {
        val safe = s.sanitizeFor(ViewSettingsSurface.TAGS)
        _settings.value = _settings.value.copy(tags = safe)
        saveSurfaceSettings("tags", safe)
    }

    fun updateFolderMedia(s: ViewSettings) {
        val safe = s.sanitizeFor(ViewSettingsSurface.FOLDER_MEDIA)
        _settings.value = _settings.value.copy(folderMedia = safe)
        persistFolderMedia(safe)
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

    private fun getCustomForPath(scope: String, path: String, fallback: ViewSettings, surface: ViewSettingsSurface): ViewSettings {
        val prefs = getApplication<Application>().applicationContext.defaultSharedPrefs()
        val raw = prefs.getString(pathSettingsKey(scope, path), null) ?: return fallback.sanitizeFor(surface)
        return deserializeViewSettings(raw, fallback).sanitizeFor(surface)
    }

    private fun hasCustomForPath(scope: String, path: String): Boolean {
        val prefs = getApplication<Application>().applicationContext.defaultSharedPrefs()
        return prefs.contains(pathSettingsKey(scope, path))
    }

    private fun saveCustomForPath(scope: String, path: String, s: ViewSettings, surface: ViewSettingsSurface) {
        val prefs = getApplication<Application>().applicationContext.defaultSharedPrefs()
        prefs.edit().putString(pathSettingsKey(scope, path), serializeViewSettings(s.sanitizeFor(surface))).apply()
    }

    private fun removeCustomForPath(scope: String, path: String) {
        val prefs = getApplication<Application>().applicationContext.defaultSharedPrefs()
        prefs.edit().remove(pathSettingsKey(scope, path)).apply()
    }

    fun getFolderMediaSettingsForPath(path: String): ViewSettings = getCustomForPath("folder_media", path, _settings.value.folderMedia, ViewSettingsSurface.FOLDER_MEDIA)
    fun hasCustomFolderMediaSettings(path: String): Boolean = hasCustomForPath("folder_media", path)
    fun updateFolderMediaForPath(path: String, s: ViewSettings, applyGlobally: Boolean) {
        if (applyGlobally) {
            removeCustomForPath("folder_media", path)
            updateFolderMedia(s)
        } else {
            saveCustomForPath("folder_media", path, s, ViewSettingsSurface.FOLDER_MEDIA)
        }
    }

    fun getExplorerMediaSettingsForPath(path: String): ViewSettings = getCustomForPath("explorer_media", path, _settings.value.explorerMedia, ViewSettingsSurface.EXPLORER_MEDIA)
    fun hasCustomExplorerMediaSettings(path: String): Boolean = hasCustomForPath("explorer_media", path)
    fun updateExplorerMediaForPath(path: String, s: ViewSettings, applyGlobally: Boolean) {
        if (applyGlobally) {
            removeCustomForPath("explorer_media", path)
            updateExplorerMedia(s)
        } else {
            saveCustomForPath("explorer_media", path, s, ViewSettingsSurface.EXPLORER_MEDIA)
        }
    }

    private fun loadFromConfig() {
        val ctx = getApplication<Application>().applicationContext
        val c = ctx.config
        val prefs = ctx.defaultSharedPrefs()
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
            .sanitizeFor(ViewSettingsSurface.EXPLORER_ALBUMS)
        val explorerMediaSettings = deserializeViewSettings(prefs.getString("explorer_media_settings", null), explorerMediaDefault)
            .sanitizeFor(ViewSettingsSurface.EXPLORER_MEDIA)

        // The legacy Config values are migration fallbacks only. Compose surfaces now persist the
        // complete setting bundle under their own key, so changing density, spacing or card styling
        // in one tab cannot silently restyle another tab that happens to use the same old Config
        // property.
        val mediaDefault = ViewSettings(
            viewType = ViewType.from(c.viewTypeFiles),
            columnCount = c.mediaColumnCnt.coerceIn(2, 6),
            showFileNames = c.mediaShowFileNames,
            roundedCorners = c.fileRoundedCorners,
            sortBy = SortField.from(c.mediaSortBy),
            sortDesc = c.mediaSortDesc,
            spacing = c.thumbnailSpacing,
            groupBy = GroupBy.from(c.mediaGroupBy),
        )
        val albumsDefault = ViewSettings(
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
        val folderMediaDefault = ViewSettings(
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
        )
        val favoritesDefault = ViewSettings(
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
        )
        val collectionsDefault = ViewSettings(
            viewType = ViewType.from(c.collectionsViewType),
            columnCount = c.collectionsColumnCnt.coerceIn(2, 6),
            roundedCorners = c.fileRoundedCorners,
            spacing = c.thumbnailSpacing,
            showFolderThumbnails = c.showFolderThumbnails,
            sortBy = SortField.from(c.collectionsSortBy),
            sortDesc = c.collectionsSortDesc,
        )
        val tagsDefault = ViewSettings(
            viewType = ViewType.from(c.tagsViewType),
            columnCount = c.tagsColumnCnt.coerceIn(2, 6),
            roundedCorners = c.fileRoundedCorners,
            spacing = c.thumbnailSpacing,
            showFolderThumbnails = c.showFolderThumbnails,
            sortBy = SortField.from(c.tagsSortBy),
            sortDesc = c.tagsSortDesc,
        )
        val albumsSettings = loadSurfaceSettings("albums", albumsDefault).sanitizeFor(ViewSettingsSurface.ALBUMS)
        // The folder order is global. On upgrade, make the established Albums order the source of
        // truth and retain Explorer's independent layout choices (grid/list, density, spacing).
        val synchronizedExplorerAlbums = explorerAlbumsSettings.copy(
            sortBy = albumsSettings.sortBy,
            sortDesc = albumsSettings.sortDesc,
        ).sanitizeFor(ViewSettingsSurface.EXPLORER_ALBUMS)
        if (synchronizedExplorerAlbums.sortBy != explorerAlbumsSettings.sortBy || synchronizedExplorerAlbums.sortDesc != explorerAlbumsSettings.sortDesc) {
            persistExplorerAlbums(synchronizedExplorerAlbums)
        }
        _settings.value = TabViewSettings(
            media = loadSurfaceSettings("media", mediaDefault).sanitizeFor(ViewSettingsSurface.MEDIA),
            collectionMedia = loadSurfaceSettings("collection_media", mediaDefault).sanitizeFor(ViewSettingsSurface.COLLECTION_MEDIA),
            albums = albumsSettings,
            explorerAlbums = synchronizedExplorerAlbums,
            explorerMedia = explorerMediaSettings,
            folderMedia = loadSurfaceSettings("folder_media", folderMediaDefault).sanitizeFor(ViewSettingsSurface.FOLDER_MEDIA),
            favorites = loadSurfaceSettings("favorites", favoritesDefault).sanitizeFor(ViewSettingsSurface.FAVORITES),
            collections = loadSurfaceSettings("collections", collectionsDefault).sanitizeFor(ViewSettingsSurface.COLLECTIONS),
            tags = loadSurfaceSettings("tags", tagsDefault).sanitizeFor(ViewSettingsSurface.TAGS),
        )
    }

    private fun persistMedia(s: ViewSettings) {
        saveSurfaceSettings("media", s)
    }

    private fun persistAlbums(s: ViewSettings) {
        saveSurfaceSettings("albums", s)
    }

    private fun persistExplorerAlbums(s: ViewSettings) {
        val ctx = getApplication<Application>().applicationContext
        // Explorer's folder view is stored entirely under its own key (not just sort) so that
        // changing view type/columns/sort/etc. while browsing the Explorer never bleeds into the
        // dedicated Albums tab, and vice versa.
        val prefs = ctx.defaultSharedPrefs()
        prefs.edit().putString("explorer_albums_settings", serializeViewSettings(s)).apply()
    }

    private fun persistExplorerMedia(s: ViewSettings) {
        val ctx = getApplication<Application>().applicationContext
        // Same isolation as persistExplorerAlbums, for the Explorer's file listing vs the Media tab.
        val prefs = ctx.defaultSharedPrefs()
        prefs.edit().putString("explorer_media_settings", serializeViewSettings(s)).apply()
    }

    private fun persistFavorites(s: ViewSettings) {
        saveSurfaceSettings("favorites", s)
    }

    private fun serializeViewSettings(s: ViewSettings): String = listOf(
        s.viewType.value, s.columnCount, s.displayMode.value, s.showFileNames,
        s.roundedCorners, s.sortBy.value, s.sortDesc, s.spacing, s.showFolderThumbnails, s.anchorBottom,
        s.groupBy.value, s.groupOrder.value, s.onlyTopLevelTags, s.listDensity.value,
    ).joinToString(",")

    private fun deserializeViewSettings(raw: String?, fallback: ViewSettings): ViewSettings {
        if (raw == null) return fallback
        val p = raw.split(",")
        // Only the original 10 fields are required - the grouping and density fields were added later, so a
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
                listDensity = p.getOrNull(13)?.toIntOrNull()?.let { ListDensity.from(it) } ?: fallback.listDensity,
            )
        } catch (_: Exception) {
            fallback
        }
    }

    private fun persistFolderMedia(s: ViewSettings) {
        saveSurfaceSettings("folder_media", s)
    }
}

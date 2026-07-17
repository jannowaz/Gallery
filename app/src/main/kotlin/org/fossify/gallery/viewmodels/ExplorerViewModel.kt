package org.fossify.gallery.viewmodels

import android.app.Application
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
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.directoryDB
import org.fossify.gallery.extensions.mediaDB
import org.fossify.gallery.helpers.RefreshBus
import org.fossify.gallery.helpers.expandTagsWithDescendants
import org.fossify.gallery.models.Directory
import org.fossify.gallery.models.Medium
import java.io.File

data class ExplorerUiState(
    val selectedTab: Int = 1,
    val explorerPath: String = "",
    val activeRatingFilter: Int = 0,
    /** Tag *names* (hierarchy-expanded), resolved to matching files in SQL - see [org.fossify.gallery.viewmodels.MediaFilter]. */
    val activeTagFilter: Set<String>? = null,
    val activeTagName: String? = null,
    /** Raw (unexpanded) tag names backing the multi-select filter UI (search panel + FilterSheetContent) -
     * kept separate from [activeTagFilter] (the hierarchy-expanded set actually used for SQL matching)
     * so toggling one tag chip doesn't need to know which other tags' descendants are already included. */
    val activeTagFilterRaw: Set<String> = emptySet(),
    val activePathFilter: Set<String>? = null,
    val activeExcludePathFilter: Set<String>? = null,
    val activeMinSizeFilter: Long = 0L,
    val activeDateRangeFilter: Int = 0,
    val activeTypeFilter: Int = 0, // 0: All, 1: Image, 2: Video - matches Medium.type
    val activePathName: String? = null,
    val activeCollectionName: String? = null,
    /** Separate from [activePathName] (which is search's query text) - the label for a folder-scoped
     * pathFilter set via "In Medien öffnen" (Explorer's recursive open-folder-in-Media action), same
     * "distinct label per filter source" pattern as [activeCollectionName]. */
    val activeFolderFilterName: String? = null,
    val mediaRefreshTrigger: Int = 0,
    val preFilterTab: Int = -1,
    val dbInitialized: Boolean = false,
    val dbInitError: String? = null,
    val gridScrollIndex: Int = 0,
    val gridScrollOffset: Int = 0,
    val lastViewedPath: String = "",
)

val ExplorerUiState.hasActiveFilter: Boolean
    get() = activeRatingFilter > 0 || activeTagFilter != null || activePathFilter != null ||
        activeMinSizeFilter > 0 || activeDateRangeFilter > 0 || activeTypeFilter > 0

/** Pure state transition backing [ExplorerViewModel.toggleTagFilter] - kept free of any Android/
 * ViewModel dependency (besides the already-pure [expandTagsWithDescendants]) so it's directly
 * unit-testable without instantiating the ViewModel. */
fun ExplorerUiState.withTagToggled(tag: String, tagHierarchy: Map<String, String>): ExplorerUiState {
    val next = if (tag in activeTagFilterRaw) activeTagFilterRaw - tag else activeTagFilterRaw + tag
    return if (next.isEmpty()) {
        copy(activeTagFilter = null, activeTagFilterRaw = emptySet(), activeTagName = null)
    } else {
        copy(activeTagFilter = expandTagsWithDescendants(next, tagHierarchy), activeTagFilterRaw = next, activeTagName = next.joinToString(", "))
    }
}

/** Pure state transition backing [ExplorerViewModel.clearFilters] - see [withTagToggled] on why this
 * is kept separate from the ViewModel. */
fun ExplorerUiState.withFiltersCleared(): ExplorerUiState = copy(
    activeRatingFilter = 0, activeTagFilter = null, activeTagName = null, activeTagFilterRaw = emptySet(),
    activePathFilter = null, activeExcludePathFilter = null, activeMinSizeFilter = 0L, activeDateRangeFilter = 0,
    activeTypeFilter = 0, activePathName = null, activeCollectionName = null, activeFolderFilterName = null, preFilterTab = -1,
)

class ExplorerViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(ExplorerUiState())
    val state: StateFlow<ExplorerUiState> = _state.asStateFlow()

    init {
        val ctx = getApplication<Application>()
        val conf = ctx.config
        _state.update { it.copy(explorerPath = android.os.Environment.getExternalStorageDirectory().absolutePath, lastViewedPath = conf.lastViewedPath) }
        viewModelScope.launch {
            RefreshBus.events.collect { triggerMediaRefresh() }
        }
    }

    fun setSelectedTab(tab: Int) { _state.update { it.copy(selectedTab = tab) } }
    fun setExplorerPath(path: String) {
        _state.update { it.copy(explorerPath = path) }
        // Persisted (not just in-memory state) so it survives a process restart too - used to
        // default the destination picker when turning a folder into a Mover pair's source.
        getApplication<Application>().config.lastExplorerPath = path
    }
    fun setRatingFilter(rating: Int) { _state.update { it.copy(activeRatingFilter = rating) } }
    fun setTagFilter(tagNames: Set<String>?, tagName: String?) { _state.update { it.copy(activeTagFilter = tagNames, activeTagName = tagName) } }
    fun setPathFilter(paths: Set<String>?, name: String? = null) { _state.update { it.copy(activePathFilter = paths, activePathName = name) } }
    fun setExcludePathFilter(paths: Set<String>?) { _state.update { it.copy(activeExcludePathFilter = paths) } }
    fun setMinSizeFilter(bytes: Long) { _state.update { it.copy(activeMinSizeFilter = bytes) } }
    fun setDateRangeFilter(range: Int) { _state.update { it.copy(activeDateRangeFilter = range) } }
    fun setTypeFilter(type: Int) { _state.update { it.copy(activeTypeFilter = type) } }
    fun setCollectionName(name: String?) { _state.update { it.copy(activeCollectionName = name) } }
    fun setFolderFilterName(name: String?) { _state.update { it.copy(activeFolderFilterName = name) } }
    fun setPreFilterTab(tab: Int) { _state.update { it.copy(preFilterTab = tab) } }

    /** Toggles [tag] in the raw multi-select tag set shared by the persistent Filter sheet and the
     * search panel's own tag chips, re-deriving the hierarchy-expanded [ExplorerUiState.activeTagFilter]
     * (used for SQL matching) and the joined display label from the updated raw set. */
    fun toggleTagFilter(tag: String) {
        _state.update { it.withTagToggled(tag, getApplication<Application>().config.tagHierarchy) }
    }

    fun clearFilters() {
        _state.update { it.withFiltersCleared() }
    }

    fun triggerMediaRefresh() {
        _state.update { it.copy(mediaRefreshTrigger = it.mediaRefreshTrigger + 1) }
    }

    fun saveScrollPosition(index: Int, offset: Int) {
        _state.update { it.copy(gridScrollIndex = index, gridScrollOffset = offset) }
    }

    fun setLastViewedPath(path: String) {
        _state.update { it.copy(lastViewedPath = path) }
    }

    fun clearLastViewedPath() {
        _state.update { it.copy(lastViewedPath = "") }
    }

    // onComplete only fires on this ViewModel's actual first-ever run (before dbInitialized flips to
    // true) - MainScreen calls this again on every Viewer round trip since it's just a LaunchedEffect(Unit)
    // in a composable that gets disposed and recreated, and re-invoking onComplete every time would
    // trigger a real MediaStore sync + bounded DB query (MediaViewModel.silentRefresh) on every single
    // round trip. Real subsequent changes are already covered by RefreshBus (see init{} above).
    fun initializeDatabase(onComplete: (() -> Unit)? = null) {
        val s = _state.value
        if (s.dbInitialized) return
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            withContext(Dispatchers.IO) {
                try {
                    // Count including recycle-bin rows: getNewestMedia filters deleted_ts = 0, so
                    // with every medium soft-deleted this looked like a fresh install and the
                    // bootstrap below re-imported everything - and since it REPLACEd rows with
                    // deletedTS=0/rating=0 defaults, one app restart silently emptied the whole
                    // recycle bin back into the library and wiped DB ratings (found 2026-07-17
                    // while reproducing the bin-emptying report).
                    val isFreshDatabase = ctx.mediaDB.getTotalCountIncludingDeleted() == 0
                    if (isFreshDatabase) {
                        val mediums = mutableListOf<Medium>()
                        val uri = MediaStore.Files.getContentUri("external")
                        val projection = arrayOf(
                            MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.DATA,
                            MediaStore.Files.FileColumns.DATE_MODIFIED, MediaStore.Files.FileColumns.DATE_TAKEN,
                            MediaStore.Files.FileColumns.DATE_ADDED,
                            MediaStore.Files.FileColumns.SIZE, MediaStore.Files.FileColumns.MIME_TYPE,
                            MediaStore.Files.FileColumns.MEDIA_TYPE, MediaStore.Files.FileColumns.DURATION,
                        )
                        val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?"
                        val selectionArgs = arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(), MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString())
                        ctx.contentResolver.query(uri, projection, selection, selectionArgs, "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC")
                            ?.use { cursor ->
                                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
                                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
                                val takenCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_TAKEN)
                                val addedCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
                                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                                val typeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
                                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DURATION)
                                while (cursor.moveToNext()) {
                                    val path = cursor.getString(dataCol) ?: continue
                                    val modified = cursor.getLong(dateCol) * 1000L
                                    val taken = if (!cursor.isNull(takenCol)) cursor.getLong(takenCol) else modified
                                    val added = (if (!cursor.isNull(addedCol)) cursor.getLong(addedCol) * 1000L else 0L).takeIf { it > 0 } ?: maxOf(modified, taken)
                                    val size = cursor.getLong(sizeCol)
                                    val mediaType = cursor.getInt(typeCol)
                                    val type = if (mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO) 2 else 1
                                    val duration = if (!cursor.isNull(durCol)) (cursor.getInt(durCol) / 1000) else 0
                                    mediums.add(Medium(
                                        id = null, name = File(path).name, path = path, parentPath = File(path).parent ?: "",
                                        modified = modified, taken = taken, size = size, type = type,
                                        videoDuration = duration, isFavorite = false, deletedTS = 0L, mediaStoreId = 0, rating = 0,
                                        dateAdded = added,
                                    ))
                                }
                            }
                        if (mediums.isNotEmpty()) {
                            // IGNORE, not REPLACE: even if the fresh-database check above ever
                            // misfires, bootstrap defaults must never clobber existing rows'
                            // deleted_ts/rating.
                            ctx.mediaDB.insertAllKeepingExisting(mediums)
                            val dirs = mediums.map { it.parentPath }.distinct()
                            dirs.forEach { dirPath ->
                                val dirMedia = mediums.filter { it.parentPath == dirPath }
                                val dirName = File(dirPath).name
                                val hasImage = dirMedia.any { it.type == 1 }
                                val hasVideo = dirMedia.any { it.type == 2 }
                                val types = if (hasImage && hasVideo) 3 else if (hasVideo) 2 else 1
                                ctx.directoryDB.insertAll(listOf(Directory(
                                    id = null, path = dirPath, tmb = dirMedia.maxByOrNull { if (it.dateSortKey > 0) it.dateSortKey else if (it.dateAdded > 0) it.dateAdded else if (it.taken > 0) it.taken else it.modified }?.path ?: "",
                                    name = dirName, mediaCnt = dirMedia.size, modified = dirMedia.maxOf { it.modified },
                                    taken = dirMedia.maxOf { it.taken }, size = dirMedia.size.toLong(),
                                    location = org.fossify.gallery.helpers.LOCATION_INTERNAL, types = types, sortValue = "",
                                )))
                            }
                        }
                    }
                    _state.update { it.copy(dbInitialized = true) }
                } catch (e: Exception) {
                    _state.update { it.copy(dbInitError = e.message) }
                }
            }
            onComplete?.invoke()
        }
    }
}

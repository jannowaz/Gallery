package org.fossify.gallery.viewmodels

import android.app.Application
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
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
import org.fossify.gallery.helpers.LibraryBootstrapAccumulator
import org.fossify.gallery.helpers.expandTagsWithDescendants
import org.fossify.gallery.models.Directory
import org.fossify.gallery.models.Medium
import org.fossify.gallery.models.MediaReviewFilter
import java.io.File

data class ExplorerUiState(
    val selectedTab: Int = 1,
    val tabHistory: List<Int> = emptyList(),
    val explorerPath: String = "",
    val activeRatingFilter: Int = 0,
    /** Tag *names* (hierarchy-expanded), resolved to matching files in SQL - see [org.fossify.gallery.viewmodels.MediaFilter]. */
    val activeTagFilter: Set<String>? = null,
    val activeTagName: String? = null,
    /** Raw (unexpanded) tag names backing the multi-select filter UI (search panel + FilterSheetContent) -
     * kept separate from [activeTagFilter] (the hierarchy-expanded set actually used for SQL matching)
     * so toggling one tag chip doesn't need to know which other tags' descendants are already included. */
    val activeTagFilterRaw: Set<String> = emptySet(),
    /** Whether multiple selected tags must all match instead of matching any selected tag. */
    val activeTagMatchAll: Boolean = false,
    val activePathFilter: Set<String>? = null,
    val activeExcludePathFilter: Set<String>? = null,
    val activeMinSizeFilter: Long = 0L,
    val activeDateRangeFilter: Int = 0,
    /** MediaStore-backed watermark for the explicit new-media inbox. */
    val activeAddedSince: Long = 0L,
    val activeTypeFilter: Int = 0, // 0: All, 1: Image, 2: Video - matches Medium.type
    /** Independent review inbox; never represented through collection/stack membership. */
    val activeReviewFilter: MediaReviewFilter = MediaReviewFilter.NONE,
    /** Search is a SQL predicate, never a materialized set of every matching path. */
    val activeTextQuery: String = "",
    /** Explicit media members of the active manual collection/stack. */
    val activeCollectionMediaPaths: Set<String> = emptySet(),
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
    val newMediaCount: Int = 0,
)

val ExplorerUiState.hasActiveFilter: Boolean
    get() = activeRatingFilter > 0 || activeTagFilter != null || activePathFilter != null ||
        activeExcludePathFilter != null || activeMinSizeFilter > 0 || activeDateRangeFilter > 0 ||
        activeAddedSince > 0L || activeTypeFilter > 0 || activeCollectionMediaPaths.isNotEmpty() ||
        activeReviewFilter != MediaReviewFilter.NONE || activeTextQuery.isNotBlank() || activePathName != null || activeCollectionName != null || activeFolderFilterName != null

/** The tab to restore when a filter-originating navigation is undone. Manually applied filters
 * stay on the current tab; filters opened from another tab return to that recorded origin. */
fun ExplorerUiState.filterReturnTab(): Int = preFilterTab.takeIf { it >= 0 } ?: selectedTab

/** Pure state transition backing [ExplorerViewModel.toggleTagFilter] - kept free of any Android/
 * ViewModel dependency (besides the already-pure [expandTagsWithDescendants]) so it's directly
 * unit-testable without instantiating the ViewModel. */
fun ExplorerUiState.withTagToggled(tag: String, tagHierarchy: Map<String, String>): ExplorerUiState {
    val next = if (tag in activeTagFilterRaw) activeTagFilterRaw - tag else activeTagFilterRaw + tag
    return if (next.isEmpty()) {
        copy(activeTagFilter = null, activeTagFilterRaw = emptySet(), activeTagName = null, activeTagMatchAll = false)
    } else {
        copy(
            activeTagFilter = expandTagsWithDescendants(next, tagHierarchy),
            activeTagFilterRaw = next,
            // A plain multi-select must keep its tags independently visible/removable in the
            // shared filter summary. A non-null display name is reserved for one semantic
            // hierarchy/tag-browser filter, where descendants are intentionally grouped.
            activeTagName = null,
            // "all" only has meaning for at least two tags; preserve it otherwise.
            activeTagMatchAll = activeTagMatchAll && next.size >= 2,
        )
    }
}

/** Pure state transition backing [ExplorerViewModel.clearFilters] - see [withTagToggled] on why this
 * is kept separate from the ViewModel. */
fun ExplorerUiState.withFiltersCleared(): ExplorerUiState = copy(
    activeRatingFilter = 0, activeTagFilter = null, activeTagName = null, activeTagFilterRaw = emptySet(), activeTagMatchAll = false,
    activePathFilter = null, activeExcludePathFilter = null, activeMinSizeFilter = 0L, activeDateRangeFilter = 0, activeAddedSince = 0L,
    activeTypeFilter = 0, activeReviewFilter = MediaReviewFilter.NONE, activeTextQuery = "", activePathName = null, activeCollectionName = null, activeCollectionMediaPaths = emptySet(), activeFolderFilterName = null, preFilterTab = -1,
)

class ExplorerViewModel(application: Application) : AndroidViewModel(application) {

    private companion object {
        /** Limits first-import heap use independently of the library's total size. */
        const val BOOTSTRAP_INSERT_BATCH_SIZE = 750
    }

    private val _state = MutableStateFlow(ExplorerUiState())
    val state: StateFlow<ExplorerUiState> = _state.asStateFlow()
    private val repository = org.fossify.gallery.helpers.MediaRepository(application.applicationContext)

    init {
        val ctx = getApplication<Application>()
        val conf = ctx.config
        val storageRoot = android.os.Environment.getExternalStorageDirectory().absolutePath
        // Restore the last Explorer location across process restarts as well as tab switches. The
        // Explorer reconstructs the parent chain from this path, so Back still moves one level up.
        val lastExplorerPath = conf.lastExplorerPath.ifBlank { storageRoot }
        _state.update {
            it.copy(
                selectedTab = conf.lastSelectedTab.coerceIn(0, 5),
                explorerPath = lastExplorerPath,
                lastViewedPath = conf.lastViewedPath,
            )
        }
        viewModelScope.launch {
            // Only structural changes need to surface the "library changed" state to MediaScreen.
            // Favorite/rating/tag updates are handled by Room's invalidation and should not show a
            // refresh banner or make the user re-confirm a library reload.
            RefreshBus.pathEvents.collect {
                triggerMediaRefresh()
                refreshNewMediaCount()
            }
        }
    }

    fun setSelectedTab(tab: Int, addToHistory: Boolean = true) {
        _state.update { current ->
            if (current.selectedTab == tab) return@update current
            getApplication<Application>().config.lastSelectedTab = tab
            val history = if (addToHistory) {
                (current.tabHistory + current.selectedTab).takeLast(12)
            } else {
                current.tabHistory
            }
            current.copy(selectedTab = tab, tabHistory = history)
        }
    }

    fun navigateBackToPreviousTab(): Boolean {
        var changed = false
        _state.update { current ->
            var history = current.tabHistory
            while (history.lastOrNull() == current.selectedTab) history = history.dropLast(1)
            val previous = history.lastOrNull() ?: return@update current.copy(tabHistory = history)
            changed = true
            current.copy(selectedTab = previous, tabHistory = history.dropLast(1))
        }
        return changed
    }
    fun setExplorerPath(path: String) {
        _state.update { it.copy(explorerPath = path) }
        // Persisted (not just in-memory state) so it survives a process restart too - used to
        // default the destination picker when turning a folder into a Mover pair's source.
        getApplication<Application>().config.lastExplorerPath = path
    }
    fun setRatingFilter(rating: Int) { _state.update { it.copy(activeRatingFilter = rating) } }
    fun setTagFilter(tagNames: Set<String>?, tagName: String?) {
        // Keep the raw selection in lockstep with the expanded SQL set. Without this, applying a
        // tag from the Tag Browser (or opening a saved collection) showed the right media but the
        // Filter sheet had no selected chip, and clearing the filter could resurrect an old raw
        // selection on the next search/filter interaction.
        val raw = if (tagNames == null) emptySet() else {
            tagName?.split(',')?.map(String::trim)?.filter(String::isNotBlank)?.toSet()
                ?: tagNames
        }
        _state.update { it.copy(activeTagFilter = tagNames, activeTagFilterRaw = raw, activeTagName = tagName, activeTagMatchAll = false) }
    }
    /** Keeps independently selected tag chips independently removable in the media summary. */
    fun removeTagFromFilter(tag: String) {
        val hierarchy = getApplication<Application>().config.tagHierarchy
        _state.update { current ->
            val remaining = current.activeTagFilterRaw
                .filterNot { it.equals(tag, ignoreCase = true) }
                .toSet()
            if (remaining.isEmpty()) {
                current.copy(activeTagFilter = null, activeTagFilterRaw = emptySet(), activeTagName = null, activeTagMatchAll = false)
            } else {
                current.copy(
                    activeTagFilter = expandTagsWithDescendants(remaining, hierarchy),
                    activeTagFilterRaw = remaining,
                    activeTagName = null,
                    activeTagMatchAll = current.activeTagMatchAll && remaining.size >= 2,
                )
            }
        }
    }
    fun setTagMatchAll(matchAll: Boolean) { _state.update { it.copy(activeTagMatchAll = matchAll) } }
    fun setPathFilter(paths: Set<String>?, name: String? = null) { _state.update { it.copy(activePathFilter = paths, activePathName = name) } }
    fun setTextQueryFilter(query: String) {
        val normalized = query.trim()
        _state.update { it.copy(activeTextQuery = normalized, activePathFilter = null, activePathName = normalized.ifBlank { null }) }
    }
    fun setExcludePathFilter(paths: Set<String>?) { _state.update { it.copy(activeExcludePathFilter = paths) } }
    fun setMinSizeFilter(bytes: Long) { _state.update { it.copy(activeMinSizeFilter = bytes) } }
    fun setDateRangeFilter(range: Int) { _state.update { it.copy(activeDateRangeFilter = range) } }
    fun setNewMediaFilter(since: Long) {
        _state.update {
            if (since <= 0L) it.copy(activeAddedSince = 0L)
            else it.withFiltersCleared().copy(activeAddedSince = since, preFilterTab = it.preFilterTab)
        }
    }

    /** Opens the review inbox as its own view. It intentionally clears stack/collection and other
     * filters so "not reviewed" never looks like a combined collection query. */
    fun setUndecidedReviewFilter(enabled: Boolean) {
        _state.update {
            if (enabled) it.withFiltersCleared().copy(activeReviewFilter = MediaReviewFilter.UNDECIDED, preFilterTab = it.preFilterTab)
            else it.copy(activeReviewFilter = MediaReviewFilter.NONE)
        }
    }

    /** Marks the current inbox contents as seen without touching the sync watermark. */
    fun markNewMediaSeen() {
        val now = System.currentTimeMillis()
        getApplication<Application>().config.newMediaSeenAt = now
        _state.update { it.copy(newMediaCount = 0) }
    }

    fun refreshNewMediaCount() {
        viewModelScope.launch(Dispatchers.IO) {
            val ctx = getApplication<Application>()
            val since = ctx.config.newMediaSeenAt
            val count = repository.getNewMediaCountSince(since)
            _state.update {
                if (ctx.config.newMediaSeenAt == since) it.copy(newMediaCount = count) else it
            }
        }
    }
    fun setTypeFilter(type: Int) { _state.update { it.copy(activeTypeFilter = type) } }
    fun setCollectionName(name: String?) { _state.update { it.copy(activeCollectionName = name, activeCollectionMediaPaths = if (name == null) emptySet() else it.activeCollectionMediaPaths) } }
    fun setCollectionMediaPaths(paths: Set<String>) { _state.update { it.copy(activeCollectionMediaPaths = paths) } }
    fun setFolderFilterName(name: String?) { _state.update { it.copy(activeFolderFilterName = name) } }
    fun setPreFilterTab(tab: Int) { _state.update { it.copy(preFilterTab = tab) } }

    /** Toggles [tag] in the raw multi-select tag set shared by the persistent Filter sheet and the
     * search panel's own tag chips, re-deriving the hierarchy-expanded [ExplorerUiState.activeTagFilter]
     * used for SQL matching while retaining each raw tag as an independently controllable chip. */
    fun toggleTagFilter(tag: String) {
        _state.update { it.withTagToggled(tag, getApplication<Application>().config.tagHierarchy) }
    }

    fun clearFilters() {
        _state.update { it.withFiltersCleared() }
    }

    fun triggerMediaRefresh() {
        _state.update { it.copy(mediaRefreshTrigger = it.mediaRefreshTrigger + 1) }
    }

    /** Runs the foreground catch-up once the startup database bootstrap is complete. Keeping this
     * in the ViewModel gives Media, Albums and Explorer the same repository/sync boundary instead
     * of letting each tab decide independently when to read MediaStore. */
    fun synchronizeLibraryAfterInitialization() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val added = org.fossify.gallery.helpers.MediaRepository(getApplication<Application>())
                    .syncNewMediaFromStoreCoalesced(source = "foreground-init")
                if (added.isNotEmpty()) {
                    RefreshBus.trigger(pathsChanged = true, affectedPaths = added.map { it.path })
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("ExplorerViewModel", "foreground library sync failed", e)
            }
        }
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
        _state.update { it.copy(dbInitError = null) }
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            var initialized = false
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
                        val accumulator = LibraryBootstrapAccumulator()
                        val batch = ArrayList<Medium>(BOOTSTRAP_INSERT_BATCH_SIZE)
                        fun persistBatch() {
                            if (batch.isNotEmpty()) {
                                // IGNORE, not REPLACE: a racing MediaStore update must never wipe
                                // metadata which may already have been written into the new row.
                                ctx.mediaDB.insertAllKeepingExisting(batch)
                                batch.clear()
                            }
                        }
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
                                    val medium = Medium(
                                        id = null, name = File(path).name, path = path, parentPath = File(path).parent ?: "",
                                        modified = modified, taken = taken, size = size, type = type,
                                        videoDuration = duration, isFavorite = false, deletedTS = 0L, mediaStoreId = 0, rating = 0,
                                        dateAdded = added,
                                    )
                                    accumulator.add(medium)
                                    batch += medium
                                    if (batch.size >= BOOTSTRAP_INSERT_BATCH_SIZE) persistBatch()
                                }
                            }
                        persistBatch()
                        // Directory count is normally in the low thousands, but batch this write
                        // too so a media dump with many one-file folders has the same bound.
                        accumulator.directories().chunked(BOOTSTRAP_INSERT_BATCH_SIZE).forEach(ctx.directoryDB::insertAll)
                        // The bootstrap above enumerated the complete current MediaStore view.
                        // Advance the incremental watermark so the post-bootstrap foreground
                        // catch-up does not immediately scan the entire library a second time.
                        ctx.config.lastSyncTimestamp = accumulator.latestSeenTimestamp.takeIf { it > 0L }
                            ?: System.currentTimeMillis()
                    }
                    _state.update { it.copy(dbInitialized = true, dbInitError = null) }
                    if (ctx.config.newMediaSeenAt == 0L) {
                        // Existing installs should not suddenly classify the whole historical
                        // library as "new" after upgrading. Start the inbox at the first usable
                        // foreground snapshot; subsequent syncs remain visible until opened.
                        ctx.config.newMediaSeenAt = System.currentTimeMillis()
                    }
                    refreshNewMediaCount()
                    initialized = true
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    _state.update { it.copy(dbInitError = e.message) }
                }
            }
            if (initialized) onComplete?.invoke()
        }
    }
}

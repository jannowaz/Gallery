package org.fossify.gallery.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.gallery.compose.util.XmpBatch
import org.fossify.gallery.compose.screens.SortField
import org.fossify.gallery.helpers.GroupBy
import org.fossify.gallery.helpers.GroupOrder
import org.fossify.gallery.helpers.MediaRepository
import org.fossify.gallery.helpers.RefreshBus
import org.fossify.gallery.models.Medium
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MonthGroup(val label: String, val items: List<Medium>)

data class SortSpec(val field: SortField, val desc: Boolean)

/** Active filtering for the media grid. Owned by the ViewModel, never resolved in composition.
 * [tagNames] is a set of tag *names* (already hierarchy-expanded by the caller via
 * [org.fossify.gallery.helpers.expandTagsWithDescendants]) - resolved to matching files in SQL via
 * `media_tags`, not pre-resolved to paths, so the resolution scales with the DB instead of a
 * client-side path-set size. [pathFilter]/[excludePaths] stay path-shaped (literal files and/or
 * directory prefixes) since search-result and arbitrary-selection callers only ever have paths. */
data class MediaFilter(
    val rating: Int = 0,
    val tagNames: Set<String>? = null,
    val pathFilter: Set<String>? = null,
    val excludePaths: Set<String>? = null,
    val minSize: Long = 0L,
    val dateRange: Int = 0, // 0: All, 1: Today, 2: 7d, 3: 30d, 4: 1y
    val typeFilter: Int = 0, // 0: All, 1: Image, 2: Video - matches Medium.type
) {
    val isActive: Boolean get() = rating > 0 || tagNames != null || pathFilter != null || excludePaths != null || minSize > 0 || dateRange > 0 || typeFilter > 0
}

data class MediaUiState(
    val allMedia: List<Medium> = emptyList(),
    /** Filtered + sorted media ready to render. The screen reads this; it never filters/sorts itself. */
    val displayMedia: List<Medium> = emptyList(),
    val monthGroups: List<MonthGroup> = emptyList(),
    /** Path -> tags, populated only while [MediaViewModel.groupBy] is [GroupBy.TAG] (an override-only
     * batch fetch - the unbounded paged tab never groups by tag, see MediaGrouping.kt). */
    val tagsByPath: Map<String, List<String>> = emptyMap(),
    val filter: MediaFilter = MediaFilter(),
    val taggedPaths: Set<String> = emptySet(),
    val aspectRatios: Map<String, Float> = emptyMap(),
    val selectedCommonTags: Set<String> = emptySet(),
    val allTags: List<String> = emptyList(),
    val tagCounts: Map<String, Int> = emptyMap(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val scrollIndex: Int = 0,
    val scrollOffset: Int = 0,
)

class MediaViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MediaRepository(application)
    private val _state = MutableStateFlow(MediaUiState())
    val state: StateFlow<MediaUiState> = _state.asStateFlow()

    private var loaded = false
    private var sortField = SortField.DATE
    private var sortDesc = true
    private var groupBy = GroupBy.NONE
    private var groupOrder = GroupOrder.ALPHABETICAL

    // Cache for activePathsSorted() below - the underlying query (MediumDao.getActivePathsByDate,
    // the default sort) has no way to avoid a full-table sort on a real library (its ORDER BY key is
    // a per-row fallback expression - date_taken if set, else last_modified - which no plain-column
    // index can satisfy; confirmed live that trying to add a matching SQLite expression index breaks
    // Room's schema validation outright, since Room's TableInfo introspection can't represent
    // expression indices at all). Without this cache, every single tap to open the Viewer re-ran
    // that full sort from scratch - on a large library (this app is used with ~200k+ media) that was
    // a real, reported "opening the Viewer has a noticeable delay" bug. Caching means only the
    // *first* tap per session/sort-change pays that cost; every subsequent one in the same browsing
    // session is instant. Invalidated below whenever the sort changes or the underlying data does.
    private var cachedSortedPaths: List<String>? = null
    private var cachedSortedPathsKey: Pair<SortField, Boolean>? = null

    // Same caching problem as [cachedSortedPaths] above, for the filtered case (e.g. opening the
    // Viewer from inside a Collection): [activePathsSortedFiltered] used to re-run its SQL query on
    // every single tap with no cache at all, reintroducing the exact "noticeable delay opening the
    // Viewer" bug the unfiltered cache above was built to fix. Prefetched whenever the filter or sort
    // changes; served straight from cache on tap.
    private var cachedFilteredPaths: List<String>? = null
    private var cachedFilteredPathsKey: Triple<MediaFilter, SortField, Boolean>? = null
    private var filteredPrefetchDeferred: Deferred<List<String>>? = null
    private fun prefetchFilteredPathsAsync() {
        val filter = filterFlow.value
        if (!filter.isActive) return
        val key = Triple(filter, sortField, sortDesc)
        filteredPrefetchDeferred?.cancel()
        filteredPrefetchDeferred = viewModelScope.async(Dispatchers.IO) {
            repository.getActivePathsSortedFiltered(filter, sortField, sortDesc).also {
                cachedFilteredPaths = it
                cachedFilteredPathsKey = key
            }
        }
    }

    // `override` (Favorites' externally-supplied media list) is the only thing `recompute()` still
    // handles in-memory. Every MediaFilter dimension (rating/tag/path/size/date) is pushed into SQL
    // and served through `pagedMedia` below instead (see MediaRepository.getMediaPagedFiltered) -
    // backed by Room's PagingSource + InvalidationTracker, so it stays fresh across rename/rating/
    // delete/sync without any manual refresh call, and isn't capped like the old in-memory pipeline.
    private var override: List<Medium>? = null

    private val sortSpec = MutableStateFlow(SortSpec(sortField, sortDesc))
    private val filterFlow = MutableStateFlow(MediaFilter())

    @OptIn(ExperimentalCoroutinesApi::class)
    val pagedMedia: Flow<PagingData<Medium>> = combine(sortSpec, filterFlow) { spec, f -> spec to f }
        .flatMapLatest { (spec, f) ->
            Pager(PagingConfig(pageSize = 60, prefetchDistance = 40, enablePlaceholders = false)) {
                if (f.isActive) repository.getMediaPagedFiltered(f, spec.field, spec.desc)
                else repository.getMediaPaged(spec.field, spec.desc)
            }.flow
        }
        .cachedIn(viewModelScope)

    /** True total for the active filter, shown in [FilterBreadcrumbs]'s result-count label - was
     * previously bound directly to `LazyPagingItems.itemCount`, which only reflects how many rows
     * Paging3 has actually loaded into memory (starting at the library-default initial-load size,
     * growing as the user scrolls), so a Collection with thousands of matches showed a frozen,
     * far-too-small number on open. `null` while no filter is active or the count hasn't resolved
     * yet - callers fall back to `itemCount` for that brief window. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val filteredResultCount: StateFlow<Int?> = filterFlow
        .flatMapLatest { f -> if (f.isActive) flow { emit(repository.getFilteredMediaCount(f)) } else flowOf<Int?>(null) }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private fun applySort(list: List<Medium>): List<Medium> {
        if (sortField == SortField.RATING) {
            return if (sortDesc) list.sortedWith(compareByDescending<Medium> { it.rating }.thenByDescending { it.modified })
            else list.sortedWith(compareBy<Medium> { it.rating }.thenBy { it.modified })
        }
        val sorted = when (sortField) {
            SortField.NAME -> list.sortedBy { it.name.lowercase() }
            // date_sort_key (date_added-preferring) - identical key to the paged/SQL date sort, so an
            // override list (Favorites, a drilled-into folder) orders the same way the main grid does.
            SortField.DATE -> list.sortedBy { if (it.dateSortKey > 0) it.dateSortKey else if (it.taken > 0) it.taken else it.modified }
            SortField.SIZE -> list.sortedBy { it.size }
            SortField.RATING -> list
            // COUNT (file count) is a folder-only sort option, never reachable for a media list -
            // falls back to name here purely so this `when` stays exhaustive.
            SortField.COUNT -> list.sortedBy { it.name.lowercase() }
        }
        return if (sortDesc) sorted.reversed() else sorted
    }

    fun setSort(field: SortField, desc: Boolean) {
        if (field == sortField && desc == sortDesc) return
        sortField = field
        sortDesc = desc
        sortSpec.value = SortSpec(field, desc)
        recompute()
        prefetchSortedPathsAsync()
        prefetchFilteredPathsAsync()
    }

    /** Only meaningful for the override path (Favorites, a drilled-into folder) - the unbounded
     * paged tab keeps its own fixed month grouping (see MediaScreen's PagedRow), so this is never
     * gated behind `mediaOverride == null` the way [setSort] is. */
    fun setGroupSettings(groupBy: GroupBy, groupOrder: GroupOrder) {
        if (groupBy == this.groupBy && groupOrder == this.groupOrder) return
        this.groupBy = groupBy
        this.groupOrder = groupOrder
        recompute()
    }

    // The DB query itself (`getActivePathsSorted`) is fast (index-backed, no full sort) but reading
    // and marshaling ~200k full_path Strings out of the cursor still costs real time on-device
    // (measured 1-3.7s on a 202k-item real library, all cursor/JNI overhead - not the query plan).
    // So this must never run synchronously on a tap: kick it off in the background whenever the
    // underlying data/sort could have changed, and let activePathsSorted() serve the (possibly
    // slightly stale - fine for swipe-through ordering) cache instead of blocking on a fresh fetch.
    private var prefetchDeferred: Deferred<List<String>>? = null
    private fun prefetchSortedPathsAsync() {
        val key = sortField to sortDesc
        prefetchDeferred?.cancel()
        prefetchDeferred = viewModelScope.async(Dispatchers.IO) {
            repository.getActivePathsSorted(sortField, sortDesc).also {
                cachedSortedPaths = it
                cachedSortedPathsKey = key
            }
        }
    }

    /** Drives the display list from an external source (e.g. Favorites' own fetched list) instead of
     * the DB scan/filter pipeline - the only remaining consumer of `recompute()`. */
    fun setOverride(media: List<Medium>?) {
        if (media == override) return
        override = media
        recompute()
    }

    /** Updates the active DB-pushed filter. `pagedMedia` observes [filterFlow] and reissues a fresh
     * Pager against [MediaRepository.getMediaPagedFiltered] - Paging3 + Room's InvalidationTracker
     * handle incremental loading and staleness from there, same as the unfiltered path. */
    fun setFilter(rating: Int, tagNames: Set<String>?, pathFilter: Set<String>?, excludePaths: Set<String>? = null, minSize: Long = 0, dateRange: Int = 0, typeFilter: Int = 0) {
        val next = MediaFilter(rating, tagNames, pathFilter, excludePaths, minSize, dateRange, typeFilter)
        if (next == filterFlow.value) return
        filterFlow.value = next
        _state.update { it.copy(filter = next) }
        prefetchFilteredPathsAsync()
    }

    private fun recompute() {
        val ov = override ?: return
        viewModelScope.launch {
            val display = withContext(Dispatchers.Default) { applySort(ov) }
            val groups = withContext(Dispatchers.Default) { groupByMonth(display) }
            val tags = if (groupBy == GroupBy.TAG) repository.getTagsForPaths(display.map { it.path }) else emptyMap()
            _state.update { it.copy(displayMedia = display, monthGroups = groups, tagsByPath = tags) }
        }
    }

    /** Lazily decodes and caches the aspect ratio of an image (mosaic layout). Decoding runs in the repo.
     *
     * An UNLIMITED channel, not a buffered SharedFlow: the previous MutableSharedFlow(extraBufferCapacity
     * = 64) + tryEmit() silently dropped requests whenever more than 64 tiles asked for their ratio
     * faster than the 200ms-batched decoder drained them - i.e. any fast scroll through a mosaic. A
     * dropped request never retried (requestAspect fires once per tile via LaunchedEffect(path) and the
     * path isn't in aspectRatios, so nothing re-asks), leaving those tiles stuck at the 1f square
     * fallback - the "mosaic not always rendered right" report. trySend on an UNLIMITED channel never
     * fails, so no request is lost. */
    private val aspectRequests = kotlinx.coroutines.channels.Channel<String>(kotlinx.coroutines.channels.Channel.UNLIMITED)

    init {
        viewModelScope.launch {
            aspectRequests.receiveAsFlow()
                .chunked(20, 200) // Batch of 20 or every 200ms
                .collect { paths ->
                    val updates = withContext(Dispatchers.Default) {
                        paths.distinct().associateWith { repository.decodeImageAspect(it) }
                    }
                    _state.update { it.copy(aspectRatios = it.aspectRatios + updates) }
                }
        }
    }

    fun requestAspect(path: String) {
        if (_state.value.aspectRatios.containsKey(path)) return
        aspectRequests.trySend(path)
    }

    // Helper for batching flow
    private fun <T> Flow<T>.chunked(size: Int, timeoutMillis: Long): Flow<List<T>> = flow {
        val buffer = mutableListOf<T>()
        var lastEmitTime = System.currentTimeMillis()
        collect { value ->
            buffer.add(value)
            val now = System.currentTimeMillis()
            if (buffer.size >= size || (now - lastEmitTime >= timeoutMillis && buffer.isNotEmpty())) {
                emit(buffer.toList())
                buffer.clear()
                lastEmitTime = now
            }
        }
    }

    fun loadTaggedPaths() {
        viewModelScope.launch {
            val tagged = withContext(Dispatchers.IO) { repository.getTaggedPaths() }
            _state.update { it.copy(taggedPaths = tagged) }
        }
    }

    fun loadAllTags() {
        viewModelScope.launch {
            val counts = withContext(Dispatchers.IO) { repository.getTagCounts() }
            val ordered = counts.entries.sortedByDescending { it.value }.map { it.key }
            _state.update { it.copy(allTags = ordered, tagCounts = counts) }
        }
    }

    fun loadCommonTags(paths: Set<String>) {
        viewModelScope.launch {
            val common = if (paths.isEmpty()) emptySet()
            else withContext(Dispatchers.IO) { paths.map { repository.getTags(it) }.reduceOrNull { a, b -> a intersect b } ?: emptySet() }
            _state.update { it.copy(selectedCommonTags = common) }
        }
    }

    fun toggleQuickTag(paths: Set<String>, tag: String) {
        viewModelScope.launch(Dispatchers.IO) {
            paths.forEach { p -> if (repository.getTags(p).contains(tag)) repository.removeTag(p, tag) else repository.addTag(p, tag) }
            loadTaggedPaths()
            loadCommonTags(paths)
        }
    }

    fun addTagFor(paths: Collection<String>, tag: String) {
        viewModelScope.launch {
            XmpBatch.run(getApplication(), paths.toList()) { repository.addTag(it, tag) }
            loadTaggedPaths()
        }
    }

    fun removeTagFor(paths: Collection<String>, tag: String) {
        viewModelScope.launch {
            XmpBatch.run(getApplication(), paths.toList()) { repository.removeTag(it, tag) }
            loadTaggedPaths()
        }
    }

    fun setRatingFor(paths: Collection<String>, rating: Int) {
        viewModelScope.launch {
            try {
                // One batch DB write (single InvalidationTracker notification) + per-file XMP writes
                // (no DB write each - see MediaRepository.writeRatingXmp). XmpBatch surfaces the
                // per-file progress and any write failures that used to vanish silently.
                withContext(Dispatchers.IO) { repository.setDbRatingBatch(paths, rating) }
                XmpBatch.run(getApplication(), paths.toList()) { repository.writeRatingXmp(it, rating) }
                silentRefresh()
            } catch (e: Exception) {
                android.util.Log.e("MediaViewModel", "setRatingFor failed", e)
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    init {
        load()
        loadTaggedPaths()
        prefetchSortedPathsAsync()
    }

    // Keeps the Viewer's swipe-through path caches (both above) fresh while the user stays put inside
    // a filtered view (e.g. a Collection) - moving/deleting/renaming files there used to only refresh
    // via MediaScreen's own refreshTrigger param, which in practice only ever bumps once at app start
    // (ExplorerViewModel.triggerMediaRefresh() has no other caller), so mid-session moves left the
    // cached filtered path list stale until the user backed out and re-entered the Collection (which
    // re-runs setFilter() and refreshes it as a side effect). Subscribing here directly, like several
    // other screens already do (CollectionsScreen, FavoritesScreen), fixes it regardless of whether
    // that other wiring ever gets fixed.
    init {
        viewModelScope.launch {
            RefreshBus.events.collect {
                prefetchSortedPathsAsync()
                prefetchFilteredPathsAsync()
            }
        }
    }

    fun load() {
        if (loaded) return
        loaded = true
        rescanAndLoad()
    }

    fun refresh() {
        loaded = false
        rescanAndLoad()
        prefetchSortedPathsAsync()
        prefetchFilteredPathsAsync()
    }

    // NOTE: state.allMedia/displayMedia only feed the override rendering path (Favorites, which
    // refreshes independently via its own RefreshBus subscription and never reads this
    // ViewModel's state) and the initial-load error state (only ever set by rescanAndLoad(),
    // never here) - every grid render (unfiltered and filtered alike) goes through `pagedMedia`
    // instead. This used to also re-fetch and re-sort up to 20,000 rows into `state.allMedia` on
    // every single call - i.e. every RefreshBus tick, which under the ContentObserver fired for
    // every MediaStore write from *any* app - even though nothing actually read the result.
    fun silentRefresh() {
        prefetchSortedPathsAsync()
        prefetchFilteredPathsAsync()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.syncNewMediaFromStore()
            } catch (e: Exception) {
                android.util.Log.e("MediaViewModel", "silentRefresh failed", e)
            }
        }
    }

    private fun rescanAndLoad() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            var loadError: String? = null
            val media = withContext(Dispatchers.IO) {
                try { repository.syncNewMediaFromStore() } catch (t: Throwable) { android.util.Log.e("MediaVMLoad", "syncNewMediaFromStore failed", t) }
                val db = try { repository.getNewestMedia(20000) } catch (t: Throwable) { android.util.Log.e("MediaVMLoad", "getNewestMedia failed", t); loadError = t.message ?: ""; emptyList() }
                android.util.Log.i("MediaVMLoad", "db=${db.size}")
                if (db.isNotEmpty()) db.sortedByDescending { it.modified }
                else {
                    val s = try { repository.scanMediaFromDisk() } catch (t: Throwable) { android.util.Log.e("MediaVMLoad", "scanMediaFromDisk failed", t); loadError = t.message ?: ""; emptyList() }
                    android.util.Log.i("MediaVMLoad", "scanMediaFromDisk=${s.size}")
                    s
                }
            }
            android.util.Log.i("MediaVMLoad", "final media=${media.size}")
            _state.update { it.copy(allMedia = applySort(media), isLoading = false, error = if (media.isEmpty()) loadError else null) }
            recompute()
        }
    }

    /** All active paths, unsorted - used for select-all/invert in the unfiltered browse view. */
    suspend fun activePaths(): Set<String> = withContext(Dispatchers.IO) { repository.getActivePaths().toSet() }

    /** Full sorted path list (matching the current sort field/direction) for Viewer swipe-through,
     * independent of how much of the paged grid has loaded so far. Cached - see cachedSortedPaths.
     *
     * Joins an in-flight [prefetchDeferred] instead of firing a second concurrent query when one is
     * already running (e.g. right after cold launch: init{} kicks off a prefetch, and the user can
     * tap into the Viewer before it finishes). Two concurrent executions of this ~200k-row query
     * fighting over SQLite's CursorWindow was reliably hanging the Viewer forever on a real ~200k-item
     * library (repeated `CursorWindow: Failed NO_MEMORY` in a tight retry loop, one worker thread
     * pinned at ~96% CPU for minutes) - joining the existing query instead of racing it fixes that. */
    suspend fun activePathsSorted(): List<String> = withContext(Dispatchers.IO) {
        val key = sortField to sortDesc
        cachedSortedPaths?.takeIf { cachedSortedPathsKey == key }?.let { return@withContext it }
        prefetchDeferred?.takeIf { it.isActive }?.await()
        cachedSortedPaths?.takeIf { cachedSortedPathsKey == key }?.let { return@withContext it }
        repository.getActivePathsSorted(sortField, sortDesc).also {
            cachedSortedPaths = it
            cachedSortedPathsKey = key
        }
    }

    /** Same as [activePathsSorted] but scoped to the currently active filter - used for Viewer
     * swipe-through and select-all/invert while a filter is applied, so both cover the complete
     * filtered set rather than just what's paged into the grid so far. Joins an in-flight prefetch
     * for the same reason as [activePathsSorted] above. */
    suspend fun activePathsSortedFiltered(): List<String> = withContext(Dispatchers.IO) {
        val f = filterFlow.value
        if (!f.isActive) return@withContext activePathsSorted()
        val key = Triple(f, sortField, sortDesc)
        cachedFilteredPaths?.takeIf { cachedFilteredPathsKey == key }?.let { return@withContext it }
        filteredPrefetchDeferred?.takeIf { it.isActive }?.await()
        cachedFilteredPaths?.takeIf { cachedFilteredPathsKey == key }?.let { return@withContext it }
        repository.getActivePathsSortedFiltered(f, sortField, sortDesc).also {
            cachedFilteredPaths = it
            cachedFilteredPathsKey = key
        }
    }

    fun deletePaths(paths: Set<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Only paths that actually got deleted are dropped from local state - deleteMedium
                // now leaves a path's row alone (and returns false) when the underlying file removal
                // itself failed, so treating every requested path as gone here would silently make a
                // still-present file disappear from the grid.
                val deleted = paths.filterTo(mutableSetOf()) { p -> repository.deleteMedium(p) }
                _state.update { s -> s.copy(allMedia = s.allMedia.filter { it.path !in deleted }) }
                recompute()
                // activePathsSorted()/activePathsSortedFiltered() cache the full swipe-through path list
                // keyed only on sort/filter, not on data version - without this, a delete leaves that
                // cache stale and openViewerPaged() hands the Viewer a list that still contains (or is
                // shifted around) the just-deleted items, opening the wrong media for a given tap index.
                prefetchSortedPathsAsync()
                prefetchFilteredPathsAsync()
            } catch (e: Exception) {
                android.util.Log.e("MediaViewModel", "deletePaths failed", e)
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun softDeletePaths(paths: Set<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.moveToRecycleBinBatch(paths)
                _state.update { s -> s.copy(allMedia = s.allMedia.filter { it.path !in paths }) }
                recompute()
                // Same staleness fix as deletePaths() above.
                prefetchSortedPathsAsync()
                prefetchFilteredPathsAsync()
            } catch (e: Exception) {
                android.util.Log.e("MediaViewModel", "softDeletePaths failed", e)
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun undoDeletePaths(paths: Set<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.restoreFromRecycleBinBatch(paths)
                refresh()
            } catch (e: Exception) {
                android.util.Log.e("MediaViewModel", "undoDeletePaths failed", e)
            }
        }
    }

    fun saveScrollPosition(index: Int, offset: Int) {
        _state.update { it.copy(scrollIndex = index, scrollOffset = offset) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    private fun groupByMonth(media: List<Medium>): List<MonthGroup> {
        if (media.isEmpty()) return emptyList()
        val g = LinkedHashMap<String, MutableList<Medium>>()
        media.forEach { m -> g.getOrPut(monthLabelFor(m)) { mutableListOf() }.add(m) }
        return g.map { MonthGroup(it.key, it.value) }
    }

    companion object {
        /** Shared with MediaScreen's paged-grid header scan so both label the same item identically.
         * Called from both a background dispatcher (recompute's groupByMonth) and Compose's main
         * thread (paged header scan, once per item on every buildPagedRows rebuild - i.e. on every
         * single Paging3 page load), and SimpleDateFormat isn't thread-safe to share across threads.
         * A ThreadLocal gives each caller thread its own reused instance - same isolation as
         * allocating fresh every call, but without paying SimpleDateFormat's pattern-compile +
         * locale-data-lookup cost per item, which on a large library was the dominant cost of every
         * paged-grid rebuild (confirmed via profiling: the per-item work in buildPagedRows was almost
         * entirely this allocation, not the list scan itself). Locale.getDefault() is read once per
         * thread's first call - a runtime locale switch mid-session won't retroactively update an
         * already-cached formatter, an acceptable tradeoff shared by most per-thread formatter caches. */
        private val monthLabelFormat = ThreadLocal.withInitial { SimpleDateFormat("MMMM yyyy", Locale.getDefault()) }

        fun monthLabelFor(m: Medium): String {
            // Key the month header on date_sort_key (date_added-preferring), the exact value the grid
            // is ordered by - otherwise a freshly downloaded photo would sort to the top yet display
            // under its old EXIF-capture month header, i.e. an "old month" group sitting above newer
            // ones. Fall back to taken/modified for rows written before date_sort_key existed (0).
            val key = when {
                m.dateSortKey > 0 -> m.dateSortKey
                m.taken > 0 -> m.taken
                else -> m.modified
            }
            return monthLabelFormat.get()!!.format(Date(key)).replaceFirstChar { it.uppercase() }
        }
    }
}

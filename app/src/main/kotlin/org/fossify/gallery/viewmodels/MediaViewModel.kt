package org.fossify.gallery.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.CancellationException
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
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.fossify.gallery.R
import org.fossify.gallery.compose.util.XmpBatch
import org.fossify.gallery.compose.screens.SortField
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.GroupBy
import org.fossify.gallery.helpers.GroupOrder
import org.fossify.gallery.helpers.GroupSummary
import org.fossify.gallery.helpers.MediaRepository
import org.fossify.gallery.helpers.RefreshBus
import org.fossify.gallery.helpers.ViewerLoadDirection
import org.fossify.gallery.helpers.ViewerPathChunk
import org.fossify.gallery.helpers.ViewerPathWindow
import org.fossify.gallery.models.Medium
import org.fossify.gallery.models.MediaReviewFilter
import org.fossify.gallery.models.MediaReviewStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MonthGroup(val label: String, val items: List<Medium>)

data class SortSpec(val field: SortField, val desc: Boolean, val groupBy: GroupBy = GroupBy.NONE)

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
    /** Stable watermark used by the Media tab's "new media" inbox. */
    val addedSince: Long = 0L,
    /** Explicit members of a manual collection. They are additive to smart filters. */
    val additionalPaths: Set<String>? = null,
    /** When true, a file must contain every selected tag; false keeps the fast any-tag behavior. */
    val tagMatchAll: Boolean = false,
    /** Independent review inbox filter. It is intentionally not a collection/stack path filter. */
    val reviewFilter: MediaReviewFilter = MediaReviewFilter.NONE,
    /** Space-separated filename, path and tag search; executed by SQLite and therefore paged. */
    val textQuery: String = "",
) {
    val isActive: Boolean get() = rating > 0 || tagNames != null || pathFilter != null || excludePaths != null || minSize > 0 || dateRange > 0 || typeFilter > 0 || addedSince > 0L || additionalPaths != null || reviewFilter != MediaReviewFilter.NONE || textQuery.isNotBlank()
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
    /** Number of selected media items carrying each tag; used to render mixed-state quick tags. */
    val selectedTagCounts: Map<String, Int> = emptyMap(),
    val allTags: List<String> = emptyList(),
    val tagCounts: Map<String, Int> = emptyMap(),
    val isLoading: Boolean = false,
    /** Loading state for the bounded full-list model used by tag grouping inside a Collection. */
    val isFullListGroupingLoading: Boolean = false,
    val error: String? = null,
    val scrollIndex: Int = 0,
    val scrollOffset: Int = 0,
)

class MediaViewModel(application: Application) : AndroidViewModel(application) {

    // Keep the first Room cursor deliberately small. PagingConfig otherwise uses three pages for
    // its initial load; on a large library that opens several large CursorWindows at once and can
    // surface as the generic "media could not be loaded" state right after a metadata write.
    private object PagingTuning {
        const val MEDIA_PAGE_SIZE = 30
        const val MEDIA_INITIAL_LOAD_SIZE = 60
        const val MEDIA_PREFETCH_DISTANCE = 15
        // Eight pages retain enough context for the visible window, grouping headers and viewer
        // range selection while allowing Paging3 to drop distant thumbnail-bearing rows.
        const val MEDIA_MAX_SIZE = MEDIA_PAGE_SIZE * 8
    }

    private val repository = MediaRepository(application)
    private val _state = MutableStateFlow(MediaUiState())
    val state: StateFlow<MediaUiState> = _state.asStateFlow()

    private var loaded = false
    private var sortField = SortField.DATE
    private var sortDesc = true
    private var groupBy = GroupBy.NONE
    private var groupOrder = GroupOrder.ALPHABETICAL
    // Only a bounded Collection may opt into a full-list tag grouping. The unbounded Media tab
    // remains Paging-backed and never loads the whole library just to build multi-tag sections.
    private var fullListGrouping = false

    // Cache for activePathsSorted() below - the underlying query (MediumDao.getActivePathsByDate,
    // the default sort) has no way to avoid a full-table sort on a real library (its ORDER BY key is
    // a per-row fallback expression - date_taken if set, else last_modified - which no plain-column
    // index can satisfy; confirmed live that trying to add a matching SQLite expression index breaks
    // Room's schema validation outright, since Room's TableInfo introspection can't represent
    // expression indices at all). Without this cache, every single tap to open the Viewer re-ran
    // that full sort from scratch - on a large library (this app is used with ~200k+ media) that was
    // a real, reported "opening the Viewer has a noticeable delay" bug. Caching means only the
    // *first* viewer tap per session/sort-change pays that cost; every subsequent one in the same
    // browsing session is instant. The query is deliberately lazy: starting it alongside the first
    // Paging cursor used to create a large CursorWindow/heap peak on real devices, and a metadata
    // write could then surface the generic "media could not be loaded" state even though the DB was
    // healthy. Invalidated below whenever the sort changes or the underlying data does.
    @Volatile private var cachedSortedPaths: List<String>? = null
    @Volatile private var cachedSortedPathsKey: Pair<SortField, Boolean>? = null
    @Volatile private var cachedSortedPathsGeneration = -1L

    // Same caching problem as [cachedSortedPaths] above, for the filtered case (e.g. opening the
    // Viewer from inside a Collection): [activePathsSortedFiltered] used to re-run its SQL query on
    // every single tap with no cache at all, reintroducing the exact "noticeable delay opening the
    // Viewer" bug the unfiltered cache above was built to fix. Prefetched whenever the filter or sort
    // changes; served straight from cache on tap. Like the unfiltered cache, it is populated on
    // demand so opening the Media tab does not compete with its first Paging load.
    @Volatile private var cachedFilteredPaths: List<String>? = null
    @Volatile private var cachedFilteredPathsKey: Triple<MediaFilter, SortField, Boolean>? = null
    @Volatile private var cachedFilteredPathsGeneration = -1L
    @Volatile private var filteredPrefetchDeferred: Deferred<List<String>>? = null
    @Volatile private var filteredPrefetchGeneration = -1L
    private val viewerPathQueryMutex = Mutex()
    private fun prefetchFilteredPathsAsync() {
        val filter = filterFlow.value
        if (!filter.isActive) return
        val key = Triple(filter, sortField, sortDesc)
        val generation = RefreshBus.pathGeneration
        if (cachedFilteredPaths != null && cachedFilteredPathsGeneration == generation && cachedFilteredPathsKey == key) return
        filteredPrefetchDeferred?.cancel()
        filteredPrefetchGeneration = generation
        filteredPrefetchDeferred = viewModelScope.async(Dispatchers.IO) {
            viewerPathQueryMutex.withLock {
                if (cachedFilteredPathsGeneration == generation && cachedFilteredPathsKey == key) {
                    return@withLock cachedFilteredPaths ?: emptyList()
                }
                repository.getActivePathsSortedFiltered(filter, sortField, sortDesc, groupBy).also { paths ->
                    // A refresh can happen while the large path query is running. Never publish that
                    // older snapshot after the generation changed, otherwise a newly inserted/deleted
                    // item can still shift the Viewer's positions back to the old library state.
                    if (generation == RefreshBus.pathGeneration && key == Triple(filterFlow.value, sortField, sortDesc)) {
                        cachedFilteredPaths = paths
                        cachedFilteredPathsKey = key
                        cachedFilteredPathsGeneration = generation
                    }
                }
            }
        }
    }

    // `override` (Favorites' externally-supplied media list) is the only thing `recompute()` still
    // handles in-memory. Every MediaFilter dimension (rating/tag/path/size/date) is pushed into SQL
    // and served through `pagedMedia` below instead (see MediaRepository.getMediaPagedFiltered) -
    // backed by Room's PagingSource + InvalidationTracker, so it stays fresh across rename/rating/
    // delete/sync without any manual refresh call, and isn't capped like the old in-memory pipeline.
    private var override: List<Medium>? = null
    private var recomputeJob: Job? = null

    private val sortSpec = MutableStateFlow(SortSpec(sortField, sortDesc, groupBy))
    private val filterFlow = MutableStateFlow(MediaFilter())
    private val collapsedGroupSqlValues = MutableStateFlow<Set<String>>(emptySet())

    fun setCollapsedGroupSqlValues(values: Set<String>) {
        if (collapsedGroupSqlValues.value != values) {
            collapsedGroupSqlValues.value = values
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val pagedMedia: Flow<PagingData<Medium>> = combine(sortSpec, filterFlow, collapsedGroupSqlValues) { spec, f, collapsed ->
        Triple(spec, f, collapsed)
    }
        .flatMapLatest { (spec, f, collapsed) ->
            Pager(
                PagingConfig(
                    pageSize = PagingTuning.MEDIA_PAGE_SIZE,
                    initialLoadSize = PagingTuning.MEDIA_INITIAL_LOAD_SIZE,
                    prefetchDistance = PagingTuning.MEDIA_PREFETCH_DISTANCE,
                    maxSize = PagingTuning.MEDIA_MAX_SIZE,
                    enablePlaceholders = false,
                )
            ) {
                val effectiveGroup = spec.groupBy.takeUnless { it == GroupBy.TAG } ?: GroupBy.NONE
                val excluded = if (effectiveGroup == GroupBy.NONE) emptySet() else collapsed
                if (f.isActive) repository.getMediaPagedFiltered(f, spec.field, spec.desc, effectiveGroup, excluded)
                else repository.getMediaPaged(spec.field, spec.desc, effectiveGroup, excluded)
            }.flow
        }
        .cachedIn(viewModelScope)

    /** Exact SQL-backed group summaries (labels + totals) for grouped Paging views. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val groupSummaries: StateFlow<List<GroupSummary>> =
        combine(filterFlow, sortSpec, RefreshBus.events.onStart { emit(Unit) }) { f, spec, _ -> f to spec }
            .flatMapLatest { (f, spec) ->
                val effectiveGroup = spec.groupBy.takeUnless { it == GroupBy.TAG } ?: GroupBy.NONE
                if (effectiveGroup == GroupBy.NONE) {
                    flowOf(emptyList())
                } else {
                    val unrated = getApplication<Application>().getString(R.string.group_unrated)
                    flow { emit(repository.getGroupedMediaSummaries(f, effectiveGroup, spec.desc, unrated)) }
                }
            }
            .flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** True total for the active filter, shown in [FilterBreadcrumbs]'s result-count label. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val filteredResultCount: StateFlow<Int?> =
        combine(filterFlow, RefreshBus.events.onStart { emit(Unit) }) { f, _ -> f }
            .flatMapLatest { f -> if (f.isActive) flow { emit(repository.getFilteredMediaCount(f)) } else flowOf<Int?>(null) }
            .flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private fun applySort(list: List<Medium>): List<Medium> {
        return applySortFor(list, sortField, sortDesc)
    }

    fun setSort(field: SortField, desc: Boolean, grouping: GroupBy = groupBy) {
        if (field == sortField && desc == sortDesc && grouping == groupBy) return
        if (grouping != groupBy) {
            cachedSortedPaths = null
            cachedFilteredPaths = null
            collapsedGroupSqlValues.value = emptySet()
        }
        sortField = field
        sortDesc = desc
        groupBy = grouping
        sortSpec.value = SortSpec(field, desc, grouping)
        recompute()
    }

    /** Only meaningful for the override path (Favorites, a drilled-into folder) - the unbounded
     * paged tab keeps its own fixed month grouping (see MediaScreen's PagedRow), so this is never
     * gated behind `mediaOverride == null` the way [setSort] is. */
    fun setGroupSettings(groupBy: GroupBy, groupOrder: GroupOrder) {
        if (groupBy == this.groupBy && groupOrder == this.groupOrder) return
        this.groupBy = groupBy
        this.groupOrder = groupOrder
        cachedSortedPaths = null
        cachedFilteredPaths = null
        collapsedGroupSqlValues.value = emptySet()
        sortSpec.value = SortSpec(sortField, sortDesc, groupBy)
        recompute()
    }

    /** Enables the bounded full-list model used for tag grouping inside a Collection. */
    fun setFullListGrouping(enabled: Boolean) {
        if (enabled == fullListGrouping) return
        fullListGrouping = enabled
        recomputeJob?.cancel()
        if (!enabled && override == null) {
            _state.update {
                it.copy(
                    displayMedia = emptyList(),
                    monthGroups = emptyList(),
                    tagsByPath = emptyMap(),
                    isFullListGroupingLoading = false,
                )
            }
        } else if (enabled && override == null) {
            _state.update { it.copy(isFullListGroupingLoading = true) }
            recompute()
        }
    }

    // The DB query itself (`getActivePathsSorted`) is fast (index-backed, no full sort) but reading
    // and marshaling ~200k full_path Strings out of the cursor still costs real time on-device
    // (measured 1-3.7s on a 202k-item real library, all cursor/JNI overhead - not the query plan).
    // So this must never run synchronously on a tap: kick it off in the background whenever the
    // underlying data/sort could have changed. Cache writes are generation-checked so an in-flight
    // query can never republish a snapshot taken before a refresh.
    @Volatile private var prefetchDeferred: Deferred<List<String>>? = null
    @Volatile private var prefetchGeneration = -1L
    private fun prefetchSortedPathsAsync() {
        val key = sortField to sortDesc
        val generation = RefreshBus.pathGeneration
        if (cachedSortedPaths != null && cachedSortedPathsGeneration == generation && cachedSortedPathsKey == key) return
        prefetchDeferred?.cancel()
        prefetchGeneration = generation
        prefetchDeferred = viewModelScope.async(Dispatchers.IO) {
            viewerPathQueryMutex.withLock {
                if (cachedSortedPathsGeneration == generation && cachedSortedPathsKey == key) {
                    return@withLock cachedSortedPaths ?: emptyList()
                }
                repository.getActivePathsSorted(sortField, sortDesc, groupBy).also { paths ->
                    if (generation == RefreshBus.pathGeneration && key == (sortField to sortDesc)) {
                        cachedSortedPaths = paths
                        cachedSortedPathsKey = key
                        cachedSortedPathsGeneration = generation
                    }
                }
            }
        }
    }

    /** Small, identity-anchored Viewer window; does not materialize the complete library. */
    suspend fun viewerPathWindow(path: String): ViewerPathWindow = withContext(Dispatchers.IO) {
        // Keep a few more neighbours mounted than the minimum edge trigger needs. This makes the
        // first two swipes after opening a photo hit an already resolved path while the window still
        // remains tiny compared with the old full-library snapshot.
        repository.getViewerPathWindow(filterFlow.value, sortField, sortDesc, groupBy, path, radius = 6)
    }

    suspend fun viewerPathChunk(path: String, direction: ViewerLoadDirection, limit: Int): ViewerPathChunk = withContext(Dispatchers.IO) {
        repository.getViewerPathChunk(filterFlow.value, sortField, sortDesc, groupBy, path, direction, limit)
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
    fun setFilter(rating: Int, tagNames: Set<String>?, pathFilter: Set<String>?, excludePaths: Set<String>? = null, minSize: Long = 0, dateRange: Int = 0, typeFilter: Int = 0, tagMatchAll: Boolean = false, additionalPaths: Set<String>? = null, addedSince: Long = 0L, reviewFilter: MediaReviewFilter = MediaReviewFilter.NONE, textQuery: String = "") {
        val next = MediaFilter(
            rating = rating,
            tagNames = tagNames,
            pathFilter = pathFilter,
            excludePaths = excludePaths,
            minSize = minSize,
            dateRange = dateRange,
            typeFilter = typeFilter,
            addedSince = addedSince,
            additionalPaths = additionalPaths,
            tagMatchAll = tagMatchAll,
            reviewFilter = reviewFilter,
            textQuery = textQuery,
        )
        if (next == filterFlow.value) return
        filterFlow.value = next
        _state.update { it.copy(filter = next) }
        if (override != null || (fullListGrouping && override == null)) recompute()
    }

    private fun recompute() {
        val ov = override
        if (ov == null && !fullListGrouping) {
            recomputeJob?.cancel()
            return
        }
        val requestedGroupBy = groupBy
        val requestedSortField = sortField
        val requestedSortDesc = sortDesc
        val requestedFilter = filterFlow.value
        val requestedFullListGrouping = fullListGrouping && ov == null
        recomputeJob?.cancel()
        if (requestedFullListGrouping) _state.update { it.copy(isFullListGroupingLoading = true) }
        recomputeJob = viewModelScope.launch(Dispatchers.Default) {
            // Snapshot the request before leaving the main thread. Rapid sort/group changes cancel
            // the obsolete calculation instead of letting an older, expensive list win the race.
            val display = if (ov != null) {
                applySortFor(applyFilterForOverride(ov, requestedFilter), requestedSortField, requestedSortDesc)
            } else {
                // A Collection's filtered result is loaded as paths first (preserving SQL's exact
                // sort order), then hydrated in bounded chunks. Tag grouping stays client-side
                // because one medium may belong to several tag sections.
                val sortedPaths = withContext(Dispatchers.IO) {
                    repository.getActivePathsSortedFiltered(requestedFilter, requestedSortField, requestedSortDesc)
                }
                val byPath = withContext(Dispatchers.IO) {
                    repository.getMediaByPaths(sortedPaths).associateBy { it.path }
                }
                sortedPaths.mapNotNull { byPath[it] }
            }
            val groups = groupByMonth(display)
            val tags = if (requestedGroupBy == GroupBy.TAG) {
                withContext(Dispatchers.IO) { repository.getTagsForPaths(display.map { it.path }) }
            } else emptyMap()
            _state.update {
                it.copy(
                    displayMedia = display,
                    monthGroups = groups,
                    tagsByPath = tags,
                    isFullListGroupingLoading = if (requestedFullListGrouping) false else it.isFullListGroupingLoading,
                )
            }
        }.also { job ->
            job.invokeOnCompletion { if (recomputeJob === job) recomputeJob = null }
        }
    }

    /**
     * Favorites and other bounded media surfaces already have their source list in memory. Apply
     * the same filter semantics as the SQL-backed Media tab before sorting that list, including
     * additive collection members and the shared tag/review axes.
     */
    private suspend fun applyFilterForOverride(list: List<Medium>, filter: MediaFilter): List<Medium> {
        if (!filter.isActive) return list
        val now = System.currentTimeMillis()
        val cutoff = when (filter.dateRange) {
            1 -> now - 86_400_000L
            2 -> now - 7 * 86_400_000L
            3 -> now - 30 * 86_400_000L
            4 -> now - 365 * 86_400_000L
            else -> 0L
        }
        val tagsByPath = if (filter.tagNames != null) {
            withContext(Dispatchers.IO) { repository.getTagsForPaths(list.map { it.path }) }
        } else emptyMap()
        val reviewByPath = if (filter.reviewFilter == MediaReviewFilter.UNDECIDED) {
            withContext(Dispatchers.IO) { list.associate { it.path to repository.getReviewStatus(it.path) } }
        } else emptyMap()
        fun isUnder(path: String, roots: Set<String>): Boolean = roots.any {
            path.equals(it, ignoreCase = true) || path.startsWith("${it.trimEnd('/')}/", ignoreCase = true)
        }
        fun matchesSmartFilters(item: Medium): Boolean {
            val tags = tagsByPath[item.path].orEmpty()
            return (filter.rating <= 0 || item.rating >= filter.rating) &&
                (filter.minSize <= 0L || item.size >= filter.minSize) &&
                (filter.typeFilter <= 0 || item.type == filter.typeFilter) &&
                (filter.tagNames == null || if (filter.tagMatchAll) filter.tagNames.all { it in tags } else filter.tagNames.any { it in tags }) &&
                (filter.pathFilter == null || isUnder(item.path, filter.pathFilter)) &&
                (filter.excludePaths == null || !isUnder(item.path, filter.excludePaths)) &&
                (filter.dateRange <= 0 || (if (item.taken > 0L) item.taken else item.modified) >= cutoff) &&
                (filter.addedSince <= 0L || (if (item.dateSortKey > 0L) item.dateSortKey else maxOf(item.dateAdded, item.modified)) > filter.addedSince) &&
                (filter.reviewFilter != MediaReviewFilter.UNDECIDED || reviewByPath[item.path] == null)
        }
        val regular = list.filter(::matchesSmartFilters)
        val additional = when (val paths = filter.additionalPaths) {
            null -> emptyList()
            else -> if (paths.isEmpty()) emptyList() else list.filter { isUnder(it.path, paths) && (filter.reviewFilter != MediaReviewFilter.UNDECIDED || reviewByPath[it.path] == null) }
        }
        return (regular + additional).distinctBy { it.path.lowercase(Locale.ROOT) }
    }

    private fun applySortFor(list: List<Medium>, field: SortField, desc: Boolean): List<Medium> {
        if (field == SortField.RATING) {
            // Secondary sort is always modified DESC (newest-changed first) within a rating bucket,
            // regardless of the rating direction - matches the DAO/filtered rating queries (see the
            // note on MediumDao.getMediaPagedByRatingAsc).
            return if (desc) list.sortedWith(compareByDescending<Medium> { it.rating }.thenByDescending { it.modified }.thenBy { it.path.lowercase() })
            else list.sortedWith(compareBy<Medium> { it.rating }.thenByDescending { it.modified }.thenBy { it.path.lowercase() })
        }
        return when (field) {
            SortField.NAME, SortField.RECENT -> if (desc) list.sortedWith(compareByDescending<Medium> { it.name.lowercase() }.thenBy { it.path.lowercase() }) else list.sortedWith(compareBy<Medium> { it.name.lowercase() }.thenBy { it.path.lowercase() })
            // date_sort_key (date_added-preferring) - identical key to the paged/SQL date sort, so an
            // override list (Favorites, a drilled-into folder) orders the same way the main grid does.
            SortField.DATE -> if (desc) list.sortedWith(compareByDescending<Medium> { if (it.dateSortKey > 0) it.dateSortKey else if (it.taken > 0) it.taken else it.modified }.thenBy { it.path.lowercase() }) else list.sortedWith(compareBy<Medium> { if (it.dateSortKey > 0) it.dateSortKey else if (it.taken > 0) it.taken else it.modified }.thenBy { it.path.lowercase() })
            SortField.SIZE -> if (desc) list.sortedWith(compareByDescending<Medium> { it.size }.thenBy { it.path.lowercase() }) else list.sortedWith(compareBy<Medium> { it.size }.thenBy { it.path.lowercase() })
            SortField.RATING -> list
            SortField.COUNT -> if (desc) list.sortedWith(compareByDescending<Medium> { it.name.lowercase() }.thenBy { it.path.lowercase() }) else list.sortedWith(compareBy<Medium> { it.name.lowercase() }.thenBy { it.path.lowercase() })
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
    // The first screenful of a mosaic is special: rendering it as squares and correcting every
    // item's height a moment later makes the whole layout visibly "pop" into place. Keep a small
    // deduplicated preparation lane for that initial viewport, then publish all decoded ratios in
    // one state update before Compose lays out the staggered grid.
    private val preparingInitialMosaicAspects = HashSet<String>()

    init {
        viewModelScope.launch {
            aspectRequests.receiveAsFlow()
                .chunked(20, 200) // Batch of 20 or every 200ms
                .collect { paths ->
                    val updates = withContext(Dispatchers.Default) {
                        paths.distinct().associateWith { repository.decodeImageAspect(it) }
                    }
                    // Copying a growing aspect-ratio map on the main dispatcher showed up during
                    // fast mosaic scrolls. Build the next immutable snapshot off-main; publishing
                    // the already-built reference is then a small StateFlow update.
                    val nextAspectRatios = withContext(Dispatchers.Default) {
                        HashMap(_state.value.aspectRatios).apply { putAll(updates) }.toMap()
                    }
                    _state.update { it.copy(aspectRatios = nextAspectRatios) }
                }
        }
        // The paged path invalidates itself through Room. The bounded Collection grouping model
        // is an in-memory snapshot, so refresh it after tag/rating/file changes while it is active.
        viewModelScope.launch {
            RefreshBus.events.collect {
                if (fullListGrouping && override == null) recompute()
            }
        }
    }

    fun requestAspect(path: String) {
        if (_state.value.aspectRatios.containsKey(path)) return
        aspectRequests.trySend(path)
    }

    /**
     * Prepares the ratios for the initial visible mosaic items as one batch. This deliberately
     * does not decode a whole large library: subsequent items retain the existing lazy queue,
     * while the first viewport never has to morph from a square placeholder into its final shape.
     */
    fun prepareInitialMosaicAspects(paths: Collection<String>) {
        val pathsToDecode = synchronized(preparingInitialMosaicAspects) {
            paths.asSequence()
                .filter { it.isNotBlank() && !_state.value.aspectRatios.containsKey(it) && preparingInitialMosaicAspects.add(it) }
                .toList()
        }
        if (pathsToDecode.isEmpty()) return

        viewModelScope.launch {
            try {
                val updates = withContext(Dispatchers.IO) {
                    pathsToDecode.associateWith { repository.decodeImageAspect(it) }
                }
                _state.update { current ->
                    current.copy(aspectRatios = HashMap(current.aspectRatios).apply { putAll(updates) }.toMap())
                }
            } finally {
                synchronized(preparingInitialMosaicAspects) {
                    preparingInitialMosaicAspects.removeAll(pathsToDecode.toSet())
                }
            }
        }
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

    /** Loads tag markers only for paths currently present in the rendered Paging window. A full
     * SELECT DISTINCT over a large media_tags table used to run at Media-tab startup and competed
     * with the first media cursor, producing CursorWindow NO_MEMORY on memory-constrained devices.
     * [paths] is intentionally bounded by the caller to the visible/loaded window. */
    fun loadTaggedPaths(paths: Collection<String>) {
        // The bounded window loader feeds chip badges for the paged renderer. Full-list tag
        // grouping owns the complete tagsByPath snapshot and must not have it pruned by a paging
        // callback that happens to remain alive underneath that renderer.
        if (groupBy == GroupBy.TAG && (fullListGrouping || override != null)) return
        val requested = paths.filter(String::isNotBlank).distinct()
        if (requested.isEmpty()) return
        viewModelScope.launch {
            val tagsByPath = withContext(Dispatchers.IO) {
                repository.getTagsForPaths(requested).mapValues { (_, tags) -> tags.distinct().sortedBy { it.lowercase(Locale.ROOT) } }
            }
            val tagged = tagsByPath.keys
            _state.update { current ->
                current.copy(
                    taggedPaths = (current.taggedPaths - requested.toSet()) + tagged,
                    // Keep only the latest bounded window plus any full-list grouping snapshot.
                    // The caller limits [requested] to the rendered Paging window, so this does
                    // not turn the unbounded Media tab into a full-library tag load.
                    tagsByPath = (current.tagsByPath - requested.toSet()) + tagsByPath,
                )
            }
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
            val selectedPaths = paths.asSequence().filter(String::isNotBlank).distinct().toList()
            val tagCounts = if (selectedPaths.isEmpty()) {
                emptyMap()
            } else {
                withContext(Dispatchers.IO) {
                    // Read the normalized Room cache in one bounded query instead of opening every
                    // sidecar file. Count tags case-insensitively, while retaining the first
                    // display spelling so the chip matches the user's configured quick tag.
                    val labelsByKey = LinkedHashMap<String, String>()
                    val countsByKey = LinkedHashMap<String, Int>()
                    repository.getTagsForPaths(selectedPaths).values.forEach { tags ->
                        tags.forEach { tag ->
                            val key = tag.lowercase(Locale.ROOT)
                            labelsByKey.putIfAbsent(key, tag)
                            countsByKey[key] = (countsByKey[key] ?: 0) + 1
                        }
                    }
                    countsByKey.mapKeys { (key, _) -> labelsByKey.getValue(key) }
                }
            }
            val common = tagCounts.filterValues { it == selectedPaths.size }.keys
            _state.update { it.copy(selectedCommonTags = common, selectedTagCounts = tagCounts) }
        }
    }

    fun toggleQuickTag(paths: Set<String>, tag: String, removeWhenCommon: Boolean = false) {
        viewModelScope.launch {
            try {
                val successful = mutableSetOf<String>()
                val items = paths.toList().distinct()
                XmpBatch.run(getApplication(), items, onSuccess = { successful += it }) { path ->
                    if (removeWhenCommon) repository.removeTag(path, tag) else repository.addTag(path, tag)
                }
                withContext(Dispatchers.IO) { repository.awaitMetadataCacheSync(successful) }
                if (successful.isNotEmpty()) {
                    if (!removeWhenCommon) getApplication<Application>().config.rememberRecentTag(tag)
                    org.fossify.gallery.helpers.UndoManager.push(
                        org.fossify.gallery.helpers.UndoAction(
                            paths = successful,
                            type = if (removeWhenCommon) org.fossify.gallery.helpers.UndoType.TAG_REMOVE else org.fossify.gallery.helpers.UndoType.TAG_ADD,
                            extra = mapOf("tag" to tag),
                        )
                    )
                }
                loadTaggedPaths(successful)
                loadCommonTags(paths)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                android.util.Log.e("MediaViewModel", "toggleQuickTag failed", e)
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun addTagFor(paths: Collection<String>, tag: String) {
        viewModelScope.launch {
            try {
                val successful = mutableSetOf<String>()
                XmpBatch.run(getApplication(), paths.toList().distinct(), onSuccess = { successful += it }) { repository.addTag(it, tag) }
                withContext(Dispatchers.IO) { repository.awaitMetadataCacheSync(successful) }
                if (successful.isNotEmpty()) {
                    getApplication<Application>().config.rememberRecentTag(tag)
                    org.fossify.gallery.helpers.UndoManager.push(org.fossify.gallery.helpers.UndoAction(successful, org.fossify.gallery.helpers.UndoType.TAG_ADD, mapOf("tag" to tag)))
                }
                loadTaggedPaths(successful)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                android.util.Log.e("MediaViewModel", "addTagFor failed", e)
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun removeTagFor(paths: Collection<String>, tag: String) {
        viewModelScope.launch {
            try {
                val successful = mutableSetOf<String>()
                XmpBatch.run(getApplication(), paths.toList().distinct(), onSuccess = { successful += it }) { repository.removeTag(it, tag) }
                withContext(Dispatchers.IO) { repository.awaitMetadataCacheSync(successful) }
                if (successful.isNotEmpty()) org.fossify.gallery.helpers.UndoManager.push(org.fossify.gallery.helpers.UndoAction(successful, org.fossify.gallery.helpers.UndoType.TAG_REMOVE, mapOf("tag" to tag)))
                loadTaggedPaths(successful)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                android.util.Log.e("MediaViewModel", "removeTagFor failed", e)
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun setRatingFor(paths: Collection<String>, rating: Int) {
        viewModelScope.launch {
            try {
                // Write XMP first and update the DB only for files that actually reached disk. A
                // failed XMP write must never leave a false rating in the queryable library cache.
                val successful = mutableListOf<String>()
                val items = paths.distinct()
                val oldRatings = withContext(Dispatchers.IO) { repository.getRatingsForPaths(items) }
                XmpBatch.run(getApplication(), items) {
                    repository.writeRatingXmp(it, rating).also { ok -> if (ok) successful += it }
                }
                withContext(Dispatchers.IO) {
                    repository.setDbRatingBatch(successful, rating)
                    repository.refreshMetadataCache(successful)
                }
                if (successful.isNotEmpty()) {
                    org.fossify.gallery.helpers.UndoManager.push(
                        org.fossify.gallery.helpers.UndoAction(
                            paths = successful.toSet(),
                            type = org.fossify.gallery.helpers.UndoType.RATING_CHANGE,
                            ratingSnapshots = oldRatings.filterKeys { it in successful },
                        )
                    )
                }
                silentRefresh()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                android.util.Log.e("MediaViewModel", "setRatingFor failed", e)
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    init {
        load()
    }

    fun load() {
        if (loaded) return
        loaded = true
        rescanAndLoad()
    }

    // Manual refresh is deliberately a sync-only operation. The paged grid already reads from the
    // Room table, so reloading the old 20,000-row compatibility snapshot here only duplicated work
    // and made pull-to-refresh look frozen on larger libraries. The caller waits for this job and
    // asks Paging3 to refresh once, after the sync committed, avoiding the old race that caused two
    // page reloads (one before and one after new MediaStore rows were inserted).
    @Volatile private var libraryRefreshJob: Job? = null

    private fun startLibraryRefresh(): Job {
        libraryRefreshJob?.takeIf { it.isActive }?.let { return it }
        val job = viewModelScope.launch(Dispatchers.IO) {
            try {
                val added = repository.syncNewMediaFromStoreCoalesced(source = "manual")
                if (added.isNotEmpty()) {
                    // Notify after the DB commit, never before it. Other tabs can then reload a
                    // consistent snapshot instead of racing the worker that is still inserting rows.
                    RefreshBus.trigger(pathsChanged = true, affectedPaths = added.map { it.path })
                }
                _state.update { it.copy(error = null) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                android.util.Log.e("MediaViewModel", "refresh failed", e)
                _state.update { it.copy(error = e.message ?: "Refresh failed") }
            }
        }
        libraryRefreshJob = job
        job.invokeOnCompletion { if (libraryRefreshJob === job) libraryRefreshJob = null }
        return job
    }

    fun refresh() {
        startLibraryRefresh()
    }

    suspend fun refreshAndWait() {
        startLibraryRefresh().join()
    }

    // NOTE: state.allMedia/displayMedia only feed the override rendering path (Favorites, which
    // refreshes independently via its own RefreshBus subscription and never reads this
    // ViewModel's state) and the initial-load error state (only ever set by rescanAndLoad(),
    // never here) - every grid render (unfiltered and filtered alike) goes through `pagedMedia`
    // instead. This used to also re-fetch and re-sort up to 20,000 rows into `state.allMedia` on
    // every single call - i.e. every RefreshBus tick, which under the ContentObserver fired for
    // every MediaStore write from *any* app - even though nothing actually read the result.
    fun silentRefresh() {
        // The paged grid observes Room invalidations directly. Viewer windows are resolved lazily
        // around the tapped identity, so a metadata refresh must not rebuild a full path snapshot.
    }

    private fun rescanAndLoad() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            var loadError: String? = null
            val media = withContext(Dispatchers.IO) {
                // Repair rows left with zero size/1970 timestamps by a transient MediaStore scan
                // before Paging or Collection grouping reads them. This is metadata-only: valid
                // image/video bytes are never rewritten.
                repository.repairInvalidMediaMetadata()
                try { repository.syncNewMediaFromStoreCoalesced() } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    android.util.Log.e("MediaVMLoad", "syncNewMediaFromStore failed", t)
                    loadError = t.message ?: "Sync failed"
                }
                // The visible grid is Paging-backed. Loading 20,000 complete Medium objects here
                // was dead work left over from the old in-memory grid and, on a large library,
                // created a second large CursorWindow exactly while Paging opened its first one.
                // That memory peak was the source of intermittent "media could not be loaded"
                // errors after a metadata/tag write. A one-row probe is enough to decide whether a
                // bootstrap disk scan is still necessary; the normal non-empty path returns no
                // redundant list at all.
                val dbHasLiveMedia = try { repository.getNewestMedia(1).isNotEmpty() } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    android.util.Log.e("MediaVMLoad", "media probe failed", t)
                    loadError = t.message ?: "Database probe failed"
                    false
                }
                android.util.Log.i("MediaVMLoad", "databaseHasLiveMedia=$dbHasLiveMedia")
                if (dbHasLiveMedia) emptyList() else {
                    val s = try { repository.scanMediaFromDisk() } catch (t: Throwable) {
                        if (t is CancellationException) throw t
                        android.util.Log.e("MediaVMLoad", "scanMediaFromDisk failed", t)
                        loadError = t.message ?: "Media scan failed"
                        emptyList()
                    }
                    android.util.Log.i("MediaVMLoad", "scanMediaFromDisk=${s.size}")
                    s
                }
            }
            android.util.Log.i("MediaVMLoad", "final media=${media.size}")
            recompute()
            _state.update { it.copy(isLoading = false, error = loadError) }
        }
    }

    /** All active paths, unsorted - used for select-all/invert in the unfiltered browse view. */
    suspend fun activePaths(): Set<String> = withContext(Dispatchers.IO) { repository.getActivePaths().toSet() }

    /** Full sorted path list (matching the current sort field/direction) for Viewer swipe-through,
     * independent of how much of the paged grid has loaded so far. Cached - see cachedSortedPaths.
     *
     * Joins an in-flight [prefetchDeferred] instead of firing a second concurrent query when one is
     * already running (e.g. immediately after a sort change, when the user can tap into the Viewer
     * before the warm-up finishes). Two concurrent executions of this ~200k-row query
     * fighting over SQLite's CursorWindow was reliably hanging the Viewer forever on a real ~200k-item
     * library (repeated `CursorWindow: Failed NO_MEMORY` in a tight retry loop, one worker thread
     * pinned at ~96% CPU for minutes) - joining the existing query instead of racing it fixes that. */
    suspend fun activePathsSorted(): List<String> = withContext(Dispatchers.IO) {
        val key = sortField to sortDesc
        val generation = RefreshBus.pathGeneration
        cachedSortedPaths?.takeIf { cachedSortedPathsKey == key && cachedSortedPathsGeneration == generation }?.let { return@withContext it }
        val prefetch = prefetchDeferred?.takeIf { it.isActive && prefetchGeneration == generation }
        if (prefetch != null) prefetch.await()
        else prefetchDeferred?.cancel()
        cachedSortedPaths?.takeIf { cachedSortedPathsKey == key && cachedSortedPathsGeneration == generation }?.let { return@withContext it }
        viewerPathQueryMutex.withLock {
            cachedSortedPaths?.takeIf { cachedSortedPathsKey == key && cachedSortedPathsGeneration == RefreshBus.pathGeneration }?.let { return@withLock it }
            val paths = repository.getActivePathsSorted(sortField, sortDesc, groupBy)
            if (generation == RefreshBus.pathGeneration && key == (sortField to sortDesc)) {
                cachedSortedPaths = paths
                cachedSortedPathsKey = key
                cachedSortedPathsGeneration = generation
            }
            paths
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
        val generation = RefreshBus.pathGeneration
        cachedFilteredPaths?.takeIf { cachedFilteredPathsKey == key && cachedFilteredPathsGeneration == generation }?.let { return@withContext it }
        val prefetch = filteredPrefetchDeferred?.takeIf { it.isActive && filteredPrefetchGeneration == generation }
        if (prefetch != null) prefetch.await()
        else filteredPrefetchDeferred?.cancel()
        cachedFilteredPaths?.takeIf { cachedFilteredPathsKey == key && cachedFilteredPathsGeneration == generation }?.let { return@withContext it }
        viewerPathQueryMutex.withLock {
            cachedFilteredPaths?.takeIf { cachedFilteredPathsKey == key && cachedFilteredPathsGeneration == RefreshBus.pathGeneration }?.let { return@withLock it }
            val paths = repository.getActivePathsSortedFiltered(f, sortField, sortDesc, groupBy)
            if (generation == RefreshBus.pathGeneration && key == Triple(filterFlow.value, sortField, sortDesc)) {
                cachedFilteredPaths = paths
                cachedFilteredPathsKey = key
                cachedFilteredPathsGeneration = generation
            }
            paths
        }
    }

    /**
     * Re-reads the complete viewer path list once, bypassing the session cache. This is used when
     * Paging has already displayed a newly inserted row but the cache still reflects the previous
     * MediaStore/Room snapshot. The tapped path is then resolved by identity, never by its old page
     * position.
     */
    suspend fun activePathsSortedFresh(): List<String> = withContext(Dispatchers.IO) {
        val key = sortField to sortDesc
        val generation = RefreshBus.pathGeneration
        viewerPathQueryMutex.withLock {
            repository.getActivePathsSorted(sortField, sortDesc, groupBy).also {
                if (generation == RefreshBus.pathGeneration && key == (sortField to sortDesc)) {
                    cachedSortedPaths = it
                    cachedSortedPathsKey = key
                    cachedSortedPathsGeneration = generation
                }
            }
        }
    }

    /** Same cache-bypassing lookup as [activePathsSortedFresh], scoped to the active filter. */
    suspend fun activePathsSortedFilteredFresh(): List<String> = withContext(Dispatchers.IO) {
        val f = filterFlow.value
        if (!f.isActive) return@withContext activePathsSortedFresh()
        val key = Triple(f, sortField, sortDesc)
        val generation = RefreshBus.pathGeneration
        viewerPathQueryMutex.withLock {
            repository.getActivePathsSortedFiltered(f, sortField, sortDesc, groupBy).also {
                if (generation == RefreshBus.pathGeneration && key == Triple(filterFlow.value, sortField, sortDesc)) {
                    cachedFilteredPaths = it
                    cachedFilteredPathsKey = key
                    cachedFilteredPathsGeneration = generation
                }
            }
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
                // RefreshBus.pathGeneration invalidates any old Viewer path snapshot. The next
                // Viewer/select-all request resolves a fresh list lazily, so deleting here does not
                // start another full-library cursor while Paging is updating the visible grid.
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
                // The generation key invalidates any old Viewer path snapshot; resolve it lazily on
                // the next Viewer/select-all action instead of competing with Paging here.
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

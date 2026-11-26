package org.fossify.gallery.viewmodels

import android.app.Application
import android.database.SQLException
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.fossify.gallery.R
import org.fossify.gallery.compose.screens.SortField
import org.fossify.gallery.compose.util.XmpBatch
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.GroupBy
import org.fossify.gallery.helpers.GroupOrder
import org.fossify.gallery.helpers.GroupSummary
import org.fossify.gallery.helpers.MediaRepository
import org.fossify.gallery.helpers.RefreshBus
import org.fossify.gallery.helpers.UndoAction
import org.fossify.gallery.helpers.UndoManager
import org.fossify.gallery.helpers.UndoType
import org.fossify.gallery.helpers.ViewerLoadDirection
import org.fossify.gallery.helpers.ViewerPathChunk
import org.fossify.gallery.helpers.ViewerPathWindow
import org.fossify.gallery.models.MediaReviewFilter
import org.fossify.gallery.models.Medium
import java.io.IOException
import java.util.Locale

class MediaViewModel(application: Application) : AndroidViewModel(application) {

    // Keep the first Room cursor deliberately small. PagingConfig otherwise uses three pages for
    // its initial load; on a large library that opens several large CursorWindows at once and can
    // surface as the generic "media could not be loaded" state right after a metadata write.
    private object PagingTuning {
        const val MEDIA_PAGE_SIZE = 30
        const val MEDIA_INITIAL_LOAD_SIZE = 60
        const val MEDIA_PREFETCH_DISTANCE = 15
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
    private var fullListGrouping = false

    @Volatile private var cachedSortedPaths: List<String>? = null
    @Volatile private var cachedSortedPathsKey: Pair<SortField, Boolean>? = null
    @Volatile private var cachedSortedPathsGeneration = -1L

    @Volatile private var cachedFilteredPaths: List<String>? = null
    @Volatile private var cachedFilteredPathsKey: Triple<MediaFilter, SortField, Boolean>? = null
    @Volatile private var cachedFilteredPathsGeneration = -1L
    private val viewerPathQueryMutex = Mutex()

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
    val pagedMedia: Flow<PagingData<Medium>> =
        combine(sortSpec, filterFlow, collapsedGroupSqlValues) { spec, f, collapsed ->
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
                    ),
                ) {
                    val effectiveGroup = spec.groupBy.takeUnless { it == GroupBy.TAG } ?: GroupBy.NONE
                    val excluded = if (effectiveGroup == GroupBy.NONE) emptySet() else collapsed
                    if (f.isActive) {
                        repository.getMediaPagedFiltered(f, spec.field, spec.desc, effectiveGroup, excluded)
                    } else {
                        repository.getMediaPaged(spec.field, spec.desc, effectiveGroup, excluded)
                    }
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
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS), emptyList())

    /** True total for the active filter, shown in [FilterBreadcrumbs]'s result-count label. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val filteredResultCount: StateFlow<Int?> =
        combine(filterFlow, RefreshBus.events.onStart { emit(Unit) }) { f, _ -> f }
            .flatMapLatest { f ->
                if (f.isActive) flow { emit(repository.getFilteredMediaCount(f)) } else flowOf<Int?>(null)
            }
            .flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS), null)

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

    /** Small, identity-anchored Viewer window; does not materialize the complete library. */
    suspend fun viewerPathWindow(path: String): ViewerPathWindow = withContext(Dispatchers.IO) {
        repository.getViewerPathWindow(
            filterFlow.value,
            sortField,
            sortDesc,
            groupBy,
            path,
            radius = VIEWER_WINDOW_RADIUS,
        )
    }

    suspend fun viewerPathChunk(
        path: String,
        direction: ViewerLoadDirection,
        limit: Int,
    ): ViewerPathChunk = withContext(Dispatchers.IO) {
        repository.getViewerPathChunk(filterFlow.value, sortField, sortDesc, groupBy, path, direction, limit)
    }

    fun setOverride(media: List<Medium>?) {
        if (media == override) return
        override = media
        recompute()
    }

    fun setFilter(
        rating: Int,
        tagNames: Set<String>?,
        pathFilter: Set<String>?,
        excludePaths: Set<String>? = null,
        minSize: Long = 0,
        dateRange: Int = 0,
        typeFilter: Int = 0,
        tagMatchAll: Boolean = false,
        additionalPaths: Set<String>? = null,
        addedSince: Long = 0L,
        reviewFilter: MediaReviewFilter = MediaReviewFilter.NONE,
        textQuery: String = "",
    ) {
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
            val display = if (ov != null) {
                val tagsByPath = if (requestedFilter.tagNames != null) {
                    withContext(Dispatchers.IO) { repository.getTagsForPaths(ov.map { it.path }) }
                } else {
                    emptyMap()
                }
                val reviewByPath = if (requestedFilter.reviewFilter == MediaReviewFilter.UNDECIDED) {
                    withContext(Dispatchers.IO) {
                        ov.associate { it.path to repository.getReviewStatus(it.path) }
                    }
                } else {
                    emptyMap()
                }
                sortMediaList(
                    list = filterOverrideMedia(ov, requestedFilter, tagsByPath, reviewByPath),
                    field = requestedSortField,
                    desc = requestedSortDesc,
                )
            } else {
                val sortedPaths = withContext(Dispatchers.IO) {
                    repository.getActivePathsSortedFiltered(
                        requestedFilter,
                        requestedSortField,
                        requestedSortDesc,
                    )
                }
                val byPath = withContext(Dispatchers.IO) {
                    repository.getMediaByPaths(sortedPaths).associateBy { it.path }
                }
                sortedPaths.mapNotNull { byPath[it] }
            }
            val groups = groupMediaByMonth(display)
            val tags = if (requestedGroupBy == GroupBy.TAG) {
                withContext(Dispatchers.IO) { repository.getTagsForPaths(display.map { it.path }) }
            } else {
                emptyMap()
            }
            _state.update {
                it.copy(
                    displayMedia = display,
                    monthGroups = groups,
                    tagsByPath = tags,
                    isFullListGroupingLoading = if (requestedFullListGrouping) {
                        false
                    } else {
                        it.isFullListGroupingLoading
                    },
                )
            }
        }.also { job ->
            job.invokeOnCompletion { if (recomputeJob === job) recomputeJob = null }
        }
    }

    private val aspectRequests = Channel<String>(Channel.UNLIMITED)
    private val preparingInitialMosaicAspects = HashSet<String>()

    init {
        viewModelScope.launch {
            aspectRequests.receiveAsFlow()
                .chunkedByCountOrTimeout(ASPECT_BATCH_SIZE, ASPECT_BATCH_TIMEOUT_MS)
                .collect { paths ->
                    val updates = withContext(Dispatchers.Default) {
                        paths.distinct().associateWith { repository.decodeImageAspect(it) }
                    }
                    val nextAspectRatios = withContext(Dispatchers.Default) {
                        HashMap(_state.value.aspectRatios).apply { putAll(updates) }.toMap()
                    }
                    _state.update { it.copy(aspectRatios = nextAspectRatios) }
                }
        }
        viewModelScope.launch {
            RefreshBus.events.collect {
                if (fullListGrouping && override == null) recompute()
            }
        }
        load()
    }

    fun requestAspect(path: String) {
        if (_state.value.aspectRatios.containsKey(path)) return
        aspectRequests.trySend(path)
    }

    fun prepareInitialMosaicAspects(paths: Collection<String>) {
        val pathsToDecode = synchronized(preparingInitialMosaicAspects) {
            paths.asSequence()
                .filter {
                    it.isNotBlank() &&
                        !_state.value.aspectRatios.containsKey(it) &&
                        preparingInitialMosaicAspects.add(it)
                }
                .toList()
        }
        if (pathsToDecode.isEmpty()) return

        viewModelScope.launch {
            try {
                val updates = withContext(Dispatchers.IO) {
                    pathsToDecode.associateWith { repository.decodeImageAspect(it) }
                }
                _state.update { current ->
                    val merged = HashMap(current.aspectRatios).apply { putAll(updates) }.toMap()
                    current.copy(aspectRatios = merged)
                }
            } finally {
                synchronized(preparingInitialMosaicAspects) {
                    preparingInitialMosaicAspects.removeAll(pathsToDecode.toSet())
                }
            }
        }
    }

    fun loadTaggedPaths(paths: Collection<String>) {
        if (groupBy == GroupBy.TAG && (fullListGrouping || override != null)) return
        val requested = paths.filter(String::isNotBlank).distinct()
        if (requested.isEmpty()) return
        viewModelScope.launch {
            val tagsByPath = withContext(Dispatchers.IO) {
                repository.getTagsForPaths(requested).mapValues { (_, tags) ->
                    tags.distinct().sortedBy { it.lowercase(Locale.ROOT) }
                }
            }
            val tagged = tagsByPath.keys
            _state.update { current ->
                current.copy(
                    taggedPaths = (current.taggedPaths - requested.toSet()) + tagged,
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
        launchMutation("toggleQuickTag") {
            val successful = mutableSetOf<String>()
            val items = paths.toList().distinct()
            XmpBatch.run(getApplication(), items, onSuccess = { successful += it }) { path ->
                if (removeWhenCommon) repository.removeTag(path, tag) else repository.addTag(path, tag)
            }
            withContext(Dispatchers.IO) { repository.awaitMetadataCacheSync(successful) }
            if (successful.isNotEmpty()) {
                if (!removeWhenCommon) getApplication<Application>().config.rememberRecentTag(tag)
                val undoType = if (removeWhenCommon) UndoType.TAG_REMOVE else UndoType.TAG_ADD
                UndoManager.push(UndoAction(paths = successful, type = undoType, extra = mapOf("tag" to tag)))
            }
            loadTaggedPaths(successful)
            loadCommonTags(paths)
        }
    }

    fun addTagFor(paths: Collection<String>, tag: String) {
        launchMutation("addTagFor") {
            val successful = mutableSetOf<String>()
            XmpBatch.run(getApplication(), paths.toList().distinct(), onSuccess = { successful += it }) {
                repository.addTag(it, tag)
            }
            withContext(Dispatchers.IO) { repository.awaitMetadataCacheSync(successful) }
            if (successful.isNotEmpty()) {
                getApplication<Application>().config.rememberRecentTag(tag)
                UndoManager.push(UndoAction(successful, UndoType.TAG_ADD, mapOf("tag" to tag)))
            }
            loadTaggedPaths(successful)
        }
    }

    fun removeTagFor(paths: Collection<String>, tag: String) {
        launchMutation("removeTagFor") {
            val successful = mutableSetOf<String>()
            XmpBatch.run(getApplication(), paths.toList().distinct(), onSuccess = { successful += it }) {
                repository.removeTag(it, tag)
            }
            withContext(Dispatchers.IO) { repository.awaitMetadataCacheSync(successful) }
            if (successful.isNotEmpty()) {
                UndoManager.push(UndoAction(successful, UndoType.TAG_REMOVE, mapOf("tag" to tag)))
            }
            loadTaggedPaths(successful)
        }
    }

    fun setRatingFor(paths: Collection<String>, rating: Int) {
        launchMutation("setRatingFor") {
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
                UndoManager.push(
                    UndoAction(
                        paths = successful.toSet(),
                        type = UndoType.RATING_CHANGE,
                        ratingSnapshots = oldRatings.filterKeys { it in successful },
                    ),
                )
            }
            silentRefresh()
        }
    }

    fun load() {
        if (loaded) return
        loaded = true
        rescanAndLoad()
    }

    @Volatile private var libraryRefreshJob: Job? = null

    private fun startLibraryRefresh(): Job {
        libraryRefreshJob?.takeIf { it.isActive }?.let { return it }
        val job = viewModelScope.launch(Dispatchers.IO) {
            runSafeStep(
                tag = TAG,
                label = "refresh",
                defaultMessage = "Refresh failed",
                onError = { msg -> _state.update { it.copy(error = msg) } },
            ) {
                val added = repository.syncNewMediaFromStoreCoalesced(source = "manual")
                if (added.isNotEmpty()) {
                    RefreshBus.trigger(pathsChanged = true, affectedPaths = added.map { it.path })
                }
                _state.update { it.copy(error = null) }
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

    fun silentRefresh() {
        // The paged grid observes Room invalidations directly; Viewer windows resolve lazily.
    }

    private fun rescanAndLoad() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            var loadError: String? = null
            val media = withContext(Dispatchers.IO) {
                repository.repairInvalidMediaMetadata()
                runSafeStep(LOAD_TAG, "syncNewMediaFromStore", "Sync failed", { loadError = it }) {
                    repository.syncNewMediaFromStoreCoalesced()
                }
                val dbHasLiveMedia = runSafeStep(
                    tag = LOAD_TAG,
                    label = "media probe",
                    defaultMessage = "Database probe failed",
                    onError = { loadError = it },
                ) {
                    repository.getNewestMedia(1).isNotEmpty()
                } ?: false
                Log.i(LOAD_TAG, "databaseHasLiveMedia=$dbHasLiveMedia")
                if (dbHasLiveMedia) {
                    emptyList()
                } else {
                    val scanned = runSafeStep(
                        tag = LOAD_TAG,
                        label = "scanMediaFromDisk",
                        defaultMessage = "Media scan failed",
                        onError = { loadError = it },
                    ) {
                        repository.scanMediaFromDisk()
                    }.orEmpty()
                    Log.i(LOAD_TAG, "scanMediaFromDisk=${scanned.size}")
                    scanned
                }
            }
            Log.i(LOAD_TAG, "final media=${media.size}")
            recompute()
            _state.update { it.copy(isLoading = false, error = loadError) }
        }
    }

    /** All active paths, unsorted - used for select-all/invert in the unfiltered browse view. */
    suspend fun activePaths(): Set<String> = withContext(Dispatchers.IO) {
        repository.getActivePaths().toSet()
    }

    suspend fun activePathsSorted(): List<String> = withContext(Dispatchers.IO) {
        val key = sortField to sortDesc
        val generation = RefreshBus.pathGeneration
        cachedSortedPaths
            ?.takeIf { cachedSortedPathsKey == key && cachedSortedPathsGeneration == generation }
            ?.let { return@withContext it }
        viewerPathQueryMutex.withLock {
            val latestGen = RefreshBus.pathGeneration
            cachedSortedPaths
                ?.takeIf { cachedSortedPathsKey == key && cachedSortedPathsGeneration == latestGen }
                ?.let { return@withLock it }
            val paths = repository.getActivePathsSorted(sortField, sortDesc, groupBy)
            if (generation == RefreshBus.pathGeneration && key == (sortField to sortDesc)) {
                cachedSortedPaths = paths
                cachedSortedPathsKey = key
                cachedSortedPathsGeneration = generation
            }
            paths
        }
    }

    suspend fun activePathsSortedFiltered(): List<String> = withContext(Dispatchers.IO) {
        val f = filterFlow.value
        if (!f.isActive) return@withContext activePathsSorted()
        val key = Triple(f, sortField, sortDesc)
        val generation = RefreshBus.pathGeneration
        cachedFilteredPaths
            ?.takeIf { cachedFilteredPathsKey == key && cachedFilteredPathsGeneration == generation }
            ?.let { return@withContext it }
        viewerPathQueryMutex.withLock {
            val latestGen = RefreshBus.pathGeneration
            cachedFilteredPaths
                ?.takeIf { cachedFilteredPathsKey == key && cachedFilteredPathsGeneration == latestGen }
                ?.let { return@withLock it }
            val paths = repository.getActivePathsSortedFiltered(f, sortField, sortDesc, groupBy)
            if (generation == RefreshBus.pathGeneration && key == Triple(filterFlow.value, sortField, sortDesc)) {
                cachedFilteredPaths = paths
                cachedFilteredPathsKey = key
                cachedFilteredPathsGeneration = generation
            }
            paths
        }
    }

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

    suspend fun activePathsSortedFilteredFresh(): List<String> = withContext(Dispatchers.IO) {
        val f = filterFlow.value
        if (!f.isActive) return@withContext activePathsSortedFresh()
        val key = Triple(f, sortField, sortDesc)
        val generation = RefreshBus.pathGeneration
        viewerPathQueryMutex.withLock {
            repository.getActivePathsSortedFiltered(f, sortField, sortDesc, groupBy).also {
                val currentKey = Triple(filterFlow.value, sortField, sortDesc)
                if (generation == RefreshBus.pathGeneration && key == currentKey) {
                    cachedFilteredPaths = it
                    cachedFilteredPathsKey = key
                    cachedFilteredPathsGeneration = generation
                }
            }
        }
    }

    fun deletePaths(paths: Set<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            runSafeStep(TAG, "deletePaths", "Delete failed", { msg -> _state.update { it.copy(error = msg) } }) {
                val deleted = paths.filterTo(mutableSetOf()) { p -> repository.deleteMedium(p) }
                _state.update { s -> s.copy(allMedia = s.allMedia.filter { it.path !in deleted }) }
                recompute()
            }
        }
    }

    fun softDeletePaths(paths: Set<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            runSafeStep(
                tag = TAG,
                label = "softDeletePaths",
                defaultMessage = "Move to bin failed",
                onError = { msg -> _state.update { it.copy(error = msg) } },
            ) {
                repository.moveToRecycleBinBatch(paths)
                _state.update { s -> s.copy(allMedia = s.allMedia.filter { it.path !in paths }) }
                recompute()
            }
        }
    }

    fun undoDeletePaths(paths: Set<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            runSafeStep(TAG, "undoDeletePaths", "Restore failed", {}) {
                repository.restoreFromRecycleBinBatch(paths)
                refresh()
            }
        }
    }

    fun saveScrollPosition(index: Int, offset: Int) {
        _state.update { it.copy(scrollIndex = index, scrollOffset = offset) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    private fun launchMutation(label: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            runSafeStep(TAG, label, "$label failed", { msg -> _state.update { it.copy(error = msg) } }) {
                block()
            }
        }
    }

    private inline fun <T> runSafeStep(
        tag: String,
        label: String,
        defaultMessage: String,
        onError: (String) -> Unit,
        block: () -> T,
    ): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        Log.e(tag, "$label failed", e)
        onError(e.message ?: defaultMessage)
        null
    } catch (e: SecurityException) {
        Log.e(tag, "$label failed", e)
        onError(e.message ?: defaultMessage)
        null
    } catch (e: SQLException) {
        Log.e(tag, "$label failed", e)
        onError(e.message ?: defaultMessage)
        null
    } catch (e: IllegalStateException) {
        Log.e(tag, "$label failed", e)
        onError(e.message ?: defaultMessage)
        null
    } catch (e: IllegalArgumentException) {
        Log.e(tag, "$label failed", e)
        onError(e.message ?: defaultMessage)
        null
    }

    companion object {
        private const val TAG = "MediaViewModel"
        private const val LOAD_TAG = "MediaVMLoad"

        fun monthLabelFor(m: Medium): String = formatMediaMonthLabel(m)
    }
}

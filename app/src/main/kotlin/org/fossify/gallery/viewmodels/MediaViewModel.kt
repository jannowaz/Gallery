package org.fossify.gallery.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.gallery.compose.screens.SortField
import org.fossify.gallery.helpers.MediaRepository
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
) {
    val isActive: Boolean get() = rating > 0 || tagNames != null || pathFilter != null || excludePaths != null || minSize > 0 || dateRange > 0
}

data class MediaUiState(
    val allMedia: List<Medium> = emptyList(),
    /** Filtered + sorted media ready to render. The screen reads this; it never filters/sorts itself. */
    val displayMedia: List<Medium> = emptyList(),
    val monthGroups: List<MonthGroup> = emptyList(),
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

    private fun applySort(list: List<Medium>): List<Medium> {
        if (sortField == SortField.RATING) {
            return if (sortDesc) list.sortedWith(compareByDescending<Medium> { it.rating }.thenByDescending { it.modified })
            else list.sortedWith(compareBy<Medium> { it.rating }.thenBy { it.modified })
        }
        val sorted = when (sortField) {
            SortField.NAME -> list.sortedBy { it.name.lowercase() }
            SortField.DATE -> list.sortedBy { it.modified }
            SortField.SIZE -> list.sortedBy { it.size }
            SortField.RATING -> list
        }
        return if (sortDesc) sorted.reversed() else sorted
    }

    fun setSort(field: SortField, desc: Boolean) {
        if (field == sortField && desc == sortDesc) return
        sortField = field
        sortDesc = desc
        sortSpec.value = SortSpec(field, desc)
        recompute()
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
    fun setFilter(rating: Int, tagNames: Set<String>?, pathFilter: Set<String>?, excludePaths: Set<String>? = null, minSize: Long = 0, dateRange: Int = 0) {
        val next = MediaFilter(rating, tagNames, pathFilter, excludePaths, minSize, dateRange)
        if (next == filterFlow.value) return
        filterFlow.value = next
        _state.update { it.copy(filter = next) }
    }

    private fun recompute() {
        val ov = override ?: return
        viewModelScope.launch {
            val display = withContext(Dispatchers.Default) { applySort(ov) }
            val groups = withContext(Dispatchers.Default) { groupByMonth(display) }
            _state.update { it.copy(displayMedia = display, monthGroups = groups) }
        }
    }

    /** Lazily decodes and caches the aspect ratio of an image (mosaic layout). Decoding runs in the repo. */
    private val aspectRequests = MutableSharedFlow<String>(extraBufferCapacity = 64)

    init {
        viewModelScope.launch {
            aspectRequests
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
        aspectRequests.tryEmit(path)
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
        viewModelScope.launch(Dispatchers.IO) { paths.forEach { repository.addTag(it, tag) }; loadTaggedPaths() }
    }

    fun removeTagFor(paths: Collection<String>, tag: String) {
        viewModelScope.launch(Dispatchers.IO) { paths.forEach { repository.removeTag(it, tag) }; loadTaggedPaths() }
    }

    fun setRatingFor(paths: Collection<String>, rating: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // One batch DB write (single InvalidationTracker notification) + per-file XMP writes
                // (no DB write each - see MediaRepository.writeRatingXmp).
                repository.setDbRatingBatch(paths, rating)
                paths.forEach { repository.writeRatingXmp(it, rating) }
                silentRefresh()
            } catch (e: Exception) {
                android.util.Log.e("MediaViewModel", "setRatingFor failed", e)
            }
        }
    }

    init {
        load()
        loadTaggedPaths()
    }

    fun load() {
        if (loaded) return
        loaded = true
        rescanAndLoad()
    }

    fun refresh() {
        loaded = false
        rescanAndLoad()
    }

    // NOTE: state.allMedia/displayMedia below only feed the override rendering path (Favorites) and
    // the initial-load error state - every grid render (unfiltered and filtered alike) goes through
    // `pagedMedia` instead and doesn't depend on this fetch or its 20,000-item cap.
    fun silentRefresh() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.syncNewMediaFromStore()
                val media = try { repository.getNewestMedia(20000).sortedByDescending { it.modified } } catch (e: Exception) {
                    android.util.Log.e("MediaViewModel", "silentRefresh getNewestMedia failed, falling back to disk scan", e)
                    repository.scanMediaFromDisk()
                }
                _state.update { it.copy(allMedia = applySort(media)) }
                recompute()
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
     * independent of how much of the paged grid has loaded so far. */
    suspend fun activePathsSorted(): List<String> = withContext(Dispatchers.IO) { repository.getActivePathsSorted(sortField, sortDesc) }

    /** Same as [activePathsSorted] but scoped to the currently active filter - used for Viewer
     * swipe-through and select-all/invert while a filter is applied, so both cover the complete
     * filtered set rather than just what's paged into the grid so far. */
    suspend fun activePathsSortedFiltered(): List<String> = withContext(Dispatchers.IO) {
        val f = filterFlow.value
        if (f.isActive) repository.getActivePathsSortedFiltered(f, sortField, sortDesc) else repository.getActivePathsSorted(sortField, sortDesc)
    }

    fun deletePaths(paths: Set<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                paths.forEach { p -> repository.deleteMedium(p) }
                _state.update { s -> s.copy(allMedia = s.allMedia.filter { it.path !in paths }) }
                recompute()
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
         * Allocates its own SimpleDateFormat per call - it's called from both a background dispatcher
         * (recompute's groupByMonth) and Compose's main thread (paged header scan), and SimpleDateFormat
         * isn't thread-safe to share across them. */
        fun monthLabelFor(m: Medium): String {
            val d = if (m.taken > 0) Date(m.taken) else Date(m.modified)
            return SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(d).replaceFirstChar { it.uppercase() }
        }
    }
}

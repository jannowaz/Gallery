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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.gallery.compose.screens.SortField
import org.fossify.gallery.helpers.MediaRepository
import org.fossify.gallery.helpers.VIDEO_EXTENSIONS
import org.fossify.gallery.models.Medium
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MonthGroup(val label: String, val items: List<Medium>)

data class SortSpec(val field: SortField, val desc: Boolean)

/** Active filtering for the media grid. Owned by the ViewModel, never resolved in composition. */
data class MediaFilter(
    val rating: Int = 0,
    val tagPaths: Set<String>? = null,
    val pathFilter: Set<String>? = null,
    val minSize: Long = 0L,
    val dateRange: Int = 0, // 0: All, 1: Today, 2: 7d, 3: 30d, 4: 1y
) {
    val isActive: Boolean get() = rating > 0 || tagPaths != null || pathFilter != null || minSize > 0 || dateRange > 0
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

    // Filter / display pipeline — all owned by the ViewModel.
    private var override: List<Medium>? = null
    private var filter = MediaFilter()
    private var ratingDbCache: List<Medium>? = null
    private var tagDbCache: List<Medium>? = null
    private var pathDbCache: List<Medium>? = null

    // Unfiltered, non-override browse list. Backed by Room's PagingSource + InvalidationTracker, so
    // it stays fresh across rename/rating/delete/sync without any manual refresh call, and isn't
    // capped like the legacy filtered/override path below.
    private val sortSpec = MutableStateFlow(SortSpec(sortField, sortDesc))

    @OptIn(ExperimentalCoroutinesApi::class)
    val pagedMedia: Flow<PagingData<Medium>> = sortSpec
        .flatMapLatest { spec ->
            Pager(PagingConfig(pageSize = 60, prefetchDistance = 40, enablePlaceholders = false)) {
                repository.getMediaPaged(spec.field, spec.desc)
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

    /** Drives the display list from an external source (e.g. a folder's media) instead of the scan. */
    fun setOverride(media: List<Medium>?) {
        if (media == override) return
        override = media
        recompute()
    }

    /** Resolves DB-backed filters off the main thread, then rebuilds the display list. */
    fun setFilter(rating: Int, tagPaths: Set<String>?, pathFilter: Set<String>?, minSize: Long = 0, dateRange: Int = 0) {
        val next = MediaFilter(rating, tagPaths, pathFilter, minSize, dateRange)
        if (next == filter) return
        filter = next
        _state.update { it.copy(filter = next) }
        // Narrow instantly using the already-loaded media (clear stale DB caches first), then refine
        // once the DB-backed results are resolved off the main thread.
        ratingDbCache = null; tagDbCache = null; pathDbCache = null
        recompute()
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                ratingDbCache = if (rating > 0) repository.getByMinRating(rating) else null
                tagDbCache = if (tagPaths != null) repository.getMediaByPaths(tagPaths.toList()) else null
                pathDbCache = if (pathFilter != null) computePathFallback(pathFilter) else null
            }
            recompute()
        }
    }

    private fun computePathFallback(pathFilter: Set<String>): List<Medium>? = try {
        val dirs = pathFilter.filter { File(it).isDirectory }.toSet()
        repository.getNewestMedia(5000)
            .filter { p -> (pathFilter + dirs).any { p.path.startsWith("$it/") || p.path == it } }
            .take(2000)
    } catch (_: Exception) { null }

    private fun recompute() {
        val base = override ?: _state.value.allMedia
        val f = filter
        viewModelScope.launch {
            val display = withContext(Dispatchers.Default) {
                var m = base
                if (f.rating > 0) {
                    val db = ratingDbCache
                    m = if (!db.isNullOrEmpty()) db else m.filter { it.rating >= f.rating }
                }
                if (f.tagPaths != null) {
                    val tagged = tagDbCache
                    if (!tagged.isNullOrEmpty()) {
                        val taggedPaths = tagged.map { it.path }.toSet()
                        m = m.filter { it.path in taggedPaths }
                        if (m.isEmpty()) m = tagged
                    } else {
                        m = m.filter { it.path in f.tagPaths }
                        if (m.isEmpty()) m = mediaFromDisk(f.tagPaths)
                    }
                }
                if (f.pathFilter != null) {
                    val dirs = f.pathFilter.filter { File(it).isDirectory }.toSet()
                    val filtered = m.filter { p -> p.path in f.pathFilter || dirs.any { p.path.startsWith("$it/") } }
                    val fb = pathDbCache
                    m = if (fb != null && fb.size > filtered.size) fb else filtered
                }
                if (f.minSize > 0) {
                    m = m.filter { it.size >= f.minSize }
                }
                if (f.dateRange > 0) {
                    val cutoff = when (f.dateRange) {
                        1 -> System.currentTimeMillis() - 86400000L
                        2 -> System.currentTimeMillis() - 7 * 86400000L
                        3 -> System.currentTimeMillis() - 30 * 86400000L
                        4 -> System.currentTimeMillis() - 365 * 86400000L
                        else -> 0L
                    }
                    m = m.filter { maxOf(it.taken, it.modified) >= cutoff }
                }
                applySort(m)
            }
            val groups = withContext(Dispatchers.Default) { groupByMonth(display) }
            _state.update { it.copy(displayMedia = display, monthGroups = groups) }
        }
    }

    private fun mediaFromDisk(paths: Set<String>): List<Medium> = paths.mapNotNull {
        val file = File(it)
        if (!file.exists()) return@mapNotNull null
        val type = if (VIDEO_EXTENSIONS.any { e -> it.endsWith(e, true) }) 2 else 1
        Medium(null, file.name, file.absolutePath, file.parent ?: "", file.lastModified(), file.lastModified(), file.length(), type, 0, false, 0L, 0L, 0)
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

    // NOTE: state.allMedia/displayMedia below only feed the legacy filtered/override rendering path
    // and the filter's instant-narrow fallback (see setFilter) - the unfiltered/no-override grid
    // renders from `pagedMedia` instead and doesn't depend on this fetch or its 20,000-item cap.
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

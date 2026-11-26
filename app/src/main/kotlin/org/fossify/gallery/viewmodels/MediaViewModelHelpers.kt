package org.fossify.gallery.viewmodels

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.fossify.gallery.compose.screens.SortField
import org.fossify.gallery.helpers.GroupBy
import org.fossify.gallery.models.MediaReviewFilter
import org.fossify.gallery.models.MediaReviewStatus
import org.fossify.gallery.models.Medium
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal const val SUBSCRIPTION_TIMEOUT_MS = 5_000L
internal const val VIEWER_WINDOW_RADIUS = 6
internal const val ASPECT_BATCH_SIZE = 20
internal const val ASPECT_BATCH_TIMEOUT_MS = 200L

private const val DATE_RANGE_TODAY = 1
private const val DATE_RANGE_WEEK = 2
private const val DATE_RANGE_MONTH = 3
private const val DATE_RANGE_YEAR = 4
private const val MILLIS_PER_DAY = 86_400_000L
private const val DAYS_PER_WEEK = 7L
private const val DAYS_PER_MONTH = 30L
private const val DAYS_PER_YEAR = 365L

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
    val isActive: Boolean
        get() = rating > 0 ||
            tagNames != null ||
            pathFilter != null ||
            excludePaths != null ||
            minSize > 0 ||
            dateRange > 0 ||
            typeFilter > 0 ||
            addedSince > 0L ||
            additionalPaths != null ||
            reviewFilter != MediaReviewFilter.NONE ||
            textQuery.isNotBlank()
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

/** Shared with MediaScreen's paged-grid header scan so both label the same item identically. */
private val monthLabelFormat: ThreadLocal<SimpleDateFormat> =
    ThreadLocal.withInitial { SimpleDateFormat("MMMM yyyy", Locale.getDefault()) }

internal fun dateRangeCutoffMs(dateRange: Int, nowMs: Long = System.currentTimeMillis()): Long =
    when (dateRange) {
        DATE_RANGE_TODAY -> nowMs - MILLIS_PER_DAY
        DATE_RANGE_WEEK -> nowMs - DAYS_PER_WEEK * MILLIS_PER_DAY
        DATE_RANGE_MONTH -> nowMs - DAYS_PER_MONTH * MILLIS_PER_DAY
        DATE_RANGE_YEAR -> nowMs - DAYS_PER_YEAR * MILLIS_PER_DAY
        else -> 0L
    }

internal fun isPathUnderRoots(path: String, roots: Set<String>): Boolean = roots.any { root ->
    path.equals(root, ignoreCase = true) ||
        path.startsWith("${root.trimEnd('/')}/", ignoreCase = true)
}

internal fun mediaDateSortKey(medium: Medium): Long = when {
    medium.dateSortKey > 0L -> medium.dateSortKey
    medium.taken > 0L -> medium.taken
    else -> medium.modified
}

internal fun filterOverrideMedia(
    list: List<Medium>,
    filter: MediaFilter,
    tagsByPath: Map<String, List<String>> = emptyMap(),
    reviewByPath: Map<String, MediaReviewStatus?> = emptyMap(),
    nowMs: Long = System.currentTimeMillis(),
): List<Medium> {
    if (!filter.isActive) return list
    val cutoff = dateRangeCutoffMs(filter.dateRange, nowMs)
    val regular = list.filter { item ->
        matchesOverrideSmartFilter(item, filter, tagsByPath, reviewByPath, cutoff)
    }
    val extraPaths = filter.additionalPaths
    val additional = if (extraPaths.isNullOrEmpty()) {
        emptyList()
    } else {
        val undecidedOnly = filter.reviewFilter == MediaReviewFilter.UNDECIDED
        list.filter { item ->
            isPathUnderRoots(item.path, extraPaths) &&
                (!undecidedOnly || reviewByPath[item.path] == null)
        }
    }
    return (regular + additional).distinctBy { it.path.lowercase(Locale.ROOT) }
}

private fun matchesOverrideSmartFilter(
    item: Medium,
    filter: MediaFilter,
    tagsByPath: Map<String, List<String>>,
    reviewByPath: Map<String, MediaReviewStatus?>,
    cutoff: Long,
): Boolean {
    val basicMatch = (filter.rating <= 0 || item.rating >= filter.rating) &&
        (filter.minSize <= 0L || item.size >= filter.minSize) &&
        (filter.typeFilter <= 0 || item.type == filter.typeFilter)
    val pathMatch = (filter.pathFilter == null || isPathUnderRoots(item.path, filter.pathFilter)) &&
        (filter.excludePaths == null || !isPathUnderRoots(item.path, filter.excludePaths))
    val reviewMatch = filter.reviewFilter != MediaReviewFilter.UNDECIDED ||
        reviewByPath[item.path] == null
    return basicMatch &&
        pathMatch &&
        reviewMatch &&
        matchesTagAndTimeFilter(item, filter, tagsByPath[item.path].orEmpty(), cutoff)
}

private fun matchesTagAndTimeFilter(
    item: Medium,
    filter: MediaFilter,
    tags: List<String>,
    cutoff: Long,
): Boolean {
    val requiredTags = filter.tagNames
    val tagMatch = requiredTags == null || if (filter.tagMatchAll) {
        requiredTags.all { it in tags }
    } else {
        requiredTags.any { it in tags }
    }
    val itemTime = if (item.taken > 0L) item.taken else item.modified
    val addedTime = if (item.dateSortKey > 0L) {
        item.dateSortKey
    } else {
        maxOf(item.dateAdded, item.modified)
    }
    val timeMatch = (filter.dateRange <= 0 || itemTime >= cutoff) &&
        (filter.addedSince <= 0L || addedTime > filter.addedSince)
    return tagMatch && timeMatch
}

internal fun sortMediaList(list: List<Medium>, field: SortField, desc: Boolean): List<Medium> {
    val comparator: Comparator<Medium> = when (field) {
        SortField.RATING -> {
            // Secondary sort is always modified DESC within a rating bucket, regardless of direction.
            val primary = if (desc) {
                compareByDescending<Medium> { it.rating }
            } else {
                compareBy<Medium> { it.rating }
            }
            primary.thenByDescending { it.modified }.thenBy { it.path.lowercase(Locale.ROOT) }
        }
        SortField.NAME, SortField.RECENT, SortField.COUNT -> {
            val primary = if (desc) {
                compareByDescending<Medium> { it.name.lowercase(Locale.ROOT) }
            } else {
                compareBy<Medium> { it.name.lowercase(Locale.ROOT) }
            }
            primary.thenBy { it.path.lowercase(Locale.ROOT) }
        }
        SortField.DATE -> {
            val primary = if (desc) {
                compareByDescending<Medium> { mediaDateSortKey(it) }
            } else {
                compareBy<Medium> { mediaDateSortKey(it) }
            }
            primary.thenBy { it.path.lowercase(Locale.ROOT) }
        }
        SortField.SIZE -> {
            val primary = if (desc) {
                compareByDescending<Medium> { it.size }
            } else {
                compareBy<Medium> { it.size }
            }
            primary.thenBy { it.path.lowercase(Locale.ROOT) }
        }
    }
    return list.sortedWith(comparator)
}

internal fun groupMediaByMonth(media: List<Medium>): List<MonthGroup> {
    if (media.isEmpty()) return emptyList()
    val grouped = LinkedHashMap<String, MutableList<Medium>>()
    media.forEach { m -> grouped.getOrPut(formatMediaMonthLabel(m)) { mutableListOf() }.add(m) }
    return grouped.map { MonthGroup(it.key, it.value) }
}

internal fun formatMediaMonthLabel(medium: Medium): String {
    val key = mediaDateSortKey(medium)
    val formatter = requireNotNull(monthLabelFormat.get())
    return formatter.format(Date(key)).replaceFirstChar { it.uppercase() }
}

internal fun <T> Flow<T>.chunkedByCountOrTimeout(size: Int, timeoutMillis: Long): Flow<List<T>> = flow {
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

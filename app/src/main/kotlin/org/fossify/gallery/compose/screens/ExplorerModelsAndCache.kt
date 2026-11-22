package org.fossify.gallery.compose.screens

import android.content.Context
import androidx.compose.runtime.saveable.listSaver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.GroupBy
import org.fossify.gallery.helpers.GroupOrder
import org.fossify.gallery.helpers.GroupRow
import org.fossify.gallery.helpers.MediaRepository
import org.fossify.gallery.helpers.MediaStoreOps
import org.fossify.gallery.helpers.RefreshBus
import org.fossify.gallery.helpers.VIDEO_EXTENSIONS
import org.fossify.gallery.models.MediaReviewFilter
import org.fossify.gallery.models.MediaReviewStatus
import org.fossify.gallery.models.Medium
import org.fossify.gallery.viewmodels.LibraryFilterState
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap

internal val stringSetSaver = listSaver<Set<String>, String>(
    save = { it.toList() },
    restore = { it.toSet() },
)

internal data class ExplorerItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val lastModified: Long = 0L,
    val size: Long = 0L,
    val taken: Long = 0L,
    val dateAdded: Long = 0L,
    val type: Int = 0,
    val thumbnailPath: String = "",
    val mediaCount: Int = 0,
    val imageCount: Int = 0,
    val videoCount: Int = 0,
    val previewPaths: List<String> = emptyList(),
    val sortKey: Long = 0L,
    val rating: Int = 0,
    val videoDuration: Int = 0,
    val isFavorite: Boolean = false,
)

internal data class ExplorerContent(
    val folders: List<ExplorerItem>,
    val files: List<ExplorerItem>,
    val tags: Map<String, List<String>>,
)

internal data class ExplorerGroupingCacheKey(
    val path: String,
    val nameFilter: String,
    val filter: LibraryFilterState,
    val filesHash: Int,
    val reviewStatusHash: Int,
    val tagsHash: Int,
    val sortBy: SortField,
    val sortDescending: Boolean,
    val groupBy: GroupBy,
    val groupOrder: GroupOrder,
    val onlyTopLevelTags: Boolean,
)

internal data class ExplorerGroupingSnapshot(
    val itemsByPath: Map<String, ExplorerItem>,
    val rows: List<GroupRow>,
)

internal fun explorerNameMatchesFilter(name: String, query: String): Boolean {
    val normalizedQuery = query.trim()
    return normalizedQuery.isEmpty() || name.contains(normalizedQuery, ignoreCase = true)
}

internal fun ExplorerItem.matchesLibraryFilter(
    filter: LibraryFilterState,
    tags: Set<String>,
    reviewStatus: MediaReviewStatus?,
): Boolean {
    val ext = name.substringAfterLast('.', "").lowercase()
    val isVideo = type == 2 || ext in VIDEO_EXTENSIONS
    val typeMatches = when (filter.typeFilter) {
        0 -> true
        1 -> type == 1 || (!isVideo && type == 0)
        2 -> isVideo
        else -> type == filter.typeFilter
    }
    val hasRegularCriteria = filter.rating > 0 || filter.minSize > 0L || filter.typeFilter > 0 ||
        filter.tagNames != null || filter.pathFilter != null || filter.excludePathFilter != null ||
        filter.dateRange > 0 || filter.addedSince > 0L || filter.reviewFilter != MediaReviewFilter.NONE ||
        filter.textQuery.isNotBlank()
    if (filter.activeCollectionName != null && !hasRegularCriteria) {
        return path in filter.additionalMediaPaths
    }
    val matchesRegular =
        (filter.rating <= 0 || rating >= filter.rating) &&
            (filter.minSize <= 0L || size >= filter.minSize) &&
            typeMatches &&
            (filter.tagNames == null || if (filter.tagMatchAll) filter.tagNames.all { it in tags } else filter.tagNames.any { it in tags }) &&
            (filter.pathFilter == null || filter.pathFilter.any { path.equals(it, ignoreCase = true) || path.startsWith("${it.trimEnd('/')}/", ignoreCase = true) }) &&
            (filter.excludePathFilter == null || filter.excludePathFilter.none { path.equals(it, ignoreCase = true) || path.startsWith("${it.trimEnd('/')}/", ignoreCase = true) }) &&
            (filter.dateRange <= 0 || (if (taken > 0L) taken else lastModified) >= System.currentTimeMillis() - when (filter.dateRange) {
                1 -> 86_400_000L
                2 -> 7 * 86_400_000L
                3 -> 30 * 86_400_000L
                4 -> 365 * 86_400_000L
                else -> 0L
            }) &&
            (filter.addedSince <= 0L || maxOf(dateAdded, lastModified) > filter.addedSince) &&
            (filter.reviewFilter != MediaReviewFilter.UNDECIDED || reviewStatus == null)
    return if (filter.additionalMediaPaths.isEmpty()) matchesRegular
    else matchesRegular || path in filter.additionalMediaPaths
}

internal fun ExplorerItem.toMediumForGrouping() = Medium(
    id = null,
    name = name,
    path = path,
    parentPath = "",
    modified = lastModified,
    taken = taken,
    size = size,
    type = type,
    videoDuration = videoDuration,
    isFavorite = isFavorite,
    deletedTS = 0L,
    mediaStoreId = 0L,
    rating = rating,
    dateAdded = dateAdded,
    dateSortKey = sortKey,
)

internal const val EXPLORER_MOSAIC_CHUNK_SIZE = 24

internal object ExplorerContentCache {
    private data class Entry(val content: ExplorerContent, val generation: Long)
    private val map = ConcurrentHashMap<String, Entry>()

    fun get(key: String): ExplorerContent? = map[key]
        ?.takeIf { it.generation == RefreshBus.generation }
        ?.content

    fun put(key: String, content: ExplorerContent) {
        map[key] = Entry(content, RefreshBus.generation)
    }

    fun clear() = map.clear()

    fun invalidatePaths(paths: Collection<String>) {
        val changed = paths.map { it.trimEnd('/') }.filter { it.isNotBlank() }
        if (changed.isEmpty()) return
        map.keys.removeIf { key ->
            val cachedPath = key.substringBefore('|')
            changed.any { path ->
                cachedPath == path || cachedPath.startsWith("$path/") || path.startsWith("$cachedPath/")
            }
        }
    }
}

internal object ExplorerGroupingCache {
    private const val MAX_ENTRIES = 4
    private data class Entry(val snapshot: ExplorerGroupingSnapshot, val generation: Long)
    private val map = object : LinkedHashMap<ExplorerGroupingCacheKey, Entry>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<ExplorerGroupingCacheKey, Entry>?): Boolean = size > MAX_ENTRIES
    }

    @Synchronized
    fun get(key: ExplorerGroupingCacheKey): ExplorerGroupingSnapshot? = map[key]
        ?.takeIf { it.generation == RefreshBus.generation }
        ?.snapshot

    @Synchronized
    fun put(key: ExplorerGroupingCacheKey, snapshot: ExplorerGroupingSnapshot) {
        map[key] = Entry(snapshot, RefreshBus.generation)
    }

    @Synchronized
    fun clear() = map.clear()

    @Synchronized
    fun invalidatePaths(paths: Collection<String>) {
        val changed = paths.map { it.trimEnd('/') }.filter { it.isNotBlank() }
        if (changed.isEmpty()) return
        map.keys.removeIf { key ->
            changed.any { path ->
                key.path == path || key.path.startsWith("$path/") || path.startsWith("${key.path}/")
            }
        }
    }
}

internal data class ExplorerScrollPosition(val index: Int, val offset: Int, val anchorPath: String = "")

internal object ExplorerScrollCache {
    private const val MAX_ENTRIES = 32
    private val positions = object : LinkedHashMap<String, ExplorerScrollPosition>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ExplorerScrollPosition>?): Boolean = size > MAX_ENTRIES
    }

    @Synchronized
    fun get(path: String): ExplorerScrollPosition? = positions[path]

    @Synchronized
    fun put(path: String, position: ExplorerScrollPosition) {
        val previous = positions[path]
        positions[path] = if (position.anchorPath.isBlank() && previous?.anchorPath.orEmpty().isNotBlank()) {
            position.copy(anchorPath = previous?.anchorPath.orEmpty())
        } else position
    }
}

/**
 * Loads, aggregates, and sorts subfolders, direct media files, and tags for an Explorer folder on IO.
 */
internal suspend fun loadExplorerFolderContents(
    context: Context,
    repo: MediaRepository,
    path: String,
    storageRoot: String,
    allEntries: List<MediaStoreOps.MediaEntry>?,
    folderSettings: ViewSettings,
    mediaSettings: ViewSettings,
    needsTags: Boolean,
): ExplorerContent = withContext(Dispatchers.IO) {
    val root = path.trimEnd('/')
    val deletedPaths = repo.getDeletedPaths()
    val hiddenLower = context.config.explorer2HiddenFolders
        .map { it.trimEnd('/').lowercase(java.util.Locale.ROOT) }
        .toSet()
    val entries = if (allEntries != null && root.startsWith(storageRoot, ignoreCase = true)) {
        MediaStoreOps.entriesUnderIgnoreCase(allEntries, root)
    } else {
        MediaStoreOps.mediaEntriesUnder(context, root)
    }
    class Agg(var displayPath: String) {
        var thumb: String = ""
        var newestAdded: Long = -1L
        var lastModified: Long = 0L
        var count: Int = 0
        var imageCount: Int = 0
        var videoCount: Int = 0
        val previews: MutableList<String> = mutableListOf()
    }
    val folderMap = LinkedHashMap<String, Agg>()
    val rootPrefix = "$root/"
    for (e in entries) {
        if (e.path in deletedPaths) continue
        if (e.path.length <= rootPrefix.length || !e.path.startsWith(rootPrefix, ignoreCase = true)) continue
        val rel = e.path.substring(rootPrefix.length)
        val slash = rel.indexOf('/')
        if (slash < 0) continue
        val seg = rel.substring(0, slash)
        val folderPath = "$root/$seg"
        val folderKey = folderPath.lowercase(java.util.Locale.ROOT)
        if (folderKey in hiddenLower) continue
        val agg = folderMap.getOrPut(folderKey) { Agg(folderPath) }
        // Prefer canonical/capitalized folder casing when case-variant paths exist on FUSE storage
        // (e.g. "Pictures" over "pictures", "Nsfw" over "nsfw").
        val existingSeg = agg.displayPath.substringAfterLast('/')
        if (seg != existingSeg && seg.firstOrNull()?.isUpperCase() == true && existingSeg.firstOrNull()?.isUpperCase() != true) {
            agg.displayPath = folderPath
        }
        if (e.dateAdded > agg.newestAdded) { agg.newestAdded = e.dateAdded; agg.thumb = e.path }
        if (agg.previews.size < 4) agg.previews.add(e.path)
        agg.count++
        if (e.type == 2 || e.path.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS) agg.videoCount++ else agg.imageCount++
        if (e.modified > agg.lastModified) agg.lastModified = e.modified
    }
    val folders = folderMap.values.map { agg ->
        val fp = agg.displayPath
        ExplorerItem(
            name = fp.substringAfterLast('/'),
            path = fp,
            isDirectory = true,
            lastModified = agg.lastModified,
            thumbnailPath = if (folderSettings.showFolderThumbnails) agg.thumb else "",
            mediaCount = agg.count,
            imageCount = agg.imageCount,
            videoCount = agg.videoCount,
            previewPaths = agg.previews.toList(),
        )
    }
    val files = repo.getMediaFromPath(root).map {
        ExplorerItem(
            name = it.name,
            path = it.path,
            isDirectory = false,
            lastModified = it.modified,
            size = it.size,
            taken = it.taken,
            dateAdded = it.dateAdded,
            type = it.type,
            sortKey = if (it.dateSortKey > 0) it.dateSortKey else if (it.taken > 0) it.taken else it.modified,
            rating = it.rating,
            videoDuration = it.videoDuration,
            isFavorite = it.isFavorite,
        )
    }
    val sortedFolders = when (folderSettings.sortBy) {
        SortField.NAME, SortField.RATING, SortField.RECENT ->
            if (folderSettings.sortDesc) folders.sortedWith(compareByDescending<ExplorerItem> { it.name.lowercase() }.thenBy { it.path.lowercase() })
            else folders.sortedWith(compareBy<ExplorerItem> { it.name.lowercase() }.thenBy { it.path.lowercase() })
        SortField.DATE ->
            if (folderSettings.sortDesc) folders.sortedWith(compareByDescending<ExplorerItem> { it.lastModified }.thenBy { it.path.lowercase() })
            else folders.sortedWith(compareBy<ExplorerItem> { it.lastModified }.thenBy { it.path.lowercase() })
        SortField.SIZE ->
            if (folderSettings.sortDesc) folders.sortedWith(compareByDescending<ExplorerItem> { it.size }.thenBy { it.path.lowercase() })
            else folders.sortedWith(compareBy<ExplorerItem> { it.size }.thenBy { it.path.lowercase() })
        SortField.COUNT ->
            if (folderSettings.sortDesc) folders.sortedWith(compareByDescending<ExplorerItem> { it.mediaCount }.thenBy { it.path.lowercase() })
            else folders.sortedWith(compareBy<ExplorerItem> { it.mediaCount }.thenBy { it.path.lowercase() })
    }
    val sortedFiles = when (mediaSettings.sortBy) {
        SortField.NAME, SortField.COUNT, SortField.RECENT ->
            if (mediaSettings.sortDesc) files.sortedWith(compareByDescending<ExplorerItem> { it.name.lowercase() }.thenBy { it.path.lowercase() })
            else files.sortedWith(compareBy<ExplorerItem> { it.name.lowercase() }.thenBy { it.path.lowercase() })
        SortField.DATE ->
            if (mediaSettings.sortDesc) files.sortedWith(compareByDescending<ExplorerItem> { it.sortKey }.thenBy { it.path.lowercase() })
            else files.sortedWith(compareBy<ExplorerItem> { it.sortKey }.thenBy { it.path.lowercase() })
        SortField.SIZE ->
            if (mediaSettings.sortDesc) files.sortedWith(compareByDescending<ExplorerItem> { it.size }.thenBy { it.path.lowercase() })
            else files.sortedWith(compareBy<ExplorerItem> { it.size }.thenBy { it.path.lowercase() })
        SortField.RATING ->
            if (mediaSettings.sortDesc) files.sortedWith(compareByDescending<ExplorerItem> { it.rating }.thenBy { it.path.lowercase() })
            else files.sortedWith(compareBy<ExplorerItem> { it.rating }.thenBy { it.path.lowercase() })
    }
    val sortedTags = if (needsTags) repo.getTagsForPaths(sortedFiles.map { it.path }) else emptyMap()
    ExplorerContent(sortedFolders, sortedFiles, sortedTags)
}

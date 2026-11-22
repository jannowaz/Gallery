package org.fossify.gallery.compose.screens

import org.fossify.gallery.helpers.GroupBy
import org.fossify.gallery.helpers.GroupRow
import org.fossify.gallery.helpers.buildAlphabetGroupRows
import org.fossify.gallery.helpers.buildRatingGroupRows
import org.fossify.gallery.helpers.buildSizeGroupRows
import org.fossify.gallery.helpers.buildTagGroupRows
import org.fossify.gallery.models.Medium
import org.fossify.gallery.viewmodels.MediaViewModel

internal fun buildExplorerFileGroupRows(
    visibleFileItems: List<ExplorerItem>,
    fileTagsByPath: Map<String, List<String>>,
    tagHierarchy: Map<String, String>,
    mediaSettings: ViewSettings,
    untaggedLabel: String,
    unratedLabel: String,
): Pair<Map<String, ExplorerItem>, List<GroupRow>> {
    val itemMap = visibleFileItems.associateBy { it.path }
    val fileMedia = visibleFileItems.map { it.toMediumForGrouping() }
    val rows = when (mediaSettings.groupBy) {
        GroupBy.TAG -> buildTagGroupRows(
            media = fileMedia,
            tagsByPath = fileTagsByPath,
            hierarchy = tagHierarchy,
            order = mediaSettings.groupOrder,
            onlyTopLevelTags = mediaSettings.onlyTopLevelTags,
            collapsedKeys = emptySet(),
            untaggedLabel = untaggedLabel,
        )
        GroupBy.RATING -> buildRatingGroupRows(fileMedia, mediaSettings.groupOrder, unratedLabel)
        GroupBy.SIZE -> buildSizeGroupRows(fileMedia, mediaSettings.sortDesc)
        GroupBy.ALPHABET -> buildAlphabetGroupRows(fileMedia, mediaSettings.sortDesc)
        GroupBy.MONTH -> buildMonthGroupRows(fileMedia)
        GroupBy.NONE -> if (fileMedia.isEmpty()) emptyList() else listOf(GroupRow.Items("all", fileMedia))
    }
    return itemMap to rows
}

private fun buildMonthGroupRows(fileMedia: List<Medium>): List<GroupRow> {
    val grouped = LinkedHashMap<String, MutableList<Medium>>()
    fileMedia.forEach { m ->
        grouped.getOrPut(MediaViewModel.monthLabelFor(m)) { mutableListOf() }.add(m)
    }
    return grouped.flatMap { (label, items) ->
        val key = "month:$label"
        listOf(
            GroupRow.SectionHeader(key, label, 0, items.size, items.size, false, true),
            GroupRow.Items(key, items),
        )
    }
}

internal fun buildExplorerScrollIndexMaps(
    visibleFolderItems: List<ExplorerItem>,
    visibleFileItems: List<ExplorerItem>,
    fileGroupRows: List<GroupRow>,
    fileItemByPath: Map<String, ExplorerItem>,
    folderSettings: ViewSettings,
    mediaSettings: ViewSettings,
    folderGridColumns: Int,
    mediaGridColumns: Int,
): Pair<Map<String, String>, Map<String, Int>> {
    val keyToPath = LinkedHashMap<String, String>()
    val indexByPath = LinkedHashMap<String, Int>()
    var lazyIndex = 0
    fun addRow(key: String, paths: List<String>) {
        val first = paths.firstOrNull()
        if (first != null) {
            keyToPath[key] = first
            paths.forEach { path -> indexByPath.putIfAbsent(path, lazyIndex) }
        }
        lazyIndex++
    }
    if (visibleFolderItems.isNotEmpty()) {
        lazyIndex++
        folderScrollRows(visibleFolderItems, folderSettings.viewType, folderGridColumns)
            .forEach { (k, paths) -> addRow(k, paths) }
    }
    if (visibleFileItems.isNotEmpty()) {
        lazyIndex++
        appendFileGroupScrollRows(
            fileGroupRows = fileGroupRows,
            fileItemByPath = fileItemByPath,
            viewType = mediaSettings.viewType,
            mediaGridColumns = mediaGridColumns,
            onHeader = { lazyIndex++ },
            onRow = ::addRow,
        )
    }
    return keyToPath to indexByPath
}

private fun appendFileGroupScrollRows(
    fileGroupRows: List<GroupRow>,
    fileItemByPath: Map<String, ExplorerItem>,
    viewType: ViewType,
    mediaGridColumns: Int,
    onHeader: () -> Unit,
    onRow: (String, List<String>) -> Unit,
) {
    fileGroupRows.forEach { row ->
        when (row) {
            is GroupRow.SectionHeader -> onHeader()
            is GroupRow.Items -> {
                val paths = row.media.mapNotNull { fileItemByPath[it.path]?.path }
                mediaScrollRows(row.sectionKey, paths, viewType, mediaGridColumns)
                    .forEach { (k, chunk) -> onRow(k, chunk) }
            }
        }
    }
}

private fun folderScrollRows(
    folders: List<ExplorerItem>,
    viewType: ViewType,
    gridColumns: Int,
): List<Pair<String, List<String>>> = if (viewType == ViewType.GRID) {
    folders.chunked(gridColumns).mapIndexed { rowIndex, chunk ->
        "folderrow_$rowIndex" to chunk.map { it.path }
    }
} else {
    folders.map { item -> "folderrow_${item.path}" to listOf(item.path) }
}

private fun mediaScrollRows(
    sectionKey: String,
    paths: List<String>,
    viewType: ViewType,
    gridColumns: Int,
): List<Pair<String, List<String>>> = when (viewType) {
    ViewType.GRID -> paths.chunked(gridColumns).mapIndexed { rowIndex, chunk ->
        "$sectionKey/gridrow_$rowIndex" to chunk
    }
    ViewType.MOSAIC -> paths.chunked(EXPLORER_MOSAIC_CHUNK_SIZE).mapIndexed { chunkIdx, chunk ->
        "explorer_media_mosaic_${sectionKey}_$chunkIdx" to chunk
    }
    else -> paths.map { path -> "$sectionKey/$path" to listOf(path) }
}

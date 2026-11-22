package org.fossify.gallery.compose.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.fossify.gallery.R
import org.fossify.gallery.compose.components.AlbumListRow
import org.fossify.gallery.compose.components.EmptyState
import org.fossify.gallery.compose.components.FastScrollBar
import org.fossify.gallery.compose.components.FolderSelectionOverlay
import org.fossify.gallery.compose.components.FolderTile
import org.fossify.gallery.compose.components.MediaListRow
import org.fossify.gallery.compose.components.MediaTile
import org.fossify.gallery.compose.components.SectionHeader
import org.fossify.gallery.compose.components.findDuplicateFolderNames
import org.fossify.gallery.compose.components.folderPathHint
import org.fossify.gallery.compose.components.tagAccentColor
import org.fossify.gallery.compose.theme.Radius
import org.fossify.gallery.compose.theme.Scrim
import org.fossify.gallery.compose.util.ColumnCountIndicator
import org.fossify.gallery.compose.util.PeekState
import org.fossify.gallery.compose.util.ThumbnailOverlayState
import org.fossify.gallery.compose.util.pinchToChangeColumns
import org.fossify.gallery.helpers.GroupBy
import org.fossify.gallery.helpers.GroupRow
import org.fossify.gallery.helpers.VIDEO_EXTENSIONS
import org.fossify.gallery.viewmodels.LibraryFilterActions
import org.fossify.gallery.viewmodels.LibraryFilterState

private const val NARROW_TILE_WIDTH_DP = 88
private const val ULTRA_NARROW_FOLDER_TILE_WIDTH_DP = 84
private const val MIN_PINCH_COLUMNS = 2
private const val MAX_MEDIA_PINCH_COLUMNS = 6
private const val MAX_FOLDER_PINCH_COLUMNS = 5

internal data class ExplorerListContentData(
    val visibleFolderItems: List<ExplorerItem>,
    val visibleFileItems: List<ExplorerItem>,
    val fileGroupRows: List<GroupRow>,
    val fileItemByPath: Map<String, ExplorerItem>,
    val fileTagsByPath: Map<String, List<String>>,
    val selectedFolderPaths: Set<String>,
    val selectedFilePaths: Set<String>,
    val hasFolderSelection: Boolean,
    val hasFileSelection: Boolean,
    val collapsibleFileGroupKeys: Set<String>,
    val allFileGroupsExpanded: Boolean,
)

internal data class ExplorerListDisplayConfig(
    val folderSettings: ViewSettings,
    val mediaSettings: ViewSettings,
    val folderGridColumns: Int,
    val mediaGridColumns: Int,
    val folderCardColor: Color,
    val isNameFiltering: Boolean,
    val normalizedNameFilter: String,
    val filterState: LibraryFilterState,
)

internal data class ExplorerListCallbacks(
    val onFolderClick: (ExplorerItem) -> Unit,
    val onFolderLongClick: (ExplorerItem) -> Unit,
    val onFileClick: (ExplorerItem) -> Unit,
    val onFileLongClick: (ExplorerItem) -> Unit,
    val onTagClick: (String) -> Unit,
    val onToggleGroup: (String) -> Unit,
    val onToggleAllGroups: () -> Unit,
    val onClearNameFilter: () -> Unit,
    val filterActions: LibraryFilterActions,
    val onMediaColumnCountChange: ((Int) -> Unit)?,
    val onFolderColumnCountChange: ((Int) -> Unit)?,
)

private data class ExplorerRenderMetrics(
    val isNarrowFolderTile: Boolean,
    val isUltraNarrowFolderTile: Boolean,
    val isNarrowMediaTile: Boolean,
    val internalStorageLabel: String,
    val duplicateFolderNames: Set<String>,
    val mediaBg: Color,
    val cornerShape: RoundedCornerShape,
    val foldersHeaderLabel: String,
    val mediaHeaderLabel: String,
)

private data class ExplorerPinchConfig(
    val enabled: Boolean,
    val currentColumns: Int,
    val maxColumns: Int,
    val onColumnsChange: (Int) -> Unit,
)

private fun resolveExplorerPinchConfig(
    data: ExplorerListContentData,
    config: ExplorerListDisplayConfig,
    callbacks: ExplorerListCallbacks,
): ExplorerPinchConfig {
    val hasSelection = data.hasFileSelection || data.hasFolderSelection
    val mediaCallback = callbacks.onMediaColumnCountChange
    val folderCallback = callbacks.onFolderColumnCountChange
    val pinchMedia = data.visibleFileItems.isNotEmpty() &&
        config.mediaSettings.viewType != ViewType.LIST &&
        mediaCallback != null
    val pinchFolders = !pinchMedia &&
        data.visibleFolderItems.isNotEmpty() &&
        config.folderSettings.viewType == ViewType.GRID &&
        folderCallback != null
    return ExplorerPinchConfig(
        enabled = (pinchMedia || pinchFolders) && !hasSelection,
        currentColumns = if (pinchMedia) config.mediaSettings.columnCount else config.folderSettings.columnCount,
        maxColumns = if (pinchMedia) MAX_MEDIA_PINCH_COLUMNS else MAX_FOLDER_PINCH_COLUMNS,
        onColumnsChange = { next ->
            if (pinchMedia) mediaCallback(next) else folderCallback?.invoke(next)
        },
    )
}

@Composable
internal fun ExplorerContentList(
    listState: LazyListState,
    data: ExplorerListContentData,
    peekState: PeekState,
    config: ExplorerListDisplayConfig,
    callbacks: ExplorerListCallbacks,
    modifier: Modifier = Modifier,
) {
    val metrics = rememberExplorerRenderMetrics(data.visibleFolderItems, config)
    var showColumnIndicator by remember { mutableStateOf(false) }
    var indicatorColumnCount by remember { mutableIntStateOf(config.mediaSettings.columnCount) }
    val pinch = resolveExplorerPinchConfig(data, config, callbacks)
    val isFilteredEmpty = (config.isNameFiltering || config.filterState.isActive) &&
        data.visibleFolderItems.isEmpty() &&
        data.visibleFileItems.isEmpty()

    Box(
        modifier.pinchToChangeColumns(
            enabled = pinch.enabled,
            currentColumns = pinch.currentColumns,
            minColumns = MIN_PINCH_COLUMNS,
            maxColumns = pinch.maxColumns,
            onColumnsChange = { next ->
                pinch.onColumnsChange(next)
                indicatorColumnCount = next
                showColumnIndicator = true
            },
        ),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(4.dp),
        ) {
            if (data.visibleFolderItems.isNotEmpty()) {
                explorerFolderSection(data, config, metrics, callbacks)
            }
            if (data.visibleFileItems.isNotEmpty()) {
                explorerMediaSection(data, config, metrics, peekState, callbacks)
            }
        }
        if (isFilteredEmpty) {
            ExplorerFilteredEmptyOverlay(config, callbacks)
        }
        ColumnCountIndicator(
            columnCount = indicatorColumnCount,
            visible = showColumnIndicator,
            onAutoHide = { showColumnIndicator = false },
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
        )
        FastScrollBar(
            state = listState,
            modifier = Modifier.align(Alignment.CenterEnd).padding(vertical = 10.dp),
        )
        ExplorerPeekOverlay(peekState = peekState)
    }
}

@Composable
private fun rememberExplorerRenderMetrics(
    visibleFolderItems: List<ExplorerItem>,
    config: ExplorerListDisplayConfig,
): ExplorerRenderMetrics {
    val windowWidthDp = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
    val folderTileWidthDp = windowWidthDp / config.folderGridColumns.coerceAtLeast(1)
    val mediaTileWidthDp = windowWidthDp / config.mediaGridColumns.coerceAtLeast(1)
    val duplicateFolderNames = remember(visibleFolderItems) {
        findDuplicateFolderNames(visibleFolderItems.map { it.name })
    }
    val cornerShape = if (config.mediaSettings.roundedCorners) {
        RoundedCornerShape(Radius.sm)
    } else {
        RoundedCornerShape(0.dp)
    }
    val mediaBg = if (config.mediaSettings.displayMode == DisplayMode.DARK) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        MaterialTheme.colorScheme.surface
    }
    return ExplorerRenderMetrics(
        isNarrowFolderTile = folderTileWidthDp < NARROW_TILE_WIDTH_DP.dp,
        isUltraNarrowFolderTile = folderTileWidthDp < ULTRA_NARROW_FOLDER_TILE_WIDTH_DP.dp,
        isNarrowMediaTile = mediaTileWidthDp < NARROW_TILE_WIDTH_DP.dp,
        internalStorageLabel = stringResource(R.string.internal_storage),
        duplicateFolderNames = duplicateFolderNames,
        mediaBg = mediaBg,
        cornerShape = cornerShape,
        foldersHeaderLabel = stringResource(R.string.folders),
        mediaHeaderLabel = stringResource(R.string.media),
    )
}

@Composable
private fun ExplorerFilteredEmptyOverlay(
    config: ExplorerListDisplayConfig,
    callbacks: ExplorerListCallbacks,
) {
    val onClear = if (config.isNameFiltering) callbacks.onClearNameFilter else callbacks.filterActions.clearAll
    EmptyState(
        icon = Icons.Default.SearchOff,
        title = stringResource(R.string.no_results),
        subtitle = if (config.isNameFiltering) {
            stringResource(R.string.explorer_name_filter_no_results, config.normalizedNameFilter)
        } else {
            stringResource(R.string.no_items_hint)
        },
        actionLabel = stringResource(R.string.clear_filter),
        onAction = onClear,
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
    )
}

@OptIn(ExperimentalFoundationApi::class)
private fun LazyListScope.explorerFolderSection(
    data: ExplorerListContentData,
    config: ExplorerListDisplayConfig,
    metrics: ExplorerRenderMetrics,
    callbacks: ExplorerListCallbacks,
) {
    val folderSettings = config.folderSettings
    item(key = "explorer_folders_header", contentType = "section_header") {
        ExplorerSectionHeader(metrics.foldersHeaderLabel, data.visibleFolderItems.size)
    }
    if (folderSettings.viewType == ViewType.GRID) {
        data.visibleFolderItems.chunked(config.folderGridColumns).forEachIndexed { rowIndex, chunk ->
            item(key = "folderrow_$rowIndex", contentType = "folder_grid_row") {
                Row(Modifier.fillMaxWidth().padding(folderSettings.spacing.dp / 2)) {
                    chunk.forEach { item ->
                        ExplorerFolderGridCell(
                            item = item,
                            folderSettings = folderSettings,
                            folderCardColor = config.folderCardColor,
                            disambiguationHint = if (item.name.lowercase() in metrics.duplicateFolderNames) {
                                folderPathHint(item.path, metrics.internalStorageLabel)
                            } else {
                                null
                            },
                            isNarrow = metrics.isNarrowFolderTile,
                            isUltraNarrow = metrics.isUltraNarrowFolderTile,
                            isSelected = item.path in data.selectedFolderPaths,
                            hasFolderSelection = data.hasFolderSelection,
                            onClick = { callbacks.onFolderClick(item) },
                            onLongClick = { callbacks.onFolderLongClick(item) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(config.folderGridColumns - chunk.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    } else {
        val folderListHalfGap = ((folderSettings.spacing / 2).coerceAtLeast(2) / 2f).dp
        data.visibleFolderItems.forEach { item ->
            item(key = "folderrow_${item.path}", contentType = "folder_list_row") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                    Box(Modifier.weight(1f)) {
                        val subtitle = pluralStringResource(
                            R.plurals.media_count_plural,
                            item.mediaCount,
                            item.mediaCount,
                        )
                        AlbumListRow(
                            name = item.name,
                            subtitle = subtitle,
                            coverPath = item.thumbnailPath.ifEmpty { item.previewPaths.firstOrNull() ?: "" },
                            selected = item.path in data.selectedFolderPaths,
                            showChevron = true,
                            showThumbnail = folderSettings.showFolderThumbnails,
                            roundedCorners = folderSettings.roundedCorners,
                            containerColor = config.folderCardColor,
                            compact = folderSettings.listDensity == ListDensity.COMPACT,
                            badgeSubtitle = true,
                            pathHint = folderPathHint(item.path, metrics.internalStorageLabel),
                            imageCount = item.imageCount,
                            videoCount = item.videoCount,
                            modifier = Modifier
                                .padding(horizontal = 4.dp, vertical = folderListHalfGap)
                                .combinedClickable(
                                    onClick = { callbacks.onFolderClick(item) },
                                    onLongClick = { callbacks.onFolderLongClick(item) },
                                ),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExplorerFolderGridCell(
    item: ExplorerItem,
    folderSettings: ViewSettings,
    folderCardColor: Color,
    disambiguationHint: String?,
    isNarrow: Boolean,
    isUltraNarrow: Boolean,
    isSelected: Boolean,
    hasFolderSelection: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .padding(folderSettings.spacing.dp / 2)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .semantics(mergeDescendants = true) {
                contentDescription = buildString {
                    append(item.name)
                    if (item.mediaCount > 0) append(", ").append(item.mediaCount)
                    if (!disambiguationHint.isNullOrEmpty()) append(", ").append(disambiguationHint)
                }
                role = Role.Button
                selected = isSelected
            },
    ) {
        FolderTile(
            name = item.name,
            thumbnailPath = item.thumbnailPath,
            showThumbnail = folderSettings.showFolderThumbnails,
            roundedCorners = folderSettings.roundedCorners,
            containerColor = folderCardColor,
            subtitle = if (item.mediaCount > 0) {
                pluralStringResource(R.plurals.media_count_plural, item.mediaCount, item.mediaCount)
            } else {
                null
            },
            pathHint = disambiguationHint,
            imageCount = item.imageCount,
            videoCount = item.videoCount,
            isNarrow = isNarrow,
            isUltraNarrow = isUltraNarrow,
        )
        if (hasFolderSelection) {
            if (isSelected) {
                FolderSelectionOverlay(
                    roundedCorners = folderSettings.roundedCorners,
                    modifier = Modifier.matchParentSize(),
                    isNarrow = isNarrow,
                )
            } else {
                val checkSize = if (isNarrow) 18.dp else 24.dp
                val pad = if (isNarrow) 3.dp else 6.dp
                Box(
                    Modifier.align(Alignment.TopStart).padding(pad).size(checkSize),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.matchParentSize().background(Scrim.a35, CircleShape))
                    Icon(
                        Icons.Default.RadioButtonUnchecked,
                        stringResource(R.string.cd_not_selected),
                        tint = Color.White,
                        modifier = Modifier.size(checkSize - 2.dp),
                    )
                }
            }
        }
    }
}

private fun LazyListScope.explorerMediaSection(
    data: ExplorerListContentData,
    config: ExplorerListDisplayConfig,
    metrics: ExplorerRenderMetrics,
    peekState: PeekState,
    callbacks: ExplorerListCallbacks,
) {
    item(key = "explorer_media_header", contentType = "section_header") {
        ExplorerSectionHeader(
            label = metrics.mediaHeaderLabel,
            count = data.visibleFileItems.size,
            showGroupToggle = config.mediaSettings.groupBy in setOf(GroupBy.TAG, GroupBy.RATING) &&
                data.collapsibleFileGroupKeys.isNotEmpty(),
            groupsExpanded = data.allFileGroupsExpanded,
            onToggleGroups = callbacks.onToggleAllGroups,
        )
    }
    val mediaSettings = config.mediaSettings
    val showGuide = mediaSettings.viewType == ViewType.LIST
    data.fileGroupRows.forEach { row ->
        when (row) {
            is GroupRow.SectionHeader -> item(key = "filehdr_${row.key}", contentType = "header") {
                SectionHeader(
                    label = row.label,
                    count = if (row.isExpanded) row.exactCount else row.totalCount,
                    depth = row.depth,
                    hasChildren = row.hasChildren,
                    isExpanded = row.isExpanded,
                    onToggle = { callbacks.onToggleGroup(row.key) },
                    ratingValue = row.ratingValue,
                    accentColor = row.rootKey?.let { tagAccentColor(it) },
                    showGuideLine = showGuide,
                )
            }
            is GroupRow.Items -> {
                val groupFileItems = row.media.mapNotNull { data.fileItemByPath[it.path] }
                when (mediaSettings.viewType) {
                    ViewType.GRID -> explorerMediaGridChunk(
                        row.sectionKey, groupFileItems, data, config, metrics, peekState, callbacks,
                    )
                    ViewType.MOSAIC -> explorerMediaMosaicChunk(
                        row.sectionKey, groupFileItems, data, config, metrics, peekState, callbacks,
                    )
                    else -> explorerMediaListChunk(
                        row.sectionKey, groupFileItems, data, config, metrics, peekState, callbacks,
                    )
                }
            }
        }
    }
}

private fun LazyListScope.explorerMediaGridChunk(
    sectionKey: String,
    groupFileItems: List<ExplorerItem>,
    data: ExplorerListContentData,
    config: ExplorerListDisplayConfig,
    metrics: ExplorerRenderMetrics,
    peekState: PeekState,
    callbacks: ExplorerListCallbacks,
) {
    val mediaSettings = config.mediaSettings
    groupFileItems.chunked(config.mediaGridColumns).forEachIndexed { rowIndex, chunk ->
        item(key = "$sectionKey/gridrow_$rowIndex", contentType = "media_grid_row") {
            Row(Modifier.fillMaxWidth().padding(mediaSettings.spacing.dp / 2)) {
                chunk.forEach { item ->
                    val isVideo = item.type == 2 ||
                        item.path.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS
                    val medium = remember(item) { item.toMediumForGrouping() }
                    val itemTags = data.fileTagsByPath[item.path].orEmpty()
                    Box(Modifier.weight(1f)) {
                        MediaTile(
                            medium = medium,
                            isVideo = isVideo,
                            isSelected = item.path in data.selectedFilePaths,
                            isSelectionMode = data.hasFileSelection,
                            hasTag = itemTags.isNotEmpty(),
                            tags = itemTags,
                            onTagClick = if (!data.hasFileSelection) callbacks.onTagClick else null,
                            showOverlays = true,
                            aspectRatio = 1f,
                            cornerShape = metrics.cornerShape,
                            cardColor = metrics.mediaBg,
                            itemSpacing = mediaSettings.spacing.dp,
                            showFileName = mediaSettings.showFileNames,
                            reserveGridCaptionSpace = mediaSettings.showFileNames,
                            showVideoDuration = ThumbnailOverlayState.showVideoDuration,
                            showRating = ThumbnailOverlayState.showRating,
                            markFavorite = true,
                            isNarrowTile = metrics.isNarrowMediaTile,
                            onClick = { callbacks.onFileClick(item) },
                            onLongClick = { callbacks.onFileLongClick(item) },
                            onSwipeToSelect = {},
                            onBoundsChanged = {},
                        )
                        if (data.hasFileSelection) {
                            IconButton(
                                onClick = { peekState.show(item.path, isVideo) },
                                modifier = Modifier.align(Alignment.TopEnd).size(48.dp),
                            ) {
                                Icon(
                                    Icons.Default.ZoomIn,
                                    stringResource(R.string.cd_preview),
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
                repeat(config.mediaGridColumns - chunk.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private fun LazyListScope.explorerMediaMosaicChunk(
    sectionKey: String,
    groupFileItems: List<ExplorerItem>,
    data: ExplorerListContentData,
    config: ExplorerListDisplayConfig,
    metrics: ExplorerRenderMetrics,
    peekState: PeekState,
    callbacks: ExplorerListCallbacks,
) {
    val mediaSettings = config.mediaSettings
    groupFileItems.chunked(EXPLORER_MOSAIC_CHUNK_SIZE).forEachIndexed { chunkIndex, chunk ->
        item(key = "explorer_media_mosaic_${sectionKey}_$chunkIndex", contentType = "media_mosaic_chunk") {
            ExplorerMosaicChunk(
                items = chunk,
                tagsByPath = data.fileTagsByPath,
                columnCount = config.mediaGridColumns,
                spacing = mediaSettings.spacing.dp,
                cornerShape = metrics.cornerShape,
                displayMode = mediaSettings.displayMode,
                showFileNames = mediaSettings.showFileNames,
                hasFileSelection = data.hasFileSelection,
                selectedFilePaths = data.selectedFilePaths,
                onClick = callbacks.onFileClick,
                onLongClick = callbacks.onFileLongClick,
                onTagClick = callbacks.onTagClick,
                onPreviewClick = { item ->
                    val isVideo = item.type == 2 ||
                        item.path.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS
                    peekState.show(item.path, isVideo)
                },
            )
        }
    }
}

private fun LazyListScope.explorerMediaListChunk(
    sectionKey: String,
    groupFileItems: List<ExplorerItem>,
    data: ExplorerListContentData,
    config: ExplorerListDisplayConfig,
    metrics: ExplorerRenderMetrics,
    peekState: PeekState,
    callbacks: ExplorerListCallbacks,
) {
    val mediaSettings = config.mediaSettings
    val listCompact = mediaSettings.listDensity == ListDensity.COMPACT
    val mediaListHalfGap = (((mediaSettings.spacing - 2) / 2).coerceAtLeast(0) / 2f).dp
    items(
        items = groupFileItems,
        key = { "$sectionKey/${it.path}" },
        contentType = { "explorer_media_row" },
    ) { item ->
        val isVideo = item.type == 2 ||
            item.path.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS
        val medium = remember(item) { item.toMediumForGrouping() }
        val itemTags = data.fileTagsByPath[item.path].orEmpty()
        val isSelected = item.path in data.selectedFilePaths
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = mediaListHalfGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MediaListRow(
                medium = medium,
                isVideo = isVideo,
                isSelected = isSelected,
                hasSelection = data.hasFileSelection,
                cardColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else metrics.mediaBg,
                fileSizeLabel = remember(item.size) { formatFileSize(item.size) },
                onClick = { callbacks.onFileClick(item) },
                onLongClick = { callbacks.onFileLongClick(item) },
                onSwipeToSelect = {},
                onBoundsChanged = {},
                modifier = Modifier.weight(1f),
                showRating = ThumbnailOverlayState.showRating,
                showVideoDuration = ThumbnailOverlayState.showVideoDuration,
                compact = listCompact,
                roundedCorners = mediaSettings.roundedCorners,
                hasTag = itemTags.isNotEmpty(),
                tags = itemTags,
                onTagClick = if (!data.hasFileSelection) callbacks.onTagClick else null,
                markFavorite = true,
            )
            if (data.hasFileSelection) {
                IconButton(onClick = { peekState.show(item.path, isVideo) }, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Default.ZoomIn,
                        stringResource(R.string.cd_preview),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

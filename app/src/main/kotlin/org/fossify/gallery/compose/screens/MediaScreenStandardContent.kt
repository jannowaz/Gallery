package org.fossify.gallery.compose.screens

import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.fossify.gallery.R
import org.fossify.gallery.compose.components.EmptyState
import org.fossify.gallery.compose.components.FastScrollBar
import org.fossify.gallery.compose.components.FloatingSectionLabel
import org.fossify.gallery.compose.components.MediaListRow
import org.fossify.gallery.compose.components.MediaTile
import org.fossify.gallery.compose.components.SectionHeader
import org.fossify.gallery.compose.components.tagAccentColor
import org.fossify.gallery.compose.util.ColumnCountIndicator
import org.fossify.gallery.compose.util.ScrollToTopEffect
import org.fossify.gallery.compose.util.SelectionDragState
import org.fossify.gallery.compose.util.ThumbnailOverlayState
import org.fossify.gallery.compose.util.dragSelectionGesture
import org.fossify.gallery.compose.util.pinchToChangeColumns
import org.fossify.gallery.helpers.GroupRow
import org.fossify.gallery.helpers.TYPE_VIDEOS
import org.fossify.gallery.helpers.VIDEO_EXTENSIONS
import org.fossify.gallery.helpers.currentBreadcrumb
import org.fossify.gallery.models.Medium
import org.fossify.gallery.viewmodels.MediaUiState
import kotlin.math.abs

private const val MIN_PINCH_COLUMNS = 2
private const val MAX_PINCH_COLUMNS = 6
private const val AUTO_SCROLL_SPEED_THRESHOLD = 0.5f
private const val AUTO_SCROLL_IDLE_DELAY_MS = 50L
private const val GRID_AUTO_SCROLL_BASE_MS = 100f
private const val GRID_AUTO_SCROLL_MIN_MS = 50L
private const val GRID_AUTO_SCROLL_MAX_MS = 150L
private const val MOSAIC_AUTO_SCROLL_BASE_MS = 120f
private const val MOSAIC_AUTO_SCROLL_MIN_MS = 60L
private const val MOSAIC_AUTO_SCROLL_MAX_MS = 200L
private const val MOSAIC_ASPECT_SETTLE_DELAY_MS = 120L

@Composable
internal fun MediaScreenStandardContent(
    state: MediaUiState,
    viewSettings: ViewSettings,
    columnCount: Int,
    itemSpacing: Dp,
    contentTopInset: Dp,
    isGrid: Boolean,
    isMosaic: Boolean,
    fullListTagGrouping: Boolean,
    initialOverrideMosaicPaths: List<String>,
    initialOverrideMosaicReady: Boolean,
    displayMedia: List<Medium>,
    hasFilter: Boolean,
    isBuildingGroupRows: Boolean,
    completeGroupRows: List<GroupRow>,
    groupRows: List<GroupRow>,
    pathIndexMap: Map<String, Int>,
    selectedPaths: Set<String>,
    hasSelection: Boolean,
    pickMultiple: Boolean,
    taggedPaths: Set<String>,
    cornerShape: Shape,
    mediaCardColor: Color,
    cropThumbnails: Boolean,
    showThumbnailFileTypes: Boolean,
    markFavoriteItems: Boolean,
    isNarrowMediaTile: Boolean,
    dragSelection: SelectionDragState,
    scrollPositionKey: String?,
    restoredScrollPosition: MediaScrollPosition,
    initialScrollIndex: Int,
    initialScrollOffset: Int,
    scrollItemIndexByPath: Map<String, Int>,
    scrollKeyToPath: Map<String, String>,
    tabIndex: Int?,
    onClearFilter: () -> Unit,
    onSaveScrollPosition: (Int, Int, String) -> Unit,
    onReportFirstVisiblePath: (String?) -> Unit,
    onToggleGroupKey: (String) -> Unit,
    onSelectGroup: (GroupRow.SectionHeader) -> Unit,
    onSelectDisplayItem: (String, Int, Boolean) -> Unit,
    onOpenViewer: (Int) -> Unit,
    onAddSelectedPath: (String) -> Unit,
    onRequestAspect: (String) -> Unit,
    onTagClick: (String) -> Unit,
    onColumnCountChange: ((Int) -> Unit)?,
) {
    var didRestoreSemanticScroll by remember(scrollPositionKey) { mutableStateOf(false) }
    var showColumnIndicator by remember { mutableStateOf(false) }

    when {
        fullListTagGrouping && state.isFullListGroupingLoading -> MediaSkeleton(
            columns = columnCount,
            viewType = viewSettings.viewType,
            itemSpacing = itemSpacing,
            showFileName = viewSettings.showFileNames,
            reserveGridCaptionSpace = false,
            listDensity = viewSettings.listDensity,
        )
        isMosaic && initialOverrideMosaicPaths.isNotEmpty() && !initialOverrideMosaicReady ->
            MediaSkeleton(
                columns = columnCount,
                viewType = ViewType.MOSAIC,
                itemSpacing = itemSpacing,
                showFileName = viewSettings.showFileNames,
            )
        displayMedia.isEmpty() -> StandardEmptyState(
            hasFilter = hasFilter,
            onClearFilter = onClearFilter,
        )
        isBuildingGroupRows && completeGroupRows.isEmpty() -> MediaSkeleton(
            columns = columnCount,
            viewType = viewSettings.viewType,
            itemSpacing = itemSpacing,
            showFileName = viewSettings.showFileNames,
            reserveGridCaptionSpace = isGrid,
            listDensity = viewSettings.listDensity,
        )
        isGrid -> MediaStandardGrid(
            state = state,
            viewSettings = viewSettings,
            columnCount = columnCount,
            itemSpacing = itemSpacing,
            contentTopInset = contentTopInset,
            groupRows = groupRows,
            pathIndexMap = pathIndexMap,
            selectedPaths = selectedPaths,
            hasSelection = hasSelection,
            pickMultiple = pickMultiple,
            taggedPaths = taggedPaths,
            cornerShape = cornerShape,
            mediaCardColor = mediaCardColor,
            cropThumbnails = cropThumbnails,
            showThumbnailFileTypes = showThumbnailFileTypes,
            markFavoriteItems = markFavoriteItems,
            isNarrowMediaTile = isNarrowMediaTile,
            dragSelection = dragSelection,
            scrollPositionKey = scrollPositionKey,
            restoredScrollPosition = restoredScrollPosition,
            didRestoreSemanticScroll = didRestoreSemanticScroll,
            onMarkSemanticScrollRestore = { didRestoreSemanticScroll = true },
            initialScrollIndex = initialScrollIndex,
            initialScrollOffset = initialScrollOffset,
            scrollItemIndexByPath = scrollItemIndexByPath,
            scrollKeyToPath = scrollKeyToPath,
            tabIndex = tabIndex,
            showColumnIndicator = showColumnIndicator,
            onShowColumnIndicator = { showColumnIndicator = it },
            onSaveScrollPosition = onSaveScrollPosition,
            onReportFirstVisiblePath = onReportFirstVisiblePath,
            onToggleGroupKey = onToggleGroupKey,
            onSelectGroup = onSelectGroup,
            onSelectDisplayItem = onSelectDisplayItem,
            onOpenViewer = onOpenViewer,
            onAddSelectedPath = onAddSelectedPath,
            onTagClick = onTagClick,
            onColumnCountChange = onColumnCountChange,
        )
        isMosaic -> MediaStandardMosaic(
            state = state,
            viewSettings = viewSettings,
            columnCount = columnCount,
            itemSpacing = itemSpacing,
            contentTopInset = contentTopInset,
            groupRows = groupRows,
            pathIndexMap = pathIndexMap,
            selectedPaths = selectedPaths,
            hasSelection = hasSelection,
            pickMultiple = pickMultiple,
            taggedPaths = taggedPaths,
            cornerShape = cornerShape,
            mediaCardColor = mediaCardColor,
            cropThumbnails = cropThumbnails,
            showThumbnailFileTypes = showThumbnailFileTypes,
            markFavoriteItems = markFavoriteItems,
            isNarrowMediaTile = isNarrowMediaTile,
            dragSelection = dragSelection,
            scrollPositionKey = scrollPositionKey,
            restoredScrollPosition = restoredScrollPosition,
            didRestoreSemanticScroll = didRestoreSemanticScroll,
            onMarkSemanticScrollRestore = { didRestoreSemanticScroll = true },
            initialScrollIndex = initialScrollIndex,
            initialScrollOffset = initialScrollOffset,
            scrollItemIndexByPath = scrollItemIndexByPath,
            scrollKeyToPath = scrollKeyToPath,
            tabIndex = tabIndex,
            showColumnIndicator = showColumnIndicator,
            onShowColumnIndicator = { showColumnIndicator = it },
            onSaveScrollPosition = onSaveScrollPosition,
            onReportFirstVisiblePath = onReportFirstVisiblePath,
            onToggleGroupKey = onToggleGroupKey,
            onSelectGroup = onSelectGroup,
            onSelectDisplayItem = onSelectDisplayItem,
            onOpenViewer = onOpenViewer,
            onAddSelectedPath = onAddSelectedPath,
            onRequestAspect = onRequestAspect,
            onTagClick = onTagClick,
            onColumnCountChange = onColumnCountChange,
        )
        else -> MediaStandardList(
            state = state,
            viewSettings = viewSettings,
            contentTopInset = contentTopInset,
            groupRows = groupRows,
            pathIndexMap = pathIndexMap,
            selectedPaths = selectedPaths,
            hasSelection = hasSelection,
            pickMultiple = pickMultiple,
            taggedPaths = taggedPaths,
            mediaCardColor = mediaCardColor,
            markFavoriteItems = markFavoriteItems,
            dragSelection = dragSelection,
            scrollPositionKey = scrollPositionKey,
            restoredScrollPosition = restoredScrollPosition,
            didRestoreSemanticScroll = didRestoreSemanticScroll,
            onMarkSemanticScrollRestore = { didRestoreSemanticScroll = true },
            initialScrollIndex = initialScrollIndex,
            initialScrollOffset = initialScrollOffset,
            scrollItemIndexByPath = scrollItemIndexByPath,
            scrollKeyToPath = scrollKeyToPath,
            tabIndex = tabIndex,
            onSaveScrollPosition = onSaveScrollPosition,
            onReportFirstVisiblePath = onReportFirstVisiblePath,
            onToggleGroupKey = onToggleGroupKey,
            onSelectGroup = onSelectGroup,
            onSelectDisplayItem = onSelectDisplayItem,
            onOpenViewer = onOpenViewer,
            onAddSelectedPath = onAddSelectedPath,
            onTagClick = onTagClick,
        )
    }
}

@Composable
private fun MediaStandardGrid(
    state: MediaUiState,
    viewSettings: ViewSettings,
    columnCount: Int,
    itemSpacing: Dp,
    contentTopInset: Dp,
    groupRows: List<GroupRow>,
    pathIndexMap: Map<String, Int>,
    selectedPaths: Set<String>,
    hasSelection: Boolean,
    pickMultiple: Boolean,
    taggedPaths: Set<String>,
    cornerShape: Shape,
    mediaCardColor: Color,
    cropThumbnails: Boolean,
    showThumbnailFileTypes: Boolean,
    markFavoriteItems: Boolean,
    isNarrowMediaTile: Boolean,
    dragSelection: SelectionDragState,
    scrollPositionKey: String?,
    restoredScrollPosition: MediaScrollPosition,
    didRestoreSemanticScroll: Boolean,
    onMarkSemanticScrollRestore: () -> Unit,
    initialScrollIndex: Int,
    initialScrollOffset: Int,
    scrollItemIndexByPath: Map<String, Int>,
    scrollKeyToPath: Map<String, String>,
    tabIndex: Int?,
    showColumnIndicator: Boolean,
    onShowColumnIndicator: (Boolean) -> Unit,
    onSaveScrollPosition: (Int, Int, String) -> Unit,
    onReportFirstVisiblePath: (String?) -> Unit,
    onToggleGroupKey: (String) -> Unit,
    onSelectGroup: (GroupRow.SectionHeader) -> Unit,
    onSelectDisplayItem: (String, Int, Boolean) -> Unit,
    onOpenViewer: (Int) -> Unit,
    onAddSelectedPath: (String) -> Unit,
    onTagClick: (String) -> Unit,
    onColumnCountChange: ((Int) -> Unit)?,
) {
    val gridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = initialScrollIndex,
        initialFirstVisibleItemScrollOffset = initialScrollOffset,
    )
    StandardScrollPositionEffect(
        stateKey = gridState,
        groupRows = groupRows,
        scrollPositionKey = scrollPositionKey,
        restoredScrollPosition = restoredScrollPosition,
        didRestoreSemanticScroll = didRestoreSemanticScroll,
        scrollItemIndexByPath = scrollItemIndexByPath,
        scrollKeyToPath = scrollKeyToPath,
        firstVisibleIndex = { gridState.firstVisibleItemIndex },
        firstVisibleOffset = { gridState.firstVisibleItemScrollOffset },
        visibleItemKeys = { gridState.layoutInfo.visibleItemsInfo.asSequence().map { it.key } },
        scrollToItem = { idx, offset -> gridState.scrollToItem(idx, offset) },
        onMarkSemanticScrollRestore = onMarkSemanticScrollRestore,
        onSaveScrollPosition = onSaveScrollPosition,
        onReportFirstVisiblePath = onReportFirstVisiblePath,
    )
    StandardDragAutoScrollEffect(
        dragSelection = dragSelection,
        firstVisibleIndex = { gridState.firstVisibleItemIndex },
        totalItemsCount = { gridState.layoutInfo.totalItemsCount },
        animateScrollToItem = { idx -> gridState.animateScrollToItem(idx, 0) },
        baseMs = GRID_AUTO_SCROLL_BASE_MS,
        minMs = GRID_AUTO_SCROLL_MIN_MS,
        maxMs = GRID_AUTO_SCROLL_MAX_MS,
    )
    ScrollToTopEffect(tabIndex) { gridState.animateScrollToItem(0) }
    val isScrolling by remember { derivedStateOf { gridState.isScrollInProgress } }

    Column(Modifier.padding(top = contentTopInset)) {
        CompositionLocalProvider(LocalOverscrollFactory provides null) {
            Box(
                Modifier
                    .pinchToChangeColumns(
                        enabled = onColumnCountChange != null && !hasSelection,
                        currentColumns = viewSettings.columnCount,
                        minColumns = MIN_PINCH_COLUMNS,
                        maxColumns = MAX_PINCH_COLUMNS,
                        onColumnsChange = { next ->
                            onColumnCountChange?.invoke(next)
                            onShowColumnIndicator(true)
                        },
                    )
                    .dragSelectionGesture(
                        dragSelection,
                        enabled = false,
                        gridState = gridState,
                        onSelectPath = onAddSelectedPath,
                        onLongPressSelectPath = null,
                    ),
            ) {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(columnCount),
                    reverseLayout = viewSettings.anchorBottom,
                    contentPadding = PaddingValues(itemSpacing / 2),
                ) {
                    groupRows.forEach { row ->
                        when (row) {
                            is GroupRow.SectionHeader -> item(
                                key = "hdr_${row.key}",
                                span = { GridItemSpan(maxLineSpan) },
                                contentType = "header",
                            ) {
                                StandardSectionHeader(
                                    row = row,
                                    onToggleGroupKey = onToggleGroupKey,
                                    onSelectGroup = onSelectGroup,
                                )
                            }
                            is GroupRow.Items -> {
                                val groupItems = row.media
                                items(
                                    groupItems.size,
                                    key = { "${row.sectionKey}/${groupItems[it].path}" },
                                    contentType = { groupItems[it].type },
                                ) { idx ->
                                    val m = groupItems[idx]
                                    val originalIdx = pathIndexMap[m.path] ?: 0
                                    val isVideo = rememberIsVideo(m)
                                    val isSelected by remember(m.path, selectedPaths) {
                                        derivedStateOf { m.path in selectedPaths }
                                    }
                                    MediaTile(
                                        medium = m,
                                        isVideo = isVideo,
                                        isSelected = isSelected,
                                        isSelectionMode = hasSelection,
                                        hasTag = m.path in taggedPaths,
                                        tags = state.tagsByPath[m.path].orEmpty(),
                                        onTagClick =
                                            if (!hasSelection && !pickMultiple) onTagClick else null,
                                        showOverlays = true,
                                        aspectRatio = 1f,
                                        cornerShape = cornerShape,
                                        cardColor = mediaCardColor,
                                        itemSpacing = itemSpacing,
                                        showFileName = viewSettings.showFileNames,
                                        reserveGridCaptionSpace = false,
                                        showVideoDuration = ThumbnailOverlayState.showVideoDuration,
                                        cropThumbnails = cropThumbnails,
                                        showRating = ThumbnailOverlayState.showRating,
                                        showFileType = showThumbnailFileTypes,
                                        markFavorite = markFavoriteItems,
                                        isNarrowTile = isNarrowMediaTile,
                                        onClick = {
                                            if (hasSelection || pickMultiple) {
                                                onSelectDisplayItem(m.path, originalIdx, false)
                                            } else {
                                                onOpenViewer(originalIdx)
                                            }
                                        },
                                        onLongClick = {
                                            onSelectDisplayItem(m.path, originalIdx, true)
                                        },
                                        onSwipeToSelect = { onAddSelectedPath(m.path) },
                                        onBoundsChanged = { r ->
                                            dragSelection.registerItemBounds(m.path, r)
                                        },
                                        onBoundsRemoved = {
                                            dragSelection.unregisterItemBounds(m.path)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
                val breadcrumb by remember(groupRows) {
                    derivedStateOf { currentBreadcrumb(groupRows, gridState.firstVisibleItemIndex) }
                }
                FloatingSectionLabel(
                    breadcrumb,
                    isScrolling,
                    Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
                )
                ColumnCountIndicator(
                    columnCount = viewSettings.columnCount,
                    visible = showColumnIndicator,
                    onAutoHide = { onShowColumnIndicator(false) },
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
                )
                FastScrollBar(
                    state = gridState,
                    positionLabel = { index -> currentBreadcrumb(groupRows, index) },
                    modifier = Modifier.align(Alignment.CenterEnd).padding(vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun MediaStandardMosaic(
    state: MediaUiState,
    viewSettings: ViewSettings,
    columnCount: Int,
    itemSpacing: Dp,
    contentTopInset: Dp,
    groupRows: List<GroupRow>,
    pathIndexMap: Map<String, Int>,
    selectedPaths: Set<String>,
    hasSelection: Boolean,
    pickMultiple: Boolean,
    taggedPaths: Set<String>,
    cornerShape: Shape,
    mediaCardColor: Color,
    cropThumbnails: Boolean,
    showThumbnailFileTypes: Boolean,
    markFavoriteItems: Boolean,
    isNarrowMediaTile: Boolean,
    dragSelection: SelectionDragState,
    scrollPositionKey: String?,
    restoredScrollPosition: MediaScrollPosition,
    didRestoreSemanticScroll: Boolean,
    onMarkSemanticScrollRestore: () -> Unit,
    initialScrollIndex: Int,
    initialScrollOffset: Int,
    scrollItemIndexByPath: Map<String, Int>,
    scrollKeyToPath: Map<String, String>,
    tabIndex: Int?,
    showColumnIndicator: Boolean,
    onShowColumnIndicator: (Boolean) -> Unit,
    onSaveScrollPosition: (Int, Int, String) -> Unit,
    onReportFirstVisiblePath: (String?) -> Unit,
    onToggleGroupKey: (String) -> Unit,
    onSelectGroup: (GroupRow.SectionHeader) -> Unit,
    onSelectDisplayItem: (String, Int, Boolean) -> Unit,
    onOpenViewer: (Int) -> Unit,
    onAddSelectedPath: (String) -> Unit,
    onRequestAspect: (String) -> Unit,
    onTagClick: (String) -> Unit,
    onColumnCountChange: ((Int) -> Unit)?,
) {
    val mosaicState = rememberLazyStaggeredGridState(
        initialFirstVisibleItemIndex = initialScrollIndex,
        initialFirstVisibleItemScrollOffset = initialScrollOffset,
    )
    StandardScrollPositionEffect(
        stateKey = mosaicState,
        groupRows = groupRows,
        scrollPositionKey = scrollPositionKey,
        restoredScrollPosition = restoredScrollPosition,
        didRestoreSemanticScroll = didRestoreSemanticScroll,
        scrollItemIndexByPath = scrollItemIndexByPath,
        scrollKeyToPath = scrollKeyToPath,
        firstVisibleIndex = { mosaicState.firstVisibleItemIndex },
        firstVisibleOffset = { mosaicState.firstVisibleItemScrollOffset },
        visibleItemKeys = { mosaicState.layoutInfo.visibleItemsInfo.asSequence().map { it.key } },
        scrollToItem = { idx, offset -> mosaicState.scrollToItem(idx, offset) },
        onMarkSemanticScrollRestore = onMarkSemanticScrollRestore,
        onSaveScrollPosition = onSaveScrollPosition,
        onReportFirstVisiblePath = onReportFirstVisiblePath,
    )
    StandardDragAutoScrollEffect(
        dragSelection = dragSelection,
        firstVisibleIndex = { mosaicState.firstVisibleItemIndex },
        totalItemsCount = { mosaicState.layoutInfo.totalItemsCount },
        animateScrollToItem = { idx -> mosaicState.animateScrollToItem(idx, 0) },
        baseMs = MOSAIC_AUTO_SCROLL_BASE_MS,
        minMs = MOSAIC_AUTO_SCROLL_MIN_MS,
        maxMs = MOSAIC_AUTO_SCROLL_MAX_MS,
    )
    ScrollToTopEffect(tabIndex) { mosaicState.animateScrollToItem(0) }
    val isScrolling by remember { derivedStateOf { mosaicState.isScrollInProgress } }
    var appliedAspectRatios by remember { mutableStateOf(state.aspectRatios) }
    LaunchedEffect(state.aspectRatios, isScrolling) {
        if (!isScrolling) {
            delay(MOSAIC_ASPECT_SETTLE_DELAY_MS)
            appliedAspectRatios = state.aspectRatios
        }
    }

    Column(Modifier.padding(top = contentTopInset)) {
        CompositionLocalProvider(LocalOverscrollFactory provides null) {
            Box(
                Modifier
                    .pinchToChangeColumns(
                        enabled = onColumnCountChange != null && !hasSelection,
                        currentColumns = viewSettings.columnCount,
                        minColumns = MIN_PINCH_COLUMNS,
                        maxColumns = MAX_PINCH_COLUMNS,
                        onColumnsChange = { next ->
                            onColumnCountChange?.invoke(next)
                            onShowColumnIndicator(true)
                        },
                    )
                    .dragSelectionGesture(
                        dragSelection,
                        enabled = false,
                        staggeredGridState = mosaicState,
                        onSelectPath = onAddSelectedPath,
                        onLongPressSelectPath = null,
                    ),
            ) {
                LazyVerticalStaggeredGrid(
                    state = mosaicState,
                    columns = StaggeredGridCells.Fixed(columnCount),
                    reverseLayout = viewSettings.anchorBottom,
                    contentPadding = PaddingValues(itemSpacing / 2),
                ) {
                    groupRows.forEach { row ->
                        when (row) {
                            is GroupRow.SectionHeader -> item(
                                key = "hdr_${row.key}",
                                span = StaggeredGridItemSpan.FullLine,
                                contentType = "header",
                            ) {
                                StandardSectionHeader(
                                    row = row,
                                    onToggleGroupKey = onToggleGroupKey,
                                    onSelectGroup = onSelectGroup,
                                )
                            }
                            is GroupRow.Items -> {
                                val groupItems = row.media
                                items(
                                    groupItems.size,
                                    key = { "${row.sectionKey}/${groupItems[it].path}" },
                                    contentType = { groupItems[it].type },
                                ) { idx ->
                                    val m = groupItems[idx]
                                    val originalIdx = pathIndexMap[m.path] ?: 0
                                    val isVideo = rememberIsVideo(m)
                                    val latestOnRequestAspect by rememberUpdatedState(onRequestAspect)
                                    if (!isVideo) {
                                        LaunchedEffect(m.path) { latestOnRequestAspect(m.path) }
                                    }
                                    val isSelected by remember(m.path, selectedPaths) {
                                        derivedStateOf { m.path in selectedPaths }
                                    }
                                    val aspectRatio = if (isVideo) 1f else appliedAspectRatios[m.path] ?: 1f
                                    val tileTagClick = if (hasSelection || pickMultiple) null else onTagClick
                                    MediaTile(
                                        medium = m,
                                        isVideo = isVideo,
                                        isSelected = isSelected,
                                        isSelectionMode = hasSelection,
                                        hasTag = m.path in taggedPaths,
                                        tags = state.tagsByPath[m.path].orEmpty(),
                                        onTagClick = tileTagClick,
                                        showOverlays = true,
                                        aspectRatio = aspectRatio,
                                        cornerShape = cornerShape,
                                        cardColor = mediaCardColor,
                                        itemSpacing = itemSpacing,
                                        showFileName = viewSettings.showFileNames,
                                        showVideoDuration = ThumbnailOverlayState.showVideoDuration,
                                        cropThumbnails = cropThumbnails,
                                        showRating = ThumbnailOverlayState.showRating,
                                        showFileType = showThumbnailFileTypes,
                                        markFavorite = markFavoriteItems,
                                        isNarrowTile = isNarrowMediaTile,
                                        onClick = {
                                            handleStandardItemClick(
                                                path = m.path,
                                                originalIdx = originalIdx,
                                                useSelection = hasSelection || pickMultiple,
                                                onSelectDisplayItem = onSelectDisplayItem,
                                                onOpenViewer = onOpenViewer,
                                            )
                                        },
                                        onLongClick = {
                                            onSelectDisplayItem(m.path, originalIdx, true)
                                        },
                                        onSwipeToSelect = { onAddSelectedPath(m.path) },
                                        onBoundsChanged = { r ->
                                            dragSelection.registerItemBounds(m.path, r)
                                        },
                                        onBoundsRemoved = {
                                            dragSelection.unregisterItemBounds(m.path)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
                val breadcrumb by remember(groupRows) {
                    derivedStateOf {
                        currentBreadcrumb(groupRows, mosaicState.firstVisibleItemIndex)
                    }
                }
                FloatingSectionLabel(
                    breadcrumb,
                    isScrolling,
                    Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
                )
                ColumnCountIndicator(
                    columnCount = viewSettings.columnCount,
                    visible = showColumnIndicator,
                    onAutoHide = { onShowColumnIndicator(false) },
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
                )
                FastScrollBar(
                    state = mosaicState,
                    positionLabel = { index -> currentBreadcrumb(groupRows, index) },
                    modifier = Modifier.align(Alignment.CenterEnd).padding(vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun MediaStandardList(
    state: MediaUiState,
    viewSettings: ViewSettings,
    contentTopInset: Dp,
    groupRows: List<GroupRow>,
    pathIndexMap: Map<String, Int>,
    selectedPaths: Set<String>,
    hasSelection: Boolean,
    pickMultiple: Boolean,
    taggedPaths: Set<String>,
    mediaCardColor: Color,
    markFavoriteItems: Boolean,
    dragSelection: SelectionDragState,
    scrollPositionKey: String?,
    restoredScrollPosition: MediaScrollPosition,
    didRestoreSemanticScroll: Boolean,
    onMarkSemanticScrollRestore: () -> Unit,
    initialScrollIndex: Int,
    initialScrollOffset: Int,
    scrollItemIndexByPath: Map<String, Int>,
    scrollKeyToPath: Map<String, String>,
    tabIndex: Int?,
    onSaveScrollPosition: (Int, Int, String) -> Unit,
    onReportFirstVisiblePath: (String?) -> Unit,
    onToggleGroupKey: (String) -> Unit,
    onSelectGroup: (GroupRow.SectionHeader) -> Unit,
    onSelectDisplayItem: (String, Int, Boolean) -> Unit,
    onOpenViewer: (Int) -> Unit,
    onAddSelectedPath: (String) -> Unit,
    onTagClick: (String) -> Unit,
) {
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialScrollIndex,
        initialFirstVisibleItemScrollOffset = initialScrollOffset,
    )
    StandardScrollPositionEffect(
        stateKey = listState,
        groupRows = groupRows,
        scrollPositionKey = scrollPositionKey,
        restoredScrollPosition = restoredScrollPosition,
        didRestoreSemanticScroll = didRestoreSemanticScroll,
        scrollItemIndexByPath = scrollItemIndexByPath,
        scrollKeyToPath = scrollKeyToPath,
        firstVisibleIndex = { listState.firstVisibleItemIndex },
        firstVisibleOffset = { listState.firstVisibleItemScrollOffset },
        visibleItemKeys = { listState.layoutInfo.visibleItemsInfo.asSequence().map { it.key } },
        scrollToItem = { idx, offset -> listState.scrollToItem(idx, offset) },
        onMarkSemanticScrollRestore = onMarkSemanticScrollRestore,
        onSaveScrollPosition = onSaveScrollPosition,
        onReportFirstVisiblePath = onReportFirstVisiblePath,
    )
    ScrollToTopEffect(tabIndex) { listState.animateScrollToItem(0) }
    val listGap = ((viewSettings.spacing - 2) / 2).coerceAtLeast(0).dp
    val rowTagClick = if (hasSelection || pickMultiple) null else onTagClick

    Column(Modifier.padding(top = contentTopInset)) {
        Box(
            Modifier
                .clipToBounds()
                .dragSelectionGesture(
                    dragSelection,
                    enabled = false,
                    onSelectPath = onAddSelectedPath,
                    onLongPressSelectPath = null,
                ),
        ) {
            LazyColumn(
                state = listState,
                reverseLayout = viewSettings.anchorBottom,
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(listGap),
            ) {
                groupRows.forEach { row ->
                    when (row) {
                        is GroupRow.SectionHeader -> stickyHeader(
                            key = "hdr_${row.key}",
                            contentType = "header",
                        ) {
                            StandardSectionHeader(
                                row = row,
                                showGuideLine = true,
                                onToggleGroupKey = onToggleGroupKey,
                                onSelectGroup = onSelectGroup,
                            )
                        }
                        is GroupRow.Items -> {
                            val groupItems = row.media
                            items(
                                groupItems.size,
                                key = { "${row.sectionKey}/${groupItems[it].path}" },
                                contentType = { groupItems[it].type },
                            ) { idx ->
                                val m = groupItems[idx]
                                val originalIdx = pathIndexMap[m.path] ?: 0
                                val isVideo = rememberIsVideo(m)
                                val isSelected by remember(m.path, selectedPaths) {
                                    derivedStateOf { m.path in selectedPaths }
                                }
                                MediaListRow(
                                    medium = m,
                                    isVideo = isVideo,
                                    isSelected = isSelected,
                                    hasSelection = hasSelection,
                                    cardColor = mediaCardColor,
                                    fileSizeLabel = remember(m.size) { formatFileSize(m.size) },
                                    onClick = {
                                        handleStandardItemClick(
                                            path = m.path,
                                            originalIdx = originalIdx,
                                            useSelection = hasSelection || pickMultiple,
                                            onSelectDisplayItem = onSelectDisplayItem,
                                            onOpenViewer = onOpenViewer,
                                        )
                                    },
                                    onLongClick = {
                                        onSelectDisplayItem(m.path, originalIdx, true)
                                    },
                                    onSwipeToSelect = { onAddSelectedPath(m.path) },
                                    onBoundsChanged = { r ->
                                        dragSelection.registerItemBounds(m.path, r)
                                    },
                                    onBoundsRemoved = {
                                        dragSelection.unregisterItemBounds(m.path)
                                    },
                                    showRating = ThumbnailOverlayState.showRating,
                                    showVideoDuration = ThumbnailOverlayState.showVideoDuration,
                                    compact = viewSettings.listDensity == ListDensity.COMPACT,
                                    roundedCorners = viewSettings.roundedCorners,
                                    hasTag = m.path in taggedPaths,
                                    tags = state.tagsByPath[m.path].orEmpty(),
                                    onTagClick = rowTagClick,
                                    markFavorite = markFavoriteItems,
                                )
                            }
                        }
                    }
                }
            }
            FastScrollBar(
                state = listState,
                positionLabel = { index -> currentBreadcrumb(groupRows, index) },
                modifier = Modifier.align(Alignment.CenterEnd).padding(vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun StandardEmptyState(
    hasFilter: Boolean,
    onClearFilter: () -> Unit,
) {
    EmptyState(
        icon = if (hasFilter) Icons.Default.SearchOff else Icons.Default.Search,
        title = stringResource(if (hasFilter) R.string.no_results else R.string.no_media_found),
        subtitle = stringResource(
            if (hasFilter) R.string.no_results_hint else R.string.no_media_found_hint,
        ),
        actionLabel = if (hasFilter) stringResource(R.string.clear_filter) else null,
        onAction = if (hasFilter) onClearFilter else null,
    )
}

@Composable
private fun StandardSectionHeader(
    row: GroupRow.SectionHeader,
    onToggleGroupKey: (String) -> Unit,
    onSelectGroup: (GroupRow.SectionHeader) -> Unit,
    showGuideLine: Boolean = false,
) {
    SectionHeader(
        label = row.label,
        count = if (row.isExpanded) row.exactCount else row.totalCount,
        depth = row.depth,
        hasChildren = row.hasChildren,
        isExpanded = row.isExpanded,
        onToggle = { onToggleGroupKey(row.key) },
        ratingValue = row.ratingValue,
        accentColor = row.rootKey?.let { tagAccentColor(it) },
        showGuideLine = showGuideLine,
        onSelectGroup = { onSelectGroup(row) },
    )
}

private fun handleStandardItemClick(
    path: String,
    originalIdx: Int,
    useSelection: Boolean,
    onSelectDisplayItem: (String, Int, Boolean) -> Unit,
    onOpenViewer: (Int) -> Unit,
) {
    if (useSelection) {
        onSelectDisplayItem(path, originalIdx, false)
    } else {
        onOpenViewer(originalIdx)
    }
}

@Composable
private fun StandardScrollPositionEffect(
    stateKey: Any,
    groupRows: List<GroupRow>,
    scrollPositionKey: String?,
    restoredScrollPosition: MediaScrollPosition,
    didRestoreSemanticScroll: Boolean,
    scrollItemIndexByPath: Map<String, Int>,
    scrollKeyToPath: Map<String, String>,
    firstVisibleIndex: () -> Int,
    firstVisibleOffset: () -> Int,
    visibleItemKeys: () -> Sequence<Any>,
    scrollToItem: suspend (Int, Int) -> Unit,
    onMarkSemanticScrollRestore: () -> Unit,
    onSaveScrollPosition: (Int, Int, String) -> Unit,
    onReportFirstVisiblePath: (String?) -> Unit,
) {
    val latestFirstIndex by rememberUpdatedState(firstVisibleIndex)
    val latestFirstOffset by rememberUpdatedState(firstVisibleOffset)
    val latestVisibleKeys by rememberUpdatedState(visibleItemKeys)
    val latestScrollToItem by rememberUpdatedState(scrollToItem)
    val latestOnMarkRestored by rememberUpdatedState(onMarkSemanticScrollRestore)
    val latestOnSaveScroll by rememberUpdatedState(onSaveScrollPosition)
    val latestOnReportVisible by rememberUpdatedState(onReportFirstVisiblePath)
    LaunchedEffect(stateKey, groupRows, scrollPositionKey) {
        val anchor = restoredScrollPosition.anchorPath
        if (scrollPositionKey != null && !didRestoreSemanticScroll && anchor.isNotBlank()) {
            scrollItemIndexByPath[anchor]?.let { index ->
                val needsScroll = latestFirstIndex() != index ||
                    latestFirstOffset() != restoredScrollPosition.offset
                if (needsScroll) latestScrollToItem(index, restoredScrollPosition.offset)
                latestOnMarkRestored()
            }
        }
        snapshotFlow {
            val firstVisibleMedia = latestVisibleKeys()
                .mapNotNull { pathForScrollKey(it, scrollKeyToPath) }
                .firstOrNull()
            Triple(latestFirstIndex(), latestFirstOffset(), firstVisibleMedia)
        }.collect { (index, offset, path) ->
            latestOnSaveScroll(index, offset, path.orEmpty())
            latestOnReportVisible(path)
        }
    }
}

@Composable
private fun StandardDragAutoScrollEffect(
    dragSelection: SelectionDragState,
    firstVisibleIndex: () -> Int,
    totalItemsCount: () -> Int,
    animateScrollToItem: suspend (Int) -> Unit,
    baseMs: Float,
    minMs: Long,
    maxMs: Long,
) {
    val latestFirstIndex by rememberUpdatedState(firstVisibleIndex)
    val latestTotalItems by rememberUpdatedState(totalItemsCount)
    val latestAnimateScroll by rememberUpdatedState(animateScrollToItem)
    LaunchedEffect(dragSelection.isDragging) {
        if (!dragSelection.isDragging) return@LaunchedEffect
        var targetIdx = latestFirstIndex()
        while (dragSelection.isDragging) {
            val speed = dragSelection.autoScrollSpeed
            if (speed < -AUTO_SCROLL_SPEED_THRESHOLD) {
                targetIdx = maxOf(targetIdx - 1, 0)
            } else if (speed > AUTO_SCROLL_SPEED_THRESHOLD) {
                val last = (latestTotalItems() - 1).coerceAtLeast(0)
                targetIdx = (targetIdx + 1).coerceAtMost(last)
            } else {
                delay(AUTO_SCROLL_IDLE_DELAY_MS)
                continue
            }
            latestAnimateScroll(targetIdx)
            val delayMs = (baseMs / abs(speed).coerceAtLeast(AUTO_SCROLL_SPEED_THRESHOLD))
                .toLong()
                .coerceIn(minMs, maxMs)
            delay(delayMs)
        }
    }
}

@Composable
private fun rememberIsVideo(medium: Medium): Boolean = remember(medium.path, medium.type) {
    medium.type == TYPE_VIDEOS ||
        medium.path.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS
}

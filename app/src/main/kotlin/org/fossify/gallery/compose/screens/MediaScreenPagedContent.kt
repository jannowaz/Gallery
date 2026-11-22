package org.fossify.gallery.compose.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.fossify.gallery.compose.components.FastScrollBar
import org.fossify.gallery.compose.components.FloatingSectionLabel
import org.fossify.gallery.compose.components.MediaListRow
import org.fossify.gallery.compose.components.MediaTile
import org.fossify.gallery.compose.components.SectionHeader
import org.fossify.gallery.compose.theme.Radius
import org.fossify.gallery.compose.util.MediaSelectionMode
import org.fossify.gallery.compose.util.ScrollToTopEffect
import org.fossify.gallery.compose.util.SelectionDragState
import org.fossify.gallery.compose.util.ThumbnailOverlayState
import org.fossify.gallery.compose.util.dragSelectionGesture
import org.fossify.gallery.compose.util.rememberEffectiveColumnCount
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.GroupBy
import org.fossify.gallery.helpers.GroupRow
import org.fossify.gallery.helpers.GroupSummary
import org.fossify.gallery.helpers.TYPE_VIDEOS
import org.fossify.gallery.helpers.VIDEO_EXTENSIONS
import org.fossify.gallery.helpers.ViewerLoadDirection
import org.fossify.gallery.helpers.ViewerPathChunk
import org.fossify.gallery.helpers.ViewerPathWindow
import org.fossify.gallery.helpers.alphabetLabelFor
import org.fossify.gallery.helpers.ratingLabelFor
import org.fossify.gallery.helpers.sizeLabelFor
import org.fossify.gallery.helpers.toggleGroupKey
import org.fossify.gallery.models.Medium
import org.fossify.gallery.viewmodels.LibraryFilterActions
import org.fossify.gallery.viewmodels.LibraryFilterState
import org.fossify.gallery.viewmodels.MediaFilter
import org.fossify.gallery.viewmodels.MediaUiState
import org.fossify.gallery.viewmodels.MediaViewModel
import java.util.LinkedHashMap
import kotlin.math.abs

internal data class MediaScrollPosition(
    val index: Int = 0,
    val offset: Int = 0,
    val anchorPath: String = "",
)

internal val mediaScrollPositionSaver = listSaver<MediaScrollPosition, String>(
    save = { listOf(it.index.toString(), it.offset.toString(), it.anchorPath) },
    restore = {
        MediaScrollPosition(
            index = it.getOrNull(0)?.toIntOrNull() ?: 0,
            offset = it.getOrNull(1)?.toIntOrNull() ?: 0,
            anchorPath = it.getOrNull(2).orEmpty(),
        )
    },
)

/**
 * A folder destination can leave composition while the Viewer is open. Keep one small process
 * cache in addition to SaveableState so a recreated destination still has a semantic anchor.
 */
internal object MediaScrollCache {
    private const val MAX_ENTRIES = 24
    private val positions = object : LinkedHashMap<String, MediaScrollPosition>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, MediaScrollPosition>?): Boolean = size > MAX_ENTRIES
    }

    @Synchronized fun get(key: String): MediaScrollPosition? = positions[key]
    @Synchronized fun put(key: String, value: MediaScrollPosition) { positions[key] = value }
}

internal fun pathForScrollKey(key: Any?, keyToPath: Map<String, String>): String? =
    (key as? String)?.let(keyToPath::get)

internal fun lazyItemIndexByPath(rows: List<GroupRow>): Map<String, Int> {
    var index = 0
    val result = LinkedHashMap<String, Int>()
    rows.forEach { row ->
        when (row) {
            is GroupRow.SectionHeader -> index++
            is GroupRow.Items -> row.media.forEach { medium ->
                result.putIfAbsent(medium.path, index)
                index++
            }
        }
    }
    return result
}

private const val INITIAL_MOSAIC_ASPECT_COUNT = 48

/** Only images require bounds decoding. Videos intentionally stay square in the mosaic. */
internal fun initialMosaicImagePaths(media: Collection<Medium>): List<String> = media.asSequence()
    .filterNot { it.path.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS }
    .map { it.path }
    .distinct()
    .take(INITIAL_MOSAIC_ASPECT_COUNT)
    .toList()

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PagedContent(
    viewModel: MediaViewModel,
    lazyPagingItems: LazyPagingItems<Medium>,
    state: MediaUiState,
    viewSettings: ViewSettings,
    selectedPathsState: MutableState<Set<String>>,
    rangeAnchorIndexState: MutableState<Int?>,
    rangeAnchorPathState: MutableState<String?>,
    selectionModeState: MutableState<MediaSelectionMode>,
    dragSelection: SelectionDragState,
    contentTopInset: Dp,
    tabIndex: Int?,
    collapsedPagedRatingKeysState: MutableState<Set<String>>,
    onTagClick: (String) -> Unit,
    onNavigateToViewer: ((paths: List<String>, startIndex: Int) -> Unit)?,
    onNavigateToViewerFast: ((startPath: String, resolveWindow: suspend () -> ViewerPathWindow, loadMore: suspend (String, ViewerLoadDirection, Int) -> ViewerPathChunk) -> Unit)? = null,
    unratedLabel: String,
    pickMultiple: Boolean = false,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var viewerOpeningPath by remember { mutableStateOf<String?>(null) }
    var selectedPaths by selectedPathsState
    var rangeAnchorIndex by rangeAnchorIndexState
    var rangeAnchorPath by rangeAnchorPathState
    var selectionMode by selectionModeState
    val hasSelection = selectedPaths.isNotEmpty()
    val taggedPaths = state.taggedPaths
    val columnCount = rememberEffectiveColumnCount(viewSettings.columnCount)
    val isGrid = viewSettings.viewType == ViewType.GRID
    val isMosaic = viewSettings.viewType == ViewType.MOSAIC
    val cornerShape = if (viewSettings.roundedCorners) RoundedCornerShape(Radius.sm) else RoundedCornerShape(0.dp)
    val itemSpacing = viewSettings.spacing.dp
    val mediaCardColor = when (viewSettings.displayMode) {
        DisplayMode.COMPACT, DisplayMode.NORMAL -> MaterialTheme.colorScheme.surface
        DisplayMode.DARK -> MaterialTheme.colorScheme.surfaceVariant
    }
    val cropThumbnails = ctx.config.cropThumbnails
    val showThumbnailFileTypes = ctx.config.showThumbnailFileTypes
    val markFavoriteItems = ctx.config.markFavoriteItems
    val windowWidthDp = with(androidx.compose.ui.platform.LocalDensity.current) {
        androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.width.toDp()
    }
    val isNarrowMediaTile = (windowWidthDp / columnCount.coerceAtLeast(1)) < 80.dp
    val groupSummaries by viewModel.groupSummaries.collectAsStateWithLifecycle()
    var collapsedPagedRatingKeys by collapsedPagedRatingKeysState

    fun openViewerPaged(tappedPath: String) {
        if (viewerOpeningPath != null) return
        viewerOpeningPath = tappedPath

        onNavigateToViewerFast?.let { fastNavigate ->
            runCatching {
                fastNavigate(
                    tappedPath,
                    { viewModel.viewerPathWindow(tappedPath) },
                    { anchor, direction, limit -> viewModel.viewerPathChunk(anchor, direction, limit) },
                )
            }
                .onFailure { viewerOpeningPath = null }
            return
        }

        scope.launch {
            onNavigateToViewer?.invoke(listOf(tappedPath), 0)
        }
    }

    fun pathsInPagedRange(rows: List<PagedRow>, fromIdx: Int, toIdx: Int): Set<String> {
        val lo = minOf(fromIdx, toIdx)
        val hi = maxOf(fromIdx, toIdx)
        return rows.asSequence()
            .filterIsInstance<PagedRow.Item>()
            .filter { it.pagingIndex in lo..hi }
            .mapNotNull { safePeek(lazyPagingItems, it.pagingIndex)?.path }
            .toSet()
    }

    val completeRows = rememberPagedRows(lazyPagingItems, state.filter, viewSettings.sortBy, viewSettings.sortDesc, viewSettings.groupBy, unratedLabel)
    val pagedGroupsAreCollapsible = viewSettings.groupBy != GroupBy.NONE && viewSettings.groupBy != GroupBy.TAG
    val endOfPaginationReached = lazyPagingItems.loadState.append.endOfPaginationReached
    val rows = remember(completeRows, groupSummaries, viewSettings.groupBy, collapsedPagedRatingKeys, endOfPaginationReached, viewSettings.anchorBottom) {
        val withCountsAndCollapsed = if (pagedGroupsAreCollapsible) {
            buildPagedRowsWithCollapsed(completeRows, groupSummaries, collapsedPagedRatingKeys, endOfPaginationReached)
        } else {
            completeRows
        }
        orderPagedRowsForLayout(withCountsAndCollapsed, viewSettings.anchorBottom)
    }
    val initialMosaicPaths = remember(isMosaic, lazyPagingItems.itemSnapshotList.items) {
        if (isMosaic) initialMosaicImagePaths(lazyPagingItems.itemSnapshotList.items) else emptyList()
    }
    LaunchedEffect(isMosaic, initialMosaicPaths) {
        if (isMosaic) viewModel.prepareInitialMosaicAspects(initialMosaicPaths)
    }
    val initialMosaicReady = initialMosaicPaths.all { it in state.aspectRatios }
    fun togglePagedRatingGroup(key: String) {
        collapsedPagedRatingKeys = toggleGroupKey(collapsedPagedRatingKeys, key)
    }
    val loadedPagingKeys = remember(lazyPagingItems.itemSnapshotList.items) {
        val occurrences = HashMap<String, Int>()
        lazyPagingItems.itemSnapshotList.items.map { medium ->
            val occurrence = occurrences.getOrDefault(medium.path, 0)
            occurrences[medium.path] = occurrence + 1
            "media:${medium.path}#$occurrence"
        }
    }
    fun pagingItemKey(index: Int): String = loadedPagingKeys.getOrNull(index) ?: "index:$index"

    fun selectPagedItem(path: String, index: Int, startSelection: Boolean = false) {
        val anchor = rangeAnchorIndex
        selectedPaths = when {
            startSelection -> selectedPaths + path
            selectionMode == MediaSelectionMode.RANGE && anchor != null -> selectedPaths + pathsInPagedRange(rows, anchor, index)
            path in selectedPaths -> selectedPaths - path
            else -> selectedPaths + path
        }
        rangeAnchorIndex = index
        rangeAnchorPath = path
    }

    when {
        isMosaic && initialMosaicPaths.isNotEmpty() && !initialMosaicReady -> {
            MediaSkeleton(
                columns = columnCount,
                viewType = ViewType.MOSAIC,
                itemSpacing = itemSpacing,
                showFileName = viewSettings.showFileNames,
            )
        }
        isGrid -> {
            Column(Modifier.padding(top = contentTopInset)) {
                val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = state.scrollIndex, initialFirstVisibleItemScrollOffset = state.scrollOffset)
                LaunchedEffect(gridState) {
                    snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }
                        .collect { (i, o) -> viewModel.saveScrollPosition(i, o) }
                }
                ScrollToTopEffect(tabIndex) { gridState.animateScrollToItem(0) }
                val isScrolling by remember { derivedStateOf { gridState.isScrollInProgress } }
                LaunchedEffect(dragSelection.isDragging) {
                    if (dragSelection.isDragging) {
                        var targetIdx = gridState.firstVisibleItemIndex
                        while (dragSelection.isDragging) {
                            val s = dragSelection.autoScrollSpeed
                            if (s < -0.5f) targetIdx = maxOf(targetIdx - 1, 0)
                            else if (s > 0.5f) targetIdx++
                            else { delay(50); continue }
                            gridState.animateScrollToItem(targetIdx, 0)
                            delay((100 / abs(s).coerceAtLeast(0.5f)).toLong().coerceIn(50, 150))
                        }
                    }
                }
                CompositionLocalProvider(LocalOverscrollFactory provides null) {
                    Box(Modifier.dragSelectionGesture(
                        dragSelection,
                        enabled = false,
                        gridState = gridState,
                        onSelectPath = { path -> selectedPaths = selectedPaths + path },
                        onLongPressSelectPath = null,
                    )) {
                        LazyVerticalGrid(state = gridState, columns = GridCells.Fixed(columnCount), reverseLayout = viewSettings.anchorBottom, contentPadding = PaddingValues(itemSpacing / 2)) {
                            items(
                                count = rows.size,
                                key = { i -> when (val r = rows[i]) { is PagedRow.Header -> "header_${i}_${r.label}"; is PagedRow.Item -> pagingItemKey(r.pagingIndex) } },
                                span = { i -> if (rows[i] is PagedRow.Header) GridItemSpan(maxLineSpan) else GridItemSpan(1) },
                                contentType = { i -> if (rows[i] is PagedRow.Header) "header" else (safePeek(lazyPagingItems, (rows[i] as PagedRow.Item).pagingIndex)?.type ?: 0) },
                            ) { i ->
                                when (val row = rows[i]) {
                                    is PagedRow.Header -> PagedSectionHeader(row, pagedGroupsAreCollapsible, ::togglePagedRatingGroup)
                                    is PagedRow.Item -> {
                                        val m = safeGet(lazyPagingItems, row.pagingIndex) ?: return@items
                                        val isVideo = remember(m.path, m.type) { m.type == TYPE_VIDEOS || m.path.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS }
                                        val isSelected by remember(m.path) { derivedStateOf { m.path in selectedPaths } }
                                        MediaTile(
                                            medium = m,
                                            isVideo = isVideo,
                                            isSelected = isSelected,
                                            isSelectionMode = hasSelection,
                                            hasTag = m.path in taggedPaths,
                                            tags = state.tagsByPath[m.path].orEmpty(),
                                            onTagClick = if (!hasSelection && !pickMultiple) onTagClick else null,
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
                                                    selectPagedItem(m.path, row.pagingIndex)
                                                } else openViewerPaged(m.path)
                                            },
                                            onLongClick = {
                                                selectPagedItem(m.path, row.pagingIndex, startSelection = true)
                                            },
                                            onSwipeToSelect = { selectedPaths = selectedPaths + m.path },
                                            onBoundsChanged = { r -> dragSelection.registerItemBounds(m.path, r) },
                                            onBoundsRemoved = { dragSelection.unregisterItemBounds(m.path) },
                                        )
                                    }
                                }
                            }
                            if (lazyPagingItems.loadState.append is LoadState.Loading) {
                                item(key = "append_loading", span = { GridItemSpan(maxLineSpan) }, contentType = "loading") {
                                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                    }
                                }
                            }
                        }
                        val currentLabel by remember(rows, viewSettings.anchorBottom) { derivedStateOf { currentSectionLabel(rows, gridState.firstVisibleItemIndex, viewSettings.anchorBottom) } }
                        FloatingSectionLabel(currentLabel, isScrolling, Modifier.align(Alignment.TopCenter).padding(top = 8.dp))
                        FastScrollBar(
                            state = gridState,
                            positionLabel = { index -> currentSectionLabel(rows, index, viewSettings.anchorBottom) },
                            modifier = Modifier.align(Alignment.CenterEnd).padding(vertical = 8.dp),
                        )
                    }
                }
            }
        }
        isMosaic -> {
            Column(Modifier.padding(top = contentTopInset)) {
                val mosaicState = rememberLazyStaggeredGridState(initialFirstVisibleItemIndex = state.scrollIndex, initialFirstVisibleItemScrollOffset = state.scrollOffset)
                LaunchedEffect(mosaicState) {
                    snapshotFlow { mosaicState.firstVisibleItemIndex to mosaicState.firstVisibleItemScrollOffset }
                        .collect { (i, o) -> viewModel.saveScrollPosition(i, o) }
                }
                ScrollToTopEffect(tabIndex) { mosaicState.animateScrollToItem(0) }
                val isScrollingStag by remember { derivedStateOf { mosaicState.isScrollInProgress } }
                var appliedAspectRatios by remember { mutableStateOf(state.aspectRatios) }
                LaunchedEffect(state.aspectRatios, isScrollingStag) {
                    if (!isScrollingStag) {
                        delay(120)
                        appliedAspectRatios = state.aspectRatios
                    }
                }
                LaunchedEffect(dragSelection.isDragging) {
                    if (dragSelection.isDragging) {
                        var targetIdx = mosaicState.firstVisibleItemIndex
                        while (dragSelection.isDragging) {
                            val s = dragSelection.autoScrollSpeed
                            if (s < -0.5f) targetIdx = maxOf(targetIdx - 1, 0)
                            else if (s > 0.5f) targetIdx++
                            else { delay(50); continue }
                            mosaicState.animateScrollToItem(targetIdx, 0)
                            delay((120 / abs(s).coerceAtLeast(0.5f)).toLong().coerceIn(60, 200))
                        }
                    }
                }
                CompositionLocalProvider(LocalOverscrollFactory provides null) {
                    Box(Modifier.dragSelectionGesture(
                        dragSelection,
                        enabled = false,
                        staggeredGridState = mosaicState,
                        onSelectPath = { path -> selectedPaths = selectedPaths + path },
                        onLongPressSelectPath = null,
                    )) {
                        LazyVerticalStaggeredGrid(state = mosaicState, columns = StaggeredGridCells.Fixed(columnCount), reverseLayout = viewSettings.anchorBottom, contentPadding = PaddingValues(itemSpacing / 2)) {
                            rows.forEachIndexed { i, row ->
                                when (row) {
                                    is PagedRow.Header -> item(key = "header_${i}_${row.label}", span = StaggeredGridItemSpan.FullLine, contentType = "header") {
                                        PagedSectionHeader(row, pagedGroupsAreCollapsible, ::togglePagedRatingGroup)
                                    }
                                    is PagedRow.Item -> item(
                                        key = pagingItemKey(row.pagingIndex),
                                        contentType = safePeek(lazyPagingItems, row.pagingIndex)?.type ?: 0,
                                    ) {
                                        val m = safeGet(lazyPagingItems, row.pagingIndex)
                                        if (m != null) {
                                            val isVideo = remember(m.path, m.type) { m.type == TYPE_VIDEOS || m.path.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS }
                                            if (!isVideo) LaunchedEffect(m.path) { viewModel.requestAspect(m.path) }
                                            val isSelected by remember(m.path) { derivedStateOf { m.path in selectedPaths } }
                                            MediaTile(
                                                medium = m,
                                                isVideo = isVideo,
                                                isSelected = isSelected,
                                                isSelectionMode = hasSelection,
                                                hasTag = m.path in taggedPaths,
                                                tags = state.tagsByPath[m.path].orEmpty(),
                                                onTagClick = if (!hasSelection && !pickMultiple) onTagClick else null,
                                                showOverlays = true,
                                                aspectRatio = if (isVideo) 1f else (appliedAspectRatios[m.path] ?: 1f),
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
                                                    if (hasSelection || pickMultiple) {
                                                        selectPagedItem(m.path, row.pagingIndex)
                                                    } else openViewerPaged(m.path)
                                                },
                                                onLongClick = {
                                                    selectPagedItem(m.path, row.pagingIndex, startSelection = true)
                                                },
                                                onSwipeToSelect = { selectedPaths = selectedPaths + m.path },
                                                onBoundsChanged = { r -> dragSelection.registerItemBounds(m.path, r) },
                                                onBoundsRemoved = { dragSelection.unregisterItemBounds(m.path) },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        val currentLabelStag by remember(rows, viewSettings.anchorBottom) { derivedStateOf { currentSectionLabel(rows, mosaicState.firstVisibleItemIndex, viewSettings.anchorBottom) } }
                        FloatingSectionLabel(currentLabelStag, isScrollingStag, Modifier.align(Alignment.TopCenter).padding(top = 8.dp))
                        FastScrollBar(
                            state = mosaicState,
                            positionLabel = { index -> currentSectionLabel(rows, index, viewSettings.anchorBottom) },
                            modifier = Modifier.align(Alignment.CenterEnd).padding(vertical = 8.dp),
                        )
                    }
                }
            }
        }
        else -> {
            val listState = rememberLazyListState(initialFirstVisibleItemIndex = state.scrollIndex, initialFirstVisibleItemScrollOffset = state.scrollOffset)
            LaunchedEffect(listState) {
                snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
                    .collect { (i, o) -> viewModel.saveScrollPosition(i, o) }
            }
            ScrollToTopEffect(tabIndex) { listState.animateScrollToItem(0) }
            Column(Modifier.padding(top = contentTopInset)) {
                Box(Modifier.dragSelectionGesture(
                    dragSelection,
                    enabled = false,
                    onSelectPath = { path -> selectedPaths = selectedPaths + path },
                    onLongPressSelectPath = null,
                )) {
                    LazyColumn(state = listState, reverseLayout = viewSettings.anchorBottom, contentPadding = PaddingValues(4.dp)) {
                        var headerSeq = 0
                        rows.forEach { row ->
                            when (row) {
                                is PagedRow.Header -> {
                                    headerSeq++
                                    if (viewSettings.anchorBottom) {
                                        item(key = "header_${headerSeq}_${row.label}", contentType = "header") { PagedSectionHeader(row, pagedGroupsAreCollapsible, ::togglePagedRatingGroup) }
                                    } else {
                                        stickyHeader(key = "header_${headerSeq}_${row.label}", contentType = "header") { PagedSectionHeader(row, pagedGroupsAreCollapsible, ::togglePagedRatingGroup) }
                                    }
                                }
                                is PagedRow.Item -> {
                                    item(key = pagingItemKey(row.pagingIndex), contentType = safePeek(lazyPagingItems, row.pagingIndex)?.type ?: 0) {
                                        val m = safeGet(lazyPagingItems, row.pagingIndex)
                                        if (m != null) {
                                            val isVideo = remember(m.path, m.type) { m.type == TYPE_VIDEOS || m.path.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS }
                                            val isSelected by remember(m.path) { derivedStateOf { m.path in selectedPaths } }
                                            MediaListRow(
                                                medium = m,
                                                isVideo = isVideo,
                                                isSelected = isSelected,
                                                hasSelection = hasSelection,
                                                cardColor = mediaCardColor,
                                                fileSizeLabel = remember(m.size) { formatFileSize(m.size) },
                                                onClick = {
                                                    if (hasSelection || pickMultiple) {
                                                        selectPagedItem(m.path, row.pagingIndex)
                                                    } else openViewerPaged(m.path)
                                                },
                                                onLongClick = {
                                                    selectPagedItem(m.path, row.pagingIndex, startSelection = true)
                                                },
                                                onSwipeToSelect = { selectedPaths = selectedPaths + m.path },
                                                onBoundsChanged = { r -> dragSelection.registerItemBounds(m.path, r) },
                                                onBoundsRemoved = { dragSelection.unregisterItemBounds(m.path) },
                                                showRating = ThumbnailOverlayState.showRating,
                                                showVideoDuration = ThumbnailOverlayState.showVideoDuration,
                                                compact = viewSettings.listDensity == ListDensity.COMPACT,
                                                hasTag = m.path in taggedPaths,
                                                tags = state.tagsByPath[m.path].orEmpty(),
                                                onTagClick = if (!hasSelection && !pickMultiple) onTagClick else null,
                                                markFavorite = markFavoriteItems,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    FastScrollBar(
                        state = listState,
                        positionLabel = { index -> currentSectionLabel(rows, index, viewSettings.anchorBottom) },
                        modifier = Modifier.align(Alignment.CenterEnd).padding(vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun PagedSectionHeader(
    row: PagedRow.Header,
    isCollapsible: Boolean,
    onToggle: (String) -> Unit,
) {
    if (isCollapsible) {
        SectionHeader(
            label = row.label,
            count = row.count,
            hasChildren = true,
            isExpanded = row.isExpanded,
            onToggle = { onToggle(row.key) },
            ratingValue = row.ratingValue,
        )
    } else {
        MonthHeader(label = row.label, count = row.count)
    }
}

@Composable
internal fun MonthHeader(label: String, count: Int) {
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
        shape = RoundedCornerShape(Radius.md),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.weight(1f))
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                Text("$count", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp))
            }
        }
    }
}

internal fun currentSectionLabel(rows: List<PagedRow>, firstVisibleIndex: Int, reverseLayout: Boolean = false): String? {
    if (rows.isEmpty()) return null
    val clamped = firstVisibleIndex.coerceIn(0, rows.size - 1)
    if (reverseLayout) {
        for (i in clamped until rows.size) {
            val r = rows[i]
            if (r is PagedRow.Header) return r.label
        }
    }
    var i = clamped
    while (i >= 0) {
        val r = rows[i]
        if (r is PagedRow.Header) return r.label
        i--
    }
    return null
}

internal sealed interface PagedRow {
    data class Header(
        val key: String,
        val label: String,
        val count: Int,
        val ratingValue: Int? = null,
        val isExpanded: Boolean = true,
    ) : PagedRow
    data class Item(val pagingIndex: Int) : PagedRow
}

/**
 * Merges SQL-backed [groupSummaries] with currently paged [loadedRows]:
 * - replaces partial loaded counts with true database totals, and
 * - re-inserts collapsed headers (whose items were excluded at SQL level) in their exact sorted position.
 */
internal fun buildPagedRowsWithCollapsed(
    loadedRows: List<PagedRow>,
    groupSummaries: List<GroupSummary>,
    collapsedKeys: Set<String>,
    endOfPaginationReached: Boolean,
): List<PagedRow> {
    if (groupSummaries.isEmpty()) {
        return applyCollapsedPagedGroups(loadedRows, collapsedKeys)
    }
    val countByKey = groupSummaries.associate { it.key to it.count }
    val result = ArrayList<PagedRow>(loadedRows.size + collapsedKeys.size)
    var summaryIdx = 0
    var skippingCollapsedGroup = false

    fun emitCollapsedUntil(stopKey: String?) {
        while (summaryIdx < groupSummaries.size) {
            val summary = groupSummaries[summaryIdx]
            if (stopKey != null && summary.key == stopKey) break
            if (summary.key in collapsedKeys) {
                result += PagedRow.Header(
                    key = summary.key,
                    label = summary.label,
                    count = summary.count,
                    ratingValue = summary.ratingValue,
                    isExpanded = false,
                )
            }
            summaryIdx++
        }
    }

    loadedRows.forEach { row ->
        when (row) {
            is PagedRow.Header -> {
                emitCollapsedUntil(row.key)
                if (summaryIdx < groupSummaries.size && groupSummaries[summaryIdx].key == row.key) {
                    summaryIdx++
                }
                val isCollapsed = row.key in collapsedKeys
                skippingCollapsedGroup = isCollapsed
                result += row.copy(
                    count = countByKey[row.key] ?: row.count,
                    isExpanded = !isCollapsed,
                )
            }
            is PagedRow.Item -> {
                if (!skippingCollapsedGroup) result += row
            }
        }
    }
    if (endOfPaginationReached || loadedRows.isEmpty()) {
        emitCollapsedUntil(null)
    }
    return result
}

/**
 * In `reverseLayout = true` (`anchorBottom`), Lazy layouts render index 0 at the bottom and grow
 * upward. Moving each group's [PagedRow.Header] after its items keeps the header visually ABOVE
 * its group instead of underneath it.
 */
internal fun orderPagedRowsForLayout(rows: List<PagedRow>, reverseLayout: Boolean): List<PagedRow> {
    if (!reverseLayout || rows.isEmpty()) return rows
    val result = ArrayList<PagedRow>(rows.size)
    var pendingHeader: PagedRow.Header? = null
    val pendingItems = ArrayList<PagedRow.Item>()
    fun flush() {
        result.addAll(pendingItems)
        pendingHeader?.let { result.add(it) }
        pendingItems.clear()
        pendingHeader = null
    }
    rows.forEach { row ->
        when (row) {
            is PagedRow.Header -> {
                flush()
                pendingHeader = row
            }
            is PagedRow.Item -> pendingItems.add(row)
        }
    }
    flush()
    return result
}

internal fun applyCollapsedPagedGroups(rows: List<PagedRow>, collapsedKeys: Set<String>): List<PagedRow> {
    if (rows.isEmpty() || collapsedKeys.isEmpty()) return rows
    val result = ArrayList<PagedRow>(rows.size)
    var hideItems = false
    rows.forEach { row ->
        when (row) {
            is PagedRow.Header -> {
                hideItems = row.key in collapsedKeys
                result += row.copy(isExpanded = !hideItems)
            }
            is PagedRow.Item -> if (!hideItems) result += row
        }
    }
    return result
}

internal fun safePeek(items: LazyPagingItems<Medium>, index: Int): Medium? =
    if (index in 0 until items.itemCount) items.peek(index) else null

internal fun safeGet(items: LazyPagingItems<Medium>, index: Int): Medium? =
    if (index in 0 until items.itemCount) items[index] else null

private fun pagedGroupLabel(m: Medium, groupBy: GroupBy, unratedLabel: String): String? = when (groupBy) {
    GroupBy.MONTH -> MediaViewModel.monthLabelFor(m)
    GroupBy.SIZE -> sizeLabelFor(m.size)
    GroupBy.ALPHABET -> alphabetLabelFor(m.name)
    GroupBy.RATING -> ratingLabelFor(m.rating, unratedLabel)
    GroupBy.NONE, GroupBy.TAG -> null
}

private class PagedRowsAccumulator {
    private val rows = ArrayList<PagedRow>()
    private var lastLabel: String? = null
    private var lastHeaderPos = -1
    private var processed = 0

    fun extendTo(snapshot: List<Medium?>, groupBy: GroupBy, unratedLabel: String): List<PagedRow> {
        val size = snapshot.size
        if (size <= processed && size > 0) reset()
        var idx = processed
        while (idx < size) {
            val medium = snapshot[idx]
            val label = medium?.let { pagedGroupLabel(it, groupBy, unratedLabel) }
            if (label != null) {
                if (label != lastLabel) {
                    lastHeaderPos = rows.size
                    rows.add(
                        PagedRow.Header(
                            key = "${groupBy.name.lowercase()}:$label",
                            label = label,
                            count = 1,
                            ratingValue = medium.rating.takeIf { groupBy == GroupBy.RATING && it > 0 },
                        ),
                    )
                    lastLabel = label
                } else if (lastHeaderPos >= 0) {
                    val h = rows[lastHeaderPos] as PagedRow.Header
                    rows[lastHeaderPos] = h.copy(count = h.count + 1)
                }
            }
            rows.add(PagedRow.Item(idx))
            idx++
        }
        processed = size
        return ArrayList(rows)
    }

    private fun reset() {
        rows.clear(); lastLabel = null; lastHeaderPos = -1; processed = 0
    }
}

@Composable
internal fun rememberPagedRows(
    lazyPagingItems: LazyPagingItems<Medium>,
    filter: MediaFilter,
    sortBy: SortField,
    sortDesc: Boolean,
    groupBy: GroupBy,
    unratedLabel: String,
): List<PagedRow> {
    val snapshot = lazyPagingItems.itemSnapshotList
    val firstPath = snapshot.firstOrNull()?.path
    val acc = remember(filter, sortBy, sortDesc, groupBy, firstPath) { PagedRowsAccumulator() }
    return remember(lazyPagingItems.itemCount, filter, sortBy, sortDesc, groupBy, unratedLabel, firstPath, snapshot) {
        acc.extendTo(snapshot, groupBy, unratedLabel)
    }
}

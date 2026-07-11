package org.fossify.gallery.compose.screens
import org.fossify.gallery.compose.theme.AppMotion
import org.fossify.gallery.compose.theme.Radius

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import org.fossify.commons.dialogs.PropertiesDialog
import org.fossify.commons.extensions.toast
import org.fossify.gallery.compose.components.GalleryImage
import org.fossify.gallery.compose.components.MediaListRow
import org.fossify.gallery.compose.components.MediaTile
import org.fossify.gallery.compose.components.QuickTagRow
import org.fossify.gallery.R
import androidx.compose.ui.res.stringResource
import org.fossify.gallery.compose.components.ConfirmDestructive
import org.fossify.gallery.compose.components.EmptyState
import org.fossify.gallery.compose.components.SelectionTopAppBar
import org.fossify.gallery.compose.components.RenameDialog
import org.fossify.gallery.compose.components.UndoBar
import org.fossify.gallery.compose.components.RateAndTagSheet
import org.fossify.gallery.compose.util.PeekState
import org.fossify.gallery.compose.util.ScrollToTopEffect
import org.fossify.gallery.compose.util.SelectionDragState
import org.fossify.gallery.compose.util.dragSelectionGesture
import org.fossify.gallery.compose.util.rememberSelectionDragState
import org.fossify.gallery.helpers.UndoAction
import org.fossify.gallery.helpers.UndoManager
import org.fossify.gallery.helpers.UndoType
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.VIDEO_EXTENSIONS
import org.fossify.gallery.models.Medium
import org.fossify.gallery.viewmodels.MediaUiState
import org.fossify.gallery.viewmodels.MediaViewModel
import java.io.File

// Extracted to a top-level function (rather than nested inside MediaScreen, as it was before) so the
// Compose compiler can give it its own restart/skip group - as a zero-parameter local function
// capturing ~25 outer values by closure, it was the only real UI composable in the app that showed up
// as neither restartable nor skippable in Compose Compiler Metrics. This is the actual grid/mosaic/list
// content renderer, so it's the one place in the screen where that matters for scroll/paging performance.
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PagedContent(
    viewModel: MediaViewModel,
    lazyPagingItems: androidx.paging.compose.LazyPagingItems<Medium>,
    state: MediaUiState,
    viewSettings: ViewSettings,
    selectedPathsState: MutableState<Set<String>>,
    rangeAnchorIndexState: MutableState<Int?>,
    dragSelection: SelectionDragState,
    peekState: PeekState,
    contentTopInset: Dp,
    tabIndex: Int?,
    hasFilter: Boolean,
    ratingFilter: Int,
    activeTagName: String?,
    activePathName: String?,
    activeCollectionName: String?,
    minSizeFilter: Long,
    dateRangeFilter: Int,
    onClearRatingFilter: () -> Unit,
    onClearTagFilter: () -> Unit,
    onClearPathFilter: () -> Unit,
    onClearSizeFilter: () -> Unit,
    onClearDateFilter: () -> Unit,
    onClearFilter: () -> Unit,
    onNavigateToViewer: ((paths: List<String>, startIndex: Int) -> Unit)?,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    // Delegating to the passed-in MutableState (rather than a plain Set<String>/Int? value param)
    // matters here: onLongClick below reads then writes both of these within a single event-handler
    // invocation (val anchor = rangeAnchorIndex; ...; rangeAnchorIndex = row.pagingIndex), and that
    // handler lambda's captures are fixed at the composition pass that created it. A live MutableState
    // reference is always read fresh through .value regardless of when/how often that lambda actually
    // executes; a plain captured value would not be, and range-select silently degraded to single-item
    // select when this was first tried as value+callback params - verified via before/after A-B test.
    var selectedPaths by selectedPathsState
    var rangeAnchorIndex by rangeAnchorIndexState
    val hasSelection = selectedPaths.isNotEmpty()
    val taggedPaths = state.taggedPaths
    val selectedCommonTags = state.selectedCommonTags
    val columnCount = viewSettings.columnCount
    val isGrid = viewSettings.viewType == ViewType.GRID
    val isMosaic = viewSettings.viewType == ViewType.MOSAIC
    val cornerShape = if (viewSettings.roundedCorners) RoundedCornerShape(Radius.sm) else RoundedCornerShape(0.dp)
    val itemSpacing = viewSettings.spacing.dp
    val mediaCardColor = when (viewSettings.displayMode) { DisplayMode.COMPACT,DisplayMode.NORMAL->MaterialTheme.colorScheme.surface; DisplayMode.DARK->MaterialTheme.colorScheme.surfaceVariant }
    // True filtered total (see MediaViewModel.filteredResultCount) - falls back to itemCount (Paging3's
    // loaded-so-far count) for the brief window before the real COUNT(*) resolves, so the breadcrumb
    // never flashes 0.
    val filteredResultCount by viewModel.filteredResultCount.collectAsState()

    // Viewer swipe-through needs the FULL sorted path list, not just what the grid has paged in so
    // far - fetched fresh on demand (cheap: paths only, no thumbnails) so opening any item can swipe
    // through the entire library, not just the loaded window. Must respect the active filter (e.g. a
    // Collection): row.pagingIndex is a position within the filtered grid, so resolving it against the
    // unfiltered path list would open whatever unrelated file happens to sit at that index instead.
    fun openViewerPaged(index: Int) {
        scope.launch {
            val paths = if (hasFilter) viewModel.activePathsSortedFiltered() else viewModel.activePathsSorted()
            onNavigateToViewer?.invoke(paths, index)
        }
    }

    // Every path whose pagingIndex falls between fromIdx/toIdx (inclusive, either order) - the
    // paged (Paging3) branches' range-select helper.
    fun pathsInPagedRange(rows: List<PagedRow>, fromIdx: Int, toIdx: Int): Set<String> {
        val lo = minOf(fromIdx, toIdx)
        val hi = maxOf(fromIdx, toIdx)
        return rows.asSequence()
            .filterIsInstance<PagedRow.Item>()
            .filter { it.pagingIndex in lo..hi }
            .mapNotNull { safePeek(lazyPagingItems, it.pagingIndex)?.path }
            .toSet()
    }

    // Computed once here (incrementally) and shared by all three view-type branches, instead of each
    // branch rebuilding the whole Header/Item list from scratch on every Paging append.
    val rows = rememberPagedRows(lazyPagingItems, state.filter, viewSettings.sortBy, viewSettings.sortDesc)
    when {
        isGrid -> {
            Column(Modifier.padding(top = contentTopInset)) {
                if (hasFilter) FilterBreadcrumbs(
                    ratingFilter, activeTagName, activePathName, activeCollectionName,
                    minSizeFilter, dateRangeFilter,
                    filteredResultCount ?: lazyPagingItems.itemCount, onClearRatingFilter, onClearTagFilter, onClearPathFilter,
                    onClearSizeFilter, onClearDateFilter, onClearFilter
                )
                val quickTags = remember { ctx.config.quickTags.filter { it.isNotBlank() } }
                QuickTagRow(quickTags = quickTags, visible = quickTags.isNotEmpty() && hasSelection, selectedCommonTags = selectedCommonTags, onToggleTag = { tag -> viewModel.toggleQuickTag(selectedPaths, tag) })
                val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = state.scrollIndex, initialFirstVisibleItemScrollOffset = state.scrollOffset)
                LaunchedEffect(gridState) {
                    snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }
                        .collect { (i, o) -> viewModel.saveScrollPosition(i, o) }
                }
                ScrollToTopEffect(tabIndex) { gridState.animateScrollToItem(0) }
                var showOverlays by remember { mutableStateOf(true) }
                val isScrolling by remember { derivedStateOf { gridState.isScrollInProgress } }
                LaunchedEffect(isScrolling) {
                    if (isScrolling) showOverlays = false
                    else { delay(300); showOverlays = true }
                }
                LaunchedEffect(dragSelection.isDragging) {
                    if (dragSelection.isDragging) {
                        var targetIdx = gridState.firstVisibleItemIndex
                        while (dragSelection.isDragging) {
                            val s = dragSelection.autoScrollSpeed
                            if (s < -0.5f) targetIdx = maxOf(targetIdx - 1, 0)
                            else if (s > 0.5f) targetIdx++
                            else { kotlinx.coroutines.delay(50); continue }
                            gridState.animateScrollToItem(targetIdx, 0)
                            kotlinx.coroutines.delay((100 / kotlin.math.abs(s).coerceAtLeast(0.5f)).toLong().coerceIn(50, 150))
                        }
                    }
                }
                CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
                Box(Modifier.dragSelectionGesture(dragSelection, enabled = hasSelection, gridState = gridState) { path -> selectedPaths = selectedPaths + path }) {
                LazyVerticalGrid(state = gridState, columns = GridCells.Fixed(columnCount), reverseLayout = viewSettings.anchorBottom, contentPadding = PaddingValues(itemSpacing / 2)) {
                    items(
                        count = rows.size,
                        key = { i -> when (val r = rows[i]) { is PagedRow.Header -> "header_${i}_${r.label}"; is PagedRow.Item -> safePeek(lazyPagingItems, r.pagingIndex)?.path ?: "empty_${r.pagingIndex}" } },
                        span = { i -> if (rows[i] is PagedRow.Header) GridItemSpan(maxLineSpan) else GridItemSpan(1) },
                        contentType = { i -> if (rows[i] is PagedRow.Header) "header" else (safePeek(lazyPagingItems, (rows[i] as PagedRow.Item).pagingIndex)?.type ?: 0) },
                    ) { i ->
                        when (val row = rows[i]) {
                            is PagedRow.Header -> MonthHeader(label = row.label, count = row.count)
                            is PagedRow.Item -> {
                                val m = safeGet(lazyPagingItems, row.pagingIndex) ?: return@items
                                val isVideo = remember(m.path) { m.path.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS }
                                val isSelected by remember(m.path) { derivedStateOf { m.path in selectedPaths } }
                                MediaTile(
                                    medium = m,
                                    isVideo = isVideo,
                                    isSelected = isSelected,
                                    isSelectionMode = hasSelection,
                                    hasTag = m.path in taggedPaths,
                                    showOverlays = showOverlays,
                                    aspectRatio = 1f,
                                    cornerShape = cornerShape,
                                    cardColor = mediaCardColor,
                                    itemSpacing = itemSpacing,
                                    showFileName = viewSettings.showFileNames,
                                    showVideoDuration = ctx.config.showVideoDurationOnThumbnails,
                                    cropThumbnails = ctx.config.cropThumbnails,
                                    showRating = ctx.config.showRatingOnThumbnails,
                                    showFileType = ctx.config.showThumbnailFileTypes,
                                    markFavorite = ctx.config.markFavoriteItems,
                                    onClick = {
                                        if (hasSelection) {
                                            selectedPaths = if (m.path in selectedPaths) selectedPaths - m.path else selectedPaths + m.path
                                            rangeAnchorIndex = row.pagingIndex
                                        } else openViewerPaged(row.pagingIndex)
                                    },
                                    onLongClick = {
                                        val anchor = rangeAnchorIndex
                                        selectedPaths = if (hasSelection && anchor != null) selectedPaths + pathsInPagedRange(rows, anchor, row.pagingIndex) else selectedPaths + m.path
                                        rangeAnchorIndex = row.pagingIndex
                                    },
                                    onSwipeToSelect = { selectedPaths = selectedPaths + m.path },
                                    onPreviewClick = { peekState.show(m.path, isVideo) },
                                    onBoundsChanged = { r -> dragSelection.registerItemBounds(m.path, r) },
                                )
                            }
                        }
                    }
                    if (lazyPagingItems.loadState.append is LoadState.Loading) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            }
                        }
                    }
                }
                val currentLabel by remember(rows) { derivedStateOf { currentSectionLabel(rows, gridState.firstVisibleItemIndex) } }
                FloatingSectionLabel(currentLabel, isScrolling, Modifier.align(Alignment.TopCenter).padding(top = 8.dp))
                }
                }
            }
        }
        isMosaic -> {
            Column(Modifier.padding(top = contentTopInset)) {
                if (hasFilter) FilterBreadcrumbs(
                    ratingFilter, activeTagName, activePathName, activeCollectionName,
                    minSizeFilter, dateRangeFilter,
                    filteredResultCount ?: lazyPagingItems.itemCount, onClearRatingFilter, onClearTagFilter, onClearPathFilter,
                    onClearSizeFilter, onClearDateFilter, onClearFilter
                )
                val quickTagsM = remember { ctx.config.quickTags.filter { it.isNotBlank() } }
                QuickTagRow(quickTags = quickTagsM, visible = quickTagsM.isNotEmpty() && hasSelection, selectedCommonTags = selectedCommonTags, onToggleTag = { tag -> viewModel.toggleQuickTag(selectedPaths, tag) })
                val mosaicState = rememberLazyStaggeredGridState(initialFirstVisibleItemIndex = state.scrollIndex, initialFirstVisibleItemScrollOffset = state.scrollOffset)
                LaunchedEffect(mosaicState) {
                    snapshotFlow { mosaicState.firstVisibleItemIndex to mosaicState.firstVisibleItemScrollOffset }
                        .collect { (i, o) -> viewModel.saveScrollPosition(i, o) }
                }
                ScrollToTopEffect(tabIndex) { mosaicState.animateScrollToItem(0) }
                var showOverlaysStag by remember { mutableStateOf(true) }
                val isScrollingStag by remember { derivedStateOf { mosaicState.isScrollInProgress } }
                LaunchedEffect(isScrollingStag) {
                    if (isScrollingStag) showOverlaysStag = false
                    else { delay(300); showOverlaysStag = true }
                }
                LaunchedEffect(dragSelection.isDragging) {
                    if (dragSelection.isDragging) {
                        var targetIdx = mosaicState.firstVisibleItemIndex
                        while (dragSelection.isDragging) {
                            val s = dragSelection.autoScrollSpeed
                            if (s < -0.5f) targetIdx = maxOf(targetIdx - 1, 0)
                            else if (s > 0.5f) targetIdx++
                            else { kotlinx.coroutines.delay(50); continue }
                            mosaicState.animateScrollToItem(targetIdx, 0)
                            kotlinx.coroutines.delay((120 / kotlin.math.abs(s).coerceAtLeast(0.5f)).toLong().coerceIn(60, 200))
                        }
                    }
                }
                CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
                Box(Modifier.dragSelectionGesture(dragSelection, enabled = hasSelection, staggeredGridState = mosaicState) { path -> selectedPaths = selectedPaths + path }) {
                LazyVerticalStaggeredGrid(state = mosaicState, columns = StaggeredGridCells.Fixed(columnCount), reverseLayout = viewSettings.anchorBottom, contentPadding = PaddingValues(itemSpacing / 2)) {
                    rows.forEachIndexed { i, row ->
                        when (row) {
                            is PagedRow.Header -> item(key = "header_${i}_${row.label}", span = StaggeredGridItemSpan.FullLine, contentType = "header") {
                                MonthHeader(label = row.label, count = row.count)
                            }
                            is PagedRow.Item -> item(
                                key = safePeek(lazyPagingItems, row.pagingIndex)?.path ?: "empty_${row.pagingIndex}",
                                contentType = safePeek(lazyPagingItems, row.pagingIndex)?.type ?: 0,
                            ) {
                                val m = safeGet(lazyPagingItems, row.pagingIndex)
                                if (m != null) {
                                    val isVideo = remember(m.path) { m.path.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS }
                                    if (!isVideo) LaunchedEffect(m.path) { viewModel.requestAspect(m.path) }
                                    val isSelected by remember(m.path) { derivedStateOf { m.path in selectedPaths } }
                                    MediaTile(
                                        medium = m,
                                        isVideo = isVideo,
                                        isSelected = isSelected,
                                        isSelectionMode = hasSelection,
                                        hasTag = m.path in taggedPaths,
                                        showOverlays = showOverlaysStag,
                                        aspectRatio = if (isVideo) 1f else (state.aspectRatios[m.path] ?: 1f),
                                        cornerShape = cornerShape,
                                        cardColor = mediaCardColor,
                                        itemSpacing = itemSpacing,
                                        showFileName = viewSettings.showFileNames,
                                        showVideoDuration = ctx.config.showVideoDurationOnThumbnails,
                                        cropThumbnails = ctx.config.cropThumbnails,
                                        showRating = ctx.config.showRatingOnThumbnails,
                                        showFileType = ctx.config.showThumbnailFileTypes,
                                        markFavorite = ctx.config.markFavoriteItems,
                                        onClick = {
                                            if (hasSelection) {
                                                selectedPaths = if (m.path in selectedPaths) selectedPaths - m.path else selectedPaths + m.path
                                                rangeAnchorIndex = row.pagingIndex
                                            } else openViewerPaged(row.pagingIndex)
                                        },
                                        onLongClick = {
                                            val anchor = rangeAnchorIndex
                                            selectedPaths = if (hasSelection && anchor != null) selectedPaths + pathsInPagedRange(rows, anchor, row.pagingIndex) else selectedPaths + m.path
                                            rangeAnchorIndex = row.pagingIndex
                                        },
                                        onSwipeToSelect = { selectedPaths = selectedPaths + m.path },
                                        onPreviewClick = { peekState.show(m.path, isVideo) },
                                        onBoundsChanged = { r -> dragSelection.registerItemBounds(m.path, r) },
                                    )
                                }
                            }
                        }
                    }
                }
                val currentLabelStag by remember(rows) { derivedStateOf { currentSectionLabel(rows, mosaicState.firstVisibleItemIndex) } }
                FloatingSectionLabel(currentLabelStag, isScrollingStag, Modifier.align(Alignment.TopCenter).padding(top = 8.dp))
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
            Box(Modifier.padding(top = contentTopInset).dragSelectionGesture(dragSelection, enabled = hasSelection) { path -> selectedPaths = selectedPaths + path }) {
            LazyColumn(state = listState, reverseLayout = viewSettings.anchorBottom, contentPadding = PaddingValues(4.dp)) {
                var headerSeq = 0
                rows.forEach { row ->
                    when (row) {
                        is PagedRow.Header -> {
                            headerSeq++
                            stickyHeader(key = "header_${headerSeq}_${row.label}") { MonthHeader(label = row.label, count = row.count) }
                        }
                        is PagedRow.Item -> {
                            val path = safePeek(lazyPagingItems, row.pagingIndex)?.path
                            item(key = path ?: "empty_${row.pagingIndex}", contentType = safePeek(lazyPagingItems, row.pagingIndex)?.type ?: 0) {
                                val m = safeGet(lazyPagingItems, row.pagingIndex)
                                if (m != null) {
                                    val isVideo = remember(m.path) { m.path.substringAfterLast('.',"").lowercase() in VIDEO_EXTENSIONS }
                                    val isSelected by remember(m.path) { derivedStateOf { m.path in selectedPaths } }
                                    MediaListRow(
                                        medium = m,
                                        isVideo = isVideo,
                                        isSelected = isSelected,
                                        hasSelection = hasSelection,
                                        cardColor = mediaCardColor,
                                        fileSizeLabel = formatFileSize(m.size),
                                        onClick = {
                                            if (hasSelection) {
                                                selectedPaths = if (m.path in selectedPaths) selectedPaths - m.path else selectedPaths + m.path
                                                rangeAnchorIndex = row.pagingIndex
                                            } else openViewerPaged(row.pagingIndex)
                                        },
                                        onLongClick = {
                                            val anchor = rangeAnchorIndex
                                            selectedPaths = if (hasSelection && anchor != null) selectedPaths + pathsInPagedRange(rows, anchor, row.pagingIndex) else selectedPaths + m.path
                                            rangeAnchorIndex = row.pagingIndex
                                        },
                                        onSwipeToSelect = { selectedPaths = selectedPaths + m.path },
                                        onPreview = { openViewerPaged(row.pagingIndex) },
                                        onBoundsChanged = { r -> dragSelection.registerItemBounds(m.path, r) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MediaScreen(
    modifier: Modifier = Modifier,
    viewSettings: ViewSettings = ViewSettings(),
    ratingFilter: Int = 0,
    tagFilterNames: Set<String>? = null,
    pathFilter: Set<String>? = null,
    excludePathFilter: Set<String>? = null,
    activeTagName: String? = null,
    activePathName: String? = null,
    activeCollectionName: String? = null,
    minSizeFilter: Long = 0L,
    dateRangeFilter: Int = 0,
    onClearFilter: () -> Unit = {},
    onClearRatingFilter: () -> Unit = {},
    onClearTagFilter: () -> Unit = {},
    onClearPathFilter: () -> Unit = {},
    onClearSizeFilter: () -> Unit = {},
    onClearDateFilter: () -> Unit = {},
    mediaOverride: List<Medium>? = null,
    refreshTrigger: Int = 0,
    onNavigateToViewer: ((paths: List<String>, startIndex: Int) -> Unit)? = null,
    scrollToPath: String = "",
    onClearScrollToPath: () -> Unit = {},
    onSelectionActiveChanged: (Boolean) -> Unit = {},
    tabIndex: Int? = null,
) {
    val ctx = LocalContext.current
    val viewModel: MediaViewModel = viewModel()
    val state by viewModel.state.collectAsState()
    val lazyPagingItems = viewModel.pagedMedia.collectAsLazyPagingItems()
    val selectionSaver = remember { listSaver<Set<String>, String>(save = { it.toList() }, restore = { it.toSet() }) }
    LaunchedEffect(refreshTrigger) { if (refreshTrigger > 0) { if (state.allMedia.isNotEmpty()) viewModel.silentRefresh() else viewModel.refresh() } }
    LaunchedEffect(viewSettings.sortBy, viewSettings.sortDesc) { if (mediaOverride == null) viewModel.setSort(viewSettings.sortBy, viewSettings.sortDesc) }
    LaunchedEffect(mediaOverride) { viewModel.setOverride(mediaOverride) }
    LaunchedEffect(ratingFilter, tagFilterNames, pathFilter, excludePathFilter, minSizeFilter, dateRangeFilter) { viewModel.setFilter(ratingFilter, tagFilterNames, pathFilter, excludePathFilter, minSizeFilter, dateRangeFilter) }
    // When filters are active (e.g. viewing a Collection) and NOT using override (Favorites), subscribe to RefreshBus
    // to auto-reload the filtered list when media is deleted/moved elsewhere. Without this, the filtered list stays stale
    // until the user manually pulls-to-refresh, the same issue FolderMediaScreen solved with direct RefreshBus subscription.
    LaunchedEffect(ratingFilter, tagFilterNames, pathFilter, excludePathFilter, minSizeFilter, dateRangeFilter, mediaOverride) {
        val hasFilter = ratingFilter > 0 || tagFilterNames != null || pathFilter != null || excludePathFilter != null || minSizeFilter > 0 || dateRangeFilter > 0
        if (hasFilter && mediaOverride == null) {
            org.fossify.gallery.helpers.RefreshBus.events.collect {
                viewModel.silentRefresh()
            }
        }
    }
    val selectedPathsState = rememberSaveable(stateSaver = selectionSaver) { mutableStateOf<Set<String>>(emptySet()) }
    var selectedPaths by selectedPathsState
    // The last item explicitly tapped or long-pressed while selecting - a long-press on a second
    // item extends the selection to everything between this and that item (inclusive), instead of
    // just adding the one long-pressed item. Index space matches whichever branch is rendering
    // (paged: pagingIndex into the full sorted list; override/Favorites: index into displayMedia).
    val rangeAnchorIndexState = rememberSaveable { mutableStateOf<Int?>(null) }
    var rangeAnchorIndex by rangeAnchorIndexState
    val dragSelection = rememberSelectionDragState()
    val peekState = org.fossify.gallery.compose.util.rememberPeekState()
    var showRateTagSheet by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showFolderPicker by remember { mutableStateOf(false) }
    var folderPickerIsMove by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var currentRating by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val pendingUndo by UndoManager.actions.collectAsState()
    val taggedPaths = state.taggedPaths
    val selectedCommonTags = state.selectedCommonTags
    LaunchedEffect(selectedPaths) { viewModel.loadCommonTags(selectedPaths) }
    val columnCount = viewSettings.columnCount
    val isGrid = viewSettings.viewType == ViewType.GRID
    val isMosaic = viewSettings.viewType == ViewType.MOSAIC
    val hasFilter = ratingFilter > 0 || tagFilterNames != null || pathFilter != null || excludePathFilter != null || minSizeFilter > 0 || dateRangeFilter > 0
    // Filtered and unfiltered browsing both render from the Paging3 flow now (MediaViewModel picks
    // the filtered or unfiltered PagingSource based on whether a filter is active) - only Favorites'
    // externally-supplied mediaOverride still uses the legacy in-memory state.displayMedia path.
    val showPaged = mediaOverride == null
    val displayMedia = state.displayMedia
    val pathIndexMap = remember(displayMedia) { displayMedia.withIndex().associate { it.value.path to it.index } }
    val cornerShape = if (viewSettings.roundedCorners) RoundedCornerShape(Radius.sm) else RoundedCornerShape(0.dp)
    val itemSpacing = viewSettings.spacing.dp
    val mediaCardColor = when (viewSettings.displayMode) { DisplayMode.COMPACT,DisplayMode.NORMAL->MaterialTheme.colorScheme.surface; DisplayMode.DARK->MaterialTheme.colorScheme.surfaceVariant }

    fun openViewer(index: Int) {
        val paths = displayMedia.map { it.path }
        onNavigateToViewer?.invoke(paths, index)
    }

    // Same, for the legacy in-memory displayMedia list (Favorites' mediaOverride branch).
    fun pathsInDisplayRange(fromIdx: Int, toIdx: Int): Set<String> {
        if (displayMedia.isEmpty()) return emptySet()
        val lo = minOf(fromIdx, toIdx).coerceIn(0, displayMedia.lastIndex)
        val hi = maxOf(fromIdx, toIdx).coerceIn(0, displayMedia.lastIndex)
        return (lo..hi).map { displayMedia[it].path }.toSet()
    }

    val hasSelection = selectedPaths.isNotEmpty()
    LaunchedEffect(hasSelection) { onSelectionActiveChanged(hasSelection) }
    var selectionBarHeightPx by remember { mutableIntStateOf(0) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    // The bouncy spring can transiently overshoot past 0 while animating the inset closed, and
    // Modifier.padding() throws on a negative value - coerce here so every use site is safe.
    val rawContentTopInset by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (hasSelection) with(density) { selectionBarHeightPx.toDp() } else 0.dp,
        animationSpec = org.fossify.gallery.compose.theme.AppMotion.insetSpring,
        label = "selectionTopInset",
    )
    val contentTopInset = rawContentTopInset.coerceAtLeast(0.dp)

    LaunchedEffect(state.error) {
        val err = state.error
        if (!err.isNullOrBlank() && displayMedia.isNotEmpty()) {
            snackbarHostState.showSnackbar(err)
            viewModel.clearError()
        }
    }

    var isRefreshing by remember { mutableStateOf(false) }
    // Set once this refresh cycle has actually been observed passing through Paging3's Loading
    // state - via snapshotFlow rather than a plain recomposition-keyed effect, since a refresh
    // fast enough to resolve within a single frame could otherwise have its Loading->NotLoading
    // transition coalesced away between compositions, letting pagingSettled below read "settled"
    // without the reload ever having visibly started. Reset to false each time onRefresh fires.
    var pagingRefreshStarted by remember { mutableStateOf(false) }
    LaunchedEffect(lazyPagingItems) {
        snapshotFlow { lazyPagingItems.loadState.refresh }.collect { if (it is LoadState.Loading) pagingRefreshStarted = true }
    }
    // Clears on the real signal for each path (legacy state.isLoading, or Paging3's own refresh
    // load state when showPaged is active) instead of guessing a fixed delay. The timeout in
    // onRefresh below is only a safety net in case neither signal ever resolves.
    LaunchedEffect(state.isLoading, showPaged, lazyPagingItems.loadState.refresh, pagingRefreshStarted) {
        val pagingSettled = !showPaged || (pagingRefreshStarted && lazyPagingItems.loadState.refresh !is LoadState.Loading)
        if (!state.isLoading && pagingSettled && isRefreshing) isRefreshing = false
    }

    val pullRefreshEnabled = ctx.config.enablePullToRefresh
    val mediaContent: @Composable BoxScope.() -> Unit = {
        BackHandler(enabled = hasSelection) { selectedPaths = emptySet() }
        Box(modifier = modifier.fillMaxSize()) {
        if (mediaOverride != null && mediaOverride.isEmpty()) {
            EmptyState(Icons.Default.Search, stringResource(R.string.no_media_in_folder))
        } else if (showPaged) {
            val refreshState = lazyPagingItems.loadState.refresh
            val refreshErrorMessage = (refreshState as? LoadState.Error)?.error?.message
            // Crossfade instead of an instant swap - the shimmer skeleton is otherwise torn out and
            // replaced with the real grid in the same frame, which undercuts how smooth the skeleton
            // itself looks.
            val pagedContentState = when {
                refreshState is LoadState.Loading && lazyPagingItems.itemCount == 0 -> "loading"
                refreshState is LoadState.Error && lazyPagingItems.itemCount == 0 -> "error"
                lazyPagingItems.itemCount == 0 -> "empty"
                else -> "content"
            }
            Crossfade(targetState = pagedContentState, animationSpec = AppMotion.short, label = "pagedMediaContent") { s ->
                when (s) {
                    "loading" -> MediaSkeleton(columns = columnCount)
                    "error" -> EmptyState(
                        icon = Icons.Default.ErrorOutline,
                        title = stringResource(R.string.error_loading_media),
                        subtitle = refreshErrorMessage?.takeIf { it.isNotBlank() },
                        actionLabel = stringResource(R.string.retry),
                        onAction = { lazyPagingItems.retry() },
                    )
                    "empty" -> EmptyState(
                        icon = Icons.Default.Search,
                        title = if (hasFilter) stringResource(R.string.no_results) else stringResource(R.string.no_media_found),
                        actionLabel = if (hasFilter) stringResource(R.string.clear_filter) else null,
                        onAction = if (hasFilter) onClearFilter else null,
                    )
                    else -> PagedContent(
                        viewModel = viewModel,
                        lazyPagingItems = lazyPagingItems,
                        state = state,
                        viewSettings = viewSettings,
                        selectedPathsState = selectedPathsState,
                        rangeAnchorIndexState = rangeAnchorIndexState,
                        dragSelection = dragSelection,
                        peekState = peekState,
                        contentTopInset = contentTopInset,
                        tabIndex = tabIndex,
                        hasFilter = hasFilter,
                        ratingFilter = ratingFilter,
                        activeTagName = activeTagName,
                        activePathName = activePathName,
                        activeCollectionName = activeCollectionName,
                        minSizeFilter = minSizeFilter,
                        dateRangeFilter = dateRangeFilter,
                        onClearRatingFilter = onClearRatingFilter,
                        onClearTagFilter = onClearTagFilter,
                        onClearPathFilter = onClearPathFilter,
                        onClearSizeFilter = onClearSizeFilter,
                        onClearDateFilter = onClearDateFilter,
                        onClearFilter = onClearFilter,
                        onNavigateToViewer = onNavigateToViewer,
                    )
                }
            }
        } else {
        when {
            state.error != null && displayMedia.isEmpty() && mediaOverride == null -> {
                EmptyState(
                    icon = Icons.Default.ErrorOutline,
                    title = stringResource(R.string.error_loading_media),
                    subtitle = state.error?.takeIf { it.isNotBlank() },
                    actionLabel = stringResource(R.string.retry),
                    onAction = { viewModel.refresh() },
                )
            }
            displayMedia.isEmpty() -> {
                EmptyState(
                    icon = Icons.Default.Search,
                    title = if (hasFilter) stringResource(R.string.no_results) else stringResource(R.string.no_media_found),
                    actionLabel = if (hasFilter) stringResource(R.string.clear_filter) else null,
                    onAction = if (hasFilter) onClearFilter else null,
                )
            }
            isGrid -> {
                Column(Modifier.padding(top = contentTopInset)) {
                    if (hasFilter) FilterBreadcrumbs(
                        ratingFilter, activeTagName, activePathName, activeCollectionName,
                        minSizeFilter, dateRangeFilter,
                        displayMedia.size, onClearRatingFilter, onClearTagFilter, onClearPathFilter,
                        onClearSizeFilter, onClearDateFilter, onClearFilter
                    )
                    val quickTags = remember { ctx.config.quickTags.filter { it.isNotBlank() } }
                    QuickTagRow(quickTags = quickTags, visible = quickTags.isNotEmpty() && hasSelection, selectedCommonTags = selectedCommonTags, onToggleTag = { tag -> viewModel.toggleQuickTag(selectedPaths, tag) })
                    val grouped = state.monthGroups
                    val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = state.scrollIndex, initialFirstVisibleItemScrollOffset = state.scrollOffset)
                    LaunchedEffect(gridState) {
                        snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }
                            .collect { (i, o) -> viewModel.saveScrollPosition(i, o) }
                    }
                    ScrollToTopEffect(tabIndex) { gridState.animateScrollToItem(0) }
                    var showOverlays by remember { mutableStateOf(true) }
                    val isScrolling by remember { derivedStateOf { gridState.isScrollInProgress } }
                    LaunchedEffect(isScrolling) {
                        if (isScrolling) showOverlays = false
                        else { delay(300); showOverlays = true }
                    }
                    LaunchedEffect(dragSelection.isDragging) {
                        if (dragSelection.isDragging) {
                            var targetIdx = gridState.firstVisibleItemIndex
                            while (dragSelection.isDragging) {
                                val s = dragSelection.autoScrollSpeed
                                if (s < -0.5f) targetIdx = maxOf(targetIdx - 1, 0)
                                else if (s > 0.5f) targetIdx++
                                else { kotlinx.coroutines.delay(50); continue }
                                gridState.animateScrollToItem(targetIdx, 0)
                                kotlinx.coroutines.delay((100 / kotlin.math.abs(s).coerceAtLeast(0.5f)).toLong().coerceIn(50, 150))
                            }
                        }
                    }
                    CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
                    Box(Modifier.dragSelectionGesture(dragSelection, enabled = hasSelection, gridState = gridState) { path -> selectedPaths = selectedPaths + path }) {
                    LazyVerticalGrid(state = gridState, columns = GridCells.Fixed(columnCount), reverseLayout = viewSettings.anchorBottom, contentPadding = PaddingValues(itemSpacing / 2)) {
                    grouped.forEach { (label, groupItems) ->
                        item(span = { GridItemSpan(maxLineSpan) }) { MonthHeader(label = label, count = groupItems.size) }
                        items(groupItems.size, key = { groupItems[it].path }, contentType = { groupItems[it].type }) { idx ->
                            val m = groupItems[idx]; val originalIdx = pathIndexMap[m.path] ?: 0; val isVideo = remember(m.path) { m.path.substringAfterLast('.',"").lowercase() in VIDEO_EXTENSIONS }
                            val isSelected by remember(m.path) { derivedStateOf { m.path in selectedPaths } }
                            MediaTile(
                                medium = m,
                                isVideo = isVideo,
                                isSelected = isSelected,
                                isSelectionMode = hasSelection,
                                hasTag = m.path in taggedPaths,
                                showOverlays = showOverlays,
                                aspectRatio = 1f,
                                cornerShape = cornerShape,
                                cardColor = mediaCardColor,
                                itemSpacing = itemSpacing,
                                showFileName = viewSettings.showFileNames,
                                showVideoDuration = ctx.config.showVideoDurationOnThumbnails,
                                cropThumbnails = ctx.config.cropThumbnails,
                                showRating = ctx.config.showRatingOnThumbnails,
                                showFileType = ctx.config.showThumbnailFileTypes,
                                markFavorite = ctx.config.markFavoriteItems,
                                onClick = {
                                    if (hasSelection) {
                                        selectedPaths = if (m.path in selectedPaths) selectedPaths - m.path else selectedPaths + m.path
                                        rangeAnchorIndex = originalIdx
                                    } else openViewer(originalIdx)
                                },
                                onLongClick = {
                                    val anchor = rangeAnchorIndex
                                    selectedPaths = if (hasSelection && anchor != null) selectedPaths + pathsInDisplayRange(anchor, originalIdx) else selectedPaths + m.path
                                    rangeAnchorIndex = originalIdx
                                },
                                onSwipeToSelect = { selectedPaths = selectedPaths + m.path },
                                onPreviewClick = { peekState.show(m.path, isVideo) },
                                onBoundsChanged = { r -> dragSelection.registerItemBounds(m.path, r) },
                            )
                        }
                    }
                    }
                    }
                    }
                }
            }
            isMosaic -> {
                Column(Modifier.padding(top = contentTopInset)) {
                    if (hasFilter) FilterBreadcrumbs(
                        ratingFilter, activeTagName, activePathName, activeCollectionName,
                        minSizeFilter, dateRangeFilter,
                        displayMedia.size, onClearRatingFilter, onClearTagFilter, onClearPathFilter,
                        onClearSizeFilter, onClearDateFilter, onClearFilter
                    )
                    val quickTagsM = remember { ctx.config.quickTags.filter { it.isNotBlank() } }
                    QuickTagRow(quickTags = quickTagsM, visible = quickTagsM.isNotEmpty() && hasSelection, selectedCommonTags = selectedCommonTags, onToggleTag = { tag -> viewModel.toggleQuickTag(selectedPaths, tag) })
                    val grouped = state.monthGroups
                    val mosaicState = rememberLazyStaggeredGridState(initialFirstVisibleItemIndex = state.scrollIndex, initialFirstVisibleItemScrollOffset = state.scrollOffset)
                    LaunchedEffect(mosaicState) {
                        snapshotFlow { mosaicState.firstVisibleItemIndex to mosaicState.firstVisibleItemScrollOffset }
                            .collect { (i, o) -> viewModel.saveScrollPosition(i, o) }
                    }
                    ScrollToTopEffect(tabIndex) { mosaicState.animateScrollToItem(0) }
                    var showOverlaysStag by remember { mutableStateOf(true) }
                    val isScrollingStag by remember { derivedStateOf { mosaicState.isScrollInProgress } }
                    LaunchedEffect(isScrollingStag) {
                        if (isScrollingStag) showOverlaysStag = false
                        else { delay(300); showOverlaysStag = true }
                    }
                    LaunchedEffect(dragSelection.isDragging) {
                        if (dragSelection.isDragging) {
                            var targetIdx = mosaicState.firstVisibleItemIndex
                            while (dragSelection.isDragging) {
                                val s = dragSelection.autoScrollSpeed
                                if (s < -0.5f) targetIdx = maxOf(targetIdx - 1, 0)
                                else if (s > 0.5f) targetIdx++
                                else { kotlinx.coroutines.delay(50); continue }
                                mosaicState.animateScrollToItem(targetIdx, 0)
                                kotlinx.coroutines.delay((120 / kotlin.math.abs(s).coerceAtLeast(0.5f)).toLong().coerceIn(60, 200))
                            }
                        }
                    }
                    CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
                    Box(Modifier.dragSelectionGesture(dragSelection, enabled = hasSelection, staggeredGridState = mosaicState) { path -> selectedPaths = selectedPaths + path }) {
                    LazyVerticalStaggeredGrid(state = mosaicState, columns = StaggeredGridCells.Fixed(columnCount), reverseLayout = viewSettings.anchorBottom, contentPadding = PaddingValues(itemSpacing / 2)) {
                    grouped.forEach { (label, groupItems) ->
                        item(span = StaggeredGridItemSpan.FullLine) { MonthHeader(label = label, count = groupItems.size) }
                        items(groupItems.size, key = { groupItems[it].path }, contentType = { groupItems[it].type }) { idx ->
                            val m = groupItems[idx]; val originalIdx = pathIndexMap[m.path] ?: 0; val isVideo = remember(m.path) { m.path.substringAfterLast('.',"").lowercase() in VIDEO_EXTENSIONS }
                            if (!isVideo) LaunchedEffect(m.path) { viewModel.requestAspect(m.path) }
                            val isSelected by remember(m.path) { derivedStateOf { m.path in selectedPaths } }
                            MediaTile(
                                medium = m,
                                isVideo = isVideo,
                                isSelected = isSelected,
                                isSelectionMode = hasSelection,
                                hasTag = m.path in taggedPaths,
                                showOverlays = showOverlaysStag,
                                aspectRatio = if (isVideo) 1f else (state.aspectRatios[m.path] ?: 1f),
                                cornerShape = cornerShape,
                                cardColor = mediaCardColor,
                                itemSpacing = itemSpacing,
                                showFileName = viewSettings.showFileNames,
                                showVideoDuration = ctx.config.showVideoDurationOnThumbnails,
                                cropThumbnails = ctx.config.cropThumbnails,
                                showRating = ctx.config.showRatingOnThumbnails,
                                showFileType = ctx.config.showThumbnailFileTypes,
                                markFavorite = ctx.config.markFavoriteItems,
                                onClick = {
                                    if (hasSelection) {
                                        selectedPaths = if (m.path in selectedPaths) selectedPaths - m.path else selectedPaths + m.path
                                        rangeAnchorIndex = originalIdx
                                    } else openViewer(originalIdx)
                                },
                                onLongClick = {
                                    val anchor = rangeAnchorIndex
                                    selectedPaths = if (hasSelection && anchor != null) selectedPaths + pathsInDisplayRange(anchor, originalIdx) else selectedPaths + m.path
                                    rangeAnchorIndex = originalIdx
                                },
                                onSwipeToSelect = { selectedPaths = selectedPaths + m.path },
                                onPreviewClick = { peekState.show(m.path, isVideo) },
                                onBoundsChanged = { r -> dragSelection.registerItemBounds(m.path, r) },
                            )
                        }
                    }
                    }
                    }
                    }
                }
            }
            else -> {
                val grouped = state.monthGroups
                val listState = rememberLazyListState(initialFirstVisibleItemIndex = state.scrollIndex, initialFirstVisibleItemScrollOffset = state.scrollOffset)
                LaunchedEffect(listState) {
                    snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
                        .collect { (i, o) -> viewModel.saveScrollPosition(i, o) }
                }
                ScrollToTopEffect(tabIndex) { listState.animateScrollToItem(0) }
                Box(Modifier.padding(top = contentTopInset).dragSelectionGesture(dragSelection, enabled = hasSelection) { path -> selectedPaths = selectedPaths + path }) {
                LazyColumn(state = listState, reverseLayout = viewSettings.anchorBottom, contentPadding = PaddingValues(4.dp)) {
                    grouped.forEach { (label, groupItems) ->
                        stickyHeader { MonthHeader(label = label, count = groupItems.size) }
                        items(groupItems.size, key = { groupItems[it].path }, contentType = { groupItems[it].type }) { idx ->
                            val m = groupItems[idx]; val originalIdx = pathIndexMap[m.path] ?: 0; val isVideo = remember(m.path) { m.path.substringAfterLast('.',"").lowercase() in VIDEO_EXTENSIONS }
                            val isSelected by remember(m.path) { derivedStateOf { m.path in selectedPaths } }
                            MediaListRow(
                                medium = m,
                                isVideo = isVideo,
                                isSelected = isSelected,
                                hasSelection = hasSelection,
                                cardColor = mediaCardColor,
                                fileSizeLabel = formatFileSize(m.size),
                                onClick = {
                                    if (hasSelection) {
                                        selectedPaths = if (m.path in selectedPaths) selectedPaths - m.path else selectedPaths + m.path
                                        rangeAnchorIndex = originalIdx
                                    } else openViewer(originalIdx)
                                },
                                onLongClick = {
                                    val anchor = rangeAnchorIndex
                                    selectedPaths = if (hasSelection && anchor != null) selectedPaths + pathsInDisplayRange(anchor, originalIdx) else selectedPaths + m.path
                                    rangeAnchorIndex = originalIdx
                                },
                                onSwipeToSelect = { selectedPaths = selectedPaths + m.path },
                                onPreview = { openViewer(originalIdx) },
                                onBoundsChanged = { r -> dragSelection.registerItemBounds(m.path, r) },
                            )
                        }
                    }
                }
                }
            }
        }
        }
        AnimatedVisibility(visible=hasSelection,enter=slideInVertically(initialOffsetY={-it})+fadeIn(AppMotion.medium),exit=slideOutVertically(targetOffsetY={-it})+fadeOut(AppMotion.medium),modifier=Modifier.align(Alignment.TopCenter)) {
            SelectionTopAppBar(
                modifier = Modifier.onGloballyPositioned { selectionBarHeightPx = it.size.height },
                count = selectedPaths.size,
                onClose = { selectedPaths = emptySet() },
                onShare = {
                    val uris = ArrayList(selectedPaths.map { androidx.core.content.FileProvider.getUriForFile(ctx, "${ctx.packageName}.provider", File(it)) })
                    val allVideo = selectedPaths.all { it.substringAfterLast('.').lowercase() in VIDEO_EXTENSIONS }
                    val si = if (uris.size == 1) Intent(Intent.ACTION_SEND).apply { type = if (allVideo) "video/*" else "image/*"; putExtra(Intent.EXTRA_STREAM, uris.first()); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                    else Intent(Intent.ACTION_SEND_MULTIPLE).apply { type = "*/*"; putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                    ctx.startActivity(Intent.createChooser(si, ctx.getString(R.string.action_share)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); selectedPaths = emptySet()
                },
                onDelete = { if (ctx.config.skipDeleteConfirmation) { val d = selectedPaths.toSet(); viewModel.softDeletePaths(d); UndoManager.push(UndoAction(paths = d, type = UndoType.DELETE)); selectedPaths = emptySet() } else showDeleteConfirm = true },
                onSelectAll = { scope.launch { selectedPaths = if (hasFilter) viewModel.activePathsSortedFiltered().toSet() else viewModel.activePaths() } },
                onInvert = { scope.launch { val all = if (hasFilter) viewModel.activePathsSortedFiltered().toSet() else viewModel.activePaths(); selectedPaths = all - selectedPaths } },
                onCopy = { folderPickerIsMove = false; showFolderPicker = true },
                onMove = { folderPickerIsMove = true; showFolderPicker = true },
                onRate = { showRateTagSheet = true },
                onTags = { showRateTagSheet = true },
                onRename = { showRenameDialog = true },
                onInfo = { try { selectedPaths.firstOrNull()?.let { p -> (ctx as? android.app.Activity)?.let { a -> PropertiesDialog(a, p, false) } } } catch (e: Exception) { ctx.toast(ctx.getString(R.string.info_error, e.message), Toast.LENGTH_LONG) } },
            )
        }
        SnackbarHost(hostState=snackbarHostState,modifier=Modifier.align(Alignment.BottomCenter).padding(bottom = if (pendingUndo.isNotEmpty()) 64.dp else 0.dp))
        UndoBar(modifier = Modifier.align(Alignment.BottomCenter))
        // Selection-mode "peek" (see MediaTile's eye icon): a large, inline, tap-anywhere-to-dismiss
        // preview - deliberately not the full Viewer, so checking an item never costs the grid's
        // scroll position or risks the current selection the way navigating away and back would.
        // Must be a child of this same Box (not a sibling statement after it) to actually paint on
        // top - a same-named Box emitted as a separate top-level statement elsewhere in this function
        // composes and updates state fine but never becomes visible, since it isn't laid out as part
        // of this stack at all.
        peekState.path?.let { peekPath ->
            // Registered after (so it takes priority over) the selection-clearing BackHandler above -
            // otherwise system back while peeking fell through to that one and wiped the selection
            // instead of just closing the preview.
            BackHandler(enabled = true) { peekState.hide() }
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)).clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { peekState.hide() },
                ),
                contentAlignment = Alignment.Center,
            ) {
                if (peekState.isVideo) {
                    VideoThumbnail(videoPath = peekPath, modifier = Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.75f), contentScale = ContentScale.Fit, thumbnailSize = 1024)
                } else {
                    GalleryImage(path = peekPath, contentDescription = null, modifier = Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.75f), contentScale = ContentScale.Fit, thumbnailSize = 1024)
                }
            }
        }
        }
    }
    if (pullRefreshEnabled) {
        PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = { isRefreshing = true; pagingRefreshStarted = false; viewModel.refresh(); if (showPaged) lazyPagingItems.refresh(); scope.launch { kotlinx.coroutines.delay(15000); isRefreshing = false } }, modifier = Modifier.fillMaxSize(), content = mediaContent)
    } else {
        Box(Modifier.fillMaxSize(), content = mediaContent)
    }
    // Rate/Tag/Rename keep the selection alive on purpose: the core workflow (rename -> tag ->
    // rate -> move for freshly downloaded files) chains these actions on the same batch, and
    // forcing a re-select between every step was the single biggest UX cost in that flow. Move
    // and Delete still clear the selection since the files leave the current view either way.
    if (showRateTagSheet) {
        val batch = selectedPaths.toList()
        LaunchedEffect(Unit) { viewModel.loadAllTags() }
        RateAndTagSheet(
            batchCount = batch.size,
            currentRating = currentRating,
            onRate = { i -> currentRating = i; viewModel.setRatingFor(batch, i) },
            initialTags = selectedCommonTags,
            onAddTag = { viewModel.addTagFor(batch, it) },
            onRemoveTag = { viewModel.removeTagFor(batch, it) },
            suggestedTags = state.allTags,
            suggestedTagCounts = state.tagCounts,
            onDismiss = { showRateTagSheet = false },
        )
    }
    if (showFolderPicker) { val batch=selectedPaths.toList(); FolderPickerSheet(isMoveOperation=folderPickerIsMove,sourcePaths=batch,onDismiss={showFolderPicker=false;selectedPaths=emptySet()}) }
    if (showRenameDialog) { val batch=selectedPaths.toList(); RenameDialog(paths=batch,onRenamed={mapping->selectedPaths=selectedPaths.map{mapping[it]?:it}.toSet()},onDismiss={showRenameDialog=false;viewModel.silentRefresh()}) }
    if (showDeleteConfirm) {
        val itemsCnt = selectedPaths.size
        val itemsText = ctx.resources.getQuantityString(org.fossify.commons.R.plurals.delete_items, itemsCnt, itemsCnt)
        val question = ctx.getString(if (ctx.config.useRecycleBin) org.fossify.commons.R.string.move_to_recycle_bin_confirmation else org.fossify.commons.R.string.deletion_confirmation, itemsText)
        ConfirmDestructive(
            title = stringResource(org.fossify.commons.R.string.delete),
            text = question,
            confirmLabel = stringResource(org.fossify.commons.R.string.delete),
            onConfirm = {
                showDeleteConfirm = false
                val d = selectedPaths.toSet(); viewModel.softDeletePaths(d); UndoManager.push(UndoAction(paths = d, type = UndoType.DELETE)); selectedPaths = emptySet()
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }
}

@Composable
private fun FilterBreadcrumbs(
    ratingFilter: Int, activeTagName: String?, activePathName: String?, activeCollectionName: String?,
    minSizeFilter: Long, dateRangeFilter: Int,
    resultCount: Int, onClearRating: () -> Unit, onClearTag: () -> Unit, onClearPath: () -> Unit,
    onClearSize: () -> Unit, onClearDate: () -> Unit,
    onClearAll: () -> Unit
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (activeCollectionName != null) ActiveFilterChip(stringResource(R.string.filter_collection, activeCollectionName)) { onClearPath() }
        if (activePathName != null) ActiveFilterChip(stringResource(R.string.filter_search_query, activePathName)) { onClearPath() }
        if (activeTagName != null) ActiveFilterChip(activeTagName.take(24).let { if (activeTagName.length > 24) "$it…" else it }) { onClearTag() }
        if (ratingFilter > 0) ActiveFilterChip(stringResource(R.string.filter_rating, ratingFilter)) { onClearRating() }
        if (minSizeFilter > 0) ActiveFilterChip("> ${formatFileSize(minSizeFilter)}") { onClearSize() }
        if (dateRangeFilter > 0) ActiveFilterChip(
            when (dateRangeFilter) {
                1 -> stringResource(R.string.today); 2 -> stringResource(R.string.date_range_last_7_days); 3 -> stringResource(R.string.date_range_last_30_days); 4 -> stringResource(R.string.date_range_last_year); else -> ""
            }
        ) { onClearDate() }
        Text(stringResource(R.string.result_count, resultCount), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        androidx.compose.material3.AssistChip(onClick = onClearAll, label = { Text(stringResource(R.string.clear_all_filters)) }, leadingIcon = { Icon(Icons.Default.Close, null, Modifier.size(18.dp)) })
    }
}
@Composable
private fun ActiveFilterChip(label:String,onRemove:()->Unit){
    androidx.compose.material3.InputChip(
        selected=true,
        onClick=onRemove,
        label={Text(label)},
        trailingIcon={Icon(Icons.Default.Close,stringResource(R.string.cd_remove_filter, label),Modifier.size(18.dp))},
    )
}

private fun formatFileSize(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1_000_000_000.0)
    bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000 -> "%.0f KB".format(bytes / 1_000.0)
    else -> "$bytes B"
}
@Composable
private fun MonthHeader(label:String,count:Int){Surface(Modifier.fillMaxWidth(),color=MaterialTheme.colorScheme.background){Row(Modifier.padding(horizontal=12.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically){Text(label,style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.SemiBold,color=MaterialTheme.colorScheme.onSurface);Spacer(Modifier.width(8.dp));Text("$count",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}}}

// Grid/mosaic layouts render month headers as plain scrolling items (LazyVerticalGrid/
// LazyVerticalStaggeredGrid have no stickyHeader API, unlike LazyColumn's list branch below which
// already gets real sticky headers) - so mid-scroll there was no way to tell which month you were
// looking at. This walks backward from the first visible row to the nearest preceding header,
// rendered as a small floating pill overlay instead of a true pinned-item replacement.
private fun currentSectionLabel(rows: List<PagedRow>, firstVisibleIndex: Int): String? {
    if (rows.isEmpty()) return null
    var i = firstVisibleIndex.coerceIn(0, rows.size - 1)
    while (i >= 0) {
        val r = rows[i]
        if (r is PagedRow.Header) return r.label
        i--
    }
    return null
}

@Composable
private fun FloatingSectionLabel(label: String?, isScrolling: Boolean, modifier: Modifier = Modifier) {
    // Only while actively scrolling, not permanently - it used to float for as long as any content
    // was loaded (including with a modal sheet open over the grid), duplicating the inline month
    // header directly below it for no reason once the user had stopped moving.
    AnimatedVisibility(visible = label != null && isScrolling, modifier = modifier, enter = fadeIn(AppMotion.short), exit = fadeOut(AppMotion.short)) {
        Surface(shape = RoundedCornerShape(Radius.xl), color = MaterialTheme.colorScheme.surfaceContainerHigh, shadowElevation = 2.dp, tonalElevation = 2.dp) {
            Text(
                label.orEmpty(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                // liveRegion so TalkBack announces the current month as it changes - this pill is
                // the only place that information exists (grid/mosaic have no sticky header, unlike
                // the list branch), so without it a screen-reader user scrolling the grid has no way
                // to tell which month they're currently looking at.
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp).semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

/** Row model for the paged (unfiltered, non-override) grid: a flat, contiguous-run-based month
 * grouping over whatever [androidx.paging.compose.LazyPagingItems] has loaded so far. Unlike the
 * legacy [MonthGroup] (a LinkedHashMap keyed by label, which merges non-contiguous same-month runs
 * under one earlier header), this never re-groups across non-adjacent runs - the correct behavior
 * once the list isn't fully materialized in memory. */
private sealed interface PagedRow {
    data class Header(val label: String, val count: Int) : PagedRow
    data class Item(val pagingIndex: Int) : PagedRow
}

// Paging3's itemCount/itemSnapshotList can change between when `rows` (remember(itemCount){...})
// was memoized and when the LazyColumn/LazyGrid content lambda actually evaluates key/contentType
// for each row in that same recomposition pass (both are read eagerly, for every row, to build the
// item provider) - so `rows` can transiently reference an index one step ahead of what
// itemSnapshotList currently holds. peek()/get() throw IndexOutOfBoundsException in that case
// instead of returning null, so bounds-check before ever calling them.
private fun safePeek(items: androidx.paging.compose.LazyPagingItems<Medium>, index: Int): Medium? =
    if (index in 0 until items.itemCount) items.peek(index) else null

private fun safeGet(items: androidx.paging.compose.LazyPagingItems<Medium>, index: Int): Medium? =
    if (index in 0 until items.itemCount) items[index] else null

// Builds the Header/Item row model incrementally: Paging3 only ever *appends* to this list (the grid
// is date-sorted, placeholders disabled, no prepend), so each newly loaded page only needs its own new
// items scanned - not the whole (potentially very large) loaded window re-scanned from scratch on every
// append the way a plain rebuild does. Scoped per filter+sort via [rememberPagedRows]; a shrink in item
// count (a PagingSource invalidation/reload) resets and rebuilds. monthLabelFor is the expensive bit
// (date formatting per item), so only running it on the delta is the actual win.
private class PagedRowsAccumulator {
    private val rows = ArrayList<PagedRow>()
    private var lastLabel: String? = null
    private var lastHeaderPos = -1
    private var processed = 0

    fun extendTo(snapshot: List<Medium?>): List<PagedRow> {
        val size = snapshot.size
        if (size < processed) reset()
        var idx = processed
        while (idx < size) {
            val label = snapshot[idx]?.let { MediaViewModel.monthLabelFor(it) }
            if (label != null) {
                if (label != lastLabel) {
                    lastHeaderPos = rows.size
                    rows.add(PagedRow.Header(label, 1))
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
        // Shallow copy so each emission is a distinct, stable list instance for Compose - O(n) in
        // references only (no per-item label recompute or PagedRow allocation), and only on append.
        return ArrayList(rows)
    }

    private fun reset() {
        rows.clear(); lastLabel = null; lastHeaderPos = -1; processed = 0
    }
}

@Composable
private fun rememberPagedRows(
    lazyPagingItems: androidx.paging.compose.LazyPagingItems<Medium>,
    filter: org.fossify.gallery.viewmodels.MediaFilter,
    sortBy: SortField,
    sortDesc: Boolean,
): List<PagedRow> {
    // Accumulator resets whenever the filter/sort intent changes (a new PagingSource generation) - see
    // the note at the old call sites about a stale month header surviving one frame past a filter switch.
    val acc = remember(filter, sortBy, sortDesc) { PagedRowsAccumulator() }
    return remember(lazyPagingItems.itemCount, filter, sortBy, sortDesc) {
        acc.extendTo(lazyPagingItems.itemSnapshotList)
    }
}

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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import org.fossify.commons.dialogs.PropertiesDialog
import org.fossify.commons.extensions.toast
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
import org.fossify.gallery.compose.util.ScrollToTopEffect
import org.fossify.gallery.compose.util.dragSelectionGesture
import org.fossify.gallery.compose.util.rememberSelectionDragState
import org.fossify.gallery.helpers.UndoAction
import org.fossify.gallery.helpers.UndoManager
import org.fossify.gallery.helpers.UndoType
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.VIDEO_EXTENSIONS
import org.fossify.gallery.models.Medium
import org.fossify.gallery.viewmodels.MediaViewModel
import java.io.File

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
    var selectedPaths by rememberSaveable(stateSaver = selectionSaver) { mutableStateOf<Set<String>>(emptySet()) }
    val dragSelection = rememberSelectionDragState()
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

    // Viewer swipe-through needs the FULL sorted path list, not just what the grid has paged in so
    // far - fetched fresh on demand (cheap: paths only, no thumbnails) so opening any item can swipe
    // through the entire library, not just the loaded window.
    fun openViewerPaged(index: Int) {
        scope.launch {
            val paths = viewModel.activePathsSorted()
            onNavigateToViewer?.invoke(paths, index)
        }
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

    @Composable
    fun PagedContent() {
        when {
            isGrid -> {
                Column(Modifier.padding(top = contentTopInset)) {
                    if (hasFilter) FilterBreadcrumbs(
                        ratingFilter, activeTagName, activePathName, activeCollectionName,
                        minSizeFilter, dateRangeFilter,
                        lazyPagingItems.itemCount, onClearRatingFilter, onClearTagFilter, onClearPathFilter,
                        onClearSizeFilter, onClearDateFilter, onClearFilter
                    )
                    val quickTags = remember { ctx.config.quickTags.filter { it.isNotBlank() } }
                    QuickTagRow(quickTags = quickTags, visible = quickTags.isNotEmpty() && hasSelection, selectedCommonTags = selectedCommonTags, onToggleTag = { tag -> viewModel.toggleQuickTag(selectedPaths, tag) })
                    // Keyed on the filter/sort intent too, not just itemCount - observed live that a filter
                    // switch landing on a coincidentally-similar item count could otherwise leave `rows` (and
                    // its PagedRow.Header labels/counts) built from the previous PagingSource's snapshot for
                    // one extra frame, showing a stale month header while the row content underneath it had
                    // already updated to the new filtered set.
                    val rows = remember(lazyPagingItems.itemCount, state.filter, viewSettings.sortBy, viewSettings.sortDesc) { buildPagedRows(lazyPagingItems.itemSnapshotList) }
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
                    Box(Modifier.dragSelectionGesture(dragSelection, gridState = gridState) { path -> selectedPaths = selectedPaths + path }) {
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
                                        onClick = { if (hasSelection) selectedPaths = if (m.path in selectedPaths) selectedPaths - m.path else selectedPaths + m.path else openViewerPaged(row.pagingIndex) },
                                        onLongClick = { selectedPaths = selectedPaths + m.path },
                                        onSwipeToSelect = { selectedPaths = selectedPaths + m.path },
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
                    }
                    }
                }
            }
            isMosaic -> {
                Column(Modifier.padding(top = contentTopInset)) {
                    if (hasFilter) FilterBreadcrumbs(
                        ratingFilter, activeTagName, activePathName, activeCollectionName,
                        minSizeFilter, dateRangeFilter,
                        lazyPagingItems.itemCount, onClearRatingFilter, onClearTagFilter, onClearPathFilter,
                        onClearSizeFilter, onClearDateFilter, onClearFilter
                    )
                    val quickTagsM = remember { ctx.config.quickTags.filter { it.isNotBlank() } }
                    QuickTagRow(quickTags = quickTagsM, visible = quickTagsM.isNotEmpty() && hasSelection, selectedCommonTags = selectedCommonTags, onToggleTag = { tag -> viewModel.toggleQuickTag(selectedPaths, tag) })
                    val rows = remember(lazyPagingItems.itemCount, state.filter, viewSettings.sortBy, viewSettings.sortDesc) { buildPagedRows(lazyPagingItems.itemSnapshotList) }
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
                    Box(Modifier.dragSelectionGesture(dragSelection, staggeredGridState = mosaicState) { path -> selectedPaths = selectedPaths + path }) {
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
                                            onClick = { if (hasSelection) selectedPaths = if (m.path in selectedPaths) selectedPaths - m.path else selectedPaths + m.path else openViewerPaged(row.pagingIndex) },
                                            onLongClick = { selectedPaths = selectedPaths + m.path },
                                            onSwipeToSelect = { selectedPaths = selectedPaths + m.path },
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
            else -> {
                val rows = remember(lazyPagingItems.itemCount, state.filter, viewSettings.sortBy, viewSettings.sortDesc) { buildPagedRows(lazyPagingItems.itemSnapshotList) }
                val listState = rememberLazyListState(initialFirstVisibleItemIndex = state.scrollIndex, initialFirstVisibleItemScrollOffset = state.scrollOffset)
                LaunchedEffect(listState) {
                    snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
                        .collect { (i, o) -> viewModel.saveScrollPosition(i, o) }
                }
                ScrollToTopEffect(tabIndex) { listState.animateScrollToItem(0) }
                Box(Modifier.padding(top = contentTopInset).dragSelectionGesture(dragSelection) { path -> selectedPaths = selectedPaths + path }) {
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
                                            onClick = { if (hasSelection) selectedPaths = if (m.path in selectedPaths) selectedPaths - m.path else selectedPaths + m.path else openViewerPaged(row.pagingIndex) },
                                            onLongClick = { selectedPaths = selectedPaths + m.path },
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
                    else -> PagedContent()
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
                    Box(Modifier.dragSelectionGesture(dragSelection, gridState = gridState) { path -> selectedPaths = selectedPaths + path }) {
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
                                onClick = { if (hasSelection) selectedPaths = if (m.path in selectedPaths) selectedPaths - m.path else selectedPaths + m.path else openViewer(originalIdx) },
                                onLongClick = { selectedPaths = selectedPaths + m.path },
                                onSwipeToSelect = { selectedPaths = selectedPaths + m.path },
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
                    Box(Modifier.dragSelectionGesture(dragSelection, staggeredGridState = mosaicState) { path -> selectedPaths = selectedPaths + path }) {
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
                                onClick = { if (hasSelection) selectedPaths = if (m.path in selectedPaths) selectedPaths - m.path else selectedPaths + m.path else openViewer(originalIdx) },
                                onLongClick = { selectedPaths = selectedPaths + m.path },
                                onSwipeToSelect = { selectedPaths = selectedPaths + m.path },
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
                Box(Modifier.padding(top = contentTopInset).dragSelectionGesture(dragSelection) { path -> selectedPaths = selectedPaths + path }) {
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
                                onClick = { if (hasSelection) selectedPaths = if (m.path in selectedPaths) selectedPaths - m.path else selectedPaths + m.path else openViewer(originalIdx) },
                                onLongClick = { selectedPaths = selectedPaths + m.path },
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
        if (activePathName != null) ActiveFilterChip(stringResource(R.string.filter_path, activePathName)) { onClearPath() }
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

private fun buildPagedRows(snapshot: List<Medium?>): List<PagedRow> {
    val rows = ArrayList<PagedRow>(snapshot.size + 8)
    var lastLabel: String? = null
    var lastHeaderPos = -1
    for (idx in snapshot.indices) {
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
    }
    return rows
}

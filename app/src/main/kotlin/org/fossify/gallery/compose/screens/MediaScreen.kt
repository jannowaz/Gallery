package org.fossify.gallery.compose.screens
import org.fossify.gallery.compose.theme.Radius

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import org.fossify.commons.dialogs.PropertiesDialog
import org.fossify.commons.extensions.toast
import org.fossify.gallery.activities.ComposeVideoPlayerActivity
import org.fossify.gallery.activities.ComposeViewerActivity
import org.fossify.gallery.compose.components.GalleryImage
import org.fossify.gallery.compose.components.MediaTile
import org.fossify.gallery.R
import androidx.compose.ui.res.stringResource
import org.fossify.gallery.compose.components.EmptyState
import org.fossify.gallery.compose.components.SelectionTopAppBar
import org.fossify.gallery.compose.components.RenameDialog
import org.fossify.gallery.compose.components.UndoBar
import org.fossify.gallery.compose.components.StarRatingDialog
import org.fossify.gallery.compose.components.TagInputDialog
import org.fossify.gallery.compose.util.dragSelectionGesture
import org.fossify.gallery.compose.util.rememberSelectionDragState
import org.fossify.gallery.compose.util.selectableItem
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
    tagFilterPaths: Set<String>? = null,
    pathFilter: Set<String>? = null,
    activeTagName: String? = null,
    activePathName: String? = null,
    activeCollectionName: String? = null,
    onClearFilter: () -> Unit = {},
    onClearRatingFilter: () -> Unit = {},
    onClearTagFilter: () -> Unit = {},
    onClearPathFilter: () -> Unit = {},
    mediaOverride: List<Medium>? = null,
    refreshTrigger: Int = 0,
    onNavigateToViewer: ((paths: List<String>, startIndex: Int) -> Unit)? = null,
    scrollToPath: String = "",
    onClearScrollToPath: () -> Unit = {},
    onSelectionActiveChanged: (Boolean) -> Unit = {},
) {
    val ctx = LocalContext.current
    val viewModel: MediaViewModel = viewModel()
    val state by viewModel.state.collectAsState()
    val selectionSaver = remember { listSaver<Set<String>, String>(save = { it.toList() }, restore = { it.toSet() }) }
    LaunchedEffect(refreshTrigger) { if (refreshTrigger > 0) { if (state.allMedia.isNotEmpty()) viewModel.silentRefresh() else viewModel.refresh() } }
    LaunchedEffect(viewSettings.sortBy, viewSettings.sortDesc) { if (mediaOverride == null) viewModel.setSort(viewSettings.sortBy, viewSettings.sortDesc) }
    LaunchedEffect(mediaOverride) { viewModel.setOverride(mediaOverride) }
    LaunchedEffect(ratingFilter, tagFilterPaths, pathFilter) { viewModel.setFilter(ratingFilter, tagFilterPaths, pathFilter) }
    var selectedPaths by rememberSaveable(stateSaver = selectionSaver) { mutableStateOf<Set<String>>(emptySet()) }
    val dragSelection = rememberSelectionDragState()
    var showRatingDialog by remember { mutableStateOf(false) }
    var showTagsDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showFolderPicker by remember { mutableStateOf(false) }
    var folderPickerIsMove by remember { mutableStateOf(false) }
    var currentRating by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val pendingUndo by UndoManager.actions.collectAsState()
    var heroRect by remember { mutableStateOf<android.graphics.Rect?>(null) }
    val taggedPaths = state.taggedPaths
    val selectedCommonTags = state.selectedCommonTags
    LaunchedEffect(selectedPaths) { viewModel.loadCommonTags(selectedPaths) }
    val columnCount = viewSettings.columnCount
    val isGrid = viewSettings.viewType == ViewType.GRID
    val isMosaic = viewSettings.viewType == ViewType.MOSAIC
    val hasFilter = ratingFilter > 0 || tagFilterPaths != null || pathFilter != null
    val displayMedia = state.displayMedia
    val pathIndexMap = remember(displayMedia) { displayMedia.withIndex().associate { it.value.path to it.index } }
    val cornerShape = if (viewSettings.roundedCorners) RoundedCornerShape(Radius.sm) else RoundedCornerShape(0.dp)
    val itemSpacing = viewSettings.spacing.dp
    val mediaCardColor = when (viewSettings.displayMode) { DisplayMode.COMPACT,DisplayMode.NORMAL->MaterialTheme.colorScheme.surface; DisplayMode.DARK->MaterialTheme.colorScheme.surfaceVariant }

    fun openViewer(index: Int) {
        val paths = displayMedia.map { it.path }
        val navigate = onNavigateToViewer
        if (navigate != null) {
            navigate(paths, index)
        } else {
            ctx.startActivity(Intent(ctx, ComposeViewerActivity::class.java).apply {
                putStringArrayListExtra("PATHS", ArrayList(paths)); putExtra("START_INDEX", index)
                heroRect?.let { putExtra("HERO_LEFT",it.left.toFloat()); putExtra("HERO_TOP",it.top.toFloat()); putExtra("HERO_WIDTH",it.width().toFloat()); putExtra("HERO_HEIGHT",it.height().toFloat()) }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }

    val hasSelection = selectedPaths.isNotEmpty()
    LaunchedEffect(hasSelection) { onSelectionActiveChanged(hasSelection) }
    var selectionBarHeightPx by remember { mutableIntStateOf(0) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val contentTopInset by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (hasSelection) with(density) { selectionBarHeightPx.toDp() } else 0.dp,
        label = "selectionTopInset",
    )

    LaunchedEffect(state.error) {
        val err = state.error
        if (!err.isNullOrBlank() && displayMedia.isNotEmpty()) {
            snackbarHostState.showSnackbar(err)
            viewModel.clearError()
        }
    }

    var isRefreshing by remember { mutableStateOf(false) }
    PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = { isRefreshing = true; viewModel.refresh(); scope.launch { kotlinx.coroutines.delay(800); isRefreshing = false } }, modifier = Modifier.fillMaxSize()) {
        BackHandler(enabled = hasSelection) { selectedPaths = emptySet() }
        Box(modifier = modifier.fillMaxSize()) {
        when {
            mediaOverride != null && mediaOverride.isEmpty() -> {
                EmptyState(Icons.Default.Search, stringResource(R.string.no_media_in_folder))
            }
            state.isLoading && !hasFilter && mediaOverride == null -> MediaSkeleton(columns = columnCount)
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
                    if (hasFilter) FilterBreadcrumbs(ratingFilter,activeTagName,activePathName,activeCollectionName,displayMedia.size,onClearRatingFilter,onClearTagFilter,onClearPathFilter,onClearFilter)
                    val quickTags = remember { ctx.config.quickTags.filter { it.isNotBlank() } }
                    AnimatedVisibility(visible = quickTags.isNotEmpty() && hasSelection, enter = fadeIn() + slideInVertically { -it }, exit = fadeOut() + slideOutVertically { -it }) {
                    if (quickTags.isNotEmpty() && hasSelection) {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            quickTags.forEach { tag ->
                                val active = tag in selectedCommonTags
                                Surface(
                                    onClick = { viewModel.toggleQuickTag(selectedPaths, tag) },
                                    shape = RoundedCornerShape(Radius.lg),
                                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                ) {
                                    Text(tag, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    }
                    val grouped = state.monthGroups
                    val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = state.scrollIndex, initialFirstVisibleItemScrollOffset = state.scrollOffset)
                    LaunchedEffect(gridState) {
                        snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }
                            .collect { (i, o) -> viewModel.saveScrollPosition(i, o) }
                    }
                    var showOverlays by remember { mutableStateOf(true) }
                    val isScrolling by remember { derivedStateOf { gridState.isScrollInProgress } }
                    val shouldLoadMore by remember { derivedStateOf {
                        val info = gridState.layoutInfo
                        val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                        lastVisible >= info.totalItemsCount - 6 && info.totalItemsCount > 0
                    } }
                    LaunchedEffect(shouldLoadMore) { if (shouldLoadMore && !hasFilter) viewModel.loadMore() }
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
                            MediaTile(
                                medium = m,
                                isVideo = isVideo,
                                isSelected = m.path in selectedPaths,
                                isSelectionMode = hasSelection,
                                hasTag = m.path in taggedPaths,
                                showOverlays = showOverlays,
                                aspectRatio = 1f,
                                cornerShape = cornerShape,
                                cardColor = mediaCardColor,
                                itemSpacing = itemSpacing,
                                showFileName = viewSettings.showFileNames,
                                showVideoDuration = ctx.config.showVideoDurationOnThumbnails,
                                onClick = { if (hasSelection) selectedPaths = if (m.path in selectedPaths) selectedPaths - m.path else selectedPaths + m.path else openViewer(originalIdx) },
                                onLongClick = { selectedPaths = selectedPaths + m.path },
                                onSwipeToSelect = { selectedPaths = selectedPaths + m.path },
                                onPreview = { openViewer(originalIdx) },
                                onBoundsChanged = { r -> heroRect = android.graphics.Rect(r.left.toInt(), r.top.toInt(), r.right.toInt(), r.bottom.toInt()); dragSelection.registerItemBounds(m.path, r) },
                            )
                        }
                    }
                    if (state.isLoadingMore) {
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
                    if (hasFilter) FilterBreadcrumbs(ratingFilter,activeTagName,activePathName,activeCollectionName,displayMedia.size,onClearRatingFilter,onClearTagFilter,onClearPathFilter,onClearFilter)
                    val quickTagsM = remember { ctx.config.quickTags.filter { it.isNotBlank() } }
                    AnimatedVisibility(visible = quickTagsM.isNotEmpty() && hasSelection, enter = fadeIn() + slideInVertically { -it }, exit = fadeOut() + slideOutVertically { -it }) {
                    if (quickTagsM.isNotEmpty() && hasSelection) { Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) { quickTagsM.forEach { tag -> val active = tag in selectedCommonTags; Surface(onClick = { viewModel.toggleQuickTag(selectedPaths, tag) }, shape = RoundedCornerShape(Radius.lg), color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant) { Text(tag, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant) } } } }
                    }
                    val grouped = state.monthGroups
                    val mosaicState = rememberLazyStaggeredGridState(initialFirstVisibleItemIndex = state.scrollIndex, initialFirstVisibleItemScrollOffset = state.scrollOffset)
                    LaunchedEffect(mosaicState) {
                        snapshotFlow { mosaicState.firstVisibleItemIndex to mosaicState.firstVisibleItemScrollOffset }
                            .collect { (i, o) -> viewModel.saveScrollPosition(i, o) }
                    }
                    var showOverlaysStag by remember { mutableStateOf(true) }
                    val isScrollingStag by remember { derivedStateOf { mosaicState.isScrollInProgress } }
                    val shouldLoadMoreStag by remember { derivedStateOf {
                        val info = mosaicState.layoutInfo
                        val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                        lastVisible >= info.totalItemsCount - 6 && info.totalItemsCount > 0
                    } }
                    LaunchedEffect(shouldLoadMoreStag) { if (shouldLoadMoreStag && !hasFilter) viewModel.loadMore() }
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
                            MediaTile(
                                medium = m,
                                isVideo = isVideo,
                                isSelected = m.path in selectedPaths,
                                isSelectionMode = hasSelection,
                                hasTag = m.path in taggedPaths,
                                showOverlays = showOverlaysStag,
                                aspectRatio = if (isVideo) 1f else (state.aspectRatios[m.path] ?: 1f),
                                cornerShape = cornerShape,
                                cardColor = mediaCardColor,
                                itemSpacing = itemSpacing,
                                showFileName = viewSettings.showFileNames,
                                showVideoDuration = ctx.config.showVideoDurationOnThumbnails,
                                onClick = { if (hasSelection) selectedPaths = if (m.path in selectedPaths) selectedPaths - m.path else selectedPaths + m.path else openViewer(originalIdx) },
                                onLongClick = { selectedPaths = selectedPaths + m.path },
                                onSwipeToSelect = { selectedPaths = selectedPaths + m.path },
                                onPreview = { openViewer(originalIdx) },
                                onBoundsChanged = { r -> heroRect = android.graphics.Rect(r.left.toInt(), r.top.toInt(), r.right.toInt(), r.bottom.toInt()); dragSelection.registerItemBounds(m.path, r) },
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
                Box(Modifier.padding(top = contentTopInset).dragSelectionGesture(dragSelection) { path -> selectedPaths = selectedPaths + path }) {
                LazyColumn(state = listState, reverseLayout = viewSettings.anchorBottom, contentPadding = PaddingValues(4.dp)) {
                    grouped.forEach { (label, groupItems) ->
                        stickyHeader { MonthHeader(label = label, count = groupItems.size) }
                        items(groupItems.size, key = { groupItems[it].path }, contentType = { groupItems[it].type }) { idx ->
                            val m = groupItems[idx]; val originalIdx = pathIndexMap[m.path] ?: 0; val isVideo = remember(m.path) { m.path.substringAfterLast('.',"").lowercase() in VIDEO_EXTENSIONS }
                            val isSelected by remember(m.path) { derivedStateOf { m.path in selectedPaths } }
                            var lastBoundsUpdate by remember { mutableLongStateOf(0L) }
                            Surface(modifier = Modifier.fillMaxWidth().background(mediaCardColor,RoundedCornerShape(Radius.sm)).onGloballyPositioned{coords->val p=coords.positionInWindow();val s=coords.size;heroRect=android.graphics.Rect(p.x.toInt(),p.y.toInt(),(p.x+s.width).toInt(),(p.y+s.height).toInt());val now=System.currentTimeMillis();if(now-lastBoundsUpdate>300){lastBoundsUpdate=now;dragSelection.registerItemBounds(m.path,androidx.compose.ui.geometry.Rect(p,androidx.compose.ui.geometry.Size(s.width.toFloat(),s.height.toFloat())))}}.selectableItem(isSelectionMode=hasSelection,onClick={if(hasSelection)selectedPaths=if(m.path in selectedPaths)selectedPaths-m.path else selectedPaths+m.path else openViewer(originalIdx)},onLongClick={selectedPaths=selectedPaths+m.path},onSwipeToSelect={selectedPaths=selectedPaths+m.path}),color=Color.Transparent) {
                                Row(Modifier.padding(horizontal=12.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
                                    Box(Modifier.size(56.dp).clip(RoundedCornerShape(Radius.sm))){if(isVideo)VideoThumbnail(videoPath=m.path,modifier=Modifier.fillMaxSize(),contentScale=ContentScale.Crop) else GalleryImage(path=m.path,contentDescription=m.name,modifier=Modifier.fillMaxSize(),contentScale=ContentScale.Crop,placeholderIconSize=18.dp)}
                                    Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)){Text(m.name,style=MaterialTheme.typography.bodyMedium,maxLines=1,overflow=TextOverflow.Ellipsis);Text(formatFileSize(m.size),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                                    if(hasSelection){IconButton(onClick={openViewer(originalIdx)},modifier=Modifier.size(36.dp)){Icon(Icons.Default.Visibility,stringResource(R.string.cd_preview),tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(20.dp))}}
                                    if(m.path in selectedPaths) Icon(Icons.Default.CheckCircle,stringResource(R.string.cd_selected),tint=MaterialTheme.colorScheme.primary)
                                }
                            }
                            HorizontalDivider(Modifier.padding(start=76.dp),color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.3f))
                        }
                    }
                }
                }
            }
        }
        AnimatedVisibility(visible=hasSelection,enter=slideInVertically(initialOffsetY={-it})+fadeIn(),exit=slideOutVertically(targetOffsetY={-it})+fadeOut(),modifier=Modifier.align(Alignment.TopCenter)) {
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
                onDelete = { val d = selectedPaths.toSet(); viewModel.softDeletePaths(d); UndoManager.push(UndoAction(paths = d, type = UndoType.DELETE)); selectedPaths = emptySet() },
                onSelectAll = { selectedPaths = (if (hasFilter) displayMedia.map { it.path } else viewModel.allMediaPaths()).toSet() },
                onInvert = { val all = (if (hasFilter) displayMedia.map { it.path } else viewModel.allMediaPaths()).toSet(); selectedPaths = all - selectedPaths },
                onCopy = { folderPickerIsMove = false; showFolderPicker = true },
                onMove = { folderPickerIsMove = true; showFolderPicker = true },
                onRate = { showRatingDialog = true },
                onTags = { showTagsDialog = true },
                onRename = { showRenameDialog = true },
                onInfo = { try { selectedPaths.firstOrNull()?.let { p -> (ctx as? android.app.Activity)?.let { a -> PropertiesDialog(a, p, false) } } } catch (e: Exception) { ctx.toast(ctx.getString(R.string.info_error, e.message), Toast.LENGTH_LONG) } },
            )
        }
        SnackbarHost(hostState=snackbarHostState,modifier=Modifier.align(Alignment.BottomCenter).padding(bottom = if (pendingUndo.isNotEmpty()) 64.dp else 0.dp))
        UndoBar(modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
    if (showRatingDialog) { val batch=selectedPaths.toList(); StarRatingDialog(currentRating=currentRating,onRate={i->currentRating=i;viewModel.setRatingFor(batch,i);selectedPaths=emptySet();showRatingDialog=false},onDismiss={showRatingDialog=false}) }
    if (showTagsDialog) { val batch=selectedPaths.toList(); LaunchedEffect(Unit){viewModel.loadAllTags()}; TagInputDialog(initialTags=selectedCommonTags,suggestedTags=state.allTags,suggestedTagCounts=state.tagCounts,onAddTag={viewModel.addTagFor(batch,it)},onRemoveTag={viewModel.removeTagFor(batch,it)},onDismiss={showTagsDialog=false;selectedPaths=emptySet()},batchCount=batch.size) }
    if (showFolderPicker) { val batch=selectedPaths.toList(); FolderPickerSheet(isMoveOperation=folderPickerIsMove,sourcePaths=batch,onDismiss={showFolderPicker=false;selectedPaths=emptySet()}) }
    if (showRenameDialog) { val batch=selectedPaths.toList(); RenameDialog(paths=batch,onDismiss={showRenameDialog=false;selectedPaths=emptySet();viewModel.refresh()}) }
}

@Composable
private fun FilterBreadcrumbs(ratingFilter:Int,activeTagName:String?,activePathName:String?,activeCollectionName:String?,resultCount:Int,onClearRating:()->Unit,onClearTag:()->Unit,onClearPath:()->Unit,onClearAll:()->Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=6.dp).horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
        if(activeCollectionName!=null) ActiveFilterChip(stringResource(R.string.filter_collection, activeCollectionName)){onClearPath()}
        if(activePathName!=null) ActiveFilterChip(stringResource(R.string.filter_path, activePathName)){onClearPath()}
        if(activeTagName!=null) ActiveFilterChip(activeTagName.take(24).let{if(activeTagName.length>24)"$it…" else it}){onClearTag()}
        if(ratingFilter>0) ActiveFilterChip(stringResource(R.string.filter_rating, ratingFilter)){onClearRating()}
        Text(stringResource(R.string.result_count, resultCount),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        androidx.compose.material3.AssistChip(onClick=onClearAll,label={Text(stringResource(R.string.clear_all_filters))},leadingIcon={Icon(Icons.Default.Close,null,Modifier.size(18.dp))})
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

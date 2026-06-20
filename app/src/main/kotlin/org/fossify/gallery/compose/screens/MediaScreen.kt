package org.fossify.gallery.compose.screens

import android.content.Intent
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import org.fossify.commons.dialogs.PropertiesDialog
import org.fossify.commons.extensions.toast
import org.fossify.gallery.activities.ComposeVideoPlayerActivity
import org.fossify.gallery.activities.ComposeViewerActivity
import org.fossify.gallery.compose.components.GalleryImage
import org.fossify.gallery.compose.components.EmptyState
import org.fossify.gallery.compose.components.SelectionBar
import org.fossify.gallery.compose.components.RenameDialog
import org.fossify.gallery.compose.components.UndoBar
import org.fossify.gallery.compose.components.SelectionRow
import org.fossify.gallery.compose.components.StarRatingDialog
import org.fossify.gallery.compose.components.TagInputDialog
import org.fossify.gallery.compose.theme.LocalMediaRepository
import org.fossify.gallery.compose.theme.RatingStarColor
import org.fossify.gallery.compose.util.dragSelectionGesture
import org.fossify.gallery.compose.util.rememberSelectionDragState
import org.fossify.gallery.compose.util.selectableItem
import org.fossify.gallery.helpers.UndoAction
import org.fossify.gallery.helpers.UndoManager
import org.fossify.gallery.helpers.UndoType
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.mediaCacheDB
import org.fossify.gallery.extensions.mediaDB
import org.fossify.gallery.helpers.VIDEO_EXTENSIONS
import org.fossify.gallery.models.Medium
import org.fossify.gallery.viewmodels.MediaViewModel
import org.fossify.gallery.viewmodels.MonthGroup
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val repo = LocalMediaRepository.current
    var selectedPaths by rememberSaveable(stateSaver = selectionSaver) { mutableStateOf<Set<String>>(emptySet()) }
    val dragSelection = rememberSelectionDragState()
    var showSelectionSheet by remember { mutableStateOf(false) }
    var showRatingDialog by remember { mutableStateOf(false) }
    var showTagsDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showFolderPicker by remember { mutableStateOf(false) }
    var folderPickerIsMove by remember { mutableStateOf(false) }
    var currentRating by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var heroRect by remember { mutableStateOf<android.graphics.Rect?>(null) }
    var taggedPaths by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedCommonTags by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(selectedPaths) {
        selectedCommonTags = if (selectedPaths.isEmpty()) emptySet()
        else withContext(Dispatchers.IO) { selectedPaths.map { repo.getTags(it) }.reduceOrNull { a, b -> a intersect b } ?: emptySet() }
    }
    LaunchedEffect(Unit) { taggedPaths = withContext(Dispatchers.IO) { try { ctx.mediaCacheDB.getAllTagged().map { it.fullPath }.toSet() } catch (_: Exception) { emptySet() } } }
    val columnCount = viewSettings.columnCount
    val isGrid = viewSettings.viewType == ViewType.GRID
    val isMosaic = viewSettings.viewType == ViewType.MOSAIC
    val imageAspectCache = remember { mutableStateMapOf<String, Float>() }
    val baseMedia = mediaOverride ?: state.allMedia
    var ratedMedia by remember { mutableStateOf<List<Medium>?>(null) }
    var tagMedia by remember { mutableStateOf<List<Medium>?>(null) }
    var pathFallbackMedia by remember { mutableStateOf<List<Medium>?>(null) }
    LaunchedEffect(ratingFilter) { if (ratingFilter > 0) { ratedMedia = withContext(Dispatchers.IO) { ctx.mediaDB.getByMinRating(ratingFilter) } } else ratedMedia = null }
    LaunchedEffect(tagFilterPaths) { if (tagFilterPaths != null) { tagMedia = withContext(Dispatchers.IO) { ctx.mediaDB.getMediaByPaths(tagFilterPaths.toList()) } } else tagMedia = null }
    LaunchedEffect(pathFilter) {
        if (pathFilter != null) {
            val dirs = pathFilter.filter { File(it).isDirectory }.toSet()
            pathFallbackMedia = withContext(Dispatchers.IO) { try { ctx.mediaDB.getNewestMedia(5000).filter { p -> (pathFilter + dirs).any { p.path.startsWith("$it/") || p.path == it } }.take(2000) } catch (_: Exception) { null } }
        } else pathFallbackMedia = null
    }
    val unsortedMedia by remember(baseMedia, ratingFilter, tagFilterPaths, pathFilter, ratedMedia, tagMedia, pathFallbackMedia) { derivedStateOf {
        var m = baseMedia
        if (ratingFilter > 0) { val db = ratedMedia; m = if (db != null && db.isNotEmpty()) db else m.filter { it.rating >= ratingFilter } }
        if (tagFilterPaths != null) {
            val tagged = tagMedia
            if (tagged != null && tagged.isNotEmpty()) { m = m.filter { it.path in tagged.map { it.path }.toSet() }; if (m.isEmpty()) m = tagged }
            else { m = m.filter { it.path in tagFilterPaths }; if (m.isEmpty()) { m = tagFilterPaths.mapNotNull { val f=File(it); if(f.exists()) Medium(null,f.name,f.absolutePath,f.parent?:"",f.lastModified(),f.lastModified(),f.length(),if(VIDEO_EXTENSIONS.any{e->it.endsWith(e,true)})2 else 1,0,false,0L,0L,0) else null } } }
        }
        if (pathFilter != null) { val dirs=pathFilter.filter{File(it).isDirectory}.toSet(); val filtered=m.filter{p->p.path in pathFilter||dirs.any{p.path.startsWith("$it/")}}; val fb=pathFallbackMedia; m = if(fb!=null && fb.size>filtered.size) fb else filtered }
        m
    } }
    val hasFilter = ratingFilter > 0 || tagFilterPaths != null || pathFilter != null
    val displayMedia by remember(unsortedMedia, viewSettings.sortBy, viewSettings.sortDesc) { derivedStateOf {
        val sorted = when (viewSettings.sortBy) {
            SortField.NAME -> unsortedMedia.sortedBy { it.name.lowercase() }
            SortField.DATE -> unsortedMedia.sortedBy { it.modified }
            SortField.SIZE -> unsortedMedia.sortedBy { it.size }
            SortField.RATING -> if(viewSettings.sortDesc) unsortedMedia.sortedWith(compareByDescending<Medium>{it.rating}.thenByDescending{it.modified}) else unsortedMedia.sortedWith(compareBy<Medium>{it.rating}.thenBy{it.modified})
        }
        if (viewSettings.sortDesc && viewSettings.sortBy != SortField.RATING) sorted.reversed() else sorted
    } }
    val pathIndexMap = remember(displayMedia) { displayMedia.withIndex().associate { it.value.path to it.index } }
    val cornerShape = if (viewSettings.roundedCorners) RoundedCornerShape(8.dp) else RoundedCornerShape(0.dp)
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

    var isRefreshing by remember { mutableStateOf(false) }
    PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = { isRefreshing = true; viewModel.refresh(); scope.launch { kotlinx.coroutines.delay(800); isRefreshing = false } }, modifier = Modifier.fillMaxSize()) {
        BackHandler(enabled = hasSelection) { selectedPaths = emptySet() }
        Box(modifier = modifier.fillMaxSize()) {
        when {
            mediaOverride != null && mediaOverride.isEmpty() -> {
                EmptyState(Icons.Default.Search, "Keine Medien in diesem Ordner")
            }
            state.isLoading && !hasFilter && mediaOverride == null -> MediaSkeleton(columns = columnCount)
            displayMedia.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Search,null,Modifier.size(64.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.4f)); Spacer(Modifier.height(16.dp))
                    Text(if(hasFilter)"Keine Ergebnisse" else "Keine Medien gefunden",style=MaterialTheme.typography.bodyLarge,color=MaterialTheme.colorScheme.onSurfaceVariant,textAlign=TextAlign.Center)
                    if(hasFilter){Spacer(Modifier.height(8.dp));Surface(Modifier.clickable{onClearFilter()},color=MaterialTheme.colorScheme.primaryContainer,shape=RoundedCornerShape(16.dp)){Text("Filter aufheben",Modifier.padding(horizontal=12.dp,vertical=6.dp),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onPrimaryContainer)}}
                }}
            }
            isGrid -> {
                Column {
                    if (hasFilter) FilterBreadcrumbs(ratingFilter,activeTagName,activePathName,activeCollectionName,displayMedia.size,onClearRatingFilter,onClearTagFilter,onClearPathFilter,onClearFilter)
                    val quickTags = remember { ctx.config.quickTags.filter { it.isNotBlank() } }
                    AnimatedVisibility(visible = quickTags.isNotEmpty() && hasSelection, enter = fadeIn() + slideInVertically { -it }, exit = fadeOut() + slideOutVertically { -it }) {
                    if (quickTags.isNotEmpty() && hasSelection) {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            quickTags.forEach { tag ->
                                val active = tag in selectedCommonTags
                                Surface(
                                    onClick = {
                                        val targets = selectedPaths
                                        scope.launch(Dispatchers.IO) {
                                            targets.forEach { p -> if (repo.getTags(p).contains(tag)) repo.removeTag(p, tag) else repo.addTag(p, tag) }
                                            withContext(Dispatchers.Main) { taggedPaths = withContext(Dispatchers.IO) { try { ctx.mediaCacheDB.getAllTagged().map { it.fullPath }.toSet() } catch (_: Exception) { emptySet() } } }
                                        }
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                ) {
                                    Text(tag, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    }
                    val grouped = remember(displayMedia) { displayMedia.groupByMonth() }
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
                            val isSelected by remember(m.path) { derivedStateOf { m.path in selectedPaths } }
                            val showDuration = remember { ctx.config.showVideoDurationOnThumbnails && m.videoDuration > 0 }
                            val durationText by remember(m.videoDuration) { derivedStateOf { if (showDuration) "%02d:%02d".format(m.videoDuration/60, m.videoDuration%60) else "" } }
                            val hasTag by remember(m.path) { derivedStateOf { m.path in taggedPaths } }
                            var lastBoundsUpdate by remember { mutableLongStateOf(0L) }
                            Column(Modifier.padding(itemSpacing/2).background(mediaCardColor,cornerShape).onGloballyPositioned{coords->val p=coords.positionInWindow();val s=coords.size;heroRect=android.graphics.Rect(p.x.toInt(),p.y.toInt(),(p.x+s.width).toInt(),(p.y+s.height).toInt());val now=System.currentTimeMillis();if(now-lastBoundsUpdate>300){lastBoundsUpdate=now;dragSelection.registerItemBounds(m.path,androidx.compose.ui.geometry.Rect(p,androidx.compose.ui.geometry.Size(s.width.toFloat(),s.height.toFloat())))}}) {
                                Box(Modifier.aspectRatio(1f).selectableItem(isSelectionMode=hasSelection,onClick={if(hasSelection)selectedPaths=if(m.path in selectedPaths)selectedPaths-m.path else selectedPaths+m.path else openViewer(originalIdx)},onLongClick={selectedPaths=selectedPaths+m.path},onSwipeToSelect={selectedPaths=selectedPaths+m.path})) {
                                    if(isVideo)VideoThumbnail(videoPath=m.path,modifier=Modifier.fillMaxSize().clip(cornerShape),contentScale=ContentScale.Crop) else GalleryImage(path=m.path,contentDescription=m.name,modifier=Modifier.fillMaxSize().clip(cornerShape),contentScale=ContentScale.Crop,placeholderIconSize=16.dp)
                                    if (showOverlays) {
                                    val overlayAlpha by androidx.compose.animation.core.animateFloatAsState(targetValue = 1f, animationSpec = tween(350), label = "overlayFade")
                                    Box(Modifier.fillMaxSize().graphicsLayer { alpha = overlayAlpha }, contentAlignment = Alignment.BottomCenter) {
                                        Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.padding(bottom = 3.dp)) {
                                            for (i in 1..5) {
                                                Icon(
                                                    if (i <= m.rating) Icons.Default.Star else Icons.Default.StarBorder,
                                                    contentDescription = "Bewertung $i",
                                                    tint = if (i <= m.rating) RatingStarColor else Color.White.copy(alpha = 0.35f),
                                                    modifier = Modifier.size(11.dp)
                                                )
                                            }
                                        }
                                    }
                                        if(hasTag) Box(Modifier.align(Alignment.TopEnd).padding(4.dp).background(Color.Black.copy(alpha=0.5f),RoundedCornerShape(4.dp)).padding(horizontal=4.dp,vertical=1.dp)) { Icon(Icons.Default.Label,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(10.dp)) }
                                        if(isVideo && showDuration) Box(Modifier.align(Alignment.BottomEnd).padding(4.dp).background(Color.Black.copy(alpha=0.6f),RoundedCornerShape(4.dp)).padding(horizontal=4.dp,vertical=1.dp)) { Text(durationText,style=MaterialTheme.typography.labelSmall,color=Color.White,fontSize=10.sp) }
                                    }
                                    if(isSelected){Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.primary.copy(alpha=0.4f)));Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(24.dp).background(MaterialTheme.colorScheme.primary,CircleShape),contentAlignment=Alignment.Center){Icon(Icons.Default.Close,null,tint=Color.White,modifier=Modifier.size(16.dp))}}
                                    if(hasSelection && !isSelected) Box(Modifier.align(Alignment.BottomEnd).padding(4.dp).size(28.dp).background(Color.Black.copy(alpha=0.5f),CircleShape).clickable { openViewer(originalIdx) }, contentAlignment = Alignment.Center) { Icon(Icons.Default.Visibility,"Vorschau",tint=Color.White,modifier=Modifier.size(18.dp)) }
                                }
                                if(viewSettings.showFileNames) Text(m.name,style=MaterialTheme.typography.labelSmall,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.padding(top=2.dp))
                            }
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
                Column {
                    if (hasFilter) FilterBreadcrumbs(ratingFilter,activeTagName,activePathName,activeCollectionName,displayMedia.size,onClearRatingFilter,onClearTagFilter,onClearPathFilter,onClearFilter)
                    val quickTagsM = remember { ctx.config.quickTags.filter { it.isNotBlank() } }
                    AnimatedVisibility(visible = quickTagsM.isNotEmpty() && hasSelection, enter = fadeIn() + slideInVertically { -it }, exit = fadeOut() + slideOutVertically { -it }) {
                    if (quickTagsM.isNotEmpty() && hasSelection) { Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) { quickTagsM.forEach { tag -> val active = tag in selectedCommonTags; Surface(onClick = { val targets = selectedPaths; scope.launch(Dispatchers.IO) { targets.forEach { p -> if (repo.getTags(p).contains(tag)) repo.removeTag(p, tag) else repo.addTag(p, tag) }; withContext(Dispatchers.Main) { taggedPaths = withContext(Dispatchers.IO) { try { ctx.mediaCacheDB.getAllTagged().map { it.fullPath }.toSet() } catch (_: Exception) { emptySet() } } } } }, shape = RoundedCornerShape(16.dp), color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant) { Text(tag, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant) } } } }
                    }
                    val grouped = remember(displayMedia) { displayMedia.groupByMonth() }
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
                            val isSelected by remember(m.path) { derivedStateOf { m.path in selectedPaths } }
                            val showDuration = remember { ctx.config.showVideoDurationOnThumbnails && m.videoDuration > 0 }
                            val durationText by remember(m.videoDuration) { derivedStateOf { if (showDuration) "%02d:%02d".format(m.videoDuration/60, m.videoDuration%60) else "" } }
                            val hasTag by remember(m.path) { derivedStateOf { m.path in taggedPaths } }
                            var lastBoundsUpdate by remember { mutableLongStateOf(0L) }
                            val imageAspect = if (isVideo) 1f else (imageAspectCache[m.path] ?: 1f)
                            if (!isVideo) LaunchedEffect(m.path) {
                                if (imageAspectCache[m.path] == null) {
                                    imageAspectCache[m.path] = withContext(Dispatchers.IO) { decodeImageAspect(m.path) }
                                }
                            }
                            Column(Modifier.padding(itemSpacing/2).background(mediaCardColor,cornerShape).onGloballyPositioned{coords->val p=coords.positionInWindow();val s=coords.size;heroRect=android.graphics.Rect(p.x.toInt(),p.y.toInt(),(p.x+s.width).toInt(),(p.y+s.height).toInt());val now=System.currentTimeMillis();if(now-lastBoundsUpdate>300){lastBoundsUpdate=now;dragSelection.registerItemBounds(m.path,androidx.compose.ui.geometry.Rect(p,androidx.compose.ui.geometry.Size(s.width.toFloat(),s.height.toFloat())))}}) {
                                Box(Modifier.aspectRatio(imageAspect).selectableItem(isSelectionMode=hasSelection,onClick={if(hasSelection)selectedPaths=if(m.path in selectedPaths)selectedPaths-m.path else selectedPaths+m.path else openViewer(originalIdx)},onLongClick={selectedPaths=selectedPaths+m.path},onSwipeToSelect={selectedPaths=selectedPaths+m.path})) {
                                    if(isVideo)VideoThumbnail(videoPath=m.path,modifier=Modifier.fillMaxSize().clip(cornerShape),contentScale=ContentScale.Crop) else GalleryImage(path=m.path,contentDescription=m.name,modifier=Modifier.fillMaxSize().clip(cornerShape),contentScale=ContentScale.Crop,placeholderIconSize=16.dp)
                                    if (showOverlaysStag) {
                                    val overlayAlphaM by androidx.compose.animation.core.animateFloatAsState(targetValue = 1f, animationSpec = tween(350), label = "overlayFadeM")
                                    Box(Modifier.fillMaxSize().graphicsLayer { alpha = overlayAlphaM }, contentAlignment = Alignment.BottomCenter) {
                                        Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.padding(bottom = 3.dp)) {
                                            for (i in 1..5) { Icon(if (i <= m.rating) Icons.Default.Star else Icons.Default.StarBorder, contentDescription = "Bewertung $i", tint = if (i <= m.rating) RatingStarColor else Color.White.copy(alpha = 0.35f), modifier = Modifier.size(11.dp)) }
                                        }
                                    }
                                        if(hasTag) Box(Modifier.align(Alignment.TopEnd).padding(4.dp).background(Color.Black.copy(alpha=0.5f),RoundedCornerShape(4.dp)).padding(horizontal=4.dp,vertical=1.dp)) { Icon(Icons.Default.Label,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(10.dp)) }
                                        if(isVideo && showDuration) Box(Modifier.align(Alignment.BottomEnd).padding(4.dp).background(Color.Black.copy(alpha=0.6f),RoundedCornerShape(4.dp)).padding(horizontal=4.dp,vertical=1.dp)) { Text(durationText,style=MaterialTheme.typography.labelSmall,color=Color.White,fontSize=10.sp) }
                                    }
                                    if(isSelected){Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.primary.copy(alpha=0.4f)));Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(24.dp).background(MaterialTheme.colorScheme.primary,CircleShape),contentAlignment=Alignment.Center){Icon(Icons.Default.Close,null,tint=Color.White,modifier=Modifier.size(16.dp))}}
                                    if(hasSelection && !isSelected) Box(Modifier.align(Alignment.BottomEnd).padding(4.dp).size(28.dp).background(Color.Black.copy(alpha=0.5f),CircleShape).clickable { openViewer(originalIdx) }, contentAlignment = Alignment.Center) { Icon(Icons.Default.Visibility,"Vorschau",tint=Color.White,modifier=Modifier.size(18.dp)) }
                                }
                                if(viewSettings.showFileNames) Text(m.name,style=MaterialTheme.typography.labelSmall,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.padding(top=2.dp))
                            }
                        }
                    }
                    }
                    }
                    }
                }
            }
            else -> {
                val grouped = remember(displayMedia) { displayMedia.groupByMonth() }
                val listState = rememberLazyListState(initialFirstVisibleItemIndex = state.scrollIndex, initialFirstVisibleItemScrollOffset = state.scrollOffset)
                LaunchedEffect(listState) {
                    snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
                        .collect { (i, o) -> viewModel.saveScrollPosition(i, o) }
                }
                Box(Modifier.dragSelectionGesture(dragSelection) { path -> selectedPaths = selectedPaths + path }) {
                LazyColumn(state = listState, reverseLayout = viewSettings.anchorBottom, contentPadding = PaddingValues(4.dp)) {
                    grouped.forEach { (label, groupItems) ->
                        stickyHeader { MonthHeader(label = label, count = groupItems.size) }
                        items(groupItems.size, key = { groupItems[it].path }, contentType = { groupItems[it].type }) { idx ->
                            val m = groupItems[idx]; val originalIdx = pathIndexMap[m.path] ?: 0; val isVideo = remember(m.path) { m.path.substringAfterLast('.',"").lowercase() in VIDEO_EXTENSIONS }
                            val isSelected by remember(m.path) { derivedStateOf { m.path in selectedPaths } }
                            var lastBoundsUpdate by remember { mutableLongStateOf(0L) }
                            Surface(modifier = Modifier.fillMaxWidth().background(mediaCardColor,RoundedCornerShape(8.dp)).onGloballyPositioned{coords->val p=coords.positionInWindow();val s=coords.size;heroRect=android.graphics.Rect(p.x.toInt(),p.y.toInt(),(p.x+s.width).toInt(),(p.y+s.height).toInt());val now=System.currentTimeMillis();if(now-lastBoundsUpdate>300){lastBoundsUpdate=now;dragSelection.registerItemBounds(m.path,androidx.compose.ui.geometry.Rect(p,androidx.compose.ui.geometry.Size(s.width.toFloat(),s.height.toFloat())))}}.selectableItem(isSelectionMode=hasSelection,onClick={if(hasSelection)selectedPaths=if(m.path in selectedPaths)selectedPaths-m.path else selectedPaths+m.path else openViewer(originalIdx)},onLongClick={selectedPaths=selectedPaths+m.path},onSwipeToSelect={selectedPaths=selectedPaths+m.path}),color=Color.Transparent) {
                                Row(Modifier.padding(horizontal=12.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
                                    Box(Modifier.size(56.dp).clip(RoundedCornerShape(8.dp))){if(isVideo)VideoThumbnail(videoPath=m.path,modifier=Modifier.fillMaxSize(),contentScale=ContentScale.Crop) else GalleryImage(path=m.path,contentDescription=m.name,modifier=Modifier.fillMaxSize(),contentScale=ContentScale.Crop,placeholderIconSize=18.dp)}
                                    Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)){Text(m.name,style=MaterialTheme.typography.bodyMedium,maxLines=1,overflow=TextOverflow.Ellipsis);Text(formatFileSize(m.size),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                                    if(hasSelection){IconButton(onClick={openViewer(originalIdx)},modifier=Modifier.size(36.dp)){Icon(Icons.Default.Visibility,"Vorschau",tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(20.dp))}}
                                    if(m.path in selectedPaths) Icon(Icons.Default.Close,"Ausgewählt",tint=MaterialTheme.colorScheme.primary)
                                }
                            }
                            HorizontalDivider(Modifier.padding(start=76.dp),color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.3f))
                        }
                    }
                }
                }
            }
        }
        AnimatedVisibility(visible=hasSelection,enter=slideInVertically(initialOffsetY={it})+fadeIn(animationSpec=spring(dampingRatio=0.7f)),exit=slideOutVertically(targetOffsetY={it})+fadeOut(),modifier=Modifier.align(Alignment.BottomCenter)) {
            SelectionBar(
                count = selectedPaths.size,
                onClear = { selectedPaths = emptySet() },
                onSelectAll = { selectedPaths = (if (hasFilter) displayMedia.map { it.path } else viewModel.allMediaPaths()).toSet() },
                onInvert = { val all = (if (hasFilter) displayMedia.map { it.path } else viewModel.allMediaPaths()).toSet(); selectedPaths = all - selectedPaths },
                onMoreActions = { showSelectionSheet = true },
            )
        }
        SnackbarHost(hostState=snackbarHostState,modifier=Modifier.align(Alignment.BottomCenter))
        UndoBar(modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
    if (showSelectionSheet) {
        ModalBottomSheet(onDismissRequest={showSelectionSheet=false},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=false),containerColor=MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().heightIn(max=340.dp).padding(horizontal=16.dp,vertical=8.dp).verticalScroll(rememberScrollState())) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary,CircleShape));Spacer(Modifier.width(8.dp));Text("${selectedPaths.size} ausgewählt",style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f),color=MaterialTheme.colorScheme.onSurfaceVariant);IconButton(onClick={selectedPaths=emptySet();showSelectionSheet=false}){Icon(Icons.Default.Close,"Auswahl schließen",tint=MaterialTheme.colorScheme.onSurfaceVariant)}}
                Spacer(Modifier.height(12.dp))
                SelectionRow(Icons.Default.Share,"Teilen"){val uris=ArrayList(selectedPaths.map{androidx.core.content.FileProvider.getUriForFile(ctx,"${ctx.packageName}.provider",File(it))});val allVideo=selectedPaths.all{it.substringAfterLast('.').lowercase() in VIDEO_EXTENSIONS};val si=if(uris.size==1)Intent(Intent.ACTION_SEND).apply{type=if(allVideo)"video/*" else "image/*";putExtra(Intent.EXTRA_STREAM,uris.first());addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)} else Intent(Intent.ACTION_SEND_MULTIPLE).apply{type="*/*";putParcelableArrayListExtra(Intent.EXTRA_STREAM,uris);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)};ctx.startActivity(Intent.createChooser(si,"Teilen").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));showSelectionSheet=false;selectedPaths=emptySet()}
                SelectionRow(Icons.Default.Delete,"Löschen",tint=MaterialTheme.colorScheme.error){val d=selectedPaths.toSet();viewModel.softDeletePaths(d);UndoManager.push(UndoAction(paths=d,type=UndoType.DELETE));selectedPaths=emptySet();showSelectionSheet=false}
                SelectionRow(Icons.Default.Info,"Info"){ try { selectedPaths.firstOrNull()?.let { p -> (ctx as? android.app.Activity)?.let { a -> PropertiesDialog(a, p, false) } } } catch (e: Exception) { ctx.toast("Info-Fehler: ${e.message}", Toast.LENGTH_LONG) }; showSelectionSheet = false }
                SelectionRow(Icons.Default.ContentCopy,"Kopieren"){folderPickerIsMove=false;showFolderPicker=true;showSelectionSheet=false}
                SelectionRow(Icons.AutoMirrored.Filled.DriveFileMove,"Verschieben"){folderPickerIsMove=true;showFolderPicker=true;showSelectionSheet=false}
                SelectionRow(Icons.Default.Star,"Bewerten"){showRatingDialog=true;showSelectionSheet=false}
                SelectionRow(Icons.Default.Edit,"Tags"){showTagsDialog=true;showSelectionSheet=false}
                SelectionRow(Icons.Default.Edit,"Umbenennen"){showRenameDialog=true;showSelectionSheet=false}
                Spacer(Modifier.height(24.dp))
            }
        }
    }
    if (showRatingDialog) { val batch=selectedPaths.toList(); StarRatingDialog(currentRating=currentRating,onRate={i->currentRating=i;scope.launch(Dispatchers.IO){batch.forEach{p->repo.updateRating(p,i)};withContext(Dispatchers.Main){viewModel.silentRefresh()}};selectedPaths=emptySet();showRatingDialog=false},onDismiss={showRatingDialog=false}) }
    if (showTagsDialog) { val batch=selectedPaths.toList(); var allTags by remember{mutableStateOf<List<String>>(emptyList())}; var tagCounts by remember{mutableStateOf<Map<String, Int>>(emptyMap())}; var dialogInitialTags by remember{mutableStateOf<Set<String>>(emptySet())}; LaunchedEffect(Unit){dialogInitialTags=withContext(Dispatchers.IO){repo.getTags(batch.firstOrNull().orEmpty())}}; LaunchedEffect(Unit){withContext(Dispatchers.IO){try{val tagged=ctx.mediaCacheDB.getAllTagged();val counts=tagged.flatMap{it.tags.split(",").filter(String::isNotBlank)}.groupingBy{it}.eachCount();allTags=counts.entries.sortedByDescending{it.value}.map{it.key};tagCounts=counts}catch(_:Exception){}}}; TagInputDialog(initialTags=dialogInitialTags,suggestedTags=allTags,suggestedTagCounts=tagCounts,onAddTag={scope.launch(Dispatchers.IO){batch.forEach{p->repo.addTag(p,it)}}},onRemoveTag={scope.launch(Dispatchers.IO){batch.forEach{p->repo.removeTag(p,it)}}},onDismiss={showTagsDialog=false;selectedPaths=emptySet();scope.launch(Dispatchers.IO){val t=try{ctx.mediaCacheDB.getAllTagged().map{it.fullPath}.toSet()}catch(_:Exception){emptySet()};withContext(Dispatchers.Main){taggedPaths=t}}},batchCount=batch.size) }
    if (showFolderPicker) { val batch=selectedPaths.toList(); FolderPickerSheet(isMoveOperation=folderPickerIsMove,sourcePaths=batch,onDismiss={showFolderPicker=false;selectedPaths=emptySet()}) }
    if (showRenameDialog) { val batch=selectedPaths.toList(); RenameDialog(paths=batch,onDismiss={showRenameDialog=false;selectedPaths=emptySet();viewModel.refresh()}) }
}

@Composable
private fun FilterBreadcrumbs(ratingFilter:Int,activeTagName:String?,activePathName:String?,activeCollectionName:String?,resultCount:Int,onClearRating:()->Unit,onClearTag:()->Unit,onClearPath:()->Unit,onClearAll:()->Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=6.dp).horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
        if(activeCollectionName!=null) ActiveFilterChip("Sammlung: $activeCollectionName"){onClearPath()}
        if(activePathName!=null) ActiveFilterChip("Pfad: $activePathName"){onClearPath()}
        if(activeTagName!=null) ActiveFilterChip(activeTagName.take(24).let{if(activeTagName.length>24)"$it…" else it}){onClearTag()}
        if(ratingFilter>0) ActiveFilterChip("★ ${ratingFilter}+"){onClearRating()}
        Text("$resultCount Ergebnisse",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        androidx.compose.material3.AssistChip(onClick=onClearAll,label={Text("Alle aufheben")},leadingIcon={Icon(Icons.Default.Close,null,Modifier.size(18.dp))})
    }
}
@Composable
private fun ActiveFilterChip(label:String,onRemove:()->Unit){
    androidx.compose.material3.InputChip(
        selected=true,
        onClick=onRemove,
        label={Text(label)},
        trailingIcon={Icon(Icons.Default.Close,"$label entfernen",Modifier.size(18.dp))},
    )
}

private fun formatFileSize(bytes: Long): String { if (bytes < 1024) return "$bytes B"; val kb = bytes / 1024; if (kb < 1024) return "${kb} KB"; val mb = kb / 1024; if (mb < 1024) return "${mb} MB"; return "%.1f GB".format(mb / 1024.0) }
@Composable
private fun MonthHeader(label:String,count:Int){Surface(Modifier.fillMaxWidth(),color=MaterialTheme.colorScheme.background){Row(Modifier.padding(horizontal=12.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically){Text(label,style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.SemiBold,color=MaterialTheme.colorScheme.onSurface);Spacer(Modifier.width(8.dp));Text("$count",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}}}
private fun List<Medium>.groupByMonth():List<MonthGroup>{if(isEmpty())return emptyList();val f=SimpleDateFormat("MMMM yyyy",Locale.GERMANY);val g=LinkedHashMap<String,MutableList<Medium>>();forEach{m->val d=if(m.taken>0)Date(m.taken) else Date(m.modified);val k=f.format(d).replaceFirstChar{it.uppercase()};g.getOrPut(k){mutableListOf()}.add(m)};return g.map{MonthGroup(it.key,it.value)}}

internal fun decodeImageAspect(path: String): Float = try {
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, opts)
    if (opts.outWidth > 0 && opts.outHeight > 0) opts.outWidth.toFloat() / opts.outHeight.toFloat() else 1f
} catch (_: Exception) { 1f }

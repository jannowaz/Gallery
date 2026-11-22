package org.fossify.gallery.compose.screens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fossify.gallery.compose.theme.AppMotion
import org.fossify.gallery.compose.theme.LocalMediaRepository
import org.fossify.gallery.compose.theme.Radius

import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollFactory
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
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Image
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import org.fossify.gallery.helpers.ViewerLoadDirection
import kotlinx.coroutines.launch
import kotlin.math.abs
import org.fossify.commons.extensions.toast
import org.fossify.gallery.compose.components.GalleryImage
import org.fossify.gallery.compose.components.MediaListRow
import org.fossify.gallery.compose.components.MediaTile
import org.fossify.gallery.compose.components.MediaInfoDialog
import org.fossify.gallery.R
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import org.fossify.gallery.compose.components.ConfirmDestructive
import org.fossify.gallery.compose.components.EmptyState
import org.fossify.gallery.compose.components.LoadErrorState
import org.fossify.gallery.compose.components.FastScrollBar
import org.fossify.gallery.compose.components.SelectionTopAppBar
import org.fossify.gallery.compose.components.ActiveFilterChip
import org.fossify.gallery.compose.components.LibraryFilterSummary
import org.fossify.gallery.compose.components.viewSettingsSummary
import org.fossify.gallery.compose.components.RenameDialog
import org.fossify.gallery.compose.components.UndoBar
import org.fossify.gallery.compose.components.InlineRefreshIndicator
import org.fossify.gallery.compose.components.RateAndTagSheet
import org.fossify.gallery.compose.components.SectionHeader
import org.fossify.gallery.compose.components.RefreshActionButton
import org.fossify.gallery.compose.components.GroupToggleButton
import org.fossify.gallery.compose.components.LibraryScreenHeader
import org.fossify.gallery.compose.components.FloatingSectionLabel
import org.fossify.gallery.compose.components.tagAccentColor
import org.fossify.gallery.helpers.currentBreadcrumb
import org.fossify.gallery.compose.util.PeekState
import org.fossify.gallery.compose.util.MediaSelectionMode
import org.fossify.gallery.compose.util.ScrollToTopEffect
import org.fossify.gallery.compose.util.SelectionDragState
import org.fossify.gallery.compose.util.dragSelectionGesture
import org.fossify.gallery.compose.util.rememberSelectionDragState
import org.fossify.gallery.helpers.UndoAction
import org.fossify.gallery.helpers.UndoManager
import org.fossify.gallery.helpers.withRecycleBinDeletePasswordCheck
import org.fossify.gallery.helpers.UndoType
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.shareMediaPaths
import org.fossify.gallery.helpers.TYPE_VIDEOS
import org.fossify.gallery.helpers.VIDEO_EXTENSIONS
import org.fossify.gallery.helpers.GroupBy
import org.fossify.gallery.helpers.QuickTagsBus
import org.fossify.gallery.helpers.GroupRow
import org.fossify.gallery.helpers.buildRatingGroupRows
import org.fossify.gallery.helpers.buildSizeGroupRows
import org.fossify.gallery.helpers.buildAlphabetGroupRows
import org.fossify.gallery.helpers.buildTagGroupRows
import org.fossify.gallery.helpers.applyCollapsedGroups
import org.fossify.gallery.helpers.collapsibleGroupKeys
import org.fossify.gallery.helpers.setGroupsExpanded
import org.fossify.gallery.helpers.sizeLabelFor
import org.fossify.gallery.helpers.alphabetLabelFor
import org.fossify.gallery.helpers.ratingLabelFor
import org.fossify.gallery.models.Medium
import org.fossify.gallery.models.MediaReviewFilter
import org.fossify.gallery.models.MediaReviewStatus
import org.fossify.gallery.viewmodels.MediaUiState
import org.fossify.gallery.viewmodels.MediaViewModel
import org.fossify.gallery.viewmodels.LibraryFilterActions
import org.fossify.gallery.viewmodels.LibraryFilterState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MediaScreen(
    modifier: Modifier = Modifier,
    viewSettings: ViewSettings = ViewSettings(),
    ratingFilter: Int = 0,
    tagFilterNames: Set<String>? = null,
    tagMatchAll: Boolean = false,
    pathFilter: Set<String>? = null,
    excludePathFilter: Set<String>? = null,
    activeTagName: String? = null,
    activePathName: String? = null,
    activeCollectionName: String? = null,
    /** Explicit members of a manual collection/stack, kept additive to the smart filters. */
    additionalMediaPaths: Set<String> = emptySet(),
    activeFolderFilterName: String? = null,
    minSizeFilter: Long = 0L,
    dateRangeFilter: Int = 0,
    addedSince: Long = 0L,
    typeFilter: Int = 0,
    reviewFilter: MediaReviewFilter = MediaReviewFilter.NONE,
    textQuery: String = "",
    onClearFilter: () -> Unit = {},
    onClearRatingFilter: () -> Unit = {},
    onClearTagFilter: () -> Unit = {},
    onRemoveTagFilter: (String) -> Unit = {},
    onClearTagMatchAll: () -> Unit = {},
    onTagClick: (String) -> Unit = {},
    onClearPathFilter: () -> Unit = {},
    onClearSizeFilter: () -> Unit = {},
    onClearDateFilter: () -> Unit = {},
    onClearAddedSinceFilter: () -> Unit = {},
    onClearTypeFilter: () -> Unit = {},
    onClearReviewFilter: () -> Unit = {},
    showToolbar: Boolean = false,
    onOpenViewSettings: (() -> Unit)? = null,
    newMediaCount: Int = 0,
    onOpenNewMedia: () -> Unit = {},
    onMarkNewMediaSeen: () -> Unit = {},
    mediaOverride: List<Medium>? = null,
    /** Optional stable key for an in-memory media surface, e.g. a drilled-into folder. */
    scrollPositionKey: String? = null,
    /** Isolates an override-backed grid from other media surfaces sharing a ViewModel store. */
    viewModelKey: String? = null,
    onFirstVisibleMediaPathChanged: (String?) -> Unit = {},
    onNavigateToViewer: ((paths: List<String>, startIndex: Int) -> Unit)? = null,
    onNavigateToViewerFast: ((startPath: String, resolveWindow: suspend () -> org.fossify.gallery.helpers.ViewerPathWindow, loadMore: suspend (String, ViewerLoadDirection, Int) -> org.fossify.gallery.helpers.ViewerPathChunk) -> Unit)? = null,
    scrollToPath: String = "",
    onClearScrollToPath: () -> Unit = {},
    onSelectionActiveChanged: (Boolean) -> Unit = {},
    /** Optional contextual bulk action for a review queue, such as keeping an AI suggestion. */
    onKeepSelection: ((Set<String>) -> Unit)? = null,
    /** Review queues can reserve the limited selection bar for their own primary decision. */
    showMetadataActions: Boolean = true,
    tabIndex: Int? = null,
    // Picker multi-select (EXTRA_ALLOW_MULTIPLE). When true, taps toggle selection instead of opening
    // the viewer, the normal (delete/share/…) selection bar is replaced by a "Select (N)" confirm bar,
    // and confirming hands the chosen paths to onConfirmPick. Both default off, so normal browsing and
    // selection behaviour is completely unchanged.
    pickMultiple: Boolean = false,
    onConfirmPick: ((List<String>) -> Unit)? = null,
) {
    val ctx = LocalContext.current
    val res = androidx.compose.ui.platform.LocalResources.current
    val viewModel: MediaViewModel = viewModel(key = viewModelKey)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val filteredSelectionCount by viewModel.filteredResultCount.collectAsStateWithLifecycle()
    val groupSummaries by viewModel.groupSummaries.collectAsStateWithLifecycle()
    val lazyPagingItems = viewModel.pagedMedia.collectAsLazyPagingItems()
    val collapsedPagedRatingKeysState = rememberSaveable(viewSettings.groupBy, stateSaver = listSaver(save = { it.toList() }, restore = { it.toSet() })) {
        mutableStateOf(setOf<String>())
    }
    LaunchedEffect(collapsedPagedRatingKeysState.value, groupSummaries, viewSettings.groupBy) {
        val collapsed = collapsedPagedRatingKeysState.value
        val sqlValues = if (collapsed.isEmpty() || viewSettings.groupBy == GroupBy.NONE || viewSettings.groupBy == GroupBy.TAG) {
            emptySet()
        } else {
            groupSummaries.filter { it.key in collapsed }.mapTo(hashSetOf()) { it.sqlValue }
        }
        viewModel.setCollapsedGroupSqlValues(sqlValues)
    }
    // Shared between the paged accumulator below (headers built as pages stream in) and the
    // override-path groupRows further down - declared once here so both can use it.
    val unratedLabel = stringResource(R.string.group_unrated)
    val selectionSaver = remember { listSaver<Set<String>, String>(save = { it.toList() }, restore = { it.toSet() }) }
    // Folder destinations can be removed from composition while the Viewer is on top. Keep the
    // override list's position in the destination's saveable state instead of relying only on the
    // MediaViewModel, whose state can be recreated when Navigation rebuilds the destination.
    val savedOverrideScrollPosition = rememberSaveable(
        scrollPositionKey,
        stateSaver = mediaScrollPositionSaver,
    ) { mutableStateOf(MediaScrollPosition()) }
    val cachedOverrideScrollPosition = scrollPositionKey?.let(MediaScrollCache::get)
    val restoredOverrideScrollPosition = cachedOverrideScrollPosition ?: savedOverrideScrollPosition.value
    val initialOverrideScrollIndex = if (scrollPositionKey != null) {
        restoredOverrideScrollPosition.index
    } else {
        state.scrollIndex
    }
    val initialOverrideScrollOffset = if (scrollPositionKey != null) {
        restoredOverrideScrollPosition.offset
    } else {
        state.scrollOffset
    }
    fun saveOverrideScrollPosition(index: Int, offset: Int, anchorPath: String = "") {
        if (scrollPositionKey != null) {
            val previous = savedOverrideScrollPosition.value
            val position = MediaScrollPosition(index, offset, anchorPath.ifBlank { previous.anchorPath })
            savedOverrideScrollPosition.value = position
            MediaScrollCache.put(scrollPositionKey, position)
        } else {
            viewModel.saveScrollPosition(index, offset)
        }
    }
    // Override-based screens (cleanup results, folder drills, favorites) have their own view-model
    // key, so they must receive the supplied sort as well. Previously only the paged library did,
    // which meant an explicitly requested result ordering could silently inherit an old sort.
    LaunchedEffect(viewSettings.sortBy, viewSettings.sortDesc, viewSettings.groupBy) {
        viewModel.setSort(viewSettings.sortBy, viewSettings.sortDesc, viewSettings.groupBy)
    }
    LaunchedEffect(mediaOverride) { viewModel.setOverride(mediaOverride) }
    // Grouping (month/tag/rating) only applies to the override path (Favorites, a drilled-into
    // folder) - the unbounded paged tab keeps its own fixed month grouping, see PagedRow below.
    LaunchedEffect(viewSettings.groupBy, viewSettings.groupOrder, mediaOverride) {
        if (mediaOverride != null) viewModel.setGroupSettings(viewSettings.groupBy, viewSettings.groupOrder)
    }
    LaunchedEffect(ratingFilter, tagFilterNames, tagMatchAll, pathFilter, excludePathFilter, minSizeFilter, dateRangeFilter, addedSince, typeFilter, additionalMediaPaths, activeCollectionName, reviewFilter, textQuery) {
        val hasSmartCriteria = ratingFilter > 0 || tagFilterNames != null || pathFilter != null ||
            excludePathFilter != null || minSizeFilter > 0L || dateRangeFilter > 0 ||
            addedSince > 0L || typeFilter > 0 || reviewFilter != MediaReviewFilter.NONE || textQuery.isNotBlank()
        // An empty manual/system collection (e.g. "5 Sterne" with 0 rated items) must still filter
        // to emptySet() rather than falling back to null (which means "unfiltered library").
        val effectiveAdditionalPaths = when {
            additionalMediaPaths.isNotEmpty() -> additionalMediaPaths
            activeCollectionName != null && !hasSmartCriteria -> emptySet()
            else -> null
        }
        viewModel.setFilter(ratingFilter, tagFilterNames, pathFilter, excludePathFilter, minSizeFilter, dateRangeFilter, typeFilter, tagMatchAll, effectiveAdditionalPaths, addedSince, reviewFilter, textQuery)
    }
    val selectedPathsState = rememberSaveable(stateSaver = selectionSaver) { mutableStateOf<Set<String>>(emptySet()) }
    var selectedPaths by selectedPathsState
    var overrideViewerOpeningPath by remember { mutableStateOf<String?>(null) }
    val rangeAnchorIndexState = rememberSaveable { mutableStateOf<Int?>(null) }
    var rangeAnchorIndex by rangeAnchorIndexState
    val rangeAnchorPathState = rememberSaveable { mutableStateOf<String?>(null) }
    var rangeAnchorPath by rangeAnchorPathState
    val selectionModeState = rememberSaveable { mutableStateOf(MediaSelectionMode.INDIVIDUAL) }
    var selectionMode by selectionModeState
    LaunchedEffect(mediaOverride) {
        mediaOverride?.let { allowed ->
            val allowedPaths = allowed.mapTo(hashSetOf()) { it.path }
            selectedPaths = selectedPaths.intersect(allowedPaths)
            rangeAnchorIndex = null
            rangeAnchorPath = null
        }
    }
    // A selection belongs to one concrete browsing context.  Do not leave an invisible batch
    // armed after the user changes folder, query, filter, order or grouping.
    LaunchedEffect(
        scrollPositionKey, mediaOverride, ratingFilter, tagFilterNames, pathFilter, excludePathFilter,
        minSizeFilter, dateRangeFilter, addedSince, typeFilter, reviewFilter, textQuery,
        viewSettings.sortBy, viewSettings.sortDesc, viewSettings.groupBy,
    ) {
        selectedPaths = emptySet()
        rangeAnchorIndex = null
        rangeAnchorPath = null
        selectionMode = MediaSelectionMode.INDIVIDUAL
    }
    val dragSelection = rememberSelectionDragState()
    val peekState = org.fossify.gallery.compose.util.rememberPeekState()
    var showRateTagSheet by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showFolderPicker by remember { mutableStateOf(false) }
    var folderPickerIsMove by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var infoDialogPath by remember { mutableStateOf<String?>(null) }
    var currentRating by remember { mutableIntStateOf(0) }
    var ratingIsMixed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val repo = LocalMediaRepository.current
    LaunchedEffect(selectedPaths) {
        val ratings = if (selectedPaths.isEmpty()) emptyList() else withContext(Dispatchers.IO) {
            repo.getRatingsForPaths(selectedPaths).values.distinct()
        }
        ratingIsMixed = ratings.size > 1
        currentRating = ratings.singleOrNull() ?: 0
    }
    var showSaveCollection by remember { mutableStateOf(false) }
    var showUpdateCollection by remember { mutableStateOf(false) }
    var showAddToCollection by remember { mutableStateOf(false) }
    var collectionOptions by remember { mutableStateOf<List<org.fossify.gallery.models.MediaCollection>>(emptyList()) }
    var newCollectionName by remember { mutableStateOf("") }
    LaunchedEffect(showAddToCollection) {
        if (showAddToCollection) {
            newCollectionName = ""
            collectionOptions = withContext(Dispatchers.IO) { repo.getCollections() }
        }
    }
    // Inside a collection this "updates" its filter; otherwise it "saves" the ad-hoc filter as new.
    val onSaveAsCollection: (() -> Unit)? = { if (activeCollectionName != null) showUpdateCollection = true else showSaveCollection = true }
    val snackbarHostState = remember { SnackbarHostState() }
    val pendingUndo by UndoManager.actions.collectAsStateWithLifecycle()
    val quickTagsVersion by QuickTagsBus.version.collectAsStateWithLifecycle()
    val taggedPaths = state.taggedPaths
    val selectedCommonTags = state.selectedCommonTags
    val selectedTagCounts = state.selectedTagCounts
    LaunchedEffect(selectedPaths) { viewModel.loadCommonTags(selectedPaths) }
    val columnCount = org.fossify.gallery.compose.util.rememberEffectiveColumnCount(viewSettings.columnCount)
    val isGrid = viewSettings.viewType == ViewType.GRID
    val isMosaic = viewSettings.viewType == ViewType.MOSAIC
    val hasFilter = ratingFilter > 0 || tagFilterNames != null || pathFilter != null || excludePathFilter != null || minSizeFilter > 0 || dateRangeFilter > 0 || addedSince > 0L || typeFilter > 0 || additionalMediaPaths.isNotEmpty() || activeCollectionName != null || reviewFilter != MediaReviewFilter.NONE || textQuery.isNotBlank()
    // Filtered and unfiltered browsing both render from the Paging3 flow now (MediaViewModel picks
    // the filtered or unfiltered PagingSource based on whether a filter is active) - only Favorites'
    // externally-supplied mediaOverride still uses the legacy in-memory state.displayMedia path.
    // The unbounded Media tab stays Paging-backed. A Collection is a bounded result set, so it can
    // switch to the full-list model when the user chooses tag grouping (one medium may appear in
    // more than one tag section, which the incremental paging row model cannot represent).
    val fullListTagGrouping = mediaOverride == null && activeCollectionName != null && viewSettings.groupBy == GroupBy.TAG
    LaunchedEffect(fullListTagGrouping) { viewModel.setFullListGrouping(fullListTagGrouping) }
    val showPaged = mediaOverride == null && !fullListTagGrouping
    val displayMedia = state.displayMedia
    // Folder, Favorites and Collection results are bounded lists. Hold their mosaic for the same
    // initial aspect batch as the paged Media tab, so opening an album never briefly looks like a
    // square grid before the true layout arrives.
    val initialOverrideMosaicPaths = remember(isMosaic, displayMedia) {
        if (isMosaic) initialMosaicImagePaths(displayMedia) else emptyList()
    }
    LaunchedEffect(isMosaic, initialOverrideMosaicPaths) {
        if (isMosaic) viewModel.prepareInitialMosaicAspects(initialOverrideMosaicPaths)
    }
    val initialOverrideMosaicReady = initialOverrideMosaicPaths.all { it in state.aspectRatios }
    val pathIndexMap = remember(displayMedia) { displayMedia.withIndex().associate { it.value.path to it.index } }
    // Tag badges are fetched incrementally for the paths Paging has actually materialized. Loading
    // every tagged path at Media-tab startup created a large cursor and competed with the media
    // Paging cursor on large libraries. Keep the last known markers, and only replace the subset
    // that was just checked so a scroll never makes older visible badges disappear.
    LaunchedEffect(lazyPagingItems) {
        var previous = emptySet<String>()
        snapshotFlow {
            lazyPagingItems.itemSnapshotList.items
                .map { it.path }
                .takeLast(120)
                .toSet()
        }.collect { paths ->
            if (paths != previous) {
                previous = paths
                viewModel.loadTaggedPaths(paths)
            }
        }
    }
    LaunchedEffect(mediaOverride) {
        mediaOverride?.map { it.path }?.take(120)?.let(viewModel::loadTaggedPaths)
    }
    // Section collapse/expand is purely visual, session-only state (like the scroll position saver
    // above is the only ViewModel-tracked UI state elsewhere in this screen) - kept local so
    // toggling a header never re-triggers the tag DB fetch or sort in MediaViewModel.recompute().
    var collapsedGroupKeys by rememberSaveable { mutableStateOf(setOf<String>()) }
    val untaggedLabel = stringResource(R.string.group_untagged)
    // Group models can duplicate references for multi-tag media and become large in a drilled
    // folder/Favorites view. Build them on Default so a sort/group toggle never monopolizes the
    // main thread; the latest effect cancels an obsolete model when the user toggles again.
    var completeGroupRows by remember { mutableStateOf<List<GroupRow>>(emptyList()) }
    var isBuildingGroupRows by remember { mutableStateOf(false) }
    LaunchedEffect(displayMedia, state.tagsByPath, state.monthGroups, viewSettings.groupBy, viewSettings.groupOrder, viewSettings.sortDesc, viewSettings.onlyTopLevelTags, untaggedLabel, unratedLabel) {
        val groupBy = viewSettings.groupBy
        val groupOrder = viewSettings.groupOrder
        val sortDesc = viewSettings.sortDesc
        val onlyTopLevelTags = viewSettings.onlyTopLevelTags
        val tagsByPath = state.tagsByPath
        val monthGroups = state.monthGroups
        val media = displayMedia
        val hierarchy = ctx.config.tagHierarchy
        // Keep a complete, already visible grouping on screen while the next one is prepared.
        // Clearing it here replaced the whole media surface with a skeleton for every sort/filter
        // change, then laid it out again a moment later. That is technically correct but reads as
        // a jump. A skeleton remains appropriate only for the first ever grouping, when there is
        // no stable structure to preserve yet.
        isBuildingGroupRows = completeGroupRows.isEmpty()
        try {
            completeGroupRows = withContext(Dispatchers.Default) {
                when (groupBy) {
                    GroupBy.TAG -> buildTagGroupRows(media, tagsByPath, hierarchy, groupOrder, onlyTopLevelTags, emptySet(), untaggedLabel)
                    GroupBy.RATING -> buildRatingGroupRows(media, groupOrder, unratedLabel)
                    GroupBy.SIZE -> buildSizeGroupRows(media, sortDesc)
                    GroupBy.ALPHABET -> buildAlphabetGroupRows(media, sortDesc)
                    GroupBy.MONTH -> monthGroups.flatMap { g ->
                        val key = "month:${g.label}"
                        listOf(GroupRow.SectionHeader(key, g.label, 0, g.items.size, g.items.size, false, true, mediaPaths = g.items.mapTo(linkedSetOf()) { it.path }), GroupRow.Items(key, g.items))
                    }
                    GroupBy.NONE -> if (media.isEmpty()) emptyList() else listOf(GroupRow.Items("all", media))
                }
            }
        } finally {
            isBuildingGroupRows = false
        }
    }
    val groupRows = remember(completeGroupRows, collapsedGroupKeys) {
        applyCollapsedGroups(completeGroupRows, collapsedGroupKeys)
    }
    val scrollKeyToPath = remember(groupRows) {
        groupRows.flatMap { row ->
            when (row) {
                is GroupRow.SectionHeader -> emptyList()
                is GroupRow.Items -> row.media.map { medium -> "${row.sectionKey}/${medium.path}" to medium.path }
            }
        }.toMap()
    }
    val collapsibleKeys = remember(groupRows) { collapsibleGroupKeys(groupRows) }
    val allGroupsExpanded = collapsibleKeys.isNotEmpty() && collapsibleKeys.none { it in collapsedGroupKeys }
    val scrollItemIndexByPath = remember(groupRows) { lazyItemIndexByPath(groupRows) }
    var didRestoreSemanticScroll by remember(scrollPositionKey) { mutableStateOf(false) }
    val cornerShape = if (viewSettings.roundedCorners) RoundedCornerShape(Radius.sm) else RoundedCornerShape(0.dp)
    val itemSpacing = viewSettings.spacing.dp
    val mediaCardColor = when (viewSettings.displayMode) { DisplayMode.COMPACT,DisplayMode.NORMAL->MaterialTheme.colorScheme.surface; DisplayMode.DARK->MaterialTheme.colorScheme.surfaceVariant }
    val cropThumbnails = ctx.config.cropThumbnails
    val showThumbnailFileTypes = ctx.config.showThumbnailFileTypes
    val markFavoriteItems = ctx.config.markFavoriteItems
    val windowWidthDp = with(androidx.compose.ui.platform.LocalDensity.current) {
        androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.width.toDp()
    }
    val isNarrowMediaTile = (windowWidthDp / columnCount.coerceAtLeast(1)) < 80.dp

    fun openViewer(index: Int) {
        val paths = displayMedia.map { it.path }
        val tappedPath = paths.getOrNull(index) ?: return
        // Favorites and other in-memory override lists do not use the Paging fast path below.
        // They still need the same one-tap/one-destination guard, otherwise rapid taps can queue
        // several Viewer routes and make the user press Back repeatedly.
        if (overrideViewerOpeningPath != null) return
        overrideViewerOpeningPath = tappedPath
        if (onNavigateToViewer == null) {
            overrideViewerOpeningPath = null
            return
        }
        runCatching { onNavigateToViewer.invoke(paths, index) }
            .onFailure { overrideViewerOpeningPath = null }
    }

    // Same, for the legacy in-memory displayMedia list (Favorites' mediaOverride branch).
    fun pathsInDisplayRange(fromIdx: Int, toIdx: Int): Set<String> {
        if (displayMedia.isEmpty()) return emptySet()
        val lo = minOf(fromIdx, toIdx).coerceIn(0, displayMedia.lastIndex)
        val hi = maxOf(fromIdx, toIdx).coerceIn(0, displayMedia.lastIndex)
        return (lo..hi).map { displayMedia[it].path }.toSet()
    }

    fun selectDisplayItem(path: String, targetIndex: Int, startSelection: Boolean = false) {
        val anchor = rangeAnchorIndex
        selectedPaths = when {
            startSelection -> selectedPaths + path
            selectionMode == MediaSelectionMode.RANGE && anchor != null -> selectedPaths + pathsInDisplayRange(anchor, targetIndex)
            path in selectedPaths -> selectedPaths - path
            else -> selectedPaths + path
        }
        rangeAnchorIndex = targetIndex
        rangeAnchorPath = path
    }

    // Group selection is intentionally a visible header action. A long-press was too easy to
    // trigger and could include items hidden in collapsed child sections.
    fun selectGroup(row: GroupRow.SectionHeader) {
        if (row.mediaPaths.isEmpty()) return
        selectedPaths = selectedPaths + row.mediaPaths
        rangeAnchorIndex = null
        rangeAnchorPath = null
    }

    val hasSelection = selectedPaths.isNotEmpty()
    LaunchedEffect(hasSelection) {
        if (!hasSelection) {
            selectionMode = MediaSelectionMode.INDIVIDUAL
            rangeAnchorIndex = null
            rangeAnchorPath = null
            dragSelection.reset()
        }
    }
    LaunchedEffect(hasSelection) { onSelectionActiveChanged(hasSelection) }
    var isRefreshing by remember { mutableStateOf(false) }
    var showMultiSelectHint by remember { mutableStateOf(!ctx.config.hasSeenMultiSelectHint) }
    if (hasSelection) {
        LaunchedEffect(Unit) { showMultiSelectHint = false; ctx.config.hasSeenMultiSelectHint = true }
    }
    val density = androidx.compose.ui.platform.LocalDensity.current
    // Avoid a 0px first frame followed by a layout callback correction when entering selection.
    // The Material top app bar has a predictable base height; onSizeChanged still adjusts it for
    // larger text or future toolbar variants without running during ordinary scrolling.
    var selectionBarHeightPx by remember(density) {
        mutableIntStateOf(with(density) { androidx.compose.material3.TopAppBarDefaults.TopAppBarExpandedHeight.roundToPx() })
    }
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

    fun refreshMedia() {
        if (isRefreshing) return
        isRefreshing = true
        scope.launch {
            try {
                viewModel.refreshAndWait()
                if (showPaged) {
                    // Refresh only after the MediaStore sync committed. Starting Paging3 before that
                    // point caused a stale first page followed by a second invalidation-driven reload.
                    lazyPagingItems.refresh()
                    // A fast provider can resolve in one frame; first() handles both that case and the
                    // normal Loading -> NotLoading transition without a fixed 15-second spinner.
                    kotlinx.coroutines.withTimeoutOrNull(10_000) {
                        snapshotFlow { lazyPagingItems.loadState.refresh }.first { it !is LoadState.Loading }
                    }
                }
            } catch (e: Exception) {
                isRefreshing = false
                val result = if (org.fossify.gallery.helpers.LibrarySyncStatus.state.value.phase == org.fossify.gallery.helpers.LibrarySyncPhase.ERROR) {
                    SnackbarResult.Dismissed
                } else {
                    snackbarHostState.showSnackbar(
                        message = res.getString(R.string.library_sync_error),
                        actionLabel = res.getString(R.string.retry),
                        duration = SnackbarDuration.Long,
                    )
                }
                if (result == SnackbarResult.ActionPerformed) refreshMedia()
            } finally {
                isRefreshing = false
            }
        }
    }

    val pullRefreshEnabled = ctx.config.enablePullToRefresh
    // Keep the top chrome stable while the user is pulling at the top of the grid. A pull-to-refresh
    // gesture also briefly reports the lazy layout as scrolling; hiding the toolbar at that exact
    // moment changes the content's top padding underneath the indicator and causes a visible jump.
    // The indicator is anchored below the toolbar for the same reason, so it never sits on top of
    // the title or refresh/filter controls.
    val pullRefreshState = rememberPullToRefreshState()
    val canShowToolbar = showToolbar && !hasSelection && !pickMultiple
    val refreshIndicatorTopPadding = if (canShowToolbar) 64.dp else 0.dp
    val mediaContent: @Composable BoxScope.() -> Unit = {
        val selectionBackState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
        NavigationBackHandler(
            state = selectionBackState,
            isBackEnabled = hasSelection,
            onBackCompleted = { selectedPaths = emptySet() },
        )
        Box(modifier = modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            androidx.compose.animation.AnimatedVisibility(
                visible = canShowToolbar,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(AppMotion.short),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(AppMotion.short),
                modifier = Modifier.fillMaxWidth(),
            ) {
                MediaToolbar(
                    settingsSummary = viewSettingsSummary(viewSettings),
                    newMediaCount = newMediaCount.takeIf { !hasFilter && mediaOverride == null } ?: 0,
                    isRefreshing = isRefreshing,
                    onRefresh = ::refreshMedia,
                    onOpenNewMedia = onOpenNewMedia,
                    showGroupToggle = collapsibleKeys.isNotEmpty(),
                    groupsExpanded = allGroupsExpanded,
                    onToggleGroups = {
                        collapsedGroupKeys = setGroupsExpanded(
                            collapsedKeys = collapsedGroupKeys,
                            rows = groupRows,
                            expanded = collapsibleKeys.any { it in collapsedGroupKeys },
                        )
                    },
                    onOpenViewSettings = onOpenViewSettings,
                    modifier = Modifier,
                )
            }
            if (hasFilter && !hasSelection) {
                FilterBreadcrumbs(
                    ratingFilter = ratingFilter,
                    activeTagName = activeTagName,
                    activePathName = activePathName,
                    activeCollectionName = activeCollectionName,
                    activeFolderFilterName = activeFolderFilterName,
                    minSizeFilter = minSizeFilter,
                    dateRangeFilter = dateRangeFilter,
                    typeFilter = typeFilter,
                    resultCount = if (showPaged) (filteredSelectionCount ?: lazyPagingItems.itemCount) else displayMedia.size,
                    onClearRating = onClearRatingFilter,
                    onClearTag = onClearTagFilter,
                    onClearPath = onClearPathFilter,
                    onClearSize = onClearSizeFilter,
                    onClearDate = onClearDateFilter,
                    onClearType = onClearTypeFilter,
                    onClearAll = onClearFilter,
                    onSaveAsCollection = onSaveAsCollection,
                    tagMatchAll = tagMatchAll,
                    addedSince = addedSince,
                    onClearAddedSince = onClearAddedSinceFilter,
                    reviewFilter = reviewFilter,
                    onClearReview = onClearReviewFilter,
                    newMediaCount = newMediaCount,
                    onMarkNewMediaSeen = onMarkNewMediaSeen,
                    tagFilterNames = tagFilterNames,
                    onRemoveTag = onRemoveTagFilter,
                    onClearTagMatchAll = onClearTagMatchAll,
                )
            }
            Box(Modifier.fillMaxWidth().weight(1f)) {
        if (mediaOverride != null && mediaOverride.isEmpty()) {
            EmptyState(Icons.Default.Search, stringResource(R.string.no_media_in_folder), subtitle = stringResource(R.string.no_media_in_folder_hint))
        } else if (showPaged) {
            val refreshState = lazyPagingItems.loadState.refresh
            val refreshErrorMessage = (refreshState as? LoadState.Error)?.error?.message
            var automaticPagingRetries by remember { mutableIntStateOf(0) }
            LaunchedEffect(refreshState) {
                when (refreshState) {
                    is LoadState.NotLoading -> automaticPagingRetries = 0
                    is LoadState.Error -> if (lazyPagingItems.itemCount == 0 && automaticPagingRetries < 2) {
                        val attempt = automaticPagingRetries + 1
                        automaticPagingRetries = attempt
                        android.util.Log.e("MediaScreen", "Paging refresh failed; retrying ($attempt/2)", refreshState.error)
                        delay(attempt * 250L)
                        lazyPagingItems.retry()
                    }
                    else -> Unit
                }
            }
            val hasCollapsedPagedGroups = collapsedPagedRatingKeysState.value.isNotEmpty() && groupSummaries.isNotEmpty()
            val pagedContentState = when {
                lazyPagingItems.itemCount > 0 || hasCollapsedPagedGroups -> "content"
                refreshState is LoadState.Loading -> "loading"
                refreshState is LoadState.Error -> "error"
                else -> "empty"
            }
            Crossfade(targetState = pagedContentState, animationSpec = AppMotion.short, label = "pagedMediaContent") { s ->
                when (s) {
                    "loading" -> MediaSkeleton(
                        columns = columnCount,
                        viewType = viewSettings.viewType,
                        itemSpacing = itemSpacing,
                        showFileName = viewSettings.showFileNames,
                        reserveGridCaptionSpace = false,
                        listDensity = viewSettings.listDensity,
                    )
                    "error" -> LoadErrorState(
                        message = refreshErrorMessage,
                        onRetry = { lazyPagingItems.retry() },
                    )
                    "empty" -> EmptyState(
                        icon = if (hasFilter) Icons.Default.SearchOff else Icons.Default.Search,
                        title = if (hasFilter) stringResource(R.string.no_results) else stringResource(R.string.no_media_found),
                        subtitle = stringResource(if (hasFilter) R.string.no_results_hint else R.string.no_media_found_hint),
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
                        rangeAnchorPathState = rangeAnchorPathState,
                        selectionModeState = selectionModeState,
                        dragSelection = dragSelection,
                        contentTopInset = contentTopInset,
                        tabIndex = tabIndex,
                        collapsedPagedRatingKeysState = collapsedPagedRatingKeysState,
                        onTagClick = onTagClick,
                        onNavigateToViewer = onNavigateToViewer,
                        onNavigateToViewerFast = onNavigateToViewerFast,
                        unratedLabel = unratedLabel,
                        pickMultiple = pickMultiple,
                    )
                }
            }
            InlineRefreshIndicator(
                visible = refreshState is LoadState.Loading,
                contentAvailable = lazyPagingItems.itemCount > 0,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        } else {
        when {
            fullListTagGrouping && state.isFullListGroupingLoading -> MediaSkeleton(
                columns = columnCount,
                viewType = viewSettings.viewType,
                itemSpacing = itemSpacing,
                showFileName = viewSettings.showFileNames,
                reserveGridCaptionSpace = false,
                listDensity = viewSettings.listDensity,
            )
            isMosaic && initialOverrideMosaicPaths.isNotEmpty() && !initialOverrideMosaicReady -> MediaSkeleton(
                columns = columnCount,
                viewType = ViewType.MOSAIC,
                itemSpacing = itemSpacing,
                showFileName = viewSettings.showFileNames,
            )
            displayMedia.isEmpty() -> {
                EmptyState(
                    icon = if (hasFilter) Icons.Default.SearchOff else Icons.Default.Search,
                    title = if (hasFilter) stringResource(R.string.no_results) else stringResource(R.string.no_media_found),
                    subtitle = stringResource(if (hasFilter) R.string.no_results_hint else R.string.no_media_found_hint),
                    actionLabel = if (hasFilter) stringResource(R.string.clear_filter) else null,
                    onAction = if (hasFilter) onClearFilter else null,
                )
            }
            isBuildingGroupRows && completeGroupRows.isEmpty() -> MediaSkeleton(
                columns = columnCount,
                viewType = viewSettings.viewType,
                itemSpacing = itemSpacing,
                showFileName = viewSettings.showFileNames,
                reserveGridCaptionSpace = isGrid,
                listDensity = viewSettings.listDensity,
            )
            isGrid -> {
                Column(Modifier.padding(top = contentTopInset)) {
                    val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = initialOverrideScrollIndex, initialFirstVisibleItemScrollOffset = initialOverrideScrollOffset)
                    LaunchedEffect(gridState, groupRows, scrollPositionKey) {
                        if (scrollPositionKey != null && !didRestoreSemanticScroll && restoredOverrideScrollPosition.anchorPath.isNotBlank()) {
                            scrollItemIndexByPath[restoredOverrideScrollPosition.anchorPath]?.let { index ->
                                if (gridState.firstVisibleItemIndex != index || gridState.firstVisibleItemScrollOffset != restoredOverrideScrollPosition.offset) {
                                    gridState.scrollToItem(index, restoredOverrideScrollPosition.offset)
                                }
                                didRestoreSemanticScroll = true
                            }
                        }
                        snapshotFlow {
                            val firstVisibleMedia = gridState.layoutInfo.visibleItemsInfo
                                .asSequence()
                                .mapNotNull { pathForScrollKey(it.key, scrollKeyToPath) }
                                .firstOrNull()
                            Triple(gridState.firstVisibleItemIndex, gridState.firstVisibleItemScrollOffset, firstVisibleMedia)
                        }.collect { (i, o, path) ->
                            saveOverrideScrollPosition(i, o, path.orEmpty())
                            onFirstVisibleMediaPathChanged(path)
                        }
                    }
                    ScrollToTopEffect(tabIndex) { gridState.animateScrollToItem(0) }
                    val isScrolling by remember { derivedStateOf { gridState.isScrollInProgress } }
                    LaunchedEffect(dragSelection.isDragging) {
                        if (dragSelection.isDragging) {
                            var targetIdx = gridState.firstVisibleItemIndex
                            while (dragSelection.isDragging) {
                                val s = dragSelection.autoScrollSpeed
                                if (s < -0.5f) targetIdx = maxOf(targetIdx - 1, 0)
                                else if (s > 0.5f) {
                                    val last = (gridState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
                                    targetIdx = (targetIdx + 1).coerceAtMost(last)
                                }
                                else { kotlinx.coroutines.delay(50); continue }
                                gridState.animateScrollToItem(targetIdx, 0)
                                kotlinx.coroutines.delay((100 / kotlin.math.abs(s).coerceAtLeast(0.5f)).toLong().coerceIn(50, 150))
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
                    groupRows.forEach { row ->
                        when (row) {
                            is GroupRow.SectionHeader -> item(key = "hdr_${row.key}", span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
                                SectionHeader(label = row.label, count = if (row.isExpanded) row.exactCount else row.totalCount, depth = row.depth, hasChildren = row.hasChildren, isExpanded = row.isExpanded, onToggle = { collapsedGroupKeys = org.fossify.gallery.helpers.toggleGroupKey(collapsedGroupKeys, row.key) }, ratingValue = row.ratingValue, accentColor = row.rootKey?.let { tagAccentColor(it) }, onSelectGroup = { selectGroup(row) })
                            }
                            is GroupRow.Items -> {
                        val groupItems = row.media
                        items(groupItems.size, key = { "${row.sectionKey}/${groupItems[it].path}" }, contentType = { groupItems[it].type }) { idx ->
                            val m = groupItems[idx]; val originalIdx = pathIndexMap[m.path] ?: 0; val isVideo = remember(m.path, m.type) { m.type == TYPE_VIDEOS || m.path.substringAfterLast('.',"").lowercase() in VIDEO_EXTENSIONS }
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
                                showVideoDuration = org.fossify.gallery.compose.util.ThumbnailOverlayState.showVideoDuration,
                                cropThumbnails = cropThumbnails,
                                showRating = org.fossify.gallery.compose.util.ThumbnailOverlayState.showRating,
                                showFileType = showThumbnailFileTypes,
                                markFavorite = markFavoriteItems,
                                isNarrowTile = isNarrowMediaTile,
                                onClick = {
                                    if (hasSelection || pickMultiple) {
                                        selectDisplayItem(m.path, originalIdx)
                                    } else openViewer(originalIdx)
                                },
                                onLongClick = {
                                    selectDisplayItem(m.path, originalIdx, startSelection = true)
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
                    val currentBreadcrumbLabel by remember(groupRows) { derivedStateOf { currentBreadcrumb(groupRows, gridState.firstVisibleItemIndex) } }
                    FloatingSectionLabel(currentBreadcrumbLabel, isScrolling, Modifier.align(Alignment.TopCenter).padding(top = 8.dp))
                    FastScrollBar(
                        state = gridState,
                        positionLabel = { index -> currentBreadcrumb(groupRows, index) },
                        modifier = Modifier.align(Alignment.CenterEnd).padding(vertical = 8.dp),
                    )
                    }
                    }
                }
            }
            isMosaic -> {
                Column(Modifier.padding(top = contentTopInset)) {
                    val mosaicState = rememberLazyStaggeredGridState(initialFirstVisibleItemIndex = initialOverrideScrollIndex, initialFirstVisibleItemScrollOffset = initialOverrideScrollOffset)
                    LaunchedEffect(mosaicState, groupRows, scrollPositionKey) {
                        if (scrollPositionKey != null && !didRestoreSemanticScroll && restoredOverrideScrollPosition.anchorPath.isNotBlank()) {
                            scrollItemIndexByPath[restoredOverrideScrollPosition.anchorPath]?.let { index ->
                                if (mosaicState.firstVisibleItemIndex != index || mosaicState.firstVisibleItemScrollOffset != restoredOverrideScrollPosition.offset) {
                                    mosaicState.scrollToItem(index, restoredOverrideScrollPosition.offset)
                                }
                                didRestoreSemanticScroll = true
                            }
                        }
                        snapshotFlow {
                            val firstVisibleMedia = mosaicState.layoutInfo.visibleItemsInfo
                                .asSequence()
                                .mapNotNull { pathForScrollKey(it.key, scrollKeyToPath) }
                                .firstOrNull()
                            Triple(mosaicState.firstVisibleItemIndex, mosaicState.firstVisibleItemScrollOffset, firstVisibleMedia)
                        }.collect { (i, o, path) ->
                            saveOverrideScrollPosition(i, o, path.orEmpty())
                            onFirstVisibleMediaPathChanged(path)
                        }
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
                                else if (s > 0.5f) {
                                    val last = (mosaicState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
                                    targetIdx = (targetIdx + 1).coerceAtMost(last)
                                }
                                else { kotlinx.coroutines.delay(50); continue }
                                mosaicState.animateScrollToItem(targetIdx, 0)
                                kotlinx.coroutines.delay((120 / kotlin.math.abs(s).coerceAtLeast(0.5f)).toLong().coerceIn(60, 200))
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
                    groupRows.forEach { row ->
                        when (row) {
                            is GroupRow.SectionHeader -> item(key = "hdr_${row.key}", span = StaggeredGridItemSpan.FullLine, contentType = "header") {
                                SectionHeader(label = row.label, count = if (row.isExpanded) row.exactCount else row.totalCount, depth = row.depth, hasChildren = row.hasChildren, isExpanded = row.isExpanded, onToggle = { collapsedGroupKeys = org.fossify.gallery.helpers.toggleGroupKey(collapsedGroupKeys, row.key) }, ratingValue = row.ratingValue, accentColor = row.rootKey?.let { tagAccentColor(it) }, onSelectGroup = { selectGroup(row) })
                            }
                            is GroupRow.Items -> {
                        val groupItems = row.media
                        items(groupItems.size, key = { "${row.sectionKey}/${groupItems[it].path}" }, contentType = { groupItems[it].type }) { idx ->
                            val m = groupItems[idx]; val originalIdx = pathIndexMap[m.path] ?: 0; val isVideo = remember(m.path, m.type) { m.type == TYPE_VIDEOS || m.path.substringAfterLast('.',"").lowercase() in VIDEO_EXTENSIONS }
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
                                showVideoDuration = org.fossify.gallery.compose.util.ThumbnailOverlayState.showVideoDuration,
                                cropThumbnails = cropThumbnails,
                                showRating = org.fossify.gallery.compose.util.ThumbnailOverlayState.showRating,
                                showFileType = showThumbnailFileTypes,
                                markFavorite = markFavoriteItems,
                                isNarrowTile = isNarrowMediaTile,
                                onClick = {
                                    if (hasSelection || pickMultiple) {
                                        selectDisplayItem(m.path, originalIdx)
                                    } else openViewer(originalIdx)
                                },
                                onLongClick = {
                                    selectDisplayItem(m.path, originalIdx, startSelection = true)
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
                    val currentBreadcrumbLabelStag by remember(groupRows) { derivedStateOf { currentBreadcrumb(groupRows, mosaicState.firstVisibleItemIndex) } }
                    FloatingSectionLabel(currentBreadcrumbLabelStag, isScrollingStag, Modifier.align(Alignment.TopCenter).padding(top = 8.dp))
                    FastScrollBar(
                        state = mosaicState,
                        positionLabel = { index -> currentBreadcrumb(groupRows, index) },
                        modifier = Modifier.align(Alignment.CenterEnd).padding(vertical = 8.dp),
                    )
                    }
                    }
                }
            }
            else -> {
                val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialOverrideScrollIndex, initialFirstVisibleItemScrollOffset = initialOverrideScrollOffset)
                LaunchedEffect(listState, groupRows, scrollPositionKey) {
                    if (scrollPositionKey != null && !didRestoreSemanticScroll && restoredOverrideScrollPosition.anchorPath.isNotBlank()) {
                        scrollItemIndexByPath[restoredOverrideScrollPosition.anchorPath]?.let { index ->
                            if (listState.firstVisibleItemIndex != index || listState.firstVisibleItemScrollOffset != restoredOverrideScrollPosition.offset) {
                                listState.scrollToItem(index, restoredOverrideScrollPosition.offset)
                            }
                            didRestoreSemanticScroll = true
                        }
                    }
                    snapshotFlow {
                        val firstVisibleMedia = listState.layoutInfo.visibleItemsInfo
                            .asSequence()
                            .mapNotNull { pathForScrollKey(it.key, scrollKeyToPath) }
                            .firstOrNull()
                        Triple(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset, firstVisibleMedia)
                    }.collect { (i, o, path) ->
                        saveOverrideScrollPosition(i, o, path.orEmpty())
                        onFirstVisibleMediaPathChanged(path)
                    }
                }
                ScrollToTopEffect(tabIndex) { listState.animateScrollToItem(0) }
                val listGap = ((viewSettings.spacing - 2) / 2).coerceAtLeast(0).dp
                Column(Modifier.padding(top = contentTopInset)) {
                Box(
                    Modifier
                        .clipToBounds()
                        .dragSelectionGesture(
                            dragSelection,
                            enabled = false,
                            onSelectPath = { path -> selectedPaths = selectedPaths + path },
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
                            is GroupRow.SectionHeader -> stickyHeader(key = "hdr_${row.key}", contentType = "header") {
                                SectionHeader(label = row.label, count = if (row.isExpanded) row.exactCount else row.totalCount, depth = row.depth, hasChildren = row.hasChildren, isExpanded = row.isExpanded, onToggle = { collapsedGroupKeys = org.fossify.gallery.helpers.toggleGroupKey(collapsedGroupKeys, row.key) }, ratingValue = row.ratingValue, accentColor = row.rootKey?.let { tagAccentColor(it) }, showGuideLine = true, onSelectGroup = { selectGroup(row) })
                            }
                            is GroupRow.Items -> {
                        val groupItems = row.media
                        items(groupItems.size, key = { "${row.sectionKey}/${groupItems[it].path}" }, contentType = { groupItems[it].type }) { idx ->
                            val m = groupItems[idx]; val originalIdx = pathIndexMap[m.path] ?: 0; val isVideo = remember(m.path, m.type) { m.type == TYPE_VIDEOS || m.path.substringAfterLast('.',"").lowercase() in VIDEO_EXTENSIONS }
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
                                        selectDisplayItem(m.path, originalIdx)
                                    } else openViewer(originalIdx)
                                },
                                onLongClick = {
                                    selectDisplayItem(m.path, originalIdx, startSelection = true)
                                },
                                onSwipeToSelect = { selectedPaths = selectedPaths + m.path },
                                onBoundsChanged = { r -> dragSelection.registerItemBounds(m.path, r) },
                                onBoundsRemoved = { dragSelection.unregisterItemBounds(m.path) },
                                showRating = org.fossify.gallery.compose.util.ThumbnailOverlayState.showRating,
                                showVideoDuration = org.fossify.gallery.compose.util.ThumbnailOverlayState.showVideoDuration,
                                compact = viewSettings.listDensity == ListDensity.COMPACT,
                                roundedCorners = viewSettings.roundedCorners,
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
                FastScrollBar(
                    state = listState,
                    positionLabel = { index -> currentBreadcrumb(groupRows, index) },
                    modifier = Modifier.align(Alignment.CenterEnd).padding(vertical = 8.dp),
                )
                }
                }
                }
                }
            }
        }
        }
        }
        org.fossify.gallery.compose.components.LongPressSelectHint(
            visible = showMultiSelectHint && !hasSelection && displayMedia.isNotEmpty(),
            onDismiss = { showMultiSelectHint = false; ctx.config.hasSeenMultiSelectHint = true },
            modifier = Modifier.align(Alignment.TopCenter).padding(top = contentTopInset + 8.dp),
        )
        // Selection actions are an overlay, not a reason to move the media grid. Keeping this bar
        // a stable layer avoids the old slide-in competing with the first scroll after a long press.
        if (hasSelection && !pickMultiple) {
            SelectionTopAppBar(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .onSizeChanged { size ->
                        if (selectionBarHeightPx != size.height) selectionBarHeightPx = size.height
                    },
                count = selectedPaths.size,
                onClose = { selectedPaths = emptySet() },
                onShare = {
                    // Use the same URI/MIME/permission path as the legacy media selection. It
                    // handles both one and many files and keeps the selection intact after the
                    // chooser returns, so the user can continue with the same batch.
                    (ctx as? android.app.Activity)?.shareMediaPaths(ArrayList(selectedPaths))
                },
                onPreview = selectedPaths.singleOrNull()?.let { path ->
                    { peekState.show(path, path.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS) }
                },
                onDelete = { if (ctx.config.skipDeleteConfirmation) { val d = selectedPaths.toSet(); viewModel.softDeletePaths(d); UndoManager.push(UndoAction(paths = d, type = UndoType.DELETE)); selectedPaths = emptySet() } else showDeleteConfirm = true },
                // mediaOverride (a drilled-into album) must scope select-all/invert to that album's
                // own media - viewModel.activePaths() is the app-wide set, so "Select all" inside a
                // 300-item folder used to silently mark every medium on the device (560 in the repro)
                // and put an accidental mass delete/move one tap away.
                onSelectAll = { scope.launch { selectedPaths = mediaOverride?.map { it.path }?.toSet() ?: (if (hasFilter) viewModel.activePathsSortedFiltered().toSet() else viewModel.activePaths()) } },
                onInvert = { scope.launch { val all = mediaOverride?.map { it.path }?.toSet() ?: (if (hasFilter) viewModel.activePathsSortedFiltered().toSet() else viewModel.activePaths()); selectedPaths = all - selectedPaths } },
                onCopy = { folderPickerIsMove = false; showFolderPicker = true },
                onMove = { folderPickerIsMove = true; showFolderPicker = true },
                onRate = if (showMetadataActions) ({ showRateTagSheet = true }) else null,
                onKeep = onKeepSelection?.let { keep ->
                    {
                        keep(selectedPaths)
                        selectedPaths = emptySet()
                    }
                },
                onTags = if (showMetadataActions) ({ showRateTagSheet = true }) else null,
                onRename = { showRenameDialog = true },
                onInfo = { infoDialogPath = selectedPaths.singleOrNull() },
                onAddToCollection = { showAddToCollection = true },
                onMarkDecided = {
                    val paths = selectedPaths.toSet()
                    scope.launch(Dispatchers.IO) { repo.setReviewStatus(paths, MediaReviewStatus.DECIDED) }
                    selectedPaths = emptySet()
                },
                onSkipReview = {
                    val paths = selectedPaths.toSet()
                    scope.launch(Dispatchers.IO) { repo.setReviewStatus(paths, MediaReviewStatus.SKIPPED) }
                    selectedPaths = emptySet()
                },
                onResetReview = {
                    val paths = selectedPaths.toSet()
                    scope.launch(Dispatchers.IO) { repo.setReviewStatus(paths, null) }
                    selectedPaths = emptySet()
                },
                selectionMode = selectionMode,
                onSelectionModeChange = { mode -> selectionMode = mode },
                selectionScopeLabel = when {
                    selectionMode == MediaSelectionMode.RANGE -> rangeAnchorPath?.let { stringResource(R.string.selection_range_from, java.io.File(it).name) }
                    mediaOverride != null -> stringResource(R.string.selection_scope_visible, mediaOverride.size)
                    hasFilter && filteredSelectionCount != null -> stringResource(R.string.selection_scope_results, filteredSelectionCount!!)
                    else -> null
                },
                selectAllLabel = stringResource(if (hasFilter) R.string.action_select_all_results else R.string.action_select_all),
            )
        }
        // Picker multi-select confirm bar - replaces the normal selection bar (gated above) while
        // acting as a picker. Returns the chosen paths to the caller via onConfirmPick.
        if (pickMultiple && hasSelection) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, tonalElevation = 3.dp, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { selectedPaths = emptySet() }) { Icon(Icons.Default.Close, stringResource(org.fossify.commons.R.string.cancel), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
                    Text("${selectedPaths.size}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(start = 8.dp).weight(1f))
                    androidx.compose.material3.Button(onClick = { onConfirmPick?.invoke(selectedPaths.toList()) }, enabled = selectedPaths.isNotEmpty()) {
                        Text(stringResource(R.string.pick_confirm_count, selectedPaths.size))
                    }
                }
            }
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
            // Registered after (so it takes priority over) the selection-clearing handler above -
            // otherwise system back while peeking fell through to that one and wiped the selection
            // instead of just closing the preview.
            val peekBackState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
            NavigationBackHandler(state = peekBackState, isBackEnabled = true, onBackCompleted = { peekState.hide() })
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
                    GalleryImage(path = peekPath, contentDescription = peekPath.substringAfterLast('/'), modifier = Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.75f), contentScale = ContentScale.Fit, thumbnailSize = 1024)
                }
            }
        }
        }
    if (pullRefreshEnabled) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = ::refreshMedia,
            state = pullRefreshState,
            modifier = Modifier.fillMaxSize(),
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullRefreshState,
                    isRefreshing = isRefreshing,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = refreshIndicatorTopPadding),
                )
            },
            content = mediaContent,
        )
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
            ratingIsMixed = ratingIsMixed,
            onRate = { i ->
                currentRating = i
                ratingIsMixed = false
                viewModel.setRatingFor(batch, i)
            },
            initialTags = selectedCommonTags,
            mixedTagCounts = selectedTagCounts,
            onAddTag = { viewModel.addTagFor(batch, it) },
            onRemoveTag = { viewModel.removeTagFor(batch, it) },
            suggestedTags = state.allTags,
            suggestedTagCounts = state.tagCounts,
            recentTags = remember(quickTagsVersion) { ctx.config.recentTags },
            tagHierarchy = ctx.config.tagHierarchy,
            onSetTagParent = { child, parent ->
                val hierarchy = ctx.config.tagHierarchy.toMutableMap()
                if (parent.isNullOrBlank()) hierarchy.remove(child) else hierarchy[child] = parent
                ctx.config.tagHierarchy = hierarchy
            },
            onDismiss = { showRateTagSheet = false },
        )
    }
    if (showFolderPicker) { val batch=selectedPaths.toList(); FolderPickerSheet(isMoveOperation=folderPickerIsMove,sourcePaths=batch,onDismiss={showFolderPicker=false;selectedPaths=emptySet()}) }
    if (showRenameDialog) { val batch=selectedPaths.toList(); RenameDialog(paths=batch,onRenamed={mapping->selectedPaths=selectedPaths.map{mapping[it]?:it}.toSet()},onDismiss={showRenameDialog=false;viewModel.silentRefresh()}) }
    infoDialogPath?.let { path -> MediaInfoDialog(path = path, onDismiss = { infoDialogPath = null }) }
    if (showDeleteConfirm) {
        val itemsCnt = selectedPaths.size
        val itemsText = res.getQuantityString(org.fossify.commons.R.plurals.delete_items, itemsCnt, itemsCnt)
        val question = res.getString(if (ctx.config.useRecycleBin) org.fossify.commons.R.string.move_to_recycle_bin_confirmation else org.fossify.commons.R.string.deletion_confirmation, itemsText)
        ConfirmDestructive(
            title = stringResource(org.fossify.commons.R.string.delete),
            text = question,
            confirmLabel = stringResource(org.fossify.commons.R.string.delete),
            onConfirm = {
                showDeleteConfirm = false
                val d = selectedPaths.toSet()
                ctx.withRecycleBinDeletePasswordCheck { viewModel.softDeletePaths(d); UndoManager.push(UndoAction(paths = d, type = UndoType.DELETE)); selectedPaths = emptySet() }
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }

    if (showAddToCollection) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showAddToCollection = false },
            title = { Text(stringResource(R.string.add_to_collection)) },
            text = {
                Column(
                    Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    if (collectionOptions.isNotEmpty()) {
                        Text(
                            stringResource(R.string.choose_collection),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                        collectionOptions.forEach { collection ->
                            androidx.compose.material3.TextButton(
                                onClick = {
                                    val batch = selectedPaths.toSet()
                                    scope.launch {
                                        val added = withContext(Dispatchers.IO) {
                                            val current = repo.getCollections().firstOrNull { it.id == collection.id }
                                            if (current != null) {
                                                val merged = (current.getMediaPaths() + batch).distinct()
                                                val id = repo.insertCollection(current.copy(mediaPaths = org.fossify.gallery.models.MediaCollection.createPathsJson(merged)))
                                                if (id > 0L) { repo.refreshCollectionsCache(); true } else false
                                            } else false
                                        }
                                        if (added) {
                                            selectedPaths = emptySet()
                                            showAddToCollection = false
                                            ctx.toast(res.getString(R.string.media_added_to_collection, batch.size, collection.name))
                                        } else {
                                            ctx.toast(R.string.collection_add_failed)
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(collection.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        androidx.compose.material3.HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    }
                    Text(
                        stringResource(R.string.new_collection),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                    androidx.compose.material3.OutlinedTextField(
                        value = newCollectionName,
                        onValueChange = { newCollectionName = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.name)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    enabled = newCollectionName.isNotBlank() && selectedPaths.isNotEmpty(),
                    onClick = {
                        val n = newCollectionName.trim()
                        val batch = selectedPaths.toSet()
                        scope.launch {
                            val saved = withContext(Dispatchers.IO) {
                                repo.insertCollection(
                                    org.fossify.gallery.models.MediaCollection(
                                        name = n,
                                        mediaPaths = org.fossify.gallery.models.MediaCollection.createPathsJson(batch.toList()),
                                    )
                                ).also { id -> if (id > 0L) repo.refreshCollectionsCache() } > 0L
                            }
                            if (saved) {
                                selectedPaths = emptySet()
                                showAddToCollection = false
                                ctx.toast(res.getString(R.string.collection_saved, n))
                            } else {
                                ctx.toast(R.string.collection_add_failed)
                            }
                        }
                    },
                ) { Text(stringResource(org.fossify.commons.R.string.save)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showAddToCollection = false }) {
                    Text(stringResource(org.fossify.commons.R.string.cancel))
                }
            },
        )
    }

    if (showSaveCollection) {
        // A sensible default name from whatever filter dimensions are active, e.g. "★4 · Images · > 10 MB".
        val defaultName = remember {
            buildList {
                if (ratingFilter > 0) add("★$ratingFilter")
                activeTagName?.takeIf { it.isNotBlank() }?.let { add("#$it") }
                when (typeFilter) {
                    org.fossify.gallery.helpers.TYPE_IMAGES -> add(res.getString(R.string.images))
                    org.fossify.gallery.helpers.TYPE_VIDEOS -> add(res.getString(R.string.videos))
                    org.fossify.gallery.helpers.TYPE_GIFS -> add(res.getString(R.string.gif))
                    org.fossify.gallery.helpers.TYPE_RAWS -> add(res.getString(R.string.raw))
                    org.fossify.gallery.helpers.TYPE_SVGS -> add(res.getString(R.string.svg))
                }
                if (minSizeFilter > 0) add("> ${formatFileSize(minSizeFilter)}")
                when (dateRangeFilter) { 1 -> add(res.getString(R.string.today)); 2 -> add(res.getString(R.string.date_range_last_7_days)); 3 -> add(res.getString(R.string.date_range_last_30_days)); 4 -> add(res.getString(R.string.date_range_last_year)) }
                activeFolderFilterName?.let { add(it) }
            }.joinToString(" · ").ifBlank { res.getString(R.string.save_as_collection) }
        }
        var name by remember { mutableStateOf(defaultName) }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showSaveCollection = false },
            title = { Text(stringResource(R.string.save_as_collection)) },
            text = { androidx.compose.material3.OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text(stringResource(R.string.name)) }) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    val n = name.trim().ifBlank { defaultName }
                    val coll = org.fossify.gallery.models.MediaCollection(
                        name = n,
                        includedPaths = org.fossify.gallery.models.MediaCollection.createPathsJson(pathFilter?.toList() ?: emptyList()),
                        excludedPaths = org.fossify.gallery.models.MediaCollection.createPathsJson(excludePathFilter?.toList() ?: emptyList()),
                        tagFilter = activeTagName ?: "",
                        ratingFilter = ratingFilter,
                        minSize = minSizeFilter,
                        dateRange = dateRangeFilter,
                        typeFilter = typeFilter,
                        mediaPaths = org.fossify.gallery.models.MediaCollection.createPathsJson(selectedPaths.toList()),
                    )
                    scope.launch { withContext(Dispatchers.IO) { repo.insertCollection(coll); repo.refreshCollectionsCache() }; ctx.toast(res.getString(R.string.collection_saved, n)) }
                    showSaveCollection = false
                }) { Text(stringResource(org.fossify.commons.R.string.ok)) }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { showSaveCollection = false }) { Text(stringResource(org.fossify.commons.R.string.cancel)) } },
        )
    }

    if (showUpdateCollection && activeCollectionName != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showUpdateCollection = false },
            title = { Text(stringResource(R.string.update_collection)) },
            text = { Text(stringResource(R.string.update_collection_confirm, activeCollectionName)) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    val target = activeCollectionName
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            val existing = repo.getCollections().find { it.name == target }
                            if (existing != null) {
                                // Keep id/name/searchQuery/sortOrder; overwrite only the filter dimensions
                                // with the current (possibly refined) filter.
                                repo.insertCollection(existing.copy(
                                    includedPaths = org.fossify.gallery.models.MediaCollection.createPathsJson(pathFilter?.toList() ?: emptyList()),
                                    excludedPaths = org.fossify.gallery.models.MediaCollection.createPathsJson(excludePathFilter?.toList() ?: emptyList()),
                                    tagFilter = activeTagName ?: "",
                                    ratingFilter = ratingFilter,
                                    minSize = minSizeFilter,
                                    dateRange = dateRangeFilter,
                                    typeFilter = typeFilter,
                                ))
                                repo.refreshCollectionsCache()
                            }
                        }
                        ctx.toast(res.getString(R.string.collection_updated, target))
                    }
                    showUpdateCollection = false
                }) { Text(stringResource(org.fossify.commons.R.string.ok)) }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { showUpdateCollection = false }) { Text(stringResource(org.fossify.commons.R.string.cancel)) } },
        )
    }
}

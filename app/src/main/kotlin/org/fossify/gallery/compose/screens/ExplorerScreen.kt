package org.fossify.gallery.compose.screens
import org.fossify.gallery.compose.theme.Radius

import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.fossify.gallery.compose.components.FolderSelectionOverlay
import org.fossify.gallery.compose.components.MediaListRow
import org.fossify.gallery.compose.components.MediaTile
import org.fossify.gallery.compose.components.findDuplicateFolderNames
import org.fossify.gallery.compose.components.folderPathHint
import org.fossify.gallery.compose.theme.BlurRadius
import org.fossify.gallery.compose.theme.LocalMediaRepository
import org.fossify.gallery.compose.util.ThumbnailOverlayState
import org.fossify.gallery.compose.theme.Scrim
import org.fossify.gallery.compose.theme.SelectionVisual
import org.fossify.gallery.compose.util.BlurState
import org.fossify.gallery.compose.util.ColumnCountIndicator
import org.fossify.gallery.compose.util.ScrollToTopEffect
import org.fossify.gallery.compose.util.decodeImageAspect
import org.fossify.gallery.compose.util.pinchToChangeColumns
import org.fossify.gallery.compose.util.privacyBlur
import org.fossify.gallery.compose.util.sharedElementKey
import org.fossify.gallery.compose.util.rememberPeekState
import org.fossify.gallery.compose.util.MediaSelectionMode
import org.fossify.gallery.compose.components.FolderTile
import org.fossify.gallery.compose.components.EmptyState
import org.fossify.gallery.compose.components.SelectionAppBar
import org.fossify.gallery.compose.components.SelectionTopAppBar
import org.fossify.gallery.compose.components.GalleryImage
import org.fossify.gallery.compose.components.ConfirmDestructive
import org.fossify.gallery.compose.components.FolderRenameDialog
import org.fossify.gallery.compose.components.RateAndTagSheet
import org.fossify.gallery.compose.components.RenameDialog
import org.fossify.gallery.compose.components.SectionHeader
import org.fossify.gallery.compose.components.ItemCountBadge
import org.fossify.gallery.compose.components.UndoBar
import org.fossify.gallery.compose.components.InlineRefreshIndicator
import org.fossify.gallery.compose.components.ActiveFilterChip
import org.fossify.gallery.compose.components.LibraryFilterSummary
import org.fossify.gallery.compose.components.LoadErrorBanner
import org.fossify.gallery.compose.components.LoadErrorState
import org.fossify.gallery.compose.components.MediaInfoDialog
import org.fossify.gallery.compose.components.FastScrollBar
import org.fossify.gallery.compose.components.VideoPlayOverlay
import org.fossify.gallery.compose.components.TagChipRow
import org.fossify.gallery.compose.components.GroupToggleButton
import org.fossify.gallery.compose.theme.AppMotion
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.shareMediaPaths
import org.fossify.gallery.extensions.mediaDB
import org.fossify.gallery.R
import androidx.compose.ui.res.stringResource
import org.fossify.commons.extensions.toast
import org.fossify.gallery.helpers.UndoAction
import org.fossify.gallery.helpers.UndoManager
import org.fossify.gallery.helpers.withRecycleBinDeletePasswordCheck
import org.fossify.gallery.helpers.UndoType
import org.fossify.gallery.helpers.VIDEO_EXTENSIONS
import org.fossify.gallery.helpers.explorerAncestorChain
import org.fossify.gallery.helpers.selectionForAll
import org.fossify.gallery.helpers.applyCollapsedGroups
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Delete
import java.io.File
import org.fossify.gallery.models.MediaReviewFilter
import org.fossify.gallery.models.MediaReviewStatus
import org.fossify.gallery.viewmodels.LibraryFilterActions
import org.fossify.gallery.viewmodels.LibraryFilterState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, FlowPreview::class)
@Composable
fun ExplorerScreen(
    internalStoragePath: String,
    nameFilter: String = "",
    onClearNameFilter: () -> Unit = {},
    filterState: LibraryFilterState = LibraryFilterState(),
    filterActions: LibraryFilterActions = LibraryFilterActions(),
    modifier: Modifier = Modifier,
    folderSettings: ViewSettings = ViewSettings(),
    mediaSettings: ViewSettings = ViewSettings(),
    onPathChange: (String) -> Unit = {},
    onSelectionActiveChanged: (Boolean) -> Unit = {},
    onCanGoUpChanged: (Boolean) -> Unit = {},
    externalBackHandlerActive: Boolean = false,
    onNavigateToViewer: (List<String>, Int) -> Unit = { _, _ -> },
    initialScrollIndex: Int = 0,
    initialScrollOffset: Int = 0,
    onScrollPositionChanged: (Int, Int) -> Unit = { _, _ -> },
    // "In Medien öffnen" - opens the Media tab filtered (recursively, via MediaRepository's
    // directory-prefix LIKE match) to everything under the given folder(s).
    onOpenInMedia: (Set<String>) -> Unit = {},
    onRescan: () -> Unit = {},
    onScanMetadata: () -> Unit = {},
    onOpenSwipeMode: (String, String?) -> Unit = { _, _ -> },
    onOpenPersonReview: (String) -> Unit = {},
    onOpenViewSettings: (() -> Unit)? = null,
    onTagClick: (String) -> Unit = {},
    tabIndex: Int? = null,
    onMediaColumnCountChange: ((Int) -> Unit)? = null,
    onFolderColumnCountChange: ((Int) -> Unit)? = null,
) {
    val context = LocalContext.current
    val repo = LocalMediaRepository.current
    val scope = rememberCoroutineScope()
    val storageRoot = remember { android.os.Environment.getExternalStorageDirectory().absolutePath }
    // rememberSaveable (not remember) - the Home destination's whole composition (MainScreen ->
    // ... -> ExplorerScreen) is disposed and recreated by Navigation-Compose when navigating to
    // the Viewer and back, since Viewer is a separate NavHost destination pushed on top. Plain
    // remember state doesn't survive that dispose/recompose cycle and silently resets to a
    // 1-entry stack (even though the visible path, driven by the ViewModel-backed
    // internalStoragePath param, still correctly shows the deep folder) - which made the back
    // gesture immediately fall through to the tab-switch fallback instead of going up a level,
    // any time after having viewed at least one image while browsing a subfolder.
    val navStack = rememberSaveable(saver = listSaver<SnapshotStateList<String>, String>(save = { it.toList() }, restore = { it.toMutableStateList() })) {
        // If the Explorer is recreated after a tab switch, only the last path may be available to
        // the saver. Rebuild its parents immediately so the first Back press still means "one
        // directory up" instead of falling through to MainScreen's tab-switch handler.
        explorerAncestorChain(storageRoot, internalStoragePath).toMutableStateList()
    }
    // Seed straight from the process cache so a Viewer round-trip (which disposes and recreates this
    // whole screen) paints the folder immediately instead of reloading with a skeleton. navStack
    // survives the recreate, so its top is the folder we're returning to; currentPath starts there
    // too (rather than the root) so nothing loads the wrong path first.
    val normalizedNameFilter = nameFilter.trim()
    val isNameFiltering = normalizedNameFilter.isNotEmpty()
    val needsTags = isNameFiltering || filterState.tagNames != null || mediaSettings.groupBy == org.fossify.gallery.helpers.GroupBy.TAG
    val seedPath = navStack.lastOrNull() ?: internalStoragePath
    val seed = remember(seedPath, folderSettings.sortBy, folderSettings.sortDesc, mediaSettings.sortBy, mediaSettings.sortDesc, mediaSettings.groupBy, needsTags) {
        ExplorerContentCache.get("$seedPath|${folderSettings.sortBy}|${folderSettings.sortDesc}|${mediaSettings.sortBy}|${mediaSettings.sortDesc}|${mediaSettings.groupBy}|${if (needsTags) "tags" else "no-tags"}")
    }
    var currentPath by remember { mutableStateOf(seedPath) }
    // Tracks which path the currently-shown folderItems/fileItems belong to, so that a silent
    // background refresh (RefreshBus, same currentPath) can update the list in place instead of
    // forcing the full-screen skeleton loader back up.
    var loadedPath by remember { mutableStateOf(if (seed != null) seedPath else null) }

    // Cache is per path AND per sort order, since the cached lists are already sorted; a sort change
    // is simply a different key (miss + recompute) rather than needing an explicit invalidation.
    fun contentCacheKey(path: String) =
        "$path|${folderSettings.sortBy}|${folderSettings.sortDesc}|${mediaSettings.sortBy}|${mediaSettings.sortDesc}|${mediaSettings.groupBy}|${if (needsTags) "tags" else "no-tags"}"

    var firstVisibleMediaPath by remember(currentPath) { mutableStateOf<String?>(null) }
    var folderItems by remember { mutableStateOf(seed?.folders ?: emptyList()) }
    var fileItems by remember { mutableStateOf(seed?.files ?: emptyList()) }
    var fileTagsByPath by remember { mutableStateOf(seed?.tags ?: emptyMap()) }
    val visibleFolderItems = remember(folderItems, normalizedNameFilter) {
        if (!isNameFiltering) folderItems
        else folderItems.filter { explorerNameMatchesFilter(it.name, normalizedNameFilter) }
    }
    var reviewStatusByPath by remember { mutableStateOf<Map<String, MediaReviewStatus?>>(emptyMap()) }
    LaunchedEffect(fileItems, filterState.reviewFilter) {
        reviewStatusByPath = if (filterState.reviewFilter == MediaReviewFilter.UNDECIDED) {
            withContext(Dispatchers.IO) { fileItems.associate { it.path to repo.getReviewStatus(it.path) } }
        } else emptyMap()
    }
    val visibleFileItems = remember(fileItems, fileTagsByPath, normalizedNameFilter, filterState, reviewStatusByPath) {
        fileItems.filter { item ->
            (!isNameFiltering || explorerNameMatchesFilter(item.name, normalizedNameFilter) ||
                fileTagsByPath[item.path].orEmpty().any { tag -> explorerNameMatchesFilter(tag, normalizedNameFilter) }) &&
                (!filterState.isActive || item.matchesLibraryFilter(
                    filterState,
                    fileTagsByPath[item.path].orEmpty().toSet(),
                    reviewStatusByPath[item.path],
                ))
        }
    }
    var collapsedGroupKeys by rememberSaveable { mutableStateOf(setOf<String>()) }
    var isLoading by remember { mutableStateOf(seed == null) }
    var isRefreshing by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }

    /** Changes folders as one prepared state transition; see the scroll-state rationale below. */
    fun navigateToPath(path: String) {
        val destination = path.trimEnd('/')
        if (destination == currentPath) return
        ExplorerContentCache.get(contentCacheKey(destination))?.let { cached ->
            folderItems = cached.folders
            fileItems = cached.files
            fileTagsByPath = cached.tags
            loadedPath = destination
            loadError = null
            isLoading = false
            isRefreshing = false
        } ?: run {
            loadedPath = null
            folderItems = emptyList()
            fileItems = emptyList()
            fileTagsByPath = emptyMap()
            isLoading = true
            loadError = null
        }
        currentPath = destination
    }
    var selectedFolderPaths by rememberSaveable(stateSaver = stringSetSaver) { mutableStateOf<Set<String>>(emptySet()) }
    val hasFolderSelection = selectedFolderPaths.isNotEmpty()
    // Mirrors selectedFolderPaths, but for the file grid below - previously that grid had no
    // long-press/selection support at all (plain .clickable straight to the Viewer), unlike every
    // other media grid in the app (MediaScreen/AlbumsScreen/FavoritesScreen). Kept mutually exclusive
    // with folder selection (only one selection mode active at a time) to avoid an ambiguous top bar.
    var selectedFilePaths by rememberSaveable(stateSaver = stringSetSaver) { mutableStateOf<Set<String>>(emptySet()) }
    val hasFileSelection = selectedFilePaths.isNotEmpty()
    var fileRangeAnchorPath by rememberSaveable { mutableStateOf<String?>(null) }
    var fileSelectionMode by rememberSaveable { mutableStateOf(MediaSelectionMode.INDIVIDUAL) }
    val peekState = rememberPeekState()
    LaunchedEffect(hasFileSelection) {
        if (!hasFileSelection) {
            fileSelectionMode = MediaSelectionMode.INDIVIDUAL
            fileRangeAnchorPath = null
        }
    }
    // Selection is deliberately scoped to the content the user is looking at. Carrying paths into
    // another folder, query or filter makes a later Move/Delete appear to affect fewer items than it
    // actually does.
    LaunchedEffect(currentPath, normalizedNameFilter, filterState, mediaSettings.sortBy, mediaSettings.sortDesc, mediaSettings.groupBy) {
        selectedFolderPaths = emptySet()
        selectedFilePaths = emptySet()
    }
    LaunchedEffect(hasFolderSelection, hasFileSelection) { onSelectionActiveChanged(hasFolderSelection || hasFileSelection) }
    var showMultiSelectHint by remember { mutableStateOf(!context.config.hasSeenMultiSelectHint) }
    if (hasFolderSelection || hasFileSelection) {
        LaunchedEffect(Unit) { showMultiSelectHint = false; context.config.hasSeenMultiSelectHint = true }
    }
    var showFileFolderPicker by remember { mutableStateOf(false) }
    var fileFolderPickerIsMove by remember { mutableStateOf(false) }
    var showFileDeleteConfirm by remember { mutableStateOf(false) }
    var showFileRenameDialog by remember { mutableStateOf(false) }
    var fileInfoPath by remember { mutableStateOf<String?>(null) }
    var renameFolderPath by remember { mutableStateOf<String?>(null) }
    // Rate/Tag state for the file selection toolbar - mirrors MediaViewModel's equivalent state
    // (loadCommonTags/loadAllTags/setRatingFor/addTagFor/removeTagFor) but calls MediaRepository
    // directly since Explorer has no MediaViewModel of its own.
    var showFileRateTagSheet by remember { mutableStateOf(false) }
    var fileCurrentRating by remember { mutableIntStateOf(0) }
    var fileSelectedCommonTags by remember { mutableStateOf<Set<String>>(emptySet()) }
    var fileMixedTagCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var fileRatingIsMixed by remember { mutableStateOf(false) }
    LaunchedEffect(selectedFilePaths) {
        if (selectedFilePaths.isEmpty()) {
            fileSelectedCommonTags = emptySet()
            fileMixedTagCounts = emptyMap()
            fileCurrentRating = 0
            fileRatingIsMixed = false
        } else {
            withContext(Dispatchers.IO) {
                val tagsByPath = repo.getTagsForPaths(selectedFilePaths)
                fileSelectedCommonTags = tagsByPath.values.reduceOrNull { a, b -> a intersect b } ?: emptySet()
                fileMixedTagCounts = tagsByPath.values.flatten().groupingBy { it }.eachCount()
                val ratings = repo.getRatingsForPaths(selectedFilePaths).values.distinct()
                fileCurrentRating = ratings.singleOrNull() ?: 0
                fileRatingIsMixed = ratings.size > 1
            }
        }
    }
    var showDeleteFoldersConfirm by remember { mutableStateOf(false) }
    // Set once "Use as mover source" is tapped (can be several folders at once) - the destination
    // picker (search + Explorer browse, see FolderPathPickerSheet) then decides the pair(s)' shared
    // other half, and one FolderPair per source gets created against it.
    var pendingMoverSources by remember { mutableStateOf<List<String>?>(null) }
    // Re-read every time a selection session starts/ends (not just once for the whole tab visit,
    // and not left stale from the previous session) - mirrors FolderPathPickerSheet's own "already a
    // mover source" marker (same tertiary-tinted DriveFileMove icon), just applied to the selection
    // toolbar's action icon instead of a per-row badge.
    val moverSourcePaths = remember(hasFolderSelection) { org.fossify.gallery.helpers.loadMoverPairs(context).map { it.source }.toSet() }
    // Include the ViewModel path as well: on a cold start it can be restored one composition pass
    // after this screen is created. That keeps the parent BackHandler enabled during that handoff.
    val canGoUp = navStack.size > 1 ||
        explorerAncestorChain(storageRoot, currentPath).size > 1 ||
        explorerAncestorChain(storageRoot, internalStoragePath).size > 1

    fun goUpOneLevel() {
        if (navStack.size > 1) {
            navStack.removeLastOrNull()
        } else {
            // Repair a restored one-entry stack synchronously before popping. This is the exact
            // case that previously made MainScreen believe Explorer had no parent yet.
            val currentChain = explorerAncestorChain(storageRoot, currentPath)
            val chain = if (currentChain.size > 1) currentChain
            else explorerAncestorChain(storageRoot, internalStoragePath)
            if (chain.size > 1) {
                navStack.clear()
                navStack.addAll(chain.dropLast(1))
            }
        }
        navigateToPath(navStack.lastOrNull() ?: internalStoragePath)
    }

    // SideEffect (synchronous, runs on the same composition pass), not LaunchedEffect (dispatches
    // a new coroutine, which can lag a frame or more behind) - MainScreen's own BackHandler reads
    // this via onCanGoUpChanged to decide whether it or ExplorerScreen's BackHandler below should
    // win, so any lag here reopens the race where a back-press right after navigating into a
    // folder sees stale (false) state and incorrectly falls through to the tab-switch behavior.
    SideEffect { onCanGoUpChanged(canGoUp) }

    val explorerBackState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
    val canPredictivelyGoUp = canGoUp && !externalBackHandlerActive && !hasFolderSelection && !hasFileSelection && !isNameFiltering
    val backGestureProgress = if (canPredictivelyGoUp) {
        (explorerBackState.transitionState as? NavigationEventTransitionState.InProgress)?.latestEvent?.progress ?: 0f
    } else {
        0f
    }
    NavigationBackHandler(
        state = explorerBackState,
        isBackEnabled = hasFileSelection || hasFolderSelection || isNameFiltering || (canGoUp && !externalBackHandlerActive),
        onBackCompleted = {
            when {
                hasFileSelection -> {
                    selectedFilePaths = emptySet()
                    fileRangeAnchorPath = null
                }
                hasFolderSelection -> selectedFolderPaths = emptySet()
                isNameFiltering -> onClearNameFilter()
                canGoUp && !externalBackHandlerActive -> goUpOneLevel()
            }
        },
    )

    // Re-tapping the already-selected Explorer bottom-nav tab jumps back to the Explorer's start
    // point (the bottom of the nav stack = the storage root, or the search-entry base) instead of
    // just scrolling. Kept at this top level - not folded into the content branch's scroll-to-top
    // effect below - so it still fires while a folder is loading or empty. When already at the start
    // (navStack.size == 1) this is a no-op and the inner effect handles the plain scroll-to-top.
    ScrollToTopEffect(tabIndex) {
        if (navStack.size > 1) {
            val base = navStack.first()
            navStack.clear()
            navStack.add(base)
            navigateToPath(base)
        }
    }

    LaunchedEffect(internalStoragePath) {
        if (internalStoragePath != currentPath) {
            // Entering a deep folder from outside the Explorer (a search result opens it directly, or
            // "open in Explorer" jumps here): seed the whole storage-root -> target ancestor chain,
            // not just [target]. Otherwise the freshly-entered folder is the only stack entry, so the
            // first Back / the up-button has nothing to pop to (navStack.size == 1 disables both this
            // screen's up-BackHandler and hides the breadcrumb up arrow) and the press falls straight
            // through to the activity's tab-switch fallback, dropping the user onto the Albums tab
            // instead of walking up one directory level the way manual browsing would.
            navStack.clear()
            navStack.addAll(explorerAncestorChain(storageRoot, internalStoragePath))
            navigateToPath(internalStoragePath)
        }
    }

    val folderCardColor = when (folderSettings.displayMode) {
        // Not colorScheme.surface - this app's custom ColorScheme sets background == surface, so a
        // folder tile with no thumbnail (empty folder) would be visually indistinguishable from the
        // screen behind it, leaving no indication a tappable card is even there.
        DisplayMode.COMPACT, DisplayMode.NORMAL -> MaterialTheme.colorScheme.surfaceContainerHigh
        DisplayMode.DARK -> MaterialTheme.colorScheme.surfaceVariant
    }

    // Query MediaStore once for every entry under the storage root, then derive each folder view by
    // filtering this cache in memory. Avoids a slow MediaStore round-trip on every navigation.
    // Seed from the process-level cache (see MediaStoreOps) instead of always starting at null - a
    // Viewer round-trip disposes and recreates this whole composable, and without this the cache
    // reset forced a full-device MediaStore re-query every time the user came back from viewing an
    // image.
    var allEntries by remember { mutableStateOf(org.fossify.gallery.helpers.MediaStoreOps.cachedEntriesUnder(storageRoot)) }
    val entriesRefreshMutex = remember { kotlinx.coroutines.sync.Mutex() }
    LaunchedEffect(Unit) {
        if (allEntries == null) {
            entriesRefreshMutex.withLock {
                if (allEntries == null) {
                    allEntries = withContext(Dispatchers.IO) { org.fossify.gallery.helpers.MediaStoreOps.refreshEntriesFromDb(context, storageRoot) }
                }
            }
        }
    }
    // Patch after a known sync/move/delete instead of re-reading every live media row. Unknown bulk
    // invalidations still use the full fallback because there is no safe set of paths to patch.
    // isLoading is intentionally left untouched below so this refresh happens quietly instead of
    // flashing the skeleton loader over content the user is already looking at.
    //
    // RefreshBus already coalesces a burst for every collector. Do not add a second debounce here:
    // Explorer's own one-second window made a newly created or moved file visibly late. collectLatest
    // cancels an obsolete patch/full reload when a newer path generation arrives.
    LaunchedEffect(Unit) {
        org.fossify.gallery.helpers.RefreshBus.pathEvents.collectLatest { affectedPaths ->
            entriesRefreshMutex.withLock {
            if (affectedPaths.isNullOrEmpty()) {
                ExplorerContentCache.clear()
                ExplorerGroupingCache.clear()
                allEntries = withContext(Dispatchers.IO) {
                    org.fossify.gallery.helpers.MediaStoreOps.refreshEntriesFromDb(context, storageRoot)
                }
            } else {
                ExplorerContentCache.invalidatePaths(affectedPaths)
                ExplorerGroupingCache.invalidatePaths(affectedPaths)
                val patched = withContext(Dispatchers.IO) {
                    org.fossify.gallery.helpers.MediaStoreOps.refreshEntriesForPathsFromDb(
                        context = context,
                        rootPath = storageRoot,
                        affectedPaths = affectedPaths,
                    )
                }
                // If the cache was evicted between composition and this event, do the safe full
                // bootstrap once; otherwise preserve the large cached list and patch only touched
                // rows.
                allEntries = patched ?: withContext(Dispatchers.IO) {
                    org.fossify.gallery.helpers.MediaStoreOps.refreshEntriesFromDb(context, storageRoot)
                }
            }
            }
        }
    }

    suspend fun loadFolderContents(path: String) {
        val loaded = loadExplorerFolderContents(
            context = context,
            repo = repo,
            path = path,
            storageRoot = storageRoot,
            allEntries = allEntries,
            folderSettings = folderSettings,
            mediaSettings = mediaSettings,
            needsTags = needsTags,
        )
        folderItems = loaded.folders
        fileTagsByPath = loaded.tags
        fileItems = loaded.files
        ExplorerContentCache.put(contentCacheKey(path), loaded)
    }

    LaunchedEffect(currentPath, allEntries, folderSettings.sortBy, folderSettings.sortDesc, mediaSettings.sortBy, mediaSettings.sortDesc, mediaSettings.groupBy, needsTags) {
        if (currentPath.startsWith(storageRoot) && allEntries == null) { isLoading = true; return@LaunchedEffect }
        // Instant path: a previously-computed view for this folder+sort is served straight from the
        // cache with no skeleton and no recompute - this is what makes coming back from the Viewer
        // (and revisiting a folder) feel immediate instead of flashing the loader. The cache is
        // cleared on every RefreshBus tick (below), so a hit is never stale.
        try {
            val cached = ExplorerContentCache.get(contentCacheKey(currentPath))
            if (cached != null) {
                folderItems = cached.folders
                fileItems = cached.files
                fileTagsByPath = cached.tags
                loadedPath = currentPath
                loadError = null
                isLoading = false
                return@LaunchedEffect
            }
            if (loadedPath != currentPath) isLoading = true
            else isRefreshing = true
            loadFolderContents(currentPath)
            loadedPath = currentPath
            loadError = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            loadError = e.message
        } finally {
            isLoading = false
            isRefreshing = false
        }
    }

    LaunchedEffect(currentPath) { onPathChange(currentPath); selectedFolderPaths = emptySet(); selectedFilePaths = emptySet(); fileRangeAnchorPath = null }

    // Wrapped in a Box (not just the bare Column below) purely so UndoBar can overlay on top of it -
    // Explorer's own move/delete actions already push to the same global UndoManager as
    // Media/Viewer (see onMove/showFileDeleteConfirm/showDeleteFoldersConfirm below), but until now
    // there was nowhere on this screen to actually show the resulting undo bar, so a move or delete
    // made from Explorer had no visible Undo affordance at all even though the underlying mechanism
    // worked - only MediaScreen/ViewerScreen rendered UndoBar.
    Box(modifier = modifier.fillMaxSize()) {
    Column(
        Modifier.fillMaxSize().graphicsLayer {
            // Give an in-folder back swipe a small, direct visual response before the parent path
            // is actually changed on commit. This keeps the gesture readable without adding an
            // extra overlay or altering the drawer's interaction model.
            translationX = backGestureProgress * 28.dp.toPx()
            val scale = 1f - backGestureProgress * 0.02f
            scaleX = scale
            scaleY = scale
        },
    ) {
        if (hasFolderSelection) {
            ExplorerFolderSelectionBar(
                selectedCount = selectedFolderPaths.size,
                visibleFolderCount = visibleFolderItems.size,
                allAlreadyMoverSources = selectedFolderPaths.isNotEmpty() &&
                    selectedFolderPaths.all { it in moverSourcePaths },
                onClose = { selectedFolderPaths = emptySet() },
                onOpenSelection = {
                    selectedFolderPaths.firstOrNull()?.let { p -> navStack.add(p); navigateToPath(p) }
                    selectedFolderPaths = emptySet()
                },
                onHideSelection = {
                    selectedFolderPaths.forEach { p -> context.config.hideExplorer2Folder(p) }
                    scope.launch {
                        try {
                            loadFolderContents(currentPath)
                            loadError = null
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            loadError = e.message
                        }
                    }
                    selectedFolderPaths = emptySet()
                },
                onUseAsMoverSource = {
                    pendingMoverSources = selectedFolderPaths.toList()
                    selectedFolderPaths = emptySet()
                },
                onSelectAll = {
                    val visiblePaths = visibleFolderItems.map { it.path }
                    selectedFolderPaths = selectionForAll(selectedFolderPaths, visiblePaths, invert = false)
                },
                onInvertSelection = {
                    val visiblePaths = visibleFolderItems.map { it.path }
                    selectedFolderPaths = selectionForAll(selectedFolderPaths, visiblePaths, invert = true)
                },
                onOpenInMedia = {
                    onOpenInMedia(selectedFolderPaths)
                    selectedFolderPaths = emptySet()
                },
                onRenameSingle = { renameFolderPath = selectedFolderPaths.first() },
                onDeleteSelection = { showDeleteFoldersConfirm = true },
            )
        } else if (hasFileSelection) {
            SelectionTopAppBar(
                count = selectedFilePaths.size,
                onClose = { selectedFilePaths = emptySet(); fileRangeAnchorPath = null },
                onShare = { (context as? android.app.Activity)?.shareMediaPaths(ArrayList(selectedFilePaths)) },
                onPreview = selectedFilePaths.singleOrNull()?.let { path ->
                    { peekState.show(path, path.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS) }
                },
                onDelete = {
                    if (context.config.skipDeleteConfirmation) {
                        val d = selectedFilePaths
                        context.withRecycleBinDeletePasswordCheck {
                            scope.launch(Dispatchers.IO) { repo.moveToRecycleBinBatch(d) }
                            UndoManager.push(UndoAction(paths = d, type = UndoType.DELETE))
                            selectedFilePaths = emptySet()
                            fileRangeAnchorPath = null
                        }
                    } else {
                        showFileDeleteConfirm = true
                    }
                },
                onSelectAll = {
                    selectedFilePaths = selectionForAll(selectedFilePaths, visibleFileItems.map { it.path }, invert = false)
                    fileRangeAnchorPath = visibleFileItems.firstOrNull()?.path
                },
                onInvert = {
                    selectedFilePaths = selectionForAll(selectedFilePaths, visibleFileItems.map { it.path }, invert = true)
                    fileRangeAnchorPath = selectedFilePaths.firstOrNull()
                },
                onCopy = { fileFolderPickerIsMove = false; showFileFolderPicker = true },
                onMove = { fileFolderPickerIsMove = true; showFileFolderPicker = true },
                onRate = { showFileRateTagSheet = true },
                onTags = { showFileRateTagSheet = true },
                onRename = { showFileRenameDialog = true },
                onInfo = selectedFilePaths.singleOrNull()?.let { path ->
                    { fileInfoPath = path }
                },
                selectionMode = fileSelectionMode,
                onSelectionModeChange = { mode -> fileSelectionMode = mode },
                selectionScopeLabel = if (fileSelectionMode == MediaSelectionMode.RANGE) {
                    fileRangeAnchorPath?.let { stringResource(R.string.selection_range_from, File(it).name) }
                } else {
                    stringResource(R.string.selection_scope_visible, visibleFileItems.size)
                },
                selectAllLabel = stringResource(R.string.action_select_all_visible),
            )
        } else {
            ExplorerBreadcrumbBar(
                currentPath = currentPath,
                storageRoot = storageRoot,
                canGoUp = canGoUp,
                normalizedNameFilter = normalizedNameFilter,
                filterState = filterState,
                filterActions = filterActions,
                visibleFileCount = visibleFileItems.size,
                isRefreshing = isRefreshing,
                onGoUp = { goUpOneLevel() },
                onSelectBreadcrumb = { prefix, targetPath ->
                    navStack.clear()
                    navStack.addAll(prefix)
                    navigateToPath(targetPath)
                },
                onClearNameFilter = onClearNameFilter,
                onOpenViewSettings = onOpenViewSettings,
                onOpenSwipeMode = { onOpenSwipeMode(currentPath, firstVisibleMediaPath) },
                onOpenPersonReview = { onOpenPersonReview(currentPath) },
                onRescan = onRescan,
                onScanMetadata = onScanMetadata,
            )
            org.fossify.gallery.compose.components.LongPressSelectHint(
                visible = showMultiSelectHint && !isNameFiltering,
                onDismiss = {
                    showMultiSelectHint = false
                    context.config.hasSeenMultiSelectHint = true
                },
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        val hasContent = folderItems.isNotEmpty() || fileItems.isNotEmpty()
        if (loadError != null && hasContent && !isLoading) {
            LoadErrorBanner(message = loadError, onRetry = { scope.launch {
                try {
                    loadFolderContents(currentPath)
                    loadError = null
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    loadError = e.message
                }
            } })
        }
        // Crossfade instead of an instant swap - see MediaScreen's paged content for why.
        // A refresh of the current folder must not replace an already-rendered list with a
        // skeleton. A path change is different: old-folder rows must not leak into the new folder
        // while it is loading, so the loadedPath check keeps that transition explicit.
        val hasCurrentContent = hasContent && loadedPath == currentPath
        val explorerContentState = when {
            hasCurrentContent -> "content"
            isLoading -> "loading"
            loadError != null -> "error"
            else -> "empty"
        }
        // Keep the list state outside Crossfade. When it lived inside the target-state lambda,
        // switching content -> loading -> content during a Viewer round-trip created a new
        // LazyListState and the Explorer visibly jumped back to the first row. The ViewModel values
        // provide a second source of truth when the Home destination itself is recreated.
        val cachedScrollPosition = remember(currentPath) { ExplorerScrollCache.get(currentPath) }
        val initialPosition = cachedScrollPosition ?: if (currentPath == internalStoragePath) {
            ExplorerScrollPosition(initialScrollIndex, initialScrollOffset)
        } else {
            ExplorerScrollPosition(0, 0)
        }
        // The path is part of the saveable key. Keeping one LazyListState for every visited
        // directory is what made a parent briefly render at the child's position before the
        // asynchronous restore below corrected it.
        val listState = rememberSaveable(currentPath, saver = LazyListState.Saver) {
            LazyListState(
                initialPosition.index,
                initialPosition.offset,
            )
        }
        Crossfade(targetState = explorerContentState, animationSpec = AppMotion.short, label = "explorerContent") { cs ->
        if (cs == "loading") {
            Box(Modifier.fillMaxSize()) {
                MediaSkeleton(columns = 3)
                // The first Explorer visit runs an uncached full-device MediaStore scan (multiple
                // seconds on a large real library, see MediaStoreOps.kt) with nothing but a bare
                // shimmer to look at - easy to mistake for a hang. Delayed so it doesn't flash on
                // the common fast path (cached allEntries, near-instant folder navigation).
                var showHint by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { delay(600); showHint = true }
                androidx.compose.animation.AnimatedVisibility(visible = showHint, modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp), enter = fadeIn(), exit = fadeOut()) {
                    Surface(shape = RoundedCornerShape(Radius.xl), color = MaterialTheme.colorScheme.surfaceContainerHigh, shadowElevation = 2.dp) {
                        Text(
                            stringResource(R.string.scanning_folders), style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            // A screen-reader user gets nothing but a bare shimmer otherwise - a new
                            // Text node appearing off-focus isn't announced by TalkBack on its own.
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                }
            }
        } else if (cs == "error") {
            LoadErrorState(message = loadError, onRetry = { scope.launch {
                try {
                    loadFolderContents(currentPath)
                    loadError = null
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    loadError = e.message
                }
            } })
        } else if (cs == "empty") {
            val isStorageRoot = currentPath == storageRoot
            EmptyState(
                Icons.Default.Folder,
                title = stringResource(if (isStorageRoot) R.string.no_media_found else R.string.no_items),
                subtitle = stringResource(if (isStorageRoot) R.string.explorer_empty_root_hint else R.string.no_items_hint),
                actionLabel = if (isStorageRoot) stringResource(R.string.rescan) else null,
                onAction = if (isStorageRoot) onRescan else null,
            )
        } else {
            ScrollToTopEffect(tabIndex) { listState.animateScrollToItem(0) }
            val untaggedLabel = stringResource(R.string.group_untagged)
            val unratedLabel = stringResource(R.string.group_unrated)
            // Filtering changes the visible media, but it must not disable the group interaction.
            // Previously a name/tag filter forced every group open and the headers ignored taps,
            // making tag groups in a filtered folder appear permanently expanded.
            val effectiveCollapsedGroupKeys = collapsedGroupKeys
            // The path->tile map and the full grouping model can both be six figures. Build them
            // off the main thread so changing sort/group settings never blocks the first frame of
            // the Explorer content. Re-visiting a folder used to rebuild this complete model after
            // the folder itself was already restored from cache, briefly removing its media rows
            // and then inserting them again. Keep a small matching grouping cache alongside the
            // folder cache so a back-navigation paints one coherent first frame.
            val groupingCacheKey = remember(
                currentPath,
                normalizedNameFilter,
                filterState,
                visibleFileItems,
                reviewStatusByPath,
                fileTagsByPath,
                mediaSettings.sortBy,
                mediaSettings.sortDesc,
                mediaSettings.groupBy,
                mediaSettings.groupOrder,
                mediaSettings.onlyTopLevelTags,
            ) {
                ExplorerGroupingCacheKey(
                    path = currentPath,
                    nameFilter = normalizedNameFilter,
                    filter = filterState,
                    filesHash = visibleFileItems.hashCode(),
                    reviewStatusHash = reviewStatusByPath.hashCode(),
                    tagsHash = fileTagsByPath.hashCode(),
                    sortBy = mediaSettings.sortBy,
                    sortDescending = mediaSettings.sortDesc,
                    groupBy = mediaSettings.groupBy,
                    groupOrder = mediaSettings.groupOrder,
                    onlyTopLevelTags = mediaSettings.onlyTopLevelTags,
                )
            }
            val cachedGrouping = remember(groupingCacheKey) { ExplorerGroupingCache.get(groupingCacheKey) }
            var fileItemByPath by remember(groupingCacheKey) {
                mutableStateOf(cachedGrouping?.itemsByPath.orEmpty())
            }
            var completeFileGroupRows by remember(groupingCacheKey) {
                mutableStateOf(cachedGrouping?.rows.orEmpty())
            }
            LaunchedEffect(groupingCacheKey, loadedPath, visibleFileItems, fileTagsByPath, untaggedLabel, unratedLabel) {
                if (loadedPath != currentPath) return@LaunchedEffect
                ExplorerGroupingCache.get(groupingCacheKey)?.let { cached ->
                    fileItemByPath = cached.itemsByPath
                    completeFileGroupRows = cached.rows
                    return@LaunchedEffect
                }
                val (nextMap, nextRows) = withContext(Dispatchers.Default) {
                    buildExplorerFileGroupRows(
                        visibleFileItems = visibleFileItems,
                        fileTagsByPath = fileTagsByPath,
                        tagHierarchy = context.config.tagHierarchy,
                        mediaSettings = mediaSettings,
                        untaggedLabel = untaggedLabel,
                        unratedLabel = unratedLabel,
                    )
                }
                if (loadedPath != currentPath) return@LaunchedEffect
                fileItemByPath = nextMap
                completeFileGroupRows = nextRows
                // Group rows may reference media more than once for tag grouping. The companion
                // cache is tightly bounded, so a few return navigations are instant without
                // retaining an unbounded slice of a very large library in memory.
                ExplorerGroupingCache.put(groupingCacheKey, ExplorerGroupingSnapshot(nextMap, nextRows))
            }

            val folderGridColumns = org.fossify.gallery.compose.util.rememberEffectiveColumnCount(folderSettings.columnCount)
            val mediaGridColumns = org.fossify.gallery.compose.util.rememberEffectiveColumnCount(mediaSettings.columnCount)
            val fileGroupRows = remember(completeFileGroupRows, effectiveCollapsedGroupKeys) {
                applyCollapsedGroups(completeFileGroupRows, effectiveCollapsedGroupKeys)
            }
            val collapsibleFileGroupKeys = remember(fileGroupRows) { org.fossify.gallery.helpers.collapsibleGroupKeys(fileGroupRows) }
            val allFileGroupsExpanded = collapsibleFileGroupKeys.isNotEmpty() && collapsibleFileGroupKeys.none { it in collapsedGroupKeys }
            val scrollKeyToPath = remember(
                visibleFolderItems,
                visibleFileItems,
                fileGroupRows,
                fileItemByPath,
                folderSettings.viewType,
                mediaSettings.viewType,
                folderGridColumns,
                mediaGridColumns,
            ) {
                buildExplorerScrollIndexMaps(
                    visibleFolderItems = visibleFolderItems,
                    visibleFileItems = visibleFileItems,
                    fileGroupRows = fileGroupRows,
                    fileItemByPath = fileItemByPath,
                    folderSettings = folderSettings,
                    mediaSettings = mediaSettings,
                    folderGridColumns = folderGridColumns,
                    mediaGridColumns = mediaGridColumns,
                )
            }
            val scrollKeyToPathMap = scrollKeyToPath.first
            val scrollIndexByPath = scrollKeyToPath.second
            var unfilteredScrollBeforeNameFilter by remember(currentPath) { mutableStateOf<ExplorerScrollPosition?>(null) }
            var previousNameFilter by remember(currentPath) { mutableStateOf(normalizedNameFilter) }
            LaunchedEffect(normalizedNameFilter, listState) {
                val previous = previousNameFilter
                if (previous == normalizedNameFilter) return@LaunchedEffect
                previousNameFilter = normalizedNameFilter
                if (previous.isEmpty() && normalizedNameFilter.isNotEmpty()) {
                    // Prefer the last unfiltered semantic anchor. The newly filtered list can clamp
                    // LazyListState before this effect runs, especially when filtering from deep in
                    // a very large folder.
                    unfilteredScrollBeforeNameFilter = ExplorerScrollCache.get(currentPath)
                        ?: ExplorerScrollPosition(
                            listState.firstVisibleItemIndex,
                            listState.firstVisibleItemScrollOffset,
                        )
                }
                if (normalizedNameFilter.isNotEmpty()) {
                    // Every changed query starts at the first match; retaining a deep index from the
                    // unfiltered folder can otherwise make a short result set appear blank.
                    snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > 0 }
                    listState.scrollToItem(0)
                } else {
                    snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > 0 }
                    val restored = unfilteredScrollBeforeNameFilter ?: ExplorerScrollCache.get(currentPath)
                    if (restored != null) {
                        val lastIndex = listState.layoutInfo.totalItemsCount - 1
                        val targetIndex = restored.anchorPath.takeIf { it.isNotBlank() }?.let(scrollIndexByPath::get)
                            ?: restored.index
                        listState.scrollToItem(targetIndex.coerceIn(0, lastIndex), restored.offset)
                    }
                    unfilteredScrollBeforeNameFilter = null
                }
            }

            // Do not restore or persist while the list is loading. LazyColumn reports (0, 0)
            // before its first real item is laid out; persisting that transient value used to
            // overwrite the position immediately before returning from Viewer. For folders with
            // media, wait until the grouping model is ready as well, otherwise the initial short
            // list (folders + section headers only) clamps the saved index to the top.
            var restoredScrollPath by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(currentPath, explorerContentState, visibleFileItems, fileGroupRows, listState, isNameFiltering) {
                if (explorerContentState != "content" || restoredScrollPath == currentPath) return@LaunchedEffect
                if (isNameFiltering) {
                    // The saveable LazyListState already owns the filtered position across a Viewer
                    // round-trip. Restoring the unfiltered path cache here would jump into an
                    // unrelated filtered row and could be outside the shorter result list.
                    restoredScrollPath = currentPath
                    return@LaunchedEffect
                }
                if (visibleFileItems.isNotEmpty() && fileGroupRows.isEmpty()) return@LaunchedEffect
                snapshotFlow { listState.layoutInfo.totalItemsCount }
                    .first { it > 0 }
                val totalItems = listState.layoutInfo.totalItemsCount
                val restored = ExplorerScrollCache.get(currentPath)
                if (restored != null && totalItems > 0) {
                    val targetIndex = restored.anchorPath.takeIf { it.isNotBlank() }?.let(scrollIndexByPath::get) ?: restored.index
                    val boundedIndex = targetIndex.coerceIn(0, totalItems - 1)
                    // The keyed LazyListState already starts here on an ordinary back-navigation.
                    // Avoid issuing an equivalent post-layout scroll command: even a no-op command
                    // can briefly invalidate placement and reads as a visible jump on a dense grid.
                    if (listState.firstVisibleItemIndex != boundedIndex || listState.firstVisibleItemScrollOffset != restored.offset) {
                        listState.scrollToItem(boundedIndex, restored.offset)
                    }
                }
                restoredScrollPath = currentPath
            }
            LaunchedEffect(listState, currentPath, restoredScrollPath, isNameFiltering) {
                if (restoredScrollPath != currentPath || isNameFiltering) return@LaunchedEffect
                snapshotFlow {
                    val firstVisibleMedia = listState.layoutInfo.visibleItemsInfo
                        .asSequence()
                        .mapNotNull { scrollKeyToPathMap[it.key] }
                        .firstOrNull()
                    Triple(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset, firstVisibleMedia)
                }
                    .filter { listState.layoutInfo.totalItemsCount > 0 }
                    .collect { (index, offset, anchorPath) ->
                        ExplorerScrollCache.put(currentPath, ExplorerScrollPosition(index, offset, anchorPath.orEmpty()))
                        firstVisibleMediaPath = anchorPath
                        onScrollPositionChanged(index, offset)
                    }
            }
            // The range anchor follows the order the user sees, including grouping and collapsed
            // sections. Only visible group items participate; the fallback keeps the first frame
            // usable while the background grouping model is still being built.
            val fileDisplayOrder = remember(fileGroupRows, visibleFileItems) {
                val groupedPaths = fileGroupRows.flatMap { row ->
                    when (row) {
                        is org.fossify.gallery.helpers.GroupRow.Items -> row.media.map { it.path }
                        else -> emptyList()
                    }
                }
                (if (groupedPaths.isNotEmpty()) groupedPaths else visibleFileItems.map { it.path })
                    .distinct()
            }
            fun selectFileForInteraction(path: String, startSelection: Boolean = false) {
                val targetIndex = fileDisplayOrder.indexOf(path)
                val anchorIndex = fileRangeAnchorPath?.let(fileDisplayOrder::indexOf)?.takeIf { it >= 0 }
                selectedFilePaths = when {
                    startSelection -> selectedFilePaths + path
                    fileSelectionMode == MediaSelectionMode.RANGE && anchorIndex != null && targetIndex >= 0 -> {
                        val lo = minOf(anchorIndex, targetIndex)
                        val hi = maxOf(anchorIndex, targetIndex)
                        selectedFilePaths + fileDisplayOrder.subList(lo, hi + 1)
                    }
                    path in selectedFilePaths -> selectedFilePaths - path
                    else -> selectedFilePaths + path
                }
                fileRangeAnchorPath = path
            }
            fun onFileClick(item: ExplorerItem) {
                if (hasFolderSelection) return
                if (hasFileSelection) selectFileForInteraction(item.path)
                else {
                    // Persist synchronously before navigation. The snapshotFlow above normally has
                    // already emitted this position, but a tap can race the final scroll frame.
                    if (!isNameFiltering) {
                        val position = ExplorerScrollPosition(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
                        ExplorerScrollCache.put(currentPath, position)
                        onScrollPositionChanged(position.index, position.offset)
                    }
                    onNavigateToViewer(visibleFileItems.map { it.path }, visibleFileItems.indexOfFirst { it.path == item.path }.coerceAtLeast(0))
                }
            }
            fun onFileLongClick(item: ExplorerItem) {
                if (!hasFolderSelection) {
                    selectedFolderPaths = emptySet()
                    selectFileForInteraction(item.path, startSelection = true)
                }
            }
            ExplorerContentList(
                listState = listState,
                data = ExplorerListContentData(
                    visibleFolderItems = visibleFolderItems,
                    visibleFileItems = visibleFileItems,
                    fileGroupRows = fileGroupRows,
                    fileItemByPath = fileItemByPath,
                    fileTagsByPath = fileTagsByPath,
                    selectedFolderPaths = selectedFolderPaths,
                    selectedFilePaths = selectedFilePaths,
                    hasFolderSelection = hasFolderSelection,
                    hasFileSelection = hasFileSelection,
                    collapsibleFileGroupKeys = collapsibleFileGroupKeys,
                    allFileGroupsExpanded = allFileGroupsExpanded,
                ),
                peekState = peekState,
                config = ExplorerListDisplayConfig(
                    folderSettings = folderSettings,
                    mediaSettings = mediaSettings,
                    folderGridColumns = folderGridColumns,
                    mediaGridColumns = mediaGridColumns,
                    folderCardColor = folderCardColor,
                    isNameFiltering = isNameFiltering,
                    normalizedNameFilter = normalizedNameFilter,
                    filterState = filterState,
                ),
                callbacks = ExplorerListCallbacks(
                    onFolderClick = { item ->
                        if (!hasFileSelection) {
                            if (hasFolderSelection) {
                                selectedFolderPaths = if (item.path in selectedFolderPaths) {
                                    selectedFolderPaths - item.path
                                } else {
                                    selectedFolderPaths + item.path
                                }
                            } else {
                                navStack.add(item.path)
                                navigateToPath(item.path)
                            }
                        }
                    },
                    onFolderLongClick = { item ->
                        if (!hasFileSelection) {
                            selectedFilePaths = emptySet()
                            selectedFolderPaths = selectedFolderPaths + item.path
                        }
                    },
                    onFileClick = ::onFileClick,
                    onFileLongClick = ::onFileLongClick,
                    onTagClick = onTagClick,
                    onToggleGroup = { key ->
                        collapsedGroupKeys = org.fossify.gallery.helpers.toggleGroupKey(collapsedGroupKeys, key)
                    },
                    onToggleAllGroups = {
                        collapsedGroupKeys = org.fossify.gallery.helpers.setGroupsExpanded(
                            collapsedKeys = collapsedGroupKeys,
                            rows = fileGroupRows,
                            expanded = !allFileGroupsExpanded,
                        )
                    },
                    onClearNameFilter = onClearNameFilter,
                    filterActions = filterActions,
                    onMediaColumnCountChange = onMediaColumnCountChange,
                    onFolderColumnCountChange = onFolderColumnCountChange,
                ),
                modifier = Modifier.weight(1f),
            )
        }
        }

    }
    UndoBar(modifier = Modifier.align(Alignment.BottomCenter))
    InlineRefreshIndicator(
        visible = isRefreshing && loadedPath == currentPath,
        contentAvailable = folderItems.isNotEmpty() || fileItems.isNotEmpty(),
        modifier = Modifier.align(Alignment.TopCenter),
    )
    }

    ExplorerDialogsHost(
        dialogsState = ExplorerDialogsState(
            showFileFolderPicker = showFileFolderPicker,
            fileFolderPickerIsMove = fileFolderPickerIsMove,
            fileInfoPath = fileInfoPath,
            showFileRenameDialog = showFileRenameDialog,
            showFileRateTagSheet = showFileRateTagSheet,
            showFileDeleteConfirm = showFileDeleteConfirm,
            renameFolderPath = renameFolderPath,
            showDeleteFoldersConfirm = showDeleteFoldersConfirm,
            pendingMoverSources = pendingMoverSources,
            selectedFilePaths = selectedFilePaths,
            selectedFolderPaths = selectedFolderPaths,
            fileCurrentRating = fileCurrentRating,
            fileRatingIsMixed = fileRatingIsMixed,
            fileSelectedCommonTags = fileSelectedCommonTags,
            fileMixedTagCounts = fileMixedTagCounts,
        ),
        scope = scope,
        onDismissFileFolderPicker = { showFileFolderPicker = false; selectedFilePaths = emptySet() },
        onDismissFileInfo = { fileInfoPath = null },
        onRenameFiles = { mapping ->
            selectedFilePaths = selectedFilePaths.map { mapping[it] ?: it }.toSet()
            fileRangeAnchorPath = fileRangeAnchorPath?.let { mapping[it] ?: it }
        },
        onDismissFileRename = { showFileRenameDialog = false },
        onRateChange = { rating ->
            fileCurrentRating = rating
            fileRatingIsMixed = false
        },
        onDismissRateTag = { showFileRateTagSheet = false },
        onConfirmFileDelete = {
            showFileDeleteConfirm = false
            val d = selectedFilePaths
            context.withRecycleBinDeletePasswordCheck {
                scope.launch(Dispatchers.IO) { repo.moveToRecycleBinBatch(d) }
                UndoManager.push(UndoAction(paths = d, type = UndoType.DELETE))
                selectedFilePaths = emptySet()
            }
        },
        onDismissFileDelete = { showFileDeleteConfirm = false },
        onRenameFolder = { selectedFolderPaths = emptySet() },
        onDismissFolderRename = { renameFolderPath = null },
        onConfirmFolderDelete = {
            showDeleteFoldersConfirm = false
            selectedFolderPaths = emptySet()
        },
        onDismissFolderDelete = { showDeleteFoldersConfirm = false },
        onDismissMoverPicker = { pendingMoverSources = null },
    )
}

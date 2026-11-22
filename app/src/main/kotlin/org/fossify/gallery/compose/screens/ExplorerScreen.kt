package org.fossify.gallery.compose.screens
import org.fossify.gallery.compose.theme.Radius

import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
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
import androidx.compose.foundation.layout.aspectRatio
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
import org.fossify.gallery.compose.theme.BlurRadius
import org.fossify.gallery.compose.theme.LocalMediaRepository
import org.fossify.gallery.compose.theme.Scrim
import org.fossify.gallery.compose.theme.SelectionVisual
import org.fossify.gallery.compose.util.BlurState
import org.fossify.gallery.compose.util.ScrollToTopEffect
import org.fossify.gallery.compose.util.decodeImageAspect
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
    var fileAllTags by remember { mutableStateOf<List<String>>(emptyList()) }
    var fileTagCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
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

    var backGestureProgress by remember { mutableFloatStateOf(0f) }
    PredictiveBackHandler(enabled = canGoUp && !externalBackHandlerActive && !hasFolderSelection && !hasFileSelection) { progress ->
        try {
            progress.collect { backEvent -> backGestureProgress = backEvent.progress }
            backGestureProgress = 0f
            goUpOneLevel()
        } catch (_: CancellationException) {
            // A cancelled edge swipe must leave the current folder exactly where it was.
            backGestureProgress = 0f
        }
    }
    BackHandler(enabled = isNameFiltering && !hasFolderSelection && !hasFileSelection) {
        onClearNameFilter()
    }
    BackHandler(enabled = hasFolderSelection) { selectedFolderPaths = emptySet() }
    BackHandler(enabled = hasFileSelection) { selectedFilePaths = emptySet(); fileRangeAnchorPath = null }

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
            SelectionAppBar(
                count = selectedFolderPaths.size,
                onClose = { selectedFolderPaths = emptySet() },
                subtitle = context.getString(R.string.selection_scope_visible, visibleFolderItems.size),
                actions = {
                    IconButton(onClick = { selectedFolderPaths.firstOrNull()?.let { p -> navStack.add(p); navigateToPath(p) }; selectedFolderPaths = emptySet() }) {
                        Icon(Icons.Default.Folder, stringResource(R.string.action_open))
                    }
                    IconButton(onClick = {
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
                    }) {
                        Icon(Icons.Default.VisibilityOff, stringResource(R.string.action_hide))
                    }
                    val allAlreadyMoverSources = selectedFolderPaths.isNotEmpty() && selectedFolderPaths.all { it in moverSourcePaths }
                    IconButton(onClick = {
                        pendingMoverSources = selectedFolderPaths.toList()
                        selectedFolderPaths = emptySet()
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.DriveFileMove,
                            stringResource(if (allAlreadyMoverSources) R.string.mover_source_marker else R.string.action_use_as_mover_source),
                            // error/red instead of tertiary - tertiary read as too close to the
                            // toolbar's other icons to notice at a glance, and this is a state the
                            // user needs to actually see before tapping (already-configured vs. new).
                            tint = if (allAlreadyMoverSources) MaterialTheme.colorScheme.error else LocalContentColor.current,
                        )
                    }
                    // Rename/Delete tucked behind an overflow menu, mirroring AlbumsScreen's folder
                    // selection bar - keeps this consistent across both folder-selection surfaces.
                    var folderMenuOpen by remember { mutableStateOf(false) }
                    val isSingleFolder = selectedFolderPaths.size == 1
                    Box {
                        IconButton(onClick = { folderMenuOpen = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.more_actions)) }
                        DropdownMenu(expanded = folderMenuOpen, onDismissRequest = { folderMenuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_select_all)) },
                                onClick = { folderMenuOpen = false; selectedFolderPaths = selectionForAll(selectedFolderPaths, visibleFolderItems.map { it.path }, invert = false) },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_invert_selection)) },
                                onClick = { folderMenuOpen = false; selectedFolderPaths = selectionForAll(selectedFolderPaths, visibleFolderItems.map { it.path }, invert = true) },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_open_in_media)) },
                                leadingIcon = { Icon(Icons.Default.PhotoLibrary, null) },
                                onClick = { folderMenuOpen = false; onOpenInMedia(selectedFolderPaths); selectedFolderPaths = emptySet() },
                            )
                            if (isSingleFolder) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_rename)) },
                                    leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, null) },
                                    onClick = { folderMenuOpen = false; renameFolderPath = selectedFolderPaths.first() },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(org.fossify.commons.R.string.delete)) },
                                leadingIcon = { Icon(Icons.Default.Delete, null) },
                                onClick = { folderMenuOpen = false; showDeleteFoldersConfirm = true },
                            )
                        }
                    }
                },
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
                    fileRangeAnchorPath?.let { context.getString(R.string.selection_range_from, File(it).name) }
                } else {
                    context.getString(R.string.selection_scope_visible, visibleFileItems.size)
                },
                selectAllLabel = context.getString(R.string.action_select_all_visible),
            )
        } else {
            // Breadcrumb navigation bar
            val breadcrumbChain = remember(currentPath, storageRoot) {
                explorerAncestorChain(storageRoot, currentPath)
            }
            var breadcrumbsExpanded by remember(currentPath) { mutableStateOf(false) }
            val expandPathDescription = stringResource(R.string.cd_expand_path)
            val visibleBreadcrumbIndices = if (breadcrumbsExpanded || breadcrumbChain.size <= 3) {
                breadcrumbChain.indices.toList()
            } else {
                listOf(0, -1, breadcrumbChain.lastIndex - 1, breadcrumbChain.lastIndex)
            }
            val breadcrumbScrollState = rememberScrollState()
            LaunchedEffect(currentPath) {
                // The storage root has just one breadcrumb. Scrolling it to the end clipped
                // the beginning of “Interner Speicher” (shown as “peicher”). Only nested
                // paths need to reveal their right-most, current-folder segment.
                if (breadcrumbChain.size > 1) {
                    breadcrumbScrollState.animateScrollTo(breadcrumbScrollState.maxValue)
                } else {
                    breadcrumbScrollState.scrollTo(0)
                }
            }
            var showExplorerQuickActions by remember(currentPath) { mutableStateOf(false) }
            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)).padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (canGoUp) {
                    IconButton(onClick = { goUpOneLevel() }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    }
                } else {
                    Spacer(Modifier.size(48.dp))
                }
                Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(breadcrumbScrollState),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        visibleBreadcrumbIndices.forEachIndexed { visibleIndex, breadcrumbIndex ->
                            if (visibleIndex > 0) {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            if (breadcrumbIndex == -1) {
                                TextButton(
                                    onClick = { breadcrumbsExpanded = true },
                                    modifier = Modifier.heightIn(min = 48.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                ) {
                                    Text("…", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.semantics { contentDescription = expandPathDescription })
                                }
                            } else {
                                val breadcrumbPath = breadcrumbChain[breadcrumbIndex]
                                val label = if (breadcrumbPath == storageRoot) stringResource(R.string.internal_storage) else File(breadcrumbPath).name
                                TextButton(
                                    onClick = {
                                        navStack.clear()
                                        navStack.addAll(breadcrumbChain.take(breadcrumbIndex + 1))
                                        navigateToPath(breadcrumbPath)
                                    },
                                    modifier = Modifier.heightIn(min = 48.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                ) {
                                    Text(
                                        label,
                                        style = if (breadcrumbPath == currentPath) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelLarge,
                                        fontWeight = if (breadcrumbPath == currentPath) FontWeight.Medium else FontWeight.Normal,
                                        color = if (breadcrumbPath == currentPath) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                    if (normalizedNameFilter.isNotBlank()) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(top = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ActiveFilterChip(
                                label = stringResource(R.string.filter_search_query, normalizedNameFilter),
                                onRemove = onClearNameFilter,
                            )
                        }
                    }
                    LibraryFilterSummary(
                        filter = filterState,
                        resultCount = visibleFileItems.size,
                        actions = filterActions,
                    )
                }
                if (onOpenViewSettings != null) {
                    IconButton(
                        onClick = onOpenViewSettings,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            Icons.Default.Tune,
                            contentDescription = stringResource(R.string.view_settings_title),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Box {
                    IconButton(
                        onClick = { showExplorerQuickActions = true },
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.more_actions),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DropdownMenu(
                        expanded = showExplorerQuickActions,
                        onDismissRequest = { showExplorerQuickActions = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.swipe_mode_open)) },
                            leadingIcon = { Icon(Icons.Default.TouchApp, contentDescription = null) },
                            onClick = {
                                showExplorerQuickActions = false
                                onOpenSwipeMode(currentPath, firstVisibleMediaPath)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.person_scan_open)) },
                            leadingIcon = { Icon(Icons.Default.PersonSearch, contentDescription = null) },
                            onClick = {
                                showExplorerQuickActions = false
                                onOpenPersonReview(currentPath)
                            },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_refresh)) },
                            leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                            enabled = !isRefreshing,
                            onClick = {
                                showExplorerQuickActions = false
                                onRescan()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_scan_metadata)) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null) },
                            onClick = {
                                showExplorerQuickActions = false
                                onScanMetadata()
                            },
                        )
                    }
                }
            }
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
                    val nextMap = visibleFileItems.associateBy { it.path }
                    val fileMedia = visibleFileItems.map { it.toMediumForGrouping() }
                    val nextRows = when (mediaSettings.groupBy) {
                        org.fossify.gallery.helpers.GroupBy.TAG -> org.fossify.gallery.helpers.buildTagGroupRows(fileMedia, fileTagsByPath, context.config.tagHierarchy, mediaSettings.groupOrder, mediaSettings.onlyTopLevelTags, emptySet(), untaggedLabel)
                        org.fossify.gallery.helpers.GroupBy.RATING -> org.fossify.gallery.helpers.buildRatingGroupRows(fileMedia, mediaSettings.groupOrder, unratedLabel)
                        org.fossify.gallery.helpers.GroupBy.SIZE -> org.fossify.gallery.helpers.buildSizeGroupRows(fileMedia, mediaSettings.sortDesc)
                        org.fossify.gallery.helpers.GroupBy.ALPHABET -> org.fossify.gallery.helpers.buildAlphabetGroupRows(fileMedia, mediaSettings.sortDesc)
                        org.fossify.gallery.helpers.GroupBy.MONTH -> {
                            val g = LinkedHashMap<String, MutableList<org.fossify.gallery.models.Medium>>()
                            fileMedia.forEach { m -> g.getOrPut(org.fossify.gallery.viewmodels.MediaViewModel.monthLabelFor(m)) { mutableListOf() }.add(m) }
                            g.flatMap { (label, items) ->
                                val key = "month:$label"
                                listOf(org.fossify.gallery.helpers.GroupRow.SectionHeader(key, label, 0, items.size, items.size, false, true), org.fossify.gallery.helpers.GroupRow.Items(key, items))
                            }
                        }
                        org.fossify.gallery.helpers.GroupBy.NONE -> if (fileMedia.isEmpty()) emptyList() else listOf(org.fossify.gallery.helpers.GroupRow.Items("all", fileMedia))
                    }
                    nextMap to nextRows
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
            // Compose's lazy index is only a positional detail. Build the same item-key map the
            // list uses so a folder can be restored by the first visible file after a sync inserts
            // or removes rows above it. Grid/mosaic rows use their first media path as the semantic
            // anchor; the path itself remains stable even though the row index may change.
            val scrollKeyToPath = remember(visibleFolderItems, visibleFileItems, fileGroupRows, folderSettings.viewType, mediaSettings.viewType, mediaGridColumns) {
                val keyToPath = LinkedHashMap<String, String>()
                val indexByPath = LinkedHashMap<String, Int>()
                var lazyIndex = 0
                fun addRow(key: String, paths: List<String>) {
                    paths.firstOrNull()?.let { first ->
                        keyToPath[key] = first
                        paths.forEach { path -> indexByPath.putIfAbsent(path, lazyIndex) }
                    }
                    lazyIndex++
                }
                if (visibleFolderItems.isNotEmpty()) {
                    lazyIndex++ // Folders section header
                    if (folderSettings.viewType == ViewType.GRID) {
                        visibleFolderItems.chunked(folderGridColumns).forEachIndexed { rowIndex, chunk ->
                            addRow("folderrow_$rowIndex", chunk.map { it.path })
                        }
                    } else {
                        visibleFolderItems.chunked(1).forEach { chunk ->
                            addRow("folderrow_" + chunk.joinToString { it.path }, chunk.map { it.path })
                        }
                    }
                }
                if (visibleFileItems.isNotEmpty()) {
                    lazyIndex++ // Media section header
                    fileGroupRows.forEach { row ->
                        when (row) {
                            is org.fossify.gallery.helpers.GroupRow.SectionHeader -> lazyIndex++
                            is org.fossify.gallery.helpers.GroupRow.Items -> {
                                val paths = row.media.mapNotNull { fileItemByPath[it.path]?.path }
                                when (mediaSettings.viewType) {
                                    ViewType.GRID -> paths.chunked(mediaGridColumns).forEachIndexed { rowIndex, chunk ->
                                        addRow("${row.sectionKey}/gridrow_$rowIndex", chunk)
                                    }
                                    ViewType.MOSAIC -> paths.chunked(EXPLORER_MOSAIC_CHUNK_SIZE).forEachIndexed { chunkIndex, chunk ->
                                        addRow("explorer_media_mosaic_${row.sectionKey}_$chunkIndex", chunk)
                                    }
                                    else -> paths.forEach { path -> addRow("${row.sectionKey}/$path", listOf(path)) }
                                }
                            }
                        }
                    }
                }
                keyToPath to indexByPath
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
            val explorerWindowWidthDp = with(androidx.compose.ui.platform.LocalDensity.current) {
                androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.width.toDp()
            }
            val folderTileWidthDp = explorerWindowWidthDp / folderGridColumns.coerceAtLeast(1)
            val isNarrowFolderTile = folderTileWidthDp < 88.dp
            val isUltraNarrowFolderTile = folderTileWidthDp < 84.dp
            Box(Modifier.weight(1f)) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(4.dp)) {
                if (visibleFolderItems.isNotEmpty()) {
                    item(key = "explorer_folders_header", contentType = "section_header") {
                        ExplorerSectionHeader(stringResource(R.string.folders), visibleFolderItems.size)
                    }
                    if (folderSettings.viewType == ViewType.GRID) {
                        visibleFolderItems.chunked(folderGridColumns).forEachIndexed { rowIndex, chunk ->
                            // A content-derived key changed for every later row when a new folder
                            // was inserted at the top. Keep row identity positional and let each
                            // FolderTile key its image model by path instead.
                            item(key = "folderrow_$rowIndex", contentType = "folder_grid_row") {
                                Row(Modifier.fillMaxWidth().padding(folderSettings.spacing.dp / 2)) {
                                    chunk.forEach { item ->
                                        Box(Modifier.weight(1f).padding(folderSettings.spacing.dp / 2).combinedClickable(
                                            onClick = { if (hasFileSelection) Unit else if (hasFolderSelection) selectedFolderPaths = if (item.path in selectedFolderPaths) selectedFolderPaths - item.path else selectedFolderPaths + item.path else { navStack.add(item.path); navigateToPath(item.path) } },
                                            onLongClick = { if (!hasFileSelection) { selectedFilePaths = emptySet(); selectedFolderPaths = selectedFolderPaths + item.path } }
                                        ).semantics(mergeDescendants = true) {
                                            contentDescription = buildString {
                                                append(item.name)
                                                if (item.mediaCount > 0) append(", ").append(item.mediaCount)
                                            }
                                            role = Role.Button
                                            selected = item.path in selectedFolderPaths
                                        }) {
                                            FolderTile(
                                                name = item.name,
                                                thumbnailPath = item.thumbnailPath,
                                                showThumbnail = folderSettings.showFolderThumbnails,
                                                roundedCorners = folderSettings.roundedCorners,
                                                containerColor = folderCardColor,
                                                subtitle = if (item.mediaCount > 0) androidx.compose.ui.res.pluralStringResource(R.plurals.media_count_plural, item.mediaCount, item.mediaCount) else null,
                                                imageCount = item.imageCount,
                                                videoCount = item.videoCount,
                                                isNarrow = isNarrowFolderTile,
                                                isUltraNarrow = isUltraNarrowFolderTile,
                                            )
                                            if (hasFolderSelection) {
                                                if (item.path in selectedFolderPaths) {
                                                    Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.primary.copy(alpha = SelectionVisual.overlayAlpha)))
                                                }
                                                Box(Modifier.align(Alignment.TopStart).padding(4.dp).size(24.dp), contentAlignment = Alignment.Center) {
                                                    if (item.path in selectedFolderPaths) {
                                                        Box(Modifier.size(18.dp).background(Color.White, CircleShape))
                                                        Icon(Icons.Default.CheckCircle, stringResource(R.string.cd_selected), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                                    } else {
                                                        Box(Modifier.matchParentSize().background(Scrim.a20, CircleShape))
                                                        Icon(Icons.Default.RadioButtonUnchecked, stringResource(R.string.cd_not_selected), tint = Color.White, modifier = Modifier.size(22.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    repeat(folderGridColumns - chunk.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    } else {
                        // Lists are always one column; density is controlled explicitly in the
                        // shared view settings sheet and never inferred from grid columns.
                        val listCols = 1
                        visibleFolderItems.chunked(listCols).forEach { chunk ->
                            item(key = "folderrow_" + chunk.joinToString { it.path }, contentType = "folder_list_row") {
                                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                                    chunk.forEach { item ->
                                        Box(Modifier.weight(1f)) {
                                            org.fossify.gallery.compose.components.AlbumListRow(
                                                name = item.name,
                                                subtitle = androidx.compose.ui.res.pluralStringResource(R.plurals.media_count_plural, item.mediaCount, item.mediaCount),
                                                coverPath = item.thumbnailPath.ifEmpty { item.previewPaths.firstOrNull() ?: "" },
                                                selected = item.path in selectedFolderPaths,
                                                showChevron = true,
                                                showThumbnail = folderSettings.showFolderThumbnails,
                                                compact = folderSettings.listDensity == ListDensity.COMPACT,
                                                badgeSubtitle = true,
                                                pathHint = org.fossify.gallery.compose.components.folderPathHint(item.path, stringResource(R.string.internal_storage)),
                                                imageCount = item.imageCount,
                                                videoCount = item.videoCount,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp).combinedClickable(
                                                    onClick = { if (hasFileSelection) Unit else if (hasFolderSelection) selectedFolderPaths = if (item.path in selectedFolderPaths) selectedFolderPaths - item.path else selectedFolderPaths + item.path else { navStack.add(item.path); navigateToPath(item.path) } },
                                                    onLongClick = { if (!hasFileSelection) { selectedFilePaths = emptySet(); selectedFolderPaths = selectedFolderPaths + item.path } }
                                                ),
                                            )
                                        }
                                    }
                                    repeat(listCols - chunk.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                }

                if (visibleFileItems.isNotEmpty()) {
                    item(key = "explorer_media_header", contentType = "section_header") {
                        ExplorerSectionHeader(
                            label = stringResource(R.string.media),
                            count = visibleFileItems.size,
                            showGroupToggle = mediaSettings.groupBy in setOf(org.fossify.gallery.helpers.GroupBy.TAG, org.fossify.gallery.helpers.GroupBy.RATING) && collapsibleFileGroupKeys.isNotEmpty(),
                            groupsExpanded = allFileGroupsExpanded,
                            onToggleGroups = {
                                collapsedGroupKeys = org.fossify.gallery.helpers.setGroupsExpanded(
                                    collapsedKeys = collapsedGroupKeys,
                                    rows = fileGroupRows,
                                    expanded = !allFileGroupsExpanded,
                                )
                            },
                        )
                    }
                    val cornerShape = if (mediaSettings.roundedCorners) RoundedCornerShape(Radius.sm) else RoundedCornerShape(0.dp)
                    when (mediaSettings.viewType) {
                    ViewType.GRID -> {
                        fileGroupRows.forEach { row ->
                        when (row) {
                            is org.fossify.gallery.helpers.GroupRow.SectionHeader -> item(key = "filehdr_${row.key}", contentType = "header") {
                                SectionHeader(label = row.label, count = if (row.isExpanded) row.exactCount else row.totalCount, depth = row.depth, hasChildren = row.hasChildren, isExpanded = row.isExpanded, onToggle = { collapsedGroupKeys = org.fossify.gallery.helpers.toggleGroupKey(collapsedGroupKeys, row.key) }, ratingValue = row.ratingValue, accentColor = row.rootKey?.let { org.fossify.gallery.compose.components.tagAccentColor(it) })
                            }
                            is org.fossify.gallery.helpers.GroupRow.Items -> {
                        val groupFileItems = row.media.mapNotNull { fileItemByPath[it.path] }
                        groupFileItems.chunked(mediaGridColumns).forEachIndexed { rowIndex, chunk ->
                            item(key = "${row.sectionKey}/gridrow_$rowIndex", contentType = "media_grid_row") {
                                Row(Modifier.fillMaxWidth().padding(mediaSettings.spacing.dp / 2)) {
                                        chunk.forEach { item ->
                                        val isVideo = item.type == 2 || item.path.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS
                                        val mediaTypeDescription = if (isVideo) stringResource(R.string.media_type_video) else stringResource(R.string.media_type_image)
                                        val mediaBg = when (mediaSettings.displayMode) { DisplayMode.DARK -> MaterialTheme.colorScheme.surfaceVariant else -> MaterialTheme.colorScheme.surface }
                                        Box(Modifier.weight(1f).padding(mediaSettings.spacing.dp / 2).background(mediaBg, cornerShape).combinedClickable(
                                            onClick = { onFileClick(item) },
                                            onLongClick = { onFileLongClick(item) }
                                        ).semantics(mergeDescendants = true) {
                                            contentDescription = "${item.name}, $mediaTypeDescription"
                                            role = Role.Button
                                            selected = item.path in selectedFilePaths
                                        }) {
                                            Column {
                                                Box(Modifier.aspectRatio(1f).clip(cornerShape)) {
                                                    if (isVideo) VideoThumbnail(videoPath = item.path, modifier = Modifier.fillMaxSize().sharedElementKey("media_${item.path}"), contentScale = ContentScale.Crop)
                                                    else GalleryImage(path = item.path, contentDescription = item.name, modifier = Modifier.fillMaxSize().sharedElementKey("media_${item.path}"), contentScale = ContentScale.Crop, placeholderIconSize = 20.dp)
                                                    if (isVideo) {
                                                        VideoPlayOverlay(Modifier.align(Alignment.Center))
                                                    }
                                                }
                                                if (mediaSettings.showFileNames) {
                                                    val itemTags = fileTagsByPath[item.path].orEmpty()
                                                    Column(Modifier.fillMaxWidth().padding(top = 2.dp)) {
                                                        Text(item.name, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.privacyBlur(BlurRadius.thumbnail, BlurState.enabled))
                                                        if (itemTags.isNotEmpty()) {
                                                            TagChipRow(
                                                                tags = itemTags,
                                                                maxVisible = 2,
                                                                onTagClick = if (!hasFileSelection) onTagClick else null,
                                                                modifier = Modifier.padding(top = 2.dp),
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                            if (hasFileSelection) {
                                                if (item.path in selectedFilePaths) {
                                                    Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.primary.copy(alpha = SelectionVisual.overlayAlpha)))
                                                }
                                                Box(Modifier.align(Alignment.TopStart).padding(4.dp).size(24.dp), contentAlignment = Alignment.Center) {
                                                    if (item.path in selectedFilePaths) {
                                                        Box(Modifier.size(18.dp).background(Color.White, CircleShape))
                                                        Icon(Icons.Default.CheckCircle, stringResource(R.string.cd_selected), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                                    } else {
                                                        Box(Modifier.matchParentSize().background(Scrim.a20, CircleShape))
                                                        Icon(Icons.Default.RadioButtonUnchecked, stringResource(R.string.cd_not_selected), tint = Color.White, modifier = Modifier.size(22.dp))
                                                    }
                                                }
                                                IconButton(
                                                    onClick = { peekState.show(item.path, isVideo) },
                                                    modifier = Modifier.align(Alignment.TopEnd).size(48.dp),
                                                ) {
                                                    Icon(Icons.Default.ZoomIn, stringResource(R.string.cd_preview), tint = Color.White, modifier = Modifier.size(20.dp))
                                                }
                                            }
                                        }
                                    }
                                    repeat(mediaGridColumns - chunk.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                            }
                        }
                        }
                    }
                    ViewType.MOSAIC -> {
                        fileGroupRows.forEach { row ->
                        when (row) {
                            is org.fossify.gallery.helpers.GroupRow.SectionHeader -> item(key = "filehdr_${row.key}", contentType = "header") {
                                SectionHeader(label = row.label, count = if (row.isExpanded) row.exactCount else row.totalCount, depth = row.depth, hasChildren = row.hasChildren, isExpanded = row.isExpanded, onToggle = { collapsedGroupKeys = org.fossify.gallery.helpers.toggleGroupKey(collapsedGroupKeys, row.key) }, ratingValue = row.ratingValue, accentColor = row.rootKey?.let { org.fossify.gallery.compose.components.tagAccentColor(it) })
                            }
                            is org.fossify.gallery.helpers.GroupRow.Items -> {
                        val groupFileItems = row.media.mapNotNull { fileItemByPath[it.path] }
                        groupFileItems.chunked(EXPLORER_MOSAIC_CHUNK_SIZE).forEachIndexed { chunkIndex, chunk ->
                            // Keep masonry composition bounded. The old implementation rendered all
                            // files in this group inside one LazyColumn item, so a large folder was
                            // effectively eager and one aspect-ratio result invalidated everything.
                            item(key = "explorer_media_mosaic_${row.sectionKey}_$chunkIndex", contentType = "media_mosaic_chunk") {
                                ExplorerMosaicChunk(
                                    items = chunk,
                                    tagsByPath = fileTagsByPath,
                                    columnCount = mediaGridColumns,
                                    spacing = mediaSettings.spacing.dp,
                                    cornerShape = cornerShape,
                                    displayMode = mediaSettings.displayMode,
                                    showFileNames = mediaSettings.showFileNames,
                                    hasFileSelection = hasFileSelection,
                                    selectedFilePaths = selectedFilePaths,
                                    onClick = ::onFileClick,
                                    onLongClick = ::onFileLongClick,
                                    onTagClick = onTagClick,
                                    onPreviewClick = { item ->
                                        peekState.show(item.path, item.path.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS)
                                    },
                                )
                            }
                        }
                            }
                        }
                        }
                    }
                    else -> {
                        val listCompact = mediaSettings.listDensity == ListDensity.COMPACT
                        fileGroupRows.forEach { row ->
                        when (row) {
                            is org.fossify.gallery.helpers.GroupRow.SectionHeader -> item(key = "filehdr_${row.key}", contentType = "header") {
                                SectionHeader(label = row.label, count = if (row.isExpanded) row.exactCount else row.totalCount, depth = row.depth, hasChildren = row.hasChildren, isExpanded = row.isExpanded, onToggle = { collapsedGroupKeys = org.fossify.gallery.helpers.toggleGroupKey(collapsedGroupKeys, row.key) }, ratingValue = row.ratingValue, accentColor = row.rootKey?.let { org.fossify.gallery.compose.components.tagAccentColor(it) }, showGuideLine = true)
                            }
                            is org.fossify.gallery.helpers.GroupRow.Items -> {
                        val groupFileItems = row.media.mapNotNull { fileItemByPath[it.path] }
                        items(
                            items = groupFileItems,
                            key = { "${row.sectionKey}/${it.path}" },
                            contentType = { "explorer_media_row" },
                        ) { item ->
                            val isVideo = item.type == 2 || item.path.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS
                            val mediaTypeDescription = if (isVideo) stringResource(R.string.media_type_video) else stringResource(R.string.media_type_image)
                            Surface(
                                Modifier.fillMaxWidth().combinedClickable(
                                    onClick = { onFileClick(item) },
                                    onLongClick = { onFileLongClick(item) }
                                ).semantics(mergeDescendants = true) {
                                    contentDescription = "${item.name}, $mediaTypeDescription"
                                    role = Role.Button
                                    selected = item.path in selectedFilePaths
                                },
                                color = if (item.path in selectedFilePaths) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            ) {
                                Row(Modifier.padding(horizontal = 16.dp, vertical = if (listCompact) 6.dp else 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(if (listCompact) 40.dp else 52.dp).clip(RoundedCornerShape(Radius.sm))) {
                                        if (isVideo) VideoThumbnail(videoPath = item.path, modifier = Modifier.fillMaxSize().sharedElementKey("media_${item.path}"), contentScale = ContentScale.Crop, thumbnailSize = if (listCompact) 160 else 192)
                                        else GalleryImage(path = item.path, contentDescription = item.name, modifier = Modifier.fillMaxSize().sharedElementKey("media_${item.path}"), contentScale = ContentScale.Crop, placeholderIconSize = 16.dp)
                                        if (isVideo) {
                                            VideoPlayOverlay(Modifier.align(Alignment.Center), size = if (listCompact) 24.dp else 28.dp)
                                        }
                                        if (hasFileSelection) {
                                            Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                                                if (item.path in selectedFilePaths) {
                                                    Box(Modifier.size(16.dp).background(Color.White, CircleShape))
                                                    Icon(Icons.Default.CheckCircle, stringResource(R.string.cd_selected), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                                } else {
                                                    Box(Modifier.matchParentSize().background(Scrim.a20))
                                                    Icon(Icons.Default.RadioButtonUnchecked, stringResource(R.string.cd_not_selected), tint = Color.White, modifier = Modifier.size(18.dp))
                                                }
                                            }
                                        }
                                    }
                                    Spacer(Modifier.width(if (listCompact) 10.dp else 12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(item.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.privacyBlur(BlurRadius.thumbnail, BlurState.enabled))
                                        Text(formatFileSize(item.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        TagChipRow(
                                            tags = fileTagsByPath[item.path].orEmpty(),
                                            maxVisible = 3,
                                            onTagClick = if (!hasFileSelection) onTagClick else null,
                                            modifier = Modifier.padding(top = 2.dp),
                                        )
                                    }
                                    if (hasFileSelection) {
                                        IconButton(onClick = { peekState.show(item.path, isVideo) }, modifier = Modifier.size(48.dp)) {
                                            Icon(Icons.Default.ZoomIn, stringResource(R.string.cd_preview), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                        }
                                    }
                                }
                            }
                            HorizontalDivider(Modifier.padding(start = if (listCompact) 66.dp else 80.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        }
                            }
                        }
                        }
                    }
                    }
                }
            }
            if ((isNameFiltering || filterState.isActive) && visibleFolderItems.isEmpty() && visibleFileItems.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.SearchOff,
                    title = stringResource(R.string.no_results),
                    subtitle = if (isNameFiltering) stringResource(R.string.explorer_name_filter_no_results, normalizedNameFilter) else stringResource(R.string.no_items_hint),
                    actionLabel = stringResource(R.string.clear_filter),
                    onAction = if (isNameFiltering) onClearNameFilter else filterActions.clearAll,
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
                )
            }
            FastScrollBar(
                state = listState,
                modifier = Modifier.align(Alignment.CenterEnd).padding(vertical = 10.dp),
            )
            peekState.path?.let { peekPath ->
                BackHandler(enabled = true) { peekState.hide() }
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.86f)).clickable { peekState.hide() },
                    contentAlignment = Alignment.Center,
                ) {
                    if (peekState.isVideo) {
                        VideoThumbnail(
                            videoPath = peekPath,
                            modifier = Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.75f),
                            contentScale = ContentScale.Fit,
                            thumbnailSize = 1024,
                        )
                    } else {
                        GalleryImage(
                            path = peekPath,
                            contentDescription = peekPath.substringAfterLast('/'),
                            modifier = Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.75f),
                            contentScale = ContentScale.Fit,
                            thumbnailSize = 1024,
                        )
                    }
                }
            }
            }
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

    if (showFileFolderPicker) {
        val batch = selectedFilePaths.toList()
        FolderPickerSheet(
            isMoveOperation = fileFolderPickerIsMove,
            sourcePaths = batch,
            onDismiss = { showFileFolderPicker = false; selectedFilePaths = emptySet() },
        )
    }
    fileInfoPath?.let { path ->
        MediaInfoDialog(path = path, onDismiss = { fileInfoPath = null })
    }
    if (showFileRenameDialog) {
        val batch = selectedFilePaths.toList()
        RenameDialog(
            paths = batch,
            onRenamed = { mapping ->
                selectedFilePaths = selectedFilePaths.map { mapping[it] ?: it }.toSet()
                fileRangeAnchorPath = fileRangeAnchorPath?.let { mapping[it] ?: it }
            },
            onDismiss = { showFileRenameDialog = false },
        )
    }
    if (showFileRateTagSheet) {
        val batch = selectedFilePaths.toList()
        LaunchedEffect(Unit) {
            withContext(Dispatchers.IO) {
                val counts = repo.getTagCountsCached()
                fileAllTags = counts.entries.sortedByDescending { it.value }.map { it.key }
                fileTagCounts = counts
            }
        }
        RateAndTagSheet(
            batchCount = batch.size,
            currentRating = fileCurrentRating,
            ratingIsMixed = fileRatingIsMixed,
            onRate = { i ->
                fileCurrentRating = i
                fileRatingIsMixed = false
                scope.launch {
                    val oldRatings = withContext(Dispatchers.IO) { repo.getRatingsForPaths(batch) }
                    val successful = mutableListOf<String>()
                    org.fossify.gallery.compose.util.XmpBatch.run(context, batch.distinct(), onSuccess = { successful += it }) {
                        repo.writeRatingXmp(it, i)
                    }
                    withContext(Dispatchers.IO) {
                        repo.setDbRatingBatch(successful, i)
                        repo.refreshMetadataCache(successful)
                    }
                    if (successful.isNotEmpty()) UndoManager.push(UndoAction(successful.toSet(), UndoType.RATING_CHANGE, ratingSnapshots = oldRatings.filterKeys { it in successful }))
                }
            },
            initialTags = fileSelectedCommonTags,
            mixedTagCounts = fileMixedTagCounts,
            onAddTag = { tag -> scope.launch {
                val successful = mutableSetOf<String>()
                org.fossify.gallery.compose.util.XmpBatch.run(context, batch.distinct(), onSuccess = { successful += it }) { repo.addTag(it, tag) }
                if (successful.isNotEmpty()) {
                    context.config.rememberRecentTag(tag)
                    UndoManager.push(UndoAction(successful, UndoType.TAG_ADD, extra = mapOf("tag" to tag)))
                }
            } },
            onRemoveTag = { tag -> scope.launch {
                val successful = mutableSetOf<String>()
                org.fossify.gallery.compose.util.XmpBatch.run(context, batch.distinct(), onSuccess = { successful += it }) { repo.removeTag(it, tag) }
                if (successful.isNotEmpty()) UndoManager.push(UndoAction(successful, UndoType.TAG_REMOVE, extra = mapOf("tag" to tag)))
            } },
            suggestedTags = fileAllTags,
            suggestedTagCounts = fileTagCounts,
            recentTags = context.config.recentTags,
            tagHierarchy = context.config.tagHierarchy,
            onSetTagParent = { child, parent ->
                val hierarchy = context.config.tagHierarchy.toMutableMap()
                if (parent.isNullOrBlank()) hierarchy.remove(child) else hierarchy[child] = parent
                context.config.tagHierarchy = hierarchy
            },
            onDismiss = { showFileRateTagSheet = false },
        )
    }
    if (showFileDeleteConfirm) {
        val itemsCnt = selectedFilePaths.size
        val itemsText = context.resources.getQuantityString(org.fossify.commons.R.plurals.delete_items, itemsCnt, itemsCnt)
        val question = context.getString(if (context.config.useRecycleBin) org.fossify.commons.R.string.move_to_recycle_bin_confirmation else org.fossify.commons.R.string.deletion_confirmation, itemsText)
        ConfirmDestructive(
            title = stringResource(org.fossify.commons.R.string.delete),
            text = question,
            confirmLabel = stringResource(org.fossify.commons.R.string.delete),
            onConfirm = {
                showFileDeleteConfirm = false
                val d = selectedFilePaths
                context.withRecycleBinDeletePasswordCheck {
                    scope.launch(Dispatchers.IO) { repo.moveToRecycleBinBatch(d) }
                    UndoManager.push(UndoAction(paths = d, type = UndoType.DELETE))
                    selectedFilePaths = emptySet()
                }
            },
            onDismiss = { showFileDeleteConfirm = false },
        )
    }

    renameFolderPath?.let { path ->
        FolderRenameDialog(
            folderPath = path,
            onDismiss = { renameFolderPath = null },
            onRenamed = { _, _ -> selectedFolderPaths = emptySet() },
        )
    }

    if (showDeleteFoldersConfirm) {
        ConfirmDestructive(
            title = stringResource(org.fossify.commons.R.string.delete),
            text = stringResource(R.string.delete_folders_confirmation),
            confirmLabel = stringResource(org.fossify.commons.R.string.delete),
            onConfirm = {
                showDeleteFoldersConfirm = false
                val folders = selectedFolderPaths
                context.withRecycleBinDeletePasswordCheck {
                    selectedFolderPaths = emptySet()
                    scope.launch {
                        val paths = withContext(Dispatchers.IO) { repo.mediaPathsUnderFolders(folders) }
                        if (paths.isNotEmpty()) {
                            repo.moveToRecycleBinBatch(paths)
                            UndoManager.push(UndoAction(paths = paths.toSet(), type = UndoType.DELETE))
                        }
                    }
                }
            },
            onDismiss = { showDeleteFoldersConfirm = false },
        )
    }

    pendingMoverSources?.let { sources ->
        val addedFormat = stringResource(R.string.mover_pair_added)
        val addedCountFormat = stringResource(R.string.mover_pairs_added_count)
        FolderPathPickerSheet(
            title = stringResource(R.string.mover_select_dest),
            initialPath = context.config.lastExplorerPath.ifBlank { context.config.internalStoragePath },
            onPathSelected = { destination ->
                org.fossify.gallery.helpers.addMoverPairs(context, sources, destination)
                val msg = if (sources.size == 1) addedFormat.format(File(sources[0]).name, File(destination).name) else addedCountFormat.format(sources.size, File(destination).name)
                context.toast(msg, android.widget.Toast.LENGTH_SHORT)
            },
            onDismiss = { pendingMoverSources = null },
            suggestedFolderName = if (sources.size == 1) File(sources[0]).name else null,
        )
    }
}

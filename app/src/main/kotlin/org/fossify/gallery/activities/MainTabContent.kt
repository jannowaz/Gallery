package org.fossify.gallery.activities

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import org.fossify.commons.extensions.toast
import org.fossify.gallery.R
import org.fossify.gallery.compose.screens.AlbumsScreen
import org.fossify.gallery.compose.screens.CollectionsScreen
import org.fossify.gallery.compose.screens.ExplorerScreen
import org.fossify.gallery.compose.screens.FavoritesScreen
import org.fossify.gallery.compose.screens.MediaScreen
import org.fossify.gallery.compose.screens.TabViewSettings
import org.fossify.gallery.compose.screens.ViewSettingsViewModel
import org.fossify.gallery.compose.screens.tagbrowser.TagBrowserScreen
import org.fossify.gallery.helpers.PickIntent
import org.fossify.gallery.helpers.RefreshBus
import org.fossify.gallery.navigation.Folder
import org.fossify.gallery.navigation.FolderSwipe
import org.fossify.gallery.navigation.PersonReview
import org.fossify.gallery.navigation.navigateToViewer
import org.fossify.gallery.viewmodels.ExplorerUiState
import org.fossify.gallery.viewmodels.ExplorerViewModel
import org.fossify.gallery.viewmodels.LibraryFilterActions
import org.fossify.gallery.viewmodels.LibraryFilterState
import org.fossify.gallery.viewmodels.toLibraryFilterState
import org.fossify.gallery.workers.MetadataSyncWorker
import java.io.File

internal const val TAB_MEDIA = 0
internal const val TAB_ALBUMS = 1
internal const val TAB_EXPLORER = 2
internal const val TAB_COLLECTIONS = 3
internal const val TAB_FAVORITES = 4
internal const val TAB_TAGS = 5

private const val PAGER_TAB_COUNT = 3
private const val DRAWER_EDGE_SWIPE_THRESHOLD_PX = 140f

@Composable
internal fun MainTabContent(
    state: ExplorerUiState,
    tabSettings: TabViewSettings,
    ctx: Context,
    navController: NavHostController,
    onMediaSelection: (Boolean) -> Unit = {},
    onExplorerCanGoUp: (Boolean) -> Unit = {},
    searchResultBackPending: Boolean = false,
    explorerNameFilter: String = "",
    onExplorerNameFilterChange: (String) -> Unit = {},
    tabSearchQuery: String = "",
    onClearTabSearch: () -> Unit = {},
    explorerSearchBackActive: Boolean = false,
    onOpenDrawer: () -> Unit = {},
    onOpenNewMedia: () -> Unit = {},
    onMarkNewMediaRead: () -> Unit = {},
    onOpenViewSettings: () -> Unit = {},
    mainVM: ExplorerViewModel = viewModel(),
) {
    val tabStateHolder = rememberSaveableStateHolder()
    val sharedFilterState = state.toLibraryFilterState()
    val sharedFilterActions = remember(mainVM) { createSharedFilterActions(mainVM) }

    if (state.selectedTab <= TAB_EXPLORER) {
        MainPagerTabs(
            state = state,
            tabSettings = tabSettings,
            ctx = ctx,
            navController = navController,
            tabStateHolder = tabStateHolder,
            sharedFilterState = sharedFilterState,
            sharedFilterActions = sharedFilterActions,
            onMediaSelection = onMediaSelection,
            onExplorerCanGoUp = onExplorerCanGoUp,
            searchResultBackPending = searchResultBackPending,
            explorerNameFilter = explorerNameFilter,
            onExplorerNameFilterChange = onExplorerNameFilterChange,
            tabSearchQuery = tabSearchQuery,
            onClearTabSearch = onClearTabSearch,
            explorerSearchBackActive = explorerSearchBackActive,
            onOpenDrawer = onOpenDrawer,
            onOpenNewMedia = onOpenNewMedia,
            onMarkNewMediaRead = onMarkNewMediaRead,
            onOpenViewSettings = onOpenViewSettings,
        )
    } else {
        tabStateHolder.SaveableStateProvider("main-tab-${state.selectedTab}") {
            MainSecondaryTab(
                state = state,
                tabSettings = tabSettings,
                ctx = ctx,
                navController = navController,
                sharedFilterState = sharedFilterState,
                sharedFilterActions = sharedFilterActions,
                tabSearchQuery = tabSearchQuery,
                onClearTabSearch = onClearTabSearch,
                onMediaSelection = onMediaSelection,
                onOpenViewSettings = onOpenViewSettings,
            )
        }
    }
}

private fun createSharedFilterActions(mainVM: ExplorerViewModel): LibraryFilterActions =
    LibraryFilterActions(
        clearAll = { mainVM.clearFilters() },
        clearRating = { mainVM.setRatingFilter(0) },
        clearTags = { mainVM.setTagFilter(null, null) },
        removeTag = mainVM::removeTagFromFilter,
        clearTagMatchAll = { mainVM.setTagMatchAll(false) },
        clearPath = {
            mainVM.setPathFilter(null)
            mainVM.setTextQueryFilter("")
            mainVM.setExcludePathFilter(null)
            mainVM.setCollectionName(null)
            mainVM.setCollectionMediaPaths(emptySet())
            mainVM.setFolderFilterName(null)
        },
        clearSize = { mainVM.setMinSizeFilter(0L) },
        clearDate = { mainVM.setDateRangeFilter(0) },
        clearAddedSince = { mainVM.setNewMediaFilter(0L) },
        clearType = { mainVM.setTypeFilter(0) },
        clearReview = { mainVM.setUndecidedReviewFilter(false) },
    )

@Composable
private fun MainPagerTabs(
    state: ExplorerUiState,
    tabSettings: TabViewSettings,
    ctx: Context,
    navController: NavHostController,
    tabStateHolder: SaveableStateHolder,
    sharedFilterState: LibraryFilterState,
    sharedFilterActions: LibraryFilterActions,
    onMediaSelection: (Boolean) -> Unit,
    onExplorerCanGoUp: (Boolean) -> Unit,
    searchResultBackPending: Boolean,
    explorerNameFilter: String,
    onExplorerNameFilterChange: (String) -> Unit,
    tabSearchQuery: String,
    onClearTabSearch: () -> Unit,
    explorerSearchBackActive: Boolean,
    onOpenDrawer: () -> Unit,
    onOpenNewMedia: () -> Unit,
    onMarkNewMediaRead: () -> Unit,
    onOpenViewSettings: () -> Unit,
    mainVM: ExplorerViewModel = viewModel(),
    viewSettingsVM: ViewSettingsViewModel = viewModel(),
) {
    val pagerState = rememberPagerState(
        initialPage = state.selectedTab.coerceIn(TAB_MEDIA, TAB_EXPLORER),
        pageCount = { PAGER_TAB_COUNT },
    )
    val adjacentPageCount = remember(ctx) {
        val activityManager = ctx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        if (activityManager?.isLowRamDevice == true) 0 else 1
    }
    LaunchedEffect(pagerState) {
        var initialValue = true
        snapshotFlow { pagerState.settledPage }.collect { page ->
            if (initialValue) initialValue = false else mainVM.setSelectedTab(page)
        }
    }
    LaunchedEffect(state.selectedTab) {
        val inPager = state.selectedTab in TAB_MEDIA..TAB_EXPLORER
        val needsScroll = state.selectedTab != pagerState.currentPage &&
            state.selectedTab != pagerState.targetPage
        if (inPager && needsScroll) {
            pagerState.animateScrollToPage(state.selectedTab)
        }
    }
    val edgeToDrawer = rememberEdgeToDrawerConnection(pagerState, onOpenDrawer)
    HorizontalPager(
        state = pagerState,
        beyondViewportPageCount = adjacentPageCount,
        modifier = Modifier.fillMaxSize().nestedScroll(edgeToDrawer),
    ) { tab ->
        val tabDispatcherOwner =
            rememberNavigationEventDispatcherOwner(enabled = pagerState.currentPage == tab)
        CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides tabDispatcherOwner) {
            tabStateHolder.SaveableStateProvider("main-tab-$tab") {
                when (tab) {
                    TAB_MEDIA -> MediaTabPage(
                        state = state,
                        tabSettings = tabSettings,
                        ctx = ctx,
                        navController = navController,
                        onMediaSelection = onMediaSelection,
                        onOpenNewMedia = onOpenNewMedia,
                        onMarkNewMediaRead = onMarkNewMediaRead,
                        onOpenViewSettings = onOpenViewSettings,
                    )
                    TAB_ALBUMS -> AlbumsScreen(
                        onFolderClick = { dir ->
                            openFolderChecked(ctx, dir.path) {
                                navController.navigate(Folder(dir.path)) { launchSingleTop = true }
                            }
                        },
                        viewSettings = tabSettings.albums,
                        externalSearchQuery = tabSearchQuery,
                        onClearExternalSearch = onClearTabSearch,
                        onOpenViewSettings = onOpenViewSettings,
                        onSelectionActiveChanged = onMediaSelection,
                        tabIndex = TAB_ALBUMS,
                        onColumnCountChange = { next ->
                            viewSettingsVM.updateAlbums(tabSettings.albums.copy(columnCount = next))
                        },
                    )
                    TAB_EXPLORER -> ExplorerTabPage(
                        state = state,
                        tabSettings = tabSettings,
                        ctx = ctx,
                        navController = navController,
                        sharedFilterState = sharedFilterState,
                        sharedFilterActions = sharedFilterActions,
                        explorerNameFilter = explorerNameFilter,
                        onExplorerNameFilterChange = onExplorerNameFilterChange,
                        onMediaSelection = onMediaSelection,
                        onExplorerCanGoUp = onExplorerCanGoUp,
                        externalBackHandlerActive =
                            searchResultBackPending || explorerSearchBackActive,
                        onOpenViewSettings = onOpenViewSettings,
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberEdgeToDrawerConnection(
    pagerState: PagerState,
    onOpenDrawer: () -> Unit,
): NestedScrollConnection {
    val latestOpenDrawer by rememberUpdatedState(onOpenDrawer)
    return remember(pagerState) {
        object : NestedScrollConnection {
            var accum = 0f
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.x < 0f) accum = 0f
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (pagerState.currentPage == TAB_MEDIA && available.x > 0f) {
                    accum += available.x
                    if (accum > DRAWER_EDGE_SWIPE_THRESHOLD_PX) {
                        accum = 0f
                        latestOpenDrawer()
                    }
                }
                return Offset.Zero
            }
        }
    }
}

@Composable
private fun MediaTabPage(
    state: ExplorerUiState,
    tabSettings: TabViewSettings,
    ctx: Context,
    navController: NavHostController,
    onMediaSelection: (Boolean) -> Unit,
    onOpenNewMedia: () -> Unit,
    onMarkNewMediaRead: () -> Unit,
    onOpenViewSettings: () -> Unit,
    mainVM: ExplorerViewModel = viewModel(),
    viewSettingsVM: ViewSettingsViewModel = viewModel(),
) {
    val activity = ctx as? Activity
    val pickMultiple = activity?.let {
        PickIntent.isPick(it.intent) && PickIntent.allowsMultiple(it.intent)
    } ?: false
    MediaScreen(
        viewSettings = if (state.activeCollectionName != null) {
            tabSettings.collectionMedia
        } else {
            tabSettings.media
        },
        ratingFilter = state.activeRatingFilter,
        tagFilterNames = state.activeTagFilter,
        tagMatchAll = state.activeTagMatchAll,
        pathFilter = state.activePathFilter,
        excludePathFilter = state.activeExcludePathFilter,
        minSizeFilter = state.activeMinSizeFilter,
        dateRangeFilter = state.activeDateRangeFilter,
        addedSince = state.activeAddedSince,
        typeFilter = state.activeTypeFilter,
        reviewFilter = state.activeReviewFilter,
        textQuery = state.activeTextQuery,
        newMediaCount = state.newMediaCount,
        activeTagName = state.activeTagName,
        activePathName = state.activePathName,
        activeCollectionName = state.activeCollectionName,
        additionalMediaPaths = state.activeCollectionMediaPaths,
        activeFolderFilterName = state.activeFolderFilterName,
        onClearFilter = { mainVM.clearFilters() },
        onClearRatingFilter = { mainVM.setRatingFilter(0) },
        onClearTagFilter = { mainVM.setTagFilter(null, null) },
        onRemoveTagFilter = mainVM::removeTagFromFilter,
        onClearTagMatchAll = { mainVM.setTagMatchAll(false) },
        onTagClick = { tag -> applyTagFilter(tag, mainVM, ctx, TAB_MEDIA) },
        onClearPathFilter = {
            mainVM.setPathFilter(null)
            mainVM.setTextQueryFilter("")
            mainVM.setExcludePathFilter(null)
            mainVM.setCollectionName(null)
            mainVM.setCollectionMediaPaths(emptySet())
            mainVM.setFolderFilterName(null)
        },
        onClearSizeFilter = { mainVM.setMinSizeFilter(0L) },
        onClearDateFilter = { mainVM.setDateRangeFilter(0) },
        onClearAddedSinceFilter = { mainVM.setNewMediaFilter(0L) },
        onClearTypeFilter = { mainVM.setTypeFilter(0) },
        onClearReviewFilter = { mainVM.setUndecidedReviewFilter(false) },
        showToolbar = true,
        onOpenViewSettings = onOpenViewSettings,
        onOpenNewMedia = onOpenNewMedia,
        onMarkNewMediaSeen = onMarkNewMediaRead,
        onNavigateToViewer = { paths, startIndex ->
            PickIntent.handleMediaTap(activity, paths, startIndex) {
                navController.navigateToViewer(paths, startIndex)
            }
        },
        onNavigateToViewerFast = { path, resolveWindow, loadMore ->
            if (activity != null && PickIntent.isPick(activity.intent)) {
                PickIntent.handleMediaTap(activity, listOf(path), 0) {}
            } else {
                navController.navigateToViewer(path, resolveWindow, loadMore)
            }
        },
        scrollToPath = state.lastViewedPath,
        onClearScrollToPath = { mainVM.clearLastViewedPath() },
        onSelectionActiveChanged = onMediaSelection,
        tabIndex = TAB_MEDIA,
        pickMultiple = pickMultiple,
        onConfirmPick = { paths -> activity?.let { PickIntent.returnMultiple(it, paths) } },
        onColumnCountChange = { next ->
            if (state.activeCollectionName != null) {
                viewSettingsVM.updateCollectionMedia(
                    tabSettings.collectionMedia.copy(columnCount = next),
                )
            } else {
                viewSettingsVM.updateMedia(tabSettings.media.copy(columnCount = next))
            }
        },
    )
}

@Composable
private fun ExplorerTabPage(
    state: ExplorerUiState,
    tabSettings: TabViewSettings,
    ctx: Context,
    navController: NavHostController,
    sharedFilterState: LibraryFilterState,
    sharedFilterActions: LibraryFilterActions,
    explorerNameFilter: String,
    onExplorerNameFilterChange: (String) -> Unit,
    onMediaSelection: (Boolean) -> Unit,
    onExplorerCanGoUp: (Boolean) -> Unit,
    externalBackHandlerActive: Boolean,
    onOpenViewSettings: () -> Unit,
    mainVM: ExplorerViewModel = viewModel(),
    viewSettingsVM: ViewSettingsViewModel = viewModel(),
) {
    val activity = ctx as? Activity
    ExplorerScreen(
        internalStoragePath = state.explorerPath,
        nameFilter = explorerNameFilter,
        onClearNameFilter = { onExplorerNameFilterChange("") },
        folderSettings = tabSettings.explorerAlbums,
        mediaSettings = viewSettingsVM.getExplorerMediaSettingsForPath(state.explorerPath),
        filterState = sharedFilterState,
        filterActions = sharedFilterActions,
        onPathChange = { mainVM.setExplorerPath(it) },
        onSelectionActiveChanged = onMediaSelection,
        onCanGoUpChanged = onExplorerCanGoUp,
        externalBackHandlerActive = externalBackHandlerActive,
        onNavigateToViewer = { paths, startIndex ->
            PickIntent.handleMediaTap(activity, paths, startIndex) {
                navController.navigateToViewer(paths, startIndex)
            }
        },
        initialScrollIndex = state.gridScrollIndex,
        initialScrollOffset = state.gridScrollOffset,
        onScrollPositionChanged = { index, offset -> mainVM.saveScrollPosition(index, offset) },
        onOpenInMedia = { paths ->
            val name = if (paths.size == 1) {
                File(paths.first()).name
            } else {
                ctx.getString(R.string.selected_folders_count, paths.size)
            }
            mainVM.clearFilters()
            mainVM.setPreFilterTab(TAB_EXPLORER)
            mainVM.setPathFilter(paths)
            mainVM.setExcludePathFilter(null)
            mainVM.setCollectionName(null)
            mainVM.setFolderFilterName(name)
            mainVM.setSelectedTab(TAB_MEDIA)
        },
        onRescan = { RefreshBus.trigger() },
        onScanMetadata = {
            MetadataSyncWorker.scheduleFullScan(ctx)
            ctx.toast(ctx.getString(R.string.scan_started_notification), Toast.LENGTH_SHORT)
        },
        onOpenSwipeMode = { path, startPath ->
            navController.navigate(FolderSwipe(path, startPath.orEmpty())) {
                launchSingleTop = true
            }
        },
        onOpenPersonReview = { path ->
            navController.navigate(PersonReview(path)) { launchSingleTop = true }
        },
        onOpenViewSettings = onOpenViewSettings,
        onTagClick = { tag -> applyTagFilter(tag, mainVM, ctx, TAB_EXPLORER) },
        tabIndex = TAB_EXPLORER,
        onMediaColumnCountChange = { next ->
            val current = viewSettingsVM.getExplorerMediaSettingsForPath(state.explorerPath)
            val applyGlobally = !viewSettingsVM.hasCustomExplorerMediaSettings(state.explorerPath)
            viewSettingsVM.updateExplorerMediaForPath(
                state.explorerPath,
                current.copy(columnCount = next),
                applyGlobally,
            )
        },
        onFolderColumnCountChange = { next ->
            viewSettingsVM.updateExplorerAlbums(tabSettings.explorerAlbums.copy(columnCount = next))
        },
    )
}

@Composable
private fun MainSecondaryTab(
    state: ExplorerUiState,
    tabSettings: TabViewSettings,
    ctx: Context,
    navController: NavHostController,
    sharedFilterState: LibraryFilterState,
    sharedFilterActions: LibraryFilterActions,
    tabSearchQuery: String,
    onClearTabSearch: () -> Unit,
    onMediaSelection: (Boolean) -> Unit,
    onOpenViewSettings: () -> Unit,
    mainVM: ExplorerViewModel = viewModel(),
    viewSettingsVM: ViewSettingsViewModel = viewModel(),
) {
    val activity = ctx as? Activity
    when (state.selectedTab) {
        TAB_COLLECTIONS -> CollectionsScreen(
            viewSettings = tabSettings.collections,
            onCollectionClick = { coll -> applyCollection(coll, mainVM, ctx) },
            externalSearchQuery = tabSearchQuery,
            onClearExternalSearch = onClearTabSearch,
            onOpenViewSettings = onOpenViewSettings,
            tabIndex = TAB_COLLECTIONS,
        )
        TAB_FAVORITES -> FavoritesScreen(
            viewSettings = tabSettings.favorites,
            externalSearchQuery = tabSearchQuery,
            onClearExternalSearch = onClearTabSearch,
            onOpenViewSettings = onOpenViewSettings,
            filterState = sharedFilterState,
            filterActions = sharedFilterActions,
            onNavigateToViewer = { paths, startIndex ->
                PickIntent.handleMediaTap(activity, paths, startIndex) {
                    navController.navigateToViewer(paths, startIndex)
                }
            },
            onFolderClick = { path ->
                openFolderChecked(ctx, path) {
                    navController.navigate(Folder(path)) { launchSingleTop = true }
                }
            },
            onSelectionActiveChanged = onMediaSelection,
            tabIndex = TAB_FAVORITES,
            onColumnCountChange = { next ->
                viewSettingsVM.updateFavorites(tabSettings.favorites.copy(columnCount = next))
            },
        )
        TAB_TAGS -> TagBrowserScreen(
            onBack = {},
            onTagFilterApplied = { tagNames, tagName, matchAll ->
                mainVM.clearFilters()
                mainVM.setPreFilterTab(TAB_TAGS)
                mainVM.setRatingFilter(0)
                mainVM.setTagFilter(tagNames, tagName)
                mainVM.setTagMatchAll(matchAll)
                mainVM.setPathFilter(null)
                mainVM.setExcludePathFilter(null)
                mainVM.setSelectedTab(TAB_MEDIA)
            },
            viewSettings = tabSettings.tags,
            embeddedInMainTabs = true,
            externalSearchQuery = tabSearchQuery,
            onClearExternalSearch = onClearTabSearch,
            onOpenViewSettings = onOpenViewSettings,
        )
    }
}

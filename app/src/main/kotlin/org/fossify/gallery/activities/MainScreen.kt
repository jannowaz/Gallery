package org.fossify.gallery.activities

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.ReportDrawnWhen
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import kotlinx.coroutines.launch
import org.fossify.commons.extensions.toast
import org.fossify.gallery.R
import org.fossify.gallery.compose.components.AllFilesAccessSheet
import org.fossify.gallery.compose.screens.ViewSettingsViewModel
import org.fossify.gallery.compose.util.ExplorerNavigationBus
import org.fossify.gallery.compose.util.ScrollToTopBus
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.PickIntent
import org.fossify.gallery.models.MediaReviewFilter
import org.fossify.gallery.navigation.Settings
import org.fossify.gallery.navigation.navigateToViewer
import org.fossify.gallery.viewmodels.AlbumsViewModel
import org.fossify.gallery.viewmodels.ExplorerUiState
import org.fossify.gallery.viewmodels.ExplorerViewModel
import org.fossify.gallery.viewmodels.filterReturnTab
import org.fossify.gallery.viewmodels.hasActiveFilter

private const val WIDE_WINDOW_DP = 600
private const val SEARCH_BAR_HIDE_THRESHOLD_DP = 56
private const val SEARCH_BAR_SHOW_THRESHOLD_DP = 24
private const val SCROLL_MIN_DELTA = 0.5f
private const val OVERSCROLL_REVEAL_PX = 12f
private const val SEARCH_PANEL_HEIGHT_RATIO = 0.75f
private const val SEARCH_PANEL_MIN_HEIGHT_DP = 360
private const val SEARCH_SLIDE_DIVISOR = 8
private const val SEARCH_ENTER_SLIDE_MS = 180
private const val SEARCH_ENTER_FADE_MS = 150
private const val SEARCH_EXIT_SLIDE_MS = 140
private const val SEARCH_EXIT_FADE_MS = 120
private const val PICK_KIND_IMAGES = 1
private const val PICK_KIND_VIDEOS = 2

private val FILTERABLE_TABS = setOf(TAB_MEDIA, TAB_EXPLORER, TAB_FAVORITES)

@Composable
fun MainScreen(
    navController: NavHostController,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
    onContentReady: () -> Unit = {},
    mainVM: ExplorerViewModel = viewModel(),
    viewSettingsVM: ViewSettingsViewModel = viewModel(),
    albumsViewModel: AlbumsViewModel = viewModel(),
) {
    val ctx = LocalContext.current
    val uiState by mainVM.state.collectAsStateWithLifecycle()
    val pendingExplorerFolder by ExplorerNavigationBus.folderPath.collectAsStateWithLifecycle()
    val tabSettings by viewSettingsVM.settings.collectAsStateWithLifecycle()
    val settingsMode by viewSettingsVM.settingsMode.collectAsStateWithLifecycle()
    val albumsState by albumsViewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    var activeSheet by remember { mutableStateOf<ActiveSheet?>(null) }
    var isMediaSelectionActive by remember { mutableStateOf(false) }
    var explorerCanGoUp by remember { mutableStateOf(false) }
    var searchBarVisible by remember { mutableStateOf(true) }
    var showAllFilesPrompt by remember { mutableStateOf(false) }

    val search = rememberMainSearchState(uiState = uiState, ctx = ctx)
    val allFilesLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            showAllFilesPrompt = !hasAllFilesAccess(ctx)
        }
    val allFilesAccessGranted = hasAllFilesAccess(ctx)
    val isWideWindow = LocalConfiguration.current.screenWidthDp >= WIDE_WINDOW_DP

    LaunchedEffect(pendingExplorerFolder) {
        pendingExplorerFolder?.let { folderPath ->
            mainVM.setExplorerPath(folderPath)
            mainVM.setSelectedTab(TAB_EXPLORER)
            ExplorerNavigationBus.consume(folderPath)
        }
    }
    LaunchedEffect(
        uiState.selectedTab,
        uiState.explorerPath,
        search.searchActive,
        uiState.hasActiveFilter,
        isMediaSelectionActive,
    ) {
        searchBarVisible = true
    }

    MainStartupEffects(
        ctx = ctx,
        uiState = uiState,
        albumsLoading = albumsState.isLoading,
        onSetTypeFilter = mainVM::setTypeFilter,
        onInitializeDb = {
            mainVM.initializeDatabase {
                mainVM.triggerMediaRefresh()
                mainVM.synchronizeLibraryAfterInitialization()
            }
        },
        onContentReady = onContentReady,
    )

    val selectTabFromChrome: (Int) -> Unit = { tab ->
        val originTab = search.suspendedSearchOriginTab
        when {
            originTab < 0 -> mainVM.setSelectedTab(tab)
            tab == originTab -> {
                search.suspendedSearchOriginTab = -1
                if (!mainVM.navigateBackToPreviousTab()) {
                    mainVM.setSelectedTab(tab, addToHistory = false)
                }
            }
            else -> {
                search.close()
                mainVM.setSelectedTab(tab)
            }
        }
    }

    MainBackNavigation(
        uiState = uiState,
        searchActive = search.searchActive,
        suspendedSearchOriginTab = search.suspendedSearchOriginTab,
        isMediaSelectionActive = isMediaSelectionActive,
        explorerCanGoUp = explorerCanGoUp,
        onCloseSearch = search::close,
        onRestoreSuspendedSearch = { originTab ->
            search.suspendedSearchOriginTab = -1
            if (!mainVM.navigateBackToPreviousTab()) {
                mainVM.setSelectedTab(originTab, addToHistory = false)
            }
        },
        onClearFilters = {
            val backTab = uiState.filterReturnTab()
            mainVM.clearFilters()
            mainVM.setSelectedTab(backTab, addToHistory = false)
        },
        onNavigateBackTab = { mainVM.navigateBackToPreviousTab() },
        onFinish = onFinish,
    )

    val searchBarScrollConnection = rememberSearchBarScrollConnection { visible ->
        searchBarVisible = visible
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = !isMediaSelectionActive,
        modifier = modifier,
        drawerContent = {
            AppNavigationDrawer(
                selectedTab = uiState.selectedTab,
                duplicateScanFolder =
                    if (uiState.selectedTab == TAB_EXPLORER) uiState.explorerPath else "",
                allFilesAccessGranted = allFilesAccessGranted,
                onSelectTab = { tab ->
                    scope.launch { drawerState.close(); selectTabFromChrome(tab) }
                },
                onNavigate = { route ->
                    scope.launch {
                        drawerState.close()
                        navController.navigate(route) { launchSingleTop = true }
                    }
                },
                onOpenFolder = { path ->
                    scope.launch {
                        drawerState.close()
                        mainVM.setExplorerPath(path)
                        mainVM.setSelectedTab(TAB_EXPLORER)
                    }
                },
                onOpenCollection = { collection ->
                    scope.launch { drawerState.close(); applyCollection(collection, mainVM, ctx) }
                },
                onOpenTag = { tag ->
                    scope.launch { drawerState.close(); applyTagFilter(tag, mainVM, ctx, TAB_MEDIA) }
                },
                onOpenRating = { rating ->
                    scope.launch {
                        drawerState.close()
                        mainVM.clearFilters()
                        mainVM.setPreFilterTab(TAB_MEDIA)
                        mainVM.setRatingFilter(rating)
                        mainVM.setSelectedTab(TAB_MEDIA)
                    }
                },
                onRequestAllFilesAccess = {
                    scope.launch { drawerState.close(); showAllFilesPrompt = true }
                },
                onClose = { scope.launch { drawerState.close() } },
            )
        },
    ) {
        Row(Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = isWideWindow && !isMediaSelectionActive,
                enter = expandHorizontally() + fadeIn(),
                exit = shrinkHorizontally() + fadeOut(),
            ) {
                MainNavigationRail(
                    selectedTab = uiState.selectedTab,
                    onTabSelected = selectTabFromChrome,
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onOpenSettings = {
                        navController.navigate(Settings) { launchSingleTop = true }
                    },
                )
            }
            Scaffold(
                bottomBar = {
                    BottomChrome(
                        isMediaSelectionActive = isMediaSelectionActive,
                        searchActive = search.searchActive,
                        omniQuery = search.visibleBarQuery,
                        onQueryChange = search::updateActiveQuery,
                        searchFocusRequester = search.focusRequester,
                        onFocusChanged = search::updateFocus,
                        onClear = search::close,
                        onMenuClick = { scope.launch { drawerState.open() } },
                        onSearch = search::submit,
                        onSwipeUp = { activeSheet = ActiveSheet.VIEW_SETTINGS },
                        onOpenFilterAndView = {
                            activeSheet = if (uiState.selectedTab in FILTERABLE_TABS) {
                                ActiveSheet.FILTER
                            } else {
                                ActiveSheet.VIEW_SETTINGS
                            }
                        },
                        filtersAvailable = uiState.selectedTab in FILTERABLE_TABS,
                        hasActiveFilter = uiState.hasActiveFilter,
                        searchHintRes =
                            resolveSearchHintRes(uiState.selectedTab, search.effectiveLocalScope),
                        selectedTab = uiState.selectedTab,
                        onTabSelected = selectTabFromChrome,
                        showNavigation = !isWideWindow,
                        searchBarVisible = searchBarVisible,
                    )
                },
            ) { padding ->
                BoxWithConstraints(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .nestedScroll(searchBarScrollConnection),
                ) {
                    val searchPanelHeight = (maxHeight * SEARCH_PANEL_HEIGHT_RATIO)
                        .coerceAtLeast(minOf(maxHeight, SEARCH_PANEL_MIN_HEIGHT_DP.dp))
                    MainTabContent(
                        state = uiState,
                        tabSettings = tabSettings,
                        ctx = ctx,
                        navController = navController,
                        onMediaSelection = { isMediaSelectionActive = it },
                        onExplorerCanGoUp = { explorerCanGoUp = it },
                        searchResultBackPending = search.suspendedSearchOriginTab >= 0,
                        explorerNameFilter = search.explorerNameFilter,
                        onExplorerNameFilterChange = { query ->
                            search.updateExplorerNameFilter(uiState.explorerPath, query)
                        },
                        tabSearchQuery = search.visibleLocalTabQuery,
                        onClearTabSearch = search::close,
                        explorerSearchBackActive =
                            uiState.selectedTab == TAB_EXPLORER && search.searchActive,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onOpenNewMedia = {
                            val since = ctx.config.newMediaSeenAt
                            mainVM.setPreFilterTab(uiState.selectedTab)
                            mainVM.setNewMediaFilter(since)
                            mainVM.setSelectedTab(TAB_MEDIA)
                            ScrollToTopBus.trigger(TAB_MEDIA)
                        },
                        onMarkNewMediaRead = {
                            mainVM.markNewMediaSeen()
                            ctx.toast(R.string.new_media_marked_seen)
                        },
                        onOpenViewSettings = { activeSheet = ActiveSheet.VIEW_SETTINGS },
                    )
                    uiState.dbInitError?.let { errorMessage ->
                        MainDbErrorBanner(
                            errorMessage = errorMessage,
                            onRetry = {
                                mainVM.initializeDatabase {
                                    mainVM.triggerMediaRefresh()
                                    mainVM.synchronizeLibraryAfterInitialization()
                                }
                            },
                            modifier = Modifier.align(Alignment.TopCenter).padding(12.dp),
                        )
                    }
                    MainSearchOverlay(
                        search = search,
                        searchPanelHeight = searchPanelHeight,
                        uiState = uiState,
                        onNavigateFolder = { path ->
                            search.suspendForFolderNavigation(uiState.selectedTab)
                            mainVM.setExplorerPath(path)
                            mainVM.setSelectedTab(TAB_EXPLORER)
                        },
                        onRatingFilterChange = mainVM::setRatingFilter,
                        onToggleTag = mainVM::toggleTagFilter,
                        onTagMatchAllChange = mainVM::setTagMatchAll,
                        onTypeFilterChange = mainVM::setTypeFilter,
                        onDateFilterChange = mainVM::setDateRangeFilter,
                        onMinSizeFilterChange = mainVM::setMinSizeFilter,
                        onResetFilters = mainVM::clearFilters,
                        onEditFilters = { activeSheet = ActiveSheet.FILTER },
                        onApplyTagOnly = { tag ->
                            search.rememberOmniQuery()
                            applyTagFilter(tag, mainVM, ctx, uiState.selectedTab)
                        },
                        onApplyResults = { textQuery ->
                            search.rememberQuery(textQuery)
                            mainVM.clearFilters()
                            mainVM.setPreFilterTab(uiState.selectedTab)
                            mainVM.setTextQueryFilter(textQuery)
                            mainVM.setCollectionName(null)
                            mainVM.setSelectedTab(TAB_MEDIA)
                        },
                        onOpenCollection = { coll ->
                            applyCollection(coll, mainVM, ctx)
                            search.close()
                        },
                        onOpenMedia = { path ->
                            search.close()
                            navController.navigateToViewer(listOf(path), 0)
                        },
                    )
                    LibrarySyncIndicator(modifier = Modifier.align(Alignment.BottomCenter))
                }
            }
        }
    }

    MainSheets(
        activeSheet = activeSheet,
        selectedTab = uiState.selectedTab,
        collectionActive = uiState.activeCollectionName != null,
        tabSettings = tabSettings,
        settingsMode = settingsMode,
        navController = navController,
        currentScanFolder = if (uiState.selectedTab == TAB_EXPLORER) uiState.explorerPath else "",
        onDismissSheet = { activeSheet = null },
        ratingFilter = uiState.activeRatingFilter,
        onRatingFilterChange = mainVM::setRatingFilter,
        tagFilterNamesRaw = uiState.activeTagFilterRaw,
        onToggleTag = mainVM::toggleTagFilter,
        tagMatchAll = uiState.activeTagMatchAll,
        onTagMatchAllChange = mainVM::setTagMatchAll,
        minSizeFilter = uiState.activeMinSizeFilter,
        onMinSizeFilterChange = mainVM::setMinSizeFilter,
        dateRangeFilter = uiState.activeDateRangeFilter,
        onDateRangeFilterChange = mainVM::setDateRangeFilter,
        typeFilter = uiState.activeTypeFilter,
        onTypeFilterChange = mainVM::setTypeFilter,
        reviewFilter = uiState.activeReviewFilter,
        onReviewFilterChange = {
            mainVM.setUndecidedReviewFilter(it == MediaReviewFilter.UNDECIDED)
        },
        hasActiveFilter = uiState.hasActiveFilter,
        onClearAll = mainVM::clearFilters,
    )

    if (showAllFilesPrompt) {
        AllFilesAccessSheet(
            onAllow = {
                showAllFilesPrompt = false
                launchAllFilesSettings(ctx) { intent -> allFilesLauncher.launch(intent) }
            },
            onLater = { showAllFilesPrompt = false },
        )
    }
}

@Stable
private class MainSearchState(
    private val ctx: Context,
    private val focusManager: FocusManager,
    private val keyboard: SoftwareKeyboardController?,
    val focusRequester: FocusRequester,
    val resultListState: LazyListState,
) {
    var selectedTab by mutableIntStateOf(TAB_MEDIA)
    var explorerPath by mutableStateOf("")
    var localScopeLabel by mutableStateOf<String?>(null)
    var omniQuery by mutableStateOf("")
    var tabSearchQuery by mutableStateOf("")
    var explorerNameFilter by mutableStateOf("")
    var explorerNameFilterPath by mutableStateOf("")
    var searchLocalScope by mutableStateOf(false)
    var searchFocused by mutableStateOf(false)
    var suspendedSearchOriginTab by mutableIntStateOf(-1)
    var recentSearches by mutableStateOf(ctx.config.recentSearches)

    val effectiveLocalScope: Boolean
        get() = localScopeLabel != null && searchLocalScope

    val activeQuery: String
        get() = when {
            effectiveLocalScope && selectedTab == TAB_EXPLORER -> explorerNameFilter
            effectiveLocalScope -> tabSearchQuery
            else -> omniQuery
        }

    val visibleBarQuery: String
        get() = if (suspendedSearchOriginTab >= 0) "" else activeQuery

    val visibleLocalTabQuery: String
        get() = if (effectiveLocalScope) tabSearchQuery else ""

    val searchActive: Boolean
        get() = suspendedSearchOriginTab < 0 && (searchFocused || activeQuery.isNotBlank())

    fun updateActiveQuery(query: String) {
        if (suspendedSearchOriginTab >= 0) suspendedSearchOriginTab = -1
        when {
            effectiveLocalScope && selectedTab == TAB_EXPLORER -> {
                explorerNameFilterPath = explorerPath
                explorerNameFilter = query
            }
            effectiveLocalScope -> tabSearchQuery = query
            else -> omniQuery = query
        }
    }

    fun updateExplorerNameFilter(path: String, query: String) {
        explorerNameFilterPath = path
        explorerNameFilter = query
    }

    fun updateFocus(focused: Boolean) {
        if (focused && suspendedSearchOriginTab >= 0) {
            suspendedSearchOriginTab = -1
            omniQuery = ""
        }
        searchFocused = focused
    }

    fun switchScope(toLocal: Boolean) {
        if (toLocal == searchLocalScope) return
        if (toLocal) {
            if (selectedTab == TAB_EXPLORER) {
                explorerNameFilterPath = explorerPath
                explorerNameFilter = omniQuery
            } else {
                tabSearchQuery = omniQuery
            }
            omniQuery = ""
        } else {
            omniQuery = if (selectedTab == TAB_EXPLORER) explorerNameFilter else tabSearchQuery
            if (selectedTab == TAB_EXPLORER) explorerNameFilter = "" else tabSearchQuery = ""
        }
        searchLocalScope = toLocal
    }

    fun close() {
        when {
            effectiveLocalScope && selectedTab == TAB_EXPLORER -> explorerNameFilter = ""
            effectiveLocalScope -> tabSearchQuery = ""
            else -> omniQuery = ""
        }
        searchFocused = false
        suspendedSearchOriginTab = -1
        focusManager.clearFocus()
        keyboard?.hide()
    }

    fun submit() {
        if (!effectiveLocalScope) rememberQuery(omniQuery)
        keyboard?.hide()
    }

    fun suspendForFolderNavigation(originTab: Int) {
        rememberQuery(omniQuery)
        suspendedSearchOriginTab = originTab
        searchFocused = false
        focusManager.clearFocus()
        keyboard?.hide()
    }

    fun rememberOmniQuery() = rememberQuery(omniQuery)

    fun rememberQuery(query: String) {
        ctx.config.rememberSearch(query)
        recentSearches = ctx.config.recentSearches
    }

    fun clearRecentSearches() {
        ctx.config.clearRecentSearches()
        recentSearches = emptyList()
    }
}

@Composable
private fun rememberMainSearchState(
    uiState: ExplorerUiState,
    ctx: Context,
): MainSearchState {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val resultListState = rememberLazyListState()
    val state = remember(ctx, focusManager, keyboard, focusRequester, resultListState) {
        MainSearchState(ctx, focusManager, keyboard, focusRequester, resultListState)
    }
    state.selectedTab = uiState.selectedTab
    state.explorerPath = uiState.explorerPath
    state.localScopeLabel = resolveLocalScopeLabel(uiState.selectedTab)

    var lastTab by rememberSaveable { mutableIntStateOf(uiState.selectedTab) }
    LaunchedEffect(uiState.selectedTab) {
        if (lastTab != uiState.selectedTab) {
            lastTab = uiState.selectedTab
            state.searchLocalScope = uiState.selectedTab == TAB_EXPLORER
            state.tabSearchQuery = ""
            state.searchFocused = false
            focusManager.clearFocus()
            keyboard?.hide()
        }
    }
    LaunchedEffect(uiState.explorerPath) {
        if (state.explorerNameFilterPath != uiState.explorerPath) {
            state.explorerNameFilter = ""
            state.explorerNameFilterPath = uiState.explorerPath
        }
    }
    return state
}

@Composable
private fun MainStartupEffects(
    ctx: Context,
    uiState: ExplorerUiState,
    albumsLoading: Boolean,
    onSetTypeFilter: (Int) -> Unit,
    onInitializeDb: () -> Unit,
    onContentReady: () -> Unit,
) {
    val latestOnContentReady by rememberUpdatedState(onContentReady)
    val latestOnSetTypeFilter by rememberUpdatedState(onSetTypeFilter)
    val latestOnInitializeDb by rememberUpdatedState(onInitializeDb)
    val dbReady = uiState.dbInitialized || uiState.dbInitError != null
    val isStartContentReady = dbReady && (uiState.selectedTab != TAB_ALBUMS || !albumsLoading)
    ReportDrawnWhen { isStartContentReady }
    LaunchedEffect(isStartContentReady) {
        if (isStartContentReady) latestOnContentReady()
    }
    LaunchedEffect(Unit) {
        val act = ctx as? Activity
        if (act != null && PickIntent.isPick(act.intent)) {
            when (PickIntent.requiredKind(act.intent)) {
                PICK_KIND_IMAGES -> latestOnSetTypeFilter(PICK_KIND_IMAGES)
                PICK_KIND_VIDEOS -> latestOnSetTypeFilter(PICK_KIND_VIDEOS)
            }
        }
    }
    LaunchedEffect(Unit) {
        latestOnInitializeDb()
    }
}

@Composable
private fun MainBackNavigation(
    uiState: ExplorerUiState,
    searchActive: Boolean,
    suspendedSearchOriginTab: Int,
    isMediaSelectionActive: Boolean,
    explorerCanGoUp: Boolean,
    onCloseSearch: () -> Unit,
    onRestoreSuspendedSearch: (Int) -> Unit,
    onClearFilters: () -> Unit,
    onNavigateBackTab: () -> Unit,
    onFinish: () -> Unit,
) {
    val canFallbackTabSwitch = uiState.selectedTab != TAB_ALBUMS &&
        !isMediaSelectionActive &&
        !(uiState.selectedTab == TAB_EXPLORER && explorerCanGoUp)
    val hasBackDestination = uiState.hasActiveFilter ||
        searchActive ||
        suspendedSearchOriginTab >= 0 ||
        uiState.tabHistory.isNotEmpty() ||
        canFallbackTabSwitch
    val mainBackState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
    NavigationBackHandler(
        state = mainBackState,
        isBackEnabled = hasBackDestination,
        onBackCompleted = {
            when {
                searchActive -> onCloseSearch()
                suspendedSearchOriginTab >= 0 -> onRestoreSuspendedSearch(suspendedSearchOriginTab)
                uiState.hasActiveFilter -> onClearFilters()
                uiState.tabHistory.isNotEmpty() -> onNavigateBackTab()
                else -> onFinish()
            }
        },
    )
}

@Composable
private fun rememberSearchBarScrollConnection(
    onVisibilityChange: (Boolean) -> Unit,
): NestedScrollConnection {
    val density = LocalDensity.current
    val latestOnVisibilityChange by rememberUpdatedState(onVisibilityChange)
    return remember(density) {
        val hideThresholdPx = with(density) { SEARCH_BAR_HIDE_THRESHOLD_DP.dp.toPx() }
        val showThresholdPx = with(density) { SEARCH_BAR_SHOW_THRESHOLD_DP.dp.toPx() }
        object : NestedScrollConnection {
            private var accumulatedDy = 0f
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                when {
                    consumed.y < -SCROLL_MIN_DELTA -> {
                        accumulatedDy =
                            if (accumulatedDy > 0f) consumed.y else accumulatedDy + consumed.y
                        if (accumulatedDy <= -hideThresholdPx) {
                            latestOnVisibilityChange(false)
                            accumulatedDy = 0f
                        }
                    }
                    consumed.y > SCROLL_MIN_DELTA -> {
                        accumulatedDy =
                            if (accumulatedDy < 0f) consumed.y else accumulatedDy + consumed.y
                        if (accumulatedDy >= showThresholdPx) {
                            latestOnVisibilityChange(true)
                            accumulatedDy = 0f
                        }
                    }
                    available.y > OVERSCROLL_REVEAL_PX && consumed.y == 0f -> {
                        latestOnVisibilityChange(true)
                        accumulatedDy = 0f
                    }
                }
                return Offset.Zero
            }
        }
    }
}

@Composable
private fun MainDbErrorBanner(
    errorMessage: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.error_generic, errorMessage),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.retry))
            }
        }
    }
}

@Composable
private fun BoxScope.MainSearchOverlay(
    search: MainSearchState,
    searchPanelHeight: Dp,
    uiState: ExplorerUiState,
    onNavigateFolder: (String) -> Unit,
    onRatingFilterChange: (Int) -> Unit,
    onToggleTag: (String) -> Unit,
    onTagMatchAllChange: (Boolean) -> Unit,
    onTypeFilterChange: (Int) -> Unit,
    onDateFilterChange: (Int) -> Unit,
    onMinSizeFilterChange: (Long) -> Unit,
    onResetFilters: () -> Unit,
    onEditFilters: () -> Unit,
    onApplyTagOnly: (String) -> Unit,
    onApplyResults: (String) -> Unit,
    onOpenCollection: (org.fossify.gallery.models.MediaCollection) -> Unit,
    onOpenMedia: (String) -> Unit,
) {
    AnimatedVisibility(
        visible = search.searchActive && !search.effectiveLocalScope,
        modifier = Modifier.align(Alignment.BottomCenter).height(searchPanelHeight),
        enter = slideInVertically(
            initialOffsetY = { it / SEARCH_SLIDE_DIVISOR },
            animationSpec = tween(SEARCH_ENTER_SLIDE_MS),
        ) + fadeIn(tween(SEARCH_ENTER_FADE_MS)),
        exit = slideOutVertically(
            targetOffsetY = { it / SEARCH_SLIDE_DIVISOR },
            animationSpec = tween(SEARCH_EXIT_SLIDE_MS),
        ) + fadeOut(tween(SEARCH_EXIT_FADE_MS)),
    ) {
        OmniSearchPanel(
            modifier = Modifier.fillMaxSize(),
            query = search.omniQuery,
            onQueryChange = { search.omniQuery = it },
            resultListState = search.resultListState,
            recentSearches = search.recentSearches,
            onUseRecentSearch = { search.omniQuery = it },
            onClearRecentSearches = search::clearRecentSearches,
            onDismiss = search::close,
            storagePath = Environment.getExternalStorageDirectory().absolutePath,
            onNavigate = onNavigateFolder,
            ratingFilter = uiState.activeRatingFilter,
            onRatingFilterChange = onRatingFilterChange,
            selectedTagNames = uiState.activeTagFilterRaw,
            onToggleTag = onToggleTag,
            tagMatchAll = uiState.activeTagMatchAll,
            onTagMatchAllChange = onTagMatchAllChange,
            typeFilter = uiState.activeTypeFilter,
            onTypeFilterChange = onTypeFilterChange,
            dateFilter = uiState.activeDateRangeFilter,
            onDateFilterChange = onDateFilterChange,
            minSizeFilter = uiState.activeMinSizeFilter,
            onMinSizeFilterChange = onMinSizeFilterChange,
            onResetFilters = onResetFilters,
            onEditFilters = onEditFilters,
            onApplyTagOnly = onApplyTagOnly,
            onApplyResults = onApplyResults,
            onOpenCollection = onOpenCollection,
            onOpenFavorite = onOpenMedia,
            onOpenMedia = onOpenMedia,
            localScopeLabel = search.localScopeLabel,
            onSwitchToLocalScope = search.localScopeLabel?.let { { search.switchScope(true) } },
        )
    }
    AnimatedVisibility(
        visible = search.searchActive && search.effectiveLocalScope,
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
        enter = fadeIn(tween(SEARCH_ENTER_FADE_MS)),
        exit = fadeOut(tween(SEARCH_EXIT_FADE_MS)),
    ) {
        search.localScopeLabel?.let { label ->
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 3.dp,
            ) {
                SearchScopeBar(
                    isLocal = true,
                    localLabel = label,
                    onSelectEverywhere = { search.switchScope(false) },
                    onSelectLocal = {},
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun resolveLocalScopeLabel(selectedTab: Int): String? = when (selectedTab) {
    TAB_ALBUMS -> stringResource(R.string.search_scope_current_tab, stringResource(R.string.albums))
    TAB_EXPLORER -> stringResource(R.string.search_scope_current_folder)
    TAB_COLLECTIONS ->
        stringResource(R.string.search_scope_current_tab, stringResource(R.string.nav_collections))
    TAB_FAVORITES ->
        stringResource(R.string.search_scope_current_tab, stringResource(R.string.nav_favorites))
    TAB_TAGS -> stringResource(R.string.search_scope_current_tab, stringResource(R.string.nav_tags))
    else -> null
}

private fun resolveSearchHintRes(selectedTab: Int, effectiveLocalScope: Boolean): Int =
    if (!effectiveLocalScope) {
        R.string.search_hint_compact
    } else {
        when (selectedTab) {
            TAB_ALBUMS -> R.string.search_folders_hint
            TAB_EXPLORER -> R.string.explorer_name_filter_hint
            TAB_COLLECTIONS -> R.string.search_collections
            TAB_TAGS -> R.string.search_tag
            else -> R.string.search_hint_compact
        }
    }

private fun launchAllFilesSettings(ctx: Context, launch: (Intent) -> Unit) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
    val appSpecific = Intent(
        AndroidSettings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
        Uri.parse("package:${ctx.packageName}"),
    )
    try {
        launch(appSpecific)
    } catch (_: ActivityNotFoundException) {
        runCatching {
            launch(Intent(AndroidSettings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
        }
    }
}

package org.fossify.gallery.compose.screens.folderscreen
import androidx.compose.ui.res.stringResource
import org.fossify.gallery.R

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import org.fossify.gallery.compose.screens.MediaScreen
import org.fossify.gallery.compose.screens.MediaSkeleton
import org.fossify.gallery.compose.screens.ViewSettingsSheet
import org.fossify.gallery.compose.screens.ViewSettingsViewModel
import org.fossify.gallery.compose.components.LoadErrorBanner
import org.fossify.gallery.compose.components.LoadErrorState
import org.fossify.gallery.compose.components.InlineRefreshIndicator
import org.fossify.gallery.compose.components.FilterSheetContent
import org.fossify.gallery.compose.components.LibrarySearchField
import org.fossify.gallery.compose.components.viewSettingsSummary
import org.fossify.gallery.compose.theme.AppMotion
import org.fossify.gallery.compose.theme.LocalMediaRepository
import org.fossify.gallery.models.Medium
import org.fossify.gallery.models.MediaReviewFilter
import java.io.File

// Process-level cache of the last scan per folder, kept alive across this composable's disposal
// (see MediaStoreOps for the analogous full-device cache and why a plain remember{} isn't enough).
// ConcurrentHashMap since it's written from an IO-dispatcher coroutine and read from Compose (main).
private data class FolderMediaCacheEntry(val items: List<Medium>, val generation: Long)

private val folderMediaCache = java.util.concurrent.ConcurrentHashMap<String, FolderMediaCacheEntry>()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderMediaScreen(
    folderPath: String,
    onBack: () -> Unit,
    onNavigateToViewer: (List<String>, Int) -> Unit = { _, _ -> },
    onOpenSwipeMode: (String, String?) -> Unit = { _, _ -> },
    onOpenPersonReview: (String) -> Unit = {},
) {
    val viewSettingsVM: ViewSettingsViewModel = viewModel()
    val ctx = LocalContext.current
    val repo = LocalMediaRepository.current
    val tabSettings by viewSettingsVM.settings.collectAsStateWithLifecycle()
    // Seed from the process-level cache instead of null - FolderMediaScreen is `composable<Folder>`,
    // a sibling destination that gets disposed too whenever Viewer is pushed on top of it, and without
    // this the directory-stream scan reran from disk every time the user opened a photo and came back.
    val cachedFolder = remember(folderPath) { folderMediaCache[folderPath] }
    var mediaItems by remember(folderPath) { mutableStateOf(cachedFolder?.items) }
    var cacheGeneration by remember(folderPath) { mutableStateOf(cachedFolder?.generation ?: -1L) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var showViewSettings by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    var showFolderActions by remember { mutableStateOf(false) }
    var searchOpen by remember(folderPath) { mutableStateOf(false) }
    var searchQuery by remember(folderPath) { mutableStateOf("") }
    var ratingFilter by remember(folderPath) { mutableIntStateOf(0) }
    var selectedTags by remember(folderPath) { mutableStateOf<Set<String>>(emptySet()) }
    var tagMatchAll by remember(folderPath) { mutableStateOf(false) }
    var minSizeFilter by remember(folderPath) { mutableLongStateOf(0L) }
    var dateRangeFilter by remember(folderPath) { mutableIntStateOf(0) }
    var typeFilter by remember(folderPath) { mutableIntStateOf(0) }
    var reviewFilter by remember(folderPath) { mutableStateOf(MediaReviewFilter.NONE) }
    var firstVisibleMediaPath by remember(folderPath) { mutableStateOf<String?>(null) }
    // Resolves to this folder's own pinned settings if the user saved one via the "Einstellung
    // global übernehmen" toggle, else falls back to the tab-wide default - re-resolved fresh
    // whenever a *different* folder is opened (composable<Folder> gives each folder its own
    // instance, so folderPath is stable for the screen's lifetime).
    var viewSettings by remember(folderPath) { mutableStateOf(viewSettingsVM.getFolderMediaSettingsForPath(folderPath)) }
    // Keeps tracking the global default live if this folder has no custom override - matches the
    // legacy Views-based getFolderGrouping/getFolderSorting fallback-to-global behavior.
    LaunchedEffect(tabSettings.folderMedia, folderPath) {
        if (!viewSettingsVM.hasCustomFolderMediaSettings(folderPath)) viewSettings = tabSettings.folderMedia
    }

    // Single source of truth: the same `media` DB table the Media tab pages over (getMediaFromPath =
    // WHERE deleted_ts = 0 AND parent_path = folderPath, parent_path-indexed). MediaScreen's override
    // path then sorts it by date_sort_key via applySort, so a drilled-into album shows exactly the
    // same media, in exactly the same order, as the Media tab - instead of the old direct-disk scan
    // that sorted by file mtime and could momentarily diverge from the synced DB.
    suspend fun reload() {
        val generationAtStart = org.fossify.gallery.helpers.RefreshBus.generation
        val hadContent = mediaItems != null
        if (hadContent) isRefreshing = true
        try {
            val loaded = withContext(Dispatchers.IO) { repo.getMediaFromPath(folderPath) }
            mediaItems = loaded
            cacheGeneration = generationAtStart
            folderMediaCache[folderPath] = FolderMediaCacheEntry(loaded, generationAtStart)
            loadError = null
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            loadError = e.message
        } finally {
            isRefreshing = false
        }
    }
    LaunchedEffect(folderPath) {
        if (mediaItems == null || cacheGeneration != org.fossify.gallery.helpers.RefreshBus.generation) reload()
    }
    LaunchedEffect(Unit) {
        merge(
            org.fossify.gallery.helpers.RefreshBus.pathEvents
                .filter { affected -> affected == null || affected.any { path ->
                    path == folderPath || path.startsWith("$folderPath/") || folderPath.startsWith("$path/")
                } }
                .map { Unit },
            org.fossify.gallery.helpers.RefreshBus.metadataEvents,
        ).collectLatest { reload() }
    }

    val activeTagLabel = selectedTags.takeIf { it.isNotEmpty() }?.joinToString(", ")
    val hasActiveFilter = ratingFilter > 0 || selectedTags.isNotEmpty() || minSizeFilter > 0L ||
        dateRangeFilter > 0 || typeFilter > 0 || reviewFilter != MediaReviewFilter.NONE
    fun clearFilters() {
        ratingFilter = 0
        selectedTags = emptySet()
        tagMatchAll = false
        minSizeFilter = 0L
        dateRangeFilter = 0
        typeFilter = 0
        reviewFilter = MediaReviewFilter.NONE
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(File(folderPath).name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                        Text(
                            viewSettingsSummary(viewSettings),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back)) } },
                actions = {
                    IconButton(onClick = {
                        if (searchOpen) searchQuery = ""
                        searchOpen = !searchOpen
                    }) {
                        Icon(
                            if (searchOpen) Icons.Default.Close else Icons.Default.Search,
                            stringResource(if (searchOpen) R.string.cd_clear_search else R.string.cd_search),
                        )
                    }
                    IconButton(onClick = { showFilters = true }) {
                        Icon(
                            Icons.Default.FilterList,
                            stringResource(R.string.filter_label),
                            tint = if (hasActiveFilter) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { showViewSettings = true }) {
                        Icon(Icons.Default.Tune, stringResource(R.string.view_settings_title))
                    }
                    // Keep immediate browsing controls visible. Review, people analysis and a
                    // manual refresh are useful but occasional, and a row of six equal icons made
                    // it needlessly hard to scan the top bar on a phone.
                    Box {
                        IconButton(onClick = { showFolderActions = true }) {
                            Icon(Icons.Default.MoreVert, stringResource(R.string.more_actions))
                        }
                        DropdownMenu(
                            expanded = showFolderActions,
                            onDismissRequest = { showFolderActions = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.swipe_mode_open)) },
                                leadingIcon = { Icon(Icons.Default.TouchApp, contentDescription = null) },
                                onClick = {
                                    showFolderActions = false
                                    onOpenSwipeMode(folderPath, firstVisibleMediaPath)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.person_scan_open)) },
                                leadingIcon = { Icon(Icons.Default.PersonSearch, contentDescription = null) },
                                onClick = {
                                    showFolderActions = false
                                    onOpenPersonReview(folderPath)
                                },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_refresh)) },
                                leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                                enabled = !isRefreshing,
                                onClick = {
                                    showFolderActions = false
                                    scope.launch { reload() }
                                },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (searchOpen) {
                LibrarySearchField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholderRes = R.string.search_hint_compact,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            val items = mediaItems
            // Crossfade + shimmer skeleton instead of a bare spinner - this is the same "media grid is
            // loading" moment as Media/Albums/Explorer, so it should look the same, not like a
            // different, less-polished screen.
            val contentState = when {
                items != null -> "content"
                loadError != null -> "error"
                else -> "loading"
            }
            androidx.compose.animation.Crossfade(targetState = contentState, animationSpec = AppMotion.short, label = "folderMediaLoading") { state ->
                if (state == "loading") {
                    MediaSkeleton(
                        columns = viewSettings.columnCount,
                        viewType = viewSettings.viewType,
                        itemSpacing = viewSettings.spacing.dp,
                        showFileName = viewSettings.showFileNames,
                        reserveGridCaptionSpace = viewSettings.viewType == org.fossify.gallery.compose.screens.ViewType.GRID,
                        listDensity = viewSettings.listDensity,
                    )
                } else if (state == "error") {
                    LoadErrorState(message = loadError, onRetry = { scope.launch { reload() } })
                } else {
                    androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
                        if (loadError != null) LoadErrorBanner(message = loadError, onRetry = { scope.launch { reload() } })
                        MediaScreen(
                            viewSettings = viewSettings,
                            ratingFilter = ratingFilter,
                            tagFilterNames = selectedTags.takeIf { it.isNotEmpty() },
                            tagMatchAll = tagMatchAll,
                            activeTagName = activeTagLabel,
                            minSizeFilter = minSizeFilter,
                            dateRangeFilter = dateRangeFilter,
                            typeFilter = typeFilter,
                            reviewFilter = reviewFilter,
                            textQuery = searchQuery,
                            onClearFilter = ::clearFilters,
                            onClearRatingFilter = { ratingFilter = 0 },
                            onClearTagFilter = { selectedTags = emptySet(); tagMatchAll = false },
                            onRemoveTagFilter = { tag ->
                                selectedTags = selectedTags.filterNot { it.equals(tag, ignoreCase = true) }.toSet()
                                if (selectedTags.isEmpty()) tagMatchAll = false
                            },
                            onClearTagMatchAll = { tagMatchAll = false },
                            onClearSizeFilter = { minSizeFilter = 0L },
                            onClearDateFilter = { dateRangeFilter = 0 },
                            onClearTypeFilter = { typeFilter = 0 },
                            onClearReviewFilter = { reviewFilter = MediaReviewFilter.NONE },
                            mediaOverride = items ?: emptyList(),
                            scrollPositionKey = folderPath,
                            onNavigateToViewer = onNavigateToViewer,
                            onFirstVisibleMediaPathChanged = { firstVisibleMediaPath = it },
                            onColumnCountChange = { next ->
                                val updated = viewSettings.copy(columnCount = next)
                                val applyGlobally = !viewSettingsVM.hasCustomFolderMediaSettings(folderPath)
                                viewSettingsVM.updateFolderMediaForPath(folderPath, updated, applyGlobally)
                                viewSettings = updated
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            InlineRefreshIndicator(
                visible = isRefreshing,
                contentAvailable = items != null,
                modifier = Modifier.align(androidx.compose.ui.Alignment.TopCenter),
            )
        }
        }
    }

    if (showFilters) {
        ModalBottomSheet(
            onDismissRequest = { showFilters = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            FilterSheetContent(
                ratingFilter = ratingFilter,
                onRatingChange = { ratingFilter = it },
                selectedTagNames = selectedTags,
                            onToggleTag = { tag ->
                                val next = if (tag in selectedTags) selectedTags - tag else selectedTags + tag
                                selectedTags = next
                                if (next.size < 2) tagMatchAll = false
                            },
                tagMatchAll = tagMatchAll,
                onTagMatchAllChange = { tagMatchAll = it },
                minSizeFilter = minSizeFilter,
                onMinSizeChange = { minSizeFilter = it },
                dateRangeFilter = dateRangeFilter,
                onDateRangeChange = { dateRangeFilter = it },
                typeFilter = typeFilter,
                onTypeFilterChange = { typeFilter = it },
                reviewFilter = reviewFilter,
                onReviewFilterChange = { reviewFilter = it },
                hasActiveFilter = hasActiveFilter,
                onClearAll = ::clearFilters,
                onDismiss = { showFilters = false },
            )
        }
    }

    if (showViewSettings) {
        ViewSettingsSheet(
            settings = viewSettings,
            showDisplayMode = true,
            scopeDescription = stringResource(R.string.view_settings_scope_description),
            showApplyGloballyToggle = true,
            initialApplyGlobally = !viewSettingsVM.hasCustomFolderMediaSettings(folderPath),
            onSettingsChange = { s, applyGlobally -> viewSettingsVM.updateFolderMediaForPath(folderPath, s, applyGlobally); viewSettings = s },
            onDismiss = { showViewSettings = false }
        )
    }
}

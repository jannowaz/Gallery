package org.fossify.gallery.activities

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Environment
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.ShortNavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.fossify.commons.extensions.handleLockedFolderOpening
import org.fossify.gallery.R
import org.fossify.gallery.compose.components.BottomSearchField
import org.fossify.gallery.compose.theme.LocalMediaRepository
import org.fossify.gallery.compose.theme.LocalSpacing
import org.fossify.gallery.compose.theme.RatingStarColor
import org.fossify.gallery.compose.util.ScrollToTopBus
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.LibraryActivityKind
import org.fossify.gallery.helpers.LibraryActivityPhase
import org.fossify.gallery.helpers.LibraryActivityStatus
import org.fossify.gallery.helpers.LibrarySyncPhase
import org.fossify.gallery.helpers.LibrarySyncStatus
import org.fossify.gallery.helpers.RefreshBus
import org.fossify.gallery.helpers.UndoManager
import org.fossify.gallery.helpers.expandTagsWithDescendants
import org.fossify.gallery.helpers.resolveContentUriToPath
import org.fossify.gallery.models.MediaCollection
import org.fossify.gallery.navigation.CleanupHub
import org.fossify.gallery.navigation.FoldersMover
import org.fossify.gallery.navigation.ManageCollections
import org.fossify.gallery.navigation.RecycleBin
import org.fossify.gallery.navigation.Settings
import org.fossify.gallery.viewmodels.ExplorerViewModel
import org.fossify.gallery.workers.MetadataSyncWorker
import org.fossify.gallery.workers.PersonScanWorker
import java.io.File

internal enum class MediaPermissionState(val hasMediaAccess: Boolean) {
    FULL(true),
    SELECTED(true),
    NONE(false),
    SETTINGS(false),
}

@Composable
internal fun MediaPermissionGate(
    state: MediaPermissionState,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Default.CollectionsBookmark,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp),
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(if (state == MediaPermissionState.SETTINGS) R.string.media_access_settings_title else R.string.media_access_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(
                    when (state) {
                        MediaPermissionState.SELECTED -> R.string.media_access_selected_body
                        MediaPermissionState.SETTINGS -> R.string.media_access_settings_body
                        else -> R.string.media_access_body
                    },
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = if (state == MediaPermissionState.SETTINGS) onOpenSettings else onRequest) {
                Text(stringResource(if (state == MediaPermissionState.SETTINGS) R.string.open_settings else R.string.allow_access))
            }
            if (state == MediaPermissionState.SELECTED) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onRequest) {
                    Text(stringResource(R.string.expand_media_selection))
                }
            }
        }
    }
}

internal data class NavTab(val index: Int, @StringRes val labelRes: Int, val icon: ImageVector)

internal val navTabs = listOf(
    NavTab(0, R.string.media, Icons.Default.Image),
    NavTab(1, R.string.albums, Icons.Default.Folder),
    NavTab(2, R.string.explorer, Icons.Default.FolderOpen),
    NavTab(3, R.string.nav_collections, Icons.Default.CollectionsBookmark),
    NavTab(4, R.string.nav_favorites, Icons.Default.Star),
    NavTab(5, R.string.nav_tags, Icons.AutoMirrored.Filled.Label),
)

// Collections got its own bottom-bar tab per explicit request; Tags stays drawer-only since it's
// set-up-once/occasional rather than part of the daily browse loop.
internal val bottomNavTabs = listOf(navTabs[0], navTabs[1], navTabs[2], navTabs[3], navTabs[4])

/** Applies the same single-tag filter behavior from every surface that exposes a tag chip. */
internal fun applyTagFilter(tag: String, mainVM: ExplorerViewModel, ctx: Context, originTab: Int) {
    mainVM.clearFilters()
    mainVM.setPreFilterTab(originTab)
    mainVM.setTagMatchAll(false)
    mainVM.setTagFilter(expandTagsWithDescendants(setOf(tag), ctx.config.tagHierarchy), tag)
    // Keep a tag tapped inside Explorer in Explorer so the same filter can be inspected in the
    // current folder. Tags tapped in Media/Favorites continue to open the global Media result.
    mainVM.setSelectedTab(if (originTab == 2) 2 else 0)
}

/** Applies a collection's include/exclude/tag/rating filters and jumps to the media tab. */
internal fun applyCollection(
    coll: MediaCollection,
    mainVM: ExplorerViewModel,
    ctx: Context,
) {
    mainVM.clearFilters()
    mainVM.setPreFilterTab(3)
    mainVM.setCollectionName(coll.name)
    mainVM.setCollectionMediaPaths(coll.getMediaPaths().toSet())
    mainVM.setRatingFilter(coll.ratingFilter)
    if (coll.tagFilter.isNotBlank()) {
        val tagNames = coll.tagFilter.split(",").map { it.trim() }.filter { it.isNotBlank() }
        val expandedTagNames = expandTagsWithDescendants(tagNames, ctx.config.tagHierarchy)
        mainVM.setTagFilter(expandedTagNames.ifEmpty { null }, coll.tagFilter.takeIf { it.isNotBlank() })
    } else {
        mainVM.setTagFilter(null, null)
    }
    val included = coll.getIncludedPaths()
    val excluded = coll.getExcludedPaths()
    val incPaths = included.mapNotNull { resolveContentUriToPath(it) }.filter { it.isNotEmpty() }.toSet()
    val excPaths = excluded.mapNotNull { resolveContentUriToPath(it) }.filter { it.isNotEmpty() }.toSet()
    mainVM.setPathFilter(incPaths.ifEmpty { null })
    mainVM.setExcludePathFilter(excPaths.ifEmpty { null })
    mainVM.setMinSizeFilter(coll.minSize)
    mainVM.setDateRangeFilter(coll.dateRange)
    mainVM.setTypeFilter(coll.typeFilter)
    mainVM.setSelectedTab(0)
}

@Composable
internal fun AppNavigationDrawer(
    selectedTab: Int,
    duplicateScanFolder: String,
    allFilesAccessGranted: Boolean,
    onSelectTab: (Int) -> Unit,
    onNavigate: (Any) -> Unit,
    onOpenFolder: (String) -> Unit,
    onOpenCollection: (MediaCollection) -> Unit,
    onOpenTag: (String) -> Unit,
    onOpenRating: (Int) -> Unit,
    onRequestAllFilesAccess: () -> Unit,
    onClose: () -> Unit,
) {
    val ctx = LocalContext.current
    val repo = LocalMediaRepository.current
    val s = LocalSpacing.current
    var quickAccessVersion by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { RefreshBus.events.collect { quickAccessVersion++ } }
    val pinnedFolderPaths = remember(quickAccessVersion) { ctx.config.pinnedFavoriteFolders.sortedBy { File(it).name.lowercase() } }
    val pinnedTagNames = remember(quickAccessVersion) { ctx.config.pinnedTags.sortedBy(String::lowercase) }
    val pinnedRatingValues = remember(quickAccessVersion) { ctx.config.pinnedRatings.sortedDescending() }
    val pinnedCollectionItems = remember(quickAccessVersion) {
        val ids = ctx.config.pinnedCollections
        repo.getCollectionsCached().orEmpty().filter { it.id.toString() in ids }.sortedBy { it.name.lowercase() }
    }
    val hasQuickAccess = pinnedFolderPaths.isNotEmpty() || pinnedTagNames.isNotEmpty() || pinnedRatingValues.isNotEmpty() || pinnedCollectionItems.isNotEmpty()
    val screenWidth = LocalConfiguration.current.screenWidthDp
    val drawerWidth = (screenWidth - 32).coerceIn(240, 320).dp
    ModalDrawerSheet(modifier = Modifier.width(drawerWidth)) {
        Column(
            Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(vertical = s.sm),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 28.dp, end = s.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.nav_more), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, stringResource(R.string.cd_close)) }
            }
            if (hasQuickAccess) {
                Text(stringResource(R.string.quick_access), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 28.dp, vertical = s.sm))
                pinnedFolderPaths.forEach { path ->
                    NavigationDrawerItem(label = { Text(File(path).name.ifBlank { path }, maxLines = 1, overflow = TextOverflow.Ellipsis) }, icon = { Icon(Icons.Default.FolderOpen, null) }, selected = false, onClick = { onOpenFolder(path) }, modifier = Modifier.padding(horizontal = s.md))
                }
                pinnedCollectionItems.forEach { collection ->
                    NavigationDrawerItem(label = { Text(collection.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }, icon = { Icon(Icons.Default.CollectionsBookmark, null) }, selected = false, onClick = { onOpenCollection(collection) }, modifier = Modifier.padding(horizontal = s.md))
                }
                pinnedTagNames.forEach { tag ->
                    NavigationDrawerItem(label = { Text(tag, maxLines = 1, overflow = TextOverflow.Ellipsis) }, icon = { Icon(Icons.AutoMirrored.Filled.Label, null) }, selected = false, onClick = { onOpenTag(tag) }, modifier = Modifier.padding(horizontal = s.md))
                }
                pinnedRatingValues.forEach { rating ->
                    NavigationDrawerItem(label = { Text(androidx.compose.ui.res.pluralStringResource(R.plurals.system_collection_rating, rating, rating)) }, icon = { Icon(Icons.Default.Star, null, tint = RatingStarColor) }, selected = false, onClick = { onOpenRating(rating) }, modifier = Modifier.padding(horizontal = s.md))
                }
                HorizontalDivider(Modifier.padding(horizontal = 28.dp, vertical = s.sm))
            }
            Text(
                stringResource(R.string.nav_library),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = s.sm),
            )
            NavigationDrawerItem(
                label = { Text(stringResource(R.string.nav_tags)) },
                icon = { Icon(Icons.AutoMirrored.Filled.Label, null) },
                selected = selectedTab == 5,
                onClick = { onSelectTab(5) },
                modifier = Modifier.padding(horizontal = s.md),
            )
            HorizontalDivider(Modifier.padding(horizontal = 28.dp, vertical = s.sm))
            Text(
                stringResource(R.string.nav_manage),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = s.sm),
            )
            NavigationDrawerItem(
                label = { Text(stringResource(R.string.nav_manage_collections)) },
                icon = { Icon(Icons.Default.CollectionsBookmark, null) },
                selected = false,
                onClick = { onNavigate(ManageCollections) },
                modifier = Modifier.padding(horizontal = s.md),
            )
            NavigationDrawerItem(
                label = { Text(stringResource(R.string.nav_cleanup)) },
                icon = { Icon(Icons.Default.Storage, null) },
                selected = false,
                onClick = { onNavigate(CleanupHub(duplicateScanFolder)) },
                modifier = Modifier.padding(horizontal = s.md),
            )
            NavigationDrawerItem(
                label = { Text(stringResource(R.string.nav_mover)) },
                icon = { Icon(Icons.AutoMirrored.Filled.DriveFileMove, null) },
                selected = false,
                onClick = { onNavigate(FoldersMover) },
                modifier = Modifier.padding(horizontal = s.md),
            )
            if (!allFilesAccessGranted) {
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.all_files_access_title)) },
                    icon = { Icon(Icons.Default.FolderOpen, null) },
                    selected = false,
                    onClick = onRequestAllFilesAccess,
                    modifier = Modifier.padding(horizontal = s.md),
                )
            }
            NavigationDrawerItem(
                label = { Text(stringResource(R.string.nav_recycle_bin)) },
                icon = { Icon(Icons.Default.Delete, null) },
                selected = false,
                onClick = { onNavigate(RecycleBin) },
                modifier = Modifier.padding(horizontal = s.md),
            )
            Spacer(Modifier.height(s.md))
            HorizontalDivider(Modifier.padding(horizontal = 28.dp, vertical = s.xs))
            NavigationDrawerItem(
                label = { Text(stringResource(R.string.nav_settings)) },
                icon = { Icon(Icons.Default.Settings, null) },
                selected = false,
                onClick = { onNavigate(Settings) },
                modifier = Modifier.padding(horizontal = s.md),
            )
        }
    }
}

/** One lightweight status surface for every home tab; it never replaces or clears visible content. */
@Composable
internal fun LibrarySyncIndicator(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val status by LibrarySyncStatus.state.collectAsStateWithLifecycle()
    val activities by LibraryActivityStatus.states.collectAsStateWithLifecycle()
    val pendingUndo by UndoManager.actions.collectAsStateWithLifecycle()
    val repo = LocalMediaRepository.current
    val scope = rememberCoroutineScope()
    val activity = activities.sortedBy { phase ->
        when (phase.phase) {
            LibraryActivityPhase.RUNNING -> 0
            LibraryActivityPhase.ERROR -> 1
            LibraryActivityPhase.COMPLETE -> 2
            LibraryActivityPhase.CANCELLED -> 3
        }
    }.firstOrNull()
    val visible = activity != null || status.phase == LibrarySyncPhase.SCANNING || status.phase == LibrarySyncPhase.ERROR
    val isRunning = activity?.phase == LibraryActivityPhase.RUNNING || status.phase == LibrarySyncPhase.SCANNING
    val isError = activity?.phase == LibraryActivityPhase.ERROR || status.phase == LibrarySyncPhase.ERROR
    val syncRotation = if (isRunning) {
        val transition = rememberInfiniteTransition(label = "librarySyncRotation")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
            label = "librarySyncRotationValue",
        ).value
    } else 0f
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(tween(180)),
        exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(tween(140)),
        modifier = modifier
            .padding(bottom = if (pendingUndo.isNotEmpty()) 78.dp else 10.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
            tonalElevation = 4.dp,
            shadowElevation = 2.dp,
            modifier = Modifier.padding(horizontal = 12.dp),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = when {
                            isRunning -> Icons.Default.Refresh
                            isError -> Icons.Default.Close
                            else -> Icons.Default.CheckCircle
                        },
                        contentDescription = null,
                        modifier = Modifier.size(18.dp).rotate(syncRotation),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (activity != null) when (activity.phase) {
                            LibraryActivityPhase.RUNNING -> when (activity.kind) {
                                LibraryActivityKind.METADATA_SCAN -> stringResource(R.string.library_activity_metadata_scan)
                                LibraryActivityKind.PERSON_SCAN -> stringResource(R.string.library_activity_person_scan)
                            }
                            LibraryActivityPhase.COMPLETE -> stringResource(R.string.library_activity_done)
                            LibraryActivityPhase.CANCELLED -> stringResource(R.string.library_activity_cancelled)
                            LibraryActivityPhase.ERROR -> activity.errorMessage ?: stringResource(R.string.library_sync_error)
                        } else when (status.phase) {
                            LibrarySyncPhase.SCANNING -> stringResource(if (status.fullRescan) R.string.library_sync_full else R.string.library_syncing)
                            LibrarySyncPhase.COMPLETE -> if (status.addedCount > 0) stringResource(R.string.library_sync_complete, status.addedCount) else stringResource(R.string.library_sync_up_to_date)
                            LibrarySyncPhase.ERROR -> stringResource(R.string.library_sync_error)
                            LibrarySyncPhase.IDLE -> ""
                        },
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (activity?.phase == LibraryActivityPhase.RUNNING) {
                        Spacer(Modifier.width(4.dp))
                        TextButton(
                            onClick = {
                                when (activity.kind) {
                                    LibraryActivityKind.METADATA_SCAN -> MetadataSyncWorker.cancel(ctx)
                                    LibraryActivityKind.PERSON_SCAN -> PersonScanWorker.cancelAll(ctx)
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        ) { Text(stringResource(R.string.library_activity_cancel), maxLines = 1) }
                    } else if (activity?.phase == LibraryActivityPhase.ERROR) {
                        Spacer(Modifier.width(4.dp))
                        TextButton(
                            onClick = {
                                when (activity.kind) {
                                    LibraryActivityKind.METADATA_SCAN -> activity.sourcePath?.let { MetadataSyncWorker.scheduleFolderScan(ctx, it) }
                                        ?: MetadataSyncWorker.scheduleFullScan(ctx)
                                    LibraryActivityKind.PERSON_SCAN -> activity.sourcePath?.let {
                                        PersonScanWorker.scheduleFolderScan(ctx, it, activity.includeSubfolders)
                                    }
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        ) { Text(stringResource(R.string.retry)) }
                    } else if (status.phase == LibrarySyncPhase.ERROR) {
                        Spacer(Modifier.width(4.dp))
                        TextButton(
                            onClick = {
                                scope.launch {
                                    runCatching {
                                        val added = repo.syncNewMediaFromStoreCoalesced(source = "retry")
                                        if (added.isNotEmpty()) {
                                            RefreshBus.trigger(pathsChanged = true, affectedPaths = added.map { it.path })
                                        }
                                    }
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        ) { Text(stringResource(R.string.retry)) }
                    }
                }
                if (activity?.phase == LibraryActivityPhase.RUNNING) {
                    Spacer(Modifier.height(6.dp))
                    if (activity.progress == null) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp), color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceVariant)
                    } else {
                        LinearProgressIndicator(progress = { activity.progress ?: 0f }, modifier = Modifier.fillMaxWidth().height(2.dp), color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceVariant)
                    }
                    Text(
                        text = buildString {
                            if (activity.total > 0) append(ctx.getString(R.string.library_activity_progress, activity.done, activity.total))
                            if (activity.matches > 0) {
                                if (isNotEmpty()) append(" · ")
                                append(ctx.getString(R.string.library_activity_matches, activity.matches))
                            }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (status.phase == LibrarySyncPhase.SCANNING) {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp), color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceVariant)
                }
            }
        }
    }
}

/**
 * Search field + nav bar + the swipe-up-to-open-view-settings gesture spanning both.
 */
@Composable
internal fun BottomChrome(
    isMediaSelectionActive: Boolean,
    searchActive: Boolean,
    omniQuery: String,
    onQueryChange: (String) -> Unit,
    searchFocusRequester: FocusRequester,
    onFocusChanged: (Boolean) -> Unit,
    onClear: () -> Unit,
    onMenuClick: () -> Unit,
    onSearch: () -> Unit,
    onSwipeUp: () -> Unit,
    onOpenFilterAndView: () -> Unit,
    filtersAvailable: Boolean,
    hasActiveFilter: Boolean,
    searchHintRes: Int,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    showNavigation: Boolean = true,
) {
    Column(Modifier.imePadding()) {
        if (!isMediaSelectionActive) {
            val showNavBar = !searchActive && showNavigation
            val density = LocalDensity.current
            val swipeThresholdPx = with(density) { 32.dp.toPx() }
            Column(
                modifier = if (showNavBar) {
                    Modifier.pointerInput(Unit) {
                        var totalDrag = 0f
                        detectVerticalDragGestures(
                            onDragStart = { totalDrag = 0f },
                            onDragEnd = { if (totalDrag < -swipeThresholdPx) onSwipeUp() },
                            onDragCancel = { },
                        ) { change, dragAmount ->
                            totalDrag += dragAmount
                            change.consume()
                        }
                    }
                } else {
                    Modifier
                },
            ) {
                val searchField = @Composable {
                    BottomSearchField(
                        value = omniQuery,
                        onValueChange = onQueryChange,
                        focusRequester = searchFocusRequester,
                        onFocusChanged = onFocusChanged,
                        onClear = onClear,
                        onMenuClick = onMenuClick,
                        isActive = searchActive,
                        onSearch = onSearch,
                        onOpenFilterAndView = onOpenFilterAndView,
                        filtersAvailable = filtersAvailable,
                        hasActiveFilter = hasActiveFilter,
                        placeholderRes = searchHintRes,
                    )
                }
                val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
                if (isLandscape && showNavBar) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { searchField() }
                        Box(Modifier.weight(1f)) {
                            MainBottomBar(selectedTab = selectedTab, onTabSelected = onTabSelected)
                        }
                    }
                } else {
                    searchField()
                    AnimatedVisibility(
                        visible = showNavBar,
                        enter = expandVertically(
                            expandFrom = Alignment.Top,
                            animationSpec = tween(180),
                        ) + fadeIn(tween(150)),
                        exit = shrinkVertically(
                            shrinkTowards = Alignment.Top,
                            animationSpec = tween(140),
                        ) + fadeOut(tween(120)),
                    ) {
                        MainBottomBar(
                            selectedTab = selectedTab,
                            onTabSelected = onTabSelected,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun MainBottomBar(selectedTab: Int, onTabSelected: (Int) -> Unit) {
    Box(Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow)) {
        ShortNavigationBar(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            bottomNavTabs.forEach { tab ->
                ShortNavigationBarItem(
                    selected = selectedTab == tab.index,
                    onClick = {
                        if (selectedTab == tab.index) ScrollToTopBus.trigger(tab.index)
                        else onTabSelected(tab.index)
                    },
                    icon = { Icon(tab.icon, stringResource(tab.labelRes), modifier = Modifier.size(22.dp)) },
                    label = {
                        Text(
                            stringResource(tab.labelRes),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    modifier = Modifier.testTag("nav_tab_${tab.index}"),
                    colors = ShortNavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        selectedIndicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.84f),
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.92f),
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.92f),
                    ),
                )
            }
        }
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 6.dp)
                .size(width = 32.dp, height = 4.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.32f)),
        )
    }
}

@Composable
internal fun MainNavigationRail(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onOpenDrawer: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    NavigationRail(
        modifier = Modifier
            .statusBarsPadding()
            .navigationBarsPadding(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        header = {
            IconButton(onClick = onOpenDrawer, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.nav_menu_open))
            }
        },
    ) {
        navTabs.forEach { tab ->
            NavigationRailItem(
                selected = selectedTab == tab.index,
                onClick = {
                    if (selectedTab == tab.index) ScrollToTopBus.trigger(tab.index)
                    else onTabSelected(tab.index)
                },
                icon = { Icon(tab.icon, contentDescription = stringResource(tab.labelRes)) },
                label = { Text(stringResource(tab.labelRes), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                modifier = Modifier.testTag("nav_tab_${tab.index}"),
            )
        }
        Spacer(Modifier.weight(1f))
        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier.padding(bottom = 12.dp).size(48.dp),
        ) {
            Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.nav_settings))
        }
    }
}

internal fun hasAllFilesAccess(ctx: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager()
    else ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

/** Opens a folder only after passing its per-folder lock, if one is set. */
internal fun openFolderChecked(ctx: Context, path: String, onOpen: () -> Unit) {
    val activity = ctx as? android.app.Activity
    if (activity == null) { onOpen(); return }
    activity.handleLockedFolderOpening(path) { success -> if (success) onOpen() }
}

package org.fossify.gallery.compose.screens

import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.fossify.gallery.compose.components.GalleryImage
import org.fossify.gallery.compose.components.EmptyState
import org.fossify.gallery.compose.components.LibraryAlbumGrid
import org.fossify.gallery.compose.components.AlbumGridItem
import org.fossify.gallery.compose.components.SelectionAppBar
import org.fossify.gallery.compose.components.LibraryScreenHeader
import org.fossify.gallery.compose.components.RefreshActionButton
import org.fossify.gallery.compose.components.viewSettingsSummary
import org.fossify.gallery.helpers.selectionForAll
import org.fossify.gallery.viewmodels.LibraryFilterActions
import org.fossify.gallery.viewmodels.LibraryFilterState
import org.fossify.gallery.compose.components.LoadErrorBanner
import org.fossify.gallery.compose.components.LoadErrorState
import org.fossify.gallery.compose.components.InlineRefreshIndicator
import org.fossify.gallery.compose.theme.AppMotion
import org.fossify.gallery.compose.theme.LocalMediaRepository
import org.fossify.gallery.extensions.config
import org.fossify.gallery.R
import androidx.compose.ui.res.stringResource
import org.fossify.gallery.models.Medium
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.ceil

private val favoriteSelectionSaver = listSaver<Set<String>, String>(
    save = { it.toList() },
    restore = { it.toSet() },
)

@Composable
fun FavoritesScreen(
    modifier: Modifier = Modifier,
    viewSettings: ViewSettings = ViewSettings(),
    externalSearchQuery: String = "",
    onClearExternalSearch: () -> Unit = {},
    onOpenViewSettings: (() -> Unit)? = null,
    filterState: LibraryFilterState = LibraryFilterState(),
    filterActions: LibraryFilterActions = LibraryFilterActions(),
    onNavigateToViewer: ((paths: List<String>, startIndex: Int) -> Unit)? = null,
    onFolderClick: (String) -> Unit = {},
    onSelectionActiveChanged: (Boolean) -> Unit = {},
    tabIndex: Int? = null,
    onColumnCountChange: ((Int) -> Unit)? = null,
) {
    val ctx = LocalContext.current
    val repo = LocalMediaRepository.current
    val scope = rememberCoroutineScope()
    // Long-press now starts a multi-select session (matching every other folder grid in the app)
    // instead of opening a single-item pin/unpin confirmation dialog. The toolbar's Pin action
    // toggles directly, no confirmation step - mirrors AlbumsScreen's identical Favorite-folder
    // toggle icon, which also acts immediately since pinning is trivially reversible (tap again).
    var selectedFolderPaths by rememberSaveable(stateSaver = favoriteSelectionSaver) { mutableStateOf<Set<String>>(emptySet()) }
    val hasFolderSelection = selectedFolderPaths.isNotEmpty()
    var hasMediaSelection by remember { mutableStateOf(false) }
    LaunchedEffect(hasFolderSelection, hasMediaSelection) {
        onSelectionActiveChanged(hasFolderSelection || hasMediaSelection)
    }
    val selectionBackState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
    NavigationBackHandler(
        state = selectionBackState,
        isBackEnabled = hasFolderSelection,
        onBackCompleted = { selectedFolderPaths = emptySet() },
    )
    // Seed from the repo-level cache instead of empty lists - the whole FavoritesScreen composable is
    // disposed and recreated whenever the user opens a photo (navigates to Viewer) and comes back, and
    // without this the favorites/dirs queries reran from scratch on every single round trip.
    val cached = repo.getFavoritesCached()
    var favoriteMedia by remember { mutableStateOf(cached?.first ?: emptyList()) }
    var favoriteDirs by remember { mutableStateOf(cached?.second ?: emptyList()) }
    // Only true before the very first load ever completes (cache still null) - otherwise a stale
    // "no favorites" EmptyState would flash for a moment before the real, populated grid replaces it.
    var isLoading by remember { mutableStateOf(cached == null) }
    var isRefreshing by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    val normalizedSearchQuery = externalSearchQuery.trim()
    var favoriteTagsByPath by remember { mutableStateOf<Map<String, Set<String>>>(emptyMap()) }
    LaunchedEffect(favoriteMedia, normalizedSearchQuery) {
        if (normalizedSearchQuery.isBlank()) {
            favoriteTagsByPath = emptyMap()
        } else {
            delay(180)
            val paths = favoriteMedia.map { it.path }
            favoriteTagsByPath = withContext(Dispatchers.IO) { repo.getTagsForPaths(paths.toSet()) }
        }
    }
    val visibleFavoriteDirs = remember(favoriteDirs, normalizedSearchQuery) {
        if (normalizedSearchQuery.isBlank()) favoriteDirs
        else favoriteDirs.filter { it.name.contains(normalizedSearchQuery, ignoreCase = true) || it.path.contains(normalizedSearchQuery, ignoreCase = true) }
    }
    val visibleFavoriteMedia = remember(favoriteMedia, normalizedSearchQuery, favoriteTagsByPath) {
        if (normalizedSearchQuery.isBlank()) favoriteMedia
        else favoriteMedia.filter {
            it.name.contains(normalizedSearchQuery, ignoreCase = true) ||
                it.path.contains(normalizedSearchQuery, ignoreCase = true) ||
                favoriteTagsByPath[it.path].orEmpty().any { tag -> tag.contains(normalizedSearchQuery, ignoreCase = true) }
        }
    }
    LaunchedEffect(normalizedSearchQuery) { selectedFolderPaths = emptySet() }

    suspend fun reload() {
        val hadContent = favoriteMedia.isNotEmpty() || favoriteDirs.isNotEmpty()
        if (!hadContent) isLoading = true else isRefreshing = true
        try {
            val (media, dirs) = repo.refreshFavoritesCache()
            favoriteMedia = media
            favoriteDirs = dirs
            loadError = null
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            loadError = e.message
        } finally {
            isLoading = false
            isRefreshing = false
        }
    }

    LaunchedEffect(Unit) {
        if (repo.isFavoritesCacheStale()) reload()
        merge(
            org.fossify.gallery.helpers.RefreshBus.pathEvents.map { Unit },
            org.fossify.gallery.helpers.RefreshBus.metadataEvents,
        ).collectLatest {
            if (repo.isFavoritesCacheStale()) reload()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        LibraryScreenHeader(
            title = stringResource(R.string.nav_favorites),
            subtitle = if (visibleFavoriteDirs.isEmpty()) {
                stringResource(
                    R.string.favorites_summary_media_only,
                    visibleFavoriteMedia.size,
                    viewSettingsSummary(viewSettings),
                )
            } else {
                stringResource(
                    R.string.favorites_summary,
                    visibleFavoriteMedia.size,
                    visibleFavoriteDirs.size,
                    viewSettingsSummary(viewSettings),
                )
            },
            leadingIcon = Icons.Default.Favorite,
            actions = {
                if (onOpenViewSettings != null) {
                    IconButton(onClick = onOpenViewSettings) {
                        Icon(Icons.Default.Tune, stringResource(R.string.view_settings_title))
                    }
                }
                RefreshActionButton(isRefreshing = isRefreshing, onRefresh = { scope.launch { reload() } })
            },
        )
        val hasContent = favoriteMedia.isNotEmpty() || favoriteDirs.isNotEmpty()
        Column(Modifier.weight(1f).fillMaxWidth()) {
            if (loadError != null && hasContent && !isLoading) {
                LoadErrorBanner(message = loadError, onRetry = { scope.launch { reload() } })
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
            val contentState = when {
                hasContent -> "content"
                isLoading -> "loading"
                loadError != null -> "error"
                else -> "empty"
            }
            Crossfade(targetState = contentState, animationSpec = AppMotion.short, label = "favoritesContent") { s ->
                when (s) {
                "loading" -> MediaSkeleton(
                    columns = viewSettings.columnCount,
                    viewType = viewSettings.viewType,
                    itemSpacing = viewSettings.spacing.dp,
                    showFileName = viewSettings.showFileNames,
                    reserveGridCaptionSpace = viewSettings.viewType == ViewType.GRID,
                    listDensity = viewSettings.listDensity,
                )
                "error" -> LoadErrorState(message = loadError, onRetry = { scope.launch { reload() } })
                "empty" -> EmptyState(Icons.Default.FavoriteBorder, stringResource(R.string.no_favorites), subtitle = stringResource(R.string.no_favorites_hint))
                else -> if (normalizedSearchQuery.isNotBlank() && visibleFavoriteDirs.isEmpty() && visibleFavoriteMedia.isEmpty()) {
                    EmptyState(Icons.Default.SearchOff, stringResource(R.string.no_results), subtitle = stringResource(R.string.no_results_hint), actionLabel = stringResource(R.string.clear_filter), onAction = onClearExternalSearch)
                } else Column(Modifier.fillMaxSize().padding(8.dp)) {
                    if (visibleFavoriteDirs.isNotEmpty()) {
                        if (hasFolderSelection) {
                            val allPinned = selectedFolderPaths.all { it in ctx.config.pinnedFavoriteFolders }
                            SelectionAppBar(
                                count = selectedFolderPaths.size,
                                onClose = { selectedFolderPaths = emptySet() },
                                subtitle = stringResource(R.string.selection_scope_visible, visibleFavoriteDirs.size),
                                actions = {
                                    IconButton(onClick = {
                                        val cur = ctx.config.pinnedFavoriteFolders
                                        ctx.config.pinnedFavoriteFolders = if (allPinned) cur - selectedFolderPaths else cur + selectedFolderPaths
                                        org.fossify.gallery.helpers.RefreshBus.trigger(pathsChanged = false, collectionsChanged = false)
                                        selectedFolderPaths = emptySet()
                                    }) {
                                        Icon(
                                            if (allPinned) Icons.Default.Star else Icons.Default.StarBorder,
                                            stringResource(if (allPinned) R.string.unpin_action else R.string.pin_action),
                                        )
                                    }
                                    var folderMenuOpen by remember { mutableStateOf(false) }
                                    Box {
                                        IconButton(onClick = { folderMenuOpen = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.more_actions)) }
                                        DropdownMenu(expanded = folderMenuOpen, onDismissRequest = { folderMenuOpen = false }) {
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.action_select_all)) },
                                                onClick = { folderMenuOpen = false; selectedFolderPaths = selectionForAll(selectedFolderPaths, visibleFavoriteDirs.map { it.path }, invert = false) },
                                            )
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.action_invert_selection)) },
                                                onClick = { folderMenuOpen = false; selectedFolderPaths = selectionForAll(selectedFolderPaths, visibleFavoriteDirs.map { it.path }, invert = true) },
                                            )
                                        }
                                    }
                                },
                            )
                        } else {
                            Text(stringResource(R.string.favorite_folders), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                        // Sort the favorite folders by the same field as the media below, so the
                        // whole tab responds to the sort setting instead of the folder strip staying
                        // in a fixed order while the media reorders. Same mapping as AlbumsScreen.
                        val favoriteDirItems = remember(visibleFavoriteDirs, viewSettings.sortBy, viewSettings.sortDesc) {
                            val asc = when (viewSettings.sortBy) {
                                SortField.DATE -> visibleFavoriteDirs.sortedBy { it.modified }
                                SortField.SIZE -> visibleFavoriteDirs.sortedBy { it.size }
                                SortField.COUNT -> visibleFavoriteDirs.sortedBy { it.mediaCnt }
                                else -> visibleFavoriteDirs.sortedBy { it.name.lowercase() } // NAME, and RATING (folders have none)
                            }
                            (if (viewSettings.sortDesc) asc.reversed() else asc)
                                .map { AlbumGridItem(key = it.path, name = it.name, thumbnailPath = it.tmb, count = it.mediaCnt, imageCount = it.imageCount, videoCount = it.videoCount) }
                        }
                        val folderSectionHeight = if (viewSettings.viewType == ViewType.LIST) {
                            val rowHeight = if (viewSettings.listDensity == ListDensity.COMPACT) 60 else 80
                            (favoriteDirItems.size * rowHeight).coerceAtMost(320).dp
                        } else {
                            val columns = org.fossify.gallery.compose.util.rememberEffectiveColumnCount(viewSettings.columnCount)
                            val availableWidth = (LocalConfiguration.current.screenWidthDp - 16).coerceAtLeast(1)
                            val tileWidth = availableWidth.toFloat() / columns
                            val rows = ceil(favoriteDirItems.size / columns.toFloat()).toInt()
                            (rows * (tileWidth + 60f + viewSettings.spacing)).coerceAtMost(320f).dp
                        }
                        val internalStorageLabel = stringResource(R.string.internal_storage)
                        LibraryAlbumGrid(
                            items = favoriteDirItems,
                            viewSettings = viewSettings,
                            onClick = { item -> if (hasFolderSelection) selectedFolderPaths = if (item.key in selectedFolderPaths) selectedFolderPaths - item.key else selectedFolderPaths + item.key else onFolderClick(item.key) },
                            onLongClick = { item -> selectedFolderPaths = selectedFolderPaths + item.key },
                            selectedKeys = selectedFolderPaths,
                            pathHint = { org.fossify.gallery.compose.components.folderPathHint(it.key, internalStorageLabel) },
                            modifier = Modifier.height(folderSectionHeight),
                            tabIndex = tabIndex,
                            fastScrollLabel = { index -> favoriteDirItems.getOrNull(index)?.name },
                            onColumnCountChange = onColumnCountChange,
                        )
                        if (visibleFavoriteMedia.isNotEmpty()) HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    }
                    if (visibleFavoriteMedia.isEmpty() && visibleFavoriteDirs.isNotEmpty()) {
                        // A folder-only favorites state used to leave the whole lower half of the
                        // screen blank. Keep the folder section compact, then make the remaining
                        // space purposeful with the same calm empty-state language used elsewhere.
                        EmptyState(
                            icon = Icons.Default.StarBorder,
                            title = stringResource(R.string.no_favorite_media),
                            subtitle = stringResource(R.string.no_favorite_media_hint),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (visibleFavoriteMedia.isNotEmpty()) {
                        Text(stringResource(R.string.favorite_media), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                        // anchorBottom is meant to bottom-anchor only the favorites FIRST view (the
                        // folder list above), not the media display within - so strip it here, keeping
                        // the media grid in its normal top-down order regardless of the setting.
                        MediaScreen(
                            modifier = Modifier.weight(1f),
                            viewSettings = viewSettings.copy(anchorBottom = false),
                            ratingFilter = filterState.rating,
                            tagFilterNames = filterState.tagNames,
                            tagMatchAll = filterState.tagMatchAll,
                            pathFilter = filterState.pathFilter,
                            excludePathFilter = filterState.excludePathFilter,
                            activeTagName = filterState.activeTagName,
                            activePathName = filterState.activePathName,
                            activeCollectionName = filterState.activeCollectionName,
                            activeFolderFilterName = filterState.activeFolderFilterName,
                            additionalMediaPaths = filterState.additionalMediaPaths,
                            minSizeFilter = filterState.minSize,
                            dateRangeFilter = filterState.dateRange,
                            addedSince = filterState.addedSince,
                            typeFilter = filterState.typeFilter,
                            reviewFilter = filterState.reviewFilter,
                            onClearFilter = filterActions.clearAll,
                            onClearRatingFilter = filterActions.clearRating,
                            onClearTagFilter = filterActions.clearTags,
                            onRemoveTagFilter = filterActions.removeTag,
                            onClearTagMatchAll = filterActions.clearTagMatchAll,
                            onClearPathFilter = filterActions.clearPath,
                            onClearSizeFilter = filterActions.clearSize,
                            onClearDateFilter = filterActions.clearDate,
                            onClearAddedSinceFilter = filterActions.clearAddedSince,
                            onClearTypeFilter = filterActions.clearType,
                            onClearReviewFilter = filterActions.clearReview,
                            mediaOverride = visibleFavoriteMedia,
                            onNavigateToViewer = onNavigateToViewer,
                            onSelectionActiveChanged = { hasMediaSelection = it },
                            tabIndex = tabIndex,
                            onColumnCountChange = onColumnCountChange,
                        )
                    }
                }
            }
        }
        InlineRefreshIndicator(
            visible = isRefreshing,
            contentAvailable = hasContent,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
    }
}
}

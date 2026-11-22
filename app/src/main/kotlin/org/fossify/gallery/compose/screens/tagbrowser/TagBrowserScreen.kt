package org.fossify.gallery.compose.screens.tagbrowser
import androidx.compose.ui.res.stringResource
import org.fossify.gallery.R
import org.fossify.gallery.compose.theme.AppMotion
import org.fossify.gallery.compose.theme.Radius

import android.widget.Toast
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.commons.extensions.toast
import org.fossify.gallery.compose.screens.MediaSkeleton
import org.fossify.gallery.compose.screens.FolderSkeleton
import org.fossify.gallery.compose.screens.SortField
import org.fossify.gallery.compose.screens.VideoThumbnail
import org.fossify.gallery.compose.components.GalleryImage
import org.fossify.gallery.compose.components.EmptyState
import org.fossify.gallery.compose.components.LibraryAlbumGrid
import org.fossify.gallery.compose.components.AlbumGridItem
import org.fossify.gallery.compose.components.LoadErrorBanner
import org.fossify.gallery.compose.components.LoadErrorState
import org.fossify.gallery.compose.components.RefreshActionButton
import org.fossify.gallery.compose.components.LibraryScreenHeader
import org.fossify.gallery.compose.components.SelectionAppBar
import org.fossify.gallery.compose.components.UndoBar
import org.fossify.gallery.compose.components.LibrarySearchField
import org.fossify.gallery.compose.components.TagHierarchyPickerDialog
import org.fossify.gallery.compose.components.viewSettingsSummary
import org.fossify.gallery.compose.theme.LocalMediaRepository
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.VIDEO_EXTENSIONS
import org.fossify.gallery.helpers.expandTagsWithDescendants
import org.fossify.gallery.helpers.renameTagInHierarchy
import org.fossify.gallery.helpers.UndoAction
import org.fossify.gallery.helpers.UndoManager
import org.fossify.gallery.helpers.UndoType
import org.fossify.gallery.helpers.QuickTagsBus
import org.fossify.gallery.helpers.selectionForAll
import java.io.File

private val tagSelectionSaver = listSaver<Set<String>, String>(
    save = { it.toList() },
    restore = { it.toSet() },
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TagBrowserScreen(
    onBack: () -> Unit,
    onTagFilterApplied: (tagNames: Set<String>, displayName: String, matchAll: Boolean) -> Unit,
    viewSettings: org.fossify.gallery.compose.screens.ViewSettings = org.fossify.gallery.compose.screens.ViewSettings(),
    embeddedInMainTabs: Boolean = false,
    externalSearchQuery: String = "",
    onClearExternalSearch: () -> Unit = {},
    onOpenViewSettings: (() -> Unit)? = null,
) {
    val ctx = LocalContext.current
    val res = androidx.compose.ui.platform.LocalResources.current
    val scope = rememberCoroutineScope()
    val repo = LocalMediaRepository.current

    // Seed from the repo-level cache instead of an empty map - this whole screen is disposed and
    // recreated whenever the user navigates away (e.g. to Viewer) and back, and without this the full
    // media_tags scan reran from scratch on every single round trip.
    var allTags by remember {
        mutableStateOf(
            repo.getTagsWithPathsCached()
                ?.takeUnless { repo.isTagsWithPathsCacheStale() }
                ?: emptyMap(),
        )
    }
    var scanning by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var deleteConfirmTags by remember { mutableStateOf<Set<String>>(emptySet()) }
                var mergeTargetTag by remember { mutableStateOf<String?>(null) }
                var renameTargetTag by remember { mutableStateOf<String?>(null) }
                var pendingParentAssign by remember { mutableStateOf<Set<String>?>(null) }
    var selectedTags by rememberSaveable(stateSaver = tagSelectionSaver) { mutableStateOf<Set<String>>(emptySet()) }
    var tagMatchAll by remember { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var tagSearchQuery by rememberSaveable { mutableStateOf("") }
    var showHierarchyPicker by remember { mutableStateOf(false) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    val hierarchy = remember(refreshTrigger) { ctx.config.tagHierarchy }
    val quickTagsVersion by QuickTagsBus.version.collectAsStateWithLifecycle()
    val recentTags = remember(quickTagsVersion) { ctx.config.recentTags }
    val filesCountFormat = stringResource(R.string.files_count)
    val childrenCountFormat = stringResource(R.string.tag_children_count)
    val normalizedTagSearchQuery = (if (embeddedInMainTabs) externalSearchQuery else tagSearchQuery).trim()
    val visibleTagSelectionKeys = remember(allTags, normalizedTagSearchQuery) {
        val query = normalizedTagSearchQuery
        allTags.keys.filter { query.isBlank() || it.contains(query, ignoreCase = true) }
    }

    // Keep selection scoped to what the user can currently see, just like the other library
    // screens. Otherwise a tag selected before searching would remain active invisibly and could
    // accidentally be included in a later filter/delete action.
    LaunchedEffect(visibleTagSelectionKeys) {
        val visibleKeys = visibleTagSelectionKeys.toSet()
        val nextSelection = selectedTags.intersect(visibleKeys)
        if (nextSelection != selectedTags) {
            selectedTags = nextSelection
            if (nextSelection.isEmpty()) tagMatchAll = false
        }
    }
    LaunchedEffect(selectedTags.size) {
        if (selectedTags.size < 2 && tagMatchAll) tagMatchAll = false
    }

    val tagBrowserBackState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
    NavigationBackHandler(
        state = tagBrowserBackState,
        isBackEnabled = selectedTags.isNotEmpty() || (!embeddedInMainTabs && searchOpen),
        onBackCompleted = {
            when {
                selectedTags.isNotEmpty() -> {
                    selectedTags = emptySet()
                    tagMatchAll = false
                }
                !embeddedInMainTabs && searchOpen -> {
                    searchOpen = false
                    tagSearchQuery = ""
                }
            }
        },
    )

    suspend fun refreshTags(showLoading: Boolean) {
        if (showLoading && allTags.isEmpty()) scanning = true
        if (showLoading) isRefreshing = true
        try {
            val catalog = withContext(Dispatchers.IO) { repo.getTagCatalogCached() }
            allTags = catalog.pathsByTag.entries.sortedByDescending { it.value.size }.associate { it.key to it.value }
            loadError = null
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            loadError = e.message
        } finally {
            scanning = false
            isRefreshing = false
        }
    }

    LaunchedEffect(refreshTrigger) {
        if (refreshTrigger == 0 && !repo.isTagsWithPathsCacheStale()) return@LaunchedEffect
        refreshTags(showLoading = true)
    }

    // Without this, a tag added/removed elsewhere (MediaScreen's quick-tag row, the Viewer's tag
    // editor) never invalidated this screen's cache. This refreshes allTags directly instead of
    // bumping refreshTrigger, so an unrelated app-wide event (e.g. a rating change in another folder)
    // doesn't flip scanning=true and flash the loading skeleton while the user is browsing tags.
    LaunchedEffect(Unit) {
        org.fossify.gallery.helpers.RefreshBus.events.collect {
            refreshTags(showLoading = false)
        }
    }

    Scaffold(
        contentWindowInsets = if (embeddedInMainTabs) WindowInsets(0, 0, 0, 0) else androidx.compose.material3.ScaffoldDefaults.contentWindowInsets,
        topBar = {
            if (selectedTags.isNotEmpty()) {
                SelectionAppBar(
                    count = selectedTags.size,
                    onClose = {
                        selectedTags = emptySet()
                        tagMatchAll = false
                    },
                    actions = {
                        IconButton(onClick = {
                            val tagNames = expandTagsWithDescendants(selectedTags, hierarchy)
                            onTagFilterApplied(tagNames, selectedTags.joinToString(", "), tagMatchAll)
                        }) {
                            Icon(Icons.Default.FilterList, stringResource(R.string.action_filter))
                        }
                        IconButton(onClick = { deleteConfirmTags = selectedTags }) {
                            Icon(Icons.Default.Delete, stringResource(R.string.action_delete))
                        }
                        var selectionMenuOpen by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { selectionMenuOpen = true }) {
                                Icon(Icons.Default.MoreVert, stringResource(R.string.more_actions))
                            }
                            androidx.compose.material3.DropdownMenu(
                                expanded = selectionMenuOpen,
                                onDismissRequest = { selectionMenuOpen = false },
                            ) {
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_select_all)) },
                                    onClick = {
                                        selectionMenuOpen = false
                                        selectedTags = selectionForAll(selectedTags, visibleTagSelectionKeys, invert = false)
                                    },
                                )
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_invert_selection)) },
                                    onClick = {
                                        selectionMenuOpen = false
                                        selectedTags = selectionForAll(selectedTags, visibleTagSelectionKeys, invert = true)
                                    },
                                )
                                val allPinned = selectedTags.isNotEmpty() && selectedTags.all { it in ctx.config.pinnedTags }
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text(stringResource(if (allPinned) R.string.unpin_from_drawer else R.string.pin_to_drawer)) },
                                    onClick = {
                                        selectionMenuOpen = false
                                        val current = ctx.config.pinnedTags
                                        ctx.config.pinnedTags = if (allPinned) current - selectedTags else current + selectedTags
                                        org.fossify.gallery.helpers.RefreshBus.trigger(pathsChanged = false, collectionsChanged = false)
                                    },
                                )
                            }
                        }
                    },
                )
            } else {
                LibraryScreenHeader(
                    title = stringResource(R.string.tags_count_title, allTags.size),
                    subtitle = viewSettingsSummary(viewSettings, includeGrouping = false),
                    leadingIcon = if (embeddedInMainTabs) Icons.AutoMirrored.Filled.Label else null,
                    navigation = if (embeddedInMainTabs) null else {
                        { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back)) } }
                    },
                    modifier = if (embeddedInMainTabs) Modifier else Modifier.statusBarsPadding(),
                    actions = {
                        if (!embeddedInMainTabs) {
                            IconButton(onClick = {
                                if (searchOpen) tagSearchQuery = ""
                                searchOpen = !searchOpen
                            }) {
                                Icon(
                                    if (searchOpen) Icons.Default.Close else Icons.Default.Search,
                                    contentDescription = stringResource(
                                        when {
                                            !searchOpen -> R.string.cd_search
                                            tagSearchQuery.isNotEmpty() -> R.string.cd_clear_search
                                            else -> R.string.cd_close
                                        },
                                    ),
                                )
                            }
                        }
                        if (onOpenViewSettings != null) {
                            IconButton(onClick = onOpenViewSettings) {
                                Icon(Icons.Default.Tune, contentDescription = stringResource(R.string.view_settings_title))
                            }
                        }
                        RefreshActionButton(
                            isRefreshing = isRefreshing,
                            onRefresh = { scope.launch { refreshTags(showLoading = true) } },
                        )
                        IconButton(onClick = { showHierarchyPicker = true }) {
                            Icon(Icons.Default.AccountTree, contentDescription = stringResource(R.string.browse_tag_hierarchy))
                        }
                    },
                )
            }
        },
        bottomBar = { UndoBar(if (embeddedInMainTabs) Modifier else Modifier.navigationBarsPadding()) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 8.dp)) {
            // Progress of a running tag merge/rename (their dialogs close immediately).
            org.fossify.gallery.compose.util.XmpBatchIndicator()
            if (!embeddedInMainTabs && searchOpen) {
                LibrarySearchField(
                    value = tagSearchQuery,
                    onValueChange = { tagSearchQuery = it },
                    placeholderRes = R.string.search_tag,
                )
                Spacer(Modifier.height(8.dp))
            }
            if (loadError != null && allTags.isNotEmpty()) {
                LoadErrorBanner(message = loadError, onRetry = { scope.launch { refreshTags(showLoading = false) } })
                Spacer(Modifier.height(8.dp))
            }
            // Crossfade + shimmer skeleton instead of a bare spinner - this is the same "grid of tiles
            // is loading" moment as the media grids elsewhere, so it should look consistent.
            val tagsContentState = when {
                allTags.isNotEmpty() -> "content"
                scanning -> "loading"
                loadError != null -> "error"
                else -> "empty"
            }
            Crossfade(targetState = tagsContentState, animationSpec = AppMotion.short, label = "tagsContent", modifier = Modifier.weight(1f)) { tcs ->
            if (tcs == "loading") {
                FolderSkeleton(viewSettings = viewSettings)
            } else if (tcs == "error") {
                LoadErrorState(message = loadError, onRetry = { scope.launch { refreshTags(showLoading = true) } })
            } else if (tcs == "empty") {
                EmptyState(Icons.AutoMirrored.Filled.Label, stringResource(R.string.no_tags_found), subtitle = stringResource(R.string.no_tags_hint))
            } else {
                // Explicit Column - this "content" state now lives inside Crossfade's Box slot
                // instead of being a direct child of the outer Column, so without this the grid and
                // the action-buttons row below it would overlay instead of stacking vertically.
                Column(Modifier.fillMaxSize()) {
                // Both the sort/filter and the AlbumGridItem mapping were previously recomputed on
                // every recomposition of this screen (e.g. every tag selection toggle), not just
                // when allTags/hierarchy/the search query actually changed.
                // Sort follows the sheet's name/count/recently-used choice. The ↳ child markers below
                // still show hierarchy; the ordering is the user's.
                val parentLabel = stringResource(R.string.parent_tag)
                val recentRank = remember(recentTags) {
                    recentTags.withIndex().associate { (index, tag) -> tag.lowercase() to index }
                }
                val filteredTags = remember(allTags, normalizedTagSearchQuery, viewSettings.sortBy, viewSettings.sortDesc, recentTags) {
                    val base = if (normalizedTagSearchQuery.isBlank()) allTags.entries.toList()
                        else allTags.entries.filter { (tag, _) -> tag.contains(normalizedTagSearchQuery, ignoreCase = true) }
                    when (viewSettings.sortBy) {
                        SortField.COUNT -> {
                            val asc = base.sortedBy { it.value.size }
                            if (viewSettings.sortDesc) asc.reversed() else asc
                        }
                        SortField.RECENT -> {
                            val recent = base.filter { recentRank.containsKey(it.key.lowercase()) }
                            val notRecent = base.filterNot { recentRank.containsKey(it.key.lowercase()) }
                            val recentFirst = recent.sortedWith(compareBy({ recentRank[it.key.lowercase()] }, { it.key.lowercase() }))
                            val orderedRecent = if (viewSettings.sortDesc) recentFirst else recentFirst.reversed()
                            orderedRecent + notRecent.sortedBy { it.key.lowercase() }
                        }
                        else -> {
                            val asc = base.sortedBy { it.key.lowercase() }
                            if (viewSettings.sortDesc) asc.reversed() else asc
                        }
                    }
                }
                val tagGridItems = remember(filteredTags, hierarchy) {
                    filteredTags.map { AlbumGridItem(key = it.key, name = if (it.key in hierarchy) "↳ ${it.key}" else it.key, thumbnailPath = it.value.firstOrNull() ?: "", count = it.value.size, previewPaths = it.value.take(3)) }
                }
                // Precomputed once instead of scanning the whole hierarchy map per visible row
                // inside subtitle = {...} below (was O(tags²) across all rows).
                val childrenByParent = remember(hierarchy) { hierarchy.entries.groupBy({ it.value }, { it.key }) }
                if (tagGridItems.isEmpty()) {
                    EmptyState(
                        Icons.Default.SearchOff,
                        stringResource(R.string.no_results),
                        subtitle = stringResource(R.string.no_results_hint),
                        actionLabel = stringResource(R.string.clear_filter),
                        onAction = { if (embeddedInMainTabs) onClearExternalSearch() else tagSearchQuery = "" },
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    LibraryAlbumGrid(
                        items = tagGridItems,
                        viewSettings = viewSettings,
                        onClick = { item ->
                            if (selectedTags.isNotEmpty()) selectedTags = if (item.key in selectedTags) selectedTags - item.key else selectedTags + item.key
                            else {
                                // Include descendant tag names too, so filtering on a parent like "Places"
                                // also surfaces files only tagged with a nested child like "Berlin" -
                                // resolved to files in SQL by MediaViewModel, not here.
                                val tagNames = expandTagsWithDescendants(setOf(item.key), hierarchy)
                                onTagFilterApplied(tagNames, item.key, false)
                                onBack()
                            }
                        },
                        onLongClick = { item -> selectedTags = if (item.key in selectedTags) selectedTags - item.key else selectedTags + item.key },
                        countLabel = { filesCountFormat.format(it) },
                        selectedKeys = selectedTags,
                        subtitle = { item ->
                            val parent = hierarchy[item.key]
                            val children = childrenByParent[item.key] ?: emptyList()
                            val parts = mutableListOf(filesCountFormat.format(item.count))
                            if (parent != null) parts.add("$parentLabel: $parent")
                            if (children.isNotEmpty()) parts.add(childrenCountFormat.format(children.size))
                            parts.joinToString(" · ")
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (selectedTags.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    if (selectedTags.size >= 2) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(onClick = { tagMatchAll = false }, shape = RoundedCornerShape(Radius.md), color = if (!tagMatchAll) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest, contentColor = if (!tagMatchAll) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f)) {
                                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.Center) { Text(stringResource(R.string.filter_tags_match_any), style = MaterialTheme.typography.labelSmall) }
                            }
                            Surface(onClick = { tagMatchAll = true }, shape = RoundedCornerShape(Radius.md), color = if (tagMatchAll) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest, contentColor = if (tagMatchAll) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f)) {
                                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.Center) { Text(stringResource(R.string.filter_tags_match_all), style = MaterialTheme.typography.labelSmall) }
                            }
                        }
                    }
                    if (selectedTags.size == 1) {
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(onClick = { renameTargetTag = selectedTags.sorted().firstOrNull() }, shape = RoundedCornerShape(Radius.md), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.weight(1f)) {
                                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.Center) { Text(stringResource(R.string.action_rename), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSecondaryContainer) }
                            }
                            Surface(onClick = { pendingParentAssign = selectedTags }, shape = RoundedCornerShape(Radius.md), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.weight(1f)) {
                                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.Center) { Text(stringResource(R.string.action_parent), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSecondaryContainer) }
                            }
                        }
                    } else {
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(onClick = { pendingParentAssign = selectedTags }, shape = RoundedCornerShape(Radius.md), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.weight(1f)) {
                                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.Center) { Text(stringResource(R.string.action_parent), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSecondaryContainer) }
                            }
                            Surface(onClick = { mergeTargetTag = selectedTags.sorted().firstOrNull() }, shape = RoundedCornerShape(Radius.md), color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.weight(1f)) {
                                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.Center) { Text(stringResource(R.string.action_merge), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onTertiaryContainer) }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                }
            }
            }
        }
    }

    if (deleteConfirmTags.isNotEmpty()) {
        val tagsToDelete = deleteConfirmTags
        val totalFiles = tagsToDelete.flatMap { allTags[it] ?: emptyList() }.distinct().size
        AlertDialog(
            onDismissRequest = { deleteConfirmTags = emptySet() },
            title = { Text(if (tagsToDelete.size == 1) stringResource(R.string.remove_tag_title) else stringResource(R.string.remove_tags_title)) },
            text = {
                if (tagsToDelete.size == 1) Text(stringResource(R.string.remove_tag_confirm, tagsToDelete.first(), totalFiles))
                else Text(stringResource(R.string.remove_tags_confirm, tagsToDelete.size, tagsToDelete.joinToString(", "), totalFiles))
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch(Dispatchers.IO) {
                        val affectedPaths = tagsToDelete.flatMap { allTags[it].orEmpty() }.distinct()
                        val snapshots = repo.getTagsForPaths(affectedPaths)
                        val hierarchyBefore = ctx.config.tagHierarchy
                        // Remove hierarchy references before rewriting the files. If a deleted tag
                        // is still a parent of another tag, doing this afterwards would write the
                        // orphaned parent chain back into XMP and a later scan could resurrect it.
                        try {
                            val h = ctx.config.tagHierarchy
                            val cleaned = h.filterKeys { it !in tagsToDelete }.filterValues { it !in tagsToDelete }
                            if (cleaned.size != h.size) ctx.config.tagHierarchy = cleaned.toMutableMap()
                        } catch (e: Exception) { android.util.Log.e("TagBrowser", "Orphaned hierarchy cleanup failed", e) }
                        val successful = mutableSetOf<String>()
                        org.fossify.gallery.compose.util.XmpBatch.run(ctx, affectedPaths,
                            successMessage = res.getString(R.string.tags_removed, tagsToDelete.size, totalFiles),
                            onSuccess = { successful += it },
                        ) { path -> repo.removeTags(path, tagsToDelete) }
                        withContext(Dispatchers.Main) {
                            if (successful.isNotEmpty()) UndoManager.push(
                                UndoAction(
                                    paths = successful,
                                    type = UndoType.TAG_RESTORE_SNAPSHOT,
                                    tagSnapshots = snapshots.filterKeys { it in successful }.mapValues { it.value.toSet() },
                                    hierarchyBefore = hierarchyBefore,
                                )
                            )
                            deleteConfirmTags = emptySet(); refreshTrigger++; selectedTags = emptySet(); tagMatchAll = false
                        }
                    }
                }) { Text(stringResource(R.string.action_remove), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteConfirmTags = emptySet() }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    if (pendingParentAssign != null) {
        val tagsToAssign = pendingParentAssign!!
        val candidates = allTags.keys.filter { it !in tagsToAssign }
        TagHierarchyPickerDialog(
            title = stringResource(R.string.assign_parent_title),
            subtitle = stringResource(R.string.set_parent_for, tagsToAssign.joinToString(", ")),
            tags = candidates,
            hierarchy = hierarchy.filterKeys { it !in tagsToAssign }.filterValues { it !in tagsToAssign },
            tagCounts = allTags.mapValues { it.value.size },
            allowNoParent = true,
            noParentLabel = stringResource(R.string.tag_parent_none),
            confirmLabel = stringResource(org.fossify.commons.R.string.save),
            onDismiss = { pendingParentAssign = null },
            onConfirm = { selectedParent ->
                val h = ctx.config.tagHierarchy.toMutableMap()
                fun createsCycle(child: String, parent: String): Boolean {
                    var cur: String? = parent
                    val seen = HashSet<String>()
                    while (cur != null && seen.add(cur)) {
                        if (cur == child) return true
                        cur = h[cur]
                    }
                    return false
                }
                val invalid = selectedParent != null && (selectedParent in tagsToAssign || tagsToAssign.any { createsCycle(it, selectedParent) })
                if (invalid) {
                    ctx.toast(R.string.invalid_parent_cycle, Toast.LENGTH_SHORT)
                } else {
                    val hierarchyBefore = h.toMap()
                    tagsToAssign.forEach { child ->
                        if (selectedParent.isNullOrBlank()) h.remove(child) else h[child] = selectedParent
                    }
                    ctx.config.tagHierarchy = h
                    val affectedPaths = tagsToAssign.flatMap { allTags[it].orEmpty() }.distinct()
                    pendingParentAssign = null; selectedTags = emptySet(); tagMatchAll = false; refreshTrigger++
                    if (affectedPaths.isNotEmpty()) {
                        scope.launch {
                            org.fossify.gallery.compose.util.XmpBatch.run(ctx, affectedPaths) {
                                repo.rewriteTagHierarchy(listOf(it)) == 0
                            }
                            withContext(Dispatchers.Main) {
                                UndoManager.push(UndoAction(paths = affectedPaths.toSet(), type = UndoType.TAG_HIERARCHY, hierarchyBefore = hierarchyBefore))
                                ctx.toast(R.string.parent_tag_set, Toast.LENGTH_SHORT)
                            }
                        }
                    } else {
                        UndoManager.push(UndoAction(paths = emptySet(), type = UndoType.TAG_HIERARCHY, hierarchyBefore = hierarchyBefore))
                        ctx.toast(R.string.parent_tag_set, Toast.LENGTH_SHORT)
                    }
                }
            },
        )
    }

    if (mergeTargetTag != null) {
        val defaultTarget = mergeTargetTag!!
        var mergeTargetName by remember(mergeTargetTag) { mutableStateOf(defaultTarget) }
        val existingTags = allTags.keys.filter { it != defaultTarget }.sorted()
        AlertDialog(
            onDismissRequest = { mergeTargetTag = null },
            title = { Text(stringResource(R.string.merge_tags_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.sources_label, selectedTags.joinToString(", ")), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = mergeTargetName, onValueChange = { mergeTargetName = it }, label = { Text(stringResource(R.string.target_tag)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    if (existingTags.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.existing_tags), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            existingTags.forEach { t ->
                                Surface(onClick = { mergeTargetName = t }, shape = RoundedCornerShape(Radius.md), color = if (t == mergeTargetName) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest, contentColor = if (t == mergeTargetName) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant) {
                                    Text(t, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = mergeTargetName.trim()
                    if (target.isBlank()) return@TextButton
                    val sources = selectedTags - target
                    // Close the dialog right away - the XmpBatch indicator carries the progress;
                    // the dialog used to sit visually frozen for the whole multi-minute merge.
                    mergeTargetTag = null
                    scope.launch {
                        val hierarchyBefore = ctx.config.tagHierarchy
                        val items = sources.flatMap { srcTag -> (allTags[srcTag] ?: emptyList()).map { it to srcTag } }
                        val affectedPaths = items.map { it.first }.distinct()
                        val snapshots = withContext(Dispatchers.IO) { repo.getTagsForPaths(affectedPaths) }
                        // Update hierarchy names before writing files so the replacement XMP carries
                        // the same ancestry as the newly merged/renamed tag.
                        sources.forEach { source ->
                            ctx.config.tagHierarchy = renameTagInHierarchy(ctx.config.tagHierarchy, source, target)
                        }
                        val successful = mutableSetOf<String>()
                        org.fossify.gallery.compose.util.XmpBatch.run(ctx, items, successMessage = res.getString(R.string.merged_into, target), onSuccess = { successful += it.first }) { (p, srcTag) ->
                            repo.replaceTag(p, srcTag, target)
                        }
                        if (successful.isNotEmpty()) UndoManager.push(UndoAction(successful, UndoType.TAG_RESTORE_SNAPSHOT, tagSnapshots = snapshots.filterKeys { it in successful }.mapValues { it.value.toSet() }, hierarchyBefore = hierarchyBefore))
                        refreshTrigger++; selectedTags = emptySet(); tagMatchAll = false
                    }
                }) { Text(stringResource(R.string.action_merge)) }
            },
            dismissButton = { TextButton(onClick = { mergeTargetTag = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    if (renameTargetTag != null) {
        val oldName = renameTargetTag!!
        var newName by remember(renameTargetTag) { mutableStateOf(oldName) }
        val count = allTags[oldName]?.size ?: 0
        AlertDialog(
            onDismissRequest = { renameTargetTag = null },
            title = { Text(stringResource(R.string.rename_tag_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.rename_tag_prompt, oldName, count), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text(stringResource(R.string.rename_numbered)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = newName.trim()
                    if (target.isBlank() || target == oldName) return@TextButton
                    // Dialog closes immediately, the XmpBatch indicator shows the per-file progress.
                    renameTargetTag = null
                    scope.launch {
                        val paths = allTags[oldName] ?: emptyList()
                        val hierarchyBefore = ctx.config.tagHierarchy
                        val snapshots = withContext(Dispatchers.IO) { repo.getTagsForPaths(paths) }
                        ctx.config.tagHierarchy = renameTagInHierarchy(ctx.config.tagHierarchy, oldName, target)
                        val successful = mutableSetOf<String>()
                        org.fossify.gallery.compose.util.XmpBatch.run(ctx, paths, successMessage = res.getString(R.string.renamed_tag_result, oldName, target, count), onSuccess = { successful += it }) { p ->
                            repo.replaceTag(p, oldName, target)
                        }
                        if (successful.isNotEmpty()) UndoManager.push(UndoAction(successful, UndoType.TAG_RESTORE_SNAPSHOT, tagSnapshots = snapshots.filterKeys { it in successful }.mapValues { it.value.toSet() }, hierarchyBefore = hierarchyBefore))
                        refreshTrigger++; selectedTags = emptySet(); tagMatchAll = false
                    }
                }) { Text(stringResource(R.string.action_rename)) }
            },
            dismissButton = { TextButton(onClick = { renameTargetTag = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    if (showHierarchyPicker) {
        TagHierarchyPickerDialog(
            title = stringResource(R.string.tag_picker_title),
            subtitle = stringResource(R.string.tag_browser_picker_hint),
            tags = allTags.keys,
            hierarchy = hierarchy,
            tagCounts = allTags.mapValues { it.value.size },
            confirmLabel = stringResource(R.string.action_filter),
            onDismiss = { showHierarchyPicker = false },
            onConfirm = { selected ->
                selected?.let {
                    onTagFilterApplied(expandTagsWithDescendants(setOf(it), hierarchy), it, false)
                    onBack()
                }
                showHierarchyPicker = false
            },
        )
    }
}

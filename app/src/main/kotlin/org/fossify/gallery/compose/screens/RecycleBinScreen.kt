package org.fossify.gallery.compose.screens
import org.fossify.gallery.compose.theme.Radius

import java.text.DateFormat
import java.util.Date
import java.io.File
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.commons.extensions.toast
import org.fossify.gallery.compose.components.GalleryImage
import org.fossify.gallery.compose.components.EmptyState
import org.fossify.gallery.compose.components.ConfirmDestructive
import org.fossify.gallery.compose.components.LoadErrorBanner
import org.fossify.gallery.compose.components.LoadErrorState
import org.fossify.gallery.compose.components.RefreshActionButton
import org.fossify.gallery.compose.components.LibrarySearchField
import org.fossify.gallery.compose.components.SelectionAppBar
import org.fossify.gallery.compose.components.SelectionModeMenuButton
import org.fossify.gallery.compose.theme.LocalMediaRepository
import androidx.compose.ui.res.stringResource
import org.fossify.gallery.R
import org.fossify.gallery.compose.util.rememberMediaStoreConsent
import org.fossify.gallery.compose.util.MediaSelectionMode
import org.fossify.gallery.helpers.MediaStoreOps
import org.fossify.gallery.models.Medium
import org.fossify.gallery.helpers.withDeletePasswordCheck

private val recycleSelectionSaver = listSaver<Set<String>, String>(
    save = { it.toList() },
    restore = { it.toSet() },
)

internal fun matchesRecycleBinMediaType(medium: Medium, mediaTypeFilter: Int): Boolean = when (mediaTypeFilter) {
    1 -> !medium.isVideo()
    2 -> medium.isVideo()
    else -> true
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun RecycleBinScreen(
    onBack: () -> Unit,
    onNavigateToViewer: (paths: List<String>, startIndex: Int) -> Unit,
) {
    val ctx = LocalContext.current
    val res = androidx.compose.ui.platform.LocalResources.current
    val repo = LocalMediaRepository.current
    val scope = rememberCoroutineScope()
    val consent = rememberMediaStoreConsent()
    var items by remember { mutableStateOf<List<Medium>>(emptyList()) }
    var refresh by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    // The compact list keeps batch cleanup fast, while the grid makes it practical to visually
    // inspect photos before restoring or permanently deleting them.
    // Start with a visual review surface. A new key intentionally upgrades existing installations
    // from the former dense list, while users can still choose the compact list at any time.
    var largePreviews by rememberSaveable("recycle-bin-layout-v2") { mutableStateOf(true) }
    var actionsMenuOpen by remember { mutableStateOf(false) }
    var filterMenuOpen by remember { mutableStateOf(false) }
    var mediaTypeFilter by rememberSaveable { mutableIntStateOf(0) } // 0 all, 1 image, 2 video
    var folderFilter by rememberSaveable { mutableStateOf<String?>(null) }
    // Paths of items ticked in one concrete visible result set.
    var selected by rememberSaveable(stateSaver = recycleSelectionSaver) { mutableStateOf<Set<String>>(emptySet()) }
    var selectionBehavior by rememberSaveable { mutableStateOf(MediaSelectionMode.INDIVIDUAL) }
    var rangeAnchorPath by rememberSaveable { mutableStateOf<String?>(null) }
    var showEmptyConfirm by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Medium?>(null) }
    var showDeleteSelectedConfirm by remember { mutableStateOf(false) }
    // Non-null while a permanent delete runs (done/total) - drives the progress bar and disables
    // every delete entry point. Emptying a large bin takes real time; without this the screen
    // looked completely frozen ("nothing happens") until everything was gone.
    var deleteProgress by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    suspend fun reloadItems() {
        if (items.isEmpty()) isLoading = true
        try {
            val loaded = withContext(Dispatchers.IO) { repo.getDeletedMedia() }
            items = loaded.sortedWith(compareByDescending<Medium> { it.deletedTS }.thenByDescending { it.id ?: 0L })
            loadError = null
            // Drop selections for items that no longer exist (restored/deleted elsewhere) so the count
            // and the select-all toggle stay honest.
            val live = loaded.mapTo(HashSet()) { it.path }
            selected = selected.filterTo(HashSet()) { it in live }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            loadError = e.message
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(refresh) {
        reloadItems()
    }

    val availableFolders = remember(items) { items.map { it.parentPath }.filter { it.isNotBlank() }.distinct().sorted() }
    val filtered = remember(items, query, mediaTypeFilter, folderFilter) {
        val q = query.trim()
        items.filter { medium ->
            (q.isEmpty() || medium.name.contains(q, ignoreCase = true) || medium.path.contains(q, ignoreCase = true)) &&
                matchesRecycleBinMediaType(medium, mediaTypeFilter) &&
                (folderFilter == null || medium.parentPath == folderFilter)
        }
    }
    val filteredBytes = remember(filtered) { filtered.sumOf { it.size.coerceAtLeast(0L) } }
    val nearestExpiry = remember(filtered) {
        filtered.mapNotNull { it.deletedTS.takeIf { timestamp -> timestamp > 0L }?.plus(RECYCLE_BIN_RETENTION_MS) }.minOrNull()
    }
    val selectionMode = selected.isNotEmpty()
    val visiblePaths = remember(filtered) { filtered.map { it.path } }
    // "All of the *filtered* list is selected" drives the select-all/none toggle, per the request to
    // scope select-all to the current search results rather than the whole bin.
    val allFilteredSelected = filtered.isNotEmpty() && filtered.all { it.path in selected }

    fun exitSelection() {
        selected = emptySet()
        rangeAnchorPath = null
        selectionBehavior = MediaSelectionMode.INDIVIDUAL
    }

    LaunchedEffect(query, mediaTypeFilter, folderFilter) {
        // A destructive action must never include media that disappeared behind a new filter.
        // Restarting selection is a cheaper and much clearer recovery than a hidden-selection
        // warning at the point of permanent delete.
        exitSelection()
    }

    fun selectForInteraction(path: String, startSelection: Boolean = false) {
        val targetIndex = visiblePaths.indexOf(path)
        val anchorIndex = rangeAnchorPath?.let(visiblePaths::indexOf)?.takeIf { it >= 0 }
        selected = when {
            startSelection -> selected + path
            selectionBehavior == MediaSelectionMode.RANGE && anchorIndex != null && targetIndex >= 0 -> {
                val lo = minOf(anchorIndex, targetIndex)
                val hi = maxOf(anchorIndex, targetIndex)
                selected + visiblePaths.subList(lo, hi + 1)
            }
            path in selected -> selected - path
            else -> selected + path
        }
        rangeAnchorPath = path
    }

    fun openPreview(medium: Medium) {
        val index = visiblePaths.indexOf(medium.path)
        if (index >= 0) onNavigateToViewer(visiblePaths, index)
    }

    // Permanently deletes the given paths. With All-Files-Access File.delete() works directly, so
    // no OS consent dialog is needed at all - the scan afterwards purges stale MediaStore rows.
    // Without it, consent is requested in chunks: createDeleteRequest with a whole bin's worth of
    // URIs exceeds the binder transaction limit and throws, which used to surface as nothing but a
    // bare "cancelled" toast after several seconds of per-path URI lookups (user report
    // 2026-07-17: "Leeren macht nichts mehr, nach einigen Sekunden steht da nur Abbrechen").
    fun permanentlyDelete(paths: List<String>) {
        if (paths.isEmpty() || deleteProgress != null) return
        // Permanent, unrecoverable deletion (empty bin + delete-forever both route here) - the single
        // most destructive action in the app, so it's the clearest thing to put behind the optional
        // delete password. No-op prompt when the protection is off.
        ctx.withDeletePasswordCheck {
        scope.launch {
            deleteProgress = 0 to paths.size
            if (org.fossify.commons.helpers.isRPlus() && !org.fossify.commons.extensions.isExternalStorageManager()) {
                val uris = withContext(Dispatchers.IO) { MediaStoreOps.urisForPaths(ctx, paths).map { it.second } }
                for (chunk in uris.chunked(250)) {
                    val granted = try {
                        consent.request(MediaStoreOps.deleteRequest(ctx, chunk))
                    } catch (e: Exception) {
                        android.util.Log.e("RecycleBin", "Delete consent request failed", e)
                        false
                    }
                    if (!granted) { deleteProgress = null; ctx.toast(R.string.cancelled); return@launch }
                }
            }
            val failed = withContext(Dispatchers.IO) {
                val f = repo.deleteMediaBatch(paths) { done, total -> deleteProgress = done to total }
                // The direct-delete path leaves stale MediaStore rows behind - scanning the (now
                // missing) paths removes them. Harmless no-op for rows the consent flow already purged.
                runCatching { android.media.MediaScannerConnection.scanFile(ctx, paths.toTypedArray(), null, null) }
                f
            }
            deleteProgress = null
            exitSelection()
            refresh++
            if (failed > 0) {
                ctx.toast(res.getQuantityString(R.plurals.delete_failed_count, failed, failed))
            } else {
                ctx.toast(res.getQuantityString(R.plurals.deleted_forever_count, paths.size, paths.size))
            }
        }
        }
    }

    fun restore(paths: List<String>) {
        if (paths.isEmpty()) return
        scope.launch {
            val failed = withContext(Dispatchers.IO) { repo.restoreFromRecycleBinBatch(paths) }
            exitSelection()
            refresh++
            if (failed == 0) {
                ctx.toast(res.getQuantityString(R.plurals.restored_count, paths.size, paths.size))
            } else {
                ctx.toast(res.getString(R.string.restore_partial, paths.size - failed, paths.size))
            }
        }
    }

    val recycleBinBackState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
    NavigationBackHandler(
        state = recycleBinBackState,
        isBackEnabled = showEmptyConfirm || pendingDelete != null || showDeleteSelectedConfirm || selectionMode,
        onBackCompleted = {
            when {
                showEmptyConfirm -> showEmptyConfirm = false
                pendingDelete != null -> pendingDelete = null
                showDeleteSelectedConfirm -> showDeleteSelectedConfirm = false
                else -> exitSelection()
            }
        },
    )

    Scaffold(
        topBar = {
            if (selectionMode) {
                SelectionAppBar(
                    count = selected.size,
                    onClose = { exitSelection() },
                    subtitle = if (selectionBehavior == MediaSelectionMode.RANGE) {
                        rangeAnchorPath?.let { stringResource(R.string.selection_range_from, File(it).name) }
                    } else {
                        stringResource(R.string.selection_scope_visible, filtered.size)
                    },
                    windowInsets = TopAppBarDefaults.windowInsets,
                    actions = {
                        SelectionModeMenuButton(
                            selectionMode = selectionBehavior,
                            onSelectionModeChange = { selectionBehavior = it },
                        )
                        // One toggle covers both "select all" and "select none", scoped to the
                        // filtered list (see allFilteredSelected).
                        IconButton(
                            onClick = {
                                selected = if (allFilteredSelected) selected - filtered.map { it.path }.toSet()
                                else selected + filtered.map { it.path }
                            },
                            enabled = deleteProgress == null && filtered.isNotEmpty(),
                        ) {
                            Icon(
                                if (allFilteredSelected) Icons.Default.Deselect else Icons.Default.SelectAll,
                                stringResource(if (allFilteredSelected) R.string.action_select_none else R.string.action_select_all),
                            )
                        }
                        IconButton(onClick = { restore(selected.toList()) }, enabled = deleteProgress == null) {
                            Icon(Icons.Default.Restore, stringResource(R.string.action_restore), tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = { showDeleteSelectedConfirm = true }, enabled = deleteProgress == null) {
                            Icon(Icons.Default.DeleteForever, stringResource(R.string.action_delete_forever), tint = MaterialTheme.colorScheme.error)
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.nav_recycle_bin), fontWeight = FontWeight.Bold) },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back)) } },
                    actions = {
                        // Refresh: pull the *shared* MediaStore trash (other apps trash into the same bin)
                        // and re-read, so this view reflects what's actually trashed on the device now.
                        RefreshActionButton(
                            isRefreshing = isRefreshing,
                            enabled = deleteProgress == null,
                            onRefresh = {
                                scope.launch {
                                    isRefreshing = true
                                    try {
                                        val added = withContext(Dispatchers.IO) { repo.syncTrashFromMediaStore() }
                                        refresh++
                                        ctx.toast(if (added > 0) res.getString(R.string.recycle_bin_refreshed_added, added) else res.getString(R.string.recycle_bin_refreshed))
                                    } catch (e: Exception) {
                                        loadError = e.message
                                    } finally {
                                        isRefreshing = false
                                    }
                                }
                            },
                        )
                        IconButton(onClick = { largePreviews = !largePreviews }, enabled = deleteProgress == null) {
                            Icon(
                                imageVector = if (largePreviews) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                                contentDescription = stringResource(if (largePreviews) R.string.recycle_bin_compact_list else R.string.recycle_bin_large_previews),
                            )
                        }
                        Box {
                            IconButton(onClick = { filterMenuOpen = true }, enabled = deleteProgress == null) {
                                Icon(Icons.Default.FilterList, stringResource(R.string.recycle_bin_filter))
                            }
                            DropdownMenu(expanded = filterMenuOpen, onDismissRequest = { filterMenuOpen = false }) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.recycle_bin_all_media)) }, onClick = { mediaTypeFilter = 0; folderFilter = null; filterMenuOpen = false })
                                DropdownMenuItem(text = { Text(stringResource(R.string.images)) }, onClick = { mediaTypeFilter = 1; filterMenuOpen = false })
                                DropdownMenuItem(text = { Text(stringResource(R.string.videos)) }, onClick = { mediaTypeFilter = 2; filterMenuOpen = false })
                                if (availableFolders.isNotEmpty()) {
                                    HorizontalDivider()
                                    availableFolders.take(12).forEach { folder ->
                                        DropdownMenuItem(
                                            text = { Text(File(folder).name.ifBlank { folder }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                            onClick = { folderFilter = folder; filterMenuOpen = false },
                                        )
                                    }
                                }
                            }
                        }
                        if (items.isNotEmpty()) {
                            Box {
                                IconButton(onClick = { actionsMenuOpen = true }, enabled = deleteProgress == null) {
                                    Icon(Icons.Default.MoreVert, stringResource(R.string.more_actions))
                                }
                                DropdownMenu(expanded = actionsMenuOpen, onDismissRequest = { actionsMenuOpen = false }) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.action_restore_all)) },
                                        leadingIcon = { Icon(Icons.Default.Restore, null) },
                                        onClick = { actionsMenuOpen = false; restore(items.map { it.path }) },
                                    )
                                    if (filtered.size != items.size) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.recycle_bin_restore_filtered)) },
                                            leadingIcon = { Icon(Icons.Default.Restore, null) },
                                            onClick = { actionsMenuOpen = false; restore(filtered.map { it.path }) },
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.action_empty), color = MaterialTheme.colorScheme.error) },
                                        leadingIcon = { Icon(Icons.Default.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
                                        onClick = { actionsMenuOpen = false; showEmptyConfirm = true },
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                )
            }
        }
    ) { padding ->
        if (isLoading) {
            MediaSkeleton(columns = 3, modifier = Modifier.padding(padding))
        } else if (items.isEmpty() && loadError != null) {
            LoadErrorState(message = loadError, onRetry = { scope.launch { reloadItems() } }, modifier = Modifier.padding(padding))
        } else if (items.isEmpty()) {
            EmptyState(Icons.Default.DeleteForever, stringResource(R.string.recycle_bin_empty), modifier = Modifier.padding(padding))
        } else {
            val layoutDirection = LocalLayoutDirection.current
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(
                        top = padding.calculateTopPadding(),
                        start = padding.calculateStartPadding(layoutDirection),
                        end = padding.calculateEndPadding(layoutDirection),
                    )
                    .consumeWindowInsets(padding)
                    .imePadding(),
            ) {
                if (loadError != null) LoadErrorBanner(message = loadError, onRetry = { scope.launch { reloadItems() } })
                LibrarySearchField(
                    value = query,
                    onValueChange = { query = it },
                    placeholderRes = R.string.recycle_bin_search_hint,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
                org.fossify.gallery.compose.components.AnalysisResultHeader(
                    summary = stringResource(R.string.recycle_bin_storage_summary, filtered.size, android.text.format.Formatter.formatFileSize(ctx, filteredBytes)),
                    detail = nearestExpiry?.let { recycleBinExpiryLabel(ctx, it) } ?: stringResource(R.string.recycle_bin_auto_cleanup),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                )
                deleteProgress?.let { (done, total) ->
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { if (total > 0) done / total.toFloat() else 0f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        stringResource(R.string.deleting_forever_progress, done, total),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                if (filtered.isEmpty()) {
                    EmptyState(
                        Icons.Default.SearchOff,
                        stringResource(R.string.no_results),
                        subtitle = stringResource(R.string.no_results_hint),
                        actionLabel = stringResource(R.string.clear_filter),
                        onAction = { query = "" },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    if (largePreviews) {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 152.dp),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 12.dp,
                                top = 12.dp,
                                end = 12.dp,
                                bottom = 12.dp + padding.calculateBottomPadding(),
                            ),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            gridItems(filtered, key = { it.path }) { medium ->
                                RecycleBinPreviewCard(
                                    medium = medium,
                                    selected = medium.path in selected,
                                    selectionMode = selectionMode,
                                    enabled = deleteProgress == null,
                                    onToggleSelection = { checked -> if (checked) selectForInteraction(medium.path) else selected = selected - medium.path },
                                    onStartSelection = { selectForInteraction(medium.path, startSelection = true) },
                                    onOpen = { openPreview(medium) },
                                    onRestore = {
                                        scope.launch {
                                            val ok = withContext(Dispatchers.IO) { repo.restoreFromRecycleBin(medium.path) }
                                            if (!ok) ctx.toast(R.string.restore_failed) else refresh++
                                        }
                                    },
                                    onDelete = { pendingDelete = medium },
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 8.dp,
                                top = 8.dp,
                                end = 8.dp,
                                bottom = 8.dp + padding.calculateBottomPadding(),
                            ),
                        ) {
                            items(filtered, key = { it.path }) { m ->
                                val isChecked = m.path in selected
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            enabled = deleteProgress == null,
                                            onClick = {
                                                if (selectionMode) selectForInteraction(m.path)
                                                else openPreview(m)
                                            },
                                            onLongClick = { if (!selectionMode) selectForInteraction(m.path, startSelection = true) },
                                        )
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (selectionMode) {
                                        Checkbox(
                                            checked = isChecked,
                                            onCheckedChange = { checked -> if (checked) selectForInteraction(m.path) else selected = selected - m.path },
                                            enabled = deleteProgress == null,
                                        )
                                        Spacer(Modifier.width(4.dp))
                                    }
                                    Box(Modifier.size(72.dp).clip(RoundedCornerShape(Radius.sm))) {
                                        GalleryImage(path = m.path, contentDescription = m.name, modifier = Modifier.fillMaxSize())
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text(m.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(stringResource(R.string.recycle_bin_deleted_at, formatRecycleBinTimestamp(m.deletedTS)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(
                                            "${android.text.format.Formatter.formatFileSize(ctx, m.size.coerceAtLeast(0L))} · ${recycleBinExpiryLabel(ctx, m.deletedTS + RECYCLE_BIN_RETENTION_MS)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    if (!selectionMode) {
                                        IconButton(onClick = {
                                            scope.launch {
                                                val ok = withContext(Dispatchers.IO) { repo.restoreFromRecycleBin(m.path) }
                                                if (!ok) ctx.toast(R.string.restore_failed) else refresh++
                                            }
                                        }, enabled = deleteProgress == null) { Icon(Icons.Default.Restore, stringResource(R.string.action_restore), tint = MaterialTheme.colorScheme.primary) }
                                        IconButton(onClick = { pendingDelete = m }, enabled = deleteProgress == null) { Icon(Icons.Default.DeleteForever, stringResource(R.string.action_delete_forever), tint = MaterialTheme.colorScheme.error) }
                                    }
                                }
                                HorizontalDivider(Modifier.padding(start = 92.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEmptyConfirm) {
        val count = items.size
        ConfirmDestructive(
            title = stringResource(R.string.empty_recycle_bin_title),
            text = stringResource(R.string.empty_recycle_bin_confirm, count),
            confirmLabel = stringResource(R.string.action_delete_forever),
            onConfirm = {
                showEmptyConfirm = false
                permanentlyDelete(items.map { it.path })
            },
            onDismiss = { showEmptyConfirm = false },
        )
    }

    if (showDeleteSelectedConfirm) {
        val count = selected.size
        ConfirmDestructive(
            title = stringResource(R.string.action_delete_forever),
            text = stringResource(R.string.empty_recycle_bin_confirm, count),
            confirmLabel = stringResource(R.string.action_delete_forever),
            onConfirm = {
                showDeleteSelectedConfirm = false
                permanentlyDelete(selected.toList())
            },
            onDismiss = { showDeleteSelectedConfirm = false },
        )
    }

    pendingDelete?.let { m ->
        ConfirmDestructive(
            title = stringResource(R.string.action_delete_forever),
            text = stringResource(R.string.delete_forever_single_confirm, m.name),
            confirmLabel = stringResource(R.string.action_delete_forever),
            onConfirm = {
                pendingDelete = null
                permanentlyDelete(listOf(m.path))
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecycleBinPreviewCard(
    medium: Medium,
    selected: Boolean,
    selectionMode: Boolean,
    enabled: Boolean,
    onToggleSelection: (Boolean) -> Unit,
    onStartSelection: () -> Unit,
    onOpen: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                enabled = enabled,
                onClick = { if (selectionMode) onToggleSelection(!selected) else onOpen() },
                onLongClick = { if (!selectionMode) onStartSelection() },
            ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(topStart = Radius.sm, topEnd = Radius.sm))) {
                GalleryImage(path = medium.path, contentDescription = medium.name, modifier = Modifier.fillMaxSize(), thumbnailSize = 640)
                if (selectionMode) {
                    Checkbox(
                        checked = selected,
                        onCheckedChange = onToggleSelection,
                        enabled = enabled,
                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                    )
                }
            }
            Column(Modifier.padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 4.dp)) {
                Text(medium.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(R.string.recycle_bin_deleted_at, formatRecycleBinTimestamp(medium.deletedTS)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${android.text.format.Formatter.formatFileSize(context, medium.size.coerceAtLeast(0L))} · ${recycleBinExpiryLabel(context, medium.deletedTS + RECYCLE_BIN_RETENTION_MS)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!selectionMode) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = onRestore, enabled = enabled) {
                        Icon(Icons.Default.Restore, stringResource(R.string.action_restore), tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onDelete, enabled = enabled) {
                        Icon(Icons.Default.DeleteForever, stringResource(R.string.action_delete_forever), tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

private fun formatRecycleBinTimestamp(timestamp: Long): String =
    if (timestamp <= 0L) "—" else DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timestamp))

private const val RECYCLE_BIN_RETENTION_MS = 30L * 24 * 60 * 60 * 1000

private fun recycleBinExpiryLabel(context: android.content.Context, expiry: Long): String {
    if (expiry <= 0L) return context.getString(R.string.recycle_bin_auto_cleanup)
    val remaining = ((expiry - System.currentTimeMillis()).coerceAtLeast(0L) + DAY_MS - 1) / DAY_MS
    return if (remaining == 0L) context.getString(R.string.recycle_bin_expires_today)
    else context.getString(R.string.recycle_bin_expires_in, remaining)
}

private const val DAY_MS = 24L * 60 * 60 * 1000

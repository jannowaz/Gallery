package org.fossify.gallery.compose.screens.analysis
import androidx.compose.ui.res.stringResource
import org.fossify.gallery.R
import org.fossify.gallery.helpers.formatBytes
import org.fossify.gallery.compose.theme.Radius

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.fossify.gallery.compose.components.GalleryImage
import org.fossify.gallery.compose.util.sharedElementKey
import org.fossify.gallery.compose.theme.RatingStarColor
import java.io.File
import kotlin.math.min
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicateFinderScreen(onBack: () -> Unit, initialFolder: String = "", onNavigateToViewer: (List<String>, Int) -> Unit = { _, _ -> }) {
    val vm: DuplicateFinderViewModel = viewModel()
    val state by vm.state.collectAsState()
    val ctx = LocalContext.current
    var showConfirmDialog by remember { mutableStateOf(false) }
    val defaultPath = Environment.getExternalStorageDirectory().absolutePath
    var currentFolder by remember { mutableStateOf(initialFolder.ifBlank { defaultPath }) }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) currentFolder = duplicateUriToPath(uri) ?: uri.toString()
    }

    val listState = rememberLazyListState()

    // Folder breakdown of the results: how many groups touch each folder. Drives the filter row
    // so a device-wide scan can be worked through folder by folder. Sorted by count so the busiest
    // folders come first.
    val folderCounts = remember(state.groups) {
        val m = LinkedHashMap<String, Int>()
        state.groups.forEach { g ->
            g.files.mapNotNull { File(it.path).parent }.toSet().forEach { m[it] = (m[it] ?: 0) + 1 }
        }
        m.entries.sortedByDescending { it.value }.map { it.key to it.value }
    }

    // Groups actually shown: all of them, or only those touching the active folder filter.
    val filteredGroups = remember(state.groups, state.folderFilter) {
        val f = state.folderFilter
        if (f == null) state.groups else state.groups.filter { g -> g.files.any { File(it.path).parent == f } }
    }

    // A filter can go stale after a delete empties its folder - fall back to "all" so the user
    // never faces a blank list with an orphaned chip selected.
    LaunchedEffect(state.folderFilter, filteredGroups.isEmpty()) {
        if (state.folderFilter != null && filteredGroups.isEmpty() && state.groups.isNotEmpty()) vm.setFolderFilter(null)
    }

    // Once results exist the scan form is just dead weight above a list the user wants to work -
    // collapse it (a tap on the header's gear re-opens it for a new scan). Kept open while there
    // are no results yet, including during a scan, so the controls and progress stay visible.
    var configExpanded by remember { mutableStateOf(true) }
    LaunchedEffect(state.groups.isEmpty()) { configExpanded = state.groups.isEmpty() }

    // Keep results live against deletions made elsewhere (emptying the recycle bin, deleting in the
    // gallery) without forcing a fresh scan: re-check file existence on every (re)entry to this
    // screen - e.g. returning from the recycle-bin route - and again whenever a data-change event
    // fires while it's open. Groups whose files no longer exist drop out; a no-op when nothing changed.
    LaunchedEffect(Unit) {
        vm.revalidate()
        org.fossify.gallery.helpers.RefreshBus.events.collect { vm.revalidate() }
    }

    // Same protection as the storage analysis: a duplicate scan takes minutes, so leaving a
    // running scan or its results needs a deliberate double back.
    val guardedBack = org.fossify.gallery.compose.util.rememberDoubleBackGuard(
        enabled = state.isScanning || state.groups.isNotEmpty(),
        onExit = onBack,
    )

    // Toast nudge shared by every auto-select path: SIMILAR matches aren't verified identical.
    fun cautionIfSimilar() {
        if (state.mode == DuplicateMode.SIMILAR) {
            android.widget.Toast.makeText(ctx, ctx.getString(R.string.similar_select_caution), android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_find_duplicates), fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = guardedBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back)) } },
                actions = {
                    if (state.groups.isNotEmpty()) {
                        AutoSelectAction(
                            folderFiltered = state.folderFilter != null,
                            onApplyAll = { strategy ->
                                // "All groups" honors the folder filter: with a filter active it means
                                // all *shown* groups, and only their files in that folder are marked
                                // (scoped replace, other marks preserved) - it can never mark a copy the
                                // current filter is hiding, so the count matches what you see.
                                if (state.folderFilter != null) vm.applyKeepStrategy(strategy, filteredGroups.map { it.hash }.toSet(), additive = false, restrictToFolder = state.folderFilter)
                                else vm.applyKeepStrategy(strategy)
                                cautionIfSimilar()
                            },
                            onApplyNext = { strategy, n ->
                                // "Next N groups" counts from the group currently at the top of the
                                // viewport downward, over the *filtered* list, and stacks onto the
                                // current marks so long lists get worked in batches. Under a folder
                                // filter it too marks only that folder's files.
                                val start = listState.firstVisibleItemIndex.coerceIn(0, (filteredGroups.size - 1).coerceAtLeast(0))
                                val hashes = filteredGroups.subList(start, min(start + n, filteredGroups.size)).map { it.hash }.toSet()
                                vm.applyKeepStrategy(strategy, hashes, additive = true, restrictToFolder = state.folderFilter)
                                cautionIfSimilar()
                            },
                            onSelectShownFolder = { state.folderFilter?.let { vm.selectFolder(it) } },
                            onClear = { vm.clearSelection() },
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        bottomBar = {
            // Anchored at the bottom instead of stacked into the scrolling column, so marking files
            // never shrinks the list you're working - the whole screen stays available for review.
            if (state.selectedForDeletion.isNotEmpty()) {
                val selSize = state.groups.flatMap { it.files }.filter { it.path in state.selectedForDeletion }.sumOf { it.size }
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, tonalElevation = 3.dp, shadowElevation = 8.dp) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.duplicate_marked_count, state.selectedForDeletion.size, formatBytes(selSize)), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        TextButton(onClick = { vm.clearSelection() }) { Text(stringResource(R.string.action_empty)) }
                        Spacer(Modifier.width(4.dp))
                        Button(
                            onClick = { showConfirmDialog = true },
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        ) {
                            Icon(Icons.Default.Delete, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(org.fossify.commons.R.string.delete))
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Compact results header with a collapse toggle for the scan form below.
            if (state.groups.isNotEmpty()) {
                val totalWasted = remember(state.groups) { state.groups.sumOf { it.wastedBytes } }
                val totalDupes = remember(state.groups) { state.groups.sumOf { it.files.size - 1 } }
                Surface(color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)) {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.duplicate_scan_summary, state.groups.size, totalDupes, formatBytes(totalWasted)), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            state.restoredAt?.let { ts ->
                                Text(
                                    stringResource(R.string.restored_scan_from, java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(ts))),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        IconButton(onClick = { configExpanded = !configExpanded }) {
                            Icon(
                                if (configExpanded) Icons.Default.ExpandLess else Icons.Default.Tune,
                                stringResource(R.string.dup_config_toggle),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }

            // Scan configuration form - collapsed once results exist to give the list the screen.
            AnimatedVisibility(visible = configExpanded) {
                Column {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (state.scope == DuplicateScope.FOLDER) {
                            Surface(
                                modifier = Modifier.weight(1f).clickable { folderPicker.launch(null) },
                                shape = RoundedCornerShape(Radius.md),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            ) {
                                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.FolderOpen, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        currentFolder.substringAfterLast('/').ifEmpty { stringResource(R.string.internal_storage) },
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        } else {
                            Text(
                                stringResource(R.string.dup_scope_recent_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = { if (state.isScanning) vm.cancelScan() else vm.startScan(currentFolder) }) {
                            if (state.isScanning) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(if (state.isScanning) stringResource(R.string.cancel) else stringResource(R.string.cd_search))
                        }
                    }

                    if (state.scope == DuplicateScope.FOLDER) {
                        Text(
                            currentFolder,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }

                    // Scan scope: a folder, or the recent additions checked against the whole library.
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = state.scope == DuplicateScope.FOLDER,
                            onClick = { vm.setScope(DuplicateScope.FOLDER) },
                            enabled = !state.isScanning,
                            label = { Text(stringResource(R.string.dup_scope_folder)) },
                        )
                        FilterChip(
                            selected = state.scope == DuplicateScope.LAST_WEEK,
                            onClick = { vm.setScope(DuplicateScope.LAST_WEEK) },
                            enabled = !state.isScanning,
                            label = { Text(stringResource(R.string.dup_scope_last_week)) },
                        )
                        FilterChip(
                            selected = state.scope == DuplicateScope.LAST_MONTH,
                            onClick = { vm.setScope(DuplicateScope.LAST_MONTH) },
                            enabled = !state.isScanning,
                            label = { Text(stringResource(R.string.dup_scope_last_month)) },
                        )
                    }

                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = state.mode == DuplicateMode.EXACT,
                            onClick = { vm.setMode(DuplicateMode.EXACT) },
                            enabled = !state.isScanning,
                            label = { Text(stringResource(R.string.dup_mode_exact)) },
                        )
                        FilterChip(
                            selected = state.mode == DuplicateMode.SIMILAR,
                            onClick = { vm.setMode(DuplicateMode.SIMILAR) },
                            enabled = !state.isScanning && state.scope == DuplicateScope.FOLDER,
                            label = { Text(stringResource(R.string.dup_mode_similar)) },
                        )
                    }
                    Text(
                        if (state.mode == DuplicateMode.SIMILAR) stringResource(R.string.dup_desc_similar) else stringResource(R.string.dup_desc_exact),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                    )

                    if (state.mode == DuplicateMode.SIMILAR) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.tolerance), style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                                Text("${state.similarThreshold} · ${thresholdLabel(state.similarThreshold)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            }
                            Slider(
                                value = state.similarThreshold.toFloat(),
                                onValueChange = { vm.setThreshold(it.roundToInt()) },
                                valueRange = 0f..20f,
                                steps = 19,
                                enabled = !state.isScanning,
                            )
                        }
                    }
                }
            }

            if (state.isScanning) {
                LinearProgressIndicator(progress = { state.progress / 100f }, modifier = Modifier.fillMaxWidth())
                Text(state.phase, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Folder filter: work a device-wide scan area by area instead of one mixed list.
            if (state.groups.isNotEmpty() && folderCounts.size > 1) {
                LazyRow(
                    Modifier.fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        FilterChip(
                            selected = state.folderFilter == null,
                            onClick = { vm.setFolderFilter(null) },
                            label = { Text("${stringResource(R.string.dup_folder_all)} · ${state.groups.size}") },
                        )
                    }
                    items(folderCounts, key = { it.first }) { (folder, count) ->
                        FilterChip(
                            selected = state.folderFilter == folder,
                            onClick = { vm.setFolderFilter(if (state.folderFilter == folder) null else folder) },
                            leadingIcon = { Icon(Icons.Default.FolderOpen, null, Modifier.size(16.dp)) },
                            label = { Text("${folder.substringAfterLast('/')} · $count", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        )
                    }
                }
            }

            if (state.groups.isNotEmpty()) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp)) {
                    items(filteredGroups, key = { it.hash }) { group ->
                        DuplicateGroupCard(
                            group = group,
                            similar = state.mode == DuplicateMode.SIMILAR,
                            selected = state.selectedForDeletion,
                            onToggle = { vm.toggleSelection(it) },
                            onView = onNavigateToViewer,
                            onSelectFolder = { path -> vm.selectAllInFolder(path) },
                        )
                    }
                    item { Spacer(Modifier.height(12.dp)) }
                }
            } else if (state.scanDone && !state.isScanning) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.ContentCopy, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                        Spacer(Modifier.height(16.dp))
                        Text(stringResource(R.string.no_duplicates_found), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(R.string.files_scanned_count, state.totalScanned), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else if (!state.isScanning) {
                // First visit, nothing scanned yet - explain the tool instead of bare form controls.
                org.fossify.gallery.compose.components.EmptyState(
                    icon = Icons.Default.ContentCopy,
                    title = stringResource(R.string.dup_empty_title),
                    subtitle = stringResource(R.string.dup_empty_subtitle),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }
        }
    }

    if (showConfirmDialog) {
        val count = state.selectedForDeletion.size
        val selSize = state.groups.flatMap { it.files }.filter { it.path in state.selectedForDeletion }.sumOf { it.size }
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text(stringResource(R.string.confirm_delete_title)) },
            text = { Text(stringResource(R.string.move_to_recycle_bin_confirm_size, count, formatBytes(selSize))) },
            confirmButton = { TextButton(onClick = { showConfirmDialog = false; vm.deleteSelected() }) { Text(stringResource(org.fossify.commons.R.string.delete), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { showConfirmDialog = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/**
 * The top-bar "Auto-select" menu. Beyond picking which file each group keeps, it carries a batch
 * scope: apply the strategy to *all* groups (a clean one-shot that replaces the selection), or to
 * only the next 10/25/50 groups from the current scroll position (stacked on top, so a long list
 * is marked-and-deleted area by area). "Mark all shown" appears when a folder filter is active,
 * clearing that one folder's copies in a tap.
 */
@Composable
private fun AutoSelectAction(
    folderFiltered: Boolean,
    onApplyAll: (KeepStrategy) -> Unit,
    onApplyNext: (KeepStrategy, Int) -> Unit,
    onSelectShownFolder: () -> Unit,
    onClear: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    // 0 = every group; otherwise the batch size counted from the scroll position.
    var batchCount by remember { mutableStateOf(0) }

    fun apply(strategy: KeepStrategy) {
        if (batchCount == 0) onApplyAll(strategy) else onApplyNext(strategy, batchCount)
        expanded = false
    }

    Box {
        TextButton(onClick = { expanded = true }) { Text(stringResource(R.string.auto_select)) }
        androidx.compose.material3.DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Text(
                stringResource(R.string.keep_scope_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterChip(selected = batchCount == 0, onClick = { batchCount = 0 }, label = { Text(stringResource(R.string.dup_batch_all)) })
                listOf(10, 25, 50).forEach { n ->
                    FilterChip(selected = batchCount == n, onClick = { batchCount = n }, label = { Text(stringResource(R.string.dup_batch_next, n)) })
                }
            }
            androidx.compose.material3.HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Text(
                stringResource(R.string.keep_per_group_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
            androidx.compose.material3.DropdownMenuItem(text = { Text(stringResource(R.string.keep_newest)) }, onClick = { apply(KeepStrategy.NEWEST) })
            androidx.compose.material3.DropdownMenuItem(text = { Text(stringResource(R.string.keep_oldest)) }, onClick = { apply(KeepStrategy.OLDEST) })
            androidx.compose.material3.DropdownMenuItem(text = { Text(stringResource(R.string.keep_largest)) }, onClick = { apply(KeepStrategy.LARGEST) })
            androidx.compose.material3.DropdownMenuItem(text = { Text(stringResource(R.string.keep_smallest)) }, onClick = { apply(KeepStrategy.SMALLEST) })
            androidx.compose.material3.DropdownMenuItem(text = { Text(stringResource(R.string.keep_shortest_name)) }, onClick = { apply(KeepStrategy.SHORTEST_NAME) })
            androidx.compose.material3.DropdownMenuItem(text = { Text(stringResource(R.string.keep_shortest_path)) }, onClick = { apply(KeepStrategy.SHORTEST_PATH) })
            androidx.compose.material3.HorizontalDivider(Modifier.padding(vertical = 4.dp))
            if (folderFiltered) {
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(stringResource(R.string.dup_select_all_shown)) },
                    leadingIcon = { Icon(Icons.Default.FolderOpen, null) },
                    onClick = { onSelectShownFolder(); expanded = false },
                )
            }
            androidx.compose.material3.DropdownMenuItem(text = { Text(stringResource(R.string.selection_clear)) }, onClick = { onClear(); expanded = false })
        }
    }
}

@Composable
private fun DuplicateGroupCard(
    group: DuplicateGroup,
    similar: Boolean,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onView: (List<String>, Int) -> Unit,
    onSelectFolder: (String) -> Unit,
) {
    // Hoisted once per card instead of allocating a new SimpleDateFormat (locale data lookup,
    // not free) per file, per recomposition, inside the forEachIndexed below.
    val dateFormat = remember { java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault()) }
    // Opening any file hands the viewer the whole group's paths + that file's index, so swiping in
    // the viewer moves through only this group's copies rather than the surrounding library.
    val groupPaths = remember(group.files) { group.files.map { it.path } }
    // Groups start collapsed: an overview (count + summed total size + savings) plus a swipeable
    // thumbnail strip is all most reviews need - especially after an auto-select, where the strip's
    // check badges show at a glance what will be deleted. Expand only to fine-tune per-file marks.
    var expanded by remember(group.hash) { mutableStateOf(false) }
    val totalSize = remember(group.files) { group.files.sumOf { it.size } }
    val markedInGroup = group.files.count { it.path in selected }
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(
                            R.string.dup_group_header,
                            group.files.size,
                            if (similar) stringResource(R.string.similar) else stringResource(R.string.identical),
                            formatBytes(totalSize),
                            formatBytes(group.wastedBytes),
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                    if (markedInGroup > 0) {
                        Text(
                            stringResource(R.string.dup_group_marked_count, markedInGroup),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (!expanded) {
                // Collapsed: a horizontally swipeable strip of previews. Tapping one enlarges it in
                // the viewer; a check badge marks files already selected for deletion.
                LazyRow(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(group.files.take(MAX_INLINE_FILES), key = { it.path }) { file ->
                        val isSel = file.path in selected
                        Box(Modifier.size(72.dp).clip(RoundedCornerShape(Radius.sm)).clickable { onView(groupPaths, groupPaths.indexOf(file.path)) }) {
                            GalleryImage(path = file.path, contentDescription = file.name, modifier = Modifier.size(72.dp).sharedElementKey("media_${file.path}"))
                            if (file.mediaType == 2) {
                                Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(26.dp))
                                }
                            }
                            if (isSel) {
                                Box(Modifier.size(72.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)))
                                Icon(
                                    Icons.Default.CheckCircle, null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.align(Alignment.TopEnd).padding(3.dp).size(18.dp),
                                )
                            }
                        }
                    }
                }
                if (group.files.size > MAX_INLINE_FILES) {
                    Text(
                        stringResource(R.string.duplicate_group_more_files, group.files.size - MAX_INLINE_FILES),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                return@Column
            }

            // Defense in depth against a pathologically large group: even with anchor-based
            // clustering (see DuplicateScanner), nothing stops a real cluster of dozens of
            // legitimate burst-shot near-duplicates from being sizeable, and this card isn't itself
            // virtualized (only the outer LazyColumn of *groups* is) - a real device hit a
            // transitive-clustering bug that produced a single 1087-file group, and rendering every
            // row (thumbnail + shared-element transition + text) at once froze the main thread past
            // the ANR threshold. Bulk actions (applyKeepStrategy) still operate on the full
            // group.files list regardless of this display cap.
            group.files.take(MAX_INLINE_FILES).forEachIndexed { index, file ->
                // Only the checkbox sits on the right - the old per-row preview/folder icon pair
                // ate ~90dp of width and squeezed the path into two truncated lines. Preview is now
                // a tap on the thumbnail/info area, folder-select an inline chip below the metadata.
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(64.dp).clip(RoundedCornerShape(Radius.sm)).clickable { onView(groupPaths, index) }) {
                        GalleryImage(path = file.path, contentDescription = file.name, modifier = Modifier.size(64.dp).sharedElementKey("media_${file.path}"))
                        if (file.mediaType == 2) {
                            Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(Radius.sm)).clickable { onView(groupPaths, index) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(file.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            if (index == 0) {
                                Spacer(Modifier.width(6.dp))
                                Surface(shape = RoundedCornerShape(Radius.xs), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)) {
                                    Text(stringResource(R.string.newest), Modifier.padding(horizontal = 4.dp, vertical = 1.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                        Text(file.path, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Text(
                            buildString {
                                if (file.width > 0 && file.height > 0) append("${file.width}×${file.height} · ")
                                append(formatBytes(file.size))
                                append(" · ")
                                append(dateFormat.format(java.util.Date(file.modified)))
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (file.rating > 0 || file.tags.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (file.rating > 0) {
                                    Icon(Icons.Default.Star, null, tint = RatingStarColor, modifier = Modifier.size(12.dp))
                                    Text("${file.rating}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (file.tags.isNotEmpty()) Spacer(Modifier.width(6.dp))
                                }
                                if (file.tags.isNotEmpty()) {
                                    Text(file.tags.joinToString(", "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                        // Extends the selection to every other found duplicate that shares this file's
                        // folder - a folder holding one file the user wants gone usually holds the whole
                        // batch of junk copies, not just this one. Additive (never deselects), so it can
                        // be combined freely with manual per-file checkboxes across several folders.
                        // EXACT mode only, same reasoning as "Mark older" above: in SIMILAR mode the
                        // other files in that folder are merely perceptually similar, not verified
                        // identical, so mass-selecting them by folder carries the same risk this app
                        // already deliberately avoids for the other bulk-select action.
                        if (!similar) {
                            Surface(
                                onClick = { onSelectFolder(file.path) },
                                shape = RoundedCornerShape(Radius.lg),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                                modifier = Modifier.padding(top = 4.dp),
                            ) {
                                Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.FolderOpen, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(R.string.select_folder_duplicates_short), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                                }
                            }
                        }
                    }
                    Checkbox(checked = file.path in selected, onCheckedChange = { onToggle(file.path) })
                }
            }
            if (group.files.size > MAX_INLINE_FILES) {
                Text(
                    stringResource(R.string.duplicate_group_more_files, group.files.size - MAX_INLINE_FILES),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

private const val MAX_INLINE_FILES = 40

@Composable
private fun thresholdLabel(t: Int): String = when {
    t <= 2 -> stringResource(R.string.dup_threshold_almost_identical)
    t <= 6 -> stringResource(R.string.dup_threshold_strict)
    t <= 10 -> stringResource(R.string.dup_threshold_medium)
    t <= 14 -> stringResource(R.string.dup_threshold_loose)
    else -> stringResource(R.string.dup_threshold_very_loose)
}


private fun duplicateUriToPath(uri: Uri): String? {
    return try {
        val docId = DocumentsContract.getTreeDocumentId(uri)
        val split = docId.indexOf(':')
        if (split >= 0) {
            val type = docId.substring(0, split)
            val relative = docId.substring(split + 1)
            (if (type == "primary") "/storage/emulated/0/$relative" else "/storage/$type/$relative").trimEnd('/')
        } else null
    } catch (_: Exception) { null }
}

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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.automirrored.filled.PlaylistAddCheck
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
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
import androidx.compose.material3.OutlinedTextField
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.fossify.gallery.compose.components.GalleryImage
import org.fossify.gallery.compose.components.LibrarySearchField
import org.fossify.gallery.compose.util.sharedElementKey
import org.fossify.gallery.compose.theme.RatingStarColor
import java.io.File
import kotlin.math.min
import kotlin.math.roundToInt

/** How the result groups are ordered. SAVINGS (default) leads with the groups that reclaim the most
 * space; SIZE by total group size; COUNT by how many copies a group holds. */
private enum class DuplicateSort { SAVINGS, SIZE, COUNT }

/** Which result groups receive the next auto-selection strategy. */
private enum class AutoSelectScope(val nextCount: Int? = null) {
    SELECTED,
    ALL,
    NEXT_10(10),
    NEXT_25(25),
    NEXT_50(50),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicateFinderScreen(onBack: () -> Unit, initialFolder: String = "", onNavigateToViewer: (List<String>, Int) -> Unit = { _, _ -> }) {
    val vm: DuplicateFinderViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var showConfirmDialog by remember { mutableStateOf(false) }
    val defaultPath = Environment.getExternalStorageDirectory().absolutePath
    var currentFolder by remember { mutableStateOf(initialFolder.ifBlank { defaultPath }) }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) currentFolder = duplicateUriToPath(uri) ?: uri.toString()
    }

    val listState = rememberLazyListState()
    // Automatic cleanup is the common, low-effort path. The detailed ways of marking
    // individual groups stay available, but do not compete with it on first glance.
    var manualToolsExpanded by remember { mutableStateOf(false) }

    // Folder breakdown of the results: how many groups touch each folder. Drives the filter row
    // so a device-wide scan can be worked through folder by folder. Sorted by count so the busiest
    // folders come first.
    var substringQuery by remember { mutableStateOf("") }
    val folderCounts = remember(state.groups) {
        val m = LinkedHashMap<String, Int>()
        state.groups.forEach { g ->
            g.files.mapNotNull { File(it.path).parent }.toSet().forEach { m[it] = (m[it] ?: 0) + 1 }
        }
        m.entries.sortedByDescending { it.value }.map { it.key to it.value }
    }

    // Groups actually shown: all of them, or only those touching the active folder filter, then
    // ordered so the most rewarding cleanups come first. Default is "biggest savings" - the point of
    // the tool is reclaiming space, so the groups that free the most bytes lead.
    var sortMode by remember { mutableStateOf(DuplicateSort.SAVINGS) }
    val filteredGroups = remember(state.groups, state.folderFilter, sortMode) {
        val f = state.folderFilter
        val shown = if (f == null) state.groups else state.groups.filter { g -> g.files.any { File(it.path).parent == f } }
        when (sortMode) {
            DuplicateSort.SAVINGS -> shown.sortedByDescending { it.wastedBytes }
            DuplicateSort.SIZE -> shown.sortedByDescending { it.files.sumOf { f2 -> f2.size } }
            DuplicateSort.COUNT -> shown.sortedByDescending { it.files.size }
        }
    }

    // Group selection is deliberately separate from the file marks that will be deleted. It only
    // defines the target of the next auto-select action and is limited to what is currently shown,
    // so changing the folder filter can never leave an invisible group selected by surprise.
    var selectedGroupKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    val visibleGroupKeys = remember(filteredGroups) { filteredGroups.map { it.targetKey }.toSet() }
    val selectedVisibleGroupKeys = selectedGroupKeys intersect visibleGroupKeys
    LaunchedEffect(visibleGroupKeys) {
        selectedGroupKeys = selectedGroupKeys intersect visibleGroupKeys
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
        running = state.isScanning,
        onCancel = { vm.cancelScan(); onBack() },
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
                    // The cleanup helper lives with the result summary, where its scope is visible.
                    // Keep the app bar reserved for navigation and result-level controls.
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        bottomBar = {
            // Anchored at the bottom instead of stacked into the scrolling column, so marking files
            // never shrinks the list you're working - the whole screen stays available for review.
            if (state.selectedForDeletion.isNotEmpty()) {
                val sel = state.selectedForDeletion
                val selSize = state.groups.flatMap { it.files }.filter { it.path in sel }.sumOf { it.size }
                // Live survivor accounting, before the confirm dialog: how many groups still keep a
                // copy, and a loud red count for any group where every copy is marked (nothing would
                // survive). Turns "will I lose something?" into a glance at the bar.
                val affected = remember(state.groups, sel) { state.groups.filter { g -> g.files.any { it.path in sel } } }
                val fullyMarked = remember(affected, sel) { affected.count { g -> g.files.all { it.path in sel } } }
                val keepingCopy = affected.size - fullyMarked
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, tonalElevation = 3.dp, shadowElevation = 8.dp) {
                    Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.duplicate_marked_count, sel.size, formatBytes(selSize)), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
                            if (fullyMarked > 0) {
                                Text(stringResource(R.string.dup_confirm_all_deleted, fullyMarked), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium)
                            } else if (keepingCopy > 0) {
                                Text(stringResource(R.string.dup_confirm_keeps_copies, keepingCopy), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                            }
                        }
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
            } else {
                // No selection: surface the recycle-bin safety net as a visible "Undo" after a delete.
                org.fossify.gallery.compose.components.UndoBar(Modifier.navigationBarsPadding())
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Compact results header with a collapse toggle for the scan form below.
            if (state.groups.isNotEmpty()) {
                val totalWasted = remember(state.groups) { state.groups.sumOf { it.wastedBytes } }
                val totalDupes = remember(state.groups) { state.groups.sumOf { it.files.size - 1 } }
                org.fossify.gallery.compose.components.AnalysisResultHeader(
                    summary = stringResource(R.string.duplicate_scan_summary, state.groups.size, totalDupes, formatBytes(totalWasted)),
                    detail = state.restoredAt?.let { ts ->
                        stringResource(R.string.restored_scan_from, java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(ts)))
                    },
                ) {
                        Box {
                            var sortMenu by remember { mutableStateOf(false) }
                            IconButton(onClick = { sortMenu = true }) {
                                Icon(Icons.AutoMirrored.Filled.Sort, stringResource(R.string.dup_sort), tint = MaterialTheme.colorScheme.primary)
                            }
                            androidx.compose.material3.DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                                @Composable
                                fun sortItem(mode: DuplicateSort, labelRes: Int) {
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text(stringResource(labelRes)) },
                                        leadingIcon = { if (sortMode == mode) Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary) },
                                        onClick = { sortMode = mode; sortMenu = false },
                                    )
                                }
                                sortItem(DuplicateSort.SAVINGS, R.string.dup_sort_savings)
                                sortItem(DuplicateSort.SIZE, R.string.dup_sort_size)
                                sortItem(DuplicateSort.COUNT, R.string.dup_sort_count)
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
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AutoSelectAction(
                        folderFiltered = state.folderFilter != null,
                        selectedGroupCount = selectedVisibleGroupKeys.size,
                        emphasized = true,
                        onApplySelected = { strategy ->
                            vm.applyKeepStrategy(
                                strategy = strategy,
                                targetGroupKeys = selectedVisibleGroupKeys,
                                additive = false,
                                restrictToFolder = state.folderFilter,
                            )
                            cautionIfSimilar()
                        },
                        onApplyAll = { strategy ->
                            // "All groups" honors the folder filter: with a filter active it means
                            // all shown groups, and only their files in that folder are marked.
                            if (state.folderFilter != null) vm.applyKeepStrategy(strategy, visibleGroupKeys, additive = false, restrictToFolder = state.folderFilter)
                            else vm.applyKeepStrategy(strategy)
                            cautionIfSimilar()
                        },
                        onApplyNext = { strategy, n ->
                            val start = listState.firstVisibleItemIndex.coerceIn(0, (filteredGroups.size - 1).coerceAtLeast(0))
                            val groupKeys = filteredGroups.subList(start, min(start + n, filteredGroups.size)).map { it.targetKey }.toSet()
                            vm.applyKeepStrategy(strategy, groupKeys, additive = true, restrictToFolder = state.folderFilter)
                            cautionIfSimilar()
                        },
                        onSelectShownFolder = { state.folderFilter?.let { vm.selectFolder(it) } },
                        onInvert = { vm.invertSelection(); cautionIfSimilar() },
                        hasSelection = state.selectedForDeletion.isNotEmpty(),
                        onClear = { vm.clearSelection() },
                    )
                    TextButton(onClick = { manualToolsExpanded = !manualToolsExpanded }) {
                        Text(stringResource(R.string.dup_manual_selection))
                        Icon(
                            if (manualToolsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.padding(start = 4.dp).size(18.dp),
                        )
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
                // How many marked files sit in each folder, so a chip shows at a glance where the
                // current selection lives - useful when working folder by folder.
                val markedByFolder = remember(state.groups, state.selectedForDeletion) {
                    state.selectedForDeletion.groupingBy { File(it).parent ?: "" }.eachCount()
                }
                @Composable
                fun MarkedBadge(n: Int) {
                    if (n > 0) {
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF43A047), modifier = Modifier.size(13.dp))
                        Text("$n", style = MaterialTheme.typography.labelMedium, color = Color(0xFF43A047), fontWeight = FontWeight.SemiBold)
                    }
                }
                LazyRow(
                    Modifier.fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        FilterChip(
                            selected = state.folderFilter == null,
                            onClick = { vm.setFolderFilter(null) },
                            label = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("${stringResource(R.string.dup_folder_all)} · ${state.groups.size}")
                                    MarkedBadge(state.selectedForDeletion.size)
                                }
                            },
                        )
                    }
                    items(folderCounts, key = { it.first }) { (folder, count) ->
                        FilterChip(
                            selected = state.folderFilter == folder,
                            onClick = { vm.setFolderFilter(if (state.folderFilter == folder) null else folder) },
                            leadingIcon = { Icon(Icons.Default.FolderOpen, null, Modifier.size(16.dp)) },
                            label = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("${folder.substringAfterLast('/')} · $count", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    MarkedBadge(markedByFolder[folder] ?: 0)
                                }
                            },
                        )
                    }
                }
            }

            // Select copies to delete by name or location: mark every shown file whose path (which
            // includes the filename) contains - or explicitly does NOT contain - the query. Handy for
            // "delete every copy under /WhatsApp/" or "keep only the ones named IMG_*". A group is
            // never fully marked (the VM always spares one copy), so this can't wipe a whole group.
            if (state.groups.isNotEmpty()) {
                AnimatedVisibility(visible = manualToolsExpanded || selectedVisibleGroupKeys.isNotEmpty()) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)) {
                    // One-tap for the active folder chip: mark, across every shown pair, the copy that
                    // sits in this filtered folder - so a whole "area" of junk copies goes in one tap
                    // while the originals in other folders stay as keepers. Scoped to the filter (the VM
                    // only reaches shown groups), EXACT only (same reason as the per-file folder chip).
                    if (state.folderFilter != null && state.mode == DuplicateMode.EXACT) {
                        val folderName = state.folderFilter!!.substringAfterLast('/')
                        AssistChip(
                            onClick = { vm.selectFolder(state.folderFilter!!); cautionIfSimilar() },
                            leadingIcon = { Icon(Icons.Default.FolderOpen, null, Modifier.size(AssistChipDefaults.IconSize)) },
                            label = { Text(stringResource(R.string.dup_select_in_filtered_folder, folderName), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                    LibrarySearchField(
                        value = substringQuery,
                        onValueChange = { substringQuery = it },
                        placeholderRes = R.string.dup_select_by_name_hint,
                    )
                    val q = substringQuery.trim()
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(
                            enabled = q.isNotEmpty(),
                            onClick = { vm.selectBySubstring(q, contains = true); cautionIfSimilar() },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAddCheck, null, Modifier.size(AssistChipDefaults.IconSize)) },
                            label = { Text(stringResource(R.string.dup_select_matching)) },
                        )
                        AssistChip(
                            enabled = q.isNotEmpty(),
                            onClick = { vm.selectBySubstring(q, contains = false); cautionIfSimilar() },
                            leadingIcon = { Icon(Icons.Default.Block, null, Modifier.size(AssistChipDefaults.IconSize)) },
                            label = { Text(stringResource(R.string.dup_select_not_matching)) },
                        )
                    }
                    }
                }
            }

            if (state.groups.isNotEmpty()) {
                AnimatedVisibility(visible = manualToolsExpanded || selectedVisibleGroupKeys.isNotEmpty()) {
                    GroupSelectionBar(
                        selectedCount = selectedVisibleGroupKeys.size,
                        allVisibleSelected = visibleGroupKeys.isNotEmpty() && selectedVisibleGroupKeys.size == visibleGroupKeys.size,
                        onSelectVisible = { selectedGroupKeys = selectedGroupKeys + visibleGroupKeys },
                        onClear = { selectedGroupKeys = emptySet() },
                    )
                }
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp)) {
                    // Key by the group's first file path, not its hash: a file lives in exactly one
                    // group so this is always unique, whereas two groups can share a hash (SIMILAR
                    // clustering, or a partial-hash collision) - a duplicate LazyColumn key makes
                    // Compose reuse one card's composition (incl. checkbox state) for the other.
                    items(filteredGroups, key = { it.targetKey }) { group ->
                        val groupKey = group.targetKey
                        DuplicateGroupCard(
                            group = group,
                            similar = state.mode == DuplicateMode.SIMILAR,
                            selected = state.selectedForDeletion,
                            groupSelected = groupKey in selectedVisibleGroupKeys,
                            onToggleGroup = {
                                selectedGroupKeys = if (groupKey in selectedGroupKeys) {
                                    selectedGroupKeys - groupKey
                                } else {
                                    selectedGroupKeys + groupKey
                                }
                            },
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
                    note = stringResource(R.string.dup_safety_note),
                    actionLabel = stringResource(R.string.cd_search),
                    onAction = { vm.startScan(currentFolder) },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }
        }
    }

    if (showConfirmDialog) {
        val sel = state.selectedForDeletion
        val count = sel.size
        val selSize = state.groups.flatMap { it.files }.filter { it.path in sel }.sumOf { it.size }
        // Per-group survivor accounting drives the reassurance ("N groups keep a copy") and the two
        // warnings (a group where every copy is marked; a rated/favorite file about to be deleted) -
        // the visible safety signals that turn "did I just lose something?" into a confident action.
        val affected = state.groups.filter { g -> g.files.any { it.path in sel } }
        val fullyMarked = affected.count { g -> g.files.all { it.path in sel } }
        val keepingCopy = affected.size - fullyMarked
        val ratedAffected = state.groups.flatMap { it.files }.count { it.path in sel && (it.rating > 0 || it.isFavorite) }
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text(stringResource(R.string.confirm_delete_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.move_to_recycle_bin_confirm_size, count, formatBytes(selSize)))
                    Spacer(Modifier.height(2.dp))
                    Text(
                        stringResource(R.string.dup_confirm_retention, RECYCLE_BIN_DAYS),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.mode == DuplicateMode.SIMILAR) {
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.dup_confirm_similar), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                        }
                    }
                    if (keepingCopy > 0) {
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF43A047), modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.dup_confirm_keeps_copies, keepingCopy), style = MaterialTheme.typography.labelMedium, color = Color(0xFF43A047))
                        }
                    }
                    if (fullyMarked > 0) {
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.dup_confirm_all_deleted, fullyMarked), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                        }
                    }
                    if (ratedAffected > 0) {
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, null, tint = RatingStarColor, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.dup_confirm_rated, ratedAffected), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showConfirmDialog = false; vm.deleteSelected() }) { Text(stringResource(org.fossify.commons.R.string.delete), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { showConfirmDialog = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/**
 * The top-bar "Auto-select" menu. Beyond picking which file each group keeps, it carries a target
 * scope: explicitly selected group cards, all groups, or the next 10/25/50 groups from the current
 * scroll position. "Mark all shown" appears when a folder filter is active, clearing that one
 * folder's copies in a tap.
 */
@Composable
private fun AutoSelectAction(
    folderFiltered: Boolean,
    selectedGroupCount: Int,
    emphasized: Boolean = false,
    onApplySelected: (KeepStrategy) -> Unit,
    onApplyAll: (KeepStrategy) -> Unit,
    onApplyNext: (KeepStrategy, Int) -> Unit,
    onSelectShownFolder: () -> Unit,
    onInvert: () -> Unit,
    hasSelection: Boolean,
    onClear: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var scope by remember { mutableStateOf(AutoSelectScope.ALL) }

    // As soon as group cards are selected, make that safer, narrower scope the default. An explicit
    // switch back to "all" remains sticky while the selected set merely grows or shrinks.
    LaunchedEffect(selectedGroupCount > 0) {
        if (selectedGroupCount > 0) scope = AutoSelectScope.SELECTED
        else if (scope == AutoSelectScope.SELECTED) scope = AutoSelectScope.ALL
    }

    fun apply(strategy: KeepStrategy) {
        when (scope) {
            AutoSelectScope.SELECTED -> if (selectedGroupCount > 0) onApplySelected(strategy) else onApplyAll(strategy)
            AutoSelectScope.ALL -> onApplyAll(strategy)
            else -> onApplyNext(strategy, requireNotNull(scope.nextCount))
        }
        expanded = false
    }

    Box {
        if (emphasized) {
            Button(onClick = { expanded = true }) {
                Icon(Icons.AutoMirrored.Filled.PlaylistAddCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.auto_select))
            }
        } else {
            TextButton(onClick = { expanded = true }) { Text(stringResource(R.string.auto_select)) }
        }
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
                if (selectedGroupCount > 0) {
                    FilterChip(
                        selected = scope == AutoSelectScope.SELECTED,
                        onClick = { scope = AutoSelectScope.SELECTED },
                        label = { Text(stringResource(R.string.dup_batch_selected, selectedGroupCount)) },
                    )
                }
                FilterChip(selected = scope == AutoSelectScope.ALL, onClick = { scope = AutoSelectScope.ALL }, label = { Text(stringResource(R.string.dup_batch_all)) })
                listOf(AutoSelectScope.NEXT_10, AutoSelectScope.NEXT_25, AutoSelectScope.NEXT_50).forEach { nextScope ->
                    val n = requireNotNull(nextScope.nextCount)
                    FilterChip(selected = scope == nextScope, onClick = { scope = nextScope }, label = { Text(stringResource(R.string.dup_batch_next, n)) })
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
            androidx.compose.material3.DropdownMenuItem(
                text = { Text(stringResource(R.string.keep_highest_rated)) },
                leadingIcon = { Icon(Icons.Default.Star, null, tint = RatingStarColor) },
                onClick = { apply(KeepStrategy.HIGHEST_RATED) },
            )
            androidx.compose.material3.HorizontalDivider(Modifier.padding(vertical = 4.dp))
            if (folderFiltered) {
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(stringResource(R.string.dup_select_all_shown)) },
                    leadingIcon = { Icon(Icons.Default.FolderOpen, null) },
                    onClick = { onSelectShownFolder(); expanded = false },
                )
            }
            if (hasSelection) {
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(stringResource(R.string.dup_invert_selection)) },
                    leadingIcon = { Icon(Icons.Default.SwapHoriz, null) },
                    onClick = { onInvert(); expanded = false },
                )
            }
            androidx.compose.material3.DropdownMenuItem(text = { Text(stringResource(R.string.selection_clear)) }, onClick = { onClear(); expanded = false })
        }
    }
}

@Composable
private fun GroupSelectionBar(
    selectedCount: Int,
    allVisibleSelected: Boolean,
    onSelectVisible: () -> Unit,
    onClear: () -> Unit,
) {
    val hasSelection = selectedCount > 0
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(Radius.md),
        color = if (hasSelection) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.PlaylistAddCheck,
                contentDescription = null,
                tint = if (hasSelection) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (hasSelection) {
                    pluralStringResource(R.plurals.dup_groups_selected, selectedCount, selectedCount)
                } else {
                    stringResource(R.string.dup_group_selection_hint)
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (hasSelection) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!allVisibleSelected) {
                TextButton(onClick = onSelectVisible) { Text(stringResource(R.string.dup_select_visible_groups)) }
            }
            if (hasSelection) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.Close, stringResource(R.string.dup_clear_group_selection))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DuplicateGroupCard(
    group: DuplicateGroup,
    similar: Boolean,
    selected: Set<String>,
    groupSelected: Boolean,
    onToggleGroup: () -> Unit,
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
    var expanded by remember(group.files.firstOrNull()?.path ?: group.hash) { mutableStateOf(false) }
    val totalSize = remember(group.files) { group.files.sumOf { it.size } }
    val markedInGroup = group.files.count { it.path in selected }
    // Every copy in the group is marked = nothing would survive. The recycle bin still makes it
    // recoverable, but flag it loudly so the user never destroys a group unknowingly.
    val allMarked = markedInGroup > 0 && markedInGroup == group.files.size
    val keepGreen = Color(0xFF43A047)
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(
            containerColor = if (groupSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        ),
        border = if (groupSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = groupSelected,
                    onCheckedChange = { onToggleGroup() },
                )
                Row(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(Radius.sm)).clickable { expanded = !expanded },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
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
                        if (!similar) {
                            // EXACT groups are a real byte-for-byte hash match, not a guess - say so, so
                            // deleting a "duplicate" feels safe rather than like trusting a heuristic.
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, null, tint = keepGreen, modifier = Modifier.size(11.dp))
                                Spacer(Modifier.width(3.dp))
                                Text(stringResource(R.string.dup_verified_exact), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (allMarked) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(13.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    stringResource(R.string.dup_group_all_marked),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        } else if (markedInGroup > 0) {
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
                        // Tap = enlarge in the viewer; long-press = mark/unmark for deletion, so a
                        // single file can be selected right from the strip without expanding the group.
                        Box(
                            Modifier.size(72.dp).clip(RoundedCornerShape(Radius.sm)).combinedClickable(
                                onClick = { onView(groupPaths, groupPaths.indexOf(file.path)) },
                                onLongClick = { onToggle(file.path) },
                            )
                        ) {
                            GalleryImage(path = file.path, contentDescription = file.name, modifier = Modifier.size(72.dp).sharedElementKey("media_${file.path}"))
                            if (file.mediaType == 2) {
                                Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(26.dp))
                                }
                            }
                            if (isSel) {
                                // Marked for deletion: red scrim + trash badge.
                                Box(Modifier.size(72.dp).background(MaterialTheme.colorScheme.error.copy(alpha = 0.4f)))
                                Icon(
                                    Icons.Default.Delete, null,
                                    tint = Color.White,
                                    modifier = Modifier.align(Alignment.TopEnd).padding(3.dp).size(18.dp),
                                )
                            } else if (markedInGroup > 0) {
                                // A survivor while others in the group are marked: green "keeps" badge,
                                // so it's obvious at a glance which copy will remain.
                                Icon(
                                    Icons.Default.CheckCircle, null,
                                    tint = keepGreen,
                                    modifier = Modifier.align(Alignment.TopStart).padding(3.dp).size(18.dp),
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
                            // With a selection in the group, a survivor gets a green "keeps" chip so
                            // it's unmistakable which copy stays; otherwise the newest is just labelled.
                            if (markedInGroup > 0 && file.path !in selected) {
                                Spacer(Modifier.width(6.dp))
                                Surface(shape = RoundedCornerShape(Radius.xs), color = keepGreen.copy(alpha = 0.18f)) {
                                    Text(stringResource(R.string.dup_stays), Modifier.padding(horizontal = 4.dp, vertical = 1.dp), style = MaterialTheme.typography.labelSmall, color = keepGreen, fontWeight = FontWeight.SemiBold)
                                }
                            } else if (markedInGroup == 0 && index == 0) {
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

// Mirrors RecycleBinCleanupWorker.RETENTION_MS (30 days) - shown in the delete confirm so the user
// knows exactly how long a "deleted" file stays recoverable.
private const val RECYCLE_BIN_DAYS = 30

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

package org.fossify.gallery.compose.screens.analysis

import android.os.Environment
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.fossify.gallery.R
import org.fossify.gallery.compose.components.AnalysisResultHeader
import org.fossify.gallery.compose.components.EmptyState
import org.fossify.gallery.compose.components.LibrarySearchField
import org.fossify.gallery.compose.components.UndoBar
import org.fossify.gallery.compose.theme.Radius
import org.fossify.gallery.compose.util.rememberDoubleBackGuard
import org.fossify.gallery.helpers.RefreshBus
import org.fossify.gallery.helpers.formatBytes
import java.io.File
import java.text.DateFormat
import java.util.Date
import kotlin.math.min
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicateFinderScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    initialFolder: String = "",
    onNavigateToViewer: (List<String>, Int) -> Unit = { _, _ -> },
    vm: DuplicateFinderViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val res = LocalResources.current
    var showConfirmDialog by remember { mutableStateOf(false) }
    val defaultPath = Environment.getExternalStorageDirectory().absolutePath
    var currentFolder by remember { mutableStateOf(initialFolder.ifBlank { defaultPath }) }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val resolved = runCatching {
                resolveTreeDocumentPath(DocumentsContract.getTreeDocumentId(uri))
            }.getOrNull()
            currentFolder = resolved ?: uri.toString()
        }
    }

    val listState = rememberLazyListState()
    var manualToolsExpanded by remember { mutableStateOf(false) }
    var substringQuery by remember { mutableStateOf("") }
    val folderCounts = remember(state.groups) { buildFolderGroupCounts(state.groups) }

    var sortMode by remember { mutableStateOf(DuplicateSort.SAVINGS) }
    val filteredGroups = remember(state.groups, state.folderFilter, sortMode) {
        sortAndFilterDuplicateGroups(state.groups, state.folderFilter, sortMode)
    }

    var selectedGroupKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    val visibleGroupKeys = remember(filteredGroups) { filteredGroups.map { it.targetKey }.toSet() }
    val selectedVisibleGroupKeys = selectedGroupKeys intersect visibleGroupKeys
    LaunchedEffect(visibleGroupKeys) {
        selectedGroupKeys = selectedGroupKeys intersect visibleGroupKeys
    }
    LaunchedEffect(state.folderFilter, filteredGroups.isEmpty()) {
        if (state.folderFilter != null && filteredGroups.isEmpty() && state.groups.isNotEmpty()) {
            vm.setFolderFilter(null)
        }
    }

    var configExpanded by remember { mutableStateOf(true) }
    LaunchedEffect(state.groups.isEmpty()) { configExpanded = state.groups.isEmpty() }
    LaunchedEffect(Unit) {
        vm.revalidate()
        RefreshBus.events.collect { vm.revalidate() }
    }

    val guardedBack = rememberDoubleBackGuard(
        enabled = state.isScanning || state.groups.isNotEmpty(),
        onExit = onBack,
        running = state.isScanning,
        onCancel = {
            vm.cancelScan()
            onBack()
        },
    )

    val deletionSummary = remember(state.groups, state.selectedForDeletion) {
        computeDuplicateDeletionSummary(state.groups, state.selectedForDeletion)
    }

    fun cautionIfSimilar() {
        showSimilarCautionToast(ctx, res, state.mode)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_find_duplicates), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = guardedBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        bottomBar = {
            DuplicateBottomBar(
                summary = deletionSummary,
                onClear = { vm.clearSelection() },
                onRequestDelete = { showConfirmDialog = true },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            if (state.groups.isNotEmpty()) {
                DuplicateResultsToolbar(
                    groups = state.groups,
                    restoredAt = state.restoredAt,
                    sortMode = sortMode,
                    configExpanded = configExpanded,
                    folderFilter = state.folderFilter,
                    selectedGroupCount = selectedVisibleGroupKeys.size,
                    hasSelection = state.selectedForDeletion.isNotEmpty(),
                    manualToolsExpanded = manualToolsExpanded,
                    onSortChange = { sortMode = it },
                    onToggleConfig = { configExpanded = !configExpanded },
                    onToggleManualTools = { manualToolsExpanded = !manualToolsExpanded },
                    onApplySelection = { strategy ->
                        vm.applyKeepStrategy(
                            strategy = strategy,
                            targetGroupKeys = selectedVisibleGroupKeys,
                            additive = false,
                            restrictToFolder = state.folderFilter,
                        )
                        cautionIfSimilar()
                    },
                    onApplyAll = { strategy ->
                        if (state.folderFilter != null) {
                            vm.applyKeepStrategy(strategy, visibleGroupKeys, false, state.folderFilter)
                        } else {
                            vm.applyKeepStrategy(strategy)
                        }
                        cautionIfSimilar()
                    },
                    onApplyNext = { strategy, count ->
                        val maxStart = (filteredGroups.size - 1).coerceAtLeast(0)
                        val start = listState.firstVisibleItemIndex.coerceIn(0, maxStart)
                        val end = min(start + count, filteredGroups.size)
                        val keys = filteredGroups.subList(start, end).map { it.targetKey }.toSet()
                        vm.applyKeepStrategy(strategy, keys, additive = true, restrictToFolder = state.folderFilter)
                        cautionIfSimilar()
                    },
                    onSelectShownFolder = { state.folderFilter?.let { vm.selectFolder(it) } },
                    onInvert = {
                        vm.invertSelection()
                        cautionIfSimilar()
                    },
                    onClearSelection = { vm.clearSelection() },
                )
            }

            AnimatedVisibility(visible = configExpanded) {
                DuplicateScanConfigForm(
                    state = state,
                    currentFolder = currentFolder,
                    onPickFolder = { folderPicker.launch(null) },
                    onToggleScan = {
                        if (state.isScanning) vm.cancelScan() else vm.startScan(currentFolder)
                    },
                    onSetScope = { vm.setScope(it) },
                    onSetMode = { vm.setMode(it) },
                    onSetThreshold = { vm.setThreshold(it) },
                )
            }

            DuplicateResultsListSection(
                state = state,
                listState = listState,
                filteredGroups = filteredGroups,
                folderCounts = folderCounts,
                visibleGroupKeys = visibleGroupKeys,
                selectedGroupKeys = selectedGroupKeys,
                selectedVisibleGroupKeys = selectedVisibleGroupKeys,
                manualToolsExpanded = manualToolsExpanded,
                substringQuery = substringQuery,
                onSubstringQueryChange = { substringQuery = it },
                onSelectFolderFilter = { vm.setFolderFilter(it) },
                onSelectFilteredFolder = {
                    state.folderFilter?.let {
                        vm.selectFolder(it)
                        cautionIfSimilar()
                    }
                },
                onSelectBySubstring = { query, contains ->
                    vm.selectBySubstring(query, contains = contains)
                    cautionIfSimilar()
                },
                onUpdateSelectedGroupKeys = { selectedGroupKeys = it },
                onToggleFileSelection = { vm.toggleSelection(it) },
                onNavigateToViewer = onNavigateToViewer,
                onSelectAllInFolder = { vm.selectAllInFolder(it) },
                onStartScan = { vm.startScan(currentFolder) },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        }
    }

    if (showConfirmDialog) {
        DuplicateDeleteConfirmDialog(
            summary = deletionSummary,
            isSimilarMode = state.mode == DuplicateMode.SIMILAR,
            onConfirm = {
                showConfirmDialog = false
                vm.deleteSelected()
            },
            onDismiss = { showConfirmDialog = false },
        )
    }
}

private fun showSimilarCautionToast(
    ctx: android.content.Context,
    res: android.content.res.Resources,
    mode: DuplicateMode,
) {
    if (mode == DuplicateMode.SIMILAR) {
        Toast.makeText(ctx, res.getString(R.string.similar_select_caution), Toast.LENGTH_SHORT).show()
    }
}

private fun buildFolderGroupCounts(groups: List<DuplicateGroup>): List<Pair<String, Int>> {
    val counts = LinkedHashMap<String, Int>()
    groups.forEach { group ->
        group.files.mapNotNull { File(it.path).parent }.toSet().forEach { folder ->
            counts[folder] = (counts[folder] ?: 0) + 1
        }
    }
    return counts.entries.sortedByDescending { it.value }.map { it.key to it.value }
}

@Composable
private fun DuplicateBottomBar(
    summary: DuplicateDeletionSummary,
    onClear: () -> Unit,
    onRequestDelete: () -> Unit,
) {
    if (summary.selectedCount == 0) {
        UndoBar(Modifier.navigationBarsPadding())
        return
    }
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(
                        R.string.duplicate_marked_count,
                        summary.selectedCount,
                        formatBytes(summary.selectedBytes),
                    ),
                    style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.Medium,
                )
                val metaStyle = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum")
                if (summary.fullyMarkedGroups > 0) {
                    Text(
                        stringResource(R.string.dup_confirm_all_deleted, summary.fullyMarkedGroups),
                        style = metaStyle,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium,
                    )
                } else if (summary.keepingCopyGroups > 0) {
                    Text(
                        stringResource(R.string.dup_confirm_keeps_copies, summary.keepingCopyGroups),
                        style = metaStyle,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            TextButton(onClick = onClear) {
                Text(stringResource(R.string.action_empty))
            }
            Spacer(Modifier.width(4.dp))
            Button(
                onClick = onRequestDelete,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(org.fossify.commons.R.string.delete))
            }
        }
    }
}

@Composable
private fun DuplicateResultsToolbar(
    groups: List<DuplicateGroup>,
    restoredAt: Long?,
    sortMode: DuplicateSort,
    configExpanded: Boolean,
    folderFilter: String?,
    selectedGroupCount: Int,
    hasSelection: Boolean,
    manualToolsExpanded: Boolean,
    onSortChange: (DuplicateSort) -> Unit,
    onToggleConfig: () -> Unit,
    onToggleManualTools: () -> Unit,
    onApplySelection: (KeepStrategy) -> Unit,
    onApplyAll: (KeepStrategy) -> Unit,
    onApplyNext: (KeepStrategy, Int) -> Unit,
    onSelectShownFolder: () -> Unit,
    onInvert: () -> Unit,
    onClearSelection: () -> Unit,
) {
    val totalWasted = remember(groups) { groups.sumOf { it.wastedBytes } }
    val totalDupes = remember(groups) { groups.sumOf { it.files.size - 1 } }
    val restoredDetail = restoredAt?.let { ts ->
        val formatted = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(ts))
        stringResource(R.string.restored_scan_from, formatted)
    }

    AnalysisResultHeader(
        summary = stringResource(
            R.string.duplicate_scan_summary,
            groups.size,
            totalDupes,
            formatBytes(totalWasted),
        ),
        detail = restoredDetail,
    ) {
        DuplicateSortMenuButton(sortMode = sortMode, onSortChange = onSortChange)
        IconButton(onClick = onToggleConfig) {
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
            folderFiltered = folderFilter != null,
            selectedGroupCount = selectedGroupCount,
            hasSelection = hasSelection,
            onApplySelection = onApplySelection,
            onApplyAll = onApplyAll,
            onApplyNext = onApplyNext,
            onSelectShownFolder = onSelectShownFolder,
            onInvert = onInvert,
            onClear = onClearSelection,
            emphasized = true,
        )
        TextButton(onClick = onToggleManualTools) {
            Text(stringResource(R.string.dup_manual_selection))
            Icon(
                if (manualToolsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                modifier = Modifier.padding(start = 4.dp).size(18.dp),
            )
        }
    }
}

@Composable
private fun DuplicateSortMenuButton(
    sortMode: DuplicateSort,
    onSortChange: (DuplicateSort) -> Unit,
) {
    Box {
        var sortMenu by remember { mutableStateOf(false) }
        IconButton(onClick = { sortMenu = true }) {
            Icon(
                Icons.AutoMirrored.Filled.Sort,
                stringResource(R.string.dup_sort),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
            DUPLICATE_SORT_ITEMS.forEach { (mode, labelRes) ->
                DropdownMenuItem(
                    text = { Text(stringResource(labelRes)) },
                    leadingIcon = {
                        if (sortMode == mode) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                    onClick = {
                        onSortChange(mode)
                        sortMenu = false
                    },
                )
            }
        }
    }
}

private val DUPLICATE_SORT_ITEMS = listOf(
    DuplicateSort.SAVINGS to R.string.dup_sort_savings,
    DuplicateSort.SIZE to R.string.dup_sort_size,
    DuplicateSort.COUNT to R.string.dup_sort_count,
)

@Composable
private fun DuplicateScanConfigForm(
    state: DuplicateState,
    currentFolder: String,
    onPickFolder: () -> Unit,
    onToggleScan: () -> Unit,
    onSetScope: (DuplicateScope) -> Unit,
    onSetMode: (DuplicateMode) -> Unit,
    onSetThreshold: (Int) -> Unit,
) {
    Column {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.scope == DuplicateScope.FOLDER) {
                Surface(
                    modifier = Modifier.weight(1f).clickable(onClick = onPickFolder),
                    shape = RoundedCornerShape(Radius.md),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            currentFolder.substringAfterLast('/').ifEmpty {
                                stringResource(R.string.internal_storage)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
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
            Button(onClick = onToggleScan) {
                if (state.isScanning) {
                    CircularProgressIndicator(
                        Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(stringResource(if (state.isScanning) R.string.cancel else R.string.cd_search))
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

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = state.scope == DuplicateScope.FOLDER,
                onClick = { onSetScope(DuplicateScope.FOLDER) },
                enabled = !state.isScanning,
                label = { Text(stringResource(R.string.dup_scope_folder)) },
            )
            FilterChip(
                selected = state.scope == DuplicateScope.LAST_WEEK,
                onClick = { onSetScope(DuplicateScope.LAST_WEEK) },
                enabled = !state.isScanning,
                label = { Text(stringResource(R.string.dup_scope_last_week)) },
            )
            FilterChip(
                selected = state.scope == DuplicateScope.LAST_MONTH,
                onClick = { onSetScope(DuplicateScope.LAST_MONTH) },
                enabled = !state.isScanning,
                label = { Text(stringResource(R.string.dup_scope_last_month)) },
            )
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = state.mode == DuplicateMode.EXACT,
                onClick = { onSetMode(DuplicateMode.EXACT) },
                enabled = !state.isScanning,
                label = { Text(stringResource(R.string.dup_mode_exact)) },
            )
            FilterChip(
                selected = state.mode == DuplicateMode.SIMILAR,
                onClick = { onSetMode(DuplicateMode.SIMILAR) },
                enabled = !state.isScanning && state.scope == DuplicateScope.FOLDER,
                label = { Text(stringResource(R.string.dup_mode_similar)) },
            )
        }
        Text(
            stringResource(
                if (state.mode == DuplicateMode.SIMILAR) {
                    R.string.dup_desc_similar
                } else {
                    R.string.dup_desc_exact
                },
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
        )

        if (state.mode == DuplicateMode.SIMILAR) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.tolerance),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${state.similarThreshold} · ${stringResource(thresholdLabelRes(state.similarThreshold))}",
                        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Slider(
                    value = state.similarThreshold.toFloat(),
                    onValueChange = { onSetThreshold(it.roundToInt()) },
                    valueRange = 0f..20f,
                    steps = 19,
                    enabled = !state.isScanning,
                )
            }
        }
    }
}

@Composable
private fun DuplicateFolderFilterRow(
    groups: List<DuplicateGroup>,
    folderCounts: List<Pair<String, Int>>,
    folderFilter: String?,
    selectedForDeletion: Set<String>,
    onSelectFolderFilter: (String?) -> Unit,
) {
    val markedByFolder = remember(groups, selectedForDeletion) {
        selectedForDeletion.groupingBy { File(it).parent ?: "" }.eachCount()
    }
    LazyRow(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FilterChip(
                selected = folderFilter == null,
                onClick = { onSelectFolderFilter(null) },
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${stringResource(R.string.dup_folder_all)} · ${groups.size}")
                        MarkedBadge(selectedForDeletion.size)
                    }
                },
            )
        }
        items(folderCounts, key = { it.first }) { (folder, count) ->
            FilterChip(
                selected = folderFilter == folder,
                onClick = { onSelectFolderFilter(if (folderFilter == folder) null else folder) },
                leadingIcon = { Icon(Icons.Default.FolderOpen, null, Modifier.size(16.dp)) },
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${folder.substringAfterLast('/')} · $count",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        MarkedBadge(markedByFolder[folder] ?: 0)
                    }
                },
            )
        }
    }
}

@Composable
private fun DuplicateManualSelectionPanel(
    folderFilter: String?,
    isExactMode: Boolean,
    substringQuery: String,
    onSubstringQueryChange: (String) -> Unit,
    onSelectFilteredFolder: () -> Unit,
    onSelectBySubstring: (String, Boolean) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)) {
        if (folderFilter != null && isExactMode) {
            val folderName = folderFilter.substringAfterLast('/')
            AssistChip(
                onClick = onSelectFilteredFolder,
                leadingIcon = {
                    Icon(Icons.Default.FolderOpen, null, Modifier.size(AssistChipDefaults.IconSize))
                },
                label = {
                    Text(
                        stringResource(R.string.dup_select_in_filtered_folder, folderName),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        LibrarySearchField(
            value = substringQuery,
            onValueChange = onSubstringQueryChange,
            placeholderRes = R.string.dup_select_by_name_hint,
        )
        val trimmed = substringQuery.trim()
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AssistChip(
                enabled = trimmed.isNotEmpty(),
                onClick = { onSelectBySubstring(trimmed, true) },
                leadingIcon = {
                    Icon(
                        Icons.AutoMirrored.Filled.PlaylistAddCheck,
                        contentDescription = null,
                        modifier = Modifier.size(AssistChipDefaults.IconSize),
                    )
                },
                label = { Text(stringResource(R.string.dup_select_matching)) },
            )
            AssistChip(
                enabled = trimmed.isNotEmpty(),
                onClick = { onSelectBySubstring(trimmed, false) },
                leadingIcon = {
                    Icon(Icons.Default.Block, null, Modifier.size(AssistChipDefaults.IconSize))
                },
                label = { Text(stringResource(R.string.dup_select_not_matching)) },
            )
        }
    }
}

@Composable
private fun DuplicateResultsListSection(
    state: DuplicateState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    filteredGroups: List<DuplicateGroup>,
    folderCounts: List<Pair<String, Int>>,
    visibleGroupKeys: Set<String>,
    selectedGroupKeys: Set<String>,
    selectedVisibleGroupKeys: Set<String>,
    manualToolsExpanded: Boolean,
    substringQuery: String,
    onSubstringQueryChange: (String) -> Unit,
    onSelectFolderFilter: (String?) -> Unit,
    onSelectFilteredFolder: () -> Unit,
    onSelectBySubstring: (String, Boolean) -> Unit,
    onUpdateSelectedGroupKeys: (Set<String>) -> Unit,
    onToggleFileSelection: (String) -> Unit,
    onNavigateToViewer: (List<String>, Int) -> Unit,
    onSelectAllInFolder: (String) -> Unit,
    onStartScan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        if (state.isScanning) {
            LinearProgressIndicator(
                progress = { state.progress / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                state.phase,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            )
        }
        if (state.groups.isNotEmpty() && folderCounts.size > 1) {
            DuplicateFolderFilterRow(
                groups = state.groups,
                folderCounts = folderCounts,
                folderFilter = state.folderFilter,
                selectedForDeletion = state.selectedForDeletion,
                onSelectFolderFilter = onSelectFolderFilter,
            )
        }
        if (state.groups.isNotEmpty()) {
            AnimatedVisibility(visible = manualToolsExpanded || selectedVisibleGroupKeys.isNotEmpty()) {
                Column {
                    DuplicateManualSelectionPanel(
                        folderFilter = state.folderFilter,
                        isExactMode = state.mode == DuplicateMode.EXACT,
                        substringQuery = substringQuery,
                        onSubstringQueryChange = onSubstringQueryChange,
                        onSelectFilteredFolder = onSelectFilteredFolder,
                        onSelectBySubstring = onSelectBySubstring,
                    )
                    GroupSelectionBar(
                        selectedCount = selectedVisibleGroupKeys.size,
                        allVisibleSelected = visibleGroupKeys.isNotEmpty() &&
                            selectedVisibleGroupKeys.size == visibleGroupKeys.size,
                        onSelectVisible = { onUpdateSelectedGroupKeys(selectedGroupKeys + visibleGroupKeys) },
                        onClear = { onUpdateSelectedGroupKeys(emptySet()) },
                    )
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp),
            ) {
                items(filteredGroups, key = { it.targetKey }) { group ->
                    val groupKey = group.targetKey
                    DuplicateGroupCard(
                        group = group,
                        similar = state.mode == DuplicateMode.SIMILAR,
                        selected = state.selectedForDeletion,
                        groupSelected = groupKey in selectedVisibleGroupKeys,
                        onToggleGroup = {
                            val next = if (groupKey in selectedGroupKeys) {
                                selectedGroupKeys - groupKey
                            } else {
                                selectedGroupKeys + groupKey
                            }
                            onUpdateSelectedGroupKeys(next)
                        },
                        onToggle = onToggleFileSelection,
                        onView = onNavigateToViewer,
                        onSelectFolder = onSelectAllInFolder,
                    )
                }
                item { Spacer(Modifier.height(12.dp)) }
            }
        } else if (state.scanDone && !state.isScanning) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.no_duplicates_found),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        stringResource(R.string.files_scanned_count, state.totalScanned),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else if (!state.isScanning) {
            EmptyState(
                icon = Icons.Default.ContentCopy,
                title = stringResource(R.string.dup_empty_title),
                subtitle = stringResource(R.string.dup_empty_subtitle),
                note = stringResource(R.string.dup_safety_note),
                actionLabel = stringResource(R.string.cd_search),
                onAction = onStartScan,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

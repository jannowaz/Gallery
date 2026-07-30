package org.fossify.gallery.compose.screens.analysis
import androidx.compose.ui.res.stringResource
import org.fossify.gallery.R
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.ui.draw.clip
import org.fossify.gallery.helpers.formatBytes
import org.fossify.gallery.compose.theme.Radius

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import java.io.File
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageAnalysisScreen(
    onBack: () -> Unit,
    onNavigateToViewer: (String) -> Unit = {},
    onNavigateToCompressionReview: () -> Unit = {},
    onNavigateToSwipe: (List<AnalysisResult>) -> Unit = {},
) {
    val vm: StorageAnalysisViewModel = viewModel()
    val state by vm.state.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var showConfirmDialog by remember { mutableStateOf(false) }
    val defaultPath = Environment.getExternalStorageDirectory().absolutePath
    var currentFolder by remember { mutableStateOf(defaultPath) }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val path = uriToPath(uri) ?: uri.toString()
            currentFolder = path
        }
    }

    // A finished scan is minutes of work that a single stray back press used to throw away - while
    // scanning or holding results, leaving takes two back presses (or two taps on the arrow).
    val guardedBack = org.fossify.gallery.compose.util.rememberDoubleBackGuard(
        enabled = state.isScanning || state.results.isNotEmpty(),
        onExit = onBack,
    )

    val listState = rememberLazyListState()

    // Media-type filter first (drives the folder breakdown), then the folder filter, then sort -
    // this is the exact set the list shows, and the same set every "mark" action operates on so
    // selection is what-you-see-is-what-you-get.
    val byType = remember(state.results, state.filterMode) {
        when (state.filterMode) {
            FilterMode.ALL -> state.results
            FilterMode.IMAGES -> state.results.filter { it.mediaType == 1 }
            FilterMode.VIDEOS -> state.results.filter { it.mediaType == 2 }
        }
    }
    val folderCounts = remember(byType) {
        byType.groupingBy { File(it.path).parent ?: "" }.eachCount().entries
            .sortedByDescending { it.value }.map { it.key to it.value }
    }
    val visible = remember(byType, state.folderFilter) {
        val f = state.folderFilter
        if (f == null) byType else byType.filter { File(it.path).parent == f }
    }
    val sortedVisible = remember(visible, state.sortMode) {
        when (state.sortMode) {
            AnalysisSortMode.WASTED -> visible.sortedByDescending { it.wastedBytes }
            AnalysisSortMode.SIZE -> visible.sortedByDescending { it.fileSize }
            AnalysisSortMode.NAME -> visible.sortedBy { it.name.lowercase() }
        }
    }

    // A filter can go stale after an optimize/compress re-scan drops a folder - fall back to "all".
    LaunchedEffect(state.folderFilter, sortedVisible.isEmpty()) {
        if (state.folderFilter != null && sortedVisible.isEmpty() && state.results.isNotEmpty()) vm.setFolderFilter(null)
    }

    // Once results exist the scan form is dead weight above the list - collapse it (the header's
    // gear re-opens it for a new scan). Kept open while there are no results, including during a scan.
    var configExpanded by remember { mutableStateOf(true) }
    LaunchedEffect(state.results.isEmpty()) { configExpanded = state.results.isEmpty() }

    // Paths of the currently visible (filtered) set - every "mark" action and the one-tap CTAs
    // operate on this so the whole screen is what-you-see-is-what-you-get: a folder/type filter can
    // never optimize or delete a file it is hiding.
    val visiblePaths = remember(sortedVisible) { sortedVisible.map { it.path }.toSet() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_storage_analysis), fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = guardedBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back)) } },
                actions = {
                    if (state.results.isNotEmpty()) {
                        SelectMenu(
                            shownCount = sortedVisible.size,
                            folderFiltered = state.folderFilter != null,
                            onMarkAllShown = { vm.selectPaths(visiblePaths) },
                            onMarkNext = { n ->
                                val start = listState.firstVisibleItemIndex.coerceIn(0, (sortedVisible.size - 1).coerceAtLeast(0))
                                vm.selectPaths(sortedVisible.subList(start, min(start + n, sortedVisible.size)).map { it.path }, additive = true)
                            },
                            onMarkFolder = { state.folderFilter?.let { vm.selectFolder(it) } },
                            onClear = { vm.clearSelection() },
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        bottomBar = {
            // Anchored at the bottom instead of stacked into the scrolling column, so marking files
            // never shrinks the list you're working through.
            if (state.selectedPaths.isNotEmpty()) {
                val selSize = state.results.filter { it.path in state.selectedPaths }.sumOf { it.wastedBytes }
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, tonalElevation = 3.dp, shadowElevation = 8.dp) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.selected_count_saving, state.selectedPaths.size, formatBytes(selSize)), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        TextButton(onClick = { vm.clearSelection() }) { Text(stringResource(R.string.action_empty)) }
                        TextButton(
                            onClick = { scope.launch { vm.startCompressionAwait(); onNavigateToCompressionReview() } },
                            enabled = !state.isEnqueuingCompression,
                        ) {
                            if (state.isEnqueuingCompression) {
                                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(stringResource(R.string.action_compress))
                        }
                        TextButton(onClick = { showConfirmDialog = true }, enabled = !state.isEnqueuingCompression) { Text(stringResource(R.string.optimize)) }
                    }
                }
            } else {
                // No selection: surface the recycle-bin safety net as a visible "Undo" after an
                // optimize (restores originals) or a delete.
                org.fossify.gallery.compose.components.UndoBar()
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Compact results header with a collapse toggle for the scan form below.
            if (state.results.isNotEmpty()) {
                val visibleWasted = remember(sortedVisible) { sortedVisible.sumOf { it.wastedBytes } }
                Surface(color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)) {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.storage_files_wasted, sortedVisible.size, formatBytes(visibleWasted)), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
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

            // Scan configuration - collapsed once results exist to give the list the screen.
            AnimatedVisibility(visible = configExpanded) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
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
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { if (state.isScanning) vm.cancelScan() else vm.startAnalysis(currentFolder) },
                    ) {
                        if (state.isScanning) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (state.isScanning) stringResource(R.string.cancel) else stringResource(R.string.analyze))
                    }
                }
            }

            if (state.isScanning) {
                LinearProgressIndicator(progress = { state.progress / 100f }, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.storage_files_scanned, state.scannedCount, state.totalFiles), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (state.results.isNotEmpty()) {
                var showSortMenu by remember { mutableStateOf(false) }
                // Filter + sort row (moved out of the old summary card so the list gets more height).
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = state.filterMode == FilterMode.ALL, onClick = { vm.setFilterMode(FilterMode.ALL) }, label = { Text(stringResource(R.string.filter_all)) })
                    FilterChip(selected = state.filterMode == FilterMode.IMAGES, onClick = { vm.setFilterMode(FilterMode.IMAGES) }, label = { Text(stringResource(R.string.images)) })
                    FilterChip(selected = state.filterMode == FilterMode.VIDEOS, onClick = { vm.setFilterMode(FilterMode.VIDEOS) }, label = { Text(stringResource(R.string.videos)) })
                    Spacer(Modifier.weight(1f))
                    Box {
                        IconButton(onClick = { showSortMenu = true }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.AutoMirrored.Filled.Sort, stringResource(R.string.sort_analysis_by), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        }
                        DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.sort_savings)) },
                                trailingIcon = { if (state.sortMode == AnalysisSortMode.WASTED) Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp)) },
                                onClick = { vm.setSortMode(AnalysisSortMode.WASTED); showSortMenu = false },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.sort_size)) },
                                trailingIcon = { if (state.sortMode == AnalysisSortMode.SIZE) Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp)) },
                                onClick = { vm.setSortMode(AnalysisSortMode.SIZE); showSortMenu = false },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.sort_name)) },
                                trailingIcon = { if (state.sortMode == AnalysisSortMode.NAME) Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp)) },
                                onClick = { vm.setSortMode(AnalysisSortMode.NAME); showSortMenu = false },
                            )
                        }
                    }
                }

                // Folder filter: work a whole-storage scan area by area instead of one mixed list.
                if (folderCounts.size > 1) {
                    LazyRow(
                        Modifier.fillMaxWidth(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item {
                            FilterChip(
                                selected = state.folderFilter == null,
                                onClick = { vm.setFolderFilter(null) },
                                label = { Text("${stringResource(R.string.dup_folder_all)} · ${byType.size}") },
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

                // Guided two-step workflow, scoped to the visible set (WYSIWYG): lossless optimization
                // can't lose quality, so it runs on everything eligible with one tap; compression is
                // lossy so it only ever queues files for the before/after review, never applied blind.
                val losslessEligible = remember(state.results) { vm.losslessEligiblePaths() }
                val losslessEligibleVisible = remember(losslessEligible, visiblePaths) { losslessEligible intersect visiblePaths }
                val busy = state.isTransforming || state.isEnqueuingCompression
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (losslessEligibleVisible.isNotEmpty()) {
                        Button(onClick = { vm.optimizeAll(visiblePaths) }, enabled = !busy, modifier = Modifier.weight(1f)) {
                            if (state.isTransforming) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                Spacer(Modifier.width(8.dp))
                            } else {
                                // Check mark reinforces "safe / lossless" - this is the button that
                                // changes files directly, so it should read as the trustworthy one.
                                Icon(Icons.Default.CheckCircle, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(stringResource(R.string.optimize_all_count, losslessEligibleVisible.size))
                        }
                    }
                    OutlinedButton(
                        onClick = { scope.launch { vm.compressAllAwait(visiblePaths); onNavigateToCompressionReview() } },
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (state.isEnqueuingCompression) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(stringResource(R.string.compress_all_count, sortedVisible.size))
                    }
                }
                Text(
                    stringResource(R.string.analysis_cta_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                )
                if (state.isTransforming) {
                    val prog = state.optimizeProgress
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)) {
                        if (prog != null) {
                            LinearProgressIndicator(progress = { prog.first / prog.second.toFloat() }, modifier = Modifier.fillMaxWidth())
                            Text(
                                stringResource(R.string.optimizing_progress, prog.first, prog.second),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
                OutlinedButton(
                    onClick = { onNavigateToSwipe(sortedVisible) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                ) { Text(stringResource(R.string.swipe_review_start)) }

                // Results list
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp)) {
                    items(sortedVisible, key = { it.path }) { item ->
                        AnalysisCard(
                            item = item,
                            isSelected = item.path in state.selectedPaths,
                            onClick = { vm.toggleSelection(item.path) },
                            onView = { onNavigateToViewer(item.path) }
                        )
                    }
                    item { Spacer(Modifier.height(12.dp)) }
                }
            } else if (!state.isScanning && state.totalFiles > 0) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.CheckCircle, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                        Spacer(Modifier.height(16.dp))
                        Text(stringResource(R.string.all_optimal), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(R.string.analyzed_no_optimization, state.totalFiles), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else if (!state.isScanning) {
                // First visit, nothing scanned yet - say what this tool does instead of showing
                // bare form controls on an empty screen.
                org.fossify.gallery.compose.components.EmptyState(
                    icon = Icons.Default.FolderOpen,
                    title = stringResource(R.string.storage_empty_title),
                    subtitle = stringResource(R.string.storage_empty_subtitle),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }
        }
    }

    // Confirm dialog
    if (showConfirmDialog) {
        val selCount = state.selectedPaths.size
        val selWaste = state.results.filter { it.path in state.selectedPaths }.sumOf { it.wastedBytes }
        val losslessCount = state.results.filter { it.path in state.selectedPaths && (it.imageFormat in listOf("bmp", "dib", "tiff", "tif") || (it.imageFormat == "png" && it.bpp <= 1.5f)) }.size
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text(stringResource(R.string.optimize_confirm_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.lossless_optimizable_count, losslessCount, selCount))
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.estimated_savings_recycle_bin, formatBytes(selWaste)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (selCount - losslessCount > 0) {
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.skipped_no_lossless_option, selCount - losslessCount), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showConfirmDialog = false; vm.executeTransforms() }, enabled = losslessCount > 0) { Text(stringResource(R.string.optimize)) }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    // Transform results
    if (state.transformResults.isNotEmpty()) {
        val success = state.transformResults.count { it.success }
        val failed = state.transformResults.size - success
        val saved = state.transformResults.sumOf { it.savedBytes }
        AlertDialog(
            onDismissRequest = { vm.clearTransformResults() },
            title = { Text(stringResource(R.string.optimize_done_title)) },
            text = { Text(stringResource(R.string.optimize_done_text, success, failed, formatBytes(saved))) },
            confirmButton = { TextButton(onClick = { vm.clearTransformResults() }) { Text(stringResource(org.fossify.commons.R.string.ok)) } }
        )
    }
}

/**
 * Top-bar "Select" menu. Marks files for the cleanup actions: all shown (filter-aware, WYSIWYG),
 * the next 10/25/50 from the current scroll position (additive, so a long list is worked in
 * batches, biggest wasters first when sorted by savings), or every file in the filtered folder.
 */
@Composable
private fun SelectMenu(
    shownCount: Int,
    folderFiltered: Boolean,
    onMarkAllShown: () -> Unit,
    onMarkNext: (Int) -> Unit,
    onMarkFolder: () -> Unit,
    onClear: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) { Text(stringResource(R.string.analysis_select)) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Text(
                stringResource(R.string.analysis_mark_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
            DropdownMenuItem(text = { Text(stringResource(R.string.analysis_mark_all_shown, shownCount)) }, onClick = { onMarkAllShown(); expanded = false })
            androidx.compose.material3.HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Text(
                stringResource(R.string.analysis_mark_from_scroll),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
            listOf(10, 25, 50).forEach { n ->
                DropdownMenuItem(text = { Text(stringResource(R.string.dup_batch_next, n)) }, onClick = { onMarkNext(n); expanded = false })
            }
            androidx.compose.material3.HorizontalDivider(Modifier.padding(vertical = 4.dp))
            if (folderFiltered) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.analysis_mark_folder)) },
                    leadingIcon = { Icon(Icons.Default.FolderOpen, null) },
                    onClick = { onMarkFolder(); expanded = false },
                )
            }
            DropdownMenuItem(text = { Text(stringResource(R.string.selection_clear)) }, onClick = { onClear(); expanded = false })
        }
    }
}

@Composable
private fun AnalysisCard(item: AnalysisResult, isSelected: Boolean, onClick: () -> Unit, onView: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (item.mediaType == 2) Icons.Default.PlayArrow else Icons.Default.Image,
                    null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(item.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                IconButton(onClick = onView, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.ZoomIn, stringResource(R.string.cd_preview), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(4.dp))
                Text(formatBytes(item.fileSize), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (item.mediaType == 2) {
                Text("${item.width}×${item.height} · ${formatKbps(item.bitrateKbps)} · ${formatDuration(item.durationMs)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text("${item.width}×${item.height} · ${item.imageFormat?.uppercase()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (item.wastedBytes > 0) {
                val expectedSize = (item.fileSize - item.wastedBytes).coerceAtLeast(0)
                val expectedPercent = (item.wastedBytes * 100 / item.fileSize).toInt()
                // Bar lengths compare at a glance where text numbers need reading: the track is
                // the current size, the filled part what would remain after compression.
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.weight(1f).height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.25f))
                    ) {
                        Box(
                            Modifier.fillMaxHeight()
                                .fillMaxWidth((expectedSize.toFloat() / item.fileSize).coerceIn(0.02f, 1f))
                                .background(MaterialTheme.colorScheme.tertiary)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("−$expectedPercent %", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
                }
                Text(
                    stringResource(R.string.estimated_size_after_compression, formatBytes(expectedSize), expectedPercent),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            // Findings collapsed behind one line - the full explanations are expert prose that
            // drowned the list; they stay one tap away.
            if (item.reasons.isNotEmpty()) {
                var reasonsExpanded by remember(item.path) { mutableStateOf(false) }
                Row(
                    Modifier.clickable { reasonsExpanded = !reasonsExpanded }.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        androidx.compose.ui.res.pluralStringResource(R.plurals.reasons_count, item.reasons.size, item.reasons.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Icon(
                        if (reasonsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        null, tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f), modifier = Modifier.size(16.dp),
                    )
                }
                if (reasonsExpanded) {
                    item.reasons.forEach { reason ->
                        Text(reason, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 18.dp, top = 2.dp))
                    }
                }
            }
        }
    }
}


private fun formatKbps(kbps: Long): String = when { kbps >= 1000 -> "${"%.1f".format(kbps / 1000.0)} Mbps"; else -> "$kbps Kbps" }

private fun formatDuration(ms: Long): String {
    val sec = ms / 1000
    return "%d:%02d".format(sec / 60, sec % 60)
}

private fun uriToPath(uri: Uri): String? {
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

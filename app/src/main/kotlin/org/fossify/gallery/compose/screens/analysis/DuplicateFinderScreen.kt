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
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
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
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicateFinderScreen(onBack: () -> Unit, initialFolder: String = "", onNavigateToViewer: (String) -> Unit = {}) {
    val vm: DuplicateFinderViewModel = viewModel()
    val state by vm.state.collectAsState()
    val ctx = LocalContext.current
    var showConfirmDialog by remember { mutableStateOf(false) }
    val defaultPath = Environment.getExternalStorageDirectory().absolutePath
    var currentFolder by remember { mutableStateOf(initialFolder.ifBlank { defaultPath }) }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) currentFolder = duplicateUriToPath(uri) ?: uri.toString()
    }

    // Same protection as the storage analysis: a duplicate scan takes minutes, so leaving a
    // running scan or its results needs a deliberate double back.
    val guardedBack = org.fossify.gallery.compose.util.rememberDoubleBackGuard(
        enabled = state.isScanning || state.groups.isNotEmpty(),
        onExit = onBack,
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_find_duplicates), fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = guardedBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back)) } },
                actions = {
                    if (state.groups.isNotEmpty()) {
                        var showSelectMenu by remember { mutableStateOf(false) }
                        // Applying a strategy in SIMILAR mode is allowed but deserves a nudge -
                        // those files are perceptually similar, not verified identical.
                        fun applied(strategy: KeepStrategy) {
                            vm.applyKeepStrategy(strategy)
                            showSelectMenu = false
                            if (state.mode == DuplicateMode.SIMILAR) {
                                android.widget.Toast.makeText(ctx, ctx.getString(R.string.similar_select_caution), android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                        Box {
                            TextButton(onClick = { showSelectMenu = true }) { Text(stringResource(R.string.auto_select)) }
                            androidx.compose.material3.DropdownMenu(expanded = showSelectMenu, onDismissRequest = { showSelectMenu = false }) {
                                Text(
                                    stringResource(R.string.keep_per_group_hint),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                )
                                androidx.compose.material3.DropdownMenuItem(text = { Text(stringResource(R.string.keep_newest)) }, onClick = { applied(KeepStrategy.NEWEST) })
                                androidx.compose.material3.DropdownMenuItem(text = { Text(stringResource(R.string.keep_oldest)) }, onClick = { applied(KeepStrategy.OLDEST) })
                                androidx.compose.material3.DropdownMenuItem(text = { Text(stringResource(R.string.keep_largest)) }, onClick = { applied(KeepStrategy.LARGEST) })
                                androidx.compose.material3.DropdownMenuItem(text = { Text(stringResource(R.string.keep_smallest)) }, onClick = { applied(KeepStrategy.SMALLEST) })
                                androidx.compose.material3.DropdownMenuItem(text = { Text(stringResource(R.string.keep_shortest_name)) }, onClick = { applied(KeepStrategy.SHORTEST_NAME) })
                                androidx.compose.material3.DropdownMenuItem(text = { Text(stringResource(R.string.keep_shortest_path)) }, onClick = { applied(KeepStrategy.SHORTEST_PATH) })
                                androidx.compose.material3.HorizontalDivider(Modifier.padding(vertical = 4.dp))
                                androidx.compose.material3.DropdownMenuItem(text = { Text(stringResource(R.string.selection_clear)) }, onClick = { vm.clearSelection(); showSelectMenu = false })
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
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

            if (state.isScanning) {
                LinearProgressIndicator(progress = { state.progress / 100f }, modifier = Modifier.fillMaxWidth())
                Text(state.phase, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (state.groups.isNotEmpty()) {
                val totalWasted = remember(state.groups) { state.groups.sumOf { it.wastedBytes } }
                val totalDupes = remember(state.groups) { state.groups.sumOf { it.files.size - 1 } }
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))) {
                    Column(Modifier.padding(12.dp)) {
                        Text(stringResource(R.string.duplicate_scan_summary, state.groups.size, totalDupes, formatBytes(totalWasted)), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        state.restoredAt?.let { ts ->
                            Text(
                                stringResource(R.string.restored_scan_from, java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(ts))),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                if (state.selectedForDeletion.isNotEmpty()) {
                    val selSize = state.groups.flatMap { it.files }.filter { it.path in state.selectedForDeletion }.sumOf { it.size }
                    Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(Radius.md)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.duplicate_marked_count, state.selectedForDeletion.size, formatBytes(selSize)), style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                            TextButton(onClick = { showConfirmDialog = true }) { Text(stringResource(org.fossify.commons.R.string.delete), color = MaterialTheme.colorScheme.error) }
                            TextButton(onClick = { vm.clearSelection() }) { Text(stringResource(R.string.action_empty)) }
                        }
                    }
                }

                LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp)) {
                    items(state.groups, key = { it.hash }) { group ->
                        DuplicateGroupCard(
                            group = group,
                            similar = state.mode == DuplicateMode.SIMILAR,
                            selected = state.selectedForDeletion,
                            onToggle = { vm.toggleSelection(it) },
                            onView = { path -> onNavigateToViewer(path) },
                            onSelectFolder = { path -> vm.selectAllInFolder(path) },
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
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

@Composable
private fun DuplicateGroupCard(
    group: DuplicateGroup,
    similar: Boolean,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onView: (String) -> Unit,
    onSelectFolder: (String) -> Unit,
) {
    // Hoisted once per card instead of allocating a new SimpleDateFormat (locale data lookup,
    // not free) per file, per recomposition, inside the forEachIndexed below.
    val dateFormat = remember { java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault()) }
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                stringResource(
                    R.string.duplicate_group_summary,
                    group.files.size,
                    if (similar) stringResource(R.string.similar) else stringResource(R.string.identical),
                    formatBytes(group.size),
                    formatBytes(group.wastedBytes),
                ),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.tertiary,
            )
            group.files.forEachIndexed { index, file ->
                // Only the checkbox sits on the right - the old per-row preview/folder icon pair
                // ate ~90dp of width and squeezed the path into two truncated lines. Preview is now
                // a tap on the thumbnail/info area, folder-select an inline chip below the metadata.
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(64.dp).clip(RoundedCornerShape(Radius.sm)).clickable { onView(file.path) }) {
                        GalleryImage(path = file.path, contentDescription = file.name, modifier = Modifier.size(64.dp).sharedElementKey("media_${file.path}"))
                        if (file.mediaType == 2) {
                            Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(Radius.sm)).clickable { onView(file.path) }) {
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
        }
    }
}

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

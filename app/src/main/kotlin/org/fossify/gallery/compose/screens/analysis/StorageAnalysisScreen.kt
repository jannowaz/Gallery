package org.fossify.gallery.compose.screens.analysis
import androidx.compose.ui.res.stringResource
import org.fossify.gallery.R
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageAnalysisScreen(onBack: () -> Unit, onNavigateToViewer: (String) -> Unit = {}) {
    val vm: StorageAnalysisViewModel = viewModel()
    val state by vm.state.collectAsState()
    val ctx = LocalContext.current
    var showConfirmDialog by remember { mutableStateOf(false) }
    val defaultPath = Environment.getExternalStorageDirectory().absolutePath
    var currentFolder by remember { mutableStateOf(defaultPath) }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val path = uriToPath(uri) ?: uri.toString()
            currentFolder = path
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_storage_analysis), fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back)) } },
                actions = {
                    if (state.results.isNotEmpty()) {
                        IconButton(onClick = { vm.selectAll() }) { Icon(Icons.Default.CheckCircle, stringResource(R.string.action_select_all)) }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Folder selection + scan button
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

            if (state.isScanning) {
                LinearProgressIndicator(progress = { state.progress / 100f }, modifier = Modifier.fillMaxWidth())
                Text("${state.scannedCount} / ${state.totalFiles} Dateien gescannt", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Summary header
            if (state.results.isNotEmpty()) {
                val totalWasted = remember(state.results) { state.results.sumOf { it.wastedBytes } }
                val filtered = remember(state.results, state.filterMode) {
                    when (state.filterMode) {
                    FilterMode.ALL -> state.results
                    FilterMode.IMAGES -> state.results.filter { it.mediaType == 1 }
                        FilterMode.VIDEOS -> state.results.filter { it.mediaType == 2 }
                    }
                }
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))) {
                    Column(Modifier.padding(12.dp)) {
                        Text("${filtered.size} Dateien · ${formatBytes(totalWasted)} verschwendet", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(selected = state.filterMode == FilterMode.ALL, onClick = { vm.setFilterMode(FilterMode.ALL) }, label = { Text(stringResource(R.string.filter_all)) })
                            FilterChip(selected = state.filterMode == FilterMode.IMAGES, onClick = { vm.setFilterMode(FilterMode.IMAGES) }, label = { Text(stringResource(R.string.images)) })
                            FilterChip(selected = state.filterMode == FilterMode.VIDEOS, onClick = { vm.setFilterMode(FilterMode.VIDEOS) }, label = { Text(stringResource(R.string.videos)) })
                        }
                    }
                }

                // Action bar
                if (state.selectedPaths.isNotEmpty()) {
                    val selSize = state.results.filter { it.path in state.selectedPaths }.sumOf { it.wastedBytes }
                    Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(Radius.md)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.selected_count_saving, state.selectedPaths.size, formatBytes(selSize)), style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                            TextButton(onClick = { showConfirmDialog = true }) { Text(stringResource(R.string.optimize)) }
                            TextButton(onClick = { vm.clearSelection() }) { Text(stringResource(R.string.action_empty)) }
                        }
                    }
                }

                // Results list
                LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp)) {
                    items(filtered.sortedByDescending { it.wastedBytes }, key = { it.path }) { item ->
                        AnalysisCard(
                            item = item,
                            isSelected = item.path in state.selectedPaths,
                            onClick = { vm.toggleSelection(item.path) },
                            onView = { onNavigateToViewer(item.path) }
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
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
            text = { Text("$success erfolgreich, $failed fehlgeschlagen. ${formatBytes(saved)} gespart.") },
            confirmButton = { TextButton(onClick = { vm.clearTransformResults() }) { Text(stringResource(org.fossify.commons.R.string.ok)) } }
        )
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
                IconButton(onClick = onView, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Visibility, stringResource(R.string.cd_preview), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(4.dp))
                Text(formatBytes(item.fileSize), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (item.mediaType == 2) {
                Text("${item.width}×${item.height} · ${formatKbps(item.bitrateKbps)} · ${formatDuration(item.durationMs)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text("${item.width}×${item.height} · ${item.imageFormat?.uppercase()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item.reasons.forEach { reason ->
                Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(reason, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
            if (item.wastedBytes > 0) {
                Text("≈ ${formatBytes(item.wastedBytes)} verschwendet", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> "${"%.1f".format(bytes / 1_000_000_000.0)} GB"
    bytes >= 1_000_000 -> "${"%.1f".format(bytes / 1_000_000.0)} MB"
    bytes >= 1_000 -> "${bytes / 1_000} KB"
    else -> "$bytes B"
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

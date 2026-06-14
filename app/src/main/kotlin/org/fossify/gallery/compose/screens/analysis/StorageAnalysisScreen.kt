package org.fossify.gallery.compose.screens.analysis

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
fun StorageAnalysisScreen(onBack: () -> Unit) {
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
                title = { Text("Speicher-Analyse", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") } },
                actions = {
                    if (state.results.isNotEmpty()) {
                        IconButton(onClick = { vm.selectAll() }) { Icon(Icons.Default.CheckCircle, "Alle auswählen") }
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
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.FolderOpen, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            currentFolder.substringAfterLast('/').ifEmpty { "Interner Speicher" },
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { vm.startAnalysis(currentFolder) },
                    enabled = !state.isScanning,
                ) {
                    if (state.isScanning) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (state.isScanning) "Scannt..." else "Analysieren")
                }
            }

            if (state.isScanning) {
                LinearProgressIndicator(progress = { state.progress / 100f }, modifier = Modifier.fillMaxWidth())
                Text("${state.scannedCount} / ${state.totalFiles} Dateien gescannt", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Summary header
            if (state.results.isNotEmpty()) {
                val totalWasted = state.results.sumOf { it.wastedBytes }
                val filtered = when (state.filterMode) {
                    FilterMode.ALL -> state.results
                    FilterMode.IMAGES -> state.results.filter { it.mediaType == 1 }
                    FilterMode.VIDEOS -> state.results.filter { it.mediaType == 2 }
                }
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))) {
                    Column(Modifier.padding(12.dp)) {
                        Text("${filtered.size} Dateien · ${formatBytes(totalWasted)} verschwendet", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(selected = state.filterMode == FilterMode.ALL, onClick = { vm.setFilterMode(FilterMode.ALL) }, label = { Text("Alle") })
                            FilterChip(selected = state.filterMode == FilterMode.IMAGES, onClick = { vm.setFilterMode(FilterMode.IMAGES) }, label = { Text("Bilder") })
                            FilterChip(selected = state.filterMode == FilterMode.VIDEOS, onClick = { vm.setFilterMode(FilterMode.VIDEOS) }, label = { Text("Videos") })
                        }
                    }
                }

                // Action bar
                if (state.selectedPaths.isNotEmpty()) {
                    val selSize = state.results.filter { it.path in state.selectedPaths }.sumOf { it.wastedBytes }
                    Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(12.dp)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${state.selectedPaths.size} ausgewählt · ${formatBytes(selSize)} sparen", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                            TextButton(onClick = { showConfirmDialog = true }) { Text("Optimieren") }
                            TextButton(onClick = { vm.clearSelection() }) { Text("Clear") }
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
                            onView = {
                                ctx.startActivity(Intent(ctx, org.fossify.gallery.activities.ComposeViewerActivity::class.java).apply {
                                    putStringArrayListExtra("PATHS", arrayListOf(item.path))
                                    putExtra("START_INDEX", 0)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                })
                            }
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            } else if (!state.isScanning && state.totalFiles > 0) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.CheckCircle, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                        Spacer(Modifier.height(16.dp))
                        Text("Alles optimal!", style = MaterialTheme.typography.bodyLarge)
                        Text("${state.totalFiles} Dateien analysiert, keine Optimierung nötig", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    // Confirm dialog
    if (showConfirmDialog) {
        val selCount = state.selectedPaths.size
        val selWaste = state.results.filter { it.path in state.selectedPaths }.sumOf { it.wastedBytes }
        val losslessCount = state.results.filter { it.path in state.selectedPaths && it.imageFormat in listOf("bmp", "dib", "tiff", "tif") || (it.imageFormat == "png" && it.bpp <= 1.5f) }.size
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Optimierung bestätigen") },
            text = {
                Column {
                    Text("$selCount Dateien optimieren? Geschätzte Ersparnis: ${formatBytes(selWaste)}.")
                    Spacer(Modifier.height(8.dp))
                    Text("Lossless: $losslessCount Dateien (BMP→PNG, TIFF→PNG, PNG→WebP) – 100% Qualität erhalten", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    val lossyCount = selCount - losslessCount
                    if (lossyCount > 0) {
                        Spacer(Modifier.height(4.dp))
                        Text("Lossy: $lossyCount Dateien (JPEG/PNG Rekompression) – minimale Qualitätseinbuße", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showConfirmDialog = false; vm.executeTransforms(losslessOnly = true) }) { Text("Nur Lossless") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { showConfirmDialog = false; vm.executeTransforms(losslessOnly = false) }) { Text("Alle", color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { showConfirmDialog = false }) { Text("Abbrechen") }
                }
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
            title = { Text("Optimierung abgeschlossen") },
            text = { Text("$success erfolgreich, $failed fehlgeschlagen. ${formatBytes(saved)} gespart.") },
            confirmButton = { TextButton(onClick = { vm.clearTransformResults() }) { Text("OK") } }
        )
    }
}

@Composable
private fun AnalysisCard(item: AnalysisResult, isSelected: Boolean, onClick: () -> Unit, onView: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
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
                    Icon(Icons.Default.Visibility, "Vorschau", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
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

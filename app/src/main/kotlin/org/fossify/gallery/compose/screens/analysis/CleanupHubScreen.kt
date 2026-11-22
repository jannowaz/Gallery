package org.fossify.gallery.compose.screens.analysis

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fossify.gallery.R
import org.fossify.gallery.compose.theme.LocalMediaRepository
import org.fossify.gallery.extensions.compressionReviewDB
import org.fossify.gallery.helpers.formatBytes

private data class CleanupAction(
    val title: Int,
    val subtitle: Int,
    val icon: ImageVector,
    val metric: String?,
    val priorityBytes: Long = 0L,
    val onClick: () -> Unit,
)

private data class CleanupMetric(val count: Int, val bytes: Long)

private data class CleanupStats(
    val storage: CleanupMetric? = null,
    val duplicates: CleanupMetric? = null,
    val recycleBin: CleanupMetric = CleanupMetric(0, 0L),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CleanupHubScreen(
    onBack: () -> Unit,
    onOpenStorageAnalysis: () -> Unit,
    onOpenDuplicates: () -> Unit,
    onOpenRecycleBin: () -> Unit,
    onOpenCompressionReview: () -> Unit,
) {
    val context = LocalContext.current
    val repo = LocalMediaRepository.current
    // These are deliberately lightweight, persisted summaries rather than a new full-library
    // scan. The hub should open instantly and tell the user what is already known; the detail
    // screens remain responsible for starting fresh analyses.
    var stats by remember { mutableStateOf<CleanupStats?>(null) }
    val compressionItems by context.compressionReviewDB.getAllLive().collectAsState(initial = emptyList())
    LaunchedEffect(Unit) {
        stats = withContext(Dispatchers.IO) {
            val storage = ScanResultStore.loadStorageScan(context)?.let { scan ->
                CleanupMetric(scan.results.size, scan.results.sumOf { it.wastedBytes })
            }
            val duplicates = ScanResultStore.loadDuplicateScan(context)?.let { scan ->
                CleanupMetric(
                    scan.groups.sumOf { it.files.size - 1 },
                    scan.groups.sumOf { it.wastedBytes },
                )
            }
            val recycleItems = repo.getDeletedMedia()
            CleanupStats(
                storage = storage,
                duplicates = duplicates,
                recycleBin = CleanupMetric(recycleItems.size, recycleItems.sumOf { it.size }),
            )
        }
    }

    val storageMetric = stats?.storage?.let {
        stringResource(R.string.cleanup_storage_metric, it.count, formatBytes(it.bytes))
    }
    val duplicateMetric = stats?.duplicates?.let {
        stringResource(R.string.cleanup_duplicates_metric, it.count, formatBytes(it.bytes))
    }
    val recycleMetric = stats?.let {
        stringResource(R.string.cleanup_recycle_metric, it.recycleBin.count, formatBytes(it.recycleBin.bytes))
    }
    val compressionMetric = compressionItems.takeIf { it.isNotEmpty() }?.let {
        stringResource(R.string.cleanup_compression_metric, it.size)
    }
    val actions = listOf(
        CleanupAction(R.string.cleanup_storage, R.string.cleanup_storage_subtitle, Icons.Default.Storage, storageMetric, stats?.storage?.bytes ?: 0L, onOpenStorageAnalysis),
        CleanupAction(R.string.cleanup_duplicates, R.string.cleanup_duplicates_subtitle, Icons.Default.ContentCopy, duplicateMetric, stats?.duplicates?.bytes ?: 0L, onOpenDuplicates),
        CleanupAction(
            R.string.cleanup_compression,
            R.string.cleanup_compression_subtitle,
            Icons.Default.Compress,
            compressionMetric,
            compressionItems.sumOf { (it.originalSize - it.resultSize).coerceAtLeast(0L) },
            onOpenCompressionReview,
        ),
        CleanupAction(R.string.cleanup_recycle_bin, R.string.cleanup_recycle_bin_subtitle, Icons.Default.Delete, recycleMetric, stats?.recycleBin?.bytes ?: 0L, onOpenRecycleBin),
    ).sortedWith(compareByDescending<CleanupAction> { it.priorityBytes }.thenByDescending { it.metric != null })

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.cleanup_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back))
                    }
                },
            )
        },
    ) { padding ->
        val layoutDirection = LocalLayoutDirection.current
        LazyColumn(
            modifier = Modifier.fillMaxSize().consumeWindowInsets(padding),
            contentPadding = PaddingValues(
                start = 16.dp + padding.calculateStartPadding(layoutDirection),
                top = 16.dp + padding.calculateTopPadding(),
                end = 16.dp + padding.calculateEndPadding(layoutDirection),
                bottom = 16.dp + padding.calculateBottomPadding(),
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.cleanup_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            item {
                Text(
                    stringResource(R.string.cleanup_priority_hint),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
            items(actions) { action ->
                Card(
                    onClick = action.onClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Icon(
                                action.icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(action.title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                action.metric ?: stringResource(action.subtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            item {
                Text(
                    stringResource(R.string.cleanup_safety_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp),
                )
            }
        }
    }
}

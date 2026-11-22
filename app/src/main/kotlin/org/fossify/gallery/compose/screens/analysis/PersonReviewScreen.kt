package org.fossify.gallery.compose.screens.analysis

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import org.fossify.commons.extensions.formatSize
import org.fossify.gallery.R
import org.fossify.gallery.compose.components.ConfirmDestructive
import org.fossify.gallery.compose.components.GalleryImage
import org.fossify.gallery.compose.screens.folderswipe.SwipeDecisionSurface
import org.fossify.gallery.compose.theme.LocalMediaRepository
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.RefreshBus
import org.fossify.gallery.helpers.UndoAction
import org.fossify.gallery.helpers.UndoManager
import org.fossify.gallery.helpers.UndoType
import org.fossify.gallery.helpers.withRecycleBinDeletePasswordCheck
import org.fossify.gallery.models.Medium
import org.fossify.gallery.models.PersonPresence
import org.fossify.gallery.workers.PersonScanWorker
import java.io.File

/**
 * Safe one-at-a-time cleanup inbox for a single folder/album. Model output remains a suggestion;
 * destructive deletion still uses the normal recycle-bin and confirmation safeguards.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonReviewScreen(
    folderPath: String,
    onBack: () -> Unit,
    onNavigateToViewer: (paths: List<String>, startIndex: Int) -> Unit,
) {
    val context = LocalContext.current
    val repo = LocalMediaRepository.current
    val normalizedFolderPath = remember(folderPath) { PersonScanWorker.normalizeFolderPath(folderPath) }
    val workInfos by WorkManager.getInstance(context)
        .getWorkInfosForUniqueWorkLiveData(PersonScanWorker.uniqueWorkName(normalizedFolderPath))
        .observeAsState(emptyList())
    val activeWork = workInfos.firstOrNull { !it.state.isFinished }
    val completedWork = workInfos.firstOrNull { it.state == WorkInfo.State.SUCCEEDED }
    val isScanning = activeWork != null
    var tab by remember { mutableStateOf(PersonPresence.NO_PERSON) }
    var media by remember(normalizedFolderPath, tab) { mutableStateOf<List<Medium>?>(null) }
    var counts by remember(normalizedFolderPath) { mutableStateOf<Map<PersonPresence, Int>>(emptyMap()) }
    var includeSubfolders by rememberSaveable(normalizedFolderPath) { mutableStateOf(false) }
    var reviewIndex by rememberSaveable(normalizedFolderPath, tab) { mutableIntStateOf(0) }
    var isApplyingDecision by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Medium?>(null) }
    // A path change (delete, move, restore) should refresh this queue too. Keep it as state and
    // let the single loader below own all UI updates: the former direct collector could finish
    // after a newer request with another tab or recursive setting and replace its result.
    var refreshGeneration by remember(normalizedFolderPath) { mutableStateOf(0L) }
    val reviewScope = rememberCoroutineScope()

    LaunchedEffect(normalizedFolderPath) {
        RefreshBus.pathEvents.collectLatest {
            refreshGeneration++
        }
    }
    LaunchedEffect(normalizedFolderPath) {
        // Person-scan rows change metadata, not file paths. Listen to this lane as well so a
        // completed background scan immediately replaces a loading/empty grid with its results.
        RefreshBus.metadataEvents.collectLatest {
            refreshGeneration++
        }
    }

    LaunchedEffect(normalizedFolderPath, tab, includeSubfolders, isScanning, completedWork?.id, refreshGeneration) {
        if (isScanning) return@LaunchedEffect

        // Snapshot the request parameters. LaunchedEffect cancels a previous request when any
        // key changes, and this final check also protects against a blocking database query that
        // happens to finish after cancellation.
        val requestedTab = tab
        val requestedIncludeSubfolders = includeSubfolders
        val result = withContext(Dispatchers.IO) {
            repo.getPersonScanMedia(normalizedFolderPath, requestedIncludeSubfolders, requestedTab) to
                repo.getPersonScanCounts(normalizedFolderPath, requestedIncludeSubfolders)
        }
        if (
            !isScanning &&
            tab == requestedTab &&
            includeSubfolders == requestedIncludeSubfolders
        ) {
            media = result.first
            counts = result.second
        }
    }

    val noPersonCount = counts[PersonPresence.NO_PERSON] ?: 0
    val uncertainCount = counts[PersonPresence.UNCERTAIN] ?: 0
    val activeProgress = activeWork?.progress
    val completedOutput = completedWork?.outputData
    val done = (activeProgress ?: completedOutput)?.getInt(PersonScanWorker.KEY_DONE, 0) ?: 0
    val total = (activeProgress ?: completedOutput)?.getInt(PersonScanWorker.KEY_TOTAL, 0) ?: 0
    val suggested = (activeProgress ?: completedOutput)?.getInt(PersonScanWorker.KEY_NO_PERSON, 0) ?: 0
    val hasCompletedScan = completedWork != null

    fun removeFromQueue(path: String) {
        val remaining = media.orEmpty().filterNot { it.path == path }
        media = remaining
        reviewIndex = if (remaining.isEmpty()) 0 else reviewIndex.coerceAtMost(remaining.lastIndex)
    }

    fun keepCurrent(current: Medium) {
        if (isApplyingDecision) return
        isApplyingDecision = true
        reviewScope.launch {
            withContext(Dispatchers.IO) { repo.keepPersonScanMedia(setOf(current.path)) }
            removeFromQueue(current.path)
            refreshGeneration++
            isApplyingDecision = false
        }
    }

    fun deleteCurrent(current: Medium) {
        if (isApplyingDecision) return
        context.withRecycleBinDeletePasswordCheck {
            isApplyingDecision = true
            reviewScope.launch {
                withContext(Dispatchers.IO) { repo.moveToRecycleBin(current.path) }
                UndoManager.push(UndoAction(paths = setOf(current.path), type = UndoType.DELETE))
                removeFromQueue(current.path)
                RefreshBus.trigger(affectedPaths = setOf(current.path))
                refreshGeneration++
                isApplyingDecision = false
            }
        }
    }

    var configExpanded by rememberSaveable(normalizedFolderPath) { mutableStateOf(true) }
    LaunchedEffect(counts, isScanning) {
        if (!isScanning && counts.values.sum() > 0) configExpanded = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.person_scan_title), fontWeight = FontWeight.Bold)
                        Text(
                            File(folderPath).name,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    IconButton(onClick = { configExpanded = !configExpanded }) {
                        Icon(
                            if (configExpanded) Icons.Default.ExpandLess else Icons.Default.Tune,
                            stringResource(R.string.dup_config_toggle),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
            )
        },
        bottomBar = { org.fossify.gallery.compose.components.UndoBar(Modifier.navigationBarsPadding()) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            androidx.compose.animation.AnimatedVisibility(visible = configExpanded || isScanning || counts.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            ) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(R.string.person_scan_local_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(R.string.person_scan_local_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = includeSubfolders,
                            enabled = !isScanning,
                            onCheckedChange = { includeSubfolders = it },
                        )
                        Text(
                            stringResource(R.string.person_scan_include_subfolders),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                    if (isScanning) {
                        LinearProgressIndicator(
                            progress = { if (total > 0) done.toFloat() / total else 0f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            stringResource(R.string.person_scan_screen_progress, done, total, suggested),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        TextButton(onClick = {
                            WorkManager.getInstance(context).cancelUniqueWork(PersonScanWorker.uniqueWorkName(normalizedFolderPath))
                        }) {
                            Text(stringResource(R.string.cancel))
                        }
                    } else {
                        if (hasCompletedScan) {
                            Text(
                                stringResource(R.string.person_scan_screen_complete, done, suggested),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                        Button(onClick = {
                            PersonScanWorker.scheduleFolderScan(
                                context,
                                normalizedFolderPath,
                                includeSubfolders,
                            )
                        }) {
                            Icon(Icons.Default.PersonSearch, null)
                            Text(stringResource(if (hasCompletedScan || counts.isNotEmpty()) R.string.person_scan_rescan else R.string.person_scan_start), modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
            }
            if (!isScanning && counts.isNotEmpty() && !configExpanded) {
                org.fossify.gallery.compose.components.AnalysisResultHeader(
                    summary = stringResource(R.string.person_scan_screen_complete, done, suggested),
                    detail = stringResource(R.string.person_scan_result_hint),
                )
            }
            PrimaryTabRow(selectedTabIndex = if (tab == PersonPresence.NO_PERSON) 0 else 1) {
                Tab(
                    selected = tab == PersonPresence.NO_PERSON,
                    onClick = { tab = PersonPresence.NO_PERSON },
                    text = { Text(stringResource(R.string.person_scan_no_person_tab, noPersonCount)) },
                )
                Tab(
                    selected = tab == PersonPresence.UNCERTAIN,
                    onClick = { tab = PersonPresence.UNCERTAIN },
                    text = { Text(stringResource(R.string.person_scan_uncertain_tab, uncertainCount)) },
                )
            }
            Box(Modifier.fillMaxWidth().weight(1f)) {
                when (val items = media) {
                    null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    else -> if (items.isEmpty()) {
                        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text(
                                stringResource(if (tab == PersonPresence.NO_PERSON) R.string.person_scan_no_person_empty else R.string.person_scan_uncertain_empty),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        PersonReviewDecision(
                            items = items,
                            currentIndex = reviewIndex,
                            isApplyingDecision = isApplyingDecision,
                            onKeep = ::keepCurrent,
                            onSkip = {
                                reviewIndex = (reviewIndex + 1).coerceAtMost(items.size)
                            },
                            onDelete = { current ->
                                if (context.config.skipDeleteConfirmation) deleteCurrent(current)
                                else pendingDelete = current
                            },
                            onOpenViewer = { idx -> onNavigateToViewer(items.map { it.path }, idx) },
                            onRestartSkipped = { reviewIndex = 0 },
                            hasAlternateTabItems = (tab == PersonPresence.NO_PERSON && uncertainCount > 0) || (tab == PersonPresence.UNCERTAIN && noPersonCount > 0),
                            alternateTabLabel = stringResource(
                                if (tab == PersonPresence.NO_PERSON) R.string.person_scan_uncertain_tab else R.string.person_scan_no_person_tab,
                                if (tab == PersonPresence.NO_PERSON) uncertainCount else noPersonCount,
                            ),
                            onSwitchAlternateTab = {
                                tab = if (tab == PersonPresence.NO_PERSON) PersonPresence.UNCERTAIN else PersonPresence.NO_PERSON
                                reviewIndex = 0
                            },
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { current ->
        ConfirmDestructive(
            title = stringResource(R.string.action_delete),
            text = stringResource(R.string.person_scan_delete_confirm, current.name),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = {
                pendingDelete = null
                deleteCurrent(current)
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

/**
 * One-at-a-time curation for the local person scan. It deliberately shares the swipe surface used
 * by folder triage, but maps its non-destructive left swipe to “Okay”: this excludes a verified
 * medium from future person-scan suggestions. Deletion is an explicit red button instead.
 */
@Composable
private fun PersonReviewDecision(
    items: List<Medium>,
    currentIndex: Int,
    isApplyingDecision: Boolean,
    onKeep: (Medium) -> Unit,
    onSkip: () -> Unit,
    onDelete: (Medium) -> Unit,
    onOpenViewer: (Int) -> Unit,
    onRestartSkipped: () -> Unit,
    hasAlternateTabItems: Boolean,
    alternateTabLabel: String,
    onSwitchAlternateTab: () -> Unit,
) {
    val current = items.getOrNull(currentIndex)
    if (current == null) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                stringResource(R.string.person_scan_queue_finished),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (items.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = onRestartSkipped) {
                    Text(stringResource(R.string.retry))
                }
            }
            if (hasAlternateTabItems) {
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(onClick = onSwitchAlternateTab) {
                    Text(alternateTabLabel)
                }
            }
        }
        return
    }
    SwipeDecisionSurface(
        leftLabel = stringResource(R.string.person_scan_ok),
        rightLabel = stringResource(R.string.person_scan_skip),
        enabled = !isApplyingDecision,
        onLeft = { onKeep(current) },
        onRight = onSkip,
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Card(
                    onClick = { onOpenViewer(currentIndex) },
                    modifier = Modifier.fillMaxSize(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    GalleryImage(
                        path = current.path,
                        contentDescription = current.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                        thumbnailSize = 1536,
                        backgroundColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    )
                }
            }
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f)),
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(current.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        stringResource(R.string.person_scan_position_size, currentIndex + 1, items.size, current.size.formatSize()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                stringResource(R.string.person_scan_review_hint),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = onSkip, enabled = !isApplyingDecision, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.person_scan_skip))
                }
                FilledTonalButton(onClick = { onKeep(current) }, enabled = !isApplyingDecision, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.person_scan_ok))
                }
                Button(
                    onClick = { onDelete(current) },
                    enabled = !isApplyingDecision,
                    modifier = Modifier.weight(1f),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
    if (isApplyingDecision) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    }
}

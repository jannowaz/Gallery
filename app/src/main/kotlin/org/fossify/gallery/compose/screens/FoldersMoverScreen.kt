package org.fossify.gallery.compose.screens
import androidx.compose.ui.res.stringResource
import org.fossify.gallery.R
import org.fossify.gallery.compose.theme.Radius

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.commons.extensions.toast
import org.fossify.gallery.compose.components.ConfirmDestructive
import org.fossify.gallery.compose.components.EmptyState
import org.fossify.gallery.compose.theme.LocalSpacing
import org.fossify.gallery.compose.util.rememberMediaStoreConsent
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.FolderPair
import org.fossify.gallery.helpers.MediaStoreOps
import org.fossify.gallery.helpers.flattenMoverPairs
import org.fossify.gallery.helpers.loadMoverPairs
import org.fossify.gallery.helpers.saveMoverPairs
import org.fossify.gallery.models.BatchJobItem
import org.fossify.gallery.workers.BatchOperation
import org.fossify.gallery.workers.MediaBatchWorker
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoldersMoverScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val s = LocalSpacing.current
    val defPrefs = remember { android.preference.PreferenceManager.getDefaultSharedPreferences(ctx) }
    val pairs = remember { mutableStateListOf<FolderPair>().also { it.addAll(loadMoverPairs(ctx)) } }
    // The Quick Mover widget's button label ("Move N files" vs. "Tap to set up folder pairs")
    // otherwise only reflects an edit made here once the OS's own next periodic refresh happens.
    fun savePairs() { saveMoverPairs(ctx, pairs.toList()); org.fossify.gallery.helpers.MoverWidgetProvider.requestImmediateUpdate(ctx) }
    var showAddDialog by remember { mutableStateOf(false) }
    // Set once the move job is enqueued; the job keeps running via MediaBatchWorker (with its own
    // foreground notification) even if this screen is left - it no longer depends on this
    // composable's own coroutine scope surviving.
    var activeJobId by remember { mutableStateOf<String?>(null) }
    var editingIndex by remember { mutableIntStateOf(-1) }
    var pendingRemove by remember { mutableStateOf<FolderPair?>(null) }
    val storageRoot = Environment.getExternalStorageDirectory().absolutePath
    val moverConsent = rememberMediaStoreConsent()
    val allMovedFormat = stringResource(R.string.folder_mover_all_moved)
    val moveStoppedFormat = stringResource(R.string.folder_mover_stopped)

    val workInfo by remember(activeJobId) {
        activeJobId?.let { androidx.work.WorkManager.getInstance(ctx).getWorkInfosForUniqueWorkFlow(it) } ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsState(initial = emptyList())
    val activeWorkInfo = workInfo.firstOrNull()
    val isMoving = activeJobId != null && activeWorkInfo?.state?.isFinished != true
    // WorkManager clears WorkInfo.progress back to empty once the worker reaches a terminal state -
    // the final counts only survive in outputData (see MediaBatchWorker.doWork()), so once finished
    // that's the one to read, or the toast below always shows 0 regardless of what actually moved.
    val moveProgressData = if (activeWorkInfo?.state?.isFinished == true) activeWorkInfo.outputData else activeWorkInfo?.progress
    val moveProgress = moveProgressData?.getInt("done", 0) ?: 0
    val moveTotal = moveProgressData?.getInt("total", 1) ?: 1
    val moveFailed = moveProgressData?.getInt("failed", 0) ?: 0
    val moveFailedFormat = stringResource(R.string.notif_batch_done_with_failures)
    LaunchedEffect(activeWorkInfo?.state) {
        if (activeWorkInfo?.state?.isFinished == true) {
            // WorkInfo.State.SUCCEEDED only means doWork() didn't throw - MediaBatchWorker returns
            // that even when some individual items failed (see its own per-item try/catch), so
            // "SUCCEEDED" alone used to make this say "All N moved" while quietly omitting however
            // many actually failed. Check the real per-item failed count instead.
            val msg = when {
                activeWorkInfo.state != androidx.work.WorkInfo.State.SUCCEEDED -> moveStoppedFormat.format(moveProgress)
                moveFailed > 0 -> moveFailedFormat.format(moveProgress, moveFailed)
                else -> allMovedFormat.format(moveProgress)
            }
            ctx.toast(msg, Toast.LENGTH_SHORT)
            activeJobId = null
        }
    }

    fun uriToPath(uri: Uri): String? {
        return try {
            val docId = DocumentsContract.getTreeDocumentId(uri)
            val split = docId.indexOf(':')
            if (split >= 0) {
                val type = docId.substring(0, split)
                val relative = docId.substring(split + 1)
                (if (type == "primary") "$storageRoot/$relative" else "/storage/$type/$relative").trimEnd('/')
            } else null
        } catch (_: Exception) { null }
    }

    // sources can carry more than one path when adding a new pair (not when editing an existing
    // one, see AddPairDialog's isEditing gate) - one FolderPair per source, all to the same dest.
    fun addOrUpdatePair(sources: List<String>, dest: String) {
        if (editingIndex >= 0) {
            pairs[editingIndex] = FolderPair(source = sources.first(), destination = dest)
            editingIndex = -1
        } else {
            pairs.addAll(sources.map { FolderPair(source = it, destination = dest) })
        }
        savePairs()
        showAddDialog = false
    }

    fun startMove() {
        if (pairs.isEmpty() || isMoving) return
        val allMoves = flattenMoverPairs(pairs)
        if (allMoves.isEmpty()) { ctx.toast(ctx.getString(R.string.no_files_found), Toast.LENGTH_SHORT); return }
        scope.launch {
            // With MANAGE_EXTERNAL_STORAGE (this app requires it, see all_files_access_title/
            // require_all_files_access) the app can already read/write/delete any file on shared
            // storage directly - createWriteRequest's consent dialog is redundant in that case, and
            // requesting it anyway was the actual cause of "Move all" immediately toasting
            // R.string.cancelled ("Abgebrochen") instead of moving anything: MoverWidgetProvider's
            // "move now" button enqueues the exact same worker with no consent step at all and
            // works fine, which is the tell that consent isn't actually needed here.
            if (!MediaStoreOps.hasAllFilesAccess(ctx)) {
                val uris = withContext(Dispatchers.IO) { MediaStoreOps.urisForPaths(ctx, allMoves.map { it.first }) }
                if (uris.isEmpty()) { ctx.toast(ctx.getString(R.string.no_files_found), Toast.LENGTH_SHORT); return@launch }
                val granted = try { moverConsent.request(MediaStoreOps.writeRequest(ctx, uris.map { it.second })) } catch (_: Exception) { false }
                if (!granted) { ctx.toast(ctx.getString(R.string.cancelled), Toast.LENGTH_SHORT); return@launch }
            }
            val items = allMoves.map { (srcPath, destPath) -> BatchJobItem(jobId = "", sourcePath = srcPath, targetPath = destPath) }
            activeJobId = MediaBatchWorker.enqueue(ctx, BatchOperation.MOVE_COPY_DELETE, items)
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_mover), fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (pairs.isEmpty() && !isMoving) {
                EmptyState(Icons.Default.Folder, stringResource(R.string.mover_no_pairs), subtitle = stringResource(R.string.mover_tap_to_add), modifier = Modifier.weight(1f))
            } else {
                if (isMoving) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = s.md, vertical = s.sm)) {
                        LinearProgressIndicator(progress = { if (moveTotal > 0) moveProgress.toFloat() / moveTotal else 0f }, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(s.xs))
                        Text("$moveProgress/$moveTotal", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(s.md)) {
                    items(pairs.toList(), key = { it.source + "→" + it.destination }) { pair ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = s.xs),
                            shape = RoundedCornerShape(org.fossify.gallery.compose.theme.Radius.md),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Row(Modifier.padding(s.md), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(s.xs))
                                        Text(pair.source.substringAfterLast('/'), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(" → ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(pair.destination.substringAfterLast('/'), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Text("${pair.source} → ${pair.destination}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                                }
                                IconButton(onClick = { pendingRemove = pair }, modifier = Modifier.size(40.dp), enabled = !isMoving) {
                                    Icon(Icons.Default.Delete, stringResource(org.fossify.commons.R.string.delete), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(12.dp)) }
                }
            }

            if (!isMoving) {
                Button(
                    onClick = { editingIndex = -1; showAddDialog = true },
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    shape = RoundedCornerShape(org.fossify.gallery.compose.theme.Radius.md),
                ) { Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.mover_add_pair)) }
                if (pairs.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = { startMove() },
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        shape = RoundedCornerShape(org.fossify.gallery.compose.theme.Radius.md),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    ) { Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.mover_move_all, pairs.size)) }
                }
            } else {
                TextButton(
                    onClick = { activeJobId?.let { androidx.work.WorkManager.getInstance(ctx).cancelUniqueWork(it) } },
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(8.dp),
                ) { Text(stringResource(R.string.cancel)) }
            }
        }
    }

    if (showAddDialog) {
        AddPairDialog(
            initialSource = if (editingIndex >= 0) pairs[editingIndex].source else "",
            initialDest = if (editingIndex >= 0) pairs[editingIndex].destination else "",
            onConfirm = { srcs, dest -> addOrUpdatePair(srcs, dest) },
            onDismiss = { showAddDialog = false; editingIndex = -1 },
            defPrefs = defPrefs,
        )
    }

    pendingRemove?.let { pair ->
        ConfirmDestructive(
            title = stringResource(R.string.action_remove),
            text = stringResource(R.string.mover_remove_pair_confirm),
            confirmLabel = stringResource(R.string.action_remove),
            onConfirm = {
                pendingRemove = null
                pairs.remove(pair)
                savePairs()
            },
            onDismiss = { pendingRemove = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddPairDialog(
    initialSource: String,
    initialDest: String,
    onConfirm: (List<String>, String) -> Unit,
    onDismiss: () -> Unit,
    defPrefs: android.content.SharedPreferences,
) {
    // Editing one specific existing pair only ever has (and keeps) exactly one source - multiple
    // sources are only offered while adding a brand new pair (see multiSelect below).
    val isEditing = initialSource.isNotBlank()
    val sources = remember { mutableStateListOf<String>().apply { if (initialSource.isNotBlank()) add(initialSource) } }
    var dest by remember { mutableStateOf(initialDest) }
    val ctx = LocalContext.current
    var showSearch by remember { mutableStateOf("") } // "source" or "dest" or ""
    // Set when "Last source"/"Last dest" is tapped - opens the Explorer sheet navigated straight to
    // that remembered folder instead of silently filling the field, so picking it is still a
    // deliberate confirm (and, for source, a jumping-off point to keep browsing/adding more) rather
    // than a blind paste.
    var pickerStartOverride by remember { mutableStateOf<String?>(null) }

    fun loadLast(name: String): String {
        val saved = defPrefs.getString(name, null) ?: return ""
        return if (File(saved).exists()) saved else ""
    }

    fun openPicker(target: String, startOverride: String? = null) {
        pickerStartOverride = startOverride
        showSearch = target
    }

    // Explorer-style browse-by-tapping-into-subfolders, with its own text search over the
    // already-indexed folder DB built in (see FolderPathPickerSheet) - the only way to pick a source
    // or destination here now, since that covers both typing-a-name-to-find and drilling into
    // subfolders without a second, redundant search field in this dialog.
    if (showSearch.isNotEmpty()) {
        val isSourcePick = showSearch == "source"
        // Destination defaults to wherever the user last browsed in the Explorer tab (falling back
        // to the current field value, then internal storage root) - usually where they're about to
        // file things away to next anyway.
        val initialPickerPath = pickerStartOverride
            ?: if (isSourcePick) sources.lastOrNull() ?: ctx.config.internalStoragePath
            else dest.ifBlank { ctx.config.lastExplorerPath.ifBlank { ctx.config.internalStoragePath } }
        FolderPathPickerSheet(
            title = if (isSourcePick) stringResource(R.string.mover_select_source) else stringResource(R.string.mover_select_dest),
            initialPath = initialPickerPath,
            onPathSelected = { path ->
                if (isSourcePick) { sources.clear(); sources.add(path); defPrefs.edit().putString("mover_last_source", path).apply() }
                else { dest = path; defPrefs.edit().putString("mover_last_dest", path).apply() }
            },
            onDismiss = { showSearch = ""; pickerStartOverride = null },
            suggestedFolderName = if (!isSourcePick && sources.size == 1) File(sources[0]).name else null,
            multiSelect = isSourcePick && !isEditing,
            initialSelectedPaths = sources.toList(),
            onPathsSelected = { picked ->
                sources.clear(); sources.addAll(picked)
                picked.lastOrNull()?.let { defPrefs.edit().putString("mover_last_source", it).apply() }
            },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) stringResource(R.string.mover_edit_pair) else stringResource(R.string.mover_new_pair)) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                val sourceSummary = when {
                    sources.isEmpty() -> ""
                    sources.size == 1 -> sources[0]
                    else -> stringResource(R.string.mover_n_sources_selected, sources.size)
                }
                FolderPickField(label = stringResource(R.string.source), path = sourceSummary, iconTint = MaterialTheme.colorScheme.primary, onClick = { openPicker("source") })
                if (sources.size > 1) {
                    Column(Modifier.padding(start = 4.dp, top = 2.dp)) {
                        sources.forEach { s ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(s, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                IconButton(onClick = { sources.remove(s) }, modifier = Modifier.size(40.dp)) {
                                    Icon(Icons.Default.Close, stringResource(R.string.action_remove), modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
                TextButton(onClick = {
                    val last = loadLast("mover_last_source")
                    if (last.isNotBlank()) openPicker("source", startOverride = last)
                }) { Text(stringResource(R.string.mover_last_source), style = MaterialTheme.typography.labelSmall) }
                Spacer(Modifier.height(12.dp))
                FolderPickField(label = stringResource(R.string.destination), path = dest, iconTint = MaterialTheme.colorScheme.tertiary, onClick = { openPicker("dest") })
                TextButton(onClick = {
                    val last = loadLast("mover_last_dest")
                    if (last.isNotBlank()) openPicker("dest", startOverride = last)
                }) { Text(stringResource(R.string.mover_last_dest), style = MaterialTheme.typography.labelSmall) }
            }
        },
        confirmButton = { TextButton(onClick = { if (sources.isNotEmpty() && dest.isNotBlank()) onConfirm(sources.toList().map { it.trimEnd('/') }, dest.trimEnd('/')) }) { Text(stringResource(org.fossify.commons.R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun FolderPickField(label: String, path: String, iconTint: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(org.fossify.gallery.compose.theme.Radius.md),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Folder, null, tint = iconTint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    path.ifBlank { stringResource(R.string.mover_tap_to_pick) },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (path.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Default.Search, stringResource(R.string.cd_search), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}

private fun uriToPath(uri: Uri, storageRoot: String): String? {
    return try {
        val docId = DocumentsContract.getTreeDocumentId(uri)
        val split = docId.indexOf(':')
        if (split >= 0) {
            val type = docId.substring(0, split)
            val relative = docId.substring(split + 1)
            (if (type == "primary") "$storageRoot/$relative" else "/storage/$type/$relative").trimEnd('/')
        } else null
    } catch (_: Exception) { null }
}

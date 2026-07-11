package org.fossify.gallery.compose.components
import androidx.compose.ui.res.stringResource
import org.fossify.gallery.R

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.commons.extensions.isAStorageRootFolder
import org.fossify.commons.extensions.toast
import org.fossify.gallery.compose.util.rememberMediaStoreConsent
import org.fossify.gallery.helpers.MediaStoreOps
import org.fossify.gallery.models.BatchJobItem
import org.fossify.gallery.workers.BatchOperation
import org.fossify.gallery.workers.MediaBatchWorker
import java.io.File

/**
 * Renames a folder by moving every media file underneath it (recursively) to the same path with the
 * folder segment swapped - there's no dedicated "rename this directory" MediaStore API, so this reuses
 * the exact MOVE_FAST batch machinery already used for regular file moves (RELATIVE_PATH update,
 * consent, progress, Room path sync). Every folder reachable from the Albums/Explorer selection UI is
 * guaranteed to contain at least one media file (both screens only ever show folders that do - a
 * directory with nothing in its subtree never appears as a tile in either), so there's no empty-folder
 * edge case to special-case here with a raw filesystem rename.
 *
 * One real folder tile *is* special-cased, though: a storage root (internal storage / an SD card's /
 * OTG's top level) can itself show up as a tile when its own media sits directly in it, not just in
 * subfolders. "Renaming" it wouldn't actually rename the root - MediaStore has no such concept - it
 * would instead move every one of its files one level down into a new subfolder of the same name,
 * silently restructuring the whole library. The legacy Views-based folder screen already refuses this
 * (see DirectoryAdapter.renameDir's isAStorageRootFolder check); this dialog refuses it too, matching
 * that existing behaviour instead of introducing a new, surprising way to "rename" a root.
 */
@Composable
fun FolderRenameDialog(folderPath: String, onDismiss: () -> Unit, onRenamed: (oldPath: String, newPath: String) -> Unit = { _, _ -> }) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val consent = rememberMediaStoreConsent()
    val focusRequester = remember { FocusRequester() }
    val currentName = remember(folderPath) { File(folderPath).name }
    val parentPath = remember(folderPath) { File(folderPath).parent ?: "" }
    var text by remember { mutableStateOf(currentName) }
    var errorRes by remember { mutableStateOf<Int?>(null) }

    var jobId by remember { mutableStateOf<String?>(null) }
    var plannedNewPath by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        if (ctx.isAStorageRootFolder(folderPath)) {
            ctx.toast(org.fossify.commons.R.string.rename_folder_root)
            onDismiss()
        } else {
            focusRequester.requestFocus()
        }
    }

    val workInfo by remember(jobId) {
        jobId?.let { WorkManager.getInstance(ctx).getWorkInfosForUniqueWorkFlow(it) } ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsState(initial = emptyList())
    val activeWorkInfo = workInfo.firstOrNull()

    LaunchedEffect(activeWorkInfo?.state, jobId) {
        val info = activeWorkInfo ?: return@LaunchedEffect
        if (info.state.isFinished) {
            // Fires even on partial failure (some files could be locked/gone) - matches
            // MediaBatchWorker's own tolerance for individual item failures elsewhere in the app;
            // the folder is treated as renamed to wherever the bulk of its contents landed.
            onRenamed(folderPath, plannedNewPath)
            onDismiss()
        }
    }

    fun startRename() {
        val trimmed = text.trim()
        if (trimmed.isBlank()) { errorRes = R.string.folder_name; return }
        if (trimmed.contains('/')) { errorRes = R.string.rename_folder_invalid_name; return }
        if (trimmed == currentName) { onDismiss(); return }
        val newPath = "${parentPath.trimEnd('/')}/$trimmed"
        if (File(newPath).exists()) { errorRes = R.string.rename_folder_exists; return }
        errorRes = null
        scope.launch {
            val entries = withContext(Dispatchers.IO) { MediaStoreOps.mediaEntriesUnder(ctx, folderPath) }
            if (entries.isEmpty()) { ctx.toast(ctx.getString(R.string.no_files_found)); onDismiss(); return@launch }
            val oldPrefix = folderPath.trimEnd('/')
            val items = entries.map { e -> BatchJobItem(jobId = "", sourcePath = e.path, targetPath = newPath + e.path.removePrefix(oldPrefix)) }
            val uris = withContext(Dispatchers.IO) { MediaStoreOps.urisForPaths(ctx, entries.map { it.path }) }.map { it.second }
            val granted = try {
                consent.request(MediaStoreOps.writeRequest(ctx, uris))
            } catch (_: Exception) { false }
            if (!granted) { ctx.toast(ctx.getString(R.string.cancelled)); onDismiss(); return@launch }
            plannedNewPath = newPath
            jobId = MediaBatchWorker.enqueue(ctx, BatchOperation.MOVE_FAST, items)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (jobId != null) stringResource(R.string.renaming_in_progress) else stringResource(R.string.action_rename)) },
        text = {
            if (jobId != null) {
                val progress = activeWorkInfo?.progress
                val done = progress?.getInt("done", 0) ?: 0
                val total = progress?.getInt("total", 0) ?: 0
                Column {
                    LinearProgressIndicator(progress = { if (total > 0) done.toFloat() / total else 0f }, modifier = Modifier.fillMaxWidth())
                    androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
                    Text("$done/$total", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; errorRes = null },
                    label = { Text(stringResource(R.string.folder_name)) },
                    singleLine = true,
                    isError = errorRes != null,
                    supportingText = errorRes?.let { { Text(stringResource(it)) } },
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                )
            }
        },
        confirmButton = {
            if (jobId == null) {
                TextButton(onClick = { startRename() }) { Text(stringResource(R.string.action_rename)) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(if (jobId != null) org.fossify.commons.R.string.close else R.string.cancel)) }
        },
    )
}

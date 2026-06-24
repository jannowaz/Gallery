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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import org.fossify.gallery.compose.components.EmptyState
import org.fossify.gallery.compose.util.rememberMediaStoreConsent
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.MediaStoreOps
import org.fossify.gallery.helpers.RefreshBus
import org.fossify.gallery.helpers.UndoAction
import org.fossify.gallery.helpers.UndoManager
import org.fossify.gallery.helpers.UndoType
import java.io.File

private data class FolderPair(val source: String = "", val destination: String = "")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoldersMoverScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val pairs = remember { mutableStateListOf<FolderPair>() }
    var showAddDialog by remember { mutableStateOf(false) }
    var isMoving by remember { mutableStateOf(false) }
    var moveProgress by remember { mutableIntStateOf(0) }
    var moveTotal by remember { mutableIntStateOf(1) }
    var movePhase by remember { mutableStateOf("") }
    var editingIndex by remember { mutableIntStateOf(-1) }
    val storageRoot = Environment.getExternalStorageDirectory().absolutePath
    val moverConsent = rememberMediaStoreConsent()

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

    fun addOrUpdatePair(source: String, dest: String) {
        val p = FolderPair(source = source, destination = dest)
        if (editingIndex >= 0) {
            pairs[editingIndex] = p
            editingIndex = -1
        } else {
            pairs.add(p)
        }
        showAddDialog = false
    }

    fun startMove() {
        if (pairs.isEmpty() || isMoving) return
        isMoving = true
        moveProgress = 0
        val allMoves = pairs.flatMap { pair ->
            val srcDir = File(pair.source)
            if (!srcDir.isDirectory) return@flatMap emptyList<Pair<String, String>>()
            val destBase = pair.destination
            srcDir.listFiles()?.filter { it.isFile }?.map { it.absolutePath to "$destBase/${it.name}" } ?: emptyList()
        }
        moveTotal = allMoves.size
        if (allMoves.isEmpty()) { isMoving = false; ctx.toast("Keine Dateien gefunden", Toast.LENGTH_SHORT); return }
        scope.launch {
            val uris = withContext(Dispatchers.IO) { MediaStoreOps.urisForPaths(ctx, allMoves.map { it.first }) }
            val granted = try { moverConsent.request(MediaStoreOps.writeRequest(ctx, uris.map { it.second })) } catch (_: Exception) { false }
            if (!granted) { ctx.toast("Abgebrochen", Toast.LENGTH_SHORT); isMoving = false; return@launch }
            var done = 0; var failed = 0
            val movedPaths = mutableListOf<String>()
            for ((srcPath, destPath) in allMoves) {
                movePhase = "$done/${allMoves.size}: ${File(srcPath).name}"
                val success = withContext(Dispatchers.IO) {
                    val destFile = File(destPath)
                    if (destFile.exists()) { failed++; return@withContext false }
                    destFile.parentFile?.mkdirs()
                    try {
                        val srcUri = MediaStoreOps.uriForPath(ctx, srcPath) ?: false
                        if (srcUri is android.net.Uri) {
                            val targetRel = MediaStoreOps.relativePathFor(destFile.parent ?: "")
                            val newUri = MediaStoreOps.copy(ctx, srcUri, destFile.name, targetRel, MediaStoreOps.isVideoPath(srcPath))
                            if (newUri != null) {
                                ctx.contentResolver.delete(srcUri, null, null)
                                movedPaths.add(srcPath)
                                true
                            } else false
                        } else false
                    } catch (_: Exception) { false }
                }
                if (success) done++ else failed++
                moveProgress = done + failed
            }
            if (movedPaths.isNotEmpty()) {
                UndoManager.push(UndoAction(paths = movedPaths.toSet(), type = UndoType.MOVE))
            }
            RefreshBus.trigger()
            movePhase = if (failed > 0) "$done verschoben, $failed fehlgeschlagen" else "Alle $done verschoben"
            ctx.toast(movePhase, Toast.LENGTH_SHORT)
            isMoving = false
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mover", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (pairs.isEmpty() && !isMoving) {
                EmptyState(Icons.Default.Folder, "Keine Ordner-Paare", subtitle = "Tippe unten auf + um ein Paar zu definieren", modifier = Modifier.weight(1f))
            } else {
                if (isMoving) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        LinearProgressIndicator(progress = { if (moveTotal > 0) moveProgress.toFloat() / moveTotal else 0f }, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(4.dp))
                        Text(movePhase, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                    }
                }
                LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
                    items(pairs.toList(), key = { it.source + "→" + it.destination }) { pair ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            shape = RoundedCornerShape(Radius.md),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(pair.source.substringAfterLast('/'), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(" → ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(pair.destination.substringAfterLast('/'), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Text("${pair.source} → ${pair.destination}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                                }
                                IconButton(onClick = { pairs.remove(pair) }, modifier = Modifier.size(32.dp), enabled = !isMoving) {
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
                    shape = RoundedCornerShape(Radius.md),
                ) { Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Paar hinzufügen") }
                if (pairs.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = { startMove() },
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        shape = RoundedCornerShape(Radius.md),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    ) { Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Alle verschieben (${pairs.size} Paare)") }
                }
            } else {
                TextButton(onClick = { isMoving = false }, modifier = Modifier.align(Alignment.CenterHorizontally).padding(8.dp)) { Text("Abbrechen") }
            }
        }
    }

    if (showAddDialog) {
        AddPairDialog(
            initialSource = if (editingIndex >= 0) pairs[editingIndex].source else "",
            initialDest = if (editingIndex >= 0) pairs[editingIndex].destination else "",
            onConfirm = { src, dest -> addOrUpdatePair(src, dest) },
            onDismiss = { showAddDialog = false; editingIndex = -1 },
            storageRoot = storageRoot,
        )
    }
}

@Composable
private fun AddPairDialog(
    initialSource: String,
    initialDest: String,
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit,
    storageRoot: String,
) {
    var source by remember { mutableStateOf(initialSource) }
    var dest by remember { mutableStateOf(initialDest) }
    val ctx = LocalContext.current

    val sourcePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val path = uriToPath(uri, storageRoot) ?: uri.toString()
            source = path
        }
    }
    val destPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val path = uriToPath(uri, storageRoot) ?: uri.toString()
            dest = path
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialSource.isNotBlank()) "Paar bearbeiten" else "Neues Paar") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                OutlinedTextField(value = source, onValueChange = { source = it }, label = { Text("Quelle") }, singleLine = true, modifier = Modifier.fillMaxWidth(), trailingIcon = { IconButton(onClick = { sourcePicker.launch(null) }) { Icon(Icons.Default.Folder, "Auswählen") } })
                Spacer(Modifier.height(4.dp))
                Text(source, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(value = dest, onValueChange = { dest = it }, label = { Text("Ziel") }, singleLine = true, modifier = Modifier.fillMaxWidth(), trailingIcon = { IconButton(onClick = { destPicker.launch(null) }) { Icon(Icons.Default.Folder, "Auswählen") } })
                Spacer(Modifier.height(4.dp))
                Text(dest, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        confirmButton = { TextButton(onClick = { if (source.isNotBlank() && dest.isNotBlank()) onConfirm(source.trimEnd('/'), dest.trimEnd('/')) }) { Text(stringResource(org.fossify.commons.R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
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

package org.fossify.gallery.compose.screens
import androidx.compose.ui.res.stringResource
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.OutlinedTextField
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
import org.fossify.gallery.compose.components.EmptyState
import org.fossify.gallery.compose.theme.LocalMediaRepository
import org.fossify.gallery.compose.theme.LocalSpacing
import org.fossify.gallery.compose.util.rememberMediaStoreConsent
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.MediaStoreOps
import org.fossify.gallery.models.BatchJobItem
import org.fossify.gallery.workers.BatchOperation
import org.fossify.gallery.workers.MediaBatchWorker
import java.io.File

private data class FolderPair(val source: String = "", val destination: String = "")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoldersMoverScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val s = LocalSpacing.current
    val defPrefs = remember { android.preference.PreferenceManager.getDefaultSharedPreferences(ctx) }
    val gson = remember { Gson() }
    fun loadPairs(): List<FolderPair> {
        val json = defPrefs.getString("mover_pairs", null) ?: return emptyList()
        return try { gson.fromJson(json, object : TypeToken<List<FolderPair>>() {}.type) } catch (_: Exception) { emptyList() }
    }
    val pairs = remember { mutableStateListOf<FolderPair>().also { it.addAll(loadPairs()) } }
    fun savePairs() { defPrefs.edit().putString("mover_pairs", gson.toJson(pairs.toList())).apply() }
    var showAddDialog by remember { mutableStateOf(false) }
    // Set once the move job is enqueued; the job keeps running via MediaBatchWorker (with its own
    // foreground notification) even if this screen is left - it no longer depends on this
    // composable's own coroutine scope surviving.
    var activeJobId by remember { mutableStateOf<String?>(null) }
    var editingIndex by remember { mutableIntStateOf(-1) }
    val storageRoot = Environment.getExternalStorageDirectory().absolutePath
    val moverConsent = rememberMediaStoreConsent()

    val workInfo by remember(activeJobId) {
        activeJobId?.let { androidx.work.WorkManager.getInstance(ctx).getWorkInfosForUniqueWorkFlow(it) } ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsState(initial = emptyList())
    val activeWorkInfo = workInfo.firstOrNull()
    val isMoving = activeJobId != null && activeWorkInfo?.state?.isFinished != true
    val moveProgressData = activeWorkInfo?.progress
    val moveProgress = moveProgressData?.getInt("done", 0) ?: 0
    val moveTotal = moveProgressData?.getInt("total", 1) ?: 1
    LaunchedEffect(activeWorkInfo?.state) {
        if (activeWorkInfo?.state?.isFinished == true) {
            ctx.toast(if (activeWorkInfo.state == androidx.work.WorkInfo.State.SUCCEEDED) "Alle $moveProgress verschoben" else "Verschieben beendet: $moveProgress erledigt", Toast.LENGTH_SHORT)
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

    fun addOrUpdatePair(source: String, dest: String) {
        val p = FolderPair(source = source, destination = dest)
        if (editingIndex >= 0) {
            pairs[editingIndex] = p
            editingIndex = -1
        } else {
            pairs.add(p)
        }
        savePairs()
        showAddDialog = false
    }

    fun startMove() {
        if (pairs.isEmpty() || isMoving) return
        val allMoves = pairs.flatMap { pair ->
            val srcDir = File(pair.source)
            if (!srcDir.isDirectory) return@flatMap emptyList<Pair<String, String>>()
            val destBase = pair.destination
            srcDir.listFiles()?.filter { it.isFile }?.map { it.absolutePath to "$destBase/${it.name}" } ?: emptyList()
        }
        if (allMoves.isEmpty()) { ctx.toast("Keine Dateien gefunden", Toast.LENGTH_SHORT); return }
        scope.launch {
            val uris = withContext(Dispatchers.IO) { MediaStoreOps.urisForPaths(ctx, allMoves.map { it.first }) }
            val granted = try { moverConsent.request(MediaStoreOps.writeRequest(ctx, uris.map { it.second })) } catch (_: Exception) { false }
            if (!granted) { ctx.toast("Abgebrochen", Toast.LENGTH_SHORT); return@launch }
            val items = allMoves.map { (srcPath, destPath) -> BatchJobItem(jobId = "", sourcePath = srcPath, targetPath = destPath) }
            activeJobId = MediaBatchWorker.enqueue(ctx, BatchOperation.MOVE_COPY_DELETE, items)
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
                                IconButton(onClick = { pairs.remove(pair); savePairs() }, modifier = Modifier.size(32.dp), enabled = !isMoving) {
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
                ) { Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Paar hinzufügen") }
                if (pairs.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = { startMove() },
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        shape = RoundedCornerShape(org.fossify.gallery.compose.theme.Radius.md),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    ) { Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Alle verschieben (${pairs.size} Paare)") }
                }
            } else {
                TextButton(
                    onClick = { activeJobId?.let { androidx.work.WorkManager.getInstance(ctx).cancelUniqueWork(it) } },
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(8.dp),
                ) { Text("Abbrechen") }
            }
        }
    }

    if (showAddDialog) {
        AddPairDialog(
            initialSource = if (editingIndex >= 0) pairs[editingIndex].source else "",
            initialDest = if (editingIndex >= 0) pairs[editingIndex].destination else "",
            onConfirm = { src, dest -> addOrUpdatePair(src, dest) },
            onDismiss = { showAddDialog = false; editingIndex = -1 },
            defPrefs = defPrefs,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderSearchDialog(
    title: String,
    onFolderPicked: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val repo = LocalMediaRepository.current
    var query by remember { mutableStateOf("") }
    var allDirs by remember { mutableStateOf<List<org.fossify.gallery.models.Directory>>(emptyList()) }
    LaunchedEffect(Unit) {
        allDirs = withContext(Dispatchers.IO) { repo.getAllDirectories().sortedBy { it.name.lowercase() } }
    }
    val filtered = remember(allDirs, query) {
        if (query.isBlank()) allDirs.take(50)
        else allDirs.filter { it.name.contains(query, ignoreCase = true) || it.path.contains(query, ignoreCase = true) }.take(50)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                OutlinedTextField(value = query, onValueChange = { query = it }, placeholder = { Text("Ordner suchen…") }, singleLine = true, modifier = Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp)) })
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                    items(filtered, key = { it.path }) { dir ->
                        Surface(
                            onClick = { onFolderPicked(dir.path) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(org.fossify.gallery.compose.theme.Radius.sm),
                        ) {
                            Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                                Text(dir.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (query.isNotBlank()) Text(dir.path, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun AddPairDialog(
    initialSource: String,
    initialDest: String,
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit,
    defPrefs: android.content.SharedPreferences,
) {
    var source by remember { mutableStateOf(initialSource) }
    var dest by remember { mutableStateOf(initialDest) }
    val ctx = LocalContext.current
    var showSearch by remember { mutableStateOf("") } // "source" or "dest" or ""

    fun loadLast(name: String): String {
        val saved = defPrefs.getString(name, null) ?: return ""
        return if (File(saved).exists()) saved else ""
    }

    if (showSearch.isNotEmpty()) {
            FolderSearchDialog(
                title = if (showSearch == "source") "Quelle auswählen" else "Ziel auswählen",
                onFolderPicked = { path ->
                    if (showSearch == "source") { source = path; defPrefs.edit().putString("mover_last_source", path).apply() }
                    else { dest = path; defPrefs.edit().putString("mover_last_dest", path).apply() }
                    showSearch = ""
                },
                onDismiss = { showSearch = "" },
            )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialSource.isNotBlank()) "Paar bearbeiten" else "Neues Paar") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                OutlinedTextField(value = source, onValueChange = { source = it }, label = { Text("Quelle") }, singleLine = true, modifier = Modifier.fillMaxWidth(), trailingIcon = { IconButton(onClick = { showSearch = "source" }) { Icon(Icons.Default.Search, "Durchsuchen") } })
                Text(source, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                TextButton(onClick = { source = loadLast("mover_last_source"); if (source.isNotBlank()) defPrefs.edit().putString("mover_last_source", source).apply() }) { Text("Letzte Quelle", style = MaterialTheme.typography.labelSmall) }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(value = dest, onValueChange = { dest = it }, label = { Text("Ziel") }, singleLine = true, modifier = Modifier.fillMaxWidth(), trailingIcon = { IconButton(onClick = { showSearch = "dest" }) { Icon(Icons.Default.Search, "Durchsuchen") } })
                Text(dest, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                TextButton(onClick = { dest = loadLast("mover_last_dest"); if (dest.isNotBlank()) defPrefs.edit().putString("mover_last_dest", dest).apply() }) { Text("Letztes Ziel", style = MaterialTheme.typography.labelSmall) }
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

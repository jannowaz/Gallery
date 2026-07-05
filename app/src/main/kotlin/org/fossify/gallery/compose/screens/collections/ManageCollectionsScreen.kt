package org.fossify.gallery.compose.screens.collections
import org.fossify.gallery.compose.theme.Radius

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.commons.extensions.toast
import org.fossify.gallery.R
import org.fossify.gallery.compose.components.ConfirmDestructive
import org.fossify.gallery.compose.theme.LocalMediaRepository
import org.fossify.gallery.models.MediaCollection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageCollectionsScreen(
    onBack: () -> Unit,
    onCollectionClick: (MediaCollection) -> Unit
) {
    val ctx = LocalContext.current
    val repo = LocalMediaRepository.current
    val scope = rememberCoroutineScope()
    var collections by remember { mutableStateOf<List<MediaCollection>>(emptyList()) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editingColl by remember { mutableStateOf<MediaCollection?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<MediaCollection?>(null) }

    var loadTrigger by remember { mutableIntStateOf(0) }
    LaunchedEffect(loadTrigger) {
        collections = withContext(Dispatchers.IO) {
            repo.getCollections()
        }
    }
    fun refresh() { loadTrigger++ }

    if (showDeleteConfirm != null) {
        ConfirmDestructive(
            title = stringResource(R.string.delete_collection),
            text = stringResource(R.string.delete_collection_confirm, showDeleteConfirm!!.name),
            confirmLabel = stringResource(org.fossify.commons.R.string.delete),
            onConfirm = {
                val c = showDeleteConfirm!!
                showDeleteConfirm = null
                scope.launch(Dispatchers.IO) {
                    try { repo.deleteCollection(c); withContext(Dispatchers.Main) { refresh() } }
                    catch (_: Exception) { withContext(Dispatchers.Main) { ctx.toast(R.string.delete_error) } }
                }
            },
            onDismiss = { showDeleteConfirm = null },
        )
    }

    if (showEditDialog) {
        EditCollectionDialog(
            initial = editingColl,
            onDismiss = { showEditDialog = false },
            onSave = { col ->
                scope.launch(Dispatchers.IO) {
                    try {
                        repo.insertCollection(col)
                        collections = repo.getCollections()
                        withContext(Dispatchers.Main) { showEditDialog = false }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) { ctx.toast("${ctx.getString(R.string.error_prefix)}: ${e.message}", Toast.LENGTH_LONG) }
                    }
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.collections)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editingColl = null; showEditDialog = true }) {
                Icon(Icons.Default.Add, stringResource(R.string.new_collection))
            }
        }
    ) { padding ->
        if (collections.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CollectionsBookmark, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.no_collections), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.tap_to_create_collection), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(collections, key = { it.id }) { coll ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onCollectionClick(coll) },
                        shape = RoundedCornerShape(Radius.md),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.CollectionsBookmark, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    coll.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val inclCount = coll.getIncludedPaths().size
                                val exclCount = coll.getExcludedPaths().size
                                if (inclCount > 0 || exclCount > 0) {
                                    val parts = mutableListOf<String>()
                                    if (inclCount > 0) parts.add(stringResource(R.string.folder_count, inclCount))
                                    if (exclCount > 0) parts.add(stringResource(R.string.folders_excluded, exclCount))
                                    Text(
                                        parts.joinToString(" · "),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            IconButton(onClick = { editingColl = coll; showEditDialog = true }, modifier = Modifier.size(44.dp)) {
                                Icon(Icons.Default.Edit, stringResource(R.string.edit), modifier = Modifier.size(20.dp))
                            }
                            IconButton(onClick = { showDeleteConfirm = coll }, modifier = Modifier.size(44.dp)) {
                                Icon(Icons.Default.Delete, stringResource(org.fossify.commons.R.string.delete), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditCollectionDialog(
    initial: MediaCollection?,
    onDismiss: () -> Unit,
    onSave: (MediaCollection) -> Unit
) {
    var name by remember(initial) { mutableStateOf(initial?.name ?: "") }
    var includedPaths by remember(initial) { mutableStateOf(initial?.getIncludedPaths() ?: emptyList()) }
    var excludedPaths by remember(initial) { mutableStateOf(initial?.getExcludedPaths() ?: emptyList()) }

    val ctx = LocalContext.current

    fun uriToPath(uri: Uri): String? {
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

    val inclPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val p = uriToPath(uri) ?: uri.toString()
            if (p !in includedPaths) includedPaths = includedPaths + p
        }
    }
    val exclPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val p = uriToPath(uri) ?: uri.toString()
            if (p !in excludedPaths) excludedPaths = excludedPaths + p
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial != null) stringResource(R.string.edit_collection) else stringResource(R.string.collection_create)) },
        text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(org.fossify.commons.R.string.name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))

                Text(stringResource(R.string.included_folders), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                includedPaths.forEach { p ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(p.substringAfterLast('/').take(50), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        IconButton(onClick = { includedPaths = includedPaths - p }, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.Delete, stringResource(R.string.remove), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                Surface(
                    onClick = { inclPicker.launch(null) },
                    shape = RoundedCornerShape(Radius.sm),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.add_folder), style = MaterialTheme.typography.labelSmall)
                    }
                }
                Spacer(Modifier.height(12.dp))

                Text(stringResource(R.string.excluded_folders), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                excludedPaths.forEach { p ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(p.substringAfterLast('/').take(50), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        IconButton(onClick = { excludedPaths = excludedPaths - p }, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.Delete, stringResource(R.string.remove), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                Surface(
                    onClick = { exclPicker.launch(null) },
                    shape = RoundedCornerShape(Radius.sm),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.exclude_folder), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) return@TextButton
                val col = (initial ?: MediaCollection(id = 0, name = "")).copy(
                    name = name,
                    includedPaths = MediaCollection.createPathsJson(includedPaths),
                    excludedPaths = MediaCollection.createPathsJson(excludedPaths),
                )
                onSave(col)
            }) { Text(stringResource(org.fossify.commons.R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(org.fossify.commons.R.string.cancel)) } }
    )
}

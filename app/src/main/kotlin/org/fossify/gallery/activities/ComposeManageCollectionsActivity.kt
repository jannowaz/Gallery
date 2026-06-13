package org.fossify.gallery.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.commons.extensions.toast
import org.fossify.gallery.compose.theme.GalleryTheme
import org.fossify.gallery.extensions.collectionDB
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.DIRECTORY
import org.fossify.gallery.models.MediaCollection

class ComposeManageCollectionsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GalleryTheme(darkTheme = config.forceDarkMode || isSystemInDarkTheme()) {
                ManageCollectionsScreen(
                    onBack = { finish() },
                    onCollectionClick = { coll ->
                        startActivity(Intent(this@ComposeManageCollectionsActivity, MediaActivity::class.java).apply {
                            putExtra(DIRECTORY, "collection:${coll.id}")
                        })
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManageCollectionsScreen(
    onBack: () -> Unit,
    onCollectionClick: (MediaCollection) -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var collections by remember { mutableStateOf<List<MediaCollection>>(emptyList()) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editingColl by remember { mutableStateOf<MediaCollection?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<MediaCollection?>(null) }

    var loadTrigger by remember { mutableIntStateOf(0) }
    LaunchedEffect(loadTrigger) {
        collections = withContext(Dispatchers.IO) {
            try { ctx.collectionDB.getAll() } catch (_: Exception) { emptyList() }
        }
    }
    fun refresh() { loadTrigger++ }

    if (showDeleteConfirm != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("Sammlung löschen") },
            text = { Text("»${showDeleteConfirm!!.name}« wirklich löschen?") },
            confirmButton = {
                TextButton(onClick = {
                    val c = showDeleteConfirm!!
                    showDeleteConfirm = null
                    scope.launch(Dispatchers.IO) {
                        try { ctx.collectionDB.delete(c); withContext(Dispatchers.Main) { refresh() } }
                        catch (_: Exception) { withContext(Dispatchers.Main) { ctx.toast("Fehler beim Löschen", Toast.LENGTH_SHORT) } }
                    }
                }) { Text("Löschen", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = null }) { Text("Abbrechen") } }
        )
    }

    if (showEditDialog) {
        EditCollectionDialog(
            initial = editingColl,
            onDismiss = { showEditDialog = false },
            onSave = { col ->
                scope.launch(Dispatchers.IO) {
                    try {
                        ctx.collectionDB.insert(col)
                        collections = try { ctx.collectionDB.getAll() } catch (_: Exception) { emptyList() }
                        withContext(Dispatchers.Main) { showEditDialog = false }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) { ctx.toast("Fehler: ${e.message}", Toast.LENGTH_LONG) }
                    }
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sammlungen") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editingColl = null; showEditDialog = true }) {
                Icon(Icons.Default.Add, "Neue Sammlung")
            }
        }
    ) { padding ->
        if (collections.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CollectionsBookmark, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    Spacer(Modifier.height(16.dp))
                    Text("Keine Sammlungen", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text("Tippe auf + um eine zu erstellen", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.CollectionsBookmark, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(
                                coll.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            IconButton(onClick = { editingColl = coll; showEditDialog = true }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.Edit, "Bearbeiten", modifier = Modifier.size(20.dp))
                            }
                            IconButton(onClick = { showDeleteConfirm = coll }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.Delete, "Löschen", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
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

    val inclPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val p = uri.toString()
            if (p !in includedPaths) includedPaths = includedPaths + p
        }
    }
    val exclPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val p = uri.toString()
            if (p !in excludedPaths) excludedPaths = excludedPaths + p
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial != null) "Sammlung bearbeiten" else "Sammlung erstellen") },
        text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))

                Text("Eingeschlossene Ordner:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                includedPaths.forEach { p ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(p.substringAfterLast('/').take(50), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        IconButton(onClick = { includedPaths = includedPaths - p }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Delete, "Entfernen", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                Surface(
                    onClick = { inclPicker.launch(null) },
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Ordner hinzufügen", style = MaterialTheme.typography.labelSmall)
                    }
                }
                Spacer(Modifier.height(12.dp))

                Text("Ausgeschlossene Ordner:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                excludedPaths.forEach { p ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(p.substringAfterLast('/').take(50), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        IconButton(onClick = { excludedPaths = excludedPaths - p }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Delete, "Entfernen", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                Surface(
                    onClick = { exclPicker.launch(null) },
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Ordner ausschließen", style = MaterialTheme.typography.labelSmall)
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
            }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}
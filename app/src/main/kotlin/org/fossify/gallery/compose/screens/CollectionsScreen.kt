package org.fossify.gallery.compose.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fossify.commons.extensions.toast
import org.fossify.gallery.extensions.collectionDB
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.mediaCacheDB
import org.fossify.gallery.models.MediaCollection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionsScreen(onCollectionClick: (MediaCollection) -> Unit = {}, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var collections by remember { mutableStateOf(try { ctx.collectionDB.getAll() } catch (_: Exception) { emptyList() }) }
    var editingColl by remember { mutableStateOf<MediaCollection?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }

    fun refresh() { collections = try { ctx.collectionDB.getAll() } catch (_: Exception) { emptyList() } }

    // Edit dialog
    if (showEditDialog) {
        var name by remember(editingColl) { mutableStateOf(editingColl?.name ?: "") }
        var includedUris by remember(editingColl) { mutableStateOf(editingColl?.getIncludedPaths() ?: emptyList()) }
        var excludedUris by remember(editingColl) { mutableStateOf(editingColl?.getExcludedPaths() ?: emptyList()) }
        var tagFilter by remember(editingColl) { mutableStateOf(editingColl?.tagFilter ?: "") }
        var ratingFilter by remember(editingColl) { mutableIntStateOf(editingColl?.ratingFilter ?: 0) }
        var searchQuery by remember(editingColl) { mutableStateOf(editingColl?.searchQuery ?: "") }
        var showAddIncluded by remember { mutableStateOf(false) }
        var showAddExcluded by remember { mutableStateOf(false) }

        val inclPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) includedUris = includedUris + uri.toString()
        }
        val exclPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) excludedUris = excludedUris + uri.toString()
        }

        var allCachedTags by remember { mutableStateOf<List<String>>(emptyList()) }
        androidx.compose.runtime.LaunchedEffect(Unit) {
            allCachedTags = try { withContext(kotlinx.coroutines.Dispatchers.IO) { ctx.mediaCacheDB.getAllTagged().flatMap { it.tags.split(",").filter(String::isNotBlank) }.distinct().sorted() } } catch (_: Exception) { emptyList() }
        }

        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text(if (editingColl != null) "Sammlung bearbeiten" else "Sammlung erstellen") },
            text = {
                Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    // Included folders
                    Text("Eingeschlossene Ordner:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                    includedUris.forEach { uri ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(uri, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            IconButton(onClick = { includedUris = includedUris - uri }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Delete, "Entfernen", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp)) }
                        }
                    }
                    Surface(onClick = { inclPicker.launch(null) }, shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Ordner hinzufügen", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    // Excluded folders
                    Text("Ausgeschlossene Ordner:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                    excludedUris.forEach { uri ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(uri, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            IconButton(onClick = { excludedUris = excludedUris - uri }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Delete, "Entfernen", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp)) }
                        }
                    }
                    Surface(onClick = { exclPicker.launch(null) }, shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Ordner ausschließen", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    // Tags
                    Text("Tags (kommagetrennt):", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(value = tagFilter, onValueChange = { tagFilter = it }, placeholder = { Text("z.B. Urlaub, Familie") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    if (allCachedTags.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            allCachedTags.take(30).forEach { t ->
                                val hasTag = tagFilter.split(",").any { it.trim().equals(t, ignoreCase = true) }
                                Surface(onClick = { tagFilter = if (hasTag) tagFilter.split(",").filter { it.trim() != t }.joinToString(",") else "${tagFilter},$t".trim(',') }, shape = RoundedCornerShape(12.dp), color = if (hasTag) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant) {
                                    Text(t, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), color = if (hasTag) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    // Rating
                    Text("Min. Bewertung:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                    Row(Modifier.fillMaxWidth()) {
                        Text("Alle", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(end = 8.dp, top = 4.dp).clickable { ratingFilter = 0 }, color = if (ratingFilter == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        for (i in 1..5) {
                            IconButton(onClick = { ratingFilter = if (ratingFilter == i) 0 else i }, modifier = Modifier.size(36.dp)) {
                                Icon(if (i <= ratingFilter) Icons.Default.Star else Icons.Default.StarBorder, "$i", tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(22.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    // Search
                    OutlinedTextField(value = searchQuery, onValueChange = { searchQuery = it }, label = { Text("Text-Suche (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isBlank()) return@TextButton
                    val col = (editingColl ?: MediaCollection(id = 0, name = "")).copy(
                        name = name,
                        includedPaths = MediaCollection.createPathsJson(includedUris),
                        excludedPaths = MediaCollection.createPathsJson(excludedUris),
                        tagFilter = tagFilter,
                        ratingFilter = ratingFilter,
                        searchQuery = searchQuery,
                    )
                    try { ctx.collectionDB.insert(col); refresh() } catch (_: Exception) { ctx.toast("Fehler beim Speichern", Toast.LENGTH_SHORT) }
                    showEditDialog = false
                }) { Text("Speichern") }
            },
            dismissButton = { TextButton(onClick = { showEditDialog = false }) { Text("Abbrechen") } }
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Sammlungen", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = { editingColl = null; showEditDialog = true }) { Icon(Icons.Default.Add, "Neue Sammlung") }
        }
        if (collections.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CollectionsBookmark, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    Spacer(Modifier.height(16.dp))
                    Text("Keine Sammlungen", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text("Tippe auf + um eine zu erstellen", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(8.dp)) {
                items(collections, key = { it.id }) { coll ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clickable { onCollectionClick(coll) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.CollectionsBookmark, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(coll.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val parts = mutableListOf<String>()
                                if (coll.ratingFilter > 0) parts.add("★ ${coll.ratingFilter}+")
                                if (coll.tagFilter.isNotBlank()) { val t = coll.tagFilter.split(",").filter { it.isNotBlank() }; if (t.size <= 2) parts.addAll(t) else parts.add("${t.size} Tags") }
                                if (coll.searchQuery.isNotBlank()) parts.add("\"${coll.searchQuery}\"")
                                if (parts.isNotEmpty()) Text(parts.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(onClick = { editingColl = coll; showEditDialog = true }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Edit, "Bearbeiten", modifier = Modifier.size(18.dp)) }
                            IconButton(onClick = {
                                try { ctx.collectionDB.delete(coll); refresh() } catch (_: Exception) { }
                            }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Delete, "Löschen", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp)) }
                        }
                    }
                }
            }
        }
    }
}
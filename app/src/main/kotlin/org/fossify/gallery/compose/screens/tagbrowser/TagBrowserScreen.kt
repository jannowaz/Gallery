package org.fossify.gallery.compose.screens.tagbrowser

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.commons.extensions.toast
import org.fossify.gallery.compose.screens.VideoThumbnail
import org.fossify.gallery.compose.components.GalleryImage
import org.fossify.gallery.compose.theme.LocalMediaRepository
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.mediaCacheDB
import org.fossify.gallery.helpers.VIDEO_EXTENSIONS
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TagBrowserScreen(
    onBack: () -> Unit,
    onTagFilterApplied: (tagPaths: Set<String>, tagName: String) -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = LocalMediaRepository.current

    var allTags by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    var scanning by remember { mutableStateOf(false) }
    var deleteConfirmTags by remember { mutableStateOf<Set<String>>(emptySet()) }
                var mergeTargetTag by remember { mutableStateOf<String?>(null) }
                var renameTargetTag by remember { mutableStateOf<String?>(null) }
                var pendingParentAssign by remember { mutableStateOf<Set<String>?>(null) }
    var selectedTags by remember { mutableStateOf<Set<String>>(emptySet()) }
    var tagSearchQuery by remember { mutableStateOf("") }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    val hierarchy = remember(refreshTrigger) { ctx.config.tagHierarchy }

    LaunchedEffect(refreshTrigger) {
        scanning = true
        withContext(Dispatchers.IO) {
            val tags = mutableMapOf<String, MutableList<String>>()
            try {
                val cached = ctx.mediaCacheDB.getAllTagged()
                if (cached.isNotEmpty()) {
                    cached.forEach { mc ->
                        mc.tags.split(",").filter { it.isNotBlank() }.forEach { t ->
                            tags.getOrPut(t.trim()) { mutableListOf() }.add(mc.fullPath)
                        }
                    }
                }
            } catch (_: Exception) { }
            withContext(Dispatchers.Main) { allTags = tags.entries.sortedByDescending { it.value.size }.associate { it.key to it.value }; scanning = false }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tags (${allTags.size})", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 8.dp)) {
            OutlinedTextField(
                value = tagSearchQuery,
                onValueChange = { tagSearchQuery = it },
                placeholder = { Text("Tag suchen") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, "Suchen", modifier = Modifier.size(18.dp)) },
                trailingIcon = { if (tagSearchQuery.isNotEmpty()) IconButton(onClick = { tagSearchQuery = "" }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Close, "Leeren", modifier = Modifier.size(16.dp)) } },
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyMedium,
                colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)),
                shape = RoundedCornerShape(12.dp),
            )
            Spacer(Modifier.height(8.dp))
            if (scanning) {
                Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (allTags.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                    Text("Keine Tags gefunden", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                val filteredTags = if (tagSearchQuery.isBlank()) allTags.entries.toList() else allTags.entries.filter { (tag, _) -> tag.contains(tagSearchQuery, ignoreCase = true) }.sortedByDescending { it.value.size }
                LazyColumn(Modifier.weight(1f)) {
                    items(filteredTags, key = { it.key }) { (tag, paths) ->
                        val thumbPath = paths.firstOrNull()
                        val isVideo = thumbPath?.let { it.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS } ?: false
                        val isSelected = tag in selectedTags
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).combinedClickable(
                                onClick = {
                                    onTagFilterApplied(paths.toSet(), tag)
                                    onBack()
                                },
                                onLongClick = { selectedTags = if (isSelected) selectedTags - tag else selectedTags + tag }
                            ),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surface)) {
                                    if (thumbPath != null && File(thumbPath).exists()) {
                                        if (isVideo) {
                                            VideoThumbnail(videoPath = thumbPath, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                        } else {
                                            GalleryImage(path = thumbPath, contentDescription = tag, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop, placeholderIconSize = 16.dp)
                                        }
                                    } else {
                                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                                            Icon(Icons.AutoMirrored.Filled.Label, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                                        }
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(tag, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    val parent = hierarchy[tag]
                                    val children = hierarchy.filter { it.value == tag }.keys
                                    val parts = mutableListOf("${paths.size} Dateien")
                                    if (parent != null) parts.add("← $parent")
                                    if (children.isNotEmpty()) parts.add("→ ${children.size} Kinder")
                                    Text(parts.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                if (isSelected) {
                                    Icon(Icons.Default.Close, "Ausgewählt", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
                if (selectedTags.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(onClick = {
                            val tagPaths = selectedTags.flatMap { allTags[it] ?: emptyList() }.toSet()
                            onBack()
                            onTagFilterApplied(tagPaths, selectedTags.joinToString(", "))
                        }, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f)) {
                            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.Center) { Text("Filtern", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimary) }
                        }
                        Surface(onClick = { deleteConfirmTags = selectedTags }, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.weight(1f)) {
                            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.Center) { Text("Löschen", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onErrorContainer) }
                        }
                    }
                    if (selectedTags.size == 1) {
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(onClick = { renameTargetTag = selectedTags.sorted().firstOrNull() }, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.weight(1f)) {
                                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.Center) { Text("Umbenennen", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSecondaryContainer) }
                            }
                            Surface(onClick = { pendingParentAssign = selectedTags }, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.weight(1f)) {
                                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.Center) { Text("Parent", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSecondaryContainer) }
                            }
                        }
                    } else {
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(onClick = { pendingParentAssign = selectedTags }, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.weight(1f)) {
                                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.Center) { Text("Parent", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSecondaryContainer) }
                            }
                            Surface(onClick = { mergeTargetTag = selectedTags.sorted().firstOrNull() }, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.weight(1f)) {
                                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.Center) { Text("Merge", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onTertiaryContainer) }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }

    if (deleteConfirmTags.isNotEmpty()) {
        val tagsToDelete = deleteConfirmTags
        val totalFiles = tagsToDelete.flatMap { allTags[it] ?: emptyList() }.distinct().size
        AlertDialog(
            onDismissRequest = { deleteConfirmTags = emptySet() },
            title = { Text(if (tagsToDelete.size == 1) "Tag entfernen" else "Tags entfernen") },
            text = {
                if (tagsToDelete.size == 1) Text("Tag \"${tagsToDelete.first()}\" aus $totalFiles Dateien entfernen? Die Dateien bleiben erhalten.")
                else Text("${tagsToDelete.size} Tags (${tagsToDelete.joinToString(", ")}) aus $totalFiles Dateien entfernen? Die Dateien bleiben erhalten.")
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch(Dispatchers.IO) {
                        tagsToDelete.forEach { tag ->
                            val pathsForTag = allTags[tag] ?: return@forEach
                            pathsForTag.forEach { p -> repo.removeTag(p, tag) }
                        }
                        try {
                            val cached = ctx.mediaCacheDB.getAllTagged().filter { mc -> mc.tags.split(",").map { it.trim() }.any { it in tagsToDelete } }
                            cached.forEach { mc ->
                                var newTags = mc.tags
                                tagsToDelete.forEach { tag -> newTags = newTags.split(",").filter { it.trim() != tag }.joinToString(",") }
                                ctx.mediaCacheDB.upsertAll(listOf(mc.copy(tags = newTags)))
                            }
                        } catch (_: Exception) { }
                        // Drop orphaned hierarchy entries referencing the deleted tags.
                        try {
                            val h = ctx.config.tagHierarchy
                            val cleaned = h.filterKeys { it !in tagsToDelete }.filterValues { it !in tagsToDelete }
                            if (cleaned.size != h.size) ctx.config.tagHierarchy = cleaned.toMutableMap()
                        } catch (_: Exception) { }
                        withContext(Dispatchers.Main) {
                            ctx.toast("${tagsToDelete.size} Tag${if (tagsToDelete.size != 1) "s" else ""} aus $totalFiles Dateien entfernt", Toast.LENGTH_SHORT)
                            deleteConfirmTags = emptySet(); refreshTrigger++; selectedTags = emptySet()
                        }
                    }
                }) { Text("Entfernen", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteConfirmTags = emptySet() }) { Text("Abbrechen") } }
        )
    }

    if (pendingParentAssign != null) {
        val tagsToAssign = pendingParentAssign!!
        val candidates = allTags.keys.filter { it !in tagsToAssign }.sorted()
        var selectedParent by remember { mutableStateOf(candidates.firstOrNull() ?: "") }
        AlertDialog(
            onDismissRequest = { pendingParentAssign = null },
            title = { Text("Eltern-Tag zuweisen") },
            text = {
                Column {
                    Text("Setze Eltern-Tag für: ${tagsToAssign.joinToString(", ")}")
                    Spacer(Modifier.height(8.dp))
                    if (candidates.isEmpty()) Text("Keine anderen Tags vorhanden", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else {
                        OutlinedTextField(
                            value = selectedParent,
                            onValueChange = { selectedParent = it },
                            label = { Text("Eltern-Tag") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (selectedParent.isNotBlank()) {
                        val h = ctx.config.tagHierarchy.toMutableMap()
                        fun createsCycle(child: String, parent: String): Boolean {
                            var cur: String? = parent
                            val seen = HashSet<String>()
                            while (cur != null && seen.add(cur)) {
                                if (cur == child) return true
                                cur = h[cur]
                            }
                            return false
                        }
                        val invalid = selectedParent in tagsToAssign || tagsToAssign.any { createsCycle(it, selectedParent) }
                        if (invalid) {
                            ctx.toast("Ungültiger Eltern-Tag (Zyklus)", Toast.LENGTH_SHORT)
                        } else {
                            tagsToAssign.forEach { h[it] = selectedParent }
                            ctx.config.tagHierarchy = h
                            ctx.toast("Eltern-Tag gesetzt", Toast.LENGTH_SHORT)
                            pendingParentAssign = null; selectedTags = emptySet(); refreshTrigger++
                            return@TextButton
                        }
                    }
                    pendingParentAssign = null; selectedTags = emptySet()
                }) { Text("Speichern") }
            },
            dismissButton = { TextButton(onClick = { pendingParentAssign = null }) { Text("Abbrechen") } }
        )
    }

    if (mergeTargetTag != null) {
        val defaultTarget = mergeTargetTag!!
        var mergeTargetName by remember(mergeTargetTag) { mutableStateOf(defaultTarget) }
        val existingTags = allTags.keys.filter { it != defaultTarget }.sorted()
        AlertDialog(
            onDismissRequest = { mergeTargetTag = null },
            title = { Text("Merge Tags") },
            text = {
                Column {
                    Text("Quellen: ${selectedTags.joinToString(", ")}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = mergeTargetName, onValueChange = { mergeTargetName = it }, label = { Text("Ziel-Tag") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    if (existingTags.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("Vorhandene Tags:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            existingTags.forEach { t ->
                                Surface(onClick = { mergeTargetName = t }, shape = RoundedCornerShape(12.dp), color = if (t == mergeTargetName) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant) {
                                    Text(t, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = if (t == mergeTargetName) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = mergeTargetName.trim()
                    if (target.isBlank()) return@TextButton
                    val sources = selectedTags - target
                    scope.launch(Dispatchers.IO) {
                        sources.forEach { srcTag ->
                            val srcPaths = allTags[srcTag] ?: return@forEach
                            srcPaths.forEach { p ->
                                repo.addTag(p, target)
                                repo.removeTag(p, srcTag)
                            }
                        }
                        try {
                            val cached = ctx.mediaCacheDB.getAllTagged().filter { mc -> mc.tags.split(",").map { it.trim() }.any { it in sources } }
                            cached.forEach { mc ->
                                var newTags = mc.tags
                                sources.forEach { src -> newTags = newTags.split(",").filter { it.trim() != src }.joinToString(",") }
                                if (target !in newTags.split(",").map { it.trim() }) newTags = "$newTags,$target"
                                ctx.mediaCacheDB.upsertAll(listOf(mc.copy(tags = newTags)))
                            }
                        } catch (_: Exception) { }
                        withContext(Dispatchers.Main) {
                            ctx.toast("Zu \"$target\" gemerged", Toast.LENGTH_SHORT)
                            mergeTargetTag = null; refreshTrigger++; selectedTags = emptySet()
                        }
                    }
                }) { Text("Merge") }
            },
            dismissButton = { TextButton(onClick = { mergeTargetTag = null }) { Text("Abbrechen") } }
        )
    }

    if (renameTargetTag != null) {
        val oldName = renameTargetTag!!
        var newName by remember(renameTargetTag) { mutableStateOf(oldName) }
        val count = allTags[oldName]?.size ?: 0
        AlertDialog(
            onDismissRequest = { renameTargetTag = null },
            title = { Text("Tag umbenennen") },
            text = {
                Column {
                    Text("\"$oldName\" ($count Dateien) umbenennen in:", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("Neuer Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = newName.trim()
                    if (target.isBlank() || target == oldName) return@TextButton
                    scope.launch(Dispatchers.IO) {
                        val paths = allTags[oldName] ?: emptyList()
                        paths.forEach { p -> repo.addTag(p, target); repo.removeTag(p, oldName) }
                        try {
                            val cached = ctx.mediaCacheDB.getAllTagged().filter { mc -> mc.tags.split(",").map { it.trim() }.any { it == oldName } }
                            cached.forEach { mc ->
                                var newTags = mc.tags.split(",").map { it.trim() }.toMutableList()
                                val idx = newTags.indexOf(oldName)
                                if (idx >= 0) { newTags[idx] = target; newTags = newTags.distinct().toMutableList() }
                                ctx.mediaCacheDB.upsertAll(listOf(mc.copy(tags = newTags.joinToString(","))))
                            }
                        } catch (_: Exception) { }
                        withContext(Dispatchers.Main) {
                            ctx.toast("\"$oldName\" → \"$target\" ($count Dateien)", Toast.LENGTH_SHORT)
                            renameTargetTag = null; refreshTrigger++; selectedTags = emptySet()
                        }
                    }
                }) { Text("Umbenennen") }
            },
            dismissButton = { TextButton(onClick = { renameTargetTag = null }) { Text("Abbrechen") } }
        )
    }
}

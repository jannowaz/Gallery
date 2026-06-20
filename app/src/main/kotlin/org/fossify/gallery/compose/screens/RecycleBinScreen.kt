package org.fossify.gallery.compose.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.gallery.compose.components.GalleryImage
import org.fossify.gallery.compose.theme.LocalMediaRepository
import org.fossify.gallery.extensions.mediaDB
import org.fossify.gallery.helpers.RefreshBus
import org.fossify.gallery.models.Medium
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecycleBinScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val repo = LocalMediaRepository.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<Medium>>(emptyList()) }
    var refresh by remember { mutableIntStateOf(0) }
    var showEmptyConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(refresh) {
        items = withContext(Dispatchers.IO) { try { ctx.mediaDB.getDeletedMedia() } catch (_: Exception) { emptyList() } }
    }

    BackHandler(enabled = showEmptyConfirm) { showEmptyConfirm = false }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Papierkorb", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") } },
                actions = {
                    if (items.isNotEmpty()) TextButton(onClick = { showEmptyConfirm = true }) { Text("Leeren", color = MaterialTheme.colorScheme.error) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        }
    ) { padding ->
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Papierkorb ist leer", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(8.dp)) {
                items(items, key = { it.path }) { m ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(56.dp).clip(RoundedCornerShape(8.dp))) {
                            GalleryImage(path = m.path, contentDescription = m.name, modifier = Modifier.fillMaxSize())
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(m.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(File(m.path).parent ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = {
                            scope.launch(Dispatchers.IO) { repo.restoreFromRecycleBin(m.path); RefreshBus.trigger() }
                            refresh++
                        }) { Icon(Icons.Default.Restore, "Wiederherstellen", tint = MaterialTheme.colorScheme.primary) }
                        IconButton(onClick = {
                            scope.launch(Dispatchers.IO) { repo.deleteMedium(m.path) }
                            refresh++
                        }) { Icon(Icons.Default.DeleteForever, "Endgültig löschen", tint = MaterialTheme.colorScheme.error) }
                    }
                    HorizontalDivider(Modifier.padding(start = 76.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                }
            }
        }
    }

    if (showEmptyConfirm) {
        val count = items.size
        AlertDialog(
            onDismissRequest = { showEmptyConfirm = false },
            title = { Text("Papierkorb leeren") },
            text = { Text("$count Dateien endgültig löschen? Das kann nicht rückgängig gemacht werden.") },
            confirmButton = {
                TextButton(onClick = {
                    showEmptyConfirm = false
                    val toDelete = items.map { it.path }
                    scope.launch(Dispatchers.IO) { toDelete.forEach { repo.deleteMedium(it) } }
                    refresh++
                }) { Text("Endgültig löschen", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showEmptyConfirm = false }) { Text("Abbrechen") } },
        )
    }
}

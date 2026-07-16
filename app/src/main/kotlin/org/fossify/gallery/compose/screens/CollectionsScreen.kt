package org.fossify.gallery.compose.screens
import org.fossify.gallery.compose.theme.AppMotion
import org.fossify.gallery.compose.theme.Radius

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
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
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.commons.extensions.toast
import org.fossify.gallery.compose.components.EmptyState
import org.fossify.gallery.compose.components.LibraryAlbumGrid
import org.fossify.gallery.compose.components.ConfirmDestructive
import org.fossify.gallery.compose.components.AlbumGridItem
import org.fossify.gallery.compose.theme.LocalMediaRepository
import org.fossify.gallery.compose.theme.RatingStarColor
import org.fossify.gallery.extensions.config
import org.fossify.gallery.R
import androidx.compose.ui.res.stringResource
import org.fossify.gallery.models.MediaCollection
import java.io.File

private fun pathDisplayName(uri: String): String {
    // content:// URI → extract readable path
    val idx = uri.lastIndexOf("%3A")
    if (idx >= 0) return java.net.URLDecoder.decode(uri.substring(idx + 3), "UTF-8").replace("/", " › ")
    // filesystem path
    val f = File(uri)
    if (f.exists()) return f.name
    // fallback
    val last = uri.substringAfterLast('/')
    return last.take(60)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionsScreen(onCollectionClick: (MediaCollection) -> Unit = {}, modifier: Modifier = Modifier, viewSettings: ViewSettings = ViewSettings()) {
    val ctx = LocalContext.current
    val repo = LocalMediaRepository.current
    val scope = rememberCoroutineScope()
    // Seed both from the repo-level cache instead of empty lists - CollectionsScreen's whole
    // composable is disposed and recreated whenever the user navigates to Viewer/Settings/etc. and
    // back, and without this the collections list plus the per-collection getMediaFromPath cascade
    // reran from scratch on every single round trip.
    fun toAlbumItems(colls: List<MediaCollection>) = colls.map { coll ->
        val paths = repo.getCollectionMediaCached(coll.id)
        AlbumGridItem(key = coll.id.toString(), name = coll.name, thumbnailPath = paths.firstOrNull() ?: "", count = paths.size, previewPaths = paths.take(3))
    }
    var collections by remember { mutableStateOf(repo.getCollectionsCached() ?: emptyList()) }
    var albumItems by remember { mutableStateOf(toAlbumItems(collections)) }
    var editingColl by remember { mutableStateOf<MediaCollection?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }
    var deleteConfirm by remember { mutableStateOf<MediaCollection?>(null) }
    var actionColl by remember { mutableStateOf<MediaCollection?>(null) }
    // Only true before the very first load ever completes (cache still null) - otherwise a stale
    // "no collections" EmptyState would flash for a moment before the real, populated grid replaces it.
    var isLoading by remember { mutableStateOf(repo.getCollectionsCached() == null) }

    // Re-runs both the collections list and the per-collection getMediaFromPath cascade (Room
    // verbietet Main-Thread) and refreshes the repo-level cache so a later dispose/recompose (e.g. a
    // Viewer round trip) can seed from it instead of recomputing from scratch.
    suspend fun reload() {
        val colls = withContext(Dispatchers.IO) { repo.refreshCollectionsCache() }
        collections = colls
        albumItems = toAlbumItems(colls)
        isLoading = false
    }
    LaunchedEffect(Unit) {
        if (repo.getCollectionsCached() == null) reload()
    }

    LaunchedEffect(Unit) {
        org.fossify.gallery.helpers.RefreshBus.events.collect { reload() }
    }

    actionColl?.let { coll ->
        AlertDialog(
            onDismissRequest = { actionColl = null },
            title = { Text(coll.name) },
            text = {
                Column {
                    val pinned = coll.id.toString() in ctx.config.pinnedCollections
                    TextButton(onClick = { val cur = ctx.config.pinnedCollections; ctx.config.pinnedCollections = if (pinned) cur - coll.id.toString() else cur + coll.id.toString(); org.fossify.gallery.helpers.RefreshBus.trigger(); actionColl = null }, modifier = Modifier.fillMaxWidth()) { Text(if (pinned) stringResource(R.string.unpin_from_drawer) else stringResource(R.string.pin_to_drawer)) }
                    TextButton(onClick = { editingColl = coll; showEditDialog = true; actionColl = null }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(org.fossify.commons.R.string.edit)) }
                    TextButton(onClick = { deleteConfirm = coll; actionColl = null }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(org.fossify.commons.R.string.delete), color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { actionColl = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    deleteConfirm?.let { coll ->
        ConfirmDestructive(
            title = stringResource(R.string.delete_collection),
            text = stringResource(R.string.delete_collection_confirm, coll.name),
            confirmLabel = stringResource(org.fossify.commons.R.string.delete),
            onConfirm = {
                deleteConfirm = null
                scope.launch { try { withContext(Dispatchers.IO) { repo.deleteCollection(coll) }; reload() } catch (_: Exception) { } }
            },
            onDismiss = { deleteConfirm = null },
        )
    }

    // Edit dialog
    if (showEditDialog) {
        var name by remember(editingColl) { mutableStateOf(editingColl?.name ?: "") }
        var includedUris by remember(editingColl) { mutableStateOf(editingColl?.getIncludedPaths() ?: emptyList()) }
        var excludedUris by remember(editingColl) { mutableStateOf(editingColl?.getExcludedPaths() ?: emptyList()) }
        var tagFilter by remember(editingColl) { mutableStateOf(editingColl?.tagFilter ?: "") }
        var ratingFilter by remember(editingColl) { mutableIntStateOf(editingColl?.ratingFilter ?: 0) }
        var searchQuery by remember(editingColl) { mutableStateOf(editingColl?.searchQuery ?: "") }
        val inclPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) { try { ctx.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}; val p = uri.toString(); if (p !in includedUris) includedUris = includedUris + p }
        }
        val exclPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) { try { ctx.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}; val p = uri.toString(); if (p !in excludedUris) excludedUris = excludedUris + p }
        }

        var allCachedTags by remember { mutableStateOf<List<String>>(emptyList()) }
        androidx.compose.runtime.LaunchedEffect(Unit) {
            allCachedTags = withContext(kotlinx.coroutines.Dispatchers.IO) { repo.getAllTags() }
        }

        fun saveCollection() {
            if (name.isBlank()) return
            val col = (editingColl ?: MediaCollection(id = 0, name = "")).copy(
                name = name,
                includedPaths = MediaCollection.createPathsJson(includedUris),
                excludedPaths = MediaCollection.createPathsJson(excludedUris),
                tagFilter = tagFilter,
                ratingFilter = ratingFilter,
                searchQuery = searchQuery,
            )
            scope.launch {
                try {
                    withContext(Dispatchers.IO) { repo.insertCollection(col) }
                    reload()
                    showEditDialog = false
                } catch (e: Exception) {
                    android.util.Log.e("Collections", "Save failed", e)
                    ctx.toast(ctx.getString(R.string.error_generic, e.message), Toast.LENGTH_LONG)
                }
            }
        }

        Dialog(onDismissRequest = { showEditDialog = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { showEditDialog = false }) { Icon(Icons.Default.Close, stringResource(R.string.cancel)) }
                        Text(if (editingColl != null) stringResource(R.string.edit_collection) else stringResource(R.string.create_collection), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        TextButton(onClick = { saveCollection() }) { Text(stringResource(org.fossify.commons.R.string.save)) }
                    }
                    HorizontalDivider()
                    Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(org.fossify.commons.R.string.name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    // Included folders
                    Text(stringResource(R.string.included_folders), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                    includedUris.forEach { uri ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(pathDisplayName(uri), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            IconButton(onClick = { includedUris = includedUris - uri }, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.Delete, stringResource(R.string.action_remove), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp)) }
                        }
                    }
                    Surface(onClick = { inclPicker.launch(null) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(Radius.sm), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.add_folder), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    // Excluded folders
                    Text(stringResource(R.string.excluded_folders), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                    excludedUris.forEach { uri ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(pathDisplayName(uri), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            IconButton(onClick = { excludedUris = excludedUris - uri }, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.Delete, stringResource(R.string.action_remove), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp)) }
                        }
                    }
                    Surface(onClick = { exclPicker.launch(null) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(Radius.sm), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.exclude_folder), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    // Tags
                    Text(stringResource(R.string.tags_comma_label), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(value = tagFilter, onValueChange = { tagFilter = it }, placeholder = { Text(stringResource(R.string.tags_example_hint)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    if (allCachedTags.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            allCachedTags.take(30).forEach { t ->
                                val hasTag = tagFilter.split(",").any { it.trim().equals(t, ignoreCase = true) }
                                Surface(onClick = { tagFilter = if (hasTag) tagFilter.split(",").filter { it.trim() != t }.joinToString(",") else "${tagFilter},$t".trim(',') }, shape = RoundedCornerShape(Radius.md), color = if (hasTag) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant) {
                                    Text(t, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), color = if (hasTag) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    // Rating
                    Text(stringResource(R.string.min_rating_label), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                    Row(Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.filter_all), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(end = 8.dp, top = 4.dp).clickable { ratingFilter = 0 }, color = if (ratingFilter == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        for (i in 1..5) {
                            IconButton(onClick = { ratingFilter = if (ratingFilter == i) 0 else i }, modifier = Modifier.size(44.dp)) {
                                Icon(if (i <= ratingFilter) Icons.Default.Star else Icons.Default.StarBorder, stringResource(R.string.cd_rating_star, i), tint = RatingStarColor, modifier = Modifier.size(22.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    // Search
                    OutlinedTextField(value = searchQuery, onValueChange = { searchQuery = it }, label = { Text(stringResource(R.string.text_search_optional)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.nav_collections), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = { editingColl = null; showEditDialog = true }) { Icon(Icons.Default.Add, stringResource(R.string.new_collection)) }
        }
        val contentState = when {
            isLoading -> "loading"
            collections.isEmpty() -> "empty"
            else -> "content"
        }
        Crossfade(targetState = contentState, animationSpec = AppMotion.short, label = "collectionsContent", modifier = Modifier.weight(1f)) { s ->
            when (s) {
                "loading" -> MediaSkeleton(columns = viewSettings.columnCount)
                "empty" -> EmptyState(Icons.Default.CollectionsBookmark, stringResource(R.string.no_collections), subtitle = stringResource(R.string.tap_to_create_collection))
                else -> LibraryAlbumGrid(
                    items = albumItems,
                    viewSettings = viewSettings,
                    onClick = { item -> collections.find { it.id.toString() == item.key }?.let(onCollectionClick) },
                    onLongClick = { item -> actionColl = collections.find { it.id.toString() == item.key } },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

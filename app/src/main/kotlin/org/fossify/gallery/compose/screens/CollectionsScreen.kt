package org.fossify.gallery.compose.screens
import org.fossify.gallery.compose.theme.AppMotion
import org.fossify.gallery.compose.theme.Radius

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.commons.extensions.toast
import org.fossify.gallery.compose.components.EmptyState
import org.fossify.gallery.compose.components.LibraryAlbumGrid
import org.fossify.gallery.compose.components.ConfirmDestructive
import org.fossify.gallery.compose.components.AlbumGridItem
import org.fossify.gallery.compose.components.LibraryScreenHeader
import org.fossify.gallery.compose.components.RefreshActionButton
import org.fossify.gallery.compose.components.viewSettingsSummary
import org.fossify.gallery.compose.components.LoadErrorBanner
import org.fossify.gallery.compose.components.LoadErrorState
import org.fossify.gallery.compose.components.InlineRefreshIndicator
import org.fossify.gallery.compose.components.SelectionAppBar
import org.fossify.gallery.helpers.selectionForAll
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
    // Filesystem paths can be displayed without a synchronous exists() stat; collections may point
    // at a folder that is temporarily unavailable, and the UI only needs its readable leaf name.
    val f = File(uri)
    if (f.name.isNotBlank()) return f.name
    // fallback
    val last = uri.substringAfterLast('/')
    return last.take(60)
}

private val collectionSelectionSaver = androidx.compose.runtime.saveable.listSaver<Set<String>, String>(
    save = { it.toList() },
    restore = { it.toSet() },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionsScreen(
    onCollectionClick: (MediaCollection) -> Unit = {},
    modifier: Modifier = Modifier,
    viewSettings: ViewSettings = ViewSettings(),
    externalSearchQuery: String = "",
    onClearExternalSearch: () -> Unit = {},
    onOpenViewSettings: (() -> Unit)? = null,
    tabIndex: Int? = null,
) {
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
    var isRefreshing by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var selectedCollectionIds by rememberSaveable(stateSaver = collectionSelectionSaver) { mutableStateOf<Set<String>>(emptySet()) }
    var deleteConfirmCollections by remember { mutableStateOf<Set<MediaCollection>>(emptySet()) }

    val selectionBackState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
    NavigationBackHandler(
        state = selectionBackState,
        isBackEnabled = selectedCollectionIds.isNotEmpty(),
        onBackCompleted = { selectedCollectionIds = emptySet() },
    )

    val visibleCollections = remember(collections, externalSearchQuery) {
        val query = externalSearchQuery.trim()
        if (query.isBlank()) collections else collections.filter { it.name.contains(query, ignoreCase = true) }
    }
    val selectableCollectionIds = remember(visibleCollections) {
        visibleCollections.filter { it.id > 0L }.mapTo(hashSetOf()) { it.id.toString() }
    }
    val visibleAlbumItems = remember(albumItems, visibleCollections) {
        val visibleIds = visibleCollections.mapTo(hashSetOf()) { it.id.toString() }
        albumItems.filter { it.key in visibleIds }
    }

    LaunchedEffect(visibleCollections) {
        // Search changes the visible action scope. Do not keep selections for hidden collections,
        // otherwise the selection bar can report and mutate items the user cannot currently see.
        val visibleIds = visibleCollections.mapTo(hashSetOf()) { it.id.toString() }
        selectedCollectionIds = selectedCollectionIds.intersect(visibleIds)
    }

    // Re-runs both the collections list and the per-collection getMediaFromPath cascade (Room
    // verbietet Main-Thread) and refreshes the repo-level cache so a later dispose/recompose (e.g. a
    // Viewer round trip) can seed from it instead of recomputing from scratch.
    suspend fun reload() {
        if (collections.isEmpty()) isLoading = true else isRefreshing = true
        try {
            val colls = withContext(Dispatchers.IO) { repo.refreshCollectionsCache() }
            collections = colls
            albumItems = toAlbumItems(colls)
            loadError = null
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            loadError = e.message
        } finally {
            isLoading = false
            isRefreshing = false
        }
    }
    // Reload on entry when the cache was never built OR a media mutation happened since it was last
    // built - the latter covers deleting inside a Collection and tabbing straight back here, where
    // the RefreshBus collector below never fires (the triggering event predated this
    // freshly-composed collector), which used to leave the tile count/cover frozen until an app
    // restart. When nothing changed, isCollectionsCacheStale() is false and the cache is served as-is.
    // RefreshBus already coalesces a burst for every collector. collectLatest additionally cancels
    // an obsolete collection cascade when a newer mutation arrives, so the tab stays responsive
    // without adding another visible one-second stale window.
    LaunchedEffect(Unit) {
        if (repo.isCollectionsCacheStale()) reload()
        org.fossify.gallery.helpers.RefreshBus.events.collectLatest {
            // The six built-in collections (favorites + 1–5 stars) derive their membership from
            // metadata, so the normal generation check refreshes them after a heart/rating change.
            if (repo.isCollectionsCacheStale()) reload()
        }
    }

    actionColl?.let { coll ->
        AlertDialog(
            onDismissRequest = { actionColl = null },
            title = { Text(coll.name) },
            text = {
                Column {
                    val pinned = coll.id.toString() in ctx.config.pinnedCollections
                    TextButton(onClick = { val cur = ctx.config.pinnedCollections; ctx.config.pinnedCollections = if (pinned) cur - coll.id.toString() else cur + coll.id.toString(); org.fossify.gallery.helpers.RefreshBus.trigger(pathsChanged = false, collectionsChanged = false); actionColl = null }, modifier = Modifier.fillMaxWidth()) { Text(if (pinned) stringResource(R.string.unpin_from_drawer) else stringResource(R.string.pin_to_drawer)) }
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
                scope.launch { try { withContext(Dispatchers.IO) { repo.deleteCollection(coll) }; reload() } catch (e: Exception) { android.util.Log.e("Collections", "Delete collection failed: ${coll.name}", e); ctx.toast(ctx.getString(R.string.error_generic, e.message), Toast.LENGTH_LONG) } }
            },
            onDismiss = { deleteConfirm = null },
        )
    }

    if (deleteConfirmCollections.isNotEmpty()) {
        val collectionsToDelete = deleteConfirmCollections
        ConfirmDestructive(
            title = stringResource(R.string.delete_collection),
            text = stringResource(R.string.delete_collections_confirm, collectionsToDelete.size),
            confirmLabel = stringResource(org.fossify.commons.R.string.delete),
            onConfirm = {
                deleteConfirmCollections = emptySet()
                selectedCollectionIds = emptySet()
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) { collectionsToDelete.forEach { repo.deleteCollection(it) } }
                        reload()
                    } catch (e: Exception) {
                        android.util.Log.e("Collections", "Bulk delete failed", e)
                        ctx.toast(ctx.getString(R.string.error_generic, e.message), Toast.LENGTH_LONG)
                    }
                }
            },
            onDismiss = { deleteConfirmCollections = emptySet() },
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
        var minSizeMb by remember(editingColl) { mutableStateOf(((editingColl?.minSize ?: 0L) / 1_000_000L).takeIf { it > 0L }?.toString().orEmpty()) }
        var dateRange by remember(editingColl) { mutableIntStateOf(editingColl?.dateRange ?: 0) }
        var typeFilter by remember(editingColl) { mutableIntStateOf(editingColl?.typeFilter ?: 0) }
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
                minSize = minSizeMb.toLongOrNull()?.coerceAtLeast(0L)?.times(1_000_000L) ?: 0L,
                dateRange = dateRange,
                typeFilter = typeFilter,
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

        Dialog(onDismissRequest = { showEditDialog = false }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { showEditDialog = false }) { Icon(Icons.Default.Close, stringResource(R.string.cancel)) }
                        Text(if (editingColl != null) stringResource(R.string.edit_collection) else stringResource(R.string.create_collection), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        TextButton(onClick = { saveCollection() }, enabled = name.isNotBlank()) { Text(stringResource(org.fossify.commons.R.string.save)) }
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
                            IconButton(onClick = { includedUris = includedUris - uri }, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Delete, stringResource(R.string.action_remove), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp)) }
                        }
                    }
                    Surface(onClick = { inclPicker.launch(null) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(Radius.sm), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
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
                            IconButton(onClick = { excludedUris = excludedUris - uri }, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Delete, stringResource(R.string.action_remove), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp)) }
                        }
                    }
                    Surface(onClick = { exclPicker.launch(null) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(Radius.sm), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
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
                                Surface(onClick = { tagFilter = if (hasTag) tagFilter.split(",").filter { it.trim() != t }.joinToString(",") else "${tagFilter},$t".trim(',') }, shape = RoundedCornerShape(Radius.md), color = if (hasTag) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest, contentColor = if (hasTag) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant) {
                                    Text(t, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
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
                    Text(stringResource(R.string.filter_type_label), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            stringResource(R.string.everything) to 0,
                            stringResource(R.string.images) to org.fossify.gallery.helpers.TYPE_IMAGES,
                            stringResource(R.string.videos) to org.fossify.gallery.helpers.TYPE_VIDEOS,
                            stringResource(R.string.gif) to org.fossify.gallery.helpers.TYPE_GIFS,
                            stringResource(R.string.raw) to org.fossify.gallery.helpers.TYPE_RAWS,
                            stringResource(R.string.svg) to org.fossify.gallery.helpers.TYPE_SVGS,
                        ).forEach { (label, value) ->
                            FilterChip(selected = typeFilter == value, onClick = { typeFilter = value }, label = { Text(label) })
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.filter_date_label), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            stringResource(R.string.all_dates) to 0,
                            stringResource(R.string.today) to 1,
                            stringResource(R.string.date_range_last_7_days) to 2,
                            stringResource(R.string.date_range_last_30_days) to 3,
                            stringResource(R.string.date_range_last_year) to 4,
                        ).forEach { (label, value) ->
                            FilterChip(selected = dateRange == value, onClick = { dateRange = value }, label = { Text(label) })
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = minSizeMb, onValueChange = { value -> minSizeMb = value.filter(Char::isDigit) }, label = { Text(stringResource(R.string.collection_min_size_mb)) }, supportingText = { Text(stringResource(R.string.collection_min_size_hint)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    // Search
                    OutlinedTextField(value = searchQuery, onValueChange = { searchQuery = it }, label = { Text(stringResource(R.string.text_search_optional)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (selectedCollectionIds.isNotEmpty()) {
            SelectionAppBar(
                count = selectedCollectionIds.size,
                onClose = { selectedCollectionIds = emptySet() },
                actions = {
                    IconButton(onClick = {
                        deleteConfirmCollections = selectedCollectionIds.mapNotNull { id -> collections.find { it.id.toString() == id } }.toSet()
                    }) {
                        Icon(Icons.Default.Delete, stringResource(R.string.action_delete))
                    }
                    var selectionMenuOpen by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { selectionMenuOpen = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.more_actions)) }
                        androidx.compose.material3.DropdownMenu(
                            expanded = selectionMenuOpen,
                            onDismissRequest = { selectionMenuOpen = false },
                        ) {
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_select_all)) },
                                onClick = {
                                    selectionMenuOpen = false
                                    selectedCollectionIds = selectionForAll(selectedCollectionIds, selectableCollectionIds, invert = false)
                                },
                            )
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_invert_selection)) },
                                onClick = {
                                    selectionMenuOpen = false
                                    selectedCollectionIds = selectionForAll(selectedCollectionIds, selectableCollectionIds, invert = true)
                                },
                            )
                        }
                    }
                },
            )
        } else {
            LibraryScreenHeader(
                title = stringResource(R.string.nav_collections),
                // Do not announce an empty collection count while the skeleton is still loading.
                // It briefly read as a real result ("0 collections") before the actual list arrived.
                subtitle = if (isLoading) {
                    stringResource(R.string.loading_collections)
                } else {
                    stringResource(
                        R.string.collections_summary,
                        collections.size,
                        viewSettingsSummary(viewSettings, includeGrouping = false),
                    ) + " · " + stringResource(R.string.collections_hint)
                },
                leadingIcon = Icons.Default.CollectionsBookmark,
                actions = {
                    if (onOpenViewSettings != null) {
                        IconButton(onClick = onOpenViewSettings) {
                            Icon(Icons.Default.Tune, stringResource(R.string.view_settings_title))
                        }
                    }
                    RefreshActionButton(isRefreshing = isRefreshing, onRefresh = { scope.launch { reload() } })
                    IconButton(onClick = { editingColl = null; showEditDialog = true }) { Icon(Icons.Default.Add, stringResource(R.string.new_collection)) }
                },
            )
        }
        val hasSourceContent = collections.isNotEmpty()
        val hasContent = visibleCollections.isNotEmpty()
        val hasSearch = externalSearchQuery.isNotBlank()
        Column(Modifier.weight(1f)) {
        if (loadError != null && hasSourceContent && !isLoading) {
            LoadErrorBanner(message = loadError, onRetry = { scope.launch { reload() } })
        }
        val contentState = when {
            hasContent -> "content"
            isLoading -> "loading"
            loadError != null -> "error"
            else -> "empty"
        }
        Box(Modifier.weight(1f)) {
            Crossfade(targetState = contentState, animationSpec = AppMotion.short, label = "collectionsContent", modifier = Modifier.fillMaxSize()) { s ->
                when (s) {
                "loading" -> FolderSkeleton(viewSettings = viewSettings)
                "error" -> LoadErrorState(message = loadError, onRetry = { scope.launch { reload() } })
                "empty" -> if (hasSearch) {
                    EmptyState(
                        Icons.Default.SearchOff,
                        stringResource(R.string.no_results),
                        subtitle = stringResource(R.string.no_results_hint),
                        actionLabel = stringResource(R.string.clear_filter),
                        onAction = onClearExternalSearch,
                    )
                } else {
                    EmptyState(Icons.Default.CollectionsBookmark, stringResource(R.string.no_collections), subtitle = stringResource(R.string.tap_to_create_collection))
                }
                else -> {
                    // Apply the sheet's name/count sort (containers have no other sortable field).
                        val sortedItems = remember(visibleAlbumItems, viewSettings.sortBy, viewSettings.sortDesc) {
                            val asc = when (viewSettings.sortBy) {
                            SortField.COUNT -> visibleAlbumItems.sortedBy { it.count }
                            else -> visibleAlbumItems.sortedBy { it.name.lowercase() }
                        }
                        if (viewSettings.sortDesc) asc.reversed() else asc
                    }
                        LibraryAlbumGrid(
                        items = sortedItems,
                        viewSettings = viewSettings,
                        showCollectionStacks = true,
                        onClick = { item ->
                            if (selectedCollectionIds.isNotEmpty()) {
                                selectedCollectionIds = if (item.key in selectedCollectionIds) selectedCollectionIds - item.key else selectedCollectionIds + item.key
                            } else {
                                visibleCollections.find { it.id.toString() == item.key }?.let(onCollectionClick)
                            }
                        },
                        onLongClick = { item ->
                            // Built-in rating/favorites entries are live views, not user-created
                            // records; keep them out of edit/delete selection.
                            if (item.key.toLongOrNull()?.let { it > 0L } == true) {
                                selectedCollectionIds = if (item.key in selectedCollectionIds) selectedCollectionIds - item.key else selectedCollectionIds + item.key
                            }
                        },
                        selectedKeys = selectedCollectionIds,
                        modifier = Modifier.fillMaxSize(),
                        tabIndex = tabIndex,
                    )
                }
                }
            }
            InlineRefreshIndicator(
                visible = isRefreshing,
                contentAvailable = hasContent,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}
}

package org.fossify.gallery.compose.screens
import org.fossify.gallery.compose.theme.Radius

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.gallery.compose.theme.LocalMediaRepository
import org.fossify.gallery.compose.theme.Scrim
import org.fossify.gallery.compose.util.ScrollToTopEffect
import org.fossify.gallery.compose.util.decodeImageAspect
import org.fossify.gallery.compose.util.sharedElementKey
import org.fossify.gallery.compose.components.FolderTile
import org.fossify.gallery.compose.components.EmptyState
import org.fossify.gallery.compose.components.SelectionAppBar
import org.fossify.gallery.compose.components.GalleryImage
import org.fossify.gallery.compose.components.ConfirmDestructive
import org.fossify.gallery.compose.components.RenameDialog
import org.fossify.gallery.extensions.config
import org.fossify.gallery.R
import androidx.compose.ui.res.stringResource
import org.fossify.commons.extensions.toast
import org.fossify.gallery.helpers.UndoAction
import org.fossify.gallery.helpers.UndoManager
import org.fossify.gallery.helpers.UndoType
import org.fossify.gallery.helpers.VIDEO_EXTENSIONS
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Delete
import java.io.File

private data class ExplorerItem(
    val name: String, val path: String, val isDirectory: Boolean,
    val lastModified: Long = 0L, val size: Long = 0L,
    val thumbnailPath: String = "",
    val mediaCount: Int = 0,
    val previewPaths: List<String> = emptyList(),
    // date_sort_key (date_added-preferring) for file items, so the Explorer's DATE sort matches the
    // Media tab's exactly. 0 for folders (they sort by their newest child's mtime instead).
    val sortKey: Long = 0L,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ExplorerScreen(
    internalStoragePath: String,
    modifier: Modifier = Modifier,
    folderSettings: ViewSettings = ViewSettings(),
    mediaSettings: ViewSettings = ViewSettings(),
    onPathChange: (String) -> Unit = {},
    onSelectionActiveChanged: (Boolean) -> Unit = {},
    onCanGoUpChanged: (Boolean) -> Unit = {},
    onNavigateToViewer: (List<String>, Int) -> Unit = { _, _ -> },
    tabIndex: Int? = null,
) {
    val context = LocalContext.current
    val repo = LocalMediaRepository.current
    val scope = rememberCoroutineScope()
    // rememberSaveable (not remember) - the Home destination's whole composition (MainScreen ->
    // ... -> ExplorerScreen) is disposed and recreated by Navigation-Compose when navigating to
    // the Viewer and back, since Viewer is a separate NavHost destination pushed on top. Plain
    // remember state doesn't survive that dispose/recompose cycle and silently resets to a
    // 1-entry stack (even though the visible path, driven by the ViewModel-backed
    // internalStoragePath param, still correctly shows the deep folder) - which made the back
    // gesture immediately fall through to the tab-switch fallback instead of going up a level,
    // any time after having viewed at least one image while browsing a subfolder.
    val navStack = rememberSaveable(saver = listSaver<SnapshotStateList<String>, String>(save = { it.toList() }, restore = { it.toMutableStateList() })) { mutableStateListOf(internalStoragePath) }
    var currentPath by remember { mutableStateOf(internalStoragePath) }
    var folderItems by remember { mutableStateOf<List<ExplorerItem>>(emptyList()) }
    var fileItems by remember { mutableStateOf<List<ExplorerItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedFolderPaths by remember { mutableStateOf<Set<String>>(emptySet()) }
    val hasFolderSelection = selectedFolderPaths.isNotEmpty()
    // Mirrors selectedFolderPaths, but for the file grid below - previously that grid had no
    // long-press/selection support at all (plain .clickable straight to the Viewer), unlike every
    // other media grid in the app (MediaScreen/AlbumsScreen/FavoritesScreen). Kept mutually exclusive
    // with folder selection (only one selection mode active at a time) to avoid an ambiguous top bar.
    var selectedFilePaths by remember { mutableStateOf<Set<String>>(emptySet()) }
    val hasFileSelection = selectedFilePaths.isNotEmpty()
    LaunchedEffect(hasFolderSelection, hasFileSelection) { onSelectionActiveChanged(hasFolderSelection || hasFileSelection) }
    var showFileFolderPicker by remember { mutableStateOf(false) }
    var fileFolderPickerIsMove by remember { mutableStateOf(false) }
    var showFileDeleteConfirm by remember { mutableStateOf(false) }
    var showFileRenameDialog by remember { mutableStateOf(false) }
    // Set once "Use as mover source" is tapped (can be several folders at once) - the destination
    // picker (search + Explorer browse, see FolderPathPickerSheet) then decides the pair(s)' shared
    // other half, and one FolderPair per source gets created against it.
    var pendingMoverSources by remember { mutableStateOf<List<String>?>(null) }
    // Re-read every time a selection session starts/ends (not just once for the whole tab visit,
    // and not left stale from the previous session) - mirrors FolderPathPickerSheet's own "already a
    // mover source" marker (same tertiary-tinted DriveFileMove icon), just applied to the selection
    // toolbar's action icon instead of a per-row badge.
    val moverSourcePaths = remember(hasFolderSelection) { org.fossify.gallery.helpers.loadMoverPairs(context).map { it.source }.toSet() }
    // SideEffect (synchronous, runs on the same composition pass), not LaunchedEffect (dispatches
    // a new coroutine, which can lag a frame or more behind) - MainScreen's own BackHandler reads
    // this via onCanGoUpChanged to decide whether it or ExplorerScreen's BackHandler below should
    // win, so any lag here reopens the race where a back-press right after navigating into a
    // folder sees stale (false) state and incorrectly falls through to the tab-switch behavior.
    SideEffect { onCanGoUpChanged(navStack.size > 1) }

    BackHandler(enabled = navStack.size > 1) {
        navStack.removeLastOrNull()
        currentPath = navStack.lastOrNull() ?: internalStoragePath
    }
    BackHandler(enabled = hasFolderSelection) { selectedFolderPaths = emptySet() }
    BackHandler(enabled = hasFileSelection) { selectedFilePaths = emptySet() }

    LaunchedEffect(internalStoragePath) {
        if (internalStoragePath != currentPath) {
            navStack.clear()
            navStack.add(internalStoragePath)
            currentPath = internalStoragePath
        }
    }

    val folderCardColor = when (folderSettings.displayMode) {
        // Not colorScheme.surface - this app's custom ColorScheme sets background == surface, so a
        // folder tile with no thumbnail (empty folder) would be visually indistinguishable from the
        // screen behind it, leaving no indication a tappable card is even there.
        DisplayMode.COMPACT, DisplayMode.NORMAL -> MaterialTheme.colorScheme.surfaceContainerHigh
        DisplayMode.DARK -> MaterialTheme.colorScheme.surfaceVariant
    }

    // Query MediaStore once for every entry under the storage root, then derive each folder view by
    // filtering this cache in memory. Avoids a slow MediaStore round-trip on every navigation.
    val storageRoot = remember { android.os.Environment.getExternalStorageDirectory().absolutePath }
    // Seed from the process-level cache (see MediaStoreOps) instead of always starting at null - a
    // Viewer round-trip disposes and recreates this whole composable, and without this the cache
    // reset forced a full-device MediaStore re-query every time the user came back from viewing an
    // image.
    var allEntries by remember { mutableStateOf(org.fossify.gallery.helpers.MediaStoreOps.cachedEntriesUnder(storageRoot)) }
    LaunchedEffect(Unit) {
        if (allEntries == null) {
            allEntries = withContext(Dispatchers.IO) { org.fossify.gallery.helpers.MediaStoreOps.refreshEntriesUnder(context, storageRoot) }
        }
    }
    // Re-fetch after rename/move/mover/delete etc. so the tree doesn't keep showing files/folders
    // that no longer exist at their old path. isLoading is intentionally left untouched below (see
    // the loadedPath guard) so this refresh happens quietly instead of flashing the skeleton loader.
    LaunchedEffect(Unit) {
        org.fossify.gallery.helpers.RefreshBus.events.collect {
            allEntries = withContext(Dispatchers.IO) { org.fossify.gallery.helpers.MediaStoreOps.refreshEntriesUnder(context, storageRoot) }
        }
    }

    suspend fun loadFolderContents(path: String) {
        val (sortedFolders, sortedFiles) = withContext(Dispatchers.IO) {
            val root = path.trimEnd('/')
            val deletedPaths = repo.getDeletedPaths()
            val hidden = context.config.explorer2HiddenFolders
            // Reconstruct the folder tree from MediaStore (raw directory listing is blocked under
            // scoped storage). Subfolders are derived from the media paths beneath the current path.
            val cache = allEntries
            val entries = if (cache != null && root.startsWith(storageRoot))
                cache.filter { it.path.startsWith("$root/") }
            else org.fossify.gallery.helpers.MediaStoreOps.mediaEntriesUnder(context, root)
            // Subfolder tiles (structure + counts + thumbnails) are still derived from the MediaStore
            // entry list - a recursive prefix walk that's fast there and cached; a DB-backed variant of
            // this whole-subtree scan measured slower on a large library (see MediaStoreOps doc).
            class Agg { var thumb: String = ""; var newestAdded: Long = -1L; var lastModified: Long = 0L; var count: Int = 0; val previews: MutableList<String> = mutableListOf() }
            val folderMap = LinkedHashMap<String, Agg>()
            for (e in entries) {
                if (e.path in deletedPaths) continue
                val rel = e.path.removePrefix("$root/")
                val slash = rel.indexOf('/')
                if (slash < 0) continue // direct files come from the DB below, not from here
                val seg = rel.substring(0, slash)
                val folderPath = "$root/$seg"
                if (folderPath in hidden) continue
                val agg = folderMap.getOrPut(folderPath) { Agg() }
                // Folder thumbnail = the newest media in it (by date_added, the same "newest" the media
                // lists sort by), not just the first one MediaStore happened to return.
                if (e.dateAdded > agg.newestAdded) { agg.newestAdded = e.dateAdded; agg.thumb = e.path }
                if (agg.previews.size < 4) agg.previews.add(e.path)
                agg.count++
                if (e.modified > agg.lastModified) agg.lastModified = e.modified
            }
            val folders = folderMap.map { (fp, agg) ->
                ExplorerItem(name = fp.substringAfterLast('/'), path = fp, isDirectory = true, lastModified = agg.lastModified, thumbnailPath = if (folderSettings.showFolderThumbnails) agg.thumb else "", mediaCount = agg.count, previewPaths = agg.previews.toList())
            }
            // Direct media files of this folder come from the SAME `media` DB table the Media tab pages
            // over (getMediaFromPath = WHERE deleted_ts = 0 AND parent_path = root, parent_path-indexed),
            // carrying date_sort_key so the DATE sort below is identical to the Media tab's. This is what
            // makes "same media, same order across tabs" hold for the actual media items.
            val files = repo.getMediaFromPath(root).map {
                ExplorerItem(name = it.name, path = it.path, isDirectory = false, lastModified = it.modified, size = it.size, sortKey = if (it.dateSortKey > 0) it.dateSortKey else if (it.taken > 0) it.taken else it.modified)
            }
            val sf = when (folderSettings.sortBy) {
                SortField.NAME -> folders.sortedBy { it.name.lowercase() }
                SortField.DATE -> folders.sortedBy { it.lastModified }
                SortField.SIZE -> folders.sortedBy { it.size }
                SortField.RATING -> folders.sortedBy { it.name.lowercase() }
                SortField.COUNT -> folders.sortedBy { it.mediaCount }
            }.let { if (folderSettings.sortDesc) it.reversed() else it }
            // COUNT (file count) is a folder-only sort option, filtered out of the media sort UI
            // (see ViewSettingsSheet) - falls back to name here purely so this `when` stays exhaustive.
            val sfi = when (mediaSettings.sortBy) {
                SortField.NAME -> files.sortedBy { it.name.lowercase() }
                SortField.DATE -> files.sortedBy { it.sortKey }
                SortField.SIZE -> files.sortedBy { it.size }
                SortField.RATING -> files.sortedBy { it.name.lowercase() }
                SortField.COUNT -> files.sortedBy { it.name.lowercase() }
            }.let { if (mediaSettings.sortDesc) it.reversed() else it }
            Pair(sf, sfi)
        }
        folderItems = sortedFolders
        fileItems = sortedFiles
    }

    // Tracks which path the currently-shown folderItems/fileItems belong to, so that a silent
    // background refresh (RefreshBus, same currentPath) can update the list in place instead of
    // forcing the full-screen skeleton loader back up.
    var loadedPath by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(currentPath, allEntries, folderSettings.sortBy, folderSettings.sortDesc, mediaSettings.sortBy, mediaSettings.sortDesc) {
        if (currentPath.startsWith(storageRoot) && allEntries == null) { isLoading = true; return@LaunchedEffect }
        if (loadedPath != currentPath) isLoading = true
        loadFolderContents(currentPath)
        loadedPath = currentPath
        isLoading = false
    }

    LaunchedEffect(currentPath) { onPathChange(currentPath); selectedFolderPaths = emptySet(); selectedFilePaths = emptySet() }

    Column(modifier = modifier.fillMaxSize()) {
        if (hasFolderSelection) {
            SelectionAppBar(
                count = selectedFolderPaths.size,
                onClose = { selectedFolderPaths = emptySet() },
                actions = {
                    IconButton(onClick = { selectedFolderPaths.firstOrNull()?.let { p -> navStack.add(p); currentPath = p }; selectedFolderPaths = emptySet() }) {
                        Icon(Icons.Default.Folder, stringResource(R.string.action_open))
                    }
                    IconButton(onClick = {
                        selectedFolderPaths.forEach { p -> context.config.hideExplorer2Folder(p) }
                        scope.launch { loadFolderContents(currentPath) }
                        selectedFolderPaths = emptySet()
                    }) {
                        Icon(Icons.Default.VisibilityOff, stringResource(R.string.action_hide))
                    }
                    val allAlreadyMoverSources = selectedFolderPaths.isNotEmpty() && selectedFolderPaths.all { it in moverSourcePaths }
                    IconButton(onClick = {
                        pendingMoverSources = selectedFolderPaths.toList()
                        selectedFolderPaths = emptySet()
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.DriveFileMove,
                            stringResource(if (allAlreadyMoverSources) R.string.mover_source_marker else R.string.action_use_as_mover_source),
                            // error/red instead of tertiary - tertiary read as too close to the
                            // toolbar's other icons to notice at a glance, and this is a state the
                            // user needs to actually see before tapping (already-configured vs. new).
                            tint = if (allAlreadyMoverSources) MaterialTheme.colorScheme.error else LocalContentColor.current,
                        )
                    }
                },
            )
        } else if (hasFileSelection) {
            SelectionAppBar(
                count = selectedFilePaths.size,
                onClose = { selectedFilePaths = emptySet() },
                actions = {
                    IconButton(onClick = { showFileRenameDialog = true }) {
                        Icon(Icons.Default.DriveFileRenameOutline, stringResource(R.string.action_rename))
                    }
                    IconButton(onClick = { fileFolderPickerIsMove = false; showFileFolderPicker = true }) {
                        Icon(Icons.Default.ContentCopy, stringResource(R.string.action_copy))
                    }
                    IconButton(onClick = { fileFolderPickerIsMove = true; showFileFolderPicker = true }) {
                        Icon(Icons.AutoMirrored.Filled.DriveFileMove, stringResource(R.string.action_move))
                    }
                    IconButton(onClick = {
                        if (context.config.skipDeleteConfirmation) {
                            val d = selectedFilePaths
                            scope.launch(Dispatchers.IO) { repo.moveToRecycleBinBatch(d) }
                            UndoManager.push(UndoAction(paths = d, type = UndoType.DELETE))
                            selectedFilePaths = emptySet()
                        } else {
                            showFileDeleteConfirm = true
                        }
                    }) {
                        Icon(Icons.Default.Delete, stringResource(org.fossify.commons.R.string.delete))
                    }
                },
            )
        } else {
            // Breadcrumb navigation bar
            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)).padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                if (navStack.size > 1) {
                    IconButton(onClick = { navStack.removeLastOrNull(); currentPath = navStack.lastOrNull() ?: internalStoragePath }, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    }
                } else {
                    Spacer(Modifier.width(8.dp))
                }
                Text(currentPath, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(end = 8.dp))
            }
        }

        // Crossfade instead of an instant swap - see MediaScreen's paged content for why.
        val explorerContentState = if (isLoading) "loading" else if (folderItems.isEmpty() && fileItems.isEmpty()) "empty" else "content"
        Crossfade(targetState = explorerContentState, label = "explorerContent") { cs ->
        if (cs == "loading") {
            Box(Modifier.fillMaxSize()) {
                MediaSkeleton(columns = 3)
                // The first Explorer visit runs an uncached full-device MediaStore scan (multiple
                // seconds on a large real library, see MediaStoreOps.kt) with nothing but a bare
                // shimmer to look at - easy to mistake for a hang. Delayed so it doesn't flash on
                // the common fast path (cached allEntries, near-instant folder navigation).
                var showHint by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { delay(1200); showHint = true }
                androidx.compose.animation.AnimatedVisibility(visible = showHint, modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp), enter = fadeIn(), exit = fadeOut()) {
                    Surface(shape = RoundedCornerShape(Radius.xl), color = MaterialTheme.colorScheme.surfaceContainerHigh, shadowElevation = 2.dp) {
                        Text(
                            stringResource(R.string.scanning_folders), style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            // A screen-reader user gets nothing but a bare shimmer otherwise - a new
                            // Text node appearing off-focus isn't announced by TalkBack on its own.
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                }
            }
        } else if (cs == "empty") {
            EmptyState(Icons.Default.Folder, stringResource(R.string.no_items), subtitle = stringResource(R.string.no_items_hint))
        } else {
            val listState = rememberLazyListState()
            ScrollToTopEffect(tabIndex) { listState.animateScrollToItem(0) }
            LazyColumn(state = listState, modifier = Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) {
                if (folderItems.isNotEmpty()) {
                    item {
                        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.albums), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.weight(1f))
                            Text("${folderItems.size}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                        }
                    }
                    if (folderSettings.viewType == ViewType.GRID) {
                        folderItems.chunked(folderSettings.columnCount).forEach { chunk ->
                            item(key = chunk.joinToString { it.path }) {
                                Row(Modifier.fillMaxWidth().padding(folderSettings.spacing.dp / 2)) {
                                    chunk.forEach { item ->
                                        Box(Modifier.weight(1f).padding(folderSettings.spacing.dp / 2).combinedClickable(
                                            onClick = { if (hasFileSelection) Unit else if (hasFolderSelection) selectedFolderPaths = if (item.path in selectedFolderPaths) selectedFolderPaths - item.path else selectedFolderPaths + item.path else { navStack.add(item.path); currentPath = item.path } },
                                            onLongClick = { if (!hasFileSelection) { selectedFilePaths = emptySet(); selectedFolderPaths = selectedFolderPaths + item.path } }
                                        )) {
                                            FolderTile(
                                                name = item.name,
                                                thumbnailPath = item.thumbnailPath,
                                                showThumbnail = folderSettings.showFolderThumbnails,
                                                roundedCorners = folderSettings.roundedCorners,
                                                containerColor = folderCardColor,
                                                subtitle = if (item.mediaCount > 0) stringResource(R.string.media_count, item.mediaCount) else null,
                                            )
                                            if (hasFolderSelection) {
                                                if (item.path in selectedFolderPaths) {
                                                    Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)))
                                                }
                                                Box(Modifier.align(Alignment.TopStart).padding(4.dp).size(24.dp), contentAlignment = Alignment.Center) {
                                                    if (item.path in selectedFolderPaths) {
                                                        Box(Modifier.size(18.dp).background(Color.White, CircleShape))
                                                        Icon(Icons.Default.CheckCircle, stringResource(R.string.cd_selected), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                                    } else {
                                                        Box(Modifier.matchParentSize().background(Scrim.a20, CircleShape))
                                                        Icon(Icons.Default.RadioButtonUnchecked, stringResource(R.string.cd_not_selected), tint = Color.White, modifier = Modifier.size(22.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    repeat(folderSettings.columnCount - chunk.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    } else {
                        items(folderItems, key = { it.path }) { item ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).combinedClickable(
                                    onClick = { if (hasFileSelection) Unit else if (hasFolderSelection) selectedFolderPaths = if (item.path in selectedFolderPaths) selectedFolderPaths - item.path else selectedFolderPaths + item.path else { navStack.add(item.path); currentPath = item.path } },
                                    onLongClick = { if (!hasFileSelection) { selectedFilePaths = emptySet(); selectedFolderPaths = selectedFolderPaths + item.path } }
                                ),
                                shape = RoundedCornerShape(Radius.md),
                                colors = CardDefaults.cardColors(containerColor = if (item.path in selectedFolderPaths) MaterialTheme.colorScheme.primaryContainer else folderCardColor)
                            ) {
                                Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(item.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (folderSettings.displayMode == DisplayMode.DARK) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                                        Text(stringResource(R.string.media_count, item.mediaCount), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        if (item.previewPaths.isEmpty()) {
                                            Box(Modifier.size(44.dp).clip(RoundedCornerShape(Radius.sm)).background(MaterialTheme.colorScheme.surfaceVariant))
                                        } else {
                                            item.previewPaths.take(3).forEach { p ->
                                                Box(Modifier.size(44.dp).clip(RoundedCornerShape(Radius.sm))) {
                                                    GalleryImage(path = p, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop, placeholderIconSize = 12.dp, thumbnailSize = 128)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (fileItems.isNotEmpty()) {
                    item {
                        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.media), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.weight(1f))
                            Text("${fileItems.size}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                        }
                    }
                    val cornerShape = if (mediaSettings.roundedCorners) RoundedCornerShape(Radius.sm) else RoundedCornerShape(0.dp)
                    when (mediaSettings.viewType) {
                    ViewType.GRID -> {
                        fileItems.chunked(mediaSettings.columnCount).forEach { chunk ->
                            item(key = chunk.joinToString { it.path }) {
                                Row(Modifier.fillMaxWidth().padding(mediaSettings.spacing.dp / 2)) {
                                    chunk.forEach { item ->
                                        val file = File(item.path)
                                        val isVideo = item.path.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS
                                        val mediaBg = when (mediaSettings.displayMode) { DisplayMode.DARK -> MaterialTheme.colorScheme.surfaceVariant else -> MaterialTheme.colorScheme.surface }
                                        Box(Modifier.weight(1f).padding(mediaSettings.spacing.dp / 2).background(mediaBg, cornerShape).combinedClickable(
                                            onClick = {
                                                if (hasFolderSelection) Unit
                                                else if (hasFileSelection) selectedFilePaths = if (item.path in selectedFilePaths) selectedFilePaths - item.path else selectedFilePaths + item.path
                                                else onNavigateToViewer(fileItems.map { it.path }, fileItems.indexOfFirst { it.path == item.path }.coerceAtLeast(0))
                                            },
                                            onLongClick = { if (!hasFolderSelection) { selectedFolderPaths = emptySet(); selectedFilePaths = selectedFilePaths + item.path } }
                                        )) {
                                            Column {
                                                Box(Modifier.aspectRatio(1f).clip(cornerShape)) {
                                                    if (isVideo) VideoThumbnail(videoPath = item.path, modifier = Modifier.fillMaxSize().sharedElementKey("media_${item.path}"), contentScale = ContentScale.Crop)
                                                    else GalleryImage(path = item.path, contentDescription = item.name, modifier = Modifier.fillMaxSize().sharedElementKey("media_${item.path}"), contentScale = ContentScale.Crop, placeholderIconSize = 20.dp)
                                                }
                                                if (mediaSettings.showFileNames) {
                                                    Text(item.name, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                                                }
                                            }
                                            if (hasFileSelection) {
                                                if (item.path in selectedFilePaths) {
                                                    Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)))
                                                }
                                                Box(Modifier.align(Alignment.TopStart).padding(4.dp).size(24.dp), contentAlignment = Alignment.Center) {
                                                    if (item.path in selectedFilePaths) {
                                                        Box(Modifier.size(18.dp).background(Color.White, CircleShape))
                                                        Icon(Icons.Default.CheckCircle, stringResource(R.string.cd_selected), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                                    } else {
                                                        Box(Modifier.matchParentSize().background(Scrim.a20, CircleShape))
                                                        Icon(Icons.Default.RadioButtonUnchecked, stringResource(R.string.cd_not_selected), tint = Color.White, modifier = Modifier.size(22.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    repeat(mediaSettings.columnCount - chunk.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                    ViewType.MOSAIC -> {
                        item(key = "explorer_media_mosaic") {
                            val aspectCache = remember { mutableStateMapOf<String, Float>() }
                            val n = mediaSettings.columnCount.coerceAtLeast(1)
                            val columns = remember(fileItems, n, aspectCache.size) {
                                val heights = FloatArray(n)
                                val buckets = Array(n) { mutableListOf<ExplorerItem>() }
                                fileItems.forEach { fi ->
                                    val isVid = fi.path.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS
                                    val ar = if (isVid) 1f else (aspectCache[fi.path] ?: 1f)
                                    val ci = (0 until n).minByOrNull { heights[it] } ?: 0
                                    buckets[ci].add(fi); heights[ci] += 1f / ar.coerceIn(0.3f, 3f)
                                }
                                buckets.map { it.toList() }
                            }
                            Row(Modifier.fillMaxWidth().padding(mediaSettings.spacing.dp / 2)) {
                                columns.forEach { col ->
                                    Column(Modifier.weight(1f)) {
                                        col.forEach { item ->
                                            val isVideo = item.path.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS
                                            if (!isVideo) LaunchedEffect(item.path) {
                                                if (aspectCache[item.path] == null) aspectCache[item.path] = withContext(Dispatchers.IO) { decodeImageAspect(item.path) }
                                            }
                                            val ar = (if (isVideo) 1f else (aspectCache[item.path] ?: 1f)).coerceIn(0.3f, 3f)
                                            Box(Modifier.padding(mediaSettings.spacing.dp / 2).aspectRatio(ar).clip(cornerShape).background(MaterialTheme.colorScheme.surfaceVariant, cornerShape).combinedClickable(
                                                onClick = {
                                                    if (hasFolderSelection) Unit
                                                    else if (hasFileSelection) selectedFilePaths = if (item.path in selectedFilePaths) selectedFilePaths - item.path else selectedFilePaths + item.path
                                                    else onNavigateToViewer(fileItems.map { it.path }, fileItems.indexOfFirst { it.path == item.path }.coerceAtLeast(0))
                                                },
                                                onLongClick = { if (!hasFolderSelection) { selectedFolderPaths = emptySet(); selectedFilePaths = selectedFilePaths + item.path } }
                                            )) {
                                                if (isVideo) VideoThumbnail(videoPath = item.path, modifier = Modifier.fillMaxSize().sharedElementKey("media_${item.path}"), contentScale = ContentScale.Crop)
                                                else GalleryImage(path = item.path, contentDescription = item.name, modifier = Modifier.fillMaxSize().sharedElementKey("media_${item.path}"), contentScale = ContentScale.Crop, placeholderIconSize = 20.dp)
                                                if (hasFileSelection) {
                                                    if (item.path in selectedFilePaths) {
                                                        Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)))
                                                    }
                                                    Box(Modifier.align(Alignment.TopStart).padding(4.dp).size(24.dp), contentAlignment = Alignment.Center) {
                                                        if (item.path in selectedFilePaths) {
                                                            Box(Modifier.size(18.dp).background(Color.White, CircleShape))
                                                            Icon(Icons.Default.CheckCircle, stringResource(R.string.cd_selected), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                                        } else {
                                                            Box(Modifier.matchParentSize().background(Scrim.a20, CircleShape))
                                                            Icon(Icons.Default.RadioButtonUnchecked, stringResource(R.string.cd_not_selected), tint = Color.White, modifier = Modifier.size(22.dp))
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        items(fileItems, key = { it.path }) { item ->
                            val file = File(item.path)
                                val isVideo = item.path.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS
                            Surface(
                                Modifier.fillMaxWidth().combinedClickable(
                                    onClick = {
                                        if (hasFolderSelection) Unit
                                        else if (hasFileSelection) selectedFilePaths = if (item.path in selectedFilePaths) selectedFilePaths - item.path else selectedFilePaths + item.path
                                        else onNavigateToViewer(fileItems.map { it.path }, fileItems.indexOfFirst { it.path == item.path }.coerceAtLeast(0))
                                    },
                                    onLongClick = { if (!hasFolderSelection) { selectedFolderPaths = emptySet(); selectedFilePaths = selectedFilePaths + item.path } }
                                ),
                                color = if (item.path in selectedFilePaths) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            ) {
                                Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(Radius.sm))) {
                                        if (isVideo) VideoThumbnail(videoPath = item.path, modifier = Modifier.fillMaxSize().sharedElementKey("media_${item.path}"), contentScale = ContentScale.Crop)
                                        else GalleryImage(path = item.path, contentDescription = item.name, modifier = Modifier.fillMaxSize().sharedElementKey("media_${item.path}"), contentScale = ContentScale.Crop, placeholderIconSize = 16.dp)
                                        if (hasFileSelection) {
                                            Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                                                if (item.path in selectedFilePaths) {
                                                    Box(Modifier.size(16.dp).background(Color.White, CircleShape))
                                                    Icon(Icons.Default.CheckCircle, stringResource(R.string.cd_selected), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                                } else {
                                                    Box(Modifier.matchParentSize().background(Scrim.a20))
                                                    Icon(Icons.Default.RadioButtonUnchecked, stringResource(R.string.cd_not_selected), tint = Color.White, modifier = Modifier.size(18.dp))
                                                }
                                            }
                                        }
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(item.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(formatFileSize(item.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                            HorizontalDivider(Modifier.padding(start = 68.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        }
                    }
                    }
                }
            }
        }
        }

    }

    if (showFileFolderPicker) {
        val batch = selectedFilePaths.toList()
        FolderPickerSheet(
            isMoveOperation = fileFolderPickerIsMove,
            sourcePaths = batch,
            onDismiss = { showFileFolderPicker = false; selectedFilePaths = emptySet() },
        )
    }
    if (showFileRenameDialog) {
        val batch = selectedFilePaths.toList()
        RenameDialog(
            paths = batch,
            onDismiss = { showFileRenameDialog = false; selectedFilePaths = emptySet() },
        )
    }
    if (showFileDeleteConfirm) {
        val itemsCnt = selectedFilePaths.size
        val itemsText = context.resources.getQuantityString(org.fossify.commons.R.plurals.delete_items, itemsCnt, itemsCnt)
        val question = context.getString(if (context.config.useRecycleBin) org.fossify.commons.R.string.move_to_recycle_bin_confirmation else org.fossify.commons.R.string.deletion_confirmation, itemsText)
        ConfirmDestructive(
            title = stringResource(org.fossify.commons.R.string.delete),
            text = question,
            confirmLabel = stringResource(org.fossify.commons.R.string.delete),
            onConfirm = {
                showFileDeleteConfirm = false
                val d = selectedFilePaths
                scope.launch(Dispatchers.IO) { repo.moveToRecycleBinBatch(d) }
                UndoManager.push(UndoAction(paths = d, type = UndoType.DELETE))
                selectedFilePaths = emptySet()
            },
            onDismiss = { showFileDeleteConfirm = false },
        )
    }

    pendingMoverSources?.let { sources ->
        val addedFormat = stringResource(R.string.mover_pair_added)
        val addedCountFormat = stringResource(R.string.mover_pairs_added_count)
        FolderPathPickerSheet(
            title = stringResource(R.string.mover_select_dest),
            initialPath = context.config.lastExplorerPath.ifBlank { context.config.internalStoragePath },
            onPathSelected = { destination ->
                org.fossify.gallery.helpers.addMoverPairs(context, sources, destination)
                val msg = if (sources.size == 1) addedFormat.format(File(sources[0]).name, File(destination).name) else addedCountFormat.format(sources.size, File(destination).name)
                context.toast(msg, android.widget.Toast.LENGTH_SHORT)
            },
            onDismiss = { pendingMoverSources = null },
            suggestedFolderName = if (sources.size == 1) File(sources[0]).name else null,
        )
    }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1_000_000_000.0)
    bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000 -> "%.0f KB".format(bytes / 1_000.0)
    else -> "$bytes B"
}

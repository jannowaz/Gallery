package org.fossify.gallery.compose.screens
import androidx.compose.ui.res.stringResource
import org.fossify.gallery.R
import org.fossify.gallery.compose.theme.Radius

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fossify.gallery.compose.theme.LocalMediaRepository
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.loadMoverPairs
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

private data class BrowseFolderItem(val name: String, val path: String, val mediaCount: Int = 0, val lastModified: Long = 0L)

/** Order of the Explorer-style browse list below - independent of the search results list, which is
 * always ranked by match quality. Persisted (see [BROWSE_SORT_PREF_KEY]) so a choice made while
 * picking one mover pair's folders still applies the next time this sheet is opened. */
private enum class BrowseSortMode { DATE, COUNT }

private const val BROWSE_SORT_PREF_KEY = "mover_picker_sort_mode"

/**
 * Folder picker combining a text search (over the already-indexed folder DB, instant) with an
 * Explorer-style browse-by-tapping-into-subfolders view, mirroring [FolderPickerSheet]'s UI - but
 * this one only ever hands back a chosen path via [onPathSelected], it never moves/copies anything
 * itself. Used wherever a plain folder path needs picking (Mover pair source/destination) rather
 * than an actual file operation destination.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderPathPickerSheet(
    title: String,
    initialPath: String,
    onPathSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    // Non-null only when this sheet is picking a mover pair's *destination* and the source side is
    // already known - offers a one-tap "create a folder named like the source, right here" action
    // (see the Surface below the browse list) instead of making the user create-then-select by hand.
    suggestedFolderName: String? = null,
    // When true, picking a mover pair's *source* side can gather several folders in one pass (e.g.
    // several Instagram-download subfolders that should all move to the same destination) instead of
    // forcing one round trip through this sheet per folder. Browse rows and search results each get
    // a checkbox; [onPathSelected] is never called in this mode, only [onPathsSelected] (via the
    // "N selected" confirm bar) once the user is done picking.
    multiSelect: Boolean = false,
    initialSelectedPaths: List<String> = emptyList(),
    onPathsSelected: (List<String>) -> Unit = {},
) {
    val ctx = LocalContext.current
    val repo = LocalMediaRepository.current
    val conf = ctx.config
    val defPrefs = remember { android.preference.PreferenceManager.getDefaultSharedPreferences(ctx) }
    val rootPath = conf.internalStoragePath.ifBlank { android.os.Environment.getExternalStorageDirectory().absolutePath }
    val startPath = initialPath.takeIf { it.isNotBlank() && runCatching { Files.isDirectory(Paths.get(it)) }.getOrDefault(false) } ?: rootPath
    var currentPath by remember { mutableStateOf(startPath) }
    var folders by remember { mutableStateOf<List<BrowseFolderItem>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<BrowseFolderItem>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var pendingCreateFolder by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var sortMode by remember {
        mutableStateOf(runCatching { BrowseSortMode.valueOf(defPrefs.getString(BROWSE_SORT_PREF_KEY, BrowseSortMode.DATE.name)!!) }.getOrDefault(BrowseSortMode.DATE))
    }
    var showSortMenu by remember { mutableStateOf(false) }
    // Sources already used by an existing mover pair - surfaced as a small marker on that folder's
    // row below, so re-picking the same source (or shadowing it with a second pair) is a deliberate
    // choice rather than an accident while browsing.
    val moverSourcePaths = remember { loadMoverPairs(ctx).map { it.source }.toSet() }
    val selectedPaths = remember { mutableStateListOf<String>().apply { addAll(initialSelectedPaths) } }

    fun toggleSelected(path: String) {
        if (path in selectedPaths) selectedPaths.remove(path) else selectedPaths.add(path)
    }

    fun setSortMode(mode: BrowseSortMode) {
        sortMode = mode
        defPrefs.edit().putString(BROWSE_SORT_PREF_KEY, mode.name).apply()
    }

    suspend fun loadFolders(path: String): List<BrowseFolderItem> = withContext(Dispatchers.IO) {
        val dir = Paths.get(path)
        if (!Files.isDirectory(dir)) return@withContext emptyList()
        val result = mutableListOf<BrowseFolderItem>()
        try {
            Files.newDirectoryStream(dir).use { stream ->
                for (entry in stream) {
                    val name = entry.fileName.toString()
                    if (name.startsWith(".")) continue
                    if (Files.isDirectory(entry)) {
                        val fPath = entry.toString()
                        val mc = try { Files.newDirectoryStream(entry).use { s -> s.count { !it.fileName.toString().startsWith(".") } } } catch (_: Exception) { 0 }
                        val lm = try { Files.getLastModifiedTime(entry).toMillis() } catch (_: Exception) { 0L }
                        result.add(BrowseFolderItem(name = name, path = fPath, mediaCount = mc, lastModified = lm))
                    }
                }
            }
        } catch (_: Exception) { }
        when (sortMode) {
            BrowseSortMode.DATE -> result.sortedByDescending { it.lastModified }
            BrowseSortMode.COUNT -> result.sortedByDescending { it.mediaCount }
        }
    }

    suspend fun searchFolders(query: String): List<BrowseFolderItem> = withContext(Dispatchers.IO) {
        if (query.length < 2) return@withContext emptyList()
        val qParts = query.lowercase().split(" ").filter { it.isNotBlank() }
        if (qParts.isEmpty()) return@withContext emptyList()
        val dirs = repo.getAllDirectories()
        // directories.media_count is direct children only, so a shallow parent folder with little of
        // its own but huge subfolders would otherwise rank low - sum it plus every descendant's count
        // so the folders that actually hold the most media (recursively) surface first.
        fun recursiveCount(path: String): Int {
            val prefix = path.trimEnd('/') + "/"
            return dirs.sumOf { if (it.path == path || it.path.startsWith(prefix)) it.mediaCnt else 0 }
        }
        val results = mutableListOf<BrowseFolderItem>()
        for (d in dirs) {
            val lowerPath = d.path.lowercase()
            if (qParts.any { lowerPath.indexOf(it) < 0 }) continue
            results.add(BrowseFolderItem(name = d.name, path = d.path, mediaCount = recursiveCount(d.path)))
        }
        results.sortedByDescending { it.mediaCount }.take(80)
    }

    LaunchedEffect(currentPath, sortMode) { folders = loadFolders(currentPath) }

    LaunchedEffect(searchQuery) {
        if (searchQuery.length < 2) { searchResults = emptyList(); return@LaunchedEffect }
        isSearching = true
        kotlinx.coroutines.delay(250)
        searchResults = searchFolders(searchQuery)
        isSearching = false
    }

    val isSearchingMode = searchQuery.length >= 2

    fun choose(path: String) {
        onPathSelected(path)
        onDismiss()
    }

    fun confirmMultiSelection() {
        onPathsSelected(selectedPaths.toList())
        onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        // skipPartiallyExpanded - opens straight into the full/expanded state instead of resting
        // at a partial peek height that needed a drag-up before the folder list was even visible.
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        // Capped to a fraction of screen height instead of a flat 480dp - on short/landscape
        // screens a fixed 480dp could push the confirm button below the visible area.
        val screenHeightDp = LocalConfiguration.current.screenHeightDp
        val sheetHeight = minOf(480, (screenHeightDp * 0.85f).toInt()).dp
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(sheetHeight)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            }
            if (multiSelect) {
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (selectedPaths.isEmpty()) stringResource(R.string.mover_multi_select_hint) else stringResource(R.string.mover_n_selected, selectedPaths.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (selectedPaths.isNotEmpty()) {
                        TextButton(onClick = { confirmMultiSelection() }) { Text(stringResource(R.string.action_done)) }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(stringResource(R.string.search_folders_hint)) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, stringResource(R.string.cd_search), modifier = Modifier.size(20.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.Close, stringResource(R.string.action_empty), modifier = Modifier.size(16.dp))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyMedium,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                ),
                shape = RoundedCornerShape(Radius.md),
            )
            Spacer(Modifier.height(4.dp))

            if (isSearchingMode) {
                if (isSearching && searchResults.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.searching), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else if (searchResults.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.no_folders_found), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    Text(stringResource(R.string.folders_found, searchResults.size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 4.dp))
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(searchResults, key = { it.path }) { item ->
                            Surface(
                                modifier = Modifier.fillMaxWidth().clickable { if (multiSelect) toggleSelected(item.path) else choose(item.path) },
                                color = Color.Transparent,
                                shape = RoundedCornerShape(Radius.sm),
                            ) {
                                Row(Modifier.padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    if (multiSelect) {
                                        Icon(
                                            if (item.path in selectedPaths) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                                            null,
                                            tint = if (item.path in selectedPaths) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp),
                                        )
                                        Spacer(Modifier.width(8.dp))
                                    }
                                    Icon(Icons.Default.FolderOpen, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(item.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        // Path must stay fully readable - wraps onto a 2nd (or 3rd...)
                                        // line rather than being ellipsized, since a truncated path is
                                        // exactly what makes two similarly-named folders indistinguishable.
                                        Text(item.path, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                            HorizontalDivider(Modifier.padding(start = 44.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        }
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { currentPath = rootPath }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.Home, null, tint = if (currentPath == rootPath) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    }
                    // Always offered (not just after drilling in from here) as a plain "go to parent
                    // folder" - bounded at rootPath so it can't wander above the app's storage root.
                    // The sheet can start on an arbitrary deep folder (e.g. the last-browsed Explorer
                    // path), where a history-based back button would have nothing to pop to.
                    if (currentPath != rootPath) {
                        IconButton(onClick = {
                            val parent = File(currentPath).parent
                            currentPath = if (parent != null && parent.length >= rootPath.length && parent.startsWith(rootPath)) parent else rootPath
                        }, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        }
                    }
                    val breadcrumbScroll = rememberScrollState()
                    Row(Modifier.weight(1f).horizontalScroll(breadcrumbScroll).padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        val parts = currentPath.removePrefix(rootPath).split("/").filter { it.isNotBlank() }
                        Text(if (parts.isEmpty()) stringResource(R.string.internal_storage) else parts.joinToString(" › "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                    Box {
                        IconButton(onClick = { showSortMenu = true }, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.AutoMirrored.Filled.Sort, stringResource(R.string.sort_folders_by), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        }
                        DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.sort_by_date_modified)) },
                                leadingIcon = { Icon(Icons.Default.Schedule, null, modifier = Modifier.size(18.dp)) },
                                trailingIcon = { if (sortMode == BrowseSortMode.DATE) Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp)) },
                                onClick = { setSortMode(BrowseSortMode.DATE); showSortMenu = false },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.sort_by_item_count)) },
                                leadingIcon = { Icon(Icons.Default.Numbers, null, modifier = Modifier.size(18.dp)) },
                                trailingIcon = { if (sortMode == BrowseSortMode.COUNT) Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp)) },
                                onClick = { setSortMode(BrowseSortMode.COUNT); showSortMenu = false },
                            )
                        }
                    }
                    IconButton(onClick = { pendingCreateFolder = true }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.CreateNewFolder, stringResource(R.string.new_folder), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.height(4.dp))
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(folders, key = { it.path }) { folder ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable { currentPath = folder.path },
                            color = Color.Transparent,
                            shape = RoundedCornerShape(Radius.sm),
                        ) {
                            Row(Modifier.padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (multiSelect) {
                                    IconButton(onClick = { toggleSelected(folder.path) }, modifier = Modifier.size(28.dp)) {
                                        Icon(
                                            if (folder.path in selectedPaths) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                                            null,
                                            tint = if (folder.path in selectedPaths) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                    Spacer(Modifier.width(4.dp))
                                }
                                Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(folder.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                        if (folder.path in moverSourcePaths) {
                                            Spacer(Modifier.width(4.dp))
                                            Icon(
                                                Icons.AutoMirrored.Filled.DriveFileMove,
                                                stringResource(R.string.mover_source_marker),
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(14.dp),
                                            )
                                        }
                                    }
                                    if (folder.mediaCount > 0) {
                                        Text(stringResource(R.string.media_count, folder.mediaCount), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                        HorizontalDivider(Modifier.padding(start = 44.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    }
                }

                if (!suggestedFolderName.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(Radius.md),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.fillMaxWidth().clickable {
                            val target = File(currentPath, suggestedFolderName)
                            if (!target.exists()) target.mkdirs()
                            choose(target.path)
                        },
                    ) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CreateNewFolder, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(R.string.mover_create_dest_from_source, suggestedFolderName),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(Radius.md),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth().clickable { if (multiSelect) toggleSelected(currentPath) else choose(currentPath) },
                ) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
                        val currentIsSelected = multiSelect && currentPath in selectedPaths
                        Icon(
                            if (multiSelect) { if (currentIsSelected) Icons.Default.Check else Icons.Default.Add } else Icons.Default.Check,
                            null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (multiSelect) { if (currentIsSelected) stringResource(R.string.mover_remove_this_folder) else stringResource(R.string.mover_add_this_folder) } else stringResource(R.string.folder_picker_select_here),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }
        }
    }

    if (pendingCreateFolder) {
        AlertDialog(
            onDismissRequest = { pendingCreateFolder = false; newFolderName = "" },
            title = { Text(stringResource(R.string.create_folder_title)) },
            text = {
                OutlinedTextField(value = newFolderName, onValueChange = { newFolderName = it }, label = { Text(stringResource(R.string.folder_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newFolderName.isNotBlank()) {
                        val newDir = File(currentPath, newFolderName)
                        try { newDir.mkdirs(); currentPath = newDir.path } catch (_: Exception) { }
                        pendingCreateFolder = false; newFolderName = ""
                    }
                }) { Text(stringResource(R.string.action_create)) }
            },
            dismissButton = { TextButton(onClick = { pendingCreateFolder = false; newFolderName = "" }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

package org.fossify.gallery.compose.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.commons.dialogs.PropertiesDialog
import org.fossify.gallery.compose.components.ConfirmDestructive
import org.fossify.gallery.compose.components.FolderRenameDialog
import org.fossify.gallery.compose.components.FolderTile
import org.fossify.gallery.compose.components.LibraryAlbumGrid
import org.fossify.gallery.compose.components.AlbumGridItem
import org.fossify.gallery.compose.components.SelectionAppBar
import org.fossify.gallery.compose.components.EmptyState
import org.fossify.gallery.compose.components.GalleryImage
import org.fossify.gallery.extensions.config
import org.fossify.gallery.compose.theme.AppMotion
import org.fossify.gallery.compose.theme.LocalMediaRepository
import org.fossify.gallery.R
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import org.fossify.gallery.helpers.UndoAction
import org.fossify.gallery.helpers.UndoManager
import org.fossify.gallery.helpers.UndoType
import org.fossify.gallery.models.Directory
import org.fossify.gallery.viewmodels.AlbumsViewModel
import org.fossify.gallery.workers.MetadataSyncWorker
import android.widget.Toast
import org.fossify.commons.extensions.toast
import java.io.File

/** How many thumbnails the list-mode album row shows. Also the SQL LIMIT for fetching them. */
private const val ALBUM_PREVIEW_COUNT = 4

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AlbumsScreen(
    viewModel: AlbumsViewModel,
    onFolderClick: (Directory) -> Unit,
    modifier: Modifier = Modifier,
    viewSettings: ViewSettings = ViewSettings(),
    onSelectionActiveChanged: (Boolean) -> Unit = {},
    tabIndex: Int? = null,
) {
    val ctx = LocalContext.current
    val repo = LocalMediaRepository.current
    val state by viewModel.state.collectAsState()
    var favorites by remember { mutableStateOf(ctx.config.favoriteFolders) }
    var selectedPaths by remember { mutableStateOf<Set<String>>(emptySet()) }
    val hasSelection = selectedPaths.isNotEmpty()
    LaunchedEffect(hasSelection) { onSelectionActiveChanged(hasSelection) }
    BackHandler(enabled = hasSelection) { selectedPaths = emptySet() }
    // Set once "Use as mover source" is tapped (can be several folders at once) - the destination
    // picker (search + Explorer browse, see FolderPathPickerSheet) then decides the pair(s)' shared
    // other half, and one FolderPair per source gets created against it.
    var pendingMoverSources by remember { mutableStateOf<List<String>?>(null) }
    var renameFolderPath by remember { mutableStateOf<String?>(null) }
    var showDeleteFoldersConfirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // Re-read every time a selection session starts/ends (not just once for the whole tab visit,
    // and not left stale from the previous session) - mirrors FolderPathPickerSheet's own "already a
    // mover source" marker (same tertiary-tinted DriveFileMove icon), just applied to the selection
    // toolbar's action icon instead of a per-row badge.
    val moverSourcePaths = remember(hasSelection) { org.fossify.gallery.helpers.loadMoverPairs(ctx).map { it.source }.toSet() }
    var selectionBarHeightPx by remember { mutableIntStateOf(0) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    // Bouncy spring can transiently overshoot past 0 while animating the inset closed, and
    // Modifier.padding() throws on a negative value - coerce here, mirroring MediaScreen.kt.
    val rawContentTopInset by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (hasSelection) with(density) { selectionBarHeightPx.toDp() } else 0.dp,
        animationSpec = org.fossify.gallery.compose.theme.AppMotion.insetSpring,
        label = "albumsSelectionInset",
    )
    val contentTopInset = rawContentTopInset.coerceAtLeast(0.dp)

    val sortedDirs = remember(state.directories, viewSettings.sortBy, viewSettings.sortDesc) {
        val sorted = when (viewSettings.sortBy) {
            SortField.NAME -> state.directories.sortedBy { it.name.lowercase() }
            SortField.DATE -> state.directories.sortedBy { it.modified }
            SortField.SIZE -> state.directories.sortedBy { it.size }
            SortField.RATING -> state.directories.sortedBy { it.name.lowercase() }
            SortField.COUNT -> state.directories.sortedBy { it.mediaCnt }
        }
        if (viewSettings.sortDesc) sorted.reversed() else sorted
    }

    val isGrid = viewSettings.viewType == ViewType.GRID
    val itemSpacing = viewSettings.spacing.dp
    val containerColor = when (viewSettings.displayMode) {
        // Not colorScheme.surface - this app's custom ColorScheme sets background == surface, so a
        // folder tile with no thumbnail (empty folder) would be visually indistinguishable from the
        // screen behind it, leaving no indication a tappable card is even there.
        DisplayMode.COMPACT, DisplayMode.NORMAL -> MaterialTheme.colorScheme.surfaceContainerHigh
        DisplayMode.DARK -> MaterialTheme.colorScheme.surfaceVariant
    }

    var albumPreviews by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    LaunchedEffect(sortedDirs, isGrid) {
        if (!isGrid) {
            albumPreviews = withContext(Dispatchers.IO) {
                // Capped in SQL, not after the fact. This runs once per folder, and getMediaFromPath
                // returned every live row of the folder as a full 14-column Medium just to keep the
                // first four paths - so on a large library it materialised essentially the entire
                // media table per pass. Measured on a 163k-item/~2.8k-folder device: this was the
                // cold-start CPU burn, ~85% of all on-CPU samples sitting in this one query, one
                // core pegged for minutes (several, in fact - the passes overlap).
                sortedDirs.associate { dir -> dir.path to repo.getPreviewPathsFromPath(dir.path, ALBUM_PREVIEW_COUNT) }
            }
        }
    }
    val albumItems = remember(sortedDirs, albumPreviews) {
        sortedDirs.map { AlbumGridItem(key = it.path, name = it.name, thumbnailPath = it.tmb, count = it.mediaCnt, previewPaths = albumPreviews[it.path] ?: emptyList()) }
    }

    Box(Modifier.fillMaxSize()) {
        Column(modifier = modifier.fillMaxSize().padding(top = contentTopInset)) {
            // Crossfade instead of an instant swap - see MediaScreen's paged content for why.
            Crossfade(targetState = if (state.isLoading) "loading" else if (state.directories.isEmpty()) "empty" else "content", animationSpec = AppMotion.short, label = "albumsContent") { s ->
                when (s) {
                    "loading" -> MediaSkeleton(columns = viewSettings.columnCount)
                    "empty" -> EmptyState(Icons.Default.Folder, stringResource(R.string.no_albums))
                    else -> LibraryAlbumGrid(
                        items = albumItems,
                        viewSettings = viewSettings,
                        onClick = { item ->
                            if (hasSelection) selectedPaths = if (item.key in selectedPaths) selectedPaths - item.key else selectedPaths + item.key
                            else sortedDirs.find { it.path == item.key }?.let(onFolderClick)
                        },
                        onLongClick = { item -> selectedPaths = selectedPaths + item.key },
                        selectedKeys = selectedPaths,
                        modifier = Modifier.weight(1f),
                        tabIndex = tabIndex,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = hasSelection,
            enter = slideInVertically { -it } + fadeIn(AppMotion.medium),
            exit = slideOutVertically { -it } + fadeOut(AppMotion.medium),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            val isSingle = selectedPaths.size == 1
            // "All favorited" (not just the first path) decides the icon/label - toggling then
            // favorites every not-yet-favorited folder in the batch, or un-favorites all of them
            // if the whole batch was already favorited, rather than only ever acting on one path.
            val allFav = selectedPaths.isNotEmpty() && selectedPaths.all { it in favorites }
            SelectionAppBar(
                modifier = Modifier.onGloballyPositioned { selectionBarHeightPx = it.size.height },
                count = selectedPaths.size,
                onClose = { selectedPaths = emptySet() },
                actions = {
                    // Open/Info only make sense for exactly one target folder.
                    if (isSingle) {
                        IconButton(onClick = { selectedPaths.firstOrNull()?.let { p -> sortedDirs.find { it.path == p }?.let { d -> onFolderClick(d) } }; selectedPaths = emptySet() }) { Icon(Icons.Default.Folder, stringResource(R.string.action_open)) }
                    }
                    IconButton(onClick = {
                        selectedPaths.forEach { p -> if (allFav) ctx.config.removeFavoriteFolder(p) else ctx.config.addFavoriteFolder(p) }
                        favorites = ctx.config.favoriteFolders
                        selectedPaths = emptySet()
                    }) { Icon(if (allFav) Icons.Default.Star else Icons.Default.StarBorder, stringResource(if (allFav) R.string.action_unfavorite else R.string.action_favorite)) }
                    if (isSingle) {
                        IconButton(onClick = { selectedPaths.firstOrNull()?.let { (ctx as? android.app.Activity)?.let { a -> PropertiesDialog(a, it, true) } } }) { Icon(Icons.Default.Info, stringResource(R.string.action_info)) }
                    }
                    val allAlreadyMoverSources = selectedPaths.isNotEmpty() && selectedPaths.all { it in moverSourcePaths }
                    IconButton(onClick = {
                        pendingMoverSources = selectedPaths.toList()
                        selectedPaths = emptySet()
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
                    IconButton(onClick = {
                        val batch = selectedPaths.toList()
                        batch.forEach { path -> MetadataSyncWorker.scheduleFolderScan(ctx, path) }
                        ctx.toast(
                            if (batch.size == 1) ctx.getString(R.string.scan_started, File(batch.first()).name)
                            else ctx.getString(R.string.scan_started_count, batch.size),
                            Toast.LENGTH_SHORT,
                        )
                        selectedPaths = emptySet()
                    }) { Icon(Icons.AutoMirrored.Filled.Label, stringResource(R.string.action_scan_metadata)) }
                    // Rename/Delete tucked behind an overflow menu rather than appended as raw icons -
                    // this row is already tight on a real phone with 5 icons (confirmed on-device
                    // elsewhere in this app, see SelectionTopAppBar's own overflow precedent); a single
                    // new MoreVert icon adds these two without risking the existing icons clipping off.
                    var folderMenuOpen by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { folderMenuOpen = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.more_actions)) }
                        DropdownMenu(expanded = folderMenuOpen, onDismissRequest = { folderMenuOpen = false }) {
                            if (isSingle) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_rename)) },
                                    leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, null) },
                                    onClick = { folderMenuOpen = false; renameFolderPath = selectedPaths.first() },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(org.fossify.commons.R.string.delete)) },
                                leadingIcon = { Icon(Icons.Default.Delete, null) },
                                onClick = { folderMenuOpen = false; showDeleteFoldersConfirm = true },
                            )
                        }
                    }
                },
            )
        }
    }

    renameFolderPath?.let { path ->
        FolderRenameDialog(
            folderPath = path,
            onDismiss = { renameFolderPath = null },
            onRenamed = { _, _ -> selectedPaths = emptySet() },
        )
    }

    if (showDeleteFoldersConfirm) {
        ConfirmDestructive(
            title = stringResource(org.fossify.commons.R.string.delete),
            text = stringResource(R.string.delete_folders_confirmation),
            confirmLabel = stringResource(org.fossify.commons.R.string.delete),
            onConfirm = {
                showDeleteFoldersConfirm = false
                val folders = selectedPaths
                selectedPaths = emptySet()
                scope.launch {
                    val paths = withContext(Dispatchers.IO) { repo.mediaPathsUnderFolders(folders) }
                    if (paths.isNotEmpty()) {
                        repo.moveToRecycleBinBatch(paths)
                        UndoManager.push(UndoAction(paths = paths.toSet(), type = UndoType.DELETE))
                    }
                }
            },
            onDismiss = { showDeleteFoldersConfirm = false },
        )
    }

    pendingMoverSources?.let { sources ->
        val addedFormat = stringResource(R.string.mover_pair_added)
        val addedCountFormat = stringResource(R.string.mover_pairs_added_count)
        FolderPathPickerSheet(
            title = stringResource(R.string.mover_select_dest),
            initialPath = ctx.config.lastExplorerPath.ifBlank { ctx.config.internalStoragePath },
            onPathSelected = { destination ->
                org.fossify.gallery.helpers.addMoverPairs(ctx, sources, destination)
                val msg = if (sources.size == 1) addedFormat.format(File(sources[0]).name, File(destination).name) else addedCountFormat.format(sources.size, File(destination).name)
                ctx.toast(msg, Toast.LENGTH_SHORT)
            },
            onDismiss = { pendingMoverSources = null },
            suggestedFolderName = if (sources.size == 1) File(sources[0]).name else null,
        )
    }
}

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
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Tune
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.commons.dialogs.PropertiesDialog
import org.fossify.commons.dialogs.SecurityDialog
import org.fossify.commons.dialogs.FolderLockingNoticeDialog
import org.fossify.commons.helpers.SHOW_ALL_TABS
import org.fossify.gallery.compose.components.ConfirmDestructive
import org.fossify.gallery.compose.components.FolderRenameDialog
import org.fossify.gallery.compose.components.FolderTile
import org.fossify.gallery.compose.components.LibraryAlbumGrid
import org.fossify.gallery.compose.components.AlbumGridItem
import org.fossify.gallery.compose.components.SelectionAppBar
import org.fossify.gallery.compose.components.LibraryScreenHeader
import org.fossify.gallery.compose.components.RefreshActionButton
import org.fossify.gallery.compose.components.viewSettingsSummary
import org.fossify.gallery.compose.components.EmptyState
import org.fossify.gallery.compose.components.GalleryImage
import org.fossify.gallery.compose.components.InlineRefreshIndicator
import org.fossify.gallery.compose.components.LoadErrorBanner
import org.fossify.gallery.compose.components.LoadErrorState
import org.fossify.gallery.extensions.config
import org.fossify.gallery.compose.theme.AppMotion
import org.fossify.gallery.compose.theme.LocalMediaRepository
import org.fossify.gallery.R
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import org.fossify.gallery.helpers.UndoAction
import org.fossify.gallery.helpers.UndoManager
import org.fossify.gallery.helpers.withRecycleBinDeletePasswordCheck
import org.fossify.gallery.helpers.UndoType
import org.fossify.gallery.helpers.selectionForAll
import org.fossify.gallery.models.Directory
import org.fossify.gallery.viewmodels.AlbumsViewModel
import org.fossify.gallery.workers.MetadataSyncWorker
import android.widget.Toast
import org.fossify.commons.extensions.toast
import java.io.File

private val albumSelectionSaver = listSaver<Set<String>, String>(
    save = { it.toList() },
    restore = { it.toSet() },
)

/** How many thumbnails the list-mode album row shows. Also the SQL LIMIT for fetching them. */
private const val ALBUM_PREVIEW_COUNT = 4

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AlbumsScreen(
    viewModel: AlbumsViewModel,
    onFolderClick: (Directory) -> Unit,
    modifier: Modifier = Modifier,
    viewSettings: ViewSettings = ViewSettings(),
    externalSearchQuery: String = "",
    onClearExternalSearch: () -> Unit = {},
    onOpenViewSettings: (() -> Unit)? = null,
    onSelectionActiveChanged: (Boolean) -> Unit = {},
    tabIndex: Int? = null,
) {
    val ctx = LocalContext.current
    val repo = LocalMediaRepository.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    var favorites by remember { mutableStateOf(ctx.config.favoriteFolders) }
    var selectedPaths by rememberSaveable(stateSaver = albumSelectionSaver) { mutableStateOf<Set<String>>(emptySet()) }
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
    val moverSourcePaths by androidx.compose.runtime.produceState(initialValue = emptySet<String>(), hasSelection) {
        value = if (!hasSelection) {
            emptySet()
        } else {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                org.fossify.gallery.helpers.loadMoverPairs(ctx).map { it.source }.toSet()
            }
        }
    }
    val density = androidx.compose.ui.platform.LocalDensity.current
    // TopAppBar has a stable Material height. Starting with that known value prevents the first
    // selected frame from drawing at 0px and then visibly correcting itself after measurement.
    // Keep the measured value for accessibility font scales or a future custom toolbar height.
    var selectionBarHeightPx by remember(density) {
        mutableIntStateOf(with(density) { androidx.compose.material3.TopAppBarDefaults.TopAppBarExpandedHeight.roundToPx() })
    }
    // Bouncy spring can transiently overshoot past 0 while animating the inset closed, and
    // Modifier.padding() throws on a negative value - coerce here, mirroring MediaScreen.kt.
    val rawContentTopInset by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (hasSelection) with(density) { selectionBarHeightPx.toDp() } else 0.dp,
        animationSpec = org.fossify.gallery.compose.theme.AppMotion.insetSpring,
        label = "albumsSelectionInset",
    )
    val contentTopInset = rawContentTopInset.coerceAtLeast(0.dp)

    val sortedDirs = remember(state.directories, viewSettings.sortBy, viewSettings.sortDesc) {
        when (viewSettings.sortBy) {
            SortField.NAME, SortField.RATING, SortField.RECENT -> if (viewSettings.sortDesc) state.directories.sortedWith(compareByDescending<Directory> { it.name.lowercase() }.thenBy { it.path.lowercase() }) else state.directories.sortedWith(compareBy<Directory> { it.name.lowercase() }.thenBy { it.path.lowercase() })
            SortField.DATE -> if (viewSettings.sortDesc) state.directories.sortedWith(compareByDescending<Directory> { it.modified }.thenBy { it.path.lowercase() }) else state.directories.sortedWith(compareBy<Directory> { it.modified }.thenBy { it.path.lowercase() })
            SortField.SIZE -> if (viewSettings.sortDesc) state.directories.sortedWith(compareByDescending<Directory> { it.size }.thenBy { it.path.lowercase() }) else state.directories.sortedWith(compareBy<Directory> { it.size }.thenBy { it.path.lowercase() })
            SortField.COUNT -> if (viewSettings.sortDesc) state.directories.sortedWith(compareByDescending<Directory> { it.mediaCnt }.thenBy { it.path.lowercase() }) else state.directories.sortedWith(compareBy<Directory> { it.mediaCnt }.thenBy { it.path.lowercase() })
        }
    }
    val normalizedSearchQuery = externalSearchQuery.trim()
    val visibleDirs = remember(sortedDirs, normalizedSearchQuery) {
        if (normalizedSearchQuery.isBlank()) sortedDirs
        else sortedDirs.filter { it.name.contains(normalizedSearchQuery, ignoreCase = true) || it.path.contains(normalizedSearchQuery, ignoreCase = true) }
    }
    LaunchedEffect(normalizedSearchQuery) { selectedPaths = emptySet() }
    val isGrid = viewSettings.viewType != ViewType.LIST
    val itemSpacing = viewSettings.spacing.dp
    val containerColor = when (viewSettings.displayMode) {
        // Not colorScheme.surface - this app's custom ColorScheme sets background == surface, so a
        // folder tile with no thumbnail (empty folder) would be visually indistinguishable from the
        // screen behind it, leaving no indication a tappable card is even there.
        DisplayMode.COMPACT, DisplayMode.NORMAL -> MaterialTheme.colorScheme.surfaceContainerHigh
        DisplayMode.DARK -> MaterialTheme.colorScheme.surfaceVariant
    }

    var albumPreviews by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    LaunchedEffect(visibleDirs, isGrid) {
        if (!isGrid) {
            albumPreviews = withContext(Dispatchers.IO) {
                // Capped in SQL, not after the fact. This runs once per folder, and getMediaFromPath
                // returned every live row of the folder as a full 14-column Medium just to keep the
                // first four paths - so on a large library it materialised essentially the entire
                // media table per pass. Measured on a 163k-item/~2.8k-folder device: this was the
                // cold-start CPU burn, ~85% of all on-CPU samples sitting in this one query, one
                // core pegged for minutes (several, in fact - the passes overlap).
                visibleDirs.associate { dir -> dir.path to repo.getPreviewPathsFromPath(dir.path, ALBUM_PREVIEW_COUNT) }
            }
        }
    }
    val albumItems = remember(visibleDirs, albumPreviews) {
        visibleDirs.map { AlbumGridItem(key = it.path, name = it.name, thumbnailPath = it.tmb, count = it.mediaCnt, imageCount = it.imageCount, videoCount = it.videoCount, previewPaths = albumPreviews[it.path] ?: emptyList()) }
    }

    Box(Modifier.fillMaxSize()) {
        Column(modifier = modifier.fillMaxSize().padding(top = contentTopInset)) {
            LibraryScreenHeader(
                title = stringResource(R.string.albums),
                subtitle = stringResource(R.string.albums_header_summary, visibleDirs.size, viewSettingsSummary(viewSettings, includeGrouping = false)),
                leadingIcon = Icons.Default.Folder,
                actions = {
                    if (onOpenViewSettings != null) {
                        IconButton(onClick = onOpenViewSettings) {
                            Icon(Icons.Default.Tune, stringResource(R.string.view_settings_title))
                        }
                    }
                    RefreshActionButton(isRefreshing = state.isRefreshing, onRefresh = viewModel::load)
                },
            )
            InlineRefreshIndicator(
                visible = state.isRefreshing,
                contentAvailable = state.directories.isNotEmpty(),
            )
            state.error?.let { errorMessage ->
                if (state.directories.isNotEmpty() && !state.isLoading) {
                    LoadErrorBanner(
                        message = errorMessage,
                        onRetry = viewModel::load,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
            // Crossfade instead of an instant swap - see MediaScreen's paged content for why.
            Crossfade(
                targetState = when {
                    visibleDirs.isNotEmpty() -> "content"
                    state.isLoading -> "loading"
                    state.error != null -> "error"
                    else -> "empty"
                },
                animationSpec = AppMotion.short,
                label = "albumsContent",
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { s ->
                when (s) {
                    "loading" -> FolderSkeleton(viewSettings = viewSettings)
                    "error" -> LoadErrorState(message = state.error, onRetry = viewModel::load)
                    "empty" -> if (normalizedSearchQuery.isNotBlank() && state.directories.isNotEmpty()) {
                        EmptyState(Icons.Default.SearchOff, stringResource(R.string.no_results), subtitle = stringResource(R.string.no_results_hint), actionLabel = stringResource(R.string.clear_filter), onAction = onClearExternalSearch)
                    } else {
                        EmptyState(Icons.Default.Folder, stringResource(R.string.no_albums))
                    }
                    else -> LibraryAlbumGrid(
                        items = albumItems,
                        viewSettings = viewSettings,
                        onClick = { item ->
                            if (hasSelection) selectedPaths = if (item.key in selectedPaths) selectedPaths - item.key else selectedPaths + item.key
                            else visibleDirs.find { it.path == item.key }?.let(onFolderClick)
                        },
                        onLongClick = { item -> selectedPaths = selectedPaths + item.key },
            selectedKeys = selectedPaths,
                        pathHint = { org.fossify.gallery.compose.components.folderPathHint(it.key, ctx.getString(R.string.internal_storage)) },
                        modifier = Modifier.weight(1f),
                        tabIndex = tabIndex,
                        fastScrollLabel = { index -> albumItems.getOrNull(index)?.name },
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
                modifier = Modifier.onSizeChanged { size ->
                    if (selectionBarHeightPx != size.height) selectionBarHeightPx = size.height
                },
                count = selectedPaths.size,
                onClose = { selectedPaths = emptySet() },
                subtitle = ctx.getString(R.string.selection_scope_visible, visibleDirs.size),
                actions = {
                    // Open/Info only make sense for exactly one target folder.
                    if (isSingle) {
                        IconButton(onClick = { selectedPaths.firstOrNull()?.let { p -> visibleDirs.find { it.path == p }?.let { d -> onFolderClick(d) } }; selectedPaths = emptySet() }) { Icon(Icons.Default.Folder, stringResource(R.string.action_open)) }
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
                        val activity = ctx as? android.app.Activity
                        DropdownMenu(expanded = folderMenuOpen, onDismissRequest = { folderMenuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_select_all)) },
                                onClick = { folderMenuOpen = false; selectedPaths = selectionForAll(selectedPaths, visibleDirs.map { it.path }, invert = false) },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_invert_selection)) },
                                onClick = { folderMenuOpen = false; selectedPaths = selectionForAll(selectedPaths, visibleDirs.map { it.path }, invert = true) },
                            )
                            HorizontalDivider()
                            if (isSingle) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_rename)) },
                                    leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, null) },
                                    onClick = { folderMenuOpen = false; renameFolderPath = selectedPaths.first() },
                                )
                            }
                            // Password-lock a folder so opening it needs the PIN/pattern/fingerprint -
                            // the only way to *set* per-folder protection from the Compose UI (it lived
                            // solely in the legacy folder grid before). Shown when any selected folder
                            // isn't already locked; Unlock when any is.
                            if (activity != null && selectedPaths.any { !ctx.config.isFolderProtected(it) }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(org.fossify.commons.R.string.lock_folder)) },
                                    leadingIcon = { Icon(Icons.Default.Lock, null) },
                                    onClick = {
                                        folderMenuOpen = false
                                        val targets = selectedPaths.filter { !ctx.config.isFolderProtected(it) }
                                        val doLock = {
                                            SecurityDialog(activity, "", SHOW_ALL_TABS) { hash, type, success ->
                                                if (success) { targets.forEach { ctx.config.addFolderProtection(it, hash, type) }; selectedPaths = emptySet() }
                                            }
                                        }
                                        // One-time notice that the lock only hides from THIS app.
                                        if (ctx.config.wasFolderLockingNoticeShown) doLock()
                                        else FolderLockingNoticeDialog(activity) { doLock() }
                                    },
                                )
                            }
                            if (activity != null && selectedPaths.any { ctx.config.isFolderProtected(it) }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(org.fossify.commons.R.string.unlock_folder)) },
                                    leadingIcon = { Icon(Icons.Default.LockOpen, null) },
                                    onClick = {
                                        folderMenuOpen = false
                                        val protectedPaths = selectedPaths.filter { ctx.config.isFolderProtected(it) }
                                        protectedPaths.firstOrNull()?.let { first ->
                                            val tab = ctx.config.getFolderProtectionType(first)
                                            val hash = ctx.config.getFolderProtectionHash(first)
                                            SecurityDialog(activity, hash, tab) { _, _, success ->
                                                if (success) {
                                                    protectedPaths.filter { ctx.config.getFolderProtectionType(it) == tab && ctx.config.getFolderProtectionHash(it) == hash }
                                                        .forEach { ctx.config.removeFolderProtection(it) }
                                                    selectedPaths = emptySet()
                                                }
                                            }
                                        }
                                    },
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
        // Folder deletion is recoverable, so keep the same visible undo affordance as the media
        // and Explorer surfaces instead of making the user open the recycle bin manually.
        org.fossify.gallery.compose.components.UndoBar(modifier = Modifier.align(Alignment.BottomCenter))
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
                ctx.withRecycleBinDeletePasswordCheck {
                    selectedPaths = emptySet()
                    scope.launch {
                        val paths = withContext(Dispatchers.IO) { repo.mediaPathsUnderFolders(folders) }
                        if (paths.isNotEmpty()) {
                            repo.moveToRecycleBinBatch(paths)
                            UndoManager.push(UndoAction(paths = paths.toSet(), type = UndoType.DELETE))
                        }
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

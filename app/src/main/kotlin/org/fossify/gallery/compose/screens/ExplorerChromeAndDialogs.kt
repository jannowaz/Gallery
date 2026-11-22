package org.fossify.gallery.compose.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.commons.extensions.toast
import org.fossify.gallery.R
import org.fossify.gallery.compose.components.ActiveFilterChip
import org.fossify.gallery.compose.components.ConfirmDestructive
import org.fossify.gallery.compose.components.FolderRenameDialog
import org.fossify.gallery.compose.components.GalleryImage
import org.fossify.gallery.compose.components.LibraryFilterSummary
import org.fossify.gallery.compose.components.MediaInfoDialog
import org.fossify.gallery.compose.components.RateAndTagSheet
import org.fossify.gallery.compose.components.RenameDialog
import org.fossify.gallery.compose.components.SelectionAppBar
import org.fossify.gallery.compose.theme.LocalMediaRepository
import org.fossify.gallery.compose.util.PeekState
import org.fossify.gallery.compose.util.XmpBatch
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.UndoAction
import org.fossify.gallery.helpers.UndoManager
import org.fossify.gallery.helpers.UndoType
import org.fossify.gallery.helpers.addMoverPairs
import org.fossify.gallery.helpers.explorerAncestorChain
import org.fossify.gallery.helpers.withRecycleBinDeletePasswordCheck
import org.fossify.gallery.viewmodels.LibraryFilterActions
import org.fossify.gallery.viewmodels.LibraryFilterState
import java.io.File

private const val MAX_COLLAPSED_BREADCRUMBS = 3
private const val PEEK_SCRIM_ALPHA = 0.86f
private const val PEEK_THUMBNAIL_SIZE = 1024

@Composable
internal fun ExplorerFolderSelectionBar(
    selectedCount: Int,
    visibleFolderCount: Int,
    allAlreadyMoverSources: Boolean,
    onClose: () -> Unit,
    onOpenSelection: () -> Unit,
    onHideSelection: () -> Unit,
    onUseAsMoverSource: () -> Unit,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    onOpenInMedia: () -> Unit,
    onRenameSingle: () -> Unit,
    onDeleteSelection: () -> Unit,
) {
    SelectionAppBar(
        count = selectedCount,
        onClose = onClose,
        subtitle = stringResource(R.string.selection_scope_visible, visibleFolderCount),
        actions = {
            IconButton(onClick = onOpenSelection) {
                Icon(Icons.Default.Folder, stringResource(R.string.action_open))
            }
            IconButton(onClick = onHideSelection) {
                Icon(Icons.Default.VisibilityOff, stringResource(R.string.action_hide))
            }
            IconButton(onClick = onUseAsMoverSource) {
                Icon(
                    Icons.AutoMirrored.Filled.DriveFileMove,
                    stringResource(
                        if (allAlreadyMoverSources) R.string.mover_source_marker
                        else R.string.action_use_as_mover_source,
                    ),
                    tint = if (allAlreadyMoverSources) MaterialTheme.colorScheme.error else LocalContentColor.current,
                )
            }
            ExplorerFolderSelectionOverflow(
                isSingleFolder = selectedCount == 1,
                onSelectAll = onSelectAll,
                onInvertSelection = onInvertSelection,
                onOpenInMedia = onOpenInMedia,
                onRenameSingle = onRenameSingle,
                onDeleteSelection = onDeleteSelection,
            )
        },
    )
}

@Composable
private fun ExplorerFolderSelectionOverflow(
    isSingleFolder: Boolean,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    onOpenInMedia: () -> Unit,
    onRenameSingle: () -> Unit,
    onDeleteSelection: () -> Unit,
) {
    var folderMenuOpen by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { folderMenuOpen = true }) {
            Icon(Icons.Default.MoreVert, stringResource(R.string.more_actions))
        }
        DropdownMenu(expanded = folderMenuOpen, onDismissRequest = { folderMenuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_select_all)) },
                onClick = { folderMenuOpen = false; onSelectAll() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_invert_selection)) },
                onClick = { folderMenuOpen = false; onInvertSelection() },
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_open_in_media)) },
                leadingIcon = { Icon(Icons.Default.PhotoLibrary, null) },
                onClick = { folderMenuOpen = false; onOpenInMedia() },
            )
            if (isSingleFolder) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_rename)) },
                    leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, null) },
                    onClick = { folderMenuOpen = false; onRenameSingle() },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(org.fossify.commons.R.string.delete)) },
                leadingIcon = { Icon(Icons.Default.Delete, null) },
                onClick = { folderMenuOpen = false; onDeleteSelection() },
            )
        }
    }
}

@Composable
internal fun ExplorerBreadcrumbBar(
    currentPath: String,
    storageRoot: String,
    canGoUp: Boolean,
    normalizedNameFilter: String,
    filterState: LibraryFilterState,
    filterActions: LibraryFilterActions,
    visibleFileCount: Int,
    isRefreshing: Boolean,
    onGoUp: () -> Unit,
    onSelectBreadcrumb: (List<String>, String) -> Unit,
    onClearNameFilter: () -> Unit,
    onOpenViewSettings: (() -> Unit)?,
    onOpenSwipeMode: () -> Unit,
    onOpenPersonReview: () -> Unit,
    onRescan: () -> Unit,
    onScanMetadata: () -> Unit,
) {
    val breadcrumbChain = remember(currentPath, storageRoot) {
        explorerAncestorChain(storageRoot, currentPath)
    }
    var breadcrumbsExpanded by remember(currentPath) { mutableStateOf(false) }
    val visibleBreadcrumbIndices = if (breadcrumbsExpanded || breadcrumbChain.size <= MAX_COLLAPSED_BREADCRUMBS) {
        breadcrumbChain.indices.toList()
    } else {
        listOf(0, -1, breadcrumbChain.lastIndex - 1, breadcrumbChain.lastIndex)
    }
    val breadcrumbScrollState = rememberScrollState()
    LaunchedEffect(currentPath) {
        if (breadcrumbChain.size > 1) {
            breadcrumbScrollState.animateScrollTo(breadcrumbScrollState.maxValue)
        } else {
            breadcrumbScrollState.scrollTo(0)
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (canGoUp) {
            IconButton(onClick = onGoUp, modifier = Modifier.size(48.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    stringResource(R.string.cd_back),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        } else {
            Spacer(Modifier.size(48.dp))
        }
        Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
            ExplorerBreadcrumbTrail(
                breadcrumbChain = breadcrumbChain,
                visibleIndices = visibleBreadcrumbIndices,
                currentPath = currentPath,
                storageRoot = storageRoot,
                scrollState = breadcrumbScrollState,
                onExpand = { breadcrumbsExpanded = true },
                onSelectBreadcrumb = onSelectBreadcrumb,
            )
            if (normalizedNameFilter.isNotBlank()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ActiveFilterChip(
                        label = stringResource(R.string.filter_search_query, normalizedNameFilter),
                        onRemove = onClearNameFilter,
                    )
                }
            }
            LibraryFilterSummary(
                filter = filterState,
                resultCount = visibleFileCount,
                actions = filterActions,
            )
        }
        if (onOpenViewSettings != null) {
            IconButton(onClick = onOpenViewSettings, modifier = Modifier.size(48.dp)) {
                Icon(
                    Icons.Default.Tune,
                    contentDescription = stringResource(R.string.view_settings_title),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        ExplorerQuickActionsMenu(
            currentPath = currentPath,
            isRefreshing = isRefreshing,
            onOpenSwipeMode = onOpenSwipeMode,
            onOpenPersonReview = onOpenPersonReview,
            onRescan = onRescan,
            onScanMetadata = onScanMetadata,
        )
    }
}

@Composable
private fun ExplorerBreadcrumbTrail(
    breadcrumbChain: List<String>,
    visibleIndices: List<Int>,
    currentPath: String,
    storageRoot: String,
    scrollState: androidx.compose.foundation.ScrollState,
    onExpand: () -> Unit,
    onSelectBreadcrumb: (List<String>, String) -> Unit,
) {
    val expandPathDescription = stringResource(R.string.cd_expand_path)
    Row(
        Modifier.fillMaxWidth().horizontalScroll(scrollState),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        visibleIndices.forEachIndexed { visibleIndex, breadcrumbIndex ->
            if (visibleIndex > 0) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp),
                )
            }
            if (breadcrumbIndex == -1) {
                TextButton(
                    onClick = onExpand,
                    modifier = Modifier.heightIn(min = 48.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                ) {
                    Text(
                        "…",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.semantics { contentDescription = expandPathDescription },
                    )
                }
            } else {
                val breadcrumbPath = breadcrumbChain[breadcrumbIndex]
                val isCurrent = breadcrumbPath == currentPath
                val label = if (breadcrumbPath == storageRoot) {
                    stringResource(R.string.internal_storage)
                } else {
                    File(breadcrumbPath).name
                }
                TextButton(
                    onClick = { onSelectBreadcrumb(breadcrumbChain.take(breadcrumbIndex + 1), breadcrumbPath) },
                    modifier = Modifier.heightIn(min = 48.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                ) {
                    Text(
                        label,
                        style = if (isCurrent) {
                            MaterialTheme.typography.titleSmall
                        } else {
                            MaterialTheme.typography.labelLarge
                        },
                        fontWeight = if (isCurrent) FontWeight.Medium else FontWeight.Normal,
                        color = if (isCurrent) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun ExplorerQuickActionsMenu(
    currentPath: String,
    isRefreshing: Boolean,
    onOpenSwipeMode: () -> Unit,
    onOpenPersonReview: () -> Unit,
    onRescan: () -> Unit,
    onScanMetadata: () -> Unit,
) {
    var expanded by remember(currentPath) { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.size(48.dp)) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.more_actions),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.swipe_mode_open)) },
                leadingIcon = { Icon(Icons.Default.TouchApp, contentDescription = null) },
                onClick = { expanded = false; onOpenSwipeMode() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.person_scan_open)) },
                leadingIcon = { Icon(Icons.Default.PersonSearch, contentDescription = null) },
                onClick = { expanded = false; onOpenPersonReview() },
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_refresh)) },
                leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                enabled = !isRefreshing,
                onClick = { expanded = false; onRescan() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_scan_metadata)) },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null) },
                onClick = { expanded = false; onScanMetadata() },
            )
        }
    }
}

@Composable
internal fun ExplorerPeekOverlay(peekState: PeekState) {
    peekState.path?.let { peekPath ->
        val peekBackState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
        NavigationBackHandler(
            state = peekBackState,
            isBackEnabled = true,
            onBackCompleted = { peekState.hide() },
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = PEEK_SCRIM_ALPHA))
                .clickable { peekState.hide() },
            contentAlignment = Alignment.Center,
        ) {
            if (peekState.isVideo) {
                VideoThumbnail(
                    videoPath = peekPath,
                    modifier = Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.75f),
                    contentScale = ContentScale.Fit,
                    thumbnailSize = PEEK_THUMBNAIL_SIZE,
                )
            } else {
                GalleryImage(
                    path = peekPath,
                    contentDescription = peekPath.substringAfterLast('/'),
                    modifier = Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.75f),
                    contentScale = ContentScale.Fit,
                    thumbnailSize = PEEK_THUMBNAIL_SIZE,
                )
            }
        }
    }
}

internal data class ExplorerDialogsState(
    val showFileFolderPicker: Boolean,
    val fileFolderPickerIsMove: Boolean,
    val fileInfoPath: String?,
    val showFileRenameDialog: Boolean,
    val showFileRateTagSheet: Boolean,
    val showFileDeleteConfirm: Boolean,
    val renameFolderPath: String?,
    val showDeleteFoldersConfirm: Boolean,
    val pendingMoverSources: List<String>?,
    val selectedFilePaths: Set<String>,
    val selectedFolderPaths: Set<String>,
    val fileCurrentRating: Int,
    val fileRatingIsMixed: Boolean,
    val fileSelectedCommonTags: Set<String>,
    val fileMixedTagCounts: Map<String, Int>,
)

@Composable
internal fun ExplorerDialogsHost(
    dialogsState: ExplorerDialogsState,
    scope: CoroutineScope,
    onDismissFileFolderPicker: () -> Unit,
    onDismissFileInfo: () -> Unit,
    onRenameFiles: (Map<String, String>) -> Unit,
    onDismissFileRename: () -> Unit,
    onRateChange: (Int) -> Unit,
    onDismissRateTag: () -> Unit,
    onConfirmFileDelete: () -> Unit,
    onDismissFileDelete: () -> Unit,
    onRenameFolder: () -> Unit,
    onDismissFolderRename: () -> Unit,
    onConfirmFolderDelete: () -> Unit,
    onDismissFolderDelete: () -> Unit,
    onDismissMoverPicker: () -> Unit,
) {
    val context = LocalContext.current
    val repo = LocalMediaRepository.current

    if (dialogsState.showFileFolderPicker) {
        FolderPickerSheet(
            isMoveOperation = dialogsState.fileFolderPickerIsMove,
            sourcePaths = dialogsState.selectedFilePaths.toList(),
            onDismiss = onDismissFileFolderPicker,
        )
    }
    dialogsState.fileInfoPath?.let { path ->
        MediaInfoDialog(path = path, onDismiss = onDismissFileInfo)
    }
    if (dialogsState.showFileRenameDialog) {
        RenameDialog(
            paths = dialogsState.selectedFilePaths.toList(),
            onRenamed = onRenameFiles,
            onDismiss = onDismissFileRename,
        )
    }
    if (dialogsState.showFileRateTagSheet) {
        ExplorerRateTagSheetHost(
            batch = dialogsState.selectedFilePaths.toList(),
            currentRating = dialogsState.fileCurrentRating,
            ratingIsMixed = dialogsState.fileRatingIsMixed,
            commonTags = dialogsState.fileSelectedCommonTags,
            mixedTagCounts = dialogsState.fileMixedTagCounts,
            scope = scope,
            onRateChange = onRateChange,
            onDismiss = onDismissRateTag,
        )
    }
    if (dialogsState.showFileDeleteConfirm) {
        val itemsCnt = dialogsState.selectedFilePaths.size
        val res = LocalResources.current
        val itemsText = res.getQuantityString(org.fossify.commons.R.plurals.delete_items, itemsCnt, itemsCnt)
        val confirmRes = if (context.config.useRecycleBin) {
            org.fossify.commons.R.string.move_to_recycle_bin_confirmation
        } else {
            org.fossify.commons.R.string.deletion_confirmation
        }
        ConfirmDestructive(
            title = stringResource(org.fossify.commons.R.string.delete),
            text = res.getString(confirmRes, itemsText),
            confirmLabel = stringResource(org.fossify.commons.R.string.delete),
            onConfirm = onConfirmFileDelete,
            onDismiss = onDismissFileDelete,
        )
    }
    dialogsState.renameFolderPath?.let { path ->
        FolderRenameDialog(
            folderPath = path,
            onDismiss = onDismissFolderRename,
            onRenamed = { _, _ -> onRenameFolder() },
        )
    }
    if (dialogsState.showDeleteFoldersConfirm) {
        ConfirmDestructive(
            title = stringResource(org.fossify.commons.R.string.delete),
            text = stringResource(R.string.delete_folders_confirmation),
            confirmLabel = stringResource(org.fossify.commons.R.string.delete),
            onConfirm = {
                val folders = dialogsState.selectedFolderPaths
                onConfirmFolderDelete()
                context.withRecycleBinDeletePasswordCheck {
                    scope.launch {
                        val paths = withContext(Dispatchers.IO) { repo.mediaPathsUnderFolders(folders) }
                        if (paths.isNotEmpty()) {
                            repo.moveToRecycleBinBatch(paths)
                            UndoManager.push(UndoAction(paths = paths.toSet(), type = UndoType.DELETE))
                        }
                    }
                }
            },
            onDismiss = onDismissFolderDelete,
        )
    }
    dialogsState.pendingMoverSources?.let { sources ->
        val addedFormat = stringResource(R.string.mover_pair_added)
        val addedCountFormat = stringResource(R.string.mover_pairs_added_count)
        FolderPathPickerSheet(
            title = stringResource(R.string.mover_select_dest),
            initialPath = context.config.lastExplorerPath.ifBlank { context.config.internalStoragePath },
            onPathSelected = { destination ->
                addMoverPairs(context, sources, destination)
                val msg = if (sources.size == 1) {
                    addedFormat.format(File(sources[0]).name, File(destination).name)
                } else {
                    addedCountFormat.format(sources.size, File(destination).name)
                }
                context.toast(msg, Toast.LENGTH_SHORT)
            },
            onDismiss = onDismissMoverPicker,
            suggestedFolderName = if (sources.size == 1) File(sources[0]).name else null,
        )
    }
}

@Composable
private fun ExplorerRateTagSheetHost(
    batch: List<String>,
    currentRating: Int,
    ratingIsMixed: Boolean,
    commonTags: Set<String>,
    mixedTagCounts: Map<String, Int>,
    scope: CoroutineScope,
    onRateChange: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val repo = LocalMediaRepository.current
    var allTags by remember { mutableStateOf<List<String>>(emptyList()) }
    var tagCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val counts = repo.getTagCountsCached()
            allTags = counts.entries.sortedByDescending { it.value }.map { it.key }
            tagCounts = counts
        }
    }
    RateAndTagSheet(
        batchCount = batch.size,
        currentRating = currentRating,
        ratingIsMixed = ratingIsMixed,
        onRate = { rating ->
            onRateChange(rating)
            scope.launch {
                val oldRatings = withContext(Dispatchers.IO) { repo.getRatingsForPaths(batch) }
                val successful = mutableListOf<String>()
                XmpBatch.run(context, batch.distinct(), onSuccess = { successful += it }) {
                    repo.writeRatingXmp(it, rating)
                }
                withContext(Dispatchers.IO) {
                    repo.setDbRatingBatch(successful, rating)
                    repo.refreshMetadataCache(successful)
                }
                if (successful.isNotEmpty()) {
                    UndoManager.push(
                        UndoAction(
                            paths = successful.toSet(),
                            type = UndoType.RATING_CHANGE,
                            ratingSnapshots = oldRatings.filterKeys { it in successful },
                        ),
                    )
                }
            }
        },
        initialTags = commonTags,
        mixedTagCounts = mixedTagCounts,
        onAddTag = { tag ->
            scope.launch {
                val successful = mutableSetOf<String>()
                XmpBatch.run(context, batch.distinct(), onSuccess = { successful += it }) {
                    repo.addTag(it, tag)
                }
                if (successful.isNotEmpty()) {
                    context.config.rememberRecentTag(tag)
                    UndoManager.push(UndoAction(successful, UndoType.TAG_ADD, extra = mapOf("tag" to tag)))
                }
            }
        },
        onRemoveTag = { tag ->
            scope.launch {
                val successful = mutableSetOf<String>()
                XmpBatch.run(context, batch.distinct(), onSuccess = { successful += it }) {
                    repo.removeTag(it, tag)
                }
                if (successful.isNotEmpty()) {
                    UndoManager.push(UndoAction(successful, UndoType.TAG_REMOVE, extra = mapOf("tag" to tag)))
                }
            }
        },
        suggestedTags = allTags,
        suggestedTagCounts = tagCounts,
        recentTags = context.config.recentTags,
        tagHierarchy = context.config.tagHierarchy,
        onSetTagParent = { child, parent ->
            val hierarchy = context.config.tagHierarchy.toMutableMap()
            if (parent.isNullOrBlank()) hierarchy.remove(child) else hierarchy[child] = parent
            context.config.tagHierarchy = hierarchy
        },
        onDismiss = onDismiss,
    )
}

package org.fossify.gallery.compose.screens

import android.content.res.Resources
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import org.fossify.gallery.compose.components.ConfirmDestructive
import org.fossify.gallery.compose.components.GalleryImage
import org.fossify.gallery.compose.components.MediaInfoDialog
import org.fossify.gallery.compose.components.RateAndTagSheet
import org.fossify.gallery.compose.components.RenameDialog
import org.fossify.gallery.compose.util.PeekState
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.MediaRepository
import org.fossify.gallery.helpers.TYPE_GIFS
import org.fossify.gallery.helpers.TYPE_IMAGES
import org.fossify.gallery.helpers.TYPE_RAWS
import org.fossify.gallery.helpers.TYPE_SVGS
import org.fossify.gallery.helpers.TYPE_VIDEOS
import org.fossify.gallery.helpers.UndoAction
import org.fossify.gallery.helpers.UndoManager
import org.fossify.gallery.helpers.UndoType
import org.fossify.gallery.helpers.withRecycleBinDeletePasswordCheck
import org.fossify.gallery.models.MediaCollection
import org.fossify.gallery.viewmodels.MediaUiState
import org.fossify.gallery.viewmodels.MediaViewModel

private const val PEEK_SCRIM_ALPHA = 0.85f
private const val PEEK_WIDTH_FRACTION = 0.92f
private const val PEEK_HEIGHT_FRACTION = 0.75f
private const val PEEK_THUMBNAIL_SIZE_PX = 1024
private const val COLLECTION_LIST_MAX_HEIGHT_DP = 360
private const val DATE_RANGE_TODAY = 1
private const val DATE_RANGE_WEEK = 2
private const val DATE_RANGE_MONTH = 3
private const val DATE_RANGE_YEAR = 4

@Composable
internal fun BoxScope.MediaPickerConfirmBar(
    selectedCount: Int,
    onClearSelection: () -> Unit,
    onConfirmPick: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 3.dp,
        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClearSelection) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(org.fossify.commons.R.string.cancel),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Text(
                "$selectedCount",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(start = 8.dp).weight(1f),
            )
            Button(onClick = onConfirmPick, enabled = selectedCount > 0) {
                Text(stringResource(R.string.pick_confirm_count, selectedCount))
            }
        }
    }
}

@Composable
internal fun MediaPeekOverlay(peekState: PeekState) {
    val peekPath = peekState.path ?: return
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
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { peekState.hide() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        val previewModifier = Modifier
            .fillMaxWidth(PEEK_WIDTH_FRACTION)
            .fillMaxHeight(PEEK_HEIGHT_FRACTION)
        if (peekState.isVideo) {
            VideoThumbnail(
                videoPath = peekPath,
                modifier = previewModifier,
                contentScale = ContentScale.Fit,
                thumbnailSize = PEEK_THUMBNAIL_SIZE_PX,
            )
        } else {
            GalleryImage(
                path = peekPath,
                contentDescription = peekPath.substringAfterLast('/'),
                modifier = previewModifier,
                contentScale = ContentScale.Fit,
                thumbnailSize = PEEK_THUMBNAIL_SIZE_PX,
            )
        }
    }
}

@Composable
internal fun MediaScreenDialogs(
    selectedPaths: Set<String>,
    state: MediaUiState,
    viewModel: MediaViewModel,
    repo: MediaRepository,
    scope: CoroutineScope,
    quickTagsVersion: Long,
    currentRating: Int,
    ratingIsMixed: Boolean,
    selectedCommonTags: Set<String>,
    selectedTagCounts: Map<String, Int>,
    showRateTagSheet: Boolean,
    showFolderPicker: Boolean,
    folderPickerIsMove: Boolean,
    showRenameDialog: Boolean,
    infoDialogPath: String?,
    showDeleteConfirm: Boolean,
    showAddToCollection: Boolean,
    collectionOptions: List<MediaCollection>,
    newCollectionName: String,
    onNewCollectionNameChange: (String) -> Unit,
    showSaveCollection: Boolean,
    showUpdateCollection: Boolean,
    activeCollectionName: String?,
    activeTagName: String?,
    activeFolderFilterName: String?,
    pathFilter: Set<String>?,
    excludePathFilter: Set<String>?,
    ratingFilter: Int,
    minSizeFilter: Long,
    dateRangeFilter: Int,
    typeFilter: Int,
    onSelectRating: (Int) -> Unit,
    onDismissRateTagSheet: () -> Unit,
    onDismissFolderPicker: () -> Unit,
    onRenameComplete: (Map<String, String>) -> Unit,
    onDismissRenameDialog: () -> Unit,
    onDismissInfoDialog: () -> Unit,
    onDismissDeleteConfirm: () -> Unit,
    onClearSelection: () -> Unit,
    onDismissAddToCollection: () -> Unit,
    onDismissSaveCollection: () -> Unit,
    onDismissUpdateCollection: () -> Unit,
) {
    val ctx = LocalContext.current
    val res = LocalResources.current

    if (showRateTagSheet) {
        val batch = selectedPaths.toList()
        LaunchedEffect(Unit) { viewModel.loadAllTags() }
        RateAndTagSheet(
            batchCount = batch.size,
            currentRating = currentRating,
            ratingIsMixed = ratingIsMixed,
            onRate = { rating ->
                onSelectRating(rating)
                viewModel.setRatingFor(batch, rating)
            },
            initialTags = selectedCommonTags,
            mixedTagCounts = selectedTagCounts,
            onAddTag = { viewModel.addTagFor(batch, it) },
            onRemoveTag = { viewModel.removeTagFor(batch, it) },
            suggestedTags = state.allTags,
            suggestedTagCounts = state.tagCounts,
            recentTags = remember(quickTagsVersion) { ctx.config.recentTags },
            tagHierarchy = ctx.config.tagHierarchy,
            onSetTagParent = { child, parent ->
                val hierarchy = ctx.config.tagHierarchy.toMutableMap()
                if (parent.isNullOrBlank()) hierarchy.remove(child) else hierarchy[child] = parent
                ctx.config.tagHierarchy = hierarchy
            },
            onDismiss = onDismissRateTagSheet,
        )
    }

    if (showFolderPicker) {
        FolderPickerSheet(
            isMoveOperation = folderPickerIsMove,
            sourcePaths = selectedPaths.toList(),
            onDismiss = {
                onDismissFolderPicker()
                onClearSelection()
            },
        )
    }

    if (showRenameDialog) {
        RenameDialog(
            paths = selectedPaths.toList(),
            onRenamed = onRenameComplete,
            onDismiss = {
                onDismissRenameDialog()
                viewModel.silentRefresh()
            },
        )
    }

    infoDialogPath?.let { path ->
        MediaInfoDialog(path = path, onDismiss = onDismissInfoDialog)
    }

    if (showDeleteConfirm) {
        val itemsCnt = selectedPaths.size
        val itemsText =
            res.getQuantityString(org.fossify.commons.R.plurals.delete_items, itemsCnt, itemsCnt)
        val confirmRes = if (ctx.config.useRecycleBin) {
            org.fossify.commons.R.string.move_to_recycle_bin_confirmation
        } else {
            org.fossify.commons.R.string.deletion_confirmation
        }
        ConfirmDestructive(
            title = stringResource(org.fossify.commons.R.string.delete),
            text = res.getString(confirmRes, itemsText),
            confirmLabel = stringResource(org.fossify.commons.R.string.delete),
            onConfirm = {
                onDismissDeleteConfirm()
                val deleted = selectedPaths.toSet()
                ctx.withRecycleBinDeletePasswordCheck {
                    viewModel.softDeletePaths(deleted)
                    UndoManager.push(UndoAction(paths = deleted, type = UndoType.DELETE))
                    onClearSelection()
                }
            },
            onDismiss = onDismissDeleteConfirm,
        )
    }

    if (showAddToCollection) {
        MediaAddToCollectionDialog(
            selectedPaths = selectedPaths,
            collectionOptions = collectionOptions,
            newCollectionName = newCollectionName,
            onNewCollectionNameChange = onNewCollectionNameChange,
            repo = repo,
            scope = scope,
            onAddComplete = {
                onClearSelection()
                onDismissAddToCollection()
            },
            onDismiss = onDismissAddToCollection,
        )
    }

    if (showSaveCollection) {
        MediaSaveCollectionDialog(
            selectedPaths = selectedPaths,
            pathFilter = pathFilter,
            excludePathFilter = excludePathFilter,
            activeTagName = activeTagName,
            activeFolderFilterName = activeFolderFilterName,
            ratingFilter = ratingFilter,
            minSizeFilter = minSizeFilter,
            dateRangeFilter = dateRangeFilter,
            typeFilter = typeFilter,
            repo = repo,
            scope = scope,
            onDismiss = onDismissSaveCollection,
        )
    }

    if (showUpdateCollection && activeCollectionName != null) {
        MediaUpdateCollectionDialog(
            activeCollectionName = activeCollectionName,
            pathFilter = pathFilter,
            excludePathFilter = excludePathFilter,
            activeTagName = activeTagName,
            ratingFilter = ratingFilter,
            minSizeFilter = minSizeFilter,
            dateRangeFilter = dateRangeFilter,
            typeFilter = typeFilter,
            repo = repo,
            scope = scope,
            onDismiss = onDismissUpdateCollection,
        )
    }
}

@Composable
private fun MediaAddToCollectionDialog(
    selectedPaths: Set<String>,
    collectionOptions: List<MediaCollection>,
    newCollectionName: String,
    onNewCollectionNameChange: (String) -> Unit,
    repo: MediaRepository,
    scope: CoroutineScope,
    onAddComplete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val res = LocalResources.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_to_collection)) },
        text = {
            Column(
                Modifier
                    .heightIn(max = COLLECTION_LIST_MAX_HEIGHT_DP.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (collectionOptions.isNotEmpty()) {
                    Text(
                        stringResource(R.string.choose_collection),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                    collectionOptions.forEach { collection ->
                        TextButton(
                            onClick = {
                                val batch = selectedPaths.toSet()
                                scope.launch {
                                    val added = withContext(Dispatchers.IO) {
                                        appendBatchToCollection(repo, collection.id, batch)
                                    }
                                    if (added) {
                                        onAddComplete()
                                        ctx.toast(
                                            res.getString(
                                                R.string.media_added_to_collection,
                                                batch.size,
                                                collection.name,
                                            ),
                                        )
                                    } else {
                                        ctx.toast(R.string.collection_add_failed)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(collection.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                }
                Text(
                    stringResource(R.string.new_collection),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                OutlinedTextField(
                    value = newCollectionName,
                    onValueChange = onNewCollectionNameChange,
                    singleLine = true,
                    label = { Text(stringResource(R.string.name)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = newCollectionName.isNotBlank() && selectedPaths.isNotEmpty(),
                onClick = {
                    val trimmed = newCollectionName.trim()
                    val batch = selectedPaths.toList()
                    scope.launch {
                        val saved = withContext(Dispatchers.IO) {
                            val id = repo.insertCollection(
                                MediaCollection(
                                    name = trimmed,
                                    mediaPaths = MediaCollection.createPathsJson(batch),
                                ),
                            )
                            if (id > 0L) {
                                repo.refreshCollectionsCache()
                                true
                            } else {
                                false
                            }
                        }
                        if (saved) {
                            onAddComplete()
                            ctx.toast(res.getString(R.string.collection_saved, trimmed))
                        } else {
                            ctx.toast(R.string.collection_add_failed)
                        }
                    }
                },
            ) {
                Text(stringResource(org.fossify.commons.R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(org.fossify.commons.R.string.cancel))
            }
        },
    )
}

private suspend fun appendBatchToCollection(
    repo: MediaRepository,
    collectionId: Long?,
    batch: Set<String>,
): Boolean {
    val current = repo.getCollections().firstOrNull { it.id == collectionId } ?: return false
    val merged = (current.getMediaPaths() + batch).distinct()
    val updated = current.copy(mediaPaths = MediaCollection.createPathsJson(merged))
    val id = repo.insertCollection(updated)
    if (id > 0L) {
        repo.refreshCollectionsCache()
        return true
    }
    return false
}

@Composable
private fun MediaSaveCollectionDialog(
    selectedPaths: Set<String>,
    pathFilter: Set<String>?,
    excludePathFilter: Set<String>?,
    activeTagName: String?,
    activeFolderFilterName: String?,
    ratingFilter: Int,
    minSizeFilter: Long,
    dateRangeFilter: Int,
    typeFilter: Int,
    repo: MediaRepository,
    scope: CoroutineScope,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val res = LocalResources.current
    val defaultName = remember(
        ratingFilter,
        activeTagName,
        typeFilter,
        minSizeFilter,
        dateRangeFilter,
        activeFolderFilterName,
    ) {
        buildDefaultCollectionName(
            res = res,
            ratingFilter = ratingFilter,
            activeTagName = activeTagName,
            typeFilter = typeFilter,
            minSizeFilter = minSizeFilter,
            dateRangeFilter = dateRangeFilter,
            activeFolderFilterName = activeFolderFilterName,
        )
    }
    var name by remember { mutableStateOf(defaultName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.save_as_collection)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(stringResource(R.string.name)) },
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val trimmed = name.trim().ifBlank { defaultName }
                    val coll = MediaCollection(
                        name = trimmed,
                        includedPaths = MediaCollection.createPathsJson(pathFilter?.toList().orEmpty()),
                        excludedPaths =
                            MediaCollection.createPathsJson(excludePathFilter?.toList().orEmpty()),
                        tagFilter = activeTagName.orEmpty(),
                        ratingFilter = ratingFilter,
                        minSize = minSizeFilter,
                        dateRange = dateRangeFilter,
                        typeFilter = typeFilter,
                        mediaPaths = MediaCollection.createPathsJson(selectedPaths.toList()),
                    )
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            repo.insertCollection(coll)
                            repo.refreshCollectionsCache()
                        }
                        ctx.toast(res.getString(R.string.collection_saved, trimmed))
                    }
                    onDismiss()
                },
            ) {
                Text(stringResource(org.fossify.commons.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(org.fossify.commons.R.string.cancel))
            }
        },
    )
}

@Composable
private fun MediaUpdateCollectionDialog(
    activeCollectionName: String,
    pathFilter: Set<String>?,
    excludePathFilter: Set<String>?,
    activeTagName: String?,
    ratingFilter: Int,
    minSizeFilter: Long,
    dateRangeFilter: Int,
    typeFilter: Int,
    repo: MediaRepository,
    scope: CoroutineScope,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val res = LocalResources.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.update_collection)) },
        text = { Text(stringResource(R.string.update_collection_confirm, activeCollectionName)) },
        confirmButton = {
            TextButton(
                onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            val existing =
                                repo.getCollections().find { it.name == activeCollectionName }
                            if (existing != null) {
                                repo.insertCollection(
                                    existing.copy(
                                        includedPaths = MediaCollection.createPathsJson(
                                            pathFilter?.toList().orEmpty(),
                                        ),
                                        excludedPaths = MediaCollection.createPathsJson(
                                            excludePathFilter?.toList().orEmpty(),
                                        ),
                                        tagFilter = activeTagName.orEmpty(),
                                        ratingFilter = ratingFilter,
                                        minSize = minSizeFilter,
                                        dateRange = dateRangeFilter,
                                        typeFilter = typeFilter,
                                    ),
                                )
                                repo.refreshCollectionsCache()
                            }
                        }
                        ctx.toast(res.getString(R.string.collection_updated, activeCollectionName))
                    }
                    onDismiss()
                },
            ) {
                Text(stringResource(org.fossify.commons.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(org.fossify.commons.R.string.cancel))
            }
        },
    )
}

private fun buildDefaultCollectionName(
    res: Resources,
    ratingFilter: Int,
    activeTagName: String?,
    typeFilter: Int,
    minSizeFilter: Long,
    dateRangeFilter: Int,
    activeFolderFilterName: String?,
): String = buildList {
    if (ratingFilter > 0) add("★$ratingFilter")
    activeTagName?.takeIf { it.isNotBlank() }?.let { add("#$it") }
    when (typeFilter) {
        TYPE_IMAGES -> add(res.getString(R.string.images))
        TYPE_VIDEOS -> add(res.getString(R.string.videos))
        TYPE_GIFS -> add(res.getString(R.string.gif))
        TYPE_RAWS -> add(res.getString(R.string.raw))
        TYPE_SVGS -> add(res.getString(R.string.svg))
    }
    if (minSizeFilter > 0) add("> ${formatFileSize(minSizeFilter)}")
    when (dateRangeFilter) {
        DATE_RANGE_TODAY -> add(res.getString(R.string.today))
        DATE_RANGE_WEEK -> add(res.getString(R.string.date_range_last_7_days))
        DATE_RANGE_MONTH -> add(res.getString(R.string.date_range_last_30_days))
        DATE_RANGE_YEAR -> add(res.getString(R.string.date_range_last_year))
    }
    activeFolderFilterName?.let { add(it) }
}.joinToString(" · ").ifBlank { res.getString(R.string.save_as_collection) }

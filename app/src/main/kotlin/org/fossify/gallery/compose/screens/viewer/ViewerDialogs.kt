package org.fossify.gallery.compose.screens.viewer

import android.app.Activity
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.commons.extensions.toast
import org.fossify.gallery.R
import org.fossify.gallery.compose.components.ConfirmDestructive
import org.fossify.gallery.compose.components.MediaInfoDialog
import org.fossify.gallery.compose.components.RateAndTagSheet
import org.fossify.gallery.compose.components.RenameDialog
import org.fossify.gallery.compose.screens.FolderPickerSheet
import org.fossify.gallery.compose.screens.framegrabber.FrameGrabberScreen
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.openEditor
import org.fossify.gallery.helpers.MediaRepository

private val JPEG_EXTENSIONS = setOf("jpg", "jpeg")

@Composable
internal fun ViewerDialogsHost(
    currentPath: String,
    currentIsVideo: Boolean,
    currentRating: Int,
    itemCount: Int,
    showRatingOverlay: Boolean,
    slideshowActive: Boolean,
    videoScalingMode: Int,
    videoPlaybackSpeed: Float,
    showActionSheet: Boolean,
    showSlideshowDialog: Boolean,
    showRateAndTagSheet: Boolean,
    showFrameGrabber: Boolean,
    showVideoTrimmer: Boolean,
    showVideoSettings: Boolean,
    showFolderPicker: Boolean,
    pendingFolderPickerIsMove: Boolean,
    showRenameDialog: Boolean,
    infoDialogPath: String?,
    showDeleteConfirm: Boolean,
    editorTags: Set<String>,
    quickTags: List<String>,
    editorTagSuggestions: List<Pair<String, Int>>,
    recentTags: List<String>,
    repo: MediaRepository,
    scope: CoroutineScope,
    onRate: (Int) -> Unit,
    onToggleRatingOverlay: () -> Unit,
    onDismissActionSheet: () -> Unit,
    onRequestRename: () -> Unit,
    onRequestCopy: () -> Unit,
    onRequestMove: () -> Unit,
    onRequestInfo: () -> Unit,
    onRequestTrimVideo: () -> Unit,
    onRequestVideoSettings: () -> Unit,
    onCycleVideoPlaybackSpeed: () -> Unit,
    onEnterVideoPip: () -> Unit,
    onRequestSaveFrame: () -> Unit,
    onRequestFrameGrabber: () -> Unit,
    onToggleSlideshow: () -> Unit,
    onRequestTags: () -> Unit,
    onStartSlideshow: (intervalSec: Int, loopEnabled: Boolean, randomEnabled: Boolean) -> Unit,
    onDismissSlideshowDialog: () -> Unit,
    onDismissRateAndTagSheet: () -> Unit,
    onCloseFrameGrabber: (deletedOriginal: Boolean) -> Unit,
    onDismissVideoTrimmer: () -> Unit,
    onSaveTrim: (String) -> Unit,
    onSelectVideoScalingMode: (Int) -> Unit,
    onDismissVideoSettings: () -> Unit,
    onDismissFolderPicker: () -> Unit,
    onDismissRenameDialog: () -> Unit,
    onCompleteRename: (Map<String, String>) -> Unit,
    onDismissInfoDialog: () -> Unit,
    onConfirmDelete: () -> Unit,
    onDismissDeleteConfirm: () -> Unit,
) {
    val ctx = LocalContext.current

    if (showActionSheet) {
        ViewerActionSheet(
            currentPath = currentPath,
            currentIsVideo = currentIsVideo,
            currentRating = currentRating,
            showRatingOverlay = showRatingOverlay,
            slideshowActive = slideshowActive,
            canRunSlideshow = itemCount > 1,
            videoPlaybackSpeed = videoPlaybackSpeed,
            onRate = onRate,
            onToggleRatingOverlay = onToggleRatingOverlay,
            onRename = onRequestRename,
            onCopy = onRequestCopy,
            onMove = onRequestMove,
            onInfo = onRequestInfo,
            onTrimVideo = onRequestTrimVideo,
            onVideoDisplaySettings = onRequestVideoSettings,
            onCycleVideoPlaybackSpeed = onCycleVideoPlaybackSpeed,
            onEnterVideoPip = onEnterVideoPip,
            onSaveFrame = onRequestSaveFrame,
            onOpenFrameGrabber = onRequestFrameGrabber,
            onEditImage = {
                (ctx as? Activity)?.openEditor(currentPath)
                onDismissActionSheet()
            },
            onToggleSlideshow = onToggleSlideshow,
            onOpenTags = onRequestTags,
            onDismiss = onDismissActionSheet,
        )
    }

    if (showSlideshowDialog) {
        ViewerSlideshowDialog(
            onStart = onStartSlideshow,
            onDismiss = onDismissSlideshowDialog,
        )
    }

    if (showRateAndTagSheet) {
        ViewerRateAndTagSheet(
            editorPath = currentPath,
            currentRating = currentRating,
            editorTags = editorTags,
            quickTags = quickTags,
            editorTagSuggestions = editorTagSuggestions,
            recentTags = recentTags,
            repo = repo,
            scope = scope,
            onRate = onRate,
            onDismiss = onDismissRateAndTagSheet,
        )
    }

    if (showFrameGrabber) {
        FrameGrabberScreen(currentPath, onDismiss = onCloseFrameGrabber)
    }

    if (showVideoTrimmer) {
        VideoTrimmerSheet(
            videoPath = currentPath,
            onDismiss = onDismissVideoTrimmer,
            onTrimSaved = onSaveTrim,
        )
    }

    if (showVideoSettings) {
        ViewerVideoScalingDialog(
            currentMode = videoScalingMode,
            onSelectMode = onSelectVideoScalingMode,
            onDismiss = onDismissVideoSettings,
        )
    }

    if (showFolderPicker) {
        FolderPickerSheet(
            isMoveOperation = pendingFolderPickerIsMove,
            sourcePaths = listOf(currentPath),
            onDismiss = onDismissFolderPicker,
        )
    }

    if (showRenameDialog) {
        RenameDialog(
            paths = listOf(currentPath),
            onDismiss = onDismissRenameDialog,
            onRenamed = onCompleteRename,
        )
    }

    infoDialogPath?.let { path ->
        MediaInfoDialog(path = path, onDismiss = onDismissInfoDialog)
    }

    if (showDeleteConfirm) {
        val questionRes = if (ctx.config.useRecycleBin) {
            org.fossify.commons.R.string.are_you_sure_recycle_bin
        } else {
            org.fossify.commons.R.string.are_you_sure_delete
        }
        ConfirmDestructive(
            title = stringResource(org.fossify.commons.R.string.delete),
            text = stringResource(questionRes),
            confirmLabel = stringResource(org.fossify.commons.R.string.delete),
            onConfirm = onConfirmDelete,
            onDismiss = onDismissDeleteConfirm,
        )
    }
}

@Composable
private fun ViewerRateAndTagSheet(
    editorPath: String,
    currentRating: Int,
    editorTags: Set<String>,
    quickTags: List<String>,
    editorTagSuggestions: List<Pair<String, Int>>,
    recentTags: List<String>,
    repo: MediaRepository,
    scope: CoroutineScope,
    onRate: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val isJpeg = editorPath.substringAfterLast('.', "").lowercase() in JPEG_EXTENSIONS
    RateAndTagSheet(
        batchCount = 1,
        currentRating = currentRating,
        onRate = onRate,
        initialTags = editorTags,
        onAddTag = { tag ->
            scope.launch(Dispatchers.IO) {
                val saved = repo.addTag(editorPath, tag)
                withContext(Dispatchers.Main) {
                    if (saved) {
                        ctx.config.rememberRecentTag(tag)
                    } else {
                        ctx.toast(R.string.tag_write_failed, Toast.LENGTH_SHORT)
                    }
                }
            }
        },
        onRemoveTag = { tag ->
            scope.launch(Dispatchers.IO) {
                if (!repo.removeTag(editorPath, tag)) {
                    withContext(Dispatchers.Main) {
                        ctx.toast(R.string.tag_write_failed, Toast.LENGTH_SHORT)
                    }
                }
            }
        },
        suggestedTags = (quickTags + editorTagSuggestions.map { it.first }).distinct(),
        suggestedTagCounts = editorTagSuggestions.toMap(),
        recentTags = recentTags,
        tagHierarchy = ctx.config.tagHierarchy,
        metadataStorageHint = if (isJpeg) null else stringResource(R.string.metadata_sidecar_hint),
        onSetTagParent = { child, parent ->
            val hierarchy = ctx.config.tagHierarchy.toMutableMap()
            if (parent.isNullOrBlank()) hierarchy.remove(child) else hierarchy[child] = parent
            ctx.config.tagHierarchy = hierarchy
        },
        onDismiss = onDismiss,
    )
}

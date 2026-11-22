package org.fossify.gallery.compose.screens.editor

import android.app.Activity
import android.graphics.Bitmap
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import coil.size.Size
import com.canhub.cropper.CropImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.commons.extensions.getDoesFilePathExist
import org.fossify.commons.extensions.getRealPathFromURI
import org.fossify.commons.extensions.sharePathIntent
import org.fossify.commons.extensions.toast
import org.fossify.gallery.BuildConfig
import org.fossify.gallery.R
import org.fossify.gallery.extensions.proposeNewFilePath
import org.fossify.gallery.extensions.resolveUriScheme
import org.fossify.gallery.models.FilterItem
import org.fossify.gallery.views.EditorDrawCanvas
import java.io.File

internal data class EditorSaveInputs(
    val sourceUri: () -> Uri?,
    val saveUri: () -> Uri?,
    val filterItems: () -> List<FilterItem>,
    val exif: () -> ExifInterface?,
    val cropView: () -> CropImageView?,
    val drawView: () -> EditorDrawCanvas?,
)

internal class EditorSaveCoordinator(
    private val activity: Activity,
    private val scope: CoroutineScope,
    private val stateProvider: () -> EditorUiState,
    private val updateState: ((EditorUiState) -> EditorUiState) -> Unit,
    private val inputs: EditorSaveInputs,
    private val onSaved: (path: String) -> Unit,
) {
    var pendingResizeWidth: Int = 0
    var pendingResizeHeight: Int = 0

    private val sourceUriProvider get() = inputs.sourceUri
    private val saveUriProvider get() = inputs.saveUri
    private val filterItemsProvider get() = inputs.filterItems
    private val exifProvider get() = inputs.exif
    private val cropViewProvider get() = inputs.cropView
    private val drawViewProvider get() = inputs.drawView

    fun startSaveFlow(overwrite: Boolean) {
        val targetSaveUri = saveUriProvider() ?: return
        when (stateProvider().primaryMode) {
            EditorPrimaryMode.CROP_ROTATE -> captureCropAndSave(overwrite, targetSaveUri)
            EditorPrimaryMode.DRAW -> {
                val drawn = drawViewProvider()?.getBitmap()
                if (drawn == null) {
                    activity.toast(R.string.image_editing_failed)
                    return
                }
                if (overwrite) saveToResolvedUri(drawn, targetSaveUri) else openSaveAsDialog(targetSaveUri, drawn)
            }
            EditorPrimaryMode.FILTER -> {
                if (overwrite) {
                    renderFilteredOriginalAndSave(targetPath = null, overwriteUri = targetSaveUri)
                } else {
                    openSaveAsDialog(targetSaveUri, pendingBitmap = null)
                }
            }
        }
    }

    fun confirmSaveAs(folder: String, filename: String, extension: String, pendingBitmap: Bitmap?) {
        val targetPath = "${folder.trimEnd('/')}/$filename.$extension"
        if (activity.getDoesFilePathExist(targetPath)) {
            updateState { it.copy(activeDialog = EditorDialog.OverwriteFileConfirm(targetPath, pendingBitmap)) }
            return
        }
        updateState { it.copy(activeDialog = null) }
        writePendingOrRenderedBitmap(targetPath, pendingBitmap)
    }

    fun writePendingOrRenderedBitmap(targetPath: String, pendingBitmap: Bitmap?) {
        if (pendingBitmap != null) {
            persistBitmapToPath(pendingBitmap, targetPath)
        } else {
            renderFilteredOriginalAndSave(targetPath = targetPath, overwriteUri = null)
        }
    }

    fun shareCurrentEdit() {
        val currentSaveUri = saveUriProvider() ?: sourceUriProvider() ?: return
        updateState { it.copy(isSaving = true) }
        scope.launch {
            val bitmap = captureCurrentModeBitmap()
            if (activity.isFinishing || activity.isDestroyed) return@launch
            if (bitmap == null) {
                updateState { it.copy(isSaving = false) }
                activity.toast(org.fossify.commons.R.string.unknown_error_occurred)
                return@launch
            }
            val cachedPath = EditorSaveHelper.writeBitmapToCacheForShare(activity, currentSaveUri, bitmap)
            if (activity.isFinishing || activity.isDestroyed) return@launch
            updateState { it.copy(isSaving = false) }
            if (cachedPath != null) {
                activity.sharePathIntent(cachedPath, BuildConfig.APPLICATION_ID)
            } else {
                activity.toast(org.fossify.commons.R.string.unknown_error_occurred)
            }
        }
    }

    private fun captureCropAndSave(overwrite: Boolean, targetSaveUri: Uri) {
        val view = cropViewProvider() ?: return
        updateState { it.copy(isSaving = true) }
        scope.launch {
            val cropped = try {
                withContext(Dispatchers.Default) { view.getCroppedImage() }
            } catch (_: OutOfMemoryError) {
                updateState { it.copy(isSaving = false) }
                activity.toast(org.fossify.commons.R.string.out_of_memory_error)
                return@launch
            }
            if (activity.isFinishing || activity.isDestroyed) return@launch
            updateState { it.copy(isSaving = false) }
            if (cropped == null) {
                activity.toast(R.string.image_editing_failed)
                return@launch
            }
            if (stateProvider().isCropIntent || overwrite) {
                saveToResolvedUri(cropped, targetSaveUri)
            } else {
                openSaveAsDialog(targetSaveUri, cropped)
            }
        }
    }

    private fun saveToResolvedUri(bitmap: Bitmap, targetUri: Uri) {
        activity.resolveUriScheme(
            uri = targetUri,
            onPath = { path -> persistBitmapToPath(bitmap, path) },
            onContentUri = { contentUri ->
                val realPath = runCatching { activity.getRealPathFromURI(contentUri) }
                    .getOrNull()
                    ?.takeIf { it.isNotBlank() && File(it).exists() }
                if (realPath != null) {
                    persistBitmapToPath(bitmap, realPath)
                } else {
                    openSaveAsDialog(contentUri, bitmap)
                }
            },
        )
    }

    private fun openSaveAsDialog(targetUri: Uri, pendingBitmap: Bitmap?) {
        activity.resolveUriScheme(
            uri = targetUri,
            onPath = { path ->
                val defaults = EditorSaveHelper.prepareSaveAsDefaults(activity, path, appendSuffix = true)
                updateState {
                    it.copy(
                        activeDialog = EditorDialog.SaveAs(
                            folderPath = defaults.folderPath,
                            filename = defaults.filename,
                            extension = defaults.extension,
                            pendingBitmap = pendingBitmap,
                        ),
                    )
                }
            },
            onContentUri = { contentUri ->
                val (proposedPath, append) = activity.proposeNewFilePath(contentUri)
                val defaults = EditorSaveHelper.prepareSaveAsDefaults(activity, proposedPath, appendSuffix = append)
                updateState {
                    it.copy(
                        activeDialog = EditorDialog.SaveAs(
                            folderPath = defaults.folderPath,
                            filename = defaults.filename,
                            extension = defaults.extension,
                            pendingBitmap = pendingBitmap,
                        ),
                    )
                }
            },
        )
    }

    private fun renderFilteredOriginalAndSave(targetPath: String?, overwriteUri: Uri?) {
        val srcUri = sourceUriProvider() ?: return
        val state = stateProvider()
        val filter = filterItemsProvider().getOrNull(state.selectedFilterIndex)?.filter
        updateState { it.copy(isSaving = true) }
        scope.launch {
            val rendered = try {
                withContext(Dispatchers.Default) {
                    val original = EditorSaveHelper.loadBitmapWithCoil(
                        context = activity,
                        uri = srcUri,
                        size = Size.ORIGINAL,
                        mutable = true,
                    ) ?: return@withContext null
                    if (state.selectedFilterIndex > 0) filter?.processFilter(original)
                    val adjusted = state.colorAdjustments.applyToBitmap(original)
                    if (adjusted !== original) original.recycle()
                    adjusted
                }
            } catch (_: OutOfMemoryError) {
                updateState { it.copy(isSaving = false) }
                activity.toast(org.fossify.commons.R.string.out_of_memory_error)
                return@launch
            }
            if (activity.isFinishing || activity.isDestroyed) return@launch
            if (rendered == null) {
                updateState { it.copy(isSaving = false) }
                activity.toast(R.string.image_editing_failed)
                return@launch
            }
            if (targetPath != null) {
                persistBitmapToPath(rendered, targetPath)
            } else if (overwriteUri != null) {
                saveToResolvedUri(rendered, overwriteUri)
            }
        }
    }

    private fun persistBitmapToPath(bitmap: Bitmap, targetPath: String) {
        updateState { it.copy(isSaving = true) }
        scope.launch {
            val outcome = EditorSaveHelper.saveBitmapToPath(
                context = activity,
                sourceUri = sourceUriProvider(),
                bitmap = bitmap,
                targetPath = targetPath,
                resizeWidth = pendingResizeWidth,
                resizeHeight = pendingResizeHeight,
                cachedExif = exifProvider(),
            )
            pendingResizeWidth = 0
            pendingResizeHeight = 0
            if (activity.isFinishing || activity.isDestroyed) return@launch
            when (outcome) {
                is SaveBitmapOutcome.Saved -> {
                    if (outcome.backedUpOriginal) activity.toast(R.string.edit_original_backed_up)
                    EditorSaveHelper.finalizeSavedPath(activity, outcome.path) {
                        activity.toast(org.fossify.commons.R.string.file_saved)
                        onSaved(outcome.path)
                    }
                }
                is SaveBitmapOutcome.Failed -> {
                    updateState { it.copy(isSaving = false) }
                    activity.toast(outcome.messageRes)
                }
            }
        }
    }

    private suspend fun captureCurrentModeBitmap(): Bitmap? {
        val state = stateProvider()
        return when (state.primaryMode) {
            EditorPrimaryMode.CROP_ROTATE -> withContext(Dispatchers.Default) {
                runCatching { cropViewProvider()?.getCroppedImage() }.getOrNull()
            }
            EditorPrimaryMode.DRAW -> drawViewProvider()?.getBitmap()
            EditorPrimaryMode.FILTER -> {
                val srcUri = sourceUriProvider() ?: return null
                val filter = filterItemsProvider().getOrNull(state.selectedFilterIndex)?.filter
                withContext(Dispatchers.Default) {
                    val original = EditorSaveHelper.loadBitmapWithCoil(
                        context = activity,
                        uri = srcUri,
                        size = Size.ORIGINAL,
                        mutable = true,
                    ) ?: return@withContext null
                    if (state.selectedFilterIndex > 0) filter?.processFilter(original)
                    val adjusted = state.colorAdjustments.applyToBitmap(original)
                    if (adjusted !== original) original.recycle()
                    adjusted
                }
            }
        }
    }
}

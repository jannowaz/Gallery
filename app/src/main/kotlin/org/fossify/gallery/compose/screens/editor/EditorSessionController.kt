package org.fossify.gallery.compose.screens.editor

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Point
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.IntentCompat
import androidx.core.net.toUri
import androidx.exifinterface.media.ExifInterface
import coil.size.Scale
import coil.size.Size
import com.canhub.cropper.CropImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.commons.extensions.getRealPathFromURI
import org.fossify.commons.extensions.isPathOnOTG
import org.fossify.commons.extensions.toast
import org.fossify.commons.helpers.REAL_FILE_PATH
import org.fossify.gallery.R
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.openEditor
import org.fossify.gallery.extensions.readExif
import org.fossify.gallery.helpers.ASPECT_RATIO_ONE_ONE
import org.fossify.gallery.helpers.ASPECT_RATIO_OTHER
import org.fossify.gallery.helpers.ColorModeHelper
import org.fossify.gallery.helpers.IntentSecurity
import org.fossify.gallery.models.FilterItem
import org.fossify.gallery.views.EditorDrawCanvas
import java.io.File

private const val ASPECT_X = "aspectX"
private const val ASPECT_Y = "aspectY"
private const val CROP_EXTRA = "crop"
private const val ROTATE_DEGREES = 90
private const val RIGHT_ANGLE_180 = 180
private const val PREVIEW_MAX_EDGE_PX = 2048
private const val MAX_BRUSH_PERCENT = 100

internal class EditorSessionController(
    private val activity: Activity,
    private val scope: CoroutineScope,
    private val onOpenThirdPartyEditor: () -> Unit,
    private val onSaved: (path: String) -> Unit,
) {
    var uiState by mutableStateOf(EditorUiState())
        private set
    var imageUri by mutableStateOf<Uri?>(null)
        private set
    var previewFilteredBitmap by mutableStateOf<Bitmap?>(null)
        private set
    var drawBackgroundBitmap by mutableStateOf<Bitmap?>(null)

    var cropImageView: CropImageView? = null
    var drawCanvasView: EditorDrawCanvas? = null

    private var originalUri: Uri? = null
    private var saveUri: Uri? = null
    private var previewBaseBitmap: Bitmap? = null
    private var filterItems: List<FilterItem> = emptyList()
    @Volatile
    private var cachedExif: ExifInterface? = null

    private val saveCoordinator = EditorSaveCoordinator(
        activity = activity,
        scope = scope,
        stateProvider = { uiState },
        updateState = { transform -> uiState = transform(uiState) },
        inputs = EditorSaveInputs(
            sourceUri = { imageUri },
            saveUri = { saveUri },
            filterItems = { filterItems },
            exif = { cachedExif },
            cropView = { cropImageView },
            drawView = { drawCanvasView },
        ),
        onSaved = onSaved,
    )

    fun configureInitialExtras(intent: Intent) {
        val extras = intent.extras
        val isCropIntent = extras?.getString(CROP_EXTRA) == "true" || extras?.getBoolean(CROP_EXTRA) == true
        val lockSquare = isCropIntent &&
            extras.containsKey(ASPECT_X) &&
            extras.containsKey(ASPECT_Y) &&
            extras.getInt(ASPECT_X) == extras.getInt(ASPECT_Y)
        uiState = uiState.copy(
            isCropIntent = isCropIntent,
            lockSquareCrop = lockSquare,
            drawColor = activity.config.lastEditorDrawColor,
            brushSizePercent = activity.config.lastEditorBrushSize.coerceIn(1, MAX_BRUSH_PERCENT),
        )
    }

    fun initializeFromIntent(intent: Intent): Boolean {
        val incomingUri = intent.data
        if (incomingUri == null) {
            activity.toast(R.string.invalid_image_path)
            return false
        }
        if (!IntentSecurity.isSafeIncomingUri(activity, incomingUri)) {
            activity.toast(R.string.unknown_file_location)
            return false
        }
        val extraOutput = IntentCompat.getParcelableExtra(intent, MediaStore.EXTRA_OUTPUT, Uri::class.java)
        if (extraOutput != null && !IntentSecurity.isSafeOutputUri(activity, extraOutput)) {
            activity.toast(R.string.unknown_file_location)
            return false
        }
        originalUri = incomingUri
        val resolvedUri = resolveEditableUri(activity, intent, incomingUri)
        imageUri = resolvedUri
        saveUri = extraOutput ?: resolvedUri

        val (savedRatio, otherPair) = restoreInitialAspectRatio(activity, uiState.lockSquareCrop)
        uiState = uiState.copy(
            hasImage = true,
            aspectRatio = savedRatio,
            lastOtherAspectRatio = otherPair,
            showAspectRatioStrip = !uiState.lockSquareCrop,
        )
        loadPreviewBitmap(resolvedUri)
        return true
    }

    fun updateDrawHistory(canUndo: Boolean) {
        uiState = uiState.copy(canUndoDraw = canUndo)
    }

    fun buildCallbacks(): EditorCallbacks = EditorCallbacks(
        onBack = { activity.finish() },
        onSaveClick = { saveCoordinator.startSaveFlow(overwrite = false) },
        onOverwriteOriginalClick = { uiState = uiState.copy(activeDialog = EditorDialog.OverwriteOriginalConfirm) },
        onAdjustColorsClick = {
            if (uiState.primaryMode != EditorPrimaryMode.FILTER) selectPrimaryMode(EditorPrimaryMode.FILTER)
            uiState = uiState.copy(activeDialog = EditorDialog.ColorAdjustments(uiState.colorAdjustments))
        },
        onEditWithClick = {
            imageUri?.let {
                activity.openEditor(it.toString(), true)
                onOpenThirdPartyEditor()
            }
        },
        onShareClick = { saveCoordinator.shareCurrentEdit() },
        onSelectPrimaryMode = { mode -> selectPrimaryMode(mode) },
        onRotateRight = { cropImageView?.rotateImage(ROTATE_DEGREES) },
        onFlipHorizontal = { cropImageView?.flipImageHorizontally() },
        onFlipVertical = { cropImageView?.flipImageVertically() },
        onToggleAspectRatioStrip = { uiState = uiState.copy(showAspectRatioStrip = !uiState.showAspectRatioStrip) },
        onResizeClick = { openResizeDialog() },
        onSelectAspectRatio = { ratio -> updateAspectRatio(ratio, uiState.lastOtherAspectRatio, persistOther = false) },
        onOpenOtherAspectRatio = {
            uiState = uiState.copy(activeDialog = EditorDialog.OtherAspectRatio(uiState.lastOtherAspectRatio))
        },
        onSelectFilter = { index -> applyFilterSelection(index) },
        onOpenBrushColorPicker = {
            uiState = uiState.copy(activeDialog = EditorDialog.BrushColorPicker(uiState.drawColor))
        },
        onBrushSizeChange = { size ->
            activity.config.lastEditorBrushSize = size
            uiState = uiState.copy(brushSizePercent = size)
            drawCanvasView?.updateBrushSize(size)
        },
        onUndoDraw = { drawCanvasView?.undo() },
        onDismissDialog = { uiState = uiState.copy(activeDialog = null) },
        onConfirmSaveAs = { folder, name, ext, pendingBitmap ->
            saveCoordinator.confirmSaveAs(folder, name, ext, pendingBitmap)
        },
        onConfirmOverwriteOriginal = {
            uiState = uiState.copy(activeDialog = null)
            saveCoordinator.startSaveFlow(overwrite = true)
        },
        onConfirmOverwriteFile = { targetPath, pendingBitmap ->
            uiState = uiState.copy(activeDialog = null)
            saveCoordinator.writePendingOrRenderedBitmap(targetPath, pendingBitmap)
        },
        onConfirmResize = { width, height ->
            uiState = uiState.copy(activeDialog = null)
            saveCoordinator.pendingResizeWidth = width
            saveCoordinator.pendingResizeHeight = height
            saveCoordinator.startSaveFlow(overwrite = false)
        },
        onConfirmOtherAspectRatio = { pair -> updateAspectRatio(ASPECT_RATIO_OTHER, pair, persistOther = true) },
        onOpenCustomAspectRatio = {
            uiState = uiState.copy(activeDialog = EditorDialog.CustomAspectRatio(uiState.lastOtherAspectRatio))
        },
        onConfirmCustomAspectRatio = { pair -> updateAspectRatio(ASPECT_RATIO_OTHER, pair, persistOther = true) },
        onPreviewColorAdjustments = { params -> uiState = uiState.copy(colorAdjustments = params) },
        onConfirmColorAdjustments = { params ->
            uiState = uiState.copy(colorAdjustments = params, activeDialog = null)
        },
        onCancelColorAdjustments = { previous ->
            uiState = uiState.copy(colorAdjustments = previous, activeDialog = null)
        },
        onConfirmBrushColor = { color ->
            activity.config.lastEditorDrawColor = color
            uiState = uiState.copy(drawColor = color, activeDialog = null)
            drawCanvasView?.updateColor(color)
        },
    )

    private fun selectPrimaryMode(mode: EditorPrimaryMode) {
        if (uiState.primaryMode == mode) return
        uiState = uiState.copy(primaryMode = mode)
        if (mode == EditorPrimaryMode.FILTER && filterItems.isEmpty() && !uiState.isLoadingFilters) {
            val uri = imageUri ?: return
            uiState = uiState.copy(isLoadingFilters = true)
            val thumbSize = activity.resources.getDimensionPixelSize(R.dimen.bottom_filters_thumbnail_size)
            scope.launch {
                val items = EditorSaveHelper.buildFilterItems(activity, uri, thumbSize)
                if (activity.isFinishing || activity.isDestroyed) return@launch
                filterItems = items
                val entries = items.mapIndexed { idx, item ->
                    EditorFilterEntry(index = idx, name = item.filter.name.orEmpty(), thumbnail = item.bitmap)
                }
                uiState = uiState.copy(filters = entries, isLoadingFilters = false)
            }
        }
    }

    private fun updateAspectRatio(ratio: Int, otherPair: Pair<Float, Float>?, persistOther: Boolean) {
        if (persistOther && otherPair != null) {
            activity.config.lastEditorCropOtherAspectRatioX = otherPair.first
            activity.config.lastEditorCropOtherAspectRatioY = otherPair.second
        }
        activity.config.lastEditorCropAspectRatio = ratio
        uiState = uiState.copy(
            aspectRatio = ratio,
            lastOtherAspectRatio = otherPair,
            activeDialog = if (persistOther) null else uiState.activeDialog,
        )
    }

    private fun applyFilterSelection(index: Int) {
        val base = previewBaseBitmap ?: return
        val filterItem = filterItems.getOrNull(index) ?: return
        uiState = uiState.copy(selectedFilterIndex = index)
        scope.launch {
            val processed = withContext(Dispatchers.Default) {
                val copy = base.copy(Bitmap.Config.ARGB_8888, true)
                if (index == 0) copy else filterItem.filter.processFilter(copy)
            }
            if (!activity.isFinishing && !activity.isDestroyed) {
                previewFilteredBitmap = processed
            }
        }
    }

    private fun openResizeDialog() {
        val view = cropImageView
        val rect = view?.cropRect
        val area = if (view != null && rect != null) {
            val rot = view.rotatedDegrees
            if (rot == 0 || rot == RIGHT_ANGLE_180) Point(rect.width(), rect.height())
            else Point(rect.height(), rect.width())
        } else {
            null
        }
        if (area == null) {
            activity.toast(org.fossify.commons.R.string.unknown_error_occurred)
        } else {
            uiState = uiState.copy(activeDialog = EditorDialog.Resize(area.x, area.y))
        }
    }

    private fun loadPreviewBitmap(uri: Uri) {
        scope.launch {
            var loaded = EditorSaveHelper.loadBitmapWithCoil(
                context = activity,
                uri = uri,
                size = Size(PREVIEW_MAX_EDGE_PX, PREVIEW_MAX_EDGE_PX),
                scale = Scale.FIT,
            )
            if (loaded == null && uri != originalUri && originalUri != null) {
                imageUri = originalUri
                loaded = EditorSaveHelper.loadBitmapWithCoil(
                    context = activity,
                    uri = originalUri!!,
                    size = Size(PREVIEW_MAX_EDGE_PX, PREVIEW_MAX_EDGE_PX),
                    scale = Scale.FIT,
                )
            }
            if (activity.isFinishing || activity.isDestroyed) return@launch
            if (loaded != null) {
                previewBaseBitmap = loaded
                previewFilteredBitmap = loaded
                ColorModeHelper.setColorModeForImage(activity, loaded, activity.config.ultraHdrRendering)
            } else {
                ColorModeHelper.resetColorMode(activity)
            }
            cachedExif = withContext(Dispatchers.IO) {
                imageUri?.let { runCatching { activity.readExif(it) }.getOrNull() }
            }
        }
    }
}

private fun resolveEditableUri(activity: Activity, intent: Intent, incomingUri: Uri): Uri {
    val extras = intent.extras
    if (extras?.containsKey(REAL_FILE_PATH) == true) {
        val realPath = extras.getString(REAL_FILE_PATH)
        if (!realPath.isNullOrBlank() && !activity.isPathOnOTG(realPath)) {
            val parsed = if (realPath.startsWith("file:/")) realPath.toUri().path else realPath
            if (IntentSecurity.isSafeExternalPath(activity, parsed)) {
                return Uri.fromFile(File(parsed!!))
            }
        }
        return incomingUri
    }
    val realPath = activity.getRealPathFromURI(incomingUri)
    return if (realPath != null && IntentSecurity.isSafeExternalPath(activity, realPath)) {
        Uri.fromFile(File(realPath))
    } else {
        incomingUri
    }
}

private fun restoreInitialAspectRatio(activity: Activity, lockSquare: Boolean): Pair<Int, Pair<Float, Float>?> {
    if (lockSquare) return ASPECT_RATIO_ONE_ONE to null
    val savedRatio = activity.config.lastEditorCropAspectRatio
    val otherPair = if (savedRatio == ASPECT_RATIO_OTHER) {
        val x = activity.config.lastEditorCropOtherAspectRatioX.takeIf { it > 0f } ?: 1f
        val y = activity.config.lastEditorCropOtherAspectRatioY.takeIf { it > 0f } ?: 1f
        activity.config.lastEditorCropOtherAspectRatioX = x
        activity.config.lastEditorCropOtherAspectRatioY = y
        x to y
    } else {
        null
    }
    return savedRatio to otherPair
}

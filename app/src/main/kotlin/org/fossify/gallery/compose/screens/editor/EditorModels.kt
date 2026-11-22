package org.fossify.gallery.compose.screens.editor

import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import org.fossify.gallery.helpers.ASPECT_RATIO_FREE
import org.fossify.gallery.helpers.ColorAdjustmentParams
import kotlin.math.roundToInt

enum class EditorPrimaryMode {
    CROP_ROTATE,
    FILTER,
    DRAW,
}

@Immutable
data class EditorFilterEntry(
    val index: Int,
    val name: String,
    val thumbnail: Bitmap,
)

@Immutable
data class AspectRatioPreset(
    val label: String,
    val widthRatio: Float,
    val heightRatio: Float,
)

@Suppress("MagicNumber")
internal val OTHER_ASPECT_RATIOS_LANDSCAPE = listOf(
    AspectRatioPreset("2:1", 2f, 1f),
    AspectRatioPreset("3:2", 3f, 2f),
    AspectRatioPreset("4:3", 4f, 3f),
    AspectRatioPreset("5:3", 5f, 3f),
    AspectRatioPreset("16:9", 16f, 9f),
    AspectRatioPreset("19:9", 19f, 9f),
)

@Suppress("MagicNumber")
internal val OTHER_ASPECT_RATIOS_PORTRAIT = listOf(
    AspectRatioPreset("1:2", 1f, 2f),
    AspectRatioPreset("2:3", 2f, 3f),
    AspectRatioPreset("3:4", 3f, 4f),
    AspectRatioPreset("3:5", 3f, 5f),
    AspectRatioPreset("9:16", 9f, 16f),
    AspectRatioPreset("9:19", 9f, 19f),
)

@Suppress("MagicNumber")
internal val EDITOR_DRAW_COLOR_SWATCHES = listOf(
    0xFFFFFFFF.toInt(),
    0xFF000000.toInt(),
    0xFFEF5350.toInt(),
    0xFFEC407A.toInt(),
    0xFFAB47BC.toInt(),
    0xFF5C6BC0.toInt(),
    0xFF29B6F6.toInt(),
    0xFF26A69A.toInt(),
    0xFF66BB6A.toInt(),
    0xFFD4E157.toInt(),
    0xFFFFEE58.toInt(),
    0xFFFFA726.toInt(),
    0xFFFF7043.toInt(),
    0xFF8D6E63.toInt(),
    0xFF78909C.toInt(),
)

sealed interface EditorDialog {
    data class SaveAs(
        val folderPath: String,
        val filename: String,
        val extension: String,
        val pendingBitmap: Bitmap? = null,
    ) : EditorDialog

    data object OverwriteOriginalConfirm : EditorDialog

    data class OverwriteFileConfirm(
        val targetPath: String,
        val pendingBitmap: Bitmap? = null,
    ) : EditorDialog

    data class Resize(
        val originalWidth: Int,
        val originalHeight: Int,
    ) : EditorDialog

    data class OtherAspectRatio(
        val current: Pair<Float, Float>?,
    ) : EditorDialog

    data class CustomAspectRatio(
        val current: Pair<Float, Float>?,
    ) : EditorDialog

    data class ColorAdjustments(
        val initial: ColorAdjustmentParams,
    ) : EditorDialog

    data class BrushColorPicker(
        val currentColor: Int,
    ) : EditorDialog
}

data class EditorUiState(
    val isCropIntent: Boolean = false,
    val lockSquareCrop: Boolean = false,
    val hasImage: Boolean = false,
    val isSaving: Boolean = false,
    val primaryMode: EditorPrimaryMode = EditorPrimaryMode.CROP_ROTATE,
    val showAspectRatioStrip: Boolean = true,
    val aspectRatio: Int = ASPECT_RATIO_FREE,
    val lastOtherAspectRatio: Pair<Float, Float>? = null,
    val colorAdjustments: ColorAdjustmentParams = ColorAdjustmentParams(),
    val filters: List<EditorFilterEntry> = emptyList(),
    val isLoadingFilters: Boolean = false,
    val selectedFilterIndex: Int = 0,
    val drawColor: Int = 0xFFFF7043.toInt(),
    val brushSizePercent: Int = DEFAULT_BRUSH_PERCENT,
    val canUndoDraw: Boolean = false,
    val activeDialog: EditorDialog? = null,
) {
    companion object {
        const val DEFAULT_BRUSH_PERCENT = 50
    }
}

data class EditorCallbacks(
    val onBack: () -> Unit,
    val onSaveClick: () -> Unit,
    val onOverwriteOriginalClick: () -> Unit,
    val onAdjustColorsClick: () -> Unit,
    val onEditWithClick: () -> Unit,
    val onShareClick: () -> Unit,
    val onSelectPrimaryMode: (EditorPrimaryMode) -> Unit,
    val onRotateRight: () -> Unit,
    val onFlipHorizontal: () -> Unit,
    val onFlipVertical: () -> Unit,
    val onToggleAspectRatioStrip: () -> Unit,
    val onResizeClick: () -> Unit,
    val onSelectAspectRatio: (Int) -> Unit,
    val onOpenOtherAspectRatio: () -> Unit,
    val onSelectFilter: (Int) -> Unit,
    val onOpenBrushColorPicker: () -> Unit,
    val onBrushSizeChange: (Int) -> Unit,
    val onUndoDraw: () -> Unit,
    val onDismissDialog: () -> Unit,
    val onConfirmSaveAs: (folderPath: String, filename: String, extension: String, pendingBitmap: Bitmap?) -> Unit,
    val onConfirmOverwriteOriginal: () -> Unit,
    val onConfirmOverwriteFile: (targetPath: String, pendingBitmap: Bitmap?) -> Unit,
    val onConfirmResize: (width: Int, height: Int) -> Unit,
    val onConfirmOtherAspectRatio: (Pair<Float, Float>) -> Unit,
    val onOpenCustomAspectRatio: () -> Unit,
    val onConfirmCustomAspectRatio: (Pair<Float, Float>) -> Unit,
    val onPreviewColorAdjustments: (ColorAdjustmentParams) -> Unit,
    val onConfirmColorAdjustments: (ColorAdjustmentParams) -> Unit,
    val onCancelColorAdjustments: (ColorAdjustmentParams) -> Unit,
    val onConfirmBrushColor: (Int) -> Unit,
)

internal fun computeProportionalHeight(width: Int, originalWidth: Int, originalHeight: Int): Int {
    if (width <= 0 || originalWidth <= 0 || originalHeight <= 0) return 0
    val ratio = originalWidth.toFloat() / originalHeight.toFloat()
    return (width / ratio).roundToInt().coerceIn(1, originalHeight)
}

internal fun computeProportionalWidth(height: Int, originalWidth: Int, originalHeight: Int): Int {
    if (height <= 0 || originalWidth <= 0 || originalHeight <= 0) return 0
    val ratio = originalWidth.toFloat() / originalHeight.toFloat()
    return (height * ratio).roundToInt().coerceIn(1, originalWidth)
}

package org.fossify.gallery.compose.screens.editor

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.fossify.commons.extensions.getFilenameFromPath
import org.fossify.commons.extensions.humanizePath
import org.fossify.commons.extensions.isAValidFilename
import org.fossify.gallery.R
import org.fossify.gallery.compose.screens.FolderPathPickerSheet
import org.fossify.gallery.compose.theme.Radius
import org.fossify.gallery.helpers.ColorAdjustmentParams
import java.util.Locale
import kotlin.math.roundToInt

private const val MAX_RGB = 255f
private const val MIN_BRIGHTNESS = -100f
private const val MAX_BRIGHTNESS = 100f
private const val MIN_CONTRAST = 0.5f
private const val MAX_CONTRAST = 2.0f
private const val MIN_SATURATION = 0f
private const val MAX_SATURATION = 2.0f
private const val PERCENT_SCALE = 100f
private const val LUMINANCE_THRESHOLD = 0.5f

@Composable
internal fun EditorDialogsHost(
    dialog: EditorDialog?,
    callbacks: EditorCallbacks,
    allowFolderPicker: Boolean = true,
) {
    when (dialog) {
        null -> Unit
        is EditorDialog.SaveAs -> EditorSaveAsDialog(
            initialFolder = dialog.folderPath,
            initialFilename = dialog.filename,
            initialExtension = dialog.extension,
            allowFolderPicker = allowFolderPicker,
            onDismiss = callbacks.onDismissDialog,
            onConfirm = { folder, name, ext ->
                callbacks.onConfirmSaveAs(folder, name, ext, dialog.pendingBitmap)
            },
        )
        EditorDialog.OverwriteOriginalConfirm -> AlertDialog(
            onDismissRequest = callbacks.onDismissDialog,
            title = { Text(stringResource(R.string.overwrite_original)) },
            text = { Text(stringResource(R.string.edit_overwrite_confirm)) },
            confirmButton = {
                TextButton(onClick = callbacks.onConfirmOverwriteOriginal) {
                    Text(stringResource(R.string.overwrite_original))
                }
            },
            dismissButton = {
                TextButton(onClick = callbacks.onDismissDialog) {
                    Text(stringResource(org.fossify.commons.R.string.cancel))
                }
            },
        )
        is EditorDialog.OverwriteFileConfirm -> {
            val message = stringResource(
                org.fossify.commons.R.string.file_already_exists_overwrite,
                dialog.targetPath.getFilenameFromPath(),
            )
            AlertDialog(
                onDismissRequest = callbacks.onDismissDialog,
                title = { Text(stringResource(org.fossify.commons.R.string.save_as)) },
                text = { Text(message) },
                confirmButton = {
                    TextButton(
                        onClick = { callbacks.onConfirmOverwriteFile(dialog.targetPath, dialog.pendingBitmap) },
                    ) {
                        Text(stringResource(org.fossify.commons.R.string.ok))
                    }
                },
                dismissButton = {
                    TextButton(onClick = callbacks.onDismissDialog) {
                        Text(stringResource(org.fossify.commons.R.string.cancel))
                    }
                },
            )
        }
        is EditorDialog.Resize -> EditorResizeDialog(
            originalWidth = dialog.originalWidth,
            originalHeight = dialog.originalHeight,
            onDismiss = callbacks.onDismissDialog,
            onConfirm = callbacks.onConfirmResize,
        )
        is EditorDialog.OtherAspectRatio -> EditorOtherAspectRatioDialog(
            current = dialog.current,
            onDismiss = callbacks.onDismissDialog,
            onPickPreset = callbacks.onConfirmOtherAspectRatio,
            onPickCustom = callbacks.onOpenCustomAspectRatio,
        )
        is EditorDialog.CustomAspectRatio -> EditorCustomAspectRatioDialog(
            current = dialog.current,
            onDismiss = callbacks.onDismissDialog,
            onConfirm = callbacks.onConfirmCustomAspectRatio,
        )
        is EditorDialog.ColorAdjustments -> EditorColorAdjustmentsDialog(
            initial = dialog.initial,
            onPreview = callbacks.onPreviewColorAdjustments,
            onConfirm = callbacks.onConfirmColorAdjustments,
            onCancel = { callbacks.onCancelColorAdjustments(dialog.initial) },
        )
        is EditorDialog.BrushColorPicker -> EditorBrushColorDialog(
            initialColor = dialog.currentColor,
            onDismiss = callbacks.onDismissDialog,
            onConfirm = callbacks.onConfirmBrushColor,
        )
    }
}

@Composable
fun EditorSaveAsDialog(
    initialFolder: String,
    initialFilename: String,
    initialExtension: String,
    onDismiss: () -> Unit,
    onConfirm: (folderPath: String, filename: String, extension: String) -> Unit,
    allowFolderPicker: Boolean = true,
) {
    val context = LocalContext.current
    var folderPath by remember(initialFolder) { mutableStateOf(initialFolder) }
    var filename by remember(initialFilename) { mutableStateOf(initialFilename) }
    var extension by remember(initialExtension) { mutableStateOf(initialExtension) }
    var errorRes by remember { mutableStateOf<Int?>(null) }
    var showFolderPicker by remember { mutableStateOf(false) }

    val displayFolder = remember(folderPath) {
        val humanized = runCatching { context.humanizePath(folderPath) }.getOrDefault(folderPath)
        "${humanized.trimEnd('/')}/"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(org.fossify.commons.R.string.save_as)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    shape = RoundedCornerShape(Radius.md),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.md))
                        .clickable(enabled = allowFolderPicker) { showFolderPicker = true },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.path),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = displayFolder,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = filename,
                    onValueChange = {
                        filename = it
                        errorRes = null
                    },
                    label = { Text(stringResource(org.fossify.commons.R.string.filename)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = extension,
                    onValueChange = {
                        extension = it
                        errorRes = null
                    },
                    label = { Text(stringResource(org.fossify.commons.R.string.extension)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                errorRes?.let { resId ->
                    Text(
                        text = stringResource(resId),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val trimmedName = filename.trim()
                    val trimmedExt = extension.trim().trimStart('.')
                    errorRes = when {
                        trimmedName.isEmpty() -> org.fossify.commons.R.string.filename_cannot_be_empty
                        trimmedExt.isEmpty() -> org.fossify.commons.R.string.extension_cannot_be_empty
                        !"$trimmedName.$trimmedExt".isAValidFilename() ->
                            org.fossify.commons.R.string.filename_invalid_characters
                        else -> null
                    }
                    if (errorRes == null) {
                        onConfirm(folderPath, trimmedName, trimmedExt)
                    }
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

    if (showFolderPicker && allowFolderPicker) {
        FolderPathPickerSheet(
            title = stringResource(R.string.path),
            initialPath = folderPath,
            onPathSelected = { picked ->
                folderPath = picked
                showFolderPicker = false
            },
            onDismiss = { showFolderPicker = false },
        )
    }
}

@Composable
fun EditorResizeDialog(
    originalWidth: Int,
    originalHeight: Int,
    onDismiss: () -> Unit,
    onConfirm: (width: Int, height: Int) -> Unit,
) {
    var widthText by remember(originalWidth) { mutableStateOf(originalWidth.toString()) }
    var heightText by remember(originalHeight) { mutableStateOf(originalHeight.toString()) }
    var keepAspectRatio by remember { mutableStateOf(true) }
    var showError by remember { mutableStateOf(false) }

    fun updateWidth(raw: String) {
        val clamped = raw.filter(Char::isDigit).toIntOrNull()?.coerceAtMost(originalWidth)
        widthText = clamped?.toString().orEmpty()
        if (keepAspectRatio) {
            val nextH = clamped?.let { computeProportionalHeight(it, originalWidth, originalHeight) } ?: 0
            heightText = nextH.takeIf { it > 0 }?.toString().orEmpty()
        }
        showError = false
    }

    fun updateHeight(raw: String) {
        val clamped = raw.filter(Char::isDigit).toIntOrNull()?.coerceAtMost(originalHeight)
        heightText = clamped?.toString().orEmpty()
        if (keepAspectRatio) {
            val nextW = clamped?.let { computeProportionalWidth(it, originalWidth, originalHeight) } ?: 0
            widthText = nextW.takeIf { it > 0 }?.toString().orEmpty()
        }
        showError = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.resize_and_save)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = widthText,
                    onValueChange = ::updateWidth,
                    label = { Text(stringResource(R.string.width)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = heightText,
                    onValueChange = ::updateHeight,
                    label = { Text(stringResource(R.string.height)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable(role = Role.Checkbox) { keepAspectRatio = !keepAspectRatio },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = keepAspectRatio, onCheckedChange = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.keep_aspect_ratio),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (showError) {
                    Text(
                        text = stringResource(R.string.invalid_values),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val w = widthText.toIntOrNull() ?: 0
                    val h = heightText.toIntOrNull() ?: 0
                    if (w <= 0 || h <= 0) {
                        showError = true
                    } else {
                        onConfirm(w, h)
                    }
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
fun EditorOtherAspectRatioDialog(
    current: Pair<Float, Float>?,
    onDismiss: () -> Unit,
    onPickPreset: (Pair<Float, Float>) -> Unit,
    onPickCustom: () -> Unit,
) {
    val allPresets = remember { OTHER_ASPECT_RATIOS_LANDSCAPE + OTHER_ASPECT_RATIOS_PORTRAIT }
    val isCustomSelected = current != null && allPresets.none {
        it.widthRatio == current.first && it.heightRatio == current.second
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.other_aspect_ratio)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        OTHER_ASPECT_RATIOS_LANDSCAPE.forEach { preset ->
                            AspectRatioRadioOption(
                                label = preset.label,
                                selected = current == (preset.widthRatio to preset.heightRatio),
                                onClick = { onPickPreset(preset.widthRatio to preset.heightRatio) },
                            )
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        OTHER_ASPECT_RATIOS_PORTRAIT.forEach { preset ->
                            AspectRatioRadioOption(
                                label = preset.label,
                                selected = current == (preset.widthRatio to preset.heightRatio),
                                onClick = { onPickPreset(preset.widthRatio to preset.heightRatio) },
                            )
                        }
                    }
                }
                AspectRatioRadioOption(
                    label = stringResource(org.fossify.commons.R.string.custom),
                    selected = isCustomSelected || current == null,
                    onClick = onPickCustom,
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(org.fossify.commons.R.string.cancel))
            }
        },
    )
}

@Composable
private fun AspectRatioRadioOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(Radius.sm))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(8.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun EditorCustomAspectRatioDialog(
    current: Pair<Float, Float>?,
    onDismiss: () -> Unit,
    onConfirm: (Pair<Float, Float>) -> Unit,
) {
    var widthText by remember(current) {
        mutableStateOf(current?.first?.roundToInt()?.takeIf { it > 0 }?.toString() ?: "")
    }
    var heightText by remember(current) {
        mutableStateOf(current?.second?.roundToInt()?.takeIf { it > 0 }?.toString() ?: "")
    }
    var showError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(org.fossify.commons.R.string.custom)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = widthText,
                    onValueChange = {
                        widthText = it.filter(Char::isDigit)
                        showError = false
                    },
                    label = { Text(stringResource(R.string.width)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = heightText,
                    onValueChange = {
                        heightText = it.filter(Char::isDigit)
                        showError = false
                    },
                    label = { Text(stringResource(R.string.height)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (showError) {
                    Text(
                        text = stringResource(R.string.invalid_values),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val w = widthText.toFloatOrNull() ?: 0f
                    val h = heightText.toFloatOrNull() ?: 0f
                    if (w <= 0f || h <= 0f) {
                        showError = true
                    } else {
                        onConfirm(w to h)
                    }
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
fun EditorColorAdjustmentsDialog(
    initial: ColorAdjustmentParams,
    onPreview: (ColorAdjustmentParams) -> Unit,
    onConfirm: (ColorAdjustmentParams) -> Unit,
    onCancel: () -> Unit,
) {
    var params by remember(initial) { mutableStateOf(initial) }
    val locale = LocalConfiguration.current.locales.get(0) ?: Locale.US

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.adjust_colors)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AdjustmentSliderRow(
                    label = stringResource(R.string.adjust_brightness),
                    valueText = "${params.brightness.roundToInt()}",
                    value = params.brightness,
                    range = MIN_BRIGHTNESS..MAX_BRIGHTNESS,
                    onValueChange = { next ->
                        params = params.copy(brightness = next)
                        onPreview(params)
                    },
                )
                AdjustmentSliderRow(
                    label = stringResource(R.string.adjust_contrast),
                    valueText = String.format(locale, "%.2fx", params.contrast),
                    value = params.contrast,
                    range = MIN_CONTRAST..MAX_CONTRAST,
                    onValueChange = { next ->
                        params = params.copy(contrast = next)
                        onPreview(params)
                    },
                )
                AdjustmentSliderRow(
                    label = stringResource(R.string.adjust_saturation),
                    valueText = "${(params.saturation * PERCENT_SCALE).roundToInt()}%",
                    value = params.saturation,
                    range = MIN_SATURATION..MAX_SATURATION,
                    onValueChange = { next ->
                        params = params.copy(saturation = next)
                        onPreview(params)
                    },
                )
                TextButton(
                    onClick = {
                        params = ColorAdjustmentParams()
                        onPreview(params)
                    },
                    enabled = !params.isIdentity,
                ) {
                    Text(stringResource(R.string.adjust_reset))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(params) }) {
                Text(stringResource(org.fossify.commons.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(org.fossify.commons.R.string.cancel))
            }
        },
    )
}

@Composable
private fun AdjustmentSliderRow(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditorBrushColorDialog(
    initialColor: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var red by remember(initialColor) { mutableFloatStateOf(AndroidColor.red(initialColor).toFloat()) }
    var green by remember(initialColor) { mutableFloatStateOf(AndroidColor.green(initialColor).toFloat()) }
    var blue by remember(initialColor) { mutableFloatStateOf(AndroidColor.blue(initialColor).toFloat()) }
    val selectedColor = AndroidColor.rgb(red.roundToInt(), green.roundToInt(), blue.roundToInt())
    val composeColor = Color(selectedColor)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(composeColor)
                        .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape),
                )
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.draw))
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    EDITOR_DRAW_COLOR_SWATCHES.forEach { swatchInt ->
                        val swatchColor = Color(swatchInt)
                        val isSelected = swatchInt == selectedColor
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(swatchColor)
                                .border(
                                    width = if (isSelected) 2.5.dp else 1.dp,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant
                                    },
                                    shape = CircleShape,
                                )
                                .clickable {
                                    red = AndroidColor.red(swatchInt).toFloat()
                                    green = AndroidColor.green(swatchInt).toFloat()
                                    blue = AndroidColor.blue(swatchInt).toFloat()
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = if (swatchColor.luminance() > LUMINANCE_THRESHOLD) {
                                        Color.Black
                                    } else {
                                        Color.White
                                    },
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                AdjustmentSliderRow("R", red.roundToInt().toString(), red, 0f..MAX_RGB) { red = it }
                AdjustmentSliderRow("G", green.roundToInt().toString(), green, 0f..MAX_RGB) { green = it }
                AdjustmentSliderRow("B", blue.roundToInt().toString(), blue, 0f..MAX_RGB) { blue = it }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedColor) }) {
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

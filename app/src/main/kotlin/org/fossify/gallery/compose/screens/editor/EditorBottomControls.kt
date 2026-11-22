package org.fossify.gallery.compose.screens.editor

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CropRotate
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.PhotoSizeSelectLarge
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.fossify.gallery.R
import org.fossify.gallery.compose.theme.Radius
import org.fossify.gallery.helpers.ASPECT_RATIO_FOUR_THREE
import org.fossify.gallery.helpers.ASPECT_RATIO_FREE
import org.fossify.gallery.helpers.ASPECT_RATIO_ONE_ONE
import org.fossify.gallery.helpers.ASPECT_RATIO_OTHER
import org.fossify.gallery.helpers.ASPECT_RATIO_SIXTEEN_NINE
import kotlin.math.roundToInt

private const val FLIP_VERTICAL_ROTATION = 90f
private const val MIN_BRUSH_PERCENT = 1f
private const val MAX_BRUSH_PERCENT = 100f
private const val MIN_DOT_DP = 6f
private const val MAX_DOT_DP = 26f

@Composable
internal fun EditorBottomPanel(
    state: EditorUiState,
    callbacks: EditorCallbacks,
    modifier: Modifier = Modifier,
) {
    val enabled = state.hasImage && !state.isSaving
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp,
    ) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            AnimatedContent(
                targetState = state.primaryMode,
                label = "editor_tool_strip",
            ) { mode ->
                when (mode) {
                    EditorPrimaryMode.CROP_ROTATE -> CropRotateToolStrip(
                        state = state,
                        enabled = enabled,
                        callbacks = callbacks,
                    )
                    EditorPrimaryMode.FILTER -> FilterToolStrip(
                        state = state,
                        enabled = enabled,
                        callbacks = callbacks,
                    )
                    EditorPrimaryMode.DRAW -> DrawToolStrip(
                        state = state,
                        enabled = enabled,
                        callbacks = callbacks,
                    )
                }
            }
            if (!state.isCropIntent) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                PrimaryModeBar(
                    selectedMode = state.primaryMode,
                    enabled = enabled,
                    onSelectMode = callbacks.onSelectPrimaryMode,
                )
            }
        }
    }
}

@Composable
private fun CropRotateToolStrip(
    state: EditorUiState,
    enabled: Boolean,
    callbacks: EditorCallbacks,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        AnimatedVisibility(visible = state.showAspectRatioStrip && !state.lockSquareCrop) {
            AspectRatioStrip(
                selectedRatio = state.aspectRatio,
                enabled = enabled,
                onSelectRatio = callbacks.onSelectAspectRatio,
                onOpenOther = callbacks.onOpenOtherAspectRatio,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolIconAction(
                icon = Icons.AutoMirrored.Filled.RotateRight,
                label = stringResource(R.string.rotate),
                enabled = enabled,
                onClick = callbacks.onRotateRight,
            )
            if (!state.isCropIntent) {
                ToolIconAction(
                    icon = Icons.Default.PhotoSizeSelectLarge,
                    label = stringResource(R.string.action_resize),
                    enabled = enabled,
                    onClick = callbacks.onResizeClick,
                )
            }
            if (!state.lockSquareCrop) {
                ToolIconAction(
                    icon = Icons.Default.AspectRatio,
                    label = stringResource(R.string.crop),
                    enabled = enabled,
                    active = state.showAspectRatioStrip,
                    onClick = callbacks.onToggleAspectRatioStrip,
                )
            }
            ToolIconAction(
                icon = Icons.Default.Flip,
                label = stringResource(R.string.flip_horizontally),
                enabled = enabled,
                onClick = callbacks.onFlipHorizontal,
            )
            ToolIconAction(
                icon = Icons.Default.Flip,
                label = stringResource(R.string.flip_vertically),
                enabled = enabled,
                iconRotation = FLIP_VERTICAL_ROTATION,
                onClick = callbacks.onFlipVertical,
            )
        }
    }
}

@Composable
private fun AspectRatioStrip(
    selectedRatio: Int,
    enabled: Boolean,
    onSelectRatio: (Int) -> Unit,
    onOpenOther: () -> Unit,
) {
    val options = listOf(
        ASPECT_RATIO_FREE to stringResource(R.string.free_aspect_ratio),
        ASPECT_RATIO_ONE_ONE to "1:1",
        ASPECT_RATIO_FOUR_THREE to "4:3",
        ASPECT_RATIO_SIXTEEN_NINE to "16:9",
        ASPECT_RATIO_OTHER to stringResource(R.string.other_aspect_ratio),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (ratioId, label) ->
            FilterChip(
                selected = selectedRatio == ratioId,
                onClick = {
                    if (ratioId == ASPECT_RATIO_OTHER) onOpenOther() else onSelectRatio(ratioId)
                },
                enabled = enabled,
                label = { Text(text = label, maxLines = 1) },
            )
        }
    }
}

@Composable
private fun FilterToolStrip(
    state: EditorUiState,
    enabled: Boolean,
    callbacks: EditorCallbacks,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (state.isLoadingFilters) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(92.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
            }
        } else {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!state.isCropIntent) {
                    item(key = "adjust_colors_pill") {
                        AdjustColorsTile(
                            isModified = !state.colorAdjustments.isIdentity,
                            enabled = enabled,
                            onClick = callbacks.onAdjustColorsClick,
                        )
                    }
                }
                items(state.filters, key = { it.index }) { entry ->
                    val isSelected = entry.index == state.selectedFilterIndex
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(64.dp)
                            .clip(RoundedCornerShape(Radius.sm))
                            .clickable(enabled = enabled) { callbacks.onSelectFilter(entry.index) }
                            .padding(2.dp),
                    ) {
                        Image(
                            bitmap = entry.thumbnail.asImageBitmap(),
                            contentDescription = entry.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(Radius.sm))
                                .border(
                                    width = if (isSelected) 2.5.dp else 1.dp,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant
                                    },
                                    shape = RoundedCornerShape(Radius.sm),
                                ),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = entry.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AdjustColorsTile(
    isModified: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(64.dp)
            .clip(RoundedCornerShape(Radius.sm))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(2.dp),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(Radius.sm))
                .background(
                    if (isModified) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                )
                .border(
                    width = if (isModified) 2.dp else 1.dp,
                    color = if (isModified) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                    shape = RoundedCornerShape(Radius.sm),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Tune,
                contentDescription = stringResource(R.string.adjust_colors),
                tint = if (isModified) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.adjust_colors),
            style = MaterialTheme.typography.labelSmall,
            color = if (isModified) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DrawToolStrip(
    state: EditorUiState,
    enabled: Boolean,
    callbacks: EditorCallbacks,
) {
    val dotSizeDp = MIN_DOT_DP + (MAX_DOT_DP - MIN_DOT_DP) * (state.brushSizePercent / MAX_BRUSH_PERCENT)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                .clickable(enabled = enabled, onClick = callbacks.onOpenBrushColorPicker),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(dotSizeDp.dp)
                    .clip(CircleShape)
                    .background(Color(state.drawColor))
                    .border(1.dp, Color.White.copy(alpha = 0.6f), CircleShape),
            )
        }
        Slider(
            value = state.brushSizePercent.toFloat().coerceIn(MIN_BRUSH_PERCENT, MAX_BRUSH_PERCENT),
            onValueChange = { callbacks.onBrushSizeChange(it.roundToInt()) },
            valueRange = MIN_BRUSH_PERCENT..MAX_BRUSH_PERCENT,
            enabled = enabled,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = callbacks.onUndoDraw,
            enabled = enabled && state.canUndoDraw,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Undo,
                contentDescription = stringResource(R.string.undo),
            )
        }
    }
}

@Composable
private fun PrimaryModeBar(
    selectedMode: EditorPrimaryMode,
    enabled: Boolean,
    onSelectMode: (EditorPrimaryMode) -> Unit,
) {
    val items = listOf(
        Triple(EditorPrimaryMode.CROP_ROTATE, Icons.Default.CropRotate, stringResource(R.string.transform)),
        Triple(
            EditorPrimaryMode.FILTER,
            Icons.Default.AutoFixHigh,
            stringResource(org.fossify.commons.R.string.filter),
        ),
        Triple(EditorPrimaryMode.DRAW, Icons.Default.Brush, stringResource(R.string.draw)),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { (mode, icon, label) ->
            val selected = selectedMode == mode
            Surface(
                shape = CircleShape,
                color = if (selected) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    Color.Transparent
                },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(CircleShape)
                    .clickable(enabled = enabled, role = Role.Tab) { onSelectMode(mode) },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (selected) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolIconAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    active: Boolean = false,
    iconRotation: Float = 0f,
) {
    val tint = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        active -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = if (iconRotation != 0f) Modifier.rotate(iconRotation) else Modifier,
        )
    }
}

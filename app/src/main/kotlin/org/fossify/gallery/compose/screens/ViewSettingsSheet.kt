package org.fossify.gallery.compose.screens
import androidx.compose.ui.res.stringResource
import org.fossify.gallery.R

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.fossify.gallery.extensions.config

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewSettingsSheet(
    settings: ViewSettings,
    showDisplayMode: Boolean = true,
    onSettingsChange: (ViewSettings) -> Unit,
    onDismiss: () -> Unit,
    modeTitle: String? = null,
    onToggleMode: (() -> Unit)? = null,
    modeOptions: List<String>? = null,
    isAlbumMode: Boolean = false,
) {
    var local by remember(settings) { mutableStateOf(settings) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).verticalScroll(rememberScrollState())) {
            // Header
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.view_settings_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, stringResource(R.string.cd_close)) }
            }
            if (modeOptions != null && onToggleMode != null) {
                Spacer(Modifier.height(4.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    modeOptions.forEachIndexed { i, label ->
                        SegmentedButton(
                            selected = label == modeTitle,
                            onClick = { if (label != modeTitle) onToggleMode() },
                            shape = SegmentedButtonDefaults.itemShape(i, modeOptions.size)
                        ) { Text(label, fontWeight = FontWeight.SemiBold) }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(8.dp))

            // View Type
            Text(stringResource(R.string.view_appearance), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ViewType.entries.forEachIndexed { i, vt ->
                        SegmentedButton(
                            selected = local.viewType == vt,
                            onClick = { local = local.copy(viewType = vt); onSettingsChange(local) },
                            shape = SegmentedButtonDefaults.itemShape(i, ViewType.entries.size)
                        ) { Text(when(vt) { ViewType.GRID -> stringResource(R.string.view_type_grid); ViewType.LIST -> stringResource(R.string.view_type_list); ViewType.MOSAIC -> stringResource(R.string.view_type_mosaic) }) }
                    }
            }

            // Column Count
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.columns), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (c in 2..6) {
                    TextButton(
                        onClick = { local = local.copy(columnCount = c); onSettingsChange(local) },
                        modifier = Modifier.size(width = 48.dp, height = 36.dp)
                    ) {
                        Text("$c", fontWeight = if (local.columnCount == c) FontWeight.Bold else FontWeight.Normal,
                            color = if (local.columnCount == c) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            // Display Mode
            if (showDisplayMode) {
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.display_mode), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    DisplayMode.entries.forEachIndexed { i, dm ->
                        SegmentedButton(
                            selected = local.displayMode == dm,
                            onClick = { local = local.copy(displayMode = dm); onSettingsChange(local) },
                            shape = SegmentedButtonDefaults.itemShape(i, DisplayMode.entries.size)
                        ) { Text(when(dm) { DisplayMode.COMPACT -> stringResource(R.string.display_compact); DisplayMode.NORMAL -> stringResource(R.string.display_normal); DisplayMode.DARK -> stringResource(R.string.display_dark) }) }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            // Sort
            Text(stringResource(R.string.sorting), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                // RATING only makes sense per media item, COUNT (file count) only per folder - each
                // mode gets the other's option filtered out instead of both lists carrying a
                // meaningless entry.
                val sortFields = if (isAlbumMode) SortField.entries.filter { it != SortField.RATING } else SortField.entries.filter { it != SortField.COUNT }
                sortFields.forEachIndexed { i, sf ->
                    SegmentedButton(
                        selected = local.sortBy == sf,
                        onClick = { local = local.copy(sortBy = sf); onSettingsChange(local) },
                        shape = SegmentedButtonDefaults.itemShape(i, sortFields.size)
                    ) { Text(when(sf) { SortField.NAME -> stringResource(R.string.sort_name); SortField.DATE -> stringResource(R.string.sort_date); SortField.SIZE -> stringResource(R.string.sort_size); SortField.RATING -> stringResource(R.string.sort_rating); SortField.COUNT -> stringResource(R.string.sort_by_item_count) }) }
                }
            }

            // Toggles
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.sort_descending), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.weight(1f))
                Switch(checked = local.sortDesc, onCheckedChange = { local = local.copy(sortDesc = it); onSettingsChange(local) })
            }
            if (!isAlbumMode) {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.show_filenames), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.weight(1f))
                Switch(checked = local.showFileNames, onCheckedChange = { local = local.copy(showFileNames = it); onSettingsChange(local) })
            }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.rounded_corners), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.weight(1f))
                Switch(checked = local.roundedCorners, onCheckedChange = { local = local.copy(roundedCorners = it); onSettingsChange(local) })
            }
            if (showDisplayMode) {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.folder_preview), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.weight(1f))
                    Switch(checked = local.showFolderThumbnails, onCheckedChange = { local = local.copy(showFolderThumbnails = it); onSettingsChange(local) })
                }
            }
            // Spacing
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.spacing), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { val v = (local.spacing - 1).coerceAtLeast(2); local = local.copy(spacing = v); onSettingsChange(local) }, modifier = Modifier.size(40.dp)) {
                    Text("-", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
                Text("${local.spacing}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 8.dp))
                IconButton(onClick = { val v = (local.spacing + 1).coerceAtMost(20); local = local.copy(spacing = v); onSettingsChange(local) }, modifier = Modifier.size(40.dp)) {
                    Text("+", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.anchor_bottom), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.weight(1f))
                Switch(checked = local.anchorBottom, onCheckedChange = { local = local.copy(anchorBottom = it); onSettingsChange(local) })
            }

            // Thumbnail overlays (global config)
            if (!isAlbumMode) {
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.thumbnail_overlays), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.thumbnail_overlays_scope), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            val ovCtx = LocalContext.current
            var showRatingOv by remember { mutableStateOf(ovCtx.config.showRatingOnThumbnails) }
            var showVideoDurOv by remember { mutableStateOf(ovCtx.config.showVideoDurationOnThumbnails) }
            var cropThumbnailsOv by remember { mutableStateOf(ovCtx.config.cropThumbnails) }
            // Grid-only: Mosaic already varies each tile's own height by its image's aspect ratio
            // (see MediaScreen.kt's mosaic call site), so there's no square-crop-vs-letterbox choice
            // to make there the way there is for Grid's fixed square tiles.
            if (local.viewType == ViewType.GRID) {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.set_crop_thumbnails), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.weight(1f))
                    Switch(checked = cropThumbnailsOv, onCheckedChange = { cropThumbnailsOv = it; ovCtx.config.cropThumbnails = it })
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.show_rating), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.weight(1f))
                Switch(checked = showRatingOv, onCheckedChange = { showRatingOv = it; ovCtx.config.showRatingOnThumbnails = it })
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.show_video_duration), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.weight(1f))
                Switch(checked = showVideoDurOv, onCheckedChange = { showVideoDurOv = it; ovCtx.config.showVideoDurationOnThumbnails = it })
            }

            Spacer(Modifier.height(24.dp))
            }
        }
    }
}

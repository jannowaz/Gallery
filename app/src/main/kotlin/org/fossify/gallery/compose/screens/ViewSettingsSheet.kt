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
import org.fossify.gallery.helpers.GroupBy
import org.fossify.gallery.helpers.GroupOrder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewSettingsSheet(
    settings: ViewSettings,
    showDisplayMode: Boolean = true,
    onSettingsChange: (ViewSettings, Boolean) -> Unit,
    onDismiss: () -> Unit,
    modeTitle: String? = null,
    onToggleMode: (() -> Unit)? = null,
    modeOptions: List<String>? = null,
    isAlbumMode: Boolean = false,
    supportsTagGrouping: Boolean = true,
    supportsSorting: Boolean = true,
    showApplyGloballyToggle: Boolean = false,
    initialApplyGlobally: Boolean = true,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        ViewSettingsContent(
            settings = settings,
            showDisplayMode = showDisplayMode,
            onSettingsChange = onSettingsChange,
            onDismiss = onDismiss,
            modeTitle = modeTitle,
            onToggleMode = onToggleMode,
            modeOptions = modeOptions,
            isAlbumMode = isAlbumMode,
            supportsTagGrouping = supportsTagGrouping,
            supportsSorting = supportsSorting,
            showApplyGloballyToggle = showApplyGloballyToggle,
            initialApplyGlobally = initialApplyGlobally,
        )
    }
}

/** The sheet's actual content, factored out of [ViewSettingsSheet]'s [ModalBottomSheet] so the
 * combined Filter/Ansicht sheet (see ComposeExplorerActivity's MainSheets) can render it inside
 * its own single sheet instead of nesting a second modal sheet. */
@Composable
fun ViewSettingsContent(
    settings: ViewSettings,
    showDisplayMode: Boolean = true,
    onSettingsChange: (ViewSettings, Boolean) -> Unit,
    onDismiss: () -> Unit,
    modeTitle: String? = null,
    onToggleMode: (() -> Unit)? = null,
    modeOptions: List<String>? = null,
    isAlbumMode: Boolean = false,
    // A flat list of named containers (Collections, Tags) rather than media or folders. Such lists
    // sort only by name or item count and can't be grouped, mosaicked or show file names - so the
    // sheet hides those options instead of offering ones that do nothing. See CollectionsScreen /
    // TagBrowserScreen, which apply the name/count sort this exposes.
    isContainerMode: Boolean = false,
    supportsTagGrouping: Boolean = true,
    supportsSorting: Boolean = true,
    // Only meaningful for a path-scoped screen (a specific opened folder, or Explorer's current
    // directory) - lets the user pin the settings they're picking to that one path instead of the
    // tab's global default. See ViewSettingsViewModel's *ForPath functions.
    showApplyGloballyToggle: Boolean = false,
    initialApplyGlobally: Boolean = true,
) {
    var local by remember(settings) { mutableStateOf(settings) }
    // Keyed on initialApplyGlobally too (not just settings, which can coincidentally be identical
    // across two different paths) - so reopening the sheet on a different path/folder always
    // starts from that path's actual custom-vs-global state instead of carrying over the last one.
    var applyGlobally by remember(settings, initialApplyGlobally) { mutableStateOf(initialApplyGlobally) }

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
            // Mosaic is a media-grid layout (variable aspect ratios); album/container lists render
            // it identically to the tile grid, so it's only offered where it actually differs.
            val viewTypes = if (isAlbumMode || isContainerMode) ViewType.entries.filter { it != ViewType.MOSAIC } else ViewType.entries
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    viewTypes.forEachIndexed { i, vt ->
                        SegmentedButton(
                            selected = local.viewType == vt,
                            onClick = { local = local.copy(viewType = vt); onSettingsChange(local, applyGlobally) },
                            shape = SegmentedButtonDefaults.itemShape(i, viewTypes.size)
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
                        onClick = { local = local.copy(columnCount = c); onSettingsChange(local, applyGlobally) },
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
                            onClick = { local = local.copy(displayMode = dm); onSettingsChange(local, applyGlobally) },
                            shape = SegmentedButtonDefaults.itemShape(i, DisplayMode.entries.size)
                        ) { Text(when(dm) { DisplayMode.COMPACT -> stringResource(R.string.display_compact); DisplayMode.NORMAL -> stringResource(R.string.display_normal); DisplayMode.DARK -> stringResource(R.string.display_dark) }) }
                    }
                }
            }

            // Sort + Grouping - hidden entirely for tabs whose screen doesn't actually consume
            // ViewSettings.sortBy/groupBy at all (Collections, Tags: both have their own fixed
            // ordering - see CollectionsScreen/TagBrowserScreen). Showing these controls there let
            // you toggle e.g. "Größe" and nothing would happen - the exact "state you can configure
            // that doesn't make sense" this whole sort/group model was rebuilt to eliminate.
            if (supportsSorting) {
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            // Sort
            Text(stringResource(R.string.sorting), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                // RATING only makes sense per media item, COUNT (file count) only per folder/
                // container - each mode carries just its applicable fields instead of a meaningless
                // entry. Containers (Collections/Tags) have no date/size/rating at all, only a name
                // and an item count.
                val sortFields = when {
                    isContainerMode -> listOf(SortField.NAME, SortField.COUNT)
                    isAlbumMode -> SortField.entries.filter { it != SortField.RATING }
                    else -> SortField.entries.filter { it != SortField.COUNT }
                }
                sortFields.forEachIndexed { i, sf ->
                    SegmentedButton(
                        selected = local.sortBy == sf,
                        onClick = {
                            // Grouping always follows sorting (see SortField.autoGroupBy) - only
                            // re-derive it when grouping is actually on and not pinned to the
                            // explicit Tag override, so switching sort field never silently turns
                            // grouping on/off or knocks the user out of Tag mode.
                            val newGroupBy = if (local.groupBy == GroupBy.NONE || local.groupBy == GroupBy.TAG) local.groupBy else sf.autoGroupBy()
                            local = local.copy(sortBy = sf, groupBy = newGroupBy)
                            onSettingsChange(local, applyGlobally)
                        },
                        shape = SegmentedButtonDefaults.itemShape(i, sortFields.size)
                    ) { Text(when(sf) { SortField.NAME -> stringResource(R.string.sort_name); SortField.DATE -> stringResource(R.string.sort_date); SortField.SIZE -> stringResource(R.string.sort_size); SortField.RATING -> stringResource(R.string.sort_rating); SortField.COUNT -> stringResource(R.string.sort_by_item_count) }, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
                }
            }

            // Toggles
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.sort_descending), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.weight(1f))
                Switch(checked = local.sortDesc, onCheckedChange = { local = local.copy(sortDesc = it); onSettingsChange(local, applyGlobally) })
            }

            // Grouping - folders have no tags/rating to group by, so this only applies in media mode.
            // There's no free choice of grouping scheme any more: it always follows the sort field
            // above (size sort -> size buckets, date sort -> months, etc. - see SortField.autoGroupBy),
            // so a "grouping method that doesn't match the sort order" simply isn't a state that
            // exists to configure. The only independent choice left is on/off, plus the one deliberate
            // override (Tag) that isn't derived from any sort field.
            if (!isAlbumMode && !isContainerMode) {
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.grouping), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Switch(
                    checked = local.groupBy != GroupBy.NONE,
                    onCheckedChange = { on ->
                        local = local.copy(groupBy = if (on) local.sortBy.autoGroupBy() else GroupBy.NONE)
                        onSettingsChange(local, applyGlobally)
                    },
                )
            }
            if (supportsTagGrouping && local.groupBy != GroupBy.NONE) {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.group_by_tag), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.weight(1f))
                    Switch(
                        checked = local.groupBy == GroupBy.TAG,
                        onCheckedChange = { on ->
                            local = local.copy(groupBy = if (on) GroupBy.TAG else local.sortBy.autoGroupBy())
                            onSettingsChange(local, applyGlobally)
                        },
                    )
                }
            }
            if (local.groupBy == GroupBy.TAG) {
                Spacer(Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    val orders = GroupOrder.entries.toList()
                    orders.forEachIndexed { i, go ->
                        SegmentedButton(
                            selected = local.groupOrder == go,
                            onClick = { local = local.copy(groupOrder = go); onSettingsChange(local, applyGlobally) },
                            shape = SegmentedButtonDefaults.itemShape(i, orders.size)
                        ) { Text(if (go == GroupOrder.ALPHABETICAL) stringResource(R.string.group_order_alphabetical) else stringResource(R.string.group_order_count)) }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.only_top_level_tags), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.weight(1f))
                    Switch(checked = local.onlyTopLevelTags, onCheckedChange = { local = local.copy(onlyTopLevelTags = it); onSettingsChange(local, applyGlobally) })
                }
                Text(stringResource(R.string.only_top_level_tags_hint), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            }
            }
            }
            if (!isAlbumMode && !isContainerMode) {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.show_filenames), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.weight(1f))
                Switch(checked = local.showFileNames, onCheckedChange = { local = local.copy(showFileNames = it); onSettingsChange(local, applyGlobally) })
            }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.rounded_corners), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.weight(1f))
                Switch(checked = local.roundedCorners, onCheckedChange = { local = local.copy(roundedCorners = it); onSettingsChange(local, applyGlobally) })
            }
            if (showDisplayMode) {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.folder_preview), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.weight(1f))
                    Switch(checked = local.showFolderThumbnails, onCheckedChange = { local = local.copy(showFolderThumbnails = it); onSettingsChange(local, applyGlobally) })
                }
            }
            // Spacing
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.spacing), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { val v = (local.spacing - 1).coerceAtLeast(2); local = local.copy(spacing = v); onSettingsChange(local, applyGlobally) }, modifier = Modifier.size(40.dp)) {
                    Text("-", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
                Text("${local.spacing}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 8.dp))
                IconButton(onClick = { val v = (local.spacing + 1).coerceAtMost(20); local = local.copy(spacing = v); onSettingsChange(local, applyGlobally) }, modifier = Modifier.size(40.dp)) {
                    Text("+", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.anchor_bottom), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.weight(1f))
                Switch(checked = local.anchorBottom, onCheckedChange = { local = local.copy(anchorBottom = it); onSettingsChange(local, applyGlobally) })
            }

            // Scope: apply everything above to the whole tab (default) or pin it to just this one
            // path/folder. Unchecking doesn't change any value - it only changes where the values
            // picked above get saved to (see ViewSettingsViewModel's *ForPath functions).
            if (showApplyGloballyToggle) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.apply_settings_globally), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.weight(1f))
                    Switch(checked = applyGlobally, onCheckedChange = { applyGlobally = it; onSettingsChange(local, it) })
                }
                Text(stringResource(R.string.apply_settings_globally_hint), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
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

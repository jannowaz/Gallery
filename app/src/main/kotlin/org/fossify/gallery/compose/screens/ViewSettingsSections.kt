package org.fossify.gallery.compose.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.fossify.gallery.R
import org.fossify.gallery.compose.components.LibrarySectionHeader
import org.fossify.gallery.helpers.GroupBy
import org.fossify.gallery.helpers.GroupOrder

private const val MAX_SEGMENTED_ICON_ITEMS = 3

@Composable
internal fun ViewSettingsDisplaySection(
    local: ViewSettings,
    showHeader: Boolean,
    showDisplayMode: Boolean,
    isAlbumMode: Boolean,
    isContainerMode: Boolean,
    supportsAnchorBottom: Boolean,
    onUpdate: (ViewSettings) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        if (showHeader) {
            LibrarySectionHeader(stringResource(R.string.view_appearance))
        }
        val viewTypes = if (isAlbumMode || isContainerMode) {
            ViewType.entries.filter { it != ViewType.MOSAIC }
        } else {
            ViewType.entries
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            viewTypes.forEachIndexed { i, vt ->
                val labelRes = when (vt) {
                    ViewType.GRID -> R.string.view_type_grid
                    ViewType.LIST -> R.string.view_type_list
                    ViewType.MOSAIC -> R.string.view_type_mosaic
                }
                SegmentedButton(
                    selected = local.viewType == vt,
                    onClick = { onUpdate(local.copy(viewType = vt)) },
                    shape = SegmentedButtonDefaults.itemShape(i, viewTypes.size),
                ) { Text(stringResource(labelRes)) }
            }
        }

        ViewSettingsDensityOrColumnsRow(local = local, onUpdate = onUpdate)

        if (showDisplayMode) {
            DisplayModeRow(
                viewType = local.viewType,
                displayMode = local.displayMode,
                onSelect = { onUpdate(local.copy(displayMode = it)) },
            )
        }

        ViewSettingsLayoutOptions(
            local = local,
            isAlbumMode = isAlbumMode,
            isContainerMode = isContainerMode,
            supportsAnchorBottom = supportsAnchorBottom,
            onUpdate = onUpdate,
        )
    }
}

@Composable
private fun ViewSettingsDensityOrColumnsRow(local: ViewSettings, onUpdate: (ViewSettings) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        if (local.viewType != ViewType.LIST) {
            Text(
                stringResource(R.string.columns),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                for (c in 2..6) {
                    SegmentedButton(
                        selected = local.columnCount == c,
                        onClick = { onUpdate(local.copy(columnCount = c)) },
                        shape = SegmentedButtonDefaults.itemShape(c - 2, 5),
                        // Suppress the wide checkmark icon on 5 narrow numeric cells so the active
                        // digit stays centered and never collides with the icon.
                        icon = {},
                    ) { Text("$c", fontWeight = FontWeight.SemiBold) }
                }
            }
        } else {
            Text(
                stringResource(R.string.list_density),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ListDensity.entries.forEachIndexed { i, density ->
                    val labelRes = if (density == ListDensity.COMPACT) {
                        R.string.list_density_compact
                    } else {
                        R.string.list_density_comfortable
                    }
                    SegmentedButton(
                        selected = local.listDensity == density,
                        onClick = { onUpdate(local.copy(listDensity = density)) },
                        shape = SegmentedButtonDefaults.itemShape(i, ListDensity.entries.size),
                    ) { Text(stringResource(labelRes)) }
                }
            }
        }
    }
}

@Composable
private fun DisplayModeRow(
    viewType: ViewType,
    displayMode: DisplayMode,
    onSelect: (DisplayMode) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        val labelRes = if (viewType == ViewType.LIST) {
            R.string.list_background_label
        } else {
            R.string.tile_background_label
        }
        Text(
            stringResource(labelRes),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        val visibleModes = listOf(DisplayMode.NORMAL, DisplayMode.DARK)
        val effectiveMode = if (displayMode == DisplayMode.DARK) DisplayMode.DARK else DisplayMode.NORMAL
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            visibleModes.forEachIndexed { i, dm ->
                val modeRes = if (dm == DisplayMode.DARK) {
                    R.string.tile_background_dark
                } else {
                    R.string.tile_background_default
                }
                SegmentedButton(
                    selected = effectiveMode == dm,
                    onClick = { onSelect(dm) },
                    shape = SegmentedButtonDefaults.itemShape(i, visibleModes.size),
                ) {
                    Text(stringResource(modeRes), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun ViewSettingsLayoutOptions(
    local: ViewSettings,
    isAlbumMode: Boolean,
    isContainerMode: Boolean,
    supportsAnchorBottom: Boolean,
    onUpdate: (ViewSettings) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        // List rows always display the filename as their primary title; only grid/mosaic tiles
        // treat filenames as an optional caption.
        if (!isAlbumMode && !isContainerMode && local.viewType != ViewType.LIST) {
            LibrarySectionHeader(stringResource(R.string.view_settings_metadata))
            ViewSettingsSwitchRow(
                label = stringResource(R.string.show_filenames),
                checked = local.showFileNames,
                onCheckedChange = { onUpdate(local.copy(showFileNames = it)) },
            )
        }
        LibrarySectionHeader(stringResource(R.string.view_settings_layout))
        ViewSettingsSwitchRow(
            label = stringResource(R.string.rounded_corners),
            checked = local.roundedCorners,
            onCheckedChange = { onUpdate(local.copy(roundedCorners = it)) },
        )
        if (isAlbumMode || isContainerMode) {
            ViewSettingsSwitchRow(
                label = stringResource(R.string.folder_preview),
                checked = local.showFolderThumbnails,
                onCheckedChange = { onUpdate(local.copy(showFolderThumbnails = it)) },
            )
        }
        SpacingPresetsRow(spacing = local.spacing, onSelect = { onUpdate(local.copy(spacing = it)) })
        if (supportsAnchorBottom) {
            Spacer(Modifier.height(6.dp))
            ViewSettingsSwitchRow(
                label = stringResource(R.string.anchor_bottom),
                checked = local.anchorBottom,
                onCheckedChange = { onUpdate(local.copy(anchorBottom = it)) },
            )
            Text(
                stringResource(R.string.anchor_bottom_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun SpacingPresetsRow(spacing: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Text(
            stringResource(R.string.spacing),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        val spacingPresets = listOf(
            2 to R.string.spacing_tight,
            8 to R.string.spacing_normal,
            14 to R.string.spacing_airy,
        )
        val selectedPreset = when {
            spacing <= 4 -> 2
            spacing <= 11 -> 8
            else -> 14
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            spacingPresets.forEachIndexed { i, (dpValue, labelRes) ->
                SegmentedButton(
                    selected = selectedPreset == dpValue,
                    onClick = { onSelect(dpValue) },
                    shape = SegmentedButtonDefaults.itemShape(i, spacingPresets.size),
                ) {
                    Text(stringResource(labelRes), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
internal fun ViewSettingsArrangeSection(
    local: ViewSettings,
    surface: ViewSettingsSurface,
    showHeader: Boolean,
    isAlbumMode: Boolean,
    isContainerMode: Boolean,
    supportsTagGrouping: Boolean,
    onUpdate: (ViewSettings) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        if (showHeader) {
            LibrarySectionHeader(stringResource(R.string.arrange))
        }
        if (isAlbumMode) {
            Text(
                stringResource(R.string.folder_sort_scope_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        val sortFields = sortFieldsFor(surface)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            sortFields.forEachIndexed { i, sf ->
                SegmentedButton(
                    selected = local.sortBy == sf,
                    onClick = { onUpdate(local.copy(sortBy = sf)) },
                    shape = SegmentedButtonDefaults.itemShape(i, sortFields.size),
                    // Suppress the checkmark icon on 4+ button rows so long labels (e.g. Bewertung)
                    // never collide with the icon or ellipsize when selected.
                    icon = {
                        if (sortFields.size <= MAX_SEGMENTED_ICON_ITEMS) {
                            SegmentedButtonDefaults.Icon(active = local.sortBy == sf)
                        }
                    },
                ) {
                    Text(sortFieldLabel(sf), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        SortDirectionRow(
            sortBy = local.sortBy,
            sortDesc = local.sortDesc,
            onSelect = { onUpdate(local.copy(sortDesc = it)) },
        )

        if (!isAlbumMode && !isContainerMode) {
            ViewSettingsGroupingSection(
                local = local,
                supportsTagGrouping = supportsTagGrouping,
                onUpdate = onUpdate,
            )
        }
    }
}

@Composable
private fun SortDirectionRow(
    sortBy: SortField,
    sortDesc: Boolean,
    onSelect: (Boolean) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Text(
            stringResource(R.string.sort_order_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        val directionOptions = when (sortBy) {
            SortField.NAME -> listOf(false to R.string.sort_dir_az, true to R.string.sort_dir_za)
            SortField.SIZE -> listOf(true to R.string.sort_dir_largest, false to R.string.sort_dir_smallest)
            SortField.COUNT -> listOf(true to R.string.sort_dir_most, false to R.string.sort_dir_fewest)
            SortField.RATING -> listOf(true to R.string.sort_dir_highest, false to R.string.sort_dir_lowest)
            SortField.DATE, SortField.RECENT ->
                listOf(true to R.string.sort_dir_newest, false to R.string.sort_dir_oldest)
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            directionOptions.forEachIndexed { index, (isDesc, labelRes) ->
                SegmentedButton(
                    selected = sortDesc == isDesc,
                    onClick = { onSelect(isDesc) },
                    shape = SegmentedButtonDefaults.itemShape(index, directionOptions.size),
                ) {
                    Text(stringResource(labelRes), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun ViewSettingsGroupingSection(
    local: ViewSettings,
    supportsTagGrouping: Boolean,
    onUpdate: (ViewSettings) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))
        ViewSettingsSwitchRow(
            label = stringResource(R.string.grouping),
            checked = local.groupBy != GroupBy.NONE,
            onCheckedChange = { on -> onUpdate(local.copy(groupBy = if (on) GroupBy.MONTH else GroupBy.NONE)) },
            labelStyle = MaterialTheme.typography.labelMedium,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (local.groupBy != GroupBy.NONE) {
            GroupingMethodChips(
                selected = local.groupBy,
                supportsTagGrouping = supportsTagGrouping,
                onSelect = { onUpdate(local.copy(groupBy = it)) },
            )
            if (local.groupBy == GroupBy.TAG) {
                TagGroupingOptions(local = local, onUpdate = onUpdate)
            }
            Text(
                stringResource(R.string.grouping_sort_hint, sortFieldLabel(local.sortBy)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun GroupingMethodChips(
    selected: GroupBy,
    supportsTagGrouping: Boolean,
    onSelect: (GroupBy) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        val groupOptions = buildList {
            add(GroupBy.MONTH to stringResource(R.string.group_by_month))
            add(GroupBy.SIZE to stringResource(R.string.group_by_size))
            add(GroupBy.ALPHABET to stringResource(R.string.group_by_alphabet))
            add(GroupBy.RATING to stringResource(R.string.group_by_rating))
            if (supportsTagGrouping) add(GroupBy.TAG to stringResource(R.string.group_by_tag))
        }
        Text(
            stringResource(R.string.grouping_method),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            groupOptions.forEach { (group, label) ->
                FilterChip(
                    selected = selected == group,
                    onClick = { onSelect(group) },
                    label = { Text(label, maxLines = 1) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
        if (!supportsTagGrouping) {
            Text(
                stringResource(R.string.group_by_tag_unavailable_paged_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun TagGroupingOptions(local: ViewSettings, onUpdate: (ViewSettings) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(
            stringResource(R.string.group_order_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        val orders = GroupOrder.entries.toList()
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            orders.forEachIndexed { i, go ->
                val labelRes = if (go == GroupOrder.ALPHABETICAL) {
                    R.string.group_order_alphabetical
                } else {
                    R.string.group_order_count
                }
                SegmentedButton(
                    selected = local.groupOrder == go,
                    onClick = { onUpdate(local.copy(groupOrder = go)) },
                    shape = SegmentedButtonDefaults.itemShape(i, orders.size),
                ) { Text(stringResource(labelRes)) }
            }
        }
        ViewSettingsSwitchRow(
            label = stringResource(R.string.only_top_level_tags),
            checked = local.onlyTopLevelTags,
            onCheckedChange = { onUpdate(local.copy(onlyTopLevelTags = it)) },
        )
        Text(
            stringResource(R.string.only_top_level_tags_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
    }
}

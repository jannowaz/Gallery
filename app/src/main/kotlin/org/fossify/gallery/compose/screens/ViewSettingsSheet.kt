package org.fossify.gallery.compose.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.fossify.gallery.R
import org.fossify.gallery.compose.components.LibrarySectionHeader
import org.fossify.gallery.compose.components.LibrarySheetHeader

/**
 * Keeps presentation choices separate from ordering choices when this content is embedded in the
 * library controls sheet. [ALL] is retained for the standalone sheet used by folder screens.
 */
enum class ViewSettingsPanel { ALL, DISPLAY, ARRANGE }

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
    scopeDescription: String? = null,
    isAlbumMode: Boolean = false,
    supportsTagGrouping: Boolean = true,
    supportsSorting: Boolean = true,
    supportsAnchorBottom: Boolean = true,
    showApplyGloballyToggle: Boolean = false,
    initialApplyGlobally: Boolean = true,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
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
            scopeDescription = scopeDescription,
            isAlbumMode = isAlbumMode,
            supportsTagGrouping = supportsTagGrouping,
            supportsSorting = supportsSorting,
            supportsAnchorBottom = supportsAnchorBottom,
            showApplyGloballyToggle = showApplyGloballyToggle,
            initialApplyGlobally = initialApplyGlobally,
        )
    }
}

/**
 * The sheet's actual content, factored out of [ViewSettingsSheet]'s [ModalBottomSheet] so the
 * combined Filter/Ansicht sheet can render it inside its own single sheet instead of nesting a
 * second modal sheet.
 */
@Composable
fun ViewSettingsContent(
    settings: ViewSettings,
    panel: ViewSettingsPanel = ViewSettingsPanel.ALL,
    showDisplayMode: Boolean = true,
    onSettingsChange: (ViewSettings, Boolean) -> Unit,
    onDismiss: () -> Unit,
    modeTitle: String? = null,
    onToggleMode: (() -> Unit)? = null,
    modeOptions: List<String>? = null,
    scopeDescription: String? = null,
    isAlbumMode: Boolean = false,
    isContainerMode: Boolean = false,
    supportsRecentTagSorting: Boolean = false,
    supportsTagGrouping: Boolean = true,
    supportsSorting: Boolean = true,
    supportsAnchorBottom: Boolean = true,
    showApplyGloballyToggle: Boolean = false,
    initialApplyGlobally: Boolean = true,
) {
    var activePanel by remember(panel) {
        mutableStateOf(if (panel == ViewSettingsPanel.ALL) ViewSettingsPanel.DISPLAY else panel)
    }
    val showsPanelSwitcher = panel == ViewSettingsPanel.ALL && supportsSorting
    val surface = resolveViewSettingsSurface(
        supportsRecentTagSorting = supportsRecentTagSorting,
        isContainerMode = isContainerMode,
        isAlbumMode = isAlbumMode,
        supportsTagGrouping = supportsTagGrouping,
    )
    var local by remember(settings, surface) { mutableStateOf(settings.sanitizeFor(surface)) }
    var applyGlobally by remember(settings, initialApplyGlobally) { mutableStateOf(initialApplyGlobally) }
    var showHelp by remember { mutableStateOf(false) }

    fun updateLocal(updated: ViewSettings) {
        local = updated
        onSettingsChange(updated, applyGlobally)
    }

    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        LibrarySheetHeader(
            title = panelTitle(panel),
            onDismiss = onDismiss,
            trailing = scopeDescription?.let {
                {
                    IconButton(onClick = { showHelp = true }) {
                        Icon(Icons.Default.Info, contentDescription = stringResource(R.string.action_info))
                    }
                }
            },
        )
        ViewSettingsScopeHeader(
            modeTitle = modeTitle,
            modeOptions = modeOptions,
            scopeDescription = scopeDescription,
            onToggleMode = onToggleMode,
        )
        Spacer(Modifier.height(8.dp))

        if (showsPanelSwitcher) {
            ViewSettingsPanelSwitcher(activePanel = activePanel, onSelect = { activePanel = it })
            Spacer(Modifier.height(10.dp))
        }

        if (showApplyGloballyToggle) {
            LibrarySectionHeader(stringResource(R.string.view_settings_scope))
            ViewSettingsSwitchRow(
                label = stringResource(R.string.apply_settings_globally),
                checked = applyGlobally,
                onCheckedChange = { applyGlobally = it; onSettingsChange(local, it) },
            )
            Text(
                stringResource(R.string.apply_settings_globally_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(8.dp))
        }

        // Stabilize minimum height across Display <-> Arrange switches so the segmented tab
        // header does not jump vertically under the user's finger.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (showsPanelSwitcher) Modifier.heightIn(min = 380.dp) else Modifier)
                .animateContentSize(),
        ) {
            if (activePanel != ViewSettingsPanel.ARRANGE) {
                ViewSettingsDisplaySection(
                    local = local,
                    showHeader = !showsPanelSwitcher,
                    showDisplayMode = showDisplayMode,
                    isAlbumMode = isAlbumMode,
                    isContainerMode = isContainerMode,
                    supportsAnchorBottom = supportsAnchorBottom,
                    onUpdate = ::updateLocal,
                )
            }
            if (activePanel != ViewSettingsPanel.DISPLAY && supportsSorting) {
                ViewSettingsArrangeSection(
                    local = local,
                    surface = surface,
                    showHeader = !showsPanelSwitcher,
                    isAlbumMode = isAlbumMode,
                    isContainerMode = isContainerMode,
                    supportsTagGrouping = supportsTagGrouping,
                    onUpdate = ::updateLocal,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text(stringResource(R.string.view_settings_help_title)) },
            text = { Text(stringResource(R.string.view_settings_help_text)) },
            confirmButton = {
                TextButton(onClick = { showHelp = false }) { Text(stringResource(R.string.action_done)) }
            },
        )
    }
}

private fun resolveViewSettingsSurface(
    supportsRecentTagSorting: Boolean,
    isContainerMode: Boolean,
    isAlbumMode: Boolean,
    supportsTagGrouping: Boolean,
): ViewSettingsSurface = when {
    supportsRecentTagSorting -> ViewSettingsSurface.TAGS
    isContainerMode -> ViewSettingsSurface.COLLECTIONS
    isAlbumMode -> ViewSettingsSurface.ALBUMS
    supportsTagGrouping -> ViewSettingsSurface.COLLECTION_MEDIA
    else -> ViewSettingsSurface.MEDIA
}

@Composable
private fun panelTitle(panel: ViewSettingsPanel): String = when (panel) {
    ViewSettingsPanel.ALL -> stringResource(R.string.view_settings_title)
    ViewSettingsPanel.DISPLAY -> stringResource(R.string.view_appearance)
    ViewSettingsPanel.ARRANGE -> stringResource(R.string.arrange)
}

@Composable
private fun ViewSettingsScopeHeader(
    modeTitle: String?,
    modeOptions: List<String>?,
    scopeDescription: String?,
    onToggleMode: (() -> Unit)?,
) {
    if (modeOptions != null && onToggleMode != null) {
        Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp)) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.56f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        stringResource(R.string.settings_scope_choose),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Spacer(Modifier.height(8.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        modeOptions.forEachIndexed { i, label ->
                            SegmentedButton(
                                selected = label == modeTitle,
                                onClick = { if (label != modeTitle) onToggleMode() },
                                shape = SegmentedButtonDefaults.itemShape(i, modeOptions.size),
                            ) { Text(label, fontWeight = FontWeight.SemiBold, maxLines = 1) }
                        }
                    }
                    scopeDescription?.let { description ->
                        Text(
                            description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        }
    } else if (scopeDescription != null) {
        Text(
            scopeDescription,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )
    }
}

@Composable
private fun ViewSettingsPanelSwitcher(
    activePanel: ViewSettingsPanel,
    onSelect: (ViewSettingsPanel) -> Unit,
) {
    val tabs = listOf(
        ViewSettingsPanel.DISPLAY to stringResource(R.string.view_appearance),
        ViewSettingsPanel.ARRANGE to stringResource(R.string.arrange),
    )
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        tabs.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = activePanel == value,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(index, tabs.size),
            ) {
                Text(label, maxLines = 1)
            }
        }
    }
}

@Composable
internal fun ViewSettingsSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    labelStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    labelColor: Color = Color.Unspecified,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = labelStyle, color = labelColor, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
internal fun sortFieldLabel(sf: SortField): String = when (sf) {
    SortField.NAME -> stringResource(R.string.sort_name)
    SortField.DATE -> stringResource(R.string.sort_date)
    SortField.SIZE -> stringResource(R.string.sort_size)
    SortField.RATING -> stringResource(R.string.sort_rating)
    SortField.COUNT -> stringResource(R.string.sort_by_item_count)
    SortField.RECENT -> stringResource(R.string.sort_recently_used)
}

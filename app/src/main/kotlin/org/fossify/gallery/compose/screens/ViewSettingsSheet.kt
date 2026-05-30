package org.fossify.gallery.compose.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

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
                Text("Ansicht", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Schließen") }
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
            Text("Darstellung", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ViewType.entries.forEachIndexed { i, vt ->
                        SegmentedButton(
                            selected = local.viewType == vt,
                            onClick = { local = local.copy(viewType = vt); onSettingsChange(local) },
                            shape = SegmentedButtonDefaults.itemShape(i, ViewType.entries.size)
                        ) { Text(when(vt) { ViewType.GRID -> "Kacheln"; ViewType.LIST -> "Liste" }) }
                    }
            }

            // Column Count
            Spacer(Modifier.height(12.dp))
            Text("Spalten", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                Text("Anzeigemodus", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    DisplayMode.entries.forEachIndexed { i, dm ->
                        SegmentedButton(
                            selected = local.displayMode == dm,
                            onClick = { local = local.copy(displayMode = dm); onSettingsChange(local) },
                            shape = SegmentedButtonDefaults.itemShape(i, DisplayMode.entries.size)
                        ) { Text(when(dm) { DisplayMode.COMPACT -> "Kompakt"; DisplayMode.NORMAL -> "Normal"; DisplayMode.DARK -> "Dark" }) }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            // Sort
            Text("Sortierung", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SortField.entries.forEachIndexed { i, sf ->
                    SegmentedButton(
                        selected = local.sortBy == sf,
                        onClick = { local = local.copy(sortBy = sf); onSettingsChange(local) },
                        shape = SegmentedButtonDefaults.itemShape(i, SortField.entries.size)
                    ) { Text(when(sf) { SortField.NAME -> "Name"; SortField.DATE -> "Datum"; SortField.SIZE -> "Große"; SortField.RATING -> "Bewertung" }) }
                }
            }

            // Toggles
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Absteigend", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.weight(1f))
                Switch(checked = local.sortDesc, onCheckedChange = { local = local.copy(sortDesc = it); onSettingsChange(local) })
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Dateinamen anzeigen", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.weight(1f))
                Switch(checked = local.showFileNames, onCheckedChange = { local = local.copy(showFileNames = it); onSettingsChange(local) })
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Abgerundete Ecken", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.weight(1f))
                Switch(checked = local.roundedCorners, onCheckedChange = { local = local.copy(roundedCorners = it); onSettingsChange(local) })
            }
            if (showDisplayMode) {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Ordner-Vorschau", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.weight(1f))
                    Switch(checked = local.showFolderThumbnails, onCheckedChange = { local = local.copy(showFolderThumbnails = it); onSettingsChange(local) })
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

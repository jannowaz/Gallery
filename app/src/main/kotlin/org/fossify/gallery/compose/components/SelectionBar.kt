package org.fossify.gallery.compose.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.fossify.gallery.compose.theme.LocalSpacing
import org.fossify.gallery.compose.theme.Radius

/**
 * Proper Material3 selection toolbar (replaces the old bottom bar that used bare clickable Text
 * links for "Alle"/"Inv."). Real buttons with ≥48dp touch targets and an explicit "more actions"
 * affordance instead of a tap-the-whole-bar gesture.
 */
@Composable
fun SelectionBar(
    count: Int,
    onClear: () -> Unit,
    onMoreActions: () -> Unit,
    modifier: Modifier = Modifier,
    onSelectAll: (() -> Unit)? = null,
    onInvert: (() -> Unit)? = null,
) {
    val s = LocalSpacing.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = Radius.lg, topEnd = Radius.lg),
        shadowElevation = 8.dp,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = s.sm, vertical = s.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClear) {
                Icon(Icons.Default.Close, "Auswahl aufheben", tint = MaterialTheme.colorScheme.onSurface)
            }
            Text(
                "$count ausgewählt",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f).padding(start = s.xs),
            )
            if (onSelectAll != null) TextButton(onClick = onSelectAll) { Text("Alle") }
            if (onInvert != null) TextButton(onClick = onInvert) { Text("Umkehren") }
            FilledTonalIconButton(onClick = onMoreActions) {
                Icon(Icons.Default.MoreHoriz, "Aktionen")
            }
        }
    }
}

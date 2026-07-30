package org.fossify.gallery.compose.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.fossify.gallery.R

/**
 * Shown before a Move/Copy batch when some incoming files would collide with same-named files already
 * in the destination. Every resolution is recoverable - nothing is ever permanently overwritten:
 * keep both (rename the incoming file), replace (the existing file goes to the recycle bin), or skip.
 */
@Composable
fun MoveConflictDialog(
    conflictCount: Int,
    onKeepBoth: () -> Unit,
    onReplace: () -> Unit,
    onSkip: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(stringResource(R.string.move_conflict_title)) },
        text = {
            Column {
                Text(stringResource(R.string.move_conflict_text, conflictCount))
                Spacer(Modifier.height(12.dp))
                Option(Icons.Default.CallSplit, stringResource(R.string.move_conflict_keep_both), onKeepBoth)
                Spacer(Modifier.height(6.dp))
                Option(Icons.Default.SwapHoriz, stringResource(R.string.move_conflict_replace), onReplace)
                Spacer(Modifier.height(6.dp))
                Option(Icons.Outlined.Block, stringResource(R.string.move_conflict_skip), onSkip)
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.move_conflict_recoverable_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun Option(icon: ImageVector, label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Icon(icon, null, Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
    }
}

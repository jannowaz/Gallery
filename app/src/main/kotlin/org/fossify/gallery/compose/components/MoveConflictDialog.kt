package org.fossify.gallery.compose.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.fossify.gallery.R

/**
 * Shown before a Move/Copy batch when some incoming files would collide with same-named files already
 * in the destination. Offers only safe resolutions - keep both (rename the incoming file) or skip -
 * so nothing is ever overwritten. "Cancel" aborts the whole operation.
 */
@Composable
fun MoveConflictDialog(
    conflictCount: Int,
    onKeepBoth: () -> Unit,
    onSkip: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(stringResource(R.string.move_conflict_title)) },
        text = { Text(stringResource(R.string.move_conflict_text, conflictCount)) },
        confirmButton = { TextButton(onClick = onKeepBoth) { Text(stringResource(R.string.move_conflict_keep_both)) } },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onSkip) { Text(stringResource(R.string.move_conflict_skip)) }
                TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

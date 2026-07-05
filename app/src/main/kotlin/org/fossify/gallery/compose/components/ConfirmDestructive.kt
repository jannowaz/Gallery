package org.fossify.gallery.compose.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import org.fossify.gallery.R

/**
 * Unified destructive-confirmation dialog: a title, an explanatory message and an error-coloured
 * confirm button. Use for every "really delete?" style action so destructive flows look and behave
 * the same everywhere. The caller's [onConfirm] is responsible for dismissing (e.g. clearing the
 * trigger state) and performing the action.
 */
@Composable
fun ConfirmDestructive(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.Confirm); onConfirm() }) {
                Text(confirmLabel, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

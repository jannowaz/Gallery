package org.fossify.gallery.compose.util

import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import org.fossify.gallery.R

/**
 * Guards a screen whose state is expensive to rebuild (a long storage/duplicate scan, a running
 * swipe session) against accidental exits. A real dialog makes the available choice explicit and
 * avoids a timed toast that is especially easy to miss with TalkBack or large text.
 */
@Composable
fun rememberDoubleBackGuard(
    enabled: Boolean,
    onExit: () -> Unit,
    running: Boolean = enabled,
    onCancel: (() -> Unit)? = null,
): () -> Unit {
    var showExitDialog by remember { mutableStateOf(false) }
    val currentEnabled by rememberUpdatedState(enabled)
    val currentOnExit by rememberUpdatedState(onExit)
    val currentRunning by rememberUpdatedState(running)
    val currentOnCancel by rememberUpdatedState(onCancel)
    val guarded = remember {
        {
            if (!currentEnabled) currentOnExit() else showExitDialog = true
        }
    }
    val guardBackState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
    NavigationBackHandler(
        state = guardBackState,
        isBackEnabled = enabled,
        onBackCompleted = { guarded() },
    )

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text(stringResource(if (currentRunning) R.string.analysis_exit_running_title else R.string.analysis_exit_title)) },
            text = { Text(stringResource(if (currentRunning) R.string.analysis_exit_running_body else R.string.analysis_exit_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showExitDialog = false
                    (if (currentRunning) currentOnCancel ?: currentOnExit else currentOnExit)()
                }) {
                    Text(stringResource(if (currentRunning && currentOnCancel != null) R.string.analysis_cancel_and_exit else R.string.leave))
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text(stringResource(R.string.stay_here))
                }
            },
        )
    }
    return guarded
}

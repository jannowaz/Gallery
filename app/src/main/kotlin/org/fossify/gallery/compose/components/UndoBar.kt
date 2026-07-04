package org.fossify.gallery.compose.components
import androidx.compose.ui.res.stringResource
import org.fossify.gallery.R

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.fossify.gallery.helpers.UndoAction
import org.fossify.gallery.helpers.UndoManager
import org.fossify.gallery.helpers.UndoType

@Composable
fun UndoBar(modifier: Modifier = Modifier, onActionLabel: ((UndoAction) -> String)? = null) {
    val actions by UndoManager.actions.collectAsState()
    val lastAction = actions.lastOrNull()
    val scope = rememberCoroutineScope()

    LaunchedEffect(lastAction?.timestamp) {
        val ts = lastAction?.timestamp
        if (ts != null) {
            delay(5000)
            UndoManager.remove(ts)
        }
    }

    AnimatedVisibility(
        visible = lastAction != null,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.inverseSurface,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            shadowElevation = 8.dp,
        ) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                val label = if (onActionLabel != null && lastAction != null) onActionLabel(lastAction)
                else when (lastAction?.type) {
                    UndoType.DELETE -> stringResource(R.string.undo_deleted_count, lastAction?.paths?.size ?: 0)
                    UndoType.MOVE -> stringResource(R.string.undo_moved_count, lastAction?.paths?.size ?: 0)
                    UndoType.TAG_ADD -> stringResource(R.string.undo_tag_added)
                    UndoType.TAG_REMOVE -> stringResource(R.string.undo_tag_removed)
                    UndoType.RATING_CHANGE -> stringResource(R.string.undo_rating_changed)
                    null -> ""
                }
                Text(label, color = MaterialTheme.colorScheme.inverseOnSurface, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { scope.launch { UndoManager.undoLast() } }) {
                    Text(stringResource(R.string.undo), color = MaterialTheme.colorScheme.inversePrimary, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

package org.fossify.gallery.compose.components
import androidx.compose.ui.res.stringResource
import org.fossify.gallery.R

import android.content.Context
import android.os.Build
import android.view.accessibility.AccessibilityManager
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.fossify.gallery.compose.theme.AppMotion
import org.fossify.gallery.helpers.UndoAction
import org.fossify.gallery.helpers.UndoManager
import org.fossify.gallery.helpers.UndoType

@Composable
fun UndoBar(modifier: Modifier = Modifier, onActionLabel: ((UndoAction) -> String)? = null) {
    val actions by UndoManager.actions.collectAsStateWithLifecycle()
    val inFlightActionId by UndoManager.inFlightActionId.collectAsStateWithLifecycle()
    val lastAction = actions.lastOrNull()
    val visibleAction = lastAction
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val accessibilityManager = remember(context) {
        context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
    }
    val timeoutMillis = remember(lastAction?.id, accessibilityManager) {
        val baseTimeout = 5_000
        if (Build.VERSION.SDK_INT >= 29) {
            accessibilityManager?.getRecommendedTimeoutMillis(
                baseTimeout,
                AccessibilityManager.FLAG_CONTENT_TEXT or AccessibilityManager.FLAG_CONTENT_CONTROLS,
            ) ?: baseTimeout
        } else {
            baseTimeout
        }
    }
    var undoError by remember(visibleAction?.id) { mutableStateOf(false) }

    LaunchedEffect(visibleAction?.id, timeoutMillis) {
        val actionId = visibleAction?.id
        if (actionId != null) {
            delay(timeoutMillis.toLong())
            UndoManager.remove(actionId)
        }
    }

    AnimatedVisibility(
        visible = visibleAction != null,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(AppMotion.medium),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(AppMotion.medium),
        modifier = modifier,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            color = MaterialTheme.colorScheme.inverseSurface,
            shape = RoundedCornerShape(16.dp),
            shadowElevation = 8.dp,
        ) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                val label = when {
                    inFlightActionId != null -> stringResource(R.string.undo_in_progress)
                    undoError -> stringResource(R.string.undo_failed_retry)
                    onActionLabel != null && visibleAction != null -> onActionLabel(visibleAction)
                    else -> when (visibleAction?.type) {
                        UndoType.DELETE -> stringResource(R.string.undo_deleted_count, visibleAction.paths.size)
                        UndoType.MOVE -> stringResource(R.string.undo_moved_count, visibleAction.paths.size)
                        UndoType.TAG_ADD -> stringResource(R.string.undo_tag_added)
                        UndoType.TAG_REMOVE -> stringResource(R.string.undo_tag_removed)
                        UndoType.TAG_RESTORE_SNAPSHOT -> stringResource(R.string.undo_tags_restored)
                        UndoType.TAG_HIERARCHY -> stringResource(R.string.undo_tag_hierarchy)
                        UndoType.RATING_CHANGE -> stringResource(R.string.undo_rating_changed)
                        UndoType.COMPRESS_REPLACE -> stringResource(R.string.undo_compress_replaced)
                        UndoType.OPTIMIZE_REPLACE -> stringResource(R.string.undo_optimized_count, visibleAction?.paths?.size ?: 0)
                        null -> ""
                    }
                }
                Text(label, color = MaterialTheme.colorScheme.inverseOnSurface, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                TextButton(
                    enabled = inFlightActionId == null,
                    onClick = {
                        scope.launch {
                            undoError = !UndoManager.undoLast()
                        }
                    },
                ) {
                    Text(stringResource(if (undoError) R.string.retry else R.string.undo), color = MaterialTheme.colorScheme.inversePrimary, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

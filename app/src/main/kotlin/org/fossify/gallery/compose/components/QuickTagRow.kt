package org.fossify.gallery.compose.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.fossify.gallery.compose.theme.AppMotion
import org.fossify.gallery.compose.theme.Radius

/**
 * One-tap tag row shown above a selection while quick-tagging a batch (rename -> tag -> rate
 * workflow). Shared by every media grid/list variant so the row looks and behaves identically
 * regardless of which layout (grid/mosaic/list, paged or not) is currently active.
 */
@Composable
fun QuickTagRow(
    quickTags: List<String>,
    visible: Boolean,
    selectedCommonTags: Set<String>,
    onToggleTag: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(AppMotion.medium) + slideInVertically { -it },
        exit = fadeOut(AppMotion.medium) + slideOutVertically { -it },
        modifier = modifier,
    ) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            quickTags.forEach { tag ->
                val active = tag in selectedCommonTags
                Surface(
                    onClick = { onToggleTag(tag) },
                    shape = RoundedCornerShape(Radius.lg),
                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        tag,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

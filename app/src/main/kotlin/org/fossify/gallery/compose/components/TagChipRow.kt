package org.fossify.gallery.compose.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.fossify.gallery.compose.theme.BlurRadius
import org.fossify.gallery.compose.theme.Radius
import org.fossify.gallery.compose.util.BlurState
import org.fossify.gallery.compose.util.privacyBlur

/**
 * Shared compact tag presentation for media tiles and rows.
 *
 * Keeping this in one component makes tag labels, overflow handling and privacy treatment match
 * across Media, Favorites, Collections and Explorer. Individual visible tags remain actionable;
 * the overflow chip is deliberately informational because it represents several tags at once.
 */
@Composable
fun TagChipRow(
    tags: List<String>,
    maxVisible: Int,
    onTagClick: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val distinctTags = remember(tags) { tags.filter { it.isNotBlank() }.distinct() }
    val visibleTags = distinctTags.take(maxVisible.coerceAtLeast(0))
    if (visibleTags.isEmpty()) return
    val overflow = (distinctTags.size - visibleTags.size).coerceAtLeast(0)
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        visibleTags.forEach { tag ->
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.78f),
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = RoundedCornerShape(Radius.xs),
                modifier = Modifier
                    .heightIn(min = 28.dp)
                    .semantics { if (onTagClick != null) role = Role.Button }
                    .clickable(enabled = onTagClick != null) { onTagClick?.invoke(tag) },
            ) {
                Text(
                    text = tag,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                        .privacyBlur(BlurRadius.thumbnail, BlurState.enabled),
                )
            }
        }
        if (overflow > 0) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                shape = RoundedCornerShape(Radius.xs),
            ) {
                Text(
                    text = "+$overflow",
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                )
            }
        }
    }
}

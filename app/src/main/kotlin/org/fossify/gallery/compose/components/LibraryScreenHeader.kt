package org.fossify.gallery.compose.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import org.fossify.gallery.R
import org.fossify.gallery.compose.theme.LocalSpacing

/** Compact, shared header for library surfaces that do not need a full top app bar. */
@Composable
fun LibraryScreenHeader(
    title: String,
    subtitle: String? = null,
    leadingIcon: ImageVector? = null,
    navigation: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val s = LocalSpacing.current
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = s.md, vertical = s.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (navigation != null) {
            Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                navigation()
            }
            Spacer(Modifier.width(s.xs))
        } else if (leadingIcon != null) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    modifier = Modifier.padding(10.dp),
                )
            }
            Spacer(Modifier.width(s.md))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                // titleLarge keeps library headers prominent without making the first row feel
                // pushed down by a display-sized title. The same role is now used by Albums,
                // Favorites and Collections, so the tabs share one visual rhythm.
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrEmpty()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        actions()
    }
}

/**
 * Shared refresh action for library headers. Keeping the progress state inside the button makes
 * manual refreshes behave the same on Albums, Favorites, Collections and the tag browser.
 */
@Composable
fun RefreshActionButton(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val refreshDescription = stringResource(if (isRefreshing) R.string.scanning_short else R.string.action_refresh)
    IconButton(
        onClick = onRefresh,
        enabled = enabled && !isRefreshing,
        modifier = modifier.semantics {
            contentDescription = refreshDescription
        },
    ) {
        if (isRefreshing) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
            )
        } else {
            Icon(Icons.Default.Refresh, contentDescription = null)
        }
    }
}

package org.fossify.gallery.compose.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.fossify.gallery.compose.theme.LocalSpacing
import org.fossify.gallery.compose.theme.Radius

/**
 * Visible, thumb-reachable search entry shown at the bottom (just above the nav bar). Tapping it
 * opens the OmniSearch sheet — replaces the previously hidden swipe-up gesture as the primary
 * affordance for such an important feature.
 */
@Composable
fun BottomSearchBar(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val s = LocalSpacing.current
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().padding(horizontal = s.md, vertical = s.xs),
        shape = RoundedCornerShape(Radius.xl),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp,
    ) {
        Row(Modifier.padding(horizontal = s.lg, vertical = s.md), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(s.md))
            Text(
                "Medien, Ordner & Tags durchsuchen",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

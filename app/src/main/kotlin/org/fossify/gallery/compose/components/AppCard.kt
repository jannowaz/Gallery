package org.fossify.gallery.compose.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape

/**
 * Tokenized card: M3 [MaterialTheme.shapes] medium corner radius and a tonally-elevated
 * `surfaceContainer` background by default. Use instead of ad-hoc `Card`/`Surface` with hand-picked
 * radii so every card looks consistent and follows the design tokens.
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    shape: Shape = MaterialTheme.shapes.medium,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = color),
        content = content,
    )
}

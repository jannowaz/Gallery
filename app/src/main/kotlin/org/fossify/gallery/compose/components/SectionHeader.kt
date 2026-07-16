package org.fossify.gallery.compose.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.fossify.gallery.compose.theme.RatingStarColor
import kotlin.math.abs

/** Deterministic accent color for a tag-hierarchy branch, hashed from the root tag's own name so
 * the same tag always gets the same color across sessions/screens without persisting anything.
 * Fixed saturation/lightness (mid-tone, moderately saturated) so it stays legible as a thin
 * border accent in both light and dark theme. */
fun tagAccentColor(rootTag: String): Color {
    val hue = (abs(rootTag.hashCode()) % 360).toFloat()
    return Color.hsv(hue, 0.45f, 0.75f)
}

/** Full-width section header for a grouped media grid/list (month, tag or rating). Generalizes
 * the previous month-only header with indentation ([depth], for nested tag hierarchies), an
 * optional expand/collapse chevron ([hasChildren]/[isExpanded]/[onToggle]), a per-branch color
 * accent ([accentColor]), a tree guide line ([showGuideLine], list view only - see MediaScreen/
 * ExplorerScreen call sites), and real star icons instead of text for a rating header
 * ([ratingValue]). */
@Composable
fun SectionHeader(
    label: String,
    count: Int,
    depth: Int = 0,
    hasChildren: Boolean = false,
    isExpanded: Boolean = true,
    onToggle: () -> Unit = {},
    accentColor: Color? = null,
    showGuideLine: Boolean = false,
    ratingValue: Int? = null,
) {
    val rotation by animateFloatAsState(if (isExpanded) 0f else -90f, label = "sectionHeaderChevron")
    // Root headers stay bold and fully opaque; each level deeper is progressively lighter/smaller,
    // so the hierarchy reads from typography alone even before indentation/guide lines register.
    val weight = if (depth == 0) FontWeight.SemiBold else FontWeight.Medium
    val textStyle = if (depth == 0) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium
    val alpha = (1f - depth * 0.12f).coerceIn(0.6f, 1f)
    val guideColor = MaterialTheme.colorScheme.outlineVariant
    Surface(
        Modifier
            .fillMaxWidth()
            .drawWithContent {
                // Surface paints its own background as part of drawContent() below, so the accent
                // stripe/guide line must be drawn AFTER content (drawBehind ran before Surface's own
                // background fill and got fully painted over by it - confirmed live on-device, the
                // stripe was invisible until this was switched to drawWithContent).
                drawContent()
                // One rail per row connecting it to its immediate parent's indentation column -
                // a simplified tree connector (not a fully joined T-junction tree), cheap to draw
                // and still enough to visually confirm "this is nested under the row above".
                if (showGuideLine && depth > 0) {
                    val x = ((depth - 1) * 16 + 8).dp.toPx()
                    drawLine(guideColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.5.dp.toPx())
                }
                if (accentColor != null) {
                    drawRect(accentColor, size = size.copy(width = 3.dp.toPx()))
                }
            },
        color = MaterialTheme.colorScheme.background,
    ) {
        Row(
            Modifier
                .let { if (hasChildren) it.clickable(onClick = onToggle) else it }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.width((depth * 16).dp))
            if (hasChildren) {
                Icon(
                    Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp).rotate(rotation),
                )
                Spacer(Modifier.width(4.dp))
            }
            if (ratingValue != null) {
                for (i in 1..5) {
                    Icon(
                        if (i <= ratingValue) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = null,
                        tint = RatingStarColor.copy(alpha = alpha),
                        modifier = Modifier.size(16.dp),
                    )
                }
            } else {
                Text(label, style = textStyle, fontWeight = weight, color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
            }
            Spacer(Modifier.width(8.dp))
            Text("$count", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha))
        }
    }
}

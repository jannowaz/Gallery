package org.fossify.gallery.compose.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import org.fossify.gallery.R
import org.fossify.gallery.compose.theme.RatingStarColor
import java.text.NumberFormat

/** Deterministic, theme-aware accent for a tag-hierarchy branch.
 * The same root keeps the same accent, while the palette stays inside Material's semantic roles
 * instead of introducing unrelated rainbow colours that compete with the media. */
@Composable
fun tagAccentColor(rootTag: String): Color {
    val palette = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.primary.copy(alpha = 0.62f),
        MaterialTheme.colorScheme.secondary.copy(alpha = 0.62f),
        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.62f),
    )
    return palette[Math.floorMod(rootTag.hashCode(), palette.size)]
}

/** Full-width section header for a grouped media grid/list (month, tag or rating). Generalizes
 * the previous month-only header with indentation ([depth], for nested tag hierarchies), an
 * optional expand/collapse chevron ([hasChildren]/[isExpanded]/[onToggle]), a per-branch color
 * accent ([accentColor]), a tree guide line ([showGuideLine], list view only - see MediaScreen/
 * ExplorerScreen call sites), and real star icons instead of text for a rating header
 * ([ratingValue]). */
@OptIn(ExperimentalFoundationApi::class)
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
    onLongClick: (() -> Unit)? = null,
    onSelectGroup: (() -> Unit)? = null,
) {
    val rotation by animateFloatAsState(if (isExpanded) 0f else -90f, label = "sectionHeaderChevron")
    // Root headers stay bold and fully opaque; each level deeper is progressively lighter/smaller,
    // so the hierarchy reads from typography alone even before indentation/guide lines register.
    val weight = if (depth == 0) FontWeight.SemiBold else FontWeight.Medium
    val textStyle = if (depth == 0) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium
    val alpha = (1f - depth * 0.12f).coerceIn(0.6f, 1f)
    val guideColor = MaterialTheme.colorScheme.outlineVariant
    val expandedDescription = stringResource(R.string.section_expanded)
    val collapsedDescription = stringResource(R.string.section_collapsed)
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
                .let {
                    if (hasChildren || onLongClick != null) {
                        it.combinedClickable(
                            role = Role.Button,
                            onClick = { if (hasChildren) onToggle() },
                            onLongClick = onLongClick,
                        )
                    } else it
                }
                .semantics(mergeDescendants = true) {
                    if (hasChildren) stateDescription = if (isExpanded) expandedDescription else collapsedDescription
                }
                .heightIn(min = 48.dp)
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
            ItemCountBadge(NumberFormat.getIntegerInstance().format(count))
            onSelectGroup?.let { select ->
                IconButton(onClick = select, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.SelectAll, stringResource(R.string.action_select_all), modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

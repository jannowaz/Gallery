package org.fossify.gallery.compose.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Central design tokens. Use these instead of ad-hoc dp literals so spacing and corner radii are
 * consistent across the whole app.
 *
 * Spacing scale (4dp base): xs=4, sm=8, md=12, lg=16, xl=24, xxl=32.
 * Access via [LocalSpacing] (`val s = LocalSpacing.current; s.md`) or the [Spacing] default object.
 */
data class Spacing(
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
)

val LocalSpacing = staticCompositionLocalOf { Spacing() }

/** Corner-radius scale. Map UI elements to these instead of picking arbitrary radii. */
object Radius {
    val xs: Dp = 4.dp      // tiny chips, small thumbnails
    val sm: Dp = 8.dp      // thumbnails, list thumbs
    val md: Dp = 12.dp     // cards, buttons, text fields
    val lg: Dp = 16.dp     // chips, sheets top corners
    val xl: Dp = 24.dp     // pill overlays, large containers
}

/** Material3 shape tokens wired into the theme so `shape = MaterialTheme.shapes.medium` is consistent. */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(Radius.xs),
    small = RoundedCornerShape(Radius.sm),
    medium = RoundedCornerShape(Radius.md),
    large = RoundedCornerShape(Radius.lg),
    extraLarge = RoundedCornerShape(Radius.xl),
)

package org.fossify.gallery.compose.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Fixed brand accent for the rating stars. Kept as one token (not a per-file hex literal) so the
 * "filled star" colour is identical everywhere and independent of the dynamic colour scheme.
 */
val RatingStarColor = Color(0xFFFFD700)

/**
 * Fixed brand accent for the "favorited" state (filled heart). Kept as one token instead of a
 * per-file hex literal / the dynamic `primary` colour, so a favorited item reads the same
 * everywhere (thumbnail badge, viewer bottom bar, viewer action sheet) instead of shifting hue
 * depending on which screen shows it or what the current dynamic colour scheme happens to be.
 */
val FavoriteColor = Color(0xFFE91E63)

/**
 * Fixed accent colours for the video trim handles (start/end). Kept as tokens for the same reason
 * as [RatingStarColor] - these mark a start/end pair on a scrubber and need to stay visually
 * distinct from each other and from the dynamic colour scheme.
 */
val TrimStartColor = Color(0xFF64B5F6)
val TrimEndColor = Color(0xFFEF5350)

/**
 * Shared black-scrim alphas for legibility overlays atop photos/videos (thumbnail badges, viewer
 * chrome, gradients). Kept as a fixed black scale independent of the light/dark colour scheme,
 * since these sit on top of arbitrary image content rather than an app surface - this only
 * centralises the alpha step so it isn't re-picked ad hoc at every call site. Reuse the nearest
 * existing step for new UI rather than adding another one-off value.
 */
object Scrim {
    val a20 = Color.Black.copy(alpha = 0.20f)
    val a22 = Color.Black.copy(alpha = 0.22f)
    val a30 = Color.Black.copy(alpha = 0.30f)
    val a32 = Color.Black.copy(alpha = 0.32f)
    val a35 = Color.Black.copy(alpha = 0.35f)
    val a40 = Color.Black.copy(alpha = 0.40f)
    val a50 = Color.Black.copy(alpha = 0.50f)
    val a60 = Color.Black.copy(alpha = 0.60f)
    val a85 = Color.Black.copy(alpha = 0.85f)
}

/**
 * Radii for `Modifier.privacyBlur()` ("blur all media"), named by where they're applied rather
 * than picked ad hoc per call site - a small thumbnail needs less blur to fully obscure its content
 * than a fullscreen viewer frame does, so these are intentionally not all the same value.
 */
object BlurRadius {
    val scrubPreview = 16.dp
    val thumbnail = 24.dp
    val viewer = 32.dp
}

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

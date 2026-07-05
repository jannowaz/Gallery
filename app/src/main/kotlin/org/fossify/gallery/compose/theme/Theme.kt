package org.fossify.gallery.compose.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = md_theme_light_primary,
    onPrimary = md_theme_light_onPrimary,
    primaryContainer = md_theme_light_primaryContainer,
    onPrimaryContainer = md_theme_light_onPrimaryContainer,
    secondary = md_theme_light_secondary,
    onSecondary = md_theme_light_onSecondary,
    secondaryContainer = md_theme_light_secondaryContainer,
    onSecondaryContainer = md_theme_light_onSecondaryContainer,
    tertiary = md_theme_light_tertiary,
    onTertiary = md_theme_light_onTertiary,
    tertiaryContainer = md_theme_light_tertiaryContainer,
    onTertiaryContainer = md_theme_light_onTertiaryContainer,
    error = md_theme_light_error,
    onError = md_theme_light_onError,
    errorContainer = md_theme_light_errorContainer,
    onErrorContainer = md_theme_light_onErrorContainer,
    background = md_theme_light_background,
    onBackground = md_theme_light_onBackground,
    surface = md_theme_light_surface,
    onSurface = md_theme_light_onSurface,
    surfaceVariant = md_theme_light_surfaceVariant,
    onSurfaceVariant = md_theme_light_onSurfaceVariant,
    outline = md_theme_light_outline,
    outlineVariant = md_theme_light_outlineVariant,
    surfaceTint = md_theme_light_primary,
    surfaceDim = md_theme_light_surfaceDim,
    surfaceBright = md_theme_light_surfaceBright,
    surfaceContainerLowest = md_theme_light_surfaceContainerLowest,
    surfaceContainerLow = md_theme_light_surfaceContainerLow,
    surfaceContainer = md_theme_light_surfaceContainer,
    surfaceContainerHigh = md_theme_light_surfaceContainerHigh,
    surfaceContainerHighest = md_theme_light_surfaceContainerHighest,
    inverseSurface = md_theme_light_inverseSurface,
    inverseOnSurface = md_theme_light_inverseOnSurface,
    inversePrimary = md_theme_light_inversePrimary,
)

private val DarkColorScheme = darkColorScheme(
    primary = md_theme_dark_primary,
    onPrimary = md_theme_dark_onPrimary,
    primaryContainer = md_theme_dark_primaryContainer,
    onPrimaryContainer = md_theme_dark_onPrimaryContainer,
    secondary = md_theme_dark_secondary,
    onSecondary = md_theme_dark_onSecondary,
    secondaryContainer = md_theme_dark_secondaryContainer,
    onSecondaryContainer = md_theme_dark_onSecondaryContainer,
    tertiary = md_theme_dark_tertiary,
    onTertiary = md_theme_dark_onTertiary,
    tertiaryContainer = md_theme_dark_tertiaryContainer,
    onTertiaryContainer = md_theme_dark_onTertiaryContainer,
    error = md_theme_dark_error,
    onError = md_theme_dark_onError,
    errorContainer = md_theme_dark_errorContainer,
    onErrorContainer = md_theme_dark_onErrorContainer,
    background = md_theme_dark_background,
    onBackground = md_theme_dark_onBackground,
    surface = md_theme_dark_surface,
    onSurface = md_theme_dark_onSurface,
    surfaceVariant = md_theme_dark_surfaceVariant,
    onSurfaceVariant = md_theme_dark_onSurfaceVariant,
    outline = md_theme_light_outline_dark,
    outlineVariant = md_theme_dark_outlineVariant,
    surfaceTint = md_theme_dark_primary,
    surfaceDim = md_theme_dark_surfaceDim,
    surfaceBright = md_theme_dark_surfaceBright,
    surfaceContainerLowest = md_theme_dark_surfaceContainerLowest,
    surfaceContainerLow = md_theme_dark_surfaceContainerLow,
    surfaceContainer = md_theme_dark_surfaceContainer,
    surfaceContainerHigh = md_theme_dark_surfaceContainerHigh,
    surfaceContainerHighest = md_theme_dark_surfaceContainerHighest,
    inverseSurface = md_theme_dark_inverseSurface,
    inverseOnSurface = md_theme_dark_inverseOnSurface,
    inversePrimary = md_theme_dark_inversePrimary,
)

/**
 * Notifies every [GalleryTheme] host that a theme-affecting Config value (forceDarkMode,
 * forceLightMode, useAmoledBackground, useDynamicColors) changed. Those are plain SharedPreferences-
 * backed vars, not Compose State, so writing them alone does not trigger recomposition anywhere -
 * without this, picking a new theme in Settings only updated that screen's own "Dark"/"Light" label
 * (via its unrelated local settingsVersion trick) while the actual GalleryTheme wrapper - in this
 * Activity and in every other already-running Activity in the process, since this is one global
 * object - kept rendering with whatever darkTheme/amoledBlack it read at its last composition.
 */
object ThemePrefsBus {
    var version by mutableIntStateOf(0)
        private set

    fun invalidate() {
        version++
    }
}

/**
 * Resolves the effective dark/light state from the 3-way Light/Dark/System preference. Shared by
 * every [GalleryTheme] call site so "System" vs "forced" behaves identically everywhere instead of
 * each Activity re-deriving it (and risking drift, e.g. one forgetting the forceLightMode branch).
 *
 * Reads [ThemePrefsBus.version] purely to subscribe the caller's recomposition scope to it - the
 * value itself is unused. Because Compose recomposition is scoped to the whole enclosing composable
 * body, this also refreshes any sibling `conf.xxx` reads in that same call (dynamicColor, amoledBlack)
 * even though they are not routed through this function.
 */
@Composable
fun resolveDarkTheme(forceDarkMode: Boolean, forceLightMode: Boolean): Boolean {
    ThemePrefsBus.version
    return when {
        forceDarkMode -> true
        forceLightMode -> false
        else -> isSystemInDarkTheme()
    }
}

/**
 * Overrides the neutral surface family with true black for OLED/AMOLED screens (saves power, max
 * contrast). Only the background/surface tonal steps are touched - primary/secondary/error/etc.
 * and their "on" colors are left as-is so contrast and brand colours are unaffected.
 */
private fun ColorScheme.withAmoledBlack(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceBright = Color(0xFF1A1A1A),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0D0D0D),
    surfaceContainer = Color(0xFF121212),
    surfaceContainerHigh = Color(0xFF1C1C1C),
    surfaceContainerHighest = Color(0xFF262626),
)

@Composable
fun GalleryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    amoledBlack: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }.let { if (darkTheme && amoledBlack) it.withAmoledBlack() else it }
    val view = LocalView.current
    if (!view.isInEditMode) {
        // No window.statusBarColor here - the Activity already calls enableEdgeToEdge(), which
        // makes the system bars transparent/auto-contrast; setting an explicit statusBarColor on
        // top of that fights edge-to-edge (and the API is deprecated as of API 35). Only the icon
        // appearance (light vs dark glyphs) still needs to track the theme explicitly.
        SideEffect {
            val window = (view.context as Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(colorScheme = colorScheme, typography = Typography, shapes = AppShapes) {
        CompositionLocalProvider(LocalSpacing provides Spacing()) {
            content()
        }
    }
}

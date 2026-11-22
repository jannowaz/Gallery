package org.fossify.gallery.compose.theme

import androidx.compose.ui.graphics.Color

// The non-dynamic fallback intentionally mirrors the device's cool blue/lilac Material You
// palette. Previously the fallback was warm yellow/olive while Android 12+ dynamic color was blue
// on the test device, so switching dynamic color off made the same UI feel like a different app.
val md_theme_light_primary = Color(0xFF465D91)
val md_theme_light_onPrimary = Color(0xFFFFFFFF)
val md_theme_light_primaryContainer = Color(0xFFD9E2FF)
val md_theme_light_onPrimaryContainer = Color(0xFF001A41)
val md_theme_light_secondary = Color(0xFF585E71)
val md_theme_light_onSecondary = Color(0xFFFFFFFF)
val md_theme_light_secondaryContainer = Color(0xFFDCE2F9)
val md_theme_light_onSecondaryContainer = Color(0xFF151B2C)
val md_theme_light_tertiary = Color(0xFF73546A)
val md_theme_light_onTertiary = Color(0xFFFFFFFF)
val md_theme_light_tertiaryContainer = Color(0xFFFFD8ED)
val md_theme_light_onTertiaryContainer = Color(0xFF2B1225)
val md_theme_light_error = Color(0xFFBA1A1A)
val md_theme_light_onError = Color(0xFFFFFFFF)
val md_theme_light_errorContainer = Color(0xFFFFDAD6)
val md_theme_light_onErrorContainer = Color(0xFF410002)
val md_theme_light_background = Color(0xFFFAF8FF)
val md_theme_light_onBackground = Color(0xFF191B20)
val md_theme_light_surface = Color(0xFFFAF8FF)
val md_theme_light_onSurface = Color(0xFF191B20)
val md_theme_light_surfaceVariant = Color(0xFFE1E2EC)
val md_theme_light_onSurfaceVariant = Color(0xFF44464F)
val md_theme_light_outline = Color(0xFF757780)
val md_theme_light_outlineVariant = Color(0xFFC5C6D0)
val md_theme_light_outline_dark = Color(0xFF8F9099)
val md_theme_dark_primary = Color(0xFFB0C6FF)
val md_theme_dark_onPrimary = Color(0xFF0B2F6A)
val md_theme_dark_primaryContainer = Color(0xFF294777)
val md_theme_dark_onPrimaryContainer = Color(0xFFD9E2FF)
val md_theme_dark_secondary = Color(0xFFC0C6DC)
val md_theme_dark_onSecondary = Color(0xFF2A3042)
val md_theme_dark_secondaryContainer = Color(0xFF42485A)
val md_theme_dark_onSecondaryContainer = Color(0xFFDCE2F9)
val md_theme_dark_tertiary = Color(0xFFE6B9D5)
val md_theme_dark_onTertiary = Color(0xFF44263D)
val md_theme_dark_tertiaryContainer = Color(0xFF5D3B55)
val md_theme_dark_onTertiaryContainer = Color(0xFFFFD8ED)
val md_theme_dark_error = Color(0xFFFFB4AB)
val md_theme_dark_onError = Color(0xFF690005)
val md_theme_dark_errorContainer = Color(0xFF93000A)
val md_theme_dark_onErrorContainer = Color(0xFFFFDAD6)
val md_theme_dark_background = Color(0xFF111318)
val md_theme_dark_onBackground = Color(0xFFE2E2E9)
val md_theme_dark_surface = Color(0xFF111318)
val md_theme_dark_onSurface = Color(0xFFE2E2E9)
val md_theme_dark_surfaceVariant = Color(0xFF44464F)
val md_theme_dark_onSurfaceVariant = Color(0xFFC5C6D0)
val md_theme_dark_outlineVariant = Color(0xFF44464F)

// Surface-container tonal steps and inverse roles, completing the custom scheme above. Without
// these, Material3's lightColorScheme()/darkColorScheme() factories silently fall back to the
// library's baseline (unrelated) palette for any role not passed explicitly - visible as an
// off-hue NavigationBar/TopAppBar/UndoBar whenever dynamic color is off. Tones are hand-picked to
// sit in the same warm-neutral family as the existing surface/surfaceVariant/background colors
// above rather than computed from the true HCT tonal-palette algorithm.
val md_theme_light_surfaceDim = Color(0xFFDAD9E1)
val md_theme_light_surfaceBright = Color(0xFFFAF8FF)
val md_theme_light_surfaceContainerLowest = Color(0xFFFFFFFF)
val md_theme_light_surfaceContainerLow = Color(0xFFF4F2FA)
val md_theme_light_surfaceContainer = Color(0xFFEEEDF5)
val md_theme_light_surfaceContainerHigh = Color(0xFFE8E7EF)
val md_theme_light_surfaceContainerHighest = Color(0xFFE2E1E9)
val md_theme_light_inverseSurface = Color(0xFF2E3038)
val md_theme_light_inverseOnSurface = Color(0xFFF1F0F8)
val md_theme_light_inversePrimary = md_theme_dark_primary

val md_theme_dark_surfaceDim = Color(0xFF111318)
val md_theme_dark_surfaceBright = Color(0xFF3A3D46)
val md_theme_dark_surfaceContainerLowest = Color(0xFF0C0E12)
val md_theme_dark_surfaceContainerLow = Color(0xFF191B21)
val md_theme_dark_surfaceContainer = Color(0xFF1D1F26)
val md_theme_dark_surfaceContainerHigh = Color(0xFF272A32)
val md_theme_dark_surfaceContainerHighest = Color(0xFF32353E)
val md_theme_dark_inverseSurface = Color(0xFFE2E2E9)
val md_theme_dark_inverseOnSurface = Color(0xFF2E3038)
val md_theme_dark_inversePrimary = md_theme_light_primary

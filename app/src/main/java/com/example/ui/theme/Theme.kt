package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class PrismAccentColors(
    val preset: AccentPreset = AccentPreset.PRISM_INDIGO,
    val glassAlphaMultiplier: Float = 1f,
) {
    val primary: Color get() = preset.primary
    val secondary: Color get() = preset.secondary
    val orb3: Color get() = preset.orb3
    val orb4: Color get() = preset.orb4
}

val LocalPrismColors = staticCompositionLocalOf { PrismAccentColors() }

@Composable
fun AasmoLoaderTheme(
    accentPreset: AccentPreset = AccentPreset.PRISM_INDIGO,
    glassAlphaMultiplier: Float = 1f,
    content: @Composable () -> Unit,
) {
    val colorScheme = darkColorScheme(
        primary = accentPreset.primary,
        onPrimary = Color.White,
        secondary = accentPreset.secondary,
        onSecondary = Color.White,
        tertiary = accentPreset.orb3,
        onTertiary = Color.Black,
        background = ObsidianBg1,
        onBackground = TextPrimary,
        surface = SurfaceElevated,
        onSurface = TextPrimary,
        surfaceVariant = Color(0xFF171826),
        onSurfaceVariant = TextMuted,
        outline = GlassEdgeLo,
        outlineVariant = GlassEdge,
        error = StatusError,
        onError = Color.White,
    )

    CompositionLocalProvider(
        LocalPrismColors provides PrismAccentColors(
            preset = accentPreset,
            glassAlphaMultiplier = glassAlphaMultiplier,
        )
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AasmoTypography,
            content = content,
        )
    }
}

package org.namchieh.rusmorph.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

@Composable
fun RusMorphTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    // The warm academic palette remains stable across devices and long reading sessions.
    val colors = lightColorScheme(
        primary = RusMorphColors.Primary,
        onPrimary = RusMorphColors.TextOnDark,
        primaryContainer = RusMorphColors.PrimaryContainer,
        onPrimaryContainer = RusMorphColors.TextPrimary,
        secondary = RusMorphColors.Secondary,
        onSecondary = RusMorphColors.TextOnDark,
        secondaryContainer = RusMorphColors.SecondaryContainer,
        tertiary = RusMorphColors.Tertiary,
        background = RusMorphColors.Canvas,
        onBackground = RusMorphColors.TextPrimary,
        surface = RusMorphColors.Surface,
        onSurface = RusMorphColors.TextPrimary,
        surfaceVariant = RusMorphColors.SurfaceMuted,
        onSurfaceVariant = RusMorphColors.TextSecondary,
        outline = RusMorphColors.Outline,
        outlineVariant = RusMorphColors.OutlineSoft,
        error = RusMorphColors.Error,
    )
    CompositionLocalProvider(LocalRusMorphSpacing provides RusMorphSpacing()) {
        MaterialTheme(colorScheme = colors, typography = RusMorphTypography, shapes = RusMorphShapes, content = content)
    }
}

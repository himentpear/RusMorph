package org.namchieh.rusmorph.ui.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private val WerusColorScheme = lightColorScheme(
    primary = WerusColors.Red,
    onPrimary = WerusColors.OnDark,
    primaryContainer = WerusColors.RedSoft,
    onPrimaryContainer = WerusColors.RedDark,
    secondary = WerusColors.Gold,
    onSecondary = WerusColors.Ink,
    secondaryContainer = WerusColors.Beige,
    onSecondaryContainer = WerusColors.Ink,
    tertiary = WerusColors.InkMuted,
    onTertiary = WerusColors.OnDark,
    background = WerusColors.Canvas,
    onBackground = WerusColors.Ink,
    surface = WerusColors.Paper,
    onSurface = WerusColors.Ink,
    surfaceVariant = WerusColors.BeigeMuted,
    onSurfaceVariant = WerusColors.InkMuted,
    outline = WerusColors.Border,
    outlineVariant = WerusColors.BorderSoft,
    error = WerusColors.Error,
)

@Composable
fun WerusTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalWerusSpacing provides WerusSpacing()) {
        MaterialTheme(
            colorScheme = WerusColorScheme,
            typography = WerusTypography,
            shapes = WerusShapes,
            content = content,
        )
    }
}

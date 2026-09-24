package org.namchieh.rusmorph.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import org.namchieh.rusmorph.ui.design.WerusTheme

@Composable
fun RusMorphTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    WerusTheme(content)
}

package org.namchieh.rusmorph.ui.navigation

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.namchieh.rusmorph.ui.design.WerusColors

enum class BottomDestination(val label: String, val symbol: String, val route: String) {
    Home("首页", "⌂", Routes.Home),
    Tools("工具", "⋊", Routes.Tools),
    Dictionary("词典", "А", Routes.Dictionary),
    Review("复习", "↻", Routes.Review),
    Profile("我的", "○", Routes.Profile);

    companion object {
        @Deprecated("Use Tools instead", ReplaceWith("Tools"))
        val Courses: BottomDestination get() = Tools
    }
}

@Composable
fun RusMorphBottomBar(selected: BottomDestination, onSelect: (BottomDestination) -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Column(modifier.fillMaxWidth()) {
        androidx.compose.material3.HorizontalDivider(color = WerusColors.Border, thickness = 1.dp)
        NavigationBar(containerColor = WerusColors.Paper, tonalElevation = 0.dp) {
            BottomDestination.entries.forEach { destination ->
                NavigationBarItem(
                    selected = destination == selected,
                    onClick = { onSelect(destination) },
                    icon = { Text(destination.symbol) },
                    label = { Text(destination.label, style = org.namchieh.rusmorph.ui.theme.RusMorphTechTypography.MicroPill) },
                    modifier = Modifier.semantics { contentDescription = destination.label },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = WerusColors.Ink,
                        selectedTextColor = WerusColors.Ink,
                        indicatorColor = WerusColors.Beige,
                        unselectedIconColor = WerusColors.InkFaint,
                        unselectedTextColor = WerusColors.InkFaint,
                    ),
                )
            }
        }
    }
}

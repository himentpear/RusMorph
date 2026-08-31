package org.namchieh.rusmorph.ui.navigation

import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.namchieh.rusmorph.ui.theme.RusMorphColors

enum class BottomDestination(val label: String, val symbol: String, val route: String) {
    Home("首页", "⌂", Routes.Home),
    Courses("课程", "▤", Routes.Courses),
    Dictionary("词典", "А", Routes.Dictionary),
    Review("复习", "↻", Routes.Review),
    Profile("我的", "○", Routes.Profile),
}

@Composable
fun RusMorphBottomBar(selected: BottomDestination, onSelect: (BottomDestination) -> Unit, modifier: Modifier = Modifier) {
    NavigationBar(modifier = modifier, containerColor = RusMorphColors.Surface, tonalElevation = 0.dp) {
        BottomDestination.entries.forEach { destination ->
            NavigationBarItem(
                selected = destination == selected,
                onClick = { onSelect(destination) },
                icon = { Text(destination.symbol) },
                label = { Text(destination.label) },
                modifier = Modifier.semantics { contentDescription = destination.label },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = RusMorphColors.Primary,
                    selectedTextColor = RusMorphColors.Primary,
                    indicatorColor = RusMorphColors.PrimaryContainer,
                    unselectedIconColor = RusMorphColors.TextTertiary,
                    unselectedTextColor = RusMorphColors.TextTertiary,
                ),
            )
        }
    }
}

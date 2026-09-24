package org.namchieh.rusmorph.ui.screen.placeholder

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.namchieh.rusmorph.ui.common.TerminalBackground
import org.namchieh.rusmorph.ui.common.TerminalMessageState
import org.namchieh.rusmorph.ui.navigation.BottomDestination
import org.namchieh.rusmorph.ui.navigation.RusMorphBottomBar

@Composable
fun PlaceholderScreen(destination: BottomDestination, onSelect: (BottomDestination) -> Unit, onMore: () -> Unit = {}) {
    TerminalBackground(Modifier.fillMaxSize()) {
        Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent, bottomBar = { RusMorphBottomBar(destination, onSelect) }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
                TerminalMessageState(destination.label, "即将开放", Modifier.widthIn(max = 520.dp))
            }
        }
    }
}

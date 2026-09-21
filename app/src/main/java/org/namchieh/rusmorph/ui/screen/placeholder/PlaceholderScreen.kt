package org.namchieh.rusmorph.ui.screen.placeholder

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.namchieh.rusmorph.ui.home.BrandHeader
import org.namchieh.rusmorph.ui.laboratory.LaboratoryBackground
import org.namchieh.rusmorph.ui.laboratory.LaboratoryMessageState
import org.namchieh.rusmorph.ui.navigation.BottomDestination
import org.namchieh.rusmorph.ui.navigation.RusMorphBottomBar

@Composable
fun PlaceholderScreen(destination: BottomDestination, onSelect: (BottomDestination) -> Unit, onMore: () -> Unit = {}) {
    LaboratoryBackground(Modifier.fillMaxSize()) {
        Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent, topBar = { BrandHeader(onMore) }, bottomBar = { RusMorphBottomBar(destination, onSelect) }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
                LaboratoryMessageState(destination.label, "即将开放", Modifier.widthIn(max = 520.dp))
            }
        }
    }
}

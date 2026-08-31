package org.namchieh.rusmorph

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import org.namchieh.rusmorph.ui.navigation.RusMorphApp
import org.namchieh.rusmorph.ui.theme.RusMorphTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RusMorphTheme {
                RusMorphApp(application as RusMorphApplication)
            }
        }
    }
}

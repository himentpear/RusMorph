package org.namchieh.rusmorph.ui.screen.learning

import androidx.compose.runtime.staticCompositionLocalOf

/** Shortcuts shared by nested learning, grammar, and review pages. */
data class LearningQuickNavigation(
    val back: () -> Unit,
    val learning: () -> Unit,
    val home: () -> Unit,
)

val LocalLearningQuickNavigation = staticCompositionLocalOf<LearningQuickNavigation?> { null }

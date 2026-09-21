package org.namchieh.rusmorph.ui.common

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.namchieh.rusmorph.ui.laboratory.LaboratoryBackground
import org.namchieh.rusmorph.ui.laboratory.LaboratoryLabel
import org.namchieh.rusmorph.ui.laboratory.LaboratoryLoadingState
import org.namchieh.rusmorph.ui.laboratory.LaboratoryMessageState
import org.namchieh.rusmorph.ui.laboratory.LaboratoryPanel
import org.namchieh.rusmorph.ui.laboratory.LaboratoryPrimaryButton
import org.namchieh.rusmorph.ui.theme.RusMorphColors

/**
 * Legacy terminal components.
 * Deprecated: Use [org.namchieh.rusmorph.ui.laboratory.LaboratoryComponents] instead.
 */

@Deprecated(
    message = "Use LaboratoryBackground from org.namchieh.rusmorph.ui.laboratory instead",
    replaceWith = ReplaceWith("LaboratoryBackground(modifier, content)", "org.namchieh.rusmorph.ui.laboratory.LaboratoryBackground")
)
@Composable
fun TerminalBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    LaboratoryBackground(modifier = modifier, content = content)
}

@Deprecated(
    message = "Use LaboratoryLabel from org.namchieh.rusmorph.ui.laboratory instead",
    replaceWith = ReplaceWith("LaboratoryLabel(text, modifier, color)", "org.namchieh.rusmorph.ui.laboratory.LaboratoryLabel")
)
@Composable
fun TerminalLabel(text: String, modifier: Modifier = Modifier, color: Color = RusMorphColors.TextTertiary) {
    LaboratoryLabel(text = text, modifier = modifier, color = color)
}

@Deprecated(
    message = "Use LaboratoryPanel from org.namchieh.rusmorph.ui.laboratory instead",
    replaceWith = ReplaceWith("LaboratoryPanel(modifier, content)", "org.namchieh.rusmorph.ui.laboratory.LaboratoryPanel")
)
@Composable
fun TerminalPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    LaboratoryPanel(modifier = modifier, content = content)
}

@Deprecated(
    message = "Use LaboratoryPrimaryButton from org.namchieh.rusmorph.ui.laboratory instead",
    replaceWith = ReplaceWith("LaboratoryPrimaryButton(text, onClick, modifier, enabled)", "org.namchieh.rusmorph.ui.laboratory.LaboratoryPrimaryButton")
)
@Composable
fun RusMorphPrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    LaboratoryPrimaryButton(text = text, onClick = onClick, modifier = modifier, enabled = enabled)
}

@Deprecated(
    message = "Use LaboratoryLoadingState from org.namchieh.rusmorph.ui.laboratory instead",
    replaceWith = ReplaceWith("LaboratoryLoadingState(label, modifier)", "org.namchieh.rusmorph.ui.laboratory.LaboratoryLoadingState")
)
@Composable
fun TerminalLoadingState(label: String, modifier: Modifier = Modifier) {
    LaboratoryLoadingState(label = label, modifier = modifier)
}

@Deprecated(
    message = "Use LaboratoryMessageState from org.namchieh.rusmorph.ui.laboratory instead",
    replaceWith = ReplaceWith("LaboratoryMessageState(title, detail, modifier, action, onAction)", "org.namchieh.rusmorph.ui.laboratory.LaboratoryMessageState")
)
@Composable
fun TerminalMessageState(
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    LaboratoryMessageState(
        title = title,
        detail = detail,
        modifier = modifier,
        action = action,
        onAction = onAction,
    )
}

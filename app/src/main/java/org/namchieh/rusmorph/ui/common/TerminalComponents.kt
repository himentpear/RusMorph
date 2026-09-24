package org.namchieh.rusmorph.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.namchieh.rusmorph.ui.design.WerusColors
import org.namchieh.rusmorph.ui.theme.*

@Composable
fun TerminalBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val gradient = remember { Brush.verticalGradient(listOf(WerusColors.Paper, WerusColors.Canvas, WerusColors.BeigeMuted)) }
    Box(modifier.background(gradient)) {
        Canvas(Modifier.matchParentSize()) {
            val step = 32.dp.toPx()
            val line = WerusColors.Red.copy(alpha = 0.03f)
            var x = 0f
            while (x < size.width) { drawLine(line, Offset(x, 0f), Offset(x, size.height), 1f); x += step }
            var y = 0f
            while (y < size.height) { drawLine(line, Offset(0f, y), Offset(size.width, y), 1f); y += step }
        }
        content()
    }
}

@Composable
fun TerminalLabel(text: String, modifier: Modifier = Modifier, color: Color = WerusColors.InkFaint) {
    Text(text.uppercase(), modifier, style = RusMorphTechTypography.MicroPill, color = color)
}

@Composable
fun TerminalPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .background(WerusColors.Paper, TechCardShape)
            .border(1.dp, WerusColors.Border, TechCardShape)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
fun RusMorphPrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = WerusColors.Ink, contentColor = WerusColors.OnDark),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun TerminalLoadingState(label: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(3) { index -> Box(Modifier.size(width = 22.dp, height = 4.dp).background(if (index == 0) WerusColors.Red else WerusColors.BorderSoft, CircleShape)) }
        }
        TerminalLabel(label, color = WerusColors.InkMuted)
    }
}

@Composable
fun TerminalMessageState(
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    TerminalPanel(modifier) {
        TerminalLabel("STATUS / RM-01")
        Text(title, style = MaterialTheme.typography.titleLarge, color = WerusColors.Red, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = WerusColors.InkMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        if (action != null && onAction != null) RusMorphPrimaryButton(action, onAction, Modifier.align(Alignment.CenterHorizontally))
    }
}

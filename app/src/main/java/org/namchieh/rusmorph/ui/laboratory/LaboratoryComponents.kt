package org.namchieh.rusmorph.ui.laboratory

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.namchieh.rusmorph.ui.design.WerusColors
import org.namchieh.rusmorph.ui.theme.RusMorphTechTypography
import org.namchieh.rusmorph.ui.theme.TechCardShape

/**
 * Werus 实验室与工程终端专属组件集 (Laboratory Components)。
 *
 * 规范约束：
 * - 仅限用于 AI 智能助教、语音评测与声学诊断、工程指令生成 (CommandScreen)、系统初始化 (InitializationScreen) 以及开发者工具。
 * - 严禁在主教材阅读、课程浏览与核心学习流程中引用本组件集，以保持俄式纸质典籍与学术批注的纯粹性。
 */

@Composable
fun LaboratoryBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val gradient = remember {
        Brush.verticalGradient(
            listOf(
                WerusColors.Paper,
                WerusColors.Canvas,
                WerusColors.BeigeMuted,
            )
        )
    }
    Box(modifier.background(gradient)) {
        Canvas(Modifier.matchParentSize()) {
            val step = 32.dp.toPx()
            val line = WerusColors.Red.copy(alpha = 0.03f)
            var x = 0f
            while (x < size.width) {
                drawLine(line, Offset(x, 0f), Offset(x, size.height), 1f)
                x += step
            }
            var y = 0f
            while (y < size.height) {
                drawLine(line, Offset(0f, y), Offset(size.width, y), 1f)
                y += step
            }
        }
        content()
    }
}

@Composable
fun LaboratoryLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = WerusColors.InkFaint,
) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        style = RusMorphTechTypography.MicroPill,
        color = color,
    )
}

@Composable
fun LaboratoryPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
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
fun LaboratoryPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = WerusColors.Ink,
            contentColor = WerusColors.OnDark,
        ),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun LaboratoryLoadingState(
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(3) { index ->
                Box(
                    Modifier
                        .size(width = 22.dp, height = 4.dp)
                        .background(
                            if (index == 0) WerusColors.Red else WerusColors.BorderSoft,
                            CircleShape,
                        )
                )
            }
        }
        LaboratoryLabel(label, color = WerusColors.InkMuted)
    }
}

@Composable
fun LaboratoryMessageState(
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    LaboratoryPanel(modifier) {
        LaboratoryLabel("STATUS / RM-01")
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = WerusColors.Red,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            detail,
            style = MaterialTheme.typography.bodyMedium,
            color = WerusColors.InkMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        if (action != null && onAction != null) {
            LaboratoryPrimaryButton(
                text = action,
                onClick = onAction,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

package org.namchieh.rusmorph.ui.screen.learning.text

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.namchieh.rusmorph.domain.learning.TextSentence
import org.namchieh.rusmorph.ui.design.WerusColors
import org.namchieh.rusmorph.ui.design.WerusTypography

@Composable
fun TextSentenceRow(
    sentence: TextSentence,
    expanded: Boolean,
    showTranslation: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().heightIn(min = 48.dp)
            .background(if (expanded) WerusColors.BeigeMuted else WerusColors.Canvas)
            .semantics {
                role = Role.Button
                selected = expanded
                stateDescription = if (expanded) "已展开" else "已收起"
                contentDescription = "俄语句子：${sentence.text}"
            }
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(sentence.text, style = WerusTypography.SerifBody, fontWeight = if (expanded) FontWeight.Medium else FontWeight.Normal, color = WerusColors.Ink)
        if (showTranslation) {
            Text(
                sentence.translation ?: "暂无译文",
                modifier = Modifier.padding(top = 5.dp),
                style = WerusTypography.Body,
                color = if (sentence.translation == null) WerusColors.InkFaint else WerusColors.InkMuted,
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(180)) + fadeIn(tween(180)),
            exit = shrinkVertically(tween(180)) + fadeOut(tween(150)),
        ) { SentenceAnalysisPanel(sentence) }
    }
}

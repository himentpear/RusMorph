package org.namchieh.rusmorph.ui.screen.learning.text

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.namchieh.rusmorph.domain.learning.TextContent
import org.namchieh.rusmorph.domain.learning.TextParagraph
import org.namchieh.rusmorph.ui.components.RusEmptyState
import org.namchieh.rusmorph.ui.components.RusSectionTitle
import org.namchieh.rusmorph.ui.learning.Loadable
import org.namchieh.rusmorph.ui.screen.learning.LearningScaffold
import org.namchieh.rusmorph.ui.theme.RusMorphColors
import org.namchieh.rusmorph.ui.theme.RusMorphTechTypography

@Composable
fun TextDetailScreen(state: Loadable<TextContent>, onBack: () -> Unit, onAI: () -> Unit) {
    var showTranslation by rememberSaveable { mutableStateOf(false) }
    var expandedSentenceId by rememberSaveable { mutableStateOf<String?>(null) }
    val title = (state as? Loadable.Content)?.value?.lessonId?.substringAfterLast('-')
        ?.let { "УРОК ${it.padStart(2, '0')} · ТЕКСТ" }
        ?: "ТЕКСТ · 课文"

    LearningScaffold(
        title = title,
        onBack = onBack,
        onAI = onAI,
        actions = {
            TextButton(
                onClick = { showTranslation = !showTranslation },
                modifier = Modifier.semantics {
                    contentDescription = if (showTranslation) "隐藏逐句翻译" else "显示逐句翻译"
                },
            ) {
                Text(if (showTranslation) "隐藏翻译" else "显示翻译", color = RusMorphColors.CarbonBlack)
            }
        },
    ) { root ->
        Column(
            root.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp).widthIn(max = 760.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            when (state) {
                Loadable.Loading -> CircularProgressIndicator(color = RusMorphColors.CarbonBlack)
                is Loadable.Error -> RusEmptyState("课文加载失败", state.message)
                is Loadable.Content -> {
                    RusSectionTitle(state.value.title, state.value.translationTitle ?: "逐句阅读 · 点击原文查看批注")
                    state.value.paragraphs.sortedBy { it.order }.forEachIndexed { index, paragraph ->
                        TextSection(index + 1, paragraph, showTranslation, expandedSentenceId) { sentenceId ->
                            expandedSentenceId = if (expandedSentenceId == sentenceId) null else sentenceId
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TextSection(
    index: Int,
    paragraph: TextParagraph,
    showTranslation: Boolean,
    expandedSentenceId: String?,
    onSentence: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            "${index.toString().padStart(2, '0')} · АБЗАЦ",
            style = RusMorphTechTypography.MicroPill,
            color = RusMorphColors.TextTertiary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
        paragraph.sentences.sortedBy { it.order }.forEach { sentence ->
            TextSentenceRow(
                sentence = sentence,
                expanded = expandedSentenceId == sentence.id,
                showTranslation = showTranslation,
                onToggle = { onSentence(sentence.id) },
            )
        }
    }
}

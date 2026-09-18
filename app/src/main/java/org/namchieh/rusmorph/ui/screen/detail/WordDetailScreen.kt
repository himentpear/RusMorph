@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package org.namchieh.rusmorph.ui.screen.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.namchieh.rusmorph.R
import org.namchieh.rusmorph.agent.AgentQuestionType
import org.namchieh.rusmorph.ui.LoadableState
import org.namchieh.rusmorph.ui.WordDetailUiState
import org.namchieh.rusmorph.ui.WordDetailViewModel
import org.namchieh.rusmorph.ui.components.InflectionTableCard
import org.namchieh.rusmorph.ui.components.knowledgeCategoryLabel
import org.namchieh.rusmorph.ui.components.readableSummary
import org.namchieh.rusmorph.ui.components.visibleValue
import org.namchieh.rusmorph.ui.theme.RusMorphColors
import org.namchieh.rusmorph.ui.theme.WordCardShape

@Composable
fun WordDetailScreen(
    viewModel: WordDetailViewModel,
    onBack: () -> Unit,
    onExplanationClick: (String) -> Unit,
    onAgentClick: (String, AgentQuestionType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        modifier = modifier,
        containerColor = RusMorphColors.Canvas,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.word_detail)) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(containerColor = RusMorphColors.SurfaceElevated, titleContentColor = RusMorphColors.TextPrimary),
            )
        },
    ) { padding ->
        when (val current = state) {
            LoadableState.Loading -> CenteredProgress(Modifier.padding(padding))
            LoadableState.NotFound -> CenteredMessage(
                stringResource(R.string.word_not_found), Modifier.padding(padding))
            LoadableState.Error -> CenteredMessage(
                stringResource(R.string.detail_load_failed), Modifier.padding(padding))
            is LoadableState.Content -> DetailContent(
                current.value,
                onExplanationClick,
                onAgentClick,
                Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun DetailContent(
    detail: WordDetailUiState,
    onExplanationClick: (String) -> Unit,
    onAgentClick: (String, AgentQuestionType) -> Unit,
    modifier: Modifier,
) {
    val parts = detail.partsOfSpeech.filterVisible()
    val additionalAnnotations = detail.annotations
        .filterNot { it.fieldName in STANDARD_ANNOTATION_FIELDS || it.fieldName == "inflection_data" }
        .distinctBy { it.fieldName to it.value }
        .map { it.fieldName to it.value }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    detail.displayForm,
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                detail.chineseMeaning.visibleValue()?.let {
                    Text(it, style = MaterialTheme.typography.titleLarge)
                }

                // 单词基础信息小 Tip 胶囊 (FlowRow)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    detail.lesson?.let {
                        MorphologyTip("大学俄语1 · 第 $it 课", isPrimary = true)
                    }
                    parts.forEach { MorphologyTip(it) }
                    detail.gender.visibleValue()?.let { MorphologyTip(it) }
                    detail.aspect.visibleValue()?.let { MorphologyTip(it) }
                    detail.declensionClass.visibleValue()?.let {
                        val label = if (it.endsWith("变格") || it.endsWith("变格法")) it else "${it} 变格"
                        MorphologyTip(label)
                    }
                    detail.conjugationClass.visibleValue()?.let {
                        val label = if (it.endsWith("变位") || it.endsWith("变位法")) it else "${it} 变位"
                        MorphologyTip(label)
                    }
                    detail.endingType.visibleValue()?.let { MorphologyTip(it) }
                    detail.pluralStressPattern.visibleValue()?.let { MorphologyTip(it) }
                    detail.phoneticAlternation.visibleValue()?.let { MorphologyTip("音变: $it") }
                    additionalAnnotations.forEach { (field, value) ->
                        MorphologyTip("$field: $value")
                    }
                }
            }
        }

        // 变格变位表
        item {
            InflectionTableCard(
                inflection = detail.inflection,
                onAskAi = { onAgentClick(detail.id, AgentQuestionType.MORPHOLOGY) },
            )
        }

        item { AgentQuestionActions(detail.id, onAgentClick) }

        if (detail.relatedKnowledge.isNotEmpty()) {
            item { SectionHeading(stringResource(R.string.local_knowledge)) }
            items(detail.relatedKnowledge, key = { it.id }) { knowledge ->
                KnowledgeCard(knowledge, onExplanationClick)
            }
        }
    }
}

@Composable
private fun MorphologyTip(text: String, isPrimary: Boolean = false) {
    androidx.compose.material3.Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        color = if (isPrimary) RusMorphColors.PrimaryContainer else RusMorphColors.SurfaceElevated,
        contentColor = if (isPrimary) RusMorphColors.PrimaryDark else RusMorphColors.TextPrimary,
        border = if (isPrimary) null else androidx.compose.foundation.BorderStroke(1.dp, RusMorphColors.OutlineSoft),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isPrimary) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
        )
    }
}

private val STANDARD_ANNOTATION_FIELDS = setOf(
    "词类", "词类1", "词类2", "词类3", "词类4",
    "性", "变格法", "结尾字母", "复数重音转移",
    "动词的体", "变位法", "语音交替",
)

@Composable
internal fun AgentQuestionActions(entryId: String, onAgentClick: (String, AgentQuestionType) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeading(stringResource(R.string.ask_ai))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AgentQuestionType.entries.forEach { type ->
                AssistChip(
                    onClick = { onAgentClick(entryId, type) },
                    label = { Text(stringResource(when (type) {
                        AgentQuestionType.ETYMOLOGY -> R.string.agent_etymology
                        AgentQuestionType.DERIVATION -> R.string.agent_derivation
                        AgentQuestionType.MORPHOLOGY -> R.string.agent_morphology
                        AgentQuestionType.CUSTOM -> R.string.agent_custom
                    })) },
                )
            }
        }
    }
}

@Composable
private fun MorphologySection(title: String, rows: List<Pair<String, String>>) {
    Card(modifier = Modifier.fillMaxWidth(), shape = WordCardShape, colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = RusMorphColors.SurfaceElevated), border = androidx.compose.foundation.BorderStroke(1.dp, RusMorphColors.OutlineSoft)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionHeading(title)
            rows.forEachIndexed { index, (label, value) ->
                if (index > 0) HorizontalDivider()
                Column {
                    Text(label, style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(value, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
private fun KnowledgeCard(
    knowledge: WordDetailUiState.KnowledgeExplanation,
    onExplanationClick: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = WordCardShape, colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = RusMorphColors.SurfaceElevated), border = androidx.compose.foundation.BorderStroke(1.dp, RusMorphColors.OutlineSoft)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(knowledge.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(stringResource(knowledgeCategoryLabel(knowledge.category)),
                color = MaterialTheme.colorScheme.primary)
            Text(readableSummary(knowledge.content))
            Text(stringResource(R.string.source_document, knowledge.sourceDocument),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { onExplanationClick(knowledge.id) }) {
                Text(stringResource(R.string.view_full_explanation))
            }
        }
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun CenteredProgress(modifier: Modifier) {
    Column(modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) { CircularProgressIndicator() }
}

@Composable
private fun CenteredMessage(text: String, modifier: Modifier) {
    Column(modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) { Text(text) }
}

private fun List<String>.filterVisible(): List<String> =
    mapNotNull { it.visibleValue() }.distinct()

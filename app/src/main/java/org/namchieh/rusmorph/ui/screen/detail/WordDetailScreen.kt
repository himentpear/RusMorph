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
    val basic = listOfNotNull(
        detail.lesson?.let { stringResource(R.string.lesson_label) to stringResource(R.string.lesson_format, it) },
        detail.sequence?.let { stringResource(R.string.sequence_label) to it.toString() },
        parts.takeIf { it.isNotEmpty() }?.let {
            stringResource(R.string.part_of_speech_label) to it.joinToString("、")
        },
    )
    val noun = listOfNotNull(
        detail.gender.visibleValue()?.let { stringResource(R.string.gender_label) to it },
        detail.declensionClass.visibleValue()?.let { stringResource(R.string.declension_class_label) to it },
        detail.endingType.visibleValue()?.let { stringResource(R.string.ending_type_label) to it },
        detail.pluralStressPattern.visibleValue()?.let { stringResource(R.string.plural_stress_label) to it },
    )
    val verb = listOfNotNull(
        detail.aspect.visibleValue()?.let { stringResource(R.string.aspect_label) to it },
        detail.conjugationClass.visibleValue()?.let { stringResource(R.string.conjugation_class_label) to it },
        detail.phoneticAlternation.visibleValue()?.let { stringResource(R.string.phonetic_alternation_label) to it },
    )
    val additionalAnnotations = detail.annotations
        .filterNot { it.fieldName in STANDARD_ANNOTATION_FIELDS }
        .distinctBy { it.fieldName to it.value }
        .map { it.fieldName to it.value }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(detail.displayForm, style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.SemiBold)
                detail.chineseMeaning.visibleValue()?.let {
                    Text(it, style = MaterialTheme.typography.titleLarge)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    parts.forEach { AssistChip(onClick = {}, label = { Text(it) }) }
                }
            }
        }

        if (basic.isNotEmpty()) item { MorphologySection(stringResource(R.string.basic_information), basic) }
        if (noun.isNotEmpty()) item { MorphologySection(stringResource(R.string.noun_information), noun) }
        if (verb.isNotEmpty()) item { MorphologySection(stringResource(R.string.verb_information), verb) }
        if (additionalAnnotations.isNotEmpty()) {
            item { MorphologySection(stringResource(R.string.additional_annotations), additionalAnnotations) }
        }

        item { AgentQuestionActions(detail.id, onAgentClick) }

        item { SectionHeading(stringResource(R.string.local_knowledge)) }
        if (detail.relatedKnowledge.isEmpty()) {
            item { Text(stringResource(R.string.no_local_knowledge), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(detail.relatedKnowledge, key = { it.id }) { knowledge ->
                KnowledgeCard(knowledge, onExplanationClick)
            }
        }

        if (detail.sources.isNotEmpty()) {
            item { SectionHeading(stringResource(R.string.data_sources)) }
            items(detail.sources, key = { "${it.workbook}:${it.sheet}:${it.row}" }) { source ->
                Text(
                    stringResource(R.string.workbook_source_format, source.workbook, source.sheet, source.row),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
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

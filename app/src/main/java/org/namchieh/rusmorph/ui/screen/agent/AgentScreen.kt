@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.namchieh.rusmorph.ui.screen.agent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.namchieh.rusmorph.R
import org.namchieh.rusmorph.agent.AgentAvailability
import org.namchieh.rusmorph.agent.AgentError
import org.namchieh.rusmorph.agent.AgentQuestionType
import org.namchieh.rusmorph.ui.AgentPreparedData
import org.namchieh.rusmorph.ui.AgentUiState
import org.namchieh.rusmorph.ui.AgentViewModel
import org.namchieh.rusmorph.ui.theme.RusMorphColors

@Composable
fun AgentScreen(viewModel: AgentViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(containerColor = RusMorphColors.Canvas, topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.agent_title)) },
            navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.back)) } },
            colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(containerColor = RusMorphColors.SurfaceElevated),
        )
    }) { padding ->
        when (val current = state) {
            AgentUiState.Preparing -> CenterMessage(stringResource(R.string.agent_preparing), padding)
            AgentUiState.NotFound -> CenterMessage(stringResource(R.string.word_not_found), padding)
            is AgentUiState.Ready -> AgentContent(current.data, false, null, null, viewModel::updateInput, viewModel::submit, viewModel::cancel, viewModel::retry, padding)
            is AgentUiState.Sending -> AgentContent(current.data, true, null, null, viewModel::updateInput, viewModel::submit, viewModel::cancel, viewModel::retry, padding)
            is AgentUiState.Success -> AgentContent(current.data, false, current.response, null, viewModel::updateInput, viewModel::submit, viewModel::cancel, viewModel::retry, padding)
            is AgentUiState.Error -> AgentContent(current.data, false, null, current.error, viewModel::updateInput, viewModel::submit, viewModel::cancel, viewModel::retry, padding)
        }
    }
}

@Composable
internal fun AgentContent(
    data: AgentPreparedData,
    sending: Boolean,
    response: org.namchieh.rusmorph.agent.AgentResponse?,
    error: AgentError?,
    onInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    padding: PaddingValues,
) {
    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text(data.detail.displayForm, style = MaterialTheme.typography.headlineMedium)
            data.detail.chineseMeaning?.takeIf(String::isNotBlank)?.let { Text(it) }
            Text(questionTypeLabel(data.questionType), color = MaterialTheme.colorScheme.primary)
        }
        if (data.defaultQuestion.isNotBlank()) item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = RusMorphColors.SurfaceElevated), border = androidx.compose.foundation.BorderStroke(1.dp, RusMorphColors.OutlineSoft)) { Text(questionTemplate(data), Modifier.padding(16.dp)) }
        }
        item {
            OutlinedTextField(
                value = data.userInput,
                onValueChange = onInputChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = !sending,
                label = { Text(stringResource(if (data.questionType == AgentQuestionType.CUSTOM) R.string.custom_question else R.string.additional_requirement)) },
                supportingText = { Text(stringResource(R.string.input_count, data.userInput.length, 1000)) },
                minLines = 3,
            )
        }
        item { ContextSummary(data) }
        if (data.retrieval.knowledgeChunks.isEmpty()) item {
            Text(stringResource(R.string.agent_no_knowledge_warning), color = MaterialTheme.colorScheme.tertiary)
        }
        if (data.availability != AgentAvailability.AVAILABLE) item {
            Text(stringResource(R.string.agent_not_configured), color = MaterialTheme.colorScheme.error)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onSubmit, enabled = data.canSubmit && !sending) {
                    if (sending) CircularProgressIndicator(Modifier.padding(end = 8.dp))
                    Text(stringResource(if (sending) R.string.agent_sending else R.string.agent_submit))
                }
                if (sending) TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
                if (error != null) TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
            }
        }
        error?.let { item { Text(errorMessage(it), color = MaterialTheme.colorScheme.error) } }
        response?.let { answer ->
            item { Text(stringResource(R.string.agent_answer), style = MaterialTheme.typography.titleLarge) }
            if (answer.answerSections.isEmpty()) item { Text(answer.answer) }
            else answer.answerSections.forEach { section -> item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(section.title, style = MaterialTheme.typography.titleMedium)
                    Text(section.content)
                }
            } }
            if (answer.evidence.isNotEmpty()) item {
                Text(stringResource(R.string.agent_evidence), style = MaterialTheme.typography.titleMedium)
                answer.evidence.forEach { evidence ->
                    Text("• ${evidence.title}${evidence.excerpt?.let { ": $it" }.orEmpty()}")
                }
            }
            if (answer.warnings.isNotEmpty()) item {
                Text(stringResource(R.string.agent_warnings), style = MaterialTheme.typography.titleMedium)
                answer.warnings.forEach { Text("• $it") }
            }
        }
        item { Text(stringResource(R.string.agent_privacy_notice), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun ContextSummary(data: AgentPreparedData) {
    val detail = data.detail
    val rows = listOfNotNull(
        detail.displayForm.takeIf(String::isNotBlank), detail.chineseMeaning?.takeIf(String::isNotBlank),
        detail.partsOfSpeech.takeIf(List<String>::isNotEmpty)?.joinToString("、"),
        detail.gender, detail.declensionClass, detail.endingType, detail.aspect,
        detail.conjugationClass, detail.phoneticAlternation, detail.pluralStressPattern,
    ).filter(String::isNotBlank)
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = RusMorphColors.SurfaceElevated), border = androidx.compose.foundation.BorderStroke(1.dp, RusMorphColors.OutlineSoft)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.agent_context_summary), style = MaterialTheme.typography.titleMedium)
            rows.forEach { Text(it) }
            Text(stringResource(R.string.agent_knowledge_count, data.retrieval.knowledgeChunks.size))
        }
    }
}

@Composable private fun questionTypeLabel(type: AgentQuestionType) = stringResource(when (type) {
    AgentQuestionType.ETYMOLOGY -> R.string.agent_etymology
    AgentQuestionType.DERIVATION -> R.string.agent_derivation
    AgentQuestionType.MORPHOLOGY -> R.string.agent_morphology
    AgentQuestionType.CUSTOM -> R.string.agent_custom
})

@Composable private fun questionTemplate(data: AgentPreparedData): String = when (data.questionType) {
    AgentQuestionType.ETYMOLOGY -> stringResource(R.string.agent_template_etymology, data.detail.displayForm)
    AgentQuestionType.DERIVATION -> stringResource(R.string.agent_template_derivation, data.detail.displayForm)
    AgentQuestionType.MORPHOLOGY -> stringResource(R.string.agent_template_morphology, data.detail.displayForm)
    AgentQuestionType.CUSTOM -> ""
}

@Composable private fun errorMessage(error: AgentError) = stringResource(when (error) {
    AgentError.NoNetwork -> R.string.agent_error_network
    AgentError.Timeout -> R.string.agent_error_timeout
    AgentError.RateLimited -> R.string.agent_error_rate_limited
    AgentError.ServerError -> R.string.agent_error_server
    AgentError.InvalidResponse -> R.string.agent_error_invalid
    AgentError.UnauthorizedProxy, AgentError.Unknown -> R.string.agent_error_unavailable
    AgentError.ServiceNotConfigured -> R.string.agent_error_not_configured
    AgentError.AuthenticationFailed -> R.string.agent_error_authentication
    AgentError.BalanceInsufficient -> R.string.agent_error_balance
    AgentError.ProviderBusy -> R.string.agent_error_busy
    AgentError.Cancelled -> R.string.agent_error_cancelled
})

@Composable private fun CenterMessage(text: String, padding: PaddingValues) {
    Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.Center) { Text(text) }
}

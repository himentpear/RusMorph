@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package org.namchieh.rusmorph.ui.screen.explanation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
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
import org.namchieh.rusmorph.ui.LoadableState
import org.namchieh.rusmorph.ui.LocalExplanationUiState
import org.namchieh.rusmorph.ui.LocalExplanationViewModel
import org.namchieh.rusmorph.ui.components.knowledgeCategoryLabel
import org.namchieh.rusmorph.ui.theme.RusMorphColors

@Composable
fun LocalExplanationScreen(
    viewModel: LocalExplanationViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        modifier = modifier,
        containerColor = RusMorphColors.Canvas,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.local_explanation)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.back)) } },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(containerColor = RusMorphColors.SurfaceElevated),
            )
        },
    ) { padding ->
        when (val current = state) {
            LoadableState.Loading -> CenteredProgress(Modifier.padding(padding))
            LoadableState.NotFound -> CenteredMessage(
                stringResource(R.string.explanation_not_found), Modifier.padding(padding))
            LoadableState.Error -> CenteredMessage(
                stringResource(R.string.explanation_load_failed), Modifier.padding(padding))
            is LoadableState.Content -> ExplanationContent(current.value, Modifier.padding(padding))
        }
    }
}

@Composable
private fun ExplanationContent(explanation: LocalExplanationUiState, modifier: Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(explanation.title, style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold)
        }
        item {
            Text(stringResource(knowledgeCategoryLabel(explanation.category)),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary)
        }
        explanation.content.split(Regex("\\n\\s*\\n")).filter(String::isNotBlank).forEachIndexed { index, paragraph ->
            item(key = "paragraph-$index") {
                Text(paragraph.trim(), style = MaterialTheme.typography.bodyLarge)
            }
        }
        if (explanation.examples.isNotEmpty()) {
            item { SectionHeading(stringResource(R.string.examples)) }
            items(explanation.examples, key = { "example-$it" }) { Text(it) }
        }
        if (explanation.keywords.isNotEmpty()) {
            item { SectionHeading(stringResource(R.string.keywords)) }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    explanation.keywords.forEach { AssistChip(onClick = {}, label = { Text(it) }) }
                }
            }
        }
        if (explanation.sectionPath.isNotEmpty()) {
            item { SectionHeading(stringResource(R.string.section_path)) }
            item { Text(explanation.sectionPath.joinToString(" › ")) }
        }
        item { Text(stringResource(R.string.source_document, explanation.sourceDocument),
            color = MaterialTheme.colorScheme.onSurfaceVariant) }
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

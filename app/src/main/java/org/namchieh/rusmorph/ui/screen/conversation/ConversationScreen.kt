package org.namchieh.rusmorph.ui.screen.conversation

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import org.namchieh.rusmorph.audio.RussianConversationSpeech
import org.namchieh.rusmorph.data.repository.ConversationCorrection
import org.namchieh.rusmorph.ui.conversation.ConversationViewModel
import org.namchieh.rusmorph.ui.design.WerusColors
import org.namchieh.rusmorph.ui.design.WerusTypography

private val scenarios = listOf("free" to "自由对话", "cafe" to "咖啡馆", "campus" to "校园", "shop" to "商店", "travel" to "旅行")

@Composable
fun ConversationScreen(viewModel: ConversationViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val speech = remember(viewModel) {
        RussianConversationSpeech(context, viewModel::setInput, viewModel::setListening, viewModel::showError)
    }
    DisposableEffect(speech) { onDispose { speech.close() } }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) speech.listen() else viewModel.showError("未获麦克风权限，仍可使用文字输入")
    }
    LaunchedEffect(state.speechNonce) {
        if (state.speechNonce > 0) state.messages.lastOrNull { it.role == "assistant" }?.text?.let(speech::speak)
    }
    val listState = rememberLazyListState()
    LaunchedEffect(state.messages.size, state.streamingText) {
        val count = state.messages.size + if (state.streamingText.isNotEmpty()) 1 else 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }
    Scaffold(containerColor = WerusColors.Canvas, topBar = {
        Row(Modifier.fillMaxWidth().background(WerusColors.Paper).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Text("←", color = WerusColors.Ink) }
            Column {
                Text("AI 俄语助教", style = WerusTypography.Title, color = WerusColors.Ink)
                Text("练习真实俄语对话", style = WerusTypography.Caption, color = WerusColors.InkMuted)
            }
        }
    }, bottomBar = {
        Column(Modifier.fillMaxWidth().background(WerusColors.Paper).padding(12.dp)) {
            state.errorMessage?.let { Text(it, color = WerusColors.Red, style = WerusTypography.Caption) }
            if (state.isListening) Text("正在听…", color = WerusColors.Red)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = state.input, onValueChange = viewModel::setInput, modifier = Modifier.weight(1f), placeholder = { Text("用俄语说点什么…") }, maxLines = 3)
                IconButton(onClick = {
                    viewModel.clearError()
                    if (state.isListening) speech.stopListening()
                    else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) speech.listen()
                    else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }) { Text(if (state.isListening) "■" else "🎙") }
                Button(onClick = { speech.stopSpeaking(); viewModel.send() }, enabled = state.input.isNotBlank() && !state.isSending) { Text("发送") }
            }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Text(scenarios.first { it.first == state.scenario }.second, style = WerusTypography.Subtitle, color = WerusColors.Ink, modifier = Modifier.padding(vertical = 8.dp))
            if (state.messages.none { it.role == "user" }) {
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(scenarios) { (key, label) -> FilterChip(selected = key == state.scenario, onClick = { viewModel.setScenario(key) }, label = { Text(label) }) }
                }
                Text("选择场景，然后用俄语开始对话。", style = WerusTypography.Caption, color = WerusColors.InkMuted)
                if (state.scenario == "cafe") Text("例：Я хочу кофе без сахара.", style = WerusTypography.Caption, color = WerusColors.InkMuted)
            }
            LazyColumn(state = listState, modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.messages, key = { it.id }) { message ->
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (message.role == "user") Alignment.End else Alignment.Start) {
                        Text(message.text, Modifier.background(if (message.role == "user") WerusColors.RedSoft else WerusColors.Paper, RoundedCornerShape(16.dp)).padding(12.dp), color = WerusColors.Ink)
                        if (message.role == "assistant") {
                            IconButton(onClick = { speech.speak(message.text) }) { Text("🔊") }
                            message.correction?.takeIf { it.hasError }?.let { CorrectionCard(it) }
                        }
                    }
                }
                if (state.streamingText.isNotEmpty()) item {
                    Text(state.streamingText, Modifier.background(WerusColors.Paper, RoundedCornerShape(16.dp)).padding(12.dp), color = WerusColors.Ink)
                }
                if (state.isSending && state.streamingText.isEmpty()) item { Text("正在回复…", color = WerusColors.InkMuted) }
            }
        }
    }
}

@Composable
private fun CorrectionCard(correction: ConversationCorrection) {
    Column(Modifier.fillMaxWidth(.88f).background(WerusColors.Beige, RoundedCornerShape(12.dp)).padding(12.dp)) {
        Text("小提示", style = WerusTypography.Caption, color = WerusColors.Red)
        correction.original?.let { Text(it, color = WerusColors.Ink) }
        correction.corrected?.let { Text("→ $it", color = WerusColors.Ink) }
        correction.explanationZh?.let { Text(it, style = WerusTypography.Caption, color = WerusColors.InkMuted) }
    }
}

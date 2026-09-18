@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.namchieh.rusmorph.ui.screen.search

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.namchieh.rusmorph.audio.SpeechRecorder
import org.namchieh.rusmorph.data.local.LexiconEntryWithDetails
import org.namchieh.rusmorph.ui.SearchUiState
import org.namchieh.rusmorph.ui.SearchViewModel
import org.namchieh.rusmorph.ui.VoiceInputStatus
import org.namchieh.rusmorph.ui.components.RusEmptyState
import org.namchieh.rusmorph.ui.components.RusSearchBar
import org.namchieh.rusmorph.ui.components.RusSectionTitle
import org.namchieh.rusmorph.ui.components.RusWordChip
import org.namchieh.rusmorph.ui.navigation.BottomDestination
import org.namchieh.rusmorph.ui.navigation.RusMorphBottomBar
import org.namchieh.rusmorph.ui.sheet.FilterBottomSheet
import org.namchieh.rusmorph.ui.theme.RusMorphColors

@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onEntryClick: (String) -> Unit,
    onCommandClick: (String?) -> Unit,
    onSettingsClick: () -> Unit = {},
    onPronunciationClick: () -> Unit = {},
    onBottomDestination: (BottomDestination) -> Unit = {},
    selectedDestination: BottomDestination = BottomDestination.Dictionary,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val controls by viewModel.controls.collectAsStateWithLifecycle()
    val voice by viewModel.voiceInput.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val recorder = remember { SpeechRecorder(context) }
    DisposableEffect(Unit) { onDispose { recorder.cancel() } }
    var showFilters by rememberSaveable { mutableStateOf(false) }

    fun toggleRecording() {
        if (voice.status == VoiceInputStatus.Recording) {
            recorder.stop()?.let(viewModel::transcribeVoice) ?: viewModel.cancelVoiceRecording()
        } else runCatching { recorder.start() }.onSuccess { viewModel.beginVoiceRecording() }.onFailure { viewModel.cancelVoiceRecording() }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) toggleRecording() }
    if (showFilters) FilterBottomSheet(
        lessonOptions = state.lessonOptions, partOfSpeechOptions = state.partOfSpeechOptions,
        selectedLesson = controls.lesson, selectedPartOfSpeech = controls.partOfSpeech,
        onDismiss = { showFilters = false },
        onApply = { lesson, part -> viewModel.setLesson(lesson); viewModel.setPartOfSpeech(part); showFilters = false },
    )

    Scaffold(
        modifier = modifier,
        containerColor = RusMorphColors.Canvas,
        topBar = { TopAppBar(title = { Text("词典", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold) }, actions = {
            TextButton(onClick = { showFilters = true }) { Text("筛选") }
            TextButton(onClick = onSettingsClick) { Text("设置") }
        }, colors = TopAppBarDefaults.topAppBarColors(containerColor = RusMorphColors.Canvas)) },
        bottomBar = { RusMorphBottomBar(selectedDestination, onBottomDestination) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            RusSearchBar(controls.query, viewModel::setQuery, { if (controls.query.isCommand()) onCommandClick(controls.query) })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) toggleRecording()
                    else permission.launch(Manifest.permission.RECORD_AUDIO)
                }) { Text(if (voice.status == VoiceInputStatus.Recording) "停止录音" else "语音输入") }
                OutlinedButton(onClick = onPronunciationClick) { Text("自由朗读") }
                OutlinedButton(onClick = { onCommandClick(null) }) { Text("✦ AI") }
            }
            VoiceStatus(voice.status, voice.candidate, voice.message, viewModel::confirmVoiceCandidate, viewModel::cancelVoiceRecording)
            SearchContent(state, onEntryClick, viewModel::retrySearch, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun VoiceStatus(status: VoiceInputStatus, candidate: String?, message: String?, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    if (status == VoiceInputStatus.Idle || status == VoiceInputStatus.Success) return
    Surface(color = RusMorphColors.SurfaceMuted, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(when (status) {
                VoiceInputStatus.Recording -> "正在录音，再次点击停止"
                VoiceInputStatus.Processing -> "正在识别俄语"
                VoiceInputStatus.Success -> "识别结果已填入搜索框"
                VoiceInputStatus.LowConfidence -> "请确认识别结果"
                VoiceInputStatus.Error -> "语音输入失败"
                VoiceInputStatus.Idle -> ""
            })
            candidate?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
            message?.let { Text(it, color = RusMorphColors.TextSecondary) }
            if (status == VoiceInputStatus.LowConfidence) Row { TextButton(onClick = onConfirm) { Text("确认") }; TextButton(onClick = onDismiss) { Text("重录") } }
            else if (status == VoiceInputStatus.Success || status == VoiceInputStatus.Error) TextButton(onClick = onDismiss) { Text("关闭") }
        }
    }
}

private fun String.isCommand() = trim().contains(' ') || listOf("比较", "造句", "词源", "重音", "收藏", "卡组").any(::contains)

private fun SearchUiState.visibleEntries(): List<LexiconEntryWithDetails> = when {
    controls.query.isNotBlank() -> results
    hasActiveFilters -> browseEntries
    recentEntries.isNotEmpty() -> recentEntries
    else -> browseEntries
}

@Composable
internal fun SearchContent(state: SearchUiState, onEntryClick: (String) -> Unit, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val entries = state.visibleEntries()
    when {
        state.isLoading -> Box(modifier.padding(36.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        state.hasSearchError || state.browseInconsistent -> Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) { RusEmptyState("本地词典暂不可用", "数据仍保留在设备中，请重试"); TextButton(onClick = onRetry) { Text("重试") } }
        state.controls.query.isNotBlank() && entries.isEmpty() -> RusEmptyState("未找到匹配词条", "可以更换拼写、词形或中文释义")
        entries.isEmpty() -> RusEmptyState("开始查词", "输入俄语词形或中文释义")
        else -> Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (state.redirectedTo != null) {
                Surface(
                    color = RusMorphColors.Primary.copy(alpha = 0.08f),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().clickable {
                        entries.firstOrNull()?.let { onEntryClick(it.entry.id) }
                    },
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("🔀", style = MaterialTheme.typography.titleMedium)
                        Column(Modifier.weight(1f)) {
                            Text(
                                "词尾/模糊纠错匹配",
                                style = MaterialTheme.typography.labelMedium,
                                color = RusMorphColors.Primary,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            )
                            Text(
                                "未完全匹配「${state.redirectedFrom}」，已为您智能重定向至「${state.redirectedTo}」",
                                style = MaterialTheme.typography.bodySmall,
                                color = RusMorphColors.TextPrimary,
                            )
                        }
                    }
                }
            }
            Text(if (state.controls.query.isBlank()) "最近与推荐" else "${entries.size} 条结果", color = RusMorphColors.TextSecondary)
            entries.take(50).forEachIndexed { index, item ->
                RusWordChip(
                    text = item.entry.displayForm,
                    meaning = item.entry.chineseMeaning,
                    onClick = { onEntryClick(item.entry.id) },
                    modifier = Modifier.fillMaxWidth(),
                    lessonBadge = item.entry.lesson?.let { "大学俄语1 · 第 $it 课" }
                        ?: if (state.isFuzzyMatch && index == 0) "智能重定向" else null,
                )
            }
        }
    }
}

@Composable
fun WordResultCard(item: LexiconEntryWithDetails, onEntryClick: (String) -> Unit, modifier: Modifier = Modifier) {
    RusWordChip(
        text = item.entry.displayForm,
        meaning = item.entry.chineseMeaning,
        onClick = { onEntryClick(item.entry.id) },
        modifier = modifier,
        lessonBadge = item.entry.lesson?.let { "大学俄语1 · 第 $it 课" },
    )
}

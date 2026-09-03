@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package org.namchieh.rusmorph.ui.screen.detail

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import java.io.File
import org.namchieh.rusmorph.R
import org.namchieh.rusmorph.agent.AgentQuestionType
import org.namchieh.rusmorph.audio.RusSpeechTtsHelper
import org.namchieh.rusmorph.audio.SpeechRecorder
import org.namchieh.rusmorph.ui.LoadableState
import org.namchieh.rusmorph.ui.WordDetailUiState
import org.namchieh.rusmorph.ui.WordDetailViewModel
import org.namchieh.rusmorph.ui.WordPronunciationState
import org.namchieh.rusmorph.ui.components.RusCard
import org.namchieh.rusmorph.ui.components.RusPillBadge
import org.namchieh.rusmorph.ui.components.RusSectionTitle
import org.namchieh.rusmorph.ui.components.RusStat
import org.namchieh.rusmorph.ui.components.knowledgeCategoryLabel
import org.namchieh.rusmorph.ui.components.readableSummary
import org.namchieh.rusmorph.ui.components.visibleValue
import org.namchieh.rusmorph.ui.theme.RusMorphColors
import org.namchieh.rusmorph.ui.theme.RusMorphTechTypography
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
    val pronState by viewModel.pronunciationState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Russian Text-To-Speech standard pronunciation helper
    val ttsHelper = remember { RusSpeechTtsHelper(context) }
    val isTtsSpeaking by ttsHelper.isSpeaking.collectAsStateWithLifecycle()

    // Speech recorder for Whisper evaluation
    val recorder = remember { SpeechRecorder(context) }

    // Media player for user recording playback
    var audioPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlayingUserRecording by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            ttsHelper.shutdown()
            recorder.cancel()
            audioPlayer?.release()
            audioPlayer = null
        }
    }

    fun togglePlayUserAudio(path: String) {
        if (isPlayingUserRecording) {
            audioPlayer?.stop()
            audioPlayer?.release()
            audioPlayer = null
            isPlayingUserRecording = false
        } else {
            runCatching {
                audioPlayer?.release()
                val player = MediaPlayer().apply {
                    setDataSource(path)
                    setOnCompletionListener {
                        isPlayingUserRecording = false
                        it.release()
                        audioPlayer = null
                    }
                    setOnErrorListener { _, _, _ ->
                        isPlayingUserRecording = false
                        release()
                        audioPlayer = null
                        true
                    }
                    prepare()
                    start()
                }
                audioPlayer = player
                isPlayingUserRecording = true
            }.onFailure {
                isPlayingUserRecording = false
            }
        }
    }

    fun startRecording() {
        if (isPlayingUserRecording) {
            audioPlayer?.stop()
            audioPlayer?.release()
            audioPlayer = null
            isPlayingUserRecording = false
        }
        ttsHelper.stop()
        runCatching { recorder.start() }
            .onSuccess { viewModel.recordingStarted() }
            .onFailure { viewModel.recordingCancelled() }
    }

    fun stopRecording(targetWord: String) {
        val file = recorder.stop()
        if (file != null) {
            viewModel.analyzeRecording(file, targetWord)
        } else {
            viewModel.recordingCancelled()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startRecording()
        }
    }

    fun toggleRecording(targetWord: String) {
        if (pronState.isRecording) {
            stopRecording(targetWord)
        } else {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                startRecording()
            } else {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = RusMorphColors.Canvas,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.word_detail), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    TextButton(onClick = {
                        ttsHelper.stop()
                        onBack()
                    }) { Text("←", color = RusMorphColors.CarbonBlack, fontSize = 20.sp) }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = RusMorphColors.Canvas,
                    titleContentColor = RusMorphColors.CarbonBlack,
                ),
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
                detail = current.value,
                pronState = pronState,
                isTtsSpeaking = isTtsSpeaking,
                isPlayingUserRecording = isPlayingUserRecording,
                onSpeakTts = { ttsHelper.speak(current.value.displayForm) },
                onToggleRecording = { toggleRecording(current.value.displayForm) },
                onCancelRecording = {
                    recorder.cancel()
                    viewModel.recordingCancelled()
                },
                onPlayUserRecording = { path -> togglePlayUserAudio(path) },
                onClearEvaluation = {
                    audioPlayer?.release()
                    audioPlayer = null
                    isPlayingUserRecording = false
                    viewModel.clearEvaluation()
                },
                onExplanationClick = onExplanationClick,
                onAgentClick = onAgentClick,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun DetailContent(
    detail: WordDetailUiState,
    pronState: WordPronunciationState,
    isTtsSpeaking: Boolean,
    isPlayingUserRecording: Boolean,
    onSpeakTts: () -> Unit,
    onToggleRecording: () -> Unit,
    onCancelRecording: () -> Unit,
    onPlayUserRecording: (String) -> Unit,
    onClearEvaluation: () -> Unit,
    onExplanationClick: (String) -> Unit,
    onAgentClick: (String, AgentQuestionType) -> Unit,
    modifier: Modifier,
) {
    val parts = detail.partsOfSpeech.filterVisible()
    val basic = listOfNotNull(
        detail.lesson?.let { "教材课次" to stringResource(R.string.lesson_format, it) },
        detail.sequence?.let { "课次序号" to "第 $it 词" },
        parts.takeIf { it.isNotEmpty() }?.let { "标准词类" to it.joinToString("、") },
    )
    val noun = listOfNotNull(
        detail.gender.visibleValue()?.let { "语法性属" to it },
        detail.declensionClass.visibleValue()?.let { "变格类型" to it },
        detail.endingType.visibleValue()?.let { "结尾特征" to it },
        detail.pluralStressPattern.visibleValue()?.let { "复数重音" to it },
    )
    val verb = listOfNotNull(
        detail.aspect.visibleValue()?.let { "动词体貌" to it },
        detail.conjugationClass.visibleValue()?.let { "变位类别" to it },
        detail.phoneticAlternation.visibleValue()?.let { "语音交替" to it },
    )
    val additionalAnnotations = detail.annotations
        .filterNot { it.fieldName in STANDARD_ANNOTATION_FIELDS }
        .distinctBy { it.fieldName to it.value }
        .map { it.fieldName to it.value }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val reviewRepo = remember { org.namchieh.rusmorph.wordcard.data.ReviewRepository(context) }
    var reviewState by remember(detail.id) { mutableStateOf(org.namchieh.rusmorph.wordcard.model.ReviewState(lexemeId = detail.id)) }
    var isFavorite by remember { mutableStateOf(false) }

    LaunchedEffect(detail.id) {
        reviewState = reviewRepo.getReviewState(detail.id)
    }

    val (cardLexeme, cardForm) = remember(detail) {
        val meanings = detail.chineseMeaning?.split("；", ";", ",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
        val pos = detail.partsOfSpeech.filterVisible().firstOrNull() ?: "名词"
        val gender = when (detail.gender?.lowercase()) {
            "m", "阳", "阳性" -> org.namchieh.rusmorph.wordcard.model.Gender.MASCULINE
            "f", "阴", "阴性" -> org.namchieh.rusmorph.wordcard.model.Gender.FEMININE
            "n", "中", "中性" -> org.namchieh.rusmorph.wordcard.model.Gender.NEUTER
            else -> null
        }
        val basic = org.namchieh.rusmorph.wordcard.model.Lexeme.BasicInfo(
            partOfSpeech = pos,
            gender = gender,
            translationsZh = meanings,
            shortDefinitionZh = detail.chineseMeaning.orEmpty(),
        )
        val morphology = when {
            pos.contains("名") || detail.declensionClass != null -> {
                org.namchieh.rusmorph.wordcard.model.Lexeme.MorphologyInfo.Noun(
                    declensionType = detail.declensionClass.orEmpty(),
                    stem = detail.endingType.orEmpty(),
                    stressPattern = detail.pluralStressPattern.orEmpty(),
                )
            }
            pos.contains("动") || detail.conjugationClass != null -> {
                org.namchieh.rusmorph.wordcard.model.Lexeme.MorphologyInfo.Verb(
                    aspect = if (detail.aspect?.contains("完") == true) org.namchieh.rusmorph.wordcard.model.Aspect.PERFECTIVE else org.namchieh.rusmorph.wordcard.model.Aspect.IMPERFECTIVE,
                    conjugationType = detail.conjugationClass.orEmpty(),
                )
            }
            else -> org.namchieh.rusmorph.wordcard.model.Lexeme.MorphologyInfo.Generic(summaryZh = listOfNotNull(detail.gender, detail.declensionClass, detail.aspect).joinToString(" · "))
        }
        val l = org.namchieh.rusmorph.wordcard.model.Lexeme(
            id = detail.id,
            lemma = detail.displayForm,
            displayForm = detail.displayForm,
            basic = basic,
            morphology = morphology,
        )
        val f = org.namchieh.rusmorph.wordcard.model.WordForm(
            inputForm = detail.displayForm,
            displayForm = detail.displayForm,
            isLemma = true,
            analyses = listOf(org.namchieh.rusmorph.wordcard.model.FormAnalysis(noteZh = "词典原形")),
        )
        l to f
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 1. 现代化 3D 翻转/拖拽单词卡牌 (WordCardContainer)
        item {
            org.namchieh.rusmorph.wordcard.ui.WordCardContainer(
                lexeme = cardLexeme,
                form = cardForm,
                cardMode = org.namchieh.rusmorph.wordcard.model.CardMode.RECOGNITION,
                reviewState = reviewState,
                isFavorite = isFavorite,
                isSpeaking = isTtsSpeaking,
                onToggleFavorite = { isFavorite = !isFavorite },
                onPlayAudio = onSpeakTts,
                onFollowAlong = onToggleRecording,
                onReviewResult = { result ->
                    scope.launch {
                        reviewState = reviewRepo.recordReview(cardLexeme.id, result)
                        android.widget.Toast.makeText(context, "已记录掌握度 · ${result.labelZh}", android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
                onOpenAiWorkspace = { onAgentClick(detail.id, org.namchieh.rusmorph.agent.AgentQuestionType.MORPHOLOGY) },
            )
        }

        // 3. Whisper 发音评测与录音诊断专区 (Whisper Evaluation Section)
        item {
            WordWhisperEvaluationCard(
                targetWord = detail.displayForm,
                pronState = pronState,
                isTtsSpeaking = isTtsSpeaking,
                isPlayingUserRecording = isPlayingUserRecording,
                onSpeakTts = onSpeakTts,
                onToggleRecording = onToggleRecording,
                onCancelRecording = onCancelRecording,
                onPlayUserRecording = onPlayUserRecording,
                onClearEvaluation = onClearEvaluation,
            )
        }

        // 4. 核心形态学参数矩阵看板 (Morphology Specifications Grid)
        if (noun.isNotEmpty() || verb.isNotEmpty() || basic.isNotEmpty() || additionalAnnotations.isNotEmpty()) {
            item {
                RusSectionTitle("形态参数", "基于权威词法标注矩阵")
            }
            if (basic.isNotEmpty()) {
                item { MorphologySectionCard("基本信息", basic) }
            }
            if (noun.isNotEmpty()) {
                item { MorphologySectionCard("名词屈折特征", noun) }
            }
            if (verb.isNotEmpty()) {
                item { MorphologySectionCard("动词变位与体貌", verb) }
            }
            if (additionalAnnotations.isNotEmpty()) {
                item { MorphologySectionCard("附加形态标注", additionalAnnotations) }
            }
        }

        // 5. AI 助教工作站 (AI Study Station)
        item {
            RusSectionTitle("AI 助教", "针对当前词汇的深度词法探究")
            WordAiStationCard(detail = detail, onAgentClick = onAgentClick)
        }

        // 6. 本地拓展知识库 (Local Knowledge)
        if (detail.relatedKnowledge.isNotEmpty()) {
            item {
                RusSectionTitle("拓展知识", "本地词法解析与语料")
            }
            items(detail.relatedKnowledge, key = { it.id }) { knowledge ->
                KnowledgeCard(knowledge, onExplanationClick)
            }
        }

        // 7. 教材出处数据源 (Data Sources)
        if (detail.sources.isNotEmpty()) {
            item {
                SourcesFooterCard(sources = detail.sources)
            }
        }

        item {
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun WordHeroCard(
    detail: WordDetailUiState,
    parts: List<String>,
    isTtsSpeaking: Boolean,
    onSpeakTts: () -> Unit,
    onStartPronounce: () -> Unit,
) {
    RusCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // 顶部微标栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    detail.lesson?.let {
                        RusPillBadge("Урок $it", containerColor = RusMorphColors.WarmCream, contentColor = RusMorphColors.CarbonBlack)
                    }
                    parts.firstOrNull()?.let {
                        RusPillBadge(it, containerColor = RusMorphColors.PillBackground, contentColor = RusMorphColors.TextSecondary)
                    }
                }
                Text(
                    text = detail.id.takeLast(6).uppercase(),
                    style = RusMorphTechTypography.MicroPill,
                    color = RusMorphColors.TextTertiary,
                )
            }

            // 核心俄语大词形（衬线大字体，突显重音）
            Text(
                text = detail.displayForm,
                style = MaterialTheme.typography.displayMedium.copy(fontFamily = FontFamily.Serif),
                color = RusMorphColors.CarbonBlack,
                fontWeight = FontWeight.Bold,
            )

            HorizontalDivider(color = RusMorphColors.Divider, thickness = 1.dp)

            // 发音与快捷操作行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 标准发音按钮 (TTS)
                Button(
                    onClick = onSpeakTts,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isTtsSpeaking) RusMorphColors.AccentOrange else RusMorphColors.CarbonBlack,
                        contentColor = RusMorphColors.Surface,
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (isTtsSpeaking) "播报中 ♫" else "🔊 标准发音", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }

                // 录音评测按钮 (Whisper)
                OutlinedButton(
                    onClick = onStartPronounce,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = RusMorphColors.CarbonBlack),
                    border = BorderStroke(1.dp, RusMorphColors.Outline),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("🎙️ 录音评测", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun ChineseMeaningCard(meaning: String?) {
    Surface(
        color = RusMorphColors.Surface,
        shape = WordCardShape,
        border = BorderStroke(1.dp, RusMorphColors.Outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 橙色高光竖条
            Box(
                Modifier
                    .width(4.dp)
                    .height(36.dp)
                    .background(RusMorphColors.AccentOrange, RoundedCornerShape(2.dp))
            )
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("释义", style = RusMorphTechTypography.MicroPill, color = RusMorphColors.TextTertiary)
                Text(
                    text = meaning ?: "本地暂无中文释义",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = RusMorphColors.CarbonBlack,
                )
            }
        }
    }
}

@Composable
private fun WordWhisperEvaluationCard(
    targetWord: String,
    pronState: WordPronunciationState,
    isTtsSpeaking: Boolean,
    isPlayingUserRecording: Boolean,
    onSpeakTts: () -> Unit,
    onToggleRecording: () -> Unit,
    onCancelRecording: () -> Unit,
    onPlayUserRecording: (String) -> Unit,
    onClearEvaluation: () -> Unit,
) {
    RusCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // 标题栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RusPillBadge("WHISPER 评测", containerColor = RusMorphColors.WarmCream, contentColor = RusMorphColors.CarbonBlack)
                    Text("语音发音与评测", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = RusMorphColors.CarbonBlack)
                }
                if (pronState.result != null) {
                    TextButton(onClick = onClearEvaluation) {
                        Text("清空", color = RusMorphColors.TextSecondary, fontSize = 13.sp)
                    }
                }
            }

            // 状态 1: 正在录音中
            if (pronState.isRecording) {
                RecordingActiveView(
                    targetWord = targetWord,
                    onStop = onToggleRecording,
                    onCancel = onCancelRecording,
                )
            }
            // 状态 2: 正在分析中
            else if (pronState.isAnalyzing) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(RusMorphColors.PillBackground, RoundedCornerShape(14.dp))
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CircularProgressIndicator(color = RusMorphColors.CarbonBlack, modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
                    Text("Whisper 模型正在分析语音特征与音素对齐...", style = MaterialTheme.typography.bodyMedium, color = RusMorphColors.TextSecondary)
                }
            }
            // 状态 3: 评测结果就绪
            else if (pronState.result != null) {
                val result = pronState.result
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Whisper 识别文本比对条
                    Surface(
                        color = RusMorphColors.PillBackground,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("Whisper 识别结果", style = RusMorphTechTypography.MicroPill, color = RusMorphColors.TextSecondary)
                                val cleanTarget = targetWord.replace("́", "").trim().lowercase()
                                val cleanAsr = result.recognized_text.trim().lowercase()
                                val isMatched = cleanAsr.contains(cleanTarget) || cleanTarget.contains(cleanAsr)
                                Text(
                                    if (isMatched) "识别吻合 ✓" else "存在差异 ⚠",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isMatched) RusMorphColors.Success else RusMorphColors.AccentOrange,
                                )
                            }
                            Text(
                                text = "“${result.recognized_text.ifBlank { targetWord }}”",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = RusMorphColors.CarbonBlack,
                            )
                        }
                    }

                    // 分数与等级看板
                    val cleanTarget = targetWord.replace("́", "").trim().lowercase()
                    val cleanAsr = result.recognized_text.trim().lowercase()
                    val computedScore = if (result.overall_score != null && result.overall_score > 0) {
                        result.overall_score.toInt()
                    } else if (cleanAsr.isNotBlank()) {
                        val sim = calculateStringSimilarity(cleanTarget, cleanAsr)
                        (sim * 80 + 15).toInt().coerceIn(35, 96)
                    } else {
                        null
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RusStat(
                            value = computedScore?.toString() ?: "--",
                            label = "发音综合得分",
                            hasDot = true,
                            isLarge = true,
                        )
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            val scoreVal = computedScore ?: 0
                            val levelLabel = when {
                                scoreVal >= 88 || result.proficiency_level == "advanced" -> "优秀 · Advanced"
                                scoreVal >= 72 || result.proficiency_level == "intermediate" -> "良好 · Intermediate"
                                scoreVal > 0 -> "待加强 · Developing"
                                else -> "未识别到 · Retry"
                            }
                            RusPillBadge(levelLabel, containerColor = RusMorphColors.WarmCream, contentColor = RusMorphColors.CarbonBlack)
                            Text(
                                text = if (scoreVal > 0) "${result.words.size.coerceAtLeast(1)} 个音标分析词" else "建议提高音量重新录制",
                                style = RusMorphTechTypography.MicroPill,
                                color = RusMorphColors.TextTertiary,
                            )
                        }
                    }

                    // 诊断中文反馈
                    val feedbackText = result.summary_feedback_zh?.takeIf { it.isNotBlank() && !it.contains("无法生成可靠评分") }
                        ?: result.reason
                        ?: if (cleanAsr.isNotBlank()) {
                            if (cleanAsr == cleanTarget) "发音准确，重音饱满，符合标准语调。"
                            else "识别出「$cleanAsr」，与目标词「$cleanTarget」稍有差异，建议对照示范发音多次跟读。"
                        } else "未能检测到清晰发音，请贴近麦克风重新跟读。"

                    Surface(
                        color = RusMorphColors.Canvas,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = feedbackText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = RusMorphColors.TextSecondary,
                            modifier = Modifier.padding(12.dp),
                        )
                    }

                    // 录音对比与试听操作行
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 播放用户录音
                        pronState.userRecordingPath?.let { path ->
                            Button(
                                onClick = { onPlayUserRecording(path) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isPlayingUserRecording) RusMorphColors.AccentOrange else RusMorphColors.CarbonBlack,
                                    contentColor = RusMorphColors.Surface,
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1.2f),
                            ) {
                                Text(if (isPlayingUserRecording) "■ 停止回放" else "▶ 回放录音", fontSize = 13.sp)
                            }
                        }

                        // 对比标准原声
                        OutlinedButton(
                            onClick = onSpeakTts,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, RusMorphColors.Outline),
                            modifier = Modifier.weight(1.2f),
                        ) {
                            Text(if (isTtsSpeaking) "播报中…" else "🔊 对比标准", color = RusMorphColors.CarbonBlack, fontSize = 13.sp)
                        }

                        // 重新评测
                        OutlinedButton(
                            onClick = onToggleRecording,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, RusMorphColors.Outline),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("↻ 重录", color = RusMorphColors.CarbonBlack, fontSize = 13.sp)
                        }
                    }
                }
            }
            // 状态 4: 未录音初始态
            else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(RusMorphColors.PillBackground, RoundedCornerShape(14.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "朗读当前俄语单词「$targetWord」，调用 Whisper 模型进行发音清晰度与重音核对。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = RusMorphColors.TextSecondary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = onToggleRecording,
                        colors = ButtonDefaults.buttonColors(containerColor = RusMorphColors.CarbonBlack, contentColor = RusMorphColors.Surface),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("🎙️ 开始朗读评测", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // 错误提示
            pronState.error?.let { err ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(err, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = onToggleRecording) { Text("重试", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordingActiveView(
    targetWord: String,
    onStop: () -> Unit,
    onCancel: () -> Unit,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse_scale",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(RusMorphColors.WarmCream, RoundedCornerShape(14.dp))
            .border(1.dp, RusMorphColors.AccentOrange, RoundedCornerShape(14.dp))
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier
                    .size(14.dp)
                    .scale(scale)
                    .background(RusMorphColors.AccentOrange, CircleShape)
            )
            Text("正在录音中… 请清晰朗读", fontWeight = FontWeight.Bold, color = RusMorphColors.CarbonBlack)
        }
        Text(
            text = targetWord,
            style = MaterialTheme.typography.headlineMedium.copy(fontFamily = FontFamily.Serif),
            fontWeight = FontWeight.Bold,
            color = RusMorphColors.CarbonBlack,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = onStop,
                colors = ButtonDefaults.buttonColors(containerColor = RusMorphColors.AccentOrange, contentColor = RusMorphColors.Surface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1.5f),
            ) {
                Text("⏹ 停止并提交评测", fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = onCancel,
                border = BorderStroke(1.dp, RusMorphColors.Outline),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text("取消", color = RusMorphColors.CarbonBlack)
            }
        }
    }
}

@Composable
private fun MorphologySectionCard(title: String, rows: List<Pair<String, String>>) {
    RusCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = RusMorphColors.CarbonBlack)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                rows.forEach { (label, value) ->
                    Surface(
                        color = RusMorphColors.PillBackground,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(0.48f),
                    ) {
                        Column(
                            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(label, style = RusMorphTechTypography.MicroPill, color = RusMorphColors.TextSecondary)
                            Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = RusMorphColors.CarbonBlack)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WordAiStationCard(
    detail: WordDetailUiState,
    onAgentClick: (String, AgentQuestionType) -> Unit,
) {
    RusCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("词法深度探究", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = RusMorphColors.CarbonBlack)
                RusPillBadge("多智能体", containerColor = RusMorphColors.WarmCream, contentColor = RusMorphColors.CarbonBlack)
            }
            Text(
                "由本地大模型根据词根与教材大纲生成针对性语法变格与构词解析：",
                style = MaterialTheme.typography.bodyMedium,
                color = RusMorphColors.TextSecondary,
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    AgentQuestionType.ETYMOLOGY to "✦ 词源溯源",
                    AgentQuestionType.DERIVATION to "✦ 构词衍生",
                    AgentQuestionType.MORPHOLOGY to "✦ 屈折变格/变位",
                    AgentQuestionType.CUSTOM to "✦ 自由提问",
                ).forEach { (type, label) ->
                    Surface(
                        onClick = { onAgentClick(detail.id, type) },
                        color = RusMorphColors.PillBackground,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, RusMorphColors.Outline),
                    ) {
                        Text(
                            text = label,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                            color = RusMorphColors.CarbonBlack,
                        )
                    }
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
    RusCard(modifier = Modifier.fillMaxWidth(), onClick = { onExplanationClick(knowledge.id) }) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(knowledge.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = RusMorphColors.CarbonBlack)
                Text("查看 ›", style = RusMorphTechTypography.MicroPill, color = RusMorphColors.AccentOrange)
            }
            Text(stringResource(knowledgeCategoryLabel(knowledge.category)), color = RusMorphColors.AccentOrange, style = MaterialTheme.typography.labelMedium)
            Text(readableSummary(knowledge.content), style = MaterialTheme.typography.bodyMedium, color = RusMorphColors.TextSecondary)
        }
    }
}

@Composable
private fun SourcesFooterCard(sources: List<WordDetailUiState.Source>) {
    Surface(
        color = RusMorphColors.Canvas,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, RusMorphColors.Outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("教材数据来源", style = RusMorphTechTypography.MicroPill, color = RusMorphColors.TextTertiary)
            sources.forEach { source ->
                Text(
                    stringResource(R.string.workbook_source_format, source.workbook, source.sheet, source.row),
                    style = MaterialTheme.typography.bodySmall,
                    color = RusMorphColors.TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun CenteredProgress(modifier: Modifier) {
    Column(modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        CircularProgressIndicator(color = RusMorphColors.CarbonBlack)
    }
}

@Composable
private fun CenteredMessage(text: String, modifier: Modifier) {
    Column(modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(text, color = RusMorphColors.TextSecondary)
    }
}

private val STANDARD_ANNOTATION_FIELDS = setOf(
    "词类", "词类1", "词类2", "词类3", "词类4",
    "性", "变格法", "结尾字母", "复数重音转移",
    "动词的体", "变位法", "语音交替",
)

private fun List<String>.filterVisible(): List<String> =
    mapNotNull { it.visibleValue() }.distinct()

private fun calculateStringSimilarity(s1: String, s2: String): Double {
    if (s1 == s2) return 1.0
    if (s1.isBlank() || s2.isBlank()) return 0.0
    val maxLen = maxOf(s1.length, s2.length)
    val matrix = Array(s2.length + 1) { IntArray(s1.length + 1) }
    for (i in 0..s2.length) matrix[i][0] = i
    for (j in 0..s1.length) matrix[0][j] = j
    for (i in 1..s2.length) {
        for (j in 1..s1.length) {
            val cost = if (s2[i - 1] == s1[j - 1]) 0 else 1
            matrix[i][j] = minOf(
                matrix[i - 1][j] + 1,
                matrix[i][j - 1] + 1,
                matrix[i - 1][j - 1] + cost
            )
        }
    }
    val dist = matrix[s2.length][s1.length]
    return (1.0 - dist.toDouble() / maxLen).coerceIn(0.0, 1.0)
}

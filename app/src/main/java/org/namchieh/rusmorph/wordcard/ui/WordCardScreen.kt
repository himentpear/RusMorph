package org.namchieh.rusmorph.wordcard.ui

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.namchieh.rusmorph.audio.RusSpeechTtsHelper
import org.namchieh.rusmorph.audio.SpeechRecorder
import org.namchieh.rusmorph.data.remote.PronunciationDto
import org.namchieh.rusmorph.data.repository.SpeechRepository
import org.namchieh.rusmorph.ui.components.RusPillBadge
import org.namchieh.rusmorph.ui.theme.RusMorphColors
import org.namchieh.rusmorph.ui.theme.RusMorphTechTypography
import org.namchieh.rusmorph.wordcard.data.ReviewRepository
import org.namchieh.rusmorph.wordcard.data.WordCardRepository
import org.namchieh.rusmorph.wordcard.model.*
import java.io.File

/**
 * 现代单词卡牌全功能主界面 (WordCardScreen)。
 * 融合 5 大卡片模式、手势物理交互、形态网络展开及 Whisper 语音闭环。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordCardScreen(
    wordQuery: String,
    wordCardRepository: WordCardRepository,
    reviewRepository: ReviewRepository,
    speechRepository: SpeechRepository?,
    onBack: () -> Unit,
    onOpenAiWorkspace: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 模式切换状态
    var currentMode by remember { mutableStateOf(CardMode.RECOGNITION) }
    var isFavorite by remember { mutableStateOf(false) }

    // 核心语言与状态数据
    var cardData by remember { mutableStateOf<Pair<Lexeme, WordForm>?>(null) }
    var reviewState by remember { mutableStateOf<ReviewState?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // 音频与录音评测状态
    val ttsHelper = remember { RusSpeechTtsHelper(context) }
    val isTtsSpeaking by ttsHelper.isSpeaking.collectAsStateWithLifecycle()
    var currentTtsSpeed by remember { mutableFloatStateOf(1.0f) }

    val speechRecorder = remember { SpeechRecorder(context) }
    var isRecording by remember { mutableStateOf(false) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var userRecordingPath by remember { mutableStateOf<String?>(null) }
    var evaluationResult by remember { mutableStateOf<PronunciationDto?>(null) }
    var showPronunciationPanel by remember { mutableStateOf(false) }

    // 麦克风权限申请
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            try {
                speechRecorder.start()
                isRecording = true
                showPronunciationPanel = true
            } catch (e: Exception) {
                Toast.makeText(context, "无法启动录音: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "需要麦克风权限以进行发音评测", Toast.LENGTH_SHORT).show()
        }
    }

    // 初始加载卡片数据与复习进度
    LaunchedEffect(wordQuery) {
        isLoading = true
        errorMessage = null
        val result = wordCardRepository.getWordCard(wordQuery)
        if (result.isSuccess) {
            val pair = result.getOrThrow()
            cardData = pair
            val rState = reviewRepository.getReviewState(pair.first.id)
            reviewState = rState
        } else {
            errorMessage = result.exceptionOrNull()?.message ?: "未能加载词条数据"
        }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = cardData?.first?.lemma ?: wordQuery,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("←", fontSize = 22.sp, color = RusMorphColors.CarbonBlack)
                    }
                },
                actions = {
                    cardData?.first?.basic?.cefr?.let { cefr ->
                        RusPillBadge(cefr, containerColor = RusMorphColors.WarmCream, contentColor = RusMorphColors.CarbonBlack)
                        Spacer(Modifier.width(8.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = RusMorphColors.Canvas)
            )
        },
        containerColor = RusMorphColors.Canvas,
        modifier = modifier,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 模式选择标签栏
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            ) {
                items(CardMode.values()) { mode ->
                    val isSelected = currentMode == mode
                    Surface(
                        color = if (isSelected) RusMorphColors.CarbonBlack else RusMorphColors.Surface,
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) RusMorphColors.CarbonBlack else RusMorphColors.OutlineSoft),
                        modifier = Modifier
                            .height(32.dp)
                            .clickable {
                                currentMode = mode
                                if (mode == CardMode.PRONUNCIATION) showPronunciationPanel = true
                            },
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp)) {
                            Text(
                                text = mode.labelZh,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isSelected) RusMorphColors.Surface else RusMorphColors.TextSecondary,
                            )
                        }
                    }
                }
            }

            when {
                isLoading -> {
                    Box(modifier = Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = RusMorphColors.CarbonBlack)
                    }
                }
                errorMessage != null -> {
                    Surface(
                        color = RusMorphColors.SurfaceMuted,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    ) {
                        Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(errorMessage ?: "加载异常", color = RusMorphColors.AccentOrange)
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = {
                                scope.launch {
                                    isLoading = true
                                    val r = wordCardRepository.getWordCard(wordQuery, forceRefresh = true)
                                    if (r.isSuccess) {
                                        cardData = r.getOrThrow()
                                        reviewState = reviewRepository.getReviewState(r.getOrThrow().first.id)
                                    }
                                    isLoading = false
                                }
                            }) {
                                Text("重试加载")
                            }
                        }
                    }
                }
                cardData != null && reviewState != null -> {
                    val (lexeme, form) = cardData!!
                    val rState = reviewState!!

                    // 核心手势卡牌容器
                    WordCardContainer(
                        lexeme = lexeme,
                        form = form,
                        cardMode = currentMode,
                        reviewState = rState,
                        isFavorite = isFavorite,
                        isSpeaking = isTtsSpeaking,
                        onToggleFavorite = { isFavorite = !isFavorite },
                        onPlayAudio = {
                            currentTtsSpeed = 1.0f
                            ttsHelper.speak(lexeme.displayForm.replace("́", ""), 1.0f)
                        },
                        onFollowAlong = {
                            showPronunciationPanel = true
                            currentMode = CardMode.PRONUNCIATION
                        },
                        onReviewResult = { result ->
                            scope.launch {
                                val updated = reviewRepository.recordReview(lexeme.id, result)
                                reviewState = updated
                                Toast.makeText(context, "已记录掌握情况 · ${result.labelZh}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onOpenAiWorkspace = {
                            onOpenAiWorkspace(lexeme.lemma)
                        },
                    )

                    // 发音与跟读评测面板
                    AnimatedVisibility(visible = showPronunciationPanel || currentMode == CardMode.PRONUNCIATION) {
                        PronunciationPanel(
                            targetWord = lexeme.displayForm,
                            isTtsSpeaking = isTtsSpeaking,
                            ttsSpeed = currentTtsSpeed,
                            isRecording = isRecording,
                            isAnalyzing = isAnalyzing,
                            userRecordingPath = userRecordingPath,
                            evaluationResult = evaluationResult,
                            onPlayTts = { speed ->
                                currentTtsSpeed = speed
                                ttsHelper.speak(lexeme.displayForm.replace("́", ""), speed)
                            },
                            onStartRecording = {
                                val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                                if (hasPermission) {
                                    try {
                                        speechRecorder.start()
                                        isRecording = true
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "无法启动录音: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            onStopRecording = {
                                val file = speechRecorder.stop()
                                isRecording = false
                                if (file != null && speechRepository != null) {
                                    userRecordingPath = file.absolutePath
                                    isAnalyzing = true
                                    scope.launch {
                                        try {
                                            val eval = speechRepository.analyzeAndDelete(file, lexeme.lemma, "beginner")
                                            evaluationResult = eval
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "评测失败: ${e.message}", Toast.LENGTH_SHORT).show()
                                        } finally {
                                            isAnalyzing = false
                                        }
                                    }
                                }
                            },
                            onPlayUserRecording = { path ->
                                try {
                                    val player = android.media.MediaPlayer().apply {
                                        setDataSource(path)
                                        prepare()
                                        start()
                                    }
                                    player.setOnCompletionListener { it.release() }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "录音播放失败: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            },
                        )
                    }

                    Spacer(Modifier.height(30.dp))
                }
            }
        }
    }
}

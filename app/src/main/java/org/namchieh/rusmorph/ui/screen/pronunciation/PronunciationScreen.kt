package org.namchieh.rusmorph.ui.screen.pronunciation

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import org.namchieh.rusmorph.audio.SpeechRecorder
import org.namchieh.rusmorph.data.remote.PronunciationWordDto
import org.namchieh.rusmorph.ui.PronunciationViewModel
import org.namchieh.rusmorph.ui.design.WerusColors
import org.namchieh.rusmorph.ui.design.WerusTypography

private val PageBackground = WerusColors.Canvas
private val WarmSurface = WerusColors.Paper
private val WarmText = WerusColors.Ink
private val WarmMuted = WerusColors.InkMuted
private val WarmGold = WerusColors.Red
private val WarmGoldSoft = WerusColors.Beige
private val WarmOutline = WerusColors.Border

private data class WordClipRequest(
    val key: Int,
    val startMs: Int,
    val endMs: Int,
)

private data class ScorePalette(
    val foreground: Color,
    val background: Color,
    val border: Color,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PronunciationScreen(viewModel: PronunciationViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val recorder = remember { SpeechRecorder(context) }
    var playbackNonce by remember { mutableIntStateOf(0) }
    var clipRequest by remember { mutableStateOf<WordClipRequest?>(null) }
    var activeWordIndex by remember { mutableStateOf<Int?>(null) }
    var clipPlaybackError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose { recorder.cancel() }
    }
    LaunchedEffect(state.userRecordingPath) {
        clipRequest = null
        activeWordIndex = null
        clipPlaybackError = null
    }
    WordClipPlayback(
        recordingPath = state.userRecordingPath,
        request = clipRequest,
        onFinished = {
            if (clipRequest?.key == it) {
                activeWordIndex = null
                clipRequest = null
            }
        },
        onError = {
            clipPlaybackError = it
            activeWordIndex = null
        },
    )

    fun toggleRecording() {
        clipRequest = null
        activeWordIndex = null
        clipPlaybackError = null
        if (state.isRecording) {
            recorder.stop()?.let(viewModel::analyze) ?: viewModel.recordingCancelled()
        } else {
            runCatching { recorder.start() }
                .onSuccess { viewModel.recordingStarted() }
                .onFailure { viewModel.recordingCancelled() }
        }
    }

    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) toggleRecording()
    }

    Scaffold(
        containerColor = PageBackground,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PageBackground),
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("‹", color = WarmText, fontSize = 36.sp)
                    }
                },
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "俄语朗读评测",
                            color = WarmText,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "语音学习 · 智能评分与逐词跟读",
                            color = WarmMuted,
                            style = WerusTypography.Metadata,
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            TargetSentenceCard(
                text = state.targetText,
                onTextChange = viewModel::setTargetText,
                difficulty = state.difficulty,
                onDifficultyChange = viewModel::setDifficulty,
                isGeneratingExample = state.isGeneratingExample,
                exampleChinese = state.exampleChinese,
                onGenerateExample = viewModel::generateExample,
            )

            RecordingCard(
                isRecording = state.isRecording,
                isAnalyzing = state.isAnalyzing,
                onClick = {
                    if (
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO,
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        toggleRecording()
                    } else {
                        permission.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
            )

            state.userRecordingPath?.let {
                UserRecordingPlayer(it)
            }

            state.error?.let {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            }

            state.result?.let { result ->
                if (!result.score_available) {
                    UnavailableResult(result.reason ?: "当前录音无法可靠评分")
                } else {
                    ScoreResultCard(
                        score = result.overall_score ?: 0.0,
                        level = result.proficiency_level,
                        summary = result.summary_feedback_zh,
                        words = result.words,
                        activeWordIndex = activeWordIndex,
                        canPlay = state.userRecordingPath != null,
                        playbackError = clipPlaybackError,
                        onWordClick = { index, word ->
                            playbackNonce += 1
                            activeWordIndex = index
                            clipPlaybackError = null
                            clipRequest = WordClipRequest(
                                key = playbackNonce,
                                startMs = (word.start_ms - 80).coerceAtLeast(0),
                                endMs = word.end_ms + 100,
                            )
                        },
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun TargetSentenceCard(
    text: String,
    onTextChange: (String) -> Unit,
    difficulty: String,
    onDifficultyChange: (String) -> Unit,
    isGeneratingExample: Boolean,
    exampleChinese: String?,
    onGenerateExample: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = WarmSurface),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, WarmOutline),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Surface(
                    color = WerusColors.Beige,
                    shape = RoundedCornerShape(9.dp),
                ) {
                    Text(
                        "请朗读以下俄语",
                        color = WarmMuted,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = WerusTypography.Subtitle,
                    )
                }
                OutlinedButton(
                    onClick = onGenerateExample,
                    enabled = !isGeneratingExample,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, WarmGold),
                ) {
                    if (isGeneratingExample) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = WarmGold,
                        )
                        Spacer(Modifier.width(7.dp))
                    }
                    Text(
                        if (isGeneratingExample) "生成中" else "✦ AI 例句",
                        color = WarmText,
                    )
                }
            }
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = WerusTypography.Title.copy(
                    color = WarmText,
                    fontWeight = FontWeight.SemiBold,
                ),
                minLines = 2,
                shape = RoundedCornerShape(14.dp),
                label = { Text("目标单词或例句") },
            )
            exampleChinese?.let {
                Text(
                    "AI 生成译文：$it",
                    color = WarmMuted,
                    style = WerusTypography.Body,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "beginner" to "初学",
                    "intermediate" to "进阶",
                    "advanced" to "高级",
                ).forEach { (value, label) ->
                    FilterChip(
                        selected = difficulty == value,
                        onClick = { onDifficultyChange(value) },
                        label = { Text(label) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RecordingCard(
    isRecording: Boolean,
    isAnalyzing: Boolean,
    onClick: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = WarmSurface),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, WarmOutline),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp, horizontal = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                modifier = Modifier
                    .size(118.dp)
                    .clickable(enabled = !isAnalyzing, onClick = onClick),
                shape = CircleShape,
                color = if (isRecording) WerusColors.Beige else WerusColors.Beige,
                border = BorderStroke(
                    2.dp,
                    if (isRecording) WerusColors.Red else WerusColors.Border,
                ),
                shadowElevation = 4.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isAnalyzing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(54.dp),
                            color = WarmGold,
                        )
                    } else {
                        MicrophoneMark(
                            color = if (isRecording) WerusColors.Red else WerusColors.Ink
                        )
                    }
                }
            }
            Text(
                when {
                    isAnalyzing -> "正在分析单词发音…"
                    isRecording -> "录音中…"
                    else -> "点击开始朗读"
                },
                color = WarmText,
                style = WerusTypography.Title,
                fontWeight = FontWeight.Bold,
            )
            Text(
                when {
                    isAnalyzing -> "Whisper 单词时间戳快速评分"
                    isRecording -> "再次点击结束并提交评测"
                    else -> "请靠近麦克风，用自然语速朗读"
                },
                color = WarmMuted,
                style = WerusTypography.Body,
            )
        }
    }
}

@Composable
private fun MicrophoneMark(color: Color) {
    Canvas(Modifier.size(56.dp)) {
        val center = size.width / 2
        drawRoundRect(
            color = color,
            topLeft = Offset(center - 10.dp.toPx(), 4.dp.toPx()),
            size = Size(20.dp.toPx(), 32.dp.toPx()),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx()),
        )
        drawArc(
            color = color,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(center - 16.dp.toPx(), 13.dp.toPx()),
            size = Size(32.dp.toPx(), 31.dp.toPx()),
            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
        )
        drawLine(
            color,
            Offset(center, 44.dp.toPx()),
            Offset(center, 51.dp.toPx()),
            strokeWidth = 4.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color,
            Offset(center - 10.dp.toPx(), 51.dp.toPx()),
            Offset(center + 10.dp.toPx(), 51.dp.toPx()),
            strokeWidth = 4.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun ScoreResultCard(
    score: Double,
    level: String?,
    summary: String,
    words: List<PronunciationWordDto>,
    activeWordIndex: Int?,
    canPlay: Boolean,
    playbackError: String?,
    onWordClick: (Int, PronunciationWordDto) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = WarmSurface),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, WarmOutline),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                ScoreRing(score)
                Column(Modifier.weight(1f)) {
                    Text(
                        "总分 ${score.toInt()}/100",
                        color = WarmText,
                        style = WerusTypography.Title,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        level ?: "朗读完成",
                        color = WarmGold,
                        style = WerusTypography.Subtitle,
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        summary,
                        color = WarmMuted,
                        style = WerusTypography.Body,
                    )
                }
            }

            HorizontalDivider(color = WarmOutline)
            Text(
                "▥  单词发音评测",
                color = WarmText,
                style = WerusTypography.Subtitle,
                fontWeight = FontWeight.Bold,
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                itemsIndexed(words) { index, word ->
                    WordScoreCard(
                        word = word,
                        active = activeWordIndex == index,
                        canPlay = canPlay && word.end_ms > word.start_ms,
                        onClick = { onWordClick(index, word) },
                    )
                }
            }
            ScoreLegend()
            Text(
                if (canPlay) "点击任一单词，可回放你朗读该词的录音片段。"
                else "录音文件不可用，暂时无法按词回放。",
                color = WarmMuted,
                style = WerusTypography.Caption,
            )
            playbackError?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = WerusTypography.Caption,
                )
            }
        }
    }
}

@Composable
private fun ScoreRing(score: Double) {
    Box(
        modifier = Modifier.size(104.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            progress = { (score / 100.0).toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxSize(),
            color = scorePalette(score).foreground,
            trackColor = WerusColors.Beige,
            strokeWidth = 9.dp,
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                score.toInt().toString(),
                color = WarmText,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
            )
            Text("/100", color = WarmMuted, style = WerusTypography.Metadata)
        }
    }
}

@Composable
private fun WordScoreCard(
    word: PronunciationWordDto,
    active: Boolean,
    canPlay: Boolean,
    onClick: () -> Unit,
) {
    val palette = scorePalette(word.score)
    Surface(
        modifier = Modifier
            .width(104.dp)
            .heightIn(min = 134.dp)
            .clickable(enabled = canPlay, onClick = onClick),
        color = if (active) palette.border.copy(alpha = 0.24f) else palette.background,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(if (active) 2.dp else 1.dp, palette.border),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 11.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(
                word.text,
                color = WarmText,
                maxLines = 1,
                textAlign = TextAlign.Center,
                style = WerusTypography.SerifBody,
            )
            Text(
                word.score?.toInt()?.toString() ?: "—",
                color = palette.foreground,
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold,
            )
            Surface(
                shape = CircleShape,
                color = Color.Transparent,
                border = BorderStroke(1.dp, palette.foreground),
            ) {
                Text(
                    if (active) "Ⅱ" else "▶",
                    color = palette.foreground,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun ScoreLegend() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .background(WerusColors.Beige, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        LegendItem("优秀 90–100", scorePalette(95.0).foreground)
        LegendItem("良好 75–89", scorePalette(82.0).foreground)
        LegendItem("一般 60–74", scorePalette(68.0).foreground)
        LegendItem("待提升 <60", scorePalette(45.0).foreground)
    }
}

@Composable
private fun LegendItem(text: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Text(text, color = WarmMuted, style = WerusTypography.Metadata)
    }
}

@Composable
private fun UnavailableResult(reason: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = WarmSurface),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, WarmOutline),
    ) {
        Column(
            Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "本次暂无法可靠评分",
                color = WarmText,
                style = WerusTypography.Title,
                fontWeight = FontWeight.Bold,
            )
            Text(reason, color = WarmMuted)
            Text("请在安静环境靠近麦克风重新朗读。", color = WarmMuted)
        }
    }
}

@Composable
private fun WordClipPlayback(
    recordingPath: String?,
    request: WordClipRequest?,
    onFinished: (Int) -> Unit,
    onError: (String) -> Unit,
) {
    LaunchedEffect(recordingPath, request?.key) {
        val clip = request ?: return@LaunchedEffect
        val recording = recordingPath?.let(::File)
        if (recording?.isFile != true) {
            onError("本次录音文件不可用，请重新朗读。")
            onFinished(clip.key)
            return@LaunchedEffect
        }
        var activePlayer: MediaPlayer? = null
        try {
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setVolume(1f, 1f)
                setDataSource(recording.absolutePath)
            }
            activePlayer = player

            val preparation = CompletableDeferred<Result<Unit>>()
            player.setOnPreparedListener {
                preparation.complete(Result.success(Unit))
            }
            player.setOnErrorListener { _, what, extra ->
                preparation.complete(
                    Result.failure(
                        IllegalStateException("MediaPlayer prepare error $what/$extra")
                    )
                )
                true
            }
            player.prepareAsync()
            val preparationResult = withTimeoutOrNull(5_000) {
                preparation.await()
            } ?: throw IllegalStateException("MediaPlayer prepare timeout")
            preparationResult.getOrThrow()

            val durationMs = player.duration.coerceAtLeast(0)
            val startMs = clip.startMs.coerceIn(0, durationMs)
            val endMs = clip.endMs.coerceIn(startMs, durationMs)
            val seekCompleted = CompletableDeferred<Unit>()
            player.setOnSeekCompleteListener {
                seekCompleted.complete(Unit)
            }
            player.seekTo(startMs.toLong(), MediaPlayer.SEEK_CLOSEST)
            withTimeoutOrNull(1_500) {
                seekCompleted.await()
            }
            player.setOnSeekCompleteListener(null)

            val playbackFailure = CompletableDeferred<Throwable>()
            player.setOnErrorListener { _, what, extra ->
                playbackFailure.complete(
                    IllegalStateException("MediaPlayer playback error $what/$extra")
                )
                true
            }
            player.start()
            val playbackDurationMs = (endMs - startMs).coerceAtLeast(180).toLong()
            withTimeoutOrNull(playbackDurationMs) {
                throw playbackFailure.await()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            onError("单词录音播放失败，请重新朗读后再试。")
        } finally {
            activePlayer?.let { player ->
                player.setOnPreparedListener(null)
                player.setOnSeekCompleteListener(null)
                player.setOnErrorListener(null)
                runCatching {
                    if (player.isPlaying) player.stop()
                }
                player.release()
            }
        }
        onFinished(clip.key)
    }
}

@Composable
private fun UserRecordingPlayer(recordingPath: String) {
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPrepared by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableIntStateOf(0) }
    var durationMs by remember { mutableIntStateOf(0) }
    var playbackError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(recordingPath) {
        val recording = File(recordingPath)
        val mediaPlayer = if (recording.isFile) {
            runCatching {
                MediaPlayer().apply {
                    setDataSource(recording.absolutePath)
                    setOnPreparedListener {
                        durationMs = it.duration.coerceAtLeast(0)
                        isPrepared = true
                    }
                    setOnCompletionListener {
                        isPlaying = false
                        positionMs = durationMs
                    }
                    setOnErrorListener { _, _, _ ->
                        isPlaying = false
                        playbackError = "录音回放失败"
                        true
                    }
                    prepareAsync()
                }
            }.getOrElse {
                playbackError = "无法打开本次录音"
                null
            }
        } else {
            playbackError = "本次录音文件已被清理"
            null
        }
        player = mediaPlayer
        onDispose {
            mediaPlayer?.release()
            if (player === mediaPlayer) player = null
        }
    }

    LaunchedEffect(isPlaying, player) {
        while (isPlaying) {
            positionMs = player?.currentPosition?.coerceAtLeast(0) ?: 0
            delay(150)
        }
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = WerusColors.Beige,
        border = BorderStroke(1.dp, WarmOutline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                FilledTonalButton(
                    enabled = isPrepared,
                    onClick = {
                        val active = player ?: return@FilledTonalButton
                        if (active.isPlaying) {
                            active.pause()
                            positionMs = active.currentPosition
                            isPlaying = false
                        } else {
                            if (positionMs >= durationMs && durationMs > 0) {
                                active.seekTo(0)
                                positionMs = 0
                            }
                            active.start()
                            isPlaying = true
                        }
                    },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = WarmGoldSoft,
                        contentColor = WarmText,
                    ),
                ) {
                    Text(if (isPlaying) "暂停整句" else "回放整句")
                }
                Slider(
                    value = positionMs.coerceAtMost(durationMs).toFloat(),
                    onValueChange = {
                        positionMs = it.toInt()
                        player?.seekTo(positionMs)
                    },
                    valueRange = 0f..durationMs.coerceAtLeast(1).toFloat(),
                    enabled = isPrepared,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${formatMillis(positionMs)} / ${formatMillis(durationMs)}",
                    color = WarmMuted,
                    style = WerusTypography.Metadata,
                )
            }
            playbackError?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private fun formatMillis(value: Int): String {
    val seconds = value.coerceAtLeast(0) / 1000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

private fun scorePalette(score: Double?): ScorePalette = when {
    score == null -> ScorePalette(
        WerusColors.InkFaint,
        WerusColors.BeigeMuted,
        WerusColors.Border,
    )
    score >= 90 -> ScorePalette(
        WerusColors.Success,
        WerusColors.ScoreExcellentPaper,
        WerusColors.ScoreExcellentBorder,
    )
    score >= 75 -> ScorePalette(
        WerusColors.GoldDark,
        WerusColors.ScoreGoodPaper,
        WerusColors.ScoreGoodBorder,
    )
    score >= 60 -> ScorePalette(
        WerusColors.Warning,
        WerusColors.ScoreFairPaper,
        WerusColors.ScoreFairBorder,
    )
    else -> ScorePalette(
        WerusColors.Error,
        WerusColors.RedSoft,
        WerusColors.ScoreNeedsWorkBorder,
    )
}

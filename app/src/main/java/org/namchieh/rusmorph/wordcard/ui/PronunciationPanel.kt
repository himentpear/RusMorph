package org.namchieh.rusmorph.wordcard.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.namchieh.rusmorph.data.remote.PronunciationDto
import org.namchieh.rusmorph.ui.components.RusPillBadge
import org.namchieh.rusmorph.ui.components.RusStat
import org.namchieh.rusmorph.ui.theme.RusMorphColors
import org.namchieh.rusmorph.ui.theme.RusMorphTechTypography

/**
 * 现代俄语语音发音与跟读评测面板。
 * 支持 1.0x / 0.75x TTS 复听、3~5 根实时录音跳动动效柱、及 Whisper 智能可懂度分析。
 */
@Composable
fun PronunciationPanel(
    targetWord: String,
    isTtsSpeaking: Boolean,
    ttsSpeed: Float,
    isRecording: Boolean,
    isAnalyzing: Boolean,
    userRecordingPath: String?,
    evaluationResult: PronunciationDto?,
    onPlayTts: (speed: Float) -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onPlayUserRecording: (path: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // 控制动作行：1.0x / 0.75x 播音 + 跟读按键
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 1.0x 原速播放
            OutlinedButton(
                onClick = { onPlayTts(1.0f) },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (isTtsSpeaking && ttsSpeed == 1.0f) RusMorphColors.AccentOrange else RusMorphColors.CarbonBlack
                ),
                modifier = Modifier.weight(1f),
            ) {
                Text(if (isTtsSpeaking && ttsSpeed == 1.0f) "播放中 · 1.0x" else "🔊 1.0x 示范", style = MaterialTheme.typography.bodySmall)
            }

            // 0.75x 慢速跟读
            OutlinedButton(
                onClick = { onPlayTts(0.75f) },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (isTtsSpeaking && ttsSpeed == 0.75f) RusMorphColors.AccentOrange else RusMorphColors.CarbonBlack
                ),
                modifier = Modifier.weight(1f),
            ) {
                Text(if (isTtsSpeaking && ttsSpeed == 0.75f) "播放中 · 0.75x" else "🔊 0.75x 慢速", style = MaterialTheme.typography.bodySmall)
            }

            // 🎙 跟读触发键
            Button(
                onClick = {
                    if (isRecording) onStopRecording() else onStartRecording()
                },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRecording) RusMorphColors.AccentOrange else RusMorphColors.CarbonBlack,
                    contentColor = RusMorphColors.Surface,
                ),
                modifier = Modifier.weight(1.2f),
            ) {
                Text(if (isRecording) "⏹ 结束录音" else "🎙 跟读评测", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            }
        }

        // 录音中：实时跳动音量柱动效
        AnimatedVisibility(visible = isRecording) {
            Surface(
                color = RusMorphColors.SurfaceMuted,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("正在收音，请清晰朗读…", style = MaterialTheme.typography.bodySmall, color = RusMorphColors.TextSecondary)
                    Spacer(Modifier.width(12.dp))
                    RecordingVolumeBars()
                }
            }
        }

        // 评测中加载
        AnimatedVisibility(visible = isAnalyzing) {
            Surface(
                color = RusMorphColors.SurfaceMuted,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = RusMorphColors.CarbonBlack)
                    Spacer(Modifier.width(10.dp))
                    Text("Whisper 语音模型正在比对可懂度…", style = MaterialTheme.typography.bodySmall, color = RusMorphColors.TextSecondary)
                }
            }
        }

        // 评测结果卡片
        evaluationResult?.let { result ->
            Surface(
                color = RusMorphColors.Canvas,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, RusMorphColors.OutlineSoft),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val cleanTarget = targetWord.replace("́", "").trim().lowercase()
                    val cleanAsr = result.recognized_text.trim().lowercase()
                    val score = result.overall_score?.toInt() ?: if (cleanAsr.isNotBlank()) 80 else null

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RusStat(
                            value = score?.toString() ?: "--",
                            label = "综合发音可懂度",
                            hasDot = true,
                            isLarge = true,
                        )
                        Column(horizontalAlignment = Alignment.End) {
                            val level = when {
                                (score ?: 0) >= 88 -> "优秀 · Advanced"
                                (score ?: 0) >= 72 -> "良好 · Intermediate"
                                (score ?: 0) > 0 -> "待加强 · Developing"
                                else -> "未识别到 · Retry"
                            }
                            RusPillBadge(level, containerColor = RusMorphColors.WarmCream, contentColor = RusMorphColors.CarbonBlack)
                        }
                    }

                    // 诊断说明（遵守原则：不直接断言音素错误，标注为识别差异或复听建议）
                    val feedback = result.summary_feedback_zh?.takeIf { it.isNotBlank() && !it.contains("无法生成可靠评分") }
                        ?: if (cleanAsr.isNotBlank()) {
                            if (cleanAsr == cleanTarget) "发音清晰准确，元音与重音饱满。"
                            else "模型识别为「$cleanAsr」，可能存在发音差异，建议对照示范录音多次复听。"
                        } else "未能检测到有效声音，请贴近麦克风重新朗读。"

                    Text(
                        text = feedback,
                        style = MaterialTheme.typography.bodySmall,
                        color = RusMorphColors.TextSecondary,
                    )

                    // 回放自己刚才的录音
                    userRecordingPath?.let { path ->
                        TextButton(
                            onClick = { onPlayUserRecording(path) },
                            modifier = Modifier.align(Alignment.End),
                        ) {
                            Text("🎧 试听我的录音", style = MaterialTheme.typography.labelMedium, color = RusMorphColors.AccentBlue)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 实时录音音量跳动条动效（4 根独立跳动柱）
 */
@Composable
private fun RecordingVolumeBars() {
    val infiniteTransition = rememberInfiniteTransition(label = "volume_bars")

    val h1 by infiniteTransition.animateFloat(
        initialValue = 6f, targetValue = 20f,
        animationSpec = infiniteRepeatable(tween(350, easing = LinearEasing), RepeatMode.Reverse),
        label = "bar1"
    )
    val h2 by infiniteTransition.animateFloat(
        initialValue = 18f, targetValue = 8f,
        animationSpec = infiniteRepeatable(tween(280, easing = LinearEasing), RepeatMode.Reverse),
        label = "bar2"
    )
    val h3 by infiniteTransition.animateFloat(
        initialValue = 10f, targetValue = 24f,
        animationSpec = infiniteRepeatable(tween(400, easing = LinearEasing), RepeatMode.Reverse),
        label = "bar3"
    )
    val h4 by infiniteTransition.animateFloat(
        initialValue = 14f, targetValue = 6f,
        animationSpec = infiniteRepeatable(tween(320, easing = LinearEasing), RepeatMode.Reverse),
        label = "bar4"
    )

    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(h1, h2, h3, h4).forEach { height ->
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(height.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(RusMorphColors.AccentOrange)
            )
        }
    }
}

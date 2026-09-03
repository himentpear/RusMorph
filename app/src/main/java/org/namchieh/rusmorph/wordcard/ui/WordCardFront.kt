package org.namchieh.rusmorph.wordcard.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.namchieh.rusmorph.ui.components.RusPillBadge
import org.namchieh.rusmorph.ui.theme.RusMorphColors
import org.namchieh.rusmorph.ui.theme.RusMorphTechTypography
import org.namchieh.rusmorph.wordcard.model.CardMode
import org.namchieh.rusmorph.wordcard.model.Lexeme
import org.namchieh.rusmorph.wordcard.model.WordForm

/**
 * 单词卡牌正面 (Front)。
 * 严格遵循第七章设计准则：正面极简、大留白，禁止堆砌变格表/IPA/长文本。
 */
@Composable
fun WordCardFront(
    lexeme: Lexeme,
    form: WordForm,
    cardMode: CardMode,
    isFavorite: Boolean,
    isSpeaking: Boolean,
    onToggleFavorite: () -> Unit,
    onPlayAudio: () -> Unit,
    onFollowAlong: () -> Unit,
    onFlip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = RusMorphColors.Surface,
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 4.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, RusMorphColors.OutlineSoft),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 360.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // 顶栏：词性 · CEFR 与 收藏按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    val posLabel = lexeme.basic.partOfSpeech
                    val genderLabel = lexeme.basic.gender?.labelZh
                    val tagText = listOfNotNull(posLabel, genderLabel).joinToString(" · ")
                    RusPillBadge(tagText.ifBlank { "词汇" }, containerColor = RusMorphColors.Canvas, contentColor = RusMorphColors.CarbonBlack)

                    lexeme.basic.cefr?.let { cefr ->
                        RusPillBadge(cefr, containerColor = RusMorphColors.WarmCream, contentColor = RusMorphColors.CarbonBlack)
                    }

                    if (cardMode != CardMode.RECOGNITION) {
                        RusPillBadge(cardMode.labelZh, containerColor = RusMorphColors.SurfaceMuted, contentColor = RusMorphColors.AccentBlue)
                    }
                }

                // 收藏星标
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.size(36.dp),
                ) {
                    Text(
                        text = if (isFavorite) "★" else "☆",
                        fontSize = 22.sp,
                        color = if (isFavorite) RusMorphColors.AccentOrange else RusMorphColors.TextTertiary,
                    )
                }
            }

            // 中间核心区域：根据卡片模式呈现对应核心认知任务
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                when (cardMode) {
                    CardMode.RECALL -> {
                        // 中文回忆模式：正面显示中文释义，促使回忆俄语
                        Text(
                            text = lexeme.basic.primaryTranslation.ifBlank { "请回忆此词俄语原形" },
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = RusMorphColors.CarbonBlack,
                        )
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = "思考俄语原形与重音位置后翻面",
                            style = MaterialTheme.typography.bodySmall,
                            color = RusMorphColors.TextTertiary,
                        )
                    }
                    CardMode.STRESS -> {
                        // 重音训练模式：隐藏重音符号，只显示干净字母
                        val unstressedWord = (if (form.isLemma) lexeme.lemma else form.inputForm).replace("́", "")
                        Text(
                            text = unstressedWord,
                            style = MaterialTheme.typography.displaySmall,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            color = RusMorphColors.CarbonBlack,
                        )
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = lexeme.basic.primaryTranslation,
                            style = MaterialTheme.typography.titleMedium,
                            color = RusMorphColors.TextSecondary,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "标出正确重音音节后翻面核验",
                            style = RusMorphTechTypography.MicroPill,
                            color = RusMorphColors.AccentBlue,
                        )
                    }
                    CardMode.MORPHOLOGY -> {
                        // 词形辨析模式：显示当前具体变格形式，提示思考语法格位
                        Text(
                            text = form.displayForm,
                            style = MaterialTheme.typography.displaySmall,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            color = RusMorphColors.CarbonBlack,
                        )
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = "其原形是？当前充当什么语法格位 / 时态？",
                            style = MaterialTheme.typography.bodyMedium,
                            color = RusMorphColors.AccentOrange,
                            textAlign = TextAlign.Center,
                        )
                    }
                    else -> {
                        // 标准认读 (RECOGNITION) 或语音评测模式 (PRONUNCIATION)
                        val wordText = if (form.isLemma) lexeme.displayForm else form.displayForm
                        Text(
                            text = wordText,
                            style = MaterialTheme.typography.displaySmall,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            color = RusMorphColors.CarbonBlack,
                        )

                        Spacer(Modifier.height(16.dp))

                        val meaningText = lexeme.basic.primaryTranslation
                        if (meaningText.isNotBlank()) {
                            Text(
                                text = meaningText,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Normal,
                                color = RusMorphColors.TextSecondary,
                                textAlign = TextAlign.Center,
                            )
                        }

                        if (!form.isLemma) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "原形: ${lexeme.lemma}",
                                style = RusMorphTechTypography.MicroPill,
                                color = RusMorphColors.TextTertiary,
                            )
                        }
                    }
                }
            }

            // 底部快捷操作行：发音 🔊 · 跟读 🎙 · 翻面 ↻
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 🔊 发音
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable(onClick = onPlayAudio).padding(8.dp)
                ) {
                    Text(if (isSpeaking) "🔊" else "🔈", fontSize = 24.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("发音", style = MaterialTheme.typography.labelSmall, color = RusMorphColors.TextSecondary)
                }

                // 🎙 跟读
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable(onClick = onFollowAlong).padding(8.dp)
                ) {
                    Text("🎙", fontSize = 24.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("跟读", style = MaterialTheme.typography.labelSmall, color = RusMorphColors.TextSecondary)
                }

                // ↻ 翻面
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable(onClick = onFlip).padding(8.dp)
                ) {
                    Text("↻", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = RusMorphColors.AccentBlue)
                    Spacer(Modifier.height(4.dp))
                    Text("翻面", style = MaterialTheme.typography.labelSmall, color = RusMorphColors.AccentBlue)
                }
            }
        }
    }
}

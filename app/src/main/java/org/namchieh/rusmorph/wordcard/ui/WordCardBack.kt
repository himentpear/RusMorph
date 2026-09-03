package org.namchieh.rusmorph.wordcard.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.namchieh.rusmorph.ui.components.RusPillBadge
import org.namchieh.rusmorph.ui.theme.RusMorphColors
import org.namchieh.rusmorph.ui.theme.RusMorphTechTypography
import org.namchieh.rusmorph.wordcard.model.*

/**
 * 单词卡牌背面 (Back)。
 * 严格遵循第八章设计准则：层次化信息展开结构，包括形态概览、当前形式分析、重音规律、例句、易错警示与复习动作。
 */
@Composable
fun WordCardBack(
    lexeme: Lexeme,
    form: WordForm,
    reviewState: ReviewState,
    onReviewResult: (ReviewResult) -> Unit,
    onOpenAiWorkspace: () -> Unit,
    onPlayAudio: () -> Unit,
    onFlip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isMorphologyExpanded by remember { mutableStateOf(false) }
    var isMoreExamplesExpanded by remember { mutableStateOf(false) }

    Surface(
        color = RusMorphColors.Surface,
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 4.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, RusMorphColors.OutlineSoft),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 1. 顶部标头行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = lexeme.displayForm,
                            style = MaterialTheme.typography.headlineMedium,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            color = RusMorphColors.CarbonBlack,
                        )
                        IconButton(onClick = onPlayAudio, modifier = Modifier.size(28.dp)) {
                            Text("🔊", fontSize = 16.sp)
                        }
                    }

                    val posLabel = lexeme.basic.partOfSpeech
                    val genderLabel = lexeme.basic.gender?.labelZh
                    val animacyLabel = when (lexeme.basic.animacy) {
                        true -> "有生命"
                        false -> "无生命"
                        null -> null
                    }
                    val tags = listOfNotNull(posLabel, genderLabel, animacyLabel).joinToString(" · ")
                    Text(tags, style = MaterialTheme.typography.bodySmall, color = RusMorphColors.TextSecondary)
                }

                // 翻面按键
                TextButton(onClick = onFlip) {
                    Text("↻ 正面", style = MaterialTheme.typography.labelSmall, color = RusMorphColors.AccentBlue)
                }
            }

            HorizontalDivider(color = RusMorphColors.OutlineSoft, thickness = 0.8.dp)

            // 2. 当前形式分析 (Current Form Analysis)
            Surface(
                color = RusMorphColors.SurfaceMuted,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("当前词形解析", style = RusMorphTechTypography.MicroPill, color = RusMorphColors.TextTertiary)
                    Text(
                        text = if (form.isLemma) "原形形式 (${form.displayForm})" else "检索词形: ${form.displayForm}",
                        style = MaterialTheme.typography.titleSmall,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.SemiBold,
                        color = RusMorphColors.CarbonBlack,
                    )

                    if (form.analyses.isNotEmpty()) {
                        form.analyses.forEachIndexed { index, analysis ->
                            val desc = analysis.formatShortDescription()
                            Text(
                                text = if (form.analyses.size > 1) "${index + 1}. $desc" else desc,
                                style = MaterialTheme.typography.bodySmall,
                                color = RusMorphColors.AccentBlue,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    } else if (form.explanationZh.isNotBlank()) {
                        Text(form.explanationZh, style = MaterialTheme.typography.bodySmall, color = RusMorphColors.TextSecondary)
                    }
                }
            }

            // 3. 形态概览 (Morphology Summary)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("形态特征", style = RusMorphTechTypography.MicroPill, color = RusMorphColors.TextTertiary)
                when (val m = lexeme.morphology) {
                    is Lexeme.MorphologyInfo.Noun -> {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            if (m.declensionType.isNotBlank()) Text("变格法: ${m.declensionType}", style = MaterialTheme.typography.bodySmall)
                            if (m.stem.isNotBlank()) Text("词干: ${m.stem}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    is Lexeme.MorphologyInfo.Verb -> {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            if (m.conjugationType.isNotBlank()) Text("变位法: ${m.conjugationType}", style = MaterialTheme.typography.bodySmall)
                            if (m.aspectPair != null) Text("对应体: ${m.aspectPair}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    else -> {}
                }

                // 重音特征
                if (lexeme.pronunciation.stressPattern.isNotBlank()) {
                    Text("重音规律: ${lexeme.pronunciation.stressPattern}", style = MaterialTheme.typography.bodySmall, color = RusMorphColors.TextSecondary)
                }
            }

            // 展开式完整六格 / 变位面板
            OutlinedButton(
                onClick = { isMorphologyExpanded = !isMorphologyExpanded },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                val buttonLabel = when (lexeme.morphology) {
                    is Lexeme.MorphologyInfo.Noun -> if (isMorphologyExpanded) "收起六格变格表 ▲" else "查看完整六格表 ▼"
                    is Lexeme.MorphologyInfo.Verb -> if (isMorphologyExpanded) "收起动词变位表 ▲" else "查看完整动词变位表 ▼"
                    else -> if (isMorphologyExpanded) "收起形态表 ▲" else "查看完整形态数据 ▼"
                }
                Text(buttonLabel, style = MaterialTheme.typography.bodySmall, color = RusMorphColors.AccentBlue)
            }

            MorphologyPanel(morphology = lexeme.morphology, isExpanded = isMorphologyExpanded)

            // 4. 典型例句 (Example)
            if (lexeme.usage.exampleRu.isNotBlank()) {
                Surface(
                    color = RusMorphColors.Canvas,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("典型例句", style = RusMorphTechTypography.MicroPill, color = RusMorphColors.TextTertiary)
                        Text(lexeme.usage.exampleRu, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Serif, color = RusMorphColors.CarbonBlack)
                        if (lexeme.usage.exampleZh.isNotBlank()) {
                            Text(lexeme.usage.exampleZh, style = MaterialTheme.typography.bodySmall, color = RusMorphColors.TextSecondary)
                        }
                    }
                }
            }

            // 5. 易错警示 (Common Errors)
            if (lexeme.learning.commonErrors.isNotEmpty()) {
                Surface(
                    color = RusMorphColors.WarmCream.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("易错点警示", style = RusMorphTechTypography.MicroPill, color = RusMorphColors.AccentOrange)
                        lexeme.learning.commonErrors.forEach { err ->
                            Text("• $err", style = MaterialTheme.typography.bodySmall, color = RusMorphColors.CarbonBlack)
                        }
                    }
                }
            }

            // 6. 展开式行动按键行：AI 分析
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onOpenAiWorkspace) {
                    Text("⚡ AI 深度形态工作区 →", style = MaterialTheme.typography.labelSmall, color = RusMorphColors.AccentBlue)
                }
            }

            HorizontalDivider(color = RusMorphColors.OutlineSoft, thickness = 0.8.dp)

            // 7. 复习动作提交行 (Review Actions)
            ReviewActionBar(onReviewResult = onReviewResult)
        }
    }
}

/**
 * 底部复习评级快捷按键栏
 */
@Composable
fun ReviewActionBar(
    onReviewResult: (ReviewResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Again (不熟悉)
        Button(
            onClick = { onReviewResult(ReviewResult.AGAIN) },
            colors = ButtonDefaults.buttonColors(
                containerColor = RusMorphColors.AccentOrange.copy(alpha = 0.15f),
                contentColor = RusMorphColors.AccentOrange,
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.weight(1f),
        ) {
            Text("← 不熟悉", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
        }

        // Hard (模糊)
        Button(
            onClick = { onReviewResult(ReviewResult.HARD) },
            colors = ButtonDefaults.buttonColors(
                containerColor = RusMorphColors.SurfaceMuted,
                contentColor = RusMorphColors.CarbonBlack,
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.weight(1f),
        ) {
            Text("模糊", style = MaterialTheme.typography.bodySmall)
        }

        // Good (已掌握)
        Button(
            onClick = { onReviewResult(ReviewResult.GOOD) },
            colors = ButtonDefaults.buttonColors(
                containerColor = RusMorphColors.AccentGreen.copy(alpha = 0.15f),
                contentColor = RusMorphColors.AccentGreen,
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.weight(1f),
        ) {
            Text("已掌握 →", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
        }
    }
}

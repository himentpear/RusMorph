package org.namchieh.rusmorph.wordcard.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
    onToggleEnrollment: (() -> Unit)? = null,
    onNavigateToRule: ((category: String, ruleId: String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var isMorphologyExpanded by remember { mutableStateOf(false) }
    var isMoreExamplesExpanded by remember { mutableStateOf(false) }

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
                .fillMaxWidth()
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
                Text("形态特征与分类", style = RusMorphTechTypography.MicroPill, color = RusMorphColors.TextTertiary)
                when (val m = lexeme.morphology) {
                    is Lexeme.MorphologyInfo.Noun -> {
                        val (ruleId, ruleLabel) = determineNounRuleTarget(lexeme.lemma, lexeme.basic.gender)
                        val animacyText = when (lexeme.basic.animacy) {
                            true -> "👤 有生命"
                            false -> "📦 无生命"
                            null -> null
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("类型: $ruleLabel", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                if (animacyText != null) {
                                    Text("· $animacyText", style = RusMorphTechTypography.MicroPill, color = RusMorphColors.TextSecondary)
                                }
                            }
                            if (m.declensionType.isNotBlank()) {
                                Text(m.declensionType, style = RusMorphTechTypography.MicroPill, color = RusMorphColors.TextTertiary)
                            }
                        }
                        if (onNavigateToRule != null) {
                            Text(
                                text = "查看 $ruleLabel 变格规则 →",
                                style = RusMorphTechTypography.MicroPill,
                                color = RusMorphColors.AccentOrange,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { onNavigateToRule("NOUN", ruleId) }.padding(vertical = 2.dp),
                            )
                        }
                    }
                    is Lexeme.MorphologyInfo.Verb -> {
                        val isSecond = m.conjugationType.contains("2") || m.conjugationType.contains("二") || lexeme.lemma.trim().lowercase().replace("\u0301", "").endsWith("ить")
                        val verbRuleId = if (isSecond) "verb_second" else "verb_first"
                        val verbRuleLabel = if (isSecond) "第二变位" else "第一变位"
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("变位: $verbRuleLabel", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            if (m.aspectPair != null) {
                                Text("对应体: → ${m.aspectPair}", style = RusMorphTechTypography.MicroPill, color = RusMorphColors.AccentBlue)
                            }
                        }
                        if (onNavigateToRule != null) {
                            Text(
                                text = "查看 $verbRuleLabel 规则 →",
                                style = RusMorphTechTypography.MicroPill,
                                color = RusMorphColors.AccentOrange,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { onNavigateToRule("VERB", verbRuleId) }.padding(vertical = 2.dp),
                            )
                        }
                    }
                    is Lexeme.MorphologyInfo.Adjective -> {
                        if (onNavigateToRule != null) {
                            Text(
                                text = "查看形容词变格规则 →",
                                style = RusMorphTechTypography.MicroPill,
                                color = RusMorphColors.AccentOrange,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { onNavigateToRule("ADJECTIVE", "adj_hard") }.padding(vertical = 2.dp),
                            )
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

            // 6.5 艾宾浩斯复习计划与留存率状态
            Surface(
                color = if (reviewState.isEnrolled) RusMorphColors.WarmCream.copy(alpha = 0.5f) else RusMorphColors.SurfaceMuted,
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (reviewState.isEnrolled) RusMorphColors.AccentOrange.copy(alpha = 0.4f) else RusMorphColors.OutlineSoft),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (reviewState.isEnrolled) "✓ 艾宾浩斯复习中" else "未收纳至复习计划",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (reviewState.isEnrolled) RusMorphColors.CarbonBlack else RusMorphColors.TextSecondary,
                            )
                            if (reviewState.isEnrolled) {
                                val (pct, badgeText) = reviewState.retentionBadge
                                Text("留存率 $pct · $badgeText", style = RusMorphTechTypography.MicroPill, color = RusMorphColors.AccentOrange)
                            }
                        }
                        if (reviewState.reviewCount > 0) {
                            Text(
                                text = "已复习 ${reviewState.reviewCount} 次 · 掌握度 ${reviewState.mastery}%",
                                style = RusMorphTechTypography.MicroPill,
                                color = RusMorphColors.TextTertiary,
                            )
                        }
                    }

                    if (onToggleEnrollment != null) {
                        TextButton(
                            onClick = onToggleEnrollment,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = if (reviewState.isEnrolled) "移出计划" else "+ 加入复习",
                                style = RusMorphTechTypography.MicroPill,
                                fontWeight = FontWeight.Bold,
                                color = if (reviewState.isEnrolled) RusMorphColors.TextSecondary else RusMorphColors.AccentOrange,
                            )
                        }
                    }
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

/**
 * 根据词尾与性属匹配规则表中的名词分类 ID 与标签
 */
private fun determineNounRuleTarget(lemma: String, gender: Gender?): Pair<String, String> {
    val clean = lemma.trim().lowercase().replace("\u0301", "")
    return when {
        clean.endsWith("ия") -> "noun_fem_iya" to "-ия 型阴性"
        clean.endsWith("ие") -> "noun_neut_ie" to "-ие 型中性"
        clean.endsWith("а") -> {
            if (gender == Gender.MASCULINE) {
                "noun_fem_a" to "-а 型阳性"
            } else {
                "noun_fem_a" to "-а 型阴性"
            }
        }
        clean.endsWith("я") -> {
            if (gender == Gender.MASCULINE) {
                "noun_fem_ya" to "-я 型阳性"
            } else {
                "noun_fem_ya" to "-я 型阴性"
            }
        }
        clean.endsWith("о") -> "noun_neut_o" to "-о 型中性"
        clean.endsWith("е") || clean.endsWith("ё") -> "noun_neut_e" to "-е 型中性"
        clean.endsWith("й") -> "noun_masc_j" to "-й 型阳性"
        clean.endsWith("ь") -> {
            if (gender == Gender.MASCULINE) {
                "noun_masc_soft" to "-ь 阳性"
            } else {
                "noun_fem_soft" to "-ь 阴性"
            }
        }
        else -> "noun_masc_hard" to "辅音结尾阳性"
    }
}

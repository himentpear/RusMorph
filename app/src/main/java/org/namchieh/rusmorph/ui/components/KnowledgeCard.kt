package org.namchieh.rusmorph.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.namchieh.rusmorph.domain.textbook.KnowledgeProgress
import org.namchieh.rusmorph.domain.textbook.KnowledgeProgressStatus
import org.namchieh.rusmorph.domain.textbook.KnowledgeType
import org.namchieh.rusmorph.domain.textbook.SentenceKnowledge
import org.namchieh.rusmorph.ui.design.*

/**
 * Werus 教材批注卡 (Textbook Annotation Card)
 *
 * 具有古典俄语学术教材装订气质：
 * - 左侧垂直书脊装订色条 (Accent Spine)，颜色对应知识点类型 (语法/短语/句型/词汇)
 * - 纯正米白书纸底色 (Paper / Canvas)
 * - 衬线标题 (Serif) 与正统学术排版
 * - 规范掌握度认知步进器
 */
@Composable
fun KnowledgeCard(
    knowledge: SentenceKnowledge,
    modifier: Modifier = Modifier,
    progress: KnowledgeProgress? = null,
    onProgressChange: ((KnowledgeProgressStatus) -> Unit)? = null,
    onWordLookup: ((String) -> Unit)? = null,
    onAddReview: (() -> Unit)? = null,
    onAskAi: ((String) -> Unit)? = null,
) {
    val currentStatus = progress?.status ?: KnowledgeProgressStatus.SEEN
    val typeTheme = getKnowledgeTypeTheme(knowledge.type)

    WerusCard(
        modifier = modifier.fillMaxWidth(),
        containerColor = WerusColors.Paper,
        border = BorderStroke(1.dp, WerusColors.Border),
        accentColor = typeTheme.contentColor,
        contentPadding = 16.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 1. 顶部标头：类型徽章与当前掌握状态
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 教材类型徽章
                WerusSurface(
                    shape = RoundedCornerShape(6.dp),
                    color = typeTheme.containerColor,
                    contentColor = typeTheme.contentColor,
                    border = BorderStroke(1.dp, typeTheme.contentColor.copy(alpha = 0.25f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(typeTheme.contentColor),
                        )
                        Text(
                            text = typeTheme.badgeText,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 0.6.sp,
                        )
                    }
                }

                // 认知掌握进度状态标签
                WerusSurface(
                    shape = WerusPillShape,
                    color = when (currentStatus) {
                        KnowledgeProgressStatus.MASTERED -> WerusColors.ScoreExcellentPaper
                        KnowledgeProgressStatus.PRACTICED -> WerusColors.PhrasePaper
                        KnowledgeProgressStatus.UNDERSTOOD -> WerusColors.RedSoft
                        KnowledgeProgressStatus.SEEN -> WerusColors.BeigeMuted
                    },
                    border = BorderStroke(
                        1.dp,
                        when (currentStatus) {
                            KnowledgeProgressStatus.MASTERED -> WerusColors.ScoreExcellentBorder
                            KnowledgeProgressStatus.PRACTICED -> WerusColors.ScoreGoodBorder
                            KnowledgeProgressStatus.UNDERSTOOD -> WerusColors.ScoreNeedsWorkBorder
                            KnowledgeProgressStatus.SEEN -> WerusColors.Border
                        },
                    ),
                ) {
                    Text(
                        text = currentStatus.labelZh,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                        style = WerusTypography.labelSmall,
                        color = when (currentStatus) {
                            KnowledgeProgressStatus.MASTERED -> WerusColors.Success
                            KnowledgeProgressStatus.PRACTICED -> WerusColors.GoldDark
                            KnowledgeProgressStatus.UNDERSTOOD -> WerusColors.RedDark
                            KnowledgeProgressStatus.SEEN -> WerusColors.InkMuted
                        },
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            // 2. 核心标题区 (Serif 学术教材字体)
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                val title = knowledge.label ?: knowledge.text
                Text(
                    text = title,
                    style = WerusTypography.titleLarge,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    color = WerusColors.Ink,
                    lineHeight = 26.sp,
                )
                // 若标题与原文片段不同，则辅助呈现原文划定片段
                if (knowledge.label != null && knowledge.label != knowledge.text) {
                    Text(
                        text = "原句片段：«${knowledge.text}»",
                        style = WerusTypography.bodyMedium,
                        color = WerusColors.InkMuted,
                        fontStyle = FontStyle.Italic,
                    )
                }
            }

            // 3. 详细解释区 (纸张嵌入卡)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "教材批注与解析",
                    style = WerusTypography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = WerusColors.InkMuted,
                )
                WerusSurface(
                    shape = WerusShapes.small,
                    color = WerusColors.Canvas,
                    border = BorderStroke(1.dp, WerusColors.BorderSoft),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = knowledge.explanation?.ifBlank { null } ?: "该知识点暂无额外文字解释",
                        modifier = Modifier.padding(11.dp),
                        style = WerusTypography.bodyMedium,
                        color = WerusColors.Ink,
                        lineHeight = 21.sp,
                    )
                }
            }

            // 4. 教材例句区 (若有)
            if (!knowledge.example.isNullOrBlank()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "典型例句",
                        style = WerusTypography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = WerusColors.InkMuted,
                    )
                    WerusSurface(
                        shape = WerusShapes.small,
                        color = WerusColors.Beige.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, WerusColors.Border),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = knowledge.example,
                            modifier = Modifier.padding(11.dp),
                            style = WerusTypography.bodyMedium,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Medium,
                            color = WerusColors.Ink,
                            lineHeight = 21.sp,
                        )
                    }
                }
            }

            // 5. 认知掌握进度交互流转
            if (onProgressChange != null) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "研习掌握度",
                        style = WerusTypography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = WerusColors.InkMuted,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        KnowledgeProgressStatus.values().forEach { status ->
                            val isCurrent = status == currentStatus
                            val isPassed = status.rank <= currentStatus.rank
                            WerusSurface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onProgressChange(status) },
                                shape = WerusShapes.small,
                                color = when {
                                    isCurrent -> WerusColors.Red
                                    isPassed -> WerusColors.Beige
                                    else -> WerusColors.Canvas
                                },
                                border = BorderStroke(
                                    1.dp,
                                    if (isCurrent) WerusColors.RedDark else WerusColors.Border,
                                ),
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 7.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = status.labelZh,
                                        fontSize = 11.sp,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isCurrent) WerusColors.OnDark else WerusColors.Ink,
                                    )
                                }
                            }
                        }
                    }

                    // 步进引导按钮
                    when (currentStatus) {
                        KnowledgeProgressStatus.SEEN -> {
                            WerusButton(
                                text = "标为已理解 ✓",
                                onClick = { onProgressChange(KnowledgeProgressStatus.UNDERSTOOD) },
                                modifier = Modifier.fillMaxWidth(),
                                style = WerusButtonStyle.Secondary,
                            )
                        }
                        KnowledgeProgressStatus.UNDERSTOOD -> {
                            WerusButton(
                                text = "标为已练习 ✎",
                                onClick = { onProgressChange(KnowledgeProgressStatus.PRACTICED) },
                                modifier = Modifier.fillMaxWidth(),
                                style = WerusButtonStyle.Secondary,
                            )
                        }
                        KnowledgeProgressStatus.PRACTICED -> {
                            WerusButton(
                                text = "标为已掌握 ★",
                                onClick = { onProgressChange(KnowledgeProgressStatus.MASTERED) },
                                modifier = Modifier.fillMaxWidth(),
                                style = WerusButtonStyle.Secondary,
                            )
                        }
                        KnowledgeProgressStatus.MASTERED -> {
                            Text(
                                text = "✓ 已达成掌握阶段，巩固记忆可主动加入复习",
                                style = WerusTypography.labelSmall,
                                color = WerusColors.Success,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                }
            }

            // 6. 操作按钮区
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (knowledge.type == KnowledgeType.WORD && onWordLookup != null) {
                    WerusButton(
                        text = "在词库中查找",
                        onClick = { onWordLookup(knowledge.text) },
                        modifier = Modifier.weight(1f),
                        style = WerusButtonStyle.Primary,
                    )
                }

                if (onAddReview != null) {
                    WerusButton(
                        text = "加入复习",
                        onClick = onAddReview,
                        modifier = Modifier.weight(1f),
                        style = if (knowledge.type == KnowledgeType.WORD && onWordLookup != null) WerusButtonStyle.Secondary else WerusButtonStyle.Primary,
                    )
                }
            }

            if (onAskAi != null) {
                WerusButton(
                    text = "✦ 问 AI：讲解「${knowledge.label ?: knowledge.text}」",
                    onClick = { onAskAi(knowledge.label ?: knowledge.text) },
                    modifier = Modifier.fillMaxWidth(),
                    style = WerusButtonStyle.Secondary,
                )
            }
        }
    }
}

private data class KnowledgeTypeTheme(
    val badgeText: String,
    val containerColor: Color,
    val contentColor: Color,
)

private fun getKnowledgeTypeTheme(type: KnowledgeType): KnowledgeTypeTheme {
    val palette = type.werusPalette()
    val badge = when (type) {
        KnowledgeType.GRAMMAR -> "GRAMMAR · 语法"
        KnowledgeType.PHRASE -> "PHRASE · 短语"
        KnowledgeType.PATTERN -> "PATTERN · 句型"
        KnowledgeType.WORD -> "WORD · 单词"
        KnowledgeType.PRONUNCIATION -> "PRONUNCIATION · 发音"
        KnowledgeType.AI_NOTE -> "AI NOTE · 笔记"
    }
    return KnowledgeTypeTheme(badge, palette.container, palette.content)
}

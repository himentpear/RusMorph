package org.namchieh.rusmorph.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.namchieh.rusmorph.domain.textbook.KnowledgeProgress
import org.namchieh.rusmorph.domain.textbook.KnowledgeProgressStatus
import org.namchieh.rusmorph.domain.textbook.KnowledgeType
import org.namchieh.rusmorph.domain.textbook.SentenceKnowledge
import org.namchieh.rusmorph.ui.theme.RusMorphColors

/**
 * 统一规范知识卡片组件 (KnowledgeCard)
 *
 * 支持四种核心语言学实体类型：Phrase、Grammar、Pattern、Word
 * 呈现：类型徽章、标题、解释、例句、学习进度状态机交互与上下文操作
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

    RusCard(
        modifier = modifier.fillMaxWidth(),
        backgroundColor = RusMorphColors.Surface,
        border = BorderStroke(1.dp, RusMorphColors.Outline),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 1. 类型徽章与当前状态
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 类型徽章
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = typeTheme.containerColor,
                    contentColor = typeTheme.contentColor,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(typeTheme.contentColor),
                        )
                        Text(
                            text = typeTheme.badgeText,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            letterSpacing = 0.5.sp,
                        )
                    }
                }

                // 当前学习进度标签
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = RusMorphColors.PillBackground,
                    border = BorderStroke(1.dp, RusMorphColors.OutlineSoft),
                ) {
                    Text(
                        text = currentStatus.labelZh,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = when (currentStatus) {
                            KnowledgeProgressStatus.MASTERED -> RusMorphColors.AccentGreen
                            KnowledgeProgressStatus.PRACTICED -> RusMorphColors.AccentBlue
                            KnowledgeProgressStatus.UNDERSTOOD -> RusMorphColors.VividOrange
                            KnowledgeProgressStatus.SEEN -> RusMorphColors.TextSecondary
                        },
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            // 2. 标题区
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val title = knowledge.label ?: knowledge.text
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = RusMorphColors.TextPrimary,
                    lineHeight = 28.sp,
                )
                // 若标题与原文片段不同，则辅助呈现原文划定片段
                if (knowledge.label != null && knowledge.label != knowledge.text) {
                    Text(
                        text = "原句片段：«${knowledge.text}»",
                        style = MaterialTheme.typography.bodyMedium,
                        color = RusMorphColors.TextSecondary,
                        fontStyle = FontStyle.Italic,
                    )
                }
            }

            // 3. 解释区
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "解释",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = RusMorphColors.TextSecondary,
                )
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = RusMorphColors.Canvas,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = knowledge.explanation?.ifBlank { null } ?: "该知识点暂无额外文字解释",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = RusMorphColors.TextPrimary,
                        lineHeight = 22.sp,
                    )
                }
            }

            // 4. 例句区 (若有)
            if (!knowledge.example.isNullOrBlank()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "例句",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = RusMorphColors.TextSecondary,
                    )
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = RusMorphColors.WarmCream.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, RusMorphColors.WarmCream),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = knowledge.example,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = RusMorphColors.TextPrimary,
                                lineHeight = 22.sp,
                            )
                        }
                    }
                }
            }

            // 5. 学习进度流转 (seen -> understood -> practiced -> mastered)
            if (onProgressChange != null) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "认知掌握进度",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = RusMorphColors.TextSecondary,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        KnowledgeProgressStatus.values().forEach { status ->
                            val isCurrent = status == currentStatus
                            val isPassed = status.rank <= currentStatus.rank
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onProgressChange(status) },
                                shape = RoundedCornerShape(8.dp),
                                color = when {
                                    isCurrent -> RusMorphColors.CarbonBlack
                                    isPassed -> RusMorphColors.PillBackground
                                    else -> RusMorphColors.Canvas
                                },
                                border = BorderStroke(
                                    1.dp,
                                    if (isCurrent) RusMorphColors.CarbonBlack else RusMorphColors.OutlineSoft,
                                ),
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = status.labelZh,
                                        fontSize = 11.sp,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isCurrent) RusMorphColors.TextOnDark else RusMorphColors.TextPrimary,
                                    )
                                }
                            }
                        }
                    }

                    // 步进引导按钮
                    when (currentStatus) {
                        KnowledgeProgressStatus.SEEN -> {
                            RusButton(
                                text = "标为已理解 ✓",
                                onClick = { onProgressChange(KnowledgeProgressStatus.UNDERSTOOD) },
                                modifier = Modifier.fillMaxWidth(),
                                isSecondary = true,
                            )
                        }
                        KnowledgeProgressStatus.UNDERSTOOD -> {
                            RusButton(
                                text = "标为已练习 ✎",
                                onClick = { onProgressChange(KnowledgeProgressStatus.PRACTICED) },
                                modifier = Modifier.fillMaxWidth(),
                                isSecondary = true,
                            )
                        }
                        KnowledgeProgressStatus.PRACTICED -> {
                            RusButton(
                                text = "标为已掌握 ★",
                                onClick = { onProgressChange(KnowledgeProgressStatus.MASTERED) },
                                modifier = Modifier.fillMaxWidth(),
                                isSecondary = true,
                            )
                        }
                        KnowledgeProgressStatus.MASTERED -> {
                            // 已完全掌握，显示完成提示
                            Text(
                                text = "✓ 已达成掌握阶段，巩固记忆可主动加入复习",
                                style = MaterialTheme.typography.labelSmall,
                                color = RusMorphColors.AccentGreen,
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
                    RusButton(
                        text = "在词库中查找",
                        onClick = { onWordLookup(knowledge.text) },
                        modifier = Modifier.weight(1f),
                    )
                }

                if (onAddReview != null) {
                    RusButton(
                        text = "加入复习",
                        onClick = onAddReview,
                        modifier = Modifier.weight(1f),
                        isSecondary = knowledge.type == KnowledgeType.WORD && onWordLookup != null,
                    )
                }
            }

            if (onAskAi != null) {
                RusButton(
                    text = "✦ 问 AI：讲解「${knowledge.label ?: knowledge.text}」",
                    onClick = { onAskAi(knowledge.label ?: knowledge.text) },
                    modifier = Modifier.fillMaxWidth(),
                    isSecondary = true,
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

private fun getKnowledgeTypeTheme(type: KnowledgeType): KnowledgeTypeTheme = when (type) {
    KnowledgeType.GRAMMAR -> KnowledgeTypeTheme(
        badgeText = "GRAMMAR · 语法",
        containerColor = Color(0xFFEFF6FF), // soft blue
        contentColor = Color(0xFF1D4ED8),
    )
    KnowledgeType.PHRASE -> KnowledgeTypeTheme(
        badgeText = "PHRASE · 短语",
        containerColor = Color(0xFFFEF3C7), // soft amber
        contentColor = Color(0xFFB45309),
    )
    KnowledgeType.PATTERN -> KnowledgeTypeTheme(
        badgeText = "PATTERN · 句型",
        containerColor = Color(0xFFF0FDF4), // soft green
        contentColor = Color(0xFF047857),
    )
    KnowledgeType.WORD -> KnowledgeTypeTheme(
        badgeText = "WORD · 单词",
        containerColor = Color(0xFFFEF2F2), // soft rose/brick
        contentColor = Color(0xFFB91C1C),
    )
    KnowledgeType.PRONUNCIATION -> KnowledgeTypeTheme(
        badgeText = "PRONUNCIATION · 发音",
        containerColor = Color(0xFFF5F3FF),
        contentColor = Color(0xFF6D28D9),
    )
    KnowledgeType.AI_NOTE -> KnowledgeTypeTheme(
        badgeText = "AI NOTE · 笔记",
        containerColor = Color(0xFFFFFBEB),
        contentColor = Color(0xFFD97706),
    )
}

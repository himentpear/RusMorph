package org.namchieh.rusmorph.ui.screen.tools

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
import org.namchieh.rusmorph.domain.learning.Course
import org.namchieh.rusmorph.ui.components.RusCard
import org.namchieh.rusmorph.ui.components.RusPillBadge
import org.namchieh.rusmorph.ui.components.RusSectionTitle
import org.namchieh.rusmorph.ui.learning.Loadable
import org.namchieh.rusmorph.ui.navigation.BottomDestination
import org.namchieh.rusmorph.ui.screen.learning.LearningScaffold
import org.namchieh.rusmorph.ui.theme.RusMorphColors
import org.namchieh.rusmorph.ui.theme.RusMorphTechTypography

/**
 * 全员俄人「工具」主界面 (ToolsScreen)。
 * 聚合语言学习高频工程组件：语音跟读评测、AI 助教终端、形态学变格变位速查表与系统诊断。
 */
@Composable
fun ToolsScreen(
    coursesState: Loadable<List<Course>>,
    onCourse: (String) -> Unit,
    onPronunciation: () -> Unit,
    onAiCommands: () -> Unit,
    onNavigateToRules: () -> Unit,
    onSettings: () -> Unit,
    onBottom: (BottomDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    LearningScaffold("工具", BottomDestination.Tools, onBottom) { root ->
        Column(
            modifier = root
                .then(modifier)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .widthIn(max = 760.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            RusSectionTitle("实用工具箱", "高效俄语学习辅助与工程诊断组件")

            // 1. 🎙 语音朗读与跟读评测
            RusCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = onPronunciation,
                backgroundColor = RusMorphColors.Surface,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            RusPillBadge("语音实验室", containerColor = RusMorphColors.WarmCream, contentColor = RusMorphColors.CarbonBlack)
                            Text("1.0x / 0.75x", style = RusMorphTechTypography.MicroPill, color = RusMorphColors.TextTertiary)
                        }
                        Text("自由发音与 Whisper 评测", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "支持俄语句子自由朗读、动态音量跳动柱与智能可懂度分析比对",
                            style = MaterialTheme.typography.bodySmall,
                            color = RusMorphColors.TextSecondary,
                        )
                    }
                    Text("开始 🎙", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = RusMorphColors.AccentOrange)
                }
            }

            // 2. ⚡ AI 俄语助教与指令终端
            RusCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = onAiCommands,
                backgroundColor = RusMorphColors.Surface,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            RusPillBadge("语言智能", containerColor = RusMorphColors.SurfaceMuted, contentColor = RusMorphColors.AccentBlue)
                            Text("自然语言终端", style = RusMorphTechTypography.MicroPill, color = RusMorphColors.TextTertiary)
                        }
                        Text("AI 俄语助教工作台", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "语法疑难探究、汉俄双向句型结构对比、长句变格成分拆解",
                            style = MaterialTheme.typography.bodySmall,
                            color = RusMorphColors.TextSecondary,
                        )
                    }
                    Text("唤起 ⚡", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = RusMorphColors.AccentBlue)
                }
            }

            // 3. 📊 俄语变格变位规则表
            RusCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = onNavigateToRules,
                backgroundColor = RusMorphColors.Surface,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            RusPillBadge("语法基石", containerColor = RusMorphColors.WarmCream, contentColor = RusMorphColors.CarbonBlack)
                            Text("Склонение · Спряжение", style = RusMorphTechTypography.MicroPill, color = RusMorphColors.TextTertiary)
                        }
                        Text("俄语变格变位规则表", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "名词六格、性数变化、硬软词尾、形容词一致关系与动词第一/第二变位规则。",
                            style = MaterialTheme.typography.bodySmall,
                            color = RusMorphColors.TextSecondary,
                        )
                    }
                    Text("查看规则 ›", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = RusMorphColors.CarbonBlack)
                }
            }

            // 4. 📚 完整课程目录库索引 (所有册次)
            if (coursesState is Loadable.Content && coursesState.value.isNotEmpty()) {
                RusSectionTitle("教材课程总库", "查看各册次完整课文、对话与词汇目录")
                coursesState.value.forEach { course ->
                    RusCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onCourse(course.id) },
                        backgroundColor = RusMorphColors.Surface,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                RusPillBadge("教材册次", containerColor = RusMorphColors.SurfaceMuted, contentColor = RusMorphColors.CarbonBlack)
                                Text(course.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text("${course.lessonCount} 个标准课次", style = MaterialTheme.typography.bodySmall, color = RusMorphColors.TextSecondary)
                            }
                            Text("查看目录 ›", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = RusMorphColors.CarbonBlack)
                        }
                    }
                }
            }

            // 5. ⚙ 系统设置与状态诊断
            RusCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = onSettings,
                backgroundColor = RusMorphColors.Surface,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        RusPillBadge("系统设置", containerColor = RusMorphColors.WarmCream, contentColor = RusMorphColors.CarbonBlack)
                        Text("系统参数与接口诊断", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("离线词库校验、Cloudflare 服务诊断、显示偏好", style = MaterialTheme.typography.bodySmall, color = RusMorphColors.TextSecondary)
                    }
                    Text("设置 ⚙", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = RusMorphColors.TextSecondary)
                }
            }
        }
    }
}

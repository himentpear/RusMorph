package org.namchieh.rusmorph.ui.screen.tools

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
    onSettings: () -> Unit,
    onBottom: (BottomDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showMorphologyCheatsheet by remember { mutableStateOf(false) }

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

            // 3. 📊 俄语形态学与变格变位速查
            RusCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = { showMorphologyCheatsheet = !showMorphologyCheatsheet },
                backgroundColor = RusMorphColors.Surface,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            RusPillBadge("语法基石", containerColor = RusMorphColors.WarmCream, contentColor = RusMorphColors.CarbonBlack)
                            Text("俄语形态学变格变位速查", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        }
                        Text(
                            text = if (showMorphologyCheatsheet) "收起 ▲" else "展开 ▼",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = RusMorphColors.CarbonBlack,
                        )
                    }
                    Text(
                        "名词六格硬软变化规律、动词一二变位人称词尾对照速查",
                        style = MaterialTheme.typography.bodySmall,
                        color = RusMorphColors.TextSecondary,
                    )

                    AnimatedVisibility(
                        visible = showMorphologyCheatsheet,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            HorizontalDivider(color = RusMorphColors.OutlineSoft, thickness = 0.8.dp)

                            // 名词六格词尾表简析
                            Text("1. 名词单数六格基本词尾规则", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            Surface(
                                color = RusMorphColors.Canvas,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("• 阳性辅音: И. - | Р. -а | Д. -у | В. -а/同主格 | Т. -ом | П. -е", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Serif)
                                    Text("• 阴性 -а/-я: И. -а | Р. -ы/-и | Д. -е | В. -у/-ю | Т. -ой | П. -е", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Serif)
                                    Text("• 中性 -о/-е: И. -о | Р. -а | Д. -у | В. -о | Т. -ом | П. -е", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Serif)
                                }
                            }

                            // 动词人称变位表简析
                            Text("2. 动词现在时人称变位规则", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            Surface(
                                color = RusMorphColors.Canvas,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("• 第一变位法 (читать): -ю, -ешь, -ет, -ем, -ете, -ют", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Serif)
                                    Text("• 第二变位法 (говорить): -ю/-у, -ишь, -ит, -им, -ите, -ят/-ат", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Serif)
                                }
                            }
                        }
                    }
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

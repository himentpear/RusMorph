package org.namchieh.rusmorph.ui.screen.learning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.namchieh.rusmorph.domain.learning.Course
import org.namchieh.rusmorph.domain.learning.LearningProgress
import org.namchieh.rusmorph.ui.components.RusCard
import org.namchieh.rusmorph.ui.components.RusPillBadge
import org.namchieh.rusmorph.ui.components.RusProgressBar
import org.namchieh.rusmorph.ui.components.RusSectionTitle
import org.namchieh.rusmorph.ui.design.WerusColors
import org.namchieh.rusmorph.ui.design.WerusTypography
import org.namchieh.rusmorph.ui.learning.Loadable
import org.namchieh.rusmorph.ui.navigation.BottomDestination

/** Learner-facing flow. Engineering and system tools do not belong here. */
@Composable
fun LearningScreen(
    coursesState: Loadable<List<Course>>,
    activeCourseId: String,
    progress: List<LearningProgress>,
    onSelectCourse: (String) -> Unit,
    onCourse: (String) -> Unit,
    onPronunciation: () -> Unit,
    onAiCommands: () -> Unit,
    onConversation: () -> Unit,
    onGrammar: () -> Unit = {},
    onTem4: () -> Unit = {},
    onBottom: (BottomDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    LearningScaffold("学习", BottomDestination.Learning, onBottom) { root ->
        Column(
            modifier = root.then(modifier).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("学习", style = WerusTypography.Display, color = WerusColors.Ink)

            RusSectionTitle("我的教材")
            when (coursesState) {
                Loadable.Loading -> LearningStatusCard("正在整理教材", "请稍候")
                is Loadable.Error -> LearningStatusCard("教材暂不可用", coursesState.message)
                is Loadable.Content -> if (coursesState.value.isEmpty()) {
                    LearningStatusCard("还没有教材", "教材导入后会显示在这里")
                } else {
                    coursesState.value.forEach { course ->
                        CourseFlowCard(
                            course = course,
                            active = course.id == activeCourseId,
                            progress = progress.filter { it.courseId == course.id }.maxByOrNull { it.updatedAt },
                            onClick = {
                                onSelectCourse(course.id)
                                onCourse(course.id)
                            },
                        )
                    }
                }
            }

            RusSectionTitle("专项学习")
            LearningEntryCard("Г", "语法学习", onGrammar)
            LearningEntryCard("Т", "专四练习", onTem4)
            LearningEntryCard("🎧", "发音训练", onPronunciation)
            LearningEntryCard("✦", "AI 学习辅助", onAiCommands)
            LearningEntryCard("●", "AI 对话练习", onConversation)
        }
    }
}

@Composable
private fun CourseFlowCard(course: Course, active: Boolean, progress: LearningProgress?, onClick: () -> Unit) {
    val value = progress?.progress ?: course.progress
    RusCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        backgroundColor = if (active) WerusColors.RedSoft.copy(alpha = .55f) else WerusColors.Paper,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                RusPillBadge(
                    if (active) "当前教材" else "教材",
                    containerColor = if (active) WerusColors.Red else WerusColors.Beige,
                    contentColor = if (active) WerusColors.OnDark else WerusColors.RedDark,
                )
                Text(if (active) "查看课次 ›" else "选用 ›", style = WerusTypography.Metadata, color = WerusColors.RedDark)
            }
            Text(course.title, style = WerusTypography.Title, color = WerusColors.Ink, fontWeight = FontWeight.SemiBold)
            Text(course.subtitle, style = WerusTypography.Caption, color = WerusColors.InkMuted)
            RusProgressBar(value)
            Text(
                progress?.lessonId?.substringAfterLast('-')?.let { "学习中 · 第 $it 课" }
                    ?: "${course.lessonCount} 个课次 · 尚未开始",
                style = WerusTypography.Metadata,
                color = WerusColors.InkFaint,
            )
        }
    }
}

@Composable
private fun LearningEntryCard(icon: String, title: String, onClick: () -> Unit) {
    RusCard(Modifier.fillMaxWidth(), onClick = onClick, backgroundColor = WerusColors.Paper) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(icon, style = WerusTypography.Title)
            Text(title, Modifier.weight(1f), style = WerusTypography.Title, color = WerusColors.Ink)
            Text("›", style = WerusTypography.Title, color = WerusColors.Red)
        }
    }
}

@Composable
private fun LearningStatusCard(title: String, subtitle: String) {
    RusCard(Modifier.fillMaxWidth(), backgroundColor = WerusColors.Paper) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = WerusTypography.Title, color = WerusColors.Ink)
            Text(subtitle, style = WerusTypography.Caption, color = WerusColors.InkMuted)
        }
    }
}

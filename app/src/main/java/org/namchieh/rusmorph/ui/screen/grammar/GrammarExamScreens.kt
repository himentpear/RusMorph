@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.namchieh.rusmorph.ui.screen.grammar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.namchieh.rusmorph.data.local.GrammarPointEntity
import org.namchieh.rusmorph.data.local.QuestionEntity
import org.namchieh.rusmorph.domain.grammar.GrammarPointOverview
import org.namchieh.rusmorph.domain.grammar.QuestionRunnerMode
import org.namchieh.rusmorph.ui.components.RusButton
import org.namchieh.rusmorph.ui.components.RusCard
import org.namchieh.rusmorph.ui.components.RusEmptyState
import org.namchieh.rusmorph.ui.components.RusPillBadge
import org.namchieh.rusmorph.ui.components.RusProgressBar
import org.namchieh.rusmorph.ui.components.RusSectionTitle
import org.namchieh.rusmorph.ui.design.WerusColors
import org.namchieh.rusmorph.ui.design.WerusTypography
import org.namchieh.rusmorph.ui.grammar.GrammarDetailState
import org.namchieh.rusmorph.ui.grammar.QuestionRunnerUiState
import org.namchieh.rusmorph.ui.navigation.BottomDestination
import org.namchieh.rusmorph.ui.screen.learning.LearningScaffold

@Composable
fun GrammarHomeScreen(
    points: List<GrammarPointOverview>,
    onPoint: (String) -> Unit,
    onTem4: () -> Unit,
    onBottom: (BottomDestination) -> Unit,
) {
    LearningScaffold("语法学习", BottomDestination.Learning, onBottom) { root ->
        Column(root.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            RusSectionTitle("语法学习", "语法点与专四真题共用同一学习记录")
            val continuing = points.filter { it.completedQuestionCount > 0 && it.completedQuestionCount < it.realQuestionCount }
                .maxByOrNull { it.completedQuestionCount }
            RusSectionTitle("继续学习")
            if (continuing == null) {
                RusCard(Modifier.fillMaxWidth(), onClick = { points.firstOrNull()?.let { onPoint(it.point.pointId) } }) {
                    Text("从第一个语法点开始", color = WerusColors.Ink)
                }
            } else GrammarPointCard(continuing, onPoint)

            val weak = points.filter { it.completedQuestionCount > 0 }.sortedBy { it.mastery }.take(3)
            RusSectionTitle("薄弱语法", "仅依据已提交的题目证据计算")
            if (weak.isEmpty()) Text("完成真题后将在这里显示薄弱项。", color = WerusColors.InkMuted)
            weak.forEach { GrammarPointCard(it, onPoint) }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                RusSectionTitle("全部语法", "${points.size} 个知识点")
                TextButton(onClick = onTem4) { Text("专四练习 ›", color = WerusColors.RedDark) }
            }
            points.forEach { GrammarPointCard(it, onPoint) }
        }
    }
}

@Composable
private fun GrammarPointCard(item: GrammarPointOverview, onPoint: (String) -> Unit) {
    RusCard(Modifier.fillMaxWidth(), onClick = { onPoint(item.point.pointId) }) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(item.point.titleZh, style = WerusTypography.Title, fontWeight = FontWeight.SemiBold)
            Text(item.point.titleRu, style = WerusTypography.Caption, color = WerusColors.InkMuted)
            Text("掌握度 ${(item.mastery * 100).toInt()}%", style = WerusTypography.Metadata, color = WerusColors.RedDark)
            RusProgressBar(item.mastery.toFloat())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("关联真题 ${item.realQuestionCount}", color = WerusColors.InkMuted)
                Text("已完成 ${item.completedQuestionCount}  继续学习 ›", color = WerusColors.Ink)
            }
        }
    }
}

@Composable
fun GrammarDetailScreen(
    state: GrammarDetailState,
    onQuestion: (String, String) -> Unit,
) {
    LearningScaffold(state.point?.titleZh ?: "语法") { root ->
        Column(root.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            val point = state.point
            if (point == null) {
                RusEmptyState("语法内容加载中", "")
                return@Column
            }
            RusSectionTitle(point.titleZh, point.titleRu)
            Text("掌握度 ${((state.mastery?.mastery ?: 0.0) * 100).toInt()}%", color = WerusColors.RedDark)
            RusSectionTitle("核心解释")
            Text(point.explanation, style = WerusTypography.Body, color = WerusColors.Ink)
            point.exampleRu?.let { RusCard(Modifier.fillMaxWidth()) { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("俄语例句", color = WerusColors.InkFaint); Text(it, style = WerusTypography.Title) } } }
            point.exampleZh?.let { RusCard(Modifier.fillMaxWidth()) { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("中文释义", color = WerusColors.InkFaint); Text(it) } } }
            RusSectionTitle("专四怎么考", "原始映射未审核时仍保留展示")
            if (state.questions.isEmpty()) Text("关联真题 0，仍可继续学习本语法点。", color = WerusColors.InkMuted)
            state.questions.take(5).forEach { question -> QuestionPreview(question) { onQuestion(question.questionId, point.pointId) } }
            if (state.questions.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RusButton("做一道关联真题", { onQuestion(state.questions.first().questionId, point.pointId) }, Modifier.weight(1f))
                    RusButton("AI 变式", { onQuestion(state.questions.first().questionId, point.pointId) }, Modifier.weight(1f), isSecondary = true)
                }
            }
        }
    }
}

@Composable
fun Tem4PracticeScreen(
    questions: List<QuestionEntity>,
    wrongQuestions: List<QuestionEntity>,
    grammarPoints: List<GrammarPointOverview>,
    onQuestion: (String, String?) -> Unit,
    onGrammar: (String) -> Unit,
    onBottom: (BottomDestination) -> Unit,
) {
    LearningScaffold("专四练习", BottomDestination.Learning, onBottom) { root ->
        Column(root.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            RusSectionTitle("专四语法", "真题、语法点与作答记录双向关联")
            questions.firstOrNull()?.let { QuestionPreview(it) { onQuestion(it.questionId, null) } }
            RusSectionTitle("按年份练习")
            questions.groupBy { it.examYearLabel ?: it.examYear?.toString().orEmpty() }.forEach { (year, items) ->
                RusCard(Modifier.fillMaxWidth(), onClick = { onQuestion(items.first().questionId, null) }) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(year, fontWeight = FontWeight.SemiBold); Text("${items.size} 道 ›") }
                }
            }
            RusSectionTitle("按语法练习")
            grammarPoints.filter { it.realQuestionCount > 0 }.forEach { point ->
                RusCard(Modifier.fillMaxWidth(), onClick = { onGrammar(point.point.pointId) }) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(point.point.titleZh); Text("${point.realQuestionCount} 道 ›", color = WerusColors.InkMuted) }
                }
            }
            RusSectionTitle("错题")
            if (wrongQuestions.isEmpty()) Text("暂无错题", color = WerusColors.InkMuted)
            wrongQuestions.take(10).forEach { QuestionPreview(it) { onQuestion(it.questionId, null) } }
            RusSectionTitle("随机练习")
            questions.randomOrNull()?.let { RusButton("随机开始", { onQuestion(it.questionId, null) }, Modifier.fillMaxWidth()) }
        }
    }
}

@Composable
private fun QuestionPreview(question: QuestionEntity, onClick: () -> Unit) {
    RusCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RusPillBadge(question.examYearLabel ?: question.sourceType, containerColor = WerusColors.Beige, contentColor = WerusColors.Ink)
            Text(question.stem, maxLines = 3)
            Text("开始作答 ›", color = WerusColors.RedDark)
        }
    }
}

@Composable
fun QuestionRunnerScreen(
    state: QuestionRunnerUiState,
    mode: QuestionRunnerMode,
    onSelect: (String) -> Unit,
    onSubmit: () -> Unit,
    onPeek: (String) -> Unit,
    onClosePeek: () -> Unit,
    onFullGrammar: (String) -> Unit,
    onNext: (String?) -> Unit,
    onGenerateVariant: (String) -> Unit,
    onGeneratedQuestion: (String) -> Unit,
) {
    LaunchedEffect(state.generatedQuestionId) {
        state.generatedQuestionId?.let(onGeneratedQuestion)
    }
    val peek = state.grammarPoints.firstOrNull { it.pointId == state.peekPointId }
    if (peek != null && mode != QuestionRunnerMode.SIMULATION) {
        GrammarPeekSheet(peek, onClosePeek, { onFullGrammar(peek.pointId) }, { onFullGrammar(peek.pointId) })
    }
    LearningScaffold("题目") { root ->
        Column(root.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            val question = state.question
            if (question == null) {
                RusEmptyState("题目加载中", "")
                return@Column
            }
            RusPillBadge(
                if (question.sourceType == "TEM4_REAL") "${question.examYearLabel ?: ""} · 专四真题" else "AI 变式",
                containerColor = if (question.sourceType == "TEM4_REAL") WerusColors.Beige else WerusColors.RedSoft,
                contentColor = WerusColors.Ink,
            )
            Text(question.stem, style = WerusTypography.Title, fontWeight = FontWeight.SemiBold)
            listOf("A" to question.optionA, "B" to question.optionB, "C" to question.optionC, "D" to question.optionD).forEach { (label, value) ->
                OutlinedButton(
                    onClick = { onSelect(label) },
                    enabled = !state.submitted,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = if (state.selectedAnswer == label) WerusColors.RedSoft else WerusColors.Paper),
                ) { Text("$label. $value", modifier = Modifier.fillMaxWidth(), color = WerusColors.Ink) }
            }
            RusButton(if (state.submitted) "已提交" else "提交答案", onSubmit, Modifier.fillMaxWidth(), enabled = state.selectedAnswer != null && !state.submitted)
            if (state.submitted && mode == QuestionRunnerMode.SIMULATION) {
                Text("答案已记录，模拟结束后统一查看解析。", color = WerusColors.InkMuted)
            }
            if (state.submitted && mode != QuestionRunnerMode.SIMULATION) {
                Text(if (state.correct == true) "✓ 回答正确" else "× 回答错误", color = if (state.correct == true) WerusColors.Ink else WerusColors.RedDark, fontWeight = FontWeight.Bold)
                Text("正确答案：${question.answer}", fontWeight = FontWeight.SemiBold)
                question.analysis?.let { RusCard(Modifier.fillMaxWidth()) { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { Text("解析", fontWeight = FontWeight.SemiBold); Text(it) } } }
                if (state.grammarPoints.isNotEmpty()) {
                    RusSectionTitle("考点")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.grammarPoints.forEach { point -> RusPillBadge(point.titleZh, Modifier.clickable { onPeek(point.pointId) }, WerusColors.Beige, WerusColors.Ink) }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        RusButton("再做一道", { onNext(state.grammarPoints.first().pointId) }, Modifier.weight(1f), isSecondary = true)
                        if (question.sourceType == "TEM4_REAL") {
                            RusButton(if (state.generatingVariant) "生成中…" else "AI 生成变式题", { onGenerateVariant(state.grammarPoints.first().pointId) }, Modifier.weight(1f), enabled = !state.generatingVariant)
                        }
                    }
                }
                state.variantError?.let { Text(it, color = WerusColors.RedDark) }
            }
        }
    }
}

@Composable
private fun GrammarPeekSheet(
    point: GrammarPointEntity,
    onDismiss: () -> Unit,
    onFull: () -> Unit,
    onRelated: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = WerusColors.Paper) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            RusSectionTitle(point.titleZh, point.titleRu)
            Text(point.explanation, maxLines = 8)
            point.exampleRu?.let { Text(it, style = WerusTypography.Title) }
            point.exampleZh?.let { Text(it, color = WerusColors.InkMuted) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RusButton("完整学习", onFull, Modifier.weight(1f))
                RusButton("相关真题", onRelated, Modifier.weight(1f), isSecondary = true)
            }
        }
    }
}

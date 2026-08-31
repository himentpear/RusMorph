package org.namchieh.rusmorph.ui.screen.learning

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Velocity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.namchieh.rusmorph.data.local.LexiconEntryWithDetails
import org.namchieh.rusmorph.data.repository.LearningStats
import org.namchieh.rusmorph.domain.learning.Course
import org.namchieh.rusmorph.domain.learning.Dialogue
import org.namchieh.rusmorph.domain.learning.LearningProgress
import org.namchieh.rusmorph.domain.learning.LearningUnitType
import org.namchieh.rusmorph.domain.learning.Lesson
import org.namchieh.rusmorph.domain.learning.ReviewItem
import org.namchieh.rusmorph.ui.components.RusButton
import org.namchieh.rusmorph.ui.components.RusBottomSheet
import org.namchieh.rusmorph.ui.components.RusCard
import org.namchieh.rusmorph.ui.components.RusContextChip
import org.namchieh.rusmorph.ui.components.RusCourseCard
import org.namchieh.rusmorph.ui.components.RusEmptyState
import org.namchieh.rusmorph.ui.components.RusLearningUnitCard
import org.namchieh.rusmorph.ui.components.RusLessonCard
import org.namchieh.rusmorph.ui.components.RusProgressBar
import org.namchieh.rusmorph.ui.components.RusSectionTitle
import org.namchieh.rusmorph.ui.components.RusStat
import org.namchieh.rusmorph.ui.components.RusWordChip
import org.namchieh.rusmorph.ui.learning.Loadable
import org.namchieh.rusmorph.ui.navigation.BottomDestination
import org.namchieh.rusmorph.ui.navigation.RusMorphBottomBar
import org.namchieh.rusmorph.ui.theme.RusMorphColors
import java.time.LocalTime
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearningScaffold(
    title: String,
    selected: BottomDestination? = null,
    onBottom: (BottomDestination) -> Unit = {},
    onBack: (() -> Unit)? = null,
    onAI: (() -> Unit)? = null,
    content: @Composable (Modifier) -> Unit,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val threshold = with(density) { 132.dp.toPx() }
    val maxReveal = with(density) { 252.dp.toPx() }
    var reveal by remember { mutableFloatStateOf(0f) }
    var entering by remember { mutableStateOf(false) }
    val progress = (reveal / threshold).coerceIn(0f, 1f)

    fun enterAI() {
        if (entering || onAI == null) return
        entering = true
        scope.launch {
            animate(reveal, maxReveal, animationSpec = tween(260)) { value, _ -> reveal = value }
            onAI()
        }
    }

    val connection = remember(onAI, threshold, maxReveal) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (onAI == null || entering || reveal <= 0f || available.y >= 0f) return Offset.Zero
                val previous = reveal
                reveal = (reveal + available.y).coerceAtLeast(0f)
                return Offset(0f, reveal - previous)
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (onAI == null || entering || source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
                val previous = reveal
                reveal = (reveal + available.y).coerceAtMost(maxReveal)
                return Offset(0f, reveal - previous)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (onAI == null || reveal <= 0f) return Velocity.Zero
                if (reveal >= threshold) enterAI()
                else scope.launch { animate(reveal, 0f, animationSpec = spring<Float>()) { value, _ -> reveal = value } }
                return available
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (!entering && reveal in 0.01f..<threshold) {
                    scope.launch { animate(reveal, 0f, animationSpec = spring<Float>()) { value, _ -> reveal = value } }
                }
                return Velocity.Zero
            }
        }
    }

    Box(Modifier.fillMaxSize().background(if (onAI != null) RusMorphColors.PrimaryDark else RusMorphColors.Canvas)) {
        if (onAI != null) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .height(252.dp)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .graphicsLayer {
                        translationY = -42.dp.toPx() + reveal * .24f
                        alpha = .08f + progress * .92f
                        scaleX = .94f + progress * .06f
                        scaleY = .94f + progress * .06f
                    }
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Text("✦  NEGATIVE LAYER", style = MaterialTheme.typography.labelMedium, color = RusMorphColors.Secondary, fontWeight = FontWeight.Bold)
                Text("AI 学习工作区", style = MaterialTheme.typography.headlineMedium, color = RusMorphColors.TextOnDark, fontWeight = FontWeight.SemiBold)
                Text("Context · $title", color = RusMorphColors.TextOnDark.copy(alpha = .74f))
                Text(if (progress >= 1f) "松开进入 AI" else "继续向下滑动", color = if (progress >= 1f) RusMorphColors.Secondary else RusMorphColors.TextOnDark)
                Box(Modifier.width(52.dp).height(3.dp).graphicsLayer { scaleX = progress.coerceAtLeast(.12f) }.background(RusMorphColors.Secondary))
            }
        }

        Scaffold(
            modifier = Modifier
                .then(if (onAI != null) Modifier.nestedScroll(connection) else Modifier)
                .graphicsLayer {
                    translationY = reveal
                    shadowElevation = if (reveal > 0f) 18.dp.toPx() else 0f
                },
            containerColor = RusMorphColors.Canvas,
            topBar = {
                TopAppBar(
                    title = { Text(title, fontWeight = FontWeight.SemiBold) },
                    navigationIcon = { if (onBack != null) TextButton(onClick = onBack) { Text("←") } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = RusMorphColors.Canvas),
                )
            },
            bottomBar = { if (selected != null) RusMorphBottomBar(selected, onBottom) },
        ) { padding -> content(Modifier.fillMaxSize().padding(padding)) }
    }
}

@Composable
fun HomeScreen(
    coursesState: Loadable<List<Course>>, stats: LearningStats, progress: List<LearningProgress>,
    onCourse: (String) -> Unit, onContinue: (String, String) -> Unit, onDictionary: () -> Unit,
    onPronunciation: () -> Unit, onCourses: () -> Unit, onReview: () -> Unit,
    onBottom: (BottomDestination) -> Unit, onAI: () -> Unit,
) {
    LearningScaffold("学习首页", BottomDestination.Home, onBottom, onAI = onAI) { root ->
        Column(root.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            val greeting = when (LocalTime.now().hour) { in 5..10 -> "Доброе утро"; in 11..17 -> "Добрый день"; else -> "Добрый вечер" }
            Column { Text(greeting, style = androidx.compose.material3.MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold); Text("继续学习俄语", color = RusMorphColors.TextSecondary) }
            when (coursesState) {
                Loadable.Loading -> CircularProgressIndicator()
                is Loadable.Error -> RusEmptyState("课程暂不可用", coursesState.message)
                is Loadable.Content -> {
                    val course = coursesState.value.firstOrNull()
                    if (course == null) RusEmptyState("还没有课程", "导入课程后会显示在这里") else {
                        val latest = progress.firstOrNull { it.courseId == course.id && it.lessonId != null }
                        RusSectionTitle("继续学习")
                        RusCard(Modifier.fillMaxWidth(), onClick = { latest?.lessonId?.let { onContinue(course.id, it) } ?: onCourse(course.id) }) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(course.title, color = RusMorphColors.Primary, fontWeight = FontWeight.SemiBold)
                                Text(latest?.lessonId?.substringAfterLast('-')?.let { "Урок $it · 第 $it 课" } ?: "从第一课开始", style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
                                RusProgressBar(latest?.progress ?: 0f)
                                Text(if (latest == null) "查看课程" else "继续上次学习 →", color = RusMorphColors.Primary)
                            }
                        }
                    }
                }
            }
            RusSectionTitle("今日")
            RusCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    RusStat(stats.dueReviewCount.toString(), "待复习")
                    RusStat(stats.pronunciationCount.toString(), "朗读")
                    RusStat((stats.studySeconds / 60).toString(), "分钟")
                }
            }
            if (stats.dueReviewCount > 0) RusButton("开始今日复习", onReview, Modifier.fillMaxWidth())
            RusSectionTitle("快速入口")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RusButton("查词", onDictionary, Modifier.weight(1f))
                RusButton("自由朗读", onPronunciation, Modifier.weight(1f))
                RusButton("课程", onCourses, Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun CoursesScreen(state: Loadable<List<Course>>, onCourse: (String) -> Unit, onBottom: (BottomDestination) -> Unit) {
    LearningScaffold("课程", BottomDestination.Courses, onBottom) { root ->
        ContentColumn(root) {
            RusSectionTitle("我的课程", "按课程与课次组织长期学习")
            when (state) {
                Loadable.Loading -> CircularProgressIndicator()
                is Loadable.Error -> RusEmptyState("课程加载失败", state.message)
                is Loadable.Content -> if (state.value.isEmpty()) RusEmptyState("暂无课程", "课程库中还没有可用内容") else state.value.forEach { RusCourseCard(it, { onCourse(it.id) }) }
            }
        }
    }
}

@Composable
fun CourseDetailScreen(course: Course?, lessons: Loadable<List<Lesson>>, onLesson: (String, String) -> Unit, onBack: () -> Unit) {
    LearningScaffold(course?.title ?: "课程", onBack = onBack) { root ->
        ContentColumn(root) {
            course?.let { RusSectionTitle(it.subtitle, it.description); RusProgressBar(it.progress) }
            RusSectionTitle("Lesson", "按真实内容动态展示学习单元")
            when (lessons) {
                Loadable.Loading -> CircularProgressIndicator()
                is Loadable.Error -> RusEmptyState("课次加载失败", lessons.message)
                is Loadable.Content -> lessons.value.forEach { lesson -> RusLessonCard(lesson, { onLesson(lesson.courseId, lesson.id) }) }
            }
        }
    }
}

@Composable
fun LessonDetailScreen(state: Loadable<Lesson>, onUnit: (Lesson, LearningUnitType) -> Unit, onBack: () -> Unit, onAI: () -> Unit) {
    LearningScaffold("Lesson", onBack = onBack, onAI = onAI) { root ->
        ContentColumn(root) {
            when (state) {
                Loadable.Loading -> CircularProgressIndicator()
                is Loadable.Error -> RusEmptyState("无法打开课程", state.message)
                is Loadable.Content -> {
                    val lesson = state.value
                    Text("${lesson.courseId}  /  ${lesson.titleRu}", color = RusMorphColors.Primary, style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
                    RusSectionTitle(lesson.titleRu, lesson.titleZh)
                    RusProgressBar(lesson.progress)
                    Spacer(Modifier.height(4.dp))
                    RusSectionTitle("Learning Path")
                    lesson.units.forEachIndexed { index, unit -> RusLearningUnitCard(index + 1, unit, { onUnit(lesson, unit.type) }) }
                }
            }
        }
    }
}

@Composable
fun VocabularyScreen(
    state: Loadable<List<LexiconEntryWithDetails>>, lessonId: String, onWord: (String) -> Unit,
    onAddReview: (String) -> Unit, onWordAI: (String) -> Unit, onBack: () -> Unit, onAI: () -> Unit,
) {
    var selected by remember { mutableStateOf<LexiconEntryWithDetails?>(null) }
    selected?.let { item ->
        RusBottomSheet(onDismiss = { selected = null }) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(item.entry.displayForm, style = androidx.compose.material3.MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Text(item.partsOfSpeech.joinToString(" · ") { it.partOfSpeech }.ifBlank { "词性未记录" }, color = RusMorphColors.Primary)
                Text(item.entry.chineseMeaning ?: "本地词典未记录中文释义", color = RusMorphColors.TextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RusButton("详情", { selected = null; onWord(item.entry.id) }, Modifier.weight(1f))
                    RusButton("加入复习", { onAddReview(item.entry.id); selected = null }, Modifier.weight(1f))
                }
                RusButton("✦ Ask AI", { selected = null; onWordAI(item.entry.id) }, Modifier.fillMaxWidth())
            }
        }
    }
    LearningScaffold("СЛОВА · 词汇", onBack = onBack, onAI = onAI) { root ->
        ContentColumn(root) {
            RusContextChip("${lessonId.substringAfterLast('-')} 课", {})
            RusSectionTitle("本课生词", "点击词语查看本地词典详情")
            when (state) {
                Loadable.Loading -> CircularProgressIndicator()
                is Loadable.Error -> RusEmptyState("词汇加载失败", state.message)
                is Loadable.Content -> if (state.value.isEmpty()) RusEmptyState("本课没有词汇", "没有显示虚构内容") else state.value.forEach { item ->
                    RusWordChip(item.entry.displayForm, item.entry.chineseMeaning, { selected = item }, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
fun DialogueScreen(state: Loadable<Dialogue>, onPractice: (String, String) -> Unit, onBack: () -> Unit, onAI: () -> Unit) {
    LearningScaffold("ДИАЛОГ · 对话", onBack = onBack, onAI = onAI) { root ->
        ContentColumn(root) {
            when (state) {
                Loadable.Loading -> CircularProgressIndicator()
                is Loadable.Error -> RusEmptyState("对话加载失败", state.message)
                is Loadable.Content -> {
                    val dialogue = state.value
                    RusSectionTitle(dialogue.title, "教材对话 · ${dialogue.lines.size} 句")
                    dialogue.lines.forEach { line ->
                        RusCard(Modifier.fillMaxWidth()) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                line.speaker?.let { Text(it.uppercase(), color = RusMorphColors.Primary, style = androidx.compose.material3.MaterialTheme.typography.labelMedium) }
                                Text(line.text, style = androidx.compose.material3.MaterialTheme.typography.titleLarge, lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified)
                                line.translation?.let { Text(it, color = RusMorphColors.TextSecondary) }
                                TextButton(onClick = { onPractice(line.text, line.id) }) { Text("🎤 跟读") }
                            }
                        }
                    }
                    if (!dialogue.canRolePlay) RusEmptyState("角色练习尚未启用", "教材源缺少可靠说话人标注；逐句跟读仍可使用")
                }
            }
        }
    }
}

@Composable
fun ReviewScreen(stats: LearningStats, onStart: () -> Unit, onBottom: (BottomDestination) -> Unit) {
    LearningScaffold("复习", BottomDestination.Review, onBottom) { root ->
        ContentColumn(root) {
            RusSectionTitle("今日待复习", "只统计本地真实记录")
            RusCard(Modifier.fillMaxWidth()) { Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(stats.dueReviewCount.toString(), style = androidx.compose.material3.MaterialTheme.typography.displayMedium, color = RusMorphColors.Primary)
                Text("预计 ${maxOf(1, stats.dueReviewCount / 5)} 分钟", color = RusMorphColors.TextSecondary)
                RusButton("开始复习", onStart, Modifier.fillMaxWidth(), enabled = stats.dueReviewCount > 0)
            } }
            RusSectionTitle("分类")
            listOf("词汇" to stats.dueReviewCount, "语法" to 0, "朗读" to stats.pronunciationCount, "错题" to stats.mistakeCount).forEach { (label, count) ->
                RusCard(Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label); Text(count.toString(), color = RusMorphColors.Primary) } }
            }
        }
    }
}

@Composable
fun ReviewQueueScreen(items: List<ReviewItem>, onWord: (String) -> Unit, onBack: () -> Unit) {
    LearningScaffold("今日复习", onBack = onBack) { root ->
        ContentColumn(root) {
            RusSectionTitle("复习队列", "旧词卡 SRS 与通用复习项统一呈现")
            if (items.isEmpty()) RusEmptyState("今日已完成", "当前没有到期项目")
            else items.forEachIndexed { index, item ->
                RusCard(Modifier.fillMaxWidth(), onClick = { if (item.type == org.namchieh.rusmorph.domain.learning.ReviewItemType.WORD) onWord(item.sourceId) }) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text((index + 1).toString().padStart(2, '0'), color = RusMorphColors.Secondary)
                        Column(Modifier.weight(1f)) {
                            Text(item.type.name, color = RusMorphColors.Primary, fontWeight = FontWeight.SemiBold)
                            Text(item.lessonId?.substringAfterLast('-')?.let { "第 $it 课" } ?: "跨课程复习", color = RusMorphColors.TextSecondary)
                        }
                        Text("开始 ›", color = RusMorphColors.Primary)
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileScreen(stats: LearningStats, onSettings: () -> Unit, onBottom: (BottomDestination) -> Unit) {
    LearningScaffold("我的", BottomDestination.Profile, onBottom) { root ->
        ContentColumn(root) {
            RusSectionTitle("学习档案", "所有统计来自本机学习记录")
            RusCard(Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                RusStat(stats.favoriteWordCount.toString(), "收藏词汇")
                RusStat(stats.pronunciationCount.toString(), "朗读句数")
                RusStat(stats.dueReviewCount.toString(), "待复习")
            } }
            RusCard(Modifier.fillMaxWidth()) { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("学习时间", color = RusMorphColors.TextSecondary); Text("${stats.studySeconds / 60} 分钟", style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
            } }
            RusButton("设置与 Teaching Tools", onSettings, Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun UnavailableContentScreen(title: String, onBack: () -> Unit) {
    LearningScaffold(title, onBack = onBack) { root ->
        Box(root.padding(20.dp), contentAlignment = Alignment.Center) {
            RusEmptyState("内容尚未导入", "此路由已保留；获得可靠教材来源后即可接入，不会展示 AI 生成的假内容。")
        }
    }
}

@Composable
private fun ContentColumn(root: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(root.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp).widthIn(max = 760.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
}

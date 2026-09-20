package org.namchieh.rusmorph.ui.screen.learning

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import org.namchieh.rusmorph.domain.textbook.LessonTextContent
import org.namchieh.rusmorph.ui.components.RusButton
import org.namchieh.rusmorph.ui.components.RusBottomSheet
import org.namchieh.rusmorph.ui.components.RusCard
import org.namchieh.rusmorph.ui.components.RusCircleActionButton
import org.namchieh.rusmorph.ui.components.RusContextChip
import org.namchieh.rusmorph.ui.components.RusCourseCard
import org.namchieh.rusmorph.ui.components.RusEmptyState
import org.namchieh.rusmorph.ui.components.RusLearningUnitCard
import org.namchieh.rusmorph.ui.components.RusLessonCard
import org.namchieh.rusmorph.ui.components.RusNeedleCurve
import org.namchieh.rusmorph.ui.components.RusPillBadge
import org.namchieh.rusmorph.ui.components.RusProgressBar
import org.namchieh.rusmorph.ui.components.RusSectionTitle
import org.namchieh.rusmorph.ui.components.RusStat
import org.namchieh.rusmorph.ui.components.RusWordChip
import org.namchieh.rusmorph.ui.learning.Loadable
import org.namchieh.rusmorph.ui.navigation.BottomDestination
import org.namchieh.rusmorph.ui.navigation.RusMorphBottomBar
import org.namchieh.rusmorph.ui.theme.RusMorphColors
import org.namchieh.rusmorph.ui.theme.RusMorphTechTypography
import java.time.LocalTime
import androidx.compose.ui.unit.sp
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

    Box(Modifier.fillMaxSize().background(if (onAI != null) RusMorphColors.CarbonBlack else RusMorphColors.Canvas)) {
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
                Text("✦  AI 学习工作区", style = RusMorphTechTypography.MicroPill, color = RusMorphColors.AccentOrange, fontWeight = FontWeight.Bold)
                Text("AI 助教工作区", style = MaterialTheme.typography.headlineMedium, color = RusMorphColors.TextOnDark, fontWeight = FontWeight.SemiBold)
                Text("当前上下文 · $title", color = RusMorphColors.TextOnDark.copy(alpha = .74f), style = RusMorphTechTypography.MicroPill)
                Text(if (progress >= 1f) "松开进入 AI" else "继续向下滑动", color = if (progress >= 1f) RusMorphColors.AccentOrange else RusMorphColors.TextOnDark, style = RusMorphTechTypography.MicroPill)
                Box(Modifier.width(52.dp).height(3.dp).graphicsLayer { scaleX = progress.coerceAtLeast(.12f) }.background(RusMorphColors.AccentOrange))
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
                    navigationIcon = { if (onBack != null) TextButton(onClick = onBack) { Text("←", color = RusMorphColors.CarbonBlack) } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = RusMorphColors.Canvas),
                )
            },
            bottomBar = { if (selected != null) RusMorphBottomBar(selected, onBottom) },
        ) { padding -> content(Modifier.fillMaxSize().padding(padding)) }
    }
}

@Composable
fun HomeScreen(
    coursesState: Loadable<List<Course>>,
    stats: LearningStats,
    progress: List<LearningProgress>,
    selectedCourseId: String? = null,
    onSelectCourse: (String) -> Unit = {},
    onCourse: (String) -> Unit,
    onContinue: (String, String) -> Unit,
    onDictionary: () -> Unit,
    onPronunciation: () -> Unit,
    onCourses: () -> Unit = {},
    onReview: () -> Unit,
    onBottom: (BottomDestination) -> Unit,
    onAI: () -> Unit,
) {
    LearningScaffold("学习首页", BottomDestination.Home, onBottom, onAI = onAI) { root ->
        Column(
            root.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 顶部微标状态栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("全员俄人 WeRus", style = RusMorphTechTypography.MicroPill, color = RusMorphColors.TextPrimary, fontWeight = FontWeight.Bold)
                    Text("研学 · 朗读 · 复习", style = RusMorphTechTypography.MicroPill, color = RusMorphColors.TextTertiary)
                }
                RusPillBadge("WeRus", containerColor = RusMorphColors.WarmCream, contentColor = RusMorphColors.CarbonBlack)
            }

            // 中央核心看板
            RusCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = RusMorphColors.Surface,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column {
                            RusStat(
                                value = stats.dueReviewCount.toString().padStart(2, '0'),
                                label = "今日待复习",
                                hasDot = true,
                                isLarge = true,
                            )
                            val retentionPercent = (stats.averageRetention * 100).toInt()
                            val badge = org.namchieh.rusmorph.domain.learning.EbbinghausRetention.getRetentionBadge(stats.averageRetention)
                            Text(
                                text = "留存率 $retentionPercent% · ${badge.first}",
                                style = RusMorphTechTypography.MicroPill,
                                color = RusMorphColors.TextSecondary,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("快速复习", style = RusMorphTechTypography.MicroPill, color = RusMorphColors.TextTertiary)
                            RusCircleActionButton(
                                onClick = if (stats.dueReviewCount > 0) onReview else onDictionary,
                                symbol = "+",
                            )
                        }
                    }

                    // 艾宾浩斯科学记忆留存率曲线
                    RusNeedleCurve(progress = stats.averageRetention)
                    Text(
                        text = "已收纳 ${stats.totalWordsInReview} 词 · 艾宾浩斯记忆模型生效中",
                        style = RusMorphTechTypography.MicroPill,
                        color = RusMorphColors.TextTertiary,
                    )
                }
            }

            // 双列模块状态卡片
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // 卡片 1: 朗读记录
                RusCard(
                    modifier = Modifier.weight(1f),
                    onClick = onPronunciation,
                    backgroundColor = RusMorphColors.Surface,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        RusPillBadge("朗读训练", containerColor = RusMorphColors.PillBackground, contentColor = RusMorphColors.TextSecondary)
                        Row(verticalAlignment = Alignment.Top) {
                            Text(
                                text = stats.pronunciationCount.toString().padStart(2, '0'),
                                style = RusMorphTechTypography.StatDigit,
                                color = RusMorphColors.TextPrimary,
                            )
                            Text(
                                text = " ▴",
                                fontSize = 12.sp,
                                color = RusMorphColors.AccentOrange,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        Text("朗读跟读记录", style = RusMorphTechTypography.MicroPill, color = RusMorphColors.TextTertiary)
                    }
                }

                // 卡片 2: 研习状态
                RusCard(
                    modifier = Modifier.weight(1f),
                    onClick = onReview,
                    backgroundColor = RusMorphColors.Surface,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        RusPillBadge("研习状态", containerColor = RusMorphColors.PillBackground, contentColor = RusMorphColors.TextSecondary)
                        Row(verticalAlignment = Alignment.Top) {
                            Text(
                                text = (stats.studySeconds / 60).toString().padStart(2, '0'),
                                style = RusMorphTechTypography.StatDigit,
                                color = RusMorphColors.TextPrimary,
                            )
                            Text(
                                text = " m",
                                fontSize = 12.sp,
                                color = RusMorphColors.TextTertiary,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        Text("累计研习时长", style = RusMorphTechTypography.MicroPill, color = RusMorphColors.TextTertiary)
                    }
                }
            }

            // Continue learning course card with course selector
            when (coursesState) {
                Loadable.Loading -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = RusMorphColors.CarbonBlack) }
                is Loadable.Error -> RusEmptyState("课程暂不可用", coursesState.message)
                is Loadable.Content -> {
                    val allCourses = coursesState.value
                    val latestActive = progress.firstOrNull { it.lessonId != null }
                    val currentCourse = allCourses.firstOrNull { it.id == selectedCourseId }
                        ?: allCourses.firstOrNull { it.id == latestActive?.courseId }
                        ?: allCourses.firstOrNull()

                    if (currentCourse != null) {
                        val latest = progress.firstOrNull { it.courseId == currentCourse.id && it.lessonId != null }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            // 课程选择切换胶囊
                            if (allCourses.size > 1) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    allCourses.forEach { c ->
                                        val isSelected = c.id == currentCourse.id
                                        Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            color = if (isSelected) RusMorphColors.CarbonBlack else RusMorphColors.Surface,
                                            contentColor = if (isSelected) RusMorphColors.WarmCream else RusMorphColors.TextSecondary,
                                            border = androidx.compose.foundation.BorderStroke(
                                                1.dp,
                                                if (isSelected) RusMorphColors.CarbonBlack else RusMorphColors.OutlineSoft,
                                            ),
                                            modifier = Modifier.clickable { onSelectCourse(c.id) },
                                        ) {
                                            Text(
                                                text = c.title,
                                                style = RusMorphTechTypography.MicroPill,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            )
                                        }
                                    }
                                }
                            }

                            RusCard(
                                Modifier.fillMaxWidth(),
                                onClick = { latest?.lessonId?.let { onContinue(currentCourse.id, it) } ?: onCourse(currentCourse.id) },
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                            RusPillBadge("当前课次", containerColor = RusMorphColors.WarmCream, contentColor = RusMorphColors.CarbonBlack)
                                            Text(
                                                latest?.lessonId?.substringAfterLast('-')?.let { "Урок $it" } ?: "Урок 01",
                                                style = RusMorphTechTypography.SmallDigit,
                                                color = RusMorphColors.AccentOrange,
                                            )
                                        }
                                        Text(
                                            "课次目录 ›",
                                            style = RusMorphTechTypography.MicroPill,
                                            color = RusMorphColors.TextTertiary,
                                            modifier = Modifier.clickable { onCourse(currentCourse.id) },
                                        )
                                    }
                                    Text(currentCourse.title, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    RusProgressBar(latest?.progress ?: 0f)
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = if ((latest?.progress ?: 0f) > 0f) "已研习 ${((latest?.progress ?: 0f) * 100).toInt()}% · 继续课次" else "尚未开始 · 进入学习",
                                            style = RusMorphTechTypography.MicroPill,
                                            color = RusMorphColors.TextSecondary,
                                        )
                                        Text("进入 ›", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = RusMorphColors.CarbonBlack)
                                    }
                                }
                            }
                        }
                    }
                }
            }


        }
    }
}

@Composable
fun CoursesScreen(state: Loadable<List<Course>>, onCourse: (String) -> Unit, onBottom: (BottomDestination) -> Unit) {
    LearningScaffold("课程", BottomDestination.Tools, onBottom) { root ->
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
            RusSectionTitle("课次内容", "按真实内容展示学习单元")
            when (lessons) {
                Loadable.Loading -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = RusMorphColors.CarbonBlack) }
                is Loadable.Error -> RusEmptyState("课次加载失败", lessons.message)
                is Loadable.Content -> lessons.value.forEach { lesson -> RusLessonCard(lesson, { onLesson(lesson.courseId, lesson.id) }) }
            }
        }
    }
}

@Composable
fun LessonDetailScreen(state: Loadable<Lesson>, onUnit: (Lesson, LearningUnitType) -> Unit, onBack: () -> Unit, onAI: () -> Unit, onTextbook: ((Lesson) -> Unit)? = null) {
    val topTitle = when (state) {
        is Loadable.Content -> state.value.titleZh
        else -> "课次详情"
    }
    LearningScaffold(topTitle, onBack = onBack, onAI = onAI) { root ->
        ContentColumn(root) {
            when (state) {
                Loadable.Loading -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = RusMorphColors.CarbonBlack) }
                is Loadable.Error -> RusEmptyState("无法打开课程", state.message)
                is Loadable.Content -> {
                    val lesson = state.value
                    Text(lesson.titleRu, color = RusMorphColors.CarbonBlack, style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
                    RusSectionTitle(lesson.titleRu, lesson.titleZh)
                    RusProgressBar(lesson.progress)
                    onTextbook?.let { openTextbook -> RusButton("📖 课文", { openTextbook(lesson) }, Modifier.fillMaxWidth()) }
                    Spacer(Modifier.height(4.dp))
                    RusSectionTitle("学习单元", "点击进入专项练习与跟读")
                    lesson.units.forEachIndexed { index, unit -> RusLearningUnitCard(index + 1, unit, { onUnit(lesson, unit.type) }) }
                }
            }
        }
    }
}

@Composable
fun LessonTextbookScreen(state: Loadable<LessonTextContent>, onBack: () -> Unit) {
    LearningScaffold("📖 课文", onBack = onBack) { root ->
        ContentColumn(root) {
            when (state) {
                Loadable.Loading -> CircularProgressIndicator(color = RusMorphColors.CarbonBlack)
                is Loadable.Error -> RusEmptyState("课文尚未导入", state.message)
                is Loadable.Content -> {
                    val content = state.value
                    RusSectionTitle(content.textbook.title, "${content.lesson.title} · 第 ${content.lesson.lessonNumber} 课")
                    content.sections.forEach { section ->
                        section.title?.let { Text(it, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = RusMorphColors.CarbonBlack) }
                        RusCard(Modifier.fillMaxWidth()) {
                            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                section.paragraphs.forEach { paragraph ->
                                    Text(paragraph.content, style = MaterialTheme.typography.bodyLarge, lineHeight = 29.sp, color = RusMorphColors.TextPrimary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VocabularyScreen(
    state: Loadable<List<LexiconEntryWithDetails>>,
    lessonId: String,
    enrolledSourceIds: Set<String> = emptySet(),
    onWord: (String) -> Unit,
    onAddReview: (String) -> Unit,
    onRemoveReview: (String) -> Unit = {},
    onBatchAddReview: (List<String>) -> Unit = {},
    onWordAI: (String) -> Unit,
    onBack: () -> Unit,
    onAI: () -> Unit,
) {
    var selected by remember { mutableStateOf<LexiconEntryWithDetails?>(null) }
    selected?.let { item ->
        val isEnrolled = item.entry.id in enrolledSourceIds
        RusBottomSheet(onDismiss = { selected = null }) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(item.entry.displayForm, style = androidx.compose.material3.MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Text(item.partsOfSpeech.joinToString(" · ") { it.partOfSpeech }.ifBlank { "词性未记录" }, color = RusMorphColors.Primary)
                Text(item.entry.chineseMeaning ?: "本地词典未记录中文释义", color = RusMorphColors.TextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RusButton("详情", { selected = null; onWord(item.entry.id) }, Modifier.weight(1f))
                    if (isEnrolled) {
                        RusButton("移出复习", { onRemoveReview(item.entry.id); selected = null }, Modifier.weight(1f))
                    } else {
                        RusButton("加入复习", { onAddReview(item.entry.id); selected = null }, Modifier.weight(1f))
                    }
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
                is Loadable.Content -> {
                    val list = state.value
                    if (list.isEmpty()) {
                        RusEmptyState("本课没有词汇", "没有显示虚构内容")
                    } else {
                        val unenrolled = list.filter { it.entry.id !in enrolledSourceIds }
                        val enrolledCount = list.size - unenrolled.size
                        RusCard(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        "已收纳 $enrolledCount / ${list.size} 词",
                                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = RusMorphColors.TextPrimary,
                                    )
                                    Text(
                                        "艾宾浩斯复习计划",
                                        style = RusMorphTechTypography.MicroPill,
                                        color = RusMorphColors.TextTertiary,
                                    )
                                }
                                if (unenrolled.isNotEmpty()) {
                                    RusButton(
                                        text = "📥 一键收纳 (${unenrolled.size})",
                                        onClick = { onBatchAddReview(unenrolled.map { it.entry.id }) },
                                    )
                                }
                            }
                        }

                        list.forEach { item ->
                            val isEnrolled = item.entry.id in enrolledSourceIds
                            RusWordChip(
                                text = item.entry.displayForm,
                                meaning = item.entry.chineseMeaning,
                                onClick = { selected = item },
                                modifier = Modifier.fillMaxWidth(),
                                badgeText = if (isEnrolled) "✓ 复习中" else "待学习",
                                isHighlighted = isEnrolled,
                            )
                        }
                    }
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
                Loadable.Loading -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = RusMorphColors.CarbonBlack) }
                is Loadable.Error -> RusEmptyState("对话加载失败", state.message)
                is Loadable.Content -> {
                    val dialogue = state.value
                    if (dialogue.lines.isEmpty()) {
                        RusSectionTitle(dialogue.title, "第二册教材对话 · 录入准备中")
                        RusEmptyState(
                            "第二册课文对话录入中",
                            "本课对话语料正在整理录入中；本课词汇表已全量就绪，支持完整的发音跟读与词法检索。",
                        )
                    } else {
                        RusSectionTitle(dialogue.title, "教材对话 · ${dialogue.lines.size} 句")
                        dialogue.lines.forEach { line ->
                            RusCard(Modifier.fillMaxWidth()) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    line.speaker?.let { Text(it.uppercase(), color = RusMorphColors.CarbonBlack, style = androidx.compose.material3.MaterialTheme.typography.labelMedium) }
                                    Text(line.text, style = androidx.compose.material3.MaterialTheme.typography.titleLarge, lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified)
                                    line.translation?.let { Text(it, color = RusMorphColors.TextSecondary) }
                                    TextButton(onClick = { onPractice(line.text, line.id) }) { Text("🎤 跟读", color = RusMorphColors.AccentOrange) }
                                }
                            }
                        }
                        if (!dialogue.canRolePlay) RusEmptyState("角色练习尚未启用", "教材源缺少可靠说话人标注；逐句跟读仍可使用")
                    }
                }
            }
        }
    }
}

@Composable
fun ReviewScreen(stats: LearningStats, onStart: () -> Unit, onBottom: (BottomDestination) -> Unit) {
    LearningScaffold("复习", BottomDestination.Review, onBottom) { root ->
        ContentColumn(root) {
            RusSectionTitle("今日待复习", "艾宾浩斯记忆模型智能排程")
            RusCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column {
                            RusStat(stats.dueReviewCount.toString().padStart(2, '0'), "待复习", isLarge = true, hasDot = true)
                            val retentionPercent = (stats.averageRetention * 100).toInt()
                            val badge = org.namchieh.rusmorph.domain.learning.EbbinghausRetention.getRetentionBadge(stats.averageRetention)
                            Text(
                                text = "留存率 $retentionPercent% · ${badge.first}",
                                style = RusMorphTechTypography.MicroPill,
                                color = RusMorphColors.TextSecondary,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        RusPillBadge("预计 ${maxOf(1, stats.dueReviewCount / 5)} 分钟", containerColor = RusMorphColors.WarmCream, contentColor = RusMorphColors.CarbonBlack)
                    }

                    // 艾宾浩斯科学记忆留存率曲线
                    RusNeedleCurve(progress = stats.averageRetention)
                    Text(
                        text = "已收纳 ${stats.totalWordsInReview} 词 · 艾宾浩斯记忆模型生效中",
                        style = RusMorphTechTypography.MicroPill,
                        color = RusMorphColors.TextTertiary,
                    )

                    RusButton("开始复习", onStart, Modifier.fillMaxWidth(), enabled = stats.dueReviewCount > 0)
                }
            }
            RusSectionTitle("分类")
            listOf("词汇" to stats.dueReviewCount, "语法" to 0, "朗读" to stats.pronunciationCount, "错题" to stats.mistakeCount).forEach { (label, count) ->
                RusCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(label, fontWeight = FontWeight.Medium, color = RusMorphColors.TextPrimary)
                        Text(
                            count.toString().padStart(2, '0'),
                            style = RusMorphTechTypography.SmallDigit,
                            color = if (count > 0) RusMorphColors.AccentOrange else RusMorphColors.TextTertiary,
                        )
                    }
                }
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
                val typeLabel = when (item.type) {
                    org.namchieh.rusmorph.domain.learning.ReviewItemType.WORD -> "生词复习"
                    org.namchieh.rusmorph.domain.learning.ReviewItemType.GRAMMAR -> "语法复习"
                    org.namchieh.rusmorph.domain.learning.ReviewItemType.TEXT -> "课文复习"
                    org.namchieh.rusmorph.domain.learning.ReviewItemType.SENTENCE -> "句型复习"
                    org.namchieh.rusmorph.domain.learning.ReviewItemType.DIALOGUE -> "对话复习"
                    org.namchieh.rusmorph.domain.learning.ReviewItemType.EXERCISE -> "练习复习"
                }
                RusCard(Modifier.fillMaxWidth(), onClick = { if (item.type == org.namchieh.rusmorph.domain.learning.ReviewItemType.WORD) onWord(item.sourceId) }) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text((index + 1).toString().padStart(2, '0'), style = RusMorphTechTypography.SmallDigit, color = RusMorphColors.AccentOrange)
                        Column(Modifier.weight(1f)) {
                            Text(typeLabel, color = RusMorphColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                            Text(item.lessonId?.substringAfterLast('-')?.let { "第 $it 课" } ?: "跨课程复习", style = RusMorphTechTypography.MicroPill, color = RusMorphColors.TextSecondary)
                        }
                        Text("开始 ›", color = RusMorphColors.CarbonBlack, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
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
            RusCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    RusStat(stats.favoriteWordCount.toString().padStart(2, '0'), "收藏词汇", hasDot = false)
                    RusStat(stats.pronunciationCount.toString().padStart(2, '0'), "朗读句数", hasDot = true)
                    RusStat(stats.dueReviewCount.toString().padStart(2, '0'), "待复习", hasDot = true)
                }
            }
            RusCard(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        RusPillBadge("累计时长", containerColor = RusMorphColors.WarmCream, contentColor = RusMorphColors.CarbonBlack)
                        Text("${stats.studySeconds / 60} 分钟", style = androidx.compose.material3.MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        (stats.studySeconds / 60).toString().padStart(2, '0'),
                        style = RusMorphTechTypography.StatDigit,
                        color = RusMorphColors.AccentOrange,
                    )
                }
            }
            RusButton("系统设置与教学工具", onSettings, Modifier.fillMaxWidth())
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

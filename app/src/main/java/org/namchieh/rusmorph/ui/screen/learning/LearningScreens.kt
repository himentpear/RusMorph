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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import org.namchieh.rusmorph.ui.design.WerusColors
import org.namchieh.rusmorph.ui.design.WerusTypography
import org.namchieh.rusmorph.ui.screen.settings.WallpaperImage
import org.namchieh.rusmorph.ui.theme.RusMorphTechTypography
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
    val quickNavigation = LocalLearningQuickNavigation.current
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

    Box(Modifier.fillMaxSize().background(if (onAI != null) WerusColors.Ink else WerusColors.Canvas)) {
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
                Text("✦  AI 学习工作区", style = RusMorphTechTypography.MicroPill, color = WerusColors.Red, fontWeight = FontWeight.Bold)
                Text("AI 助教工作区", style = MaterialTheme.typography.headlineMedium, color = WerusColors.OnDark, fontWeight = FontWeight.SemiBold)
                Text("当前上下文 · $title", color = WerusColors.OnDark.copy(alpha = .74f), style = RusMorphTechTypography.MicroPill)
                Text(if (progress >= 1f) "松开进入 AI" else "继续向下滑动", color = if (progress >= 1f) WerusColors.Red else WerusColors.OnDark, style = RusMorphTechTypography.MicroPill)
                Box(Modifier.width(52.dp).height(3.dp).graphicsLayer { scaleX = progress.coerceAtLeast(.12f) }.background(WerusColors.Red))
            }
        }

        Scaffold(
            modifier = Modifier
                .then(if (onAI != null) Modifier.nestedScroll(connection) else Modifier)
                .graphicsLayer {
                    translationY = reveal
                    shadowElevation = if (reveal > 0f) 18.dp.toPx() else 0f
                },
            containerColor = WerusColors.Canvas,
            topBar = {
                if (selected == null && (onBack != null || quickNavigation != null)) {
                    Column(Modifier.fillMaxWidth().background(WerusColors.Paper)) {
                        Row(
                            Modifier.fillMaxWidth().statusBarsPadding().heightIn(min = 54.dp).padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(
                                onClick = { (onBack ?: quickNavigation?.back)?.invoke() },
                                modifier = Modifier.semantics { contentDescription = "返回上一级" },
                            ) { Text("←", style = WerusTypography.Title, color = WerusColors.Ink) }
                            Text(
                                title,
                                modifier = Modifier.weight(1f),
                                style = WerusTypography.Subtitle,
                                color = WerusColors.Ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            quickNavigation?.let { actions ->
                                TextButton(onClick = actions.learning) { Text("学习", color = WerusColors.RedDark) }
                                TextButton(onClick = actions.home) { Text("首页", color = WerusColors.InkMuted) }
                            }
                        }
                        HorizontalDivider(color = WerusColors.BorderSoft)
                    }
                }
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
    onCourse: (String) -> Unit,
    onContinue: (String, String, LearningUnitType?) -> Unit,
    onReview: () -> Unit,
    onBottom: (BottomDestination) -> Unit,
    onAI: () -> Unit,
    wallpaper: String,
) {
    LearningScaffold("首页", BottomDestination.Home, onBottom, onAI = onAI) { root ->
        Box(root) {
            WallpaperImage(wallpaper, Modifier.matchParentSize())
            Box(
                Modifier.matchParentSize().background(
                    Brush.verticalGradient(
                        listOf(WerusColors.Paper.copy(alpha = .91f), WerusColors.Canvas.copy(alpha = .77f)),
                    ),
                ),
            )
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("WERUS  /  DAILY STUDY", style = WerusTypography.Metadata, color = WerusColors.RedDark)
                        TextButton(onClick = onAI) { Text("AI 答疑 ✦", color = WerusColors.RedDark) }
                    }
                    Text("今天，从一课开始", style = WerusTypography.Display, color = WerusColors.Ink)
                }
                RusSectionTitle("继续学习")
                when (coursesState) {
                    Loadable.Loading -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = WerusColors.Ink) }
                    is Loadable.Error -> RusEmptyState("课程暂不可用", coursesState.message)
                    is Loadable.Content -> {
                        val allCourses = coursesState.value
                        val latestActive = progress.filter { it.lessonId != null }.maxByOrNull { it.updatedAt }
                        val currentCourse = allCourses.firstOrNull { it.id == selectedCourseId }
                            ?: allCourses.firstOrNull { it.id == latestActive?.courseId }
                            ?: allCourses.firstOrNull()

                        if (currentCourse != null) {
                            val latest = progress.filter { it.courseId == currentCourse.id && it.lessonId != null }
                                .maxByOrNull { it.updatedAt }
                            RusCard(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { latest?.lessonId?.let { onContinue(currentCourse.id, it, latest?.unitType) } ?: onCourse(currentCourse.id) },
                                backgroundColor = WerusColors.Paper,
                                border = androidx.compose.foundation.BorderStroke(1.dp, WerusColors.RedSoft),
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        RusPillBadge(
                                            latest?.lessonId?.substringAfterLast('-')?.let { "第 $it 课" } ?: "我的教材",
                                            containerColor = WerusColors.RedSoft,
                                            contentColor = WerusColors.RedDark,
                                        )
                                        Text("继续  →", style = WerusTypography.Subtitle, color = WerusColors.RedDark)
                                    }
                                    Text(currentCourse.title, style = WerusTypography.Title, color = WerusColors.Ink)
                                    if (latest != null && latest.progress > 0f) RusProgressBar(latest.progress)
                                }
                            }
                        } else RusEmptyState("暂无教材", "前往学习页选择教材")
                    }
                }

                RusSectionTitle("今日复习")
                RusCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onReview.takeIf { stats.dueReviewCount > 0 },
                    backgroundColor = WerusColors.BeigeMuted,
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("${stats.dueReviewCount} 项待复习", style = WerusTypography.Title, color = WerusColors.Ink)
                            if (stats.dueReviewCount == 0) Text("今日已完成", style = WerusTypography.Caption, color = WerusColors.InkMuted)
                        }
                        if (stats.dueReviewCount > 0) Text("开始  →", style = WerusTypography.Subtitle, color = WerusColors.RedDark)
                    }
                }
            }
        }
    }
}

@Composable
fun CoursesScreen(state: Loadable<List<Course>>, onCourse: (String) -> Unit, onBottom: (BottomDestination) -> Unit) {
    LearningScaffold("教材", BottomDestination.Learning, onBottom) { root ->
        ContentColumn(root) {
            RusSectionTitle("我的课程")
            when (state) {
                Loadable.Loading -> CircularProgressIndicator()
                is Loadable.Error -> RusEmptyState("课程加载失败", state.message)
                is Loadable.Content -> if (state.value.isEmpty()) RusEmptyState("暂无课程", "课程库中还没有可用内容") else state.value.forEach { RusCourseCard(it, { onCourse(it.id) }) }
            }
        }
    }
}

@Composable
fun CourseDetailScreen(course: Course?, lessons: Loadable<List<Lesson>>, onUnit: (Lesson, LearningUnitType) -> Unit, onBack: () -> Unit) {
    var expandedLessonId by rememberSaveable { mutableStateOf<String?>(null) }
    LearningScaffold(course?.title ?: "课程", onBack = onBack) { root ->
        ContentColumn(root) {
            RusSectionTitle("课次")
            when (lessons) {
                Loadable.Loading -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = WerusColors.Ink) }
                is Loadable.Error -> RusEmptyState("课次加载失败", lessons.message)
                is Loadable.Content -> lessons.value.forEach { lesson ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val expanded = expandedLessonId == lesson.id
                        RusLessonCard(lesson, { expandedLessonId = if (expanded) null else lesson.id }, expanded = expanded)
                        if (expanded) {
                            lesson.units.forEachIndexed { index, unit ->
                                RusLearningUnitCard(index + 1, unit, { onUnit(lesson, unit.type) }, Modifier.padding(start = 12.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LessonDetailScreen(state: Loadable<Lesson>, onUnit: (Lesson, LearningUnitType) -> Unit, onBack: () -> Unit, onAI: () -> Unit) {
    val topTitle = when (state) {
        is Loadable.Content -> state.value.titleZh
        else -> "课次详情"
    }
    LearningScaffold(topTitle, onBack = onBack, onAI = onAI) { root ->
        ContentColumn(root) {
            when (state) {
                Loadable.Loading -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = WerusColors.Ink) }
                is Loadable.Error -> RusEmptyState("无法打开课程", state.message)
                is Loadable.Content -> {
                    val lesson = state.value
                    RusSectionTitle("学习单元")
                    lesson.units.forEachIndexed { index, unit -> RusLearningUnitCard(index + 1, unit, { onUnit(lesson, unit.type) }) }
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
                Text(item.partsOfSpeech.joinToString(" · ") { it.partOfSpeech }.ifBlank { "词性未记录" }, color = WerusColors.Red)
                Text(item.entry.chineseMeaning ?: "本地词典未记录中文释义", color = WerusColors.InkMuted)
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
    LearningScaffold("第 ${lessonId.substringAfterLast('-')} 课 · 词汇", onBack = onBack, onAI = onAI) { root ->
        ContentColumn(root) {
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
                                        color = WerusColors.Ink,
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
                Loadable.Loading -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = WerusColors.Ink) }
                is Loadable.Error -> RusEmptyState("对话加载失败", state.message)
                is Loadable.Content -> {
                    val dialogue = state.value
                    if (dialogue.lines.isEmpty()) {
                        RusSectionTitle(dialogue.title)
                        RusEmptyState(
                            "本课暂无对话正文",
                            "教材数据仍在补全",
                        )
                    } else {
                        RusSectionTitle(dialogue.title)
                        dialogue.lines.forEach { line ->
                            RusCard(Modifier.fillMaxWidth()) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    line.speaker?.let { Text(it.uppercase(), color = WerusColors.Ink, style = androidx.compose.material3.MaterialTheme.typography.labelMedium) }
                                    Text(line.text, style = androidx.compose.material3.MaterialTheme.typography.titleLarge, lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified)
                                    line.translation?.let { Text(it, color = WerusColors.InkMuted) }
                                    TextButton(onClick = { onPractice(line.text, line.id) }) { Text("🎤 跟读", color = WerusColors.Red) }
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
            RusSectionTitle("今日待复习")
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
                                color = WerusColors.InkMuted,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        RusPillBadge("预计 ${maxOf(1, stats.dueReviewCount / 5)} 分钟", containerColor = WerusColors.Beige, contentColor = WerusColors.Ink)
                    }

                    // 艾宾浩斯科学记忆留存率曲线
                    RusNeedleCurve(progress = stats.averageRetention)

                    RusButton("开始复习", onStart, Modifier.fillMaxWidth(), enabled = stats.dueReviewCount > 0)
                }
            }
            RusSectionTitle("分类")
            listOf("词汇" to stats.dueReviewCount, "语法" to 0, "朗读" to stats.pronunciationCount, "错题" to stats.mistakeCount).forEach { (label, count) ->
                RusCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(label, fontWeight = FontWeight.Medium, color = WerusColors.Ink)
                        Text(
                            count.toString().padStart(2, '0'),
                            style = RusMorphTechTypography.SmallDigit,
                            color = if (count > 0) WerusColors.Red else WerusColors.InkFaint,
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
            RusSectionTitle("复习队列")
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
                val canOpen = item.type == org.namchieh.rusmorph.domain.learning.ReviewItemType.WORD
                RusCard(Modifier.fillMaxWidth(), onClick = if (canOpen) ({ onWord(item.sourceId) }) else null) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text((index + 1).toString().padStart(2, '0'), style = RusMorphTechTypography.SmallDigit, color = WerusColors.Red)
                        Column(Modifier.weight(1f)) {
                            Text(typeLabel, color = WerusColors.Ink, fontWeight = FontWeight.SemiBold)
                            Text(item.lessonId?.substringAfterLast('-')?.let { "第 $it 课" } ?: "跨课程复习", style = RusMorphTechTypography.MicroPill, color = WerusColors.InkMuted)
                        }
                        if (canOpen) Text("打开 ›", color = WerusColors.Ink, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
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
                        RusPillBadge("累计时长", containerColor = WerusColors.Beige, contentColor = WerusColors.Ink)
                        Text("${stats.studySeconds / 60} 分钟", style = androidx.compose.material3.MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        (stats.studySeconds / 60).toString().padStart(2, '0'),
                        style = RusMorphTechTypography.StatDigit,
                        color = WerusColors.Red,
                    )
                }
            }
            RusButton("设置", onSettings, Modifier.fillMaxWidth())
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

package org.namchieh.rusmorph.ui.screen.tools

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.namchieh.rusmorph.ui.components.RusCard
import org.namchieh.rusmorph.ui.components.RusPillBadge
import org.namchieh.rusmorph.ui.components.RusSectionTitle
import org.namchieh.rusmorph.ui.design.WerusColors
import org.namchieh.rusmorph.ui.theme.RusMorphTechTypography
import org.namchieh.rusmorph.wordcard.model.*

/**
 * 俄语变格变位规则表 (MorphologyRulesScreen)。
 * 纯通用语法规则总表，面向初学者友好设计（以词尾为入口，词干与词尾高亮分离）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MorphologyRulesScreen(
    onBack: () -> Unit,
    initialCategory: String? = null,
    initialRuleId: String? = null,
    modifier: Modifier = Modifier,
) {
    var selectedCategory by remember {
        mutableStateOf(
            when (initialCategory?.uppercase()) {
                "ADJECTIVE", "ADJ" -> MorphologyCategory.ADJECTIVE
                "PRONOUN", "PRON" -> MorphologyCategory.PRONOUN
                "VERB" -> MorphologyCategory.VERB
                else -> MorphologyCategory.NOUN
            }
        )
    }

    var isBeginnerMode by remember { mutableStateOf(true) }
    var isCompactMode by remember { mutableStateOf(false) }
    var highlightedRuleId by remember { mutableStateOf(initialRuleId) }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        containerColor = WerusColors.Canvas,
        modifier = modifier,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("俄语变格变位", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Склонение · Спряжение", style = RusMorphTechTypography.MicroPill, color = WerusColors.InkFaint)
                }
                FilterChip(
                    selected = isBeginnerMode,
                    onClick = { isBeginnerMode = !isBeginnerMode },
                    label = { Text(if (isBeginnerMode) "初学者" else "进阶", style = RusMorphTechTypography.MicroPill) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = WerusColors.Ink,
                        selectedLabelColor = WerusColors.Beige,
                    ),
                )
            }
            // 顶部一级分类 Tab 切换器
            PrimaryCategoryTabs(
                selected = selectedCategory,
                onSelect = { selectedCategory = it },
            )

            // 主内容区域
            when (selectedCategory) {
                MorphologyCategory.NOUN -> {
                    NounRulesContent(
                        isBeginnerMode = isBeginnerMode,
                        isCompactMode = isCompactMode,
                        onToggleCompact = { isCompactMode = !isCompactMode },
                        highlightedRuleId = highlightedRuleId,
                        onSelectEnding = { endingId ->
                            highlightedRuleId = endingId
                            coroutineScope.launch {
                                val idx = MorphologyRulesRepository.nounRules.indexOfFirst { it.id == endingId }
                                if (idx >= 0) {
                                    listState.animateScrollToItem((idx + 2).coerceAtLeast(0))
                                }
                            }
                        },
                        listState = listState,
                    )
                }
                MorphologyCategory.ADJECTIVE -> {
                    AdjectiveRulesContent(isCompactMode = isCompactMode)
                }
                MorphologyCategory.PRONOUN -> {
                    PronounRulesContent()
                }
                MorphologyCategory.VERB -> {
                    VerbRulesContent(isCompactMode = isCompactMode)
                }
            }
        }
    }
}

// =========================================================================
// 一级分类标签栏
// =========================================================================
@Composable
private fun PrimaryCategoryTabs(
    selected: MorphologyCategory,
    onSelect: (MorphologyCategory) -> Unit,
) {
    Surface(
        color = WerusColors.Canvas,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MorphologyCategory.entries.forEach { category ->
                val isSelected = category == selected
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) WerusColors.Ink else WerusColors.Paper,
                    contentColor = if (isSelected) WerusColors.Beige else WerusColors.InkMuted,
                    border = BorderStroke(1.dp, if (isSelected) WerusColors.Ink else WerusColors.BorderSoft),
                    modifier = Modifier.clickable { onSelect(category) },
                ) {
                    Text(
                        text = "${category.labelZh} · ${category.labelRu}",
                        style = RusMorphTechTypography.MicroPill,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    )
                }
            }
        }
    }
}

// =========================================================================
// 1. 名词变格内容区 (NOUN)
// =========================================================================
@Composable
private fun NounRulesContent(
    isBeginnerMode: Boolean,
    isCompactMode: Boolean,
    onToggleCompact: () -> Unit,
    highlightedRuleId: String?,
    onSelectEnding: (String) -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState,
) {
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        // 头部说明与词尾过滤器
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RusSectionTitle("名词六格规律", "根据词尾形态推导变格形式")
                    Text(
                        text = if (isCompactMode) "展开释义" else "紧凑视图",
                        style = RusMorphTechTypography.MicroPill,
                        color = WerusColors.Info,
                        modifier = Modifier.clickable { onToggleCompact() },
                    )
                }

                if (isBeginnerMode) {
                    Surface(
                        color = WerusColors.Paper,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, WerusColors.BorderSoft),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "💡 初学者快速定位：这个词以什么结尾？",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = WerusColors.Ink,
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                MorphologyRulesRepository.nounRules.forEach { rule ->
                                    val isCurrent = rule.id == highlightedRuleId
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isCurrent) WerusColors.Red else WerusColors.Canvas,
                                        contentColor = if (isCurrent) Color.White else WerusColors.Ink,
                                        border = BorderStroke(1.dp, if (isCurrent) WerusColors.Red else WerusColors.BorderSoft),
                                        modifier = Modifier.clickable { onSelectEnding(rule.id) },
                                    ) {
                                        Text(
                                            text = rule.endingToken,
                                            style = RusMorphTechTypography.MicroPill,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 核心规则独立卡：有生命性与宾格
        item {
            AnimacyAccusativeCard()
        }

        // 10 类名词变格规则卡片
        items(MorphologyRulesRepository.nounRules, key = { it.id }) { rule ->
            DeclensionRuleCard(
                rule = rule,
                isHighlighted = rule.id == highlightedRuleId,
                isCompactMode = isCompactMode,
            )
        }
    }
}

// =========================================================================
// 核心规则卡：有生命性与宾格 (AnimacyAccusativeCard)
// =========================================================================
@Composable
private fun AnimacyAccusativeCard() {
    var isWhyExpanded by remember { mutableStateOf(false) }

    RusCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = WerusColors.Paper,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    RusPillBadge("核心规则", containerColor = WerusColors.Beige, contentColor = WerusColors.Ink)
                    Text("宾格与有生命性", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Text("Одушевлённость", style = RusMorphTechTypography.MicroPill, color = WerusColors.InkFaint)
            }

            Text(
                "俄语阳性名词单数及所有名词复数中，宾格（动作作用的对象）严格区分有无生命：",
                style = MaterialTheme.typography.bodySmall,
                color = WerusColors.InkMuted,
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // 📦 无生命
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = WerusColors.Canvas,
                    border = BorderStroke(1.dp, WerusColors.BorderSoft),
                    modifier = Modifier.weight(1f),
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("📦 无生命物体", style = MaterialTheme.typography.labelSmall, color = WerusColors.InkMuted)
                        Text(
                            text = "В = И (同主格)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = WerusColors.Info,
                        )
                        HorizontalDivider(color = WerusColors.BorderSoft, thickness = 0.5.dp)
                        Text("стол (桌子)", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                        Text("И. стол", style = RusMorphTechTypography.MicroPill, color = WerusColors.InkFaint)
                        Text("В. стол", style = RusMorphTechTypography.MicroPill, color = WerusColors.Info, fontWeight = FontWeight.Bold)
                    }
                }

                // 👤 有生命
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = WerusColors.Canvas,
                    border = BorderStroke(1.dp, WerusColors.BorderSoft),
                    modifier = Modifier.weight(1f),
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("👤 人或动物", style = MaterialTheme.typography.labelSmall, color = WerusColors.InkMuted)
                        Text(
                            text = "В = Р (同属格)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = WerusColors.Red,
                        )
                        HorizontalDivider(color = WerusColors.BorderSoft, thickness = 0.5.dp)
                        Text("студент (学生)", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                        Text("Р. студента", style = RusMorphTechTypography.MicroPill, color = WerusColors.InkFaint)
                        Text("В. студента", style = RusMorphTechTypography.MicroPill, color = WerusColors.Red, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // 为什么展开区
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isWhyExpanded = !isWhyExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("为什么俄语要这样区分？", style = RusMorphTechTypography.MicroPill, color = WerusColors.Info)
                Text(if (isWhyExpanded) "收起 ▲" else "展开为什么 ▼", style = RusMorphTechTypography.MicroPill, color = WerusColors.InkFaint)
            }

            AnimatedVisibility(
                visible = isWhyExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Surface(
                    color = WerusColors.Beige.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "古俄语中阳性宾格原本与主格相同。但在处理指人的句子时（例如“老师看见学生”），如果主格与宾格完全一样，极易发生混淆。为了明确谁是动作发出者、谁是被作用者，俄语借用了属格（所有格 -а/-я）来专门作为有生命名词的宾格。",
                        style = MaterialTheme.typography.bodySmall,
                        color = WerusColors.Ink,
                        modifier = Modifier.padding(10.dp),
                    )
                }
            }
        }
    }
}

// =========================================================================
// 单条名词规则卡片 (DeclensionRuleCard)
// =========================================================================
@Composable
fun DeclensionRuleCard(
    rule: MorphologyRule,
    isHighlighted: Boolean,
    isCompactMode: Boolean,
    modifier: Modifier = Modifier,
) {
    var isWhyExpanded by remember { mutableStateOf(false) }

    val borderColor by animateColorAsState(
        targetValue = if (isHighlighted) WerusColors.Red else WerusColors.BorderSoft,
        animationSpec = tween(250),
        label = "border",
    )

    RusCard(
        modifier = modifier.fillMaxWidth(),
        backgroundColor = WerusColors.Paper,
        border = BorderStroke(if (isHighlighted) 1.5.dp else 1.dp, borderColor),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // 标题与词尾徽章
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        RusPillBadge(rule.endingToken, containerColor = WerusColors.Beige, contentColor = WerusColors.Ink)
                        Text(rule.titleZh, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    rule.genderZh?.let {
                        Text(it, style = RusMorphTechTypography.MicroPill, color = WerusColors.InkMuted)
                    }
                }
                rule.traditionalLabel?.let {
                    Text(it, style = RusMorphTechTypography.MicroPill, color = WerusColors.InkFaint)
                }
            }

            // 代表词示例
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "代表词: ${rule.representativeWord}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = WerusColors.Ink,
                )
                if (rule.examples.isNotEmpty()) {
                    Text(
                        text = "常见: " + rule.examples.take(4).joinToString(" · "),
                        style = RusMorphTechTypography.MicroPill,
                        color = WerusColors.InkFaint,
                    )
                }
            }

            HorizontalDivider(color = WerusColors.BorderSoft, thickness = 0.5.dp)

            // 6格表格
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                rule.rows.forEach { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = if (row.case == RussianCase.ACCUSATIVE) WerusColors.Canvas else Color.Transparent,
                                shape = RoundedCornerShape(4.dp),
                            )
                            .padding(vertical = 3.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 格位代号与问句
                        Column(modifier = Modifier.width(100.dp)) {
                            Text(row.labelZh, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            if (!isCompactMode && row.questionZh != null) {
                                Text(row.questionZh, style = RusMorphTechTypography.MicroPill, color = WerusColors.InkFaint)
                            }
                        }

                        // 单数词尾 + 词干高亮拼接
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("单: ", style = RusMorphTechTypography.MicroPill, color = WerusColors.InkFaint)
                            Text(
                                text = buildStemEndingText(row.singularStemExample, row.singularEnding),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Serif,
                            )
                        }

                        // 复数词尾 + 词干高亮拼接
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("复: ", style = RusMorphTechTypography.MicroPill, color = WerusColors.InkFaint)
                            Text(
                                text = buildStemEndingText(row.pluralStemExample, row.pluralEnding),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Serif,
                            )
                        }
                    }
                }
            }

            // 规则为什么解释区
            if (!rule.whyExplanation.isNullOrBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isWhyExpanded = !isWhyExpanded },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("为什么？", style = RusMorphTechTypography.MicroPill, color = WerusColors.Info)
                    Text(if (isWhyExpanded) "收起 ▲" else "展开解释 ▼", style = RusMorphTechTypography.MicroPill, color = WerusColors.InkFaint)
                }

                AnimatedVisibility(
                    visible = isWhyExpanded,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    Surface(
                        color = WerusColors.Canvas,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = rule.whyExplanation,
                            style = MaterialTheme.typography.bodySmall,
                            color = WerusColors.InkMuted,
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                }
            }
        }
    }
}

// =========================================================================
// 2. 形容词变格内容区 (ADJECTIVE)
// =========================================================================
@Composable
private fun AdjectiveRulesContent(isCompactMode: Boolean) {
    var showComparison by remember { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RusSectionTitle("形容词变格规则", "形容词会跟着修饰的名词一起改变性、数、格")

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RusPillBadge("性", containerColor = WerusColors.Beige, contentColor = WerusColors.Ink)
                        RusPillBadge("数", containerColor = WerusColors.Beige, contentColor = WerusColors.Ink)
                        RusPillBadge("格", containerColor = WerusColors.Beige, contentColor = WerusColors.Ink)
                    }
                    TextButton(onClick = { showComparison = !showComparison }) {
                        Text(if (showComparison) "独立查看" else "硬/软对比 ⇄", color = WerusColors.Info, style = RusMorphTechTypography.MicroPill)
                    }
                }
            }
        }

        if (!showComparison) {
            // 硬变化卡片 (новый)
            item {
                AdjectiveCard(
                    title = "硬变化形容词 (новый 型)",
                    rep = "новый (新的)",
                    rows = MorphologyRulesRepository.adjectiveHardRows,
                    tag = "最主要规则",
                    isCompactMode = isCompactMode,
                )
            }

            // 软变化卡片 (синий)
            item {
                AdjectiveCard(
                    title = "软变化形容词 (синий 型)",
                    rep = "синий (蓝色的)",
                    rows = MorphologyRulesRepository.adjectiveSoftRows,
                    tag = "词干为软辅音",
                    isCompactMode = isCompactMode,
                )
            }
        } else {
            // 纵向对比卡片 (窄屏最友好)
            item {
                AdjectiveComparisonCard(isCompactMode = isCompactMode)
            }
        }
    }
}

@Composable
private fun AdjectiveCard(
    title: String,
    rep: String,
    rows: List<MorphologyRuleRow>,
    tag: String,
    isCompactMode: Boolean,
) {
    RusCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = WerusColors.Paper,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("代表: $rep", style = RusMorphTechTypography.MicroPill, color = WerusColors.InkMuted)
                }
                RusPillBadge(tag, containerColor = WerusColors.Canvas, contentColor = WerusColors.InkMuted)
            }

            HorizontalDivider(color = WerusColors.BorderSoft, thickness = 0.5.dp)

            rows.forEach { r ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(r.labelZh, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, modifier = Modifier.width(60.dp))
                    Text(
                        text = r.note ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Serif,
                        color = WerusColors.Ink,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun AdjectiveComparisonCard(isCompactMode: Boolean) {
    RusCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = WerusColors.Paper,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("硬变化 (новый) ⇄ 软变化 (синий) 词尾对照", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            HorizontalDivider(color = WerusColors.BorderSoft, thickness = 0.5.dp)

            val cases = listOf("И 主", "Р 属", "Д 与", "В 宾", "Т 工", "П 前")
            cases.forEachIndexed { i, c ->
                val hardRow = MorphologyRulesRepository.adjectiveHardRows.getOrNull(i)
                val softRow = MorphologyRulesRepository.adjectiveSoftRows.getOrNull(i)

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = WerusColors.Canvas,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                ) {
                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(c, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = WerusColors.Red)
                        Text("硬: ${hardRow?.note ?: ""}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Serif)
                        Text("软: ${softRow?.note ?: ""}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Serif, color = WerusColors.Info)
                    }
                }
            }
        }
    }
}

// =========================================================================
// 3. 代词六格内容区 (PRONOUN)
// =========================================================================
@Composable
private fun PronounRulesContent() {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                RusSectionTitle("人称代词六格表", "俄语人称代词具有高度不规则性，建议直接熟记整体词形")
                Surface(
                    color = WerusColors.Beige.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "📌 关键提示：在介词后，第三人称代词（он, она, оно, они）词首需加 н-（如 у него, к ней, с ними）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = WerusColors.Ink,
                        modifier = Modifier.padding(10.dp),
                    )
                }
            }
        }

        items(MorphologyRulesRepository.personalPronouns, key = { it.pronoun }) { p ->
            RusCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = WerusColors.Paper,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${p.pronoun} (${p.translationZh})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = WerusColors.Ink,
                        )
                        RusPillBadge("代词", containerColor = WerusColors.Canvas, contentColor = WerusColors.InkMuted)
                    }

                    HorizontalDivider(color = WerusColors.BorderSoft, thickness = 0.5.dp)

                    // 六格紧凑展示
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("И. ${p.nominative}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Serif)
                            Text("Р. ${p.genitive}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
                            Text("Д. ${p.dative}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Serif)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("В. ${p.accusative}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = WerusColors.Red)
                            Text("Т. ${p.instrumental}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Serif)
                            Text("П. ${p.prepositional}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Serif)
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// 4. 动词变位内容区 (VERB)
// =========================================================================
@Composable
private fun VerbRulesContent(isCompactMode: Boolean) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                RusSectionTitle("动词变位与时态", "动词根据“谁在做动作（人称）”改变词尾，按“性数”改变过去时")
            }
        }

        // 核心对比表：第一变位 ⇄ 第二变位
        item {
            VerbConjugationComparisonCard()
        }

        // 第一变位卡
        item {
            VerbCard(
                title = "第一变位法 (-ешь, -ет...)",
                rep = "читать (读)",
                summary = "以 -ать, -ять, -еть 等结尾的绝大多数动词。",
                conjugationNote = "я чита-ю, ты чита-ешь, он чита-ет, мы чита-ем, вы чита-ете, они чита-ют",
                tag = "第一变位",
            )
        }

        // 第二变位卡
        item {
            VerbCard(
                title = "第二变位法 (-ишь, -ит...)",
                rep = "говорить (说)",
                summary = "以 -ить 结尾的大多数动词及少数特殊变位词。",
                conjugationNote = "я говор-ю, ты говор-ишь, он говор-ит, мы говор-им, вы говор-ите, они говор-ят",
                mutationAlert = "⚠️ 提示：部分动词在第一人称单数 (я) 会发生词干辅音更替（如 писать → пишу, любить → люблю, видеть → вижу）。此类特殊具体形式请在各单词卡中查询。",
                tag = "第二变位",
            )
        }

        // 过去时卡片
        item {
            PastTenseCard()
        }

        // 体与将来时卡片
        item {
            AspectAndFutureCard()
        }
    }
}

@Composable
private fun VerbConjugationComparisonCard() {
    RusCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = WerusColors.Paper,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RusPillBadge("核心对比", containerColor = WerusColors.Beige, contentColor = WerusColors.Ink)
                Text("第一变位 ⇄ 第二变位对照表", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            HorizontalDivider(color = WerusColors.BorderSoft, thickness = 0.5.dp)

            // 对比列
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("人称", style = RusMorphTechTypography.MicroPill, color = WerusColors.InkFaint, modifier = Modifier.weight(1.1f))
                Text("第一变位 (читать)", style = RusMorphTechTypography.MicroPill, color = WerusColors.Info, modifier = Modifier.weight(1.3f))
                Text("第二变位 (говорить)", style = RusMorphTechTypography.MicroPill, color = WerusColors.Red, modifier = Modifier.weight(1.3f))
            }

            MorphologyRulesRepository.conjugationComparison.forEach { c ->
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = WerusColors.Canvas,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(c.person, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1.1f))
                        Text(
                            text = "${c.firstEnding} (${c.firstExample})",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Serif,
                            color = WerusColors.Info,
                            modifier = Modifier.weight(1.3f),
                        )
                        Text(
                            text = "${c.secondEnding} (${c.secondExample})",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Serif,
                            color = WerusColors.Red,
                            modifier = Modifier.weight(1.3f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VerbCard(
    title: String,
    rep: String,
    summary: String,
    conjugationNote: String,
    mutationAlert: String? = null,
    tag: String,
) {
    RusCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = WerusColors.Paper,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("典型代表: $rep", style = RusMorphTechTypography.MicroPill, color = WerusColors.InkMuted)
                }
                RusPillBadge(tag, containerColor = WerusColors.Canvas, contentColor = WerusColors.InkMuted)
            }

            Text(summary, style = MaterialTheme.typography.bodySmall, color = WerusColors.InkMuted)

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = WerusColors.Canvas,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = conjugationNote,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Serif,
                    modifier = Modifier.padding(10.dp),
                )
            }

            mutationAlert?.let {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = WerusColors.Beige.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = WerusColors.Ink,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PastTenseCard() {
    RusCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = WerusColors.Paper,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RusPillBadge("过去时态", containerColor = WerusColors.Beige, contentColor = WerusColors.Ink)
                Text("动词过去时形式", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            Surface(
                color = WerusColors.Canvas,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "★ 核心记忆法：过去时不看“我 / 你 / 他”，而严格看“性 + 数”！",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = WerusColors.Red,
                    modifier = Modifier.padding(10.dp),
                )
            }

            MorphologyRulesRepository.pastTenseRows.forEach { r ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(r.labelZh, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, modifier = Modifier.width(100.dp))
                    Text(r.singularEnding ?: "", style = MaterialTheme.typography.bodySmall, color = WerusColors.Red, fontWeight = FontWeight.Bold, modifier = Modifier.width(50.dp))
                    Text(r.note ?: "", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Serif, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun AspectAndFutureCard() {
    RusCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = WerusColors.Paper,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RusPillBadge("体与将来", containerColor = WerusColors.Beige, contentColor = WerusColors.Ink)
                Text("动词体貌与将来时构成", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // 未完成体
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = WerusColors.Canvas,
                    border = BorderStroke(1.dp, WerusColors.BorderSoft),
                    modifier = Modifier.weight(1f),
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("🔁 未完成体 (НСВ)", style = MaterialTheme.typography.labelSmall, color = WerusColors.InkMuted)
                        Text("过程 / 重复 / 持续", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text("代表: читать", style = RusMorphTechTypography.MicroPill, color = WerusColors.InkFaint)
                        HorizontalDivider(color = WerusColors.BorderSoft, thickness = 0.5.dp)
                        Text("复合将来时:", style = RusMorphTechTypography.MicroPill, color = WerusColors.InkMuted)
                        Text("буду читать", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = WerusColors.Info)
                    }
                }

                // 完成体
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = WerusColors.Canvas,
                    border = BorderStroke(1.dp, WerusColors.BorderSoft),
                    modifier = Modifier.weight(1f),
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("✓ 完成体 (СВ)", style = MaterialTheme.typography.labelSmall, color = WerusColors.InkMuted)
                        Text("完成 / 结果 / 一次性", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text("代表: прочитать", style = RusMorphTechTypography.MicroPill, color = WerusColors.InkFaint)
                        HorizontalDivider(color = WerusColors.BorderSoft, thickness = 0.5.dp)
                        Text("简单将来时:", style = RusMorphTechTypography.MicroPill, color = WerusColors.InkMuted)
                        Text("прочитаю", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = WerusColors.Red)
                    }
                }
            }
        }
    }
}

// =========================================================================
// 词干与词尾视觉分离辅助函数
// =========================================================================
private fun buildStemEndingText(stem: String?, ending: String?): androidx.compose.ui.text.AnnotatedString {
    return buildAnnotatedString {
        if (!stem.isNullOrBlank() && !ending.isNullOrBlank() && ending != "—" && !ending.startsWith("=")) {
            append(stem)
            append("·")
            withStyle(
                SpanStyle(
                    color = WerusColors.Red,
                    fontWeight = FontWeight.Bold,
                )
            ) {
                append(ending.trimStart('-', ' '))
            }
        } else {
            append(ending ?: "—")
        }
    }
}

@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package org.namchieh.rusmorph.ui.card

import android.animation.ValueAnimator
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.namchieh.rusmorph.agent.EvidenceType
import org.namchieh.rusmorph.agent.WordCard
import org.namchieh.rusmorph.data.local.LexiconEntryWithDetails
import org.namchieh.rusmorph.ui.common.TerminalLabel
import org.namchieh.rusmorph.ui.theme.*

data class TerminalCardData(
    val id: String,
    val word: String,
    val meaning: String?,
    val partsOfSpeech: List<String>,
    val lesson: Int? = null,
    val morphology: List<String> = emptyList(),
    val analysis: List<Pair<String, String>> = emptyList(),
    val sourceLabels: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
)

fun LexiconEntryWithDetails.toTerminalCardData(): TerminalCardData {
    val e = entry
    return TerminalCardData(
        id = e.id,
        word = e.displayForm,
        meaning = e.chineseMeaning,
        partsOfSpeech = partsOfSpeech.map { it.partOfSpeech }.filter { it.isNotBlank() && it != "8" },
        lesson = e.lesson,
        morphology = listOfNotNull(e.gender, e.declensionClass, e.aspect, e.conjugationClass, e.phoneticAlternation).filter { it.isNotBlank() && it != "8" },
        analysis = listOfNotNull(
            e.gender?.let { "性" to it }, e.declensionClass?.let { "变格法" to it },
            e.endingType?.let { "结尾" to it }, e.pluralStressPattern?.let { "复数重音" to it },
            e.aspect?.let { "体" to it }, e.conjugationClass?.let { "变位法" to it },
            e.phoneticAlternation?.let { "语音交替" to it },
        ).filter { it.second.isNotBlank() && it.second != "8" },
        sourceLabels = sources.map { "${it.sourceWorkbook} · ${it.sourceSheet} · ${it.sourceRow}" },
    )
}

fun WordCard.toTerminalCardData(): TerminalCardData = TerminalCardData(
    id = id,
    word = word,
    meaning = meanings.joinToString("；").ifBlank { null },
    partsOfSpeech = partsOfSpeech,
    morphology = listOfNotNull(stress, morphology),
    analysis = buildList {
        etymology?.let { add("词源" to it) }
        morphology?.let { add("形态变化" to it) }
        generatedSentences.forEach { add("AI 生成例句" to listOfNotNull(it.russian, it.chinese).joinToString(" — ")) }
        sourceExamples.forEach { add("教材例句" to listOfNotNull(it.russian, it.chinese).joinToString(" — ")) }
        distinctions.takeIf { it.isNotEmpty() }?.let { add("易混辨析" to it.joinToString("\n")) }
        commonErrors.takeIf { it.isNotEmpty() }?.let { add("易错点" to it.joinToString("\n")) }
    },
    sourceLabels = evidence.map { evidence ->
        when (evidence.type) {
            EvidenceType.LEXICON_FIELD -> "本地词表 · ${evidence.title}"
            EvidenceType.LOCAL_KNOWLEDGE -> "本地知识文档 · ${evidence.title}"
            EvidenceType.MODEL_KNOWLEDGE -> "AI 通用知识 · ${evidence.title}"
            EvidenceType.MODEL_GENERATED_EXAMPLE -> "AI 生成例句 · ${evidence.title}"
            EvidenceType.SOURCE_EXAMPLE -> "教材例句 · ${evidence.title}"
        }
    },
    warnings = warnings.map { it.message },
)

@Composable
fun MorphologyTag(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.border(1.dp, RusMorphColors.OutlineSoft, MaterialTheme.shapes.small).background(RusMorphColors.SurfaceMuted.copy(alpha = .5f), MaterialTheme.shapes.small).padding(horizontal = 9.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium, color = RusMorphColors.TextSecondary)
}

@Composable
fun PhysicalCardSurface(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.shadow(10.dp, WordCardShape, ambientColor = RusMorphColors.ShadowWarm, spotColor = RusMorphColors.ShadowWarm)
            .background(RusMorphColors.SurfaceElevated, WordCardShape)
            .border(1.5.dp, RusMorphColors.Primary, WordCardShape)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

@Composable
fun WordCardFront(data: TerminalCardData, onOpen: (() -> Unit)? = null, onSave: (() -> Unit)? = null, onFlip: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    PhysicalCardSurface(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TerminalLabel(data.partsOfSpeech.firstOrNull() ?: "词条", color = RusMorphColors.Primary)
            TerminalLabel(data.lesson?.let { "L$it" } ?: data.id.takeLast(6), color = RusMorphColors.Secondary)
        }
        Box(Modifier.fillMaxWidth().height(3.dp).background(RusMorphColors.SecondaryContainer))
        Text(data.word, style = MaterialTheme.typography.displayMedium.copy(fontFamily = FontFamily.Serif), color = RusMorphColors.TextPrimary, maxLines = 2, overflow = TextOverflow.Visible)
        data.meaning?.let { Text(it, style = MaterialTheme.typography.titleLarge, color = RusMorphColors.TextSecondary) }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (data.partsOfSpeech + data.morphology).distinct().take(6).forEach { MorphologyTag(it) }
        }
        if (data.analysis.isNotEmpty()) Text(data.analysis.take(2).joinToString(" · ") { "${it.first} ${it.second}" }, style = MaterialTheme.typography.bodyMedium, color = RusMorphColors.TextSecondary)
        HorizontalDivider(color = RusMorphColors.Divider)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (onSave != null) TextButton(onClick = onSave, modifier = Modifier.heightIn(min = 48.dp)) { Text("收藏") }
            if (onOpen != null) TextButton(onClick = onOpen, modifier = Modifier.heightIn(min = 48.dp)) { Text("详情") }
            Spacer(Modifier.weight(1f))
            if (onFlip != null) TextButton(onClick = onFlip, modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "查看卡片背面" }) { Text("翻面  ↻") }
        }
    }
}

@Composable
fun WordCardBack(data: TerminalCardData, onFlip: () -> Unit, modifier: Modifier = Modifier) {
    PhysicalCardSurface(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { TerminalLabel("分析", color = RusMorphColors.Primary); TerminalLabel(data.word, color = RusMorphColors.Secondary) }
        if (data.analysis.isEmpty()) Text("暂无扩展资料", style = MaterialTheme.typography.bodyLarge, color = RusMorphColors.TextSecondary)
        data.analysis.forEach { (label, value) ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { TerminalLabel(label); Text(value, style = MaterialTheme.typography.bodyMedium) }
            HorizontalDivider(color = RusMorphColors.Divider)
        }
        if (data.sourceLabels.isNotEmpty()) {
            TerminalLabel("来源")
            data.sourceLabels.take(4).forEach { Text(it, style = MaterialTheme.typography.labelMedium, color = RusMorphColors.Tertiary) }
        }
        data.warnings.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = RusMorphColors.Error) }
        TextButton(onClick = onFlip, modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp).semantics { contentDescription = "返回卡片正面" }) { Text("↻  返回正面") }
    }
}

@Composable
fun FlippableWordCard(data: TerminalCardData, modifier: Modifier = Modifier, onOpen: (() -> Unit)? = null, onSave: (() -> Unit)? = null) {
    var flipped by rememberSaveable(data.id) { mutableStateOf(false) }
    val motionEnabled = remember { ValueAnimator.areAnimatorsEnabled() }
    if (!motionEnabled) {
        Crossfade(flipped, label = "cardReducedMotion") { back -> if (back) WordCardBack(data, { flipped = false }, modifier) else WordCardFront(data, onOpen, onSave, { flipped = true }, modifier) }
        return
    }
    val rotation by animateFloatAsState(if (flipped) 180f else 0f, tween(RusMorphMotion.CardFlipMillis, easing = FastOutSlowInEasing), label = "cardFlip")
    Box(modifier.graphicsLayer { rotationY = rotation; cameraDistance = 12f * density }.semantics { stateDescription = if (flipped) "卡片背面" else "卡片正面" }) {
        if (rotation <= 90f) WordCardFront(data, onOpen, onSave, { flipped = true }, Modifier.fillMaxWidth())
        else Box(Modifier.graphicsLayer { rotationY = 180f }) { WordCardBack(data, { flipped = false }, Modifier.fillMaxWidth()) }
    }
}

@Composable
fun CardPageIndicator(current: Int, total: Int, modifier: Modifier = Modifier) {
    Row(modifier.semantics { contentDescription = "第 ${current + 1} 张，共 $total 张" }, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (total <= 10) repeat(total) { index -> Box(Modifier.size(width = if (index == current) 20.dp else 6.dp, height = 6.dp).background(if (index == current) RusMorphColors.Secondary else RusMorphColors.OutlineSoft, MaterialTheme.shapes.small)) }
        Text("${current + 1} / $total", style = MaterialTheme.typography.labelMedium, color = RusMorphColors.TextSecondary)
    }
}

@Composable
fun WordCardCarousel(cards: List<TerminalCardData>, onOpen: (String) -> Unit = {}, onSave: ((TerminalCardData) -> Unit)? = null, modifier: Modifier = Modifier) {
    val pager = rememberPagerState(pageCount = { cards.size })
    val scope = rememberCoroutineScope()
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HorizontalPager(state = pager, key = { cards[it].id }, contentPadding = PaddingValues(horizontal = 18.dp), pageSpacing = 12.dp) { page ->
            val data = cards[page]
            FlippableWordCard(data, Modifier.fillMaxWidth(), onOpen = { onOpen(data.id) }, onSave = onSave?.let { { it(data) } })
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = { scope.launch { pager.animateScrollToPage((pager.currentPage - 1).coerceAtLeast(0)) } }, enabled = pager.currentPage > 0, modifier = Modifier.semantics { contentDescription = "上一张卡片" }) { Text("←") }
            CardPageIndicator(pager.currentPage, cards.size)
            TextButton(onClick = { scope.launch { pager.animateScrollToPage((pager.currentPage + 1).coerceAtMost(cards.lastIndex)) } }, enabled = pager.currentPage < cards.lastIndex, modifier = Modifier.semantics { contentDescription = "下一张卡片" }) { Text("→") }
        }
    }
}

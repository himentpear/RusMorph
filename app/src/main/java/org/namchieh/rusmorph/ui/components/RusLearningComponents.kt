package org.namchieh.rusmorph.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.namchieh.rusmorph.domain.learning.Course
import org.namchieh.rusmorph.domain.learning.LearningStatus
import org.namchieh.rusmorph.domain.learning.LearningUnit
import org.namchieh.rusmorph.domain.learning.Lesson
import org.namchieh.rusmorph.ui.design.WerusColors
import org.namchieh.rusmorph.ui.theme.RusMorphTechTypography
import org.namchieh.rusmorph.ui.theme.TechCardShape
import org.namchieh.rusmorph.ui.theme.PillShape

@Composable
fun RusButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isSecondary: Boolean = false,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSecondary) WerusColors.Beige else WerusColors.Ink,
            contentColor = if (isSecondary) WerusColors.Ink else WerusColors.OnDark,
        ),
    ) {
        Text(text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@Composable
fun RusCircleActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    symbol: String = "+",
    contentDescription: String = "Action",
) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(44.dp),
        shape = CircleShape,
        color = WerusColors.Ink,
        contentColor = WerusColors.OnDark,
        shadowElevation = 2.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = symbol,
                fontSize = 22.sp,
                fontWeight = FontWeight.Light,
                color = WerusColors.OnDark,
            )
        }
    }
}

@Composable
fun RusCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    backgroundColor: Color = WerusColors.Paper,
    border: BorderStroke = BorderStroke(1.dp, WerusColors.Border),
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        border = border,
        shape = TechCardShape,
    ) {
        Box(Modifier.padding(18.dp)) { content() }
    }
}

@Composable
fun RusPillBadge(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = WerusColors.Beige,
    contentColor: Color = WerusColors.InkMuted,
) {
    Surface(
        modifier = modifier,
        shape = PillShape,
        color = containerColor,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = RusMorphTechTypography.MicroPill,
            color = contentColor,
        )
    }
}

@Composable
fun RusSectionTitle(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            title,
            style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = WerusColors.Ink,
        )
        subtitle?.let {
            Text(
                it,
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = WerusColors.InkMuted,
            )
        }
    }
}

@Composable
fun RusProgressBar(progress: Float, modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) },
        modifier = modifier.fillMaxWidth().height(4.dp),
        color = WerusColors.Red,
        trackColor = WerusColors.BorderSoft,
    )
}

/**
 * High-density vertical needle/curve chart inspired by the reference design.
 */
@Composable
fun RusNeedleCurve(
    progress: Float = 0.65f,
    modifier: Modifier = Modifier,
    lineCount: Int = 46,
    color: Color = WerusColors.Border.copy(alpha = 0.85f),
    highlightColor: Color = WerusColors.Red,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(modifier = Modifier.fillMaxWidth().height(110.dp)) {
            val width = size.width
            val height = size.height
            val spacing = width / (lineCount - 1).coerceAtLeast(1)
            val activeIndex = (lineCount * progress.coerceIn(0f, 1f)).toInt()

            for (i in 0 until lineCount) {
                val ratio = i.toFloat() / (lineCount - 1)
                // Quadratic curve rising from left (15% height) to right (95% height)
                val barHeightFraction = 0.12f + (0.88f * (ratio * ratio))
                val barHeight = height * barHeightFraction
                val x = i * spacing
                val isHighlighted = i == activeIndex

                drawLine(
                    color = if (isHighlighted) highlightColor else color,
                    start = Offset(x, height),
                    end = Offset(x, height - barHeight),
                    strokeWidth = if (isHighlighted) 2.dp.toPx() else 1.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            listOf("0", "25", "50", "75", "100%").forEach { step ->
                Text(
                    text = step,
                    style = RusMorphTechTypography.MicroPill,
                    color = WerusColors.InkFaint,
                )
            }
        }
    }
}

@Composable
fun RusCourseCard(course: Course, onClick: () -> Unit, modifier: Modifier = Modifier) {
    RusCard(modifier.fillMaxWidth(), onClick) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RusPillBadge(course.subtitle.lowercase(), containerColor = WerusColors.Beige, contentColor = WerusColors.Ink)
                Text(
                    "${(course.progress * 100).toInt()}%",
                    style = RusMorphTechTypography.SmallDigit,
                    color = WerusColors.Red,
                )
            }
            Text(course.title, style = androidx.compose.material3.MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text(course.description, color = WerusColors.InkMuted, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
            RusProgressBar(course.progress)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("${course.completedLessonCount} / ${course.lessonCount} 课", color = WerusColors.InkFaint, style = RusMorphTechTypography.MicroPill)
                Text("继续 →", color = WerusColors.Ink, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun RusLessonCard(lesson: Lesson, onClick: () -> Unit, modifier: Modifier = Modifier, expanded: Boolean = false) {
    val accent = if (lesson.isReviewLesson) WerusColors.Red else WerusColors.Ink
    RusCard(modifier.fillMaxWidth(), onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                Modifier.size(46.dp).background(if (lesson.isReviewLesson) WerusColors.Beige else WerusColors.Beige, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    lesson.number.toString().padStart(2, '0'),
                    style = RusMorphTechTypography.SmallDigit,
                    color = accent,
                    fontWeight = FontWeight.Bold,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(lesson.titleRu, fontWeight = FontWeight.SemiBold, color = WerusColors.Ink)
                Text(lesson.titleZh, color = WerusColors.InkMuted, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                Text(
                    "${lesson.units.size} 个学习单元",
                    style = RusMorphTechTypography.MicroPill,
                    color = WerusColors.InkFaint,
                )
            }
            Text(if (expanded) "⌄" else "›", style = androidx.compose.material3.MaterialTheme.typography.headlineMedium, color = WerusColors.InkFaint)
        }
    }
}

@Composable
fun RusLearningUnitCard(index: Int, unit: LearningUnit, onClick: () -> Unit, modifier: Modifier = Modifier) {
    RusCard(modifier.fillMaxWidth(), onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                index.toString().padStart(2, '0'),
                style = RusMorphTechTypography.SmallDigit,
                color = WerusColors.Red,
            )
            Column(Modifier.weight(1f)) {
                Text(unit.titleRu, fontWeight = FontWeight.Bold, color = WerusColors.Ink)
                Text(unit.titleZh, color = WerusColors.InkMuted, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                if (unit.itemCount > 0) Text(
                    "${unit.itemCount} 项内容",
                    style = RusMorphTechTypography.MicroPill,
                    color = WerusColors.InkFaint,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RusStatusDot(unit.status)
                    Text(
                        when (unit.status) {
                            LearningStatus.COMPLETED -> "已完成"
                            LearningStatus.IN_PROGRESS -> "学习中"
                            LearningStatus.NOT_STARTED -> "未开始"
                        },
                        style = RusMorphTechTypography.MicroPill,
                        color = WerusColors.InkFaint,
                    )
                }
                Text("›", style = androidx.compose.material3.MaterialTheme.typography.titleLarge, color = WerusColors.InkFaint)
            }
        }
    }
}

@Composable
private fun RusStatusDot(status: LearningStatus) {
    val color = when (status) {
        LearningStatus.COMPLETED -> WerusColors.Success
        LearningStatus.IN_PROGRESS -> WerusColors.Red
        LearningStatus.NOT_STARTED -> WerusColors.Border
    }
    Box(Modifier.size(8.dp).background(color, CircleShape))
}

@Composable
fun RusWordChip(
    text: String,
    meaning: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badgeText: String? = null,
    isHighlighted: Boolean = false,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = if (isHighlighted) WerusColors.Beige.copy(alpha = 0.4f) else WerusColors.Paper,
        border = BorderStroke(1.dp, if (isHighlighted) WerusColors.Red.copy(alpha = 0.5f) else WerusColors.Border),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(text, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                meaning?.let { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis, color = WerusColors.InkMuted, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium) }
            }
            if (badgeText != null) {
                RusPillBadge(
                    badgeText,
                    containerColor = if (isHighlighted) WerusColors.Red.copy(alpha = 0.15f) else WerusColors.BeigeMuted,
                    contentColor = if (isHighlighted) WerusColors.Red else WerusColors.InkFaint,
                )
            }
        }
    }
}

@Composable fun RusGrammarChip(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) = RusContextChip(text, onClick, modifier)
@Composable fun RusScoreChip(score: Double, modifier: Modifier = Modifier) = RusContextChip("${score.toInt()} · 可懂度", {}, modifier)

@Composable
fun RusContextChip(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = PillShape,
        color = WerusColors.Beige,
    ) {
        Text(
            text = text,
            Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            color = WerusColors.Ink,
            style = RusMorphTechTypography.MicroPill,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * Modern digital stat component inspired by the reference mockup.
 * Shows large monospace numbers, an optional signature orange dot or arrow,
 * and a minimalist lowercase label.
 */
@Composable
fun RusStat(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    hasDot: Boolean = true,
    indicatorSymbol: String? = null,
    isLarge: Boolean = false,
) {
    Column(modifier, horizontalAlignment = Alignment.Start) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = value,
                style = if (isLarge) RusMorphTechTypography.HeroDigit else RusMorphTechTypography.StatDigit,
                color = WerusColors.Ink,
            )
            if (hasDot) {
                Box(
                    modifier = Modifier
                        .padding(start = 4.dp, top = if (isLarge) 10.dp else 4.dp)
                        .size(if (isLarge) 8.dp else 6.dp)
                        .background(WerusColors.Red, CircleShape),
                )
            } else if (indicatorSymbol != null) {
                Text(
                    text = indicatorSymbol,
                    fontSize = if (isLarge) 14.sp else 11.sp,
                    color = WerusColors.Red,
                    modifier = Modifier.padding(start = 3.dp, top = if (isLarge) 8.dp else 3.dp),
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = label.lowercase(),
            style = RusMorphTechTypography.MicroPill,
            color = WerusColors.InkMuted,
        )
    }
}

@Composable
fun RusEmptyState(title: String, message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier.size(6.dp).background(WerusColors.Red, CircleShape),
        )
        Text(title, fontWeight = FontWeight.SemiBold, color = WerusColors.Ink)
        Text(message, color = WerusColors.InkMuted, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun RusSearchBar(value: String, onValueChange: (String) -> Unit, onSearch: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text("输入俄语词形或中文释义", style = androidx.compose.material3.MaterialTheme.typography.bodyMedium) },
        singleLine = true,
        shape = PillShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = WerusColors.Paper,
            unfocusedContainerColor = WerusColors.Paper,
            focusedBorderColor = WerusColors.Ink,
            unfocusedBorderColor = WerusColors.Border,
        ),
        trailingIcon = {
            Text(
                "搜索",
                Modifier.clickable(onClick = onSearch).padding(end = 8.dp),
                color = WerusColors.Ink,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
            )
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RusBottomSheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = WerusColors.Paper,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Box(Modifier.padding(20.dp)) { content() }
    }
}

@Composable fun RusAudioPlayer(label: String, onPlay: () -> Unit, modifier: Modifier = Modifier) = RusButton("▶  $label", onPlay, modifier)
@Composable fun RusRecorder(recording: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) = RusButton(if (recording) "停止录音" else "开始跟读", onToggle, modifier)
@Composable fun RusWaveform(active: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .background(WerusColors.Paper, RoundedCornerShape(10.dp))
            .border(1.dp, if (active) WerusColors.Red else WerusColors.Border, RoundedCornerShape(10.dp)),
    )
}

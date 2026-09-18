package org.namchieh.rusmorph.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.namchieh.rusmorph.domain.learning.WordBook
import org.namchieh.rusmorph.domain.learning.LearningStatus
import org.namchieh.rusmorph.domain.learning.LearningUnit
import org.namchieh.rusmorph.domain.learning.Lesson
import org.namchieh.rusmorph.ui.theme.RusMorphColors

@Composable
fun RusButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(onClick, modifier.heightIn(min = 48.dp), enabled, colors = ButtonDefaults.buttonColors(containerColor = RusMorphColors.Primary)) {
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun RusCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        colors = CardDefaults.cardColors(containerColor = RusMorphColors.Surface),
        border = BorderStroke(1.dp, RusMorphColors.Outline),
        shape = RoundedCornerShape(18.dp),
    ) { Box(Modifier.padding(18.dp)) { content() } }
}

@Composable
fun RusSectionTitle(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, style = androidx.compose.material3.MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = RusMorphColors.TextPrimary)
        subtitle?.let { Text(it, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, color = RusMorphColors.TextSecondary) }
    }
}

@Composable
fun RusProgressBar(progress: Float, modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) }, modifier = modifier.fillMaxWidth().height(5.dp),
        color = RusMorphColors.Primary, trackColor = RusMorphColors.SurfaceMuted,
    )
}

@Composable
fun RusCourseCard(wordBook: WordBook, onClick: () -> Unit, modifier: Modifier = Modifier) {
    RusCard(modifier.fillMaxWidth(), onClick) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
            WordBookCover(wordBook, Modifier.width(92.dp).height(136.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(wordBook.subtitle.uppercase(), style = androidx.compose.material3.MaterialTheme.typography.labelMedium, color = RusMorphColors.Primary)
                Text(wordBook.title, style = androidx.compose.material3.MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(wordBook.description.orEmpty(), color = RusMorphColors.TextSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis)
                RusProgressBar(wordBook.progress)
                Text("${wordBook.completedLessonCount} / ${wordBook.lessonCount} 课", color = RusMorphColors.TextSecondary)
                Text("继续 →", color = RusMorphColors.Primary, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun WordBookCover(wordBook: WordBook, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val resourceId = (wordBook.coverResourceName ?: wordBook.cover)?.let { context.resources.getIdentifier(it, "drawable", context.packageName) } ?: 0
    if (resourceId != 0) {
        Image(
            painter = painterResource(resourceId),
            contentDescription = "${wordBook.title}封面",
            modifier = modifier,
            contentScale = ContentScale.Fit,
        )
    } else {
        Box(modifier.background(RusMorphColors.PrimaryContainer, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
            Text(wordBook.title.take(2), color = RusMorphColors.Primary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun RusLessonCard(lesson: Lesson, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val accent = if (lesson.isReviewLesson) RusMorphColors.Secondary else RusMorphColors.Primary
    RusCard(modifier.fillMaxWidth(), onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(48.dp).background(accent.copy(alpha = .12f), CircleShape), contentAlignment = Alignment.Center) {
                Text(lesson.number.toString().padStart(2, '0'), color = accent, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(lesson.titleRu.orEmpty(), fontWeight = FontWeight.SemiBold, color = accent)
                Text(lesson.titleZh.orEmpty(), color = RusMorphColors.TextSecondary)
                Text("${lesson.units.size} 个学习单元", style = androidx.compose.material3.MaterialTheme.typography.labelMedium, color = RusMorphColors.TextTertiary)
            }
            Text("›", style = androidx.compose.material3.MaterialTheme.typography.headlineMedium, color = accent)
        }
    }
}

@Composable
fun RusLearningUnitCard(index: Int, unit: LearningUnit, onClick: () -> Unit, modifier: Modifier = Modifier) {
    RusCard(modifier.fillMaxWidth(), onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(index.toString().padStart(2, '0'), style = androidx.compose.material3.MaterialTheme.typography.titleLarge, color = RusMorphColors.Secondary)
            Column(Modifier.weight(1f)) {
                Text(unit.titleRu, fontWeight = FontWeight.Bold, color = RusMorphColors.Primary)
                Text(unit.titleZh, color = RusMorphColors.TextSecondary)
                if (unit.itemCount > 0) Text("${unit.itemCount} 项内容", style = androidx.compose.material3.MaterialTheme.typography.labelMedium, color = RusMorphColors.TextTertiary)
            }
            RusStatusDot(unit.status)
        }
    }
}

@Composable
private fun RusStatusDot(status: LearningStatus) {
    val color = when (status) {
        LearningStatus.COMPLETED -> RusMorphColors.Success
        LearningStatus.IN_PROGRESS -> RusMorphColors.Warning
        LearningStatus.NOT_STARTED -> RusMorphColors.Outline
    }
    Box(Modifier.size(10.dp).background(color, CircleShape))
}

@Composable
fun RusWordChip(
    text: String,
    meaning: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    lessonBadge: String? = null,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = RusMorphColors.Surface,
        border = BorderStroke(
            1.dp,
            if (lessonBadge != null) RusMorphColors.Primary.copy(alpha = 0.35f) else RusMorphColors.Outline,
        ),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text,
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (lessonBadge != null) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = RusMorphColors.PrimaryContainer,
                    ) {
                        Text(
                            text = lessonBadge,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                            color = RusMorphColors.PrimaryDark,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
            meaning?.let { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis, color = RusMorphColors.TextSecondary) }
        }
    }
}

@Composable fun RusGrammarChip(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) = RusContextChip(text, onClick, modifier)
@Composable fun RusScoreChip(score: Double, modifier: Modifier = Modifier) = RusContextChip("${score.toInt()} · 可懂度", {}, modifier)

@Composable
fun RusContextChip(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(50), color = RusMorphColors.PrimaryContainer) {
        Text(text, Modifier.padding(horizontal = 12.dp, vertical = 7.dp), color = RusMorphColors.PrimaryDark, style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun RusStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = androidx.compose.material3.MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, color = RusMorphColors.Primary)
        Text(label, style = androidx.compose.material3.MaterialTheme.typography.labelMedium, color = RusMorphColors.TextSecondary)
    }
}

@Composable
fun RusEmptyState(title: String, message: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("—", color = RusMorphColors.Secondary)
        Text(title, fontWeight = FontWeight.SemiBold)
        Text(message, color = RusMorphColors.TextSecondary)
    }
}

@Composable
fun RusSearchBar(value: String, onValueChange: (String) -> Unit, onSearch: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(value, onValueChange, modifier.fillMaxWidth(), placeholder = { Text("输入俄语词形或中文释义") }, singleLine = true, trailingIcon = { Text("搜索", Modifier.clickable(onClick = onSearch), color = RusMorphColors.Primary) })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RusBottomSheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = RusMorphColors.Surface) { Box(Modifier.padding(20.dp)) { content() } }
}

@Composable fun RusAudioPlayer(label: String, onPlay: () -> Unit, modifier: Modifier = Modifier) = RusButton("▶  $label", onPlay, modifier)
@Composable fun RusRecorder(recording: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) = RusButton(if (recording) "停止录音" else "开始跟读", onToggle, modifier)
@Composable fun RusWaveform(active: Boolean, modifier: Modifier = Modifier) { Box(modifier.fillMaxWidth().height(28.dp).border(1.dp, if (active) RusMorphColors.Primary else RusMorphColors.Outline, RoundedCornerShape(8.dp))) }

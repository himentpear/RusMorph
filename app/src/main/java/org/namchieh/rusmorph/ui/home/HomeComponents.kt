@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package org.namchieh.rusmorph.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.namchieh.rusmorph.ui.common.TerminalLabel
import org.namchieh.rusmorph.ui.theme.*

@Composable
fun BrandHeader(onMore: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("俄语词法助手", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
            TerminalLabel("RM–01", color = RusMorphColors.AccentOrange)
        }
        Surface(
            onClick = onMore,
            modifier = Modifier.size(40.dp).semantics { contentDescription = "更多功能" },
            shape = androidx.compose.foundation.shape.CircleShape,
            color = RusMorphColors.Surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, RusMorphColors.Outline),
        ) { Box(contentAlignment = Alignment.Center) { Text("•••", color = RusMorphColors.CarbonBlack, fontWeight = FontWeight.Bold, fontSize = 11.sp) } }
    }
    HorizontalDivider(color = RusMorphColors.Divider, thickness = 1.dp)
}

@Composable
fun AiSearchHero(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onFilter: () -> Unit,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
    onMicrophone: () -> Unit = {},
    microphoneActive: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val border by animateColorAsState(if (focused) RusMorphColors.CarbonBlack else RusMorphColors.Outline, tween(RusMorphMotion.FocusMillis), label = "searchBorder")
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TerminalLabel("AI 搜索", color = RusMorphColors.CarbonBlack)
            TextButton(onClick = onFilter, modifier = Modifier.heightIn(min = 40.dp).semantics { contentDescription = "打开检索筛选" }) { Text("筛选  ≡", color = RusMorphColors.TextSecondary) }
        }
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }.semantics { contentDescription = "AI 检索终端输入框" },
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = RusMorphColors.TextPrimary),
            cursorBrush = SolidColor(RusMorphColors.AccentOrange),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
            decorationBox = { inner ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 68.dp).background(RusMorphColors.Surface, HeroSearchShape).border(if (focused) 1.5.dp else 1.dp, border, HeroSearchShape).padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("⌕", style = MaterialTheme.typography.headlineSmall, color = RusMorphColors.TextTertiary, modifier = Modifier.padding(horizontal = 10.dp))
                    Box(Modifier.weight(1f).padding(vertical = 8.dp)) {
                        if (query.isBlank()) Text("单词、释义或命令", style = MaterialTheme.typography.bodyMedium, color = RusMorphColors.TextTertiary)
                        inner()
                    }
                    Surface(
                        onClick = onMicrophone,
                        modifier = Modifier.size(44.dp).semantics {
                            contentDescription = if (microphoneActive) "停止俄语录音" else "开始俄语语音输入"
                        },
                        color = if (microphoneActive) RusMorphColors.AccentOrange else RusMorphColors.PillBackground,
                        contentColor = if (microphoneActive) RusMorphColors.TextOnDark else RusMorphColors.CarbonBlack,
                        shape = androidx.compose.foundation.shape.CircleShape,
                    ) { Box(contentAlignment = Alignment.Center) { Text(if (microphoneActive) "■" else "●", fontSize = 14.sp) } }
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        onClick = onSubmit,
                        enabled = query.isNotBlank() && !isLoading,
                        modifier = Modifier.size(44.dp).semantics { contentDescription = if (isLoading) "正在检索" else "执行检索" },
                        color = RusMorphColors.CarbonBlack,
                        contentColor = RusMorphColors.TextOnDark,
                        shape = androidx.compose.foundation.shape.CircleShape,
                    ) { Box(contentAlignment = Alignment.Center) { Text(if (isLoading) "···" else "→", style = MaterialTheme.typography.titleMedium) } }
                }
            },
        )
    }
}

@Composable
fun SuggestionCommandRow(onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val commands = listOf("查单词" to "автомобиль", "造句" to "用这个词造句", "辨析" to "比较 тоже 和 также", "课内词汇" to "第一课词汇")
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        commands.forEach { (label, command) ->
            Surface(onClick = { onSelect(command) }, shape = RoundedCornerShape(10.dp), color = RusMorphColors.Surface, border = androidx.compose.foundation.BorderStroke(1.dp, RusMorphColors.OutlineStrong)) {
                Text(label, Modifier.padding(horizontal = 13.dp, vertical = 9.dp), style = MaterialTheme.typography.labelMedium, color = RusMorphColors.TextPrimary)
            }
        }
    }
}

@Composable
fun SearchStatusSummary(lesson: Int?, partOfSpeech: String?, resultCount: Int, modifier: Modifier = Modifier) {
    if (lesson == null && partOfSpeech == null) return
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(listOfNotNull(lesson?.let { "第 $it 课" }, partOfSpeech).joinToString(" · "), style = MaterialTheme.typography.labelLarge, color = RusMorphColors.Primary)
        Spacer(Modifier.weight(1f))
        Text("$resultCount 条", style = MaterialTheme.typography.labelMedium, color = RusMorphColors.TextTertiary)
    }
}

@Composable
fun EmptyTerminalState(modifier: Modifier = Modifier, onCommand: (String) -> Unit = {}) {
    Column(modifier.border(1.dp, RusMorphColors.OutlineSoft, MaterialTheme.shapes.large).background(RusMorphColors.Surface.copy(alpha = .72f), MaterialTheme.shapes.large).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("开始检索", style = MaterialTheme.typography.titleLarge, color = RusMorphColors.Primary)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("автомобиль", "第一课动词", "тоже / также").forEach { value -> TextButton(onClick = { onCommand(value) }) { Text(value) } }
        }
    }
}

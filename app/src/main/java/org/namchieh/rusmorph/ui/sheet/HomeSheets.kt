@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package org.namchieh.rusmorph.ui.sheet

import org.namchieh.rusmorph.ui.design.WerusColors

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.namchieh.rusmorph.ui.common.RusMorphPrimaryButton
import org.namchieh.rusmorph.ui.common.TerminalLabel
import org.namchieh.rusmorph.ui.theme.*

@Composable
fun FilterBottomSheet(
    lessonOptions: List<Int>,
    partOfSpeechOptions: List<String>,
    selectedLesson: Int?,
    selectedPartOfSpeech: String?,
    onDismiss: () -> Unit,
    onApply: (Int?, String?) -> Unit,
) {
    var lesson by remember(selectedLesson) { mutableStateOf(selectedLesson) }
    var part by remember(selectedPartOfSpeech) { mutableStateOf(selectedPartOfSpeech) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = WerusColors.Paper, dragHandle = { BottomSheetDefaults.DragHandle(color = WerusColors.BorderStrong) }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("筛选本地词库", style = MaterialTheme.typography.headlineMedium)
            Text("当前筛选：${listOfNotNull(lesson?.let { "第 $it 课" }, part).ifEmpty { listOf("全部词条") }.joinToString(" · ")}", color = WerusColors.InkMuted)
            HorizontalDivider(color = WerusColors.BorderSoft)
            TerminalLabel("课号")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = lesson == null, onClick = { lesson = null }, label = { Text("全部课号") })
                lessonOptions.forEach { value -> FilterChip(selected = lesson == value, onClick = { lesson = value }, label = { Text("第 $value 课") }) }
            }
            TerminalLabel("词性")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = part == null, onClick = { part = null }, label = { Text("全部词性") })
                partOfSpeechOptions.forEach { value -> FilterChip(selected = part == value, onClick = { part = value }, label = { Text(value) }) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { lesson = null; part = null }, Modifier.weight(1f).heightIn(min = 48.dp)) { Text("重置") }
                RusMorphPrimaryButton("应用筛选", { onApply(lesson, part) }, Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
fun MoreActionsSheet(onDismiss: () -> Unit, onSmartCommand: () -> Unit, onFilter: () -> Unit, onSettings: () -> Unit) {
    val actions = listOf(
        Triple("筛选", "按课号与词性缩小检索范围", onFilter),
        Triple("智能命令", "生成词卡、卡组与词语辨析", onSmartCommand),
        Triple("搜索历史", "即将开放", {}), Triple("自定义归档", "从收藏页管理归档", {}),
        Triple("错题本", "即将开放", {}), Triple("单词抽背", "即将开放", {}), Triple("数据状态", "947 条真实词条", {}), Triple("设置与关于", "即将开放", {}),
    )
    val allActions = actions + Triple("设置", "AI 请求诊断", onSettings)
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = WerusColors.Paper) {
        LazyColumn(Modifier.fillMaxWidth().navigationBarsPadding(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            item { Text("更多", style = MaterialTheme.typography.headlineMedium); Spacer(Modifier.height(12.dp)) }
            items(allActions) { (title, description, action) ->
                Surface(onClick = { if (!description.contains("即将开放")) { onDismiss(); action() } }, color = WerusColors.Paper) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(title, style = MaterialTheme.typography.titleMedium)
                        if (description == "即将开放") Text(description, style = MaterialTheme.typography.labelMedium, color = WerusColors.InkFaint)
                    }
                }
                HorizontalDivider(color = WerusColors.BorderSoft)
            }
        }
    }
}

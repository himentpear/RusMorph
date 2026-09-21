package org.namchieh.rusmorph.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.namchieh.rusmorph.data.diagnostics.AgentDiagnostics
import org.namchieh.rusmorph.data.repository.AgentRepository
import org.namchieh.rusmorph.data.settings.AppSettings
import kotlinx.coroutines.launch
import org.namchieh.rusmorph.ui.design.WerusColors
import org.namchieh.rusmorph.BuildConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    diagnostics: AgentDiagnostics,
    agentRepository: AgentRepository,
    appSettings: AppSettings,
    onBack: () -> Unit,
) {
    val enabled by diagnostics.enabled.collectAsStateWithLifecycle()
    val events by diagnostics.events.collectAsStateWithLifecycle()
    val deckLimit by appSettings.deckLimit.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    Scaffold(containerColor = WerusColors.Canvas, topBar = { TopAppBar(title = { Text("设置", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold) }, navigationIcon = { TextButton(onClick = onBack) { Text("←", color = WerusColors.Ink) } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = WerusColors.Canvas)) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = WerusColors.Paper), border = androidx.compose.foundation.BorderStroke(1.dp, WerusColors.Border)) {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("朗读样本工作台", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                        Text(
                            "按教材课号生成对话任务，录音、回放并提交人工评分。",
                            color = WerusColors.InkMuted,
                        )
                        Text(
                            "将在系统浏览器中打开，以获得更稳定的麦克风与音频播放支持。",
                            color = WerusColors.InkFaint,
                        )
                        OutlinedButton(onClick = { uriHandler.openUri(BuildConfig.REVIEW_WORKBENCH_URL) }) {
                            Text("打开工作台")
                        }
                    }
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = WerusColors.Paper), border = androidx.compose.foundation.BorderStroke(1.dp, WerusColors.Border)) {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("牌组上限", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            AppSettings.DECK_LIMIT_OPTIONS.forEach { option ->
                                FilterChip(
                                    selected = deckLimit == option,
                                    onClick = { appSettings.setDeckLimit(option) },
                                    label = { Text(option.toString()) },
                                )
                            }
                        }
                    }
                }
            }
            item { Card(colors = CardDefaults.cardColors(containerColor = WerusColors.Paper), border = androidx.compose.foundation.BorderStroke(1.dp, WerusColors.Border)) { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Column(Modifier.weight(1f)) { Text("AI 请求诊断", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold); Text("记录接口阶段、状态码、耗时和安全错误码", color = WerusColors.InkMuted) }; Switch(checked = enabled, onCheckedChange = diagnostics::setEnabled) }
                Text("默认关闭；开启后仅保留本机当前会话数据，不含问题、回答或密钥。", color = WerusColors.InkFaint)
                if (enabled) OutlinedButton(onClick = { scope.launch { agentRepository.checkWorkerConnection() } }) { Text("测试 Worker 连接") }
            } } }
            if (enabled) {
                item { Text("最近反馈") }
                if (events.isEmpty()) item { Text("尚无 AI 请求。", color = WerusColors.InkMuted) }
                items(events.size) { index -> val event = events[index]; Card(colors = CardDefaults.cardColors(containerColor = WerusColors.Paper)) { Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("${event.operation} · ${event.outcome}")
                    Text(listOfNotNull(event.httpStatus?.let { "HTTP $it" }, event.elapsedMs?.let { "${it}ms" }, event.errorCode).joinToString(" · ").ifBlank { "正在等待响应" }, color = WerusColors.InkMuted)
                    Text("请求 ${event.requestId.take(8)}", color = WerusColors.InkFaint)
                } } }
            }
        }
    }
}

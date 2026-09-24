package org.namchieh.rusmorph.ui.screen.settings

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.namchieh.rusmorph.data.diagnostics.AgentDiagnostics
import org.namchieh.rusmorph.data.repository.AgentRepository
import org.namchieh.rusmorph.data.settings.AppSettings
import kotlinx.coroutines.launch
import org.namchieh.rusmorph.ui.design.WerusColors
import org.namchieh.rusmorph.ui.design.WerusTypography
import org.namchieh.rusmorph.BuildConfig
import org.namchieh.rusmorph.update.UpdateCoordinator
import org.namchieh.rusmorph.update.model.ManualUpdateStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    diagnostics: AgentDiagnostics,
    agentRepository: AgentRepository,
    appSettings: AppSettings,
    updateCoordinator: UpdateCoordinator,
    onBack: () -> Unit,
    onWallpaper: () -> Unit,
) {
    val enabled by diagnostics.enabled.collectAsStateWithLifecycle()
    val events by diagnostics.events.collectAsStateWithLifecycle()
    val deckLimit by appSettings.deckLimit.collectAsStateWithLifecycle()
    val updateStatus by updateCoordinator.manualStatus.collectAsStateWithLifecycle()
    val developerMode by appSettings.developerMode.collectAsStateWithLifecycle()
    var versionTaps by remember { mutableIntStateOf(0) }
    var debugExpanded by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    Scaffold(containerColor = WerusColors.Canvas) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onBack) { Text("‹ 返回", color = WerusColors.RedDark) }
                    Text("设置", style = WerusTypography.Title, color = WerusColors.Ink)
                }
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onWallpaper),
                    colors = CardDefaults.cardColors(containerColor = WerusColors.Paper),
                    border = androidx.compose.foundation.BorderStroke(1.dp, WerusColors.Border),
                ) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("首页背景", style = WerusTypography.Subtitle, color = WerusColors.Ink)
                            Text("选择喜欢的阅读氛围", style = WerusTypography.Caption, color = WerusColors.InkMuted)
                        }
                        Text("›", style = WerusTypography.Title, color = WerusColors.RedDark)
                    }
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = WerusColors.Paper), border = androidx.compose.foundation.BorderStroke(1.dp, WerusColors.Border)) {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("关于", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                        Text(
                            "全员俄人 WeRus ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                            color = WerusColors.InkMuted,
                            modifier = Modifier.clickable {
                                versionTaps += 1
                                if (versionTaps >= 7) appSettings.setDeveloperMode(true)
                            },
                        )
                        if (BuildConfig.FLAVOR == "production") {
                            OutlinedButton(
                                onClick = updateCoordinator::checkManually,
                                enabled = updateStatus !is ManualUpdateStatus.Checking,
                            ) {
                                Text(if (updateStatus is ManualUpdateStatus.Checking) "正在检查…" else "检查更新")
                            }
                            when (val status = updateStatus) {
                                ManualUpdateStatus.UpToDate -> Text("已是最新版本", color = WerusColors.InkMuted)
                                is ManualUpdateStatus.Failed -> Text(status.message, color = WerusColors.InkMuted)
                                else -> Unit
                            }
                        } else {
                            Text("本地构建不连接正式更新服务。", color = WerusColors.InkFaint)
                        }
                        TextButton(onClick = { uriHandler.openUri("https://github.com/himentpear/RusMorph") }) {
                            Text("GitHub · 查看源代码")
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
            if (developerMode) item {
                Card(colors = CardDefaults.cardColors(containerColor = WerusColors.Paper), border = androidx.compose.foundation.BorderStroke(1.dp, WerusColors.Border)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            Modifier.fillMaxWidth().clickable { debugExpanded = !debugExpanded },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("Debug · 测试系统", style = WerusTypography.Subtitle, color = WerusColors.Ink)
                                Text("仅供开发与诊断", style = WerusTypography.Caption, color = WerusColors.InkMuted)
                            }
                            Text(if (debugExpanded) "⌃" else "⌄", style = WerusTypography.Title, color = WerusColors.RedDark)
                        }
                        if (debugExpanded) {
                            HorizontalDivider(color = WerusColors.BorderSoft)
                            Text("朗读样本工作台", style = WerusTypography.Subtitle, color = WerusColors.Ink)
                            Text("按教材课号生成任务，录音并提交人工评分。", style = WerusTypography.Caption, color = WerusColors.InkMuted)
                            OutlinedButton(onClick = { uriHandler.openUri(BuildConfig.REVIEW_WORKBENCH_URL) }) { Text("打开测试工作台") }

                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("AI 请求诊断", style = WerusTypography.Subtitle, color = WerusColors.Ink)
                                    Text("仅记录本机会话状态", style = WerusTypography.Caption, color = WerusColors.InkMuted)
                                }
                                Switch(checked = enabled, onCheckedChange = diagnostics::setEnabled)
                            }
                            if (enabled) {
                                OutlinedButton(onClick = { scope.launch { agentRepository.checkWorkerConnection() } }) { Text("测试 Worker 连接") }
                                Text("最近反馈", style = WerusTypography.Subtitle, color = WerusColors.Ink)
                                if (events.isEmpty()) Text("尚无 AI 请求。", style = WerusTypography.Caption, color = WerusColors.InkMuted)
                                events.take(5).forEach { event ->
                                    Text(
                                        "${event.operation} · ${event.outcome} · " +
                                            listOfNotNull(event.httpStatus?.let { "HTTP $it" }, event.elapsedMs?.let { "${it}ms" }, event.errorCode).joinToString(" · "),
                                        style = WerusTypography.Caption,
                                        color = WerusColors.InkMuted,
                                    )
                                }
                            }
                            if (BuildConfig.DEBUG) {
                                HorizontalDivider(color = WerusColors.BorderSoft)
                                Text("更新流程模拟", style = WerusTypography.Subtitle, color = WerusColors.Ink)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = { updateCoordinator.simulateAvailable(false) }) { Text("普通更新") }
                                    TextButton(onClick = { updateCoordinator.simulateAvailable(true) }) { Text("强制更新") }
                                }
                            }
                            TextButton(onClick = { appSettings.setDeveloperMode(false); debugExpanded = false }) { Text("退出 Debug") }
                        }
                    }
                }
            }
        }
    }
}

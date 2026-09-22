package org.namchieh.rusmorph.update.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.namchieh.rusmorph.BuildConfig
import org.namchieh.rusmorph.update.model.AppUpdate
import org.namchieh.rusmorph.update.model.UpdateUiState

@Composable
fun AppUpdateDialog(
    state: UpdateUiState,
    onLater: () -> Unit,
    onSkip: () -> Unit,
    onDownload: () -> Unit,
    onInstall: (Context) -> Unit,
    onOpenPermission: (Context) -> Unit,
) {
    val context = LocalContext.current
    val mandatory = state.mandatory
    val activity = context.findActivity()
    if (mandatory) BackHandler(enabled = true) { }
    when (state) {
        is UpdateUiState.Available -> AlertDialog(
            onDismissRequest = { if (!state.mandatory) onLater() },
            title = { Text(if (state.mandatory) "需要更新" else "发现新版本") },
            text = { UpdateDetails(state.update, state.mandatory) },
            confirmButton = { TextButton(onClick = onDownload) { Text("立即更新") } },
            dismissButton = {
                if (state.mandatory) {
                    TextButton(onClick = { activity?.finishAffinity() }) { Text("退出应用") }
                } else {
                    Row {
                        TextButton(onClick = onLater) { Text("稍后") }
                        TextButton(onClick = onSkip) { Text("跳过此版本") }
                    }
                }
            },
        )
        is UpdateUiState.Downloading -> ProgressDialog(
            title = "正在下载更新",
            message = "RusMorph ${state.update.versionName}\n下载完成后即可安装。",
            mandatory = state.mandatory,
            onLater = onLater,
            onExit = { activity?.finishAffinity() },
        )
        is UpdateUiState.Verifying -> ProgressDialog(
            title = "正在验证更新",
            message = "正在确认更新包安全完整，请稍候。",
            mandatory = state.mandatory,
            onLater = onLater,
            onExit = { activity?.finishAffinity() },
        )
        is UpdateUiState.Downloaded -> AlertDialog(
            onDismissRequest = { if (!state.mandatory) onLater() },
            title = { Text("更新已下载") },
            text = { Text("RusMorph ${state.update.versionName} 已准备好，可以开始安装。") },
            confirmButton = { TextButton(onClick = { onInstall(context) }) { Text("安装更新") } },
            dismissButton = {
                TextButton(onClick = {
                    if (state.mandatory) activity?.finishAffinity() else onLater()
                }) { Text(if (state.mandatory) "退出应用" else "稍后") }
            },
        )
        is UpdateUiState.PermissionRequired -> AlertDialog(
            onDismissRequest = { if (!state.mandatory) onLater() },
            title = { Text("允许安装更新") },
            text = { Text("为了安装从 RusMorph 官方发布渠道下载的更新，需要允许此应用安装未知来源应用。") },
            confirmButton = { TextButton(onClick = { onOpenPermission(context) }) { Text("前往设置") } },
            dismissButton = {
                Row {
                    if (state.mandatory) {
                        TextButton(onClick = { activity?.finishAffinity() }) { Text("退出应用") }
                    } else {
                        TextButton(onClick = onLater) { Text("取消") }
                    }
                    TextButton(onClick = { onInstall(context) }) { Text("我已允许") }
                }
            },
        )
        is UpdateUiState.Failed -> AlertDialog(
            onDismissRequest = { if (!state.mandatory) onLater() },
            title = { Text("更新未完成") },
            text = { Text(state.message) },
            confirmButton = { TextButton(onClick = onDownload) { Text("重新下载") } },
            dismissButton = {
                TextButton(onClick = {
                    if (state.mandatory) activity?.finishAffinity() else onLater()
                }) { Text(if (state.mandatory) "退出应用" else "稍后") }
            },
        )
        UpdateUiState.Idle, UpdateUiState.Checking -> Unit
    }
}

@Composable
private fun UpdateDetails(update: AppUpdate, mandatory: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            if (mandatory) "当前版本已停止支持。更新至 RusMorph ${update.versionName} 后才能继续使用。"
            else "RusMorph ${update.versionName} 已发布",
            style = MaterialTheme.typography.bodyLarge,
        )
        Text("当前版本：${BuildConfig.VERSION_NAME}")
        Text("更新大小：${formatFileSize(update.apkSize)}")
        if (update.releaseNotes.isNotEmpty()) {
            Text("本次更新", style = MaterialTheme.typography.titleSmall)
            Column(
                Modifier.fillMaxWidth().heightIn(max = 220.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                update.releaseNotes.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}

@Composable
private fun ProgressDialog(
    title: String,
    message: String,
    mandatory: Boolean,
    onLater: () -> Unit,
    onExit: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!mandatory) onLater() },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(message)
                LinearProgressIndicator(
                    Modifier.fillMaxWidth().semantics { contentDescription = title },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = if (mandatory) onExit else onLater) {
                Text(if (mandatory) "退出应用" else "后台继续")
            }
        },
    )
}

private val UpdateUiState.mandatory: Boolean
    get() = when (this) {
        is UpdateUiState.Available -> mandatory
        is UpdateUiState.Downloading -> mandatory
        is UpdateUiState.Verifying -> mandatory
        is UpdateUiState.Downloaded -> mandatory
        is UpdateUiState.PermissionRequired -> mandatory
        is UpdateUiState.Failed -> mandatory
        else -> false
    }

private fun formatFileSize(bytes: Long): String = when {
    bytes <= 0 -> "未知"
    bytes < 1_000_000 -> "%.1f KB".format(bytes / 1_000.0)
    else -> "%.1f MB".format(bytes / 1_000_000.0)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

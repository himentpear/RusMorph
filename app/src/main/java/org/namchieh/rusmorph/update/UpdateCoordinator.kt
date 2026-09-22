package org.namchieh.rusmorph.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.namchieh.rusmorph.BuildConfig
import org.namchieh.rusmorph.data.settings.AppSettings
import org.namchieh.rusmorph.update.data.UpdateRepository
import org.namchieh.rusmorph.update.model.AppUpdate
import org.namchieh.rusmorph.update.model.ManualUpdateStatus
import org.namchieh.rusmorph.update.model.UpdateDecision
import org.namchieh.rusmorph.update.model.UpdateFlavorPolicy
import org.namchieh.rusmorph.update.model.UpdateRules
import org.namchieh.rusmorph.update.model.UpdateUiState

class UpdateCoordinator(
    private val context: Context,
    private val settings: AppSettings,
    private val repository: UpdateRepository,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val checkMutex = Mutex()
    private val automaticCheckStarted = AtomicBoolean(false)
    private val downloadManager = context.getSystemService(DownloadManager::class.java)
    private val mutableState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val state: StateFlow<UpdateUiState> = mutableState.asStateFlow()
    private val mutableManualStatus = MutableStateFlow<ManualUpdateStatus>(ManualUpdateStatus.Idle)
    val manualStatus: StateFlow<ManualUpdateStatus> = mutableManualStatus.asStateFlow()
    private var downloadId: Long? = null
    private var activeDownload: ActiveDownload? = null

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(receiverContext: Context?, intent: Intent?) {
            if (intent?.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
            val completedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (completedId == downloadId) scope.launch { verifyDownloadedFile(completedId) }
        }
    }

    init {
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        // DownloadManager is a system service outside this process. The download ID,
        // status and digest are verified before downloaded content is trusted.
        androidx.core.content.ContextCompat.registerReceiver(
            context,
            downloadReceiver,
            filter,
            androidx.core.content.ContextCompat.RECEIVER_EXPORTED,
        )
    }

    fun checkAutomatically() {
        if (!UpdateFlavorPolicy.allowsAutomaticCheck(BuildConfig.FLAVOR, BuildConfig.DEBUG)) return
        if (!automaticCheckStarted.compareAndSet(false, true)) return
        if (now() - settings.getLastUpdateCheckTimestamp() < AUTO_CHECK_INTERVAL_MS) return
        scope.launch { performCheck(manual = false) }
    }

    fun checkManually() {
        // Local variants must never contact the stable production update service.
        if (!UpdateFlavorPolicy.allowsManualCheck(BuildConfig.FLAVOR)) return
        scope.launch { performCheck(manual = true) }
    }

    private suspend fun performCheck(manual: Boolean) = checkMutex.withLock {
        if (manual) mutableManualStatus.value = ManualUpdateStatus.Checking
        else mutableState.value = UpdateUiState.Checking
        settings.setLastUpdateCheckTimestamp(now())
        runCatching { repository.latestStable() }
            .onSuccess { update ->
                when (val decision = UpdateRules.decide(
                    update = update,
                    localVersionCode = BuildConfig.VERSION_CODE,
                    skippedVersionCode = settings.getSkippedUpdateVersionCode(),
                    automatic = !manual,
                )) {
                    UpdateDecision.None -> {
                        mutableState.value = UpdateUiState.Idle
                        if (manual) mutableManualStatus.value = ManualUpdateStatus.UpToDate
                    }
                    is UpdateDecision.Available -> {
                        mutableState.value = UpdateUiState.Available(decision.update, decision.mandatory)
                        if (manual) mutableManualStatus.value = ManualUpdateStatus.Idle
                    }
                }
            }
            .onFailure {
                mutableState.value = UpdateUiState.Idle
                if (manual) mutableManualStatus.value = ManualUpdateStatus.Failed()
            }
    }

    fun remindLater() {
        if (!state.value.isMandatory()) mutableState.value = UpdateUiState.Idle
    }

    fun skipVersion() {
        val available = state.value as? UpdateUiState.Available ?: return
        if (available.mandatory) return
        settings.setSkippedUpdateVersionCode(available.update.versionCode)
        mutableState.value = UpdateUiState.Idle
    }

    fun startDownload() {
        val current = state.value
        val update = current.updateOrNull() ?: return
        val mandatory = current.isMandatory()
        if (activeDownload != null || current is UpdateUiState.Downloading || current is UpdateUiState.Verifying) return
        activeDownload = ActiveDownload(update, mandatory)
        mutableState.value = UpdateUiState.Downloading(update, mandatory)
        scope.launch {
            runCatching {
                val fileName = "RusMorph-${update.versionName}-production.apk"
                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?.resolve(fileName)
                    ?.takeIf { it.isFile }
                    ?.delete()
                val request = DownloadManager.Request(Uri.parse(update.apkUrl))
                    .setTitle("正在下载 RusMorph ${update.versionName}")
                    .setDescription("下载完成后即可安装")
                    .setMimeType(APK_MIME)
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setAllowedOverMetered(true)
                    .setAllowedOverRoaming(false)
                    .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
                downloadManager.enqueue(request)
            }.onSuccess { id ->
                downloadId = id
            }.onFailure {
                activeDownload = null
                mutableState.value = UpdateUiState.Failed(update, mandatory, "更新下载失败，请稍后重试")
            }
        }
    }

    private suspend fun verifyDownloadedFile(id: Long) {
        val active = activeDownload ?: return
        val update = active.update
        val mandatory = active.mandatory
        val result = downloadManager.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            if (status != DownloadManager.STATUS_SUCCESSFUL) return@use null
            downloadManager.getUriForDownloadedFile(id)
        }
        if (result == null) {
            activeDownload = null
            downloadId = null
            mutableState.value = UpdateUiState.Failed(update, mandatory, "更新下载失败，请稍后重试")
            return
        }
        mutableState.value = UpdateUiState.Verifying(update, mandatory)
        val actualHash = runCatching { sha256(result) }.getOrNull()
        if (!actualHash.equals(update.apkSha256, ignoreCase = true)) {
            downloadManager.remove(id)
            downloadId = null
            activeDownload = null
            mutableState.value = UpdateUiState.Failed(update, mandatory, "更新包校验失败，请重新下载")
            return
        }
        mutableState.value = UpdateUiState.Downloaded(update, result, mandatory)
    }

    private suspend fun sha256(uri: Uri): String = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-256")
        context.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        } ?: error("Downloaded APK is unavailable")
        digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    fun installDownloaded(contextForIntent: Context) {
        val current = state.value
        val downloaded = when (current) {
            is UpdateUiState.Downloaded -> current
            is UpdateUiState.PermissionRequired -> UpdateUiState.Downloaded(
                current.update,
                current.fileUri,
                current.mandatory,
            )
            else -> return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !contextForIntent.packageManager.canRequestPackageInstalls()) {
            mutableState.value = UpdateUiState.PermissionRequired(
                downloaded.update,
                downloaded.fileUri,
                downloaded.mandatory,
            )
            return
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(downloaded.fileUri, APK_MIME)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { contextForIntent.startActivity(intent) }
            .onFailure {
                clearActiveDownload()
                mutableState.value = UpdateUiState.Failed(
                    downloaded.update,
                    downloaded.mandatory,
                    "无法打开安装程序，请重新下载",
                )
            }
    }

    fun openInstallPermission(contextForIntent: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { contextForIntent.startActivity(intent) }
            .onFailure {
                val current = state.value as? UpdateUiState.PermissionRequired ?: return@onFailure
                clearActiveDownload()
                mutableState.value = UpdateUiState.Failed(
                    current.update,
                    current.mandatory,
                    "无法打开系统设置，请稍后重试",
                )
            }
    }

    fun simulateAvailable(mandatory: Boolean) {
        if (!BuildConfig.DEBUG) return
        mutableState.value = UpdateUiState.Available(
            AppUpdate(
                versionCode = BuildConfig.VERSION_CODE + 1,
                versionName = "${BuildConfig.VERSION_NAME}-test",
                minSupportedVersionCode = if (mandatory) BuildConfig.VERSION_CODE + 1 else 1,
                forceUpdate = mandatory,
                title = "RusMorph 测试更新",
                releaseNotes = listOf("用于检查更新弹窗布局", "不会自动安装任何内容"),
                apkUrl = "https://example.com/rusmorph-test.apk",
                apkSha256 = "0".repeat(64),
                apkSize = 32_600_000,
                releasePageUrl = null,
            ),
            mandatory,
        )
    }

    private fun clearActiveDownload() {
        downloadId?.let { downloadManager.remove(it) }
        downloadId = null
        activeDownload = null
    }

    private fun UpdateUiState.updateOrNull(): AppUpdate? = when (this) {
        is UpdateUiState.Available -> update
        is UpdateUiState.Downloading -> update
        is UpdateUiState.Verifying -> update
        is UpdateUiState.Downloaded -> update
        is UpdateUiState.PermissionRequired -> update
        is UpdateUiState.Failed -> update
        else -> null
    }

    private fun UpdateUiState.isMandatory(): Boolean = when (this) {
        is UpdateUiState.Available -> mandatory
        is UpdateUiState.Downloading -> mandatory
        is UpdateUiState.Verifying -> mandatory
        is UpdateUiState.Downloaded -> mandatory
        is UpdateUiState.PermissionRequired -> mandatory
        is UpdateUiState.Failed -> mandatory
        else -> false
    }

    companion object {
        const val AUTO_CHECK_INTERVAL_MS = 12L * 60L * 60L * 1_000L
        private const val APK_MIME = "application/vnd.android.package-archive"
    }

    private data class ActiveDownload(val update: AppUpdate, val mandatory: Boolean)
}

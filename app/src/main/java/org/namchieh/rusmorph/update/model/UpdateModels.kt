package org.namchieh.rusmorph.update.model

import android.net.Uri
import androidx.annotation.Keep

@Keep
data class UpdateManifest(
    val platform: String? = null,
    val channel: String? = null,
    val versionCode: Int = 0,
    val versionName: String? = null,
    val minSupportedVersionCode: Int = 1,
    val forceUpdate: Boolean = false,
    val publishedAt: String? = null,
    val title: String? = null,
    val releaseNotes: List<String>? = null,
    val apk: UpdateApkManifest? = null,
    val releasePageUrl: String? = null,
)

@Keep
data class UpdateApkManifest(
    val url: String? = null,
    val sha256: String? = null,
    val size: Long = 0,
)

data class AppUpdate(
    val versionCode: Int,
    val versionName: String,
    val minSupportedVersionCode: Int,
    val forceUpdate: Boolean,
    val title: String,
    val releaseNotes: List<String>,
    val apkUrl: String,
    val apkSha256: String,
    val apkSize: Long,
    val releasePageUrl: String?,
)

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data class Available(val update: AppUpdate, val mandatory: Boolean) : UpdateUiState
    data class Downloading(val update: AppUpdate, val mandatory: Boolean) : UpdateUiState
    data class Verifying(val update: AppUpdate, val mandatory: Boolean) : UpdateUiState
    data class Downloaded(val update: AppUpdate, val fileUri: Uri, val mandatory: Boolean) : UpdateUiState
    data class PermissionRequired(val update: AppUpdate, val fileUri: Uri, val mandatory: Boolean) : UpdateUiState
    data class Failed(val update: AppUpdate, val mandatory: Boolean, val message: String) : UpdateUiState
}

sealed interface ManualUpdateStatus {
    data object Idle : ManualUpdateStatus
    data object Checking : ManualUpdateStatus
    data object UpToDate : ManualUpdateStatus
    data class Failed(val message: String = "检查更新失败，请稍后重试") : ManualUpdateStatus
}

sealed interface UpdateDecision {
    data object None : UpdateDecision
    data class Available(val update: AppUpdate, val mandatory: Boolean) : UpdateDecision
}

object UpdateRules {
    fun decide(
        update: AppUpdate,
        localVersionCode: Int,
        skippedVersionCode: Int,
        automatic: Boolean,
    ): UpdateDecision {
        if (update.versionCode <= localVersionCode) return UpdateDecision.None
        val mandatory = update.forceUpdate || localVersionCode < update.minSupportedVersionCode
        if (automatic && !mandatory && update.versionCode == skippedVersionCode) return UpdateDecision.None
        return UpdateDecision.Available(update, mandatory)
    }
}

object UpdateFlavorPolicy {
    fun allowsAutomaticCheck(flavor: String, debug: Boolean): Boolean =
        flavor == "production" && !debug

    fun allowsManualCheck(flavor: String): Boolean = flavor == "production"
}

fun UpdateManifest.toValidatedUpdate(): AppUpdate? {
    val normalizedVersionName = versionName?.trim()?.takeIf { it.isNotEmpty() && it.length <= 40 } ?: return null
    val apkManifest = apk ?: return null
    val url = apkManifest.url?.trim()?.takeIf(::isSafeHttpsUrl) ?: return null
    val hash = apkManifest.sha256?.trim()?.lowercase()?.takeIf { SHA_256.matches(it) } ?: return null
    if (versionCode <= 0 || minSupportedVersionCode <= 0 || apkManifest.size < 0) return null
    if (platform != "android" || channel != "stable") return null
    val notes = releaseNotes.orEmpty().take(MAX_RELEASE_NOTES).mapNotNull { note ->
        note.trim().takeIf { it.isNotEmpty() }?.take(MAX_RELEASE_NOTE_LENGTH)
    }
    val pageUrl = releasePageUrl?.trim()?.takeIf(::isSafeHttpsUrl)
    return AppUpdate(
        versionCode = versionCode,
        versionName = normalizedVersionName,
        minSupportedVersionCode = minSupportedVersionCode,
        forceUpdate = forceUpdate,
        title = title?.trim()?.takeIf { it.isNotEmpty() }?.take(160) ?: "RusMorph $normalizedVersionName",
        releaseNotes = notes,
        apkUrl = url,
        apkSha256 = hash,
        apkSize = apkManifest.size,
        releasePageUrl = pageUrl,
    )
}

private fun isSafeHttpsUrl(value: String): Boolean = runCatching {
    val uri = java.net.URI(value)
    uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank() && uri.userInfo == null
}.getOrDefault(false)

private val SHA_256 = Regex("^[a-fA-F0-9]{64}$")
private const val MAX_RELEASE_NOTES = 20
private const val MAX_RELEASE_NOTE_LENGTH = 500

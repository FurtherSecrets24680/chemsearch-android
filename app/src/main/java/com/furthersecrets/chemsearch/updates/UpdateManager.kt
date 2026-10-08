package com.furthersecrets.chemsearch.updates

import android.content.Context
import android.content.SharedPreferences
import com.furthersecrets.chemsearch.BuildConfig
import com.furthersecrets.chemsearch.R
import com.furthersecrets.chemsearch.data.UpdateStatus
import com.furthersecrets.chemsearch.data.settings.SettingsRepository
import com.furthersecrets.chemsearch.data.updates.UpdateRepository
import com.furthersecrets.chemsearch.ui.DebugLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * State-holding owner of app-update flow: periodic/manual checks, APK download
 * with progress, install prompting and notification bookkeeping. Backed by
 * [UpdateRepository] for mechanics.
 */
class UpdateManager(
    context: Context,
    private val prefs: SharedPreferences,
    private val settingsRepository: SettingsRepository,
    private val updateRepository: UpdateRepository,
    private val scope: CoroutineScope
) {
    private val appContext = context.applicationContext

    private val _updateNotificationsEnabled =
        MutableStateFlow(prefs.getBoolean(PREF_UPDATE_NOTIFICATIONS, true))
    val updateNotificationsEnabled: StateFlow<Boolean> = _updateNotificationsEnabled.asStateFlow()

    private val _updateStatus = MutableStateFlow(
        UpdateStatus(lastCheckedAt = prefs.getLong(PREF_UPDATE_LAST_CHECK, 0L).takeIf { it != 0L })
    )
    val updateStatus: StateFlow<UpdateStatus> = _updateStatus.asStateFlow()

    fun reloadFromPrefs() {
        _updateNotificationsEnabled.value = prefs.getBoolean(PREF_UPDATE_NOTIFICATIONS, true)
        _updateStatus.update {
            it.copy(lastCheckedAt = prefs.getLong(PREF_UPDATE_LAST_CHECK, 0L).takeIf { ts -> ts != 0L })
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        if (!BuildConfig.GITHUB_UPDATES_ENABLED) return
        _updateNotificationsEnabled.value = enabled
        prefs.edit().putBoolean(PREF_UPDATE_NOTIFICATIONS, enabled).apply()
        settingsRepository.setUpdateNotificationsEnabled(enabled)
        if (enabled) checkForUpdates()
    }

    fun checkForUpdates(manual: Boolean = false) {
        if (!BuildConfig.GITHUB_UPDATES_ENABLED) {
            return
        }
        if (_updateStatus.value.isChecking) return
        val now = System.currentTimeMillis()
        if (!manual) {
            val lastCheck = prefs.getLong(PREF_UPDATE_LAST_CHECK, 0L)
            if (lastCheck != 0L && now - lastCheck < UPDATE_CHECK_INTERVAL_MS) return
        }
        _updateStatus.update { it.copy(isChecking = true, error = null) }
        scope.launch {
            val result = updateRepository.fetchLatestRelease()
            val currentStatus = _updateStatus.value
            val nextStatus = result.fold(
                onSuccess = { outcome ->
                    val downloadUrl = outcome.downloadUrl
                    val releaseUrl = outcome.releaseUrl
                    if (outcome.updateAvailable) {
                        updateRepository.maybeNotifyUpdate(
                            latestTag = outcome.latestTag!!,
                            downloadUrl = downloadUrl,
                            releaseUrl = releaseUrl,
                            notificationsEnabled = _updateNotificationsEnabled.value,
                            lastNotifiedTag = prefs.getString(PREF_UPDATE_LAST_NOTIFIED, null)
                        ) { tag ->
                            prefs.edit().putString(PREF_UPDATE_LAST_NOTIFIED, tag).apply()
                        }
                    }
                    currentStatus.copy(
                        isChecking = false,
                        latestVersion = outcome.latestTag,
                        updateAvailable = outcome.updateAvailable,
                        downloadUrl = downloadUrl,
                        releaseUrl = releaseUrl,
                        changelog = outcome.changelog,
                        lastCheckedAt = now,
                        error = null
                    )
                },
                onFailure = { e ->
                    currentStatus.copy(
                        isChecking = false,
                        error = e.message ?: appContext.getString(R.string.ui_error_update_check_failed),
                        lastCheckedAt = now
                    )
                }
            )
            _updateStatus.value = nextStatus
            prefs.edit().putLong(PREF_UPDATE_LAST_CHECK, now).apply()
        }
    }

    fun downloadUpdateApk() {
        if (!BuildConfig.GITHUB_UPDATES_ENABLED) {
            return
        }
        val status = _updateStatus.value
        if (status.isDownloadingUpdate) return

        status.latestVersion
            ?.let(updateRepository::findDownloadedApk)
            ?.let { file ->
                promptInstallUpdate(file)
                return
            }

        val downloadUrl = status.downloadUrl?.takeIf { it.isNotBlank() }
        if (downloadUrl == null) {
            _updateStatus.update { it.copy(error = appContext.getString(R.string.ui_error_no_apk_download_link)) }
            return
        }

        _updateStatus.update {
            it.copy(
                isDownloadingUpdate = true,
                updateDownloadProgress = 0f,
                downloadedUpdateApkPath = null,
                error = null
            )
        }

        scope.launch {
            val result = withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    updateRepository.downloadUpdateApkFile(
                        url = downloadUrl,
                        version = status.latestVersion ?: "update"
                    ) { progress ->
                        _updateStatus.update {
                            it.copy(
                                isDownloadingUpdate = true,
                                updateDownloadProgress = progress.coerceIn(0f, 1f),
                                error = null
                            )
                        }
                    }
                }
            }

            result.onSuccess { apkFile ->
                _updateStatus.update {
                    it.copy(
                        isDownloadingUpdate = false,
                        updateDownloadProgress = 1f,
                        downloadedUpdateApkPath = apkFile.absolutePath,
                        error = null
                    )
                }
                promptInstallUpdate(apkFile)
            }.onFailure { e ->
                _updateStatus.update {
                    it.copy(
                        isDownloadingUpdate = false,
                        updateDownloadProgress = null,
                        downloadedUpdateApkPath = null,
                        error = e.message ?: appContext.getString(R.string.ui_error_update_download_failed)
                    )
                }
                DebugLog.e("ChemSearch", "Update download failed: ${e.message}")
            }
        }
    }

    private fun promptInstallUpdate(apkFile: File) {
        if (!apkFile.exists() || apkFile.length() == 0L) {
            _updateStatus.update {
                it.copy(
                    downloadedUpdateApkPath = null,
                    updateDownloadProgress = null,
                    error = appContext.getString(R.string.ui_error_apk_missing)
                )
            }
            return
        }
        val errorRes = updateRepository.promptInstallUpdate(apkFile)
        if (errorRes != null) {
            _updateStatus.update { it.copy(error = appContext.getString(errorRes)) }
        }
    }

    fun sendDebugUpdateNotification() {
        val url = _updateStatus.value.downloadUrl
            ?: _updateStatus.value.releaseUrl
        updateRepository.sendDebugUpdateNotification(url)
    }

    companion object {
        private const val PREF_UPDATE_NOTIFICATIONS = "update_notifications"
        private const val PREF_UPDATE_LAST_CHECK = "update_last_check"
        private const val PREF_UPDATE_LAST_NOTIFIED = "update_last_notified"
        private const val UPDATE_CHECK_INTERVAL_MS = 12 * 60 * 60 * 1000L
    }
}

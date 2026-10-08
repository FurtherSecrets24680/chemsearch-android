package com.furthersecrets.chemsearch.data.updates

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.furthersecrets.chemsearch.BuildConfig
import com.furthersecrets.chemsearch.R
import com.furthersecrets.chemsearch.data.ApiClient
import com.furthersecrets.chemsearch.data.UpdateStatus
import com.furthersecrets.chemsearch.ui.DebugLog
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.IOException

/**
 * Owns update checking, APK downloading with progress, install prompting and
 * update notifications. Gated by [BuildConfig.GITHUB_UPDATES_ENABLED].
 */
class UpdateRepository(private val context: Context) {

    data class CheckOutcome(
        val latestTag: String?,
        val downloadUrl: String?,
        val releaseUrl: String?,
        val changelog: String?,
        val updateAvailable: Boolean
    )

    suspend fun fetchLatestRelease(): Result<CheckOutcome> = withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            val release = ApiClient.github.getLatestRelease()
            val latestTag = release.tagName?.trim().orEmpty()
            if (latestTag.isBlank()) throw IOException("No release tag")
            val downloadUrl = release.assets
                ?.firstOrNull { it.browserDownloadUrl?.endsWith(".apk", ignoreCase = true) == true }
                ?.browserDownloadUrl
            CheckOutcome(
                latestTag = latestTag,
                downloadUrl = downloadUrl,
                releaseUrl = release.htmlUrl,
                changelog = release.body,
                updateAvailable = isUpdateAvailable(BuildConfig.VERSION_NAME, latestTag)
            )
        }
    }

    fun downloadUpdateApkFile(
        url: String,
        version: String,
        onProgress: (Float) -> Unit
    ): File {
        val safeVersion = version.replace(Regex("""[^A-Za-z0-9._-]"""), "_")
        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val target = File(updatesDir, "chemsearch-$safeVersion.apk")
        val temp = File(updatesDir, "chemsearch-$safeVersion.apk.part")

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "ChemSearch/${BuildConfig.VERSION_NAME} (Android; github.com/FurtherSecrets24680)")
            .build()

        ApiClient.rawHttp.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException(context.getString(R.string.ui_error_download_failed_http, response.code))
            }
            val body = response.body
            val totalBytes = body.contentLength()
            var copiedBytes = 0L
            var lastProgress = 0f

            body.byteStream().use { input ->
                temp.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        copiedBytes += read
                        if (totalBytes > 0L) {
                            val progress = (copiedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                            if (progress - lastProgress >= 0.01f || progress >= 1f) {
                                lastProgress = progress
                                onProgress(progress)
                            }
                        }
                    }
                }
            }
        }

        if (temp.length() == 0L) {
            temp.delete()
            throw IOException(context.getString(R.string.ui_error_apk_empty))
        }
        if (target.exists()) target.delete()
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
        onProgress(1f)
        return target
    }

    fun findDownloadedApk(version: String): File? {
        val safeVersion = version.replace(Regex("""[^A-Za-z0-9._-]"""), "_")
        val file = File(File(context.cacheDir, "updates"), "chemsearch-$safeVersion.apk")
        return file.takeIf { it.exists() && it.length() > 0L }
    }

    /** Returns an error string resource id when install cannot proceed, else null and fires the intent. */
    fun promptInstallUpdate(apkFile: File): Int? {
        if (!apkFile.exists() || apkFile.length() == 0L) {
            return R.string.ui_error_apk_missing
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            val settingsIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(settingsIntent) }
            return R.string.ui_error_allow_installs
        }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        val installIntent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        runCatching { context.startActivity(installIntent) }
            .onFailure { return R.string.ui_error_could_not_open_installer }
        return null
    }

    // ---- Notifications ----

    fun maybeNotifyUpdate(
        latestTag: String,
        downloadUrl: String?,
        releaseUrl: String?,
        notificationsEnabled: Boolean,
        lastNotifiedTag: String?,
        onNotified: (String) -> Unit
    ) {
        if (!BuildConfig.GITHUB_UPDATES_ENABLED) return
        if (!notificationsEnabled) return
        if (latestTag.equals(lastNotifiedTag, ignoreCase = true)) return
        sendUpdateNotification(latestTag, downloadUrl ?: releaseUrl)
        onNotified(latestTag)
    }

    fun sendDebugUpdateNotification(url: String?) {
        if (!BuildConfig.GITHUB_UPDATES_ENABLED) {
            DebugLog.d("ChemSearch", "Update notification skipped in F-Droid build")
            return
        }
        val debugTag = "debug-${System.currentTimeMillis() % 100000}"
        val target = url
            ?: "https://github.com/FurtherSecrets24680/chemsearch-android/releases/latest"
        sendUpdateNotification(debugTag, target)
        DebugLog.d("ChemSearch", "Debug update notification sent ($debugTag)")
    }

    private fun sendUpdateNotification(latestTag: String, url: String?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            if (permission != PackageManager.PERMISSION_GRANTED) return
        }
        ensureUpdateChannel()
        val intent = url?.takeIf { it.isNotBlank() }?.let { Intent(Intent.ACTION_VIEW, Uri.parse(it)) }
        val pendingIntent = intent?.let {
            PendingIntent.getActivity(
                context,
                0,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        val builder = NotificationCompat.Builder(context, UPDATE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_chemsearch)
            .setContentTitle(context.getString(R.string.ui_notification_update_title))
            .setContentText(context.getString(R.string.ui_notification_update_text, latestTag))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        if (pendingIntent != null) builder.setContentIntent(pendingIntent)
        NotificationManagerCompat.from(context).notify(UPDATE_NOTIFICATION_ID, builder.build())
    }

    private fun ensureUpdateChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(UPDATE_CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            UPDATE_CHANNEL_ID,
            context.getString(R.string.ui_notification_channel_updates),
            NotificationManager.IMPORTANCE_DEFAULT
        )
        channel.description = context.getString(R.string.ui_notification_channel_updates_desc)
        manager.createNotificationChannel(channel)
    }

    // ---- Version comparison ----

    fun isUpdateAvailable(currentVersion: String, latestTag: String): Boolean {
        val currentBase = baseVersion(currentVersion)
        val latestBase = baseVersion(latestTag)
        if (currentBase.equals(latestBase, ignoreCase = true)) return false
        val currentParts = parseVersionParts(currentBase)
        val latestParts = parseVersionParts(latestBase)
        if (currentParts.isNotEmpty() && latestParts.isNotEmpty()) {
            return compareVersionParts(latestParts, currentParts) > 0
        }
        if (currentVersion.contains(latestBase, ignoreCase = true)) return false
        return true
    }

    private fun baseVersion(raw: String): String {
        val normalized = normalizeVersion(raw)
        return normalized.split(Regex("[+\\-\\s]")).firstOrNull().orEmpty()
    }

    private fun normalizeVersion(raw: String): String =
        raw.trim().removePrefix("v").removePrefix("V")

    private fun parseVersionParts(version: String): List<Int> {
        if (version.isBlank()) return emptyList()
        return version.split(".")
            .mapNotNull { part ->
                part.takeWhile { it.isDigit() }.toIntOrNull()
            }
    }

    private fun compareVersionParts(a: List<Int>, b: List<Int>): Int {
        val maxSize = maxOf(a.size, b.size)
        for (i in 0 until maxSize) {
            val av = a.getOrElse(i) { 0 }
            val bv = b.getOrElse(i) { 0 }
            if (av != bv) return av.compareTo(bv)
        }
        return 0
    }

    companion object {
        private const val UPDATE_CHANNEL_ID = "updates"
        private const val UPDATE_NOTIFICATION_ID = 901
        private const val DEFAULT_BUFFER_SIZE = 8 * 1024
    }
}

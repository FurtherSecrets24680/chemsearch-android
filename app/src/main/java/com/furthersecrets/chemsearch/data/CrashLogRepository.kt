package com.furthersecrets.chemsearch.data

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Debug
import android.os.Process
import android.os.StatFs
import android.os.SystemClock
import android.util.Log
import com.furthersecrets.chemsearch.BuildConfig
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

data class CrashLogEntry(
    val file: File,
    val capturedAt: Long,
    val exceptionType: String,
    val message: String,
    val threadName: String,
    val sizeBytes: Long
)

/**
 * Saves uncaught exceptions to app-private storage before Android terminates the process.
 *
 * Reports deliberately include a lot of context: full throwable and cause chains, every live
 * thread stack, device/build details, memory state, /proc information, and the in-app debug log.
 */
class CrashLogRepository(context: Context) {
    private val appContext = context.applicationContext
    private val logsDir = File(appContext.filesDir, DIRECTORY_NAME)
    private val writeLock = Any()
    private val recording = AtomicBoolean(false)
    private val processStartedAt = SystemClock.elapsedRealtime()
    private val processStartedAtWall = System.currentTimeMillis()

    @Volatile
    private var installed = false

    @Volatile
    private var recentLogs: () -> List<String> = { emptyList() }

    fun install(recentLogsProvider: () -> List<String>) {
        recentLogs = recentLogsProvider
        if (installed) return
        synchronized(this) {
            if (installed) return
            installed = true
            val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                record(thread, throwable)
                if (previousHandler != null) {
                    previousHandler.uncaughtException(thread, throwable)
                } else {
                    Process.killProcess(Process.myPid())
                    exitProcess(10)
                }
            }
        }
    }

    fun list(): List<CrashLogEntry> = synchronized(writeLock) {
        logsDir.listFiles { file ->
            file.isFile && file.name.startsWith(FILE_PREFIX) && file.name.endsWith(FILE_SUFFIX)
        }
            ?.mapNotNull { file -> runCatching { parseEntry(file) }.getOrNull() }
            ?.sortedByDescending { it.capturedAt }
            ?: emptyList()
    }

    fun read(entry: CrashLogEntry): String = runCatching { entry.file.readText() }.getOrDefault("")

    fun readAll(): String = list().joinToString(separator = "\n\n${"=".repeat(80)}\n\n") { entry ->
        read(entry)
    }

    fun delete(entry: CrashLogEntry) {
        synchronized(writeLock) {
            runCatching { entry.file.delete() }
        }
    }

    fun deleteAll() {
        synchronized(writeLock) {
            logsDir.listFiles()?.forEach { file ->
                if (file.isFile) runCatching { file.delete() }
            }
        }
    }

    fun totalBytes(): Long = synchronized(writeLock) {
        list().sumOf { it.sizeBytes }
    }

    fun directoryPath(): String = logsDir.absolutePath

    private fun record(thread: Thread, throwable: Throwable) {
        if (!recording.compareAndSet(false, true)) return
        try {
            synchronized(writeLock) {
                if (!logsDir.exists() && !logsDir.mkdirs()) return
                val capturedAt = System.currentTimeMillis()
                val stamp = fileStamp(capturedAt)
                var target = File(logsDir, "$FILE_PREFIX$stamp$FILE_SUFFIX")
                var duplicate = 1
                while (target.exists()) {
                    target = File(logsDir, "$FILE_PREFIX$stamp-${duplicate++}$FILE_SUFFIX")
                }
                val temporary = File(logsDir, ".$FILE_PREFIX$stamp.tmp")
                val report = buildReport(capturedAt, thread, throwable)
                FileOutputStream(temporary).use { output ->
                    output.write(report.toByteArray(Charsets.UTF_8))
                    output.flush()
                    runCatching { output.fd.sync() }
                }
                if (!temporary.renameTo(target)) {
                    temporary.copyTo(target, overwrite = true)
                    temporary.delete()
                }
                pruneLocked()
            }
        } catch (failure: Throwable) {
            Log.e(LOG_TAG, "Unable to persist crash report", failure)
        }
    }

    private fun buildReport(capturedAt: Long, thread: Thread, throwable: Throwable): String {
        val report = buildString {
            appendLine("ChemSearch Crash Report")
            appendLine("=======================")
            appendLine("Report ID: ${fileStamp(capturedAt)}")
            appendLine("Captured: ${isoTime(capturedAt)}")
            appendLine("Captured epoch ms: $capturedAt")
            appendLine("Exception: ${throwable.javaClass.name}")
            appendLine("Message: ${singleLine(throwable.message) ?: "(none)"}")
            appendLine(
                "Crashed thread: ${thread.name} (id=${threadId(thread)}, state=${thread.state}, " +
                    "priority=${thread.priority}, daemon=${thread.isDaemon})"
            )
            appendLine("Process: ${processName()}")
            appendLine("PID: ${Process.myPid()}")
            appendLine()

            appendLine("--- App ---")
            appendLine("Package: ${appContext.packageName}")
            appendLine("Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Build type: ${BuildConfig.BUILD_TYPE}")
            appendLine("Flavor: ${BuildConfig.FLAVOR}")
            appendLine("Debug build: ${BuildConfig.DEBUG}")
            appendLine("Installer: ${installerPackage()}")
            appendLine("Process started: ${isoTime(processStartedAtWall)}")
            appendLine("Process uptime: ${formatDuration(SystemClock.elapsedRealtime() - processStartedAt)}")
            appendLine("Device uptime: ${formatDuration(SystemClock.elapsedRealtime())}")
            appendLine("Wall clock: ${isoTime(System.currentTimeMillis())}")
            appendLine("Time zone: ${TimeZone.getDefault().id} (${ZoneId.systemDefault().id})")
            appendLine()

            appendLine("--- Device ---")
            appendLine("Manufacturer: ${Build.MANUFACTURER}")
            appendLine("Brand: ${Build.BRAND}")
            appendLine("Model: ${Build.MODEL}")
            appendLine("Device: ${Build.DEVICE}")
            appendLine("Product: ${Build.PRODUCT}")
            appendLine("Hardware: ${Build.HARDWARE}")
            appendLine("Board: ${Build.BOARD}")
            appendLine("Bootloader: ${Build.BOOTLOADER}")
            appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Android codename: ${Build.VERSION.CODENAME}")
            appendLine("Incremental: ${Build.VERSION.INCREMENTAL}")
            appendLine("Security patch: ${Build.VERSION.SECURITY_PATCH}")
            appendLine("Build ID: ${Build.ID}")
            appendLine("Build type: ${Build.TYPE}")
            appendLine("Build tags: ${Build.TAGS}")
            appendLine("Build time: ${Build.TIME}")
            appendLine("Fingerprint: ${Build.FINGERPRINT}")
            appendLine("Supported ABIs: ${Build.SUPPORTED_ABIS.joinToString()}")
            appendLine("Primary ABI: ${Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"}")
            appendLine("Available processors: ${Runtime.getRuntime().availableProcessors()}")
            appendLine("Locale: ${currentLocale()}")
            appendLine("Screen: ${screenDescription()}")
            appendLine("Kernel: ${runCatching { System.getProperty("os.version") }.getOrNull() ?: "unknown"}")
            appendLine()

            appendLine("--- Memory ---")
            appendMemory()
            appendLine()

            appendLine("--- Storage ---")
            appendStorage()
            appendLine()

            appendLine("--- Process status (/proc/self/status) ---")
            appendProcFile("/proc/self/status")
            appendLine()
            appendLine("--- Process limits (/proc/self/limits) ---")
            appendProcFile("/proc/self/limits")
            appendLine()

            appendLine("--- Throwable stack trace ---")
            val stack = StringWriter()
            throwable.printStackTrace(PrintWriter(stack))
            appendLine(stack.toString())
            appendLine()

            appendLine("--- Causes and suppressed exceptions ---")
            appendCauseSummary(throwable)
            appendLine()

            appendLine("--- All live threads ---")
            appendThreadDumps(thread)
            appendLine()

            val recent = runCatching(recentLogs).getOrDefault(emptyList())
            appendLine("--- Recent in-app debug log (${recent.size} lines) ---")
            if (recent.isEmpty()) {
                appendLine("(No debug log lines captured)")
            } else {
                recent.takeLast(MAX_LOG_LINES).forEach { appendLine(it) }
            }
        }
        return report.take(MAX_REPORT_CHARS) + if (report.length > MAX_REPORT_CHARS) {
            "\n\n[Report truncated after $MAX_REPORT_CHARS characters]"
        } else {
            ""
        }
    }

    private fun StringBuilder.appendMemory() {
        val runtime = Runtime.getRuntime()
        appendLine("JVM heap max: ${megabytes(runtime.maxMemory())}")
        appendLine("JVM heap total: ${megabytes(runtime.totalMemory())}")
        appendLine("JVM heap free: ${megabytes(runtime.freeMemory())}")
        appendLine("JVM heap used: ${megabytes(runtime.totalMemory() - runtime.freeMemory())}")
        appendLine("Native heap allocated: ${megabytes(Debug.getNativeHeapAllocatedSize())}")
        appendLine("Native heap size: ${megabytes(Debug.getNativeHeapSize())}")
        appendLine("Native heap free: ${megabytes(Debug.getNativeHeapFreeSize())}")

        val activityManager = appContext.getSystemService(ActivityManager::class.java)
        if (activityManager != null) {
            runCatching {
                val info = ActivityManager.MemoryInfo()
                activityManager.getMemoryInfo(info)
                appendLine("System RAM total: ${megabytes(info.totalMem)}")
                appendLine("System RAM available: ${megabytes(info.availMem)}")
                appendLine("System RAM threshold: ${megabytes(info.threshold)}")
                appendLine("System low memory: ${info.lowMemory}")
            }.onFailure { appendLine("System RAM: unavailable (${it.message})") }

            runCatching {
                activityManager.getProcessMemoryInfo(intArrayOf(Process.myPid())).firstOrNull()?.let { info ->
                    appendLine("Process total PSS: ${info.totalPss} KB")
                    appendLine("Process private dirty: ${info.totalPrivateDirty} KB")
                    appendLine("Process private clean: ${info.totalPrivateClean} KB")
                    appendLine("Process shared dirty: ${info.totalSharedDirty} KB")
                    appendLine("Process shared clean: ${info.totalSharedClean} KB")
                    appendLine("Process swap: ${info.totalSwappablePss} KB (swappable PSS)")
                }
            }.onFailure { appendLine("Process memory: unavailable (${it.message})") }
        } else {
            appendLine("System RAM: ActivityManager unavailable")
        }
    }

    private fun StringBuilder.appendStorage() {
        runCatching {
            val stat = StatFs(appContext.filesDir.absolutePath)
            appendLine("Data partition total: ${megabytes(stat.totalBytes)}")
            appendLine("Data partition available: ${megabytes(stat.availableBytes)}")
            appendLine("Data partition block size: ${stat.blockSizeLong} bytes")
        }.onFailure { appendLine("Data partition: unavailable (${it.message})") }
        appendLine("App files dir: ${appContext.filesDir.absolutePath}")
        appendLine("Crash log dir: ${logsDir.absolutePath}")
        appendLine("Existing crash reports before this one: ${logsDir.listFiles()?.count { it.isFile } ?: 0}")
        appendLine("Free JVM heap headroom: ${megabytes(Runtime.getRuntime().maxMemory() - Runtime.getRuntime().totalMemory() + Runtime.getRuntime().freeMemory())}")
    }

    private fun StringBuilder.appendProcFile(path: String) {
        runCatching {
            File(path).readLines().forEach { line -> appendLine(line) }
        }.onFailure {
            appendLine("($path unavailable: ${it.message})")
        }
    }

    private fun StringBuilder.appendCauseSummary(throwable: Throwable) {
        var current: Throwable? = throwable
        val seen = mutableSetOf<Throwable>()
        var depth = 0
        while (current != null && seen.add(current) && depth < 25) {
            if (depth == 0) {
                appendLine("Primary: ${current.javaClass.name}: ${singleLine(current.message) ?: "(no message)"}")
            } else {
                appendLine("Cause $depth: ${current.javaClass.name}: ${singleLine(current.message) ?: "(no message)"}")
                current.stackTrace.firstOrNull()?.let { appendLine("  at $it") }
            }
            current.suppressed.forEachIndexed { index, suppressed ->
                appendLine(
                    "Suppressed [$depth.$index]: ${suppressed.javaClass.name}: " +
                        (singleLine(suppressed.message) ?: "(no message)")
                )
                suppressed.stackTrace.firstOrNull()?.let { appendLine("  at $it") }
            }
            current = current.cause
            depth++
        }
        if (depth == 0) appendLine("(No throwable details)")
    }

    private fun StringBuilder.appendThreadDumps(crashedThread: Thread) {
        val allStacks = runCatching { Thread.getAllStackTraces() }.getOrNull()
        if (allStacks.isNullOrEmpty()) {
            appendLine("(Thread stacks unavailable)")
            return
        }
        val ordered = allStacks.entries.sortedWith(
            compareByDescending<Map.Entry<Thread, Array<StackTraceElement>>> {
                threadId(it.key) == threadId(crashedThread)
            }.thenBy { it.key.name }
        )
        ordered.forEach { (thread, frames) ->
            appendLine()
            appendLine(
                "Thread \"${thread.name}\" id=${threadId(thread)} state=${thread.state} " +
                    "priority=${thread.priority} daemon=${thread.isDaemon}" +
                    if (threadId(thread) == threadId(crashedThread)) "  <== CRASHED" else ""
            )
            frames.forEach { frame -> appendLine("    at $frame") }
        }
    }

    private fun parseEntry(file: File): CrashLogEntry {
        val header = file.useLines { lines -> lines.take(32).toList() }
        fun value(key: String): String? = header
            .firstOrNull { it.startsWith("$key:") }
            ?.substringAfter(':')
            ?.trim()

        val capturedAt = value("Captured epoch ms")?.toLongOrNull() ?: file.lastModified()
        val exceptionType = value("Exception") ?: "Unknown crash"
        val message = value("Message").orEmpty().let { if (it == "(none)") "" else it }
        val threadName = value("Crashed thread")
            ?.substringBefore(" (")
            ?.trim()
            .orEmpty()
            .ifBlank { "unknown" }
        return CrashLogEntry(
            file = file,
            capturedAt = capturedAt,
            exceptionType = exceptionType,
            message = message,
            threadName = threadName,
            sizeBytes = file.length()
        )
    }

    private fun pruneLocked() {
        val reports = logsDir.listFiles { file ->
            file.isFile && file.name.startsWith(FILE_PREFIX) && file.name.endsWith(FILE_SUFFIX)
        }?.sortedByDescending { it.lastModified() } ?: return

        reports.drop(MAX_REPORTS).forEach { runCatching { it.delete() } }

        var remaining = reports.take(MAX_REPORTS)
        var total = remaining.sumOf { it.length() }
        while (remaining.size > 1 && total > MAX_TOTAL_BYTES) {
            val oldest = remaining.last()
            total -= oldest.length()
            runCatching { oldest.delete() }
            remaining = remaining.dropLast(1)
        }

        val staleTempCutoff = System.currentTimeMillis() - TEMP_FILE_MAX_AGE_MS
        logsDir.listFiles { file ->
            file.isFile && file.name.startsWith(".$FILE_PREFIX") && file.name.endsWith(".tmp") &&
                file.lastModified() < staleTempCutoff
        }?.forEach { runCatching { it.delete() } }
    }

    private fun processName(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { android.app.Application.getProcessName() }.getOrNull() ?: appContext.packageName
        } else {
            appContext.packageName
        }

    private fun installerPackage(): String = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            appContext.packageManager.getInstallSourceInfo(appContext.packageName).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            appContext.packageManager.getInstallerPackageName(appContext.packageName)
        }
    }.getOrNull() ?: "unknown"

    private fun currentLocale(): String {
        val configuration = appContext.resources.configuration
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            configuration.locales[0]?.toLanguageTag() ?: "unknown"
        } else {
            @Suppress("DEPRECATION")
            configuration.locale?.toLanguageTag() ?: "unknown"
        }
    }

    private fun screenDescription(): String {
        val metrics = appContext.resources.displayMetrics
        return "${metrics.widthPixels}x${metrics.heightPixels} px, " +
            "${metrics.densityDpi} dpi, density ${metrics.density}"
    }

    private fun isoTime(epochMs: Long): String =
        DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
            Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault())
        )

    private fun fileStamp(epochMs: Long): String =
        java.text.SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US)
            .apply { timeZone = TimeZone.getDefault() }
            .format(Date(epochMs))

    private fun megabytes(bytes: Long): String =
        String.format(Locale.US, "%.2f MB (%d bytes)", bytes / 1048576.0, bytes)

    private fun formatDuration(millis: Long): String {
        val safe = millis.coerceAtLeast(0L)
        val days = safe / 86_400_000L
        val hours = (safe % 86_400_000L) / 3_600_000L
        val minutes = (safe % 3_600_000L) / 60_000L
        val seconds = (safe % 60_000L) / 1_000L
        return buildString {
            if (days > 0) append("${days}d ")
            if (hours > 0) append("${hours}h ")
            if (minutes > 0) append("${minutes}m ")
            append("${seconds}s")
        }
    }

    private fun singleLine(value: String?): String? =
        value?.replace('\n', ' ')?.replace('\r', ' ')?.trim()?.takeIf { it.isNotEmpty() }

    @Suppress("DEPRECATION")
    private fun threadId(thread: Thread): Long = thread.id

    companion object {
        const val DIRECTORY_NAME = "crash_logs"
        private const val FILE_PREFIX = "crash-"
        private const val FILE_SUFFIX = ".txt"
        private const val LOG_TAG = "ChemSearchCrash"
        private const val MAX_REPORTS = 30
        private const val MAX_TOTAL_BYTES = 16L * 1024L * 1024L
        private const val MAX_LOG_LINES = 300
        private const val MAX_REPORT_CHARS = 1_500_000
        private const val TEMP_FILE_MAX_AGE_MS = 60L * 60L * 1000L
    }
}

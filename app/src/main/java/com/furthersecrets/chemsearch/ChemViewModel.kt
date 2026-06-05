package com.furthersecrets.chemsearch

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Base64
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.furthersecrets.chemsearch.data.*
import com.furthersecrets.chemsearch.data.local.ChemSearchDatabase
import com.furthersecrets.chemsearch.data.local.OfflineDownloadRepository
import com.furthersecrets.chemsearch.data.settings.AppSettingsStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.IOException
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.furthersecrets.chemsearch.ui.DebugLog
import okhttp3.Request
import java.io.File
import java.util.Locale
import kotlin.random.Random

internal const val PubChemRandomCompoundUpperBound = 123_880_397L

internal fun randomPubChemCid(
    random: Random = Random.Default,
    upperBound: Long = PubChemRandomCompoundUpperBound
): Long = random.nextLong(upperBound) + 1L

class ChemViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("chemsearch_prefs", Context.MODE_PRIVATE)

    private val gson = Gson()
    private val settingsStore = AppSettingsStore(application)
    private val offlineDownloadRepository = OfflineDownloadRepository(
        dao = ChemSearchDatabase.getInstance(application).downloadedCompoundDao(),
        prefs = prefs,
        gson = gson
    )
    private val _favorites = MutableStateFlow<List<FavoriteCompound>>(loadFavorites())
    val favorites: StateFlow<List<FavoriteCompound>> = _favorites.asStateFlow()

    private val _recentSearches = MutableStateFlow<List<RecentSearch>>(loadRecentSearches())
    val recentSearches: StateFlow<List<RecentSearch>> = _recentSearches.asStateFlow()

    private val _downloads = MutableStateFlow<List<DownloadedCompound>>(loadDownloads())
    val downloads: StateFlow<List<DownloadedCompound>> = _downloads.asStateFlow()

    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite.asStateFlow()

    private val _isDownloaded = MutableStateFlow(false)
    val isDownloaded: StateFlow<Boolean> = _isDownloaded.asStateFlow()

    private val _isSavingOffline = MutableStateFlow(false)
    val isSavingOffline: StateFlow<Boolean> = _isSavingOffline.asStateFlow()

    private val _offlineDownloadProgress = MutableStateFlow<Float?>(null)
    val offlineDownloadProgress: StateFlow<Float?> = _offlineDownloadProgress.asStateFlow()

    private val _uiState = MutableStateFlow(ChemUiState())
    val uiState: StateFlow<ChemUiState> = _uiState.asStateFlow()

    private val _structureSearchState = MutableStateFlow(StructureSearchUiState())
    val structureSearchState: StateFlow<StructureSearchUiState> = _structureSearchState.asStateFlow()

    private val _advancedSearchState = MutableStateFlow(AdvancedSearchUiState())
    val advancedSearchState: StateFlow<AdvancedSearchUiState> = _advancedSearchState.asStateFlow()

    private val _isDarkTheme = MutableStateFlow(prefs.getBoolean("dark_theme", false))
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    private val _colorScheme = MutableStateFlow(getSavedColorScheme())
    val colorScheme: StateFlow<AppColorScheme> = _colorScheme.asStateFlow()

    private val _autoSuggest = MutableStateFlow(prefs.getBoolean("auto_suggest", true))
    val autoSuggest: StateFlow<Boolean> = _autoSuggest.asStateFlow()

    private val _compactMode = MutableStateFlow(prefs.getBoolean("compact_mode", false))
    val compactMode: StateFlow<Boolean> = _compactMode.asStateFlow()

    private val _oledDarkTheme = MutableStateFlow(prefs.getBoolean("oled_dark_theme", false))
    val oledDarkTheme: StateFlow<Boolean> = _oledDarkTheme.asStateFlow()

    private val _defaultDescSource = MutableStateFlow(getSavedDescSource())
    val defaultDescSource: StateFlow<DescSource> = _defaultDescSource.asStateFlow()

    private val _defaultStructureView = MutableStateFlow(getSavedDefaultStructureView())
    val defaultStructureView: StateFlow<DefaultStructureView> = _defaultStructureView.asStateFlow()

    private val _offlineDownloadQuality = MutableStateFlow(getSavedOfflineDownloadQuality())
    val offlineDownloadQuality: StateFlow<OfflineDownloadQuality> = _offlineDownloadQuality.asStateFlow()

    private val _formulaDisplayStyle = MutableStateFlow(getSavedFormulaDisplayStyle())
    val formulaDisplayStyle: StateFlow<FormulaDisplayStyle> = _formulaDisplayStyle.asStateFlow()

    private val _cacheSizeLimit = MutableStateFlow(getSavedCacheSizeLimit())
    val cacheSizeLimit: StateFlow<CacheSizeLimit> = _cacheSizeLimit.asStateFlow()

    private val _cacheRetention = MutableStateFlow(getSavedCacheRetention())
    val cacheRetention: StateFlow<CacheRetention> = _cacheRetention.asStateFlow()

    private val _reduceMotion = MutableStateFlow(prefs.getBoolean("reduce_motion", false))
    val reduceMotion: StateFlow<Boolean> = _reduceMotion.asStateFlow()

    private val _highContrastOutlines = MutableStateFlow(prefs.getBoolean("high_contrast_outlines", false))
    val highContrastOutlines: StateFlow<Boolean> = _highContrastOutlines.asStateFlow()

    private val _cacheSizeBytes = MutableStateFlow(0L)
    val cacheSizeBytes: StateFlow<Long> = _cacheSizeBytes.asStateFlow()

    private val _cacheDirPath = MutableStateFlow(prefs.getString("cache_dir", "") ?: "")
    val cacheDirPath: StateFlow<String> = _cacheDirPath.asStateFlow()

    private val _hasGeminiKey = MutableStateFlow(getGeminiKey()?.isNotBlank() == true)
    val hasGeminiKey: StateFlow<Boolean> = _hasGeminiKey.asStateFlow()

    private val _hasGroqKey = MutableStateFlow(getGroqKey()?.isNotBlank() == true)
    val hasGroqKey: StateFlow<Boolean> = _hasGroqKey.asStateFlow()

    private val _aiKeyStatus = MutableStateFlow(loadAiKeyStatus())
    val aiKeyStatus: StateFlow<Map<AiProvider, Boolean>> = _aiKeyStatus.asStateFlow()

    private val _aiModelCatalogs = MutableStateFlow(loadAiModelCatalogs())
    val aiModelCatalogs: StateFlow<Map<AiProvider, AiModelCatalog>> = _aiModelCatalogs.asStateFlow()

    private val _updateNotificationsEnabled = MutableStateFlow(prefs.getBoolean(PREF_UPDATE_NOTIFICATIONS, true))
    val updateNotificationsEnabled: StateFlow<Boolean> = _updateNotificationsEnabled.asStateFlow()

    private val _updateStatus = MutableStateFlow(
        UpdateStatus(lastCheckedAt = prefs.getLong(PREF_UPDATE_LAST_CHECK, 0L).takeIf { it != 0L })
    )
    val updateStatus: StateFlow<UpdateStatus> = _updateStatus.asStateFlow()

    private val _showWelcome = MutableStateFlow(!prefs.getBoolean(PREF_WELCOME_SKIPPED, false))
    val showWelcome: StateFlow<Boolean> = _showWelcome.asStateFlow()

    init {
        DebugLog.verbose = prefs.getBoolean("debug_verbose", false)
        val savedProvider = AiProvider.entries.firstOrNull { it.name == prefs.getString("ai_provider", null) } ?: AiProvider.GEMINI
        _uiState.update { it.copy(history = recentQueries(), aiProvider = savedProvider) }
        viewModelScope.launch {
            settingsStore.migrateSharedPreferencesIfNeeded(prefs)
            settingsStore.settings.collect { settings ->
                _isDarkTheme.value = settings.isDarkTheme
                _colorScheme.value = settings.colorScheme
                _autoSuggest.value = settings.autoSuggest
                _compactMode.value = settings.compactMode
                _oledDarkTheme.value = settings.oledDarkTheme
                _defaultDescSource.value = settings.descSource
                _cacheDirPath.value = settings.cacheDir
                _updateNotificationsEnabled.value = settings.updateNotificationsEnabled
                _showWelcome.value = !settings.welcomeSkipped
                _defaultStructureView.value = settings.defaultStructureView
                _offlineDownloadQuality.value = settings.offlineDownloadQuality
                _formulaDisplayStyle.value = settings.formulaDisplayStyle
                _cacheSizeLimit.value = settings.cacheSizeLimit
                _cacheRetention.value = settings.cacheRetention
                _reduceMotion.value = settings.reduceMotion
                _highContrastOutlines.value = settings.highContrastOutlines
            }
        }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                offlineDownloadRepository.migrateLegacyDownloadsIfNeeded()
            }
            offlineDownloadRepository.downloads
                .catch { e -> DebugLog.e("ChemSearch", "Download database read failed: ${e.message}") }
                .collect { downloads ->
                    _downloads.value = downloads.mapNotNull { it.normalizedOrNull() }
                }
        }
        viewModelScope.launch {
            combine(
                _uiState.map { it.cid }.distinctUntilChanged(),
                _favorites
            ) { cid, favorites ->
                cid?.let { selectedCid -> favorites.any { it.cid == selectedCid } } ?: false
            }
                .distinctUntilChanged()
                .collect { isFavoriteNow ->
                    _isFavorite.value = isFavoriteNow
                }
        }
        viewModelScope.launch {
            combine(
                _uiState.map { it.cid }.distinctUntilChanged(),
                _downloads
            ) { cid, downloads ->
                cid?.let { selectedCid -> downloads.any { it.cid == selectedCid } } ?: false
            }
                .distinctUntilChanged()
                .collect { isDownloadedNow ->
                    _isDownloaded.value = isDownloadedNow
                }
        }
        refreshCacheSizeAsync()
        if (BuildConfig.GITHUB_UPDATES_ENABLED) {
            checkForUpdates()
        }
    }

    fun isAiProviderSet(): Boolean = prefs.contains("ai_provider")

    fun skipWelcome() {
        _showWelcome.value = false
        prefs.edit().putBoolean(PREF_WELCOME_SKIPPED, true).apply()
        viewModelScope.launch { settingsStore.setWelcomeSkipped(true) }
        DebugLog.d("ChemSearch", "Welcome screen skipped")
    }

    fun showWelcomeAgain() {
        prefs.edit().putBoolean(PREF_WELCOME_SKIPPED, false).apply()
        _showWelcome.value = true
        viewModelScope.launch { settingsStore.setWelcomeSkipped(false) }
        DebugLog.d("ChemSearch", "Welcome screen opened from debug settings")
    }

    fun setUpdateNotificationsEnabled(enabled: Boolean) {
        if (!BuildConfig.GITHUB_UPDATES_ENABLED) return
        _updateNotificationsEnabled.value = enabled
        prefs.edit().putBoolean(PREF_UPDATE_NOTIFICATIONS, enabled).apply()
        viewModelScope.launch { settingsStore.setUpdateNotificationsEnabled(enabled) }
        if (enabled) checkForUpdates()
    }

    fun checkForUpdates(manual: Boolean = false) {
        if (!BuildConfig.GITHUB_UPDATES_ENABLED) {
            if (manual) {
                _updateStatus.update {
                    it.copy(
                        isChecking = false,
                        updateAvailable = false,
                        error = "Updates are handled by F-Droid for this build."
                    )
                }
            }
            return
        }
        if (_updateStatus.value.isChecking) return
        val now = System.currentTimeMillis()
        if (!manual) {
            val lastCheck = prefs.getLong(PREF_UPDATE_LAST_CHECK, 0L)
            if (lastCheck != 0L && now - lastCheck < UPDATE_CHECK_INTERVAL_MS) return
        }
        _updateStatus.update { it.copy(isChecking = true, error = null) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { ApiClient.github.getLatestRelease() }
            }
            val currentStatus = _updateStatus.value
            val nextStatus = result.fold(
                onSuccess = { release ->
                    val latestTag = release.tagName?.trim().orEmpty()
                    if (latestTag.isBlank()) {
                        currentStatus.copy(
                            isChecking = false,
                            error = "No release tag found.",
                            lastCheckedAt = now
                        )
                    } else {
                        val downloadUrl = release.assets
                            ?.firstOrNull { it.browserDownloadUrl?.endsWith(".apk", ignoreCase = true) == true }
                            ?.browserDownloadUrl
                        val releaseUrl = release.htmlUrl
                        val updateAvailable = isUpdateAvailable(BuildConfig.VERSION_NAME, latestTag)
                        val status = UpdateStatus(
                            isChecking = false,
                            latestVersion = latestTag,
                            updateAvailable = updateAvailable,
                            downloadUrl = downloadUrl,
                            releaseUrl = releaseUrl,
                            changelog = release.body,
                            lastCheckedAt = now,
                            error = null
                        )
                        if (updateAvailable) maybeNotifyUpdate(latestTag, downloadUrl, releaseUrl)
                        status
                    }
                },
                onFailure = { e ->
                    currentStatus.copy(
                        isChecking = false,
                        error = e.message ?: "Update check failed.",
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
            _updateStatus.update {
                it.copy(error = "Updates are handled by F-Droid for this build.")
            }
            return
        }
        val status = _updateStatus.value
        if (status.isDownloadingUpdate) return

        status.downloadedUpdateApkPath
            ?.let(::File)
            ?.takeIf { it.exists() && it.length() > 0L }
            ?.let { file ->
                promptInstallUpdate(file)
                return
            }

        val downloadUrl = status.downloadUrl?.takeIf { it.isNotBlank() }
        if (downloadUrl == null) {
            _updateStatus.update { it.copy(error = "No APK download link found.") }
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

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    downloadUpdateApkFile(
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
                        error = e.message ?: "Update download failed."
                    )
                }
                DebugLog.e("ChemSearch", "Update download failed: ${e.message}")
            }
        }
    }

    private fun downloadUpdateApkFile(
        url: String,
        version: String,
        onProgress: (Float) -> Unit
    ): File {
        val context = getApplication<Application>()
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
                throw IOException("Download failed: HTTP ${response.code}")
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
            throw IOException("Downloaded APK was empty.")
        }
        if (target.exists()) target.delete()
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
        onProgress(1f)
        return target
    }

    private fun promptInstallUpdate(apkFile: File) {
        val context = getApplication<Application>()
        if (!apkFile.exists() || apkFile.length() == 0L) {
            _updateStatus.update {
                it.copy(
                    downloadedUpdateApkPath = null,
                    updateDownloadProgress = null,
                    error = "Downloaded APK is missing."
                )
            }
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            val settingsIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(settingsIntent) }
            _updateStatus.update {
                it.copy(error = "Allow installs from ChemSearch, then tap Install.")
            }
            return
        }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        val installIntent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        runCatching { context.startActivity(installIntent) }
            .onFailure { e ->
                _updateStatus.update {
                    it.copy(error = e.message ?: "Could not open installer.")
                }
            }
    }

    fun toggleFavorite() {
        val state = _uiState.value
        val cid = state.cid ?: return
        val current = _favorites.value.toMutableList()
        val wasFavorite = current.any { it.cid == cid }
        val nextFavorite = !wasFavorite
        if (wasFavorite) {
            current.removeAll { it.cid == cid }
            DebugLog.d("ChemSearch", "Removed favorite: ${state.name} (CID $cid)")
        } else {
            current.add(0, FavoriteCompound(
                cid = cid,
                name = state.name,
                formula = state.formula,
                molecularWeight = state.weight,
                iupacName = state.iupacName
            ))
            DebugLog.d("ChemSearch", "Added favorite: ${state.name} (CID $cid)")
        }
        _isFavorite.value = nextFavorite
        _favorites.value = current
        saveFavorites(current)
    }

    fun deleteFavorite(cid: Long) {
        val updated = _favorites.value.filter { it.cid != cid }
        _favorites.value = updated
        saveFavorites(updated)
        if (_uiState.value.cid == cid) _isFavorite.value = false
    }

    fun restoreFavorite(favorite: FavoriteCompound) {
        val normalized = favorite.normalizedOrNull() ?: return
        val updated = listOf(normalized) + _favorites.value.filterNot { it.cid == normalized.cid }
        _favorites.value = updated
        saveFavorites(updated)
        if (_uiState.value.cid == normalized.cid) _isFavorite.value = true
        DebugLog.d("ChemSearch", "Restored favorite: ${normalized.name} (CID ${normalized.cid})")
    }

    fun moveFavorite(fromIndex: Int, toIndex: Int) {
        val current = _favorites.value.toMutableList()
        if (fromIndex !in current.indices || toIndex !in current.indices) return
        val item = current.removeAt(fromIndex)
        current.add(toIndex, item)
        _favorites.value = current
        saveFavorites(current)
    }

    private fun loadFavorites(): List<FavoriteCompound> {
        val json = prefs.getString("favorites", null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<FavoriteCompound>>() {}.type
            val favorites = gson.fromJson<List<FavoriteCompound>>(json, type) ?: emptyList()
            favorites.mapNotNull { it.normalizedOrNull() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveFavorites(list: List<FavoriteCompound>) {
        prefs.edit().putString("favorites", gson.toJson(list)).apply()
    }

    private fun FavoriteCompound.normalizedOrNull(): FavoriteCompound? {
        val safeCid = runCatching { cid }.getOrNull()?.takeIf { it > 0L } ?: return null
        val safeFormula = runCatching { formula }.getOrNull()?.trim().orEmpty()
        val safeName = runCatching { name }.getOrNull()?.trim().orEmpty()
        val safeWeight = runCatching { molecularWeight }.getOrNull()?.trim().orEmpty()
        val safeIupacName = runCatching { iupacName }.getOrNull()?.trim().orEmpty()
        val safeSavedAt = runCatching { savedAt }.getOrNull()?.takeIf { it > 0L }
            ?: System.currentTimeMillis()
        val conventional = formatConventionalFormula(safeFormula)
        return FavoriteCompound(
            cid = safeCid,
            name = safeName.ifBlank { conventional.ifBlank { "CID $safeCid" } },
            formula = conventional,
            molecularWeight = safeWeight,
            iupacName = safeIupacName,
            savedAt = safeSavedAt
        )
    }

    fun saveCurrentCompoundOffline() {
        val startState = _uiState.value
        val cid = startState.cid ?: return
        if (!startState.hasResult || _isSavingOffline.value) return

        _isSavingOffline.value = true
        _offlineDownloadProgress.value = 0f
        viewModelScope.launch {
            try {
                val snapshot = buildOfflineSnapshot(startState, _offlineDownloadQuality.value) { progress ->
                    _offlineDownloadProgress.value = progress
                }
                val item = DownloadedCompound(
                    cid = cid,
                    name = snapshot.name,
                    formula = snapshot.formula,
                    molecularWeight = snapshot.weight,
                    iupacName = snapshot.iupacName,
                    state = snapshot,
                    structurePngBase64 = snapshot.offline2dPngBase64,
                    offlineMetadata = buildOfflineDownloadMetadata(snapshot)
                )
                val updated = listOf(item) + _downloads.value.filterNot { it.cid == cid }
                _downloads.value = updated
                withContext(Dispatchers.IO) { offlineDownloadRepository.upsert(item) }
                _offlineDownloadProgress.value = 1f
                _isDownloaded.value = true
                _uiState.update { current ->
                    if (current.cid == cid) snapshot.copy(history = current.history) else current
                }
                DebugLog.d("ChemSearch", "Downloaded offline compound: ${snapshot.name} (CID $cid)")
            } catch (e: Exception) {
                DebugLog.e("ChemSearch", "Offline download failed for CID $cid: ${e.message}")
                _uiState.update { it.copy(error = "Offline download failed: ${e.message ?: "unknown error"}") }
            } finally {
                delay(250)
                _isSavingOffline.value = false
                _offlineDownloadProgress.value = null
            }
        }
    }

    fun openDownloadedCompound(cid: Long) {
        val downloaded = _downloads.value.firstOrNull { it.cid == cid } ?: return
        _query.value = downloaded.name
        saveToHistory(downloaded.name)
        _uiState.value = downloaded.state.copy(
            isLoading = false,
            error = null,
            hasResult = true,
            suggestions = emptyList(),
            history = recentQueries(),
            isCached = false,
            isOfflineDownload = true,
            isLoadingDesc = false,
            isLoadingSdf = false,
            isLoadingSafety = false,
            isLoadingSynonyms = false,
            isLoadingPubChemContext = false
        )
        DebugLog.d("ChemSearch", "Opened downloaded compound: ${downloaded.name} (CID $cid)")
    }

    fun deleteDownload(cid: Long) {
        val updated = _downloads.value.filterNot { it.cid == cid }
        _downloads.value = updated
        viewModelScope.launch(Dispatchers.IO) { offlineDownloadRepository.delete(cid) }
        if (_uiState.value.cid == cid) _isDownloaded.value = false
        DebugLog.d("ChemSearch", "Deleted offline download for CID $cid")
    }

    fun restoreDownload(download: DownloadedCompound) {
        val normalized = download.normalizedOrNull() ?: return
        val updated = listOf(normalized) + _downloads.value.filterNot { it.cid == normalized.cid }
        _downloads.value = updated
        viewModelScope.launch(Dispatchers.IO) { offlineDownloadRepository.upsert(normalized) }
        if (_uiState.value.cid == normalized.cid) _isDownloaded.value = true
        DebugLog.d("ChemSearch", "Restored offline download: ${normalized.name} (CID ${normalized.cid})")
    }

    fun buildLibraryBackupJson(): String =
        gson.toJson(
            LibraryBackup(
                appVersionName = BuildConfig.VERSION_NAME,
                appVersionCode = BuildConfig.VERSION_CODE,
                favorites = _favorites.value,
                downloads = _downloads.value
            )
        )

    fun importLibraryBackup(
        rawJson: String,
        replace: Boolean,
        onResult: (Result<LibraryImportResult>) -> Unit
    ) {
        viewModelScope.launch {
            val result = runCatching {
                val backup = gson.fromJson(rawJson, LibraryBackup::class.java)
                    ?: throw IllegalArgumentException("Invalid library backup file.")
                if (backup.format != LIBRARY_BACKUP_FORMAT) {
                    throw IllegalArgumentException("This is not a ChemSearch library backup.")
                }

                val (mergedFavorites, importedFavorites) = mergeFavoritesForImport(
                    current = _favorites.value,
                    imported = backup.favorites,
                    replace = replace
                )
                val (mergedDownloads, importedDownloads) = mergeDownloadsForImport(
                    current = _downloads.value,
                    imported = backup.downloads,
                    replace = replace
                )

                _favorites.value = mergedFavorites
                saveFavorites(mergedFavorites)
                _downloads.value = mergedDownloads
                withContext(Dispatchers.IO) {
                    if (replace) {
                        offlineDownloadRepository.replaceAll(mergedDownloads)
                    } else {
                        offlineDownloadRepository.upsertAll(
                            mergedDownloads.filter { imported -> backup.downloads.any { it.cid == imported.cid } }
                        )
                    }
                }

                LibraryImportResult(
                    favoriteCount = importedFavorites,
                    downloadCount = importedDownloads,
                    skippedFavorites = backup.favorites.size - importedFavorites,
                    skippedDownloads = backup.downloads.size - importedDownloads
                )
            }
            onResult(result)
        }
    }

    private fun loadDownloads(): List<DownloadedCompound> {
        val json = prefs.getString("downloads", null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<DownloadedCompound>>() {}.type
            val restored = gson.fromJson<List<DownloadedCompound>>(json, type) ?: emptyList()
            restored.mapNotNull { it.normalizedOrNull() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun DownloadedCompound.normalizedOrNull(): DownloadedCompound? {
        val safeCid = runCatching { cid }.getOrNull()?.takeIf { it > 0L } ?: return null
        val rawState = runCatching { state }.getOrNull()
        val safeName = runCatching { name }.getOrNull()?.trim().orEmpty()
        val safeFormula = runCatching { formula }.getOrNull()?.trim().orEmpty()
        val safeWeight = runCatching { molecularWeight }.getOrNull()?.trim().orEmpty()
        val safeIupacName = runCatching { iupacName }.getOrNull()?.trim().orEmpty()
        val safeSavedAt = runCatching { savedAt }.getOrNull()?.takeIf { it > 0L }
            ?: System.currentTimeMillis()
        val safeStructurePngBase64 = runCatching { structurePngBase64 }.getOrNull()
        val fallbackState = ChemUiState(
            cid = safeCid,
            name = safeName,
            formula = safeFormula,
            weight = safeWeight,
            iupacName = safeIupacName,
            hasResult = true,
            isOfflineDownload = true
        )
        val normalizedState = runCatching {
            (rawState ?: fallbackState).copy(
                cid = rawState?.cid ?: safeCid,
                name = runCatching { rawState?.name }.getOrNull()?.trim().orEmpty()
                    .ifBlank { safeName },
                formula = runCatching { rawState?.formula }.getOrNull()?.trim().orEmpty()
                    .ifBlank { safeFormula },
                weight = runCatching { rawState?.weight }.getOrNull()?.trim().orEmpty()
                    .ifBlank { safeWeight },
                iupacName = runCatching { rawState?.iupacName }.getOrNull()?.trim().orEmpty()
                    .ifBlank { safeIupacName },
                hasResult = true,
                isOfflineDownload = true,
                isLoading = false,
                error = null,
                isLoadingDesc = false,
                isLoadingSdf = false,
                isLoadingSafety = false,
                isLoadingSynonyms = false
            ).withConventionalFormula()
        }.getOrElse { fallbackState.withConventionalFormula() }
        val normalizedFormula = formatConventionalFormula(
            safeFormula.ifBlank { normalizedState.formula }
        )
        val restoredMetadata = runCatching { offlineMetadata }.getOrNull()
        return DownloadedCompound(
            cid = safeCid,
            name = safeName.ifBlank { normalizedState.name.ifBlank { "CID $safeCid" } },
            formula = normalizedFormula,
            molecularWeight = safeWeight.ifBlank { normalizedState.weight },
            iupacName = safeIupacName.ifBlank { normalizedState.iupacName },
            savedAt = safeSavedAt,
            state = normalizedState.copy(formula = normalizedFormula),
            structurePngBase64 = safeStructurePngBase64,
            offlineMetadata = restoredMetadata ?: buildOfflineDownloadMetadata(normalizedState, safeSavedAt)
        )
    }

    private fun ChemUiState.withConventionalFormula(): ChemUiState {
        val originalFormula = runCatching { rawFormula }.getOrDefault("").orEmpty()
        val conventional = formatConventionalFormula(formula)
        val safeRawFormula = originalFormula.ifBlank { formula }
        if (conventional == formula && safeRawFormula == originalFormula) return this
        return copy(
            formula = conventional,
            rawFormula = safeRawFormula,
            empiricalFormula = getEmpiricalFormula(conventional),
            elementalData = calcElementalData(conventional)
        )
    }

    fun toggleTheme() {
        val next = !_isDarkTheme.value
        _isDarkTheme.value = next
        prefs.edit().putBoolean("dark_theme", next).apply()
        viewModelScope.launch { settingsStore.setDarkTheme(next) }
        DebugLog.d("ChemSearch", "Theme → ${if (next) "dark" else "light"}")
    }

    fun setColorScheme(scheme: AppColorScheme) {
        _colorScheme.value = scheme
        prefs.edit().putString("color_scheme", scheme.name).apply()
        viewModelScope.launch { settingsStore.setColorScheme(scheme) }
        DebugLog.d("ChemSearch", "Color scheme → ${scheme.name}")
    }

    fun toggleAutoSuggest() {
        val next = !_autoSuggest.value
        _autoSuggest.value = next
        prefs.edit().putBoolean("auto_suggest", next).apply()
        viewModelScope.launch { settingsStore.setAutoSuggest(next) }
        if (!next) _uiState.update { it.copy(suggestions = emptyList()) }
        DebugLog.d("ChemSearch", "Autosuggestions → ${if (next) "on" else "off"}")
    }

    fun setCompactMode(enabled: Boolean) {
        _compactMode.value = enabled
        prefs.edit().putBoolean("compact_mode", enabled).apply()
        viewModelScope.launch { settingsStore.setCompactMode(enabled) }
        DebugLog.d("ChemSearch", "Compact mode → ${if (enabled) "on" else "off"}")
    }

    fun setOledDarkTheme(enabled: Boolean) {
        _oledDarkTheme.value = enabled
        prefs.edit().putBoolean("oled_dark_theme", enabled).apply()
        viewModelScope.launch { settingsStore.setOledDarkTheme(enabled) }
        DebugLog.d("ChemSearch", "AMOLED mode → ${if (enabled) "on" else "off"}")
    }

    fun setDefaultDescSource(source: DescSource) {
        _defaultDescSource.value = source
        saveDescSource(source)
    }

    fun setDefaultStructureView(view: DefaultStructureView) {
        _defaultStructureView.value = view
        prefs.edit().putString("default_structure_view", view.name).apply()
        viewModelScope.launch { settingsStore.setDefaultStructureView(view) }
        DebugLog.d("ChemSearch", "Default structure view → ${view.name}")
    }

    fun setOfflineDownloadQuality(quality: OfflineDownloadQuality) {
        _offlineDownloadQuality.value = quality
        prefs.edit().putString("offline_download_quality", quality.name).apply()
        viewModelScope.launch { settingsStore.setOfflineDownloadQuality(quality) }
        DebugLog.d("ChemSearch", "Offline download quality → ${quality.name}")
    }

    fun setFormulaDisplayStyle(style: FormulaDisplayStyle) {
        _formulaDisplayStyle.value = style
        prefs.edit().putString("formula_display_style", style.name).apply()
        viewModelScope.launch { settingsStore.setFormulaDisplayStyle(style) }
        DebugLog.d("ChemSearch", "Formula display style → ${style.name}")
    }

    fun setCacheSizeLimit(limit: CacheSizeLimit) {
        _cacheSizeLimit.value = limit
        prefs.edit().putString("cache_size_limit", limit.name).apply()
        viewModelScope.launch { settingsStore.setCacheSizeLimit(limit) }
        refreshCacheSizeAsync()
        DebugLog.d("ChemSearch", "Cache size limit → ${limit.name}")
    }

    fun setCacheRetention(retention: CacheRetention) {
        _cacheRetention.value = retention
        prefs.edit().putString("cache_retention", retention.name).apply()
        viewModelScope.launch { settingsStore.setCacheRetention(retention) }
        refreshCacheSizeAsync()
        DebugLog.d("ChemSearch", "Cache retention → ${retention.name}")
    }

    fun setReduceMotion(enabled: Boolean) {
        _reduceMotion.value = enabled
        prefs.edit().putBoolean("reduce_motion", enabled).apply()
        viewModelScope.launch { settingsStore.setReduceMotion(enabled) }
        DebugLog.d("ChemSearch", "Reduce motion → ${if (enabled) "on" else "off"}")
    }

    fun setHighContrastOutlines(enabled: Boolean) {
        _highContrastOutlines.value = enabled
        prefs.edit().putBoolean("high_contrast_outlines", enabled).apply()
        viewModelScope.launch { settingsStore.setHighContrastOutlines(enabled) }
        DebugLog.d("ChemSearch", "High contrast outlines → ${if (enabled) "on" else "off"}")
    }

    fun setAiProvider(provider: AiProvider) {
        _uiState.update { it.copy(aiProvider = provider) }
        prefs.edit().putString("ai_provider", provider.name).apply()
        DebugLog.d("ChemSearch", "AI provider → ${provider.displayName}")
        if (_uiState.value.descSource == DescSource.AI) {
            fetchAiDescription()
        }
    }

    fun reloadSettingsFromPreferences() {
        _isDarkTheme.value = prefs.getBoolean("dark_theme", false)
        _colorScheme.value = getSavedColorScheme()
        _autoSuggest.value = prefs.getBoolean("auto_suggest", true)
        _compactMode.value = prefs.getBoolean("compact_mode", false)
        _oledDarkTheme.value = prefs.getBoolean("oled_dark_theme", false)
        _defaultDescSource.value = getSavedDescSource()
        _defaultStructureView.value = getSavedDefaultStructureView()
        _offlineDownloadQuality.value = getSavedOfflineDownloadQuality()
        _formulaDisplayStyle.value = getSavedFormulaDisplayStyle()
        _cacheSizeLimit.value = getSavedCacheSizeLimit()
        _cacheRetention.value = getSavedCacheRetention()
        _reduceMotion.value = prefs.getBoolean("reduce_motion", false)
        _highContrastOutlines.value = prefs.getBoolean("high_contrast_outlines", false)
        _cacheDirPath.value = prefs.getString("cache_dir", "") ?: ""
        refreshCacheSizeAsync()
        refreshAiKeyStatus()
        _updateNotificationsEnabled.value = prefs.getBoolean(PREF_UPDATE_NOTIFICATIONS, true)
        _updateStatus.update {
            it.copy(lastCheckedAt = prefs.getLong(PREF_UPDATE_LAST_CHECK, 0L).takeIf { ts -> ts != 0L })
        }
        _favorites.value = loadFavorites()
        _showWelcome.value = !prefs.getBoolean(PREF_WELCOME_SKIPPED, false)
        _recentSearches.value = loadRecentSearches()

        val provider = AiProvider.entries.firstOrNull { it.name == prefs.getString("ai_provider", null) }
            ?: AiProvider.GEMINI
        val source = getSavedDescSource()
        _uiState.update { current ->
            current.copy(
                history = recentQueries(),
                aiProvider = provider,
                descSource = source,
                suggestions = if (_autoSuggest.value) current.suggestions else emptyList()
            )
        }
        DebugLog.d("ChemSearch", "Settings reloaded from SharedPreferences")
    }

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private var searchJob: Job? = null
    private var autocompleteJob: Job? = null

    fun onQueryChange(q: String) {
        _query.value = q
        autocompleteJob?.cancel()
        _uiState.update {
            it.copy(
                failedSearchQuery = null,
                searchCorrectionSuggestions = emptyList()
            )
        }
        if (!_autoSuggest.value || q.length < 2) {
            _uiState.update {
                it.copy(
                    suggestions = emptyList(),
                )
            }
            return
        }
        autocompleteJob = viewModelScope.launch {
            delay(300)
            try {
                val res = ApiClient.pubChemAutocomplete.autocomplete(q)
                val suggestions = res.dictionaryTerms?.compound ?: emptyList()
                DebugLog.d("ChemSearch", "Autocomplete \"$q\" → ${suggestions.size} results")
                _uiState.update {
                    it.copy(
                        suggestions = suggestions,
                    )
                }
            } catch (e: Exception) {
                DebugLog.e("ChemSearch", "Autocomplete error for \"$q\": ${e.message}")
                _uiState.update { it.copy(suggestions = emptyList()) }
            }
        }
    }
    private val cacheDir: java.io.File get() {
        val custom = prefs.getString("cache_dir", null)
        val dir = if (!custom.isNullOrBlank()) java.io.File(custom) else java.io.File(getApplication<Application>().cacheDir, "compound_cache")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getCacheSizeBytes(): Long = computeCacheSizeBlocking()

    private fun computeCacheSizeBlocking(): Long =
        runCatching { cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() } }.getOrDefault(0L)

    private fun refreshCacheSizeAsync() {
        viewModelScope.launch {
            _cacheSizeBytes.value = withContext(Dispatchers.IO) {
                enforceCachePolicyBlocking()
                computeCacheSizeBlocking()
            }
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                cacheDir.walkTopDown().filter { it.isFile }.forEach { it.delete() }
            }
            _cacheSizeBytes.value = 0L
            DebugLog.d("ChemSearch", "Compound cache cleared")
        }
    }

    fun setCacheDir(path: String): Boolean {
        val cleanPath = path.trim()
        if (cleanPath.isBlank()) {
            prefs.edit().remove("cache_dir").apply()
            _cacheDirPath.value = ""
            viewModelScope.launch { settingsStore.setCacheDir("") }
            refreshCacheSizeAsync()
            DebugLog.d("ChemSearch", "Cache dir reset to default")
            return true
        }

        val target = java.io.File(cleanPath)
        val canUseDirectory = runCatching {
            if (!target.exists()) target.mkdirs()
            if (!target.isDirectory) return@runCatching false
            val probe = java.io.File(target, ".chemsearch_write_test")
            probe.writeText("ok")
            probe.delete()
            true
        }.getOrDefault(false)

        if (!canUseDirectory) {
            DebugLog.e("ChemSearch", "Rejected cache dir: $cleanPath")
            return false
        }

        prefs.edit().putString("cache_dir", cleanPath).apply()
        _cacheDirPath.value = cleanPath
        viewModelScope.launch { settingsStore.setCacheDir(cleanPath) }
        refreshCacheSizeAsync()
        DebugLog.d("ChemSearch", "Cache dir set to: $cleanPath")
        return true
    }

    fun getCacheDir(): String = prefs.getString("cache_dir", null) ?: ""

    private suspend fun readCache(cid: Long): ChemUiState? =
        withContext(Dispatchers.IO) {
            val file = java.io.File(cacheDir, "$cid.json")
            if (!file.exists()) return@withContext null
            try {
                val json = file.readText()
                gson.fromJson(json, ChemUiState::class.java)?.withConventionalFormula()
            } catch (e: Exception) {
                DebugLog.e("ChemSearch", "Cache read failed for CID $cid: ${e.message}")
                null
            }
        }

    private suspend fun findCacheByName(query: String): ChemUiState? = withContext(Dispatchers.IO) {
        val q = query.trim().lowercase()
        try {
            cacheDir.listFiles()
                ?.filter { it.isFile && it.extension == "json" }
                ?.asSequence()
                ?.mapNotNull { file ->
                    runCatching {
                        gson.fromJson(file.readText(), ChemUiState::class.java)?.withConventionalFormula()
                    }.getOrNull()
                }
                ?.firstOrNull { state ->
                    state.name.lowercase() == q ||
                        state.synonyms.any { it.lowercase() == q } ||
                        state.casNumber?.lowercase() == q ||
                        state.cid?.toString() == q
                }
        } catch (e: Exception) {
            DebugLog.e("ChemSearch", "Cache name search failed: ${e.message}")
            null
        }
    }

    private suspend fun writeCache(state: ChemUiState) {
        val cid = state.cid ?: return
        try {
            val (fileSizeKb, cacheBytes) = withContext(Dispatchers.IO) {
                val file = java.io.File(cacheDir, "$cid.json")
                file.writeText(gson.toJson(state))
                enforceCachePolicyBlocking()
                (file.length() / 1024L) to computeCacheSizeBlocking()
            }
            _cacheSizeBytes.value = cacheBytes
            DebugLog.d("ChemSearch", "Cached compound CID $cid (${fileSizeKb}KB)")
        } catch (e: Exception) {
            DebugLog.e("ChemSearch", "Cache write failed: ${e.message}")
        }
    }

    private fun enforceCachePolicyBlocking() {
        val dir = cacheDir
        val files = dir.listFiles()
            ?.filter { it.isFile && it.extension == "json" }
            .orEmpty()
        if (files.isEmpty()) return

        val retentionCutoff = _cacheRetention.value.maxAgeMillis?.let { System.currentTimeMillis() - it }
        val retainedFiles = if (retentionCutoff != null) {
            files.filter { file ->
                val expired = file.lastModified() in 1 until retentionCutoff
                if (expired) file.delete()
                !expired
            }
        } else {
            files
        }

        val maxBytes = _cacheSizeLimit.value.maxBytes ?: return
        var totalBytes = retainedFiles.sumOf { it.length() }
        if (totalBytes <= maxBytes) return
        retainedFiles
            .sortedBy { it.lastModified().takeIf { modified -> modified > 0L } ?: Long.MAX_VALUE }
            .forEach { file ->
                if (totalBytes <= maxBytes) return@forEach
                val length = file.length()
                if (file.delete()) totalBytes -= length
            }
    }

    private suspend fun fetchSynonyms(cid: Long): List<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                ApiClient.pubChem.getSynonyms(cid)
                    .informationList
                    ?.information
                    ?.firstOrNull()
                    ?.synonym
                    ?: emptyList()
            }.getOrDefault(emptyList())
        }.distinct()

    private fun loadSynonymsForCurrentCompound(cid: Long, force: Boolean = false) {
        val current = _uiState.value
        if (!force && current.cid == cid && current.synonyms.size >= 10) return
        viewModelScope.launch {
            _uiState.update { state ->
                if (state.cid == cid) state.copy(isLoadingSynonyms = true) else state
            }
            val synonyms = fetchSynonyms(cid)
            val currentState = _uiState.value.takeIf { it.cid == cid } ?: return@launch
            if (synonyms.isEmpty()) {
                _uiState.update { state ->
                    if (state.cid == cid) state.copy(isLoadingSynonyms = false) else state
                }
                return@launch
            }

            val casRegex = Regex("""^\d{1,7}-\d{2}-\d$""")
            val refreshed = currentState.copy(
                synonyms = synonyms,
                casNumber = synonyms.firstOrNull { casRegex.matches(it) } ?: currentState.casNumber,
                isLoadingSynonyms = false,
                isCached = currentState.isCached
            )

            _uiState.value = refreshed
            writeCache(refreshed)
            DebugLog.d("ChemSearch", "Synonyms loaded for CID $cid: ${synonyms.size} names")
        }
    }

    fun search(queryOverride: String? = null) {

        val q = (queryOverride ?: _query.value).trim()
        if (q.isBlank()) return
        if (queryOverride != null) _query.value = q

        q.toLongOrNull()?.takeIf { it > 0 }?.let { cid ->
            searchByCid(cid)
            return
        }

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            DebugLog.d("ChemSearch", "Search started: \"$q\"")
            _uiState.update {
                it.copy(
                    isLoading = true,
                    error = null,
                    suggestions = emptyList(),
                    failedSearchQuery = null,
                    searchCorrectionSuggestions = emptyList(),
                    hasResult = false,
                    isCached = false,
                    isOfflineDownload = false,
                    offline2dPngBase64 = null,
                    sdfData = null,
                    sdfSource = null,
                    sdfMessage = null,
                    ghsData = null,
                    advancedProperties = emptyList(),
                    classificationTags = emptyList(),
                    useEntries = emptyList(),
                    isLoadingPubChemContext = false,
                    aiDescriptionBasis = emptyList(),
                    isLoadingSafety = false
                )
            }

            val cachedByName = findCacheByName(q)
            if (cachedByName != null) {
                DebugLog.d("ChemSearch", "Cache hit by name for \"$q\" → CID ${cachedByName.cid}")
                val savedSource = getSavedDescSource()
                _uiState.update {
                    cachedByName.copy(
                        isLoading = false,
                        hasResult = true,
                        isCached = true,
                        history = recentQueries(),
                        descSource = savedSource,
                        sdfData = null,
                        sdfSource = null,
                        sdfMessage = null,
                        offline2dPngBase64 = null,
                        isOfflineDownload = false,
                        activeTab = defaultMolTab(),
                        isLoadingSynonyms = false,
                        isLoadingPubChemContext = false
                    )
                }
                if (_uiState.value.activeTab == MolTab.THREE_D) fetchSdfData()
                saveToHistory(cachedByName.name)
                cachedByName.cid?.let { loadSynonymsForCurrentCompound(it) }
                cachedByName.cid?.let { loadPubChemExtrasForCurrentCompound(it) }
                when (savedSource) {
                    DescSource.WIKI -> if (cachedByName.wikiDescription == null) fetchWikiDescription()
                    DescSource.AI   -> if (cachedByName.aiDescription == null) fetchAiDescription()
                    else -> Unit
                }
                if (cachedByName.ghsData == null) fetchSafetyData()
                return@launch
            }

            try {
                val cidResponse = ApiClient.pubChem.getCid(q)
                val cid = cidResponse.identifierList?.cid?.firstOrNull()
                    ?: throw NoSuchElementException("Chemical not found.")
                DebugLog.d("ChemSearch", "CID resolved: $cid for \"$q\"")

                val cached = readCache(cid)
                if (cached != null) {
                    DebugLog.d("ChemSearch", "Cache hit for CID $cid (${cached.name})")
                    val savedSource = getSavedDescSource()
                    _uiState.update {
                        cached.copy(
                            isLoading = false,
                            hasResult = true,
                            isCached = true,
                            history = recentQueries(),
                            descSource = savedSource,
                            ghsData = cached.ghsData,
                            sdfData = null,
                            sdfSource = null,
                            sdfMessage = null,
                            offline2dPngBase64 = null,
                            isOfflineDownload = false,
                            activeTab = defaultMolTab(),
                            isLoadingSynonyms = false,
                            isLoadingPubChemContext = false
                        )
                    }
                    if (_uiState.value.activeTab == MolTab.THREE_D) fetchSdfData()
                    backfillStructureMetadataIfMissing(cached, cid)
                    saveToHistory(cached.name)
                    loadSynonymsForCurrentCompound(cid)
                    loadPubChemExtrasForCurrentCompound(cid)
                    when (savedSource) {
                        DescSource.WIKI -> if (cached.wikiDescription == null) fetchWikiDescription()
                        DescSource.AI   -> if (cached.aiDescription == null) fetchAiDescription()
                        else -> Unit
                    }
                    if (cached.ghsData == null) fetchSafetyData()
                    return@launch
                }

                val propsDeferred = async { runCatching { ApiClient.pubChem.getProperties(cid) }.getOrNull() }
                val descDeferred  = async { runCatching { ApiClient.pubChem.getDescription(cid) }.getOrNull() }
                val recordDeferred = async { runCatching { ApiClient.pubChem.getRecord(cid) }.getOrNull() }

                val props = propsDeferred.await()?.propertyTable?.properties?.firstOrNull()
                    ?: CompoundProperty(cid = cid)
                val descItem = descDeferred.await()
                    ?.informationList?.information?.find { it.description != null }
                val structureCounts = extractStructureCounts(recordDeferred.await())

                DebugLog.d("ChemSearch", "Properties fetched: MW=${props.molecularWeight}, formula=${props.molecularFormula}")

                val compoundName = props.title?.takeIf { it.isNotBlank() }
                    ?: props.iupacName?.takeIf { it.isNotBlank() }
                    ?: q
                val rawFormula = props.molecularFormula ?: ""
                val formula = formatConventionalFormula(rawFormula)

                val pubDesc: String? = descItem?.description?.let { el ->
                    when {
                        el.isJsonPrimitive -> el.asString
                        el.isJsonArray -> el.asJsonArray.mapNotNull {
                            runCatching { it.asString }.getOrNull()
                        }.joinToString("\n\n")
                        else -> null
                    }
                }

                val savedSource = getSavedDescSource()
                saveToHistory(compoundName)
                DebugLog.d("ChemSearch", "Search complete: \"$compoundName\" (CID $cid), desc=${pubDesc != null}")

                val newState = ChemUiState(
                    isLoading = false,
                    hasResult = true,
                    cid = cid,
                    name = compoundName.replaceFirstChar { c -> c.uppercase() },
                    formula = formula,
                    rawFormula = rawFormula,
                    empiricalFormula = getEmpiricalFormula(formula),
                    weight = props.molecularWeight ?: "",
                    charge = props.charge ?: 0,
                    atomNumber = structureCounts.atomCount,
                    bondNumber = structureCounts.bondCount,
                    covalentUnitCount = props.covalentUnitCount,
                    iupacName = props.iupacName ?: "",
                    smiles = props.smiles ?: "",
                    connectivitySmiles = props.connectivitySmiles ?: props.smiles ?: "",
                    inchiKey = props.inchiKey ?: "",
                    inchi = props.inchi ?: "",
                    synonyms = emptyList(),
                    casNumber = null,
                    pubDescription = pubDesc,
                    wikiDescription = null,
                    aiDescription = null,
                    descSource = savedSource,
                    elementalData = calcElementalData(formula),
                    history = recentQueries(),
                    activeTab = defaultMolTab(),
                    aiProvider = _uiState.value.aiProvider,
                    isCached = false,
                    isLoadingSynonyms = true,
                    advancedProperties = buildAdvancedProperties(props)
                )
                _uiState.update { newState }
                if (newState.activeTab == MolTab.THREE_D) fetchSdfData()
                writeCache(newState)
                loadSynonymsForCurrentCompound(cid, force = true)
                loadPubChemExtrasForCurrentCompound(cid, force = true)

                when (savedSource) {
                    DescSource.WIKI -> fetchWikiDescription()
                    DescSource.AI   -> fetchAiDescription()
                    else -> Unit
                }

                fetchSafetyData()

            } catch (e: Exception) {
                val msg = when (e) {
                    is IOException -> "Network error. Check your connection."
                    is NoSuchElementException -> e.message ?: "Not found"
                    else -> "Chemical not found. Try a different name or spelling."
                }
                val corrections = if (e is IOException) emptyList() else fetchSearchCorrectionSuggestions(q)
                DebugLog.e("ChemSearch", "Search failed for \"$q\": ${e::class.simpleName} — ${e.message}")
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = msg,
                        failedSearchQuery = q,
                        searchCorrectionSuggestions = corrections
                    )
                }
            }
        }
    }

    private suspend fun fetchSearchCorrectionSuggestions(query: String): List<String> =
        runCatching {
            val suggestions = ApiClient.pubChemAutocomplete.autocomplete(query, limit = 8)
                .dictionaryTerms
                ?.compound
                .orEmpty()
            cleanSearchCorrectionSuggestions(query, suggestions)
        }.getOrDefault(emptyList())

    fun updateAdvancedSearchFilters(filters: AdvancedSearchFilters) {
        _advancedSearchState.update { it.copy(filters = filters, error = null) }
    }

    fun clearAdvancedSearchResults() {
        _advancedSearchState.update { it.copy(isLoading = false, results = emptyList(), error = null) }
    }

    fun searchAdvanced(filters: AdvancedSearchFilters = _advancedSearchState.value.filters) {
        val normalized = filters.copy(
            query = normalizeAdvancedSearchQuery(filters.query),
            maxRecords = filters.maxRecords.coerceIn(1, 50)
        )
        if (normalized.query.isBlank()) {
            _advancedSearchState.update {
                it.copy(filters = normalized, error = "Enter a name, formula, CID, or CAS number.")
            }
            return
        }

        viewModelScope.launch {
            _advancedSearchState.update {
                it.copy(isLoading = true, filters = normalized, results = emptyList(), error = null)
            }
            try {
                val cids = resolveAdvancedSearchCids(normalized)
                    .distinct()
                    .take(normalized.maxRecords)
                if (cids.isEmpty()) throw NoSuchElementException("No candidates found.")

                val cidString = cids.joinToString(",")
                val properties = ApiClient.pubChem.getAdvancedSearchProperties(cidString)
                    .propertyTable?.properties
                    .orEmpty()
                    .filter { it.cid != null }

                val decorated = properties.map { property ->
                    val cid = property.cid ?: return@map null
                    val hasThreeD = if (normalized.requireThreeD) hasPubChem3d(cid) else null
                    val hasGhs = if (normalized.requireGhs) fetchGhsDataBlocking(cid) != null else null
                    if (!advancedSearchMatchesFilters(property, normalized, hasThreeD, hasGhs)) return@map null
                    AdvancedSearchResultItem(
                        cid = cid,
                        title = property.title ?: property.iupacName ?: "CID $cid",
                        formula = property.molecularFormula.orEmpty(),
                        molecularWeight = property.molecularWeight.orEmpty(),
                        charge = property.charge,
                        hasThreeD = hasThreeD,
                        hasGhs = hasGhs
                    )
                }.filterNotNull()

                if (decorated.isEmpty()) throw NoSuchElementException("No compounds matched those filters.")
                _advancedSearchState.update {
                    it.copy(isLoading = false, results = decorated, error = null)
                }
            } catch (e: Exception) {
                val msg = when (e) {
                    is IOException -> "Network error. Check your connection."
                    is NoSuchElementException -> e.message ?: "No advanced search results."
                    else -> "Advanced search failed. Try fewer filters."
                }
                DebugLog.e("ChemSearch", "Advanced search failed: ${e.message}")
                _advancedSearchState.update { it.copy(isLoading = false, error = msg) }
            }
        }
    }

    private suspend fun resolveAdvancedSearchCids(filters: AdvancedSearchFilters): List<Long> {
        val query = filters.query
        return when (filters.type) {
            AdvancedSearchType.CID -> query.toLongOrNull()?.takeIf { it > 0 }?.let(::listOf).orEmpty()
            AdvancedSearchType.FORMULA -> fetchFormulaCids(query, filters.maxRecords)
            AdvancedSearchType.CAS -> ApiClient.pubChem.getCid(query)
                .identifierList?.cid?.take(1).orEmpty()
            AdvancedSearchType.NAME -> {
                val names = buildList {
                    add(query)
                    val suggestions = runCatching {
                        ApiClient.pubChemAutocomplete.autocomplete(query, limit = 5)
                            .dictionaryTerms?.compound.orEmpty()
                    }.getOrDefault(emptyList())
                    addAll(suggestions)
                }.distinctBy { it.lowercase(Locale.US) }

                names.mapNotNull { name ->
                    runCatching {
                        ApiClient.pubChem.getCid(name).identifierList?.cid?.firstOrNull()
                    }.getOrNull()
                }
            }
        }
    }

    private suspend fun hasPubChem3d(cid: Long): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val sdf = ApiClient.pubChem.getSdf(cid, recordType = "3d").string()
            sdf.contains("V2000") && sdf.contains("M  END")
        }.getOrDefault(false)
    }

    fun fetchWikiDescription() {
        val name = _uiState.value.name.ifBlank { return }
        _uiState.update { it.copy(isLoadingDesc = true) }
        DebugLog.d("ChemSearch", "Fetching Wikipedia description for \"$name\"")
        viewModelScope.launch {
            val titleCased = name.trim().split(" ")
                .joinToString(" ") { word -> word.lowercase().replaceFirstChar { it.uppercase() } }
            val desc = runCatching { ApiClient.wiki.getSummary(titleCased).extract }.getOrNull()
                ?: runCatching { ApiClient.wiki.getSummary(name.trim().lowercase().replaceFirstChar { it.uppercase() }).extract }.getOrNull()
            DebugLog.d("ChemSearch", "Wikipedia result: ${if (desc != null) "${desc.take(60)}…" else "not found"}")
            _uiState.update { it.copy(isLoadingDesc = false, wikiDescription = desc) }
        }
    }

    fun fetchAiDescription() {
        val name = _uiState.value.name.ifBlank { return }
        val provider = _uiState.value.aiProvider

        when (provider) {
            AiProvider.GEMINI -> fetchGeminiDescription(name)
            else -> fetchChatDescription(name, provider)
        }
    }

    private fun fetchGeminiDescription(name: String) {
        val provider = AiProvider.GEMINI
        val key = getAiKey(provider) ?: run {
            _uiState.update { it.copy(isLoadingDesc = false, aiDescription = "No ${provider.shortName} API key set. Add it in Settings.") }
            return
        }
        val prompt = buildAiDescriptionPrompt(_uiState.value, provider, getSelectedAiModel(provider))
        loadCachedAiDescription(prompt)?.let { cached ->
            _uiState.update { it.copy(isLoadingDesc = false, aiDescription = cached, aiDescriptionBasis = prompt.basis) }
            return
        }
        _uiState.update { it.copy(isLoadingDesc = true, aiDescriptionBasis = prompt.basis) }
        DebugLog.d("ChemSearch", "Fetching ${provider.shortName} description for \"$name\"")
        viewModelScope.launch {
            val req = GeminiRequest(contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt.text)))))
            try {
                val response = ApiClient.gemini.generateContent(getSelectedAiModel(provider), key, req)
                val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                DebugLog.d("ChemSearch", "${provider.shortName} response: ${text?.take(80) ?: "empty"}")
                text?.takeIf { it.isNotBlank() }?.let { saveCachedAiDescription(prompt, it) }
                _uiState.update { it.copy(isLoadingDesc = false, aiDescription = text ?: "${provider.shortName} returned empty response.", aiDescriptionBasis = prompt.basis) }
            } catch (e: Exception) {
                DebugLog.e("ChemSearch", "${provider.shortName} error: ${e.message}")
                _uiState.update { it.copy(isLoadingDesc = false, aiDescription = "${provider.shortName} error: ${e.message}") }
            }
        }
    }

    private fun fetchChatDescription(name: String, provider: AiProvider) {
        val key = getAiKey(provider) ?: run {
            _uiState.update { it.copy(isLoadingDesc = false, aiDescription = "No ${provider.shortName} API key set. Add it in Settings.") }
            return
        }
        val prompt = buildAiDescriptionPrompt(_uiState.value, provider, getSelectedAiModel(provider))
        loadCachedAiDescription(prompt)?.let { cached ->
            _uiState.update { it.copy(isLoadingDesc = false, aiDescription = cached, aiDescriptionBasis = prompt.basis) }
            return
        }
        _uiState.update { it.copy(isLoadingDesc = true, aiDescriptionBasis = prompt.basis) }
        DebugLog.d("ChemSearch", "Fetching ${provider.shortName} description for \"$name\"")
        viewModelScope.launch {
            val req = GroqRequest(
                model = getSelectedAiModel(provider),
                messages = listOf(GroqMessage(role = "user", content = prompt.text))
            )
            try {
                val api = when (provider) {
                    AiProvider.GROQ -> ApiClient.groq
                    AiProvider.OPENAI -> ApiClient.openAi
                    AiProvider.OPENROUTER -> ApiClient.openRouter
                    AiProvider.MISTRAL -> ApiClient.mistral
                    AiProvider.GEMINI -> error("Gemini uses a separate API")
                }
                val response = api.generateContent("Bearer $key", req)
                val text = response.choices?.firstOrNull()?.message?.content
                DebugLog.d("ChemSearch", "${provider.shortName} response: ${text?.take(80) ?: "empty"}")
                text?.takeIf { it.isNotBlank() }?.let { saveCachedAiDescription(prompt, it) }
                _uiState.update { it.copy(isLoadingDesc = false, aiDescription = text ?: "${provider.shortName} returned empty response.", aiDescriptionBasis = prompt.basis) }
            } catch (e: Exception) {
                DebugLog.e("ChemSearch", "${provider.shortName} error: ${e.message}")
                _uiState.update { it.copy(isLoadingDesc = false, aiDescription = "${provider.shortName} error: ${e.message}") }
            }
        }
    }

    private fun loadCachedAiDescription(prompt: AiDescriptionPrompt): String? =
        prefs.getString("ai_description_${prompt.cacheKey}", null)

    private fun saveCachedAiDescription(prompt: AiDescriptionPrompt, text: String) {
        prefs.edit()
            .putString("ai_description_${prompt.cacheKey}", text)
            .putString("ai_description_basis_${prompt.cacheKey}", gson.toJson(prompt.basis))
            .apply()
    }

    fun setDescSource(source: DescSource) {
        _uiState.update { it.copy(descSource = source) }
        val state = _uiState.value
        if (source == DescSource.WIKI && state.wikiDescription == null) fetchWikiDescription()
        if (source == DescSource.AI   && state.aiDescription == null)   fetchAiDescription()
    }

    fun setTab(tab: MolTab) {
        prefs.edit().putString("last_structure_view", tab.name).apply()
        _uiState.update { it.copy(activeTab = tab) }
        if (tab == MolTab.THREE_D && _uiState.value.sdfData == null) {
            fetchSdfData()
        }
    }

    private fun defaultMolTab(): MolTab =
        when (_defaultStructureView.value) {
            DefaultStructureView.TWO_D -> MolTab.TWO_D
            DefaultStructureView.THREE_D -> MolTab.THREE_D
            DefaultStructureView.LAST_USED -> MolTab.entries.firstOrNull {
                it.name == prefs.getString("last_structure_view", null)
            } ?: MolTab.TWO_D
        }

    private fun fetchSdfData() {
        val cid = _uiState.value.cid ?: return
        _uiState.update { it.copy(isLoadingSdf = true, sdfData = null, sdfSource = null, sdfMessage = null) }
        DebugLog.d("ChemSearch", "Fetching SDF for CID $cid")
        viewModelScope.launch {
            val pubChemSdf = runCatching {
                withContext(Dispatchers.IO) { ApiClient.pubChem.getSdf(cid).string() }
            }

            pubChemSdf.getOrNull()?.takeIf(::isUsableSdf)?.let { sdf ->
                DebugLog.d("ChemSearch", "PubChem SDF loaded: ${sdf.lines().size} lines, ${sdf.length} bytes")
                _uiState.update { state ->
                    if (state.cid == cid) {
                        state.copy(
                            isLoadingSdf = false,
                            sdfData = sdf,
                            sdfSource = SdfSource.PUBCHEM,
                            sdfMessage = null
                        )
                    } else state
                }
                return@launch
            }

            pubChemSdf.exceptionOrNull()?.let { e ->
                DebugLog.e("ChemSearch", "PubChem SDF fetch failed for CID $cid: ${e.message}")
                Log.e("ChemViewModel", "Error fetching PubChem SDF", e)
            } ?: DebugLog.e("ChemSearch", "PubChem returned unusable SDF for CID $cid")

            val current = _uiState.value.takeIf { it.cid == cid } ?: return@launch
            val candidates = buildSdfIdentifierCandidates(
                smiles = current.smiles,
                connectivitySmiles = current.connectivitySmiles,
                inchi = current.inchi,
                inchiKey = current.inchiKey
            )

            if (candidates.isNotEmpty()) {
                _uiState.update { state ->
                    if (state.cid == cid) {
                        state.copy(sdfMessage = "PubChem 3D unavailable. Trying generated fallback...")
                    } else state
                }
            }

            val fallback = runCatching {
                withContext(Dispatchers.IO) {
                    fetchGeneratedSdfFromIdentifiers(candidates, expectedFormula = current.formula)
                }
            }.getOrNull()

            _uiState.update { state ->
                if (state.cid != cid) return@update state
                if (fallback != null) {
                    DebugLog.d("ChemSearch", "Generated SDF loaded for CID $cid")
                    state.copy(
                        isLoadingSdf = false,
                        sdfData = fallback.sdf,
                        sdfSource = fallback.source,
                        sdfMessage = fallback.message
                    )
                } else {
                    val message = if (candidates.isEmpty()) {
                        "PubChem 3D unavailable and no SMILES/InChI fallback identifier was found."
                    } else {
                        "PubChem 3D and formula-matched generated fallback are unavailable for this compound."
                    }
                    DebugLog.e("ChemSearch", message)
                    state.copy(
                        isLoadingSdf = false,
                        sdfData = null,
                        sdfSource = null,
                        sdfMessage = message
                    )
                }
            }
        }
    }

    private data class OfflineSdfResult(
        val sdf: String,
        val source: SdfSource,
        val message: String?
    )

    private suspend fun buildOfflineSnapshot(
        startState: ChemUiState,
        quality: OfflineDownloadQuality,
        onProgress: (Float) -> Unit = {}
    ): ChemUiState {
        val cid = startState.cid ?: return startState
        val latest = _uiState.value.takeIf { it.cid == cid } ?: startState
        onProgress(0.08f)
        val synonyms = when (quality) {
            OfflineDownloadQuality.COMPLETE -> latest.synonyms.takeIf { it.size >= 10 } ?: fetchSynonyms(cid)
            else -> latest.synonyms
        }
        onProgress(0.22f)
        val casRegex = Regex("""^\d{1,7}-\d{2}-\d$""")
        val pubDescription = if (quality == OfflineDownloadQuality.COMPLETE) {
            latest.pubDescription ?: fetchPubChemDescription(cid)
        } else {
            latest.pubDescription
        }
        onProgress(0.36f)
        val wikiDescription = if (quality == OfflineDownloadQuality.COMPLETE) {
            latest.wikiDescription ?: fetchWikiDescriptionBlocking(latest.name)
        } else {
            latest.wikiDescription
        }
        onProgress(0.50f)
        val ghsData = if (quality == OfflineDownloadQuality.COMPLETE) {
            latest.ghsData ?: fetchGhsDataBlocking(cid)
        } else {
            latest.ghsData
        }
        onProgress(0.60f)
        val advancedProperties = if (quality == OfflineDownloadQuality.COMPLETE && latest.advancedProperties.isEmpty()) {
            fetchAdvancedProperties(cid)
        } else {
            latest.advancedProperties
        }
        val pubChemContext = if (quality == OfflineDownloadQuality.COMPLETE && latest.classificationTags.isEmpty() && latest.useEntries.isEmpty()) {
            fetchPubChemCompoundContext(cid)
        } else {
            PubChemCompoundContext(latest.classificationTags, latest.useEntries)
        }
        onProgress(0.68f)
        val sdfResult = if (quality != OfflineDownloadQuality.BASIC) fetchSdfForOffline(latest) else null
        onProgress(0.82f)
        val pngBase64 = if (quality != OfflineDownloadQuality.BASIC) {
            latest.offline2dPngBase64 ?: fetch2dStructurePngBase64(cid)
        } else {
            latest.offline2dPngBase64
        }
        onProgress(0.94f)

        return latest.copy(
            isLoading = false,
            error = null,
            hasResult = true,
            suggestions = emptyList(),
            synonyms = synonyms,
            casNumber = synonyms.firstOrNull { casRegex.matches(it) } ?: latest.casNumber,
            pubDescription = pubDescription,
            wikiDescription = wikiDescription,
            ghsData = ghsData,
            advancedProperties = advancedProperties,
            classificationTags = pubChemContext.classificationTags,
            useEntries = pubChemContext.useEntries,
            sdfData = sdfResult?.sdf ?: latest.sdfData,
            sdfSource = sdfResult?.source ?: latest.sdfSource,
            sdfMessage = sdfResult?.message ?: latest.sdfMessage,
            offline2dPngBase64 = pngBase64,
            isOfflineDownload = true,
            isLoadingDesc = false,
            isLoadingSdf = false,
            isLoadingSafety = false,
            isLoadingSynonyms = false,
            isLoadingPubChemContext = false
        )
    }

    private suspend fun fetchPubChemDescription(cid: Long): String? = withContext(Dispatchers.IO) {
        runCatching {
            ApiClient.pubChem.getDescription(cid)
                .informationList
                ?.information
                ?.find { it.description != null }
                ?.description
                ?.let { el ->
                    when {
                        el.isJsonPrimitive -> el.asString
                        el.isJsonArray -> el.asJsonArray.mapNotNull {
                            runCatching { it.asString }.getOrNull()
                        }.joinToString("\n\n")
                        else -> null
                    }
                }
        }.getOrNull()
    }

    private suspend fun fetchWikiDescriptionBlocking(name: String): String? = withContext(Dispatchers.IO) {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return@withContext null
        val titleCased = cleanName.split(" ")
            .joinToString(" ") { word -> word.lowercase().replaceFirstChar { it.uppercase() } }
        runCatching { ApiClient.wiki.getSummary(titleCased).extract }.getOrNull()
            ?: runCatching { ApiClient.wiki.getSummary(cleanName.lowercase().replaceFirstChar { it.uppercase() }).extract }.getOrNull()
    }

    private suspend fun fetchGhsDataBlocking(cid: Long): GhsData? = withContext(Dispatchers.IO) {
        runCatching {
            parseGhsData(ApiClient.pubChemView.getSection(cid, "GHS Classification"))
        }.getOrNull()
    }

    private suspend fun fetchAdvancedProperties(cid: Long): List<AdvancedPropertyRow> = withContext(Dispatchers.IO) {
        runCatching {
            ApiClient.pubChem.getProperties(cid)
                .propertyTable
                ?.properties
                ?.firstOrNull()
                ?.let(::buildAdvancedProperties)
                .orEmpty()
        }.getOrDefault(emptyList())
    }

    private suspend fun fetchPubChemCompoundContext(cid: Long): PubChemCompoundContext = withContext(Dispatchers.IO) {
        coroutineScope {
            val classificationHeadings = listOf(
                "Chemical Classes",
                "Drug Classes",
                "MeSH Pharmacological Classification"
            )
            val useHeadings = listOf("Uses", "Therapeutic Uses")

            val classificationDeferred = classificationHeadings.map { heading ->
                async {
                    runCatching { extractPubChemSectionTexts(ApiClient.pubChemView.getSection(cid, heading)) }
                        .getOrDefault(emptyList())
                }
            }
            val useDeferred = useHeadings.map { heading ->
                async {
                    runCatching { extractPubChemSectionTexts(ApiClient.pubChemView.getSection(cid, heading)) }
                        .getOrDefault(emptyList())
                }
            }

            PubChemCompoundContext(
                classificationTags = buildPubChemClassificationTags(classificationDeferred.awaitAll().flatten()),
                useEntries = buildPubChemUseEntries(useDeferred.awaitAll().flatten())
            )
        }
    }

    private fun loadPubChemExtrasForCurrentCompound(cid: Long, force: Boolean = false) {
        val current = _uiState.value.takeIf { it.cid == cid } ?: return
        val needsProperties = current.advancedProperties.isEmpty()
        val needsContext = force || (current.classificationTags.isEmpty() && current.useEntries.isEmpty())
        if (!needsProperties && !needsContext) return

        _uiState.update { state ->
            if (state.cid == cid) state.copy(isLoadingPubChemContext = true) else state
        }

        viewModelScope.launch {
            val advancedProperties = if (needsProperties) fetchAdvancedProperties(cid) else current.advancedProperties
            val context = if (needsContext) fetchPubChemCompoundContext(cid) else PubChemCompoundContext(current.classificationTags, current.useEntries)
            val latest = _uiState.value.takeIf { it.cid == cid } ?: return@launch
            val updated = latest.copy(
                advancedProperties = advancedProperties.ifEmpty { latest.advancedProperties },
                classificationTags = context.classificationTags.ifEmpty { latest.classificationTags },
                useEntries = context.useEntries.ifEmpty { latest.useEntries },
                isLoadingPubChemContext = false
            )
            _uiState.value = updated
            writeCache(updated)
            DebugLog.d(
                "ChemSearch",
                "PubChem context loaded for CID $cid: properties=${updated.advancedProperties.size}, classes=${updated.classificationTags.size}, uses=${updated.useEntries.size}"
            )
        }
    }

    private suspend fun fetchSdfForOffline(state: ChemUiState): OfflineSdfResult? {
        val cid = state.cid ?: return null
        state.sdfData?.let { sdf ->
            return OfflineSdfResult(sdf, state.sdfSource ?: SdfSource.PUBCHEM, state.sdfMessage)
        }

        val pubChemSdf = runCatching {
            withContext(Dispatchers.IO) { ApiClient.pubChem.getSdf(cid).string() }
        }.getOrNull()

        pubChemSdf?.takeIf(::isUsableSdf)?.let { sdf ->
            return OfflineSdfResult(sdf, SdfSource.PUBCHEM, null)
        }

        val candidates = buildSdfIdentifierCandidates(
            smiles = state.smiles,
            connectivitySmiles = state.connectivitySmiles,
            inchi = state.inchi,
            inchiKey = state.inchiKey
        )
        if (candidates.isEmpty()) return null

        val fallback = runCatching {
            withContext(Dispatchers.IO) {
                fetchGeneratedSdfFromIdentifiers(candidates, expectedFormula = state.formula)
            }
        }.getOrNull()

        return fallback?.let {
            OfflineSdfResult(it.sdf, it.source, it.message)
        }
    }

    private suspend fun fetch2dStructurePngBase64(cid: Long): String? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("https://pubchem.ncbi.nlm.nih.gov/rest/pug/compound/cid/$cid/PNG?image_size=large")
                .header("User-Agent", "ChemSearch/1.0 (Android; github.com/FurtherSecrets24680)")
                .build()
            ApiClient.rawHttp.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val bytes = response.body.bytes()
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            }
        }.getOrNull()
    }

    fun clearSuggestions() = _uiState.update { it.copy(suggestions = emptyList()) }
    fun clearError() = _uiState.update { it.copy(error = null) }

    fun clearSearchResult() {
        searchJob?.cancel()
        autocompleteJob?.cancel()
        _query.value = ""
        _uiState.update { current ->
            ChemUiState(
            history = recentQueries(),
                aiProvider = current.aiProvider,
                descSource = getSavedDescSource(),
                suggestions = emptyList(),
                failedSearchQuery = null,
                searchCorrectionSuggestions = emptyList()
            )
        }
    }

    private fun loadAiKeyStatus(): Map<AiProvider, Boolean> =
        AiProvider.entries.associateWith { provider ->
            SecurePrefs.getString(prefs, provider.keyPref)?.isNotBlank() == true
        }

    private fun refreshAiKeyStatus() {
        val status = loadAiKeyStatus()
        _aiKeyStatus.value = status
        _hasGeminiKey.value = status[AiProvider.GEMINI] == true
        _hasGroqKey.value = status[AiProvider.GROQ] == true
    }

    private fun loadAiModelCatalogs(): Map<AiProvider, AiModelCatalog> =
        AiProvider.entries.associateWith { provider ->
            val selected = prefs.getString(modelPrefKey(provider), null)?.takeIf { it.isNotBlank() }
                ?: provider.modelName
            AiModelCatalog(
                models = (listOf(selected) + provider.defaultModels).distinct(),
                selectedModel = selected
            )
        }

    private fun modelPrefKey(provider: AiProvider): String = "ai_model_${provider.name.lowercase()}"

    fun getSelectedAiModel(provider: AiProvider): String =
        _aiModelCatalogs.value[provider]?.selectedModel?.takeIf { it.isNotBlank() }
            ?: prefs.getString(modelPrefKey(provider), null)?.takeIf { it.isNotBlank() }
            ?: provider.modelName

    fun setAiModel(provider: AiProvider, model: String) {
        val cleanModel = model.trim()
        if (cleanModel.isBlank()) return
        prefs.edit().putString(modelPrefKey(provider), cleanModel).apply()
        _aiModelCatalogs.update { catalogs ->
            val current = catalogs[provider] ?: AiModelCatalog(models = provider.defaultModels, selectedModel = provider.modelName)
            catalogs + (provider to current.copy(
                models = (listOf(cleanModel) + current.models + provider.defaultModels).distinct(),
                selectedModel = cleanModel,
                error = null
            ))
        }
        DebugLog.d("ChemSearch", "${provider.shortName} model → $cleanModel")
        if (_uiState.value.descSource == DescSource.AI && _uiState.value.aiProvider == provider) {
            _uiState.update { it.copy(aiDescription = null, aiDescriptionBasis = emptyList()) }
            fetchAiDescription()
        }
    }

    fun refreshAiModels(provider: AiProvider) {
        val key = getAiKey(provider) ?: run {
            _aiModelCatalogs.update { catalogs ->
                val current = catalogs[provider] ?: AiModelCatalog(models = provider.defaultModels, selectedModel = provider.modelName)
                catalogs + (provider to current.copy(error = "Add an API key first."))
            }
            return
        }
        _aiModelCatalogs.update { catalogs ->
            val current = catalogs[provider] ?: AiModelCatalog(models = provider.defaultModels, selectedModel = provider.modelName)
            catalogs + (provider to current.copy(isLoading = true, error = null))
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    when (provider) {
                        AiProvider.GEMINI -> ApiClient.gemini.listModels(key)
                            .models
                            ?.filter { model -> model.supportedGenerationMethods?.contains("generateContent") != false }
                            ?.mapNotNull { it.name?.removePrefix("models/") }
                            ?: emptyList()
                        else -> {
                            val api = when (provider) {
                                AiProvider.GROQ -> ApiClient.groq
                                AiProvider.OPENAI -> ApiClient.openAi
                                AiProvider.OPENROUTER -> ApiClient.openRouter
                                AiProvider.MISTRAL -> ApiClient.mistral
                                AiProvider.GEMINI -> error("Gemini uses a separate API")
                            }
                            api.listModels("Bearer $key").data?.mapNotNull { it.id } ?: emptyList()
                        }
                    }.filter { it.isNotBlank() }.distinct().sorted()
                }
            }
            _aiModelCatalogs.update { catalogs ->
                val current = catalogs[provider] ?: AiModelCatalog(models = provider.defaultModels, selectedModel = provider.modelName)
                result.fold(
                    onSuccess = { fetched ->
                        val selected = current.selectedModel.ifBlank { provider.modelName }
                        val models = (listOf(selected) + fetched + provider.defaultModels).distinct()
                        catalogs + (provider to current.copy(
                            models = models,
                            selectedModel = selected,
                            isLoading = false,
                            error = if (fetched.isEmpty()) "No models returned. Keeping defaults." else null
                        ))
                    },
                    onFailure = { e ->
                        catalogs + (provider to current.copy(
                            isLoading = false,
                            error = e.message ?: "Could not refresh models."
                        ))
                    }
                )
            }
        }
    }

    fun getAiKey(provider: AiProvider): String? =
        SecurePrefs.getString(prefs, provider.keyPref)?.ifBlank { null }

    fun hasAiKey(provider: AiProvider): Boolean = getAiKey(provider)?.isNotBlank() == true

    fun saveAiKey(provider: AiProvider, key: String) {
        runCatching { SecurePrefs.putString(prefs, provider.keyPref, key) }
            .onFailure { DebugLog.e("ChemSearch", "${provider.shortName} key save failed: ${it.message}") }
        refreshAiKeyStatus()
    }

    fun clearAiKey(provider: AiProvider) {
        SecurePrefs.remove(prefs, provider.keyPref)
        refreshAiKeyStatus()
        if (_uiState.value.aiProvider == provider) {
            _uiState.update { it.copy(aiDescription = null, aiDescriptionBasis = emptyList()) }
        }
    }

    fun getGeminiKey(): String? = getAiKey(AiProvider.GEMINI)
    fun saveGeminiKey(key: String) = saveAiKey(AiProvider.GEMINI, key)
    fun clearGeminiKey() = clearAiKey(AiProvider.GEMINI)

    fun getGroqKey(): String? = getAiKey(AiProvider.GROQ)
    fun saveGroqKey(key: String) = saveAiKey(AiProvider.GROQ, key)
    fun clearGroqKey() = clearAiKey(AiProvider.GROQ)

    private fun getSavedDescSource(): DescSource =
        DescSource.entries.firstOrNull { it.name == prefs.getString("desc_source", null) }
            ?: DescSource.PUBCHEM

    private fun getSavedColorScheme(): AppColorScheme =
        AppColorScheme.entries.firstOrNull { it.name == prefs.getString("color_scheme", null) }
            ?: AppColorScheme.BLUE

    private fun getSavedDefaultStructureView(): DefaultStructureView =
        DefaultStructureView.entries.firstOrNull { it.name == prefs.getString("default_structure_view", null) }
            ?: DefaultStructureView.TWO_D

    private fun getSavedOfflineDownloadQuality(): OfflineDownloadQuality =
        OfflineDownloadQuality.entries.firstOrNull { it.name == prefs.getString("offline_download_quality", null) }
            ?: OfflineDownloadQuality.COMPLETE

    private fun getSavedFormulaDisplayStyle(): FormulaDisplayStyle =
        FormulaDisplayStyle.entries.firstOrNull {
            it.name == normalizeSavedFormulaDisplayStyleName(prefs.getString("formula_display_style", null))
        }
            ?: FormulaDisplayStyle.CONVENTIONAL

    private fun normalizeSavedFormulaDisplayStyleName(name: String?): String? =
        when (name) {
            "PUBCHEM" -> FormulaDisplayStyle.HILL.name
            "CHARGE_FOCUSED" -> FormulaDisplayStyle.CONVENTIONAL.name
            else -> name
        }

    private fun getSavedCacheSizeLimit(): CacheSizeLimit =
        CacheSizeLimit.entries.firstOrNull {
            it.name == normalizeSavedCacheSizeLimitName(prefs.getString("cache_size_limit", null))
        }
            ?: CacheSizeLimit.UNLIMITED

    private fun normalizeSavedCacheSizeLimitName(name: String?): String? =
        when (name) {
            "MB_250" -> CacheSizeLimit.UNLIMITED.name
            else -> name
        }

    private fun getSavedCacheRetention(): CacheRetention =
        CacheRetention.entries.firstOrNull { it.name == prefs.getString("cache_retention", null) }
            ?: CacheRetention.MANUAL

    private fun saveDescSource(source: DescSource) {
        prefs.edit().putString("desc_source", source.name).apply()
        viewModelScope.launch { settingsStore.setDescSource(source) }
    }

    private fun loadHistory(): List<String> =
        prefs.getString(PREF_HISTORY, "")?.split("||")?.filter { it.isNotBlank() } ?: emptyList()

    private fun loadRecentSearches(): List<RecentSearch> {
        val json = prefs.getString(PREF_RECENT_SEARCHES, null)
        val stored = if (json.isNullOrBlank()) {
            emptyList()
        } else {
            runCatching {
                val type = object : TypeToken<List<RecentSearch>>() {}.type
                gson.fromJson<List<RecentSearch>>(json, type)
            }.getOrNull().orEmpty()
        }

        if (stored.isNotEmpty()) return stored
            .mapNotNull { it.normalizedOrNull() }
            .distinctBy { it.query.lowercase() }

        return loadHistory().map { query ->
            RecentSearch(query = query, lastSearchedAt = 0L, pinned = false)
        }
    }

    private fun saveRecentSearches(searches: List<RecentSearch>) {
        val cleaned = searches
            .mapNotNull { it.normalizedOrNull() }
            .distinctBy { it.query.lowercase() }
        prefs.edit()
            .putString(PREF_RECENT_SEARCHES, gson.toJson(cleaned))
            .putString(PREF_HISTORY, cleaned.joinToString("||") { it.query })
            .apply()
    }

    private fun RecentSearch.normalizedOrNull(): RecentSearch? {
        val safeQuery = runCatching { query }.getOrNull()?.trim().orEmpty()
        if (safeQuery.isBlank()) return null
        return RecentSearch(
            query = safeQuery,
            lastSearchedAt = runCatching { lastSearchedAt }.getOrNull()?.takeIf { it > 0L }
                ?: System.currentTimeMillis(),
            pinned = runCatching { pinned }.getOrNull() ?: false
        )
    }

    private fun recentQueries(): List<String> = _recentSearches.value.map { it.query }

    private fun saveToHistory(name: String) {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return
        val existing = _recentSearches.value.firstOrNull { it.query.equals(cleanName, ignoreCase = true) }
        val updated = listOf(
            RecentSearch(
                query = cleanName,
                lastSearchedAt = System.currentTimeMillis(),
                pinned = existing?.pinned ?: false
            )
        ) + _recentSearches.value.filterNot { it.query.equals(cleanName, ignoreCase = true) }
        _recentSearches.value = updated
        saveRecentSearches(updated)
        _uiState.update { it.copy(history = recentQueries()) }
    }

    fun clearHistory() {
        prefs.edit().remove(PREF_HISTORY).remove(PREF_RECENT_SEARCHES).apply()
        _recentSearches.value = emptyList()
        _uiState.update { it.copy(history = emptyList()) }
        DebugLog.d("ChemSearch", "Search history cleared")
    }

    fun removeHistoryItem(query: String) {
        val updated = _recentSearches.value.filterNot { it.query.equals(query, ignoreCase = true) }
        _recentSearches.value = updated
        saveRecentSearches(updated)
        _uiState.update { it.copy(history = recentQueries()) }
        DebugLog.d("ChemSearch", "Removed recent search: $query")
    }

    fun restoreRecentSearch(search: RecentSearch) {
        val normalized = search.normalizedOrNull() ?: return
        val updated = listOf(normalized) + _recentSearches.value.filterNot {
            it.query.equals(normalized.query, ignoreCase = true)
        }
        _recentSearches.value = updated
        saveRecentSearches(updated)
        _uiState.update { it.copy(history = recentQueries()) }
        DebugLog.d("ChemSearch", "Restored recent search: ${normalized.query}")
    }

    fun restoreRecentSearches(searches: List<RecentSearch>) {
        val cleaned = searches
            .mapNotNull { it.normalizedOrNull() }
            .distinctBy { it.query.lowercase() }
        _recentSearches.value = cleaned
        saveRecentSearches(cleaned)
        _uiState.update { it.copy(history = recentQueries()) }
        DebugLog.d("ChemSearch", "Restored ${cleaned.size} recent searches")
    }

    fun toggleRecentPin(query: String) {
        val updated = _recentSearches.value.map { item ->
            if (item.query.equals(query, ignoreCase = true)) item.copy(pinned = !item.pinned) else item
        }
        _recentSearches.value = updated
        saveRecentSearches(updated)
        _uiState.update { it.copy(history = recentQueries()) }
        DebugLog.d("ChemSearch", "Recent pin toggled: $query")
    }



    fun onIsomerQueryChange(q: String) {
        _uiState.update {
            it.copy(
                isomerQuery = q,
                isomers = emptyList(),
                isomerResultLimit = 20,
                isomerCanLoadMore = false,
                isLoadingMoreIsomers = false,
                isomerError = null
            )
        }
    }

    fun searchIsomers() {
        val formula = _uiState.value.isomerQuery.trim()
        if (formula.isBlank()) return
        loadIsomers(formula = formula, maxRecords = 20, isLoadMore = false)
    }

    fun loadMoreIsomers() {
        val state = _uiState.value
        val formula = state.isomerQuery.trim()
        if (formula.isBlank() || state.isLoadingIsomers || state.isLoadingMoreIsomers || !state.isomerCanLoadMore) return
        val nextLimit = if (state.isomerResultLimit < 20) 20 else state.isomerResultLimit + 20
        loadIsomers(formula = formula, maxRecords = nextLimit, isLoadMore = true)
    }

    private fun loadIsomers(formula: String, maxRecords: Int, isLoadMore: Boolean) {
        viewModelScope.launch {
            DebugLog.d("ChemSearch", "Isomer search: \"$formula\" limit=$maxRecords")
            _uiState.update {
                if (isLoadMore) {
                    it.copy(isLoadingMoreIsomers = true, isomerError = null)
                } else {
                    it.copy(
                        isLoadingIsomers = true,
                        isLoadingMoreIsomers = false,
                        isomers = emptyList(),
                        isomerResultLimit = maxRecords,
                        isomerCanLoadMore = false,
                        isomerError = null
                    )
                }
            }
            try {
                val cids = fetchFormulaCids(formula, maxRecords).take(maxRecords)
                if (cids.isEmpty()) throw NoSuchElementException("No isomers found for $formula.")

                DebugLog.d("ChemSearch", "Isomers: ${cids.size} CIDs for $formula")

                val cidString = cids.joinToString(",")
                val titleMap: Map<Long, TitleProperty> = runCatching {
                    ApiClient.pubChem.getTitles(cidString)
                        .propertyTable?.properties
                        ?.mapNotNull { p -> p.cid?.let { it to p } }
                        ?.toMap()
                }.getOrNull() ?: emptyMap()

                val isomers = cids.map { cid ->
                    val property = titleMap[cid]
                    IsomerItem(
                        cid = cid,
                        title = property?.title ?: "CID $cid",
                        isIsotope = (property?.isotopeAtomCount ?: 0) > 0
                    )
                }
                DebugLog.d("ChemSearch", "Isomers loaded: ${isomers.size} items")
                _uiState.update {
                    it.copy(
                        isLoadingIsomers = false,
                        isLoadingMoreIsomers = false,
                        isomers = isomers,
                        isomerResultLimit = maxRecords,
                        isomerCanLoadMore = cids.size >= maxRecords
                    )
                }

            } catch (e: Exception) {
                val msg = when (e) {
                    is java.io.IOException -> "Network error. Check your connection."
                    is NoSuchElementException -> e.message ?: "No isomers found."
                    else -> "No isomers found for \"$formula\". Check the formula and try again."
                }
                DebugLog.e("ChemSearch", "Isomer search failed: ${e.message}")
                _uiState.update {
                    it.copy(
                        isLoadingIsomers = false,
                        isLoadingMoreIsomers = false,
                        isomerCanLoadMore = if (isLoadMore) false else it.isomerCanLoadMore,
                        isomerError = msg
                    )
                }
            }
        }
    }

    private suspend fun fetchFormulaCids(formula: String, maxRecords: Int): List<Long> {
        var status = pubChemCidLookupStatus(ApiClient.pubChem.getIsomerCids(formula, maxRecords))
        repeat(8) { attempt ->
            when (status) {
                is PubChemCidLookupStatus.Ready -> return status.cids
                is PubChemCidLookupStatus.Empty -> return emptyList()
                is PubChemCidLookupStatus.Waiting -> {
                    delay(700L + attempt * 250L)
                    status = pubChemCidLookupStatus(ApiClient.pubChem.getCidsByListKey(status.listKey))
                }
            }
        }

        return when (val finalStatus = status) {
            is PubChemCidLookupStatus.Ready -> finalStatus.cids
            else -> emptyList()
        }
    }

    fun setStructureSearchMode(mode: StructureSearchMode) {
        _structureSearchState.update { it.copy(mode = mode, error = null) }
    }

    fun setStructureSimilarityThreshold(threshold: Int) {
        _structureSearchState.update { it.copy(similarityThreshold = threshold.coerceIn(70, 99), error = null) }
    }

    fun setStructureMaxRecords(maxRecords: Int) {
        _structureSearchState.update { it.copy(maxRecords = maxRecords.coerceIn(5, 100), error = null) }
    }

    fun clearStructureSearchResults() {
        _structureSearchState.update { it.copy(isLoading = false, results = emptyList(), error = null, searchedMolfile = null) }
    }

    fun standardizeStructure(sketch: StructureSketch) {
        if (sketch.atoms.isEmpty()) {
            _structureSearchState.update {
                it.copy(error = "Draw or import a structure before cleaning it.")
            }
            return
        }
        viewModelScope.launch {
            _structureSearchState.update {
                it.copy(isStandardizing = true, error = null, standardizeMessage = null, standardizedSketch = null)
            }
            try {
                val sdf = ApiClient.pubChem.standardizeSdf(sketch.toMolfile()).string()
                val standardized = StructureSketch.fromMolfile(sdf)
                if (standardized.atoms.isEmpty()) throw IllegalArgumentException("PubChem could not clean this drawing.")
                _structureSearchState.update {
                    it.copy(
                        isStandardizing = false,
                        standardizedSketch = standardized,
                        standardizeMessage = "Structure cleaned with PubChem standardization."
                    )
                }
            } catch (e: Exception) {
                DebugLog.e("ChemSearch", "Structure standardization failed: ${e.message}")
                _structureSearchState.update {
                    it.copy(
                        isStandardizing = false,
                        error = when (e) {
                            is IOException -> "Network error. Check your connection."
                            else -> e.message ?: "Could not clean this structure."
                        }
                    )
                }
            }
        }
    }

    fun importStructureText(text: String) {
        val input = text.trim()
        if (input.isBlank()) {
            _structureSearchState.update { it.copy(error = "Paste a SMILES, InChI, or molfile first.") }
            return
        }
        viewModelScope.launch {
            _structureSearchState.update {
                it.copy(isStandardizing = true, error = null, standardizeMessage = null, standardizedSketch = null)
            }
            try {
                val imported = if (input.contains("M  END") || input.contains("V2000")) {
                    StructureSketch.fromMolfile(input)
                } else {
                    val sdf = if (input.startsWith("InChI=", ignoreCase = true)) {
                        ApiClient.pubChem.standardizeInchi(input).string()
                    } else {
                        ApiClient.pubChem.standardizeSmiles(input).string()
                    }
                    StructureSketch.fromMolfile(sdf)
                }
                if (imported.atoms.isEmpty()) throw IllegalArgumentException("Could not read that structure.")
                _structureSearchState.update {
                    it.copy(
                        isStandardizing = false,
                        standardizedSketch = imported,
                        standardizeMessage = "Imported ${imported.formula.ifBlank { "structure" }}."
                    )
                }
            } catch (e: Exception) {
                DebugLog.e("ChemSearch", "Structure import failed: ${e.message}")
                _structureSearchState.update {
                    it.copy(
                        isStandardizing = false,
                        error = when (e) {
                            is IOException -> "Network error. Check your connection."
                            else -> "Could not import that structure. Try a valid SMILES, InChI, or V2000 molfile."
                        }
                    )
                }
            }
        }
    }

    fun consumeStructureSketchUpdate() {
        _structureSearchState.update { it.copy(standardizedSketch = null) }
    }

    fun searchByStructure(sketch: StructureSketch) {
        val currentState = _structureSearchState.value
        val mode = currentState.mode
        val maxRecords = currentState.maxRecords
        val threshold = currentState.similarityThreshold
        if (!sketch.canSearch) {
            _structureSearchState.update {
                it.copy(
                    isLoading = false,
                    error = StructureSearchWarning.forSketch(sketch).firstOrNull()?.message
                        ?: "Draw at least two connected atoms before searching.",
                    results = emptyList()
                )
            }
            return
        }
        val molfile = sketch.toMolfile()
        viewModelScope.launch {
            DebugLog.d("ChemSearch", "Structure search: ${mode.name}")
            _structureSearchState.update {
                it.copy(
                    isLoading = true,
                    results = emptyList(),
                    error = null,
                    searchedMolfile = molfile
                )
            }
            try {
                val response = ApiClient.pubChem.searchStructureBySdf(
                    operation = mode.pubChemOperation,
                    sdf = molfile,
                    maxRecords = maxRecords,
                    threshold = if (mode == StructureSearchMode.SIMILAR) threshold else null
                )
                val cids = response.identifierList?.cid?.take(maxRecords).orEmpty()
                if (cids.isEmpty()) throw NoSuchElementException("No structure matches found.")

                val propertyMap = loadStructurePropertiesForCids(cids)
                val results = cids.map { cid ->
                    val property = propertyMap[cid]
                    StructureSearchResultItem(
                        cid = cid,
                        title = property?.title ?: "CID $cid",
                        formula = property?.molecularFormula.orEmpty(),
                        molecularWeight = property?.molecularWeight.orEmpty()
                    )
                }
                DebugLog.d("ChemSearch", "Structure search loaded ${results.size} results")
                _structureSearchState.update {
                    it.copy(
                        isLoading = false,
                        results = results,
                        error = null
                    )
                }
            } catch (e: Exception) {
                val msg = when (e) {
                    is IOException -> "Network error. Check your connection."
                    is NoSuchElementException -> e.message ?: "No structure matches found."
                    else -> "Structure search failed. Try a simpler drawing."
                }
                DebugLog.e("ChemSearch", "Structure search failed: ${e.message}")
                _structureSearchState.update { it.copy(isLoading = false, error = msg) }
            }
        }
    }

    private suspend fun loadStructurePropertiesForCids(cids: List<Long>): Map<Long, CompoundProperty> {
        if (cids.isEmpty()) return emptyMap()
        val cidString = cids.joinToString(",")
        return runCatching {
            ApiClient.pubChem.getStructureResultProperties(cidString)
                .propertyTable?.properties
                ?.mapNotNull { property -> property.cid?.let { it to property } }
                ?.toMap()
        }.getOrNull() ?: emptyMap()
    }

    private suspend fun loadTitlesForCids(cids: List<Long>): Map<Long, String> {
        if (cids.isEmpty()) return emptyMap()
        val cidString = cids.joinToString(",")
        return runCatching {
            ApiClient.pubChem.getTitles(cidString)
                .propertyTable?.properties
                ?.mapNotNull { property -> property.cid?.let { it to (property.title ?: "CID $it") } }
                ?.toMap()
        }.getOrNull() ?: emptyMap()
    }

    fun searchByCid(cid: Long) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            DebugLog.d("ChemSearch", "Search by CID: $cid")
            _uiState.update {
                it.copy(
                    isLoading = true, error = null, hasResult = false,
                    failedSearchQuery = null,
                    searchCorrectionSuggestions = emptyList(),
                    sdfData = null, sdfSource = null, sdfMessage = null, ghsData = null, isLoadingSafety = false,
                    isomerMode = false, isomers = emptyList(),
                    isCached = false,
                    isOfflineDownload = false,
                    offline2dPngBase64 = null,
                    advancedProperties = emptyList(),
                    classificationTags = emptyList(),
                    useEntries = emptyList(),
                    isLoadingPubChemContext = false,
                    aiDescriptionBasis = emptyList()
                )
            }

            val cached = readCache(cid)
            if (cached != null) {
                DebugLog.d("ChemSearch", "Cache hit for CID $cid (${cached.name})")
                val savedSource = getSavedDescSource()
                _uiState.update {
                    cached.copy(
                        isLoading = false, hasResult = true,
                        history = recentQueries(), descSource = savedSource,
                        sdfData = null, sdfSource = null, sdfMessage = null, activeTab = defaultMolTab(),
                        isomerMode = false, isomers = emptyList(),
                        isCached = true,
                        isOfflineDownload = false,
                        offline2dPngBase64 = null,
                        isLoadingSynonyms = false,
                        isLoadingPubChemContext = false
                    )
                }
                if (_uiState.value.activeTab == MolTab.THREE_D) fetchSdfData()
                backfillStructureMetadataIfMissing(cached, cid)
                _query.value = cached.name
                saveToHistory(cached.name)
                loadSynonymsForCurrentCompound(cid)
                loadPubChemExtrasForCurrentCompound(cid)
                when (savedSource) {
                    DescSource.WIKI -> if (cached.wikiDescription == null) fetchWikiDescription()
                    DescSource.AI   -> if (cached.aiDescription == null) fetchAiDescription()
                    else -> Unit
                }
                if (cached.ghsData == null) fetchSafetyData()
                return@launch
            }

            try {
                val propsDeferred = async { runCatching { ApiClient.pubChem.getProperties(cid) }.getOrNull() }
                val descDeferred  = async { runCatching { ApiClient.pubChem.getDescription(cid) }.getOrNull() }
                val recordDeferred = async { runCatching { ApiClient.pubChem.getRecord(cid) }.getOrNull() }

                val props = propsDeferred.await()?.propertyTable?.properties?.firstOrNull()
                    ?: CompoundProperty(cid = cid)
                val descItem = descDeferred.await()
                    ?.informationList?.information?.find { it.description != null }
                val structureCounts = extractStructureCounts(recordDeferred.await())

                val compoundName = props.title?.takeIf { it.isNotBlank() }
                    ?: props.iupacName?.takeIf { it.isNotBlank() }
                    ?: "CID $cid"
                val rawFormula = props.molecularFormula ?: ""
                val formula = formatConventionalFormula(rawFormula)

                val pubDesc: String? = descItem?.description?.let { el ->
                    when {
                        el.isJsonPrimitive -> el.asString
                        el.isJsonArray -> el.asJsonArray
                            .mapNotNull { runCatching { it.asString }.getOrNull() }
                            .joinToString("\n\n")
                        else -> null
                    }
                }

                val savedSource = getSavedDescSource()
                saveToHistory(compoundName)
                DebugLog.d("ChemSearch", "CID $cid resolved: \"$compoundName\"")

                val newState = ChemUiState(
                    isLoading = false, hasResult = true,
                    cid = cid,
                    name = compoundName.replaceFirstChar { c -> c.uppercase() },
                    formula = formula,
                    rawFormula = rawFormula,
                    empiricalFormula = getEmpiricalFormula(formula),
                    weight = props.molecularWeight ?: "",
                    charge = props.charge ?: 0,
                    atomNumber = structureCounts.atomCount,
                    bondNumber = structureCounts.bondCount,
                    covalentUnitCount = props.covalentUnitCount,
                    iupacName = props.iupacName ?: "",
                    smiles = props.smiles ?: "",
                    connectivitySmiles = props.connectivitySmiles ?: props.smiles ?: "",
                    inchiKey = props.inchiKey ?: "",
                    inchi = props.inchi ?: "",
                    synonyms = emptyList(),
                    casNumber = null,
                    pubDescription = pubDesc,
                    wikiDescription = null, aiDescription = null,
                    descSource = savedSource,
                    elementalData = calcElementalData(formula),
                    history = recentQueries(),
                    activeTab = defaultMolTab(),
                    aiProvider = _uiState.value.aiProvider,
                    isomerMode = false,
                    isomers = emptyList(),
                    isCached = false,
                    isLoadingSynonyms = true,
                    advancedProperties = buildAdvancedProperties(props)
                )
                _uiState.update { newState }
                if (newState.activeTab == MolTab.THREE_D) fetchSdfData()
                _query.value = compoundName
                writeCache(newState)
                loadSynonymsForCurrentCompound(cid, force = true)
                loadPubChemExtrasForCurrentCompound(cid, force = true)

                when (savedSource) {
                    DescSource.WIKI -> fetchWikiDescription()
                    DescSource.AI   -> fetchAiDescription()
                    else -> Unit
                }
                fetchSafetyData()

            } catch (e: Exception) {
                val msg = when (e) {
                    is java.io.IOException -> "Network error. Check your connection."
                    else -> "Could not load compound (CID $cid)."
                }
                DebugLog.e("ChemSearch", "searchByCid failed for $cid: ${e.message}")
                _uiState.update { it.copy(isLoading = false, error = msg) }
            }
        }
    }

    fun searchRandomCompound() {
        searchByCid(randomPubChemCid())
    }

    private data class StructureCounts(
        val atomCount: Int? = null,
        val bondCount: Int? = null
    )

    private suspend fun backfillStructureMetadataIfMissing(cached: ChemUiState, cid: Long) {
        if (cached.atomNumber != null && cached.bondNumber != null && cached.covalentUnitCount != null) return

        val props = runCatching { ApiClient.pubChem.getProperties(cid) }
            .getOrNull()
            ?.propertyTable
            ?.properties
            ?.firstOrNull()
        val counts = extractStructureCounts(runCatching { ApiClient.pubChem.getRecord(cid) }.getOrNull())

        val atomNumber = cached.atomNumber ?: counts.atomCount
        val bondNumber = cached.bondNumber ?: counts.bondCount
        val covalentUnits = cached.covalentUnitCount ?: props?.covalentUnitCount

        if (atomNumber == cached.atomNumber &&
            bondNumber == cached.bondNumber &&
            covalentUnits == cached.covalentUnitCount
        ) return

        _uiState.update { state ->
            if (state.cid != cid) state else state.copy(
                atomNumber = atomNumber,
                bondNumber = bondNumber,
                covalentUnitCount = covalentUnits
            )
        }
        _uiState.value
            .takeIf { it.cid == cid }
            ?.copy(isCached = false)
            ?.let { writeCache(it) }
    }

    private fun extractStructureCounts(record: JsonObject?): StructureCounts {
        val compound = runCatching {
            record
                ?.getAsJsonArray("PC_Compounds")
                ?.firstOrNull()
                ?.asJsonObject
        }.getOrNull() ?: return StructureCounts()

        val atomCount = runCatching {
            compound
                .getAsJsonObject("atoms")
                ?.getAsJsonArray("aid")
                ?.size()
        }.getOrNull()

        val bondCount = runCatching {
            compound
                .getAsJsonObject("bonds")
                ?.getAsJsonArray("aid1")
                ?.size()
        }.getOrNull()

        return StructureCounts(atomCount = atomCount, bondCount = bondCount)
    }

    private fun getEmpiricalFormula(formula: String): String {
        return calculateEmpiricalFormula(formula)
    }

    private fun calcElementalData(formula: String): List<ElementData> {
        return calculateElementalPercentages(formula)
            .map { ElementData(it.element, it.percentage.toFloat()) }
    }

    fun fetchSafetyData() {
        val cid = _uiState.value.cid ?: return
        _uiState.update { it.copy(isLoadingSafety = true) }
        DebugLog.d("ChemSearch", "Fetching GHS safety data for CID $cid")
        viewModelScope.launch {
            try {
                val json = ApiClient.pubChemView.getSection(cid, "GHS Classification")
                val ghs = parseGhsData(json)
                DebugLog.d("ChemSearch", "GHS result: signal=${ghs?.signalWord}, pictograms=${ghs?.pictogramCodes?.size ?: 0}, hazards=${ghs?.hazardStatements?.size ?: 0}")
                _uiState.update { it.copy(isLoadingSafety = false, ghsData = ghs) }
            } catch (e: Exception) {
                DebugLog.e("ChemSearch", "GHS fetch failed for CID $cid: ${e.message}")
                _uiState.update { it.copy(isLoadingSafety = false, ghsData = null) }
            }
        }
    }

    private val hazardCodeRegex = Regex("\\bH\\d{3}(?:[+/](?:H)?\\d{3})*\\b")
    private val hazardPercentRegex = Regex("\\((\\d+(?:\\.\\d+)?)%\\)")
    private val hazardWhitespaceRegex = Regex("\\s+")

    private data class HazardStatementChoice(
        val raw: String,
        val percent: Float?,
        val normalized: String
    )

    private fun normalizeHazardStatement(statement: String): String {
        return statement
            .replace(hazardPercentRegex, "")
            .replace(hazardWhitespaceRegex, " ")
            .trim()
    }

    private fun shouldReplaceHazard(existing: HazardStatementChoice, candidate: HazardStatementChoice): Boolean {
        val existingPercent = existing.percent
        val candidatePercent = candidate.percent

        if (candidatePercent != null && existingPercent == null) return true
        if (candidatePercent == null && existingPercent != null) return false
        if (candidatePercent != null && existingPercent != null) {
            if (candidatePercent > existingPercent) return true
            if (candidatePercent < existingPercent) return false
        }

        val existingScore = existing.normalized.length
        val candidateScore = candidate.normalized.length
        return candidateScore > existingScore
    }

    private fun dedupeHazardStatements(statements: List<String>): List<String> {
        val bestByKey = LinkedHashMap<String, HazardStatementChoice>()
        for (statement in statements) {
            val normalized = normalizeHazardStatement(statement)
            if (normalized.isBlank()) continue
            val key = hazardCodeRegex.find(statement)?.value?.uppercase()
                ?: normalized.lowercase()
            val percent = hazardPercentRegex.find(statement)?.groupValues?.getOrNull(1)?.toFloatOrNull()
            val candidate = HazardStatementChoice(statement, percent, normalized)
            val existing = bestByKey[key]
            if (existing == null || shouldReplaceHazard(existing, candidate)) {
                bestByKey[key] = candidate
            }
        }
        return bestByKey.values.map { it.raw }
    }


    private fun parseGhsData(json: com.google.gson.JsonObject): GhsData? {
        return try {
            val record = json.getAsJsonObject("Record") ?: return null
            val sections = record.getAsJsonArray("Section") ?: return null

            fun flatten(arr: com.google.gson.JsonArray): List<com.google.gson.JsonObject> {
                val result = mutableListOf<com.google.gson.JsonObject>()
                for (el in arr) {
                    val obj = runCatching { el.asJsonObject }.getOrNull() ?: continue
                    result.add(obj)
                    obj.getAsJsonArray("Section")?.let { result.addAll(flatten(it)) }
                }
                return result
            }

            val allSections = flatten(sections)

            val hazardStatements = mutableListOf<String>()
            var signalWord: String? = null
            val pictogramCodes = mutableListOf<String>()

            for (section in allSections) {
                val heading = section.get("TOCHeading")?.asString ?: continue
                val infoList = section.getAsJsonArray("Information") ?: continue

                for (infoEl in infoList) {
                    val info = runCatching { infoEl.asJsonObject }.getOrNull() ?: continue
                    val name = info.get("Name")?.asString ?: continue
                    val value = info.getAsJsonObject("Value") ?: continue
                    val swm = value.getAsJsonArray("StringWithMarkup") ?: continue

                    when {
                        heading == "GHS Classification" && name.contains("Signal", ignoreCase = true) -> {
                            signalWord = swm.firstOrNull()
                                ?.asJsonObject?.get("String")?.asString
                        }
                        heading == "GHS Classification" && name.contains("Hazard Statement", ignoreCase = true) -> {
                            swm.mapNotNull {
                                runCatching { it.asJsonObject.get("String")?.asString }.getOrNull()
                            }
                                .filter { it.isNotBlank() && !it.equals("Not Classified", ignoreCase = true) && !it.startsWith("Reported as not meeting", ignoreCase = true) }
                                .let { hazardStatements.addAll(it) }
                        }
                        heading == "Pictogram(s)" || name.contains("Pictogram", ignoreCase = true) -> {
                            swm.forEach { markupEl ->
                                val obj = runCatching { markupEl.asJsonObject }.getOrNull() ?: return@forEach
                                obj.getAsJsonArray("Markup")?.forEach { m ->
                                    val mObj = runCatching { m.asJsonObject }.getOrNull() ?: return@forEach
                                    val url = mObj.get("URL")?.asString ?: return@forEach
                                    Regex("GHS\\d{2}").find(url)?.value?.let { pictogramCodes.add(it) }
                                }
                            }
                        }
                    }
                }
            }

            val dedupedHazards = dedupeHazardStatements(hazardStatements)
            if (dedupedHazards.isEmpty() && signalWord == null && pictogramCodes.isEmpty()) return null
            GhsData(signalWord, dedupedHazards, pictogramCodes.distinct(), retrievedAt = System.currentTimeMillis())
        } catch (e: Exception) {
            Log.e("ChemViewModel", "GHS parse error", e)
            null
        }
    }

    private fun maybeNotifyUpdate(latestTag: String, downloadUrl: String?, releaseUrl: String?) {
        if (!BuildConfig.GITHUB_UPDATES_ENABLED) return
        if (!_updateNotificationsEnabled.value) return
        val lastNotified = prefs.getString(PREF_UPDATE_LAST_NOTIFIED, null)
        if (latestTag.equals(lastNotified, ignoreCase = true)) return
        sendUpdateNotification(latestTag, downloadUrl ?: releaseUrl)
        prefs.edit().putString(PREF_UPDATE_LAST_NOTIFIED, latestTag).apply()
    }

    fun sendDebugUpdateNotification() {
        if (!BuildConfig.GITHUB_UPDATES_ENABLED) {
            DebugLog.d("ChemSearch", "Update notification skipped in F-Droid build")
            return
        }
        val debugTag = "debug-${System.currentTimeMillis() % 100000}"
        val url = _updateStatus.value.downloadUrl
            ?: _updateStatus.value.releaseUrl
            ?: "https://github.com/FurtherSecrets24680/chemsearch-android/releases/latest"
        sendUpdateNotification(debugTag, url)
        DebugLog.d("ChemSearch", "Debug update notification sent ($debugTag)")
    }

    private fun sendUpdateNotification(latestTag: String, url: String?) {
        val context = getApplication<Application>()
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
            .setContentTitle("ChemSearch update available")
            .setContentText("Version $latestTag is available")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        if (pendingIntent != null) builder.setContentIntent(pendingIntent)
        NotificationManagerCompat.from(context).notify(UPDATE_NOTIFICATION_ID, builder.build())
    }

    private fun ensureUpdateChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val context = getApplication<Application>()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(UPDATE_CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            UPDATE_CHANNEL_ID,
            "Updates",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        channel.description = "Notifications when a new version is available"
        manager.createNotificationChannel(channel)
    }

    private fun isUpdateAvailable(currentVersion: String, latestTag: String): Boolean {
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
        private const val PREF_UPDATE_NOTIFICATIONS = "update_notifications"
        private const val PREF_UPDATE_LAST_CHECK = "update_last_check"
        private const val PREF_UPDATE_LAST_NOTIFIED = "update_last_notified"
        private const val PREF_WELCOME_SKIPPED = "welcome_skipped"
        private const val PREF_HISTORY = "history"
        private const val PREF_RECENT_SEARCHES = "recent_searches"
        private const val UPDATE_CHECK_INTERVAL_MS = 12 * 60 * 60 * 1000L
    }
}

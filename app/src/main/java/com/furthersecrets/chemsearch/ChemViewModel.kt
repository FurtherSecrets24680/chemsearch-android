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
            }
        }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                offlineDownloadRepository.migrateLegacyDownloadsIfNeeded()
            }
            offlineDownloadRepository.downloads
                .catch { e -> DebugLog.e("ChemSearch", "Download database read failed: ${e.message}") }
                .collect { downloads ->
                    _downloads.value = downloads.map { it.withResolvedOfflineMetadata() }
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
        checkForUpdates()
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
        _updateNotificationsEnabled.value = enabled
        prefs.edit().putBoolean(PREF_UPDATE_NOTIFICATIONS, enabled).apply()
        viewModelScope.launch { settingsStore.setUpdateNotificationsEnabled(enabled) }
        if (enabled) checkForUpdates()
    }

    fun checkForUpdates(manual: Boolean = false) {
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
            favorites.map { it.withConventionalFormula() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveFavorites(list: List<FavoriteCompound>) {
        prefs.edit().putString("favorites", gson.toJson(list)).apply()
    }

    private fun FavoriteCompound.withConventionalFormula(): FavoriteCompound {
        val conventional = formatConventionalFormula(formula)
        return if (conventional == formula) this else copy(formula = conventional)
    }

    fun saveCurrentCompoundOffline() {
        val startState = _uiState.value
        val cid = startState.cid ?: return
        if (!startState.hasResult || _isSavingOffline.value) return

        _isSavingOffline.value = true
        _offlineDownloadProgress.value = 0f
        viewModelScope.launch {
            try {
                val snapshot = buildOfflineSnapshot(startState) { progress ->
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
            isLoadingSynonyms = false
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

    private fun loadDownloads(): List<DownloadedCompound> {
        val json = prefs.getString("downloads", null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<DownloadedCompound>>() {}.type
            val restored = gson.fromJson<List<DownloadedCompound>>(json, type) ?: emptyList()
            restored.map { it.withResolvedOfflineMetadata() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun DownloadedCompound.withResolvedOfflineMetadata(): DownloadedCompound {
        val normalizedState = state.withConventionalFormula()
        val normalizedFormula = formatConventionalFormula(formula.ifBlank { normalizedState.formula })
        val normalized = copy(
            formula = normalizedFormula,
            state = normalizedState
        )
        val restoredMetadata: OfflineDownloadMetadata? = runCatching { offlineMetadata }.getOrNull()
        return if (restoredMetadata != null) normalized else normalized.copy(
            offlineMetadata = buildOfflineDownloadMetadata(normalizedState, savedAt)
        )
    }

    private fun ChemUiState.withConventionalFormula(): ChemUiState {
        val conventional = formatConventionalFormula(formula)
        if (conventional == formula) return this
        return copy(
            formula = conventional,
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
        if (!_autoSuggest.value || q.length < 2) {
            _uiState.update { it.copy(suggestions = emptyList()) }
            return
        }
        autocompleteJob = viewModelScope.launch {
            delay(300)
            try {
                val res = ApiClient.pubChemAutocomplete.autocomplete(q)
                val suggestions = res.dictionaryTerms?.compound ?: emptyList()
                DebugLog.d("ChemSearch", "Autocomplete \"$q\" → ${suggestions.size} results")
                _uiState.update { it.copy(suggestions = suggestions) }
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
            _cacheSizeBytes.value = withContext(Dispatchers.IO) { computeCacheSizeBlocking() }
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
                (file.length() / 1024L) to computeCacheSizeBlocking()
            }
            _cacheSizeBytes.value = cacheBytes
            DebugLog.d("ChemSearch", "Cached compound CID $cid (${fileSizeKb}KB)")
        } catch (e: Exception) {
            DebugLog.e("ChemSearch", "Cache write failed: ${e.message}")
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
                    hasResult = false,
                    isCached = false,
                    isOfflineDownload = false,
                    offline2dPngBase64 = null,
                    sdfData = null,
                    sdfSource = null,
                    sdfMessage = null,
                    ghsData = null,
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
                        activeTab = MolTab.TWO_D,
                        isLoadingSynonyms = false
                    )
                }
                saveToHistory(cachedByName.name)
                cachedByName.cid?.let { loadSynonymsForCurrentCompound(it) }
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
                            activeTab = MolTab.TWO_D,
                            isLoadingSynonyms = false
                        )
                    }
                    backfillStructureMetadataIfMissing(cached, cid)
                    saveToHistory(cached.name)
                    loadSynonymsForCurrentCompound(cid)
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
                val formula = formatConventionalFormula(props.molecularFormula ?: "")

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
                    activeTab = MolTab.TWO_D,
                    aiProvider = _uiState.value.aiProvider,
                    isCached = false,
                    isLoadingSynonyms = true
                )
                _uiState.update { newState }
                writeCache(newState)
                loadSynonymsForCurrentCompound(cid, force = true)

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
                DebugLog.e("ChemSearch", "Search failed for \"$q\": ${e::class.simpleName} — ${e.message}")
                _uiState.update {
                    it.copy(isLoading = false, error = msg)
                }
            }
        }
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
        _uiState.update { it.copy(activeTab = tab) }
        if (tab == MolTab.THREE_D && _uiState.value.sdfData == null) {
            fetchSdfData()
        }
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
        onProgress: (Float) -> Unit = {}
    ): ChemUiState {
        val cid = startState.cid ?: return startState
        val latest = _uiState.value.takeIf { it.cid == cid } ?: startState
        onProgress(0.08f)
        val synonyms = latest.synonyms.takeIf { it.size >= 10 } ?: fetchSynonyms(cid)
        onProgress(0.22f)
        val casRegex = Regex("""^\d{1,7}-\d{2}-\d$""")
        val pubDescription = latest.pubDescription ?: fetchPubChemDescription(cid)
        onProgress(0.36f)
        val wikiDescription = latest.wikiDescription ?: fetchWikiDescriptionBlocking(latest.name)
        onProgress(0.50f)
        val ghsData = latest.ghsData ?: fetchGhsDataBlocking(cid)
        onProgress(0.64f)
        val sdfResult = fetchSdfForOffline(latest)
        onProgress(0.82f)
        val pngBase64 = latest.offline2dPngBase64 ?: fetch2dStructurePngBase64(cid)
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
            sdfData = sdfResult?.sdf ?: latest.sdfData,
            sdfSource = sdfResult?.source ?: latest.sdfSource,
            sdfMessage = sdfResult?.message ?: latest.sdfMessage,
            offline2dPngBase64 = pngBase64,
            isOfflineDownload = true,
            isLoadingDesc = false,
            isLoadingSdf = false,
            isLoadingSafety = false,
            isLoadingSynonyms = false
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
                suggestions = emptyList()
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
            .filter { it.query.isNotBlank() }
            .distinctBy { it.query.lowercase() }

        return loadHistory().map { query ->
            RecentSearch(query = query, lastSearchedAt = 0L, pinned = false)
        }
    }

    private fun saveRecentSearches(searches: List<RecentSearch>) {
        val cleaned = searches
            .filter { it.query.isNotBlank() }
            .distinctBy { it.query.lowercase() }
        prefs.edit()
            .putString(PREF_RECENT_SEARCHES, gson.toJson(cleaned))
            .putString(PREF_HISTORY, cleaned.joinToString("||") { it.query })
            .apply()
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
        _uiState.update { it.copy(isomerQuery = q, isomers = emptyList(), isomerError = null) }
    }

    fun searchIsomers() {
        val formula = _uiState.value.isomerQuery.trim()
        if (formula.isBlank()) return

        viewModelScope.launch {
            DebugLog.d("ChemSearch", "Isomer search: \"$formula\"")
            _uiState.update {
                it.copy(isLoadingIsomers = true, isomers = emptyList(), isomerError = null)
            }
            try {
                val cidResponse = ApiClient.pubChem.getIsomerCids(formula)
                val cids = cidResponse.identifierList?.cid?.take(20)
                    ?: throw NoSuchElementException("No isomers found for $formula.")

                DebugLog.d("ChemSearch", "Isomers: ${cids.size} CIDs for $formula")

                val cidString = cids.joinToString(",")
                val titleMap: Map<Long, String> = runCatching {
                    ApiClient.pubChem.getTitles(cidString)
                        .propertyTable?.properties
                        ?.mapNotNull { p -> p.cid?.let { it to (p.title ?: "Unnamed Compound") } }
                        ?.toMap()
                }.getOrNull() ?: emptyMap()

                val isomers = cids.map { cid ->
                    IsomerItem(cid = cid, title = titleMap[cid] ?: "CID $cid")
                }
                DebugLog.d("ChemSearch", "Isomers loaded: ${isomers.size} items")
                _uiState.update { it.copy(isLoadingIsomers = false, isomers = isomers) }

            } catch (e: Exception) {
                val msg = when (e) {
                    is java.io.IOException -> "Network error. Check your connection."
                    is NoSuchElementException -> e.message ?: "No isomers found."
                    else -> "No isomers found for \"$formula\". Check the formula and try again."
                }
                DebugLog.e("ChemSearch", "Isomer search failed: ${e.message}")
                _uiState.update { it.copy(isLoadingIsomers = false, isomerError = msg) }
            }
        }
    }

    fun searchByCid(cid: Long) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            DebugLog.d("ChemSearch", "Search by CID: $cid")
            _uiState.update {
                it.copy(
                    isLoading = true, error = null, hasResult = false,
                    sdfData = null, sdfSource = null, sdfMessage = null, ghsData = null, isLoadingSafety = false,
                    isomerMode = false, isomers = emptyList(),
                    isCached = false,
                    isOfflineDownload = false,
                    offline2dPngBase64 = null,
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
                        sdfData = null, sdfSource = null, sdfMessage = null, activeTab = MolTab.TWO_D,
                        isomerMode = false, isomers = emptyList(),
                        isCached = true,
                        isOfflineDownload = false,
                        offline2dPngBase64 = null,
                        isLoadingSynonyms = false
                    )
                }
                backfillStructureMetadataIfMissing(cached, cid)
                _query.value = cached.name
                saveToHistory(cached.name)
                loadSynonymsForCurrentCompound(cid)
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
                val formula = formatConventionalFormula(props.molecularFormula ?: "")

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
                    activeTab = MolTab.TWO_D,
                    aiProvider = _uiState.value.aiProvider,
                    isomerMode = false,
                    isomers = emptyList(),
                    isCached = false,
                    isLoadingSynonyms = true
                )
                _uiState.update { newState }
                _query.value = compoundName
                writeCache(newState)
                loadSynonymsForCurrentCompound(cid, force = true)

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
        if (!_updateNotificationsEnabled.value) return
        val lastNotified = prefs.getString(PREF_UPDATE_LAST_NOTIFIED, null)
        if (latestTag.equals(lastNotified, ignoreCase = true)) return
        sendUpdateNotification(latestTag, downloadUrl ?: releaseUrl)
        prefs.edit().putString(PREF_UPDATE_LAST_NOTIFIED, latestTag).apply()
    }

    fun sendDebugUpdateNotification() {
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

package com.furthersecrets.chemsearch

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.furthersecrets.chemsearch.R
import com.furthersecrets.chemsearch.data.*
import com.furthersecrets.chemsearch.data.OfflineTestMode
import com.furthersecrets.chemsearch.data.local.ChemSearchDatabase
import com.furthersecrets.chemsearch.data.local.LibraryRepository
import com.furthersecrets.chemsearch.data.local.OfflineDownloadRepository
import com.furthersecrets.chemsearch.data.settings.SettingsRepository
import com.furthersecrets.chemsearch.data.updates.UpdateRepository
import com.furthersecrets.chemsearch.settings.SettingsManager
import com.furthersecrets.chemsearch.updates.UpdateManager
import com.furthersecrets.chemsearch.ui.DebugLog
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale
import kotlin.random.Random

internal const val PubChemRandomCompoundUpperBound = 123_880_397L

internal fun randomPubChemCid(
    random: Random = Random.Default,
    upperBound: Long = PubChemRandomCompoundUpperBound
): Long = random.nextLong(upperBound) + 1L

/**
 * UI-facing facade for the app's feature state. Delegates persistence and
 * domain work to focused collaborators:
 *  - [SettingsManager] / [SettingsRepository] — settings and AI configuration
 *  - [LibraryRepository] — favorites, downloads, backups
 *  - [RecentSearchesRepository] — search history
 *  - [CompoundCacheRepository] / [SearchRepository] / [CompoundDataRepository] — search data
 *  - [UpdateManager] / [UpdateRepository] — app updates
 */
class ChemViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext: Application = application
    private val prefs = application.getSharedPreferences("chemsearch_prefs", Context.MODE_PRIVATE)

    private fun localizedAppContext(): Context =
        AppLocalization.localizedContext(appContext, prefs)

    private fun localizedString(resId: Int): String =
        AppLocalization.string(appContext, prefs, resId)

    private fun localizedString(resId: Int, vararg formatArgs: Any): String =
        AppLocalization.string(appContext, prefs, resId, *formatArgs)

    private val gson = Gson()

    // ---- Collaborators ----
    private val settingsRepository = SettingsRepository(application, prefs, scope = viewModelScope)
    private val settingsManager = SettingsManager(prefs, settingsRepository, viewModelScope)
    private val offlineDownloadRepository = OfflineDownloadRepository(
        dao = ChemSearchDatabase.getInstance(appContext).downloadedCompoundDao(),
        prefs = prefs,
        gson = gson
    )
    private val libraryRepository = LibraryRepository(prefs, gson, offlineDownloadRepository, viewModelScope)
    private val recentSearchesRepository = RecentSearchesRepository(prefs, gson)
    private val cacheRepository = CompoundCacheRepository(appContext, prefs, gson)
    private val dataRepository = CompoundDataRepository(appContext, prefs)

    init {
        // Offline Test Mode reads its persisted config before any repository
        // call can be intercepted.
        OfflineTestMode.configure(prefs)
    }

    private val offlineIntercept = OfflineTestIntercept(dataRepository)
    private val searchRepository = SearchRepository(
        context = appContext,
        prefs = prefs,
        gson = gson
    )
    private val updateRepository = UpdateRepository(appContext)
    private val updateManager = UpdateManager(application, prefs, settingsRepository, updateRepository, viewModelScope)

    // ---- Shared state from collaborators ----
    val favorites: StateFlow<List<FavoriteCompound>> = libraryRepository.favorites
    val downloads: StateFlow<List<DownloadedCompound>> = libraryRepository.downloads

    val isDarkTheme: StateFlow<Boolean> = settingsManager.isDarkTheme
    val colorScheme: StateFlow<AppColorScheme> = settingsManager.colorScheme
    val autoSuggest: StateFlow<Boolean> = settingsManager.autoSuggest
    val compactMode: StateFlow<Boolean> = settingsManager.compactMode
    val oledDarkTheme: StateFlow<Boolean> = settingsManager.oledDarkTheme
    val defaultDescSource: StateFlow<DescSource> = settingsManager.defaultDescSource
    val defaultStructureView: StateFlow<DefaultStructureView> = settingsManager.defaultStructureView
    val offlineDownloadQuality: StateFlow<OfflineDownloadQuality> = settingsManager.offlineDownloadQuality
    val formulaDisplayStyle: StateFlow<FormulaDisplayStyle> = settingsManager.formulaDisplayStyle
    val cacheSizeLimit: StateFlow<CacheSizeLimit> = settingsManager.cacheSizeLimit
    val cacheRetention: StateFlow<CacheRetention> = settingsManager.cacheRetention
    val reduceMotion: StateFlow<Boolean> = settingsManager.reduceMotion
    val highContrastOutlines: StateFlow<Boolean> = settingsManager.highContrastOutlines
    val cardsEnabled: StateFlow<Boolean> = settingsManager.cardsEnabled
    val temperatureUnit: StateFlow<TemperatureUnit> = settingsManager.temperatureUnit
    val appLanguage: StateFlow<AppLanguage> = settingsManager.appLanguage
    val cacheDirPath: StateFlow<String> = settingsManager.cacheDirPath
    val showWelcome: StateFlow<Boolean> = settingsManager.showWelcome
    val aiKeyStatus: StateFlow<Map<AiProvider, Boolean>> = settingsManager.aiKeyStatus
    val aiModelCatalogs: StateFlow<Map<AiProvider, AiModelCatalog>> = settingsManager.aiModelCatalogs
    val updateNotificationsEnabled: StateFlow<Boolean> = updateManager.updateNotificationsEnabled
    val updateStatus: StateFlow<UpdateStatus> = updateManager.updateStatus

    private val _recentSearches = MutableStateFlow(recentSearchesRepository.load())
    val recentSearches: StateFlow<List<RecentSearch>> = _recentSearches.asStateFlow()

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

    private val _cacheSizeBytes = MutableStateFlow(0L)
    val cacheSizeBytes: StateFlow<Long> = _cacheSizeBytes.asStateFlow()

    private val _hasGeminiKey = MutableStateFlow(settingsRepository.hasAiKey(AiProvider.GEMINI))
    val hasGeminiKey: StateFlow<Boolean> = _hasGeminiKey.asStateFlow()

    private val _hasGroqKey = MutableStateFlow(settingsRepository.hasAiKey(AiProvider.GROQ))
    val hasGroqKey: StateFlow<Boolean> = _hasGroqKey.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private var searchJob: Job? = null
    private var autocompleteJob: Job? = null

    init {
        DebugLog.verbose = prefs.getBoolean("debug_verbose", false)
        _uiState.update {
            it.copy(history = recentQueries(), aiProvider = settingsRepository.savedAiProvider())
        }
        settingsManager.startCollecting()
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                offlineDownloadRepository.migrateLegacyDownloadsIfNeeded()
            }
        }
        viewModelScope.launch {
            combine(
                _uiState.map { it.cid }.distinctUntilChanged(),
                libraryRepository.favorites
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
                libraryRepository.downloads
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

    // =====================================================================
    // Welcome / updates
    // =====================================================================

    fun isAiProviderSet(): Boolean = settingsRepository.isAiProviderSet()

    fun skipWelcome() = settingsManager.skipWelcome()

    fun showWelcomeAgain() = settingsManager.showWelcomeAgain()

    fun setUpdateNotificationsEnabled(enabled: Boolean) =
        updateManager.setNotificationsEnabled(enabled)

    fun checkForUpdates(manual: Boolean = false) = updateManager.checkForUpdates(manual)

    fun downloadUpdateApk() = updateManager.downloadUpdateApk()

    fun sendDebugUpdateNotification() = updateManager.sendDebugUpdateNotification()

    // =====================================================================
    // Favorites / library
    // =====================================================================

    fun toggleFavorite() {
        val state = _uiState.value
        val cid = state.cid ?: return
        val nextFavorite = libraryRepository.toggleFavorite(
            cid = cid,
            compound = FavoriteCompound(
                cid = cid,
                name = state.name,
                formula = state.formula,
                molecularWeight = state.weight,
                iupacName = state.iupacName
            )
        )
        _isFavorite.value = nextFavorite
    }

    fun deleteFavorite(cid: Long) {
        libraryRepository.deleteFavorite(cid)
        if (_uiState.value.cid == cid) _isFavorite.value = false
    }

    fun restoreFavorite(favorite: FavoriteCompound) {
        libraryRepository.restoreFavorite(favorite)
        if (_uiState.value.cid == favorite.cid) _isFavorite.value = true
    }

    fun moveFavorite(fromIndex: Int, toIndex: Int) = libraryRepository.moveFavorite(fromIndex, toIndex)

    // =====================================================================
    // Offline downloads
    // =====================================================================

    fun saveCurrentCompoundOffline() {
        val startState = _uiState.value
        val cid = startState.cid ?: return
        if (!startState.hasResult || _isSavingOffline.value) return

        _isSavingOffline.value = true
        _offlineDownloadProgress.value = 0f
        viewModelScope.launch {
            try {
                val snapshot = buildOfflineSnapshot(startState, settingsManager.offlineDownloadQuality.value) { progress ->
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
                libraryRepository.upsertDownload(item)
                _offlineDownloadProgress.value = 1f
                _isDownloaded.value = true
                _uiState.update { current ->
                    if (current.cid == cid) snapshot.copy(history = current.history) else current
                }
                DebugLog.d("ChemSearch", "Downloaded offline compound: ${snapshot.name} (CID $cid)")
            } catch (e: Exception) {
                DebugLog.e("ChemSearch", "Offline download failed for CID $cid: ${e.message}")
                _uiState.update {
                    it.copy(
                        error = localizedString(
                            R.string.ui_error_offline_download_failed,
                            e.message ?: localizedString(R.string.ui_unknown_error)
                        )
                    )
                }
            } finally {
                delay(250)
                _isSavingOffline.value = false
                _offlineDownloadProgress.value = null
            }
        }
    }

    fun openDownloadedCompound(cid: Long) {
        val downloaded = libraryRepository.findDownload(cid) ?: return
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
        libraryRepository.deleteDownload(cid)
        if (_uiState.value.cid == cid) _isDownloaded.value = false
    }

    fun restoreDownload(download: DownloadedCompound) {
        libraryRepository.restoreDownload(download)
        if (_uiState.value.cid == download.cid) _isDownloaded.value = true
    }

    // =====================================================================
    // Library backup
    // =====================================================================

    fun buildLibraryBackupJson(): String = libraryRepository.buildBackupJson()

    fun buildLibraryCsv(): String = libraryRepository.buildLibraryCsv()

    fun importLibraryBackup(
        rawJson: String,
        replace: Boolean,
        onResult: (Result<LibraryImportResult>) -> Unit
    ) {
        viewModelScope.launch {
            val result =        libraryRepository.importBackup(rawJson, replace)
                .fold(
                    onSuccess = { Result.success(it) },
                    onFailure = { e ->
                        Result.failure(
                            when {
                                e is IllegalArgumentException && e.message?.contains("Not a ChemSearch") == true ->
                                    IllegalArgumentException(localizedString(R.string.ui_error_not_chemsearch_backup))
                                e is IllegalArgumentException ->
                                    IllegalArgumentException(localizedString(R.string.ui_error_invalid_library_backup))
                                else -> e
                            }
                        )
                    }
                )
            onResult(result)
        }
    }

    // =====================================================================
    // Settings
    // =====================================================================

    fun toggleTheme() = settingsManager.toggleTheme()

    fun setColorScheme(scheme: AppColorScheme) = settingsManager.setColorScheme(scheme)

    fun toggleAutoSuggest() {
        val next = settingsManager.toggleAutoSuggest()
        if (!next) _uiState.update { it.copy(suggestions = emptyList()) }
    }

    fun setCompactMode(enabled: Boolean) = settingsManager.setCompactMode(enabled)

    fun setOledDarkTheme(enabled: Boolean) = settingsManager.setOledDarkTheme(enabled)

    fun setDefaultDescSource(source: DescSource) = settingsManager.setDefaultDescSource(source)

    fun setDefaultStructureView(view: DefaultStructureView) = settingsManager.setDefaultStructureView(view)

    fun setOfflineDownloadQuality(quality: OfflineDownloadQuality) =
        settingsManager.setOfflineDownloadQuality(quality)

    fun setFormulaDisplayStyle(style: FormulaDisplayStyle) = settingsManager.setFormulaDisplayStyle(style)

    fun setCacheSizeLimit(limit: CacheSizeLimit) {
        settingsManager.setCacheSizeLimit(limit)
        refreshCacheSizeAsync()
    }

    fun setCacheRetention(retention: CacheRetention) {
        settingsManager.setCacheRetention(retention)
        refreshCacheSizeAsync()
    }

    fun setReduceMotion(enabled: Boolean) = settingsManager.setReduceMotion(enabled)

    fun setTemperatureUnit(unit: TemperatureUnit) = settingsManager.setTemperatureUnit(unit)

    fun setHighContrastOutlines(enabled: Boolean) = settingsManager.setHighContrastOutlines(enabled)

    fun setCardsEnabled(enabled: Boolean) = settingsManager.setCardsEnabled(enabled)

    fun setAppLanguage(language: AppLanguage) = settingsManager.setAppLanguage(language)

    fun setAiProvider(provider: AiProvider) {
        settingsManager.setAiProvider(provider)
        _uiState.update { it.copy(aiProvider = provider) }
        if (_uiState.value.descSource == DescSource.AI) {
            fetchAiDescription()
        }
    }

    fun reloadSettingsFromPreferences() {
        settingsManager.reloadFromPrefs()
        updateManager.reloadFromPrefs()
        refreshCacheSizeAsync()
        refreshAiKeyStatus()
        _recentSearches.value = recentSearchesRepository.load()

        val provider = settingsRepository.savedAiProvider()
        val source = getSavedDescSource()
        _uiState.update { current ->
            current.copy(
                history = recentQueries(),
                aiProvider = provider,
                descSource = source,
                suggestions = if (settingsManager.autoSuggest.value) current.suggestions else emptyList()
            )
        }
        DebugLog.d("ChemSearch", "Settings reloaded from SharedPreferences")
    }

    // =====================================================================
    // AI keys & models
    // =====================================================================

    fun getSelectedAiModel(provider: AiProvider): String =
        settingsManager.aiModelCatalogs.value[provider]?.selectedModel?.takeIf { it.isNotBlank() }
            ?: settingsRepository.savedAiModel(provider)
            ?: provider.modelName

    fun setAiModel(provider: AiProvider, model: String) {
        val cleanModel = model.trim()
        if (cleanModel.isBlank()) return
        settingsRepository.saveAiModel(provider, cleanModel)
        settingsManager.updateAiModelCatalog(provider) { current ->
            current.copy(
                models = (listOf(cleanModel) + current.models + provider.defaultModels).distinct(),
                selectedModel = cleanModel,
                error = null
            )
        }
        DebugLog.d("ChemSearch", "${provider.shortName} model → $cleanModel")
        if (_uiState.value.descSource == DescSource.AI && _uiState.value.aiProvider == provider) {
            _uiState.update { it.copy(aiDescription = null, aiDescriptionBasis = emptyList()) }
            fetchAiDescription()
        }
    }

    fun refreshAiModels(provider: AiProvider) {
        val key = getAiKey(provider) ?: run {
            settingsManager.updateAiModelCatalog(provider) { current ->
                current.copy(error = localizedString(R.string.ui_error_add_api_key))
            }
            return
        }
        settingsManager.updateAiModelCatalog(provider) { current ->
            current.copy(isLoading = true, error = null)
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
                                AiProvider.GEMINI -> error(localizedString(R.string.ui_error_gemini_separate_api))
                            }
                            api.listModels("Bearer $key").data?.mapNotNull { it.id } ?: emptyList()
                        }
                    }.filter { it.isNotBlank() }.distinct().sorted()
                }
            }
            settingsManager.updateAiModelCatalog(provider) { current ->
                result.fold(
                    onSuccess = { fetched ->
                        val selected = current.selectedModel.ifBlank { provider.modelName }
                        val models = (listOf(selected) + fetched + provider.defaultModels).distinct()
                        current.copy(
                            models = models,
                            selectedModel = selected,
                            isLoading = false,
                            error = if (fetched.isEmpty()) localizedString(R.string.ui_error_no_models_returned) else null
                        )
                    },
                    onFailure = { e ->
                        current.copy(
                            isLoading = false,
                            error = e.message ?: localizedString(R.string.ui_error_could_not_refresh_models)
                        )
                    }
                )
            }
        }
    }

    fun getAiKey(provider: AiProvider): String? = settingsRepository.getAiKey(provider)

    fun hasAiKey(provider: AiProvider): Boolean = settingsRepository.hasAiKey(provider)

    fun saveAiKey(provider: AiProvider, key: String) {
        settingsRepository.saveAiKey(provider, key)
        refreshAiKeyStatus()
    }

    fun clearAiKey(provider: AiProvider) {
        settingsRepository.clearAiKey(provider)
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

    private fun loadAiKeyStatus(): Map<AiProvider, Boolean> = settingsRepository.aiKeyStatus()

    private fun refreshAiKeyStatus() {
        val status = loadAiKeyStatus()
        settingsManager.refreshAiKeyStatus()
        _hasGeminiKey.value = status[AiProvider.GEMINI] == true
        _hasGroqKey.value = status[AiProvider.GROQ] == true
    }

    private fun getSavedDescSource(): DescSource = settingsRepository.savedDescSource()

    // =====================================================================
    // Cache
    // =====================================================================

    fun getCacheSizeBytes(): Long = cacheRepository.computeSizeBlocking()

    private fun refreshCacheSizeAsync() {
        viewModelScope.launch {
            _cacheSizeBytes.value = withContext(Dispatchers.IO) {
                cacheRepository.enforcePolicyBlocking(
                    settingsManager.cacheSizeLimit.value,
                    settingsManager.cacheRetention.value
                )
                cacheRepository.computeSizeBlocking()
            }
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { cacheRepository.clearAll() }
            _cacheSizeBytes.value = 0L
            DebugLog.d("ChemSearch", "Compound cache cleared")
        }
    }

    fun setCacheDir(path: String): Boolean {
        val cleanPath = path.trim()
        if (cleanPath.isBlank()) {
            cacheRepository.setDirPath("")
            settingsManager.setCacheDirPath("")
            settingsRepository.setCacheDir("")
            refreshCacheSizeAsync()
            DebugLog.d("ChemSearch", "Cache dir reset to default")
            return true
        }

        val canUseDirectory = cacheRepository.canUseDirectory(cleanPath)
        if (!canUseDirectory) {
            DebugLog.e("ChemSearch", "Rejected cache dir: $cleanPath")
            return false
        }

        cacheRepository.setDirPath(cleanPath)
        settingsManager.setCacheDirPath(cleanPath)
        settingsRepository.setCacheDir(cleanPath)
        refreshCacheSizeAsync()
        DebugLog.d("ChemSearch", "Cache dir set to: $cleanPath")
        return true
    }

    fun getCacheDir(): String = cacheRepository.cachedDirPath()

    private suspend fun readCache(cid: Long): ChemUiState? = cacheRepository.read(cid)

    private suspend fun findCacheByName(query: String): ChemUiState? = cacheRepository.findByName(query)

    private suspend fun writeCache(state: ChemUiState) {
        val cid = state.cid ?: return
        val fileLength = cacheRepository.write(
            state,
            settingsManager.cacheSizeLimit.value,
            settingsManager.cacheRetention.value
        )
        if (fileLength != null) {
            _cacheSizeBytes.value = cacheRepository.computeSizeBlocking()
            DebugLog.d("ChemSearch", "Cached compound CID $cid (${fileLength / 1024L}KB)")
        }
    }

    // =====================================================================
    // Query & autocomplete
    // =====================================================================

    fun onQueryChange(q: String) {
        _query.value = q
        autocompleteJob?.cancel()
        _uiState.update {
            it.copy(
                failedSearchQuery = null,
                searchCorrectionSuggestions = emptyList()
            )
        }
        if (!settingsManager.autoSuggest.value || q.length < 2) {
            _uiState.update {
                it.copy(
                    suggestions = emptyList(),
                )
            }
            return
        }
        autocompleteJob = viewModelScope.launch {
            delay(300)
            if (OfflineTestMode.enabled) {
                // Dummy autocomplete: stable suggestions derived from the query.
                val stem = q.take(5)
                _uiState.update {
                    it.copy(
                        suggestions = listOf(
                            q, "${stem}ol", "${stem}one", "test-$q", "demo-$q"
                        ).distinct().take(5)
                    )
                }
                return@launch
            }
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

    // =====================================================================
    // Search
    // =====================================================================

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
                    errorKind = null,
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

            if (OfflineTestMode.enabled) {
                emitDummySearchResult(q)
                return@launch
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
                    ?: throw NoSuchElementException(localizedString(R.string.ui_error_chemical_not_found))
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
                val structureCounts = dataRepository.extractStructureCounts(recordDeferred.await())

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
                    empiricalFormula = getEmpiricalFormulaFor(formula),
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
                    elementalData = calcElementalDataFor(formula),
                    history = recentQueries(),
                    activeTab = defaultMolTab(),
                    aiProvider = _uiState.value.aiProvider,
                    isCached = false,
                    isLoadingSynonyms = true,
                    advancedProperties = buildAdvancedProperties(props, localizedAdvancedPropertyLabels(localizedAppContext()))
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
                // NoSuchElementException carries an already-localized message
                // (e.g. "chemical not found"); everything else is decoded from
                // the request failure (HTTP status / PubChem Fault body).
                val presentation = if (e is NoSuchElementException) {
                    com.furthersecrets.chemsearch.data.SearchErrorPresentation(
                        messageRes = R.string.ui_error_search_not_found_s,
                        args = listOf(q.take(80)),
                        kind = com.furthersecrets.chemsearch.data.SearchErrorKind.NOT_FOUND
                    )
                } else {
                    com.furthersecrets.chemsearch.data.SearchErrorResolver.fromThrowable(e, q)
                }
                val msg = localizedString(presentation.messageRes, *presentation.args.toTypedArray())
                val corrections = if (presentation.kind == com.furthersecrets.chemsearch.data.SearchErrorKind.NETWORK) {
                    emptyList()
                } else if (OfflineTestMode.enabled) {
                    runCatching { offlineIntercept.fetchSearchCorrectionSuggestions(q) }.getOrDefault(emptyList())
                } else {
                    dataRepository.fetchSearchCorrectionSuggestions(q)
                }
                DebugLog.e("ChemSearch", "Search failed for \"$q\": ${e::class.simpleName} — ${e.message}")
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = msg,
                        errorKind = presentation.kind,
                        failedSearchQuery = q,
                        searchCorrectionSuggestions = corrections
                    )
                }
            }
        }
    }

    /**
     * Offline Test Mode: builds a full dummy result for a name query, then
     * runs the exact same follow-up pipeline (synonyms, extras, safety,
     * description fetch) so every downstream surface can be exercised.
     */
    private suspend fun emitDummySearchResult(q: String) {
        try {
            OfflineTestMode.simulateNetworkProbe(q)
        } catch (e: Exception) {
            val presentation = SearchErrorResolver.fromThrowable(e, q)
            val msg = localizedString(presentation.messageRes, *presentation.args.toTypedArray())
            _uiState.update {
                it.copy(
                    isLoading = false,
                    error = msg,
                    errorKind = presentation.kind,
                    failedSearchQuery = q,
                    searchCorrectionSuggestions = emptyList()
                )
            }
            return
        }
        val cid = OfflineTestMode.cidFor(q)
        val rawFormula = OfflineTestMode.formulaFor(cid)
        val formula = formatConventionalFormula(rawFormula)
        saveToHistory(q.replaceFirstChar { c -> c.uppercase() })
        val newState = ChemUiState(
            isLoading = false,
            hasResult = true,
            cid = cid,
            name = q.replaceFirstChar { c -> c.uppercase() },
            formula = formula,
            rawFormula = rawFormula,
            empiricalFormula = getEmpiricalFormulaFor(formula),
            weight = OfflineTestMode.weightFor(cid),
            charge = 0,
            atomNumber = 10 + (cid % 30).toInt(),
            bondNumber = 9 + (cid % 28).toInt(),
            covalentUnitCount = (cid % 20).toInt() + 1,
            iupacName = OfflineTestMode.iupacFor(cid),
            smiles = OfflineTestMode.smilesFor(cid),
            connectivitySmiles = OfflineTestMode.smilesFor(cid),
            inchiKey = "TEST$cid-KEYOFFLINE",
            inchi = "InChI=1S/test.$cid",
            synonyms = emptyList(),
            casNumber = null,
            pubDescription = OfflineTestMode.descriptionFor(cid, q),
            wikiDescription = null,
            aiDescription = null,
            descSource = getSavedDescSource(),
            elementalData = calcElementalDataFor(formula),
            history = recentQueries(),
            activeTab = defaultMolTab(),
            aiProvider = _uiState.value.aiProvider,
            isCached = false,
            isLoadingSynonyms = true,
            advancedProperties = buildAdvancedProperties(
                offlineIntercept.dummyProperty(cid),
                localizedAdvancedPropertyLabels(localizedAppContext())
            )
        )
        _uiState.update { newState }
        loadSynonymsForCurrentCompound(cid, force = true)
        loadPubChemExtrasForCurrentCompound(cid, force = true)
        fetchSafetyData()
    }

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
                it.copy(filters = normalized, error = localizedString(R.string.ui_error_enter_query))
            }
            return
        }

        viewModelScope.launch {
            _advancedSearchState.update {
                it.copy(isLoading = true, filters = normalized, results = emptyList(), error = null)
            }
            try {
                val cids = (if (OfflineTestMode.enabled) {
                    offlineIntercept.resolveAdvancedSearchCids(normalized)
                } else {
                    dataRepository.resolveAdvancedSearchCids(normalized)
                })
                    .distinct()
                    .take(normalized.maxRecords)
                if (cids.isEmpty()) throw NoSuchElementException(localizedString(R.string.ui_error_no_candidates))

                val properties = if (OfflineTestMode.enabled) {
                    cids.map { offlineIntercept.dummyProperty(it) }
                } else {
                    val cidString = cids.joinToString(",")
                    ApiClient.pubChem.getAdvancedSearchProperties(cidString)
                        .propertyTable?.properties
                        .orEmpty()
                        .filter { it.cid != null }
                }

                val decorated = properties.map { property ->
                    val cid = property.cid ?: return@map null
                    val hasThreeD = if (normalized.requireThreeD) {
                        if (OfflineTestMode.enabled) cid % 2 == 0L else dataRepository.hasPubChem3d(cid)
                    } else null
                    val hasGhs = if (normalized.requireGhs) {
                        if (OfflineTestMode.enabled) cid % 3 != 0L else dataRepository.fetchGhsData(cid) != null
                    } else null
                    if (!advancedSearchMatchesFilters(property, normalized, hasThreeD, hasGhs)) return@map null
                    AdvancedSearchResultItem(
                        cid = cid,
                        title = property.title ?: property.iupacName ?: "CID $cid",
                        formula = property.molecularFormula.orEmpty(),
                        molecularWeight = property.molecularWeight.orEmpty(),
                        charge = property.charge,
                        hasThreeD = hasThreeD,
                        hasGhs = hasGhs,
                        iupacName = property.iupacName.orEmpty()
                    )
                }.filterNotNull()

                if (decorated.isEmpty()) throw NoSuchElementException(localizedString(R.string.ui_error_no_compounds_matched_filters))
                _advancedSearchState.update {
                    it.copy(isLoading = false, results = decorated, error = null)
                }
            } catch (e: Exception) {
                val msg = when (e) {
                    is IOException -> localizedString(R.string.ui_error_network)
                    is NoSuchElementException -> e.message ?: localizedString(R.string.ui_error_no_advanced_results)
                    else -> localizedString(R.string.ui_error_advanced_search_failed)
                }
                DebugLog.e("ChemSearch", "Advanced search failed: ${e.message}")
                _advancedSearchState.update { it.copy(isLoading = false, error = msg) }
            }
        }
    }

    // =====================================================================
    // Descriptions
    // =====================================================================

    fun fetchWikiDescription() {
        val name = _uiState.value.name.ifBlank { return }
        _uiState.update { it.copy(isLoadingDesc = true) }
        DebugLog.d("ChemSearch", "Fetching Wikipedia description for \"$name\"")
        viewModelScope.launch {
            val desc = if (OfflineTestMode.enabled) {
                try {
                    OfflineTestMode.simulateNetworkProbe("wiki/$name")
                    "Simulated Wikipedia extract for \"$name\". Offline Test Mode generated this " +
                        "text so the description card, source switcher, and copy actions can be " +
                        "tested without reaching Wikipedia."
                } catch (e: Exception) {
                    null
                }
            } else {
                dataRepository.fetchWikiDescription(name)
            }
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
        searchRepository.loadCachedAiDescription(prompt)?.let { cached ->
            _uiState.update { it.copy(isLoadingDesc = false, aiDescription = cached, aiDescriptionBasis = prompt.basis) }
            return
        }
        _uiState.update { it.copy(isLoadingDesc = true, aiDescriptionBasis = prompt.basis) }
        DebugLog.d("ChemSearch", "Fetching ${provider.shortName} description for \"$name\"")
        viewModelScope.launch {
            val model = getSelectedAiModel(provider)
            val text = if (OfflineTestMode.enabled) {
                try {
                    OfflineTestMode.simulateNetworkProbe("ai/$name")
                    "Simulated ${provider.shortName} description for \"$name\". Generated offline so " +
                        "AI provider cards, model pickers, and response rendering can be tested " +
                        "without spending API quota."
                } catch (e: Exception) {
                    null
                }
            } else {
                searchRepository.fetchGeminiDescriptionBlocking(prompt, key, model)
            }
            DebugLog.d("ChemSearch", "${provider.shortName} response: ${text?.take(80) ?: "empty"}")
            text?.takeIf { it.isNotBlank() }?.let { searchRepository.saveCachedAiDescription(prompt, it) }
            _uiState.update {
                it.copy(
                    isLoadingDesc = false,
                    aiDescription = text ?: "${provider.shortName} returned empty response.",
                    aiDescriptionBasis = prompt.basis
                )
            }
        }
    }

    private fun fetchChatDescription(name: String, provider: AiProvider) {
        val key = getAiKey(provider) ?: run {
            _uiState.update { it.copy(isLoadingDesc = false, aiDescription = "No ${provider.shortName} API key set. Add it in Settings.") }
            return
        }
        val prompt = buildAiDescriptionPrompt(_uiState.value, provider, getSelectedAiModel(provider))
        searchRepository.loadCachedAiDescription(prompt)?.let { cached ->
            _uiState.update { it.copy(isLoadingDesc = false, aiDescription = cached, aiDescriptionBasis = prompt.basis) }
            return
        }
        _uiState.update { it.copy(isLoadingDesc = true, aiDescriptionBasis = prompt.basis) }
        DebugLog.d("ChemSearch", "Fetching ${provider.shortName} description for \"$name\"")
        viewModelScope.launch {
            val model = getSelectedAiModel(provider)
            val text = if (OfflineTestMode.enabled) {
                try {
                    OfflineTestMode.simulateNetworkProbe("ai/$name")
                    "Simulated ${provider.shortName} description for \"$name\". Generated offline so " +
                        "AI provider cards, model pickers, and response rendering can be tested " +
                        "without spending API quota."
                } catch (e: Exception) {
                    null
                }
            } else {
                searchRepository.fetchChatDescriptionBlocking(provider, prompt, key, model)
            }
            DebugLog.d("ChemSearch", "${provider.shortName} response: ${text?.take(80) ?: "empty"}")
            text?.takeIf { it.isNotBlank() }?.let { searchRepository.saveCachedAiDescription(prompt, it) }
            _uiState.update {
                it.copy(
                    isLoadingDesc = false,
                    aiDescription = text ?: "${provider.shortName} returned empty response.",
                    aiDescriptionBasis = prompt.basis
                )
            }
        }
    }

    fun setDescSource(source: DescSource) {
        _uiState.update { it.copy(descSource = source) }
        val state = _uiState.value
        if (source == DescSource.WIKI && state.wikiDescription == null) fetchWikiDescription()
        if (source == DescSource.AI   && state.aiDescription == null)   fetchAiDescription()
    }

    // =====================================================================
    // Structure tab & SDF
    // =====================================================================

    fun setTab(tab: MolTab) {
        prefs.edit().putString("last_structure_view", tab.name).apply()
        _uiState.update { it.copy(activeTab = tab) }
        if (tab == MolTab.THREE_D && _uiState.value.sdfData == null) {
            fetchSdfData()
        }
    }

    private fun defaultMolTab(): MolTab =
        when (settingsManager.defaultStructureView.value) {
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
                        state.copy(sdfMessage = localizedString(R.string.ui_pubchem_3d_trying_generated_fallback))
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
                        sdfMessage = localizedString(fallback.messageRes!!, *fallback.messageArgs.toTypedArray())
                    )
                } else {
                    val message = if (candidates.isEmpty()) {
                        localizedString(R.string.ui_pubchem_3d_unavailable_no_fallback_identifier)
                    } else {
                        localizedString(R.string.ui_pubchem_3d_and_generated_unavailable)
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

    // =====================================================================
    // Offline snapshot builder
    // =====================================================================

    private suspend fun buildOfflineSnapshot(
        startState: ChemUiState,
        quality: OfflineDownloadQuality,
        onProgress: (Float) -> Unit = {}
    ): ChemUiState {
        val cid = startState.cid ?: return startState
        val latest = _uiState.value.takeIf { it.cid == cid } ?: startState
        onProgress(0.08f)
        val synonyms = when (quality) {
            OfflineDownloadQuality.COMPLETE -> latest.synonyms.takeIf { it.size >= 10 } ?: dataRepository.fetchSynonyms(cid)
            else -> latest.synonyms
        }
        onProgress(0.22f)
        val casRegex = Regex("""^\d{1,7}-\d{2}-\d$""")
        val pubDescription = if (quality == OfflineDownloadQuality.COMPLETE) {
            latest.pubDescription ?: dataRepository.fetchPubChemDescription(cid)
        } else {
            latest.pubDescription
        }
        onProgress(0.36f)
        val wikiDescription = if (quality == OfflineDownloadQuality.COMPLETE) {
            latest.wikiDescription ?: dataRepository.fetchWikiDescription(latest.name)
        } else {
            latest.wikiDescription
        }
        onProgress(0.50f)
        val ghsData = if (quality == OfflineDownloadQuality.COMPLETE) {
            latest.ghsData ?: dataRepository.fetchGhsData(cid)
        } else {
            latest.ghsData
        }
        onProgress(0.60f)
        val advancedProperties = if (quality == OfflineDownloadQuality.COMPLETE && latest.advancedProperties.isEmpty()) {
            dataRepository.fetchAdvancedProperties(cid)
        } else {
            latest.advancedProperties
        }
        val pubChemContext = if (quality == OfflineDownloadQuality.COMPLETE && latest.classificationTags.isEmpty() && latest.useEntries.isEmpty()) {
            dataRepository.fetchPubChemCompoundContext(cid)
        } else {
            PubChemCompoundContext(latest.classificationTags, latest.useEntries)
        }
        onProgress(0.68f)
        val sdfResult = if (quality != OfflineDownloadQuality.BASIC) dataRepository.fetchSdfForOffline(latest) else null
        onProgress(0.82f)
        val pngBase64 = if (quality != OfflineDownloadQuality.BASIC) {
            latest.offline2dPngBase64 ?: dataRepository.fetch2dStructurePngBase64(cid)
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

    // =====================================================================
    // Synonyms & PubChem extras
    // =====================================================================

    private fun loadSynonymsForCurrentCompound(cid: Long, force: Boolean = false) {
        val current = _uiState.value
        if (!force && current.cid == cid && current.synonyms.size >= 10) return
        viewModelScope.launch {
            _uiState.update { state ->
                if (state.cid == cid) state.copy(isLoadingSynonyms = true) else state
            }
            val synonyms = if (OfflineTestMode.enabled) {
                // Simulated probes can throw (forced failures); degrade to an
                // empty list instead of crashing the scroll-triggered loader.
                runCatching { offlineIntercept.fetchSynonyms(cid) }.getOrDefault(emptyList())
            } else {
                dataRepository.fetchSynonyms(cid)
            }
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

    private fun loadPubChemExtrasForCurrentCompound(cid: Long, force: Boolean = false) {
        val current = _uiState.value.takeIf { it.cid == cid } ?: return
        val needsProperties = current.advancedProperties.isEmpty()
        val needsContext = force || (current.classificationTags.isEmpty() && current.useEntries.isEmpty())
        if (!needsProperties && !needsContext) return

        _uiState.update { state ->
            if (state.cid == cid) state.copy(isLoadingPubChemContext = true) else state
        }

        viewModelScope.launch {
            val advancedProperties = if (needsProperties) {
                if (OfflineTestMode.enabled) {
                    runCatching { offlineIntercept.fetchAdvancedProperties(cid) }.getOrDefault(emptyList())
                } else {
                    dataRepository.fetchAdvancedProperties(cid)
                }
            } else current.advancedProperties
            val context = if (needsContext) {
                if (OfflineTestMode.enabled) {
                    runCatching { offlineIntercept.fetchPubChemCompoundContext(cid) }
                        .getOrDefault(PubChemCompoundContext(emptyList(), emptyList()))
                } else {
                    dataRepository.fetchPubChemCompoundContext(cid)
                }
            } else PubChemCompoundContext(current.classificationTags, current.useEntries)
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

    // =====================================================================
    // Result lifecycle
    // =====================================================================

    fun clearSuggestions() = _uiState.update { it.copy(suggestions = emptyList()) }
    fun clearError() = _uiState.update { it.copy(error = null, errorKind = null) }

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

    // =====================================================================
    // Recent searches
    // =====================================================================

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
        recentSearchesRepository.save(updated)
        _uiState.update { it.copy(history = recentQueries()) }
    }

    fun clearHistory() {
        recentSearchesRepository.clear()
        _recentSearches.value = emptyList()
        _uiState.update { it.copy(history = emptyList()) }
        DebugLog.d("ChemSearch", "Search history cleared")
    }

    fun removeHistoryItem(query: String) {
        val updated = _recentSearches.value.filterNot { it.query.equals(query, ignoreCase = true) }
        _recentSearches.value = updated
        recentSearchesRepository.save(updated)
        _uiState.update { it.copy(history = recentQueries()) }
        DebugLog.d("ChemSearch", "Removed recent search: $query")
    }

    fun restoreRecentSearch(search: RecentSearch) {
        val normalized = search.normalizedOrNull() ?: return
        val updated = listOf(normalized) + _recentSearches.value.filterNot {
            it.query.equals(normalized.query, ignoreCase = true)
        }
        _recentSearches.value = updated
        recentSearchesRepository.save(updated)
        _uiState.update { it.copy(history = recentQueries()) }
        DebugLog.d("ChemSearch", "Restored recent search: ${normalized.query}")
    }

    fun restoreRecentSearches(searches: List<RecentSearch>) {
        val cleaned = searches
            .mapNotNull { it.normalizedOrNull() }
            .distinctBy { it.query.lowercase() }
        _recentSearches.value = cleaned
        recentSearchesRepository.save(cleaned)
        _uiState.update { it.copy(history = recentQueries()) }
        DebugLog.d("ChemSearch", "Restored ${cleaned.size} recent searches")
    }

    fun toggleRecentPin(query: String) {
        val updated = _recentSearches.value.map { item ->
            if (item.query.equals(query, ignoreCase = true)) item.copy(pinned = !item.pinned) else item
        }
        _recentSearches.value = updated
        recentSearchesRepository.save(updated)
        _uiState.update { it.copy(history = recentQueries()) }
        DebugLog.d("ChemSearch", "Recent pin toggled: $query")
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

    // =====================================================================
    // Isomers
    // =====================================================================

    fun onIsomerQueryChange(q: String) {
        _uiState.update {
            it.copy(
                isomerQuery = q,
                isomers = emptyList(),
                isomerResultLimit = 20,
                isomerCanLoadMore = false,
                isLoadingMoreIsomers = false,
                isomerError = null,
                isomerErrorKind = null
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
                        isomerError = null,
                        isomerErrorKind = null
                    )
                }
            }
            try {
                val cids = if (OfflineTestMode.enabled) {
                    offlineIntercept.fetchFormulaCids(formula, maxRecords)
                } else {
                    dataRepository.fetchFormulaCids(formula, maxRecords)
                }.take(maxRecords)
                if (cids.isEmpty()) throw NoSuchElementException(localizedString(R.string.ui_error_no_isomers_for_formula, formula))

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
                val presentation = com.furthersecrets.chemsearch.data.SearchErrorResolver.fromThrowable(e, formula)
                val msg = when (e) {
                    is NoSuchElementException -> e.message ?: localizedString(R.string.ui_error_no_isomers)
                    else -> localizedString(presentation.messageRes, *presentation.args.toTypedArray())
                }
                DebugLog.e("ChemSearch", "Isomer search failed: ${e.message}")
                _uiState.update {
                    it.copy(
                        isLoadingIsomers = false,
                        isLoadingMoreIsomers = false,
                        isomerCanLoadMore = if (isLoadMore) false else it.isomerCanLoadMore,
                        isomerError = msg,
                        isomerErrorKind = presentation.kind
                    )
                }
            }
        }
    }

    // =====================================================================
    // Structure search
    // =====================================================================

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
                it.copy(error = localizedString(R.string.ui_error_draw_structure_first))
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
                if (standardized.atoms.isEmpty()) throw IllegalArgumentException(localizedString(R.string.ui_error_pubchem_could_not_clean))
                _structureSearchState.update {
                    it.copy(
                        isStandardizing = false,
                        standardizedSketch = standardized,
                        standardizeMessage = localizedString(R.string.ui_message_structure_cleaned)
                    )
                }
            } catch (e: Exception) {
                DebugLog.e("ChemSearch", "Structure standardization failed: ${e.message}")
                _structureSearchState.update {
                    it.copy(
                        isStandardizing = false,
                        error = when (e) {
                            is IOException -> localizedString(R.string.ui_error_network)
                            else -> e.message ?: localizedString(R.string.ui_error_could_not_clean)
                        }
                    )
                }
            }
        }
    }

    fun importStructureText(text: String) {
        val input = text.trim()
        if (input.isBlank()) {
            _structureSearchState.update { it.copy(error = localizedString(R.string.ui_error_paste_structure_first)) }
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
                if (imported.atoms.isEmpty()) throw IllegalArgumentException(localizedString(R.string.ui_error_could_not_read_structure))
                _structureSearchState.update {
                    it.copy(
                        isStandardizing = false,
                        standardizedSketch = imported,
                        standardizeMessage = localizedString(R.string.ui_message_imported_s, imported.formula.ifBlank { localizedString(R.string.ui_structure) })
                    )
                }
            } catch (e: Exception) {
                DebugLog.e("ChemSearch", "Structure import failed: ${e.message}")
                _structureSearchState.update {
                    it.copy(
                        isStandardizing = false,
                        error = when (e) {
                            is IOException -> localizedString(R.string.ui_error_network)
                            else -> localizedString(R.string.ui_error_could_not_import_structure)
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
                    error = StructureSearchWarning.forSketch(sketch).firstOrNull()?.let { warning ->
                        localizedString(warning.messageRes)
                    } ?: localizedString(R.string.ui_error_draw_at_least_two_connected_atoms),
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
                val cids = if (OfflineTestMode.enabled) {
                    OfflineTestMode.simulateNetworkProbe("structure/${mode.name}")
                    (1..minOf(maxRecords, 8)).map { OfflineTestMode.cidFor("struct-${mode.name}-$it") }
                } else {
                    val response = ApiClient.pubChem.searchStructureBySdf(
                        operation = mode.pubChemOperation,
                        sdf = molfile,
                        maxRecords = maxRecords,
                        threshold = if (mode == StructureSearchMode.SIMILAR) threshold else null
                    )
                    response.identifierList?.cid?.take(maxRecords).orEmpty()
                }
                if (cids.isEmpty()) throw NoSuchElementException(localizedString(R.string.ui_error_no_structure_matches))

                val propertyMap = if (OfflineTestMode.enabled) {
                    offlineIntercept.loadStructurePropertiesForCids(cids)
                } else {
                    dataRepository.loadStructurePropertiesForCids(cids)
                }
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
                    is IOException -> localizedString(R.string.ui_error_network)
                    is NoSuchElementException -> e.message ?: localizedString(R.string.ui_error_no_structure_matches)
                    else -> localizedString(R.string.ui_error_structure_search_failed)
                }
                DebugLog.e("ChemSearch", "Structure search failed: ${e.message}")
                _structureSearchState.update { it.copy(isLoading = false, error = msg) }
            }
        }
    }

    // =====================================================================
    // CID & random search
    // =====================================================================

    fun searchByCid(cid: Long) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            DebugLog.d("ChemSearch", "Search by CID: $cid")
            _uiState.update {
                it.copy(
                    isLoading = true, error = null, errorKind = null, hasResult = false,
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

            if (OfflineTestMode.enabled) {
                // Dummy CID lookup: synthesize a full record without network.
                _uiState.update { it.copy(isLoading = true, hasResult = false) }
                try {
                    OfflineTestMode.simulateNetworkProbe("cid/$cid")
                    val name = OfflineTestMode.nameFor(cid)
                    val rawFormula = OfflineTestMode.formulaFor(cid)
                    val formula = formatConventionalFormula(rawFormula)
                    saveToHistory(name)
                    val newState = ChemUiState(
                        isLoading = false,
                        hasResult = true,
                        cid = cid,
                        name = name,
                        formula = formula,
                        rawFormula = rawFormula,
                        empiricalFormula = getEmpiricalFormulaFor(formula),
                        weight = OfflineTestMode.weightFor(cid),
                        charge = 0,
                        atomNumber = 10 + (cid % 30).toInt(),
                        bondNumber = 9 + (cid % 28).toInt(),
                        covalentUnitCount = (cid % 20).toInt() + 1,
                        iupacName = OfflineTestMode.iupacFor(cid),
                        smiles = OfflineTestMode.smilesFor(cid),
                        connectivitySmiles = OfflineTestMode.smilesFor(cid),
                        inchiKey = "TEST$cid-KEYOFFLINE",
                        inchi = "InChI=1S/test.$cid",
                        pubDescription = OfflineTestMode.descriptionFor(cid, "CID $cid"),
                        descSource = getSavedDescSource(),
                        elementalData = calcElementalDataFor(formula),
                        history = recentQueries(),
                        activeTab = defaultMolTab(),
                        aiProvider = _uiState.value.aiProvider,
                        isomerMode = false,
                        isomers = emptyList(),
                        isCached = false,
                        isLoadingSynonyms = true,
                        advancedProperties = buildAdvancedProperties(
                            offlineIntercept.dummyProperty(cid),
                            localizedAdvancedPropertyLabels(localizedAppContext())
                        )
                    )
                    _uiState.update { newState }
                    _query.value = name
                    loadSynonymsForCurrentCompound(cid, force = true)
                    loadPubChemExtrasForCurrentCompound(cid, force = true)
                    fetchSafetyData()
                } catch (e: Exception) {
                    val presentation = SearchErrorResolver.fromThrowable(e, "CID $cid")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = localizedString(presentation.messageRes, *presentation.args.toTypedArray()),
                            errorKind = presentation.kind
                        )
                    }
                }
                return@launch
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
                val structureCounts = dataRepository.extractStructureCounts(recordDeferred.await())

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
                    empiricalFormula = getEmpiricalFormulaFor(formula),
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
                    elementalData = calcElementalDataFor(formula),
                    history = recentQueries(),
                    activeTab = defaultMolTab(),
                    aiProvider = _uiState.value.aiProvider,
                    isomerMode = false,
                    isomers = emptyList(),
                    isCached = false,
                    isLoadingSynonyms = true,
                    advancedProperties = buildAdvancedProperties(props, localizedAdvancedPropertyLabels(localizedAppContext()))
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
                    is java.io.IOException -> localizedString(R.string.ui_error_network)
                    else -> localizedString(R.string.ui_error_could_not_load_compound, cid)
                }
                DebugLog.e("ChemSearch", "searchByCid failed for $cid: ${e.message}")
                _uiState.update { it.copy(isLoading = false, error = msg) }
            }
        }
    }

    fun searchRandomCompound() {
        if (OfflineTestMode.enabled) {
            searchByCid(randomPubChemCid(upperBound = 8_999L))
            return
        }
        searchByCid(randomPubChemCid())
    }

    private suspend fun backfillStructureMetadataIfMissing(cached: ChemUiState, cid: Long) {
        if (OfflineTestMode.enabled) return
        if (cached.atomNumber != null && cached.bondNumber != null && cached.covalentUnitCount != null) return

        val props = runCatching { ApiClient.pubChem.getProperties(cid) }
            .getOrNull()
            ?.propertyTable
            ?.properties
            ?.firstOrNull()
        val counts = dataRepository.extractStructureCounts(runCatching { ApiClient.pubChem.getRecord(cid) }.getOrNull())

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

    // =====================================================================
    // Safety data
    // =====================================================================

    fun fetchSafetyData() {
        val cid = _uiState.value.cid ?: return
        _uiState.update { it.copy(isLoadingSafety = true) }
        DebugLog.d("ChemSearch", "Fetching GHS safety data for CID $cid")
        viewModelScope.launch {
            try {
                val ghs = if (OfflineTestMode.enabled) {
                    OfflineTestMode.simulateNetworkProbe("ghs/$cid")
                    if (cid % 3 == 0L) {
                        // Every third dummy compound is safety-quiet on purpose.
                        null
                    } else {
                        GhsData(
                            signalWord = if (cid % 2 == 0L) "Danger" else "Warning",
                            hazardStatements = listOf(
                                "H30${cid % 4}: Simulated hazard statement (may cause test irritation)",
                                "H31${cid % 4}: Simulated hazard statement (test-only)",
                                "P261: Avoid breathing dust/fume/gas/mist/vapours/spray (simulated)"
                            ),
                            pictogramCodes = if (cid % 2 == 0L) listOf("GHS02", "GHS07") else listOf("GHS06"),
                            retrievedAt = System.currentTimeMillis()
                        )
                    }
                } else {
                    dataRepository.parseGhsData(ApiClient.pubChemView.getSection(cid, "GHS Classification"))
                }
                DebugLog.d("ChemSearch", "GHS result: signal=${ghs?.signalWord}, pictograms=${ghs?.pictogramCodes?.size ?: 0}, hazards=${ghs?.hazardStatements?.size ?: 0}")
                _uiState.update { it.copy(isLoadingSafety = false, ghsData = ghs) }
            } catch (e: Exception) {
                DebugLog.e("ChemSearch", "GHS fetch failed for CID $cid: ${e.message}")
                _uiState.update { it.copy(isLoadingSafety = false, ghsData = null) }
            }
        }
    }
}

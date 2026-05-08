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
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.furthersecrets.chemsearch.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.IOException
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.furthersecrets.chemsearch.ui.DebugLog

class ChemViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("chemsearch_prefs", Context.MODE_PRIVATE)

    private val gson = Gson()
    private val _favorites = MutableStateFlow<List<FavoriteCompound>>(loadFavorites())
    val favorites: StateFlow<List<FavoriteCompound>> = _favorites.asStateFlow()

    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite.asStateFlow()

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

    init {
        DebugLog.verbose = prefs.getBoolean("debug_verbose", false)
        val savedProvider = AiProvider.entries.firstOrNull { it.name == prefs.getString("ai_provider", null) } ?: AiProvider.GEMINI
        _uiState.update { it.copy(history = loadHistory(), aiProvider = savedProvider) }
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
        refreshCacheSizeAsync()
        checkForUpdates()
    }

    fun isAiProviderSet(): Boolean = prefs.contains("ai_provider")

    fun setUpdateNotificationsEnabled(enabled: Boolean) {
        _updateNotificationsEnabled.value = enabled
        prefs.edit().putBoolean(PREF_UPDATE_NOTIFICATIONS, enabled).apply()
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
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveFavorites(list: List<FavoriteCompound>) {
        prefs.edit().putString("favorites", gson.toJson(list)).apply()
    }

    fun toggleTheme() {
        val next = !_isDarkTheme.value
        _isDarkTheme.value = next
        prefs.edit().putBoolean("dark_theme", next).apply()
        DebugLog.d("ChemSearch", "Theme → ${if (next) "dark" else "light"}")
    }

    fun setColorScheme(scheme: AppColorScheme) {
        _colorScheme.value = scheme
        prefs.edit().putString("color_scheme", scheme.name).apply()
        DebugLog.d("ChemSearch", "Color scheme → ${scheme.name}")
    }

    fun toggleAutoSuggest() {
        val next = !_autoSuggest.value
        _autoSuggest.value = next
        prefs.edit().putBoolean("auto_suggest", next).apply()
        if (!next) _uiState.update { it.copy(suggestions = emptyList()) }
        DebugLog.d("ChemSearch", "Autosuggestions → ${if (next) "on" else "off"}")
    }

    fun setCompactMode(enabled: Boolean) {
        _compactMode.value = enabled
        prefs.edit().putBoolean("compact_mode", enabled).apply()
        DebugLog.d("ChemSearch", "Compact mode → ${if (enabled) "on" else "off"}")
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
        _defaultDescSource.value = getSavedDescSource()
        _cacheDirPath.value = prefs.getString("cache_dir", "") ?: ""
        refreshCacheSizeAsync()
        refreshAiKeyStatus()
        _updateNotificationsEnabled.value = prefs.getBoolean(PREF_UPDATE_NOTIFICATIONS, true)
        _updateStatus.update {
            it.copy(lastCheckedAt = prefs.getLong(PREF_UPDATE_LAST_CHECK, 0L).takeIf { ts -> ts != 0L })
        }
        _favorites.value = loadFavorites()

        val provider = AiProvider.entries.firstOrNull { it.name == prefs.getString("ai_provider", null) }
            ?: AiProvider.GEMINI
        val source = getSavedDescSource()
        _uiState.update { current ->
            current.copy(
                history = loadHistory(),
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
                gson.fromJson(json, ChemUiState::class.java)
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
                    runCatching { gson.fromJson(file.readText(), ChemUiState::class.java) }.getOrNull()
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
                it.copy(isLoading = true, error = null, suggestions = emptyList(), hasResult = false, isCached = false, sdfData = null, ghsData = null, isLoadingSafety = false)
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
                        history = loadHistory(),
                        descSource = savedSource,
                        sdfData = null,
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
                            history = loadHistory(),
                            descSource = savedSource,
                            ghsData = cached.ghsData,
                            sdfData = null,
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
                val formula = props.molecularFormula ?: ""

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
                    history = loadHistory(),
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
        _uiState.update { it.copy(isLoadingDesc = true) }
        DebugLog.d("ChemSearch", "Fetching ${provider.shortName} description for \"$name\"")
        viewModelScope.launch {
            val prompt = "Write a short 2-3 sentence description of the chemical \"$name\". Include real-world applications. Keep it clear and easy to read."
            val req = GeminiRequest(contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt)))))
            try {
                val response = ApiClient.gemini.generateContent(getSelectedAiModel(provider), key, req)
                val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                DebugLog.d("ChemSearch", "${provider.shortName} response: ${text?.take(80) ?: "empty"}")
                _uiState.update { it.copy(isLoadingDesc = false, aiDescription = text ?: "${provider.shortName} returned empty response.") }
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
        _uiState.update { it.copy(isLoadingDesc = true) }
        DebugLog.d("ChemSearch", "Fetching ${provider.shortName} description for \"$name\"")
        viewModelScope.launch {
            val prompt = "Write a short 2-3 sentence description of the chemical \"$name\". Include real-world applications. Keep it clear and easy to read."
            val req = GroqRequest(
                model = getSelectedAiModel(provider),
                messages = listOf(GroqMessage(role = "user", content = prompt))
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
                _uiState.update { it.copy(isLoadingDesc = false, aiDescription = text ?: "${provider.shortName} returned empty response.") }
            } catch (e: Exception) {
                DebugLog.e("ChemSearch", "${provider.shortName} error: ${e.message}")
                _uiState.update { it.copy(isLoadingDesc = false, aiDescription = "${provider.shortName} error: ${e.message}") }
            }
        }
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
        _uiState.update { it.copy(isLoadingSdf = true) }
        DebugLog.d("ChemSearch", "Fetching SDF for CID $cid")
        viewModelScope.launch {
            try {
                val sdf = withContext(Dispatchers.IO) {
                    ApiClient.pubChem.getSdf(cid).string()
                }
                DebugLog.d("ChemSearch", "SDF loaded: ${sdf.lines().size} lines, ${sdf.length} bytes")
                _uiState.update { it.copy(isLoadingSdf = false, sdfData = sdf) }
            } catch (e: Exception) {
                DebugLog.e("ChemSearch", "SDF fetch failed for CID $cid: ${e.message}")
                Log.e("ChemViewModel", "Error fetching SDF", e)
                _uiState.update { it.copy(isLoadingSdf = false) }
            }
        }
    }

    fun clearSuggestions() = _uiState.update { it.copy(suggestions = emptyList()) }
    fun clearError() = _uiState.update { it.copy(error = null) }

    fun clearSearchResult() {
        searchJob?.cancel()
        autocompleteJob?.cancel()
        _query.value = ""
        _uiState.update { current ->
            ChemUiState(
                history = loadHistory(),
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
            _uiState.update { it.copy(aiDescription = null) }
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
        if (_uiState.value.aiProvider == provider) _uiState.update { it.copy(aiDescription = null) }
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

    private fun saveDescSource(source: DescSource) =
        prefs.edit().putString("desc_source", source.name).apply()

    private fun loadHistory(): List<String> =
        prefs.getString("history", "")?.split("||")?.filter { it.isNotBlank() } ?: emptyList()

    private fun saveToHistory(name: String) {
        val updated = loadHistory().toMutableList().apply {
            removeAll { it.equals(name, ignoreCase = true) }
            add(0, name)
        }
        prefs.edit().putString("history", updated.joinToString("||")).apply()
        _uiState.update { it.copy(history = updated) }
    }

    fun clearHistory() {
        prefs.edit().remove("history").apply()
        _uiState.update { it.copy(history = emptyList()) }
        DebugLog.d("ChemSearch", "Search history cleared")
    }

    fun removeHistoryItem(query: String) {
        val updated = loadHistory().toMutableList().apply {
            removeAll { it.equals(query, ignoreCase = true) }
        }
        prefs.edit().putString("history", updated.joinToString("||")).apply()
        _uiState.update { it.copy(history = updated) }
        DebugLog.d("ChemSearch", "Removed recent search: $query")
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
                    sdfData = null, ghsData = null, isLoadingSafety = false,
                    isomerMode = false, isomers = emptyList(),
                    isCached = false
                )
            }

            val cached = readCache(cid)
            if (cached != null) {
                DebugLog.d("ChemSearch", "Cache hit for CID $cid (${cached.name})")
                val savedSource = getSavedDescSource()
                _uiState.update {
                    cached.copy(
                        isLoading = false, hasResult = true,
                        history = loadHistory(), descSource = savedSource,
                        sdfData = null, activeTab = MolTab.TWO_D,
                        isomerMode = false, isomers = emptyList(),
                        isCached = true,
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
                val formula = props.molecularFormula ?: ""

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
                    history = loadHistory(),
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

    private fun parseFormula(formula: String): Map<String, Int> {
        val totalResult = mutableMapOf<String, Int>()
        val parts = formula.split(Regex("[·.*\\s]")).filter { it.isNotBlank() }
        for (part in parts) {
            val multMatch = Regex("^(\\d+)").find(part)
            val partMult = multMatch?.groupValues?.get(1)?.toInt() ?: 1
            val cleanPart = if (multMatch != null) part.substring(multMatch.range.last + 1) else part
            parseBasicFormula(cleanPart).forEach { (el, cnt) ->
                totalResult[el] = (totalResult[el] ?: 0) + (cnt * partMult)
            }
        }
        return totalResult
    }
    private fun parseBasicFormula(formula: String): Map<String, Int> {
        val result = mutableMapOf<String, Int>()
        val stack = ArrayDeque<MutableMap<String, Int>>().apply { addLast(result) }
        var i = 0
        while (i < formula.length) {
            when {
                formula[i] == '(' -> { stack.addLast(mutableMapOf()); i++ }
                formula[i] == ')' -> {
                    i++
                    var numStr = ""
                    while (i < formula.length && formula[i].isDigit()) numStr += formula[i++]
                    val mult = numStr.toIntOrNull() ?: 1
                    val top = stack.removeLast()
                    top.forEach { (el, cnt) -> stack.last()[el] = (stack.last()[el] ?: 0) + cnt * mult }
                }
                formula[i].isUpperCase() -> {
                    var el = formula[i].toString(); i++
                    while (i < formula.length && formula[i].isLowerCase()) el += formula[i++]
                    var numStr = ""
                    while (i < formula.length && formula[i].isDigit()) numStr += formula[i++]
                    val cnt = numStr.toIntOrNull() ?: 1
                    stack.last()[el] = (stack.last()[el] ?: 0) + cnt
                }
                else -> i++
            }
        }
        return result
    }

    private fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)

    private fun getEmpiricalFormula(formula: String): String {
        if (formula.isBlank()) return ""
        val els = parseFormula(formula)
        if (els.isEmpty()) return formula
        val d = els.values.reduce { a, b -> gcd(a, b) }
        return els.entries.joinToString("") { (el, cnt) -> el + if (cnt / d > 1) "${cnt / d}" else "" }
    }

    private fun calcElementalData(formula: String): List<ElementData> {
        if (formula.isBlank()) return emptyList()
        val els = parseFormula(formula)
        var total = 0.0
        val raw = els.mapNotNull { (el, cnt) ->
            val w = ATOMIC_WEIGHTS[el] ?: return@mapNotNull null
            val mass = w * cnt
            total += mass
            el to mass
        }
        if (total == 0.0) return emptyList()
        return raw.map { (el, mass) ->
            ElementData(el, ((mass / total) * 100).toFloat())
        }.sortedByDescending { it.percentage }
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
            GhsData(signalWord, dedupedHazards, pictogramCodes.distinct())
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
            .setSmallIcon(R.drawable.ic_launcher_foreground)
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
        private const val UPDATE_CHECK_INTERVAL_MS = 12 * 60 * 60 * 1000L

        private val ATOMIC_WEIGHTS = mapOf(
            "H" to 1.008, "He" to 4.0026, "Li" to 6.94, "Be" to 9.0122, "B" to 10.81, "C" to 12.011,
            "N" to 14.007, "O" to 15.999, "F" to 18.998, "Ne" to 20.180, "Na" to 22.990, "Mg" to 24.305,
            "Al" to 26.982, "Si" to 28.085, "P" to 30.974, "S" to 32.06, "Cl" to 35.45, "Ar" to 39.948,
            "K" to 39.098, "Ca" to 40.078, "Sc" to 44.956, "Ti" to 47.867, "V" to 50.942, "Cr" to 51.996,
            "Mn" to 54.938, "Fe" to 55.845, "Co" to 58.933, "Ni" to 58.693, "Cu" to 63.546, "Zn" to 65.38,
            "Ga" to 69.723, "Ge" to 72.63, "As" to 74.922, "Se" to 78.97, "Br" to 79.904, "Kr" to 83.798,
            "Rb" to 85.468, "Sr" to 87.62, "Y" to 88.906, "Zr" to 91.224, "Nb" to 92.906, "Mo" to 95.95,
            "Ru" to 101.07, "Rh" to 102.91, "Pd" to 106.42, "Ag" to 107.87, "Cd" to 112.41, "In" to 114.82,
            "Sn" to 118.71, "Sb" to 121.76, "Te" to 127.60, "I" to 126.90, "Xe" to 131.29, "Cs" to 132.91,
            "Ba" to 137.33, "La" to 138.91, "Ce" to 140.12, "Pr" to 140.91, "Nd" to 144.24, "Pm" to 145.0,
            "Sm" to 150.36, "Eu" to 151.96, "Gd" to 157.25, "Tb" to 158.93, "Dy" to 162.50, "Ho" to 164.93,
            "Er" to 167.26, "Tm" to 168.93, "Yb" to 173.05, "Lu" to 174.97, "Hf" to 178.49, "Ta" to 180.95,
            "W" to 183.84, "Re" to 186.21, "Os" to 190.23, "Ir" to 192.22, "Pt" to 195.08, "Au" to 196.97,
            "Hg" to 200.59, "Tl" to 204.38, "Pb" to 207.2, "Bi" to 208.98, "Po" to 209.0, "At" to 210.0,
            "Rn" to 222.0, "Fr" to 223.0, "Ra" to 226.0, "Ac" to 227.0, "Th" to 232.04, "Pa" to 231.04,
            "U" to 238.03, "Np" to 237.0, "Pu" to 244.0, "Am" to 243.0, "Cm" to 247.0, "Bk" to 247.0,
            "Cf" to 251.0, "Es" to 252.0, "Fm" to 257.0, "Md" to 258.0, "No" to 259.0, "Lr" to 262.0,
            "Rf" to 267.0, "Db" to 270.0, "Sg" to 271.0, "Bh" to 270.0, "Hs" to 277.0, "Mt" to 276.0,
            "Ds" to 281.0, "Rg" to 280.0, "Cn" to 285.0, "Nh" to 284.0, "Fl" to 289.0, "Mc" to 288.0,
            "Lv" to 293.0, "Ts" to 294.0, "Og" to 294.0
        )
    }
}

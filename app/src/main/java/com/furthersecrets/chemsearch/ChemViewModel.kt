package com.furthersecrets.chemsearch

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.furthersecrets.chemsearch.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.IOException
import com.google.gson.Gson
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

    private val _autoSuggest = MutableStateFlow(prefs.getBoolean("auto_suggest", true))
    val autoSuggest: StateFlow<Boolean> = _autoSuggest.asStateFlow()

    private val _defaultDescSource = MutableStateFlow(getSavedDescSource())
    val defaultDescSource: StateFlow<DescSource> = _defaultDescSource.asStateFlow()

    private val _cacheSizeBytes = MutableStateFlow(computeCacheSize())
    val cacheSizeBytes: StateFlow<Long> = _cacheSizeBytes.asStateFlow()

    private val _cacheDirPath = MutableStateFlow(prefs.getString("cache_dir", "") ?: "")
    val cacheDirPath: StateFlow<String> = _cacheDirPath.asStateFlow()

    private val _hasGeminiKey = MutableStateFlow(prefs.getString("gemini_key", null)?.isNotBlank() == true)
    val hasGeminiKey: StateFlow<Boolean> = _hasGeminiKey.asStateFlow()

    private val _hasGroqKey = MutableStateFlow(prefs.getString("groq_key", null)?.isNotBlank() == true)
    val hasGroqKey: StateFlow<Boolean> = _hasGroqKey.asStateFlow()

    init {
        val savedProvider = AiProvider.entries.firstOrNull { it.name == prefs.getString("ai_provider", null) } ?: AiProvider.GEMINI
        _uiState.update { it.copy(history = loadHistory(), aiProvider = savedProvider) }
        viewModelScope.launch {
            _uiState.collect { state ->
                _isFavorite.value = state.cid?.let { cid ->
                    _favorites.value.any { it.cid == cid }
                } ?: false
            }
        }
    }

    fun isAiProviderSet(): Boolean = prefs.contains("ai_provider")

    fun toggleFavorite() {
        val state = _uiState.value
        val cid = state.cid ?: return
        val current = _favorites.value.toMutableList()
        if (_isFavorite.value) {
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
        _favorites.value = current
        _isFavorite.value = !_isFavorite.value
        saveFavorites(current)
    }

    fun deleteFavorite(cid: Long) {
        val updated = _favorites.value.filter { it.cid != cid }
        _favorites.value = updated
        saveFavorites(updated)
        if (_uiState.value.cid == cid) _isFavorite.value = false
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

    fun toggleAutoSuggest() {
        val next = !_autoSuggest.value
        _autoSuggest.value = next
        prefs.edit().putBoolean("auto_suggest", next).apply()
        if (!next) _uiState.update { it.copy(suggestions = emptyList()) }
        DebugLog.d("ChemSearch", "Autosuggestions → ${if (next) "on" else "off"}")
    }

    fun setDefaultDescSource(source: DescSource) {
        _defaultDescSource.value = source
        saveDescSource(source)
    }

    fun setAiProvider(provider: AiProvider) {
        _uiState.update { it.copy(aiProvider = provider) }
        prefs.edit().putString("ai_provider", provider.name).apply()
        DebugLog.d("ChemSearch", "AI provider → ${provider.name}")
        if (_uiState.value.descSource == DescSource.AI) {
            fetchAiDescription()
        }
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

    fun getCacheSizeBytes(): Long = computeCacheSize()

    private fun computeCacheSize(): Long =
        runCatching { cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() } }.getOrDefault(0L)

    fun clearCache() {
        cacheDir.walkTopDown().filter { it.isFile }.forEach { it.delete() }
        _cacheSizeBytes.value = 0L
        DebugLog.d("ChemSearch", "Compound cache cleared")
    }

    fun setCacheDir(path: String) {
        prefs.edit().putString("cache_dir", path).apply()
        _cacheDirPath.value = path
        _cacheSizeBytes.value = computeCacheSize()
        DebugLog.d("ChemSearch", "Cache dir set to: $path")
    }

    fun getCacheDir(): String = prefs.getString("cache_dir", null) ?: ""

    private fun readCache(cid: Long): ChemUiState? {
        val file = java.io.File(cacheDir, "$cid.json")
        if (!file.exists()) return null
        return try {
            val json = file.readText()
            gson.fromJson(json, ChemUiState::class.java)
        } catch (e: Exception) {
            DebugLog.e("ChemSearch", "Cache read failed for CID $cid: ${e.message}")
            null
        }
    }

    private fun findCacheByName(query: String): ChemUiState? {
        val q = query.trim().lowercase()
        return try {
            cacheDir.listFiles()
                ?.filter { it.isFile && it.extension == "json" }
                ?.firstOrNull { file ->
                    try {
                        val state = gson.fromJson(file.readText(), ChemUiState::class.java)
                        state.name.lowercase() == q ||
                        state.synonyms.any { it.lowercase() == q } ||
                        state.casNumber?.lowercase() == q ||
                        state.cid?.toString() == q
                    } catch (e: Exception) { false }
                }
                ?.let { file ->
                    gson.fromJson(file.readText(), ChemUiState::class.java)
                }
        } catch (e: Exception) {
            DebugLog.e("ChemSearch", "Cache name search failed: ${e.message}")
            null
        }
    }

    private fun writeCache(state: ChemUiState) {
        val cid = state.cid ?: return
        try {
            val file = java.io.File(cacheDir, "$cid.json")
            file.writeText(gson.toJson(state))
            _cacheSizeBytes.value = computeCacheSize()
            DebugLog.d("ChemSearch", "Cached compound CID $cid (${file.length() / 1024}KB)")
        } catch (e: Exception) {
            DebugLog.e("ChemSearch", "Cache write failed: ${e.message}")
        }
    }

    fun search(queryOverride: String? = null) {

        val q = (queryOverride ?: _query.value).trim()
        if (q.isBlank()) return
        if (queryOverride != null) _query.value = q

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            DebugLog.d("ChemSearch", "Search started: \"$q\"")
            _uiState.update {
                it.copy(isLoading = true, error = null, suggestions = emptyList(), hasResult = false, sdfData = null, ghsData = null, isLoadingSafety = false)
            }

            val cachedByName = findCacheByName(q)
            if (cachedByName != null) {
                DebugLog.d("ChemSearch", "Cache hit by name for \"$q\" → CID ${cachedByName.cid}")
                val savedSource = getSavedDescSource()
                _uiState.update {
                    cachedByName.copy(
                        isLoading = false,
                        hasResult = true,
                        history = loadHistory(),
                        descSource = savedSource,
                        sdfData = null,
                        activeTab = MolTab.TWO_D
                    )
                }
                saveToHistory(cachedByName.name)
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
                            history = loadHistory(),
                            descSource = savedSource,
                            ghsData = cached.ghsData,
                            sdfData = null,
                            activeTab = MolTab.TWO_D
                        )
                    }
                    saveToHistory(cached.name)
                    when (savedSource) {
                        DescSource.WIKI -> if (cached.wikiDescription == null) fetchWikiDescription()
                        DescSource.AI   -> if (cached.aiDescription == null) fetchAiDescription()
                        else -> Unit
                    }
                    if (cached.ghsData == null) fetchSafetyData()
                    return@launch
                }

                val propsDeferred = async { runCatching { ApiClient.pubChem.getProperties(cid) }.getOrNull() }
                val synsDeferred  = async { runCatching { ApiClient.pubChem.getSynonyms(cid) }.getOrNull() }
                val descDeferred  = async { runCatching { ApiClient.pubChem.getDescription(cid) }.getOrNull() }

                val props = propsDeferred.await()?.propertyTable?.properties?.firstOrNull()
                    ?: CompoundProperty(cid, null, null, null, null, null, null, null, null)
                val synonyms = synsDeferred.await()
                    ?.informationList?.information?.firstOrNull()?.synonym ?: emptyList()
                val descItem = descDeferred.await()
                    ?.informationList?.information?.find { it.description != null }

                DebugLog.d("ChemSearch", "Properties fetched: MW=${props.molecularWeight}, formula=${props.molecularFormula}")

                val casRegex = Regex("""^\d{1,7}-\d{2}-\d$""")
                val casNumber = synonyms.firstOrNull { casRegex.matches(it) }

                val compoundName = synonyms.firstOrNull() ?: q
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
                DebugLog.d("ChemSearch", "Search complete: \"$compoundName\" (CID $cid), ${synonyms.size} synonyms, desc=${pubDesc != null}")

                val newState = ChemUiState(
                    isLoading = false,
                    hasResult = true,
                    cid = cid,
                    name = compoundName.replaceFirstChar { c -> c.uppercase() },
                    formula = formula,
                    empiricalFormula = getEmpiricalFormula(formula),
                    weight = props.molecularWeight ?: "",
                    charge = props.charge ?: 0,
                    iupacName = props.iupacName ?: "",
                    smiles = props.smiles ?: "",
                    connectivitySmiles = props.connectivitySmiles ?: props.smiles ?: "",
                    inchiKey = props.inchiKey ?: "",
                    inchi = props.inchi ?: "",
                    synonyms = synonyms.take(8),
                    casNumber = casNumber,
                    pubDescription = pubDesc,
                    wikiDescription = null,
                    aiDescription = null,
                    descSource = savedSource,
                    elementalData = calcElementalData(formula),
                    history = loadHistory(),
                    activeTab = MolTab.TWO_D,
                    aiProvider = _uiState.value.aiProvider
                )
                _uiState.update { newState }
                writeCache(newState)

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
        
        if (provider == AiProvider.GEMINI) {
            fetchGeminiDescription(name)
        } else {
            fetchGroqDescription(name)
        }
    }

    private fun fetchGeminiDescription(name: String) {
        val key = getGeminiKey() ?: run {
            _uiState.update { it.copy(isLoadingDesc = false, aiDescription = "No Gemini API key set. Add it in Settings.") }
            return
        }
        _uiState.update { it.copy(isLoadingDesc = true) }
        DebugLog.d("ChemSearch", "Fetching Gemini description for \"$name\"")
        viewModelScope.launch {
            val prompt = "Write a short 2-3 sentence description of the chemical \"$name\". Include real-world applications. Keep it clear and easy to read."
            val req = GeminiRequest(contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt)))))
            try {
                val response = ApiClient.gemini.generateContent(key, req)
                val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                DebugLog.d("ChemSearch", "Gemini response: ${text?.take(80) ?: "empty"}")
                _uiState.update { it.copy(isLoadingDesc = false, aiDescription = text ?: "Gemini returned empty response.") }
            } catch (e: Exception) {
                DebugLog.e("ChemSearch", "Gemini error: ${e.message}")
                _uiState.update { it.copy(isLoadingDesc = false, aiDescription = "Gemini error: ${e.message}") }
            }
        }
    }

    private fun fetchGroqDescription(name: String) {
        val key = getGroqKey() ?: run {
            _uiState.update { it.copy(isLoadingDesc = false, aiDescription = "No Groq API key set. Add it in Settings.") }
            return
        }
        _uiState.update { it.copy(isLoadingDesc = true) }
        DebugLog.d("ChemSearch", "Fetching Groq description for \"$name\"")
        viewModelScope.launch {
            val prompt = "Write a short 2-3 sentence description of the chemical \"$name\". Include real-world applications. Keep it clear and easy to read."
            val req = GroqRequest(
                model = "openai/gpt-oss-120b",
                messages = listOf(GroqMessage(role = "user", content = prompt))
            )
            try {
                val response = ApiClient.groq.generateContent("Bearer $key", req)
                val text = response.choices?.firstOrNull()?.message?.content
                DebugLog.d("ChemSearch", "Groq response: ${text?.take(80) ?: "empty"}")
                _uiState.update { it.copy(isLoadingDesc = false, aiDescription = text ?: "Groq returned empty response.") }
            } catch (e: Exception) {
                DebugLog.e("ChemSearch", "Groq error: ${e.message}")
                _uiState.update { it.copy(isLoadingDesc = false, aiDescription = "Groq error: ${e.message}") }
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

    fun getGeminiKey(): String? = prefs.getString("gemini_key", null)?.ifBlank { null }
    fun saveGeminiKey(key: String) {
        prefs.edit().putString("gemini_key", key).apply()
        _hasGeminiKey.value = key.isNotBlank()
    }

    fun clearGeminiKey() {
        prefs.edit().remove("gemini_key").apply()
        _hasGeminiKey.value = false
        if (_uiState.value.aiProvider == AiProvider.GEMINI) _uiState.update { it.copy(aiDescription = null) }
    }

    fun getGroqKey(): String? = prefs.getString("groq_key", null)?.ifBlank { null }
    fun saveGroqKey(key: String) {
        prefs.edit().putString("groq_key", key).apply()
        _hasGroqKey.value = key.isNotBlank()
    }

    fun clearGroqKey() {
        prefs.edit().remove("groq_key").apply()
        _hasGroqKey.value = false
        if (_uiState.value.aiProvider == AiProvider.GROQ) _uiState.update { it.copy(aiDescription = null) }
    }

    private fun getSavedDescSource(): DescSource =
        DescSource.entries.firstOrNull { it.name == prefs.getString("desc_source", null) }
            ?: DescSource.PUBCHEM

    private fun saveDescSource(source: DescSource) =
        prefs.edit().putString("desc_source", source.name).apply()

    private fun loadHistory(): List<String> =
        prefs.getString("history", "")?.split("||")?.filter { it.isNotBlank() } ?: emptyList()

    private fun saveToHistory(name: String) {
        val updated = loadHistory().toMutableList().apply {
            removeAll { it.equals(name, ignoreCase = true) }
            add(0, name)
            if (size > 10) removeLast()
        }
        prefs.edit().putString("history", updated.joinToString("||")).apply()
        _uiState.update { it.copy(history = updated) }
    }

    fun clearHistory() {
        prefs.edit().remove("history").apply()
        _uiState.update { it.copy(history = emptyList()) }
        DebugLog.d("ChemSearch", "Search history cleared")
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
                    isomerMode = false, isomers = emptyList()
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
                        isomerMode = false, isomers = emptyList()
                    )
                }
                _query.value = cached.name
                saveToHistory(cached.name)
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
                val synsDeferred  = async { runCatching { ApiClient.pubChem.getSynonyms(cid) }.getOrNull() }
                val descDeferred  = async { runCatching { ApiClient.pubChem.getDescription(cid) }.getOrNull() }

                val props = propsDeferred.await()?.propertyTable?.properties?.firstOrNull()
                    ?: CompoundProperty(cid, null, null, null, null, null, null, null, null)
                val synonyms = synsDeferred.await()
                    ?.informationList?.information?.firstOrNull()?.synonym ?: emptyList()
                val descItem = descDeferred.await()
                    ?.informationList?.information?.find { it.description != null }

                val casRegex = Regex("""^\d{1,7}-\d{2}-\d$""")
                val casNumber = synonyms.firstOrNull { casRegex.matches(it) }
                val compoundName = synonyms.firstOrNull() ?: "CID $cid"
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
                    iupacName = props.iupacName ?: "",
                    smiles = props.smiles ?: "",
                    connectivitySmiles = props.connectivitySmiles ?: props.smiles ?: "",
                    inchiKey = props.inchiKey ?: "",
                    inchi = props.inchi ?: "",
                    synonyms = synonyms.take(8),
                    casNumber = casNumber,
                    pubDescription = pubDesc,
                    wikiDescription = null, aiDescription = null,
                    descSource = savedSource,
                    elementalData = calcElementalData(formula),
                    history = loadHistory(),
                    activeTab = MolTab.TWO_D,
                    aiProvider = _uiState.value.aiProvider,
                    isomerMode = false,
                    isomers = emptyList()
                )
                _uiState.update { newState }
                _query.value = compoundName
                writeCache(newState)

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

            if (hazardStatements.isEmpty() && signalWord == null && pictogramCodes.isEmpty()) return null
            GhsData(signalWord, hazardStatements, pictogramCodes.distinct())
        } catch (e: Exception) {
            Log.e("ChemViewModel", "GHS parse error", e)
            null
        }
    }

    companion object {
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

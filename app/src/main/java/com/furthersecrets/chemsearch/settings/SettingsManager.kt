package com.furthersecrets.chemsearch.settings

import android.content.SharedPreferences
import com.furthersecrets.chemsearch.data.AppColorScheme
import com.furthersecrets.chemsearch.data.AppLanguage
import com.furthersecrets.chemsearch.data.AiModelCatalog
import com.furthersecrets.chemsearch.data.AiProvider
import com.furthersecrets.chemsearch.data.CacheRetention
import com.furthersecrets.chemsearch.data.CacheSizeLimit
import com.furthersecrets.chemsearch.data.DefaultStructureView
import com.furthersecrets.chemsearch.data.DescSource
import com.furthersecrets.chemsearch.data.FormulaDisplayStyle
import com.furthersecrets.chemsearch.data.OfflineDownloadQuality
import com.furthersecrets.chemsearch.data.TemperatureUnit
import com.furthersecrets.chemsearch.data.settings.AppSettingsSnapshot
import com.furthersecrets.chemsearch.data.settings.SettingsRepository
import com.furthersecrets.chemsearch.ui.DebugLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * State-holding owner of user-facing settings: display toggles, locale,
 * cache policy and AI provider configuration. Persists through
 * [SettingsRepository] (SharedPreferences + DataStore mirror) and replays
 * the DataStore snapshot so the two stores stay in sync.
 */
class SettingsManager(
    private val prefs: SharedPreferences,
    private val settingsRepository: SettingsRepository,
    private val scope: CoroutineScope
) {
    private val _isDarkTheme = MutableStateFlow(prefs.getBoolean("dark_theme", false))
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    private val _colorScheme = MutableStateFlow(settingsRepository.savedColorScheme())
    val colorScheme: StateFlow<AppColorScheme> = _colorScheme.asStateFlow()

    private val _autoSuggest = MutableStateFlow(prefs.getBoolean("auto_suggest", true))
    val autoSuggest: StateFlow<Boolean> = _autoSuggest.asStateFlow()

    private val _compactMode = MutableStateFlow(prefs.getBoolean("compact_mode", false))
    val compactMode: StateFlow<Boolean> = _compactMode.asStateFlow()

    private val _oledDarkTheme = MutableStateFlow(prefs.getBoolean("oled_dark_theme", false))
    val oledDarkTheme: StateFlow<Boolean> = _oledDarkTheme.asStateFlow()

    private val _defaultDescSource = MutableStateFlow(settingsRepository.savedDescSource())
    val defaultDescSource: StateFlow<DescSource> = _defaultDescSource.asStateFlow()

    private val _defaultStructureView = MutableStateFlow(settingsRepository.savedDefaultStructureView())
    val defaultStructureView: StateFlow<DefaultStructureView> = _defaultStructureView.asStateFlow()

    private val _offlineDownloadQuality = MutableStateFlow(settingsRepository.savedOfflineDownloadQuality())
    val offlineDownloadQuality: StateFlow<OfflineDownloadQuality> = _offlineDownloadQuality.asStateFlow()

    private val _formulaDisplayStyle = MutableStateFlow(settingsRepository.savedFormulaDisplayStyle())
    val formulaDisplayStyle: StateFlow<FormulaDisplayStyle> = _formulaDisplayStyle.asStateFlow()

    private val _cacheSizeLimit = MutableStateFlow(settingsRepository.savedCacheSizeLimit())
    val cacheSizeLimit: StateFlow<CacheSizeLimit> = _cacheSizeLimit.asStateFlow()

    private val _cacheRetention = MutableStateFlow(settingsRepository.savedCacheRetention())
    val cacheRetention: StateFlow<CacheRetention> = _cacheRetention.asStateFlow()

    private val _reduceMotion = MutableStateFlow(prefs.getBoolean("reduce_motion", false))
    val reduceMotion: StateFlow<Boolean> = _reduceMotion.asStateFlow()

    private val _temperatureUnit = MutableStateFlow(settingsRepository.savedTemperatureUnit())
    val temperatureUnit: StateFlow<TemperatureUnit> = _temperatureUnit.asStateFlow()

    private val _highContrastOutlines = MutableStateFlow(prefs.getBoolean("high_contrast_outlines", false))
    val highContrastOutlines: StateFlow<Boolean> = _highContrastOutlines.asStateFlow()

    private val _cardsEnabled = MutableStateFlow(prefs.getBoolean("cards_enabled", true))
    val cardsEnabled: StateFlow<Boolean> = _cardsEnabled.asStateFlow()

    private val _appLanguage = MutableStateFlow(
        AppLanguage.fromPreferenceKey(prefs.getString("language", AppLanguage.SYSTEM.preferenceKey))
    )
    val appLanguage: StateFlow<AppLanguage> = _appLanguage.asStateFlow()

    private val _cacheDirPath = MutableStateFlow(prefs.getString("cache_dir", "") ?: "")
    val cacheDirPath: StateFlow<String> = _cacheDirPath.asStateFlow()

    private val _showWelcome = MutableStateFlow(!prefs.getBoolean(PREF_WELCOME_SKIPPED, false))
    val showWelcome: StateFlow<Boolean> = _showWelcome.asStateFlow()

    private val _aiKeyStatus = MutableStateFlow(settingsRepository.aiKeyStatus())
    val aiKeyStatus: StateFlow<Map<AiProvider, Boolean>> = _aiKeyStatus.asStateFlow()

    private val _aiModelCatalogs = MutableStateFlow(settingsRepository.aiModelCatalogs())
    val aiModelCatalogs: StateFlow<Map<AiProvider, AiModelCatalog>> = _aiModelCatalogs.asStateFlow()

    /** Replays the DataStore snapshot into the flows; also performs the legacy migration. */
    fun startCollecting(onSnapshot: (AppSettingsSnapshot) -> Unit = {}) {
        settingsRepository.collectSettings { settings ->
            _isDarkTheme.value = settings.isDarkTheme
            _colorScheme.value = settings.colorScheme
            _autoSuggest.value = settings.autoSuggest
            _compactMode.value = settings.compactMode
            _oledDarkTheme.value = settings.oledDarkTheme
            _defaultDescSource.value = settings.descSource
            _cacheDirPath.value = settings.cacheDir
            _showWelcome.value = !settings.welcomeSkipped
            _defaultStructureView.value = settings.defaultStructureView
            _offlineDownloadQuality.value = settings.offlineDownloadQuality
            _formulaDisplayStyle.value = settings.formulaDisplayStyle
            _cacheSizeLimit.value = settings.cacheSizeLimit
            _cacheRetention.value = settings.cacheRetention
            _reduceMotion.value = settings.reduceMotion
            _highContrastOutlines.value = settings.highContrastOutlines
            _cardsEnabled.value = settings.cardsEnabled
            _appLanguage.value = settings.language
            onSnapshot(settings)
        }
    }

    fun toggleTheme() {
        val next = settingsRepository.toggleTheme(_isDarkTheme.value)
        _isDarkTheme.value = next
    }

    fun setColorScheme(scheme: AppColorScheme) {
        _colorScheme.value = scheme
        settingsRepository.setColorScheme(scheme)
    }

    fun toggleAutoSuggest(): Boolean {
        val next = settingsRepository.toggleAutoSuggest(_autoSuggest.value)
        _autoSuggest.value = next
        return next
    }

    fun setCompactMode(enabled: Boolean) {
        _compactMode.value = enabled
        settingsRepository.setCompactMode(enabled)
    }

    fun setOledDarkTheme(enabled: Boolean) {
        _oledDarkTheme.value = enabled
        settingsRepository.setOledDarkTheme(enabled)
    }

    fun setDefaultDescSource(source: DescSource) {
        _defaultDescSource.value = source
        settingsRepository.setDescSource(source)
    }

    fun setDefaultStructureView(view: DefaultStructureView) {
        _defaultStructureView.value = view
        settingsRepository.setDefaultStructureView(view)
    }

    fun setOfflineDownloadQuality(quality: OfflineDownloadQuality) {
        _offlineDownloadQuality.value = quality
        settingsRepository.setOfflineDownloadQuality(quality)
    }

    fun setFormulaDisplayStyle(style: FormulaDisplayStyle) {
        _formulaDisplayStyle.value = style
        settingsRepository.setFormulaDisplayStyle(style)
    }

    fun setCacheSizeLimit(limit: CacheSizeLimit) {
        _cacheSizeLimit.value = limit
        settingsRepository.setCacheSizeLimit(limit)
    }

    fun setCacheRetention(retention: CacheRetention) {
        _cacheRetention.value = retention
        settingsRepository.setCacheRetention(retention)
    }

    fun setReduceMotion(enabled: Boolean) {
        _reduceMotion.value = enabled
        settingsRepository.setReduceMotion(enabled)
    }

    fun setTemperatureUnit(unit: TemperatureUnit) {
        _temperatureUnit.value = unit
        settingsRepository.setTemperatureUnit(unit)
    }

    fun setHighContrastOutlines(enabled: Boolean) {
        _highContrastOutlines.value = enabled
        settingsRepository.setHighContrastOutlines(enabled)
    }

    fun setCardsEnabled(enabled: Boolean) {
        _cardsEnabled.value = enabled
        settingsRepository.setCardsEnabled(enabled)
    }

    fun setAppLanguage(language: AppLanguage) {
        _appLanguage.value = language
        settingsRepository.setAppLanguage(language)
    }

    fun setAiProvider(provider: AiProvider) {
        settingsRepository.setAiProvider(provider)
    }

    fun setCacheDirPath(path: String) {
        _cacheDirPath.value = path
    }

    fun skipWelcome() {
        _showWelcome.value = false
        prefs.edit().putBoolean(PREF_WELCOME_SKIPPED, true).apply()
        settingsRepository.setWelcomeSkipped(true)
        DebugLog.d("ChemSearch", "Welcome screen skipped")
    }

    fun showWelcomeAgain() {
        prefs.edit().putBoolean(PREF_WELCOME_SKIPPED, false).apply()
        _showWelcome.value = true
        settingsRepository.setWelcomeSkipped(false)
        DebugLog.d("ChemSearch", "Welcome screen opened from debug settings")
    }

    fun reloadFromPrefs() {
        _isDarkTheme.value = prefs.getBoolean("dark_theme", false)
        _colorScheme.value = settingsRepository.savedColorScheme()
        _autoSuggest.value = prefs.getBoolean("auto_suggest", true)
        _compactMode.value = prefs.getBoolean("compact_mode", false)
        _oledDarkTheme.value = prefs.getBoolean("oled_dark_theme", false)
        _defaultDescSource.value = settingsRepository.savedDescSource()
        _defaultStructureView.value = settingsRepository.savedDefaultStructureView()
        _offlineDownloadQuality.value = settingsRepository.savedOfflineDownloadQuality()
        _formulaDisplayStyle.value = settingsRepository.savedFormulaDisplayStyle()
        _cacheSizeLimit.value = settingsRepository.savedCacheSizeLimit()
        _cacheRetention.value = settingsRepository.savedCacheRetention()
        _reduceMotion.value = prefs.getBoolean("reduce_motion", false)
        _highContrastOutlines.value = prefs.getBoolean("high_contrast_outlines", false)
        _cardsEnabled.value = prefs.getBoolean("cards_enabled", true)
        _appLanguage.value = AppLanguage.fromPreferenceKey(
            prefs.getString("language", AppLanguage.SYSTEM.preferenceKey)
        )
        _cacheDirPath.value = prefs.getString("cache_dir", "") ?: ""
        _aiKeyStatus.value = settingsRepository.aiKeyStatus()
        _aiModelCatalogs.value = settingsRepository.aiModelCatalogs()
        _showWelcome.value = !prefs.getBoolean(PREF_WELCOME_SKIPPED, false)
    }

    fun refreshAiKeyStatus() {
        _aiKeyStatus.value = settingsRepository.aiKeyStatus()
    }

    fun refreshAiModelCatalogs() {
        _aiModelCatalogs.value = settingsRepository.aiModelCatalogs()
    }

    /** Applies [transform] to one provider's catalog (creating a default if absent). */
    fun updateAiModelCatalog(
        provider: AiProvider,
        transform: (AiModelCatalog) -> AiModelCatalog
    ) {
        _aiModelCatalogs.update { catalogs ->
            val current = catalogs[provider]
                ?: AiModelCatalog(models = provider.defaultModels, selectedModel = provider.modelName)
            catalogs + (provider to transform(current))
        }
    }

    companion object {
        private const val PREF_WELCOME_SKIPPED = "welcome_skipped"
    }
}

package com.furthersecrets.chemsearch.data.settings

import android.content.Context
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
import com.furthersecrets.chemsearch.data.SecurePrefs
import com.furthersecrets.chemsearch.ui.DebugLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Single owner of persisted app settings: SharedPreferences as the synchronous
 * legacy source, [AppSettingsStore] (DataStore) as the durable mirror, plus
 * AI provider keys and model catalogs.
 */
class SettingsRepository(
    context: Context,
    val prefs: SharedPreferences,
    private val settingsStore: AppSettingsStore = AppSettingsStore(context),
    private val scope: CoroutineScope
) {
    init {
        scope.launch {
            settingsStore.migrateSharedPreferencesIfNeeded(prefs)
        }
    }

    fun collectSettings(onEach: (AppSettingsSnapshot) -> Unit) {
        scope.launch {
            settingsStore.settings.collect { onEach(it) }
        }
    }

    // ---- Display / behaviour toggles ----

    fun toggleTheme(current: Boolean): Boolean {
        val next = !current
        prefs.edit().putBoolean("dark_theme", next).apply()
        scope.launch { settingsStore.setDarkTheme(next) }
        DebugLog.d("ChemSearch", "Theme → ${if (next) "dark" else "light"}")
        return next
    }

    fun setColorScheme(scheme: AppColorScheme) {
        prefs.edit().putString("color_scheme", scheme.name).apply()
        scope.launch { settingsStore.setColorScheme(scheme) }
        DebugLog.d("ChemSearch", "Color scheme → ${scheme.name}")
    }

    fun toggleAutoSuggest(current: Boolean): Boolean {
        val next = !current
        prefs.edit().putBoolean("auto_suggest", next).apply()
        scope.launch { settingsStore.setAutoSuggest(next) }
        DebugLog.d("ChemSearch", "Autosuggestions → ${if (next) "on" else "off"}")
        return next
    }

    fun setCompactMode(enabled: Boolean) {
        prefs.edit().putBoolean("compact_mode", enabled).apply()
        scope.launch { settingsStore.setCompactMode(enabled) }
        DebugLog.d("ChemSearch", "Compact mode → ${if (enabled) "on" else "off"}")
    }

    fun setOledDarkTheme(enabled: Boolean) {
        prefs.edit().putBoolean("oled_dark_theme", enabled).apply()
        scope.launch { settingsStore.setOledDarkTheme(enabled) }
        DebugLog.d("ChemSearch", "AMOLED mode → ${if (enabled) "on" else "off"}")
    }

    fun setDescSource(source: DescSource) {
        prefs.edit().putString("desc_source", source.name).apply()
        scope.launch { settingsStore.setDescSource(source) }
    }

    fun setDefaultStructureView(view: DefaultStructureView) {
        prefs.edit().putString("default_structure_view", view.name).apply()
        scope.launch { settingsStore.setDefaultStructureView(view) }
        DebugLog.d("ChemSearch", "Default structure view → ${view.name}")
    }

    fun setOfflineDownloadQuality(quality: OfflineDownloadQuality) {
        prefs.edit().putString("offline_download_quality", quality.name).apply()
        scope.launch { settingsStore.setOfflineDownloadQuality(quality) }
        DebugLog.d("ChemSearch", "Offline download quality → ${quality.name}")
    }

    fun setFormulaDisplayStyle(style: FormulaDisplayStyle) {
        prefs.edit().putString("formula_display_style", style.name).apply()
        scope.launch { settingsStore.setFormulaDisplayStyle(style) }
        DebugLog.d("ChemSearch", "Formula display style → ${style.name}")
    }

    fun setCacheSizeLimit(limit: CacheSizeLimit) {
        prefs.edit().putString("cache_size_limit", limit.name).apply()
        scope.launch { settingsStore.setCacheSizeLimit(limit) }
    }

    fun setCacheRetention(retention: CacheRetention) {
        prefs.edit().putString("cache_retention", retention.name).apply()
        scope.launch { settingsStore.setCacheRetention(retention) }
    }

    fun setReduceMotion(enabled: Boolean) {
        prefs.edit().putBoolean("reduce_motion", enabled).apply()
        scope.launch { settingsStore.setReduceMotion(enabled) }
        DebugLog.d("ChemSearch", "Reduce motion → ${if (enabled) "on" else "off"}")
    }

    fun savedTemperatureUnit(): TemperatureUnit =
        TemperatureUnit.entries.firstOrNull { it.name == prefs.getString("temperature_unit", null) }
            ?: TemperatureUnit.KELVIN

    fun setTemperatureUnit(unit: TemperatureUnit) {
        prefs.edit().putString("temperature_unit", unit.name).apply()
        scope.launch { settingsStore.setTemperatureUnit(unit) }
        DebugLog.d("ChemSearch", "Temperature unit → ${unit.name}")
    }

    fun setHighContrastOutlines(enabled: Boolean) {
        prefs.edit().putBoolean("high_contrast_outlines", enabled).apply()
        scope.launch { settingsStore.setHighContrastOutlines(enabled) }
        DebugLog.d("ChemSearch", "High contrast outlines → ${if (enabled) "on" else "off"}")
    }

    fun setCardsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("cards_enabled", enabled).apply()
        scope.launch { settingsStore.setCardsEnabled(enabled) }
        DebugLog.d("ChemSearch", "Cards → ${if (enabled) "on" else "off"}")
    }

    fun setAppLanguage(language: AppLanguage) {
        prefs.edit().putString("language", language.preferenceKey).apply()
        scope.launch { settingsStore.setLanguage(language) }
        DebugLog.d("ChemSearch", "Language → ${language.preferenceKey}")
    }

    fun setCacheDir(path: String) {
        scope.launch { settingsStore.setCacheDir(path) }
    }

    fun setWelcomeSkipped(skipped: Boolean) {
        scope.launch { settingsStore.setWelcomeSkipped(skipped) }
    }

    fun setUpdateNotificationsEnabled(enabled: Boolean) {
        scope.launch { settingsStore.setUpdateNotificationsEnabled(enabled) }
    }

    fun setAiProvider(provider: AiProvider) {
        prefs.edit().putString("ai_provider", provider.name).apply()
        DebugLog.d("ChemSearch", "AI provider → ${provider.shortName}")
    }

    fun isAiProviderSet(): Boolean = prefs.contains("ai_provider")

    // ---- Saved-value readers (legacy SharedPreferences sources of truth) ----

    fun savedDescSource(): DescSource =
        DescSource.entries.firstOrNull { it.name == prefs.getString("desc_source", null) }
            ?: DescSource.PUBCHEM

    fun savedColorScheme(): AppColorScheme =
        AppColorScheme.entries.firstOrNull { it.name == prefs.getString("color_scheme", null) }
            ?: AppColorScheme.BLUE

    fun savedDefaultStructureView(): DefaultStructureView =
        DefaultStructureView.entries.firstOrNull { it.name == prefs.getString("default_structure_view", null) }
            ?: DefaultStructureView.TWO_D

    fun savedOfflineDownloadQuality(): OfflineDownloadQuality =
        OfflineDownloadQuality.entries.firstOrNull { it.name == prefs.getString("offline_download_quality", null) }
            ?: OfflineDownloadQuality.COMPLETE

    fun savedFormulaDisplayStyle(): FormulaDisplayStyle =
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

    fun savedCacheSizeLimit(): CacheSizeLimit =
        CacheSizeLimit.entries.firstOrNull {
            it.name == normalizeSavedCacheSizeLimitName(prefs.getString("cache_size_limit", null))
        }
            ?: CacheSizeLimit.UNLIMITED

    private fun normalizeSavedCacheSizeLimitName(name: String?): String? =
        when (name) {
            "MB_250" -> CacheSizeLimit.UNLIMITED.name
            else -> name
        }

    fun savedCacheRetention(): CacheRetention =
        CacheRetention.entries.firstOrNull { it.name == prefs.getString("cache_retention", null) }
            ?: CacheRetention.MANUAL

    // ---- AI provider keys (Android Keystore encrypted) ----

    fun getAiKey(provider: AiProvider): String? =
        SecurePrefs.getString(prefs, provider.keyPref)?.ifBlank { null }

    fun hasAiKey(provider: AiProvider): Boolean = getAiKey(provider)?.isNotBlank() == true

    fun saveAiKey(provider: AiProvider, key: String): Boolean =
        runCatching { SecurePrefs.putString(prefs, provider.keyPref, key) }
            .onFailure { DebugLog.e("ChemSearch", "${provider.shortName} key save failed: ${it.message}") }
            .isSuccess

    fun clearAiKey(provider: AiProvider) {
        SecurePrefs.remove(prefs, provider.keyPref)
    }

    fun aiKeyStatus(): Map<AiProvider, Boolean> =
        AiProvider.entries.associateWith { provider ->
            SecurePrefs.getString(prefs, provider.keyPref)?.isNotBlank() == true
        }

    // ---- AI model catalogs ----

    fun aiModelCatalogs(): Map<AiProvider, AiModelCatalog> =
        AiProvider.entries.associateWith { provider ->
            val selected = prefs.getString(modelPrefKey(provider), null)?.takeIf { it.isNotBlank() }
                ?: provider.modelName
            AiModelCatalog(
                models = (listOf(selected) + provider.defaultModels).distinct(),
                selectedModel = selected
            )
        }

    fun modelPrefKey(provider: AiProvider): String = "ai_model_${provider.name.lowercase()}"

    fun savedAiModel(provider: AiProvider): String? =
        prefs.getString(modelPrefKey(provider), null)?.takeIf { it.isNotBlank() }

    fun saveAiModel(provider: AiProvider, model: String) {
        prefs.edit().putString(modelPrefKey(provider), model).apply()
    }

    fun savedAiProvider(): AiProvider =
        AiProvider.entries.firstOrNull { it.name == prefs.getString("ai_provider", null) }
            ?: AiProvider.GEMINI
}

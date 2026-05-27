package com.furthersecrets.chemsearch.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.furthersecrets.chemsearch.data.AppColorScheme
import com.furthersecrets.chemsearch.data.CacheRetention
import com.furthersecrets.chemsearch.data.CacheSizeLimit
import com.furthersecrets.chemsearch.data.DescSource
import com.furthersecrets.chemsearch.data.DefaultStructureView
import com.furthersecrets.chemsearch.data.FormulaDisplayStyle
import com.furthersecrets.chemsearch.data.OfflineDownloadQuality
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.chemSearchSettingsDataStore by preferencesDataStore(name = "chemsearch_settings")

data class AppSettingsSnapshot(
    val isDarkTheme: Boolean,
    val colorScheme: AppColorScheme,
    val autoSuggest: Boolean,
    val compactMode: Boolean,
    val oledDarkTheme: Boolean,
    val descSource: DescSource,
    val cacheDir: String,
    val updateNotificationsEnabled: Boolean,
    val welcomeSkipped: Boolean,
    val defaultStructureView: DefaultStructureView,
    val offlineDownloadQuality: OfflineDownloadQuality,
    val formulaDisplayStyle: FormulaDisplayStyle,
    val cacheSizeLimit: CacheSizeLimit,
    val cacheRetention: CacheRetention,
    val reduceMotion: Boolean,
    val highContrastOutlines: Boolean
) {
    companion object {
        fun fromRawValues(
            isDarkTheme: Boolean?,
            colorSchemeName: String?,
            autoSuggest: Boolean?,
            compactMode: Boolean?,
            oledDarkTheme: Boolean?,
            descSourceName: String?,
            cacheDir: String?,
            updateNotificationsEnabled: Boolean?,
            welcomeSkipped: Boolean?,
            defaultStructureViewName: String?,
            offlineDownloadQualityName: String?,
            formulaDisplayStyleName: String?,
            cacheSizeLimitName: String?,
            cacheRetentionName: String?,
            reduceMotion: Boolean?,
            highContrastOutlines: Boolean?
        ): AppSettingsSnapshot =
            AppSettingsSnapshot(
                isDarkTheme = isDarkTheme ?: false,
                colorScheme = AppColorScheme.entries.firstOrNull { it.name == colorSchemeName } ?: AppColorScheme.BLUE,
                autoSuggest = autoSuggest ?: true,
                compactMode = compactMode ?: false,
                oledDarkTheme = oledDarkTheme ?: false,
                descSource = DescSource.entries.firstOrNull { it.name == descSourceName } ?: DescSource.PUBCHEM,
                cacheDir = cacheDir ?: "",
                updateNotificationsEnabled = updateNotificationsEnabled ?: true,
                welcomeSkipped = welcomeSkipped ?: false,
                defaultStructureView = DefaultStructureView.entries.firstOrNull { it.name == defaultStructureViewName }
                    ?: DefaultStructureView.TWO_D,
                offlineDownloadQuality = OfflineDownloadQuality.entries.firstOrNull { it.name == offlineDownloadQualityName }
                    ?: OfflineDownloadQuality.COMPLETE,
                formulaDisplayStyle = FormulaDisplayStyle.entries.firstOrNull {
                    it.name == normalizeFormulaDisplayStyleName(formulaDisplayStyleName)
                }
                    ?: FormulaDisplayStyle.CONVENTIONAL,
                cacheSizeLimit = CacheSizeLimit.entries.firstOrNull { it.name == normalizeCacheSizeLimitName(cacheSizeLimitName) }
                    ?: CacheSizeLimit.UNLIMITED,
                cacheRetention = CacheRetention.entries.firstOrNull { it.name == cacheRetentionName }
                    ?: CacheRetention.MANUAL,
                reduceMotion = reduceMotion ?: false,
                highContrastOutlines = highContrastOutlines ?: false
            )
    }
}

private fun normalizeFormulaDisplayStyleName(name: String?): String? =
    when (name) {
        "PUBCHEM" -> FormulaDisplayStyle.HILL.name
        "CHARGE_FOCUSED" -> FormulaDisplayStyle.CONVENTIONAL.name
        else -> name
    }

private fun normalizeCacheSizeLimitName(name: String?): String? =
    when (name) {
        "MB_250" -> CacheSizeLimit.UNLIMITED.name
        else -> name
    }

class AppSettingsStore(private val context: Context) {
    private val dataStore = context.chemSearchSettingsDataStore

    val settings: Flow<AppSettingsSnapshot> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences ->
            AppSettingsSnapshot.fromRawValues(
                isDarkTheme = preferences[Keys.DARK_THEME],
                colorSchemeName = preferences[Keys.COLOR_SCHEME],
                autoSuggest = preferences[Keys.AUTO_SUGGEST],
                compactMode = preferences[Keys.COMPACT_MODE],
                oledDarkTheme = preferences[Keys.OLED_DARK_THEME],
                descSourceName = preferences[Keys.DESC_SOURCE],
                cacheDir = preferences[Keys.CACHE_DIR],
                updateNotificationsEnabled = preferences[Keys.UPDATE_NOTIFICATIONS],
                welcomeSkipped = preferences[Keys.WELCOME_SKIPPED],
                defaultStructureViewName = preferences[Keys.DEFAULT_STRUCTURE_VIEW],
                offlineDownloadQualityName = preferences[Keys.OFFLINE_DOWNLOAD_QUALITY],
                formulaDisplayStyleName = preferences[Keys.FORMULA_DISPLAY_STYLE],
                cacheSizeLimitName = preferences[Keys.CACHE_SIZE_LIMIT],
                cacheRetentionName = preferences[Keys.CACHE_RETENTION],
                reduceMotion = preferences[Keys.REDUCE_MOTION],
                highContrastOutlines = preferences[Keys.HIGH_CONTRAST_OUTLINES]
            )
        }

    suspend fun migrateSharedPreferencesIfNeeded(prefs: SharedPreferences) {
        dataStore.edit { preferences ->
            if (preferences[Keys.MIGRATED] == true) return@edit
            preferences[Keys.DARK_THEME] = prefs.getBoolean("dark_theme", false)
            preferences[Keys.COLOR_SCHEME] = prefs.getString("color_scheme", null) ?: AppColorScheme.BLUE.name
            preferences[Keys.AUTO_SUGGEST] = prefs.getBoolean("auto_suggest", true)
            preferences[Keys.COMPACT_MODE] = prefs.getBoolean("compact_mode", false)
            preferences[Keys.OLED_DARK_THEME] = prefs.getBoolean("oled_dark_theme", false)
            preferences[Keys.DESC_SOURCE] = prefs.getString("desc_source", null) ?: DescSource.PUBCHEM.name
            preferences[Keys.CACHE_DIR] = prefs.getString("cache_dir", null) ?: ""
            preferences[Keys.UPDATE_NOTIFICATIONS] = prefs.getBoolean("update_notifications", true)
            preferences[Keys.WELCOME_SKIPPED] = prefs.getBoolean("welcome_skipped", false)
            preferences[Keys.DEFAULT_STRUCTURE_VIEW] = prefs.getString("default_structure_view", null)
                ?: DefaultStructureView.TWO_D.name
            preferences[Keys.OFFLINE_DOWNLOAD_QUALITY] = prefs.getString("offline_download_quality", null)
                ?: OfflineDownloadQuality.COMPLETE.name
            preferences[Keys.FORMULA_DISPLAY_STYLE] = prefs.getString("formula_display_style", null)
                ?: FormulaDisplayStyle.CONVENTIONAL.name
            preferences[Keys.CACHE_SIZE_LIMIT] = prefs.getString("cache_size_limit", null)
                ?: CacheSizeLimit.UNLIMITED.name
            preferences[Keys.CACHE_RETENTION] = prefs.getString("cache_retention", null)
                ?: CacheRetention.MANUAL.name
            preferences[Keys.REDUCE_MOTION] = prefs.getBoolean("reduce_motion", false)
            preferences[Keys.HIGH_CONTRAST_OUTLINES] = prefs.getBoolean("high_contrast_outlines", false)
            preferences[Keys.MIGRATED] = true
        }
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        dataStore.edit { it[Keys.DARK_THEME] = enabled }
    }

    suspend fun setColorScheme(scheme: AppColorScheme) {
        dataStore.edit { it[Keys.COLOR_SCHEME] = scheme.name }
    }

    suspend fun setAutoSuggest(enabled: Boolean) {
        dataStore.edit { it[Keys.AUTO_SUGGEST] = enabled }
    }

    suspend fun setCompactMode(enabled: Boolean) {
        dataStore.edit { it[Keys.COMPACT_MODE] = enabled }
    }

    suspend fun setOledDarkTheme(enabled: Boolean) {
        dataStore.edit { it[Keys.OLED_DARK_THEME] = enabled }
    }

    suspend fun setDescSource(source: DescSource) {
        dataStore.edit { it[Keys.DESC_SOURCE] = source.name }
    }

    suspend fun setCacheDir(path: String) {
        dataStore.edit { it[Keys.CACHE_DIR] = path }
    }

    suspend fun setUpdateNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.UPDATE_NOTIFICATIONS] = enabled }
    }

    suspend fun setWelcomeSkipped(skipped: Boolean) {
        dataStore.edit { it[Keys.WELCOME_SKIPPED] = skipped }
    }

    suspend fun setDefaultStructureView(view: DefaultStructureView) {
        dataStore.edit { it[Keys.DEFAULT_STRUCTURE_VIEW] = view.name }
    }

    suspend fun setOfflineDownloadQuality(quality: OfflineDownloadQuality) {
        dataStore.edit { it[Keys.OFFLINE_DOWNLOAD_QUALITY] = quality.name }
    }

    suspend fun setFormulaDisplayStyle(style: FormulaDisplayStyle) {
        dataStore.edit { it[Keys.FORMULA_DISPLAY_STYLE] = style.name }
    }

    suspend fun setCacheSizeLimit(limit: CacheSizeLimit) {
        dataStore.edit { it[Keys.CACHE_SIZE_LIMIT] = limit.name }
    }

    suspend fun setCacheRetention(retention: CacheRetention) {
        dataStore.edit { it[Keys.CACHE_RETENTION] = retention.name }
    }

    suspend fun setReduceMotion(enabled: Boolean) {
        dataStore.edit { it[Keys.REDUCE_MOTION] = enabled }
    }

    suspend fun setHighContrastOutlines(enabled: Boolean) {
        dataStore.edit { it[Keys.HIGH_CONTRAST_OUTLINES] = enabled }
    }

    private object Keys {
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val COLOR_SCHEME = stringPreferencesKey("color_scheme")
        val AUTO_SUGGEST = booleanPreferencesKey("auto_suggest")
        val COMPACT_MODE = booleanPreferencesKey("compact_mode")
        val OLED_DARK_THEME = booleanPreferencesKey("oled_dark_theme")
        val DESC_SOURCE = stringPreferencesKey("desc_source")
        val CACHE_DIR = stringPreferencesKey("cache_dir")
        val UPDATE_NOTIFICATIONS = booleanPreferencesKey("update_notifications")
        val WELCOME_SKIPPED = booleanPreferencesKey("welcome_skipped")
        val DEFAULT_STRUCTURE_VIEW = stringPreferencesKey("default_structure_view")
        val OFFLINE_DOWNLOAD_QUALITY = stringPreferencesKey("offline_download_quality")
        val FORMULA_DISPLAY_STYLE = stringPreferencesKey("formula_display_style")
        val CACHE_SIZE_LIMIT = stringPreferencesKey("cache_size_limit")
        val CACHE_RETENTION = stringPreferencesKey("cache_retention")
        val REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
        val HIGH_CONTRAST_OUTLINES = booleanPreferencesKey("high_contrast_outlines")
        val MIGRATED = booleanPreferencesKey("settings_datastore_migrated")
    }
}

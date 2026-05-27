package com.furthersecrets.chemsearch.data.settings

import com.furthersecrets.chemsearch.data.AppColorScheme
import com.furthersecrets.chemsearch.data.CacheRetention
import com.furthersecrets.chemsearch.data.CacheSizeLimit
import com.furthersecrets.chemsearch.data.DescSource
import com.furthersecrets.chemsearch.data.DefaultStructureView
import com.furthersecrets.chemsearch.data.FormulaDisplayStyle
import com.furthersecrets.chemsearch.data.OfflineDownloadQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSettingsSnapshotTest {
    @Test
    fun fromRawValuesUsesSavedValuesWhenValid() {
        val snapshot = AppSettingsSnapshot.fromRawValues(
            isDarkTheme = true,
            colorSchemeName = "EMERALD",
            autoSuggest = false,
            compactMode = true,
            oledDarkTheme = true,
            descSourceName = "WIKI",
            cacheDir = "C:\\ChemSearchCache",
            updateNotificationsEnabled = false,
            welcomeSkipped = true,
            defaultStructureViewName = "THREE_D",
            offlineDownloadQualityName = "STRUCTURES",
            formulaDisplayStyleName = "HILL",
            cacheSizeLimitName = "MB_10",
            cacheRetentionName = "AUTO_CLEAR_1_DAY",
            reduceMotion = true,
            highContrastOutlines = true
        )

        assertTrue(snapshot.isDarkTheme)
        assertEquals(AppColorScheme.EMERALD, snapshot.colorScheme)
        assertFalse(snapshot.autoSuggest)
        assertTrue(snapshot.compactMode)
        assertTrue(snapshot.oledDarkTheme)
        assertEquals(DescSource.WIKI, snapshot.descSource)
        assertEquals("C:\\ChemSearchCache", snapshot.cacheDir)
        assertFalse(snapshot.updateNotificationsEnabled)
        assertTrue(snapshot.welcomeSkipped)
        assertEquals(DefaultStructureView.THREE_D, snapshot.defaultStructureView)
        assertEquals(OfflineDownloadQuality.STRUCTURES, snapshot.offlineDownloadQuality)
        assertEquals(FormulaDisplayStyle.HILL, snapshot.formulaDisplayStyle)
        assertEquals(CacheSizeLimit.MB_10, snapshot.cacheSizeLimit)
        assertEquals(CacheRetention.AUTO_CLEAR_1_DAY, snapshot.cacheRetention)
        assertTrue(snapshot.reduceMotion)
        assertTrue(snapshot.highContrastOutlines)
    }

    @Test
    fun fromRawValuesFallsBackToDefaultsWhenValuesAreMissingOrInvalid() {
        val snapshot = AppSettingsSnapshot.fromRawValues(
            isDarkTheme = null,
            colorSchemeName = "NOT_A_SCHEME",
            autoSuggest = null,
            compactMode = null,
            oledDarkTheme = null,
            descSourceName = "BAD_SOURCE",
            cacheDir = null,
            updateNotificationsEnabled = null,
            welcomeSkipped = null,
            defaultStructureViewName = "BAD_VIEW",
            offlineDownloadQualityName = "BAD_QUALITY",
            formulaDisplayStyleName = "BAD_FORMULA",
            cacheSizeLimitName = "BAD_LIMIT",
            cacheRetentionName = "BAD_RETENTION",
            reduceMotion = null,
            highContrastOutlines = null
        )

        assertFalse(snapshot.isDarkTheme)
        assertEquals(AppColorScheme.BLUE, snapshot.colorScheme)
        assertTrue(snapshot.autoSuggest)
        assertFalse(snapshot.compactMode)
        assertFalse(snapshot.oledDarkTheme)
        assertEquals(DescSource.PUBCHEM, snapshot.descSource)
        assertEquals("", snapshot.cacheDir)
        assertTrue(snapshot.updateNotificationsEnabled)
        assertFalse(snapshot.welcomeSkipped)
        assertEquals(DefaultStructureView.TWO_D, snapshot.defaultStructureView)
        assertEquals(OfflineDownloadQuality.COMPLETE, snapshot.offlineDownloadQuality)
        assertEquals(FormulaDisplayStyle.CONVENTIONAL, snapshot.formulaDisplayStyle)
        assertEquals(CacheSizeLimit.UNLIMITED, snapshot.cacheSizeLimit)
        assertEquals(CacheRetention.MANUAL, snapshot.cacheRetention)
        assertFalse(snapshot.reduceMotion)
        assertFalse(snapshot.highContrastOutlines)
    }

    @Test
    fun fromRawValuesMigratesOldFormulaDisplayNames() {
        val oldPubChem = AppSettingsSnapshot.fromRawValues(
            isDarkTheme = null,
            colorSchemeName = null,
            autoSuggest = null,
            compactMode = null,
            oledDarkTheme = null,
            descSourceName = null,
            cacheDir = null,
            updateNotificationsEnabled = null,
            welcomeSkipped = null,
            defaultStructureViewName = null,
            offlineDownloadQualityName = null,
            formulaDisplayStyleName = "PUBCHEM",
            cacheSizeLimitName = null,
            cacheRetentionName = null,
            reduceMotion = null,
            highContrastOutlines = null
        )
        val removedChargeMode = AppSettingsSnapshot.fromRawValues(
            isDarkTheme = null,
            colorSchemeName = null,
            autoSuggest = null,
            compactMode = null,
            oledDarkTheme = null,
            descSourceName = null,
            cacheDir = null,
            updateNotificationsEnabled = null,
            welcomeSkipped = null,
            defaultStructureViewName = null,
            offlineDownloadQualityName = null,
            formulaDisplayStyleName = "CHARGE_FOCUSED",
            cacheSizeLimitName = null,
            cacheRetentionName = null,
            reduceMotion = null,
            highContrastOutlines = null
        )

        assertEquals(FormulaDisplayStyle.HILL, oldPubChem.formulaDisplayStyle)
        assertEquals(FormulaDisplayStyle.CONVENTIONAL, removedChargeMode.formulaDisplayStyle)
    }
}

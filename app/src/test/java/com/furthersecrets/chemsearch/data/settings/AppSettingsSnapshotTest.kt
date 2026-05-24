package com.furthersecrets.chemsearch.data.settings

import com.furthersecrets.chemsearch.data.AppColorScheme
import com.furthersecrets.chemsearch.data.DescSource
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
            welcomeSkipped = true
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
            welcomeSkipped = null
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
    }
}

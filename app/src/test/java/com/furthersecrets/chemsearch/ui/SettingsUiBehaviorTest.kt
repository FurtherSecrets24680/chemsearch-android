package com.furthersecrets.chemsearch.ui

import com.furthersecrets.chemsearch.data.UpdateStatus
import com.furthersecrets.chemsearch.data.CacheRetention
import com.furthersecrets.chemsearch.data.CacheSizeLimit
import com.furthersecrets.chemsearch.data.ChemUiState
import com.furthersecrets.chemsearch.data.DefaultStructureView
import com.furthersecrets.chemsearch.data.FormulaDisplayStyle
import com.furthersecrets.chemsearch.data.OfflineDownloadQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsUiBehaviorTest {
    @Test
    fun oledModeControlIsOnlyEnabledInDarkMode() {
        assertFalse(isOledModeControlEnabled(isDark = false))
        assertTrue(isOledModeControlEnabled(isDark = true))
    }

    @Test
    fun amoledModeCopyUsesRequestedName() {
        assertEquals("AMOLED Mode", amoledModeTitle())
        assertEquals("Turn on dark mode to use AMOLED Mode", amoledModeSubtitle(isDark = false))
    }

    @Test
    fun updateDownloadProgressOnlyAppearsInSubtitleWhileDownloading() {
        val status = UpdateStatus(
            updateAvailable = true,
            isDownloadingUpdate = true,
            updateDownloadProgress = 0.42f
        )

        assertEquals("", updateDownloadActionLabel(status))
        assertEquals("Downloading update (42%)", updateDownloadSubtitle(status))
    }

    @Test
    fun newSettingsOptionsUseShortReadableLabels() {
        assertEquals("3D", defaultStructureViewLabel(DefaultStructureView.THREE_D))
        assertEquals("Complete", offlineDownloadQualityLabel(OfflineDownloadQuality.COMPLETE))
        assertEquals("Conventional", formulaDisplayStyleLabel(FormulaDisplayStyle.CONVENTIONAL))
        assertEquals("Hill", formulaDisplayStyleLabel(FormulaDisplayStyle.HILL))
        assertEquals("10 MB", cacheSizeLimitLabel(CacheSizeLimit.MB_10))
        assertEquals("100 MB", cacheSizeLimitLabel(CacheSizeLimit.MB_100))
        assertEquals("Daily", cacheRetentionLabel(CacheRetention.AUTO_CLEAR_1_DAY))
        assertEquals("Weekly", cacheRetentionLabel(CacheRetention.AUTO_CLEAR_7_DAYS))
        assertEquals("Monthly", cacheRetentionLabel(CacheRetention.AUTO_CLEAR_30_DAYS))
    }

    @Test
    fun cacheControlOptionsUseRequestedOrder() {
        assertEquals(
            listOf(CacheSizeLimit.MB_10, CacheSizeLimit.MB_50, CacheSizeLimit.MB_100, CacheSizeLimit.UNLIMITED),
            CacheSizeLimit.entries
        )
        assertEquals(
            listOf(
                CacheRetention.AUTO_CLEAR_1_DAY,
                CacheRetention.AUTO_CLEAR_7_DAYS,
                CacheRetention.AUTO_CLEAR_30_DAYS,
                CacheRetention.MANUAL
            ),
            CacheRetention.entries
        )
    }

    @Test
    fun formulaDisplayStyleCanUseConventionalOrHillOrdering() {
        val state = ChemUiState(formula = "NaCl", rawFormula = "ClNa")

        assertEquals("NaCl", displayFormulaForStyle(state, FormulaDisplayStyle.CONVENTIONAL))
        assertEquals("ClNa", displayFormulaForStyle(state, FormulaDisplayStyle.HILL))
    }

    @Test
    fun conventionalFormulaDisplayKeepsIonCharge() {
        val ammonium = ChemUiState(formula = "NH4", rawFormula = "H4N+", charge = 1)
        val sulfate = ChemUiState(formula = "SO4", rawFormula = "O4S-2", charge = -2)

        assertEquals("NH4+", displayFormulaForStyle(ammonium, FormulaDisplayStyle.CONVENTIONAL))
        assertEquals("SO4^2-", displayFormulaForStyle(sulfate, FormulaDisplayStyle.CONVENTIONAL))
    }
}

package com.furthersecrets.chemsearch.ui

import com.furthersecrets.chemsearch.data.UpdateStatus
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
    fun updateDownloadActionShowsProgressPercent() {
        val status = UpdateStatus(
            updateAvailable = true,
            isDownloadingUpdate = true,
            updateDownloadProgress = 0.42f
        )

        assertEquals("42%", updateDownloadActionLabel(status))
        assertEquals("Downloading update… 42%", updateDownloadSubtitle(status))
    }
}

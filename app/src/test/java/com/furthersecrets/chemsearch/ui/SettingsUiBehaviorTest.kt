package com.furthersecrets.chemsearch.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsUiBehaviorTest {
    @Test
    fun oledModeControlIsOnlyEnabledInDarkMode() {
        assertFalse(isOledModeControlEnabled(isDark = false))
        assertTrue(isOledModeControlEnabled(isDark = true))
    }
}

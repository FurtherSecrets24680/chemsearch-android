package com.furthersecrets.chemsearch.ui

import androidx.compose.ui.graphics.Color
import com.furthersecrets.chemsearch.data.AppColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeColorSchemeTest {
    @Test
    fun oledDarkUsesTrueBlackBaseWithVisibleRaisedSurfacesForEveryAccentScheme() {
        AppColorScheme.entries.forEach { accent ->
            val colors = chemSearchColorScheme(
                darkTheme = true,
                colorScheme = accent,
                oledDarkTheme = true
            )

            assertEquals("background for $accent", Color.Black, colors.background)
            assertEquals("surface for $accent", Color.Black, colors.surface)
            assertEquals("lowest surface container for $accent", Color.Black, colors.surfaceContainerLowest)
            assertNotEquals("surface container for $accent", Color.Black, colors.surfaceContainer)
            assertNotEquals("surface variant for $accent", Color.Black, colors.surfaceVariant)
            assertTrue("surface container should be visible for $accent", colors.surfaceContainer.red > colors.background.red)
            assertTrue("surface variant should be visible for $accent", colors.surfaceVariant.red > colors.background.red)
            assertTrue("outline should stand out from cards for $accent", colors.outline.red > colors.surfaceVariant.red)
        }
    }

    @Test
    fun oledDarkKeepsAccentPrimaryColor() {
        AppColorScheme.entries.forEach { accent ->
            val normalDark = chemSearchColorScheme(
                darkTheme = true,
                colorScheme = accent,
                oledDarkTheme = false
            )
            val oledDark = chemSearchColorScheme(
                darkTheme = true,
                colorScheme = accent,
                oledDarkTheme = true
            )

            assertEquals("primary for $accent", normalDark.primary, oledDark.primary)
            assertNotEquals("normal dark background for $accent", Color.Black, normalDark.background)
        }
    }
}

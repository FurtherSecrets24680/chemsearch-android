package com.furthersecrets.chemsearch.ui

import androidx.compose.runtime.compositionLocalOf
import com.furthersecrets.chemsearch.data.TemperatureUnit

val LocalCompactMode = compositionLocalOf { false }
val LocalReduceMotion = compositionLocalOf { false }
val LocalTemperatureUnit = compositionLocalOf { TemperatureUnit.KELVIN }

/** When false, cards/outlines collapse into a single unified flat page. */
val LocalCardsEnabled = compositionLocalOf { true }

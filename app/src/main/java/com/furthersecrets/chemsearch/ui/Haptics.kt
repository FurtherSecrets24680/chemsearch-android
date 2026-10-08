package com.furthersecrets.chemsearch.ui

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * App-wide haptic feedback helper. Centralized so every interaction uses the
 * same strength, and so it can honor the in-app "reduce motion" setting
 * (vibration is motion; users who turned it off should not be buzzed).
 */
class ChemHaptics(private val view: View, private val enabled: Boolean) {

    /** Light confirmation tick: tabs, chips, unit changes. */
    fun tick() {
        if (!enabled) return
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    /** Slightly stronger feedback for state flips: favorite, offline save. */
    fun confirm() {
        if (!enabled) return
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    /** Distinct buzz for primary actions completing: Predict, Balance, Copy. */
    fun action() {
        if (!enabled) return
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }
}

@Composable
fun rememberChemHaptics(reduceMotion: Boolean = LocalReduceMotion.current): ChemHaptics {
    val view = LocalView.current
    return remember(view, reduceMotion) { ChemHaptics(view, !reduceMotion) }
}

package com.furthersecrets.chemsearch.data

import androidx.annotation.StringRes
import com.furthersecrets.chemsearch.R
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow

class PhPohInputException(@StringRes val messageRes: Int) : IllegalArgumentException()

enum class PhPohInputType(val label: String, val symbol: String) {
    PH("pH", "pH"),
    POH("pOH", "pOH"),
    HYDROGEN("[H+]", "[H+]"),
    HYDROXIDE("[OH-]", "[OH-]")
}

data class PhPohResult(
    val ph: Double,
    val poh: Double,
    val hydrogenConcentration: Double,
    val hydroxideConcentration: Double,
    @StringRes val classificationRes: Int,
    @StringRes val assumptionRes: Int = R.string.ui_phpoh_assumption
)

fun calculatePhPoh(rawInput: String, inputType: PhPohInputType): PhPohResult {
    val value = rawInput.trim().toDoubleOrNull()
        ?: throw PhPohInputException(R.string.ui_error_enter_a_valid_number)

    val (ph, poh) = when (inputType) {
        PhPohInputType.PH -> {
            require(value.isFinite()) { throw PhPohInputException(R.string.ui_error_ph_must_be_a_valid_number) }
            value to (14.0 - value)
        }
        PhPohInputType.POH -> {
            require(value.isFinite()) { throw PhPohInputException(R.string.ui_error_poh_must_be_a_valid_number) }
            (14.0 - value) to value
        }
        PhPohInputType.HYDROGEN -> {
            require(value > 0.0 && value.isFinite()) { throw PhPohInputException(R.string.ui_error_concentration_must_be_greater_than_zero) }
            val calculatedPh = -log10(value)
            calculatedPh to (14.0 - calculatedPh)
        }
        PhPohInputType.HYDROXIDE -> {
            require(value > 0.0 && value.isFinite()) { throw PhPohInputException(R.string.ui_error_concentration_must_be_greater_than_zero) }
            val calculatedPoh = -log10(value)
            (14.0 - calculatedPoh) to calculatedPoh
        }
    }

    val hydrogen = 10.0.pow(-ph)
    val hydroxide = 10.0.pow(-poh)
    return PhPohResult(
        ph = ph,
        poh = poh,
        hydrogenConcentration = hydrogen,
        hydroxideConcentration = hydroxide,
        classificationRes = classifyPh(ph)
    )
}

fun formatPhPohNumber(value: Double): String {
    val absValue = abs(value)
    return when {
        value == 0.0 -> "0"
        absValue >= 0.001 && absValue < 10000 -> trimTrailingZeros("%.4f".format(value))
        else -> "%.3e".format(value)
    }
}

private fun classifyPh(ph: Double): Int = when {
    ph < 6.95 -> R.string.ui_classification_acidic
    ph > 7.05 -> R.string.ui_classification_basic
    else -> R.string.ui_classification_neutral
}

private fun trimTrailingZeros(value: String): String =
    value.trimEnd('0').trimEnd('.')

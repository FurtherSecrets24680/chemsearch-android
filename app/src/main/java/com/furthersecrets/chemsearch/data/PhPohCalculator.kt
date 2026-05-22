package com.furthersecrets.chemsearch.data

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow

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
    val classification: String
)

fun calculatePhPoh(rawInput: String, inputType: PhPohInputType): PhPohResult {
    val value = rawInput.trim().toDoubleOrNull()
        ?: throw IllegalArgumentException("Enter a valid number.")

    val (ph, poh) = when (inputType) {
        PhPohInputType.PH -> {
            require(value.isFinite()) { "pH must be a valid number." }
            value to (14.0 - value)
        }
        PhPohInputType.POH -> {
            require(value.isFinite()) { "pOH must be a valid number." }
            (14.0 - value) to value
        }
        PhPohInputType.HYDROGEN -> {
            require(value > 0.0 && value.isFinite()) { "Concentration must be greater than 0." }
            val calculatedPh = -log10(value)
            calculatedPh to (14.0 - calculatedPh)
        }
        PhPohInputType.HYDROXIDE -> {
            require(value > 0.0 && value.isFinite()) { "Concentration must be greater than 0." }
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
        classification = classifyPh(ph)
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

private fun classifyPh(ph: Double): String = when {
    ph < 6.95 -> "Acidic"
    ph > 7.05 -> "Basic"
    else -> "Neutral"
}

private fun trimTrailingZeros(value: String): String =
    value.trimEnd('0').trimEnd('.')

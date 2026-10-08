package com.furthersecrets.chemsearch.ui

import com.furthersecrets.chemsearch.data.FormulaParseResult
import com.furthersecrets.chemsearch.data.parseFormula
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * What the isomer search input means, computed locally before any network
 * call: normalized Hill-notation formula, total atom count, molar mass, and
 * degrees of unsaturation (DBE). DBE tells the user how many rings and/or
 * double/triple bonds any matching isomer must contain, which turns a bare
 * formula into real chemistry context.
 *
 * Halogens count as hydrogens and trivalent atoms (N, P, As...) count as
 * negative hydrogens in the DBE formula, so C2H4Cl2 (0 DBE) and C2H5N (1 DBE)
 * both report correctly.
 */
internal data class IsomerFormulaAnalysis(
    val input: String,
    val normalized: String,
    val counts: Map<String, Int>,
    val atomCount: Int,
    val molarMass: Double?,
    val unknownElements: List<String>,
    val dbe: Double?,
    val dbeNegative: Boolean
)

/** Returns null when the input is not a parseable molecular formula. */
internal fun analyzeIsomerFormula(input: String): IsomerFormulaAnalysis? {
    val trimmed = input.trim()
    if (trimmed.isBlank()) return null
    val counts: Map<String, Int> = when (val result = parseFormula(trimmed)) {
        is FormulaParseResult.Success -> result.composition.elements
        is FormulaParseResult.Failure -> return null
    }
    if (counts.isEmpty()) return null

    val atomCount = counts.values.sum()
    val unknownElements = counts.keys.filter { it !in ISOMER_ATOMIC_MASSES }
    val molarMass: Double? = if (unknownElements.isEmpty()) {
        counts.entries.sumOf { (element, count) -> ISOMER_ATOMIC_MASSES.getValue(element) * count }
    } else {
        null
    }

    val c = counts["C"] ?: 0
    val hasCarbon = c > 0
    val h = counts["H"] ?: 0
    val halogens = ISOMER_HALOGENS.sumOf { el -> counts[el] ?: 0 }
    val trivalents = ISOMER_TRIVALENTS.sumOf { el -> counts[el] ?: 0 }
    val dbe = if (hasCarbon) {
        c + 1.0 - (h + halogens - trivalents) / 2.0
    } else {
        null
    }

    return IsomerFormulaAnalysis(
        input = trimmed,
        normalized = hillNotation(counts),
        counts = counts,
        atomCount = atomCount,
        molarMass = molarMass,
        unknownElements = unknownElements,
        dbe = dbe,
        dbeNegative = dbe != null && dbe < 0.0
    )
}

/** Formats a DBE value: whole numbers without decimals, otherwise 1 decimal. */
internal fun formatIsomerDbe(dbe: Double?): String {
    if (dbe == null) return "-"
    val rounded = dbe.roundToInt()
    return if (abs(dbe - rounded) < 0.01) rounded.toString() else String.format(java.util.Locale.US, "%.1f", dbe)
}

/**
 * Hill notation: carbon first, hydrogen second, remaining elements
 * alphabetically; carbonless formulas are fully alphabetical.
 */
internal fun hillNotation(counts: Map<String, Int>): String {
    val ordered = buildList {
        val carbon = counts["C"] ?: 0
        val hydrogen = counts["H"] ?: 0
        if (carbon > 0) add("C" to carbon)
        if (hydrogen > 0) add("H" to hydrogen)
        counts.filterKeys { it != "C" && it != "H" }.toSortedMap().forEach { entry -> add(entry.key to entry.value) }
    }
    return ordered.joinToString("") { (element, count) -> if (count == 1) element else "$element$count" }
}

internal enum class IsomerSortMode { RELEVANCE, NAME }

internal fun sortIsomersForDisplay(
    isomers: List<com.furthersecrets.chemsearch.data.IsomerItem>,
    mode: IsomerSortMode
): List<com.furthersecrets.chemsearch.data.IsomerItem> = when (mode) {
    IsomerSortMode.RELEVANCE -> isomers
    IsomerSortMode.NAME -> isomers.sortedBy { it.title.lowercase() }
}

private val ISOMER_HALOGENS = setOf("F", "Cl", "Br", "I", "At")
private val ISOMER_TRIVALENTS = setOf("N", "P", "As", "Sb", "Bi")

/** Standard atomic weights (g/mol) for elements the analyzer can price. */
internal val ISOMER_ATOMIC_MASSES: Map<String, Double> = mapOf(
    "H" to 1.008, "He" to 4.003, "Li" to 6.94, "Be" to 9.012, "B" to 10.81,
    "C" to 12.011, "N" to 14.007, "O" to 15.999, "F" to 18.998, "Ne" to 20.180,
    "Na" to 22.990, "Mg" to 24.305, "Al" to 26.982, "Si" to 28.085, "P" to 30.974,
    "S" to 32.06, "Cl" to 35.45, "Ar" to 39.95, "K" to 39.098, "Ca" to 40.078,
    "Sc" to 44.956, "Ti" to 47.867, "V" to 50.942, "Cr" to 51.996, "Mn" to 54.938,
    "Fe" to 55.845, "Co" to 58.933, "Ni" to 58.693, "Cu" to 63.546, "Zn" to 65.38,
    "Ga" to 69.723, "Ge" to 72.630, "As" to 74.922, "Se" to 78.971, "Br" to 79.904,
    "Kr" to 83.798, "Rb" to 85.468, "Sr" to 87.62, "Ag" to 107.868, "Sn" to 118.710,
    "I" to 126.904, "Ba" to 137.327, "Pt" to 195.084, "Au" to 196.967, "Hg" to 200.592,
    "Pb" to 207.2, "Bi" to 208.980
)

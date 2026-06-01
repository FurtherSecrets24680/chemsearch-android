package com.furthersecrets.chemsearch.data

import kotlin.math.abs
import kotlin.math.roundToInt

enum class FormulaCompositionMode { PERCENT, MASS }

data class FormulaCompositionComponent(
    val element: String,
    val amount: Double
)

data class EmpiricalFormulaRow(
    val element: String,
    val amount: Double,
    val atomicMass: Double,
    val moles: Double,
    val ratio: Double,
    val wholeNumber: Int
)

data class EmpiricalFormulaCalculationResult(
    val empiricalFormula: String = "",
    val empiricalMass: Double = 0.0,
    val molecularFormula: String? = null,
    val molecularMultiplier: Int? = null,
    val rows: List<EmpiricalFormulaRow> = emptyList(),
    val error: String? = null,
    val warning: String? = null
)

fun calculateEmpiricalFormulaFromComposition(
    components: List<FormulaCompositionComponent>,
    mode: FormulaCompositionMode,
    molecularMass: Double? = null
): EmpiricalFormulaCalculationResult {
    val cleanComponents = components
        .mapNotNull { component ->
            val element = component.element.normalizeElementSymbol()
            if (element.isBlank() || component.amount <= 0.0) null else component.copy(element = element)
        }

    if (cleanComponents.isEmpty()) {
        return EmpiricalFormulaCalculationResult(error = "Enter at least one element amount")
    }

    val unknown = cleanComponents.map { it.element }.filter { it !in STANDARD_ATOMIC_WEIGHTS }.distinct()
    if (unknown.isNotEmpty()) {
        return EmpiricalFormulaCalculationResult(error = "Unknown element(s): ${unknown.joinToString(", ")}")
    }

    if (mode == FormulaCompositionMode.PERCENT) {
        val total = cleanComponents.sumOf { it.amount }
        if (total <= 0.0) return EmpiricalFormulaCalculationResult(error = "Percent composition must be greater than zero")
    }

    val moleRows = cleanComponents.map { component ->
        val atomicMass = STANDARD_ATOMIC_WEIGHTS.getValue(component.element)
        EmpiricalMoleRow(
            element = component.element,
            amount = component.amount,
            atomicMass = atomicMass,
            moles = component.amount / atomicMass
        )
    }

    val minMoles = moleRows.minOf { it.moles }
    if (minMoles <= 0.0) return EmpiricalFormulaCalculationResult(error = "Amounts must be greater than zero")

    val ratios = moleRows.map { it.moles / minMoles }
    val ratioMultiplier = findWholeRatioMultiplier(ratios)
    val wholeNumbers = ratios.map { ratio ->
        val whole = (ratio * ratioMultiplier).roundToInt()
        whole.coerceAtLeast(1)
    }

    val rows = moleRows.mapIndexed { index, row ->
        EmpiricalFormulaRow(
            element = row.element,
            amount = row.amount,
            atomicMass = row.atomicMass,
            moles = row.moles,
            ratio = ratios[index],
            wholeNumber = wholeNumbers[index]
        )
    }

    val empiricalCounts = rows.associate { it.element to it.wholeNumber }
    val empiricalFormula = buildFormulaFromOrderedCounts(rows.map { it.element }, empiricalCounts)
    val empiricalMass = rows.sumOf { it.atomicMass * it.wholeNumber }
    val molecular = buildMolecularFormula(empiricalCounts, rows.map { it.element }, empiricalMass, molecularMass)

    return EmpiricalFormulaCalculationResult(
        empiricalFormula = empiricalFormula,
        empiricalMass = empiricalMass,
        molecularFormula = molecular?.formula,
        molecularMultiplier = molecular?.multiplier,
        rows = rows,
        warning = molecular?.warning
    )
}

fun calculateEmpiricalFormulaFromMolecularFormula(
    formula: String,
    molecularMass: Double? = null
): EmpiricalFormulaCalculationResult {
    val counts = try {
        parseFormulaElementCounts(formula)
    } catch (error: FormulaParseException) {
        return EmpiricalFormulaCalculationResult(error = error.parseError.message)
    }
    if (counts.isEmpty()) return EmpiricalFormulaCalculationResult(error = "Enter a formula")

    val unknown = counts.keys.filter { it !in STANDARD_ATOMIC_WEIGHTS }
    if (unknown.isNotEmpty()) {
        return EmpiricalFormulaCalculationResult(error = "Unknown element(s): ${unknown.joinToString(", ")}")
    }

    val divisor = counts.values.reduce { a, b -> gcd(a, b) }.coerceAtLeast(1)
    val empiricalCounts = counts.mapValues { (_, count) -> count / divisor }
    val order = counts.keys.toList()
    val empiricalFormula = buildFormulaFromOrderedCounts(order, empiricalCounts)
    val empiricalMass = empiricalCounts.entries.sumOf { (element, count) ->
        STANDARD_ATOMIC_WEIGHTS.getValue(element) * count
    }
    val molecular = buildMolecularFormula(empiricalCounts, order, empiricalMass, molecularMass)

    return EmpiricalFormulaCalculationResult(
        empiricalFormula = empiricalFormula,
        empiricalMass = empiricalMass,
        molecularFormula = molecular?.formula ?: buildFormulaFromOrderedCounts(order, counts),
        molecularMultiplier = molecular?.multiplier ?: divisor,
        rows = order.map { element ->
            val atomicMass = STANDARD_ATOMIC_WEIGHTS.getValue(element)
            EmpiricalFormulaRow(
                element = element,
                amount = counts.getValue(element).toDouble(),
                atomicMass = atomicMass,
                moles = counts.getValue(element).toDouble(),
                ratio = empiricalCounts.getValue(element).toDouble(),
                wholeNumber = empiricalCounts.getValue(element)
            )
        },
        warning = molecular?.warning
    )
}

private data class EmpiricalMoleRow(
    val element: String,
    val amount: Double,
    val atomicMass: Double,
    val moles: Double
)

private data class MolecularFormulaCandidate(
    val formula: String?,
    val multiplier: Int?,
    val warning: String? = null
)

private fun buildMolecularFormula(
    empiricalCounts: Map<String, Int>,
    order: List<String>,
    empiricalMass: Double,
    molecularMass: Double?
): MolecularFormulaCandidate? {
    if (molecularMass == null || molecularMass <= 0.0 || empiricalMass <= 0.0) return null
    val rawMultiplier = molecularMass / empiricalMass
    val multiplier = rawMultiplier.roundToInt()
    if (multiplier < 1) {
        return MolecularFormulaCandidate(null, null, "Molecular mass must be at least the empirical formula mass")
    }
    val tolerance = if (molecularMass < 100.0) 0.18 else 0.12
    if (abs(rawMultiplier - multiplier) > tolerance) {
        return MolecularFormulaCandidate(null, null, "Molecular mass is not a clean multiple of the empirical formula mass")
    }

    val molecularCounts = empiricalCounts.mapValues { (_, count) -> count * multiplier }
    return MolecularFormulaCandidate(
        formula = buildFormulaFromOrderedCounts(order, molecularCounts),
        multiplier = multiplier
    )
}

private fun findWholeRatioMultiplier(ratios: List<Double>): Int {
    for (multiplier in 1..8) {
        val allClose = ratios.all { ratio ->
            val value = ratio * multiplier
            abs(value - value.roundToInt()) <= 0.08
        }
        if (allClose) return multiplier
    }
    return 1
}

private fun buildFormulaFromOrderedCounts(order: List<String>, counts: Map<String, Int>): String =
    order.distinct().joinToString("") { element ->
        val count = counts[element] ?: 0
        element + if (count > 1) count.toString() else ""
    }

private fun gcd(a: Int, b: Int): Int =
    if (b == 0) abs(a) else gcd(b, a % b)

private fun String.normalizeElementSymbol(): String {
    val clean = trim()
    if (clean.isBlank()) return ""
    return clean.take(1).uppercase() + clean.drop(1).lowercase()
}

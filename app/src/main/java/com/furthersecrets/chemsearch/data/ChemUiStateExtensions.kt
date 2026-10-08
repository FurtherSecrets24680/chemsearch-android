package com.furthersecrets.chemsearch.data

internal fun getEmpiricalFormulaFor(formula: String): String =
    calculateEmpiricalFormula(formula)

internal fun calcElementalDataFor(formula: String): List<ElementData> =
    calculateElementalPercentages(formula)
        .map { ElementData(it.element, it.percentage.toFloat()) }

internal fun ChemUiState.withConventionalFormula(): ChemUiState {
    val originalFormula = runCatching { rawFormula }.getOrDefault("").orEmpty()
    val conventional = formatConventionalFormula(formula)
    val safeRawFormula = originalFormula.ifBlank { formula }
    if (conventional == formula && safeRawFormula == originalFormula) return this
    return copy(
        formula = conventional,
        rawFormula = safeRawFormula,
        empiricalFormula = getEmpiricalFormulaFor(conventional),
        elementalData = calcElementalDataFor(conventional)
    )
}

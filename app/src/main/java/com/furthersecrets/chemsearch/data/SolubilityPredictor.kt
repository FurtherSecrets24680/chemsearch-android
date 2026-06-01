package com.furthersecrets.chemsearch.data

import kotlin.math.abs

enum class SolubilityState {
    SOLUBLE,
    INSOLUBLE,
    SLIGHTLY_SOLUBLE,
    UNKNOWN
}

data class SolubilityRuleResult(
    val state: SolubilityState,
    val rule: String
)

data class PrecipitationProduct(
    val formula: String,
    val cation: String,
    val anion: String,
    val solubility: SolubilityRuleResult
)

data class PrecipitationPredictionResult(
    val reactants: List<String> = emptyList(),
    val products: List<PrecipitationProduct> = emptyList(),
    val precipitates: List<PrecipitationProduct> = emptyList(),
    val molecularEquation: String = "",
    val completeIonicEquation: String = "",
    val netIonicEquation: String = "",
    val summary: String = "",
    val error: String? = null
)

fun predictPrecipitation(firstFormula: String, secondFormula: String): PrecipitationPredictionResult {
    val first = identifyCommonIonicCompound(firstFormula)
        ?: return PrecipitationPredictionResult(error = "Could not identify common aqueous ions in ${firstFormula.trim()}.")
    val second = identifyCommonIonicCompound(secondFormula)
        ?: return PrecipitationPredictionResult(error = "Could not identify common aqueous ions in ${secondFormula.trim()}.")

    val firstProduct = buildIonicCompound(first.cation, second.anion)
    val secondProduct = buildIonicCompound(second.cation, first.anion)
    val products = listOf(firstProduct, secondProduct).map { compound ->
        PrecipitationProduct(
            formula = compound.formula,
            cation = compound.cation.formula,
            anion = compound.anion.formula,
            solubility = predictSolubility(compound.cation, compound.anion)
        )
    }
    val precipitates = products.filter {
        it.solubility.state == SolubilityState.INSOLUBLE || it.solubility.state == SolubilityState.SLIGHTLY_SOLUBLE
    }

    val rawEquation = "${first.formula} + ${second.formula} -> ${firstProduct.formula} + ${secondProduct.formula}"
    val balanced = balanceChemicalReaction(rawEquation)
    val molecularEquation = formatPrecipitationMolecularEquation(
        balanced = balanced,
        fallbackReactants = listOf(first.formula, second.formula),
        productStates = products.associate { it.formula to it.stateSuffix() }
    )

    return PrecipitationPredictionResult(
        reactants = listOf(first.formula, second.formula),
        products = products,
        precipitates = precipitates,
        molecularEquation = molecularEquation,
        completeIonicEquation = formatCompleteIonicEquation(balanced, products),
        netIonicEquation = formatNetIonicEquation(precipitates, firstProduct, secondProduct),
        summary = if (precipitates.isEmpty()) {
            "No precipitate predicted"
        } else {
            "Precipitate predicted: ${precipitates.joinToString { it.formula }}"
        }
    )
}

private data class CommonIon(
    val formula: String,
    val charge: Int,
    val name: String,
    val counts: Map<String, Int> = parseFormulaElementCounts(formula)
)

private data class IonicCompoundMatch(
    val formula: String,
    val cation: CommonIon,
    val anion: CommonIon,
    val cationCount: Int,
    val anionCount: Int
)

private val commonCations = listOf(
    CommonIon("H", 1, "hydrogen"),
    CommonIon("Li", 1, "lithium"),
    CommonIon("Na", 1, "sodium"),
    CommonIon("K", 1, "potassium"),
    CommonIon("Rb", 1, "rubidium"),
    CommonIon("Cs", 1, "cesium"),
    CommonIon("NH4", 1, "ammonium"),
    CommonIon("Ag", 1, "silver"),
    CommonIon("Mg", 2, "magnesium"),
    CommonIon("Ca", 2, "calcium"),
    CommonIon("Sr", 2, "strontium"),
    CommonIon("Ba", 2, "barium"),
    CommonIon("Zn", 2, "zinc"),
    CommonIon("Cu", 2, "copper(II)"),
    CommonIon("Fe", 2, "iron(II)"),
    CommonIon("Fe", 3, "iron(III)"),
    CommonIon("Al", 3, "aluminum"),
    CommonIon("Pb", 2, "lead(II)"),
    CommonIon("Hg2", 2, "mercury(I)")
)

private val commonAnions = listOf(
    CommonIon("F", -1, "fluoride"),
    CommonIon("Cl", -1, "chloride"),
    CommonIon("Br", -1, "bromide"),
    CommonIon("I", -1, "iodide"),
    CommonIon("OH", -1, "hydroxide"),
    CommonIon("NO3", -1, "nitrate"),
    CommonIon("NO2", -1, "nitrite"),
    CommonIon("C2H3O2", -1, "acetate"),
    CommonIon("CH3COO", -1, "acetate"),
    CommonIon("ClO3", -1, "chlorate"),
    CommonIon("ClO4", -1, "perchlorate"),
    CommonIon("SO4", -2, "sulfate"),
    CommonIon("SO3", -2, "sulfite"),
    CommonIon("CO3", -2, "carbonate"),
    CommonIon("PO4", -3, "phosphate"),
    CommonIon("S", -2, "sulfide"),
    CommonIon("CrO4", -2, "chromate"),
    CommonIon("C2O4", -2, "oxalate"),
    CommonIon("O", -2, "oxide")
)

private val alwaysSolubleCations = setOf("Li", "Na", "K", "Rb", "Cs", "NH4")
private val alwaysSolubleAnions = setOf("NO3", "NO2", "C2H3O2", "CH3COO", "ClO3", "ClO4")
private val halideAnions = setOf("Cl", "Br", "I")
private val halideExceptions = setOf("Ag", "Pb", "Hg2")
private val sulfateExceptions = setOf("Ba", "Pb", "Sr", "Ca", "Ag", "Hg2")
private val mostlyInsolubleAnions = setOf("CO3", "PO4", "S", "SO3", "CrO4", "C2O4", "O")

private fun identifyCommonIonicCompound(rawFormula: String): IonicCompoundMatch? {
    val formula = rawFormula.trim()
    val counts = runCatching { parseFormulaElementCounts(formula) }.getOrNull() ?: return null
    for (cation in commonCations) {
        for (anion in commonAnions) {
            val compound = buildIonicCompound(cation, anion)
            if (compoundCounts(compound) == counts) {
                return compound.copy(formula = formula)
            }
        }
    }
    return null
}

private fun buildIonicCompound(cation: CommonIon, anion: CommonIon): IonicCompoundMatch {
    val chargeGcd = gcd(abs(cation.charge), abs(anion.charge)).coerceAtLeast(1)
    val cationCount = abs(anion.charge) / chargeGcd
    val anionCount = abs(cation.charge) / chargeGcd
    val formula = formatIonicFormula(cation, cationCount) + formatIonicFormula(anion, anionCount)
    return IonicCompoundMatch(
        formula = formula,
        cation = cation,
        anion = anion,
        cationCount = cationCount,
        anionCount = anionCount
    )
}

private fun compoundCounts(compound: IonicCompoundMatch): Map<String, Int> {
    val counts = linkedMapOf<String, Int>()
    compound.cation.counts.forEach { (element, count) ->
        counts[element] = (counts[element] ?: 0) + count * compound.cationCount
    }
    compound.anion.counts.forEach { (element, count) ->
        counts[element] = (counts[element] ?: 0) + count * compound.anionCount
    }
    return counts
}

private fun formatIonicFormula(ion: CommonIon, count: Int): String {
    if (count == 1) return ion.formula
    val needsParentheses = ion.counts.size > 1
    return if (needsParentheses) "(${ion.formula})$count" else "${ion.formula}$count"
}

private fun predictSolubility(cation: CommonIon, anion: CommonIon): SolubilityRuleResult = when {
    cation.formula in alwaysSolubleCations ->
        SolubilityRuleResult(SolubilityState.SOLUBLE, "Group 1 and ammonium salts are soluble.")
    anion.formula in alwaysSolubleAnions ->
        SolubilityRuleResult(SolubilityState.SOLUBLE, "Nitrates, acetates, chlorates, and perchlorates are soluble.")
    anion.formula in halideAnions && cation.formula in halideExceptions ->
        SolubilityRuleResult(SolubilityState.INSOLUBLE, "Chlorides, bromides, and iodides are insoluble with Ag+, Pb2+, and Hg2^2+.")
    anion.formula in halideAnions ->
        SolubilityRuleResult(SolubilityState.SOLUBLE, "Most chlorides, bromides, and iodides are soluble.")
    anion.formula == "SO4" && cation.formula in sulfateExceptions ->
        SolubilityRuleResult(SolubilityState.INSOLUBLE, "Sulfates are insoluble or only slightly soluble with Ba2+, Pb2+, Sr2+, Ca2+, Ag+, and Hg2^2+.")
    anion.formula == "SO4" ->
        SolubilityRuleResult(SolubilityState.SOLUBLE, "Most sulfates are soluble.")
    anion.formula == "OH" && cation.formula in setOf("Ba", "Sr", "Ca") ->
        SolubilityRuleResult(SolubilityState.SLIGHTLY_SOLUBLE, "Hydroxides of Ba2+, Sr2+, and Ca2+ are only partly soluble.")
    anion.formula == "OH" ->
        SolubilityRuleResult(SolubilityState.INSOLUBLE, "Most hydroxides are insoluble except Group 1 and ammonium salts.")
    anion.formula in mostlyInsolubleAnions ->
        SolubilityRuleResult(SolubilityState.INSOLUBLE, "Carbonates, phosphates, sulfides, sulfites, chromates, oxalates, and oxides are usually insoluble except with Group 1 or ammonium ions.")
    else ->
        SolubilityRuleResult(SolubilityState.UNKNOWN, "No common solubility rule matched this product.")
}

private fun formatPrecipitationMolecularEquation(
    balanced: BalancedReactionResult,
    fallbackReactants: List<String>,
    productStates: Map<String, String>
): String {
    if (balanced.error != null) {
        return fallbackReactants.joinToString(" + ") + " ⟶ " +
            productStates.entries.joinToString(" + ") { (formula, state) -> "$formula$state" }
    }
    return balanced.reactants.joinToString(" + ") { it.formatTerm() } +
        " ⟶ " +
        balanced.products.joinToString(" + ") { term ->
            term.formatTerm() + (productStates[term.formula] ?: "(aq)")
        }
}

private fun formatCompleteIonicEquation(
    balanced: BalancedReactionResult,
    products: List<PrecipitationProduct>
): String {
    if (balanced.error != null) return ""
    val productStates = products.associateBy { it.formula }
    val left = balanced.reactants.flatMap { term ->
        dissociateFormula(term.formula, term.coefficient)
    }
    val right = balanced.products.flatMap { term ->
        val product = productStates[term.formula]
        if (product != null && product.solubility.state != SolubilityState.SOLUBLE) {
            listOf(term.formatTerm() + product.stateSuffix())
        } else {
            dissociateFormula(term.formula, term.coefficient)
        }
    }
    return left.joinToString(" + ") + " ⟶ " + right.joinToString(" + ")
}

private fun dissociateFormula(formula: String, coefficient: Int): List<String> {
    val compound = identifyCommonIonicCompound(formula) ?: return listOf(if (coefficient > 1) "$coefficient$formula" else formula)
    return listOf(
        formatIonTerm(coefficient * compound.cationCount, compound.cation),
        formatIonTerm(coefficient * compound.anionCount, compound.anion)
    )
}

private fun formatNetIonicEquation(
    precipitates: List<PrecipitationProduct>,
    firstProduct: IonicCompoundMatch,
    secondProduct: IonicCompoundMatch
): String {
    if (precipitates.size != 1) return if (precipitates.isEmpty()) "No net ionic reaction" else ""
    val precipitate = precipitates.single()
    val compound = if (firstProduct.formula == precipitate.formula) firstProduct else secondProduct
    return formatIonTerm(compound.cationCount, compound.cation) +
        " + " +
        formatIonTerm(compound.anionCount, compound.anion) +
        " ⟶ " +
        precipitate.formula +
        precipitate.stateSuffix()
}

private fun BalancedTerm.formatTerm(): String =
    if (coefficient == 1) formula else "$coefficient$formula"

private fun PrecipitationProduct.stateSuffix(): String =
    when (solubility.state) {
        SolubilityState.SOLUBLE -> "(aq)"
        SolubilityState.INSOLUBLE -> "(s)"
        SolubilityState.SLIGHTLY_SOLUBLE -> "(s)"
        SolubilityState.UNKNOWN -> "(?)"
    }

private fun formatIonTerm(count: Int, ion: CommonIon): String {
    val coefficient = if (count > 1) count.toString() else ""
    return coefficient + ion.formula + ion.chargeText()
}

private fun CommonIon.chargeText(): String {
    val sign = if (charge > 0) "+" else "-"
    val magnitude = abs(charge)
    if (magnitude == 1) return sign
    val needsCaret = counts.size > 1 || formula.any { it.isDigit() }
    return if (needsCaret) "^$magnitude$sign" else "$magnitude$sign"
}

private fun gcd(a: Int, b: Int): Int =
    if (b == 0) abs(a) else gcd(b, a % b)

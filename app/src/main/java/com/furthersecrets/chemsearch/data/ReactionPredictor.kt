package com.furthersecrets.chemsearch.data

import androidx.annotation.StringRes
import com.furthersecrets.chemsearch.R

data class PredictedProduct(
    val formula: String,
    val noteRes: Int? = null
)

data class ReactionPredictionResult(
    val reactants: List<String> = emptyList(),
    val products: List<PredictedProduct> = emptyList(),
    val balancedEquation: String = "",
    val balanced: BalancedReactionResult? = null,
    val confidence: ReactionPredictor.Confidence = ReactionPredictor.Confidence.LOW,
    val ruleLabelRes: Int? = null,
    val matchedDbEntryTitle: String? = null,
    val observation: String? = null,
    val conditions: String? = null,
    @StringRes val errorRes: Int? = null,
    val errorArgs: List<Any> = emptyList()
)

/**
 * Predicts reaction products for a pair of reactants using two complementary
 * strategies:
 *
 * 1. Exact match against the bundled Chemical Database reaction entries, where
 *    both entered reactants appear on the left side of a known equation.
 * 2. A general rule engine for common inorganic patterns (neutralization,
 *    acid + carbonate, acid + metal, metal + salt via the activity series,
 *    double displacement via ion swapping, and synthesis/combustion).
 *
 * Every predicted equation is balanced with [balanceChemicalReaction] so the
 * result is always a valid, atoms-conserved equation.
 */
object ReactionPredictor {

    /** Confidence in a prediction; drives the UI summary pill. */
    enum class Confidence {
        /** Both reactants matched a curated database entry verbatim. */
        DATABASE,
        /** A general rule fired (ion swap, activity series, combustion...). */
        RULE,
        /** No rule matched; only a tentative guess is offered. */
        LOW
    }

    /**
     * @param dbReactions normalized database entries (category REACTIONS) from
     *   [ChemicalDatabase.load]; kept as a parameter so the engine stays pure
     *   and unit-testable without Android assets.
     */
    fun predict(
        inputs: List<String>,
        dbReactions: List<ChemicalDbEntry>
    ): ReactionPredictionResult {
        val cleaned = inputs.map { it.trim() }.filter { it.isNotEmpty() }
        if (cleaned.size < 2) {
            return ReactionPredictionResult(errorRes = R.string.ui_error_enter_two_reactants)
        }

        // 1. Exact database match: the inputs appear on the reactant side of a curated entry.
        dbMatch(cleaned, dbReactions)?.let { return it }

        // 2. General rule engine (two reactants only).
        if (cleaned.size == 2) {
            ruleEngine(cleaned[0], cleaned[1])?.let { return it }
        }

        return ReactionPredictionResult(
            reactants = cleaned,
            errorRes = R.string.ui_error_no_prediction_available
        )
    }

    /** Convenience overload for the common two-reactant case. */
    fun predict(
        firstInput: String,
        secondInput: String,
        dbReactions: List<ChemicalDbEntry>
    ): ReactionPredictionResult = predict(listOf(firstInput, secondInput), dbReactions)

    // ------------------------------------------------------------------ //
    // Database matching
    // ------------------------------------------------------------------ //

    private fun dbMatch(
        inputs: List<String>,
        entries: List<ChemicalDbEntry>
    ): ReactionPredictionResult? {
        val reactions = entries.filter { it.category == ChemicalDbCategory.REACTIONS }
        if (reactions.isEmpty()) return null
        val wanted = inputs.map(::normalizeSpecies)
        val equation = reactions.firstOrNull { entry ->
            val sides = entry.formula.split(ARROW_CHARS)
            if (sides.size != 2) return@firstOrNull false
            val reactants = splitSpecies(sides[0]).map(::normalizeSpecies)
            reactants.size == wanted.size && reactants.containsAll(wanted)
        } ?: return null

        val sides = equation.formula.split(ARROW_CHARS)
        val balanced = balanceChemicalReaction(sides.joinToString("->"))
        val productFormulas = splitSpecies(sides[1])
        return ReactionPredictionResult(
            reactants = inputs,
            products = productFormulas.map { PredictedProduct(it) },
            balancedEquation = if (balanced.error == null) balanced.displayEquation() else equation.formula,
            balanced = balanced,
            confidence = Confidence.DATABASE,
            ruleLabelRes = null,
            matchedDbEntryTitle = equation.title,
            observation = equation.summary.ifBlank { null },
            conditions = equation.sections
                .firstOrNull { it.title.equals("Typical conditions", ignoreCase = true) }
                ?.rows?.firstOrNull()?.value?.takeIf { it.isNotBlank() }
        )
    }

    // ------------------------------------------------------------------ //
    // Rule engine
    // ------------------------------------------------------------------ //

    private fun ruleEngine(first: String, second: String): ReactionPredictionResult? {
        // Ammonium salt + hydroxide base -> ammonia (before neutralization).
        ammoniumSaltBase(first, second)?.let { return it }
        // Neutralization: identifiable acid + identifiable hydroxide base.
        acidBase(first, second)?.let { return it }
        // Acid + carbonate/bicarbonate -> salt + water + CO2.
        acidCarbonate(first, second)?.let { return it }
        // Acid + basic metal oxide -> salt + water.
        acidMetalOxide(first, second)?.let { return it }
        // Acid + sulfite -> salt + water + SO2.
        acidSulfite(first, second)?.let { return it }
        // Acid + sulfide -> salt + H2S.
        acidSulfide(first, second)?.let { return it }
        // Active metal + cold water -> hydroxide + H2.
        metalWater(first, second)?.let { return it }
        // Metal + acid -> salt + H2 (activity series).
        metalAcid(first, second)?.let { return it }
        // More active metal + salt solution -> displacement.
        metalSaltDisplacement(first, second)?.let { return it }
        // Combustion: organic CxHy(Oz) + O2 -> CO2 + H2O.
        combustion(first, second)?.let { return it }
        // Synthesis fallback: two elements -> binary compound.
        synthesis(first, second)?.let { return it }
        return null
    }

    private fun acidBase(first: String, second: String): ReactionPredictionResult? {
        val (acidRaw, baseRaw) = swapIfBaseFirst(first, second) { isAcidFormula(it) } ?: return null
        val acid = identifyCommonIonicCompound(acidRaw) ?: return null
        val base = identifyCommonIonicCompound(baseRaw) ?: return null
        // A real hydroxide base; exclude water (identifies as H+/OH-) and acids.
        if (base.anion.formula != "OH" || base.cation.formula == "H") return null

        val salt = buildIonicCompound(base.cation, acid.anion)
        val products = listOf(PredictedProduct(salt.formula), PredictedProduct("H2O", noteRes = R.string.ui_pred_note_neutralization))
        return assemble(
            reactants = listOf(acidRaw, baseRaw),
            productFormulas = listOf(salt.formula, "H2O"),
            products = products,
            confidence = Confidence.RULE,
            ruleLabelRes = R.string.ui_pred_rule_neutralization
        )
    }

    private fun ammoniumSaltBase(first: String, second: String): ReactionPredictionResult? {
        val (saltRaw, baseRaw) = swapIfBaseFirst(first, second) { isAmmoniumSalt(it) } ?: return null
        val base = identifyCommonIonicCompound(baseRaw) ?: return null
        if (base.anion.formula != "OH" || base.cation.formula == "H") return null

        // The base's cation pairs with the ammonium salt's anion (e.g. NaCl),
        // not with the ammonium ion itself.
        val saltMatch = identifyCommonIonicCompound(saltRaw)
            ?: runCatching { parseFormulaElementCounts(saltRaw) }.getOrNull()
                ?.takeIf { it["N"] == 1 && it["H"] == 4 }
                ?.let { CommonIon("NH4", 1, "ammonium") }
                ?.let { buildIonicCompound(it, CommonIon("Cl", -1, "chloride")) }
            ?: return null
        val salt = buildIonicCompound(base.cation, saltMatch.anion)
        val productFormulas = listOf(salt.formula, "NH3", "H2O")
        val products = listOf(
            PredictedProduct(salt.formula),
            PredictedProduct("NH3", noteRes = R.string.ui_pred_note_ammonia_gas),
            PredictedProduct("H2O")
        )
        return assemble(
            reactants = listOf(saltRaw, baseRaw),
            productFormulas = productFormulas,
            products = products,
            confidence = Confidence.RULE,
            ruleLabelRes = R.string.ui_pred_rule_ammonium_base
        )
    }

    private fun isAmmoniumSalt(raw: String): Boolean {
        val counts = runCatching { parseFormulaElementCounts(raw) }.getOrNull() ?: return false
        if (counts["N"] != 1 || counts["H"] != 4) return false
        val rest = counts.filterKeys { it != "N" && it != "H" }
        return rest.isNotEmpty()
    }

    private fun acidMetalOxide(first: String, second: String): ReactionPredictionResult? {
        val (oxideRaw, acidRaw) = swapIfBaseFirst(first, second) { isBasicOxide(it) } ?: return null
        val acid = identifyCommonIonicCompound(acidRaw) ?: return null
        val counts = runCatching { parseFormulaElementCounts(oxideRaw) }.getOrNull() ?: return null
        if (counts.size != 2) return null
        val metalSymbol = counts.keys.first { it != "O" }
        val metalFormula = metalSymbol.takeIf { it in METALS } ?: return null

        val salt = buildIonicCompound(metalFormula.asCation(), acid.anion)
        val productFormulas = listOf(salt.formula, "H2O")
        val products = listOf(
            PredictedProduct(salt.formula),
            PredictedProduct("H2O", noteRes = R.string.ui_pred_note_neutralization)
        )
        return assemble(
            reactants = listOf(oxideRaw, acidRaw),
            productFormulas = productFormulas,
            products = products,
            confidence = Confidence.RULE,
            ruleLabelRes = R.string.ui_pred_rule_acid_oxide
        )
    }

    private val BASIC_OXIDES = setOf("Na2O", "K2O", "Li2O", "CaO", "MgO", "BaO", "CuO", "ZnO", "FeO", "Fe2O3", "Al2O3")

    private fun isBasicOxide(raw: String): Boolean =
        BASIC_OXIDES.any { raw.equals(it, ignoreCase = true) }

    private fun acidSulfite(first: String, second: String): ReactionPredictionResult? {
        val (sulfiteRaw, acidRaw) = swapIfBaseFirst(first, second) { isSulfiteSalt(it) } ?: return null
        val acid = identifyCommonIonicCompound(acidRaw) ?: return null
        val sulfite = identifyCommonIonicCompound(sulfiteRaw) ?: return null
        if (sulfite.anion.formula != "SO3") return null

        val salt = buildIonicCompound(sulfite.cation, acid.anion)
        val productFormulas = listOf(salt.formula, "H2O", "SO2")
        val products = listOf(
            PredictedProduct(salt.formula),
            PredictedProduct("H2O"),
            PredictedProduct("SO2", noteRes = R.string.ui_pred_note_pungent_gas)
        )
        return assemble(
            reactants = listOf(sulfiteRaw, acidRaw),
            productFormulas = productFormulas,
            products = products,
            confidence = Confidence.RULE,
            ruleLabelRes = R.string.ui_pred_rule_acid_sulfite
        )
    }

    private fun isSulfiteSalt(raw: String): Boolean {
        val match = identifyCommonIonicCompound(raw) ?: return false
        return match.anion.formula == "SO3"
    }

    private fun acidSulfide(first: String, second: String): ReactionPredictionResult? {
        val (sulfideRaw, acidRaw) = swapIfBaseFirst(first, second) { isSulfideSalt(it) } ?: return null
        val acid = identifyCommonIonicCompound(acidRaw) ?: return null
        val sulfide = identifyCommonIonicCompound(sulfideRaw) ?: return null
        if (sulfide.anion.formula != "S") return null

        val salt = buildIonicCompound(sulfide.cation, acid.anion)
        val productFormulas = listOf(salt.formula, "H2S")
        val products = listOf(
            PredictedProduct(salt.formula),
            PredictedProduct("H2S", noteRes = R.string.ui_pred_note_rotten_egg)
        )
        return assemble(
            reactants = listOf(sulfideRaw, acidRaw),
            productFormulas = productFormulas,
            products = products,
            confidence = Confidence.RULE,
            ruleLabelRes = R.string.ui_pred_rule_acid_sulfide
        )
    }

    private fun isSulfideSalt(raw: String): Boolean {
        val match = identifyCommonIonicCompound(raw) ?: return false
        return match.anion.formula == "S"
    }

    private fun metalWater(first: String, second: String): ReactionPredictionResult? {
        val metal = metalForFormula(first) ?: metalForFormula(second) ?: return null
        val other = if (metalForFormula(first) != null) second else first
        if (!other.equals("H2O", ignoreCase = true)) return null
        if (metal !in WATER_REACTIVE_METALS) return null

        val isColdWaterMetal = metal in COLD_WATER_METALS
        val cation = metal.asCation()
        val productFormulas = if (isColdWaterMetal) {
            listOf(buildIonicCompound(cation, CommonIon("OH", -1, "hydroxide")).formula, "H2")
        } else {
            listOf(buildIonicCompound(cation, CommonIon("O", -2, "oxide")).formula, "H2")
        }
        val products = listOf(
            PredictedProduct(productFormulas[0]),
            PredictedProduct("H2", noteRes = R.string.ui_pred_note_hydrogen_gas)
        )
        return assemble(
            reactants = listOf(metal, "H2O"),
            productFormulas = productFormulas,
            products = products,
            confidence = Confidence.RULE,
            ruleLabelRes = if (isColdWaterMetal) R.string.ui_pred_rule_metal_water else R.string.ui_pred_rule_metal_steam
        )
 }

    private fun acidCarbonate(first: String, second: String): ReactionPredictionResult? {
        val (acidRaw, carbonateRaw) = swapIfBaseFirst(first, second) { isAcidFormula(it) } ?: return null
        val acid = identifyCommonIonicCompound(acidRaw) ?: return null
        val carbonate = identifyCommonIonicCompound(carbonateRaw)
        val cation = carbonate?.cation ?: bicarbonateCation(carbonateRaw) ?: return null
        if (carbonate != null && carbonate.anion.formula != "CO3") return null

        val salt = buildIonicCompound(cation, acid.anion)
        val productFormulas = listOf(salt.formula, "H2O", "CO2")
        val products = listOf(
            PredictedProduct(salt.formula),
            PredictedProduct("H2O"),
            PredictedProduct("CO2", noteRes = R.string.ui_pred_note_effervescence)
        )
        return assemble(
            reactants = listOf(acidRaw, carbonateRaw),
            productFormulas = productFormulas,
            products = products,
            confidence = Confidence.RULE,
            ruleLabelRes = R.string.ui_pred_rule_acid_carbonate
        )
    }

    private fun metalAcid(first: String, second: String): ReactionPredictionResult? {
        val (metalRaw, acidRaw) = swapIfBaseFirst(first, second) { !isAcidFormula(it) && isMetalFormula(it) } ?: return null
        val acid = identifyCommonIonicCompound(acidRaw) ?: return null
        // Water is not an acid here; the metal+water rule handles it.
        if (acidRaw.equals("H2O", ignoreCase = true)) return null
        val metal = metalForFormula(metalRaw) ?: return null
        if (metal !in ACID_REACTIVE_METALS) return null

        val salt = buildIonicCompound(metal.asCation(), acid.anion)
        val productFormulas = listOf(salt.formula, "H2")
        val products = listOf(
            PredictedProduct(salt.formula),
            PredictedProduct("H2", noteRes = R.string.ui_pred_note_hydrogen_gas)
        )
        return assemble(
            reactants = listOf(metalRaw, acidRaw),
            productFormulas = productFormulas,
            products = products,
            confidence = Confidence.RULE,
            ruleLabelRes = R.string.ui_pred_rule_metal_acid
        )
    }

    private fun metalSaltDisplacement(first: String, second: String): ReactionPredictionResult? {
        val metal = metalForFormula(first) ?: metalForFormula(second) ?: return null
        val saltRaw = if (metalForFormula(first) != null) second else first
        val salt = identifyCommonIonicCompound(saltRaw) ?: return null
        // The salt's metal must be a different element, and less active.
        val displaced = metalForFormula(salt.cation.formula) ?: return null
        if (displaced == metal) return null
        if (activityIndex(metal) >= activityIndex(displaced)) return null

        val newSalt = buildIonicCompound(metal.asCation(), salt.anion)
        val productFormulas = listOf(newSalt.formula, displaced)
        val products = listOf(
            PredictedProduct(newSalt.formula),
            PredictedProduct(displaced, noteRes = R.string.ui_pred_note_metal_deposited)
        )
        return assemble(
            reactants = listOf(first, second),
            productFormulas = productFormulas,
            products = products,
            confidence = Confidence.RULE,
            ruleLabelRes = R.string.ui_pred_rule_single_displacement
        )
    }

    private fun combustion(first: String, second: String): ReactionPredictionResult? {
        val fuel = listOf(first, second).firstOrNull { it != "O2" && isCombustible(it) } ?: return null
        if ("O2" !in listOf(first, second)) return null
        val counts = runCatching { parseFormulaElementCounts(fuel) }.getOrNull() ?: return null
        if (counts["C"] == null && counts["H"] == null) return null
        val c = counts["C"] ?: 0
        val h = counts["H"] ?: 0
        val productFormulas = buildList {
            if (c > 0) add("CO2")
            if (h > 0) add("H2O")
        }
        if (productFormulas.isEmpty()) return null
        val products = buildList {
            if (c > 0) add(PredictedProduct("CO2"))
            if (h > 0) add(PredictedProduct("H2O"))
        }
        return assemble(
            reactants = listOf(fuel, "O2"),
            productFormulas = productFormulas,
            products = products,
            confidence = Confidence.RULE,
            ruleLabelRes = R.string.ui_pred_rule_combustion
        )
    }

    private fun synthesis(first: String, second: String): ReactionPredictionResult? {
        val firstCounts = runCatching { parseFormulaElementCounts(first) }.getOrNull() ?: return null
        val secondCounts = runCatching { parseFormulaElementCounts(second) }.getOrNull() ?: return null
        // Elements only (single element each side), different elements.
        if (firstCounts.size != 1 || secondCounts.size != 1) return null
        val e1 = firstCounts.keys.first()
        val e2 = secondCounts.keys.first()
        if (e1 == e2) return null
        // Known binary product formulas for common pairs.
        val product = BINARY_PRODUCTS[e1 to e2] ?: BINARY_PRODUCTS[e2 to e1] ?: return null
        return assemble(
            reactants = listOf(first, second),
            productFormulas = listOf(product),
            products = listOf(PredictedProduct(product)),
            confidence = Confidence.RULE,
            ruleLabelRes = R.string.ui_pred_rule_synthesis
        )
    }

    // ------------------------------------------------------------------ //
    // Assembly + balancing
    // ------------------------------------------------------------------ //

    private fun assemble(
        reactants: List<String>,
        productFormulas: List<String>,
        products: List<PredictedProduct>,
        confidence: Confidence,
        @StringRes ruleLabelRes: Int
    ): ReactionPredictionResult {
        val raw = reactants.joinToString(" + ") + " -> " + productFormulas.joinToString(" + ")
        val balanced = balanceChemicalReaction(raw)
        val equation = if (balanced.error == null) balanced.displayEquation() else raw.replace("->", " ⟶ ")
        return ReactionPredictionResult(
            reactants = reactants,
            products = products,
            balancedEquation = equation,
            balanced = balanced,
            confidence = confidence,
            ruleLabelRes = ruleLabelRes
        )
    }

    // ------------------------------------------------------------------ //
    // Chemistry tables
    // ------------------------------------------------------------------ //

    private val ARROW_CHARS = Regex("[⟶→]")

    private val BICARBONATE_CATIONS = mapOf(
        "NaHCO3" to CommonIon("Na", 1, "sodium"),
        "KHCO3" to CommonIon("K", 1, "potassium"),
        "Ca(HCO3)2" to CommonIon("Ca", 2, "calcium"),
        "Mg(HCO3)2" to CommonIon("Mg", 2, "magnesium"),
        "NH4HCO3" to CommonIon("NH4", 1, "ammonium")
    )

    private fun bicarbonateCation(raw: String): CommonIon? =
        BICARBONATE_CATIONS.entries.firstOrNull { raw.equals(it.key, ignoreCase = true) }?.value

    private fun isAcidFormula(formula: String): Boolean =
        formula in setOf("HCl", "HBr", "HI", "HNO3", "H2SO4", "H3PO4", "CH3COOH", "H2CO3", "H2S", "HF")

    private val METALS = mapOf(
        "K" to 1, "Na" to 1, "Li" to 1, "Ba" to 2, "Ca" to 2, "Mg" to 2, "Al" to 3,
        "Zn" to 2, "Fe" to 2, "Fe2" to 2, "Cu" to 2, "Ag" to 1, "Pb" to 2, "Sn" to 2, "Hg" to 2
    )

    private fun isMetalFormula(formula: String): Boolean = formula in METALS

    private fun metalForFormula(formula: String): String? =
        METALS.keys.firstOrNull { formula.equals(it, ignoreCase = true) }

    private fun String.asCation(): CommonIon = when (this) {
        "K" -> CommonIon("K", 1, "potassium")
        "Na" -> CommonIon("Na", 1, "sodium")
        "Li" -> CommonIon("Li", 1, "lithium")
        "Ba" -> CommonIon("Ba", 2, "barium")
        "Ca" -> CommonIon("Ca", 2, "calcium")
        "Mg" -> CommonIon("Mg", 2, "magnesium")
        "Al" -> CommonIon("Al", 3, "aluminum")
        "Zn" -> CommonIon("Zn", 2, "zinc")
        "Fe" -> CommonIon("Fe", 2, "iron(II)")
        "Fe2" -> CommonIon("Fe", 3, "iron(III)")
        "Cu" -> CommonIon("Cu", 2, "copper(II)")
        "Ag" -> CommonIon("Ag", 1, "silver")
        "Pb" -> CommonIon("Pb", 2, "lead(II)")
        "Sn" -> CommonIon("Sn", 2, "tin(II)")
        "Hg" -> CommonIon("Hg2", 2, "mercury(I)")
        else -> CommonIon(this, 1, this)
    }

    /** Activity series index; lower = more reactive. */
    private val ACTIVITY_SERIES = listOf("K", "Na", "Li", "Ba", "Ca", "Mg", "Al", "Zn", "Fe", "Sn", "Pb", "H", "Cu", "Hg", "Ag")

    private fun activityIndex(metal: String): Int = ACTIVITY_SERIES.indexOf(metal)

    private val ACID_REACTIVE_METALS = setOf("K", "Na", "Li", "Ca", "Mg", "Al", "Zn", "Fe", "Sn", "Pb")

    /** React with cold water to form hydroxide + H2. */
    private val COLD_WATER_METALS = setOf("K", "Na", "Li", "Ba", "Ca")

    /** React with steam to form oxide + H2 (slow or no cold-water reaction). */
    private val WATER_REACTIVE_METALS = COLD_WATER_METALS + setOf("Mg")

    private val COMBUSTIBLE_ORGANICS = setOf(
        "CH4", "C2H6", "C3H8", "C4H10", "C2H4", "C2H2", "C3H6",
        "C2H5OH", "C6H6", "CH3OH", "C6H12O6", "C6H14", "C8H18", "C12H22O11"
    )

    private fun isCombustible(formula: String): Boolean = formula in COMBUSTIBLE_ORGANICS

    private val BINARY_PRODUCTS = mapOf(
        "H" to "O" to "H2O",
        "H" to "Cl" to "HCl",
        "H" to "N" to "NH3",
        "H" to "S" to "H2S",
        "Na" to "Cl" to "NaCl",
        "K" to "Cl" to "KCl",
        "Mg" to "O" to "MgO",
        "Ca" to "O" to "CaO",
        "Fe" to "O" to "Fe2O3",
        "Al" to "O" to "Al2O3",
        "C" to "O" to "CO2",
        "S" to "O" to "SO2",
        "N" to "O" to "NO2",
        "Cu" to "O" to "CuO",
        "Zn" to "O" to "ZnO"
    )

    // ------------------------------------------------------------------ //
    // Small helpers
    // ------------------------------------------------------------------ //

    private fun normalizeSpecies(value: String): String =
        value.replace(" ", "").replace("^", "").uppercase()

    private fun splitSpecies(side: String): List<String> =
        side.split("+")
            .map { it.trim().dropWhile { ch -> ch.isDigit() }.trim() }
            .filter { it.isNotBlank() && !it.equals("heat", ignoreCase = true) }

    private inline fun swapIfBaseFirst(
        first: String,
        second: String,
        isTarget: (String) -> Boolean
    ): Pair<String, String>? = when {
        isTarget(first) && !isTarget(second) -> first to second
        isTarget(second) && !isTarget(first) -> second to first
        else -> null
    }
}

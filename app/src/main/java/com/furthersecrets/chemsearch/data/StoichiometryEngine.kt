package com.furthersecrets.chemsearch.data

import kotlin.math.abs

/**
 * Pure stoichiometry engine shared by the Limiting Reagent, Percent Yield and
 * Reaction Scaling tools. Operates on balanced reactions (formula -> coefficient)
 * and per-reactant mole amounts; all results are plain data so they can be
 * unit-tested and rendered by any UI.
 */
object StoichiometryEngine {

    /** Amount units supported by the stoichiometry tool inputs. */
    enum class StoichUnit(val label: String) {
        GRAMS("g"),
        KILOGRAMS("kg"),
        MOLES("mol"),
        MILLIMOLES("mmol"),
        LITERS_GAS("L gas"),
        MILLILITERS_GAS("mL gas"),
        MOLARITY("M (mol/L)"),
        PARTICLES("particles x10^23")
    }

    private const val ENGINE_AVOGADRO = 6.02214076e23

    /** Converts an amount expressed in [unit] to moles. Returns null when the unit needs molar data that is missing. */
    fun amountToMoles(amount: Double, unit: StoichUnit, molarMass: Double?, molarVolume: Double): Double? = when (unit) {
        StoichUnit.GRAMS -> molarMass?.let { amount / it }
        StoichUnit.KILOGRAMS -> molarMass?.let { (amount * 1000.0) / it }
        StoichUnit.MOLES -> amount
        StoichUnit.MILLIMOLES -> amount / 1000.0
        StoichUnit.LITERS_GAS -> amount / molarVolume
        StoichUnit.MILLILITERS_GAS -> (amount / 1000.0) / molarVolume
        StoichUnit.PARTICLES -> (amount * 1e23) / ENGINE_AVOGADRO
        StoichUnit.MOLARITY -> null
    }

    /** Converts moles into an amount in [unit]. Returns null when the unit needs molar data that is missing. */
    fun molesToAmount(moles: Double, unit: StoichUnit, molarMass: Double?, molarVolume: Double): Double? = when (unit) {
        StoichUnit.GRAMS -> molarMass?.let { moles * it }
        StoichUnit.KILOGRAMS -> molarMass?.let { (moles * it) / 1000.0 }
        StoichUnit.MOLES -> moles
        StoichUnit.MILLIMOLES -> moles * 1000.0
        StoichUnit.LITERS_GAS -> moles * molarVolume
        StoichUnit.MILLILITERS_GAS -> (moles * molarVolume) * 1000.0
        StoichUnit.PARTICLES -> (moles * ENGINE_AVOGADRO) / 1e23
        StoichUnit.MOLARITY -> null
    }

    /** Formats a converted amount for an input field (max 6 decimals, trailing zeros trimmed). */
    fun formatConversion(value: Double): String {
        val rounded = kotlin.math.round(value * 1e6) / 1e6
        return if (rounded == kotlin.math.floor(rounded)) {
            rounded.toLong().toString()
        } else {
            "%.6f".format(rounded).trimEnd('0').trimEnd('.')
        }
    }

    /** Amount of one species: moles plus an optional mass (grams) view. */
    data class SpeciesAmount(
        val moles: Double,
        val grams: Double? = null
    )

    /** Per-reactant consumption summary relative to the limiting reagent. */
    data class ReactantConsumption(
        val formula: String,
        val availableMoles: Double,
        val consumedMoles: Double,
        val leftoverMoles: Double,
        /** 0..1, fraction of what was available that gets consumed. */
        val consumedFraction: Double,
        val isLimiting: Boolean,
        val leftoverGrams: Double? = null
    )

    /** Theoretical yield row for one product. */
    data class ProductYield(
        val formula: String,
        val moles: Double,
        val grams: Double? = null
    )

    data class Analysis(
        val limitingFormula: String,
        val limitingMoles: Double,
        val limitingGrams: Double?,
        val extent: Double,
        val reactants: List<ReactantConsumption>,
        val products: List<ProductYield>,
        /** Atom economy of the target reaction in percent (mass of desired product over mass of all products). */
        val atomEconomyPercent: Double?
    )

    /**
     * Analyzes a fully specified reaction.
     *
     * @param reactantAmounts formula -> available amount (moles, with optional grams)
     * @param productMolarMasses formula -> molar mass; when present, gram views are filled
     */
    fun analyze(
        reactants: List<Pair<String, Int>>,
        products: List<Pair<String, Int>>,
        reactantAmounts: Map<String, SpeciesAmount>,
        molarMasses: Map<String, Double?> = emptyMap()
    ): Analysis? {
        if (reactants.isEmpty() || products.isEmpty()) return null

        val missing = reactants.firstOrNull { (formula, _) -> reactantAmounts[formula] == null }
        if (missing != null) return null

        val limitingEntry = reactants
            .map { (formula, coeff) -> Triple(formula, coeff, reactantAmounts.getValue(formula).moles / coeff) }
            .minByOrNull { it.third } ?: return null

        val extent = limitingEntry.third
        val limitingFormula = limitingEntry.first
        val limitingCoeff = limitingEntry.second

        val gramOf: (String, Double) -> Double? = { formula, moles ->
            molarMasses[formula]?.takeIf { it != null && it > 0.0 }?.let { it * moles }
        }

        val consumption = reactants.map { (formula, coeff) ->
            val available = reactantAmounts.getValue(formula)
            val used = extent * coeff
            val leftover = (available.moles - used).coerceAtLeast(0.0)
            ReactantConsumption(
                formula = formula,
                availableMoles = available.moles,
                consumedMoles = used,
                leftoverMoles = leftover,
                consumedFraction = if (available.moles > 0.0) (used / available.moles).coerceIn(0.0, 1.0) else 1.0,
                isLimiting = formula == limitingFormula,
                leftoverGrams = gramOf(formula, leftover)
            )
        }

        val yields = products.map { (formula, coeff) ->
            val moles = extent * coeff
            ProductYield(
                formula = formula,
                moles = moles,
                grams = gramOf(formula, moles)
            )
        }

        // Atom economy: mass fraction of reactants that ends up in products.
        val reactantMass = reactants.sumOf { (formula, coeff) ->
            val mm = molarMasses[formula]
            if (mm != null && mm > 0.0) mm * coeff else 0.0
        }
        val atomEconomy = if (reactantMass > 0.0) {
            val productMass = products.sumOf { (formula, coeff) ->
                val mm = molarMasses[formula]
                if (mm != null && mm > 0.0) mm * coeff else 0.0
            }
            if (productMass > 0.0) (productMass / reactantMass) * 100.0 else null
        } else {
            null
        }

        return Analysis(
            limitingFormula = limitingFormula,
            limitingMoles = reactantAmounts.getValue(limitingFormula).moles,
            limitingGrams = reactantAmounts.getValue(limitingFormula).grams ?: gramOf(limitingFormula, reactantAmounts.getValue(limitingFormula).moles),
            extent = extent,
            reactants = consumption,
            products = yields,
            atomEconomyPercent = atomEconomy
        )
    }

    /** Theoretical yield of a single target product for the given extent. */
    fun theoreticalYield(
        products: List<Pair<String, Int>>,
        extent: Double,
        targetFormula: String,
        molarMasses: Map<String, Double?> = emptyMap()
    ): ProductYield? {
        val target = products.firstOrNull { (formula, _) -> formula == targetFormula } ?: return null
        val moles = extent * target.second
        val mm = molarMasses[targetFormula]
        return ProductYield(
            formula = targetFormula,
            moles = moles,
            grams = if (mm != null && mm > 0.0) mm * moles else null
        )
    }

    /** Percent yield in 0..∞; returns null when inputs are not usable. */
    fun percentYield(actualMoles: Double?, theoreticalMoles: Double?): Double? {
        if (actualMoles == null || theoreticalMoles == null) return null
        if (theoreticalMoles <= 0.0 || actualMoles < 0.0) return null
        return (actualMoles / theoreticalMoles) * 100.0
    }

    /**
     * Scaling: required reactant amounts to reach [desiredProductMoles] of the target product.
     * Returns null when the target product is not in the reaction.
     */
    fun scalingRequirements(
        reactants: List<Pair<String, Int>>,
        products: List<Pair<String, Int>>,
        desiredProductMoles: Double,
        targetFormula: String,
        molarMasses: Map<String, Double?> = emptyMap()
    ): List<Pair<String, SpeciesAmount>>? {
        val extent = requiredExtent(products, targetFormula, desiredProductMoles) ?: return null
        return requiredReactants(reactants, extent, molarMasses)
    }

    /** Required extent to obtain [desiredProductMoles] of [targetFormula]. */
    fun requiredExtent(
        products: List<Pair<String, Int>>,
        targetFormula: String,
        desiredProductMoles: Double
    ): Double? {
        if (desiredProductMoles <= 0.0) return null
        val coeff = products.firstOrNull { (formula, _) -> formula == targetFormula }?.second ?: return null
        return desiredProductMoles / coeff
    }

    /** Required moles (and grams) of each reactant for the given extent. */
    fun requiredReactants(
        reactants: List<Pair<String, Int>>,
        extent: Double,
        molarMasses: Map<String, Double?> = emptyMap()
    ): List<Pair<String, SpeciesAmount>> =
        reactants.map { (formula, coeff) ->
            val moles = extent * coeff
            val mm = molarMasses[formula]
            formula to SpeciesAmount(moles, if (mm != null && mm > 0.0) mm * moles else null)
        }

    /** True when the reaction is balanced within floating-point tolerance. */
    fun isBalanced(
        reactants: List<Pair<String, Int>>,
        products: List<Pair<String, Int>>
    ): Boolean {
        val left = reactants + products.map { (f, c) -> f to -c }
        val counts = linkedMapOf<String, Int>()
        for ((formula, coeff) in left) {
            val composition = runCatching { parseFormulaElementCounts(formula) }.getOrNull() ?: return false
            composition.forEach { (element, count) ->
                counts[element] = (counts[element] ?: 0) + count * coeff
            }
        }
        return counts.values.all { abs(it) <= 0 }
    }
}

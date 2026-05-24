package com.furthersecrets.chemsearch.data

data class MolarMassBreakdown(
    val element: String,
    val count: Int,
    val contribution: Double
)

data class MolarMassResult(
    val molarMass: Double,
    val breakdown: List<MolarMassBreakdown>,
    val error: String? = null
)

data class ElementalPercentage(
    val element: String,
    val percentage: Double
)

val STANDARD_ATOMIC_WEIGHTS: Map<String, Double> = mapOf(
    "H" to 1.008, "He" to 4.003, "Li" to 6.941, "Be" to 9.012, "B" to 10.811,
    "C" to 12.011, "N" to 14.007, "O" to 15.999, "F" to 18.998, "Ne" to 20.180,
    "Na" to 22.990, "Mg" to 24.305, "Al" to 26.982, "Si" to 28.086, "P" to 30.974,
    "S" to 32.065, "Cl" to 35.453, "Ar" to 39.948, "K" to 39.098, "Ca" to 40.078,
    "Sc" to 44.956, "Ti" to 47.867, "V" to 50.942, "Cr" to 51.996, "Mn" to 54.938,
    "Fe" to 55.845, "Co" to 58.933, "Ni" to 58.693, "Cu" to 63.546, "Zn" to 65.38,
    "Ga" to 69.723, "Ge" to 72.630, "As" to 74.922, "Se" to 78.971, "Br" to 79.904,
    "Kr" to 83.798, "Rb" to 85.468, "Sr" to 87.620, "Y" to 88.906, "Zr" to 91.224,
    "Nb" to 92.906, "Mo" to 95.950, "Tc" to 98.0, "Ru" to 101.07, "Rh" to 102.91,
    "Pd" to 106.42, "Ag" to 107.87, "Cd" to 112.41, "In" to 114.82, "Sn" to 118.71,
    "Sb" to 121.76, "Te" to 127.60, "I" to 126.90, "Xe" to 131.29, "Cs" to 132.91,
    "Ba" to 137.33, "La" to 138.91, "Ce" to 140.12, "Pr" to 140.91, "Nd" to 144.24,
    "Pm" to 145.0, "Sm" to 150.36, "Eu" to 151.96, "Gd" to 157.25, "Tb" to 158.93,
    "Dy" to 162.50, "Ho" to 164.93, "Er" to 167.26, "Tm" to 168.93, "Yb" to 173.05,
    "Lu" to 174.97, "Hf" to 178.49, "Ta" to 180.95, "W" to 183.84, "Re" to 186.21,
    "Os" to 190.23, "Ir" to 192.22, "Pt" to 195.08, "Au" to 196.97, "Hg" to 200.59,
    "Tl" to 204.38, "Pb" to 207.20, "Bi" to 208.98, "Po" to 209.0, "At" to 210.0,
    "Rn" to 222.0, "Fr" to 223.0, "Ra" to 226.0, "Ac" to 227.0, "Th" to 232.04,
    "Pa" to 231.04, "U" to 238.03, "Np" to 237.0, "Pu" to 244.0, "Am" to 243.0,
    "Cm" to 247.0, "Bk" to 247.0, "Cf" to 251.0, "Es" to 252.0, "Fm" to 257.0,
    "Md" to 258.0, "No" to 259.0, "Lr" to 262.0, "Rf" to 267.0, "Db" to 270.0,
    "Sg" to 271.0, "Bh" to 270.0, "Hs" to 277.0, "Mt" to 276.0, "Ds" to 281.0,
    "Rg" to 280.0, "Cn" to 285.0, "Nh" to 284.0, "Fl" to 289.0, "Mc" to 288.0,
    "Lv" to 293.0, "Ts" to 294.0, "Og" to 294.0
)

fun calculateMolarMass(formula: String): MolarMassResult {
    if (formula.isBlank()) return MolarMassResult(0.0, emptyList(), "Enter a formula")

    val elements = try {
        parseFormulaElementCounts(formula)
    } catch (error: FormulaParseException) {
        return MolarMassResult(0.0, emptyList(), error.parseError.message)
    }

    val unknown = elements.keys.filter { it !in STANDARD_ATOMIC_WEIGHTS }
    if (unknown.isNotEmpty()) {
        return MolarMassResult(0.0, emptyList(), "Unknown element(s): ${unknown.joinToString(", ")}")
    }

    var total = 0.0
    val breakdown = elements.map { (element, count) ->
        val contribution = STANDARD_ATOMIC_WEIGHTS.getValue(element) * count
        total += contribution
        MolarMassBreakdown(element, count, contribution)
    }.sortedByDescending { it.contribution }

    return MolarMassResult(total, breakdown)
}

fun calculateEmpiricalFormula(formula: String): String {
    if (formula.isBlank()) return ""
    val elements = try {
        parseFormulaElementCounts(formula)
    } catch (_: FormulaParseException) {
        return formula
    }
    if (elements.isEmpty()) return formula
    val divisor = elements.values.reduce { a, b -> gcd(a, b) }
    return elements.entries.joinToString("") { (element, count) ->
        element + ((count / divisor).takeIf { it > 1 }?.toString() ?: "")
    }
}

fun calculateElementalPercentages(formula: String): List<ElementalPercentage> {
    val mass = calculateMolarMass(formula)
    if (mass.error != null || mass.molarMass == 0.0) return emptyList()
    return mass.breakdown.map { item ->
        ElementalPercentage(item.element, item.contribution / mass.molarMass * 100.0)
    }.sortedByDescending { it.percentage }
}

private fun gcd(a: Int, b: Int): Int =
    if (b == 0) kotlin.math.abs(a) else gcd(b, a % b)

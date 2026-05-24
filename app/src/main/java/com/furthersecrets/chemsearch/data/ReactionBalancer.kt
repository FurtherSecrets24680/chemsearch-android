package com.furthersecrets.chemsearch.data

enum class ReactionBalanceErrorCode {
    MISSING_ARROW,
    MISSING_REACTANTS,
    MISSING_PRODUCTS,
    INVALID_FORMULA,
    UNSOLVABLE
}

data class ReactionBalanceError(
    val code: ReactionBalanceErrorCode,
    val message: String
)

data class BalancedTerm(
    val formula: String,
    val coefficient: Int
)

data class ReactionVerificationRow(
    val element: String,
    val reactantCount: Int,
    val productCount: Int
)

data class BalancedReactionResult(
    val reactants: List<BalancedTerm> = emptyList(),
    val products: List<BalancedTerm> = emptyList(),
    val verificationRows: List<ReactionVerificationRow> = emptyList(),
    val error: ReactionBalanceError? = null
) {
    fun displayEquation(): String {
        if (error != null) return ""
        fun termText(term: BalancedTerm): String =
            if (term.coefficient == 1) term.formula else "${term.coefficient}${term.formula}"
        return reactants.joinToString(" + ", transform = ::termText) +
            " -> " +
            products.joinToString(" + ", transform = ::termText)
    }
}

fun balanceChemicalReaction(equation: String): BalancedReactionResult {
    val parts = equation.replace("→", "->").replace("⟶", "->").replace("=>", "->").split("->")
    if (parts.size != 2) {
        return BalancedReactionResult(error = ReactionBalanceError(ReactionBalanceErrorCode.MISSING_ARROW, "Use '->' between reactants and products."))
    }

    val reactants = splitReactionSide(parts[0])
    val products = splitReactionSide(parts[1])
    if (reactants.isEmpty()) {
        return BalancedReactionResult(error = ReactionBalanceError(ReactionBalanceErrorCode.MISSING_REACTANTS, "No reactants found."))
    }
    if (products.isEmpty()) {
        return BalancedReactionResult(error = ReactionBalanceError(ReactionBalanceErrorCode.MISSING_PRODUCTS, "No products found."))
    }

    val formulas = reactants + products
    val parsed = formulas.map { formula ->
        runCatching { parseFormulaElementCounts(formula) }.getOrElse {
            return BalancedReactionResult(
                error = ReactionBalanceError(
                    ReactionBalanceErrorCode.INVALID_FORMULA,
                    "Cannot parse formula: $formula"
                )
            )
        }
    }

    val elements = parsed.flatMap { it.keys }.distinct().sorted()
    if (elements.isEmpty()) {
        return BalancedReactionResult(error = ReactionBalanceError(ReactionBalanceErrorCode.INVALID_FORMULA, "No elements found."))
    }

    val matrix = Array(elements.size) { row ->
        val element = elements[row]
        Array(formulas.size) { column ->
            val sign = if (column >= reactants.size) -1 else 1
            Fraction.of((parsed[column][element] ?: 0).toLong() * sign)
        }
    }

    for (freeIndex in formulas.indices.reversed()) {
        val solution = tryBalance(matrix, elements.size, formulas.size, freeIndex) ?: continue
        if (solution.all { it > 0 }) {
            val balancedReactants = reactants.mapIndexed { index, formula -> BalancedTerm(formula, solution[index]) }
            val balancedProducts = products.mapIndexed { index, formula -> BalancedTerm(formula, solution[reactants.size + index]) }
            return BalancedReactionResult(
                reactants = balancedReactants,
                products = balancedProducts,
                verificationRows = buildVerificationRows(elements, parsed, solution, reactants.size)
            )
        }
    }

    return BalancedReactionResult(
        error = ReactionBalanceError(
            ReactionBalanceErrorCode.UNSOLVABLE,
            "Could not balance this equation. Check that all elements appear on both sides."
        )
    )
}

private fun splitReactionSide(side: String): List<String> =
    side.split("+")
        .map { stripLeadingCoefficient(it.trim()) }
        .filter { it.isNotBlank() }

private fun stripLeadingCoefficient(value: String): String =
    value.dropWhile { it.isDigit() }.trim()

private fun buildVerificationRows(
    elements: List<String>,
    parsed: List<Map<String, Int>>,
    coefficients: List<Int>,
    reactantCount: Int
): List<ReactionVerificationRow> =
    elements.map { element ->
        val left = parsed.take(reactantCount).mapIndexed { index, formula ->
            (formula[element] ?: 0) * coefficients[index]
        }.sum()
        val right = parsed.drop(reactantCount).mapIndexed { index, formula ->
            (formula[element] ?: 0) * coefficients[reactantCount + index]
        }.sum()
        ReactionVerificationRow(element, left, right)
    }

private data class Fraction(val numerator: Long, val denominator: Long = 1L) {
    init {
        require(denominator != 0L)
    }

    companion object {
        fun of(value: Long) = Fraction(value, 1L)
        fun zero() = Fraction(0L, 1L)
        fun gcd(a: Long, b: Long): Long = if (b == 0L) kotlin.math.abs(a) else gcd(b, a % b)
        fun lcm(a: Long, b: Long): Long = a / gcd(a, b) * b
    }

    fun isZero(): Boolean = numerator == 0L

    operator fun unaryMinus(): Fraction = Fraction(-numerator, denominator)

    operator fun plus(other: Fraction): Fraction =
        Fraction(numerator * other.denominator + other.numerator * denominator, denominator * other.denominator).reduced()

    operator fun minus(other: Fraction): Fraction =
        Fraction(numerator * other.denominator - other.numerator * denominator, denominator * other.denominator).reduced()

    operator fun times(other: Fraction): Fraction =
        Fraction(numerator * other.numerator, denominator * other.denominator).reduced()

    operator fun div(other: Fraction): Fraction {
        if (other.numerator == 0L) throw ArithmeticException("Division by zero")
        return Fraction(numerator * other.denominator, denominator * other.numerator).reduced()
    }

    private fun reduced(): Fraction {
        if (numerator == 0L) return Fraction(0L, 1L)
        val gcd = gcd(kotlin.math.abs(numerator), kotlin.math.abs(denominator))
        return if (denominator < 0) {
            Fraction(-numerator / gcd, -denominator / gcd)
        } else {
            Fraction(numerator / gcd, denominator / gcd)
        }
    }
}

private fun tryBalance(matrix: Array<Array<Fraction>>, rowCount: Int, columnCount: Int, freeIndex: Int): List<Int>? {
    val unknownCount = columnCount - 1
    val columnOrder = (0 until columnCount).filter { it != freeIndex }
    val augmented = Array(rowCount) { row ->
        Array(unknownCount + 1) { column ->
            if (column < unknownCount) matrix[row][columnOrder[column]] else -matrix[row][freeIndex]
        }
    }

    var pivotRow = 0
    val pivotForColumn = IntArray(unknownCount) { -1 }
    for (column in 0 until unknownCount) {
        val found = (pivotRow until rowCount).firstOrNull { row -> !augmented[row][column].isZero() } ?: return null
        if (found != pivotRow) {
            val temp = augmented[pivotRow]
            augmented[pivotRow] = augmented[found]
            augmented[found] = temp
        }

        val pivot = augmented[pivotRow][column]
        if (pivot.isZero()) return null
        for (entry in 0..unknownCount) augmented[pivotRow][entry] = augmented[pivotRow][entry] / pivot
        for (row in 0 until rowCount) {
            if (row != pivotRow && !augmented[row][column].isZero()) {
                val factor = augmented[row][column]
                for (entry in 0..unknownCount) {
                    augmented[row][entry] = augmented[row][entry] - factor * augmented[pivotRow][entry]
                }
            }
        }
        pivotForColumn[column] = pivotRow
        pivotRow++
    }

    val nonFree = List(unknownCount) { column ->
        val row = pivotForColumn[column]
        if (row < 0) return null
        augmented[row][unknownCount]
    }
    val full = MutableList(columnCount) { Fraction.zero() }
    full[freeIndex] = Fraction.of(1L)
    columnOrder.forEachIndexed { index, originalColumn -> full[originalColumn] = nonFree[index] }
    if (full.any { it.numerator < 0 }) return null

    val lcm = full.map { it.denominator }.fold(1L) { acc, value -> Fraction.lcm(acc, value) }
    val scaled = full.map { (it * Fraction.of(lcm)).numerator }
    if (scaled.any { it <= 0 }) return null
    val gcd = scaled.map { kotlin.math.abs(it) }.fold(0L) { acc, value -> Fraction.gcd(acc, value) }
    if (gcd == 0L) return null
    return scaled.map { (it / gcd).toInt() }
}

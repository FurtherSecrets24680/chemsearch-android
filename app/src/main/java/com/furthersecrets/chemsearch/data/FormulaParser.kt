package com.furthersecrets.chemsearch.data

data class FormulaComposition(
    val elements: Map<String, Int>
)

enum class FormulaParseErrorCode {
    EMPTY_FORMULA,
    INVALID_CHARACTER,
    INVALID_MULTIPLIER,
    UNCLOSED_GROUP,
    UNMATCHED_GROUP
}

data class FormulaParseError(
    val code: FormulaParseErrorCode,
    val message: String,
    val position: Int? = null,
    val token: String? = null
)

sealed interface FormulaParseResult {
    data class Success(val composition: FormulaComposition) : FormulaParseResult
    data class Failure(val error: FormulaParseError) : FormulaParseResult
}

class FormulaParseException(val parseError: FormulaParseError) : IllegalArgumentException(parseError.message)

private val superscriptDigits = mapOf(
    '⁰' to '0',
    '¹' to '1',
    '²' to '2',
    '³' to '3',
    '⁴' to '4',
    '⁵' to '5',
    '⁶' to '6',
    '⁷' to '7',
    '⁸' to '8',
    '⁹' to '9'
)

private val subscriptDigits = mapOf(
    '₀' to '0',
    '₁' to '1',
    '₂' to '2',
    '₃' to '3',
    '₄' to '4',
    '₅' to '5',
    '₆' to '6',
    '₇' to '7',
    '₈' to '8',
    '₉' to '9'
)

fun parseFormula(formula: String): FormulaParseResult {
    val normalized = stripCharge(normalizeFormulaText(formula))
    if (normalized.isBlank()) {
        return FormulaParseResult.Failure(
            FormulaParseError(
                code = FormulaParseErrorCode.EMPTY_FORMULA,
                message = "Enter a formula"
            )
        )
    }

    val total = linkedMapOf<String, Int>()
    val parts = normalized
        .split(Regex("[\\u00b7\\u2022*.]"))
        .filter { it.isNotBlank() }
    if (parts.isEmpty()) {
        return FormulaParseResult.Failure(
            FormulaParseError(
                code = FormulaParseErrorCode.EMPTY_FORMULA,
                message = "Enter a formula"
            )
        )
    }

    for (part in parts) {
        val (multiplier, body) = readLeadingMultiplier(part)
            ?: return FormulaParseResult.Failure(
                FormulaParseError(
                    code = FormulaParseErrorCode.INVALID_MULTIPLIER,
                    message = "Invalid leading multiplier",
                    token = part
                )
            )
        if (body.isBlank()) {
            return FormulaParseResult.Failure(
                FormulaParseError(
                    code = FormulaParseErrorCode.INVALID_MULTIPLIER,
                    message = "Multiplier must be followed by a formula",
                    token = part
                )
            )
        }
        val parsed = parseSequence(body, 0, null)
        if (parsed.error != null) return FormulaParseResult.Failure(parsed.error)
        if (parsed.index != body.length) {
            return FormulaParseResult.Failure(
                FormulaParseError(
                    code = FormulaParseErrorCode.INVALID_CHARACTER,
                    message = "Could not parse formula near '${body[parsed.index]}'",
                    position = parsed.index,
                    token = body[parsed.index].toString()
                )
            )
        }
        parsed.elements.forEach { (element, count) ->
            total[element] = (total[element] ?: 0) + count * multiplier
        }
    }

    if (total.isEmpty()) {
        return FormulaParseResult.Failure(
            FormulaParseError(
                code = FormulaParseErrorCode.EMPTY_FORMULA,
                message = "Could not parse formula"
            )
        )
    }
    return FormulaParseResult.Success(FormulaComposition(total))
}

fun parseFormulaElementCounts(formula: String): Map<String, Int> =
    when (val result = parseFormula(formula)) {
        is FormulaParseResult.Success -> result.composition.elements
        is FormulaParseResult.Failure -> throw FormulaParseException(result.error)
    }

fun formatConventionalFormula(formula: String): String {
    val cleanFormula = normalizeFormulaText(formula)
    if (cleanFormula.isBlank()) return formula
    if (cleanFormula.any { it == '(' || it == ')' || it == '[' || it == ']' || it == '.' || it == '*' || it == '\u00b7' || it == '\u2022' }) {
        return formula
    }

    val counts = runCatching { parseFormulaElementCounts(cleanFormula) }.getOrNull() ?: return formula
    if (counts.size <= 1) return cleanFormula

    val hasCarbon = counts.containsKey("C")
    val hasMetal = counts.keys.any { it in metalElements }
    val carbonCount = counts["C"] ?: 0

    val orderedElements = when {
        hasMetal && (!hasCarbon || carbonCount == 1) -> metalFirstFormulaOrder(counts)
        !hasCarbon -> inorganicFormulaOrder(counts)
        else -> counts.keys.toList()
    }

    return buildFormula(orderedElements, counts)
}

private data class ParseOutput(
    val elements: MutableMap<String, Int> = linkedMapOf(),
    val index: Int,
    val error: FormulaParseError? = null
)

private val metalElements = setOf(
    "Li", "Na", "K", "Rb", "Cs", "Fr",
    "Be", "Mg", "Ca", "Sr", "Ba", "Ra",
    "Sc", "Ti", "V", "Cr", "Mn", "Fe", "Co", "Ni", "Cu", "Zn",
    "Y", "Zr", "Nb", "Mo", "Tc", "Ru", "Rh", "Pd", "Ag", "Cd",
    "Hf", "Ta", "W", "Re", "Os", "Ir", "Pt", "Au", "Hg",
    "Al", "Ga", "In", "Tl", "Sn", "Pb", "Bi", "Po",
    "La", "Ce", "Pr", "Nd", "Pm", "Sm", "Eu", "Gd", "Tb", "Dy", "Ho", "Er", "Tm", "Yb", "Lu",
    "Ac", "Th", "Pa", "U", "Np", "Pu", "Am", "Cm", "Bk", "Cf", "Es", "Fm", "Md", "No", "Lr"
)

private val halogenElements = setOf("F", "Cl", "Br", "I", "At", "Ts")
private val hydrogenFirstBinaryElements = halogenElements + setOf("S", "Se", "Te")
private val centralHydrideElements = setOf("B", "N", "P", "As", "Sb", "Si")

private fun metalFirstFormulaOrder(counts: Map<String, Int>): List<String> {
    val metals = counts.keys.filter { it in metalElements }.sortedWith(elementComparator)
    val remaining = counts.keys.filterNot { it in metalElements }
    val central = remaining
        .filterNot { it == "H" || it == "O" || it in halogenElements }
        .sortedWith(elementComparator)
    val halogens = remaining.filter { it in halogenElements }.sortedWith(elementComparator)

    return buildList {
        addAll(metals)
        if (central.isNotEmpty()) {
            if ("H" in remaining) add("H")
            addAll(central)
            if ("O" in remaining) add("O")
            addAll(halogens)
        } else {
            addAll(halogens)
            if ("O" in remaining) add("O")
            if ("H" in remaining) add("H")
            addAll(remaining.filterNot { it == "H" || it == "O" || it in halogenElements }.sortedWith(elementComparator))
        }
    }.distinct()
}

private fun inorganicFormulaOrder(counts: Map<String, Int>): List<String> {
    val elements = counts.keys
    val nonHydrogen = elements.filterNot { it == "H" }

    if ("H" in elements && "O" in elements) {
        val central = elements.filterNot { it == "H" || it == "O" }.sortedWith(elementComparator)
        if (central.isNotEmpty()) return listOf("H") + central + "O"
    }

    if ("H" in elements && nonHydrogen.size == 1) {
        val partner = nonHydrogen.first()
        return if (partner in centralHydrideElements) listOf(partner, "H") else listOf("H", partner)
    }

    return elements.toList()
}

private val elementComparator = compareBy<String> { periodicElementOrder[it] ?: Int.MAX_VALUE }.thenBy { it }

private val periodicElementOrder = listOf(
    "H", "He",
    "Li", "Be", "B", "C", "N", "O", "F", "Ne",
    "Na", "Mg", "Al", "Si", "P", "S", "Cl", "Ar",
    "K", "Ca", "Sc", "Ti", "V", "Cr", "Mn", "Fe", "Co", "Ni", "Cu", "Zn", "Ga", "Ge", "As", "Se", "Br", "Kr",
    "Rb", "Sr", "Y", "Zr", "Nb", "Mo", "Tc", "Ru", "Rh", "Pd", "Ag", "Cd", "In", "Sn", "Sb", "Te", "I", "Xe",
    "Cs", "Ba", "La", "Ce", "Pr", "Nd", "Pm", "Sm", "Eu", "Gd", "Tb", "Dy", "Ho", "Er", "Tm", "Yb", "Lu",
    "Hf", "Ta", "W", "Re", "Os", "Ir", "Pt", "Au", "Hg", "Tl", "Pb", "Bi", "Po", "At", "Rn",
    "Fr", "Ra", "Ac", "Th", "Pa", "U", "Np", "Pu", "Am", "Cm", "Bk", "Cf", "Es", "Fm", "Md", "No", "Lr",
    "Rf", "Db", "Sg", "Bh", "Hs", "Mt", "Ds", "Rg", "Cn", "Nh", "Fl", "Mc", "Lv", "Ts", "Og"
).withIndex().associate { it.value to it.index }

private fun buildFormula(elements: List<String>, counts: Map<String, Int>): String =
    elements.joinToString("") { element ->
        val count = counts[element] ?: 0
        if (count <= 1) element else "$element$count"
    }

private fun parseSequence(input: String, startIndex: Int, close: Char?): ParseOutput {
    val elements = linkedMapOf<String, Int>()
    var index = startIndex
    while (index < input.length) {
        val char = input[index]
        when {
            close != null && char == close -> {
                return ParseOutput(elements, index + 1)
            }
            char == ')' || char == ']' -> {
                return ParseOutput(
                    index = index,
                    error = FormulaParseError(
                        code = FormulaParseErrorCode.UNMATCHED_GROUP,
                        message = "Unmatched closing group",
                        position = index,
                        token = char.toString()
                    )
                )
            }
            char == '(' || char == '[' -> {
                val expectedClose = if (char == '(') ')' else ']'
                val inner = parseSequence(input, index + 1, expectedClose)
                if (inner.error != null) return inner
                if (inner.index > input.length || input.getOrNull(inner.index - 1) != expectedClose) {
                    return ParseOutput(
                        index = index,
                        error = FormulaParseError(
                            code = FormulaParseErrorCode.UNCLOSED_GROUP,
                            message = "Unclosed group",
                            position = index,
                            token = char.toString()
                        )
                    )
                }
                val countRead = readCount(input, inner.index)
                if (countRead.count <= 0) {
                    return ParseOutput(
                        index = inner.index,
                        error = FormulaParseError(
                            code = FormulaParseErrorCode.INVALID_MULTIPLIER,
                            message = "Group multiplier must be greater than zero",
                            position = inner.index
                        )
                    )
                }
                inner.elements.forEach { (element, count) ->
                    elements[element] = (elements[element] ?: 0) + count * countRead.count
                }
                index = countRead.nextIndex
            }
            char.isUpperCase() -> {
                val symbolStart = index
                index++
                while (index < input.length && input[index].isLowerCase()) index++
                val symbol = input.substring(symbolStart, index)
                val countRead = readCount(input, index)
                if (countRead.count <= 0) {
                    return ParseOutput(
                        index = index,
                        error = FormulaParseError(
                            code = FormulaParseErrorCode.INVALID_MULTIPLIER,
                            message = "Element count must be greater than zero",
                            position = index,
                            token = symbol
                        )
                    )
                }
                elements[symbol] = (elements[symbol] ?: 0) + countRead.count
                index = countRead.nextIndex
            }
            else -> {
                return ParseOutput(
                    index = index,
                    error = FormulaParseError(
                        code = FormulaParseErrorCode.INVALID_CHARACTER,
                        message = "Invalid formula character '$char'",
                        position = index,
                        token = char.toString()
                    )
                )
            }
        }
    }

    if (close != null) {
        return ParseOutput(
            index = index,
            error = FormulaParseError(
                code = FormulaParseErrorCode.UNCLOSED_GROUP,
                message = "Unclosed group",
                position = startIndex - 1
            )
        )
    }
    return ParseOutput(elements, index)
}

private data class CountRead(val count: Int, val nextIndex: Int)

private fun readCount(input: String, startIndex: Int): CountRead {
    var index = startIndex
    while (index < input.length && input[index].isDigit()) index++
    val count = input.substring(startIndex, index).toIntOrNull() ?: 1
    return CountRead(count, index)
}

private fun readLeadingMultiplier(part: String): Pair<Int, String>? {
    var index = 0
    while (index < part.length && part[index].isDigit()) index++
    if (index == 0) return 1 to part
    val multiplier = part.substring(0, index).toIntOrNull() ?: return null
    return multiplier to part.substring(index)
}

private fun normalizeFormulaText(formula: String): String {
    val withoutWhitespace = formula.filterNot { it.isWhitespace() }
    val superscriptCharge = Regex("[⁰¹²³⁴⁵⁶⁷⁸⁹]*[⁺⁻]+$").find(withoutWhitespace)
    val withCaretCharge = if (superscriptCharge != null) {
        val charge = superscriptCharge.value.map { char ->
            superscriptDigits[char] ?: when (char) {
                '⁺' -> '+'
                '⁻' -> '-'
                else -> char
            }
        }.joinToString("")
        withoutWhitespace.replaceRange(superscriptCharge.range, "^$charge")
    } else {
        withoutWhitespace
    }

    return withCaretCharge.map { char ->
        subscriptDigits[char] ?: char
    }.joinToString("")
}

private fun stripCharge(formula: String): String {
    val caretCharge = Regex("\\^\\d*[+-]+$").find(formula)
    if (caretCharge != null) return formula.substring(0, caretCharge.range.first)

    val signOnly = Regex("[+-]+$").find(formula) ?: return formula
    val beforeSigns = formula.substring(0, signOnly.range.first)
    val chargeWithDigits = Regex("\\d+[+-]+$").find(formula)

    if (chargeWithDigits != null) {
        val beforeDigits = formula.substring(0, chargeWithDigits.range.first)
        val singleElementIon = Regex("[A-Z][a-z]?$").matches(beforeDigits)
        val bracketIon = beforeDigits.endsWith("]") && beforeDigits.startsWith("[")
        if (singleElementIon || bracketIon) return beforeDigits
    }

    return beforeSigns
}

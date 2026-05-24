package com.furthersecrets.chemsearch.data

private val GROUP1 = setOf("Li", "Na", "K", "Rb", "Cs", "Fr")
private val GROUP2 = setOf("Be", "Mg", "Ca", "Sr", "Ba", "Ra")

data class OxidationStateResult(
    val states: List<Pair<String, String>> = emptyList(),
    val note: String? = null,
    val error: String? = null
)

private fun osSign(value: Int): String = if (value > 0) "+$value" else "$value"

private fun gcdOs(a: Long, b: Long): Long = if (b == 0L) a else gcdOs(b, a % b)

fun calculateOxidationStates(formula: String, chargeIn: Int): OxidationStateResult {
    if (formula.isBlank()) return OxidationStateResult(error = "Enter a formula")
    val elements = try {
        parseFormulaElementCounts(formula)
    } catch (_: Exception) {
        return OxidationStateResult(error = "Invalid formula syntax")
    }
    if (elements.isEmpty()) return OxidationStateResult(error = "Could not parse formula")

    val oCount = elements["O"] ?: 0
    val hCount = elements["H"] ?: 0
    val fCount = elements["F"] ?: 0
    val hasO = oCount > 0
    val hasH = hCount > 0
    val hasF = fCount > 0

    if (elements.size == 1) {
        val (el, cnt) = elements.entries.first()
        return if (cnt == 1) {
            OxidationStateResult(states = listOf(el to osSign(chargeIn)))
        } else {
            OxidationStateResult(states = listOf(el to "0"), note = "Free element. Oxidation state is 0")
        }
    }

    val alkaliEl = elements.keys.firstOrNull { it in GROUP1 }
    val alkalineEl = elements.keys.firstOrNull { it in GROUP2 }

    if (elements.size == 2 && hasO && hasF) {
        val oOs = (chargeIn + fCount) / oCount
        return OxidationStateResult(
            states = listOf("F" to "-1", "O" to osSign(oOs)),
            note = "Oxygen fluoride: F is always -1, so O = ${osSign(oOs)}"
        )
    }

    if (alkaliEl != null && elements.size == 2 && hasO) {
        val mCnt = elements[alkaliEl]!!
        if (oCount == mCnt * 2) {
            val metalSum = mCnt
            if (chargeIn - metalSum == -mCnt) {
                return OxidationStateResult(
                    states = listOf(alkaliEl to "+1", "O" to "-1/2"),
                    note = "Superoxide: O2- unit, each O has oxidation state = -1/2"
                )
            }
        }
    }

    if (alkaliEl != null && elements.size == 2 && hasO) {
        val mCnt = elements[alkaliEl]!!
        if (oCount == mCnt * 3 && chargeIn - mCnt == -mCnt) {
            return OxidationStateResult(
                states = listOf(alkaliEl to "+1", "O" to "-1/3"),
                note = "Ozonide: O3- unit, each O has oxidation state = -1/3"
            )
        }
    }

    val isPeroxide = when {
        elements.size == 2 && hasH && hasO && hCount == 2 && oCount == 2 && chargeIn == 0 -> true
        alkaliEl != null && elements.size == 2 && hasO && oCount == elements[alkaliEl]!! && chargeIn == 0 -> true
        alkalineEl != null && elements.size == 2 && hasO && oCount == elements[alkalineEl]!! * 2 && chargeIn == 0 -> true
        elements.size == 1 && hasO && oCount == 2 && chargeIn == -2 -> true
        else -> false
    }

    if (isPeroxide) {
        val res = mutableListOf("O" to "-1")
        for ((el, cnt) in elements) {
            if (el == "O") continue
            val fixedOs = when {
                el in GROUP1 -> 1
                el in GROUP2 -> 2
                el == "H" -> 1
                el == "F" -> -1
                else -> null
            }
            if (fixedOs != null) {
                res.add(el to osSign(fixedOs))
            } else {
                val knownSumLocal = elements.entries
                    .filter { it.key != el }
                    .sumOf { (e, c) ->
                        when {
                            e == "O" -> -1 * c
                            e in GROUP1 -> c
                            e in GROUP2 -> 2 * c
                            e == "H" -> c
                            else -> 0
                        }
                    }
                val rem = chargeIn - knownSumLocal
                res.add(el to if (rem % cnt == 0) osSign(rem / cnt) else "$rem/$cnt")
            }
        }
        return OxidationStateResult(states = res, note = "Peroxide compound: O has oxidation state = -1")
    }

    val metalHydrideMetals = setOf(
        "Li", "Na", "K", "Rb", "Cs", "Fr",
        "Be", "Mg", "Ca", "Sr", "Ba", "Ra",
        "Al", "Ga", "In", "Tl",
        "B"
    )
    val fixedOsForMetal: (String) -> Int? = { el ->
        when {
            el in GROUP1 -> 1
            el in GROUP2 -> 2
            el == "Al" || el == "Ga" || el == "In" || el == "Tl" || el == "B" -> 3
            else -> null
        }
    }
    val isMetalHydride = hasH && !hasO && !hasF &&
        elements.keys.filter { it != "H" }.all { it in metalHydrideMetals }

    if (isMetalHydride) {
        val res = mutableListOf("H" to "-1")
        for ((el, _) in elements) {
            if (el == "H") continue
            val metalOs = fixedOsForMetal(el)
            res.add(el to if (metalOs != null) osSign(metalOs) else "?")
        }
        val sum = elements.entries.sumOf { (el, cnt) ->
            when (el) {
                "H" -> -1 * cnt
                else -> (fixedOsForMetal(el) ?: 0) * cnt
            }
        }
        return OxidationStateResult(
            states = res,
            note = "Metal hydride: H has oxidation state = -1" +
                if (sum != chargeIn) " (sum = $sum, charge = $chargeIn, check formula)" else ""
        )
    }

    val fixed = mutableMapOf<String, Int>()
    var knownSum = 0
    val unknowns = mutableListOf<String>()

    for ((el, cnt) in elements) {
        val os: Int? = when (el) {
            "F" -> -1
            in GROUP1 -> 1
            in GROUP2 -> 2
            "Al", "Ga", "Sc", "Y", "La", "Lu" -> 3
            "Zn", "Cd" -> 2
            "Ag" -> 1
            "In", "Tl" -> 3
            "Cl" -> if (hasF || hasO) null else -1
            "Br" -> if (hasF || hasO || elements.containsKey("Cl")) null else -1
            "I" -> if (hasF || hasO || elements.containsKey("Cl") || elements.containsKey("Br")) null else -1
            "O" -> -2
            "H" -> 1
            else -> null
        }
        if (os != null) {
            fixed[el] = os
            knownSum += os * cnt
        } else {
            unknowns.add(el)
        }
    }

    return when (unknowns.size) {
        0 -> {
            val states = fixed.entries.map { it.key to osSign(it.value) }
            if (knownSum != chargeIn) {
                OxidationStateResult(
                    states = states,
                    note = "Sum of known oxidation state ($knownSum) does not match overall charge ($chargeIn). Possible mixed-valence, peroxo group, or formula error."
                )
            } else {
                OxidationStateResult(states = states)
            }
        }
        1 -> {
            val unknown = unknowns[0]
            val cnt = elements[unknown]!!
            val rem = chargeIn - knownSum
            if (rem % cnt != 0) {
                val g = gcdOs(kotlin.math.abs(rem.toLong()), kotlin.math.abs(cnt.toLong())).toInt()
                val fracStr = "${rem / g}/${cnt / g}"
                val states = fixed.entries.map { it.key to osSign(it.value) } + listOf(unknown to fracStr)
                OxidationStateResult(
                    states = states,
                    note = "Non-integer oxidation state for $unknown ($fracStr). It may indicate mixed-valence or a special compound."
                )
            } else {
                fixed[unknown] = rem / cnt
                OxidationStateResult(states = fixed.entries.map { it.key to osSign(it.value) })
            }
        }
        else -> {
            val states = fixed.entries.map { it.key to osSign(it.value) } + unknowns.map { it to "?" }
            OxidationStateResult(
                states = states,
                error = "Cannot solve! multiple unknown elements: ${unknowns.joinToString(", ")}. For transition metal complexes, use the charge field to provide additional constraints."
            )
        }
    }
}

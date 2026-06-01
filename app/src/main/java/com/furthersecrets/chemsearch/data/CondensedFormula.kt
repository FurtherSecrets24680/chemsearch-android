package com.furthersecrets.chemsearch.data

import java.util.ArrayDeque

fun buildBestCondensedFormula(connectivitySmiles: String?, smiles: String?): String? =
    listOf(connectivitySmiles, smiles)
        .mapNotNull { it?.trim()?.takeIf(String::isNotBlank) }
        .distinct()
        .firstNotNullOfOrNull(::buildCondensedFormula)

fun buildCondensedFormula(smiles: String): String? {
    val trimmed = smiles.trim()
    if (trimmed.isBlank() || hasRingOrAromaticNotation(trimmed)) return null

    buildCoordinationFormula(trimmed)?.let { return it }
    buildInorganicIonicFormula(trimmed)?.let { return it }

    val components = trimmed.split('.')
    if (components.isEmpty() || components.any { it.isBlank() }) return null

    val renderedComponents = components.map { component ->
        renderCondensedComponent(component) ?: return null
    }
    return groupedComponents(renderedComponents).joinToString(" · ").takeIf { it.isNotBlank() }
}

private data class ParsedSmilesComponent(
    val graph: SimpleSmilesGraph,
    val charge: Int
)

private data class CoordinationLigand(
    val formula: String,
    val charge: Int,
    val order: Int
)

private data class CoordinationCounterIon(
    val formula: String,
    val charge: Int
)

private data class InorganicIon(
    val formula: String,
    val charge: Int
)

private val coordinationCenterElements = setOf(
    "Sc", "Ti", "V", "Cr", "Mn", "Fe", "Co", "Ni", "Cu", "Zn",
    "Y", "Zr", "Nb", "Mo", "Tc", "Ru", "Rh", "Pd", "Ag", "Cd",
    "Hf", "Ta", "W", "Re", "Os", "Ir", "Pt", "Au", "Hg"
)

private fun buildCoordinationFormula(smiles: String): String? {
    val parsed = smiles.split('.')
        .takeIf { it.size >= 2 && it.none(String::isBlank) }
        ?.map { component ->
            val graph = parseSimpleSmiles(component) ?: return null
            ParsedSmilesComponent(graph = graph, charge = graph.atoms.sumOf { it.charge })
        } ?: return null

    val centerIndexes = parsed.indices.filter { index ->
        val component = parsed[index]
        component.graph.atoms.size == 1 &&
            component.charge != 0 &&
            component.graph.atoms.first().symbol in coordinationCenterElements
    }
    if (centerIndexes.size != 1) return null

    val center = parsed[centerIndexes.first()].graph.atoms.first()
    val ligands = mutableListOf<CoordinationLigand>()
    val counterIons = mutableListOf<CoordinationCounterIon>()

    parsed.forEachIndexed { index, component ->
        if (index == centerIndexes.first()) return@forEachIndexed

        val ligand = coordinationLigand(component)
        if (ligand != null) {
            ligands.add(ligand)
            return@forEachIndexed
        }

        val counterIon = coordinationCounterIon(component) ?: return null
        counterIons.add(counterIon)
    }

    if (ligands.isEmpty()) return null

    val complexCharge = center.charge + ligands.sumOf { it.charge }
    val counterCharge = counterIons.sumOf { it.charge }
    if (complexCharge + counterCharge != 0) return null
    if (complexCharge == 0 && counterIons.isNotEmpty()) return null

    val complex = "[" + center.symbol + renderCoordinationLigands(ligands) + "]"
    val counters = renderCounterIons(counterIons)

    return when {
        complexCharge < 0 -> counters + complex
        complexCharge > 0 -> complex + counters
        else -> complex
    }.takeIf { it != "[]" }
}

private fun buildInorganicIonicFormula(smiles: String): String? {
    val components = smiles.split('.').takeIf { it.size >= 2 && it.none(String::isBlank) } ?: return null
    val ions = components.map { component ->
        val graph = parseSimpleSmiles(component) ?: return null
        val parsed = ParsedSmilesComponent(graph = graph, charge = graph.atoms.sumOf { it.charge })
        inorganicIon(parsed) ?: return null
    }

    if (ions.sumOf { it.charge } != 0) return null
    if (ions.none { it.charge > 0 } || ions.none { it.charge < 0 }) return null

    return renderIonFormulaGroup(ions.filter { it.charge > 0 }) +
        renderIonFormulaGroup(ions.filter { it.charge < 0 })
}

private fun inorganicIon(component: ParsedSmilesComponent): InorganicIon? {
    if (component.charge == 0) return null

    if (component.graph.atoms.size == 1) {
        return InorganicIon(
            formula = singleIonFormula(component.graph.atoms.first()),
            charge = component.charge
        )
    }

    val oxoanion = oxoanionFormula(component.graph) ?: return null
    return InorganicIon(formula = oxoanion, charge = component.charge)
}

private fun singleIonFormula(atom: SimpleSmilesAtom): String {
    val hydrogens = atom.explicitHydrogens ?: 0
    return when {
        atom.symbol == "O" && hydrogens > 0 && atom.charge > 0 -> hydrogenPrefix(hydrogens) + "O"
        atom.symbol == "O" && hydrogens > 0 -> "OH"
        else -> atom.symbol + hydrogenText(hydrogens)
    }
}

private fun oxoanionFormula(graph: SimpleSmilesGraph): String? {
    val centralIndexes = graph.atoms.indices.filter { graph.atoms[it].symbol != "O" }
    if (centralIndexes.size != 1) return null

    val centralIndex = centralIndexes.single()
    val central = graph.atoms[centralIndex]
    if (central.symbol !in oxoanionCenterElements) return null

    val oxygenIndexes = graph.atoms.indices.filter { it != centralIndex }
    if (oxygenIndexes.isEmpty()) return null
    val allOxygensBondToCenter = oxygenIndexes.all { oxygenIndex ->
        graph.atoms[oxygenIndex].symbol == "O" &&
            graph.bonds[oxygenIndex].size == 1 &&
            graph.bonds[oxygenIndex].first().to == centralIndex
    }
    if (!allOxygensBondToCenter) return null

    val centralOnlyBondsToOxygen = graph.bonds[centralIndex].size == oxygenIndexes.size &&
        graph.bonds[centralIndex].all { bond -> bond.to in oxygenIndexes }
    if (!centralOnlyBondsToOxygen) return null

    val hydrogenCount = oxygenIndexes.sumOf { oxygenIndex ->
        val oxygen = graph.atoms[oxygenIndex]
        oxygen.explicitHydrogens
            ?: if (oxygen.charge != 0) 0 else (implicitHydrogenCount(graph, oxygenIndex) ?: return null)
    }
    val oxygenCount = oxygenIndexes.size

    return hydrogenPrefix(hydrogenCount) + central.symbol + "O" + countText(oxygenCount)
}

private val oxoanionCenterElements = setOf(
    "B", "C", "N", "P", "S", "Cl", "Br", "I", "Cr", "Mn", "Se", "As"
)

private fun renderIonFormulaGroup(ions: List<InorganicIon>): String {
    val counts = linkedMapOf<String, Int>()
    ions.forEach { ion ->
        counts[ion.formula] = (counts[ion.formula] ?: 0) + 1
    }
    return counts.map { (formula, count) ->
        when {
            count == 1 -> formula
            shouldWrapRepeatedFormula(formula) -> "($formula)$count"
            else -> formula + count
        }
    }.joinToString("")
}

private fun shouldWrapRepeatedFormula(formula: String): Boolean =
    Regex("""[A-Z][a-z]?""").findAll(formula).count() > 1

private fun hydrogenPrefix(count: Int): String =
    when (count) {
        0 -> ""
        1 -> "H"
        else -> "H$count"
    }

private fun countText(count: Int): String =
    if (count == 1) "" else count.toString()

private fun coordinationLigand(component: ParsedSmilesComponent): CoordinationLigand? {
    val graph = component.graph
    if (graph.atoms.size == 1 && component.charge == 0) {
        val symbol = graph.atoms.first().symbol
        return when (symbol) {
            "N" -> CoordinationLigand(formula = "NH3", charge = 0, order = 30)
            "O" -> CoordinationLigand(formula = "H2O", charge = 0, order = 40)
            else -> null
        }
    }

    if (graph.atoms.size == 1 && graph.atoms.first().symbol == "O" && component.charge == -1) {
        val atom = graph.atoms.first()
        if ((atom.explicitHydrogens ?: 0) == 1) {
            return CoordinationLigand(formula = "OH", charge = -1, order = 25)
        }
    }

    if (graph.atoms.size != 2 || graph.edgeCount != 1) return null
    val first = graph.atoms[0]
    val second = graph.atoms[1]
    val bondOrder = graph.bonds[0].firstOrNull { it.to == 1 }?.order ?: return null
    if (bondOrder != 3) return null

    val symbols = setOf(first.symbol, second.symbol)
    return when {
        symbols == setOf("C", "N") && component.charge == -1 ->
            CoordinationLigand(formula = "CN", charge = -1, order = 10)
        symbols == setOf("N", "O") && component.charge == 1 ->
            CoordinationLigand(formula = "NO", charge = 1, order = 20)
        else -> null
    }
}

private fun coordinationCounterIon(component: ParsedSmilesComponent): CoordinationCounterIon? {
    if (component.charge == 0 || component.graph.atoms.size != 1) return null
    val atom = component.graph.atoms.first()
    val hydrogens = atom.explicitHydrogens ?: 0
    val formula = atom.symbol + hydrogenText(hydrogens)
    return CoordinationCounterIon(formula = formula, charge = component.charge)
}

private fun renderCoordinationLigands(ligands: List<CoordinationLigand>): String {
    val counts = linkedMapOf<String, Pair<CoordinationLigand, Int>>()
    ligands.sortedBy(CoordinationLigand::order).forEach { ligand ->
        val existing = counts[ligand.formula]
        counts[ligand.formula] = ligand to ((existing?.second ?: 0) + 1)
    }
    return counts.values.joinToString("") { (ligand, count) ->
        when {
            count == 1 -> ligand.formula
            ligand.formula.length == 1 -> ligand.formula + count
            else -> "(${ligand.formula})$count"
        }
    }
}

private fun renderCounterIons(counterIons: List<CoordinationCounterIon>): String {
    val counts = linkedMapOf<String, Int>()
    counterIons.forEach { counterIon ->
        counts[counterIon.formula] = (counts[counterIon.formula] ?: 0) + 1
    }
    return counts.map { (formula, count) ->
        when {
            count == 1 -> formula
            formula.length == 1 || bracketFormulaElements.contains(formula) -> formula + count
            else -> "($formula)$count"
        }
    }.joinToString("")
}

private fun renderCondensedComponent(smiles: String): String? {
    val graph = parseSimpleSmiles(smiles.trim()) ?: return null
    if (graph.atoms.size !in 1..40) return null
    if (graph.atoms.size == 1) return renderSingleAtomComponent(graph.atoms.first())
    if (graph.edgeCount != graph.atoms.size - 1) return null

    val path = chooseMainPath(graph) ?: return null
    return renderCondensedFormula(graph, path)
}

private fun groupedComponents(components: List<String>): List<String> {
    val counts = linkedMapOf<String, Int>()
    components.forEach { component ->
        counts[component] = (counts[component] ?: 0) + 1
    }
    return counts.map { (component, count) ->
        if (count == 1) component else "$count$component"
    }
}

private data class SimpleSmilesGraph(
    val atoms: MutableList<SimpleSmilesAtom> = mutableListOf(),
    val bonds: MutableList<MutableList<SimpleSmilesBond>> = mutableListOf(),
    var edgeCount: Int = 0
) {
    fun addAtom(atom: SimpleSmilesAtom): Int {
        atoms.add(atom)
        bonds.add(mutableListOf())
        return atoms.lastIndex
    }

    fun addBond(first: Int, second: Int, order: Int) {
        bonds[first].add(SimpleSmilesBond(second, order))
        bonds[second].add(SimpleSmilesBond(first, order))
        edgeCount++
    }
}

private data class SimpleSmilesAtom(
    val symbol: String,
    val explicitHydrogens: Int? = null,
    val charge: Int = 0,
    val bracketed: Boolean = false
)

private data class SimpleSmilesBond(val to: Int, val order: Int)

private val condensedFormulaElements = setOf(
    "B", "C", "N", "O", "P", "S", "F", "Cl", "Br", "I"
)

private val bracketFormulaElements = setOf(
    "H", "He", "Li", "Be", "B", "C", "N", "O", "F", "Ne",
    "Na", "Mg", "Al", "Si", "P", "S", "Cl", "Ar", "K", "Ca",
    "Sc", "Ti", "V", "Cr", "Mn", "Fe", "Co", "Ni", "Cu", "Zn",
    "Ga", "Ge", "As", "Se", "Br", "Kr", "Rb", "Sr", "Y", "Zr",
    "Nb", "Mo", "Tc", "Ru", "Rh", "Pd", "Ag", "Cd", "In", "Sn",
    "Sb", "Te", "I", "Xe", "Cs", "Ba", "La", "Ce", "Pr", "Nd",
    "Pm", "Sm", "Eu", "Gd", "Tb", "Dy", "Ho", "Er", "Tm", "Yb",
    "Lu", "Hf", "Ta", "W", "Re", "Os", "Ir", "Pt", "Au", "Hg",
    "Tl", "Pb", "Bi", "Po", "At", "Rn", "Fr", "Ra", "Ac", "Th",
    "Pa", "U", "Np", "Pu", "Am", "Cm", "Bk", "Cf", "Es", "Fm",
    "Md", "No", "Lr", "Rf", "Db", "Sg", "Bh", "Hs", "Mt", "Ds",
    "Rg", "Cn", "Nh", "Fl", "Mc", "Lv", "Ts", "Og"
)

private val aromaticAtomSymbols = setOf('b', 'c', 'n', 'o', 'p', 's')

private fun hasRingOrAromaticNotation(smiles: String): Boolean {
    var index = 0
    while (index < smiles.length) {
        val char = smiles[index]
        when {
            char.isDigit() || char == '%' -> return true
            char == '[' -> {
                val end = smiles.indexOf(']', startIndex = index + 1)
                if (end == -1) return true
                val content = smiles.substring(index + 1, end).trimStart { it.isDigit() }
                if (content.firstOrNull() in aromaticAtomSymbols) return true
                index = end + 1
            }
            char in aromaticAtomSymbols && (index == 0 || !smiles[index - 1].isUpperCase()) -> return true
            else -> index++
        }
    }
    return false
}

private fun parseSimpleSmiles(smiles: String): SimpleSmilesGraph? {
    if (smiles.isBlank()) return null

    val graph = SimpleSmilesGraph()
    val branchStack = ArrayDeque<Int>()
    var currentAtom: Int? = null
    var pendingBondOrder = 1
    var index = 0

    while (index < smiles.length) {
        when (val char = smiles[index]) {
            '(' -> {
                val current = currentAtom ?: return null
                branchStack.addLast(current)
                index++
            }
            ')' -> {
                currentAtom = branchStack.pollLast() ?: return null
                index++
            }
            '-' -> {
                pendingBondOrder = 1
                index++
            }
            '=' -> {
                pendingBondOrder = 2
                index++
            }
            '#' -> {
                pendingBondOrder = 3
                index++
            }
            '[' -> {
                val bracketAtom = readBracketAtom(smiles, index) ?: return null
                val nextAtom = graph.addAtom(bracketAtom.atom)
                currentAtom?.let { graph.addBond(it, nextAtom, pendingBondOrder) }
                currentAtom = nextAtom
                pendingBondOrder = 1
                index = bracketAtom.nextIndex
            }
            ']', '.', '/', '\\', '@', '+', '%', ':' -> return null
            else -> {
                if (!char.isUpperCase()) return null
                val symbol = readElementSymbol(smiles, index) ?: return null
                if (symbol !in condensedFormulaElements) return null

                val nextAtom = graph.addAtom(SimpleSmilesAtom(symbol = symbol))
                currentAtom?.let { graph.addBond(it, nextAtom, pendingBondOrder) }
                currentAtom = nextAtom
                pendingBondOrder = 1
                index += symbol.length
            }
        }
    }

    return graph.takeIf { branchStack.isEmpty() && it.atoms.isNotEmpty() }
}

private data class BracketAtomRead(val atom: SimpleSmilesAtom, val nextIndex: Int)

private fun readBracketAtom(input: String, index: Int): BracketAtomRead? {
    val end = input.indexOf(']', startIndex = index + 1)
    if (end == -1) return null
    val atom = parseBracketAtomContent(input.substring(index + 1, end)) ?: return null
    return BracketAtomRead(atom = atom, nextIndex = end + 1)
}

private fun parseBracketAtomContent(content: String): SimpleSmilesAtom? {
    if (content.isBlank()) return null
    var index = 0
    while (index < content.length && content[index].isDigit()) index++

    val first = content.getOrNull(index) ?: return null
    if (!first.isUpperCase()) return null
    val second = content.getOrNull(index + 1)
    val symbol = if (second != null && second.isLowerCase()) "$first$second" else first.toString()
    if (symbol !in bracketFormulaElements) return null
    index += symbol.length

    var explicitHydrogens: Int? = null
    if (content.getOrNull(index) == 'H') {
        index++
        val digitStart = index
        while (index < content.length && content[index].isDigit()) index++
        explicitHydrogens = if (digitStart == index) {
            1
        } else {
            content.substring(digitStart, index).toIntOrNull() ?: return null
        }
    }

    val charge = if (index < content.length) {
        parseBracketCharge(content.substring(index)) ?: return null
    } else {
        0
    }

    return SimpleSmilesAtom(
        symbol = symbol,
        explicitHydrogens = explicitHydrogens,
        charge = charge,
        bracketed = true
    )
}

private fun parseBracketCharge(value: String): Int? {
    if (value.isBlank()) return 0
    if (value.all { it == '+' }) return value.length
    if (value.all { it == '-' }) return -value.length

    val signFirst = value.firstOrNull()
    if ((signFirst == '+' || signFirst == '-') && value.drop(1).all { it.isDigit() }) {
        val amount = value.drop(1).toIntOrNull() ?: return null
        return if (signFirst == '+') amount else -amount
    }

    val signLast = value.lastOrNull()
    if ((signLast == '+' || signLast == '-') && value.dropLast(1).all { it.isDigit() }) {
        val amount = value.dropLast(1).toIntOrNull() ?: return null
        return if (signLast == '+') amount else -amount
    }

    return null
}

private fun readElementSymbol(input: String, index: Int): String? {
    val first = input.getOrNull(index) ?: return null
    if (!first.isUpperCase()) return null
    val second = input.getOrNull(index + 1)
    val candidate = if (second != null && second.isLowerCase()) "$first$second" else first.toString()
    return candidate.takeIf { it in condensedFormulaElements }
}

private data class PathScore(
    val length: Int,
    val carbonCount: Int,
    val terminalHydrogenCount: Int,
    val singleBondCount: Int
) : Comparable<PathScore> {
    override fun compareTo(other: PathScore): Int =
        compareValuesBy(this, other, PathScore::length, PathScore::carbonCount, PathScore::terminalHydrogenCount, PathScore::singleBondCount)
}

private fun chooseMainPath(graph: SimpleSmilesGraph): List<Int>? {
    val leaves = graph.atoms.indices.filter { graph.bonds[it].size <= 1 }
    if (leaves.size < 2) return null

    var bestPath: List<Int>? = null
    var bestScore: PathScore? = null

    for (startIndex in leaves.indices) {
        for (endIndex in startIndex + 1 until leaves.size) {
            val path = pathBetween(graph, leaves[startIndex], leaves[endIndex]) ?: continue
            val oriented = orientPath(graph, path)
            val score = scorePath(graph, oriented)
            if (bestScore == null || score > bestScore) {
                bestScore = score
                bestPath = oriented
            }
        }
    }

    return bestPath
}

private fun pathBetween(graph: SimpleSmilesGraph, start: Int, end: Int): List<Int>? {
    val previous = IntArray(graph.atoms.size) { -1 }
    val visited = BooleanArray(graph.atoms.size)
    val queue = ArrayDeque<Int>()

    queue.add(start)
    visited[start] = true

    while (!queue.isEmpty()) {
        val atom = queue.removeFirst()
        if (atom == end) break
        graph.bonds[atom].forEach { bond ->
            if (!visited[bond.to]) {
                visited[bond.to] = true
                previous[bond.to] = atom
                queue.add(bond.to)
            }
        }
    }

    if (!visited[end]) return null

    val path = mutableListOf<Int>()
    var cursor = end
    while (cursor != -1) {
        path.add(cursor)
        if (cursor == start) break
        cursor = previous[cursor]
    }
    return path.asReversed()
}

private fun orientPath(graph: SimpleSmilesGraph, path: List<Int>): List<Int> {
    val reversed = path.asReversed()
    val forwardScore = orientationScore(graph, path)
    val reverseScore = orientationScore(graph, reversed)
    return if (reverseScore > forwardScore) reversed else path
}

private data class OrientationScore(
    val startsWithCarbon: Int,
    val endsWithHeteroAtom: Int,
    val startsWithMoreHydrogen: Int
) : Comparable<OrientationScore> {
    override fun compareTo(other: OrientationScore): Int =
        compareValuesBy(this, other, OrientationScore::startsWithCarbon, OrientationScore::endsWithHeteroAtom, OrientationScore::startsWithMoreHydrogen)
}

private fun orientationScore(graph: SimpleSmilesGraph, path: List<Int>): OrientationScore {
    val start = path.first()
    val end = path.last()
    return OrientationScore(
        startsWithCarbon = if (graph.atoms[start].symbol == "C") 1 else 0,
        endsWithHeteroAtom = if (graph.atoms[end].symbol != "C") 1 else 0,
        startsWithMoreHydrogen = implicitHydrogenCount(graph, start) ?: 0
    )
}

private fun scorePath(graph: SimpleSmilesGraph, path: List<Int>): PathScore =
    PathScore(
        length = path.size,
        carbonCount = path.count { graph.atoms[it].symbol == "C" },
        terminalHydrogenCount = (implicitHydrogenCount(graph, path.first()) ?: 0) + (implicitHydrogenCount(graph, path.last()) ?: 0),
        singleBondCount = path.zipWithNext().count { (first, second) -> bondOrder(graph, first, second) == 1 }
    )

private fun renderCondensedFormula(graph: SimpleSmilesGraph, path: List<Int>): String? {
    val builder = StringBuilder()
    path.forEachIndexed { index, atom ->
        if (index > 0) {
            builder.append(bondText(bondOrder(graph, path[index - 1], atom) ?: return null))
        }
        builder.append(renderPathAtom(graph, atom, path.getOrNull(index - 1), path.getOrNull(index + 1)) ?: return null)
    }
    return builder.toString().takeIf { it.isNotBlank() }
}

private fun renderPathAtom(graph: SimpleSmilesGraph, atom: Int, previousPathAtom: Int?, nextPathAtom: Int?): String? {
    val excluded = setOfNotNull(previousPathAtom, nextPathAtom)
    val branches = graph.bonds[atom]
        .filterNot { it.to in excluded }
        .sortedWith(compareByDescending<SimpleSmilesBond> { branchWeight(graph, it.to, atom) }.thenBy { graph.atoms[it.to].symbol })

    val atomText = atomText(graph, atom) ?: return null
    if (branches.isEmpty()) return atomText

    return buildString {
        append(atomText)
        branches.forEach { branch ->
            append('(')
            append(bondText(branch.order))
            append(renderBranch(graph, branch.to, atom) ?: return null)
            append(')')
        }
    }
}

private fun renderBranch(graph: SimpleSmilesGraph, atom: Int, parent: Int): String? {
    val branches = graph.bonds[atom]
        .filterNot { it.to == parent }
        .sortedWith(compareByDescending<SimpleSmilesBond> { branchWeight(graph, it.to, atom) }.thenBy { graph.atoms[it.to].symbol })

    val atomText = atomText(graph, atom) ?: return null
    if (branches.isEmpty()) return atomText

    return buildString {
        append(atomText)
        branches.forEach { branch ->
            append('(')
            append(bondText(branch.order))
            append(renderBranch(graph, branch.to, atom) ?: return null)
            append(')')
        }
    }
}

private fun branchWeight(graph: SimpleSmilesGraph, start: Int, parent: Int): Int {
    var weight = 1
    graph.bonds[start]
        .filterNot { it.to == parent }
        .forEach { weight += branchWeight(graph, it.to, start) }
    return weight
}

private fun atomText(graph: SimpleSmilesGraph, atom: Int): String? {
    val atomInfo = graph.atoms[atom]
    val hydrogens = if (atomInfo.bracketed && (atomInfo.explicitHydrogens != null || atomInfo.charge != 0)) {
        atomInfo.explicitHydrogens ?: 0
    } else {
        implicitHydrogenCount(graph, atom) ?: return null
    }
    return atomInfo.symbol + hydrogenText(hydrogens) + chargeText(atomInfo.charge)
}

private fun renderSingleAtomComponent(atom: SimpleSmilesAtom): String? {
    val hydrogens = if (atom.bracketed && (atom.explicitHydrogens != null || atom.charge != 0)) {
        atom.explicitHydrogens ?: 0
    } else {
        neutralSingleAtomHydrogenCount(atom.symbol) ?: return null
    }

    val neutralSingleAtomText = when (atom.symbol) {
        "O" -> "H2O"
        "S" -> "H2S"
        "F", "Cl", "Br", "I" -> "H${atom.symbol}"
        else -> atom.symbol + hydrogenText(hydrogens)
    }

    return if (atom.charge == 0) neutralSingleAtomText else atom.symbol + hydrogenText(hydrogens) + chargeText(atom.charge)
}

private fun neutralSingleAtomHydrogenCount(symbol: String): Int? =
    when (symbol) {
        "B" -> 3
        "C" -> 4
        "N" -> 3
        "O" -> 2
        "P" -> 3
        "S" -> 2
        "F", "Cl", "Br", "I" -> 1
        else -> null
    }

private fun hydrogenText(hydrogens: Int): String =
    when (hydrogens) {
        0 -> ""
        1 -> "H"
        else -> "H$hydrogens"
    }

private fun chargeText(charge: Int): String =
    when {
        charge > 0 -> if (charge == 1) "+" else "$charge+"
        charge < 0 -> if (charge == -1) "-" else "${-charge}-"
        else -> ""
    }

private fun implicitHydrogenCount(graph: SimpleSmilesGraph, atom: Int): Int? {
    val symbol = graph.atoms[atom].symbol
    val bondOrderSum = graph.bonds[atom].sumOf { it.order }
    val valence = when (symbol) {
        "B" -> 3
        "C" -> 4
        "N" -> 3
        "O" -> 2
        "P" -> if (bondOrderSum > 3) 5 else 3
        "S" -> if (bondOrderSum > 2) 6 else 2
        "F", "Cl", "Br", "I" -> 1
        else -> return null
    }
    return (valence - bondOrderSum).takeIf { it >= 0 }
}

private fun bondOrder(graph: SimpleSmilesGraph, first: Int, second: Int): Int? =
    graph.bonds[first].firstOrNull { it.to == second }?.order

private fun bondText(order: Int): String = when (order) {
    1 -> "-"
    2 -> "="
    3 -> "≡"
    else -> "-"
}

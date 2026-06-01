package com.furthersecrets.chemsearch.data

import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

enum class BondOrder(val molfileValue: Int) {
    SINGLE(1),
    DOUBLE(2),
    TRIPLE(3),
    AROMATIC(4)
}

enum class RingTemplate(
    val label: String,
    val elements: List<String>,
    val doubleBondIndices: Set<Int> = emptySet()
) {
    CYCLOPROPANE(
        label = "Cyclopropane",
        elements = listOf("C", "C", "C")
    ),
    CYCLOBUTANE(
        label = "Cyclobutane",
        elements = listOf("C", "C", "C", "C")
    ),
    CYCLOPENTANE(
        label = "Cyclopentane",
        elements = listOf("C", "C", "C", "C", "C")
    ),
    CYCLOHEXANE(
        label = "Cyclohexane",
        elements = listOf("C", "C", "C", "C", "C", "C")
    ),
    CYCLOHEPTANE(
        label = "Cycloheptane",
        elements = listOf("C", "C", "C", "C", "C", "C", "C")
    ),
    CYCLOOCTANE(
        label = "Cyclooctane",
        elements = listOf("C", "C", "C", "C", "C", "C", "C", "C")
    ),
    BENZENE(
        label = "Benzene",
        elements = listOf("C", "C", "C", "C", "C", "C"),
        doubleBondIndices = setOf(0, 2, 4)
    ),
    PYRIDINE(
        label = "Pyridine",
        elements = listOf("N", "C", "C", "C", "C", "C"),
        doubleBondIndices = setOf(0, 2, 4)
    ),
    FURAN(
        label = "Furan",
        elements = listOf("O", "C", "C", "C", "C"),
        doubleBondIndices = setOf(1, 3)
    ),
    THIOPHENE(
        label = "Thiophene",
        elements = listOf("S", "C", "C", "C", "C"),
        doubleBondIndices = setOf(1, 3)
    ),
    PYRROLE(
        label = "Pyrrole",
        elements = listOf("N", "C", "C", "C", "C"),
        doubleBondIndices = setOf(1, 3)
    ),
    IMIDAZOLE(
        label = "Imidazole",
        elements = listOf("N", "C", "N", "C", "C"),
        doubleBondIndices = setOf(0, 3)
    ),
    OXAZOLE(
        label = "Oxazole",
        elements = listOf("O", "C", "N", "C", "C"),
        doubleBondIndices = setOf(1, 3)
    ),
    THIAZOLE(
        label = "Thiazole",
        elements = listOf("S", "C", "N", "C", "C"),
        doubleBondIndices = setOf(1, 3)
    )
}

enum class ChainTemplate(
    val label: String,
    val elements: List<String>,
    val bondOrder: BondOrder = BondOrder.SINGLE
) {
    ETHYL("Ethyl chain", listOf("C", "C")),
    PROPYL("Propyl chain", listOf("C", "C", "C")),
    BUTYL("Butyl chain", listOf("C", "C", "C", "C")),
    HEXYL("Hexyl chain", listOf("C", "C", "C", "C", "C", "C")),
    CARBONYL("Carbonyl", listOf("C", "O"), BondOrder.DOUBLE),
    NITRILE("Nitrile", listOf("C", "N"), BondOrder.TRIPLE),
    HYDROXYL("Hydroxyl", listOf("O", "H")),
    AMINO("Amino", listOf("N", "H"))
}

enum class StructureSearchMode(
    val label: String,
    val pubChemOperation: String,
    val description: String
) {
    EXACT(
        label = "Exact",
        pubChemOperation = "fastidentity",
        description = "Find the same structure"
    ),
    SIMILAR(
        label = "Similar",
        pubChemOperation = "fastsimilarity_2d",
        description = "Find close 2D matches"
    ),
    CONTAINS(
        label = "Contains",
        pubChemOperation = "fastsuperstructure",
        description = "Find compounds containing this drawing"
    ),
    PART_OF(
        label = "Part of",
        pubChemOperation = "fastsubstructure",
        description = "Find structures inside this drawing"
    )
}

data class SketchAtom(
    val id: Int,
    val element: String,
    val x: Double,
    val y: Double,
    val charge: Int = 0
)

data class SketchBond(
    val id: Int,
    val atomA: Int,
    val atomB: Int,
    val order: Int
)

data class StructureSketch(
    val atoms: List<SketchAtom> = emptyList(),
    val bonds: List<SketchBond> = emptyList()
) {
    val canSearch: Boolean
        get() = atoms.size >= 2 && bonds.isNotEmpty() && isConnected()

    val formula: String
        get() {
            if (atoms.isEmpty()) return ""
            val counts = atoms.groupingBy { it.element }.eachCount()
            val orderedElements = buildList {
                if ("C" in counts) add("C")
                if ("H" in counts) add("H")
                addAll(counts.keys.filterNot { it == "C" || it == "H" }.sorted())
            }
            return orderedElements.joinToString(separator = "") { element ->
                val count = counts.getValue(element)
                if (count == 1) element else "$element$count"
            }
        }

    fun addAtom(element: String, x: Double, y: Double, charge: Int = 0): StructureSketch {
        val nextId = (atoms.maxOfOrNull { it.id } ?: 0) + 1
        return copy(atoms = atoms + SketchAtom(nextId, normalizeElement(element), x, y, charge))
    }

    fun updateAtom(id: Int, element: String, charge: Int = atoms.firstOrNull { it.id == id }?.charge ?: 0): StructureSketch =
        copy(atoms = atoms.map { atom ->
            if (atom.id == id) atom.copy(element = normalizeElement(element), charge = charge) else atom
        })

    fun moveAtom(id: Int, x: Double, y: Double): StructureSketch =
        copy(atoms = atoms.map { atom -> if (atom.id == id) atom.copy(x = x, y = y) else atom })

    fun removeAtom(id: Int): StructureSketch =
        copy(
            atoms = atoms.filterNot { it.id == id },
            bonds = bonds.filterNot { it.atomA == id || it.atomB == id }
        )

    fun addBond(atomA: Int, atomB: Int, order: BondOrder = BondOrder.SINGLE): StructureSketch {
        if (atomA == atomB) return this
        val atomIds = atoms.map { it.id }.toSet()
        if (atomA !in atomIds || atomB !in atomIds) return this
        val existing = bonds.firstOrNull {
            (it.atomA == atomA && it.atomB == atomB) || (it.atomA == atomB && it.atomB == atomA)
        }
        if (existing != null) {
            return copy(bonds = bonds.map { bond ->
                if (bond.id == existing.id) bond.copy(order = order.molfileValue) else bond
            })
        }
        val nextId = (bonds.maxOfOrNull { it.id } ?: 0) + 1
        return copy(bonds = bonds + SketchBond(nextId, atomA, atomB, order.molfileValue))
    }

    fun removeBond(id: Int): StructureSketch =
        copy(bonds = bonds.filterNot { it.id == id })

    fun cycleBondOrder(id: Int): StructureSketch =
        copy(bonds = bonds.map { bond ->
            if (bond.id != id) {
                bond
            } else {
                val nextOrder = when (bond.order) {
                    BondOrder.SINGLE.molfileValue -> BondOrder.DOUBLE.molfileValue
                    BondOrder.DOUBLE.molfileValue -> BondOrder.TRIPLE.molfileValue
                    BondOrder.TRIPLE.molfileValue -> BondOrder.AROMATIC.molfileValue
                    else -> BondOrder.SINGLE.molfileValue
                }
                bond.copy(order = nextOrder)
            }
        })

    fun duplicateConnectedFragmentFrom(atomId: Int, dx: Double, dy: Double): StructureSketch {
        if (atoms.none { it.id == atomId }) return this
        val componentIds = connectedComponent(atomId)
        val componentAtoms = atoms.filter { it.id in componentIds }
        val startAtomId = (atoms.maxOfOrNull { it.id } ?: 0) + 1
        val idMap = componentAtoms.mapIndexed { index, atom -> atom.id to startAtomId + index }.toMap()
        val duplicatedAtoms = componentAtoms.map { atom ->
            atom.copy(
                id = idMap.getValue(atom.id),
                x = atom.x + dx,
                y = atom.y + dy
            )
        }
        val componentBonds = bonds.filter { it.atomA in componentIds && it.atomB in componentIds }
        val startBondId = (bonds.maxOfOrNull { it.id } ?: 0) + 1
        val duplicatedBonds = componentBonds.mapIndexed { index, bond ->
            bond.copy(
                id = startBondId + index,
                atomA = idMap.getValue(bond.atomA),
                atomB = idMap.getValue(bond.atomB)
            )
        }
        return copy(atoms = atoms + duplicatedAtoms, bonds = bonds + duplicatedBonds)
    }

    fun translate(dx: Double, dy: Double): StructureSketch =
        copy(atoms = atoms.map { atom -> atom.copy(x = atom.x + dx, y = atom.y + dy) })

    fun centeredAtOrigin(): StructureSketch {
        if (atoms.isEmpty()) return this
        val minX = atoms.minOf { it.x }
        val maxX = atoms.maxOf { it.x }
        val minY = atoms.minOf { it.y }
        val maxY = atoms.maxOf { it.y }
        val centerX = (minX + maxX) / 2.0
        val centerY = (minY + maxY) / 2.0
        return translate(dx = -centerX, dy = -centerY)
    }

    fun normalizedForCanvas(targetBondLength: Double = 1.55): StructureSketch {
        val centered = centeredAtOrigin()
        if (centered.atoms.size < 2) return centered

        val atomById = centered.atoms.associateBy { it.id }
        val bondLengths = centered.bonds.mapNotNull { bond ->
            val atomA = atomById[bond.atomA] ?: return@mapNotNull null
            val atomB = atomById[bond.atomB] ?: return@mapNotNull null
            hypot(atomA.x - atomB.x, atomA.y - atomB.y).takeIf { it > 0.0001 }
        }
        val currentLength = bondLengths
            .average()
            .takeIf { it.isFinite() && it > 0.0001 }
            ?: run {
                val spanX = centered.atoms.maxOf { it.x } - centered.atoms.minOf { it.x }
                val spanY = centered.atoms.maxOf { it.y } - centered.atoms.minOf { it.y }
                maxOf(spanX, spanY).takeIf { it > 0.0001 } ?: return centered
            }
        val scale = (targetBondLength / currentLength).coerceIn(0.55, 8.0)
        if (abs(scale - 1.0) < 0.04) return centered

        return centered.copy(
            atoms = centered.atoms.map { atom ->
                atom.copy(x = atom.x * scale, y = atom.y * scale)
            }
        )
    }

    fun addRingTemplate(template: RingTemplate, centerX: Double, centerY: Double, radius: Double): StructureSketch {
        val size = template.elements.size
        val startId = (atoms.maxOfOrNull { it.id } ?: 0) + 1
        val ringAtoms = (0 until size).map { index ->
            val angle = (-PI / 2.0) + (2.0 * PI * index / size)
            SketchAtom(
                id = startId + index,
                element = template.elements[index],
                x = centerX + radius * cos(angle),
                y = centerY + radius * sin(angle)
            )
        }
        val startBondId = (bonds.maxOfOrNull { it.id } ?: 0) + 1
        val ringBonds = (0 until size).map { index ->
            val order = if (index in template.doubleBondIndices) {
                BondOrder.DOUBLE.molfileValue
            } else {
                BondOrder.SINGLE.molfileValue
            }
            SketchBond(
                id = startBondId + index,
                atomA = ringAtoms[index].id,
                atomB = ringAtoms[(index + 1) % size].id,
                order = order
            )
        }
        return copy(atoms = atoms + ringAtoms, bonds = bonds + ringBonds)
    }

    fun addChainTemplate(template: ChainTemplate, centerX: Double, centerY: Double, bondLength: Double = 1.45): StructureSketch {
        val startId = (atoms.maxOfOrNull { it.id } ?: 0) + 1
        val startX = centerX - ((template.elements.size - 1) * bondLength / 2.0)
        val chainAtoms = template.elements.mapIndexed { index, element ->
            SketchAtom(
                id = startId + index,
                element = normalizeElement(element),
                x = startX + index * bondLength,
                y = centerY
            )
        }
        val startBondId = (bonds.maxOfOrNull { it.id } ?: 0) + 1
        val chainBonds = chainAtoms.zipWithNext().mapIndexed { index, (atomA, atomB) ->
            SketchBond(
                id = startBondId + index,
                atomA = atomA.id,
                atomB = atomB.id,
                order = template.bondOrder.molfileValue
            )
        }
        return copy(atoms = atoms + chainAtoms, bonds = bonds + chainBonds)
    }

    fun toMolfile(title: String = "ChemSearch Structure Search"): String {
        val atomIndexById = atoms.mapIndexed { index, atom -> atom.id to index + 1 }.toMap()
        return buildString {
            appendLine(title.take(80))
            appendLine("  ChemSearch")
            appendLine("")
            appendLine(
                String.format(
                    Locale.US,
                    "%3d%3d  0  0  0  0            999 V2000",
                    atoms.size,
                    bonds.size
                )
            )
            atoms.forEach { atom ->
                val molfileY = if (atom.y == 0.0) 0.0 else -atom.y
                appendLine(
                    String.format(
                        Locale.US,
                        "%10.4f%10.4f%10.4f %-3s 0  0  0  0  0  0  0  0  0  0  0  0",
                        atom.x,
                        molfileY,
                        0.0,
                        atom.element
                    )
                )
            }
            bonds.forEach { bond ->
                appendLine(
                    String.format(
                        Locale.US,
                        "%3d%3d%3d  0  0  0  0",
                        atomIndexById.getValue(bond.atomA),
                        atomIndexById.getValue(bond.atomB),
                        bond.order
                    )
                )
            }
            val chargedAtoms = atoms.filter { it.charge != 0 }
            if (chargedAtoms.isNotEmpty()) {
                append("M  CHG")
                append(String.format(Locale.US, "%3d", chargedAtoms.size))
                chargedAtoms.forEach { atom ->
                    append(
                        String.format(
                            Locale.US,
                            "%4d%4d",
                            atomIndexById.getValue(atom.id),
                            atom.charge
                        )
                    )
                }
                appendLine()
            }
            appendLine("M  END")
        }.replace(System.lineSeparator(), "\n")
    }

    private fun isConnected(): Boolean {
        if (atoms.isEmpty()) return false
        return connectedComponent(atoms.first().id).size == atoms.size
    }

    private fun connectedComponent(startId: Int): Set<Int> {
        val adjacency = atoms.associate { it.id to mutableSetOf<Int>() }
        bonds.forEach { bond ->
            adjacency[bond.atomA]?.add(bond.atomB)
            adjacency[bond.atomB]?.add(bond.atomA)
        }
        val visited = mutableSetOf<Int>()
        val queue = ArrayDeque<Int>()
        queue.add(atoms.first().id)
        while (queue.isNotEmpty()) {
            val id = queue.removeFirst()
            if (visited.add(id)) {
                adjacency[id].orEmpty().forEach { neighbor ->
                    if (neighbor !in visited) queue.add(neighbor)
                }
            }
        }
        return visited
    }

    companion object {
        fun empty(): StructureSketch = StructureSketch()

        fun fromMolfile(molfile: String): StructureSketch {
            val lines = molfile.replace("\r\n", "\n").replace('\r', '\n').lines()
            if (lines.size < 4) return empty()
            val countsLine = lines.getOrNull(3).orEmpty()
            val atomCount = countsLine.substringSafe(0, 3).trim().toIntOrNull()
                ?: countsLine.trim().split(Regex("\\s+")).getOrNull(0)?.toIntOrNull()
                ?: return empty()
            val bondCount = countsLine.substringSafe(3, 6).trim().toIntOrNull()
                ?: countsLine.trim().split(Regex("\\s+")).getOrNull(1)?.toIntOrNull()
                ?: 0
            val atomStart = 4
            val parsedAtoms = (0 until atomCount).mapNotNull { index ->
                val line = lines.getOrNull(atomStart + index) ?: return@mapNotNull null
                val parts = line.trim().split(Regex("\\s+"))
                val x = line.substringSafe(0, 10).trim().toDoubleOrNull() ?: parts.getOrNull(0)?.toDoubleOrNull()
                val y = line.substringSafe(10, 20).trim().toDoubleOrNull() ?: parts.getOrNull(1)?.toDoubleOrNull()
                val element = line.substringSafe(31, 34).trim().ifBlank { parts.getOrNull(3).orEmpty() }
                if (x == null || y == null || element.isBlank()) return@mapNotNull null
                SketchAtom(
                    id = index + 1,
                    element = normalizeElement(element),
                    x = x,
                    y = if (y == 0.0) 0.0 else -y
                )
            }
            val bondStart = atomStart + atomCount
            val parsedBonds = (0 until bondCount).mapNotNull { index ->
                val line = lines.getOrNull(bondStart + index) ?: return@mapNotNull null
                val parts = line.trim().split(Regex("\\s+"))
                val atomA = line.substringSafe(0, 3).trim().toIntOrNull() ?: parts.getOrNull(0)?.toIntOrNull()
                val atomB = line.substringSafe(3, 6).trim().toIntOrNull() ?: parts.getOrNull(1)?.toIntOrNull()
                val order = line.substringSafe(6, 9).trim().toIntOrNull() ?: parts.getOrNull(2)?.toIntOrNull()
                if (atomA == null || atomB == null || order == null) return@mapNotNull null
                SketchBond(
                    id = index + 1,
                    atomA = atomA,
                    atomB = atomB,
                    order = order.coerceIn(BondOrder.SINGLE.molfileValue, BondOrder.AROMATIC.molfileValue)
                )
            }
            val charges = lines
                .filter { it.startsWith("M  CHG") }
                .flatMap { chargeLine ->
                    val values = chargeLine.drop(6).trim().split(Regex("\\s+")).mapNotNull { it.toIntOrNull() }
                    values.drop(1).chunked(2).mapNotNull { pair ->
                        if (pair.size == 2) pair[0] to pair[1] else null
                    }
                }
                .toMap()
            val chargedAtoms = parsedAtoms.map { atom -> atom.copy(charge = charges[atom.id] ?: 0) }
            return StructureSketch(atoms = chargedAtoms, bonds = parsedBonds)
        }
    }
}

data class StructureSearchWarning(
    val message: String
) {
    companion object {
        fun forSketch(sketch: StructureSketch): List<StructureSearchWarning> = buildList {
            if (sketch.atoms.size >= 2 && sketch.bonds.isEmpty()) {
                add(StructureSearchWarning("Add at least one bond so PubChem can compare a structure."))
            } else if (sketch.atoms.size > 1 && !sketch.canSearch) {
                add(StructureSearchWarning("Connect all fragments before searching."))
            }
            if (sketch.atoms.size > 80) {
                add(StructureSearchWarning("Very large drawings can be slow or may be rejected by PubChem."))
            }
            if (sketch.bonds.any { it.order == BondOrder.AROMATIC.molfileValue }) {
                add(StructureSearchWarning("Aromatic bonds are exported as V2000 aromatic bond type 4."))
            }
        }
    }
}

data class StructureSearchResultItem(
    val cid: Long,
    val title: String,
    val formula: String = "",
    val molecularWeight: String = ""
)

fun pubChemStructureThumbnailUrl(cid: Long): String =
    "https://pubchem.ncbi.nlm.nih.gov/rest/pug/compound/cid/$cid/PNG?record_type=2d&image_size=small"

data class StructureSearchUiState(
    val isLoading: Boolean = false,
    val isStandardizing: Boolean = false,
    val mode: StructureSearchMode = StructureSearchMode.EXACT,
    val similarityThreshold: Int = 85,
    val maxRecords: Int = 30,
    val results: List<StructureSearchResultItem> = emptyList(),
    val error: String? = null,
    val searchedMolfile: String? = null,
    val standardizedSketch: StructureSketch? = null,
    val standardizeMessage: String? = null
)

data class PeriodicElement(
    val atomicNumber: Int,
    val symbol: String,
    val name: String,
    val group: Int,
    val period: Int,
    val tableColumn: Int = group,
    val tableRow: Int = period
) {
    val pubChemProperties: PeriodicElementProperties?
        get() = PubChemPeriodicElementProperties[symbol]

    val extraProperties: PeriodicElementExtraProperties?
        get() = PeriodicElementExtraPropertiesBySymbol[symbol]

    val pubChemName: String
        get() = pubChemProperties?.name?.takeIf { it.isNotBlank() } ?: name

    val category: ElementCategory
        get() = elementCategoryFromGroupBlock(pubChemProperties?.groupBlock)
            ?: elementCategory(symbol, atomicNumber, group)

    val groupBlock: String
        get() = pubChemProperties?.groupBlock?.takeIf { it.isNotBlank() } ?: category.label

    val standardState: String
        get() = pubChemProperties?.standardState?.takeIf { it.isNotBlank() } ?: elementStandardState(symbol)

    val atomicWeight: Double?
        get() = pubChemProperties?.atomicMass?.toDoubleOrNull() ?: STANDARD_ATOMIC_WEIGHTS[symbol]

    val atomicWeightLabel: String
        get() = pubChemProperties?.atomicMass?.takeIf { it.isNotBlank() }
            ?: atomicWeight?.let { "%.3f".format(Locale.US, it).trimEnd('0').trimEnd('.') }
            ?: "Unknown"

    val commonOxidationStates: String
        get() = pubChemProperties?.oxidationStates?.takeIf { it.isNotBlank() } ?: elementOxidationStates(symbol)

    val electronConfiguration: String
        get() = pubChemProperties?.electronConfiguration.orMissing()

    val electronegativity: String
        get() = pubChemProperties?.electronegativity.orMissing()

    val atomicRadius: String
        get() = pubChemProperties?.atomicRadius.orMissing()

    val ionizationEnergy: String
        get() = pubChemProperties?.ionizationEnergy.orMissing()

    val electronAffinity: String
        get() = pubChemProperties?.electronAffinity.orMissing()

    val meltingPoint: String
        get() = pubChemProperties?.meltingPoint.orMissing()

    val boilingPoint: String
        get() = pubChemProperties?.boilingPoint.orMissing()

    val density: String
        get() = pubChemProperties?.density.orMissing()

    val yearDiscovered: String
        get() = pubChemProperties?.yearDiscovered.orMissing()

    val cpkHexColor: String
        get() = pubChemProperties?.cpkHexColor.orMissing()
}

enum class ElementCategory(val label: String) {
    ALKALI_METAL("Alkali metal"),
    ALKALINE_EARTH_METAL("Alkaline earth metal"),
    TRANSITION_METAL("Transition metal"),
    POST_TRANSITION_METAL("Post-transition metal"),
    METALLOID("Metalloid"),
    REACTIVE_NONMETAL("Reactive nonmetal"),
    HALOGEN("Halogen"),
    NOBLE_GAS("Noble gas"),
    LANTHANIDE("Lanthanide"),
    ACTINIDE("Actinide"),
    UNKNOWN("Unknown")
}

val PeriodicTableElements = listOf(
    PeriodicElement(1, "H", "Hydrogen", 1, 1),
    PeriodicElement(2, "He", "Helium", 18, 1),
    PeriodicElement(3, "Li", "Lithium", 1, 2),
    PeriodicElement(4, "Be", "Beryllium", 2, 2),
    PeriodicElement(5, "B", "Boron", 13, 2),
    PeriodicElement(6, "C", "Carbon", 14, 2),
    PeriodicElement(7, "N", "Nitrogen", 15, 2),
    PeriodicElement(8, "O", "Oxygen", 16, 2),
    PeriodicElement(9, "F", "Fluorine", 17, 2),
    PeriodicElement(10, "Ne", "Neon", 18, 2),
    PeriodicElement(11, "Na", "Sodium", 1, 3),
    PeriodicElement(12, "Mg", "Magnesium", 2, 3),
    PeriodicElement(13, "Al", "Aluminium", 13, 3),
    PeriodicElement(14, "Si", "Silicon", 14, 3),
    PeriodicElement(15, "P", "Phosphorus", 15, 3),
    PeriodicElement(16, "S", "Sulfur", 16, 3),
    PeriodicElement(17, "Cl", "Chlorine", 17, 3),
    PeriodicElement(18, "Ar", "Argon", 18, 3),
    PeriodicElement(19, "K", "Potassium", 1, 4),
    PeriodicElement(20, "Ca", "Calcium", 2, 4),
    PeriodicElement(21, "Sc", "Scandium", 3, 4),
    PeriodicElement(22, "Ti", "Titanium", 4, 4),
    PeriodicElement(23, "V", "Vanadium", 5, 4),
    PeriodicElement(24, "Cr", "Chromium", 6, 4),
    PeriodicElement(25, "Mn", "Manganese", 7, 4),
    PeriodicElement(26, "Fe", "Iron", 8, 4),
    PeriodicElement(27, "Co", "Cobalt", 9, 4),
    PeriodicElement(28, "Ni", "Nickel", 10, 4),
    PeriodicElement(29, "Cu", "Copper", 11, 4),
    PeriodicElement(30, "Zn", "Zinc", 12, 4),
    PeriodicElement(31, "Ga", "Gallium", 13, 4),
    PeriodicElement(32, "Ge", "Germanium", 14, 4),
    PeriodicElement(33, "As", "Arsenic", 15, 4),
    PeriodicElement(34, "Se", "Selenium", 16, 4),
    PeriodicElement(35, "Br", "Bromine", 17, 4),
    PeriodicElement(36, "Kr", "Krypton", 18, 4),
    PeriodicElement(37, "Rb", "Rubidium", 1, 5),
    PeriodicElement(38, "Sr", "Strontium", 2, 5),
    PeriodicElement(39, "Y", "Yttrium", 3, 5),
    PeriodicElement(40, "Zr", "Zirconium", 4, 5),
    PeriodicElement(41, "Nb", "Niobium", 5, 5),
    PeriodicElement(42, "Mo", "Molybdenum", 6, 5),
    PeriodicElement(43, "Tc", "Technetium", 7, 5),
    PeriodicElement(44, "Ru", "Ruthenium", 8, 5),
    PeriodicElement(45, "Rh", "Rhodium", 9, 5),
    PeriodicElement(46, "Pd", "Palladium", 10, 5),
    PeriodicElement(47, "Ag", "Silver", 11, 5),
    PeriodicElement(48, "Cd", "Cadmium", 12, 5),
    PeriodicElement(49, "In", "Indium", 13, 5),
    PeriodicElement(50, "Sn", "Tin", 14, 5),
    PeriodicElement(51, "Sb", "Antimony", 15, 5),
    PeriodicElement(52, "Te", "Tellurium", 16, 5),
    PeriodicElement(53, "I", "Iodine", 17, 5),
    PeriodicElement(54, "Xe", "Xenon", 18, 5),
    PeriodicElement(55, "Cs", "Caesium", 1, 6),
    PeriodicElement(56, "Ba", "Barium", 2, 6),
    PeriodicElement(57, "La", "Lanthanum", 3, 6, tableColumn = 4, tableRow = 8),
    PeriodicElement(58, "Ce", "Cerium", 3, 6, tableColumn = 5, tableRow = 8),
    PeriodicElement(59, "Pr", "Praseodymium", 3, 6, tableColumn = 6, tableRow = 8),
    PeriodicElement(60, "Nd", "Neodymium", 3, 6, tableColumn = 7, tableRow = 8),
    PeriodicElement(61, "Pm", "Promethium", 3, 6, tableColumn = 8, tableRow = 8),
    PeriodicElement(62, "Sm", "Samarium", 3, 6, tableColumn = 9, tableRow = 8),
    PeriodicElement(63, "Eu", "Europium", 3, 6, tableColumn = 10, tableRow = 8),
    PeriodicElement(64, "Gd", "Gadolinium", 3, 6, tableColumn = 11, tableRow = 8),
    PeriodicElement(65, "Tb", "Terbium", 3, 6, tableColumn = 12, tableRow = 8),
    PeriodicElement(66, "Dy", "Dysprosium", 3, 6, tableColumn = 13, tableRow = 8),
    PeriodicElement(67, "Ho", "Holmium", 3, 6, tableColumn = 14, tableRow = 8),
    PeriodicElement(68, "Er", "Erbium", 3, 6, tableColumn = 15, tableRow = 8),
    PeriodicElement(69, "Tm", "Thulium", 3, 6, tableColumn = 16, tableRow = 8),
    PeriodicElement(70, "Yb", "Ytterbium", 3, 6, tableColumn = 17, tableRow = 8),
    PeriodicElement(71, "Lu", "Lutetium", 3, 6, tableColumn = 18, tableRow = 8),
    PeriodicElement(72, "Hf", "Hafnium", 4, 6),
    PeriodicElement(73, "Ta", "Tantalum", 5, 6),
    PeriodicElement(74, "W", "Tungsten", 6, 6),
    PeriodicElement(75, "Re", "Rhenium", 7, 6),
    PeriodicElement(76, "Os", "Osmium", 8, 6),
    PeriodicElement(77, "Ir", "Iridium", 9, 6),
    PeriodicElement(78, "Pt", "Platinum", 10, 6),
    PeriodicElement(79, "Au", "Gold", 11, 6),
    PeriodicElement(80, "Hg", "Mercury", 12, 6),
    PeriodicElement(81, "Tl", "Thallium", 13, 6),
    PeriodicElement(82, "Pb", "Lead", 14, 6),
    PeriodicElement(83, "Bi", "Bismuth", 15, 6),
    PeriodicElement(84, "Po", "Polonium", 16, 6),
    PeriodicElement(85, "At", "Astatine", 17, 6),
    PeriodicElement(86, "Rn", "Radon", 18, 6),
    PeriodicElement(87, "Fr", "Francium", 1, 7),
    PeriodicElement(88, "Ra", "Radium", 2, 7),
    PeriodicElement(89, "Ac", "Actinium", 3, 7, tableColumn = 4, tableRow = 9),
    PeriodicElement(90, "Th", "Thorium", 3, 7, tableColumn = 5, tableRow = 9),
    PeriodicElement(91, "Pa", "Protactinium", 3, 7, tableColumn = 6, tableRow = 9),
    PeriodicElement(92, "U", "Uranium", 3, 7, tableColumn = 7, tableRow = 9),
    PeriodicElement(93, "Np", "Neptunium", 3, 7, tableColumn = 8, tableRow = 9),
    PeriodicElement(94, "Pu", "Plutonium", 3, 7, tableColumn = 9, tableRow = 9),
    PeriodicElement(95, "Am", "Americium", 3, 7, tableColumn = 10, tableRow = 9),
    PeriodicElement(96, "Cm", "Curium", 3, 7, tableColumn = 11, tableRow = 9),
    PeriodicElement(97, "Bk", "Berkelium", 3, 7, tableColumn = 12, tableRow = 9),
    PeriodicElement(98, "Cf", "Californium", 3, 7, tableColumn = 13, tableRow = 9),
    PeriodicElement(99, "Es", "Einsteinium", 3, 7, tableColumn = 14, tableRow = 9),
    PeriodicElement(100, "Fm", "Fermium", 3, 7, tableColumn = 15, tableRow = 9),
    PeriodicElement(101, "Md", "Mendelevium", 3, 7, tableColumn = 16, tableRow = 9),
    PeriodicElement(102, "No", "Nobelium", 3, 7, tableColumn = 17, tableRow = 9),
    PeriodicElement(103, "Lr", "Lawrencium", 3, 7, tableColumn = 18, tableRow = 9),
    PeriodicElement(104, "Rf", "Rutherfordium", 4, 7),
    PeriodicElement(105, "Db", "Dubnium", 5, 7),
    PeriodicElement(106, "Sg", "Seaborgium", 6, 7),
    PeriodicElement(107, "Bh", "Bohrium", 7, 7),
    PeriodicElement(108, "Hs", "Hassium", 8, 7),
    PeriodicElement(109, "Mt", "Meitnerium", 9, 7),
    PeriodicElement(110, "Ds", "Darmstadtium", 10, 7),
    PeriodicElement(111, "Rg", "Roentgenium", 11, 7),
    PeriodicElement(112, "Cn", "Copernicium", 12, 7),
    PeriodicElement(113, "Nh", "Nihonium", 13, 7),
    PeriodicElement(114, "Fl", "Flerovium", 14, 7),
    PeriodicElement(115, "Mc", "Moscovium", 15, 7),
    PeriodicElement(116, "Lv", "Livermorium", 16, 7),
    PeriodicElement(117, "Ts", "Tennessine", 17, 7),
    PeriodicElement(118, "Og", "Oganesson", 18, 7)
)

private fun normalizeElement(element: String): String {
    val clean = element.trim()
    if (clean.isBlank()) return "C"
    return clean.lowercase(Locale.US).replaceFirstChar { it.titlecase(Locale.US) }
}

fun elementBySymbol(symbol: String): PeriodicElement? =
    PeriodicTableElements.firstOrNull { it.symbol.equals(symbol.trim(), ignoreCase = true) }

fun elementCategory(symbol: String, atomicNumber: Int, group: Int): ElementCategory = when {
    atomicNumber in 57..71 -> ElementCategory.LANTHANIDE
    atomicNumber in 89..103 -> ElementCategory.ACTINIDE
    symbol in setOf("H", "C", "N", "O", "P", "S", "Se") -> ElementCategory.REACTIVE_NONMETAL
    group == 1 -> ElementCategory.ALKALI_METAL
    group == 2 -> ElementCategory.ALKALINE_EARTH_METAL
    group in 3..12 -> ElementCategory.TRANSITION_METAL
    group == 17 -> ElementCategory.HALOGEN
    group == 18 -> ElementCategory.NOBLE_GAS
    symbol in setOf("B", "Si", "Ge", "As", "Sb", "Te", "Po") -> ElementCategory.METALLOID
    symbol in setOf("Al", "Ga", "In", "Sn", "Tl", "Pb", "Bi", "Nh", "Fl", "Mc", "Lv") -> ElementCategory.POST_TRANSITION_METAL
    else -> ElementCategory.UNKNOWN
}

fun elementCategoryFromGroupBlock(groupBlock: String?): ElementCategory? = when (groupBlock?.lowercase(Locale.US)) {
    "alkali metal" -> ElementCategory.ALKALI_METAL
    "alkaline earth metal" -> ElementCategory.ALKALINE_EARTH_METAL
    "transition metal" -> ElementCategory.TRANSITION_METAL
    "post-transition metal" -> ElementCategory.POST_TRANSITION_METAL
    "metalloid" -> ElementCategory.METALLOID
    "nonmetal" -> ElementCategory.REACTIVE_NONMETAL
    "halogen" -> ElementCategory.HALOGEN
    "noble gas" -> ElementCategory.NOBLE_GAS
    "lanthanide" -> ElementCategory.LANTHANIDE
    "actinide" -> ElementCategory.ACTINIDE
    else -> null
}

private fun String?.orMissing(): String =
    this?.takeIf { it.isNotBlank() } ?: "Not listed"

fun elementStandardState(symbol: String): String = when (symbol) {
    "H", "N", "O", "F", "Cl", "He", "Ne", "Ar", "Kr", "Xe", "Rn" -> "Gas"
    "Br", "Hg" -> "Liquid"
    else -> "Solid"
}

fun elementOxidationStates(symbol: String): String = commonElementOxidationStates[symbol] ?: "Varies"

private val commonElementOxidationStates = mapOf(
    "H" to "+1, -1",
    "Li" to "+1",
    "Na" to "+1",
    "K" to "+1",
    "Rb" to "+1",
    "Cs" to "+1",
    "Be" to "+2",
    "Mg" to "+2",
    "Ca" to "+2",
    "Sr" to "+2",
    "Ba" to "+2",
    "B" to "+3",
    "C" to "-4, +2, +4",
    "N" to "-3, +3, +5",
    "O" to "-2, -1",
    "F" to "-1",
    "Al" to "+3",
    "Si" to "-4, +4",
    "P" to "-3, +3, +5",
    "S" to "-2, +4, +6",
    "Cl" to "-1, +1, +3, +5, +7",
    "Fe" to "+2, +3",
    "Co" to "+2, +3",
    "Ni" to "+2, +3",
    "Cu" to "+1, +2",
    "Zn" to "+2",
    "Br" to "-1, +1, +3, +5",
    "Ag" to "+1",
    "I" to "-1, +1, +5, +7",
    "Au" to "+1, +3",
    "Hg" to "+1, +2",
    "Pb" to "+2, +4"
)

private fun String.substringSafe(startIndex: Int, endIndex: Int): String =
    if (startIndex >= length) "" else substring(startIndex, endIndex.coerceAtMost(length))

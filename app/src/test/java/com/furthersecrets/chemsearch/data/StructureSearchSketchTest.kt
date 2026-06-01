package com.furthersecrets.chemsearch.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class StructureSearchSketchTest {
    @Test
    fun benzeneTemplateCreatesSixCarbonRingWithAlternatingBonds() {
        val sketch = StructureSketch.empty()
            .addRingTemplate(RingTemplate.BENZENE, centerX = 0.0, centerY = 0.0, radius = 1.2)

        assertEquals(6, sketch.atoms.size)
        assertEquals(6, sketch.bonds.size)
        assertTrue(sketch.atoms.all { it.element == "C" })
        assertEquals(listOf(2, 1, 2, 1, 2, 1), sketch.bonds.map { it.order })
    }

    @Test
    fun cyclobutaneTemplateCreatesFourCarbonRing() {
        val sketch = StructureSketch.empty()
            .addRingTemplate(RingTemplate.CYCLOBUTANE, centerX = 0.0, centerY = 0.0, radius = 1.2)

        assertEquals(4, sketch.atoms.size)
        assertEquals(4, sketch.bonds.size)
        assertTrue(sketch.atoms.all { it.element == "C" })
        assertEquals(listOf(1, 1, 1, 1), sketch.bonds.map { it.order })
    }

    @Test
    fun pyridineTemplateCreatesNitrogenAromaticRing() {
        val sketch = StructureSketch.empty()
            .addRingTemplate(RingTemplate.PYRIDINE, centerX = 0.0, centerY = 0.0, radius = 1.2)

        assertEquals(6, sketch.atoms.size)
        assertEquals(6, sketch.bonds.size)
        assertEquals("N", sketch.atoms.first().element)
        assertEquals(listOf(2, 1, 2, 1, 2, 1), sketch.bonds.map { it.order })
    }

    @Test
    fun imidazoleTemplateCreatesFiveMemberedRingWithTwoNitrogens() {
        val sketch = StructureSketch.empty()
            .addRingTemplate(RingTemplate.IMIDAZOLE, centerX = 0.0, centerY = 0.0, radius = 1.2)

        assertEquals(5, sketch.atoms.size)
        assertEquals(listOf("N", "C", "N", "C", "C"), sketch.atoms.map { it.element })
        assertEquals(listOf(2, 1, 1, 2, 1), sketch.bonds.map { it.order })
    }

    @Test
    fun chainTemplateCreatesLinearFragment() {
        val sketch = StructureSketch.empty()
            .addChainTemplate(ChainTemplate.BUTYL, centerX = 0.0, centerY = 0.0)

        assertEquals(4, sketch.atoms.size)
        assertEquals(3, sketch.bonds.size)
        assertTrue(sketch.atoms.all { it.element == "C" })
        assertEquals(listOf(1, 1, 1), sketch.bonds.map { it.order })
    }

    @Test
    fun functionalChainTemplatesUseExpectedBondOrder() {
        val carbonyl = StructureSketch.empty()
            .addChainTemplate(ChainTemplate.CARBONYL, centerX = 0.0, centerY = 0.0)
        val nitrile = StructureSketch.empty()
            .addChainTemplate(ChainTemplate.NITRILE, centerX = 0.0, centerY = 0.0)

        assertEquals(BondOrder.DOUBLE.molfileValue, carbonyl.bonds.first().order)
        assertEquals(BondOrder.TRIPLE.molfileValue, nitrile.bonds.first().order)
    }

    @Test
    fun molfileExporterWritesV2000CountsAtomsAndBonds() {
        val sketch = StructureSketch.empty()
            .addAtom("C", 0.0, 0.0)
            .addAtom("O", 1.4, 0.0)
            .addBond(1, 2, BondOrder.DOUBLE)

        val molfile = sketch.toMolfile("ChemSearch Structure Search")

        assertTrue(molfile.contains("ChemSearch Structure Search"))
        assertTrue(molfile.contains("  2  1  0  0  0  0            999 V2000"))
        assertTrue(molfile.contains("    0.0000    0.0000    0.0000 C"))
        assertTrue(molfile.contains("    1.4000    0.0000    0.0000 O"))
        assertTrue(molfile.contains("  1  2  2  0  0  0  0"))
        assertTrue(molfile.endsWith("M  END\n"))
    }

    @Test
    fun validatesEmptySketchAndDisconnectedFragments() {
        assertFalse(StructureSketch.empty().canSearch)

        val disconnected = StructureSketch.empty()
            .addAtom("C", 0.0, 0.0)
            .addAtom("O", 4.0, 0.0)

        assertFalse(disconnected.canSearch)
    }

    @Test
    fun molfileDoesNotAddImplicitHydrogenAtoms() {
        val sketch = StructureSketch.empty()
            .addAtom("C", 0.0, 0.0)
            .addAtom("C", 1.4, 0.0)
            .addBond(1, 2, BondOrder.SINGLE)

        val molfile = sketch.toMolfile()

        assertTrue(molfile.contains("  2  1  0  0  0  0            999 V2000"))
        assertFalse(molfile.lines().any { it.contains(" H ") })
    }

    @Test
    fun buildsPubChemThumbnailUrlForStructureResults() {
        assertEquals(
            "https://pubchem.ncbi.nlm.nih.gov/rest/pug/compound/cid/887/PNG?record_type=2d&image_size=small",
            pubChemStructureThumbnailUrl(887)
        )
    }

    @Test
    fun periodicTableHasAll118ElementsAndPositions() {
        assertEquals(118, PeriodicTableElements.size)
        assertEquals("H", PeriodicTableElements.first().symbol)
        assertEquals("Hydrogen", PeriodicTableElements.first().name)
        assertEquals(1, PeriodicTableElements.first().period)
        assertEquals(1, PeriodicTableElements.first().group)

        val oganesson = PeriodicTableElements.last()
        assertEquals(118, oganesson.atomicNumber)
        assertEquals("Og", oganesson.symbol)
        assertEquals(7, oganesson.period)
        assertEquals(18, oganesson.group)
    }

    @Test
    fun periodicTableHasPubChemPropertiesForEveryElement() {
        assertEquals(118, PubChemPeriodicElementProperties.size)
        assertTrue(PeriodicTableElements.all { it.pubChemProperties != null })

        val hydrogen = PeriodicTableElements.first()
        assertEquals("1.0080", hydrogen.atomicWeightLabel)
        assertEquals("1s1", hydrogen.electronConfiguration)
        assertEquals("+1, -1", hydrogen.commonOxidationStates)
        assertEquals("Nonmetal", hydrogen.groupBlock)
        assertEquals("FFFFFF", hydrogen.cpkHexColor)
    }

    @Test
    fun cyclesBondOrderAndDeletesBonds() {
        val sketch = StructureSketch.empty()
            .addAtom("C", 0.0, 0.0)
            .addAtom("O", 1.4, 0.0)
            .addBond(1, 2, BondOrder.SINGLE)

        val doubled = sketch.cycleBondOrder(1)
        assertEquals(BondOrder.DOUBLE.molfileValue, doubled.bonds.first().order)

        val deleted = doubled.removeBond(1)
        assertEquals(0, deleted.bonds.size)
    }

    @Test
    fun duplicatesConnectedFragmentWithOffset() {
        val sketch = StructureSketch.empty()
            .addAtom("C", 0.0, 0.0)
            .addAtom("O", 1.4, 0.0)
            .addBond(1, 2, BondOrder.DOUBLE)

        val duplicated = sketch.duplicateConnectedFragmentFrom(1, dx = 2.0, dy = 1.0)

        assertEquals(4, duplicated.atoms.size)
        assertEquals(2, duplicated.bonds.size)
        assertEquals(listOf(1, 2, 3, 4), duplicated.atoms.map { it.id })
        assertEquals(BondOrder.DOUBLE.molfileValue, duplicated.bonds.last().order)
    }

    @Test
    fun parsesMolfileBackIntoSketch() {
        val sketch = StructureSketch.empty()
            .addAtom("C", 0.0, 0.0)
            .addAtom("O", 1.4, 0.0)
            .addBond(1, 2, BondOrder.DOUBLE)

        val parsed = StructureSketch.fromMolfile(sketch.toMolfile())

        assertEquals(2, parsed.atoms.size)
        assertEquals("O", parsed.atoms[1].element)
        assertEquals(BondOrder.DOUBLE.molfileValue, parsed.bonds.first().order)
    }

    @Test
    fun normalizesTinyImportedCoordinatesForCanvas() {
        val sketch = StructureSketch.empty()
            .addAtom("C", 0.0, 0.0)
            .addAtom("H", 0.2, 0.0)
            .addBond(1, 2, BondOrder.SINGLE)

        val normalized = sketch.normalizedForCanvas()
        val atomA = normalized.atoms[0]
        val atomB = normalized.atoms[1]
        val distance = hypot(atomA.x - atomB.x, atomA.y - atomB.y)

        assertTrue(distance > 1.4)
    }

    @Test
    fun buildsFormulaFromSketchAndWarnings() {
        val ethanolSkeleton = StructureSketch.empty()
            .addAtom("C", 0.0, 0.0)
            .addAtom("C", 1.4, 0.0)
            .addAtom("O", 2.8, 0.0)
            .addBond(1, 2, BondOrder.SINGLE)
            .addBond(2, 3, BondOrder.SINGLE)
        val singleAtom = StructureSketch.empty()
            .addAtom("C", 0.0, 0.0)

        assertEquals("C2O", ethanolSkeleton.formula)
        assertTrue(StructureSearchWarning.forSketch(StructureSketch.empty()).isEmpty())
        assertFalse(
            StructureSearchWarning.forSketch(singleAtom)
                .any { it.message == "Draw at least two connected atoms before searching." }
        )
        assertTrue(StructureSearchWarning.forSketch(ethanolSkeleton).isEmpty())
    }

    @Test
    fun mapsStructureSearchModesToPubChemFastOperations() {
        assertEquals("fastidentity", StructureSearchMode.EXACT.pubChemOperation)
        assertEquals("fastsimilarity_2d", StructureSearchMode.SIMILAR.pubChemOperation)
        assertEquals("fastsuperstructure", StructureSearchMode.CONTAINS.pubChemOperation)
        assertEquals("fastsubstructure", StructureSearchMode.PART_OF.pubChemOperation)
    }
}

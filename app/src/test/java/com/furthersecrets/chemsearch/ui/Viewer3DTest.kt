package com.furthersecrets.chemsearch.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Viewer3DTest {
    @Test
    fun parseSdfReadsV2000AtomsAndBonds() {
        val sdf = """
            Water
              ChemSearch

              3  2  0  0  0  0            999 V2000
                0.0000    0.0000    0.0000 O   0  0  0  0  0  0  0  0  0  0  0  0
                0.9572    0.0000    0.0000 H   0  0  0  0  0  0  0  0  0  0  0  0
               -0.2390    0.9270    0.0000 H   0  0  0  0  0  0  0  0  0  0  0  0
              1  2  1  0  0  0  0
              1  3  1  0  0  0  0
            M  END
        """.trimIndent()

        val molecule = parseSdf(sdf)

        assertEquals(listOf("O", "H", "H"), molecule.atoms.map { it.element })
        assertEquals(2, molecule.bonds.size)
        assertEquals(0, molecule.bonds.first().a1)
        assertEquals(1, molecule.bonds.first().a2)
    }

    @Test
    fun parseSdfReadsV3000AtomsAndBonds() {
        val sdf = """
            Water
              ChemSearch

              0  0  0     0  0            999 V3000
            M  V30 BEGIN CTAB
            M  V30 COUNTS 3 2 0 0 0
            M  V30 BEGIN ATOM
            M  V30 1 O 0.0000 0.0000 0.0000 0
            M  V30 2 H 0.9572 0.0000 0.0000 0
            M  V30 3 H -0.2390 0.9270 0.0000 0
            M  V30 END ATOM
            M  V30 BEGIN BOND
            M  V30 1 1 1 2
            M  V30 2 1 1 3
            M  V30 END BOND
            M  V30 END CTAB
            M  END
        """.trimIndent()

        val molecule = parseSdf(sdf)

        assertEquals(listOf("O", "H", "H"), molecule.atoms.map { it.element })
        assertEquals(2, molecule.bonds.size)
        assertEquals(Bond3D(a1 = 0, a2 = 1, type = 1), molecule.bonds.first())
    }

    @Test
    fun viewerStateKeyChangesWhenSdfChangesForSameCid() {
        val first = viewer3DStateKey(cid = -1, sdfData = "first")
        val second = viewer3DStateKey(cid = -1, sdfData = "second")

        assertTrue(first != second)
    }

    @Test
    fun displayBoundsUseCenterOfExtentsInsteadOfAveragePosition() {
        val molecule = Molecule3D(
            atoms = listOf(
                Atom3D(0f, 0f, 0f, "C"),
                Atom3D(2f, 0f, 0f, "C"),
                Atom3D(100f, 0f, 0f, "O")
            ),
            bonds = emptyList()
        )

        val bounds = calculateViewerBounds(molecule)

        assertEquals(50f, bounds.centerX, 0.0001f)
        assertEquals(50f, bounds.maxExtent, 0.0001f)
    }
}

package com.furthersecrets.chemsearch.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ChemicalDatabaseDisplayTest {
    @Test
    fun formatsIonChargesInsideEquations() {
        assertEquals("NH₄⁺ + OH⁻", chemicalDatabaseDisplayText("Equation", "NH4+ + OH-"))
        assertEquals("Cu²⁺ + 2e⁻ ⟶ Cu", chemicalDatabaseDisplayText("Equation", "Cu^2+ + 2e- -> Cu"))
        assertEquals("SO₄²⁻", chemicalDatabaseDisplayText("Formula", "SO4^2-"))
    }

    @Test
    fun formatsFunctionalGroupStructures() {
        assertEquals("R-NH₂", chemicalDatabaseDisplayText("Structure", "R-NH2"))
        assertEquals("C₂H₅OH", chemicalDatabaseDisplayText("Example", "C2H5OH"))
    }

    @Test
    fun leavesPlainReferenceTextAlone() {
        assertEquals("blue-black complex", chemicalDatabaseDisplayText("Observation", "blue-black complex"))
    }
}

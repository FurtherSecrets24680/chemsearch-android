package com.furthersecrets.chemsearch.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ChemicalExpressionDisplayTest {
    @Test
    fun formatsWholeEquationsWithChargesStatesAndUnicodeArrows() {
        assertEquals(
            "Ca(OH)₂ + Na₂CO₃ ⟶ CaCO₃(s) + 2NaOH(aq)",
            toChemicalExpressionDisplay("Ca(OH)2 + Na2CO3 -> CaCO3(s) + 2NaOH(aq)")
        )
        assertEquals(
            "Ca²⁺ + CO₃²⁻ ⟶ CaCO₃(s)",
            toChemicalExpressionDisplay("Ca2+ + CO3^2- -> CaCO3(s)")
        )
        assertEquals(
            "NH₃ + H₂O ⇌ NH₄⁺ + OH⁻",
            toChemicalExpressionDisplay("NH3 + H2O <-> NH4+ + OH-")
        )
    }

    @Test
    fun formatsBracketedCoordinationFormulas() {
        assertEquals("K₄[Fe(CN)₆]", toChemicalExpressionDisplay("K4[Fe(CN)6]"))
        assertEquals("Na₂[Fe(CN)₅NO]", toChemicalExpressionDisplay("Na2[Fe(CN)5NO]"))
        assertEquals("[Co(NH₃)₆]Cl₃", toChemicalExpressionDisplay("[Co(NH3)6]Cl3"))
    }

    @Test
    fun formatsGroupedInorganicSaltFormulas() {
        assertEquals("(NH₄)₂HPO₄", toChemicalExpressionDisplay("(NH4)2HPO4"))
        assertEquals("NH₄OH", toChemicalExpressionDisplay("NH4OH"))
    }
}

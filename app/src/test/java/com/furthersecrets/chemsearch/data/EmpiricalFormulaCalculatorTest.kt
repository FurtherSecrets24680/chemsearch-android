package com.furthersecrets.chemsearch.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmpiricalFormulaCalculatorTest {
    @Test
    fun calculatesEmpiricalAndMolecularFormulaFromPercentComposition() {
        val result = calculateEmpiricalFormulaFromComposition(
            components = listOf(
                FormulaCompositionComponent("C", 40.00),
                FormulaCompositionComponent("H", 6.71),
                FormulaCompositionComponent("O", 53.29)
            ),
            mode = FormulaCompositionMode.PERCENT,
            molecularMass = 180.16
        )

        assertNull(result.error)
        assertEquals("CH2O", result.empiricalFormula)
        assertEquals(30.03, result.empiricalMass, 0.03)
        assertEquals(6, result.molecularMultiplier)
        assertEquals("C6H12O6", result.molecularFormula)
        assertEquals(listOf("C", "H", "O"), result.rows.map { it.element })
    }

    @Test
    fun calculatesEmpiricalFormulaFromMassComposition() {
        val result = calculateEmpiricalFormulaFromComposition(
            components = listOf(
                FormulaCompositionComponent("C", 2.40),
                FormulaCompositionComponent("H", 0.40),
                FormulaCompositionComponent("O", 3.20)
            ),
            mode = FormulaCompositionMode.MASS
        )

        assertNull(result.error)
        assertEquals("CH2O", result.empiricalFormula)
        assertEquals(null, result.molecularFormula)
        assertTrue(result.rows.all { it.moles > 0.0 })
    }

    @Test
    fun reducesExistingFormulaToEmpiricalFormula() {
        val result = calculateEmpiricalFormulaFromMolecularFormula("C6H12O6", molecularMass = 180.16)

        assertNull(result.error)
        assertEquals("CH2O", result.empiricalFormula)
        assertEquals("C6H12O6", result.molecularFormula)
        assertEquals(6, result.molecularMultiplier)
    }

    @Test
    fun reportsUnknownElementsInComposition() {
        val result = calculateEmpiricalFormulaFromComposition(
            components = listOf(FormulaCompositionComponent("Xx", 10.0)),
            mode = FormulaCompositionMode.MASS
        )

        assertEquals("Unknown element(s): Xx", result.error)
    }
}

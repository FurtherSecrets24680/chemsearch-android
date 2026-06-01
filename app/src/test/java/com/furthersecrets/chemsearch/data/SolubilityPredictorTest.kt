package com.furthersecrets.chemsearch.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SolubilityPredictorTest {
    @Test
    fun predictsSilverChloridePrecipitate() {
        val result = predictPrecipitation("AgNO3", "NaCl")

        assertNull(result.error)
        assertEquals("AgNO3 + NaCl ⟶ AgCl(s) + NaNO3(aq)", result.molecularEquation)
        assertEquals("Ag+ + Cl- ⟶ AgCl(s)", result.netIonicEquation)
        assertEquals("AgCl", result.precipitates.single().formula)
        assertEquals(SolubilityState.INSOLUBLE, result.precipitates.single().solubility.state)
    }

    @Test
    fun balancesBariumSulfatePrecipitation() {
        val result = predictPrecipitation("BaCl2", "Na2SO4")

        assertNull(result.error)
        assertEquals("BaCl2 + Na2SO4 ⟶ BaSO4(s) + 2NaCl(aq)", result.molecularEquation)
        assertEquals("Ba2+ + SO4^2- ⟶ BaSO4(s)", result.netIonicEquation)
        assertEquals(listOf("BaSO4"), result.precipitates.map { it.formula })
    }

    @Test
    fun reportsNoPrecipitateWhenProductsStaySoluble() {
        val result = predictPrecipitation("NaCl", "KNO3")

        assertNull(result.error)
        assertTrue(result.precipitates.isEmpty())
        assertEquals("No precipitate predicted", result.summary)
    }

    @Test
    fun parsesPolyatomicIonsWithParentheses() {
        val result = predictPrecipitation("Ca(OH)2", "Na2CO3")

        assertNull(result.error)
        assertEquals("CaCO3", result.precipitates.single().formula)
        assertEquals("Ca2+ + CO3^2- ⟶ CaCO3(s)", result.netIonicEquation)
    }

    @Test
    fun reportsUnknownIonicCompounds() {
        val result = predictPrecipitation("C6H12O6", "NaCl")

        assertEquals("Could not identify common aqueous ions in C6H12O6.", result.error)
    }
}

package com.furthersecrets.chemsearch.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvancedSearchTest {
    @Test
    fun parsesElementFiltersIntoValidSymbols() {
        assertEquals(
            setOf("C", "H", "Cl"),
            parseElementFilterText("c, H cl nonsense")
        )
    }

    @Test
    fun matchesFormulaWeightChargeAndElementFilters() {
        val property = CompoundProperty(
            cid = 702,
            molecularFormula = "C2H6O",
            molecularWeight = "46.07",
            charge = 0
        )
        val filters = AdvancedSearchFilters(
            includeElements = setOf("C", "O"),
            excludeElements = setOf("Cl"),
            minMolecularWeight = 40.0,
            maxMolecularWeight = 50.0,
            charge = 0
        )

        assertTrue(advancedSearchMatchesFilters(property, filters))
    }

    @Test
    fun rejectsExcludedElementsAndMissingRequiredMetadata() {
        val property = CompoundProperty(
            cid = 2244,
            molecularFormula = "C9H8O4",
            molecularWeight = "180.16",
            charge = 0
        )

        assertFalse(
            advancedSearchMatchesFilters(
                property,
                AdvancedSearchFilters(excludeElements = setOf("O"))
            )
        )
        assertFalse(
            advancedSearchMatchesFilters(
                property,
                AdvancedSearchFilters(requireThreeD = true),
                hasThreeD = false
            )
        )
    }

    @Test
    fun guessesSearchTypeFromQuery() {
        assertEquals(AdvancedSearchType.CID, advancedSearchTypeForQuery("702"))
        assertEquals(AdvancedSearchType.CAS, advancedSearchTypeForQuery("64-17-5"))
        assertEquals(AdvancedSearchType.FORMULA, advancedSearchTypeForQuery("C2H6O"))
        assertEquals(AdvancedSearchType.NAME, advancedSearchTypeForQuery("ethanol"))
    }
}

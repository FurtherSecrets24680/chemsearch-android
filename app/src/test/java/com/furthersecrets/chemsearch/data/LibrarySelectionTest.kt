package com.furthersecrets.chemsearch.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySelectionTest {
    @Test
    fun compareButtonNeedsTwoSelectedCompounds() {
        assertFalse(shouldShowLibraryCompareButton(0))
        assertFalse(shouldShowLibraryCompareButton(1))
        assertTrue(shouldShowLibraryCompareButton(2))
        assertTrue(shouldShowLibraryCompareButton(3))
    }

    @Test
    fun buildsSelectionItemsFromLibrarySources() {
        val favorite = FavoriteCompound(
            cid = 702,
            name = "Ethanol",
            formula = "C2H6O",
            molecularWeight = "46.07",
            iupacName = "ethanol"
        )
        val entry = ChemicalDbEntry(
            id = "sodium-chloride",
            category = ChemicalDbCategory.SUBSTANCES,
            title = "Sodium chloride",
            formula = "NaCl",
            searchQuery = "sodium chloride"
        )

        assertEquals(
            LibrarySelectionItem("favorite:702", "Ethanol", "Ethanol"),
            favorite.toLibrarySelectionItem()
        )
        assertEquals(
            LibrarySelectionItem("database:SUBSTANCES:sodium-chloride", "sodium chloride", "Sodium chloride"),
            entry.toLibrarySelectionItem()
        )
    }

    @Test
    fun chemicalDatabaseCompareSelectionOnlySupportsSubstancesAndIons() {
        val substance = ChemicalDbEntry(
            id = "ethanol",
            category = ChemicalDbCategory.SUBSTANCES,
            title = "Ethanol",
            formula = "C2H6O",
            searchQuery = "ethanol"
        )
        val ion = ChemicalDbEntry(
            id = "ammonium",
            category = ChemicalDbCategory.IONS,
            title = "Ammonium",
            formula = "NH4+",
            searchQuery = "ammonium"
        )
        val functionalGroup = ChemicalDbEntry(
            id = "alcohol",
            category = ChemicalDbCategory.FUNCTIONAL_GROUPS,
            title = "Alcohol",
            formula = "R-OH",
            searchQuery = "alcohol"
        )
        val reaction = ChemicalDbEntry(
            id = "neutralization",
            category = ChemicalDbCategory.REACTIONS,
            title = "Neutralization",
            formula = "H+ + OH- -> H2O",
            searchQuery = "neutralization"
        )

        assertEquals("ethanol", substance.toComparableLibrarySelectionItem()?.query)
        assertEquals("ammonium", ion.toComparableLibrarySelectionItem()?.query)
        assertEquals(null, functionalGroup.toComparableLibrarySelectionItem())
        assertEquals(null, reaction.toComparableLibrarySelectionItem())
    }

    @Test
    fun compareQueriesAreDistinctAndKeepSelectionOrder() {
        val queries = buildLibraryCompareQueries(
            listOf(
                LibrarySelectionItem("favorite:1", "ethanol", "Ethanol"),
                LibrarySelectionItem("download:2", "methanol", "Methanol"),
                LibrarySelectionItem("database:SUBSTANCES:ethanol", "Ethanol", "Ethanol")
            )
        )

        assertEquals(listOf("ethanol", "methanol"), queries)
    }
}

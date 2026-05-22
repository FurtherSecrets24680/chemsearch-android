package com.furthersecrets.chemsearch.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ChemicalDatabaseSummaryTest {
    @Test
    fun summarizesDatabaseEntriesByCategory() {
        val entries = listOf(
            entry(ChemicalDbCategory.SUBSTANCES),
            entry(ChemicalDbCategory.SUBSTANCES),
            entry(ChemicalDbCategory.IONS),
            entry(ChemicalDbCategory.FUNCTIONAL_GROUPS),
            entry(ChemicalDbCategory.REACTIONS),
            entry(ChemicalDbCategory.REACTIONS),
            entry(ChemicalDbCategory.REACTIONS)
        )

        val summary = summarizeChemicalDatabase(entries)

        assertEquals(2, summary.substances)
        assertEquals(1, summary.ions)
        assertEquals(1, summary.functionalGroups)
        assertEquals(3, summary.reactions)
    }

    @Test
    fun summarizesEmptyDatabase() {
        val summary = summarizeChemicalDatabase(emptyList())

        assertEquals(0, summary.substances)
        assertEquals(0, summary.ions)
        assertEquals(0, summary.functionalGroups)
        assertEquals(0, summary.reactions)
    }

    @Test
    fun buildsDisplayRowsInLibraryOrder() {
        val rows = chemicalDatabaseSummaryRows(
            ChemicalDatabaseSummary(
                substances = 2,
                ions = 4,
                functionalGroups = 6,
                reactions = 8
            )
        )

        assertEquals(
            listOf(
                ChemicalDatabaseSummaryRow("Substances:", 2),
                ChemicalDatabaseSummaryRow("Ions:", 4),
                ChemicalDatabaseSummaryRow("Functional groups:", 6),
                ChemicalDatabaseSummaryRow("Reactions:", 8)
            ),
            rows
        )
    }

    private fun entry(category: ChemicalDbCategory): ChemicalDbEntry =
        ChemicalDbEntry(
            id = category.name,
            category = category,
            title = category.label
        )
}

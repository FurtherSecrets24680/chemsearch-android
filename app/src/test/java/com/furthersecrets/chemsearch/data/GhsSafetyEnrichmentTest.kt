package com.furthersecrets.chemsearch.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GhsSafetyEnrichmentTest {
    @Test
    fun addsHazardCodeMeaningsAndSourcePolicy() {
        val data = GhsData(
            signalWord = "Danger",
            hazardStatements = listOf("H225: Highly flammable liquid and vapour", "H319: Causes serious eye irritation"),
            pictogramCodes = listOf("GHS02", "GHS07")
        )

        val summary = enrichGhsSafety(data, retrievedAt = 1234L)

        assertEquals("PubChem GHS Classification", summary.source.name)
        assertEquals(2, summary.hazards.size)
        assertEquals("Highly flammable liquid and vapour", summary.hazards.first { it.code == "H225" }.meaning)
        assertTrue(summary.disclaimer.contains("does not replace", ignoreCase = true))
        assertNotNull(summary.retrievedAt)
    }
}

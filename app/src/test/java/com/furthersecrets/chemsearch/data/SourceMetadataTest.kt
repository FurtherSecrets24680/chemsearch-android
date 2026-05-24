package com.furthersecrets.chemsearch.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceMetadataTest {
    @Test
    fun sourceReliabilityMatrixCoversMajorAppSources() {
        val matrix = buildSourceReliabilityMatrix()

        assertTrue(matrix.any { it.name == "IUPAC Gold Book" && it.confidence == SourceConfidence.HIGH })
        assertTrue(matrix.any { it.name == "PubChem PUG REST" && it.appliesTo.contains(AppDataArea.COMPOUND_PROPERTIES) })
        assertTrue(matrix.any { it.name == "NCI/CADD Resolver" && it.appliesTo.contains(AppDataArea.STRUCTURE_FALLBACK) })
        assertTrue(matrix.all { it.url.startsWith("https://") })
    }

    @Test
    fun pubChemMetadataIsHighConfidenceButFieldSpecific() {
        val metadata = pubChemSourceMetadata(AppDataArea.GHS_SAFETY)

        assertEquals("PubChem PUG View", metadata.name)
        assertEquals(SourceConfidence.MEDIUM_HIGH, metadata.confidence)
    }
}

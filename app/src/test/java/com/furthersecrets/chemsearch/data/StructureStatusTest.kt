package com.furthersecrets.chemsearch.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StructureStatusTest {
    @Test
    fun labelsPubChem3dAsConformer() {
        val status = describeStructureStatus(hasSdf = true, source = SdfSource.PUBCHEM, message = null)

        assertEquals("PubChem 3D conformer", status.label)
        assertFalse(status.estimated)
        assertEquals(SourceConfidence.HIGH, status.confidence)
    }

    @Test
    fun labelsGeneratedFallbackAsEstimate() {
        val status = describeStructureStatus(
            hasSdf = true,
            source = SdfSource.GENERATED,
            message = "Generated estimate from SMILES using the NCI/CADD resolver."
        )

        assertEquals("Generated fallback", status.label)
        assertTrue(status.estimated)
        assertEquals(SourceConfidence.MEDIUM, status.confidence)
    }

    @Test
    fun unavailableStructureExplainsIonicAndMetallicLimits() {
        val status = describeStructureStatus(hasSdf = false, source = null, message = null)

        assertEquals("Structure unavailable", status.label)
        assertTrue(status.detail.contains("ionic lattice", ignoreCase = true))
    }
}

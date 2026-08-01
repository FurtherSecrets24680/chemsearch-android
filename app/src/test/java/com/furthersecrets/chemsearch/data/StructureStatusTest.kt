package com.furthersecrets.chemsearch.data

import com.furthersecrets.chemsearch.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StructureStatusTest {
    @Test
    fun labelsPubChem3dAsConformer() {
        val status = describeStructureStatus(hasSdf = true, source = SdfSource.PUBCHEM, message = null)

        assertEquals(R.string.ui_structure_status_pubchem_3d, status.labelRes)
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

        assertEquals(R.string.ui_structure_status_generated_fallback, status.labelRes)
        assertTrue(status.estimated)
        assertEquals(SourceConfidence.MEDIUM, status.confidence)
    }

    @Test
    fun unavailableStructureExplainsIonicAndMetallicLimits() {
        val status = describeStructureStatus(hasSdf = false, source = null, message = null)

        assertEquals(R.string.ui_structure_status_unavailable, status.labelRes)
        assertEquals(R.string.ui_structure_status_unavailable_detail, status.detailRes)
    }
}

package com.furthersecrets.chemsearch.data.local

import com.furthersecrets.chemsearch.data.ChemUiState
import com.furthersecrets.chemsearch.data.DownloadedCompound
import com.furthersecrets.chemsearch.data.GhsData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadedCompoundEntityTest {
    @Test
    fun downloadedCompoundRoundTripPreservesOfflineSnapshot() {
        val downloaded = DownloadedCompound(
            cid = 16132283L,
            name = "Glucagon",
            formula = "C153H225N43O49S",
            molecularWeight = "3482.7",
            iupacName = "glucagon",
            savedAt = 42L,
            state = ChemUiState(
                cid = 16132283L,
                name = "Glucagon",
                formula = "C153H225N43O49S",
                weight = "3482.7",
                synonyms = listOf("Glucagon", "9007-92-5"),
                pubDescription = "A peptide hormone.",
                sdfData = "sdf-data",
                offline2dPngBase64 = "png-data",
                isOfflineDownload = true,
                ghsData = GhsData(
                    signalWord = "Warning",
                    hazardStatements = listOf("H315 Causes skin irritation"),
                    pictogramCodes = listOf("GHS07")
                ),
                hasResult = true
            ),
            structurePngBase64 = "png-data"
        )

        val restored = downloaded.toEntity().toDomain()

        assertEquals(downloaded.cid, restored.cid)
        assertEquals(downloaded.name, restored.name)
        assertEquals(downloaded.formula, restored.formula)
        assertEquals(downloaded.molecularWeight, restored.molecularWeight)
        assertEquals(downloaded.savedAt, restored.savedAt)
        assertEquals(downloaded.structurePngBase64, restored.structurePngBase64)
        assertEquals(downloaded.state.synonyms, restored.state.synonyms)
        assertEquals(downloaded.state.sdfData, restored.state.sdfData)
        assertEquals(downloaded.state.ghsData, restored.state.ghsData)
        assertTrue(restored.state.isOfflineDownload)
        assertTrue(restored.state.hasResult)
    }
}

package com.furthersecrets.chemsearch.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineDownloadMetadataTest {
    @Test
    fun summarizesDownloadedAssetsAndFreshness() {
        val metadata = buildOfflineDownloadMetadata(
            ChemUiState(
                cid = 2244,
                pubDescription = "Aspirin description",
                wikiDescription = "Aspirin wiki",
                synonyms = listOf("aspirin", "acetylsalicylic acid"),
                ghsData = GhsData("Warning", listOf("H302: Harmful if swallowed"), listOf("GHS07")),
                sdfData = "mock sdf",
                sdfSource = SdfSource.PUBCHEM,
                offline2dPngBase64 = "png"
            ),
            savedAt = 1000L,
            now = 1000L + 5L * 24L * 60L * 60L * 1000L
        )

        assertEquals(OFFLINE_DOWNLOAD_SCHEMA_VERSION, metadata.schemaVersion)
        assertTrue(metadata.assetChips.contains("2D saved"))
        assertTrue(metadata.assetChips.contains("3D saved"))
        assertTrue(metadata.assetChips.contains("Safety saved"))
        assertEquals(OfflineFreshness.FRESH, metadata.freshness)
    }
}

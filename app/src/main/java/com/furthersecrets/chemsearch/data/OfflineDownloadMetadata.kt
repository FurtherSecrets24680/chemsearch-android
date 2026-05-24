package com.furthersecrets.chemsearch.data

const val OFFLINE_DOWNLOAD_SCHEMA_VERSION = 2
const val OFFLINE_DOWNLOAD_DATA_VERSION = "2026-05-source-metadata"

enum class OfflineFreshness {
    FRESH,
    STALE
}

data class OfflineDownloadMetadata(
    val schemaVersion: Int = OFFLINE_DOWNLOAD_SCHEMA_VERSION,
    val dataVersion: String = OFFLINE_DOWNLOAD_DATA_VERSION,
    val savedAt: Long,
    val freshness: OfflineFreshness,
    val assetChips: List<String>,
    val sourceSummary: List<String>
)

private const val STALE_AFTER_MS = 30L * 24L * 60L * 60L * 1000L

fun buildOfflineDownloadMetadata(
    state: ChemUiState,
    savedAt: Long = System.currentTimeMillis(),
    now: Long = System.currentTimeMillis()
): OfflineDownloadMetadata {
    val chips = buildList {
        if (!state.offline2dPngBase64.isNullOrBlank()) add("2D saved")
        if (!state.sdfData.isNullOrBlank()) add("3D saved")
        if (state.ghsData != null) add("Safety saved")
        if (state.synonyms.isNotEmpty()) add("Synonyms saved")
        if (!state.pubDescription.isNullOrBlank() || !state.wikiDescription.isNullOrBlank() || !state.aiDescription.isNullOrBlank()) {
            add("Descriptions saved")
        }
        if (state.cid != null || state.iupacName.isNotBlank() || state.smiles.isNotBlank() || state.inchiKey.isNotBlank()) {
            add("Identifiers saved")
        }
    }
    val sources = buildList {
        if (state.cid != null || state.formula.isNotBlank()) add("PubChem properties")
        if (!state.pubDescription.isNullOrBlank()) add("PubChem description")
        if (!state.wikiDescription.isNullOrBlank()) add("Wikipedia")
        if (state.ghsData != null) add("PubChem GHS")
        state.sdfSource?.let { add(describeStructureStatus(state.sdfData != null, it, state.sdfMessage).label) }
    }
    return OfflineDownloadMetadata(
        savedAt = savedAt,
        freshness = if (now - savedAt > STALE_AFTER_MS) OfflineFreshness.STALE else OfflineFreshness.FRESH,
        assetChips = chips,
        sourceSummary = sources
    )
}

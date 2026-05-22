package com.furthersecrets.chemsearch.data.local

import com.furthersecrets.chemsearch.data.ChemUiState
import com.furthersecrets.chemsearch.data.DownloadedCompound
import com.google.gson.Gson

private val downloadedCompoundGson = Gson()

fun DownloadedCompound.toEntity(gson: Gson = downloadedCompoundGson): DownloadedCompoundEntity =
    DownloadedCompoundEntity(
        cid = cid,
        name = name,
        formula = formula,
        molecularWeight = molecularWeight,
        iupacName = iupacName,
        savedAt = savedAt,
        stateJson = gson.toJson(
            state.copy(
                isLoading = false,
                error = null,
                isOfflineDownload = true,
                isLoadingDesc = false,
                isLoadingSdf = false,
                isLoadingSafety = false,
                isLoadingSynonyms = false
            )
        ),
        structurePngBase64 = structurePngBase64
    )

fun DownloadedCompoundEntity.toDomain(gson: Gson = downloadedCompoundGson): DownloadedCompound {
    val state = runCatching { gson.fromJson(stateJson, ChemUiState::class.java) }
        .getOrNull()
        ?: ChemUiState(
            cid = cid,
            name = name,
            formula = formula,
            weight = molecularWeight,
            iupacName = iupacName,
            hasResult = true
        )

    return DownloadedCompound(
        cid = cid,
        name = name,
        formula = formula,
        molecularWeight = molecularWeight,
        iupacName = iupacName,
        savedAt = savedAt,
        state = state.copy(
            cid = state.cid ?: cid,
            name = state.name.ifBlank { name },
            formula = state.formula.ifBlank { formula },
            weight = state.weight.ifBlank { molecularWeight },
            iupacName = state.iupacName.ifBlank { iupacName },
            hasResult = true,
            isOfflineDownload = true,
            isLoading = false,
            error = null,
            isLoadingDesc = false,
            isLoadingSdf = false,
            isLoadingSafety = false,
            isLoadingSynonyms = false
        ),
        structurePngBase64 = structurePngBase64
    )
}

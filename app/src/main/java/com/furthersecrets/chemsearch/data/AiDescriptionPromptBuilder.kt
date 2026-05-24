package com.furthersecrets.chemsearch.data

data class AiDescriptionPrompt(
    val text: String,
    val basis: List<String>,
    val cacheKey: String
)

private const val AI_PROMPT_VERSION = "chemsearch-ai-description-v2"

fun buildAiDescriptionPrompt(
    state: ChemUiState,
    provider: AiProvider,
    model: String
): AiDescriptionPrompt {
    val contextRows = buildList {
        add("Compound name: ${state.name.ifBlank { "Unknown" }}")
        if (state.formula.isNotBlank()) add("Formula: ${state.formula}")
        if (state.weight.isNotBlank()) add("Molecular weight: ${state.weight} g/mol")
        if (state.iupacName.isNotBlank()) add("IUPAC name: ${state.iupacName}")
        state.cid?.let { add("PubChem CID: $it") }
        if (state.smiles.isNotBlank()) add("SMILES: ${state.smiles}")
        if (state.inchiKey.isNotBlank()) add("InChIKey: ${state.inchiKey}")
        state.pubDescription?.takeIf { it.isNotBlank() }?.let { add("PubChem description: $it") }
        if (state.synonyms.isNotEmpty()) add("Synonyms: ${state.synonyms.take(8).joinToString(", ")}")
        state.ghsData?.hazardStatements?.takeIf { it.isNotEmpty() }?.let { hazards ->
            add("Hazards: ${hazards.joinToString("; ") { hazardInfo(it).code ?: it }}")
        }
        state.atomNumber?.let { add("Atom count: $it") }
        state.bondNumber?.let { add("Bond count: $it") }
        state.sdfSource?.let { add("3D structure source: ${describeStructureStatus(state.sdfData != null, it, state.sdfMessage).label}") }
    }

    val basis = buildAiDescriptionBasis(state)
    val text = buildString {
        appendLine("Use only the source context below to write a clear 2-3 sentence chemistry description.")
        appendLine("Include common use or relevance only if the context supports it.")
        appendLine("Avoid unsupported claims, medical advice, synthesis instructions, or safety instructions beyond the listed GHS facts.")
        appendLine("If source context is thin, say that only limited source data is available.")
        appendLine()
        appendLine("Provider: ${provider.shortName}")
        appendLine("Model: $model")
        appendLine("Prompt version: $AI_PROMPT_VERSION")
        appendLine()
        appendLine("Source context:")
        contextRows.forEach { appendLine("- $it") }
    }.trim()

    return AiDescriptionPrompt(
        text = text,
        basis = basis,
        cacheKey = buildAiDescriptionCacheKey(state, provider, model, basis)
    )
}

fun buildAiDescriptionBasis(state: ChemUiState): List<String> =
    buildList {
        if (state.cid != null || state.formula.isNotBlank() || state.weight.isNotBlank() || state.iupacName.isNotBlank()) {
            add("PubChem properties")
        }
        if (!state.pubDescription.isNullOrBlank()) add("PubChem description")
        if (!state.wikiDescription.isNullOrBlank()) add("Wikipedia summary")
        if (state.ghsData != null) add("GHS safety")
        if (state.synonyms.isNotEmpty()) add("Synonyms")
        if (state.smiles.isNotBlank() || state.inchi.isNotBlank() || state.inchiKey.isNotBlank()) {
            add("SMILES/InChI identifiers")
        }
        if (state.sdfSource != null || state.atomNumber != null || state.bondNumber != null) add("Structure metadata")
    }

private fun buildAiDescriptionCacheKey(
    state: ChemUiState,
    provider: AiProvider,
    model: String,
    basis: List<String>
): String {
    val raw = listOf(
        AI_PROMPT_VERSION,
        state.cid?.toString().orEmpty(),
        state.name,
        state.formula,
        provider.name,
        model,
        basis.joinToString("|"),
        state.pubDescription.orEmpty().hashCode().toString(),
        state.ghsData?.hazardStatements.orEmpty().joinToString("|").hashCode().toString()
    ).joinToString("::")
    return raw.hashCode().toUInt().toString(16)
}

package com.furthersecrets.chemsearch.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiDescriptionPromptBuilderTest {
    @Test
    fun promptIsGroundedInAvailableCompoundData() {
        val state = ChemUiState(
            name = "Methanol",
            formula = "CH4O",
            weight = "32.04",
            iupacName = "methanol",
            smiles = "CO",
            inchiKey = "OKKJLVBELUTLKV-UHFFFAOYSA-N",
            pubDescription = "Methanol is the simplest alcohol.",
            ghsData = GhsData("Danger", listOf("H225: Highly flammable liquid and vapour"), listOf("GHS02"))
        )

        val prompt = buildAiDescriptionPrompt(state, provider = AiProvider.GEMINI, model = "gemini-flash-latest")

        assertTrue(prompt.text.contains("Use only the source context below"))
        assertTrue(prompt.text.contains("Formula: CH4O"))
        assertTrue(prompt.text.contains("Hazards: H225"))
        assertTrue(prompt.text.contains("avoid unsupported claims", ignoreCase = true))
        assertEquals(listOf("PubChem properties", "PubChem description", "GHS safety", "SMILES/InChI identifiers"), prompt.basis)
    }
}

package com.furthersecrets.chemsearch.data

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

// PubChem CID

data class CidResponse(
    @SerializedName("IdentifierList") val identifierList: IdentifierList?
)

data class IdentifierList(
    @SerializedName("CID") val cid: List<Long>?
)

// PubChem Properties

data class PropertiesResponse(
    @SerializedName("PropertyTable") val propertyTable: PropertyTable?
)

data class PropertyTable(
    @SerializedName("Properties") val properties: List<CompoundProperty>?
)

data class CompoundProperty(
    @SerializedName("CID") val cid: Long?,
    @SerializedName("MolecularFormula") val molecularFormula: String?,
    @SerializedName("MolecularWeight") val molecularWeight: String?,
    @SerializedName("IUPACName") val iupacName: String?,
    @SerializedName("SMILES") val smiles: String?,
    @SerializedName("ConnectivitySMILES") val connectivitySmiles: String?,
    @SerializedName("InChIKey") val inchiKey: String?,
    @SerializedName("InChI") val inchi: String?,
    @SerializedName("Charge") val charge: Int?
)

// PubChem Synonyms

data class SynonymsResponse(
    @SerializedName("InformationList") val informationList: SynonymInfoList?
)

data class SynonymInfoList(
    @SerializedName("Information") val information: List<SynonymInfo>?
)

data class SynonymInfo(
    @SerializedName("Synonym") val synonym: List<String>?
)

// PubChem Description

data class DescriptionResponse(
    @SerializedName("InformationList") val informationList: DescInfoList?
)

data class DescInfoList(
    @SerializedName("Information") val information: List<DescriptionInfo>?
)

data class DescriptionInfo(
    @SerializedName("Description") val description: JsonElement?,
    @SerializedName("DescriptionSourceName") val sourceName: String?
)

// PubChem Autocomplete

data class AutocompleteResponse(
    @SerializedName("dictionary_terms") val dictionaryTerms: DictionaryTerms?
)

data class DictionaryTerms(
    @SerializedName("compound") val compound: List<String>?
)

// Wikipedia

data class WikiResponse(
    @SerializedName("extract") val extract: String?,
    @SerializedName("title") val title: String?
)

// Gemini

data class GeminiRequest(val contents: List<GeminiContent>)
data class GeminiContent(val parts: List<GeminiPart>)
data class GeminiPart(val text: String)

data class GeminiResponse(
    val candidates: List<GeminiCandidate>?
)

data class GeminiCandidate(
    val content: GeminiContent?
)

// Groq

data class GroqRequest(
    val model: String,
    val messages: List<GroqMessage>,
    val temperature: Float = 0.7f
)

data class GroqMessage(
    val role: String,
    val content: String
)

data class GroqResponse(
    val choices: List<GroqChoice>?
)

data class GroqChoice(
    val message: GroqMessage?
)

// UI State

data class ChemUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val cid: Long? = null,
    val name: String = "",
    val formula: String = "",
    val empiricalFormula: String = "",
    val weight: String = "",
    val charge: Int = 0,
    val iupacName: String = "",
    val smiles: String = "",
    val connectivitySmiles: String = "",
    val inchiKey: String = "",
    val inchi: String = "",
    val synonyms: List<String> = emptyList(),
    val pubDescription: String? = null,
    val wikiDescription: String? = null,
    val aiDescription: String? = null,
    val descSource: DescSource = DescSource.PUBCHEM,
    val aiProvider: AiProvider = AiProvider.GEMINI,
    val suggestions: List<String> = emptyList(),
    val history: List<String> = emptyList(),
    val activeTab: MolTab = MolTab.TWO_D,
    val isLoadingDesc: Boolean = false,
    val casNumber: String? = null,
    val elementalData: List<ElementData> = emptyList(),
    val hasResult: Boolean = false,
    val sdfData: String? = null,
    val isLoadingSdf: Boolean = false,
    val ghsData: GhsData? = null,
    val isLoadingSafety: Boolean = false,
)

enum class DescSource { PUBCHEM, WIKI, AI }
enum class AiProvider { GEMINI, GROQ }
enum class MolTab { TWO_D, THREE_D }

data class ElementData(
    val element: String,
    val percentage: Float
)

// Favorites

data class FavoriteCompound(
    val cid: Long,
    val name: String,
    val formula: String,
    val molecularWeight: String,
    val iupacName: String,
    val savedAt: Long = System.currentTimeMillis()
)

// GHS Safety

data class GhsData(
    val signalWord: String?,
    val hazardStatements: List<String>,
    val pictogramCodes: List<String>
)
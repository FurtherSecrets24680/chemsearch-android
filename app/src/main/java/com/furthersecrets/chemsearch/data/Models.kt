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
    @SerializedName("CID") val cid: Long? = null,
    @SerializedName("MolecularFormula") val molecularFormula: String? = null,
    @SerializedName("MolecularWeight") val molecularWeight: String? = null,
    @SerializedName("IUPACName") val iupacName: String? = null,
    @SerializedName("SMILES") val smiles: String? = null,
    @SerializedName("ConnectivitySMILES") val connectivitySmiles: String? = null,
    @SerializedName("InChIKey") val inchiKey: String? = null,
    @SerializedName("InChI") val inchi: String? = null,
    @SerializedName("Charge") val charge: Int? = null,
    @SerializedName("CovalentUnitCount") val covalentUnitCount: Int? = null,
    @SerializedName("Title") val title: String? = null
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

data class GeminiModelsResponse(
    @SerializedName("models") val models: List<GeminiModelInfo>? = null
)

data class GeminiModelInfo(
    @SerializedName("name") val name: String? = null,
    @SerializedName("supportedGenerationMethods") val supportedGenerationMethods: List<String>? = null
)

// OpenAI-compatible chat

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

data class ChatModelsResponse(
    @SerializedName("data") val data: List<ChatModelInfo>? = null
)

data class ChatModelInfo(
    @SerializedName("id") val id: String? = null
)

// GitHub Releases

data class GitHubRelease(
    @SerializedName("tag_name") val tagName: String?,
    @SerializedName("name") val name: String?,
    @SerializedName("body") val body: String?,
    @SerializedName("html_url") val htmlUrl: String?,
    @SerializedName("assets") val assets: List<GitHubAsset>?
)

data class GitHubAsset(
    @SerializedName("name") val name: String?,
    @SerializedName("content_type") val contentType: String?,
    @SerializedName("browser_download_url") val browserDownloadUrl: String?
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
    val atomNumber: Int? = null,
    val bondNumber: Int? = null,
    val covalentUnitCount: Int? = null,
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
    val isCached: Boolean = false,
    val sdfData: String? = null,
    val isLoadingSdf: Boolean = false,
    val ghsData: GhsData? = null,
    val isLoadingSafety: Boolean = false,
    val isomerMode: Boolean = false,
    val isomerQuery: String = "",
    val isomers: List<IsomerItem> = emptyList(),
    val isLoadingIsomers: Boolean = false,
    val isomerError: String? = null,
    val isLoadingSynonyms: Boolean = false,

)

enum class DescSource { PUBCHEM, WIKI, AI }
enum class AiProvider(
    val displayName: String,
    val shortName: String,
    val modelName: String,
    val defaultModels: List<String>,
    val keyPref: String,
    val helpHost: String,
    val description: String
) {
    GEMINI(
        displayName = "Google Gemini",
        shortName = "Gemini",
        modelName = "gemini-flash-latest",
        defaultModels = listOf("gemini-flash-latest", "gemini-1.5-flash-latest", "gemini-1.5-pro-latest"),
        keyPref = "gemini_key",
        helpHost = "aistudio.google.com",
        description = "Fast general chemistry summaries from Google."
    ),
    GROQ(
        displayName = "Groq Cloud",
        shortName = "Groq",
        modelName = "openai/gpt-oss-120b",
        defaultModels = listOf("openai/gpt-oss-120b", "openai/gpt-oss-20b", "llama-3.3-70b-versatile"),
        keyPref = "groq_key",
        helpHost = "console.groq.com",
        description = "Very fast OpenAI-compatible chat completions."
    ),
    OPENAI(
        displayName = "OpenAI",
        shortName = "OpenAI",
        modelName = "gpt-4o-mini",
        defaultModels = listOf("gpt-4o-mini", "gpt-4o", "gpt-4.1-mini"),
        keyPref = "openai_key",
        helpHost = "platform.openai.com/api-keys",
        description = "Balanced AI descriptions using OpenAI chat completions."
    ),
    OPENROUTER(
        displayName = "OpenRouter",
        shortName = "OpenRouter",
        modelName = "openrouter/auto",
        defaultModels = listOf("openrouter/auto", "openai/gpt-4o-mini", "anthropic/claude-3.5-haiku"),
        keyPref = "openrouter_key",
        helpHost = "openrouter.ai/keys",
        description = "Routes requests to an available model through OpenRouter."
    ),
    MISTRAL(
        displayName = "Mistral AI",
        shortName = "Mistral",
        modelName = "mistral-small-latest",
        defaultModels = listOf("mistral-small-latest", "mistral-medium-latest", "mistral-large-latest"),
        keyPref = "mistral_key",
        helpHost = "console.mistral.ai/api-keys",
        description = "Lightweight summaries from Mistral's chat API."
    )
}
enum class MolTab { TWO_D, THREE_D }
enum class AppColorScheme { BLUE, VIOLET, EMERALD, ROSE, AMBER }

data class AiModelCatalog(
    val models: List<String> = emptyList(),
    val selectedModel: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

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

data class IsomerItem(
    val cid: Long,
    val title: String
)

data class TitleResponse(
    @SerializedName("PropertyTable") val propertyTable: TitlePropertyTable?
)

data class TitlePropertyTable(
    @SerializedName("Properties") val properties: List<TitleProperty>?
)

data class TitleProperty(
    @SerializedName("CID") val cid: Long?,
    @SerializedName("Title") val title: String?
)

data class ReleaseInfo(
    val tagName: String,
    val changelog: String,
    val downloadUrl: String,
    val releaseUrl: String? = null
)

data class UpdateStatus(
    val isChecking: Boolean = false,
    val latestVersion: String? = null,
    val updateAvailable: Boolean = false,
    val downloadUrl: String? = null,
    val releaseUrl: String? = null,
    val changelog: String? = null,
    val lastCheckedAt: Long? = null,
    val error: String? = null
)

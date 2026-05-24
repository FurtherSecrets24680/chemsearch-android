package com.furthersecrets.chemsearch.data

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

enum class NetworkDiagnosticMethod { GET, POST }
enum class NetworkDiagnosticAuth { NONE, QUERY_KEY, BEARER }

data class NetworkDiagnosticProbeSpec(
    val service: String,
    val endpoint: String,
    val method: NetworkDiagnosticMethod = NetworkDiagnosticMethod.GET,
    val body: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val aiProvider: AiProvider? = null,
    val auth: NetworkDiagnosticAuth = NetworkDiagnosticAuth.NONE
) {
    fun requiresApiKey(): Boolean = aiProvider != null

    fun requestUrl(apiKey: String?): String {
        if (auth != NetworkDiagnosticAuth.QUERY_KEY || apiKey.isNullOrBlank()) return endpoint
        val separator = if (endpoint.contains("?")) "&" else "?"
        val encodedKey = URLEncoder.encode(apiKey, StandardCharsets.UTF_8.name())
        return "$endpoint${separator}key=$encodedKey"
    }
}

fun buildNetworkDiagnosticProbeSpecs(): List<NetworkDiagnosticProbeSpec> =
    buildList {
        add(
            NetworkDiagnosticProbeSpec(
                service = "PubChem Search",
                endpoint = "https://pubchem.ncbi.nlm.nih.gov/rest/pug/compound/name/water/cids/JSON"
            )
        )
        add(
            NetworkDiagnosticProbeSpec(
                service = "PubChem Autocomplete",
                endpoint = "https://pubchem.ncbi.nlm.nih.gov/rest/autocomplete/compound/caffeine/JSON?limit=3"
            )
        )
        add(
            NetworkDiagnosticProbeSpec(
                service = "PubChem GHS Safety",
                endpoint = "https://pubchem.ncbi.nlm.nih.gov/rest/pug_view/data/compound/2244/JSON?heading=GHS%20Classification"
            )
        )
        add(
            NetworkDiagnosticProbeSpec(
                service = "PubChem 2D Structure",
                endpoint = "https://pubchem.ncbi.nlm.nih.gov/rest/pug/compound/cid/2244/PNG?image_size=small"
            )
        )
        add(
            NetworkDiagnosticProbeSpec(
                service = "PubChem 3D SDF",
                endpoint = "https://pubchem.ncbi.nlm.nih.gov/rest/pug/compound/cid/2244/SDF?record_type=3d"
            )
        )
        add(
            NetworkDiagnosticProbeSpec(
                service = "NCI/CADD Fallback SDF",
                endpoint = "https://cactus.nci.nih.gov/chemical/structure/water/sdf",
                headers = mapOf("User-Agent" to "ChemSearch Android")
            )
        )
        add(
            NetworkDiagnosticProbeSpec(
                service = "Wikipedia",
                endpoint = "https://en.wikipedia.org/api/rest_v1/page/summary/Water",
                headers = jsonUserAgentHeaders()
            )
        )
        add(
            NetworkDiagnosticProbeSpec(
                service = "GitHub Releases",
                endpoint = "https://api.github.com/repos/FurtherSecrets24680/chemsearch-android/releases/latest",
                headers = jsonUserAgentHeaders("application/vnd.github+json")
            )
        )
        AiProvider.entries.forEach { provider -> add(provider.toAiProbeSpec()) }
    }

fun buildDebugApiEndpointLines(): List<String> =
    listOf(
        "PubChem PUG REST: https://pubchem.ncbi.nlm.nih.gov/rest/pug/",
        "PubChem Autocomplete: https://pubchem.ncbi.nlm.nih.gov/rest/autocomplete/",
        "PubChem PUG View: https://pubchem.ncbi.nlm.nih.gov/rest/pug_view/",
        "PubChem structure images: https://pubchem.ncbi.nlm.nih.gov/rest/pug/compound/cid/{cid}/PNG",
        "NCI/CADD fallback SDF: https://cactus.nci.nih.gov/chemical/structure/",
        "Wikipedia REST: https://en.wikipedia.org/api/rest_v1/",
        "GitHub Releases: https://api.github.com/repos/FurtherSecrets24680/chemsearch-android/releases/latest"
    ) + AiProvider.entries.map { provider ->
        "${provider.shortName}: ${provider.baseEndpoint()}"
    }

private fun AiProvider.toAiProbeSpec(): NetworkDiagnosticProbeSpec =
    when (this) {
        AiProvider.GEMINI -> NetworkDiagnosticProbeSpec(
            service = "$shortName API",
            endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent",
            method = NetworkDiagnosticMethod.POST,
            body = """{"contents":[{"parts":[{"text":"Reply with PONG only."}]}]}""",
            aiProvider = this,
            auth = NetworkDiagnosticAuth.QUERY_KEY
        )
        else -> NetworkDiagnosticProbeSpec(
            service = "$shortName API",
            endpoint = "${baseEndpoint()}chat/completions",
            method = NetworkDiagnosticMethod.POST,
            body = """{"model":"$modelName","messages":[{"role":"user","content":"Reply with PONG only."}],"max_tokens":8,"temperature":0}""",
            aiProvider = this,
            auth = NetworkDiagnosticAuth.BEARER
        )
    }

private fun AiProvider.baseEndpoint(): String =
    when (this) {
        AiProvider.GEMINI -> "https://generativelanguage.googleapis.com/v1beta/"
        AiProvider.GROQ -> "https://api.groq.com/openai/v1/"
        AiProvider.OPENAI -> "https://api.openai.com/v1/"
        AiProvider.OPENROUTER -> "https://openrouter.ai/api/v1/"
        AiProvider.MISTRAL -> "https://api.mistral.ai/v1/"
    }

private fun jsonUserAgentHeaders(
    accept: String = "application/json"
): Map<String, String> =
    mapOf(
        "User-Agent" to "ChemSearch/1.0 (Android; github.com/FurtherSecrets24680)",
        "Accept" to accept
    )

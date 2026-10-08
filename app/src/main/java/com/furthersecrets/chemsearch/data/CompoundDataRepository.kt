package com.furthersecrets.chemsearch.data

import android.content.Context
import android.content.SharedPreferences
import com.furthersecrets.chemsearch.AppLocalization
import com.furthersecrets.chemsearch.R
import com.furthersecrets.chemsearch.ui.DebugLog
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Request
import android.util.Base64

/**
 * Pure data seam for compound lookups: PubChem fetches, synonyms, descriptions,
 * GHS safety parsing, SDF loading and cache snapshot assembly. No Android UI
 * state lives here — callers merge results into their own state holders.
 */
class CompoundDataRepository(
    private val context: Context,
    private val prefs: SharedPreferences
) {
    private fun str(resId: Int): String = AppLocalization.string(context, prefs, resId)

    private fun str(resId: Int, vararg formatArgs: Any): String =
        AppLocalization.string(context, prefs, resId, *formatArgs)

    suspend fun fetchSynonyms(cid: Long): List<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                ApiClient.pubChem.getSynonyms(cid)
                    .informationList
                    ?.information
                    ?.firstOrNull()
                    ?.synonym
                    ?: emptyList()
            }.getOrDefault(emptyList())
        }.distinct()

    suspend fun fetchPubChemDescription(cid: Long): String? = withContext(Dispatchers.IO) {
        runCatching {
            ApiClient.pubChem.getDescription(cid)
                .informationList
                ?.information
                ?.find { it.description != null }
                ?.description
                ?.let { el ->
                    when {
                        el.isJsonPrimitive -> el.asString
                        el.isJsonArray -> el.asJsonArray.mapNotNull {
                            runCatching { it.asString }.getOrNull()
                        }.joinToString("\n\n")
                        else -> null
                    }
                }
        }.getOrNull()
    }

    suspend fun fetchWikiDescription(name: String): String? = withContext(Dispatchers.IO) {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return@withContext null
        val titleCased = cleanName.split(" ")
            .joinToString(" ") { word -> word.lowercase().replaceFirstChar { it.uppercase() } }
        runCatching { ApiClient.wiki.getSummary(titleCased).extract }.getOrNull()
            ?: runCatching { ApiClient.wiki.getSummary(cleanName.lowercase().replaceFirstChar { it.uppercase() }).extract }.getOrNull()
    }

    suspend fun fetchGhsData(cid: Long): GhsData? = withContext(Dispatchers.IO) {
        runCatching {
            parseGhsData(ApiClient.pubChemView.getSection(cid, "GHS Classification"))
        }.getOrNull()
    }

    suspend fun fetchAdvancedProperties(cid: Long): List<AdvancedPropertyRow> = withContext(Dispatchers.IO) {
        runCatching {
            ApiClient.pubChem.getProperties(cid)
                .propertyTable
                ?.properties
                ?.firstOrNull()
                ?.let { buildAdvancedProperties(it, localizedAdvancedPropertyLabels(localizedContext)) }
                .orEmpty()
        }.getOrDefault(emptyList())
    }

    suspend fun fetchPubChemCompoundContext(cid: Long): PubChemCompoundContext = withContext(Dispatchers.IO) {
        coroutineScope {
            val classificationHeadings = listOf(
                "Chemical Classes",
                "Drug Classes",
                "MeSH Pharmacological Classification"
            )
            val useHeadings = listOf("Uses", "Therapeutic Uses")

            val classificationDeferred = classificationHeadings.map { heading ->
                async {
                    runCatching { extractPubChemSectionTexts(ApiClient.pubChemView.getSection(cid, heading)) }
                        .getOrDefault(emptyList())
                }
            }
            val useDeferred = useHeadings.map { heading ->
                async {
                    runCatching { extractPubChemSectionTexts(ApiClient.pubChemView.getSection(cid, heading)) }
                        .getOrDefault(emptyList())
                }
            }

            PubChemCompoundContext(
                classificationTags = buildPubChemClassificationTags(classificationDeferred.awaitAll().flatten()),
                useEntries = buildPubChemUseEntries(useDeferred.awaitAll().flatten())
            )
        }
    }

    suspend fun fetchSdf(cid: Long): String? = withContext(Dispatchers.IO) {
        runCatching { ApiClient.pubChem.getSdf(cid).string() }.getOrNull()
    }

    suspend fun fetchSdfForOffline(state: ChemUiState): OfflineSdfResult? {
        val cid = state.cid ?: return null
        state.sdfData?.let { sdf ->
            return OfflineSdfResult(sdf, state.sdfSource ?: SdfSource.PUBCHEM, state.sdfMessage)
        }

        val pubChemSdf = fetchSdf(cid)

        pubChemSdf?.takeIf(::isUsableSdf)?.let { sdf ->
            return OfflineSdfResult(sdf, SdfSource.PUBCHEM, null)
        }

        val candidates = buildSdfIdentifierCandidates(
            smiles = state.smiles,
            connectivitySmiles = state.connectivitySmiles,
            inchi = state.inchi,
            inchiKey = state.inchiKey
        )
        if (candidates.isEmpty()) return null

        val fallback = runCatching {
            withContext(Dispatchers.IO) {
                fetchGeneratedSdfFromIdentifiers(candidates, expectedFormula = state.formula)
            }
        }.getOrNull()

        return fallback?.let {
            OfflineSdfResult(it.sdf, it.source, str(it.messageRes!!, *it.messageArgs.toTypedArray()))
        }
    }

    suspend fun fetch2dStructurePngBase64(cid: Long): String? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("https://pubchem.ncbi.nlm.nih.gov/rest/pug/compound/cid/$cid/PNG?image_size=large")
                .header("User-Agent", "ChemSearch/1.0 (Android; github.com/FurtherSecrets24680)")
                .build()
            ApiClient.rawHttp.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val bytes = response.body.bytes()
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            }
        }.getOrNull()
    }

    suspend fun fetchFormulaCids(formula: String, maxRecords: Int): List<Long> {
        var status = pubChemCidLookupStatus(ApiClient.pubChem.getIsomerCids(formula, maxRecords))
        repeat(8) { attempt ->
            when (status) {
                is PubChemCidLookupStatus.Ready -> return status.cids
                is PubChemCidLookupStatus.Empty -> return emptyList()
                is PubChemCidLookupStatus.Waiting -> {
                    delay(700L + attempt * 250L)
                    status = pubChemCidLookupStatus(ApiClient.pubChem.getCidsByListKey(status.listKey))
                }
            }
        }

        return when (val finalStatus = status) {
            is PubChemCidLookupStatus.Ready -> finalStatus.cids
            else -> emptyList()
        }
    }

    suspend fun resolveAdvancedSearchCids(filters: AdvancedSearchFilters): List<Long> {
        val query = filters.query
        return when (filters.type) {
            AdvancedSearchType.CID -> query.toLongOrNull()?.takeIf { it > 0 }?.let(::listOf).orEmpty()
            AdvancedSearchType.FORMULA -> fetchFormulaCids(query, filters.maxRecords)
            AdvancedSearchType.CAS -> ApiClient.pubChem.getCid(query)
                .identifierList?.cid?.take(1).orEmpty()
            AdvancedSearchType.NAME -> {
                val names = buildList {
                    add(query)
                    val suggestions = runCatching {
                        ApiClient.pubChemAutocomplete.autocomplete(query, limit = 5)
                            .dictionaryTerms?.compound.orEmpty()
                    }.getOrDefault(emptyList())
                    addAll(suggestions)
                }.distinctBy { it.lowercase(java.util.Locale.US) }

                names.mapNotNull { name ->
                    runCatching {
                        ApiClient.pubChem.getCid(name).identifierList?.cid?.firstOrNull()
                    }.getOrNull()
                }
            }
        }
    }

    suspend fun hasPubChem3d(cid: Long): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val sdf = ApiClient.pubChem.getSdf(cid, recordType = "3d").string()
            sdf.contains("V2000") && sdf.contains("M  END")
        }.getOrDefault(false)
    }

    suspend fun loadStructurePropertiesForCids(cids: List<Long>): Map<Long, CompoundProperty> {
        if (cids.isEmpty()) return emptyMap()
        val cidString = cids.joinToString(",")
        return runCatching {
            ApiClient.pubChem.getStructureResultProperties(cidString)
                .propertyTable?.properties
                ?.mapNotNull { property -> property.cid?.let { it to property } }
                ?.toMap()
        }.getOrNull() ?: emptyMap()
    }

    suspend fun fetchSearchCorrectionSuggestions(query: String): List<String> =
        runCatching {
            val suggestions = ApiClient.pubChemAutocomplete.autocomplete(query, limit = 8)
                .dictionaryTerms
                ?.compound
                .orEmpty()
            cleanSearchCorrectionSuggestions(query, suggestions)
        }.getOrDefault(emptyList())

    fun extractStructureCounts(record: JsonObject?): StructureCounts {
        val compound = runCatching {
            record
                ?.getAsJsonArray("PC_Compounds")
                ?.firstOrNull()
                ?.asJsonObject
        }.getOrNull() ?: return StructureCounts()

        val atomCount = runCatching {
            compound
                .getAsJsonObject("atoms")
                ?.getAsJsonArray("aid")
                ?.size()
        }.getOrNull()

        val bondCount = runCatching {
            compound
                .getAsJsonObject("bonds")
                ?.getAsJsonArray("aid1")
                ?.size()
        }.getOrNull()

        return StructureCounts(atomCount = atomCount, bondCount = bondCount)
    }

    fun parseGhsData(json: JsonObject): GhsData? {
        return try {
            val record = json.getAsJsonObject("Record") ?: return null
            val sections = record.getAsJsonArray("Section") ?: return null

            fun flatten(arr: com.google.gson.JsonArray): List<com.google.gson.JsonObject> {
                val result = mutableListOf<com.google.gson.JsonObject>()
                for (el in arr) {
                    val obj = runCatching { el.asJsonObject }.getOrNull() ?: continue
                    result.add(obj)
                    obj.getAsJsonArray("Section")?.let { result.addAll(flatten(it)) }
                }
                return result
            }

            val allSections = flatten(sections)

            val hazardStatements = mutableListOf<String>()
            var signalWord: String? = null
            val pictogramCodes = mutableListOf<String>()

            for (section in allSections) {
                val heading = section.get("TOCHeading")?.asString ?: continue
                val infoList = section.getAsJsonArray("Information") ?: continue

                for (infoEl in infoList) {
                    val info = runCatching { infoEl.asJsonObject }.getOrNull() ?: continue
                    val name = info.get("Name")?.asString ?: continue
                    val value = info.getAsJsonObject("Value") ?: continue
                    val swm = value.getAsJsonArray("StringWithMarkup") ?: continue

                    when {
                        heading == "GHS Classification" && name.contains("Signal", ignoreCase = true) -> {
                            signalWord = swm.firstOrNull()
                                ?.asJsonObject?.get("String")?.asString
                        }
                        heading == "GHS Classification" && name.contains("Hazard Statement", ignoreCase = true) -> {
                            swm.mapNotNull {
                                runCatching { it.asJsonObject.get("String")?.asString }.getOrNull()
                            }
                                .filter { it.isNotBlank() && !it.equals("Not Classified", ignoreCase = true) && !it.startsWith("Reported as not meeting", ignoreCase = true) }
                                .let { hazardStatements.addAll(it) }
                        }
                        heading == "Pictogram(s)" || name.contains("Pictogram", ignoreCase = true) -> {
                            swm.forEach { markupEl ->
                                val obj = runCatching { markupEl.asJsonObject }.getOrNull() ?: return@forEach
                                obj.getAsJsonArray("Markup")?.forEach { m ->
                                    val mObj = runCatching { m.asJsonObject }.getOrNull() ?: return@forEach
                                    val url = mObj.get("URL")?.asString ?: return@forEach
                                    Regex("GHS\\d{2}").find(url)?.value?.let { pictogramCodes.add(it) }
                                }
                            }
                        }
                    }
                }
            }

            val dedupedHazards = dedupeHazardStatements(hazardStatements)
            if (dedupedHazards.isEmpty() && signalWord == null && pictogramCodes.isEmpty()) return null
            GhsData(signalWord, dedupedHazards, pictogramCodes.distinct(), retrievedAt = System.currentTimeMillis())
        } catch (e: Exception) {
            DebugLog.e("ChemViewModel", "GHS parse error: ${e.message}")
            null
        }
    }

    // ---- Hazard statement normalization (unchanged behavior) ----

    private val hazardCodeRegex = Regex("\\bH\\d{3}(?:[+/](?:H)?\\d{3})*\\b")
    private val hazardPercentRegex = Regex("\\((\\d+(?:\\.\\d+)?)%\\)")
    private val hazardWhitespaceRegex = Regex("\\s+")

    private data class HazardStatementChoice(
        val raw: String,
        val percent: Float?,
        val normalized: String
    )

    private fun normalizeHazardStatement(statement: String): String {
        return statement
            .replace(hazardPercentRegex, "")
            .replace(hazardWhitespaceRegex, " ")
            .trim()
    }

    private fun shouldReplaceHazard(existing: HazardStatementChoice, candidate: HazardStatementChoice): Boolean {
        val existingPercent = existing.percent
        val candidatePercent = candidate.percent

        if (candidatePercent != null && existingPercent == null) return true
        if (candidatePercent == null && existingPercent != null) return false
        if (candidatePercent != null && existingPercent != null) {
            if (candidatePercent > existingPercent) return true
            if (candidatePercent < existingPercent) return false
        }

        val existingScore = existing.normalized.length
        val candidateScore = candidate.normalized.length
        return candidateScore > existingScore
    }

    private fun dedupeHazardStatements(statements: List<String>): List<String> {
        val bestByKey = LinkedHashMap<String, HazardStatementChoice>()
        for (statement in statements) {
            val normalized = normalizeHazardStatement(statement)
            if (normalized.isBlank()) continue
            val key = hazardCodeRegex.find(statement)?.value?.uppercase()
                ?: normalized.lowercase()
            val percent = hazardPercentRegex.find(statement)?.groupValues?.getOrNull(1)?.toFloatOrNull()
            val candidate = HazardStatementChoice(statement, percent, normalized)
            val existing = bestByKey[key]
            if (existing == null || shouldReplaceHazard(existing, candidate)) {
                bestByKey[key] = candidate
            }
        }
        return bestByKey.values.map { it.raw }
    }

    private val localizedContext: Context
        get() = AppLocalization.localizedContext(context, prefs)

    data class StructureCounts(
        val atomCount: Int? = null,
        val bondCount: Int? = null
    )

    data class OfflineSdfResult(
        val sdf: String,
        val source: SdfSource,
        val message: String?
    )
}

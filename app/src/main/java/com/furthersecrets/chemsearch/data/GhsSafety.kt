package com.furthersecrets.chemsearch.data

const val GHS_SAFETY_DISCLAIMER =
    "Reference only. This does not replace an official SDS, local lab rules, or supervisor guidance."

data class HazardStatementInfo(
    val code: String?,
    val statement: String,
    val meaning: String?
)

data class GhsSafetySummary(
    val signalWord: String?,
    val pictogramCodes: List<String>,
    val hazards: List<HazardStatementInfo>,
    val source: SourceMetadata,
    val retrievedAt: Long?,
    val disclaimer: String = GHS_SAFETY_DISCLAIMER
)

private val hazardMeanings = mapOf(
    "H200" to "Unstable explosive",
    "H225" to "Highly flammable liquid and vapour",
    "H226" to "Flammable liquid and vapour",
    "H301" to "Toxic if swallowed",
    "H302" to "Harmful if swallowed",
    "H311" to "Toxic in contact with skin",
    "H314" to "Causes severe skin burns and eye damage",
    "H315" to "Causes skin irritation",
    "H319" to "Causes serious eye irritation",
    "H331" to "Toxic if inhaled",
    "H335" to "May cause respiratory irritation",
    "H350" to "May cause cancer",
    "H400" to "Very toxic to aquatic life"
)

private val hazardCodePattern = Regex("\\bH\\d{3}\\b")

fun enrichGhsSafety(data: GhsData?, retrievedAt: Long?): GhsSafetySummary =
    GhsSafetySummary(
        signalWord = data?.signalWord,
        pictogramCodes = data?.pictogramCodes.orEmpty(),
        hazards = data?.hazardStatements.orEmpty().map(::hazardInfo),
        source = pubChemSourceMetadata(AppDataArea.GHS_SAFETY).copy(name = "PubChem GHS Classification"),
        retrievedAt = retrievedAt
    )

fun hazardInfo(statement: String): HazardStatementInfo {
    val code = hazardCodePattern.find(statement)?.value
    return HazardStatementInfo(
        code = code,
        statement = statement,
        meaning = code?.let { hazardMeanings[it] }
    )
}

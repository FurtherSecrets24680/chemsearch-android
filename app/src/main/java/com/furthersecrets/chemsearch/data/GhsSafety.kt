package com.furthersecrets.chemsearch.data

import androidx.annotation.StringRes
import com.furthersecrets.chemsearch.R

val GHS_SAFETY_DISCLAIMER_RES = R.string.ui_ghs_disclaimer

data class HazardStatementInfo(
    val code: String?,
    val statement: String,
    @StringRes val meaningRes: Int?
)

data class GhsSafetySummary(
    val signalWord: String?,
    val pictogramCodes: List<String>,
    val hazards: List<HazardStatementInfo>,
    val source: SourceMetadata,
    val retrievedAt: Long?,
    @StringRes val disclaimerRes: Int = GHS_SAFETY_DISCLAIMER_RES
)

private val hazardMeanings = mapOf(
    "H200" to R.string.ui_hazard_unstable_explosive,
    "H225" to R.string.ui_hazard_highly_flammable,
    "H226" to R.string.ui_hazard_flammable_liquid,
    "H301" to R.string.ui_hazard_toxic_if_swallowed,
    "H302" to R.string.ui_hazard_harmful_if_swallowed,
    "H311" to R.string.ui_hazard_toxic_skin,
    "H314" to R.string.ui_hazard_severe_burns,
    "H315" to R.string.ui_hazard_skin_irritation,
    "H319" to R.string.ui_hazard_eye_irritation,
    "H331" to R.string.ui_hazard_toxic_if_inhaled,
    "H335" to R.string.ui_hazard_respiratory_irritation,
    "H350" to R.string.ui_hazard_may_cause_cancer,
    "H400" to R.string.ui_hazard_very_toxic_aquatic
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
        meaningRes = code?.let { hazardMeanings[it] }
    )
}

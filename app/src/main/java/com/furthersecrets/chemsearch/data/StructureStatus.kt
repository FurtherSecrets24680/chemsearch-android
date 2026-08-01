package com.furthersecrets.chemsearch.data

import com.furthersecrets.chemsearch.R

data class StructureStatus(
    val labelRes: Int,
    val detailRes: Int,
    val detailMessage: String? = null,
    val promptLabel: String,
    val confidence: SourceConfidence,
    val estimated: Boolean
)

fun describeStructureStatus(
    hasSdf: Boolean,
    source: SdfSource?,
    message: String?
): StructureStatus =
    when {
        hasSdf && source == SdfSource.PUBCHEM -> StructureStatus(
            labelRes = R.string.ui_structure_status_pubchem_3d,
            detailRes = R.string.ui_structure_status_pubchem_3d_detail,
            promptLabel = "PubChem 3D conformer",
            confidence = SourceConfidence.HIGH,
            estimated = false
        )
        hasSdf && source == SdfSource.GENERATED -> StructureStatus(
            labelRes = R.string.ui_structure_status_generated_fallback,
            detailRes = R.string.ui_structure_status_generated_fallback_detail,
            detailMessage = message,
            promptLabel = "Generated fallback",
            confidence = SourceConfidence.MEDIUM,
            estimated = true
        )
        else -> StructureStatus(
            labelRes = R.string.ui_structure_status_unavailable,
            detailRes = R.string.ui_structure_status_unavailable_detail,
            detailMessage = message,
            promptLabel = "Structure unavailable",
            confidence = SourceConfidence.LOW,
            estimated = false
        )
    }

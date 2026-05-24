package com.furthersecrets.chemsearch.data

data class StructureStatus(
    val label: String,
    val detail: String,
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
            label = "PubChem 3D conformer",
            detail = "3D conformer from PubChem. It is a molecular conformer, not a crystal or ionic lattice model.",
            confidence = SourceConfidence.HIGH,
            estimated = false
        )
        hasSdf && source == SdfSource.GENERATED -> StructureStatus(
            label = "Generated fallback",
            detail = message ?: "Generated estimate from SMILES, InChI, or InChIKey. Confirm before using for exact geometry.",
            confidence = SourceConfidence.MEDIUM,
            estimated = true
        )
        else -> StructureStatus(
            label = "Structure unavailable",
            detail = message ?: "No usable 3D model is available. Metallic solids, salts, and ionic lattice structures often do not have a single PubChem-style molecular 3D conformer.",
            confidence = SourceConfidence.LOW,
            estimated = false
        )
    }

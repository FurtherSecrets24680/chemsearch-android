package com.furthersecrets.chemsearch.data

enum class SourceConfidence {
    HIGH,
    MEDIUM_HIGH,
    MEDIUM,
    LOW
}

enum class AppDataArea {
    DEFINITIONS,
    NOMENCLATURE,
    COMPOUND_PROPERTIES,
    DESCRIPTIONS,
    GHS_SAFETY,
    STRUCTURE_2D,
    STRUCTURE_3D,
    STRUCTURE_FALLBACK,
    AI_DESCRIPTION,
    OFFLINE_STORAGE,
    DEVELOPER_DIAGNOSTICS
}

data class SourceMetadata(
    val name: String,
    val url: String,
    val confidence: SourceConfidence,
    val appliesTo: Set<AppDataArea>,
    val licenseOrPolicy: String,
    val note: String
)

fun buildSourceReliabilityMatrix(): List<SourceMetadata> =
    listOf(
        SourceMetadata(
            name = "IUPAC Gold Book",
            url = "https://goldbook.iupac.org/",
            confidence = SourceConfidence.HIGH,
            appliesTo = setOf(AppDataArea.DEFINITIONS, AppDataArea.NOMENCLATURE),
            licenseOrPolicy = "IUPAC terminology reference",
            note = "Use for chemistry terms and definitions."
        ),
        SourceMetadata(
            name = "IUPAC Red Book",
            url = "https://iupac.org/what-we-do/books/redbook/",
            confidence = SourceConfidence.HIGH,
            appliesTo = setOf(AppDataArea.NOMENCLATURE, AppDataArea.DEFINITIONS),
            licenseOrPolicy = "IUPAC inorganic nomenclature reference",
            note = "Use for inorganic formula and coordination notation."
        ),
        SourceMetadata(
            name = "PubChem PUG REST",
            url = "https://pubchem.ncbi.nlm.nih.gov/docs/pug-rest",
            confidence = SourceConfidence.HIGH,
            appliesTo = setOf(
                AppDataArea.COMPOUND_PROPERTIES,
                AppDataArea.DESCRIPTIONS,
                AppDataArea.STRUCTURE_2D,
                AppDataArea.STRUCTURE_3D
            ),
            licenseOrPolicy = "NIH/NLM PubChem public data service",
            note = "Primary source for identifiers, properties, images, synonyms, and SDF."
        ),
        SourceMetadata(
            name = "PubChem PUG View",
            url = "https://pubchem.ncbi.nlm.nih.gov/docs/pug-view",
            confidence = SourceConfidence.MEDIUM_HIGH,
            appliesTo = setOf(AppDataArea.GHS_SAFETY, AppDataArea.DESCRIPTIONS),
            licenseOrPolicy = "NIH/NLM PubChem aggregated source records",
            note = "Use for safety and curated section text. Treat GHS as aggregated reference data."
        ),
        SourceMetadata(
            name = "NCI/CADD Resolver",
            url = "https://cactus.nci.nih.gov/chemical/structure_documentation",
            confidence = SourceConfidence.MEDIUM,
            appliesTo = setOf(AppDataArea.STRUCTURE_FALLBACK),
            licenseOrPolicy = "NCI/CADD resolver service",
            note = "Use only as a generated fallback when PubChem 3D is unavailable."
        ),
        SourceMetadata(
            name = "UNECE GHS",
            url = "https://unece.org/transport/dangerous-goods/ghs-pictograms",
            confidence = SourceConfidence.HIGH,
            appliesTo = setOf(AppDataArea.GHS_SAFETY),
            licenseOrPolicy = "UN GHS reference",
            note = "Use for official GHS pictogram artwork and safety wording policy."
        ),
        SourceMetadata(
            name = "Android Offline First",
            url = "https://developer.android.com/topic/architecture/data-layer/offline-first",
            confidence = SourceConfidence.HIGH,
            appliesTo = setOf(AppDataArea.OFFLINE_STORAGE, AppDataArea.DEVELOPER_DIAGNOSTICS),
            licenseOrPolicy = "Android developer documentation",
            note = "Use for local-first download behavior and refresh semantics."
        )
    )

fun pubChemSourceMetadata(area: AppDataArea): SourceMetadata =
    buildSourceReliabilityMatrix()
        .firstOrNull { source ->
            source.appliesTo.contains(area) &&
                (source.name == "PubChem PUG View" || source.name == "PubChem PUG REST")
        }
        ?: buildSourceReliabilityMatrix().first { it.name == "PubChem PUG REST" }

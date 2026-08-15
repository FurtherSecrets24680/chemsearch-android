package com.furthersecrets.chemsearch.data

enum class AdvancedSearchType(val label: String) {
    NAME("Name"),
    FORMULA("Formula"),
    CID("CID"),
    CAS("CAS")
}

data class AdvancedSearchFilters(
    val query: String = "",
    val type: AdvancedSearchType = AdvancedSearchType.NAME,
    val includeElements: Set<String> = emptySet(),
    val excludeElements: Set<String> = emptySet(),
    val minMolecularWeight: Double? = null,
    val maxMolecularWeight: Double? = null,
    val charge: Int? = null,
    val requireThreeD: Boolean = false,
    val requireGhs: Boolean = false,
    val maxRecords: Int = 20
)

data class AdvancedSearchResultItem(
    val cid: Long,
    val title: String,
    val formula: String,
    val molecularWeight: String,
    val charge: Int? = null,
    val hasThreeD: Boolean? = null,
    val hasGhs: Boolean? = null
)

data class AdvancedSearchUiState(
    val isLoading: Boolean = false,
    val filters: AdvancedSearchFilters = AdvancedSearchFilters(),
    val results: List<AdvancedSearchResultItem> = emptyList(),
    val error: String? = null
)

fun parseElementFilterText(text: String): Set<String> =
    text.split(',', ' ', ';', '\n', '\t')
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .mapNotNull { elementBySymbol(it)?.symbol }
        .toSet()

fun advancedSearchMatchesFilters(
    property: CompoundProperty,
    filters: AdvancedSearchFilters,
    hasThreeD: Boolean? = null,
    hasGhs: Boolean? = null
): Boolean {
    val formula = property.molecularFormula.orEmpty()
    val elementCounts = runCatching { parseFormulaElementCounts(formula) }.getOrDefault(emptyMap())
    if (filters.includeElements.any { it !in elementCounts }) return false
    if (filters.excludeElements.any { it in elementCounts }) return false

    val molecularWeight = property.molecularWeight
        ?.replace(",", "")
        ?.trim()
        ?.toDoubleOrNull()
    if (filters.minMolecularWeight != null && (molecularWeight == null || molecularWeight < filters.minMolecularWeight)) return false
    if (filters.maxMolecularWeight != null && (molecularWeight == null || molecularWeight > filters.maxMolecularWeight)) return false
    if (filters.charge != null && property.charge != filters.charge) return false
    if (filters.requireThreeD && hasThreeD != true) return false
    if (filters.requireGhs && hasGhs != true) return false
    return true
}

fun normalizeAdvancedSearchQuery(query: String): String =
    query.trim().replace(Regex("\\s+"), " ")

fun looksLikeFormulaQuery(query: String): Boolean {
    val clean = normalizeAdvancedSearchQuery(query)
    if (clean.isBlank() || clean.any { it.isWhitespace() }) return false
    return clean.any { it.isDigit() } && clean.any { it.isUpperCase() }
}

fun advancedSearchTypeForQuery(query: String): AdvancedSearchType =
    when {
        normalizeAdvancedSearchQuery(query).toLongOrNull()?.let { it > 0 } == true -> AdvancedSearchType.CID
        Regex("""^\d{1,7}-\d{2}-\d$""").matches(normalizeAdvancedSearchQuery(query)) -> AdvancedSearchType.CAS
        looksLikeFormulaQuery(query) -> AdvancedSearchType.FORMULA
        else -> AdvancedSearchType.NAME
    }

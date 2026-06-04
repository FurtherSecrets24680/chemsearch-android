package com.furthersecrets.chemsearch.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class RecentSearch(
    val query: String,
    val lastSearchedAt: Long = System.currentTimeMillis(),
    val pinned: Boolean = false
)

data class RecentSearchGroup(
    val label: String,
    val searches: List<RecentSearch>
)

fun groupRecentSearches(
    searches: List<RecentSearch>,
    nowMillis: Long = System.currentTimeMillis(),
    zoneId: ZoneId = ZoneId.systemDefault(),
    newestFirst: Boolean = true
): List<RecentSearchGroup> {
    val nowDate = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
    val sorted = if (newestFirst) {
        searches.sortedByDescending { it.lastSearchedAt }
    } else {
        searches.sortedBy { it.lastSearchedAt }
    }
    val pinned = sorted.filter { it.pinned }
    val unpinned = sorted.filterNot { it.pinned }

    fun bucketFor(search: RecentSearch): String {
        val searchedAt = search.lastSearchedAt
        if (searchedAt <= 0L) return "Older"
        val date = Instant.ofEpochMilli(searchedAt).atZone(zoneId).toLocalDate()
        return when {
            date == nowDate -> "Today"
            date == nowDate.minusDays(1) -> "Yesterday"
            date.isWithinPreviousDays(nowDate, days = 7) -> "Previous 7 days"
            date.isWithinPreviousDays(nowDate, days = 30) -> "Previous 30 days"
            else -> "Older"
        }
    }

    val grouped = linkedMapOf<String, MutableList<RecentSearch>>()
    if (pinned.isNotEmpty()) grouped["Pinned"] = pinned.toMutableList()
    unpinned.forEach { search ->
        grouped.getOrPut(bucketFor(search)) { mutableListOf() }.add(search)
    }
    return grouped.map { (label, items) -> RecentSearchGroup(label, items) }
}

fun recentSearchesForDisplay(
    searches: List<RecentSearch>,
    newestFirst: Boolean = true
): List<RecentSearch> {
    val sorted = if (newestFirst) {
        searches.sortedByDescending { it.lastSearchedAt }
    } else {
        searches.sortedBy { it.lastSearchedAt }
    }
    return sorted.filter { it.pinned } + sorted.filterNot { it.pinned }
}

fun recentSearchCountLabel(count: Int): String =
    if (count == 1) "1 saved search" else "$count saved searches"

fun cleanSearchCorrectionSuggestions(
    query: String,
    suggestions: List<String>,
    limit: Int = 4
): List<String> {
    val normalizedQuery = normalizeCorrectionTerm(query)
    if (normalizedQuery.length < 3) return emptyList()

    data class RankedCorrection(
        val text: String,
        val normalized: String,
        val distance: Int,
        val sourceIndex: Int
    )

    return suggestions
        .flatMapIndexed { index, suggestion ->
            correctionCandidates(suggestion).map { candidate -> candidate to index }
        }
        .mapNotNull { (candidate, sourceIndex) ->
            val normalizedCandidate = normalizeCorrectionTerm(candidate)
            if (!isCloseCorrection(normalizedQuery, normalizedCandidate)) return@mapNotNull null
            RankedCorrection(
                text = candidate.trim(),
                normalized = normalizedCandidate,
                distance = levenshteinDistance(normalizedQuery, normalizedCandidate),
                sourceIndex = sourceIndex
            )
        }
        .distinctBy { it.normalized }
        .sortedWith(
            compareBy<RankedCorrection> { it.distance }
                .thenBy { it.normalized.length }
                .thenBy { it.sourceIndex }
        )
        .take(limit)
        .map { it.text }
}

fun parseCompareCompoundInputs(raw: String): List<String> =
    raw
        .replace(Regex("\\bvs\\b", RegexOption.IGNORE_CASE), "\n")
        .replace(";", "\n")
        .replace(",", "\n")
        .lines()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase() }

fun formatCompoundComparisonValue(compoundName: String, value: String): String {
    val cleanName = compoundName.trim().ifBlank { "Compound" }
    val cleanValue = value.trim().ifBlank { "—" }
    return "$cleanName: $cleanValue"
}

fun previewComparisonDescription(text: String, maxChars: Int = 180): String {
    return previewComparisonCellText(text, maxChars)
}

fun previewComparisonCellText(text: String, maxChars: Int = 180): String {
    val clean = text.trim()
    if (clean.length <= maxChars) return clean
    if (maxChars <= 1) return "…"
    return clean.take(maxChars - 1).trimEnd() + "…"
}

private fun LocalDate.isWithinPreviousDays(nowDate: LocalDate, days: Long): Boolean =
    this.isAfter(nowDate.minusDays(days)) && this.isBefore(nowDate.minusDays(1))

private fun correctionCandidates(suggestion: String): List<String> {
    val clean = suggestion.trim().replace(Regex("\\s+"), " ")
    if (clean.isBlank()) return emptyList()
    val rootToken = if (clean.any { it.isDigit() } || clean.any { it == '(' || it == ')' || it == '-' }) {
        Regex("[A-Za-z][A-Za-z0-9]*")
            .findAll(clean)
            .map { it.value }
            .filter { it.length >= 3 }
            .lastOrNull()
    } else {
        null
    }
    return listOfNotNull(clean, rootToken)
        .distinctBy { normalizeCorrectionTerm(it) }
}

private fun normalizeCorrectionTerm(value: String): String =
    value.lowercase().filter { it.isLetterOrDigit() }

private fun isCloseCorrection(query: String, candidate: String): Boolean {
    if (candidate.isBlank() || candidate == query) return false
    if (candidate.firstOrNull() != query.firstOrNull()) return false

    val lengthDelta = kotlin.math.abs(candidate.length - query.length)
    if (lengthDelta > maxOf(4, query.length / 2)) return false

    val distance = levenshteinDistance(query, candidate)
    val allowedDistance = when {
        query.length <= 4 -> 1
        query.length <= 8 -> 2
        else -> maxOf(2, query.length / 4)
    }
    return distance <= allowedDistance
}

private fun levenshteinDistance(a: String, b: String): Int {
    if (a == b) return 0
    if (a.isEmpty()) return b.length
    if (b.isEmpty()) return a.length

    var previous = IntArray(b.length + 1) { it }
    var current = IntArray(b.length + 1)
    for (i in 1..a.length) {
        current[0] = i
        for (j in 1..b.length) {
            val substitutionCost = if (a[i - 1] == b[j - 1]) 0 else 1
            current[j] = minOf(
                current[j - 1] + 1,
                previous[j] + 1,
                previous[j - 1] + substitutionCost
            )
        }
        val swap = previous
        previous = current
        current = swap
    }
    return previous[b.length]
}

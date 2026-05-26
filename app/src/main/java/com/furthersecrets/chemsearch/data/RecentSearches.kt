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
            date.isWithinThisWeek(nowDate) -> "This week"
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

private fun LocalDate.isWithinThisWeek(nowDate: LocalDate): Boolean =
    this.isAfter(nowDate.minusDays(7)) && this.isBefore(nowDate.minusDays(1))

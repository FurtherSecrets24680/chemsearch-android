package com.furthersecrets.chemsearch.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class RecentSearchesTest {
    private val zone = ZoneId.of("Asia/Dhaka")
    private val now = LocalDateTime.of(2026, 5, 22, 10, 0).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun groupsPinnedTodayYesterdayThisWeekAndOlder() {
        val searches = listOf(
            RecentSearch("glucose", timestamp(daysAgo = 0)),
            RecentSearch("ethanol", timestamp(daysAgo = 1)),
            RecentSearch("aspirin", timestamp(daysAgo = 3)),
            RecentSearch("caffeine", timestamp(daysAgo = 20)),
            RecentSearch("methanol", timestamp(daysAgo = 0), pinned = true),
            RecentSearch("legacy", 0L)
        )

        val groups = groupRecentSearches(searches, nowMillis = now, zoneId = zone)

        assertEquals("Pinned", groups[0].label)
        assertEquals(listOf("methanol"), groups[0].searches.map { it.query })
        assertEquals("Today", groups[1].label)
        assertEquals(listOf("glucose"), groups[1].searches.map { it.query })
        assertEquals("Yesterday", groups[2].label)
        assertEquals(listOf("ethanol"), groups[2].searches.map { it.query })
        assertEquals("This week", groups[3].label)
        assertEquals(listOf("aspirin"), groups[3].searches.map { it.query })
        assertEquals("Older", groups[4].label)
        assertEquals(listOf("caffeine", "legacy"), groups[4].searches.map { it.query })
    }

    @Test
    fun parsingCompareInputAcceptsVsCommasAndNewLines() {
        val inputs = parseCompareCompoundInputs("caffeine vs theobromine, ethanol\nmethanol")

        assertEquals(listOf("caffeine", "theobromine", "ethanol", "methanol"), inputs)
    }

    @Test
    fun comparisonValueIncludesCompoundName() {
        val value = formatCompoundComparisonValue("Methanol", "CH4O")

        assertEquals("Methanol: CH4O", value)
    }

    @Test
    fun descriptionPreviewUsesEllipsisBeforeFullText() {
        val text = "A".repeat(220)

        val preview = previewComparisonDescription(text, maxChars = 80)

        assertEquals(80, preview.length)
        assertEquals("…", preview.last().toString())
    }

    @Test
    fun comparisonCellPreviewKeepsShortText() {
        val preview = previewComparisonCellText("Methanol: CH4O", maxChars = 40)

        assertEquals("Methanol: CH4O", preview)
    }

    @Test
    fun comparisonCellPreviewTruncatesLongText() {
        val preview = previewComparisonCellText("Methanol: " + "C".repeat(120), maxChars = 40)

        assertEquals(40, preview.length)
        assertEquals("…", preview.last().toString())
    }

    private fun timestamp(daysAgo: Long): Long =
        LocalDateTime.of(2026, 5, 22, 9, 0)
            .minusDays(daysAgo)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
}

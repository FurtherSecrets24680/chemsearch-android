package com.furthersecrets.chemsearch.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PeriodicTrendsTest {
    @Test
    fun trendPointsSkipMissingValuesAndNormalizeRange() {
        val points = periodicTrendPoints(
            elements = PeriodicTableElements,
            metric = PeriodicTrendMetric.ELECTRONEGATIVITY
        )

        assertTrue(points.none { it.element.symbol == "Ne" })
        assertEquals("F", points.maxBy { it.value }.element.symbol)
        assertEquals(0f, points.minOf { it.normalized }, 0.0001f)
        assertEquals(1f, points.maxOf { it.normalized }, 0.0001f)
    }

    @Test
    fun metricSummariesNameLowAndHighElements() {
        val summary = periodicTrendSummary(
            elements = PeriodicTableElements,
            metric = PeriodicTrendMetric.ATOMIC_RADIUS
        )

        assertTrue(summary.totalElements > 80)
        assertTrue(summary.lowest.isNotBlank())
        assertTrue(summary.highest.isNotBlank())
        assertTrue(summary.lowest.matches(Regex("[A-Z][a-z]? \\(.+ pm\\)")))
        assertTrue(summary.highest.matches(Regex("[A-Z][a-z]? \\(.+ pm\\)")))
        assertTrue(summary.rangeLabel.contains("pm"))
    }
}

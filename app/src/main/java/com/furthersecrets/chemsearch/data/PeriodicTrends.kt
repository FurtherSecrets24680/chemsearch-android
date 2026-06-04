package com.furthersecrets.chemsearch.data

import java.util.Locale

enum class PeriodicTrendMetric(
    val label: String,
    val shortLabel: String,
    val unit: String,
    val description: String
) {
    ELECTRONEGATIVITY(
        label = "Electronegativity",
        shortLabel = "EN",
        unit = "",
        description = "How strongly an atom attracts bonding electrons."
    ),
    ATOMIC_RADIUS(
        label = "Atomic radius",
        shortLabel = "Radius",
        unit = "pm",
        description = "Approximate atomic size in picometers."
    ),
    IONIZATION_ENERGY(
        label = "Ionization energy",
        shortLabel = "IE",
        unit = "eV",
        description = "Energy needed to remove the first electron from a neutral atom."
    ),
    DENSITY(
        label = "Density",
        shortLabel = "Density",
        unit = "g/cm3",
        description = "Mass per unit volume near standard conditions when listed."
    ),
    MELTING_POINT(
        label = "Melting point",
        shortLabel = "Melt",
        unit = "K",
        description = "Temperature where the element changes from solid to liquid."
    ),
    BOILING_POINT(
        label = "Boiling point",
        shortLabel = "Boil",
        unit = "K",
        description = "Temperature where the element changes from liquid to gas."
    )
}

data class PeriodicTrendPoint(
    val element: PeriodicElement,
    val value: Double,
    val normalized: Float
) {
    val valueLabel: String
        get() = formatPeriodicTrendValue(value)
}

data class PeriodicTrendSummary(
    val metric: PeriodicTrendMetric,
    val totalElements: Int,
    val lowest: String,
    val highest: String,
    val rangeLabel: String
)

fun periodicTrendPoints(
    elements: List<PeriodicElement>,
    metric: PeriodicTrendMetric
): List<PeriodicTrendPoint> {
    val values = elements.mapNotNull { element ->
        element.periodicTrendValue(metric)?.let { element to it }
    }
    if (values.isEmpty()) return emptyList()

    val min = values.minOf { it.second }
    val max = values.maxOf { it.second }
    val span = max - min
    return values.map { (element, value) ->
        PeriodicTrendPoint(
            element = element,
            value = value,
            normalized = if (span == 0.0) 1f else ((value - min) / span).toFloat().coerceIn(0f, 1f)
        )
    }
}

fun periodicTrendSummary(
    elements: List<PeriodicElement>,
    metric: PeriodicTrendMetric
): PeriodicTrendSummary {
    val points = periodicTrendPoints(elements, metric)
    if (points.isEmpty()) {
        return PeriodicTrendSummary(
            metric = metric,
            totalElements = 0,
            lowest = "Not listed",
            highest = "Not listed",
            rangeLabel = "No listed values"
        )
    }

    val low = points.minBy { it.value }
    val high = points.maxBy { it.value }
    return PeriodicTrendSummary(
        metric = metric,
        totalElements = points.size,
        lowest = low.summaryLabel(metric),
        highest = high.summaryLabel(metric),
        rangeLabel = "${low.valueLabel} - ${high.valueLabel}${metric.unitLabel()}"
    )
}

fun PeriodicTrendMetric.unitLabel(): String =
    if (unit.isBlank()) "" else " $unit"

private fun PeriodicTrendPoint.summaryLabel(metric: PeriodicTrendMetric): String =
    "${element.symbol} (${valueLabel}${metric.unitLabel()})"

private fun PeriodicElement.periodicTrendValue(metric: PeriodicTrendMetric): Double? =
    when (metric) {
        PeriodicTrendMetric.ELECTRONEGATIVITY -> electronegativity
        PeriodicTrendMetric.ATOMIC_RADIUS -> atomicRadius
        PeriodicTrendMetric.IONIZATION_ENERGY -> ionizationEnergy
        PeriodicTrendMetric.DENSITY -> density
        PeriodicTrendMetric.MELTING_POINT -> meltingPoint
        PeriodicTrendMetric.BOILING_POINT -> boilingPoint
    }.cleanPeriodicNumber()

private fun String.cleanPeriodicNumber(): Double? {
    val normalized = trim()
        .replace(",", "")
        .replace("about", "", ignoreCase = true)
        .replace("approx.", "", ignoreCase = true)
        .replace("predicted", "", ignoreCase = true)
        .replace(Regex("\\(.*?\\)"), "")
        .trim()
    return Regex("-?\\d+(\\.\\d+)?").find(normalized)?.value?.toDoubleOrNull()
}

private fun formatPeriodicTrendValue(value: Double): String =
    when {
        value >= 100 -> String.format(Locale.US, "%.0f", value)
        value >= 10 -> String.format(Locale.US, "%.2f", value).trimTrailingZeros()
        else -> String.format(Locale.US, "%.3f", value).trimTrailingZeros()
    }

private fun String.trimTrailingZeros(): String =
    trimEnd('0').trimEnd('.')

package com.furthersecrets.chemsearch.data

import androidx.annotation.StringRes
import com.furthersecrets.chemsearch.R
import java.util.Locale

enum class PeriodicTrendMetric(
    @StringRes val labelRes: Int,
    @StringRes val shortLabelRes: Int,
    val unit: String,
    @StringRes val descriptionRes: Int
) {
    ELECTRONEGATIVITY(
        labelRes = R.string.ui_trend_electronegativity,
        shortLabelRes = R.string.ui_trend_electronegativity_short,
        unit = "",
        descriptionRes = R.string.ui_trend_electronegativity_desc
    ),
    ATOMIC_RADIUS(
        labelRes = R.string.ui_trend_atomic_radius,
        shortLabelRes = R.string.ui_trend_atomic_radius_short,
        unit = "pm",
        descriptionRes = R.string.ui_trend_atomic_radius_desc
    ),
    IONIZATION_ENERGY(
        labelRes = R.string.ui_trend_ionization_energy,
        shortLabelRes = R.string.ui_trend_ionization_energy_short,
        unit = "eV",
        descriptionRes = R.string.ui_trend_ionization_energy_desc
    ),
    DENSITY(
        labelRes = R.string.ui_trend_density,
        shortLabelRes = R.string.ui_trend_density_short,
        unit = "g/cm3",
        descriptionRes = R.string.ui_trend_density_desc
    ),
    MELTING_POINT(
        labelRes = R.string.ui_trend_melting_point,
        shortLabelRes = R.string.ui_trend_melting_point_short,
        unit = "K",
        descriptionRes = R.string.ui_trend_melting_point_desc
    ),
    BOILING_POINT(
        labelRes = R.string.ui_trend_boiling_point,
        shortLabelRes = R.string.ui_trend_boiling_point_short,
        unit = "K",
        descriptionRes = R.string.ui_trend_boiling_point_desc
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
    val lowest: String?,
    val highest: String?,
    val rangeLabel: String?
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
            lowest = null,
            highest = null,
            rangeLabel = null
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

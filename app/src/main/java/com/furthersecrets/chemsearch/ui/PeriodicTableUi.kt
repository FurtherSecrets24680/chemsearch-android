package com.furthersecrets.chemsearch.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.furthersecrets.chemsearch.R
import com.furthersecrets.chemsearch.data.ApiClient
import com.furthersecrets.chemsearch.data.ElementCategory
import com.furthersecrets.chemsearch.data.PeriodicElement
import com.furthersecrets.chemsearch.data.PeriodicTableElements
import com.furthersecrets.chemsearch.data.PeriodicTrendMetric
import com.furthersecrets.chemsearch.data.PeriodicTrendPoint
import com.furthersecrets.chemsearch.data.periodicTrendPoints
import com.furthersecrets.chemsearch.data.periodicTrendSummary
import com.furthersecrets.chemsearch.data.unitLabel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import java.util.Locale

@Composable
fun PeriodicTableLibraryScreen(
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }
    var selectedElement by remember { mutableStateOf<PeriodicElement?>(null) }
    var fullDetailElement by remember { mutableStateOf<PeriodicElement?>(null) }
    var showTrends by remember { mutableStateOf(false) }
    val normalizedQuery = query.trim().lowercase(Locale.US)
    val matchingElements = remember(normalizedQuery) {
        if (normalizedQuery.isBlank()) {
            PeriodicTableElements
        } else {
            PeriodicTableElements.filter {
                it.symbol.lowercase(Locale.US).contains(normalizedQuery) ||
                    it.pubChemName.lowercase(Locale.US).contains(normalizedQuery) ||
                    it.groupBlock.lowercase(Locale.US).contains(normalizedQuery) ||
                    it.atomicNumber.toString() == normalizedQuery
            }
        }
    }

    fullDetailElement?.let { element ->
        ElementFullDetailPage(
            element = element,
            onBack = { fullDetailElement = null },
            modifier = modifier
        )
        return
    }

    selectedElement?.let { element ->
        ElementDetailDialog(
            element = element,
            onDismiss = { selectedElement = null },
            onOpenDetails = {
                selectedElement = null
                fullDetailElement = element
            }
        )
    }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null)
            },
            placeholder = {
                Text("Search elements", color = MaterialTheme.colorScheme.onSurface.copy(0.34f))
            },
            singleLine = true,
            shape = RoundedCornerShape(18.dp)
        )

        if (normalizedQuery.isBlank()) {
            PeriodicModeToggle(
                showTrends = showTrends,
                onShowTrendsChange = { showTrends = it }
            )
            if (showTrends) {
                PeriodicTrendsPanel(onElementClick = { selectedElement = it })
            } else {
                PeriodicTableGrid(onElementClick = { selectedElement = it })
                PeriodicLegend()
            }
        } else {
            Text(
                "${matchingElements.size} element${if (matchingElements.size == 1) "" else "s"}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
            )
            matchingElements.forEach { element ->
                PeriodicElementListCard(
                    element = element,
                    onClick = { selectedElement = element }
                )
            }
        }
    }
}

@Composable
private fun PeriodicTableGrid(onElementClick: (PeriodicElement) -> Unit) {
    val horizontalScroll = rememberScrollState()
    val tileSize = 56.dp
    val rowLabelWidth = 82.dp
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(horizontalScroll),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(rowLabelWidth))
            (1..18).forEach { group ->
                Box(Modifier.size(tileSize, 22.dp), contentAlignment = Alignment.Center) {
                    Text(
                        group.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.44f),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }
        (1..9).forEach { row ->
            if (row == 8) {
                Spacer(Modifier.height(10.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                PeriodicRowLabel(row = row, modifier = Modifier.width(rowLabelWidth).height(tileSize))
                (1..18).forEach { column ->
                    val element = PeriodicTableElements.firstOrNull {
                        it.tableRow == row && it.tableColumn == column
                    }
                    if (element == null) {
                        Spacer(Modifier.size(tileSize))
                    } else {
                        PeriodicElementTile(
                            element = element,
                            modifier = Modifier.size(tileSize),
                            onClick = { onElementClick(element) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PeriodicModeToggle(
    showTrends: Boolean,
    onShowTrendsChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PeriodicModeButton(
            label = "Table",
            icon = ChemAppIcons.Atom,
            selected = !showTrends,
            onClick = { onShowTrendsChange(false) }
        )
        PeriodicModeButton(
            label = "Trends",
            icon = ChemAppIcons.Trend,
            selected = showTrends,
            onClick = { onShowTrendsChange(true) }
        )
    }
}

@Composable
private fun PeriodicModeButton(
    label: String,
    icon: ChemIconSpec,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(0.16f) else Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ChemIcon(
                icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(0.58f),
                modifier = Modifier.size(16.dp)
            )
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(0.62f),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun PeriodicTrendsPanel(
    onElementClick: (PeriodicElement) -> Unit
) {
    var selectedMetric by remember { mutableStateOf(PeriodicTrendMetric.ELECTRONEGATIVITY) }
    val points = remember(selectedMetric) { periodicTrendPoints(PeriodicTableElements, selectedMetric) }
    val pointsBySymbol = remember(points) { points.associateBy { it.element.symbol } }
    val summary = remember(selectedMetric) { periodicTrendSummary(PeriodicTableElements, selectedMetric) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Compare listed element properties across the whole table. Darker tiles mean higher values for the selected property.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(0.55f)
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PeriodicTrendMetric.entries.forEach { metric ->
                PeriodicTrendMetricChip(
                    metric = metric,
                    selected = metric == selectedMetric,
                    onClick = { selectedMetric = metric }
                )
            }
        }
        PeriodicTrendSummaryCard(summary = summary)
        PeriodicTrendHeatmap(
            metric = selectedMetric,
            pointsBySymbol = pointsBySymbol,
            onElementClick = onElementClick
        )
    }
}

@Composable
private fun PeriodicTrendMetricChip(
    metric: PeriodicTrendMetric,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(0.14f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary.copy(0.42f) else MaterialTheme.colorScheme.outline.copy(0.18f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                metric.shortLabel,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(0.68f),
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun PeriodicTrendSummaryCard(
    summary: com.furthersecrets.chemsearch.data.PeriodicTrendSummary
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(0.16f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ChemIcon(
                    ChemAppIcons.Trend,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(summary.metric.label, fontWeight = FontWeight.Bold)
                    Text(
                        summary.metric.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.55f)
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                PeriodicTrendFact("Lowest", summary.lowest, Modifier.weight(1f))
                PeriodicTrendFact("Highest", summary.highest, Modifier.weight(1f))
            }
            Text(
                "${summary.totalElements} listed values · Range ${summary.rangeLabel}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(0.46f)
            )
        }
    }
}

@Composable
private fun PeriodicTrendFact(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primary.copy(0.08f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(0.13f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Text(
                value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PeriodicTrendHeatmap(
    metric: PeriodicTrendMetric,
    pointsBySymbol: Map<String, PeriodicTrendPoint>,
    onElementClick: (PeriodicElement) -> Unit
) {
    val horizontalScroll = rememberScrollState()
    val tileSize = 58.dp
    val rowLabelWidth = 82.dp
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(horizontalScroll),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(rowLabelWidth))
            (1..18).forEach { group ->
                Box(Modifier.size(tileSize, 22.dp), contentAlignment = Alignment.Center) {
                    Text(
                        group.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.42f),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }
        (1..9).forEach { row ->
            if (row == 8) Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                PeriodicRowLabel(row = row, modifier = Modifier.width(rowLabelWidth).height(tileSize))
                (1..18).forEach { column ->
                    val element = PeriodicTableElements.firstOrNull {
                        it.tableRow == row && it.tableColumn == column
                    }
                    if (element == null) {
                        Spacer(Modifier.size(tileSize))
                    } else {
                        PeriodicTrendTile(
                            element = element,
                            point = pointsBySymbol[element.symbol],
                            metric = metric,
                            modifier = Modifier.size(tileSize),
                            onClick = { onElementClick(element) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PeriodicTrendTile(
    element: PeriodicElement,
    point: PeriodicTrendPoint?,
    metric: PeriodicTrendMetric,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val baseColor = MaterialTheme.colorScheme.primary
    val fillAlpha = point?.let { 0.08f + (it.normalized * 0.34f) } ?: 0.04f
    val borderAlpha = point?.let { 0.18f + (it.normalized * 0.42f) } ?: 0.12f
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = baseColor.copy(alpha = fillAlpha),
        border = BorderStroke(1.dp, baseColor.copy(alpha = borderAlpha))
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(4.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    element.atomicNumber.toString(),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                    color = MaterialTheme.colorScheme.onSurface.copy(0.46f),
                    maxLines = 1
                )
                Text(
                    metric.shortLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp),
                    color = baseColor.copy(alpha = if (point == null) 0.38f else 0.76f),
                    maxLines = 1
                )
            }
            Text(
                element.symbol,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Black,
                color = if (point == null) MaterialTheme.colorScheme.onSurface.copy(0.42f) else baseColor,
                maxLines = 1
            )
            Text(
                point?.let { "${it.valueLabel}${metric.unitLabel()}" } ?: "n/a",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp),
                color = MaterialTheme.colorScheme.onSurface.copy(if (point == null) 0.34f else 0.58f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PeriodicRowLabel(row: Int, modifier: Modifier = Modifier) {
    val label = when (row) {
        in 1..7 -> "Period $row"
        8 -> "Lanthanides"
        9 -> "Actinides"
        else -> ""
    }
    Box(modifier = modifier, contentAlignment = Alignment.CenterEnd) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(0.48f),
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            textAlign = TextAlign.End,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PeriodicElementTile(
    element: PeriodicElement,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val color = periodicCategoryColor(element.category)
    val isLightTheme = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val contentColor = if (isLightTheme) color.darkenForLightMode() else color
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = color.copy(alpha = if (isLightTheme) 0.18f else 0.1f),
        border = BorderStroke(1.25.dp, contentColor.copy(alpha = if (isLightTheme) 0.88f else 0.72f))
    ) {
        Column(
            modifier = Modifier.padding(4.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    element.atomicNumber.toString(),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = MaterialTheme.colorScheme.onSurface.copy(0.46f),
                    maxLines = 1
                )
                Text(
                    element.group.toString(),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                    color = contentColor.copy(alpha = if (isLightTheme) 0.9f else 0.78f),
                    maxLines = 1
                )
            }
            Text(
                element.symbol,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Black,
                color = contentColor,
                maxLines = 1
            )
            Text(
                element.atomicWeightLabel,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                color = MaterialTheme.colorScheme.onSurface.copy(0.45f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PeriodicElementListCard(
    element: PeriodicElement,
    onClick: () -> Unit
) {
    val color = periodicCategoryColor(element.category)
    val isLightTheme = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val contentColor = if (isLightTheme) color.darkenForLightMode() else color
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, contentColor.copy(alpha = if (isLightTheme) 0.42f else 0.26f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(color.copy(alpha = if (isLightTheme) 0.18f else 0.12f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(element.symbol, fontWeight = FontWeight.Black, color = contentColor)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(element.pubChemName, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${element.groupBlock} | Atomic mass ${element.atomicWeightLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.52f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                element.atomicNumber.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ElementDetailDialog(
    element: PeriodicElement,
    onDismiss: () -> Unit,
    onOpenDetails: () -> Unit
) {
    val color = periodicCategoryColor(element.category)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = color.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, color.copy(alpha = 0.5f)),
                        modifier = Modifier.size(58.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                element.symbol,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black,
                                color = color
                            )
                        }
                    }
                    Column {
                        Text(element.pubChemName, fontWeight = FontWeight.Bold)
                        Text(
                            "Atomic number ${element.atomicNumber}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.55f)
                        )
                    }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 260.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ElementFactList(
                    facts = listOf(
                        ElementFactItem("Element type", element.groupBlock),
                        ElementFactItem("Atomic mass", withUnit(element.atomicWeightLabel, "u")),
                        ElementFactItem("Group / period", "Group ${element.group}, period ${element.period}"),
                        ElementFactItem("Standard state", element.standardState),
                        ElementFactItem("Electron configuration", toElectronConfigurationDisplay(element.electronConfiguration)),
                        ElementFactItem("Oxidation states", element.commonOxidationStates)
                    )
                )
            }
        },
        confirmButton = {
            Button(onClick = onOpenDetails, shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Full details")
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
private fun ElementFullDetailPage(
    element: PeriodicElement,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val color = periodicCategoryColor(element.category)
    val fallbackDescription = remember(element.atomicNumber) { element.referenceDescriptionState() }
    var descriptionState by remember(element.atomicNumber) {
        mutableStateOf(fallbackDescription)
    }

    LaunchedEffect(element.atomicNumber) {
        val loadedDescription = loadElementDescription(element)
        descriptionState = if (loadedDescription is ElementDescriptionState.Ready) {
            loadedDescription
        } else if (fallbackDescription is ElementDescriptionState.Ready) {
            fallbackDescription
        } else {
            loadedDescription
        }
    }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(42.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(element.pubChemName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Text(
                    "${element.symbol} · Atomic number ${element.atomicNumber}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.55f)
                )
            }
        }

        ElementHeroCard(element = element, color = color, descriptionState = descriptionState)
        ElementMediaCard(element = element, color = color)
        ElectronShellCard(element = element, color = color)
        ElementPhysicalPropertiesCard(element = element, color = color)
        ElementAllFactsCard(element = element, color = color)
        ElementSpectralLinesCard(element = element, color = color)
        ElementSourceCard(element = element, descriptionState = descriptionState)
    }
}

@Composable
private fun ElementHeroCard(
    element: PeriodicElement,
    color: Color,
    descriptionState: ElementDescriptionState
) {
    val uriHandler = LocalUriHandler.current
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, color.copy(alpha = 0.26f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = color.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, color.copy(alpha = 0.5f)),
                    modifier = Modifier.size(78.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            element.symbol,
                            style = MaterialTheme.typography.headlineMedium,
                            color = color,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(element.pubChemName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    Text(
                        element.groupBlock,
                        style = MaterialTheme.typography.bodyMedium,
                        color = color,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Group ${element.group} · Period ${element.period} · ${element.standardState}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.56f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    CardInfoIcon(info = periodicDetailCardInfo("Element Overview"))
                    if (descriptionState is ElementDescriptionState.Ready) {
                        Surface(
                            onClick = { uriHandler.openUri(descriptionState.url) },
                            modifier = Modifier.size(38.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f))
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Image(
                                    painter = painterResource(R.drawable.icons8_wikipedia_logo),
                                    contentDescription = "Open Wikipedia page",
                                    modifier = Modifier.size(22.dp),
                                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f))
                                )
                            }
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ElementStatChip("Mass", withUnit(element.atomicWeightLabel, "u"), modifier = Modifier.weight(1f))
                ElementStatChip("State", element.standardState, modifier = Modifier.weight(1f))
                ElementStatChip("Found", element.yearDiscovered, modifier = Modifier.weight(1f))
            }

            Text(
                when (descriptionState) {
                    ElementDescriptionState.Loading -> "Loading Wikipedia description..."
                    ElementDescriptionState.Missing -> "No Wikipedia summary is available for this element."
                    is ElementDescriptionState.Error -> "Wikipedia description could not be loaded."
                    is ElementDescriptionState.Ready -> descriptionState.extract
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(0.76f)
            )
        }
    }
}

@Composable
private fun ElementStatChip(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                value.ifMissing("Unknown"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(0.74f),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ElementMediaCard(element: PeriodicElement, color: Color) {
    val extra = element.extraProperties ?: return
    val imageUrl = extra.imageUrl.takeIf { it.isNotBlank() }?.let(::directWikipediaFileImageUrl)
    if (imageUrl == null) return

    DetailCard(title = "Element Images", accent = color) {
        ElementRemoteImage(
            url = imageUrl,
            contentDescription = extra.imageTitle.ifBlank { "${element.pubChemName} element image" },
            heightDp = 190,
            contentScale = ContentScale.Fit
        )
        if (extra.imageTitle.isNotBlank() && !extra.imageTitle.equals("No Image Found", ignoreCase = true)) {
            Text(
                extra.imageTitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(0.7f),
                fontWeight = FontWeight.SemiBold
            )
        }
        if (extra.imageAttribution.isNotBlank()) {
            Text(
                extra.imageAttribution,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(0.48f)
            )
        }
    }
}

@Composable
private fun ElementSpectralLinesCard(element: PeriodicElement, color: Color) {
    val spectralImage = element.spectralImagePageUrl()?.let(::directWikipediaFileImageUrl) ?: return

    DetailCard(title = "Spectral Lines", accent = color) {
        ElementRemoteImage(
            url = spectralImage,
            contentDescription = "${element.pubChemName} spectral lines",
            heightDp = 118,
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
private fun ElementRemoteImage(
    url: String,
    contentDescription: String,
    heightDp: Int,
    contentScale: ContentScale
) {
    val context = LocalContext.current
    var failed by remember(url) { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(heightDp.dp)
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            if (failed) {
                Text(
                    "Image unavailable",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.52f),
                    fontWeight = FontWeight.SemiBold
                )
            }
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(url)
                    .setHeader("User-Agent", "ChemSearch/1.0 (Android; github.com/FurtherSecrets24680)")
                    .crossfade(true)
                    .build(),
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxWidth().height(heightDp.dp),
                contentScale = contentScale,
                onSuccess = { failed = false },
                onError = { failed = true }
            )
        }
    }
}

@Composable
private fun ElectronShellCard(element: PeriodicElement, color: Color) {
    val shells = remember(element.atomicNumber, element.electronConfiguration) {
        electronShellCounts(element)
    }
    var showFullConfiguration by remember(element.atomicNumber) { mutableStateOf(false) }
    DetailCard(title = "Electron Shells", accent = color) {
        ElectronShellDiagram(shells = shells, color = color)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            shells.forEachIndexed { index, count ->
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = color.copy(alpha = 0.1f),
                    border = BorderStroke(1.dp, color.copy(alpha = 0.26f))
                ) {
                    Text(
                        "${shellLabels.getOrElse(index) { "n=${index + 1}" }} $count",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.76f),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
        ElementFactList(
            facts = listOf(
                ElementFactItem(periodicElectronConfigurationLabel, electronConfigurationText(element, showFull = showFullConfiguration))
            )
        )
        Text(
            text = if (showFullConfiguration) "Show short configuration" else "Show full configuration",
            modifier = Modifier
                .padding(top = 2.dp)
                .clickable { showFullConfiguration = !showFullConfiguration },
            style = MaterialTheme.typography.labelSmall.copy(fontSize = periodicFullConfigurationToggleTextSizeSp.sp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = periodicFullConfigurationToggleContentAlpha),
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun ElectronShellDiagram(shells: List<Int>, color: Color) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val surface = MaterialTheme.colorScheme.surface
    Canvas(
        modifier = Modifier.fillMaxWidth().height(250.dp)
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val maxRadius = min(size.width, size.height) / 2f - 18.dp.toPx()
        val nucleusRadius = 18.dp.toPx()
        val orbitRadii = electronShellOrbitRadii(
            shellCount = shells.size,
            maxRadius = maxRadius,
            nucleusRadius = nucleusRadius,
            minimumNucleusGap = 12.dp.toPx()
        )

        drawCircle(color = color.copy(alpha = 0.16f), radius = nucleusRadius, center = center)
        drawCircle(color = color.copy(alpha = 0.6f), radius = nucleusRadius, center = center, style = Stroke(1.5.dp.toPx()))

        shells.forEachIndexed { shellIndex, electrons ->
            val radius = orbitRadii.getOrElse(shellIndex) { maxRadius }
            drawCircle(
                color = onSurface.copy(alpha = 0.14f),
                radius = radius,
                center = center,
                style = Stroke(width = 1.4.dp.toPx())
            )
            val electronRadius = 3.4.dp.toPx()
            repeat(electrons.coerceAtLeast(0)) { electronIndex ->
                val angle = (2.0 * PI * electronIndex / electrons.coerceAtLeast(1)) - (PI / 2.0)
                val electronCenter = Offset(
                    x = center.x + cos(angle).toFloat() * radius,
                    y = center.y + sin(angle).toFloat() * radius
                )
                drawCircle(color = surface, radius = electronRadius + 1.6.dp.toPx(), center = electronCenter)
                drawCircle(color = color, radius = electronRadius, center = electronCenter)
            }
        }
    }
}

@Composable
private fun ElementPhysicalPropertiesCard(element: PeriodicElement, color: Color) {
    val facts = remember(element) { elementPhysicalPropertyFacts(element) }
    if (facts.isEmpty()) return

    DetailCard(title = periodicPhysicalPropertiesCardTitle, accent = color) {
        ElementFactList(facts = facts)
    }
}

@Composable
private fun ElementAllFactsCard(element: PeriodicElement, color: Color) {
    val extra = element.extraProperties
    DetailCard(title = "More Details", accent = color) {
        DetailSubhead(periodicMoreDetailsSectionTitles[0])
        ElementFactList(
            facts = buildList {
                add(ElementFactItem("Name", element.pubChemName))
                add(ElementFactItem("Symbol", element.symbol))
                add(ElementFactItem("Atomic number", element.atomicNumber.toString()))
                add(ElementFactItem("Category", element.category.label))
                add(ElementFactItem("Group / period", "Group ${element.group}, period ${element.period}"))
                add(ElementFactItem("Standard state", element.standardState))
                add(ElementFactItem("Year discovered", element.yearDiscovered))
                extra?.let {
                    add(ElementFactItem("Appearance", it.appearance.ifMissing("Not listed")))
                    add(ElementFactItem("Discovered by", it.discoveredBy.ifMissing("Not listed")))
                    add(ElementFactItem("Named by", it.namedBy.ifMissing("Not listed")))
                    add(ElementFactItem("Orbital block", it.block.ifMissing("Not listed")))
                }
            }
        )

        DetailSubhead(periodicMoreDetailsSectionTitles[1])
        ElementFactList(
            facts = listOf(
                ElementFactItem("Atomic mass", withUnit(element.atomicWeightLabel, "u")),
                ElementFactItem("Model color", element.cpkHexColor.takeIf { !it.isMissingValue() }?.let { "#$it" } ?: element.cpkHexColor)
            )
        )

        DetailSubhead(periodicMoreDetailsSectionTitles[2])
        val electronConfiguration = toElectronConfigurationDisplay(element.electronConfiguration)
        ElementFactList(
            facts = buildList {
                add(ElementFactItem("Electron configuration", electronConfiguration))
                add(ElementFactItem("Shell distribution", electronShellCounts(element).joinToString(" · ")))
                add(ElementFactItem("Oxidation states", element.commonOxidationStates))
                extra?.let {
                    val expandedConfiguration = toElectronConfigurationDisplay(it.electronConfigurationSemantic.removePrefix("*"))
                    if (!expandedConfiguration.isMissingValue() && expandedConfiguration != electronConfiguration) {
                        add(ElementFactItem("Expanded configuration", expandedConfiguration))
                    }
                    add(ElementFactItem("Ionization energies", formatJsonArrayList(it.ionizationEnergiesKjMol, "kJ/mol")))
                }
            }
        )

    }
}

@Composable
private fun ElementSourceCard(element: PeriodicElement, descriptionState: ElementDescriptionState) {
    val extra = element.extraProperties
    val uriHandler = LocalUriHandler.current
    val wikipediaUrl = when (descriptionState) {
        is ElementDescriptionState.Ready -> descriptionState.url
        else -> extra?.source
    }
    val links = buildList {
        add(
            ElementSourceLink(
                title = "PubChem Periodic Table",
                detail = "Element properties",
                url = "https://pubchem.ncbi.nlm.nih.gov/periodic-table/"
            )
        )
        if (extra != null) {
            add(
                ElementSourceLink(
                    title = "Bowserinator/Periodic-Table-JSON",
                    detail = "Extra element data",
                    url = "https://github.com/Bowserinator/Periodic-Table-JSON/"
                )
            )
        }
        wikipediaUrl?.takeIf { it.isNotBlank() }?.let { url ->
            add(
                ElementSourceLink(
                    title = "Wikipedia",
                    detail = "${element.pubChemName} summary",
                    url = url
                )
            )
        }
        extra?.imageUrl?.takeIf { it.isNotBlank() }?.let { url ->
            add(
                ElementSourceLink(
                    title = "Element image",
                    detail = "Image file",
                    url = url
                )
            )
        }
        element.spectralImagePageUrl()?.let { url ->
            add(
                ElementSourceLink(
                    title = "Spectral lines",
                    detail = "Image file",
                    url = url
                )
            )
        }
    }
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Sources", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                CardInfoIcon(info = periodicDetailCardInfo("Sources"))
            }
            links.forEach { link ->
                ElementSourceLinkRow(
                    link = link,
                    onClick = { uriHandler.openUri(link.url) }
                )
            }
        }
    }
}

private data class ElementSourceLink(
    val title: String,
    val detail: String,
    val url: String
)

@Composable
private fun ElementSourceLinkRow(
    link: ElementSourceLink,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    link.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    link.detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.55f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun DetailCard(
    title: String,
    accent: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.22f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black
                )
                CardInfoIcon(info = periodicDetailCardInfo(title))
            }
            content()
        }
    }
}

@Composable
private fun CardInfoIcon(info: PeriodicDetailCardInfo?) {
    if (info == null) return
    var showInfo by remember { mutableStateOf(false) }

    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            title = { Text(info.title) },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.74f),
            tonalElevation = 0.dp,
            text = {
                Text(
                    info.description
                )
            },
            confirmButton = {
                TextButton(onClick = { showInfo = false }) {
                    Text("OK")
                }
            }
        )
    }

    IconButton(
        onClick = { showInfo = true },
        modifier = Modifier.size(periodicInfoButtonSizeDp.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = "About ${info.title}",
            modifier = Modifier.size(periodicInfoIconSizeDp.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f)
        )
    }
}

@Composable
private fun DetailSubhead(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
        fontWeight = FontWeight.Black
    )
}

private fun withUnit(value: String, unit: String): String =
    if (value.isMissingValue()) value else "$value $unit"

internal data class ElementFactItem(
    val label: String,
    val value: String
)

internal data class PeriodicDetailCardInfo(
    val title: String,
    val description: String
)

internal const val periodicPhysicalPropertiesCardTitle = "Physical Properties"

internal const val periodicInfoButtonSizeDp = 28
internal const val periodicInfoIconSizeDp = 17
internal const val periodicElectronConfigurationLabel = "Electronic configuration"
internal const val periodicFullConfigurationToggleTextSizeSp = 12
internal const val periodicFullConfigurationToggleContentAlpha = 0.62f

internal val periodicMoreDetailsSectionTitles = listOf("Identity", "Atomic Data", "Electrons")

private val periodicDetailCardInfos = listOf(
    PeriodicDetailCardInfo(
        "Element Overview",
        "The quick identity panel for this element. It shows the symbol, element family, table position, usual state, atomic mass, discovery year, and a short plain-language summary."
    ),
    PeriodicDetailCardInfo(
        "Element Images",
        "A reference image of the element when one is available. It helps connect the table entry with what the element or a sample of it can look like in real life."
    ),
    PeriodicDetailCardInfo(
        "Electron Shells",
        "A shell view of how electrons are arranged around the atom. The outer shell is especially useful because valence electrons explain many bonding and reactivity patterns."
    ),
    PeriodicDetailCardInfo(
        periodicPhysicalPropertiesCardTitle,
        "Measured properties that describe how the element behaves physically: how strongly it attracts electrons, how large its atoms are, when it melts or boils, and how dense it is."
    ),
    PeriodicDetailCardInfo(
        "More Details",
        "Extra reference facts for the element, grouped by identity, atomic data, and electron data. This keeps detailed values separate from the quick overview."
    ),
    PeriodicDetailCardInfo(
        "Spectral Lines",
        "The visible emission pattern for the element. These bright lines act like a fingerprint, helping identify an element from the light it emits."
    ),
    PeriodicDetailCardInfo(
        "Sources",
        "Open the original data and image sources used for this page. Use these links when you want to verify a value or read more from the source."
    )
).associateBy { it.title }

internal fun periodicDetailCardInfo(title: String): PeriodicDetailCardInfo? =
    periodicDetailCardInfos[title]

internal fun elementPhysicalPropertyFacts(element: PeriodicElement): List<ElementFactItem> = buildList {
    add(ElementFactItem("Electronegativity", element.electronegativity))
    add(ElementFactItem("Atomic radius", withUnit(element.atomicRadius, "pm")))
    add(ElementFactItem("Ionization energy", withUnit(element.ionizationEnergy, "eV")))
    add(ElementFactItem("Electron affinity", withUnit(element.electronAffinity, "eV")))
    add(ElementFactItem("Melting point", withUnit(element.meltingPoint, "K")))
    add(ElementFactItem("Boiling point", withUnit(element.boilingPoint, "K")))
    add(ElementFactItem("Density", withUnit(element.density, "g/cm3")))
    element.extraProperties?.let { extra ->
        add(ElementFactItem("Molar heat", withUnit(extra.molarHeat, "J/(mol·K)")))
    }
}.filterNot { it.value.isMissingValue() }

private sealed interface ElementDescriptionState {
    data object Loading : ElementDescriptionState
    data object Missing : ElementDescriptionState
    data class Ready(val title: String, val extract: String, val url: String) : ElementDescriptionState
    data class Error(val message: String) : ElementDescriptionState
}

private suspend fun loadElementDescription(element: PeriodicElement): ElementDescriptionState =
    withContext(Dispatchers.IO) {
        val titles = listOf("${element.pubChemName} (element)", element.pubChemName).distinct()
        for (title in titles) {
            val response = runCatching { ApiClient.wiki.getSummary(title) }.getOrNull()
            val extract = response?.extract?.trim().orEmpty()
            if (
                extract.isNotBlank() &&
                !extract.contains("may refer to", ignoreCase = true) &&
                !extract.contains("commonly refers to", ignoreCase = true)
            ) {
                return@withContext ElementDescriptionState.Ready(
                    title = response?.title?.takeIf { it.isNotBlank() } ?: title,
                    extract = extract,
                    url = wikipediaPageUrl(response?.title?.takeIf { it.isNotBlank() } ?: title)
                )
            }
        }
        ElementDescriptionState.Missing
    }

private fun PeriodicElement.referenceDescriptionState(): ElementDescriptionState =
    extraProperties?.let { extra ->
        val summary = extra.summary.trim()
        val source = extra.source.trim()
        if (summary.isNotBlank() && source.isNotBlank()) {
            ElementDescriptionState.Ready(
                title = extra.name.ifBlank { pubChemName },
                extract = summary,
                url = source
            )
        } else {
            ElementDescriptionState.Loading
        }
    } ?: ElementDescriptionState.Loading

private fun wikipediaPageUrl(title: String): String {
    val encodedTitle = URLEncoder.encode(title.trim().replace(' ', '_'), "UTF-8")
        .replace("+", "_")
    return "https://en.wikipedia.org/wiki/$encodedTitle"
}

private fun electronShellCounts(element: PeriodicElement): List<Int> {
    parseCsvShells(element.extraProperties?.shells.orEmpty())?.let { return it }
    val expanded = expandElectronConfiguration(element.electronConfiguration)
    val counts = linkedMapOf<Int, Int>()
    Regex("""(\d+)[spdfgh](\d+)""").findAll(expanded).forEach { match ->
        val shell = match.groupValues[1].toIntOrNull() ?: return@forEach
        val electrons = match.groupValues[2].toIntOrNull() ?: return@forEach
        counts[shell] = (counts[shell] ?: 0) + electrons
    }
    val parsed = counts.toSortedMap().values.toList().filter { it > 0 }
    if (parsed.sum() == element.atomicNumber) return parsed
    return fallbackShellCounts(element.atomicNumber)
}

private fun parseCsvShells(shells: String): List<Int>? {
    val parsed = Regex("""\d+""").findAll(shells).mapNotNull { it.value.toIntOrNull() }.toList()
    return parsed.takeIf { it.isNotEmpty() }
}

internal fun electronShellOrbitRadii(
    shellCount: Int,
    maxRadius: Float,
    nucleusRadius: Float,
    minimumNucleusGap: Float
): List<Float> {
    if (shellCount <= 0) return emptyList()
    val outerRadius = maxRadius.coerceAtLeast(0f)
    val firstRadius = (nucleusRadius + minimumNucleusGap).coerceAtMost(outerRadius)
    if (shellCount == 1) return listOf(firstRadius)
    val spacing = (outerRadius - firstRadius) / (shellCount - 1)
    return List(shellCount) { index -> firstRadius + spacing * index }
}

internal fun electronConfigurationText(element: PeriodicElement, showFull: Boolean): String {
    val configuration = if (showFull) {
        fullElectronConfiguration(element)
    } else {
        element.electronConfiguration
    }
    return toElectronConfigurationDisplay(configuration)
}

private fun fullElectronConfiguration(element: PeriodicElement): String {
    val extraConfiguration = element.extraProperties?.electronConfiguration.orEmpty().trim()
    if (!extraConfiguration.isMissingValue() && !extraConfiguration.contains("[")) {
        return extraConfiguration
    }
    return expandElectronConfiguration(element.electronConfiguration)
}

private fun formatJsonArrayList(value: String, unit: String): String {
    val values = Regex("""-?\d+(?:\.\d+)?""").findAll(value).map { it.value }.toList()
    if (values.isEmpty()) return "Not listed"
    return values.joinToString(", ") { "$it $unit" }
}

private fun expandElectronConfiguration(configuration: String): String {
    if (configuration.isMissingValue()) return ""
    val cleaned = configuration
        .replace("(calculated)", "")
        .replace("(predicted)", "")
        .trim()
    return Regex("""\[([A-Z][a-z]?)\]""").replace(cleaned) { match ->
        nobleGasConfigurations[match.groupValues[1]]?.let { "$it " }.orEmpty()
    }
}

private fun fallbackShellCounts(atomicNumber: Int): List<Int> {
    val capacities = listOf(2, 8, 18, 32, 32, 18, 8)
    var remaining = atomicNumber
    val shells = mutableListOf<Int>()
    for (capacity in capacities) {
        if (remaining <= 0) break
        val count = min(remaining, capacity)
        shells.add(count)
        remaining -= count
    }
    return shells
}

private fun String.isMissingValue(): Boolean {
    val clean = trim()
    return clean.isBlank() || clean.equals("Not listed", ignoreCase = true) || clean.equals("Unknown", ignoreCase = true)
}

private fun String.ifMissing(fallback: String): String =
    if (isMissingValue()) fallback else this

private val shellLabels = listOf("K", "L", "M", "N", "O", "P", "Q")

private val nobleGasConfigurations = mapOf(
    "He" to "1s2",
    "Ne" to "1s2 2s2 2p6",
    "Ar" to "1s2 2s2 2p6 3s2 3p6",
    "Kr" to "1s2 2s2 2p6 3s2 3p6 4s2 3d10 4p6",
    "Xe" to "1s2 2s2 2p6 3s2 3p6 4s2 3d10 4p6 5s2 4d10 5p6",
    "Rn" to "1s2 2s2 2p6 3s2 3p6 4s2 3d10 4p6 5s2 4d10 5p6 6s2 4f14 5d10 6p6",
    "Og" to "1s2 2s2 2p6 3s2 3p6 4s2 3d10 4p6 5s2 4d10 5p6 6s2 4f14 5d10 6p6 7s2 5f14 6d10 7p6"
)

private fun toElectronConfigurationDisplay(configuration: String): String {
    if (configuration == "Not listed") return configuration
    return Regex("""([spdfgh])(\d+)""").replace(configuration) { match ->
        match.groupValues[1] + match.groupValues[2].map { electronSuperscriptMap[it] ?: it }.joinToString("")
    }
}

private val electronSuperscriptMap = mapOf(
    '0' to '⁰',
    '1' to '¹',
    '2' to '²',
    '3' to '³',
    '4' to '⁴',
    '5' to '⁵',
    '6' to '⁶',
    '7' to '⁷',
    '8' to '⁸',
    '9' to '⁹'
)

internal fun PeriodicElement.spectralImagePageUrl(): String? {
    extraProperties?.spectralImage?.trim()?.takeIf { it.isNotBlank() }?.let { return it }

    return when (atomicNumber) {
        80 -> "https://commons.wikimedia.org/wiki/File:80_(Hg_I)_NIST_ASD_emission_spectrum.png"
        85 -> null
        87 -> "https://commons.wikimedia.org/wiki/File:Atomic_spectrum_of_francium.svg"
        in 1..99 -> {
            val fileName = "${name.replace(" ", "_")}_spectrum_visible.png"
            "https://commons.wikimedia.org/wiki/File:$fileName"
        }
        else -> null
    }
}

private fun directWikipediaFileImageUrl(url: String): String {
    val marker = "/wiki/File:"
    val markerIndex = url.indexOf(marker)
    if (markerIndex == -1) return url
    val fileName = url
        .substring(markerIndex + marker.length)
        .substringBefore("#")
        .substringBefore("?")
        .trim()
        .replace(" ", "_")
    return if (fileName.isBlank()) {
        url
    } else if (fileName.endsWith(".svg", ignoreCase = true)) {
        "https://commons.wikimedia.org/wiki/Special:FilePath/$fileName?width=1280"
    } else {
        "https://commons.wikimedia.org/wiki/Special:FilePath/$fileName"
    }
}

@Composable
private fun ElementFactRow(label: String, value: String) {
    if (value.isMissingValue()) return
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            label,
            modifier = Modifier.weight(0.9f),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            value,
            modifier = Modifier.weight(1.1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(0.82f),
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun ElementFactList(facts: List<ElementFactItem>) {
    val visibleFacts = facts.filterNot { it.value.isMissingValue() }
    if (visibleFacts.isEmpty()) return

    Column(modifier = Modifier.fillMaxWidth()) {
        visibleFacts.forEachIndexed { index, fact ->
            ElementFactRow(fact.label, fact.value)
            if (index < visibleFacts.lastIndex) {
                PeriodicDetailDivider()
            }
        }
    }
}

@Composable
private fun PeriodicDetailDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 7.dp),
        color = MaterialTheme.colorScheme.outline.copy(alpha = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) 0.18f else 0.24f),
        thickness = 1.dp
    )
}

@Composable
private fun PeriodicLegend() {
    val categories = ElementCategory.entries.filterNot { it == ElementCategory.UNKNOWN }
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        categories.forEach { category ->
            val color = periodicCategoryColor(category)
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = color.copy(alpha = 0.1f),
                border = BorderStroke(1.dp, color.copy(alpha = 0.35f))
            ) {
                Text(
                    category.label,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun periodicCategoryColor(category: ElementCategory): Color = when (category) {
    ElementCategory.ALKALI_METAL -> Color(0xFFE06C75)
    ElementCategory.ALKALINE_EARTH_METAL -> Color(0xFFD19A66)
    ElementCategory.TRANSITION_METAL -> Color(0xFFE5C07B)
    ElementCategory.POST_TRANSITION_METAL -> Color(0xFF98C379)
    ElementCategory.METALLOID -> Color(0xFF56B6C2)
    ElementCategory.REACTIVE_NONMETAL -> Color(0xFF61AFEF)
    ElementCategory.HALOGEN -> Color(0xFFC678DD)
    ElementCategory.NOBLE_GAS -> Color(0xFFABB2BF)
    ElementCategory.LANTHANIDE -> Color(0xFF4DB6AC)
    ElementCategory.ACTINIDE -> Color(0xFFF06292)
    ElementCategory.UNKNOWN -> MaterialTheme.colorScheme.primary
}

private fun Color.darkenForLightMode(): Color =
    Color(
        red = (red * 0.78f).coerceIn(0f, 1f),
        green = (green * 0.68f).coerceIn(0f, 1f),
        blue = (blue * 0.68f).coerceIn(0f, 1f),
        alpha = alpha
    )

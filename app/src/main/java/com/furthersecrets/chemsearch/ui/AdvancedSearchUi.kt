package com.furthersecrets.chemsearch.ui

import com.furthersecrets.chemsearch.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.furthersecrets.chemsearch.data.AdvancedSearchFilters
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.bold.Funnel
import com.furthersecrets.chemsearch.data.AdvancedSearchResultItem
import com.furthersecrets.chemsearch.data.AdvancedSearchType
import com.furthersecrets.chemsearch.data.AdvancedSearchUiState
import com.furthersecrets.chemsearch.data.advancedSearchTypeForQuery
import com.furthersecrets.chemsearch.data.elementBySymbol
import com.furthersecrets.chemsearch.data.parseElementFilterText
import java.util.Locale

@Composable
fun AdvancedSearchDialog(
    state: AdvancedSearchUiState,
    initialQuery: String,
    onUpdateFilters: (AdvancedSearchFilters) -> Unit,
    onSearch: (AdvancedSearchFilters) -> Unit,
    onOpenResult: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember(initialQuery) { mutableStateOf(initialQuery) }
    var selectedType by remember(initialQuery) { mutableStateOf(advancedSearchTypeForQuery(initialQuery)) }
    var includeText by remember { mutableStateOf("") }
    var excludeText by remember { mutableStateOf("") }
    var nameFilterText by remember { mutableStateOf("") }
    var minWeight by remember { mutableStateOf("") }
    var maxWeight by remember { mutableStateOf("") }
    var chargeText by remember { mutableStateOf("") }
    var requireThreeD by remember { mutableStateOf(false) }
    var requireGhs by remember { mutableStateOf(false) }
    var resultsLimit by remember { mutableStateOf(20) }
    var typeExpanded by remember { mutableStateOf(false) }
    var limitExpanded by remember { mutableStateOf(false) }

    fun buildFilters(): AdvancedSearchFilters =
        AdvancedSearchFilters(
            query = query,
            type = selectedType,
            includeElements = parseElementFilterText(includeText),
            excludeElements = parseElementFilterText(excludeText),
            minMolecularWeight = minWeight.toDoubleOrNull(),
            maxMolecularWeight = maxWeight.toDoubleOrNull(),
            charge = chargeText.toIntOrNull(),
            requireThreeD = requireThreeD,
            requireGhs = requireGhs,
            maxRecords = resultsLimit,
            nameFilter = nameFilterText
        )

    LaunchedEffect(query, selectedType, includeText, excludeText, nameFilterText, minWeight, maxWeight, chargeText, requireThreeD, requireGhs, resultsLimit) {
        onUpdateFilters(buildFilters())
    }

    ChemDialog(
        title = stringResource(R.string.ui_advanced_search),
        onDismiss = onDismiss,
        tone = ChemDialogTone.INFO,
        icon = PhosphorIcons.Bold.Funnel,
        actions = {
            TextButton(
                onClick = {
                    includeText = ""
                    excludeText = ""
                    nameFilterText = ""
                    minWeight = ""
                    maxWeight = ""
                    chargeText = ""
                    requireThreeD = false
                    requireGhs = false
                    resultsLimit = 20
                },
                contentPadding = PaddingValues(horizontal = 8.dp),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(stringResource(R.string.ui_clear_all), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = { onSearch(buildFilters()) },
                shape = RoundedCornerShape(12.dp),
                enabled = !state.isLoading
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.ui_searching))
                } else {
                    ChemIcon(
                        ChemAppIcons.Search,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.ui_search), fontWeight = FontWeight.Bold)
                }
            }
        }
    ) {
            Column(
                modifier = Modifier
                    .heightIn(max = 620.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Active-filter summary line
                if (state.filters.activeFilterCount > 0) {
                    Text(
                        filterSummaryText(state.filters),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // ---- Section: what to search -------------------------------
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AdvancedSectionLabel(stringResource(R.string.ui_section_what_to_search))
                    OutlinedTextField(
                        value = query,
                        onValueChange = {
                            query = it
                            selectedType = advancedSearchTypeForQuery(it)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.ui_query)) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = advancedSearchTextFieldColors(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onSearch(buildFilters()) })
                    )
                    Box {
                        Surface(
                            onClick = { typeExpanded = true },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primary.copy(0.1f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(0.22f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    advancedSearchTypeLabel(selectedType),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        SettingsDropdownMenu(
                            expanded = typeExpanded,
                            onDismissRequest = { typeExpanded = false }
                        ) {
                            AdvancedSearchType.entries.forEach { type ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            advancedSearchTypeLabel(type),
                                            fontWeight = if (type == selectedType) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    trailingIcon = if (type == selectedType) {
                                        {
                                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(15.dp))
                                        }
                                    } else null,
                                    onClick = {
                                        selectedType = type
                                        typeExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.14f))

                // ---- Section: narrow by content -----------------------------
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AdvancedSectionLabel(stringResource(R.string.ui_section_narrow_by))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = includeText,
                            onValueChange = { includeText = it },
                            modifier = Modifier.weight(1f),
                            label = { Text(stringResource(R.string.ui_include)) },
                            placeholder = { AdvancedSearchPlaceholder("C, O, Fe") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = advancedSearchTextFieldColors()
                        )
                        OutlinedTextField(
                            value = excludeText,
                            onValueChange = { excludeText = it },
                            modifier = Modifier.weight(1f),
                            label = { Text(stringResource(R.string.ui_exclude)) },
                            placeholder = { AdvancedSearchPlaceholder("Cl, Br") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = advancedSearchTextFieldColors()
                        )
                    }
                    OutlinedTextField(
                        value = nameFilterText,
                        onValueChange = { nameFilterText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.ui_name_contains)) },
                        placeholder = { AdvancedSearchPlaceholder(stringResource(R.string.ui_name_contains_hint)) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = advancedSearchTextFieldColors()
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.14f))

                // ---- Section: properties ------------------------------------
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AdvancedSectionLabel(stringResource(R.string.ui_section_properties))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = minWeight,
                            onValueChange = { minWeight = it.filter { ch -> ch.isDigit() || ch == '.' } },
                            modifier = Modifier.weight(1f),
                            label = { AdvancedSearchFieldLabel(stringResource(R.string.ui_min_weight)) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = advancedSearchTextFieldColors()
                        )
                        OutlinedTextField(
                            value = maxWeight,
                            onValueChange = { maxWeight = it.filter { ch -> ch.isDigit() || ch == '.' } },
                            modifier = Modifier.weight(1f),
                            label = { AdvancedSearchFieldLabel(stringResource(R.string.ui_max_weight)) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = advancedSearchTextFieldColors()
                        )
                        OutlinedTextField(
                            value = chargeText,
                            onValueChange = { chargeText = it.filter { ch -> ch.isDigit() || ch == '-' || ch == '+' }.take(3) },
                            modifier = Modifier.width(104.dp),
                            label = { AdvancedSearchFieldLabel(stringResource(R.string.ui_charge)) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = advancedSearchTextFieldColors()
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AdvancedToggleChip(stringResource(R.string.ui_has_3d), requireThreeD) { requireThreeD = !requireThreeD }
                        AdvancedToggleChip(stringResource(R.string.ui_has_ghs), requireGhs) { requireGhs = !requireGhs }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.14f))

                // ---- Section: how many results ------------------------------
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AdvancedSectionLabel(stringResource(R.string.ui_section_results))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(10, 20, 50).forEach { limit ->
                            AdvancedToggleChip(
                                label = stringResource(R.string.ui_results_limit_n, limit),
                                checked = resultsLimit == limit
                            ) {
                                resultsLimit = limit
                            }
                        }
                        Spacer(Modifier.weight(1f))
                    }
                }

                state.error?.let { error ->
                    Text(
                        error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (state.results.isNotEmpty()) {
                    Text(
                        stringResource(R.string.ui_results_count, state.results.size, if (state.results.size == 1) "" else "s"),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.results.forEach { item ->
                            AdvancedSearchResultCard(item = item, onOpen = { onOpenResult(item.cid) })
                        }
                    }
                }
            }
        }
    }

@Composable
private fun filterSummaryText(filters: AdvancedSearchFilters): String = buildList {
    if (filters.includeElements.isNotEmpty()) add(stringResource(R.string.ui_filter_includes_s, filters.includeElements.sortedBy { elementBySymbol(it)?.atomicNumber ?: Int.MAX_VALUE }.joinToString(", ")))
    if (filters.excludeElements.isNotEmpty()) add(stringResource(R.string.ui_filter_excludes_s, filters.excludeElements.sortedBy { elementBySymbol(it)?.atomicNumber ?: Int.MAX_VALUE }.joinToString(", ")))
    filters.minMolecularWeight?.let { add(stringResource(R.string.ui_filter_mw_ge_s, it.cleanNumber())) }
    filters.maxMolecularWeight?.let { add(stringResource(R.string.ui_filter_mw_le_s, it.cleanNumber())) }
    filters.charge?.let { add(stringResource(R.string.ui_filter_charge_s, if (it > 0) "+$it" else it.toString())) }
    if (filters.nameFilter.isNotBlank()) add(stringResource(R.string.ui_filter_name_contains_s, filters.nameFilter.trim()))
    if (filters.requireThreeD) add(stringResource(R.string.ui_filter_has_3d))
    if (filters.requireGhs) add(stringResource(R.string.ui_filter_has_ghs))
}.joinToString(" | ")

@Composable
private fun AdvancedSectionLabel(text: String) {
    Text(
        text.uppercase(Locale.US),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp
    )
}

@Composable
private fun AdvancedSearchPlaceholder(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurface.copy(0.28f), maxLines = 1)
}

@Composable
private fun AdvancedSearchFieldLabel(text: String) {
    Text(
        text,
        maxLines = 1,
        overflow = TextOverflow.Clip,
        style = MaterialTheme.typography.labelMedium
    )
}

@Composable
private fun advancedSearchTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(0.6f),
    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(0.38f),
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurface.copy(0.6f),
    cursorColor = MaterialTheme.colorScheme.primary,
    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.12f),
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.08f)
)

@Composable
private fun AdvancedToggleChip(label: String, checked: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = checked,
        onClick = onClick,
        label = { Text(label, maxLines = 1) },
        colors = chemFilterChipColors(),
        border = BorderStroke(
            1.dp,
            if (checked) MaterialTheme.colorScheme.primary.copy(0.55f)
            else MaterialTheme.colorScheme.outline.copy(0.35f)
        ),
        shape = RoundedCornerShape(10.dp),
        leadingIcon = if (checked) {{
            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
        }} else null
    )
}

@Composable
private fun AdvancedSearchResultCard(
    item: AdvancedSearchResultItem,
    onOpen: () -> Unit
) {
    ChemCardSurface(onClick = onOpen) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White,
                modifier = Modifier.size(54.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(0.18f))
            ) {
                AsyncImage(
                    model = "https://pubchem.ncbi.nlm.nih.gov/rest/pug/compound/cid/${item.cid}/PNG?record_type=2d&image_size=small",
                    contentDescription = stringResource(R.string.ui_structure_of, item.title),
                    modifier = Modifier.padding(4.dp)
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (item.formula.isNotBlank()) {
                    Text(
                        toSubscriptFormula(item.formula),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    listOfNotNull(
                        stringResource(R.string.ui_cid_label, item.cid.toString()),
                        item.molecularWeight.takeIf { it.isNotBlank() }?.let { stringResource(R.string.ui_mw, it) },
                        item.charge?.takeIf { it != 0 }?.let { stringResource(R.string.ui_charge_value, if (it > 0) "+$it" else it.toString()) },
                        item.hasThreeD?.takeIf { it }?.let { "3D" },
                        item.hasGhs?.takeIf { it }?.let { "GHS" }
                    ).joinToString(" | "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.52f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.iupacName.isNotBlank() && !item.iupacName.equals(item.title, ignoreCase = true)) {
                    Text(
                        item.iupacName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.42f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(0.35f))
        }
    }
}

@Composable
private fun advancedSearchTypeLabel(type: AdvancedSearchType): String = when (type) {
    AdvancedSearchType.NAME -> stringResource(R.string.ui_name)
    AdvancedSearchType.FORMULA -> stringResource(R.string.ui_formula)
    AdvancedSearchType.CID -> "CID"
    AdvancedSearchType.CAS -> "CAS"
}

private fun Double.cleanNumber(): String =
    if (this % 1.0 == 0.0) toInt().toString() else String.format(Locale.US, "%.2f", this)

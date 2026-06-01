package com.furthersecrets.chemsearch.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.furthersecrets.chemsearch.data.AdvancedSearchFilters
import com.furthersecrets.chemsearch.data.AdvancedSearchResultItem
import com.furthersecrets.chemsearch.data.AdvancedSearchType
import com.furthersecrets.chemsearch.data.AdvancedSearchUiState
import com.furthersecrets.chemsearch.data.advancedSearchTypeForQuery
import com.furthersecrets.chemsearch.data.filterSummary
import com.furthersecrets.chemsearch.data.parseElementFilterText

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
    var minWeight by remember { mutableStateOf("") }
    var maxWeight by remember { mutableStateOf("") }
    var chargeText by remember { mutableStateOf("") }
    var requireThreeD by remember { mutableStateOf(false) }
    var requireGhs by remember { mutableStateOf(false) }
    var typeExpanded by remember { mutableStateOf(false) }

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
            requireGhs = requireGhs
        )

    LaunchedEffect(query, selectedType, includeText, excludeText, minWeight, maxWeight, chargeText, requireThreeD, requireGhs) {
        onUpdateFilters(buildFilters())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Advanced search", fontWeight = FontWeight.Bold)
                    Text(
                        filterSummary(buildFilters()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.5f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = {
                            query = it
                            selectedType = advancedSearchTypeForQuery(it)
                        },
                        modifier = Modifier.weight(1f),
                        label = { Text("Query") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = advancedSearchTextFieldColors()
                    )
                    Box {
                        Surface(
                            onClick = { typeExpanded = true },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primary.copy(0.1f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(0.22f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    selectedType.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        }
                        SettingsDropdownMenu(
                            expanded = typeExpanded,
                            onDismissRequest = { typeExpanded = false }
                        ) {
                            AdvancedSearchType.entries.forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type.label) },
                                    onClick = {
                                        selectedType = type
                                        typeExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = includeText,
                        onValueChange = { includeText = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("Include") },
                        placeholder = { AdvancedSearchPlaceholder("C, O, Fe") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = advancedSearchTextFieldColors()
                    )
                    OutlinedTextField(
                        value = excludeText,
                        onValueChange = { excludeText = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("Exclude") },
                        placeholder = { AdvancedSearchPlaceholder("Cl, Br") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = advancedSearchTextFieldColors()
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = minWeight,
                        onValueChange = { minWeight = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        modifier = Modifier.weight(1f),
                        label = { AdvancedSearchFieldLabel("Min weight") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = advancedSearchTextFieldColors()
                    )
                    OutlinedTextField(
                        value = maxWeight,
                        onValueChange = { maxWeight = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        modifier = Modifier.weight(1f),
                        label = { AdvancedSearchFieldLabel("Max weight") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = advancedSearchTextFieldColors()
                    )
                    OutlinedTextField(
                        value = chargeText,
                        onValueChange = { chargeText = it.filter { ch -> ch.isDigit() || ch == '-' || ch == '+' }.take(3) },
                        modifier = Modifier.width(104.dp),
                        label = { AdvancedSearchFieldLabel("Charge") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = advancedSearchTextFieldColors()
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AdvancedToggleChip("Has 3D", requireThreeD) { requireThreeD = !requireThreeD }
                    AdvancedToggleChip("Has GHS", requireGhs) { requireGhs = !requireGhs }
                }

                Button(
                    onClick = { onSearch(buildFilters()) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    enabled = !state.isLoading
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Searching...")
                    } else {
                        ChemIcon(
                            ChemAppIcons.Search,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Search", fontWeight = FontWeight.Bold)
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
                        "${state.results.size} result${if (state.results.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.results.take(8).forEach { item ->
                            AdvancedSearchResultCard(item = item, onOpen = { onOpenResult(item.cid) })
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {},
        containerColor = MaterialTheme.colorScheme.surface
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
    Card(
        onClick = onOpen,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.42f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(0.14f))
    ) {
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
                    contentDescription = "Structure of ${item.title}",
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
                        "CID ${item.cid}",
                        item.molecularWeight.takeIf { it.isNotBlank() }?.let { "MW $it" },
                        item.charge?.takeIf { it != 0 }?.let { "charge ${if (it > 0) "+$it" else it}" },
                        item.hasThreeD?.takeIf { it }?.let { "3D" },
                        item.hasGhs?.takeIf { it }?.let { "GHS" }
                    ).joinToString(" | "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.52f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(0.35f))
        }
    }
}

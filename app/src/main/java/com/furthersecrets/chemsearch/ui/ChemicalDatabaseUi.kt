package com.furthersecrets.chemsearch.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.furthersecrets.chemsearch.data.ChemicalDatabase
import com.furthersecrets.chemsearch.data.ChemicalDbActionTarget
import com.furthersecrets.chemsearch.data.ChemicalDbCategory
import com.furthersecrets.chemsearch.data.ChemicalDbEntry
import com.furthersecrets.chemsearch.data.ChemicalDbRow

@Composable
fun ChemicalDatabaseTool(
    modifier: Modifier = Modifier,
    onSearchCompound: (String) -> Unit = {}
) {
    val compact = LocalCompactMode.current
    val context = LocalContext.current
    val clipboard = remember { context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }
    val focusManager = LocalFocusManager.current
    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<ChemicalDbCategory?>(null) }
    var selectedType by remember { mutableStateOf<String?>(null) }
    var showCategoryResults by remember { mutableStateOf(false) }
    var selectedEntry by remember { mutableStateOf<ChemicalDbEntry?>(null) }

    val entries = remember(context) { ChemicalDatabase.load(context) }
    val entriesByCategory = remember(entries) { entries.groupBy { it.category } }
    val categoryEntries = remember(selectedCategory, entries, entriesByCategory) {
        selectedCategory?.let { entriesByCategory[it].orEmpty() } ?: entries
    }
    val typeGroups = remember(categoryEntries) {
        categoryEntries.filter { it.type.isNotBlank() }.groupBy { it.type }
    }
    val typeOptions = remember(typeGroups) { typeGroups.keys.toList().sortedByTypePriority() }
    val filteredEntries = remember(query, selectedCategory, selectedType, categoryEntries, typeGroups) {
        val normalizedQuery = query.normalizedSearch()
        val baseEntries = selectedType?.let { typeGroups[it].orEmpty() } ?: categoryEntries
        if (normalizedQuery.isBlank()) {
            baseEntries
        } else {
            baseEntries.filter { entry ->
                entry.searchText.contains(normalizedQuery)
            }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = if (compact) 8.dp else 12.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)
    ) {
        if (selectedEntry == null) {
            item(key = "database-header") {
                ChemicalDatabaseHeader(total = entries.size)
            }
            item(key = "database-search") {
                ChemicalDatabaseSearchBar(
                    query = query,
                    onQueryChange = { query = it },
                    onSearch = { focusManager.clearFocus() },
                    onClear = { query = "" }
                )
            }

            when {
                selectedCategory == null && query.isBlank() -> {
                    item(key = "database-categories") {
                        ChemicalDatabaseCategoryCards(
                            entriesByCategory = entriesByCategory,
                            onSelect = { category ->
                                selectedCategory = category
                                selectedType = null
                                showCategoryResults = false
                            }
                        )
                    }
                }
                selectedCategory != null && query.isBlank() && !showCategoryResults -> {
                    item(key = "database-browser-header") {
                        DatabaseBrowserHeader(
                            title = selectedCategory!!.label,
                            subtitle = "${categoryEntries.size} entries",
                            onBack = {
                                selectedCategory = null
                                selectedType = null
                                showCategoryResults = false
                            }
                        )
                    }
                    item(key = "database-types") {
                        ChemicalDatabaseTypeCards(
                            category = selectedCategory!!,
                            entries = categoryEntries,
                            typeGroups = typeGroups,
                            typeOptions = typeOptions,
                            onSelectAll = {
                                selectedType = null
                                showCategoryResults = true
                            },
                            onSelectType = { type ->
                                selectedType = type
                                showCategoryResults = true
                            }
                        )
                    }
                }
                else -> {
                    item(key = "database-results-header") {
                        DatabaseBrowserHeader(
                            title = when {
                                query.isNotBlank() && selectedCategory != null -> "Search ${selectedCategory!!.label}"
                                query.isNotBlank() -> "Search Results"
                                selectedCategory != null -> selectedType ?: "All ${selectedCategory!!.label}"
                                else -> "Search Results"
                            },
                            subtitle = "${filteredEntries.size} matching entries",
                            onBack = {
                                if (selectedCategory != null && query.isBlank()) {
                                    selectedType = null
                                    showCategoryResults = false
                                } else {
                                    query = ""
                                    selectedCategory = null
                                    selectedType = null
                                    showCategoryResults = false
                                }
                            }
                        )
                    }
                    if (filteredEntries.isEmpty()) {
                        item(key = "database-empty") {
                            EmptyDatabaseResults(query = query)
                        }
                    } else {
                        items(
                            items = filteredEntries,
                            key = { entry -> "${entry.category.name}:${entry.id}" },
                            contentType = { entry -> entry.category }
                        ) { entry ->
                            DatabaseResultCard(
                                entry = entry,
                                onClick = { selectedEntry = entry },
                                onCopy = { value, label -> copyToClipboard(context, clipboard, label, value) }
                            )
                        }
                    }
                }
            }
        } else {
            item(key = "entry-back") {
                TextButton(
                    onClick = { selectedEntry = null },
                    contentPadding = PaddingValues(horizontal = 0.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Back to Database")
                }
            }
            item(key = "entry-detail-${selectedEntry!!.category.name}:${selectedEntry!!.id}") {
                DatabaseEntryDetail(
                    entry = selectedEntry!!,
                    onSearchCompound = onSearchCompound,
                    onCopy = { value, label -> copyToClipboard(context, clipboard, label, value) }
                )
            }
        }
    }
}

@Composable
private fun ChemicalDatabaseHeader(total: Int) {
    val compact = LocalCompactMode.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(if (compact) 14.dp else 18.dp),
        color = MaterialTheme.colorScheme.primary.copy(0.08f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(0.18f))
    ) {
        Row(
            modifier = Modifier.padding(if (compact) 12.dp else 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(if (compact) 42.dp else 50.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(0.12f), RoundedCornerShape(if (compact) 11.dp else 14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    "Chemical Database",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.sp
                )
                Text(
                    "$total offline entries for quick reference. Use SDS and lab rules for real safety decisions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.6f),
                    lineHeight = 17.sp
                )
            }
        }
    }
}

@Composable
private fun ChemicalDatabaseCategoryCards(
    entriesByCategory: Map<ChemicalDbCategory, List<ChemicalDbEntry>>,
    onSelect: (ChemicalDbCategory) -> Unit
) {
    val order = listOf(
        ChemicalDbCategory.SUBSTANCES,
        ChemicalDbCategory.IONS,
        ChemicalDbCategory.FUNCTIONAL_GROUPS,
        ChemicalDbCategory.REACTIONS
    )
    Column(verticalArrangement = Arrangement.spacedBy(if (LocalCompactMode.current) 8.dp else 10.dp)) {
        order.forEach { category ->
            val categoryEntries = entriesByCategory[category].orEmpty()
            val typePreview = categoryEntries
                .map { it.type }
                .filter { it.isNotBlank() }
                .distinct()
                .sortedByTypePriority()
                .take(5)
                .joinToString(" / ")
            DatabaseSelectorCard(
                icon = category.icon(),
                title = category.label,
                subtitle = if (typePreview.isBlank()) "${categoryEntries.size} entries" else typePreview,
                meta = "${categoryEntries.size}",
                onClick = { onSelect(category) }
            )
        }
    }
}

@Composable
private fun ChemicalDatabaseTypeCards(
    category: ChemicalDbCategory,
    entries: List<ChemicalDbEntry>,
    typeGroups: Map<String, List<ChemicalDbEntry>>,
    typeOptions: List<String>,
    onSelectAll: () -> Unit,
    onSelectType: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(if (LocalCompactMode.current) 8.dp else 10.dp)) {
        DatabaseSelectorCard(
            icon = category.icon(),
            title = "All ${category.label}",
            subtitle = "Show every entry in this section",
            meta = "${entries.size}",
            onClick = onSelectAll
        )
        typeOptions.forEach { type ->
            val typeEntries = typeGroups[type].orEmpty()
            val examples = typeEntries
                .take(3)
                .joinToString(" / ") { it.title }
            DatabaseSelectorCard(
                icon = category.icon(),
                title = type,
                subtitle = if (examples.isBlank()) "${typeEntries.size} entries" else examples,
                meta = "${typeEntries.size}",
                onClick = { onSelectType(type) }
            )
        }
    }
}

@Composable
private fun DatabaseBrowserHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(34.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", modifier = Modifier.size(18.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(0.52f))
        }
    }
}

@Composable
private fun DatabaseSelectorCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    meta: String,
    onClick: () -> Unit
) {
    val compact = LocalCompactMode.current
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(if (compact) 14.dp else 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(if (compact) 12.dp else 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(if (compact) 44.dp else 52.dp)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(0.1f),
                        RoundedCornerShape(if (compact) 10.dp else 12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(if (compact) 22.dp else 26.dp))
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 3.dp)
            ) {
                Text(
                    title,
                    style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = if (compact) 1 else 2,
                    overflow = if (compact) TextOverflow.Ellipsis else TextOverflow.Clip
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.primary.copy(0.12f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(0.22f))
            ) {
                Text(
                    meta,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                null,
                tint = MaterialTheme.colorScheme.onSurface.copy(0.3f),
                modifier = Modifier.size(if (compact) 18.dp else 22.dp)
            )
        }
    }
}

@Composable
private fun ChemicalDatabaseSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit
) {
    val compact = LocalCompactMode.current
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (compact) 54.dp else 62.dp),
        placeholder = {
            Text(
                "Search substances, reactions, groups, ions...",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface.copy(0.38f)
            )
        },
        leadingIcon = {
            Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurface.copy(0.42f), modifier = Modifier.size(18.dp))
        },
        trailingIcon = {
            if (query.isNotBlank()) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = MaterialTheme.colorScheme.onSurface.copy(0.42f), modifier = Modifier.size(17.dp))
                }
            }
        },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        shape = RoundedCornerShape(if (compact) 16.dp else 20.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(0.42f),
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface
        )
    )
}

@Composable
private fun DatabaseResultCard(
    entry: ChemicalDbEntry,
    onClick: () -> Unit,
    onCopy: (String, String) -> Unit
) {
    val compact = LocalCompactMode.current
    val displayFormula = remember(entry.formula) { toSubscriptFormula(entry.formula) }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(if (compact) 14.dp else 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(if (compact) 12.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 9.dp)
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(if (compact) 9.dp else 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(if (compact) 38.dp else 44.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(0.1f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(entry.category.icon(), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(if (compact) 20.dp else 23.dp))
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        entry.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (entry.formula.isNotBlank()) {
                        Text(
                            displayFormula,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(
                    onClick = { onCopy(entry.actionValue, entry.copyLabel()) },
                    modifier = Modifier.size(if (compact) 28.dp else 32.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = MaterialTheme.colorScheme.onSurface.copy(0.38f), modifier = Modifier.size(16.dp))
                }
            }
            Text(
                entry.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(0.62f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            TagRow(tags = listOf(entry.category.label) + entry.tags.take(4))
        }
    }
}

@Composable
private fun DatabaseEntryDetail(
    entry: ChemicalDbEntry,
    onSearchCompound: (String) -> Unit,
    onCopy: (String, String) -> Unit
) {
    val compact = LocalCompactMode.current
    val uriHandler = LocalUriHandler.current
    val displayFormula = remember(entry.formula) { toSubscriptFormula(entry.formula) }

    Column(verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(if (compact) 16.dp else 20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(0.16f))
        ) {
            Column(
                modifier = Modifier.padding(if (compact) 12.dp else 16.dp),
                verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp)
            ) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier
                            .size(if (compact) 42.dp else 50.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(0.1f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(entry.category.icon(), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(if (compact) 22.dp else 26.dp))
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(entry.category.label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Text(entry.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, letterSpacing = 0.sp)
                        if (entry.formula.isNotBlank()) {
                            Text(
                                displayFormula,
                                style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Text(entry.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(0.72f), lineHeight = 20.sp)
                TagRow(tags = listOf(entry.category.label) + entry.tags)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            if (entry.actionTarget == ChemicalDbActionTarget.SEARCH_COMPOUND) {
                                onSearchCompound(entry.searchQuery)
                            } else {
                                onCopy(entry.actionValue, entry.copyLabel())
                            }
                        },
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(entry.primaryActionIcon(), null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(entry.primaryActionLabel(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(
                        onClick = { onCopy(entry.actionValue, entry.copyLabel()) },
                        shape = RoundedCornerShape(999.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Copy")
                    }
                }
            }
        }

        entry.sections.forEach { section ->
            DetailSectionCard(section.title, section.rows, onCopy)
        }

        if (entry.sourceLabel.isNotBlank()) {
            if (entry.sourceUrl.isNotBlank()) {
                Surface(
                    onClick = { runCatching { uriHandler.openUri(entry.sourceUrl) } },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(0.48f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(0.16f))
                ) {
                    SourceRow(entry.sourceLabel, showChevron = true)
                }
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(0.48f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(0.16f))
                ) {
                    SourceRow(entry.sourceLabel, showChevron = false)
                }
            }
        }
    }
}

@Composable
private fun SourceRow(sourceLabel: String, showChevron: Boolean) {
    val compact = LocalCompactMode.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(if (compact) 10.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.AutoMirrored.Filled.OpenInNew, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Source", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(0.45f), fontWeight = FontWeight.Bold)
            Text(sourceLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(0.72f))
        }
        if (showChevron) {
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurface.copy(0.3f))
        }
    }
}

@Composable
private fun DetailSectionCard(
    title: String,
    rows: List<ChemicalDbRow>,
    onCopy: (String, String) -> Unit
) {
    val compact = LocalCompactMode.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(if (compact) 14.dp else 16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(0.16f))
    ) {
        Column(
            modifier = Modifier.padding(if (compact) 12.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp)
        ) {
            Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(0.45f), letterSpacing = 0.8.sp)
            rows.forEachIndexed { index, row ->
                val label = row.label
                val value = row.value
                val isFormulaValue = label.isFormulaLike() || value.looksFormulaLike()
                val displayValue = remember(value, isFormulaValue) {
                    if (isFormulaValue) toSubscriptFormula(value) else value
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onCopy(value, label) },
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        Text(
                            displayValue,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = if (isFormulaValue) FontFamily.Monospace else FontFamily.Default
                            ),
                            color = MaterialTheme.colorScheme.onSurface.copy(0.78f),
                            lineHeight = 18.sp
                        )
                    }
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy $label", tint = MaterialTheme.colorScheme.onSurface.copy(0.25f), modifier = Modifier.size(15.dp))
                }
                if (index < rows.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.12f))
                }
            }
        }
    }
}

@Composable
private fun TagRow(tags: List<String>) {
    val visibleTags = remember(tags) { tags.distinct().filter { it.isNotBlank() } }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        visibleTags.forEach { tag ->
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.primary.copy(0.08f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(0.14f))
            ) {
                Text(
                    tag,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun EmptyDatabaseResults(query: String) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 34.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.SearchOff, null, tint = MaterialTheme.colorScheme.onSurface.copy(0.25f), modifier = Modifier.size(30.dp))
            Text(
                if (query.isBlank()) "No entries in this category" else "No database entries match \"$query\"",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(0.45f)
            )
        }
    }
}

private fun copyToClipboard(
    context: Context,
    clipboard: ClipboardManager,
    label: String,
    value: String
) {
    clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
    Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
}

private fun ChemicalDbEntry.copyLabel(): String = when (actionTarget) {
    ChemicalDbActionTarget.SEARCH_COMPOUND -> if (formula.isBlank()) title else "Formula"
    ChemicalDbActionTarget.COPY_EQUATION -> "Equation"
    ChemicalDbActionTarget.COPY_TEXT -> category.label
}

private fun ChemicalDbEntry.primaryActionLabel(): String = when (actionTarget) {
    ChemicalDbActionTarget.SEARCH_COMPOUND -> "Search in ChemSearch"
    ChemicalDbActionTarget.COPY_EQUATION -> "Copy equation"
    ChemicalDbActionTarget.COPY_TEXT -> "Copy reference"
}

private fun ChemicalDbEntry.primaryActionIcon(): ImageVector = when (actionTarget) {
    ChemicalDbActionTarget.SEARCH_COMPOUND -> Icons.Default.Search
    ChemicalDbActionTarget.COPY_EQUATION -> Icons.Default.ContentCopy
    ChemicalDbActionTarget.COPY_TEXT -> Icons.Default.ContentCopy
}

private fun ChemicalDbCategory.icon(): ImageVector = when (this) {
    ChemicalDbCategory.SUBSTANCES -> Icons.Default.Science
    ChemicalDbCategory.REACTIONS -> Icons.Default.SwapHoriz
    ChemicalDbCategory.FUNCTIONAL_GROUPS -> Icons.Default.AccountTree
    ChemicalDbCategory.IONS -> Icons.Default.BubbleChart
}

private fun String.normalizedSearch(): String =
    lowercase()
        .replace("₀", "0")
        .replace("₁", "1")
        .replace("₂", "2")
        .replace("₃", "3")
        .replace("₄", "4")
        .replace("₅", "5")
        .replace("₆", "6")
        .replace("₇", "7")
        .replace("₈", "8")
        .replace("₉", "9")

private fun String.isFormulaLike(): Boolean =
    contains("formula", ignoreCase = true) ||
        contains("equation", ignoreCase = true) ||
        contains("structure", ignoreCase = true) ||
        contains("charge", ignoreCase = true)

private fun String.looksFormulaLike(): Boolean {
    if (length > 90) return false
    val hasFormulaSignal = any { it.isDigit() } || contains("->") || contains("=") || contains("#") || contains("+") || contains("-")
    val hasElementSignal = Regex("""\b[A-Z][a-z]?\b""").containsMatchIn(this) || contains("R-") || contains("Ar-")
    return hasFormulaSignal && hasElementSignal
}

private fun List<String>.sortedByTypePriority(): List<String> {
    val priority = listOf(
        "Acid", "Acid ester", "Base", "Alkane", "Alkene", "Alkyne", "Cycloalkane", "Alcohol",
        "Phenol", "Aldehyde", "Ketone", "Carboxylic acid", "Ester", "Amide", "Amine",
        "Amino acid", "Alkaloid", "Nucleobase", "Aromatic", "Haloalkane", "Haloalkene",
        "Carbohydrate", "Surfactant", "Salt", "Hydrate", "Oxide", "Peroxide",
        "Molecular compound", "Organic compound", "Indicator", "Ore", "Element", "Elemental molecule",
        "Alloy", "Petroleum fraction", "Cleaner", "Mixture", "Polymer",
        "Hydrocarbon substituent", "Monoatomic cation", "Monoatomic anion", "Polyatomic cation", "Polyatomic anion"
    )
    return sortedWith(
        compareBy<String> { type ->
            priority.indexOf(type).takeIf { it >= 0 } ?: Int.MAX_VALUE
        }.thenBy { it.lowercase() }
    )
}

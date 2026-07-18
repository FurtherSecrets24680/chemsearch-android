package com.furthersecrets.chemsearch.ui

import com.furthersecrets.chemsearch.R
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import coil.compose.AsyncImage
import com.furthersecrets.chemsearch.data.ChemUiState
import com.furthersecrets.chemsearch.data.IsomerItem

internal const val InitialIsomerResultLimit = 20
private const val IsomerResultChunkSize = 20

internal fun nextIsomerResultLimit(currentLimit: Int): Int =
    if (currentLimit < InitialIsomerResultLimit) {
        InitialIsomerResultLimit
    } else {
        currentLimit + IsomerResultChunkSize
    }

internal val subscriptMap = mapOf(
    '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄',
    '5' to '₅', '6' to '₆', '7' to '₇', '8' to '₈', '9' to '₉'
)

private fun shouldSubscriptDigit(text: String, index: Int): Boolean {
    if (index !in text.indices) return false
    if (!text[index].isDigit()) return false
    if (index == 0) return false
    val prev = text[index - 1]
    return when {
        prev.isLetter() || prev == ')' || prev == ']' -> true
        prev.isDigit() -> shouldSubscriptDigit(text, index - 1)
        else -> false
    }
}

val FormulaSubscriptTransformation = VisualTransformation { text ->
    val raw = text.text
    val transformed = raw.mapIndexed { index, ch ->
        if (ch.isDigit() && shouldSubscriptDigit(raw, index)) subscriptMap[ch] ?: ch else ch
    }.joinToString("")
    TransformedText(
        AnnotatedString(transformed, text.spanStyles, text.paragraphStyles),
        OffsetMapping.Identity
    )
}

fun String.toFormulaSubscript(): String = mapIndexed { index, ch ->
    if (ch.isDigit() && shouldSubscriptDigit(this, index)) subscriptMap[ch] ?: ch else ch
}.joinToString("")

internal fun visibleIsomers(
    isomers: List<IsomerItem>,
    includeIsotopes: Boolean,
    maxResults: Int = 20
): List<IsomerItem> =
    isomers
        .filter { includeIsotopes || !it.isIsotope }
        .take(maxResults)

internal fun hiddenIsotopeCount(isomers: List<IsomerItem>, includeIsotopes: Boolean): Int =
    if (includeIsotopes) 0 else isomers.count { it.isIsotope }

internal fun visibleIsomersForState(state: ChemUiState, includeIsotopes: Boolean): List<IsomerItem> =
    visibleIsomers(
        isomers = state.isomers,
        includeIsotopes = includeIsotopes,
        maxResults = state.isomerResultLimit
    )

internal fun shouldShowIsomerCompareAction(selectedCount: Int): Boolean = selectedCount >= 2

internal fun isomerCompareQueries(isomers: List<IsomerItem>, selectedCids: List<Long>): List<String> {
    val isomerIds = isomers.map { it.cid }.toSet()
    return selectedCids
        .distinct()
        .filter { it in isomerIds }
        .map { it.toString() }
}

@Composable
fun IsomerSearchScreen(
    state: ChemUiState,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onLoadMore: () -> Unit,
    onClear: () -> Unit,
    onOpenResult: (Long) -> Unit,
    onCompareSelected: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues()
) {
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    var showInfo by remember { mutableStateOf(false) }
    var includeIsotopes by remember { mutableStateOf(false) }
    var selectedCids by remember { mutableStateOf<List<Long>>(emptyList()) }
    val visibleIsomers = remember(state.isomers, state.isomerResultLimit, includeIsotopes) {
        visibleIsomersForState(state, includeIsotopes = includeIsotopes)
    }
    val visibleCidSet = remember(visibleIsomers) { visibleIsomers.map { it.cid }.toSet() }
    val selectedVisibleCids = remember(selectedCids, visibleCidSet) {
        selectedCids.filter { it in visibleCidSet }
    }
    val compareQueries = remember(state.isomers, selectedVisibleCids) {
        isomerCompareQueries(state.isomers, selectedVisibleCids)
    }
    val showCompareAction = shouldShowIsomerCompareAction(compareQueries.size)
    val hiddenIsotopes = hiddenIsotopeCount(state.isomers, includeIsotopes)
    BackHandler(onBack = onBack)

    LaunchedEffect(visibleCidSet) {
        selectedCids = selectedCids.filter { it in visibleCidSet }
    }
    LaunchedEffect(state.isomerQuery) {
        selectedCids = emptyList()
    }

    if (showInfo) {
        InfoDialog(
            title = "Isomer Search",
            entries = listOf(
                "What it does" to "Searches PubChem for compounds with the same molecular formula.",
                "How to use it" to "Enter a formula such as C6H6, C2H6O, or C6H12O6, then tap search.",
                "Results" to "Tap any result to open that compound on the main Search page.",
                "Limitations" to "Only compounds indexed by PubChem can appear here."
            ),
            onDismiss = { showInfo = false }
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                IsomerSearchHeader(
                    onBack = onBack,
                    onInfo = { showInfo = true }
                )
            }
            item {
                Text(stringResource(R.string.ui_enter_a_molecular_formula_to_find_matching_structural),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.56f)
                )
            }
            item {
                IsomerSearchBar(
                    query = state.isomerQuery,
                    onQueryChange = onQueryChange,
                    onSearch = {
                        focusManager.clearFocus()
                        onSearch()
                    },
                    onClear = onClear
                )
            }
            if (state.isLoadingIsomers) {
                item { IsomerLoadingState() }
            }
            state.isomerError?.let { error ->
                item { IsomerErrorState(error) }
            }
            if (state.isomers.isNotEmpty()) {
                item {
                    IsomerResultsHeader(
                        formula = state.isomerQuery.trim(),
                        count = visibleIsomers.size
                    )
                }
                if (state.isomers.any { it.isIsotope }) {
                    item {
                        IsotopeFilterRow(
                            includeIsotopes = includeIsotopes,
                            hiddenCount = hiddenIsotopes,
                            onIncludeIsotopesChange = { includeIsotopes = it }
                        )
                    }
                }
                items(visibleIsomers.size) { index ->
                    val isomer = visibleIsomers[index]
                    val selected = isomer.cid in selectedVisibleCids
                    IsomerCard(
                        isomer = isomer,
                        selected = selected,
                        selectionMode = selectedVisibleCids.isNotEmpty(),
                        onClick = {
                            focusManager.clearFocus()
                            if (selectedVisibleCids.isNotEmpty()) {
                                selectedCids = toggleIsomerSelection(selectedCids, isomer.cid)
                            } else {
                                onOpenResult(isomer.cid)
                            }
                        },
                        onToggleSelected = {
                            selectedCids = toggleIsomerSelection(selectedCids, isomer.cid)
                        }
                    )
                }
                if (showCompareAction) {
                    item { Spacer(Modifier.height(76.dp)) }
                }
                if (state.isomerCanLoadMore || state.isLoadingMoreIsomers) {
                    item {
                        IsomerShowMoreRow(
                            isLoading = state.isLoadingMoreIsomers,
                            onLoadMore = onLoadMore
                        )
                    }
                }
            } else if (!state.isLoadingIsomers && state.isomerError == null) {
                item {
                    IsomerEmptyState()
                }
            }
        }

        if (showCompareAction) {
            ExtendedFloatingActionButton(
                onClick = { onCompareSelected(compareQueries) },
                icon = {
                    Icon(
                        Icons.AutoMirrored.Filled.CompareArrows,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                text = { Text("Compare ${compareQueries.size}") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 18.dp)
            )
        }
    }
}

@Composable
private fun IsomerShowMoreRow(
    isLoading: Boolean,
    onLoadMore: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        TextButton(
            onClick = onLoadMore,
            enabled = !isLoading,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(15.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.ui_loading_more))
            } else {
                Text(stringResource(R.string.ui_show_20_more),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

private fun toggleIsomerSelection(selectedCids: List<Long>, cid: Long): List<Long> =
    if (cid in selectedCids) selectedCids - cid else selectedCids + cid

@Composable
private fun IsotopeFilterRow(
    includeIsotopes: Boolean,
    hiddenCount: Int,
    onIncludeIsotopesChange: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(0.14f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.ui_include_isotopes),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    if (includeIsotopes) "Showing isotope-substituted compounds too."
                    else "$hiddenCount isotope result${if (hiddenCount == 1) "" else "s"} hidden.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
            Switch(
                checked = includeIsotopes,
                onCheckedChange = onIncludeIsotopesChange
            )
        }
    }
}

@Composable
private fun IsomerSearchHeader(
    onBack: () -> Unit,
    onInfo: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(42.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.primary)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.ui_isomer_search),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.sp
            )
            Text(stringResource(R.string.ui_search_compounds_by_molecular_formula),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(0.56f)
            )
        }
        IconButton(onClick = onInfo, modifier = Modifier.size(42.dp)) {
            Icon(Icons.Default.Info, "Isomer search info", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun IsomerEmptyState() {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(stringResource(R.string.ui_no_isomer_search_yet),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(stringResource(R.string.ui_try_formulas_like_c2h6o_c6h6_or_c6h12o6),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}

// Formula input field

@Composable
fun IsomerSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = {
            Text(stringResource(R.string.ui_molecular_formula_e_g_c_h_o),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        leadingIcon = {
            Icon(
                Icons.Default.Atom,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
                modifier = Modifier.size(19.dp)
            )
        },
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (query.isNotEmpty()) {
                    IconButton(onClick = onClear) {
                        Icon(
                            Icons.Default.Close, "Clear formula",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
                IconButton(onClick = onSearch) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward, "Find Isomers",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        },
        visualTransformation = FormulaSubscriptTransformation,
        keyboardOptions = KeyboardOptions(
            imeAction = ImeAction.Search,
            capitalization = KeyboardCapitalization.Characters
        ),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        shape = RoundedCornerShape(16.dp),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface
        )
    )
}

// Header row above results

@Composable
fun IsomerResultsHeader(formula: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "Structural Isomers",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "$count result${if (count != 1) "s" else ""} for ${formula.toFormulaSubscript()}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
            )
        }
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
        ) {
            Text(
                text = count.toString(),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

// Individual isomer card

@Composable
fun IsomerCard(
    isomer: IsomerItem,
    selected: Boolean = false,
    selectionMode: Boolean = false,
    onClick: () -> Unit,
    onToggleSelected: () -> Unit = {}
) {
    val imageUrl = "https://pubchem.ncbi.nlm.nih.gov/rest/pug/compound/cid/${isomer.cid}" +
            "/PNG?record_type=2d&image_size=small"

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 2.dp else 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier.size(76.dp)
            ) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = "2D structure of ${isomer.title}",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp),
                    contentScale = ContentScale.Fit
                )
            }


            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = isomer.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    modifier = Modifier.wrapContentWidth()
                ) {
                    Text(
                        text = "CID ${isomer.cid}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        fontFamily = FontFamily.Monospace
                    )
                }
                if (isomer.isIsotope) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                        modifier = Modifier.wrapContentWidth()
                    ) {
                        Text(
                            text = "isotope",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }

            Checkbox(
                checked = selected,
                onCheckedChange = { onToggleSelected() },
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                    uncheckedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (selectionMode) 0.45f else 0.28f)
                )
            )
        }
    }
}
@Composable
fun IsomerLoadingState() {
    val reduceMotion = LocalReduceMotion.current
    val compactMode = LocalCompactMode.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SearchLoadingChemistryAnimation(
                reduceMotion = reduceMotion,
                compactMode = compactMode
            )
            Text(
                isomerLoadingStatusText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }
    }
}

internal const val isomerLoadingStatusText = "Searching PubChem for isomers…"

internal fun isomerLoadingAnimationLayout(compactMode: Boolean): SearchLoadingAnimationLayout =
    searchLoadingAnimationLayout(compactMode)

@Composable
fun IsomerErrorState(message: String) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.error.copy(alpha = 0.08f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Atom,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
            )
        }
    }
}

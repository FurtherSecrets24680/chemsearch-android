package com.furthersecrets.chemsearch.ui

import com.furthersecrets.chemsearch.R
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import coil.compose.AsyncImage
import com.furthersecrets.chemsearch.data.ChemUiState
import com.furthersecrets.chemsearch.data.IsomerItem
import com.furthersecrets.chemsearch.data.SearchErrorKind

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
    var sortMode by remember { mutableStateOf(IsomerSortMode.RELEVANCE) }
    var selectedCids by remember { mutableStateOf<List<Long>>(emptyList()) }
    val analysis = remember(state.isomerQuery) { analyzeIsomerFormula(state.isomerQuery) }
    val showInsight = remember(analysis, state.isomerQuery, state.isomers) {
        state.isomerQuery.isNotBlank() && (state.isomers.isEmpty() || analysis != null)
    }
    val sortedIsomers = remember(state.isomers, sortMode) { sortIsomersForDisplay(state.isomers, sortMode) }
    val visibleIsomers = remember(sortedIsomers, state.isomerResultLimit, includeIsotopes) {
        visibleIsomers(
            isomers = sortedIsomers,
            includeIsotopes = includeIsotopes,
            maxResults = state.isomerResultLimit
        )
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
    val hasIsotopeToggle = state.isomers.any { it.isIsotope }
    BackHandler(onBack = onBack)

    LaunchedEffect(visibleCidSet) {
        selectedCids = selectedCids.filter { it in visibleCidSet }
    }
    LaunchedEffect(state.isomerQuery) {
        selectedCids = emptyList()
    }

    if (showInfo) {
        InfoDialog(
            titleRes = R.string.ui_isomer_search,
            entries = listOf(
                R.string.ui_what_it_does to R.string.ui_isomer_what_it_does_desc,
                R.string.ui_how_to_use_it to R.string.ui_isomer_how_to_desc,
                R.string.ui_results to R.string.ui_isomer_results_desc,
                R.string.ui_limitations to R.string.ui_isomer_limitations_desc
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
            if (showInsight) {
                item {
                    IsomerFormulaInsightCard(
                        analysis = analysis,
                        rawInput = state.isomerQuery
                    )
                }
            }
            if (state.isLoadingIsomers) {
                item { IsomerLoadingState() }
                items(3) { IsomerSkeletonCard() }
            }
            state.isomerError?.let { error ->
                item {
                    IsomerErrorState(
                        message = error,
                        errorKind = state.isomerErrorKind,
                        onRetry = { onSearch() }
                    )
                }
            }
            if (state.isomers.isNotEmpty()) {
                item {
                    IsomerResultsHeader(
                        formula = state.isomerQuery.trim(),
                        count = visibleIsomers.size,
                        sortMode = sortMode,
                        onSortModeChange = { sortMode = it }
                    )
                }
                if (hasIsotopeToggle) {
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
                text = { Text(stringResource(R.string.ui_compare_count, compareQueries.size)) },
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

/**
 * Live read-out of what the typed formula means, before and after searching:
 * normalized Hill notation, total atoms, molar mass, and degrees of
 * unsaturation. Shows an invalid-formula warning instead when parsing fails.
 */
@Composable
private fun IsomerFormulaInsightCard(
    analysis: IsomerFormulaAnalysis?,
    rawInput: String
) {
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val accent = if (analysis == null) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }
    ChemCardSurfaceStatic(accent = accent) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (analysis == null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(17.dp)
                    )
                    Text(
                        stringResource(R.string.ui_isomer_invalid_formula),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Text(
                    stringResource(R.string.ui_try_formulas_like_c2h6o_c6h6_or_c6h12o6),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Column(modifier = Modifier.weight(1.2f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            stringResource(R.string.ui_isomer_insight_formula),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = accent
                        )
                        Text(
                            analysis.normalized.toFormulaSubscript(),
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Column(modifier = Modifier.weight(0.8f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            stringResource(R.string.ui_isomer_insight_atoms),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = accent
                        )
                        Text(
                            analysis.atomCount.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    analysis.molarMass?.let { mass ->
                        Column(modifier = Modifier.weight(1.3f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                stringResource(R.string.ui_isomer_mass),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = accent
                            )
                            Text(
                                String.format(java.util.Locale.US, "%.2f g/mol", mass),
                                style = MaterialTheme.typography.titleMedium,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                analysis.dbe?.let { dbe ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.12f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = accent.copy(alpha = if (isLight) 0.10f else 0.16f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.30f))
                        ) {
                            Text(
                                text = formatIsomerDbe(dbe),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelMedium,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = accent
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(
                                stringResource(R.string.ui_isomer_insight_dbe),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface.copy(0.72f)
                            )
                            Text(
                                stringResource(R.string.ui_isomer_insight_dbe_desc),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(0.48f),
                                lineHeight = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

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
                    if (includeIsotopes) stringResource(R.string.ui_isotope_showing_substituted)
                    else stringResource(R.string.ui_isotope_results_hidden, hiddenCount, if (hiddenCount == 1) "" else "s"),
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
            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.ui_back), tint = MaterialTheme.colorScheme.primary)
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
            Icon(Icons.Default.Info, stringResource(R.string.ui_isomer_search_info), tint = MaterialTheme.colorScheme.primary)
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
                            Icons.Default.Close, stringResource(R.string.ui_clear_formula),
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
                            Icons.AutoMirrored.Filled.ArrowForward, stringResource(R.string.ui_find_isomers),
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
internal fun IsomerResultsHeader(
    formula: String,
    count: Int,
    sortMode: IsomerSortMode,
    onSortModeChange: (IsomerSortMode) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(R.string.ui_structural_isomers),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.ui_results_count_for_formula, count, if (count != 1) "s" else "", formula.toFormulaSubscript()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
            )
        }
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.22f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IsomerSortChip(
                    label = stringResource(R.string.ui_isomer_sort_relevance),
                    selected = sortMode == IsomerSortMode.RELEVANCE,
                    onClick = { onSortModeChange(IsomerSortMode.RELEVANCE) }
                )
                IsomerSortChip(
                    label = stringResource(R.string.ui_isomer_sort_name),
                    selected = sortMode == IsomerSortMode.NAME,
                    onClick = { onSortModeChange(IsomerSortMode.NAME) }
                )
            }
        }
    }
}

@Composable
private fun IsomerSortChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(0.6f)
        )
    }
}

// Skeleton placeholder shown while isomers load

@Composable
private fun IsomerSkeletonCard() {
    val shimmer = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    ChemCardSurfaceStatic {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .background(shimmer, RoundedCornerShape(10.dp))
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(14.dp)
                        .background(shimmer, RoundedCornerShape(5.dp))
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.35f)
                        .height(11.dp)
                        .background(shimmer, RoundedCornerShape(5.dp))
                )
            }
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
    val context = LocalContext.current
    val imageUrl = "https://pubchem.ncbi.nlm.nih.gov/rest/pug/compound/cid/${isomer.cid}" +
            "/PNG?record_type=2d&image_size=small"

    ChemCardSurface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        selected = selected
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
                    contentDescription = stringResource(R.string.ui_2d_structure_of, isomer.title),
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ChemAccentPill(
                        text = stringResource(R.string.ui_cid_label, isomer.cid.toString()),
                        accent = MaterialTheme.colorScheme.primary
                    )
                    if (isomer.isIsotope) {
                        ChemAccentPill(
                            text = stringResource(R.string.ui_isotope),
                            accent = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }

            TextButton(
                onClick = {
                    val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText(isomer.title, isomer.title))
                    Toast.makeText(context, context.getString(R.string.ui_copied_to_clipboard), Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.size(34.dp),
                contentPadding = PaddingValues(4.dp)
            ) {
                Icon(
                    Icons.Default.Copy,
                    contentDescription = stringResource(R.string.ui_copy),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    modifier = Modifier.size(16.dp)
                )
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
                stringResource(R.string.ui_searching_pubchem_for_isomers),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }
    }
}

internal fun isomerLoadingAnimationLayout(compactMode: Boolean): SearchLoadingAnimationLayout =
    searchLoadingAnimationLayout(compactMode)

@Composable
fun IsomerErrorState(
    message: String,
    errorKind: SearchErrorKind? = null,
    onRetry: (() -> Unit)? = null
) {
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val (accent, containerAlpha) = when (errorKind) {
        SearchErrorKind.NOT_FOUND ->
            MaterialTheme.colorScheme.tertiary to 0.08f
        SearchErrorKind.THROTTLED ->
            Color(0xFFF59E0B) to 0.10f
        SearchErrorKind.NETWORK ->
            Color(0xFFEF4444) to 0.08f
        SearchErrorKind.TIMEOUT, SearchErrorKind.SERVER ->
            Color(0xFFF97316) to 0.08f
        SearchErrorKind.BAD_REQUEST, SearchErrorKind.OTHER, null ->
            MaterialTheme.colorScheme.error to 0.08f
    }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = accent.copy(alpha = containerAlpha),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = if (isLight) 0.34f else 0.28f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = accent,
                modifier = Modifier.weight(1f),
                lineHeight = 16.sp
            )
            if (onRetry != null) {
                IconButton(onClick = onRetry, modifier = Modifier.size(34.dp)) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = stringResource(R.string.ui_retry),
                        tint = accent,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }
    }
}

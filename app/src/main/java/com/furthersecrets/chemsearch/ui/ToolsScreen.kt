package com.furthersecrets.chemsearch.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.furthersecrets.chemsearch.ChemViewModel
import com.furthersecrets.chemsearch.data.ApiClient
import com.furthersecrets.chemsearch.data.AiProvider
import com.furthersecrets.chemsearch.data.BalancedReactionResult
import com.furthersecrets.chemsearch.data.CompoundProperty
import com.furthersecrets.chemsearch.data.DescSource
import com.furthersecrets.chemsearch.data.GeminiContent
import com.furthersecrets.chemsearch.data.GeminiPart
import com.furthersecrets.chemsearch.data.GeminiRequest
import com.furthersecrets.chemsearch.data.GhsData
import com.furthersecrets.chemsearch.data.GroqMessage
import com.furthersecrets.chemsearch.data.GroqRequest
import com.furthersecrets.chemsearch.data.PhPohInputType
import com.furthersecrets.chemsearch.data.SdfSource
import com.furthersecrets.chemsearch.data.balanceChemicalReaction
import com.furthersecrets.chemsearch.data.buildSdfIdentifierCandidates
import com.furthersecrets.chemsearch.data.calculateOxidationStates
import com.furthersecrets.chemsearch.data.fetchGeneratedSdfFromIdentifiers
import com.furthersecrets.chemsearch.data.calculatePhPoh
import com.furthersecrets.chemsearch.data.calculateMolarMass as calculateDomainMolarMass
import com.furthersecrets.chemsearch.data.formatPhPohNumber
import com.furthersecrets.chemsearch.data.formatConventionalFormula
import com.furthersecrets.chemsearch.data.formatCompoundComparisonValue
import com.furthersecrets.chemsearch.data.isUsableSdf
import com.furthersecrets.chemsearch.data.parseFormulaElementCounts
import com.furthersecrets.chemsearch.data.parseCompareCompoundInputs
import com.furthersecrets.chemsearch.data.previewComparisonCellText
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// TOOLS SCREEN
private const val TOOL_ORDER_PREF = "tool_order"
private const val TOOL_VIEW_MODE_PREF = "tool_view_mode"

private fun loadToolOrder(prefs: SharedPreferences, defaultIds: List<Int>): List<Int> {
    val stored = prefs.getString(TOOL_ORDER_PREF, null)
        ?.split(',')
        ?.mapNotNull { it.toIntOrNull() }
        ?: emptyList()
    val order = stored.filter { it in defaultIds }.toMutableList()
    defaultIds.forEach { id ->
        if (id !in order) order.add(id)
    }
    return order
}

private fun saveToolOrder(prefs: SharedPreferences, order: List<Int>) {
    prefs.edit().putString(TOOL_ORDER_PREF, order.joinToString(",")).apply()
}

private enum class ToolCategory(val label: String) {
    ALL("All"),
    VISUALIZE("Visualize"),
    CALCULATORS("Calculators"),
    REACTIONS("Reactions"),
    STOICHIOMETRY("Stoichiometry"),
    STRUCTURE("Structure")
}

private enum class ToolViewMode { LIST, GRID }

private val TOOL_CATEGORIES = listOf(
    ToolCategory.ALL,
    ToolCategory.VISUALIZE,
    ToolCategory.CALCULATORS,
    ToolCategory.REACTIONS,
    ToolCategory.STOICHIOMETRY,
    ToolCategory.STRUCTURE
)

private data class ToolDefinition(
    val id: Int,
    val icon: ChemIconSpec,
    val title: String,
    val subtitle: String,
    val category: ToolCategory
)

@Composable
fun ToolsScreen(
    isDark: Boolean,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    jumpToTool: Int = 0,
    jumpToToolVersion: Int = 0,
    vm: ChemViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    defaultDescSource: DescSource = DescSource.PUBCHEM,
    aiProvider: AiProvider = AiProvider.GEMINI,
    getAiKey: (AiProvider) -> String? = { null },
    getSelectedAiModel: (AiProvider) -> String = { it.modelName },
    onNavigateToSearch: () -> Unit = {},
    onSearchCompoundFromTool: (String) -> Unit = {}
) {
    var selectedTool by remember { mutableStateOf(0) }
    var toolSearch by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(ToolCategory.ALL) }
    var isReordering by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val prefs = remember(context) { context.getSharedPreferences("chemsearch_prefs", Context.MODE_PRIVATE) }
    var toolViewMode by remember {
        mutableStateOf(
            ToolViewMode.entries.firstOrNull { it.name == prefs.getString(TOOL_VIEW_MODE_PREF, null) }
                ?: ToolViewMode.LIST
        )
    }
    val compact = LocalCompactMode.current

    LaunchedEffect(jumpToTool, jumpToToolVersion) {
        if (jumpToTool != 0) selectedTool = jumpToTool
    }
    LaunchedEffect(selectedTool) {
        if (selectedTool != 0 && isReordering) isReordering = false
    }

    val defaultTools = listOf(
        ToolDefinition(
            id = 2,
            icon = ChemAppIcons.Calculator,
            title = "Molar Mass Calculator",
            subtitle = "Enter a molecular formula and get the molar mass",
            category = ToolCategory.CALCULATORS
        ),
        ToolDefinition(
            id = 5,
            icon = ChemAppIcons.ArrowLeftRight,
            title = "Reaction Balancer",
            subtitle = "Balance any chemical equation automatically",
            category = ToolCategory.REACTIONS
        ),
        ToolDefinition(
            id = 14,
            icon = ChemAppIcons.Droplets,
            title = "pH / pOH Calculator",
            subtitle = "Convert pH, pOH, [H+], and [OH-]",
            category = ToolCategory.CALCULATORS
        ),
        ToolDefinition(
            id = 3,
            icon = ChemAppIcons.Atom,
            title = "Oxidation State Finder",
            subtitle = "Find oxidation states of each element in a compound",
            category = ToolCategory.CALCULATORS
        ),
        ToolDefinition(
            id = 7,
            icon = ChemAppIcons.ListFilter,
            title = "Limiting Reagent",
            subtitle = "Find limiting reagent, ratios, and theoretical yield",
            category = ToolCategory.STOICHIOMETRY
        ),
        ToolDefinition(
            id = 8,
            icon = ChemAppIcons.Percent,
            title = "Percent Yield",
            subtitle = "Compare actual yield against theoretical yield",
            category = ToolCategory.STOICHIOMETRY
        ),
        ToolDefinition(
            id = 11,
            icon = ChemAppIcons.Droplet,
            title = "Dilution Calculator",
            subtitle = "Solve C₁V₁ = C₂V₂ for solutions",
            category = ToolCategory.CALCULATORS
        ),
        ToolDefinition(
            id = 12,
            icon = ChemAppIcons.Wind,
            title = "Ideal Gas Law",
            subtitle = "Solve PV = nRT for gases",
            category = ToolCategory.CALCULATORS
        ),
        ToolDefinition(
            id = 13,
            icon = ChemAppIcons.GitCompareArrows,
            title = "Compare Compounds",
            subtitle = "Compare formulas, mass, identifiers, safety, and structures",
            category = ToolCategory.CALCULATORS
        ),
        ToolDefinition(
            id = 6,
            icon = ChemAppIcons.Dna,
            title = "Isomer Finder",
            subtitle = "Enter a molecular formula to find its structural isomers",
            category = ToolCategory.STRUCTURE
        ),
        ToolDefinition(
            id = 4,
            icon = ChemAppIcons.Network,
            title = "SMILES Visualizer",
            subtitle = "Paste a SMILES string to view its 2D and 3D structure",
            category = ToolCategory.VISUALIZE
        ),
        ToolDefinition(
            id = 1,
            icon = ChemAppIcons.Axis3d,
            title = "Custom 3D Molecule Viewer",
            subtitle = "Load any .sdf or .mol file and view it in 3D",
            category = ToolCategory.VISUALIZE
        ),
        ToolDefinition(
            id = 9,
            icon = ChemAppIcons.SlidersHorizontal,
            title = "Reaction Scaling",
            subtitle = "Scale reactants for a target product amount",
            category = ToolCategory.STOICHIOMETRY
        ),
    )
    val defaultToolIds = defaultTools.map { it.id }
    var toolOrder by remember { mutableStateOf(loadToolOrder(prefs, defaultToolIds)) }
    val toolsById = defaultTools.associateBy { it.id }
    val orderedTools = toolOrder.mapNotNull { id -> toolsById[id] }.ifEmpty { defaultTools }

    val filteredTools = remember(toolSearch, orderedTools, selectedCategory) {
        val categoryFiltered = if (selectedCategory == ToolCategory.ALL) orderedTools
        else orderedTools.filter { it.category == selectedCategory }
        if (toolSearch.isBlank()) categoryFiltered
        else categoryFiltered.filter {
            it.title.contains(toolSearch, ignoreCase = true) ||
                    it.subtitle.contains(toolSearch, ignoreCase = true)
        }
    }
    val visibleTools = if (isReordering) orderedTools else filteredTools

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { focusManager.clearFocus() },
        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = if (compact) 0.dp else 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Tools",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            if (selectedTool == 0 && defaultTools.size > 1) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!isReordering) {
                        ToolViewToggle(
                            viewMode = toolViewMode,
                            onViewModeChange = { mode ->
                                toolViewMode = mode
                                prefs.edit().putString(TOOL_VIEW_MODE_PREF, mode.name).apply()
                            }
                        )
                    }
                    IconButton(
                        onClick = {
                            toolOrder = defaultToolIds
                            saveToolOrder(prefs, defaultToolIds)
                            selectedCategory = ToolCategory.ALL
                            toolSearch = ""
                        },
                        modifier = Modifier.size(if (compact) 34.dp else 38.dp)
                    ) {
                        Icon(
                            Icons.Default.RestartAlt,
                            contentDescription = "Reset tool order",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(if (compact) 19.dp else 21.dp)
                        )
                    }
                    TextButton(
                        onClick = {
                            val next = !isReordering
                            isReordering = next
                            if (next) {
                                toolSearch = ""
                                selectedCategory = ToolCategory.ALL
                                toolViewMode = ToolViewMode.LIST
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text(if (isReordering) "Done" else "Reorder")
                    }
                }
            }
        }
        if (selectedTool == 0 && isReordering) {
            Text(
                "Reorder mode: use the arrows to move tools.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(0.55f)
            )
        }

        if (selectedTool == 0) {
            if (!isReordering) {
                Column(verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp)) {
                    OutlinedTextField(
                        value = toolSearch,
                        onValueChange = { toolSearch = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                "Search tools…",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Search, contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = {
                            if (toolSearch.isNotEmpty()) {
                                IconButton(onClick = { toolSearch = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear search",
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                        modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TOOL_CATEGORIES.forEach { category ->
                            CategoryPill(
                                label = category.label,
                                selected = selectedCategory == category,
                                onClick = { selectedCategory = category }
                            )
                        }
                    }
                }
            }

            if (!isReordering && filteredTools.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = if (compact) 20.dp else 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No tools match \"$toolSearch\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            } else {
                if (!isReordering && toolViewMode == ToolViewMode.GRID) {
                    visibleTools.chunked(2).forEach { rowTools ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp)
                        ) {
                            rowTools.forEach { tool ->
                                ToolGridCard(
                                    icon = tool.icon,
                                    title = tool.title,
                                    subtitle = tool.subtitle,
                                    onClick = { selectedTool = tool.id },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (rowTools.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                } else {
                    visibleTools.forEachIndexed { index, tool ->
                        ToolCard(
                            icon = tool.icon,
                            title = tool.title,
                            subtitle = tool.subtitle,
                            categoryLabel = tool.category.label,
                            onClick = { selectedTool = tool.id },
                            enableSelect = !isReordering,
                            showReorderControls = isReordering,
                            canMoveUp = isReordering && index > 0,
                            canMoveDown = isReordering && index < visibleTools.lastIndex,
                            onMoveUp = {
                                if (isReordering && index > 0) {
                                    val updated = toolOrder.toMutableList()
                                    val item = updated.removeAt(index)
                                    updated.add(index - 1, item)
                                    toolOrder = updated
                                    saveToolOrder(prefs, updated)
                                }
                            },
                            onMoveDown = {
                                if (isReordering && index < visibleTools.lastIndex) {
                                    val updated = toolOrder.toMutableList()
                                    val item = updated.removeAt(index)
                                    updated.add(index + 1, item)
                                    toolOrder = updated
                                    saveToolOrder(prefs, updated)
                                }
                            }
                        )
                    }
                }
            }
        } else {
            TextButton(
                onClick = { selectedTool = 0; toolSearch = "" },
                contentPadding = PaddingValues(horizontal = 0.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Back to Tools")
            }

            when (selectedTool) {
                1 -> SdfViewerTool(isDark = isDark)
                2 -> MolarMassCalculator()
                3 -> OxidationStateFinder()
                4 -> SmilesVisualizer(isDark = isDark)
                5 -> ReactionBalancer()
                6 -> IsomerFinderTool(vm = vm, onNavigateToSearch = onNavigateToSearch)
                7 -> StoichiometryCalculator(
                    mode = StoichiometryMode.LIMITING,
                    title = "Limiting Reagent"
                )
                8 -> StoichiometryCalculator(
                    mode = StoichiometryMode.YIELD,
                    title = "Percent Yield"
                )
                9 -> StoichiometryCalculator(
                    mode = StoichiometryMode.SCALING,
                    title = "Reaction Scaling"
                )
                11 -> DilutionCalculatorTool()
                12 -> IdealGasLawTool()
                13 -> CompareCompoundsTool(
                    defaultDescSource = defaultDescSource,
                    aiProvider = aiProvider,
                    getAiKey = getAiKey,
                    getSelectedAiModel = getSelectedAiModel,
                    onSearchCompound = onSearchCompoundFromTool
                )
                14 -> PhPohCalculatorTool()
            }
        }
    }
}

@Composable
private fun ToolViewToggle(
    viewMode: ToolViewMode,
    onViewModeChange: (ToolViewMode) -> Unit
) {
    val compact = LocalCompactMode.current
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(0.65f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(0.18f))
    ) {
        Row(modifier = Modifier.padding(3.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(
                ToolViewMode.LIST to Icons.AutoMirrored.Filled.ViewList,
                ToolViewMode.GRID to Icons.Default.GridView
            ).forEach { (mode, icon) ->
                val selected = viewMode == mode
                Surface(
                    onClick = { onViewModeChange(mode) },
                    shape = CircleShape,
                    color = if (selected) MaterialTheme.colorScheme.primary.copy(0.16f) else Color.Transparent,
                    modifier = Modifier.size(if (compact) 30.dp else 32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            icon,
                            contentDescription = if (mode == ToolViewMode.LIST) "List view" else "Grid view",
                            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(0.55f),
                            modifier = Modifier.size(if (compact) 16.dp else 18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolGridCard(
    icon: ChemIconSpec,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val compact = LocalCompactMode.current
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(if (compact) 0.92f else 0.95f),
        shape = RoundedCornerShape(if (compact) 16.dp else 18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (compact) 13.dp else 15.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .size(if (compact) 42.dp else 48.dp)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(0.1f),
                        RoundedCornerShape(if (compact) 11.dp else 12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                ChemIcon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(if (compact) 22.dp else 25.dp)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 5.dp)) {
                Text(
                    title,
                    style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = if (compact) 3 else 3,
                    letterSpacing = 0.sp
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.52f),
                    maxLines = if (compact) 3 else 3,
                    overflow = TextOverflow.Ellipsis,
                    letterSpacing = 0.sp
                )
            }
        }
    }
}

@Composable
private fun ToolCard(
    icon: ChemIconSpec,
    title: String,
    subtitle: String,
    categoryLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enableSelect: Boolean = true,
    showReorderControls: Boolean = false,
    canMoveUp: Boolean = false,
    canMoveDown: Boolean = false,
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {}
) {
    val compact = LocalCompactMode.current
    Card(
        onClick = { if (enableSelect) onClick() },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(if (compact) 14.dp else 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
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
                ChemIcon(
                    icon,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(if (compact) 22.dp else 26.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 3.dp)
            ) {
                Text(
                    title,
                    style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                ToolCategoryIndicatorPill(categoryLabel)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
            }

            if (showReorderControls) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    IconButton(
                        onClick = onMoveUp,
                        enabled = canMoveUp,
                        modifier = Modifier.size(if (compact) 24.dp else 28.dp)
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowUp,
                            contentDescription = "Move up",
                            tint = MaterialTheme.colorScheme.onSurface.copy(if (canMoveUp) 0.6f else 0.25f),
                            modifier = Modifier.size(if (compact) 18.dp else 24.dp)
                        )
                    }
                    IconButton(
                        onClick = onMoveDown,
                        enabled = canMoveDown,
                        modifier = Modifier.size(if (compact) 24.dp else 28.dp)
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = "Move down",
                            tint = MaterialTheme.colorScheme.onSurface.copy(if (canMoveDown) 0.6f else 0.25f),
                            modifier = Modifier.size(if (compact) 18.dp else 24.dp)
                        )
                    }
                }
            } else {
                Icon(
                    Icons.Default.ChevronRight,
                    null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(0.3f),
                    modifier = Modifier.size(if (compact) 18.dp else 24.dp)
                )
            }
        }
    }
}

@Composable
private fun CategoryPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val compact = LocalCompactMode.current
    val background = if (selected) MaterialTheme.colorScheme.primary.copy(0.12f) else MaterialTheme.colorScheme.surfaceVariant
    val border = if (selected) MaterialTheme.colorScheme.primary.copy(0.35f) else MaterialTheme.colorScheme.outline.copy(0.2f)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = background,
        border = BorderStroke(1.dp, border)
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = if (compact) 10.dp else 12.dp, vertical = if (compact) 5.dp else 6.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(0.7f)
        )
    }
}

@Composable
private fun ToolCategoryIndicatorPill(label: String) {
    val compact = LocalCompactMode.current
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.primary.copy(0.12f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(0.25f))
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = if (compact) 7.dp else 8.dp, vertical = if (compact) 2.dp else 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

// TOOL 1 : CUSTOM 3D MOLECULE VIEWER
@Composable
fun SdfViewerTool(isDark: Boolean) {
    val context = LocalContext.current
    var sdfContent by remember { mutableStateOf<String?>(null) }
    var fileName by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val stream = context.contentResolver.openInputStream(uri)
            val content = stream?.bufferedReader()?.readText()
            stream?.close()
            if (content != null) {
                sdfContent = content
                fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "file.sdf"
                error = null
            } else {
                error = "Could not read file."
            }
        } catch (e: Exception) {
            error = "Error reading file: ${e.message}"
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        var showInfo by remember { mutableStateOf(false) }
        if (showInfo) {
            InfoDialog(
                title = "Custom 3D Molecule Viewer",
                entries = listOf(
                    "What is an SDF file?" to "Structure Data File (.sdf) is a standard chemical file format that stores 3D atomic coordinates and bond information for one or more molecules.",
                    "What is a MOL file?" to "A .mol file is the single-molecule variant of SDF. Both formats are widely exported by chemistry software like ChemDraw, Avogadro, and PubChem.",
                    "How to get an SDF file" to "You can download SDF files from PubChem by searching a compound and choosing '3D SDF' from the download options, or export them from any molecular editor.",
                    "Controls" to "Drag to rotate the molecule. Pinch to zoom in and out. The model auto-spins when idle, so tap to pause. Tap the reset button to return to the default view.",
                    "CPK coloring" to "Atoms are colored using the Jmol CPK convention: carbon is dark grey, oxygen is red, nitrogen is blue, hydrogen is white, and so on across all 118 elements."
                ),
                onDismiss = { showInfo = false }
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Custom 3D Molecule Viewer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            IconButton(onClick = { showInfo = true }, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Info, contentDescription = "Info", tint = MaterialTheme.colorScheme.onSurface.copy(0.35f), modifier = Modifier.size(16.dp))
            }
        }
        if (sdfContent == null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(0.1f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.FolderOpen, null, tint = MaterialTheme.colorScheme.primary.copy(0.6f), modifier = Modifier.size(40.dp)) }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("No file loaded", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Load any .sdf or .mol file from your device to view it in 3D",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.5f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                    Button(
                        onClick = { filePicker.launch("*/*") },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Choose File")
                    }
                }
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.InsertDriveFile, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text(fileName ?: "file.sdf", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { filePicker.launch("*/*") }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Text("Change", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Box(modifier = Modifier.fillMaxWidth().height(360.dp)) {
                    Viewer3D(cid = -1L, sdfData = sdfContent!!, isDark = isDark)
                }
            }
        }

        if (error != null) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(0.5f))
            ) {
                Text(error!!, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

// TOOL 2 : MOLAR MASS CALCULATOR
private data class CalcResult(
    val molarMass: Double,
    val breakdown: List<Triple<String, Int, Double>>,
    val error: String? = null
)

private fun parseFormulaForCalc(formula: String): Map<String, Int> =
    parseFormulaElementCounts(formula)

private fun calculateMolarMass(formula: String): CalcResult {
    val result = calculateDomainMolarMass(formula)
    return CalcResult(
        molarMass = result.molarMass,
        breakdown = result.breakdown.map { Triple(it.element, it.count, it.contribution) },
        error = result.error
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MolarMassCalculator() {
    var input by remember { mutableStateOf(TextFieldValue("")) }
    val result = remember(input.text) {
        if (input.text.isBlank()) null else calculateMolarMass(input.text)
    }
    val focusManager = LocalFocusManager.current

    val examples = listOf("H₂O", "C₆H₁₂O₆", "NaCl", "H₂SO₄", "Ca(OH)₂", "C₂H₅OH", "Fe₂O₃")

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        var showInfo by remember { mutableStateOf(false) }
        if (showInfo) {
            InfoDialog(
                title = "Molar Mass Calculator",
                entries = listOf(
                    "What is molar mass?" to "Molar mass is the mass of one mole (6.022 × 10²³ particles) of a substance, expressed in grams per mole (g/mol). It equals the sum of atomic weights of all atoms in the formula.",
                    "How to enter a formula" to "Type the molecular formula using standard element symbols with numbers for atom counts. Parentheses are supported for groups, e.g. Ca(OH)2 or Al2(SO4)3.",
                    "Case sensitivity" to "Element symbols are case-sensitive! 'Co' is cobalt, 'CO' is carbon monoxide. Always capitalize only the first letter of each element symbol.",
                    "Atomic weights" to "Atomic weights used here are the standard values from IUPAC, based on the natural isotopic abundance of each element.",
                    "Elemental breakdown" to "The breakdown table shows each element's contribution to the total molar mass, both as an absolute value (g/mol) and as a percentage by mass.",
                ),
                onDismiss = { showInfo = false }
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Molar Mass Calculator", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            IconButton(onClick = { showInfo = true }, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Info, contentDescription = "Info", tint = MaterialTheme.colorScheme.onSurface.copy(0.35f), modifier = Modifier.size(16.dp))
            }
        }

        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("Molecular Formula") },
            placeholder = { Text("e.g. H2O, Ca(OH)2, CuSO4·5H2O", color = MaterialTheme.colorScheme.onSurface.copy(0.4f)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            singleLine = true,
            visualTransformation = FormulaSubscriptTransformation,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            trailingIcon = {
                if (input.text.isNotBlank()) {
                    IconButton(onClick = { input = TextFieldValue("") }) {
                        Icon(Icons.Default.Clear, null, modifier = Modifier.size(18.dp))
                    }
                }
            }
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Insert:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(0.45f))
            listOf("(" to "(", ")" to ")", "·" to "·").forEach { (label, insert) ->
                Surface(
                    onClick = { input = insertIntoField(input, insert) },
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(0.3f))
                ) {
                    Text(
                        label,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        result?.let { calc ->
            if (calc.error != null) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(
                            0.4f
                        )
                    )
                ) {
                    Text(
                        calc.error,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            } else {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("MOLAR MASS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.45f))
                                Text(toSubscriptFormula(input.text), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primary.copy(0.1f),
                                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(0.4f))
                            ) {
                                Text(
                                    "${"%.4f".format(calc.molarMass)} g/mol",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.12f))

                        Text(
                            "ELEMENTAL BREAKDOWN",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.45f)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Element", modifier = Modifier.weight(1.2f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
                            Text("Count", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
                            Text("g/mol", modifier = Modifier.weight(1.3f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
                            Text("%", modifier = Modifier.weight(0.7f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(0.4f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                        }

                        calc.breakdown.forEach { (el, cnt, contrib) ->
                            val pct = (contrib / calc.molarMass * 100).toFloat()
                            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(modifier = Modifier.weight(1.2f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Box(modifier = Modifier.size(26.dp).background(MaterialTheme.colorScheme.primary.copy(0.1f), RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                                            Text(el, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                                        }
                                    }
                                    Text("×$cnt", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface.copy(0.7f))
                                    Text("%.3f".format(contrib), modifier = Modifier.weight(1.3f), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                                    Text("%.1f%%".format(pct), modifier = Modifier.weight(0.7f), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary, textAlign = androidx.compose.ui.text.style.TextAlign.End)
                                }
                                Box(modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.outline.copy(0.1f))) {
                                    Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(pct / 100f).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.primary.copy(0.65f)))
                                }
                            }
                        }
                    }
                }
            }
        }

        Text(
            "EXAMPLES",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(0.4f)
        )
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("H2O", "NaCl", "Ca(OH)2", "C6H12O6", "H2SO4", "CuSO4·5H2O", "MgSO4·7H2O", "Fe2O3").forEach { ex ->
                val isActive = input.text == ex
                FilterChip(
                    selected = isActive,
                    onClick = { input = fieldValueAtEnd(ex); focusManager.clearFocus() },
                    label = { Text(toSubscriptFormula(ex), style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace) }
                )
            }
        }

    }
}

// TOOL 3 : OXIDATION STATE FINDER
private data class OsResult(
    val states: List<Pair<String, String>> = emptyList(),
    val note: String? = null,
    val error: String? = null
)

private fun findOxidationStates(formula: String, chargeIn: Int): OsResult {
    val result = calculateOxidationStates(formula, chargeIn)
    return OsResult(
        states = result.states,
        note = result.note,
        error = result.error
    )
}

@Composable
fun OxidationStateFinder() {
    var formula by remember { mutableStateOf("") }
    var chargeInput by remember { mutableStateOf("0") }
    var result by remember { mutableStateOf<OsResult?>(null) }
    val focusManager = LocalFocusManager.current

    val examples = listOf("KMnO4", "H2SO4", "Fe2O3", "Cr2O7" to -2, "NaCl" to 0, "HNO3" to 0)

    var showInfo by remember { mutableStateOf(false) }
    if (showInfo) {
        InfoDialog(
            title = "Oxidation State Finder",
            entries = listOf(
                "What is an oxidation state?" to "A number assigned to an atom representing its degree of oxidation. Positive = electrons lost, negative = electrons gained. Used to track electron transfer in redox reactions.",
                "Rules applied" to "F is always -1. Group 1 = +1, Group 2 = +2. Al, Ga, In, Sc, Y, La, Lu = +3. Zn, Cd = +2. Ag = +1. O defaults to -2 (except peroxides/superoxides). H defaults to +1 (except metal hydrides). Halogens (Cl, Br, I) default to -1 in simple halides, but are solved when bonded with oxygen or a more electronegative halogen.",
                "Halogen priority" to "Electronegativity order: F > Cl > Br > I. In oxo-halogen compounds and interhalogen compounds, the less electronegative halogen can take a positive OS. Example: HOCl -> Cl=+1. ICl3 -> I=+3, Cl=-1. ClF3 -> Cl=+3, F=-1.",
                "Special cases handled" to "Peroxides (O=-1): H2O2, Na2O2, BaO2. Superoxides (O=-½): KO2, NaO2. Ozonides (O=-⅓): KO3. Metal hydrides (H=-1): NaH, LiAlH4, NaBH4, CaH2.",
                "Overall charge" to "For neutral compounds enter 0. For polyatomic ions enter the ion charge. Examples: SO4²⁻ → charge -2. NH4⁺ → charge +1. MnO4⁻ → charge -1.",
                "Organic compounds" to "For single-carbon compounds (CH4, CO2, CCl4) the result is exact. For multi-carbon compounds, the app calculates an average oxidation state across all carbons, which is chemically meaningful for comparisons but does not reflect individual carbon environments. Ethanol (C2H5OH) has carbons at -3 and -1, but the app returns -2 as the average.",
                "Limitations" to "Compounds with 2 or more transition metals or unknown elements cannot be solved without additional information. Mixed-valence compounds like Fe3O4 (Fe²⁺ and Fe³⁺ coexist) return a fractional average with a warning. For these, enter the ion charge separately if known.",
                "Example" to "KMnO4 (charge 0): K=+1 (fixed), O=-2 (fixed, ×4). Mn = 0 − (+1) − 4(−2) = +7."
            ),
            onDismiss = { showInfo = false }
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Oxidation State Finder", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            IconButton(onClick = { showInfo = true }, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.onSurface.copy(0.35f), modifier = Modifier.size(16.dp))
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = formula,
                onValueChange = { formula = it; result = null },
                label = { Text("Formula") },
                placeholder = { Text("e.g. KMnO4", color = MaterialTheme.colorScheme.onSurface.copy(0.4f)) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                singleLine = true,
                visualTransformation = FormulaSubscriptTransformation,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )
            OutlinedTextField(
                value = chargeInput,
                onValueChange = { chargeInput = it; result = null },
                label = { Text("Charge") },
                placeholder = { Text("0", color = MaterialTheme.colorScheme.onSurface.copy(0.4f)) },
                modifier = Modifier.width(90.dp),
                shape = RoundedCornerShape(14.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    focusManager.clearFocus()
                    result = findOxidationStates(formula, chargeInput.toIntOrNull() ?: 0)
                })
            )
        }

        Button(
            onClick = {
                focusManager.clearFocus()
                result = findOxidationStates(formula, chargeInput.toIntOrNull() ?: 0)
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            enabled = formula.isNotBlank()
        ) {
            Text("Find Oxidation States")
        }

        result?.let { res ->
            if (res.error != null) {
                Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(0.4f))) {
                    Text(res.error, modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            } else {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("OXIDATION STATES", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.45f))
                            Text(
                                toSubscriptFormula(formula) + if ((chargeInput.toIntOrNull() ?: 0) != 0) " (${if ((chargeInput.toIntOrNull() ?: 0) > 0) "+${chargeInput}" else chargeInput})" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                            )
                        }

                        res.states.forEach { (el, os) ->
                            val osColor = when {
                                os == "?" -> MaterialTheme.colorScheme.onSurface.copy(0.4f)
                                os.startsWith("+") && os != "+0" -> Color(0xFF3B82F6)
                                os.startsWith("-") -> Color(0xFFEF4444)
                                else -> MaterialTheme.colorScheme.onSurface.copy(0.6f)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier.size(36.dp).background(osColor.copy(0.1f), RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(el, fontWeight = FontWeight.Bold, color = osColor, style = MaterialTheme.typography.bodyMedium)
                                }
                                Text(el, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                Surface(shape = RoundedCornerShape(8.dp), color = osColor.copy(0.1f), border = BorderStroke(1.dp, osColor.copy(0.4f))) {
                                    Text(
                                        os,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = osColor
                                    )
                                }
                            }
                        }

                        if (res.note != null) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.15f))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                                Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary.copy(0.6f), modifier = Modifier.size(14.dp))
                                Text(res.note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(0.6f), lineHeight = 17.sp)
                            }
                        }
                    }
                }
            }
        }

        Text("EXAMPLES", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("KMnO4" to 0, "H2SO4" to 0, "HOCl" to 0, "Fe2O3" to 0, "Cr2O7" to -2, "NH4" to 1, "HNO3" to 0).forEach { (f, c) ->
                FilterChip(
                    selected = formula == f && (chargeInput.toIntOrNull() ?: 0) == c,
                    onClick = { formula = f; chargeInput = c.toString(); focusManager.clearFocus(); result = findOxidationStates(f, c) },
                    label = { Text(toSubscriptFormula(f) + if (c != 0) " (${if (c > 0) "+$c" else "$c"})" else "", style = MaterialTheme.typography.labelMedium) }
                )
            }
        }

    }
}
// TOOL 4 : SMILES VISUALIZER
@Composable
fun SmilesVisualizer(isDark: Boolean) {
    var input by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var cidResult by remember { mutableStateOf<Long?>(null) }
    var compoundName by remember { mutableStateOf<String?>(null) }
    var sdfData by remember { mutableStateOf<String?>(null) }
    var sdfSource by remember { mutableStateOf<SdfSource?>(null) }
    var sdfMessage by remember { mutableStateOf<String?>(null) }
    var activeTab by remember { mutableStateOf(0) } // 0=2D, 1=3D
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    val examples = listOf(
        "Aspirin"   to "CC(=O)Oc1ccccc1C(=O)O",
        "Caffeine"  to "CN1C=NC2=C1C(=O)N(C(=O)N2C)C",
        "Glucose"   to "C([C@@H]1[C@H]([C@@H]([C@H](C(O1)O)O)O)O)O",
        "Ethanol"   to "CCO",
        "Benzene"   to "c1ccccc1"
    )

    var showInfo by remember { mutableStateOf(false) }
    if (showInfo) {
        InfoDialog(
            title = "SMILES Visualizer",
            entries = listOf(
                "What is SMILES?" to "Simplified Molecular Input Line Entry System. A notation that encodes molecular structure as a text string using atom symbols and bond characters.",
                "How to use" to "Paste any valid SMILES string and tap Visualize. The app looks up the compound on PubChem and shows its 2D structure and 3D model.",
                "Where to get SMILES" to "PubChem, ChemDraw, SciFinder, and most chemistry databases provide SMILES strings for compounds. You can also find them in published papers.",
                "Aromatic notation" to "Lowercase letters (c, n, o) denote aromatic atoms. For example, benzene is 'c1ccccc1' and pyridine is 'c1ccncc1'.",
                "Chirality" to "@  and @@ in SMILES denote stereocenters. The visualizer handles both standard and isomeric SMILES.",
                "Limitations" to "Only SMILES strings recognized by PubChem can be visualized. Novel or hypothetical molecules not in PubChem will return no result."
            ),
            onDismiss = { showInfo = false }
        )
    }

    fun visualize() {
        val smiles = input.trim()
        if (smiles.isBlank()) return
        focusManager.clearFocus()
        scope.launch {
            isLoading = true
            error = null
            cidResult = null
            compoundName = null
            sdfData = null
            sdfSource = null
            sdfMessage = null
            try {
                val body = okhttp3.FormBody.Builder().add("smiles", smiles).build()
                val request = okhttp3.Request.Builder()
                    .url("https://pubchem.ncbi.nlm.nih.gov/rest/pug/compound/smiles/cids/JSON")
                    .post(body)
                    .build()
                val response = withContext(Dispatchers.IO) {
                    ApiClient.rawHttp.newCall(request).execute()
                }
                val bodyStr = response.body.string()
                if (!response.isSuccessful || bodyStr.isBlank()) {
                    error = "Compound not found on PubChem. Check your SMILES string."
                    return@launch
                }
                val json = com.google.gson.Gson().fromJson(bodyStr, com.google.gson.JsonObject::class.java)
                val cidElement = json
                    ?.getAsJsonObject("IdentifierList")
                    ?.getAsJsonArray("CID")
                    ?.firstOrNull()
                if (cidElement == null) {
                    error = "No compound found for this SMILES string."
                    return@launch
                }
                val cid = cidElement.asJsonPrimitive.asLong
                cidResult = cid
                val props = withContext(Dispatchers.IO) {
                    runCatching { ApiClient.pubChem.getProperties(cid).propertyTable?.properties?.firstOrNull() }.getOrNull()
                }
                val syns = withContext(Dispatchers.IO) {
                    runCatching { ApiClient.pubChem.getSynonyms(cid) }.getOrNull()
                }
                compoundName = props?.title
                    ?: syns?.informationList?.information?.firstOrNull()?.synonym?.firstOrNull()
                val sdf = withContext(Dispatchers.IO) {
                    runCatching { ApiClient.pubChem.getSdf(cid).string() }.getOrNull()
                }
                if (sdf != null && isUsableSdf(sdf)) {
                    sdfData = sdf
                    sdfSource = SdfSource.PUBCHEM
                } else {
                    sdfMessage = "PubChem 3D unavailable. Trying generated fallback..."
                    val fallback = withContext(Dispatchers.IO) {
                        fetchGeneratedSdfFromIdentifiers(
                            buildSdfIdentifierCandidates(
                                smiles = props?.smiles ?: smiles,
                                connectivitySmiles = props?.connectivitySmiles,
                                inchi = props?.inchi,
                                inchiKey = props?.inchiKey
                            ),
                            expectedFormula = props?.molecularFormula
                        )
                    }
                    sdfData = fallback?.sdf
                    sdfSource = fallback?.source
                    sdfMessage = fallback?.message ?: "Formula-matched 3D fallback unavailable for this SMILES."
                }
            } catch (e: Exception) {
                error = "Error: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("SMILES Visualizer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            IconButton(onClick = { showInfo = true }, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.onSurface.copy(0.35f), modifier = Modifier.size(16.dp))
            }
        }

        OutlinedTextField(
            value = input,
            onValueChange = { input = it; cidResult = null; error = null },
            label = { Text("SMILES String") },
            placeholder = { Text("e.g. CCO", color = MaterialTheme.colorScheme.onSurface.copy(0.4f)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { visualize() }),
            trailingIcon = {
                if (input.isNotBlank()) {
                    IconButton(onClick = { input = ""; cidResult = null; error = null }) {
                        Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp))
                    }
                }
            }
        )

        Text("EXAMPLES", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            examples.forEach { (name, smiles) ->
                FilterChip(
                    selected = input == smiles,
                    onClick = { input = smiles; cidResult = null; error = null },
                    label = { Text(name, style = MaterialTheme.typography.labelMedium) }
                )
            }
        }

        Button(
            onClick = { visualize() },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            enabled = input.isNotBlank() && !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                Spacer(Modifier.width(8.dp))
            }
            Text(if (isLoading) "Looking up..." else "Visualize")
        }

        if (error != null) {
            Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(0.4f))) {
                Text(error!!, modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }

        if (cidResult != null) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.primary.copy(0.1f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Science, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(compoundName?.replaceFirstChar { it.uppercase() } ?: "CID $cidResult", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text("PubChem CID: $cidResult", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(0.5f), fontFamily = FontFamily.Monospace)
                    }
                }
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.outline.copy(0.12f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(4.dp)) {
                                listOf("2D Structure", "3D Model").forEachIndexed { idx, label ->
                                    Surface(
                                        onClick = { activeTab = idx },
                                        shape = RoundedCornerShape(50),
                                        color = if (activeTab == idx) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            label,
                                            modifier = Modifier.padding(vertical = 8.dp),
                                            color = if (activeTab == idx) Color.White else MaterialTheme.colorScheme.onSurface.copy(0.55f),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Box(
                        modifier = Modifier.fillMaxWidth().height(300.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        when (activeTab) {
                            0 -> AsyncImage(
                                model = "https://pubchem.ncbi.nlm.nih.gov/rest/pug/compound/cid/$cidResult/PNG?image_size=large",
                                contentDescription = "2D Structure",
                                modifier = Modifier.fillMaxSize().padding(12.dp),
                                contentScale = ContentScale.Fit
                            )
                            1 -> if (sdfData != null) {
                                Viewer3D(cid = cidResult ?: -1L, sdfData = sdfData!!, isDark = isDark)
                                if (sdfSource == SdfSource.GENERATED) {
                                    Surface(
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(10.dp),
                                        shape = RoundedCornerShape(999.dp),
                                        color = MaterialTheme.colorScheme.surface.copy(0.92f),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(0.28f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.AutoFixHigh,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                "Generated estimate",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.VisibilityOff, null, tint = MaterialTheme.colorScheme.onSurface.copy(0.3f), modifier = Modifier.size(28.dp))
                                    Text("3D model not available", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
                                    sdfMessage?.let { message ->
                                        Text(
                                            message,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(0.36f),
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.widthIn(max = 260.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

    }
}

// TOOL 5 : REACTION BALANCER
private data class BalancerResult(
    val reactants: List<Pair<String, Int>> = emptyList(),
    val products:  List<Pair<String, Int>> = emptyList(),
    val error: String? = null
)

private fun balanceReaction(equation: String): BalancerResult {
    val result: BalancedReactionResult = balanceChemicalReaction(equation)
    if (result.error != null) return BalancerResult(error = result.error.message)
    return BalancerResult(
        reactants = result.reactants.map { it.formula to it.coefficient },
        products = result.products.map { it.formula to it.coefficient }
    )
}

private fun reactionToDisplay(raw: String): String =
    raw.replace("->", "→")
        .map { subscriptMap[it] ?: it }
        .joinToString("")

private fun fieldValueAtEnd(text: String): TextFieldValue =
    TextFieldValue(text = text, selection = TextRange(text.length))

private fun insertIntoField(current: TextFieldValue, insert: String): TextFieldValue {
    val start = current.selection.start.coerceIn(0, current.text.length)
    val end = current.selection.end.coerceIn(0, current.text.length)
    val from = minOf(start, end)
    val to = maxOf(start, end)
    val newText = current.text.replaceRange(from, to, insert)
    val cursor = from + insert.length
    return TextFieldValue(text = newText, selection = TextRange(cursor))
}

private fun normalizeEquationField(value: TextFieldValue): TextFieldValue {
    val raw = value.text
    val normalized = raw.replace("->", "→")
    if (raw == normalized) return value

    val start = raw
        .substring(0, value.selection.start.coerceIn(0, raw.length))
        .replace("->", "→")
        .length
    val end = raw
        .substring(0, value.selection.end.coerceIn(0, raw.length))
        .replace("->", "→")
        .length
    return value.copy(text = normalized, selection = TextRange(start, end))
}

private fun swapEquationSides(input: String): String {
    val normalized = input.replace("→", "->")
    val parts = normalized.split("->")
    if (parts.size < 2) return input
    val left = parts.first().trim()
    val right = parts.drop(1).joinToString("->").trim()
    if (left.isBlank() || right.isBlank()) return input
    return "$right → $left"
}

@Composable
fun ReactionBalancer() {
    var input by remember { mutableStateOf(TextFieldValue("")) }
    var result by remember { mutableStateOf<BalancerResult?>(null) }
    val focusManager = LocalFocusManager.current

    val examples = listOf(
        "H2 + O2 -> H2O",
        "Fe + O2 -> Fe2O3",
        "C3H8 + O2 -> CO2 + H2O",
        "Al + HCl -> AlCl3 + H2",
        "KMnO4 + HCl -> KCl + MnCl2 + H2O + Cl2"
    )

    var showInfo by remember { mutableStateOf(false) }
    if (showInfo) {
        InfoDialog(
            title = "Reaction Balancer",
            entries = listOf(
                "How to enter equations" to "Type reactants on the left and products on the right, separated by '->'. Separate compounds with '+'. Example: H2 + O2 -> H2O",
                "Coefficients" to "Do not enter coefficients. The app determines them. H2 + O2 -> H2O, not 2H2 + O2 -> 2H2O.",
                "How it works" to "The balancer builds a matrix of element counts and solves the system using Gaussian elimination with exact rational arithmetic to find integer coefficients.",
                "Supported formulas" to "Standard molecular formulas with parentheses are supported, e.g. Ca(OH)2, Al2(SO4)3.",
                "Limitations" to "Equations that cannot be balanced by integer stoichiometry (e.g. some redox reactions requiring half-reaction method) may not solve correctly.",
                "Verification" to "The element count table below the result shows that atoms are conserved. You can verify the balancing manually."
            ),
            onDismiss = { showInfo = false }
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Reaction Balancer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            IconButton(onClick = { showInfo = true }, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.onSurface.copy(0.35f), modifier = Modifier.size(16.dp))
            }
        }

        OutlinedTextField(
            value = input,
            onValueChange = { new ->
                val normalized = normalizeEquationField(new)
                val textChanged = normalized.text != input.text
                input = normalized
                if (textChanged) result = null
            },
            label = { Text("Chemical Equation") },
            placeholder = { Text("H2 + O2 -> H2O", color = MaterialTheme.colorScheme.onSurface.copy(0.4f)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            singleLine = true,
            visualTransformation = FormulaSubscriptTransformation,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                focusManager.clearFocus()
                result = balanceReaction(input.text.replace("→", "->"))
            }),
            trailingIcon = {
                if (input.text.isNotBlank()) {
                    IconButton(onClick = { input = TextFieldValue(""); result = null }) {
                        Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp))
                    }
                }
            }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Insert:",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(0.45f)
            )
            Row(
                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("+" to " + ", "→" to " → ", "(" to "(", ")" to ")").forEach { (label, insert) ->
                    Surface(
                        onClick = { input = insertIntoField(input, insert); result = null },
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(0.3f))
                    ) {
                        Text(
                            label,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = { input = fieldValueAtEnd(swapEquationSides(input.text)); result = null },
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(0.3f))
                ) {
                    Icon(
                        Icons.Default.SwapHoriz,
                        contentDescription = "Swap sides",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp).size(14.dp)
                    )
                }
            }
        }

        Button(
            onClick = { focusManager.clearFocus(); result = balanceReaction(input.text.replace("→", "->")) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            enabled = input.text.isNotBlank()
        ) {
            Icon(Icons.Default.SwapHoriz, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Balance")
        }

        result?.let { res ->
            if (res.error != null) {
                Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(0.4f))) {
                    Text(res.error, modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            } else {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {

                        Text("BALANCED EQUATION", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.45f))

                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            res.reactants.forEachIndexed { i, (formula, coeff) ->
                                if (i > 0) {
                                    Text("+", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(0.5f), modifier = Modifier.align(Alignment.CenterVertically))
                                }
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(0.08f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(0.25f))
                                ) {
                                    Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        if (coeff > 1) {
                                            Text("$coeff", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                                        }
                                        Text(toSubscriptFormula(formula), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
                                    }
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.outline.copy(0.1f),
                                modifier = Modifier.align(Alignment.CenterVertically)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurface.copy(0.6f), modifier = Modifier.padding(6.dp).size(16.dp))
                            }

                            res.products.forEachIndexed { i, (formula, coeff) ->
                                if (i > 0) {
                                    Text("+", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(0.5f), modifier = Modifier.align(Alignment.CenterVertically))
                                }
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.secondary.copy(0.08f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(0.25f))
                                ) {
                                    Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        if (coeff > 1) {
                                            Text("$coeff", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.secondary)
                                        }
                                        Text(toSubscriptFormula(formula), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.15f))

                        Text("ATOM COUNT VERIFICATION", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.45f))

                        val allElements = (res.reactants + res.products).flatMap { (f, _) ->
                            try { parseFormulaForCalc(f).keys } catch (e: Exception) { emptySet() }
                        }.distinct().sorted()

                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text("Element", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
                            Text("Reactants", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary.copy(0.7f), textAlign = TextAlign.Center)
                            Text("Products", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary.copy(0.7f), textAlign = TextAlign.Center)
                            Text("✓", modifier = Modifier.width(24.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(0.4f), textAlign = TextAlign.Center)
                        }

                        allElements.forEach { el ->
                            val rCount = res.reactants.sumOf { (f, c) ->
                                try { (parseFormulaForCalc(f)[el] ?: 0) * c } catch (e: Exception) { 0 }
                            }
                            val pCount = res.products.sumOf { (f, c) ->
                                try { (parseFormulaForCalc(f)[el] ?: 0) * c } catch (e: Exception) { 0 }
                            }
                            val balanced = rCount == pCount
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box(
                                        modifier = Modifier.size(22.dp).background(MaterialTheme.colorScheme.primary.copy(0.1f), RoundedCornerShape(5.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(el, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 10.sp)
                                    }
                                }
                                Text("$rCount", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center)
                                Text("$pCount", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center)
                                Icon(
                                    if (balanced) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                    null,
                                    tint = if (balanced) Color(0xFF22C55E) else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.width(24.dp).size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Text("EXAMPLES", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            examples.forEach { ex ->
                Surface(
                    onClick = { input = fieldValueAtEnd(ex.replace("->", "→")); result = null },
                    shape = RoundedCornerShape(10.dp),
                    color = if (input.text.replace("→", "->") == ex) MaterialTheme.colorScheme.primary.copy(0.1f) else MaterialTheme.colorScheme.surfaceVariant.copy(0.5f),
                    border = if (input.text.replace("→", "->") == ex) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(0.4f)) else null
                ) {
                    Text(
                        reactionToDisplay(ex),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = if (input.text.replace("→", "->") == ex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(0.7f)
                    )
                }
            }
        }
    }
}

// TOOL 13 : Compare Compounds

private data class CompareCompound(
    val query: String,
    val cid: Long,
    val name: String,
    val formula: String,
    val molecularWeight: String,
    val iupacName: String,
    val smiles: String,
    val connectivitySmiles: String,
    val inchiKey: String,
    val inchi: String,
    val charge: Int?,
    val covalentUnitCount: Int?,
    val atomCount: Int?,
    val bondCount: Int?,
    val casNumber: String?,
    val description: String?,
    val descriptionSource: DescSource,
    val ghsData: GhsData?
)

private suspend fun fetchCompareCompound(
    query: String,
    descriptionSource: DescSource,
    aiProvider: AiProvider,
    aiKey: String?,
    selectedAiModel: String
): CompareCompound = withContext(Dispatchers.IO) {
    val cleanQuery = query.trim()
    val cid = cleanQuery.toLongOrNull()?.takeIf { it > 0 }
        ?: ApiClient.pubChem.getCid(cleanQuery).identifierList?.cid?.firstOrNull()
        ?: throw NoSuchElementException("No compound found for $cleanQuery")

    val props = runCatching { ApiClient.pubChem.getProperties(cid).propertyTable?.properties?.firstOrNull() }
        .getOrNull()
        ?: CompoundProperty(cid = cid)
    val name = props.title?.takeIf { it.isNotBlank() }
        ?: props.iupacName?.takeIf { it.isNotBlank() }
        ?: cleanQuery.replaceFirstChar { it.uppercase() }
    val formula = formatConventionalFormula(props.molecularFormula.orEmpty())
    val description = runCatching {
        fetchCompareDescription(
            cid = cid,
            name = name,
            formula = formula,
            source = descriptionSource,
            aiProvider = aiProvider,
            aiKey = aiKey,
            selectedAiModel = selectedAiModel
        )
    }.getOrNull()
    val synonyms = runCatching {
        ApiClient.pubChem.getSynonyms(cid).informationList?.information?.firstOrNull()?.synonym.orEmpty()
    }.getOrDefault(emptyList())
    val safety = runCatching {
        parseCompareGhsData(ApiClient.pubChemView.getSection(cid, "GHS Classification"))
    }.getOrNull()
    val structureCounts = runCatching {
        extractCompareStructureCounts(ApiClient.pubChem.getRecord(cid))
    }.getOrDefault(CompareStructureCounts())
    val casRegex = Regex("""^\d{1,7}-\d{2}-\d$""")

    CompareCompound(
        query = cleanQuery,
        cid = cid,
        name = name,
        formula = formula,
        molecularWeight = props.molecularWeight.orEmpty(),
        iupacName = props.iupacName.orEmpty(),
        smiles = props.smiles.orEmpty(),
        connectivitySmiles = props.connectivitySmiles.orEmpty(),
        inchiKey = props.inchiKey.orEmpty(),
        inchi = props.inchi.orEmpty(),
        charge = props.charge,
        covalentUnitCount = props.covalentUnitCount,
        atomCount = structureCounts.atomCount,
        bondCount = structureCounts.bondCount,
        casNumber = synonyms.firstOrNull { casRegex.matches(it) },
        description = description,
        descriptionSource = descriptionSource,
        ghsData = safety
    )
}

private data class CompareStructureCounts(
    val atomCount: Int? = null,
    val bondCount: Int? = null
)

private fun extractCompareStructureCounts(record: JsonObject?): CompareStructureCounts {
    val compound = runCatching {
        record
            ?.getAsJsonArray("PC_Compounds")
            ?.firstOrNull()
            ?.asJsonObject
    }.getOrNull() ?: return CompareStructureCounts()

    val atomCount = runCatching {
        compound
            .getAsJsonObject("atoms")
            ?.getAsJsonArray("aid")
            ?.size()
    }.getOrNull()

    val bondCount = runCatching {
        compound
            .getAsJsonObject("bonds")
            ?.getAsJsonArray("aid1")
            ?.size()
    }.getOrNull()

    return CompareStructureCounts(atomCount = atomCount, bondCount = bondCount)
}

private suspend fun fetchCompareDescription(
    cid: Long,
    name: String,
    formula: String,
    source: DescSource,
    aiProvider: AiProvider,
    aiKey: String?,
    selectedAiModel: String
): String? = when (source) {
    DescSource.PUBCHEM -> fetchComparePubChemDescription(cid)
    DescSource.WIKI -> fetchCompareWikiDescription(name)
    DescSource.AI -> fetchCompareAiDescription(name, formula, aiProvider, aiKey, selectedAiModel)
}

private suspend fun fetchComparePubChemDescription(cid: Long): String? =
    ApiClient.pubChem.getDescription(cid)
        .informationList
        ?.information
        ?.find { it.description != null }
        ?.description
        ?.let { el ->
            when {
                el.isJsonPrimitive -> el.asString
                el.isJsonArray -> el.asJsonArray.mapNotNull {
                    runCatching { it.asString }.getOrNull()
                }.joinToString("\n\n")
                else -> null
            }
        }

private suspend fun fetchCompareWikiDescription(name: String): String? {
    val cleanName = name.trim()
    if (cleanName.isBlank()) return null
    val titleCased = cleanName.split(" ")
        .joinToString(" ") { word -> word.lowercase().replaceFirstChar { it.uppercase() } }
    return runCatching { ApiClient.wiki.getSummary(titleCased).extract }.getOrNull()
        ?: runCatching { ApiClient.wiki.getSummary(cleanName.lowercase().replaceFirstChar { it.uppercase() }).extract }.getOrNull()
}

private suspend fun fetchCompareAiDescription(
    name: String,
    formula: String,
    provider: AiProvider,
    key: String?,
    selectedModel: String
): String? {
    if (key.isNullOrBlank()) return "No ${provider.shortName} API key set."
    val prompt = buildString {
        append("Write a concise 2 sentence chemistry description for $name")
        if (formula.isNotBlank()) append(" ($formula)")
        append(". Mention identity and common uses. Keep it clear.")
    }
    return if (provider == AiProvider.GEMINI) {
        val request = GeminiRequest(contents = listOf(GeminiContent(parts = listOf(GeminiPart(prompt)))))
        ApiClient.gemini.generateContent(selectedModel, key, request)
            .candidates
            ?.firstOrNull()
            ?.content
            ?.parts
            ?.firstOrNull()
            ?.text
    } else {
        val api = when (provider) {
            AiProvider.GROQ -> ApiClient.groq
            AiProvider.OPENAI -> ApiClient.openAi
            AiProvider.OPENROUTER -> ApiClient.openRouter
            AiProvider.MISTRAL -> ApiClient.mistral
            AiProvider.GEMINI -> error("Gemini uses a separate API")
        }
        val request = GroqRequest(
            model = selectedModel,
            messages = listOf(GroqMessage(role = "user", content = prompt))
        )
        api.generateContent("Bearer $key", request)
            .choices
            ?.firstOrNull()
            ?.message
            ?.content
    }
}

private fun parseCompareGhsData(json: JsonObject): GhsData? {
    val sections = json.getAsJsonObject("Record")?.getAsJsonArray("Section") ?: return null
    val flattened = mutableListOf<JsonObject>()

    fun flatten(sectionArray: com.google.gson.JsonArray) {
        sectionArray.forEach { element ->
            val section = runCatching { element.asJsonObject }.getOrNull() ?: return@forEach
            flattened.add(section)
            section.getAsJsonArray("Section")?.let(::flatten)
        }
    }

    flatten(sections)

    var signalWord: String? = null
    val hazards = mutableListOf<String>()
    val pictograms = mutableListOf<String>()

    flattened.forEach { section ->
        val heading = section.get("TOCHeading")?.asString.orEmpty()
        val information = section.getAsJsonArray("Information") ?: return@forEach
        information.forEach { infoElement ->
            val info = runCatching { infoElement.asJsonObject }.getOrNull() ?: return@forEach
            val name = info.get("Name")?.asString.orEmpty()
            val strings = info.getAsJsonObject("Value")
                ?.getAsJsonArray("StringWithMarkup")
                ?: return@forEach

            when {
                heading == "GHS Classification" && name.contains("Signal", ignoreCase = true) -> {
                    signalWord = strings.firstOrNull()?.asJsonObject?.get("String")?.asString
                }
                heading == "GHS Classification" && name.contains("Hazard Statement", ignoreCase = true) -> {
                    strings.mapNotNullTo(hazards) {
                        runCatching { it.asJsonObject.get("String")?.asString }.getOrNull()
                    }
                }
                heading == "Pictogram(s)" || name.contains("Pictogram", ignoreCase = true) -> {
                    strings.forEach { stringElement ->
                        val markup = runCatching { stringElement.asJsonObject.getAsJsonArray("Markup") }.getOrNull()
                            ?: return@forEach
                        markup.mapNotNullTo(pictograms) { markupElement ->
                            val url = runCatching { markupElement.asJsonObject.get("URL")?.asString }.getOrNull()
                            url?.let { Regex("GHS\\d{2}").find(it)?.value }
                        }
                    }
                }
            }
        }
    }

    val cleanHazards = hazards
        .filter { it.isNotBlank() && !it.equals("Not Classified", ignoreCase = true) }
        .distinct()
    if (signalWord == null && cleanHazards.isEmpty() && pictograms.isEmpty()) return null
    return GhsData(signalWord, cleanHazards.take(3), pictograms.distinct())
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CompareCompoundsTool(
    defaultDescSource: DescSource,
    aiProvider: AiProvider,
    getAiKey: (AiProvider) -> String?,
    getSelectedAiModel: (AiProvider) -> String,
    onSearchCompound: (String) -> Unit = {}
) {
    var compoundFields by remember {
        mutableStateOf(
            listOf(
                TextFieldValue("caffeine"),
                TextFieldValue("theobromine")
            )
        )
    }
    var results by remember { mutableStateOf<List<CompareCompound>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val compact = LocalCompactMode.current

    fun runCompare(fields: List<TextFieldValue> = compoundFields) {
        val queries = fields
            .flatMap { parseCompareCompoundInputs(it.text) }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
        if (queries.size < 2) {
            error = "Enter at least two compounds."
            return
        }
        isLoading = true
        error = null
        results = emptyList()
        focusManager.clearFocus()
        scope.launch {
            val loaded = mutableListOf<CompareCompound>()
            val failures = mutableListOf<String>()
            queries.take(MAX_COMPARE_COMPOUNDS).forEach { query ->
                runCatching {
                    fetchCompareCompound(
                        query = query,
                        descriptionSource = defaultDescSource,
                        aiProvider = aiProvider,
                        aiKey = getAiKey(aiProvider),
                        selectedAiModel = getSelectedAiModel(aiProvider)
                    )
                }
                    .onSuccess { loaded.add(it) }
                    .onFailure { failures.add(query) }
            }
            results = loaded
            error = when {
                loaded.size < 2 && failures.isNotEmpty() -> "Could not load: ${failures.joinToString(", ")}"
                failures.isNotEmpty() -> "Skipped: ${failures.joinToString(", ")}"
                else -> null
            }
            isLoading = false
        }
    }

    fun setFieldsFromExample(example: String) {
        val values = parseCompareCompoundInputs(example)
            .take(MAX_COMPARE_COMPOUNDS)
            .map { fieldValueAtEnd(it) }
        compoundFields = values.ifEmpty { compoundFields }
        runCompare(values)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Compare Compounds", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            IconButton(onClick = { runCompare() }, enabled = !isLoading, modifier = Modifier.size(34.dp)) {
                Icon(Icons.AutoMirrored.Filled.CompareArrows, contentDescription = "Compare", tint = MaterialTheme.colorScheme.primary)
            }
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            compoundFields.forEachIndexed { index, field ->
                OutlinedTextField(
                    value = field,
                    onValueChange = { value ->
                        compoundFields = compoundFields.toMutableList().also { it[index] = value }
                    },
                    label = { Text("Compound ${index + 1}") },
                    placeholder = { Text("compound ${index + 1}") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Science, null) },
                    trailingIcon = {
                        if (compoundFields.size > MIN_COMPARE_COMPOUNDS) {
                            IconButton(
                                onClick = {
                                    compoundFields = compoundFields.toMutableList().also { it.removeAt(index) }
                                    results = emptyList()
                                }
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Remove compound")
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onDone = { runCompare() }),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            FilledTonalIconButton(
                onClick = {
                    if (compoundFields.size < MAX_COMPARE_COMPOUNDS) {
                        compoundFields = compoundFields + TextFieldValue("")
                    }
                },
                enabled = compoundFields.size < MAX_COMPARE_COMPOUNDS,
                modifier = Modifier.size(if (compact) 38.dp else 42.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add compound")
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                "caffeine vs theobromine",
                "ethanol vs methanol",
                "glucose vs fructose"
            ).forEach { example ->
                AssistChip(
                    onClick = { setFieldsFromExample(example) },
                    label = { Text(example, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = {
                        Icon(Icons.Default.Bolt, null, modifier = Modifier.size(14.dp))
                    }
                )
            }
        }

        Button(
            onClick = { runCompare() },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Icon(Icons.AutoMirrored.Filled.CompareArrows, null, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(if (isLoading) "Comparing..." else "Compare")
        }

        error?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        if (results.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                results.forEach { compound ->
                    CompareCompoundCard(compound = compound, onOpen = { onSearchCompound(compound.cid.toString()) })
                }
            }
            CompareRows(results)
        }
    }
}

private const val MIN_COMPARE_COMPOUNDS = 2
private const val MAX_COMPARE_COMPOUNDS = 6

@Composable
private fun CompareCompoundCard(
    compound: CompareCompound,
    onOpen: () -> Unit
) {
    val compact = LocalCompactMode.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(0.14f))
    ) {
        Row(
            modifier = Modifier.padding(if (compact) 10.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(0.55f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(0.10f))
            ) {
                AsyncImage(
                    model = "https://pubchem.ncbi.nlm.nih.gov/rest/pug/compound/cid/${compound.cid}/PNG?record_type=2d&image_size=small",
                    contentDescription = "Structure of ${compound.name}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(if (compact) 64.dp else 78.dp).padding(4.dp)
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    compound.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (compound.formula.isNotBlank()) {
                    Text(
                        toSubscriptFormula(compound.formula),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    "CID ${compound.cid}${compound.molecularWeight.takeIf { it.isNotBlank() }?.let { " · $it g/mol" } ?: ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.48f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onOpen) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open in Search")
            }
        }
    }
}

@Composable
private fun CompareRows(results: List<CompareCompound>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "COMPARISON",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(0.45f)
        )
        CompareSectionLabel("Core")
        CompareInfoRow("Formula", results) { toSubscriptFormula(it.formula.ifBlank { "—" }) }
        CompareInfoRow("Molar mass", results) {
            it.molecularWeight.takeIf { value -> value.isNotBlank() }?.let { value -> "$value g/mol" } ?: "—"
        }
        CompareInfoRow("IUPAC name", results, monospace = true) { it.iupacName.ifBlank { "—" } }

        CompareSectionLabel("Identifiers")
        CompareInfoRow("CID", results, monospace = true) { it.cid.toString() }
        CompareInfoRow("CAS", results, monospace = true) { it.casNumber ?: "—" }
        CompareInfoRow("SMILES", results, monospace = true) { it.smiles.ifBlank { "—" } }
        CompareInfoRow("Connectivity SMILES", results, monospace = true) { it.connectivitySmiles.ifBlank { "—" } }
        CompareInfoRow("InChIKey", results, monospace = true) { it.inchiKey.ifBlank { "—" } }
        CompareInfoRow("InChI", results, monospace = true) { it.inchi.ifBlank { "—" } }

        CompareSectionLabel("Structure")
        CompareInfoRow("Charge", results) { it.charge?.toString() ?: "—" }
        CompareInfoRow("Covalent units", results) { it.covalentUnitCount?.toString() ?: "—" }
        CompareInfoRow("Atom count", results) { it.atomCount?.toString() ?: "—" }
        CompareInfoRow("Bond count", results) { it.bondCount?.toString() ?: "—" }

        CompareSectionLabel("Description & Safety")
        CompareInfoRow("Safety", results) {
            it.ghsData?.signalWord ?: it.ghsData?.hazardStatements?.firstOrNull() ?: "—"
        }
        val sourceLabel = results.firstOrNull()?.descriptionSource?.compareLabel().orEmpty()
        CompareDescriptionRow(
            label = "Description${if (sourceLabel.isBlank()) "" else " ($sourceLabel)"}",
            compounds = results
        )
    }
}

@Composable
private fun CompareSectionLabel(label: String) {
    Text(
        label.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.7.sp,
        color = MaterialTheme.colorScheme.primary.copy(0.72f),
        modifier = Modifier.padding(top = 6.dp)
    )
}

@Composable
private fun CompareInfoRow(
    label: String,
    compounds: List<CompareCompound>,
    monospace: Boolean = false,
    valueFor: (CompareCompound) -> String
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface.copy(0.62f)
        )
        compounds.forEach { compound ->
            ExpandableCompareValueCard(
                compoundName = compound.name,
                value = valueFor(compound),
                monospace = monospace
            )
        }
    }
}

@Composable
private fun CompareDescriptionRow(
    label: String,
    compounds: List<CompareCompound>
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface.copy(0.62f)
        )
        compounds.forEach { compound ->
            ExpandableCompareValueCard(
                compoundName = compound.name,
                value = compound.description?.trim().orEmpty().ifBlank { "—" },
                maxPreviewChars = 180
            )
        }
    }
}

@Composable
private fun ExpandableCompareValueCard(
    compoundName: String,
    value: String,
    monospace: Boolean = false,
    maxPreviewChars: Int = 180
) {
    val fullValue = remember(compoundName, value) {
        formatCompoundComparisonValue(compoundName, value)
    }
    val previewValue = remember(fullValue, maxPreviewChars) {
        previewComparisonCellText(fullValue, maxPreviewChars)
    }
    val canExpand = fullValue != previewValue
    var expanded by remember(fullValue) { mutableStateOf(false) }
    val displayedValue = if (expanded || !canExpand) fullValue else previewValue

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(0.38f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(0.10f)),
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (canExpand) {
                    Modifier.clickable { expanded = !expanded }
                } else {
                    Modifier
                }
            )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                displayedValue,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
                color = MaterialTheme.colorScheme.onSurface.copy(0.78f)
            )
            if (canExpand) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        if (expanded) "Tap to collapse" else "Tap to expand",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}

private fun DescSource.compareLabel(): String = when (this) {
    DescSource.PUBCHEM -> "PubChem"
    DescSource.WIKI -> "Wikipedia"
    DescSource.AI -> "AI"
}

// TOOL 6 : Isomer Finder

@Composable
fun IsomerFinderTool(
    vm: ChemViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onNavigateToSearch: () -> Unit = {}
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    var showInfo by remember { mutableStateOf(false) }
    if (showInfo) {
        InfoDialog(
            title = "Isomer Finder",
            entries = listOf(
                "How to use" to "Enter any molecular formula (e.g. C₆H₆ or C₆H₁₂O₆). The app instantly shows up to 20 known isomers with the exact same formula.",
                "What are isomers?" to "Isomers are different compounds that have the same molecular formula but different atom connectivity or 3D arrangement.",
                "How it works" to "The app queries PubChem’s official API in real-time.",
                "Supported formulas" to "Standard molecular formulas with parentheses are fully supported, e.g. Ca(OH)₂, C₆H₅OH, Al₂(SO₄)₃, or complex ones like C₁₇H₃₅COOH.",
                "What you get" to "A list of all matching PubChem compounds with names, 2D structures, CIDs, and quick links to full details.",
                "Limitations" to "Only experimentally known compounds from PubChem are shown. Very rare or brand-new compounds may not appear yet."
            ),
            onDismiss = { showInfo = false }
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Isomer Finder", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                IconButton(onClick = { showInfo = true }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.onSurface.copy(0.35f), modifier = Modifier.size(16.dp))
                }
            }
            Text(
                "Enter a molecular formula to find up to 20 structural isomers from PubChem. " +
                        "Tap any result to load it in the Search tab.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }

        IsomerSearchBar(
            query = state.isomerQuery,
            onQueryChange = { vm.onIsomerQueryChange(it) },
            onSearch = {
                focusManager.clearFocus()
                vm.searchIsomers()
            },
            onClear = { vm.onIsomerQueryChange("") }
        )

        if (state.isLoadingIsomers) {
            IsomerLoadingState()
        }

        state.isomerError?.let { IsomerErrorState(it) }

        if (state.isomers.isNotEmpty()) {
            IsomerResultsHeader(
                formula = state.isomerQuery.trim(),
                count = state.isomers.size
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.isomers.forEach { isomer ->
                    IsomerCard(
                        isomer = isomer,
                        onClick = {
                            focusManager.clearFocus()
                            onNavigateToSearch()
                            vm.searchByCid(isomer.cid)
                        }
                    )
                }
            }
        }
    }
}

private data class SolveResult(val value: Double?, val error: String? = null)

private enum class DilutionSolve(val label: String, val unit: String) {
    C1("C₁ (stock)", "M"),
    V1("V₁ (stock)", "mL"),
    C2("C₂ (final)", "M"),
    V2("V₂ (final)", "mL")
}

private fun solveDilution(
    solveFor: DilutionSolve,
    c1: String,
    v1: String,
    c2: String,
    v2: String
): SolveResult {
    val c1Val = parsePositiveNumber(c1)
    val v1Val = parsePositiveNumber(v1)
    val c2Val = parsePositiveNumber(c2)
    val v2Val = parsePositiveNumber(v2)
    return when (solveFor) {
        DilutionSolve.C1 -> {
            if (v1Val == null) SolveResult(null, "Enter V₁")
            else if (c2Val == null) SolveResult(null, "Enter C₂")
            else if (v2Val == null) SolveResult(null, "Enter V₂")
            else SolveResult((c2Val * v2Val) / v1Val)
        }
        DilutionSolve.V1 -> {
            if (c1Val == null) SolveResult(null, "Enter C₁")
            else if (c2Val == null) SolveResult(null, "Enter C₂")
            else if (v2Val == null) SolveResult(null, "Enter V₂")
            else SolveResult((c2Val * v2Val) / c1Val)
        }
        DilutionSolve.C2 -> {
            if (c1Val == null) SolveResult(null, "Enter C₁")
            else if (v1Val == null) SolveResult(null, "Enter V₁")
            else if (v2Val == null) SolveResult(null, "Enter V₂")
            else SolveResult((c1Val * v1Val) / v2Val)
        }
        DilutionSolve.V2 -> {
            if (c1Val == null) SolveResult(null, "Enter C₁")
            else if (v1Val == null) SolveResult(null, "Enter V₁")
            else if (c2Val == null) SolveResult(null, "Enter C₂")
            else SolveResult((c1Val * v1Val) / c2Val)
        }
    }
}

private const val GAS_R = 0.082057

private enum class GasSolve(val label: String, val unit: String) {
    PRESSURE("Pressure", "atm"),
    VOLUME("Volume", "L"),
    MOLES("Moles", "mol"),
    TEMPERATURE("Temperature", "K")
}

private fun solveGasLaw(
    solveFor: GasSolve,
    pressure: String,
    volume: String,
    moles: String,
    temperature: String
): SolveResult {
    val pVal = parsePositiveNumber(pressure)
    val vVal = parsePositiveNumber(volume)
    val nVal = parsePositiveNumber(moles)
    val tVal = parsePositiveNumber(temperature)
    return when (solveFor) {
        GasSolve.PRESSURE -> {
            if (nVal == null) SolveResult(null, "Enter moles")
            else if (tVal == null) SolveResult(null, "Enter temperature")
            else if (vVal == null) SolveResult(null, "Enter volume")
            else SolveResult((nVal * GAS_R * tVal) / vVal)
        }
        GasSolve.VOLUME -> {
            if (nVal == null) SolveResult(null, "Enter moles")
            else if (tVal == null) SolveResult(null, "Enter temperature")
            else if (pVal == null) SolveResult(null, "Enter pressure")
            else SolveResult((nVal * GAS_R * tVal) / pVal)
        }
        GasSolve.MOLES -> {
            if (pVal == null) SolveResult(null, "Enter pressure")
            else if (vVal == null) SolveResult(null, "Enter volume")
            else if (tVal == null) SolveResult(null, "Enter temperature")
            else SolveResult((pVal * vVal) / (GAS_R * tVal))
        }
        GasSolve.TEMPERATURE -> {
            if (pVal == null) SolveResult(null, "Enter pressure")
            else if (vVal == null) SolveResult(null, "Enter volume")
            else if (nVal == null) SolveResult(null, "Enter moles")
            else SolveResult((pVal * vVal) / (GAS_R * nVal))
        }
    }
}

private fun renderLatex(latex: String): AnnotatedString {
    val source = latex.trim()
        .removePrefix("$")
        .removeSuffix("$")
        .replace("\\,", " ")

    return buildAnnotatedString {
        var i = 0
        while (i < source.length) {
            when {
                source[i] == '\\' -> {
                    var j = i + 1
                    while (j < source.length && source[j].isLetter()) j++
                    val command = source.substring(i + 1, j)
                    when (command) {
                        "cdot" -> append("·")
                        "times" -> append("×")
                        "left", "right" -> Unit
                        else -> append(command)
                    }
                    i = if (j == i + 1 && i + 1 < source.length) {
                        append(source[i + 1].toString())
                        i + 2
                    } else {
                        j
                    }
                }
                source[i] == '_' || source[i] == '^' -> {
                    val isSubscript = source[i] == '_'
                    i++
                    if (i >= source.length) break
                    val token = if (source[i] == '{') {
                        var depth = 1
                        val start = i + 1
                        i++
                        while (i < source.length && depth > 0) {
                            when (source[i]) {
                                '{' -> depth++
                                '}' -> depth--
                            }
                            i++
                        }
                        source.substring(start, (i - 1).coerceAtLeast(start))
                    } else {
                        source[i++].toString()
                    }
                    withStyle(
                        SpanStyle(
                            baselineShift = if (isSubscript) BaselineShift.Subscript else BaselineShift.Superscript,
                            fontSize = 0.82.em
                        )
                    ) {
                        append(token)
                    }
                }
                else -> {
                    append(source[i])
                    i++
                }
            }
        }
    }
}

@Composable
private fun FormulaExplanationCard(
    latexFormula: String,
    explanation: String
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.4f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Explanation for Formula",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(0.6f)
            )
            Text(
                text = remember(latexFormula) { renderLatex(latexFormula) },
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace
            )
            Text(
                explanation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(0.7f)
            )
        }
    }
}

// TOOL 14 : pH / pOH CALCULATOR
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PhPohCalculatorTool() {
    var inputType by remember { mutableStateOf(PhPohInputType.PH) }
    var input by remember { mutableStateOf("7") }
    var showInfo by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val result = remember(input, inputType) {
        runCatching { calculatePhPoh(input, inputType) }
    }
    val hasInput = input.trim().isNotEmpty()

    if (showInfo) {
        InfoDialog(
            title = "pH / pOH Calculator",
            entries = listOf(
                "What it solves" to "Enter pH, pOH, [H+], or [OH-] to calculate the other three values.",
                "Concentration units" to "[H+] and [OH-] are mol/L.",
                "Assumption" to "Uses pH + pOH = 14 for water at 25 C."
            ),
            onDismiss = { showInfo = false }
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("pH / pOH Calculator", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            IconButton(onClick = { showInfo = true }, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.onSurface.copy(0.35f), modifier = Modifier.size(16.dp))
            }
        }

        Text(
            "Choose the value you know, then enter it below.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(0.58f)
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PhPohInputType.entries.forEach { type ->
                FilterChip(
                    selected = inputType == type,
                    onClick = {
                        inputType = type
                        focusManager.clearFocus()
                    },
                    label = { Text(type.label) },
                    leadingIcon = if (inputType == type) {
                        {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    } else null
                )
            }
        }

        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("Known ${inputType.symbol}") },
            placeholder = {
                Text(
                    if (inputType == PhPohInputType.HYDROGEN || inputType == PhPohInputType.HYDROXIDE) "e.g. 1e-7" else "e.g. 7",
                    color = MaterialTheme.colorScheme.onSurface.copy(0.4f)
                )
            },
            leadingIcon = { Icon(Icons.Default.WaterDrop, contentDescription = null) },
            trailingIcon = {
                if (input.isNotBlank()) {
                    IconButton(onClick = { input = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear")
                    }
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                Triple("Neutral water", PhPohInputType.PH, "7"),
                Triple("Acidic pH", PhPohInputType.PH, "3"),
                Triple("Basic pOH", PhPohInputType.POH, "3"),
                Triple("[H+] example", PhPohInputType.HYDROGEN, "1e-4")
            ).forEach { (label, type, value) ->
                AssistChip(
                    onClick = {
                        inputType = type
                        input = value
                        focusManager.clearFocus()
                    },
                    label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { Icon(Icons.Default.Bolt, null, modifier = Modifier.size(14.dp)) }
                )
            }
        }

        if (result.isFailure && hasInput) {
            Text(
                result.exceptionOrNull()?.message ?: "Enter a valid value.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        result.getOrNull()?.let { values ->
            PhPohResultCards(values)
        }

        FormulaExplanationCard(
            latexFormula = "pH = -log[H^+], pOH = -log[OH^-], pH + pOH = 14",
            explanation = "At 25 C, pH and pOH add to 14. Lower pH is acidic, higher pH is basic, and pH near 7 is neutral."
        )
    }
}

@Composable
private fun PhPohResultCards(result: com.furthersecrets.chemsearch.data.PhPohResult) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = when (result.classification) {
                "Acidic" -> Color(0xFFEF4444).copy(0.12f)
                "Basic" -> MaterialTheme.colorScheme.primary.copy(0.12f)
                else -> Color(0xFF22C55E).copy(0.12f)
            },
            border = BorderStroke(
                1.dp,
                when (result.classification) {
                    "Acidic" -> Color(0xFFEF4444).copy(0.32f)
                    "Basic" -> MaterialTheme.colorScheme.primary.copy(0.32f)
                    else -> Color(0xFF22C55E).copy(0.32f)
                }
            )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Solution type", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(0.62f))
                Text(result.classification, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            PhPohMetricCard("pH", formatPhPohNumber(result.ph), Modifier.weight(1f))
            PhPohMetricCard("pOH", formatPhPohNumber(result.poh), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            PhPohMetricCard("[H+] mol/L", formatPhPohNumber(result.hydrogenConcentration), Modifier.weight(1f))
            PhPohMetricCard("[OH-] mol/L", formatPhPohNumber(result.hydroxideConcentration), Modifier.weight(1f))
        }
        Text(
            result.assumption,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(0.48f)
        )
    }
}

@Composable
private fun PhPohMetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(0.42f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(0.12f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
            )
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

// TOOL 7 : DILUTION CALCULATOR
@Composable
fun DilutionCalculatorTool() {
    var c1 by remember { mutableStateOf("") }
    var v1 by remember { mutableStateOf("") }
    var c2 by remember { mutableStateOf("") }
    var v2 by remember { mutableStateOf("") }
    var solveFor by remember { mutableStateOf(DilutionSolve.C2) }
    val focusManager = LocalFocusManager.current

    val result = remember(solveFor, c1, v1, c2, v2) { solveDilution(solveFor, c1, v1, c2, v2) }
    val solvedText = result.value?.let { formatNumber(it, 4) }.orEmpty()
    val hasAnyInput = c1.isNotBlank() || v1.isNotBlank() || c2.isNotBlank() || v2.isNotBlank()

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        var showInfo by remember { mutableStateOf(false) }
        if (showInfo) {
            InfoDialog(
                title = "Dilution Calculator",
                entries = listOf(
                    "Equation" to "Uses C₁V₁ = C₂V₂ to solve dilutions.",
                    "Units" to "Keep concentration units consistent (M) and volume units consistent (mL or L).",
                    "Tip" to "Pick the variable you want to solve, then fill the other three."
                ),
                onDismiss = { showInfo = false }
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Dilution Calculator", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            IconButton(onClick = { showInfo = true }, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.onSurface.copy(0.35f), modifier = Modifier.size(16.dp))
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Solve for", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
            var expanded by remember { mutableStateOf(false) }
            Box {
                Surface(
                    onClick = { expanded = true },
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(0.25f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(solveFor.label, style = MaterialTheme.typography.labelMedium)
                        Icon(Icons.Default.KeyboardArrowDown, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                    }
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DilutionSolve.values().forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            onClick = {
                                solveFor = option
                                expanded = false
                                focusManager.clearFocus()
                            }
                        )
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = if (solveFor == DilutionSolve.C1) solvedText else c1,
                onValueChange = { c1 = it },
                label = { Text("C₁ (M)") },
                placeholder = { Text("e.g. 1.0", color = MaterialTheme.colorScheme.onSurface.copy(0.4f)) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                enabled = solveFor != DilutionSolve.C1
            )
            OutlinedTextField(
                value = if (solveFor == DilutionSolve.V1) solvedText else v1,
                onValueChange = { v1 = it },
                label = { Text("V₁ (mL)") },
                placeholder = { Text("e.g. 25", color = MaterialTheme.colorScheme.onSurface.copy(0.4f)) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                enabled = solveFor != DilutionSolve.V1
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = if (solveFor == DilutionSolve.C2) solvedText else c2,
                onValueChange = { c2 = it },
                label = { Text("C₂ (M)") },
                placeholder = { Text("e.g. 0.5", color = MaterialTheme.colorScheme.onSurface.copy(0.4f)) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                enabled = solveFor != DilutionSolve.C2
            )
            OutlinedTextField(
                value = if (solveFor == DilutionSolve.V2) solvedText else v2,
                onValueChange = { v2 = it },
                label = { Text("V₂ (mL)") },
                placeholder = { Text("e.g. 50", color = MaterialTheme.colorScheme.onSurface.copy(0.4f)) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                enabled = solveFor != DilutionSolve.V2
            )
        }

        if (result.error != null && hasAnyInput) {
            Text(result.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        } else if (result.value != null) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary.copy(0.1f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(0.3f))
            ) {
                Text(
                    "${solveFor.label} = ${formatNumber(result.value, 4)} ${solveFor.unit}",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        FormulaExplanationCard(
            latexFormula = "C_1V_1 = C_2V_2",
            explanation = "Stock concentration and volume are related to final concentration and volume. " +
                "Given any three values, the fourth is determined directly from this equation."
        )
    }
}

// TOOL 8 : IDEAL GAS LAW
@Composable
fun IdealGasLawTool() {
    var pressure by remember { mutableStateOf("") }
    var volume by remember { mutableStateOf("") }
    var moles by remember { mutableStateOf("") }
    var temperature by remember { mutableStateOf("") }
    var solveFor by remember { mutableStateOf(GasSolve.PRESSURE) }
    val focusManager = LocalFocusManager.current

    val result = remember(solveFor, pressure, volume, moles, temperature) {
        solveGasLaw(solveFor, pressure, volume, moles, temperature)
    }
    val solvedText = result.value?.let { formatNumber(it, 4) }.orEmpty()
    val hasAnyInput = pressure.isNotBlank() || volume.isNotBlank() || moles.isNotBlank() || temperature.isNotBlank()

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        var showInfo by remember { mutableStateOf(false) }
        if (showInfo) {
            InfoDialog(
                title = "Ideal Gas Law",
                entries = listOf(
                    "Equation" to "Uses PV = nRT to solve for any variable.",
                    "Units" to "P in atm, V in liters, n in moles, T in Kelvin.",
                    "Gas constant" to "R = 0.082057 L·atm/mol·K."
                ),
                onDismiss = { showInfo = false }
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Ideal Gas Law", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            IconButton(onClick = { showInfo = true }, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.onSurface.copy(0.35f), modifier = Modifier.size(16.dp))
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Solve for", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
            var expanded by remember { mutableStateOf(false) }
            Box {
                Surface(
                    onClick = { expanded = true },
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(0.25f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(solveFor.label, style = MaterialTheme.typography.labelMedium)
                        Icon(Icons.Default.KeyboardArrowDown, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                    }
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    GasSolve.values().forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            onClick = {
                                solveFor = option
                                expanded = false
                                focusManager.clearFocus()
                            }
                        )
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = if (solveFor == GasSolve.PRESSURE) solvedText else pressure,
                onValueChange = { pressure = it },
                label = { Text("Pressure (atm)") },
                placeholder = { Text("e.g. 1.0", color = MaterialTheme.colorScheme.onSurface.copy(0.4f)) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                enabled = solveFor != GasSolve.PRESSURE
            )
            OutlinedTextField(
                value = if (solveFor == GasSolve.VOLUME) solvedText else volume,
                onValueChange = { volume = it },
                label = { Text("Volume (L)") },
                placeholder = { Text("e.g. 2.5", color = MaterialTheme.colorScheme.onSurface.copy(0.4f)) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                enabled = solveFor != GasSolve.VOLUME
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = if (solveFor == GasSolve.MOLES) solvedText else moles,
                onValueChange = { moles = it },
                label = { Text("Moles (mol)") },
                placeholder = { Text("e.g. 0.5", color = MaterialTheme.colorScheme.onSurface.copy(0.4f)) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                enabled = solveFor != GasSolve.MOLES
            )
            OutlinedTextField(
                value = if (solveFor == GasSolve.TEMPERATURE) solvedText else temperature,
                onValueChange = { temperature = it },
                label = { Text("Temperature (K)") },
                placeholder = { Text("e.g. 298", color = MaterialTheme.colorScheme.onSurface.copy(0.4f)) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                enabled = solveFor != GasSolve.TEMPERATURE
            )
        }

        Text("R = 0.082057 L·atm/mol·K", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(0.5f))

        if (result.error != null && hasAnyInput) {
            Text(result.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        } else if (result.value != null) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary.copy(0.1f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(0.3f))
            ) {
                Text(
                    "${solveFor.label} = ${formatNumber(result.value, 4)} ${solveFor.unit}",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        FormulaExplanationCard(
            latexFormula = "PV = nRT",
            explanation = "Pressure, volume, amount, and temperature are linked by the ideal gas law. " +
                "Solve any one variable when the other three are known and units are consistent."
        )
    }
}

// TOOL 9 : STOICHIOMETRY TOOLS
private const val AVOGADRO = 6.02214076e23
private const val DEFAULT_MOLAR_VOLUME = 22.414

private enum class StoichiometryMode {
    LIMITING,
    YIELD,
    SCALING
}

private enum class StoichUnit(val label: String) {
    GRAMS("g"),
    KILOGRAMS("kg"),
    MOLES("mol"),
    MILLIMOLES("mmol"),
    LITERS_GAS("L gas"),
    MILLILITERS_GAS("mL gas"),
    MOLARITY("M (mol/L)"),
    PARTICLES("particles x10^23")
}

private data class StoichReactantInput(
    val formula: String,
    val coeff: Int,
    val amount: String = "",
    val unit: StoichUnit = StoichUnit.GRAMS,
    val molarity: String = "",
    val volume: String = "",
    val purity: String = ""
)

private data class StoichMoleInfo(
    val moles: Double?,
    val error: String? = null,
    val purityApplied: Boolean = false
)

private fun parsePositiveNumber(raw: String): Double? {
    val cleaned = raw.trim().replace(",", "")
    val value = cleaned.toDoubleOrNull() ?: return null
    return if (value > 0) value else null
}

private fun ratioToString(numerator: Int, denominator: Int): String {
    fun gcdInt(a: Int, b: Int): Int = if (b == 0) kotlin.math.abs(a) else gcdInt(b, a % b)
    val g = gcdInt(numerator, denominator).coerceAtLeast(1)
    val n = numerator / g
    val d = denominator / g
    return if (d == 1) "$n" else "$n/$d"
}

private fun formatNumber(value: Double, decimals: Int = 4): String = "%.${decimals}f".format(value)

private fun computeMolesForInput(
    input: StoichReactantInput,
    molarMass: Double?,
    molarVolume: Double
): StoichMoleInfo {
    val purityRaw = input.purity.trim()
    val purity = if (purityRaw.isBlank()) null else parsePositiveNumber(purityRaw)
    if (purityRaw.isNotBlank() && purity == null) {
        return StoichMoleInfo(null, "Invalid purity %")
    }
    if (purity != null && purity > 100.0) {
        return StoichMoleInfo(null, "Purity must be <= 100%")
    }

    val molesBase = when (input.unit) {
        StoichUnit.MOLARITY -> {
            if (input.molarity.isBlank() && input.volume.isBlank()) return StoichMoleInfo(null)
            val molarity = parsePositiveNumber(input.molarity) ?: return StoichMoleInfo(null, "Enter molarity")
            val volume = parsePositiveNumber(input.volume) ?: return StoichMoleInfo(null, "Enter volume")
            molarity * (volume / 1000.0)
        }
        else -> {
            if (input.amount.isBlank()) return StoichMoleInfo(null)
            val amount = parsePositiveNumber(input.amount) ?: return StoichMoleInfo(null, "Enter a valid amount")
            when (input.unit) {
                StoichUnit.GRAMS -> {
                    if (molarMass == null) return StoichMoleInfo(null, "Molar mass unavailable")
                    amount / molarMass
                }
                StoichUnit.KILOGRAMS -> {
                    if (molarMass == null) return StoichMoleInfo(null, "Molar mass unavailable")
                    (amount * 1000.0) / molarMass
                }
                StoichUnit.MOLES -> amount
                StoichUnit.MILLIMOLES -> amount / 1000.0
                StoichUnit.LITERS_GAS -> {
                    if (molarVolume <= 0.0) return StoichMoleInfo(null, "Invalid molar volume")
                    amount / molarVolume
                }
                StoichUnit.MILLILITERS_GAS -> {
                    if (molarVolume <= 0.0) return StoichMoleInfo(null, "Invalid molar volume")
                    (amount / 1000.0) / molarVolume
                }
                StoichUnit.PARTICLES -> (amount * 1e23) / AVOGADRO
                StoichUnit.MOLARITY -> return StoichMoleInfo(null)
            }
        }
    }

    val moles = if (purity != null) molesBase * (purity / 100.0) else molesBase
    return StoichMoleInfo(moles, null, purityApplied = purity != null)
}

@Composable
private fun StoichUnitDropdown(
    unit: StoichUnit,
    onUnitChange: (StoichUnit) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(0.25f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(unit.label, style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace)
                Icon(Icons.Default.KeyboardArrowDown, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurface.copy(0.6f))
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            StoichUnit.values().forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label, fontFamily = FontFamily.Monospace) },
                    onClick = {
                        onUnitChange(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun StoichiometryCalculator(
    mode: StoichiometryMode = StoichiometryMode.LIMITING,
    title: String = "Stoichiometry Calculator"
) {
    var equation by remember { mutableStateOf(TextFieldValue("")) }
    var result by remember { mutableStateOf<BalancerResult?>(null) }
    val reactantInputs = remember { mutableStateListOf<StoichReactantInput>() }
    var molarVolumeInput by remember { mutableStateOf(DEFAULT_MOLAR_VOLUME.toString()) }
    var selectedProductIndex by remember { mutableStateOf(0) }
    var desiredAmount by remember { mutableStateOf("") }
    var desiredUnit by remember { mutableStateOf(StoichUnit.GRAMS) }
    var desiredMolarity by remember { mutableStateOf("") }
    var desiredVolume by remember { mutableStateOf("") }
    var actualAmount by remember { mutableStateOf("") }
    var actualUnit by remember { mutableStateOf(StoichUnit.GRAMS) }
    var actualMolarity by remember { mutableStateOf("") }
    var actualVolume by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val showReactantInputs = mode != StoichiometryMode.SCALING
    val showSummary = mode == StoichiometryMode.LIMITING || mode == StoichiometryMode.YIELD
    val showYield = mode == StoichiometryMode.YIELD
    val showScaling = mode == StoichiometryMode.SCALING

    val examples = listOf(
        "H2 + O2 -> H2O",
        "C3H8 + O2 -> CO2 + H2O",
        "N2 + H2 -> NH3",
        "CaCO3 -> CaO + CO2",
        "Fe2O3 + CO -> Fe + CO2"
    )

    var showInfo by remember { mutableStateOf(false) }
    if (showInfo) {
        InfoDialog(
            title = title,
            entries = listOf(
                "What this does" to "Balances a reaction, finds the limiting reagent, computes theoretical yields, excess reagents, and reaction scaling.",
                "Limiting reagent" to "The limiting reagent has the smallest (moles / coefficient) ratio. It determines the reaction extent.",
                "Units supported" to "Mass (g, kg), amount (mol, mmol), gas volume (L, mL), solutions (M and mL), and particles (x10^23).",
                "Gas volumes" to "Gas moles are calculated from a molar volume you can edit (22.414 L/mol at STP).",
                "Purity" to "Optional purity % applies a correction to effective moles for each reactant.",
                "Percent yield" to "Compare actual yield to theoretical yield for the selected product.",
                "Scaling" to "Enter a desired product amount to see the required reactant amounts."
            ),
            onDismiss = { showInfo = false }
        )
    }

    LaunchedEffect(result) {
        if (result == null || result?.error != null) {
            reactantInputs.clear()
        } else {
            val existing = reactantInputs.associateBy { it.formula }
            reactantInputs.clear()
            result?.reactants?.forEach { (formula, coeff) ->
                val prev = existing[formula]
                reactantInputs.add(prev?.copy(coeff = coeff) ?: StoichReactantInput(formula = formula, coeff = coeff))
            }
            val productCount = result?.products?.size ?: 0
            if (selectedProductIndex >= productCount) selectedProductIndex = 0
        }
    }

    val molarVolume = parsePositiveNumber(molarVolumeInput) ?: DEFAULT_MOLAR_VOLUME
    val molarVolumeError = molarVolumeInput.isNotBlank() && parsePositiveNumber(molarVolumeInput) == null
    val molarMassMap = remember(result) {
        val map = mutableMapOf<String, Double?>()
        val formulas = (result?.reactants ?: emptyList()) + (result?.products ?: emptyList())
        formulas.forEach { (formula, _) ->
            val res = calculateMolarMass(formula)
            map[formula] = if (res.error == null) res.molarMass else null
        }
        map
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            IconButton(onClick = { showInfo = true }, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.onSurface.copy(0.35f), modifier = Modifier.size(16.dp))
            }
        }

        OutlinedTextField(
            value = equation,
            onValueChange = { new ->
                val normalized = normalizeEquationField(new)
                val textChanged = normalized.text != equation.text
                equation = normalized
                if (textChanged) result = null
            },
            label = { Text("Chemical Equation") },
            placeholder = { Text("H2 + O2 -> H2O", color = MaterialTheme.colorScheme.onSurface.copy(0.4f)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            singleLine = true,
            visualTransformation = FormulaSubscriptTransformation,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                focusManager.clearFocus()
                result = balanceReaction(equation.text.replace("→", "->"))
            }),
            trailingIcon = {
                if (equation.text.isNotBlank()) {
                    IconButton(onClick = { equation = TextFieldValue(""); result = null }) {
                        Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp))
                    }
                }
            }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Insert:",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(0.45f)
            )
            Row(
                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("+" to " + ", "→" to " → ", "(" to "(", ")" to ")").forEach { (label, insert) ->
                    Surface(
                        onClick = { equation = insertIntoField(equation, insert); result = null },
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(0.3f))
                    ) {
                        Text(
                            label,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = { equation = fieldValueAtEnd(swapEquationSides(equation.text)); result = null },
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(0.3f))
                ) {
                    Icon(
                        Icons.Default.SwapHoriz,
                        contentDescription = "Swap sides",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp).size(14.dp)
                    )
                }
            }
        }

        Button(
            onClick = { focusManager.clearFocus(); result = balanceReaction(equation.text.replace("→", "->")) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            enabled = equation.text.isNotBlank()
        ) {
            Icon(Icons.Default.Calculate, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Balance & Calculate")
        }

        OutlinedTextField(
            value = molarVolumeInput,
            onValueChange = { molarVolumeInput = it },
            label = { Text("Gas molar volume (L/mol)") },
            placeholder = { Text(DEFAULT_MOLAR_VOLUME.toString(), color = MaterialTheme.colorScheme.onSurface.copy(0.4f)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            singleLine = true,
            isError = molarVolumeError,
            supportingText = if (molarVolumeError) {{ Text("Enter a valid molar volume (e.g. 22.414)") }} else null
        )

        result?.let { res ->
            if (res.error != null) {
                Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(0.4f))) {
                    Text(res.error, modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            } else {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("BALANCED EQUATION", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.45f))

                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            res.reactants.forEachIndexed { i, (formula, coeff) ->
                                if (i > 0) {
                                    Text("+", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(0.5f), modifier = Modifier.align(Alignment.CenterVertically))
                                }
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(0.08f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(0.25f))
                                ) {
                                    Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        if (coeff > 1) {
                                            Text("$coeff", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                                        }
                                        Text(toSubscriptFormula(formula), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
                                    }
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.outline.copy(0.1f),
                                modifier = Modifier.align(Alignment.CenterVertically)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurface.copy(0.6f), modifier = Modifier.padding(6.dp).size(16.dp))
                            }

                            res.products.forEachIndexed { i, (formula, coeff) ->
                                if (i > 0) {
                                    Text("+", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(0.5f), modifier = Modifier.align(Alignment.CenterVertically))
                                }
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.secondary.copy(0.08f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(0.25f))
                                ) {
                                    Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        if (coeff > 1) {
                                            Text("$coeff", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.secondary)
                                        }
                                        Text(toSubscriptFormula(formula), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
                                    }
                                }
                            }
                        }
                    }
                }

                if (showReactantInputs && reactantInputs.isNotEmpty()) {
                    Text("REACTANT AMOUNTS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.45f))
                }

                val reactantMoles = reactantInputs.map { input ->
                    val molarMass = molarMassMap[input.formula]
                    input to computeMolesForInput(input, molarMass, molarVolume)
                }

                if (showReactantInputs) {
                    reactantInputs.forEachIndexed { index, input ->
                        val molarMass = molarMassMap[input.formula]
                        val info = reactantMoles.getOrNull(index)?.second
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.primary.copy(0.1f),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(0.25f))
                                        ) {
                                            Text(
                                                if (input.coeff > 1) "${input.coeff} ${toSubscriptFormula(input.formula)}" else toSubscriptFormula(input.formula),
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                fontFamily = FontFamily.Monospace,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    Text(
                                        molarMass?.let { "${formatNumber(it, 4)} g/mol" } ?: "Molar mass N/A",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedTextField(
                                        value = if (input.unit == StoichUnit.MOLARITY) input.molarity else input.amount,
                                        onValueChange = { value ->
                                            val updated = if (input.unit == StoichUnit.MOLARITY) input.copy(molarity = value) else input.copy(amount = value)
                                            reactantInputs[index] = updated
                                        },
                                        label = { Text(if (input.unit == StoichUnit.MOLARITY) "Molarity (M)" else "Amount") },
                                        placeholder = { Text("e.g. 2.5", color = MaterialTheme.colorScheme.onSurface.copy(0.4f)) },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp),
                                        singleLine = true
                                    )
                                    StoichUnitDropdown(
                                        unit = input.unit,
                                        onUnitChange = { newUnit ->
                                            reactantInputs[index] = input.copy(unit = newUnit)
                                        }
                                    )
                                }

                                if (input.unit == StoichUnit.MOLARITY) {
                                    OutlinedTextField(
                                        value = input.volume,
                                        onValueChange = { reactantInputs[index] = input.copy(volume = it) },
                                        label = { Text("Volume (mL)") },
                                        placeholder = { Text("e.g. 250", color = MaterialTheme.colorScheme.onSurface.copy(0.4f)) },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        singleLine = true
                                    )
                                }

                                OutlinedTextField(
                                    value = input.purity,
                                    onValueChange = { reactantInputs[index] = input.copy(purity = it) },
                                    label = { Text("Purity % (optional)") },
                                    placeholder = { Text("100", color = MaterialTheme.colorScheme.onSurface.copy(0.4f)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true
                                )

                                when {
                                    info?.error != null -> {
                                        Text(info.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                    }
                                    info?.moles != null -> {
                                        val note = if (info.purityApplied) " (purity applied)" else ""
                                        Text(
                                            "Moles available: ${formatNumber(info.moles)} mol$note",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(0.7f)
                                        )
                                    }
                                    else -> {
                                        Text("Enter an amount to compute moles.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(0.45f))
                                    }
                                }
                            }
                        }
                    }

                    if (reactantInputs.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                for (i in reactantInputs.indices) {
                                    reactantInputs[i] = reactantInputs[i].copy(amount = "", molarity = "", volume = "", purity = "")
                                }
                                actualAmount = ""
                                actualMolarity = ""
                                actualVolume = ""
                                desiredAmount = ""
                                desiredMolarity = ""
                                desiredVolume = ""
                            },
                            contentPadding = PaddingValues(horizontal = 0.dp)
                        ) {
                            Text("Clear all amounts", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                val allReactantsReady = reactantMoles.isNotEmpty() && reactantMoles.all { it.second.moles != null && it.second.error == null }
                val limitingData = if (allReactantsReady) {
                    val minEntry = reactantMoles.minBy { (input, info) -> info.moles!! / input.coeff }
                    val extent = minEntry.second.moles!! / minEntry.first.coeff
                    Triple(minEntry, extent, minEntry.first.coeff)
                } else {
                    null
                }

                if (showSummary) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("STOICHIOMETRY SUMMARY", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.45f))

                            if (limitingData == null) {
                                Text(
                                    "Enter valid amounts for all reactants to determine the limiting reagent.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(0.6f)
                                )
                            } else {
                                val (limitingEntry, extent, limitingCoeff) = limitingData
                                val limitingFormula = limitingEntry.first.formula
                                val limitingMoles = limitingEntry.second.moles ?: 0.0
                                val limitingMass = molarMassMap[limitingFormula]?.let { it * limitingMoles }

                                Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text("Limiting reagent", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                                        Text(toSubscriptFormula(limitingFormula), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("${formatNumber(limitingMoles)} mol", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                                        if (limitingMass != null) {
                                            Text("${formatNumber(limitingMass, 3)} g", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                                        }
                                    }
                                }

                                Text("Reaction extent: ${formatNumber(extent)} mol", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(0.65f))

                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.15f))
                                Text("MOLE RATIOS (relative to limiting reagent)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.45f))

                                (res.reactants + res.products).forEach { (formula, coeff) ->
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(toSubscriptFormula(formula), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                                        Text(ratioToString(coeff, limitingCoeff), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                                    }
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.15f))
                                Text("THEORETICAL YIELD", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.45f))

                                res.products.forEach { (formula, coeff) ->
                                    val moles = extent * coeff
                                    val mass = molarMassMap[formula]?.let { it * moles }
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(toSubscriptFormula(formula), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text("${formatNumber(moles)} mol", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                                            Text(
                                                mass?.let { "${formatNumber(it, 3)} g" } ?: "g N/A",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface.copy(0.6f)
                                            )
                                        }
                                    }
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.15f))
                                Text("EXCESS REACTANTS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.45f))

                                reactantMoles.forEach { (input, info) ->
                                    val available = info.moles ?: return@forEach
                                    val used = extent * input.coeff
                                    val leftover = (available - used).coerceAtLeast(0.0)
                                    val leftoverMass = molarMassMap[input.formula]?.let { it * leftover }
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(toSubscriptFormula(input.formula), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text("${formatNumber(leftover)} mol", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                                            Text(
                                                leftoverMass?.let { "${formatNumber(it, 3)} g" } ?: "g N/A",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface.copy(0.6f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                val products = res.products
                if (products.isNotEmpty() && (showYield || showScaling)) {
                    val sectionTitle = when {
                        showYield && showScaling -> "YIELD & SCALING"
                        showYield -> "PERCENT YIELD"
                        else -> "SCALING"
                    }
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(sectionTitle, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.45f))

                            var productMenuExpanded by remember { mutableStateOf(false) }
                            val selectedProduct = products.getOrNull(selectedProductIndex) ?: products.first()
                            Box {
                                Surface(
                                    onClick = { productMenuExpanded = true },
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(0.25f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text("Target product:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                                        Text(toSubscriptFormula(selectedProduct.first), style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace)
                                        Icon(Icons.Default.KeyboardArrowDown, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                                    }
                                }
                                DropdownMenu(expanded = productMenuExpanded, onDismissRequest = { productMenuExpanded = false }) {
                                    products.forEachIndexed { idx, (formula, _) ->
                                        DropdownMenuItem(
                                            text = { Text(toSubscriptFormula(formula), fontFamily = FontFamily.Monospace) },
                                            onClick = {
                                                selectedProductIndex = idx
                                                productMenuExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            if (showYield) {
                                Text("Actual yield (optional)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedTextField(
                                        value = if (actualUnit == StoichUnit.MOLARITY) actualMolarity else actualAmount,
                                        onValueChange = { value ->
                                            if (actualUnit == StoichUnit.MOLARITY) actualMolarity = value else actualAmount = value
                                        },
                                        label = { Text(if (actualUnit == StoichUnit.MOLARITY) "Molarity (M)" else "Amount") },
                                        placeholder = { Text("e.g. 1.25", color = MaterialTheme.colorScheme.onSurface.copy(0.4f)) },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp),
                                        singleLine = true
                                    )
                                    StoichUnitDropdown(
                                        unit = actualUnit,
                                        onUnitChange = { actualUnit = it }
                                    )
                                }

                                if (actualUnit == StoichUnit.MOLARITY) {
                                    OutlinedTextField(
                                        value = actualVolume,
                                        onValueChange = { actualVolume = it },
                                        label = { Text("Volume (mL)") },
                                        placeholder = { Text("e.g. 100", color = MaterialTheme.colorScheme.onSurface.copy(0.4f)) },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        singleLine = true
                                    )
                                }

                                val theoreticalMoles = if (limitingData != null) {
                                    val extent = limitingData.second
                                    extent * selectedProduct.second
                                } else null
                                val actualInfo = computeMolesForInput(
                                    StoichReactantInput(
                                        formula = selectedProduct.first,
                                        coeff = 1,
                                        amount = actualAmount,
                                        unit = actualUnit,
                                        molarity = actualMolarity,
                                        volume = actualVolume
                                    ),
                                    molarMassMap[selectedProduct.first],
                                    molarVolume
                                )
                                val actualProvided = if (actualUnit == StoichUnit.MOLARITY) {
                                    actualMolarity.isNotBlank() || actualVolume.isNotBlank()
                                } else {
                                    actualAmount.isNotBlank()
                                }

                                if (theoreticalMoles != null && actualInfo.moles != null && actualInfo.error == null) {
                                    val percentYield = (actualInfo.moles / theoreticalMoles) * 100.0
                                    Text(
                                        "Percent yield: ${formatNumber(percentYield, 2)}%",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (percentYield >= 100.0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(0.75f)
                                    )
                                } else if (actualInfo.error != null) {
                                    Text(actualInfo.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                } else if (actualProvided && limitingData == null) {
                                    Text("Need limiting reagent to compute percent yield.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(0.55f))
                                }
                            }

                            if (showYield && showScaling) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.15f))
                            }

                            if (showScaling) {
                                Text("Desired product amount", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedTextField(
                                        value = if (desiredUnit == StoichUnit.MOLARITY) desiredMolarity else desiredAmount,
                                        onValueChange = { value ->
                                            if (desiredUnit == StoichUnit.MOLARITY) desiredMolarity = value else desiredAmount = value
                                        },
                                        label = { Text(if (desiredUnit == StoichUnit.MOLARITY) "Molarity (M)" else "Amount") },
                                        placeholder = { Text("e.g. 5.0", color = MaterialTheme.colorScheme.onSurface.copy(0.4f)) },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp),
                                        singleLine = true
                                    )
                                    StoichUnitDropdown(
                                        unit = desiredUnit,
                                        onUnitChange = { desiredUnit = it }
                                    )
                                }

                                if (desiredUnit == StoichUnit.MOLARITY) {
                                    OutlinedTextField(
                                        value = desiredVolume,
                                        onValueChange = { desiredVolume = it },
                                        label = { Text("Volume (mL)") },
                                        placeholder = { Text("e.g. 500", color = MaterialTheme.colorScheme.onSurface.copy(0.4f)) },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        singleLine = true
                                    )
                                }

                                val desiredInfo = computeMolesForInput(
                                    StoichReactantInput(
                                        formula = selectedProduct.first,
                                        coeff = 1,
                                        amount = desiredAmount,
                                        unit = desiredUnit,
                                        molarity = desiredMolarity,
                                        volume = desiredVolume
                                    ),
                                    molarMassMap[selectedProduct.first],
                                    molarVolume
                                )

                                if (desiredInfo.moles != null && desiredInfo.error == null) {
                                    val extent = desiredInfo.moles / selectedProduct.second
                                    Text("Required reactants (theoretical)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                                    res.reactants.forEach { (formula, coeff) ->
                                        val reqMoles = extent * coeff
                                        val reqMass = molarMassMap[formula]?.let { it * reqMoles }
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(toSubscriptFormula(formula), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text("${formatNumber(reqMoles)} mol", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                                                Text(
                                                    reqMass?.let { "${formatNumber(it, 3)} g" } ?: "g N/A",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(0.6f)
                                                )
                                            }
                                        }
                                    }
                                } else if (desiredInfo.error != null) {
                                    Text(desiredInfo.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }

        Text("EXAMPLES", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            examples.forEach { ex ->
                Surface(
                    onClick = { equation = fieldValueAtEnd(ex.replace("->", "→")); result = null },
                    shape = RoundedCornerShape(10.dp),
                    color = if (equation.text.replace("→", "->") == ex) MaterialTheme.colorScheme.primary.copy(0.1f) else MaterialTheme.colorScheme.surfaceVariant.copy(0.5f),
                    border = if (equation.text.replace("→", "->") == ex) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(0.4f)) else null
                ) {
                    Text(
                        reactionToDisplay(ex),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = if (equation.text.replace("→", "->") == ex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(0.7f)
                    )
                }
            }
        }
    }
}

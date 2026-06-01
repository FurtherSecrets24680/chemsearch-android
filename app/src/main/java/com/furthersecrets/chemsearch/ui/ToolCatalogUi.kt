package com.furthersecrets.chemsearch.ui

import android.content.SharedPreferences

internal const val TOOL_ORDER_PREF = "tool_order"
internal const val TOOL_VIEW_MODE_PREF = "tool_view_mode"

internal fun loadToolOrder(prefs: SharedPreferences, defaultIds: List<Int>): List<Int> {
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

internal fun saveToolOrder(prefs: SharedPreferences, order: List<Int>) {
    prefs.edit().putString(TOOL_ORDER_PREF, order.joinToString(",")).apply()
}

internal enum class ToolCategory(val label: String) {
    ALL("All"),
    VISUALIZE("Visualize"),
    CALCULATORS("Calculators"),
    REACTIONS("Reactions"),
    STOICHIOMETRY("Stoichiometry")
}

internal enum class ToolViewMode { LIST, GRID }

internal val TOOL_CATEGORIES = listOf(
    ToolCategory.ALL,
    ToolCategory.VISUALIZE,
    ToolCategory.CALCULATORS,
    ToolCategory.REACTIONS,
    ToolCategory.STOICHIOMETRY
)

internal data class ToolDefinition(
    val id: Int,
    val icon: ChemIconSpec,
    val title: String,
    val subtitle: String,
    val category: ToolCategory
)

internal val DEFAULT_TOOLS = listOf(
    ToolDefinition(
        id = 2,
        icon = ChemAppIcons.Calculator,
        title = "Molar Mass Calculator",
        subtitle = "Enter a molecular formula and get the molar mass",
        category = ToolCategory.CALCULATORS
    ),
    ToolDefinition(
        id = 16,
        icon = ChemAppIcons.Percent,
        title = "Empirical Formula Finder",
        subtitle = "Find empirical and molecular formulas from composition data",
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
        id = 15,
        icon = ChemAppIcons.TestTubes,
        title = "Precipitate Predictor",
        subtitle = "Predict precipitates and net ionic equations from two salts",
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
        id = 4,
        icon = ChemAppIcons.Network,
        title = "SMILES Visualizer",
        subtitle = "Paste a SMILES string to view its 2D and 3D structure",
        category = ToolCategory.VISUALIZE
    ),
    ToolDefinition(
        id = 1,
        icon = ChemAppIcons.Cube,
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
    )
)

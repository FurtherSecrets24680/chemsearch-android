package com.furthersecrets.chemsearch.ui

import android.content.SharedPreferences
import androidx.compose.ui.res.stringResource
import com.furthersecrets.chemsearch.R

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

internal enum class ToolCategory(val labelRes: Int) {
    ALL(R.string.ui_tool_category_all),
    VISUALIZE(R.string.ui_tool_category_visualize),
    CALCULATORS(R.string.ui_tool_category_calculators),
    REACTIONS(R.string.ui_tool_category_reactions),
    STOICHIOMETRY(R.string.ui_tool_category_stoichiometry)
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
    val titleRes: Int,
    val subtitleRes: Int,
    val category: ToolCategory
)

internal val DEFAULT_TOOLS = listOf(
    ToolDefinition(
        id = 2,
        icon = ChemAppIcons.Calculator,
        titleRes = R.string.ui_molar_mass_calculator,
        subtitleRes = R.string.ui_molar_mass_calculator_subtitle,
        category = ToolCategory.CALCULATORS
    ),
    ToolDefinition(
        id = 16,
        icon = ChemAppIcons.Percent,
        titleRes = R.string.ui_empirical_formula_finder,
        subtitleRes = R.string.ui_empirical_formula_finder_subtitle,
        category = ToolCategory.CALCULATORS
    ),
    ToolDefinition(
        id = 5,
        icon = ChemAppIcons.ArrowLeftRight,
        titleRes = R.string.ui_reaction_balancer,
        subtitleRes = R.string.ui_reaction_balancer_subtitle,
        category = ToolCategory.REACTIONS
    ),
    ToolDefinition(
        id = 15,
        icon = ChemAppIcons.TestTubes,
        titleRes = R.string.ui_precipitate_predictor,
        subtitleRes = R.string.ui_precipitate_predictor_subtitle,
        category = ToolCategory.REACTIONS
    ),
    ToolDefinition(
        id = 14,
        icon = ChemAppIcons.Droplets,
        titleRes = R.string.ui_ph_poh_calculator,
        subtitleRes = R.string.ui_ph_poh_calculator_subtitle,
        category = ToolCategory.CALCULATORS
    ),
    ToolDefinition(
        id = 3,
        icon = ChemAppIcons.Atom,
        titleRes = R.string.ui_oxidation_state_finder,
        subtitleRes = R.string.ui_oxidation_state_finder_subtitle,
        category = ToolCategory.CALCULATORS
    ),
    ToolDefinition(
        id = 7,
        icon = ChemAppIcons.ListFilter,
        titleRes = R.string.ui_limiting_reagent,
        subtitleRes = R.string.ui_limiting_reagent_subtitle,
        category = ToolCategory.STOICHIOMETRY
    ),
    ToolDefinition(
        id = 8,
        icon = ChemAppIcons.Percent,
        titleRes = R.string.ui_percent_yield,
        subtitleRes = R.string.ui_percent_yield_subtitle,
        category = ToolCategory.STOICHIOMETRY
    ),
    ToolDefinition(
        id = 11,
        icon = ChemAppIcons.Droplet,
        titleRes = R.string.ui_dilution_calculator,
        subtitleRes = R.string.ui_dilution_calculator_subtitle,
        category = ToolCategory.CALCULATORS
    ),
    ToolDefinition(
        id = 12,
        icon = ChemAppIcons.Wind,
        titleRes = R.string.ui_ideal_gas_law,
        subtitleRes = R.string.ui_ideal_gas_law_subtitle,
        category = ToolCategory.CALCULATORS
    ),
    ToolDefinition(
        id = 13,
        icon = ChemAppIcons.GitCompareArrows,
        titleRes = R.string.ui_compare_compounds,
        subtitleRes = R.string.ui_compare_compounds_subtitle,
        category = ToolCategory.CALCULATORS
    ),
    ToolDefinition(
        id = 4,
        icon = ChemAppIcons.Network,
        titleRes = R.string.ui_smiles_visualizer,
        subtitleRes = R.string.ui_smiles_visualizer_subtitle,
        category = ToolCategory.VISUALIZE
    ),
    ToolDefinition(
        id = 1,
        icon = ChemAppIcons.Cube,
        titleRes = R.string.ui_custom_3d_molecule_viewer,
        subtitleRes = R.string.ui_custom_3d_viewer_subtitle,
        category = ToolCategory.VISUALIZE
    ),
    ToolDefinition(
        id = 9,
        icon = ChemAppIcons.SlidersHorizontal,
        titleRes = R.string.ui_reaction_scaling,
        subtitleRes = R.string.ui_reaction_scaling_subtitle,
        category = ToolCategory.STOICHIOMETRY
    )
)
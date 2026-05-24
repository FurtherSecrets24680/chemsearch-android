package com.furthersecrets.chemsearch.data

import android.content.Context
import com.google.gson.Gson

enum class ChemicalDbCategory(val label: String) {
    SUBSTANCES("Substances"),
    REACTIONS("Reactions"),
    FUNCTIONAL_GROUPS("Functional Groups"),
    IONS("Ions")
}

enum class ChemicalDbActionTarget {
    SEARCH_COMPOUND,
    COPY_EQUATION,
    COPY_TEXT
}

data class ChemicalDbSubstancesFile(
    val version: Int = 1,
    val substances: List<ChemicalDbSubstanceConfig>? = emptyList(),
)

data class ChemicalDbReactionsFile(
    val version: Int = 1,
    val reactions: List<ChemicalDbReactionConfig>? = emptyList(),
)

data class ChemicalDbFunctionalGroupsFile(
    val version: Int = 1,
    val functionalGroups: List<ChemicalDbFunctionalGroupConfig>? = emptyList(),
)

data class ChemicalDbIonsFile(
    val version: Int = 1,
    val ions: List<ChemicalDbIonConfig>? = emptyList(),
)

data class ChemicalDbSubstanceConfig(
    val id: String? = null,
    val name: String? = null,
    val formula: String? = null,
    val otherNames: List<String>? = emptyList(),
    val type: String? = null,
    val uses: String? = null,
    val notes: String? = null,
    val tags: List<String>? = emptyList(),
    val sourceLabel: String? = "PubChem",
    val sourceUrl: String? = "https://pubchem.ncbi.nlm.nih.gov/",
    val searchQuery: String? = null
)

data class ChemicalDbReactionConfig(
    val id: String? = null,
    val name: String? = null,
    val equation: String? = null,
    val type: String? = null,
    val conditions: String? = null,
    val observation: String? = null,
    val notes: String? = null,
    val tags: List<String>? = emptyList(),
    val sourceLabel: String? = "LibreTexts reaction overview",
    val sourceUrl: String? = "https://chem.libretexts.org/Courses/Southwestern_College/Atoms_First_-_Introductory_Chemistry_for_Science_and_Engineering/08%3A_Chemical_Reactions"
)

data class ChemicalDbFunctionalGroupConfig(
    val id: String? = null,
    val name: String? = null,
    val generalFormula: String? = null,
    val structure: String? = null,
    val type: String? = null,
    val namingCue: String? = null,
    val example: String? = null,
    val behavior: String? = null,
    val tags: List<String>? = emptyList(),
    val sourceLabel: String? = "IUPAC Gold Book / LibreTexts",
    val sourceUrl: String? = "https://goldbook.iupac.org/terms/view/F02555"
)

data class ChemicalDbIonConfig(
    val id: String? = null,
    val name: String? = null,
    val formula: String? = null,
    val otherNames: List<String>? = emptyList(),
    val type: String? = null,
    val charge: String? = null,
    val commonCompounds: List<String>? = emptyList(),
    val notes: String? = null,
    val tags: List<String>? = emptyList(),
    val sourceLabel: String? = "General chemistry reference",
    val sourceUrl: String? = "https://chem.libretexts.org/"
)

data class ChemicalDbSection(
    val title: String = "",
    val rows: List<ChemicalDbRow> = emptyList()
)

data class ChemicalDbRow(
    val label: String = "",
    val value: String = ""
)

data class ChemicalDbEntry(
    val id: String = "",
    val category: ChemicalDbCategory = ChemicalDbCategory.SUBSTANCES,
    val title: String = "",
    val formula: String = "",
    val type: String = "",
    val aliases: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val summary: String = "",
    val sections: List<ChemicalDbSection> = emptyList(),
    val sourceLabel: String = "",
    val sourceUrl: String = "",
    val actionTarget: ChemicalDbActionTarget = ChemicalDbActionTarget.COPY_TEXT,
    val actionValue: String = "",
    val searchQuery: String = "",
    val searchText: String = ""
)

data class ChemicalDatabaseSummary(
    val substances: Int = 0,
    val ions: Int = 0,
    val functionalGroups: Int = 0,
    val reactions: Int = 0
)

data class ChemicalDatabaseSummaryRow(
    val label: String,
    val count: Int
)

fun summarizeChemicalDatabase(entries: List<ChemicalDbEntry>): ChemicalDatabaseSummary {
    val counts = entries.groupingBy { it.category }.eachCount()
    return ChemicalDatabaseSummary(
        substances = counts[ChemicalDbCategory.SUBSTANCES] ?: 0,
        ions = counts[ChemicalDbCategory.IONS] ?: 0,
        functionalGroups = counts[ChemicalDbCategory.FUNCTIONAL_GROUPS] ?: 0,
        reactions = counts[ChemicalDbCategory.REACTIONS] ?: 0
    )
}

fun chemicalDatabaseSummaryRows(summary: ChemicalDatabaseSummary): List<ChemicalDatabaseSummaryRow> =
    listOf(
        ChemicalDatabaseSummaryRow("Substances:", summary.substances),
        ChemicalDatabaseSummaryRow("Ions:", summary.ions),
        ChemicalDatabaseSummaryRow("Functional groups:", summary.functionalGroups),
        ChemicalDatabaseSummaryRow("Reactions:", summary.reactions)
    )

fun chemicalDatabaseTotalEntriesLabel(total: Int): String = "Total entries: $total"

object ChemicalDatabase {
    private const val SUBSTANCES_ASSET = "chemical_database/substances.json"
    private const val REACTIONS_ASSET = "chemical_database/reactions.json"
    private const val FUNCTIONAL_GROUPS_ASSET = "chemical_database/functional_groups.json"
    private const val IONS_ASSET = "chemical_database/ions.json"
    private val gson = Gson()
    @Volatile
    private var cachedEntries: List<ChemicalDbEntry>? = null

    fun load(context: Context): List<ChemicalDbEntry> {
        cachedEntries?.let { return it }
        return synchronized(this) {
            cachedEntries ?: loadUncached(context.applicationContext).also { cachedEntries = it }
        }
    }

    private fun loadUncached(context: Context): List<ChemicalDbEntry> =
        runCatching {
            val structuredEntries =
                loadAsset<ChemicalDbSubstancesFile>(context, SUBSTANCES_ASSET)?.substances.orEmpty().map { it.toEntry() } +
                    loadAsset<ChemicalDbReactionsFile>(context, REACTIONS_ASSET)?.reactions.orEmpty().map { it.toEntry() } +
                    loadAsset<ChemicalDbFunctionalGroupsFile>(context, FUNCTIONAL_GROUPS_ASSET)?.functionalGroups.orEmpty().map { it.toEntry() } +
                    loadAsset<ChemicalDbIonsFile>(context, IONS_ASSET)?.ions.orEmpty().map { it.toEntry() }

            structuredEntries
                .filter { it.id.isNotBlank() && it.title.isNotBlank() }
                .map { it.normalized() }
        }.getOrDefault(emptyList())

    private inline fun <reified T> loadAsset(context: Context, assetName: String): T? =
        runCatching {
            context.assets.open(assetName).bufferedReader().use { reader ->
                gson.fromJson(reader, T::class.java)
            }
        }.getOrNull()

    private fun ChemicalDbEntry.normalized(): ChemicalDbEntry {
        val normalizedEntry = copy(
            actionValue = actionValue.ifBlank { formula.ifBlank { title } },
            searchQuery = searchQuery.ifBlank { title },
            sourceLabel = sourceLabel.ifBlank { "Reference" }
        )
        return normalizedEntry.copy(searchText = normalizedEntry.searchBlob())
    }

    private fun ChemicalDbSubstanceConfig.toEntry(): ChemicalDbEntry =
        run {
            val cleanName = name.clean()
            val cleanFormula = formula.clean()
            val cleanType = type.clean()
            val cleanOtherNames = otherNames.cleanList()
            ChemicalDbEntry(
                id = id.clean(),
                category = ChemicalDbCategory.SUBSTANCES,
                title = cleanName,
                formula = cleanFormula,
                type = cleanType,
                aliases = cleanOtherNames,
                tags = (tags.cleanList() + cleanType).filter { it.isNotBlank() }.distinct(),
                summary = uses.clean(),
                sections = details(
                    "Formula" to cleanFormula,
                    "Type" to cleanType,
                    "Common uses" to uses.clean(),
                    "Notes" to notes.clean(),
                    "Other names" to cleanOtherNames.joinToString(", ")
                ),
                sourceLabel = sourceLabel.clean("PubChem"),
                sourceUrl = sourceUrl.clean(),
                actionTarget = ChemicalDbActionTarget.SEARCH_COMPOUND,
                actionValue = cleanFormula,
                searchQuery = searchQuery.clean().ifBlank { cleanName }
            )
        }

    private fun ChemicalDbReactionConfig.toEntry(): ChemicalDbEntry =
        run {
            val cleanName = name.clean()
            val cleanEquation = equation.clean()
            val cleanType = type.clean()
            ChemicalDbEntry(
                id = id.clean(),
                category = ChemicalDbCategory.REACTIONS,
                title = cleanName,
                formula = cleanEquation,
                type = cleanType,
                tags = (tags.cleanList() + cleanType).filter { it.isNotBlank() }.distinct(),
                summary = observation.clean(),
                sections = details(
                    "Equation" to cleanEquation,
                    "Type" to cleanType,
                    "Typical conditions" to conditions.clean(),
                    "Observation" to observation.clean(),
                    "Notes" to notes.clean()
                ),
                sourceLabel = sourceLabel.clean("LibreTexts reaction overview"),
                sourceUrl = sourceUrl.clean(),
                actionTarget = ChemicalDbActionTarget.COPY_EQUATION,
                actionValue = cleanEquation,
                searchQuery = cleanName
            )
        }

    private fun ChemicalDbFunctionalGroupConfig.toEntry(): ChemicalDbEntry =
        run {
            val cleanName = name.clean()
            val cleanGeneralFormula = generalFormula.clean()
            val cleanStructure = structure.clean()
            val cleanType = type.clean()
            ChemicalDbEntry(
                id = id.clean(),
                category = ChemicalDbCategory.FUNCTIONAL_GROUPS,
                title = cleanName,
                formula = cleanStructure.ifBlank { cleanGeneralFormula },
                type = cleanType,
                tags = (tags.cleanList() + cleanType + "organic").filter { it.isNotBlank() }.distinct(),
                summary = behavior.clean(),
                sections = details(
                    "General formula" to cleanGeneralFormula,
                    "Structure" to cleanStructure,
                    "Type" to cleanType,
                    "Naming cue" to namingCue.clean(),
                    "Example" to example.clean(),
                    "Behavior" to behavior.clean()
                ),
                sourceLabel = sourceLabel.clean("IUPAC Gold Book / LibreTexts"),
                sourceUrl = sourceUrl.clean(),
                actionTarget = ChemicalDbActionTarget.COPY_TEXT,
                actionValue = cleanStructure.ifBlank { cleanGeneralFormula },
                searchQuery = cleanName
            )
        }

    private fun ChemicalDbIonConfig.toEntry(): ChemicalDbEntry =
        run {
            val cleanName = name.clean()
            val cleanFormula = formula.clean()
            val cleanType = type.clean()
            val cleanCharge = charge.clean()
            val cleanOtherNames = otherNames.cleanList()
            val cleanCommonCompounds = commonCompounds.cleanList()
            ChemicalDbEntry(
                id = id.clean(),
                category = ChemicalDbCategory.IONS,
                title = cleanName,
                formula = cleanFormula,
                type = cleanType,
                aliases = cleanOtherNames,
                tags = (tags.cleanList() + cleanType + cleanCharge).filter { it.isNotBlank() }.distinct(),
                summary = cleanCommonCompounds.joinToString(", "),
                sections = details(
                    "Formula" to cleanFormula,
                    "Charge" to cleanCharge,
                    "Type" to cleanType,
                    "Common compounds" to cleanCommonCompounds.joinToString(", "),
                    "Other names" to cleanOtherNames.joinToString(", "),
                    "Notes" to notes.clean()
                ),
                sourceLabel = sourceLabel.clean("General chemistry reference"),
                sourceUrl = sourceUrl.clean(),
                actionTarget = ChemicalDbActionTarget.COPY_TEXT,
                actionValue = cleanFormula,
                searchQuery = cleanName
            )
        }

    private fun String?.clean(default: String = ""): String =
        this?.trim().orEmpty().ifBlank { default }

    private fun List<String>?.cleanList(): List<String> =
        orEmpty().mapNotNull { it.trim().takeIf(String::isNotBlank) }

    private fun details(vararg rows: Pair<String, String>): List<ChemicalDbSection> =
        listOf(
            ChemicalDbSection(
                title = "Details",
                rows = rows
                    .filter { it.second.isNotBlank() }
                    .map { ChemicalDbRow(label = it.first, value = it.second) }
            )
        )

    private fun ChemicalDbEntry.searchBlob(): String =
        buildString {
            append(title).append(' ')
            append(formula).append(' ')
            append(summary).append(' ')
            append(category.label).append(' ')
            append(type).append(' ')
            aliases.forEach { append(it).append(' ') }
            tags.forEach { append(it).append(' ') }
            sections.forEach { section ->
                append(section.title).append(' ')
                section.rows.forEach { row ->
                    append(row.label).append(' ').append(row.value).append(' ')
                }
            }
        }.normalizedSearch()

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
}

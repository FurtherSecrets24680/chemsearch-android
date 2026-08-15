package com.furthersecrets.chemsearch.data

import android.content.Context
import com.furthersecrets.chemsearch.R
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.util.Locale

data class AdvancedPropertyLabels(
    val tpsa: String = "Topological polar surface area",
    val complexity: String = "Complexity",
    val exactMass: String = "Exact mass",
    val monoisotopicMass: String = "Monoisotopic mass",
    val hBondDonors: String = "Hydrogen bond donors",
    val hBondAcceptors: String = "Hydrogen bond acceptors",
    val rotatableBonds: String = "Rotatable bonds",
    val heavyAtomCount: String = "Heavy atom count",
    val covalentUnits: String = "Covalently bonded units",
    val isotopeAtomCount: String = "Isotope atom count",
    val volume3d: String = "3D volume",
    val featureCount3d: String = "3D feature count",
    val acceptorFeatures3d: String = "3D acceptor features",
    val donorFeatures3d: String = "3D donor features",
    val anionFeatures3d: String = "3D anion features",
    val cationFeatures3d: String = "3D cation features",
    val ringFeatures3d: String = "3D ring features",
    val hydrophobeFeatures3d: String = "3D hydrophobe features",
    val effectiveRotors3d: String = "Effective 3D rotors",
    val conformerRmsd: String = "Conformer RMSD",
    val conformerCount3d: String = "3D conformer count",
    val atomStereocenters: String = "Atom stereocenters",
    val bondStereocenters: String = "Bond stereocenters",
    val countTotal: String = "%1\$d total",
    val countDefined: String = "%1\$d defined",
    val countUndefined: String = "%1\$d undefined"
)

fun localizedAdvancedPropertyLabels(context: Context): AdvancedPropertyLabels = AdvancedPropertyLabels(
    tpsa = context.getString(R.string.ui_prop_tpsa),
    complexity = context.getString(R.string.ui_prop_complexity),
    exactMass = context.getString(R.string.ui_prop_exact_mass),
    monoisotopicMass = context.getString(R.string.ui_prop_monoisotopic_mass),
    hBondDonors = context.getString(R.string.ui_prop_hbond_donors),
    hBondAcceptors = context.getString(R.string.ui_prop_hbond_acceptors),
    rotatableBonds = context.getString(R.string.ui_prop_rotatable_bonds),
    heavyAtomCount = context.getString(R.string.ui_prop_heavy_atom_count),
    covalentUnits = context.getString(R.string.ui_prop_covalent_units),
    isotopeAtomCount = context.getString(R.string.ui_prop_isotope_atom_count),
    volume3d = context.getString(R.string.ui_prop_volume_3d),
    featureCount3d = context.getString(R.string.ui_prop_feature_count_3d),
    acceptorFeatures3d = context.getString(R.string.ui_prop_acceptor_features_3d),
    donorFeatures3d = context.getString(R.string.ui_prop_donor_features_3d),
    anionFeatures3d = context.getString(R.string.ui_prop_anion_features_3d),
    cationFeatures3d = context.getString(R.string.ui_prop_cation_features_3d),
    ringFeatures3d = context.getString(R.string.ui_prop_ring_features_3d),
    hydrophobeFeatures3d = context.getString(R.string.ui_prop_hydrophobe_features_3d),
    effectiveRotors3d = context.getString(R.string.ui_prop_effective_rotors_3d),
    conformerRmsd = context.getString(R.string.ui_prop_conformer_rmsd),
    conformerCount3d = context.getString(R.string.ui_prop_conformer_count_3d),
    atomStereocenters = context.getString(R.string.ui_prop_atom_stereocenters),
    bondStereocenters = context.getString(R.string.ui_prop_bond_stereocenters),
    countTotal = context.getString(R.string.ui_prop_count_total),
    countDefined = context.getString(R.string.ui_prop_count_defined),
    countUndefined = context.getString(R.string.ui_prop_count_undefined)
)

fun buildAdvancedProperties(props: CompoundProperty, labels: AdvancedPropertyLabels = AdvancedPropertyLabels()): List<AdvancedPropertyRow> = buildList {
    props.xLogP?.let { add(AdvancedPropertyRow("XLogP", formatPropertyNumber(it))) }
    props.tpsa?.let { add(AdvancedPropertyRow(labels.tpsa, "${formatPropertyNumber(it)} A^2")) }
    props.complexity?.let { add(AdvancedPropertyRow(labels.complexity, formatPropertyNumber(it))) }
    props.exactMass?.takeIf { it.isNotBlank() }?.let { add(AdvancedPropertyRow(labels.exactMass, "$it Da")) }
    props.monoisotopicMass?.takeIf { it.isNotBlank() }?.let { add(AdvancedPropertyRow(labels.monoisotopicMass, "$it Da")) }
    props.hBondDonorCount?.let { add(AdvancedPropertyRow(labels.hBondDonors, it.toString())) }
    props.hBondAcceptorCount?.let { add(AdvancedPropertyRow(labels.hBondAcceptors, it.toString())) }
    props.rotatableBondCount?.let { add(AdvancedPropertyRow(labels.rotatableBonds, it.toString())) }
    props.heavyAtomCount?.let { add(AdvancedPropertyRow(labels.heavyAtomCount, it.toString())) }
    props.covalentUnitCount?.let { add(AdvancedPropertyRow(labels.covalentUnits, it.toString())) }
    props.isotopeAtomCount?.takeIf { it > 0 }?.let { add(AdvancedPropertyRow(labels.isotopeAtomCount, it.toString())) }

    val stereoRows = buildStereoRows(props, labels)
    addAll(stereoRows)

    props.volume3d?.let { add(AdvancedPropertyRow(labels.volume3d, formatPropertyNumber(it))) }
    props.featureCount3d?.let { add(AdvancedPropertyRow(labels.featureCount3d, it.toString())) }
    props.featureAcceptorCount3d?.let { add(AdvancedPropertyRow(labels.acceptorFeatures3d, it.toString())) }
    props.featureDonorCount3d?.let { add(AdvancedPropertyRow(labels.donorFeatures3d, it.toString())) }
    props.featureAnionCount3d?.let { add(AdvancedPropertyRow(labels.anionFeatures3d, it.toString())) }
    props.featureCationCount3d?.let { add(AdvancedPropertyRow(labels.cationFeatures3d, it.toString())) }
    props.featureRingCount3d?.let { add(AdvancedPropertyRow(labels.ringFeatures3d, it.toString())) }
    props.featureHydrophobeCount3d?.let { add(AdvancedPropertyRow(labels.hydrophobeFeatures3d, it.toString())) }
    props.effectiveRotorCount3d?.let { add(AdvancedPropertyRow(labels.effectiveRotors3d, formatPropertyNumber(it))) }
    props.conformerModelRmsd3d?.let { add(AdvancedPropertyRow(labels.conformerRmsd, formatPropertyNumber(it))) }
    props.conformerCount3d?.let { add(AdvancedPropertyRow(labels.conformerCount3d, it.toString())) }
}

private fun buildStereoRows(props: CompoundProperty, labels: AdvancedPropertyLabels): List<AdvancedPropertyRow> = buildList {
    val atomStereo = props.atomStereoCount
    val definedAtomStereo = props.definedAtomStereoCount
    val undefinedAtomStereo = props.undefinedAtomStereoCount
    if ((atomStereo ?: 0) > 0 || (definedAtomStereo ?: 0) > 0 || (undefinedAtomStereo ?: 0) > 0) {
        add(AdvancedPropertyRow(labels.atomStereocenters, stereoSummary(labels, atomStereo, definedAtomStereo, undefinedAtomStereo)))
    }

    val bondStereo = props.bondStereoCount
    val definedBondStereo = props.definedBondStereoCount
    val undefinedBondStereo = props.undefinedBondStereoCount
    if ((bondStereo ?: 0) > 0 || (definedBondStereo ?: 0) > 0 || (undefinedBondStereo ?: 0) > 0) {
        add(AdvancedPropertyRow(labels.bondStereocenters, stereoSummary(labels, bondStereo, definedBondStereo, undefinedBondStereo)))
    }
}

private fun stereoSummary(labels: AdvancedPropertyLabels, total: Int?, defined: Int?, undefined: Int?): String {
    val parts = buildList {
        total?.let { add(String.format(Locale.US, labels.countTotal, it)) }
        defined?.takeIf { it > 0 }?.let { add(String.format(Locale.US, labels.countDefined, it)) }
        undefined?.takeIf { it > 0 }?.let { add(String.format(Locale.US, labels.countUndefined, it)) }
    }
    return parts.joinToString(", ").ifBlank { "0" }
}

private fun formatPropertyNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else "%.3f".format(Locale.US, value).trimEnd('0').trimEnd('.')

fun extractPubChemSectionTexts(json: JsonObject?): List<PubChemSectionText> {
    val record = json?.getAsJsonObject("Record") ?: return emptyList()
    val sections = record.getAsJsonArray("Section") ?: return emptyList()
    return extractPubChemSectionTexts(sections)
}

private fun extractPubChemSectionTexts(sections: JsonArray): List<PubChemSectionText> {
    val rows = mutableListOf<PubChemSectionText>()

    fun walk(section: JsonObject, inheritedHeading: String?) {
        val heading = section.get("TOCHeading")?.asString ?: inheritedHeading ?: ""
        section.getAsJsonArray("Information")?.forEach { infoElement ->
            val info = runCatching { infoElement.asJsonObject }.getOrNull() ?: return@forEach
            val name = info.get("Name")?.asString
            val value = info.getAsJsonObject("Value") ?: return@forEach
            value.getAsJsonArray("StringWithMarkup")?.forEach { stringElement ->
                val text = runCatching { stringElement.asJsonObject.get("String")?.asString }.getOrNull()
                if (!text.isNullOrBlank()) rows.add(PubChemSectionText(heading, name, text.trim()))
            }
        }
        section.getAsJsonArray("Section")?.forEach { child ->
            runCatching { child.asJsonObject }.getOrNull()?.let { walk(it, heading) }
        }
    }

    sections.forEach { element ->
        runCatching { element.asJsonObject }.getOrNull()?.let { walk(it, null) }
    }
    return rows
}

fun buildPubChemClassificationTags(texts: List<PubChemSectionText>, maxTags: Int = 12): List<String> {
    val tags = linkedMapOf<String, String>()
    texts.forEach { row ->
        classificationCandidates(row.text).forEach { candidate ->
            val clean = cleanClassificationTag(candidate)
            if (clean.isNotBlank() && !isWeakClassificationTag(clean)) {
                tags.putIfAbsent(clean.lowercase(Locale.US), clean)
            }
        }
    }
    return tags.values.take(maxTags)
}

private fun classificationCandidates(raw: String): List<String> =
    raw
        .replace(", Non-Steroidal", " Non-Steroidal")
        .split("->", ";", ",")
        .map { it.trim() }

private fun cleanClassificationTag(raw: String): String =
    raw
        .replace(Regex("\\s+"), " ")
        .trim(' ', '.', ':')

private fun isWeakClassificationTag(tag: String): Boolean {
    val lower = tag.lowercase(Locale.US)
    return lower in setOf("other uses", "uses", "drugs", "human drugs", "animal drugs") ||
        lower.length < 3 ||
        lower.length > 44
}

fun buildPubChemUseEntries(texts: List<PubChemSectionText>, maxEntries: Int = 4): List<CompoundUseEntry> {
    val entries = linkedMapOf<String, CompoundUseEntry>()
    texts.forEach { row ->
        val text = cleanUseText(row.text)
        if (text.isBlank() || text.length < 8) return@forEach
        val label = useEntryLabel(row)
        entries.putIfAbsent(text.lowercase(Locale.US), CompoundUseEntry(label, text))
    }
    return entries.values.take(maxEntries)
}

fun compoundUseBulletLines(entries: List<CompoundUseEntry>): List<String> =
    entries
        .map { it.text.trim() }
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase(Locale.US) }
        .map { "• $it" }

private fun useEntryLabel(row: PubChemSectionText): String {
    val heading = row.heading.trim()
    val name = row.name?.trim().orEmpty()
    return when {
        heading.equals("Therapeutic Uses", ignoreCase = true) -> "Therapeutic uses"
        name.contains("Sources/Uses", ignoreCase = true) -> "Uses"
        name.isNotBlank() && !name.contains("EPA CPDat", ignoreCase = true) -> name
        heading.isNotBlank() -> heading.replaceFirstChar { it.uppercase(Locale.US) }
        else -> "Uses"
    }
}

private fun cleanUseText(raw: String): String =
    raw
        .replace(Regex("\\s*;\\s*\\[[^]]+]\\s*$"), "")
        .replace(Regex("\\s*\\[[^]]+]\\s*$"), "")
        .replace(Regex("\\s+"), " ")
        .trim(' ', '.', ';')
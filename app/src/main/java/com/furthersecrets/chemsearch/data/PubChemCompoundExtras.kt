package com.furthersecrets.chemsearch.data

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.util.Locale

fun buildAdvancedProperties(props: CompoundProperty): List<AdvancedPropertyRow> = buildList {
    props.xLogP?.let { add(AdvancedPropertyRow("XLogP", formatPropertyNumber(it))) }
    props.tpsa?.let { add(AdvancedPropertyRow("Topological polar surface area", "${formatPropertyNumber(it)} A^2")) }
    props.complexity?.let { add(AdvancedPropertyRow("Complexity", formatPropertyNumber(it))) }
    props.exactMass?.takeIf { it.isNotBlank() }?.let { add(AdvancedPropertyRow("Exact mass", "$it Da")) }
    props.monoisotopicMass?.takeIf { it.isNotBlank() }?.let { add(AdvancedPropertyRow("Monoisotopic mass", "$it Da")) }
    props.hBondDonorCount?.let { add(AdvancedPropertyRow("Hydrogen bond donors", it.toString())) }
    props.hBondAcceptorCount?.let { add(AdvancedPropertyRow("Hydrogen bond acceptors", it.toString())) }
    props.rotatableBondCount?.let { add(AdvancedPropertyRow("Rotatable bonds", it.toString())) }
    props.heavyAtomCount?.let { add(AdvancedPropertyRow("Heavy atom count", it.toString())) }
    props.covalentUnitCount?.let { add(AdvancedPropertyRow("Covalently bonded units", it.toString())) }
    props.isotopeAtomCount?.takeIf { it > 0 }?.let { add(AdvancedPropertyRow("Isotope atom count", it.toString())) }

    val stereoRows = buildStereoRows(props)
    addAll(stereoRows)

    props.volume3d?.let { add(AdvancedPropertyRow("3D volume", formatPropertyNumber(it))) }
    props.featureCount3d?.let { add(AdvancedPropertyRow("3D feature count", it.toString())) }
    props.featureAcceptorCount3d?.let { add(AdvancedPropertyRow("3D acceptor features", it.toString())) }
    props.featureDonorCount3d?.let { add(AdvancedPropertyRow("3D donor features", it.toString())) }
    props.featureAnionCount3d?.let { add(AdvancedPropertyRow("3D anion features", it.toString())) }
    props.featureCationCount3d?.let { add(AdvancedPropertyRow("3D cation features", it.toString())) }
    props.featureRingCount3d?.let { add(AdvancedPropertyRow("3D ring features", it.toString())) }
    props.featureHydrophobeCount3d?.let { add(AdvancedPropertyRow("3D hydrophobe features", it.toString())) }
    props.effectiveRotorCount3d?.let { add(AdvancedPropertyRow("Effective 3D rotors", formatPropertyNumber(it))) }
    props.conformerModelRmsd3d?.let { add(AdvancedPropertyRow("Conformer RMSD", formatPropertyNumber(it))) }
    props.conformerCount3d?.let { add(AdvancedPropertyRow("3D conformer count", it.toString())) }
}

private fun buildStereoRows(props: CompoundProperty): List<AdvancedPropertyRow> = buildList {
    val atomStereo = props.atomStereoCount
    val definedAtomStereo = props.definedAtomStereoCount
    val undefinedAtomStereo = props.undefinedAtomStereoCount
    if ((atomStereo ?: 0) > 0 || (definedAtomStereo ?: 0) > 0 || (undefinedAtomStereo ?: 0) > 0) {
        add(AdvancedPropertyRow("Atom stereocenters", stereoSummary(atomStereo, definedAtomStereo, undefinedAtomStereo)))
    }

    val bondStereo = props.bondStereoCount
    val definedBondStereo = props.definedBondStereoCount
    val undefinedBondStereo = props.undefinedBondStereoCount
    if ((bondStereo ?: 0) > 0 || (definedBondStereo ?: 0) > 0 || (undefinedBondStereo ?: 0) > 0) {
        add(AdvancedPropertyRow("Bond stereocenters", stereoSummary(bondStereo, definedBondStereo, undefinedBondStereo)))
    }
}

private fun stereoSummary(total: Int?, defined: Int?, undefined: Int?): String {
    val parts = buildList {
        total?.let { add("$it total") }
        defined?.takeIf { it > 0 }?.let { add("$it defined") }
        undefined?.takeIf { it > 0 }?.let { add("$it undefined") }
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

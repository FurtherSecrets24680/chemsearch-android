package com.furthersecrets.chemsearch.data

import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class SdfIdentifierCandidate(
    val label: String,
    val value: String
)

data class SdfLoadResult(
    val sdf: String,
    val source: SdfSource,
    val message: String? = null
)

private val sdfWhitespaceRegex = Regex("\\s+")

fun buildSdfIdentifierCandidates(
    smiles: String?,
    connectivitySmiles: String?,
    inchi: String?,
    inchiKey: String?
): List<SdfIdentifierCandidate> {
    val seen = mutableSetOf<String>()
    val candidates = mutableListOf<SdfIdentifierCandidate>()

    fun add(label: String, value: String?) {
        val clean = value?.trim().orEmpty()
        if (clean.isBlank()) return
        if (seen.add(clean.lowercase())) {
            candidates.add(SdfIdentifierCandidate(label, clean))
        }
    }

    add("InChI", inchi)
    add("InChIKey", inchiKey)
    add("SMILES", smiles)
    add("connectivity SMILES", connectivitySmiles)

    return candidates
}

suspend fun fetchGeneratedSdfFromIdentifiers(
    candidates: List<SdfIdentifierCandidate>,
    expectedFormula: String? = null
): SdfLoadResult? {
    val expectedCounts = formulaElementCountsOrNull(expectedFormula)
    for (candidate in candidates) {
        val sdf = runCatching { fetchCactusSdf(candidate.value) }.getOrNull()
        if (sdf != null && isUsableSdf(sdf) && sdfMatchesExpectedFormula(sdf, expectedCounts)) {
            return SdfLoadResult(
                sdf = sdf,
                source = SdfSource.GENERATED,
                message = "Generated estimate from ${candidate.label} using the NCI/CADD resolver."
            )
        }
    }
    return null
}

private fun fetchCactusSdf(identifier: String): String {
    val encoded = URLEncoder.encode(identifier, StandardCharsets.UTF_8.name())
        .replace("+", "%20")
    val request = Request.Builder()
        .url("https://cactus.nci.nih.gov/chemical/structure/$encoded/sdf")
        .header("User-Agent", "ChemSearch Android")
        .build()

    ApiClient.rawHttp.newCall(request).execute().use { response ->
        val body = response.body.string()
        if (!response.isSuccessful || body.isBlank()) {
            throw IOException("Generated SDF lookup failed: HTTP ${response.code}")
        }
        return body
    }
}

fun isUsableSdf(sdf: String): Boolean {
    val leading = sdf.trimStart()
    if (leading.isBlank()) return false
    if (leading.startsWith("<", ignoreCase = true)) return false
    if (leading.contains("<html", ignoreCase = true)) return false
    if (
        leading.contains("not found", ignoreCase = true) &&
        !leading.contains("V2000") &&
        !leading.contains("V3000")
    ) return false

    val normalized = sdf.replace("\r\n", "\n").replace('\r', '\n')
    val lines = normalized.lines()
    if (lines.size < 4) return false
    if (lines.any { it.contains("V3000") || it.trim().startsWith("M  V30") }) {
        return isUsableV3000Sdf(lines)
    }

    val atomCount = parseSdfAtomCount(lines[3]) ?: return false
    if (atomCount <= 0) return false

    var coordinateLines = 0
    for (index in 4 until (4 + atomCount).coerceAtMost(lines.size)) {
        if (hasSdfCoordinateLine(lines[index])) coordinateLines++
    }
    return coordinateLines == atomCount
}

private fun isUsableV3000Sdf(lines: List<String>): Boolean {
    val declaredAtomCount = lines
        .mapNotNull(::parseV3000CountsAtomCount)
        .firstOrNull()
        ?: return false
    if (declaredAtomCount <= 0) return false

    var inAtomBlock = false
    var coordinateLines = 0
    lines.forEach { rawLine ->
        val line = rawLine.trim()
        when {
            line.contains("BEGIN ATOM") -> {
                inAtomBlock = true
                return@forEach
            }
            line.contains("END ATOM") -> {
                inAtomBlock = false
                return@forEach
            }
        }

        if (inAtomBlock && parseV3000AtomElement(line).isNotBlank()) {
            coordinateLines++
        }
    }

    return coordinateLines == declaredAtomCount
}

private fun parseV3000CountsAtomCount(line: String): Int? {
    val parts = line.trim().split(sdfWhitespaceRegex)
    val countsIndex = parts.indexOfFirst { it.equals("COUNTS", ignoreCase = true) }
    if (countsIndex < 0) return null
    return parts.getOrNull(countsIndex + 1)?.toIntOrNull()
}

private fun parseSdfAtomCount(countsLine: String): Int? {
    val fixedWidthCount = countsLine.take(3).trim().toIntOrNull()
    if (fixedWidthCount != null) return fixedWidthCount

    return countsLine.trim()
        .split(sdfWhitespaceRegex)
        .firstOrNull()
        ?.toIntOrNull()
}

private fun hasSdfCoordinateLine(line: String): Boolean {
    if (line.length >= 34) {
        val x = line.substring(0, 10).trim().toDoubleOrNull()
        val y = line.substring(10, 20).trim().toDoubleOrNull()
        val z = line.substring(20, 30).trim().toDoubleOrNull()
        val element = line.substring(31, 34).trim()
        if (x != null && y != null && z != null && element.isNotBlank()) return true
    }

    val parts = line.trim().split(sdfWhitespaceRegex)
    return parts.size >= 4 &&
        parts[0].toDoubleOrNull() != null &&
        parts[1].toDoubleOrNull() != null &&
        parts[2].toDoubleOrNull() != null &&
        parts[3].isNotBlank()
}

private fun sdfMatchesExpectedFormula(sdf: String, expectedCounts: Map<String, Int>?): Boolean {
    if (expectedCounts == null) return true
    val sdfCounts = parseSdfElementCounts(sdf)
    return sdfCounts == expectedCounts
}

private fun parseSdfElementCounts(sdf: String): Map<String, Int> {
    val lines = sdf.replace("\r\n", "\n").replace('\r', '\n').lines()
    if (lines.any { it.contains("V3000") || it.trim().startsWith("M  V30") }) {
        return parseV3000ElementCounts(lines)
    }
    val atomCount = lines.getOrNull(3)?.let(::parseSdfAtomCount) ?: return emptyMap()
    val counts = linkedMapOf<String, Int>()

    for (index in 4 until (4 + atomCount).coerceAtMost(lines.size)) {
        val element = extractSdfElement(lines[index])
        if (element.isNotBlank()) {
            counts[element] = (counts[element] ?: 0) + 1
        }
    }

    return counts
}

private fun parseV3000ElementCounts(lines: List<String>): Map<String, Int> {
    val counts = linkedMapOf<String, Int>()
    var inAtomBlock = false

    lines.forEach { rawLine ->
        val line = rawLine.trim()
        when {
            line.contains("BEGIN ATOM") -> {
                inAtomBlock = true
                return@forEach
            }
            line.contains("END ATOM") -> {
                inAtomBlock = false
                return@forEach
            }
        }

        if (inAtomBlock) {
            val element = parseV3000AtomElement(line)
            if (element.isNotBlank()) counts[element] = (counts[element] ?: 0) + 1
        }
    }

    return counts
}

private fun parseV3000AtomElement(line: String): String {
    val payload = line.trim().removePrefix("M  V30").trim()
    val parts = payload.split(sdfWhitespaceRegex)
    if (parts.size < 5) return ""
    if (parts[0].toIntOrNull() == null) return ""
    if (parts[2].toDoubleOrNull() == null) return ""
    if (parts[3].toDoubleOrNull() == null) return ""
    if (parts[4].toDoubleOrNull() == null) return ""
    return normalizeFormulaElement(parts[1])
}

private fun extractSdfElement(line: String): String {
    val raw = if (line.length >= 34) {
        line.substring(31, 34).trim()
    } else {
        line.trim().split(sdfWhitespaceRegex).getOrNull(3).orEmpty()
    }
    return normalizeFormulaElement(raw)
}

private fun formulaElementCountsOrNull(formula: String?): Map<String, Int>? {
    val clean = formula?.trim().orEmpty()
    if (clean.isBlank()) return null
    return runCatching { parseFormulaElementCounts(clean) }.getOrNull()
}

private fun normalizeFormulaElement(raw: String): String {
    val letters = raw.filter { it.isLetter() }
    if (letters.isBlank()) return ""
    val lower = letters.lowercase()
    return lower.replaceFirstChar { it.uppercase() }
}

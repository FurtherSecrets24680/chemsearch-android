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
    val expectedCounts = parseFormulaElementCounts(expectedFormula)
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
    if (leading.contains("not found", ignoreCase = true) && !leading.contains("V2000")) return false

    val normalized = sdf.replace("\r\n", "\n").replace('\r', '\n')
    val lines = normalized.lines()
    if (lines.size < 4) return false

    val atomCount = parseSdfAtomCount(lines[3]) ?: return false
    if (atomCount <= 0) return false

    var coordinateLines = 0
    for (index in 4 until (4 + atomCount).coerceAtMost(lines.size)) {
        if (hasSdfCoordinateLine(lines[index])) coordinateLines++
    }
    return coordinateLines == atomCount
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

private fun extractSdfElement(line: String): String {
    val raw = if (line.length >= 34) {
        line.substring(31, 34).trim()
    } else {
        line.trim().split(sdfWhitespaceRegex).getOrNull(3).orEmpty()
    }
    return normalizeFormulaElement(raw)
}

private fun parseFormulaElementCounts(formula: String?): Map<String, Int>? {
    val clean = formula?.trim().orEmpty()
    if (clean.isBlank()) return null

    val stack = mutableListOf(linkedMapOf<String, Int>())
    var i = 0

    fun addToCurrent(element: String, count: Int) {
        val current = stack.last()
        current[element] = (current[element] ?: 0) + count
    }

    while (i < clean.length) {
        val ch = clean[i]
        when {
            ch == '(' || ch == '[' -> {
                stack.add(linkedMapOf())
                i++
            }

            ch == ')' || ch == ']' -> {
                i++
                val multiplier = readFormulaNumber(clean, i).also { i = it.nextIndex }.value
                val group = if (stack.size > 1) stack.removeAt(stack.lastIndex) else linkedMapOf()
                group.forEach { (element, count) -> addToCurrent(element, count * multiplier) }
            }

            ch.isUpperCase() -> {
                val start = i
                i++
                if (i < clean.length && clean[i].isLowerCase()) i++
                val element = normalizeFormulaElement(clean.substring(start, i))
                val number = readFormulaNumber(clean, i).also { i = it.nextIndex }.value
                if (element.isNotBlank()) addToCurrent(element, number)
            }

            else -> i++
        }
    }

    if (stack.size != 1) return null
    return stack.single().takeIf { it.isNotEmpty() }
}

private data class FormulaNumber(val value: Int, val nextIndex: Int)

private fun readFormulaNumber(formula: String, startIndex: Int): FormulaNumber {
    var i = startIndex
    while (i < formula.length && formula[i].isDigit()) i++
    val value = formula.substring(startIndex, i).toIntOrNull()?.takeIf { it > 0 } ?: 1
    return FormulaNumber(value, i)
}

private fun normalizeFormulaElement(raw: String): String {
    val letters = raw.filter { it.isLetter() }
    if (letters.isBlank()) return ""
    val lower = letters.lowercase()
    return lower.replaceFirstChar { it.uppercase() }
}

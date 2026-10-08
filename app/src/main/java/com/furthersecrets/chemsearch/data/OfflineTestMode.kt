package com.furthersecrets.chemsearch.data

import android.content.SharedPreferences
import com.furthersecrets.chemsearch.ui.DebugLog
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import kotlin.random.Random

/**
 * Offline Test Mode: a developer-only switch that makes the entire app behave
 * as if the network exists but only returns deterministic dummy data. Every
 * network-backed path (search, CID lookup, autocomplete, isomers, advanced
 * search, structure search, synonyms, descriptions, safety, PubChem context,
 * correction suggestions, random compound) is served from here, so the whole
 * UI can be exercised on a plane, in CI, or with flaky internet.
 *
 * The mode is intentionally a process-global singleton read directly at the
 * repository seam: this keeps the interception in ONE place instead of
 * scattering `if (offline)` through every ViewModel function.
 */
object OfflineTestMode {

    /** Master switch. When false, the app behaves exactly as before. */
    @Volatile
    var enabled: Boolean = false
        private set

    /** Simulated network latency in milliseconds added to every dummy call. */
    @Volatile
    var latencyMs: Long = 0L
        private set

    /** 0..100: percentage of dummy calls that fail with a synthetic error. */
    @Volatile
    var errorRatePercent: Int = 0
        private set

    /**
     * Which failure to simulate when the error rate triggers. Cycles through
     * representative kinds so every color-coded error surface can be tested.
     */
    enum class SimulatedFailure { NONE, NOT_FOUND, THROTTLED, TIMEOUT, BAD_REQUEST, NETWORK, SERVER }

    @Volatile
    var simulatedFailure: SimulatedFailure = SimulatedFailure.NONE
        private set

    /** CIDs handed out for name searches; stable per query via hashing. */
    private val pool: List<Long> = listOf(
        2244L,   // aspirin (anchor: real well-known CID for debugging)
        1983L,   // acetaminophen
        5329102L, // glucose-ish
        702L,    // ethanol
        6342L,   // benzene-ish
        1140L,   // toluene-ish
        312L,    // water region
        241L     // benzaldehyde region
    )

    /** Names handed to the UI in dummy results. */
    internal val dummyNames: List<String> = listOf(
        "Testolone", "Samplecillin", "Demozene", "Mockanol",
        "Pseudoxide", "Fictisalol", "Placeholderine", "Synthetol"
    )

    fun configure(prefs: SharedPreferences) {
        enabled = prefs.getBoolean(PREF_ENABLED, false)
        latencyMs = prefs.getLong(PREF_LATENCY, 0L).coerceIn(0L, 5000L)
        errorRatePercent = prefs.getInt(PREF_ERROR_RATE, 0).coerceIn(0, 100)
        simulatedFailure = runCatching {
            SimulatedFailure.valueOf(prefs.getString(PREF_FAILURE, SimulatedFailure.NONE.name) ?: SimulatedFailure.NONE.name)
        }.getOrDefault(SimulatedFailure.NONE)
        if (enabled) {
            DebugLog.i("ChemSearch", "Offline Test Mode ACTIVE (latency=${latencyMs}ms, errorRate=$errorRatePercent%, failure=$simulatedFailure)")
        }
    }

    fun setEnabled(prefs: SharedPreferences, value: Boolean) {
        enabled = value
        prefs.edit().putBoolean(PREF_ENABLED, value).apply()
        DebugLog.i("ChemSearch", if (value) "Offline Test Mode enabled" else "Offline Test Mode disabled")
    }

    fun setLatency(prefs: SharedPreferences, ms: Long) {
        latencyMs = ms.coerceIn(0L, 5000L)
        prefs.edit().putLong(PREF_LATENCY, latencyMs).apply()
    }

    fun setErrorRate(prefs: SharedPreferences, percent: Int) {
        errorRatePercent = percent.coerceIn(0, 100)
        prefs.edit().putInt(PREF_ERROR_RATE, errorRatePercent).apply()
    }

    fun setSimulatedFailure(prefs: SharedPreferences, failure: SimulatedFailure) {
        simulatedFailure = failure
        prefs.edit().putString(PREF_FAILURE, failure.name).apply()
    }

    private const val BASE_DELAY_MS = 500L
    private const val JITTER_MS = 250L

    private const val PREF_ENABLED = "offline_test_mode"
    private const val PREF_LATENCY = "offline_test_mode_latency"
    private const val PREF_ERROR_RATE = "offline_test_mode_error_rate"
    private const val PREF_FAILURE = "offline_test_mode_failure"

    /** Deterministic CID for any query text (stable across runs & devices). */
    fun cidFor(query: String): Long {
        if (query.isBlank()) return pool.first()
        val hash = query.lowercase().fold(17) { acc, c -> (acc * 31 + c.code) and 0x7FFFFFFF }
        return pool[hash % pool.size] + (hash % 1000L)
    }

    fun nameFor(cid: Long): String = dummyNames[(cid % dummyNames.size).toInt()]

    /** Deterministic molecular weight for a CID: 58.0 .. 958.0 g/mol. */
    fun weightFor(cid: Long): String = String.format(java.util.Locale.US, "%.2f", 58.0 + (cid % 9000) / 10.0)

    /** Deterministic formula for a CID (plausible C/H/O/N shapes). */
    fun formulaFor(cid: Long): String {
        val c = 2 + (cid % 24).toInt()
        val h = 4 + (cid % 30).toInt() * 2
        val o = (cid % 5).toInt()
        val n = (cid % 3).toInt()
        return buildString {
            append("C").append(c).append("H").append(h)
            if (n > 0) append("N").append(n)
            if (o > 0) append("O").append(o)
        }
    }

    fun smilesFor(cid: Long): String = "CCC(CC)COC(=O)C${(cid % 9) + 1}N"

    fun iupacFor(cid: Long): String =
        "2-(acetyloxy)-${(cid % 7) + 3}-methylbenzoic acid (test-${cid})"

    fun descriptionFor(cid: Long, query: String): String =
        "Offline test record for \"$query\" (CID $cid). This description is generated " +
            "locally by Offline Test Mode to exercise typography, wrapping and layout " +
            "without any network. Length is varied deterministically: " +
            List((cid % 3).toInt() + 1) { "segment ${it + 1} adds padding text for scroll testing." }
                .joinToString(" ")

    /**
     * Applies the configured latency and throws a synthetic exception when the
     * error-rate dice trigger or an explicit failure is requested. Callers
     * wrap their normal `try/catch` around the real work after calling this.
     */
    suspend fun simulateNetworkProbe(query: String) {
        if (!enabled) return
        // Base handoff delay so loading spinners and skeletons are always
        // visible in test mode, even with the latency slider at zero.
        delay(BASE_DELAY_MS + Random.nextLong(0L, JITTER_MS))
        if (latencyMs > 0) delay(latencyMs)
        if (simulatedFailure != SimulatedFailure.NONE) throw failureException(query, simulatedFailure)
        val roll = Random.nextInt(100)
        if (roll < errorRatePercent) {
            // Cycle through failures so each roll exercises a different surface.
            val cycle = SimulatedFailure.entries.filter { it != SimulatedFailure.NONE }
            val pick = cycle[roll % cycle.size]
            throw failureException(query, pick)
        }
    }

    internal fun failureException(query: String, failure: SimulatedFailure): Exception = when (failure) {
        SimulatedFailure.NONE -> IllegalStateException("no failure")
        SimulatedFailure.NOT_FOUND ->
            java.util.NoSuchElementException("chemical not found: $query (simulated)")
        SimulatedFailure.THROTTLED ->
            retrofit2.HttpException(
                retrofit2.Response.error<Any>(
                    503,
                    """{"Fault":{"Code":"PUGREST.ServerBusy","Message":"Server Busy","Details":["simulated throttle"]}}"""
                        .toResponseBody("application/json".toMediaType())
                )
            )
        SimulatedFailure.TIMEOUT ->
            java.net.SocketTimeoutException("simulated timeout for $query")
        SimulatedFailure.BAD_REQUEST ->
            retrofit2.HttpException(
                retrofit2.Response.error<Any>(
                    400,
                    """{"Fault":{"Code":"PUGREST.BadRequest","Message":"Bad Request","Details":["simulated bad request"]}}"""
                        .toResponseBody("application/json".toMediaType())
                )
            )
        SimulatedFailure.NETWORK ->
            java.net.UnknownHostException("simulated offline network for $query")
        SimulatedFailure.SERVER ->
            retrofit2.HttpException(
                retrofit2.Response.error<Any>(
                    502,
                    """{"Fault":{"Code":"PUGREST.Internal","Message":"Internal Server Error","Details":["simulated server error"]}}"""
                        .toResponseBody("application/json".toMediaType())
                )
            )
    }
}

/**
 * Offline-aware wrapper for [CompoundDataRepository]: returns dummy data
 * instead of performing network calls when [OfflineTestMode.enabled] is set.
 * One seam intercepts every search surface; the ViewModel needs no changes
 * beyond calling this instead of the raw repository.
 */
class OfflineTestIntercept(
    private val repository: CompoundDataRepository
) {
    val isEnabled: Boolean get() = OfflineTestMode.enabled

    suspend fun fetchSynonyms(cid: Long): List<String> {
        OfflineTestMode.simulateNetworkProbe("synonyms/$cid")
        val base = listOf(
            "Test Synonym ${cid % 97}", "Dummy Alias ${cid % 89}", "Sample Trade Name ${cid % 71}",
            "Mock Chemical ${cid % 61}", "Testolone (test)"
        )
        // One deterministic CAS-like number so the CAS row has content.
        return base + listOf("1%02d-45-6".format((cid % 90) + 10))
    }

    suspend fun fetchAdvancedProperties(cid: Long): List<AdvancedPropertyRow> {
        OfflineTestMode.simulateNetworkProbe("props/$cid")
        val w = OfflineTestMode.weightFor(cid).toDoubleOrNull() ?: 300.0
        return listOf(
            AdvancedPropertyRow("XLogP", String.format(java.util.Locale.US, "%.1f", (cid % 50) / 10.0)),
            AdvancedPropertyRow("TPSA", "${40 + (cid % 120)} A^2"),
            AdvancedPropertyRow("Complexity", "${100 + (cid % 400)}"),
            AdvancedPropertyRow("Exact Mass", "$w Da"),
            AdvancedPropertyRow("H-Bond Donor", "${(cid % 4)}"),
            AdvancedPropertyRow("H-Bond Acceptor", "${(cid % 8) + 1}"),
            AdvancedPropertyRow("Rotatable Bonds", "${(cid % 6)}"),
            AdvancedPropertyRow("Heavy Atom Count", "${10 + (cid % 30)}")
        )
    }

    suspend fun fetchPubChemCompoundContext(cid: Long): PubChemCompoundContext {
        OfflineTestMode.simulateNetworkProbe("context/$cid")
        return PubChemCompoundContext(
            classificationTags = listOf(
                "Test Category ${cid % 5 + 1}",
                if (cid % 2 == 0L) "Small Molecule (simulated)" else "Organic Compound (simulated)"
            ),
            useEntries = listOf(
                CompoundUseEntry("Simulated Use", "Placeholder therapeutic use for CID $cid."),
                CompoundUseEntry("Test Note", "Classification tags render as chips; verify wrapping here.")
            )
        )
    }

    suspend fun fetchFormulaCids(formula: String, maxRecords: Int): List<Long> {
        OfflineTestMode.simulateNetworkProbe("isomers/$formula")
        val count = minOf(maxRecords, 6 + (formula.hashCode().toLong().mod(9L)).toInt())
        return (1..count).map { OfflineTestMode.cidFor("$formula#$it") }
    }

    suspend fun resolveAdvancedSearchCids(filters: AdvancedSearchFilters): List<Long> {
        OfflineTestMode.simulateNetworkProbe("adv/${filters.type}/$filters.query")
        val count = minOf(filters.maxRecords, 5 + (filters.query.hashCode().toLong().mod(8L)).toInt())
        return (1..count).map { OfflineTestMode.cidFor("${filters.type}:$filters.query#$it") }
    }

    suspend fun loadStructurePropertiesForCids(cids: List<Long>): Map<Long, CompoundProperty> {
        OfflineTestMode.simulateNetworkProbe("structProps/${cids.size}")
        return cids.associateWith { cid -> dummyProperty(cid) }
    }

    fun dummyProperty(cid: Long): CompoundProperty = CompoundProperty(
        cid = cid,
        title = OfflineTestMode.nameFor(cid),
        molecularFormula = OfflineTestMode.formulaFor(cid),
        molecularWeight = OfflineTestMode.weightFor(cid),
        iupacName = OfflineTestMode.iupacFor(cid),
        smiles = OfflineTestMode.smilesFor(cid),
        charge = 0,
        covalentUnitCount = (cid % 20).toInt() + 1
    )

    suspend fun fetchSearchCorrectionSuggestions(query: String): List<String> {
        OfflineTestMode.simulateNetworkProbe("corrections/$query")
        val stem = query.take(4).ifBlank { "demo" }
        return listOf("$stem-ol", "$stem-one", "test$stem", "demo$stem")
    }
}

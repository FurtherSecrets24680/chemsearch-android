package com.furthersecrets.chemsearch.data

import androidx.annotation.StringRes
import com.furthersecrets.chemsearch.R
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Classification of why a search/network request failed. Drives both the
 * explanation text and the color coding of the error surface:
 *  - NOT_FOUND: the source simply has no record for this query (calm/info color)
 *  - THROTTLED / TIMEOUT / BAD_REQUEST: retryable request problems (warning color)
 *  - NETWORK / SERVER: connection or upstream failure (error color)
 *  - OTHER: anything unexpected (error color)
 */
enum class SearchErrorKind {
    NOT_FOUND,
    THROTTLED,
    TIMEOUT,
    BAD_REQUEST,
    NETWORK,
    SERVER,
    OTHER
}

/**
 * A reasoned error explanation: which localized message to show, its format
 * arguments (query text, HTTP code, PubChem fault message), and the severity
 * kind used for color coding.
 */
data class SearchErrorPresentation(
    @StringRes val messageRes: Int,
    val args: List<String> = emptyList(),
    val kind: SearchErrorKind = SearchErrorKind.OTHER
)

/**
 * Decodes the "invisible" failure signals from PubChem PUG REST into human
 * reasoning. PubChem answers failures with an HTTP status plus a JSON Fault
 * body, e.g.:
 *   { "Fault": { "Code": "PUGREST.NotFound", "Message": "No CID found",
 *                "Details": ["No CID found that matches the given name"] } }
 *
 * Known codes: PUGREST.NotFound (404), PUGREST.BadRequest (400),
 * PUGREST.ServerBusy (503, throttling), PUGREST.Timeout (504).
 */
object SearchErrorResolver {

    /** Extracts the PubChem Fault code/message from an error body, if present. */
    fun parseFault(body: String?): Pair<String, String> {
        if (body.isNullOrBlank()) return "" to ""
        val code = Regex("\"Code\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1).orEmpty()
        val message = Regex("\"Message\"\\s*:\\s*\"([^\"]*)\"").find(body)?.groupValues?.get(1).orEmpty()
        return code to message
    }

    fun fromThrowable(throwable: Throwable, query: String? = null): SearchErrorPresentation = when (throwable) {
        is HttpException -> fromHttp(throwable, query)
        is UnknownHostException, is SocketTimeoutException -> SearchErrorPresentation(
            messageRes = R.string.ui_error_search_network,
            kind = SearchErrorKind.NETWORK
        )
        is IOException -> SearchErrorPresentation(
            messageRes = R.string.ui_error_search_network,
            kind = SearchErrorKind.NETWORK
        )
        else -> SearchErrorPresentation(
            messageRes = R.string.ui_error_search_unknown,
            args = listOf(throwable.message?.take(120).orEmpty()),
            kind = SearchErrorKind.OTHER
        )
    }

    private fun fromHttp(e: HttpException, query: String?): SearchErrorPresentation {
        val (faultCode, faultMessage) = parseFault(
            runCatching { e.response()?.errorBody()?.string() }.getOrNull()
        )
        val status = e.code()
        return when {
            status == 404 || faultCode.contains("NotFound", ignoreCase = true) -> SearchErrorPresentation(
                messageRes = R.string.ui_error_search_not_found_s,
                args = listOf(query?.take(80).orEmpty()),
                kind = SearchErrorKind.NOT_FOUND
            )
            status == 503 || faultCode.contains("ServerBusy", ignoreCase = true) -> SearchErrorPresentation(
                messageRes = R.string.ui_error_search_throttled,
                kind = SearchErrorKind.THROTTLED
            )
            status == 504 || faultCode.contains("Timeout", ignoreCase = true) -> SearchErrorPresentation(
                messageRes = R.string.ui_error_search_timeout,
                kind = SearchErrorKind.TIMEOUT
            )
            status == 400 || faultCode.contains("BadRequest", ignoreCase = true) -> SearchErrorPresentation(
                messageRes = if (faultMessage.isBlank()) R.string.ui_error_search_bad_request
                else R.string.ui_error_search_bad_request_detail,
                args = if (faultMessage.isBlank()) emptyList() else listOf(faultMessage.take(120)),
                kind = SearchErrorKind.BAD_REQUEST
            )
            status in 500..599 -> SearchErrorPresentation(
                messageRes = R.string.ui_error_search_server,
                args = listOf(status.toString()),
                kind = SearchErrorKind.SERVER
            )
            else -> SearchErrorPresentation(
                messageRes = R.string.ui_error_search_unknown,
                args = listOf("HTTP $status ${faultMessage.takeIf { it.isNotBlank() } ?: faultCode}".take(120)),
                kind = SearchErrorKind.SERVER
            )
        }
    }
}

package com.furthersecrets.chemsearch.data

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class SearchErrorResolverTest {

    private fun faultBody(code: String, message: String): String =
        """{"Fault":{"Code":"$code","Message":"$message","Details":["$message"]}}"""

    private fun httpException(code: Int, body: String): HttpException {
        val response = retrofit2.Response.error<Any>(
            code,
            body.toResponseBody("application/json".toMediaTypeOrNull())
        )
        return HttpException(response)
    }

    @Test
    fun parseFaultExtractsCodeAndMessage() {
        val (code, message) = SearchErrorResolver.parseFault(
            faultBody("PUGREST.NotFound", "No CID found")
        )
        assertEquals("PUGREST.NotFound", code)
        assertEquals("No CID found", message)
    }

    @Test
    fun parseFaultToleratesBlankOrGarbageBodies() {
        assertEquals("" to "", SearchErrorResolver.parseFault(null))
        assertEquals("" to "", SearchErrorResolver.parseFault(""))
        assertEquals("" to "", SearchErrorResolver.parseFault("not json at all"))
    }

    @Test
    fun http404WithFaultClassifiesAsNotFound() {
        val presentation = SearchErrorResolver.fromThrowable(
            httpException(404, faultBody("PUGREST.NotFound", "No CID found")),
            query = "xyzabcpotion"
        )
        assertEquals(SearchErrorKind.NOT_FOUND, presentation.kind)
        assertEquals(listOf("xyzabcpotion"), presentation.args)
    }

    @Test
    fun plainHttp404WithoutFaultBodyStillClassifiesAsNotFound() {
        val presentation = SearchErrorResolver.fromThrowable(
            httpException(404, "server said no"),
            query = "benzene"
        )
        assertEquals(SearchErrorKind.NOT_FOUND, presentation.kind)
    }

    @Test
    fun http503ClassifiesAsThrottled() {
        val presentation = SearchErrorResolver.fromThrowable(
            httpException(503, faultBody("PUGREST.ServerBusy", "Server busy")),
            query = "water"
        )
        assertEquals(SearchErrorKind.THROTTLED, presentation.kind)
    }

    @Test
    fun http504ClassifiesAsTimeout() {
        val presentation = SearchErrorResolver.fromThrowable(
            httpException(504, faultBody("PUGREST.Timeout", "Timed out")),
            query = "water"
        )
        assertEquals(SearchErrorKind.TIMEOUT, presentation.kind)
    }

    @Test
    fun http400CarriesPubChemDetailMessage() {
        val presentation = SearchErrorResolver.fromThrowable(
            httpException(400, faultBody("PUGREST.BadRequest", "Invalid input")),
            query = "water"
        )
        assertEquals(SearchErrorKind.BAD_REQUEST, presentation.kind)
        assertTrue(presentation.args.single().contains("Invalid input"))
    }

    @Test
    fun http500ClassifiesAsServerWithStatusCodeArg() {
        val presentation = SearchErrorResolver.fromThrowable(
            httpException(502, "bad gateway"),
            query = "water"
        )
        assertEquals(SearchErrorKind.SERVER, presentation.kind)
        assertEquals(listOf("502"), presentation.args)
    }

    @Test
    fun connectionErrorsClassifyAsNetwork() {
        val cases = listOf<Throwable>(
            UnknownHostException("Unable to resolve host"),
            SocketTimeoutException("timed out"),
            IOException("connection reset")
        )
        cases.forEach { throwable ->
            assertEquals(
                "$throwable should be NETWORK",
                SearchErrorKind.NETWORK,
                SearchErrorResolver.fromThrowable(throwable, query = "water").kind
            )
        }
    }

    @Test
    fun unexpectedErrorsClassifyAsOther() {
        val presentation = SearchErrorResolver.fromThrowable(
            IllegalStateException("boom"),
            query = "water"
        )
        assertEquals(SearchErrorKind.OTHER, presentation.kind)
        assertTrue(presentation.args.single().contains("boom"))
    }
}

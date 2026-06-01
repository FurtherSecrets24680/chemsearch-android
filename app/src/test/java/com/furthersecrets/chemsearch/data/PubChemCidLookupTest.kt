package com.furthersecrets.chemsearch.data

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Test

class PubChemCidLookupTest {
    @Test
    fun waitingFormulaResponseCarriesListKeyForPolling() {
        val json = """
            {
              "Waiting": {
                "ListKey": "4199336941665111481",
                "Message": "Your request is running"
              }
            }
        """.trimIndent()

        val response = Gson().fromJson(json, CidResponse::class.java)
        val status = pubChemCidLookupStatus(response)

        assertEquals(PubChemCidLookupStatus.Waiting("4199336941665111481"), status)
    }

    @Test
    fun readyFormulaResponseCarriesCids() {
        val json = """
            {
              "IdentifierList": {
                "CID": [241, 69402, 71601]
              }
            }
        """.trimIndent()

        val response = Gson().fromJson(json, CidResponse::class.java)
        val status = pubChemCidLookupStatus(response)

        assertEquals(PubChemCidLookupStatus.Ready(listOf(241L, 69402L, 71601L)), status)
    }
}

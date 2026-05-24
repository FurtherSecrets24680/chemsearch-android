package com.furthersecrets.chemsearch.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReactionBalancerTest {
    private val gson = Gson()

    data class ReactionFixture(
        val equation: String,
        val expected: String,
        val source: String,
        val notes: String
    )

    @Test
    fun balancesReactionFixtures() {
        loadFixtures().forEach { fixture ->
            val result = balanceChemicalReaction(fixture.equation)

            assertNull("${fixture.equation}: ${fixture.notes}", result.error)
            assertEquals(fixture.expected, result.displayEquation())
            assertTrue(result.verificationRows.all { it.reactantCount == it.productCount })
        }
    }

    @Test
    fun returnsStructuredErrorForMalformedEquation() {
        val result = balanceChemicalReaction("H2 + O2")

        assertEquals(ReactionBalanceErrorCode.MISSING_ARROW, result.error?.code)
    }

    private fun loadFixtures(): List<ReactionFixture> {
        val json = requireNotNull(javaClass.classLoader?.getResource("chemistry-fixtures/reaction-balancing.json"))
            .readText()
        val type = object : TypeToken<List<ReactionFixture>>() {}.type
        return gson.fromJson(json, type)
    }
}

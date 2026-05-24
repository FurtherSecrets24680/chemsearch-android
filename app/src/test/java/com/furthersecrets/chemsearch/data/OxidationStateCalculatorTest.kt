package com.furthersecrets.chemsearch.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OxidationStateCalculatorTest {
    private val gson = Gson()

    data class OxidationFixture(
        val formula: String,
        val charge: Int,
        val expected: Map<String, String>,
        val source: String,
        val notes: String
    )

    @Test
    fun solvesOxidationStateFixtures() {
        loadFixtures().forEach { fixture ->
            val result = calculateOxidationStates(fixture.formula, fixture.charge)

            assertNull("${fixture.formula}: ${fixture.notes}", result.error)
            assertEquals(fixture.expected, result.states.toMap())
        }
    }

    @Test
    fun solvesChlorineAsPositiveInHypochlorousAcid() {
        val result = calculateOxidationStates("HOCl", 0)

        assertNull(result.error)
        assertEquals("+1", result.states.toMap()["H"])
        assertEquals("-2", result.states.toMap()["O"])
        assertEquals("+1", result.states.toMap()["Cl"])
    }

    @Test
    fun keepsChlorineNegativeInSimpleChlorides() {
        val result = calculateOxidationStates("NaCl", 0)

        assertNull(result.error)
        assertEquals("+1", result.states.toMap()["Na"])
        assertEquals("-1", result.states.toMap()["Cl"])
    }

    private fun loadFixtures(): List<OxidationFixture> {
        val json = requireNotNull(javaClass.classLoader?.getResource("chemistry-fixtures/oxidation-states.json"))
            .readText()
        val type = object : TypeToken<List<OxidationFixture>>() {}.type
        return gson.fromJson(json, type)
    }
}

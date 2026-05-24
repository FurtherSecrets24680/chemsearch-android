package com.furthersecrets.chemsearch.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MolarMassCalculatorTest {
    private val gson = Gson()

    data class MolarMassFixture(
        val formula: String,
        val expected: Double,
        val tolerance: Double,
        val source: String,
        val notes: String
    )

    @Test
    fun calculatesMolarMassFixtures() {
        loadFixtures().forEach { fixture ->
            val result = calculateMolarMass(fixture.formula)

            assertTrue("${fixture.formula}: ${fixture.notes}", result.error == null)
            assertEquals(fixture.expected, result.molarMass, fixture.tolerance)
        }
    }

    @Test
    fun reportsUnknownElements() {
        val result = calculateMolarMass("Xx2")

        assertEquals("Unknown element(s): Xx", result.error)
    }

    @Test
    fun calculatesEmpiricalFormulaWithSharedParser() {
        assertEquals("CH2O", calculateEmpiricalFormula("C6H12O6"))
    }

    private fun loadFixtures(): List<MolarMassFixture> {
        val json = requireNotNull(javaClass.classLoader?.getResource("chemistry-fixtures/molar-masses.json"))
            .readText()
        val type = object : TypeToken<List<MolarMassFixture>>() {}.type
        return gson.fromJson(json, type)
    }
}

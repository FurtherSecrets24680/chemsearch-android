package com.furthersecrets.chemsearch.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FormulaParserTest {
    private val gson = Gson()

    data class FormulaFixture(
        val input: String,
        val expected: Map<String, Int>,
        val source: String,
        val notes: String
    )

    @Test
    fun parsesFormulaFixtures() {
        loadFormulaFixtures().forEach { fixture ->
            val result = parseFormula(fixture.input)

            assertTrue("${fixture.input}: ${fixture.notes}", result is FormulaParseResult.Success)
            assertEquals(fixture.expected, (result as FormulaParseResult.Success).composition.elements)
        }
    }

    @Test
    fun returnsStructuredErrorForUnclosedGroup() {
        val result = parseFormula("Fe(OH")

        assertTrue(result is FormulaParseResult.Failure)
        val error = (result as FormulaParseResult.Failure).error
        assertEquals(FormulaParseErrorCode.UNCLOSED_GROUP, error.code)
        assertTrue(error.message.isNotBlank())
    }

    @Test
    fun formatsPubChemHillFormulasInConventionalInorganicOrder() {
        assertEquals("NaCl", formatConventionalFormula("ClNa"))
        assertEquals("H2SO4", formatConventionalFormula("H2O4S"))
        assertEquals("NaOH", formatConventionalFormula("HNaO"))
        assertEquals("Na2SO4", formatConventionalFormula("Na2O4S"))
        assertEquals("NH3", formatConventionalFormula("H3N"))
    }

    @Test
    fun keepsOrganicHillFormulasStable() {
        assertEquals("CH4O", formatConventionalFormula("CH4O"))
        assertEquals("C153H225N43O49S", formatConventionalFormula("C153H225N43O49S"))
    }

    private fun loadFormulaFixtures(): List<FormulaFixture> {
        val json = requireNotNull(javaClass.classLoader?.getResource("chemistry-fixtures/formula-parsing.json"))
            .readText()
        val type = object : TypeToken<List<FormulaFixture>>() {}.type
        return gson.fromJson(json, type)
    }
}

package com.furthersecrets.chemsearch.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhPohCalculatorTest {
    private val gson = Gson()

    data class PhPohFixture(
        val input: String,
        val type: PhPohInputType,
        val expectedPh: Double,
        val expectedPoh: Double,
        val source: String,
        val notes: String
    )

    @Test
    fun calculatesPhPohFixturesAtTwentyFiveCelsius() {
        loadFixtures().forEach { fixture ->
            val result = calculatePhPoh(fixture.input, fixture.type)

            assertEquals("${fixture.input}: ${fixture.notes}", fixture.expectedPh, result.ph, 0.000001)
            assertEquals("${fixture.input}: ${fixture.notes}", fixture.expectedPoh, result.poh, 0.000001)
            assertEquals("Assumes 25 C and pKw = 14.00", result.assumption)
        }
    }

    @Test
    fun calculatesFromPh() {
        val result = calculatePhPoh("3", PhPohInputType.PH)

        assertEquals(3.0, result.ph, 0.000001)
        assertEquals(11.0, result.poh, 0.000001)
        assertEquals(1e-3, result.hydrogenConcentration, 0.0000001)
        assertEquals("Acidic", result.classification)
    }

    @Test
    fun calculatesFromHydroxideConcentration() {
        val result = calculatePhPoh("1e-5", PhPohInputType.HYDROXIDE)

        assertEquals(5.0, result.poh, 0.000001)
        assertEquals(9.0, result.ph, 0.000001)
        assertEquals(1e-5, result.hydroxideConcentration, 0.0000001)
        assertEquals("Basic", result.classification)
    }

    @Test
    fun classifiesNeutralAtSeven() {
        val result = calculatePhPoh("7", PhPohInputType.PH)

        assertEquals("Neutral", result.classification)
    }

    @Test
    fun rejectsNonPositiveConcentrations() {
        val result = runCatching { calculatePhPoh("0", PhPohInputType.HYDROGEN) }

        assertTrue(result.isFailure)
    }

    private fun loadFixtures(): List<PhPohFixture> {
        val json = requireNotNull(javaClass.classLoader?.getResource("chemistry-fixtures/ph-poh.json"))
            .readText()
        val type = object : TypeToken<List<PhPohFixture>>() {}.type
        return gson.fromJson(json, type)
    }
}

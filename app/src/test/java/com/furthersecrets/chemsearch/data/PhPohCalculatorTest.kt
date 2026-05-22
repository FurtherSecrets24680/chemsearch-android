package com.furthersecrets.chemsearch.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhPohCalculatorTest {
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
}

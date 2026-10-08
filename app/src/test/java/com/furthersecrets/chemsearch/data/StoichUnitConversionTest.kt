package com.furthersecrets.chemsearch.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StoichUnitConversionTest {

    private val waterMolarMass = 18.015

    @Test
    fun `grams to moles uses molar mass`() {
        val moles = StoichiometryEngine.amountToMoles(18.015, StoichiometryEngine.StoichUnit.GRAMS, waterMolarMass, 22.414)
        assertEquals(1.0, moles!!, 1e-9)
    }

    @Test
    fun `grams conversion without molar mass is null`() {
        assertNull(StoichiometryEngine.amountToMoles(5.0, StoichiometryEngine.StoichUnit.GRAMS, null, 22.414))
    }

    @Test
    fun `moles to grams round trip is lossless`() {
        val moles = 2.5
        val grams = StoichiometryEngine.molesToAmount(moles, StoichiometryEngine.StoichUnit.GRAMS, waterMolarMass, 22.414)
        val back = StoichiometryEngine.amountToMoles(grams!!, StoichiometryEngine.StoichUnit.GRAMS, waterMolarMass, 22.414)
        assertEquals(moles, back!!, 1e-9)
    }

    @Test
    fun `millimoles to kilograms conversion`() {
        val moles = StoichiometryEngine.amountToMoles(500.0, StoichiometryEngine.StoichUnit.MILLIMOLES, null, 22.414)
        assertEquals(0.5, moles!!, 1e-9)
        val kg = StoichiometryEngine.molesToAmount(1.0, StoichiometryEngine.StoichUnit.KILOGRAMS, waterMolarMass, 22.414)
        assertEquals(0.018015, kg!!, 1e-9)
    }

    @Test
    fun `gas volume conversions use molar volume`() {
        val moles = StoichiometryEngine.amountToMoles(22.414, StoichiometryEngine.StoichUnit.LITERS_GAS, null, 22.414)
        assertEquals(1.0, moles!!, 1e-9)
        val ml = StoichiometryEngine.amountToMoles(22414.0, StoichiometryEngine.StoichUnit.MILLILITERS_GAS, null, 22.414)
        assertEquals(1.0, ml!!, 1e-9)
    }

    @Test
    fun `particles conversion matches avogadro`() {
        val moles = StoichiometryEngine.amountToMoles(6.02214076, StoichiometryEngine.StoichUnit.PARTICLES, null, 22.414)
        assertEquals(1.0, moles!!, 1e-6)
    }

    @Test
    fun `molarity converts only through explicit path`() {
        assertNull(StoichiometryEngine.amountToMoles(1.0, StoichiometryEngine.StoichUnit.MOLARITY, waterMolarMass, 22.414))
        assertNull(StoichiometryEngine.molesToAmount(1.0, StoichiometryEngine.StoichUnit.MOLARITY, waterMolarMass, 22.414))
    }

    @Test
    fun `formatConversion trims trailing zeros`() {
        assertEquals("2", StoichiometryEngine.formatConversion(2.0))
        assertEquals("128.1", StoichiometryEngine.formatConversion(128.100000))
        assertEquals("0.5", StoichiometryEngine.formatConversion(0.500000))
    }
}

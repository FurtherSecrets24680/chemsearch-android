package com.furthersecrets.chemsearch.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IsomerFormulaAnalyzerTest {

    @Test
    fun `parses simple organic formula`() {
        val a = analyzeIsomerFormula("C2H6O")!!
        assertEquals("C2H6O", a.normalized)
        assertEquals(9, a.atomCount)
        assertEquals(46.07, a.molarMass!!, 0.01)
        assertEquals(0.0, a.dbe!!, 0.001)
        assertFalse(a.dbeNegative)
    }

    @Test
    fun `normalizes to Hill notation`() {
        assertEquals("C2H6O", analyzeIsomerFormula("OC2H6")!!.normalized)
        assertEquals("C6H6", analyzeIsomerFormula("H6C6")!!.normalized)
        assertEquals("CH6", analyzeIsomerFormula("H6C")!!.normalized)
        assertEquals("CH4", analyzeIsomerFormula("CH4")!!.normalized)
        // Carbonless formulas sort purely alphabetically
        assertEquals("H2O", analyzeIsomerFormula("OH2")!!.normalized)
        assertEquals("HCl", analyzeIsomerFormula("ClH")!!.normalized)
    }

    @Test
    fun `benzene has four degrees of unsaturation`() {
        val a = analyzeIsomerFormula("C6H6")!!
        assertEquals(4.0, a.dbe!!, 0.001)
    }

    @Test
    fun `halogens count as hydrogens for DBE`() {
        // C2H4Cl2: (2+1) - (4+2)/2 = 0
        assertEquals(0.0, analyzeIsomerFormula("C2H4Cl2")!!.dbe!!, 0.001)
        // C2H3Cl3: (2+1) - (3+3)/2 = 0
        assertEquals(0.0, analyzeIsomerFormula("C2H3Cl3")!!.dbe!!, 0.001)
    }

    @Test
    fun `nitrogen raises DBE like a missing hydrogen`() {
        // C2H5N: (2+1) - (5-1)/2 = 1  (aziridine or CH3-N=CH2)
        assertEquals(1.0, analyzeIsomerFormula("C2H5N")!!.dbe!!, 0.001)
    }

    @Test
    fun `negative DBE is flagged`() {
        val a = analyzeIsomerFormula("C2H8")!!
        assertTrue(a.dbe!! < 0)
        assertTrue(a.dbeNegative)
    }

    @Test
    fun `tripple bond counts as two`() {
        // C2H2 acetylene: (2+1) - 2/2 = 2
        assertEquals(2.0, analyzeIsomerFormula("C2H2")!!.dbe!!, 0.001)
    }

    @Test
    fun `carbonless formula has no DBE`() {
        val a = analyzeIsomerFormula("H2O2")!!
        assertNull(a.dbe)
        assertEquals(4, a.atomCount)
    }

    @Test
    fun `unknown element skips mass but keeps DBE`() {
        val a = analyzeIsomerFormula("C2H6Xx")!!
        assertNull(a.molarMass)
        assertTrue(a.unknownElements.contains("Xx"))
        assertEquals(0.0, a.dbe!!, 0.001)
    }

    @Test
    fun `invalid formulas return null`() {
        assertNull(analyzeIsomerFormula(""))
        assertNull(analyzeIsomerFormula("   "))
        assertNull(analyzeIsomerFormula("hello"))
        assertNull(analyzeIsomerFormula("C2H6O)"))
    }

    @Test
    fun `condensed groups and hydrates parse`() {
        val a = analyzeIsomerFormula("Ca(OH)2")!!
        assertEquals(5, a.atomCount)
        assertEquals(74.09, a.molarMass!!, 0.01)
        val b = analyzeIsomerFormula("CuSO4.5H2O")!!
        assertEquals("H10CuO9S", b.normalized)
        assertEquals(249.68, b.molarMass!!, 0.05)
    }

    @Test
    fun `formatting strips trailing decimals`() {
        assertEquals("4", formatIsomerDbe(4.0))
        assertEquals("0", formatIsomerDbe(0.0))
        assertEquals("1.5", formatIsomerDbe(1.5))
        assertEquals("-", formatIsomerDbe(null))
    }

    @Test
    fun `sort modes behave`() {
        val items = listOf(
            IsomerSortItem("morphine"),
            IsomerSortItem("Amphetamine"),
            IsomerSortItem("beta-carotene")
        )
        val sorted = sortIsomerItemsByName(items)
        assertEquals(listOf("Amphetamine", "beta-carotene", "morphine"), sorted.map { it.name })
    }

    private data class IsomerSortItem(val name: String)

    private fun <T> sortIsomerItemsByName(items: List<T>): List<T> =
        items.sortedBy {
            @Suppress("UNCHECKED_CAST")
            (it as IsomerSortItem).name.lowercase()
        }
}

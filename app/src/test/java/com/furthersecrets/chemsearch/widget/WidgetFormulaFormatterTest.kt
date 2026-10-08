package com.furthersecrets.chemsearch.widget

import com.furthersecrets.chemsearch.data.ChemicalDbCategory
import com.furthersecrets.chemsearch.data.ChemicalDbEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class WidgetFormulaFormatterTest {

    @Test
    fun `digits after elements become Unicode subscripts`() {
        assertEquals("C₂H₄O₂", WidgetFormulas.format("C2H4O2"))
    }

    @Test
    fun `digits after parenthesis groups become subscripts`() {
        assertEquals("Al(OH)₃", WidgetFormulas.format("Al(OH)3"))
        assertEquals("Ba(OH)₂", WidgetFormulas.format("Ba(OH)2"))
    }

    @Test
    fun `hydrate dots keep coefficients at baseline`() {
        assertEquals("CuSO₄·5H₂O", WidgetFormulas.format("CuSO4·5H2O"))
    }

    @Test
    fun `caret charges become superscripts`() {
        assertEquals("As³⁻", WidgetFormulas.format("As^3-"))
        assertEquals("O²⁻", WidgetFormulas.format("O^2-"))
        assertEquals("N³⁻", WidgetFormulas.format("N^3-"))
    }

    @Test
    fun `trailing charges become superscripts`() {
        assertEquals("Al³⁺", WidgetFormulas.format("Al3+"))
        assertEquals("NH₄⁺", WidgetFormulas.format("NH4+"))
        assertEquals("Br⁻", WidgetFormulas.format("Br-"))
    }

    @Test
    fun `sulfate with space charge stays correct`() {
        assertEquals("SO₄ ²⁻", WidgetFormulas.format("SO4 2-"))
    }

    @Test
    fun `plain text without digits passes through`() {
        assertEquals("Fe", WidgetFormulas.format("Fe"))
    }

    @Test
    fun `blank input yields empty string`() {
        assertEquals("", WidgetFormulas.format("  "))
    }

    @Test
    fun `hyphen separated alloys keep digits at baseline`() {
        assertEquals("Fe + C + Si", WidgetFormulas.format("Fe + C + Si"))
        assertEquals("Sn + Pb or Sn + Ag + Cu", WidgetFormulas.format("Sn + Pb or Sn + Ag + Cu"))
    }

    @Test
    fun `output contains no html markup`() {
        val out = WidgetFormulas.format("C6H8O7")
        assertTrue(!out.contains('<') && !out.contains('>'))
    }
}

class RandomCompoundPickerTest {

    private fun db(size: Int) = (1..size).map { i ->
        ChemicalDbEntry(
            id = "s$i",
            category = ChemicalDbCategory.SUBSTANCES,
            title = "Substance $i",
            formula = "C${i}H${i + 1}"
        )
    }

    @Test
    fun `never immediately repeats when database has more than one entry`() {
        val entries = db(5)
        repeat(50) {
            val (_, compound) = RandomCompoundPicker.pick(entries, 2)!!
            assertTrue(compound.name != "Substance 3")
        }
    }

    @Test
    fun `single entry database always returns it`() {
        val entries = db(1)
        val (index, compound) = RandomCompoundPicker.pick(entries, 0)!!
        assertEquals(0, index)
        assertEquals("Substance 1", compound.name)
    }

    @Test
    fun `null previous index still returns a pick`() {
        val (index, _) = RandomCompoundPicker.pick(db(4), null)!!
        assertTrue(index in 0..3)
    }

    @Test
    fun `random is respected for seeding`() {
        val entries = db(30)
        val picks = (1..40).map { RandomCompoundPicker.pick(entries, null, Random(it))!!.first }.toSet()
        assertTrue("Expected variety, got $picks", picks.size > 1)
    }

    @Test
    fun `empty database yields null`() {
        assertTrue(RandomCompoundPicker.pick(emptyList(), null) == null)
    }
}

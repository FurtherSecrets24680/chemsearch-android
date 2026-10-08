package com.furthersecrets.chemsearch.widget

import com.furthersecrets.chemsearch.data.ChemicalDbCategory
import com.furthersecrets.chemsearch.data.ChemicalDbEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompoundOfTheDayPickerTest {

    private fun entry(id: String, title: String, formula: String = "H2O") = ChemicalDbEntry(
        id = id,
        category = ChemicalDbCategory.SUBSTANCES,
        title = title,
        formula = formula,
        summary = "  A   test summary  with extra   spaces. "
    )

    private fun db() = listOf(
        entry("1", "Acetic acid"),
        entry("2", "Acetone"),
        entry("3", "Benzene")
    )

    @Test
    fun `picks a substance for the day deterministically`() {
        val a = CompoundOfTheDayPicker.pick(db(), 10)
        val b = CompoundOfTheDayPicker.pick(db(), 10)
        assertEquals(a, b)
        assertTrue(a!!.name.isNotBlank())
        assertTrue(a.formula.isNotBlank())
    }

    @Test
    fun `rotates across days`() {
        val db = db()
        val picks = (0L until 6L).map { CompoundOfTheDayPicker.pick(db, it)!!.name }.toSet()
        assertTrue("Expected rotation across days, got $picks", picks.size > 1)
    }

    @Test
    fun `skips non-substance entries`() {
        val reaction = ChemicalDbEntry(
            id = "r1",
            category = ChemicalDbCategory.REACTIONS,
            title = "Rusting"
        )
        val picked = CompoundOfTheDayPicker.pick(db() + reaction, 0)
        assertTrue(picked!!.name != "Rusting")
    }

    @Test
    fun `collapses whitespace in summary`() {
        val picked = CompoundOfTheDayPicker.pick(db(), 0)
        assertTrue(!picked!!.uses.contains("  "))
    }

    @Test
    fun `empty database yields null`() {
        assertNull(CompoundOfTheDayPicker.pick(emptyList(), 0))
    }

    @Test
    fun `day index is stable within a day and grows with time`() {
        val base = 1_700_000_000_000L
        val sameDay = base + 60_000L
        assertEquals(CompoundOfTheDayPicker.dayIndex(base), CompoundOfTheDayPicker.dayIndex(sameDay))
        val nextDay = base + 25 * 60 * 60 * 1000L
        assertTrue(CompoundOfTheDayPicker.dayIndex(nextDay) > CompoundOfTheDayPicker.dayIndex(base))
    }
}

package com.furthersecrets.chemsearch.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReactionPredictorTest {

    private val emptyDb = emptyList<ChemicalDbEntry>()

    private fun dbReaction(
        id: String,
        title: String,
        equation: String,
        observation: String = "",
        conditions: String = ""
    ): ChemicalDbEntry {
        val sections = buildList {
            if (conditions.isNotBlank()) {
                add(ChemicalDbSection(title = "Typical conditions", rows = listOf(ChemicalDbRow("Conditions", conditions))))
            }
        }
        return ChemicalDbEntry(
            id = id,
            category = ChemicalDbCategory.REACTIONS,
            title = title,
            formula = equation,
            summary = observation,
            sections = sections
        )
    }

    // ---------------- Database matching ---------------- //

    @Test
    fun `database match returns curated equation with observation and conditions`() {
        val db = listOf(
            dbReaction(
                id = "test-1",
                title = "Sodium hydroxide with hydrochloric acid",
                equation = "HCl + NaOH ⟶ NaCl + H2O",
                observation = "Heat is released",
                conditions = "Room temperature"
            )
        )
        val result = ReactionPredictor.predict("HCl", "NaOH", db)
        assertNull(result.errorRes)
        assertEquals(ReactionPredictor.Confidence.DATABASE, result.confidence)
        assertEquals("Sodium hydroxide with hydrochloric acid", result.matchedDbEntryTitle)
        assertEquals("Heat is released", result.observation)
        assertEquals("Room temperature", result.conditions)
        assertTrue(result.balancedEquation.contains("NaCl"))
    }

    @Test
    fun `database match works regardless of reactant order`() {
        val db = listOf(dbReaction("t", "t", "Fe + CuSO4 ⟶ FeSO4 + Cu"))
        val result = ReactionPredictor.predict("CuSO4", "Fe", db)
        assertEquals(ReactionPredictor.Confidence.DATABASE, result.confidence)
    }

    @Test
    fun `non-reaction database entries are ignored`() {
        val db = listOf(
            ChemicalDbEntry(id = "s", category = ChemicalDbCategory.SUBSTANCES, title = "Water", formula = "H2O")
        )
        val result = ReactionPredictor.predict("HCl", "NaOH", db)
        // Falls through to the rule engine instead of matching the substance entry.
        assertEquals(ReactionPredictor.Confidence.RULE, result.confidence)
    }

    @Test
    fun `heat reagent in db reactant side does not break matching`() {
        val db = listOf(dbReaction("t", "N2 to NO", "N2 + O2 + heat ⟶ 2NO"))
        val result = ReactionPredictor.predict("N2", "O2", db)
        assertEquals(ReactionPredictor.Confidence.DATABASE, result.confidence)
        assertEquals("N2 + O2 + heat ⟶ 2NO", result.balancedEquation)
    }

    // ---------------- Neutralization ---------------- //

    @Test
    fun `acid and hydroxide base neutralize to salt and water`() {
        val result = ReactionPredictor.predict("HCl", "NaOH", emptyDb)
        assertNull(result.errorRes)
        assertEquals(ReactionPredictor.Confidence.RULE, result.confidence)
        assertTrue(result.balancedEquation.contains("NaCl"))
        assertTrue(result.balancedEquation.contains("H2O"))
        // 1:1:1:1 balance
        assertTrue(result.balancedEquation.startsWith("HCl + NaOH"))
    }

    @Test
    fun `sulfuric acid with aluminum hydroxide balances to 3 and 2`() {
        val result = ReactionPredictor.predict("Al(OH)3", "H2SO4", emptyDb)
        assertNull(result.errorRes)
        // The balancer may order reactants as it solves; assert the coefficients.
        assertTrue(result.balancedEquation.replace(" ", "").contains("3H2SO4"))
        assertTrue(result.balancedEquation.replace(" ", "").contains("2Al(OH)3"))
        assertTrue(result.balancedEquation.contains("Al2(SO4)3"))
        assertTrue(result.balancedEquation.contains("6H2O"))
    }

    // ---------------- Acid + carbonate ---------------- //

    @Test
    fun `acid with carbonate yields salt water and carbon dioxide`() {
        val result = ReactionPredictor.predict("HCl", "CaCO3", emptyDb)
        assertNull(result.errorRes)
        assertTrue(result.balancedEquation.contains("CaCl2"))
        assertTrue(result.balancedEquation.contains("CO2"))
        assertTrue(result.balancedEquation.contains("H2O"))
    }

    @Test
    fun `bicarbonate is identified and predicted`() {
        val result = ReactionPredictor.predict("NaHCO3", "HNO3", emptyDb)
        assertNull(result.errorRes)
        assertTrue(result.balancedEquation.contains("CO2"))
        assertTrue(result.balancedEquation.contains("NaNO3"))
    }

    // ---------------- Metal + acid ---------------- //

    @Test
    fun `zinc with hydrochloric acid gives zinc chloride and hydrogen`() {
        val result = ReactionPredictor.predict("Zn", "HCl", emptyDb)
        assertNull(result.errorRes)
        assertTrue(result.balancedEquation.contains("ZnCl2"))
        assertTrue(result.balancedEquation.contains("H2"))
    }

    @Test
    fun `copper does not react with dilute acid`() {
        val result = ReactionPredictor.predict("Cu", "HCl", emptyDb)
        assertNotNull(result.errorRes)
    }

    // ---------------- Single displacement ---------------- //

    @Test
    fun `more active metal displaces less active metal from salt`() {
        val result = ReactionPredictor.predict("Fe", "CuSO4", emptyDb)
        assertNull(result.errorRes)
        assertTrue(result.balancedEquation.contains("FeSO4"))
        assertTrue(result.balancedEquation.contains("Cu"))
    }

    @Test
    fun `less active metal cannot displace more active metal`() {
        val result = ReactionPredictor.predict("Ag", "ZnSO4", emptyDb)
        assertNotNull(result.errorRes)
    }

    @Test
    fun `identical metal pair does not displace`() {
        val result = ReactionPredictor.predict("Cu", "CuSO4", emptyDb)
        assertNotNull(result.errorRes)
    }

    // ---------------- Combustion ---------------- //

    @Test
    fun `methane combusts to carbon dioxide and water`() {
        val result = ReactionPredictor.predict("CH4", "O2", emptyDb)
        assertNull(result.errorRes)
        assertEquals("CH4 + 2O2 ⟶ CO2 + 2H2O", result.balancedEquation)
    }

    @Test
    fun `ethanol combusts to carbon dioxide and water`() {
        val result = ReactionPredictor.predict("C2H5OH", "O2", emptyDb)
        assertNull(result.errorRes)
        assertTrue(result.balancedEquation.contains("CO2"))
        assertTrue(result.balancedEquation.contains("H2O"))
    }

    // ---------------- Synthesis ---------------- //

    @Test
    fun `elements combine into known binary compound`() {
        val result = ReactionPredictor.predict("Na", "Cl2", emptyDb)
        assertNull(result.errorRes)
        assertTrue(result.balancedEquation.contains("NaCl"))
    }

    @Test
    fun `magnesium burns in oxygen to magnesium oxide`() {
        val result = ReactionPredictor.predict("Mg", "O2", emptyDb)
        assertNull(result.errorRes)
        assertEquals("2Mg + O2 ⟶ 2MgO", result.balancedEquation)
    }

    @Test
    fun `unknown element pair has no synthesis`() {
        val result = ReactionPredictor.predict("Xe", "Os", emptyDb)
        assertNotNull(result.errorRes)
    }

    // ---------------- Errors ---------------- //

    @Test
    fun `blank reactants produce an error`() {
        val result = ReactionPredictor.predict("", "NaOH", emptyDb)
        assertNotNull(result.errorRes)
        val result2 = ReactionPredictor.predict("HCl", "  ", emptyDb)
        assertNotNull(result2.errorRes)
    }

    @Test
    fun `unmatched pair reports no prediction`() {
        val result = ReactionPredictor.predict("Fe2O3", "KMnO4", emptyDb)
        assertNotNull(result.errorRes)
    }

    // ---------------- New rules ---------------- //

    @Test
    fun `ammonium salt with hydroxide releases ammonia`() {
        val result = ReactionPredictor.predict("NH4Cl", "NaOH", emptyDb)
        assertNull(result.errorRes)
        assertTrue(result.balancedEquation.contains("NH3"))
        assertTrue(result.balancedEquation.contains("NaCl"))
        assertTrue(result.balancedEquation.contains("H2O"))
    }

    @Test
    fun `acid with basic metal oxide gives salt and water`() {
        val result = ReactionPredictor.predict("CuO", "H2SO4", emptyDb)
        assertNull(result.errorRes)
        assertTrue(result.balancedEquation.contains("CuSO4"))
        assertTrue(result.balancedEquation.contains("H2O"))
    }

    @Test
    fun `acid with sulfite releases sulfur dioxide`() {
        val result = ReactionPredictor.predict("Na2SO3", "HCl", emptyDb)
        assertNull(result.errorRes)
        assertTrue(result.balancedEquation.contains("SO2"))
        assertTrue(result.balancedEquation.contains("NaCl"))
    }

    @Test
    fun `acid with sulfide releases hydrogen sulfide`() {
        val result = ReactionPredictor.predict("FeS", "HCl", emptyDb)
        assertNull(result.errorRes)
        assertTrue(result.balancedEquation.contains("H2S"))
        assertTrue(result.balancedEquation.contains("FeCl2"))
    }

    @Test
    fun `calcium reacts with cold water to hydroxide`() {
        val result = ReactionPredictor.predict("Ca", "H2O", emptyDb)
        assertNull(result.errorRes)
        assertTrue(result.balancedEquation.contains("Ca(OH)2"))
        assertTrue(result.balancedEquation.contains("H2"))
    }

    @Test
    fun `magnesium with steam forms oxide not hydroxide`() {
        val result = ReactionPredictor.predict("Mg", "H2O", emptyDb)
        assertNull(result.errorRes)
        assertTrue(result.balancedEquation.contains("MgO"))
    }

    @Test
    fun `iron does not react with water`() {
        val result = ReactionPredictor.predict("Fe", "H2O", emptyDb)
        assertNotNull(result.errorRes)
    }

    // ---------------- Multi-reactant ---------------- //

    @Test
    fun `three reactants match a database entry`() {
        val db = listOf(dbReaction("t", "thermite", "Fe2O3 + 2Al ⟶ Al2O3 + 2Fe"))
        val result = ReactionPredictor.predict(listOf("Fe2O3", "Al"), db)
        assertEquals(ReactionPredictor.Confidence.DATABASE, result.confidence)
    }

    @Test
    fun `single reactant input errors`() {
        val result = ReactionPredictor.predict(listOf("HCl"), emptyDb)
        assertNotNull(result.errorRes)
    }

    @Test
    fun `three reactants skip rules and report no prediction when unmatched`() {
        val result = ReactionPredictor.predict(listOf("HCl", "NaOH", "O2"), emptyDb)
        // Rules need exactly two reactants; 3 inputs only match the DB.
        assertNotNull(result.errorRes)
    }
}

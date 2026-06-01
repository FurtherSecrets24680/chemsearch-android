package com.furthersecrets.chemsearch.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CondensedFormulaTest {
    @Test
    fun buildsCondensedFormulaForSimpleAlcohols() {
        assertEquals("CH3-OH", buildCondensedFormula("CO"))
        assertEquals("CH3-CH2-OH", buildCondensedFormula("CCO"))
    }

    @Test
    fun keepsEtherConnectivityDistinctFromAlcohol() {
        assertEquals("CH3-O-CH3", buildCondensedFormula("COC"))
    }

    @Test
    fun showsSimpleBranchesAndMultipleBonds() {
        assertEquals("CH3-C(=O)-CH3", buildCondensedFormula("CC(=O)C"))
        assertEquals("CH≡CH", buildCondensedFormula("C#C"))
        assertEquals("CH3-CH(-OH)-CH3", buildCondensedFormula("CC(C)O"))
    }

    @Test
    fun rendersBracketedIonsAndDisconnectedSalts() {
        assertEquals("NH4+", buildCondensedFormula("[NH4+]"))
        assertEquals("NaCl", buildCondensedFormula("[Na+].[Cl-]"))
        assertEquals("CH3-C(=O)-O- · Na+", buildCondensedFormula("CC(=O)[O-].[Na+]"))
    }

    @Test
    fun rendersCommonInorganicAmmoniumSaltsAsGroupedFormulas() {
        assertEquals(
            "(NH4)3PO4",
            buildCondensedFormula("[NH4+].[NH4+].[NH4+].P(=O)([O-])([O-])[O-]")
        )
        assertEquals(
            "(NH4)2HPO4",
            buildCondensedFormula("[NH4+].[NH4+].OP(=O)([O-])[O-]")
        )
        assertEquals(
            "NH4H2PO4",
            buildCondensedFormula("[NH4+].OP(=O)(O)[O-]")
        )
        assertEquals(
            "NH4OH",
            buildCondensedFormula("[NH4+].[OH-]")
        )
    }

    @Test
    fun groupsRepeatedDisconnectedLigandsForComplexIonicCompounds() {
        assertEquals(
            "2NH4+ · Cl-",
            buildCondensedFormula("[NH4+].[NH4+].[Cl-]")
        )
    }

    @Test
    fun rendersCoordinationSaltsWithBracketedComplexIons() {
        assertEquals(
            "K3[Fe(CN)6]",
            buildCondensedFormula("[C-]#N.[C-]#N.[C-]#N.[C-]#N.[C-]#N.[C-]#N.[K+].[K+].[K+].[Fe+3]")
        )
        assertEquals(
            "K4[Fe(CN)6]",
            buildCondensedFormula("[C-]#N.[C-]#N.[C-]#N.[C-]#N.[C-]#N.[C-]#N.[K+].[K+].[K+].[K+].[Fe+2]")
        )
        assertEquals(
            "Na2[Fe(CN)5NO]",
            buildCondensedFormula("[C-]#N.[C-]#N.[C-]#N.[C-]#N.[C-]#N.N#[O+].[Na+].[Na+].[Fe+2]")
        )
    }

    @Test
    fun rendersCationicCoordinationSaltsWithCounterionsAfterTheComplex() {
        assertEquals(
            "[Co(NH3)6]Cl3",
            buildCondensedFormula("[Co+3].N.N.N.N.N.N.[Cl-].[Cl-].[Cl-]")
        )
    }

    @Test
    fun skipsStructuresThatNeedMoreThanAConservativeFormatter() {
        assertNull(buildCondensedFormula("C1=CC=CC=C1"))
        assertNull(buildCondensedFormula("c1ccccc1"))
        assertNull(buildCondensedFormula("C1=CC=CC=C1.[Na+]"))
    }
}

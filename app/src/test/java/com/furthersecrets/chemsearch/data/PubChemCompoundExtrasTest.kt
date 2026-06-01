package com.furthersecrets.chemsearch.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PubChemCompoundExtrasTest {
    @Test
    fun advancedPropertiesSkipAlreadyVisibleMainFields() {
        val props = CompoundProperty(
            cid = 2244,
            molecularFormula = "C9H8O4",
            molecularWeight = "180.16",
            iupacName = "2-acetyloxybenzoic acid",
            charge = 0,
            xLogP = 1.2,
            tpsa = 63.6,
            complexity = 212.0,
            hBondDonorCount = 1,
            hBondAcceptorCount = 4,
            heavyAtomCount = 13,
            rotatableBondCount = 3,
            conformerCount3d = 10
        )

        val rows = buildAdvancedProperties(props)
        val labels = rows.map { it.label }

        assertTrue("XLogP" in labels)
        assertTrue("Topological polar surface area" in labels)
        assertTrue("Heavy atom count" in labels)
        assertFalse("Molecular formula" in labels)
        assertFalse("Molecular weight" in labels)
        assertFalse("Formal charge" in labels)
    }

    @Test
    fun classificationTagsCleanHierarchyAndDeduplicate() {
        val tags = buildPubChemClassificationTags(
            listOf(
                PubChemSectionText("Chemical Classes", null, "Other Uses -> Pharmaceuticals"),
                PubChemSectionText("Chemical Classes", null, "Pharmaceuticals -> Analgesics"),
                PubChemSectionText("Drug Classes", null, "Antithrombotic Agents, Antiinflammatory Agents, Salicylates"),
                PubChemSectionText("Chemical Classes", null, "Pharmaceuticals")
            )
        )

        assertEquals(
            listOf("Pharmaceuticals", "Analgesics", "Antithrombotic Agents", "Antiinflammatory Agents", "Salicylates"),
            tags
        )
    }

    @Test
    fun useEntriesPreferReadableShortStrings() {
        val entries = buildPubChemUseEntries(
            listOf(
                PubChemSectionText("Uses", "EPA CPDat Chemical and Product Categories", ""),
                PubChemSectionText("Uses", "Sources/Uses", "Used in manufacturing and health care; [ACGIH TLVs and BEIs]"),
                PubChemSectionText("Therapeutic Uses", null, "Therapeutic Category: Analgesic; antipyretic; anti-inflammatory.")
            )
        )

        assertEquals(2, entries.size)
        assertEquals("Uses", entries[0].label)
        assertEquals("Used in manufacturing and health care", entries[0].text)
        assertEquals("Therapeutic uses", entries[1].label)
    }

    @Test
    fun useEntriesDisplayAsBulletsWithoutRepeatedLabels() {
        val bullets = compoundUseBulletLines(
            listOf(
                CompoundUseEntry("Uses", "Used as a solvent."),
                CompoundUseEntry("Uses", "Used in manufacturing.")
            )
        )

        assertEquals(listOf("• Used as a solvent.", "• Used in manufacturing."), bullets)
    }
}

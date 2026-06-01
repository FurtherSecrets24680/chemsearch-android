package com.furthersecrets.chemsearch.ui

import com.furthersecrets.chemsearch.data.PeriodicTableElements
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PeriodicTableUiBehaviorTest {
    @Test
    fun fillsSpectralLineFallbacksForVerifiedVisibleSpectra() {
        assertEquals(
            "https://commons.wikimedia.org/wiki/File:Chromium_spectrum_visible.png",
            PeriodicTableElements.element("Cr").spectralImagePageUrl()
        )
        assertEquals(
            "https://commons.wikimedia.org/wiki/File:Rubidium_spectrum_visible.png",
            PeriodicTableElements.element("Rb").spectralImagePageUrl()
        )
        assertEquals(
            "https://commons.wikimedia.org/wiki/File:Caesium_spectrum_visible.png",
            PeriodicTableElements.element("Cs").spectralImagePageUrl()
        )
    }

    @Test
    fun usesKnownSpecialCaseSpectralSources() {
        assertEquals(
            "https://commons.wikimedia.org/wiki/File:80_(Hg_I)_NIST_ASD_emission_spectrum.png",
            PeriodicTableElements.element("Hg").spectralImagePageUrl()
        )
        assertEquals(
            "https://commons.wikimedia.org/wiki/File:Atomic_spectrum_of_francium.svg",
            PeriodicTableElements.element("Fr").spectralImagePageUrl()
        )
    }

    @Test
    fun keepsSpectralLinesHiddenWhenNoReliableSourceIsMapped() {
        assertNull(PeriodicTableElements.element("At").spectralImagePageUrl())
        assertNull(PeriodicTableElements.element("Og").spectralImagePageUrl())
    }

    @Test
    fun mapsSpectralCoverageAcrossAllElementsDeliberately() {
        val withSpectra = PeriodicTableElements.filter { it.spectralImagePageUrl() != null }
        val withoutSpectra = PeriodicTableElements.filter { it.spectralImagePageUrl() == null }

        assertTrue(withSpectra.size >= 98)
        assertTrue(withoutSpectra.all { it.symbol == "At" || it.atomicNumber >= 100 })
        assertNotNull(PeriodicTableElements.element("Cm").spectralImagePageUrl())
        assertNotNull(PeriodicTableElements.element("Es").spectralImagePageUrl())
    }

    @Test
    fun physicalPropertiesCardUsesPlainFactsWithoutDuplicatingMoreDetails() {
        val calcium = PeriodicTableElements.element("Ca")
        val labels = elementPhysicalPropertyFacts(calcium).map { it.label }

        assertEquals("Physical Properties", periodicPhysicalPropertiesCardTitle)
        assertEquals(
            listOf(
                "Electronegativity",
                "Atomic radius",
                "Ionization energy",
                "Melting point",
                "Boiling point",
                "Density",
                "Molar heat"
            ),
            labels
        )
        assertTrue("Physical Properties" !in periodicMoreDetailsSectionTitles)
    }

    @Test
    fun detailCardsHaveReadableInfoDescriptions() {
        val expectedTitles = listOf(
            "Element Overview",
            "Element Images",
            "Electron Shells",
            "Physical Properties",
            "More Details",
            "Spectral Lines",
            "Sources"
        )

        expectedTitles.forEach { title ->
            val info = periodicDetailCardInfo(title)
            assertNotNull(info)
            assertTrue(info!!.description.length > 24)
        }

        assertTrue(periodicDetailCardInfo("Electron Shells")!!.description.contains("valence", ignoreCase = true))
        assertTrue(periodicDetailCardInfo("Physical Properties")!!.description.contains("boils", ignoreCase = true))
        assertTrue(periodicDetailCardInfo("Spectral Lines")!!.description.contains("fingerprint", ignoreCase = true))
        assertTrue(periodicDetailCardInfo("Sources")!!.description.contains("open", ignoreCase = true))
    }

    @Test
    fun infoButtonsStaySmallBesideCardTitles() {
        assertTrue(periodicInfoButtonSizeDp <= 30)
        assertTrue(periodicInfoIconSizeDp <= 18)
    }

    @Test
    fun heavyElementShellRadiiKeepInnerShellOutsideNucleus() {
        val radii = electronShellOrbitRadii(
            shellCount = 6,
            maxRadius = 107f,
            nucleusRadius = 18f,
            minimumNucleusGap = 12f
        )

        assertEquals(6, radii.size)
        assertTrue(radii.first() >= 30f)
        assertEquals(107f, radii.last(), 0.001f)
        assertTrue(radii.zipWithNext().all { (inner, outer) -> outer > inner })
    }

    @Test
    fun electronConfigurationCanSwitchBetweenShortAndFullForms() {
        val holmium = PeriodicTableElements.element("Ho")

        assertEquals("[Xe]6s² 4f¹¹", electronConfigurationText(holmium, showFull = false))
        assertEquals(
            "1s² 2s² 2p⁶ 3s² 3p⁶ 4s² 3d¹⁰ 4p⁶ 5s² 4d¹⁰ 5p⁶ 6s² 4f¹¹",
            electronConfigurationText(holmium, showFull = true)
        )
        assertFalse(electronConfigurationText(holmium, showFull = true).contains("[Xe]"))
    }

    @Test
    fun electronConfigurationToggleUsesQuietLabeling() {
        assertEquals("Electronic configuration", periodicElectronConfigurationLabel)
        assertTrue(periodicFullConfigurationToggleTextSizeSp <= 12)
        assertTrue(periodicFullConfigurationToggleContentAlpha <= 0.7f)
    }
}

private fun List<com.furthersecrets.chemsearch.data.PeriodicElement>.element(symbol: String) =
    first { it.symbol == symbol }

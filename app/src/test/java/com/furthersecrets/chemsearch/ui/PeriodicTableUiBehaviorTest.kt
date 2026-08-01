package com.furthersecrets.chemsearch.ui

import com.furthersecrets.chemsearch.R
import com.furthersecrets.chemsearch.data.PeriodicTableElements
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
        val labels = elementPhysicalPropertyFacts(calcium).map { it.labelRes }

        assertEquals(R.string.ui_physical_properties, R.string.ui_physical_properties)
        assertEquals(
            listOf(
                R.string.ui_electronegativity,
                R.string.ui_atomic_radius,
                R.string.ui_ionization_energy,
                R.string.ui_melting_point,
                R.string.ui_boiling_point,
                R.string.ui_density,
                R.string.ui_molar_heat
            ),
            labels
        )
        assertNotEquals(R.string.ui_physical_properties, R.string.ui_more_details)
    }

    @Test
    fun detailCardsHaveReadableInfoDescriptions() {
        val expectedTitles = listOf(
            R.string.ui_element_overview,
            R.string.ui_element_images,
            R.string.ui_electron_shells,
            R.string.ui_physical_properties,
            R.string.ui_more_details,
            R.string.ui_spectral_lines,
            R.string.ui_sources
        )

        expectedTitles.forEach { title ->
            val info = periodicDetailCardInfo(title)
            assertNotNull(info)
            assertNotEquals(0, info!!.descriptionRes)
        }

        assertEquals(R.string.ui_periodic_detail_electron_shells_desc, periodicDetailCardInfo(R.string.ui_electron_shells)!!.descriptionRes)
        assertEquals(R.string.ui_periodic_detail_physical_properties_desc, periodicDetailCardInfo(R.string.ui_physical_properties)!!.descriptionRes)
        assertEquals(R.string.ui_periodic_detail_spectral_lines_desc, periodicDetailCardInfo(R.string.ui_spectral_lines)!!.descriptionRes)
        assertEquals(R.string.ui_periodic_detail_sources_desc, periodicDetailCardInfo(R.string.ui_sources)!!.descriptionRes)
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
        assertNotEquals(0, R.string.ui_electronic_configuration)
        assertTrue(periodicFullConfigurationToggleTextSizeSp <= 12)
        assertTrue(periodicFullConfigurationToggleContentAlpha <= 0.7f)
    }
}

private fun List<com.furthersecrets.chemsearch.data.PeriodicElement>.element(symbol: String) =
    first { it.symbol == symbol }

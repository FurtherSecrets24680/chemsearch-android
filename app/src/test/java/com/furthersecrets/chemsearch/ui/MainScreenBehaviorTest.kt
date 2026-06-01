package com.furthersecrets.chemsearch.ui

import com.furthersecrets.chemsearch.data.ChemUiState
import com.furthersecrets.chemsearch.data.IsomerItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MainScreenBehaviorTest {
    @Test
    fun randomCompoundButtonOnlyAppearsOnEmptySearchPage() {
        assertTrue(shouldShowRandomCompoundButton(ChemUiState(), query = ""))
        assertFalse(shouldShowRandomCompoundButton(ChemUiState(isLoading = true), query = ""))
        assertFalse(shouldShowRandomCompoundButton(ChemUiState(hasResult = true), query = ""))
        assertFalse(shouldShowRandomCompoundButton(ChemUiState(), query = "aspirin"))
    }

    @Test
    fun starterSuggestionsCoverCommonNamesAndFormulaExamples() {
        assertEquals("Try searching", homeStarterSuggestionsLabel)
        assertTrue(homeStarterSuggestions.contains("caffeine"))
        assertTrue(homeStarterSuggestions.contains("NaCl"))
        assertTrue(homeStarterSuggestions.contains("H2SO4"))
        assertTrue(homeStarterSuggestions.contains("ammonium phosphate"))
    }

    @Test
    fun homeQuickActionsUseClearSearchLabels() {
        assertEquals("Structure Search", homeStructureSearchActionTitle)
        assertEquals("Draw a molecule and search PubChem", homeStructureSearchActionDescription)
        assertEquals("Isomer Search", homeIsomerSearchActionTitle)
        assertEquals("Find compounds with the same formula", homeIsomerSearchActionDescription)
    }

    @Test
    fun isomerSearchUsesStandaloneRouteOutsideTools() {
        assertEquals("isomer_search", IsomerSearchRoute)
        assertTrue(isStandalonePageRoute(IsomerSearchRoute))
        assertFalse(isStandalonePageRoute(AppTab.TOOLS.route))
    }

    @Test
    fun homeQuickActionDescriptionsCanUseTwoLines() {
        assertEquals(2, homeQuickActionTitleMaxLines)
        assertEquals(2, homeQuickActionDescriptionMaxLines)
    }

    @Test
    fun homeQuickActionLayoutStaysCompactWithoutHorizontalScrolling() {
        val regular = homeQuickActionLayoutMetrics(compact = false)
        val compact = homeQuickActionLayoutMetrics(compact = true)

        assertFalse(regular.usesHorizontalScroll)
        assertTrue(regular.horizontalPaddingDp <= 8)
        assertTrue(regular.textGapDp <= 7)
        assertTrue(regular.iconBoxSizeDp <= compact.iconBoxSizeDp + 2)
    }

    @Test
    fun homeQuickActionTextSlotsAlignTitlesAndDescriptions() {
        val regular = homeQuickActionLayoutMetrics(compact = false)
        val compact = homeQuickActionLayoutMetrics(compact = true)

        assertFalse(regular.usesFixedTextSlots)
        assertFalse(compact.usesFixedTextSlots)
        assertTrue(regular.cardMinHeightDp >= 84)
    }

    @Test
    fun compoundExtraInfoUsesSingleCollapsedActionBelowSafety() {
        assertEquals("Show more information about this substance", compoundExtraInfoToggleLabel(expanded = false))
        assertEquals("Hide extra compound information", compoundExtraInfoToggleLabel(expanded = true))

        val collapsed = compoundExtraInfoSectionOrder(showExtraInfo = false, hasExtraInfo = true)
        assertEquals(listOf("GHS Safety", "More information toggle"), collapsed)

        val expanded = compoundExtraInfoSectionOrder(showExtraInfo = true, hasExtraInfo = true)
        assertEquals(
            listOf("GHS Safety", "More information toggle", "Uses & Occurrence", "Advanced Properties", "Classification"),
            expanded
        )
    }

    @Test
    fun identifierDividerUsesVisibleThemeAwareAlpha() {
        assertEquals(0.10f, identifierDividerAlpha(isDarkSurface = true), 0.0001f)
        assertEquals(0.08f, identifierDividerAlpha(isDarkSurface = false), 0.0001f)
    }

    @Test
    fun extraCompoundCardsHaveHelpfulInfoDialogCopy() {
        assertTrue(advancedPropertiesInfoEntries().any { it.first == "Why this is hidden by default" })
        assertTrue(classificationInfoEntries().any { it.second.contains("Breast feeding", ignoreCase = true) })
        assertTrue(usesOccurrenceInfoEntries().any { it.second.contains("not instructions", ignoreCase = true) })
    }

    @Test
    fun searchLoadingBubbleFrameStaggersAnimatedBubbles() {
        val first = searchLoadingBubbleFrame(progress = 0.25f, bubbleIndex = 0, reduceMotion = false)
        val second = searchLoadingBubbleFrame(progress = 0.25f, bubbleIndex = 1, reduceMotion = false)

        assertTrue(first.liftFraction in 0f..1f)
        assertTrue(first.alpha in 0.35f..1f)
        assertNotEquals(first.liftFraction, second.liftFraction)
    }

    @Test
    fun searchLoadingBubbleFrameStaysStillWhenMotionIsReduced() {
        val frame = searchLoadingBubbleFrame(progress = 0.25f, bubbleIndex = 0, reduceMotion = true)

        assertEquals(0f, frame.liftFraction, 0.0001f)
        assertEquals(0.65f, frame.alpha, 0.0001f)
    }

    @Test
    fun searchLoadingAnimationLayoutKeepsLargerBubblesBelowText() {
        val layout = searchLoadingAnimationLayout(compactMode = false)

        assertTrue(layout.topPaddingDp >= 10f)
        assertTrue(layout.iconSizeDp >= 62f)
        assertTrue(layout.bubbleSpecs.all { it.sizeDp >= 9f })
        assertTrue(layout.minimumBubbleTopAtFullLiftDp >= 2f)
    }

    @Test
    fun isomerSearchLoadingUsesMainSearchChemistryAnimationScale() {
        assertEquals(searchLoadingAnimationLayout(compactMode = false), isomerLoadingAnimationLayout(compactMode = false))
        assertEquals(searchLoadingAnimationLayout(compactMode = true), isomerLoadingAnimationLayout(compactMode = true))
        assertEquals("Searching PubChem for isomers…", isomerLoadingStatusText)
    }

    @Test
    fun isomerSearchHidesIsotopesByDefault() {
        val isomers = listOf(
            IsomerItem(cid = 241, title = "Benzene"),
            IsomerItem(cid = 71601, title = "Benzene-D6", isIsotope = true)
        )

        assertEquals(listOf(241L), visibleIsomers(isomers, includeIsotopes = false).map { it.cid })
        assertEquals(listOf(241L, 71601L), visibleIsomers(isomers, includeIsotopes = true).map { it.cid })
        assertEquals(1, hiddenIsotopeCount(isomers, includeIsotopes = false))
    }

    @Test
    fun selectedIsomersCanPrefillCompareByCid() {
        val isomers = listOf(
            IsomerItem(cid = 241, title = "Benzene"),
            IsomerItem(cid = 69402, title = "Dipropargyl"),
            IsomerItem(cid = 71601, title = "Benzene-D6", isIsotope = true)
        )

        assertFalse(shouldShowIsomerCompareAction(selectedCount = 1))
        assertTrue(shouldShowIsomerCompareAction(selectedCount = 2))
        assertEquals(listOf("69402", "241"), isomerCompareQueries(isomers, selectedCids = listOf(69402, 241, 999)))
    }

    @Test
    fun isomerSearchLoadsResultsInTwentyResultChunks() {
        assertEquals(20, InitialIsomerResultLimit)
        assertEquals(40, nextIsomerResultLimit(currentLimit = 20))
        assertEquals(60, nextIsomerResultLimit(currentLimit = 40))
        assertEquals(20, nextIsomerResultLimit(currentLimit = 0))
    }

    @Test
    fun isomerSearchVisibleResultsFollowLoadedResultLimit() {
        val isomers = (1L..40L).map { IsomerItem(cid = it, title = "CID $it") }
        val state = ChemUiState(isomers = isomers, isomerResultLimit = 40)

        assertEquals(40, visibleIsomersForState(state, includeIsotopes = false).size)
    }
}

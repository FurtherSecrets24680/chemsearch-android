package com.furthersecrets.chemsearch.ui

import com.furthersecrets.chemsearch.R
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
        assertNotEquals(0, R.string.ui_try_searching)
        assertTrue(homeStarterSuggestions.contains("caffeine"))
        assertTrue(homeStarterSuggestions.contains("NaCl"))
        assertTrue(homeStarterSuggestions.contains("H2SO4"))
        assertTrue(homeStarterSuggestions.contains("ammonium phosphate"))
    }

    @Test
    fun homeQuickActionsUseClearSearchLabels() {
        assertNotEquals(R.string.ui_structure_search_2, R.string.ui_isomer_search)
        assertNotEquals(R.string.ui_draw_molecule_and_search, R.string.ui_find_compounds_same_formula)
        assertNotEquals(R.string.ui_structure_search_2, R.string.ui_draw_molecule_and_search)
        assertNotEquals(R.string.ui_isomer_search, R.string.ui_find_compounds_same_formula)
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
        assertNotEquals(0, R.string.ui_show_more_compound_info)
        assertNotEquals(0, R.string.ui_hide_extra_compound_info)
        assertNotEquals(R.string.ui_show_more_compound_info, R.string.ui_hide_extra_compound_info)
    }

    @Test
    fun identifierDividerUsesVisibleThemeAwareAlpha() {
        assertEquals(0.10f, identifierDividerAlpha(isDarkSurface = true), 0.0001f)
        assertEquals(0.08f, identifierDividerAlpha(isDarkSurface = false), 0.0001f)
    }

    @Test
    fun extraCompoundCardsHaveHelpfulInfoDialogCopy() {
        assertTrue(advancedPropertiesInfoEntries().any { it.first == R.string.ui_info_why_hidden_by_default })
        assertTrue(classificationInfoEntries().any { it.second == R.string.ui_info_why_tags_look_strange_body })
        assertTrue(usesOccurrenceInfoEntries().any { it.second == R.string.ui_info_uses_safety_note_body })
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
        assertNotEquals(R.string.ui_searching_pubchem_for_isomers, R.string.ui_searching_pubchem)
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

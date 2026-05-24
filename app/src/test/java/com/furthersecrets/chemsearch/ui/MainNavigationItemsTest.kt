package com.furthersecrets.chemsearch.ui

import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.bold.Books as BoldBooks
import com.adamglin.phosphoricons.bold.ClockCounterClockwise as BoldClockCounterClockwise
import com.adamglin.phosphoricons.bold.GearSix as BoldGearSix
import com.adamglin.phosphoricons.bold.MagnifyingGlass as BoldMagnifyingGlass
import com.adamglin.phosphoricons.bold.Wrench as BoldWrench
import com.adamglin.phosphoricons.fill.Books as FillBooks
import com.adamglin.phosphoricons.fill.ClockCounterClockwise as FillClockCounterClockwise
import com.adamglin.phosphoricons.fill.GearSix as FillGearSix
import com.adamglin.phosphoricons.fill.MagnifyingGlass as FillMagnifyingGlass
import com.adamglin.phosphoricons.fill.Wrench as FillWrench
import org.junit.Assert.assertSame
import org.junit.Test

class MainNavigationItemsTest {
    @Test
    fun selectedAndUnselectedBottomBarIconsUseMatchingGlyphs() {
        val expected = mapOf(
            AppTab.SEARCH to (PhosphorIcons.Fill.FillMagnifyingGlass to PhosphorIcons.Bold.BoldMagnifyingGlass),
            AppTab.LIBRARY to (PhosphorIcons.Fill.FillBooks to PhosphorIcons.Bold.BoldBooks),
            AppTab.RECENT to (PhosphorIcons.Fill.FillClockCounterClockwise to PhosphorIcons.Bold.BoldClockCounterClockwise),
            AppTab.TOOLS to (PhosphorIcons.Fill.FillWrench to PhosphorIcons.Bold.BoldWrench),
            AppTab.SETTINGS to (PhosphorIcons.Fill.FillGearSix to PhosphorIcons.Bold.BoldGearSix)
        )

        mainNavigationItems.forEach { item ->
            val selected = item.selectedIcon as ChemIconSpec.Vector
            val unselected = item.unselectedIcon as ChemIconSpec.Vector
            val (selectedIcon, unselectedIcon) = expected.getValue(item.tab)

            assertSame("${item.label} selected icon should use the filled matching glyph", selectedIcon, selected.imageVector)
            assertSame("${item.label} unselected icon should use the bold matching glyph", unselectedIcon, unselected.imageVector)
        }
    }
}

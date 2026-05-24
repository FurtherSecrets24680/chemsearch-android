package com.furthersecrets.chemsearch.ui

import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.bold.MagnifyingGlass
import com.adamglin.phosphoricons.fill.MagnifyingGlass as FillMagnifyingGlass
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

class IconUiTest {
    @Test
    fun appSearchIconUsesPhosphorVector() {
        val icon = ChemAppIcons.Search as ChemIconSpec.Vector

        assertSame(PhosphorIcons.Bold.MagnifyingGlass, icon.imageVector)
    }

    @Test
    fun selectedNavigationIconsUseFilledPhosphorVariants() {
        mainNavigationItems.forEach { item ->
            val selected = item.selectedIcon as ChemIconSpec.Vector
            val unselected = item.unselectedIcon as ChemIconSpec.Vector

            assertNotSame("${item.label} selected icon should differ", selected.imageVector, unselected.imageVector)
        }

        val search = mainNavigationItems.first { it.tab == AppTab.SEARCH }.selectedIcon as ChemIconSpec.Vector
        assertSame(PhosphorIcons.Fill.FillMagnifyingGlass, search.imageVector)
    }
}

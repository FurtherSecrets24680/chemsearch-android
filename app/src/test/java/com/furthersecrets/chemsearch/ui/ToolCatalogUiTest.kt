package com.furthersecrets.chemsearch.ui

import com.furthersecrets.chemsearch.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolCatalogUiTest {
    @Test
    fun defaultToolsDoNotIncludeIsomerFinderBecauseItLivesInSearch() {
        assertFalse(DEFAULT_TOOLS.any { it.titleRes == R.string.ui_isomer_search })
    }

    @Test
    fun visibleToolCategoriesAllHaveTools() {
        val categoriesWithTools = DEFAULT_TOOLS.map { it.category }.toSet()

        assertTrue(TOOL_CATEGORIES.filterNot { it == ToolCategory.ALL }.all { it in categoriesWithTools })
    }

    @Test
    fun custom3dViewerUsesCubeIcon() {
        val customViewer = DEFAULT_TOOLS.first { it.titleRes == R.string.ui_custom_3d_molecule_viewer }

        assertEquals(ChemAppIcons.Cube, customViewer.icon)
    }
}

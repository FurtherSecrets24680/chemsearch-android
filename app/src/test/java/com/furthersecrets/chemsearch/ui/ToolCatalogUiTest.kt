package com.furthersecrets.chemsearch.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolCatalogUiTest {
    @Test
    fun defaultToolsDoNotIncludeIsomerFinderBecauseItLivesInSearch() {
        assertFalse(DEFAULT_TOOLS.any { it.title == "Isomer Finder" })
    }

    @Test
    fun visibleToolCategoriesAllHaveTools() {
        val categoriesWithTools = DEFAULT_TOOLS.map { it.category }.toSet()

        assertTrue(TOOL_CATEGORIES.filterNot { it == ToolCategory.ALL }.all { it in categoriesWithTools })
    }

    @Test
    fun custom3dViewerUsesCubeIcon() {
        val customViewer = DEFAULT_TOOLS.first { it.title == "Custom 3D Molecule Viewer" }

        assertEquals(ChemAppIcons.Cube, customViewer.icon)
    }
}

package com.furthersecrets.chemsearch.data

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryBackupTest {
    @Test
    fun mergesFavoritesWithoutDuplicatingCids() {
        val current = listOf(
            FavoriteCompound(702, "Ethanol", "C2H6O", "46.07", "ethanol")
        )
        val imported = listOf(
            FavoriteCompound(702, "Ethanol duplicate", "C2H6O", "46.07", "ethanol"),
            FavoriteCompound(887, "Methanol", "CH4O", "32.04", "methanol")
        )

        val (merged, importedCount) = mergeFavoritesForImport(current, imported, replace = false)

        assertEquals(2, merged.size)
        assertEquals(1, importedCount)
        assertEquals(887, merged.first().cid)
    }

    @Test
    fun replaceModeUsesImportedFavoritesOnly() {
        val current = listOf(
            FavoriteCompound(702, "Ethanol", "C2H6O", "46.07", "ethanol")
        )
        val imported = listOf(
            FavoriteCompound(887, "Methanol", "CH4O", "32.04", "methanol")
        )

        val (merged, importedCount) = mergeFavoritesForImport(current, imported, replace = true)

        assertEquals(1, merged.size)
        assertEquals(1, importedCount)
        assertEquals(887, merged.first().cid)
    }
}

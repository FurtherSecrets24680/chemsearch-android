package com.furthersecrets.chemsearch.data

data class LibrarySelectionItem(
    val key: String,
    val query: String,
    val label: String
)

fun shouldShowLibraryCompareButton(selectedCount: Int): Boolean = selectedCount >= 2

fun buildLibraryCompareQueries(items: Collection<LibrarySelectionItem>): List<String> =
    items
        .map { it.query.trim() }
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase() }

fun FavoriteCompound.toLibrarySelectionItem(): LibrarySelectionItem =
    LibrarySelectionItem(
        key = "favorite:$cid",
        query = name.ifBlank { cid.toString() },
        label = name.ifBlank { "CID $cid" }
    )

fun DownloadedCompound.toLibrarySelectionItem(): LibrarySelectionItem =
    LibrarySelectionItem(
        key = "download:$cid",
        query = name.ifBlank { cid.toString() },
        label = name.ifBlank { "CID $cid" }
    )

fun ChemicalDbEntry.toLibrarySelectionItem(): LibrarySelectionItem =
    LibrarySelectionItem(
        key = "database:${category.name}:$id",
        query = searchQuery.ifBlank {
            when {
                formula.isNotBlank() -> formula
                actionValue.isNotBlank() -> actionValue
                else -> title
            }
        },
        label = title.ifBlank { searchQuery.ifBlank { formula.ifBlank { id } } }
    )

fun ChemicalDbEntry.toComparableLibrarySelectionItem(): LibrarySelectionItem? =
    if (category == ChemicalDbCategory.SUBSTANCES || category == ChemicalDbCategory.IONS) {
        toLibrarySelectionItem()
    } else {
        null
    }

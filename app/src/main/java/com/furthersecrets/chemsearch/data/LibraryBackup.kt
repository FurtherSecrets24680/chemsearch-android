package com.furthersecrets.chemsearch.data

data class LibraryBackup(
    val format: String = LIBRARY_BACKUP_FORMAT,
    val schemaVersion: Int = LIBRARY_BACKUP_SCHEMA_VERSION,
    val exportedAt: Long = System.currentTimeMillis(),
    val appVersionName: String = "",
    val appVersionCode: Int = 0,
    val favorites: List<FavoriteCompound> = emptyList(),
    val downloads: List<DownloadedCompound> = emptyList()
)

data class LibraryImportResult(
    val favoriteCount: Int,
    val downloadCount: Int,
    val skippedFavorites: Int = 0,
    val skippedDownloads: Int = 0
) {
    val totalImported: Int
        get() = favoriteCount + downloadCount
}

const val LIBRARY_BACKUP_FORMAT = "chemsearch_library"
const val LIBRARY_BACKUP_SCHEMA_VERSION = 1

fun mergeFavoritesForImport(
    current: List<FavoriteCompound>,
    imported: List<FavoriteCompound>,
    replace: Boolean
): Pair<List<FavoriteCompound>, Int> {
    val normalizedImported = imported
        .filter { it.cid > 0 && it.name.isNotBlank() }
        .distinctBy { it.cid }
    if (replace) return normalizedImported to normalizedImported.size

    val existingCids = current.map { it.cid }.toSet()
    val additions = normalizedImported.filterNot { it.cid in existingCids }
    return (additions + current) to additions.size
}

fun mergeDownloadsForImport(
    current: List<DownloadedCompound>,
    imported: List<DownloadedCompound>,
    replace: Boolean
): Pair<List<DownloadedCompound>, Int> {
    val normalizedImported = imported
        .filter { it.cid > 0 && it.name.isNotBlank() }
        .distinctBy { it.cid }
    if (replace) return normalizedImported to normalizedImported.size

    val existingCids = current.map { it.cid }.toSet()
    val additions = normalizedImported.filterNot { it.cid in existingCids }
    return (additions + current) to additions.size
}

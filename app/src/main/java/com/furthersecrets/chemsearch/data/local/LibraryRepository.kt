package com.furthersecrets.chemsearch.data.local

import android.content.SharedPreferences
import com.furthersecrets.chemsearch.BuildConfig
import com.furthersecrets.chemsearch.data.ChemUiState
import com.furthersecrets.chemsearch.data.DownloadedCompound
import com.furthersecrets.chemsearch.data.FavoriteCompound
import com.furthersecrets.chemsearch.data.LibraryBackup
import com.furthersecrets.chemsearch.data.LibraryImportResult
import com.furthersecrets.chemsearch.data.LIBRARY_BACKUP_FORMAT
import com.furthersecrets.chemsearch.data.buildOfflineDownloadMetadata
import com.furthersecrets.chemsearch.data.calcElementalDataFor
import com.furthersecrets.chemsearch.data.getEmpiricalFormulaFor
import com.furthersecrets.chemsearch.data.formatConventionalFormula
import com.furthersecrets.chemsearch.data.mergeDownloadsForImport
import com.furthersecrets.chemsearch.data.mergeFavoritesForImport
import com.furthersecrets.chemsearch.data.withConventionalFormula
import com.furthersecrets.chemsearch.ui.DebugLog
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Single owner of the user's library state: favorites (SharedPreferences JSON),
 * offline downloads (Room via [OfflineDownloadRepository]) and backup import/export.
 * Exposes shared [StateFlow]s so multiple ViewModels observe the same truth.
 */
class LibraryRepository(
    private val prefs: SharedPreferences,
    private val gson: Gson,
    private val offlineDownloadRepository: OfflineDownloadRepository,
    private val scope: CoroutineScope
) {
    private val _favorites = MutableStateFlow(loadFavorites())
    val favorites: StateFlow<List<FavoriteCompound>> = _favorites.asStateFlow()

    private val _downloads = MutableStateFlow<List<DownloadedCompound>>(emptyList())
    val downloads: StateFlow<List<DownloadedCompound>> = _downloads.asStateFlow()

    init {
        scope.launch {
            offlineDownloadRepository.migrateLegacyDownloadsIfNeeded()
        }
        scope.launch {
            offlineDownloadRepository.downloads
                .catch { e -> DebugLog.e("ChemSearch", "Download database read failed: ${e.message}") }
                .collect { downloads ->
                    _downloads.value = downloads.mapNotNull { it.normalizedOrNull() }
                }
        }
    }

    // ---- Favorites ----

    fun toggleFavorite(cid: Long, compound: FavoriteCompound): Boolean {
        val current = _favorites.value.toMutableList()
        val wasFavorite = current.any { it.cid == cid }
        if (wasFavorite) {
            current.removeAll { it.cid == cid }
            DebugLog.d("ChemSearch", "Removed favorite: ${compound.name} (CID $cid)")
        } else {
            current.add(0, compound)
            DebugLog.d("ChemSearch", "Added favorite: ${compound.name} (CID $cid)")
        }
        _favorites.value = current
        saveFavorites(current)
        return !wasFavorite
    }

    fun deleteFavorite(cid: Long) {
        val updated = _favorites.value.filter { it.cid != cid }
        _favorites.value = updated
        saveFavorites(updated)
    }

    fun restoreFavorite(favorite: FavoriteCompound) {
        val normalized = favorite.normalizedOrNull() ?: return
        val updated = listOf(normalized) + _favorites.value.filterNot { it.cid == normalized.cid }
        _favorites.value = updated
        saveFavorites(updated)
        DebugLog.d("ChemSearch", "Restored favorite: ${normalized.name} (CID ${normalized.cid})")
    }

    fun moveFavorite(fromIndex: Int, toIndex: Int) {
        val current = _favorites.value.toMutableList()
        if (fromIndex !in current.indices || toIndex !in current.indices) return
        val item = current.removeAt(fromIndex)
        current.add(toIndex, item)
        _favorites.value = current
        saveFavorites(current)
    }

    fun setFavoriteFlag(cid: Long, isFavorite: Boolean) {
        // Reserved for derived-flag sync if favorites move fully to Room.
    }

    private fun loadFavorites(): List<FavoriteCompound> {
        val json = prefs.getString("favorites", null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<FavoriteCompound>>() {}.type
            val favorites = gson.fromJson<List<FavoriteCompound>>(json, type) ?: emptyList()
            favorites.mapNotNull { it.normalizedOrNull() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveFavorites(list: List<FavoriteCompound>) {
        prefs.edit().putString("favorites", gson.toJson(list)).apply()
    }

    fun FavoriteCompound.normalizedOrNull(): FavoriteCompound? {
        val safeCid = runCatching { cid }.getOrNull()?.takeIf { it > 0L } ?: return null
        val safeFormula = runCatching { formula }.getOrNull()?.trim().orEmpty()
        val safeName = runCatching { name }.getOrNull()?.trim().orEmpty()
        val safeWeight = runCatching { molecularWeight }.getOrNull()?.trim().orEmpty()
        val safeIupacName = runCatching { iupacName }.getOrNull()?.trim().orEmpty()
        val safeSavedAt = runCatching { savedAt }.getOrNull()?.takeIf { it > 0L }
            ?: System.currentTimeMillis()
        val conventional = formatConventionalFormula(safeFormula)
        return FavoriteCompound(
            cid = safeCid,
            name = safeName.ifBlank { conventional.ifBlank { "CID $safeCid" } },
            formula = conventional,
            molecularWeight = safeWeight,
            iupacName = safeIupacName,
            savedAt = safeSavedAt
        )
    }

    // ---- Downloads ----

    fun upsertDownload(item: DownloadedCompound) {
        _downloads.value = listOf(item) + _downloads.value.filterNot { it.cid == item.cid }
        scope.launch(Dispatchers.IO) { offlineDownloadRepository.upsert(item) }
    }

    fun deleteDownload(cid: Long) {
        _downloads.value = _downloads.value.filterNot { it.cid == cid }
        scope.launch(Dispatchers.IO) { offlineDownloadRepository.delete(cid) }
        DebugLog.d("ChemSearch", "Deleted offline download for CID $cid")
    }

    fun restoreDownload(download: DownloadedCompound) {
        val normalized = download.normalizedOrNull() ?: return
        _downloads.value = listOf(normalized) + _downloads.value.filterNot { it.cid == normalized.cid }
        scope.launch(Dispatchers.IO) { offlineDownloadRepository.upsert(normalized) }
        DebugLog.d("ChemSearch", "Restored offline download: ${normalized.name} (CID ${normalized.cid})")
    }

    fun findDownload(cid: Long): DownloadedCompound? = _downloads.value.firstOrNull { it.cid == cid }

    fun DownloadedCompound.normalizedOrNull(): DownloadedCompound? {
        val safeCid = runCatching { cid }.getOrNull()?.takeIf { it > 0L } ?: return null
        val rawState = runCatching { state }.getOrNull()
        val safeName = runCatching { name }.getOrNull()?.trim().orEmpty()
        val safeFormula = runCatching { formula }.getOrNull()?.trim().orEmpty()
        val safeWeight = runCatching { molecularWeight }.getOrNull()?.trim().orEmpty()
        val safeIupacName = runCatching { iupacName }.getOrNull()?.trim().orEmpty()
        val safeSavedAt = runCatching { savedAt }.getOrNull()?.takeIf { it > 0L }
            ?: System.currentTimeMillis()
        val safeStructurePngBase64 = runCatching { structurePngBase64 }.getOrNull()
        val fallbackState = ChemUiState(
            cid = safeCid,
            name = safeName,
            formula = safeFormula,
            weight = safeWeight,
            iupacName = safeIupacName,
            hasResult = true,
            isOfflineDownload = true
        )
        val normalizedState = runCatching {
            (rawState ?: fallbackState).copy(
                cid = rawState?.cid ?: safeCid,
                name = runCatching { rawState?.name }.getOrNull()?.trim().orEmpty()
                    .ifBlank { safeName },
                formula = runCatching { rawState?.formula }.getOrNull()?.trim().orEmpty()
                    .ifBlank { safeFormula },
                weight = runCatching { rawState?.weight }.getOrNull()?.trim().orEmpty()
                    .ifBlank { safeWeight },
                iupacName = runCatching { rawState?.iupacName }.getOrNull()?.trim().orEmpty()
                    .ifBlank { safeIupacName },
                hasResult = true,
                isOfflineDownload = true,
                isLoading = false,
                error = null,
                isLoadingDesc = false,
                isLoadingSdf = false,
                isLoadingSafety = false,
                isLoadingSynonyms = false
            ).withConventionalFormula()
        }.getOrElse { fallbackState.withConventionalFormula() }
        val normalizedFormula = formatConventionalFormula(
            safeFormula.ifBlank { normalizedState.formula }
        )
        val restoredMetadata = runCatching { offlineMetadata }.getOrNull()
        return DownloadedCompound(
            cid = safeCid,
            name = safeName.ifBlank { normalizedState.name.ifBlank { "CID $safeCid" } },
            formula = normalizedFormula,
            molecularWeight = safeWeight.ifBlank { normalizedState.weight },
            iupacName = safeIupacName.ifBlank { normalizedState.iupacName },
            savedAt = safeSavedAt,
            state = normalizedState.copy(formula = normalizedFormula),
            structurePngBase64 = safeStructurePngBase64,
            offlineMetadata = restoredMetadata ?: buildOfflineDownloadMetadata(normalizedState, safeSavedAt)
        )
    }

    private fun loadLegacyDownloads(): List<DownloadedCompound> {
        val json = prefs.getString("downloads", null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<DownloadedCompound>>() {}.type
            val restored = gson.fromJson<List<DownloadedCompound>>(json, type) ?: emptyList()
            restored.mapNotNull { it.normalizedOrNull() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    // ---- Backup import/export ----

    fun buildBackupJson(): String =
        gson.toJson(
            LibraryBackup(
                appVersionName = BuildConfig.VERSION_NAME,
                appVersionCode = BuildConfig.VERSION_CODE,
                favorites = _favorites.value,
                downloads = _downloads.value
            )
        )

    /**
     * Builds a spreadsheet-friendly CSV of the whole library (favorites +
     * downloads), one row per compound. RFC 4180 quoting; formulas kept as
     * plain text so they can be processed further in spreadsheet tools.
     */
    fun buildLibraryCsv(): String {
        fun cell(raw: String?): String {
            val value = raw.orEmpty()
            return if (value.any { it in ",\"\n\r" }) {
                "\"" + value.replace("\"", "\"\"") + "\""
            } else value
        }
        val header = "Type,CID,Name,Formula,IUPAC Name,Molecular Weight,Saved At"
        val rows = buildList {
            _favorites.value.forEach { fav ->
                add(
                    listOf(
                        "favorite",
                        fav.cid.toString(),
                        fav.name,
                        fav.formula,
                        fav.iupacName,
                        fav.molecularWeight,
                        formatCsvTimestamp(fav.savedAt)
                    )
                )
            }
            _downloads.value.forEach { item ->
                add(
                    listOf(
                        "download",
                        item.cid.toString(),
                        item.name,
                        item.formula,
                        item.iupacName,
                        item.molecularWeight,
                        formatCsvTimestamp(item.savedAt)
                    )
                )
            }
        }
        return (listOf(header) + rows.map { row -> row.joinToString(",") { cell(it) } })
            .joinToString("\r\n")
    }

    private fun formatCsvTimestamp(epochMs: Long): String =
        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
            .format(java.util.Date(epochMs))

    fun importBackup(
        rawJson: String,
        replace: Boolean
    ): Result<LibraryImportResult> = runCatching {
        val backup = gson.fromJson(rawJson, LibraryBackup::class.java)
            ?: throw IllegalArgumentException("Invalid library backup")
        if (backup.format != LIBRARY_BACKUP_FORMAT) {
            throw IllegalArgumentException("Not a ChemSearch library backup")
        }

        val (mergedFavorites, importedFavorites) = mergeFavoritesForImport(
            current = _favorites.value,
            imported = backup.favorites,
            replace = replace
        )
        val (mergedDownloads, importedDownloads) = mergeDownloadsForImport(
            current = _downloads.value,
            imported = backup.downloads,
            replace = replace
        )

        _favorites.value = mergedFavorites
        saveFavorites(mergedFavorites)
        _downloads.value = mergedDownloads
        val importedCids = backup.downloads.map { it.cid }.toSet()
        scope.launch(Dispatchers.IO) {
            if (replace) {
                offlineDownloadRepository.replaceAll(mergedDownloads)
            } else {
                offlineDownloadRepository.upsertAll(mergedDownloads.filter { it.cid in importedCids })
            }
        }

        LibraryImportResult(
            favoriteCount = importedFavorites,
            downloadCount = importedDownloads,
            skippedFavorites = backup.favorites.size - importedFavorites,
            skippedDownloads = backup.downloads.size - importedDownloads
        )
    }
}

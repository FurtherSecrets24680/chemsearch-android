package com.furthersecrets.chemsearch.data.local

import android.content.SharedPreferences
import com.furthersecrets.chemsearch.data.DownloadedCompound
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class OfflineDownloadRepository(
    private val dao: DownloadedCompoundDao,
    private val prefs: SharedPreferences,
    private val gson: Gson
) {
    val downloads: Flow<List<DownloadedCompound>> =
        dao.observeDownloads().map { entities -> entities.map { it.toDomain(gson) } }

    suspend fun upsert(download: DownloadedCompound) {
        dao.upsert(download.toEntity(gson))
    }

    suspend fun replaceAll(downloads: List<DownloadedCompound>) {
        dao.clearAll()
        val entities = downloads.mapNotNull { download ->
            runCatching { download.toEntity(gson) }.getOrNull()
        }
        if (entities.isNotEmpty()) {
            dao.upsertAll(entities)
        }
    }

    suspend fun upsertAll(downloads: List<DownloadedCompound>) {
        val entities = downloads.mapNotNull { download ->
            runCatching { download.toEntity(gson) }.getOrNull()
        }
        if (entities.isNotEmpty()) {
            dao.upsertAll(entities)
        }
    }

    suspend fun delete(cid: Long) {
        dao.delete(cid)
    }

    suspend fun migrateLegacyDownloadsIfNeeded() {
        if (prefs.getBoolean(PREF_ROOM_MIGRATED, false)) return
        val legacyDownloads = loadLegacyDownloads()
        val entities = legacyDownloads.mapNotNull { download ->
            runCatching { download.toEntity(gson) }.getOrNull()
        }
        if (entities.isNotEmpty()) {
            dao.upsertAll(entities)
        }
        prefs.edit().putBoolean(PREF_ROOM_MIGRATED, true).apply()
    }

    private fun loadLegacyDownloads(): List<DownloadedCompound> {
        val json = prefs.getString(PREF_LEGACY_DOWNLOADS, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<DownloadedCompound>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    companion object {
        private const val PREF_LEGACY_DOWNLOADS = "downloads"
        private const val PREF_ROOM_MIGRATED = "downloads_room_migrated"
    }
}

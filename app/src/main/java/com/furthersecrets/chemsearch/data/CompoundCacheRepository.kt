package com.furthersecrets.chemsearch.data

import android.content.Context
import android.content.SharedPreferences
import com.furthersecrets.chemsearch.ui.DebugLog
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Owns the on-disk compound cache: per-CID JSON snapshots, directory selection,
 * size computation and retention/size-limit policy enforcement.
 */
class CompoundCacheRepository(
    context: Context,
    private val prefs: SharedPreferences,
    private val gson: Gson
) {
    private val appCacheDir: File = context.cacheDir

    val cacheDir: File
        get() {
            val custom = prefs.getString("cache_dir", null)
            val dir = if (!custom.isNullOrBlank()) File(custom) else File(appCacheDir, "compound_cache")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    fun cachedDirPath(): String = prefs.getString("cache_dir", null) ?: ""

    fun computeSizeBlocking(): Long =
        runCatching { cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() } }.getOrDefault(0L)

    fun clearAll() {
        cacheDir.walkTopDown().filter { it.isFile }.forEach { it.delete() }
    }

    fun canUseDirectory(path: String): Boolean {
        val target = File(path)
        return runCatching {
            if (!target.exists()) target.mkdirs()
            if (!target.isDirectory) return@runCatching false
            val probe = File(target, ".chemsearch_write_test")
            probe.writeText("ok")
            probe.delete()
            true
        }.getOrDefault(false)
    }

    fun setDirPath(path: String) {
        if (path.isBlank()) {
            prefs.edit().remove("cache_dir").apply()
        } else {
            prefs.edit().putString("cache_dir", path).apply()
        }
    }

    suspend fun read(cid: Long): ChemUiState? = withContext(Dispatchers.IO) {
        val file = File(cacheDir, "$cid.json")
        if (!file.exists()) return@withContext null
        try {
            val json = file.readText()
            gson.fromJson(json, ChemUiState::class.java)?.withConventionalFormula()
        } catch (e: Exception) {
            DebugLog.e("ChemSearch", "Cache read failed for CID $cid: ${e.message}")
            null
        }
    }

    suspend fun findByName(query: String): ChemUiState? = withContext(Dispatchers.IO) {
        val q = query.trim().lowercase()
        try {
            cacheDir.listFiles()
                ?.filter { it.isFile && it.extension == "json" }
                ?.asSequence()
                ?.mapNotNull { file ->
                    runCatching {
                        gson.fromJson(file.readText(), ChemUiState::class.java)?.withConventionalFormula()
                    }.getOrNull()
                }
                ?.firstOrNull { state ->
                    state.name.lowercase() == q ||
                        state.synonyms.any { it.lowercase() == q } ||
                        state.casNumber?.lowercase() == q ||
                        state.cid?.toString() == q
                }
        } catch (e: Exception) {
            DebugLog.e("ChemSearch", "Cache name search failed: ${e.message}")
            null
        }
    }

    suspend fun write(state: ChemUiState, sizeLimit: CacheSizeLimit, retention: CacheRetention): Long? {
        val cid = state.cid ?: return null
        return try {
            withContext(Dispatchers.IO) {
                val file = File(cacheDir, "$cid.json")
                file.writeText(gson.toJson(state))
                enforcePolicyBlocking(sizeLimit, retention)
                file.length()
            }
        } catch (e: Exception) {
            DebugLog.e("ChemSearch", "Cache write failed: ${e.message}")
            null
        }
    }

    fun enforcePolicyBlocking(sizeLimit: CacheSizeLimit, retention: CacheRetention) {
        val files = cacheDir.listFiles()
            ?.filter { it.isFile && it.extension == "json" }
            .orEmpty()
        if (files.isEmpty()) return

        val retentionCutoff = retention.maxAgeMillis?.let { System.currentTimeMillis() - it }
        val retainedFiles = if (retentionCutoff != null) {
            files.filter { file ->
                val expired = file.lastModified() in 1 until retentionCutoff
                if (expired) file.delete()
                !expired
            }
        } else {
            files
        }

        val maxBytes = sizeLimit.maxBytes ?: return
        var totalBytes = retainedFiles.sumOf { it.length() }
        if (totalBytes <= maxBytes) return
        retainedFiles
            .sortedBy { it.lastModified().takeIf { modified -> modified > 0L } ?: Long.MAX_VALUE }
            .forEach { file ->
                if (totalBytes <= maxBytes) return@forEach
                val length = file.length()
                if (file.delete()) totalBytes -= length
            }
    }
}

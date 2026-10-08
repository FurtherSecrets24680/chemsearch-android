package com.furthersecrets.chemsearch.data

import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/** Owns the recent-searches list and its SharedPreferences/legacy-history persistence. */
class RecentSearchesRepository(
    private val prefs: SharedPreferences,
    private val gson: Gson
) {
    fun load(): List<RecentSearch> {
        val json = prefs.getString(PREF_RECENT_SEARCHES, null)
        val stored = if (json.isNullOrBlank()) {
            emptyList()
        } else {
            runCatching {
                val type = object : TypeToken<List<RecentSearch>>() {}.type
                gson.fromJson<List<RecentSearch>>(json, type)
            }.getOrNull().orEmpty()
        }

        if (stored.isNotEmpty()) return stored
            .mapNotNull { it.normalizedOrNull() }
            .distinctBy { it.query.lowercase() }

        return loadLegacyHistory().map { query ->
            RecentSearch(query = query, lastSearchedAt = 0L, pinned = false)
        }
    }

    fun save(searches: List<RecentSearch>) {
        val cleaned = searches
            .mapNotNull { it.normalizedOrNull() }
            .distinctBy { it.query.lowercase() }
        prefs.edit()
            .putString(PREF_RECENT_SEARCHES, gson.toJson(cleaned))
            .putString(PREF_HISTORY, cleaned.joinToString("||") { it.query })
            .apply()
    }

    fun clear() {
        prefs.edit().remove(PREF_HISTORY).remove(PREF_RECENT_SEARCHES).apply()
    }

    private fun loadLegacyHistory(): List<String> =
        prefs.getString(PREF_HISTORY, "")?.split("||")?.filter { it.isNotBlank() } ?: emptyList()

    fun RecentSearch.normalizedOrNull(): RecentSearch? {
        val safeQuery = runCatching { query }.getOrNull()?.trim().orEmpty()
        if (safeQuery.isBlank()) return null
        return RecentSearch(
            query = safeQuery,
            lastSearchedAt = runCatching { lastSearchedAt }.getOrNull()?.takeIf { it > 0L }
                ?: System.currentTimeMillis(),
            pinned = runCatching { pinned }.getOrNull() ?: false
        )
    }

    companion object {
        private const val PREF_HISTORY = "history"
        private const val PREF_RECENT_SEARCHES = "recent_searches"
    }
}

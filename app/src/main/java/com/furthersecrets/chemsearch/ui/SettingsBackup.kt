package com.furthersecrets.chemsearch.ui

import android.content.SharedPreferences
import com.furthersecrets.chemsearch.BuildConfig
import com.furthersecrets.chemsearch.data.AiProvider
import com.furthersecrets.chemsearch.data.SecurePrefs
import org.json.JSONArray
import org.json.JSONObject

private val SECRET_PREF_KEYS = AiProvider.entries.map { it.keyPref }.toSet()

internal fun buildSettingsBackupJson(prefs: SharedPreferences): String {
    val root = JSONObject()
    root.put("format", "chemsearch_settings")
    root.put("version", 2)
    root.put("exported_at", System.currentTimeMillis())
    root.put("app_version_name", BuildConfig.VERSION_NAME)
    root.put("app_version_code", BuildConfig.VERSION_CODE)
    root.put("includes_api_keys", true)
    val entries = JSONObject()
    val apiKeys = JSONObject()

    prefs.all.toSortedMap().forEach { (key, value) ->
        if (key in SECRET_PREF_KEYS) {
            SecurePrefs.getString(prefs, key)
                ?.takeIf { it.isNotBlank() }
                ?.let { apiKeys.put(key, it) }
            return@forEach
        }
        val item = JSONObject()
        when (value) {
            is Boolean -> {
                item.put("type", "boolean")
                item.put("value", value)
            }
            is Int -> {
                item.put("type", "int")
                item.put("value", value)
            }
            is Long -> {
                item.put("type", "long")
                item.put("value", value)
            }
            is Float -> {
                item.put("type", "float")
                item.put("value", value)
            }
            is String -> {
                item.put("type", "string")
                item.put("value", value)
            }
            is Set<*> -> {
                item.put("type", "string_set")
                val arr = JSONArray()
                value.filterIsInstance<String>().forEach(arr::put)
                item.put("value", arr)
            }
            else -> return@forEach
        }
        entries.put(key, item)
    }

    root.put("entries", entries)
    root.put("api_keys", apiKeys)
    root.put("included_sensitive_keys", JSONArray().apply {
        SECRET_PREF_KEYS.sorted()
            .filter { apiKeys.has(it) }
            .forEach(::put)
    })
    return root.toString(2)
}

internal fun restoreSettingsFromBackup(
    prefs: SharedPreferences,
    rawJson: String
): Int {
    val root = JSONObject(rawJson)
    val entries = root.optJSONObject("entries")
        ?: throw IllegalArgumentException("Invalid settings backup file.")
    val apiKeys = root.optJSONObject("api_keys")
    val shouldPreserveExistingSecrets = apiKeys == null
    val preservedSecrets = if (shouldPreserveExistingSecrets) SECRET_PREF_KEYS.mapNotNull { key ->
        prefs.getString(key, null)?.let { key to it }
    }.toMap() else emptyMap()
    val editor = prefs.edit().clear()
    var restored = 0

    val keys = entries.keys()
    while (keys.hasNext()) {
        val key = keys.next()
        if (key in SECRET_PREF_KEYS) continue
        val item = entries.optJSONObject(key) ?: continue
        when (item.optString("type")) {
            "boolean" -> editor.putBoolean(key, item.optBoolean("value"))
            "int" -> editor.putInt(key, item.optInt("value"))
            "long" -> editor.putLong(key, item.optLong("value"))
            "float" -> editor.putFloat(key, item.optDouble("value").toFloat())
            "string" -> editor.putString(key, item.optString("value"))
            "string_set" -> {
                val set = buildSet {
                    val arr = item.optJSONArray("value") ?: JSONArray()
                    for (idx in 0 until arr.length()) {
                        add(arr.optString(idx))
                    }
                }
                editor.putStringSet(key, set)
            }
            else -> continue
        }
        restored++
    }

    preservedSecrets.forEach { (key, value) ->
        editor.putString(key, value)
    }

    editor.apply()

    if (apiKeys != null) {
        SECRET_PREF_KEYS.forEach { key ->
            val value = apiKeys.optString(key, "").trim()
            if (value.isNotBlank()) {
                SecurePrefs.putString(prefs, key, value)
                restored++
            } else {
                SecurePrefs.remove(prefs, key)
            }
        }
    }

    return restored
}

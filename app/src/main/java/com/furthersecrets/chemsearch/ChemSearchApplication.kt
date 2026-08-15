package com.furthersecrets.chemsearch

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import java.util.Locale

class ChemSearchApplication : Application() {
    override fun attachBaseContext(base: Context) {
        val languageKey = base
            .getSharedPreferences("chemsearch_prefs", Context.MODE_PRIVATE)
            .getString("language", "system")
        super.attachBaseContext(base.withAppLanguage(languageKey))
    }
}

internal fun Context.withAppLanguage(languageKey: String?): Context {
    if (languageKey.isNullOrBlank() || languageKey == "system") return this
    val configuration = Configuration(resources.configuration)
    configuration.setLocale(Locale.forLanguageTag(languageKey))
    return createConfigurationContext(configuration)
}
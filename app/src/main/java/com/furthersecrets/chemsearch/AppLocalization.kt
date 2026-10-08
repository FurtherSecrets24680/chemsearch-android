package com.furthersecrets.chemsearch

import android.content.Context
import android.content.SharedPreferences
import com.furthersecrets.chemsearch.data.AppLanguage

/** Central helper for resolving localized strings that respect the in-app language override. */
object AppLocalization {

    fun localizedContext(context: Context, prefs: SharedPreferences): Context {
        val languageKey = prefs.getString("language", AppLanguage.SYSTEM.preferenceKey)
        return context.withAppLanguage(languageKey)
    }

    fun string(context: Context, prefs: SharedPreferences, resId: Int): String =
        localizedContext(context, prefs).getString(resId)

    fun string(context: Context, prefs: SharedPreferences, resId: Int, vararg formatArgs: Any): String =
        localizedContext(context, prefs).getString(resId, *formatArgs)
}

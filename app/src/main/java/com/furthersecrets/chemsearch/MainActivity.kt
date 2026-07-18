package com.furthersecrets.chemsearch

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.furthersecrets.chemsearch.ui.ChemSearchTheme
import com.furthersecrets.chemsearch.ui.MainScreen
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val vm: ChemViewModel by viewModels()

    override fun attachBaseContext(newBase: Context) {
        val languageKey = newBase
            .getSharedPreferences("chemsearch_prefs", Context.MODE_PRIVATE)
            .getString("language", "system")
        super.attachBaseContext(newBase.withAppLanguage(languageKey))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            val isDark by vm.isDarkTheme.collectAsStateWithLifecycle()
            val colorScheme by vm.colorScheme.collectAsStateWithLifecycle()
            val oledDarkTheme by vm.oledDarkTheme.collectAsStateWithLifecycle()
            val highContrastOutlines by vm.highContrastOutlines.collectAsStateWithLifecycle()
            ChemSearchTheme(
                darkTheme = isDark,
                colorScheme = colorScheme,
                oledDarkTheme = oledDarkTheme,
                highContrastOutlines = highContrastOutlines
            ) {
                MainScreen(vm = vm)
            }
        }
    }
}

private fun Context.withAppLanguage(languageKey: String?): Context {
    if (languageKey.isNullOrBlank() || languageKey == "system") return this
    val configuration = Configuration(resources.configuration)
    configuration.setLocale(Locale.forLanguageTag(languageKey))
    return createConfigurationContext(configuration)
}

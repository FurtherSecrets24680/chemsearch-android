package com.furthersecrets.chemsearch

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.furthersecrets.chemsearch.ui.ChemSearchTheme
import com.furthersecrets.chemsearch.ui.ChemLaunchRequest
import com.furthersecrets.chemsearch.ui.MainScreen
import kotlinx.coroutines.flow.MutableStateFlow

/** Keys used by app shortcuts and widgets to deep-link into a specific screen. */
object ChemLaunchContract {
    const val EXTRA_TAB = "chemsearch_extra_tab"
    const val EXTRA_TOOL = "chemsearch_extra_tool"
    const val EXTRA_QUERY = "chemsearch_extra_query"
    const val EXTRA_CID = "chemsearch_extra_cid"

    const val TAB_SEARCH = "search"
    const val TAB_LIBRARY = "library"
    const val TAB_TOOLS = "tools"
    const val TAB_SETTINGS = "settings"
}

class MainActivity : ComponentActivity() {
    private val vm: ChemViewModel by viewModels()
    private val launchRequests = MutableStateFlow<ChemLaunchRequest?>(null)

    override fun attachBaseContext(newBase: Context) {
        val languageKey = newBase
            .getSharedPreferences("chemsearch_prefs", Context.MODE_PRIVATE)
            .getString("language", "system")
        super.attachBaseContext(newBase.withAppLanguage(languageKey))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        parseLaunchIntent(intent)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            val isDark by vm.isDarkTheme.collectAsStateWithLifecycle()
            val colorScheme by vm.colorScheme.collectAsStateWithLifecycle()
            val oledDarkTheme by vm.oledDarkTheme.collectAsStateWithLifecycle()
            val highContrastOutlines by vm.highContrastOutlines.collectAsStateWithLifecycle()
            val cardsEnabled by vm.cardsEnabled.collectAsStateWithLifecycle()
            val launchRequest by launchRequests.collectAsStateWithLifecycle()
            ChemSearchTheme(
                darkTheme = isDark,
                colorScheme = colorScheme,
                oledDarkTheme = oledDarkTheme,
                highContrastOutlines = highContrastOutlines,
                cardsEnabled = cardsEnabled
            ) {
                MainScreen(
                    vm = vm,
                    launchRequest = launchRequest,
                    onLaunchRequestConsumed = { launchRequests.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        parseLaunchIntent(intent)
    }

    private fun parseLaunchIntent(intent: Intent?) {
        val tab = intent?.getStringExtra(ChemLaunchContract.EXTRA_TAB)
        val tool = intent?.getIntExtra(ChemLaunchContract.EXTRA_TOOL, -1)?.takeIf { it > 0 }
        val query = intent?.getStringExtra(ChemLaunchContract.EXTRA_QUERY)
        val cid = intent?.getLongExtra(ChemLaunchContract.EXTRA_CID, -1L)?.takeIf { it > 0 }
        if (tab == null && tool == null && query == null && cid == null) return
        launchRequests.value = ChemLaunchRequest(
            tab = tab,
            toolId = tool,
            query = query,
            cid = cid
        )
    }
}

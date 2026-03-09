package com.furthersecrets.chemsearch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import com.furthersecrets.chemsearch.ui.ChemSearchTheme
import com.furthersecrets.chemsearch.ui.MainScreen

class MainActivity : ComponentActivity() {
    private val vm: ChemViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            val isDark by vm.isDarkTheme.collectAsState()
            ChemSearchTheme(darkTheme = isDark) {
                MainScreen(vm = vm)
            }
        }
    }
}
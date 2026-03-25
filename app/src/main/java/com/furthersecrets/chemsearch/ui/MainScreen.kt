package com.furthersecrets.chemsearch.ui

import android.app.Activity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.furthersecrets.chemsearch.ChemViewModel
import androidx.activity.compose.BackHandler
import com.furthersecrets.chemsearch.data.AiProvider
import com.furthersecrets.chemsearch.data.DescSource

enum class AppTab { SEARCH, FAVORITES, RECENT, TOOLS, SETTINGS }

// Root

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: ChemViewModel = viewModel()) {
    val state by vm.uiState.collectAsState()
    val query by vm.query.collectAsState()
    val isDark by vm.isDarkTheme.collectAsState()
    val autoSuggest by vm.autoSuggest.collectAsState()
    val defaultDescSource by vm.defaultDescSource.collectAsState()
    val hasGeminiKey by vm.hasGeminiKey.collectAsState()
    val hasGroqKey by vm.hasGroqKey.collectAsState()
    val cacheSizeBytes by vm.cacheSizeBytes.collectAsState()
    val cacheDirPath by vm.cacheDirPath.collectAsState()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val snackbar = remember { SnackbarHostState() }
    val favorites by vm.favorites.collectAsState()
    val isFavorite by vm.isFavorite.collectAsState()

    var showGeminiKeyDialog by remember { mutableStateOf(false) }
    var showGroqKeyDialog by remember { mutableStateOf(false) }
    var showAiProviderDialog by remember { mutableStateOf(false) }
    var showSuggestions by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showFavorites by remember { mutableStateOf(false) }

    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it); vm.clearError() }
    }

    LaunchedEffect(state.suggestions) {
        if (state.suggestions.isNotEmpty() && !state.isLoading && !state.hasResult) {
            showSuggestions = true
        } else if (state.hasResult || state.isLoading) {
            showSuggestions = false
        }
    }

    if (showGeminiKeyDialog) {
        ApiKeyDialog(
            title = "Gemini API Key",
            link = "aistudio.google.com",
            current = vm.getGeminiKey() ?: "",
            onSave = { key -> vm.saveGeminiKey(key); showGeminiKeyDialog = false; vm.setDescSource(DescSource.AI) },
            onDismiss = { showGeminiKeyDialog = false }
        )
    }

    if (showGroqKeyDialog) {
        ApiKeyDialog(
            title = "Groq API Key",
            link = "console.groq.com",
            current = vm.getGroqKey() ?: "",
            onSave = { key -> vm.saveGroqKey(key); showGroqKeyDialog = false; vm.setDescSource(DescSource.AI) },
            onDismiss = { showGroqKeyDialog = false }
        )
    }

    if (showAiProviderDialog) {
        AiProviderDialog(
            onSelect = { provider ->
                vm.setAiProvider(provider)
                showAiProviderDialog = false
                val key = if (provider == AiProvider.GEMINI) vm.getGeminiKey() else vm.getGroqKey()
                if (key.isNullOrBlank()) {
                    if (provider == AiProvider.GEMINI) showGeminiKeyDialog = true else showGroqKeyDialog = true
                } else {
                    vm.setDescSource(DescSource.AI)
                }
            },
            onDismiss = { showAiProviderDialog = false }
        )
    }

    if (showSettings) {
        SettingsSheet(
            isDark = isDark,
            autoSuggest = autoSuggest,
            defaultDescSource = defaultDescSource,
            aiProvider = state.aiProvider,
            hasGeminiKey = hasGeminiKey,
            hasGroqKey = hasGroqKey,
            onToggleTheme = { vm.toggleTheme() },
            onToggleAutoSuggest = { vm.toggleAutoSuggest() },
            onSetDefaultDesc = { vm.setDefaultDescSource(it) },
            onSetAiProvider = { vm.setAiProvider(it) },
            onSetGeminiKey = { showGeminiKeyDialog = true; showSettings = false },
            onSetGroqKey = { showGroqKeyDialog = true; showSettings = false },
            onClearGeminiKey = { vm.clearGeminiKey() },
            onClearGroqKey = { vm.clearGroqKey() },
            onClearHistory = { vm.clearHistory() },
            onDismiss = { showSettings = false }
        )
    }

    if (showFavorites) {
        FavoritesSheet(
            favorites = favorites,
            onSelect = { name -> vm.search(name); showFavorites = false },
            onDelete = { cid -> vm.deleteFavorite(cid) },
            onDismiss = { showFavorites = false }
        )
    }

    var selectedTab by remember { mutableStateOf(AppTab.SEARCH) }
    var showExitDialog by remember { mutableStateOf(false) }
    var jumpToTool by remember { mutableStateOf(0) }

    BackHandler {
        if (selectedTab != AppTab.SEARCH) {
            selectedTab = AppTab.SEARCH
        } else {
            showExitDialog = true
        }
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Exit ChemSearch?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to exit?") },
            confirmButton = {
                Button(
                    onClick = { (context as? Activity)?.finish() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Exit") }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) { Text("Cancel") }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                listOf(
                    AppTab.SEARCH    to Triple(Icons.Default.Search,    Icons.Default.Search,        "Search"),
                    AppTab.FAVORITES to Triple(Icons.Default.Bookmark,  Icons.Default.BookmarkBorder,"Favorites"),
                    AppTab.RECENT    to Triple(Icons.Default.History,   Icons.Default.History,       "Recent"),
                    AppTab.TOOLS     to Triple(Icons.Default.Build,     Icons.Default.Build,         "Tools"),
                    AppTab.SETTINGS  to Triple(Icons.Default.Settings,  Icons.Default.Settings,      "Settings")

                ).forEach { (tab, triple) ->
                    val (selectedIcon, unselectedIcon, label) = triple
                    val isSelected = selectedTab == tab
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { selectedTab = tab },
                        icon = {
                            Icon(
                                if (isSelected) selectedIcon else unselectedIcon,
                                contentDescription = label,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        label = {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(0.5f),
                            unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(0.5f),
                            indicatorColor = MaterialTheme.colorScheme.primary.copy(0.1f)
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                    (slideInHorizontally(
                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                    ) { it / 5 * direction } + fadeIn(tween(300))) togetherWith
                    (slideOutHorizontally(
                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                    ) { -it / 5 * direction } + fadeOut(tween(200)))
                },
                modifier = Modifier.fillMaxSize()
            ) { tab ->
                if (tab == AppTab.SEARCH) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) { showSuggestions = false; focusManager.clearFocus() },
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 40.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            AppHeader(
                                isDark = isDark,
                                onToggleTheme = { vm.toggleTheme() }
                            )
                        }
                        item {
                            SearchBar(
                                query = query,
                                onQueryChange = {
                                    vm.onQueryChange(it)
                                    if (it.isNotEmpty() && autoSuggest) showSuggestions = true
                                },
                                onSearch = { showSuggestions = false; focusManager.clearFocus(); vm.search() },
                                onClear = { vm.onQueryChange(""); showSuggestions = false }
                            )
                        }

                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() }
                                    ) {
                                        jumpToTool = 6
                                        selectedTab = AppTab.TOOLS
                                    }
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Default.Biotech,
                                    contentDescription = "Isomer Finder",
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "Searching with formula? Use Isomer Finder →",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                )
                            }
                        }

                        if (state.isLoading) {
                            item {
                                Box(
                                    Modifier.fillMaxWidth().padding(vertical = 40.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(32.dp),
                                            strokeWidth = 2.5.dp
                                        )
                                        Text(
                                            "Looking up compound...",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(0.4f)
                                        )
                                    }
                                }
                            }
                        }
                        if (!state.hasResult && !state.isLoading) {
                            item {
                                HistorySection(
                                    history = state.history,
                                    onSelect = { vm.search(it) },
                                    onClear = { vm.clearHistory() }
                                )
                            }
                        }
                        if (state.hasResult) {
                            item { StructureViewer(state, vm) }
                            item {
                                CompoundHeader(
                                    state,
                                    isFavorite,
                                    onToggleFavorite = { vm.toggleFavorite() })
                            }
                            item { IdentifiersSection(state, context) }
                            if (state.elementalData.isNotEmpty()) item { ElementalSection(state.elementalData) }
                            if (state.synonyms.isNotEmpty()) item { SynonymsSection(state.synonyms) }
                            item {
                                DescriptionSection(
                                    state = state,
                                    onPubChem = { vm.setDescSource(DescSource.PUBCHEM) },
                                    onWiki = { vm.setDescSource(DescSource.WIKI) },
                                    onAI = {
                                        val key =
                                            if (state.aiProvider == AiProvider.GEMINI) vm.getGeminiKey() else vm.getGroqKey()
                                        if (key.isNullOrBlank()) showAiProviderDialog = true
                                        else vm.setDescSource(DescSource.AI)
                                    },
                                    onRegenerate = { vm.fetchAiDescription() }
                                )
                            }
                            item {
                                SafetySection(
                                    ghsData = state.ghsData,
                                    isLoading = state.isLoadingSafety
                                )
                            }
                            item { PubChemCredits() }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 40.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            when (tab) {
                                AppTab.RECENT -> HistorySection(
                                    history = state.history,
                                    onSelect = { vm.search(it); selectedTab = AppTab.SEARCH },
                                    onClear = { vm.clearHistory() }
                                )
                                AppTab.FAVORITES -> FavoritesInline(
                                    favorites = favorites,
                                    onSelect = { name -> vm.search(name); selectedTab = AppTab.SEARCH },
                                    onDelete = { cid -> vm.deleteFavorite(cid) }
                                )
                                AppTab.SETTINGS -> SettingsInline(
                                    isDark = isDark,
                                    autoSuggest = autoSuggest,
                                    defaultDescSource = defaultDescSource,
                                    aiProvider = state.aiProvider,
                                    hasGeminiKey = hasGeminiKey,
                                    hasGroqKey = hasGroqKey,
                                    onToggleTheme = { vm.toggleTheme() },
                                    onToggleAutoSuggest = { vm.toggleAutoSuggest() },
                                    onSetDefaultDesc = { vm.setDefaultDescSource(it) },
                                    onSetAiProvider = { vm.setAiProvider(it) },
                                    onSetGeminiKey = { showGeminiKeyDialog = true },
                                    onSetGroqKey = { showGroqKeyDialog = true },
                                    onClearGeminiKey = { vm.clearGeminiKey() },
                                    onClearGroqKey = { vm.clearGroqKey() },
                                    onClearHistory = { vm.clearHistory() },
                                    cacheSizeBytes = cacheSizeBytes,
                                    cacheDir = cacheDirPath,
                                    onClearCache = { vm.clearCache() },
                                    onSetCacheDir = { vm.setCacheDir(it) }
                                )
                                AppTab.TOOLS -> ToolsScreen(
                                    isDark = isDark,
                                    jumpToTool = jumpToTool,
                                    onNavigateToSearch = { selectedTab = AppTab.SEARCH }
                                )
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = showSuggestions && state.suggestions.isNotEmpty(),
                modifier = Modifier
                    .padding(top = 148.dp, start = 16.dp, end = 16.dp)
                    .zIndex(10f),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                SuggestionsDropdown(
                    suggestions = state.suggestions,
                    onSelect = {
                        showSuggestions = false
                        focusManager.clearFocus()
                        vm.search(it)
                    }
                )
            }
        }
    }
}

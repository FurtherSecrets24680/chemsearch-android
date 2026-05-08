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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.furthersecrets.chemsearch.ChemViewModel
import androidx.activity.compose.BackHandler
import com.furthersecrets.chemsearch.data.AiProvider
import com.furthersecrets.chemsearch.data.AppColorScheme
import com.furthersecrets.chemsearch.data.DescSource

enum class AppTab { SEARCH, FAVORITES, RECENT, TOOLS, SETTINGS }

// Root

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: ChemViewModel = viewModel()) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val isDark by vm.isDarkTheme.collectAsStateWithLifecycle()
    val colorScheme by vm.colorScheme.collectAsStateWithLifecycle()
    val autoSuggest by vm.autoSuggest.collectAsStateWithLifecycle()
    val compactMode by vm.compactMode.collectAsStateWithLifecycle()
    val defaultDescSource by vm.defaultDescSource.collectAsStateWithLifecycle()
    val aiKeyStatus by vm.aiKeyStatus.collectAsStateWithLifecycle()
    val aiModelCatalogs by vm.aiModelCatalogs.collectAsStateWithLifecycle()
    val cacheSizeBytes by vm.cacheSizeBytes.collectAsStateWithLifecycle()
    val cacheDirPath by vm.cacheDirPath.collectAsStateWithLifecycle()
    val updateNotificationsEnabled by vm.updateNotificationsEnabled.collectAsStateWithLifecycle()
    val updateStatus by vm.updateStatus.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val snackbar = remember { SnackbarHostState() }
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val isFavorite by vm.isFavorite.collectAsStateWithLifecycle()

    var editingAiKeyProvider by remember { mutableStateOf<AiProvider?>(null) }
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

    editingAiKeyProvider?.let { provider ->
        ApiKeyDialog(
            title = "${provider.displayName} API Key",
            link = provider.helpHost,
            current = vm.getAiKey(provider) ?: "",
            onSave = { key ->
                vm.saveAiKey(provider, key)
                vm.setAiProvider(provider)
                vm.refreshAiModels(provider)
                editingAiKeyProvider = null
                vm.setDescSource(DescSource.AI)
            },
            onDismiss = { editingAiKeyProvider = null }
        )
    }

    if (showAiProviderDialog) {
        AiProviderDialog(
            selectedProvider = state.aiProvider,
            keyStatus = aiKeyStatus,
            onSelect = { provider ->
                vm.setAiProvider(provider)
                showAiProviderDialog = false
                if (vm.hasAiKey(provider)) {
                    vm.setDescSource(DescSource.AI)
                } else {
                    editingAiKeyProvider = provider
                }
            },
            onEditKey = { provider ->
                showAiProviderDialog = false
                editingAiKeyProvider = provider
            },
            onDismiss = { showAiProviderDialog = false }
        )
    }

    if (showSettings) {
        SettingsSheet(
            isDark = isDark,
            colorScheme = colorScheme,
            autoSuggest = autoSuggest,
            compactMode = compactMode,
            defaultDescSource = defaultDescSource,
            aiProvider = state.aiProvider,
            aiKeyStatus = aiKeyStatus,
            aiModelCatalogs = aiModelCatalogs,
            updateNotificationsEnabled = updateNotificationsEnabled,
            updateStatus = updateStatus,
            onToggleTheme = { vm.toggleTheme() },
            onSetColorScheme = { vm.setColorScheme(it) },
            onToggleAutoSuggest = { vm.toggleAutoSuggest() },
            onToggleCompactMode = { vm.setCompactMode(!compactMode) },
            onSetDefaultDesc = { vm.setDefaultDescSource(it) },
            onSetAiProvider = { vm.setAiProvider(it) },
            onSetAiModel = { provider, model -> vm.setAiModel(provider, model) },
            onRefreshAiModels = { provider -> vm.refreshAiModels(provider) },
            onEditAiKey = { provider -> editingAiKeyProvider = provider; showSettings = false },
            onClearAiKey = { provider -> vm.clearAiKey(provider) },
            onClearHistory = { vm.clearHistory() },
            onToggleUpdateNotifications = { enabled -> vm.setUpdateNotificationsEnabled(enabled) },
            onCheckForUpdates = { vm.checkForUpdates(manual = true) },
            onDismiss = { showSettings = false }
        )
    }

    if (showFavorites) {
        FavoritesSheet(
            favorites = favorites,
            onSelect = { name -> vm.search(name); showFavorites = false },
            onDelete = { cid -> vm.deleteFavorite(cid) },
            onMoveFavorite = { from, to -> vm.moveFavorite(from, to) },
            onDismiss = { showFavorites = false }
        )
    }

    var selectedTab by rememberSaveable { mutableStateOf(AppTab.SEARCH) }
    var showExitDialog by remember { mutableStateOf(false) }
    var jumpToTool by remember { mutableStateOf(0) }
    var jumpToToolVersion by remember { mutableStateOf(0) }

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

    val pageHorizontalPadding = if (compactMode) 12.dp else 16.dp
    val pageTopPadding = if (compactMode) 8.dp else 20.dp
    val pageBottomPadding = if (compactMode) 18.dp else 40.dp
    val pageSpacing = if (compactMode) 6.dp else 12.dp
    val suggestionTopPadding = if (compactMode) 124.dp else 148.dp

    CompositionLocalProvider(LocalCompactMode provides compactMode) {
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
                            .pointerInput(Unit) {
                                detectTapGestures {
                                    showSuggestions = false
                                    focusManager.clearFocus(force = true)
                                }
                            },
                        contentPadding = PaddingValues(start = pageHorizontalPadding, end = pageHorizontalPadding, top = pageTopPadding, bottom = pageBottomPadding),
                        verticalArrangement = Arrangement.spacedBy(pageSpacing)
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
                                onClear = {
                                    vm.clearSearchResult()
                                    showSuggestions = false
                                }
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
                                    .padding(
                                        horizontal = if (compactMode) 2.dp else 4.dp,
                                        vertical = if (compactMode) 1.dp else 2.dp
                                    ),
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
                                    Modifier.fillMaxWidth().padding(vertical = if (compactMode) 20.dp else 40.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(if (compactMode) 8.dp else 12.dp)
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
                                    onClear = { vm.clearHistory() },
                                    onDelete = { vm.removeHistoryItem(it) }
                                )
                            }
                        }
                        if (state.hasResult) {
                            item { StructureViewer(state, vm) }
                            item {
                                CompoundHeader(
                                    state,
                                    isFavorite,
                                    onToggleFavorite = { vm.toggleFavorite() },
                                    onFormulaClick = if (state.formula.isNotBlank()) {{
                                        vm.onIsomerQueryChange(state.formula)
                                        vm.searchIsomers()
                                        jumpToTool = 6
                                        jumpToToolVersion++
                                        selectedTab = AppTab.TOOLS
                                    }} else null
                                )
                            }
                            item { IdentifiersSection(state, context) }
                            if (state.synonyms.isNotEmpty() || state.isLoadingSynonyms) {
                                item {
                                    SynonymsSection(
                                        synonyms = state.synonyms,
                                        isLoading = state.isLoadingSynonyms
                                    )
                                }
                            }
                            item {
                                DescriptionSection(
                                    state = state,
                                    onPubChem = { vm.setDescSource(DescSource.PUBCHEM) },
                                    onWiki = { vm.setDescSource(DescSource.WIKI) },
                                    onAI = {
                                        if (vm.hasAiKey(state.aiProvider)) vm.setDescSource(DescSource.AI)
                                        else showAiProviderDialog = true
                                    },
                                    onRegenerate = { vm.fetchAiDescription() }
                                )
                            }
                            if (state.elementalData.isNotEmpty()) item { ElementalSection(state.elementalData) }
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
                        contentPadding = PaddingValues(start = pageHorizontalPadding, end = pageHorizontalPadding, top = pageTopPadding, bottom = pageBottomPadding),
                        verticalArrangement = Arrangement.spacedBy(pageSpacing)
                    ) {
                        item {
                            when (tab) {
                                AppTab.RECENT -> HistorySection(
                                    history = state.history,
                                    onSelect = { vm.search(it); selectedTab = AppTab.SEARCH },
                                    onClear = { vm.clearHistory() },
                                    onDelete = { vm.removeHistoryItem(it) }
                                )
                                AppTab.FAVORITES -> FavoritesInline(
                                    favorites = favorites,
                                    onSelect = { name -> vm.search(name); selectedTab = AppTab.SEARCH },
                                    onDelete = { cid -> vm.deleteFavorite(cid) },
                                    onMoveFavorite = { from, to -> vm.moveFavorite(from, to) }
                                )
                                AppTab.SETTINGS -> SettingsInline(
                                    isDark = isDark,
                                    colorScheme = colorScheme,
                                    autoSuggest = autoSuggest,
                                    compactMode = compactMode,
                                    defaultDescSource = defaultDescSource,
                                    aiProvider = state.aiProvider,
                                    aiKeyStatus = aiKeyStatus,
                                    aiModelCatalogs = aiModelCatalogs,
                                    updateNotificationsEnabled = updateNotificationsEnabled,
                                    updateStatus = updateStatus,
                                    onToggleTheme = { vm.toggleTheme() },
                                    onSetColorScheme = { vm.setColorScheme(it) },
                                    onToggleAutoSuggest = { vm.toggleAutoSuggest() },
                                    onToggleCompactMode = { vm.setCompactMode(!compactMode) },
                                    onSetDefaultDesc = { vm.setDefaultDescSource(it) },
                                    onSetAiProvider = { vm.setAiProvider(it) },
                                    onSetAiModel = { provider, model -> vm.setAiModel(provider, model) },
                                    onRefreshAiModels = { provider -> vm.refreshAiModels(provider) },
                                    onEditAiKey = { provider -> editingAiKeyProvider = provider },
                                    onClearAiKey = { provider -> vm.clearAiKey(provider) },
                                    onClearHistory = { vm.clearHistory() },
                                    onToggleUpdateNotifications = { enabled -> vm.setUpdateNotificationsEnabled(enabled) },
                                    onCheckForUpdates = { vm.checkForUpdates(manual = true) },
                                    cacheSizeBytes = cacheSizeBytes,
                                    cacheDir = cacheDirPath,
                                    onClearCache = { vm.clearCache() },
                                    onSetCacheDir = { vm.setCacheDir(it) },
                                    onTestUpdateNotification = { vm.sendDebugUpdateNotification() },
                                    onSettingsImported = { vm.reloadSettingsFromPreferences() }
                                )
                                AppTab.TOOLS -> ToolsScreen(
                                    isDark = isDark,
                                    jumpToTool = jumpToTool,
                                    jumpToToolVersion = jumpToToolVersion,
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
                    .padding(top = suggestionTopPadding, start = pageHorizontalPadding, end = pageHorizontalPadding)
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
}

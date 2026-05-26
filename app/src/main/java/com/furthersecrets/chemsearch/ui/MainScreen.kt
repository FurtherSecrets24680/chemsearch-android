package com.furthersecrets.chemsearch.ui

import android.app.Activity
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.furthersecrets.chemsearch.ChemViewModel
import androidx.activity.compose.BackHandler
import com.furthersecrets.chemsearch.data.AiProvider
import com.furthersecrets.chemsearch.data.AppColorScheme
import com.furthersecrets.chemsearch.data.DescSource

enum class AppTab(val route: String) {
    SEARCH("search"),
    LIBRARY("library"),
    RECENT("recent"),
    TOOLS("tools"),
    SETTINGS("settings");

    companion object {
        fun fromRoute(route: String?): AppTab =
            entries.firstOrNull { it.route == route } ?: SEARCH
    }
}

private val mainTabOrder = AppTab.entries.map { it.route }

internal data class MainNavigationItem(
    val tab: AppTab,
    val selectedIcon: ChemIconSpec,
    val unselectedIcon: ChemIconSpec,
    val label: String
)

internal val mainNavigationItems = listOf(
    MainNavigationItem(AppTab.SEARCH, ChemAppIcons.SearchFilled, ChemAppIcons.Search, "Search"),
    MainNavigationItem(AppTab.LIBRARY, ChemAppIcons.LibraryFilled, ChemAppIcons.Library, "Library"),
    MainNavigationItem(AppTab.RECENT, ChemAppIcons.HistoryFilled, ChemAppIcons.History, "Recent"),
    MainNavigationItem(AppTab.TOOLS, ChemAppIcons.WrenchFilled, ChemAppIcons.Wrench, "Tools"),
    MainNavigationItem(AppTab.SETTINGS, ChemAppIcons.SettingsFilled, ChemAppIcons.Settings, "Settings")
)

private fun routeIndex(route: String?): Int =
    mainTabOrder.indexOf(route).takeIf { it >= 0 } ?: 0

private fun AnimatedContentTransitionScope<NavBackStackEntry>.mainTabEnterTransition(): EnterTransition {
    val forward = routeIndex(targetState.destination.route) >= routeIndex(initialState.destination.route)
    val direction = if (forward) {
        AnimatedContentTransitionScope.SlideDirection.Left
    } else {
        AnimatedContentTransitionScope.SlideDirection.Right
    }
    return slideIntoContainer(
        towards = direction,
        animationSpec = tween(ChemMotionMedium, easing = ChemMotionEasing)
    ) + fadeIn(tween(ChemMotionFast))
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.mainTabExitTransition(): ExitTransition {
    val forward = routeIndex(targetState.destination.route) >= routeIndex(initialState.destination.route)
    val direction = if (forward) {
        AnimatedContentTransitionScope.SlideDirection.Left
    } else {
        AnimatedContentTransitionScope.SlideDirection.Right
    }
    return slideOutOfContainer(
        towards = direction,
        animationSpec = tween(ChemMotionMedium, easing = ChemMotionEasing)
    ) + fadeOut(tween(ChemMotionFast))
}

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
    val oledDarkTheme by vm.oledDarkTheme.collectAsStateWithLifecycle()
    val defaultDescSource by vm.defaultDescSource.collectAsStateWithLifecycle()
    val aiKeyStatus by vm.aiKeyStatus.collectAsStateWithLifecycle()
    val aiModelCatalogs by vm.aiModelCatalogs.collectAsStateWithLifecycle()
    val cacheSizeBytes by vm.cacheSizeBytes.collectAsStateWithLifecycle()
    val cacheDirPath by vm.cacheDirPath.collectAsStateWithLifecycle()
    val updateNotificationsEnabled by vm.updateNotificationsEnabled.collectAsStateWithLifecycle()
    val updateStatus by vm.updateStatus.collectAsStateWithLifecycle()
    val showWelcome by vm.showWelcome.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val snackbar = remember { SnackbarHostState() }
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val recentSearches by vm.recentSearches.collectAsStateWithLifecycle()
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    val isFavorite by vm.isFavorite.collectAsStateWithLifecycle()
    val isDownloaded by vm.isDownloaded.collectAsStateWithLifecycle()
    val isSavingOffline by vm.isSavingOffline.collectAsStateWithLifecycle()
    val offlineDownloadProgress by vm.offlineDownloadProgress.collectAsStateWithLifecycle()

    var editingAiKeyProvider by remember { mutableStateOf<AiProvider?>(null) }
    var showAiProviderDialog by remember { mutableStateOf(false) }
    var showSuggestions by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showFavorites by remember { mutableStateOf(false) }

    if (showWelcome) {
        CompositionLocalProvider(LocalCompactMode provides compactMode) {
            WelcomeScreen(
                isDark = isDark,
                colorScheme = colorScheme,
                defaultDescSource = defaultDescSource,
                onSetDarkTheme = { dark -> if (isDark != dark) vm.toggleTheme() },
                onSetColorScheme = { vm.setColorScheme(it) },
                onSetDefaultDesc = { vm.setDefaultDescSource(it) },
                onConfigureAiProvider = { showAiProviderDialog = true },
                onContinue = { vm.skipWelcome() }
            )
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
            aiModelCatalogs = aiModelCatalogs,
            onSelect = { provider ->
                vm.setAiProvider(provider)
            },
            onUseProvider = { provider ->
                vm.setAiProvider(provider)
                showAiProviderDialog = false
                if (vm.hasAiKey(provider)) {
                    vm.setDescSource(DescSource.AI)
                } else {
                    editingAiKeyProvider = provider
                }
            },
            onDismiss = { showAiProviderDialog = false }
        )
    }

    if (showWelcome) {
        return
    }

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

    if (showSettings) {
        SettingsSheet(
            isDark = isDark,
            colorScheme = colorScheme,
            autoSuggest = autoSuggest,
            compactMode = compactMode,
            oledDarkTheme = oledDarkTheme,
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
            onToggleOledDarkTheme = { vm.setOledDarkTheme(!oledDarkTheme) },
            onSetDefaultDesc = { vm.setDefaultDescSource(it) },
            onSetAiProvider = { vm.setAiProvider(it) },
            onSetAiModel = { provider, model -> vm.setAiModel(provider, model) },
            onRefreshAiModels = { provider -> vm.refreshAiModels(provider) },
            onEditAiKey = { provider -> editingAiKeyProvider = provider; showSettings = false },
            onClearAiKey = { provider -> vm.clearAiKey(provider) },
            onClearHistory = { vm.clearHistory() },
            onToggleUpdateNotifications = { enabled -> vm.setUpdateNotificationsEnabled(enabled) },
            onCheckForUpdates = { vm.checkForUpdates(manual = true) },
            onDownloadUpdate = { vm.downloadUpdateApk() },
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

    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val selectedTab = AppTab.fromRoute(currentBackStackEntry?.destination?.route)
    fun navigateToTab(tab: AppTab) {
        navController.navigate(tab.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }
    var showExitDialog by remember { mutableStateOf(false) }
    var jumpToTool by remember { mutableStateOf(0) }
    var jumpToToolVersion by remember { mutableStateOf(0) }

    BackHandler {
        if (selectedTab != AppTab.SEARCH) {
            navigateToTab(AppTab.SEARCH)
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
                mainNavigationItems.forEach { item ->
                    val isSelected = selectedTab == item.tab
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { navigateToTab(item.tab) },
                        icon = {
                            AnimatedStateIcon(
                                selected = isSelected,
                                selectedIcon = item.selectedIcon,
                                unselectedIcon = item.unselectedIcon,
                                selectedDescription = item.label,
                                unselectedDescription = item.label,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface.copy(0.5f),
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        label = {
                            Text(
                                item.label,
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
            NavHost(
                navController = navController,
                startDestination = AppTab.SEARCH.route,
                modifier = Modifier.fillMaxSize(),
                enterTransition = { mainTabEnterTransition() },
                exitTransition = { mainTabExitTransition() },
                popEnterTransition = { mainTabEnterTransition() },
                popExitTransition = { mainTabExitTransition() }
            ) {
                composable(AppTab.SEARCH.route) {
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
                                        navigateToTab(AppTab.TOOLS)
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
                                    recentSearches = recentSearches,
                                    onSelect = { vm.search(it) },
                                    onClear = { vm.clearHistory() },
                                    onDelete = { vm.removeHistoryItem(it) },
                                    onTogglePin = { vm.toggleRecentPin(it) }
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
                                    isDownloaded = isDownloaded,
                                    isSavingOffline = isSavingOffline,
                                    offlineDownloadProgress = offlineDownloadProgress,
                                    onDownloadOffline = { vm.saveCurrentCompoundOffline() },
                                    onFormulaClick = if (state.formula.isNotBlank()) {{
                                        vm.onIsomerQueryChange(state.formula)
                                        vm.searchIsomers()
                                        jumpToTool = 6
                                        jumpToToolVersion++
                                        navigateToTab(AppTab.TOOLS)
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
                                    onAI = { vm.setDescSource(DescSource.AI) },
                                    onConfigureAI = { showAiProviderDialog = true },
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
                }
                composable(AppTab.TOOLS.route) {
                    ToolsScreen(
                        isDark = isDark,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = pageHorizontalPadding,
                            end = pageHorizontalPadding,
                            top = pageTopPadding,
                            bottom = pageBottomPadding
                        ),
                        jumpToTool = jumpToTool,
                        jumpToToolVersion = jumpToToolVersion,
                        vm = vm,
                        defaultDescSource = defaultDescSource,
                        aiProvider = state.aiProvider,
                        getAiKey = { provider -> vm.getAiKey(provider) },
                        getSelectedAiModel = { provider -> vm.getSelectedAiModel(provider) },
                        onNavigateToSearch = { navigateToTab(AppTab.SEARCH) },
                        onSearchCompoundFromTool = { compoundQuery ->
                            vm.search(compoundQuery)
                            navigateToTab(AppTab.SEARCH)
                        }
                    )
                }
                composable(AppTab.LIBRARY.route) {
                    LibraryInline(
                        favorites = favorites,
                        downloads = downloads,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = pageHorizontalPadding,
                            end = pageHorizontalPadding,
                            top = pageTopPadding,
                            bottom = pageBottomPadding
                        ),
                        onSelectFavorite = { name -> vm.search(name); navigateToTab(AppTab.SEARCH) },
                        onSelectDownload = { cid -> vm.openDownloadedCompound(cid); navigateToTab(AppTab.SEARCH) },
                        onDeleteFavorite = { cid -> vm.deleteFavorite(cid) },
                        onDeleteDownload = { cid -> vm.deleteDownload(cid) },
                        onMoveFavorite = { from, to -> vm.moveFavorite(from, to) },
                        onSearchCompoundFromDatabase = { compoundQuery ->
                            vm.search(compoundQuery)
                            navigateToTab(AppTab.SEARCH)
                        }
                    )
                }
                composable(AppTab.RECENT.route) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = pageHorizontalPadding, end = pageHorizontalPadding, top = pageTopPadding, bottom = pageBottomPadding),
                        verticalArrangement = Arrangement.spacedBy(pageSpacing)
                    ) {
                        item {
                            HistorySection(
                                recentSearches = recentSearches,
                                onSelect = { vm.search(it); navigateToTab(AppTab.SEARCH) },
                                onClear = { vm.clearHistory() },
                                onDelete = { vm.removeHistoryItem(it) },
                                onTogglePin = { vm.toggleRecentPin(it) }
                            )
                        }
                    }
                }
                composable(AppTab.SETTINGS.route) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = pageHorizontalPadding, end = pageHorizontalPadding, top = pageTopPadding, bottom = pageBottomPadding),
                        verticalArrangement = Arrangement.spacedBy(pageSpacing)
                    ) {
                        item {
                            SettingsInline(
                                isDark = isDark,
                                colorScheme = colorScheme,
                                autoSuggest = autoSuggest,
                                compactMode = compactMode,
                                oledDarkTheme = oledDarkTheme,
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
                                onToggleOledDarkTheme = { vm.setOledDarkTheme(!oledDarkTheme) },
                                onSetDefaultDesc = { vm.setDefaultDescSource(it) },
                                onSetAiProvider = { vm.setAiProvider(it) },
                                onSetAiModel = { provider, model -> vm.setAiModel(provider, model) },
                                onRefreshAiModels = { provider -> vm.refreshAiModels(provider) },
                                onEditAiKey = { provider -> editingAiKeyProvider = provider },
                                onClearAiKey = { provider -> vm.clearAiKey(provider) },
                                onClearHistory = { vm.clearHistory() },
                                onToggleUpdateNotifications = { enabled -> vm.setUpdateNotificationsEnabled(enabled) },
                                onCheckForUpdates = { vm.checkForUpdates(manual = true) },
                                onDownloadUpdate = { vm.downloadUpdateApk() },
                                cacheSizeBytes = cacheSizeBytes,
                                cacheDir = cacheDirPath,
                                onClearCache = { vm.clearCache() },
                                onSetCacheDir = { vm.setCacheDir(it) },
                                onTestUpdateNotification = { vm.sendDebugUpdateNotification() },
                                onShowWelcome = { vm.showWelcomeAgain() },
                                onSettingsImported = { vm.reloadSettingsFromPreferences() }
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = showSuggestions && state.suggestions.isNotEmpty(),
                modifier = Modifier
                    .padding(top = suggestionTopPadding, start = pageHorizontalPadding, end = pageHorizontalPadding)
                    .zIndex(10f),
                enter = slideInVertically(
                    animationSpec = tween(ChemMotionMedium, easing = ChemMotionEasing),
                    initialOffsetY = { -it / 6 }
                ) + fadeIn(tween(ChemMotionFast)),
                exit = slideOutVertically(
                    animationSpec = tween(ChemMotionFast, easing = ChemMotionEasing),
                    targetOffsetY = { -it / 8 }
                ) + fadeOut(tween(ChemMotionFast))
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

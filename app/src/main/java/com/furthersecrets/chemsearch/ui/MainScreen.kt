package com.furthersecrets.chemsearch.ui

import android.app.Activity
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.furthersecrets.chemsearch.data.ChemUiState
import com.furthersecrets.chemsearch.data.DescSource
import kotlinx.coroutines.launch

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
private const val StructureSearchRoute = "structure_search"
private const val AboutRoute = "about"
internal const val IsomerSearchRoute = "isomer_search"

internal fun isStandalonePageRoute(route: String?): Boolean =
    route == StructureSearchRoute || route == AboutRoute || route == IsomerSearchRoute

internal fun compoundExtraInfoToggleLabel(expanded: Boolean): String =
    if (expanded) "Hide extra compound information" else "Show more information about this substance"

internal fun compoundExtraInfoSectionOrder(showExtraInfo: Boolean, hasExtraInfo: Boolean): List<String> = buildList {
    add("GHS Safety")
    if (hasExtraInfo) {
        add("More information toggle")
        if (showExtraInfo) {
            add("Uses & Occurrence")
            add("Advanced Properties")
            add("Classification")
        }
    }
}

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

internal fun shouldShowRandomCompoundButton(state: ChemUiState, query: String): Boolean =
    !state.hasResult && !state.isLoading && query.isBlank()

internal const val homeStarterSuggestionsLabel = "Try searching"

internal const val homeStructureSearchActionTitle = "Structure Search"
internal const val homeStructureSearchActionDescription = "Draw a molecule and search PubChem"
internal const val homeIsomerSearchActionTitle = "Isomer Search"
internal const val homeIsomerSearchActionDescription = "Find compounds with the same formula"

internal const val homeQuickActionTitleMaxLines = 2
internal const val homeQuickActionDescriptionMaxLines = 2

internal data class HomeQuickActionLayoutMetrics(
    val iconBoxSizeDp: Int,
    val horizontalPaddingDp: Int,
    val verticalPaddingDp: Int,
    val textGapDp: Int,
    val cardMinHeightDp: Int,
    val cornerRadiusDp: Int,
    val usesFixedTextSlots: Boolean,
    val usesHorizontalScroll: Boolean
)

internal fun homeQuickActionLayoutMetrics(compact: Boolean): HomeQuickActionLayoutMetrics =
    if (compact) {
        HomeQuickActionLayoutMetrics(
            iconBoxSizeDp = 32,
            horizontalPaddingDp = 8,
            verticalPaddingDp = 10,
            textGapDp = 7,
            cardMinHeightDp = 84,
            cornerRadiusDp = 16,
            usesFixedTextSlots = false,
            usesHorizontalScroll = false
        )
    } else {
        HomeQuickActionLayoutMetrics(
            iconBoxSizeDp = 32,
            horizontalPaddingDp = 8,
            verticalPaddingDp = 10,
            textGapDp = 7,
            cardMinHeightDp = 86,
            cornerRadiusDp = 16,
            usesFixedTextSlots = false,
            usesHorizontalScroll = false
        )
    }

internal val homeStarterSuggestions = listOf(
    "caffeine",
    "aspirin",
    "NaCl",
    "H2SO4",
    "glucose",
    "ethanol",
    "ammonium phosphate",
    "calcium carbonate"
)

internal data class SearchLoadingBubbleFrame(
    val liftFraction: Float,
    val alpha: Float
)

internal data class SearchLoadingBubbleSpec(
    val xDp: Float,
    val baseYDp: Float,
    val sizeDp: Float
)

internal data class SearchLoadingAnimationLayout(
    val widthDp: Float,
    val heightDp: Float,
    val topPaddingDp: Float,
    val iconSizeDp: Float,
    val backgroundSizeDp: Float,
    val bubbleLiftDp: Float,
    val bubbleSpecs: List<SearchLoadingBubbleSpec>
) {
    val minimumBubbleTopAtFullLiftDp: Float
        get() = bubbleSpecs.minOf { it.baseYDp - bubbleLiftDp }
}

internal fun searchLoadingAnimationLayout(compactMode: Boolean): SearchLoadingAnimationLayout =
    if (compactMode) {
        SearchLoadingAnimationLayout(
            widthDp = 96f,
            heightDp = 80f,
            topPaddingDp = 8f,
            iconSizeDp = 50f,
            backgroundSizeDp = 66f,
            bubbleLiftDp = 16f,
            bubbleSpecs = listOf(
                SearchLoadingBubbleSpec(xDp = -22f, baseYDp = 26f, sizeDp = 9f),
                SearchLoadingBubbleSpec(xDp = 0f, baseYDp = 21f, sizeDp = 8f),
                SearchLoadingBubbleSpec(xDp = 22f, baseYDp = 28f, sizeDp = 10f)
            )
        )
    } else {
        SearchLoadingAnimationLayout(
            widthDp = 112f,
            heightDp = 96f,
            topPaddingDp = 14f,
            iconSizeDp = 64f,
            backgroundSizeDp = 82f,
            bubbleLiftDp = 22f,
            bubbleSpecs = listOf(
                SearchLoadingBubbleSpec(xDp = -26f, baseYDp = 34f, sizeDp = 11f),
                SearchLoadingBubbleSpec(xDp = 0f, baseYDp = 28f, sizeDp = 10f),
                SearchLoadingBubbleSpec(xDp = 26f, baseYDp = 36f, sizeDp = 12f)
            )
        )
    }

internal fun searchLoadingBubbleFrame(
    progress: Float,
    bubbleIndex: Int,
    reduceMotion: Boolean
): SearchLoadingBubbleFrame {
    if (reduceMotion) return SearchLoadingBubbleFrame(liftFraction = 0f, alpha = 0.65f)
    val localProgress = normalizedAnimationProgress(progress - bubbleIndex * 0.22f)
    val lift = if (localProgress < 0.5f) {
        localProgress * 2f
    } else {
        (1f - localProgress) * 2f
    }.coerceIn(0f, 1f)
    return SearchLoadingBubbleFrame(
        liftFraction = lift,
        alpha = (0.35f + lift * 0.65f).coerceIn(0.35f, 1f)
    )
}

private fun normalizedAnimationProgress(progress: Float): Float {
    val remainder = progress % 1f
    return if (remainder < 0f) remainder + 1f else remainder
}

@Composable
internal fun SearchLoadingChemistryAnimation(
    reduceMotion: Boolean,
    compactMode: Boolean,
    modifier: Modifier = Modifier
) {
    val activeProgress = if (reduceMotion) {
        0f
    } else {
        val transition = rememberInfiniteTransition(label = "compound-search-loading")
        val progress by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1350, easing = ChemMotionEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "compound-search-loading-progress"
        )
        progress
    }
    val layout = remember(compactMode) { searchLoadingAnimationLayout(compactMode) }
    val pulse = searchLoadingBubbleFrame(activeProgress, bubbleIndex = 2, reduceMotion = reduceMotion).liftFraction

    Box(
        modifier = modifier
            .padding(top = layout.topPaddingDp.dp)
            .size(width = layout.widthDp.dp, height = layout.heightDp.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.Center)
                .size(layout.backgroundSizeDp.dp)
                .graphicsLayer {
                    alpha = 0.12f + pulse * 0.08f
                    scaleX = 0.96f + pulse * 0.06f
                    scaleY = 0.96f + pulse * 0.06f
                },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
            content = {}
        )
        ChemIcon(
            ChemAppIcons.FlaskConical,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .align(Alignment.Center)
                .size(layout.iconSizeDp.dp)
                .graphicsLayer {
                    scaleX = 1f + pulse * 0.025f
                    scaleY = 1f + pulse * 0.025f
                }
        )
        layout.bubbleSpecs.forEachIndexed { index, spec ->
            val frame = searchLoadingBubbleFrame(activeProgress, index, reduceMotion)
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(x = spec.xDp.dp, y = spec.baseYDp.dp - layout.bubbleLiftDp.dp * frame.liftFraction)
                    .size(spec.sizeDp.dp)
                    .graphicsLayer {
                        alpha = frame.alpha
                        scaleX = 0.72f + frame.liftFraction * 0.35f
                        scaleY = 0.72f + frame.liftFraction * 0.35f
                    },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                content = {}
            )
        }
    }
}

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

@Composable
private fun HomeStarterSuggestions(
    suggestions: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val compact = LocalCompactMode.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            homeStarterSuggestionsLabel,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface.copy(0.5f),
            maxLines = 1
        )
        suggestions.forEach { suggestion ->
            Surface(
                onClick = { onSelect(suggestion) },
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.primary.copy(0.1f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(0.18f))
            ) {
                Text(
                    suggestion,
                    modifier = Modifier.padding(
                        horizontal = if (compact) 10.dp else 12.dp,
                        vertical = if (compact) 6.dp else 7.dp
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary.copy(0.86f),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun HomeSearchQuickActions(
    onStructureSearch: () -> Unit,
    onIsomerSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val compact = LocalCompactMode.current
    val metrics = homeQuickActionLayoutMetrics(compact)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HomeSearchQuickActionButton(
            title = homeStructureSearchActionTitle,
            description = homeStructureSearchActionDescription,
            onClick = onStructureSearch,
            modifier = Modifier.weight(1f)
        ) {
            StructureSearchIcon(
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(if (metrics.iconBoxSizeDp <= 32) 18.dp else 20.dp)
            )
        }
        HomeSearchQuickActionButton(
            title = homeIsomerSearchActionTitle,
            description = homeIsomerSearchActionDescription,
            onClick = onIsomerSearch,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                Icons.Default.Atom,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(if (metrics.iconBoxSizeDp <= 32) 18.dp else 20.dp)
            )
        }
    }
}

@Composable
private fun HomeSearchQuickActionButton(
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit
) {
    val compact = LocalCompactMode.current
    val metrics = homeQuickActionLayoutMetrics(compact)
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = metrics.cardMinHeightDp.dp),
        shape = RoundedCornerShape(metrics.cornerRadiusDp.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(0.34f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(0.18f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = metrics.horizontalPaddingDp.dp,
                    vertical = metrics.verticalPaddingDp.dp
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(metrics.textGapDp.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(metrics.iconBoxSizeDp.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(0.1f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = homeQuickActionTitleMaxLines,
                    overflow = TextOverflow.Clip,
                    softWrap = true
                )
                Text(
                    description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.52f),
                    maxLines = homeQuickActionDescriptionMaxLines,
                    overflow = TextOverflow.Clip,
                    softWrap = true
                )
            }
        }
    }
}

// Root

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: ChemViewModel = viewModel()) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val structureSearchState by vm.structureSearchState.collectAsStateWithLifecycle()
    val advancedSearchState by vm.advancedSearchState.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val isDark by vm.isDarkTheme.collectAsStateWithLifecycle()
    val colorScheme by vm.colorScheme.collectAsStateWithLifecycle()
    val autoSuggest by vm.autoSuggest.collectAsStateWithLifecycle()
    val compactMode by vm.compactMode.collectAsStateWithLifecycle()
    val oledDarkTheme by vm.oledDarkTheme.collectAsStateWithLifecycle()
    val defaultDescSource by vm.defaultDescSource.collectAsStateWithLifecycle()
    val defaultStructureView by vm.defaultStructureView.collectAsStateWithLifecycle()
    val offlineDownloadQuality by vm.offlineDownloadQuality.collectAsStateWithLifecycle()
    val formulaDisplayStyle by vm.formulaDisplayStyle.collectAsStateWithLifecycle()
    val cacheSizeLimit by vm.cacheSizeLimit.collectAsStateWithLifecycle()
    val cacheRetention by vm.cacheRetention.collectAsStateWithLifecycle()
    val reduceMotion by vm.reduceMotion.collectAsStateWithLifecycle()
    val highContrastOutlines by vm.highContrastOutlines.collectAsStateWithLifecycle()
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
    val snackbarScope = rememberCoroutineScope()
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
    var showAdvancedSearch by remember { mutableStateOf(false) }

    fun showUndoSnackbar(message: String, onUndo: () -> Unit) {
        snackbarScope.launch {
            val result = snackbar.showSnackbar(
                message = message,
                actionLabel = "Undo",
                withDismissAction = true
            )
            if (result == SnackbarResult.ActionPerformed) onUndo()
        }
    }

    fun removeRecentWithUndo(query: String) {
        val removed = recentSearches.firstOrNull { it.query.equals(query, ignoreCase = true) }
        vm.removeHistoryItem(query)
        removed?.let { item ->
            showUndoSnackbar("Removed ${item.query}") {
                vm.restoreRecentSearch(item)
            }
        }
    }

    fun clearHistoryWithUndo() {
        val snapshot = recentSearches
        vm.clearHistory()
        if (snapshot.isNotEmpty()) {
            showUndoSnackbar("Cleared ${snapshot.size} recent search${if (snapshot.size == 1) "" else "es"}") {
                vm.restoreRecentSearches(snapshot)
            }
        }
    }

    fun deleteFavoriteWithUndo(cid: Long) {
        val removed = favorites.firstOrNull { it.cid == cid }
        vm.deleteFavorite(cid)
        removed?.let { favorite ->
            showUndoSnackbar("Removed ${favorite.name}") {
                vm.restoreFavorite(favorite)
            }
        }
    }

    fun deleteDownloadWithUndo(cid: Long) {
        val removed = downloads.firstOrNull { it.cid == cid }
        vm.deleteDownload(cid)
        removed?.let { download ->
            showUndoSnackbar("Deleted ${download.name}") {
                vm.restoreDownload(download)
            }
        }
    }

    if (showWelcome) {
        CompositionLocalProvider(
            LocalCompactMode provides compactMode,
            LocalReduceMotion provides reduceMotion
        ) {
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

    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    val selectedTab = AppTab.fromRoute(currentRoute)
    val showBottomNavigation = currentRoute == null || currentRoute in mainTabOrder
    fun navigateToTab(tab: AppTab) {
        navController.navigate(tab.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
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
            defaultStructureView = defaultStructureView,
            formulaDisplayStyle = formulaDisplayStyle,
            reduceMotion = reduceMotion,
            highContrastOutlines = highContrastOutlines,
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
            onSetDefaultStructureView = { vm.setDefaultStructureView(it) },
            onSetFormulaDisplayStyle = { vm.setFormulaDisplayStyle(it) },
            onToggleReduceMotion = { vm.setReduceMotion(!reduceMotion) },
            onToggleHighContrastOutlines = { vm.setHighContrastOutlines(!highContrastOutlines) },
            onSetAiProvider = { vm.setAiProvider(it) },
            onSetAiModel = { provider, model -> vm.setAiModel(provider, model) },
            onRefreshAiModels = { provider -> vm.refreshAiModels(provider) },
            onEditAiKey = { provider -> editingAiKeyProvider = provider; showSettings = false },
            onClearAiKey = { provider -> vm.clearAiKey(provider) },
            onClearHistory = { clearHistoryWithUndo() },
            onToggleUpdateNotifications = { enabled -> vm.setUpdateNotificationsEnabled(enabled) },
            onCheckForUpdates = { vm.checkForUpdates(manual = true) },
            onDownloadUpdate = { vm.downloadUpdateApk() },
            onOpenAbout = {
                showSettings = false
                navController.navigate(AboutRoute)
            },
            onDismiss = { showSettings = false }
        )
    }

    if (showFavorites) {
        FavoritesSheet(
            favorites = favorites,
            onSelect = { name -> vm.search(name); showFavorites = false },
            onDelete = { cid -> deleteFavoriteWithUndo(cid) },
            onMoveFavorite = { from, to -> vm.moveFavorite(from, to) },
            onDismiss = { showFavorites = false }
        )
    }

    if (showAdvancedSearch) {
        AdvancedSearchDialog(
            state = advancedSearchState,
            initialQuery = query,
            onUpdateFilters = { vm.updateAdvancedSearchFilters(it) },
            onSearch = { vm.searchAdvanced(it) },
            onOpenResult = { cid ->
                showAdvancedSearch = false
                vm.searchByCid(cid)
            },
            onDismiss = {
                showAdvancedSearch = false
                vm.clearAdvancedSearchResults()
            }
        )
    }

    var showExitDialog by remember { mutableStateOf(false) }
    var jumpToTool by remember { mutableStateOf(0) }
    var jumpToToolVersion by remember { mutableStateOf(0) }
    var comparePrefillCompounds by remember { mutableStateOf<List<String>>(emptyList()) }
    var comparePrefillVersion by remember { mutableStateOf(0) }

    fun openCompareTool(prefillCompounds: List<String> = emptyList()) {
        comparePrefillCompounds = prefillCompounds
        comparePrefillVersion++
        jumpToTool = 13
        jumpToToolVersion++
        navigateToTab(AppTab.TOOLS)
    }

    BackHandler {
        if (isStandalonePageRoute(currentRoute)) {
            navController.popBackStack()
        } else if (selectedTab != AppTab.SEARCH) {
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
    val pageBottomPadding = if (compactMode) 4.dp else 8.dp
    val pageSpacing = if (compactMode) 6.dp else 12.dp
    val suggestionTopPadding = if (compactMode) 124.dp else 148.dp

    CompositionLocalProvider(
        LocalCompactMode provides compactMode,
        LocalReduceMotion provides reduceMotion
    ) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomNavigation) {
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
                enterTransition = { if (reduceMotion) fadeIn(tween(80)) else mainTabEnterTransition() },
                exitTransition = { if (reduceMotion) fadeOut(tween(80)) else mainTabExitTransition() },
                popEnterTransition = { if (reduceMotion) fadeIn(tween(80)) else mainTabEnterTransition() },
                popExitTransition = { if (reduceMotion) fadeOut(tween(80)) else mainTabExitTransition() }
            ) {
                composable(AppTab.SEARCH.route) {
                    val hasExtraCompoundInfo = state.advancedProperties.isNotEmpty() ||
                        state.classificationTags.isNotEmpty() ||
                        state.useEntries.isNotEmpty() ||
                        state.isLoadingPubChemContext
                    var showExtraCompoundInfo by rememberSaveable(state.cid, state.hasResult) { mutableStateOf(false) }
                    Box(modifier = Modifier.fillMaxSize()) {
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
                                    },
                                    onAdvancedSearch = {
                                        showSuggestions = false
                                        focusManager.clearFocus()
                                        showAdvancedSearch = true
                                    }
                                )
                            }
                            if (!state.hasResult && !state.isLoading && state.searchCorrectionSuggestions.isNotEmpty()) {
                                item {
                                    SearchCorrectionCard(
                                        failedQuery = state.failedSearchQuery,
                                        suggestions = state.searchCorrectionSuggestions,
                                        onSelect = {
                                            showSuggestions = false
                                            focusManager.clearFocus()
                                            vm.search(it)
                                        }
                                    )
                                }
                            }

                            if (!state.hasResult && !state.isLoading && query.isBlank()) {
                                item {
                                    HomeStarterSuggestions(
                                        suggestions = homeStarterSuggestions,
                                        onSelect = { vm.search(it) }
                                    )
                                }
                                item {
                                    HomeSearchQuickActions(
                                        onStructureSearch = {
                                            showSuggestions = false
                                            focusManager.clearFocus()
                                            navController.navigate(StructureSearchRoute)
                                        },
                                        onIsomerSearch = {
                                            showSuggestions = false
                                            focusManager.clearFocus()
                                            navController.navigate(IsomerSearchRoute)
                                        }
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
                                            Text(
                                                "Looking up compound...",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface.copy(0.4f)
                                            )
                                            SearchLoadingChemistryAnimation(
                                                reduceMotion = reduceMotion,
                                                compactMode = compactMode
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
                                        onClear = { clearHistoryWithUndo() },
                                        onDelete = { removeRecentWithUndo(it) },
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
                                            navController.navigate(IsomerSearchRoute)
                                        }} else null,
                                        formulaDisplayStyle = formulaDisplayStyle
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
                                if (hasExtraCompoundInfo) {
                                    item {
                                        TextButton(
                                            onClick = { showExtraCompoundInfo = !showExtraCompoundInfo },
                                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                compoundExtraInfoToggleLabel(showExtraCompoundInfo),
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Icon(
                                                if (showExtraCompoundInfo) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                                if (showExtraCompoundInfo) {
                                    if (state.useEntries.isNotEmpty() || state.isLoadingPubChemContext) {
                                        item {
                                            UsesAndOccurrenceSection(
                                                entries = state.useEntries,
                                                isLoading = state.isLoadingPubChemContext
                                            )
                                        }
                                    }
                                    if (state.advancedProperties.isNotEmpty() || state.isLoadingPubChemContext) {
                                        item {
                                            AdvancedPropertiesSection(
                                                properties = state.advancedProperties,
                                                isLoading = state.isLoadingPubChemContext
                                            )
                                        }
                                    }
                                    if (state.classificationTags.isNotEmpty() || state.isLoadingPubChemContext) {
                                        item {
                                            ClassificationTagsSection(
                                                tags = state.classificationTags,
                                                isLoading = state.isLoadingPubChemContext
                                            )
                                        }
                                    }
                                }
                                item { PubChemCredits() }
                            }
                        }

                        if (shouldShowRandomCompoundButton(state, query)) {
                            FloatingActionButton(
                                onClick = {
                                    showSuggestions = false
                                    focusManager.clearFocus()
                                    vm.searchRandomCompound()
                                },
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(
                                        end = pageHorizontalPadding,
                                        bottom = if (compactMode) 12.dp else 18.dp
                                    ),
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ) {
                                ChemIcon(
                                    icon = ChemAppIcons.Dice,
                                    contentDescription = "Random compound",
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
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
                        prefillCompareCompounds = comparePrefillCompounds,
                        prefillCompareVersion = comparePrefillVersion,
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
                        onDeleteFavorite = { cid -> deleteFavoriteWithUndo(cid) },
                        onDeleteDownload = { cid -> deleteDownloadWithUndo(cid) },
                        onMoveFavorite = { from, to -> vm.moveFavorite(from, to) },
                        onSearchCompoundFromDatabase = { compoundQuery ->
                            vm.search(compoundQuery)
                            navigateToTab(AppTab.SEARCH)
                        },
                        onCompareSelected = { compounds ->
                            openCompareTool(compounds)
                        },
                        onBuildLibraryBackupJson = { vm.buildLibraryBackupJson() },
                        onImportLibraryBackup = { rawJson, replace, onResult ->
                            vm.importLibraryBackup(rawJson, replace, onResult)
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
                                onClear = { clearHistoryWithUndo() },
                                onDelete = { removeRecentWithUndo(it) },
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
                                defaultStructureView = defaultStructureView,
                                offlineDownloadQuality = offlineDownloadQuality,
                                formulaDisplayStyle = formulaDisplayStyle,
                                cacheSizeLimit = cacheSizeLimit,
                                cacheRetention = cacheRetention,
                                reduceMotion = reduceMotion,
                                highContrastOutlines = highContrastOutlines,
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
                                onSetDefaultStructureView = { vm.setDefaultStructureView(it) },
                                onSetOfflineDownloadQuality = { vm.setOfflineDownloadQuality(it) },
                                onSetFormulaDisplayStyle = { vm.setFormulaDisplayStyle(it) },
                                onSetCacheSizeLimit = { vm.setCacheSizeLimit(it) },
                                onSetCacheRetention = { vm.setCacheRetention(it) },
                                onToggleReduceMotion = { vm.setReduceMotion(!reduceMotion) },
                                onToggleHighContrastOutlines = { vm.setHighContrastOutlines(!highContrastOutlines) },
                                onSetAiProvider = { vm.setAiProvider(it) },
                                onSetAiModel = { provider, model -> vm.setAiModel(provider, model) },
                                onRefreshAiModels = { provider -> vm.refreshAiModels(provider) },
                                onEditAiKey = { provider -> editingAiKeyProvider = provider },
                                onClearAiKey = { provider -> vm.clearAiKey(provider) },
                                onClearHistory = { clearHistoryWithUndo() },
                                onToggleUpdateNotifications = { enabled -> vm.setUpdateNotificationsEnabled(enabled) },
                                onCheckForUpdates = { vm.checkForUpdates(manual = true) },
                                onDownloadUpdate = { vm.downloadUpdateApk() },
                                cacheSizeBytes = cacheSizeBytes,
                                cacheDir = cacheDirPath,
                                onClearCache = { vm.clearCache() },
                                onSetCacheDir = { vm.setCacheDir(it) },
                                onTestUpdateNotification = { vm.sendDebugUpdateNotification() },
                                onShowWelcome = { vm.showWelcomeAgain() },
                                onOpenAbout = { navController.navigate(AboutRoute) },
                                onSettingsImported = { vm.reloadSettingsFromPreferences() }
                            )
                        }
                    }
                }
                composable(AboutRoute) {
                    AboutScreen(
                        onBack = { navController.popBackStack() },
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = pageHorizontalPadding,
                            end = pageHorizontalPadding,
                            top = pageTopPadding,
                            bottom = pageBottomPadding
                        )
                    )
                }
                composable(IsomerSearchRoute) {
                    IsomerSearchScreen(
                        state = state,
                        onBack = { navController.popBackStack() },
                        onQueryChange = { vm.onIsomerQueryChange(it) },
                        onSearch = { vm.searchIsomers() },
                        onLoadMore = { vm.loadMoreIsomers() },
                        onClear = { vm.onIsomerQueryChange("") },
                        onOpenResult = { cid ->
                            vm.searchByCid(cid)
                            navController.popBackStack(AppTab.SEARCH.route, false)
                        },
                        onCompareSelected = { compounds ->
                            openCompareTool(compounds)
                        },
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = pageHorizontalPadding,
                            end = pageHorizontalPadding,
                            top = pageTopPadding,
                            bottom = pageBottomPadding
                        )
                    )
                }
                composable(StructureSearchRoute) {
                    StructureSearchScreen(
                        state = structureSearchState,
                        onBack = { navController.popBackStack() },
                        onModeChange = { vm.setStructureSearchMode(it) },
                        onThresholdChange = { vm.setStructureSimilarityThreshold(it) },
                        onMaxRecordsChange = { vm.setStructureMaxRecords(it) },
                        onStandardize = { vm.standardizeStructure(it) },
                        onImportText = { vm.importStructureText(it) },
                        onConsumeSketchUpdate = { vm.consumeStructureSketchUpdate() },
                        onSearch = { vm.searchByStructure(it) },
                        onClearResults = { vm.clearStructureSearchResults() },
                        onOpenResult = { cid ->
                            vm.searchByCid(cid)
                            navController.popBackStack(AppTab.SEARCH.route, false)
                        }
                    )
                }
            }

            AnimatedVisibility(
                visible = showSuggestions && state.suggestions.isNotEmpty(),
                modifier = Modifier
                    .padding(top = suggestionTopPadding, start = pageHorizontalPadding, end = pageHorizontalPadding)
                    .zIndex(10f),
                enter = if (reduceMotion) fadeIn(tween(80)) else slideInVertically(
                    animationSpec = tween(ChemMotionMedium, easing = ChemMotionEasing),
                    initialOffsetY = { -it / 6 }
                ) + fadeIn(tween(ChemMotionFast)),
                exit = if (reduceMotion) fadeOut(tween(80)) else slideOutVertically(
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

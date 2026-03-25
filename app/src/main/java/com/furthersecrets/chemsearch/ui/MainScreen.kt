package com.furthersecrets.chemsearch.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.furthersecrets.chemsearch.ChemViewModel
import com.furthersecrets.chemsearch.data.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.furthersecrets.chemsearch.R
import com.furthersecrets.chemsearch.BuildConfig
import androidx.compose.ui.graphics.luminance
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.activity.compose.BackHandler
import android.app.Activity
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardCapitalization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.shape.CircleShape
import java.io.File
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.Feed
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.material.icons.filled.Biotech


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
                                else -> {}
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

// App header

@Composable
fun AppHeader(isDark: Boolean, onToggleTheme: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(
                painter = painterResource(id = R.drawable.chemsearch),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(46.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    "ChemSearch",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.3).sp
                )
                Text(
                    "CHEMISTRY SIMPLIFIED",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.8.sp,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            HeaderIconButton(onClick = onToggleTheme, icon = if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode, description = "Toggle theme")
        }
    }
}

@Composable
private fun HeaderIconButton(onClick: () -> Unit, icon: ImageVector, description: String) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(38.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurface.copy(0.55f),
            modifier = Modifier.size(20.dp)
        )
    }
}

// Settings bottom sheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    isDark: Boolean,
    autoSuggest: Boolean,
    defaultDescSource: DescSource,
    aiProvider: AiProvider,
    hasGeminiKey: Boolean,
    hasGroqKey: Boolean,
    onToggleTheme: () -> Unit,
    onToggleAutoSuggest: () -> Unit,
    onSetDefaultDesc: (DescSource) -> Unit,
    onSetAiProvider: (AiProvider) -> Unit,
    onSetGeminiKey: () -> Unit,
    onSetGroqKey: () -> Unit,
    onClearGeminiKey: () -> Unit,
    onClearGroqKey: () -> Unit,
    onClearHistory: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                "Settings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            SettingsSectionHeader("Appearance")
            SettingsToggleRow(
                icon = if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                title = "Dark Mode",
                subtitle = if (isDark) "Currently dark" else "Currently light",
                checked = isDark,
                onToggle = onToggleTheme
            )

            Spacer(Modifier.height(4.dp))
            SettingsSectionHeader("Search")
            SettingsToggleRow(
                icon = Icons.Default.Search,
                title = "Autosuggestions",
                subtitle = "Show dropdown while typing",
                checked = autoSuggest,
                onToggle = onToggleAutoSuggest
            )

            Spacer(Modifier.height(4.dp))
            SettingsSectionHeader("Default Description Source")
            Text(
                "Automatically shown when you search a compound",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(0.5f),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(DescSource.PUBCHEM to "PubChem", DescSource.WIKI to "Wikipedia", DescSource.AI to "AI").forEach { (src, label) ->
                    SourceBtn(label = label, active = defaultDescSource == src) { onSetDefaultDesc(src) }
                }
            }

            Spacer(Modifier.height(4.dp))
            SettingsSectionHeader("AI Provider")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(AiProvider.GEMINI to "Gemini", AiProvider.GROQ to "Groq").forEach { (prov, label) ->
                    SourceBtn(label = label, active = aiProvider == prov) { onSetAiProvider(prov) }
                }
            }

            Spacer(Modifier.height(4.dp))
            SettingsSectionHeader("API Keys")
            if (hasGeminiKey) {
                SettingsActionRow(Icons.Default.Key, "Gemini API Key saved", "Tap to replace", "Replace", MaterialTheme.colorScheme.primary, onSetGeminiKey)
                SettingsActionRow(Icons.Default.DeleteOutline, "Remove Gemini Key", "Disables Gemini AI", "Remove", MaterialTheme.colorScheme.error, onClearGeminiKey)
            } else {
                SettingsActionRow(Icons.Default.Key, "No Gemini Key set", "Required for Gemini descriptions", "Add Key", MaterialTheme.colorScheme.primary, onSetGeminiKey)
            }

            HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outline.copy(0.1f))

            if (hasGroqKey) {
                SettingsActionRow(Icons.Default.Key, "Groq API Key saved", "Tap to replace", "Replace", MaterialTheme.colorScheme.primary, onSetGroqKey)
                SettingsActionRow(Icons.Default.DeleteOutline, "Remove Groq Key", "Disables Groq AI", "Remove", MaterialTheme.colorScheme.error, onClearGroqKey)
            } else {
                SettingsActionRow(Icons.Default.Key, "No Groq Key set", "Required for Groq descriptions", "Add Key", MaterialTheme.colorScheme.primary, onSetGroqKey)
            }

            Spacer(Modifier.height(4.dp))
            SettingsSectionHeader("Data")
            SettingsActionRow(Icons.Default.History, "Search History", "Clear all recent searches", "Clear", MaterialTheme.colorScheme.error, onClearHistory)

            Spacer(Modifier.height(4.dp))
            SettingsSectionHeader("About")
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("ChemSearch for Android", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary.copy(0.12f)
                        ) {
                            Text(
                                text = "v${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE})",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text("Data from PubChem, Wikipedia, Google Gemini and Groq", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(0.55f))
                        Text("by FurtherSecrets", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.15f))

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("SOURCE CODE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
                        Row(
                            modifier = Modifier.clickable {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/FurtherSecrets24680/chemsearch-android"))
                                context.startActivity(intent)
                            },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Code, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Text("GitHub Repository", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("LIBRARIES & CREDITS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
                        val libs = listOf(
                            "Jetpack Compose" to "UI Framework",
                            "Material 3" to "Design System",
                            "Retrofit & OkHttp" to "Networking",
                            "Coil" to "Image Loading",
                            "Native 3D Engine" to "3D Molecular Visualization",
                            "Gemini & Groq" to "AI Models",
                            "Gson" to "JSON Parsing",
                            "Kotlin Coroutines" to "Asynchronous Tasks"
                        )
                        libs.forEach { (name, desc) ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                            }
                        }
                    }
                }
            }
        }

    }
}

@Composable
fun SettingsSectionHeader(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
    )
}

@Composable
fun SettingsToggleRow(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurface.copy(0.5f), modifier = Modifier.size(20.dp))
            Column {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
            }
        }
        Switch(checked = checked, onCheckedChange = { onToggle() })
    }
}

@Composable
fun SettingsActionRow(icon: ImageVector, title: String, subtitle: String, actionLabel: String, actionColor: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurface.copy(0.5f), modifier = Modifier.size(20.dp))
            Column {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
            }
        }
        TextButton(onClick = onClick) {
            Text(actionLabel, color = actionColor, fontWeight = FontWeight.SemiBold)
        }
    }
}

// Search bar

@Composable
fun SearchBar(query: String, onQueryChange: (String) -> Unit, onSearch: () -> Unit, onClear: () -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = {
            Text(
                "Search compound by name...",
                color = MaterialTheme.colorScheme.onSurface.copy(0.38f)
            )
        },
        leadingIcon = {
            Icon(
                Icons.Default.Search,
                null,
                tint = MaterialTheme.colorScheme.onSurface.copy(0.45f),
                modifier = Modifier.size(20.dp)
            )
        },
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (query.isNotEmpty()) {
                    IconButton(onClick = onClear) {
                        Icon(Icons.Default.Close, "Clear", tint = MaterialTheme.colorScheme.onSurface.copy(0.45f), modifier = Modifier.size(18.dp))
                    }
                }
                IconButton(onClick = onSearch) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            "Search",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        shape = RoundedCornerShape(20.dp),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(0.5f),
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        )
    )
}

// Suggestions dropdown

@Composable
fun SuggestionsDropdown(suggestions: List<String>, onSelect: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
            suggestions.forEachIndexed { index, suggestion ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(suggestion) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.Science,
                        null,
                        tint = MaterialTheme.colorScheme.primary.copy(0.7f),
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        suggestion.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (index < suggestions.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.15f))
            }
        }
    }
}

// History (Recents)

@Composable
fun HistorySection(history: List<String>, onSelect: (String) -> Unit, onClear: () -> Unit) {
    if (history.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 64.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(0.08f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Science, null, tint = MaterialTheme.colorScheme.primary.copy(0.5f), modifier = Modifier.size(52.dp))
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "Search any compound",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.7f)
                    )
                    Text(
                        "Try caffeine, aspirin, ethanol...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.38f)
                    )
                }
            }
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "RECENT SEARCHES",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(0.45f)
            )
            TextButton(onClick = onClear, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                Text("Clear all", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
            }
        }
        history.forEach { item ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onSelect(item) },
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Icon(
                        Icons.Default.History,
                        null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(0.3f),
                        modifier = Modifier.size(17.dp)
                    )
                    Text(
                        item.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(0.2f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

    }
}

// Compound header

@Composable
fun CompoundHeader(state: ChemUiState, isFavorite: Boolean, onToggleFavorite: () -> Unit) {
    val context = LocalContext.current
    val cm = remember { context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Row(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(72.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.primary.copy(0f)
                            )
                        )
                    )
            )
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = state.name.uppercase(),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.5).sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (state.formula.isNotBlank()) {
                    Text(
                        text = toSubscriptFormula(state.formula),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    state.cid?.let {
                        CompactIdentifier(label = "CID", value = it.toString()) {
                            cm.setPrimaryClip(ClipData.newPlainText("CID", it.toString()))
                        }
                    }
                    state.casNumber?.let {
                        CompactIdentifier(label = "CAS", value = it) {
                            cm.setPrimaryClip(ClipData.newPlainText("CAS", it))
                        }
                    }
                    if (state.weight.isNotBlank()) {
                        CompactIdentifier(label = "MW", value = "${state.weight} g/mol") {
                            cm.setPrimaryClip(ClipData.newPlainText("MW", "${state.weight} g/mol"))
                        }
                    }
                }
            }
        }
        IconButton(
            onClick = onToggleFavorite,
            modifier = Modifier.padding(top = 4.dp)
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                contentDescription = if (isFavorite) "Remove bookmark" else "Add bookmark",
                tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(0.35f)
            )
        }
    }
}

@Composable
private fun CompactIdentifier(label: String, value: String, onCopy: () -> Unit) {
    Column(
        modifier = Modifier.clickable { onCopy() },
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(0.4f),
            fontSize = 9.sp,
            letterSpacing = 0.8.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun ClickableIdentifier(label: String, value: String, cm: ClipboardManager) {
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))) { append("$label: ") }
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)) { append(value) }
        },
        style = MaterialTheme.typography.bodyMedium,
        softWrap = false,
        modifier = Modifier.clickable { cm.setPrimaryClip(ClipData.newPlainText(label, value)) }
    )
}

@Composable
fun PubChemCredits() {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp)
            .clickable {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://pubchem.ncbi.nlm.nih.gov")))
            },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Science,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(0.25f),
            modifier = Modifier.size(12.dp)
        )
        Spacer(Modifier.width(5.dp))
        Text(
            "Compound data from PubChem (NIH/NCBI)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(0.28f),
            fontSize = 10.sp
        )
    }
}

// Structure viewer

private suspend fun saveSdfFile(context: Context, compoundName: String, sdfData: String) {
    val fileName = "${compoundName.replace(" ", "_").lowercase()}_3d.sdf"
    val sdfBytes = sdfData.toByteArray()
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "chemical/x-mdl-sdfile")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            uri?.let { context.contentResolver.openOutputStream(it)?.use { os -> os.write(sdfBytes) } }
        } else {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            File(dir, fileName).writeBytes(sdfBytes)
        }
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Saved $fileName to Downloads", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun StructureViewer(state: ChemUiState, vm: ChemViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Runtime storage permission for Android 9 and below
    val writePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            scope.launch(Dispatchers.IO) {
                saveSdfFile(context, state.name, state.sdfData ?: return@launch)
            }
        } else {
            Toast.makeText(context, "Storage permission denied. Cannot save file", Toast.LENGTH_LONG).show()
        }
    }

    fun triggerDownload() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            writePermissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            scope.launch(Dispatchers.IO) {
                saveSdfFile(context, state.name, state.sdfData ?: return@launch)
            }
        }
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.outline.copy(0.12f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val twoDActive = state.activeTab == MolTab.TWO_D
                        Surface(
                            onClick = { vm.setTab(MolTab.TWO_D) },
                            shape = RoundedCornerShape(50),
                            color = if (twoDActive) MaterialTheme.colorScheme.primary else Color.Transparent,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                "2D Structure",
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = if (twoDActive) Color.White else MaterialTheme.colorScheme.onSurface.copy(0.55f),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                        }
                        val threeDActive = state.activeTab == MolTab.THREE_D
                        Surface(
                            onClick = { vm.setTab(MolTab.THREE_D) },
                            shape = RoundedCornerShape(50),
                            color = if (threeDActive) MaterialTheme.colorScheme.primary else Color.Transparent,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                "3D Model",
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = if (threeDActive) Color.White else MaterialTheme.colorScheme.onSurface.copy(0.55f),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .clipToBounds(),
                contentAlignment = Alignment.Center
            ) {
                when (state.activeTab) {
                    MolTab.TWO_D -> state.cid?.let { cid ->
                        AsyncImage(
                            model = "https://pubchem.ncbi.nlm.nih.gov/rest/pug/compound/cid/$cid/PNG?image_size=large",
                            contentDescription = "2D Structure",
                            modifier = Modifier.fillMaxSize().padding(12.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                    MolTab.THREE_D -> {
                        if (state.isLoadingSdf) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                                Text("Loading 3D model...", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
                            }
                        } else if (state.sdfData != null) {
                            val isDark = !MaterialTheme.colorScheme.background.luminance().let { it > 0.5f }
                            Viewer3D(cid = state.cid ?: 0, sdfData = state.sdfData, isDark = isDark)
                            IconButton(
                                onClick = { triggerDownload() },
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(top = 36.dp, start = 0.dp)
                                    .size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Download,
                                    contentDescription = "Download SDF",
                                    tint = if (!MaterialTheme.colorScheme.background.luminance().let { it > 0.5f })
                                        Color.White.copy(0.4f) else Color.Black.copy(0.35f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else {
                            var showWhyDialog by remember { mutableStateOf(false) }

                            if (showWhyDialog) {
                                No3DModelDialog(onDismiss = { showWhyDialog = false })
                            }

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.VisibilityOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface.copy(0.3f),
                                    modifier = Modifier.size(32.dp)
                                )
                                Text(
                                    "3D model not available",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(0.4f)
                                )
                                Text(
                                    "Learn why →",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                    modifier = Modifier.clickable { showWhyDialog = true }
                                )
                            }
                        }

                    }
                }
            }
        }

    }
}

// Dialog that appears when 3D model is unavailable
@Composable
fun No3DModelDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Got it", fontWeight = FontWeight.SemiBold)
            }
        },
        icon = {
            Icon(
                Icons.Default.VisibilityOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                "Why is 3D unavailable?",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "PubChem pre-computes 3D conformer models for ~90% of its compounds using " +
                            "the OMEGA toolkit and MMFF94s force field. A compound gets no 3D model if " +
                            "it fails any of these criteria:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                )

                val reasons = listOf(
                    "Too large : More than 50 non-hydrogen atoms",
                    "Too flexible : More than 15 rotatable bonds",
                    "Unsupported elements : Only H, C, N, O, F, Si, P, S, Cl, Br and I are supported by the force field. Metals are not supported.",
                    "Salt or mixture : The compound has more than one covalent unit (e.g. NaCl). PubChem may have a 3D model for the parent free base instead",
                    "Too many undefined stereo centres : 6 or more undefined atom or bond stereo centres",
                    "Conformer generation failure : The algorithm could not converge on a stable geometry",
                )

                reasons.forEach { text ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text("•", style = MaterialTheme.typography.bodySmall)
                        Text(
                            text,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                Text(
                    "Source: PubChem3D project",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                )
            }
        },
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface
    )
}

// Identifiers

@Composable
fun IdentifiersSection(state: ChemUiState, context: Context) {
    var showInfo by remember { mutableStateOf(false) }

    if (showInfo) {
        InfoDialog(
            title = "About Identifiers",
            entries = listOf(
                "IUPAC Name" to "The systematic name assigned by the International Union of Pure and Applied Chemistry. Uniquely describes the structure using a standard naming convention.",
                "CID" to "PubChem Compound ID (CID) is a unique number assigned by the PubChem database to identify this exact compound.",
                "CAS Number" to "Chemical Abstracts Service registry number. A globally recognized unique identifier assigned to every chemical substance.",
                "SMILES" to "Simplified Molecular Input Line Entry System. A text notation that describes the molecular structure using atoms and bonds in a single line.",
                "InChI" to "International Chemical Identifier. A standard text identifier for chemical substances designed to be unique and non-proprietary.",
                "InChIKey" to "A fixed-length, hashed version of the full InChI. Easier to search and index than the full InChI string.",
                "Empirical Formula" to "The simplest whole-number ratio of atoms in a compound. Differs from molecular formula for compounds like benzene (C₆H₆ → CH).",
                "Formal Charge" to "The electric charge assigned to an atom in a molecule, assuming all bonds are equally shared. Non-zero means the compound is an ion."
            ),
            onDismiss = { showInfo = false }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CardSectionHeader("Identifiers") { showInfo = true }
            if (state.iupacName.isNotBlank()) IdentifierRow("IUPAC Name", state.iupacName, context, mono = false)
            if (state.connectivitySmiles.isNotBlank()) IdentifierRow("SMILES (Connectivity)", state.connectivitySmiles, context)
            if (state.smiles.isNotBlank() && state.smiles != state.connectivitySmiles) IdentifierRow("SMILES (Full)", state.smiles, context)
            if (state.inchiKey.isNotBlank()) IdentifierRow("InChIKey", state.inchiKey, context)
            if (state.inchi.isNotBlank()) IdentifierRow("InChI", state.inchi, context)
            if (state.empiricalFormula.isNotBlank() && state.empiricalFormula != state.formula) IdentifierRow("Empirical Formula", toSubscriptFormula(state.empiricalFormula), context)
            if (state.charge != 0) IdentifierRow("Formal Charge", state.charge.toString(), context)
        }
    }
}

@Composable
fun IdentifierRow(label: String, value: String, context: Context, mono: Boolean = true) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    var expanded by remember { mutableStateOf(false) }
    val isLong = value.length > 80

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                when {
                    isLong && !expanded -> expanded = true
                    isLong && expanded -> expanded = false
                    else -> cm.setPrimaryClip(ClipData.newPlainText(label, value))
                }
            }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(0.4f)
            )
            if (isLong) {
                Text(
                    if (expanded) "collapse" else "expand",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary.copy(0.7f),
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable { expanded = !expanded }
                )
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(
            value,
            style = if (mono) MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace) else MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.onSurface.copy(0.88f),
            lineHeight = 18.sp,
            maxLines = if (expanded || !isLong) Int.MAX_VALUE else 2,
            overflow = if (expanded || !isLong) androidx.compose.ui.text.style.TextOverflow.Visible else androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
        if (isLong && expanded) {
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = { cm.setPrimaryClip(ClipData.newPlainText(label, value)) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Copy", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        HorizontalDivider(modifier = Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.outline.copy(0.12f))
    }
}

// Elemental analysis (Percentage Composition)

@Composable
fun ElementalSection(data: List<ElementData>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            var showInfo by remember { mutableStateOf(false) }
            if (showInfo) {
                InfoDialog(
                    title = "Elemental Analysis",
                    entries = listOf(
                        "What is this?" to "Shows the percentage of each element by mass in the compound. Calculated from atomic weights and the molecular formula.",
                        "Example" to "Water (H₂O) is ~11% hydrogen and ~89% oxygen by mass, since oxygen atoms are much heavier than hydrogen atoms.",
                        "Why it matters" to "Useful in analytical chemistry to verify compound identity, and in nutrition science to understand nutrient composition."
                    ),
                    onDismiss = { showInfo = false }
                )
            }
            CardSectionHeader("Elemental Analysis") { showInfo = true }
            data.forEach { el ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(0.1f), RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            el.element,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(MaterialTheme.colorScheme.outline.copy(0.12f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(el.percentage / 100f)
                                .clip(RoundedCornerShape(5.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.primary.copy(0.6f)
                                        )
                                    )
                                )
                        )
                    }
                    Text(
                        "${"%.1f".format(el.percentage)}%",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.width(44.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(0.55f),
                        textAlign = TextAlign.End
                    )
                }
            }
        }

    }
}

// Synonyms

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SynonymsSection(synonyms: List<String>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel("Synonyms")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                synonyms.forEach { syn ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary.copy(0.07f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(0.18f))
                    ) {
                        Text(
                            syn,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

    }
}

// Description

@Composable
fun DescriptionSection(state: ChemUiState, onPubChem: () -> Unit, onWiki: () -> Unit, onAI: () -> Unit, onRegenerate: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionLabel("Description")
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.outline.copy(0.1f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(4.dp)) {
                    listOf(
                        DescSource.PUBCHEM to "PubChem",
                        DescSource.WIKI to "Wikipedia",
                        DescSource.AI to "AI ✨"
                    ).forEach { (src, label) ->
                        val active = state.descSource == src
                        val onClick = when (src) {
                            DescSource.PUBCHEM -> onPubChem
                            DescSource.WIKI -> onWiki
                            DescSource.AI -> onAI
                        }
                        Surface(
                            onClick = onClick,
                            shape = RoundedCornerShape(50),
                            color = if (active) MaterialTheme.colorScheme.primary else Color.Transparent,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                label,
                                modifier = Modifier.padding(vertical = 7.dp),
                                color = if (active) Color.White else MaterialTheme.colorScheme.onSurface.copy(0.55f),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            if (state.isLoadingDesc) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            } else {
                val text = when (state.descSource) {
                    DescSource.PUBCHEM -> state.pubDescription ?: "PubChem description not available."
                    DescSource.WIKI    -> state.wikiDescription ?: "Wikipedia description not available for this compound."
                    DescSource.AI      -> state.aiDescription   ?: "AI description not available. Check your API key in Settings."
                }
                Text(text, style = MaterialTheme.typography.bodyMedium, lineHeight = 22.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.85f))
                if (state.descSource == DescSource.AI && state.aiDescription != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onRegenerate, contentPadding = PaddingValues(0.dp)) {
                            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Regenerate", style = MaterialTheme.typography.labelMedium)
                        }
                        Spacer(Modifier.weight(1f))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary.copy(0.08f)
                        ) {
                            Text(
                                "via ${if (state.aiProvider == AiProvider.GEMINI) "Gemini" else "Groq"}",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary.copy(0.8f)
                            )
                        }
                    }
                }
            }
        }

    }
}

@Composable
fun SourceBtn(label: String, active: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
            contentColor = if (active) Color.White else MaterialTheme.colorScheme.onSurface
        ),
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp),
        elevation = ButtonDefaults.buttonElevation(if (active) 2.dp else 0.dp),
        border = if (!active) BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(0.5f)) else null
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
    }
}

// API Provider dialog

@Composable
fun AiProviderDialog(onSelect: (AiProvider) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose AI Provider", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Select which model to use for AI descriptions.", style = MaterialTheme.typography.bodyMedium)
                Card(
                    onClick = { onSelect(AiProvider.GEMINI) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Google Gemini", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                        Text("gemini-flash-latest", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace)
                    }
                }
                Card(
                    onClick = { onSelect(AiProvider.GROQ) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Groq Cloud", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                        Text("openai/gpt-oss-120b", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

// API Key dialog

@Composable
fun ApiKeyDialog(title: String, link: String, current: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var key by remember { mutableStateOf(current) }
    var visible by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Required for AI descriptions.", style = MaterialTheme.typography.bodySmall)
                val context = LocalContext.current
                Text(
                    "Get a free key at $link",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://$link"))
                        context.startActivity(intent)
                    }
                )
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("API Key") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { visible = !visible }) {
                            Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                        }
                    }
                )
                Text("Stored locally on your device only.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
            }
        },
        confirmButton = {
            Button(onClick = { if (key.isNotBlank()) onSave(key.trim()) }, shape = RoundedCornerShape(10.dp)) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

// Utilities

@Composable
fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.onSurface.copy(0.45f)
    )
}

// Info dialog

@Composable
fun InfoDialog(title: String, entries: List<Pair<String, String>>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                entries.forEach { (term, explanation) ->
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(term, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text(explanation, style = MaterialTheme.typography.bodySmall, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.8f))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Got it") } },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
fun CardSectionHeader(label: String, onInfoClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        SectionLabel(label)
        IconButton(onClick = onInfoClick, modifier = Modifier.size(20.dp)) {
            Icon(Icons.Default.Info, contentDescription = "Info", tint = MaterialTheme.colorScheme.onSurface.copy(0.3f), modifier = Modifier.size(14.dp))
        }
    }
}

fun toSubscriptFormula(formula: String): String {
    val sub = mapOf('0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄', '5' to '₅', '6' to '₆', '7' to '₇', '8' to '₈', '9' to '₉')
    val regex = Regex("([A-Za-z\\)])(\\d+)")
    return regex.replace(formula) { match ->
        val prefix = match.groupValues[1]
        val digits = match.groupValues[2]
        prefix + digits.map { sub[it] ?: it }.joinToString("")
    }
}

// Favorites sheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesSheet(
    favorites: List<FavoriteCompound>,
    onSelect: (String) -> Unit,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("Favorites", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
            if (favorites.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .background(MaterialTheme.colorScheme.primary.copy(0.08f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.BookmarkBorder, null, tint = MaterialTheme.colorScheme.primary.copy(0.5f), modifier = Modifier.size(36.dp))
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("No favorites yet", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                            Text("Tap the bookmark icon on any compound", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(0.38f))
                        }
                    }
                }
            } else {
                favorites.forEach { fav ->
                    Card(
                        onClick = { onSelect(fav.name) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(fav.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                if (fav.formula.isNotBlank()) {
                                    Text(toSubscriptFormula(fav.formula), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                                }
                                if (fav.molecularWeight.isNotBlank()) {
                                    Text("MW: ${fav.molecularWeight} g/mol", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(0.45f))
                                }
                            }
                            IconButton(onClick = { onDelete(fav.cid) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error.copy(0.65f), modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }

    }
}

// GHS Safety

private val ghsEmoji = mapOf(
    "GHS01" to "💥", "GHS02" to "🔥", "GHS03" to "🔆",
    "GHS04" to "🔵", "GHS05" to "⚗️", "GHS06" to "☠️",
    "GHS07" to "⚠️", "GHS08" to "🫁", "GHS09" to "🌿"
)

private val ghsLabel = mapOf(
    "GHS01" to "Explosive", "GHS02" to "Flammable", "GHS03" to "Oxidizing",
    "GHS04" to "Compressed Gas", "GHS05" to "Corrosive", "GHS06" to "Toxic",
    "GHS07" to "Harmful", "GHS08" to "Health Hazard", "GHS09" to "Environmental"
)

private val DangerRed = Color(0xFFDC2626)
private val WarningAmber = Color(0xFFD97706)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SafetySection(ghsData: GhsData?, isLoading: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            var showInfo by remember { mutableStateOf(false) }
            if (showInfo) {
                InfoDialog(
                    title = "GHS Safety",
                    entries = listOf(
                        "What is GHS?" to "The Globally Harmonized System of Classification and Labelling of Chemicals (GHS) is a UN standard for communicating chemical hazards worldwide.",
                        "Signal Word" to "'Danger' indicates a more severe hazard. 'Warning' indicates a less severe hazard.",
                        "Pictograms" to "Standardized symbols (GHS01–GHS09) that visually communicate the type of hazard (e.g flammability, toxicity, corrosion, etc.)",
                        "Hazard Statements" to "Standardized H-codes that describe the nature and degree of hazard. For example, H225 means 'Highly flammable liquid and vapour'.",
                        "Data source" to "GHS data is sourced from PubChem's aggregated classification records, which combine data from multiple regulatory bodies."
                    ),
                    onDismiss = { showInfo = false }
                )
            }
            CardSectionHeader("GHS Safety Information") { showInfo = true }

            if (isLoading) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            } else if (ghsData == null) {
                Text("No GHS classification available.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
            } else {
                ghsData.signalWord?.let { word ->
                    val isDanger = word.equals("Danger", ignoreCase = true)
                    val badgeColor = if (isDanger) DangerRed else WarningAmber
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = badgeColor.copy(alpha = 0.1f),
                        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                if (isDanger) Icons.Default.Error else Icons.Default.Warning,
                                null,
                                tint = badgeColor,
                                modifier = Modifier.size(18.dp)
                            )

                            Text(
                                word.uppercase(),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = badgeColor,
                                letterSpacing = 1.5.sp
                            )
                        }
                    }
                }

                if (ghsData.pictogramCodes.isNotEmpty()) {
                    Text("PICTOGRAMS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(0.45f), letterSpacing = 0.5.sp)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ghsData.pictogramCodes.forEach { code ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(0.25f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(ghsEmoji[code] ?: "⚠️", fontSize = 28.sp)
                                    Text(code, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    Text(ghsLabel[code] ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(0.5f), fontSize = 9.sp, textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }
                }

                if (ghsData.hazardStatements.isNotEmpty()) {
                    Text("HAZARD STATEMENTS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(0.45f), letterSpacing = 0.5.sp)
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        ghsData.hazardStatements.take(8).forEach { statement ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 5.dp)
                                        .size(5.dp)
                                        .background(MaterialTheme.colorScheme.primary.copy(0.5f), CircleShape)
                                )
                                Text(statement, style = MaterialTheme.typography.bodySmall, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.85f))
                            }
                        }
                    }
                }
            }
        }

    }
}

@Composable
fun FavoritesInline(
    favorites: List<FavoriteCompound>,
    onSelect: (String) -> Unit,
    onDelete: (Long) -> Unit
) {
    if (favorites.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(top = 64.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier.size(80.dp).background(MaterialTheme.colorScheme.primary.copy(0.08f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.BookmarkBorder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(0.5f),
                        modifier = Modifier.size(40.dp)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("No bookmarks yet", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface.copy(0.7f))
                    Text("Tap the bookmark icon on any compound", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(0.38f))
                }
            }
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("FAVORITES", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.45f))
        favorites.forEach { fav ->
            Card(
                onClick = { onSelect(fav.name) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier.padding(10.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.background,
                        modifier = Modifier.size(64.dp)
                    ) {
                        AsyncImage(
                            model = "https://pubchem.ncbi.nlm.nih.gov/rest/pug/compound/cid/${fav.cid}/PNG?record_type=2d&image_size=small",
                            contentDescription = "Structure of ${fav.name}",
                            modifier = Modifier.fillMaxSize().padding(4.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(fav.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        if (fav.formula.isNotBlank()) {
                            Text(toSubscriptFormula(fav.formula), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                        }
                        if (fav.molecularWeight.isNotBlank()) {
                            Text("MW: ${fav.molecularWeight} g/mol", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(0.45f))
                        }
                    }
                    IconButton(onClick = { onDelete(fav.cid) }) {
                        Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error.copy(0.6f), modifier = Modifier.size(18.dp))
                    }
                }
            }

        }

    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsInline(
    isDark: Boolean,
    autoSuggest: Boolean,
    defaultDescSource: DescSource,
    aiProvider: AiProvider,
    hasGeminiKey: Boolean,
    hasGroqKey: Boolean,
    onToggleTheme: () -> Unit,
    onToggleAutoSuggest: () -> Unit,
    onSetDefaultDesc: (DescSource) -> Unit,
    onSetAiProvider: (AiProvider) -> Unit,
    onSetGeminiKey: () -> Unit,
    onSetGroqKey: () -> Unit,
    onClearGeminiKey: () -> Unit,
    onClearGroqKey: () -> Unit,
    onClearHistory: () -> Unit,
    cacheSizeBytes: Long = 0L,
    cacheDir: String = "",
    onClearCache: () -> Unit = {},
    onSetCacheDir: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("chemsearch_prefs", Context.MODE_PRIVATE) }
    var buildTapCount by remember { mutableIntStateOf(0) }
    var isDevMode by remember { mutableStateOf(prefs.getBoolean("dev_mode", false)) }
    var themeDropdownExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))

        SettingsSectionHeader("Appearance")
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.Palette, null, tint = MaterialTheme.colorScheme.onSurface.copy(0.5f), modifier = Modifier.size(20.dp))
                Column {
                    Text("Theme mode", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(if (isDark) "Dark" else "Light", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                }
            }
            Box {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(0.3f)),
                    modifier = Modifier.clickable { themeDropdownExpanded = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            if (isDark) Icons.Default.DarkMode else Icons.Default.LightMode,
                            null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            if (isDark) "Dark" else "Light",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Icon(Icons.Default.ArrowDropDown, null, tint = MaterialTheme.colorScheme.onSurface.copy(0.4f), modifier = Modifier.size(16.dp))
                    }
                }
                DropdownMenu(
                    expanded = themeDropdownExpanded,
                    onDismissRequest = { themeDropdownExpanded = false }
                ) {
                    listOf(false to "Light", true to "Dark").forEach { (dark, label) ->
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(
                                        if (dark) Icons.Default.DarkMode else Icons.Default.LightMode,
                                        null,
                                        tint = if (isDark == dark) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(0.6f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(label, color = if (isDark == dark) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                }
                            },
                            onClick = {
                                themeDropdownExpanded = false
                                if (isDark != dark) onToggleTheme()
                            },
                            trailingIcon = {
                                if (isDark == dark) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        SettingsSectionHeader("Search")
        SettingsToggleRow(icon = Icons.Default.Search, title = "Autosuggestions", subtitle = "Show dropdown while typing", checked = autoSuggest, onToggle = onToggleAutoSuggest)

        Spacer(Modifier.height(4.dp))
        SettingsSectionHeader("Default Description Source")
        Text("Automatically shown when you search a compound", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(0.5f), modifier = Modifier.padding(bottom = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(DescSource.PUBCHEM to "PubChem", DescSource.WIKI to "Wikipedia", DescSource.AI to "AI").forEach { (src, label) ->
                SourceBtn(label = label, active = defaultDescSource == src) { onSetDefaultDesc(src) }
            }
        }

        Spacer(Modifier.height(4.dp))
        SettingsSectionHeader("AI Provider")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(AiProvider.GEMINI to "Gemini", AiProvider.GROQ to "Groq").forEach { (prov, label) ->
                SourceBtn(label = label, active = aiProvider == prov) { onSetAiProvider(prov) }
            }
        }

        Spacer(Modifier.height(4.dp))
        SettingsSectionHeader("API Keys")
        if (hasGeminiKey) {
            SettingsActionRow(Icons.Default.Key, "Gemini API Key saved", "Tap to replace", "Replace", MaterialTheme.colorScheme.primary, onSetGeminiKey)
            SettingsActionRow(Icons.Default.DeleteOutline, "Remove Gemini Key", "Disables Gemini AI", "Remove", MaterialTheme.colorScheme.error, onClearGeminiKey)
        } else {
            SettingsActionRow(Icons.Default.Key, "No Gemini Key set", "Required for Gemini descriptions", "Add Key", MaterialTheme.colorScheme.primary, onSetGeminiKey)
        }

        HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outline.copy(0.1f))

        if (hasGroqKey) {
            SettingsActionRow(Icons.Default.Key, "Groq API Key saved", "Tap to replace", "Replace", MaterialTheme.colorScheme.primary, onSetGroqKey)
            SettingsActionRow(Icons.Default.DeleteOutline, "Remove Groq Key", "Disables Groq AI", "Remove", MaterialTheme.colorScheme.error, onClearGroqKey)
        } else {
            SettingsActionRow(Icons.Default.Key, "No Groq Key set", "Required for Groq descriptions", "Add Key", MaterialTheme.colorScheme.primary, onSetGroqKey)
        }

        Spacer(Modifier.height(4.dp))
        SettingsSectionHeader("Data")
        SettingsActionRow(Icons.Default.History, "Search History", "Clear all recent searches", "Clear", MaterialTheme.colorScheme.error, onClearHistory)

        // Cache settings
        var showCacheDirDialog by remember { mutableStateOf(false) }
        var cacheDirInput by remember { mutableStateOf(cacheDir) }

        if (showCacheDirDialog) {
            AlertDialog(
                onDismissRequest = { showCacheDirDialog = false },
                title = { Text("Cache location", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Enter a custom path or leave blank to use the default app cache directory.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.6f)
                        )
                        OutlinedTextField(
                            value = cacheDirInput,
                            onValueChange = { cacheDirInput = it },
                            label = { Text("Directory path") },
                            placeholder = { Text("Leave blank for default") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "Default: app internal cache. Custom paths must be writable.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.4f)
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        onSetCacheDir(cacheDirInput.trim())
                        showCacheDirDialog = false
                    }, shape = RoundedCornerShape(10.dp)) { Text("Save") }
                },
                dismissButton = { TextButton(onClick = { showCacheDirDialog = false }) { Text("Cancel") } },
                containerColor = MaterialTheme.colorScheme.surface
            )
        }

        val cacheSizeLabel = when {
            cacheSizeBytes == 0L -> "Empty"
            cacheSizeBytes < 1024 -> "${cacheSizeBytes} B"
            cacheSizeBytes < 1024 * 1024 -> "${"%.1f".format(cacheSizeBytes / 1024.0)} KB"
            else -> "${"%.2f".format(cacheSizeBytes / (1024.0 * 1024.0))} MB"
        }
        SettingsActionRow(
            icon = Icons.Default.Cached,
            title = "Compound cache",
            subtitle = "$cacheSizeLabel · ${if (cacheDir.isBlank()) "Default location" else cacheDir.takeLast(30)}",
            actionLabel = "Clear",
            actionColor = MaterialTheme.colorScheme.error,
            onClick = onClearCache
        )
        SettingsActionRow(
            icon = Icons.Default.FolderOpen,
            title = "Cache location",
            subtitle = if (cacheDir.isBlank()) "App internal cache (default)" else cacheDir,
            actionLabel = "Change",
            actionColor = MaterialTheme.colorScheme.primary,
            onClick = { cacheDirInput = cacheDir; showCacheDirDialog = true }
        )

        if (isDevMode) {
            Spacer(Modifier.height(4.dp))
            DebugSettingsSection(
                prefs = prefs,
                onDisableDevMode = {
                    isDevMode = false
                    buildTapCount = 0
                    prefs.edit().putBoolean("dev_mode", false).apply()
                    Toast.makeText(context, "Debug settings hidden", Toast.LENGTH_SHORT).show()
                }
            )
        }

        Spacer(Modifier.height(4.dp))
        SettingsSectionHeader("About")
        Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("ChemSearch for Android", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primary.copy(0.12f),
                        modifier = Modifier.clickable {
                            buildTapCount++
                            when (buildTapCount) {
                                3 -> Toast.makeText(context, "2 more taps to unlock debug settings", Toast.LENGTH_SHORT).show()
                                4 -> Toast.makeText(context, "1 more tap to unlock debug settings", Toast.LENGTH_SHORT).show()
                                5 -> {
                                    isDevMode = true
                                    prefs.edit().putBoolean("dev_mode", true).apply()
                                    Toast.makeText(context, "Debug settings unlocked", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    ) {
                        Text("v${BuildConfig.VERSION_NAME}  •  build ${BuildConfig.VERSION_CODE}", modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                    Text("Data from PubChem, Wikipedia, Google Gemini and Groq", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(0.55f))
                    Text("by FurtherSecrets", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.15f))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("SOURCE CODE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
                    Row(modifier = Modifier.clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/FurtherSecrets24680/chemsearch-android"))
                        context.startActivity(intent)
                    }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Code, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("GitHub Repository", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("LIBRARIES & CREDITS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
                    listOf(
                        "Jetpack Compose" to "UI Framework", "Material 3" to "Design System",
                        "Retrofit & OkHttp" to "Networking", "Coil" to "Image Loading",
                        "Native 3D Engine" to "3D Molecular Visualization", "Gemini & Groq" to "AI Models",
                        "Gson" to "JSON Parsing", "Kotlin Coroutines" to "Asynchronous Tasks"
                    ).forEach { (name, desc) ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                        }
                    }
                }
            }
        }

    }
}

// DEBUG SETTINGS
object DebugLog {
    private const val MAX = 200
    val lines = mutableStateListOf<String>()
    var verbose = false

    fun d(tag: String, msg: String) {
        Log.d(tag, msg)
        if (verbose) append("D/$tag: $msg")
    }
    fun e(tag: String, msg: String) {
        Log.e(tag, msg)
        append("E/$tag: $msg")
    }
    fun i(tag: String, msg: String) {
        Log.i(tag, msg)
        if (verbose) append("I/$tag: $msg")
    }
    private fun append(line: String) {
        val ts = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date())
        if (lines.size >= MAX) lines.removeAt(0)
        lines.add("$ts  $line")
    }
    fun clear() { lines.clear() }
}

@Composable
fun DebugSettingsSection(
    prefs: android.content.SharedPreferences,
    onDisableDevMode: () -> Unit
) {
    val context = LocalContext.current
    var verboseLogging by remember { mutableStateOf(prefs.getBoolean("debug_verbose", false)) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showPrefsDialog by remember { mutableStateOf(false) }
    var showLogsDialog by remember { mutableStateOf(false) }
    var showMemoryDialog by remember { mutableStateOf(false) }
    var showCrashConfirm by remember { mutableStateOf(false) }
    val logLines = DebugLog.lines

    if (showInfoDialog) {
        InfoDialog(
            title = "Debug Settings",
            entries = listOf(
                "Verbose logging" to "Writes detailed log lines tagged 'ChemSearch' to Android Logcat and to the in-app live log buffer. Disable in production to reduce noise.",
                "Live log viewer" to "Shows the in-app log buffer in real time (up to 200 lines). Verbose logs (D/) only appear when verbose logging is on. Errors (E/) are always captured. You can copy or clear the buffer.",
                "Inspect SharedPreferences" to "Dumps every key-value pair in the app's preference file. Includes API keys (masked), theme, history entries, dev mode flag, and anything else saved with SharedPreferences.",
                "Memory info" to "Shows current heap usage from the JVM runtime and the Android ActivityManager. Useful for spotting memory leaks or unusually high allocations.",
                "Network requests" to "Copies all 5 API base URLs to your clipboard for manual testing in a browser or HTTP client like Postman.",
                "Wipe all SharedPreferences" to "Calls prefs.edit().clear(). Removes everything: API keys, history, favorites, settings. Restart required for changes to take full effect.",
                "Force crash" to "Deliberately throws an unhandled RuntimeException. Used to verify that crash reporting / Logcat is working correctly. There is a confirmation step before it fires.",
                "Hide debug settings" to "Sets dev_mode=false and hides this section. Tap the build number 5 times in the About card to unlock it again."
            ),
            onDismiss = { showInfoDialog = false }
        )
    }

    if (showPrefsDialog) {
        AlertDialog(
            onDismissRequest = { showPrefsDialog = false },
            title = { Text("SharedPreferences dump", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (prefs.all.isEmpty()) {
                        Text("No keys stored.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                    } else {
                        prefs.all.entries.sortedBy { it.key }.forEach { (k, v) ->
                            val display = if (k.contains("key", ignoreCase = true) && v.toString().length > 8)
                                v.toString().take(4) + "••••" + v.toString().takeLast(4)
                            else v.toString()
                            Text(
                                text = "$k\n  → $display",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurface.copy(0.85f),
                                lineHeight = 16.sp,
                                modifier = Modifier.fillMaxWidth()
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.1f))
                        }
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val dump = prefs.all.entries.sortedBy { it.key }.joinToString("\n") { "${it.key} = ${it.value}" }
                        cm.setPrimaryClip(ClipData.newPlainText("prefs", dump))
                        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                    }) { Text("Copy") }
                    TextButton(onClick = { showPrefsDialog = false }) { Text("Close") }
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    if (showLogsDialog) {
        AlertDialog(
            onDismissRequest = { showLogsDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Live Logs", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                    Surface(shape = RoundedCornerShape(6.dp), color = if (verboseLogging) Color(0xFF22C55E).copy(0.15f) else MaterialTheme.colorScheme.outline.copy(0.1f)) {
                        Text(
                            if (verboseLogging) "● LIVE" else "○ PAUSED",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = if (verboseLogging) Color(0xFF22C55E) else MaterialTheme.colorScheme.onSurface.copy(0.4f),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            text = {
                Column(modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    if (logLines.isEmpty()) {
                        Text(
                            if (verboseLogging) "No logs yet. Perform an action in the app."
                            else "Verbose logging is off. Only errors are captured.\nEnable it to see debug logs.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                        )
                    } else {
                        logLines.toList().forEach { line ->
                            val isError = line.contains("E/")
                            Text(
                                line,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(0.8f),
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { DebugLog.clear() }) { Text("Clear") }
                    TextButton(onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("logs", logLines.joinToString("\n")))
                        Toast.makeText(context, "Copied ${logLines.size} lines", Toast.LENGTH_SHORT).show()
                    }) { Text("Copy") }
                    TextButton(onClick = { showLogsDialog = false }) { Text("Close") }
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    if (showMemoryDialog) {
        val rt = Runtime.getRuntime()
        val usedMb = (rt.totalMemory() - rt.freeMemory()) / 1_048_576L
        val totalMb = rt.totalMemory() / 1_048_576L
        val maxMb = rt.maxMemory() / 1_048_576L
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val mi = android.app.ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val availMb = mi.availMem / 1_048_576L
        val totalSystemMb = mi.totalMem / 1_048_576L
        AlertDialog(
            onDismissRequest = { showMemoryDialog = false },
            title = { Text("Memory info", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("JVM HEAP", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    listOf(
                        "Used" to "${usedMb} MB",
                        "Allocated" to "${totalMb} MB",
                        "Max" to "${maxMb} MB"
                    ).forEach { (k, v) ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(k, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                            Text(v, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), fontWeight = FontWeight.SemiBold)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.15f))
                    Text("SYSTEM RAM", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    listOf(
                        "Available" to "${availMb} MB",
                        "Total" to "${totalSystemMb} MB",
                        "Low memory" to if (mi.lowMemory) "YES ⚠️" else "No"
                    ).forEach { (k, v) ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(k, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                            Text(v, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showMemoryDialog = false }) { Text("Close") } },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    if (showCrashConfirm) {
        AlertDialog(
            onDismissRequest = { showCrashConfirm = false },
            title = { Text("Force crash?", fontWeight = FontWeight.Bold) },
            text = { Text("This will immediately crash the app with an unhandled exception. Used to verify crash reporting is working.") },
            confirmButton = {
                Button(
                    onClick = { throw RuntimeException("ChemSearch debug force crash") },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Crash now") }
            },
            dismissButton = { TextButton(onClick = { showCrashConfirm = false }) { Text("Cancel") } },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(0.06f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.BugReport, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Text(
                        "DEBUG SETTINGS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.5.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = { showInfoDialog = true }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary.copy(0.6f), modifier = Modifier.size(16.dp))
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(0.2f), modifier = Modifier.padding(vertical = 4.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Terminal, null, tint = MaterialTheme.colorScheme.onSurface.copy(0.5f), modifier = Modifier.size(20.dp))
                    Column {
                        Text("Verbose logging", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text("Tag: ChemSearch", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
                    }
                }
                Switch(
                    checked = verboseLogging,
                    onCheckedChange = {
                        verboseLogging = it
                        DebugLog.verbose = it
                        prefs.edit().putBoolean("debug_verbose", it).apply()
                        DebugLog.d("ChemSearch", if (it) "Verbose logging enabled" else "Verbose logging disabled")
                    }
                )
            }

            // Live logs
            SettingsActionRow(
                icon = Icons.AutoMirrored.Filled.Feed,
                title = "Live log viewer",
                subtitle = "${logLines.size} line${if (logLines.size != 1) "s" else ""} captured",
                actionLabel = "Open",
                actionColor = MaterialTheme.colorScheme.primary,
                onClick = { showLogsDialog = true }
            )

            // SharedPreferences dump
            SettingsActionRow(
                icon = Icons.Default.Storage,
                title = "Inspect SharedPreferences",
                subtitle = "${prefs.all.size} keys stored",
                actionLabel = "View",
                actionColor = MaterialTheme.colorScheme.primary,
                onClick = { showPrefsDialog = true }
            )

            // Memory info
            SettingsActionRow(
                icon = Icons.Default.Memory,
                title = "Memory info",
                subtitle = "JVM heap + system RAM",
                actionLabel = "View",
                actionColor = MaterialTheme.colorScheme.primary,
                onClick = { showMemoryDialog = true }
            )

            // API endpoints copy
            SettingsActionRow(
                icon = Icons.Default.Hub,
                title = "API endpoints",
                subtitle = "PubChem · Wikipedia · Gemini · Groq",
                actionLabel = "Copy",
                actionColor = MaterialTheme.colorScheme.primary,
                onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val endpoints = listOf(
                        "PubChem PUG REST: https://pubchem.ncbi.nlm.nih.gov/rest/pug/",
                        "PubChem PUG View: https://pubchem.ncbi.nlm.nih.gov/rest/pug_view/",
                        "Wikipedia REST: https://en.wikipedia.org/api/rest_v1/",
                        "Gemini: https://generativelanguage.googleapis.com/v1beta/",
                        "Groq: https://api.groq.com/openai/v1/"
                    ).joinToString("\n")
                    cm.setPrimaryClip(ClipData.newPlainText("endpoints", endpoints))
                    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                }
            )

            // Wipe prefs
            SettingsActionRow(
                icon = Icons.Default.DeleteSweep,
                title = "Wipe all SharedPreferences",
                subtitle = "Clears keys, history, API keys, settings",
                actionLabel = "Wipe",
                actionColor = MaterialTheme.colorScheme.error,
                onClick = {
                    prefs.edit().clear().apply()
                    DebugLog.e("ChemSearch", "SharedPreferences wiped by developer")
                    Toast.makeText(context, "All preferences wiped. Restart the app.", Toast.LENGTH_LONG).show()
                }
            )

            // Force crash
            SettingsActionRow(
                icon = Icons.Default.Warning,
                title = "Force crash",
                subtitle = "Throws unhandled exception for testing",
                actionLabel = "Crash",
                actionColor = MaterialTheme.colorScheme.error,
                onClick = { showCrashConfirm = true }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(0.2f), modifier = Modifier.padding(vertical = 4.dp))
            TextButton(
                onClick = onDisableDevMode,
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(Icons.Default.VisibilityOff, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurface.copy(0.4f))
                Spacer(Modifier.width(6.dp))
                Text("Hide debug settings", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
            }
        }
    }
}

// TOOLS SCREEN
@Composable
fun ToolsScreen(isDark: Boolean, jumpToTool: Int = 0, onNavigateToSearch: () -> Unit = {}) {
    var selectedTool by remember { mutableStateOf(0) }
    var toolSearch by remember { mutableStateOf("") }

    LaunchedEffect(jumpToTool) {
        if (jumpToTool != 0) selectedTool = jumpToTool
    }

    val allTools = listOf(
        1 to Triple(Icons.Default.ViewInAr,    "Custom 3D Molecule Viewer",  "Load any .sdf or .mol file and view it in 3D"),
        2 to Triple(Icons.Default.Calculate,   "Molar Mass Calculator",       "Enter a molecular formula and get the molar mass"),
        3 to Triple(Icons.Default.Science,     "Oxidation State Finder",      "Find oxidation states of each element in a compound"),
        4 to Triple(Icons.Default.AccountTree, "SMILES Visualizer",           "Paste a SMILES string to view its 2D and 3D structure"),
        5 to Triple(Icons.Default.SwapHoriz,   "Reaction Balancer",           "Balance any chemical equation automatically"),
        6 to Triple(Icons.Default.Biotech,     "Isomer Finder",               "Enter a molecular formula to find its structural isomers"),
    )

    val filteredTools = remember(toolSearch) {
        if (toolSearch.isBlank()) allTools
        else allTools.filter {
            it.second.second.contains(toolSearch, ignoreCase = true) ||
                    it.second.third.contains(toolSearch, ignoreCase = true)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Tools",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        if (selectedTool == 0) {
            OutlinedTextField(
                value = toolSearch,
                onValueChange = { toolSearch = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        "Search tools…",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    if (toolSearch.isNotEmpty()) {
                        IconButton(onClick = { toolSearch = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                modifier = Modifier.size(16.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )

            if (filteredTools.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No tools match \"$toolSearch\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            } else {
                filteredTools.forEach { (id, triple) ->
                    val (icon, title, subtitle) = triple
                    ToolCard(icon = icon, title = title, subtitle = subtitle, onClick = { selectedTool = id })
                }
            }
        } else {
            TextButton(
                onClick = { selectedTool = 0; toolSearch = "" },
                contentPadding = PaddingValues(horizontal = 0.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Back to Tools")
            }

            when (selectedTool) {
                1 -> SdfViewerTool(isDark = isDark)
                2 -> MolarMassCalculator()
                3 -> OxidationStateFinder()
                4 -> SmilesVisualizer(isDark = isDark)
                5 -> ReactionBalancer()
                6 -> IsomerFinderTool(onNavigateToSearch = onNavigateToSearch)
            }
        }
    }
}

@Composable
private fun ToolCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(0.1f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
            }

            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurface.copy(0.3f))
        }
    }
}

// TOOL 1 : CUSTOM 3D MOLECULE VIEWER
@Composable
fun SdfViewerTool(isDark: Boolean) {
    val context = LocalContext.current
    var sdfContent by remember { mutableStateOf<String?>(null) }
    var fileName by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val stream = context.contentResolver.openInputStream(uri)
            val content = stream?.bufferedReader()?.readText()
            stream?.close()
            if (content != null) {
                sdfContent = content
                fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "file.sdf"
                error = null
            } else {
                error = "Could not read file."
            }
        } catch (e: Exception) {
            error = "Error reading file: ${e.message}"
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        var showInfo by remember { mutableStateOf(false) }
        if (showInfo) {
            InfoDialog(
                title = "Custom 3D Molecule Viewer",
                entries = listOf(
                    "What is an SDF file?" to "Structure Data File (.sdf) is a standard chemical file format that stores 3D atomic coordinates and bond information for one or more molecules.",
                    "What is a MOL file?" to "A .mol file is the single-molecule variant of SDF. Both formats are widely exported by chemistry software like ChemDraw, Avogadro, and PubChem.",
                    "How to get an SDF file" to "You can download SDF files from PubChem by searching a compound and choosing '3D SDF' from the download options, or export them from any molecular editor.",
                    "Controls" to "Drag to rotate the molecule. Pinch to zoom in and out. The model auto-spins when idle, so tap to pause. Tap the reset button to return to the default view.",
                    "CPK coloring" to "Atoms are colored using the Jmol CPK convention: carbon is dark grey, oxygen is red, nitrogen is blue, hydrogen is white, and so on across all 118 elements."
                ),
                onDismiss = { showInfo = false }
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Custom 3D Molecule Viewer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            IconButton(onClick = { showInfo = true }, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Info, contentDescription = "Info", tint = MaterialTheme.colorScheme.onSurface.copy(0.35f), modifier = Modifier.size(16.dp))
            }
        }
        if (sdfContent == null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(0.1f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.FolderOpen, null, tint = MaterialTheme.colorScheme.primary.copy(0.6f), modifier = Modifier.size(40.dp)) }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("No file loaded", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Load any .sdf or .mol file from your device to view it in 3D",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.5f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                    Button(
                        onClick = { filePicker.launch("*/*") },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Choose File")
                    }
                }
            }
        } else {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.InsertDriveFile, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text(fileName ?: "file.sdf", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { filePicker.launch("*/*") }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Text("Change", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Box(modifier = Modifier.fillMaxWidth().height(360.dp)) {
                    Viewer3D(cid = -1L, sdfData = sdfContent!!, isDark = isDark)
                }
            }
        }

        if (error != null) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(0.5f))
            ) {
                Text(error!!, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

// TOOL 2 : MOLAR MASS CALCULATOR
private val MOLAR_WEIGHTS = mapOf(
    "H" to 1.008, "He" to 4.003, "Li" to 6.941, "Be" to 9.012, "B" to 10.811,
    "C" to 12.011, "N" to 14.007, "O" to 15.999, "F" to 18.998, "Ne" to 20.180,
    "Na" to 22.990, "Mg" to 24.305, "Al" to 26.982, "Si" to 28.086, "P" to 30.974,
    "S" to 32.065, "Cl" to 35.453, "Ar" to 39.948, "K" to 39.098, "Ca" to 40.078,
    "Sc" to 44.956, "Ti" to 47.867, "V" to 50.942, "Cr" to 51.996, "Mn" to 54.938,
    "Fe" to 55.845, "Co" to 58.933, "Ni" to 58.693, "Cu" to 63.546, "Zn" to 65.38,
    "Ga" to 69.723, "Ge" to 72.630, "As" to 74.922, "Se" to 78.971, "Br" to 79.904,
    "Kr" to 83.798, "Rb" to 85.468, "Sr" to 87.620, "Y" to 88.906, "Zr" to 91.224,
    "Nb" to 92.906, "Mo" to 95.950, "Tc" to 98.0, "Ru" to 101.07, "Rh" to 102.91,
    "Pd" to 106.42, "Ag" to 107.87, "Cd" to 112.41, "In" to 114.82, "Sn" to 118.71,
    "Sb" to 121.76, "Te" to 127.60, "I" to 126.90, "Xe" to 131.29, "Cs" to 132.91,
    "Ba" to 137.33, "La" to 138.91, "Ce" to 140.12, "Pr" to 140.91, "Nd" to 144.24,
    "Pm" to 145.0, "Sm" to 150.36, "Eu" to 151.96, "Gd" to 157.25, "Tb" to 158.93,
    "Dy" to 162.50, "Ho" to 164.93, "Er" to 167.26, "Tm" to 168.93, "Yb" to 173.05,
    "Lu" to 174.97, "Hf" to 178.49, "Ta" to 180.95, "W" to 183.84, "Re" to 186.21,
    "Os" to 190.23, "Ir" to 192.22, "Pt" to 195.08, "Au" to 196.97, "Hg" to 200.59,
    "Tl" to 204.38, "Pb" to 207.20, "Bi" to 208.98, "Po" to 209.0, "At" to 210.0,
    "Rn" to 222.0, "Fr" to 223.0, "Ra" to 226.0, "Ac" to 227.0, "Th" to 232.04,
    "Pa" to 231.04, "U" to 238.03, "Np" to 237.0, "Pu" to 244.0, "Am" to 243.0,
    "Cm" to 247.0, "Bk" to 247.0, "Cf" to 251.0, "Es" to 252.0, "Fm" to 257.0,
    "Md" to 258.0, "No" to 259.0, "Lr" to 262.0, "Rf" to 267.0, "Db" to 270.0,
    "Sg" to 271.0, "Bh" to 270.0, "Hs" to 277.0, "Mt" to 276.0, "Ds" to 281.0,
    "Rg" to 280.0, "Cn" to 285.0, "Nh" to 284.0, "Fl" to 289.0, "Mc" to 288.0,
    "Lv" to 293.0, "Ts" to 294.0, "Og" to 294.0
)

private data class CalcResult(
    val molarMass: Double,
    val breakdown: List<Triple<String, Int, Double>>,
    val error: String? = null
)

private fun parseFormulaForCalc(formula: String): Map<String, Int> {
    val result = mutableMapOf<String, Int>()
    val stack = ArrayDeque<MutableMap<String, Int>>().apply { addLast(result) }
    var i = 0
    val f = formula.trim()
    while (i < f.length) {
        when {
            f[i] == '(' -> { stack.addLast(mutableMapOf()); i++ }
            f[i] == ')' -> {
                i++
                var num = ""
                while (i < f.length && f[i].isDigit()) num += f[i++]
                val mult = num.toIntOrNull() ?: 1
                val top = stack.removeLast()
                top.forEach { (el, cnt) -> stack.last()[el] = (stack.last()[el] ?: 0) + cnt * mult }
            }
            f[i].isUpperCase() -> {
                var el = f[i].toString(); i++
                while (i < f.length && f[i].isLowerCase()) el += f[i++]
                var num = ""
                while (i < f.length && f[i].isDigit()) num += f[i++]
                val cnt = num.toIntOrNull() ?: 1
                stack.last()[el] = (stack.last()[el] ?: 0) + cnt
            }
            else -> i++
        }
    }
    return result
}

private fun calculateMolarMass(formula: String): CalcResult {
    if (formula.isBlank()) return CalcResult(0.0, emptyList(), "Enter a formula")
    val normalized = formula.trim()
    val hydrateRegex = Regex("""[·*](\d*\.?\d*)\s*([A-Z].*)$""")
    val dotHydrateRegex = Regex("""\.(\d+)([A-Z].*)$""")

    val parts: List<Pair<String, Double>> = run {
        val hydrateMatch = hydrateRegex.find(normalized)
        val dotMatch = dotHydrateRegex.find(normalized)
        val match = hydrateMatch ?: dotMatch
        if (match != null) {
            val mainPart = normalized.substring(0, match.range.first)
            val multiplier = match.groupValues[1].toDoubleOrNull() ?: 1.0
            val hydratePart = match.groupValues[2]
            listOf(mainPart to 1.0, hydratePart to multiplier)
        } else {
            listOf(normalized to 1.0)
        }
    }

    val combined = mutableMapOf<String, Int>()
    for ((part, multiplier) in parts) {
        if (part.isBlank()) continue
        val elements = try { parseFormulaForCalc(part) }
        catch (e: Exception) { return CalcResult(0.0, emptyList(), "Invalid formula syntax") }
        for ((el, cnt) in elements) {
            val scaled = (cnt * multiplier).let { if (it == it.toLong().toDouble()) it.toLong().toInt() else { return CalcResult(0.0, emptyList(), "Non-integer atom count from hydrate multiplier") } }
            combined[el] = (combined[el] ?: 0) + scaled
        }
    }

    if (combined.isEmpty()) return CalcResult(0.0, emptyList(), "Could not parse formula")
    val unknown = combined.keys.filter { it !in MOLAR_WEIGHTS }
    if (unknown.isNotEmpty()) return CalcResult(0.0, emptyList(), "Unknown element(s): ${unknown.joinToString(", ")}")
    var total = 0.0
    val breakdown = combined.map { (el, cnt) ->
        val contrib = MOLAR_WEIGHTS[el]!! * cnt
        total += contrib
        Triple(el, cnt, contrib)
    }.sortedByDescending { it.third }
    return CalcResult(total, breakdown)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MolarMassCalculator() {
    var input by remember { mutableStateOf("") }
    val result by remember(input) {
        mutableStateOf(
            if (input.isBlank()) null else calculateMolarMass(
                input
            )
        )
    }
    val focusManager = LocalFocusManager.current

    val examples = listOf("H₂O", "C₆H₁₂O₆", "NaCl", "H₂SO₄", "Ca(OH)₂", "C₂H₅OH", "Fe₂O₃")

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        var showInfo by remember { mutableStateOf(false) }
        if (showInfo) {
            InfoDialog(
                title = "Molar Mass Calculator",
                entries = listOf(
                    "What is molar mass?" to "Molar mass is the mass of one mole (6.022 × 10²³ particles) of a substance, expressed in grams per mole (g/mol). It equals the sum of atomic weights of all atoms in the formula.",
                    "How to enter a formula" to "Type the molecular formula using standard element symbols with numbers for atom counts. Parentheses are supported for groups, e.g. Ca(OH)2 or Al2(SO4)3.",
                    "Case sensitivity" to "Element symbols are case-sensitive! 'Co' is cobalt, 'CO' is carbon monoxide. Always capitalize only the first letter of each element symbol.",
                    "Atomic weights" to "Atomic weights used here are the standard values from IUPAC, based on the natural isotopic abundance of each element.",
                    "Elemental breakdown" to "The breakdown table shows each element's contribution to the total molar mass, both as an absolute value (g/mol) and as a percentage by mass.",
                ),
                onDismiss = { showInfo = false }
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Molar Mass Calculator", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            IconButton(onClick = { showInfo = true }, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Info, contentDescription = "Info", tint = MaterialTheme.colorScheme.onSurface.copy(0.35f), modifier = Modifier.size(16.dp))
            }
        }

        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("Molecular Formula") },
            placeholder = { Text("e.g. H2O, Ca(OH)2, CuSO4·5H2O") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            singleLine = true,
            visualTransformation = FormulaSubscriptTransformation,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            trailingIcon = {
                if (input.isNotBlank()) {
                    IconButton(onClick = { input = "" }) {
                        Icon(Icons.Default.Clear, null, modifier = Modifier.size(18.dp))
                    }
                }
            }
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Insert:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(0.45f))
            listOf("(" to "(", ")" to ")", "·" to "·").forEach { (label, insert) ->
                Surface(
                    onClick = { input += insert },
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(0.3f))
                ) {
                    Text(
                        label,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Surface(
                onClick = { if (input.isNotEmpty()) input = input.dropLast(1) },
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(0.3f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(0.2f))
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = "Backspace",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp).size(14.dp),
                    tint = MaterialTheme.colorScheme.error.copy(0.7f)
                )
            }
        }

        if (result != null) {
            if (result!!.error != null) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(
                            0.4f
                        )
                    )
                ) {
                    Text(
                        result!!.error!!,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            } else {
                Card(shape = RoundedCornerShape(16.dp)) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("MOLAR MASS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.45f))
                                Text(toSubscriptFormula(input), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primary.copy(0.1f),
                                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(0.4f))
                            ) {
                                Text(
                                    "${"%.4f".format(result!!.molarMass)} g/mol",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.12f))

                        Text(
                            "ELEMENTAL BREAKDOWN",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.45f)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Element", modifier = Modifier.weight(1.2f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
                            Text("Count", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
                            Text("g/mol", modifier = Modifier.weight(1.3f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
                            Text("%", modifier = Modifier.weight(0.7f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(0.4f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                        }

                        result!!.breakdown.forEach { (el, cnt, contrib) ->
                            val pct = (contrib / result!!.molarMass * 100).toFloat()
                            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(modifier = Modifier.weight(1.2f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Box(modifier = Modifier.size(26.dp).background(MaterialTheme.colorScheme.primary.copy(0.1f), RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                                            Text(el, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                                        }
                                    }
                                    Text("×$cnt", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface.copy(0.7f))
                                    Text("%.3f".format(contrib), modifier = Modifier.weight(1.3f), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                                    Text("%.1f%%".format(pct), modifier = Modifier.weight(0.7f), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary, textAlign = androidx.compose.ui.text.style.TextAlign.End)
                                }
                                Box(modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.outline.copy(0.1f))) {
                                    Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(pct / 100f).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.primary.copy(0.65f)))
                                }
                            }
                        }
                    }
                }
            }
        }

        Text(
            "EXAMPLES",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(0.4f)
        )
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("H2O", "NaCl", "Ca(OH)2", "C6H12O6", "H2SO4", "CuSO4·5H2O", "MgSO4·7H2O", "Fe2O3").forEach { ex ->
                val isActive = input == ex
                FilterChip(
                    selected = isActive,
                    onClick = { input = ex; focusManager.clearFocus() },
                    label = { Text(toSubscriptFormula(ex), style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace) }
                )
            }
        }

    }
}

// TOOL 3 : OXIDATION STATE FINDER
private val GROUP1  = setOf("Li","Na","K","Rb","Cs","Fr")
private val GROUP2  = setOf("Be","Mg","Ca","Sr","Ba","Ra")

private data class OsResult(
    val states: List<Pair<String, String>> = emptyList(),
    val note: String? = null,
    val error: String? = null
)
private fun osSign(v: Int) = if (v > 0) "+$v" else "$v"
private fun gcdOs(a: Long, b: Long): Long = if (b == 0L) a else gcdOs(b, a % b)

private fun findOxidationStates(formula: String, chargeIn: Int): OsResult {
    if (formula.isBlank()) return OsResult(error = "Enter a formula")
    val elements = try { parseFormulaForCalc(formula) }
    catch (e: Exception) { return OsResult(error = "Invalid formula syntax") }
    if (elements.isEmpty()) return OsResult(error = "Could not parse formula")

    val oCount = elements["O"] ?: 0
    val hCount = elements["H"] ?: 0
    val fCount = elements["F"] ?: 0
    val hasO = oCount > 0
    val hasH = hCount > 0
    val hasF = fCount > 0

    // Free / monoatomic
    if (elements.size == 1) {
        val (el, cnt) = elements.entries.first()
        return if (cnt == 1) OsResult(states = listOf(el to osSign(chargeIn)))
        else OsResult(states = listOf(el to "0"), note = "Free element. Oxidation state is 0")
    }

    val alkaliEl   = elements.keys.firstOrNull { it in GROUP1 }
    val alkalineEl = elements.keys.firstOrNull { it in GROUP2 }

    // OF₂ / higher oxygen fluorides
    if (elements.size == 2 && hasO && hasF) {
        val oOs = (chargeIn + fCount) / oCount
        return OsResult(
            states = listOf("F" to "-1", "O" to osSign(oOs)),
            note = "Oxygen fluoride : F is always -1, so O = ${osSign(oOs)}"
        )
    }

    // Superoxide  (O = -½)
    if (alkaliEl != null && elements.size == 2 && hasO) {
        val mCnt = elements[alkaliEl]!!
        if (oCount == mCnt * 2) {
            val metalSum = mCnt * 1
            if (chargeIn - metalSum == -mCnt) {
                return OsResult(
                    states = listOf(alkaliEl to "+1", "O" to "-\u00BD"),
                    note = "Superoxide : O₂⁻ unit, each O has oxidation state = -½"
                )
            }
        }
    }

    // Ozonides (O = -⅓)
    if (alkaliEl != null && elements.size == 2 && hasO) {
        val mCnt = elements[alkaliEl]!!
        if (oCount == mCnt * 3 && chargeIn - mCnt == -mCnt) {
            return OsResult(
                states = listOf(alkaliEl to "+1", "O" to "-\u2153"),
                note = "Ozonide : O₃⁻ unit, each O has oxidation state = -⅓"
            )
        }
    }

    // Peroxides  (O = -1)
    val isPeroxide: Boolean = when {
        // H₂O₂
        elements.size == 2 && hasH && hasO && hCount == 2 && oCount == 2 && chargeIn == 0 -> true
        // Alkali M₂O₂  (Na₂O₂: 2 Na, 2 O, ratio 1:1)
        alkaliEl != null && elements.size == 2 && hasO &&
                oCount == elements[alkaliEl]!! && chargeIn == 0 -> true
        // Alkaline earth MO₂  (BaO₂: 1 Ba, 2 O)
        alkalineEl != null && elements.size == 2 && hasO &&
                oCount == elements[alkalineEl]!! * 2 && chargeIn == 0 -> true
        // Peroxide anion O₂²⁻
        elements.size == 1 && hasO && oCount == 2 && chargeIn == -2 -> true
        // Generic: compound with exactly H and O where H:O = 1:1 and charge = 0 (like peroxy acids fragment)
        else -> false
    }

    if (isPeroxide) {
        val res = mutableListOf("O" to "-1")
        for ((el, cnt) in elements) {
            if (el == "O") continue
            val fixedOs = when {
                el in GROUP1  -> 1
                el in GROUP2  -> 2
                el == "H"     -> 1
                el == "F"     -> -1
                else          -> null
            }
            if (fixedOs != null) {
                res.add(el to osSign(fixedOs))
            } else {
                val knownSumLocal = elements.entries
                    .filter { it.key != el }
                    .sumOf { (e, c) ->
                        when {
                            e == "O"      -> -1 * c
                            e in GROUP1   ->  1 * c
                            e in GROUP2   ->  2 * c
                            e == "H"      ->  1 * c
                            else          ->  0
                        }
                    }
                val rem = chargeIn - knownSumLocal
                res.add(el to if (rem % cnt == 0) osSign(rem / cnt) else "$rem/$cnt")
            }
        }
        return OsResult(states = res, note = "Peroxide compound : O has oxidation state = -1")
    }

    // Metal hydrides  (H = -1)
    // Metal hydride: contains H, no O or F, and ALL non-H elements are metals
    // with fixed oxidation states. This covers binary (NaH), ternary (LiAlH4) and complex hydrides.
    val metalHydrideMetals = setOf(
        "Li","Na","K","Rb","Cs","Fr",  // Group 1
        "Be","Mg","Ca","Sr","Ba","Ra",  // Group 2
        "Al","Ga","In","Tl",             // Group 13 metals
        "B"                               // boron hydrides (BH4- etc.)
    )
    val fixedOsForMetal: (String) -> Int? = { el ->
        when {
            el in GROUP1 -> 1
            el in GROUP2 -> 2
            el == "Al" || el == "Ga" || el == "In" || el == "Tl" || el == "B" -> 3
            else -> null
        }
    }
    val isMetalHydride = hasH && !hasO && !hasF &&
            elements.keys.filter { it != "H" }.all { it in metalHydrideMetals }

    if (isMetalHydride) {
        val res = mutableListOf("H" to "-1")
        for ((el, _) in elements) {
            if (el == "H") continue
            val metalOs = fixedOsForMetal(el)
            res.add(el to if (metalOs != null) osSign(metalOs) else "?")
        }
        val sum = elements.entries.sumOf { (el, cnt) ->
            when (el) {
                "H" -> -1 * cnt
                else -> (fixedOsForMetal(el) ?: 0) * cnt
            }
        }
        return OsResult(
            states = res,
            note = "Metal hydride : H has oxidation state = -1" + if (sum != chargeIn) " (sum = $sum, charge = $chargeIn , check formula)" else ""
        )
    }

    val fixed = mutableMapOf<String, Int>()
    var knownSum = 0
    val unknowns = mutableListOf<String>()

    for ((el, cnt) in elements) {
        val os: Int? = when (el) {
            "F"                                     -> -1
            in GROUP1                               ->  1
            in GROUP2                               ->  2
            "Al","Ga","Sc","Y","La","Lu"            ->  3
            "Zn","Cd"                               ->  2
            "Ag"                                    ->  1
            "In","Tl"                               ->  3
            // Halogen electronegativity order: F > Cl > Br > I
            // A halogen is fixed at -1 only when NO more electronegative halogen is present.
            // If a more electronegative halogen exists, this one becomes the central atom
            // and is solved algebraically (e.g. ICl3: I=+3,Cl=-1; ClF3: Cl=+3,F=-1)
            "Cl" -> if (hasF) null else -1
            "Br" -> if (hasF || elements.containsKey("Cl")) null else -1
            "I"  -> if (hasF || elements.containsKey("Cl") || elements.containsKey("Br")) null else -1
            "O"                                     -> -2  // default; special cases above
            "H"                                     ->  1  // default; hydrides above
            else                                    ->  null
        }
        if (os != null) { fixed[el] = os; knownSum += os * cnt }
        else unknowns.add(el)
    }

    return when (unknowns.size) {
        0 -> {
            val states = fixed.entries.map { it.key to osSign(it.value) }
            if (knownSum != chargeIn)
                OsResult(states = states,
                    note = "Sum of known oxidation state ($knownSum) ≠ overall charge ($chargeIn). " +
                            "Possible mixed-valence, peroxo group, or formula error.")
            else OsResult(states = states)
        }
        1 -> {
            val unknown = unknowns[0]
            val cnt = elements[unknown]!!
            val rem = chargeIn - knownSum
            if (rem % cnt != 0) {
                val g = gcdOs(Math.abs(rem.toLong()), Math.abs(cnt.toLong())).toInt()
                val fracStr = "${rem / g}/${cnt / g}"
                val states = fixed.entries.map { it.key to osSign(it.value) } + listOf(unknown to fracStr)
                OsResult(states = states,
                    note = "Non-integer oxidation state for $unknown ($fracStr). It may indicate mixed-valence or a special compound.")
            } else {
                fixed[unknown] = rem / cnt
                OsResult(states = fixed.entries.map { it.key to osSign(it.value) })
            }
        }
        else -> {
            val states = fixed.entries.map { it.key to osSign(it.value) } +
                    unknowns.map { it to "?" }
            OsResult(states = states,
                error = "Cannot solve! multiple unknown elements: ${unknowns.joinToString(", ")}. " +
                        "For transition metal complexes, use the charge field to provide additional constraints.")
        }
    }
}

@Composable
fun OxidationStateFinder() {
    var formula by remember { mutableStateOf("") }
    var chargeInput by remember { mutableStateOf("0") }
    var result by remember { mutableStateOf<OsResult?>(null) }
    val focusManager = LocalFocusManager.current

    val examples = listOf("KMnO4", "H2SO4", "Fe2O3", "Cr2O7" to -2, "NaCl" to 0, "HNO3" to 0)

    var showInfo by remember { mutableStateOf(false) }
    if (showInfo) {
        InfoDialog(
            title = "Oxidation State Finder",
            entries = listOf(
                "What is an oxidation state?" to "A number assigned to an atom representing its degree of oxidation. Positive = electrons lost, negative = electrons gained. Used to track electron transfer in redox reactions.",
                "Rules applied" to "F is always -1. Group 1 = +1, Group 2 = +2. Al, Ga, In, Sc, Y, La, Lu = +3. Zn, Cd = +2. Ag = +1. O defaults to -2 (except peroxides/superoxides). H defaults to +1 (except metal hydrides). Halogens (Cl, Br, I) default to -1 unless a more electronegative halogen is present.",
                "Halogen priority" to "Electronegativity order: F > Cl > Br > I. In interhalogen compounds, the less electronegative halogen takes a positive OS. Example: ICl3 → I=+3, Cl=-1. ClF3 → Cl=+3, F=-1.",
                "Special cases handled" to "Peroxides (O=-1): H2O2, Na2O2, BaO2. Superoxides (O=-½): KO2, NaO2. Ozonides (O=-⅓): KO3. Metal hydrides (H=-1): NaH, LiAlH4, NaBH4, CaH2.",
                "Overall charge" to "For neutral compounds enter 0. For polyatomic ions enter the ion charge. Examples: SO4²⁻ → charge -2. NH4⁺ → charge +1. MnO4⁻ → charge -1.",
                "Organic compounds" to "For single-carbon compounds (CH4, CO2, CCl4) the result is exact. For multi-carbon compounds, the app calculates an average oxidation state across all carbons, which is chemically meaningful for comparisons but does not reflect individual carbon environments. Ethanol (C2H5OH) has carbons at -3 and -1, but the app returns -2 as the average.",
                "Limitations" to "Compounds with 2 or more transition metals or unknown elements cannot be solved without additional information. Mixed-valence compounds like Fe3O4 (Fe²⁺ and Fe³⁺ coexist) return a fractional average with a warning. For these, enter the ion charge separately if known.",
                "Example" to "KMnO4 (charge 0): K=+1 (fixed), O=-2 (fixed, ×4). Mn = 0 − (+1) − 4(−2) = +7."
            ),
            onDismiss = { showInfo = false }
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Oxidation State Finder", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            IconButton(onClick = { showInfo = true }, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.onSurface.copy(0.35f), modifier = Modifier.size(16.dp))
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = formula,
                onValueChange = { formula = it; result = null },
                label = { Text("Formula") },
                placeholder = { Text("e.g. KMnO4") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                singleLine = true,
                visualTransformation = FormulaSubscriptTransformation,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )
            OutlinedTextField(
                value = chargeInput,
                onValueChange = { chargeInput = it; result = null },
                label = { Text("Charge") },
                placeholder = { Text("0") },
                modifier = Modifier.width(90.dp),
                shape = RoundedCornerShape(14.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    focusManager.clearFocus()
                    result = findOxidationStates(formula, chargeInput.toIntOrNull() ?: 0)
                })
            )
        }

        Button(
            onClick = {
                focusManager.clearFocus()
                result = findOxidationStates(formula, chargeInput.toIntOrNull() ?: 0)
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            enabled = formula.isNotBlank()
        ) {
            Text("Find Oxidation States")
        }

        result?.let { res ->
            if (res.error != null) {
                Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(0.4f))) {
                    Text(res.error, modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            } else {
                Card(shape = RoundedCornerShape(16.dp)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("OXIDATION STATES", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.45f))
                            Text(
                                toSubscriptFormula(formula) + if ((chargeInput.toIntOrNull() ?: 0) != 0) " (${if ((chargeInput.toIntOrNull() ?: 0) > 0) "+${chargeInput}" else chargeInput})" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                            )
                        }

                        res.states.forEach { (el, os) ->
                            val osColor = when {
                                os == "?" -> MaterialTheme.colorScheme.onSurface.copy(0.4f)
                                os.startsWith("+") && os != "+0" -> Color(0xFF3B82F6)
                                os.startsWith("-") -> Color(0xFFEF4444)
                                else -> MaterialTheme.colorScheme.onSurface.copy(0.6f)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier.size(36.dp).background(osColor.copy(0.1f), RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(el, fontWeight = FontWeight.Bold, color = osColor, style = MaterialTheme.typography.bodyMedium)
                                }
                                Text(el, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                Surface(shape = RoundedCornerShape(8.dp), color = osColor.copy(0.1f), border = BorderStroke(1.dp, osColor.copy(0.4f))) {
                                    Text(
                                        os,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = osColor
                                    )
                                }
                            }
                        }

                        if (res.note != null) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.15f))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                                Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary.copy(0.6f), modifier = Modifier.size(14.dp))
                                Text(res.note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(0.6f), lineHeight = 17.sp)
                            }
                        }
                    }
                }
            }
        }

        Text("EXAMPLES", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("KMnO4" to 0, "H2SO4" to 0, "Fe2O3" to 0, "Cr2O7" to -2, "NH4" to 1, "HNO3" to 0).forEach { (f, c) ->
                FilterChip(
                    selected = formula == f && (chargeInput.toIntOrNull() ?: 0) == c,
                    onClick = { formula = f; chargeInput = c.toString(); focusManager.clearFocus(); result = findOxidationStates(f, c) },
                    label = { Text(toSubscriptFormula(f) + if (c != 0) " (${if (c > 0) "+$c" else "$c"})" else "", style = MaterialTheme.typography.labelMedium) }
                )
            }
        }

    }
}

// TOOL 4 : SMILES VISUALIZER
@Composable
fun SmilesVisualizer(isDark: Boolean) {
    var input by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var cidResult by remember { mutableStateOf<Long?>(null) }
    var compoundName by remember { mutableStateOf<String?>(null) }
    var sdfData by remember { mutableStateOf<String?>(null) }
    var activeTab by remember { mutableStateOf(0) } // 0=2D, 1=3D
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    val examples = listOf(
        "Aspirin"   to "CC(=O)Oc1ccccc1C(=O)O",
        "Caffeine"  to "CN1C=NC2=C1C(=O)N(C(=O)N2C)C",
        "Glucose"   to "C([C@@H]1[C@H]([C@@H]([C@H](C(O1)O)O)O)O)O",
        "Ethanol"   to "CCO",
        "Benzene"   to "c1ccccc1"
    )

    var showInfo by remember { mutableStateOf(false) }
    if (showInfo) {
        InfoDialog(
            title = "SMILES Visualizer",
            entries = listOf(
                "What is SMILES?" to "Simplified Molecular Input Line Entry System. A notation that encodes molecular structure as a text string using atom symbols and bond characters.",
                "How to use" to "Paste any valid SMILES string and tap Visualize. The app looks up the compound on PubChem and shows its 2D structure and 3D model.",
                "Where to get SMILES" to "PubChem, ChemDraw, SciFinder, and most chemistry databases provide SMILES strings for compounds. You can also find them in published papers.",
                "Aromatic notation" to "Lowercase letters (c, n, o) denote aromatic atoms. For example, benzene is 'c1ccccc1' and pyridine is 'c1ccncc1'.",
                "Chirality" to "@  and @@ in SMILES denote stereocenters. The visualizer handles both standard and isomeric SMILES.",
                "Limitations" to "Only SMILES strings recognized by PubChem can be visualized. Novel or hypothetical molecules not in PubChem will return no result."
            ),
            onDismiss = { showInfo = false }
        )
    }

    fun visualize() {
        val smiles = input.trim()
        if (smiles.isBlank()) return
        focusManager.clearFocus()
        scope.launch {
            isLoading = true
            error = null
            cidResult = null
            compoundName = null
            sdfData = null
            try {
                val body = okhttp3.FormBody.Builder().add("smiles", smiles).build()
                val request = okhttp3.Request.Builder()
                    .url("https://pubchem.ncbi.nlm.nih.gov/rest/pug/compound/smiles/cids/JSON")
                    .post(body)
                    .build()
                val response = withContext(Dispatchers.IO) {
                    com.furthersecrets.chemsearch.data.ApiClient.rawHttp.newCall(request).execute()
                }
                val bodyStr = response.body?.string()
                if (!response.isSuccessful || bodyStr == null) {
                    error = "Compound not found on PubChem. Check your SMILES string."
                    return@launch
                }
                val json = com.google.gson.Gson().fromJson(bodyStr, com.google.gson.JsonObject::class.java)
                val cidElement = json
                    ?.getAsJsonObject("IdentifierList")
                    ?.getAsJsonArray("CID")
                    ?.firstOrNull()
                if (cidElement == null) {
                    error = "No compound found for this SMILES string."
                    return@launch
                }
                val cid = cidElement.asJsonPrimitive.asLong
                cidResult = cid
                val syns = withContext(Dispatchers.IO) {
                    runCatching { com.furthersecrets.chemsearch.data.ApiClient.pubChem.getSynonyms(cid) }.getOrNull()
                }
                compoundName = syns?.informationList?.information?.firstOrNull()?.synonym?.firstOrNull()
                val sdf = withContext(Dispatchers.IO) {
                    runCatching { com.furthersecrets.chemsearch.data.ApiClient.pubChem.getSdf(cid).string() }.getOrNull()
                }
                sdfData = sdf
            } catch (e: Exception) {
                error = "Error: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("SMILES Visualizer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            IconButton(onClick = { showInfo = true }, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.onSurface.copy(0.35f), modifier = Modifier.size(16.dp))
            }
        }

        OutlinedTextField(
            value = input,
            onValueChange = { input = it; cidResult = null; error = null },
            label = { Text("SMILES String") },
            placeholder = { Text("e.g. CCO") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { visualize() }),
            trailingIcon = {
                if (input.isNotBlank()) {
                    IconButton(onClick = { input = ""; cidResult = null; error = null }) {
                        Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp))
                    }
                }
            }
        )

        Text("EXAMPLES", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            examples.forEach { (name, smiles) ->
                FilterChip(
                    selected = input == smiles,
                    onClick = { input = smiles; cidResult = null; error = null },
                    label = { Text(name, style = MaterialTheme.typography.labelMedium) }
                )
            }
        }

        Button(
            onClick = { visualize() },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            enabled = input.isNotBlank() && !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                Spacer(Modifier.width(8.dp))
            }
            Text(if (isLoading) "Looking up..." else "Visualize")
        }

        if (error != null) {
            Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(0.4f))) {
                Text(error!!, modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }

        if (cidResult != null) {
            Card(shape = RoundedCornerShape(14.dp)) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.primary.copy(0.1f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Science, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(compoundName?.replaceFirstChar { it.uppercase() } ?: "CID $cidResult", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text("PubChem CID: $cidResult", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(0.5f), fontFamily = FontFamily.Monospace)
                    }
                }
            }

            Card(shape = RoundedCornerShape(16.dp)) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.outline.copy(0.12f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(4.dp)) {
                                listOf("2D Structure", "3D Model").forEachIndexed { idx, label ->
                                    Surface(
                                        onClick = { activeTab = idx },
                                        shape = RoundedCornerShape(50),
                                        color = if (activeTab == idx) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            label,
                                            modifier = Modifier.padding(vertical = 8.dp),
                                            color = if (activeTab == idx) Color.White else MaterialTheme.colorScheme.onSurface.copy(0.55f),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Box(
                        modifier = Modifier.fillMaxWidth().height(300.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        when (activeTab) {
                            0 -> AsyncImage(
                                model = "https://pubchem.ncbi.nlm.nih.gov/rest/pug/compound/cid/$cidResult/PNG?image_size=large",
                                contentDescription = "2D Structure",
                                modifier = Modifier.fillMaxSize().padding(12.dp),
                                contentScale = ContentScale.Fit
                            )
                            1 -> if (sdfData != null) {
                                Viewer3D(cid = cidResult ?: -1L, sdfData = sdfData!!, isDark = isDark)
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.VisibilityOff, null, tint = MaterialTheme.colorScheme.onSurface.copy(0.3f), modifier = Modifier.size(28.dp))
                                    Text("3D model not available", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
                                }
                            }
                        }
                    }
                }
            }
        }

    }
}

// TOOL 5 : REACTION BALANCER
private data class Frac(val num: Long, val den: Long) {
    companion object {
        fun of(n: Long) = Frac(n, 1L)
        fun zero() = Frac(0L, 1L)
        fun gcd(a: Long, b: Long): Long = if (b == 0L) a else gcd(b, a % b)
        fun lcm(a: Long, b: Long): Long = a / gcd(a, b) * b
    }
    fun isZero() = num == 0L
    operator fun unaryMinus() = Frac(-num, den)
    operator fun plus(o: Frac)  = Frac(num * o.den + o.num * den, den * o.den).r()
    operator fun minus(o: Frac) = Frac(num * o.den - o.num * den, den * o.den).r()
    operator fun times(o: Frac) = Frac(num * o.num, den * o.den).r()
    operator fun div(o: Frac): Frac {
        if (o.num == 0L) throw ArithmeticException("div by zero")
        return Frac(num * o.den, den * o.num).r()
    }
    private fun r(): Frac {
        if (num == 0L) return Frac(0L, 1L)
        val g = gcd(Math.abs(num), Math.abs(den))
        return if (den < 0) Frac(-num / g, -den / g) else Frac(num / g, den / g)
    }
}

private data class BalancerResult(
    val reactants: List<Pair<String, Int>> = emptyList(),
    val products:  List<Pair<String, Int>> = emptyList(),
    val error: String? = null
)

private fun stripCoeff(s: String): String {
    var i = 0; while (i < s.length && s[i].isDigit()) i++; return s.substring(i)
}

private fun tryBalance(matrix: Array<Array<Frac>>, m: Int, n: Int, freeIdx: Int): List<Int>? {
    val nv = n - 1
    val colOrder = (0 until n).filter { it != freeIdx }
    val aug = Array(m) { row ->
        Array(nv + 1) { col ->
            if (col < nv) matrix[row][colOrder[col]] else -matrix[row][freeIdx]
        }
    }
    var pr = 0
    val pivotForCol = IntArray(nv) { -1 }
    for (col in 0 until nv) {
        var found = -1
        for (row in pr until m) { if (!aug[row][col].isZero()) { found = row; break } }
        if (found == -1) return null
        if (found != pr) { val t = aug[pr]; aug[pr] = aug[found]; aug[found] = t }
        val pv = aug[pr][col]
        if (pv.isZero()) return null
        for (k in 0..nv) aug[pr][k] = aug[pr][k] / pv
        for (row in 0 until m) {
            if (row != pr && !aug[row][col].isZero()) {
                val f = aug[row][col]
                for (k in 0..nv) aug[row][k] = aug[row][k] - f * aug[pr][k]
            }
        }
        pivotForCol[col] = pr; pr++
    }
    val solNonFree = List(nv) { col ->
        val r = pivotForCol[col]; if (r < 0) return null; aug[r][nv]
    }
    val full = MutableList(n) { Frac.zero() }
    full[freeIdx] = Frac.of(1)
    colOrder.forEachIndexed { i, oc -> full[oc] = solNonFree[i] }
    if (full.any { it.num < 0 }) return null
    val lcm = full.map { it.den }.fold(1L) { acc, d -> Frac.lcm(acc, d) }
    val scaled = full.map { (it * Frac.of(lcm)).num }
    if (scaled.any { it <= 0 }) return null
    val g = scaled.map { Math.abs(it) }.fold(0L) { acc, v -> Frac.gcd(acc, v) }
    if (g == 0L) return null
    return scaled.map { (it / g).toInt() }
}

private fun balanceReaction(equation: String): BalancerResult {
    val parts = equation.split(Regex("->|=>|→|⟶"))
    if (parts.size != 2) return BalancerResult(error = "Use '->' to separate reactants and products.\nExample: H2 + O2 -> H2O")
    val rStr = parts[0].split("+").map { it.trim() }.filter { it.isNotEmpty() }
    val pStr = parts[1].split("+").map { it.trim() }.filter { it.isNotEmpty() }
    if (rStr.isEmpty()) return BalancerResult(error = "No reactants found")
    if (pStr.isEmpty()) return BalancerResult(error = "No products found")
    val rFormulas = rStr.map { stripCoeff(it) }
    val pFormulas = pStr.map { stripCoeff(it) }
    val all = rFormulas + pFormulas
    val n = all.size
    val parsed = all.mapIndexed { i, f ->
        try { parseFormulaForCalc(f) } catch (e: Exception) {
            return BalancerResult(error = "Cannot parse: ${all[i]}")
        }
    }
    val elements = parsed.flatMap { it.keys }.distinct().sorted()
    val m = elements.size
    if (m == 0) return BalancerResult(error = "No elements found")
    val matrix = Array(m) { row ->
        val el = elements[row]
        Array(n) { col ->
            val cnt = parsed[col][el] ?: 0
            val sign = if (col >= rFormulas.size) -1 else 1
            Frac.of(cnt.toLong() * sign)
        }
    }
    for (freeIdx in n - 1 downTo 0) {
        val sol = tryBalance(matrix, m, n, freeIdx) ?: continue
        if (sol.all { it > 0 }) {
            return BalancerResult(
                reactants = rFormulas.mapIndexed { i, f -> f to sol[i] },
                products  = pFormulas.mapIndexed { i, f -> f to sol[rFormulas.size + i] }
            )
        }
    }
    return BalancerResult(error = "Could not balance this equation. Check that all elements appear on both sides and the equation is valid.")
}

private fun reactionToDisplay(raw: String): String =
    raw.replace("->", "→")
        .map { subscriptMap[it] ?: it }
        .joinToString("")

@Composable
fun ReactionBalancer() {
    var input by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<BalancerResult?>(null) }
    val focusManager = LocalFocusManager.current

    val examples = listOf(
        "H2 + O2 -> H2O",
        "Fe + O2 -> Fe2O3",
        "C3H8 + O2 -> CO2 + H2O",
        "Al + HCl -> AlCl3 + H2",
        "KMnO4 + HCl -> KCl + MnCl2 + H2O + Cl2"
    )

    var showInfo by remember { mutableStateOf(false) }
    if (showInfo) {
        InfoDialog(
            title = "Reaction Balancer",
            entries = listOf(
                "How to enter equations" to "Type reactants on the left and products on the right, separated by '->'. Separate compounds with '+'. Example: H2 + O2 -> H2O",
                "Coefficients" to "Do not enter coefficients. The app determines them. H2 + O2 -> H2O, not 2H2 + O2 -> 2H2O.",
                "How it works" to "The balancer builds a matrix of element counts and solves the system using Gaussian elimination with exact rational arithmetic to find integer coefficients.",
                "Supported formulas" to "Standard molecular formulas with parentheses are supported, e.g. Ca(OH)2, Al2(SO4)3.",
                "Limitations" to "Equations that cannot be balanced by integer stoichiometry (e.g. some redox reactions requiring half-reaction method) may not solve correctly.",
                "Verification" to "The element count table below the result shows that atoms are conserved. You can verify the balancing manually."
            ),
            onDismiss = { showInfo = false }
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Reaction Balancer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            IconButton(onClick = { showInfo = true }, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.onSurface.copy(0.35f), modifier = Modifier.size(16.dp))
            }
        }

        OutlinedTextField(
            value = input,
            onValueChange = { new ->
                input = new.replace("->", "→")
                result = null
            },
            label = { Text("Chemical Equation") },
            placeholder = { Text("H2 + O2 -> H2O") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            singleLine = true,
            visualTransformation = FormulaSubscriptTransformation,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                focusManager.clearFocus()
                result = balanceReaction(input.replace("→", "->"))
            }),
            trailingIcon = {
                if (input.isNotBlank()) {
                    IconButton(onClick = { input = ""; result = null }) {
                        Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp))
                    }
                }
            }
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Insert:",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(0.45f)
            )
            listOf("+" to " + ", "→" to " → ", "(" to "(", ")" to ")").forEach { (label, insert) ->
                Surface(
                    onClick = { input += insert; result = null },
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(0.3f))
                ) {
                    Text(
                        label,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Surface(
                onClick = { if (input.isNotEmpty()) { input = input.dropLast(1); result = null } },
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(0.3f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(0.2f))
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = "Backspace",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp).size(14.dp),
                    tint = MaterialTheme.colorScheme.error.copy(0.7f)
                )
            }
        }

        Button(
            onClick = { focusManager.clearFocus(); result = balanceReaction(input.replace("→", "->")) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            enabled = input.isNotBlank()
        ) {
            Icon(Icons.Default.SwapHoriz, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Balance")
        }

        result?.let { res ->
            if (res.error != null) {
                Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(0.4f))) {
                    Text(res.error, modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            } else {
                Card(shape = RoundedCornerShape(16.dp)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {

                        Text("BALANCED EQUATION", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.45f))

                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            res.reactants.forEachIndexed { i, (formula, coeff) ->
                                if (i > 0) {
                                    Text("+", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(0.5f), modifier = Modifier.align(Alignment.CenterVertically))
                                }
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(0.08f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(0.25f))
                                ) {
                                    Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        if (coeff > 1) {
                                            Text("$coeff", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                                        }
                                        Text(toSubscriptFormula(formula), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
                                    }
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.outline.copy(0.1f),
                                modifier = Modifier.align(Alignment.CenterVertically)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurface.copy(0.6f), modifier = Modifier.padding(6.dp).size(16.dp))
                            }

                            res.products.forEachIndexed { i, (formula, coeff) ->
                                if (i > 0) {
                                    Text("+", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(0.5f), modifier = Modifier.align(Alignment.CenterVertically))
                                }
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.secondary.copy(0.08f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(0.25f))
                                ) {
                                    Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        if (coeff > 1) {
                                            Text("$coeff", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.secondary)
                                        }
                                        Text(toSubscriptFormula(formula), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.15f))

                        Text("ATOM COUNT VERIFICATION", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.45f))

                        val allElements = (res.reactants + res.products).flatMap { (f, _) ->
                            try { parseFormulaForCalc(f).keys } catch (e: Exception) { emptySet() }
                        }.distinct().sorted()

                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text("Element", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
                            Text("Reactants", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary.copy(0.7f), textAlign = TextAlign.Center)
                            Text("Products", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary.copy(0.7f), textAlign = TextAlign.Center)
                            Text("✓", modifier = Modifier.width(24.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(0.4f), textAlign = TextAlign.Center)
                        }

                        allElements.forEach { el ->
                            val rCount = res.reactants.sumOf { (f, c) ->
                                try { (parseFormulaForCalc(f)[el] ?: 0) * c } catch (e: Exception) { 0 }
                            }
                            val pCount = res.products.sumOf { (f, c) ->
                                try { (parseFormulaForCalc(f)[el] ?: 0) * c } catch (e: Exception) { 0 }
                            }
                            val balanced = rCount == pCount
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box(
                                        modifier = Modifier.size(22.dp).background(MaterialTheme.colorScheme.primary.copy(0.1f), RoundedCornerShape(5.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(el, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 10.sp)
                                    }
                                }
                                Text("$rCount", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center)
                                Text("$pCount", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center)
                                Icon(
                                    if (balanced) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                    null,
                                    tint = if (balanced) Color(0xFF22C55E) else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.width(24.dp).size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Text("EXAMPLES", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            examples.forEach { ex ->
                Surface(
                    onClick = { input = ex.replace("->", "→"); result = null },
                    shape = RoundedCornerShape(10.dp),
                    color = if (input.replace("→", "->") == ex) MaterialTheme.colorScheme.primary.copy(0.1f) else MaterialTheme.colorScheme.surfaceVariant.copy(0.5f),
                    border = if (input.replace("→", "->") == ex) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(0.4f)) else null
                ) {
                    Text(
                        reactionToDisplay(ex),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = if (input.replace("→", "->") == ex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(0.7f)
                    )
                }
            }
        }
    }
}

// TOOL 6 : Isomer Finder

@Composable
fun IsomerFinderTool(onNavigateToSearch: () -> Unit = {}) {
    val vm: com.furthersecrets.chemsearch.ChemViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel()
    val state by vm.uiState.collectAsState()
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    var showInfo by remember { mutableStateOf(false) }
    if (showInfo) {
        InfoDialog(
            title = "Isomer Finder",
            entries = listOf(
                "How to use" to "Enter any molecular formula (e.g. C₆H₆ or C₆H₁₂O₆). The app instantly shows up to 20 known isomers with the exact same formula.",
                "What are isomers?" to "Isomers are different compounds that have the same molecular formula but different atom connectivity or 3D arrangement.",
                "How it works" to "The app queries PubChem’s official API in real-time.",
                "Supported formulas" to "Standard molecular formulas with parentheses are fully supported, e.g. Ca(OH)₂, C₆H₅OH, Al₂(SO₄)₃, or complex ones like C₁₇H₃₅COOH.",
                "What you get" to "A list of all matching PubChem compounds with names, 2D structures, CIDs, and quick links to full details.",
                "Limitations" to "Only experimentally known compounds from PubChem are shown. Very rare or brand-new compounds may not appear yet."
            ),
            onDismiss = { showInfo = false }
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Isomer Finder", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                IconButton(onClick = { showInfo = true }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.onSurface.copy(0.35f), modifier = Modifier.size(16.dp))
                }
            }
            Text(
                "Enter a molecular formula to find up to 20 structural isomers from PubChem. " +
                        "Tap any result to load it in the Search tab.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }

        IsomerSearchBar(
            query = state.isomerQuery,
            onQueryChange = { vm.onIsomerQueryChange(it) },
            onSearch = {
                focusManager.clearFocus()
                vm.searchIsomers()
            },
            onClear = { vm.onIsomerQueryChange("") }
        )

        if (state.isLoadingIsomers) {
            IsomerLoadingState()
        }

        state.isomerError?.let { IsomerErrorState(it) }

        if (state.isomers.isNotEmpty()) {
            IsomerResultsHeader(
                formula = state.isomerQuery.trim(),
                count = state.isomers.size
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.isomers.forEach { isomer ->
                    IsomerCard(
                        isomer = isomer,
                        onClick = {
                            focusManager.clearFocus()
                            onNavigateToSearch()
                            vm.searchByCid(isomer.cid)
                        }
                    )
                }
            }
        }
    }
}

package com.furthersecrets.chemsearch.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.furthersecrets.chemsearch.ChemViewModel
import com.furthersecrets.chemsearch.data.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.furthersecrets.chemsearch.R

// ─── Root ──────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: ChemViewModel = viewModel()) {
    val state by vm.uiState.collectAsState()
    val query by vm.query.collectAsState()
    val isDark by vm.isDarkTheme.collectAsState()
    val autoSuggest by vm.autoSuggest.collectAsState()
    val defaultDescSource by vm.defaultDescSource.collectAsState()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val snackbar = remember { SnackbarHostState() }

    var showApiKeyDialog by remember { mutableStateOf(false) }
    var showSuggestions by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it); vm.clearError() }
    }
    LaunchedEffect(state.suggestions) {
        showSuggestions = state.suggestions.isNotEmpty()
    }

    if (showApiKeyDialog) {
        ApiKeyDialog(
            current = vm.getGeminiKey() ?: "",
            onSave = { key -> vm.saveGeminiKey(key); showApiKeyDialog = false },
            onDismiss = { showApiKeyDialog = false }
        )
    }

    if (showSettings) {
        SettingsSheet(
            isDark = isDark,
            autoSuggest = autoSuggest,
            defaultDescSource = defaultDescSource,
            hasGeminiKey = vm.getGeminiKey() != null,
            onToggleTheme = { vm.toggleTheme() },
            onToggleAutoSuggest = { vm.toggleAutoSuggest() },
            onSetDefaultDesc = { vm.setDefaultDescSource(it) },
            onSetApiKey = { showApiKeyDialog = true; showSettings = false },
            onClearApiKey = { vm.clearGeminiKey() },
            onClearHistory = { vm.clearHistory() },
            onDismiss = { showSettings = false }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { showSuggestions = false; focusManager.clearFocus() },
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                AppHeader(
                    isDark = isDark,
                    onToggleTheme = { vm.toggleTheme() },
                    onOpenSettings = { showSettings = true }
                )
            }

            item {
                Box {
                    SearchBar(
                        query = query,
                        onQueryChange = {
                            vm.onQueryChange(it)
                            if (it.isNotEmpty() && autoSuggest) showSuggestions = true
                        },
                        onSearch = { showSuggestions = false; focusManager.clearFocus(); vm.search() },
                        onClear = { vm.onQueryChange(""); showSuggestions = false }
                    )
                    AnimatedVisibility(
                        visible = showSuggestions && state.suggestions.isNotEmpty(),
                        modifier = Modifier.padding(top = 64.dp).zIndex(10f),
                        enter = fadeIn(), exit = fadeOut()
                    ) {
                        SuggestionsDropdown(
                            suggestions = state.suggestions,
                            onSelect = {
                                vm.onQueryChange(it)
                                showSuggestions = false
                                focusManager.clearFocus()
                                vm.search(it)
                            }
                        )
                    }
                }
            }

            if (state.isLoading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
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
                item { CompoundHeader(state) }
                item { StructureViewer(state, vm) }
                item { QuickInfoSection(state, context) }
                item { IdentifiersSection(state, context) }
                if (state.elementalData.isNotEmpty()) item { ElementalSection(state.elementalData) }
                if (state.synonyms.isNotEmpty()) item { SynonymsSection(state.synonyms) }
                item {
                    DescriptionSection(
                        state = state,
                        onPubChem = { vm.setDescSource(DescSource.PUBCHEM) },
                        onWiki = { vm.setDescSource(DescSource.WIKI) },
                        onAI = {
                            if (vm.getGeminiKey().isNullOrBlank()) showApiKeyDialog = true
                            else vm.setDescSource(DescSource.AI)
                        },
                        onRegenerate = { vm.fetchAiDescription() }
                    )
                }
            }
        }
    }
}

// ─── App header ────────────────────────────────────────────────────────────────

@Composable
fun AppHeader(isDark: Boolean, onToggleTheme: () -> Unit, onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(
                painter = painterResource(id = R.drawable.chemsearch),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(50.dp)
            )
            Column {
                Text("ChemSearch", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                Text(
                    "POWERED BY PUBCHEM",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Row {
            IconButton(onClick = onToggleTheme) {
                Icon(
                    imageVector = if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                    contentDescription = "Toggle theme",
                    tint = MaterialTheme.colorScheme.onSurface.copy(0.6f)
                )
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onSurface.copy(0.6f))
            }
        }
    }
}

// ─── Settings bottom sheet ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    isDark: Boolean,
    autoSuggest: Boolean,
    defaultDescSource: DescSource,
    hasGeminiKey: Boolean,
    onToggleTheme: () -> Unit,
    onToggleAutoSuggest: () -> Unit,
    onSetDefaultDesc: (DescSource) -> Unit,
    onSetApiKey: () -> Unit,
    onClearApiKey: () -> Unit,
    onClearHistory: () -> Unit,
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
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))

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
            Text("Automatically shown when you search a compound", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(0.5f), modifier = Modifier.padding(bottom = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(DescSource.PUBCHEM to "PubChem", DescSource.WIKI to "Wikipedia", DescSource.AI to "AI").forEach { (src, label) ->
                    SourceBtn(label = label, active = defaultDescSource == src) { onSetDefaultDesc(src) }
                }
            }

            Spacer(Modifier.height(4.dp))
            SettingsSectionHeader("AI (Gemini)")
            if (hasGeminiKey) {
                SettingsActionRow(Icons.Default.Key, "API Key saved", "Tap to replace", "Replace", MaterialTheme.colorScheme.primary, onSetApiKey)
                SettingsActionRow(Icons.Default.DeleteOutline, "Remove API Key", "Disables AI descriptions", "Remove", MaterialTheme.colorScheme.error, onClearApiKey)
            } else {
                SettingsActionRow(Icons.Default.Key, "No API Key set", "Required for AI descriptions", "Add Key", MaterialTheme.colorScheme.primary, onSetApiKey)
            }

            Spacer(Modifier.height(4.dp))
            SettingsSectionHeader("Data")
            SettingsActionRow(Icons.Default.History, "Search History", "Clear all recent searches", "Clear", MaterialTheme.colorScheme.error, onClearHistory)

            Spacer(Modifier.height(4.dp))
            SettingsSectionHeader("About")
            Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("ChemSearch for Android", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                    Text("Data from PubChem, Wikipedia and Google Gemini", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(0.55f))
                    Text("by FurtherSecrets", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
fun SettingsSectionHeader(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
}

@Composable
fun SettingsToggleRow(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onToggle: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
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
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurface.copy(0.5f), modifier = Modifier.size(20.dp))
            Column {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
            }
        }
        TextButton(onClick = onClick) { Text(actionLabel, color = actionColor, fontWeight = FontWeight.SemiBold) }
    }
}

// ─── Search bar ────────────────────────────────────────────────────────────────

@Composable
fun SearchBar(query: String, onQueryChange: (String) -> Unit, onSearch: () -> Unit, onClear: () -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Search compound ..") },
        leadingIcon = { Icon(Icons.Default.Search, null) },
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (query.isNotEmpty()) {
                    IconButton(onClick = onClear) { Icon(Icons.Default.Close, "Clear", tint = MaterialTheme.colorScheme.error) }
                }
                IconButton(onClick = onSearch) { Icon(Icons.Default.ArrowForward, "Search", tint = MaterialTheme.colorScheme.primary) }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        shape = RoundedCornerShape(16.dp),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline
        )
    )
}

// ─── Suggestions dropdown ──────────────────────────────────────────────────────

@Composable
fun SuggestionsDropdown(suggestions: List<String>, onSelect: (String) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column {
            suggestions.forEachIndexed { index, suggestion ->
                Row(modifier = Modifier.fillMaxWidth().clickable { onSelect(suggestion) }.padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Science, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Text(suggestion.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium)
                }
                if (index < suggestions.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.2f))
            }
        }
    }
}

// ─── History ───────────────────────────────────────────────────────────────────

@Composable
fun HistorySection(history: List<String>, onSelect: (String) -> Unit, onClear: () -> Unit) {
    if (history.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("⚗️", fontSize = 48.sp)
                Text("Search for any chemical compound", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
            }
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("RECENT SEARCHES", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
            TextButton(onClick = onClear) { Text("Clear all", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall) }
        }
        history.forEach { item ->
            Card(modifier = Modifier.fillMaxWidth().clickable { onSelect(item) }, shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.History, null, tint = MaterialTheme.colorScheme.onSurface.copy(0.35f), modifier = Modifier.size(18.dp))
                    Text(item.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

// ─── Compound header ───────────────────────────────────────────────────────────

@Composable
fun CompoundHeader(state: ChemUiState) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(state.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
            Text(toSubscriptFormula(state.formula), style = MaterialTheme.typography.titleMedium, color = Color.White.copy(0.85f), fontFamily = FontFamily.Monospace)
        }
    }
}

// ─── Quick Info ────────────────────────────────────────────────────────────────

@Composable
fun QuickInfoSection(state: ChemUiState, context: Context) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel("Basic Information")
            if (state.weight.isNotBlank()) IdentifierRow("Weight", "${state.weight} g/mol", context)
            state.cid?.let { IdentifierRow("CID", it.toString(), context) }
            state.casNumber?.let { IdentifierRow("CAS", it, context) }
            if (state.charge != 0) IdentifierRow("Formal Charge", state.charge.toString(), context)
        }
    }
}

// ─── Structure viewer ──────────────────────────────────────────────────────────

@Composable
fun StructureViewer(state: ChemUiState, vm: ChemViewModel) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column {
            TabRow(selectedTabIndex = if (state.activeTab == MolTab.TWO_D) 0 else 1, containerColor = MaterialTheme.colorScheme.surface) {
                Tab(selected = state.activeTab == MolTab.TWO_D, onClick = { vm.setTab(MolTab.TWO_D) }, text = { Text("2D Structure") })
                Tab(selected = state.activeTab == MolTab.THREE_D, onClick = { vm.setTab(MolTab.THREE_D) }, text = { Text("3D Model") })
            }
            Box(modifier = Modifier.fillMaxWidth().height(280.dp), contentAlignment = Alignment.Center) {
                when (state.activeTab) {
                    MolTab.TWO_D -> state.cid?.let { cid ->
                        AsyncImage(
                            model = "https://pubchem.ncbi.nlm.nih.gov/rest/pug/compound/cid/$cid/PNG?image_size=large",
                            contentDescription = "2D Structure",
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                    MolTab.THREE_D -> state.cid?.let { cid ->
                        Viewer3D(cid = cid)
                    } ?: Text("3D not available", color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
                }
            }
        }
    }
}

// ─── 3D Viewer ─────────────────────────────────────────────────────────────────

@Composable
fun Viewer3D(cid: Long) {
    val html = remember(cid) {
        """<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
<script src="https://cdnjs.cloudflare.com/ajax/libs/3Dmol/2.0.4/3Dmol-min.js"></script>
<style>
* { margin:0; padding:0; box-sizing:border-box; }
html, body { width:100%; height:100%; overflow:hidden; background:#1e293b; }
#viewer { width:100%; height:100%; position:relative; }
#msg { position:absolute; top:50%; left:50%; transform:translate(-50%,-50%);
       color:#94a3b8; font-family:sans-serif; font-size:13px; text-align:center; }
</style>
</head>
<body>
<div id="viewer"></div>
<div id="msg">Loading 3D model...</div>
<script>
(function() {
  var cid = $cid;
  var sdfUrl = 'https://pubchem.ncbi.nlm.nih.gov/rest/pug/compound/cid/' + cid + '/SDF?record_type=3d';

  fetch(sdfUrl)
    .then(function(r) {
      if (!r.ok) throw new Error('HTTP ' + r.status);
      return r.text();
    })
    .then(function(sdf) {
      document.getElementById('msg').style.display = 'none';
      var viewer = ${'$'}3Dmol.createViewer(document.getElementById('viewer'), {
        backgroundColor: '#1e293b',
        antialias: true
      });
      viewer.addModel(sdf, 'sdf');
      viewer.setStyle({}, {
        stick: { radius: 0.15, colorscheme: 'default' },
        sphere: { scale: 0.25 }
      });
      viewer.zoomTo();
      viewer.render();
      viewer.spin('y', 1);
    })
    .catch(function(err) {
      document.getElementById('msg').textContent = '3D structure not available for this compound.';
    });
})();
</script>
</body>
</html>"""
    }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    
    DisposableEffect(cid) {
        onDispose {
            webViewRef?.destroy()
        }
    }

    key(cid) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    webViewRef = this
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        @Suppress("DEPRECATION")
                        allowFileAccess = false
                        mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                    }
                    webViewClient = WebViewClient()
                    webChromeClient = android.webkit.WebChromeClient()
                    setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                    loadDataWithBaseURL(
                        "https://pubchem.ncbi.nlm.nih.gov",
                        html,
                        "text/html",
                        "UTF-8",
                        null
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

// ─── Identifiers ───────────────────────────────────────────────────────────────

@Composable
fun IdentifiersSection(state: ChemUiState, context: Context) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel("Identifiers")
            if (state.iupacName.isNotBlank()) IdentifierRow("IUPAC Name", state.iupacName, context, mono = false)
            if (state.connectivitySmiles.isNotBlank()) IdentifierRow("SMILES (Connectivity)", state.connectivitySmiles, context)
            if (state.smiles.isNotBlank() && state.smiles != state.connectivitySmiles) IdentifierRow("SMILES (Full)", state.smiles, context)
            if (state.inchiKey.isNotBlank()) IdentifierRow("InChIKey", state.inchiKey, context)
            if (state.inchi.isNotBlank()) IdentifierRow("InChI", state.inchi, context)
            if (state.empiricalFormula.isNotBlank() && state.empiricalFormula != state.formula) IdentifierRow("Empirical Formula", state.empiricalFormula, context)
        }
    }
}

@Composable
fun IdentifierRow(label: String, value: String, context: Context, mono: Boolean = true) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
        Spacer(Modifier.height(2.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                value,
                style = if (mono) MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace) else MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            IconButton(onClick = {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText(label, value))
            }, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Default.ContentCopy, "Copy", modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurface.copy(0.35f))
            }
        }
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.outline.copy(0.15f))
    }
}

// ─── Elemental analysis ────────────────────────────────────────────────────────

@Composable
fun ElementalSection(data: List<com.furthersecrets.chemsearch.data.ElementData>) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel("Elemental Analysis")
            data.forEach { el ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(el.element, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.width(26.dp))
                    Box(modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.outline.copy(0.15f))) {
                        Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(el.percentage / 100f).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.primary))
                    }
                    Text("${"%.1f".format(el.percentage)}%", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, modifier = Modifier.width(44.dp), color = MaterialTheme.colorScheme.onSurface.copy(0.55f))
                }
            }
        }
    }
}

// ─── Synonyms ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SynonymsSection(synonyms: List<String>) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel("Synonyms")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                synonyms.forEach { syn ->
                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primary.copy(0.08f), border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(0.2f))) {
                        Text(syn, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

// ─── Description ───────────────────────────────────────────────────────────────

@Composable
fun DescriptionSection(state: ChemUiState, onPubChem: () -> Unit, onWiki: () -> Unit, onAI: () -> Unit, onRegenerate: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionLabel("Description")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SourceBtn("PubChem", state.descSource == DescSource.PUBCHEM, onPubChem)
                SourceBtn("Wikipedia", state.descSource == DescSource.WIKI, onWiki)
                SourceBtn("AI ✨", state.descSource == DescSource.AI, onAI)
            }
            if (state.isLoadingDesc) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            } else {
                val text = when (state.descSource) {
                    DescSource.PUBCHEM -> state.pubDescription ?: "PubChem description not available."
                    DescSource.WIKI    -> state.wikiDescription ?: "Wikipedia description not available for this compound."
                    DescSource.AI      -> state.aiDescription   ?: "AI description not available. Check your API key in Settings."
                }
                Text(text, style = MaterialTheme.typography.bodyMedium, lineHeight = 22.sp)
                if (state.descSource == DescSource.AI && state.aiDescription != null) {
                    TextButton(onClick = onRegenerate, contentPadding = PaddingValues(0.dp)) {
                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Regenerate", style = MaterialTheme.typography.labelMedium)
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
        border = if (!active) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
    }
}

// ─── API Key dialog ─────────────────────────────────────────────────────────────

@Composable
fun ApiKeyDialog(current: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var key by remember { mutableStateOf(current) }
    var visible by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Gemini API Key", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Required for AI descriptions.", style = MaterialTheme.typography.bodySmall)
                Text("Get a free key at aistudio.google.com", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                OutlinedTextField(
                    value = key, onValueChange = { key = it }, label = { Text("API Key") }, singleLine = true, shape = RoundedCornerShape(12.dp),
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = { IconButton(onClick = { visible = !visible }) { Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility, null) } }
                )
                Text("Stored locally on your device only.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
            }
        },
        confirmButton = { Button(onClick = { if (key.isNotBlank()) onSave(key.trim()) }, shape = RoundedCornerShape(10.dp)) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ─── Utilities ──────────────────────────────────────────────────────────────────

@Composable
fun SectionLabel(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.45f))
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
package com.furthersecrets.chemsearch.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Feed
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.furthersecrets.chemsearch.BuildConfig
import com.furthersecrets.chemsearch.data.*

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

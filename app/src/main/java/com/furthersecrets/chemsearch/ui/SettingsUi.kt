package com.furthersecrets.chemsearch.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.height
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.format.DateUtils
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.furthersecrets.chemsearch.BuildConfig
import com.furthersecrets.chemsearch.data.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    isDark: Boolean,
    autoSuggest: Boolean,
    defaultDescSource: DescSource,
    aiProvider: AiProvider,
    hasGeminiKey: Boolean,
    hasGroqKey: Boolean,
    updateNotificationsEnabled: Boolean,
    updateStatus: UpdateStatus,
    onToggleTheme: () -> Unit,
    onToggleAutoSuggest: () -> Unit,
    onSetDefaultDesc: (DescSource) -> Unit,
    onSetAiProvider: (AiProvider) -> Unit,
    onSetGeminiKey: () -> Unit,
    onSetGroqKey: () -> Unit,
    onClearGeminiKey: () -> Unit,
    onClearGroqKey: () -> Unit,
    onClearHistory: () -> Unit,
    onToggleUpdateNotifications: (Boolean) -> Unit,
    onCheckForUpdates: () -> Unit,
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

            UpdatesSection(
                updateNotificationsEnabled = updateNotificationsEnabled,
                updateStatus = updateStatus,
                onToggleUpdateNotifications = onToggleUpdateNotifications,
                onCheckForUpdates = onCheckForUpdates
            )

            Spacer(Modifier.height(4.dp))
            SettingsSectionHeader("FAQ")
            var showFaqDialogSheet by remember { mutableStateOf(false) }
            if (showFaqDialogSheet) {
                InfoDialog(title = "FAQ", entries = FAQ_ENTRIES, onDismiss = { showFaqDialogSheet = false })
            }
            SettingsActionRow(
                icon = Icons.AutoMirrored.Filled.HelpOutline,
                title = "Frequently asked questions",
                subtitle = "Quick answers about ChemSearch",
                actionLabel = "Open",
                actionColor = MaterialTheme.colorScheme.primary,
                onClick = { showFaqDialogSheet = true }
            )

            Spacer(Modifier.height(4.dp))
            SettingsSectionHeader("About")
            AboutCard()
        }

    }
}

private fun buildSettingsBackupJson(prefs: android.content.SharedPreferences): String {
    val root = JSONObject()
    root.put("format", "chemsearch_settings")
    root.put("version", 1)
    root.put("exported_at", System.currentTimeMillis())
    val entries = JSONObject()

    prefs.all.toSortedMap().forEach { (key, value) ->
        val item = JSONObject()
        when (value) {
            is Boolean -> {
                item.put("type", "boolean")
                item.put("value", value)
            }
            is Int -> {
                item.put("type", "int")
                item.put("value", value)
            }
            is Long -> {
                item.put("type", "long")
                item.put("value", value)
            }
            is Float -> {
                item.put("type", "float")
                item.put("value", value)
            }
            is String -> {
                item.put("type", "string")
                item.put("value", value)
            }
            is Set<*> -> {
                item.put("type", "string_set")
                val arr = JSONArray()
                value.filterIsInstance<String>().forEach(arr::put)
                item.put("value", arr)
            }
            else -> return@forEach
        }
        entries.put(key, item)
    }

    root.put("entries", entries)
    return root.toString(2)
}

private fun restoreSettingsFromBackup(
    prefs: android.content.SharedPreferences,
    rawJson: String
): Int {
    val root = JSONObject(rawJson)
    val entries = root.optJSONObject("entries")
        ?: throw IllegalArgumentException("Invalid settings backup file.")
    val editor = prefs.edit().clear()
    var restored = 0

    val keys = entries.keys()
    while (keys.hasNext()) {
        val key = keys.next()
        val item = entries.optJSONObject(key) ?: continue
        when (item.optString("type")) {
            "boolean" -> editor.putBoolean(key, item.optBoolean("value"))
            "int" -> editor.putInt(key, item.optInt("value"))
            "long" -> editor.putLong(key, item.optLong("value"))
            "float" -> editor.putFloat(key, item.optDouble("value").toFloat())
            "string" -> editor.putString(key, item.optString("value"))
            "string_set" -> {
                val set = buildSet {
                    val arr = item.optJSONArray("value") ?: JSONArray()
                    for (idx in 0 until arr.length()) {
                        add(arr.optString(idx))
                    }
                }
                editor.putStringSet(key, set)
            }
            else -> continue
        }
        restored++
    }

    editor.apply()
    return restored
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

@Composable
private fun UpdatesSection(
    updateNotificationsEnabled: Boolean,
    updateStatus: UpdateStatus,
    onToggleUpdateNotifications: (Boolean) -> Unit,
    onCheckForUpdates: () -> Unit
) {
    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onToggleUpdateNotifications(true)
        } else {
            onToggleUpdateNotifications(false)
            Toast.makeText(context, "Notifications permission denied", Toast.LENGTH_SHORT).show()
        }
    }
    val requestUpdateNotifications = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            if (permission == PackageManager.PERMISSION_GRANTED) {
                onToggleUpdateNotifications(true)
            } else {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            onToggleUpdateNotifications(true)
        }
    }
    val lastCheckedLabel = updateStatus.lastCheckedAt?.let { lastChecked ->
        val relative = DateUtils.getRelativeTimeSpanString(
            lastChecked,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS
        )
        "Last checked $relative"
    } ?: "Never checked"
    val updateLink = updateStatus.downloadUrl ?: updateStatus.releaseUrl
    val checkLabel = if (updateStatus.isChecking) "Checking..." else "Check"

    Spacer(Modifier.height(4.dp))
    SettingsSectionHeader("Updates")
    SettingsToggleRow(
        icon = Icons.Default.Notifications,
        title = "Update notifications",
        subtitle = "Notify when a new version is available",
        checked = updateNotificationsEnabled,
        onToggle = {
            val next = !updateNotificationsEnabled
            if (next) requestUpdateNotifications() else onToggleUpdateNotifications(false)
        }
    )
    SettingsActionRow(
        icon = Icons.Default.SystemUpdate,
        title = "Check for updates",
        subtitle = lastCheckedLabel,
        actionLabel = checkLabel,
        actionColor = MaterialTheme.colorScheme.primary
    ) {
        if (!updateStatus.isChecking) onCheckForUpdates()
    }
    if (updateStatus.updateAvailable) {
        val latestLabel = updateStatus.latestVersion?.let { "Latest: $it" } ?: "Update available"
        SettingsActionRow(
            icon = Icons.Default.Download,
            title = "Update available",
            subtitle = latestLabel,
            actionLabel = "Download",
            actionColor = MaterialTheme.colorScheme.primary
        ) {
            if (updateLink.isNullOrBlank()) {
                Toast.makeText(context, "No download link found", Toast.LENGTH_SHORT).show()
            } else {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(updateLink))
                context.startActivity(intent)
            }
        }
    } else if (updateStatus.latestVersion != null && !updateStatus.isChecking) {
        Text(
            "You're up to date (latest ${updateStatus.latestVersion})",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(0.5f),
            modifier = Modifier.padding(start = 32.dp, top = 2.dp)
        )
    }
    updateStatus.error?.let { error ->
        Text(
            "Update check failed: $error",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(start = 32.dp, top = 2.dp)
        )
    }
}

@Composable
private fun AboutCard(onVersionTap: (() -> Unit)? = null) {
    val versionModifier = if (onVersionTap != null) {
        Modifier.clickable { onVersionTap() }
    } else {
        Modifier
    }
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("ChemSearch for Android", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primary.copy(0.12f),
                        modifier = versionModifier
                    ) {
                        Text(
                            text = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.secondary.copy(0.12f)
                    ) {
                        Text(
                            text = if (BuildConfig.DEBUG) "Debug" else "Release",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Text(
                    "Search compounds, view 2D/3D structures, and read safety data.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.55f)
                )
                Text("Built by FurtherSecrets", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                Text(
                    "Package: ${BuildConfig.APPLICATION_ID}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.15f))

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("LINKS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
                LinkRow(
                    icon = Icons.Default.Code,
                    title = "GitHub repository",
                    subtitle = "Source, docs, and releases",
                    url = "https://github.com/FurtherSecrets24680/chemsearch-android"
                )
                LinkRow(
                    icon = Icons.Default.SystemUpdate,
                    title = "Latest release",
                    subtitle = "Download the newest APK",
                    url = "https://github.com/FurtherSecrets24680/chemsearch-android/releases/latest"
                )
                LinkRow(
                    icon = Icons.Default.BugReport,
                    title = "Report an issue",
                    subtitle = "Bug reports and feature requests",
                    url = "https://github.com/FurtherSecrets24680/chemsearch-android/issues"
                )
                LinkRow(
                    icon = Icons.Default.Description,
                    title = "License",
                    subtitle = "View the open-source license",
                    url = "https://github.com/FurtherSecrets24680/chemsearch-android/blob/main/LICENSE"
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("DATA SOURCES", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
                listOf(
                    "PubChem" to "Structures and properties",
                    "Wikipedia" to "Descriptions",
                    "Google Gemini" to "AI summaries",
                    "Groq" to "AI summaries"
                ).forEach { (name, detail) ->
                    CreditRow(name = name, detail = detail)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("LIBRARIES", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
                listOf(
                    "Jetpack Compose" to "UI",
                    "Material 3" to "Design",
                    "Retrofit + OkHttp" to "Networking",
                    "Coil" to "Images",
                    "Gson" to "JSON",
                    "Coroutines" to "Async",
                    "Custom 3D renderer" to "SDF viewer"
                ).forEach { (name, detail) ->
                    CreditRow(name = name, detail = detail)
                }
            }
        }
    }
}

@Composable
private fun LinkRow(icon: ImageVector, title: String, subtitle: String, url: String) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Column {
            Text(title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
        }
    }
}

@Composable
private fun CreditRow(name: String, detail: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
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

private val FAQ_ENTRIES = listOf(
    "Why use this app instead of PubChem or ChemSpider?" to "It puts the same core data in a fast, mobile-friendly view and comes bundled with useful chemistry tools ( reaction balancing, molar mass, stoichiometry, 3D viewer etc.) so you can study without constantly changing sites.",
    "Where does compound data come from?" to "Most compound properties, structures, and safety data are pulled from PubChem. Descriptions can also come from Wikipedia or AI depending on your settings.",
    "What can I search for?" to "Search by common name, IUPAC name, CAS number, or CID. Suggestions use PubChem as you type.",
    "Why am I not getting results?" to "Check spelling, try a CID or CAS number, or remove extra spaces. Some compounds are not listed in PubChem.",
    "Do I need an API key for AI descriptions?" to "Yes. AI descriptions use Gemini or Groq. Add a key in Settings > API Keys. Keys are stored locally on your device.",
    "Where are my API keys stored?" to "Keys are stored locally in app preferences on your device and are not synced.",
    "Is the app offline?" to "Most features require internet access. Recently viewed compounds may load from cache, but live searches and AI always need a connection.",
    "How do autosuggestions work?" to "Autosuggestions query PubChem as you type. You can toggle them in Settings > Search.",
    "How do I save favorites?" to "Tap the bookmark icon on a compound to add it to Favorites. Favorites are stored locally.",
    "How do I clear history or cache?" to "Go to Settings > Data to clear search history or manage the compound cache.",
    "How do I download structures?" to "Use the download buttons in the Structure section to save a 2D PNG or 3D SDF file.",
    "Why is the 3D model missing?" to "Some compounds do not have a 3D SDF available in PubChem. In that case the 3D viewer shows a placeholder.",
    "How do I use the custom 3D viewer?" to "Open Tools > Custom 3D Molecule Viewer and load a .sdf or .mol file from your device.",
    "What does the SMILES visualizer do?" to "Paste a SMILES string to look it up on PubChem and view its 2D or 3D structure when available.",
    "How does the Reaction Balancer work?" to "It builds an element matrix and solves it with exact arithmetic. Very complex redox reactions or incorrect formulas may fail to balance.",
    "What does the Stoichiometry tool calculate?" to "It balances the reaction, finds the limiting reagent, and computes theoretical yield, excess reagents, and reaction scaling.",
    "How is molar mass calculated?" to "The calculator sums standard atomic weights for each element in the formula. Parentheses and hydrates are supported.",
    "Why does oxidation state show a fraction or question mark?" to "Some formulas need a charge to solve, and some have multiple unknown elements. The tool shows averages or unknowns in those cases.",
    "How does the Isomer Finder work?" to "It queries PubChem for matching formulas and returns up to 20 results.",
    "Are update notifications optional?" to "Yes. You can toggle update notifications in Settings. Manual update checks are also available.",
    "Is safety info official?" to "GHS data is aggregated from multiple sources in PubChem. It is for reference only and does not replace an official SDS.",
    "How do I unlock debug settings?" to "Tap the build number in the About card five times to reveal the developer tools."
)

// Favorites sheet

private enum class FavoritesSort { RECENT, NAME, ATOMS_DESC, ATOMS_ASC }

private fun countAtomsInFormula(formula: String): Int {
    val trimmed = formula.trim()
    if (trimmed.isBlank()) return 0
    val parts = trimmed.split('·', '.').filter { it.isNotBlank() }
    var total = 0
    for (part in parts) {
        val match = Regex("""^(\d+)(.*)$""").find(part)
        val multiplier = match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
        val fragment = match?.groupValues?.getOrNull(2)?.takeIf { it.isNotBlank() } ?: part
        total += multiplier * countAtomsInFragment(fragment)
    }
    return total
}

private fun countAtomsInFragment(formula: String): Int {
    val stack = ArrayDeque<MutableMap<String, Int>>().apply { addLast(mutableMapOf()) }
    var i = 0
    while (i < formula.length) {
        when {
            formula[i] == '(' -> {
                stack.addLast(mutableMapOf())
                i++
            }
            formula[i] == ')' -> {
                i++
                var num = ""
                while (i < formula.length && formula[i].isDigit()) {
                    num += formula[i++]
                }
                val mult = num.toIntOrNull() ?: 1
                val top = stack.removeLast()
                top.forEach { (el, cnt) ->
                    stack.last()[el] = (stack.last()[el] ?: 0) + cnt * mult
                }
            }
            formula[i].isUpperCase() -> {
                var el = formula[i].toString()
                i++
                while (i < formula.length && formula[i].isLowerCase()) {
                    el += formula[i++]
                }
                var num = ""
                while (i < formula.length && formula[i].isDigit()) {
                    num += formula[i++]
                }
                val cnt = num.toIntOrNull() ?: 1
                stack.last()[el] = (stack.last()[el] ?: 0) + cnt
            }
            else -> i++
        }
    }
    return stack.last().values.sum()
}

@Composable
private fun FavoriteCard(
    favorite: FavoriteCompound,
    onSelect: (String) -> Unit,
    onDelete: (Long) -> Unit,
    modifier: Modifier = Modifier,
    showImage: Boolean = true,
    enableSelect: Boolean = true,
    showReorderControls: Boolean = false,
    canMoveUp: Boolean = false,
    canMoveDown: Boolean = false,
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {}
) {
    Card(
        onClick = { if (enableSelect) onSelect(favorite.name) },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (showImage) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.size(64.dp)
                ) {
                    AsyncImage(
                        model = "https://pubchem.ncbi.nlm.nih.gov/rest/pug/compound/cid/${favorite.cid}/PNG?record_type=2d&image_size=small",
                        contentDescription = "Structure of ${favorite.name}",
                        modifier = Modifier.fillMaxSize().padding(4.dp),
                        contentScale = ContentScale.Fit
                    )
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(favorite.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                if (favorite.formula.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary.copy(0.08f)
                    ) {
                        Text(
                            toSubscriptFormula(favorite.formula),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Text(
                    "CID ${favorite.cid}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.4f)
                )
            }
            if (showReorderControls) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    IconButton(
                        onClick = onMoveUp,
                        enabled = canMoveUp,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowUp,
                            contentDescription = "Move up",
                            tint = MaterialTheme.colorScheme.onSurface.copy(if (canMoveUp) 0.6f else 0.25f)
                        )
                    }
                    IconButton(
                        onClick = onMoveDown,
                        enabled = canMoveDown,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = "Move down",
                            tint = MaterialTheme.colorScheme.onSurface.copy(if (canMoveDown) 0.6f else 0.25f)
                        )
                    }
                }
            }
            IconButton(onClick = { onDelete(favorite.cid) }) {
                Icon(Icons.Default.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error.copy(0.65f), modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun SortPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val background = if (selected) MaterialTheme.colorScheme.primary.copy(0.12f) else MaterialTheme.colorScheme.surfaceVariant
    val border = if (selected) MaterialTheme.colorScheme.primary.copy(0.35f) else MaterialTheme.colorScheme.outline.copy(0.2f)
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = background,
        border = BorderStroke(1.dp, border)
    ) {
        Box(
            modifier = Modifier
                .clickable { onClick() }
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(0.7f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesSheet(
    favorites: List<FavoriteCompound>,
    onSelect: (String) -> Unit,
    onDelete: (Long) -> Unit,
    onMoveFavorite: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var isReordering by remember { mutableStateOf(false) }
    LaunchedEffect(favorites.size) {
        if (favorites.size < 2 && isReordering) isReordering = false
    }
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
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Favorites", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.primary.copy(0.12f)
                    ) {
                        Text(
                            "${favorites.size} saved",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (favorites.size > 1) {
                        TextButton(
                            onClick = { isReordering = !isReordering },
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text(if (isReordering) "Done" else "Reorder")
                        }
                    }
                }
            }
            if (isReordering) {
                Text(
                    "Tap the arrows to move favorites.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.55f),
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
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
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    favorites.forEachIndexed { index, fav ->
                        FavoriteCard(
                            favorite = fav,
                            onSelect = onSelect,
                            onDelete = onDelete,
                            enableSelect = !isReordering,
                            showReorderControls = isReordering,
                            canMoveUp = index > 0,
                            canMoveDown = index < favorites.lastIndex,
                            onMoveUp = { if (index > 0) onMoveFavorite(index, index - 1) },
                            onMoveDown = { if (index < favorites.lastIndex) onMoveFavorite(index, index + 1) }
                        )
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
    onDelete: (Long) -> Unit,
    onMoveFavorite: (Int, Int) -> Unit
) {
    var filterQuery by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf(FavoritesSort.RECENT) }
    var isReordering by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(favorites.size) {
        if (favorites.size < 2 && isReordering) isReordering = false
    }
    val normalizedQuery = filterQuery.trim().lowercase(Locale.US)
    val filteredFavorites = remember(favorites, normalizedQuery, sortMode) {
        val base = if (normalizedQuery.isBlank()) {
            favorites
        } else {
            favorites.filter { fav ->
                fav.name.lowercase(Locale.US).contains(normalizedQuery) ||
                    fav.formula.lowercase(Locale.US).contains(normalizedQuery) ||
                    fav.iupacName.lowercase(Locale.US).contains(normalizedQuery) ||
                    fav.cid.toString().contains(normalizedQuery) ||
                    fav.molecularWeight.lowercase(Locale.US).contains(normalizedQuery)
            }
        }
        when (sortMode) {
            FavoritesSort.NAME -> base.sortedBy { it.name.lowercase(Locale.US) }
            FavoritesSort.ATOMS_DESC -> base.sortedWith(
                compareByDescending<FavoriteCompound> { countAtomsInFormula(it.formula) }
                    .thenBy { it.name.lowercase(Locale.US) }
            )
            FavoritesSort.ATOMS_ASC -> base.sortedWith(
                compareBy<FavoriteCompound> { countAtomsInFormula(it.formula) }
                    .thenBy { it.name.lowercase(Locale.US) }
            )
            FavoritesSort.RECENT -> base
        }
    }
    val showControls = favorites.size >= 2
    val displayFavorites = if (isReordering) favorites else filteredFavorites

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
                    Text("No favorites yet", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface.copy(0.7f))
                    Text("Tap the favorite icon on any compound", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(0.38f))
                }
            }
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Bookmark, null, tint = MaterialTheme.colorScheme.primary.copy(0.7f), modifier = Modifier.size(18.dp))
                Text("Favorites", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.primary.copy(0.12f)
                ) {
                    Text(
                        "${favorites.size} saved",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (favorites.size > 1) {
                    TextButton(
                        onClick = {
                            val next = !isReordering
                            isReordering = next
                            if (next) {
                                filterQuery = ""
                                sortMode = FavoritesSort.RECENT
                                focusManager.clearFocus()
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text(if (isReordering) "Done" else "Reorder")
                    }
                }
            }
        }
        Text(
            "Tap a card to open. Favorites are stored on this device only.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
        )
        if (isReordering) {
            Text(
                "Reorder mode: use the arrows to move items.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(0.55f)
            )
        }

        if (showControls && !isReordering) {
            OutlinedTextField(
                value = filterQuery,
                onValueChange = { filterQuery = it },
                label = { Text("Filter favorites") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (filterQuery.isNotBlank()) {
                        IconButton(onClick = { filterQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear filter")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Sort",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.45f)
                    )
                    if (filterQuery.isNotBlank()) {
                        Text(
                            "${filteredFavorites.size} match${if (filteredFavorites.size == 1) "" else "es"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.4f)
                        )
                    }
                }
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SortPill(label = "Recent", selected = sortMode == FavoritesSort.RECENT) {
                        sortMode = FavoritesSort.RECENT
                    }
                    SortPill(label = "A-Z", selected = sortMode == FavoritesSort.NAME) {
                        sortMode = FavoritesSort.NAME
                    }
                    SortPill(label = "Most atoms", selected = sortMode == FavoritesSort.ATOMS_DESC) {
                        sortMode = FavoritesSort.ATOMS_DESC
                    }
                    SortPill(label = "Least atoms", selected = sortMode == FavoritesSort.ATOMS_ASC) {
                        sortMode = FavoritesSort.ATOMS_ASC
                    }
                }
            }
        }

        if (displayFavorites.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "No matches found.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.6f)
                )
                Text(
                    "Try a different name, formula, or CID.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.45f)
                )
                if (filterQuery.isNotBlank()) {
                    TextButton(onClick = { filterQuery = "" }) { Text("Clear filter") }
                }
            }
        } else {
            displayFavorites.forEachIndexed { index, fav ->
                FavoriteCard(
                    favorite = fav,
                    onSelect = onSelect,
                    onDelete = onDelete,
                    enableSelect = !isReordering,
                    showReorderControls = isReordering,
                    canMoveUp = isReordering && index > 0,
                    canMoveDown = isReordering && index < displayFavorites.lastIndex,
                    onMoveUp = { if (isReordering && index > 0) onMoveFavorite(index, index - 1) },
                    onMoveDown = { if (isReordering && index < displayFavorites.lastIndex) onMoveFavorite(index, index + 1) }
                )
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
    updateNotificationsEnabled: Boolean = true,
    updateStatus: UpdateStatus = UpdateStatus(),
    onToggleTheme: () -> Unit,
    onToggleAutoSuggest: () -> Unit,
    onSetDefaultDesc: (DescSource) -> Unit,
    onSetAiProvider: (AiProvider) -> Unit,
    onSetGeminiKey: () -> Unit,
    onSetGroqKey: () -> Unit,
    onClearGeminiKey: () -> Unit,
    onClearGroqKey: () -> Unit,
    onClearHistory: () -> Unit,
    onToggleUpdateNotifications: (Boolean) -> Unit = {},
    onCheckForUpdates: () -> Unit = {},
    cacheSizeBytes: Long = 0L,
    cacheDir: String = "",
    onClearCache: () -> Unit = {},
    onSetCacheDir: (String) -> Unit = {},
    onTestUpdateNotification: () -> Unit = {},
    onSettingsImported: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("chemsearch_prefs", Context.MODE_PRIVATE) }
    var buildTapCount by remember { mutableIntStateOf(0) }
    var isDevMode by remember { mutableStateOf(prefs.getBoolean("dev_mode", false)) }
    var themeDropdownExpanded by remember { mutableStateOf(false) }
    var showFaqDialog by remember { mutableStateOf(false) }
    val exportSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val json = buildSettingsBackupJson(prefs)
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                writer.write(json)
            } ?: error("Unable to open file for export")
        }.onSuccess {
            Toast.makeText(context, "Settings exported", Toast.LENGTH_SHORT).show()
        }.onFailure { e ->
            Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    val importSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                reader.readText()
            } ?: error("Unable to open file for import")
            restoreSettingsFromBackup(prefs, raw)
        }.onSuccess { restoredCount ->
            onSettingsImported()
            Toast.makeText(context, "Imported $restoredCount settings", Toast.LENGTH_SHORT).show()
        }.onFailure { e ->
            Toast.makeText(context, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

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
        SettingsActionRow(
            icon = Icons.Default.Description,
            title = "Export settings",
            subtitle = "Save current app settings to a JSON file",
            actionLabel = "Export",
            actionColor = MaterialTheme.colorScheme.primary,
            onClick = {
                exportSettingsLauncher.launch("chemsearch-settings-${System.currentTimeMillis()}.json")
            }
        )
        SettingsActionRow(
            icon = Icons.Default.FolderOpen,
            title = "Import settings",
            subtitle = "Restore settings from a JSON backup",
            actionLabel = "Import",
            actionColor = MaterialTheme.colorScheme.primary,
            onClick = {
                importSettingsLauncher.launch(arrayOf("application/json", "text/plain"))
            }
        )

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

        UpdatesSection(
            updateNotificationsEnabled = updateNotificationsEnabled,
            updateStatus = updateStatus,
            onToggleUpdateNotifications = onToggleUpdateNotifications,
            onCheckForUpdates = onCheckForUpdates
        )

        Spacer(Modifier.height(4.dp))
        SettingsSectionHeader("FAQ")
        if (showFaqDialog) {
            InfoDialog(title = "FAQ", entries = FAQ_ENTRIES, onDismiss = { showFaqDialog = false })
        }
        SettingsActionRow(
            icon = Icons.AutoMirrored.Filled.HelpOutline,
            title = "Frequently asked questions",
            subtitle = "Quick answers about ChemSearch",
            actionLabel = "Open",
            actionColor = MaterialTheme.colorScheme.primary,
            onClick = { showFaqDialog = true }
        )

        if (isDevMode) {
            Spacer(Modifier.height(4.dp))
            DebugSettingsSection(
                prefs = prefs,
                onTestUpdateNotification = onTestUpdateNotification,
                onDisableDevMode = { persist ->
                    isDevMode = false
                    buildTapCount = 0
                    if (persist) {
                        prefs.edit().putBoolean("dev_mode", false).apply()
                    }
                }
            )
        }

        Spacer(Modifier.height(4.dp))
        SettingsSectionHeader("About")
        AboutCard(onVersionTap = {
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
        })

    }
}


// DEBUG SETTINGS
object DebugLog {
    private const val MAX = 200
    val lines = mutableStateListOf<String>()
    private val formatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS", Locale.US)
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile var verbose = false

    fun d(tag: String, msg: String) {
        if (!verbose) return
        Log.d(tag, msg)
        append("D/$tag: $msg")
    }
    fun e(tag: String, msg: String) {
        Log.e(tag, msg)
        append("E/$tag: $msg")
    }
    fun i(tag: String, msg: String) {
        if (!verbose) return
        Log.i(tag, msg)
        append("I/$tag: $msg")
    }
    private fun append(line: String) {
        val entry = "${LocalTime.now().format(formatter)}  $line"
        runOnMain {
            while (lines.size >= MAX) lines.removeAt(0)
            lines.add(entry)
        }
    }
    fun clear() = runOnMain { lines.clear() }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post { block() }
        }
    }
}

@Composable
fun DebugSettingsSection(
    prefs: android.content.SharedPreferences,
    onTestUpdateNotification: () -> Unit,
    onDisableDevMode: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var verboseLogging by remember { mutableStateOf(prefs.getBoolean("debug_verbose", false)) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showPrefsDialog by remember { mutableStateOf(false) }
    var showLogsDialog by remember { mutableStateOf(false) }
    var showMemoryDialog by remember { mutableStateOf(false) }
    var showCrashConfirm by remember { mutableStateOf(false) }
    var showWipeConfirm by remember { mutableStateOf(false) }
    val logLines = DebugLog.lines
    val sensitiveKeyTokens = listOf("key", "token", "secret")

    fun redactValue(key: String, value: Any?): String {
        val raw = value?.toString() ?: "null"
        val isSensitive = sensitiveKeyTokens.any { key.lowercase(Locale.US).contains(it) }
        return if (isSensitive && raw.length > 8) raw.take(4) + "••••" + raw.takeLast(4) else raw
    }

    LaunchedEffect(verboseLogging) {
        DebugLog.verbose = verboseLogging
    }

    if (showInfoDialog) {
        InfoDialog(
            title = "Debug Settings",
            entries = listOf(
                "Verbose logging" to "Enables debug/info logs (tagged 'ChemSearch') in Logcat and the in-app buffer. Errors are always captured. Disable to reduce noise.",
                "Live log viewer" to "Shows the in-app log buffer in real time (up to 200 lines). Verbose logs (D/) only appear when verbose logging is on. Errors (E/) are always captured. You can copy or clear the buffer.",
                "Inspect SharedPreferences" to "Dumps every key-value pair in the app's preference file. Keys like key/token/secret are masked; use Copy full if you need raw values.",
                "Memory info" to "Shows current heap usage from the JVM runtime and the Android ActivityManager. Useful for spotting memory leaks or unusually high allocations.",
                "API endpoints" to "Copies base URLs for PubChem, Wikipedia, Gemini, and Groq to your clipboard for manual testing.",
                "Wipe all SharedPreferences" to "Calls prefs.edit().clear(). Removes API keys, history, favorites, settings, and debug flags. You'll need to unlock debug settings again; restart recommended.",
                "Force crash" to "Deliberately throws an unhandled RuntimeException. Used to verify that crash reporting / Logcat is working correctly. There is a confirmation step before it fires.",
                "Hide debug settings" to "Sets dev_mode=false and hides this section. Tap the build number 5 times in the About card to unlock it again."
            ),
            onDismiss = { showInfoDialog = false }
        )
    }

    if (showPrefsDialog) {
        val prefEntries = prefs.all.entries.sortedBy { it.key }
        val hasSensitive = prefEntries.any { entry ->
            sensitiveKeyTokens.any { entry.key.lowercase(Locale.US).contains(it) }
        }
        AlertDialog(
            onDismissRequest = { showPrefsDialog = false },
            title = { Text("SharedPreferences dump", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (prefEntries.isEmpty()) {
                        Text("No keys stored.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                    } else {
                        if (hasSensitive) {
                            Text(
                                "Sensitive values are masked. Use \"Copy full\" to include raw values.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                            )
                        }
                        prefEntries.forEach { (k, v) ->
                            val display = redactValue(k, v)
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
                        val dump = prefEntries.joinToString("\n") { "${it.key} = ${redactValue(it.key, it.value)}" }
                        cm.setPrimaryClip(ClipData.newPlainText("prefs", dump))
                        Toast.makeText(context, "Copied masked prefs", Toast.LENGTH_SHORT).show()
                    }) { Text("Copy masked") }
                    TextButton(onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val dump = prefEntries.joinToString("\n") { "${it.key} = ${it.value}" }
                        cm.setPrimaryClip(ClipData.newPlainText("prefs", dump))
                        Toast.makeText(context, "Copied full prefs", Toast.LENGTH_SHORT).show()
                    }) { Text("Copy full") }
                }
            },
            dismissButton = { TextButton(onClick = { showPrefsDialog = false }) { Text("Close") } },
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
        val heapUsedMb = (rt.totalMemory() - rt.freeMemory()) / 1_048_576L
        val heapAllocatedMb = rt.totalMemory() / 1_048_576L
        val heapMaxMb = rt.maxMemory() / 1_048_576L
        val heapHeadroomMb = (heapMaxMb - heapUsedMb).coerceAtLeast(0)
        val heapPercent = if (heapMaxMb > 0) heapUsedMb.toFloat() / heapMaxMb else 0f
        val heapPercentLabel = if (heapMaxMb > 0) String.format(Locale.US, "%.0f%%", heapPercent * 100f) else "—"
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val mi = android.app.ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val availMb = mi.availMem / 1_048_576L
        val totalSystemMb = mi.totalMem / 1_048_576L
        val usedSystemMb = (totalSystemMb - availMb).coerceAtLeast(0)
        val systemPercent = if (totalSystemMb > 0) usedSystemMb.toFloat() / totalSystemMb else 0f
        val systemPercentLabel = if (totalSystemMb > 0) String.format(Locale.US, "%.0f%%", systemPercent * 100f) else "—"
        val heapColor = when {
            heapPercent >= 0.85f -> MaterialTheme.colorScheme.error
            heapPercent >= 0.7f -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.primary
        }
        val systemColor = when {
            systemPercent >= 0.85f -> MaterialTheme.colorScheme.error
            systemPercent >= 0.7f -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.primary
        }
        val trackColor = MaterialTheme.colorScheme.outline.copy(0.2f)
        val lowMemoryLabel = if (mi.lowMemory) "YES (low)" else "No"
        val lowMemoryColor = if (mi.lowMemory) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(0.7f)
        AlertDialog(
            onDismissRequest = { showMemoryDialog = false },
            title = { Text("Memory info", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    @Composable
                    fun UsageBar(percent: Float, color: Color) {
                        val clamped = percent.coerceIn(0f, 1f)
                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .background(trackColor, RoundedCornerShape(6.dp))
                        ) {
                            val barWidth = maxWidth * clamped
                            Box(
                                modifier = Modifier
                                    .width(barWidth)
                                    .fillMaxHeight()
                                    .background(color, RoundedCornerShape(6.dp))
                            )
                        }
                    }

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.6f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(0.15f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.Memory, null, tint = heapColor, modifier = Modifier.size(18.dp))
                                    Column {
                                        Text("JVM heap", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            "Used ${heapUsedMb} MB of ${heapMaxMb} MB max",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(0.6f)
                                        )
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = heapColor.copy(0.15f)
                                ) {
                                    Text(
                                        heapPercentLabel,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                        color = heapColor,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            UsageBar(heapPercent, heapColor)
                            listOf(
                                "Used" to "${heapUsedMb} MB",
                                "Allocated" to "${heapAllocatedMb} MB",
                                "Max" to "${heapMaxMb} MB",
                                "Headroom" to "${heapHeadroomMb} MB"
                            ).forEach { (k, v) ->
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(k, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                                    Text(v, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.6f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(0.15f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.Storage, null, tint = systemColor, modifier = Modifier.size(18.dp))
                                    Column {
                                        Text("System RAM", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            "Used ${usedSystemMb} MB of ${totalSystemMb} MB total",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(0.6f)
                                        )
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = systemColor.copy(0.15f)
                                ) {
                                    Text(
                                        systemPercentLabel,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                        color = systemColor,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            UsageBar(systemPercent, systemColor)
                            listOf(
                                "Used" to "${usedSystemMb} MB",
                                "Available" to "${availMb} MB",
                                "Total" to "${totalSystemMb} MB"
                            ).forEach { (k, v) ->
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(k, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                                    Text(v, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), fontWeight = FontWeight.SemiBold)
                                }
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Low memory", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                                Text(lowMemoryLabel, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), fontWeight = FontWeight.SemiBold, color = lowMemoryColor)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val snapshot = buildString {
                            append("JVM heap: used ${heapUsedMb} MB, allocated ${heapAllocatedMb} MB, max ${heapMaxMb} MB, headroom ${heapHeadroomMb} MB (${heapPercentLabel})\n")
                            append("System RAM: used ${usedSystemMb} MB, available ${availMb} MB, total ${totalSystemMb} MB (${systemPercentLabel}), low memory: $lowMemoryLabel")
                        }
                        cm.setPrimaryClip(ClipData.newPlainText("memory", snapshot))
                        Toast.makeText(context, "Copied memory snapshot", Toast.LENGTH_SHORT).show()
                    }) { Text("Copy") }
                    TextButton(onClick = { showMemoryDialog = false }) { Text("Close") }
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    if (showWipeConfirm) {
        AlertDialog(
            onDismissRequest = { showWipeConfirm = false },
            title = { Text("Wipe all preferences?", fontWeight = FontWeight.Bold) },
            text = { Text("This clears API keys, history, favorites, settings, and debug flags. You will need to unlock debug settings again.") },
            confirmButton = {
                Button(
                    onClick = {
                        showWipeConfirm = false
                        prefs.edit().clear().apply()
                        DebugLog.verbose = false
                        verboseLogging = false
                        DebugLog.e("ChemSearch", "SharedPreferences wiped by developer")
                        onDisableDevMode(false)
                        Toast.makeText(context, "All preferences wiped. Restart the app.", Toast.LENGTH_LONG).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Wipe now") }
            },
            dismissButton = { TextButton(onClick = { showWipeConfirm = false }) { Text("Cancel") } },
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

            // Update notification test
            SettingsActionRow(
                icon = Icons.Default.NotificationsActive,
                title = "Test update notification",
                subtitle = "Send a sample update notification now",
                actionLabel = "Send",
                actionColor = MaterialTheme.colorScheme.primary,
                onClick = {
                    val hasPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                    if (!hasPermission) {
                        Toast.makeText(context, "Grant notification permission first", Toast.LENGTH_SHORT).show()
                    } else {
                        onTestUpdateNotification()
                        Toast.makeText(context, "Test notification sent", Toast.LENGTH_SHORT).show()
                    }
                }
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
                subtitle = "Clears keys, history, API keys, settings, debug flags",
                actionLabel = "Wipe",
                actionColor = MaterialTheme.colorScheme.error,
                onClick = { showWipeConfirm = true }
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
                onClick = {
                    onDisableDevMode(true)
                    Toast.makeText(context, "Debug settings hidden", Toast.LENGTH_SHORT).show()
                },
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(Icons.Default.VisibilityOff, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurface.copy(0.4f))
                Spacer(Modifier.width(6.dp))
                Text("Hide debug settings", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
            }
        }
    }
}

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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.furthersecrets.chemsearch.BuildConfig
import com.furthersecrets.chemsearch.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SECRET_PREF_KEYS = AiProvider.entries.map { it.keyPref }.toSet()
private val SENSITIVE_PREF_TOKENS = listOf("key", "token", "secret")

internal fun isOledModeControlEnabled(isDark: Boolean): Boolean = isDark

internal fun amoledModeTitle(): String = "AMOLED Mode"

internal fun amoledModeSubtitle(isDark: Boolean): String =
    if (isDark) "True-black background for every color scheme" else "Turn on dark mode to use AMOLED Mode"

private fun updateDownloadPercent(progress: Float?): Int =
    ((progress ?: 0f).coerceIn(0f, 1f) * 100f).toInt().coerceIn(0, 100)

internal fun updateDownloadActionLabel(status: UpdateStatus): String =
    when {
        status.isDownloadingUpdate -> "${updateDownloadPercent(status.updateDownloadProgress)}%"
        status.downloadedUpdateApkPath != null -> "Install"
        else -> "Download"
    }

internal fun updateDownloadSubtitle(status: UpdateStatus): String {
    if (status.isDownloadingUpdate) {
        return "Downloading update… ${updateDownloadPercent(status.updateDownloadProgress)}%"
    }
    if (status.downloadedUpdateApkPath != null) {
        return "Download complete. Tap Install if the prompt closed."
    }
    return status.latestVersion?.let { "Latest: $it" } ?: "Update available"
}

private fun AppColorScheme.label(): String = when (this) {
    AppColorScheme.BLUE -> "Blue"
    AppColorScheme.VIOLET -> "Violet"
    AppColorScheme.EMERALD -> "Emerald"
    AppColorScheme.ROSE -> "Rose"
    AppColorScheme.AMBER -> "Amber"
}

private fun AppColorScheme.previewColor(): Color = when (this) {
    AppColorScheme.BLUE -> Color(0xFF2563EB)
    AppColorScheme.VIOLET -> Color(0xFF7C3AED)
    AppColorScheme.EMERALD -> Color(0xFF059669)
    AppColorScheme.ROSE -> Color(0xFFE11D48)
    AppColorScheme.AMBER -> Color(0xFFD97706)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorSchemePicker(
    colorScheme: AppColorScheme,
    onSetColorScheme: (AppColorScheme) -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AppColorScheme.entries.forEach { scheme ->
            val selected = colorScheme == scheme
            Surface(
                onClick = { onSetColorScheme(scheme) },
                shape = RoundedCornerShape(999.dp),
                color = if (selected) scheme.previewColor().copy(0.14f) else MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    1.dp,
                    if (selected) scheme.previewColor().copy(0.75f) else MaterialTheme.colorScheme.outline.copy(0.24f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(scheme.previewColor(), CircleShape)
                    )
                    Text(
                        scheme.label(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (selected) scheme.previewColor() else MaterialTheme.colorScheme.onSurface.copy(0.72f)
                    )
                    if (selected) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = scheme.previewColor(),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AiProviderSettings(
    aiProvider: AiProvider,
    aiKeyStatus: Map<AiProvider, Boolean>,
    aiModelCatalogs: Map<AiProvider, AiModelCatalog>,
    onSetAiProvider: (AiProvider) -> Unit,
    onSetAiModel: (AiProvider, String) -> Unit,
    onRefreshAiModels: (AiProvider) -> Unit,
    onEditAiKey: (AiProvider) -> Unit,
    onClearAiKey: (AiProvider) -> Unit
) {
    var providerExpanded by remember { mutableStateOf(false) }
    var modelExpanded by remember(aiProvider) { mutableStateOf(false) }
    val selectedHasKey = aiKeyStatus[aiProvider] == true
    val catalog = aiModelCatalogs[aiProvider] ?: AiModelCatalog(
        models = aiProvider.defaultModels,
        selectedModel = aiProvider.modelName
    )
    val selectedModel = catalog.selectedModel.ifBlank { aiProvider.modelName }
    val modelOptions = (listOf(selectedModel) + catalog.models + aiProvider.defaultModels).distinct()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "Provider",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(0.45f)
        )
        Box {
            Surface(
                onClick = { providerExpanded = true },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(0.28f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        if (selectedHasKey) Icons.Default.Check else Icons.Default.Key,
                        contentDescription = null,
                        tint = if (selectedHasKey) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(0.48f),
                        modifier = Modifier.size(18.dp)
                    )
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(aiProvider.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text(
                            if (selectedHasKey) "Key saved" else "Needs API key",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selectedHasKey) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error.copy(0.78f),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(0.55f))
                }
            }
            DropdownMenu(
                expanded = providerExpanded,
                onDismissRequest = { providerExpanded = false }
            ) {
                AiProvider.entries.forEach { provider ->
                    val hasKey = aiKeyStatus[provider] == true
                    DropdownMenuItem(
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Text(provider.displayName, fontWeight = FontWeight.SemiBold)
                                Text(
                                    if (hasKey) "Key saved" else "Needs key",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (hasKey) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error.copy(0.75f)
                                )
                            }
                        },
                        leadingIcon = {
                            Icon(
                                if (hasKey) Icons.Default.Check else Icons.Default.Key,
                                contentDescription = null,
                                tint = if (hasKey) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(0.45f),
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = {
                            if (provider == aiProvider) {
                                Icon(Icons.Default.RadioButtonChecked, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            }
                        },
                        onClick = {
                            providerExpanded = false
                            onSetAiProvider(provider)
                        }
                    )
                }
            }
        }

        Text(
            aiProvider.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(0.58f)
        )

        Text(
            "Model",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(0.45f)
        )
        Box {
            Surface(
                onClick = { modelExpanded = true },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(0.28f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Text(
                        selectedModel,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1
                    )
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(0.55f))
                }
            }
            DropdownMenu(
                expanded = modelExpanded,
                onDismissRequest = { modelExpanded = false }
            ) {
                modelOptions.forEach { model ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                model,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace
                            )
                        },
                        trailingIcon = {
                            if (model == selectedModel) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            }
                        },
                        onClick = {
                            modelExpanded = false
                            onSetAiModel(aiProvider, model)
                        }
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { onEditAiKey(aiProvider) },
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(5.dp))
                Text(if (selectedHasKey) "Replace" else "Add key")
            }
            OutlinedButton(
                onClick = { onRefreshAiModels(aiProvider) },
                enabled = selectedHasKey && !catalog.isLoading,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
            ) {
                if (catalog.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                }
                Spacer(Modifier.width(5.dp))
                Text("Refresh models")
            }
            if (selectedHasKey) {
                IconButton(onClick = { onClearAiKey(aiProvider) }) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Remove key", tint = MaterialTheme.colorScheme.error.copy(0.72f))
                }
            }
        }

        catalog.error?.let { error ->
            Text(
                error,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    isDark: Boolean,
    colorScheme: AppColorScheme,
    autoSuggest: Boolean,
    compactMode: Boolean,
    oledDarkTheme: Boolean,
    defaultDescSource: DescSource,
    aiProvider: AiProvider,
    aiKeyStatus: Map<AiProvider, Boolean>,
    aiModelCatalogs: Map<AiProvider, AiModelCatalog>,
    updateNotificationsEnabled: Boolean,
    updateStatus: UpdateStatus,
    onToggleTheme: () -> Unit,
    onSetColorScheme: (AppColorScheme) -> Unit,
    onToggleAutoSuggest: () -> Unit,
    onToggleCompactMode: () -> Unit,
    onToggleOledDarkTheme: () -> Unit,
    onSetDefaultDesc: (DescSource) -> Unit,
    onSetAiProvider: (AiProvider) -> Unit,
    onSetAiModel: (AiProvider, String) -> Unit,
    onRefreshAiModels: (AiProvider) -> Unit,
    onEditAiKey: (AiProvider) -> Unit,
    onClearAiKey: (AiProvider) -> Unit,
    onClearHistory: () -> Unit,
    onToggleUpdateNotifications: (Boolean) -> Unit,
    onCheckForUpdates: () -> Unit,
    onDownloadUpdate: () -> Unit,
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
            SettingsToggleRow(
                icon = Icons.Default.Brightness2,
                title = amoledModeTitle(),
                subtitle = amoledModeSubtitle(isDark),
                checked = oledDarkTheme,
                enabled = isOledModeControlEnabled(isDark),
                onToggle = onToggleOledDarkTheme
            )
            Text(
                "Color scheme",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(0.45f),
                modifier = Modifier.padding(top = 6.dp)
            )
            ColorSchemePicker(
                colorScheme = colorScheme,
                onSetColorScheme = onSetColorScheme
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
            SettingsToggleRow(
                icon = Icons.Default.Tune,
                title = "Compact mode",
                subtitle = "Show more content per screen",
                checked = compactMode,
                onToggle = onToggleCompactMode
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
            SettingsSectionHeader("AI Provider & Keys")
            AiProviderSettings(
                aiProvider = aiProvider,
                aiKeyStatus = aiKeyStatus,
                aiModelCatalogs = aiModelCatalogs,
                onSetAiProvider = onSetAiProvider,
                onSetAiModel = onSetAiModel,
                onRefreshAiModels = onRefreshAiModels,
                onEditAiKey = onEditAiKey,
                onClearAiKey = onClearAiKey
            )

            Spacer(Modifier.height(4.dp))
            SettingsSectionHeader("Data")
            SettingsActionRow(
                icon = Icons.Default.History,
                title = "Search History",
                subtitle = "Clear all recent searches",
                actionLabel = "Clear",
                actionColor = MaterialTheme.colorScheme.error,
                onClick = onClearHistory
            )

            UpdatesSection(
                updateNotificationsEnabled = updateNotificationsEnabled,
                updateStatus = updateStatus,
                onToggleUpdateNotifications = onToggleUpdateNotifications,
                onCheckForUpdates = onCheckForUpdates,
                onDownloadUpdate = onDownloadUpdate
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
    root.put("version", 2)
    root.put("exported_at", System.currentTimeMillis())
    root.put("app_version_name", BuildConfig.VERSION_NAME)
    root.put("app_version_code", BuildConfig.VERSION_CODE)
    root.put("includes_api_keys", true)
    val entries = JSONObject()
    val apiKeys = JSONObject()

    prefs.all.toSortedMap().forEach { (key, value) ->
        if (key in SECRET_PREF_KEYS) {
            SecurePrefs.getString(prefs, key)
                ?.takeIf { it.isNotBlank() }
                ?.let { apiKeys.put(key, it) }
            return@forEach
        }
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
    root.put("api_keys", apiKeys)
    root.put("included_sensitive_keys", JSONArray().apply {
        SECRET_PREF_KEYS.sorted()
            .filter { apiKeys.has(it) }
            .forEach(::put)
    })
    return root.toString(2)
}

private fun restoreSettingsFromBackup(
    prefs: android.content.SharedPreferences,
    rawJson: String
): Int {
    val root = JSONObject(rawJson)
    val entries = root.optJSONObject("entries")
        ?: throw IllegalArgumentException("Invalid settings backup file.")
    val apiKeys = root.optJSONObject("api_keys")
    val shouldPreserveExistingSecrets = apiKeys == null
    val preservedSecrets = if (shouldPreserveExistingSecrets) SECRET_PREF_KEYS.mapNotNull { key ->
        prefs.getString(key, null)?.let { key to it }
    }.toMap() else emptyMap()
    val editor = prefs.edit().clear()
    var restored = 0

    val keys = entries.keys()
    while (keys.hasNext()) {
        val key = keys.next()
        if (key in SECRET_PREF_KEYS) continue
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

    preservedSecrets.forEach { (key, value) ->
        editor.putString(key, value)
    }

    editor.apply()

    if (apiKeys != null) {
        SECRET_PREF_KEYS.forEach { key ->
            val value = apiKeys.optString(key, "").trim()
            if (value.isNotBlank()) {
                SecurePrefs.putString(prefs, key, value)
                restored++
            } else {
                SecurePrefs.remove(prefs, key)
            }
        }
    }

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
fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onToggle: () -> Unit
) {
    val compact = LocalCompactMode.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.45f)
            .padding(vertical = if (compact) 2.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f, fill = true)
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurface.copy(0.5f), modifier = Modifier.size(if (compact) 18.dp else 20.dp))
            Column(modifier = Modifier.weight(1f, fill = true)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = { onToggle() },
            enabled = enabled
        )
    }
}

@Composable
fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String,
    actionColor: Color,
    enabled: Boolean = true,
    progress: Float? = null,
    onClick: () -> Unit
) {
    val compact = LocalCompactMode.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .chemAnimateContentSize()
            .padding(vertical = if (compact) 2.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurface.copy(0.5f), modifier = Modifier.size(if (compact) 18.dp else 20.dp))
            Column(modifier = Modifier.weight(1f).chemAnimateContentSize()) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        TextButton(onClick = onClick, enabled = enabled) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                progress?.let {
                    CircularProgressIndicator(
                        progress = { it.coerceIn(0f, 1f) },
                        modifier = Modifier.size(if (compact) 16.dp else 18.dp),
                        strokeWidth = 2.dp,
                        color = actionColor,
                        trackColor = MaterialTheme.colorScheme.outline.copy(0.18f)
                    )
                }
                AnimatedActionLabel(
                    text = actionLabel,
                    color = if (enabled) actionColor else MaterialTheme.colorScheme.onSurface.copy(0.35f)
                )
            }
        }
    }
}

@Composable
private fun UpdatesSection(
    updateNotificationsEnabled: Boolean,
    updateStatus: UpdateStatus,
    onToggleUpdateNotifications: (Boolean) -> Unit,
    onCheckForUpdates: () -> Unit,
    onDownloadUpdate: () -> Unit,
    showHeader: Boolean = true
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
    val checkLabel = if (updateStatus.isChecking) "Checking..." else "Check"

    if (showHeader) {
        Spacer(Modifier.height(4.dp))
        SettingsSectionHeader("Updates")
    }
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
        SettingsActionRow(
            icon = Icons.Default.Download,
            title = "Update available",
            subtitle = updateDownloadSubtitle(updateStatus),
            actionLabel = updateDownloadActionLabel(updateStatus),
            actionColor = MaterialTheme.colorScheme.primary,
            enabled = !updateStatus.isDownloadingUpdate,
            progress = updateStatus.updateDownloadProgress?.takeIf { updateStatus.isDownloadingUpdate }
        ) {
            if (updateStatus.downloadUrl.isNullOrBlank() && updateStatus.downloadedUpdateApkPath == null) {
                Toast.makeText(context, "No download link found", Toast.LENGTH_SHORT).show()
            } else {
                onDownloadUpdate()
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
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                "Groq" to "AI summaries",
                "OpenAI" to "AI summaries",
                "OpenRouter" to "AI summaries",
                "Mistral AI" to "AI summaries"
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
fun AiProviderDialog(
    selectedProvider: AiProvider,
    keyStatus: Map<AiProvider, Boolean>,
    aiModelCatalogs: Map<AiProvider, AiModelCatalog>,
    onSelect: (AiProvider) -> Unit,
    onUseProvider: (AiProvider) -> Unit,
    onDismiss: () -> Unit
) {
    var activeProvider by remember(selectedProvider) { mutableStateOf(selectedProvider) }
    val activeHasKey = keyStatus[activeProvider] == true

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("AI description source", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Choose which provider should generate this compound description.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.64f)
                )

                AiProvider.entries.forEach { provider ->
                    val selected = activeProvider == provider
                    val hasKey = keyStatus[provider] == true
                    val catalog = aiModelCatalogs[provider]
                    val selectedModel = catalog?.selectedModel?.takeIf { it.isNotBlank() } ?: provider.modelName
                    Surface(
                        onClick = {
                            activeProvider = provider
                            onSelect(provider)
                        },
                        shape = RoundedCornerShape(13.dp),
                        color = if (selected) MaterialTheme.colorScheme.primary.copy(0.1f) else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            if (selected) 1.5.dp else 1.dp,
                            if (selected) MaterialTheme.colorScheme.primary.copy(0.72f)
                            else MaterialTheme.colorScheme.outline.copy(0.38f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                modifier = Modifier.size(36.dp),
                                shape = RoundedCornerShape(10.dp),
                                color = if (hasKey) MaterialTheme.colorScheme.primary.copy(0.12f)
                                else MaterialTheme.colorScheme.surface
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        if (hasKey) Icons.Default.Check else Icons.Default.Key,
                                        contentDescription = null,
                                        tint = if (hasKey) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(0.5f),
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(provider.displayName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        if (hasKey) "Key saved" else "Needs key",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (hasKey) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error.copy(0.82f),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    provider.description,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(0.58f)
                                )
                                Text(
                                    selectedModel,
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                    color = MaterialTheme.colorScheme.onSurface.copy(0.45f)
                                )
                            }
                            if (selected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onUseProvider(activeProvider) },
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    if (activeHasKey) Icons.Default.SmartToy else Icons.Default.Key,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(if (activeHasKey) "Use AI" else "Add key")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
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
                    "Get or manage a key at $link",
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
    "Why use this app instead of PubChem or ChemSpider?" to "It puts the same core data in a fast, mobile-friendly view and comes bundled with useful chemistry tools (reaction balancing, molar mass, stoichiometry, 3D viewer, comparison tools, and more) so you can study without constantly changing sites.",
    "Where does compound data come from?" to "Most compound properties, structures, and safety data are pulled from PubChem. Descriptions can also come from Wikipedia or AI depending on your settings.",
    "What can I search for?" to "Search by common name, IUPAC name, CAS number, or CID. Suggestions use PubChem as you type.",
    "Why am I not getting results?" to "Check spelling, try a CID or CAS number, or remove extra spaces. Some compounds are not listed in PubChem.",
    "What AI providers are supported?" to "ChemSearch supports Google Gemini, Groq Cloud, OpenAI, OpenRouter, and Mistral AI. Each provider needs its own API key.",
    "Do I need an API key for AI descriptions?" to "Yes. AI descriptions use your selected provider. Add a key in Settings > AI Provider & Keys. Keys are stored locally on your device.",
    "Where are my API keys stored?" to "Keys are stored locally with Android Keystore-backed encryption and are not synced.",
    "Is the app offline?" to "Most live search features require internet access. Downloaded compounds in Library can be opened later with saved structures, descriptions, synonyms, safety info, and identifiers.",
    "What is the difference between cache and Downloads?" to "Cache helps repeated searches load faster and can be cleared anytime. Downloads are deliberate offline copies saved in Library with compound data and structures.",
    "Where are downloaded compounds stored?" to "Downloaded compounds are stored in the app's local Room database on your device. They are not uploaded anywhere by ChemSearch.",
    "What is the Chemical Database?" to "It is a built-in reference browser for substances, ions, functional groups, and reactions. It uses local JSON data bundled with the app.",
    "How do autosuggestions work?" to "Autosuggestions query PubChem as you type. You can toggle them in Settings > Search.",
    "How do I save favorites?" to "Tap the star icon on a compound, then open Library > Favorites. Favorites are stored locally.",
    "How do I clear history or cache?" to "Go to Settings > Data to clear search history or manage the compound cache.",
    "How do I download structures?" to "Use the Structure buttons to save PNG/SDF files, or tap the download icon below the star to save the whole compound in Library > Downloads.",
    "Why is the 3D model missing?" to "Some compounds do not have a 3D SDF available in PubChem or through the fallback resolver. Metals, salts, ionic compounds, and very large molecules are common cases.",
    "How do I use the custom 3D viewer?" to "Open Tools > Custom 3D Molecule Viewer and load a .sdf or .mol file from your device.",
    "What does the SMILES visualizer do?" to "Paste a SMILES string to look it up on PubChem and view its 2D or 3D structure when available.",
    "How does Compare Compounds work?" to "Open Tools > Compare Compounds, enter two or more compound names, and compare formula, descriptions, identifiers, atom counts, bond counts, and key properties.",
    "What does the pH / pOH calculator do?" to "It converts between pH, pOH, hydrogen ion concentration, and hydroxide ion concentration, then classifies the solution as acidic, neutral, or basic.",
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
    offlineMetadata: OfflineDownloadMetadata? = null,
    showImage: Boolean = true,
    enableSelect: Boolean = true,
    showReorderControls: Boolean = false,
    canMoveUp: Boolean = false,
    canMoveDown: Boolean = false,
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {}
) {
    val compact = LocalCompactMode.current
    Card(
        onClick = { if (enableSelect) onSelect(favorite.name) },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(if (compact) 9.dp else 12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 9.dp else 12.dp)
        ) {
            if (showImage) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.size(if (compact) 52.dp else 64.dp)
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
                OfflineAssetChips(metadata = offlineMetadata, maxChips = 4)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OfflineAssetChips(
    metadata: OfflineDownloadMetadata?,
    maxChips: Int,
    modifier: Modifier = Modifier
) {
    val chips = metadata?.assetChips.orEmpty().take(maxChips)
    if (chips.isEmpty()) return

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        chips.forEach { label ->
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.primary.copy(0.08f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(0.16f))
            ) {
                Text(
                    label,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.58f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    letterSpacing = 0.sp
                )
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

private enum class LibraryTab { FAVORITES, DOWNLOADS, DATABASE }
private enum class LibraryViewMode { LIST, GRID }

private data class LibraryOption(
    val tab: LibraryTab,
    val icon: ChemIconSpec,
    val title: String,
    val subtitle: String,
    val countLabel: String? = null,
    val databaseSummary: ChemicalDatabaseSummary? = null
)

private fun LibraryTab.icon(): ChemIconSpec = when (this) {
    LibraryTab.FAVORITES -> ChemAppIcons.Star
    LibraryTab.DOWNLOADS -> ChemAppIcons.Download
    LibraryTab.DATABASE -> ChemAppIcons.Library
}

@Composable
private fun LibraryOptionCard(
    icon: ChemIconSpec,
    title: String,
    subtitle: String,
    countLabel: String? = null,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val compact = LocalCompactMode.current
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(if (compact) 1.18f else 1.25f),
        shape = RoundedCornerShape(if (compact) 16.dp else 18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary.copy(0.42f)
            else MaterialTheme.colorScheme.outline.copy(0.18f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (compact) 13.dp else 15.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.Start
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .size(if (compact) 42.dp else 48.dp)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(if (selected) 0.16f else 0.1f),
                            RoundedCornerShape(if (compact) 11.dp else 12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    ChemIcon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(if (compact) 22.dp else 25.dp)
                    )
                }
                if (!countLabel.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (selected) MaterialTheme.colorScheme.primary.copy(0.14f) else MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(0.12f))
                    ) {
                        Text(
                            countLabel,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(0.58f),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 5.dp)) {
                Text(
                    title,
                    style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = if (compact) 2 else 3,
                    letterSpacing = 0.sp
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.52f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    letterSpacing = 0.sp
                )
            }
        }
    }
}

@Composable
private fun ChemicalDatabaseSummaryBreakdown(
    summary: ChemicalDatabaseSummary,
    modifier: Modifier = Modifier
) {
    val compact = LocalCompactMode.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(9.dp),
        color = MaterialTheme.colorScheme.primary.copy(0.08f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(0.14f))
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = if (compact) 8.dp else 9.dp,
                vertical = if (compact) 5.dp else 6.dp
            ),
            verticalArrangement = Arrangement.spacedBy(if (compact) 1.dp else 2.dp)
        ) {
            chemicalDatabaseSummaryRows(summary).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        row.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        letterSpacing = 0.sp
                    )
                    Text(
                        row.count.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        letterSpacing = 0.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryOptionListCard(
    icon: ChemIconSpec,
    title: String,
    subtitle: String,
    countLabel: String? = null,
    databaseSummary: ChemicalDatabaseSummary? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val compact = LocalCompactMode.current
    var showDatabaseSummary by remember(databaseSummary) { mutableStateOf(false) }
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(if (compact) 14.dp else 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (compact) 12.dp else 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(if (compact) 44.dp else 52.dp)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(0.1f),
                        RoundedCornerShape(if (compact) 10.dp else 12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                ChemIcon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(if (compact) 22.dp else 26.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 3.dp)
            ) {
                Text(
                    title,
                    style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = if (compact) 1 else 2,
                    overflow = if (compact) TextOverflow.Ellipsis else TextOverflow.Clip
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp)
                ) {
                    Text(
                        subtitle,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.52f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (databaseSummary != null) {
                        IconButton(
                            onClick = { showDatabaseSummary = !showDatabaseSummary },
                            modifier = Modifier.size(if (compact) 26.dp else 30.dp)
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = if (showDatabaseSummary) "Hide database counts" else "Show database counts",
                                tint = MaterialTheme.colorScheme.primary.copy(if (showDatabaseSummary) 0.86f else 0.58f),
                                modifier = Modifier.size(if (compact) 15.dp else 17.dp)
                            )
                        }
                    }
                }
                databaseSummary?.takeIf { showDatabaseSummary }?.let {
                    ChemicalDatabaseSummaryBreakdown(summary = it)
                }
            }
            if (!countLabel.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.primary.copy(0.1f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(0.16f))
                ) {
                    Text(
                        countLabel,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(0.3f),
                modifier = Modifier.size(if (compact) 18.dp else 22.dp)
            )
        }
    }
}

@Composable
private fun LibraryViewToggle(
    viewMode: LibraryViewMode,
    onViewModeChange: (LibraryViewMode) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(0.65f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(0.18f))
    ) {
        Row(modifier = Modifier.padding(3.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(
                LibraryViewMode.LIST to Icons.AutoMirrored.Filled.ViewList,
                LibraryViewMode.GRID to Icons.Default.GridView
            ).forEach { (mode, icon) ->
                val selected = viewMode == mode
                Surface(
                    onClick = { onViewModeChange(mode) },
                    shape = CircleShape,
                    color = if (selected) MaterialTheme.colorScheme.primary.copy(0.16f) else Color.Transparent,
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            icon,
                            contentDescription = if (mode == LibraryViewMode.LIST) "List view" else "Grid view",
                            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(0.55f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryEmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
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
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(0.5f),
                    modifier = Modifier.size(40.dp)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface.copy(0.7f))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(0.38f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}

private fun DownloadedCompound.toFavoriteCardData(): FavoriteCompound =
    FavoriteCompound(
        cid = cid,
        name = name,
        formula = formula,
        molecularWeight = molecularWeight,
        iupacName = iupacName,
        savedAt = savedAt
    )

@Composable
private fun LibraryGridCard(
    favorite: FavoriteCompound,
    onSelect: (String) -> Unit,
    onDelete: (Long) -> Unit,
    modifier: Modifier = Modifier,
    offlineMetadata: OfflineDownloadMetadata? = null
) {
    val compact = LocalCompactMode.current
    Card(
        onClick = { onSelect(favorite.name) },
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(if (compact) 0.88f else 0.92f),
        shape = RoundedCornerShape(if (compact) 16.dp else 18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (compact) 11.dp else 13.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = RoundedCornerShape(11.dp),
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.size(if (compact) 46.dp else 54.dp)
                ) {
                    AsyncImage(
                        model = "https://pubchem.ncbi.nlm.nih.gov/rest/pug/compound/cid/${favorite.cid}/PNG?record_type=2d&image_size=small",
                        contentDescription = "Structure of ${favorite.name}",
                        modifier = Modifier.fillMaxSize().padding(4.dp),
                        contentScale = ContentScale.Fit
                    )
                }
                IconButton(
                    onClick = { onDelete(favorite.cid) },
                    modifier = Modifier.size(if (compact) 28.dp else 30.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Remove",
                        tint = MaterialTheme.colorScheme.error.copy(0.62f),
                        modifier = Modifier.size(if (compact) 16.dp else 17.dp)
                    )
                }
            }

            Text(
                favorite.name,
                style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                letterSpacing = 0.sp
            )

            if (favorite.formula.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(0.08f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(0.14f))
                ) {
                    Text(
                        toSubscriptFormula(favorite.formula),
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Text(
                "CID ${favorite.cid}",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface.copy(0.42f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            OfflineAssetChips(metadata = offlineMetadata, maxChips = 3)
        }
    }
}

@Composable
fun LibraryInline(
    favorites: List<FavoriteCompound>,
    downloads: List<DownloadedCompound>,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onSelectFavorite: (String) -> Unit,
    onSelectDownload: (Long) -> Unit,
    onDeleteFavorite: (Long) -> Unit,
    onDeleteDownload: (Long) -> Unit,
    onMoveFavorite: (Int, Int) -> Unit,
    onSearchCompoundFromDatabase: (String) -> Unit = {}
) {
    var selectedSection by remember { mutableStateOf<LibraryTab?>(null) }
    var homeViewMode by remember { mutableStateOf(LibraryViewMode.LIST) }
    var itemViewMode by remember { mutableStateOf(LibraryViewMode.LIST) }
    var filterQuery by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf(FavoritesSort.RECENT) }
    var isReordering by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val databaseEntries = remember(context) { ChemicalDatabase.load(context) }
    val databaseSummary = remember(databaseEntries) { summarizeChemicalDatabase(databaseEntries) }

    LaunchedEffect(selectedSection, favorites.size) {
        if (selectedSection != LibraryTab.FAVORITES || favorites.size < 2) isReordering = false
        filterQuery = ""
        sortMode = FavoritesSort.RECENT
        focusManager.clearFocus()
    }

    val libraryOptions = remember(favorites.size, downloads.size, databaseSummary) {
        listOf(
            LibraryOption(
                tab = LibraryTab.FAVORITES,
                icon = LibraryTab.FAVORITES.icon(),
                title = "Favorites",
                subtitle = "Saved quick links",
                countLabel = favorites.size.toString()
            ),
            LibraryOption(
                tab = LibraryTab.DOWNLOADS,
                icon = LibraryTab.DOWNLOADS.icon(),
                title = "Downloads",
                subtitle = "Full offline copies",
                countLabel = downloads.size.toString()
            ),
            LibraryOption(
                tab = LibraryTab.DATABASE,
                icon = LibraryTab.DATABASE.icon(),
                title = "Chemical Database",
                subtitle = "Substances and references",
                databaseSummary = databaseSummary
            )
        )
    }

    @Composable
    fun SortAndFilterControls(filterLabel: String, matchCount: Int) {
        OutlinedTextField(
            value = filterQuery,
            onValueChange = { filterQuery = it },
            label = { Text(filterLabel) },
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
                        "$matchCount match${if (matchCount == 1) "" else "es"}",
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (selectedSection == null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChemIcon(ChemAppIcons.Library, null, tint = MaterialTheme.colorScheme.primary.copy(0.7f), modifier = Modifier.size(18.dp))
                    Text("Library", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                LibraryViewToggle(viewMode = homeViewMode, onViewModeChange = { homeViewMode = it })
            } else {
                TextButton(
                    onClick = {
                        selectedSection = null
                        isReordering = false
                    },
                    contentPadding = PaddingValues(horizontal = 0.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Back to Library")
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (selectedSection != LibraryTab.DATABASE) {
                        LibraryViewToggle(viewMode = itemViewMode, onViewModeChange = { itemViewMode = it })
                    }
                    if (selectedSection == LibraryTab.FAVORITES && favorites.size > 1) {
                    TextButton(
                        onClick = {
                            val next = !isReordering
                            isReordering = next
                            if (next) {
                                filterQuery = ""
                                sortMode = FavoritesSort.RECENT
                                    itemViewMode = LibraryViewMode.LIST
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
        }

        if (selectedSection == null) {
            Text(
                "Open saved compounds, offline copies, or built-in reference data.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
            )
            if (homeViewMode == LibraryViewMode.LIST) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    libraryOptions.forEach { option ->
                        LibraryOptionListCard(
                            icon = option.icon,
                            title = option.title,
                            subtitle = option.subtitle,
                            countLabel = if (option.databaseSummary == null) option.countLabel else null,
                            databaseSummary = option.databaseSummary,
                            onClick = { selectedSection = option.tab }
                        )
                    }
                }
            } else {
                libraryOptions.chunked(2).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowItems.forEach { option ->
                            LibraryOptionCard(
                                icon = option.icon,
                                title = option.title,
                                subtitle = option.subtitle,
                                countLabel = if (option.databaseSummary == null) option.countLabel else null,
                                selected = false,
                                modifier = Modifier.weight(1f),
                                onClick = { selectedSection = option.tab }
                            )
                        }
                        if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            return@Column
        }

        val section = selectedSection ?: return@Column
        val sectionTitle = when (section) {
            LibraryTab.FAVORITES -> "Favorites"
            LibraryTab.DOWNLOADS -> "Downloads"
            LibraryTab.DATABASE -> "Chemical Database"
        }
        val sectionSubtitle = when (section) {
            LibraryTab.FAVORITES -> "Saved quick links on this device."
            LibraryTab.DOWNLOADS -> "Offline compound copies with saved structures and data."
            LibraryTab.DATABASE -> "Browse substances, reactions, functional groups, and ions."
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChemIcon(
                section.icon(),
                null,
                tint = MaterialTheme.colorScheme.primary.copy(0.7f),
                modifier = Modifier.size(18.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(sectionTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(sectionSubtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
            }
        }

        if (isReordering) {
            Text(
                "Reorder mode: use the arrows to move favorite items.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(0.55f)
            )
        }

        if (section == LibraryTab.DATABASE) {
            ChemicalDatabaseTool(
                modifier = Modifier.weight(1f),
                onSearchCompound = onSearchCompoundFromDatabase
            )
            return@Column
        }

        val normalizedQuery = filterQuery.trim().lowercase(Locale.US)
        if (section == LibraryTab.FAVORITES) {
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
            val displayFavorites = if (isReordering) favorites else filteredFavorites
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (favorites.isEmpty()) {
                    LibraryEmptyState(
                        icon = Icons.Default.Star,
                        title = "No favorites yet",
                        subtitle = "Tap the star icon on any compound"
                    )
                } else {
                    if (!isReordering) SortAndFilterControls("Filter favorites", filteredFavorites.size)
                    if (displayFavorites.isEmpty()) {
                        LibraryEmptyState(
                            icon = Icons.Default.Search,
                            title = "No matches found",
                            subtitle = "Try a different name, formula, or CID."
                        )
                    } else if (itemViewMode == LibraryViewMode.LIST || isReordering) {
                        displayFavorites.forEachIndexed { index, fav ->
                            FavoriteCard(
                                favorite = fav,
                                onSelect = onSelectFavorite,
                                onDelete = onDeleteFavorite,
                                enableSelect = !isReordering,
                                showReorderControls = isReordering,
                                canMoveUp = isReordering && index > 0,
                                canMoveDown = isReordering && index < displayFavorites.lastIndex,
                                onMoveUp = { if (isReordering && index > 0) onMoveFavorite(index, index - 1) },
                                onMoveDown = { if (isReordering && index < displayFavorites.lastIndex) onMoveFavorite(index, index + 1) }
                            )
                        }
                    } else {
                        displayFavorites.chunked(2).forEach { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowItems.forEach { fav ->
                                    LibraryGridCard(
                                        favorite = fav,
                                        onSelect = onSelectFavorite,
                                        onDelete = onDeleteFavorite,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        } else {
            val filteredDownloads = remember(downloads, normalizedQuery, sortMode) {
                val base = if (normalizedQuery.isBlank()) {
                    downloads
                } else {
                    downloads.filter { item ->
                        item.name.lowercase(Locale.US).contains(normalizedQuery) ||
                            item.formula.lowercase(Locale.US).contains(normalizedQuery) ||
                            item.iupacName.lowercase(Locale.US).contains(normalizedQuery) ||
                            item.cid.toString().contains(normalizedQuery) ||
                            item.molecularWeight.lowercase(Locale.US).contains(normalizedQuery)
                    }
                }
                when (sortMode) {
                    FavoritesSort.NAME -> base.sortedBy { it.name.lowercase(Locale.US) }
                    FavoritesSort.ATOMS_DESC -> base.sortedWith(
                        compareByDescending<DownloadedCompound> { countAtomsInFormula(it.formula) }
                            .thenBy { it.name.lowercase(Locale.US) }
                    )
                    FavoritesSort.ATOMS_ASC -> base.sortedWith(
                        compareBy<DownloadedCompound> { countAtomsInFormula(it.formula) }
                            .thenBy { it.name.lowercase(Locale.US) }
                    )
                    FavoritesSort.RECENT -> base
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (downloads.isEmpty()) {
                    LibraryEmptyState(
                        icon = Icons.Default.Download,
                        title = "No downloads yet",
                        subtitle = "Tap the download icon under the star on any compound"
                    )
                } else {
                    SortAndFilterControls("Filter downloads", filteredDownloads.size)
                    if (filteredDownloads.isEmpty()) {
                        LibraryEmptyState(
                            icon = Icons.Default.Search,
                            title = "No matches found",
                            subtitle = "Try a different name, formula, or CID."
                        )
                    } else if (itemViewMode == LibraryViewMode.LIST) {
                        filteredDownloads.forEach { item ->
                            FavoriteCard(
                                favorite = item.toFavoriteCardData(),
                                onSelect = { onSelectDownload(item.cid) },
                                onDelete = onDeleteDownload,
                                offlineMetadata = item.offlineMetadata
                            )
                        }
                    } else {
                        filteredDownloads.chunked(2).forEach { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowItems.forEach { item ->
                                    LibraryGridCard(
                                        favorite = item.toFavoriteCardData(),
                                        onSelect = { onSelectDownload(item.cid) },
                                        onDelete = onDeleteDownload,
                                        modifier = Modifier.weight(1f),
                                        offlineMetadata = item.offlineMetadata
                                    )
                                }
                                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
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
                            Icon(Icons.Default.StarBorder, null, tint = MaterialTheme.colorScheme.primary.copy(0.5f), modifier = Modifier.size(36.dp))
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("No favorites yet", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                            Text("Tap the star icon on any compound", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(0.38f))
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
                        Icons.Default.StarBorder,
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
                Icon(Icons.Default.Star, null, tint = MaterialTheme.colorScheme.primary.copy(0.7f), modifier = Modifier.size(18.dp))
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

@Composable
private fun SettingsGroupCard(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val compact = LocalCompactMode.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = if (compact) 4.dp else 6.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 9.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }
        if (!subtitle.isNullOrBlank()) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(0.6f)
            )
        }
        content()
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outline.copy(0.14f),
            modifier = Modifier.padding(top = if (compact) 4.dp else 6.dp)
        )
    }
}

@Composable
private fun SettingsGroupDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.14f))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsInline(
    isDark: Boolean,
    colorScheme: AppColorScheme,
    autoSuggest: Boolean,
    compactMode: Boolean,
    oledDarkTheme: Boolean,
    defaultDescSource: DescSource,
    aiProvider: AiProvider,
    aiKeyStatus: Map<AiProvider, Boolean>,
    aiModelCatalogs: Map<AiProvider, AiModelCatalog>,
    updateNotificationsEnabled: Boolean = true,
    updateStatus: UpdateStatus = UpdateStatus(),
    onToggleTheme: () -> Unit,
    onSetColorScheme: (AppColorScheme) -> Unit,
    onToggleAutoSuggest: () -> Unit,
    onToggleCompactMode: () -> Unit,
    onToggleOledDarkTheme: () -> Unit,
    onSetDefaultDesc: (DescSource) -> Unit,
    onSetAiProvider: (AiProvider) -> Unit,
    onSetAiModel: (AiProvider, String) -> Unit,
    onRefreshAiModels: (AiProvider) -> Unit,
    onEditAiKey: (AiProvider) -> Unit,
    onClearAiKey: (AiProvider) -> Unit,
    onClearHistory: () -> Unit,
    onToggleUpdateNotifications: (Boolean) -> Unit = {},
    onCheckForUpdates: () -> Unit = {},
    onDownloadUpdate: () -> Unit = {},
    cacheSizeBytes: Long = 0L,
    cacheDir: String = "",
    onClearCache: () -> Unit = {},
    onSetCacheDir: (String) -> Boolean = { true },
    onTestUpdateNotification: () -> Unit = {},
    onShowWelcome: () -> Unit = {},
    onSettingsImported: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("chemsearch_prefs", Context.MODE_PRIVATE) }
    var buildTapCount by remember { mutableIntStateOf(0) }
    var isDevMode by remember { mutableStateOf(prefs.getBoolean("dev_mode", false)) }
    var themeDropdownExpanded by remember { mutableStateOf(false) }
    var showFaqDialog by remember { mutableStateOf(false) }
    var showCacheDirDialog by remember { mutableStateOf(false) }
    var cacheDirInput by remember(cacheDir) { mutableStateOf(cacheDir) }

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
            Toast.makeText(context, "Settings exported with API keys.", Toast.LENGTH_LONG).show()
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

    if (showFaqDialog) {
        InfoDialog(title = "FAQ", entries = FAQ_ENTRIES, onDismiss = { showFaqDialog = false })
    }

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
                Button(
                    onClick = {
                        val saved = onSetCacheDir(cacheDirInput.trim())
                        if (saved) {
                            Toast.makeText(context, "Cache location updated", Toast.LENGTH_SHORT).show()
                            showCacheDirDialog = false
                        } else {
                            Toast.makeText(context, "That cache location is not writable", Toast.LENGTH_LONG).show()
                        }
                    },
                    shape = RoundedCornerShape(10.dp)
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showCacheDirDialog = false }) { Text("Cancel") } },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    val cacheSizeLabel = remember(cacheSizeBytes) {
        when {
            cacheSizeBytes == 0L -> "Empty"
            cacheSizeBytes < 1024 -> "${cacheSizeBytes} B"
            cacheSizeBytes < 1024 * 1024 -> "${"%.1f".format(cacheSizeBytes / 1024.0)} KB"
            else -> "${"%.2f".format(cacheSizeBytes / (1024.0 * 1024.0))} MB"
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "Customize search, AI, storage, and app updates.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(0.55f)
        )

        SettingsGroupCard(
            icon = Icons.Default.Tune,
            title = "Display & Search",
            subtitle = "Control theme, autosuggestions, and default description behavior."
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
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
                        color = MaterialTheme.colorScheme.surface,
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
            SettingsGroupDivider()
            SettingsToggleRow(
                icon = Icons.Default.Brightness2,
                title = amoledModeTitle(),
                subtitle = amoledModeSubtitle(isDark),
                checked = oledDarkTheme,
                enabled = isOledModeControlEnabled(isDark),
                onToggle = onToggleOledDarkTheme
            )
            SettingsGroupDivider()
            Text(
                "Color scheme",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(0.45f)
            )
            ColorSchemePicker(
                colorScheme = colorScheme,
                onSetColorScheme = onSetColorScheme
            )
            SettingsGroupDivider()
            SettingsToggleRow(
                icon = Icons.Default.Search,
                title = "Autosuggestions",
                subtitle = "Show dropdown while typing",
                checked = autoSuggest,
                onToggle = onToggleAutoSuggest
            )
            SettingsGroupDivider()
            SettingsToggleRow(
                icon = Icons.Default.Tune,
                title = "Compact mode",
                subtitle = "Show more content per screen",
                checked = compactMode,
                onToggle = onToggleCompactMode
            )
            SettingsGroupDivider()
            Text(
                "Default description source",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(0.45f)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(DescSource.PUBCHEM to "PubChem", DescSource.WIKI to "Wikipedia", DescSource.AI to "AI").forEach { (src, label) ->
                    SourceBtn(label = label, active = defaultDescSource == src) { onSetDefaultDesc(src) }
                }
            }
        }

        SettingsGroupCard(
            icon = Icons.Default.Key,
            title = "AI Provider & Keys",
            subtitle = "Pick a provider and manage encrypted local API keys."
        ) {
            AiProviderSettings(
                aiProvider = aiProvider,
                aiKeyStatus = aiKeyStatus,
                aiModelCatalogs = aiModelCatalogs,
                onSetAiProvider = onSetAiProvider,
                onSetAiModel = onSetAiModel,
                onRefreshAiModels = onRefreshAiModels,
                onEditAiKey = onEditAiKey,
                onClearAiKey = onClearAiKey
            )
        }

        SettingsGroupCard(
            icon = Icons.Default.Storage,
            title = "Data & Storage",
            subtitle = "Clear history/cache and import or export local settings."
        ) {
            SettingsActionRow(
                icon = Icons.Default.History,
                title = "Search History",
                subtitle = "Clear all recent searches",
                actionLabel = "Clear",
                actionColor = MaterialTheme.colorScheme.error,
                onClick = onClearHistory
            )
            SettingsActionRow(
                icon = Icons.Default.Cached,
                title = "Compound cache",
                subtitle = "$cacheSizeLabel · ${if (cacheDir.isBlank()) "Default location" else cacheDir.takeLast(42)}",
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
                onClick = {
                    cacheDirInput = cacheDir
                    showCacheDirDialog = true
                }
            )
            SettingsGroupDivider()
            SettingsActionRow(
                icon = Icons.Default.Description,
                title = "Export settings",
                subtitle = "Save settings, AI provider models, and API keys to JSON",
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
        }

        SettingsGroupCard(
            icon = Icons.Default.SystemUpdate,
            title = "Updates & Help",
            subtitle = "Control update checks and open support resources."
        ) {
            UpdatesSection(
                updateNotificationsEnabled = updateNotificationsEnabled,
                updateStatus = updateStatus,
                onToggleUpdateNotifications = onToggleUpdateNotifications,
                onCheckForUpdates = onCheckForUpdates,
                onDownloadUpdate = onDownloadUpdate,
                showHeader = false
            )
            SettingsGroupDivider()
            SettingsActionRow(
                icon = Icons.AutoMirrored.Filled.HelpOutline,
                title = "Frequently asked questions",
                subtitle = "Quick answers about ChemSearch",
                actionLabel = "Open",
                actionColor = MaterialTheme.colorScheme.primary,
                onClick = { showFaqDialog = true }
            )
        }

        if (isDevMode) {
            SettingsGroupCard(
                icon = Icons.Default.BugReport,
                title = "Developer",
                subtitle = "Diagnostics and advanced debugging tools."
            ) {
                DebugSettingsSection(
                    prefs = prefs,
                    onTestUpdateNotification = onTestUpdateNotification,
                    onShowWelcome = onShowWelcome,
                    onDisableDevMode = { persist ->
                        isDevMode = false
                        buildTapCount = 0
                        if (persist) {
                            prefs.edit().putBoolean("dev_mode", false).apply()
                        }
                    }
                )
            }
        }

        SettingsGroupCard(
            icon = Icons.Default.Info,
            title = "About ChemSearch"
        ) {
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
}

private enum class NetworkProbeState { SUCCESS, FAILED, SKIPPED }

private data class NetworkProbeResult(
    val service: String,
    val endpoint: String,
    val state: NetworkProbeState,
    val statusCode: Int?,
    val latencyMs: Long?,
    val detail: String,
    val responsePreview: String? = null
)

private fun skippedNetworkProbe(service: String, endpoint: String, reason: String): NetworkProbeResult =
    NetworkProbeResult(
        service = service,
        endpoint = endpoint,
        state = NetworkProbeState.SKIPPED,
        statusCode = null,
        latencyMs = null,
        detail = reason,
        responsePreview = null
    )

private fun String.toNetworkSnippet(): String =
    replace(Regex("\\s+"), " ").trim().take(260)

private fun runNetworkProbe(
    service: String,
    endpoint: String,
    request: Request
): NetworkProbeResult {
    val startedAt = System.nanoTime()
    return try {
        ApiClient.rawHttp.newCall(request).execute().use { response ->
            val latencyMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)
            val statusCode = response.code
            val preview = runCatching { response.body.string().toNetworkSnippet() }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
            val detail = "HTTP $statusCode ${response.message}"
            NetworkProbeResult(
                service = service,
                endpoint = endpoint,
                state = if (response.isSuccessful) NetworkProbeState.SUCCESS else NetworkProbeState.FAILED,
                statusCode = statusCode,
                latencyMs = latencyMs,
                detail = detail,
                responsePreview = preview
            )
        }
    } catch (e: Exception) {
        val latencyMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)
        NetworkProbeResult(
            service = service,
            endpoint = endpoint,
            state = NetworkProbeState.FAILED,
            statusCode = null,
            latencyMs = latencyMs,
            detail = "${e::class.simpleName}: ${e.message ?: "Unknown error"}",
            responsePreview = null
        )
    }
}

private suspend fun runNetworkDiagnosticsChecks(
    apiKeys: Map<AiProvider, String?>
): List<NetworkProbeResult> = withContext(Dispatchers.IO) {
    buildNetworkDiagnosticProbeSpecs().map { spec ->
        val apiKey = spec.aiProvider?.let { apiKeys[it] }
        if (spec.requiresApiKey() && apiKey.isNullOrBlank()) {
            skippedNetworkProbe(
                service = spec.service,
                endpoint = spec.endpoint,
                reason = "Skipped: ${spec.aiProvider?.displayName ?: "Provider"} API key is not set."
            )
        } else {
            val builder = Request.Builder().url(spec.requestUrl(apiKey))
            spec.headers.forEach { (key, value) -> builder.header(key, value) }
            when (spec.auth) {
                NetworkDiagnosticAuth.BEARER -> builder.header("Authorization", "Bearer $apiKey")
                NetworkDiagnosticAuth.NONE,
                NetworkDiagnosticAuth.QUERY_KEY -> Unit
            }
            val request = when (spec.method) {
                NetworkDiagnosticMethod.GET -> builder.get().build()
                NetworkDiagnosticMethod.POST -> builder
                    .post(spec.body.orEmpty().toRequestBody("application/json".toMediaType()))
                    .build()
            }
            runNetworkProbe(spec.service, spec.endpoint, request)
        }
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
    onShowWelcome: () -> Unit,
    onDisableDevMode: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var verboseLogging by remember { mutableStateOf(prefs.getBoolean("debug_verbose", false)) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showPrefsDialog by remember { mutableStateOf(false) }
    var showLogsDialog by remember { mutableStateOf(false) }
    var showMemoryDialog by remember { mutableStateOf(false) }
    var showNetworkDialog by remember { mutableStateOf(false) }
    var showCrashConfirm by remember { mutableStateOf(false) }
    var showWipeConfirm by remember { mutableStateOf(false) }
    var isRunningNetworkDiagnostics by remember { mutableStateOf(false) }
    var networkDiagnosticsRunAt by remember { mutableStateOf<Long?>(null) }
    var networkDiagnosticsResults by remember { mutableStateOf<List<NetworkProbeResult>>(emptyList()) }
    val logLines = DebugLog.lines

    fun runNetworkDiagnostics() {
        if (isRunningNetworkDiagnostics) return
        isRunningNetworkDiagnostics = true
        scope.launch {
            val apiKeys = AiProvider.entries.associateWith { provider ->
                SecurePrefs.getString(prefs, provider.keyPref)
            }
            val results = runNetworkDiagnosticsChecks(apiKeys)
            networkDiagnosticsResults = results
            networkDiagnosticsRunAt = System.currentTimeMillis()
            isRunningNetworkDiagnostics = false
            val successCount = results.count { it.state == NetworkProbeState.SUCCESS }
            val failureCount = results.count { it.state == NetworkProbeState.FAILED }
            val skippedCount = results.count { it.state == NetworkProbeState.SKIPPED }
            DebugLog.i(
                "ChemSearch",
                "Network diagnostics finished: success=$successCount, failed=$failureCount, skipped=$skippedCount"
            )
        }
    }

    fun redactValue(key: String, value: Any?): String {
        val raw = value?.toString() ?: "null"
        val isSensitive = SENSITIVE_PREF_TOKENS.any { key.lowercase(Locale.US).contains(it) }
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
                "Inspect SharedPreferences" to "Dumps legacy SharedPreferences and encrypted key records with sensitive values masked. DataStore settings and Room downloads are stored separately.",
                "Memory info" to "Shows current heap usage from the JVM runtime and the Android ActivityManager. Useful for spotting memory leaks or unusually high allocations.",
                "Network diagnostics" to "Runs endpoint checks against PubChem search, autocomplete, GHS data, 2D/3D structures, the NCI/CADD fallback resolver, Wikipedia, GitHub releases, and every configured AI provider. Shows HTTP status, latency, and response previews for each service.",
                "Show welcome screen" to "Clears the welcome-screen skip flag and opens the first-run welcome screen again.",
                "API endpoints" to "Copies base URLs for PubChem, NCI/CADD, Wikipedia, GitHub releases, and supported AI providers to your clipboard for manual testing.",
                "Wipe all SharedPreferences" to "Calls prefs.edit().clear(). Removes legacy preference values, encrypted key records, history, favorites, and debug flags. DataStore settings, Room downloads, and app cache files are not deleted by this action; restart recommended.",
                "Force crash" to "Deliberately throws an unhandled RuntimeException. Used to verify that crash reporting / Logcat is working correctly. There is a confirmation step before it fires.",
                "Hide debug settings" to "Sets dev_mode=false and hides this section. Tap the build number 5 times in the About card to unlock it again."
            ),
            onDismiss = { showInfoDialog = false }
        )
    }

    if (showPrefsDialog) {
        val prefEntries = prefs.all.entries.sortedBy { it.key }
        val hasSensitive = prefEntries.any { entry ->
            SENSITIVE_PREF_TOKENS.any { entry.key.lowercase(Locale.US).contains(it) }
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
                                "Sensitive values are masked and are never copied from this view.",
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
                    }) { Text("Copy") }
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

    if (showNetworkDialog) {
        val successCount = networkDiagnosticsResults.count { it.state == NetworkProbeState.SUCCESS }
        val failedCount = networkDiagnosticsResults.count { it.state == NetworkProbeState.FAILED }
        val skippedCount = networkDiagnosticsResults.count { it.state == NetworkProbeState.SKIPPED }
        val runLabel = networkDiagnosticsRunAt?.let {
            DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), DateUtils.SECOND_IN_MILLIS)
        } ?: "Not run yet"

        AlertDialog(
            onDismissRequest = { showNetworkDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Network diagnostics", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (isRunningNetworkDiagnostics) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Last run: $runLabel",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.55f)
                    )
                    if (networkDiagnosticsResults.isNotEmpty()) {
                        Text(
                            "Success $successCount · Failed $failedCount · Skipped $skippedCount",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.6f)
                        )
                    }
                    if (networkDiagnosticsResults.isEmpty() && !isRunningNetworkDiagnostics) {
                        Text(
                            "Run diagnostics to test PubChem lookup, structures, GHS data, fallback 3D loading, Wikipedia, GitHub releases, and AI endpoints.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.65f)
                        )
                    }
                    networkDiagnosticsResults.forEach { result ->
                        val tint = when (result.state) {
                            NetworkProbeState.SUCCESS -> Color(0xFF22C55E)
                            NetworkProbeState.FAILED -> MaterialTheme.colorScheme.error
                            NetworkProbeState.SKIPPED -> MaterialTheme.colorScheme.tertiary
                        }
                        val stateLabel = when (result.state) {
                            NetworkProbeState.SUCCESS -> "SUCCESS"
                            NetworkProbeState.FAILED -> "FAILED"
                            NetworkProbeState.SKIPPED -> "SKIPPED"
                        }
                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.45f)),
                            border = BorderStroke(1.dp, tint.copy(alpha = 0.35f))
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(result.service, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = tint.copy(alpha = 0.14f)
                                    ) {
                                        Text(
                                            stateLabel,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                            color = tint,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Text(
                                    result.endpoint,
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                    color = MaterialTheme.colorScheme.onSurface.copy(0.55f)
                                )
                                val meta = buildString {
                                    append(result.detail)
                                    result.latencyMs?.let { append(" • ${it}ms") }
                                }
                                Text(
                                    meta,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(0.75f)
                                )
                                result.responsePreview?.let { preview ->
                                    Text(
                                        preview,
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                        color = MaterialTheme.colorScheme.onSurface.copy(0.65f)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = { runNetworkDiagnostics() },
                        enabled = !isRunningNetworkDiagnostics
                    ) { Text(if (networkDiagnosticsResults.isEmpty()) "Run" else "Re-run") }
                    TextButton(
                        onClick = {
                            val report = buildString {
                                append("ChemSearch network diagnostics\n")
                                append("Run at: ${networkDiagnosticsRunAt ?: 0L}\n")
                                append("Success: $successCount, Failed: $failedCount, Skipped: $skippedCount\n\n")
                                networkDiagnosticsResults.forEach { item ->
                                    append("[${item.state}] ${item.service}\n")
                                    append("Endpoint: ${item.endpoint}\n")
                                    append("Status: ${item.detail}\n")
                                    item.latencyMs?.let { append("Latency: ${it}ms\n") }
                                    item.responsePreview?.let { append("Preview: $it\n") }
                                    append("\n")
                                }
                            }
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("network_diagnostics", report))
                            Toast.makeText(context, "Diagnostics copied", Toast.LENGTH_SHORT).show()
                        },
                        enabled = networkDiagnosticsResults.isNotEmpty()
                    ) { Text("Copy") }
                    TextButton(onClick = { showNetworkDialog = false }) { Text("Close") }
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
            text = { Text("This clears legacy preferences, encrypted key records, history, favorites, and debug flags. DataStore settings, Room downloads, and cache files are not deleted. You will need to unlock debug settings again.") },
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
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

            val networkSummary = if (networkDiagnosticsResults.isEmpty()) {
                "Ping PubChem, fallback 3D, Wikipedia, GitHub, and AI providers"
            } else {
                val ok = networkDiagnosticsResults.count { it.state == NetworkProbeState.SUCCESS }
                val fail = networkDiagnosticsResults.count { it.state == NetworkProbeState.FAILED }
                val skipped = networkDiagnosticsResults.count { it.state == NetworkProbeState.SKIPPED }
                "Last run: $ok ok • $fail failed • $skipped skipped"
            }
            SettingsActionRow(
                icon = Icons.Default.Public,
                title = "Network diagnostics",
                subtitle = networkSummary,
                actionLabel = if (isRunningNetworkDiagnostics) "Running..." else "Run",
                actionColor = MaterialTheme.colorScheme.primary,
                onClick = {
                    showNetworkDialog = true
                    if (networkDiagnosticsResults.isEmpty() && !isRunningNetworkDiagnostics) {
                        runNetworkDiagnostics()
                    }
                }
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

            SettingsActionRow(
                icon = Icons.Default.WavingHand,
                title = "Show welcome screen",
                subtitle = "Replay the first-launch intro",
                actionLabel = "Open",
                actionColor = MaterialTheme.colorScheme.primary,
                onClick = {
                    onShowWelcome()
                    Toast.makeText(context, "Welcome screen restored", Toast.LENGTH_SHORT).show()
                }
            )

            // SharedPreferences dump
            SettingsActionRow(
                icon = Icons.Default.Storage,
                title = "Inspect SharedPreferences",
                subtitle = "${prefs.all.size} legacy keys stored · secrets masked",
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
                subtitle = "PubChem · NCI/CADD · Wikipedia · GitHub · AI",
                actionLabel = "Copy",
                actionColor = MaterialTheme.colorScheme.primary,
                onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val endpoints = buildDebugApiEndpointLines().joinToString("\n")
                    cm.setPrimaryClip(ClipData.newPlainText("endpoints", endpoints))
                    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                }
            )

            // Wipe prefs
            SettingsActionRow(
                icon = Icons.Default.DeleteSweep,
                title = "Wipe all SharedPreferences",
                subtitle = "Clears legacy prefs and encrypted key records only",
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

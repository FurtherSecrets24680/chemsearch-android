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
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.furthersecrets.chemsearch.BuildConfig
import com.furthersecrets.chemsearch.R
import com.furthersecrets.chemsearch.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val SENSITIVE_PREF_TOKENS = listOf("key", "token", "secret")

internal fun isOledModeControlEnabled(isDark: Boolean): Boolean = isDark

internal fun amoledModeTitle(): String = "AMOLED Mode"

internal fun amoledModeSubtitle(isDark: Boolean): String =
    if (isDark) "True-black background for every color scheme" else "Turn on dark mode to use AMOLED Mode"

internal fun defaultStructureViewLabel(view: DefaultStructureView): String =
    when (view) {
        DefaultStructureView.TWO_D -> "2D"
        DefaultStructureView.THREE_D -> "3D"
        DefaultStructureView.LAST_USED -> "Last used"
    }

internal fun offlineDownloadQualityLabel(quality: OfflineDownloadQuality): String =
    when (quality) {
        OfflineDownloadQuality.BASIC -> "Basic"
        OfflineDownloadQuality.STRUCTURES -> "Structures"
        OfflineDownloadQuality.COMPLETE -> "Complete"
    }

internal fun formulaDisplayStyleLabel(style: FormulaDisplayStyle): String =
    when (style) {
        FormulaDisplayStyle.CONVENTIONAL -> "Conventional"
        FormulaDisplayStyle.HILL -> "Hill"
    }

internal fun descSourceLabel(source: DescSource): String =
    when (source) {
        DescSource.PUBCHEM -> "PubChem"
        DescSource.WIKI -> "Wikipedia"
        DescSource.AI -> "AI"
    }

internal fun cacheSizeLimitLabel(limit: CacheSizeLimit): String =
    when (limit) {
        CacheSizeLimit.MB_10 -> "10 MB"
        CacheSizeLimit.MB_50 -> "50 MB"
        CacheSizeLimit.MB_100 -> "100 MB"
        CacheSizeLimit.UNLIMITED -> "Unlimited"
    }

internal fun cacheRetentionLabel(retention: CacheRetention): String =
    when (retention) {
        CacheRetention.AUTO_CLEAR_1_DAY -> "Daily"
        CacheRetention.AUTO_CLEAR_7_DAYS -> "Weekly"
        CacheRetention.AUTO_CLEAR_30_DAYS -> "Monthly"
        CacheRetention.MANUAL -> "Manual"
    }

@Composable
fun SettingsDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(14.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 6.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(0.18f)),
        content = content
    )
}

@Composable
private fun <T> SettingsDropdownSelector(
    title: String,
    subtitle: String? = null,
    selected: T,
    options: List<T>,
    labelFor: (T) -> String,
    onSelect: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                )
            }
        }
        Box {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(0.3f)),
                modifier = Modifier
                    .widthIn(min = 104.dp, max = 156.dp)
                    .clickable { expanded = true }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        labelFor(selected),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(0.48f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            SettingsDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    val isSelected = option == selected
                    DropdownMenuItem(
                        text = {
                            Text(
                                labelFor(option),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        },
                        trailingIcon = {
                            if (isSelected) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        },
                        onClick = {
                            expanded = false
                            onSelect(option)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun <T> SettingsSliderSelector(
    title: String,
    subtitle: String? = null,
    selected: T,
    options: List<T>,
    labelFor: (T) -> String,
    onSelect: (T) -> Unit
) {
    val selectedIndex = options.indexOf(selected).coerceAtLeast(0)
    var sliderValue by remember(selectedIndex, options.size) { mutableFloatStateOf(selectedIndex.toFloat()) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(0.45f)
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            options.forEachIndexed { index, option ->
                Text(
                    labelFor(option),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (index == selectedIndex) FontWeight.Bold else FontWeight.Medium,
                    color = if (index == selectedIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(0.45f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Slider(
            value = sliderValue,
            onValueChange = { value ->
                val index = value.roundToInt().coerceIn(0, options.lastIndex)
                sliderValue = index.toFloat()
                if (options[index] != selected) onSelect(options[index])
            },
            valueRange = 0f..options.lastIndex.toFloat(),
            steps = (options.size - 2).coerceAtLeast(0),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.outline.copy(0.28f),
                activeTickColor = MaterialTheme.colorScheme.onPrimary.copy(0.72f),
                inactiveTickColor = MaterialTheme.colorScheme.onSurface.copy(0.32f)
            )
        )
    }
}

private fun updateDownloadPercent(progress: Float?): Int =
    ((progress ?: 0f).coerceIn(0f, 1f) * 100f).toInt().coerceIn(0, 100)

internal fun updateDownloadActionLabel(status: UpdateStatus): String =
    when {
        status.isDownloadingUpdate -> ""
        status.downloadedUpdateApkPath != null -> "Install"
        else -> "Download"
    }

internal fun updateDownloadSubtitle(status: UpdateStatus): String {
    if (status.isDownloadingUpdate) {
        return "Downloading update (${updateDownloadPercent(status.updateDownloadProgress)}%)"
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

@Composable
private fun ColorSchemePicker(
    colorScheme: AppColorScheme,
    onSetColorScheme: (AppColorScheme) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        AppColorScheme.entries.forEach { scheme ->
            val selected = colorScheme == scheme
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSetColorScheme(scheme) }
                    .padding(vertical = 3.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(
                            if (selected) scheme.previewColor().copy(0.14f) else Color.Transparent,
                            CircleShape
                        )
                        .border(
                            width = 1.dp,
                            color = if (selected) scheme.previewColor().copy(0.72f) else Color.Transparent,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .background(scheme.previewColor(), CircleShape)
                            .border(1.dp, MaterialTheme.colorScheme.background.copy(0.28f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
                Text(
                    scheme.label(),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    lineHeight = 11.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (selected) scheme.previewColor() else MaterialTheme.colorScheme.onSurface.copy(0.62f),
                    modifier = Modifier.fillMaxWidth()
                )
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
            SettingsDropdownMenu(
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
            SettingsDropdownMenu(
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
    defaultStructureView: DefaultStructureView = DefaultStructureView.TWO_D,
    formulaDisplayStyle: FormulaDisplayStyle = FormulaDisplayStyle.CONVENTIONAL,
    reduceMotion: Boolean = false,
    highContrastOutlines: Boolean = false,
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
    onSetDefaultStructureView: (DefaultStructureView) -> Unit = {},
    onSetFormulaDisplayStyle: (FormulaDisplayStyle) -> Unit = {},
    onToggleReduceMotion: () -> Unit = {},
    onToggleHighContrastOutlines: () -> Unit = {},
    onSetAiProvider: (AiProvider) -> Unit,
    onSetAiModel: (AiProvider, String) -> Unit,
    onRefreshAiModels: (AiProvider) -> Unit,
    onEditAiKey: (AiProvider) -> Unit,
    onClearAiKey: (AiProvider) -> Unit,
    onClearHistory: () -> Unit,
    onToggleUpdateNotifications: (Boolean) -> Unit,
    onCheckForUpdates: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onOpenAbout: () -> Unit = {},
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
                icon = Icons.Default.GridView,
                title = "Compact mode",
                subtitle = "Show more content per screen",
                checked = compactMode,
                onToggle = onToggleCompactMode
            )
            SettingsToggleRow(
                icon = Icons.Default.VisibilityOff,
                title = "Reduce motion",
                subtitle = "Use calmer transitions and resizing",
                checked = reduceMotion,
                onToggle = onToggleReduceMotion
            )
            SettingsToggleRow(
                icon = Icons.Default.Visibility,
                title = "High contrast outlines",
                subtitle = "Make cards and controls easier to separate",
                checked = highContrastOutlines,
                onToggle = onToggleHighContrastOutlines
            )
            SettingsDropdownSelector(
                title = "Default structure view",
                subtitle = "Choose which structure tab opens first after search",
                selected = defaultStructureView,
                options = DefaultStructureView.entries,
                labelFor = ::defaultStructureViewLabel,
                onSelect = onSetDefaultStructureView
            )
            SettingsDropdownSelector(
                title = "Formula display",
                subtitle = "Conventional uses common element order; Hill keeps PubChem/Hill order",
                selected = formulaDisplayStyle,
                options = FormulaDisplayStyle.entries,
                labelFor = ::formulaDisplayStyleLabel,
                onSelect = onSetFormulaDisplayStyle
            )

            Spacer(Modifier.height(4.dp))
            SettingsSectionHeader("Default Description Source")
            Text(
                "Automatically shown when you search a compound",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(0.5f),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            SettingsDropdownSelector(
                title = "Source",
                subtitle = "Choose which description type appears first",
                selected = defaultDescSource,
                options = DescSource.entries,
                labelFor = ::descSourceLabel,
                onSelect = onSetDefaultDesc
            )

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

            if (BuildConfig.GITHUB_UPDATES_ENABLED) {
                UpdatesSection(
                    updateNotificationsEnabled = updateNotificationsEnabled,
                    updateStatus = updateStatus,
                    onToggleUpdateNotifications = onToggleUpdateNotifications,
                    onCheckForUpdates = onCheckForUpdates,
                    onDownloadUpdate = onDownloadUpdate
                )
            } else {
                FdroidUpdatesSection()
            }

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
            SettingsActionRow(
                icon = Icons.Default.Info,
                title = "About ChemSearch",
                subtitle = "App info, links, data sources, and credits",
                actionLabel = "Open",
                actionColor = MaterialTheme.colorScheme.primary,
                onClick = onOpenAbout
            )
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
                if (actionLabel.isNotBlank()) {
                    AnimatedActionLabel(
                        text = actionLabel,
                        color = if (enabled) actionColor else MaterialTheme.colorScheme.onSurface.copy(0.35f)
                    )
                }
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
private fun FdroidUpdatesSection(
    showHeader: Boolean = true
) {
    if (showHeader) {
        Spacer(Modifier.height(4.dp))
        SettingsSectionHeader("Updates")
    }
    SettingsActionRow(
        icon = Icons.Default.SystemUpdate,
        title = "Updates",
        subtitle = "F-Droid handles updates for this build.",
        actionLabel = "F-Droid",
        actionColor = MaterialTheme.colorScheme.onSurface.copy(0.45f),
        enabled = false,
        onClick = {}
    )
}

@Composable
private fun AboutCard(
    onVersionTap: (() -> Unit)? = null,
    onOpenLegalDocument: (LegalDocument) -> Unit = {}
) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        AboutHero(onVersionTap = onVersionTap)
        AboutLegalSection(onOpenDocument = onOpenLegalDocument)
        AboutSection(
            title = "APP LINKS",
            entries = aboutAppLinks,
            iconFor = ::aboutAppLinkIcon
        )
        AboutSection(
            title = "CHEMISTRY DATA",
            entries = aboutDataCredits,
            iconFor = ::aboutDataCreditIcon
        )
        AboutSection(
            title = "AI PROVIDERS",
            entries = aboutAiProviderCredits,
            iconFor = { Icons.Default.SmartToy }
        )
        AboutSection(
            title = "BUILT WITH",
            entries = aboutTechnologyCredits,
            iconFor = ::aboutTechnologyCreditIcon
        )
    }
}

@Composable
private fun AboutHero(onVersionTap: (() -> Unit)?) {
    val versionModifier = if (onVersionTap != null) {
        Modifier.clickable { onVersionTap() }
    } else {
        Modifier
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primary.copy(0.08f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(0.18f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(0.18f)),
                tonalElevation = 0.dp,
                shadowElevation = 3.dp
            ) {
                Image(
                    painter = painterResource(R.drawable.chemsearch),
                    contentDescription = "ChemSearch app icon",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(128.dp)
                        .padding(10.dp)
                )
            }
            Text(
                "ChemSearch",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "Chemistry simplified for Android.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(0.66f)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                "Search compounds, draw structures, view 2D/3D models, save offline data, compare compounds, and use chemistry tools.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(0.58f)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Code, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Text(
                    "Built by FurtherSecrets",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                "Package: ${BuildConfig.APPLICATION_ID}",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
            )
        }
    }
}

@Composable
fun AboutScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp)
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("chemsearch_prefs", Context.MODE_PRIVATE) }
    var buildTapCount by remember { mutableIntStateOf(0) }
    var selectedLegalDocument by remember { mutableStateOf<LegalDocument?>(null) }

    selectedLegalDocument?.let { document ->
        LegalDocumentDialog(
            document = document,
            onDismiss = { selectedLegalDocument = null }
        )
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(42.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.primary)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "About ChemSearch",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.sp
                    )
                    Text(
                        "App info, links, data sources, and credits.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.56f)
                    )
                }
            }
        }

        item {
            AboutCard(
                onVersionTap = {
                    buildTapCount++
                    when (buildTapCount) {
                        3 -> Toast.makeText(context, "2 more taps to unlock debug settings", Toast.LENGTH_SHORT).show()
                        4 -> Toast.makeText(context, "1 more tap to unlock debug settings", Toast.LENGTH_SHORT).show()
                        5 -> {
                            prefs.edit().putBoolean("dev_mode", true).apply()
                            Toast.makeText(context, "Debug settings unlocked", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onOpenLegalDocument = { selectedLegalDocument = it }
            )
        }
    }
}

@Composable
private fun AboutLegalSection(onOpenDocument: (LegalDocument) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "LEGAL AND SAFETY",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface.copy(0.4f)
        )
        legalDocuments.forEach { document ->
            AboutLegalRow(document = document, onClick = { onOpenDocument(document) })
        }
    }
}

@Composable
private fun AboutLegalRow(document: LegalDocument, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.primary.copy(0.1f)
        ) {
            Icon(
                legalDocumentIcon(document.type),
                null,
                modifier = Modifier.padding(8.dp).size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                document.title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                document.summary,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(0.55f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(0.36f)
        )
    }
}

@Composable
private fun AboutSection(
    title: String,
    entries: List<AboutCreditEntry>,
    iconFor: (AboutCreditEntry) -> ImageVector
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface.copy(0.4f)
        )
        entries.forEach { entry ->
            AboutSourceRow(entry = entry, icon = iconFor(entry))
        }
    }
}

@Composable
private fun AboutSourceRow(entry: AboutCreditEntry, icon: ImageVector) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(entry.url))) }
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.primary.copy(0.1f)
        ) {
            Icon(
                icon,
                null,
                modifier = Modifier.padding(8.dp).size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                entry.title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                entry.detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(0.55f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.OpenInNew,
            null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(0.36f)
        )
    }
}

private fun aboutAppLinkIcon(entry: AboutCreditEntry): ImageVector =
    when (entry.title) {
        "GitHub repository" -> Icons.Default.Code
        "Latest release" -> Icons.Default.SystemUpdate
        "Wiki" -> Icons.AutoMirrored.Filled.MenuBook
        "Issue tracker" -> Icons.Default.BugReport
        "Product Hunt" -> Icons.Default.Public
        "License" -> Icons.Default.Description
        else -> Icons.AutoMirrored.Filled.OpenInNew
    }

private fun aboutDataCreditIcon(entry: AboutCreditEntry): ImageVector =
    when {
        entry.title.contains("PubChem") -> Icons.Default.Storage
        entry.title.contains("Wikipedia") -> Icons.Default.Public
        entry.title.contains("Bowserinator") -> Icons.AutoMirrored.Filled.MenuBook
        entry.title.contains("NCI") -> Icons.Default.Science
        entry.title.contains("IUPAC") -> Icons.AutoMirrored.Filled.MenuBook
        entry.title.contains("GHS") -> Icons.Default.HealthAndSafety
        else -> Icons.Default.Info
    }

private fun aboutTechnologyCreditIcon(entry: AboutCreditEntry): ImageVector =
    when {
        entry.title.contains("Compose") || entry.title.contains("Material") -> Icons.Default.Palette
        entry.title.contains("Navigation") -> Icons.AutoMirrored.Filled.ArrowForward
        entry.title.contains("Room") || entry.title.contains("DataStore") -> Icons.Default.Storage
        entry.title.contains("WorkManager") -> Icons.Default.Cached
        entry.title.contains("Retrofit") || entry.title.contains("OkHttp") -> Icons.Default.Hub
        entry.title.contains("Coil") -> Icons.Default.Visibility
        entry.title.contains("Gson") -> Icons.Default.Code
        entry.title.contains("Coroutines") -> Icons.Default.Bolt
        entry.title.contains("Phosphor") -> Icons.Default.Star
        else -> Icons.Default.Code
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
    "What is ChemSearch for?" to "ChemSearch is for quick chemistry lookup, study, and reference. It brings compound search, structures, safety summaries, offline saving, references, and calculators into one Android app.",
    "Is ChemSearch a replacement for PubChem?" to "No. ChemSearch uses PubChem as a main data source and presents useful parts in a phone-friendly way. Open PubChem directly when you need the full record or original source trail.",
    "Where does compound data come from?" to "Most compound properties, identifiers, structures, synonyms, classifications, and GHS summaries come from PubChem. Some descriptions can come from Wikipedia or your selected AI provider, depending on settings.",
    "Where does periodic table data come from?" to "Periodic table details are bundled from public reference data, including PubChem, Wikipedia, and Bowserinator/Periodic-Table-JSON. Some fields are hidden when a value is not listed.",
    "Can I trust the data?" to "Use ChemSearch for study and quick checks. Chemical databases can disagree because names, tautomers, salts, hydrates, stereochemistry, and standardization rules are complicated. For lab, medical, legal, or regulatory decisions, verify with official sources.",
    "Is the safety information official?" to "No. GHS cards are quick reference summaries from PubChem data. They do not replace a supplier Safety Data Sheet, lab policy, teacher instructions, or local safety rules.",
    "Why can safety data be missing or different from an SDS?" to "Safety data depends on source, purity, mixture, concentration, physical form, region, and update date. A supplier SDS is written for a specific product; ChemSearch usually shows substance-level reference data.",
    "Does ChemSearch give lab procedure or synthesis advice?" to "No. ChemSearch is for lookup and learning. It does not verify experimental procedures, exposure limits, dosages, storage compatibility, or safe handling steps.",
    "What can I search for?" to "You can search by common name, IUPAC name, CAS number, PubChem CID, formula, or drawn structure. Some modes work better for exact identifiers than short names.",
    "Why did my search fail?" to "Try a spelling correction, CID, CAS number, or a more specific name. Very broad names, unofficial names, unstable structures, and compounds absent from PubChem may not return a useful match.",
    "Why are Did you mean suggestions sometimes missing?" to "ChemSearch only shows close spelling matches. It hides loose PubChem autocomplete results when they look unrelated, so it may show nothing instead of a bad suggestion.",
    "What is the difference between normal search and isomer search?" to "Normal search tries to find one best compound. Isomer Search treats the input as a molecular formula and lists compounds with the same formula.",
    "Why do formulas sometimes look different from textbooks?" to "ChemSearch can show formulas in different styles. Hill order sorts carbon, hydrogen, then the rest alphabetically. Conventional order tries to show the familiar chemistry form, such as NaCl or H2SO4.",
    "Why do ions sometimes need charges shown separately?" to "Formula text and charge text are different pieces of data. ChemSearch tries to keep charges visible with superscripts, but some source records may store ions in unusual formats.",
    "Why does a 2D structure not show every hydrogen?" to "Many chemical diagrams omit hydrogens on carbon unless they matter for clarity. That is normal for skeletal and PubChem-style 2D structures.",
    "Why is a 3D model missing?" to "Some compounds do not have a PubChem 3D conformer. Salts, metals, coordination compounds, ionic records, very flexible molecules, and very large molecules are common cases.",
    "Are 3D models exact real shapes?" to "No. PubChem 3D conformers are computed models, not guaranteed experimental structures. They are useful for visualization, but they may not represent the exact shape in a crystal, solvent, protein pocket, or classroom model.",
    "Why do bond orders look different in 3D?" to "3D coordinate files focus on atom positions and connectivity. Bond order, aromaticity, charge placement, and tautomer form can be interpreted differently by different tools.",
    "What does Structure Search do?" to "Structure Search sends the drawn molecule as a structure query and looks for matching PubChem compounds. Invalid valence, incomplete drawings, or very complex sketches may fail.",
    "Why does Structure Search return a different compound than expected?" to "Small drawing differences can change the search, especially with charges, aromatic rings, tautomers, stereochemistry, salts, and implicit hydrogens. Use exact identifiers when you need one specific record.",
    "Can AI descriptions be wrong?" to "Yes. AI text can sound confident while missing details or mixing facts. Use AI descriptions as a plain-language helper, not as the final source for safety, exams, lab work, or citations.",
    "What data is sent to AI providers?" to "Only when you request an AI description, ChemSearch sends compound context to the provider you selected. That provider handles the request under its own terms and privacy policy.",
    "Do I need an API key for AI descriptions?" to "Yes. Google Gemini, Groq Cloud, OpenAI, OpenRouter, and Mistral AI each need their own API key. Add keys in Settings > AI Provider & Keys.",
    "Where are API keys stored?" to "API keys are stored locally with Android Keystore-backed encryption. ChemSearch does not sync them to a ChemSearch account.",
    "Does ChemSearch collect my searches?" to "ChemSearch has no account system. Search history, favorites, downloads, and cache are stored on your device. Live searches still contact external services such as PubChem, Wikipedia, GitHub updates, or your chosen AI provider when needed.",
    "What works offline?" to "The built-in Library references, calculators, favorites, and downloaded compounds can work offline. Live compound lookup, fresh descriptions, structure search, update checks, and AI descriptions need internet access.",
    "What is the difference between cache and Downloads?" to "Cache makes repeat searches faster and may be cleared automatically. Downloads are intentional offline compound saves with identifiers, descriptions, structures, synonyms, safety info, and source data when available.",
    "Where are downloaded compounds stored?" to "Downloaded compounds are stored in the app's local Room database on your device. Uninstalling the app can remove them unless you export or back them up first.",
    "How do I save a compound?" to "Use the star for Favorites or the download button for a full offline save. Favorites are quick bookmarks; Downloads are meant for offline reading.",
    "Can I compare saved compounds?" to "Yes. Select two or more compounds in Favorites, Downloads, or supported Chemical Database entries, then use the floating Compare button.",
    "What is the Chemical Database?" to "It is a bundled reference for common substances, ions, functional groups, and reactions. It is useful offline, but it is not as broad as PubChem.",
    "Why can I compare substances and ions but not reactions or functional groups?" to "Compare Compounds expects compound-like records with formulas and identifiers. Reactions and functional groups are reference entries, not full compound records.",
    "Why do some classification tags look unrelated?" to "Classification and annotation tags come from source records. A tag can describe a data context, literature category, assay topic, or indexing term; it does not always mean the compound is mainly used for that topic.",
    "Why are some names very long or truncated?" to "Some IUPAC names and biomolecule names are too long for compact screens. Tap the name or expand control when available to see the full text.",
    "How is molar mass calculated?" to "The calculator sums standard atomic weights from the parsed formula. Parentheses, nested groups, hydrates, and common formula notation are supported.",
    "Why can oxidation states be wrong for unusual formulas?" to "Oxidation state rules need assumptions about bonds, charges, and known exceptions. The tool handles common chemistry, but coordination compounds and ambiguous formulas may need manual checking.",
    "Why can reaction balancing fail?" to "The balancer uses formula parsing and exact arithmetic. It may fail when formulas are invalid, charges are missing, or the reaction needs chemistry context beyond atom conservation.",
    "Can I use the tools for homework?" to "Yes, as a checker and learning aid. Still show your work, because the app gives answers and summaries, not your teacher's required reasoning steps.",
    "How do app updates work?" to "ChemSearch can check GitHub releases, download the APK inside the app, and then hand it to Android's installer. You can also update manually from GitHub.",
    "Are update notifications optional?" to "Yes. You can turn update notifications on or off in Settings and still check manually whenever you want.",
    "How do I clear history or cache?" to "Open Settings > Data. You can clear recent searches, manage cache size, choose auto-clear timing, and remove saved temporary data.",
    "How do I unlock debug settings?" to "Tap the build number on the About screen five times. Debug settings are for diagnostics, endpoint checks, logs, and development testing."
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
    selectionItem: LibrarySelectionItem? = null,
    selected: Boolean = false,
    onToggleSelection: (LibrarySelectionItem) -> Unit = {},
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {}
) {
    val compact = LocalCompactMode.current
    Card(
        onClick = { if (enableSelect) onSelect(favorite.name) },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(0.42f)) else null
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
            if (selectionItem != null && !showReorderControls) {
                LibrarySelectionToggle(
                    selected = selected,
                    onClick = { onToggleSelection(selectionItem) }
                )
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

private enum class LibraryTab { FAVORITES, DOWNLOADS, DATABASE, PERIODIC_TABLE }
private enum class LibraryViewMode { LIST, GRID }

private data class LibraryOption(
    val tab: LibraryTab,
    val icon: ChemIconSpec,
    val title: String,
    val subtitle: String,
    val countLabel: String? = null
)

private fun ChemicalDatabaseSummary.totalEntries(): Int =
    substances + ions + functionalGroups + reactions

private fun LibraryTab.icon(): ChemIconSpec = when (this) {
    LibraryTab.FAVORITES -> ChemAppIcons.Star
    LibraryTab.DOWNLOADS -> ChemAppIcons.Download
    LibraryTab.DATABASE -> ChemAppIcons.Library
    LibraryTab.PERIODIC_TABLE -> ChemAppIcons.Atom
}

@Composable
private fun LibraryHomeSectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        modifier = modifier.fillMaxWidth(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun LibraryOptionGridRows(
    options: List<LibraryOption>,
    onSelect: (LibraryTab) -> Unit
) {
    options.chunked(2).forEach { rowItems ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            rowItems.forEach { option ->
                LibraryOptionCard(
                    icon = option.icon,
                    title = option.title,
                    subtitle = option.subtitle,
                    countLabel = option.countLabel,
                    selected = false,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelect(option.tab) }
                )
            }
            if (rowItems.size == 1) Spacer(Modifier.weight(1f))
        }
    }
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
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val compact = LocalCompactMode.current
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
private fun LibrarySelectionToggle(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val compact = LocalCompactMode.current
    IconButton(
        onClick = onClick,
        modifier = modifier.size(if (compact) 30.dp else 34.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
            border = BorderStroke(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(0.48f)
            ),
            modifier = Modifier.size(if (compact) 22.dp else 24.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (selected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(if (compact) 13.dp else 14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryGridCard(
    favorite: FavoriteCompound,
    onSelect: (String) -> Unit,
    onDelete: (Long) -> Unit,
    modifier: Modifier = Modifier,
    offlineMetadata: OfflineDownloadMetadata? = null,
    selectionItem: LibrarySelectionItem? = null,
    selected: Boolean = false,
    onToggleSelection: (LibrarySelectionItem) -> Unit = {}
) {
    val compact = LocalCompactMode.current
    Card(
        onClick = { onSelect(favorite.name) },
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(if (compact) 0.88f else 0.92f),
        shape = RoundedCornerShape(if (compact) 16.dp else 18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(0.42f)) else null
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
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                    selectionItem?.let { item ->
                        LibrarySelectionToggle(
                            selected = selected,
                            onClick = { onToggleSelection(item) }
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
    onSearchCompoundFromDatabase: (String) -> Unit = {},
    onCompareSelected: (List<String>) -> Unit = {},
    onBuildLibraryBackupJson: () -> String = { "" },
    onImportLibraryBackup: (String, Boolean, (Result<LibraryImportResult>) -> Unit) -> Unit = { _, _, _ -> }
) {
    var selectedSection by remember { mutableStateOf<LibraryTab?>(null) }
    var homeViewMode by remember { mutableStateOf(LibraryViewMode.LIST) }
    var itemViewMode by remember { mutableStateOf(LibraryViewMode.LIST) }
    var filterQuery by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf(FavoritesSort.RECENT) }
    var isReordering by remember { mutableStateOf(false) }
    val selectedLibraryItems = remember { mutableStateListOf<LibrarySelectionItem>() }
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val databaseEntries = remember(context) { ChemicalDatabase.load(context) }
    val databaseSummary = remember(databaseEntries) { summarizeChemicalDatabase(databaseEntries) }
    val selectedLibraryKeys = selectedLibraryItems.map { it.key }.toSet()
    var pendingLibraryImportJson by remember { mutableStateOf<String?>(null) }

    val exportLibraryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val json = onBuildLibraryBackupJson()
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                writer.write(json)
            } ?: error("Unable to open file for export")
        }.onSuccess {
            Toast.makeText(context, "Library exported", Toast.LENGTH_SHORT).show()
        }.onFailure { e ->
            Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    val importLibraryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                reader.readText()
            } ?: error("Unable to open file for import")
        }.onSuccess {
            pendingLibraryImportJson = it
        }.onFailure { e ->
            Toast.makeText(context, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    pendingLibraryImportJson?.let { rawJson ->
        AlertDialog(
            onDismissRequest = { pendingLibraryImportJson = null },
            title = { Text("Import Library", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Merge the backup with your current Library, or replace current favorites and downloads with the backup file.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.65f)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingLibraryImportJson = null
                        onImportLibraryBackup(rawJson, false) { result ->
                            result.onSuccess { imported ->
                                Toast.makeText(
                                    context,
                                    "Imported ${imported.favoriteCount} favorites and ${imported.downloadCount} downloads",
                                    Toast.LENGTH_LONG
                                ).show()
                            }.onFailure { e ->
                                Toast.makeText(context, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Merge") }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { pendingLibraryImportJson = null }) { Text("Cancel") }
                    TextButton(
                        onClick = {
                            pendingLibraryImportJson = null
                            onImportLibraryBackup(rawJson, true) { result ->
                                result.onSuccess { imported ->
                                    Toast.makeText(
                                        context,
                                        "Replaced Library with ${imported.favoriteCount} favorites and ${imported.downloadCount} downloads",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }.onFailure { e ->
                                    Toast.makeText(context, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    ) { Text("Replace") }
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    fun toggleLibrarySelection(item: LibrarySelectionItem) {
        val existingIndex = selectedLibraryItems.indexOfFirst { it.key == item.key }
        if (existingIndex >= 0) {
            selectedLibraryItems.removeAt(existingIndex)
        } else {
            selectedLibraryItems.add(item)
        }
    }

    LaunchedEffect(selectedSection, favorites.size) {
        if (selectedSection != LibraryTab.FAVORITES || favorites.size < 2) isReordering = false
        filterQuery = ""
        sortMode = FavoritesSort.RECENT
        focusManager.clearFocus()
    }

    LaunchedEffect(favorites, downloads, databaseEntries) {
        val validKeys = buildSet {
            favorites.forEach { add(it.toLibrarySelectionItem().key) }
            downloads.forEach { add(it.toLibrarySelectionItem().key) }
            databaseEntries.mapNotNull { it.toComparableLibrarySelectionItem() }.forEach { add(it.key) }
        }
        selectedLibraryItems.removeAll { it.key !in validKeys }
    }

    val savedLibraryOptions = remember(favorites.size, downloads.size) {
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
            )
        )
    }
    val referenceLibraryOptions = remember(databaseSummary) {
        listOf(
            LibraryOption(
                tab = LibraryTab.PERIODIC_TABLE,
                icon = LibraryTab.PERIODIC_TABLE.icon(),
                title = "Periodic Table",
                subtitle = "All 118 elements offline",
                countLabel = PeriodicTableElements.size.toString()
            ),
            LibraryOption(
                tab = LibraryTab.DATABASE,
                icon = LibraryTab.DATABASE.icon(),
                title = "Chemical Database",
                subtitle = "Substances, ions, groups, reactions",
                countLabel = databaseSummary.totalEntries().toString()
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding)
    ) {
    Column(
        modifier = Modifier.fillMaxSize(),
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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = { exportLibraryLauncher.launch("chemsearch-library-${System.currentTimeMillis()}.json") },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Default.Description, contentDescription = "Export Library", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(
                        onClick = { importLibraryLauncher.launch(arrayOf("application/json", "text/plain")) },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "Import Library", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                    LibraryViewToggle(viewMode = homeViewMode, onViewModeChange = { homeViewMode = it })
                }
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
                    if (selectedSection == LibraryTab.FAVORITES || selectedSection == LibraryTab.DOWNLOADS) {
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Open saved compounds, offline copies, the periodic table, or the chemical database.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                )
                if (homeViewMode == LibraryViewMode.LIST) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        savedLibraryOptions.forEach { option ->
                            LibraryOptionListCard(
                                icon = option.icon,
                                title = option.title,
                                subtitle = option.subtitle,
                                countLabel = option.countLabel,
                                onClick = { selectedSection = option.tab }
                            )
                        }
                        LibraryHomeSectionTitle("Reference", modifier = Modifier.padding(top = 8.dp))
                        referenceLibraryOptions.forEach { option ->
                            LibraryOptionListCard(
                                icon = option.icon,
                                title = option.title,
                                subtitle = option.subtitle,
                                countLabel = option.countLabel,
                                onClick = { selectedSection = option.tab }
                            )
                        }
                    }
                } else {
                    LibraryOptionGridRows(
                        options = savedLibraryOptions,
                        onSelect = { selectedSection = it }
                    )
                    LibraryHomeSectionTitle("Reference", modifier = Modifier.padding(top = 8.dp))
                    LibraryOptionGridRows(
                        options = referenceLibraryOptions,
                        onSelect = { selectedSection = it }
                    )
                }
            }
            return@Column
        }

        val section = selectedSection ?: return@Column
        val sectionTitle = when (section) {
            LibraryTab.FAVORITES -> "Favorites"
            LibraryTab.DOWNLOADS -> "Downloads"
            LibraryTab.DATABASE -> "Chemical Database"
            LibraryTab.PERIODIC_TABLE -> "Periodic Table"
        }
        val sectionSubtitle = when (section) {
            LibraryTab.FAVORITES -> "Saved quick links on this device."
            LibraryTab.DOWNLOADS -> "Offline compound copies with saved structures and data."
            LibraryTab.DATABASE -> "Browse substances, reactions, functional groups, and ions."
            LibraryTab.PERIODIC_TABLE -> "Browse all elements, groups, masses, and common oxidation states."
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
                onSearchCompound = onSearchCompoundFromDatabase,
                selectedLibraryKeys = selectedLibraryKeys,
                onToggleLibrarySelection = ::toggleLibrarySelection
            )
            return@Column
        }

        if (section == LibraryTab.PERIODIC_TABLE) {
            PeriodicTableLibraryScreen(
                modifier = Modifier.weight(1f)
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
                            val selectionItem = fav.toLibrarySelectionItem()
                            FavoriteCard(
                                favorite = fav,
                                onSelect = onSelectFavorite,
                                onDelete = onDeleteFavorite,
                                enableSelect = !isReordering,
                                showReorderControls = isReordering,
                                canMoveUp = isReordering && index > 0,
                                canMoveDown = isReordering && index < displayFavorites.lastIndex,
                                selectionItem = if (isReordering) null else selectionItem,
                                selected = selectionItem.key in selectedLibraryKeys,
                                onToggleSelection = ::toggleLibrarySelection,
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
                                    val selectionItem = fav.toLibrarySelectionItem()
                                    LibraryGridCard(
                                        favorite = fav,
                                        onSelect = onSelectFavorite,
                                        onDelete = onDeleteFavorite,
                                        modifier = Modifier.weight(1f),
                                        selectionItem = selectionItem,
                                        selected = selectionItem.key in selectedLibraryKeys,
                                        onToggleSelection = ::toggleLibrarySelection
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
                            val selectionItem = item.toLibrarySelectionItem()
                            FavoriteCard(
                                favorite = item.toFavoriteCardData(),
                                onSelect = { onSelectDownload(item.cid) },
                                onDelete = onDeleteDownload,
                                offlineMetadata = item.offlineMetadata,
                                selectionItem = selectionItem,
                                selected = selectionItem.key in selectedLibraryKeys,
                                onToggleSelection = ::toggleLibrarySelection
                            )
                        }
                    } else {
                        filteredDownloads.chunked(2).forEach { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowItems.forEach { item ->
                                    val selectionItem = item.toLibrarySelectionItem()
                                    LibraryGridCard(
                                        favorite = item.toFavoriteCardData(),
                                        onSelect = { onSelectDownload(item.cid) },
                                        onDelete = onDeleteDownload,
                                        modifier = Modifier.weight(1f),
                                        offlineMetadata = item.offlineMetadata,
                                        selectionItem = selectionItem,
                                        selected = selectionItem.key in selectedLibraryKeys,
                                        onToggleSelection = ::toggleLibrarySelection
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
    if (shouldShowLibraryCompareButton(selectedLibraryItems.size)) {
        ExtendedFloatingActionButton(
            onClick = {
                val queries = buildLibraryCompareQueries(selectedLibraryItems)
                if (queries.size >= 2) {
                    selectedLibraryItems.clear()
                    onCompareSelected(queries)
                } else {
                    Toast.makeText(context, "Select two different compounds to compare", Toast.LENGTH_SHORT).show()
                }
            },
            icon = {
                Icon(Icons.AutoMirrored.Filled.CompareArrows, contentDescription = null, modifier = Modifier.size(18.dp))
            },
            text = {
                Text("Compare", fontWeight = FontWeight.Bold)
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 10.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
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
    defaultStructureView: DefaultStructureView = DefaultStructureView.TWO_D,
    offlineDownloadQuality: OfflineDownloadQuality = OfflineDownloadQuality.COMPLETE,
    formulaDisplayStyle: FormulaDisplayStyle = FormulaDisplayStyle.CONVENTIONAL,
    cacheSizeLimit: CacheSizeLimit = CacheSizeLimit.UNLIMITED,
    cacheRetention: CacheRetention = CacheRetention.MANUAL,
    reduceMotion: Boolean = false,
    highContrastOutlines: Boolean = false,
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
    onSetDefaultStructureView: (DefaultStructureView) -> Unit = {},
    onSetOfflineDownloadQuality: (OfflineDownloadQuality) -> Unit = {},
    onSetFormulaDisplayStyle: (FormulaDisplayStyle) -> Unit = {},
    onSetCacheSizeLimit: (CacheSizeLimit) -> Unit = {},
    onSetCacheRetention: (CacheRetention) -> Unit = {},
    onToggleReduceMotion: () -> Unit = {},
    onToggleHighContrastOutlines: () -> Unit = {},
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
    onOpenAbout: () -> Unit = {},
    onSettingsImported: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("chemsearch_prefs", Context.MODE_PRIVATE) }
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
                    SettingsDropdownMenu(
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
                icon = Icons.Default.GridView,
                title = "Compact mode",
                subtitle = "Show more content per screen",
                checked = compactMode,
                onToggle = onToggleCompactMode
            )
            SettingsGroupDivider()
            SettingsToggleRow(
                icon = Icons.Default.VisibilityOff,
                title = "Reduce motion",
                subtitle = "Use calmer transitions and resizing",
                checked = reduceMotion,
                onToggle = onToggleReduceMotion
            )
            SettingsGroupDivider()
            SettingsToggleRow(
                icon = Icons.Default.Visibility,
                title = "High contrast outlines",
                subtitle = "Make cards and controls easier to separate",
                checked = highContrastOutlines,
                onToggle = onToggleHighContrastOutlines
            )
            SettingsGroupDivider()
            SettingsDropdownSelector(
                title = "Default structure view",
                subtitle = "Choose which structure tab opens first after search",
                selected = defaultStructureView,
                options = DefaultStructureView.entries,
                labelFor = ::defaultStructureViewLabel,
                onSelect = onSetDefaultStructureView
            )
            SettingsGroupDivider()
            SettingsDropdownSelector(
                title = "Formula display",
                subtitle = "Conventional uses common element order; Hill keeps PubChem/Hill order",
                selected = formulaDisplayStyle,
                options = FormulaDisplayStyle.entries,
                labelFor = ::formulaDisplayStyleLabel,
                onSelect = onSetFormulaDisplayStyle
            )
            SettingsGroupDivider()
            SettingsDropdownSelector(
                title = "Default description source",
                subtitle = "Choose which description type appears first",
                selected = defaultDescSource,
                options = DescSource.entries,
                labelFor = ::descSourceLabel,
                onSelect = onSetDefaultDesc
            )
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
            SettingsDropdownSelector(
                title = "Offline download quality",
                subtitle = "Pick how much extra data ChemSearch saves for offline viewing",
                selected = offlineDownloadQuality,
                options = OfflineDownloadQuality.entries,
                labelFor = ::offlineDownloadQualityLabel,
                onSelect = onSetOfflineDownloadQuality
            )
            SettingsGroupDivider()
            SettingsSliderSelector(
                title = "Cache size limit",
                subtitle = "Limit temporary compound cache storage",
                selected = cacheSizeLimit,
                options = CacheSizeLimit.entries,
                labelFor = ::cacheSizeLimitLabel,
                onSelect = onSetCacheSizeLimit
            )
            SettingsGroupDivider()
            SettingsDropdownSelector(
                title = "Auto-clear cache",
                subtitle = "Remove old temporary cache files on this schedule",
                selected = cacheRetention,
                options = CacheRetention.entries,
                labelFor = ::cacheRetentionLabel,
                onSelect = onSetCacheRetention
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
            subtitle = if (BuildConfig.GITHUB_UPDATES_ENABLED) {
                "Control update checks and open support resources."
            } else {
                "F-Droid updates and support resources."
            }
        ) {
            if (BuildConfig.GITHUB_UPDATES_ENABLED) {
                UpdatesSection(
                    updateNotificationsEnabled = updateNotificationsEnabled,
                    updateStatus = updateStatus,
                    onToggleUpdateNotifications = onToggleUpdateNotifications,
                    onCheckForUpdates = onCheckForUpdates,
                    onDownloadUpdate = onDownloadUpdate,
                    showHeader = false
                )
            } else {
                FdroidUpdatesSection(showHeader = false)
            }
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
                        if (persist) {
                            prefs.edit().putBoolean("dev_mode", false).apply()
                        }
                    }
                )
            }
        }

        SettingsActionRow(
            icon = Icons.Default.Info,
            title = "About ChemSearch",
            subtitle = "App info, links, data sources, and credits",
            actionLabel = "Open",
            actionColor = MaterialTheme.colorScheme.primary,
            onClick = onOpenAbout
        )
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
                "Hide debug settings" to "Sets dev_mode=false and hides this section. Tap the build number 5 times on the About screen to unlock it again."
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

            if (BuildConfig.GITHUB_UPDATES_ENABLED) {
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
            }

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

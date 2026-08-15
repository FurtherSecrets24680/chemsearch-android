package com.furthersecrets.chemsearch.ui

import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
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

@StringRes
internal fun amoledModeTitle(): Int = R.string.ui_amoled_mode

@StringRes
internal fun amoledModeSubtitle(isDark: Boolean): Int =
    if (isDark) R.string.ui_amoled_mode_dark_subtitle else R.string.ui_amoled_mode_light_subtitle

@StringRes
internal fun defaultStructureViewLabel(view: DefaultStructureView): Int =
    when (view) {
        DefaultStructureView.TWO_D -> R.string.ui_structure_view_2d
        DefaultStructureView.THREE_D -> R.string.ui_structure_view_3d
        DefaultStructureView.LAST_USED -> R.string.ui_structure_view_last_used
    }

@StringRes
internal fun offlineDownloadQualityLabel(quality: OfflineDownloadQuality): Int =
    when (quality) {
        OfflineDownloadQuality.BASIC -> R.string.ui_offline_quality_basic
        OfflineDownloadQuality.STRUCTURES -> R.string.ui_offline_quality_structures
        OfflineDownloadQuality.COMPLETE -> R.string.ui_offline_quality_complete
    }

@StringRes
internal fun formulaDisplayStyleLabel(style: FormulaDisplayStyle): Int =
    when (style) {
        FormulaDisplayStyle.CONVENTIONAL -> R.string.ui_formula_style_conventional
        FormulaDisplayStyle.HILL -> R.string.ui_formula_style_hill
    }

@StringRes
internal fun descSourceLabel(source: DescSource): Int =
    when (source) {
        DescSource.PUBCHEM -> R.string.ui_desc_source_pubchem
        DescSource.WIKI -> R.string.ui_desc_source_wikipedia
        DescSource.AI -> R.string.ui_desc_source_ai
    }

@StringRes
internal fun cacheSizeLimitLabel(limit: CacheSizeLimit): Int =
    when (limit) {
        CacheSizeLimit.MB_10 -> R.string.ui_cache_limit_10_mb
        CacheSizeLimit.MB_50 -> R.string.ui_cache_limit_50_mb
        CacheSizeLimit.MB_100 -> R.string.ui_cache_limit_100_mb
        CacheSizeLimit.UNLIMITED -> R.string.ui_cache_limit_unlimited
    }

@StringRes
internal fun cacheRetentionLabel(retention: CacheRetention): Int =
    when (retention) {
        CacheRetention.AUTO_CLEAR_1_DAY -> R.string.ui_cache_retention_daily
        CacheRetention.AUTO_CLEAR_7_DAYS -> R.string.ui_cache_retention_weekly
        CacheRetention.AUTO_CLEAR_30_DAYS -> R.string.ui_cache_retention_monthly
        CacheRetention.MANUAL -> R.string.ui_cache_retention_manual
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

@StringRes
internal fun updateDownloadActionLabel(status: UpdateStatus): Int =
    when {
        status.isDownloadingUpdate -> 0
        status.downloadedUpdateApkPath != null -> R.string.ui_install
        else -> R.string.ui_download
    }

@StringRes
internal fun updateDownloadSubtitle(status: UpdateStatus): Int {
    if (status.isDownloadingUpdate) {
        return R.string.ui_downloading_update_d
    }
    if (status.downloadedUpdateApkPath != null) {
        return R.string.ui_download_complete_tap_install
    }
    return status.latestVersion?.let { R.string.ui_latest_s } ?: R.string.ui_update_available
}

@StringRes
private fun AppColorScheme.label(): Int = when (this) {
    AppColorScheme.BLUE -> R.string.ui_color_blue
    AppColorScheme.VIOLET -> R.string.ui_color_violet
    AppColorScheme.EMERALD -> R.string.ui_color_emerald
    AppColorScheme.ROSE -> R.string.ui_color_rose
    AppColorScheme.AMBER -> R.string.ui_color_amber
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
                    stringResource(scheme.label()),
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
        Text(stringResource(R.string.ui_provider),
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
                        Text(stringResource(aiProvider.displayNameRes), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text(
                            if (selectedHasKey) stringResource(R.string.ui_key_saved) else stringResource(R.string.ui_needs_api_key),
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
                                Text(stringResource(provider.displayNameRes), fontWeight = FontWeight.SemiBold)
                                Text(
                                    if (hasKey) stringResource(R.string.ui_key_saved) else stringResource(R.string.ui_needs_key),
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
            stringResource(aiProvider.descriptionRes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(0.58f)
        )

        Text(stringResource(R.string.ui_model),
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
                Text(if (selectedHasKey) stringResource(R.string.ui_replace) else stringResource(R.string.ui_add_key))
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
                Text(stringResource(R.string.ui_refresh_models))
            }
            if (selectedHasKey) {
                IconButton(onClick = { onClearAiKey(aiProvider) }) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = stringResource(R.string.ui_remove_key), tint = MaterialTheme.colorScheme.error.copy(0.72f))
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
    appLanguage: AppLanguage = AppLanguage.SYSTEM,
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
    onSetAppLanguage: (AppLanguage) -> Unit = {},
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
    val context = LocalContext.current
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
            Text(stringResource(R.string.ui_settings_2),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            SettingsSectionHeader(stringResource(R.string.ui_appearance_label))
            SettingsToggleRow(
                icon = if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                title = stringResource(R.string.ui_dark_mode),
                subtitle = if (isDark) stringResource(R.string.ui_currently_dark) else stringResource(R.string.ui_currently_light),
                checked = isDark,
                onToggle = onToggleTheme
            )
            SettingsToggleRow(
                icon = Icons.Default.Brightness2,
                title = stringResource(amoledModeTitle()),
                subtitle = stringResource(amoledModeSubtitle(isDark)),
                checked = oledDarkTheme,
                enabled = isOledModeControlEnabled(isDark),
                onToggle = onToggleOledDarkTheme
            )
            Text(stringResource(R.string.ui_color_scheme),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(0.45f),
                modifier = Modifier.padding(top = 6.dp)
            )
            ColorSchemePicker(
                colorScheme = colorScheme,
                onSetColorScheme = onSetColorScheme
            )
            SettingsDropdownSelector(
                title = stringResource(R.string.ui_language),
                subtitle = stringResource(R.string.ui_language_subtitle),
                selected = appLanguage,
                options = AppLanguage.entries,
                labelFor = { language -> context.getString(language.displayNameRes) },
                onSelect = onSetAppLanguage
            )

            Spacer(Modifier.height(4.dp))
            SettingsSectionHeader(stringResource(R.string.ui_search))
            SettingsToggleRow(
                icon = Icons.Default.Search,
                title = stringResource(R.string.ui_autosuggestions),
                subtitle = stringResource(R.string.ui_subtitle_show_dropdown),
                checked = autoSuggest,
                onToggle = onToggleAutoSuggest
            )
            SettingsToggleRow(
                icon = Icons.Default.GridView,
                title = stringResource(R.string.ui_compact_mode),
                subtitle = stringResource(R.string.ui_subtitle_show_more_content),
                checked = compactMode,
                onToggle = onToggleCompactMode
            )
            SettingsToggleRow(
                icon = Icons.Default.VisibilityOff,
                title = stringResource(R.string.ui_reduce_motion),
                subtitle = stringResource(R.string.ui_subtitle_use_calmer_transitions),
                checked = reduceMotion,
                onToggle = onToggleReduceMotion
            )
            SettingsToggleRow(
                icon = Icons.Default.Visibility,
                title = stringResource(R.string.ui_high_contrast_outlines),
                subtitle = stringResource(R.string.ui_subtitle_make_cards_easier),
                checked = highContrastOutlines,
                onToggle = onToggleHighContrastOutlines
            )
            SettingsDropdownSelector(
                title = stringResource(R.string.ui_default_structure_view),
                subtitle = stringResource(R.string.ui_subtitle_choose_structure_tab),
                selected = defaultStructureView,
                options = DefaultStructureView.entries,
                labelFor = { context.getString(defaultStructureViewLabel(it)) },
                onSelect = onSetDefaultStructureView
            )
            SettingsDropdownSelector(
                title = stringResource(R.string.ui_formula_display),
                subtitle = stringResource(R.string.ui_subtitle_conventional_hill),
                selected = formulaDisplayStyle,
                options = FormulaDisplayStyle.entries,
                labelFor = { context.getString(formulaDisplayStyleLabel(it)) },
                onSelect = onSetFormulaDisplayStyle
            )

            Spacer(Modifier.height(4.dp))
            SettingsSectionHeader(stringResource(R.string.ui_default_description_source))
            Text(stringResource(R.string.ui_automatically_shown_when_you_search_a_compound),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(0.5f),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            SettingsDropdownSelector(
                title = stringResource(R.string.ui_source),
                subtitle = stringResource(R.string.ui_subtitle_choose_description),
                selected = defaultDescSource,
                options = DescSource.entries,
                labelFor = { context.getString(descSourceLabel(it)) },
                onSelect = onSetDefaultDesc
            )

            Spacer(Modifier.height(4.dp))
            SettingsSectionHeader(stringResource(R.string.ui_ai_provider_and_keys))
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
            SettingsSectionHeader(stringResource(R.string.ui_section_data))
            SettingsActionRow(
                icon = Icons.Default.History,
                title = stringResource(R.string.ui_search_history),
                subtitle = stringResource(R.string.ui_subtitle_clear_recent_searches),
                actionLabel = stringResource(R.string.ui_clear),
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
            }

            Spacer(Modifier.height(4.dp))
            SettingsSectionHeader(stringResource(R.string.ui_faq))
            var showFaqDialogSheet by remember { mutableStateOf(false) }
            if (showFaqDialogSheet) {
                InfoDialog(titleRes = R.string.ui_faq, entries = faqEntriesForCurrentBuild(), onDismiss = { showFaqDialogSheet = false })
            }
            SettingsActionRow(
                icon = Icons.AutoMirrored.Filled.HelpOutline,
                title = stringResource(R.string.ui_frequently_asked_questions),
                subtitle = stringResource(R.string.ui_subtitle_quick_answers),
                actionLabel = stringResource(R.string.ui_open),
                actionColor = MaterialTheme.colorScheme.primary,
                onClick = { showFaqDialogSheet = true }
            )

            Spacer(Modifier.height(4.dp))
            SettingsActionRow(
                icon = Icons.Default.Info,
                title = stringResource(R.string.ui_about_chemsearch),
                subtitle = stringResource(R.string.ui_subtitle_about_links),
                actionLabel = stringResource(R.string.ui_open),
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
                Toast.makeText(context, context.getString(R.string.ui_notifications_permission_denied), Toast.LENGTH_SHORT).show()
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
        context.getString(R.string.ui_last_checked_s, relative)
    } ?: context.getString(R.string.ui_never_checked)
    val checkLabel = if (updateStatus.isChecking) context.getString(R.string.ui_checking) else context.getString(R.string.ui_check)

    if (showHeader) {
        Spacer(Modifier.height(4.dp))
        SettingsSectionHeader(stringResource(R.string.ui_section_updates))
    }
    SettingsToggleRow(
        icon = Icons.Default.Notifications,
        title = stringResource(R.string.ui_update_notifications),
        subtitle = stringResource(R.string.ui_subtitle_notify_new_version),
        checked = updateNotificationsEnabled,
        onToggle = {
            val next = !updateNotificationsEnabled
            if (next) requestUpdateNotifications() else onToggleUpdateNotifications(false)
        }
    )
    SettingsActionRow(
        icon = Icons.Default.SystemUpdate,
        title = stringResource(R.string.ui_check_for_updates),
        subtitle = lastCheckedLabel,
        actionLabel = checkLabel,
        actionColor = MaterialTheme.colorScheme.primary
    ) {
        if (!updateStatus.isChecking) onCheckForUpdates()
    }
    if (updateStatus.updateAvailable) {
        val updateSubtitleRes = updateDownloadSubtitle(updateStatus)
        val updateSubtitle = when {
            updateStatus.isDownloadingUpdate -> context.getString(
                updateSubtitleRes,
                updateDownloadPercent(updateStatus.updateDownloadProgress)
            )
            updateStatus.latestVersion != null -> context.getString(updateSubtitleRes, updateStatus.latestVersion)
            else -> context.getString(updateSubtitleRes)
        }
        SettingsActionRow(
            icon = Icons.Default.Download,
            title = stringResource(R.string.ui_update_available),
            subtitle = updateSubtitle,
            actionLabel = if (updateStatus.isDownloadingUpdate) {
                ""
            } else {
                context.getString(updateDownloadActionLabel(updateStatus))
            },
            actionColor = MaterialTheme.colorScheme.primary,
            enabled = !updateStatus.isDownloadingUpdate,
            progress = updateStatus.updateDownloadProgress?.takeIf { updateStatus.isDownloadingUpdate }
        ) {
            if (updateStatus.downloadUrl.isNullOrBlank() && updateStatus.downloadedUpdateApkPath == null) {
                Toast.makeText(context, context.getString(R.string.ui_no_download_link_found), Toast.LENGTH_SHORT).show()
            } else {
                onDownloadUpdate()
            }
        }
    } else if (updateStatus.latestVersion != null && !updateStatus.isChecking) {
        Text(
            stringResource(R.string.ui_up_to_date_latest_s, updateStatus.latestVersion),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(0.5f),
            modifier = Modifier.padding(start = 32.dp, top = 2.dp)
        )
    }
    updateStatus.error?.let { error ->
        Text(
            stringResource(R.string.ui_update_check_failed_s, error),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(start = 32.dp, top = 2.dp)
        )
    }
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
            title = stringResource(R.string.ui_section_app_links),
            entries = aboutAppLinks,
            iconFor = ::aboutAppLinkIcon
        )
        AboutSection(
            title = stringResource(R.string.ui_section_chemistry_data),
            entries = aboutDataCredits,
            iconFor = ::aboutDataCreditIcon
        )
        AboutSection(
            title = stringResource(R.string.ui_section_ai_providers),
            entries = aboutAiProviderCredits,
            iconFor = { Icons.Default.SmartToy }
        )
        AboutSection(
            title = stringResource(R.string.ui_section_built_with),
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
                    contentDescription = stringResource(R.string.ui_chemsearch_app_icon),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(128.dp)
                        .padding(10.dp)
                )
            }
            Text(stringResource(R.string.ui_chemsearch_2),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(stringResource(R.string.ui_chemistry_simplified_for_android),
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
                        text = stringResource(R.string.ui_version_s_d, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
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
                        text = if (BuildConfig.DEBUG) stringResource(R.string.ui_debug) else stringResource(R.string.ui_release),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Text(stringResource(R.string.ui_search_compounds_draw_structures_view_2d_3d_models),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(0.58f)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Code, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Text(stringResource(R.string.ui_built_by_furthersecrets),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                stringResource(R.string.ui_package_s, BuildConfig.APPLICATION_ID),
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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.ui_back), tint = MaterialTheme.colorScheme.primary)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.ui_about_chemsearch),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.sp
                    )
                    Text(stringResource(R.string.ui_app_info_links_data_sources_and_credits),
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
                    3 -> Toast.makeText(context, context.getString(R.string.ui_taps_to_unlock_debug, 2), Toast.LENGTH_SHORT).show()
                    4 -> Toast.makeText(context, context.getString(R.string.ui_one_more_tap_to_unlock), Toast.LENGTH_SHORT).show()
                    5 -> {
                        prefs.edit().putBoolean("dev_mode", true).apply()
                        Toast.makeText(context, context.getString(R.string.ui_debug_settings_unlocked), Toast.LENGTH_SHORT).show()
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
        Text(stringResource(R.string.ui_legal_and_safety),
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
                stringResource(document.titleRes),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                stringResource(document.summaryRes),
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
                stringResource(entry.titleRes),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                stringResource(entry.detailRes),
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
    when (entry.titleRes) {
        R.string.ui_credit_github_repository -> Icons.Default.Code
        R.string.ui_credit_latest_release -> Icons.Default.SystemUpdate
        R.string.ui_credit_wiki -> Icons.AutoMirrored.Filled.MenuBook
        R.string.ui_credit_issue_tracker -> Icons.Default.BugReport
        R.string.ui_credit_product_hunt -> Icons.Default.Public
        R.string.ui_credit_license -> Icons.Default.Description
        else -> Icons.AutoMirrored.Filled.OpenInNew
    }

private fun aboutDataCreditIcon(entry: AboutCreditEntry): ImageVector =
    when (entry.titleRes) {
        R.string.ui_source_pubchem_pug_rest, R.string.ui_source_pubchem_pug_view, R.string.ui_credit_pubchem_periodic_table -> Icons.Default.Storage
        R.string.ui_credit_wikipedia_wikimedia -> Icons.Default.Public
        R.string.ui_pt_bowserinator -> Icons.AutoMirrored.Filled.MenuBook
        R.string.ui_source_nci_cadd_resolver -> Icons.Default.Science
        R.string.ui_source_iupac_gold_book, R.string.ui_source_iupac_red_book -> Icons.AutoMirrored.Filled.MenuBook
        R.string.ui_source_unece_ghs -> Icons.Default.HealthAndSafety
        else -> Icons.Default.Info
    }

private fun aboutTechnologyCreditIcon(entry: AboutCreditEntry): ImageVector =
    when (entry.titleRes) {
        R.string.ui_credit_jetpack_compose, R.string.ui_credit_material_3 -> Icons.Default.Palette
        R.string.ui_credit_androidx_navigation -> Icons.AutoMirrored.Filled.ArrowForward
        R.string.ui_credit_androidx_room, R.string.ui_credit_androidx_datastore -> Icons.Default.Storage
        R.string.ui_credit_androidx_workmanager -> Icons.Default.Cached
        R.string.ui_credit_retrofit, R.string.ui_credit_okhttp -> Icons.Default.Hub
        R.string.ui_credit_coil -> Icons.Default.Visibility
        R.string.ui_credit_gson -> Icons.Default.Code
        R.string.ui_credit_coroutines -> Icons.Default.Bolt
        R.string.ui_credit_phosphor_icons -> Icons.Default.Star
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
        title = { Text(stringResource(R.string.ui_ai_description_source), fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(stringResource(R.string.ui_choose_which_provider_should_generate_this_compound_description),
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
                                    Text(stringResource(provider.displayNameRes), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        if (hasKey) stringResource(R.string.ui_key_saved) else stringResource(R.string.ui_needs_key),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (hasKey) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error.copy(0.82f),
                        fontWeight = FontWeight.Bold
                    )
                                }
                                Text(
                                    stringResource(provider.descriptionRes),
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
                Text(if (activeHasKey) stringResource(R.string.ui_use_ai) else stringResource(R.string.ui_add_key))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_cancel)) }
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
                Text(stringResource(R.string.ui_required_for_ai_descriptions), style = MaterialTheme.typography.bodySmall)
                val context = LocalContext.current
                Text(
                    stringResource(R.string.ui_get_or_manage_a_key_at_s, link),
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
                    label = { Text(stringResource(R.string.ui_api_key)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { visible = !visible }) {
                            Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                        }
                    }
                )
                Text(stringResource(R.string.ui_stored_locally_on_your_device_only), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
            }
        },
        confirmButton = {
            Button(onClick = { if (key.isNotBlank()) onSave(key.trim()) }, shape = RoundedCornerShape(10.dp)) { Text(stringResource(R.string.ui_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_cancel)) } },
        containerColor = MaterialTheme.colorScheme.surface
    )
}


// Info dialog

@Composable
fun InfoDialog(titleRes: Int, entries: List<Pair<Int, Int>>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes), fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                entries.forEach { (termRes, explanationRes) ->
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(stringResource(termRes), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text(stringResource(explanationRes), style = MaterialTheme.typography.bodySmall, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.8f))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_got_it)) } },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

private val FAQ_ENTRIES = listOf(
    R.string.ui_faq_what_for_q to R.string.ui_faq_what_for_a,
    R.string.ui_faq_replacement_q to R.string.ui_faq_replacement_a,
    R.string.ui_faq_data_source_q to R.string.ui_faq_data_source_a,
    R.string.ui_faq_pt_data_q to R.string.ui_faq_pt_data_a,
    R.string.ui_faq_trust_q to R.string.ui_faq_trust_a,
    R.string.ui_faq_safety_official_q to R.string.ui_faq_safety_official_a,
    R.string.ui_faq_safety_missing_q to R.string.ui_faq_safety_missing_a,
    R.string.ui_faq_lab_advice_q to R.string.ui_faq_lab_advice_a,
    R.string.ui_faq_search_what_q to R.string.ui_faq_search_what_a,
    R.string.ui_faq_search_fail_q to R.string.ui_faq_search_fail_a,
    R.string.ui_faq_did_you_mean_q to R.string.ui_faq_did_you_mean_a,
    R.string.ui_faq_normal_vs_isomer_q to R.string.ui_faq_normal_vs_isomer_a,
    R.string.ui_faq_formula_style_q to R.string.ui_faq_formula_style_a,
    R.string.ui_faq_ions_q to R.string.ui_faq_ions_a,
    R.string.ui_faq_2d_hydrogen_q to R.string.ui_faq_2d_hydrogen_a,
    R.string.ui_faq_3d_missing_q to R.string.ui_faq_3d_missing_a,
    R.string.ui_faq_3d_exact_q to R.string.ui_faq_3d_exact_a,
    R.string.ui_faq_3d_bond_order_q to R.string.ui_faq_3d_bond_order_a,
    R.string.ui_faq_structure_search_q to R.string.ui_faq_structure_search_a,
    R.string.ui_faq_structure_diff_q to R.string.ui_faq_structure_diff_a,
    R.string.ui_faq_ai_wrong_q to R.string.ui_faq_ai_wrong_a,
    R.string.ui_faq_ai_data_q to R.string.ui_faq_ai_data_a,
    R.string.ui_faq_ai_key_q to R.string.ui_faq_ai_key_a,
    R.string.ui_faq_key_storage_q to R.string.ui_faq_key_storage_a,
    R.string.ui_faq_collect_q to R.string.ui_faq_collect_a,
    R.string.ui_faq_offline_q to R.string.ui_faq_offline_a,
    R.string.ui_faq_cache_vs_downloads_q to R.string.ui_faq_cache_vs_downloads_a,
    R.string.ui_faq_downloads_stored_q to R.string.ui_faq_downloads_stored_a,
    R.string.ui_faq_save_q to R.string.ui_faq_save_a,
    R.string.ui_faq_compare_q to R.string.ui_faq_compare_a,
    R.string.ui_faq_database_q to R.string.ui_faq_database_a,
    R.string.ui_faq_compare_reactions_q to R.string.ui_faq_compare_reactions_a,
    R.string.ui_faq_tags_q to R.string.ui_faq_tags_a,
    R.string.ui_faq_long_names_q to R.string.ui_faq_long_names_a,
    R.string.ui_faq_molar_mass_q to R.string.ui_faq_molar_mass_a,
    R.string.ui_faq_oxidation_q to R.string.ui_faq_oxidation_a,
    R.string.ui_faq_balancing_q to R.string.ui_faq_balancing_a,
    R.string.ui_faq_homework_q to R.string.ui_faq_homework_a,
    R.string.ui_faq_updates_q to R.string.ui_faq_updates_a,
    R.string.ui_faq_updates_optional_q to R.string.ui_faq_updates_optional_a,
    R.string.ui_faq_clear_q to R.string.ui_faq_clear_a,
    R.string.ui_faq_debug_q to R.string.ui_faq_debug_a
)

private val DEBUG_ENTRIES = listOf(
    R.string.ui_verbose_logging to R.string.ui_debug_verbose_logging_body,
    R.string.ui_live_log_viewer to R.string.ui_debug_live_log_viewer_body,
    R.string.ui_inspect_sharedpreferences to R.string.ui_debug_inspect_prefs_body,
    R.string.ui_memory_info to R.string.ui_debug_memory_info_body,
    R.string.ui_network_diagnostics to R.string.ui_debug_network_diagnostics_body,
    R.string.ui_show_welcome_screen to R.string.ui_debug_show_welcome_body,
    R.string.ui_api_endpoints to R.string.ui_debug_api_endpoints_body,
    R.string.ui_wipe_all_sharedpreferences to R.string.ui_debug_wipe_prefs_body,
    R.string.ui_debug_force_crash to R.string.ui_debug_force_crash_body,
    R.string.ui_hide_debug_settings to R.string.ui_debug_hide_body
)

private fun faqEntriesForCurrentBuild(): List<Pair<Int, Int>> {
    if (BuildConfig.GITHUB_UPDATES_ENABLED) return FAQ_ENTRIES
    return FAQ_ENTRIES.mapNotNull { (question, answer) ->
        when (question) {
            R.string.ui_faq_updates_q,
            R.string.ui_faq_updates_optional_q -> null
            R.string.ui_faq_collect_q -> question to R.string.ui_faq_collect_no_updates_a
            R.string.ui_faq_offline_q -> question to R.string.ui_faq_offline_no_updates_a
            else -> question to answer
        }
    }
}

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
                        contentDescription = stringResource(R.string.ui_structure_of, favorite.name),
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
                    stringResource(R.string.ui_cid_label, favorite.cid.toString()),
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
                            contentDescription = stringResource(R.string.ui_move_up),
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
                            contentDescription = stringResource(R.string.ui_move_down),
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
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.ui_remove), tint = MaterialTheme.colorScheme.error.copy(0.65f), modifier = Modifier.size(18.dp))
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
                            contentDescription = if (mode == LibraryViewMode.LIST) stringResource(R.string.ui_list_view) else stringResource(R.string.ui_grid_view),
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
                        contentDescription = stringResource(R.string.ui_selected),
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
                        contentDescription = stringResource(R.string.ui_structure_of, favorite.name),
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
                            contentDescription = stringResource(R.string.ui_remove),
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
                stringResource(R.string.ui_cid_label, favorite.cid.toString()),
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
            } ?: error(context.getString(R.string.ui_error_unable_to_open_export_file))
        }.onSuccess {
            Toast.makeText(context, context.getString(R.string.ui_library_exported), Toast.LENGTH_SHORT).show()
        }.onFailure { e ->
            Toast.makeText(context, context.getString(R.string.ui_export_failed_s, e.message ?: ""), Toast.LENGTH_LONG).show()
        }
    }
    val importLibraryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                reader.readText()
            } ?: error(context.getString(R.string.ui_error_unable_to_open_import_file))
        }.onSuccess {
            pendingLibraryImportJson = it
        }.onFailure { e ->
            Toast.makeText(context, context.getString(R.string.ui_import_failed_s, e.message ?: ""), Toast.LENGTH_LONG).show()
        }
    }

    pendingLibraryImportJson?.let { rawJson ->
        AlertDialog(
            onDismissRequest = { pendingLibraryImportJson = null },
            title = { Text(stringResource(R.string.ui_import_library), fontWeight = FontWeight.Bold) },
            text = {
                Text(stringResource(R.string.ui_merge_the_backup_with_your_current_library_or),
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
                                    context.getString(R.string.ui_imported_d_favorites_and_d_downloads, imported.favoriteCount, imported.downloadCount),
                                    Toast.LENGTH_LONG
                                ).show()
                            }.onFailure { e ->
                                Toast.makeText(context, context.getString(R.string.ui_import_failed_s, e.message ?: ""), Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) { Text(stringResource(R.string.ui_merge)) }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { pendingLibraryImportJson = null }) { Text(stringResource(R.string.ui_cancel)) }
                    TextButton(
                        onClick = {
                            pendingLibraryImportJson = null
                            onImportLibraryBackup(rawJson, true) { result ->
                                result.onSuccess { imported ->
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.ui_replaced_library_with_d_favorites_and_d_downloads, imported.favoriteCount, imported.downloadCount),
                                        Toast.LENGTH_LONG
                                    ).show()
                                }.onFailure { e ->
                                    Toast.makeText(context, context.getString(R.string.ui_import_failed_s, e.message ?: ""), Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    ) { Text(stringResource(R.string.ui_replace)) }
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

    val savedLibraryOptions = remember(context, favorites.size, downloads.size) {
        listOf(
            LibraryOption(
                tab = LibraryTab.FAVORITES,
                icon = LibraryTab.FAVORITES.icon(),
                title = context.getString(R.string.ui_favorites),
                subtitle = context.getString(R.string.ui_subtitle_saved_quick_links),
                countLabel = favorites.size.toString()
            ),
            LibraryOption(
                tab = LibraryTab.DOWNLOADS,
                icon = LibraryTab.DOWNLOADS.icon(),
                title = context.getString(R.string.ui_downloads),
                subtitle = context.getString(R.string.ui_subtitle_full_offline_copies),
                countLabel = downloads.size.toString()
            )
        )
    }
    val referenceLibraryOptions = remember(context, databaseSummary) {
        listOf(
            LibraryOption(
                tab = LibraryTab.PERIODIC_TABLE,
                icon = LibraryTab.PERIODIC_TABLE.icon(),
                title = context.getString(R.string.ui_periodic_table),
                subtitle = context.getString(R.string.ui_subtitle_all_118_offline),
                countLabel = PeriodicTableElements.size.toString()
            ),
            LibraryOption(
                tab = LibraryTab.DATABASE,
                icon = LibraryTab.DATABASE.icon(),
                title = context.getString(R.string.ui_chemical_database),
                subtitle = context.getString(R.string.ui_subtitle_substances_etc),
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
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.ui_clear_filter))
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
                Text(stringResource(R.string.ui_sort),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.45f)
                )
                if (filterQuery.isNotBlank()) {
                    Text(
                        pluralStringResource(R.plurals.ui_matches_count, matchCount, matchCount),
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
                SortPill(label = stringResource(R.string.ui_label_recent), selected = sortMode == FavoritesSort.RECENT) {
                    sortMode = FavoritesSort.RECENT
                }
                SortPill(label = stringResource(R.string.ui_a_z), selected = sortMode == FavoritesSort.NAME) {
                    sortMode = FavoritesSort.NAME
                }
                SortPill(label = stringResource(R.string.ui_most_atoms), selected = sortMode == FavoritesSort.ATOMS_DESC) {
                    sortMode = FavoritesSort.ATOMS_DESC
                }
                SortPill(label = stringResource(R.string.ui_least_atoms), selected = sortMode == FavoritesSort.ATOMS_ASC) {
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
                    Text(stringResource(R.string.ui_library), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = { exportLibraryLauncher.launch("chemsearch-library-${System.currentTimeMillis()}.json") },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Default.Description, contentDescription = stringResource(R.string.ui_export_library), modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(
                        onClick = { importLibraryLauncher.launch(arrayOf("application/json", "text/plain")) },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = stringResource(R.string.ui_import_library), modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
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
                    Text(stringResource(R.string.ui_back_to_library))
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
                        Text(if (isReordering) stringResource(R.string.ui_done) else stringResource(R.string.ui_reorder))
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
                Text(stringResource(R.string.ui_open_saved_compounds_offline_copies_the_periodic_table),
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
                        LibraryHomeSectionTitle(stringResource(R.string.ui_reference), modifier = Modifier.padding(top = 8.dp))
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
                    LibraryHomeSectionTitle(stringResource(R.string.ui_reference), modifier = Modifier.padding(top = 8.dp))
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
            LibraryTab.FAVORITES -> stringResource(R.string.ui_favorites)
            LibraryTab.DOWNLOADS -> stringResource(R.string.ui_downloads)
            LibraryTab.DATABASE -> stringResource(R.string.ui_chemical_database)
            LibraryTab.PERIODIC_TABLE -> stringResource(R.string.ui_periodic_table)
        }
        val sectionSubtitle = when (section) {
            LibraryTab.FAVORITES -> stringResource(R.string.ui_subtitle_saved_quick_links_device)
            LibraryTab.DOWNLOADS -> stringResource(R.string.ui_subtitle_offline_copies_saved_data)
            LibraryTab.DATABASE -> stringResource(R.string.ui_subtitle_browse_database)
            LibraryTab.PERIODIC_TABLE -> stringResource(R.string.ui_subtitle_browse_periodic_table)
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
            Text(stringResource(R.string.ui_reorder_mode_use_the_arrows_to_move_favorite),
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
                        title = stringResource(R.string.ui_no_favorites_yet),
                        subtitle = stringResource(R.string.ui_tap_the_star_icon_on_any_compound)
                    )
                } else {
                    if (!isReordering) SortAndFilterControls(stringResource(R.string.ui_filter_favorites), filteredFavorites.size)
                    if (displayFavorites.isEmpty()) {
                        LibraryEmptyState(
                            icon = Icons.Default.Search,
                            title = stringResource(R.string.ui_no_matches_found_title),
                            subtitle = stringResource(R.string.ui_try_a_different_name_formula_or_cid)
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
                        title = stringResource(R.string.ui_no_downloads_yet),
                        subtitle = stringResource(R.string.ui_subtitle_tap_download_icon)
                    )
                } else {
                    SortAndFilterControls(stringResource(R.string.ui_filter_downloads), filteredDownloads.size)
                    if (filteredDownloads.isEmpty()) {
                        LibraryEmptyState(
                            icon = Icons.Default.Search,
                            title = stringResource(R.string.ui_no_matches_found_title),
                            subtitle = stringResource(R.string.ui_try_a_different_name_formula_or_cid)
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
                    Toast.makeText(context, context.getString(R.string.ui_select_two_different_compounds), Toast.LENGTH_SHORT).show()
                }
            },
            icon = {
                Icon(Icons.AutoMirrored.Filled.CompareArrows, contentDescription = null, modifier = Modifier.size(18.dp))
            },
            text = {
                Text(stringResource(R.string.ui_compare), fontWeight = FontWeight.Bold)
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
                Text(stringResource(R.string.ui_favorites), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.primary.copy(0.12f)
                    ) {
                        Text(
                            stringResource(R.string.ui_saved_d, favorites.size),
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
                            Text(if (isReordering) stringResource(R.string.ui_done) else stringResource(R.string.ui_reorder))
                        }
                    }
                }
            }
            if (isReordering) {
                Text(stringResource(R.string.ui_tap_the_arrows_to_move_favorites),
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
                            Text(stringResource(R.string.ui_no_favorites_yet), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                            Text(stringResource(R.string.ui_tap_the_star_icon_on_any_compound), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(0.38f))
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
                    Text(stringResource(R.string.ui_no_favorites_yet), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface.copy(0.7f))
                    Text(stringResource(R.string.ui_tap_the_favorite_icon_on_any_compound), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(0.38f))
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
                Text(stringResource(R.string.ui_favorites), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.primary.copy(0.12f)
                ) {
                    Text(
                        stringResource(R.string.ui_saved_d, favorites.size),
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
                        Text(if (isReordering) stringResource(R.string.ui_done) else stringResource(R.string.ui_reorder))
                    }
                }
            }
        }
        Text(stringResource(R.string.ui_tap_a_card_to_open_favorites_are_stored),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
        )
        if (isReordering) {
            Text(stringResource(R.string.ui_reorder_mode_use_the_arrows_to_move_items),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(0.55f)
            )
        }

        if (showControls && !isReordering) {
            OutlinedTextField(
                value = filterQuery,
                onValueChange = { filterQuery = it },
                label = { Text(stringResource(R.string.ui_filter_favorites)) },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (filterQuery.isNotBlank()) {
                        IconButton(onClick = { filterQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.ui_clear_filter))
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
                    Text(stringResource(R.string.ui_sort),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.45f)
                    )
                    if (filterQuery.isNotBlank()) {
                        Text(
                            pluralStringResource(R.plurals.ui_matches_count, filteredFavorites.size, filteredFavorites.size),
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
                    SortPill(label = stringResource(R.string.ui_label_recent), selected = sortMode == FavoritesSort.RECENT) {
                        sortMode = FavoritesSort.RECENT
                    }
                    SortPill(label = stringResource(R.string.ui_a_z), selected = sortMode == FavoritesSort.NAME) {
                        sortMode = FavoritesSort.NAME
                    }
                    SortPill(label = stringResource(R.string.ui_most_atoms), selected = sortMode == FavoritesSort.ATOMS_DESC) {
                        sortMode = FavoritesSort.ATOMS_DESC
                    }
                    SortPill(label = stringResource(R.string.ui_least_atoms), selected = sortMode == FavoritesSort.ATOMS_ASC) {
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
                Text(stringResource(R.string.ui_no_matches_found),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.6f)
                )
                Text(stringResource(R.string.ui_try_a_different_name_formula_or_cid),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.45f)
                )
                if (filterQuery.isNotBlank()) {
                    TextButton(onClick = { filterQuery = "" }) { Text(stringResource(R.string.ui_clear_filter)) }
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
    appLanguage: AppLanguage = AppLanguage.SYSTEM,
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
    onSetAppLanguage: (AppLanguage) -> Unit = {},
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
            } ?: error(context.getString(R.string.ui_error_unable_to_open_export_file))
        }.onSuccess {
            Toast.makeText(context, context.getString(R.string.ui_settings_exported_with_keys), Toast.LENGTH_LONG).show()
        }.onFailure { e ->
            Toast.makeText(context, context.getString(R.string.ui_export_failed_s, e.message ?: ""), Toast.LENGTH_LONG).show()
        }
    }
    val importSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                reader.readText()
            } ?: error(context.getString(R.string.ui_error_unable_to_open_import_file))
            restoreSettingsFromBackup(context, prefs, raw)
        }.onSuccess { restoredCount ->
            onSettingsImported()
            Toast.makeText(context, context.getString(R.string.ui_imported_d_settings, restoredCount), Toast.LENGTH_SHORT).show()
        }.onFailure { e ->
            Toast.makeText(context, context.getString(R.string.ui_import_failed_s, e.message ?: ""), Toast.LENGTH_LONG).show()
        }
    }

    if (showFaqDialog) {
        InfoDialog(titleRes = R.string.ui_faq, entries = faqEntriesForCurrentBuild(), onDismiss = { showFaqDialog = false })
    }

    if (showCacheDirDialog) {
        AlertDialog(
            onDismissRequest = { showCacheDirDialog = false },
            title = { Text(stringResource(R.string.ui_cache_location), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.ui_enter_a_custom_path_or_leave_blank_to),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.6f)
                    )
                    OutlinedTextField(
                        value = cacheDirInput,
                        onValueChange = { cacheDirInput = it },
                        label = { Text(stringResource(R.string.ui_directory_path)) },
                        placeholder = { Text(stringResource(R.string.ui_leave_blank_for_default)) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(stringResource(R.string.ui_default_app_internal_cache_custom_paths_must_be),
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
                            Toast.makeText(context, context.getString(R.string.ui_cache_location_updated), Toast.LENGTH_SHORT).show()
                            showCacheDirDialog = false
                        } else {
                            Toast.makeText(context, context.getString(R.string.ui_cache_not_writable), Toast.LENGTH_LONG).show()
                        }
                    },
                    shape = RoundedCornerShape(10.dp)
                ) { Text(stringResource(R.string.ui_save)) }
            },
            dismissButton = { TextButton(onClick = { showCacheDirDialog = false }) { Text(stringResource(R.string.ui_cancel)) } },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    val cacheSizeLabel = remember(cacheSizeBytes) {
        when {
            cacheSizeBytes == 0L -> context.getString(R.string.ui_empty)
            cacheSizeBytes < 1024 -> "${cacheSizeBytes} B"
            cacheSizeBytes < 1024 * 1024 -> "${"%.1f".format(cacheSizeBytes / 1024.0)} KB"
            else -> "${"%.2f".format(cacheSizeBytes / (1024.0 * 1024.0))} MB"
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(stringResource(R.string.ui_settings), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            if (BuildConfig.GITHUB_UPDATES_ENABLED) {
                stringResource(R.string.ui_settings_description_updates)
            } else {
                stringResource(R.string.ui_settings_description_support)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(0.55f)
        )

        SettingsGroupCard(
            icon = Icons.Default.Tune,
            title = stringResource(R.string.ui_display_and_search),
            subtitle = stringResource(R.string.ui_subtitle_control_theme)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Palette, null, tint = MaterialTheme.colorScheme.onSurface.copy(0.5f), modifier = Modifier.size(20.dp))
                    Column {
                        Text(stringResource(R.string.ui_theme_mode), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(if (isDark) stringResource(R.string.ui_dark) else stringResource(R.string.ui_light), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
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
                                if (isDark) stringResource(R.string.ui_dark) else stringResource(R.string.ui_light),
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
                        listOf(false to R.string.ui_light, true to R.string.ui_dark).forEach { (dark, labelRes) ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(
                                            if (dark) Icons.Default.DarkMode else Icons.Default.LightMode,
                                            null,
                                            tint = if (isDark == dark) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(0.6f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(stringResource(labelRes), color = if (isDark == dark) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
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
                title = stringResource(amoledModeTitle()),
                subtitle = stringResource(amoledModeSubtitle(isDark)),
                checked = oledDarkTheme,
                enabled = isOledModeControlEnabled(isDark),
                onToggle = onToggleOledDarkTheme
            )
            SettingsGroupDivider()
            Text(stringResource(R.string.ui_color_scheme),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(0.45f)
            )
            ColorSchemePicker(
                colorScheme = colorScheme,
                onSetColorScheme = onSetColorScheme
            )
            SettingsGroupDivider()
            SettingsDropdownSelector(
                title = stringResource(R.string.ui_language),
                subtitle = stringResource(R.string.ui_language_subtitle),
                selected = appLanguage,
                options = AppLanguage.entries,
                labelFor = { language -> context.getString(language.displayNameRes) },
                onSelect = onSetAppLanguage
            )
            SettingsGroupDivider()
            SettingsToggleRow(
                icon = Icons.Default.Search,
                title = stringResource(R.string.ui_autosuggestions),
                subtitle = stringResource(R.string.ui_subtitle_show_dropdown),
                checked = autoSuggest,
                onToggle = onToggleAutoSuggest
            )
            SettingsGroupDivider()
            SettingsToggleRow(
                icon = Icons.Default.GridView,
                title = stringResource(R.string.ui_compact_mode),
                subtitle = stringResource(R.string.ui_subtitle_show_more_content),
                checked = compactMode,
                onToggle = onToggleCompactMode
            )
            SettingsGroupDivider()
            SettingsToggleRow(
                icon = Icons.Default.VisibilityOff,
                title = stringResource(R.string.ui_reduce_motion),
                subtitle = stringResource(R.string.ui_subtitle_use_calmer_transitions),
                checked = reduceMotion,
                onToggle = onToggleReduceMotion
            )
            SettingsGroupDivider()
            SettingsToggleRow(
                icon = Icons.Default.Visibility,
                title = stringResource(R.string.ui_high_contrast_outlines),
                subtitle = stringResource(R.string.ui_subtitle_make_cards_easier),
                checked = highContrastOutlines,
                onToggle = onToggleHighContrastOutlines
            )
            SettingsGroupDivider()
            SettingsDropdownSelector(
                title = stringResource(R.string.ui_default_structure_view),
                subtitle = stringResource(R.string.ui_subtitle_choose_structure_tab),
                selected = defaultStructureView,
                options = DefaultStructureView.entries,
                labelFor = { context.getString(defaultStructureViewLabel(it)) },
                onSelect = onSetDefaultStructureView
            )
            SettingsGroupDivider()
            SettingsDropdownSelector(
                title = stringResource(R.string.ui_formula_display),
                subtitle = stringResource(R.string.ui_subtitle_conventional_hill),
                selected = formulaDisplayStyle,
                options = FormulaDisplayStyle.entries,
                labelFor = { context.getString(formulaDisplayStyleLabel(it)) },
                onSelect = onSetFormulaDisplayStyle
            )
            SettingsGroupDivider()
            SettingsDropdownSelector(
                title = stringResource(R.string.ui_default_description_source),
                subtitle = stringResource(R.string.ui_subtitle_choose_description),
                selected = defaultDescSource,
                options = DescSource.entries,
                labelFor = { context.getString(descSourceLabel(it)) },
                onSelect = onSetDefaultDesc
            )
        }

        SettingsGroupCard(
            icon = Icons.Default.Key,
            title = stringResource(R.string.ui_ai_provider_and_keys),
            subtitle = stringResource(R.string.ui_subtitle_pick_provider)
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
            title = stringResource(R.string.ui_data_and_storage),
            subtitle = stringResource(R.string.ui_subtitle_clear_history_cache)
        ) {
            SettingsActionRow(
                icon = Icons.Default.History,
                title = stringResource(R.string.ui_search_history),
                subtitle = stringResource(R.string.ui_subtitle_clear_recent_searches),
                actionLabel = stringResource(R.string.ui_clear),
                actionColor = MaterialTheme.colorScheme.error,
                onClick = onClearHistory
            )
            SettingsActionRow(
                icon = Icons.Default.Cached,
                title = stringResource(R.string.ui_compound_cache),
                subtitle = "$cacheSizeLabel · ${if (cacheDir.isBlank()) stringResource(R.string.ui_default_location) else cacheDir.takeLast(42)}",
                actionLabel = stringResource(R.string.ui_clear),
                actionColor = MaterialTheme.colorScheme.error,
                onClick = onClearCache
            )
            SettingsActionRow(
                icon = Icons.Default.FolderOpen,
                title = stringResource(R.string.ui_cache_location),
                subtitle = if (cacheDir.isBlank()) stringResource(R.string.ui_app_internal_cache_default) else cacheDir,
                actionLabel = stringResource(R.string.ui_change),
                actionColor = MaterialTheme.colorScheme.primary,
                onClick = {
                    cacheDirInput = cacheDir
                    showCacheDirDialog = true
                }
            )
            SettingsGroupDivider()
            SettingsDropdownSelector(
                title = stringResource(R.string.ui_offline_download_quality),
                subtitle = stringResource(R.string.ui_subtitle_pick_offline_quality),
                selected = offlineDownloadQuality,
                options = OfflineDownloadQuality.entries,
                labelFor = { context.getString(offlineDownloadQualityLabel(it)) },
                onSelect = onSetOfflineDownloadQuality
            )
            SettingsGroupDivider()
            SettingsSliderSelector(
                title = stringResource(R.string.ui_cache_size_limit),
                subtitle = stringResource(R.string.ui_subtitle_limit_temp_cache),
                selected = cacheSizeLimit,
                options = CacheSizeLimit.entries,
                labelFor = { context.getString(cacheSizeLimitLabel(it)) },
                onSelect = onSetCacheSizeLimit
            )
            SettingsGroupDivider()
            SettingsDropdownSelector(
                title = stringResource(R.string.ui_auto_clear_cache),
                subtitle = stringResource(R.string.ui_subtitle_remove_old_cache),
                selected = cacheRetention,
                options = CacheRetention.entries,
                labelFor = { context.getString(cacheRetentionLabel(it)) },
                onSelect = onSetCacheRetention
            )
            SettingsGroupDivider()
            SettingsActionRow(
                icon = Icons.Default.Description,
                title = stringResource(R.string.ui_export_settings),
                subtitle = stringResource(R.string.ui_subtitle_save_settings),
                actionLabel = stringResource(R.string.ui_export),
                actionColor = MaterialTheme.colorScheme.primary,
                onClick = {
                    exportSettingsLauncher.launch("chemsearch-settings-${System.currentTimeMillis()}.json")
                }
            )
            SettingsActionRow(
                icon = Icons.Default.FolderOpen,
                title = stringResource(R.string.ui_import_settings),
                subtitle = stringResource(R.string.ui_subtitle_restore_settings),
                actionLabel = stringResource(R.string.ui_import),
                actionColor = MaterialTheme.colorScheme.primary,
                onClick = {
                    importSettingsLauncher.launch(arrayOf("application/json", "text/plain"))
                }
            )
        }

        SettingsGroupCard(
            icon = Icons.Default.SystemUpdate,
            title = if (BuildConfig.GITHUB_UPDATES_ENABLED) stringResource(R.string.ui_updates_and_help) else stringResource(R.string.ui_help),
            subtitle = if (BuildConfig.GITHUB_UPDATES_ENABLED) {
                stringResource(R.string.ui_control_update_checks_and_open_support_resources)
            } else {
                stringResource(R.string.ui_faq_and_support_resources)
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
                SettingsGroupDivider()
            }
            SettingsActionRow(
                icon = Icons.AutoMirrored.Filled.HelpOutline,
                title = stringResource(R.string.ui_frequently_asked_questions),
                subtitle = stringResource(R.string.ui_subtitle_quick_answers),
                actionLabel = stringResource(R.string.ui_open),
                actionColor = MaterialTheme.colorScheme.primary,
                onClick = { showFaqDialog = true }
            )
        }

        if (isDevMode) {
            SettingsGroupCard(
                icon = Icons.Default.BugReport,
                title = stringResource(R.string.ui_developer),
                subtitle = stringResource(R.string.ui_subtitle_diagnostics_tools)
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
            title = stringResource(R.string.ui_about_chemsearch),
            subtitle = stringResource(R.string.ui_subtitle_about_links),
            actionLabel = stringResource(R.string.ui_open),
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
                reason = "Skipped: ${spec.aiProvider?.shortName ?: "Provider"} API key is not set."
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
            titleRes = R.string.ui_debug_settings_title,
            entries = DEBUG_ENTRIES,
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
            title = { Text(stringResource(R.string.ui_sharedpreferences_dump), fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (prefEntries.isEmpty()) {
                        Text(stringResource(R.string.ui_no_keys_stored), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                    } else {
                        if (hasSensitive) {
                            Text(stringResource(R.string.ui_sensitive_values_are_masked_and_are_never_copied),
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
                        cm.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.ui_clipboard_preferences), dump))
                        Toast.makeText(context, context.getString(R.string.ui_copied_masked_prefs), Toast.LENGTH_SHORT).show()
                    }) { Text(stringResource(R.string.ui_copy)) }
                }
            },
            dismissButton = { TextButton(onClick = { showPrefsDialog = false }) { Text(stringResource(R.string.ui_close)) } },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    if (showLogsDialog) {
        AlertDialog(
            onDismissRequest = { showLogsDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.ui_live_logs), fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
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
                            if (verboseLogging) stringResource(R.string.ui_no_logs_yet_perform_an_action)
                            else stringResource(R.string.ui_verbose_logging_off_only_errors_captured),
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
                    TextButton(onClick = { DebugLog.clear() }) { Text(stringResource(R.string.ui_clear)) }
                    TextButton(onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.ui_clipboard_logs), logLines.joinToString("\n")))
                        Toast.makeText(context, context.getString(R.string.ui_copied_d_lines, logLines.size), Toast.LENGTH_SHORT).show()
                    }) { Text(stringResource(R.string.ui_copy)) }
                    TextButton(onClick = { showLogsDialog = false }) { Text(stringResource(R.string.ui_close)) }
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
        } ?: context.getString(R.string.ui_not_run_yet)

        AlertDialog(
            onDismissRequest = { showNetworkDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(stringResource(R.string.ui_network_diagnostics), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
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
                        stringResource(R.string.ui_last_run_s, runLabel),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.55f)
                    )
                    if (networkDiagnosticsResults.isNotEmpty()) {
                        Text(
                            stringResource(R.string.ui_success_d_failed_d_skipped_d, successCount, failedCount, skippedCount),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.6f)
                        )
                    }
                    if (networkDiagnosticsResults.isEmpty() && !isRunningNetworkDiagnostics) {
                        Text(stringResource(R.string.ui_run_diagnostics_to_test_pubchem_lookup_structures_ghs),
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
                    ) { Text(if (networkDiagnosticsResults.isEmpty()) stringResource(R.string.ui_run) else stringResource(R.string.ui_re_run)) }
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
                            cm.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.ui_clipboard_network_diagnostics), report))
                            Toast.makeText(context, context.getString(R.string.ui_diagnostics_copied), Toast.LENGTH_SHORT).show()
                        },
                        enabled = networkDiagnosticsResults.isNotEmpty()
                    ) { Text(stringResource(R.string.ui_copy)) }
                    TextButton(onClick = { showNetworkDialog = false }) { Text(stringResource(R.string.ui_close)) }
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
            title = { Text(stringResource(R.string.ui_memory_info), fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace) },
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
                                        Text(stringResource(R.string.ui_jvm_heap), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            stringResource(R.string.ui_used_d_mb_of_d_mb_max, heapUsedMb, heapMaxMb),
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
                                stringResource(R.string.ui_mem_used) to "${heapUsedMb} MB",
                                stringResource(R.string.ui_mem_allocated) to "${heapAllocatedMb} MB",
                                stringResource(R.string.ui_mem_max) to "${heapMaxMb} MB",
                                stringResource(R.string.ui_mem_headroom) to "${heapHeadroomMb} MB"
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
                                        Text(stringResource(R.string.ui_system_ram), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            stringResource(R.string.ui_used_d_mb_of_d_mb_total, usedSystemMb, totalSystemMb),
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
                                stringResource(R.string.ui_mem_used) to "${usedSystemMb} MB",
                                stringResource(R.string.ui_mem_available) to "${availMb} MB",
                                stringResource(R.string.ui_mem_total) to "${totalSystemMb} MB"
                            ).forEach { (k, v) ->
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(k, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                                    Text(v, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), fontWeight = FontWeight.SemiBold)
                                }
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(stringResource(R.string.ui_low_memory), style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
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
                        cm.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.ui_clipboard_memory), snapshot))
                        Toast.makeText(context, context.getString(R.string.ui_copied_memory_snapshot), Toast.LENGTH_SHORT).show()
                    }) { Text(stringResource(R.string.ui_copy)) }
                    TextButton(onClick = { showMemoryDialog = false }) { Text(stringResource(R.string.ui_close)) }
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    if (showWipeConfirm) {
        AlertDialog(
            onDismissRequest = { showWipeConfirm = false },
            title = { Text(stringResource(R.string.ui_wipe_all_preferences), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.ui_this_clears_legacy_preferences_encrypted_key_records_history)) },
            confirmButton = {
                Button(
                    onClick = {
                        showWipeConfirm = false
                        prefs.edit().clear().apply()
                        DebugLog.verbose = false
                        verboseLogging = false
                        DebugLog.e("ChemSearch", "SharedPreferences wiped by developer")
                        onDisableDevMode(false)
                        Toast.makeText(context, context.getString(R.string.ui_all_preferences_wiped), Toast.LENGTH_LONG).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.ui_wipe_now)) }
            },
            dismissButton = { TextButton(onClick = { showWipeConfirm = false }) { Text(stringResource(R.string.ui_cancel)) } },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    if (showCrashConfirm) {
        AlertDialog(
            onDismissRequest = { showCrashConfirm = false },
            title = { Text(stringResource(R.string.ui_force_crash), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.ui_this_will_immediately_crash_the_app_with_an)) },
            confirmButton = {
                Button(
                    onClick = { throw RuntimeException("ChemSearch debug force crash") },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.ui_crash_now)) }
            },
            dismissButton = { TextButton(onClick = { showCrashConfirm = false }) { Text(stringResource(R.string.ui_cancel)) } },
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
                Text(stringResource(R.string.ui_debug_settings),
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
                        Text(stringResource(R.string.ui_verbose_logging), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(stringResource(R.string.ui_tag_chemsearch), style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
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
                title = stringResource(R.string.ui_live_log_viewer),
                subtitle = pluralStringResource(R.plurals.ui_lines_captured, logLines.size, logLines.size),
                actionLabel = stringResource(R.string.ui_open),
                actionColor = MaterialTheme.colorScheme.primary,
                onClick = { showLogsDialog = true }
            )

            val networkSummary = if (networkDiagnosticsResults.isEmpty()) {
                context.getString(R.string.ui_ping_pubchem_fallback_3d_wikipedia_github_and_ai)
            } else {
                val ok = networkDiagnosticsResults.count { it.state == NetworkProbeState.SUCCESS }
                val fail = networkDiagnosticsResults.count { it.state == NetworkProbeState.FAILED }
                val skipped = networkDiagnosticsResults.count { it.state == NetworkProbeState.SKIPPED }
                context.getString(R.string.ui_last_run_d_ok_d_failed_d_skipped, ok, fail, skipped)
            }
            SettingsActionRow(
                icon = Icons.Default.Public,
                title = stringResource(R.string.ui_network_diagnostics),
                subtitle = networkSummary,
                actionLabel = if (isRunningNetworkDiagnostics) stringResource(R.string.ui_running) else stringResource(R.string.ui_run),
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
                    title = stringResource(R.string.ui_test_update_notification),
                    subtitle = stringResource(R.string.ui_subtitle_send_sample_notification),
                    actionLabel = stringResource(R.string.ui_send),
                    actionColor = MaterialTheme.colorScheme.primary,
                    onClick = {
                        val hasPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                        if (!hasPermission) {
                            Toast.makeText(context, context.getString(R.string.ui_grant_notification_permission), Toast.LENGTH_SHORT).show()
                        } else {
                            onTestUpdateNotification()
                            Toast.makeText(context, context.getString(R.string.ui_test_notification_sent), Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }

            SettingsActionRow(
                icon = Icons.Default.WavingHand,
                title = stringResource(R.string.ui_show_welcome_screen),
                subtitle = stringResource(R.string.ui_subtitle_replay_intro),
                actionLabel = stringResource(R.string.ui_open),
                actionColor = MaterialTheme.colorScheme.primary,
                onClick = {
                    onShowWelcome()
                    Toast.makeText(context, context.getString(R.string.ui_welcome_screen_restored), Toast.LENGTH_SHORT).show()
                }
            )

            // SharedPreferences dump
            SettingsActionRow(
                icon = Icons.Default.Storage,
                title = stringResource(R.string.ui_inspect_sharedpreferences),
                subtitle = context.getString(R.string.ui_d_legacy_keys_stored_secrets_masked, prefs.all.size),
                actionLabel = stringResource(R.string.ui_view),
                actionColor = MaterialTheme.colorScheme.primary,
                onClick = { showPrefsDialog = true }
            )

            // Memory info
            SettingsActionRow(
                icon = Icons.Default.Memory,
                title = stringResource(R.string.ui_memory_info),
                subtitle = stringResource(R.string.ui_subtitle_jvm_system_ram),
                actionLabel = stringResource(R.string.ui_view),
                actionColor = MaterialTheme.colorScheme.primary,
                onClick = { showMemoryDialog = true }
            )

            // API endpoints copy
            SettingsActionRow(
                icon = Icons.Default.Hub,
                title = stringResource(R.string.ui_api_endpoints),
                subtitle = stringResource(R.string.ui_subtitle_pubchem_etc),
                actionLabel = stringResource(R.string.ui_copy),
                actionColor = MaterialTheme.colorScheme.primary,
                onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val endpoints = buildDebugApiEndpointLines().joinToString("\n")
                    cm.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.ui_clipboard_endpoints), endpoints))
                    Toast.makeText(context, context.getString(R.string.ui_copied_to_clipboard), Toast.LENGTH_SHORT).show()
                }
            )

            // Wipe prefs
            SettingsActionRow(
                icon = Icons.Default.DeleteSweep,
                title = stringResource(R.string.ui_wipe_all_sharedpreferences),
                subtitle = stringResource(R.string.ui_subtitle_clears_legacy_prefs),
                actionLabel = stringResource(R.string.ui_wipe),
                actionColor = MaterialTheme.colorScheme.error,
                onClick = { showWipeConfirm = true }
            )

            // Force crash
            SettingsActionRow(
                icon = Icons.Default.Warning,
                title = stringResource(R.string.ui_debug_force_crash),
                subtitle = stringResource(R.string.ui_subtitle_throws_exception),
                actionLabel = stringResource(R.string.ui_crash),
                actionColor = MaterialTheme.colorScheme.error,
                onClick = { showCrashConfirm = true }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(0.2f), modifier = Modifier.padding(vertical = 4.dp))
            TextButton(
                onClick = {
                    onDisableDevMode(true)
                    Toast.makeText(context, context.getString(R.string.ui_debug_settings_hidden), Toast.LENGTH_SHORT).show()
                },
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(Icons.Default.VisibilityOff, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurface.copy(0.4f))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.ui_hide_debug_settings), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
            }
        }
    }

package com.furthersecrets.chemsearch.ui

import androidx.compose.ui.res.stringResource
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.furthersecrets.chemsearch.ChemViewModel
import com.furthersecrets.chemsearch.R
import com.furthersecrets.chemsearch.data.*
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import java.util.Locale

// App header

private val HeaderLogoBaseBlue = Color(0xFF2563EB)
private val PubChemStructureBackground = Color(0xFFF5F5F5)

private fun copyTextWithFeedback(
    context: Context,
    label: String,
    value: String,
    message: String = "$label copied"
) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

internal fun appHeaderLogoHueRotationDegrees(primary: Color): Float =
    normalizeHueDegrees(colorHueDegrees(primary) - colorHueDegrees(HeaderLogoBaseBlue))

private fun appHeaderLogoColorFilter(primary: Color): ColorFilter =
    ColorFilter.colorMatrix(hueRotationMatrix(appHeaderLogoHueRotationDegrees(primary)))

private fun colorHueDegrees(color: Color): Float {
    val red = color.red
    val green = color.green
    val blue = color.blue
    val max = maxOf(red, green, blue)
    val min = minOf(red, green, blue)
    val delta = max - min
    if (delta == 0f) return 0f

    val hue = when (max) {
        red -> ((green - blue) / delta) % 6f
        green -> ((blue - red) / delta) + 2f
        else -> ((red - green) / delta) + 4f
    } * 60f
    return if (hue < 0f) hue + 360f else hue
}

internal fun displayFormulaForStyle(state: ChemUiState, style: FormulaDisplayStyle): String {
    val rawFormula = runCatching { state.rawFormula }.getOrDefault("").orEmpty()
    return when (style) {
        FormulaDisplayStyle.CONVENTIONAL -> appendFormulaCharge(state.formula, state.charge)
        FormulaDisplayStyle.HILL -> rawFormula.ifBlank { appendFormulaCharge(state.formula, state.charge) }
    }
}

private fun formulaChargeSuffix(charge: Int): String =
    when {
        charge > 0 -> if (charge == 1) "+" else "^${charge}+"
        charge < 0 -> if (charge == -1) "-" else "^${-charge}-"
        else -> ""
    }

private fun appendFormulaCharge(formula: String, charge: Int): String {
    val cleanFormula = formula.trim()
    if (cleanFormula.isBlank() || charge == 0) return cleanFormula
    val hasChargeTail = listOf(
        caretChargeRegex,
        signThenDigitsChargeRegex,
        digitsThenSignChargeRegex,
        signOnlyChargeRegex
    ).any { regex -> regex.find(cleanFormula)?.range?.last == cleanFormula.lastIndex }
    return if (hasChargeTail) cleanFormula else cleanFormula + formulaChargeSuffix(charge)
}

private fun normalizeHueDegrees(degrees: Float): Float =
    when {
        degrees > 180f -> degrees - 360f
        degrees < -180f -> degrees + 360f
        else -> degrees
    }

private fun hueRotationMatrix(degrees: Float): ColorMatrix {
    val radians = (degrees * PI / 180.0).toFloat()
    val cosine = cos(radians)
    val sine = sin(radians)
    val luminanceRed = 0.213f
    val luminanceGreen = 0.715f
    val luminanceBlue = 0.072f

    return ColorMatrix(
        floatArrayOf(
            luminanceRed + cosine * (1 - luminanceRed) + sine * (-luminanceRed),
            luminanceGreen + cosine * (-luminanceGreen) + sine * (-luminanceGreen),
            luminanceBlue + cosine * (-luminanceBlue) + sine * (1 - luminanceBlue),
            0f,
            0f,
            luminanceRed + cosine * (-luminanceRed) + sine * 0.143f,
            luminanceGreen + cosine * (1 - luminanceGreen) + sine * 0.140f,
            luminanceBlue + cosine * (-luminanceBlue) + sine * -0.283f,
            0f,
            0f,
            luminanceRed + cosine * (-luminanceRed) + sine * -(1 - luminanceRed),
            luminanceGreen + cosine * (-luminanceGreen) + sine * luminanceGreen,
            luminanceBlue + cosine * (1 - luminanceBlue) + sine * luminanceBlue,
            0f,
            0f,
            0f,
            0f,
            0f,
            1f,
            0f
        )
    )
}

@Composable
fun AppHeader(isDark: Boolean, onToggleTheme: () -> Unit) {
    val compact = LocalCompactMode.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.chemsearch),
                contentDescription = null,
                colorFilter = appHeaderLogoColorFilter(MaterialTheme.colorScheme.primary),
                modifier = Modifier.size(if (compact) 40.dp else 46.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(stringResource(R.string.ui_chemsearch_2),
                    style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.sp
                )
                Text(stringResource(R.string.ui_chemistry_simplified),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = if (compact) 1.4.sp else 1.8.sp,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (compact) 8.sp else 9.sp
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            HeaderIconButton(onClick = onToggleTheme, icon = if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode, description = stringResource(R.string.ui_toggle_theme))
        }
    }
}

@Composable
private fun HeaderIconButton(onClick: () -> Unit, icon: ImageVector, description: String) {
    val compact = LocalCompactMode.current
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(if (compact) 34.dp else 38.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurface.copy(0.55f),
            modifier = Modifier.size(if (compact) 18.dp else 20.dp)
        )
    }
}

// Search bar

@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    onAdvancedSearch: (() -> Unit)? = null
) {
    val compact = LocalCompactMode.current
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (compact) 58.dp else 66.dp),
        placeholder = {
            Text(stringResource(R.string.ui_search_any_compound),
                color = MaterialTheme.colorScheme.onSurface.copy(0.38f),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
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
                        Icon(
                            Icons.Default.Close,
                            stringResource(R.string.ui_clear),
                            tint = MaterialTheme.colorScheme.onSurface.copy(0.45f),
                            modifier = Modifier.size(if (compact) 16.dp else 18.dp)
                        )
                    }
                }
                if (onAdvancedSearch != null) {
                    IconButton(onClick = onAdvancedSearch) {
                        Box(
                            modifier = Modifier
                                .size(if (compact) 34.dp else 40.dp)
                                .background(MaterialTheme.colorScheme.primary.copy(0.1f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.FilterList,
                                stringResource(R.string.ui_advanced_search),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(if (compact) 17.dp else 20.dp)
                            )
                        }
                    }
                }
                IconButton(onClick = onSearch) {
                    Box(
                        modifier = Modifier
                            .size(if (compact) 34.dp else 42.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            stringResource(R.string.ui_search),
                            tint = Color.White,
                            modifier = Modifier.size(if (compact) 14.dp else 16.dp)
                        )
                    }
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        shape = RoundedCornerShape(if (compact) 22.dp else 28.dp),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
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
    val compact = LocalCompactMode.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(if (compact) 14.dp else 16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (compact) 5.dp else 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.heightIn(max = if (compact) 232.dp else 280.dp).verticalScroll(rememberScrollState())) {
            suggestions.forEachIndexed { index, suggestion ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(suggestion) }
                        .padding(
                            horizontal = if (compact) 12.dp else 16.dp,
                            vertical = if (compact) 8.dp else 12.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 12.dp)
                ) {
                    Icon(
                        Icons.Default.Science,
                        null,
                        tint = MaterialTheme.colorScheme.primary.copy(0.7f),
                        modifier = Modifier.size(if (compact) 13.dp else 15.dp)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchCorrectionCard(
    failedQuery: String?,
    suggestions: List<String>,
    onSelect: (String) -> Unit
) {
    if (suggestions.isEmpty()) return
    val compact = LocalCompactMode.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = if (compact) 2.dp else 4.dp,
                vertical = if (compact) 2.dp else 4.dp
            ),
        verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 3.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 6.dp)) {
            Text(
                failedQuery?.let { stringResource(R.string.ui_no_match_for, it) } ?: stringResource(R.string.ui_no_matches_found),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(0.56f),
                fontWeight = FontWeight.SemiBold
            )
            Text(stringResource(R.string.ui_did_you_mean),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(0.42f)
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 5.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 5.dp)
        ) {
            suggestions.forEach { suggestion ->
                Surface(
                    onClick = { onSelect(suggestion) },
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.primary.copy(0.10f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(0.24f))
                ) {
                    Text(
                        suggestion,
                        modifier = Modifier.padding(
                            horizontal = if (compact) 8.dp else 10.dp,
                            vertical = if (compact) 4.dp else 5.dp
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// History (Recents)

private enum class RecentSortMode(val labelRes: Int) {
    NEWEST(R.string.ui_newest_first),
    OLDEST(R.string.ui_oldest_first)
}

@Composable
fun HistorySection(
    recentSearches: List<RecentSearch>,
    onSelect: (String) -> Unit,
    onClear: () -> Unit,
    onDelete: (String) -> Unit,
    onTogglePin: (String) -> Unit
) {
    var showClearConfirm by remember { mutableStateOf(false) }
    var sortMode by remember { mutableStateOf(RecentSortMode.NEWEST) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    val displayGroups = remember(recentSearches, sortMode) {
        groupRecentSearches(
            recentSearches,
            newestFirst = sortMode == RecentSortMode.NEWEST
        )
    }
    val compact = LocalCompactMode.current
    val recentDividerColor = if (MaterialTheme.colorScheme.surface.luminance() < 0.35f) {
        Color.White.copy(alpha = 0.10f)
    } else {
        Color.Black.copy(alpha = 0.08f)
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(stringResource(R.string.ui_clear_recent_searches), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.ui_this_removes_your_recent_search_list_on_this)) },
            confirmButton = {
                Button(
                    onClick = {
                        showClearConfirm = false
                        onClear()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.ui_clear_all)) }
            },
            dismissButton = { TextButton(onClick = { showClearConfirm = false }) { Text(stringResource(R.string.ui_cancel)) } },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    if (recentSearches.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = if (compact) 28.dp else 64.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(if (compact) 68.dp else 80.dp)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(0.08f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Science,
                        null,
                        tint = MaterialTheme.colorScheme.primary.copy(0.5f),
                        modifier = Modifier.size(if (compact) 42.dp else 52.dp)
                    )
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp)
                ) {
                    Text(stringResource(R.string.ui_search_any_compound_2),
                        style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.7f)
                    )
                    Text(stringResource(R.string.ui_type_a_compound_name_formula_cid_or_cas),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.38f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.History,
                    null,
                    tint = MaterialTheme.colorScheme.primary.copy(0.7f),
                    modifier = Modifier.size(if (compact) 16.dp else 18.dp)
                )
                Text(stringResource(R.string.ui_recent_searches),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    IconButton(
                        onClick = { sortMenuExpanded = true },
                        modifier = Modifier.size(if (compact) 34.dp else 38.dp)
                    ) {
                        Icon(
                            Icons.Default.FilterList,
                            contentDescription = stringResource(R.string.ui_sort_recents),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(if (compact) 18.dp else 20.dp)
                        )
                    }
                    SettingsDropdownMenu(
                        expanded = sortMenuExpanded,
                        onDismissRequest = { sortMenuExpanded = false }
                    ) {
                        RecentSortMode.entries.forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(stringResource(mode.labelRes)) },
                                leadingIcon = {
                                    if (sortMode == mode) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                },
                                onClick = {
                                    sortMode = mode
                                    sortMenuExpanded = false
                                }
                            )
                        }
                    }
                }
                TextButton(
                    onClick = { showClearConfirm = true },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(stringResource(R.string.ui_clear_all_2),
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (displayGroups.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(if (compact) 14.dp else 18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(if (compact) 0.92f else 0.96f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(0.10f))
            ) {
                Column {
                    val totalRows = displayGroups.sumOf { it.searches.size }
                    var renderedRows = 0
                    displayGroups.forEach { group ->
                        Text(
                            group.label,
                            modifier = Modifier.padding(
                                start = if (compact) 10.dp else 12.dp,
                                end = if (compact) 10.dp else 12.dp,
                                top = if (renderedRows == 0) 10.dp else 12.dp,
                                bottom = if (compact) 3.dp else 4.dp
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary.copy(0.76f),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        group.searches.forEach { item ->
                            val display = item.query.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(item.query) }
                                    .padding(
                                        horizontal = if (compact) 10.dp else 12.dp,
                                        vertical = if (compact) 7.dp else 9.dp
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(if (compact) 8.dp else 10.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(if (item.pinned) 0.16f else 0.10f)
                                ) {
                                    Icon(
                                        Icons.Default.History,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(if (compact) 6.dp else 7.dp).size(if (compact) 14.dp else 16.dp)
                                    )
                                }
                                Text(
                                    display,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                IconButton(onClick = { onTogglePin(item.query) }, modifier = Modifier.size(if (compact) 28.dp else 32.dp)) {
                                    Icon(
                                        if (item.pinned) Icons.Default.PushPin else Icons.Default.PushPinBorder,
                                        contentDescription = if (item.pinned) stringResource(R.string.ui_unpin) else stringResource(R.string.ui_pin),
                                        tint = if (item.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(0.42f),
                                        modifier = Modifier.size(if (compact) 16.dp else 18.dp)
                                    )
                                }
                                IconButton(onClick = { onDelete(item.query) }, modifier = Modifier.size(if (compact) 28.dp else 32.dp)) {
                                    Icon(
                                        Icons.Default.DeleteOutline,
                                        contentDescription = stringResource(R.string.ui_remove),
                                        tint = MaterialTheme.colorScheme.error.copy(0.6f),
                                        modifier = Modifier.size(if (compact) 16.dp else 18.dp)
                                    )
                                }
                            }
                            renderedRows++
                            if (renderedRows < totalRows) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = if (compact) 10.dp else 12.dp),
                                    color = recentDividerColor
                                )
                            }
                        }
                    }
                    Text(
                        recentSearchCountLabel(recentSearches.size),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = if (compact) 3.dp else 5.dp, bottom = if (compact) 7.dp else 9.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.44f),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun FormulaHeaderRow(
    formula: String,
    compact: Boolean,
    onCopyFormula: () -> Unit,
    onFormulaClick: (() -> Unit)?
) {
    InlineFormulaLayout(
        modifier = Modifier.fillMaxWidth(),
        horizontalGap = if (compact) 6.dp else 8.dp,
        verticalGap = if (compact) 5.dp else 6.dp,
        formula = {
            Text(
                text = toWrappedSubscriptFormula(formula),
                style = MaterialTheme.typography.titleLarge.copy(
                    lineBreak = LineBreak.Simple,
                    hyphens = Hyphens.None
                ),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.ExtraBold,
                maxLines = Int.MAX_VALUE,
                overflow = TextOverflow.Visible,
                modifier = Modifier.clickable { onCopyFormula() }
            )
        },
        action = onFormulaClick?.let { click ->
            {
                Surface(
                    onClick = click,
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.primary.copy(0.09f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(0.18f))
                ) {
                    Row(
                        modifier = Modifier.padding(
                            horizontal = if (compact) 7.dp else 9.dp,
                            vertical = if (compact) 3.dp else 4.dp
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.Atom,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(0.75f),
                            modifier = Modifier.size(if (compact) 11.dp else 12.dp)
                        )
                        Text(stringResource(R.string.ui_find_isomers),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun InlineFormulaLayout(
    modifier: Modifier = Modifier,
    horizontalGap: Dp,
    verticalGap: Dp,
    formula: @Composable () -> Unit,
    action: (@Composable () -> Unit)?
) {
    Layout(
        modifier = modifier,
        content = {
            formula()
            if (action != null) action()
        }
    ) { measurables, constraints ->
        val formulaMeasurable = measurables.first()
        val actionMeasurable = measurables.getOrNull(1)
        val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val actionPlaceable = actionMeasurable?.measure(looseConstraints)
        val horizontalGapPx = if (actionPlaceable != null) horizontalGap.roundToPx() else 0
        val verticalGapPx = if (actionPlaceable != null) verticalGap.roundToPx() else 0
        val availableWidth = constraints.maxWidth

        val formulaSingleLineWidth = formulaMeasurable.maxIntrinsicWidth(constraints.maxHeight)
        val fitsInline = actionPlaceable == null ||
            formulaSingleLineWidth + horizontalGapPx + actionPlaceable.width <= availableWidth

        val formulaMaxWidth = if (fitsInline && actionPlaceable != null) {
            (availableWidth - horizontalGapPx - actionPlaceable.width).coerceAtLeast(0)
        } else {
            availableWidth
        }
        val formulaPlaceable = formulaMeasurable.measure(
            constraints.copy(minWidth = 0, maxWidth = formulaMaxWidth)
        )

        if (fitsInline) {
            val contentWidth = formulaPlaceable.width + horizontalGapPx + (actionPlaceable?.width ?: 0)
            val layoutWidth = contentWidth.coerceAtLeast(constraints.minWidth).coerceAtMost(availableWidth)
            val layoutHeight = maxOf(formulaPlaceable.height, actionPlaceable?.height ?: 0)
                .coerceAtLeast(constraints.minHeight)

            layout(layoutWidth, layoutHeight) {
                formulaPlaceable.placeRelative(0, (layoutHeight - formulaPlaceable.height) / 2)
                actionPlaceable?.placeRelative(
                    formulaPlaceable.width + horizontalGapPx,
                    (layoutHeight - actionPlaceable.height) / 2
                )
            }
        } else {
            val layoutWidth = maxOf(formulaPlaceable.width, actionPlaceable.width)
                .coerceAtLeast(constraints.minWidth)
                .coerceAtMost(availableWidth)
            val layoutHeight = (formulaPlaceable.height + verticalGapPx + actionPlaceable.height)
                .coerceAtLeast(constraints.minHeight)

            layout(layoutWidth, layoutHeight) {
                formulaPlaceable.placeRelative(0, 0)
                actionPlaceable.placeRelative(0, formulaPlaceable.height + verticalGapPx)
            }
        }
    }
}

// Compound header

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CompoundHeader(
    state: ChemUiState,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    isDownloaded: Boolean = false,
    isSavingOffline: Boolean = false,
    offlineDownloadProgress: Float? = null,
    onDownloadOffline: () -> Unit = {},
    onFormulaClick: (() -> Unit)? = null,
    formulaDisplayStyle: FormulaDisplayStyle = FormulaDisplayStyle.CONVENTIONAL
) {
    val context = LocalContext.current
    val cm = remember { context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }
    val compact = LocalCompactMode.current

    @Composable
    fun StatChip(
        icon: ImageVector,
        label: String,
        value: String,
        displayValue: String = value,
        modifier: Modifier = Modifier,
        onCopy: () -> Unit
    ) {
        Surface(
            modifier = modifier.heightIn(min = if (compact) 50.dp else 56.dp),
            shape = RoundedCornerShape(if (compact) 11.dp else 13.dp),
            color = MaterialTheme.colorScheme.primary.copy(0.08f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(0.18f)),
            onClick = {
                onCopy()
                Toast.makeText(context, context.getString(R.string.ui_s_copied, label), Toast.LENGTH_SHORT).show()
            }
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = if (compact) 5.dp else 6.dp,
                    vertical = if (compact) 6.dp else 7.dp
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(if (compact) 21.dp else 24.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(0.12f), RoundedCornerShape(7.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(if (compact) 12.dp else 13.dp))
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    Text(
                        if (label.startsWith("MW")) label else label.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.45f),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.3.sp,
                        maxLines = 1,
                        fontSize = if (label.length > 5) {
                            if (compact) 7.sp else 8.sp
                        } else {
                            if (compact) 8.sp else 9.sp
                        }
                    )
                    Text(
                        displayValue,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = if (compact) 11.sp else 12.sp),
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }

    @Composable
    fun StatePill(label: String) {
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(0.25f))
        ) {
            Text(
                label,
                modifier = Modifier.padding(
                    horizontal = if (compact) 7.dp else 8.dp,
                    vertical = if (compact) 2.dp else 3.dp
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(0.6f),
                fontWeight = FontWeight.SemiBold
            )
        }
    }

    SearchCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(if (compact) 12.dp else 16.dp),
        spacing = if (compact) 10.dp else 12.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 12.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(if (compact) 70.dp else 84.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.primary.copy(0f)
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 4.dp)
            ) {
                val displayName = remember(state.name) { formatCompoundTitle(state.name) }
                var isExpanded by remember(state.name) { mutableStateOf(false) }
                var canExpand by remember(state.name) { mutableStateOf(false) }
                val titleToggle = {
                    if (canExpand) isExpanded = !isExpanded
                }
                Text(
                    text = displayName,
                    style = (if (compact) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineLarge).copy(
                        lineBreak = LineBreak.Heading,
                        hyphens = Hyphens.None
                    ),
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                    overflow = if (isExpanded) TextOverflow.Visible else TextOverflow.Ellipsis,
                    modifier = if (canExpand) Modifier.clickable { titleToggle() } else Modifier,
                    onTextLayout = { layout ->
                        if (!isExpanded) canExpand = layout.hasVisualOverflow
                    }
                )
                if (canExpand) {
                    Text(
                        text = if (isExpanded) stringResource(R.string.ui_show_less) else stringResource(R.string.ui_show_full_name),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary.copy(0.6f),
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .clickable { titleToggle() }
                    )
                }
                val displayFormula = remember(state.formula, state.rawFormula, formulaDisplayStyle) {
                    displayFormulaForStyle(state, formulaDisplayStyle)
                }
                if (displayFormula.isNotBlank()) {
                    FormulaHeaderRow(
                        formula = displayFormula,
                        compact = compact,
                        onCopyFormula = {
                            cm.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.ui_clipboard_formula), displayFormula))
                            Toast.makeText(context, context.getString(R.string.ui_formula_copied), Toast.LENGTH_SHORT).show()
                        },
                        onFormulaClick = onFormulaClick
                    )
                }
                if (state.isCached || state.isOfflineDownload) {
                    Row(
                        modifier = Modifier.padding(top = if (compact) 2.dp else 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (state.isCached) {
                            StatePill(stringResource(R.string.ui_cached))
                        }
                        if (state.isOfflineDownload) {
                            StatePill(stringResource(R.string.ui_offline))
                        }
                    }
                }
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    onClick = onToggleFavorite,
                    shape = RoundedCornerShape(if (compact) 12.dp else 14.dp),
                    color = MaterialTheme.colorScheme.primary.copy(if (isFavorite) 0.16f else 0.08f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(if (isFavorite) 0.35f else 0.16f)),
                    modifier = Modifier.size(if (compact) 42.dp else 48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        AnimatedStateIcon(
                            selected = isFavorite,
                            selectedIcon = Icons.Default.Star,
                            unselectedIcon = Icons.Default.StarBorder,
                            selectedDescription = stringResource(R.string.ui_remove_favorite),
                            unselectedDescription = stringResource(R.string.ui_add_favorite),
                            tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(0.45f),
                            modifier = Modifier.size(if (compact) 22.dp else 24.dp)
                        )
                    }
                }
                Surface(
                    onClick = {
                        if (!isSavingOffline) {
                            onDownloadOffline()
                            Toast.makeText(context, context.getString(R.string.ui_saving_offline_copy), Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = !isSavingOffline,
                    shape = RoundedCornerShape(if (compact) 12.dp else 14.dp),
                    color = MaterialTheme.colorScheme.primary.copy(if (isDownloaded) 0.16f else 0.08f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(if (isDownloaded) 0.35f else 0.16f)),
                    modifier = Modifier.size(if (compact) 42.dp else 48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isSavingOffline) {
                            val progress = offlineDownloadProgress?.coerceIn(0f, 1f)
                            if (progress != null) {
                                CircularProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.size(if (compact) 30.dp else 34.dp),
                                    strokeWidth = 3.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.outline.copy(0.18f)
                                )
                            } else {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(if (compact) 17.dp else 19.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Icon(
                                Icons.Default.Download,
                                contentDescription = stringResource(R.string.ui_downloading_for_offline_use),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(if (compact) 14.dp else 16.dp)
                            )
                        } else {
                            AnimatedStateIcon(
                                selected = isDownloaded,
                                selectedIcon = Icons.Default.DownloadDone,
                                unselectedIcon = Icons.Default.Download,
                                selectedDescription = stringResource(R.string.ui_update_offline_download),
                                unselectedDescription = stringResource(R.string.ui_download_for_offline_use),
                                tint = if (isDownloaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(0.45f),
                                modifier = Modifier.size(if (compact) 21.dp else 23.dp)
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            state.cid?.let {
                StatChip(Icons.Default.Science, "CID", it.toString(), modifier = Modifier.weight(1f)) {
                    copyTextWithFeedback(context, "CID", it.toString())
                }
            }
            state.casNumber?.let {
                StatChip(Icons.Default.Info, "CAS", it, modifier = Modifier.weight(1f)) {
                    copyTextWithFeedback(context, "CAS", it)
                }
            }
            if (state.weight.isNotBlank()) {
                StatChip(Icons.Default.Scale, stringResource(R.string.ui_mw_g_mol), "${state.weight} g/mol", displayValue = state.weight, modifier = Modifier.weight(1f)) {
                    copyTextWithFeedback(context, "MW", "${state.weight} g/mol")
                }
            }
        }
        state.cid?.let { cid ->
            OutlinedButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://pubchem.ncbi.nlm.nih.gov/compound/$cid"))
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(if (compact) 12.dp else 14.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(0.18f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(0.08f),
                    contentColor = MaterialTheme.colorScheme.primary
                ),
                contentPadding = PaddingValues(vertical = if (compact) 8.dp else 12.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(if (compact) 16.dp else 18.dp)
                )
                Spacer(modifier = Modifier.width(if (compact) 6.dp else 8.dp))
                Text(
                    text = stringResource(R.string.ui_view_in_pubchem),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

private fun compoundShareText(state: ChemUiState, context: Context): String = buildString {
    appendLine(state.name.ifBlank { context.getString(R.string.ui_share_compound_name) })
    if (state.formula.isNotBlank()) appendLine(context.getString(R.string.ui_share_formula, state.formula))
    state.cid?.let { appendLine(context.getString(R.string.ui_share_cid, it)) }
    state.casNumber?.let { appendLine(context.getString(R.string.ui_share_cas, it)) }
    if (state.weight.isNotBlank()) appendLine(context.getString(R.string.ui_share_molecular_weight, state.weight))
    if (state.iupacName.isNotBlank()) appendLine(context.getString(R.string.ui_share_iupac, state.iupacName))
    state.cid?.let { appendLine(context.getString(R.string.ui_share_pubchem_url, "https://pubchem.ncbi.nlm.nih.gov/compound/$it")) }
}.trim()

@Composable
private fun CompactIdentifier(label: String, value: String, onCopy: () -> Unit) {
    val compact = LocalCompactMode.current
    Column(
        modifier = Modifier.clickable { onCopy() },
        verticalArrangement = Arrangement.spacedBy(if (compact) 1.dp else 2.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(0.4f),
            fontSize = if (compact) 8.sp else 9.sp,
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
    val context = LocalContext.current
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))) { append("$label: ") }
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)) { append(value) }
        },
        style = MaterialTheme.typography.bodyMedium,
        softWrap = false,
        modifier = Modifier.clickable {
            cm.setPrimaryClip(ClipData.newPlainText(label, value))
            Toast.makeText(context, context.getString(R.string.ui_s_copied, label), Toast.LENGTH_SHORT).show()
        }
    )
}

@Composable
fun PubChemCredits() {
    val context = LocalContext.current
    val compact = LocalCompactMode.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (compact) 6.dp else 8.dp, bottom = if (compact) 2.dp else 4.dp)
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
            modifier = Modifier.size(if (compact) 11.dp else 12.dp)
        )
        Spacer(Modifier.width(if (compact) 4.dp else 5.dp))
        Text(stringResource(R.string.ui_compound_data_from_pubchem_nih_ncbi),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(0.28f),
            fontSize = if (compact) 9.sp else 10.sp
        )
    }
}

private fun formatCompoundTitle(name: String): String {
    val upper = name.uppercase()
    return buildString(upper.length + 4) {
        for (ch in upper) {
            append(ch)
            if (ch == '-' || ch == ',' || ch == '/' || ch == '\u00B7') {
                append('\u200B')
            }
        }
    }
}

// Structure viewer

private fun safeDownloadName(rawName: String, fallback: String): String {
    val cleaned = rawName
        .lowercase(Locale.US)
        .replace(Regex("[^a-z0-9._-]+"), "_")
        .trim('_', '.', '-')
        .take(80)
    return cleaned.ifBlank { fallback }
}

private suspend fun saveSdfFile(context: Context, compoundName: String, sdfData: String) {
    val fileName = "${safeDownloadName(compoundName, "compound")}_3d.sdf"
    try {
        withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "chemical/x-mdl-sdfile")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: error("Could not create destination file")
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                    writer.write(sdfData)
                } ?: error("Unable to open output stream")
            } else {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                File(dir, fileName).bufferedWriter().use { writer ->
                    writer.write(sdfData)
                }
            }
        }
        withContext(Dispatchers.Main) {
            Toast.makeText(context, context.getString(R.string.ui_saved_s_to_downloads, fileName), Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, context.getString(R.string.ui_download_failed_s, e.message ?: ""), Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
private fun StructurePngImage(
    cid: Long,
    offlinePngBase64: String?,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val offlineBitmap = remember(offlinePngBase64) {
        offlinePngBase64?.let { encoded ->
            runCatching {
                val bytes = Base64.decode(encoded, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }.getOrNull()
        }
    }

    if (offlineBitmap != null) {
        Image(
            bitmap = offlineBitmap,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Fit
        )
    } else {
        AsyncImage(
            model = "https://pubchem.ncbi.nlm.nih.gov/rest/pug/compound/cid/$cid/PNG?image_size=large",
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
fun StructureViewer(state: ChemUiState, vm: ChemViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val compact = LocalCompactMode.current

    // Runtime storage permission for Android 9 and below
    val writePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            scope.launch(Dispatchers.IO) {
                saveSdfFile(context, state.name, state.sdfData ?: return@launch)
            }
        } else {
            Toast.makeText(context, context.getString(R.string.ui_storage_permission_denied), Toast.LENGTH_LONG).show()
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

    fun trigger2dDownload(cid: Long) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            writePermissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            scope.launch(Dispatchers.IO) {
                save2dPng(context, state.name, cid)
            }
        }
    }

    fun shareCompound() {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, compoundShareText(state, context))
        }
        context.startActivity(Intent.createChooser(sendIntent, context.getString(R.string.ui_share_compound)))
    }

    @Composable
    fun ViewerActionButton(icon: ImageVector, label: String, onClick: () -> Unit) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.surface.copy(0.94f),
            shadowElevation = 4.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(0.16f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = if (compact) 12.dp else 16.dp, vertical = if (compact) 7.dp else 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(if (compact) 15.dp else 17.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }

    SearchCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(0.dp),
        spacing = 0.dp
    ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = if (compact) 8.dp else 12.dp,
                        vertical = if (compact) 8.dp else 10.dp
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp)
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)
                ) {
                    val twoDActive = state.activeTab == MolTab.TWO_D
                    Surface(
                        onClick = { vm.setTab(MolTab.TWO_D) },
                        shape = RoundedCornerShape(50),
                        color = if (twoDActive) MaterialTheme.colorScheme.primary else Color.Transparent,
                        border = BorderStroke(
                            1.dp,
                            if (twoDActive) MaterialTheme.colorScheme.primary.copy(0.45f)
                            else MaterialTheme.colorScheme.onSurface.copy(0.18f)
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.ui_2d_structure_2),
                            modifier = Modifier.padding(vertical = if (compact) 7.dp else 8.dp),
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
                        border = BorderStroke(
                            1.dp,
                            if (threeDActive) MaterialTheme.colorScheme.primary.copy(0.45f)
                            else MaterialTheme.colorScheme.onSurface.copy(0.18f)
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.ui_3d_model),
                            modifier = Modifier.padding(vertical = if (compact) 7.dp else 8.dp),
                            color = if (threeDActive) Color.White else MaterialTheme.colorScheme.onSurface.copy(0.55f),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                IconButton(
                    onClick = { shareCompound() },
                    modifier = Modifier.size(if (compact) 38.dp else 44.dp)
                ) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = stringResource(R.string.ui_share_compound),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(if (compact) 18.dp else 20.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (compact) 260.dp else 300.dp)
                    .clipToBounds(),
                contentAlignment = Alignment.Center
            ) {
                when (state.activeTab) {
                    MolTab.TWO_D -> state.cid?.let { cid ->
                        var showZoom by remember { mutableStateOf(false) }

                        if (showZoom) {
                            TwoDZoomDialog(
                                cid = cid,
                                offlinePngBase64 = state.offline2dPngBase64,
                                onDismiss = { showZoom = false }
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(
                                    start = if (compact) 10.dp else 14.dp,
                                    end = if (compact) 10.dp else 14.dp,
                                    bottom = if (compact) 10.dp else 14.dp
                                )
                        ) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable { showZoom = true },
                                shape = RoundedCornerShape(if (compact) 16.dp else 20.dp),
                                color = PubChemStructureBackground,
                                shadowElevation = 1.dp
                            ) {
                                StructurePngImage(
                                    cid = cid,
                                    offlinePngBase64 = state.offline2dPngBase64,
                                    contentDescription = stringResource(R.string.ui_2d_structure),
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(if (compact) 14.dp else 18.dp)
                                )
                            }
                            Surface(
                                onClick = { trigger2dDownload(cid) },
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(
                                        end = if (compact) 12.dp else 16.dp,
                                        bottom = if (compact) 12.dp else 16.dp
                                    )
                                    .size(if (compact) 38.dp else 42.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                shadowElevation = 4.dp
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Download,
                                        contentDescription = stringResource(R.string.ui_download_2d_structure),
                                        tint = Color.White,
                                        modifier = Modifier.size(if (compact) 17.dp else 19.dp)
                                    )
                                }
                            }
                        }
                    }
                    MolTab.THREE_D -> {
                        if (state.isLoadingSdf) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(if (compact) 24.dp else 28.dp),
                                    strokeWidth = 2.dp
                                )
                                Text(
                                    state.sdfMessage ?: stringResource(R.string.ui_loading_3d_model),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(0.4f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else if (state.sdfData != null) {
                            val isDark = !MaterialTheme.colorScheme.background.luminance().let { it > 0.5f }
                            Viewer3D(cid = state.cid ?: 0, sdfData = state.sdfData, isDark = isDark)
                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = if (compact) 10.dp else 14.dp),
                                horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ViewerActionButton(Icons.Default.Download, stringResource(R.string.ui_download_sdf)) { triggerDownload() }
                            }
                        } else {
                            var showWhyDialog by remember { mutableStateOf(false) }
                            val structureStatus = remember(state.sdfMessage) {
                                describeStructureStatus(hasSdf = false, source = null, message = state.sdfMessage)
                            }

                            if (showWhyDialog) {
                                No3DModelDialog(onDismiss = { showWhyDialog = false })
                            }

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)
                            ) {
                                Icon(
                                    Icons.Default.VisibilityOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface.copy(0.3f),
                                    modifier = Modifier.size(if (compact) 28.dp else 32.dp)
                                )
                                Text(stringResource(R.string.ui_3d_model_not_available_2),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(0.4f)
                                )
                                Text(
                                    structureStatus.detailMessage ?: stringResource(structureStatus.detailRes),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(0.36f),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.widthIn(max = if (compact) 230.dp else 280.dp)
                                )
                                Text(stringResource(R.string.ui_learn_why),
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

@Composable
fun TwoDZoomDialog(
    cid: Long,
    offlinePngBase64: String? = null,
    onDismiss: () -> Unit
) {
    var scale by remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
    var offsetX by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    var offsetY by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(androidx.compose.ui.graphics.Color.Black.copy(0.92f))
                .clickable(
                    indication = null,
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                ) { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(0.5f, 6f)
                            offsetX += pan.x
                            offsetY += pan.y
                        }
                    }
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY
                    )
                    .clickable(
                        indication = null,
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                    ) { }
            ) {
                StructurePngImage(
                    cid = cid,
                    offlinePngBase64 = offlinePngBase64,
                    contentDescription = stringResource(R.string.ui_2d_structure_zoom),
                    modifier = Modifier.fillMaxSize().padding(16.dp)
                )
            }


            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(0.8f)
                ) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.ui_close),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface.copy(0.7f)
                ) {
                    Text(stringResource(R.string.ui_pinch_to_zoom_tap_outside_to_close),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.7f)
                    )
                }
            }
        }
    }
}

@Composable
private fun StructureStatusBadge(status: StructureStatus, modifier: Modifier = Modifier) {
    val compact = LocalCompactMode.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surface.copy(0.92f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(if (status.estimated) 0.36f else 0.24f))
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) 8.dp else 10.dp,
                vertical = if (compact) 5.dp else 6.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                if (status.estimated) Icons.Default.AutoFixHigh else Icons.Default.ViewInAr,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(if (compact) 13.dp else 14.dp)
            )
            Text(
                stringResource(status.labelRes),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
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
                Text(stringResource(R.string.ui_got_it), fontWeight = FontWeight.SemiBold)
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
            Text(stringResource(R.string.ui_why_is_3d_unavailable),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.ui_pubchem_pre_computes_3d_conformer_models_for_90) +
                            stringResource(R.string.ui_3d_unavailable_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                )

                val reasons = listOf(
                    R.string.ui_3d_reason_too_large,
                    R.string.ui_3d_reason_too_flexible,
                    R.string.ui_3d_reason_unsupported_elements,
                    R.string.ui_3d_reason_salt_or_mixture,
                    R.string.ui_3d_reason_crystal_or_metallic,
                    R.string.ui_3d_reason_too_many_undefined_stereo,
                    R.string.ui_3d_reason_conformer_failure,
                )

                reasons.forEach { textRes ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text("•", style = MaterialTheme.typography.bodySmall)
                        Text(
                            stringResource(textRes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                Text(stringResource(R.string.ui_source_pubchem3d_project),
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
            titleRes = R.string.ui_about_identifiers,
            entries = listOf(
                R.string.ui_info_iupac_name to R.string.ui_info_iupac_name_body,
                R.string.ui_info_cid to R.string.ui_info_cid_body,
                R.string.ui_info_cas_number to R.string.ui_info_cas_number_body,
                R.string.ui_info_condensed_formula to R.string.ui_info_condensed_formula_body,
                R.string.ui_info_smiles to R.string.ui_info_smiles_body,
                R.string.ui_info_inchi to R.string.ui_info_inchi_body,
                R.string.ui_info_inchikey to R.string.ui_info_inchikey_body,
                R.string.ui_info_empirical_formula to R.string.ui_info_empirical_formula_body,
                R.string.ui_info_atom_number to R.string.ui_info_atom_number_body,
                R.string.ui_info_bond_number to R.string.ui_info_bond_number_body,
                R.string.ui_info_formal_charge to R.string.ui_info_formal_charge_body
            ),
            onDismiss = { showInfo = false }
        )
    }

    SearchCard(modifier = Modifier.fillMaxWidth(), spacing = 10.dp) {
        CardSectionHeader(stringResource(R.string.ui_identifiers)) { showInfo = true }
        val condensedFormula = remember(state.connectivitySmiles, state.smiles) {
            buildBestCondensedFormula(state.connectivitySmiles, state.smiles)
        }
        val rows = buildList {
            if (!condensedFormula.isNullOrBlank()) add(Triple(stringResource(R.string.ui_condensed_formula_label), toChemicalExpressionDisplay(condensedFormula), true))
            if (state.iupacName.isNotBlank()) add(Triple(stringResource(R.string.ui_iupac_name_label), state.iupacName, false))
            if (state.connectivitySmiles.isNotBlank()) add(Triple(stringResource(R.string.ui_smiles_connectivity_label), state.connectivitySmiles, true))
            if (state.smiles.isNotBlank() && state.smiles != state.connectivitySmiles) add(Triple(stringResource(R.string.ui_smiles_full_label), state.smiles, true))
            if (state.inchiKey.isNotBlank()) add(Triple(stringResource(R.string.ui_inchikey_label), state.inchiKey, true))
            if (state.inchi.isNotBlank()) add(Triple(stringResource(R.string.ui_inchi_label), state.inchi, true))
            if (state.empiricalFormula.isNotBlank() && state.empiricalFormula != state.formula) add(Triple(stringResource(R.string.ui_empirical_formula_label), toSubscriptFormula(state.empiricalFormula), true))
            state.atomNumber?.let { add(Triple(stringResource(R.string.ui_atom_number_label), it.toString(), true)) }
            state.bondNumber?.let { add(Triple(stringResource(R.string.ui_bond_number_label), it.toString(), true)) }
            if (state.charge != 0) add(Triple(stringResource(R.string.ui_formal_charge_label), state.charge.toString(), true))
        }
        rows.forEach { (label, value, mono) ->
            IdentifierRow(label, value, context, mono = mono)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClassificationTagsSection(tags: List<String>, isLoading: Boolean) {
    if (tags.isEmpty() && !isLoading) return
    var showInfo by remember { mutableStateOf(false) }
    val compact = LocalCompactMode.current
    if (showInfo) {
        InfoDialog(
            titleRes = R.string.ui_classification,
            entries = classificationInfoEntries(),
            onDismiss = { showInfo = false }
        )
    }
    SearchCard(modifier = Modifier.fillMaxWidth(), spacing = if (compact) 8.dp else 10.dp) {
        CardSectionHeader(stringResource(R.string.ui_classification)) { showInfo = true }
        if (tags.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp),
                verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)
            ) {
                tags.forEach { tag ->
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.primary.copy(0.08f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(0.20f))
                    ) {
                        Text(
                            tag,
                            modifier = Modifier.padding(
                                horizontal = if (compact) 8.dp else 10.dp,
                                vertical = if (compact) 4.dp else 5.dp
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        } else {
            Text(stringResource(R.string.ui_loading_pubchem_categories),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(0.50f)
            )
        }
    }
}

@Composable
fun UsesAndOccurrenceSection(entries: List<CompoundUseEntry>, isLoading: Boolean) {
    if (entries.isEmpty() && !isLoading) return
    var showInfo by remember { mutableStateOf(false) }
    var expanded by remember(entries) { mutableStateOf(false) }
    val compact = LocalCompactMode.current
    if (showInfo) {
        InfoDialog(
            titleRes = R.string.ui_uses_and_occurrence,
            entries = usesOccurrenceInfoEntries(),
            onDismiss = { showInfo = false }
        )
    }
    SearchCard(modifier = Modifier.fillMaxWidth(), spacing = if (compact) 8.dp else 10.dp) {
        CardSectionHeader(stringResource(R.string.ui_uses_and_occurrence)) { showInfo = true }
        if (entries.isEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.ui_loading_pubchem_use_notes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.50f)
                )
            }
        } else {
            val visibleEntries = if (expanded) entries else entries.take(2)
            val bulletLines = remember(visibleEntries) { compoundUseBulletLines(visibleEntries) }
            Column(verticalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 9.dp)) {
                bulletLines.forEach { line ->
                    Text(
                        line,
                        style = MaterialTheme.typography.bodySmall,
                        lineHeight = if (compact) 17.sp else 18.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.78f),
                        maxLines = if (expanded) Int.MAX_VALUE else 3,
                        overflow = if (expanded) TextOverflow.Visible else TextOverflow.Ellipsis
                    )
                }
            }
            if (entries.size > 2) {
                TextButton(
                    onClick = { expanded = !expanded },
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
                ) {
                    Text(
                        if (expanded) stringResource(R.string.ui_show_less) else stringResource(R.string.ui_show_more_count, entries.size - 2),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Text(stringResource(R.string.ui_source_pubchem_annotations),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(0.42f)
            )
        }
    }
}

@Composable
fun AdvancedPropertiesSection(properties: List<AdvancedPropertyRow>, isLoading: Boolean) {
    if (properties.isEmpty() && !isLoading) return
    var showInfo by remember { mutableStateOf(false) }
    var expanded by remember(properties) { mutableStateOf(false) }
    val compact = LocalCompactMode.current
    if (showInfo) {
        InfoDialog(
            titleRes = R.string.ui_advanced_properties,
            entries = advancedPropertiesInfoEntries(),
            onDismiss = { showInfo = false }
        )
    }
    SearchCard(modifier = Modifier.fillMaxWidth(), spacing = if (compact) 8.dp else 10.dp) {
        CardSectionHeader(stringResource(R.string.ui_advanced_properties)) { showInfo = true }
        if (properties.isEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.ui_loading_pubchem_properties),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.50f)
                )
            }
        } else {
            val visibleRows = if (expanded) properties else properties.take(8)
            visibleRows.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)
                ) {
                    row.forEach { property ->
                        AdvancedPropertyTile(
                            property = property,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            if (properties.size > 8) {
                TextButton(
                    onClick = { expanded = !expanded },
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
                ) {
                    Text(
                        if (expanded) stringResource(R.string.ui_show_less) else stringResource(R.string.ui_show_more_count, properties.size - 8),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

internal fun advancedPropertiesInfoEntries(): List<Pair<Int, Int>> = listOf(
    R.string.ui_info_what_this_shows to R.string.ui_info_advanced_properties_what_body,
    R.string.ui_info_why_hidden_by_default to R.string.ui_info_advanced_properties_why_hidden_body,
    R.string.ui_info_xlogp to R.string.ui_info_xlogp_body,
    R.string.ui_info_tpsa to R.string.ui_info_tpsa_body,
    R.string.ui_info_complexity to R.string.ui_info_complexity_body,
    R.string.ui_info_mass_values to R.string.ui_info_mass_values_body,
    R.string.ui_info_3d_values to R.string.ui_info_3d_values_body
)

internal fun classificationInfoEntries(): List<Pair<Int, Int>> = listOf(
    R.string.ui_info_what_tags_mean to R.string.ui_info_what_tags_mean_body,
    R.string.ui_info_why_tags_look_strange to R.string.ui_info_why_tags_look_strange_body,
    R.string.ui_info_how_to_read_tags to R.string.ui_info_how_to_read_tags_body,
    R.string.ui_info_why_classification_empty to R.string.ui_info_why_classification_empty_body
)

internal fun usesOccurrenceInfoEntries(): List<Pair<Int, Int>> = listOf(
    R.string.ui_info_what_this_shows to R.string.ui_info_uses_what_body,
    R.string.ui_info_why_bullets_vary to R.string.ui_info_why_bullets_vary_body,
    R.string.ui_info_safety_note to R.string.ui_info_uses_safety_note_body,
    R.string.ui_info_why_uses_empty to R.string.ui_info_why_uses_empty_body
)

internal fun identifierDividerAlpha(isDarkSurface: Boolean): Float =
    if (isDarkSurface) 0.10f else 0.08f

@Composable
private fun AdvancedPropertyTile(property: AdvancedPropertyRow, modifier: Modifier = Modifier) {
    val compact = LocalCompactMode.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(if (compact) 10.dp else 12.dp),
        color = MaterialTheme.colorScheme.primary.copy(0.07f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(0.14f))
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = if (compact) 8.dp else 10.dp,
                vertical = if (compact) 7.dp else 9.dp
            ),
            verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 3.dp)
        ) {
            Text(
                property.label,
                modifier = Modifier.heightIn(min = if (compact) 26.dp else 30.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(0.52f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                property.value,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(0.88f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun IdentifierRow(label: String, value: String, context: Context, mono: Boolean = true) {
    var expanded by remember { mutableStateOf(false) }
    val isLong = value.length > 80
    val compact = LocalCompactMode.current
    val dividerColor = if (MaterialTheme.colorScheme.surface.luminance() < 0.35f) {
        Color.White.copy(alpha = identifierDividerAlpha(isDarkSurface = true))
    } else {
        Color.Black.copy(alpha = identifierDividerAlpha(isDarkSurface = false))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                when {
                    isLong && !expanded -> expanded = true
                    isLong && expanded -> expanded = false
                    else -> copyTextWithFeedback(context, label, value)
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
                    if (expanded) stringResource(R.string.ui_collapse) else stringResource(R.string.ui_expand),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary.copy(0.7f),
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable { expanded = !expanded }
                )
            }
        }
        Spacer(Modifier.height(if (compact) 2.dp else 3.dp))
        Text(
            value,
            style = if (mono) MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace) else MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.onSurface.copy(0.88f),
            lineHeight = if (compact) 16.sp else 18.sp,
            maxLines = if (expanded || !isLong) Int.MAX_VALUE else 2,
            overflow = if (expanded || !isLong) androidx.compose.ui.text.style.TextOverflow.Visible else androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
        if (isLong && expanded) {
            Spacer(Modifier.height(if (compact) 3.dp else 4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = { copyTextWithFeedback(context, label, value) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.ui_copy), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(top = if (compact) 4.dp else 6.dp),
            color = dividerColor
        )
    }
}

// Elemental analysis (Percentage Composition)

@Composable
fun ElementalSection(data: List<ElementData>) {
    val compact = LocalCompactMode.current
    SearchCard(modifier = Modifier.fillMaxWidth(), spacing = 10.dp) {
        var showInfo by remember { mutableStateOf(false) }
        if (showInfo) {
            InfoDialog(
                titleRes = R.string.ui_elemental_analysis,
                entries = listOf(
                    R.string.ui_info_elemental_analysis_what to R.string.ui_info_elemental_analysis_what_body,
                    R.string.ui_info_elemental_analysis_example to R.string.ui_info_elemental_analysis_example_body,
                    R.string.ui_info_elemental_analysis_why to R.string.ui_info_elemental_analysis_why_body
                ),
                onDismiss = { showInfo = false }
            )
        }
        CardSectionHeader(stringResource(R.string.ui_elemental_analysis)) { showInfo = true }
        data.forEach { el ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(if (compact) 24.dp else 28.dp)
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
                        .height(if (compact) 8.dp else 10.dp)
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
                val percentageLabel = if (el.percentage >= 99.95f) "100%" else "${"%.1f".format(el.percentage)}%"
                Text(
                    percentageLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.width(if (compact) 40.dp else 44.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(0.55f),
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

// Synonyms

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SynonymsSection(synonyms: List<String>, isLoading: Boolean = false) {
    val context = LocalContext.current
    val cm = remember { context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }
    val compact = LocalCompactMode.current
    var visibleCount by remember(synonyms) { mutableIntStateOf(10) }
    val visibleSynonyms = synonyms.take(visibleCount)
    val remaining = (synonyms.size - visibleSynonyms.size).coerceAtLeast(0)
    SearchCard(modifier = Modifier.fillMaxWidth(), spacing = 10.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            SectionLabel(stringResource(R.string.ui_synonyms))
            if (synonyms.isNotEmpty()) {
                Text(
                    "${visibleSynonyms.size}/${synonyms.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.42f),
                    fontFamily = FontFamily.Monospace
                )
            }
        }
        if (isLoading && synonyms.isEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = if (compact) 4.dp else 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.ui_loading_synonyms),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.55f)
                )
            }
        }
        if (visibleSynonyms.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp),
                verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)
            ) {
                visibleSynonyms.forEach { syn ->
                    Surface(
                        shape = RoundedCornerShape(if (compact) 7.dp else 8.dp),
                        color = MaterialTheme.colorScheme.primary.copy(0.07f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(0.18f)),
                        modifier = Modifier.clickable {
                            cm.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.ui_clipboard_synonym), syn))
                            Toast.makeText(context, context.getString(R.string.ui_synonym_copied), Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text(
                            syn,
                            modifier = Modifier.padding(
                                horizontal = if (compact) 8.dp else 10.dp,
                                vertical = if (compact) 4.dp else 5.dp
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
        if (synonyms.isNotEmpty() && (remaining > 0 || visibleCount > 10 || isLoading)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (remaining > 0) {
                    TextButton(
                        onClick = { visibleCount = (visibleCount + 10).coerceAtMost(synonyms.size) },
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp)
                    ) {
                        Text(
                            stringResource(R.string.ui_show_more_count, remaining.coerceAtMost(10)),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                if (visibleCount > 10) {
                    TextButton(
                        onClick = { visibleCount = 10 },
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp)
                    ) {
                        Text(stringResource(R.string.ui_collapse),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.62f)
                        )
                    }
                }
                if (isLoading) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.ui_loading),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.45f)
                        )
                    }
                }
            }
        }
    }
}
// Description

@Composable
fun DescriptionSection(
    state: ChemUiState,
    onPubChem: () -> Unit,
    onWiki: () -> Unit,
    onAI: () -> Unit,
    onConfigureAI: () -> Unit,
    onRegenerate: () -> Unit
) {
    val compact = LocalCompactMode.current
    var expanded by remember(state.descSource, state.pubDescription, state.wikiDescription, state.aiDescription) { mutableStateOf(false) }
    SearchCard(modifier = Modifier.fillMaxWidth(), spacing = 12.dp) {
        SectionLabel(stringResource(R.string.ui_description))
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.outline.copy(0.1f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.padding(if (compact) 3.dp else 4.dp)) {
                listOf(
                    DescSource.PUBCHEM to stringResource(R.string.ui_pubchem),
                    DescSource.WIKI to stringResource(R.string.ui_wikipedia),
                    DescSource.AI to "AI"
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
                            modifier = Modifier.padding(vertical = if (compact) 6.dp else 7.dp),
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
                DescSource.PUBCHEM -> state.pubDescription ?: stringResource(R.string.ui_pubchem_desc_not_available)
                DescSource.WIKI    -> state.wikiDescription ?: stringResource(R.string.ui_wiki_desc_not_available)
                DescSource.AI      -> state.aiDescription   ?: stringResource(R.string.ui_ai_desc_not_available)
            }
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = if (compact) 20.sp else 22.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(0.85f),
                maxLines = if (expanded) Int.MAX_VALUE else 5,
                overflow = if (expanded) TextOverflow.Visible else TextOverflow.Ellipsis
            )
            if (text.length > 260) {
                TextButton(
                    onClick = { expanded = !expanded },
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
                ) {
                    Text(
                        if (expanded) stringResource(R.string.ui_read_less) else stringResource(R.string.ui_read_more),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            if (state.descSource == DescSource.AI && state.aiDescription != null) {
                if (state.aiDescriptionBasis.isNotEmpty()) {
                    Text(
                        stringResource(R.string.ui_based_on, state.aiDescriptionBasis.joinToString(", ")),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.48f),
                        lineHeight = if (compact) 15.sp else 16.sp
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onRegenerate, contentPadding = PaddingValues(0.dp)) {
                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(if (compact) 13.dp else 14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.ui_regenerate), style = MaterialTheme.typography.labelMedium)
                    }
                    Spacer(Modifier.weight(1f))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primary.copy(0.08f)
                    ) {
                        Text(
                            stringResource(R.string.ui_via, state.aiProvider.shortName),
                            modifier = Modifier.padding(
                                horizontal = if (compact) 6.dp else 8.dp,
                                vertical = if (compact) 2.dp else 3.dp
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary.copy(0.8f)
                        )
                    }
                }
            }
        }
        if (state.descSource == DescSource.AI) {
            OutlinedButton(
                onClick = onConfigureAI,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = if (compact) 6.dp else 8.dp)
            ) {
                Icon(Icons.Default.Settings, null, modifier = Modifier.size(if (compact) 14.dp else 16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.ui_configure_ai_provider_2),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
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

@Composable
private fun SearchCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    spacing: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val compact = LocalCompactMode.current
    val layoutDirection = LocalLayoutDirection.current
    val scale = if (compact) 0.62f else 1f
    val scaledInset: (Dp, Dp) -> Dp = { base, minimum ->
        if (base == 0.dp) 0.dp else (base * scale).coerceAtLeast(minimum)
    }
    val horizontalBase = contentPadding.calculateLeftPadding(layoutDirection)
    val rightBase = contentPadding.calculateRightPadding(layoutDirection)
    val topBase = contentPadding.calculateTopPadding()
    val bottomBase = contentPadding.calculateBottomPadding()
    val effectivePadding = PaddingValues(
        start = scaledInset(horizontalBase, 4.dp),
        top = scaledInset(topBase, 3.dp),
        end = scaledInset(rightBase, 4.dp),
        bottom = scaledInset(bottomBase, 3.dp)
    )
    val effectiveSpacing = if (spacing == 0.dp) 0.dp else (spacing * scale).coerceAtLeast(4.dp)
    val shape = RoundedCornerShape(if (compact) 16.dp else 22.dp)
    Surface(
        modifier = modifier,
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(0.16f))
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
                        )
                    )
                )
                .padding(effectivePadding),
            verticalArrangement = Arrangement.spacedBy(effectiveSpacing),
            content = content
        )
    }
}


@Composable
fun CardSectionHeader(label: String, onInfoClick: () -> Unit) {
    val compact = LocalCompactMode.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        SectionLabel(label)
        IconButton(onClick = onInfoClick, modifier = Modifier.size(if (compact) 18.dp else 20.dp)) {
            Icon(
                Icons.Default.Info,
                contentDescription = stringResource(R.string.ui_info),
                tint = MaterialTheme.colorScheme.onSurface.copy(0.3f),
                modifier = Modifier.size(if (compact) 13.dp else 14.dp)
            )
        }
    }
}

private val superscriptMap = mapOf(
    '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
    '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
    '+' to '⁺', '-' to '⁻'
)

private val formulaWrapGroupRegex = Regex("[A-Z][a-z]?\\d*")
private val caretChargeRegex = Regex("""\^([0-9]*[+-]+|[+-]+[0-9]*)$""")
private val signThenDigitsChargeRegex = Regex("""([+-][0-9]+)$""")
private val digitsThenSignChargeRegex = Regex("""([0-9]+[+-]+)$""")
private val signOnlyChargeRegex = Regex("""([+-]+)$""")
private val singleElementRegex = Regex("""^[A-Z][a-z]?$""")

private fun String.toFormulaDisplay(): String {
    val text = trim()
    if (text.isEmpty()) return this

    fun superscriptCharge(charge: String): String {
        val normalized = if (charge.length > 1 && (charge.first() == '+' || charge.first() == '-') && charge.drop(1).all { it.isDigit() }) {
            charge.drop(1) + charge.first()
        } else {
            charge
        }
        return normalized.map { superscriptMap[it] ?: it }.joinToString("")
    }

    caretChargeRegex.find(text)?.takeIf { it.range.last == text.lastIndex }?.let { match ->
        val base = text.substring(0, match.range.first)
        return base.toFormulaSubscript() + superscriptCharge(match.groupValues[1])
    }

    signThenDigitsChargeRegex.find(text)?.takeIf { it.range.last == text.lastIndex }?.let { match ->
        val base = text.substring(0, match.range.first)
        return base.toFormulaSubscript() + superscriptCharge(match.value)
    }

    digitsThenSignChargeRegex.find(text)?.takeIf { it.range.last == text.lastIndex }?.let { match ->
        val baseBeforeCharge = text.substring(0, match.range.first)
        val shouldTreatDigitsAsCharge = baseBeforeCharge.lastOrNull() == ')' ||
            baseBeforeCharge.lastOrNull() == ']' ||
            singleElementRegex.matches(baseBeforeCharge)
        if (shouldTreatDigitsAsCharge) {
            return baseBeforeCharge.toFormulaSubscript() + superscriptCharge(match.value)
        }
    }

    signOnlyChargeRegex.find(text)?.takeIf { it.range.last == text.lastIndex }?.let { match ->
        val base = text.substring(0, match.range.first)
        return base.toFormulaSubscript() + superscriptCharge(match.value)
    }

    return text.toFormulaSubscript()
}

fun toSubscriptFormula(formula: String): String {
    return formula.toFormulaDisplay()
}

fun toChemicalExpressionDisplay(expression: String): String {
    val normalized = expression
        .replace("<->", "⇌")
        .replace("↔", "⇌")
        .replace("⇄", "⇌")
        .replace("->", "⟶")
        .replace("→", "⟶")

    return Regex("""\S+""").replace(normalized) { match ->
        match.value.toChemicalExpressionTokenDisplay()
    }
}

private fun String.toChemicalExpressionTokenDisplay(): String {
    if (this == "+" || this == "⟶" || this == "⇌") return this

    val stateMatch = Regex("""^(.*?)(\((?:aq|s|l|g)\))$""").matchEntire(this)
    val withoutState = stateMatch?.groupValues?.get(1) ?: this
    val state = stateMatch?.groupValues?.get(2).orEmpty()

    val coefficientMatch = Regex("""^(\d+)([A-Z].*|e[+-])$""").matchEntire(withoutState)
    val coefficient = coefficientMatch?.groupValues?.get(1).orEmpty()
    val formula = coefficientMatch?.groupValues?.get(2) ?: withoutState

    return coefficient + formula.toFormulaDisplay() + state
}

fun toWrappedSubscriptFormula(formula: String): String {
    val trimmed = formula.trim()
    val hasChargeTail = listOf(
        caretChargeRegex,
        signThenDigitsChargeRegex,
        digitsThenSignChargeRegex,
        signOnlyChargeRegex
    ).any { regex -> regex.find(trimmed)?.range?.last == trimmed.lastIndex }
    if (hasChargeTail) return formula.toFormulaDisplay()

    val builder = StringBuilder()
    var lastIndex = 0

    formulaWrapGroupRegex.findAll(formula).forEach { match ->
        if (match.range.first > lastIndex) {
            builder.append(formula.substring(lastIndex, match.range.first).toFormulaDisplay())
        }
        builder.append(match.value.toFormulaDisplay())
        if (match.range.last < formula.lastIndex) {
            builder.append('\u200B')
        }
        lastIndex = match.range.last + 1
    }

    if (lastIndex < formula.length) {
        builder.append(formula.substring(lastIndex).toFormulaDisplay())
    }

    return builder.toString().trim('\u200B')
}

// GHS Safety

@Composable
private fun ghsLabelText(code: String): String = when (code) {
    "GHS01" -> stringResource(R.string.ui_ghs_label_explosive)
    "GHS02" -> stringResource(R.string.ui_ghs_label_flammable)
    "GHS03" -> stringResource(R.string.ui_ghs_label_oxidizing)
    "GHS04" -> stringResource(R.string.ui_ghs_label_compressed_gas)
    "GHS05" -> stringResource(R.string.ui_ghs_label_corrosive)
    "GHS06" -> stringResource(R.string.ui_ghs_label_toxic)
    "GHS07" -> stringResource(R.string.ui_ghs_label_harmful)
    "GHS08" -> stringResource(R.string.ui_ghs_label_health_hazard)
    "GHS09" -> stringResource(R.string.ui_ghs_label_environmental)
    else -> code
}

private fun ghsPictogramRes(code: String): Int? = when (code) {
    "GHS01" -> R.drawable.ghs_pictogram_ghs01
    "GHS02" -> R.drawable.ghs_pictogram_ghs02
    "GHS03" -> R.drawable.ghs_pictogram_ghs03
    "GHS04" -> R.drawable.ghs_pictogram_ghs04
    "GHS05" -> R.drawable.ghs_pictogram_ghs05
    "GHS06" -> R.drawable.ghs_pictogram_ghs06
    "GHS07" -> R.drawable.ghs_pictogram_ghs07
    "GHS08" -> R.drawable.ghs_pictogram_ghs08
    "GHS09" -> R.drawable.ghs_pictogram_ghs09
    else -> null
}

private val DangerRed = Color(0xFFDC2626)
private val WarningAmber = Color(0xFFD97706)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SafetySection(ghsData: GhsData?, isLoading: Boolean) {
    val compact = LocalCompactMode.current
    SearchCard(modifier = Modifier.fillMaxWidth(), spacing = 12.dp) {
        var showInfo by remember { mutableStateOf(false) }
        if (showInfo) {
            InfoDialog(
                titleRes = R.string.ui_ghs_safety,
                entries = listOf(
                    R.string.ui_info_what_is_ghs to R.string.ui_info_what_is_ghs_body,
                    R.string.ui_info_signal_word to R.string.ui_info_signal_word_body,
                    R.string.ui_info_pictograms to R.string.ui_info_pictograms_body,
                    R.string.ui_info_hazard_statements to R.string.ui_info_hazard_statements_body,
                    R.string.ui_info_ghs_data_source to R.string.ui_info_ghs_data_source_body
                ),
                onDismiss = { showInfo = false }
            )
        }
        CardSectionHeader(stringResource(R.string.ui_ghs_safety_information)) { showInfo = true }

        if (isLoading) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        } else if (ghsData == null) {
            Text(stringResource(R.string.ui_no_ghs_classification_available), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
        } else {
            val safetySummary = remember(ghsData) { enrichGhsSafety(ghsData, ghsData.retrievedAt) }
            Text(
                stringResource(R.string.ui_source_label, safetySummary.source.name),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(0.45f)
            )
            ghsData.signalWord?.let { word ->
                val isDanger = word.equals("Danger", ignoreCase = true)
                val badgeColor = if (isDanger) DangerRed else WarningAmber
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = badgeColor.copy(alpha = 0.1f),
                    border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(
                            horizontal = if (compact) 12.dp else 14.dp,
                            vertical = if (compact) 6.dp else 7.dp
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)
                    ) {
                        Icon(
                            if (isDanger) Icons.Default.Error else Icons.Default.Warning,
                            null,
                            tint = badgeColor,
                            modifier = Modifier.size(if (compact) 16.dp else 18.dp)
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
                Text(stringResource(R.string.ui_pictograms), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(0.45f), letterSpacing = 0.5.sp)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp),
                    verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)
                ) {
                    ghsData.pictogramCodes.forEach { code ->
                        val pictogramRes = ghsPictogramRes(code)
                        Surface(
                            shape = RoundedCornerShape(if (compact) 10.dp else 12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(0.25f))
                        ) {
                            Column(
                                modifier = Modifier.padding(
                                    horizontal = if (compact) 10.dp else 12.dp,
                                    vertical = if (compact) 8.dp else 10.dp
                                ),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(if (compact) 42.dp else 48.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (pictogramRes != null) {
                                        Image(
                                            painter = painterResource(id = pictogramRes),
                                            contentDescription = ghsLabelText(code),
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Fit
                                        )
                                    } else {
                                        Icon(
                                            Icons.Default.Warning,
                                            contentDescription = ghsLabelText(code),
                                            modifier = Modifier
                                                .size(if (compact) 30.dp else 34.dp)
                                                .background(MaterialTheme.colorScheme.error.copy(0.08f), CircleShape)
                                                .padding(if (compact) 6.dp else 7.dp)
                                        )
                                    }
                                }
                                Text(code, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Text(ghsLabelText(code), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(0.5f), fontSize = 9.sp, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }

            if (ghsData.hazardStatements.isNotEmpty()) {
                Text(stringResource(R.string.ui_hazard_statements), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(0.45f), letterSpacing = 0.5.sp)
                Column(verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 7.dp)) {
                    safetySummary.hazards.take(8).forEach { hazard ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(top = if (compact) 4.dp else 5.dp)
                                    .size(if (compact) 4.dp else 5.dp)
                                    .background(MaterialTheme.colorScheme.primary.copy(0.5f), CircleShape)
                            )
                            Text(
                                hazard.statement,
                                style = MaterialTheme.typography.bodySmall,
                                lineHeight = if (compact) 16.sp else 18.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(0.85f)
                            )
                        }
                        hazard.meaningRes?.let { meaningRes ->
                            Text(
                                stringResource(meaningRes),
                                modifier = Modifier.padding(start = if (compact) 14.dp else 16.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(0.48f)
                            )
                        }
                    }
                }
            }
            Text(
                stringResource(safetySummary.disclaimerRes),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(0.5f),
                lineHeight = if (compact) 15.sp else 16.sp
            )
        }
    }
}

private suspend fun save2dPng(context: Context, compoundName: String, cid: Long) {
    val url = "https://pubchem.ncbi.nlm.nih.gov/rest/pug/compound/cid/$cid/PNG?image_size=large"
    val fileName = "${safeDownloadName(compoundName, "compound")}_2d.png"
    try {
        val request = okhttp3.Request.Builder().url(url).build()
        withContext(Dispatchers.IO) {
            com.furthersecrets.chemsearch.data.ApiClient.rawHttp.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("Image download failed (${response.code})")
                val body = response.body
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                    }
                    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                        ?: error("Could not create destination file")
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        body.byteStream().use { input ->
                            input.copyTo(output)
                        }
                    } ?: error("Unable to open output stream")
                } else {
                    val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                    File(dir, fileName).outputStream().use { output ->
                        body.byteStream().use { input ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        }
        withContext(Dispatchers.Main) {
            Toast.makeText(context, context.getString(R.string.ui_saved_s_to_pictures, fileName), Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, context.getString(R.string.ui_download_failed_s, e.message ?: ""), Toast.LENGTH_SHORT).show()
        }
    }
}

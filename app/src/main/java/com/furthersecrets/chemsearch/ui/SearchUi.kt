package com.furthersecrets.chemsearch.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
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

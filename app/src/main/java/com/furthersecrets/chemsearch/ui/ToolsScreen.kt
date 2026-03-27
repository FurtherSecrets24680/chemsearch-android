package com.furthersecrets.chemsearch.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// TOOLS SCREEN
@Composable
fun ToolsScreen(isDark: Boolean, jumpToTool: Int = 0, jumpToToolVersion: Int = 0, onNavigateToSearch: () -> Unit = {}) {
    var selectedTool by remember { mutableStateOf(0) }
    var toolSearch by remember { mutableStateOf("") }

    LaunchedEffect(jumpToTool, jumpToToolVersion) {
        if (jumpToTool != 0) selectedTool = jumpToTool
    }

    val allTools = listOf(
        1 to Triple(Icons.Default.ViewInAr,    "Custom 3D Molecule Viewer",  "Load any .sdf or .mol file and view it in 3D"),
        2 to Triple(Icons.Default.Calculate,   "Molar Mass Calculator",       "Enter a molecular formula and get the molar mass"),
        3 to Triple(Icons.Default.Science,     "Oxidation State Finder",      "Find oxidation states of each element in a compound"),
        4 to Triple(Icons.Default.AccountTree, "SMILES Visualizer",           "Paste a SMILES string to view its 2D and 3D structure"),
        5 to Triple(Icons.Default.SwapHoriz,   "Reaction Balancer",           "Balance any chemical equation automatically"),
        6 to Triple(Icons.Default.Biotech,     "Isomer Finder",               "Enter a molecular formula to find its structural isomers"),
        7 to Triple(Icons.Default.Calculate,   "Stoichiometry Calculator",    "Limiting reagents, yields, and reaction scaling"),
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
                7 -> StoichiometryCalculator()
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
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
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

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
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
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
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
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
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
                val bodyStr = response.body.string()
                if (!response.isSuccessful || bodyStr.isBlank()) {
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
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
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

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
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
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
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

// TOOL 7 : STOICHIOMETRY CALCULATOR
private const val AVOGADRO = 6.02214076e23
private const val DEFAULT_MOLAR_VOLUME = 22.414

private enum class StoichUnit(val label: String) {
    GRAMS("g"),
    KILOGRAMS("kg"),
    MOLES("mol"),
    MILLIMOLES("mmol"),
    LITERS_GAS("L gas"),
    MILLILITERS_GAS("mL gas"),
    MOLARITY("M (mol/L)"),
    PARTICLES("particles x10^23")
}

private data class StoichReactantInput(
    val formula: String,
    val coeff: Int,
    val amount: String = "",
    val unit: StoichUnit = StoichUnit.GRAMS,
    val molarity: String = "",
    val volume: String = "",
    val purity: String = ""
)

private data class StoichMoleInfo(
    val moles: Double?,
    val error: String? = null,
    val purityApplied: Boolean = false
)

private fun parsePositiveNumber(raw: String): Double? {
    val cleaned = raw.trim().replace(",", "")
    val value = cleaned.toDoubleOrNull() ?: return null
    return if (value > 0) value else null
}

private fun ratioToString(numerator: Int, denominator: Int): String {
    fun gcdInt(a: Int, b: Int): Int = if (b == 0) kotlin.math.abs(a) else gcdInt(b, a % b)
    val g = gcdInt(numerator, denominator).coerceAtLeast(1)
    val n = numerator / g
    val d = denominator / g
    return if (d == 1) "$n" else "$n/$d"
}

private fun formatNumber(value: Double, decimals: Int = 4): String = "%.${decimals}f".format(value)

private fun computeMolesForInput(
    input: StoichReactantInput,
    molarMass: Double?,
    molarVolume: Double
): StoichMoleInfo {
    val purityRaw = input.purity.trim()
    val purity = if (purityRaw.isBlank()) null else parsePositiveNumber(purityRaw)
    if (purityRaw.isNotBlank() && purity == null) {
        return StoichMoleInfo(null, "Invalid purity %")
    }
    if (purity != null && purity > 100.0) {
        return StoichMoleInfo(null, "Purity must be <= 100%")
    }

    val molesBase = when (input.unit) {
        StoichUnit.MOLARITY -> {
            if (input.molarity.isBlank() && input.volume.isBlank()) return StoichMoleInfo(null)
            val molarity = parsePositiveNumber(input.molarity) ?: return StoichMoleInfo(null, "Enter molarity")
            val volume = parsePositiveNumber(input.volume) ?: return StoichMoleInfo(null, "Enter volume")
            molarity * (volume / 1000.0)
        }
        else -> {
            if (input.amount.isBlank()) return StoichMoleInfo(null)
            val amount = parsePositiveNumber(input.amount) ?: return StoichMoleInfo(null, "Enter a valid amount")
            when (input.unit) {
                StoichUnit.GRAMS -> {
                    if (molarMass == null) return StoichMoleInfo(null, "Molar mass unavailable")
                    amount / molarMass
                }
                StoichUnit.KILOGRAMS -> {
                    if (molarMass == null) return StoichMoleInfo(null, "Molar mass unavailable")
                    (amount * 1000.0) / molarMass
                }
                StoichUnit.MOLES -> amount
                StoichUnit.MILLIMOLES -> amount / 1000.0
                StoichUnit.LITERS_GAS -> {
                    if (molarVolume <= 0.0) return StoichMoleInfo(null, "Invalid molar volume")
                    amount / molarVolume
                }
                StoichUnit.MILLILITERS_GAS -> {
                    if (molarVolume <= 0.0) return StoichMoleInfo(null, "Invalid molar volume")
                    (amount / 1000.0) / molarVolume
                }
                StoichUnit.PARTICLES -> (amount * 1e23) / AVOGADRO
                StoichUnit.MOLARITY -> return StoichMoleInfo(null)
            }
        }
    }

    val moles = if (purity != null) molesBase * (purity / 100.0) else molesBase
    return StoichMoleInfo(moles, null, purityApplied = purity != null)
}

@Composable
private fun StoichUnitDropdown(
    unit: StoichUnit,
    onUnitChange: (StoichUnit) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(0.25f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(unit.label, style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace)
                Icon(Icons.Default.KeyboardArrowDown, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurface.copy(0.6f))
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            StoichUnit.values().forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label, fontFamily = FontFamily.Monospace) },
                    onClick = {
                        onUnitChange(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun StoichiometryCalculator() {
    var equation by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<BalancerResult?>(null) }
    val reactantInputs = remember { mutableStateListOf<StoichReactantInput>() }
    var molarVolumeInput by remember { mutableStateOf(DEFAULT_MOLAR_VOLUME.toString()) }
    var selectedProductIndex by remember { mutableStateOf(0) }
    var desiredAmount by remember { mutableStateOf("") }
    var desiredUnit by remember { mutableStateOf(StoichUnit.GRAMS) }
    var desiredMolarity by remember { mutableStateOf("") }
    var desiredVolume by remember { mutableStateOf("") }
    var actualAmount by remember { mutableStateOf("") }
    var actualUnit by remember { mutableStateOf(StoichUnit.GRAMS) }
    var actualMolarity by remember { mutableStateOf("") }
    var actualVolume by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    val examples = listOf(
        "H2 + O2 -> H2O",
        "C3H8 + O2 -> CO2 + H2O",
        "N2 + H2 -> NH3",
        "CaCO3 -> CaO + CO2",
        "Fe2O3 + CO -> Fe + CO2"
    )

    var showInfo by remember { mutableStateOf(false) }
    if (showInfo) {
        InfoDialog(
            title = "Stoichiometry Calculator",
            entries = listOf(
                "What this does" to "Balances a reaction, finds the limiting reagent, computes theoretical yields, excess reagents, and reaction scaling.",
                "Limiting reagent" to "The limiting reagent has the smallest (moles / coefficient) ratio. It determines the reaction extent.",
                "Units supported" to "Mass (g, kg), amount (mol, mmol), gas volume (L, mL), solutions (M and mL), and particles (x10^23).",
                "Gas volumes" to "Gas moles are calculated from a molar volume you can edit (22.414 L/mol at STP).",
                "Purity" to "Optional purity % applies a correction to effective moles for each reactant.",
                "Percent yield" to "Compare actual yield to theoretical yield for the selected product.",
                "Scaling" to "Enter a desired product amount to see the required reactant amounts."
            ),
            onDismiss = { showInfo = false }
        )
    }

    LaunchedEffect(result) {
        if (result == null || result?.error != null) {
            reactantInputs.clear()
        } else {
            val existing = reactantInputs.associateBy { it.formula }
            reactantInputs.clear()
            result?.reactants?.forEach { (formula, coeff) ->
                val prev = existing[formula]
                reactantInputs.add(prev?.copy(coeff = coeff) ?: StoichReactantInput(formula = formula, coeff = coeff))
            }
            val productCount = result?.products?.size ?: 0
            if (selectedProductIndex >= productCount) selectedProductIndex = 0
        }
    }

    val molarVolume = parsePositiveNumber(molarVolumeInput) ?: DEFAULT_MOLAR_VOLUME
    val molarVolumeError = molarVolumeInput.isNotBlank() && parsePositiveNumber(molarVolumeInput) == null
    val molarMassMap = remember(result) {
        val map = mutableMapOf<String, Double?>()
        val formulas = (result?.reactants ?: emptyList()) + (result?.products ?: emptyList())
        formulas.forEach { (formula, _) ->
            val res = calculateMolarMass(formula)
            map[formula] = if (res.error == null) res.molarMass else null
        }
        map
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
            Text("Stoichiometry Calculator", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            IconButton(onClick = { showInfo = true }, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.onSurface.copy(0.35f), modifier = Modifier.size(16.dp))
            }
        }

        OutlinedTextField(
            value = equation,
            onValueChange = { new ->
                equation = new.replace("->", "→")
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
                result = balanceReaction(equation.replace("→", "->"))
            }),
            trailingIcon = {
                if (equation.isNotBlank()) {
                    IconButton(onClick = { equation = ""; result = null }) {
                        Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp))
                    }
                }
            }
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Insert:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(0.45f))
            listOf("+" to " + ", "→" to " → ", "(" to "(", ")" to ")").forEach { (label, insert) ->
                Surface(
                    onClick = { equation += insert; result = null },
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
                onClick = { if (equation.isNotEmpty()) { equation = equation.dropLast(1); result = null } },
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
            onClick = { focusManager.clearFocus(); result = balanceReaction(equation.replace("→", "->")) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            enabled = equation.isNotBlank()
        ) {
            Icon(Icons.Default.Calculate, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Balance & Calculate")
        }

        OutlinedTextField(
            value = molarVolumeInput,
            onValueChange = { molarVolumeInput = it },
            label = { Text("Gas molar volume (L/mol)") },
            placeholder = { Text(DEFAULT_MOLAR_VOLUME.toString()) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            singleLine = true,
            isError = molarVolumeError,
            supportingText = if (molarVolumeError) {{ Text("Enter a valid molar volume (e.g. 22.414)") }} else null
        )

        result?.let { res ->
            if (res.error != null) {
                Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(0.4f))) {
                    Text(res.error, modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            } else {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                    }
                }

                if (reactantInputs.isNotEmpty()) {
                    Text("REACTANT AMOUNTS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.45f))
                }

                val reactantMoles = reactantInputs.map { input ->
                    val molarMass = molarMassMap[input.formula]
                    input to computeMolesForInput(input, molarMass, molarVolume)
                }

                reactantInputs.forEachIndexed { index, input ->
                    val molarMass = molarMassMap[input.formula]
                    val info = reactantMoles.getOrNull(index)?.second
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(0.1f),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(0.25f))
                                    ) {
                                        Text(
                                            if (input.coeff > 1) "${input.coeff} ${toSubscriptFormula(input.formula)}" else toSubscriptFormula(input.formula),
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                Text(
                                    molarMass?.let { "${formatNumber(it, 4)} g/mol" } ?: "Molar mass N/A",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = if (input.unit == StoichUnit.MOLARITY) input.molarity else input.amount,
                                    onValueChange = { value ->
                                        val updated = if (input.unit == StoichUnit.MOLARITY) input.copy(molarity = value) else input.copy(amount = value)
                                        reactantInputs[index] = updated
                                    },
                                    label = { Text(if (input.unit == StoichUnit.MOLARITY) "Molarity (M)" else "Amount") },
                                    placeholder = { Text("e.g. 2.5") },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true
                                )
                                StoichUnitDropdown(
                                    unit = input.unit,
                                    onUnitChange = { newUnit ->
                                        reactantInputs[index] = input.copy(unit = newUnit)
                                    }
                                )
                            }

                            if (input.unit == StoichUnit.MOLARITY) {
                                OutlinedTextField(
                                    value = input.volume,
                                    onValueChange = { reactantInputs[index] = input.copy(volume = it) },
                                    label = { Text("Volume (mL)") },
                                    placeholder = { Text("e.g. 250") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true
                                )
                            }

                            OutlinedTextField(
                                value = input.purity,
                                onValueChange = { reactantInputs[index] = input.copy(purity = it) },
                                label = { Text("Purity % (optional)") },
                                placeholder = { Text("100") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )

                            when {
                                info?.error != null -> {
                                    Text(info.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                }
                                info?.moles != null -> {
                                    val note = if (info.purityApplied) " (purity applied)" else ""
                                    Text(
                                        "Moles available: ${formatNumber(info.moles)} mol$note",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(0.7f)
                                    )
                                }
                                else -> {
                                    Text("Enter an amount to compute moles.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(0.45f))
                                }
                            }
                        }
                    }
                }

                if (reactantInputs.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            for (i in reactantInputs.indices) {
                                reactantInputs[i] = reactantInputs[i].copy(amount = "", molarity = "", volume = "", purity = "")
                            }
                            actualAmount = ""
                            actualMolarity = ""
                            actualVolume = ""
                            desiredAmount = ""
                            desiredMolarity = ""
                            desiredVolume = ""
                        },
                        contentPadding = PaddingValues(horizontal = 0.dp)
                    ) {
                        Text("Clear all amounts", style = MaterialTheme.typography.labelMedium)
                    }
                }

                val allReactantsReady = reactantMoles.isNotEmpty() && reactantMoles.all { it.second.moles != null && it.second.error == null }
                val limitingData = if (allReactantsReady) {
                    val minEntry = reactantMoles.minBy { (input, info) -> info.moles!! / input.coeff }
                    val extent = minEntry.second.moles!! / minEntry.first.coeff
                    Triple(minEntry, extent, minEntry.first.coeff)
                } else {
                    null
                }

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("STOICHIOMETRY SUMMARY", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.45f))

                        if (limitingData == null) {
                            Text(
                                "Enter valid amounts for all reactants to determine the limiting reagent.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(0.6f)
                            )
                        } else {
                            val (limitingEntry, extent, limitingCoeff) = limitingData
                            val limitingFormula = limitingEntry.first.formula
                            val limitingMoles = limitingEntry.second.moles ?: 0.0
                            val limitingMass = molarMassMap[limitingFormula]?.let { it * limitingMoles }

                            Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text("Limiting reagent", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                                    Text(toSubscriptFormula(limitingFormula), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("${formatNumber(limitingMoles)} mol", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                                    if (limitingMass != null) {
                                        Text("${formatNumber(limitingMass, 3)} g", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                                    }
                                }
                            }

                            Text("Reaction extent: ${formatNumber(extent)} mol", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(0.65f))

                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.15f))
                            Text("MOLE RATIOS (relative to limiting reagent)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.45f))

                            (res.reactants + res.products).forEach { (formula, coeff) ->
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(toSubscriptFormula(formula), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                                    Text(ratioToString(coeff, limitingCoeff), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.15f))
                            Text("THEORETICAL YIELD", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.45f))

                            res.products.forEach { (formula, coeff) ->
                                val moles = extent * coeff
                                val mass = molarMassMap[formula]?.let { it * moles }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(toSubscriptFormula(formula), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("${formatNumber(moles)} mol", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                                        Text(
                                            mass?.let { "${formatNumber(it, 3)} g" } ?: "g N/A",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(0.6f)
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.15f))
                            Text("EXCESS REACTANTS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.45f))

                            reactantMoles.forEach { (input, info) ->
                                val available = info.moles ?: return@forEach
                                val used = extent * input.coeff
                                val leftover = (available - used).coerceAtLeast(0.0)
                                val leftoverMass = molarMassMap[input.formula]?.let { it * leftover }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(toSubscriptFormula(input.formula), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("${formatNumber(leftover)} mol", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                                        Text(
                                            leftoverMass?.let { "${formatNumber(it, 3)} g" } ?: "g N/A",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(0.6f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                val products = res.products
                if (products.isNotEmpty()) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("YIELD & SCALING", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.45f))

                            var productMenuExpanded by remember { mutableStateOf(false) }
                            val selectedProduct = products.getOrNull(selectedProductIndex) ?: products.first()
                            Box {
                                Surface(
                                    onClick = { productMenuExpanded = true },
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(0.25f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text("Target product:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                                        Text(toSubscriptFormula(selectedProduct.first), style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace)
                                        Icon(Icons.Default.KeyboardArrowDown, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                                    }
                                }
                                DropdownMenu(expanded = productMenuExpanded, onDismissRequest = { productMenuExpanded = false }) {
                                    products.forEachIndexed { idx, (formula, _) ->
                                        DropdownMenuItem(
                                            text = { Text(toSubscriptFormula(formula), fontFamily = FontFamily.Monospace) },
                                            onClick = {
                                                selectedProductIndex = idx
                                                productMenuExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            Text("Actual yield (optional)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = if (actualUnit == StoichUnit.MOLARITY) actualMolarity else actualAmount,
                                    onValueChange = { value ->
                                        if (actualUnit == StoichUnit.MOLARITY) actualMolarity = value else actualAmount = value
                                    },
                                    label = { Text(if (actualUnit == StoichUnit.MOLARITY) "Molarity (M)" else "Amount") },
                                    placeholder = { Text("e.g. 1.25") },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true
                                )
                                StoichUnitDropdown(
                                    unit = actualUnit,
                                    onUnitChange = { actualUnit = it }
                                )
                            }

                            if (actualUnit == StoichUnit.MOLARITY) {
                                OutlinedTextField(
                                    value = actualVolume,
                                    onValueChange = { actualVolume = it },
                                    label = { Text("Volume (mL)") },
                                    placeholder = { Text("e.g. 100") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true
                                )
                            }

                            val theoreticalMoles = if (limitingData != null) {
                                val extent = limitingData.second
                                extent * selectedProduct.second
                            } else null
                            val actualInfo = computeMolesForInput(
                                StoichReactantInput(
                                    formula = selectedProduct.first,
                                    coeff = 1,
                                    amount = actualAmount,
                                    unit = actualUnit,
                                    molarity = actualMolarity,
                                    volume = actualVolume
                                ),
                                molarMassMap[selectedProduct.first],
                                molarVolume
                            )
                            val actualProvided = if (actualUnit == StoichUnit.MOLARITY) {
                                actualMolarity.isNotBlank() || actualVolume.isNotBlank()
                            } else {
                                actualAmount.isNotBlank()
                            }

                            if (theoreticalMoles != null && actualInfo.moles != null && actualInfo.error == null) {
                                val percentYield = (actualInfo.moles / theoreticalMoles) * 100.0
                                Text(
                                    "Percent yield: ${formatNumber(percentYield, 2)}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (percentYield >= 100.0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(0.75f)
                                )
                            } else if (actualInfo.error != null) {
                                Text(actualInfo.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            } else if (actualProvided && limitingData == null) {
                                Text("Need limiting reagent to compute percent yield.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(0.55f))
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.15f))
                            Text("Desired product amount", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = if (desiredUnit == StoichUnit.MOLARITY) desiredMolarity else desiredAmount,
                                    onValueChange = { value ->
                                        if (desiredUnit == StoichUnit.MOLARITY) desiredMolarity = value else desiredAmount = value
                                    },
                                    label = { Text(if (desiredUnit == StoichUnit.MOLARITY) "Molarity (M)" else "Amount") },
                                    placeholder = { Text("e.g. 5.0") },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true
                                )
                                StoichUnitDropdown(
                                    unit = desiredUnit,
                                    onUnitChange = { desiredUnit = it }
                                )
                            }

                            if (desiredUnit == StoichUnit.MOLARITY) {
                                OutlinedTextField(
                                    value = desiredVolume,
                                    onValueChange = { desiredVolume = it },
                                    label = { Text("Volume (mL)") },
                                    placeholder = { Text("e.g. 500") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true
                                )
                            }

                            val desiredInfo = computeMolesForInput(
                                StoichReactantInput(
                                    formula = selectedProduct.first,
                                    coeff = 1,
                                    amount = desiredAmount,
                                    unit = desiredUnit,
                                    molarity = desiredMolarity,
                                    volume = desiredVolume
                                ),
                                molarMassMap[selectedProduct.first],
                                molarVolume
                            )

                            if (desiredInfo.moles != null && desiredInfo.error == null) {
                                val extent = desiredInfo.moles / selectedProduct.second
                                Text("Required reactants (theoretical)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                                res.reactants.forEach { (formula, coeff) ->
                                    val reqMoles = extent * coeff
                                    val reqMass = molarMassMap[formula]?.let { it * reqMoles }
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(toSubscriptFormula(formula), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text("${formatNumber(reqMoles)} mol", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                                            Text(
                                                reqMass?.let { "${formatNumber(it, 3)} g" } ?: "g N/A",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface.copy(0.6f)
                                            )
                                        }
                                    }
                                }
                            } else if (desiredInfo.error != null) {
                                Text(desiredInfo.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
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
                    onClick = { equation = ex.replace("->", "→"); result = null },
                    shape = RoundedCornerShape(10.dp),
                    color = if (equation.replace("→", "->") == ex) MaterialTheme.colorScheme.primary.copy(0.1f) else MaterialTheme.colorScheme.surfaceVariant.copy(0.5f),
                    border = if (equation.replace("→", "->") == ex) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(0.4f)) else null
                ) {
                    Text(
                        reactionToDisplay(ex),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = if (equation.replace("→", "->") == ex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(0.7f)
                    )
                }
            }
        }
    }
}

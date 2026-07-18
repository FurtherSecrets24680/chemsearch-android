package com.furthersecrets.chemsearch.ui

import com.furthersecrets.chemsearch.R
import androidx.compose.ui.res.stringResource
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.furthersecrets.chemsearch.data.BondOrder
import com.furthersecrets.chemsearch.data.ChainTemplate
import com.furthersecrets.chemsearch.data.PeriodicElement
import com.furthersecrets.chemsearch.data.PeriodicTableElements
import com.furthersecrets.chemsearch.data.RingTemplate
import com.furthersecrets.chemsearch.data.SketchAtom
import com.furthersecrets.chemsearch.data.SketchBond
import com.furthersecrets.chemsearch.data.StructureSearchWarning
import com.furthersecrets.chemsearch.data.StructureSearchMode
import com.furthersecrets.chemsearch.data.StructureSearchUiState
import com.furthersecrets.chemsearch.data.StructureSketch
import com.furthersecrets.chemsearch.data.pubChemStructureThumbnailUrl
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

private val CommonAtoms = listOf("C", "N", "O", "S", "P", "F", "Cl", "Br", "I", "H")
private val StructureTemplates = listOf(
    RingTemplate.BENZENE,
    RingTemplate.CYCLOHEXANE,
    RingTemplate.CYCLOPENTANE,
    RingTemplate.CYCLOBUTANE,
    RingTemplate.CYCLOPROPANE,
    RingTemplate.CYCLOHEPTANE,
    RingTemplate.CYCLOOCTANE,
    RingTemplate.PYRIDINE,
    RingTemplate.FURAN,
    RingTemplate.THIOPHENE,
    RingTemplate.PYRROLE,
    RingTemplate.IMIDAZOLE,
    RingTemplate.OXAZOLE,
    RingTemplate.THIAZOLE
)
private val ChainTemplates = listOf(
    ChainTemplate.ETHYL,
    ChainTemplate.PROPYL,
    ChainTemplate.BUTYL,
    ChainTemplate.HEXYL,
    ChainTemplate.CARBONYL,
    ChainTemplate.NITRILE,
    ChainTemplate.HYDROXYL,
    ChainTemplate.AMINO
)
internal const val StructureChargeLimit = 8

private enum class StructureCanvasMode {
    DRAW,
    SELECT
}

internal fun clampStructureAtomCharge(charge: Int): Int =
    charge.coerceIn(-StructureChargeLimit, StructureChargeLimit)

internal fun structureSearchBlockedMessage(sketch: StructureSketch): String? = when {
    sketch.atoms.size == 1 -> "Draw at least two connected atoms before searching."
    sketch.atoms.size > 1 && !sketch.canSearch -> "Connect the atoms before searching."
    else -> null
}

internal fun shouldShowStructureSearchResultsDialog(state: StructureSearchUiState): Boolean =
    state.isLoading || state.results.isNotEmpty() || !state.error.isNullOrBlank()

internal fun structureSearchVisibleResultSlots(state: StructureSearchUiState): Int =
    state.results.size.takeIf { it > 0 }?.coerceIn(1, 4) ?: 1

internal fun shouldOpenStructureSearchResultsDialog(
    state: StructureSearchUiState,
    isArmed: Boolean
): Boolean = isArmed && shouldShowStructureSearchResultsDialog(state)

internal fun applyStructureCanvasBlankTap(
    sketch: StructureSketch,
    x: Double,
    y: Double,
    selectedElement: String,
    selectedTemplate: RingTemplate?,
    selectedChainTemplate: ChainTemplate? = null,
    selectionMode: Boolean
): StructureSketch =
    if (selectionMode) sketch else selectedChainTemplate?.let { template ->
        sketch.addChainTemplate(template, centerX = x, centerY = y)
    } ?: selectedTemplate?.let { template ->
        sketch.addRingTemplate(template, centerX = x, centerY = y, radius = 1.35)
    } ?: sketch.addAtom(selectedElement, x, y)

internal fun applyStructureCanvasSelectionDrag(
    sketch: StructureSketch,
    draggingMolecule: Boolean = false,
    draggingAtomId: Int?,
    draggingBondId: Int?,
    atomX: Double,
    atomY: Double,
    bondDx: Double,
    bondDy: Double
): StructureSketch = when {
    draggingMolecule -> sketch.translate(bondDx, bondDy)
    draggingAtomId != null -> sketch.moveAtom(draggingAtomId, atomX, atomY)
    draggingBondId != null -> {
        val bond = sketch.bonds.firstOrNull { it.id == draggingBondId }
        val atomA = bond?.let { sketch.atoms.firstOrNull { atom -> atom.id == it.atomA } }
        val atomB = bond?.let { sketch.atoms.firstOrNull { atom -> atom.id == it.atomB } }
        if (atomA == null || atomB == null) {
            sketch
        } else {
            sketch
                .moveAtom(atomA.id, atomA.x + bondDx, atomA.y + bondDy)
                .moveAtom(atomB.id, atomB.x + bondDx, atomB.y + bondDy)
        }
    }
    else -> sketch
}

@Composable
fun StructureSearchScreen(
    state: StructureSearchUiState,
    onBack: () -> Unit,
    onModeChange: (StructureSearchMode) -> Unit,
    onThresholdChange: (Int) -> Unit,
    onMaxRecordsChange: (Int) -> Unit,
    onStandardize: (StructureSketch) -> Unit,
    onImportText: (String) -> Unit,
    onConsumeSketchUpdate: () -> Unit,
    onSearch: (StructureSketch) -> Unit,
    onClearResults: () -> Unit,
    onOpenResult: (Long) -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val compact = LocalCompactMode.current
    var sketch by remember { mutableStateOf(StructureSketch.empty()) }
    var undoStack by remember { mutableStateOf<List<StructureSketch>>(emptyList()) }
    var redoStack by remember { mutableStateOf<List<StructureSketch>>(emptyList()) }
    var selectedAtomId by remember { mutableStateOf<Int?>(null) }
    var selectedBondId by remember { mutableStateOf<Int?>(null) }
    var selectedMolecule by remember { mutableStateOf(false) }
    var selectedElement by remember { mutableStateOf("C") }
    var selectedBond by remember { mutableStateOf(BondOrder.SINGLE) }
    var selectedTemplate by remember { mutableStateOf<RingTemplate?>(null) }
    var selectedChainTemplate by remember { mutableStateOf<ChainTemplate?>(null) }
    var canvasMode by remember { mutableStateOf(StructureCanvasMode.DRAW) }
    var selectedToolLabel by remember { mutableStateOf("C") }
    var canvasPanOffset by remember { mutableStateOf(Offset.Zero) }
    var canvasGestureActive by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showSearchConfigDialog by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showResultsDialog by remember { mutableStateOf(false) }
    var resultsDialogArmed by remember { mutableStateOf(false) }
    var blockedSearchMessage by remember { mutableStateOf<String?>(null) }
    val hasSelection = selectedMolecule || selectedAtomId != null || selectedBondId != null

    fun applySketch(next: StructureSketch) {
        undoStack = (undoStack + sketch).takeLast(30)
        redoStack = emptyList()
        sketch = next
        blockedSearchMessage = null
    }

    fun clearSelection() {
        selectedAtomId = null
        selectedBondId = null
        selectedMolecule = false
    }

    LaunchedEffect(state.standardizedSketch) {
        state.standardizedSketch?.let { next ->
            applySketch(next.normalizedForCanvas())
            canvasPanOffset = Offset.Zero
            selectedAtomId = null
            selectedBondId = null
            selectedMolecule = false
            onConsumeSketchUpdate()
        }
    }

    LaunchedEffect(blockedSearchMessage) {
        if (blockedSearchMessage != null) {
            delay(2600)
            blockedSearchMessage = null
        }
    }

    LaunchedEffect(state.isLoading, state.results, state.error) {
        if (!shouldShowStructureSearchResultsDialog(state)) {
            showResultsDialog = false
            resultsDialogArmed = false
        } else if (shouldOpenStructureSearchResultsDialog(state, resultsDialogArmed)) {
            showResultsDialog = true
        }
    }

    if (showImportDialog) {
        StructureImportDialog(
            isLoading = state.isStandardizing,
            onDismiss = { showImportDialog = false },
            onImport = { input ->
                onImportText(input)
                showImportDialog = false
            }
        )
    }

    if (showSearchConfigDialog) {
        StructureSearchConfigDialog(
            mode = state.mode,
            similarityThreshold = state.similarityThreshold,
            maxRecords = state.maxRecords,
            onModeChange = onModeChange,
            onThresholdChange = onThresholdChange,
            onMaxRecordsChange = onMaxRecordsChange,
            onDismiss = { showSearchConfigDialog = false }
        )
    }

    if (showInfoDialog) {
        StructureSearchInfoDialog(onDismiss = { showInfoDialog = false })
    }

    if (showExportDialog) {
        StructureExportDialog(
            molfile = sketch.toMolfile(),
            onDismiss = { showExportDialog = false },
            onCopy = { molfile ->
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("ChemSearch molfile", molfile))
                Toast.makeText(context, "Structure copied", Toast.LENGTH_SHORT).show()
            },
            onShare = { molfile ->
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, molfile)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share structure"))
            }
        )
    }

    if (showResultsDialog && shouldShowStructureSearchResultsDialog(state)) {
        StructureSearchResultsDialog(
            state = state,
            onDismiss = {
                showResultsDialog = false
                resultsDialogArmed = false
            },
            onOpenResult = { cid ->
                showResultsDialog = false
                resultsDialogArmed = false
                onOpenResult(cid)
            }
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(hasSelection) {
                    awaitEachGesture {
                        val down = awaitFirstDown(pass = PointerEventPass.Final)
                        val up = waitForUpOrCancellation(pass = PointerEventPass.Final)
                        if (hasSelection && up != null && !down.isConsumed && !up.isConsumed) {
                            clearSelection()
                        }
                    }
                }
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = !canvasGestureActive,
                contentPadding = PaddingValues(
                    start = if (compact) 12.dp else 16.dp,
                    end = if (compact) 12.dp else 16.dp,
                    top = if (compact) 10.dp else 18.dp,
                    bottom = when {
                        hasSelection && compact -> 172.dp
                        hasSelection -> 188.dp
                        compact -> 112.dp
                        else -> 128.dp
                    }
                ),
                verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 12.dp)
            ) {
                item {
                    StructureSearchHeader(
                        onBack = onBack,
                        onInfo = { showInfoDialog = true }
                    )
                }

                item {
                    StructureEditingActions(
                        sketch = sketch,
                        canUndo = undoStack.isNotEmpty(),
                        canRedo = redoStack.isNotEmpty(),
                        selectionMode = canvasMode == StructureCanvasMode.SELECT,
                        isStandardizing = state.isStandardizing,
                        onUndo = {
                            undoStack.lastOrNull()?.let { previous ->
                                redoStack = (redoStack + sketch).takeLast(30)
                                undoStack = undoStack.dropLast(1)
                                sketch = previous
                                selectedAtomId = null
                                selectedBondId = null
                                selectedMolecule = false
                            }
                        },
                        onRedo = {
                            redoStack.lastOrNull()?.let { next ->
                                undoStack = (undoStack + sketch).takeLast(30)
                                redoStack = redoStack.dropLast(1)
                                sketch = next
                                selectedAtomId = null
                                selectedBondId = null
                                selectedMolecule = false
                            }
                        },
                        onSelectMode = {
                            canvasMode = StructureCanvasMode.SELECT
                            selectedTemplate = null
                            selectedChainTemplate = null
                            selectedToolLabel = "Select"
                        },
                        onClean = { onStandardize(sketch) },
                        onImport = { showImportDialog = true },
                        onExport = { showExportDialog = true },
                        onClear = {
                            applySketch(StructureSketch.empty())
                            canvasPanOffset = Offset.Zero
                            selectedAtomId = null
                            selectedBondId = null
                            selectedMolecule = false
                            onClearResults()
                        }
                    )
                }

                item {
                    StructureSketchCanvas(
                        sketch = sketch,
                        selectedAtomId = selectedAtomId,
                        selectedBondId = selectedBondId,
                        selectedMolecule = selectedMolecule,
                        selectedElement = selectedElement,
                        selectedBond = selectedBond,
                        selectedTemplate = selectedTemplate,
                        selectedChainTemplate = selectedChainTemplate,
                        selectionMode = canvasMode == StructureCanvasMode.SELECT,
                        panOffset = canvasPanOffset,
                        onPanOffsetChange = { canvasPanOffset = it },
                        onCanvasGestureActiveChange = { canvasGestureActive = it },
                        onSketchChange = { applySketch(it) },
                        onSelectedAtom = {
                            selectedAtomId = it
                            if (it != null) {
                                selectedBondId = null
                                sketch.atoms.firstOrNull { atom -> atom.id == it }?.let { atom ->
                                    selectedToolLabel = atom.element + chargeLabel(atom.charge)
                                }
                            }
                            selectedMolecule = false
                        },
                        onSelectedBond = {
                            selectedBondId = it
                            if (it != null) {
                                selectedAtomId = null
                                sketch.bonds.firstOrNull { bond -> bond.id == it }?.let { bond ->
                                    selectedToolLabel = bondToolLabel(bond.order)
                                }
                            }
                            selectedMolecule = false
                        }
                    )
                }

                item {
                    StructureToolPanel(
                        selectedElement = selectedElement,
                        selectedBond = selectedBond,
                        selectedToolLabel = selectedToolLabel,
                        selectedAtom = sketch.atoms.firstOrNull { it.id == selectedAtomId },
                        onElement = {
                            canvasMode = StructureCanvasMode.DRAW
                            selectedElement = it
                            selectedTemplate = null
                            selectedChainTemplate = null
                            selectedToolLabel = it
                        },
                        onBond = {
                            canvasMode = StructureCanvasMode.DRAW
                            selectedBond = it
                            selectedTemplate = null
                            selectedChainTemplate = null
                            selectedToolLabel = bondToolLabel(it)
                        },
                        onCharge = { delta ->
                            selectedAtomId?.let { id ->
                                val atom = sketch.atoms.firstOrNull { it.id == id } ?: return@let
                                applySketch(sketch.updateAtom(id, atom.element, clampStructureAtomCharge(atom.charge + delta)))
                            }
                        },
                        onRing = { template ->
                            canvasMode = StructureCanvasMode.DRAW
                            selectedTemplate = template
                            selectedChainTemplate = null
                            selectedToolLabel = ringTemplateLabel(template)
                            selectedAtomId = null
                            selectedBondId = null
                            selectedMolecule = false
                        },
                        onChain = { template ->
                            canvasMode = StructureCanvasMode.DRAW
                            selectedChainTemplate = template
                            selectedTemplate = null
                            selectedToolLabel = chainTemplateLabel(template)
                            selectedAtomId = null
                            selectedBondId = null
                            selectedMolecule = false
                        }
                    )
                }

                val warnings = StructureSearchWarning.forSketch(sketch)
                if (warnings.isNotEmpty()) {
                    item {
                        StructureWarningsCard(warnings = warnings)
                    }
                }

                if (!state.standardizeMessage.isNullOrBlank()) {
                    item {
                        Text(
                            text = state.standardizeMessage,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                if (state.error != null && !resultsDialogArmed && !showResultsDialog) {
                    item {
                        Text(
                            text = state.error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            if (hasSelection) {
                StructureSelectionActions(
                    selectedAtomId = selectedAtomId,
                    selectedBondId = selectedBondId,
                    selectedMolecule = selectedMolecule,
                    onDeleteSelected = {
                        when {
                            selectedMolecule -> {
                                applySketch(StructureSketch.empty())
                                canvasPanOffset = Offset.Zero
                                selectedMolecule = false
                                onClearResults()
                            }
                            selectedBondId != null -> {
                                applySketch(sketch.removeBond(selectedBondId!!))
                                selectedBondId = null
                            }
                            selectedAtomId != null -> {
                                applySketch(sketch.removeAtom(selectedAtomId!!))
                                selectedAtomId = null
                            }
                        }
                    },
                    onSelectMolecule = {
                        if (sketch.atoms.isNotEmpty()) {
                            selectedMolecule = true
                            selectedAtomId = null
                            selectedBondId = null
                        }
                    },
                    onDuplicate = {
                        selectedAtomId?.let { id ->
                            applySketch(sketch.duplicateConnectedFragmentFrom(id, dx = 1.8, dy = 1.2))
                            selectedAtomId = null
                            selectedMolecule = false
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(
                            start = 16.dp,
                            end = 16.dp,
                            bottom = if (compact) 88.dp else 96.dp
                        )
                )
            }

            blockedSearchMessage?.let { message ->
                StructureSearchBlockedHint(
                    message = message,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(
                            start = 18.dp,
                            end = 18.dp,
                            bottom = when {
                                hasSelection && compact -> 150.dp
                                hasSelection -> 164.dp
                                compact -> 82.dp
                                else -> 92.dp
                            }
                        )
                )
            }

            FloatingStructureSearchButton(
                canSearch = sketch.atoms.isNotEmpty() && !state.isLoading,
                isLoading = state.isLoading,
                onClick = {
                    val message = structureSearchBlockedMessage(sketch)
                    if (message == null && sketch.canSearch) {
                        resultsDialogArmed = true
                        onSearch(sketch)
                    } else {
                        blockedSearchMessage = message
                    }
                },
                onConfigClick = { showSearchConfigDialog = true },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = if (compact) 14.dp else 18.dp)
            )
        }
    }
}

@Composable
private fun StructureSearchBlockedHint(
    message: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.inverseSurface,
        tonalElevation = 8.dp,
        shadowElevation = 10.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.inverseOnSurface,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = message,
                color = MaterialTheme.colorScheme.inverseOnSurface,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun StructureSearchHeader(
    onBack: () -> Unit,
    onInfo: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(42.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.primary)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.ui_structure_search_2),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.sp
            )
            Text(stringResource(R.string.ui_draw_a_connected_structure_and_search_pubchem),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(0.56f)
            )
        }
        IconButton(onClick = onInfo, modifier = Modifier.size(42.dp)) {
            Icon(Icons.Default.Info, "Structure search info", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun StructureSearchInfoDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(0.18f))
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                    Text(stringResource(R.string.ui_structure_search_3),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(stringResource(R.string.ui_tap_the_canvas_to_place_atoms_select_two),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.72f)
                )
                Text(stringResource(R.string.ui_drag_atoms_bonds_or_the_whole_selected_molecule),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.72f)
                )
                Text(stringResource(R.string.ui_clean_can_standardize_the_drawing_through_pubchem_before),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.72f)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_got_it)) }
                }
            }
        }
    }
}

@Composable
private fun StructureToolPanel(
    selectedElement: String,
    selectedBond: BondOrder,
    selectedToolLabel: String,
    selectedAtom: SketchAtom?,
    onElement: (String) -> Unit,
    onBond: (BondOrder) -> Unit,
    onCharge: (Int) -> Unit,
    onRing: (RingTemplate) -> Unit,
    onChain: (ChainTemplate) -> Unit
) {
    var showPeriodicTable by remember { mutableStateOf(false) }
    val elementScrollState = rememberScrollState()
    val bondTemplateScrollState = rememberScrollState()

    if (showPeriodicTable) {
        PeriodicTableDialog(
            selectedElement = selectedElement,
            onElement = { element ->
                onElement(element)
                showPeriodicTable = false
            },
            onDismiss = { showPeriodicTable = false }
        )
    }

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(0.2f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                StructurePickerLabel("Atoms")
                Row(
                    modifier = Modifier.horizontalScroll(elementScrollState),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CommonAtoms.forEach { element ->
                        CommonAtomButton(
                            element = element,
                            selected = selectedElement == element,
                            onClick = { onElement(element) }
                        )
                    }
                    Surface(
                        modifier = Modifier
                            .size(width = 44.dp, height = 38.dp)
                            .clickable(onClick = { showPeriodicTable = true }),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(0.34f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(0.36f))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                "...",
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface.copy(0.72f)
                            )
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                StructurePickerLabel("Bond type")
                Row(
                    modifier = Modifier.horizontalScroll(bondTemplateScrollState),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BondSymbolButton(BondOrder.SINGLE, selectedBond == BondOrder.SINGLE) { onBond(BondOrder.SINGLE) }
                    BondSymbolButton(BondOrder.DOUBLE, selectedBond == BondOrder.DOUBLE) { onBond(BondOrder.DOUBLE) }
                    BondSymbolButton(BondOrder.TRIPLE, selectedBond == BondOrder.TRIPLE) { onBond(BondOrder.TRIPLE) }
                    BondSymbolButton(BondOrder.AROMATIC, selectedBond == BondOrder.AROMATIC) { onBond(BondOrder.AROMATIC) }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                StructurePickerLabel("Templates")
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ChainTemplates.forEach { template ->
                        ChainTemplateButton(
                            template = template,
                            onClick = { onChain(template) }
                        )
                    }
                    StructureTemplates.forEach { template ->
                        RingTemplateButton(
                            template = template,
                            onClick = { onRing(template) }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Selected: ${selectedAtom?.let { it.element + chargeLabel(it.charge) } ?: selectedToolLabel}",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.58f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                OutlinedButton(
                    onClick = { onCharge(-1) },
                    enabled = selectedAtom != null && selectedAtom.charge > -StructureChargeLimit
                ) { Text("-") }
                OutlinedButton(
                    onClick = { onCharge(1) },
                    enabled = selectedAtom != null && selectedAtom.charge < StructureChargeLimit
                ) { Text("+") }
            }
        }
    }
}

@Composable
private fun CommonAtomButton(
    element: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val cpkColor = elementColor(element)
    Surface(
        modifier = Modifier
            .size(width = if (element.length > 1) 50.dp else 44.dp, height = 38.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = cpkColor.copy(alpha = if (selected) 0.24f else 0.11f),
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            cpkColor.copy(alpha = if (selected) 0.96f else 0.64f)
        )
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                element,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun StructurePickerLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface.copy(0.46f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}
@Composable
private fun StructureEditingActions(
    sketch: StructureSketch,
    canUndo: Boolean,
    canRedo: Boolean,
    selectionMode: Boolean,
    isStandardizing: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSelectMode: () -> Unit,
    onClean: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onClear: () -> Unit
) {
    var helpAction by remember { mutableStateOf<StructureActionItem?>(null) }
    val actions = listOf(
        StructureActionItem(
            label = "Undo",
            shortLabel = "Undo",
            description = "Restores the previous drawing step.",
            icon = Icons.Default.RestartAlt,
            enabled = canUndo,
            onClick = onUndo
        ),
        StructureActionItem(
            label = "Redo",
            shortLabel = "Redo",
            description = "Restores the drawing step you just undid.",
            icon = Icons.Default.Refresh,
            enabled = canRedo,
            onClick = onRedo
        ),
        StructureActionItem(
            label = "Select",
            shortLabel = "Select",
            description = "Selects atoms or bonds without drawing, moving, or changing them.",
            icon = Icons.Default.Cursor,
            enabled = true,
            selected = selectionMode,
            onClick = onSelectMode
        ),
        StructureActionItem(
            label = "Clean structure",
            shortLabel = "Clean",
            description = "Asks PubChem to standardize the drawing.",
            icon = Icons.Default.AutoFixHigh,
            enabled = sketch.atoms.isNotEmpty() && !isStandardizing,
            onClick = onClean
        ),
        StructureActionItem(
            label = "Import",
            shortLabel = "Import",
            description = "Imports SMILES, InChI, or a V2000 molfile.",
            icon = Icons.AutoMirrored.Filled.InsertDriveFile,
            enabled = true,
            onClick = onImport
        ),
        StructureActionItem(
            label = "Export",
            shortLabel = "Export",
            description = "Copies or shares this drawing as a molfile.",
            icon = Icons.Default.Share,
            enabled = sketch.atoms.isNotEmpty(),
            onClick = onExport
        ),
        StructureActionItem(
            label = "Clear drawing",
            shortLabel = "Clear",
            description = "Removes the whole drawing and current structure results.",
            icon = Icons.Default.Clear,
            enabled = sketch.atoms.isNotEmpty(),
            onClick = onClear
        )
    )

    helpAction?.let { action ->
        StructureActionHelpDialog(
            action = action,
            onDismiss = { helpAction = null }
        )
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(0.2f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            actions.forEach { action ->
                StructureActionButton(
                    action = action,
                    size = 42.dp,
                    iconSize = 20.dp,
                    onLongPress = { helpAction = action }
                )
            }
        }
    }
}

private data class StructureActionItem(
    val label: String,
    val shortLabel: String = label,
    val description: String,
    val icon: ImageVector,
    val enabled: Boolean,
    val selected: Boolean = false,
    val onClick: () -> Unit
)

@Composable
private fun StructureSelectionActions(
    selectedAtomId: Int?,
    selectedBondId: Int?,
    selectedMolecule: Boolean,
    onDeleteSelected: () -> Unit,
    onSelectMolecule: () -> Unit,
    onDuplicate: () -> Unit,
    modifier: Modifier = Modifier
) {
    var helpAction by remember { mutableStateOf<StructureActionItem?>(null) }
    val actions = listOf(
        StructureActionItem(
            label = when {
                selectedMolecule -> "Delete molecule"
                selectedBondId != null -> "Delete bond"
                else -> "Delete selected"
            },
            shortLabel = "Delete",
            description = if (selectedMolecule) {
                "Removes the selected molecule from the drawing."
            } else {
                "Removes the selected atom or bond from the drawing."
            },
            icon = Icons.Default.Delete,
            enabled = true,
            onClick = onDeleteSelected
        ),
        StructureActionItem(
            label = if (selectedMolecule) "Molecule selected" else "Select molecule",
            shortLabel = "Move",
            description = "Selects the whole molecule so you can drag it or delete it.",
            icon = Icons.Default.ViewInAr,
            enabled = selectedAtomId != null || selectedBondId != null || selectedMolecule,
            selected = selectedMolecule,
            onClick = onSelectMolecule
        ),
        StructureActionItem(
            label = "Duplicate fragment",
            shortLabel = "Copy",
            description = "Copies the connected fragment that contains the selected atom.",
            icon = Icons.Default.ContentCopy,
            enabled = selectedAtomId != null,
            onClick = onDuplicate
        )
    )

    helpAction?.let { action ->
        StructureActionHelpDialog(action = action, onDismiss = { helpAction = null })
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 7.dp,
        shadowElevation = 10.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(0.18f))
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            actions.forEach { action ->
                StructureActionButton(
                    action = action,
                    size = 40.dp,
                    iconSize = 19.dp,
                    onLongPress = { helpAction = action }
                )
            }
        }
    }
}

@Composable
private fun StructureActionButton(
    action: StructureActionItem,
    size: Dp = 48.dp,
    iconSize: Dp = 22.dp,
    onLongPress: () -> Unit
) {
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.onSurface.copy(0.34f)
    Column(
        modifier = Modifier
            .width(size + 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Surface(
            modifier = Modifier
                .size(size)
                .pointerInput(action.enabled, action.label) {
                    detectTapGestures(
                        onTap = { if (action.enabled) action.onClick() },
                        onLongPress = { onLongPress() }
                    )
                },
            shape = CircleShape,
            color = when {
                action.selected -> activeColor
                action.enabled -> activeColor.copy(0.12f)
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(0.28f)
            },
            border = BorderStroke(
                1.dp,
                if (action.enabled) activeColor.copy(if (action.selected) 0.78f else 0.48f) else MaterialTheme.colorScheme.outline.copy(0.18f)
            )
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    action.icon,
                    contentDescription = action.label,
                    tint = when {
                        action.selected -> MaterialTheme.colorScheme.onPrimary
                        action.enabled -> activeColor
                        else -> inactiveColor
                    },
                    modifier = Modifier.size(iconSize)
                )
            }
        }
        Text(
            text = action.shortLabel,
            color = MaterialTheme.colorScheme.onSurface.copy(if (action.enabled) 0.68f else 0.34f),
            style = MaterialTheme.typography.labelSmall,
            fontSize = 8.sp,
            lineHeight = 9.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun StructureActionHelpDialog(
    action: StructureActionItem,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.inverseSurface,
            tonalElevation = 8.dp,
            shadowElevation = 10.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    action.label,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    action.description,
                    color = MaterialTheme.colorScheme.inverseOnSurface.copy(0.78f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun StructureWarningsCard(warnings: List<StructureSearchWarning>) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(0.22f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(0.22f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            warnings.forEach { warning ->
                Text(
                    text = warning.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.74f)
                )
            }
        }
    }
}

@Composable
private fun StructureImportDialog(
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onImport: (String) -> Unit
) {
    var input by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(0.18f))
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(stringResource(R.string.ui_import_structure), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                Text(stringResource(R.string.ui_paste_smiles_inchi_or_a_v2000_molfile),
                    color = MaterialTheme.colorScheme.onSurface.copy(0.58f),
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
                    minLines = 5,
                    label = { Text(stringResource(R.string.ui_structure_text)) }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_cancel)) }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onImport(input) },
                        enabled = input.isNotBlank() && !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(stringResource(R.string.ui_import))
                    }
                }
            }
        }
    }
}

@Composable
private fun StructureExportDialog(
    molfile: String,
    onDismiss: () -> Unit,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(0.18f))
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(stringResource(R.string.ui_export_structure),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.ui_close_export_dialog),
                            tint = MaterialTheme.colorScheme.onSurface.copy(0.78f)
                        )
                    }
                }
                Text(
                    molfile,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .verticalScroll(rememberScrollState())
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(0.28f), RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.76f)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = { onCopy(molfile) },
                        modifier = Modifier.width(112.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.ui_copy), maxLines = 1, softWrap = false)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onShare(molfile) },
                        modifier = Modifier.width(146.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.ui_share), maxLines = 1, softWrap = false)
                    }
                }
            }
        }
    }
}

@Composable
private fun PeriodicTableDialog(
    selectedElement: String,
    onElement: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(0.24f))
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.ui_select_element),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(stringResource(R.string.ui_all_118_elements),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.54f)
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Close")
                    }
                }

                val horizontal = rememberScrollState()
                val vertical = rememberScrollState()
                Box(
                    modifier = Modifier
                        .heightIn(max = 460.dp)
                        .horizontalScroll(horizontal)
                        .verticalScroll(vertical)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        (1..9).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                (1..18).forEach { column ->
                                    val element = PeriodicTableElements.firstOrNull {
                                        it.tableRow == row && it.tableColumn == column
                                    }
                                    if (element == null) {
                                        Spacer(Modifier.size(width = 46.dp, height = 50.dp))
                                    } else {
                                        PeriodicElementCell(
                                            element = element,
                                            selected = selectedElement == element.symbol,
                                            onClick = { onElement(element.symbol) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PeriodicElementCell(
    element: PeriodicElement,
    selected: Boolean,
    onClick: () -> Unit
) {
    val cpkColor = elementColor(element.symbol)
    val textColor = MaterialTheme.colorScheme.onSurface
    Surface(
        modifier = Modifier
            .size(width = 46.dp, height = 50.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = cpkColor.copy(alpha = if (selected) 0.20f else 0.10f),
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            cpkColor.copy(alpha = if (selected) 0.95f else 0.62f)
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                element.atomicNumber.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = textColor.copy(0.68f),
                fontSize = 8.sp,
                maxLines = 1
            )
            Text(
                element.symbol,
                fontWeight = FontWeight.ExtraBold,
                color = textColor,
                fontSize = if (element.symbol.length == 1) 16.sp else 13.sp,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun BondSymbolButton(order: BondOrder, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .size(42.dp)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primary.copy(0.18f) else MaterialTheme.colorScheme.surfaceVariant.copy(0.38f),
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(0.28f)
        )
    ) {
        Box(contentAlignment = Alignment.Center) {
            BondSymbol(
                order = order,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(0.7f),
                modifier = Modifier.size(25.dp)
            )
        }
    }
}

@Composable
private fun BenzeneTemplateButton(onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .size(width = 74.dp, height = 42.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(0.28f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(0.32f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            BenzeneSymbol(
                tint = MaterialTheme.colorScheme.onSurface.copy(0.78f),
                modifier = Modifier.size(34.dp)
            )
        }
    }
}

@Composable
private fun RingTemplateButton(template: RingTemplate, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .size(46.dp)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(0.28f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(0.32f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            RingTemplateSymbol(
                template = template,
                tint = MaterialTheme.colorScheme.onSurface.copy(0.78f),
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
private fun ChainTemplateButton(template: ChainTemplate, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .size(46.dp)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(0.28f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(0.32f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            ChainTemplateSymbol(
                template = template,
                tint = MaterialTheme.colorScheme.onSurface.copy(0.78f),
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
private fun ChainTemplateSymbol(
    template: ChainTemplate,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val centerY = size.height / 2f
        val count = template.elements.size.coerceAtLeast(2)
        val step = size.width * 0.68f / (count - 1)
        val startX = size.width * 0.16f
        val points = (0 until count).map { index ->
            Offset(
                x = startX + index * step,
                y = centerY + if (index % 2 == 0) -size.height * 0.12f else size.height * 0.12f
            )
        }
        val stroke = 2.2.dp.toPx()
        points.zipWithNext().forEach { (start, end) ->
            when (template.bondOrder) {
                BondOrder.SINGLE -> drawLine(tint, start, end, stroke, StrokeCap.Round)
                BondOrder.DOUBLE -> {
                    drawLine(tint, start.copy(y = start.y - 3.dp.toPx()), end.copy(y = end.y - 3.dp.toPx()), stroke * 0.7f, StrokeCap.Round)
                    drawLine(tint, start.copy(y = start.y + 3.dp.toPx()), end.copy(y = end.y + 3.dp.toPx()), stroke * 0.7f, StrokeCap.Round)
                }
                BondOrder.TRIPLE -> {
                    drawLine(tint, start, end, stroke * 0.65f, StrokeCap.Round)
                    drawLine(tint, start.copy(y = start.y - 4.dp.toPx()), end.copy(y = end.y - 4.dp.toPx()), stroke * 0.65f, StrokeCap.Round)
                    drawLine(tint, start.copy(y = start.y + 4.dp.toPx()), end.copy(y = end.y + 4.dp.toPx()), stroke * 0.65f, StrokeCap.Round)
                }
                BondOrder.AROMATIC -> drawLine(tint, start, end, stroke, StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f)))
            }
        }
        drawIntoCanvas { canvas ->
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textSize = 8.sp.toPx()
                color = tint.toArgb()
            }
            template.elements.withIndex()
                .filter { it.value != "C" || template.elements.size <= 2 }
                .forEach { (index, element) ->
                    val point = points[index]
                    canvas.nativeCanvas.drawText(element, point.x, point.y + 3.dp.toPx(), paint)
                }
        }
    }
}

@Composable
private fun RingTemplateSymbol(
    template: RingTemplate,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val sides = template.elements.size
        val radius = min(size.width, size.height) * 0.38f
        val center = Offset(size.width / 2f, size.height / 2f)
        val points = (0 until sides).map { index ->
            val angle = (-PI / 2.0) + (2.0 * PI * index / sides)
            Offset(
                x = center.x + radius * cos(angle).toFloat(),
                y = center.y + radius * sin(angle).toFloat()
            )
        }
        val stroke = 2.4.dp.toPx()
        points.indices.forEach { index ->
            drawLine(tint, points[index], points[(index + 1) % points.size], stroke, StrokeCap.Round)
        }
        template.doubleBondIndices.forEach { index ->
            val start = points[index]
            val end = points[(index + 1) % points.size]
            val innerStart = start * 0.78f + center * 0.22f
            val innerEnd = end * 0.78f + center * 0.22f
            drawLine(tint, innerStart, innerEnd, stroke * 0.7f, StrokeCap.Round)
        }
        val heteroAtoms = template.elements.withIndex().filter { it.value != "C" }
        if (heteroAtoms.isNotEmpty()) {
            drawIntoCanvas { canvas ->
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textAlign = Paint.Align.CENTER
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textSize = 9.sp.toPx()
                }
                heteroAtoms.forEach { (index, element) ->
                    val point = points[index]
                    val atomColor = elementColor(element)
                    drawCircle(
                        color = atomColor.copy(alpha = 0.96f),
                        radius = 6.6.dp.toPx(),
                        center = point
                    )
                    paint.color = (if (atomColor.luminance() < 0.42f) Color.White else Color(0xFF111827)).toArgb()
                    canvas.nativeCanvas.drawText(
                        element,
                        point.x,
                        point.y + 3.2.dp.toPx(),
                        paint
                    )
                }
            }
        }
    }
}

@Composable
private fun BondSymbol(
    order: BondOrder,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val centerY = size.height / 2f
        val stroke = size.minDimension * 0.12f
        val left = Offset(size.width * 0.16f, centerY)
        val right = Offset(size.width * 0.84f, centerY)
        val gap = size.height * 0.18f
        when (order) {
            BondOrder.SINGLE -> drawLine(tint, left, right, stroke, StrokeCap.Round)
            BondOrder.DOUBLE -> {
                drawLine(tint, left.copy(y = centerY - gap), right.copy(y = centerY - gap), stroke, StrokeCap.Round)
                drawLine(tint, left.copy(y = centerY + gap), right.copy(y = centerY + gap), stroke, StrokeCap.Round)
            }
            BondOrder.TRIPLE -> {
                drawLine(tint, left, right, stroke, StrokeCap.Round)
                drawLine(tint, left.copy(y = centerY - gap * 1.35f), right.copy(y = centerY - gap * 1.35f), stroke, StrokeCap.Round)
                drawLine(tint, left.copy(y = centerY + gap * 1.35f), right.copy(y = centerY + gap * 1.35f), stroke, StrokeCap.Round)
            }
            BondOrder.AROMATIC -> {
                drawLine(
                    color = tint,
                    start = left,
                    end = right,
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(size.width * 0.18f, size.width * 0.11f))
                )
            }
        }
    }
}

@Composable
private fun BenzeneSymbol(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val radius = size.minDimension * 0.38f
        val center = Offset(size.width / 2f, size.height / 2f)
        val points = (0 until 6).map { index ->
            val angle = (-PI / 2.0) + (2.0 * PI * index / 6.0)
            Offset(
                x = center.x + radius * cos(angle).toFloat(),
                y = center.y + radius * sin(angle).toFloat()
            )
        }
        val stroke = size.minDimension * 0.065f
        points.indices.forEach { index ->
            drawLine(tint, points[index], points[(index + 1) % points.size], stroke, StrokeCap.Round)
        }
        listOf(0, 2, 4).forEach { index ->
            val start = points[index]
            val end = points[(index + 1) % points.size]
            val inwardStart = start * 0.76f + center * 0.24f
            val inwardEnd = end * 0.76f + center * 0.24f
            drawLine(tint, inwardStart, inwardEnd, stroke * 0.72f, StrokeCap.Round)
        }
    }
}

@Composable
private fun StructureSketchCanvas(
    sketch: StructureSketch,
    selectedAtomId: Int?,
    selectedBondId: Int?,
    selectedMolecule: Boolean,
    selectedElement: String,
    selectedBond: BondOrder,
    selectedTemplate: RingTemplate?,
    selectedChainTemplate: ChainTemplate?,
    selectionMode: Boolean,
    panOffset: Offset,
    onPanOffsetChange: (Offset) -> Unit,
    onCanvasGestureActiveChange: (Boolean) -> Unit,
    onSketchChange: (StructureSketch) -> Unit,
    onSelectedAtom: (Int?) -> Unit,
    onSelectedBond: (Int?) -> Unit
) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var draggingAtomId by remember { mutableStateOf<Int?>(null) }
    var draggingBondId by remember { mutableStateOf<Int?>(null) }
    var draggingMolecule by remember { mutableStateOf(false) }
    var panningCanvas by remember { mutableStateOf(false) }
    val latestSketch by rememberUpdatedState(sketch)
    val latestPanOffset by rememberUpdatedState(panOffset)
    val latestSelectedAtomId by rememberUpdatedState(selectedAtomId)
    val latestSelectedMolecule by rememberUpdatedState(selectedMolecule)
    val latestSelectedElement by rememberUpdatedState(selectedElement)
    val latestSelectedBond by rememberUpdatedState(selectedBond)
    val latestSelectedTemplate by rememberUpdatedState(selectedTemplate)
    val latestSelectedChainTemplate by rememberUpdatedState(selectedChainTemplate)
    val latestSelectionMode by rememberUpdatedState(selectionMode)
    val primary = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface
    val onSurface = MaterialTheme.colorScheme.onSurface
    val outline = MaterialTheme.colorScheme.outline

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(0.2f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 360.dp)
                .background(surface)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(380.dp)
                    .onSizeChanged { canvasSize = it }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(pass = PointerEventPass.Initial)
                            onCanvasGestureActiveChange(true)
                            waitForUpOrCancellation(pass = PointerEventPass.Initial)
                            onCanvasGestureActiveChange(false)
                        }
                    }
                    .pointerInput(canvasSize) {
                        detectTapGestures { offset ->
                            val activeSketch = latestSketch
                            val activePanOffset = latestPanOffset
                            val point = offset.toSketchPoint(canvasSize, activePanOffset)
                            if (latestSelectionMode) {
                                val hit = activeSketch.hitAtom(offset, canvasSize, activePanOffset)
                                if (hit != null) {
                                    onSelectedAtom(hit.id)
                                    return@detectTapGestures
                                }
                                val hitBond = activeSketch.hitBond(offset, canvasSize, activePanOffset)
                                if (hitBond != null) {
                                    onSelectedBond(hitBond.id)
                                    return@detectTapGestures
                                }
                                onSelectedAtom(null)
                                onSelectedBond(null)
                                return@detectTapGestures
                            }
                            val activeTemplate = latestSelectedTemplate
                            val activeChainTemplate = latestSelectedChainTemplate
                            if (activeTemplate != null || activeChainTemplate != null) {
                                onSketchChange(
                                    applyStructureCanvasBlankTap(
                                        sketch = activeSketch,
                                        x = point.first,
                                        y = point.second,
                                        selectedElement = latestSelectedElement,
                                        selectedTemplate = activeTemplate,
                                        selectedChainTemplate = activeChainTemplate,
                                        selectionMode = false
                                    )
                                )
                                onSelectedAtom(null)
                                onSelectedBond(null)
                                return@detectTapGestures
                            }
                            val hit = activeSketch.hitAtom(offset, canvasSize, activePanOffset)
                            if (hit != null) {
                                val selected = latestSelectedAtomId
                                if (selected != null && selected != hit.id) {
                                    onSketchChange(activeSketch.addBond(selected, hit.id, latestSelectedBond))
                                }
                                onSelectedAtom(hit.id)
                            } else {
                                val hitBond = activeSketch.hitBond(offset, canvasSize, activePanOffset)
                                if (hitBond != null) {
                                    onSketchChange(activeSketch.cycleBondOrder(hitBond.id))
                                    onSelectedBond(hitBond.id)
                                } else {
                                    val nextAtomId = (activeSketch.atoms.maxOfOrNull { it.id } ?: 0) + 1
                                    val withAtom = applyStructureCanvasBlankTap(
                                        sketch = activeSketch,
                                        x = point.first,
                                        y = point.second,
                                        selectedElement = latestSelectedElement,
                                        selectedTemplate = null,
                                        selectedChainTemplate = null,
                                        selectionMode = false
                                    )
                                    val selected = latestSelectedAtomId
                                    val nextSketch = if (selected != null) {
                                        withAtom.addBond(selected, nextAtomId, latestSelectedBond)
                                    } else {
                                        withAtom
                                    }
                                    onSketchChange(nextSketch)
                                    onSelectedAtom(nextAtomId)
                                }
                            }
                        }
                    }
                    .pointerInput(canvasSize) {
                        detectDragGestures(
                            onDragStart = { start ->
                                val activeSketch = latestSketch
                                val activePanOffset = latestPanOffset
                                val hitAtomId = activeSketch.hitAtom(start, canvasSize, activePanOffset)?.id
                                val hitBondId = if (hitAtomId == null) activeSketch.hitBond(start, canvasSize, activePanOffset)?.id else null
                                if (latestSelectionMode) {
                                    draggingMolecule = latestSelectedMolecule && (hitAtomId != null || hitBondId != null)
                                    panningCanvas = false
                                    draggingAtomId = if (!draggingMolecule) hitAtomId else null
                                    draggingBondId = if (!draggingMolecule) hitBondId else null
                                    when {
                                        draggingMolecule -> Unit
                                        hitAtomId != null -> onSelectedAtom(hitAtomId)
                                        hitBondId != null -> onSelectedBond(hitBondId)
                                        else -> {
                                            onSelectedAtom(null)
                                            onSelectedBond(null)
                                        }
                                    }
                                } else {
                                    draggingMolecule = latestSelectedMolecule && (hitAtomId != null || hitBondId != null)
                                    panningCanvas = hitAtomId == null && hitBondId == null
                                    draggingAtomId = if (!draggingMolecule) hitAtomId else null
                                    draggingBondId = if (!draggingMolecule) hitBondId else null
                                    draggingAtomId?.let(onSelectedAtom)
                                    draggingBondId?.let(onSelectedBond)
                                }
                            },
                            onDragEnd = {
                                draggingAtomId = null
                                draggingBondId = null
                                draggingMolecule = false
                                panningCanvas = false
                            },
                            onDragCancel = {
                                draggingAtomId = null
                                draggingBondId = null
                                draggingMolecule = false
                                panningCanvas = false
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                if (latestSelectionMode) {
                                    when {
                                        draggingMolecule -> {
                                            val scale = sketchScale(canvasSize).coerceAtLeast(1f)
                                            onSketchChange(
                                                applyStructureCanvasSelectionDrag(
                                                    sketch = latestSketch,
                                                    draggingMolecule = true,
                                                    draggingAtomId = null,
                                                    draggingBondId = null,
                                                    atomX = 0.0,
                                                    atomY = 0.0,
                                                    bondDx = (dragAmount.x / scale).toDouble(),
                                                    bondDy = (dragAmount.y / scale).toDouble()
                                                )
                                            )
                                        }
                                        draggingAtomId != null -> {
                                            val point = change.position.toSketchPoint(canvasSize, latestPanOffset)
                                            onSketchChange(
                                                applyStructureCanvasSelectionDrag(
                                                    sketch = latestSketch,
                                                    draggingMolecule = false,
                                                    draggingAtomId = draggingAtomId,
                                                    draggingBondId = null,
                                                    atomX = point.first,
                                                    atomY = point.second,
                                                    bondDx = 0.0,
                                                    bondDy = 0.0
                                                )
                                            )
                                        }
                                        draggingBondId != null -> {
                                            val scale = sketchScale(canvasSize).coerceAtLeast(1f)
                                            onSketchChange(
                                                applyStructureCanvasSelectionDrag(
                                                    sketch = latestSketch,
                                                    draggingMolecule = false,
                                                    draggingAtomId = null,
                                                    draggingBondId = draggingBondId,
                                                    atomX = 0.0,
                                                    atomY = 0.0,
                                                    bondDx = (dragAmount.x / scale).toDouble(),
                                                    bondDy = (dragAmount.y / scale).toDouble()
                                                )
                                            )
                                        }
                                    }
                                } else {
                                    when {
                                        draggingMolecule -> {
                                            val scale = sketchScale(canvasSize).coerceAtLeast(1f)
                                            onSketchChange(latestSketch.translate((dragAmount.x / scale).toDouble(), (dragAmount.y / scale).toDouble()))
                                        }
                                        draggingAtomId != null -> {
                                            val point = change.position.toSketchPoint(canvasSize, latestPanOffset)
                                            onSketchChange(latestSketch.moveAtom(draggingAtomId!!, point.first, point.second))
                                        }
                                        draggingBondId != null -> {
                                            val scale = sketchScale(canvasSize).coerceAtLeast(1f)
                                            val dx = (dragAmount.x / scale).toDouble()
                                            val dy = (dragAmount.y / scale).toDouble()
                                            val activeSketch = latestSketch
                                            val bond = activeSketch.bonds.firstOrNull { it.id == draggingBondId }
                                            if (bond != null) {
                                                val atomA = activeSketch.atoms.firstOrNull { it.id == bond.atomA }
                                                val atomB = activeSketch.atoms.firstOrNull { it.id == bond.atomB }
                                                if (atomA != null && atomB != null) {
                                                    onSketchChange(
                                                        activeSketch
                                                            .moveAtom(atomA.id, atomA.x + dx, atomA.y + dy)
                                                            .moveAtom(atomB.id, atomB.x + dx, atomB.y + dy)
                                                    )
                                                }
                                            }
                                        }
                                        panningCanvas -> {
                                            onPanOffsetChange(latestPanOffset + dragAmount)
                                        }
                                    }
                                }
                            }
                        )
                    }
            ) {
                val grid = 36.dp.toPx()
                var x = ((panOffset.x % grid) + grid) % grid
                while (x < size.width) {
                    drawLine(
                        color = outline.copy(0.08f),
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 1f
                    )
                    x += grid
                }
                var y = ((panOffset.y % grid) + grid) % grid
                while (y < size.height) {
                    drawLine(
                        color = outline.copy(0.08f),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1f
                    )
                    y += grid
                }

                sketch.bonds.forEach { bond ->
                    val atomA = sketch.atoms.firstOrNull { it.id == bond.atomA } ?: return@forEach
                    val atomB = sketch.atoms.firstOrNull { it.id == bond.atomB } ?: return@forEach
                    drawBond(
                        start = atomA.toOffset(canvasSize, panOffset),
                        end = atomB.toOffset(canvasSize, panOffset),
                        order = bond.order,
                        color = if (bond.id == selectedBondId) primary else onSurface.copy(0.72f)
                    )
                }

                val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textAlign = Paint.Align.CENTER
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textSize = 15.sp.toPx()
                }

                sketch.atoms.forEach { atom ->
                    val center = atom.toOffset(canvasSize, panOffset)
                    val selected = atom.id == selectedAtomId || selectedMolecule
                    val atomColor = elementColor(atom.element)
                    val labelColor = if (atomColor.luminance() < 0.42f) Color.White else Color(0xFF111827)
                    drawCircle(
                        color = if (selected) primary.copy(0.16f) else atomColor.copy(alpha = 0.18f),
                        radius = 23.dp.toPx(),
                        center = center
                    )
                    drawCircle(
                        color = atomColor,
                        radius = 18.dp.toPx(),
                        center = center
                    )
                    drawCircle(
                        color = if (selected) primary else outline.copy(0.52f),
                        radius = 22.dp.toPx(),
                        center = center,
                        style = Stroke(width = if (selected) 2.5.dp.toPx() else 1.5.dp.toPx())
                    )
                    drawIntoCanvas { canvas ->
                        labelPaint.color = labelColor.toArgb()
                        canvas.nativeCanvas.drawText(
                            atom.element + chargeLabel(atom.charge),
                            center.x,
                            center.y + 5.dp.toPx(),
                            labelPaint
                        )
                    }
                }

                if (sketch.atoms.isEmpty()) {
                    drawIntoCanvas { canvas ->
                        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = onSurface.copy(0.34f).toArgb()
                            textAlign = Paint.Align.CENTER
                            textSize = 16.sp.toPx()
                            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        }
                        canvas.nativeCanvas.drawText(
                            "Tap to place atoms",
                            size.width / 2f,
                            size.height / 2f,
                            paint
                        )
                    }
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBond(
    start: Offset,
    end: Offset,
    order: Int,
    color: Color
) {
    val dx = end.x - start.x
    val dy = end.y - start.y
    val length = hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtLeast(1f)
    val normal = Offset(-dy / length, dx / length)
    val spacing = 5.dp.toPx()
    val stroke = 3.dp.toPx()
    when (order) {
        2 -> {
            drawLine(color, start + normal * spacing, end + normal * spacing, stroke, StrokeCap.Round)
            drawLine(color, start - normal * spacing, end - normal * spacing, stroke, StrokeCap.Round)
        }
        3 -> {
            drawLine(color, start, end, stroke, StrokeCap.Round)
            drawLine(color, start + normal * spacing * 1.4f, end + normal * spacing * 1.4f, stroke, StrokeCap.Round)
            drawLine(color, start - normal * spacing * 1.4f, end - normal * spacing * 1.4f, stroke, StrokeCap.Round)
        }
        4 -> {
            drawLine(color, start, end, stroke, StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)))
        }
        else -> drawLine(color, start, end, stroke, StrokeCap.Round)
    }
}

@Composable
private fun StructureSearchControls(
    mode: StructureSearchMode,
    similarityThreshold: Int,
    maxRecords: Int,
    onModeChange: (StructureSearchMode) -> Unit,
    onThresholdChange: (Int) -> Unit,
    onMaxRecordsChange: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var thresholdExpanded by remember { mutableStateOf(false) }
    var maxRecordsExpanded by remember { mutableStateOf(false) }
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(0.2f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.ui_search_mode), fontWeight = FontWeight.Bold)
                    Text(
                        mode.description,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.52f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Box {
                    TextButton(onClick = { expanded = true }) {
                        Text(mode.label, fontWeight = FontWeight.Bold)
                        Icon(Icons.Default.KeyboardArrowDown, null, modifier = Modifier.size(16.dp))
                    }
                    SettingsDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        StructureSearchMode.entries.forEach { item ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(item.label, fontWeight = FontWeight.Bold)
                                        Text(
                                            item.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(0.52f)
                                        )
                                    }
                                },
                                onClick = {
                                    onModeChange(item)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { maxRecordsExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("$maxRecords results", maxLines = 1)
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.KeyboardArrowDown, null, modifier = Modifier.size(16.dp))
                    }
                    SettingsDropdownMenu(
                        expanded = maxRecordsExpanded,
                        onDismissRequest = { maxRecordsExpanded = false }
                    ) {
                        listOf(10, 20, 30, 50).forEach { value ->
                            DropdownMenuItem(
                                text = { Text("$value results") },
                                onClick = {
                                    onMaxRecordsChange(value)
                                    maxRecordsExpanded = false
                                }
                            )
                        }
                    }
                }
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { thresholdExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = mode == StructureSearchMode.SIMILAR
                    ) {
                        Text("$similarityThreshold% similar", maxLines = 1)
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.KeyboardArrowDown, null, modifier = Modifier.size(16.dp))
                    }
                    SettingsDropdownMenu(
                        expanded = thresholdExpanded,
                        onDismissRequest = { thresholdExpanded = false }
                    ) {
                        listOf(70, 80, 85, 90, 95).forEach { value ->
                            DropdownMenuItem(
                                text = { Text("$value% similarity") },
                                onClick = {
                                    onThresholdChange(value)
                                    thresholdExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StructureSearchConfigDialog(
    mode: StructureSearchMode,
    similarityThreshold: Int,
    maxRecords: Int,
    onModeChange: (StructureSearchMode) -> Unit,
    onThresholdChange: (Int) -> Unit,
    onMaxRecordsChange: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(0.18f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.ui_structure_search), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Close")
                    }
                }
                StructureSearchControls(
                    mode = mode,
                    similarityThreshold = similarityThreshold,
                    maxRecords = maxRecords,
                    onModeChange = onModeChange,
                    onThresholdChange = onThresholdChange,
                    onMaxRecordsChange = onMaxRecordsChange
                )
            }
        }
    }
}

@Composable
private fun FloatingStructureSearchButton(
    canSearch: Boolean,
    isLoading: Boolean,
    onClick: () -> Unit,
    onConfigClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        shadowElevation = 12.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(0.18f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onClick,
                enabled = canSearch,
                modifier = Modifier
                    .weight(1f)
                    .height(58.dp),
                shape = RoundedCornerShape(22.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(0.58f)
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, disabledElevation = 0.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    StructureSearchIcon(
                        tint = if (canSearch) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(0.58f),
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(if (isLoading) "Searching..." else "Search structure", fontWeight = FontWeight.Bold)
            }
            Surface(
                modifier = Modifier.size(58.dp).clickable(onClick = onConfigClick),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(0.54f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(0.2f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Tune,
                        contentDescription = stringResource(R.string.ui_structure_search_settings),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StructureSearchResultsDialog(
    state: StructureSearchUiState,
    onDismiss: () -> Unit,
    onOpenResult: (Long) -> Unit
) {
    val visibleSlots = structureSearchVisibleResultSlots(state)
    val resultListMaxHeight = ((visibleSlots * 96) + ((visibleSlots - 1).coerceAtLeast(0) * 10)).dp +
        if (state.isLoading) 72.dp else 0.dp

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 22.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(0.22f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (!state.error.isNullOrBlank() && !state.isLoading && state.results.isEmpty()) {
                                "Structure search failed"
                            } else {
                                "Matching results"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            when {
                                state.isLoading -> "Searching PubChem..."
                                !state.error.isNullOrBlank() && state.results.isEmpty() -> "Try a simpler drawing or clean the structure."
                                else -> "${state.results.size} match${if (state.results.size == 1) "" else "es"} found"
                            },
                            color = MaterialTheme.colorScheme.onSurface.copy(0.58f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.ui_close_results))
                    }
                }

                StructureSearchResults(
                    state = state,
                    onOpenResult = onOpenResult,
                    modifier = Modifier.heightIn(max = resultListMaxHeight)
                )
            }
        }
    }
}

@Composable
private fun StructureSearchResults(
    state: StructureSearchUiState,
    onOpenResult: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        if (!state.isLoading && state.results.isEmpty() && !state.error.isNullOrBlank()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.32f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = state.error,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
        state.results.forEach { result ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onOpenResult(result.cid) },
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(0.2f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .background(Color.White, RoundedCornerShape(14.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(0.16f), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = pubChemStructureThumbnailUrl(result.cid),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().padding(5.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(result.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (result.formula.isNotBlank()) {
                            Text(
                                toSubscriptFormula(result.formula),
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            buildString {
                                append("CID ${result.cid}")
                                if (result.molecularWeight.isNotBlank()) append(" • MW ${result.molecularWeight}")
                            },
                            color = MaterialTheme.colorScheme.onSurface.copy(0.5f),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurface.copy(0.36f))
                }
            }
        }
    }
}

@Composable
fun StructureSearchIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val radius = min(size.width, size.height) * 0.36f
        val center = Offset(size.width / 2f, size.height / 2f)
        val points = (0 until 6).map { index ->
            val angle = (-PI / 2.0) + (2.0 * PI * index / 6.0)
            Offset(
                x = center.x + radius * cos(angle).toFloat(),
                y = center.y + radius * sin(angle).toFloat()
            )
        }
        points.indices.forEach { index ->
            drawLine(
                color = tint,
                start = points[index],
                end = points[(index + 1) % points.size],
                strokeWidth = size.minDimension * 0.085f,
                cap = StrokeCap.Round
            )
        }
        drawLine(tint, points[0] * 0.76f + points[1] * 0.24f, points[1] * 0.76f + points[0] * 0.24f, size.minDimension * 0.045f, StrokeCap.Round)
        drawLine(tint, points[2] * 0.76f + points[3] * 0.24f, points[3] * 0.76f + points[2] * 0.24f, size.minDimension * 0.045f, StrokeCap.Round)
        drawLine(tint, points[4] * 0.76f + points[5] * 0.24f, points[5] * 0.76f + points[4] * 0.24f, size.minDimension * 0.045f, StrokeCap.Round)
    }
}

private fun SketchAtom.toOffset(size: IntSize, panOffset: Offset = Offset.Zero): Offset {
    val scale = sketchScale(size)
    return Offset(
        x = size.width / 2f + panOffset.x + x.toFloat() * scale,
        y = size.height / 2f + panOffset.y + y.toFloat() * scale
    )
}

private fun Offset.toSketchPoint(size: IntSize, panOffset: Offset = Offset.Zero): Pair<Double, Double> {
    val scale = sketchScale(size).coerceAtLeast(1f)
    return Pair(
        ((x - size.width / 2f - panOffset.x) / scale).toDouble(),
        ((y - size.height / 2f - panOffset.y) / scale).toDouble()
    )
}

private fun StructureSketch.hitAtom(offset: Offset, size: IntSize, panOffset: Offset = Offset.Zero): SketchAtom? {
    val radius = 28f
    return atoms.minByOrNull { atom ->
        val point = atom.toOffset(size, panOffset)
        hypot((point.x - offset.x).toDouble(), (point.y - offset.y).toDouble())
    }?.takeIf { atom ->
        val point = atom.toOffset(size, panOffset)
        hypot((point.x - offset.x).toDouble(), (point.y - offset.y).toDouble()) <= radius
    }
}

private fun StructureSketch.hitBond(offset: Offset, size: IntSize, panOffset: Offset = Offset.Zero): SketchBond? {
    return bonds
        .mapNotNull { bond ->
            val atomA = atoms.firstOrNull { it.id == bond.atomA } ?: return@mapNotNull null
            val atomB = atoms.firstOrNull { it.id == bond.atomB } ?: return@mapNotNull null
            bond to distanceToSegment(offset, atomA.toOffset(size, panOffset), atomB.toOffset(size, panOffset))
        }
        .filter { it.second <= 18f }
        .minByOrNull { it.second }
        ?.first
}

private fun distanceToSegment(point: Offset, start: Offset, end: Offset): Double {
    val dx = end.x - start.x
    val dy = end.y - start.y
    if (dx == 0f && dy == 0f) {
        return hypot((point.x - start.x).toDouble(), (point.y - start.y).toDouble())
    }
    val t = (((point.x - start.x) * dx + (point.y - start.y) * dy) / (dx * dx + dy * dy)).coerceIn(0f, 1f)
    val projected = Offset(start.x + t * dx, start.y + t * dy)
    return hypot((point.x - projected.x).toDouble(), (point.y - projected.y).toDouble())
}

private fun sketchScale(size: IntSize): Float =
    (min(size.width, size.height).coerceAtLeast(1) / 7.0f)

private fun bondToolLabel(order: BondOrder): String =
    when (order) {
        BondOrder.SINGLE -> "Single bond"
        BondOrder.DOUBLE -> "Double bond"
        BondOrder.TRIPLE -> "Triple bond"
        BondOrder.AROMATIC -> "Aromatic bond"
    }

private fun bondToolLabel(order: Int): String =
    bondToolLabel(BondOrder.entries.firstOrNull { it.molfileValue == order } ?: BondOrder.SINGLE)

private fun ringTemplateLabel(template: RingTemplate): String =
    template.label

private fun chainTemplateLabel(template: ChainTemplate): String =
    template.label

private fun chargeLabel(charge: Int): String =
    when {
        charge > 0 -> if (charge == 1) "⁺" else "${charge.toSuperscriptDigits()}⁺"
        charge < 0 -> if (charge == -1) "⁻" else "${(-charge).toSuperscriptDigits()}⁻"
        else -> ""
    }

private fun Int.toSuperscriptDigits(): String =
    toString().map { digit ->
        when (digit) {
            '0' -> '⁰'
            '1' -> '¹'
            '2' -> '²'
            '3' -> '³'
            '4' -> '⁴'
            '5' -> '⁵'
            '6' -> '⁶'
            '7' -> '⁷'
            '8' -> '⁸'
            '9' -> '⁹'
            else -> digit
        }
    }.joinToString("")

private operator fun Offset.times(value: Float): Offset = Offset(x * value, y * value)

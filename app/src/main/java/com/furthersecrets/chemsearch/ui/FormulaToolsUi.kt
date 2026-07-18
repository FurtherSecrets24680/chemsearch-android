package com.furthersecrets.chemsearch.ui

import com.furthersecrets.chemsearch.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.furthersecrets.chemsearch.data.EmpiricalFormulaCalculationResult
import com.furthersecrets.chemsearch.data.FormulaCompositionComponent
import com.furthersecrets.chemsearch.data.FormulaCompositionMode
import com.furthersecrets.chemsearch.data.PrecipitationPredictionResult
import com.furthersecrets.chemsearch.data.SolubilityState
import com.furthersecrets.chemsearch.data.calculateEmpiricalFormulaFromComposition
import com.furthersecrets.chemsearch.data.calculateEmpiricalFormulaFromMolecularFormula
import com.furthersecrets.chemsearch.data.predictPrecipitation

private enum class EmpiricalFormulaEntryMode(val label: String) {
    PERCENT("Percent"),
    MASS("Mass"),
    FORMULA("Formula")
}

private data class EmpiricalFormulaInputRow(
    val element: String,
    val amount: String
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EmpiricalFormulaFinderTool() {
    var mode by remember { mutableStateOf(EmpiricalFormulaEntryMode.PERCENT) }
    val rows = remember {
        mutableStateListOf(
            EmpiricalFormulaInputRow("C", ""),
            EmpiricalFormulaInputRow("H", ""),
            EmpiricalFormulaInputRow("O", "")
        )
    }
    var formulaInput by remember { mutableStateOf(TextFieldValue("")) }
    var molecularMassInput by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<EmpiricalFormulaCalculationResult?>(null) }
    var showInfo by remember { mutableStateOf(false) }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    fun calculate() {
        focusManager.clearFocus()
        val molecularMass = molecularMassInput.toDoubleOrNull()
        result = when (mode) {
            EmpiricalFormulaEntryMode.PERCENT,
            EmpiricalFormulaEntryMode.MASS -> {
                val compositionMode = if (mode == EmpiricalFormulaEntryMode.PERCENT) {
                    FormulaCompositionMode.PERCENT
                } else {
                    FormulaCompositionMode.MASS
                }
                calculateEmpiricalFormulaFromComposition(
                    components = rows.map { row ->
                        FormulaCompositionComponent(row.element, row.amount.toDoubleOrNull() ?: 0.0)
                    },
                    mode = compositionMode,
                    molecularMass = molecularMass
                )
            }
            EmpiricalFormulaEntryMode.FORMULA -> calculateEmpiricalFormulaFromMolecularFormula(
                formula = formulaInput.text,
                molecularMass = molecularMass
            )
        }
    }

    if (showInfo) {
        InfoDialog(
            title = "Empirical Formula Finder",
            entries = listOf(
                "Percent mode" to "Treats percentages as grams in a 100 g sample, then converts each element to moles.",
                "Mass mode" to "Uses the entered mass of each element directly.",
                "Formula mode" to "Reduces a molecular formula to its simplest whole-number ratio.",
                "Molecular formula" to "If molar mass is entered, the app checks whether it is a clean multiple of the empirical formula mass."
            ),
            onDismiss = { showInfo = false }
        )
    }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ToolTitleRow(title = "Empirical Formula Finder", onInfo = { showInfo = true })
        Text(stringResource(R.string.ui_use_composition_data_or_reduce_an_existing_formula),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(0.58f)
        )

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            EmpiricalFormulaEntryMode.entries.forEach { option ->
                FilterChip(
                    selected = mode == option,
                    onClick = {
                        mode = option
                        result = null
                        focusManager.clearFocus()
                    },
                    label = { Text(option.label) },
                    colors = chemFilterChipColors(),
                    leadingIcon = if (mode == option) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null
                )
            }
        }

        if (mode == EmpiricalFormulaEntryMode.FORMULA) {
            OutlinedTextField(
                value = formulaInput,
                onValueChange = { formulaInput = it },
                label = { Text(stringResource(R.string.ui_molecular_formula)) },
                placeholder = { Text(stringResource(R.string.ui_e_g_c6h12o6), color = MaterialTheme.colorScheme.onSurface.copy(0.4f)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                visualTransformation = FormulaSubscriptTransformation,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { calculate() })
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                rows.forEachIndexed { index, row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = row.element,
                            onValueChange = { value -> rows[index] = row.copy(element = value.take(2)) },
                            label = { Text(stringResource(R.string.ui_element)) },
                            modifier = Modifier.weight(0.8f),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Next)
                        )
                        OutlinedTextField(
                            value = row.amount,
                            onValueChange = { value -> rows[index] = row.copy(amount = value) },
                            label = { Text(if (mode == EmpiricalFormulaEntryMode.PERCENT) "%" else "Mass") },
                            modifier = Modifier.weight(1.2f),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next)
                        )
                        IconButton(
                            onClick = { if (rows.size > 1) rows.removeAt(index) },
                            enabled = rows.size > 1,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.ui_remove_row), modifier = Modifier.size(18.dp))
                        }
                    }
                }
                TextButton(onClick = { rows.add(EmpiricalFormulaInputRow("", "")) }, contentPadding = PaddingValues(horizontal = 0.dp)) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.ui_add_element))
                }
            }
        }

        OutlinedTextField(
            value = molecularMassInput,
            onValueChange = { molecularMassInput = it },
            label = { Text(stringResource(R.string.ui_molar_mass_for_molecular_formula_optional)) },
            placeholder = { Text(stringResource(R.string.ui_e_g_180_16), color = MaterialTheme.colorScheme.onSurface.copy(0.4f)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { calculate() })
        )

        Button(onClick = { calculate() }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
            Icon(Icons.Default.Calculate, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.ui_calculate_formula))
        }

        result?.let { EmpiricalFormulaResultCard(it) }
        FormulaExplanationCard(
            latexFormula = "moles = mass / atomic mass",
            explanation = "Divide all mole values by the smallest value, then multiply if needed to get whole-number subscripts."
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PrecipitatePredictorTool() {
    var first by remember { mutableStateOf(TextFieldValue("")) }
    var second by remember { mutableStateOf(TextFieldValue("")) }
    var result by remember { mutableStateOf<PrecipitationPredictionResult?>(null) }
    var showInfo by remember { mutableStateOf(false) }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    fun predict() {
        focusManager.clearFocus()
        result = if (first.text.isBlank() || second.text.isBlank()) {
            PrecipitationPredictionResult(error = "Enter two aqueous ionic compounds")
        } else {
            predictPrecipitation(first.text, second.text)
        }
    }

    if (showInfo) {
        InfoDialog(
            title = "Precipitate Predictor",
            entries = listOf(
                "What it does" to "Predicts double-replacement products and checks common solubility rules.",
                "Best inputs" to "Use common aqueous ionic compounds such as AgNO3, NaCl, BaCl2, Na2SO4, Ca(OH)2, or Na2CO3.",
                "Limits" to "This first version focuses on common school and lab solubility rules, not every complex ion or coordination compound.",
                "Net ionic equation" to "Spectator ions are removed when exactly one precipitate is predicted."
            ),
            onDismiss = { showInfo = false }
        )
    }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ToolTitleRow(title = "Precipitate Predictor", onInfo = { showInfo = true })
        Text(stringResource(R.string.ui_enter_two_aqueous_ionic_compounds_to_predict_products),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(0.58f)
        )

        OutlinedTextField(
            value = first,
            onValueChange = { first = it },
            label = { Text(stringResource(R.string.ui_first_compound)) },
            placeholder = { Text(stringResource(R.string.ui_e_g_agno3), color = MaterialTheme.colorScheme.onSurface.copy(0.4f)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            visualTransformation = FormulaSubscriptTransformation,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Next)
        )
        OutlinedTextField(
            value = second,
            onValueChange = { second = it },
            label = { Text(stringResource(R.string.ui_second_compound)) },
            placeholder = { Text(stringResource(R.string.ui_e_g_nacl), color = MaterialTheme.colorScheme.onSurface.copy(0.4f)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            visualTransformation = FormulaSubscriptTransformation,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { predict() })
        )

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                Triple("AgCl", "AgNO3", "NaCl"),
                Triple("BaSO4", "BaCl2", "Na2SO4"),
                Triple("CaCO3", "Ca(OH)2", "Na2CO3"),
                Triple("No ppt", "NaCl", "KNO3")
            ).forEach { (label, a, b) ->
                AssistChip(
                    onClick = {
                        first = textFieldAtEnd(a)
                        second = textFieldAtEnd(b)
                        result = null
                        focusManager.clearFocus()
                    },
                    colors = chemAssistChipColors(),
                    border = chemAssistChipBorder(),
                    label = { Text(label) },
                    leadingIcon = { Icon(Icons.Default.Science, null, modifier = Modifier.size(14.dp)) }
                )
            }
        }

        Button(onClick = { predict() }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
            Icon(Icons.Default.Science, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.ui_predict_precipitate))
        }

        result?.let { PrecipitationResultCard(it) }
        FormulaExplanationCard(
            latexFormula = "AB(aq) + CD(aq) ⟶ AD + CB",
            explanation = "The tool swaps ions, balances the equation, then applies common solubility rules to each product."
        )
    }
}

@Composable
private fun EmpiricalFormulaResultCard(result: EmpiricalFormulaCalculationResult) {
    if (result.error != null) {
        ToolErrorCard(result.error)
        return
    }
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FormulaMetricCard("Empirical", toSubscriptFormula(result.empiricalFormula), Modifier.weight(1f))
                FormulaMetricCard("Empirical mass", "${toolNumber(result.empiricalMass, 3)} g/mol", Modifier.weight(1f))
            }
            result.molecularFormula?.let { formula ->
                FormulaMetricCard(
                    label = "Molecular formula",
                    value = toSubscriptFormula(formula) + (result.molecularMultiplier?.let { "  x$it" } ?: ""),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            result.warning?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            if (result.rows.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.12f))
                Text(stringResource(R.string.ui_mole_ratio),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.45f)
                )
                result.rows.forEach { row ->
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(row.element, modifier = Modifier.weight(0.7f), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text(toolNumber(row.moles, 4), modifier = Modifier.weight(1f), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                        Text(toolNumber(row.ratio, 3), modifier = Modifier.weight(1f), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                        Text(row.wholeNumber.toString(), modifier = Modifier.weight(0.7f), textAlign = TextAlign.End, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun PrecipitationResultCard(result: PrecipitationPredictionResult) {
    if (result.error != null) {
        ToolErrorCard(result.error)
        return
    }
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (result.precipitates.isEmpty()) Color(0xFF22C55E).copy(0.12f) else MaterialTheme.colorScheme.primary.copy(0.12f),
                border = BorderStroke(1.dp, if (result.precipitates.isEmpty()) Color(0xFF22C55E).copy(0.32f) else MaterialTheme.colorScheme.primary.copy(0.32f))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.ui_result), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(0.62f))
                    Text(result.summary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                }
            }

            FormulaDisplayLine("Molecular equation", result.molecularEquation)
            if (result.netIonicEquation.isNotBlank()) FormulaDisplayLine("Net ionic equation", result.netIonicEquation)
            if (result.completeIonicEquation.isNotBlank()) FormulaDisplayLine("Complete ionic equation", result.completeIonicEquation)

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.12f))
            Text(stringResource(R.string.ui_product_check),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(0.45f)
            )
            result.products.forEach { product ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(0.34f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(0.12f))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(toSubscriptFormula(product.formula), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            SolubilityStatePill(product.solubility.state)
                        }
                        Text(product.solubility.rule, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(0.66f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolTitleRow(title: String, onInfo: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        IconButton(onClick = onInfo, modifier = Modifier.size(24.dp)) {
            Icon(Icons.Default.Info, contentDescription = stringResource(R.string.ui_info), tint = MaterialTheme.colorScheme.onSurface.copy(0.35f), modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun ToolErrorCard(message: String) {
    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(0.42f))) {
        Text(message, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun FormulaMetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primary.copy(0.1f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(0.26f)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(0.54f))
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun FormulaDisplayLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(0.48f))
        Text(toChemicalExpressionDisplay(value), style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface, lineHeight = 20.sp)
    }
}

@Composable
private fun SolubilityStatePill(state: SolubilityState) {
    val (label, color) = when (state) {
        SolubilityState.SOLUBLE -> "aq" to Color(0xFF22C55E)
        SolubilityState.INSOLUBLE -> "solid" to MaterialTheme.colorScheme.primary
        SolubilityState.SLIGHTLY_SOLUBLE -> "slightly" to Color(0xFFF59E0B)
        SolubilityState.UNKNOWN -> "unknown" to MaterialTheme.colorScheme.outline
    }
    Surface(shape = RoundedCornerShape(999.dp), color = color.copy(0.12f), border = BorderStroke(1.dp, color.copy(0.32f))) {
        Text(label, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun FormulaExplanationCard(
    latexFormula: String,
    explanation: String
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.4f))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.ui_formula), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
            Text(toChemicalExpressionDisplay(latexFormula), style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace)
            Text(explanation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(0.7f))
        }
    }
}

private fun textFieldAtEnd(text: String): TextFieldValue =
    TextFieldValue(text, TextRange(text.length))

private fun toolNumber(value: Double, decimals: Int = 4): String =
    "%.${decimals}f".format(value)

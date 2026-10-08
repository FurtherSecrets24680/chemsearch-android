package com.furthersecrets.chemsearch.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.bold.Info
import com.adamglin.phosphoricons.bold.Warning
import com.adamglin.phosphoricons.bold.X
import com.furthersecrets.chemsearch.R

/**
 * Visual tone for dialogs, driving the header icon chip and (for confirmations)
 * the action button color. Colors match the app's existing accents: green and
 * amber as used on the Reaction Predictor confidence card.
 */
enum class ChemDialogTone { NEUTRAL, INFO, SUCCESS, WARNING, DANGER }

@Composable
private fun chemDialogToneColors(tone: ChemDialogTone): Pair<Color, Color> = when (tone) {
    ChemDialogTone.NEUTRAL ->
        MaterialTheme.colorScheme.onSurface.copy(0.06f) to MaterialTheme.colorScheme.onSurface.copy(0.7f)
    ChemDialogTone.INFO ->
        MaterialTheme.colorScheme.primary.copy(0.14f) to MaterialTheme.colorScheme.primary
    ChemDialogTone.SUCCESS ->
        Color(0xFF22C55E).copy(0.14f) to Color(0xFF22C55E)
    ChemDialogTone.WARNING ->
        Color(0xFFF59E0B).copy(0.14f) to Color(0xFFF59E0B)
    ChemDialogTone.DANGER ->
        MaterialTheme.colorScheme.error.copy(0.14f) to MaterialTheme.colorScheme.error
}

/**
 * The app's standard dialog shell: a rounded bordered card (not the platform
 * AlertDialog look) with a tinted icon chip, bold title, optional close button,
 * a scroll-friendly body slot and an optional trailing actions row. All
 * user-facing dialogs (FAQ, legal, help, confirmations) render through this
 * for a consistent, elevated feel on every screen and locale.
 */
@Composable
fun ChemDialog(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    tone: ChemDialogTone = ChemDialogTone.INFO,
    icon: ImageVector? = null,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    showClose: Boolean = true,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val (chipBg, chipFg) = chemDialogToneColors(tone)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(0.14f)),
            shadowElevation = 10.dp,
            modifier = modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .widthIn(max = 520.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    when {
                        leading != null -> leading()
                        icon != null -> {
                            Surface(
                                shape = RoundedCornerShape(13.dp),
                                color = chipBg,
                                modifier = Modifier.size(42.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(icon, null, tint = chipFg, modifier = Modifier.size(22.dp))
                                }
                            }
                        }
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (subtitle != null) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(0.55f)
                            )
                        }
                    }
                    if (showClose) {
                        IconButton(onClick = onDismiss, modifier = Modifier.size(34.dp)) {
                            Icon(
                                PhosphorIcons.Bold.X,
                                contentDescription = stringResource(R.string.ui_close),
                                tint = MaterialTheme.colorScheme.onSurface.copy(0.45f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                content()
                if (actions != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                        content = actions
                    )
                }
            }
        }
    }
}

/**
 * Term/explanation dialog (FAQ, tool help, glossaries): each entry renders as
 * a bold primary-colored term over a muted explanation, scrollable so long
 * lists stay compact. Ends with a single "Got it" action.
 */
@Composable
fun ChemInfoDialog(
    title: String,
    entries: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    ChemDialog(
        title = title,
        onDismiss = onDismiss,
        tone = ChemDialogTone.INFO,
        icon = PhosphorIcons.Bold.Info,
        modifier = modifier,
        actions = {
            TextButton(onClick = onDismiss) {
                Text(
                    stringResource(R.string.ui_got_it),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    ) {
        Column(
            modifier = Modifier
                .heightIn(max = 430.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            entries.forEach { (term, explanation) ->
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = term,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = explanation,
                        style = MaterialTheme.typography.bodySmall,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.8f)
                    )
                }
            }
        }
    }
}

/**
 * Confirmation dialog with a tone-driven action button: destructive tone gets
 * the error color, so "remove/clear/wipe" choices always read as cautionary.
 */
@Composable
fun ChemConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    tone: ChemDialogTone = ChemDialogTone.DANGER,
    icon: ImageVector? = null,
    dismissLabel: String? = null
) {
    ChemDialog(
        title = title,
        onDismiss = onDismiss,
        tone = tone,
        icon = icon ?: PhosphorIcons.Bold.Warning,
        showClose = false,
        modifier = modifier,
        actions = {
            TextButton(onClick = onDismiss) {
                Text(dismissLabel ?: stringResource(R.string.ui_cancel))
            }
            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(12.dp),
                colors = if (tone == ChemDialogTone.DANGER) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                } else {
                    ButtonDefaults.buttonColors()
                }
            ) {
                Text(confirmLabel, fontWeight = FontWeight.SemiBold)
            }
        }
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(0.75f)
        )
    }
}

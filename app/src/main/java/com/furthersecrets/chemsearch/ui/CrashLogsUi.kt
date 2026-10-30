package com.furthersecrets.chemsearch.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.furthersecrets.chemsearch.R
import com.furthersecrets.chemsearch.data.CrashLogEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun CrashHistoryDialog(
    reports: List<CrashLogEntry>,
    totalBytes: Long,
    onOpen: (CrashLogEntry) -> Unit,
    onCopyAll: () -> Unit,
    onDeleteAll: () -> Unit,
    onDismiss: () -> Unit
) {
    var showDeleteAllConfirm by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy · HH:mm:ss", Locale.getDefault()) }

    if (showDeleteAllConfirm) {
        ChemConfirmDialog(
            title = stringResource(R.string.ui_crash_delete_all_title),
            message = stringResource(R.string.ui_crash_delete_all_body),
            confirmLabel = stringResource(R.string.ui_delete_all),
            onConfirm = {
                showDeleteAllConfirm = false
                onDeleteAll()
            },
            onDismiss = { showDeleteAllConfirm = false }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.BugReport,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column {
                    Text(
                        stringResource(R.string.ui_crash_history),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (reports.isEmpty()) {
                            stringResource(R.string.ui_crash_no_reports)
                        } else {
                            pluralStringResource(
                                R.plurals.ui_crash_reports_count,
                                reports.size,
                                reports.size
                            ) + "  •  " + formatCrashReportBytes(totalBytes)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.6f)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    stringResource(R.string.ui_crash_history_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.65f)
                )
                if (reports.isEmpty()) {
                    Text(
                        stringResource(R.string.ui_crash_empty_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                    )
                } else {
                    reports.forEach { report ->
                        CrashReportRow(report, dateFormat, onOpen)
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onCopyAll, enabled = reports.isNotEmpty()) {
                    Text(stringResource(R.string.ui_crash_copy_all))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.ui_close))
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = { showDeleteAllConfirm = true },
                enabled = reports.isNotEmpty()
            ) {
                Text(
                    stringResource(R.string.ui_delete_all),
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
private fun CrashReportRow(
    report: CrashLogEntry,
    dateFormat: SimpleDateFormat,
    onOpen: (CrashLogEntry) -> Unit
) {
    Card(
        onClick = { onOpen(report) },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.45f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(0.25f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 2.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = report.exceptionType.substringAfterLast('.'),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (report.message.isNotBlank()) {
                    Text(
                        text = report.message,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.7f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = "${dateFormat.format(Date(report.capturedAt))}  •  " +
                        "thread ${report.threadName}  •  ${formatCrashReportBytes(report.sizeBytes)}",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                )
            }
            Text(
                stringResource(R.string.ui_view),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
internal fun CrashLogDetailDialog(
    report: CrashLogEntry,
    reportText: String,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        ChemConfirmDialog(
            title = stringResource(R.string.ui_crash_delete_one_title),
            message = stringResource(R.string.ui_crash_delete_one_body),
            confirmLabel = stringResource(R.string.ui_delete),
            onConfirm = {
                showDeleteConfirm = false
                onDelete()
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(R.string.ui_crash_report_detail),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    report.exceptionType,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(0.35f)
            ) {
                SelectionContainer {
                    Text(
                        text = reportText.ifBlank { stringResource(R.string.ui_crash_read_failed) },
                        modifier = Modifier
                            .heightIn(max = 460.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(10.dp),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            lineHeight = 14.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface.copy(0.9f)
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { copyToClipboard(context, reportText) }) {
                    Text(stringResource(R.string.ui_copy))
                }
                TextButton(onClick = { shareCrashReport(context, report) }) {
                    Text(stringResource(R.string.ui_crash_share))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.ui_close))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = { showDeleteConfirm = true }) {
                Text(
                    stringResource(R.string.ui_delete),
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

internal fun copyToClipboard(
    context: Context,
    text: String,
    toastRes: Int = R.string.ui_crash_copied
) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    runCatching {
        clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.ui_clipboard_crash_report), text))
    }.onSuccess {
        Toast.makeText(context, context.getString(toastRes), Toast.LENGTH_SHORT).show()
    }.onFailure {
        Toast.makeText(context, context.getString(R.string.ui_crash_copy_failed), Toast.LENGTH_LONG).show()
    }
}

private fun shareCrashReport(context: Context, report: CrashLogEntry) {
    val shared = runCatching {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            report.file
        )
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.ui_crash_share_subject))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }.getOrElse {
        Toast.makeText(context, context.getString(R.string.ui_crash_share_failed), Toast.LENGTH_SHORT).show()
        return
    }
    runCatching {
        context.startActivity(
            Intent.createChooser(shared, context.getString(R.string.ui_crash_share_chooser))
        )
    }.onFailure {
        Toast.makeText(context, context.getString(R.string.ui_crash_share_failed), Toast.LENGTH_SHORT).show()
    }
}

internal fun formatCrashReportBytes(bytes: Long): String = when {
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
    else -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
}

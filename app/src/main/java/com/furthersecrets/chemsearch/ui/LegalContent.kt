package com.furthersecrets.chemsearch.ui

import com.furthersecrets.chemsearch.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

enum class LegalDocumentType {
    PRIVACY,
    TERMS,
    SAFETY
}

data class LegalDocument(
    val type: LegalDocumentType,
    val titleRes: Int,
    val summaryRes: Int,
    val sections: List<Pair<Int, Int>>
)

val legalDocuments = listOf(
    LegalDocument(
        type = LegalDocumentType.PRIVACY,
        titleRes = R.string.ui_privacy_policy,
        summaryRes = R.string.ui_legal_privacy_summary,
        sections = listOf(
            R.string.ui_legal_data_on_device_title to R.string.ui_legal_data_on_device,
            R.string.ui_legal_external_services_title to R.string.ui_legal_external_services,
            R.string.ui_legal_ai_descriptions_title to R.string.ui_legal_ai_descriptions,
            R.string.ui_legal_no_account_system_title to R.string.ui_legal_no_account_system,
            R.string.ui_legal_your_control_title to R.string.ui_legal_your_control
        )
    ),
    LegalDocument(
        type = LegalDocumentType.TERMS,
        titleRes = R.string.ui_terms_of_service,
        summaryRes = R.string.ui_legal_terms_summary,
        sections = listOf(
            R.string.ui_legal_use to R.string.ui_legal_use_body,
            R.string.ui_legal_third_party_sources to R.string.ui_legal_third_party_sources_body,
            R.string.ui_legal_no_guarantee to R.string.ui_legal_no_guarantee_body,
            R.string.ui_legal_user_content to R.string.ui_legal_user_content_body,
            R.string.ui_legal_changes to R.string.ui_legal_changes_body
        )
    ),
    LegalDocument(
        type = LegalDocumentType.SAFETY,
        titleRes = R.string.ui_safety_disclaimer,
        summaryRes = R.string.ui_legal_safety_summary,
        sections = listOf(
            R.string.ui_legal_quick_checks_only to R.string.ui_legal_quick_checks_only_body,
            R.string.ui_legal_safety_data_limits to R.string.ui_legal_safety_data_limits_body,
            R.string.ui_legal_ai_limits to R.string.ui_legal_ai_limits_body,
            R.string.ui_legal_emergency_use to R.string.ui_legal_emergency_use_body,
            R.string.ui_legal_generated_structures to R.string.ui_legal_generated_structures_body
        )
    )
)

fun legalDocumentIcon(type: LegalDocumentType): ImageVector = when (type) {
    LegalDocumentType.PRIVACY -> Icons.Default.Key
    LegalDocumentType.TERMS -> Icons.Default.Description
    LegalDocumentType.SAFETY -> Icons.Default.HealthAndSafety
}

@Composable
fun LegalDocumentDialog(
    document: LegalDocument,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(document.titleRes), fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 430.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    stringResource(document.summaryRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.7f)
                )
                document.sections.forEach { (titleRes, bodyRes) ->
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            stringResource(titleRes),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            stringResource(bodyRes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.72f)
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(stringResource(R.string.ui_this_in_app_notice_is_a_practical_summary),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.46f)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ui_close))
            }
        },
        shape = RoundedCornerShape(22.dp),
        containerColor = MaterialTheme.colorScheme.surface
    )
}

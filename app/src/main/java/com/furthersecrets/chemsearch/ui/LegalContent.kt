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
    val title: String,
    val summary: String,
    val sections: List<Pair<String, String>>
)

val legalDocuments = listOf(
    LegalDocument(
        type = LegalDocumentType.PRIVACY,
        title = "Privacy Policy",
        summary = "How ChemSearch handles searches, AI providers, saved data, and updates.",
        sections = listOf(
            "Data on your device" to "Favorites, recent searches, downloads, cache data, settings, API keys, and imported or exported library files are stored on your device unless you choose to share or export them.",
            "External services" to "ChemSearch can contact PubChem, Wikipedia, NCI/CADD, GitHub, and optional AI providers to fetch compound data, descriptions, structures, updates, and model responses.",
            "AI descriptions" to "If you use an AI provider, compound names, identifiers, formulas, available source data, and your prompt context may be sent to that provider. Provider API keys are saved locally on your device.",
            "No account system" to "ChemSearch does not include a ChemSearch account, profile, or built-in analytics account system.",
            "Your control" to "You can clear cache, delete downloads, remove saved items, change providers, remove API keys, and export or import library data from the app settings."
        )
    ),
    LegalDocument(
        type = LegalDocumentType.TERMS,
        title = "Terms of Service",
        summary = "Basic rules for using ChemSearch and its third-party data sources.",
        sections = listOf(
            "Use" to "ChemSearch is a chemistry lookup and study app. Use it responsibly and follow the rules of your school, workplace, lab, and local law.",
            "Third-party sources" to "Compound data, safety information, descriptions, structures, updates, and AI responses may come from external services. Those services may have their own terms and limits.",
            "No guarantee" to "ChemSearch is provided as-is. Results can be incomplete, unavailable, outdated, generated, or wrong.",
            "User content" to "You are responsible for any searches, structure drawings, API keys, imported files, exported files, or prompts you use with the app.",
            "Changes" to "Features, sources, update behavior, and provider support may change between app versions."
        )
    ),
    LegalDocument(
        type = LegalDocumentType.SAFETY,
        title = "Safety Disclaimer",
        summary = "ChemSearch is for study and quick checks, not official safety guidance.",
        sections = listOf(
            "Quick checks only" to "Chemical and safety information in ChemSearch is for learning, review, and quick checks. It is not a replacement for SDS documents, lab rules, labels, regulations, teacher instructions, or professional judgment.",
            "Safety data limits" to "GHS and hazard details may be missing, source-dependent, or incomplete. Always verify safety information from official documents before handling chemicals.",
            "AI limits" to "AI descriptions may sound confident while being incomplete or incorrect. Do not use AI text as your only source for safety, medical, legal, or lab decisions.",
            "Emergency use" to "Do not rely on ChemSearch during emergencies. Follow your lab, institution, poison control, emergency service, or workplace procedures.",
            "Generated structures" to "Fallback or generated 3D structures are estimates and should be treated as visual aids, not verified experimental structures."
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
            Text(document.title, fontWeight = FontWeight.Bold)
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
                    document.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.7f)
                )
                document.sections.forEach { (title, body) ->
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            title,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            body,
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

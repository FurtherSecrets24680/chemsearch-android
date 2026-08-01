package com.furthersecrets.chemsearch.ui

import com.furthersecrets.chemsearch.R
import org.junit.Assert.assertTrue
import org.junit.Test

class AboutCreditsCatalogTest {
    @Test
    fun aboutCreditsPointToSourcePages() {
        val credits = aboutDataCredits + aboutAiProviderCredits + aboutTechnologyCredits

        assertTrue(aboutAppLinks.all { it.url.startsWith("https://") })
        assertTrue(credits.all { it.url.startsWith("https://") })
        assertTrue(aboutDataCredits.any { it.titleRes == R.string.ui_source_pubchem_pug_rest && it.url.contains("pug-rest") })
        assertTrue(aboutDataCredits.any { it.titleRes == R.string.ui_source_pubchem_pug_view && it.url.contains("pug-view") })
        assertTrue(aboutDataCredits.any { it.titleRes == R.string.ui_source_nci_cadd_resolver && it.url.contains("cactus.nci.nih.gov") })
        assertTrue(aboutTechnologyCredits.any { it.titleRes == R.string.ui_credit_phosphor_icons && it.url.contains("phosphor") })
    }

    @Test
    fun legalDocumentsCoverPrivacyTermsAndSafety() {
        assertTrue(legalDocuments.any { it.type == LegalDocumentType.PRIVACY })
        assertTrue(legalDocuments.any { it.type == LegalDocumentType.TERMS })
        assertTrue(legalDocuments.any { it.type == LegalDocumentType.SAFETY })
        assertTrue(legalDocuments.all { it.sections.isNotEmpty() })
    }
}

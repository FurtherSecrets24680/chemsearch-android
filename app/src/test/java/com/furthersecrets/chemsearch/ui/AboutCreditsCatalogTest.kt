package com.furthersecrets.chemsearch.ui

import org.junit.Assert.assertTrue
import org.junit.Test

class AboutCreditsCatalogTest {
    @Test
    fun aboutCreditsPointToSourcePages() {
        val credits = aboutDataCredits + aboutAiProviderCredits + aboutTechnologyCredits

        assertTrue(aboutAppLinks.all { it.url.startsWith("https://") })
        assertTrue(credits.all { it.url.startsWith("https://") })
        assertTrue(aboutDataCredits.any { it.title == "PubChem PUG REST" && it.url.contains("pug-rest") })
        assertTrue(aboutDataCredits.any { it.title == "PubChem PUG View" && it.url.contains("pug-view") })
        assertTrue(aboutDataCredits.any { it.title == "NCI/CADD Resolver" && it.url.contains("cactus.nci.nih.gov") })
        assertTrue(aboutTechnologyCredits.any { it.title == "Phosphor Icons" && it.url.contains("phosphor") })
    }

    @Test
    fun legalDocumentsCoverPrivacyTermsAndSafety() {
        assertTrue(legalDocuments.any { it.type == LegalDocumentType.PRIVACY })
        assertTrue(legalDocuments.any { it.type == LegalDocumentType.TERMS })
        assertTrue(legalDocuments.any { it.type == LegalDocumentType.SAFETY })
        assertTrue(legalDocuments.all { it.sections.isNotEmpty() })
    }
}

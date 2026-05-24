package com.furthersecrets.chemsearch.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkDiagnosticsTest {
    @Test
    fun diagnosticsIncludeCoreDataSourcesAndFallback3d() {
        val services = buildNetworkDiagnosticProbeSpecs().map { it.service }.toSet()

        assertTrue("PubChem search probe missing", "PubChem Search" in services)
        assertTrue("PubChem autocomplete probe missing", "PubChem Autocomplete" in services)
        assertTrue("PubChem GHS probe missing", "PubChem GHS Safety" in services)
        assertTrue("PubChem 2D probe missing", "PubChem 2D Structure" in services)
        assertTrue("PubChem 3D probe missing", "PubChem 3D SDF" in services)
        assertTrue("NCI/CADD fallback probe missing", "NCI/CADD Fallback SDF" in services)
        assertTrue("Wikipedia probe missing", "Wikipedia" in services)
        assertTrue("GitHub releases probe missing", "GitHub Releases" in services)
    }

    @Test
    fun diagnosticsIncludeEveryAiProvider() {
        val providerProbes = buildNetworkDiagnosticProbeSpecs()
            .mapNotNull { it.aiProvider }
            .toSet()

        assertEquals(AiProvider.entries.toSet(), providerProbes)
    }

    @Test
    fun copyableEndpointListIncludesEveryAiProvider() {
        val endpointLabels = buildDebugApiEndpointLines()

        AiProvider.entries.forEach { provider ->
            assertTrue(
                "${provider.displayName} endpoint missing",
                endpointLabels.any { it.startsWith("${provider.shortName}: ") }
            )
        }
    }
}

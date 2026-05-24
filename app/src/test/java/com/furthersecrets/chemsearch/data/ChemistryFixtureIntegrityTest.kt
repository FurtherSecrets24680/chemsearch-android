package com.furthersecrets.chemsearch.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertTrue
import org.junit.Test

class ChemistryFixtureIntegrityTest {
    private val gson = Gson()

    @Test
    fun allChemistryFixturesNameTheirSourceAndMeetMinimumCount() {
        val fixtureFiles = listOf(
            "chemistry-fixtures/formula-parsing.json",
            "chemistry-fixtures/molar-masses.json",
            "chemistry-fixtures/oxidation-states.json",
            "chemistry-fixtures/ph-poh.json",
            "chemistry-fixtures/reaction-balancing.json"
        )

        val entries = fixtureFiles.flatMap { file ->
            val json = requireNotNull(javaClass.classLoader?.getResource(file)) { "Missing fixture $file" }
                .readText()
            val type = object : TypeToken<List<Map<String, Any?>>>() {}.type
            gson.fromJson<List<Map<String, Any?>>>(json, type)
        }

        assertTrue("Expected at least 50 chemistry fixtures", entries.size >= 50)
        entries.forEach { entry ->
            assertTrue("Fixture source must be named: $entry", entry["source"].toString().isNotBlank())
        }
    }
}

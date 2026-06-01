package com.furthersecrets.chemsearch

import kotlin.random.Random
import org.junit.Assert.assertTrue
import org.junit.Test

class RandomCompoundTest {
    @Test
    fun randomPubChemCidStaysInsideConfiguredCompoundCount() {
        repeat(200) {
            val cid = randomPubChemCid(Random(it), upperBound = 123_880_397L)

            assertTrue(cid in 1L..123_880_397L)
        }
    }
}

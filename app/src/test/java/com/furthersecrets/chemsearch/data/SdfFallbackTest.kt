package com.furthersecrets.chemsearch.data

import org.junit.Assert.assertTrue
import org.junit.Test

class SdfFallbackTest {
    @Test
    fun usableSdfAcceptsV3000Records() {
        val sdf = """
            Water
              ChemSearch

              0  0  0     0  0            999 V3000
            M  V30 BEGIN CTAB
            M  V30 COUNTS 3 2 0 0 0
            M  V30 BEGIN ATOM
            M  V30 1 O 0.0000 0.0000 0.0000 0
            M  V30 2 H 0.9572 0.0000 0.0000 0
            M  V30 3 H -0.2390 0.9270 0.0000 0
            M  V30 END ATOM
            M  V30 BEGIN BOND
            M  V30 1 1 1 2
            M  V30 2 1 1 3
            M  V30 END BOND
            M  V30 END CTAB
            M  END
        """.trimIndent()

        assertTrue(isUsableSdf(sdf))
    }
}

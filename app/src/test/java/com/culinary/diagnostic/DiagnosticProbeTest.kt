package com.culinary.diagnostic

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Temporary diagnostic probe.
 *
 * This intentionally tiny test verifies that the app module's JVM unit-test
 * source set is compiled and executed by the repository CI pipeline.
 */
class DiagnosticProbeTest {
    @Test
    fun diagnosticProbeRuns() {
        assertEquals("MISEBASE", "MISE" + "BASE")
    }
}

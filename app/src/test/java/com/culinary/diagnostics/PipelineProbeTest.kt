package com.culinary.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Test

class PipelineProbeTest {
    @Test
    fun readyStateUsesExpectedLabel() {
        assertEquals("Ready", diagnosticBuildLabel(isReady = true))
    }

    @Test
    fun unavailableStateUsesExpectedLabel() {
        assertEquals("Unavailable", diagnosticBuildLabel(isReady = false))
    }
}

internal fun diagnosticBuildLabel(isReady: Boolean): String =
    if (isReady) "Ready" else "Unavailable"

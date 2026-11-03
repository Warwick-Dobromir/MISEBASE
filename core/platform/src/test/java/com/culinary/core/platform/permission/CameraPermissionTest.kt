package com.culinary.core.platform.permission

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CameraPermissionTest {
    @Test
    fun `granted wins over everything`() {
        val state =
            CameraPermissionEvaluator.evaluate(
                granted = true,
                shouldShowRationale = false,
                requestedBefore = true,
            )

        assertThat(state).isEqualTo(CameraPermissionState.GRANTED)
    }

    @Test
    fun `first request can ask even without rationale`() {
        val state =
            CameraPermissionEvaluator.evaluate(
                granted = false,
                shouldShowRationale = false,
                requestedBefore = false,
            )

        assertThat(state).isEqualTo(CameraPermissionState.DENIED_CAN_ASK)
    }

    @Test
    fun `denied with rationale can ask again`() {
        val state =
            CameraPermissionEvaluator.evaluate(
                granted = false,
                shouldShowRationale = true,
                requestedBefore = true,
            )

        assertThat(state).isEqualTo(CameraPermissionState.DENIED_CAN_ASK)
    }

    @Test
    fun `denied after a previous request without rationale is permanent`() {
        val state =
            CameraPermissionEvaluator.evaluate(
                granted = false,
                shouldShowRationale = false,
                requestedBefore = true,
            )

        assertThat(state).isEqualTo(CameraPermissionState.DENIED_PERMANENTLY)
    }
}

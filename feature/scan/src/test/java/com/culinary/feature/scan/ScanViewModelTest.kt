package com.culinary.feature.scan

import com.culinary.core.common.Clock
import com.culinary.core.domain.barcode.ScanCapture
import com.culinary.core.domain.barcode.ScanFrameGate
import com.culinary.core.domain.barcode.ScanSource
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant

/**
 * 扫码页 ViewModel：权限状态机、一次识别即停、手电筒回滚、手工输入兜底。
 *
 * ViewModel 是同步的（无协程），因此用固定时钟即可完整覆盖去抖窗口。
 */
class ScanViewModelTest {
    private var nowMillis: Long = 1_000L
    private val clock = Clock { Instant.ofEpochMilli(nowMillis) }

    private fun viewModel(windowMillis: Long = 2_000L): ScanViewModel =
        ScanViewModel(gate = ScanFrameGate(windowMillis), clock = clock)

    private fun grantedViewModel(windowMillis: Long = 2_000L): ScanViewModel =
        viewModel(windowMillis).apply {
            onCameraAvailability(true)
            onPermissionChecked(granted = true)
            onResumeScanning()
        }

    private fun cameraValue(value: String): ScanCapture = ScanCapture(rawValue = value, format = "EAN13")

    @Test
    fun `starts in checking state`() {
        val state = viewModel().uiState.value

        assertThat(state.cameraAccess).isEqualTo(CameraAccessState.CHECKING)
        assertThat(state.scanning).isFalse()
    }

    @Test
    fun `granted permission starts scanning`() {
        val state = grantedViewModel().uiState.value

        assertThat(state.cameraAccess).isEqualTo(CameraAccessState.GRANTED)
        assertThat(state.scanning).isTrue()
        assertThat(state.showCamera).isTrue()
    }

    @Test
    fun `denied permission keeps the explanation without scanning`() {
        val viewModel = viewModel()
        viewModel.onCameraAvailability(true)
        viewModel.onPermissionChecked(granted = false)
        viewModel.onPermissionResult(granted = false)

        assertThat(viewModel.uiState.value.cameraAccess).isEqualTo(CameraAccessState.DENIED)
        assertThat(viewModel.uiState.value.scanning).isFalse()
        assertThat(viewModel.uiState.value.manualEntry).isFalse()
    }

    @Test
    fun `missing camera forces manual entry`() {
        val viewModel = viewModel()

        viewModel.onCameraAvailability(false)
        viewModel.onPermissionChecked(granted = false)

        assertThat(viewModel.uiState.value.cameraAccess).isEqualTo(CameraAccessState.UNAVAILABLE)
        assertThat(viewModel.uiState.value.manualEntry).isTrue()
        assertThat(viewModel.uiState.value.showCamera).isFalse()
    }

    @Test
    fun `accepted frame pauses scanning and emits one capture`() {
        val viewModel = grantedViewModel()

        viewModel.onCameraFrame(cameraValue("6901234567892"))

        val state = viewModel.uiState.value
        assertThat(state.scanning).isFalse()
        assertThat(state.pendingCapture?.normalizedValue).isEqualTo("6901234567892")
        assertThat(state.pendingCapture?.source).isEqualTo(ScanSource.CAMERA)
    }

    @Test
    fun `consecutive frames of the same code do not retrigger`() {
        val viewModel = grantedViewModel()

        viewModel.onCameraFrame(cameraValue("6901234567892"))
        viewModel.onCaptureConsumed()
        repeat(5) { viewModel.onCameraFrame(cameraValue("6901234567892")) }

        assertThat(viewModel.uiState.value.pendingCapture).isNull()
    }

    @Test
    fun `frames are ignored while manual entry is open`() {
        val viewModel = grantedViewModel()
        viewModel.onOpenManualEntry()

        viewModel.onCameraFrame(cameraValue("6901234567892"))

        assertThat(viewModel.uiState.value.pendingCapture).isNull()
        assertThat(viewModel.uiState.value.scanning).isFalse()
    }

    @Test
    fun `resume after confirm allows a different code immediately`() {
        val viewModel = grantedViewModel()
        viewModel.onCameraFrame(cameraValue("code-a"))
        viewModel.onCaptureConsumed()

        viewModel.onResumeScanning()
        nowMillis += 100
        viewModel.onCameraFrame(cameraValue("code-b"))

        val capture = viewModel.uiState.value.pendingCapture
        assertThat(capture?.normalizedValue).isEqualTo("code-b")
    }

    @Test
    fun `resume inside the debounce window still rejects the same code`() {
        val viewModel = grantedViewModel()
        viewModel.onCameraFrame(cameraValue("code-a"))
        viewModel.onCaptureConsumed()

        viewModel.onResumeScanning()
        nowMillis += 1_000
        viewModel.onCameraFrame(cameraValue("code-a"))

        assertThat(viewModel.uiState.value.pendingCapture).isNull()
    }

    @Test
    fun `resume after the debounce window accepts the same code again`() {
        val viewModel = grantedViewModel()
        viewModel.onCameraFrame(cameraValue("code-a"))
        viewModel.onCaptureConsumed()

        viewModel.onResumeScanning()
        nowMillis += 2_000
        viewModel.onCameraFrame(cameraValue("code-a"))

        val capture = viewModel.uiState.value.pendingCapture
        assertThat(capture?.normalizedValue).isEqualTo("code-a")
    }

    @Test
    fun `manual submission emits a manual capture`() {
        val viewModel = viewModel()
        viewModel.onCameraAvailability(false)
        viewModel.onPermissionChecked(granted = false)
        viewModel.onManualCodeChange("  6901234567892 ")

        viewModel.onSubmitManualCode()

        val capture = viewModel.uiState.value.pendingCapture
        assertThat(capture?.rawValue).isEqualTo("6901234567892")
        assertThat(capture?.source).isEqualTo(ScanSource.MANUAL)
        assertThat(capture?.format).isNull()
    }

    @Test
    fun `manual submission rejects blank and overlong values`() {
        val viewModel = viewModel()

        viewModel.onManualCodeChange("   ")
        viewModel.onSubmitManualCode()
        assertThat(viewModel.uiState.value.pendingCapture).isNull()
        assertThat(viewModel.uiState.value.manualError).isNotNull()

        viewModel.onManualCodeChange("a".repeat(ScanCapture.MAX_VALUE_LENGTH + 1))
        viewModel.onSubmitManualCode()
        assertThat(viewModel.uiState.value.pendingCapture).isNull()
        assertThat(viewModel.uiState.value.manualError).isNotNull()
    }

    @Test
    fun `torch toggles and rolls back when the device has no flash`() {
        val viewModel = grantedViewModel()

        viewModel.onToggleTorch()
        assertThat(viewModel.uiState.value.torchOn).isTrue()

        viewModel.onTorchResult(success = false)

        assertThat(viewModel.uiState.value.torchOn).isFalse()
        assertThat(viewModel.uiState.value.torchNotice).isNotNull()
    }

    @Test
    fun `torch is not toggled without camera access`() {
        val viewModel = viewModel()
        viewModel.onCameraAvailability(true)
        viewModel.onPermissionChecked(granted = false)

        viewModel.onToggleTorch()

        assertThat(viewModel.uiState.value.torchOn).isFalse()
    }

    @Test
    fun `capture signal is cleared once consumed`() {
        val viewModel = grantedViewModel()
        viewModel.onCameraFrame(cameraValue("code-a"))

        viewModel.onCaptureConsumed()

        assertThat(viewModel.uiState.value.pendingCapture).isNull()
    }

    @Test
    fun `camera error is surfaced and dismissible`() {
        val viewModel = grantedViewModel()

        viewModel.onError("识别失败，请重试")
        assertThat(viewModel.uiState.value.error).isNotNull()

        viewModel.onDismissError()
        assertThat(viewModel.uiState.value.error).isNull()
    }
}

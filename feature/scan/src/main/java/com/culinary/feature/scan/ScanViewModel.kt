package com.culinary.feature.scan

import androidx.lifecycle.ViewModel
import com.culinary.core.common.Clock
import com.culinary.core.domain.barcode.ScanCapture
import com.culinary.core.domain.barcode.ScanFrameGate
import com.culinary.core.domain.barcode.ScanSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * 扫码页 ViewModel：权限状态机、手电筒、手工输入兜底与"识别一次即停"。
 *
 * 去抖与一次性消费交给 [ScanFrameGate]（纯逻辑，见 core:domain）：相机帧先过闸门，
 * 只有被接受的码才产生 [ScanUiState.pendingCapture] 并暂停分析。
 * 本类不访问数据库，也不创建食材/库存——识别结果交给确认页与业务模块。
 */
@HiltViewModel
class ScanViewModel
    @Inject
    constructor(
        private val gate: ScanFrameGate,
        private val clock: Clock,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(ScanUiState())
        val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

        /** 设备是否有相机；无相机时直接进入手工输入。 */
        fun onCameraAvailability(available: Boolean) {
            _uiState.update { state ->
                state.copy(
                    hasCamera = available,
                    manualEntry = if (available) state.manualEntry else true,
                    cameraAccess =
                        when {
                            !available -> CameraAccessState.UNAVAILABLE
                            state.cameraAccess == CameraAccessState.UNAVAILABLE -> CameraAccessState.NOT_REQUESTED
                            else -> state.cameraAccess
                        },
                    scanning = available && state.cameraAccess == CameraAccessState.GRANTED,
                )
            }
        }

        /** 进入页面时检查既有授权：已授权直接开相机，未授权展示权限说明页。 */
        fun onPermissionChecked(granted: Boolean) {
            _uiState.update { state ->
                if (granted) {
                    state.copy(
                        cameraAccess = CameraAccessState.GRANTED,
                        scanning = state.hasCamera,
                        manualEntry = false,
                        error = null,
                    )
                } else {
                    state.copy(
                        cameraAccess =
                            if (state.hasCamera) {
                                CameraAccessState.NOT_REQUESTED
                            } else {
                                CameraAccessState.UNAVAILABLE
                            },
                        scanning = false,
                    )
                }
            }
        }

        /** 权限对话框结果；拒绝后保留说明页与手工输入兜底，不再反复弹窗。 */
        fun onPermissionResult(granted: Boolean) {
            if (granted) {
                gate.reset()
                _uiState.update { state ->
                    state.copy(
                        cameraAccess = CameraAccessState.GRANTED,
                        scanning = state.hasCamera,
                        manualEntry = false,
                        error = null,
                    )
                }
            } else {
                _uiState.update { state ->
                    state.copy(
                        cameraAccess = CameraAccessState.DENIED,
                        scanning = false,
                        error = null,
                    )
                }
            }
        }

        /** 相机帧回调：先过闸门，被接受才算一次识别。 */
        fun onCameraFrame(capture: ScanCapture) {
            val state = _uiState.value
            if (!state.showCamera || !state.scanning) return
            if (!gate.offer(capture.normalizedValue, clock.now().toEpochMilli())) return
            _uiState.update {
                it.copy(
                    scanning = false,
                    torchOn = false,
                    status = null,
                    pendingCapture = capture.copy(source = ScanSource.CAMERA),
                )
            }
        }

        /** 从确认页返回或点击"重新扫描"：解除一次性消费，重新开始分析。 */
        fun onResumeScanning() {
            gate.resume()
            _uiState.update { state ->
                state.copy(
                    scanning = state.hasCamera && state.cameraAccess == CameraAccessState.GRANTED,
                    pendingCapture = null,
                    status = null,
                    error = null,
                    manualError = null,
                )
            }
        }

        /** 导航已消费本次识别，清空信号，避免返回时重复跳转。 */
        fun onCaptureConsumed() {
            _uiState.update { it.copy(pendingCapture = null) }
        }

        fun onToggleTorch() {
            val state = _uiState.value
            if (state.cameraAccess != CameraAccessState.GRANTED || !state.hasCamera) return
            _uiState.update { it.copy(torchOn = !it.torchOn, torchNotice = null) }
        }

        /** 相机返回手电筒不可用（无闪光灯或相机未就绪）时，回滚状态并提示。 */
        fun onTorchResult(success: Boolean) {
            if (success) return
            _uiState.update { it.copy(torchOn = false, torchNotice = TORCH_UNSUPPORTED) }
        }

        fun onTorchNoticeShown() {
            _uiState.update { it.copy(torchNotice = null) }
        }

        fun onOpenManualEntry() {
            _uiState.update {
                it.copy(
                    manualEntry = true,
                    scanning = false,
                    manualError = null,
                    error = null,
                    status = null,
                )
            }
        }

        fun onCloseManualEntry() {
            _uiState.update {
                it.copy(
                    manualEntry = false,
                    manualError = null,
                    scanning = it.hasCamera && it.cameraAccess == CameraAccessState.GRANTED,
                )
            }
        }

        fun onManualCodeChange(value: String) {
            _uiState.update { it.copy(manualCode = value, manualError = null) }
        }

        /** 手工输入兜底：显式用户动作，不经去抖闸门，但仍走同一确认页与幂等键。 */
        fun onSubmitManualCode() {
            val value = _uiState.value.manualCode
            val validation = ScanCapture.validate(value)
            if (validation != null) {
                _uiState.update { it.copy(manualError = validation) }
                return
            }
            val capture = ScanCapture(rawValue = value.trim(), format = null, source = ScanSource.MANUAL)
            _uiState.update {
                it.copy(
                    scanning = false,
                    manualError = null,
                    status = null,
                    pendingCapture = capture,
                )
            }
        }

        fun onError(message: String) {
            _uiState.update { it.copy(error = message) }
        }

        fun onDismissError() {
            _uiState.update { it.copy(error = null) }
        }

        private companion object {
            const val TORCH_UNSUPPORTED = "此设备不支持手电筒。"
        }
    }

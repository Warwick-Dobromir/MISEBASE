package com.culinary.feature.scan

import com.culinary.core.domain.barcode.ScanCapture

/** 相机可用性/授权状态；决定扫码页展示相机、权限说明还是手工输入兜底。 */
enum class CameraAccessState {
    /** 尚未检查权限（首帧）。 */
    CHECKING,

    /** 尚未请求权限，展示权限说明页。 */
    NOT_REQUESTED,

    /** 已授权，展示相机预览。 */
    GRANTED,

    /** 用户拒绝授权，展示说明 + 手工输入兜底。 */
    DENIED,

    /** 设备没有可用相机，只能手工输入。 */
    UNAVAILABLE,
}

/**
 * 扫码页状态。
 *
 * [pendingCapture] 是一次性的导航信号：识别被接受后由页面消费并跳到确认页，
 * 消费后必须立刻清空，避免返回扫码页时重复导航。
 */
data class ScanUiState(
    val cameraAccess: CameraAccessState = CameraAccessState.CHECKING,
    val hasCamera: Boolean = true,
    val scanning: Boolean = false,
    val torchOn: Boolean = false,
    val torchNotice: String? = null,
    val manualEntry: Boolean = false,
    val manualCode: String = "",
    val manualError: String? = null,
    val error: String? = null,
    val status: String? = null,
    val pendingCapture: ScanCapture? = null,
) {
    /** 是否应展示相机预览：必须有相机、已授权、未切到手工输入。 */
    val showCamera: Boolean
        get() = hasCamera && cameraAccess == CameraAccessState.GRANTED && !manualEntry
}

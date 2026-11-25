package com.culinary.feature.scan

import com.culinary.core.domain.barcode.ScanCapture
import com.culinary.core.domain.model.InventoryArchive

/** 相机可用性/授权状态；决定扫码页展示相机、权限说明还是手工输入。 */
enum class CameraAccessState {
    CHECKING,
    NOT_REQUESTED,
    GRANTED,
    DENIED,
    UNAVAILABLE,
}

/** 扫码工作模式。 */
enum class ScanMode {
    STOCK_IN,
    VIEW_DETAILS,
}

/** 一次成功入库反馈；id 用于让连续动画彼此独立。 */
data class ScanSuccessFeedback(val id: Long, val visible: Boolean = true)

/** 扫码页状态；新档案通过 pendingCapture 一次性导航，已有档案按当前模式处理。 */
data class ScanUiState(
    val cameraAccess: CameraAccessState = CameraAccessState.CHECKING,
    val hasCamera: Boolean = true,
    val scanning: Boolean = false,
    val lookupInProgress: Boolean = false,
    val torchOn: Boolean = false,
    val torchNotice: String? = null,
    val manualEntry: Boolean = false,
    val manualCode: String = "",
    val manualError: String? = null,
    val error: String? = null,
    val status: String? = null,
    val mode: ScanMode = ScanMode.STOCK_IN,
    val successFeedbacks: List<ScanSuccessFeedback> = emptyList(),
    val openArchiveId: String? = null,
    val pendingCapture: ScanCapture? = null,
    val matchedArchive: InventoryArchive? = null,
) {
    /** 是否应展示相机预览：必须有相机、已授权、未切到手工输入。 */
    val showCamera: Boolean
        get() = hasCamera && cameraAccess == CameraAccessState.GRANTED && !manualEntry
}

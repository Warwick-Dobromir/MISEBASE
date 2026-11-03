package com.culinary.core.platform.permission

/**
 * 相机权限的三态（E06/阶段 5 退出条件）。
 *
 * - [GRANTED]：可扫码；
 * - [DENIED_CAN_ASK]：仍可再次请求（首次请求或系统允许弹窗）；
 * - [DENIED_PERMANENTLY]：系统不再弹窗，必须引导到设置或改用手工输入。
 */
enum class CameraPermissionState {
    GRANTED,
    DENIED_CAN_ASK,
    DENIED_PERMANENTLY,
}

/**
 * 根据 `checkSelfPermission` 与 `shouldShowRequestPermissionRationale` 判定状态。
 *
 * Android 没有"永久拒绝"的官方标志：通常用"已请求过 + 不再显示 rationale"推断。
 * 首次请求前同样不显示 rationale，因此必须用 [requestedBefore] 区分。
 */
object CameraPermissionEvaluator {
    fun evaluate(
        granted: Boolean,
        shouldShowRationale: Boolean,
        requestedBefore: Boolean,
    ): CameraPermissionState =
        when {
            granted -> CameraPermissionState.GRANTED
            shouldShowRationale -> CameraPermissionState.DENIED_CAN_ASK
            requestedBefore -> CameraPermissionState.DENIED_PERMANENTLY
            else -> CameraPermissionState.DENIED_CAN_ASK
        }
}

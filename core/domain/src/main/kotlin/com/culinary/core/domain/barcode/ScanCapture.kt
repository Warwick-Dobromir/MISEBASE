package com.culinary.core.domain.barcode

/**
 * 扫码识别来源。
 *
 * [CAMERA] 为 CameraX + ML Kit 的实时识别；[MANUAL] 是无相机或权限被拒绝时的
 * 手工输入兜底。来源必须随识别结果一起下传，业务模块据此决定提示与审计口径。
 */
enum class ScanSource {
    CAMERA,
    MANUAL,
}

/**
 * 一次被接受的扫码捕获（MB-D-004 的识别结果契约）。
 *
 * - [rawValue] 保留识别原文，不做数字清洗：清洗是业务决定，不是识别层职责；
 * - [format] 使用与 ML Kit 对齐的稳定名称（`EAN13`/`QR`/`CODE128` …），未知格式为 `null`；
 * - [source] 见 [ScanSource]。
 *
 * 去抖与幂等使用 [normalizedValue]（去掉首尾空白），避免同一码因帧间空白差异重复触发。
 */
data class ScanCapture(
    val rawValue: String,
    val format: String? = null,
    val source: ScanSource = ScanSource.CAMERA,
) {
    init {
        require(rawValue.isNotBlank()) { "scan rawValue must not be blank" }
    }

    /** 去抖与幂等键使用的规范化值；不做大小写或数字清洗。 */
    val normalizedValue: String get() = rawValue.trim()

    /** 识别结果的人类可读格式标签；未知格式显示"未知格式"。 */
    val formatLabel: String get() = format ?: UNKNOWN_FORMAT

    companion object {
        /** 手工输入与相机识别共用的长度上限，防止超长文本进入导航与幂等键。 */
        const val MAX_VALUE_LENGTH: Int = 512

        const val UNKNOWN_FORMAT: String = "未知格式"

        /** 校验候选条码值；返回 `null` 表示可接受，否则返回原因。 */
        fun validate(value: String): String? {
            val trimmed = value.trim()
            return when {
                trimmed.isEmpty() -> "条码内容不能为空。"
                trimmed.length > MAX_VALUE_LENGTH -> "条码内容不能超过 $MAX_VALUE_LENGTH 个字符。"
                else -> null
            }
        }
    }
}

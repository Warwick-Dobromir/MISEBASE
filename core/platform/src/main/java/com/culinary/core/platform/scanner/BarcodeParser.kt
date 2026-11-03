package com.culinary.core.platform.scanner

import com.culinary.core.common.CulinaryError
import com.culinary.core.common.ErrorCode
import com.culinary.core.common.Outcome
import com.culinary.core.domain.barcode.BarcodeResult

/**
 * 手工输入兜底（D-013）。
 *
 * 相机权限被永久拒绝或识别失败时，用户直接输入条码；这里做最小校验与规范化，
 * 不猜测条码类型，也不做在线查询。
 */
object BarcodeParser {
    private const val MIN_LENGTH = 4
    private const val MAX_LENGTH = 64
    private const val MANUAL_FORMAT = "MANUAL"

    fun parse(input: String): Outcome<BarcodeResult> {
        val normalized =
            input
                .trim()
                .replace(" ", "")
                .replace("-", "")
        if (normalized.length < MIN_LENGTH || normalized.length > MAX_LENGTH) {
            return invalid("barcode length must be between $MIN_LENGTH and $MAX_LENGTH")
        }
        if (normalized.any { it.code !in 33..126 }) {
            return invalid("barcode contains unsupported characters")
        }
        return Outcome.success(BarcodeResult(rawValue = normalized, format = MANUAL_FORMAT))
    }

    private fun invalid(reason: String): Outcome<Nothing> =
        Outcome.failure(
            CulinaryError.Validation(
                field = "barcode",
                rule = reason,
                code = ErrorCode.VAL_001,
            ),
        )
}

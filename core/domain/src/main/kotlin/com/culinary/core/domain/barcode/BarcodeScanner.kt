package com.culinary.core.domain.barcode

import com.culinary.core.common.Outcome

/**
 * 扫码端口（A03A §6.4）。
 *
 * 实现由 `core:platform` 提供；失败必须是可预期错误（权限拒绝 `CUL-SCAN-001`、
 * 识别失败 `CUL-SCAN-002`），由 UI 决定重试或转手工输入。
 */
interface BarcodeScanner {
    suspend fun scan(): Outcome<BarcodeResult>
}

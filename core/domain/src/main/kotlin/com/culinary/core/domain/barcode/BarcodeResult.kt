package com.culinary.core.domain.barcode

/**
 * 一次成功的条码识别结果。
 *
 * [format] 使用与 ML Kit 对齐的稳定名称（`EAN13`/`EAN8`/`UPC_A`/`CODE128`/`QR` 等）；
 * [rawValue] 保留识别原文，不做数字清洗——清洗由调用方按业务决定。
 */
data class BarcodeResult(
    val rawValue: String,
    val format: String?,
) {
    init {
        require(rawValue.isNotBlank()) { "barcode rawValue must not be blank" }
    }
}

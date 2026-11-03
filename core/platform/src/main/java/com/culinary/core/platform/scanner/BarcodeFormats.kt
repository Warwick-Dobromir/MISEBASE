package com.culinary.core.platform.scanner

import com.google.mlkit.vision.barcode.common.Barcode

/**
 * ML Kit 条码格式常量 → 稳定名称。
 *
 * 常量在编译期内联，因此本对象不依赖 ML Kit 运行时，可被纯 JVM 测试覆盖。
 */
object BarcodeFormats {
    fun name(format: Int): String? =
        when (format) {
            Barcode.FORMAT_EAN_13 -> "EAN13"
            Barcode.FORMAT_EAN_8 -> "EAN8"
            Barcode.FORMAT_UPC_A -> "UPC_A"
            Barcode.FORMAT_UPC_E -> "UPC_E"
            Barcode.FORMAT_CODE_128 -> "CODE128"
            Barcode.FORMAT_CODE_39 -> "CODE39"
            Barcode.FORMAT_QR_CODE -> "QR"
            Barcode.FORMAT_DATA_MATRIX -> "DATA_MATRIX"
            else -> null
        }
}

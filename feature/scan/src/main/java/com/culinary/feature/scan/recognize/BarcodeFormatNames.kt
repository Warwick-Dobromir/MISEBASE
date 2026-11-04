package com.culinary.feature.scan.recognize

import com.google.mlkit.vision.barcode.common.Barcode

/**
 * ML Kit 条码格式常量 → 稳定名称（D-013）。
 *
 * 常量在编译期内联，因此本对象不依赖 ML Kit 运行时，可被纯 JVM 测试覆盖。
 * 名称与 `core:platform` 的既有映射保持一致，避免同一格式在不同模块出现两个名字。
 */
internal object BarcodeFormatNames {
    private val names: Map<Int, String> =
        mapOf(
            Barcode.FORMAT_EAN_13 to "EAN13",
            Barcode.FORMAT_EAN_8 to "EAN8",
            Barcode.FORMAT_UPC_A to "UPC_A",
            Barcode.FORMAT_UPC_E to "UPC_E",
            Barcode.FORMAT_CODE_128 to "CODE128",
            Barcode.FORMAT_CODE_39 to "CODE39",
            Barcode.FORMAT_CODE_93 to "CODE93",
            Barcode.FORMAT_CODABAR to "CODABAR",
            Barcode.FORMAT_ITF to "ITF",
            Barcode.FORMAT_QR_CODE to "QR",
            Barcode.FORMAT_DATA_MATRIX to "DATA_MATRIX",
            Barcode.FORMAT_PDF417 to "PDF417",
            Barcode.FORMAT_AZTEC to "AZTEC",
        )

    fun name(format: Int): String? = names[format]
}

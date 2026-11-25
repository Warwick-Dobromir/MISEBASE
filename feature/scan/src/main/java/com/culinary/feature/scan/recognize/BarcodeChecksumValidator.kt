package com.culinary.feature.scan.recognize

import com.culinary.core.domain.barcode.ScanCapture

/**
 * 验证带 GS1/UPC 校验位的一维商品条码。
 *
 * 不对 QR、Code 128 等不使用本校验位规则的格式做错误拦截。
 * EAN-13、EAN-8、UPC-A 与 UPC-E 分别按其数据位长度/压缩规则校验。
 */
internal object BarcodeChecksumValidator {
    fun isValid(capture: ScanCapture): Boolean {
        val value = capture.normalizedValue
        return when (capture.format) {
            "EAN13" ->
                hasValidCheckDigit(
                    value,
                    dataLength = 12,
                )
            "EAN8" ->
                hasValidCheckDigit(
                    value,
                    dataLength = 7,
                )
            "UPC_A" ->
                hasValidCheckDigit(
                    value,
                    dataLength = 11,
                )
            "UPC_E" -> isValidUpcE(value)
            else -> true
        }
    }

    private fun hasValidCheckDigit(value: String, dataLength: Int): Boolean {
        if (value.length != dataLength + 1 || !value.all(Char::isDigit)) return false
        val data = value.take(dataLength)
        val suppliedCheckDigit = value.last().digitToInt()
        return calculateCheckDigit(data) == suppliedCheckDigit
    }

    /** GS1/UPC：从数据位最右侧开始依次乘 3、1、3、1…… */
    private fun calculateCheckDigit(data: String): Int {
        var weightedSum = 0
        data.reversed().forEachIndexed { index, character ->
            val weight = if (index % 2 == 0) 3 else 1
            weightedSum += character.digitToInt() * weight
        }
        return (10 - weightedSum % 10) % 10
    }

    private fun isValidUpcE(value: String): Boolean {
        if (value.length != 8 || !value.all(Char::isDigit)) return false
        val numberSystem = value[0]
        if (numberSystem != '0' && numberSystem != '1') return false

        val compressed = value.substring(1, 7)
        val checkDigit = value.last()
        val a = compressed[0]
        val b = compressed[1]
        val c = compressed[2]
        val d = compressed[3]
        val e = compressed[4]
        val f = compressed[5]

        val expandedData =
            when (f) {
                '0', '1', '2' -> "$numberSystem$a$b$f" + "0000" + "$c$d$e"
                '3' -> "$numberSystem$a$b$c" + "00000" + "$d$e"
                '4' -> "$numberSystem$a$b$c$d" + "00000" + e
                else -> "$numberSystem$a$b$c$d$e" + "0000" + f
            }
        return calculateCheckDigit(expandedData) == checkDigit.digitToInt()
    }
}

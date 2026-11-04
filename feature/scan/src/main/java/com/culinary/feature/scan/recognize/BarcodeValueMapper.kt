package com.culinary.feature.scan.recognize

import com.culinary.core.domain.barcode.ScanCapture
import com.culinary.core.domain.barcode.ScanSource

/**
 * ML Kit 原始结果 → 领域识别结果。
 *
 * 只做三件事：丢弃空值、丢弃超长值、补上格式名称与来源。不做数字清洗，
 * 也不做码制校验——那是业务模块的事（MB-D-004 §2.4）。
 */
internal object BarcodeValueMapper {
    fun toCapture(
        rawValue: String?,
        formatName: String?,
        source: ScanSource = ScanSource.CAMERA,
    ): ScanCapture? {
        val value = rawValue?.takeIf { it.isNotBlank() } ?: return null
        if (value.length > ScanCapture.MAX_VALUE_LENGTH) return null
        return ScanCapture(rawValue = value, format = formatName, source = source)
    }

    fun toCaptures(rawValues: List<Pair<String?, Int>>): List<ScanCapture> =
        rawValues.mapNotNull { (rawValue, format) ->
            toCapture(rawValue, BarcodeFormatNames.name(format))
        }
}

package com.culinary.core.domain.scan

import com.culinary.core.domain.barcode.ScanCapture

/**
 * 扫码导入键：同一条码使用同一个幂等键。
 *
 * 「同一条码重复导入不重复入库」由库存流水的唯一幂等键保证：扫同一个码再确认一次，
 * 命中既有流水后直接返回重放结果，不新增批次、不新增流水、也不重复创建食材。
 * 键只由条码值决定，不包含时间或随机数，否则重复确认会绕过幂等。
 */
object ScanImportKey {
    const val PREFIX: String = "scan-import:"

    fun of(capture: ScanCapture): String = PREFIX + capture.normalizedValue

    fun of(rawValue: String): String = PREFIX + rawValue.trim()
}

package com.culinary.core.domain.scan

import com.culinary.core.domain.barcode.ScanCapture

/**
 * 扫码导入幂等键（**事件级**，MB-S5-002 修正）。
 *
 * 格式：`scan-import:<barcode>:<operation_id>`
 *
 * - **一次确认 = 一个 operation id**：允许用户在不同时间把同一件商品多次入库，
 *   同时保证"同一次确认"在失败重试、进程内重复点击、事务重放时只写一次；
 * - **不包含时间**：时间戳精度不足会把同一秒内的两次确认合并；
 * - **条码做最小转义**（`%`、`:`）：否则条码 `A` 的键前缀会包含条码 `A:B` 的键，
 *   把不同商品误判为同一个。
 *
 * 幂等的最终保证在数据库层：`inventory_ledger.idempotency_key` 唯一，
 * `RecordStockReceiptUseCase` 先查键再写批次与流水；UI 置灰只是体验优化，不是幂等手段。
 */
object ScanImportKey {
    const val PREFIX: String = "scan-import:"

    private const val SEPARATOR = ':'

    /** 事件级键；[operationId] 必须非空（来源于一次确认动作）。 */
    fun of(
        capture: ScanCapture,
        operationId: String,
    ): String {
        val normalizedOperation = operationId.trim()
        require(normalizedOperation.isNotEmpty()) { "operationId must not be blank" }
        return prefixFor(capture) + normalizedOperation
    }

    /** 某条码的键前缀，用于"此前是否导入过"的判断。 */
    fun prefixFor(capture: ScanCapture): String = PREFIX + escape(capture.normalizedValue) + SEPARATOR

    /**
     * 该键是否属于这个条码。
     *
     * 兼容 Stage 5 早期按条码生成的旧键（`scan-import:<barcode>`，无事件 id）：
     * 旧键只用于展示"此前已导入"，不会再产生新数据。
     */
    fun isForBarcode(
        key: String,
        capture: ScanCapture,
    ): Boolean = key.startsWith(prefixFor(capture)) || key == PREFIX + capture.normalizedValue

    /** 条码里的 `%` 与 `:` 必须转义，保证前缀匹配没有歧义。 */
    private fun escape(value: String): String = value.replace("%", "%25").replace(":", "%3A")
}

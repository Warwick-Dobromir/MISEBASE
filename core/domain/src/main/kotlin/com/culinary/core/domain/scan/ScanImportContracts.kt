package com.culinary.core.domain.scan

import com.culinary.core.common.Outcome
import com.culinary.core.domain.barcode.ScanCapture
import com.culinary.core.domain.model.Ingredient
import com.culinary.core.domain.model.IngredientId
import com.culinary.core.domain.model.InventoryItem
import com.culinary.core.domain.model.InventoryLedgerEntry
import com.culinary.core.domain.model.Product
import com.culinary.core.domain.model.Unit

/**
 * 扫码结果交给业务模块时使用的明确数据接口（MB-D-004 §2.4）。
 *
 * `feature:scan` 只依赖这些端口，不依赖任何 feature 实现，也不直接写业务表：
 * - 食材创建/映射由 `feature:products` 实现 [ScanIngredientGateway]；
 * - 入库由 `feature:inventory` 实现 [ScanStockGateway]。
 *
 * 两个实现都**不开事务**：事务边界归调用方（确认页用例/ViewModel），
 * 否则会与 `TransactionRunner` 的"禁止嵌套"约束冲突。
 */
interface ScanIngredientGateway {
    /**
     * 解析扫码目标：复用已有食材（[ScanIngredientRequest.existingIngredientId]），
     * 或按名称新建食材（[ScanIngredientRequest.newIngredientName]）。
     */
    suspend fun resolve(request: ScanIngredientRequest): Outcome<ScanIngredientTarget>
}

/** 识别结果交给 `feature:products` 的请求：二选一。 */
data class ScanIngredientRequest(
    val capture: ScanCapture,
    val existingIngredientId: IngredientId? = null,
    val newIngredientName: String? = null,
)

/**
 * 解析后的导入目标。
 *
 * [created] 标记本次是否新建了食材，供调用方在后续入库失败时做明确补偿（归档）。
 * [unit] 是数量使用的单位：优先食材默认单位，其次系统可用的第一个单位。
 */
data class ScanIngredientTarget(
    val ingredient: Ingredient,
    val product: Product?,
    val unit: Unit,
    val created: Boolean,
)

/** 识别结果交给 `feature:inventory` 的入库请求。 */
data class ScanStockRequest(
    val capture: ScanCapture,
    val target: ScanIngredientTarget,
    val amount: Double,
    val note: String? = null,
)

/** 扫码入库结果；[idempotentReplay] 为真表示同一条码已导入过，本次未重复入库。 */
data class ScanStockReceipt(
    val entry: InventoryLedgerEntry,
    val item: InventoryItem,
    val idempotentReplay: Boolean,
)

/**
 * 由 `feature:inventory` 实现的"入库"端口。
 *
 * 实现必须以幂等键（[ScanImportKey]）查重，并保证批次与流水在同一事务内写入。
 */
interface ScanStockGateway {
    suspend fun receive(request: ScanStockRequest): Outcome<ScanStockReceipt>
}

/** 该条码此前的导入记录；[item] 可能已被后续出库/盘点改动，仅用于展示。 */
data class ScanPreviousImport(
    val entry: InventoryLedgerEntry,
    val item: InventoryItem?,
)

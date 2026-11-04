package com.culinary.core.domain.usecase.inventory

import com.culinary.core.domain.model.BatchId
import com.culinary.core.domain.model.IngredientId
import com.culinary.core.domain.model.InventoryItem
import com.culinary.core.domain.model.InventoryLedgerEntry
import com.culinary.core.domain.model.LocationId
import com.culinary.core.domain.model.ProductId
import com.culinary.core.domain.model.Quantity
import java.time.LocalDate

/** 出库原因；映射到稳定的流水类型，不自由写字符串。 */
enum class StockIssueReason {
    CONSUMED,
    WASTE,
    OTHER,
}

/** 入库输入：商品或食材二选一；位置为空时使用默认位置。 */
data class StockReceiptInput(
    val quantity: Quantity,
    val productId: ProductId? = null,
    val ingredientId: IngredientId? = null,
    val batchCode: String? = null,
    val locationId: LocationId? = null,
    val expiresOn: LocalDate? = null,
    val note: String? = null,
    val idempotencyKey: String? = null,
)

/** 出库输入：数量使用调用方单位，用例内换算成批次单位。 */
data class StockIssueInput(
    val batchId: BatchId,
    val quantity: Quantity,
    val reason: StockIssueReason = StockIssueReason.CONSUMED,
    val note: String? = null,
    val idempotencyKey: String? = null,
)

/** 盘点调整输入：[countedQuantity] 是实盘数量（可带单位），差额由用例计算。 */
data class StockAdjustmentInput(
    val batchId: BatchId,
    val countedQuantity: Quantity,
    val note: String? = null,
    val idempotencyKey: String? = null,
)

/** 一次库存变动的结果：新流水与变动后的批次。 */
data class StockMovementResult(
    val entry: InventoryLedgerEntry,
    val item: InventoryItem,
    val idempotentReplay: Boolean = false,
)

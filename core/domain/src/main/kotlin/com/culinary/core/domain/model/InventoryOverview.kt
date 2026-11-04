package com.culinary.core.domain.model

import java.time.Instant
import java.time.LocalDate

/** 库存总览的一行：按商品聚合全部批次。 */
data class InventoryOverviewRow(
    val productId: ProductId,
    val productName: String,
    val ingredientId: IngredientId,
    val ingredientName: String,
    val stockUnit: Unit,
    val onHand: Double,
    val reserved: Double,
    val available: Double,
    val batchCount: Int,
    val nearestExpiry: LocalDate? = null,
    val locationNames: List<String> = emptyList(),
) {
    init {
        require(onHand >= 0.0 && reserved >= 0.0 && available >= 0.0) {
            "inventory quantities must not be negative"
        }
    }

    val isLow: Boolean get() = available <= 0.0
}

/** 批次摘要：物料详情与入库/出库选择共用。 */
data class InventoryBatchSummary(
    val item: InventoryItem,
    val batchCode: String? = null,
    val receivedAt: Instant? = null,
    val locationName: String? = null,
)

/** 物料详情：批次余额、流水、位置与关联菜谱。 */
data class InventoryItemDetail(
    val batch: InventoryBatchSummary,
    val product: Product?,
    val ingredientName: String,
    val ledger: List<InventoryLedgerEntry>,
    val relatedRecipeNames: List<String>,
)

/** 商品级详情：全部批次 + 该商品流水 + 关联菜谱。 */
data class InventoryProductDetail(
    val product: Product,
    val ingredientName: String,
    val batches: List<InventoryBatchSummary>,
    val ledger: List<InventoryLedgerEntry>,
    val relatedRecipeNames: List<String>,
)

/** 流水展示行：把商品、食材、位置名称解析好，UI 不再查库。 */
data class InventoryLedgerRow(
    val entry: InventoryLedgerEntry,
    val productName: String,
    val ingredientName: String,
    val locationName: String,
)

/** 入库/出库表单需要的只读选项。 */
data class InventoryFormOptions(
    val products: List<Product>,
    val locations: List<StorageLocation>,
    val ingredients: List<Ingredient>,
    val units: List<Unit> = emptyList(),
    val batches: List<InventoryBatchSummary> = emptyList(),
)

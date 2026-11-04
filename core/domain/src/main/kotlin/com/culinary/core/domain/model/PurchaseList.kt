package com.culinary.core.domain.model

import java.time.Instant

/**
 * 采购清单行（MB-D-016）。
 *
 * 采购清单**不落** `purchase_order*` 表：它是按"需求 − 可用"即时计算出的展示/导出
 * 结果，属于 Inventory 能力。可选配料默认不进入清单（MB-D-015）。
 */
data class PurchaseListLine(
    val ingredientId: IngredientId,
    val required: Quantity,
    val available: Quantity,
    val toPurchase: Quantity,
    val productId: ProductId? = null,
) {
    init {
        require(required.unit.id == available.unit.id) { "required and available must share a unit" }
        require(toPurchase.unit.id == required.unit.id) { "toPurchase must use the requirement unit" }
        require(toPurchase.amount > 0.0) { "purchase line quantity must be > 0, was ${toPurchase.amount}" }
    }
}

/**
 * 采购清单（MB-D-016）：按需计算、可展示、可导出；不提供持久化实体。
 */
data class PurchaseList(
    val generatedAt: Instant,
    val lines: List<PurchaseListLine>,
) {
    val isEmpty: Boolean get() = lines.isEmpty()

    val totalLines: Int get() = lines.size
}

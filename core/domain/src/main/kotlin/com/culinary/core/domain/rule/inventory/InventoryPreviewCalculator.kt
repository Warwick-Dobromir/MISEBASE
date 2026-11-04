package com.culinary.core.domain.rule.inventory

import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.BatchId
import com.culinary.core.domain.model.InventoryItem
import com.culinary.core.domain.model.ProductId
import com.culinary.core.domain.model.ProductRequirement
import com.culinary.core.domain.model.Quantity
import com.culinary.core.domain.model.Unit
import com.culinary.core.domain.rule.unit.UnitConverter

/** 单个批次的分配量（数量使用需求单位，便于展示与流水换算）。 */
data class InventoryAllocation(
    val batchId: BatchId,
    val quantity: Quantity,
)

/** 预览行：需求、可用、缺口与建议批次（FEFO）。 */
data class InventoryPreviewLine(
    val productId: ProductId,
    val required: Quantity,
    val available: Quantity,
    val shortfall: Quantity,
    val allocations: List<InventoryAllocation>,
) {
    val isCovered: Boolean get() = shortfall.amount <= 0.0
}

/** 扣减预览（MB-D-014）：确认前唯一允许产生的副作用是"读"。 */
data class InventoryPreview(
    val lines: List<InventoryPreviewLine>,
) {
    val hasShortfall: Boolean get() = lines.any { !it.isCovered }
}

/**
 * 需求/可用/缺口预览（MB-D-014）。
 *
 * - 批次按 FEFO 排序（无到期日排最后，同日期按批次号稳定排序）；
 * - 库存批次单位与需求单位不同维度且无换算依据时，该批次计 0 可用，不猜密度；
 * - 同一商品的多行需求按顺序消费可用量，不重复占用同一批次；
 * - 本类不创建预留，也不写流水。
 */
class InventoryPreviewCalculator(
    private val unitConverter: UnitConverter = UnitConverter(),
) {
    fun preview(
        requirements: List<ProductRequirement>,
        items: List<InventoryItem>,
    ): Outcome<InventoryPreview> {
        val consumed = mutableMapOf<BatchId, Double>()
        val lines =
            requirements.map { requirement ->
                val unit = requirement.quantity.unit
                var remaining = requirement.quantity.amount
                val allocations = mutableListOf<InventoryAllocation>()

                items
                    .asSequence()
                    .filter { it.productId == requirement.productId }
                    .sortedWith(compareBy({ it.fefoKey }, { it.batchId.value }))
                    .forEach { item ->
                        if (remaining <= EPSILON) return@forEach
                        val available =
                            convertAvailable(item, unit) - (consumed[item.batchId] ?: 0.0)
                        if (available <= EPSILON) return@forEach
                        val take = minOf(remaining, available)
                        allocations += InventoryAllocation(item.batchId, Quantity(take, unit))
                        consumed[item.batchId] = (consumed[item.batchId] ?: 0.0) + take
                        remaining -= take
                    }

                val availableAmount = requirement.quantity.amount - remaining
                InventoryPreviewLine(
                    productId = requirement.productId,
                    required = requirement.quantity,
                    available = Quantity(availableAmount, unit),
                    shortfall = Quantity(remaining, unit),
                    allocations = allocations,
                )
            }
        return Outcome.success(InventoryPreview(lines))
    }

    private fun convertAvailable(
        item: InventoryItem,
        target: Unit,
    ): Double =
        when (val converted = unitConverter.convert(Quantity(item.available, item.stockUnit), target)) {
            is Outcome.Success -> converted.value.amount
            is Outcome.Failure -> 0.0
        }

    private companion object {
        const val EPSILON = 1e-9
    }
}

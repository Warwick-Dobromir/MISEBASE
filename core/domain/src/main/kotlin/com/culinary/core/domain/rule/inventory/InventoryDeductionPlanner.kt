package com.culinary.core.domain.rule.inventory

import com.culinary.core.common.CulinaryError
import com.culinary.core.common.DomainDetail
import com.culinary.core.common.ErrorCode
import com.culinary.core.common.IdGenerator
import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.BatchId
import com.culinary.core.domain.model.InventoryItem
import com.culinary.core.domain.model.InventoryLedgerEntry
import com.culinary.core.domain.model.LedgerEntryId
import com.culinary.core.domain.model.LedgerEntryType
import com.culinary.core.domain.model.ProductRequirement
import com.culinary.core.domain.model.Quantity
import com.culinary.core.domain.rule.unit.UnitConverter
import java.time.Instant

/** 扣减计划：预览 + 待追加的流水（尚未写入）。 */
data class DeductionPlan(
    val preview: InventoryPreview,
    val entries: List<InventoryLedgerEntry>,
)

/**
 * 库存扣减计划（MB-D-014）。
 *
 * - 先预览需求/可用/缺口，确认时才生成流水；
 * - 默认**不创建预留**：`reservedDelta = 0`，`reservedAfter` 保持不变；
 * - 任何批次扣减后不得为负，否则返回 `CUL-INV-003` 且不产生半条流水；
 * - 幂等键由引用（类型 + ID + 批次 + 行序）确定性生成，重复确认不会重复写。
 */
class InventoryDeductionPlanner(
    private val previewCalculator: InventoryPreviewCalculator = InventoryPreviewCalculator(),
    private val unitConverter: UnitConverter = UnitConverter(),
) {
    fun plan(
        requirements: List<ProductRequirement>,
        items: List<InventoryItem>,
        referenceType: String,
        referenceId: String,
        occurredAt: Instant,
        idGenerator: IdGenerator,
    ): Outcome<DeductionPlan> =
        when (val preview = previewCalculator.preview(requirements, items)) {
            is Outcome.Failure -> preview
            is Outcome.Success ->
                when (
                    val entries =
                        planAllocations(
                            lines = preview.value.lines,
                            items = items,
                            referenceType = referenceType,
                            referenceId = referenceId,
                            occurredAt = occurredAt,
                            idGenerator = idGenerator,
                        )
                ) {
                    is Outcome.Failure -> entries
                    is Outcome.Success -> Outcome.success(DeductionPlan(preview.value, entries.value))
                }
        }

    fun planAllocations(
        lines: List<InventoryPreviewLine>,
        items: List<InventoryItem>,
        referenceType: String,
        referenceId: String,
        occurredAt: Instant,
        idGenerator: IdGenerator,
    ): Outcome<List<InventoryLedgerEntry>> {
        val onHand = items.associate { it.batchId to it.onHand }.toMutableMap()
        val reserved = items.associate { it.batchId to it.reserved }.toMutableMap()
        val entries = mutableListOf<InventoryLedgerEntry>()

        lines.forEachIndexed { lineIndex, line ->
            line.allocations.forEachIndexed { allocationIndex, allocation ->
                val item =
                    items.firstOrNull { it.batchId == allocation.batchId }
                        ?: return missingBatch(allocation.batchId)

                val batchDelta =
                    when (
                        val converted =
                            unitConverter.convert(allocation.quantity, item.stockUnit)
                    ) {
                        is Outcome.Failure -> return converted
                        is Outcome.Success -> converted.value.amount
                    }

                val currentOnHand = onHand.getValue(allocation.batchId)
                val currentReserved = reserved.getValue(allocation.batchId)
                val newOnHand = currentOnHand - batchDelta
                if (newOnHand < -EPSILON) {
                    return Outcome.failure(
                        CulinaryError.Domain(
                            detail =
                                DomainDetail.InsufficientStock(
                                    productId = line.productId.value,
                                    requested = batchDelta,
                                    available = currentOnHand,
                                ),
                            code = ErrorCode.INV_003,
                        ),
                    )
                }

                val key = "$referenceType:$referenceId:${allocation.batchId.value}:$lineIndex:$allocationIndex"
                entries +=
                    InventoryLedgerEntry(
                        id = LedgerEntryId(idGenerator.newId()),
                        batchId = allocation.batchId,
                        productId = line.productId,
                        locationId = item.locationId,
                        type = LedgerEntryType.PRODUCTION_ISSUE,
                        stockUnit = item.stockUnit,
                        onHandDelta = -batchDelta,
                        reservedDelta = 0.0,
                        onHandAfter = newOnHand,
                        reservedAfter = currentReserved,
                        idempotencyKey = key,
                        occurredAt = occurredAt,
                        referenceType = referenceType,
                        referenceId = referenceId,
                    )
                onHand[allocation.batchId] = newOnHand
            }
        }
        return Outcome.success(entries)
    }

    private fun missingBatch(batchId: BatchId): Outcome<Nothing> =
        Outcome.failure(CulinaryError.NotFound(entity = "inventory_batch", id = batchId.value))

    private companion object {
        const val EPSILON = 1e-9
    }
}

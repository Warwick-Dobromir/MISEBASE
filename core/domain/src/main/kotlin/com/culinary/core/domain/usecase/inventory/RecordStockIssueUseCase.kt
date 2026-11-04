package com.culinary.core.domain.usecase.inventory

import com.culinary.core.common.Clock
import com.culinary.core.common.CulinaryError
import com.culinary.core.common.IdGenerator
import com.culinary.core.common.Outcome
import com.culinary.core.common.flatMap
import com.culinary.core.domain.model.InventoryLedgerEntry
import com.culinary.core.domain.model.LedgerEntryId
import com.culinary.core.domain.model.LedgerEntryType
import com.culinary.core.domain.repository.InventoryRepository
import com.culinary.core.domain.rule.unit.UnitConverter
import com.culinary.core.domain.transaction.TransactionRunner

/**
 * 出库（领用/损耗）：数量先换算为批次单位，再按可用量校验。
 *
 * 不足时返回 `CUL-INV-003` 并附带缺口（请求量/可用量），不写任何流水。
 */
class RecordStockIssueUseCase(
    private val inventoryRepository: InventoryRepository,
    private val transactionRunner: TransactionRunner,
    private val idGenerator: IdGenerator,
    private val clock: Clock,
    private val unitConverter: UnitConverter = UnitConverter(),
) {
    suspend operator fun invoke(input: StockIssueInput): Outcome<StockMovementResult> =
        transactionRunner
            .write { execute(input) }
            .flatMap { it }

    @Suppress("LongMethod", "CyclomaticComplexMethod", "ReturnCount")
    private suspend fun execute(input: StockIssueInput): Outcome<StockMovementResult> {
        if (!input.quantity.amount.isFinite() || input.quantity.amount <= 0.0) {
            return invalid("quantity", "must be a finite positive number")
        }
        val key = input.idempotencyKey?.takeIf { it.isNotBlank() } ?: idGenerator.newId()
        existingReplay(key)?.let { return it }
        val item =
            when (val result = inventoryRepository.findItem(input.batchId)) {
                is Outcome.Failure -> return result
                is Outcome.Success ->
                    result.value
                        ?: return Outcome.failure(
                            CulinaryError.NotFound("inventory_batch", input.batchId.value),
                        )
            }
        val converted =
            when (val result = unitConverter.convert(input.quantity, item.stockUnit)) {
                is Outcome.Failure -> return result
                is Outcome.Success -> result.value
            }
        if (converted.amount > item.available + EPSILON) {
            return Outcome.failure(
                CulinaryError.Domain(
                    detail =
                        com.culinary.core.common.DomainDetail.InsufficientStock(
                            productId = item.productId.value,
                            requested = converted.amount,
                            available = item.available,
                        ),
                    code = com.culinary.core.common.ErrorCode.INV_003,
                ),
            )
        }
        val now = clock.now()
        val entry =
            InventoryLedgerEntry(
                id = LedgerEntryId(idGenerator.newId()),
                batchId = item.batchId,
                productId = item.productId,
                locationId = item.locationId,
                type = input.reason.toLedgerType(),
                stockUnit = item.stockUnit,
                onHandDelta = -converted.amount,
                reservedDelta = 0.0,
                onHandAfter = item.onHand - converted.amount,
                reservedAfter = item.reserved,
                idempotencyKey = key,
                occurredAt = now,
                referenceType = REFERENCE_TYPE,
                referenceId = item.batchId.value,
                note = input.note?.trim()?.takeIf { it.isNotEmpty() },
            )
        when (val appended = inventoryRepository.appendIfAbsent(entry)) {
            is Outcome.Failure -> return appended
            is Outcome.Success ->
                if (!appended.value) {
                    existingReplay(key)?.let { return it }
                    return Outcome.failure(CulinaryError.Conflict(entity = "inventory_ledger"))
                }
        }
        val updated =
            when (val result = inventoryRepository.findItem(item.batchId)) {
                is Outcome.Failure -> return result
                is Outcome.Success ->
                    result.value
                        ?: return Outcome.failure(
                            CulinaryError.NotFound("inventory_batch", item.batchId.value),
                        )
            }
        return Outcome.success(StockMovementResult(entry = entry, item = updated))
    }

    @Suppress("ReturnCount") // 幂等重放需要分别处理查键/读批次/缺失三条失败路径。
    private suspend fun existingReplay(key: String): Outcome<StockMovementResult>? {
        val existing =
            when (val result = inventoryRepository.findByIdempotencyKey(key)) {
                is Outcome.Failure -> return result
                is Outcome.Success -> result.value
            } ?: return null
        val item =
            when (val result = inventoryRepository.findItem(existing.batchId)) {
                is Outcome.Failure -> return result
                is Outcome.Success ->
                    result.value
                        ?: return Outcome.failure(
                            CulinaryError.NotFound("inventory_batch", existing.batchId.value),
                        )
            }
        return Outcome.success(
            StockMovementResult(entry = existing, item = item, idempotentReplay = true),
        )
    }

    private fun StockIssueReason.toLedgerType(): LedgerEntryType =
        when (this) {
            StockIssueReason.CONSUMED -> LedgerEntryType.PRODUCTION_ISSUE
            StockIssueReason.WASTE -> LedgerEntryType.WASTE
            StockIssueReason.OTHER -> LedgerEntryType.ADJUSTMENT_OUT
        }

    private fun invalid(
        field: String,
        rule: String,
    ): Outcome<Nothing> = Outcome.failure(CulinaryError.Validation(field = field, rule = rule))

    private companion object {
        const val REFERENCE_TYPE = "stock_issue"
        const val EPSILON = 1e-9
    }
}

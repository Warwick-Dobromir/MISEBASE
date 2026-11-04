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
 * 盘点调整：以实盘数量为锚，差额走 `ADJUSTMENT_IN/OUT` 流水。
 *
 * 不直接改余额；差额为 0 时拒绝，避免产生无意义的 0 流水。
 */
class AdjustStockUseCase(
    private val inventoryRepository: InventoryRepository,
    private val transactionRunner: TransactionRunner,
    private val idGenerator: IdGenerator,
    private val clock: Clock,
    private val unitConverter: UnitConverter = UnitConverter(),
) {
    suspend operator fun invoke(input: StockAdjustmentInput): Outcome<StockMovementResult> =
        transactionRunner
            .write { execute(input) }
            .flatMap { it }

    private suspend fun execute(input: StockAdjustmentInput): Outcome<StockMovementResult> {
        if (!input.countedQuantity.amount.isFinite() || input.countedQuantity.amount < 0.0) {
            return invalid("countedQuantity", "must be a finite non negative number")
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
        val counted =
            when (val result = unitConverter.convert(input.countedQuantity, item.stockUnit)) {
                is Outcome.Failure -> return result
                is Outcome.Success -> result.value
            }
        val delta = counted.amount - item.onHand
        if (kotlin.math.abs(delta) <= EPSILON) {
            return invalid("countedQuantity", "must differ from on hand")
        }
        if (counted.amount < item.reserved) {
            return Outcome.failure(
                CulinaryError.Validation(
                    field = "countedQuantity",
                    rule = "must not be below reserved quantity",
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
                type =
                    if (delta > 0.0) {
                        LedgerEntryType.ADJUSTMENT_IN
                    } else {
                        LedgerEntryType.ADJUSTMENT_OUT
                    },
                stockUnit = item.stockUnit,
                onHandDelta = delta,
                reservedDelta = 0.0,
                onHandAfter = counted.amount,
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

    private fun invalid(
        field: String,
        rule: String,
    ): Outcome<Nothing> = Outcome.failure(CulinaryError.Validation(field = field, rule = rule))

    private companion object {
        const val REFERENCE_TYPE = "stock_adjustment"
        const val EPSILON = 1e-9
    }
}

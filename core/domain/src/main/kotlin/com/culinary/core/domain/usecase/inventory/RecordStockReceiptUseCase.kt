package com.culinary.core.domain.usecase.inventory

import com.culinary.core.common.Clock
import com.culinary.core.common.CulinaryError
import com.culinary.core.common.IdGenerator
import com.culinary.core.common.Outcome
import com.culinary.core.common.flatMap
import com.culinary.core.domain.model.EntityStatus
import com.culinary.core.domain.model.Ingredient
import com.culinary.core.domain.model.InventoryLedgerEntry
import com.culinary.core.domain.model.LedgerEntryId
import com.culinary.core.domain.model.LedgerEntryType
import com.culinary.core.domain.model.NewInventoryBatch
import com.culinary.core.domain.model.Product
import com.culinary.core.domain.model.ProductId
import com.culinary.core.domain.model.StorageLocation
import com.culinary.core.domain.repository.IngredientRepository
import com.culinary.core.domain.repository.InventoryRepository
import com.culinary.core.domain.repository.UnitRepository
import com.culinary.core.domain.rule.unit.UnitConverter
import com.culinary.core.domain.transaction.TransactionRunner

/**
 * 入库（MB-D-014 的"确认后写流水"）。
 *
 * 支持直接选择商品，或选择食材后按需创建库存商品。批次初始余额为 0，
 * 余额只由随后的 `PURCHASE_RECEIPT` 流水增加；批次与流水同一事务。
 */
class RecordStockReceiptUseCase(
    private val inventoryRepository: InventoryRepository,
    private val ingredientRepository: IngredientRepository,
    private val unitRepository: UnitRepository,
    private val transactionRunner: TransactionRunner,
    private val idGenerator: IdGenerator,
    private val clock: Clock,
    private val unitConverter: UnitConverter = UnitConverter(),
) {
    suspend operator fun invoke(input: StockReceiptInput): Outcome<StockMovementResult> =
        transactionRunner
            .write { execute(input) }
            .flatMap { it }

    private suspend fun execute(input: StockReceiptInput): Outcome<StockMovementResult> {
        val validation = validate(input)
        if (validation != null) return validation
        val key = input.idempotencyKey?.takeIf { it.isNotBlank() } ?: idGenerator.newId()
        existingReplay(key)?.let { return it }

        val product =
            when (val result = resolveProduct(input)) {
                is Outcome.Failure -> return result
                is Outcome.Success -> result.value
            }
        val location =
            when (val result = resolveLocation(input)) {
                is Outcome.Failure -> return result
                is Outcome.Success -> result.value
            }
        val converted =
            when (val result = unitConverter.convert(input.quantity, product.stockUnit)) {
                is Outcome.Failure -> return result
                is Outcome.Success -> result.value
            }
        if (converted.amount <= 0.0) {
            return invalid("quantity", "must be a finite positive number")
        }

        val now = clock.now()
        val batchId =
            when (
                val created =
                    inventoryRepository.createBatch(
                        NewInventoryBatch(
                            productId = product.id,
                            locationId = location.id,
                            stockUnit = product.stockUnit,
                            batchCode = input.batchCode?.trim()?.takeIf { it.isNotEmpty() },
                            receivedAt = now,
                            expiresOn = input.expiresOn,
                        ),
                    )
            ) {
                is Outcome.Failure -> return created
                is Outcome.Success -> created.value
            }
        val entry =
            InventoryLedgerEntry(
                id = LedgerEntryId(idGenerator.newId()),
                batchId = batchId,
                productId = product.id,
                locationId = location.id,
                type = LedgerEntryType.PURCHASE_RECEIPT,
                stockUnit = product.stockUnit,
                onHandDelta = converted.amount,
                reservedDelta = 0.0,
                onHandAfter = converted.amount,
                reservedAfter = 0.0,
                idempotencyKey = key,
                occurredAt = now,
                referenceType = REFERENCE_TYPE,
                referenceId = batchId.value,
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
        val item =
            when (val result = inventoryRepository.findItem(batchId)) {
                is Outcome.Failure -> return result
                is Outcome.Success ->
                    result.value
                        ?: return Outcome.failure(CulinaryError.NotFound("inventory_batch", batchId.value))
            }
        return Outcome.success(StockMovementResult(entry = entry, item = item))
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

    private suspend fun resolveProduct(input: StockReceiptInput): Outcome<Product> =
        when {
            input.productId != null ->
                when (val result = inventoryRepository.findProduct(input.productId)) {
                    is Outcome.Failure -> result
                    is Outcome.Success ->
                        result.value
                            ?.let { Outcome.success(it) }
                            ?: Outcome.failure(
                                CulinaryError.NotFound("product", input.productId.value),
                            )
                }

            input.ingredientId != null -> resolveProductForIngredient(input.ingredientId)
            else -> invalid("productId", "product or ingredient is required")
        }

    private suspend fun resolveProductForIngredient(
        ingredientId: com.culinary.core.domain.model.IngredientId,
    ): Outcome<Product> {
        val existing =
            when (val result = inventoryRepository.findProductForIngredient(ingredientId)) {
                is Outcome.Failure -> return result
                is Outcome.Success -> result.value
            }
        if (existing != null) return Outcome.success(existing)

        val ingredient =
            when (val result = ingredientRepository.get(ingredientId)) {
                is Outcome.Failure -> return result
                is Outcome.Success -> result.value
            }
        val unit = defaultUnit(ingredient) ?: return unitMissing()
        val product =
            Product(
                id = ProductId(idGenerator.newId()),
                ingredientId = ingredient.id,
                name = ingredient.name,
                stockUnit = unit,
                contentQty = 1.0,
                contentUnit = unit,
                stockToContentFactor = 1.0,
                status = EntityStatus.ACTIVE,
            )
        return when (val saved = inventoryRepository.saveProduct(product)) {
            is Outcome.Failure -> saved
            is Outcome.Success -> Outcome.success(product)
        }
    }

    private suspend fun defaultUnit(ingredient: Ingredient): com.culinary.core.domain.model.Unit? {
        val id = ingredient.defaultUnitId
        if (id != null) {
            unitRepository.find(id)?.let { return it }
        }
        return unitRepository.all().firstOrNull()
    }

    private suspend fun resolveLocation(input: StockReceiptInput): Outcome<StorageLocation> =
        if (input.locationId == null) {
            inventoryRepository.ensureDefaultLocation()
        } else {
            when (val result = inventoryRepository.findLocation(input.locationId)) {
                is Outcome.Failure -> result
                is Outcome.Success ->
                    result.value
                        ?.let { Outcome.success(it) }
                        ?: Outcome.failure(
                            CulinaryError.NotFound("storage_location", input.locationId.value),
                        )
            }
        }

    private fun validate(input: StockReceiptInput): Outcome<Nothing>? {
        if ((input.productId == null) == (input.ingredientId == null)) {
            return invalid("productId", "exactly one of product or ingredient is required")
        }
        if (!input.quantity.amount.isFinite() || input.quantity.amount <= 0.0) {
            return invalid("quantity", "must be a finite positive number")
        }
        return null
    }

    private fun invalid(
        field: String,
        rule: String,
    ): Outcome<Nothing> = Outcome.failure(CulinaryError.Validation(field = field, rule = rule))

    private fun unitMissing(): Outcome<Nothing> = Outcome.failure(CulinaryError.NotFound("unit", "ingredient.default"))

    private companion object {
        const val REFERENCE_TYPE = "stock_receipt"
    }
}

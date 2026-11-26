package com.culinary.core.domain.usecase.inventory

import com.culinary.core.common.Clock
import com.culinary.core.common.CulinaryError
import com.culinary.core.common.IdGenerator
import com.culinary.core.common.Outcome
import com.culinary.core.common.flatMap
import com.culinary.core.domain.model.InventoryBatchRecord
import com.culinary.core.domain.model.InventoryLedgerEntry
import com.culinary.core.domain.model.LedgerEntryId
import com.culinary.core.domain.model.LedgerEntryType
import com.culinary.core.domain.model.LocationId
import com.culinary.core.domain.model.NewArchiveBatch
import com.culinary.core.domain.model.PrintJobRecord
import com.culinary.core.domain.model.PrintKind
import com.culinary.core.domain.model.Quantity
import com.culinary.core.domain.model.StorageLocation
import com.culinary.core.domain.repository.IngredientRepository
import com.culinary.core.domain.repository.InventoryArchiveRepository
import com.culinary.core.domain.repository.UnitRepository
import com.culinary.core.domain.rule.unit.UnitConverter
import com.culinary.core.domain.transaction.TransactionRunner
import java.time.LocalDate

/** 手工入库输入（v9）。 */
data class ArchiveReceiptInput(
    val archiveId: String,
    val quantity: Double,
    val unitId: String,
    val batchCode: String? = null,
    val productionDate: LocalDate? = null,
    val expiresOn: LocalDate? = null,
    val locationId: LocationId? = null,
    val sourceProductId: String? = null,
    val note: String? = null,
    val isTemporary: Boolean = false,
    val operator: String? = null,
    val idempotencyKey: String? = null,
)

/** 入库结果：批次、流水与入库打印任务。 */
data class ReceiptResult(
    val batch: InventoryBatchRecord,
    val ledger: InventoryLedgerEntry,
    val printJob: PrintJobRecord? = null,
    val idempotentReplay: Boolean = false,
)

/**
 * 手工/采购入库（v9）。
 *
 * 确认后在一个事务内完成：建批次（余额 0）→ 追加 `PURCHASE_RECEIPT` 流水（余额增加）
 * → 建入库打印任务。打印失败只影响任务状态，不回滚库存。
 */
class RecordArchiveReceiptUseCase(
    private val repository: InventoryArchiveRepository,
    private val unitRepository: UnitRepository,
    private val ingredientRepository: IngredientRepository,
    private val transactionRunner: TransactionRunner,
    private val idGenerator: IdGenerator,
    private val clock: Clock,
    private val unitConverter: UnitConverter = UnitConverter(),
) {
    suspend operator fun invoke(input: ArchiveReceiptInput): Outcome<ReceiptResult> =
        transactionRunner
            .write { execute(input, input.idempotencyKey?.takeIf { it.isNotBlank() } ?: idGenerator.newId(), 0) }
            .flatMap { it }

    @Suppress("ReturnCount", "LongMethod", "CyclomaticComplexMethod")
    internal suspend fun execute(
        input: ArchiveReceiptInput,
        idempotencyKey: String,
        printLineSuffix: Int,
    ): Outcome<ReceiptResult> {
        if (!input.quantity.isFinite() || input.quantity <= 0.0) {
            return invalid("quantity", "must be a finite positive number")
        }
        repository.findLedgerByIdempotencyKey(idempotencyKey).let { result ->
            when (result) {
                is Outcome.Failure -> return result
                is Outcome.Success -> {
                    val existing = result.value
                    if (existing != null) {
                        return replay(existing, idempotencyKey)
                    }
                }
            }
        }
        val archive =
            when (val result = repository.findArchive(input.archiveId)) {
                is Outcome.Failure -> return result
                is Outcome.Success ->
                    result.value
                        ?: return Outcome.failure(
                            CulinaryError.NotFound("inventory_archive", input.archiveId),
                        )
            }
        val unit =
            unitRepository.find(
                com.culinary.core.domain.model
                    .UnitId(input.unitId),
            )
                ?: return Outcome.failure(CulinaryError.NotFound("unit", input.unitId))
        val ingredientId =
            repository.archiveLinks(archive.id).let { result ->
                when (result) {
                    is Outcome.Failure -> null
                    is Outcome.Success ->
                        result.value
                            .firstOrNull()
                            ?.product
                            ?.ingredientId
                }
            }
        val conversions = ingredientId?.let { unitRepository.conversionsFor(it) }.orEmpty()
        val converted =
            when (
                val result =
                    unitConverter.convert(
                        Quantity(input.quantity, unit),
                        archive.stockUnit,
                        conversions,
                        ingredientId,
                    )
            ) {
                is Outcome.Failure -> return result
                is Outcome.Success -> result.value
            }
        if (converted.amount <= 0.0) return invalid("quantity", "must be positive after conversion")
        val location =
            when (val result = resolveLocation(input.locationId ?: archive.defaultLocationId)) {
                is Outcome.Failure -> return result
                is Outcome.Success -> result.value
            }
        val now = clock.now()
        val batchId =
            when (
                val created =
                    repository.createBatch(
                        NewArchiveBatch(
                            archiveId = archive.id,
                            productId = input.sourceProductId,
                            locationId = location.id,
                            batchCode = input.batchCode?.trim()?.takeIf { it.isNotEmpty() },
                            receivedAt = now,
                            productionDate = input.productionDate,
                            expiresOn = input.expiresOn,
                            stockUnit = archive.stockUnit,
                            isTemporary = input.isTemporary,
                        ),
                    )
            ) {
                is Outcome.Failure -> return created
                is Outcome.Success -> created.value
            }
        val entry =
            InventoryLedgerEntry(
                id = LedgerEntryId(idGenerator.newId()),
                batchId =
                    com.culinary.core.domain.model
                        .BatchId(batchId),
                productId =
                    input.sourceProductId?.let {
                        com.culinary.core.domain.model
                            .ProductId(it)
                    },
                locationId = location.id,
                type = LedgerEntryType.PURCHASE_RECEIPT,
                stockUnit = archive.stockUnit,
                onHandDelta = converted.amount,
                reservedDelta = 0.0,
                onHandAfter = converted.amount,
                reservedAfter = 0.0,
                idempotencyKey = idempotencyKey,
                occurredAt = now,
                referenceType = REFERENCE_TYPE,
                referenceId = batchId,
                note = input.note?.trim()?.takeIf { it.isNotEmpty() },
                archiveId = archive.id,
                operator = input.operator,
            )
        when (val appended = repository.appendLedger(entry)) {
            is Outcome.Failure -> return appended
            is Outcome.Success ->
                if (!appended.value) {
                    return Outcome.failure(CulinaryError.Conflict(entity = "inventory_ledger"))
                }
        }
        val printJob =
            when (
                val job =
                    repository.createPrintJob(
                        kind = PrintKind.RECEIPT,
                        refType = REFERENCE_TYPE,
                        refId = batchId,
                        paperWidthMm = if (printLineSuffix % 2 == 0) 80 else 58,
                        at = now,
                    )
            ) {
                is Outcome.Failure -> null
                is Outcome.Success -> job.value
            }
        val batch =
            when (val result = repository.findBatch(batchId)) {
                is Outcome.Failure -> return result
                is Outcome.Success ->
                    result.value
                        ?: return Outcome.failure(CulinaryError.NotFound("inventory_batch", batchId))
            }
        return Outcome.success(ReceiptResult(batch = batch, ledger = entry, printJob = printJob))
    }

    private suspend fun resolveLocation(preferred: LocationId?): Outcome<StorageLocation> {
        if (preferred != null) {
            repository.listLocations().let { result ->
                when (result) {
                    is Outcome.Failure -> return result
                    is Outcome.Success ->
                        result.value.firstOrNull { it.id == preferred }?.let { return Outcome.success(it) }
                }
            }
        }
        val locations =
            when (val result = repository.listLocations()) {
                is Outcome.Failure -> return result
                is Outcome.Success -> result.value
            }
        locations.firstOrNull()?.let { return Outcome.success(it) }
        val created =
            StorageLocation(id = LocationId(idGenerator.newId()), name = DEFAULT_LOCATION_NAME)
        when (val saved = repository.saveLocation(created, "OTHER", 0)) {
            is Outcome.Failure -> return saved
            is Outcome.Success -> return Outcome.success(created)
        }
    }

    private suspend fun replay(
        entry: InventoryLedgerEntry,
        key: String,
    ): Outcome<ReceiptResult> {
        val batch =
            when (val result = repository.findBatch(entry.batchId.value)) {
                is Outcome.Failure -> return result
                is Outcome.Success ->
                    result.value
                        ?: return Outcome.failure(CulinaryError.NotFound("inventory_batch", entry.batchId.value))
            }
        val job =
            when (val result = repository.latestPrintJob(REFERENCE_TYPE, entry.batchId.value)) {
                is Outcome.Failure -> null
                is Outcome.Success -> result.value
            }
        return Outcome.success(
            ReceiptResult(batch = batch, ledger = entry, printJob = job, idempotentReplay = true),
        )
    }

    private fun invalid(
        field: String,
        rule: String,
    ): Outcome<Nothing> = Outcome.failure(CulinaryError.Validation(field = field, rule = rule))

    companion object {
        const val REFERENCE_TYPE = "stock_receipt"
        const val DEFAULT_LOCATION_NAME = "默认储藏"
    }
}

/**
 * 采购入库（v9）：多行待确认入库单。
 *
 * 确认前不写任何库存；确认时整单在一个事务内逐行入库，
 * 任一行失败整单回滚，不会出现只入一半的采购收货。
 */
class RecordPurchaseReceiptUseCase(
    private val recordReceipt: RecordArchiveReceiptUseCase,
    private val transactionRunner: TransactionRunner,
    private val idGenerator: IdGenerator,
) {
    suspend operator fun invoke(
        draftId: String,
        lines: List<ArchiveReceiptInput>,
        note: String? = null,
    ): Outcome<List<ReceiptResult>> {
        if (lines.isEmpty()) {
            return Outcome.failure(
                CulinaryError.Validation(field = "lines", rule = "purchase receipt needs at least one line"),
            )
        }
        val draftKey = draftId.takeIf { it.isNotBlank() } ?: idGenerator.newId()
        return transactionRunner
            .write {
                val results = mutableListOf<ReceiptResult>()
                lines.forEachIndexed { index, line ->
                    val key = "purchase:$draftKey:$index"
                    when (
                        val result =
                            recordReceipt.execute(
                                line.copy(note = line.note ?: note),
                                key,
                                index,
                            )
                    ) {
                        is Outcome.Failure -> return@write result
                        is Outcome.Success -> results += result.value
                    }
                }
                Outcome.success(results.toList())
            }.flatMap { it }
    }
}

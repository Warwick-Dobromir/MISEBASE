package com.culinary.core.domain.usecase.inventory

import com.culinary.core.common.Clock
import com.culinary.core.common.CulinaryError
import com.culinary.core.common.IdGenerator
import com.culinary.core.common.Outcome
import com.culinary.core.common.flatMap
import com.culinary.core.domain.model.ArchiveCategory
import com.culinary.core.domain.model.ArchiveLibrary
import com.culinary.core.domain.model.FreezeRecord
import com.culinary.core.domain.model.InventoryBatchRecord
import com.culinary.core.domain.model.InventoryLedgerEntry
import com.culinary.core.domain.model.InventoryRules
import com.culinary.core.domain.model.LedgerEntryId
import com.culinary.core.domain.model.LedgerEntryType
import com.culinary.core.domain.model.NewStocktake
import com.culinary.core.domain.model.NewStocktakeLine
import com.culinary.core.domain.model.PrintJobRecord
import com.culinary.core.domain.model.PrintKind
import com.culinary.core.domain.model.PrintStatus
import com.culinary.core.domain.model.RecycleRow
import com.culinary.core.domain.model.Stocktake
import com.culinary.core.domain.model.StocktakeLine
import com.culinary.core.domain.model.StocktakeScope
import com.culinary.core.domain.model.StocktakeStatus
import com.culinary.core.domain.model.TemporaryStatus
import com.culinary.core.domain.model.TemporaryStockRow
import com.culinary.core.domain.repository.ArchiveCategoryRepository
import com.culinary.core.domain.repository.InventoryArchiveRepository
import com.culinary.core.domain.transaction.TransactionRunner
import java.time.Duration

/** 冻结批次（v9）：写冻结记录 + 批次状态 + 零数量变动流水；冻结批次不允许出库。 */
class FreezeBatchUseCase(
    private val repository: InventoryArchiveRepository,
    private val transactionRunner: TransactionRunner,
    private val idGenerator: IdGenerator,
    private val clock: Clock,
) {
    suspend operator fun invoke(
        batchId: String,
        reason: String,
        operator: String? = null,
    ): Outcome<Unit> {
        if (reason.isBlank()) {
            return Outcome.failure(CulinaryError.Validation(field = "reason", rule = "freeze reason is required"))
        }
        return transactionRunner
            .write {
                val batch =
                    when (val result = repository.findBatch(batchId)) {
                        is Outcome.Failure -> return@write result
                        is Outcome.Success ->
                            result.value
                                ?: return@write Outcome.failure(
                                    CulinaryError.NotFound("inventory_batch", batchId),
                                )
                    }
                if (batch.isRecycled) {
                    return@write Outcome.failure(
                        CulinaryError.Validation(field = "batchId", rule = "recycled batch cannot be frozen"),
                    )
                }
                val at = clock.now()
                when (val frozen = repository.freezeBatch(batchId, reason.trim(), operator, at)) {
                    is Outcome.Failure -> return@write frozen
                    is Outcome.Success -> Unit
                }
                val entry =
                    InventoryLedgerEntry(
                        id = LedgerEntryId(idGenerator.newId()),
                        batchId =
                            com.culinary.core.domain.model
                                .BatchId(batchId),
                        productId = null,
                        locationId = batch.locationId,
                        type = LedgerEntryType.FREEZE,
                        stockUnit = batch.unit,
                        onHandDelta = 0.0,
                        reservedDelta = 0.0,
                        onHandAfter = batch.onHand,
                        reservedAfter = batch.reserved,
                        idempotencyKey = "freeze:$batchId:${at.toEpochMilli()}",
                        occurredAt = at,
                        referenceType = "inventory_batch",
                        referenceId = batchId,
                        note = reason.trim(),
                        archiveId = batch.archiveId,
                        operator = operator,
                    )
                when (val appended = repository.appendLedger(entry)) {
                    is Outcome.Failure -> return@write appended
                    is Outcome.Success -> Outcome.success(Unit)
                }
            }.flatMap { it }
    }
}

/** 解冻批次（v9）：必须填写原因，保留冻结与解冻历史。 */
class UnfreezeBatchUseCase(
    private val repository: InventoryArchiveRepository,
    private val transactionRunner: TransactionRunner,
    private val idGenerator: IdGenerator,
    private val clock: Clock,
) {
    suspend operator fun invoke(
        batchId: String,
        reason: String,
        operator: String? = null,
    ): Outcome<Unit> {
        if (reason.isBlank()) {
            return Outcome.failure(CulinaryError.Validation(field = "reason", rule = "unfreeze reason is required"))
        }
        return transactionRunner
            .write {
                val batch =
                    when (val result = repository.findBatch(batchId)) {
                        is Outcome.Failure -> return@write result
                        is Outcome.Success ->
                            result.value
                                ?: return@write Outcome.failure(
                                    CulinaryError.NotFound("inventory_batch", batchId),
                                )
                    }
                if (!batch.isFrozen) {
                    return@write Outcome.failure(
                        CulinaryError.Validation(field = "batchId", rule = "batch is not frozen"),
                    )
                }
                val at = clock.now()
                when (val unfrozen = repository.unfreezeBatch(batchId, reason.trim(), operator, at)) {
                    is Outcome.Failure -> return@write unfrozen
                    is Outcome.Success -> Unit
                }
                val entry =
                    InventoryLedgerEntry(
                        id = LedgerEntryId(idGenerator.newId()),
                        batchId =
                            com.culinary.core.domain.model
                                .BatchId(batchId),
                        productId = null,
                        locationId = batch.locationId,
                        type = LedgerEntryType.UNFREEZE,
                        stockUnit = batch.unit,
                        onHandDelta = 0.0,
                        reservedDelta = 0.0,
                        onHandAfter = batch.onHand,
                        reservedAfter = batch.reserved,
                        idempotencyKey = "unfreeze:$batchId:${at.toEpochMilli()}",
                        occurredAt = at,
                        referenceType = "inventory_batch",
                        referenceId = batchId,
                        note = reason.trim(),
                        archiveId = batch.archiveId,
                        operator = operator,
                    )
                when (val appended = repository.appendLedger(entry)) {
                    is Outcome.Failure -> return@write appended
                    is Outcome.Success -> Outcome.success(Unit)
                }
            }.flatMap { it }
    }
}

/** 批次库位转移（v9）：不新建批次，写零数量变动流水并保留转出/转入。 */
class TransferBatchLocationUseCase(
    private val repository: InventoryArchiveRepository,
    private val transactionRunner: TransactionRunner,
    private val idGenerator: IdGenerator,
    private val clock: Clock,
) {
    suspend operator fun invoke(
        batchId: String,
        toLocationId: String,
        note: String? = null,
    ): Outcome<Unit> =
        transactionRunner
            .write {
                val batch =
                    when (val result = repository.findBatch(batchId)) {
                        is Outcome.Failure -> return@write result
                        is Outcome.Success ->
                            result.value
                                ?: return@write Outcome.failure(
                                    CulinaryError.NotFound("inventory_batch", batchId),
                                )
                    }
                if (batch.locationId.value == toLocationId) {
                    return@write Outcome.failure(
                        CulinaryError.Validation(field = "locationId", rule = "target location is the same"),
                    )
                }
                val at = clock.now()
                when (val moved = repository.transferBatch(batchId, toLocationId, note, at)) {
                    is Outcome.Failure -> return@write moved
                    is Outcome.Success -> Unit
                }
                val entry =
                    InventoryLedgerEntry(
                        id = LedgerEntryId(idGenerator.newId()),
                        batchId =
                            com.culinary.core.domain.model
                                .BatchId(batchId),
                        productId = null,
                        locationId =
                            com.culinary.core.domain.model
                                .LocationId(toLocationId),
                        type = LedgerEntryType.LOCATION_TRANSFER,
                        stockUnit = batch.unit,
                        onHandDelta = 0.0,
                        reservedDelta = 0.0,
                        onHandAfter = batch.onHand,
                        reservedAfter = batch.reserved,
                        idempotencyKey = "transfer:$batchId:${at.toEpochMilli()}",
                        occurredAt = at,
                        referenceType = "inventory_batch",
                        referenceId = batchId,
                        note = note,
                        archiveId = batch.archiveId,
                    )
                when (val appended = repository.appendLedger(entry)) {
                    is Outcome.Failure -> return@write appended
                    is Outcome.Success -> Outcome.success(Unit)
                }
            }.flatMap { it }
}

/**
 * 盘点（v9）：先快照生成报告，确认后才调整库存。
 *
 * 冻结批次可盘点，但盘点不会解除冻结。
 */
class StocktakeUseCases(
    private val repository: InventoryArchiveRepository,
    private val categoryRepository: ArchiveCategoryRepository,
    private val transactionRunner: TransactionRunner,
    private val idGenerator: IdGenerator,
    private val clock: Clock,
) {
    /** 建单：把范围内所有在库批次快照为账面数。 */
    suspend fun create(
        scope: StocktakeScope,
        scopeRef: String? = null,
        note: String? = null,
    ): Outcome<Stocktake> {
        val batches =
            when (val result = repository.listLiveBatches()) {
                is Outcome.Failure -> return result
                is Outcome.Success -> result.value
            }
        val filtered =
            when (scope) {
                StocktakeScope.ALL -> batches
                StocktakeScope.ARCHIVE -> batches.filter { it.archiveId == scopeRef }
                StocktakeScope.LOCATION -> batches.filter { it.locationId.value == scopeRef }
                StocktakeScope.CATEGORY ->
                    batches.filter { batch ->
                        val archiveId = batch.archiveId
                        val categoryId = scopeRef
                        archiveId != null && categoryId != null && belongsToCategory(archiveId, categoryId)
                    }
            }
        if (filtered.isEmpty()) {
            return Outcome.failure(
                CulinaryError.Validation(field = "scope", rule = "no batches in the selected scope"),
            )
        }
        val label =
            when (scope) {
                StocktakeScope.ALL -> "全部库存"
                StocktakeScope.ARCHIVE -> filtered.first().archiveName
                StocktakeScope.LOCATION -> filtered.first().locationName ?: "指定库位"
                StocktakeScope.CATEGORY -> "指定分类"
            }
        return repository.createStocktake(
            NewStocktake(
                scope = scope,
                scopeRef = scopeRef,
                scopeLabel = label,
                note = note,
                createdAt = clock.now(),
                lines =
                    filtered.mapIndexed { index, batch ->
                        NewStocktakeLine(
                            lineNo = index + 1,
                            batchId = batch.id,
                            archiveId = batch.archiveId.orEmpty(),
                            archiveName = batch.archiveName,
                            batchCode = batch.batchCode,
                            locationName = batch.locationName,
                            bookQty = batch.onHand,
                            unitId = batch.unit.id.value,
                            unitSymbol = batch.unit.symbol,
                            frozen = batch.isFrozen,
                        )
                    },
            ),
        )
    }

    private suspend fun belongsToCategory(
        archiveId: String,
        categoryId: String,
    ): Boolean {
        val archive =
            when (val result = repository.findArchive(archiveId)) {
                is Outcome.Failure -> null
                is Outcome.Success -> result.value
            } ?: return false
        val categories =
            when (val result = categoryRepository.listByLibrary(ArchiveLibrary.INVENTORY)) {
                is Outcome.Failure -> emptyList<ArchiveCategory>()
                is Outcome.Success -> result.value
            }
        val ids = mutableSetOf(categoryId)
        var changed = true
        while (changed) {
            changed = false
            categories.forEach { if (it.parentId in ids && ids.add(it.id)) changed = true }
        }
        return archive.categoryId in ids
    }

    suspend fun counted(
        stocktakeId: String,
        lineId: String,
        countedQty: Double,
    ): Outcome<Stocktake> {
        if (!countedQty.isFinite() || countedQty < 0.0) {
            return Outcome.failure(
                CulinaryError.Validation(field = "countedQty", rule = "must be a finite non negative number"),
            )
        }
        return repository.updateStocktakeCounted(stocktakeId, lineId, countedQty)
    }

    /** 确认盘点：逐行差额写调整流水；全部成功才标记确认。 */
    suspend fun confirm(stocktakeId: String): Outcome<Stocktake> =
        transactionRunner
            .write {
                val stocktake =
                    when (val result = repository.findStocktake(stocktakeId)) {
                        is Outcome.Failure -> return@write result
                        is Outcome.Success ->
                            result.value
                                ?: return@write Outcome.failure(
                                    CulinaryError.NotFound("inventory_stocktake", stocktakeId),
                                )
                    }
                if (stocktake.status == StocktakeStatus.CONFIRMED) {
                    return@write Outcome.success(stocktake)
                }
                val at = clock.now()
                for (line in stocktake.lines) {
                    if (!line.hasDiff) continue
                    val batch =
                        when (val result = repository.findBatch(line.batchId)) {
                            is Outcome.Failure -> return@write result
                            is Outcome.Success ->
                                result.value
                                    ?: return@write Outcome.failure(
                                        CulinaryError.NotFound("inventory_batch", line.batchId),
                                    )
                        }
                    val delta = line.countedQty - line.bookQty
                    val entry =
                        InventoryLedgerEntry(
                            id = LedgerEntryId(idGenerator.newId()),
                            batchId =
                                com.culinary.core.domain.model
                                    .BatchId(line.batchId),
                            productId = null,
                            locationId = batch.locationId,
                            type = if (delta > 0) LedgerEntryType.ADJUSTMENT_IN else LedgerEntryType.ADJUSTMENT_OUT,
                            stockUnit = batch.unit,
                            onHandDelta = delta,
                            reservedDelta = 0.0,
                            onHandAfter = batch.onHand + delta,
                            reservedAfter = batch.reserved,
                            idempotencyKey = "stocktake:$stocktakeId:${line.id}",
                            occurredAt = at,
                            referenceType = "inventory_stocktake",
                            referenceId = stocktakeId,
                            note = "盘点调整",
                            archiveId = batch.archiveId,
                        )
                    when (val appended = repository.appendLedger(entry)) {
                        is Outcome.Failure -> return@write appended
                        is Outcome.Success -> Unit
                    }
                }
                repository.confirmStocktake(stocktakeId, at)
            }.flatMap { it }

    suspend fun get(id: String): Outcome<Stocktake?> = repository.findStocktake(id)

    suspend fun recent(limit: Int = 20): Outcome<List<Stocktake>> = repository.recentStocktakes(limit)

    fun linesOf(stocktake: Stocktake): List<StocktakeLine> = stocktake.lines
}

/** 回收站（v9）：归零批次保留 30 天，可恢复；到期只标记为已报废，不删除历史。 */
class BatchRecycleUseCases(
    private val repository: InventoryArchiveRepository,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) {
    suspend fun list(): Outcome<List<RecycleRow>> {
        val now = clock.now()
        val retentionDays = repository.readRules().recycleRetentionDays
        val batches =
            when (val result = repository.listRecycledBatches()) {
                is Outcome.Failure -> return result
                is Outcome.Success -> result.value
            }
        return Outcome.success(
            batches.map { batch ->
                val since = batch.recycledAt ?: now
                val days = Duration.between(since, now).toDays().coerceAtLeast(0)
                RecycleRow(
                    batch = batch,
                    daysInRecycle = days,
                    daysLeft = (retentionDays - days).coerceAtLeast(0),
                )
            },
        )
    }

    /** 恢复：重新校验批次状态；余额保持原值（恢复历史批次 ≠ 恢复实物库存）。 */
    suspend fun restore(batchId: String): Outcome<Unit> =
        transactionRunner
            .write {
                val batch =
                    when (val result = repository.findBatch(batchId)) {
                        is Outcome.Failure -> return@write result
                        is Outcome.Success ->
                            result.value
                                ?: return@write Outcome.failure(
                                    CulinaryError.NotFound("inventory_batch", batchId),
                                )
                    }
                if (!batch.isRecycled) {
                    return@write Outcome.failure(
                        CulinaryError.Validation(field = "batchId", rule = "batch is not in recycle bin"),
                    )
                }
                val now = clock.now()
                val retentionDays = repository.readRules().recycleRetentionDays
                val since = batch.recycledAt ?: now
                val days = Duration.between(since, now).toDays()
                if (days > retentionDays) {
                    return@write Outcome.failure(
                        CulinaryError.Validation(field = "batchId", rule = "recycle retention expired"),
                    )
                }
                when (val restored = repository.restoreBatch(batchId, now)) {
                    is Outcome.Failure -> return@write restored
                    is Outcome.Success -> Outcome.success(Unit)
                }
            }.flatMap { it }

    /** 启动维护：超过保留期的回收批次标记为已报废，不删除任何历史。 */
    suspend fun purgeExpired(): Outcome<Int> {
        val now = clock.now()
        val retentionDays = repository.readRules().recycleRetentionDays
        val batches =
            when (val result = repository.listRecycledBatches()) {
                is Outcome.Failure -> return result
                is Outcome.Success -> result.value
            }
        var purged = 0
        batches.forEach { batch ->
            val since = batch.recycledAt ?: return@forEach
            if (Duration.between(since, now).toDays() > retentionDays) {
                if (repository.purgeBatch(batch.id, now) is Outcome.Success) purged += 1
            }
        }
        return Outcome.success(purged)
    }
}

/** 临时库存（v9）：15 天提醒、20 天待处理；不自动删除。 */
class TemporaryStockUseCases(
    private val repository: InventoryArchiveRepository,
    private val clock: Clock,
) {
    suspend fun list(): Outcome<List<TemporaryStockRow>> {
        val now = clock.now()
        val rules = repository.readRules()
        val batches =
            when (val result = repository.listTemporaryBatches()) {
                is Outcome.Failure -> return result
                is Outcome.Success -> result.value
            }
        return Outcome.success(
            batches.map { batch ->
                val since = batch.temporarySince ?: batch.receivedAt
                val days = Duration.between(since, now).toDays().coerceAtLeast(0)
                val status =
                    when {
                        days >= rules.temporaryPendingDays -> TemporaryStatus.PENDING
                        days >= rules.temporaryRemindDays -> TemporaryStatus.REMIND
                        else -> TemporaryStatus.NORMAL
                    }
                TemporaryStockRow(batch = batch, days = days, status = status)
            },
        )
    }

    suspend fun markFormal(batchId: String): Outcome<Unit> = repository.markTemporary(batchId, false, clock.now())
}

/** 库存规则（v9）：读取即生效；设置页写入后影响临时库存与回收站判定。 */
class InventoryRulesUseCases(
    private val repository: InventoryArchiveRepository,
) {
    suspend fun read(): InventoryRules = repository.readRules()

    suspend fun update(rules: InventoryRules): Outcome<Unit> = repository.writeRules(rules)
}

/** 打印任务（v9）：失败只更新任务状态，重打不重复扣减、不重复写流水。 */
class PrintJobUseCases(
    private val repository: InventoryArchiveRepository,
    private val clock: Clock,
) {
    suspend fun latest(
        refType: String,
        refId: String,
    ): Outcome<PrintJobRecord?> = repository.latestPrintJob(refType, refId)

    suspend fun markPrinted(jobId: String): Outcome<PrintJobRecord> =
        repository.updatePrintJob(jobId, PrintStatus.PRINTED, null, clock.now())

    suspend fun markFailed(
        jobId: String,
        error: String?,
    ): Outcome<PrintJobRecord> = repository.updatePrintJob(jobId, PrintStatus.FAILED, error, clock.now())

    suspend fun reprint(jobId: String): Outcome<PrintJobRecord> =
        repository.updatePrintJob(jobId, PrintStatus.PENDING, null, clock.now(), countReprint = true)

    suspend fun recent(limit: Int = 20): Outcome<List<PrintJobRecord>> = repository.recentPrintJobs(limit)
}

/** 库存流水（v9）：按档案/批次/单据查询；类型与日期筛选在读取时完成。 */
class InventoryLedgerUseCases(
    private val repository: InventoryArchiveRepository,
) {
    suspend fun forArchive(archiveId: String): Outcome<List<InventoryLedgerEntry>> =
        repository.ledgerForArchive(archiveId)

    suspend fun forBatch(batchId: String): Outcome<List<InventoryLedgerEntry>> = repository.ledgerForBatch(batchId)

    suspend fun recent(limit: Int = 50): Outcome<List<InventoryLedgerEntry>> = repository.recentLedger(limit)

    suspend fun find(id: String): Outcome<InventoryLedgerEntry?> = repository.findLedger(id)

    suspend fun forReference(
        refType: String,
        refId: String,
    ): Outcome<List<InventoryLedgerEntry>> = repository.ledgerByReference(refType, refId)
}

/** 冻结管理列表：读取当前冻结批次与冻结原因。 */
class ListFrozenBatchesUseCase(
    private val repository: InventoryArchiveRepository,
) {
    suspend operator fun invoke(): Outcome<List<InventoryBatchRecord>> {
        val batches =
            when (val result = repository.listLiveBatches()) {
                is Outcome.Failure -> return result
                is Outcome.Success -> result.value
            }
        return Outcome.success(batches.filter { it.isFrozen }.sortedBy { it.frozenAt })
    }
}

/** 单批次冻结历史（详情页）。 */
class BatchFreezeHistoryUseCase(
    private val repository: InventoryArchiveRepository,
) {
    suspend operator fun invoke(batchId: String): Outcome<List<FreezeRecord>> = repository.freezeRecords(batchId)
}

/** 创建打印任务（打印预览页在真正打印前调用）。 */
class CreatePrintJobUseCase(
    private val repository: InventoryArchiveRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(
        kind: PrintKind,
        refType: String,
        refId: String,
        paperWidthMm: Int = 80,
    ): Outcome<PrintJobRecord> = repository.createPrintJob(kind, refType, refId, paperWidthMm, clock.now())
}

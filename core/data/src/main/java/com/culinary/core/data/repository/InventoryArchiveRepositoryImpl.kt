package com.culinary.core.data.repository

import com.culinary.core.common.Clock
import com.culinary.core.common.CulinaryError
import com.culinary.core.common.DomainDetail
import com.culinary.core.common.ErrorCode
import com.culinary.core.common.IdGenerator
import com.culinary.core.common.Outcome
import com.culinary.core.data.mapper.InventoryArchiveMapper
import com.culinary.core.data.mapper.InventoryMapper
import com.culinary.core.data.mapper.StorageLocationMapper
import com.culinary.core.data.mapper.UnitMapper
import com.culinary.core.data.transaction.DbErrorMapper
import com.culinary.core.database.dao.AppMetadataDao
import com.culinary.core.database.dao.InventoryArchiveDao
import com.culinary.core.database.dao.InventoryArchiveProductDao
import com.culinary.core.database.dao.InventoryBatchDao
import com.culinary.core.database.dao.InventoryIssueDao
import com.culinary.core.database.dao.InventoryLedgerDao
import com.culinary.core.database.dao.InventoryLifecycleDao
import com.culinary.core.database.dao.InventoryStocktakeDao
import com.culinary.core.database.dao.ProductDao
import com.culinary.core.database.dao.StorageLocationDao
import com.culinary.core.database.dao.UnitDao
import com.culinary.core.database.entity.AppMetadataEntity
import com.culinary.core.database.entity.InventoryArchiveEntity
import com.culinary.core.database.entity.InventoryArchiveProductEntity
import com.culinary.core.database.entity.InventoryBatchEntity
import com.culinary.core.database.entity.InventoryFreezeRecordEntity
import com.culinary.core.database.entity.InventoryIssueSlipEntity
import com.culinary.core.database.entity.InventoryIssueSlipLineEntity
import com.culinary.core.database.entity.InventoryLedgerEntity
import com.culinary.core.database.entity.InventoryLocationTransferEntity
import com.culinary.core.database.entity.InventoryPrintJobEntity
import com.culinary.core.database.entity.InventoryStocktakeEntity
import com.culinary.core.database.entity.InventoryStocktakeLineEntity
import com.culinary.core.database.model.BatchStatusDb
import com.culinary.core.database.model.EntityStatusDb
import com.culinary.core.database.model.FreezeActionDb
import com.culinary.core.database.model.IssueSlipStatusDb
import com.culinary.core.database.model.PrintJobStatusDb
import com.culinary.core.database.model.StocktakeStatusDb
import com.culinary.core.database.model.StorageLocationKindDb
import com.culinary.core.domain.model.ArchiveProductLink
import com.culinary.core.domain.model.FreezeRecord
import com.culinary.core.domain.model.IngredientId
import com.culinary.core.domain.model.InventoryArchive
import com.culinary.core.domain.model.InventoryBatchRecord
import com.culinary.core.domain.model.InventoryLedgerEntry
import com.culinary.core.domain.model.InventoryRules
import com.culinary.core.domain.model.IssueSlip
import com.culinary.core.domain.model.IssueType
import com.culinary.core.domain.model.LocationId
import com.culinary.core.domain.model.LocationTransferRecord
import com.culinary.core.domain.model.NewArchiveBatch
import com.culinary.core.domain.model.NewIssueSlip
import com.culinary.core.domain.model.NewStocktake
import com.culinary.core.domain.model.PrintJobRecord
import com.culinary.core.domain.model.PrintKind
import com.culinary.core.domain.model.PrintStatus
import com.culinary.core.domain.model.Stocktake
import com.culinary.core.domain.model.StorageLocation
import com.culinary.core.domain.repository.InventoryArchiveRepository
import com.culinary.core.domain.rule.common.NameText
import kotlinx.coroutines.CancellationException
import java.time.Instant
import javax.inject.Inject
import kotlin.math.abs
import com.culinary.core.domain.model.Unit as UnitModel

/**
 * 库存档案化 Repository 的 Room 实现（v9）。
 *
 * 数量变化仍然只经过 [appendLedger]：先查幂等键 → 校验余额/可用量 → 原子调整批次 →
 * 追加流水；调整失败不写流水，调用方须把失败视为"整笔未发生"。
 * 方法本身不开事务，事务边界由 Use Case 通过 `TransactionRunner` 控制。
 */
@Suppress("TooManyFunctions", "TooGenericExceptionCaught") // 端口方法多、错误边界统一映射
class InventoryArchiveRepositoryImpl
    @Inject
    constructor(
        private val archiveDao: InventoryArchiveDao,
        private val archiveProductDao: InventoryArchiveProductDao,
        private val batchDao: InventoryBatchDao,
        private val ledgerDao: InventoryLedgerDao,
        private val lifecycleDao: InventoryLifecycleDao,
        private val issueDao: InventoryIssueDao,
        private val stocktakeDao: InventoryStocktakeDao,
        private val locationDao: StorageLocationDao,
        private val productDao: ProductDao,
        private val unitDao: UnitDao,
        private val metadataDao: AppMetadataDao,
        private val idGenerator: IdGenerator,
        private val clock: Clock,
    ) : InventoryArchiveRepository {
        // ---- 档案 ----

        override suspend fun listArchives(): Outcome<List<InventoryArchive>> =
            read { archiveDao.listActive().map { toArchive(it) } }

        override suspend fun findArchive(id: String): Outcome<InventoryArchive?> =
            read { archiveDao.findById(id)?.let { toArchive(it) } }

        override suspend fun saveArchive(archive: InventoryArchive): Outcome<String> =
            read {
                val now = clock.now().toEpochMilli()
                val existing = archiveDao.findById(archive.id)
                val normalizedName = NameText.normalize(archive.name)
                if (existing == null) {
                    archiveDao.insert(
                        InventoryArchiveMapper.toEntity(
                            archive = archive,
                            normalizedName = normalizedName,
                            createdAt = now,
                            updatedAt = now,
                            deletedAt = null,
                            revision = 1,
                        ),
                    )
                } else {
                    archiveDao.update(
                        InventoryArchiveMapper.toEntity(
                            archive = archive,
                            normalizedName = normalizedName,
                            createdAt = existing.createdAt,
                            updatedAt = now,
                            deletedAt = existing.deletedAt,
                            revision = existing.revision + 1,
                        ),
                    )
                }
                archive.id
            }

        override suspend fun softDeleteArchive(id: String, at: Instant): Outcome<Unit> =
            guard {
                val existing =
                    archiveDao.findById(id)
                        ?: return@guard Outcome.failure(
                            CulinaryError.NotFound(entity = "inventory_archive", id = id),
                        )
                val stamp = at.toEpochMilli()
                archiveDao.update(
                    existing.copy(
                        status = EntityStatusDb.ARCHIVED,
                        deletedAt = stamp,
                        updatedAt = stamp,
                        revision = existing.revision + 1,
                    ),
                )
                Outcome.success(Unit)
            }

        override suspend fun findArchiveForSauce(sauceId: String): Outcome<InventoryArchive?> =
            read { archiveDao.findBySauce(sauceId)?.let { toArchive(it) } }

        override suspend fun archiveLinks(archiveId: String): Outcome<List<ArchiveProductLink>> =
            read {
                archiveProductDao.listByArchive(archiveId).mapNotNull { link ->
                    val product = productDao.findById(link.productId) ?: return@mapNotNull null
                    val stockUnit = loadUnit(product.stockUnitId) ?: unitNotFound(product.stockUnitId)
                    val contentUnit = loadUnit(product.contentUnitId) ?: unitNotFound(product.contentUnitId)
                    InventoryArchiveMapper.toDomain(link, product, stockUnit, contentUnit)
                }
            }

        override suspend fun linkProduct(
            archiveId: String,
            productId: String,
            conversionRatio: Double,
            isDefault: Boolean,
        ): Outcome<Unit> =
            guard {
                if (!conversionRatio.isFinite() || conversionRatio <= 0.0) {
                    return@guard Outcome.failure(
                        CulinaryError.Validation(field = "conversionRatio", rule = "must be finite and > 0"),
                    )
                }
                val archive =
                    archiveDao.findById(archiveId)
                        ?: return@guard Outcome.failure(
                            CulinaryError.NotFound(entity = "inventory_archive", id = archiveId),
                        )
                productDao.findById(productId)
                    ?: return@guard Outcome.failure(
                        CulinaryError.NotFound(entity = "product", id = productId),
                    )
                val existing = archiveProductDao.findByProduct(productId)
                when {
                    existing == null ->
                        archiveProductDao.insert(
                            InventoryArchiveProductEntity(
                                id = idGenerator.newId(),
                                archiveId = archive.id,
                                productId = productId,
                                conversionRatio = conversionRatio,
                                isDefault = isDefault,
                                createdAt = clock.now().toEpochMilli(),
                            ),
                        )

                    existing.archiveId == archiveId ->
                        archiveProductDao.update(
                            existing.copy(conversionRatio = conversionRatio, isDefault = isDefault),
                        )

                    // 测试版约束：一个商品只归属一个档案，换绑必须先解绑。
                    else ->
                        return@guard Outcome.failure(
                            CulinaryError.Conflict(entity = "inventory_archive_product"),
                        )
                }
                Outcome.success(Unit)
            }

        override suspend fun unlinkProduct(archiveId: String, productId: String): Outcome<Unit> =
            read { archiveProductDao.delete(archiveId, productId) }

        override suspend fun archiveForProduct(productId: String): Outcome<String?> =
            read { archiveProductDao.findByProduct(productId)?.archiveId }

        override suspend fun archiveForIngredient(ingredientId: IngredientId): Outcome<String?> =
            read { archiveProductDao.findArchiveIdByIngredient(ingredientId.value) }

        // ---- 批次 ----

        override suspend fun listBatches(archiveId: String): Outcome<List<InventoryBatchRecord>> =
            read { toBatchRecords(batchDao.listByArchive(archiveId)) }

        override suspend fun listLiveBatches(): Outcome<List<InventoryBatchRecord>> =
            read { toBatchRecords(batchDao.listLive()) }

        override suspend fun findBatch(batchId: String): Outcome<InventoryBatchRecord?> =
            read { batchDao.findById(batchId)?.let { toBatchRecords(listOf(it)).first() } }

        override suspend fun listFifoBatches(archiveId: String): Outcome<List<InventoryBatchRecord>> =
            read { toBatchRecords(batchDao.listFifoAvailable(archiveId)) }

        override suspend fun listRecycledBatches(): Outcome<List<InventoryBatchRecord>> =
            read { toBatchRecords(batchDao.listRecycled()) }

        override suspend fun listTemporaryBatches(): Outcome<List<InventoryBatchRecord>> =
            read { toBatchRecords(batchDao.listTemporary()) }

        override suspend fun countBatches(archiveId: String): Outcome<Int> =
            read { batchDao.countByArchive(archiveId) }

        override suspend fun createBatch(batch: NewArchiveBatch): Outcome<String> =
            read {
                val now = clock.now().toEpochMilli()
                val id = idGenerator.newId()
                batchDao.insert(
                    InventoryBatchEntity(
                        id = id,
                        productId = batch.productId,
                        archiveId = batch.archiveId,
                        locationId = batch.locationId.value,
                        batchCode = batch.batchCode,
                        receivedAt = batch.receivedAt.toEpochMilli(),
                        productionDate = batch.productionDate?.toString(),
                        expiryDate = batch.expiresOn?.toString(),
                        quantityOnHand = 0.0,
                        quantityReserved = 0.0,
                        stockUnitId = batch.stockUnit.id.value,
                        status = BatchStatusDb.ACTIVE,
                        isTemporary = batch.isTemporary,
                        temporarySince = if (batch.isTemporary) now else null,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
                id
            }

        // ---- 流水 ----

        override suspend fun appendLedger(entry: InventoryLedgerEntry): Outcome<Boolean> =
            guard { applyLedger(entry) }

        override suspend fun findLedgerByIdempotencyKey(key: String): Outcome<InventoryLedgerEntry?> =
            read { ledgerDao.findByIdempotencyKey(key)?.let { toLedger(it) } }

        override suspend fun ledgerForArchive(archiveId: String): Outcome<List<InventoryLedgerEntry>> =
            read { ledgerDao.listByArchive(archiveId).map { toLedger(it) } }

        override suspend fun ledgerForBatch(batchId: String): Outcome<List<InventoryLedgerEntry>> =
            read { ledgerDao.listByBatch(batchId).map { toLedger(it) } }

        override suspend fun recentLedger(limit: Int): Outcome<List<InventoryLedgerEntry>> =
            read { ledgerDao.listRecent(limit).map { toLedger(it) } }

        override suspend fun findLedger(id: String): Outcome<InventoryLedgerEntry?> =
            read { ledgerDao.findById(id)?.let { toLedger(it) } }

        override suspend fun ledgerByReference(
            refType: String,
            refId: String,
        ): Outcome<List<InventoryLedgerEntry>> =
            read { ledgerDao.listByReference(refType, refId).map { toLedger(it) } }

        // ---- 冻结 / 解冻 / 转移 ----

        override suspend fun freezeBatch(
            batchId: String,
            reason: String,
            operator: String?,
            at: Instant,
        ): Outcome<Unit> =
            guard {
                val stamp = at.toEpochMilli()
                val rows = batchDao.freeze(batchId, stamp, reason)
                if (rows != 1) {
                    return@guard Outcome.failure(
                        CulinaryError.NotFound(entity = "inventory_batch", id = batchId),
                    )
                }
                lifecycleDao.insertFreezeRecord(
                    InventoryFreezeRecordEntity(
                        id = idGenerator.newId(),
                        batchId = batchId,
                        action = FreezeActionDb.FREEZE,
                        reason = reason,
                        operator = operator,
                        occurredAt = stamp,
                    ),
                )
                Outcome.success(Unit)
            }

        override suspend fun unfreezeBatch(
            batchId: String,
            reason: String,
            operator: String?,
            at: Instant,
        ): Outcome<Unit> =
            guard {
                val stamp = at.toEpochMilli()
                val rows = batchDao.unfreeze(batchId, stamp)
                if (rows != 1) {
                    return@guard Outcome.failure(
                        CulinaryError.NotFound(entity = "inventory_batch", id = batchId),
                    )
                }
                lifecycleDao.insertFreezeRecord(
                    InventoryFreezeRecordEntity(
                        id = idGenerator.newId(),
                        batchId = batchId,
                        action = FreezeActionDb.UNFREEZE,
                        reason = reason,
                        operator = operator,
                        occurredAt = stamp,
                    ),
                )
                Outcome.success(Unit)
            }

        override suspend fun freezeRecords(batchId: String): Outcome<List<FreezeRecord>> =
            read { lifecycleDao.freezeRecords(batchId).map { InventoryArchiveMapper.toDomain(it) } }

        override suspend fun transferBatch(
            batchId: String,
            toLocationId: String,
            note: String?,
            at: Instant,
        ): Outcome<Unit> =
            guard {
                val batch =
                    batchDao.findById(batchId)
                        ?: return@guard Outcome.failure(
                            CulinaryError.NotFound(entity = "inventory_batch", id = batchId),
                        )
                val stamp = at.toEpochMilli()
                val rows = batchDao.updateLocation(batchId, toLocationId, stamp)
                if (rows != 1) {
                    return@guard Outcome.failure(CulinaryError.Conflict(entity = "inventory_batch"))
                }
                lifecycleDao.insertTransfer(
                    InventoryLocationTransferEntity(
                        id = idGenerator.newId(),
                        batchId = batchId,
                        fromLocationId = batch.locationId,
                        toLocationId = toLocationId,
                        quantity = batch.quantityOnHand,
                        note = note,
                        occurredAt = stamp,
                    ),
                )
                Outcome.success(Unit)
            }

        override suspend fun transfers(batchId: String): Outcome<List<LocationTransferRecord>> =
            read {
                lifecycleDao.transfers(batchId).map { transfer ->
                    InventoryArchiveMapper.toDomain(
                        entity = transfer,
                        fromLocationName = locationDao.findById(transfer.fromLocationId)?.name,
                        toLocationName = locationDao.findById(transfer.toLocationId)?.name,
                    )
                }
            }

        override suspend fun restoreBatch(batchId: String, at: Instant): Outcome<Unit> =
            guard {
                val rows = batchDao.restore(batchId, at.toEpochMilli())
                if (rows != 1) {
                    return@guard Outcome.failure(
                        CulinaryError.NotFound(entity = "inventory_batch", id = batchId),
                    )
                }
                Outcome.success(Unit)
            }

        override suspend fun purgeBatch(batchId: String, at: Instant): Outcome<Unit> =
            guard {
                val rows = batchDao.updateStatus(batchId, BatchStatusDb.DISCARDED, at.toEpochMilli())
                if (rows != 1) {
                    return@guard Outcome.failure(
                        CulinaryError.NotFound(entity = "inventory_batch", id = batchId),
                    )
                }
                Outcome.success(Unit)
            }

        override suspend fun markTemporary(batchId: String, temporary: Boolean, at: Instant): Outcome<Unit> =
            guard {
                val stamp = at.toEpochMilli()
                val rows = batchDao.setTemporary(
                    id = batchId,
                    temporary = temporary,
                    since = if (temporary) stamp else null,
                    at = stamp,
                )
                if (rows != 1) {
                    return@guard Outcome.failure(
                        CulinaryError.NotFound(entity = "inventory_batch", id = batchId),
                    )
                }
                Outcome.success(Unit)
            }

        // ---- 出库单 ----

        override suspend fun saveIssueSlip(slip: NewIssueSlip): Outcome<IssueSlip> =
            guard {
                val existing = issueDao.findByKey(slip.idempotencyKey)
                if (existing != null) {
                    return@guard Outcome.success(slipOf(existing))
                }
                val id = idGenerator.newId()
                val entity =
                    InventoryIssueSlipEntity(
                        id = id,
                        slipNo = slip.slipNo,
                        type = InventoryArchiveMapper.toDb(slip.type),
                        status = IssueSlipStatusDb.CONFIRMED,
                        recipeVersionId = slip.recipeVersionId,
                        recipeName = slip.recipeName,
                        totalLines = slip.lines.size,
                        idempotencyKey = slip.idempotencyKey,
                        note = slip.note,
                        operator = slip.operator,
                        occurredAt = slip.occurredAt.toEpochMilli(),
                    )
                issueDao.insertSlip(entity)
                val lines =
                    slip.lines.sortedBy { it.lineNo }.map { line ->
                        InventoryIssueSlipLineEntity(
                            id = idGenerator.newId(),
                            slipId = id,
                            lineNo = line.lineNo,
                            archiveId = line.archiveId,
                            archiveName = line.archiveName,
                            batchId = line.batchId,
                            batchCode = line.batchCode,
                            locationId = line.locationId.value,
                            locationName = line.locationName,
                            quantity = line.quantity,
                            unitId = line.unitId,
                            unitSymbol = line.unitSymbol,
                            role = InventoryArchiveMapper.toRoleString(line.role),
                        )
                    }
                if (lines.isNotEmpty()) {
                    issueDao.insertLines(lines)
                }
                Outcome.success(InventoryArchiveMapper.toDomain(entity, lines))
            }

        override suspend fun findIssueSlipByKey(key: String): Outcome<IssueSlip?> =
            read { issueDao.findByKey(key)?.let { slipOf(it) } }

        override suspend fun findIssueSlip(id: String): Outcome<IssueSlip?> =
            read { issueDao.findSlip(id)?.let { slipOf(it) } }

        override suspend fun recentIssueSlips(limit: Int): Outcome<List<IssueSlip>> =
            read { issueDao.recentSlips(limit).map { slipOf(it) } }

        override suspend fun issueTypeCounts(): Outcome<Map<IssueType, Int>> =
            read {
                issueDao.countsByType().associate { row ->
                    InventoryArchiveMapper.toDomain(row.type) to row.count
                }
            }

        // ---- 打印任务 ----

        override suspend fun createPrintJob(
            kind: PrintKind,
            refType: String,
            refId: String,
            paperWidthMm: Int,
            at: Instant,
        ): Outcome<PrintJobRecord> =
            read {
                val stamp = at.toEpochMilli()
                val entity =
                    InventoryPrintJobEntity(
                        id = idGenerator.newId(),
                        kind = InventoryArchiveMapper.toDb(kind),
                        refType = refType,
                        refId = refId,
                        status = PrintJobStatusDb.PENDING,
                        attempts = 0,
                        reprintCount = 0,
                        lastError = null,
                        paperWidthMm = paperWidthMm,
                        createdAt = stamp,
                        updatedAt = stamp,
                        printedAt = null,
                    )
                issueDao.insertPrintJob(entity)
                InventoryArchiveMapper.toDomain(entity)
            }

        override suspend fun updatePrintJob(
            id: String,
            status: PrintStatus,
            lastError: String?,
            at: Instant,
            countReprint: Boolean,
        ): Outcome<PrintJobRecord> =
            guard {
                val existing =
                    issueDao.findPrintJob(id)
                        ?: return@guard Outcome.failure(
                            CulinaryError.NotFound(entity = "inventory_print_job", id = id),
                        )
                val stamp = at.toEpochMilli()
                val entity =
                    existing.copy(
                        status = InventoryArchiveMapper.toDb(status),
                        lastError = lastError,
                        attempts = existing.attempts + 1,
                        reprintCount = existing.reprintCount + if (countReprint) 1 else 0,
                        updatedAt = stamp,
                        printedAt = if (status == PrintStatus.PRINTED) stamp else existing.printedAt,
                    )
                issueDao.updatePrintJob(entity)
                Outcome.success(InventoryArchiveMapper.toDomain(entity))
            }

        override suspend fun latestPrintJob(refType: String, refId: String): Outcome<PrintJobRecord?> =
            read { issueDao.latestPrintJob(refType, refId)?.let { InventoryArchiveMapper.toDomain(it) } }

        override suspend fun printJob(id: String): Outcome<PrintJobRecord?> =
            read { issueDao.findPrintJob(id)?.let { InventoryArchiveMapper.toDomain(it) } }

        override suspend fun recentPrintJobs(limit: Int): Outcome<List<PrintJobRecord>> =
            read { issueDao.recentPrintJobs(limit).map { InventoryArchiveMapper.toDomain(it) } }

        // ---- 盘点 ----

        override suspend fun createStocktake(session: NewStocktake): Outcome<Stocktake> =
            read {
                val id = idGenerator.newId()
                val lines =
                    session.lines.sortedBy { it.lineNo }.map { line ->
                        InventoryStocktakeLineEntity(
                            id = idGenerator.newId(),
                            stocktakeId = id,
                            lineNo = line.lineNo,
                            archiveId = line.archiveId,
                            archiveName = line.archiveName,
                            batchId = line.batchId,
                            batchCode = line.batchCode,
                            locationName = line.locationName,
                            bookQty = line.bookQty,
                            countedQty = line.bookQty,
                            unitId = line.unitId,
                            unitSymbol = line.unitSymbol,
                            frozen = line.frozen,
                        )
                    }
                val entity =
                    InventoryStocktakeEntity(
                        id = id,
                        scopeType = InventoryArchiveMapper.toDb(session.scope),
                        scopeRef = session.scopeRef,
                        scopeLabel = session.scopeLabel,
                        status = StocktakeStatusDb.DRAFT,
                        note = session.note,
                        lineCount = lines.size,
                        diffCount = lines.count { abs(it.countedQty - it.bookQty) > EPSILON },
                        createdAt = session.createdAt.toEpochMilli(),
                        confirmedAt = null,
                    )
                stocktakeDao.insertStocktake(entity)
                if (lines.isNotEmpty()) {
                    stocktakeDao.insertLines(lines)
                }
                InventoryArchiveMapper.toDomain(entity, lines)
            }

        override suspend fun findStocktake(id: String): Outcome<Stocktake?> =
            read { stocktakeDao.findStocktake(id)?.let { stocktakeOf(it) } }

        override suspend fun recentStocktakes(limit: Int): Outcome<List<Stocktake>> =
            read { stocktakeDao.recentStocktakes(limit).map { stocktakeOf(it) } }

        override suspend fun updateStocktakeCounted(
            stocktakeId: String,
            lineId: String,
            countedQty: Double,
        ): Outcome<Stocktake> =
            guard {
                val entity =
                    stocktakeDao.findStocktake(stocktakeId)
                        ?: return@guard Outcome.failure(
                            CulinaryError.NotFound(entity = "inventory_stocktake", id = stocktakeId),
                        )
                val lines = stocktakeDao.linesOf(stocktakeId)
                if (lines.none { it.id == lineId }) {
                    return@guard Outcome.failure(
                        CulinaryError.NotFound(entity = "inventory_stocktake_line", id = lineId),
                    )
                }
                stocktakeDao.updateCounted(lineId, countedQty)
                val updatedLines = lines.map { if (it.id == lineId) it.copy(countedQty = countedQty) else it }
                val updated =
                    entity.copy(
                        diffCount = updatedLines.count { abs(it.countedQty - it.bookQty) > EPSILON },
                    )
                stocktakeDao.updateStocktake(updated)
                Outcome.success(InventoryArchiveMapper.toDomain(updated, updatedLines))
            }

        override suspend fun confirmStocktake(id: String, at: Instant): Outcome<Stocktake> =
            guard {
                val entity =
                    stocktakeDao.findStocktake(id)
                        ?: return@guard Outcome.failure(
                            CulinaryError.NotFound(entity = "inventory_stocktake", id = id),
                        )
                val lines = stocktakeDao.linesOf(id)
                if (entity.status == StocktakeStatusDb.CONFIRMED) {
                    return@guard Outcome.success(InventoryArchiveMapper.toDomain(entity, lines))
                }
                val updated =
                    entity.copy(status = StocktakeStatusDb.CONFIRMED, confirmedAt = at.toEpochMilli())
                stocktakeDao.updateStocktake(updated)
                Outcome.success(InventoryArchiveMapper.toDomain(updated, lines))
            }

        // ---- 库位 ----

        override suspend fun listLocations(): Outcome<List<StorageLocation>> =
            read { locationDao.listActive().map { StorageLocationMapper.toDomain(it) } }

        override suspend fun saveLocation(
            location: StorageLocation,
            kind: String?,
            sortOrder: Int,
        ): Outcome<LocationId> =
            read {
                val now = clock.now().toEpochMilli()
                val existing = locationDao.findById(location.id.value)
                val entity =
                    StorageLocationMapper
                        .toEntity(
                            location = location,
                            normalizedName = NameText.normalize(location.name),
                            createdAt = existing?.createdAt ?: now,
                            updatedAt = now,
                        ).copy(
                            kind = toLocationKind(kind),
                            sortOrder = sortOrder.coerceAtLeast(0),
                            notes = existing?.notes,
                            revision = (existing?.revision ?: 0) + 1,
                        )
                if (existing == null) {
                    locationDao.insert(entity)
                } else {
                    locationDao.update(entity)
                }
                location.id
            }

        override suspend fun batchCountAtLocation(locationId: String): Outcome<Int> =
            read { batchDao.countAtLocation(locationId) }

        // ---- 规则 ----

        override suspend fun readRules(): InventoryRules =
            try {
                val defaults = InventoryRules()
                val remindDays = readInt(KEY_TEMPORARY_REMIND_DAYS, defaults.temporaryRemindDays).coerceIn(1, 3650)
                val pendingDays =
                    readInt(KEY_TEMPORARY_PENDING_DAYS, defaults.temporaryPendingDays)
                        .coerceIn(1, 3650)
                        .coerceAtLeast(remindDays)
                val retentionDays =
                    readInt(KEY_RECYCLE_RETENTION_DAYS, defaults.recycleRetentionDays).coerceIn(1, 3650)
                InventoryRules(
                    fifo = readBoolean(KEY_FIFO, defaults.fifo),
                    temporaryRemindDays = remindDays,
                    temporaryPendingDays = pendingDays,
                    recycleRetentionDays = retentionDays,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // 读取/解析失败一律回默认值：规则只影响提醒阈值，不能阻断库存页。
                InventoryRules()
            }

        override suspend fun writeRules(rules: InventoryRules): Outcome<Unit> =
            read {
                val now = clock.now().toEpochMilli()
                writeMetadata(KEY_TEMPORARY_REMIND_DAYS, rules.temporaryRemindDays.toString(), now)
                writeMetadata(KEY_TEMPORARY_PENDING_DAYS, rules.temporaryPendingDays.toString(), now)
                writeMetadata(KEY_RECYCLE_RETENTION_DAYS, rules.recycleRetentionDays.toString(), now)
                writeMetadata(KEY_FIFO, rules.fifo.toString(), now)
                Unit
            }

        // ---- 内部实现 ----

        /**
         * 追加流水：查幂等键 → 校验余额与可用量 → 原子调整批次 → 写流水。
         *
         * 返回 `false` 只表示幂等键已存在；任何一步失败都不写流水，调用方按整笔失败处理。
         */
        @Suppress("ReturnCount") // 余额/预留/并发三类守卫必须显式失败，合并会掩盖原因
        private suspend fun applyLedger(entry: InventoryLedgerEntry): Outcome<Boolean> {
            if (ledgerDao.findByIdempotencyKey(entry.idempotencyKey) != null) {
                return Outcome.success(false)
            }
            val batch =
                batchDao.findById(entry.batchId.value)
                    ?: return Outcome.failure(
                        CulinaryError.NotFound(entity = "inventory_batch", id = entry.batchId.value),
                    )
            val newOnHand = batch.quantityOnHand + entry.onHandDelta
            val newReserved = batch.quantityReserved + entry.reservedDelta
            if (entry.onHandDelta < 0 && newOnHand < -EPSILON) {
                return insufficient(entry, requested = -entry.onHandDelta, available = batch.quantityOnHand)
            }
            if (entry.onHandDelta < 0 &&
                -entry.onHandDelta > batch.quantityOnHand - batch.quantityReserved + EPSILON
            ) {
                return insufficient(
                    entry = entry,
                    requested = -entry.onHandDelta,
                    available = batch.quantityOnHand - batch.quantityReserved,
                )
            }
            if (newReserved < -EPSILON || newReserved > newOnHand + EPSILON) {
                return Outcome.failure(CulinaryError.Conflict(entity = "inventory_batch"))
            }
            val rows =
                batchDao.applyDeltas(
                    id = entry.batchId.value,
                    onHandDelta = entry.onHandDelta,
                    reservedDelta = entry.reservedDelta,
                    updatedAt = clock.now().toEpochMilli(),
                )
            if (rows != 1) {
                return Outcome.failure(CulinaryError.Conflict(entity = "inventory_batch"))
            }
            ledgerDao.insert(
                InventoryMapper.toEntity(entry.copy(onHandAfter = newOnHand, reservedAfter = newReserved)),
            )
            return Outcome.success(true)
        }

        private suspend fun toArchive(entity: InventoryArchiveEntity): InventoryArchive {
            val unit = loadUnit(entity.stockUnitId) ?: unitNotFound(entity.stockUnitId)
            return InventoryArchiveMapper.toDomain(entity, unit)
        }

        private suspend fun toLedger(entity: InventoryLedgerEntity): InventoryLedgerEntry {
            val unit = loadUnit(entity.stockUnitId) ?: unitNotFound(entity.stockUnitId)
            return InventoryMapper.toDomain(entity, unit)
        }

        private suspend fun toBatchRecords(entities: List<InventoryBatchEntity>): List<InventoryBatchRecord> {
            if (entities.isEmpty()) return emptyList()
            val sums = ledgerDao.ledgerSumsByBatch().associateBy { it.batchId }
            val archiveNames = mutableMapOf<String, String>()
            entities.mapNotNull { it.archiveId }.distinct().forEach { archiveId ->
                archiveDao.findById(archiveId)?.let { archiveNames[archiveId] = it.name }
            }
            val productNames = mutableMapOf<String, String>()
            entities.mapNotNull { it.productId }.distinct().forEach { productId ->
                productDao.findById(productId)?.let { productNames[productId] = it.name }
            }
            val locationNames = mutableMapOf<String, String>()
            entities.map { it.locationId }.distinct().forEach { locationId ->
                locationDao.findById(locationId)?.let { locationNames[locationId] = it.name }
            }
            val units = mutableMapOf<String, UnitModel>()
            entities.map { it.stockUnitId }.distinct().forEach { unitId ->
                units[unitId] = loadUnit(unitId) ?: unitNotFound(unitId)
            }
            return entities.map { entity ->
                val sum = sums[entity.id]
                InventoryArchiveMapper.toDomain(
                    entity = entity,
                    stockUnit = units.getValue(entity.stockUnitId),
                    archiveName = entity.archiveId?.let(archiveNames::get).orEmpty(),
                    productName = entity.productId?.let(productNames::get),
                    locationName = locationNames[entity.locationId],
                    inboundQuantity = sum?.inbound,
                    outboundQuantity = sum?.outbound,
                )
            }
        }

        private suspend fun slipOf(entity: InventoryIssueSlipEntity): IssueSlip =
            InventoryArchiveMapper.toDomain(entity, issueDao.linesOf(entity.id))

        private suspend fun stocktakeOf(entity: InventoryStocktakeEntity): Stocktake =
            InventoryArchiveMapper.toDomain(entity, stocktakeDao.linesOf(entity.id))

        private suspend fun loadUnit(unitId: String): UnitModel? = unitDao.findById(unitId)?.let(UnitMapper::toDomain)

        private fun unitNotFound(unitId: String): Nothing = throw NoSuchElementException("missing unit: $unitId")

        private suspend fun readInt(key: String, fallback: Int): Int =
            metadataDao.find(key)?.value?.trim()?.toIntOrNull() ?: fallback

        private suspend fun readBoolean(key: String, fallback: Boolean): Boolean =
            when (metadataDao.find(key)?.value?.trim()?.lowercase()) {
                "true" -> true
                "false" -> false
                else -> fallback
            }

        private suspend fun writeMetadata(key: String, value: String, at: Long) {
            metadataDao.put(AppMetadataEntity(key = key, value = value, updatedAt = at))
        }

        private fun toLocationKind(kind: String?): StorageLocationKindDb {
            val name = kind?.trim()?.uppercase() ?: return StorageLocationKindDb.OTHER
            return StorageLocationKindDb.entries.firstOrNull { it.name == name } ?: StorageLocationKindDb.OTHER
        }

        private fun insufficient(
            entry: InventoryLedgerEntry,
            requested: Double,
            available: Double,
        ): Outcome<Nothing> =
            Outcome.failure(
                CulinaryError.Domain(
                    detail =
                        DomainDetail.InsufficientStock(
                            productId = entry.productId?.value.orEmpty(),
                            requested = requested,
                            available = available,
                        ),
                    code = ErrorCode.INV_003,
                ),
            )

        private suspend fun <T> read(block: suspend () -> T): Outcome<T> =
            try {
                Outcome.success(block())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Outcome.failure(DbErrorMapper.map(e))
            }

        private suspend fun <T> guard(block: suspend () -> Outcome<T>): Outcome<T> =
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Outcome.failure(DbErrorMapper.map(e))
            }

        private companion object {
            const val EPSILON = 1e-9
            const val KEY_TEMPORARY_REMIND_DAYS = "inventory.rule.temporary_remind_days"
            const val KEY_TEMPORARY_PENDING_DAYS = "inventory.rule.temporary_pending_days"
            const val KEY_RECYCLE_RETENTION_DAYS = "inventory.rule.recycle_retention_days"
            const val KEY_FIFO = "inventory.rule.fifo"
        }
    }
